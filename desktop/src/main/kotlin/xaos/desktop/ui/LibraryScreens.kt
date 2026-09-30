@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package xaos.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
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
import xaos.desktop.player.PlaySource
import xaos.desktop.online.DownloadState
import xaos.desktop.online.OnlineSearch
import xaos.desktop.online.OnlineTrack
import xaos.desktop.online.YtDlp
import androidx.compose.ui.graphics.toComposeImageBitmap
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
fun AlbumDetailScreen(album: Album?, player: Player, onOpenArtist: (String) -> Unit, onEdit: (Album) -> Unit) {
    if (album == null) {
        EmptyMessage("ALBUM NON TROVATO")
        return
    }
    val colors = Xaos.colors
    val actions = LocalTrackActions.current
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
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("RIPRODUCI", onClick = { player.play(album.tracks, 0, PlaySource(PlaySource.Kind.ALBUM, album.key, album.title)) }, icon = XaosIcons.Play, filled = true)
                        PillButton("CASUALE", onClick = { player.play(album.tracks.shuffled(), 0, PlaySource(PlaySource.Kind.ALBUM, album.key, album.title)) }, icon = XaosIcons.Shuffle)
                        PillButton("PLAYLIST", onClick = { actions?.onAddToPlaylist(album.tracks) }, icon = XaosIcons.PlaylistAdd)
                        PillButton("MODIFICA", onClick = { onEdit(album) }, icon = XaosIcons.Edit)
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
                onClick = { player.play(album.tracks, index, PlaySource(PlaySource.Kind.ALBUM, album.key, album.title)) },
            )
        }
    }
}

// ------------------------------------------------------------------ Brani

/**
 * Tutti i brani. Le intestazioni ordinano come in Esplora risorse: un clic
 * ordina per quella colonna, un altro clic inverte. "#" torna all'ordine della
 * libreria, per album.
 */
@Composable
fun SongsScreen(
    snapshot: LibrarySnapshot,
    player: Player,
    sort: xaos.desktop.SongSort,
    descending: Boolean,
    onSort: (xaos.desktop.SongSort) -> Unit,
) {
    val current by player.current.collectAsState()
    if (snapshot.tracks.isEmpty()) {
        EmptyMessage("NESSUN BRANO")
        return
    }
    val tracks = remember(snapshot.tracks, sort, descending) { sortTracks(snapshot.tracks, sort, descending) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
        item {
            ScreenTitle(
                "BRANI",
                caption = "[${snapshot.tracks.size}] BRANI · ${formatDuration(snapshot.tracks.sumOf { it.durationMs })}",
                trailing = {
                    PillButton("CASUALE", onClick = { player.play(snapshot.tracks.shuffled(), 0, PlaySource(PlaySource.Kind.SONGS, "", "Brani")) }, icon = XaosIcons.Shuffle, filled = true)
                },
            )
        }
        item { TrackHeader(showAlbum = true, sort = sort, descending = descending, onSort = onSort) }
        itemsIndexed(tracks, key = { _, t -> t.path }) { index, track ->
            TrackRow(
                number = index + 1,
                track = track,
                isCurrent = track.path == current?.path,
                showAlbum = true,
                showArtwork = true,
                onClick = { player.play(tracks, index, PlaySource(PlaySource.Kind.SONGS, "", "Brani")) },
            )
        }
    }
}

