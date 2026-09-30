package com.example.xaosmusicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.MonthlyRecap
import com.example.xaosmusicplayer.data.RankedName
import com.example.xaosmusicplayer.data.RecentEntry
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.data.YearlyRecap
import com.example.xaosmusicplayer.ui.components.AccentDot
import com.example.xaosmusicplayer.ui.components.Artwork
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.components.SectionHeader
import com.example.xaosmusicplayer.ui.components.Tag
import com.example.xaosmusicplayer.ui.components.nothingCard
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Schermata iniziale: da dove riprendere, com'è andato il mese, e due playlist
 * costruite dagli ascolti.
 *
 * Tutto qui dipende dalla cronologia: al primo avvio è normalmente vuota, e
 * ogni sezione si nasconde da sola finché non ha qualcosa da dire.
 */
@Composable
fun HomeScreen(
    recentAlbums: List<RecentEntry>,
    recap: MonthlyRecap,
    yearRecap: YearlyRecap,
    mostPlayed: List<Song>,
    leastPlayed: List<Song>,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayMostPlayed: () -> Unit,
    onPlayLeastPlayed: () -> Unit,
    onOpenMostPlayed: () -> Unit,
    onOpenLeastPlayed: () -> Unit,
    onExportRecap: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenTitle(
                title = "HOME",
                caption = rememberTodayCaption(),
                trailing = {
                    CircleIconButton(
                        icon = XaosIcons.Settings,
                        contentDescription = "Impostazioni",
                        onClick = onOpenSettings,
                    )
                },
            )
        }

        if (recentAlbums.isNotEmpty()) {
            item { HomeSection("RIPRENDI DA QUI", recentAlbums.size) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recentAlbums, key = { it.album.id }) { entry ->
                        RecentCard(entry) { onOpenAlbum(entry.album.id, entry.album.title) }
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }

        if (!recap.isEmpty) {
            item { HomeSection("QUESTO MESE") }
            item { RecapCard(recap) }
            item { Spacer(Modifier.height(28.dp)) }
        }

        if (!yearRecap.isEmpty) {
            item {
                HomeSection(
                    text = "IL TUO ${yearRecap.year}",
                    trailing = {
                        CircleIconButton(
                            icon = XaosIcons.Download,
                            contentDescription = "Salva l'immagine del riepilogo",
                            onClick = onExportRecap,
                            size = 34.dp,
                        )
                    },
                )
            }
            item { YearStatsCard(yearRecap) }
            item { Spacer(Modifier.height(10.dp)) }

            yearRecap.topSong?.let { top ->
                item {
                    HighlightCard(
                        label = "BRANO DELL'ANNO",
                        title = top.song.title,
                        subtitle = top.song.artist,
                        artworkUri = top.song.artworkUri,
                        plays = top.plays,
                        onClick = { onPlaySong(top.song) },
                    )
                }
                item { Spacer(Modifier.height(10.dp)) }
            }

            yearRecap.topAlbum?.let { top ->
                item {
                    HighlightCard(
                        label = "ALBUM DELL'ANNO",
                        title = top.album.title,
                        subtitle = top.album.artist,
                        artworkUri = top.album.artworkUri,
                        plays = top.plays,
                        onClick = { onOpenAlbum(top.album.id, top.album.title) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }

            if (yearRecap.topSongs.size > 1) {
                item { SubTitle("TOP BRANI", yearRecap.topSongs.size) }
                item {
                    RankedList {
                        yearRecap.topSongs.forEachIndexed { index, ranked ->
                            RankedRow(
                                position = index + 1,
                                title = ranked.song.title,
                                subtitle = ranked.song.artist,
                                plays = ranked.plays,
                                onClick = { onPlaySong(ranked.song) },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            if (yearRecap.topArtists.size > 1) {
                item { SubTitle("TOP ARTISTI", yearRecap.topArtists.size) }
                item {
                    RankedList {
                        yearRecap.topArtists.forEachIndexed { index, ranked ->
                            RankedRow(
                                position = index + 1,
                                title = ranked.name,
                                subtitle = null,
                                plays = ranked.plays,
                                onClick = { onOpenArtist(ranked.name) },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            // I generi arrivano dai tag dei file: su molte librerie mancano del
            // tutto, quindi la sezione compare solo se c'è qualcosa da dire.
            if (yearRecap.topGenres.isNotEmpty()) {
                item { SubTitle("GENERI", yearRecap.topGenres.size) }
                item { GenreChips(yearRecap.topGenres) }
                item { Spacer(Modifier.height(20.dp)) }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayed.isNotEmpty()) {
            item { HomeSection("I PIÙ ASCOLTATI", mostPlayed.size) }
            item {
                AutoPlaylistCard(
                    songs = mostPlayed,
                    caption = "I brani su cui torni sempre",
                    onPlay = onPlayMostPlayed,
                    onOpen = onOpenMostPlayed,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (leastPlayed.isNotEmpty()) {
            item { HomeSection("DA RISCOPRIRE", leastPlayed.size) }
            item {
                AutoPlaylistCard(
                    songs = leastPlayed,
                    caption = "Quello che ascolti di meno",
                    onPlay = onPlayLeastPlayed,
                    onOpen = onOpenLeastPlayed,
                )
            }
        }

        val nothingToShow = recentAlbums.isEmpty() && recap.isEmpty &&
            mostPlayed.isEmpty() && leastPlayed.isEmpty()
        if (nothingToShow) {
            item { EmptyHome() }
        }
    }
}

/**
 * Titolo di una sezione principale: la parola in matrice di punti, sotto una
 * riga tecnica, a destra le azioni rotonde.
 */
@Composable
fun ScreenTitle(
    title: String,
    caption: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = Xaos.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = colors.ink,
            )
            if (caption != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkTertiary,
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(top = 4.dp)) { trailing() }
        }
    }
}

/** "LUNEDÌ 28.09": il timbro della schermata, come la data su un display di servizio. */
@Composable
private fun rememberTodayCaption(): String = remember {
    SimpleDateFormat("EEEE dd.MM", Locale.ITALIAN).format(Date()).uppercase(Locale.ITALIAN)
}

@Composable
private fun HomeSection(
    text: String,
    count: Int? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    SectionHeader(
        text = text,
        count = count,
        trailing = trailing,
        modifier = Modifier
            .padding(start = 20.dp, end = 16.dp, bottom = 12.dp)
            .height(34.dp),
    )
}

@Composable
private fun SubTitle(text: String, count: Int) {
    val colors = Xaos.colors
    Row(
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        Text(text = "[$count]", style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
    }
}

@Composable
private fun YearStatsCard(recap: YearlyRecap) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .nothingCard()
            .padding(18.dp),
    ) {
        Stat(value = recap.minutesListened.toString(), label = "MINUTI")
        Stat(value = recap.playCount.toString(), label = "ASCOLTI")
        Stat(value = recap.distinctSongs.toString(), label = "BRANI")
    }
}

/** Il pezzo forte di una categoria: copertina grande e conteggio in evidenza. */
@Composable
private fun HighlightCard(
    label: String,
    title: String,
    subtitle: String,
    artworkUri: android.net.Uri?,
    plays: Int,
    onClick: () -> Unit,
) {
    val colors = Xaos.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .nothingCard()
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(uri = artworkUri, cornerRadius = 12, modifier = Modifier.size(76.dp))
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tag(label)
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 6.dp),
        ) {
            Text(
                text = plays.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.ink,
            )
            Text(
                text = if (plays == 1) "ASCOLTO" else "ASCOLTI",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
            )
        }
    }
}

/** Le classifiche stanno in una card sola, righe separate da filetti. */
@Composable
private fun RankedList(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .nothingCard(),
    ) {
        content()
    }
}

/** Riga di classifica: posizione a matrice di punti, poi il contenuto, poi il conteggio. */
@Composable
private fun RankedRow(
    position: Int,
    title: String,
    subtitle: String?,
    plays: Int,
    onClick: () -> Unit,
) {
    val colors = Xaos.colors
    if (position > 1) Hairline(Modifier.padding(horizontal = 14.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.toString().padStart(2, '0'),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
            ),
            // Il primo posto è l'unico nel colore d'accento.
            color = if (position == 1) colors.accentInk else colors.inkTertiary,
            modifier = Modifier.width(34.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "[$plays]",
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun GenreChips(genres: List<RankedName>) {
    val colors = Xaos.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEachIndexed { index, genre ->
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(colors.card, shape)
                    .border(1.dp, colors.line, shape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == 0) AccentDot(size = 5.dp)
                Text(
                    text = genre.name.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                    maxLines = 1,
                )
                Text(
                    text = genre.plays.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inkTertiary,
                )
            }
        }
    }
}

@Composable
private fun RecentCard(entry: RecentEntry, onClick: () -> Unit) {
    val colors = Xaos.colors
    Column(
        modifier = Modifier
            .width(150.dp)
            .nothingCard()
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Artwork(
            uri = entry.album.artworkUri,
            cornerRadius = 14,
            modifier = Modifier.size(138.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.album.title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = entry.album.artist,
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun RecapCard(recap: MonthlyRecap) {
    val colors = Xaos.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .nothingCard()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat(value = recap.minutesListened.toString(), label = "MINUTI")
            Stat(value = recap.playCount.toString(), label = "ASCOLTI")
            Stat(value = recap.distinctSongs.toString(), label = "BRANI")
        }

        if (recap.topArtist != null) {
            Hairline()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "ARTISTA DEL MESE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkTertiary,
                    )
                    Text(
                        text = recap.topArtist.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.accentInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (recap.topArtistPlays == 1) "[1 ASCOLTO]"
                    else "[${recap.topArtistPlays} ASCOLTI]",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkTertiary,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Stat(value: String, label: String) {
    val colors = Xaos.colors
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            color = colors.ink,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkTertiary,
        )
    }
}

/**
 * Card di una playlist automatica: una copertina, il conteggio, e due azioni
 * distinte — riprodurre subito o aprire l'elenco.
 */
@Composable
private fun AutoPlaylistCard(
    songs: List<Song>,
    caption: String,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = Xaos.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .nothingCard()
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            uri = songs.firstOrNull()?.artworkUri,
            cornerRadius = 12,
            modifier = Modifier.size(64.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = if (songs.size == 1) "1 BRANO" else "${songs.size} BRANI",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.accent, CircleShape)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = XaosIcons.Play,
                contentDescription = "Riproduci",
                tint = colors.onAccent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun EmptyHome() {
    val colors = Xaos.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ANCORA NIENTE QUI",
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkSecondary,
        )
        Text(
            text = "Ascolta qualcosa dalla libreria: questa schermata si popola " +
                "da sola con quello che riproduci.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkTertiary,
        )
    }
}
