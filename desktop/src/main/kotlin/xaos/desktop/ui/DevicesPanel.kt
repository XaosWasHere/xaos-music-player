package xaos.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xaos.desktop.beam.BeamServer
import xaos.desktop.beam.ConnectedPhone
import xaos.desktop.player.Player
import xaos.desktop.theme.Xaos
import xaos.desktop.i18n.t

/**
 * Il pulsante dei dispositivi nella barra del player, come su Spotify: si
 * accende quando un telefono collegato con Beam sta suonando, e apre il
 * pannello per vederlo, comandarlo e spostare l'ascolto.
 */
@Composable
fun DevicesButton(beam: BeamServer, player: Player) {
    val phones by beam.phones.collectAsState()
    var open by remember { mutableStateOf(false) }
    val someonePlaying = phones.values.any { it.now.playing && it.now.track != null }
    Box {
        ModeButton(XaosIcons.Phone, t("Dispositivi"), someonePlaying) { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Xaos.colors.surface,
        ) {
            DevicesPanel(beam, player)
        }
    }
}

@Composable
private fun DevicesPanel(beam: BeamServer, player: Player) {
    val colors = Xaos.colors
    val phones by beam.phones.collectAsState()
    val current by player.current.collectAsState()
    val pcPlaying by player.isPlaying.collectAsState()
    // Solo gli altri dispositivi: cosa suonano e come spostare l'ascolto. I
    // comandi stanno già nella barra del player, che segue chi suona.
    Column(Modifier.width(360.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(t("DISPOSITIVI"))
        if (phones.isEmpty()) {
            Text(
                t("Nessun telefono collegato. Sul telefono apri Xaos con Beam acceso e abbinato a questo PC."),
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
            )
        }
        phones.values.sortedBy { it.name }.forEachIndexed { i, phone ->
            if (i > 0) Hairline()
            PhoneDevice(phone, beam, hasPcTrack = current != null, pcPlaying = pcPlaying)
        }
    }
}

@Composable
private fun PhoneDevice(phone: ConnectedPhone, beam: BeamServer, hasPcTrack: Boolean, pcPlaying: Boolean) {
    val track = phone.now.track
    DeviceHeader(
        name = phone.name.uppercase(),
        detail = track?.let { "${it.title} · ${xaos.desktop.library.displayName(it.artist)}" } ?: t("Niente in riproduzione"),
        live = phone.now.playing && track != null,
        icon = XaosIcons.Phone,
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Quello che suona il telefono continua qui, da dove era arrivato; se
        // qui sta già suonando qualcosa, non serve. "Manda al telefono" non
        // serve se suona già solo il telefono.
        if (track != null && phone.now.queue.any { it != null } && !pcPlaying) {
            PillButton(t("ASCOLTA QUI"), onClick = { beam.connect.takeFromPhone(phone.name) }, icon = XaosIcons.Play, filled = true)
        }
        if (hasPcTrack && (pcPlaying || !phone.now.playing)) {
            PillButton(t("MANDA AL TELEFONO"), onClick = { beam.connect.transferToPhone(phone.name) }, icon = XaosIcons.Phone)
        }
    }
}

@Composable
private fun DeviceHeader(name: String, detail: String, live: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = Xaos.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp)) { AccentDot(size = 8.dp, color = if (live) colors.accent else colors.track) }
        Icon(icon, null, tint = colors.inkSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