@Composable
fun TrackHeader(
    showAlbum: Boolean,
    sort: xaos.desktop.SongSort? = null,
    descending: Boolean = false,
    onSort: ((xaos.desktop.SongSort) -> Unit)? = null,
) {
    val colors = Xaos.colors
    /** Un'intestazione: cliccabile se si può ordinare, con la freccia su quella attiva. */
    @Composable
    fun Head(label: String, key: xaos.desktop.SongSort?, modifier: Modifier) {
        val active = key != null && key == sort
        Row(
            modifier.then(
                if (onSort != null && key != null) Modifier.clip(RoundedCornerShape(6.dp)).pressable { onSort(key) } else Modifier
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) colors.ink else colors.inkTertiary)
            if (active && key != xaos.desktop.SongSort.LIBRARY) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (descending) XaosIcons.ArrowDown else XaosIcons.ArrowUp,
                    if (descending) "Decrescente" else "Crescente",
                    tint = colors.accentInk,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Head("#", xaos.desktop.SongSort.LIBRARY, Modifier.width(40.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Head("TITOLO", xaos.desktop.SongSort.TITLE, Modifier)
                if (onSort != null) {
                    Text("  ·  ", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
                    Head("ARTISTA", xaos.desktop.SongSort.ARTIST, Modifier)
                }
            }
            if (showAlbum) Head("ALBUM", xaos.desktop.SongSort.ALBUM, Modifier.weight(0.7f))
            Spacer(Modifier.width(36.dp))
            Head("DURATA", xaos.desktop.SongSort.DURATION, Modifier.width(64.dp))
        }
        Hairline()
        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TrackRow(
    number: Int,
    track: Track,
    isCurrent: Boolean,
    showAlbum: Boolean,
    showArtwork: Boolean,
    onClick: () -> Unit,
    /** Voci in più per il menu, per esempio "togli dalla playlist". */
    extraMenu: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = emptyList(),
) {
    val colors = Xaos.colors
    val actions = LocalTrackActions.current
    val favorite = track.path in LocalFavorites.current
    var menuOpen by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(hover)
            .hoverRow(isCurrent)
            // Clic destro: lo stesso menu del bottone con i tre punti.
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed && actions != null) menuOpen = true
            }
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
        // Il cuore si vede sempre sui preferiti, sugli altri solo col mouse sopra.
        FavoriteButton(track, visible = hovered || menuOpen)
        Spacer(Modifier.width(6.dp))
        Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary, modifier = Modifier.width(64.dp))
        if (actions != null) {
            Box {
                // Il bottone c'è sempre (così la riga non cambia larghezza) ma si
                // vede solo col mouse sopra o a menu aperto.
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .pressable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (hovered || menuOpen) {
                        Icon(XaosIcons.More, "Altre azioni", tint = colors.inkSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                TrackMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    items = trackMenuItems(track, favorite, actions, extraMenu),
                )
            }
        }
    }
}

/** Le voci del menu di un brano: le stesse ovunque, nelle liste e nella barra del player. */
fun trackMenuItems(
    track: Track,
    favorite: Boolean,
    actions: TrackActions,
    extraMenu: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = emptyList(),
): List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = listOf(
    Triple(
        if (favorite) XaosIcons.Favorite else XaosIcons.FavoriteBorder,
        if (favorite) "TOGLI DAI PREFERITI" else "AGGIUNGI AI PREFERITI",
    ) { actions.onToggleFavorite(track) },
    Triple(XaosIcons.PlaylistAdd, "AGGIUNGI A PLAYLIST") { actions.onAddToPlaylist(listOf(track)) },
) + extraMenu + listOf(
    Triple(XaosIcons.Album, "VAI ALL'ALBUM") { actions.onGoToAlbum(track) },
    Triple(XaosIcons.Person, "VAI ALL'ARTISTA") { actions.onGoToArtist(track) },
    Triple(XaosIcons.Edit, "MODIFICA INFO") { actions.onEdit(track) },
    Triple(XaosIcons.Mic, "TESTO") { actions.onLyrics(track) },
    Triple(XaosIcons.Folder, "MOSTRA NELLA CARTELLA") { actions.onShowInFolder(track) },
)

@Composable
fun TrackMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>>,
) {
    val colors = Xaos.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.line),
    ) {
        items.forEach { (icon, label, action) ->
            DropdownMenuItem(
                text = { Text(label, style = MaterialTheme.typography.labelLarge, color = colors.ink) },
                leadingIcon = { Icon(icon, null, tint = colors.inkSecondary, modifier = Modifier.size(16.dp)) },
                onClick = { onDismiss(); action() },
            )
        }
    }
}

/**
 * L'ordine dei brani. I nomi si confrontano come li legge una persona:
 * maiuscole e accenti non contano ("Été" sta con le E). A parità, il titolo.
 */
