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
import org.json.JSONArray
import org.json.JSONObject

/**
 * La scelta fra tema chiaro e scuro, e il tema personalizzato.
 *
 * Sta in SharedPreferences e non nel DataStore del resto delle preferenze perché
 * va letta prima del primo frame: con una lettura asincrona l'app partirebbe
 * sempre scura e poi cambierebbe, un lampo a ogni avvio per chi usa il chiaro.
 *
 * È una sola istanza per processo: la sincronizzazione col PC scrive il tema
 * da un ricevitore, e l'app aperta deve vederlo cambiare subito.
 */
class ThemeStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isDark = MutableStateFlow(prefs.getBoolean(KEY_DARK, true))
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    fun toggle() = setDark(!_isDark.value)

    fun setDark(dark: Boolean) {
        _isDark.value = dark
        prefs.edit().putBoolean(KEY_DARK, dark).apply()
    }

    private val _custom = MutableStateFlow(
        runCatching { CustomTheme.fromJson(prefs.getString(KEY_CUSTOM, null)?.let(::JSONObject)) }.getOrDefault(CustomTheme())
    )
    val custom: StateFlow<CustomTheme> = _custom.asStateFlow()

    fun setCustom(theme: CustomTheme) {
        _custom.value = theme
        prefs.edit().putString(KEY_CUSTOM, theme.toJson().toString()).apply()
    }

    /** Il tema personalizzato come testo: entra nell'impronta della sincronizzazione. */
    fun customJson(): String = _custom.value.toJson().toString()

    private val _presets = MutableStateFlow(
        runCatching { ThemePreset.listFromJson(prefs.getString(KEY_PRESETS, null)?.let(::JSONArray)) }.getOrDefault(emptyList())
    )
    /** I temi salvati con un nome. */
    val presets: StateFlow<List<ThemePreset>> = _presets.asStateFlow()

    fun setPresets(list: List<ThemePreset>) {
        _presets.value = list
        prefs.edit().putString(KEY_PRESETS, ThemePreset.listToJson(list).toString()).apply()
    }

    /** I preset come testo, per l'impronta della sincronizzazione. */
    fun presetsJson(): String = ThemePreset.listToJson(_presets.value).toString()

    /**
     * Salva il tema in uso come preset [name]. Con un nome già usato aggiorna
     * quel preset invece di crearne un altro.
     */
    fun saveAsPreset(name: String) {
        val theme = _custom.value.copy(enabled = true)
        val same = _presets.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
        setPresets(
            if (same != null) _presets.value.map { if (it.id == same.id) it.copy(theme = theme) else it }
            else _presets.value + ThemePreset("tp_${System.currentTimeMillis()}", name, theme)
        )
    }

    companion object {
        private const val PREFS_NAME = "xaos_theme"
        private const val KEY_DARK = "dark"
        private const val KEY_CUSTOM = "custom"
        private const val KEY_PRESETS = "presets"

        @Volatile private var instance: ThemeStore? = null

        fun get(context: Context): ThemeStore =
            instance ?: synchronized(this) { instance ?: ThemeStore(context.applicationContext).also { instance = it } }
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
    custom: CustomTheme = CustomTheme(),
    content: @Composable () -> Unit,
) {
    val target = (if (darkTheme) DarkPalette else LightPalette).customized(custom)
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
        // Sempre animato, anche quando non c'è: una chiamata che compare e
        // scompare cambierebbe la struttura della composizione.
        background2 = (background2 ?: background).follow("background2").takeIf { background2 != null },
    )
}

private const val THEME_FADE_MS = 420
