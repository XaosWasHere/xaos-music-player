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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
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
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerBar(
    player: Player,
    onVolumeChange: (Int) -> Unit,
    onOpenFullscreen: () -> Unit,
    lyricsOpen: Boolean,
    onToggleLyrics: () -> Unit,
    onOpenMini: () -> Unit,
    beam: xaos.desktop.beam.BeamServer? = null,
) {
    val colors = Xaos.colors
    val current by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val volume by player.volume.collectAsState()
    val shuffle by player.shuffle.collectAsState()
    val repeat by player.repeat.collectAsState()
    val engine by player.engine.collectAsState()

    // Connect, come su Spotify: la barra segue il dispositivo che suona. Se
    // suona il telefono e il PC no, mostra il brano del telefono e i comandi
    // vanno a lui; resta così finché non riparte il PC.
    val remote = LocalActivePhone.current
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis(), remote != null) {
        while (remote != null) { kotlinx.coroutines.delay(500); value = System.currentTimeMillis() }
    }
    fun onPhone(action: String, ms: Long? = null) {
        val phone = remote ?: return
        beam?.connect?.sendToPhone(phone.name, xaos.desktop.beam.BeamCommand(action, ms = ms))
    }
    val shownPlaying = remote?.now?.playing ?: isPlaying
    val shownDuration = remote?.now?.track?.durationMs ?: duration
    val shownPosition = remote?.positionNow(now)?.coerceAtMost(shownDuration.takeIf { it > 0 } ?: Long.MAX_VALUE) ?: position

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Finestra stretta: comandi al centro più corti e volume più breve, così i
    // pulsanti a destra non escono dalla barra.
    val narrow = maxWidth < 1180.dp
    Column(Modifier.fillMaxWidth().background(colors.sidebar)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brano in corso. Il clic destro apre lo stesso menu dei brani in lista.
            val actions = LocalTrackActions.current
            val favorites = LocalFavorites.current
            var menuOpen by remember { mutableStateOf(false) }
            Row(
                Modifier.weight(1f).onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed && current != null && actions != null) menuOpen = true
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val track = current.takeIf { remote == null }
                if (remote != null) {
                    RemoteNowPlaying(remote, beam)
                }
                if (track != null && actions != null) {
                    Box {
                        TrackMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            items = trackMenuItems(track, track.path in favorites, actions),
                        )
                    }
                }
                if (track != null) {
                    // Copertina e titolo aprono lo schermo intero, come su Spotify.
                    ArtworkImage(track, size = 58.dp, corner = 10.dp, modifier = Modifier.pressable(onOpenFullscreen))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f, fill = false).pressable(onOpenFullscreen)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    FavoriteButton(track, size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                } else if (remote != null) {
                    // Il brano del telefono è già mostrato sopra.
                } else if (engine == Player.Engine.Starting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DotSpinner(size = 16.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("AVVIO DEL MOTORE AUDIO…", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
                    }
                } else if (engine == Player.Engine.Missing) {
                    Column {
                        Text("MOTORE AUDIO NON TROVATO", style = MaterialTheme.typography.labelLarge, color = colors.accentInk)
                        Text("Reinstalla Xaos, oppure installa VLC 3", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
                    }
                } else {
                    Text("NIENTE IN RIPRODUZIONE", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
                }
            }

            // Comandi e avanzamento
            Column(Modifier.width(if (narrow) 380.dp else 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ModeButton(XaosIcons.Shuffle, "Casuale", shuffle && remote == null) {
                        if (remote != null) onPhone("shuffle") else player.toggleShuffle()
                    }
                    CircleIconButton(XaosIcons.Previous, "Precedente", { if (remote != null) onPhone("previous") else player.previous() }, outlined = false)
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.accent, CircleShape)
                            .pressable { if (remote != null) onPhone("toggle") else player.togglePlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayPauseGlyph(shownPlaying, if (shownPlaying) "Pausa" else "Riproduci", colors.onAccent, glyphSize = 24.dp)
                    }
                    CircleIconButton(XaosIcons.Next, "Successivo", { if (remote != null) onPhone("next") else player.next() }, outlined = false)
                    ModeButton(
                        if (repeat == RepeatMode.ONE) XaosIcons.RepeatOne else XaosIcons.Repeat,
                        "Ripeti",
                        repeat != RepeatMode.OFF && remote == null,
                    ) { if (remote != null) onPhone("repeat") else player.cycleRepeat() }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, modifier = Modifier.width(52.dp))
                    DotSlider(
                        value = if (shownDuration > 0) shownPosition.toFloat() / shownDuration else 0f,
                        modifier = Modifier.weight(1f),
                        color = colors.ink,
                        onChangeFinished = { f ->
                            if (remote != null) onPhone("seek", (shownDuration * f).toLong()) else player.seekTo(f)
                        },
                    )
                    Text(
                        formatDuration(shownDuration),
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
                    // Col peso il volume si accorcia quando lo spazio manca, invece
                    // di schiacciare i pulsanti tondi dopo di lui in ovali.
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = if (narrow) 72.dp else 130.dp),
                    color = colors.ink,
                    onChange = { player.setVolume((it * 100).toInt()) },
                    onChangeFinished = { onVolumeChange((it * 100).toInt()) },
                    // La rotella sopra il cursore: cinque punti per scatto.
                    onScroll = { dy ->
                        val v = (volume - (dy * 5).toInt()).coerceIn(0, 100)
                        player.setVolume(v)
                        onVolumeChange(v)
                    },
                )
                Spacer(Modifier.width(14.dp))
                if (beam != null) {
                    val beamState by beam.state.collectAsState()
                    if (beamState is xaos.desktop.beam.BeamState.Running) {
                        DevicesButton(beam, player)
                        Spacer(Modifier.width(6.dp))
                    }
                }
                ModeButton(XaosIcons.Mic, if (lyricsOpen) "Chiudi il testo" else "Testo", lyricsOpen, onClick = onToggleLyrics)
                Spacer(Modifier.width(6.dp))
                CircleIconButton(XaosIcons.MiniPlayer, "Miniplayer", onOpenMini, size = 34.dp, outlined = false)
                Spacer(Modifier.width(6.dp))
                CircleIconButton(XaosIcons.Fullscreen, "Schermo intero", onOpenFullscreen, size = 34.dp)
            }
        }
    }
    }
}

