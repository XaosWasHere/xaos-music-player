package com.example.xaosmusicplayer.ui.effect

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * La copertina ingrandita fino a coprire tutto lo schermo, dietro al player.
 *
 * Il ritaglio centrato la zooma quanto serve a riempire l'altezza, che su un
 * telefono vuol dire quasi il doppio. Verso l'alto sfuma nel fondo, così la barra
 * di stato e i comandi in cima restano leggibili; verso il basso si chiude prima
 * del titolo, perché testo, cursore e comandi stiano sul fondo pieno e non su
 * un'immagine.
 *
 * Senza copertina non disegna niente: resta il fondo del player.
 */
@Composable
fun ArtworkBackdrop(
    artworkUri: Uri?,
    backdrop: Color,
    modifier: Modifier = Modifier,
) {
    if (artworkUri == null) return
    val context = LocalContext.current

    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artworkUri)
                // Cambiando brano la nuova copertina entra in dissolvenza invece
                // di sostituire di colpo mezzo schermo.
                .crossfade(CROSSFADE_MS)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Un velo uniforme: l'immagine resta sfondo e non compete
                    // con la copertina vera, che le sta sopra.
                    drawRect(backdrop.copy(alpha = VEIL_ALPHA))
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to backdrop,
                            TOP_FADE_END to Color.Transparent,
                            BOTTOM_FADE_START to Color.Transparent,
                            BOTTOM_FADE_END to backdrop,
                            1f to backdrop,
                        ),
                    )
                },
        )
    }
}

private const val CROSSFADE_MS = 500

/** Quanto il fondo copre l'immagine ovunque, prima delle sfumature. */
private const val VEIL_ALPHA = 0.28f

/** Dove finisce la sfumatura in alto, in frazioni d'altezza. */
private const val TOP_FADE_END = 0.26f

/** La sfumatura in basso: parte a metà copertina e si chiude sul titolo. */
private const val BOTTOM_FADE_START = 0.36f
private const val BOTTOM_FADE_END = 0.62f
