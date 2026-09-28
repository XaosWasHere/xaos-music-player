package xaos.desktop.sync

import xaos.desktop.library.Track
import java.text.Normalizer
import kotlin.math.abs

/** Un brano come lo conosce la libreria di Android (MediaStore). */
data class PhoneSong(val title: String, val artist: String, val durationMs: Long)

/**
 * Riconosce se un brano del PC c'è già sul telefono, a prescindere da dove sta
 * e da come si chiama il file.
 *
 * Sul telefono la musica può essere arrivata per altre strade — rinominata,
 * riorganizzata per artista, ricodificata — quindi né percorso né dimensione
 * valgono come prova. Contano titolo, artista e durata, letti dai tag:
 *
 * - stesso titolo e artista compatibile: è lui;
 * - stesso titolo al netto delle parentesi ("(feat. …)", "[Remastered]") e
 *   durata entro [DURATION_TOLERANCE_MS]: è lui con i tag scritti diversamente.
 *
 * La durata fa da arbitro nei casi ambigui: "Song" e "Song (Live)" hanno lo
 * stesso titolo spogliato, ma quasi mai la stessa lunghezza.
 */
class SongMatcher(phoneSongs: List<PhoneSong>) {

    private val byTitle: Map<String, List<PhoneSong>> = phoneSongs.groupBy { fullKey(it.title) }
    private val byBareTitle: Map<String, List<PhoneSong>> = phoneSongs.groupBy { bareKey(it.title) }

    fun isOnPhone(track: Track): Boolean {
        byTitle[fullKey(track.title)]?.let { candidates ->
            if (candidates.any { artistsCompatible(it.artist, track.artist) || closeDuration(it, track) }) return true
        }
        byBareTitle[bareKey(track.title)]?.let { candidates ->
            if (candidates.any { closeDuration(it, track) }) return true
        }
        return false
    }

    private fun closeDuration(song: PhoneSong, track: Track): Boolean =
        song.durationMs > 0 && track.durationMs > 0 &&
            abs(song.durationMs - track.durationMs) <= DURATION_TOLERANCE_MS

    /**
     * "9Lana, Giga, TeddyLoid" e "9Lana" sono compatibili: basta che uno
     * contenga l'artista principale dell'altro.
     */
    private fun artistsCompatible(a: String, b: String): Boolean {
        val na = compact(a)
        val nb = compact(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        val pa = compact(primaryArtist(a))
        val pb = compact(primaryArtist(b))
        return na == nb || (pa.isNotEmpty() && nb.contains(pa)) || (pb.isNotEmpty() && na.contains(pb))
    }

    companion object {
        const val DURATION_TOLERANCE_MS = 3_000L

        private val brackets = Regex("\\([^)]*\\)|\\[[^]]*]|\\{[^}]*}")
        private val featTail = Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s.*$", RegexOption.IGNORE_CASE)
        private val artistSplit = Regex("\\s*(,|&|;|/|\\sx\\s|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sand\\s)\\s*", RegexOption.IGNORE_CASE)

        /** Minuscolo, senza accenti, solo lettere e cifre (di qualunque alfabeto). */
        fun compact(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .filter { it.isLetterOrDigit() }

        fun fullKey(title: String): String = compact(title)

        fun bareKey(title: String): String =
            compact(title.replace(brackets, " ").replace(featTail, "")).ifEmpty { compact(title) }

        fun primaryArtist(artist: String): String = artist.split(artistSplit).firstOrNull().orEmpty()

        /**
         * Legge l'uscita di `content query`, una riga per brano:
         * `Row: 3 title=…, artist=…, duration=…`. I valori possono contenere
         * virgole, quindi si taglia sulle chiavi, dall'ultima alla prima.
         */
        fun parseContentQuery(output: String): List<PhoneSong> =
            output.lineSequence().mapNotNull { raw ->
                val line = raw.trimEnd('\r')
                if (!line.startsWith("Row:")) return@mapNotNull null
                val d = line.lastIndexOf(", duration=")
                val a = line.lastIndexOf(", artist=", d)
                val t = line.indexOf("title=")
                if (d < 0 || a < 0 || t < 0 || t > a) return@mapNotNull null
                PhoneSong(
                    title = line.substring(t + 6, a).nullIfNull(),
                    artist = line.substring(a + 9, d).nullIfNull(),
                    durationMs = line.substring(d + 11).trim().toLongOrNull() ?: 0L,
                )
            }.filter { it.title.isNotBlank() }.toList()

        private fun String.nullIfNull() = if (this == "NULL") "" else this
    }
}
