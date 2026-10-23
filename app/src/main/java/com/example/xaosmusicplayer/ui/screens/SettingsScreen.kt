package com.example.xaosmusicplayer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.i18n.I18n
import com.example.xaosmusicplayer.i18n.Language
import com.example.xaosmusicplayer.i18n.t
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.components.SectionHeader
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.ThemeStore
import com.example.xaosmusicplayer.ui.theme.Xaos

/**
 * Le impostazioni, tutte in un posto: ci si arriva dall'ingranaggio in alto a
 * destra di ogni sezione, così non serve passare dalla Libreria.
 */
@Composable
fun SettingsScreen(
    sleepRemainingMs: Long?,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onRescan: () -> Unit,
    onUpdateEngine: () -> Unit,
    onOpenBeam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = ThemeStore.get(context)
    val dark by store.isDark.collectAsState()
    val custom by store.custom.collectAsState()
    val beamOn by com.example.xaosmusicplayer.beam.Beam.enabled.collectAsState()
    val beamStatus by com.example.xaosmusicplayer.beam.Beam.status.collectAsState()
    val beamPc by com.example.xaosmusicplayer.beam.Beam.pairing.collectAsState()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = t("IMPOSTAZIONI"), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Group(t("ASPETTO"))
            SettingRow(
                icon = XaosIcons.Contrast,
                title = t("TEMA SCURO"),
                hint = if (custom.enabled) t("Il tema di partenza per i colori personalizzati") else t("Nero, oppure chiaro come la carta"),
                onClick = store::toggle,
                trailing = {
                    Switch(
                        checked = dark,
                        onCheckedChange = { store.setDark(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Xaos.colors.onAccent,
                            checkedTrackColor = Xaos.colors.accent,
                            checkedBorderColor = Xaos.colors.accent,
                            uncheckedThumbColor = Xaos.colors.inkTertiary,
                            uncheckedTrackColor = Xaos.colors.card,
                            uncheckedBorderColor = Xaos.colors.line,
                        ),
                    )
                },
            )
            SettingRow(
                icon = XaosIcons.Edit,
                title = t("PERSONALIZZA TEMA"),
                hint = if (custom.enabled) t("Attivo · preset condivisi col PC") else t("Sfondo, pannelli, testo, accento e preset"),
                onClick = onOpenTheme,
            )

            Group(t("LINGUA"))
            LanguageSelector(
                selected = I18n.choice,
                onSelect = { I18n.setChoice(context, it) },
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Group(t("RIPRODUZIONE"))
            SettingRow(
                icon = XaosIcons.Equalizer,
                title = t("EQUALIZZATORE"),
                hint = t("Bande, preamplificazione, bass boost e virtualizer"),
                onClick = onOpenEqualizer,
            )
            SettingRow(
                icon = XaosIcons.Timer,
                title = t("SLEEP TIMER"),
                hint = sleepRemainingMs?.let { t("Attivo: ancora %s", formatMinutes(it)) } ?: t("Ferma la musica dopo un po'"),
                onClick = onOpenSleepTimer,
            )

            Group(t("LIBRERIA"))
            SettingRow(
                icon = XaosIcons.Sync,
                title = t("RISCANSIONA"),
                hint = t("Rilegge la musica del telefono"),
                onClick = onRescan,
            )
            SettingRow(
                icon = XaosIcons.Download,
                title = t("AGGIORNA MOTORE DOWNLOAD"),
                hint = t("Scarica l'ultima versione di yt-dlp, se i download non vanno"),
                onClick = onUpdateEngine,
            )

            Group("XAOS BEAM")
            SettingRow(
                icon = XaosIcons.Computer,
                title = "XAOS BEAM",
                hint = if (!beamOn) t("Ascolta la libreria del PC in Wi-Fi, senza copiarla")
                else beamPc?.let { "${it.name} · ${statusText(beamStatus).lowercase()}" } ?: t("Acceso · nessun PC abbinato"),
                onClick = onOpenBeam,
                trailing = {
                    Switch(
                        checked = beamOn,
                        onCheckedChange = com.example.xaosmusicplayer.beam.Beam::setEnabled,
                        colors = beamSwitchColors(),
                    )
                },
            )
            SettingRow(
                icon = XaosIcons.Sync,
                title = t("DISPOSITIVI"),
                hint = t("Cerca i PC in rete e abbina questo telefono"),
                onClick = onOpenBeam,
            )

            Group(t("INFO"))
            SettingRow(
                icon = XaosIcons.MusicNote,
                title = "XAOS $version",
                hint = t("Il progetto su GitHub"),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

private const val GITHUB_URL = "https://github.com/XaosWasHere/xaos-music-player"

private fun formatMinutes(ms: Long): String {
    val minutes = (ms + 59_999) / 60_000
    return if (minutes >= 60) t("%d h %d min", minutes / 60, minutes % 60) else t("%d min", minutes)
}

/**
 * La lingua dell'app, in una capsula come le modalità dello sfondo:
 * automatica (quella del telefono) o una delle lingue, col nome nella lingua stessa.
 */
@Composable
private fun LanguageSelector(
    selected: Language?,
    onSelect: (Language?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Xaos.colors
    val shape = RoundedCornerShape(22.dp)
    val options: List<Language?> = listOf(null) + Language.entries
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card, shape)
            .border(1.dp, colors.line, shape)
            .padding(4.dp),
    ) {
        options.forEach { lang ->
            val isSelected = lang == selected
            val fill by animateColorAsState(
                if (isSelected) colors.ink else Color.Transparent,
                label = "lang-fill",
            )
            val tint by animateColorAsState(
                if (isSelected) colors.background else colors.inkSecondary,
                label = "lang-tint",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(fill)
                    .clickable { onSelect(lang) }
                    .padding(vertical = 12.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = lang?.nativeName ?: t("AUTOMATICA"),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(18.dp))
    SectionHeader(title)
    Spacer(Modifier.height(6.dp))
    Hairline()
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Xaos.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.inkTertiary)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}