fun sortTracks(tracks: List<Track>, sort: xaos.desktop.SongSort, descending: Boolean): List<Track> {
    val collator = java.text.Collator.getInstance(Locale.ITALIAN).apply { strength = java.text.Collator.PRIMARY }
    val byTitle = Comparator<Track> { a, b -> collator.compare(a.title, b.title) }
    val comparator: Comparator<Track>? = when (sort) {
        xaos.desktop.SongSort.LIBRARY -> null
        xaos.desktop.SongSort.TITLE -> byTitle.thenBy { it.artist.lowercase() }
        xaos.desktop.SongSort.ARTIST -> Comparator<Track> { a, b -> collator.compare(a.artist, b.artist) }.then(byTitle)
        xaos.desktop.SongSort.ALBUM -> Comparator<Track> { a, b -> collator.compare(a.album, b.album) }
            .thenBy { it.discNumber }.thenBy { it.trackNumber }.then(byTitle)
        xaos.desktop.SongSort.DURATION -> compareBy<Track> { it.durationMs }.then(byTitle)
    }
    val sorted = comparator?.let { tracks.sortedWith(it) } ?: tracks
    return if (descending) sorted.asReversed() else sorted
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
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("RIPRODUCI", onClick = { player.play(all, 0, PlaySource(PlaySource.Kind.ARTIST, artist.name, artist.name)) }, icon = XaosIcons.Play, filled = true)
                    PillButton("CASUALE", onClick = { player.play(all.shuffled(), 0, PlaySource(PlaySource.Kind.ARTIST, artist.name, artist.name)) }, icon = XaosIcons.Shuffle)
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
    ytdlp: YtDlp,
    onDownload: (OnlineTrack) -> Unit,
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

    val online by ytdlp.search.collectAsState()
    val downloads by ytdlp.downloads.collectAsState()
    val nothingLocal = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
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
        if (nothingLocal) {
            item {
                Text(
                    "Niente in libreria per \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
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
                    onClick = { player.play(tracks, index, PlaySource(PlaySource.Kind.SEARCH, query, "Ricerca: " + query)) },
                )
            }
        }
        // ---- in rete: si cerca solo a richiesta, ogni ricerca avvia yt-dlp.
        item {
            SectionHeader(
                "IN RETE",
                modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
                count = (online as? OnlineSearch.Results)?.takeIf { it.query == query }?.tracks?.size,
            )
        }
        item {
            val current = online
            when {
                ytdlp.tools.collectAsState().value.let { it.first == null || it.second == null } -> Text(
                    "Gli strumenti per scaricare non sono ancora pronti: se il messaggio resta, reinstalla Xaos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                current is OnlineSearch.Searching -> Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DotSpinner(size = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("RICERCA IN CORSO…", style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
                }
                current is OnlineSearch.Results && current.query == query -> Unit
                else -> Row(
                    Modifier.fillMaxWidth().nothingCard().pressable { ytdlp.search(query) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).background(colors.accent, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Icon(XaosIcons.Search, null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("CERCA \"${query.uppercase()}\" IN RETE", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(
                            if (current is OnlineSearch.Failed) "Non è andata: ${current.message}. Clicca per riprovare."
                            else "Scarica il brano in MP3 e aggiungilo alla libreria",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkTertiary,
                        )
                    }
                }
            }
        }
        val results = (online as? OnlineSearch.Results)?.takeIf { it.query == query }?.tracks.orEmpty()
        items(results, key = { "o-" + it.id }) { t ->
            OnlineRow(t, downloads[t.id], onDownload = { onDownload(t) }, onCancel = { ytdlp.cancel(t.id) })
        }
    }
}

@Composable
private fun OnlineRow(track: OnlineTrack, state: DownloadState?, onDownload: () -> Unit, onCancel: () -> Unit) {
    val colors = Xaos.colors
    Column {
        Row(
            Modifier.fillMaxWidth().hoverRow().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnlineThumb(track.thumbnail)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.uploader, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary, modifier = Modifier.width(64.dp))
            when (state) {
                null -> PillButton("SCARICA", onClick = onDownload, icon = XaosIcons.Download)
                DownloadState.Queued -> DotSpinner(size = 20.dp)
                is DownloadState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(XaosIcons.Close, "Annulla", onCancel, size = 30.dp)
                }
                DownloadState.Converting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    DotSpinner(size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("CONVERSIONE", style = MaterialTheme.typography.labelSmall, color = colors.inkSecondary)
                }
                is DownloadState.Completed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentDot(size = 6.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("IN LIBRERIA", style = MaterialTheme.typography.labelMedium, color = colors.ink)
                }
                is DownloadState.Failed -> PillButton("RIPROVA", onClick = onDownload, icon = XaosIcons.Repeat)
            }
        }
        (state as? DownloadState.Running)?.let {
            DotProgressLine(it.progress, modifier = Modifier.fillMaxWidth().height(6.dp).padding(start = 62.dp, end = 12.dp))
        }
        (state as? DownloadState.Failed)?.let {
            Text(it.message, style = MaterialTheme.typography.labelSmall, color = colors.accentInk, modifier = Modifier.padding(start = 62.dp, bottom = 6.dp))
        }
    }
}

/** La miniatura di un risultato online, scaricata al volo e tenuta in memoria. */
@Composable
private fun OnlineThumb(url: String?) {
    val colors = Xaos.colors
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = url?.let { u -> OnlineThumbs.load(u) }
    }
    Box(Modifier.size(width = 64.dp, height = 38.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceHigh)) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private object OnlineThumbs {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
    suspend fun load(url: String): androidx.compose.ui.graphics.ImageBitmap? = cache[url] ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val bytes = java.net.URI(url).toURL().openStream().use { it.readAllBytes() }
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()?.also { cache[url] = it }
    }
}
