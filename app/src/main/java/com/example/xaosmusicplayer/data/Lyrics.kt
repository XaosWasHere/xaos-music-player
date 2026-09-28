package com.example.xaosmusicplayer.data

/**
 * Cosa sappiamo del testo del brano in ascolto.
 *
 * Leggere il tag richiede un accesso al disco, quindi "non lo so ancora" e "non
 * c'è" sono due cose diverse: confonderle farebbe lampeggiare "nessun testo" per
 * un istante a ogni cambio di brano, anche su quelli che il testo ce l'hanno.
 */
sealed interface LyricsState {
    data object Loading : LyricsState
    data object Missing : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
}

/**
 * Una riga di testo, con il momento in cui va illuminata.
 *
 * [timeMs] vale -1 quando il testo non è sincronizzato: in quel caso la riga si
 * legge ma non segue la riproduzione.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * Il testo di un brano, come è scritto dentro il file.
 *
 * [synced] distingue i due casi che cambiano la schermata: con i tempi il testo
 * scorre da solo e la riga corrente si accende, senza si legge e basta.
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * L'indice della riga da illuminare a una data posizione, o -1 prima che il
     * canto cominci.
     *
     * Ricerca binaria: viene chiamata a ogni aggiornamento della posizione, cioè
     * diverse volte al secondo, su testi che possono avere un centinaio di righe.
     */
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
}
