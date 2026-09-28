package com.example.xaosmusicplayer.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * I caratteri di Nothing OS, presi dal sistema invece che spediti nell'APK.
 *
 * Sui telefoni Nothing stanno in /system/fonts e sono leggibili da qualunque app,
 * ma non sono registrati come famiglie: per nome non si trovano, per percorso sì.
 * Su un altro telefono i file non ci sono e ogni famiglia ricade sul monospace di
 * sistema, che è l'aspetto che l'app aveva prima.
 */
object XaosFonts {

    /** La matrice di punti 5×7: titoli di schermata e numeri. */
    val Dot: FontFamily by lazy { systemFont("NDot57Caps.otf") ?: FontFamily.Monospace }

    /** Il grottesco di Nothing: titoli di brani, album e schermate secondarie. */
    val Headline: FontFamily by lazy {
        systemFont("NType82-Headline.otf") ?: FontFamily.Monospace
    }
    val HeadlineRegular: FontFamily by lazy {
        systemFont("NType82-Regular.otf") ?: FontFamily.Monospace
    }

    /** Il monospace di sistema di Nothing OS: tutto il resto. */
    val Mono: FontFamily by lazy {
        val regular = systemFile("LetteraMonoLL-Regular.otf")
        if (regular == null) {
            FontFamily.Monospace
        } else {
            val light = systemFile("LetteraMonoLL-Light.otf") ?: regular
            val medium = systemFile("LetteraMonoLL-Medium.otf") ?: regular
            FontFamily(
                Font(light, FontWeight.Light),
                Font(regular, FontWeight.Normal),
                Font(medium, FontWeight.Medium),
                // Il "grassetto" di questa famiglia è il medium: un bold sintetico
                // su un mono sottile impasta le lettere.
                Font(medium, FontWeight.Bold),
            )
        }
    }

    private fun systemFile(name: String): File? =
        File(SYSTEM_FONTS, name).takeIf { it.canRead() }

    private fun systemTypeface(name: String): Typeface? {
        val file = systemFile(name) ?: return null
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }

    private fun systemFont(name: String): FontFamily? =
        systemTypeface(name)?.let { FontFamily(it) }

    private const val SYSTEM_FONTS = "/system/fonts"
}

val Typography: Typography by lazy { buildTypography() }

private fun buildTypography(): Typography {
    val dot = XaosFonts.Dot
    val headline = XaosFonts.Headline
    val mono = XaosFonts.Mono

    return Typography(
        // "HOME", "LIBRERIA", "CERCA": la matrice di punti in grande.
        displaySmall = TextStyle(
            fontFamily = dot,
            fontWeight = FontWeight.Normal,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = 1.sp,
        ),
        // I numeri delle statistiche.
        headlineLarge = TextStyle(
            fontFamily = dot,
            fontWeight = FontWeight.Normal,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            letterSpacing = 1.sp,
        ),
        // Titolo del brano nella schermata di riproduzione.
        headlineMedium = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.Normal,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        ),
        // Titoli delle schermate secondarie, artista nel player.
        titleLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.4.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.3.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.3.sp,
        ),
        // Etichette tecniche: intestazioni di sezione, durate, conteggi.
        labelLarge = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.4.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 1.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            letterSpacing = 1.2.sp,
        ),
    )
}
