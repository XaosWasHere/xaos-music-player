package com.example.xaosmusicplayer.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Contenitore che cambia brano quando lo si trascina di lato.
 *
 * Il contenuto segue il dito e, superata la soglia, esce dal lato verso cui è
 * stato spinto; il brano nuovo entra dal lato opposto. È il gesto stesso a fare
 * spazio al brano successivo, invece di un cambio istantaneo sotto le dita.
 *
 * L'attesa fra l'uscita e l'entrata non è estetica: il comando al player è
 * asincrono, e far entrare il blocco troppo presto mostrerebbe per qualche
 * fotogramma il brano appena lasciato.
 */
@Composable
fun SwipeableSong(
    songId: Long,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    // Il gesto vive in una coroutine che sopravvive alle ricomposizioni: senza
    // questo leggerebbe l'id del brano com'era quando è cominciato.
    val currentId by rememberUpdatedState(songId)

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> offset += delta },
                onDragStopped = { velocity ->
                    // Passa anche uno scatto veloce che non arriva alla soglia:
                    // un gesto deciso ma corto è comunque una richiesta chiara.
                    val far = abs(offset) > width * TRIGGER_FRACTION
                    val fast = abs(velocity) > TRIGGER_VELOCITY &&
                        abs(offset) > width * MIN_FRACTION
                    val forward = offset < 0f

                    if (far || fast) {
                        val leaving = currentId
                        val exit = if (forward) -width else width
                        animate(offset, exit, animationSpec = tween(160)) { v, _ -> offset = v }
                        if (forward) onNext() else onPrevious()
                        // Se il player non risponde si rientra lo stesso: meglio
                        // rivedere lo stesso brano che una schermata vuota.
                        withTimeoutOrNull(HANDOVER_TIMEOUT_MS) {
                            snapshotFlow { currentId }.first { it != leaving }
                        }
                        offset = -exit
                        animate(offset, 0f, animationSpec = tween(220)) { v, _ -> offset = v }
                    } else {
                        animate(offset, 0f, animationSpec = tween(200)) { v, _ -> offset = v }
                    }
                },
            )
            .offset { IntOffset(offset.roundToInt(), 0) },
        content = content,
    )
}

/** Quanta parte della larghezza serve percorrere perché il brano cambi. */
private const val TRIGGER_FRACTION = 0.3f

/** Sotto questa frazione non basta nemmeno uno scatto veloce: è un tocco storto. */
private const val MIN_FRACTION = 0.05f

/** Pixel al secondo oltre i quali il gesto vale come deciso. */
private const val TRIGGER_VELOCITY = 700f

private const val HANDOVER_TIMEOUT_MS = 600L
