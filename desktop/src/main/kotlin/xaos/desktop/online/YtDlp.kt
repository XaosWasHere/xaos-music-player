package xaos.desktop.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Un risultato della ricerca in rete. */
data class OnlineTrack(
    val id: String,
    val title: String,
    val uploader: String,
    val durationMs: Long,
    val url: String,
    val thumbnail: String?,
)

sealed interface OnlineSearch {
    data object Idle : OnlineSearch
    data object Searching : OnlineSearch
    data class Results(val query: String, val tracks: List<OnlineTrack>) : OnlineSearch
    data class Failed(val message: String) : OnlineSearch
}

sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(val progress: Float) : DownloadState
    /** Conversione in MP3 e incorporamento di metadati e copertina. */
    data object Converting : DownloadState
    data class Completed(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Ricerca e download con yt-dlp, come nell'app Android.
 *
 * yt-dlp invecchia in fretta: YouTube cambia spesso, e una versione di qualche
 * mese fa riceve solo "403 Forbidden". Per questo Xaos tiene una sua copia in
 * `~/.xaos/tools`, scaricata dalle release ufficiali, controlla una volta al
 * giorno se ce n'è una nuova e, a un 403, si aggiorna subito e ritenta. Quella
 * installata con winget non basta: winget non la aggiorna da sola, e yt-dlp
 * rifiuta di aggiornarsi se l'ha installato un gestore di pacchetti. Se la
 * copia non si può scaricare, si usa comunque quella del PC.
 *
 * ffmpeg resta quello installato sul PC. Il formato è lo stesso del telefono:
 * miglior audio, convertito in MP3 alla qualità massima, con metadati e
 * copertina incorporati, così il brano arriva in libreria già pronto.
 */
class YtDlp(private val scope: CoroutineScope) {

    /**
     * yt-dlp e ffmpeg, trovati in background: all'avvio non si aspetta nessuna
     * ricerca su disco. È un flusso perché le schermate si aggiornino quando arrivano.
     */
    private val _tools = MutableStateFlow<Pair<File?, File?>>(null to null)
    val tools: StateFlow<Pair<File?, File?>> = _tools.asStateFlow()

    var exe: File?
        get() = _tools.value.first
        private set(value) { _tools.update { it.copy(first = value) } }
    var ffmpeg: File?
        get() = _tools.value.second
        private set(value) { _tools.update { it.copy(second = value) } }
    val available: Boolean get() = exe != null && ffmpeg != null

    /** Gli strumenti che viaggiano con Xaos: ffmpeg, deno e una prima copia di yt-dlp. */
    private val bundled: File? = xaos.desktop.appResourcesDir?.let { File(it, "tools") }?.takeIf { it.isDirectory }

    /** Il motore JavaScript per yt-dlp: YouTube lo richiede per sbloccare i flussi. */
    private val deno: File? = bundled?.let { File(it, "deno.exe") }?.takeIf { it.isFile }

    /** Gli argomenti che dicono a yt-dlp di usare il deno incluso, se c'è. */
    private fun runtimeArgs(): List<String> = deno?.let { listOf("--js-runtimes", "deno:" + it.path) }.orEmpty()

    init {
        Thread({
            // La copia di Xaos; alla prima apertura la si prende da quella inclusa,
            // senza scaricare niente.
            if (!ownExe.isFile) {
                bundled?.let { File(it, "yt-dlp.exe") }?.takeIf { it.isFile }?.let { seed ->
                    runCatching {
                        ownExe.parentFile.mkdirs()
                        seed.copyTo(ownExe, overwrite = true)
                        checkedMarker.writeText(System.currentTimeMillis().toString())
                    }
                }
            }
            exe = ownExe.takeIf { it.isFile } ?: locate("yt-dlp.exe", "yt-dlp.yt-dlp")
            ffmpeg = bundled?.let { File(it, "ffmpeg/ffmpeg.exe") }?.takeIf { it.isFile }
                ?: locate("ffmpeg.exe", "Gyan.FFmpeg", "yt-dlp.FFmpeg")
            // Poi, con calma, la copia di Xaos: scaricata se manca, aggiornata se vecchia.
            refresh(force = false)
        }, "xaos-tools").apply { isDaemon = true }.start()
    }

    private val ownExe = File(xaos.desktop.Settings.appDir, "tools/yt-dlp.exe")
    private val checkedMarker = File(xaos.desktop.Settings.appDir, "tools/yt-dlp.checked")
    private val refreshLock = Any()

    /**
     * Si assicura che la copia di Xaos ci sia e sia aggiornata. Senza [force]
     * controlla al massimo una volta al giorno. Restituisce true se ora c'è una
     * copia utilizzabile.
     */
    private fun refresh(force: Boolean): Boolean = synchronized(refreshLock) {
        runCatching {
            if (!ownExe.isFile) {
                ownExe.parentFile.mkdirs()
                val tmp = File(ownExe.path + ".download")
                val client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build()
                val request = java.net.http.HttpRequest.newBuilder(java.net.URI(RELEASE_URL))
                    .header("User-Agent", "Xaos Desktop")
                    .timeout(java.time.Duration.ofMinutes(3))
                    .build()
                val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
                if (response.statusCode() != 200 || tmp.length() < 1_000_000) { tmp.delete(); return@runCatching false }
                java.nio.file.Files.move(tmp.toPath(), ownExe.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                checkedMarker.writeText(System.currentTimeMillis().toString())
            } else {
                val last = checkedMarker.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0L
                if (force || System.currentTimeMillis() - last > DAY_MS) {
                    // La copia standalone sa aggiornarsi da sola.
                    val process = ProcessBuilder(ownExe.path, "-U").redirectErrorStream(true).start()
                    process.inputStream.readAllBytes()
                    process.waitFor(3, java.util.concurrent.TimeUnit.MINUTES)
                    checkedMarker.writeText(System.currentTimeMillis().toString())
                }
            }
            exe = ownExe
            true
        }.getOrDefault(false)
    }

    private val _search = MutableStateFlow<OnlineSearch>(OnlineSearch.Idle)
    val search: StateFlow<OnlineSearch> = _search.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val processes = ConcurrentHashMap<String, Process>()
    private val json = Json { ignoreUnknownKeys = true }

    fun clearSearch() { _search.value = OnlineSearch.Idle }

    /** `ytsearchN:` cerca su YouTube; `--flat-playlist` evita di risolvere ogni risultato. */
    fun search(query: String, limit: Int = 15) {
        val yt = exe ?: return
        if (query.isBlank()) return
        scope.launch(Dispatchers.IO) {
            _search.value = OnlineSearch.Searching
            val result = runCatching {
                val process = ProcessBuilder(
                    listOf(yt.path, "ytsearch$limit:$query", "--dump-json", "--flat-playlist", "--no-warnings", "--ignore-errors") +
                        runtimeArgs(),
                ).redirectErrorStream(false).start()
                val out = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
                process.waitFor()
                out.lineSequence().filter { it.isNotBlank() }.mapNotNull { parse(it) }.toList()
            }
            _search.value = result.fold(
                onSuccess = { OnlineSearch.Results(query, it) },
                onFailure = { OnlineSearch.Failed(it.message ?: "Ricerca non riuscita") },
            )
        }
    }

    /** Scarica [track] in [folder]; alla fine [onDone] riceve il file MP3. */
    fun download(track: OnlineTrack, folder: File, onDone: (File) -> Unit) {
        if (exe == null) return
        val ff = ffmpeg ?: return
        if (jobs[track.id]?.isActive == true) return
        set(track.id, DownloadState.Queued)
        jobs[track.id] = scope.launch(Dispatchers.IO) {
            val result = runCatching { runDownload(track, folder, ff) }
                .recoverCatching { failure ->
                    // Un 403 vuol dire quasi sempre yt-dlp vecchio: lo si aggiorna
                    // e si ritenta una volta, senza che l'utente debba fare niente.
                    val message = failure.message.orEmpty()
                    if (!message.contains("403") && !message.contains("Forbidden", ignoreCase = true)) throw failure
                    set(track.id, DownloadState.Queued)
                    if (!refresh(force = true)) throw failure
                    runDownload(track, folder, ff)
                }
            processes.remove(track.id)
            result.onSuccess { file ->
                set(track.id, DownloadState.Completed(file))
                onDone(file)
            }.onFailure {
                set(track.id, DownloadState.Failed(it.message ?: "Download non riuscito"))
            }
        }
    }

    private fun runDownload(track: OnlineTrack, folder: File, ff: File): File {
        val yt = exe ?: error("yt-dlp non trovato")
        folder.mkdirs()
        val process = ProcessBuilder(
            listOf(yt.path, track.url) + runtimeArgs() + listOf(
            "-f", "bestaudio/best",
            "-x", "--audio-format", "mp3", "--audio-quality", "0",
            "--embed-metadata", "--embed-thumbnail",
            "--no-playlist", "--no-warnings",
            "--ffmpeg-location", ff.parent,
            "-o", "${folder.path}${File.separator}%(title)s.%(ext)s",
            // Il percorso finale, dopo la conversione: è l'unico modo
            // affidabile di saperlo, yt-dlp ripulisce i caratteri del titolo.
            "--print", "after_move:XAOSFILE %(filepath)s",
            "--no-simulate", "--progress", "--newline",
            "--progress-template", "download:XAOSPROG %(progress._percent_str)s",
            ),
        ).redirectErrorStream(true).start()
        processes[track.id] = process
        var finalPath: String? = null
        var lastError: String? = null
        process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            when {
                line.startsWith("XAOSPROG") -> {
                    val pct = line.removePrefix("XAOSPROG").trim().removeSuffix("%").trim().toFloatOrNull()
                    if (pct != null) {
                        set(track.id, if (pct >= 100f) DownloadState.Converting else DownloadState.Running(pct / 100f))
                    }
                }
                line.startsWith("XAOSFILE ") -> finalPath = line.removePrefix("XAOSFILE ").trim()
                line.startsWith("ERROR") -> lastError = line.removePrefix("ERROR:").trim()
                line.startsWith("[ExtractAudio]") || line.startsWith("[EmbedThumbnail]") ->
                    set(track.id, DownloadState.Converting)
            }
        }
        val code = process.waitFor()
        val file = finalPath?.let(::File)
        if (code != 0 || file == null || !file.isFile) error(lastError ?: "yt-dlp ha terminato con errore ($code)")
        return file
    }

    fun cancel(id: String) {
        processes.remove(id)?.destroy()
        jobs.remove(id)?.cancel()
        _downloads.update { it - id }
    }

    private fun set(id: String, state: DownloadState) = _downloads.update { it + (id to state) }

    private fun parse(line: String): OnlineTrack? = runCatching {
        val o = json.parseToJsonElement(line).jsonObject
        fun str(key: String) = o[key]?.jsonPrimitive?.contentOrNull
        val id = str("id") ?: return null
        OnlineTrack(
            id = id,
            title = str("title") ?: return null,
            uploader = str("channel") ?: str("uploader") ?: "",
            durationMs = ((o["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong(),
            url = str("url") ?: "https://www.youtube.com/watch?v=$id",
            thumbnail = bestThumbnail(o),
        )
    }.getOrNull()

    /** La miniatura più piccola che sia almeno di 120 pixel: in lista basta e pesa poco. */
    private fun bestThumbnail(o: JsonObject): String? = runCatching {
        o["thumbnails"]?.jsonArray
            ?.map { it.jsonObject }
            ?.sortedBy { it["width"]?.jsonPrimitive?.double ?: 0.0 }
            ?.firstOrNull { (it["width"]?.jsonPrimitive?.double ?: 0.0) >= 120.0 }
            ?.get("url")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private companion object {
        /** L'ultima versione ufficiale per Windows, dalle release di GitHub. */
        const val RELEASE_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
        const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * Nel PATH, poi fra i collegamenti di winget, poi nelle cartelle dei
         * soli pacchetti che ci interessano ([packages], per prefisso del
         * nome). Mai una scansione di tutti i pacchetti: su certi PC sono
         * decine di migliaia di file.
         */
        fun locate(name: String, vararg packages: String): File? {
            System.getenv("PATH")?.split(File.pathSeparator)
                ?.map { File(it, name) }?.firstOrNull { it.isFile }?.let { return it }
            val local = System.getenv("LOCALAPPDATA") ?: return null
            File(local, "Microsoft/WinGet/Links/$name").takeIf { it.isFile }?.let { return it }
            val root = File(local, "Microsoft/WinGet/Packages")
            val dirs = root.listFiles()?.filter { d -> packages.any { d.name.startsWith(it + "_") } }.orEmpty()
            return dirs.asSequence()
                .flatMap { it.walkTopDown().maxDepth(3) }
                .firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }
        }
    }
}
