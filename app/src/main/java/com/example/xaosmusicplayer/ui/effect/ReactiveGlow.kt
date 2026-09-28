package com.example.xaosmusicplayer.ui.effect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.audio.AudioLevels
import com.example.xaosmusicplayer.data.GlowMode
import com.example.xaosmusicplayer.ui.theme.Xaos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lo sfondo della schermata di riproduzione: una matrice di punti accesa dalla
 * copertina.
 *
 * Sotto c'è lo stesso campo di prima — tre macchie di colore prese dall'artwork
 * che derivano lentamente, gonfiate dai bassi e rese più opache dall'ampiezza —
 * ma invece di dipingerlo come una sfumatura continua lo si campiona su una
 * griglia: ogni punto prende il colore del campo nel suo centro e diventa tanto
 * più grande quanto più il campo è intenso. È una mezzatinta, come un pannello a
 * LED: dove la luce è forte i punti quasi si toccano, dove si spegne restano i
 * puntini della griglia di sempre.
 *
 * Sotto i punti resta una velatura molto tenue della sfumatura originale, così
 * l'insieme ha profondità invece di sembrare stampato sul fondo.
 *
 * La matrice vive solo nella metà alta, intorno alla copertina, e si spegne
 * prima del titolo: sotto ci sono testo, cursore e comandi, e una trama di punti
 * dietro a cose da leggere e da toccare è solo rumore. [backdrop] è il fondo su
 * cui sfuma la velatura.
 *
 * Va messo come primo elemento di un Box, sotto il contenuto.
 */
@Composable
fun ReactiveGlow(
    colors: ArtworkColors,
    levels: AudioLevels,
    isPlaying: Boolean,
    mode: GlowMode,
    backdrop: Color,
    modifier: Modifier = Modifier,
) {
    val palette = Xaos.colors
    val baseDot = palette.dot
    // Sul chiaro i punti colorati sul grigio pesano molto più che sul nero:
    // stessa matrice, meno inchiostro.
    val strength = if (palette.isDark) 1f else LIGHT_STRENGTH
    // Sul chiaro la mezzatinta è monocroma, in inchiostro: punti colorati su un
    // fondo grigio si leggono come sporco, punti grafite come una stampa.
    val mono = if (palette.isDark) null else palette.ink

    // Senza effetto resta la griglia nuda, con lo stesso passo e la stessa
    // sfumatura della matrice: cambiando modalità i punti restano dove sono e
    // cambia solo la luce.
    if (mode == GlowMode.OFF) {
        Canvas(modifier = modifier) {
            drawMatrix(emptyArray(), baseDot, strength = strength, mono = mono, sparkle = 0f, seed = 0)
        }
        return
    }

    val animated = mode == GlowMode.ANIMATED

    // Deriva lenta: un giro completo ogni 18 secondi, sfasato per ogni macchia.
    // In modalità fissa la fase resta congelata su un valore arbitrario ma
    // stabile, così le macchie restano dove sono senza ruotare.
    val drift = rememberInfiniteTransition(label = "glow-drift")
    val animatedPhase by drift.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(DRIFT_PERIOD_MS, easing = LinearEasing),
        ),
        label = "phase",
    )

    // Respiro di riserva, usato quando il Visualizer non è disponibile.
    val breath by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val phase = if (animated) animatedPhase else STATIC_PHASE
    val useAudio = animated && isPlaying
    // In modalità fissa niente respiro: un valore costante, altrimenti la
    // matrice "ferma" continuerebbe comunque a pulsare.
    val rawEnergy = when {
        useAudio -> levels.amplitude
        animated -> breath * IDLE_ENERGY
        else -> STATIC_ENERGY
    }
    val rawImpact = when {
        useAudio -> levels.bass
        animated -> breath * IDLE_ENERGY
        else -> STATIC_ENERGY
    }

    // Molla sull'energia: assorbe i gradini fra un frame FFT e l'altro senza
    // introdurre il ritardo che darebbe un tween.
    val energy by animateFloatAsState(
        targetValue = rawEnergy,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 340f),
        label = "energy",
    )
    val impact by animateFloatAsState(
        targetValue = rawImpact,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "impact",
    )
    val treble by animateFloatAsState(
        targetValue = if (useAudio) levels.treble else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "treble",
    )

    // In pausa la matrice non si spegne del tutto: si abbassa a un fondo tenue.
    // Da fermo la presenza è piena a prescindere: "fisso" vuol dire immobile,
    // non che si spegne mettendo in pausa.
    val presence by animateFloatAsState(
        targetValue = if (isPlaying || !animated) 1f else 0.35f,
        animationSpec = tween(900),
        label = "presence",
    )

    val midLevel = if (useAudio) levels.mid else STATIC_ENERGY
    // Gli scintillii sul tema chiaro diventano puntini sparsi a caso: li teniamo
    // solo sullo scuro, dove si leggono come luce.
    val sparkle = if (palette.isDark) treble * presence else 0f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Il raggio base è tarato sulla diagonale, così l'effetto tiene su
        // qualunque densità e formato di schermo.
        val baseRadius = (w + h) * 0.32f

        val blobs = arrayOf(
            Blob(
                color = colors.primary,
                center = Offset(
                    x = w * (0.5f + 0.18f * cos(phase)),
                    y = h * (0.34f + 0.10f * sin(phase)),
                ),
                // Il fondo costante è basso e la quota guidata dall'audio è alta:
                // è questo rapporto, più del guadagno assoluto, a far leggere
                // l'effetto come reattivo.
                radius = baseRadius * (1f + impact * 0.62f),
                alpha = (0.24f + energy * 0.56f) * presence,
            ),
            Blob(
                color = colors.secondary,
                center = Offset(
                    x = w * (0.5f + 0.22f * cos(phase + PHASE_OFFSET_2)),
                    y = h * (0.58f + 0.14f * sin(phase + PHASE_OFFSET_2)),
                ),
                radius = baseRadius * (1.15f + impact * 0.42f),
                alpha = (0.28f + energy * 0.42f) * presence,
            ),
            // La terza macchia è la più mobile e reagisce ai medi: è quella che
            // dà la sensazione di "vivo" senza sfarfallare sui transienti acuti.
            Blob(
                color = colors.accent,
                center = Offset(
                    x = w * (0.5f + 0.26f * cos(phase + PHASE_OFFSET_3)),
                    y = h * (0.30f + 0.18f * sin(phase * 1.3f + PHASE_OFFSET_3)),
                ),
                radius = baseRadius * (0.72f + midLevel * 0.54f),
                alpha = (0.13f + energy * 0.35f) * presence,
            ),
        )

        // La velatura: la sfumatura continua, molto attenuata, che poi si
        // richiude sul fondo prima della zona dei comandi.
        blobs.forEach { drawBlob(it, alphaScale = HAZE_ALPHA * strength, canvasSize = size) }
        drawRect(
            brush = Brush.verticalGradient(
                MASK_START to Color.Transparent,
                MASK_END to backdrop,
                startY = 0f,
                endY = h,
            ),
        )

        drawMatrix(
            blobs,
            baseDot,
            strength = strength,
            mono = mono,
            sparkle = sparkle,
            seed = (phase * GLINT_RATE).toInt(),
        )
    }
}

