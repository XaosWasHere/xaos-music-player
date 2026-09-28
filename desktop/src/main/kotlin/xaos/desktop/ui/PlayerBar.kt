package xaos.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xaos.desktop.player.Player
import xaos.desktop.player.RepeatMode
import xaos.desktop.theme.Xaos

/**
 * La barra del player in fondo alla finestra: brano a sinistra, comandi e
 * avanzamento al centro, volume a destra — la disposizione dei player desktop,
 * con i controlli a punti e il play nel colore d'accento.
 */
@Composable
fun PlayerBar(player: Player, onVolumeChange: (Int) -> Unit) {
    val colors = Xaos.colors
    val current by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val volume by player.volume.collectAsState()
    val shuffle by player.shuffle.collectAsState()
    val repeat by player.repeat.collectAsState()

    Column(Modifier.fillMaxWidth().background(colors.sidebar)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brano in corso
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val track = current
                if (track != null) {
                    ArtworkImage(track, size = 58.dp, corner = 10.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else if (!player.available) {
                    Column {
                        Text("VLC NON TROVATO", style = MaterialTheme.typography.labelLarge, color = colors.accentInk)
                        Text("Serve VLC 3 installato per la riproduzione", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
                    }
                } else {
                    Text("NIENTE IN RIPRODUZIONE", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
                }
            }

            // Comandi e avanzamento
            Column(Modifier.width(520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ModeButton(XaosIcons.Shuffle, "Casuale", shuffle) { player.toggleShuffle() }
                    CircleIconButton(XaosIcons.Previous, "Precedente", { player.previous() }, outlined = false)
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.accent, CircleShape)
                            .pressable { player.togglePlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) XaosIcons.Pause else XaosIcons.Play,
                            if (isPlaying) "Pausa" else "Riproduci",
                            tint = colors.onAccent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    CircleIconButton(XaosIcons.Next, "Successivo", { player.next() }, outlined = false)
                    ModeButton(
                        if (repeat == RepeatMode.ONE) XaosIcons.RepeatOne else XaosIcons.Repeat,
                        "Ripeti",
                        repeat != RepeatMode.OFF,
                    ) { player.cycleRepeat() }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(position), style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, modifier = Modifier.width(52.dp))
                    DotSlider(
                        value = if (duration > 0) position.toFloat() / duration else 0f,
                        modifier = Modifier.weight(1f),
                        color = colors.ink,
                        onChangeFinished = { player.seekTo(it) },
                    )
                    Text(
                        formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkTertiary,
                        modifier = Modifier.width(52.dp).padding(start = 10.dp),
                    )
                }
            }

            // Volume
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (volume == 0) XaosIcons.VolumeOff else XaosIcons.Volume,
                    "Volume",
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                DotSlider(
                    value = volume / 100f,
                    modifier = Modifier.width(130.dp),
                    color = colors.ink,
                    onChange = { player.setVolume((it * 100).toInt()) },
                    onChangeFinished = { onVolumeChange((it * 100).toInt()) },
                )
            }
        }
    }
}

/** Casuale e ripeti: icona piena e punto d'accento sotto quando sono attivi. */
@Composable
private fun ModeButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val colors = Xaos.colors
    val tint by animateColorAsState(if (active) colors.ink else colors.inkTertiary, label = "mode")
    Box(Modifier.size(36.dp).clip(CircleShape).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(18.dp))
        if (active) AccentDot(size = 4.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
    }
}
