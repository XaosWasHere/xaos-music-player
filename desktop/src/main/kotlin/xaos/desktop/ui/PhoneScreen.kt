package xaos.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xaos.desktop.library.LibrarySnapshot
import xaos.desktop.sync.PhoneState
import xaos.desktop.sync.PhoneSync
import xaos.desktop.sync.SyncPlan
import xaos.desktop.sync.SyncStatus
import xaos.desktop.theme.DotText
import xaos.desktop.theme.Xaos
import java.util.Locale

@Composable
fun PhoneScreen(
    phone: PhoneSync,
    snapshot: LibrarySnapshot,
    preferMp3: Boolean,
    onPreferMp3Change: (Boolean) -> Unit,
) {
    val state by phone.state.collectAsState()
    val plan by phone.plan.collectAsState()
    val status by phone.status.collectAsState()
    val root = snapshot.root

    // Appena il telefono c'è e la libreria è letta, si calcola cosa manca:
    // collegarlo deve bastare a vedere la situazione.
    LaunchedEffect(state, snapshot.tracks.size, preferMp3) {
        if (state is PhoneState.Connected && root != null && snapshot.tracks.isNotEmpty()) {
            phone.plan(root, snapshot.tracks, preferMp3)
        }
    }
    val hasTwins = remember(snapshot) { snapshot.tracks.any { it.mobilePath != null } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 28.dp)) {
        item {
            ScreenTitle(
                "TELEFONO",
                caption = "INVIO VIA USB · SOLO AGGIUNTE, NIENTE VIENE CANCELLATO",
            )
        }
        item {
            when (val s = state) {
                PhoneState.NoAdb -> InfoCard(
                    "ADB NON TROVATO",
                    "Serve l'utilità adb di Android (platform-tools). Installala con Android Studio " +
                        "o scaricala da developer.android.com, poi riavvia Xaos.",
                )
                PhoneState.Disconnected -> InfoCard(
                    "COLLEGA IL TELEFONO",
                    "Collegalo con il cavo USB e tieni attivo il Debug USB nelle Opzioni sviluppatore. " +
                        "Xaos lo riconosce da solo in un paio di secondi.",
                )
                is PhoneState.Unauthorized -> InfoCard(
                    "CONFERMA SUL TELEFONO",
                    "Sul telefono è comparso \"Consentire il debug USB?\": tocca Consenti " +
                        "(e \"Consenti sempre da questo computer\", così non lo chiede più).",
                )
                is PhoneState.Connected -> ConnectedPanel(
                    state = s,
                    remoteRoot = phone.remoteRoot,
                    plan = plan,
                    status = status,
                    onSync = { if (root != null) phone.sync(root, snapshot.tracks, preferMp3) },
                    onCancel = phone::cancel,
                    onRefresh = { if (root != null) phone.plan(root, snapshot.tracks, preferMp3) },
                    onDismiss = phone::dismissResult,
                )
            }
        }

        if (hasTwins) {
            item {
                FormatChoice(
                    preferMp3 = preferMp3,
                    enabled = status !is SyncStatus.Running,
                    onChange = onPreferMp3Change,
                )
            }
        }

        val pending = plan?.missing.orEmpty()
        if (state is PhoneState.Connected && pending.isNotEmpty() && status !is SyncStatus.Running) {
            val byAlbum = pending.groupBy { it.track.album }.entries.sortedBy { it.key.lowercase() }
            item {
                SectionHeader(
                    "DA INVIARE",
                    count = byAlbum.size,
                    modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
                )
            }
            items(byAlbum, key = { it.key }) { (album, entries) ->
                val colors = Xaos.colors
                Row(
                    Modifier.fillMaxWidth().hoverRow().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(entries.first().track, size = 36.dp, corner = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(album.uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("[${entries.size}] · ${formatBytes(entries.sumOf { it.size })}", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
                }
            }
        }
    }
}

@Composable
private fun ConnectedPanel(
    state: PhoneState.Connected,
    remoteRoot: String,
    plan: SyncPlan?,
    status: SyncStatus,
    onSync: () -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Xaos.colors
    Column(Modifier.fillMaxWidth().nothingCard().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(XaosIcons.Phone, null, tint = colors.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.name.uppercase(), style = MaterialTheme.typography.titleLarge, color = colors.ink)
                Text("CARTELLA  $remoteRoot", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
            }
            AccentDot(size = 8.dp)
            Spacer(Modifier.width(8.dp))
            Text("COLLEGATO", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        }

        Hairline()

        when {
            status is SyncStatus.Running -> RunningView(status, onCancel)
            plan == null || status is SyncStatus.Planning -> Row(verticalAlignment = Alignment.CenterVertically) {
                DotSpinner(size = 22.dp)
                Spacer(Modifier.width(12.dp))
                Text("CONFRONTO CON LA LIBRERIA DEL TELEFONO…", style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
            }
            else -> PlanView(plan, onSync, onRefresh)
        }

        when (status) {
            is SyncStatus.Done -> ResultLine(
                text = buildString {
                    append(if (status.cancelled) "INTERROTTO · " else "COMPLETATO · ")
                    append("[${status.sent}] INVIATI")
                    if (status.failed > 0) append(" · [${status.failed}] NON RIUSCITI")
                },
                onDismiss = onDismiss,
            )
            is SyncStatus.Failed -> ResultLine("ERRORE · ${status.message}", onDismiss)
            else -> Unit
        }
    }
}

@Composable
private fun PlanView(plan: SyncPlan, onSync: () -> Unit, onRefresh: () -> Unit) {
    val colors = Xaos.colors
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        BigStat(plan.missing.size.toString(), "DA INVIARE")
        BigStat(formatBytes(plan.bytesToSend), "DA COPIARE")
        BigStat(plan.alreadyThere.toString(), "GIÀ SUL TELEFONO")
        plan.freeBytes?.let { BigStat(formatBytes(it), "SPAZIO LIBERO") }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
            text = if (plan.missing.isEmpty()) "TUTTO SINCRONIZZATO" else "SINCRONIZZA",
            onClick = onSync,
            icon = XaosIcons.Sync,
            filled = true,
            enabled = plan.missing.isNotEmpty() && plan.fits,
        )
        PillButton("RICONTROLLA", onClick = onRefresh)
        if (!plan.fits) {
            Text(
                "NON C'È ABBASTANZA SPAZIO SUL TELEFONO",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentInk,
            )
        }
    }
}

@Composable
private fun RunningView(status: SyncStatus.Running, onCancel: () -> Unit) {
    val colors = Xaos.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DotText("${(status.fraction * 100).toInt()}%", color = colors.ink, pitch = 5.dp)
            Text(
                "[${status.doneFiles}/${status.totalFiles}] · ${formatBytes(status.doneBytes)} / ${formatBytes(status.totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkTertiary,
            )
        }
        DotProgressLine(status.fraction, modifier = Modifier.fillMaxWidth().height(10.dp), spacing = 6.dp, radius = 1.8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotSpinner(size = 16.dp)
            Spacer(Modifier.width(10.dp))
            Text(status.currentName, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            PillButton("ANNULLA", onClick = onCancel, icon = XaosIcons.Close)
        }
    }
}

