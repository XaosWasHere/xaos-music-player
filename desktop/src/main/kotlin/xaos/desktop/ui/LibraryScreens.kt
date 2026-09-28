package xaos.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xaos.desktop.library.Album
import xaos.desktop.library.Artist
import xaos.desktop.library.LibrarySnapshot
import xaos.desktop.library.ScanState
import xaos.desktop.library.Track
import xaos.desktop.library.rememberArtwork
import xaos.desktop.player.Player
import xaos.desktop.theme.DotText
import xaos.desktop.theme.Xaos
import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%02d:%02d", (s % 3600) / 60, s % 60)
}

/** Copertina di un brano (o di un album, tramite il suo primo brano). */
@Composable
fun ArtworkImage(track: Track?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val colors = Xaos.colors
    val px = with(LocalDensity.current) { size.roundToPx() }
    // Le miniature si decodificano a taglie fisse: la stessa immagine a 52 e
    // a 56 pixel sarebbe due decodifiche per niente.
    val bucket = listOf(96, 192, 320, 512).firstOrNull { it >= px } ?: 640
    val image by rememberArtwork(track, bucket)
    val shape = RoundedCornerShape(corner)
    Box(
        modifier.size(size).clip(shape).background(colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().dotGrid(colors.inkTertiary.copy(alpha = 0.35f), spacing = 7.dp, radius = 0.8.dp))
            Icon(XaosIcons.MusicNote, null, tint = colors.inkTertiary, modifier = Modifier.size(size * 0.34f))
        }
    }
}

// ------------------------------------------------------------------ Album

@Composable
fun AlbumsScreen(snapshot: LibrarySnapshot, scan: ScanState, onOpen: (Album) -> Unit) {
    if (snapshot.albums.isEmpty()) {
        if (scan is ScanState.Scanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DotSpinner(size = 40.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("LETTURA DELLA LIBRERIA  ${scan.done}/${scan.total}", style = MaterialTheme.typography.labelLarge, color = Xaos.colors.inkSecondary)
                }
            }
        } else {
            EmptyMessage("NESSUN ALBUM", "Scegli la cartella della musica dalla barra a sinistra.")
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(184.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle(
                "ALBUM",
                caption = "[${snapshot.albums.size}] ALBUM · [${snapshot.artists.size}] ARTISTI",
            )
        }
        items(snapshot.albums, key = { it.key }) { album -> AlbumCard(album) { onOpen(album) } }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    val colors = Xaos.colors
    Column(
        Modifier
            .nothingCard()
            .hoverRow()
            .pressable(onClick)
            .padding(8.dp),
    ) {
        BoxWithArtwork(album.tracks.firstOrNull())
        Spacer(Modifier.height(10.dp))
        Text(
            album.title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            album.artist,
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun BoxWithArtwork(track: Track?) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        ArtworkImage(track, size = maxWidth, corner = 12.dp)
    }
}

@Composable
fun AlbumDetailScreen(album: Album?, player: Player, onOpenArtist: (String) -> Unit) {
    if (album == null) {
        EmptyMessage("ALBUM NON TROVATO")
        return
    }
    val colors = Xaos.colors
    val current by player.current.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
                ArtworkImage(album.tracks.firstOrNull(), size = 220.dp, corner = 20.dp)
                Spacer(Modifier.width(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("ALBUM")
                    Text(album.title, style = MaterialTheme.typography.headlineMedium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        album.artist.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentInk,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).pressable { onOpenArtist(album.artist) },
                    )
                    Text(
                        listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            "[${album.tracks.size}] BRANI",
                            formatDuration(album.durationMs),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("RIPRODUCI", onClick = { player.play(album.tracks, 0) }, icon = XaosIcons.Play, filled = true)
                        PillButton("CASUALE", onClick = { player.play(album.tracks.shuffled(), 0) }, icon = XaosIcons.Shuffle)
                    }
                }
            }
        }
        item { TrackHeader(showAlbum = false) }
        itemsIndexed(album.tracks, key = { _, t -> t.path }) { index, track ->
            TrackRow(
                number = if (track.trackNumber > 0) track.trackNumber else index + 1,
                track = track,
                isCurrent = track.path == current?.path,
                showAlbum = false,
                showArtwork = false,
                onClick = { player.play(album.tracks, index) },
            )
        }
    }
}

// ------------------------------------------------------------------ Brani

@Composable
fun SongsScreen(snapshot: LibrarySnapshot, player: Player) {
    val current by player.current.collectAsState()
    if (snapshot.tracks.isEmpty()) {
        EmptyMessage("NESSUN BRANO")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
        item {
            ScreenTitle(
                "BRANI",
                caption = "[${snapshot.tracks.size}] BRANI · ${formatDuration(snapshot.tracks.sumOf { it.durationMs })}",
                trailing = {
                    PillButton("CASUALE", onClick = { player.play(snapshot.tracks.shuffled(), 0) }, icon = XaosIcons.Shuffle, filled = true)
                },
            )
        }
        item { TrackHeader(showAlbum = true) }
        itemsIndexed(snapshot.tracks, key = { _, t -> t.path }) { index, track ->
            TrackRow(
                number = index + 1,
                track = track,
                isCurrent = track.path == current?.path,
                showAlbum = true,
                showArtwork = true,
                onClick = { player.play(snapshot.tracks, index) },
            )
        }
    }
}

