package com.example.xaosmusicplayer.ui.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * I colori estratti dalla copertina, già scelti per essere usati come luce
 * ambientale su fondo nero.
 */
data class ArtworkColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    /**
     * La media dei pixel della copertina. A differenza degli altri tre non è
     * ritoccata: serve come tinta piatta dietro il testo, dove deve somigliare
     * alla copertina e non illuminarla.
     */
    val background: Color,
) {
    /**
     * Bianco o nero, scelto sul fondo: la luminanza percepita non è la media dei
     * canali — l'occhio pesa molto il verde e quasi nulla il blu — e una soglia
     * sui canali grezzi sbaglierebbe proprio sui gialli e sui blu accesi.
     */
    val onBackground: Color
        get() = if (background.luminance() > 0.45f) Color.Black else Color.White

    companion object {
        /** Grigio molto scuro: quando non c'è copertina il glow resta discreto. */
        val Fallback = ArtworkColors(
            primary = Color(0xFF2A2A35),
            secondary = Color(0xFF15151C),
            accent = Color(0xFF3A3A4A),
            background = Color(0xFF15151C),
        )
    }
}

/**
 * Estrae la palette della copertina, ricalcolandola a ogni cambio di brano.
 *
 * Restituisce [ArtworkColors.Fallback] mentre carica e se l'immagine manca, così
 * il chiamante non deve gestire uno stato nullo.
 */
@Composable
fun rememberArtworkColors(artworkUri: Uri?): State<ArtworkColors> {
    val context = LocalContext.current
    return produceState(initialValue = ArtworkColors.Fallback, artworkUri) {
        value = artworkUri?.let { extractColors(context, it) } ?: ArtworkColors.Fallback
    }
}

private suspend fun extractColors(context: Context, uri: Uri): ArtworkColors =
    withContext(Dispatchers.IO) {
        val bitmap = loadDownsampledBitmap(context, uri) ?: return@withContext ArtworkColors.Fallback

        val palette = runCatching {
            Palette.from(bitmap).clearFilters().maximumColorCount(16).generate()
        }.getOrNull() ?: return@withContext ArtworkColors.Fallback

        val average = bitmap.averageColor()
        bitmap.recycle()

        // Ordine di preferenza: swatch vivaci prima, poi quelli smorzati, così
        // una copertina satura dà un glow acceso e una desaturata resta sobria.
        val primaryArgb = palette.vibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: return@withContext ArtworkColors.Fallback

        val secondaryArgb = palette.darkMutedSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: primaryArgb

        val accentArgb = palette.lightVibrantSwatch?.rgb
            ?: palette.vibrantSwatch?.rgb
            ?: primaryArgb

        ArtworkColors(
            primary = Color(primaryArgb).forGlow(targetLightness = 0.42f),
            secondary = Color(secondaryArgb).forGlow(targetLightness = 0.22f),
            accent = Color(accentArgb).forGlow(targetLightness = 0.55f),
            background = average,
        )
    }

/**
 * La media dei pixel della copertina.
 *
 * Media vera, non il colore dominante: è quella che chiedeva il progetto, ed è
 * anche la più stabile — un dominante può saltare da un brano all'altro dello
 * stesso disco, la media no. Si legge un pixel ogni tanto invece di tutti:
 * su una miniatura di duecento pixel il risultato è indistinguibile e costa
 * un quarto del tempo.
 */
private fun Bitmap.averageColor(): Color {
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
            count++
            x += AVERAGE_STEP
        }
        y += AVERAGE_STEP
    }
    if (count == 0) return ArtworkColors.Fallback.background
    return Color(
        red = (r / count).toInt(),
        green = (g / count).toInt(),
        blue = (b / count).toInt(),
    )
}

/**
 * Carica la copertina ridotta: per estrarre una palette bastano poche centinaia
 * di pixel e decodificare l'immagine intera sprecherebbe memoria a ogni brano.
 */
private fun loadDownsampledBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > PALETTE_SIZE_PX) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}.getOrNull()

/**
 * Normalizza un colore per l'uso come luce: satura un po' e porta la luminosità
 * al valore voluto. Senza questo passaggio le copertine scure danno un glow
 * invisibile e quelle chiarissime lo fanno esplodere in bianco.
 */
private fun Color.forGlow(targetLightness: Float): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.25f).coerceIn(0.25f, 0.95f)
    hsl[2] = targetLightness
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

private const val PALETTE_SIZE_PX = 200

/** Un pixel ogni due per lato, cioè un quarto del totale. */
private const val AVERAGE_STEP = 2
