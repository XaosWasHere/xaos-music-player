package xaos.desktop.online

import xaos.desktop.library.TagEditor
import xaos.desktop.library.Track
import kotlin.math.abs

/**
 * Il testo di un brano appena scaricato, cercato da solo su LRCLIB.
 *
 * Si aggiunge solo se c'è un risultato con la stessa durata del brano (entro
 * [TOLERANCE_MS]): è l'unico modo di essere quasi certi che sia la stessa
 * incisione e non un live o una versione diversa. Se il testo non va bene lo
 * si cambia dall'editor, come ogni altro.
 */
object AutoLyrics {

    private const val TOLERANCE_MS = 1_500L

    /** Le parti tra parentesi dei titoli di YouTube: "(Official Video)", "[4K]"… */
    private val brackets = Regex("""\s*[(\[{][^)\]}]*[)\]}]""")

    /** true se ha aggiunto un testo al file. */
    suspend fun fetchAndEmbed(track: Track): Boolean {
        if (!TagEditor.readLyrics(track).isNullOrBlank()) return false
        val found = candidates(track).firstNotNullOfOrNull { (title, artist) -> bestMatch(title, artist, track.durationMs) }
            ?: return false
        return TagEditor.writeLyrics(track, found).isSuccess
    }

    /**
     * Prima i tag così come sono; poi il titolo ripulito, e se è nella forma
     * "Artista - Titolo" (tipica dei video) le due parti separate.
     */
    private fun candidates(track: Track): List<Pair<String, String>> = buildList {
        add(track.title to track.artist)
        val clean = track.title.replace(brackets, "").trim()
        if (clean.isNotEmpty() && clean != track.title) add(clean to track.artist)
        val parts = clean.split(" - ", limit = 2)
        if (parts.size == 2 && parts.all { it.isNotBlank() }) add(parts[1].trim() to parts[0].trim())
    }.distinct()

    private suspend fun bestMatch(title: String, artist: String, durationMs: Long): String? {
        if (durationMs <= 0) return null
        val matching = LrcLib.search(title, artist, durationMs).filter { r ->
            !r.instrumental && r.duration != null && abs((r.duration * 1000).toLong() - durationMs) <= TOLERANCE_MS
        }
        return matching.firstNotNullOfOrNull { it.syncedLyrics?.takeIf(String::isNotBlank) }
            ?: matching.firstNotNullOfOrNull { it.plainLyrics?.takeIf(String::isNotBlank) }
    }
}
