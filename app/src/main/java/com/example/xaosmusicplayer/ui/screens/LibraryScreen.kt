package com.example.xaosmusicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.Album
import com.example.xaosmusicplayer.data.Artist
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.playback.LibraryState
import com.example.xaosmusicplayer.playback.LibraryTab
import com.example.xaosmusicplayer.ui.components.AccentDot
import com.example.xaosmusicplayer.ui.components.AlbumCell
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.components.DotSpinner
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.components.PillButton
import com.example.xaosmusicplayer.ui.components.SongRow
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos

/** Voci del menu in alto a destra della libreria. */
enum class LibraryMenuAction {
    PLAYLISTS, FAVORITES, EQUALIZER, SLEEP_TIMER, THEME, CUSTOM_THEME, RESCAN, UPDATE_ENGINE
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    tab: LibraryTab,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    currentSongId: Long?,
    onTabChange: (LibraryTab) -> Unit,
    onSongClick: (Int) -> Unit,
    onSongMenu: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onMenuAction: (LibraryMenuAction) -> Unit,
    onRequestPermission: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(
            title = "LIBRERIA",
            caption = "${songs.size} BRANI · ${albums.size} ALBUM · ${artists.size} ARTISTI",
            trailing = { LibraryMenu(isDark = isDark, onMenuAction = onMenuAction) },
        )
        LibraryTabs(
            selected = tab,
            counts = mapOf(
                LibraryTab.TITLE to songs.size,
                LibraryTab.ARTIST to artists.size,
                LibraryTab.ALBUM to albums.size,
            ),
            onSelect = onTabChange,
        )

        when (state) {
            LibraryState.NEEDS_PERMISSION -> PermissionPrompt(onRequestPermission)
            LibraryState.LOADING -> LoadingState()
            LibraryState.READY -> when (tab) {
                LibraryTab.TITLE -> SongList(songs, currentSongId, onSongClick, onSongMenu)
                LibraryTab.ARTIST -> ArtistList(artists, onArtistClick)
                LibraryTab.ALBUM -> AlbumGrid(albums, onAlbumClick, onAlbumLongClick)
            }
        }
    }
}

@Composable
private fun LibraryMenu(isDark: Boolean, onMenuAction: (LibraryMenuAction) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = Xaos.colors

    Box {
        CircleIconButton(
            icon = XaosIcons.Sort,
            contentDescription = "Menu",
            onClick = { menuOpen = true },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        ) {
            LibraryMenuAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label(isDark),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.ink,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onMenuAction(action)
                    },
                )
            }
        }
    }
}

private fun LibraryMenuAction.label(isDark: Boolean): String = when (this) {
    LibraryMenuAction.PLAYLISTS -> "PLAYLIST"
    LibraryMenuAction.FAVORITES -> "PREFERITI"
    LibraryMenuAction.EQUALIZER -> "EQUALIZZATORE"
    LibraryMenuAction.SLEEP_TIMER -> "SLEEP TIMER"
    LibraryMenuAction.THEME -> if (isDark) "TEMA CHIARO" else "TEMA SCURO"
    LibraryMenuAction.CUSTOM_THEME -> "PERSONALIZZA TEMA"
    LibraryMenuAction.RESCAN -> "RISCANSIONA"
    LibraryMenuAction.UPDATE_ENGINE -> "AGGIORNA MOTORE DOWNLOAD"
}

private val LibraryTab.label: String
    get() = when (this) {
        LibraryTab.TITLE -> "BRANI"
        LibraryTab.ARTIST -> "ARTISTI"
        LibraryTab.ALBUM -> "ALBUM"
    }

/**
 * Selettore a pillola, come i filtri del sito Nothing: una capsula filettata
 * con dentro i tre segmenti, e quello attivo pieno in inchiostro.
 */
@Composable
private fun LibraryTabs(
    selected: LibraryTab,
    counts: Map<LibraryTab, Int>,
    onSelect: (LibraryTab) -> Unit,
) {
    val colors = Xaos.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(colors.card, shape)
            .border(1.dp, colors.line, shape)
            .padding(4.dp),
    ) {
        LibraryTab.entries.forEach { entry ->
            val isSelected = entry == selected
            val fill by animateColorAsState(
                if (isSelected) colors.ink else Color.Transparent,
                label = "tab-fill",
            )
            val text by animateColorAsState(
                if (isSelected) colors.background else colors.inkSecondary,
                label = "tab-text",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(fill, shape)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    AccentDot(size = 5.dp)
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = text,
                )
                counts[entry]?.let { count ->
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = text.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun SongList(
    songs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Int) -> Unit,
    onSongMenu: (Song) -> Unit,
) {
    if (songs.isEmpty()) {
        EmptyState("Nessun brano")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                isCurrent = song.id == currentSongId,
                onClick = { onSongClick(index) },
                onMenuClick = { onSongMenu(song) },
            )
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onClick: (Artist) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState("Nessun artista")
        return
    }
    val colors = Xaos.colors
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(artist) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = artist.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "[${artist.songCount}·${artist.albumCount}]",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkTertiary,
                )
            }
            Hairline(Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    onClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyState("Nessun album")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCell(
                title = album.title,
                // L'artista, non l'etichetta "ALBUM": in una griglia di album
                // dire che sono album non aggiunge niente.
                subtitle = album.artist,
                artworkUri = album.artworkUri,
                onClick = { onClick(album) },
                onLongClick = { onLongClick(album) },
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Xaos.colors.inkTertiary,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DotSpinner(size = 40.dp)
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    val colors = Xaos.colors
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ACCESSO ALLA MUSICA",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
            )
            Text(
                text = "Xaos ha bisogno del permesso di leggere i file audio " +
                    "del dispositivo per costruire la tua libreria.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
            )
            PillButton(text = "CONCEDI PERMESSO", onClick = onRequest, filled = true)
        }
    }
}
