package com.example.xaosmusicplayer.ui.theme

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La scelta fra tema chiaro e scuro.
 *
 * Sta in SharedPreferences e non nel DataStore del resto delle preferenze perché
 * va letta prima del primo frame: con una lettura asincrona l'app partirebbe
 * sempre scura e poi cambierebbe, un lampo a ogni avvio per chi usa il chiaro.
 */
class ThemeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isDark = MutableStateFlow(prefs.getBoolean(KEY_DARK, true))
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    fun toggle() = setDark(!_isDark.value)

    fun setDark(dark: Boolean) {
        _isDark.value = dark
        prefs.edit().putBoolean(KEY_DARK, dark).apply()
    }

    private companion object {
        const val PREFS_NAME = "xaos_theme"
        const val KEY_DARK = "dark"
    }
}

/**
 * Il tema dell'app. Al cambio fra chiaro e scuro i colori non scattano: ogni
 * ruolo scorre verso il nuovo valore, così tutta l'interfaccia si "ricolora"
 * insieme invece di lampeggiare.
 */
@Composable
fun XaosMusicPlayerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val target = if (darkTheme) DarkPalette else LightPalette
    val palette = target.animated()

    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accent,
            onPrimaryContainer = palette.onAccent,
            secondary = palette.ink,
            onSecondary = palette.background,
            tertiary = palette.accent,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.inkSecondary,
            surfaceContainer = palette.surface,
            surfaceContainerLow = palette.surface,
            surfaceContainerHigh = palette.surfaceHigh,
            surfaceContainerHighest = palette.surfaceHigh,
            outline = palette.line,
            outlineVariant = palette.line,
            error = palette.accent,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accent,
            onPrimaryContainer = palette.onAccent,
            secondary = palette.ink,
            onSecondary = palette.background,
            tertiary = palette.accent,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.inkSecondary,
            surfaceContainer = palette.surface,
            surfaceContainerLow = palette.surface,
            surfaceContainerHigh = palette.surfaceHigh,
            surfaceContainerHighest = palette.surfaceHigh,
            outline = palette.line,
            outlineVariant = palette.line,
            error = palette.ink,
        )
    }

    CompositionLocalProvider(LocalXaosPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content,
        )
    }
}

@Composable
private fun XaosPalette.animated(): XaosPalette {
    @Composable
    fun Color.follow(label: String): Color {
        val value by animateColorAsState(this, tween(THEME_FADE_MS), label = label)
        return value
    }
    return XaosPalette(
        isDark = isDark,
        background = background.follow("background"),
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
    )
}

private const val THEME_FADE_MS = 420
