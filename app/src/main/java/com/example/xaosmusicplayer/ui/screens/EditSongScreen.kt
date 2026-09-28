package com.example.xaosmusicplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.ArtworkStore
import com.example.xaosmusicplayer.data.MusicRepository
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.data.SongOverride
import com.example.xaosmusicplayer.ui.components.Artwork
import com.example.xaosmusicplayer.ui.theme.Xaos
import kotlinx.coroutines.launch

/**
 * Correzione dei metadati di un brano.
 *
 * Artista, album e genere si completano con quelli già presenti in libreria:
 * è il modo per far confluire un brano scaricato — che spesso arriva con
 * l'artista sbagliato e nessun album — dentro le raccolte che esistono già,
 * invece di crearne una quasi omonima.
 */
@Composable
fun EditSongScreen(
    song: Song,
    /**
     * Il brano come sta in MediaStore. Ogni campo si confronta con questo, non
     * con quello che si vedeva entrando: così riscrivere il valore di partenza
     * cancella la correzione invece di sostituirla con una identica.
     */
    rawSong: Song,
    knownArtists: List<String>,
    knownAlbums: List<String>,
    knownGenres: List<String>,
    hasEdits: Boolean,
    onBack: () -> Unit,
    onSave: (SongOverride) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var albumArtist by remember(song.id) { mutableStateOf(song.albumArtist) }
    var album by remember(song.id) { mutableStateOf(song.album) }
    var genre by remember(song.id) { mutableStateOf(song.genre.orEmpty()) }
    var track by remember(song.id) {
        mutableStateOf(song.trackNumber.takeIf { it > 0 }?.toString().orEmpty())
    }
    var artworkPath by remember(song.id) { mutableStateOf(song.artworkPath) }

    // L'artista principale, finché non lo si tocca, segue il credito: è così
    // che viene calcolato quando non c'è una correzione esplicita, e mostrarlo
    // aggiornato evita di salvare un proprietario ormai scollegato dal credito.
    val derivedOwner = ownerDerivedFrom(artist, rawSong)
    var ownerTouched by remember(song.id) {
        mutableStateOf(song.albumArtist != ownerDerivedFrom(song.artist, rawSong))
    }
    val ownerValue = if (ownerTouched) albumArtist else derivedOwner

    val pickArtwork = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { artworkPath = ArtworkStore(context).save(uri, song.id) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            title = "MODIFICA",
            onBack = onBack,
            trailing = {
                TextButton(
                    onClick = {
                        onSave(
                            // Solo i campi davvero cambiati diventano correzioni:
                            // così un brano che poi viene ritaggato altrove torna
                            // ad aggiornarsi da solo.
                            SongOverride(
                                title = title.changedFrom(rawSong.title),
                                artist = artist.changedFrom(rawSong.artist),
                                // Solo se l'utente lo ha deciso a mano: altrimenti
                                // lo ricava il modello dal credito, e fissarlo qui
                                // impedirebbe di tornare al raggruppamento originale.
                                albumArtist = if (ownerTouched) {
                                    albumArtist.changedFrom(derivedOwner)
                                } else null,
                                album = album.changedFrom(rawSong.album),
                                genre = genre.changedFrom(rawSong.genre.orEmpty()),
                                trackNumber = track.toIntOrNull()
                                    ?.takeIf { it != rawSong.trackNumber },
                                artworkPath = artworkPath?.takeIf { it != rawSong.artworkPath },
                            )
                        )
                    },
                ) {
                    Text(
                        text = "SALVA",
                        style = MaterialTheme.typography.labelLarge,
                        color = Xaos.colors.onAccent,
                        modifier = Modifier
                            .background(Xaos.colors.accent, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // I suggerimenti stanno sotto il campo, dove compare la
                // tastiera: senza questo l'area utile finisce coperta proprio
                // mentre si scrive, che è l'unico momento in cui serve.
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
                        ?: song.artworkUri,
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

            SuggestingField(
                label = "ARTISTA (CREDITO)",
                value = artist,
                suggestions = knownArtists,
                onValueChange = { artist = it },
            )

            SuggestingField(
                // Il credito sopra è quello che si legge sulla traccia; questo
                // decide sotto quale artista il brano viene raggruppato, come
                // fa l'"artista dell'album" nei tag.
                label = "ARTISTA PRINCIPALE",
                value = ownerValue,
                suggestions = knownArtists,
                onValueChange = { ownerTouched = true; albumArtist = it },
            )

            SuggestingField(
                label = "ALBUM",
                value = album,
                suggestions = knownAlbums,
                onValueChange = { album = it },
            )

            SuggestingField(
                label = "GENERE",
                value = genre,
                suggestions = knownGenres,
                onValueChange = { genre = it },
            )

            LabeledTextField(
                label = "N. TRACCIA",
                value = track,
                // Solo cifre: il campo decide una posizione, non è testo libero.
                onValueChange = { input -> track = input.filter { it.isDigit() }.take(3) },
                numeric = true,
            )

            if (hasEdits) {
                Spacer(Modifier.height(16.dp))
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

/** Null se il valore non è cambiato: una correzione identica non serve. */
private fun String.changedFrom(original: String): String? =
    trim().takeIf { it.isNotEmpty() && it != original }

/**
 * L'artista principale che il modello dedurrebbe da un certo credito.
 *
 * Con il credito originale vale il tag del file, che è più affidabile di
 * qualunque deduzione; altrimenti si taglia sui featuring. Deve restare
 * allineata a come `applyOverrides` calcola lo stesso campo, o la schermata
 * mostrerebbe un proprietario diverso da quello che poi finisce in libreria.
 */
private fun ownerDerivedFrom(credit: String, rawSong: Song): String {
    val typed = credit.trim().takeIf { it.isNotEmpty() } ?: return rawSong.albumArtist
    return if (typed == rawSong.artist) rawSong.albumArtist
    else MusicRepository.primaryArtistOf(typed)
}

/** Campo di testo etichettato, condiviso con la schermata di modifica album. */
@Composable
internal fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = FIELD_GAP.dp)) {
        // Stessa fascia dei campi con suggerimenti, così tutte le etichette
        // stanno alla stessa distanza dal proprio campo.
        Row(
            modifier = Modifier.fillMaxWidth().height(LABEL_ROW_HEIGHT.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Xaos.colors.inkSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        XaosTextField(value = value, onValueChange = onValueChange, numeric = numeric)
    }
}

@Composable
private fun XaosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
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

/**
 * Campo con i valori già presenti in libreria proposti mentre si scrive.
 *
 * I suggerimenti compaiono solo se non coincidono già con quanto digitato: una
 * proposta identica a quello che c'è scritto sarebbe rumore.
 */
@Composable
private fun SuggestingField(
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
            .take(MAX_SUGGESTIONS)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = FIELD_GAP.dp)) {
        // Etichetta e proposte condividono la stessa riga, alta quanto un chip
        // anche quando è vuota. Così il campo resta attaccato alla sua etichetta
        // come tutti gli altri — una fascia libera in mezzo lo farebbe sembrare
        // di appartenere al campo precedente — e niente si sposta quando le
        // proposte compaiono: se il campo scendesse, finirebbe sotto la
        // tastiera proprio mentre si scrive.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(LABEL_ROW_HEIGHT.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Xaos.colors.inkSecondary,
            )

            if (matches.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    matches.forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, Xaos.colors.line, RoundedCornerShape(50))
                                .clickable { onValueChange(suggestion) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelMedium,
                                color = Xaos.colors.ink,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        XaosTextField(value = value, onValueChange = onValueChange)
    }
}

private const val MAX_SUGGESTIONS = 6

/**
 * Altezza della riga dell'etichetta: quanto un chip di suggerimento, sempre,
 * anche quando di proposte non ce ne sono e quando il campo non ne prevede.
 * Un'altezza costante tiene ogni campo alla stessa distanza dalla sua etichetta
 * e impedisce che la comparsa di una proposta sposti il campo.
 */
private const val LABEL_ROW_HEIGHT = 30

/** Distanza fra un campo e l'etichetta del successivo: più del respiro interno. */
private const val FIELD_GAP = 10
