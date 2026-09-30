package xaos.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
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
import xaos.desktop.library.LibrarySnapshot
import xaos.desktop.library.Track
import xaos.desktop.library.UserData
import xaos.desktop.library.UserDataState
import xaos.desktop.player.Player
import xaos.desktop.theme.DotText
import xaos.desktop.theme.Xaos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ------------------------------------------------------------------ Preferiti

@Composable
fun FavoritesScreen(snapshot: LibrarySnapshot, data: UserDataState, player: Player) {
    val current by player.current.collectAsState()
    // I più recenti in cima, come sul telefono.
    val tracks = remember(snapshot, data.favorites) {
        data.favorites.asReversed().mapNotNull { snapshot.byPath[it] }.distinctBy { it.path }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
        item {
            ScreenTitle(
                "PREFERITI",
                caption = "[${tracks.size}] BRANI · ${formatDuration(tracks.sumOf { it.durationMs })} · SI SINCRONIZZANO COL TELEFONO",
                trailing = if (tracks.isNotEmpty()) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("RIPRODUCI", onClick = { player.play(tracks, 0) }, icon = XaosIcons.Play, filled = true)
                            PillButton("CASUALE", onClick = { player.play(tracks.shuffled(), 0) }, icon = XaosIcons.Shuffle)
                        }
                    }
                } else null,
            )
        }
        if (tracks.isEmpty()) {
            item { EmptyHint("NESSUN PREFERITO", "Tocca il cuore accanto a un brano: lo ritrovi qui, e sul telefono.") }
        }
        if (tracks.isNotEmpty()) item { TrackHeader(showAlbum = true) }
        itemsIndexed(tracks, key = { _, t -> t.path }) { index, track ->
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

// ------------------------------------------------------------------ Playlist

@Composable
fun PlaylistsScreen(snapshot: LibrarySnapshot, data: UserDataState, userData: UserData, onOpen: (String) -> Unit) {
    val colors = Xaos.colors
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = "Nuova playlist",
            initial = "",
            confirm = "CREA",
            onConfirm = { name -> creating = false; onOpen(userData.createPlaylist(name)) },
            onDismiss = { creating = false },
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
        item {
            ScreenTitle(
                "PLAYLIST",
                caption = "[${data.playlists.size}] PLAYLIST · SI SINCRONIZZANO COL TELEFONO",
                trailing = { PillButton("NUOVA PLAYLIST", onClick = { creating = true }, icon = XaosIcons.Add, filled = true) },
            )
        }
        if (data.playlists.isEmpty()) {
            item { EmptyHint("NESSUNA PLAYLIST", "Creane una da qui, o dal menu di un brano: \"Aggiungi a playlist\".") }
        }
        itemsIndexed(data.playlists, key = { _, p -> p.id }) { _, pl ->
            val tracks = pl.paths.mapNotNull { snapshot.byPath[it] }
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverRow()
                    .pressable { onOpen(pl.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(tracks.firstOrNull(), size = 48.dp, corner = 8.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(pl.name, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "[${tracks.size}] BRANI · ${formatDuration(tracks.sumOf { it.durationMs })}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkTertiary,
                    )
                }
                Icon(XaosIcons.PlaylistMusic, null, tint = colors.inkTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    id: String,
    snapshot: LibrarySnapshot,
    data: UserDataState,
    userData: UserData,
    player: Player,
    onDeleted: () -> Unit,
) {
    val colors = Xaos.colors
    val pl = data.playlists.firstOrNull { it.id == id }
    if (pl == null) {
        EmptyMessage("PLAYLIST NON TROVATA")
        return
    }
    val current by player.current.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Le voci tengono il loro indice nella playlist: i brani che mancano dal
    // PC (stanno solo sul telefono) non si vedono ma non si perdono.
    val entries = pl.paths.mapIndexedNotNull { i, p -> snapshot.byPath[p]?.let { i to it } }
    val tracks = entries.map { it.second }

    if (renaming) {
        NameDialog("Rinomina playlist", pl.name, "SALVA", { userData.renamePlaylist(id, it); renaming = false }, { renaming = false })
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = colors.surface,
            title = { Text("Eliminare \"${pl.name}\"?", style = MaterialTheme.typography.titleLarge, color = colors.ink) },
            text = {
                Text(
                    "La playlist sparisce anche dal telefono alla prossima sincronizzazione. I brani restano dove sono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = { PillButton("ELIMINA", onClick = { deleting = false; userData.deletePlaylist(id); onDeleted() }, filled = true) },
            dismissButton = { PillButton("ANNULLA", onClick = { deleting = false }) },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
                ArtworkImage(tracks.firstOrNull(), size = 200.dp, corner = 20.dp)
                Spacer(Modifier.width(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("PLAYLIST")
                    Text(pl.name, style = MaterialTheme.typography.headlineMedium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "[${tracks.size}] BRANI · ${formatDuration(tracks.sumOf { it.durationMs })}" +
                            if (pl.paths.size > tracks.size) " · [${pl.paths.size - tracks.size}] NON SU QUESTO PC" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("RIPRODUCI", onClick = { player.play(tracks, 0) }, icon = XaosIcons.Play, filled = true, enabled = tracks.isNotEmpty())
                        PillButton("CASUALE", onClick = { player.play(tracks.shuffled(), 0) }, icon = XaosIcons.Shuffle, enabled = tracks.isNotEmpty())
                        PillButton("RINOMINA", onClick = { renaming = true }, icon = XaosIcons.Edit)
                        PillButton("ELIMINA", onClick = { deleting = true }, icon = XaosIcons.Delete)
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            item { EmptyHint("PLAYLIST VUOTA", "Aggiungi brani dal loro menu: \"Aggiungi a playlist\".") }
        } else {
            item { TrackHeader(showAlbum = true) }
        }
        itemsIndexed(entries, key = { i, e -> "${e.first}-${e.second.path}" }) { index, (position, track) ->
            TrackRow(
                number = index + 1,
                track = track,
                isCurrent = track.path == current?.path,
                showAlbum = true,
                showArtwork = true,
                onClick = { player.play(tracks, index) },
                extraMenu = listOfNotNull(
                    Triple(XaosIcons.Close, "TOGLI DALLA PLAYLIST") { userData.removeFromPlaylist(id, position) },
                    entries.getOrNull(index - 1)?.let { (above, _) ->
                        Triple(XaosIcons.ArrowUp, "SPOSTA SU") { userData.movePlaylistItem(id, position, above) }
                    },
                    entries.getOrNull(index + 1)?.let { (below, _) ->
                        Triple(XaosIcons.ArrowDown, "SPOSTA GIÙ") { userData.movePlaylistItem(id, position, below) }
                    },
                ),
            )
        }
    }
}

/** La scelta della playlist in cui aggiungere [tracks], o di crearne una nuova. */
@Composable
fun AddToPlaylistDialog(tracks: List<Track>, data: UserDataState, userData: UserData, onDone: (String?) -> Unit) {
    val colors = Xaos.colors
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = colors.surface,
        title = {
            Text(
                if (tracks.size == 1) "Aggiungi \"${tracks.first().title}\"" else "Aggiungi ${tracks.size} brani",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (data.playlists.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(data.playlists, key = { _, p -> p.id }) { _, pl ->
                            val has = tracks.all { t -> t.allPaths.any { it in pl.paths } }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .hoverRow()
                                    .pressable { userData.addToPlaylist(pl.id, tracks); onDone(pl.name) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(XaosIcons.PlaylistMusic, null, tint = colors.inkSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(pl.name, style = MaterialTheme.typography.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
                                if (has) Text("GIÀ PRESENTE", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
                            }
                        }
                    }
                    Hairline()
                }
                XaosTextField("NUOVA PLAYLIST", name, { name = it }, placeholder = "Nome della playlist")
            }
        },
        confirmButton = {
            PillButton(
                "CREA E AGGIUNGI",
                onClick = { userData.createPlaylist(name, tracks); onDone(name.trim()) },
                filled = true,
                enabled = name.isNotBlank(),
            )
        },
        dismissButton = { PillButton("ANNULLA", onClick = { onDone(null) }) },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = Xaos.colors
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink) },
        text = { XaosTextField("NOME", name, { name = it }, placeholder = "Nome della playlist") },
        confirmButton = { PillButton(confirm, onClick = { onConfirm(name) }, filled = true, enabled = name.isNotBlank()) },
        dismissButton = { PillButton("ANNULLA", onClick = onDismiss) },
    )
}

@Composable
private fun EmptyHint(title: String, body: String) {
    val colors = Xaos.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.inkTertiary)
    }
}

// ------------------------------------------------------------------ Statistiche

enum class StatsPeriod(val label: String) { MONTH("QUESTO MESE"), YEAR("QUEST'ANNO"), ALL("SEMPRE") }

private data class Ranked<T>(val item: T, val plays: Int)

/**
 * I dati d'ascolto: quanto, cosa e chi. Sono la somma di PC e telefono: gli
 * ascolti fatti su uno arrivano sull'altro a ogni sincronizzazione.
 */
@Composable
fun StatsScreen(snapshot: LibrarySnapshot, data: UserDataState, userData: UserData, player: Player) {
    val colors = Xaos.colors
    var period by remember { mutableStateOf(StatsPeriod.YEAR) }
    var confirmClear by remember { mutableStateOf(false) }
    val stats = remember(snapshot, data.plays, period) { computeStats(snapshot, data, period) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = colors.surface,
            title = { Text("Cancellare la cronologia?", style = MaterialTheme.typography.titleLarge, color = colors.ink) },
            text = {
                Text(
                    "Tutti gli ascolti registrati spariscono, qui e — alla prossima sincronizzazione — anche sul telefono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = { PillButton("CANCELLA", onClick = { confirmClear = false; userData.clearHistory() }, filled = true) },
            dismissButton = { PillButton("ANNULLA", onClick = { confirmClear = false }) },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
        item {
            ScreenTitle(
                "ASCOLTI",
                caption = "PC E TELEFONO INSIEME · CONTA UN ASCOLTO DOPO 30 SECONDI",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatsPeriod.entries.forEach { p ->
                            PillButton(p.label, onClick = { period = p }, filled = p == period)
                        }
                    }
                },
            )
        }
        item {
            Row(Modifier.fillMaxWidth().nothingCard().padding(22.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Stat(stats.plays.toString(), "ASCOLTI")
                Stat(stats.minutes.toString(), "MINUTI")
                Stat(stats.distinctSongs.toString(), "BRANI DIVERSI")
                Stat(stats.distinctArtists.toString(), "ARTISTI")
            }
            Spacer(Modifier.height(20.dp))
        }
        if (stats.plays == 0) {
            item { EmptyHint("NESSUN ASCOLTO IN QUESTO PERIODO", "Gli ascolti si registrano da soli, qui e sul telefono.") }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    RankCard("BRANI PIÙ ASCOLTATI", Modifier.weight(1.3f)) {
                        stats.topSongs.forEachIndexed { i, r ->
                            RankRow(i, r.item.title, r.item.artist, r.plays, track = r.item) {
                                player.play(stats.topSongs.map { it.item }, i)
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        RankCard("ARTISTI", Modifier.fillMaxWidth()) {
                            stats.topArtists.forEachIndexed { i, r -> RankRow(i, r.item, null, r.plays) }
                        }
                        RankCard("ALBUM", Modifier.fillMaxWidth()) {
                            stats.topAlbums.forEachIndexed { i, r -> RankRow(i, r.item.first, r.item.second, r.plays, track = r.item.third) }
                        }
                        if (stats.topGenres.isNotEmpty()) {
                            RankCard("GENERI", Modifier.fillMaxWidth()) {
                                stats.topGenres.forEachIndexed { i, r -> RankRow(i, r.item.uppercase(), null, r.plays) }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "[${data.plays.size}] ASCOLTI REGISTRATI IN TUTTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton("CANCELLA CRONOLOGIA", onClick = { confirmClear = true }, icon = XaosIcons.Delete)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val colors = Xaos.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DotText(value, color = colors.ink, pitch = 4.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
    }
}

@Composable
private fun RankCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.nothingCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun RankRow(index: Int, title: String, subtitle: String?, plays: Int, track: Track? = null, onClick: (() -> Unit)? = null) {
    val colors = Xaos.colors
    Row(
        Modifier
            .fillMaxWidth()
            .hoverRow()
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = if (index == 0) colors.accentInk else colors.inkTertiary, modifier = Modifier.width(30.dp))
        if (track != null) {
            ArtworkImage(track, size = 34.dp, corner = 6.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("[$plays]", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
    }
}

private class Stats(
    val plays: Int,
    val minutes: Long,
    val distinctSongs: Int,
    val distinctArtists: Int,
    val topSongs: List<Ranked<Track>>,
    val topArtists: List<Ranked<String>>,
    val topAlbums: List<Ranked<Triple<String, String, Track>>>,
    val topGenres: List<Ranked<String>>,
)

private fun computeStats(snapshot: LibrarySnapshot, data: UserDataState, period: StatsPeriod): Stats {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val from = when (period) {
        StatsPeriod.MONTH -> today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        StatsPeriod.YEAR -> today.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
        StatsPeriod.ALL -> Long.MIN_VALUE
    }
    val plays = data.plays.filter { it.at >= from && it.at <= Instant.now().toEpochMilli() + 60_000 }
    val tracks = plays.mapNotNull { snapshot.byPath[it.path] }
    fun <K> rank(keys: List<K>, n: Int = TOP) = keys.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }.take(n).map { Ranked(it.key, it.value) }
    val byAlbum = tracks.groupBy { it.album.lowercase() }
    return Stats(
        plays = plays.size,
        minutes = tracks.sumOf { it.durationMs } / 60_000,
        distinctSongs = tracks.map { it.path }.distinct().size,
        distinctArtists = tracks.map { it.albumArtist }.distinct().size,
        topSongs = rank(tracks.map { it.path }).mapNotNull { r -> snapshot.byPath[r.item]?.let { Ranked(it, r.plays) } },
        topArtists = rank(tracks.map { it.albumArtist }),
        topAlbums = rank(tracks.map { it.album.lowercase() }, 5).mapNotNull { r ->
            val first = byAlbum[r.item]?.firstOrNull() ?: return@mapNotNull null
            Ranked(Triple(first.album, first.albumArtist, first), r.plays)
        },
        topGenres = rank(tracks.flatMap { t -> t.genre.orEmpty().split(',', ';', '/', '|').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct() }, 5),
    )
}

private const val TOP = 10
