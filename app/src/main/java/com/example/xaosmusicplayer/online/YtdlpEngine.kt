package com.example.xaosmusicplayer.online

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * yt-dlp incorporato nell'app.
 *
 * La libreria impacchetta un interprete Python e i binari di yt-dlp e ffmpeg
 * come librerie native: [ensureReady] li estrae al primo utilizzo, e da quel
 * momento in poi ricerca e download girano interamente in locale, senza app
 * esterne.
 *
 * Tutte le chiamate a yt-dlp sono bloccanti e vanno fuori dal thread principale.
 */
class YtdlpEngine(private val context: Context) {

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    /** Un solo aggiornamento automatico per avvio: se non basta, non insiste. */
    @Volatile
    private var extractorsRefreshed = false

    /**
     * Estrae e inizializza i binari. Idempotente e sicura da chiamare da più
     * corotine: l'inizializzazione avviene una volta sola.
     */
    suspend fun ensureReady(): Result<Unit> = withContext(Dispatchers.IO) {
        if (initialized) return@withContext Result.success(Unit)
        initMutex.withLock {
            if (initialized) return@withContext Result.success(Unit)
            val result = runCatching {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                initialized = true
            }.onFailure { Log.e(TAG, "Inizializzazione di yt-dlp fallita", it) }

            result
        }
    }

    /**
     * Cerca su YouTube tramite lo pseudo-URL `ytsearchN:` di yt-dlp.
     *
     * `--flat-playlist` evita di risolvere ogni singolo risultato: interessano
     * titolo e durata, non i formati disponibili, e risolverli tutti
     * richiederebbe una richiesta di rete per brano.
     */
    suspend fun search(query: String, limit: Int = 25): Result<List<OnlineTrack>> =
        withContext(Dispatchers.IO) {
            ensureReady().exceptionOrNull()?.let { return@withContext Result.failure(it) }

            runCatching {
                val request = YoutubeDLRequest("ytsearch$limit:$query").apply {
                    addOption("--dump-json")
                    addOption("--flat-playlist")
                    addOption("--no-warnings")
                    addOption("--ignore-errors")
                }
                val response = YoutubeDL.getInstance().execute(request, null, null)
                response.out
                    .lineSequence()
                    .filter { it.startsWith("{") }
                    .mapNotNull { parseTrack(it) }
                    .toList()
            }.onFailure { Log.e(TAG, "Ricerca online fallita", it) }
        }

