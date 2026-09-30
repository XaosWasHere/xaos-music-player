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
 * Da dove arriva la coda in riproduzione: l'album, la playlist, i preferiti…
 * È quello che mostra "In riproduzione da" e dove porta un clic lì sopra.
 */
data class PlaySource(val kind: Kind, val id: String, val label: String) {
    enum class Kind { ALBUM, PLAYLIST, FAVORITES, SONGS, ARTIST, SEARCH, STATS }
}

/**
 * La riproduzione, affidata a VLC.
 *
 * VLC legge qualunque formato abbia la libreria (FLAC compreso, che le librerie
 * Java pure non gestiscono), e vlcj lo pilota senza finestre. Di norma si usa
 * il VLC che viaggia dentro Xaos; se sul PC ce n'è uno installato e quello
 * incluso manca, si usa quello.
 *
 * Il motore si avvia con [start], su un thread a parte: caricare VLC può
 * richiedere qualche secondo, e la finestra non deve aspettarlo. Finché non è
 * pronto [engine] vale [Engine.Starting]; volume, equalizzatore e uscita audio
 * chiesti nel frattempo si applicano appena arriva.
 *
 * Gli eventi di VLC arrivano su un suo thread: da lì non si può comandare il
 * player direttamente, quindi il passaggio al brano successivo passa da
 * `submit`, che lo esegue fuori dal callback.
 */
class Player {

    enum class Engine { Starting, Ready, Missing }

    private val _engine = MutableStateFlow(Engine.Starting)
    val engine: StateFlow<Engine> = _engine.asStateFlow()

    /** true quando VLC è carico e pronto a suonare. */
    val available: Boolean get() = _engine.value == Engine.Ready

    @Volatile private var factory: MediaPlayerFactory? = null
    @Volatile private var mp: MediaPlayer? = null
    @Volatile private var equalizer: uk.co.caprica.vlcj.player.base.Equalizer? = null

    /** La versione di VLC in uso, per la schermata Informazioni. */
    @Volatile var vlcVersion: String? = null
        private set

    /** Da dove è stato caricato VLC: quello di Xaos o quello installato. */
    @Volatile var vlcPath: String? = null
        private set

    /**
     * Carica VLC su un thread a parte. [bundledDir] è la cartella del VLC incluso
     * in Xaos: se c'è, ha la precedenza su quello installato.
     */
    fun start(bundledDir: java.io.File?) {
        Thread({
            val found = runCatching {
                val discovery = NativeDiscovery(*strategies(bundledDir))
                val ok = discovery.discover()
                if (ok) vlcPath = discovery.discoveredPath()
                ok
            }.getOrDefault(false)
            val f = if (found) runCatching { MediaPlayerFactory("--no-video", "--quiet") }.getOrNull() else null
            val player = f?.mediaPlayers()?.newMediaPlayer()
            if (f == null || player == null) {
                _engine.value = Engine.Missing
                return@Thread
            }
            factory = f
            vlcVersion = runCatching { f.application().version() }.getOrNull()
            eqBands = runCatching { f.equalizer().bands() }.getOrNull().orEmpty()
            eqPresets = runCatching { f.equalizer().presets() }.getOrNull().orEmpty()
            equalizer = runCatching { f.equalizer().newEqualizer() }.getOrNull()
            player.events().addMediaPlayerEventListener(listener)
            mp = player
            player.audio().setVolume(_volume.value)
            pendingEq?.let { (on, pre, bands) -> applyEqualizer(on, pre, bands) }
            outputDevice?.let { player.audio().setOutputDevice(null, it) }
            _engine.value = Engine.Ready
        }, "xaos-vlc-init").apply { isDaemon = true }.start()
    }

    /**
     * Prima il VLC incluso, cercato solo nella sua cartella; poi la ricerca
     * normale di vlcj (registro di Windows, Program Files, PATH).
     */
    private fun strategies(bundledDir: java.io.File?): Array<uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy> {
        val bundled = bundledDir?.takeIf { java.io.File(it, "libvlc.dll").isFile }?.let { dir ->
            object : uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy {
                override fun supported() = true
                override fun discover(): String = dir.absolutePath
                override fun onFound(path: String) = true
                // VLC trova i plugin tramite questa variabile, letta quando la
                // libreria si carica: va impostata nel processo prima di allora.
                override fun onSetPluginPath(path: String): Boolean =
                    uk.co.caprica.vlcj.binding.lib.LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=" + java.io.File(path, "plugins").path) == 0
            }
        }
        return listOfNotNull(bundled, uk.co.caprica.vlcj.factory.discovery.strategy.WindowsNativeDiscoveryStrategy())
            .toTypedArray()
    }

    // ---------------------------------------------------------- analisi

    private val analysisLock = Any()
    @Volatile private var analyzer: MediaPlayer? = null

