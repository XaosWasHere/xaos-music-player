package xaos.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xaos.desktop.theme.Xaos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * I mattoni dell'aspetto Nothing, gli stessi dell'app Android: griglia di
 * puntini, card velate con il filetto, pillole, controlli a punti. In più, qui,
 * il puntatore a manina e gli stati di hover che su un PC ci si aspetta.
 */

fun Modifier.dotGrid(color: Color, spacing: Dp = 14.dp, radius: Dp = 0.9.dp): Modifier =
    drawWithCache {
        val step = spacing.toPx()
        // Fuori da buildList: lì dentro `size` sarebbe quella della lista.
        val width = size.width
        val height = size.height
        val points = buildList {
            val startX = (width % step) / 2f + step / 2f
            var y = step / 2f
            while (y < height) {
                var x = startX
                while (x < width) {
                    add(Offset(x, y)); x += step
                }
                y += step
            }
        }
        val stroke = radius.toPx() * 2f
        onDrawBehind {
            drawPoints(points, PointMode.Points, color, strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }

val CardShape = RoundedCornerShape(18.dp)

@Composable
fun Modifier.nothingCard(shape: Shape = CardShape): Modifier {
    val c = Xaos.colors
    return clip(shape).background(c.card, shape).border(1.dp, c.line, shape)
}

/** Cliccabile con la manina sopra: su desktop è il segnale che si può premere. */
fun Modifier.pressable(onClick: () -> Unit): Modifier =
    pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)

/** Riga che si illumina appena al passaggio del mouse. */
@Composable
fun Modifier.hoverRow(selected: Boolean = false): Modifier {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val c = Xaos.colors
    val bg = when {
        selected -> c.surfaceHigh
        hovered -> c.surfaceHigh.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    return hoverable(source).background(bg, RoundedCornerShape(10.dp))
}

@Composable
fun AccentDot(modifier: Modifier = Modifier, size: Dp = 6.dp, color: Color = Xaos.colors.accent) {
    Box(modifier.size(size).background(color, CircleShape))
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val c = Xaos.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AccentDot()
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.ink)
        if (count != null) {
            Spacer(Modifier.size(8.dp))
            Text("[$count]", style = MaterialTheme.typography.labelLarge, color = c.inkTertiary)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val c = Xaos.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = c.onAccent,
        maxLines = 1,
        modifier = modifier
            .background(c.accent, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    filled: Boolean = false,
    tint: Color? = null,
    outlined: Boolean = true,
) {
    val c = Xaos.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) c.accent else Color.Transparent, CircleShape)
            .then(if (!filled && outlined) Modifier.border(1.dp, c.line, CircleShape) else Modifier)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: if (filled) c.onAccent else c.ink,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Xaos.colors
    val shape = RoundedCornerShape(50)
    val content = when {
        !enabled -> c.inkTertiary
        filled -> c.onAccent
        else -> c.ink
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (filled && enabled) c.accent else c.card, shape)
            .then(if (filled && enabled) Modifier else Modifier.border(1.dp, c.line, shape))
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(Xaos.colors.line))
}

@Composable
fun DotSpinner(modifier: Modifier = Modifier, size: Dp = 28.dp, color: Color = Xaos.colors.accent) {
    val idle = Xaos.colors.track
    val transition = rememberInfiniteTransition(label = "spinner")
    val head by transition.animateFloat(
        0f, 8f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "head",
    )
    Canvas(modifier.size(size)) {
        val ring = this.size.minDimension / 2f * 0.72f
        val r = this.size.minDimension * 0.085f
        for (i in 0 until 8) {
            val behind = ((head - i) % 8 + 8) % 8
            val glow = (1f - behind / 3.5f).coerceIn(0f, 1f)
            val a = i / 8f * 2f * PI.toFloat() - PI.toFloat() / 2f
            val pos = Offset(center.x + ring * cos(a), center.y + ring * sin(a))
            drawCircle(idle, r, pos)
            if (glow > 0f) drawCircle(color.copy(alpha = glow), r, pos)
        }
    }
}

/** Avanzamento come fila di punti. */
@Composable
fun DotProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Xaos.colors.accent,
    trackColor: Color = Xaos.colors.track,
    spacing: Dp = 5.dp,
    radius: Dp = 1.3.dp,
) {
    Canvas(modifier) {
        val step = spacing.toPx()
        val r = radius.toPx()
        val count = ((size.width - 2 * r) / step).toInt().coerceAtLeast(1) + 1
        val startX = (size.width - (count - 1) * step) / 2f
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

/**
 * Cursore a punti trascinabile: la seek bar e il volume. Mentre si trascina
 * mostra la posizione del mouse, e comunica il valore solo al rilascio
 * ([onChangeFinished]) oppure di continuo ([onChange]) se serve.
 */
@Composable
fun DotSlider(
    value: Float,
    modifier: Modifier = Modifier,
    color: Color = Xaos.colors.ink,
    trackColor: Color = Xaos.colors.track,
    thumbColor: Color = Xaos.colors.accent,
    spacing: Dp = 5.dp,
    radius: Dp = 1.4.dp,
    onChange: ((Float) -> Unit)? = null,
    onChangeFinished: ((Float) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else value.coerceIn(0f, 1f)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .height(18.dp)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val v = (pos.x / size.width).coerceIn(0f, 1f)
                    onChange?.invoke(v)
                    onChangeFinished?.invoke(v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        dragging = true
                        dragValue = (pos.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onChangeFinished?.invoke(dragValue)
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    onChange?.invoke(dragValue)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        DotProgressLine(
            progress = shown,
            color = color,
            trackColor = trackColor,
            spacing = spacing,
            radius = radius,
            modifier = Modifier.fillMaxWidth().height(18.dp),
        )
        // Il pallino appare solo quando serve: al passaggio del mouse o mentre
        // si trascina, come nei player desktop.
        if (hovered || dragging) {
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                drawCircle(thumbColor, 6.dp.toPx(), Offset(size.width * shown, size.height / 2f))
            }
        }
    }
}