@Composable
private fun TrackHeader(showAlbum: Boolean) {
    val colors = Xaos.colors
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, modifier = Modifier.width(40.dp))
            Text("TITOLO", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, modifier = Modifier.weight(1f))
            if (showAlbum) Text("ALBUM", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, modifier = Modifier.weight(0.7f))
            Text("DURATA", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, modifier = Modifier.width(64.dp))
        }
        Hairline()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun TrackRow(
    number: Int,
    track: Track,
    isCurrent: Boolean,
    showAlbum: Boolean,
    showArtwork: Boolean,
    onClick: () -> Unit,
) {
    val colors = Xaos.colors
    Row(
        Modifier
            .fillMaxWidth()
            .hoverRow(isCurrent)
            .pressable(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp)) {
            if (isCurrent) AccentDot(size = 8.dp, modifier = Modifier.padding(start = 2.dp))
            else Text(number.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
        }
        if (showArtwork) {
            ArtworkImage(track, size = 38.dp, corner = 6.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) colors.accentInk else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(track.artist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showAlbum) {
            Text(
                track.album,
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.7f).padding(end = 12.dp),
            )
        }
        Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary, modifier = Modifier.width(64.dp))
    }
}

// ------------------------------------------------------------------ Artisti

@Composable
fun ArtistsScreen(snapshot: LibrarySnapshot, onOpen: (Artist) -> Unit) {
    val colors = Xaos.colors
    if (snapshot.artists.isEmpty()) {
        EmptyMessage("NESSUN ARTISTA")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
        item { ScreenTitle("ARTISTI", caption = "[${snapshot.artists.size}] ARTISTI") }
        items(snapshot.artists, key = { it.name }) { artist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverRow()
                    .pressable { onOpen(artist) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(artist.albums.firstOrNull()?.tracks?.firstOrNull(), size = 44.dp, corner = 22.dp)
                Spacer(Modifier.width(14.dp))
                Text(artist.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("[${artist.albums.size}] ALBUM · [${artist.trackCount}] BRANI", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(artist: Artist?, player: Player, onOpenAlbum: (Album) -> Unit) {
    if (artist == null) {
        EmptyMessage("ARTISTA NON TROVATO")
        return
    }
    val all = remember(artist) { artist.albums.flatMap { it.tracks } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(184.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Tag("ARTISTA")
                Text(artist.name, style = MaterialTheme.typography.headlineMedium, color = Xaos.colors.ink)
                Text("[${artist.albums.size}] ALBUM · [${artist.trackCount}] BRANI", style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("RIPRODUCI", onClick = { player.play(all, 0) }, icon = XaosIcons.Play, filled = true)
                    PillButton("CASUALE", onClick = { player.play(all.shuffled(), 0) }, icon = XaosIcons.Shuffle)
                }
            }
        }
        items(artist.albums, key = { it.key }) { album -> AlbumCard(album) { onOpenAlbum(album) } }
    }
}

// ------------------------------------------------------------------ Ricerca

@Composable
fun SearchResults(
    query: String,
    snapshot: LibrarySnapshot,
    player: Player,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
) {
    val q = query.trim().lowercase()
    val artists = remember(q, snapshot) { snapshot.artists.filter { q in it.name.lowercase() }.take(8) }
    val albums = remember(q, snapshot) { snapshot.albums.filter { q in it.title.lowercase() || q in it.artist.lowercase() }.take(12) }
    val tracks = remember(q, snapshot) {
        snapshot.tracks.filter { q in it.title.lowercase() || q in it.artist.lowercase() || q in it.album.lowercase() }.take(100)
    }
    val current by player.current.collectAsState()
    val colors = Xaos.colors

    if (artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()) {
        EmptyMessage("NESSUN RISULTATO", "Niente in libreria per \"$query\".")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 28.dp)) {
        if (artists.isNotEmpty()) {
            item { SectionHeader("ARTISTI", count = artists.size, modifier = Modifier.padding(vertical = 10.dp)) }
            items(artists, key = { "a-" + it.name }) { artist ->
                Row(
                    Modifier.fillMaxWidth().hoverRow().pressable { onOpenArtist(artist) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(artist.albums.firstOrNull()?.tracks?.firstOrNull(), size = 36.dp, corner = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(artist.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.ink)
                }
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader("ALBUM", count = albums.size, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)) }
            items(albums, key = { "b-" + it.key }) { album ->
                Row(
                    Modifier.fillMaxWidth().hoverRow().pressable { onOpenAlbum(album) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(album.tracks.firstOrNull(), size = 44.dp, corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(album.title.uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
                    }
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item { SectionHeader("BRANI", count = tracks.size, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)) }
            itemsIndexed(tracks, key = { _, t -> "t-" + t.path }) { index, track ->
                TrackRow(
                    number = index + 1,
                    track = track,
                    isCurrent = track.path == current?.path,
                    showAlbum = true,
                    showArtwork = true,
                    onClick = { player.play(tracks, index) },
                )
            }
        }
    }
}
