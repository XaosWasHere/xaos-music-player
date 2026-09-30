package xaos.desktop.system

import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.vector.PathParser
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import xaos.desktop.Settings
import xaos.desktop.library.ArtworkLoader
import xaos.desktop.library.Track
import xaos.desktop.player.Player
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.Executors

/**
 * I controlli di Windows per Xaos, fuori dalla sua finestra:
 *
 * - [TaskbarButtons]: precedente, play/pausa e successivo sotto l'anteprima
 *   della finestra, passando col mouse sull'icona nella barra delle applicazioni;
 * - [MediaBridge]: il riquadro multimediale di Windows (volume, Impostazioni
 *   rapide, schermata di blocco) con titolo e copertina, e i tasti
 *   multimediali della tastiera.
 */
object SystemControls {
    private val actions = Executors.newSingleThreadExecutor { Thread(it, "xaos-system-controls").apply { isDaemon = true } }

    /** I comandi arrivano da thread di Windows: li si esegue in fila, fuori da lì. */
    fun run(block: () -> Unit) = actions.execute { runCatching(block) }
}

// ---------------------------------------------------------------- miniature

/**
 * I pulsanti sotto l'anteprima nella barra delle applicazioni (ITaskbarList3).
 *
 * Windows manda i clic alla finestra come WM_COMMAND, quindi la si
 * "sottoclassa": un nostro gestore di messaggi vede quelli che ci interessano
 * e passa tutti gli altri a quello di AWT. Tutte le chiamate COM avvengono lì
 * dentro, sul thread della finestra, come vuole Windows.
 */
class TaskbarButtons private constructor(private val window: java.awt.Window, private val player: Player) {

    private val hwnd = WinDef.HWND(Native.getWindowPointer(window))
    private val user32 = User32.INSTANCE
    private val buttonCreated = user32.RegisterWindowMessage("TaskbarButtonCreated")
    private var previousProc: Pointer? = null
    private var taskbar: TaskbarList3? = null
    private var added = false
    private val icons = HashMap<String, WinDef.HICON?>()

