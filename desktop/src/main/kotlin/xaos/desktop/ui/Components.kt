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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xaos.desktop.theme.Xaos
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/*
 * I mattoni dell'aspetto Nothing, gli stessi dell'app Android: griglia di
 * puntini, card velate con il filetto, pillole, controlli a punti. In più, qui,
 * il puntatore a manina e gli stati di hover che su un PC ci si aspetta.
 */

/**
 * La griglia di puntini, allineata ai pixel dello schermo.
 *
 * Passo e diametro sono arrotondati a pixel interi e ogni centro cade sempre
 * nello stesso punto di un pixel. Senza, un puntino a cavallo fra due pixel
 * viene sfumato dall'antialiasing e sembra più tenue degli altri: con lo
 * zoom di Windows, o semplicemente con la larghezza della finestra, i puntini
 * "mezzi" si alternano a quelli pieni e compare una trama a scacchiera.
 */
fun Modifier.dotGrid(color: Color, spacing: Dp = 16.dp, radius: Dp = 0.5.dp): Modifier =
    drawWithCache {
        val step = spacing.toPx().roundToInt().coerceAtLeast(2).toFloat()
        val diameter = (radius.toPx() * 2f).roundToInt().coerceAtLeast(1)
        // Diametro pari: centro sul bordo fra pixel; dispari: al centro del pixel.
        val phase = if (diameter % 2 == 0) 0f else 0.5f
        // Fuori da buildList: lì dentro `size` sarebbe quella della lista.
        val width = size.width
        val height = size.height
        val points = buildList {
            val startX = floor((width % step) / 2f + step / 2f) + phase
            val startY = floor(step / 2f) + phase
            var y = startY
            while (y < height) {
                var x = startX
                while (x < width) {
                    add(Offset(x, y)); x += step
                }
                y += step
            }
        }
        val stroke = diameter.toFloat()
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
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    /** La rotella del mouse sopra il cursore: riceve di quanto è girata (su = negativo). */
    onScroll: ((Float) -> Unit)? = null,
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
            .then(
                if (onScroll == null) Modifier
                else Modifier.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Scroll) { event ->
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) {
                        onScroll(dy)
                        event.changes.forEach { it.consume() }
                    }
                }
            )
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

/** Stato di una casella: spuntata, vuota, o in parte (un album con solo alcuni brani). */
enum class Check { ON, OFF, PARTIAL }

/**
 * Casella di spunta: quadratino arrotondato, pieno nel colore d'accento
 * quando è spuntata, con un trattino quando lo è solo in parte.
 */
@Composable
fun XaosCheckbox(state: Check, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val c = Xaos.colors
    val shape = RoundedCornerShape(6.dp)
    val filled = state != Check.OFF
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (filled) c.accent else Color.Transparent, shape)
            .then(if (filled) Modifier else Modifier.border(1.5.dp, c.inkTertiary, shape))
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            Check.ON -> Icon(XaosIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(size * 0.75f))
            Check.PARTIAL -> Box(Modifier.size(width = size * 0.5f, height = 2.dp).background(c.onAccent, RoundedCornerShape(1.dp)))
            Check.OFF -> Unit
        }
    }
}

/** Interruttore a pillola: il pallino scorre e la pista si riempie d'accento. */
@Composable
fun XaosSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Xaos.colors
    val knob by androidx.compose.animation.core.animateDpAsState(if (checked) 20.dp else 2.dp, label = "switch")
    val track by androidx.compose.animation.animateColorAsState(if (checked) c.accent else c.card, label = "switch-track")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(shape)
            .background(track, shape)
            .border(1.dp, if (checked) Color.Transparent else c.line, shape)
            .pressable { onChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(start = knob, top = 2.dp)
                .size(20.dp)
                .background(if (checked) c.onAccent else c.inkTertiary, CircleShape),
        )
    }
}

