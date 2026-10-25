package xaos.desktop.online

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import xaos.desktop.Settings
import xaos.desktop.library.Track
import xaos.desktop.library.hasLyricsFile
import java.io.File
import kotlin.coroutines.coroutineContext

/** La ricerca dei testi, per la schermata Impostazioni che ne mostra l'avanzamento. */
val LocalLibraryLyrics = compositionLocalOf<LibraryLyrics?> { null }

/**
 * I testi per tutta la libreria: all'avvio i brani senza testo lo cercano su
 * LRCLIB, con la stessa regola dei download (si aggiunge solo con la stessa
 * durata, entro un secondo e mezzo). Se non c'è, non si fa niente.
 *
 * I brani già cercati si ricordano in `lyrics-checked.json`, con dimensione e
 * data del file: non si richiedono a ogni avvio, e tornano in lista solo se il
 * file cambia. Senza rete ci si ferma e si riprova la volta dopo, senza
 * segnare niente. Una richiesta alla volta, con una pausa: LRCLIB è un
 * servizio gratuito e non va sommerso.
 */
class LibraryLyrics(
    private val scope: CoroutineScope,
    /** Dopo un po' di testi aggiunti: la libreria va riletta per saperlo. */
    private val onAdded: () -> Unit,
) {
    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val added: Int = 0,
        /** LRCLIB non risponde (occupato, o rete assente): si aspetta un po' e si riprende. */
        val waiting: Boolean = false,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val file = File(Settings.appDir, "lyrics-checked.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val checked: MutableMap<String, String> by lazy {
        runCatching { json.decodeFromString<Map<String, String>>(file.readText()) }.getOrDefault(emptyMap()).toMutableMap()
    }

    private var job: Job? = null
    /** La libreria è cambiata mentre si cercava: si ripassa alla fine. */
    @Volatile private var pending: List<Track>? = null

    fun request(tracks: List<Track>) {
        if (job?.isActive == true) {
            pending = tracks
            return
        }
        job = scope.launch(Dispatchers.IO) {
            var next: List<Track>? = tracks
            while (next != null) {
                pending = null
                if (!scan(next)) break
                next = pending
            }
        }
    }

    fun stop() {
        job?.cancel()
        _progress.value = _progress.value.copy(running = false)
    }

    private fun signature(path: String): String {
        val f = File(path)
        return "${f.length()}:${f.lastModified()}"
    }

    /** Cerca i testi per [tracks]; con LRCLIB occupato aspetta e riprende, non si ferma. */
    private suspend fun scan(tracks: List<Track>): Boolean {
        val todo = synchronized(checked) {
            tracks.filter { t ->
                t.durationMs > 0 && !t.hasLyrics && !t.hasLyricsFile() &&
                    checked[t.path.lowercase()] != "${t.size}:${t.modified}"
            }
        }
        if (todo.isEmpty()) return true
        var added = 0
        var sinceReload = 0
        _progress.value = Progress(running = true, total = todo.size)
        var i = 0
        var refused = 0
        while (i < todo.size) {
            coroutineContext.ensureActive()
            val track = todo[i]
            when (runCatching { AutoLyrics.lookup(track) }.getOrDefault(AutoLyrics.Outcome.UNAVAILABLE)) {
                AutoLyrics.Outcome.UNAVAILABLE -> {
                    // Il brano non si segna: si riprova. Dopo qualche rifiuto di
                    // fila (LRCLIB occupato, o la rete che manca) si fa una pausa.
                    if (++refused >= REFUSALS_BEFORE_PAUSE) {
                        save()
                        if (sinceReload > 0) {
                            sinceReload = 0
                            onAdded()
                        }
                        _progress.value = Progress(running = true, done = i, total = todo.size, added = added, waiting = true)
                        delay(BUSY_PAUSE_MS)
                        refused = 0
                    } else {
                        delay(PAUSE_MS * 10)
                    }
                    continue
                }
                AutoLyrics.Outcome.ADDED -> {
                    added++
                    sinceReload++
                    mark(track.path)
                }
                AutoLyrics.Outcome.NOT_FOUND -> mark(track.path)
            }
            refused = 0
            i++
            _progress.value = Progress(running = true, done = i, total = todo.size, added = added)
            if (i % 20 == 0) save()
            // Ogni tanto la libreria si rilegge, così i testi si vedono subito.
            if (sinceReload >= 25) {
                sinceReload = 0
                onAdded()
            }
            delay(PAUSE_MS)
        }
        save()
        if (sinceReload > 0) onAdded()
        _progress.value = Progress(running = false, done = todo.size, total = todo.size, added = added)
        return true
    }

    /** Il file com'è ora: se ha ricevuto il testo, dimensione e data sono cambiate. */
    private fun mark(path: String) {
        synchronized(checked) { checked[path.lowercase()] = signature(path) }
    }

    private fun save() {
        val copy = synchronized(checked) { checked.toMap() }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(copy))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        /** Fra un brano e l'altro: LRCLIB non va sommerso di richieste. */
        const val PAUSE_MS = 300L
        /** Quanti rifiuti di fila prima di una pausa lunga. */
        const val REFUSALS_BEFORE_PAUSE = 3
        /** La pausa quando LRCLIB non risponde: poi si riprende da dove si era. */
        const val BUSY_PAUSE_MS = 2 * 60_000L
    }
}
