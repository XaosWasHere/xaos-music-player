package com.example.xaosmusicplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xaosmusicplayer.data.ArtworkStore
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.ui.components.Artwork
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Come l'album va salvato: i dati comuni più l'ordine deciso dall'utente. */
data class AlbumEdit(
    val title: String?,
    val artist: String?,
    val genre: String?,
    val artworkPath: String?,
    val songsInOrder: List<Song>,
)

/**
 * Modifica di un intero album: i dati che le tracce hanno in comune, e la loro
 * posizione al suo interno.
 *
 * Il riordino avviene qui e non traccia per traccia perché la posizione è
 * relativa: spostare un brano ha senso solo rispetto agli altri.
 */
@Composable
fun EditAlbumScreen(
    albumTitle: String,
    songs: List<Song>,
    knownArtists: List<String>,
    knownGenres: List<String>,
    hasEdits: Boolean,
    onBack: () -> Unit,
    onSave: (AlbumEdit) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val originalArtist = remember(songs) {
        songs.map { it.albumArtist }.distinct().singleOrNull().orEmpty()
    }
    val originalGenre = remember(songs) {
        songs.mapNotNull { it.genre }.distinct().singleOrNull().orEmpty()
    }

    var title by remember(albumTitle) { mutableStateOf(albumTitle) }
    var artist by remember(albumTitle) { mutableStateOf(originalArtist) }
    var genre by remember(albumTitle) { mutableStateOf(originalGenre) }
    var artworkPath by remember(albumTitle) {
        mutableStateOf(songs.firstOrNull()?.artworkPath)
    }

    // L'ordine è stato locale finché non si salva: trascinare non deve
    // riscrivere la libreria a ogni pixel.
    val ordered: SnapshotStateList<Song> = remember(albumTitle, songs) {
        songs.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
            .toMutableStateList()
    }

    val pickArtwork = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = songs.firstOrNull() ?: return@rememberLauncherForActivityResult
        scope.launch { artworkPath = ArtworkStore(context).save(uri, target.id) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            title = "MODIFICA ALBUM",
            onBack = onBack,
            trailing = {
                TextButton(
                    onClick = {
                        onSave(
                            AlbumEdit(
                                title = title.trim().takeIf { it.isNotEmpty() && it != albumTitle },
                                artist = artist.trim()
                                    .takeIf { it.isNotEmpty() && it != originalArtist },
                                genre = genre.trim()
                                    .takeIf { it.isNotEmpty() && it != originalGenre },
                                artworkPath = artworkPath,
                                songsInOrder = ordered.toList(),
                            )
                        )
                    },
                ) {
                    Text(
                        text = "SALVA",
                        style = MaterialTheme.typography.labelLarge,
                        color = Xaos.colors.onAccent,
                        modifier = Modifier
                            .background(
                                Xaos.colors.accent,
                                androidx.compose.foundation.shape.RoundedCornerShape(50),
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(
                    uri = artworkPath?.let { android.net.Uri.fromFile(java.io.File(it)) }
                        ?: songs.firstOrNull()?.artworkUri,
                    cornerRadius = 20,
                    modifier = Modifier
                        .size(180.dp)
                        .clickable {
                            pickArtwork.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "TOCCA PER CAMBIARE COPERTINA",
                    style = MaterialTheme.typography.labelMedium,
                    color = Xaos.colors.inkSecondary,
                )
            }

            Spacer(Modifier.height(28.dp))

            LabeledTextField(label = "TITOLO", value = title, onValueChange = { title = it })
            AlbumSuggestingField(
                label = "ARTISTA",
                value = artist,
                suggestions = knownArtists,
                onValueChange = { artist = it },
            )
            AlbumSuggestingField(
                label = "GENERE",
                value = genre,
                suggestions = knownGenres,
                onValueChange = { genre = it },
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "ORDINE DELLE TRACCE",
                style = MaterialTheme.typography.labelMedium,
                color = Xaos.colors.inkSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            ReorderableTracks(ordered)

            if (hasEdits) {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onReset) {
                    Text(
                        text = "RIPRISTINA DATI ORIGINALI",
                        style = MaterialTheme.typography.titleMedium,
                        color = Xaos.colors.inkSecondary,
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * Elenco riordinabile trascinando la maniglia.
 *
 * Non è una LazyColumn: le tracce di un album sono poche e una lista non pigra
 * ha altezze note, che è ciò che rende il calcolo della posizione di arrivo una
 * semplice divisione invece di una misurazione continua.
 */
@Composable
private fun ReorderableTracks(ordered: SnapshotStateList<Song>) {
    val rowHeight = ROW_HEIGHT_DP.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ordered.forEachIndexed { index, song ->
            val isDragged = index == draggedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    // La riga trascinata segue il dito e passa sopra le altre.
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .background(
                        if (isDragged) Xaos.colors.surfaceHigh else Color.Transparent
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleMedium,
                    color = Xaos.colors.accentInk,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Xaos.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = Xaos.colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    imageVector = XaosIcons.DragHandle,
                    contentDescription = "Trascina per riordinare",
                    tint = Xaos.colors.inkSecondary,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(24.dp)
                        .pointerInput(ordered.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggedIndex = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggedIndex = -1
                                    dragOffset = 0f
                                },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y

                                // Quando lo scostamento supera una riga intera,
                                // lo scambio avviene subito e l'offset si azzera
                                // di altrettanto: così il dito resta sulla riga.
                                val steps = (dragOffset / rowHeightPx).roundToInt()
                                if (steps == 0) return@detectDragGestures
                                val from = draggedIndex
                                val to = (from + steps).coerceIn(ordered.indices)
                                if (to == from) return@detectDragGestures
                                ordered.add(to, ordered.removeAt(from))
                                draggedIndex = to
                                dragOffset -= (to - from) * rowHeightPx
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun AlbumSuggestingField(
    label: String,
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
) {
    val matches = remember(value, suggestions) {
        val typed = value.trim()
        if (typed.isEmpty()) emptyList()
        else suggestions
            .filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, true) }
            .take(6)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LabeledTextField(label = label, value = value, onValueChange = onValueChange)
        if (matches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                matches.forEach { suggestion ->
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.labelMedium,
                        color = Xaos.colors.ink,
                        maxLines = 1,
                        modifier = Modifier
                            .border(
                                1.dp,
                                Xaos.colors.line,
                                androidx.compose.foundation.shape.RoundedCornerShape(50),
                            )
                            .clickable { onValueChange(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

private const val ROW_HEIGHT_DP = 60
