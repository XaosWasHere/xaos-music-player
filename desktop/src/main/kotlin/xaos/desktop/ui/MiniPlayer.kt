package xaos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xaos.desktop.player.Player
import xaos.desktop.theme.Xaos

/**
 * Il miniplayer: la stessa card degli album — copertina quadrata, titolo in
 * maiuscolo, artista sotto — con i comandi essenziali e l'avanzamento a punti.
 * Sulla copertina, col mouse sopra, i pulsanti per tornare alla finestra
 * intera o chiudere. Si trascina da qualunque punto.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MiniPlayerCard(
    player: Player,
    onExpand: () -> Unit,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Xaos.colors
    val track by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    Column(
        modifier
            // Doppio clic ovunque: torna la finestra intera. Si legge dall'evento
            // del sistema, così il trascinamento della card resta libero.
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press) { event: androidx.compose.ui.input.pointer.PointerEvent ->
                if ((event.nativeEvent as? java.awt.event.MouseEvent)?.clickCount == 2) onExpand()
            }
            .shadow(18.dp, CardShape)
            // La card del tema è velata, pensata per stare sullo sfondo dell'app:
            // qui sotto c'è il desktop, quindi prima un fondo pieno.
            .background(colors.background, CardShape)
            .nothingCard()
            .hoverable(hover)
            .padding(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            // Copertina o testo, a scelta.
            if (showLyrics) {
                Box(
                    Modifier
                        .size(maxWidth)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(colors.surfaceHigh)
                        .padding(horizontal = 10.dp),
                ) {
                    LyricsView(player, track, Modifier.fillMaxSize(), compact = true)
                }
            } else {
                ArtworkImage(track, size = maxWidth, corner = 12.dp)
            }
            if (hovered) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OverlayButton(
                        if (showLyrics) XaosIcons.Album else XaosIcons.Mic,
                        if (showLyrics) "Mostra la copertina" else "Mostra il testo",
                        onToggleLyrics,
                    )
                    OverlayButton(XaosIcons.Fullscreen, "Torna a Xaos (anche con doppio clic)", onExpand)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title?.uppercase() ?: "NIENTE IN RIPRODUZIONE",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.artist ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        DotSlider(
            value = if (duration > 0) position.toFloat() / duration else 0f,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            color = colors.ink,
            onChangeFinished = { player.seekTo(it) },
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(XaosIcons.Previous, "Precedente", { player.previous() }, size = 38.dp, outlined = false)
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(colors.accent, CircleShape).pressable { player.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) XaosIcons.Pause else XaosIcons.Play,
                    if (isPlaying) "Pausa" else "Riproduci",
                    tint = colors.onAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            CircleIconButton(XaosIcons.Next, "Successivo", { player.next() }, size = 38.dp, outlined = false)
        }
    }
}

@Composable
private fun OverlayButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val colors = Xaos.colors
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(colors.background.copy(alpha = 0.82f), CircleShape)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = colors.ink, modifier = Modifier.size(15.dp))
    }
}
