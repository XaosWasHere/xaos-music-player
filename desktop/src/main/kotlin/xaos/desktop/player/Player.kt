package xaos.desktop.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import xaos.desktop.library.Track
import java.net.URI

enum class RepeatMode { OFF, ALL, ONE }

/**
 * La riproduzione, affidata al VLC installato sul PC.
 *
 * VLC legge qualunque formato abbia la libreria (FLAC compreso, che le librerie
 * Java pure non gestiscono), e vlcj lo pilota senza finestre. Se VLC manca il
 * player resta [available] = false e l'interfaccia lo dice, invece di fallire
 * in silenzio al primo play.
 *
 * Gli eventi di VLC arrivano su un suo thread: da lì non si può comandare il
 * player direttamente, quindi il passaggio al brano successivo passa da
 * `submit`, che lo esegue fuori dal callback.
 */
class Player {

    val available: Boolean = runCatching { NativeDiscovery().discover() }.getOrDefault(false)

    private val factory: MediaPlayerFactory? =
        if (available) runCatching { MediaPlayerFactory("--no-video", "--quiet") }.getOrNull() else null
    private val mp: MediaPlayer? = factory?.mediaPlayers()?.newMediaPlayer()

    /** La versione di VLC in uso, per la schermata Informazioni. */
    val vlcVersion: String? = runCatching { factory?.application()?.version() }.getOrNull()

    // ---------------------------------------------------------- equalizzatore

    /** Le frequenze centrali delle bande, in Hz, come le espone VLC. */
    val eqBands: List<Float> = runCatching { factory?.equalizer()?.bands() }.getOrNull().orEmpty()

    /** I preset di VLC ("Flat", "Rock", "Classical"…). */
    val eqPresets: List<String> = runCatching { factory?.equalizer()?.presets() }.getOrNull().orEmpty()

    private val equalizer = runCatching { factory?.equalizer()?.newEqualizer() }.getOrNull()

    /** Preamp e bande di un preset, per mostrarli e poi ritoccarli a mano. */
    fun presetValues(name: String): Pair<Float, List<Float>>? = runCatching {
        val eq = factory?.equalizer()?.newEqualizer(name) ?: return null
        eq.preamp() to eq.amps().toList()
    }.getOrNull()

    /**
     * Applica l'equalizzatore. vlcj ricalcola il filtro a ogni modifica
     * dell'oggetto, quindi basta aggiornarne i valori; spento, lo si stacca.
     */
    fun applyEqualizer(enabled: Boolean, preamp: Float, bands: List<Float>) {
        val eq = equalizer ?: return
        val player = mp ?: return
        eq.setPreamp(preamp.coerceIn(-20f, 20f))
        bands.forEachIndexed { i, v -> if (i < eq.bandCount()) eq.setAmp(i, v.coerceIn(-20f, 20f)) }
        player.audio().setEqualizer(if (enabled) eq else null)
    }

    // ---------------------------------------------------------- uscita audio

    /** Le uscite audio disponibili: identificativo e nome leggibile. */
    fun outputDevices(): List<Pair<String, String>> = runCatching {
        mp?.audio()?.outputDevices()?.map { it.deviceId to it.longName }
    }.getOrNull().orEmpty().filter { it.first.isNotBlank() }

    private var outputDevice: String? = null

    /** null = l'uscita predefinita di Windows. Vale dal brano in corso in poi. */
    fun setOutputDevice(id: String?) {
        outputDevice = id
        mp?.audio()?.setOutputDevice(null, id ?: "")
    }

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(80)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeat = MutableStateFlow(RepeatMode.OFF)
    val repeat: StateFlow<RepeatMode> = _repeat.asStateFlow()

    private val _upNext = MutableStateFlow<List<Track>>(emptyList())
    /** I prossimi brani, nell'ordine in cui verranno davvero suonati. */
    val upNext: StateFlow<List<Track>> = _upNext.asStateFlow()

    /** L'ordine di ascolto: gli indici della coda, mescolati se c'è il casuale. */
    private var order: List<Int> = emptyList()
    private var cursor = -1