@Composable
private fun BigStat(value: String, label: String) {
    val colors = Xaos.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DotText(value, color = colors.ink, pitch = 4.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
    }
}

@Composable
private fun ResultLine(text: String, onDismiss: () -> Unit) {
    val colors = Xaos.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        AccentDot(size = 6.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.ink, modifier = Modifier.weight(1f))
        Icon(XaosIcons.Close, "Chiudi", tint = colors.inkTertiary, modifier = Modifier.size(16.dp).pressable(onDismiss))
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    val colors = Xaos.colors
    Column(Modifier.fillMaxWidth().nothingCard().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentDot(size = 7.dp, color = colors.track)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.ink)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.inkSecondary)
    }
}

/**
 * Quale versione mandare quando un brano c'è sia in FLAC sia in MP3. Compare
 * solo se la libreria ha davvero delle copie doppie.
 */
@Composable
private fun FormatChoice(preferMp3: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Xaos.colors
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("VERSIONE PER IL TELEFONO")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("MP3 QUANDO C'È", onClick = { onChange(true) }, filled = preferMp3, enabled = enabled || preferMp3)
            PillButton("ORIGINALE (FLAC)", onClick = { onChange(false) }, filled = !preferMp3, enabled = enabled || !preferMp3)
            Spacer(Modifier.width(8.dp))
            Text(
                if (preferMp3) "Le copie MP3 pesano circa un quinto dei FLAC."
                else "Qualità piena, ma occupa molto più spazio.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkTertiary,
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
        else -> String.format(Locale.US, "%d MB", bytes / 1_048_576)
    }
}
