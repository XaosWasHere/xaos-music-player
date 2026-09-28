package com.example.xaosmusicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.example.xaosmusicplayer.data.GlowMode
import com.example.xaosmusicplayer.ui.components.Artwork
import com.example.xaosmusicplayer.ui.components.SectionHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.Album
import com.example.xaosmusicplayer.data.Playlist
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.playback.SleepTimer
import com.example.xaosmusicplayer.ui.components.formatDuration
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.theme.Xaos

/** Menu contestuale di un brano. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Xaos.colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = song.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Xaos.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.accentInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Divider()

            SheetAction(XaosIcons.Next, "RIPRODUCI DOPO") {
                onPlayNext(); onDismiss()
            }
            SheetAction(XaosIcons.Queue, "AGGIUNGI ALLA CODA") {
                onAddToQueue(); onDismiss()
            }
            SheetAction(
                icon = if (isFavorite) XaosIcons.Favorite else XaosIcons.FavoriteBorder,
                label = if (isFavorite) "RIMUOVI DAI PREFERITI" else "AGGIUNGI AI PREFERITI",
                tint = if (isFavorite) Xaos.colors.accentInk else Xaos.colors.ink,
            ) {
                onToggleFavorite(); onDismiss()
            }
            SheetAction(XaosIcons.PlaylistAdd, "AGGIUNGI A PLAYLIST") {
                onAddToPlaylist(); onDismiss()
            }
            SheetAction(XaosIcons.Edit, "MODIFICA") {
                onEdit(); onDismiss()
            }
            Divider()
            // In rosso perché è l'unica voce che cancella un file dal telefono.
            SheetAction(XaosIcons.Delete, "ELIMINA", tint = Xaos.colors.accentInk) {
                onDelete(); onDismiss()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Menu contestuale di un album. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumOptionsSheet(
    album: Album,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onEdit: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Xaos.colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = album.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Xaos.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${album.artist} · ${album.songCount} BRANI",
                    style = MaterialTheme.typography.labelMedium,
                    color = Xaos.colors.accentInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Divider()

            SheetAction(XaosIcons.Play, "RIPRODUCI") { onPlay(); onDismiss() }
            SheetAction(XaosIcons.Shuffle, "RIPRODUZIONE CASUALE") { onShuffle(); onDismiss() }
            SheetAction(XaosIcons.Queue, "AGGIUNGI ALLA CODA") { onAddToQueue(); onDismiss() }
            SheetAction(XaosIcons.Edit, "MODIFICA") { onEdit(); onDismiss() }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Scelta della playlist di destinazione, con creazione al volo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Xaos.colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = "AGGIUNGI A PLAYLIST",
                style = MaterialTheme.typography.titleMedium,
                color = Xaos.colors.ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Divider()

            SheetAction(XaosIcons.Add, "NUOVA PLAYLIST", tint = Xaos.colors.accentInk) {
                showCreate = true
            }

            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    SheetAction(
                        icon = XaosIcons.Queue,
                        label = playlist.name.uppercase(),
                        subtitle = "${playlist.songIds.size} brani",
                    ) {
                        onSelect(playlist); onDismiss()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showCreate) {
        NameDialog(
            title = "NUOVA PLAYLIST",
            initialValue = "",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                onCreateAndAdd(name)
                onDismiss()
            },
        )
    }
}

/** Impostazione dello sleep timer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    onDismiss: () -> Unit,
    onStart: (minutes: Int, finishTrack: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var finishTrack by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Xaos.colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = "SLEEP TIMER",
                style = MaterialTheme.typography.titleMedium,
                color = Xaos.colors.ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            if (remainingMs != null) {
                Text(
                    text = "ATTIVO · ${formatDuration(remainingMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Xaos.colors.accentInk,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Divider()

            // Interruttore: fermarsi subito allo scadere o lasciar finire il brano.
            SheetAction(
                icon = if (finishTrack) XaosIcons.Check else XaosIcons.Close,
                label = "COMPLETA IL BRANO IN CORSO",
                tint = if (finishTrack) Xaos.colors.accentInk else Xaos.colors.inkTertiary,
            ) {
                finishTrack = !finishTrack
            }
            Divider()

            SleepTimer.PRESETS_MINUTES.forEach { minutes ->
                SheetAction(XaosIcons.Timer, "$minutes MINUTI") {
                    onStart(minutes, finishTrack)
                    onDismiss()
                }
            }

            if (remainingMs != null) {
                Divider()
                SheetAction(XaosIcons.Close, "ANNULLA TIMER", tint = Xaos.colors.accentInk) {
                    onCancel(); onDismiss()
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Il menu dei tre punti del player: tutto ciò che non serve a ogni ascolto.
 *
 * Lo sfondo si sceglie direttamente fra le quattro modalità invece di ciclarle
 * con un tasto: vedere le alternative una accanto all'altra è più chiaro che
 * indovinare cosa arriva al prossimo tocco.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsSheet(
    song: Song,
    glowMode: GlowMode,
    sleepTimerRemainingMs: Long?,
    onDismiss: () -> Unit,
    onSetGlowMode: (GlowMode) -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Xaos.colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(uri = song.artworkUri, cornerRadius = 10, modifier = Modifier.size(48.dp))
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        text = song.title.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Xaos.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = Xaos.colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Divider()

            SectionHeader(
                text = "SFONDO",
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            )
            GlowModeSelector(
                selected = glowMode,
                onSelect = onSetGlowMode,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(14.dp))
            Divider()

            SheetAction(XaosIcons.PlaylistAdd, "AGGIUNGI A PLAYLIST") {
                onDismiss(); onAddToPlaylist()
            }
            SheetAction(XaosIcons.Library, "VAI ALL'ALBUM", subtitle = song.album) {
                onDismiss(); onGoToAlbum()
            }
            SheetAction(XaosIcons.Person, "VAI ALL'ARTISTA", subtitle = song.albumArtist) {
                onDismiss(); onGoToArtist()
            }
            Divider()
            SheetAction(
                icon = XaosIcons.Timer,
                label = "SLEEP TIMER",
                subtitle = sleepTimerRemainingMs?.let { "Attivo · ${formatDuration(it)}" },
                tint = if (sleepTimerRemainingMs != null) Xaos.colors.accentInk else Xaos.colors.ink,
            ) {
                onDismiss(); onOpenSleepTimer()
            }
            SheetAction(XaosIcons.Equalizer, "EQUALIZZATORE") {
                onDismiss(); onOpenEqualizer()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Le quattro modalità dello sfondo, in una capsula come i tab della libreria. */
