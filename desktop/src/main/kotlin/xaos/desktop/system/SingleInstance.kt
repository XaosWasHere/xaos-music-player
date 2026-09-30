package xaos.desktop.system

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import xaos.desktop.Settings
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Una sola copia di Xaos alla volta.
 *
 * La prima copia crea un mutex di sistema con un nome fisso; una seconda lo
 * trova già esistente e, invece di aprirsi, chiede alla prima di tornare in
 * primo piano e si chiude. Lo stesso nome lo usa l'installer (AppMutex) per
 * capire che Xaos è aperto prima di aggiornarlo.
 *
 * La richiesta passa da una porta locale annotata in `~/.xaos/instance.port`,
 * aperta solo su 127.0.0.1.
 */
object SingleInstance {

    /** Deve restare uguale a AppMutex in installer/xaos.iss. */
    private const val MUTEX_NAME = "XaosMusicPlayer.SingleInstance"
    private const val ERROR_ALREADY_EXISTS = 183
    private const val ASFW_ANY = -1

    @Volatile private var mutex: WinNT.HANDLE? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile var onActivate: (() -> Unit)? = null

    private val portFile get() = File(Settings.appDir, "instance.port")

    /** true se questa è l'unica copia; false se ce n'è già un'altra aperta. */
    fun acquire(): Boolean {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return true
        val handle = runCatching { Kernel32.INSTANCE.CreateMutex(null, false, MUTEX_NAME) }.getOrNull() ?: return true
        if (Kernel32.INSTANCE.GetLastError() == ERROR_ALREADY_EXISTS) {
            Kernel32.INSTANCE.CloseHandle(handle)
            return false
        }
        // Il mutex vive finché vive il processo: basta tenerne il riferimento.
        mutex = handle
        listen()
        return true
    }

    /** Dalla seconda copia: sveglia la prima e le cede il primo piano. */
    fun activateExisting() {
        runCatching { ForegroundApi.INSTANCE.AllowSetForegroundWindow(ASFW_ANY) }
        val port = runCatching { portFile.readText().trim().toInt() }.getOrNull() ?: return
        runCatching {
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                socket.soTimeout = 2_000
                socket.getOutputStream().write("show\n".toByteArray())
                socket.getOutputStream().flush()
            }
        }
    }

    private fun listen() {
        runCatching {
            val socket = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
            server = socket
            portFile.parentFile?.mkdirs()
            portFile.writeText(socket.localPort.toString())
            Thread({
                while (!socket.isClosed) {
                    runCatching {
                        socket.accept().use { client ->
                            client.soTimeout = 2_000
                            val line = client.getInputStream().bufferedReader().readLine()
                            if (line == "show") java.awt.EventQueue.invokeLater { onActivate?.invoke() }
                        }
                    }
                }
            }, "xaos-single-instance").apply { isDaemon = true }.start()
        }
    }

    private interface ForegroundApi : Library {
        fun AllowSetForegroundWindow(processId: Int): Boolean

        companion object {
            val INSTANCE: ForegroundApi = Native.load("user32", ForegroundApi::class.java)
        }
    }
}
