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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import xaos.desktop.library.Library
import xaos.desktop.library.ScanState
import xaos.desktop.library.UserData
import xaos.desktop.sync.DataSync
import xaos.desktop.system.MediaBridge
import xaos.desktop.system.TaskbarButtons
import xaos.desktop.ui.MiniPlayerCard
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.padding
import xaos.desktop.online.YtDlp
import xaos.desktop.player.Player
import xaos.desktop.sync.PhoneSync
import xaos.desktop.theme.XaosTheme
import xaos.desktop.ui.XaosDesktopApp
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

fun main() = application {
    remember { StartupLog.mark("main") }
    val settings = remember { Settings(File(Settings.appDir, "settings.json")) }
    // v2: l'indice registra anche quali brani hanno un testo. Cambiando nome si
    // rilegge tutto una volta sola; il vecchio file non serve più.
    val library = remember {
        File(Settings.appDir, "library.json").delete()
        Library(File(Settings.appDir, "library-v2.json"))
    }
    val player = remember { Player().also { it.setVolume(settings.data.value.volume) } }
    val scope = rememberCoroutineScope()
    val phone = remember { PhoneSync(scope).also { if (!Settings.isTestInstance) it.startWatching() } }
    val ytdlp = remember { YtDlp(scope) }
    // Preferiti, playlist e ascolti del PC; gli ascolti li registra il player.
    val userData = remember {
        UserData(File(Settings.appDir, "userdata.json")).also { data ->
            player.onListened = { track, at -> data.recordPlay(track, at) }
        }
    }
    val dataSync = remember { DataSync(scope, phone, userData, File(Settings.appDir, "sync")) }
    val prefs by settings.data.collectAsState()
    // La stessa icona dell'app Android: barra del titolo e barra delle applicazioni.
    val appIcon = remember {
        val bytes = Thread.currentThread().contextClassLoader
            .getResourceAsStream("xaos.png")!!
            .use { it.readAllBytes() }
        BitmapPainter(bytes.decodeToImageBitmap())
    }

    // Il motore audio parte dopo la finestra, su un thread suo: caricare VLC
    // può richiedere qualche secondo e l'app non deve aspettarlo per aprirsi.
    LaunchedEffect(Unit) {
        StartupLog.mark("finestra composta")
        player.start(appResourcesDir?.let { File(it, "vlc") })
        player.engine.collect { if (it != xaos.desktop.player.Player.Engine.Starting) StartupLog.mark("motore audio: $it (${player.vlcPath})") }
    }

    // Le impostazioni si applicano man mano che cambiano, e una volta all'avvio.
    LaunchedEffect(prefs.roots) {
        library.load(prefs.roots.map { File(it) }.filter { it.isDirectory })
        library.snapshot.value.let { snap ->
            StartupLog.mark("libreria letta: ${snap.tracks.size} brani, ${snap.albums.size} album, ${snap.duplicates.sumOf { it.copies.size }} doppioni nascosti")
        }
    }
    LaunchedEffect(prefs.equalizer) {
        player.applyEqualizer(prefs.equalizer.enabled, prefs.equalizer.preamp, prefs.equalizer.bands)
    }
    LaunchedEffect(prefs.outputDevice) { player.setOutputDevice(prefs.outputDevice) }
    LaunchedEffect(prefs.phoneFolder) { phone.remoteRoot = prefs.phoneFolder }
    LaunchedEffect(prefs.effectiveImportFolder, prefs.roots) {
        phone.importFolder = prefs.effectiveImportFolder?.let(::File)
        phone.libraryRoots = prefs.roots.map(::File)
    }
    DataEffects(settings, library, phone, userData, dataSync)

    val windowState = rememberWindowState(size = DpSize(1320.dp, 860.dp))
    var fullscreen by remember { mutableStateOf(System.getenv("XAOS_START") == "FULLSCREEN") }
    // XAOS_START=MINI apre direttamente il miniplayer: serve solo per provarlo.
    var mini by remember { mutableStateOf(System.getenv("XAOS_START") == "MINI") }
    var mediaBridge by remember { mutableStateOf<MediaBridge?>(null) }
    val mainWindow = remember { arrayOfNulls<java.awt.Window>(1) }
    // Riaprire Xaos dalla barra delle applicazioni chiude il miniplayer.
    LaunchedEffect(windowState.isMinimized) { if (!windowState.isMinimized) mini = false }

    // Il player a tutto schermo è una finestra sua (la apre XaosDesktopApp);
    // chiudendola si torna davanti alla finestra principale.
    fun setFullscreen(on: Boolean) {
        if (on == fullscreen) return
        fullscreen = on
        if (!on) mainWindow[0]?.toFront()
    }

    Window(
        onCloseRequest = {
            mediaBridge?.close()
            player.release()
            exitApplication()
        },
        title = "Xaos",
        icon = appIcon,
        state = windowState,
    ) {
        window.minimumSize = java.awt.Dimension(1000, 640)
        mainWindow[0] = window
        LaunchedEffect(Unit) { if (mini) windowState.isMinimized = true }
        // I controlli nella barra delle applicazioni e nel riquadro multimediale di Windows.
        LaunchedEffect(Unit) {
            TaskbarButtons.install(window, player, scope)
            if (!Settings.isTestInstance) mediaBridge = MediaBridge.start(appResourcesDir?.let { File(it, "media/XaosMedia.exe") }, player, scope)
        }
        // L'ingrandimento vale per tutto: testo, spazi, icone. Si ottiene
        // dichiarando allo strato di Compose uno schermo un po' più denso.
        val base = androidx.compose.ui.platform.LocalDensity.current
        val scaled = androidx.compose.ui.unit.Density(base.density * prefs.uiScale, base.fontScale)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaled) {
        XaosTheme(dark = prefs.dark) {
            XaosDesktopApp(
                settings = settings,
                library = library,
                player = player,
                phone = phone,
                ytdlp = ytdlp,
                userData = userData,
                dataSync = dataSync,
                fullscreen = fullscreen,
                onFullscreenChange = ::setFullscreen,
                screenBounds = { mainWindow[0]?.graphicsConfiguration?.bounds },
                appIcon = appIcon,
                onOpenMini = {
                    mini = true
                    windowState.isMinimized = true
                },
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
                onPickImportFolder = {
                    scope.launch {
                        pickFolder(prefs.effectiveImportFolder)?.let { dir ->
                            settings.update { it.copy(importFolder = dir.path) }
                        }
                    }
                },
                onImport = { files ->
                    val folder = prefs.effectiveImportFolder?.let(::File) ?: return@XaosDesktopApp
                    // Come per i download: la cartella d'arrivo entra in libreria.
                    if (prefs.roots.none { folder.path.startsWith(it) }) settings.setRoots(prefs.roots + folder.path)
                    phone.importFiles(files, folder) {
                        scope.launch {
                            library.load(settings.data.value.roots.map(::File).filter { it.isDirectory })
                            phone.planImport(library.snapshot.value.tracks)
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

    if (mini) {
        MiniPlayerWindow(
            player = player,
            settings = settings,
            icon = appIcon,
            onExpand = {
                mini = false
                windowState.isMinimized = false
                mainWindow[0]?.toFront()
            },
            onClose = { mini = false },
        )
    }
}

/**
 * Il miniplayer: una finestra piccola senza bordi, sempre in primo piano, con
 * la card del brano. Compare nell'angolo in basso a destra, sopra la barra
 * delle applicazioni, e si sposta trascinandola.
 */
@androidx.compose.runtime.Composable
private fun MiniPlayerWindow(
    player: xaos.desktop.player.Player,
    settings: Settings,
    icon: BitmapPainter,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    val prefs by settings.data.collectAsState()
    val width = 232.dp * prefs.uiScale
    // Card quadrata più titolo e comandi, più il margine per l'ombra.
    val height = 346.dp * prefs.uiScale
    val state = rememberWindowState(
        size = DpSize(width, height),
        position = run {
            val area = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            androidx.compose.ui.window.WindowPosition(
                (area.x + area.width).dp - width - 20.dp,
                (area.y + area.height).dp - height - 20.dp,
            )
        },
    )
    Window(
        onCloseRequest = onClose,
        state = state,
        title = "Xaos",
        icon = icon,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        val base = androidx.compose.ui.platform.LocalDensity.current
        val scaled = androidx.compose.ui.unit.Density(base.density * prefs.uiScale, base.fontScale)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaled) {
            XaosTheme(dark = prefs.dark) {
                // Il margine trasparente lascia spazio all'ombra della card.
                WindowDraggableArea {
                    MiniPlayerCard(
                        player = player,
                        onExpand = onExpand,
                        onClose = onClose,
                        modifier = androidx.compose.ui.Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Gli effetti che seguono libreria e telefono. Stanno in una funzione a parte
 * perché cambiano spesso: così a ricomporsi è solo questa, non tutta l'app.
 */
@androidx.compose.runtime.Composable
private fun DataEffects(settings: Settings, library: Library, phone: PhoneSync, userData: UserData, dataSync: DataSync) {
    val snapshot by library.snapshot.collectAsState()
    val scanState by library.scan.collectAsState()
    val phoneState by phone.state.collectAsState()

    // Un file spostato o un doppione eliminato: preferiti e playlist lo ritrovano.
    LaunchedEffect(snapshot) { if (scanState == ScanState.Idle) userData.relink(snapshot) }

    // Preferiti, playlist e ascolti si scambiano da soli a ogni collegamento,
    // appena la libreria è letta (serve a riconoscere i brani), e di nuovo
    // dopo un aggiornamento dell'app sul telefono.
    val connected = phoneState as? xaos.desktop.sync.PhoneState.Connected
    val libraryReady = scanState == ScanState.Idle && snapshot.tracks.isNotEmpty()
    LaunchedEffect(connected?.serial, connected?.appVersionCode, connected?.appInstalled, libraryReady) {
        if (connected == null) dataSync.reset()
        else if (libraryReady) dataSync.run(library.snapshot.value, settings.data.value.roots.map(::File).filter { it.isDirectory })
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
