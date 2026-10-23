package com.example.xaosmusicplayer.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.i18n.t
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos
import java.util.Locale

/** Millisecondi come "MM:SS", o "H:MM:SS" per le tracce lunghe. */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Copertina con segnaposto. Molti brani non hanno artwork in MediaStore, quindi
 * il fallback non è un caso limite ma la normalità.
 */
@Composable
fun Artwork(
    uri: Uri?,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 10,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    SubcomposeAsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(Xaos.colors.surfaceHigh),
        error = { ArtworkPlaceholder() },
        loading = { ArtworkPlaceholder() },
    )
}

/** Senza copertina resta un riquadro a puntini con la nota: vuoto ma voluto. */
@Composable
private fun ArtworkPlaceholder() {
    val colors = Xaos.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceHigh)
            .dotGrid(colors.inkTertiary.copy(alpha = 0.35f), spacing = 6.dp, radius = 0.8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = XaosIcons.MusicNote,
            contentDescription = null,
            tint = colors.inkTertiary,
            modifier = Modifier.fillMaxSize(0.36f),
        )
    }
}

/** Riga della lista brani: copertina, titolo, artista, durata e menu. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Il tocco prolungato apre lo stesso menu del pulsante a destra:
            // due gesti per la stessa cosa, non due menu diversi.
            .combinedClickable(onClick = onClick, onLongClick = onMenuClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = Xaos.colors
        Artwork(uri = song.artworkUri, modifier = Modifier.size(52.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Il brano in riproduzione ha il punto d'accento davanti: sul tema
                // chiaro il colore da solo non basterebbe, il giallo non si legge.
                if (isCurrent) {
                    AccentDot(size = 7.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    text = song.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) colors.accentInk else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = com.example.xaosmusicplayer.data.MusicRepository.displayName(song.artist),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (song.isRemote) PcDownloadButton(song)

        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkTertiary,
        )

        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = XaosIcons.Sort,
                contentDescription = t("Opzioni brano"),
                tint = colors.inkTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Il segno dei brani che stanno solo sul PC (Xaos Beam): la freccia di
 * download, da toccare per tenerli anche sul telefono. Mentre scarica mostra
 * la percentuale; se il download non riesce la freccia si accende, e toccarla
 * riprova.
 */
@Composable
fun PcDownloadButton(song: Song, modifier: Modifier = Modifier) {
    val colors = Xaos.colors
    val downloads by com.example.xaosmusicplayer.beam.Beam.downloads.collectAsState()
    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        when (val state = downloads[song.id]) {
            is com.example.xaosmusicplayer.beam.Beam.Download.Running -> Text(
                text = if (state.progress < 0f) "…" else "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentInk,
                maxLines = 1,
            )
            com.example.xaosmusicplayer.beam.Beam.Download.Queued -> AccentDot(size = 7.dp, color = colors.inkTertiary)
            else -> IconButton(onClick = { com.example.xaosmusicplayer.beam.Beam.download(listOf(song)) }) {
                Icon(
                    imageVector = XaosIcons.Download,
                    contentDescription = t("Solo sul PC: scarica sul telefono"),
                    tint = if (state is com.example.xaosmusicplayer.beam.Beam.Download.Failed) colors.accentInk else colors.inkTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Cella della griglia album: copertina quadrata e footer con titolo. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlbumCell(
    title: String,
    subtitle: String,
    artworkUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** Quanti brani dell'album stanno solo sul PC: se ce ne sono, un segno sulla copertina. */
    onPcOnly: Int = 0,
) {
    val colors = Xaos.colors
    Column(
        modifier = modifier
            .nothingCard()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Box {
            Artwork(
                uri = artworkUri,
                cornerRadius = 14,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            if (onPcOnly > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(colors.background.copy(alpha = 0.75f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = XaosIcons.Download,
                        contentDescription = t("Brani solo sul PC"),
                        tint = colors.ink,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
