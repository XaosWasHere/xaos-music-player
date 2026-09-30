package xaos.desktop.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Il progetto su GitHub, e se c'è una release più recente di questa.
 *
 * Una sola richiesta all'avvio, senza account né token: l'API pubblica di
 * GitHub dice qual è l'ultima release. Senza rete semplicemente non si sa, e
 * il pallino accanto al logo resta fermo.
 */
object ReleaseCheck {

    const val REPO_URL = "https://github.com/XaosWasHere/xaos-music-player"
    const val RELEASES_URL = "$REPO_URL/releases/latest"
    private const val API_URL = "https://api.github.com/repos/XaosWasHere/xaos-music-player/releases/latest"

    /** La versione di questa app, passata dal build (`-Dxaos.version`). */
    val currentVersion: String? = System.getProperty("xaos.version")

    @Serializable
    private data class Release(val tag_name: String = "", val html_url: String = "", val draft: Boolean = false, val prerelease: Boolean = false)

    data class Latest(val version: String, val url: String) {
        val isNewer: Boolean get() = currentVersion?.let { compare(version, it) > 0 } ?: false
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): Latest? = withContext(Dispatchers.IO) {
        runCatching {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
            val request = HttpRequest.newBuilder(URI(API_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Xaos Desktop ${currentVersion ?: ""}".trim())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return@runCatching null
            val release = json.decodeFromString<Release>(response.body())
            if (release.draft || release.prerelease || release.tag_name.isBlank()) null
            else Latest(release.tag_name.removePrefix("v"), release.html_url.ifBlank { RELEASES_URL })
        }.getOrNull()
    }

    /** Apre [url] nel browser predefinito. */
    fun open(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }
            .onFailure { runCatching { ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start() } }
    }

    /** "1.10.0" viene dopo "1.9.2": si confrontano i numeri, non il testo. */
    fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
