package xaos.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import xaos.desktop.CustomTheme
import xaos.desktop.Settings
import xaos.desktop.SettingsData
import xaos.desktop.ThemePreset
import xaos.desktop.theme.customized
import xaos.desktop.theme.DarkPalette
import xaos.desktop.theme.LightPalette
import xaos.desktop.theme.Xaos

/** I colori del tema che si possono scegliere, e dove stanno in [CustomTheme]. */
private enum class ThemeSlot(val label: String, val hint: String) {
    BACKGROUND("SFONDO", "Il fondo di tutta l'app"),
    BACKGROUND2("SFUMATURA", "Il secondo colore dello sfondo, in diagonale"),
    PANELS("PANNELLI", "Schede, menu e finestre"),
    INK("TESTO", "Testo e icone; le tonalità più tenui se ne ricavano"),
    ACCENT("ACCENTO", "Play, pallini d'accento, selezioni"),
}

/**
 * La personalizzazione del tema: si parte dal tema chiaro o scuro e si
 * cambiano i colori che si vogliono. Tutto si vede subito, mentre si sceglie.
 */
@Composable
fun ThemeEditor(prefs: SettingsData, settings: Settings) {
    val colors = Xaos.colors
    val t = prefs.customTheme
    val base = if (prefs.dark) DarkPalette else LightPalette
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
        val v = color?.toArgb()?.toLong()
        settings.update { s ->
            val c = s.customTheme
            s.copy(customTheme = when (slot) {
                ThemeSlot.BACKGROUND -> c.copy(background = v)
                ThemeSlot.BACKGROUND2 -> c.copy(background2 = v)
                ThemeSlot.PANELS -> c.copy(panels = v)
                ThemeSlot.INK -> c.copy(ink = v)
                ThemeSlot.ACCENT -> c.copy(accent = v)
            })
        }
    }

    Text("TEMA PERSONALIZZATO", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (t.enabled) "I tuoi colori, sopra il tema ${if (prefs.dark) "scuro" else "chiaro"}." else "Scegli i colori di sfondo, pannelli, testo e accento.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSecondary,
            modifier = Modifier.weight(1f),
        )
        XaosSwitch(t.enabled, onChange = { on -> settings.update { it.copy(customTheme = it.customTheme.copy(enabled = on)) } })
    }
    if (t.enabled) {

        ThemeSlot.entries.forEach { slot ->
            val value = current(slot)
            Row(
                Modifier.fillMaxWidth().hoverRow().pressable { editing = slot }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Swatch(value ?: fallback(slot), size = 26.dp, dimmed = value == null && slot == ThemeSlot.BACKGROUND2)
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
                    CircleIconButton(XaosIcons.Close, "Torna al predefinito", { set(slot, null) }, size = 26.dp, outlined = false)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PALLINI DI SFONDO", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                Text("La griglia di punti dietro le schermate", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
            }
            XaosSwitch(t.dots, onChange = { on -> settings.update { it.copy(customTheme = it.customTheme.copy(dots = on)) } })
        }
        PillButton("RIPRISTINA I COLORI", onClick = {
            settings.update { it.copy(customTheme = CustomTheme(enabled = true)) }
        }, icon = XaosIcons.Sync)

    }
    ThemePresets(prefs, settings)

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
private fun Swatch(color: Color, size: androidx.compose.ui.unit.Dp, dimmed: Boolean = false) {
    val colors = Xaos.colors
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dimmed) color.copy(alpha = 0.35f) else color, CircleShape)
            .border(1.dp, colors.line, CircleShape),
    )
}

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private val PRESETS = listOf(
    0xFF000000, 0xFF1C1C1C, 0xFF606060, 0xFFF2F2F2, 0xFFFFFFFF,
    0xFFD71921, 0xFFFFC700, 0xFFFF6B00, 0xFF2BD96B, 0xFF1E88FF, 0xFF7C4DFF, 0xFFFF4FA3,
)

/**
 * Il selettore di colore: saturazione e luminosità nel quadrato, la tinta
 * nella barra, i colori di Nothing e qualche altro già pronti, e il codice
 * esadecimale. Ogni movimento si applica subito all'app.
 */
