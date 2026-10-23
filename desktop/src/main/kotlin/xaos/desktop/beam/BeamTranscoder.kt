package xaos.desktop.beam

import xaos.desktop.BeamQuality
import xaos.desktop.library.Track
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * I brani dello streaming di Beam alla qualità scelta.
 *
 * Un FLAC a 24 bit e 96 kHz viaggia a 3–5 Mbit/s: su un Wi-Fi debole basta
 * da solo a riempire la rete. Con "Alta" si manda la copia MP3 se la
 * libreria ne ha una, altrimenti il brano convertito in AAC a 256 kbit/s; con
 * "Leggera" in AAC a 128. MP3, AAC e Ogg già abbastanza leggeri restano come
 * sono: riconvertirli peggiorerebbe e basta.
 *
 * Le conversioni si fanno con ffmpeg (lo stesso dei download) e restano in
 * una cartella di cache, fino a [MAX_CACHE_BYTES]: un brano riascoltato non si
 * riconverte, e i successivi della coda si preparano in anticipo.
 */
class BeamTranscoder(
    private val ffmpeg: () -> File?,
    private val cacheDir: File,
) {
    /** Un brano per volta per chiave: due richieste dello stesso aspettano la stessa conversione. */
    private val locks = ConcurrentHashMap<String, Any>()
    private val background = Executors.newSingleThreadExecutor { r -> Thread(r, "xaos-beam-transcode").apply { isDaemon = true } }

    /** Il file da mandare al telefono per [track], convertendolo se serve (e se si può). */
    fun streamFile(track: Track, original: File, quality: BeamQuality): File {
        val target = targetKbps(track, original, quality) ?: return mp3CopyOr(track, original, quality)
        return transcoded(original, track, target) ?: mp3CopyOr(track, original, quality)
    }

    /** Prepara in anticipo i prossimi brani della coda, uno dopo l'altro, senza fretta. */
    fun prefetch(tracks: List<Pair<Track, File>>, quality: BeamQuality) {
        tracks.forEach { (track, original) ->
            val target = targetKbps(track, original, quality) ?: return@forEach
            if (cacheFile(original, track, target).isFile) return@forEach
            background.execute { transcoded(original, track, target) }
        }
    }

    /**
     * A che bitrate convertire, o null se il file va bene così (o la copia
     * MP3 basta). Le copie MP3 della libreria sono già fatte per il telefono.
     */
    private fun targetKbps(track: Track, original: File, quality: BeamQuality): Int? {
        if (quality == BeamQuality.ORIGINAL) return null
        val ext = original.extension.lowercase()
        val lossy = ext in LOSSY
        val kbps = bitrateKbps(original, track.durationMs)
        return when (quality) {
            // Un .m4a può anche essere ALAC (senza perdita): lo tradisce il bitrate.
            BeamQuality.HIGH -> if (hasMp3Copy(track) || (lossy && (kbps == null || kbps <= 400))) null else quality.kbps
            BeamQuality.LIGHT -> if (lossy && kbps != null && kbps <= quality.kbps * 13 / 10) null else quality.kbps
            BeamQuality.ORIGINAL -> null
        }
    }

    private fun hasMp3Copy(track: Track) = track.mobilePath?.let(::File)?.isFile == true

    private fun mp3CopyOr(track: Track, original: File, quality: BeamQuality): File =
        if (quality != BeamQuality.ORIGINAL) track.mobilePath?.let(::File)?.takeIf { it.isFile } ?: original else original

    private fun bitrateKbps(file: File, durationMs: Long): Int? =
        if (durationMs <= 0) null else (file.length() * 8 / durationMs).toInt()

    private fun cacheFile(original: File, track: Track, kbps: Int): File {
        val key = sha1("${original.path}|${track.modified}|${original.length()}").take(20)
        return File(cacheDir, "$key-$kbps.m4a")
    }

    /** La conversione, o quella già fatta; null se ffmpeg manca o non ce la fa. */
    private fun transcoded(original: File, track: Track, kbps: Int): File? {
        val out = cacheFile(original, track, kbps)
        val lock = locks.computeIfAbsent(out.name) { Any() }
        synchronized(lock) {
            if (out.isFile && out.length() > 0) {
                out.setLastModified(System.currentTimeMillis())
                return out
            }
            val exe = ffmpeg() ?: return null
            cacheDir.mkdirs()
            val tmp = File(cacheDir, out.name + ".part")
            tmp.delete()
            val ok = runCatching {
                val process = ProcessBuilder(
                    exe.path, "-nostdin", "-v", "error", "-y",
                    "-i", original.path,
                    // Solo l'audio: la copertina incorporata non serve a chi ascolta in streaming.
                    "-map", "0:a:0", "-vn", "-map_metadata", "-1",
                    "-c:a", "aac", "-b:a", "${kbps}k",
                    // L'hi-res scende a 44,1 kHz: a questi bitrate di più non si sente.
                    "-ar", "44100",
                    // L'indice in testa al file: il telefono può saltare nel brano subito.
                    "-movflags", "+faststart",
                    "-f", "mp4", tmp.path,
                ).redirectErrorStream(true).start()
                process.inputStream.readAllBytes()
                process.waitFor(5, TimeUnit.MINUTES) && process.exitValue() == 0 && tmp.length() > 0
            }.getOrDefault(false)
            if (!ok || !tmp.renameTo(out)) {
                tmp.delete()
                return null
            }
            trim()
            return out
        }
    }

    /** La cache non cresce oltre il limite: se ne vanno i brani non ascoltati da più tempo. */
    private fun trim() {
        val files = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".m4a") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_CACHE_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val LOSSY = setOf("mp3", "m4a", "aac", "ogg", "opus")
        const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024
    }
}