private class Blob(
    val color: Color,
    val center: Offset,
    val radius: Float,
    val alpha: Float,
)

/**
 * La mezzatinta. Per ogni punto si somma il contributo delle tre macchie; il
 * colore è la loro media pesata, la dimensione segue l'intensità totale.
 */
private fun DrawScope.drawMatrix(
    blobs: Array<Blob>,
    baseDot: Color,
    strength: Float,
    mono: Color?,
    sparkle: Float,
    seed: Int,
) {
    val step = MATRIX_SPACING.toPx()
    val minR = BASE_RADIUS.toPx()
    val maxR = step * MAX_FILL / 2f
    val startX = (size.width % step) / 2f + step / 2f
    val startY = step / 2f

    val fadeFrom = size.height * MASK_START
    val fadeTo = size.height * MASK_END

    var row = 0
    var y = startY
    while (y < size.height) {
        // Quanto la riga appartiene alla zona della matrice: 1 in alto, 0 dalla
        // zona del titolo in giù, con una discesa morbida in mezzo.
        val mask = 1f - smoothstep(fadeFrom, fadeTo, y)
        if (mask <= 0.01f) break
        var col = 0
        var x = startX
        while (x < size.width) {
            var weight = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            for (blob in blobs) {
                if (blob.radius <= 0f || blob.alpha <= 0.001f) continue
                val dx = x - blob.center.x
                val dy = y - blob.center.y
                val t = sqrt(dx * dx + dy * dy) / blob.radius
                if (t >= 1f) continue
                // Stessa caduta degli stop della sfumatura: piena al centro,
                // poi scende dolce fino a zero sul bordo.
                val f = blob.alpha * (1f - t).pow(FALLOFF)
                weight += f
                r += blob.color.red * f
                g += blob.color.green * f
                b += blob.color.blue * f
            }

            val intensity = (weight * GAIN * mask).coerceIn(0f, 1f)
            val center = Offset(x, y)
            if (intensity < 0.06f) {
                drawCircle(baseDot.copy(alpha = baseDot.alpha * mask), minR, center)
            } else {
                // Gli acuti accendono qualche punto qua e là. L'estrazione cambia
                // qualche volta al secondo, non a ogni frame: un luccichio, non
                // un rumore.
                val glint = if (sparkle > 0.05f && hash(col, row, seed) < sparkle * 0.18f) 0.35f else 0f
                val level = (intensity + glint).coerceAtMost(1f)
                val radius = minR + (maxR - minR) * level
                val color = if (mono != null) {
                    mono.copy(alpha = (MONO_ALPHA_MIN + level * MONO_ALPHA_SPAN) * strength)
                } else {
                    Color(
                        r / weight, g / weight, b / weight,
                        alpha = (ALPHA_MIN + level * ALPHA_SPAN) * strength,
                    )
                }
                drawCircle(color, radius, center)
            }
            x += step
            col++
        }
        y += step
        row++
    }
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Un numero pseudo-casuale stabile per punto e istante, fra 0 e 1. */
private fun hash(col: Int, row: Int, seed: Int): Float {
    var h = col * 374761393 + row * 668265263 + seed * 144269504
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

/**
 * Una macchia di luce: gradiente radiale che sfuma a trasparente, non a nero.
 * Sfumare a trasparente permette alle macchie di sommarsi invece di coprirsi.
 */
private fun DrawScope.drawBlob(blob: Blob, alphaScale: Float, canvasSize: Size) {
    val alpha = blob.alpha * alphaScale
    if (blob.radius <= 0f || alpha <= 0.001f) return
    drawRect(
        brush = Brush.radialGradient(
            // Il centro resta pieno per un terzo del raggio, poi cade dolcemente:
            // senza questa sosta il gradiente sembra un cerchio netto.
            colorStops = arrayOf(
                0.0f to blob.color.copy(alpha = alpha),
                0.35f to blob.color.copy(alpha = alpha * 0.55f),
                0.70f to blob.color.copy(alpha = alpha * 0.16f),
                1.0f to Color.Transparent,
            ),
            center = blob.center,
            radius = blob.radius,
        ),
        size = canvasSize,
    )
}

private const val DRIFT_PERIOD_MS = 18_000
private const val BREATH_PERIOD_MS = 4_200

/** Sfasature scelte per tenere le tre macchie sempre distanti fra loro. */
private const val PHASE_OFFSET_2 = 2.2f
private const val PHASE_OFFSET_3 = 4.4f

/** Quanta energia simulare quando l'audio non è leggibile. */
private const val IDLE_ENERGY = 0.45f

/** Fase e intensità congelate della modalità "matrice fissa". */
private const val STATIC_PHASE = 1.1f
private const val STATIC_ENERGY = 0.5f

/** Passo della matrice: poco più fitto della griglia delle altre schermate. */
private val MATRIX_SPACING = 13.dp
private val BASE_RADIUS = 0.9.dp

/** Quanto del passo può occupare un punto acceso: a 1 si toccherebbero. */
private const val MAX_FILL = 0.5f

/** Curvatura della caduta di luce dal centro di una macchia al bordo. */
private const val FALLOFF = 1.6f

/** Amplifica l'intensità: le macchie da sole restano sotto 1 anche al centro. */
private const val GAIN = 1.25f

/** Opacità dei punti accesi: tenui anche al massimo, sono uno sfondo. */
private const val ALPHA_MIN = 0.16f
private const val ALPHA_SPAN = 0.36f

/** Lo stesso per la mezzatinta monocroma del tema chiaro. */
private const val MONO_ALPHA_MIN = 0.10f
private const val MONO_ALPHA_SPAN = 0.30f

/** Quante estrazioni di luccichii per radiante di deriva: circa sei al secondo. */
private const val GLINT_RATE = 18f

/**
 * La fascia in cui la matrice si spegne, in frazioni d'altezza: comincia a metà
 * copertina e finisce dove comincia il titolo.
 */
private const val MASK_START = 0.34f
private const val MASK_END = 0.54f

/** Quanto inchiostro dare alla matrice sul tema chiaro rispetto allo scuro. */
private const val LIGHT_STRENGTH = 0.75f

/** Opacità della sfumatura continua sotto i punti. */
private const val HAZE_ALPHA = 0.28f
