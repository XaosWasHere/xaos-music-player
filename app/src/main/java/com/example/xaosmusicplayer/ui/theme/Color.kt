package com.example.xaosmusicplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * La scala di grigi e i due colori di marca vengono dal design system di Nothing
 * (le variabili `--nos-*` e `--brand-*` di playground.nothing.tech). Niente tinte
 * inventate: il carattere dell'aspetto sta proprio nel non averne.
 */
private val Nos0 = Color(0xFFFFFFFF)
private val Nos50 = Color(0xFFF2F2F2)
private val Nos100 = Color(0xFFE3E3E3)
private val Nos200 = Color(0xFFC8C8C8)
private val Nos400 = Color(0xFF929292)
private val Nos600 = Color(0xFF606060)
private val Nos800 = Color(0xFF323232)
private val Nos850 = Color(0xFF292929)
private val Nos900 = Color(0xFF1C1C1C)
private val Nos1000 = Color(0xFF000000)

val NothingRed = Color(0xFFD71921)
val NothingYellow = Color(0xFFFFC700)

/**
 * I colori di un tema, per ruolo e non per tinta.
 *
 * L'accento ha due ruoli distinti perché il giallo non regge come testo: su
 * fondo chiaro ha un contrasto di 1,4:1, cioè non si legge. [accent] è quindi
 * riservato a ciò che si riempie — punti, barre, pulsanti pieni, indicatori — e
 * [accentInk] a testo e icone appoggiati sul fondo. Sul tema scuro coincidono.
 */
@Immutable
data class XaosPalette(
    val isDark: Boolean,
    /** Fondo di tutte le schermate. */
    val background: Color,
    /** Card appoggiate sul fondo: semitrasparenti, i puntini si intravedono. */
    val card: Color,
    /** Superfici opache: fogli modali, menu, dialoghi. */
    val surface: Color,
    /** Campi di testo, chip, riga trascinata. */
    val surfaceHigh: Color,
    /** Filetti da 1dp: bordi delle card e separatori. */
    val line: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    /** I puntini della griglia di sfondo. */
    val dot: Color,
    /** Parte non riempita di cursori e barre di avanzamento. */
    val track: Color,
)

val DarkPalette = XaosPalette(
    isDark = true,
    background = Nos1000,
    card = Nos900.copy(alpha = 0.78f),
    surface = Nos900,
    surfaceHigh = Nos850,
    line = Nos800,
    ink = Nos50,
    inkSecondary = Nos400,
    inkTertiary = Nos600,
    accent = NothingRed,
    onAccent = Nos0,
    accentInk = NothingRed,
    dot = Nos0.copy(alpha = 0.13f),
    track = Nos800,
)

val LightPalette = XaosPalette(
    isDark = false,
    background = Nos50,
    card = Nos0.copy(alpha = 0.72f),
    surface = Nos0,
    surfaceHigh = Nos100,
    line = Color(0xFFDADADA),
    ink = Nos900,
    inkSecondary = Nos600,
    inkTertiary = Nos400,
    accent = NothingYellow,
    onAccent = Nos900,
    accentInk = Nos900,
    // Lo stesso valore del sito: rgba(200, 200, 200, 0.4) su #F2F2F2 è poco,
    // su uno schermo in mano serve un filo di più.
    dot = Nos200.copy(alpha = 0.75f),
    track = Nos200,
)

val LocalXaosPalette = staticCompositionLocalOf { DarkPalette }

/** Punto d'accesso ai colori del tema corrente: `Xaos.colors.ink`. */
object Xaos {
    val colors: XaosPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalXaosPalette.current
}
