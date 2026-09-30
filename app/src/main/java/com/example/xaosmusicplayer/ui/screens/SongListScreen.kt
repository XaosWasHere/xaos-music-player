package com.example.xaosmusicplayer.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.ui.components.Artwork
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.components.PillButton
import com.example.xaosmusicplayer.ui.components.SongRow
import com.example.xaosmusicplayer.ui.components.formatDuration
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos

/**
 * La testata di una raccolta, come su Spotify: copertina grande al centro,
 * titolo, dettagli e comandi, e sotto i brani, tutto nello stesso scorrimento.
 */
data class CollectionHero(
    /** La copertina; null mostra il riquadro a punti. */
    val artwork: Uri?,
    /** "ALBUM", "PLAYLIST"… sopra il titolo. */
    val kind: String,
    /** L'artista dell'album, per esempio. */
    val subtitle: String? = null,
    /** Le righe scritte dall'utente sotto la copertina (le playlist). */
    val description: String? = null,
    /** Il tocco sulla copertina: per le playlist, cambiarla. */
    val onArtworkClick: (() -> Unit)? = null,
    /** Comandi in più, a sinistra dei pulsanti di riproduzione. */
    val actions: (@Composable RowScope.() -> Unit)? = null,
)

/**
 * Elenco di brani riusato per album, artista, playlist e preferiti: cambiano
 * titolo, sorgente della lista e, se c'è, la testata con la copertina.
 */
@Composable
fun SongListScreen(
    title: String,
    songs: List<Song>,
    currentSongId: Long?,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffleAll: () -> Unit,
    onSongMenu: (Song) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Nessun brano",
    hero: CollectionHero? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (hero != null) {
            // Con la testata il titolo sta sotto la copertina: in alto solo il ritorno.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 4.dp)) {
                CircleIconButton(icon = XaosIcons.Back, contentDescription = "Indietro", onClick = onBack)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                item { HeroHeader(title, hero, songs, onPlay = { onPlay(0) }, onShuffle = onShuffleAll) }
                if (songs.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Text(emptyMessage.uppercase(), style = MaterialTheme.typography.labelLarge, color = Xaos.colors.inkSecondary)
                        }
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { onPlay(index) },
                        onMenuClick = { onSongMenu(song) },
                    )
                }
            }
            return@Column
        }

        ScreenHeader(title = title.uppercase(), onBack = onBack)

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyMessage.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Xaos.colors.inkSecondary,
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = "RIPRODUCI",
                icon = XaosIcons.Play,
                filled = true,
                onClick = { onPlay(0) },
            )
            PillButton(text = "CASUALE", icon = XaosIcons.Shuffle, onClick = onShuffleAll)
            Spacer(Modifier.weight(1f))
            Text(
                text = "[${songs.size}] ${formatDuration(songs.sumOf { it.durationMs })}",
                style = MaterialTheme.typography.labelMedium,
                color = Xaos.colors.inkTertiary,
            )
        }

        Hairline()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    onClick = { onPlay(index) },
                    onMenuClick = { onSongMenu(song) },
                )
            }
        }
    }
}

@Composable
private fun HeroHeader(
    title: String,
    hero: CollectionHero,
    songs: List<Song>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val colors = Xaos.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(236.dp)
                .then(if (hero.onArtworkClick != null) Modifier.clickable(onClick = hero.onArtworkClick) else Modifier),
        ) {
            Artwork(uri = hero.artwork, cornerRadius = 20, modifier = Modifier.fillMaxSize())
        }
        if (!hero.description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                hero.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(hero.kind, style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hero.subtitle.isNullOrBlank()) {
                Text(
                    hero.subtitle.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.accentInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "[${songs.size}] BRANI · ${totalLength(songs.sumOf { it.durationMs })}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                hero.actions?.invoke(this)
                Spacer(Modifier.weight(1f))
                CircleIconButton(icon = XaosIcons.Shuffle, contentDescription = "Casuale", onClick = onShuffle)
                Spacer(Modifier.width(12.dp))
                // Il play grande a destra, nel colore d'accento, come su Spotify.
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.accent, CircleShape)
                        .clickable(enabled = songs.isNotEmpty(), onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(XaosIcons.Play, "Riproduci", tint = colors.onAccent, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
    Hairline()
}

/** "42 MIN" o "1 H 12 MIN": per una raccolta i secondi non servono. */
private fun totalLength(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} H ${minutes % 60} MIN" else "$minutes MIN"
}
