package com.example.xaosmusicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.audio.EqualizerController
import com.example.xaosmusicplayer.audio.formatBandFrequency
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.components.PillButton
import com.example.xaosmusicplayer.ui.theme.Xaos

@Composable
fun EqualizerScreen(
    controller: EqualizerController,
    enabled: Boolean,
    levels: List<Int>,
    selectedPreset: Int,
    bassBoost: Int,
    virtualizer: Int,
    preampDb: Int,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onPresetSelected: (Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onPreampChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = "EQUALIZER", onBack = onBack)

        if (!controller.isAvailable) {
            UnavailableNotice()
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ATTIVO",
                    style = MaterialTheme.typography.titleMedium,
                    color = Xaos.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Xaos.colors.onAccent,
                        checkedTrackColor = Xaos.colors.accent,
                        checkedBorderColor = Xaos.colors.accent,
                        uncheckedThumbColor = Xaos.colors.inkTertiary,
                        uncheckedTrackColor = Xaos.colors.card,
                        uncheckedBorderColor = Xaos.colors.line,
                    ),
                )
            }

            if (controller.presetNames.isNotEmpty()) {
                PresetSelector(
                    presets = controller.presetNames,
                    selected = selectedPreset,
                    onSelect = onPresetSelected,
                )
            }

            Spacer(Modifier.height(20.dp))

            BandSliders(
                frequencies = controller.bandFrequencies,
                levels = levels,
                minLevel = controller.minLevel.toInt(),
                maxLevel = controller.maxLevel.toInt(),
                enabled = enabled,
                onBandChange = onBandChange,
            )

            Spacer(Modifier.height(28.dp))

            PreampSlider(valueDb = preampDb, enabled = enabled, onChange = onPreampChange)

            if (controller.bassBoostSupported) {
                EffectSlider(
                    label = "BASS BOOST",
                    value = bassBoost,
                    enabled = enabled,
                    onChange = onBassBoostChange,
                )
            }
            // Sempre presente: la spazializzazione ora è nostra, quindi non
            // dipende più da cosa supporta il dispositivo.
            EffectSlider(
                label = "VIRTUALIZER",
                value = virtualizer,
                enabled = enabled,
                onChange = onVirtualizerChange,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PresetSelector(presets: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = presets.getOrNull(selected)?.uppercase() ?: "PERSONALIZZATO"

    Box {
        PillButton(text = "PRESET · $label", onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Xaos.colors.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Xaos.colors.line),
        ) {
            presets.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (index == selected) Xaos.colors.accentInk else Xaos.colors.ink,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

/**
 * Le bande, come cursori verticali.
 *
 * Compose non ha uno Slider verticale: si ruota quello orizzontale di -90° e si
 * forza la larghezza al valore che dovrà avere una volta ruotato, altrimenti il
 * layout continua a misurarlo come se fosse ancora orizzontale.
 */
@Composable
private fun BandSliders(
    frequencies: List<Int>,
    levels: List<Int>,
    minLevel: Int,
    maxLevel: Int,
    enabled: Boolean,
    onBandChange: (Int, Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(SLIDER_LENGTH_DP.dp + 44.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        frequencies.forEachIndexed { index, hz ->
            val level = levels.getOrNull(index) ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "${level / 100}dB",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (level == 0) Xaos.colors.inkSecondary else Xaos.colors.accentInk,
                )
                Box(
                    modifier = Modifier.height(SLIDER_LENGTH_DP.dp).weight(1f, fill = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { onBandChange(index, it.toInt()) },
                        valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                        enabled = enabled,
                        modifier = Modifier
                            .requiredWidth(SLIDER_LENGTH_DP.dp)
                            .rotate(-90f),
                        colors = SliderDefaults.colors(
                            thumbColor = Xaos.colors.ink,
                            activeTrackColor = Xaos.colors.accent,
                            inactiveTrackColor = Xaos.colors.track,
                            disabledThumbColor = Xaos.colors.inkSecondary,
                            disabledActiveTrackColor = Xaos.colors.track,
                        ),
                    )
                }
                Text(
                    text = formatBandFrequency(hz),
                    style = MaterialTheme.typography.labelMedium,
                    color = Xaos.colors.inkSecondary,
                )
            }
        }
    }
}

/**
 * Margine dato agli effetti, applicato dentro la pipeline del player.
 *
 * Sta sopra bass boost e virtualizer perché è il rimedio al loro effetto
 * collaterale: sono loro a saturare, e la distorsione cresce con la loro
 * intensità.
 */
@Composable
private fun PreampSlider(valueDb: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "PREAMP",
                style = MaterialTheme.typography.titleMedium,
                color = Xaos.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$valueDb dB",
                style = MaterialTheme.typography.labelMedium,
                color = if (valueDb == 0) Xaos.colors.inkSecondary else Xaos.colors.accentInk,
            )
        }
        Text(
            text = "Dà margine agli effetti. Se il virtualizer gracchia, " +
                "scendi finché smette.",
            style = MaterialTheme.typography.labelMedium,
            color = Xaos.colors.inkSecondary,
        )
        Slider(
            value = valueDb.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = MIN_PREAMP_DB.toFloat()..0f,
            // Un passo per decibel: i valori intermedi non servono a nessuno.
            steps = -MIN_PREAMP_DB - 1,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Xaos.colors.ink,
                activeTrackColor = Xaos.colors.track,
                inactiveTrackColor = Xaos.colors.accent,
            ),
        )
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Xaos.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                // A zero l'effetto non viene proprio inserito: dirlo esplicitamente
                // evita di far credere che sia attivo ma silenzioso.
                text = if (value == 0) "OFF" else "${value / 10}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (value == 0) Xaos.colors.inkSecondary else Xaos.colors.accentInk,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                // Aggancio allo zero: con un cursore continuo da 0 a 1000
                // centrare lo zero esatto è quasi impossibile, e restare
                // all'1% lascerebbe l'effetto inserito senza motivo.
                val raw = it.toInt()
                onChange(if (raw < SNAP_TO_ZERO) 0 else raw)
            },
            valueRange = 0f..1000f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Xaos.colors.ink,
                activeTrackColor = Xaos.colors.accent,
                inactiveTrackColor = Xaos.colors.track,
            ),
        )
    }
}

@Composable
private fun UnavailableNotice() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "L'equalizzatore non è disponibile su questo dispositivo, " +
                "oppure un'altra app lo sta usando.",
            style = MaterialTheme.typography.bodyMedium,
            color = Xaos.colors.inkSecondary,
        )
    }
}

/** Intestazione comune alle schermate secondarie: freccia indietro e titolo. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = XaosIcons.Back,
            contentDescription = "Indietro",
            onClick = onBack,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Xaos.colors.ink,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
    Hairline()
}

private const val SLIDER_LENGTH_DP = 180

/** Sotto il 3% bass boost e virtualizer si considerano spenti. */
private const val SNAP_TO_ZERO = 30

/** Attenuazione massima selezionabile, in dB. */
private const val MIN_PREAMP_DB = -15

