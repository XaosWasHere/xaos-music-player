package xaos.desktop.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import java.io.File

/*
 * Lo stesso tema dell'app Android: scala di grigi e colori di marca presi dal
 * design system di Nothing. Tenerli identici è ciò che fa sembrare le due app
 * la stessa cosa.
 */
private val Nos0 = Color(0xFFFFFFFF)
private val Nos50 = Color(0xFFF2F2F2)
private val Nos100 = Color(0xFFE3E3E3)
private val Nos200 = Color(0xFFC8C8C8)
private val Nos400 = Color(0xFF929292)
private val Nos600 = Color(0xFF606060)
private val Nos800 = Color(0xFF323232)
private val Nos850 = Color(0xFF292929)
private val Nos900 = Color(0xFF1C1C1C)
private val Nos1000 = Color(0xFF000000)

val NothingRed = Color(0xFFD71921)
val NothingYellow = Color(0xFFFFC700)

/**
 * I colori per ruolo. Come sul telefono, l'accento ha due ruoli: [accent] per
 * ciò che si riempie, [accentInk] per testo e icone sul fondo — sul chiaro il
 * giallo come testo non si leggerebbe.
 */
@Immutable
data class XaosPalette(
    val isDark: Boolean,
    val background: Color,
    val sidebar: Color,
    val card: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val line: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    val dot: Color,
    val track: Color,
    /** Il secondo colore dello sfondo, se l'utente ha scelto una sfumatura. */
    val background2: Color? = null,
)

val DarkPalette = XaosPalette(
    isDark = true,
    background = Nos1000,
    sidebar = Color(0xFF0A0A0A),
    card = Nos900.copy(alpha = 0.78f),
    surface = Nos900,
    surfaceHigh = Nos850,
    line = Nos800,
    ink = Nos50,
    inkSecondary = Nos400,
    inkTertiary = Nos600,
    accent = NothingRed,
    onAccent = Nos0,
    accentInk = NothingRed,
    // Puntini da un pixel: presenti ma sotto il testo, non accanto a lui.
    dot = Nos0.copy(alpha = 0.20f),
    track = Nos800,
)

val LightPalette = XaosPalette(
    isDark = false,
    background = Nos50,
    sidebar = Color(0xFFEAEAEA),
    card = Nos0.copy(alpha = 0.72f),
    surface = Nos0,
    surfaceHigh = Nos100,
    line = Color(0xFFDADADA),
    ink = Nos900,
    inkSecondary = Nos600,
    inkTertiary = Nos400,
    accent = NothingYellow,
    onAccent = Nos900,
    accentInk = Nos900,
    dot = Nos400.copy(alpha = 0.45f),
    track = Nos200,
)

val LocalXaosPalette = staticCompositionLocalOf { DarkPalette }

object Xaos {
    val colors: XaosPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalXaosPalette.current
}

/**
 * I caratteri. Su Windows non ci sono quelli di Nothing: il monospace è Cascadia
 * Mono (di serie su Windows 11), poi Consolas; i titoli sono in Segoe UI
 * Variable. La matrice di punti dei titoli grandi non è un font ma [DotText],
 * disegnata a mano, così è identica su qualunque PC.
 */
object XaosFonts {
    val Mono: FontFamily by lazy {
        windowsFont("CascadiaMono.ttf") ?: windowsFont("consola.ttf") ?: FontFamily.Monospace
    }
    val Headline: FontFamily by lazy {
        windowsFont("SegUIVar.ttf") ?: windowsFont("segoeui.ttf") ?: FontFamily.SansSerif
    }

    private fun windowsFont(name: String): FontFamily? {
        val dir = System.getenv("WINDIR") ?: "C:\\Windows"
        val file = File(dir, "Fonts\\$name")
        if (!file.canRead()) return null
        return runCatching {
            FontFamily(
                Font(file, FontWeight.Light),
                Font(file, FontWeight.Normal),
                Font(file, FontWeight.Medium),
                Font(file, FontWeight.SemiBold),
                Font(file, FontWeight.Bold),
            )
        }.getOrNull()
    }
}

