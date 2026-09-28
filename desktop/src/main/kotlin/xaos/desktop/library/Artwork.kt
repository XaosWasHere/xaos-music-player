package xaos.desktop.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Le copertine, lette dalla cartella o dal file, ridotte e tenute in memoria.
 *
 * L'ordine di ricerca rispecchia com'è organizzata una libreria tipica:
 * prima la copertina accanto al brano ("<brano>.cover.jpg"), poi quella della
 * cartella (Folder.jpg, cover.jpg…), infine quella incorporata nel file, che è
 * la più costosa da leggere.
 */
object ArtworkLoader {

    private val cache = object : LinkedHashMap<String, ImageBitmap?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>): Boolean =
            size > MAX_ENTRIES
    }

    /** Le decodifiche costano CPU: poche per volta, così lo scorrimento resta fluido. */
    private val gate = Semaphore(4)

    /**
     * Dimentica le copertine dopo una modifica di [track]. Si svuota tutto: la
     * stessa immagine può stare sotto più chiavi (cartella, file, taglie), e
     * le miniature si ricaricano da sole, solo quelle che si vedono.
     */
    fun invalidate(track: Track) {
        synchronized(cache) { cache.clear() }
        synchronized(folderCache) { folderCache.clear() }
        ArtColorExtractor.forget(track)
    }

    fun cached(track: Track, sizePx: Int): ImageBitmap? =
        synchronized(cache) { cache[key(track, sizePx)] }

    suspend fun load(track: Track, sizePx: Int): ImageBitmap? {
        val k = key(track, sizePx)
        synchronized(cache) { if (cache.containsKey(k)) return cache[k] }
        val image = withContext(Dispatchers.IO) {
            gate.withPermit { readBytes(track)?.let { decode(it, sizePx) } }
        }
        synchronized(cache) { cache[k] = image }
        return image
    }

    private fun key(track: Track, sizePx: Int): String {
        // Le tracce di un album con la copertina di cartella condividono la
        // stessa immagine: stessa chiave, una sola decodifica.
        val sidecar = sidecarFor(track.file)
        val source = when {
            sidecar != null -> sidecar.path
            folderImage(track.file.parentFile) != null && !track.hasEmbeddedArt -> track.file.parent
            else -> track.path
        }
        return "$source@$sizePx"
    }

    /** I byte della copertina così come sono, per chi deve analizzarla. */
    fun rawBytes(track: Track): ByteArray? = readBytes(track)

    private fun readBytes(track: Track): ByteArray? {
        sidecarFor(track.file)?.let { return it.readBytes() }
        if (track.hasEmbeddedArt) {
            runCatching { AudioFileIO.read(track.file).tag?.firstArtwork?.binaryData }
                .getOrNull()?.let { return it }
        }
        return folderImage(track.file.parentFile)?.readBytes()
    }

    private fun sidecarFor(file: File): File? {
        val dir = file.parentFile ?: return null
        return listOf("jpg", "jpeg", "png")
            .map { File(dir, "${file.nameWithoutExtension}.cover.$it") }
            .firstOrNull { it.isFile }
    }

    private val folderNames = listOf("folder", "cover", "front", "albumart", "albumartlarge")

    private val folderCache = HashMap<String, File?>()

    private fun folderImage(dir: File?): File? {
        dir ?: return null
        synchronized(folderCache) { if (folderCache.containsKey(dir.path)) return folderCache[dir.path] }
        val found = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            ?.sortedBy { f -> folderNames.indexOf(f.nameWithoutExtension.lowercase()).let { if (it < 0) 99 else it } }
            ?.firstOrNull { it.nameWithoutExtension.lowercase() in folderNames }
        synchronized(folderCache) { folderCache[dir.path] = found }
        return found
    }

    /** Ritaglio quadrato al centro, ridotto con interpolazione bicubica. */
    private fun decode(bytes: ByteArray, sizePx: Int): ImageBitmap? = runCatching {
        val src = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        // ARGB e non RGB: una copertina PNG con parti trasparenti, ridotta su
        // un fondo senza alfa, diventerebbe nera proprio lì.
        val out = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, sizePx, sizePx, x, y, x + side, y + side, null)
        g.dispose()
        out.toComposeImageBitmap()
    }.getOrNull()

    private const val MAX_ENTRIES = 600
}

