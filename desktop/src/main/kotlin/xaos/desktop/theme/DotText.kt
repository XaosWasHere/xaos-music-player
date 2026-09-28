package xaos.desktop.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.Normalizer

/**
 * Testo a matrice di punti 5×7, come il carattere NDot di Nothing.
 *
 * Il font vero esiste solo sui telefoni Nothing; qui ogni lettera è una griglia
 * di cinque colonne per sette righe, disegnata punto per punto. Le lettere
 * accentate perdono l'accento (Ù diventa U): a sette righe non c'è spazio per
 * disegnarlo, ed è quello che fanno anche i display veri.
 *
 * [pitch] è la distanza fra due punti: l'altezza di una riga è sette volte tanto.
 */
@Composable
fun DotText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    pitch: Dp = 5.dp,
    dimColor: Color = Color.Transparent,
) {
    val glyphs = remember(text) { layout(text) }
    val columns = glyphs.sumOf { GLYPH_COLS + 1 }.coerceAtLeast(1) - 1
    Canvas(modifier = modifier.size(pitch * columns, pitch * GLYPH_ROWS)) {
        val p = pitch.toPx()
        val r = p * DOT_RADIUS
        var x0 = 0f
        for (glyph in glyphs) {
            for (row in 0 until GLYPH_ROWS) {
                val bits = glyph[row]
                for (col in 0 until GLYPH_COLS) {
                    val on = bits[col] == '1'
                    val c = if (on) color else dimColor
                    if (c.alpha > 0f) {
                        drawCircle(c, r, Offset(x0 + p * (col + 0.5f), p * (row + 0.5f)))
                    }
                }
            }
            x0 += p * (GLYPH_COLS + 1)
        }
    }
}

private fun layout(text: String): List<List<String>> {
    val plain = Normalizer.normalize(text.uppercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return plain.map { GLYPHS[it] ?: GLYPHS['?']!! }
}

private const val GLYPH_COLS = 5
private const val GLYPH_ROWS = 7
private const val DOT_RADIUS = 0.36f

private fun g(vararg rows: String) = rows.toList()

private val GLYPHS: Map<Char, List<String>> = mapOf(
    'A' to g("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'B' to g("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'C' to g("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
    'D' to g("11100", "10010", "10001", "10001", "10001", "10010", "11100"),
    'E' to g("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'F' to g("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'G' to g("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
    'H' to g("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to g("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
    'J' to g("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
    'K' to g("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'L' to g("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to g("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to g("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
    'O' to g("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'P' to g("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'Q' to g("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    'R' to g("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to g("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to g("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to g("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'V' to g("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'W' to g("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
    'X' to g("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    'Y' to g("10001", "10001", "10001", "01010", "00100", "00100", "00100"),
    'Z' to g("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
    '0' to g("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to g("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to g("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to g("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
    '4' to g("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to g("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to g("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to g("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to g("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to g("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    ' ' to g("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
    '.' to g("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    ',' to g("00000", "00000", "00000", "00000", "01100", "00100", "01000"),
    ':' to g("00000", "01100", "01100", "00000", "01100", "01100", "00000"),
    '-' to g("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
    '/' to g("00000", "00001", "00010", "00100", "01000", "10000", "00000"),
    '\'' to g("01100", "00100", "01000", "00000", "00000", "00000", "00000"),
    '!' to g("00100", "00100", "00100", "00100", "00100", "00000", "00100"),
    '?' to g("01110", "10001", "00001", "00010", "00100", "00000", "00100"),
    '+' to g("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
    '%' to g("11000", "11001", "00010", "00100", "01000", "10011", "00011"),
    '(' to g("00010", "00100", "01000", "01000", "01000", "00100", "00010"),
    ')' to g("01000", "00100", "00010", "00010", "00010", "00100", "01000"),
    '[' to g("01110", "01000", "01000", "01000", "01000", "01000", "01110"),
    ']' to g("01110", "00010", "00010", "00010", "00010", "00010", "01110"),
    '&' to g("01100", "10010", "10100", "01000", "10101", "10010", "01101"),
    '·' to g("00000", "00000", "00000", "00100", "00000", "00000", "00000"),
)
