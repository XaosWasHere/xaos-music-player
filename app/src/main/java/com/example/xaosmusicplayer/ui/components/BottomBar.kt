package com.example.xaosmusicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos

/** Le sezioni principali dell'app, come su Spotify. */
enum class Section(val label: String, val icon: ImageVector) {
    HOME("HOME", XaosIcons.Home),
    LIBRARY("LIBRERIA", XaosIcons.Library),
    PLAYLISTS("PLAYLIST", XaosIcons.Queue),
    SEARCH("CERCA", XaosIcons.Search),
}

/**
 * Barra di navigazione in fondo. Sta sotto al mini-player, così l'ordine
 * verticale è: contenuto, brano in riproduzione, sezioni.
 *
 * Non ha un fondo proprio: poggia sulla stessa griglia di puntini del resto, e la
 * sezione attiva si riconosce dal punto d'accento sotto l'etichetta.
 */
@Composable
fun BottomBar(
    selected: Section,
    onSelect: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Section.entries.forEach { section ->
            SectionTab(
                section = section,
                isSelected = section == selected,
                onClick = { onSelect(section) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionTab(
    section: Section,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Xaos.colors
    // Niente ripple: l'onda di Material stona con un'interfaccia fatta di punti.
    val interactionSource = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        if (isSelected) colors.ink else colors.inkTertiary,
        label = "tab-tint",
    )
    val dotSize by animateDpAsState(if (isSelected) 5.dp else 0.dp, label = "tab-dot")

    Column(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = section.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = section.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
        Box(modifier = Modifier.height(5.dp), contentAlignment = Alignment.Center) {
            AccentDot(size = dotSize)
        }
    }
}
