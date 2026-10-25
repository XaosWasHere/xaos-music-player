package xaos.desktop.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xaos.desktop.library.LibrarySnapshot
import java.io.File

/**
 * Dove si era rimasti: la coda, il brano, il punto, casuale e ripeti. Si
 * salva a ogni cambio di brano, ogni pochi secondi mentre suona e alla
 * chiusura; alla riapertura la coda torna caricata, in pausa, e play riparte
 * da lì.
 *
 * La coda è salvata come percorsi dei file: si ritrova anche se la libreria
 * nel frattempo è stata riletta, e i brani che non ci sono più si saltano.
 */
class PlaybackMemory(private val file: File, private val player: Player, scope: CoroutineScope) {

    @Serializable
    private data class Saved(
        val paths: List<String>,
        val index: Int,
        val order: List<Int> = emptyList(),
        val positionMs: Long = 0,
        val shuffle: Boolean = false,
        val repeat: String = "OFF",
        val sourceKind: String? = null,
        val sourceId: String? = null,
        val sourceLabel: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var restored = false

    init {
        // Brano, coda, casuale o ripeti cambiati: si salva poco dopo.
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        scope.launch(Dispatchers.IO) {
            combine(player.current, player.queue, player.shuffle, player.repeat, player.isPlaying) { _, _, _, _, _ -> Unit }
                .debounce(SAVE_DELAY_MS)
                .collect { if (restored) save() }
        }
        // Mentre suona, anche il punto, ogni tanto.
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POSITION_EVERY_MS)
                if (restored && player.isPlaying.value) save()
            }
        }
    }

    /** Alla chiusura di Xaos. */
    fun saveNow() {
        if (restored) save()
    }

    /**
     * Ricarica la coda salvata, una volta sola, appena la libreria è letta.
     * Se nel frattempo si è già messo a suonare qualcosa non tocca niente.
     */
    fun restore(snapshot: LibrarySnapshot) {
        if (restored) return
        restored = true
        val saved = runCatching { json.decodeFromString<Saved>(file.readText()) }.getOrNull() ?: return
        val current = saved.paths.getOrNull(saved.index) ?: return
        val byPath = snapshot.byPath
        // I brani che non ci sono più si tolgono, e l'ordine di ascolto si rifà.
        val kept = saved.paths.mapIndexedNotNull { i, p -> byPath[p]?.let { i to it } }
        val track = byPath[current] ?: return
        val oldToNew = kept.withIndex().associate { (newIndex, pair) -> pair.first to newIndex }
        val order = saved.order.mapNotNull { oldToNew[it] }
        val source = saved.sourceKind?.let { kind ->
            runCatching { PlaySource(PlaySource.Kind.valueOf(kind), saved.sourceId.orEmpty(), saved.sourceLabel.orEmpty()) }.getOrNull()
        }
        player.restore(
            Player.Resume(
                queue = kept.map { it.second },
                index = kept.indexOfFirst { it.second.path == track.path }.coerceAtLeast(0),
                order = order,
                positionMs = saved.positionMs.coerceIn(0, (track.durationMs - 1_000).coerceAtLeast(0)),
                shuffle = saved.shuffle,
                repeat = runCatching { RepeatMode.valueOf(saved.repeat) }.getOrDefault(RepeatMode.OFF),
                source = source,
            )
        )
    }

    private fun save() {
        val state = player.resumeState() ?: return
        val saved = Saved(
            paths = state.queue.map { it.path },
            index = state.index,
            order = state.order,
            positionMs = state.positionMs,
            shuffle = state.shuffle,
            repeat = state.repeat.name,
            sourceKind = state.source?.kind?.name,
            sourceId = state.source?.id,
            sourceLabel = state.source?.label,
        )
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(saved))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 800L
        const val POSITION_EVERY_MS = 5_000L
    }
}