    private fun parseTrack(json: String): OnlineTrack? = runCatching {
        val obj = JSONObject(json)
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        OnlineTrack(
            id = id,
            title = obj.optString("title").ifBlank { "Senza titolo" },
            // A seconda dell'estrattore l'autore sta in uno di questi campi.
            uploader = listOf("uploader", "channel", "artist")
                .firstNotNullOfOrNull { key -> obj.optString(key).takeIf { it.isNotBlank() } }
                ?: "Sconosciuto",
            durationMs = (obj.optDouble("duration", 0.0) * 1000).toLong(),
            thumbnailUrl = obj.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: obj.optJSONArray("thumbnails")?.let { arr ->
                    // L'ultima miniatura è la più grande.
                    (arr.length() - 1).takeIf { it >= 0 }
                        ?.let { arr.optJSONObject(it)?.optString("url") }
                }?.takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    /**
     * Scarica [track] come MP3 e lo consegna alla libreria locale.
     *
     * yt-dlp scrive in una cartella privata dell'app — sempre scrivibile e non
     * soggetta allo scoped storage — e solo a file completo il brano viene
     * pubblicato in MediaStore, così la libreria non mostra mai un download a metà.
     */
    suspend fun download(
        track: OnlineTrack,
        onProgress: (Float, Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureReady().exceptionOrNull()?.let { return@withContext Result.failure(it) }

        val workDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        // Ogni download ha la sua cartella: così individuare il file prodotto è
        // banale, qualunque nome yt-dlp gli dia.
        val trackDir = File(workDir, track.id).apply { mkdirs() }

        try {
            var failure = tryAllClients(track, trackDir, onProgress)
            if (failure == null) return@withContext publish(track, trackDir)

            // Se ogni client viene respinto, quasi sempre non è il video: è
            // yt-dlp rimasto indietro rispetto all'ultima modifica di YouTube.
            // Succede ogni poche settimane, e senza questo passaggio i download
            // resterebbero rotti finché l'utente non pensa ad aggiornare a mano.
            if (failure.isStreamRejected() && !extractorsRefreshed) {
                extractorsRefreshed = true
                Log.i(TAG, "Tutti i client respinti: aggiorno yt-dlp e riprovo")
                updateYtdlp()
                failure = tryAllClients(track, trackDir, onProgress)
                if (failure == null) return@withContext publish(track, trackDir)
            }

            // L'aggiornamento può aver portato una versione di yt-dlp che
            // pretende un Python più recente di quello impacchettato. In quel
            // caso il motore è inavviabile e va riportato alla copia originale,
            // altrimenti resterebbe rotta anche la ricerca.
            if (failure.isEngineUnrunnable()) {
                Log.w(TAG, "yt-dlp aggiornato ma inavviabile: ripristino la copia inclusa")
                restoreBundledYtdlp()
                return@withContext Result.failure(
                    IllegalStateException(
                        "Aggiornamento incompatibile, motore ripristinato. Riprova."
                    )
                )
            }

            Log.e(TAG, "Download fallito con tutti i client", failure)
            // Il testo grezzo di yt-dlp ("Requested format is not available")
            // fa pensare a un problema del brano o dell'app. Nei fatti è
            // YouTube che rifiuta la richiesta, spesso solo per un po': lo
            // stesso video può funzionare qualche minuto dopo.
            if (failure.isStreamRejected()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "YouTube ha rifiutato il download. Riprova fra qualche minuto."
                    )
                )
            }
            Result.failure(failure)
        } catch (error: Throwable) {
            Log.e(TAG, "Download fallito", error)
            Result.failure(error)
        } finally {
            trackDir.deleteRecursively()
        }
    }

    /**
     * Prova ogni client in sequenza. Restituisce null se uno ha funzionato,
     * altrimenti l'ultimo errore.
     *
     * YouTube lega l'URL del flusso al client che l'ha richiesto e ne rifiuta
     * alcuni, in modo diverso da video a video: non esiste un client che vada
     * sempre bene, quindi si scende lungo la lista finché uno non passa.
     */
    private fun tryAllClients(
        track: OnlineTrack,
        trackDir: File,
        onProgress: (Float, Long) -> Unit,
    ): Throwable? {
        var lastError: Throwable? = null
        for (client in PLAYER_CLIENTS) {
            for (embedThumbnail in listOf(true, false)) {
                val attempt = runDownload(track, trackDir, embedThumbnail, client, onProgress)
                if (attempt.isSuccess) {
                    Log.i(TAG, "Scaricato con client=${client ?: "default"}")
                    return null
                }
                lastError = attempt.exceptionOrNull()
                // Un tentativo fallito può aver lasciato file parziali.
                trackDir.listFiles()?.forEach { file -> file.delete() }

                // Se il flusso è stato rifiutato non è colpa della copertina:
                // cambiare client è l'unica mossa utile.
                if (lastError.isStreamRejected()) break
            }
        }
        return lastError ?: IllegalStateException("Download fallito")
    }

    private fun publish(track: OnlineTrack, trackDir: File): Result<Unit> {
        val audioFile = trackDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 }
            ?: return Result.failure(IllegalStateException("yt-dlp non ha prodotto alcun file"))
        return LibraryPublisher(context).publish(
            source = audioFile,
            title = track.title,
            artist = track.uploader,
        )
    }

    /** Vero se l'errore indica che YouTube ha rifiutato il flusso a questo client. */
    private fun Throwable?.isStreamRejected(): Boolean {
        val text = this?.message.orEmpty()
        return text.contains("403") ||
            text.contains("Requested format is not available") ||
            text.contains("Sign in to confirm")
    }

    /**
     * Cancella la versione che la libreria crede di avere installato.
     *
     * `updateYoutubeDL` non guarda i file: confronta la versione remota con
     * quella che ha annotato, e se coincidono risponde ALREADY_UP_TO_DATE senza
     * fare nulla. Basta però che l'annotazione si disallinei dai file — o che
     * la stessa versione abbia smesso di funzionare perché YouTube è cambiato —
     * perché l'aggiornamento diventi un no-op proprio quando serve. Azzerarla
     * costringe a riscaricare davvero.
     *
     * Dipende dai nomi interni della libreria: se cambiassero, questa funzione
     * non farebbe nulla e si tornerebbe al comportamento predefinito.
     */
    private fun forgetInstalledVersion() {
        runCatching {
            context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(DLP_VERSION_KEY)
                .apply()
        }.onFailure { Log.w(TAG, "Versione installata non azzerata", it) }
    }

