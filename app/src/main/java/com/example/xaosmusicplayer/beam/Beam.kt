package com.example.xaosmusicplayer.beam

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.example.xaosmusicplayer.data.MusicRepository
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.i18n.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.util.zip.GZIPInputStream

/**
 * Xaos Beam, il lato del telefono: la libreria del PC dentro quella del
 * telefono, ascoltata in streaming sulla rete di casa.
 *
 * Il PC si trova con un broadcast UDP (il suo indirizzo cambia, il suo id no),
 * si abbina una volta con il PIN che mostra nelle sue impostazioni, e da lì in
 * poi il catalogo dei suoi brani si scarica e si tiene aggiornato da solo
 * finché Beam è acceso. I brani diventano [Song] come gli altri, con l'id
 * negativo e l'indirizzo da cui il player li scarica mentre suonano.
 *
 * Il catalogo resta anche in un file: all'avvio la libreria del PC compare
 * subito, prima ancora che il PC risponda. Se il PC non si trova, i suoi brani
 * spariscono finché non torna raggiungibile: un brano che non si può suonare
 * in libreria confonde soltanto.
 */
object Beam {

    /** Un PC con Xaos trovato in rete. */
    data class Server(val id: String, val name: String, val host: String, val port: Int)

    /** Il PC abbinato, con la chiave che ha dato a questo telefono. */
    data class Pairing(val id: String, val name: String, val host: String, val port: Int, val token: String) {
        val baseUrl: String get() = "http://$host:$port/beam"
    }

    sealed interface Status {
        data object Off : Status
        /** Acceso ma senza un PC abbinato: si cercano quelli in rete. */
        data object NotPaired : Status
        data object Connecting : Status
        data class Connected(val trackCount: Int) : Status
        /** Il PC non risponde: spento, o su un'altra rete. */
        data object Unreachable : Status
        /** Il PC ha tolto questo telefono dall'elenco: va abbinato di nuovo. */
        data object Revoked : Status
    }

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val refreshLock = Mutex()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _pairing = MutableStateFlow<Pairing?>(null)
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _found = MutableStateFlow<List<Server>>(emptyList())
    /** I PC trovati all'ultima ricerca. */
    val found: StateFlow<List<Server>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    /** I brani del PC, pronti da mettere in libreria accanto a quelli del telefono. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    /** Il catalogo così come è arrivato, per ricostruire i brani se cambia indirizzo. */
    @Volatile internal var catalog: JSONArray? = null
        private set
    private var etag: String? = null

    /** L'impronta del catalogo che abbiamo: se quella del PC è diversa, va riscaricato. */
    internal val catalogTag: String? get() = etag?.takeIf { catalog != null }

    private val prefs by lazy { app.getSharedPreferences("xaos_beam", Context.MODE_PRIVATE) }
    private val catalogFile by lazy { File(app.filesDir, "beam-catalog.json") }

    /** Da chiamare prima di usarlo; le chiamate successive non fanno nulla. */
    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        _pairing.value = prefs.getString(KEY_PAIRING, null)?.let { runCatching { pairingFrom(JSONObject(it)) }.getOrNull() }
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        etag = prefs.getString(KEY_ETAG, null)
        if (_enabled.value && _pairing.value != null) {
            runCatching { JSONArray(catalogFile.readText()) }.getOrNull()?.let { cached ->
                catalog = cached
                _songs.value = buildSongs(cached)
            }
        }
        BeamConnect.attach()
        if (_enabled.value) startLoop()
    }

    fun setEnabled(on: Boolean) {
        if (_enabled.value == on) return
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) startLoop() else {
            loop?.cancel()
            loop = null
            _songs.value = emptyList()
            _found.value = emptyList()
            _status.value = Status.Off
        }
    }

    /** Cerca subito i PC in rete, senza aspettare il prossimo giro. */
    fun scan() {
        scope.launch { discover() }
    }

    /**
     * Abbina questo telefono a [server] con il PIN letto sul PC. Il PIN serve
     * una volta sola: in cambio il PC dà una chiave, che resta qui.
     */
    suspend fun pair(server: Server, pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("pin", pin.trim()).put("device", deviceName()).toString()
            val conn = open("http://${server.host}:${server.port}/beam/pair", token = null).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                when (conn.responseCode) {
                    200 -> Unit
                    403 -> error(t("PIN sbagliato"))
                    else -> error(t("il PC ha risposto %d", conn.responseCode))
                }
                val reply = JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                val pairing = Pairing(
                    id = reply.getString("id"),
                    name = reply.optString("name", server.name),
                    host = server.host,
                    port = server.port,
                    token = reply.getString("token"),
                )
                savePairing(pairing)
                // Un PC diverso ha un catalogo diverso: quello vecchio non serve più.
                catalog = null
                etag = null
                prefs.edit().remove(KEY_ETAG).apply()
                _songs.value = emptyList()
            } finally {
                conn.disconnect()
            }
            refreshNow()
        }
    }

    /**
     * Abbina un PC scritto a mano (indirizzo o indirizzo:porta): serve quando
     * il broadcast non passa, per esempio in VPN.
     */
    suspend fun serverAt(address: String): Result<Server> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = address.trim().removePrefix("http://").trimEnd('/')
            val host = trimmed.substringBefore(':')
            val port = trimmed.substringAfter(':', "").toIntOrNull() ?: DEFAULT_PORT
            require(host.isNotBlank()) { t("indirizzo vuoto") }
            val conn = open("http://$host:$port/beam/info", token = null)
            try {
                if (conn.responseCode != 200) error(t("il PC ha risposto %d", conn.responseCode))
                val info = JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                if (info.optString("app") != APP_TAG) error(t("lì non c'è Xaos"))
                Server(info.getString("id"), info.optString("name", host), host, port)
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Dimentica il PC abbinato; i suoi brani spariscono dalla libreria. */
    fun forget() {
        _pairing.value = null
        prefs.edit().remove(KEY_PAIRING).remove(KEY_ETAG).apply()
        catalog = null
        etag = null
        catalogFile.delete()
        _songs.value = emptyList()
        if (_enabled.value) {
            _status.value = Status.NotPaired
            scan()
        }
    }

    /** Riscarica il catalogo adesso (se è cambiato), per esempio da "Riscansiona". */
    fun refresh() {
        if (_enabled.value) scope.launch { refreshNow() }
    }

    /** Il testo di un brano del PC, così com'è nel file; null se non ce l'ha. */
    fun lyrics(songId: Long): String? {
        val pairing = _pairing.value ?: return null
        if (songId >= 0) return null
        return runCatching {
            val conn = open("${pairing.baseUrl}/lyrics/${-songId}", pairing.token)
            try {
                if (conn.responseCode != 200) null
                else conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * Apre un'immagine o un file indicato da [uri], che sia di MediaStore o
     * del PC: il ContentResolver da solo non sa leggere da un indirizzo web.
     */
    fun openStream(context: Context, uri: Uri): InputStream? =
        if (uri.scheme == "http" || uri.scheme == "https") {
            runCatching {
                (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }.inputStream
            }.getOrNull()
        } else {
            context.contentResolver.openInputStream(uri)
        }

    // ---------------------------------------------------------------- download

    /** Un brano del PC che si sta portando sul telefono. */
    sealed interface Download {
        data object Queued : Download
        /** [progress] da 0 a 1, o -1 se il PC non ha detto quanto è grande. */
        data class Running(val progress: Float) : Download
        data class Failed(val message: String) : Download
    }

    private val _downloads = MutableStateFlow<Map<Long, Download>>(emptyMap())
    /** Id del brano del PC → a che punto è il suo download. */
    val downloads: StateFlow<Map<Long, Download>> = _downloads.asStateFlow()

    private val _downloaded = MutableSharedFlow<Song>(extraBufferCapacity = 64)
    /**
     * I brani appena arrivati sul telefono: la libreria va riletta, e il
     * brano del PC lascia il posto a quello locale anche in preferiti,
     * playlist e ascolti.
     */
    val downloaded: SharedFlow<Song> = _downloaded.asSharedFlow()

    private val downloadQueue = Channel<Song>(Channel.UNLIMITED)
    private var downloadWorker: Job? = null

    /**
     * Scarica [songs] sul telefono, uno alla volta, nella stessa cartella in
     * cui li metterebbe la sincronizzazione via cavo: così il PC, alla
     * prossima sincronizzazione, li riconosce come già mandati.
     */
    fun download(songs: List<Song>) {
        val busy = _downloads.value.filterValues { it !is Download.Failed }.keys
        val wanted = songs.filter { it.isRemote && it.id !in busy }
        if (wanted.isEmpty()) return
        _downloads.value = _downloads.value + wanted.associate { it.id to Download.Queued }
        wanted.forEach { downloadQueue.trySend(it) }
        if (downloadWorker?.isActive != true) {
            downloadWorker = scope.launch {
                for (song in downloadQueue) {
                    if (_downloads.value[song.id] !is Download.Queued) continue
                    val result = runCatching { saveToPhone(song) }
                    result.onSuccess {
                        _downloads.value = _downloads.value - song.id
                        _downloaded.tryEmit(song)
                    }.onFailure {
                        _downloads.value = _downloads.value + (song.id to Download.Failed(it.message ?: t("download non riuscito")))
                    }
                }
            }
        }
    }

    /** Toglie dalla coda un download non ancora partito, o ne dimentica uno fallito. */
    fun cancelDownload(songId: Long) {
        val state = _downloads.value[songId]
        if (state is Download.Queued || state is Download.Failed) _downloads.value = _downloads.value - songId
    }

    private fun saveToPhone(song: Song) {
        val url = (song.remoteUrl ?: error(t("non è un brano del PC"))) + "&copy=1"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        try {
            if (conn.responseCode != 200) error(t("il PC ha risposto %d", conn.responseCode))
            val fileName = conn.getHeaderField(FILE_NAME_HEADER)?.let { URLDecoder.decode(it, "UTF-8") }
                ?: "${song.artist} - ${song.title}.flac".replace(Regex("""[\\/:*?"<>|]"""), "_")
            // Il percorso della sincronizzazione via cavo, "/sdcard/Music/Xaos/…";
            // se il PC non lo sa (il file non sta in una cartella della
            // libreria) si fa come lei: cartella dell'album dentro Music/Xaos.
            val folder = conn.getHeaderField(PHONE_PATH_HEADER)
                ?.let { URLDecoder.decode(it, "UTF-8") }
                ?.removePrefix("/sdcard/")?.removePrefix("/storage/emulated/0/")
                ?.substringBeforeLast('/', "")
                ?.takeIf { it.startsWith("Music/") }
                ?: "Music/Xaos/" + song.album.replace(Regex("""[\\/:*?"<>|]"""), "_")
            val total = conn.contentLengthLong
            conn.inputStream.use { input -> publish(input, total, "$folder/", fileName, song.id) }
        } finally {
            conn.disconnect()
        }
    }

    /** Scrive il file nella musica del telefono, come i download di yt-dlp. */
    private fun publish(input: InputStream, total: Long, folder: String, fileName: String, songId: Long) {
        val mime = when (fileName.substringAfterLast('.').lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/*"
        }
        fun copy(out: java.io.OutputStream) {
            val buffer = ByteArray(64 * 1024)
            var done = 0L
            var lastShown = -1
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                done += n
                val percent = if (total > 0) (done * 100 / total).toInt() else -1
                if (percent != lastShown) {
                    lastShown = percent
                    _downloads.value = _downloads.value + (songId to Download.Running(if (total > 0) done.toFloat() / total else -1f))
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                // Finché è pending il brano resta invisibile: un file a metà
                // non deve comparire in libreria.
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error(t("impossibile creare il file"))
            try {
                resolver.openOutputStream(uri)?.use(::copy) ?: error(t("impossibile scrivere il file"))
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStorageDirectory(), folder).apply { mkdirs() }
            val target = File(dir, fileName)
            try {
                target.outputStream().use(::copy)
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            MediaScannerConnection.scanFile(app, arrayOf(target.path), arrayOf(mime), null)
        }
    }

    // ---------------------------------------------------------------- il giro

    /**
     * Finché Beam è acceso: con un PC abbinato si controlla ogni tanto se il
     * catalogo è cambiato (una richiesta minuscola se non lo è); senza, si
     * cercano i PC in rete.
     */
    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                if (_pairing.value == null) {
                    _status.value = Status.NotPaired
                    discover()
                    delay(SCAN_INTERVAL_MS)
                } else {
                    refreshNow()
                    delay(
                        when {
                            lastFetch == Fetch.BUSY -> BUSY_RETRY_MS
                            _status.value is Status.Connected -> CHECK_INTERVAL_MS
                            else -> RETRY_INTERVAL_MS
                        }
                    )
                }
            }
        }
    }

    private suspend fun refreshNow() = refreshLock.withLock {
        val pairing = _pairing.value ?: return@withLock
        if (_status.value !is Status.Connected) _status.value = Status.Connecting
        val result = fetchCatalog(pairing)
        lastFetch = result
        when (result) {
            Fetch.OK -> Unit
            Fetch.BUSY -> if (_status.value !is Status.Connected) _status.value = Status.Connecting
            Fetch.REVOKED -> {
                _status.value = Status.Revoked
                _songs.value = emptyList()
            }
            Fetch.UNREACHABLE -> {
                // Forse ha solo cambiato indirizzo: lo si cerca per id.
                val moved = discover().firstOrNull { it.id == pairing.id }
                if (moved != null && (moved.host != pairing.host || moved.port != pairing.port)) {
                    val updated = pairing.copy(host = moved.host, port = moved.port, name = moved.name)
                    savePairing(updated)
                    // Gli indirizzi dei brani contengono quello del PC: vanno rifatti.
                    catalog?.let { _songs.value = buildSongs(it) }
                    if (fetchCatalog(updated) == Fetch.OK) return@withLock
                }
                _status.value = Status.Unreachable
                _songs.value = emptyList()
            }
        }
    }

    /** BUSY: il PC c'è ma sta ancora leggendo la libreria. */
    private enum class Fetch { OK, UNREACHABLE, REVOKED, BUSY }

    @Volatile private var lastFetch = Fetch.OK

    private fun fetchCatalog(pairing: Pairing): Fetch {
        // Con un catalogo in mano bastano le differenze: il PC manda solo i brani cambiati.
        val since = etag?.takeIf { catalog != null }?.let { "?since=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val conn = runCatching { open("${pairing.baseUrl}/catalog$since", pairing.token) }.getOrElse { return Fetch.UNREACHABLE }
        try {
            // Senza un catalogo in mano non ha senso chiedere "se è cambiato".
            if (catalog != null) etag?.let { conn.setRequestProperty("If-None-Match", it) }
            val code = runCatching { conn.responseCode }.getOrElse { return Fetch.UNREACHABLE }
            when (code) {
                304 -> {
                    catalog?.let { if (_songs.value.isEmpty()) _songs.value = buildSongs(it) }
                    _status.value = Status.Connected(catalog?.length() ?: 0)
                    return Fetch.OK
                }
                401 -> return Fetch.REVOKED
                // Si tengono i brani che si hanno: fra poco il catalogo è pronto.
                503 -> return Fetch.BUSY
                200 -> Unit
                else -> return Fetch.UNREACHABLE
            }
            val raw = runCatching {
                val stream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(conn.inputStream) else conn.inputStream
                stream.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrElse { return Fetch.UNREACHABLE }
            val tracks = runCatching {
                val body = JSONObject(raw)
                if (body.optBoolean("delta")) applyDelta(catalog ?: error("nessun catalogo"), body) else body.getJSONArray("tracks")
            }.getOrElse { return Fetch.UNREACHABLE }
            catalog = tracks
            etag = conn.getHeaderField("ETag")
            prefs.edit().putString(KEY_ETAG, etag).apply()
            runCatching { catalogFile.writeText(tracks.toString()) }
            _songs.value = buildSongs(tracks)
            _status.value = Status.Connected(tracks.length())
            return Fetch.OK
        } finally {
            conn.disconnect()
        }
    }

    /** Il catalogo che si ha, con i brani cambiati al posto dei vecchi e senza quelli tolti. */
    private fun applyDelta(current: JSONArray, delta: JSONObject): JSONArray {
        val removed = delta.optJSONArray("removed")?.let { r -> (0 until r.length()).map { r.getLong(it) }.toHashSet() } ?: emptySet()
        val changed = delta.optJSONArray("changed") ?: JSONArray()
        val byId = LinkedHashMap<Long, JSONObject>()
        for (i in 0 until current.length()) {
            val t = current.getJSONObject(i)
            val id = t.getLong("id")
            if (id !in removed) byId[id] = t
        }
        for (i in 0 until changed.length()) {
            val t = changed.getJSONObject(i)
            byId[t.getLong("id")] = t
        }
        return JSONArray(byId.values)
    }

    /** Il corpo di una risposta, anche se arriva compresso. */
    internal fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(conn.inputStream) else conn.inputStream
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** I brani del catalogo, con gli indirizzi del PC com'è adesso. */
    private fun buildSongs(tracks: JSONArray): List<Song> {
        val pairing = _pairing.value ?: return emptyList()
        val base = pairing.baseUrl
        val key = Uri.encode(pairing.token)
        val artKey = artKeyOf(pairing.token)
        return List(tracks.length()) { i ->
            val t = tracks.getJSONObject(i)
            val id = t.getLong("id")
            val artist = t.optString("artist").ifBlank { MusicRepository.UNKNOWN_ARTIST }
            val owner = MusicRepository.primaryArtistOf(t.optString("albumArtist").ifBlank { artist })
            val album = t.optString("album").ifBlank { MusicRepository.UNKNOWN_ALBUM }
            Song(
                id = -id,
                title = t.optString("title").ifBlank { MusicRepository.UNKNOWN_TITLE },
                artist = artist,
                albumArtist = owner,
                album = album,
                albumId = MusicRepository.syntheticAlbumId(album, owner),
                durationMs = t.optLong("durationMs"),
                // Come MediaStore: disco × 1000 + traccia. Così nello stesso
                // album i brani del PC si mettono in fila con quelli locali.
                trackNumber = t.optInt("disc").let { disc -> if (disc > 0) disc * 1000 + t.optInt("track") else t.optInt("track") },
                year = t.optInt("year"),
                dateAdded = t.optLong("modified") / 1000,
                relativePath = "$FOLDER_PREFIX${pairing.name}/",
                genre = t.optString("genre").takeIf { it.isNotBlank() && it != "null" },
                remoteUrl = "$base/track/$id?k=$key",
                // Per la copertina basta la chiave ridotta: questo indirizzo
                // finisce nella notifica e nei log di sistema.
                remoteArtworkUrl = "$base/art/$id?s=$ART_SIZE&a=$artKey",
            )
        }
    }

    /**
     * La chiave delle sole copertine, ricavata da quella vera come fa il PC:
     * gli indirizzi delle copertine finiscono nella notifica e nei log di
     * sistema, e lì la chiave che apre tutta la libreria non deve esserci.
     */
    internal fun artKeyOf(token: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("xaos-beam-art:$token".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)

    // ---------------------------------------------------------------- scoperta

    /**
     * Chiede a tutta la rete chi è un PC con Xaos. Risponde ognuno con id,
     * nome e porta; l'indirizzo è quello da cui arriva la risposta.
     */
    private suspend fun discover(): List<Server> {
        _scanning.value = true
        val servers = runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 250
                val query = DISCOVERY_QUERY.toByteArray(Charsets.UTF_8)
                broadcastTargets().forEach { target ->
                    runCatching { socket.send(DatagramPacket(query, query.size, target, DISCOVERY_PORT)) }
                }
                val found = LinkedHashMap<String, Server>()
                val buffer = ByteArray(1024)
                val deadline = System.currentTimeMillis() + DISCOVERY_WINDOW_MS
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    runCatching {
                        val info = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                        if (info.optString("app") == APP_TAG) {
                            val id = info.getString("id")
                            found[id] = Server(id, info.optString("name", "PC"), packet.address.hostAddress ?: return@runCatching, info.getInt("port"))
                        }
                    }
                }
                found.values.toList()
            }
        }.getOrDefault(emptyList())
        _found.value = servers
        _scanning.value = false
        return servers
    }

    /** Il broadcast generico più quello di ogni rete a cui il telefono è collegato. */
    private fun broadcastTargets(): List<InetAddress> {
        val subnets = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
        }.getOrDefault(emptyList())
        return (subnets + InetAddress.getByName("255.255.255.255")).distinct()
    }

    // ---------------------------------------------------------------- utilità

    internal fun open(url: String, token: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            token?.let { setRequestProperty(TOKEN_HEADER, it) }
        }

    private fun savePairing(pairing: Pairing) {
        _pairing.value = pairing
        prefs.edit().putString(
            KEY_PAIRING,
            JSONObject()
                .put("id", pairing.id)
                .put("name", pairing.name)
                .put("host", pairing.host)
                .put("port", pairing.port)
                .put("token", pairing.token)
                .toString(),
        ).apply()
    }

    private fun pairingFrom(o: JSONObject) = Pairing(
        id = o.getString("id"),
        name = o.getString("name"),
        host = o.getString("host"),
        port = o.getInt("port"),
        token = o.getString("token"),
    )

    /** Il nome che l'utente ha dato al telefono, quello che vede anche il PC. */
    private fun deviceName(): String =
        runCatching { Settings.Global.getString(app.contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** La cartella con cui i brani del PC compaiono nella vista per cartelle. */
    const val FOLDER_PREFIX = "Xaos Beam/"

    private const val APP_TAG = "xaos-beam"
    private const val DISCOVERY_QUERY = "XAOS-BEAM?"
    private const val DISCOVERY_PORT = 47811
    private const val DEFAULT_PORT = 47810
    private const val TOKEN_HEADER = "X-Xaos-Beam"
    private const val PHONE_PATH_HEADER = "X-Xaos-Phone-Path"
    private const val FILE_NAME_HEADER = "X-Xaos-File-Name"
    private const val ART_SIZE = 512
    private const val DISCOVERY_WINDOW_MS = 1_500L
    private const val SCAN_INTERVAL_MS = 8_000L
    private const val CHECK_INTERVAL_MS = 20_000L
    private const val RETRY_INTERVAL_MS = 10_000L
    private const val BUSY_RETRY_MS = 3_000L
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PAIRING = "pairing"
    private const val KEY_ETAG = "etag"
}
