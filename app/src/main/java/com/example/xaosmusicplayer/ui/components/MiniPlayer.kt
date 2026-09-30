package com.example.xaosmusicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos

/**
 * Barra persistente in fondo alla libreria. Compare solo quando c'è un brano
 * caricato e porta alla schermata di riproduzione.
 *
 * È una card che galleggia sulla griglia, non una fascia a tutta larghezza:
 * sotto si continuano a vedere i puntini, ed è quello che la fa sembrare leggera.
 */
@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = song != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        if (song == null) return@AnimatedVisibility
        val colors = Xaos.colors

        // Il blocco che scorre non deve invadere la libreria che sta sopra né la
        // barra di navigazione sotto: esce di scena dentro i propri bordi.
        Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .nothingCard(),
            ) {
                SwipeableSong(
                    songId = song.id,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            uri = song.artworkUri,
                            cornerRadius = 10,
                            modifier = Modifier.size(44.dp),
                        )

                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = song.title.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Il play è il solo elemento pieno della barra: è l'azione
                        // che si cerca con il pollice senza guardare.
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.accent, CircleShape)
                                .clickable(onClick = onPlayPause),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayPauseGlyph(isPlaying, if (isPlaying) "Pausa" else "Riproduci", colors.onAccent, glyphSize = 20.dp)
                        }
                        IconButton(onClick = onNext) {
                            Icon(
                                imageVector = XaosIcons.Next,
                                contentDescription = "Brano successivo",
                                tint = colors.ink,
                            )
                        }
                    }
                }

                // L'avanzamento come fila di punti: dà il contesto temporale
                // senza aggiungere una barra piena in un'interfaccia fatta di punti.
                DotProgressLine(
                    progress = progress,
                    color = colors.accent,
                    trackColor = colors.track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(horizontal = 14.dp),
                )
                Box(Modifier.height(4.dp))
            }
        }
    }
}
