package xaos.desktop.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import xaos.desktop.CustomTheme
import java.io.File

/** Un tema letto da un file .xtheme: il nome e i colori. */
data class XThemeFile(val name: String, val theme: CustomTheme)

/**
 * Il formato .xtheme: un tema di Xaos in un file da passarsi, lo stesso su PC
 * e telefono. È un JSON piccolo e leggibile, con i colori in esadecimale, che
 * si può anche ritoccare a mano:
 *
 * ```
 * {
 *   "xaos": "theme",
 *   "version": 1,
 *   "name": "Vapor Night",
 *   "colors": { "background": "#D39BFF", "gradient": "#38009A", "panels": "#400041",
 *               "text": "#FFFFFF", "accent": "#FFFFFF" },
 *   "dots": true
 * }
 * ```
 *
 * Un colore che manca (o null) resta quello del tema chiaro o scuro.
 */
object XTheme {
    const val EXTENSION = "xtheme"

    private val json = Json { prettyPrint = true }

    fun encode(name: String, theme: CustomTheme): String {
        fun color(v: Long?) = v?.let { JsonPrimitive(hex(it)) } ?: JsonNull
        val obj = buildJsonObject {
            put("xaos", "theme")
            put("version", 1)
            put("name", name)
            put("colors", JsonObject(linkedMapOf(
                "background" to color(theme.background),
                "gradient" to color(theme.background2),
                "panels" to color(theme.panels),
                "text" to color(theme.ink),
                "accent" to color(theme.accent),
            )))
            put("dots", theme.dots)
        }
        return json.encodeToString(JsonObject.serializer(), obj) + "\n"
    }

    /** Il tema nel testo, o null se non è un .xtheme di Xaos. */
    fun decode(text: String): XThemeFile? = runCatching {
        val obj = Json.parseToJsonElement(text.removePrefix("﻿")).jsonObject
        if (obj["xaos"]?.jsonPrimitive?.contentOrNull != "theme") return null
        val version = obj["version"]?.jsonPrimitive?.intOrNull ?: 1
        if (version > 1) return null
        val colors = obj["colors"]?.jsonObject ?: JsonObject(emptyMap())
        fun color(key: String): Long? = colors[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.let(::parseHex)
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()?.take(40)?.ifEmpty { null } ?: return null
        XThemeFile(
            name = name,
            theme = CustomTheme(
                enabled = true,
                background = color("background"),
                background2 = color("gradient"),
                panels = color("panels"),
                ink = color("text"),
                accent = color("accent"),
                dots = obj["dots"]?.jsonPrimitive?.booleanOrNull ?: true,
            ).normalized(),
        )
    }.getOrNull()

    fun read(file: File): XThemeFile? =
        if (!file.isFile || file.length() > MAX_BYTES) null else decode(file.readText(Charsets.UTF_8))

    /** Un nome che va bene come nome di file su Windows. */
    fun fileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Xaos" } + ".$EXTENSION"

    /** "#RRGGBB", o "#AARRGGBB" se il colore è trasparente. */
    private fun hex(argb: Long): String {
        val v = argb.toInt()
        val alpha = (v ushr 24) and 0xFF
        return if (alpha == 0xFF) "#%06X".format(v and 0xFFFFFF) else "#%08X".format(v)
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

    /** Un file di tema è piccolo: uno grande non è un tema. */
    private const val MAX_BYTES = 64 * 1024
}