private fun buildTypography(): Typography {
    val mono = XaosFonts.Mono
    val headline = XaosFonts.Headline
    return Typography(
        headlineMedium = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
        titleLarge = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
        bodyLarge = TextStyle(fontFamily = mono, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
        bodyMedium = TextStyle(fontFamily = mono, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
        labelLarge = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
        labelMedium = TextStyle(fontFamily = mono, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.sp),
        labelSmall = TextStyle(fontFamily = mono, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.2.sp),
    )
}

private val XaosTypography by lazy { buildTypography() }

/**
 * La tavolozza con i colori scelti dall'utente. Si scelgono sfondo, pannelli,
 * testo e accento; tutto il resto se ne ricava: il testo secondario è il testo
 * sfumato verso lo sfondo, i bordi sono i pannelli verso il testo, e così via.
 */
fun XaosPalette.customized(t: xaos.desktop.CustomTheme): XaosPalette {
    if (!t.enabled) return this
    fun c(v: Long?, fallback: Color) = v?.let { Color(it.toInt()) } ?: fallback
    fun mix(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)
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
        sidebar = mix(bg, text, 0.035f),
        card = panel.copy(alpha = card.alpha),
        surface = panel,
        surfaceHigh = mix(panel, text, 0.08f),
        line = mix(panel, text, 0.14f),
        ink = text,
        inkSecondary = mix(bg, text, 0.62f),
        inkTertiary = mix(bg, text, 0.42f),
        accent = acc,
        onAccent = onAcc,
        accentInk = if (contrast >= 3f) acc else text,
        dot = if (t.dots) text.copy(alpha = if (darkBg) 0.20f else 0.30f) else Color.Transparent,
        track = mix(bg, text, 0.2f),
    )
}

/** Il tema, con la dissolvenza fra chiaro e scuro come sul telefono. */
@Composable
fun XaosTheme(dark: Boolean, custom: xaos.desktop.CustomTheme = xaos.desktop.CustomTheme(), content: @Composable () -> Unit) {
    val palette = (if (dark) DarkPalette else LightPalette).customized(custom).animated()
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.background, onBackground = palette.ink,
            surface = palette.surface, onSurface = palette.ink,
            surfaceVariant = palette.surfaceHigh, onSurfaceVariant = palette.inkSecondary,
            surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceHigh,
            outline = palette.line, outlineVariant = palette.line,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.background, onBackground = palette.ink,
            surface = palette.surface, onSurface = palette.ink,
            surfaceVariant = palette.surfaceHigh, onSurfaceVariant = palette.inkSecondary,
            surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceHigh,
            outline = palette.line, outlineVariant = palette.line,
        )
    }
    CompositionLocalProvider(LocalXaosPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = XaosTypography, content = content)
    }
}

@Composable
private fun XaosPalette.animated(): XaosPalette {
    @Composable
    fun Color.follow(label: String): Color {
        val value by animateColorAsState(this, tween(420), label = label)
        return value
    }
    return XaosPalette(
        isDark = isDark,
        background = background.follow("background"),
        sidebar = sidebar.follow("sidebar"),
        card = card.follow("card"),
        surface = surface.follow("surface"),
        surfaceHigh = surfaceHigh.follow("surfaceHigh"),
        line = line.follow("line"),
        ink = ink.follow("ink"),
        inkSecondary = inkSecondary.follow("inkSecondary"),
        inkTertiary = inkTertiary.follow("inkTertiary"),
        accent = accent.follow("accent"),
        onAccent = onAccent.follow("onAccent"),
        accentInk = accentInk.follow("accentInk"),
        dot = dot.follow("dot"),
        track = track.follow("track"),
        background2 = background2?.follow("background2"),
    )
}

/** Lo sfondo dell'app: il colore, o la sfumatura se l'utente l'ha scelta. */
@Composable
fun androidx.compose.ui.Modifier.appBackground(): androidx.compose.ui.Modifier {
    val c = Xaos.colors
    val second = c.background2
    return if (second == null) this.background(c.background)
    else this.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.background, second)))
}