@Composable
private fun GlowModeSelector(
    selected: GlowMode,
    onSelect: (GlowMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Xaos.colors
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card, shape)
            .border(1.dp, colors.line, shape)
            .padding(4.dp),
    ) {
        GlowMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val fill by animateColorAsState(
                if (isSelected) colors.ink else Color.Transparent,
                label = "glow-fill",
            )
            val tint by animateColorAsState(
                if (isSelected) colors.background else colors.inkSecondary,
                label = "glow-tint",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(fill)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = mode.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = mode.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 1,
                )
            }
        }
    }
}

private val GlowMode.icon: ImageVector
    get() = when (this) {
        GlowMode.ANIMATED -> XaosIcons.GlowAnimated
        GlowMode.STATIC -> XaosIcons.GlowStatic
        GlowMode.ARTWORK -> XaosIcons.GlowArtwork
        GlowMode.OFF -> XaosIcons.GlowOff
    }

/** Il nome breve, per stare in un quarto di larghezza. */
private val GlowMode.shortLabel: String
    get() = when (this) {
        GlowMode.ANIMATED -> "REATTIVA"
        GlowMode.STATIC -> "FISSA"
        GlowMode.ARTWORK -> "COPERTINA"
        GlowMode.OFF -> "SPENTA"
    }

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    tint: Color = Xaos.colors.ink,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Xaos.colors.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Hairline(Modifier.padding(horizontal = 20.dp))
}
