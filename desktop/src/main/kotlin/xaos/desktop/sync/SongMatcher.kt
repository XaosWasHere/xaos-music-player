package xaos.desktop.sync

import xaos.desktop.library.Track
import java.text.Normalizer
import kotlin.math.abs

/** Un brano come lo conosce la libreria di Android (MediaStore). */
data class PhoneSong(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val path: String = "",
    /** L'_ID di MediaStore: è così che l'app Android identifica i brani. */
    val id: Long = -1,
)

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
 *
 * Ultimo tentativo, lo scheletro ASCII del titolo con la durata: MediaStore
 * legge male i tag ID3 scritti in Latin-1 con dentro caratteri UTF-8, e
 * restituisce "Jinuâ€™s Lament" per "Jinu’s Lament" o "Une vie ŕ peindre" per
 * "Une vie à peindre". Tolti tutti i caratteri non ASCII i due titoli
 * coincidono. Senza questo passaggio quei brani risultavano sempre mancanti e
 * rimbalzavano fra PC e telefono a ogni sincronizzazione, moltiplicandosi.
 */
class SongMatcher(phoneSongs: List<PhoneSong>) {

    private val byTitle: Map<String, List<PhoneSong>> = phoneSongs.groupBy { fullKey(it.title) }
    private val byBareTitle: Map<String, List<PhoneSong>> = phoneSongs.groupBy { bareKey(it.title) }
    private val byAsciiTitle: Map<String, List<PhoneSong>> =
        phoneSongs.groupBy { asciiKey(it.title) }.filterKeys { it.length >= MIN_ASCII_KEY }
    private val byAsciiBareTitle: Map<String, List<PhoneSong>> =
        phoneSongs.groupBy { asciiKey(bareTitle(it.title)) }.filterKeys { it.length >= MIN_ASCII_KEY }

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
        for ((index, key) in listOf(byAsciiTitle to asciiKey(title), byAsciiBareTitle to asciiKey(bareTitle(title)))) {
            if (key.length < MIN_ASCII_KEY) continue
            index[key]?.filter { closeDuration(it.durationMs, durationMs) }
                ?.minByOrNull { abs(it.durationMs - durationMs) }
                ?.let { return it }
        }
        return null
    }

    /** Tutti i brani dell'elenco che corrispondono, non solo il migliore: servono per i doppioni. */
    fun findAll(title: String, artist: String, durationMs: Long): List<PhoneSong> {
        val best = find(title, artist, durationMs) ?: return emptyList()
        val key = asciiKey(bareTitle(best.title)).ifEmpty { bareKey(best.title) }
        val pool = byAsciiBareTitle[key] ?: byBareTitle[bareKey(best.title)] ?: listOf(best)
        return (listOf(best) + pool.filter { it !== best && abs(it.durationMs - best.durationMs) <= SAME_RECORDING_MS }).distinct()
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
        /** Due file dello stesso brano (FLAC e sua copia MP3, o due copie) differiscono di pochi centesimi. */
        const val SAME_RECORDING_MS = 1_500L
        /** Sotto questa lunghezza lo scheletro ASCII non distingue abbastanza: "a", "01"… */
        private const val MIN_ASCII_KEY = 4

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
            compact(bareTitle(title)).ifEmpty { compact(title) }

        fun bareTitle(title: String): String =
            title.replace(brackets, " ").replace(featTail, "").ifBlank { title }

        /**
         * Solo lettere e cifre ASCII, minuscole. Non si scompongono gli accenti
         * apposta: una lettera accentata e la sua versione storpiata da una
         * codifica sbagliata spariscono entrambe, e il resto del titolo combacia.
         */
        fun asciiKey(s: String): String =
            s.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

        /** Lo stesso brano, per quanto si possa dire dai tag: titolo, artista compatibile, durata. */
        fun sameSong(a: Track, b: Track): Boolean {
            if (a.durationMs > 0 && b.durationMs > 0 && abs(a.durationMs - b.durationMs) > SAME_RECORDING_MS) return false
            val titles = fullKey(a.title) == fullKey(b.title) ||
                asciiKey(a.title).let { it.length >= MIN_ASCII_KEY && it == asciiKey(b.title) }
            if (!titles) return false
            val pa = compact(primaryArtist(a.artist))
            val pb = compact(primaryArtist(b.artist))
            return pa.isEmpty() || pb.isEmpty() || pa == pb || compact(a.artist).contains(pb) || compact(b.artist).contains(pa)
        }

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