@Composable
private fun ColorPickerDialog(title: String, initial: Color, onChange: (Color) -> Unit, onDismiss: () -> Unit) {
    val colors = Xaos.colors
    val hsb = remember { java.awt.Color.RGBtoHSB((initial.red * 255).toInt(), (initial.green * 255).toInt(), (initial.blue * 255).toInt(), null) }
    var hue by remember { mutableFloatStateOf(hsb[0]) }
    var sat by remember { mutableFloatStateOf(hsb[1]) }
    var bri by remember { mutableFloatStateOf(hsb[2]) }
    var hex by remember { mutableStateOf(initial.hex()) }

    fun colorOf(h: Float, s: Float, b: Float) = Color(java.awt.Color.HSBtoRGB(h, s, b) or (0xFF shl 24))
    fun emit() {
        val c = colorOf(hue, sat, bri)
        hex = c.hex()
        onChange(c)
    }
    fun setFrom(c: Color) {
        val v = java.awt.Color.RGBtoHSB((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), null)
        hue = v[0]; sat = v[1]; bri = v[2]
        emit()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Saturazione (orizzontale) e luminosità (verticale).
                val pure = colorOf(hue, 1f, 1f)
                Canvas(
                    Modifier
                        .width(300.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            fun pick(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                bri = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                emit()
                            }
                            detectTapGestures { pick(it) }
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
                    drawCircle(Color.White, 8.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.5f), 9.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                }
                // La tinta.
                Canvas(
                    Modifier
                        .width(300.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESETS.forEach { v ->
                        val c = Color(v.toInt())
                        Box(Modifier.pressable { setFrom(c) }) { Swatch(c, size = 20.dp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Swatch(colorOf(hue, sat, bri), size = 34.dp)
                    XaosTextField("CODICE", hex, { typed ->
                        hex = typed.uppercase().take(7)
                        val clean = typed.trim().removePrefix("#")
                        if (clean.length == 6) clean.toLongOrNull(16)?.let { setFrom(Color((0xFF000000 or it).toInt())) }
                    }, modifier = Modifier.width(160.dp), placeholder = "#RRGGBB")
                }
            }
        },
        confirmButton = { PillButton("FATTO", onClick = onDismiss, filled = true) },
    )
}

/**
 * I preset: temi salvati con un nome. Un clic lo applica; con un nome già
 * usato il salvataggio aggiorna quel preset. Si sincronizzano col telefono.
 */
@Composable
private fun ThemePresets(prefs: SettingsData, settings: Settings) {
    val colors = Xaos.colors
    val current = prefs.customTheme.normalized()
    var saving by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ThemePreset?>(null) }

    Spacer(Modifier.height(6.dp))
    Text("PRESET", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
    if (prefs.themePresets.isEmpty()) {
        Text(
            "Salva i colori di adesso con un nome, per riapplicarli quando vuoi. I preset arrivano anche sul telefono.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkTertiary,
        )
    }
    prefs.themePresets.forEach { preset ->
        val inUse = current.enabled && preset.theme.normalized().copy(enabled = true) == current
        Row(
            Modifier
                .fillMaxWidth()
                .hoverRow()
                .pressable { settings.update { it.copy(customTheme = preset.theme.copy(enabled = true)) } }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemePreview(preset.theme, prefs.dark)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(preset.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.ink)
                Text(
                    if (inUse) "IN USO" else "Clic per applicarlo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inUse) colors.accentInk else colors.inkTertiary,
                )
            }
            CircleIconButton(XaosIcons.Edit, "Rinomina", { renaming = preset }, size = 28.dp, outlined = false)
            Spacer(Modifier.width(4.dp))
            CircleIconButton(
                XaosIcons.Delete,
                "Elimina il preset",
                { settings.update { s -> s.copy(themePresets = s.themePresets.filter { it.id != preset.id }) } },
                size = 28.dp,
                outlined = false,
            )
        }
    }
    if (prefs.customTheme.enabled) {
        PillButton("SALVA COME PRESET", onClick = { saving = true }, icon = XaosIcons.Add)
    }

    if (saving) {
        NameDialog(
            title = "SALVA COME PRESET",
            initial = "",
            hint = "Con un nome già usato, quel preset prende i colori di adesso.",
            onConfirm = { name ->
                saving = false
                settings.update { s ->
                    val theme = s.customTheme.copy(enabled = true).normalized()
                    val same = s.themePresets.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    s.copy(
                        themePresets = if (same != null) s.themePresets.map { if (it.id == same.id) it.copy(theme = theme) else it }
                        else s.themePresets + ThemePreset("tp_${System.currentTimeMillis()}", name, theme),
                    )
                }
            },
            onDismiss = { saving = false },
        )
    }
    renaming?.let { preset ->
        NameDialog(
            title = "RINOMINA",
            initial = preset.name,
            hint = null,
            onConfirm = { name ->
                renaming = null
                settings.update { s -> s.copy(themePresets = s.themePresets.map { if (it.id == preset.id) it.copy(name = name) else it }) }
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, hint: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = Xaos.colors
    var name by remember { mutableStateOf(initial) }
    val ok = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                XaosTextField("NOME", name, { name = it.take(40) }, modifier = Modifier.width(300.dp), placeholder = "Notte, Carta, Ufficio…")
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.inkTertiary)
            }
        },
        confirmButton = { PillButton("SALVA", onClick = { if (ok) onConfirm(name.trim()) }, filled = true, enabled = ok) },
        dismissButton = { PillButton("ANNULLA", onClick = onDismiss) },
    )
}

/**
 * L'anteprima di un tema in miniatura: lo sfondo (anche sfumato), un pannello,
 * una riga di testo e il punto d'accento, con i colori che il tema darebbe.
 */
@Composable
internal fun ThemePreview(theme: CustomTheme, dark: Boolean) {
    val colors = Xaos.colors
    val p = (if (dark) DarkPalette else LightPalette).customized(theme.copy(enabled = theme.enabled))
    val shape = RoundedCornerShape(8.dp)
    val second = p.background2
    Box(
        Modifier
            .size(width = 46.dp, height = 30.dp)
            .clip(shape)
            .background(if (second != null) Brush.linearGradient(listOf(p.background, second)) else Brush.linearGradient(listOf(p.background, p.background)))
            .border(1.dp, colors.line, shape),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(width = 22.dp, height = 14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(p.surface),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 5.dp, top = 6.dp)
                .size(width = 16.dp, height = 3.dp)
                .background(p.ink, RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
                .size(7.dp)
                .background(p.accent, CircleShape),
        )
    }
}
