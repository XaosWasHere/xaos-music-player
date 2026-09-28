package xaos.desktop.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xaos.desktop.library.Library
import xaos.desktop.library.Track
import java.io.File
import java.util.concurrent.TimeUnit

/** Com'è collegato il telefono in questo momento. */
sealed interface PhoneState {
    /** adb non è installato o non si trova. */
    data object NoAdb : PhoneState
    data object Disconnected : PhoneState
    /** Collegato, ma sul telefono non è stato ancora accettato questo PC. */
    data class Unauthorized(val serial: String) : PhoneState
    /**
     * [appInstalled]: se sul telefono c'è Xaos per Android. [abi]: il
     * processore, per sapere se l'APK incluso ci gira.
     */
    data class Connected(
        val serial: String,
        val name: String,
        val appInstalled: Boolean,
        val abi: String,
    ) : PhoneState
}

/** Un brano da mandare: quale file, e dove finisce sul telefono. */
data class SyncItem(
    val track: Track,
    val file: File,
    val remoteRel: String,
    val size: Long,
)

/** Cosa manca sul telefono rispetto alla libreria del PC. */
data class SyncPlan(
    val missing: List<SyncItem>,
    val alreadyThere: Int,
    val bytesToSend: Long,
    val freeBytes: Long?,
) {
    val fits: Boolean get() = freeBytes == null || bytesToSend < freeBytes - SPACE_MARGIN

    companion object {
        /** Mezzo giga di margine: un telefono pieno fino all'ultimo byte smette di funzionare bene. */
        const val SPACE_MARGIN = 512L * 1024 * 1024
    }
}

/** L'installazione dell'app Android sul telefono. */
sealed interface AppInstall {
    data object Idle : AppInstall
    data object Installing : AppInstall
    data object Done : AppInstall
    data class Failed(val message: String) : AppInstall
}

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Planning : SyncStatus
    data class Running(
        val doneFiles: Int,
        val totalFiles: Int,
        val doneBytes: Long,
        val totalBytes: Long,
        val currentName: String,
    ) : SyncStatus {
        val fraction: Float get() = if (totalBytes <= 0) 0f else doneBytes.toFloat() / totalBytes
    }
    data class Done(val sent: Int, val failed: Int, val cancelled: Boolean) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Il collegamento col telefono, tutto via adb.
 *
 * La sincronizzazione aggiunge e basta: copia i brani che sul telefono mancano,
 * nella stessa struttura di cartelle del PC sotto [remoteRoot], e non cancella
 * mai niente. Un file già presente con la stessa dimensione si considera già
 * inviato; con una dimensione diversa (un invio interrotto a metà) si rimanda.
 *
 * [remoteRoot] è la cartella dove l'app Android salva anche i download, quindi
 * sul telefono i brani arrivati dal PC finiscono nella libreria di Xaos come
 * tutti gli altri.
 */
