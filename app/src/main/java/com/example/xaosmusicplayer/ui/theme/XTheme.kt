package com.example.xaosmusicplayer.ui.theme

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.xaosmusicplayer.i18n.t
import org.json.JSONObject
import java.io.File

/** Un tema letto da un file .xtheme: il nome e i colori. */
data class XThemeFile(val name: String, val theme: CustomTheme)

/**
 * Il formato .xtheme: un tema di Xaos in un file da passarsi, lo stesso su PC
 * e telefono. È un JSON piccolo e leggibile, con i colori in esadecimale:
 *
 * ```
 * { "xaos": "theme", "version": 1, "name": "Vapor Night",
 *   "colors": { "background": "#D39BFF", "gradient": "#38009A", "panels": "#400041",
 *               "text": "#FFFFFF", "accent": "#FFFFFF" },
 *   "dots": true }
 * ```
 *
 * Un colore che manca (o null) resta quello del tema chiaro o scuro.
 */
object XTheme {
    const val EXTENSION = "xtheme"

    /**
     * Il tipo con cui si condivide. Chi riceve il file (WhatsApp, Telegram, la
     * posta) lo riapre con lo stesso tipo, e Android lo dà a Xaos.
     */
    const val MIME = "application/vnd.xaos.theme+json"

    fun encode(name: String, theme: CustomTheme): String {
        fun color(v: Long?): Any = v?.let(::hex) ?: JSONObject.NULL
        val colors = JSONObject()
            .put("background", color(theme.background))
            .put("gradient", color(theme.background2))
            .put("panels", color(theme.panels))
            .put("text", color(theme.ink))
            .put("accent", color(theme.accent))
        return JSONObject()
            .put("xaos", "theme")
            .put("version", 1)
            .put("name", name)
            .put("colors", colors)
            .put("dots", theme.dots)
            .toString(2) + "\n"
    }

    /** Il tema nel testo, o null se non è un .xtheme di Xaos. */
    fun decode(text: String): XThemeFile? = runCatching {
        val obj = JSONObject(text.removePrefix("﻿"))
        if (obj.optString("xaos") != "theme" || obj.optInt("version", 1) > 1) return null
        val name = obj.optString("name").trim().take(40).ifEmpty { return null }
        val colors = obj.optJSONObject("colors") ?: JSONObject()
        fun color(key: String): Long? = if (!colors.has(key) || colors.isNull(key)) null else parseHex(colors.getString(key))
        XThemeFile(
            name,
            CustomTheme(
                enabled = true,
                background = color("background"),
                background2 = color("gradient"),
                panels = color("panels"),
                ink = color("text"),
                accent = color("accent"),
                dots = obj.optBoolean("dots", true),
            ),
        )
    }.getOrNull()

    /** Legge un file ricevuto (da una chat, dai file del telefono). */
    fun read(context: Context, uri: Uri): XThemeFile? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytesCompat(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) null else decode(bytes.toString(Charsets.UTF_8))
        }
    }.getOrNull()

    /**
     * Aggiunge il tema ai preset e lo applica. Con un nome già usato aggiorna
     * quel preset, come "Salva come preset".
     */
    fun importTheme(store: ThemeStore, file: XThemeFile) {
        val presets = store.presets.value
        val same = presets.firstOrNull { it.name.equals(file.name, ignoreCase = true) }
        store.setPresets(
            if (same != null) presets.map { if (it.id == same.id) it.copy(theme = file.theme) else it }
            else presets + ThemePreset("tp_${System.currentTimeMillis()}", file.name, file.theme)
        )
        store.setCustom(file.theme.copy(enabled = true))
    }

    /** Apre la schermata di condivisione di Android con il preset come file .xtheme. */
    fun share(context: Context, preset: ThemePreset) {
        val dir = File(context.cacheDir, "themes").apply { mkdirs() }
        val file = File(dir, fileName(preset.name))
        file.writeText(encode(preset.name, preset.theme.copy(enabled = true)), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, t("Tema di Xaos: %s", preset.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, t("Condividi il tema")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun fileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Xaos" } + ".$EXTENSION"

    /** "#RRGGBB", o "#AARRGGBB" se il colore è trasparente. */
    private fun hex(argb: Long): String {
        val v = argb.toInt()
        return if ((v ushr 24) and 0xFF == 0xFF) "#%06X".format(v and 0xFFFFFF) else "#%08X".format(v)
    }

    private fun parseHex(text: String): Long? {
        val h = text.trim().removePrefix("#")
        val v = h.toLongOrNull(16) ?: return null
        return when (h.length) {
            6 -> (0xFF000000 or v).toInt().toLong()
            8 -> v.toInt().toLong()
            else -> null
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Un file di tema è piccolo: uno grande non è un tema. */
    private const val MAX_BYTES = 64 * 1024
}
