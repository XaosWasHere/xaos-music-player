package xaos.desktop.beam

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xaos.desktop.BeamDevice
import xaos.desktop.Settings
import xaos.desktop.library.ArtworkLoader
import xaos.desktop.library.Library
import xaos.desktop.library.LibrarySnapshot
import xaos.desktop.library.TagEditor
import xaos.desktop.library.Track
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream
import javax.imageio.ImageIO

/** Com'è Beam in questo momento, per le impostazioni. */
sealed interface BeamState {
    data object Off : BeamState
    /** In ascolto su [port], raggiungibile a questi indirizzi della rete di casa. */
    data class Running(val port: Int, val addresses: List<String>) : BeamState
    data class Failed(val message: String) : BeamState
}

/** Un brano come lo vede il telefono: i tag e un id che non cambia fra un avvio e l'altro. */
@Serializable
data class BeamTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val track: Int,
    val disc: Int,
    val year: Int,
    val genre: String? = null,
    val durationMs: Long,
    val lyrics: Boolean,
    /** Il formato del file che arriva al telefono, in minuscolo ("flac", "mp3"…). */
    val format: String,
    val modified: Long,
)

@Serializable
data class BeamCatalog(val server: String, val name: String, val tracks: List<BeamTrack>)

@Serializable
data class BeamInfo(
    val app: String = "xaos-beam",
    val protocol: Int = BeamServer.PROTOCOL,
    val id: String,
    val name: String,
    val port: Int,
    val version: String,
)

@Serializable
private data class PairRequest(val pin: String, val device: String)

@Serializable
private data class PairResponse(val token: String, val id: String, val name: String)

/**
 * Xaos Beam, il lato del PC: la libreria esposta sulla rete di casa perché il
 * telefono la possa sfogliare e ascoltare in streaming, senza copiarla.
 *
 * Due porte. Su [UDP_PORT] si risponde a chi cerca un PC con Xaos (il telefono
 * manda un broadcast, noi rispondiamo con nome e porta). Su [HTTP_PORT] c'è un
 * piccolo server HTTP con il catalogo, i file audio (con le richieste Range,
 * che servono a spostarsi nel brano), le copertine e i testi.
 *
 * Per entrare serve l'abbinamento: il telefono chiede un PIN che si legge
 * nelle impostazioni del PC e riceve in cambio una chiave, che poi manda a
 * ogni richiesta. Senza chiave si vede solo che qui c'è un Xaos e come si
 * chiama. Togliendo un telefono dall'elenco la sua chiave smette di valere.
 */
