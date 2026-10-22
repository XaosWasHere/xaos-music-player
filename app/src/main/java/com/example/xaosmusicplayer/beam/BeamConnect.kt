package com.example.xaosmusicplayer.beam

import com.example.xaosmusicplayer.data.MusicRepository
import com.example.xaosmusicplayer.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Xaos Beam Connect, il lato del telefono: come Spotify Connect, si vede cosa
 * suona il PC, lo si comanda, e l'ascolto passa da una parte all'altra.
 *
 * Il telefono non apre porte: resta in attesa sul PC (una richiesta che il PC
 * tiene aperta finché non cambia qualcosa, o finché non ha un comando per
 * noi), e gli manda il proprio stato quando cambia e ogni dieci secondi.
 * Tutto parte da solo quando Beam è collegato e si ferma quando non lo è.
 */
object BeamConnect {

    /** Cosa sta facendo il PC, com'era quando è arrivato. */
    data class PcNow(
        val rev: Long,
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
        /** L'id nel catalogo del PC del brano in corso. */
        val pcId: Long?,
        val playing: Boolean,
        val positionMs: Long,
        val queue: List<Long>,
        val index: Int,
        val receivedAt: Long,
        val shuffle: Boolean = false,
        /** OFF, ALL o ONE. */
        val repeat: String = "OFF",
    ) {
        val hasTrack: Boolean get() = title != null

        /** La posizione adesso, contando il tempo passato da quando è arrivata. */
        fun positionNow(at: Long = System.currentTimeMillis()): Long {
            val p = if (playing) positionMs + (at - receivedAt) else positionMs
            return if (durationMs > 0) p.coerceAtMost(durationMs) else p
        }
    }

    /** Un comando arrivato dal PC per il player del telefono. */
    data class Command(
        val action: String,
        val ms: Long? = null,
        val value: Int? = null,
        val ids: List<Long> = emptyList(),
        val index: Int = 0,
        /** Il numero d'ordine: il PC lo rimanda finché non diciamo di averlo ricevuto. */
        val seq: Long = 0,
    )

    /** L'ultimo comando del PC ricevuto ed eseguito: lo si conferma, e non si ripete. */
    @Volatile private var lastSeq = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    private var reportJob: Job? = null
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private var attached = false

    private val _pc = MutableStateFlow<PcNow?>(null)
    /** Lo stato del PC; null se il PC non è collegato. */
    val pc: StateFlow<PcNow?> = _pc.asStateFlow()

    /** Se il telefono sta suonando: lo aggiornano il player (dal widget) e l'app. */
    val localPlaying = MutableStateFlow(false)

    private val _remoteActive = MutableStateFlow(false)
    /**
     * Se il dispositivo attivo è il PC, come su Spotify: diventa vero quando il
     * PC suona e il telefono no, torna falso quando riparte il telefono. Fra
     * le due cose resta com'era: mettere in pausa il PC non riporta la musica
     * sul telefono. Lo usano l'app e il widget, così dicono la stessa cosa.
     */
    val remoteActive: StateFlow<Boolean> = _remoteActive.asStateFlow()

    /** Chi esegue i comandi del PC sul player del telefono (il ViewModel). */
    @Volatile var commandHandler: ((Command) -> Unit)? = null

    /** Lo stato del telefono da mandare al PC, nel formato del protocollo. */
    @Volatile var phoneState: (() -> JSONObject?)? = null

    /** Si aggancia a Beam: parte quando il PC è collegato, si ferma quando non lo è. */
    @Synchronized
    internal fun attach() {
        if (attached) return
        attached = true
        scope.launch {
            Beam.status.collect { status ->
                if (status is Beam.Status.Connected) start() else stop()
            }
        }
        scope.launch {
            kotlinx.coroutines.flow.combine(localPlaying, _pc) { local, pc -> local to pc }.collect { (local, pc) ->
                _remoteActive.value = when {
                    pc == null || !pc.hasTrack -> false
                    local -> false
                    pc.playing -> true
                    else -> _remoteActive.value
                }
            }
        }
    }

    /**
     * L'app è tornata in primo piano: l'attesa sul PC può essere morta mentre
     * era in background (Android la congela, il Wi-Fi si riconnette), e
     * accorgersene solo al timeout vorrebbe dire stato vecchio e comandi in
     * ritardo. Si ricomincia subito, e il PC riceve lo stato del telefono.
     */
    fun wake() {
        if (Beam.status.value !is Beam.Status.Connected) {
            Beam.refresh()
            return
        }
        sessionJob?.cancel()
        sessionJob = scope.launch { sessionLoop() }
        if (reportJob?.isActive != true) reportJob = scope.launch { reportLoop() }
        phoneChanged()
    }