/** Casuale e ripeti: icona piena e punto d'accento sotto quando sono attivi. */
@Composable
internal fun ModeButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    onClick: () -> Unit,
) {
    val colors = Xaos.colors
    val tint by animateColorAsState(if (active) colors.ink else colors.inkTertiary, label = "mode")
    Box(Modifier.size(size).clip(CircleShape).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size / 2))
        if (active) AccentDot(size = 4.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
    }
}

/**
 * Il brano che sta suonando il telefono, al posto di quello del PC: copertina
 * (se il PC ha lo stesso brano), titolo, artista e il nome del telefono nel
 * colore d'accento, come il "In riproduzione su…" di Spotify.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.RemoteNowPlaying(phone: xaos.desktop.beam.ConnectedPhone, beam: xaos.desktop.beam.BeamServer?) {
    val colors = Xaos.colors
    val track = phone.now.track ?: return
    val local = track.pcId?.let { beam?.trackFor(it) }
    if (local != null) {
        ArtworkImage(local, size = 58.dp, corner = 10.dp)
    } else {
        Box(
            Modifier.size(58.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(colors.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(XaosIcons.MusicNote, null, tint = colors.inkTertiary, modifier = Modifier.size(22.dp))
        }
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f, fill = false)) {
        Text(track.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(XaosIcons.Phone, null, tint = colors.accentInk, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                "SUL TELEFONO · ${phone.name.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.width(12.dp))
}
