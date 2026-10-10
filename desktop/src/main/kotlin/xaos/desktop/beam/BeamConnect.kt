package xaos.desktop.beam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import xaos.desktop.library.Track
import xaos.desktop.player.PlaySource
import xaos.desktop.player.Player

/** Il brano in corso su un dispositivo, quanto basta per mostrarlo. */
@Serializable
data class NowTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    /** L'id del brano nel catalogo del PC, se il PC ce l'ha: serve per la copertina e per spostare l'ascolto. */
    val pcId: Long? = null,
)

/** Cosa sta facendo il PC: il telefono lo mostra e lo può prendere in mano. */
@Serializable
data class PcNow(
    val rev: Long,
    val track: NowTrack? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val queue: List<Long> = emptyList(),
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeat: String = "OFF",
    val volume: Int = 0,
    /** L'impronta del catalogo: quando cambia, il telefono lo riscarica. */
    val catalog: String = "",
)

/** Cosa sta facendo il telefono: lo manda lui, quando cambia e ogni tanto. */
@Serializable
data class PhoneNow(
    val track: NowTrack? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    /** La coda, come id del catalogo del PC (null per i brani che il PC non ha). */
    val queue: List<Long?> = emptyList(),
    val index: Int = -1,
)

/**
 * Un comando da un dispositivo all'altro: play, pause, toggle, next,
 * previous, seek ([ms]), volume ([value]), queue ([ids] da [index] a [ms]).
 */
@Serializable
data class BeamCommand(
    val action: String,
    val ms: Long? = null,
    val value: Int? = null,
    val ids: List<Long>? = null,
    val index: Int? = null,
)

/** La risposta all'attesa del telefono: lo stato del PC e i comandi per lui. */
@Serializable
data class BeamSession(val pc: PcNow, val commands: List<BeamCommand> = emptyList())

/** Un telefono collegato, con quello che sta suonando. */
data class ConnectedPhone(val name: String, val now: PhoneNow, val seenAt: Long, val receivedAt: Long) {
    /** La posizione adesso, contando il tempo passato dall'ultimo aggiornamento. */
    fun positionNow(at: Long = System.currentTimeMillis()): Long =
        if (now.playing) now.positionMs + (at - receivedAt) else now.positionMs
}

/**
 * Xaos Beam Connect, il lato del PC: come Spotify Connect, ogni dispositivo
 * vede cosa suona l'altro, lo comanda, e sposta l'ascolto da una parte
 * all'altra.
 *
 * Il telefono non apre porte (su Android un server in ascolto vive male):
 * è lui a cercare il PC. Manda il suo stato con [updatePhone] e resta in
 * attesa con [await], che torna appena il PC cambia brano o stato, o appena
 * c'è un comando per lui; altrimenti dopo una ventina di secondi, e il
 * telefono richiede. Così un comando dato dal PC arriva subito, senza che il
 * telefono debba chiedere di continuo.
 */