class BeamServer(
    private val settings: Settings,
    private val library: Library,
    player: xaos.desktop.player.Player,
    scope: kotlinx.coroutines.CoroutineScope,
    /** Dove la sincronizzazione via cavo metterebbe un file sul telefono. */
    private val phonePathOf: (File) -> String? = { null },
) {

    /** Connect: cosa suona l'altro dispositivo, comandarlo, spostare l'ascolto. */
    val connect = BeamConnect(player, scope, { catalogFor(library.snapshot.value).byId }, { catalogTag() })

    init {
        // La libreria cambiata (riletta, un brano aggiunto) arriva subito al telefono.
        scope.launch { library.snapshot.collect { connect.notifyChanged() } }
    }

    private val _state = MutableStateFlow<BeamState>(BeamState.Off)
    val state: StateFlow<BeamState> = _state.asStateFlow()

    private val _pin = MutableStateFlow(newPinValue())
    /** Il PIN da digitare sul telefono per abbinarlo. */
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _active = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Telefono → ultima richiesta: chi sta usando Beam adesso. */
    val active: StateFlow<Map<String, Long>> = _active.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    private var http: HttpServer? = null
    private var executor: ExecutorService? = null
    @Volatile private var discovery: DatagramSocket? = null
    @Volatile private var wrongPins = 0

    /** Il catalogo pronto da spedire, rifatto solo quando la libreria cambia. */
    private class CatalogCache(val snapshot: LibrarySnapshot, val gzip: ByteArray, val etag: String, val byId: Map<Long, Track>)
    @Volatile private var catalog: CatalogCache? = null

    /** Copertine già ridotte: scorrendo la libreria si chiedono di continuo. */
    private val artCache = object : LinkedHashMap<String, ByteArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > ART_CACHE_ENTRIES
    }

    private val serverId: String
        get() = settings.data.value.beamServerId ?: synchronized(this) {
            settings.data.value.beamServerId ?: randomToken(12).also { id -> settings.update { it.copy(beamServerId = id) } }
        }

    private val serverName: String =
        System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
            ?: "PC"

    @Synchronized
    fun start() {
        if (http != null) return
        val server = runCatching { HttpServer.create(InetSocketAddress(HTTP_PORT), 0) }
            // La porta solita è occupata: se ne prende una libera, tanto il
            // telefono la scopre dalla risposta al broadcast.
            .recoverCatching { HttpServer.create(InetSocketAddress(0), 0) }
            .getOrElse {
                _state.value = BeamState.Failed(it.message ?: "impossibile aprire la porta")
                return
            }
        val pool = Executors.newFixedThreadPool(THREADS) { r -> Thread(r, "xaos-beam").apply { isDaemon = true } }
        server.executor = pool
        server.createContext("/beam/") { ex -> handle(ex) }
        server.start()
        http = server
        executor = pool
        val port = server.address.port
        startDiscovery(port)
        _state.value = BeamState.Running(port, localAddresses())
    }

    @Synchronized
    fun stop() {
        discovery?.close()
        discovery = null
        http?.stop(0)
        http = null
        executor?.shutdownNow()
        executor = null
        _active.value = emptyMap()
        _state.value = BeamState.Off
    }

    /** I telefoni collegati con Connect: i loro nomi sono quelli dell'abbinamento. */
    val phones get() = connect.phones

    /** Un PIN nuovo: quello vecchio non vale più. */
    fun newPin() {
        wrongPins = 0
        _pin.value = newPinValue()
    }

    fun forget(device: BeamDevice) {
        settings.update { s -> s.copy(beamDevices = s.beamDevices.filterNot { it.token == device.token }) }
        _active.value = _active.value - device.name
    }

    // ---------------------------------------------------------------- scoperta

    /**
     * Risponde ai broadcast del telefono. Il telefono non sa l'indirizzo del
     * PC (cambia con il DHCP), quindi lo chiede a tutta la rete.
     */
    private fun startDiscovery(port: Int) {
        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(UDP_PORT))
            }
        }.getOrNull() ?: return
        discovery = socket
        Thread({
            val buffer = ByteArray(512)
            while (!socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                runCatching { socket.receive(packet) }.onFailure { return@Thread }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                if (!text.startsWith(DISCOVERY_QUERY)) continue
                val reply = json.encodeToString(info(port)).toByteArray(Charsets.UTF_8)
                runCatching { socket.send(DatagramPacket(reply, reply.size, packet.socketAddress)) }
            }
        }, "xaos-beam-discovery").apply { isDaemon = true }.start()
    }

    private fun info(port: Int) = BeamInfo(
        id = serverId,
        name = serverName,
        port = port,
        version = System.getProperty("xaos.version") ?: "dev",
    )

    // ---------------------------------------------------------------- HTTP

    private fun handle(ex: HttpExchange) {
        try {
            val path = ex.requestURI.path.removePrefix("/beam/").trimEnd('/')
            val parts = path.split('/')
            when {
                path == "info" -> sendJson(ex, json.encodeToString(info(ex.localAddress.port)))
                path == "pair" && ex.requestMethod == "POST" -> pair(ex)
                else -> {
                    val device = authorize(ex)
                        ?: (if (parts.size == 2 && parts[0] == "art") authorizeArt(ex) else null)
                        ?: return send(ex, 401, "chiave mancante o non valida")
                    _active.value = _active.value + (device.name to System.currentTimeMillis())
                    when {
                        path == "catalog" -> sendCatalog(ex)
                        path == "session" -> {
                            val known = query(ex)["rev"]?.toLongOrNull() ?: 0
                            // Senza "ack" è un'app del telefono più vecchia: i comandi le si danno una volta sola.
                            val ack = query(ex)["ack"]?.toLongOrNull() ?: -1
                            sendJson(ex, json.encodeToString(connect.await(device.name, known, ack)))
                        }
                        path == "state" && ex.requestMethod == "POST" -> {
                            val now = json.decodeFromString<PhoneNow>(ex.requestBody.readNBytes(256 * 1024).toString(Charsets.UTF_8))
                            connect.updatePhone(device.name, now)
                            sendJson(ex, "{}")
                        }
                        path == "control" && ex.requestMethod == "POST" -> {
                            val command = json.decodeFromString<BeamCommand>(ex.requestBody.readNBytes(256 * 1024).toString(Charsets.UTF_8))
                            connect.execute(command)
                            sendJson(ex, json.encodeToString(connect.pcNow()))
                        }
                        parts.size == 2 && parts[0] == "track" -> withTrack(ex, parts[1]) { sendAudio(ex, it) }
                        parts.size == 2 && parts[0] == "art" -> withTrack(ex, parts[1]) { sendArt(ex, it) }
                        parts.size == 2 && parts[0] == "lyrics" -> withTrack(ex, parts[1]) { sendLyrics(ex, it) }
                        else -> send(ex, 404, "non trovato")
                    }
                }
            }
        } catch (_: IOException) {
            // Il telefono ha chiuso la connessione a metà (un salto nel brano,
            // un cambio di canzone): normale, niente da fare.
        } catch (e: Exception) {
            runCatching { send(ex, 500, e.message ?: "errore") }
        } finally {
            ex.close()
        }
    }

    /** La chiave arriva nell'intestazione o, per player e immagini, nell'indirizzo. */
    private fun authorize(ex: HttpExchange): BeamDevice? {
        val token = ex.requestHeaders.getFirst(TOKEN_HEADER) ?: query(ex)["k"] ?: return null
        val given = token.toByteArray()
        return settings.data.value.beamDevices.firstOrNull { MessageDigest.isEqual(it.token.toByteArray(), given) }
    }

    /**
     * Le copertine accettano anche una chiave ridotta, ricavata da quella del
     * telefono: il loro indirizzo finisce nella notifica e nei log di Android,
     * e lì non deve esserci la chiave che apre tutta la libreria.
     */
    private fun authorizeArt(ex: HttpExchange): BeamDevice? {
        val given = query(ex)["a"]?.toByteArray() ?: return null
        return settings.data.value.beamDevices.firstOrNull { MessageDigest.isEqual(artKeyOf(it.token).toByteArray(), given) }
    }

    private fun pair(ex: HttpExchange) {
        val request = runCatching { json.decodeFromString<PairRequest>(ex.requestBody.readNBytes(4096).toString(Charsets.UTF_8)) }
            .getOrElse { return send(ex, 400, "richiesta non valida") }
        if (request.pin.trim() != _pin.value) {
            // Pochi tentativi, poi il PIN cambia: provarli tutti non serve a niente.
            if (++wrongPins >= MAX_WRONG_PINS) newPin()
            return send(ex, 403, "PIN sbagliato")
        }
        val name = request.device.trim().take(60).ifEmpty { "Telefono" }
        val device = BeamDevice(name = name, token = randomToken(32), pairedAt = System.currentTimeMillis())
        // Lo stesso telefono riabbinato prende il posto di quello vecchio.
        settings.update { s -> s.copy(beamDevices = s.beamDevices.filterNot { it.name == name } + device) }
        newPin()
        sendJson(ex, json.encodeToString(PairResponse(device.token, serverId, serverName)))
    }

    private fun sendCatalog(ex: HttpExchange) {
        // Appena avviato, la libreria si sta ancora leggendo: un catalogo
        // vuoto farebbe sparire dal telefono tutti i brani del PC.
        if (!libraryReady()) return send(ex, 503, "libreria in caricamento")
        val cache = catalogFor(library.snapshot.value)
        ex.responseHeaders.add("ETag", cache.etag)
        if (ex.requestHeaders.getFirst("If-None-Match") == cache.etag) {
            ex.sendResponseHeaders(304, -1)
            return
        }
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.responseHeaders.add("Content-Encoding", "gzip")
        ex.sendResponseHeaders(200, cache.gzip.size.toLong())
        ex.responseBody.use { it.write(cache.gzip) }
    }

    private fun libraryReady(): Boolean {
        val snapshot = library.snapshot.value
        if (snapshot === LibrarySnapshot.Empty) return false
        return snapshot.tracks.isNotEmpty() || library.scan.value !is xaos.desktop.library.ScanState.Scanning
    }

    /** Il brano del PC con quell'id del catalogo, se c'è. */
    fun trackFor(id: Long): Track? = if (libraryReady()) catalogFor(library.snapshot.value).byId[id] else null

    /** L'impronta del catalogo, per far sapere al telefono che è cambiato. */
    internal fun catalogTag(): String = if (libraryReady()) catalogFor(library.snapshot.value).etag else ""

    private fun catalogFor(snapshot: LibrarySnapshot): CatalogCache {
        catalog?.takeIf { it.snapshot === snapshot }?.let { return it }
        val byId = HashMap<Long, Track>(snapshot.tracks.size * 2)
        val tracks = snapshot.tracks.map { t ->
            val id = idOf(t.path)
            byId[id] = t
            BeamTrack(
                id = id,
                title = t.title,
                artist = t.artist,
                albumArtist = t.albumArtist,
                album = t.album,
                track = t.trackNumber,
                disc = t.discNumber,
                year = t.year,
                genre = t.genre,
                durationMs = t.durationMs,
                lyrics = t.hasLyrics,
                format = fileFor(t).extension.lowercase(),
                modified = t.modified,
            )
        }
        val bytes = json.encodeToString(BeamCatalog(serverId, serverName, tracks)).toByteArray(Charsets.UTF_8)
        val etag = "\"" + sha1(bytes).take(16) + "\""
        val gzip = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        return CatalogCache(snapshot, gzip, etag, byId).also { catalog = it }
    }

    private inline fun withTrack(ex: HttpExchange, rawId: String, block: (Track) -> Unit) {
        val id = rawId.toLongOrNull() ?: return send(ex, 400, "id non valido")
        val track = catalogFor(library.snapshot.value).byId[id] ?: return send(ex, 404, "brano non trovato")
        block(track)
    }

    /**
     * Il file del brano, a pezzi se il player li chiede: spostarsi a metà di
     * un FLAC vuol dire chiedere i byte da lì in poi, non riscaricare tutto.
     */
    private fun sendAudio(ex: HttpExchange, track: Track) {
        // Per tenerlo sul telefono (?copy=1) vale la stessa scelta della
        // sincronizzazione via cavo: l'MP3 quando c'è, se così è impostato.
        val copy = query(ex)["copy"] == "1"
        val file = if (copy) copyFileFor(track) else fileFor(track)
        if (copy) {
            phonePathOf(file)?.let { ex.responseHeaders.add(PHONE_PATH_HEADER, java.net.URLEncoder.encode(it, Charsets.UTF_8)) }
            ex.responseHeaders.add(FILE_NAME_HEADER, java.net.URLEncoder.encode(file.name, Charsets.UTF_8))
        }
        if (!file.isFile) return send(ex, 404, "file non trovato")
        val length = file.length()
        var start = 0L
        var end = length - 1
        var status = 200
        ex.requestHeaders.getFirst("Range")?.let { header ->
            val m = Regex("""bytes=(\d*)-(\d*)""").matchEntire(header.trim()) ?: return@let
            val (from, to) = m.destructured
            if (from.isEmpty()) {
                if (to.isEmpty()) return@let
                start = (length - to.toLong()).coerceAtLeast(0)
            } else {
                start = from.toLong()
                if (to.isNotEmpty()) end = minOf(to.toLong(), length - 1)
            }
            if (start >= length || start > end) {
                ex.responseHeaders.add("Content-Range", "bytes */$length")
                ex.sendResponseHeaders(416, -1)
                return
            }
            status = 206
        }
        val count = end - start + 1
        ex.responseHeaders.add("Accept-Ranges", "bytes")
        ex.responseHeaders.add("Content-Type", MIME[file.extension.lowercase()] ?: "application/octet-stream")
        if (status == 206) ex.responseHeaders.add("Content-Range", "bytes $start-$end/$length")
        if (ex.requestMethod == "HEAD") {
            ex.responseHeaders.add("Content-Length", count.toString())
            ex.sendResponseHeaders(status, -1)
            return
        }
        ex.sendResponseHeaders(status, count)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(64 * 1024)
            var left = count
            ex.responseBody.use { out ->
                while (left > 0) {
                    val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    left -= n
                }
            }
        }
    }

    /** La copertina ridotta al lato chiesto: in un elenco basta piccola. */
    private fun sendArt(ex: HttpExchange, track: Track) {
        val side = (query(ex)["s"]?.toIntOrNull() ?: 512).coerceIn(64, 1024)
        val key = "${track.path}@$side"
        val bytes = synchronized(artCache) { artCache[key] }
            ?: ArtworkLoader.rawBytes(track)?.let { scaled(it, side) }?.also { synchronized(artCache) { artCache[key] = it } }
            ?: return send(ex, 404, "nessuna copertina")
        ex.responseHeaders.add("Content-Type", "image/jpeg")
        ex.responseHeaders.add("Cache-Control", "max-age=86400")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun sendLyrics(ex: HttpExchange, track: Track) {
        val text = runBlocking { TagEditor.readLyrics(track) } ?: return send(ex, 404, "nessun testo")
        val bytes = text.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun sendJson(ex: HttpExchange, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun send(ex: HttpExchange, code: Int, message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun query(ex: HttpExchange): Map<String, String> =
        ex.requestURI.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else pair.substring(0, i) to URLDecoder.decode(pair.substring(i + 1), Charsets.UTF_8)
        }.toMap()

    // ---------------------------------------------------------------- utilità

    /**
     * Il file che va al telefono: l'originale se il telefono lo sa suonare,
     * altrimenti la copia MP3 quando c'è (un WMA, un AIFF).
     */
    private fun fileFor(track: Track): File {
        val original = track.file
        if (original.extension.lowercase() in PHONE_FORMATS) return original
        return track.mobilePath?.let(::File)?.takeIf { it.isFile } ?: original
    }

    private fun copyFileFor(track: Track): File {
        val mp3 = track.mobilePath?.let(::File)?.takeIf { it.isFile }
        return if (settings.data.value.preferMp3OnPhone && mp3 != null) mp3 else fileFor(track)
    }

    private fun scaled(bytes: ByteArray, side: Int): ByteArray? = runCatching {
        val src = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val crop = minOf(src.width, src.height)
        val x = (src.width - crop) / 2
        val y = (src.height - crop) / 2
        val size = minOf(side, crop)
        // RGB: il JPEG non ha la trasparenza, e da ARGB ImageIO non scrive niente.
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, size, size, x, y, x + crop, y + crop, null)
        g.dispose()
        ByteArrayOutputStream().also { ImageIO.write(out, "jpg", it) }.toByteArray()
    }.getOrNull()

    private fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    private fun newPinValue(): String = (100_000 + SecureRandom().nextInt(900_000)).toString()

    private fun randomToken(bytes: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

    companion object {
        const val HTTP_PORT = 47810
        const val UDP_PORT = 47811
        const val PROTOCOL = 1
        const val DISCOVERY_QUERY = "XAOS-BEAM?"
        const val TOKEN_HEADER = "X-Xaos-Beam"
        /** Il percorso completo sul telefono, quello della sincronizzazione via cavo. */
        const val PHONE_PATH_HEADER = "X-Xaos-Phone-Path"
        const val FILE_NAME_HEADER = "X-Xaos-File-Name"
        private const val THREADS = 12
        private const val MAX_WRONG_PINS = 5
        private const val ART_CACHE_ENTRIES = 400

        /** I formati che il player di Android suona da solo. */
        private val PHONE_FORMATS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")

        private val MIME = mapOf(
            "mp3" to "audio/mpeg",
            "flac" to "audio/flac",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "ogg" to "audio/ogg",
            "opus" to "audio/ogg",
            "wav" to "audio/wav",
            "aiff" to "audio/aiff",
            "wma" to "audio/x-ms-wma",
        )

        /**
         * L'id di un brano per il telefono: dal percorso, così resta lo stesso
         * fra un avvio e l'altro e preferiti e playlist non si perdono. Sempre
         * positivo; il telefono lo usa col segno meno, per non scontrarsi con
         * gli id dei suoi brani.
         */
        fun idOf(path: String): Long {
            val digest = MessageDigest.getInstance("SHA-1").digest(path.lowercase().toByteArray(Charsets.UTF_8))
            var v = 0L
            for (i in 0 until 8) v = (v shl 8) or (digest[i].toLong() and 0xFF)
            return (v and Long.MAX_VALUE).coerceAtLeast(1)
        }

        /** La chiave delle sole copertine: il telefono la calcola allo stesso modo. */
        fun artKeyOf(token: String): String =
            MessageDigest.getInstance("SHA-256").digest("xaos-beam-art:$token".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(32)

        private fun sha1(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
