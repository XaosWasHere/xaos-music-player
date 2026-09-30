package xaos.desktop.library

/**
 * Una riga di testo, con il momento in cui va illuminata. [timeMs] vale -1
 * quando il testo non è sincronizzato.
 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Il testo di un brano, letto come lo legge l'app Android: con i tempi LRC
 * scorre da solo e la riga corrente si accende, senza si legge e basta.
 */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {

    val isEmpty: Boolean get() = lines.isEmpty()

    /** L'indice della riga da illuminare a [positionMs], o -1 prima che il canto cominci. */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    companion object {
        private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val HEADER_TAG = Regex("""\[[a-zA-Z#]+:.*]""")

        /** Lo stesso parser del telefono: più tempi sulla stessa riga, centesimi o millesimi. */
        fun parse(raw: String): Lyrics {
            val timed = mutableListOf<LyricLine>()
            val plain = mutableListOf<String>()
            raw.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { line ->
                val stamps = mutableListOf<Long>()
                var rest = line
                while (true) {
                    val match = TIMESTAMP.find(rest)
                    if (match == null || match.range.first != 0) break
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val fraction = match.groupValues[3]
                    val millis = when (fraction.length) {
                        0 -> 0L
                        3 -> fraction.toLong()
                        else -> fraction.padEnd(2, '0').take(2).toLong() * 10
                    }
                    stamps += minutes * 60_000 + seconds * 1_000 + millis
                    rest = rest.substring(match.range.last + 1)
                }
                val text = rest.trim()
                if (stamps.isEmpty()) {
                    if (text.isNotEmpty() && !HEADER_TAG.matches(text)) plain += text
                } else {
                    stamps.forEach { timed += LyricLine(it, text) }
                }
            }
            return if (timed.isNotEmpty()) Lyrics(timed.sortedBy { it.timeMs }, synced = true)
            else Lyrics(plain.map { LyricLine(-1L, it) }, synced = false)
        }
    }
}
