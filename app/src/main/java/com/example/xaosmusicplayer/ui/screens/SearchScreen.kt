package com.example.xaosmusicplayer.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.xaosmusicplayer.data.Album
import com.example.xaosmusicplayer.data.Artist
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.online.DownloadState
import com.example.xaosmusicplayer.online.OnlineSearchState
import com.example.xaosmusicplayer.online.OnlineTrack
import com.example.xaosmusicplayer.ui.components.Artwork
import com.example.xaosmusicplayer.ui.components.SongRow
import com.example.xaosmusicplayer.ui.components.formatDuration
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.components.DotProgressLine
import com.example.xaosmusicplayer.ui.components.DotSpinner
import com.example.xaosmusicplayer.ui.components.PillButton
import com.example.xaosmusicplayer.ui.components.SectionHeader
import com.example.xaosmusicplayer.ui.components.nothingCard
import com.example.xaosmusicplayer.ui.theme.Xaos

/**
 * Ricerca unica su libreria e rete.
 *
 * I risultati locali compaiono mentre si digita; quelli online vanno chiesti
 * esplicitamente, perché ogni ricerca fa partire yt-dlp e una richiesta di rete.
 */
@Composable
fun SearchScreen(
    query: String,
    localResults: List<Song>,
    artistResults: List<Artist>,
    albumResults: List<Album>,
    currentSongId: Long?,
    onlineState: OnlineSearchState,
    downloads: Map<String, DownloadState>,
    onQueryChange: (String) -> Unit,
    onPlayLocal: (Int) -> Unit,
    onSongMenu: (Song) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSearchOnline: () -> Unit,
    onDownload: (OnlineTrack) -> Unit,
    onCancelDownload: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(
            title = "CERCA",
            caption = "LIBRERIA · RETE",
            trailing = {
                CircleIconButton(
                    icon = XaosIcons.Settings,
                    contentDescription = "Impostazioni",
                    onClick = onOpenSettings,
                )
            },
        )

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = {
                keyboard?.hide()
                onSearchOnline()
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (query.isBlank()) {
                item { Hint() }
                return@LazyColumn
            }

            val nothingLocal = localResults.isEmpty() &&
                artistResults.isEmpty() && albumResults.isEmpty()

            if (nothingLocal) {
                item { SectionLabel("NIENTE IN LIBRERIA") }
            }

            // Artisti e album per primi: sono pochi, e sotto una lista di brani
            // lunga non li troverebbe nessuno.
            if (artistResults.isNotEmpty()) {
                item { SectionLabel("ARTISTI [${artistResults.size}]") }
                items(artistResults, key = { "artist-${it.name}" }) { artist ->
                    ArtistResultRow(artist) { onArtistClick(artist) }
                }
            }

            if (albumResults.isNotEmpty()) {
                item { SectionLabel("ALBUM [${albumResults.size}]") }
                items(albumResults, key = { "album-${it.id}" }) { album ->
                    AlbumResultRow(album) { onAlbumClick(album) }
                }
            }

            if (localResults.isNotEmpty()) {
                item { SectionLabel("BRANI [${localResults.size}]") }
                itemsIndexed(localResults, key = { _, song -> "local-${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { onPlayLocal(index) },
                        onMenuClick = { onSongMenu(song) },
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
            item { SectionLabel("ONLINE") }

            when (onlineState) {
                OnlineSearchState.Idle -> item {
                    OnlineTrigger(
                        onClick = {
                            keyboard?.hide()
                            onSearchOnline()
                        },
                    )
                }

                OnlineSearchState.Preparing -> item { OnlineBusy("PREPARAZIONE DI YT-DLP...") }
                OnlineSearchState.Searching -> item { OnlineBusy("RICERCA IN CORSO...") }

                is OnlineSearchState.Failed -> item {
                    OnlineFailure(onlineState.message) {
                        keyboard?.hide()
                        onSearchOnline()
                    }
                }

                is OnlineSearchState.Results -> {
                    if (onlineState.tracks.isEmpty()) {
                        item { SectionLabel("NESSUN RISULTATO ONLINE") }
                    } else {
                        itemsIndexed(
                            onlineState.tracks,
                            key = { _, track -> "online-${track.id}" },
                        ) { _, track ->
                            OnlineRow(
                                track = track,
                                downloadState = downloads[track.id],
                                onDownload = { onDownload(track) },
                                onCancel = { onCancelDownload(track.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                text = "Brani, album, artisti...",
                style = MaterialTheme.typography.bodyLarge,
                color = Xaos.colors.inkSecondary,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = XaosIcons.Search,
                contentDescription = null,
                tint = Xaos.colors.inkSecondary,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = XaosIcons.Close,
                        contentDescription = "Cancella ricerca",
                        tint = Xaos.colors.inkSecondary,
                    )
                }
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(50),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Invio dalla tastiera = cerca online: in libreria si filtra già mentre si digita.
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Xaos.colors.card,
            unfocusedContainerColor = Xaos.colors.card,
            focusedBorderColor = Xaos.colors.accent,
            unfocusedBorderColor = Xaos.colors.line,
            focusedTextColor = Xaos.colors.ink,
            unfocusedTextColor = Xaos.colors.ink,
            cursorColor = Xaos.colors.accentInk,
        ),
    )
}

@Composable
private fun ArtistResultRow(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = artist.name.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = Xaos.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "[${artist.songCount}] BRANI · [${artist.albumCount}] ALBUM",
            style = MaterialTheme.typography.labelMedium,
            color = Xaos.colors.inkTertiary,
        )
    }
}

@Composable
private fun AlbumResultRow(album: Album, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(uri = album.artworkUri, modifier = Modifier.size(52.dp))
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = album.title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Xaos.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Xaos.colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${album.songCount}",
            style = MaterialTheme.typography.labelMedium,
            color = Xaos.colors.inkSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    SectionHeader(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun OnlineTrigger(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .nothingCard()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Xaos.colors.accent, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = XaosIcons.Search,
                contentDescription = null,
                tint = Xaos.colors.onAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "CERCA IN RETE",
                style = MaterialTheme.typography.titleMedium,
                color = Xaos.colors.ink,
            )
            Text(
                text = "Scarica il brano e aggiungilo alla libreria",
                style = MaterialTheme.typography.labelMedium,
                color = Xaos.colors.inkSecondary,
            )
        }
    }
}

@Composable
private fun OnlineBusy(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotSpinner(size = 22.dp)
        Spacer(Modifier.size(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Xaos.colors.inkSecondary,
        )
    }
}

@Composable
private fun OnlineFailure(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Xaos.colors.inkSecondary,
        )
        PillButton(text = "RIPROVA", icon = XaosIcons.Repeat, onClick = onRetry)
    }
}

@Composable
private fun OnlineRow(
    track: OnlineTrack,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = downloadState == null, onClick = onDownload)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(uri = track.thumbnailUrl?.toUri(), modifier = Modifier.size(52.dp))

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = track.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Xaos.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.uploader,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (track.durationMs > 0) {
                Text(
                    text = formatDuration(track.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Xaos.colors.inkSecondary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            DownloadControl(downloadState, onDownload, onCancel)
        }

        (downloadState as? DownloadState.Running)?.let { running ->
            DotProgressLine(
                progress = running.progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(horizontal = 16.dp),
            )
        }

        // Il motivo del fallimento va scritto, non lasciato all'icona: senza
        // testo un download fallito è indistinguibile da uno che non parte.
        (downloadState as? DownloadState.Failed)?.let { failed ->
            Text(
                text = failed.message,
                style = MaterialTheme.typography.labelMedium,
                color = Xaos.colors.accentInk,
                modifier = Modifier.padding(start = 80.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun DownloadControl(
    state: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        null -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = XaosIcons.MusicNote,
                contentDescription = "Scarica",
                tint = Xaos.colors.accentInk,
                modifier = Modifier.size(20.dp),
            )
        }

        DownloadState.Queued -> Box(modifier = Modifier.size(48.dp), Alignment.Center) {
            DotSpinner(size = 22.dp)
        }

        is DownloadState.Running -> IconButton(onClick = onCancel) {
            Icon(
                imageVector = XaosIcons.Close,
                contentDescription = "Annulla download",
                tint = Xaos.colors.inkSecondary,
                modifier = Modifier.size(18.dp),
            )
        }

        DownloadState.Completed -> Icon(
            imageVector = XaosIcons.Check,
            contentDescription = "Scaricato",
            tint = Xaos.colors.accentInk,
            modifier = Modifier.padding(14.dp).size(20.dp),
        )

        is DownloadState.Failed -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = XaosIcons.Repeat,
                contentDescription = "Riprova: ${state.message}",
                tint = Xaos.colors.inkSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Hint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "CERCA OVUNQUE",
            style = MaterialTheme.typography.titleMedium,
            color = Xaos.colors.inkSecondary,
        )
        Text(
            text = "Prima nella tua libreria, poi in rete se non basta.",
            style = MaterialTheme.typography.bodyMedium,
            color = Xaos.colors.inkSecondary,
        )
    }
}
