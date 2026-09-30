package com.example.xaosmusicplayer.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.Playlist
import com.example.xaosmusicplayer.ui.components.AlbumCell
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos
import java.io.File

/** La copertina di una playlist: quella scelta, altrimenti quella del primo brano. */
fun Playlist.coverUri(firstSongArtwork: Uri?): Uri? =
    coverPath?.let(::File)?.takeIf { it.isFile }?.let(Uri::fromFile) ?: firstSongArtwork

/**
 * La sezione Playlist: una griglia come quella degli album, con i Preferiti
 * per primi. Tenere premuto su una playlist la elimina, dopo conferma.
 */
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    favoritesCount: Int,
    artworkOf: (Playlist) -> Uri?,
    onOpenFavorites: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (Playlist) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Playlist?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PLAYLIST",
                style = MaterialTheme.typography.headlineSmall,
                color = Xaos.colors.ink,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(
                icon = XaosIcons.Add,
                contentDescription = "Nuova playlist",
                onClick = { showCreateDialog = true },
                filled = true,
            )
            Spacer(Modifier.width(10.dp))
            CircleIconButton(
                icon = XaosIcons.Settings,
                contentDescription = "Impostazioni",
                onClick = onOpenSettings,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "favorites") {
                AlbumCell(
                    title = "Preferiti",
                    subtitle = "[$favoritesCount] brani",
                    artworkUri = null,
                    onClick = onOpenFavorites,
                )
            }
            items(playlists, key = { it.id }) { playlist ->
                AlbumCell(
                    title = playlist.name,
                    subtitle = "[${playlist.songIds.size}] brani",
                    artworkUri = playlist.coverUri(artworkOf(playlist)),
                    onClick = { onOpen(playlist) },
                    onLongClick = { toDelete = playlist },
                )
            }
            if (playlists.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Crea una playlist con il + in alto, o dal menu di un brano.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Xaos.colors.inkSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title = "NUOVA PLAYLIST",
            initialValue = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreate(name)
            },
        )
    }
    toDelete?.let { playlist ->
        DeletePlaylistDialog(playlist, onDismiss = { toDelete = null }, onConfirm = { toDelete = null; onDelete(playlist) })
    }
}

@Composable
fun DeletePlaylistDialog(playlist: Playlist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Xaos.colors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text("ELIMINARE \"${playlist.name.uppercase()}\"?", style = MaterialTheme.typography.titleLarge, color = Xaos.colors.ink) },
        text = {
            Text(
                "I brani restano dove sono. La playlist sparisce anche dal PC alla prossima sincronizzazione.",
                style = MaterialTheme.typography.bodyMedium,
                color = Xaos.colors.inkSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("ELIMINA", style = MaterialTheme.typography.titleMedium, color = Xaos.colors.accentInk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANNULLA", style = MaterialTheme.typography.titleMedium, color = Xaos.colors.inkSecondary)
            }
        },
    )
}

/** Nome, descrizione e copertina di una playlist, dallo stesso dialogo. */
@Composable
fun PlaylistInfoDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    var name by remember { mutableStateOf(playlist.name) }
    var description by remember { mutableStateOf(playlist.description) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Xaos.colors.ink,
        unfocusedTextColor = Xaos.colors.ink,
        focusedBorderColor = Xaos.colors.accent,
        unfocusedBorderColor = Xaos.colors.line,
        cursorColor = Xaos.colors.accentInk,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Xaos.colors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text("MODIFICA PLAYLIST", style = MaterialTheme.typography.titleLarge, color = Xaos.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nome") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(DESCRIPTION_MAX) },
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("Descrizione") },
                    supportingText = { Text("${description.length}/$DESCRIPTION_MAX") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = fieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPickCover) {
                        Text("SCEGLI COPERTINA", style = MaterialTheme.typography.labelLarge, color = Xaos.colors.ink)
                    }
                    if (playlist.coverPath != null) {
                        TextButton(onClick = onRemoveCover) {
                            Text("TOGLI", style = MaterialTheme.typography.labelLarge, color = Xaos.colors.inkSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), description.trim()) }, enabled = name.isNotBlank()) {
                Text(
                    "SALVA",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (name.isNotBlank()) Xaos.colors.accentInk else Xaos.colors.inkSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANNULLA", style = MaterialTheme.typography.titleMedium, color = Xaos.colors.inkSecondary)
            }
        },
    )
}

/** Una breve descrizione: sta sotto la copertina, non è un articolo. */
private const val DESCRIPTION_MAX = 160

/** Dialogo per creare o rinominare una playlist. */
@Composable
fun NameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Xaos.colors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Xaos.colors.ink,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Nome",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Xaos.colors.inkSecondary,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Xaos.colors.ink,
                    unfocusedTextColor = Xaos.colors.ink,
                    focusedBorderColor = Xaos.colors.accent,
                    unfocusedBorderColor = Xaos.colors.line,
                    cursorColor = Xaos.colors.accentInk,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(
                    text = "CONFERMA",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (name.isNotBlank()) Xaos.colors.accentInk else Xaos.colors.inkSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "ANNULLA",
                    style = MaterialTheme.typography.titleMedium,
                    color = Xaos.colors.inkSecondary,
                )
            }
        },
    )
}
