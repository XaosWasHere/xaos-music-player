package com.example.xaosmusicplayer.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.beam.BeamConnect
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.ui.components.AccentDot
import com.example.xaosmusicplayer.ui.components.Artwork
import com.example.xaosmusicplayer.ui.components.PillButton
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos

/**
 * I dispositivi, come su Spotify: cosa suona questo telefono, cosa suona il
 * PC collegato con Beam, i comandi del PC e i due spostamenti — "ascolta qui"
 * (quello che suona il PC continua sul telefono) e "manda al PC".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(
    phoneSong: Song?,
    phonePlaying: Boolean,
    pcName: String?,
    pc: BeamConnect.PcNow?,
    onDismiss: () -> Unit,
    onTakeFromPc: () -> Unit,
    onSendToPc: () -> Unit,
    onOpenBeam: () -> Unit,
) {
    val colors = Xaos.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("DISPOSITIVI", style = MaterialTheme.typography.titleMedium, color = colors.ink)

            // Solo l'altro dispositivo: cosa suona e come spostare l'ascolto. I
            // comandi stanno già nel player, che segue il dispositivo attivo.
            if (pcName == null || pc == null) {
                Text(
                    if (pcName == null) "Nessun PC abbinato: accendi Xaos Beam e abbina il PC per vederlo e comandarlo da qui."
                    else "$pcName non è raggiungibile in questo momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkTertiary,
                )
                PillButton("XAOS BEAM", onClick = onOpenBeam, icon = XaosIcons.Computer)
            } else {
                DeviceLine(
                    name = pcName.uppercase(),
                    detail = if (pc.hasTrack) "${pc.title} · ${pc.artist}" else "Niente in riproduzione",
                    live = pc.playing && pc.hasTrack,
                    artwork = pc.pcId?.let(BeamConnect::artworkUrl)?.let(Uri::parse),
                    icon = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "Ascolta qui" non serve se qui sta già suonando qualcosa;
                    // "Manda al PC" non serve se suona già solo il PC.
                    if (pc.hasTrack && pc.queue.isNotEmpty() && !phonePlaying) {
                        PillButton("ASCOLTA QUI", onClick = { onTakeFromPc(); onDismiss() }, icon = XaosIcons.Play, filled = true)
                    }
                    if (phoneSong != null && (phonePlaying || !pc.playing)) {
                        PillButton("MANDA AL PC", onClick = { onSendToPc(); onDismiss() }, icon = XaosIcons.Computer)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceLine(name: String, detail: String, live: Boolean, artwork: Uri?, icon: Boolean = false) {
    val colors = Xaos.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(16.dp)) { AccentDot(size = 8.dp, color = if (live) colors.accent else colors.line) }
        Artwork(uri = artwork, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon) {
                    Icon(XaosIcons.Computer, null, tint = colors.inkSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(name, style = MaterialTheme.typography.labelLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