class BeamConnect(
    private val player: Player,
    scope: CoroutineScope,
    /** Il catalogo come lo vede il telefono: id ↔ brano. */
    private val tracksById: () -> Map<Long, Track>,
    private val catalogTag: () -> String,
) {
    private val lock = Object()
    @Volatile private var rev = 1L
    private val pending = HashMap<String, MutableList<BeamCommand>>()

    private val _phones = MutableStateFlow<Map<String, ConnectedPhone>>(emptyMap())
    /** I telefoni che si sono fatti vivi di recente, con quello che suonano. */
    val phones: StateFlow<Map<String, ConnectedPhone>> = _phones.asStateFlow()

    private val _activePhone = MutableStateFlow<ConnectedPhone?>(null)
    /**
     * Il telefono che è il dispositivo attivo, come su Spotify: quello che
     * suona mentre il PC è fermo. Resta lui anche se va in pausa, finché non
     * riparte il PC; se il PC non ha niente in coda, vale anche un telefono
     * fermo con un brano caricato.
     */
    val activePhone: StateFlow<ConnectedPhone?> = _activePhone.asStateFlow()

    init {
        // Ogni cambio di brano o di stato sveglia chi aspetta.
        scope.launch {
            combine(player.current, player.isPlaying, player.queue, player.shuffle, player.repeat) { _, _, _, _, _ -> Unit }
                .collect { bump() }
        }
        scope.launch { player.volume.collect { bump() } }
        // Un brano scelto sul PC mentre suona il telefono va al telefono.
        player.redirectPlay = { tracks, index ->
            val phone = _activePhone.value
            if (phone == null) false else {
                sendToPhone(phone.name, BeamCommand("queue", ms = 0, ids = tracks.map { BeamServer.idOf(it.path) }, index = index))
                true
            }
        }
        scope.launch {
            combine(player.isPlaying, player.current, _phones) { playing, current, phones ->
                val playingPhone = phones.values.firstOrNull { it.now.playing && it.now.track != null }?.name
                val name = when {
                    playing -> null
                    playingPhone != null -> playingPhone
                    else -> _activePhone.value?.name?.takeIf { phones[it]?.now?.track != null }
                        ?: if (current == null) phones.values.firstOrNull { it.now.track != null }?.name else null
                }
                name?.let { phones[it] }
            }.collect { _activePhone.value = it }
        }
        // Un salto nel brano non ha un evento suo: lo si riconosce da una
        // posizione che non torna con il tempo trascorso.
        scope.launch(Dispatchers.Default) {
            var lastPos = player.positionMs.value
            var lastAt = System.currentTimeMillis()
            while (isActive) {
                delay(1_000)
                val now = System.currentTimeMillis()
                val pos = player.positionMs.value
                val expected = if (player.isPlaying.value) lastPos + (now - lastAt) else lastPos
                if (kotlin.math.abs(pos - expected) > SEEK_JUMP_MS) bump()
                lastPos = pos
                lastAt = now
                // Chi non si fa vivo da un po' non è più collegato.
                val stale = _phones.value.filterValues { now - it.seenAt > PHONE_TIMEOUT_MS }.keys
                if (stale.isNotEmpty()) _phones.value = _phones.value - stale
            }
        }
    }

    /** Qualcosa che il telefono deve sapere è cambiato (la libreria, per esempio). */
    fun notifyChanged() = bump()

    private fun bump() {
        synchronized(lock) {
            rev++
            lock.notifyAll()
        }
    }

    /** Lo stato del PC adesso. */
    fun pcNow(): PcNow {
        val current = player.current.value
        val queue = player.queue.value
        return PcNow(
            rev = rev,
            track = current?.let { nowTrackOf(it) },
            playing = player.isPlaying.value,
            positionMs = player.positionMs.value,
            queue = queue.map { BeamServer.idOf(it.path) },
            index = current?.let { c -> queue.indexOfFirst { it.path == c.path } } ?: -1,
            shuffle = player.shuffle.value,
            repeat = player.repeat.value.name,
            volume = player.volume.value,
            catalog = catalogTag(),
        )
    }

    /**
     * L'attesa del telefono: torna subito se il PC è cambiato da [knownRev] o
     * se ci sono comandi per [device], altrimenti appena succede, al più dopo
     * [WAIT_MS].
     */
    fun await(device: String, knownRev: Long): BeamSession {
        seen(device)
        val deadline = System.currentTimeMillis() + WAIT_MS
        synchronized(lock) {
            while (true) {
                val commands = pending[device]
                if (rev != knownRev || !commands.isNullOrEmpty()) break
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                lock.wait(left)
            }
            val commands = pending.remove(device).orEmpty()
            return BeamSession(pcNow(), commands)
        }
    }

    fun updatePhone(device: String, now: PhoneNow) {
        val at = System.currentTimeMillis()
        _phones.value = _phones.value + (device to ConnectedPhone(device, now, at, at))
    }

    private fun seen(device: String) {
        _phones.value[device]?.let { _phones.value = _phones.value + (device to it.copy(seenAt = System.currentTimeMillis())) }
    }

    /** Un comando per il telefono: lo riceve alla sua prossima attesa, cioè subito. */
    fun sendToPhone(device: String, command: BeamCommand) {
        synchronized(lock) {
            pending.getOrPut(device) { mutableListOf() } += command
            lock.notifyAll()
        }
    }

    /** Un comando dal telefono per il player del PC. */
    fun execute(command: BeamCommand) {
        when (command.action) {
            "play" -> if (!player.isPlaying.value) player.togglePlayPause()
            "pause" -> if (player.isPlaying.value) player.togglePlayPause()
            "toggle" -> player.togglePlayPause()
            "next" -> player.next()
            "previous" -> player.previous()
            "seek" -> command.ms?.let(player::seekToMs)
            "volume" -> command.value?.let(player::setVolume)
            "shuffle" -> player.toggleShuffle()
            "repeat" -> player.cycleRepeat()
            "queue" -> playQueue(command.ids.orEmpty(), command.index ?: 0, command.ms ?: 0)
        }
    }

    /** Suona sul PC la coda arrivata dal telefono, dal brano e dal punto in cui era. */
    private fun playQueue(ids: List<Long>, index: Int, positionMs: Long) {
        val byId = tracksById()
        val wanted = ids.getOrNull(index)
        val tracks = ids.mapNotNull { byId[it] }
        if (tracks.isEmpty()) return
        val start = wanted?.let { w -> tracks.indexOfFirst { BeamServer.idOf(it.path) == w } }?.takeIf { it >= 0 } ?: 0
        player.playHere(tracks, start, PlaySource(PlaySource.Kind.SONGS, "beam", "DAL TELEFONO"))
        if (positionMs > SEEK_JUMP_MS) seekWhenReady(positionMs)
    }

    /** VLC accetta il salto solo quando il brano è partito davvero. */
    private fun seekWhenReady(positionMs: Long) {
        Thread({
            val until = System.currentTimeMillis() + 5_000
            while (!player.isPlaying.value && System.currentTimeMillis() < until) Thread.sleep(50)
            Thread.sleep(150)
            player.seekToMs(positionMs)
        }, "xaos-beam-seek").apply { isDaemon = true }.start()
    }

    /** Sposta l'ascolto del PC sul telefono: lui riprende da qui, il PC si ferma. */
    fun transferToPhone(device: String) {
        val now = pcNow()
        if (now.track == null || now.queue.isEmpty()) return
        sendToPhone(device, BeamCommand("queue", ms = player.positionMs.value, ids = now.queue, index = now.index.coerceAtLeast(0)))
        if (player.isPlaying.value) player.togglePlayPause()
    }

    /** Prende sul PC quello che suona il telefono: il telefono si ferma. */
    fun takeFromPhone(device: String) {
        val phone = _phones.value[device] ?: return
        val ids = phone.now.queue
        val index = phone.now.index
        // I brani che il PC non ha restano fuori; l'indice va ricalcolato.
        val known = ids.withIndex().filter { it.value != null }
        val start = known.indexOfFirst { it.index == index }.takeIf { it >= 0 } ?: 0
        if (known.isEmpty()) return
        playQueue(known.map { it.value!! }, start, phone.positionNow())
        sendToPhone(device, BeamCommand("pause"))
    }

    private fun nowTrackOf(track: Track) = NowTrack(
        title = track.title,
        artist = track.artist,
        album = track.album,
        durationMs = track.durationMs,
        pcId = BeamServer.idOf(track.path),
    )

    companion object {
        const val WAIT_MS = 20_000L
        const val SEEK_JUMP_MS = 2_500L
        /** Un telefono che non si fa vivo da tanto è uscito da Xaos o dalla rete. */
        const val PHONE_TIMEOUT_MS = 45_000L
    }
}
