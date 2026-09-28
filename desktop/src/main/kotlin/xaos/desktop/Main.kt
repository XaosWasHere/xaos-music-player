package xaos.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import xaos.desktop.library.Library
import xaos.desktop.player.Player
import xaos.desktop.sync.PhoneSync
import xaos.desktop.theme.XaosTheme
import xaos.desktop.ui.XaosDesktopApp
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

fun main() = application {
    val settings = remember { Settings(File(Settings.appDir, "settings.json")) }
    val library = remember { Library(File(Settings.appDir, "library.json")) }
    val player = remember { Player().also { it.setVolume(settings.data.value.volume) } }
    val scope = rememberCoroutineScope()
    val phone = remember { PhoneSync(scope).also { it.startWatching() } }
    val prefs by settings.data.collectAsState()
    // La stessa icona dell'app Android: barra del titolo e barra delle applicazioni.
    val appIcon = remember {
        val bytes = Thread.currentThread().contextClassLoader
            .getResourceAsStream("xaos.png")!!
            .use { it.readAllBytes() }
        BitmapPainter(bytes.decodeToImageBitmap())
    }

    // La libreria si ricarica a ogni cambio di cartella.
    LaunchedEffect(prefs.libraryRoot) {
        prefs.libraryRoot?.let { File(it) }?.takeIf { it.isDirectory }?.let { library.load(it) }
    }

    Window(
        onCloseRequest = {
            player.release()
            exitApplication()
        },
        title = "Xaos",
        icon = appIcon,
        state = rememberWindowState(size = DpSize(1320.dp, 860.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(1000, 640)
        XaosTheme(dark = prefs.dark) {
            XaosDesktopApp(
                settings = settings,
                library = library,
                player = player,
                phone = phone,
                onPickLibrary = {
                    scope.launch {
                        pickFolder(prefs.libraryRoot)?.let { dir ->
                            settings.update { it.copy(libraryRoot = dir.path) }
                        }
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
        dialogTitle = "Cartella della musica"
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