/**
 * Cursore verticale a punti, per le bande dell'equalizzatore: una colonna di
 * punti con lo zero al centro, accesi dallo zero fino al valore. Si trascina
 * o si clicca; [range] è simmetrico attorno allo zero.
 */
@Composable
fun VerticalDotSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: Float = 20f,
    enabled: Boolean = true,
) {
    val c = Xaos.colors
    val lit = if (enabled) c.accent else c.inkTertiary
    fun valueAt(y: Float, height: Float): Float {
        val f = 1f - (y / height).coerceIn(0f, 1f)
        // Aggancio al decibel intero: valori come 3,37 dB non servono a nessuno.
        return (f * 2f * range - range).let { kotlin.math.round(it) }.coerceIn(-range, range)
    }
    Canvas(
        modifier
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onChange(valueAt(it.y, size.height.toFloat())) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange(valueAt(change.position.y, size.height.toFloat()))
                }
            },
    ) {
        val steps = 20
        // Mezzo passo di margine sopra e sotto: i punti estremi restano interi.
        val stepY = size.height / (steps + 1)
        val x = size.width / 2f
        val r = 2.dp.toPx()
        val zeroRow = steps / 2
        val valueRow = ((1f - (value + range) / (2f * range)) * steps).let { kotlin.math.round(it).toInt() }
        for (row in 0..steps) {
            val y = (row + 0.5f) * stepY
            val between = (row in minOf(zeroRow, valueRow)..maxOf(zeroRow, valueRow))
            val color = when {
                row == valueRow -> lit
                between && value != 0f -> lit.copy(alpha = 0.55f)
                row == zeroRow -> c.inkSecondary
                else -> c.track
            }
            drawCircle(color, if (row == valueRow) r * 1.9f else r, Offset(x, y))
        }
    }
}

/** Campo di testo a pillola, con l'etichetta tecnica sopra. */
@Composable
fun XaosTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val c = Xaos.colors
    val shape = RoundedCornerShape(12.dp)
    val focus = remember { MutableInteractionSource() }
    val focused by focus.collectIsFocusedAsState()
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.inkTertiary)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.card, shape)
                .border(1.dp, if (focused) c.accent else c.line, shape)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.inkTertiary)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                interactionSource = focus,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accentInk),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Le azioni su un brano, offerte da ogni elenco tramite il suo menu. */
class TrackActions(
    val onEdit: (xaos.desktop.library.Track) -> Unit,
    val onLyrics: (xaos.desktop.library.Track) -> Unit,
    val onShowInFolder: (xaos.desktop.library.Track) -> Unit,
    val onToggleFavorite: (xaos.desktop.library.Track) -> Unit,
    /** Apre la scelta della playlist per questi brani. */
    val onAddToPlaylist: (List<xaos.desktop.library.Track>) -> Unit,
    val onGoToAlbum: (xaos.desktop.library.Track) -> Unit = {},
    val onGoToArtist: (xaos.desktop.library.Track) -> Unit = {},
)

val LocalTrackActions = androidx.compose.runtime.staticCompositionLocalOf<TrackActions?> { null }

/** I percorsi dei brani preferiti (già risolti sui brani della libreria). */
val LocalFavorites = androidx.compose.runtime.compositionLocalOf<Set<String>> { emptySet() }

/** Il cuore dei preferiti: pieno e d'accento se il brano è fra i preferiti. */
@Composable
fun FavoriteButton(
    track: xaos.desktop.library.Track,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    visible: Boolean = true,
) {
    val c = Xaos.colors
    val actions = LocalTrackActions.current ?: return
    val on = track.path in LocalFavorites.current
    Box(
        modifier.size(size).clip(CircleShape).pressable { actions.onToggleFavorite(track) },
        contentAlignment = Alignment.Center,
    ) {
        if (on || visible) {
            Icon(
                if (on) XaosIcons.Favorite else XaosIcons.FavoriteBorder,
                if (on) "Togli dai preferiti" else "Aggiungi ai preferiti",
                tint = if (on) c.accentInk else c.inkSecondary,
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}
