package xaos.desktop.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
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

@Composable
fun rememberArtwork(track: Track?, sizePx: Int): State<ImageBitmap?> =
    produceState(initialValue = track?.let { ArtworkLoader.cached(it, sizePx) }, track?.path, sizePx) {
        value = track?.let { ArtworkLoader.load(it, sizePx) }
    }