    init {
        mp?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) { _isPlaying.value = true }
            override fun paused(mediaPlayer: MediaPlayer) { _isPlaying.value = false }
            override fun stopped(mediaPlayer: MediaPlayer) { _isPlaying.value = false }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) { _positionMs.value = newTime }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength > 0) _durationMs.value = newLength
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                mediaPlayer.submit { advance(automatic = true) }
            }
            override fun error(mediaPlayer: MediaPlayer) {
                // Un file che VLC non riesce ad aprire si salta, come farebbe
                // qualunque player: fermarsi lì lascerebbe la coda appesa.
                mediaPlayer.submit { advance(automatic = true) }
            }
        })
    }

    fun play(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        _queue.value = tracks
        rebuildOrder(startIndex.coerceIn(tracks.indices))
        startCurrent()
    }

    fun togglePlayPause() {
        val player = mp ?: return
        when {
            _current.value == null -> _queue.value.takeIf { it.isNotEmpty() }?.let { play(it, 0) }
            player.status().isPlaying -> player.controls().pause()
            else -> player.controls().play()
        }
    }

    fun next() = advance(automatic = false)

    /** Come ovunque: oltre i primi tre secondi "indietro" riavvolge il brano. */
    fun previous() {
        val player = mp ?: return
        if (_positionMs.value > RESTART_THRESHOLD_MS || cursor <= 0) {
            player.controls().setTime(0)
            _positionMs.value = 0
            return
        }
        cursor -= 1
        startCurrent()
    }

    fun seekTo(fraction: Float) {
        val player = mp ?: return
        val duration = _durationMs.value
        if (duration <= 0) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        player.controls().setTime(target)
        _positionMs.value = target
    }

    /** Avanti o indietro di [deltaMs] nel brano in corso. */
    fun seekBy(deltaMs: Long) {
        val player = mp ?: return
        val duration = _durationMs.value
        if (duration <= 0) return
        val target = (_positionMs.value + deltaMs).coerceIn(0, duration - 500)
        player.controls().setTime(target)
        _positionMs.value = target
    }

    fun setVolume(percent: Int) {
        val v = percent.coerceIn(0, 100)
        _volume.value = v
        mp?.audio()?.setVolume(v)
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
        val playing = order.getOrNull(cursor) ?: 0
        rebuildOrder(playing)
    }

    fun cycleRepeat() {
        _repeat.value = when (_repeat.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun release() {
        runCatching { mp?.release() }
        runCatching { factory?.release() }
    }

    /**
     * Ricostruisce l'ordine di ascolto mettendo in testa il brano [first]:
     * attivare il casuale non deve interrompere né ripetere quello in corso.
     */
    private fun rebuildOrder(first: Int) {
        val indices = _queue.value.indices.toList()
        order = if (_shuffle.value) listOf(first) + (indices - first).shuffled() else indices
        cursor = order.indexOf(first).coerceAtLeast(0)
        refreshUpNext()
    }

    private fun advance(automatic: Boolean) {
        if (order.isEmpty()) return
        if (automatic && _repeat.value == RepeatMode.ONE) {
            startCurrent()
            return
        }
        val nextCursor = cursor + 1
        when {
            nextCursor < order.size -> cursor = nextCursor
            _repeat.value == RepeatMode.ALL -> {
                if (_shuffle.value) rebuildOrder(order.random()) else cursor = 0
            }
            else -> {
                // Fine della coda: ci si ferma sull'ultimo brano, riavvolto.
                mp?.controls()?.stop()
                _positionMs.value = 0
                return
            }
        }
        startCurrent()
    }

    private fun startCurrent() {
        val player = mp ?: return
        val track = _queue.value.getOrNull(order.getOrNull(cursor) ?: return) ?: return
        _current.value = track
        refreshUpNext()
        _positionMs.value = 0
        _durationMs.value = track.durationMs
        player.media().play(mrlOf(track))
        player.audio().setVolume(_volume.value)
        // VLC dimentica l'uscita scelta a ogni nuovo media: la si riapplica.
        outputDevice?.let { player.audio().setOutputDevice(null, it) }
    }

    private fun refreshUpNext() {
        val queue = _queue.value
        _upNext.value = order.drop(cursor + 1).take(UP_NEXT).mapNotNull { queue.getOrNull(it) }
    }

    /**
     * Un percorso Windows come MRL di VLC: `file:///C:/...` con i caratteri
     * speciali codificati. Passare il percorso nudo fallisce su accenti e
     * simboli nei nomi dei file.
     */
    private fun mrlOf(track: Track): String =
        URI("file", "", "/" + track.path.replace('\\', '/'), null).toASCIIString()

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
        const val UP_NEXT = 3
    }
}
