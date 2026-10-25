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
    suspend fun search(title: String, artist: String, durationMs: Long, strict: Boolean = false): List<LrcResult> = withContext(Dispatchers.IO) {
        val first = query(mapOf("track_name" to title, "artist_name" to artist), strict)
        val results = first.ifEmpty { query(mapOf("q" to "$title $artist"), strict) }.ifEmpty { query(mapOf("q" to title), strict) }
        val seconds = durationMs / 1000.0
        results
            .filter { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() }
            .sortedWith(
                compareBy<LrcResult>({ it.syncedLyrics.isNullOrBlank() }, { kotlin.math.abs((it.duration ?: 0.0) - seconds) })
            )
            .take(12)
    }

    /**
     * Con [strict] un errore di rete o una risposta strana non valgono come
     * "nessun testo": si lancia [Unavailable], così chi cerca per tutta la
     * libreria non segna come cercati i brani mentre la rete manca.
     */
    private fun query(params: Map<String, String>, strict: Boolean): List<LrcResult> = runCatching {
        val qs = params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
        val request = HttpRequest.newBuilder(URI("https://lrclib.net/api/search?$qs"))
            // LRCLIB chiede di identificare il client.
            .header("User-Agent", "Xaos Desktop 1.0 (https://github.com/XaosWasHere/xaos-music-player)")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        // LRCLIB è un servizio gratuito e spesso risponde "occupato, riprova
        // tra un momento" (503) o "troppe richieste" (429): si aspetta e si
        // riprova, sempre più a lungo, prima di dire che non risponde.
        var attempt = 0
        while (true) {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            val code = response.statusCode()
            if (code == 200) return@runCatching json.decodeFromString<List<LrcResult>>(response.body())
            if (code !in RETRY_CODES || attempt >= RETRIES) throw Unavailable("HTTP $code")
            val asked = response.headers().firstValue("Retry-After").orElse(null)?.toLongOrNull()?.times(1000)
            Thread.sleep((asked ?: (RETRY_BASE_MS shl attempt)).coerceAtMost(RETRY_MAX_MS))
            attempt++
        }
        @Suppress("UNREACHABLE_CODE")
        emptyList<LrcResult>()
    }.getOrElse { e -> if (strict) throw (e as? Unavailable ?: Unavailable(e.message ?: e.toString())) else emptyList() }

    /** LRCLIB non ha risposto (rete assente, server giù, limite di richieste). */
    class Unavailable(message: String) : Exception(message)

    /** Le risposte che vogliono dire "riprova tra poco". */
    private val RETRY_CODES = setOf(429, 502, 503, 504)
    private const val RETRIES = 4
    private const val RETRY_BASE_MS = 2_000L
    private const val RETRY_MAX_MS = 30_000L
}
