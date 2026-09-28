package com.example.xaosmusicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.data.Playlist
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.theme.Xaos

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            title = "PLAYLIST",
            onBack = onBack,
            trailing = {
                CircleIconButton(
                    icon = XaosIcons.Add,
                    contentDescription = "Nuova playlist",
                    onClick = { showCreateDialog = true },
                    filled = true,
                )
            },
        )

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NESSUNA PLAYLIST",
                    style = MaterialTheme.typography.titleMedium,
                    color = Xaos.colors.inkSecondary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(playlist) }
                            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = playlist.name.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Xaos.colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "[${playlist.songIds.size}] BRANI",
                                style = MaterialTheme.typography.labelMedium,
                                color = Xaos.colors.inkTertiary,
                            )
                        }
                        IconButton(onClick = { onDelete(playlist) }) {
                            Icon(
                                imageVector = XaosIcons.Delete,
                                contentDescription = "Elimina playlist",
                                tint = Xaos.colors.inkSecondary,
                            )
                        }
                    }
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
}

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
