package xaos.desktop.sync

import xaos.desktop.library.Track
import java.text.Normalizer
import kotlin.math.abs

/** Un brano come lo conosce la libreria di Android (MediaStore). */
data class PhoneSong(val title: String, val artist: String, val durationMs: Long, val path: String = "")

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

    fun isOnPhone(track: Track): Boolean = find(track) != null

    /** Il brano del telefono che corrisponde a [track], con il suo percorso. */
    fun find(track: Track): PhoneSong? = find(track.title, track.artist, track.durationMs)

    /**
     * Il brano dell'elenco che corrisponde a titolo, artista e durata dati.
     * Funziona nei due sensi: l'elenco può essere quello del telefono (per
     * sapere cosa c'è già lì) o la libreria del PC (per sapere cosa c'è solo
     * sul telefono).
     */
    fun find(title: String, artist: String, durationMs: Long): PhoneSong? {
        byTitle[fullKey(title)]?.let { candidates ->
            // A parità di titolo, prima quello con la durata più vicina.
            candidates
                .filter { artistsCompatible(it.artist, artist) || closeDuration(it.durationMs, durationMs) }
                .minByOrNull { abs(it.durationMs - durationMs) }
                ?.let { return it }
        }
        byBareTitle[bareKey(title)]?.let { candidates ->
            candidates.filter { closeDuration(it.durationMs, durationMs) }.minByOrNull { abs(it.durationMs - durationMs) }?.let { return it }
        }
        return null
    }

    private fun closeDuration(a: Long, b: Long): Boolean =
        a > 0 && b > 0 && abs(a - b) <= DURATION_TOLERANCE_MS

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
                // Il percorso, se chiesto, è l'ultima colonna.
                val p = line.lastIndexOf(", _data=")
                val endDuration = if (p >= 0) p else line.length
                val d = line.lastIndexOf(", duration=", endDuration)
                val a = line.lastIndexOf(", artist=", d)
                val t = line.indexOf("title=")
                if (d < 0 || a < 0 || t < 0 || t > a) return@mapNotNull null
                PhoneSong(
                    title = line.substring(t + 6, a).nullIfNull(),
                    artist = line.substring(a + 9, d).nullIfNull(),
                    durationMs = line.substring(d + 11, endDuration).trim().toLongOrNull() ?: 0L,
                    path = if (p >= 0) line.substring(p + 8).nullIfNull() else "",
                )
            }.filter { it.title.isNotBlank() }.toList()

        private fun String.nullIfNull() = if (this == "NULL") "" else this

        /**
         * Legge l'uscita di `content query` con le colonne [keys], nell'ordine
         * della proiezione. I valori possono contenere virgole, quindi ogni
         * colonna si cerca dall'ultima alla prima, a ritroso.
         */
        fun parseRows(output: String, keys: List<String>): List<Map<String, String>> =
            output.lineSequence().mapNotNull { raw ->
                val line = raw.trimEnd('\r')
                if (!line.startsWith("Row:")) return@mapNotNull null
                val row = HashMap<String, String>()
                var end = line.length
                for (i in keys.indices.reversed()) {
                    val marker = if (i == 0) " ${keys[i]}=" else ", ${keys[i]}="
                    val at = line.lastIndexOf(marker, end - 1)
                    if (at < 0) return@mapNotNull null
                    row[keys[i]] = line.substring(at + marker.length, end).nullIfNull()
                    end = at
                }
                row
            }.toList()
    }
}
