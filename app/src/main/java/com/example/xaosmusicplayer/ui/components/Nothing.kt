package com.example.xaosmusicplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.ui.theme.Xaos
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/*
 * I mattoni dell'aspetto Nothing: la griglia di puntini, le card velate con il
 * filetto, le intestazioni tecniche, i controlli a matrice di punti.
 */

/** Passo e raggio dei puntini di sfondo: gli stessi rapporti del sito Nothing. */
val DotGridSpacing = 14.dp
val DotGridRadius = 0.9.dp

/**
 * La griglia di puntini disegnata dietro al contenuto.
 *
 * I punti si calcolano una volta per dimensione e si disegnano con una sola
 * chiamata: anche a tutto schermo sono un paio di migliaia, e ridisegnarli a ogni
 * frame di uno scorrimento non deve costare niente.
 */
fun Modifier.dotGrid(
    color: Color,
    spacing: Dp = DotGridSpacing,
    radius: Dp = DotGridRadius,
): Modifier = if (color.alpha == 0f) this else drawWithCache {
    // Pallini spenti dal tema personalizzato: niente da disegnare.
    // Passo e diametro in pixel interi, e ogni centro nello stesso punto di un
    // pixel: un puntino a cavallo fra due pixel verrebbe sfumato e sembrerebbe
    // più tenue degli altri.
    val step = spacing.toPx().roundToInt().coerceAtLeast(2).toFloat()
    val diameter = (radius.toPx() * 2f).roundToInt().coerceAtLeast(1)
    val phase = if (diameter % 2 == 0) 0f else 0.5f
    // Fuori da buildList: lì dentro `size` sarebbe quella della lista.
    val width = size.width
    val height = size.height
    val points = buildList {
        // Mezzo passo di margine: la griglia resta centrata invece di
        // appoggiarsi al bordo sinistro.
        val startX = floor((width % step) / 2f + step / 2f) + phase
        val startY = floor(step / 2f) + phase
        var y = startY
        while (y < height) {
            var x = startX
            while (x < width) {
                add(Offset(x, y))
                x += step
            }
            y += step
        }
    }
    val stroke = diameter.toFloat()
    onDrawBehind {
        drawPoints(points, PointMode.Points, color, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Fondo di una schermata, con la sua griglia: pieno, o sfumato se il tema lo vuole. */
@Composable
fun Modifier.screenBackground(): Modifier {
    val colors = Xaos.colors
    val second = colors.background2
    val fill = if (second == null) background(colors.background)
    else background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors.background, second)))
    return fill.dotGrid(colors.dot)
}

val CardShape = RoundedCornerShape(20.dp)

/** Card velata: si intravedono i puntini sotto, il filetto la stacca dal fondo. */
@Composable
fun Modifier.nothingCard(shape: Shape = CardShape): Modifier {
    val colors = Xaos.colors
    return clip(shape)
        .background(colors.card, shape)
        .border(1.dp, colors.line, shape)
}

/**
 * Intestazione di sezione alla Nothing: maiuscolo tecnico, e il conteggio fra
 * parentesi quadre quando c'è qualcosa da contare.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = Xaos.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentDot()
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink,
        )
        if (count != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = "[$count]",
                style = MaterialTheme.typography.labelLarge,
                color = colors.inkTertiary,
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Il punto d'accento: segna ciò che è vivo o selezionato. */
@Composable
fun AccentDot(modifier: Modifier = Modifier, size: Dp = 6.dp, color: Color = Xaos.colors.accent) {
    Box(modifier = modifier.size(size).background(color, CircleShape))
}

/** Etichetta piena nel colore d'accento: l'unico modo per avere testo "giallo" leggibile. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val colors = Xaos.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.onAccent,
        maxLines = 1,
        modifier = modifier
            .background(colors.accent, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Pulsante tondo con il filetto, come quelli in alto a destra sul sito. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    filled: Boolean = false,
    tint: Color? = null,
) {
    val colors = Xaos.colors
    val background = if (filled) colors.accent else colors.card
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background, CircleShape)
            .then(if (filled) Modifier else Modifier.border(1.dp, colors.line, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: if (filled) colors.onAccent else colors.ink,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** Pulsante a pillola: pieno per l'azione principale, filettato per le altre. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
) {
    val colors = Xaos.colors
    val shape = RoundedCornerShape(50)
    val content = if (filled) colors.onAccent else colors.ink
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (filled) colors.accent else colors.card, shape)
            .then(if (filled) Modifier else Modifier.border(1.dp, colors.line, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** Filetto orizzontale da 1dp. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Xaos.colors.line)
    )
}

/**
 * Caricamento a matrice: otto punti in cerchio, uno acceso che gira e si lascia
 * dietro una scia. Sostituisce l'anello di Material, che qui sarebbe l'unico
 * elemento continuo in un'interfaccia fatta di punti.
 */
@Composable
fun DotSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = Xaos.colors.accent,
    idleColor: Color = Xaos.colors.track,
) {
    val transition = rememberInfiniteTransition(label = "dot-spinner")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = SPINNER_DOTS.toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "head",
    )
    Canvas(modifier = modifier.size(size)) {
        val ringRadius = this.size.minDimension / 2f * 0.72f
        val dotRadius = this.size.minDimension * 0.085f
        val c = center
        for (i in 0 until SPINNER_DOTS) {
            // Distanza del punto dalla testa, all'indietro: 0 è la testa.
            val behind = ((head - i) % SPINNER_DOTS + SPINNER_DOTS) % SPINNER_DOTS
            val glow = (1f - behind / 3.5f).coerceIn(0f, 1f)
            val angle = (i.toFloat() / SPINNER_DOTS) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val pos = Offset(c.x + ringRadius * cos(angle), c.y + ringRadius * sin(angle))
            drawCircle(idleColor, dotRadius, pos)
            if (glow > 0f) drawCircle(color.copy(alpha = glow), dotRadius, pos)
        }
    }
}

private const val SPINNER_DOTS = 8

/**
 * Avanzamento come fila di punti: pieni fin dove si è arrivati, spenti dopo.
 * La frazione cade fra un punto e l'altro, e quello a cavallo si accende a metà.
 */
@Composable
fun DotProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Xaos.colors.accent,
    trackColor: Color = Xaos.colors.track,
    spacing: Dp = 5.dp,
    radius: Dp = 1.2.dp,
) {
    Canvas(modifier = modifier) {
        val step = spacing.toPx()
        val r = radius.toPx()
        val count = ((size.width - 2 * r) / step).toInt().coerceAtLeast(1) + 1
        val used = (count - 1) * step
        val startX = (size.width - used) / 2f
        val y = size.height / 2f
        val filled = progress.coerceIn(0f, 1f) * (count - 1)
        for (i in 0 until count) {
            val x = startX + i * step
            val on = (filled - i + 1f).coerceIn(0f, 1f)
            drawCircle(trackColor, r, Offset(x, y))
            if (on > 0f) drawCircle(color.copy(alpha = color.alpha * on), r, Offset(x, y))
        }
    }
}
