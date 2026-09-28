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
 * Qui yt-dlp e ffmpeg non viaggiano dentro l'app: si usano quelli installati sul
 * PC (con winget, o comunque nel PATH). Il formato è lo stesso del telefono:
 * miglior audio, convertito in MP3 alla qualità massima, con metadati e
 * copertina incorporati, così il brano arriva in libreria già pronto.
 */
class YtDlp(private val scope: CoroutineScope) {

    val exe: File? = locate("yt-dlp.exe")
    val ffmpeg: File? = locate("ffmpeg.exe")
    val available: Boolean get() = exe != null && ffmpeg != null

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
                    yt.path, "ytsearch$limit:$query",
                    "--dump-json", "--flat-playlist", "--no-warnings", "--ignore-errors",
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
        val yt = exe ?: return
        val ff = ffmpeg ?: return
        if (jobs[track.id]?.isActive == true) return
        set(track.id, DownloadState.Queued)
        jobs[track.id] = scope.launch(Dispatchers.IO) {
            val result = runCatching {
                folder.mkdirs()
                val process = ProcessBuilder(
                    yt.path, track.url,
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
                file
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
        /** Nel PATH, poi fra i collegamenti e i pacchetti di winget. */
        fun locate(name: String): File? {
            System.getenv("PATH")?.split(File.pathSeparator)
                ?.map { File(it, name) }?.firstOrNull { it.isFile }?.let { return it }
            val local = System.getenv("LOCALAPPDATA") ?: return null
            File(local, "Microsoft/WinGet/Links/$name").takeIf { it.isFile }?.let { return it }
            return File(local, "Microsoft/WinGet/Packages").walkTopDown().maxDepth(5)
                .firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }
        }
    }
}