    /** Vero se yt-dlp non parte proprio, tipicamente per un Python troppo vecchio. */
    private fun Throwable?.isEngineUnrunnable(): Boolean =
        this?.message.orEmpty().contains("unsupported version of Python")

    /**
     * Riporta yt-dlp alla copia impacchettata nella libreria.
     *
     * Cancellare la cartella basta: il prossimo init la riestrae. Serve solo
     * come rete di sicurezza dopo un aggiornamento incompatibile.
     */
    private fun restoreBundledYtdlp() {
        runCatching {
            File(File(context.noBackupFilesDir, "youtubedl-android"), "yt-dlp").deleteRecursively()
            initialized = false
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            initialized = true
        }.onFailure { Log.e(TAG, "Ripristino di yt-dlp fallito", it) }
    }

    private fun runDownload(
        track: OnlineTrack,
        destination: File,
        embedThumbnail: Boolean,
        playerClient: String?,
        onProgress: (Float, Long) -> Unit,
    ): Result<Unit> = runCatching {
        val request = YoutubeDLRequest(track.pageUrl).apply {
            // "bestaudio/best" e non "bestaudio": su molti video YouTube non
            // espone un formato solo-audio al client usato da yt-dlp, e senza
            // ripiego il download muore con "Requested format is not available".
            // Il ripiego scarica il flusso completo, da cui -x estrae l'audio.
            addOption("-f", "bestaudio/best")
            if (playerClient != null) {
                addOption("--extractor-args", "youtube:player_client=$playerClient")
            }
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "0")
            addOption("--embed-metadata")
            if (embedThumbnail) addOption("--embed-thumbnail")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("-o", "${destination.absolutePath}/%(title)s.%(ext)s")
        }
        YoutubeDL.getInstance().execute(request, track.id) { progress, eta, _ ->
            onProgress(progress / 100f, eta)
        }
        Unit
    }

    /** Interrompe un download in corso, se yt-dlp lo sta ancora eseguendo. */
    fun cancel(trackId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(trackId) }
    }

    /**
     * Aggiorna yt-dlp all'ultima versione.
     *
     * Gli estrattori di YouTube invecchiano in settimane, mentre quello
     * impacchettato nella libreria è fermo alla data di rilascio: senza questa
     * via d'uscita i download comincerebbero a fallire da soli e non ci sarebbe
     * modo di rimediare dall'app.
     *
     * È volutamente manuale. Aggiornare in automatico è un'arma a doppio taglio:
     * se una versione futura di yt-dlp richiedesse un Python più recente di
     * quello impacchettato, l'aggiornamento riuscirebbe e lascerebbe il motore
     * inavviabile. Meglio che sia una scelta, presa quando qualcosa non va.
     */
    suspend fun updateYtdlp(): Result<String> = withContext(Dispatchers.IO) {
        ensureReady().exceptionOrNull()?.let { return@withContext Result.failure(it) }
        forgetInstalledVersion()
        runCatching {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            Log.i(TAG, "Aggiornamento yt-dlp: $status")
            when (status?.name) {
                "DONE" -> "Motore aggiornato"
                "ALREADY_UP_TO_DATE" -> "Motore già aggiornato"
                else -> "Aggiornamento: ${status?.name ?: "esito sconosciuto"}"
            }
        }.onFailure { Log.e(TAG, "Aggiornamento di yt-dlp fallito", it) }
    }

    private companion object {
        const val TAG = "YtdlpEngine"

        /** Preferenze interne di youtubedl-android, dove annota cosa ha installato. */
        const val LIBRARY_PREFS = "youtubedl-android"
        const val DLP_VERSION_KEY = "dlpVersion"

        /**
         * Client YouTube da provare in ordine, dal predefinito ai ripieghi.
         *
         * `null` lascia decidere yt-dlp, che di norma è la scelta giusta. Gli
         * altri sono client che YouTube tratta con meno sospetto quando quello
         * predefinito riceve un 403; l'ordine è dal più al meno affidabile
         * nell'esperienza corrente, e va rivisto quando YouTube cambia le regole.
         */
        val PLAYER_CLIENTS = listOf(null, "tv", "android_vr", "ios", "web_safari")
    }
}