class PhoneSync(
    private val scope: CoroutineScope,
    val remoteRoot: String = "/sdcard/Music/Xaos",
) {
    private val adb: File? = locateAdb()

    private val _state = MutableStateFlow<PhoneState>(if (adb == null) PhoneState.NoAdb else PhoneState.Disconnected)
    val state: StateFlow<PhoneState> = _state.asStateFlow()

    private val _plan = MutableStateFlow<SyncPlan?>(null)
    val plan: StateFlow<SyncPlan?> = _plan.asStateFlow()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var syncJob: Job? = null
    @Volatile private var currentPush: Process? = null

    /** Controlla ogni due secondi se il telefono c'è: collegarlo basta. */
    fun startWatching() {
        if (adb == null) return
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val next = detect()
                if (next != _state.value) {
                    _state.value = next
                    if (next !is PhoneState.Connected) _plan.value = null
                }
                delay(POLL_MS)
            }
        }
    }

    /**
     * Confronta la libreria con quello che c'è sul telefono.
     *
     * Con [preferMp3] un brano che ha la copia MP3 viaggia in MP3; in ogni
     * caso sul telefono finisce nella cartella del suo album, senza la
     * sottocartella "MP3" che sul PC serve solo a tenere separate le copie.
     */
    fun plan(root: File, tracks: List<Track>, preferMp3: Boolean) {
        val phone = _state.value as? PhoneState.Connected ?: return
        if (_status.value is SyncStatus.Running) return
        scope.launch(Dispatchers.IO) {
            // Il ricalcolo subito dopo un invio non deve coprire l'esito.
            if (_status.value !is SyncStatus.Done) _status.value = SyncStatus.Planning
            val result = runCatching {
                val remote = listRemote(phone.serial)
                val matcher = SongMatcher(listPhoneSongs(phone.serial))
                val items = tracks.mapNotNull { track ->
                    val file = File(if (preferMp3) track.mobilePath ?: track.path else track.path)
                    val rel = remoteRelative(root, file) ?: return@mapNotNull null
                    SyncItem(track, file, rel, file.length())
                }
                // Già presente se Xaos l'ha mandato (stesso percorso e stessa
                // dimensione), oppure se il telefono ha lo stesso brano altrove.
                val missing = items.filter { item ->
                    remote[item.remoteRel] != item.size && !matcher.isOnPhone(item.track)
                }
                SyncPlan(
                    missing = missing,
                    alreadyThere = items.size - missing.size,
                    bytesToSend = missing.sumOf { it.size },
                    freeBytes = freeSpace(phone.serial),
                )
            }
            result.onSuccess {
                _plan.value = it
                if (_status.value is SyncStatus.Planning) _status.value = SyncStatus.Idle
            }
            result.onFailure { _status.value = SyncStatus.Failed(it.message ?: "Impossibile leggere il telefono") }
        }
    }

    /** Invia i brani mancanti; [tracks] è la libreria intera, per ricalcolare il piano alla fine. */
    fun sync(root: File, tracks: List<Track>, preferMp3: Boolean) {
        val phone = _state.value as? PhoneState.Connected ?: return
        val plan = _plan.value ?: return
        if (plan.missing.isEmpty() || syncJob?.isActive == true) return

        syncJob = scope.launch(Dispatchers.IO) {
            val total = plan.missing.size
            var sent = 0
            var failed = 0
            var doneBytes = 0L
            val pushed = mutableListOf<String>()

            for (item in plan.missing) {
                if (!isActive) break
                _status.value = SyncStatus.Running(sent + failed, total, doneBytes, plan.bytesToSend, item.file.name)
                val remote = "$remoteRoot/${item.remoteRel}"
                val ok = push(phone.serial, item.file, remote)
                if (ok) { sent++; pushed += remote } else failed++
                doneBytes += item.size
            }

            val cancelled = !isActive
            // Si avvisa il telefono anche se l'invio è stato annullato: i brani
            // già arrivati devono comparire comunque.
            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                announce(phone.serial, pushed)
            }
            _status.value = SyncStatus.Done(sent, failed, cancelled)
            plan(root, tracks, preferMp3)
        }
    }

    fun cancel() {
        syncJob?.cancel()
        currentPush?.destroy()
    }

    // ---------------------------------------------------------- app Android

    /**
     * L'APK di Xaos per Android incluso nell'app desktop, se c'è. Sta fra le
     * risorse del pacchetto, non dentro il jar: è un file da passare ad adb.
     */
    val bundledApk: File? = System.getProperty("compose.application.resources.dir")
        ?.let { File(it, BUNDLED_APK) }
        ?.takeIf { it.isFile }

    private val _install = MutableStateFlow<AppInstall>(AppInstall.Idle)
    val install: StateFlow<AppInstall> = _install.asStateFlow()

    /** Se l'APK incluso può girare sul telefono collegato. */
    fun canInstallOn(phone: PhoneState.Connected): Boolean =
        bundledApk != null && (phone.abi.isEmpty() || phone.abi == BUNDLED_ABI)

    /** Installa l'app Android: va chiamato solo dopo la conferma dell'utente. */
    fun installApp() {
        val phone = _state.value as? PhoneState.Connected ?: return
        val apk = bundledApk ?: return
        if (_install.value is AppInstall.Installing) return
        scope.launch(Dispatchers.IO) {
            _install.value = AppInstall.Installing
            val out = run(listOf("-s", phone.serial, "install", "-r", apk.path), timeoutS = INSTALL_TIMEOUT_S)
            val installed = isAppInstalled(phone.serial)
            if (installed) {
                _state.value = phone.copy(appInstalled = true)
                _install.value = AppInstall.Done
            } else {
                // adb scrive il motivo come "Failure [INSTALL_FAILED_...]".
                val reason = out?.lineSequence()?.firstOrNull { it.contains("Failure") }?.trim()
                _install.value = AppInstall.Failed(reason ?: "Installazione non riuscita")
            }
        }
    }

    /** Apre Xaos sul telefono, dopo l'installazione. */
    fun launchApp() {
        val phone = _state.value as? PhoneState.Connected ?: return
        scope.launch(Dispatchers.IO) {
            shell(phone.serial, "monkey -p $APP_PACKAGE -c android.intent.category.LAUNCHER 1")
        }
    }

    fun dismissInstall() { _install.value = AppInstall.Idle }

    private fun isAppInstalled(serial: String): Boolean =
        shell(serial, "pm list packages $APP_PACKAGE")
            ?.lineSequence()
            ?.any { it.trim() == "package:$APP_PACKAGE" } == true

    fun dismissResult() {
        if (_status.value is SyncStatus.Done || _status.value is SyncStatus.Failed) _status.value = SyncStatus.Idle
    }

    // ---------------------------------------------------------------- adb

    private fun detect(): PhoneState {
        val out = run(listOf("devices", "-l"), timeoutS = 5) ?: return PhoneState.Disconnected
        val line = out.lines().drop(1).map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("*") }
            ?: return PhoneState.Disconnected
        val parts = line.split(Regex("\\s+"))
        val serial = parts[0]
        return when (parts.getOrNull(1)) {
            "device" -> {
                val known = _state.value
                if (known is PhoneState.Connected && known.serial == serial) known
                else PhoneState.Connected(
                    serial = serial,
                    name = deviceName(serial, parts),
                    appInstalled = isAppInstalled(serial),
                    abi = shell(serial, "getprop ro.product.cpu.abi")?.trim().orEmpty(),
                )
            }
            "unauthorized" -> PhoneState.Unauthorized(serial)
            else -> PhoneState.Disconnected
        }
    }

    private fun deviceName(serial: String, parts: List<String>): String {
        val custom = shell(serial, "settings get global device_name")?.trim()
        if (!custom.isNullOrBlank() && custom != "null") return custom
        val market = shell(serial, "getprop ro.product.marketname")?.trim()
        if (!market.isNullOrBlank()) return market
        return parts.firstOrNull { it.startsWith("model:") }?.removePrefix("model:")?.replace('_', ' ') ?: serial
    }

    /** Percorso relativo → dimensione, per tutti i file già sotto [remoteRoot]. */
    private fun listRemote(serial: String): Map<String, Long> {
        val out = shell(serial, "find ${q(remoteRoot)} -type f -exec stat -c '%s|%n' {} + 2>/dev/null")
            ?: return emptyMap()
        val prefix = "$remoteRoot/"
        return out.lineSequence()
            .mapNotNull { line ->
                val bar = line.indexOf('|')
                if (bar <= 0) return@mapNotNull null
                val size = line.substring(0, bar).toLongOrNull() ?: return@mapNotNull null
                val path = line.substring(bar + 1).trimEnd('\r')
                if (!path.startsWith(prefix)) null else path.removePrefix(prefix) to size
            }
            .toMap()
    }

    /**
     * Tutti i brani musicali che Android conosce, ovunque siano sul telefono,
     * con i tag già letti da MediaStore: niente da scaricare né da decodificare.
     */
    private fun listPhoneSongs(serial: String): List<PhoneSong> {
        val out = shell(
            serial,
            "content query --uri content://media/external/audio/media " +
                "--projection title:artist:duration --where \"is_music!=0\"",
            timeoutS = 60,
        ) ?: return emptyList()
        return SongMatcher.parseContentQuery(out)
    }

    private fun freeSpace(serial: String): Long? {
        val out = shell(serial, "df -k /sdcard") ?: return null
        val cols = out.lines().lastOrNull { it.isNotBlank() }?.trim()?.split(Regex("\\s+")) ?: return null
        return cols.getOrNull(3)?.toLongOrNull()?.times(1024)
    }

    private fun push(serial: String, local: File, remote: String): Boolean {
        val adb = adb ?: return false
        return runCatching {
            val process = ProcessBuilder(adb.path, "-s", serial, "push", local.path, remote)
                .redirectErrorStream(true)
                .start()
            currentPush = process
            process.inputStream.readAllBytes()
            process.waitFor() == 0
        }.getOrDefault(false).also { currentPush = null }
    }

    /**
     * Dice al telefono di indicizzare i file arrivati, a gruppi per non superare
     * la lunghezza massima di un comando. Sui telefoni recenti i file scritti in
     * /sdcard vengono indicizzati da soli; su quelli più vecchi serve questo.
     */
    private fun announce(serial: String, remotePaths: List<String>) {
        remotePaths.chunked(ANNOUNCE_BATCH).forEach { batch ->
            val command = batch.joinToString(" ; ") { path ->
                "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d ${q("file://$path")} >/dev/null"
            }
            shell(serial, command, timeoutS = 60)
        }
    }

    private fun shell(serial: String, command: String, timeoutS: Long = 30): String? =
        run(listOf("-s", serial, "shell", command), timeoutS)

    private fun run(args: List<String>, timeoutS: Long): String? {
        val adb = adb ?: return null
        return runCatching {
            val process = ProcessBuilder(listOf(adb.path) + args).redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (!process.waitFor(timeoutS, TimeUnit.SECONDS)) {
                process.destroy(); return null
            }
            out
        }.getOrNull()
    }

    /** Virgolette singole per la shell del telefono, apostrofi compresi. */
    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Il percorso sul telefono: quello sul PC, senza la sottocartella delle copie MP3. */
    private fun remoteRelative(root: File, file: File): String? = runCatching {
        val folder = Library.collapsedParent(file) ?: return null
        val relFolder = root.toPath().relativize(folder.toPath()).joinToString("/")
        if (relFolder.startsWith("..")) null
        else if (relFolder.isEmpty()) file.name
        else "$relFolder/${file.name}"
    }.getOrNull()

    private companion object {
        const val APP_PACKAGE = "com.example.xaosmusicplayer"
        const val BUNDLED_APK = "xaos-android.apk"
        const val BUNDLED_ABI = "arm64-v8a"
        const val INSTALL_TIMEOUT_S = 180L
        const val POLL_MS = 2_000L
        const val ANNOUNCE_BATCH = 40

        fun locateAdb(): File? {
            val candidates = buildList {
                System.getenv("ANDROID_HOME")?.let { add(File(it, "platform-tools/adb.exe")) }
                System.getenv("ANDROID_SDK_ROOT")?.let { add(File(it, "platform-tools/adb.exe")) }
                System.getenv("LOCALAPPDATA")?.let { add(File(it, "Android/Sdk/platform-tools/adb.exe")) }
                System.getenv("PATH")?.split(File.pathSeparator)?.forEach { add(File(it, "adb.exe")) }
            }
            return candidates.firstOrNull { it.isFile }
        }
    }
}
