package com.example.xaosmusicplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.components.PillButton
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.CustomTheme
import com.example.xaosmusicplayer.ui.theme.DarkPalette
import com.example.xaosmusicplayer.ui.theme.LightPalette
import com.example.xaosmusicplayer.ui.theme.ThemeStore
import com.example.xaosmusicplayer.ui.theme.Xaos

/** I colori del tema che si possono scegliere, e dove stanno in [CustomTheme]. */
private enum class ThemeSlot(val label: String, val hint: String) {
    BACKGROUND("SFONDO", "Il fondo di tutta l'app"),
    BACKGROUND2("SFUMATURA", "Il secondo colore dello sfondo, in diagonale"),
    PANELS("PANNELLI", "Schede, menu e finestre"),
    INK("TESTO", "Testo e icone; le tonalità più tenui se ne ricavano"),
    ACCENT("ACCENTO", "Play, pallini d'accento, selezioni"),
}

/**
 * La personalizzazione del tema, la stessa di Xaos desktop: si parte dal tema
 * chiaro o scuro e si cambiano i colori che si vogliono, vedendoli subito. Il
 * tema viaggia col PC a ogni sincronizzazione.
 */
@Composable
fun ThemeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val store = ThemeStore.get(LocalContext.current)
    val t by store.custom.collectAsState()
    val dark by store.isDark.collectAsState()
    val colors = Xaos.colors
    val base = if (dark) DarkPalette else LightPalette
    var editing by remember { mutableStateOf<ThemeSlot?>(null) }

    fun current(slot: ThemeSlot): Color? = when (slot) {
        ThemeSlot.BACKGROUND -> t.background
        ThemeSlot.BACKGROUND2 -> t.background2
        ThemeSlot.PANELS -> t.panels
        ThemeSlot.INK -> t.ink
        ThemeSlot.ACCENT -> t.accent
    }?.let { Color(it.toInt()) }

    fun fallback(slot: ThemeSlot): Color = when (slot) {
        ThemeSlot.BACKGROUND -> base.background
        ThemeSlot.BACKGROUND2 -> current(ThemeSlot.BACKGROUND) ?: base.background
        ThemeSlot.PANELS -> base.surface
        ThemeSlot.INK -> base.ink
        ThemeSlot.ACCENT -> base.accent
    }

    fun set(slot: ThemeSlot, color: Color?) {
        val v = color?.let(CustomTheme::argb)
        val c = store.custom.value
        store.setCustom(
            when (slot) {
                ThemeSlot.BACKGROUND -> c.copy(background = v)
                ThemeSlot.BACKGROUND2 -> c.copy(background2 = v)
                ThemeSlot.PANELS -> c.copy(panels = v)
                ThemeSlot.INK -> c.copy(ink = v)
                ThemeSlot.ACCENT -> c.copy(accent = v)
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = "TEMA", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            ToggleRow(
                title = "PERSONALIZZATO",
                hint = if (t.enabled) "I tuoi colori, sopra il tema ${if (dark) "scuro" else "chiaro"}. Arrivano anche sul PC."
                else "Scegli i colori di sfondo, pannelli, testo e accento.",
                checked = t.enabled,
                onChange = { on -> store.setCustom(store.custom.value.copy(enabled = on)) },
            )
            if (t.enabled) {
                Hairline()
                Spacer(Modifier.height(8.dp))
                ThemeSlot.entries.forEach { slot ->
                    val value = current(slot)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { editing = slot }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Swatch(value ?: fallback(slot), size = 30.dp, dimmed = value == null && slot == ThemeSlot.BACKGROUND2)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(slot.label, style = MaterialTheme.typography.labelLarge, color = colors.ink)
                            Text(
                                if (slot == ThemeSlot.BACKGROUND2 && value == null) "Nessuna: sfondo in tinta unita" else slot.hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkTertiary,
                            )
                        }
                        Text(value?.hex() ?: "PREDEFINITO", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
                        if (value != null) {
                            Spacer(Modifier.width(8.dp))
                            CircleIconButton(XaosIcons.Close, "Torna al predefinito", { set(slot, null) }, size = 30.dp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Hairline()
                ToggleRow(
                    title = "PALLINI DI SFONDO",
                    hint = "La griglia di punti dietro le schermate",
                    checked = t.dots,
                    onChange = { on -> store.setCustom(store.custom.value.copy(dots = on)) },
                )
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = "RIPRISTINA I COLORI",
                    icon = XaosIcons.Sync,
                    onClick = { store.setCustom(CustomTheme(enabled = true)) },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    editing?.let { slot ->
        ColorPickerDialog(
            title = slot.label,
            initial = current(slot) ?: fallback(slot),
            onChange = { set(slot, it) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Xaos.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.inkSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.inkTertiary,
                uncheckedTrackColor = colors.card,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}

@Composable
private fun Swatch(color: Color, size: Dp, dimmed: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dimmed) color.copy(alpha = 0.35f) else color, CircleShape)
            .border(1.dp, Xaos.colors.line, CircleShape),
    )
}

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private val PRESETS = listOf(
    0xFF000000, 0xFF1C1C1C, 0xFF606060, 0xFFF2F2F2, 0xFFFFFFFF,
    0xFFD71921, 0xFFFFC700, 0xFFFF6B00, 0xFF2BD96B, 0xFF1E88FF, 0xFF7C4DFF, 0xFFFF4FA3,
)

private fun hsvOf(c: Color): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }

private fun colorOf(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h * 360f, s, v)))

/**
 * Il selettore di colore: saturazione e luminosità nel quadrato, la tinta
 * nella barra, i colori di Nothing e qualche altro già pronti, e il codice
 * esadecimale. Ogni tocco si applica subito all'app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(title: String, initial: Color, onChange: (Color) -> Unit, onDismiss: () -> Unit) {
    val colors = Xaos.colors
    val start = remember { hsvOf(initial) }
    var hue by remember { mutableFloatStateOf(start[0] / 360f) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var bri by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(initial.hex()) }

    fun emit() {
        val c = colorOf(hue, sat, bri)
        hex = c.hex()
        onChange(c)
    }
    fun setFrom(c: Color) {
        val v = hsvOf(c)
        hue = v[0] / 360f; sat = v[1]; bri = v[2]
        emit()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Saturazione (orizzontale) e luminosità (verticale).
                val pure = colorOf(hue, 1f, 1f)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                bri = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                emit()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                bri = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                emit()
                            }
                        },
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val c = Offset(sat * size.width, (1f - bri) * size.height)
                    drawCircle(Color.White, 9.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.5f), 10.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                }
                // La tinta.
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { hue = (it.x / size.width).coerceIn(0f, 1f); emit() }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width).coerceIn(0f, 1f)
                                emit()
                            }
                        },
                ) {
                    drawRect(Brush.horizontalGradient((0..6).map { colorOf(it / 6f, 1f, 1f) }))
                    val x = hue * size.width
                    drawCircle(Color.White, size.height / 2f - 1.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(2.dp.toPx()))
                }
                // I colori pronti.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { v ->
                        val c = Color(v.toInt())
                        Box(Modifier.clip(CircleShape).clickable { setFrom(c) }) { Swatch(c, size = 28.dp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Swatch(colorOf(hue, sat, bri), size = 40.dp)
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { typed ->
                            hex = typed.uppercase().take(7)
                            val clean = typed.trim().removePrefix("#")
                            if (clean.length == 6) clean.toLongOrNull(16)?.let { setFrom(Color((0xFF000000 or it).toInt())) }
                        },
                        singleLine = true,
                        label = { Text("Codice") },
                        placeholder = { Text("#RRGGBB") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.ink,
                            unfocusedTextColor = colors.ink,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.line,
                            cursorColor = colors.accentInk,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("FATTO", style = MaterialTheme.typography.titleMedium, color = colors.accentInk)
            }
        },
    )
}
