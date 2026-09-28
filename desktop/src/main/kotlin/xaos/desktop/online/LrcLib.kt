package xaos.desktop.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Un testo trovato su LRCLIB: semplice, sincronizzato, o entrambi. */
@Serializable
data class LrcResult(
    val id: Long = 0,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/**
 * LRCLIB (lrclib.net): un archivio libero e gratuito di testi, anche
 * sincronizzati nel formato .lrc, che non chiede account né chiavi.
 */
object LrcLib {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Cerca per titolo e artista; se non trova niente riprova col solo titolo,
     * che salva i casi in cui l'artista è scritto in modo diverso. I risultati
     * con il testo sincronizzato vengono prima, e fra questi quelli con la
     * durata più vicina al brano.
     */
    suspend fun search(title: String, artist: String, durationMs: Long): List<LrcResult> = withContext(Dispatchers.IO) {
        val first = query(mapOf("track_name" to title, "artist_name" to artist))
        val results = first.ifEmpty { query(mapOf("q" to "$title $artist")) }.ifEmpty { query(mapOf("q" to title)) }
        val seconds = durationMs / 1000.0
        results
            .filter { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() }
            .sortedWith(
                compareBy<LrcResult>({ it.syncedLyrics.isNullOrBlank() }, { kotlin.math.abs((it.duration ?: 0.0) - seconds) })
            )
            .take(12)
    }

    private fun query(params: Map<String, String>): List<LrcResult> = runCatching {
        val qs = params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
        val request = HttpRequest.newBuilder(URI("https://lrclib.net/api/search?$qs"))
            // LRCLIB chiede di identificare il client.
            .header("User-Agent", "Xaos Desktop 1.0 (https://github.com/XaosWasHere/xaos-music-player)")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() != 200) return emptyList()
        json.decodeFromString<List<LrcResult>>(response.body())
    }.getOrDefault(emptyList())
}