    /**
     * Converte [source] in un WAV mono a bassa frequenza in [target], per
     * disegnarne le onde. Usa un secondo lettore dello stesso VLC, che scrive
     * sul file invece che sulle casse: non si sente nulla e la riproduzione
     * non viene disturbata. Un brano alla volta.
     */
    fun decodeForWaveform(source: java.io.File, target: java.io.File, sampleRate: Int): Boolean = synchronized(analysisLock) {
        val f = factory ?: return false
        val analysis = analyzer ?: f.mediaPlayers().newMediaPlayer().also { analyzer = it }
        val done = java.util.concurrent.CountDownLatch(1)
        val ended = object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer) = done.countDown()
            override fun error(mediaPlayer: MediaPlayer) = done.countDown()
        }
        analysis.events().addMediaPlayerEventListener(ended)
        return try {
            target.delete()
            val dst = target.path.replace('\\', '/')
            val started = analysis.media().play(
                mrlOf(source.path),
                ":sout=#transcode{acodec=s16l,channels=1,samplerate=$sampleRate}:std{access=file,mux=wav,dst=\"$dst\"}",
                ":no-sout-video",
            )
            started && done.await(90, java.util.concurrent.TimeUnit.SECONDS) && target.length() > 44
        } catch (_: Throwable) {
            false
        } finally {
            analysis.events().removeMediaPlayerEventListener(ended)
            runCatching { analysis.controls().stop() }
        }
    }

    // ---------------------------------------------------------- equalizzatore

    /** Le frequenze centrali delle bande, in Hz, come le espone VLC. */
    @Volatile var eqBands: List<Float> = emptyList()
        private set

    /** I preset di VLC ("Flat", "Rock", "Classical"…). */
    @Volatile var eqPresets: List<String> = emptyList()
        private set

    @Volatile private var pendingEq: Triple<Boolean, Float, List<Float>>? = null

    /** Preamp e bande di un preset, per mostrarli e poi ritoccarli a mano. */
    fun presetValues(name: String): Pair<Float, List<Float>>? = runCatching {
        val eq = factory?.equalizer()?.newEqualizer(name) ?: return null
        eq.preamp() to eq.amps().toList()
    }.getOrNull()

    /**
     * Applica l'equalizzatore. vlcj ricalcola il filtro a ogni modifica
     * dell'oggetto, quindi basta aggiornarne i valori; spento, lo si stacca.
     * Prima che VLC sia pronto la richiesta si conserva e si applica dopo.
     */
    fun applyEqualizer(enabled: Boolean, preamp: Float, bands: List<Float>) {
        pendingEq = Triple(enabled, preamp, bands)
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

    @Volatile private var outputDevice: String? = null

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

    /**
     * Chiamato una volta per brano, quando è stato ascoltato davvero: trenta
     * secondi, o tutto se è più corto. È la stessa regola del telefono, così
     * le statistiche dei due si possono sommare.
     */
    @Volatile var onListened: ((Track, Long) -> Unit)? = null
    @Volatile private var listenedMs = 0L
    @Volatile private var lastTimeMs = 0L
    @Volatile private var listenRecorded = false

    /** L'ordine di ascolto: gli indici della coda, mescolati se c'è il casuale. */
    private var order: List<Int> = emptyList()
    private var cursor = -1

    private val listener = object : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) { _isPlaying.value = true }
        override fun paused(mediaPlayer: MediaPlayer) { _isPlaying.value = false }
        override fun stopped(mediaPlayer: MediaPlayer) { _isPlaying.value = false }
        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            _positionMs.value = newTime
            // Solo il tempo che scorre suonando: un salto in avanti non è ascolto.
            val delta = newTime - lastTimeMs
            lastTimeMs = newTime
            if (listenRecorded || delta !in 1..MAX_TICK_MS) return
            listenedMs += delta
            val track = _current.value ?: return
            val needed = if (track.durationMs in 1 until PLAY_THRESHOLD_MS) track.durationMs - 1_000 else PLAY_THRESHOLD_MS
            if (listenedMs >= needed) {
                listenRecorded = true
                onListened?.invoke(track, System.currentTimeMillis())
            }
        }
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
    }

    private val _source = MutableStateFlow<PlaySource?>(null)
    val source: StateFlow<PlaySource?> = _source.asStateFlow()

    fun play(tracks: List<Track>, startIndex: Int, source: PlaySource? = null) {
        if (tracks.isEmpty()) return
        _source.value = source
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

    /** Salta a [ms] nel brano in corso: il clic su una riga del testo. */
    fun seekToMs(ms: Long) {
        val player = mp ?: return
        val duration = _durationMs.value
        val target = if (duration > 0) ms.coerceIn(0, duration - 500) else ms.coerceAtLeast(0)
        player.controls().setTime(target)
        _positionMs.value = target
        if (!player.status().isPlaying) player.controls().play()
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
        runCatching { analyzer?.release() }
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
        listenedMs = 0
        lastTimeMs = 0
        listenRecorded = false
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
    private fun mrlOf(track: Track): String = mrlOf(track.path)

    private fun mrlOf(path: String): String =
        URI("file", "", "/" + path.replace('\\', '/'), null).toASCIIString()

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
        const val PLAY_THRESHOLD_MS = 30_000L
        /** VLC aggiorna la posizione più volte al secondo: oltre questo è un salto. */
        const val MAX_TICK_MS = 1_500L
        const val UP_NEXT = 3
    }
}
