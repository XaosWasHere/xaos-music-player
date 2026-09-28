package com.example.xaosmusicplayer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.ui.components.SongRow
import com.example.xaosmusicplayer.ui.components.formatDuration
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.components.PillButton
import com.example.xaosmusicplayer.ui.theme.Xaos

/**
 * Elenco di brani riusato per album, artista, playlist e preferiti: cambiano
 * solo titolo e sorgente della lista.
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
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
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