    /** Lo stato del telefono è cambiato: il PC lo sa subito. */
    fun phoneChanged() {
        changes.trySend(Unit)
    }

    /** Un comando per il player del PC. */
    fun control(
        action: String,
        ms: Long? = null,
        value: Int? = null,
        ids: List<Long>? = null,
        index: Int? = null,
    ) {
        val pairing = Beam.pairing.value ?: return
        expect(action, ms)
        scope.launch {
            runCatching {
                val body = JSONObject().put("action", action).apply {
                    ms?.let { put("ms", it) }
                    value?.let { put("value", it) }
                    ids?.let { put("ids", JSONArray(it)) }
                    index?.let { put("index", it) }
                }
                val conn = Beam.open("${pairing.baseUrl}/control", pairing.token).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                try {
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    if (conn.responseCode == 200) {
                        val reply = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                        // La risposta arriva insieme agli aggiornamenti dell'attesa, e
                        // può essere più vecchia di loro (un "queue" risponde prima del
                        // salto al punto giusto): vale solo se è più recente.
                        val fresh = parsePc(JSONObject(reply))
                        _pc.update { current -> if (current == null || fresh.rev > current.rev) fresh else current }
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    /**
     * Play, pausa e salti si vedono subito, senza aspettare che il PC
     * risponda: su un Wi-Fi lento la conferma può metterci un secondo, e un
     * pulsante che non reagisce sembra rotto. Lo stato vero del PC, quando
     * arriva, prende comunque il posto di questo.
     */
    private fun expect(action: String, ms: Long?) {
        val now = System.currentTimeMillis()
        _pc.update { current ->
            current ?: return@update null
            when (action) {
                "toggle", "play", "pause" -> {
                    val playing = when (action) {
                        "toggle" -> !current.playing
                        "play" -> true
                        else -> false
                    }
                    current.copy(playing = playing, positionMs = current.positionNow(now), receivedAt = now)
                }
                "seek" -> ms?.let { current.copy(positionMs = it, receivedAt = now) } ?: current
                else -> current
            }
        }
    }

    /** La copertina di un brano del PC, servita dal PC. */
    fun artworkUrl(pcId: Long): String? {
        val pairing = Beam.pairing.value ?: return null
        return "${pairing.baseUrl}/art/$pcId?s=512&a=${Beam.artKeyOf(pairing.token)}"
    }

    /**
     * L'id nel catalogo del PC di un brano del telefono: quello del PC se è
     * un brano del PC, altrimenti lo stesso brano riconosciuto da titolo,
     * artista e durata. Null se il PC non ce l'ha.
     */
    fun pcIdOf(song: Song): Long? {
        if (song.id < 0) return -song.id
        val entries = index()[keyOf(song.title, song.artist)] ?: return null
        return entries.firstOrNull { kotlin.math.abs(it.second - song.durationMs) <= SAME_SONG_TOLERANCE_MS }?.first
    }

    // ---------------------------------------------------------------- i giri

    private fun start() {
        if (sessionJob?.isActive != true) sessionJob = scope.launch { sessionLoop() }
        if (reportJob?.isActive != true) reportJob = scope.launch { reportLoop() }
    }

    private fun stop() {
        sessionJob?.cancel()
        reportJob?.cancel()
        sessionJob = null
        reportJob = null
        _pc.value = null
    }

    /** L'attesa sul PC: ogni risposta porta il suo stato e i comandi per noi. */
    private suspend fun sessionLoop() {
        var rev = 0L
        while (currentCoroutineContext().isActive) {
            val pairing = Beam.pairing.value ?: return
            val result = runCatching {
                val conn = Beam.open("${pairing.baseUrl}/session?rev=$rev&ack=$lastSeq", pairing.token).apply {
                    readTimeout = SESSION_READ_TIMEOUT_MS
                }
                try {
                    if (conn.responseCode != 200) error("il PC ha risposto ${conn.responseCode}")
                    JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                } finally {
                    conn.disconnect()
                }
            }
            // Un'attesa rimpiazzata da wake(): la sua risposta è vecchia.
            if (!currentCoroutineContext().isActive) return
            val session = result.getOrNull()
            if (session == null) {
                delay(RETRY_MS)
                continue
            }
            val pcJson = session.getJSONObject("pc")
            val pc = parsePc(pcJson)
            _pc.value = pc
            rev = pc.rev
            // La libreria del PC è cambiata: il catalogo si riscarica subito.
            val tag = pcJson.optString("catalog")
            if (tag.isNotEmpty() && tag != Beam.catalogTag) Beam.refresh()
            val commands = session.optJSONArray("commands") ?: JSONArray()
            if (commands.length() > 0) {
                // Il PC rimanda i comandi finché non li confermiamo: quelli già
                // eseguiti si saltano (con un PC più vecchio, senza numeri, valgono tutti).
                val parsed = List(commands.length()) { parseCommand(commands.getJSONObject(it)) }
                    .filter { it.seq == 0L || it.seq > lastSeq }
                parsed.maxOfOrNull { it.seq }?.let { if (it > lastSeq) lastSeq = it }
                withContext(Dispatchers.Main) { parsed.forEach { commandHandler?.invoke(it) } }
                // Dopo un comando lo stato del telefono è cambiato: il PC lo vede subito.
                phoneChanged()
            }
        }
    }

    /** Lo stato del telefono al PC: quando cambia, e comunque ogni tanto. */
    private suspend fun reportLoop() {
        while (currentCoroutineContext().isActive) {
            withTimeoutOrNull(REPORT_EVERY_MS) { changes.receive() }
            // Più cambi in fila (brano nuovo, poi play) diventano un invio solo.
            delay(COALESCE_MS)
            val pairing = Beam.pairing.value ?: return
            val body = withContext(Dispatchers.Main) { phoneState?.invoke() } ?: continue
            runCatching {
                val conn = Beam.open("${pairing.baseUrl}/state", pairing.token).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                try {
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    // ---------------------------------------------------------------- formato

    private fun parsePc(o: JSONObject): PcNow {
        val track = o.optJSONObject("track")
        val queue = o.optJSONArray("queue") ?: JSONArray()
        return PcNow(
            rev = o.optLong("rev"),
            title = track?.optString("title"),
            artist = track?.optString("artist"),
            album = track?.optString("album"),
            durationMs = track?.optLong("durationMs") ?: 0,
            pcId = track?.takeIf { it.has("pcId") && !it.isNull("pcId") }?.getLong("pcId"),
            playing = o.optBoolean("playing"),
            positionMs = o.optLong("positionMs"),
            queue = List(queue.length()) { queue.getLong(it) },
            index = o.optInt("index", -1),
            receivedAt = System.currentTimeMillis(),
            shuffle = o.optBoolean("shuffle"),
            repeat = o.optString("repeat", "OFF"),
        )
    }

    private fun parseCommand(o: JSONObject): Command {
        val ids = o.optJSONArray("ids") ?: JSONArray()
        return Command(
            action = o.optString("action"),
            ms = o.takeIf { it.has("ms") && !it.isNull("ms") }?.getLong("ms"),
            value = o.takeIf { it.has("value") && !it.isNull("value") }?.getInt("value"),
            ids = List(ids.length()) { ids.getLong(it) },
            index = o.optInt("index", 0),
            seq = o.optLong("seq", 0),
        )
    }

    /** Il brano del telefono nel formato che il PC capisce. */
    fun nowTrackJson(song: Song): JSONObject = JSONObject()
        .put("title", song.title)
        .put("artist", song.artist)
        .put("album", song.album)
        .put("durationMs", song.durationMs)
        .apply { pcIdOf(song)?.let { put("pcId", it) } }

    // ---------------------------------------------------------------- indice

    @Volatile private var indexOf: JSONArray? = null
    @Volatile private var indexCache: Map<String, List<Pair<Long, Long>>> = emptyMap()

    /** Titolo e artista → id e durata, dal catalogo del PC; si rifà quando il catalogo cambia. */
    private fun index(): Map<String, List<Pair<Long, Long>>> {
        val catalog = Beam.catalog ?: return emptyMap()
        if (catalog === indexOf) return indexCache
        val built = HashMap<String, MutableList<Pair<Long, Long>>>()
        for (i in 0 until catalog.length()) {
            val t = catalog.getJSONObject(i)
            built.getOrPut(keyOf(t.optString("title"), t.optString("artist"))) { mutableListOf() } +=
                t.getLong("id") to t.optLong("durationMs")
        }
        indexCache = built
        indexOf = catalog
        return built
    }

    private fun keyOf(title: String, artist: String): String =
        title.trim().lowercase() + '\u0000' + MusicRepository.primaryArtistOf(artist).trim().lowercase()

    /** Il PC tiene l'attesa al più 12 secondi: oltre i 20 la connessione è morta. */
    private const val SESSION_READ_TIMEOUT_MS = 20_000
    private const val RETRY_MS = 3_000L
    private const val REPORT_EVERY_MS = 10_000L
    private const val COALESCE_MS = 150L
    private const val SAME_SONG_TOLERANCE_MS = 3_000L
}
