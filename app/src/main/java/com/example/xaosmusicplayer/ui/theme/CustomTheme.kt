package com.example.xaosmusicplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

/**
 * I colori scelti dall'utente, sopra il tema chiaro o scuro. Ogni colore
 * lasciato vuoto resta quello del tema; [background2], se c'è, fa dello sfondo
 * una sfumatura.
 *
 * È lo stesso tema di Xaos desktop, campo per campo: si sincronizza col PC e i
 * colori sono interi ARGB con segno, come li salva il PC.
 */
data class CustomTheme(
    val enabled: Boolean = false,
    val background: Long? = null,
    val background2: Long? = null,
    val panels: Long? = null,
    val ink: Long? = null,
    val accent: Long? = null,
    val dots: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("background", background ?: JSONObject.NULL)
        put("background2", background2 ?: JSONObject.NULL)
        put("panels", panels ?: JSONObject.NULL)
        put("ink", ink ?: JSONObject.NULL)
        put("accent", accent ?: JSONObject.NULL)
        put("dots", dots)
    }

    companion object {
        /** Un colore come intero ARGB con segno: 0xFF000000 e -16777216 sono lo stesso nero. */
        fun argb(color: Color): Long = color.toArgb().toLong()

        fun fromJson(obj: JSONObject?): CustomTheme {
            if (obj == null) return CustomTheme()
            fun color(key: String): Long? =
                if (!obj.has(key) || obj.isNull(key)) null else obj.optLong(key).toInt().toLong()
            return CustomTheme(
                enabled = obj.optBoolean("enabled", false),
                background = color("background"),
                background2 = color("background2"),
                panels = color("panels"),
                ink = color("ink"),
                accent = color("accent"),
                dots = obj.optBoolean("dots", true),
            )
        }
    }
}

/**
 * La palette con i colori dell'utente. Dai quattro colori scelti si ricavano
 * tutti gli altri ruoli, come su desktop: le tonalità tenui del testo sono il
 * testo sfumato verso lo sfondo, i bordi sono i pannelli verso il testo, e così via.
 */
fun XaosPalette.customized(t: CustomTheme): XaosPalette {
    if (!t.enabled) return this
    fun c(v: Long?, fallback: Color) = v?.let { Color(it.toInt()) } ?: fallback
    val bg = c(t.background, background)
    val panel = c(t.panels, surface)
    val text = c(t.ink, ink)
    val acc = c(t.accent, accent)
    val darkBg = bg.luminance() < 0.4f
    // Il testo sull'accento: nero o bianco, quello che si legge meglio.
    val onAcc = if (acc.luminance() > 0.45f) Color(0xFF111111) else Color.White
    // L'accento come testo solo se sul fondo si legge; altrimenti il testo.
    val contrast = (maxOf(acc.luminance(), bg.luminance()) + 0.05f) / (minOf(acc.luminance(), bg.luminance()) + 0.05f)
    return copy(
        isDark = darkBg,
        background = bg,
        background2 = t.background2?.let { Color(it.toInt()) },
        card = panel.copy(alpha = card.alpha),
        surface = panel,
        surfaceHigh = lerp(panel, text, 0.08f),
        line = lerp(panel, text, 0.14f),
        ink = text,
        inkSecondary = lerp(bg, text, 0.62f),
        inkTertiary = lerp(bg, text, 0.42f),
        accent = acc,
        onAccent = onAcc,
        accentInk = if (contrast >= 3f) acc else text,
        dot = if (t.dots) text.copy(alpha = if (darkBg) 0.16f else 0.30f) else Color.Transparent,
        track = lerp(bg, text, 0.2f),
    )
}
