package xaos.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import xaos.desktop.library.Library
import xaos.desktop.online.YtDlp
import xaos.desktop.player.Player
import xaos.desktop.sync.PhoneSync
import xaos.desktop.theme.XaosTheme
import xaos.desktop.ui.XaosDesktopApp
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

fun main() = application {
    val settings = remember { Settings(File(Settings.appDir, "settings.json")) }
    // v2: l'indice registra anche quali brani hanno un testo. Cambiando nome si
    // rilegge tutto una volta sola; il vecchio file non serve più.
    val library = remember {
        File(Settings.appDir, "library.json").delete()
        Library(File(Settings.appDir, "library-v2.json"))
    }
    val player = remember { Player().also { it.setVolume(settings.data.value.volume) } }
    val scope = rememberCoroutineScope()
    val phone = remember { PhoneSync(scope).also { it.startWatching() } }
    val ytdlp = remember { YtDlp(scope) }
    val prefs by settings.data.collectAsState()
    // La stessa icona dell'app Android: barra del titolo e barra delle applicazioni.
    val appIcon = remember {
        val bytes = Thread.currentThread().contextClassLoader
            .getResourceAsStream("xaos.png")!!
            .use { it.readAllBytes() }
        BitmapPainter(bytes.decodeToImageBitmap())
    }

    // Le impostazioni si applicano man mano che cambiano, e una volta all'avvio.
    LaunchedEffect(prefs.roots) {
        library.load(prefs.roots.map { File(it) }.filter { it.isDirectory })
    }
    LaunchedEffect(prefs.equalizer) {
        player.applyEqualizer(prefs.equalizer.enabled, prefs.equalizer.preamp, prefs.equalizer.bands)
    }
    LaunchedEffect(prefs.outputDevice) { player.setOutputDevice(prefs.outputDevice) }
    LaunchedEffect(prefs.phoneFolder) { phone.remoteRoot = prefs.phoneFolder }

    val windowState = rememberWindowState(size = DpSize(1320.dp, 860.dp))
    var fullscreen by remember { mutableStateOf(System.getenv("XAOS_START") == "FULLSCREEN") }
    var placementBefore by remember { mutableStateOf(WindowPlacement.Floating) }

    // Il player a tutto schermo porta a tutto schermo anche la finestra, come
    // Spotify; uscendo, la finestra torna com'era (anche se era massimizzata).
    fun setFullscreen(on: Boolean) {
        if (on == fullscreen) return
        if (on) {
            placementBefore = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        } else {
            windowState.placement = placementBefore
        }
        fullscreen = on
    }

    Window(
        onCloseRequest = {
            player.release()
            exitApplication()
        },
        title = "Xaos",
        icon = appIcon,
        state = windowState,
        onPreviewKeyEvent = { event ->
            // I tasti valgono solo a schermo intero: altrove lo spazio serve a
            // scrivere nella ricerca.
            if (!fullscreen || event.type != KeyEventType.KeyDown) return@Window false
            when (event.key) {
                Key.Escape -> { setFullscreen(false); true }
                Key.Spacebar -> { player.togglePlayPause(); true }
                Key.DirectionRight -> { player.seekBy(10_000); true }
                Key.DirectionLeft -> { player.seekBy(-10_000); true }
                else -> false
            }
        },
    ) {
        window.minimumSize = java.awt.Dimension(1000, 640)
        XaosTheme(dark = prefs.dark) {
            XaosDesktopApp(
                settings = settings,
                library = library,
                player = player,
                phone = phone,
                ytdlp = ytdlp,
                fullscreen = fullscreen,
                onFullscreenChange = ::setFullscreen,
                onAddFolder = {
                    scope.launch {
                        pickFolder(prefs.roots.firstOrNull())?.let { dir ->
                            settings.setRoots(prefs.roots + dir.path)
                        }
                    }
                },
                onRescan = {
                    scope.launch { library.load(prefs.roots.map { File(it) }.filter { it.isDirectory }) }
                },
                onPickDownloadFolder = {
                    scope.launch {
                        pickFolder(prefs.effectiveDownloadFolder)?.let { dir ->
                            settings.update { it.copy(downloadFolder = dir.path) }
                        }
                    }
                },
                onDownload = { track ->
                    val folder = prefs.effectiveDownloadFolder?.let(::File) ?: return@XaosDesktopApp
                    // Se i download finiscono fuori dalla libreria, la cartella
                    // entra fra quelle scansionate: un brano scaricato che non
                    // compare da nessuna parte sarebbe un brano perso.
                    val inLibrary = prefs.roots.any { folder.path.startsWith(it) }
                    if (!inLibrary) settings.setRoots(prefs.roots + folder.path)
                    ytdlp.download(track, folder) {
                        scope.launch { library.load(settings.data.value.roots.map(::File).filter { it.isDirectory }) }
                    }
                },
            )
        }
    }
}

/** Il selettore di cartelle di Windows, con l'aspetto di sistema. */
private fun pickFolder(start: String?): File? {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val chooser = JFileChooser(start).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Aggiungi una cartella di musica"
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