    // Il gestore deve restare raggiungibile: se il GC lo raccogliesse,
    // Windows chiamerebbe memoria liberata.
    private val proc = WinUser.WindowProc { h, msg, wParam, lParam ->
        try {
            when (msg) {
                buttonCreated -> { added = false; addButtons() }
                WM_APP_ADD -> addButtons()
                WM_APP_UPDATE -> updatePlayButton()
                WM_COMMAND -> if ((wParam.toLong() shr 16) and 0xFFFF == THBN_CLICKED.toLong()) {
                    when ((wParam.toLong() and 0xFFFF).toInt()) {
                        ID_PREVIOUS -> SystemControls.run { player.previous() }
                        ID_PLAY -> SystemControls.run { player.togglePlayPause() }
                        ID_NEXT -> SystemControls.run { player.next() }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        user32.CallWindowProc(previousProc, h, msg, wParam, lParam)
    }

    private fun install() {
        previousProc = user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, CallbackReference.getFunctionPointer(proc))
        user32.PostMessage(hwnd, WM_APP_ADD, WinDef.WPARAM(0), WinDef.LPARAM(0))
    }

    /** Il pulsante play/pausa cambia icona: lo si chiede al thread della finestra. */
    fun refresh() {
        user32.PostMessage(hwnd, WM_APP_UPDATE, WinDef.WPARAM(0), WinDef.LPARAM(0))
    }

    private fun list(): TaskbarList3? {
        taskbar?.let { return it }
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        val ref = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(CLSID_TASKBAR_LIST, null, WTypes.CLSCTX_INPROC_SERVER, IID_TASKBAR_LIST3, ref)
        if (hr.toInt() != 0 || ref.value == null) return null
        return TaskbarList3(ref.value).also { it.hrInit(); taskbar = it }
    }

    private fun addButtons() {
        if (added) return
        val tb = list() ?: return
        val buttons = Structure.newInstance(ThumbButton::class.java).toArray(3).map { it as ThumbButton }
        fill(buttons[0], ID_PREVIOUS, "previous", "Precedente")
        fill(buttons[1], ID_PLAY, playIconName(), playTip())
        fill(buttons[2], ID_NEXT, "next", "Successivo")
        buttons.forEach { it.write() }
        val hr = tb.addButtons(hwnd, buttons.size, buttons[0].pointer)
        added = hr == 0
        xaos.desktop.StartupLog.mark("barra delle applicazioni: pulsanti " + if (added) "aggiunti" else "non aggiunti (0x${Integer.toHexString(hr)})")
    }

    private fun updatePlayButton() {
        if (!added) return
        val tb = list() ?: return
        val button = Structure.newInstance(ThumbButton::class.java)
        fill(button, ID_PLAY, playIconName(), playTip())
        button.write()
        tb.updateButtons(hwnd, 1, button.pointer)
    }

    private fun playIconName() = if (player.isPlaying.value) "pause" else "play"
    private fun playTip() = if (player.isPlaying.value) "Pausa" else "Riproduci"

    private fun fill(b: ThumbButton, id: Int, icon: String, tip: String) {
        b.dwMask = THB_ICON or THB_TOOLTIP or THB_FLAGS
        b.iId = id
        b.hIcon = icons.getOrPut(icon) { loadIcon(icon) }
        tip.toCharArray().copyInto(b.szTip, endIndex = minOf(tip.length, 259))
        b.dwFlags = 0
    }

    /** Il glifo disegnato con Skia in un .ico temporaneo, poi caricato da Windows. */
    private fun loadIcon(name: String): WinDef.HICON? = runCatching {
        val size = user32.GetSystemMetrics(SM_CXSMICON).coerceAtLeast(16)
        val px = 32
        val surface = Surface.makeRasterN32Premul(px, px)
        surface.canvas.scale(px / 24f, px / 24f)
        val path = PathParser().parsePathString(GLYPHS.getValue(name)).toPath().asSkiaPath()
        surface.canvas.drawPath(path, Paint().apply { color = -0x1; isAntiAlias = true })
        val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        val file = File(File(Settings.appDir, "cache").apply { mkdirs() }, "taskbar-$name.ico")
        file.writeBytes(icoOf(png, px))
        val handle = user32.LoadImage(null, file.path, WinUser.IMAGE_ICON, size, size, WinUser.LR_LOADFROMFILE)
        handle?.let { WinDef.HICON(it.pointer) }
    }.getOrNull()

    private fun icoOf(png: ByteArray, px: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun u32(v: Int) { u16(v and 0xFFFF); u16((v shr 16) and 0xFFFF) }
        u16(0); u16(1); u16(1)
        out.write(px); out.write(px); out.write(0); out.write(0)
        u16(1); u16(32); u32(png.size); u32(22)
        out.write(png)
        return out.toByteArray()
    }

    @Structure.FieldOrder("dwMask", "iId", "iBitmap", "hIcon", "szTip", "dwFlags")
    class ThumbButton : Structure() {
        @JvmField var dwMask: Int = 0
        @JvmField var iId: Int = 0
        @JvmField var iBitmap: Int = 0
        @JvmField var hIcon: WinDef.HICON? = null
        @JvmField var szTip: CharArray = CharArray(260)
        @JvmField var dwFlags: Int = 0
    }

    /** ITaskbarList3, chiamata per posizione nella tabella dei metodi. */
    private class TaskbarList3(p: Pointer) : Unknown(p) {
        fun hrInit(): Int = _invokeNativeInt(3, arrayOf(pointer))
        fun addButtons(hwnd: WinDef.HWND, count: Int, buttons: Pointer): Int =
            _invokeNativeInt(15, arrayOf(pointer, hwnd, count, buttons))
        fun updateButtons(hwnd: WinDef.HWND, count: Int, buttons: Pointer): Int =
            _invokeNativeInt(16, arrayOf(pointer, hwnd, count, buttons))
    }

    companion object {
        private val CLSID_TASKBAR_LIST = Guid.CLSID("{56FDF344-FD6D-11d0-958A-006097C9A090}")
        private val IID_TASKBAR_LIST3 = Guid.GUID.fromString("{EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}")
        private const val WM_COMMAND = 0x0111
        private const val WM_APP_ADD = 0x8000 + 0x51
        private const val WM_APP_UPDATE = 0x8000 + 0x52
        private const val THBN_CLICKED = 0x1800
        private const val THB_ICON = 0x2
        private const val THB_TOOLTIP = 0x4
        private const val THB_FLAGS = 0x8
        private const val SM_CXSMICON = 49
        private const val ID_PREVIOUS = 1
        private const val ID_PLAY = 2
        private const val ID_NEXT = 3

        private val GLYPHS = mapOf(
            "play" to "M8 5v14l11-7z",
            "pause" to "M6 19h4V5H6v14zm8-14v14h4V5h-4z",
            "next" to "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z",
            "previous" to "M6 6h2v12H6zm3.5 6l8.5 6V6z",
        )

        /** Da chiamare con la finestra già visibile. Fuori da Windows non fa niente. */
        fun install(window: java.awt.Window, player: Player, scope: CoroutineScope): TaskbarButtons? {
            if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return null
            return runCatching { TaskbarButtons(window, player).also { it.install() } }.getOrNull()?.also { tb ->
                scope.launch { player.isPlaying.collect { tb.refresh() } }
            }
        }
    }
}

// ---------------------------------------------------------------- SMTC

/**
 * Il collegamento con XaosMedia.exe, il programmino che parla con i controlli
 * multimediali di Windows. Gli si dice cosa suona e se è in pausa; lui
 * risponde con i tasti premuti nel riquadro di Windows o sulla tastiera.
 */
class MediaBridge private constructor(private val process: Process, private val player: Player) {

    private val writer: BufferedWriter = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private var cover: File? = null

    private fun send(vararg fields: Any?) {
        runCatching {
            synchronized(writer) {
                writer.write(fields.joinToString("\t") { it?.toString().orEmpty().replace('\t', ' ').replace('\n', ' ') })
                writer.newLine()
                writer.flush()
            }
        }
    }

    private fun listen() {
        Thread({
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    val parts = line.split(' ')
                    when (parts[0]) {
                        "play" -> SystemControls.run { if (!player.isPlaying.value) player.togglePlayPause() }
                        "pause", "stop" -> SystemControls.run { if (player.isPlaying.value) player.togglePlayPause() }
                        "next" -> SystemControls.run { player.next() }
                        "previous" -> SystemControls.run { player.previous() }
                        "seek" -> parts.getOrNull(1)?.toLongOrNull()?.let { ms -> SystemControls.run { player.seekToMs(ms) } }
                    }
                }
            }
        }, "xaos-media-bridge").apply { isDaemon = true }.start()
    }

    private fun follow(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            combine(player.current, player.isPlaying) { t, p -> t to p }.distinctUntilChanged().collect { (track, playing) ->
                if (track == null) { send("clear"); return@collect }
                if (track.path != lastPath) {
                    lastPath = track.path
                    send("meta", track.title, track.artist, track.album, coverOf(track)?.path, track.durationMs)
                }
                send("state", if (playing) "playing" else "paused", player.positionMs.value)
            }
        }
        // La posizione ogni tanto, e subito dopo un salto: la barra di Windows resta allineata.
        scope.launch(Dispatchers.IO) {
            var last = 0L
            while (isActive) {
                delay(1_000)
                val now = player.positionMs.value
                val jumped = kotlin.math.abs(now - last - 1_000) > 2_500
                if (player.current.value != null && (jumped || (now / 1_000) % 10 == 0L)) {
                    send("state", if (player.isPlaying.value) "playing" else "paused", now)
                }
                last = now
            }
        }
    }

    @Volatile private var lastPath: String? = null

    /** La copertina in un file, perché Windows la vuole da lì. */
    private fun coverOf(track: Track): File? = runCatching {
        val bytes = ArtworkLoader.rawBytes(track) ?: return null
        val dir = File(Settings.appDir, "cache").apply { mkdirs() }
        val file = File(dir, "cover-${track.path.hashCode().toUInt()}.jpg")
        file.writeBytes(bytes)
        cover?.takeIf { it != file }?.delete()
        cover = file
        file
    }.getOrNull()

    fun close() {
        runCatching { send("clear") }
        process.destroy()
    }

    companion object {
        fun start(exe: File?, player: Player, scope: CoroutineScope): MediaBridge? {
            if (exe == null || !exe.isFile) return null
            return runCatching {
                val process = ProcessBuilder(exe.path).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                MediaBridge(process, player).also {
                    it.listen()
                    it.follow(scope)
                    xaos.desktop.StartupLog.mark("controlli multimediali di Windows: avviati")
                }
            }.getOrNull()
        }
    }
}