/**
 * I colori di una copertina, già pronti per fare da luce sullo sfondo: tre
 * tinte dominanti portate a una luminosità fissa, più la media vera.
 */
data class ArtColors(val primary: Color, val secondary: Color, val accent: Color, val average: Color) {
    companion object {
        val Fallback = ArtColors(Color(0xFF2A2A35), Color(0xFF15151C), Color(0xFF3A3A4A), Color(0xFF15151C))
    }
}

object ArtColorExtractor {
    private val cache = HashMap<String, ArtColors>()

    fun forget(track: Track) { synchronized(cache) { cache.remove(track.path) } }

    suspend fun colors(track: Track): ArtColors {
        synchronized(cache) { cache[track.path]?.let { return it } }
        val result = withContext(Dispatchers.IO) {
            runCatching { ArtworkLoader.rawBytes(track)?.let { extract(it) } }.getOrNull()
        } ?: ArtColors.Fallback
        synchronized(cache) { cache[track.path] = result }
        return result
    }

    /**
     * Le tinte si raccolgono in dodici spicchi di colore, pesando ogni pixel
     * per saturazione e luminosità: i grigi e i neri non contano, così una
     * copertina scura con un dettaglio rosso dà un rosso, non un grigio.
     */
    private fun extract(bytes: ByteArray): ArtColors? {
        val src = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val n = 48
        val small = BufferedImage(n, n, BufferedImage.TYPE_INT_RGB)
        small.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, n, n, null)
            dispose()
        }
        val weight = FloatArray(12)
        val sumR = FloatArray(12); val sumG = FloatArray(12); val sumB = FloatArray(12)
        var ar = 0L; var ag = 0L; var ab = 0L
        val hsb = FloatArray(3)
        for (y in 0 until n) for (x in 0 until n) {
            val rgb = small.getRGB(x, y)
            val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
            ar += r; ag += g; ab += b
            java.awt.Color.RGBtoHSB(r, g, b, hsb)
            val w = hsb[1] * hsb[2]
            if (hsb[1] < 0.18f || hsb[2] < 0.15f) continue
            val bin = ((hsb[0] * 12).toInt()).coerceIn(0, 11)
            weight[bin] += w; sumR[bin] += r * w; sumG[bin] += g * w; sumB[bin] += b * w
        }
        val count = n * n
        val average = Color(red = (ar / count).toInt(), green = (ag / count).toInt(), blue = (ab / count).toInt())
        val ranked = (0 until 12).filter { weight[it] > 0f }.sortedByDescending { weight[it] }
        if (ranked.isEmpty()) return ArtColors.Fallback.copy(average = average)
        fun binColor(i: Int) = Color(
            red = (sumR[i] / weight[i]).toInt().coerceIn(0, 255),
            green = (sumG[i] / weight[i]).toInt().coerceIn(0, 255),
            blue = (sumB[i] / weight[i]).toInt().coerceIn(0, 255),
        )
        val first = binColor(ranked[0])
        val second = ranked.getOrNull(1)?.let(::binColor) ?: first
        val third = ranked.getOrNull(2)?.let(::binColor) ?: second
        return ArtColors(
            primary = first.forGlow(0.45f),
            secondary = second.forGlow(0.28f),
            accent = third.forGlow(0.58f),
            average = average,
        )
    }

    /** Stessa normalizzazione del telefono: satura un po' e fissa la luminosità. */
    private fun Color.forGlow(lightness: Float): Color {
        val hsb = java.awt.Color.RGBtoHSB((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), null)
        val sat = (hsb[1] * 1.25f).coerceIn(0.25f, 0.95f)
        // HSB non ha la luminosità di HSL: la si approssima con la brillantezza.
        val bright = (lightness * 1.6f).coerceIn(0.2f, 1f)
        return Color(java.awt.Color.HSBtoRGB(hsb[0], sat, bright))
    }
}

@Composable
fun rememberArtwork(track: Track?, sizePx: Int): State<ImageBitmap?> =
    produceState(initialValue = track?.let { ArtworkLoader.cached(it, sizePx) }, track?.path, sizePx) {
        value = track?.let { ArtworkLoader.load(it, sizePx) }
    }
