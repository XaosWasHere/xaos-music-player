package xaos.desktop

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Il registro dei tempi d'avvio, in `~/.xaos/startup.log`.
 *
 * Ogni avvio riscrive il file con quanto è servito a ogni passo, dall'avvio
 * del processo. Se l'app ci mette troppo ad aprirsi, lì c'è scritto dove.
 */
object StartupLog {
    private val t0 = System.nanoTime()
    private val file = File(Settings.appDir, "startup.log")
    private val lines = mutableListOf<String>()

    init {
        mark("avvio " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    @Synchronized
    fun mark(step: String) {
        val ms = (System.nanoTime() - t0) / 1_000_000
        lines += String.format("%7d ms  %s", ms, step)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(lines.joinToString("\n") + "\n")
        }
    }
}

/** La cartella delle risorse che viaggiano con l'app (VLC, adb, l'APK). */
val appResourcesDir: File? =
    System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }
