package xaos.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeat
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xaos.desktop.FullscreenBackground
import xaos.desktop.library.ArtColorExtractor
import xaos.desktop.library.ArtColors
import xaos.desktop.library.Track
import xaos.desktop.library.rememberArtwork
import xaos.desktop.player.Player
import xaos.desktop.player.RepeatMode
import xaos.desktop.theme.Xaos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Il player a tutto schermo.
 *
 * Tre fasce: in alto la testata (chiudi, da dove arriva il brano, sfondo); al
 * centro copertina grande e, accanto, titolo, artista e i prossimi brani; in
 * basso avanzamento e comandi, sempre sul fondo pulito perché devono restare
 * leggibili qualunque sia lo sfondo. Dietro, la matrice di punti accesa dalla
 * copertina, come sul telefono.
 */
@Composable
fun FullscreenPlayer(
    player: Player,
    background: FullscreenBackground,
    onBackgroundChange: (FullscreenBackground) -> Unit,
    onClose: () -> Unit,
) {
    val colors = Xaos.colors
    val current by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val shuffle by player.shuffle.collectAsState()
    val repeat by player.repeat.collectAsState()
    val volume by player.volume.collectAsState()
    val upNext by player.upNext.collectAsState()

    val track = current
    val artColors by produceState(ArtColors.Fallback, track?.path) {
        value = track?.let { ArtColorExtractor.colors(it) } ?: ArtColors.Fallback
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (background) {
            FullscreenBackground.ARTWORK -> ArtworkWash(track, colors.background)
            FullscreenBackground.OFF -> Box(Modifier.fillMaxSize().dotGrid(colors.dot.copy(alpha = colors.dot.alpha * 0.6f), spacing = 20.dp))
            else -> DotMatrix(
                colors = artColors,
                animated = background == FullscreenBackground.ANIMATED,
                isPlaying = isPlaying,
                baseDot = colors.dot,
                backdrop = colors.background,
                mono = if (colors.isDark) null else colors.ink,
            )
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp)) {
            // ---- testata
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(XaosIcons.ChevronDown, "Chiudi (Esc)", onClose, size = 44.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("IN RIPRODUZIONE DA", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
                    Text(
                        track?.album?.uppercase() ?: "—",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Lo sfondo si sceglie qui, fra le quattro modalità.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FullscreenBackground.entries.forEach { mode ->
                        CircleIconButton(
                            icon = mode.icon,
                            contentDescription = "Sfondo: ${mode.label.lowercase()}",
                            onClick = { onBackgroundChange(mode) },
                            size = 36.dp,
                            filled = mode == background,
                        )
                    }
                }
            }

            // ---- copertina e informazioni
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val art = minOf(maxHeight * 0.86f, maxWidth * 0.42f)
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(track, size = art, corner = 28.dp)
                    Spacer(Modifier.width(56.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccentDot(size = 7.dp, color = if (isPlaying) colors.accent else colors.inkTertiary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (isPlaying) "IN RIPRODUZIONE" else "IN PAUSA",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.inkSecondary,
                            )
                        }
                        Text(
                            track?.title ?: "Niente in riproduzione",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 44.sp, lineHeight = 52.sp),
                            color = colors.ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track?.artist ?: "",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                            color = if (colors.isDark) colors.accentInk else colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (track != null) {
                            Text(
                                listOfNotNull(track.album, track.year.takeIf { it > 0 }?.toString())
                                    .joinToString(" · ").uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.inkTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (upNext.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            UpNext(upNext)
                        }
                    }
                }
            }

            // ---- avanzamento e comandi
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(position), style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, modifier = Modifier.width(64.dp))
                DotSlider(
                    value = if (duration > 0) position.toFloat() / duration else 0f,
                    modifier = Modifier.weight(1f),
                    color = colors.ink,
                    spacing = 7.dp,
                    radius = 2.dp,
                    onChangeFinished = { player.seekTo(it) },
                )
                Text(
                    formatDuration(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkSecondary,
                    modifier = Modifier.width(64.dp).padding(start = 14.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    BigModeButton(XaosIcons.Shuffle, "Casuale", shuffle) { player.toggleShuffle() }
                    CircleIconButton(XaosIcons.Previous, "Precedente", { player.previous() }, size = 56.dp, outlined = false)
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(colors.accent, CircleShape).pressable { player.togglePlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) XaosIcons.Pause else XaosIcons.Play,
                            if (isPlaying) "Pausa (spazio)" else "Riproduci (spazio)",
                            tint = colors.onAccent,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    CircleIconButton(XaosIcons.Next, "Successivo", { player.next() }, size = 56.dp, outlined = false)
                    BigModeButton(
                        if (repeat == RepeatMode.ONE) XaosIcons.RepeatOne else XaosIcons.Repeat,
                        "Ripeti",
                        repeat != RepeatMode.OFF,
                    ) { player.cycleRepeat() }
                }
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (volume == 0) XaosIcons.VolumeOff else XaosIcons.Volume, "Volume", tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    DotSlider(
                        value = volume / 100f,
                        modifier = Modifier.width(150.dp),
                        color = colors.ink,
                        onChange = { player.setVolume((it * 100).toInt()) },
                        onScroll = { dy -> player.setVolume(volume - (dy * VOLUME_STEP).toInt()) },
                    )
                }
                Text(
                    "ESC CHIUDE · SPAZIO PLAY/PAUSA · ← → 10 SECONDI",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkTertiary,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}

/** Punti di volume per ogni scatto della rotella. */
private const val VOLUME_STEP = 5

private val FullscreenBackground.icon: ImageVector
    get() = when (this) {
        FullscreenBackground.ANIMATED -> XaosIcons.GlowAnimated
        FullscreenBackground.STATIC -> XaosIcons.GlowStatic
        FullscreenBackground.ARTWORK -> XaosIcons.GlowArtwork
        FullscreenBackground.OFF -> XaosIcons.GlowOff
    }

@Composable
private fun UpNext(tracks: List<Track>) {
    val colors = Xaos.colors
    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("A SEGUIRE", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
        tracks.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(t, size = 36.dp, corner = 6.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BigModeButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val colors = Xaos.colors
    val tint by animateColorAsState(if (active) colors.ink else colors.inkTertiary, label = "fs-mode")
    Box(Modifier.size(48.dp).clip(CircleShape).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(24.dp))
        if (active) AccentDot(size = 5.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp))
    }
}

/** La copertina a tutto schermo, velata e sfumata nel fondo in alto e in basso. */
@Composable
private fun ArtworkWash(track: Track?, backdrop: Color) {
    val image by rememberArtwork(track, 640)
    val bitmap = image ?: return
    Image(
        bitmap,
        null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().drawWithContent {
            drawContent()
            drawRect(backdrop.copy(alpha = 0.45f))
            drawRect(
                Brush.verticalGradient(
                    0f to backdrop,
                    0.18f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.78f to backdrop,
                    1f to backdrop,
                ),
            )
        },
    )
}

/**
 * La matrice a mezzatinta del telefono: tre macchie di colore che derivano
 * lentamente, campionate su una griglia di punti che crescono dove la luce è
 * forte. Sul PC non c'è l'analisi dell'audio, quindi "animata" respira da
 * sola; "fissa" resta ferma. Sul tema chiaro i punti sono in inchiostro.
 */
@Composable
private fun DotMatrix(
    colors: ArtColors,
    animated: Boolean,
    isPlaying: Boolean,
    baseDot: Color,
    backdrop: Color,
    mono: Color?,
) {
    val transition = rememberInfiniteTransition(label = "fs-matrix")
    val drift by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "drift",
    )
    val breath by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4_800, easing = LinearEasing), AnimRepeat.Reverse),
        label = "breath",
    )
    val phase = if (animated) drift else 1.1f
    val energy = when {
        !animated -> 0.5f
        isPlaying -> 0.35f + breath * 0.35f
        else -> 0.2f
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val base = (w + h) * 0.30f
        val blobs = listOf(
            Triple(colors.primary, Offset(w * (0.30f + 0.12f * cos(phase)), h * (0.45f + 0.10f * sin(phase))), base * (1f + energy * 0.4f)),
            Triple(colors.secondary, Offset(w * (0.62f + 0.16f * cos(phase + 2.2f)), h * (0.55f + 0.12f * sin(phase + 2.2f))), base * 1.15f),
            Triple(colors.accent, Offset(w * (0.45f + 0.20f * cos(phase + 4.4f)), h * (0.30f + 0.14f * sin(phase * 1.3f + 4.4f))), base * 0.8f),
        )
        val alphas = listOf(0.30f + energy * 0.5f, 0.30f + energy * 0.35f, 0.16f + energy * 0.3f)
        drawMatrix(blobs, alphas, baseDot, backdrop, mono)
    }
}

private fun DrawScope.drawMatrix(
    blobs: List<Triple<Color, Offset, Float>>,
    alphas: List<Float>,
    baseDot: Color,
    backdrop: Color,
    mono: Color?,
) {
    // Punti radi e piccoli: la matrice deve fare da atmosfera, non da trama
    // sotto il titolo. Anche al massimo un punto occupa meno di un quinto del passo.
    val step = 20.dp.toPx().let { kotlin.math.round(it) }
    val minR = 0.6.dp.toPx()
    val maxR = step * 0.17f
    val startX = kotlin.math.floor((size.width % step) / 2f + step / 2f)
    // La matrice si spegne verso il basso: i comandi stanno sul fondo pulito.
    val fadeFrom = size.height * 0.62f
    val fadeTo = size.height * 0.80f
    var y = kotlin.math.floor(step / 2f)
    while (y < size.height) {
        val t = ((y - fadeFrom) / (fadeTo - fadeFrom)).coerceIn(0f, 1f)
        val mask = 1f - t * t * (3f - 2f * t)
        if (mask <= 0.01f) break
        var x = startX
        while (x < size.width) {
            var weight = 0f
            var r = 0f; var g = 0f; var b = 0f
            blobs.forEachIndexed { i, (color, center, radius) ->
                val dx = x - center.x
                val dy = y - center.y
                val d = sqrt(dx * dx + dy * dy) / radius
                if (d < 1f) {
                    val f = alphas[i] * (1f - d).pow(1.6f)
                    weight += f
                    r += color.red * f; g += color.green * f; b += color.blue * f
                }
            }
            val intensity = (weight * 1.25f * mask).coerceIn(0f, 1f)
            val center = Offset(x, y)
            if (intensity < 0.06f) {
                drawCircle(baseDot.copy(alpha = baseDot.alpha * 0.6f * mask), minR, center)
            } else {
                val radius = minR + (maxR - minR) * intensity
                val color = mono?.copy(alpha = 0.05f + intensity * 0.16f)
                    ?: Color(r / weight, g / weight, b / weight, alpha = 0.08f + intensity * 0.24f)
                drawCircle(color, radius, center)
            }
            x += step
        }
        y += step
    }
    // Un velo sul fondo, sotto la matrice, per staccare i comandi.
    drawRect(Brush.verticalGradient(0.7f to Color.Transparent, 1f to backdrop.copy(alpha = 0.6f)))
}
