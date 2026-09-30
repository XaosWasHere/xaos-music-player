package com.example.xaosmusicplayer.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.example.xaosmusicplayer.data.Lyrics
import com.example.xaosmusicplayer.ui.theme.XaosPalette
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Le pagine del widget, disegnate come immagini.
 *
 * Un widget non può usare caratteri propri: le sue viste vivono nel launcher,
 * che conosce solo quelli registrati. Disegnando noi le pagine si usano gli
 * stessi caratteri di Nothing OS dell'app (presi da /system/fonts, come fa
 * l'app) e si ha il quadrato stondato, la griglia di punti e i colori del tema
 * esattamente come nell'app.
 */
internal object WidgetRenderer {

    /** Il raggio degli angoli, in proporzione al lato: quello dei widget di Nothing OS. */
    private const val CORNER = 0.13f

    private val dot: Typeface by lazy { systemTypeface("NDot57Caps.otf") ?: Typeface.MONOSPACE }
    private val headline: Typeface by lazy { systemTypeface("NType82-Headline.otf") ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val mono: Typeface by lazy { systemTypeface("LetteraMonoLL-Regular.otf") ?: Typeface.MONOSPACE }

    private fun systemTypeface(name: String): Typeface? {
        val file = File("/system/fonts", name).takeIf { it.canRead() } ?: return null
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }

    /**
     * La copertina a tutto quadrato. In pausa si vela e compare il play nel
     * colore d'accento; mentre suona c'è solo il punto d'accento in alto.
     */
    fun cover(side: Int, art: Bitmap?, title: String?, playing: Boolean, pages: Int, palette: XaosPalette): Bitmap =
        page(side, palette) { canvas ->
            if (art != null) {
                val s = min(art.width, art.height)
                val src = Rect((art.width - s) / 2, (art.height - s) / 2, (art.width + s) / 2, (art.height + s) / 2)
                canvas.drawBitmap(art, src, RectF(0f, 0f, side.toFloat(), side.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                dotGrid(canvas, side, palette)
                // Senza copertina, il titolo nel carattere a punti.
                val paint = textPaint(dot, palette.inkSecondary.toArgb(), side * 0.085f)
                drawBlock(canvas, (title ?: "XAOS").uppercase(), paint, side * 0.12f, side * 0.5f, side - side * 0.24f, 3, center = true)
            }
            if (playing) {
                // Il punto "in registrazione" di Nothing, con un alone scuro che
                // lo stacca anche da una copertina rossa.
                val r = side * 0.03f
                val cx = side - side * 0.1f
                val cy = side * 0.1f
                canvas.drawCircle(cx, cy, r * 1.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 })
                canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent.toArgb() })
            } else {
                canvas.drawColor(if (art != null) 0x73000000 else 0x00000000)
                val r = side * 0.13f
                val c = side / 2f
                canvas.drawCircle(c, c, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent.toArgb() })
                val t = r * 0.42f
                val path = Path().apply {
                    moveTo(c - t * 0.75f, c - t)
                    lineTo(c + t * 1.05f, c)
                    lineTo(c - t * 0.75f, c + t)
                    close()
                }
                canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.onAccent.toArgb() })
            }
            if (pages > 1) pager(canvas, side, 0, pages, onImage = art != null, palette = palette)
        }

    /**
     * Il testo in sincrono, come nel miniplayer: il verso in corso al centro,
     * grande, con il precedente sopra e il successivo sotto, attenuati. Un
     * verso lungo rimpicciolisce invece di essere tagliato.
     */
    fun lyrics(side: Int, lyrics: Lyrics, index: Int, palette: XaosPalette): Bitmap =
        page(side, palette) { canvas ->
            dotGrid(canvas, side, palette)
            val pad = side * 0.1f
            val width = side - pad * 2
            val label = textPaint(dot, palette.inkTertiary.toArgb(), side * 0.05f)
            canvas.drawText("TESTO", pad, pad + label.textSize * 0.8f, label)

            val previous = lyrics.lines.getOrNull(index - 1)?.text?.takeIf { it.isNotBlank() }
            val current = lyrics.lines.getOrNull(index)?.text?.takeIf { it.isNotBlank() } ?: "♪"
            val next = lyrics.lines.getOrNull(index + 1)?.text?.takeIf { it.isNotBlank() }

            val dim = palette.inkTertiary.toArgb()
            val small = side * 0.058f
            val prevLayout = previous?.let { layout(it, textPaint(mono, dim, small), width, 2) }
            val nextLayout = next?.let { layout(it, textPaint(mono, dim, small), width, 2) }
            // Il verso in corso: dal più grande che entra in quattro righe.
            var size = side * 0.1f
            var currentLayout: StaticLayout
            while (true) {
                currentLayout = layout(current, textPaint(headline, palette.ink.toArgb(), size), width, Int.MAX_VALUE)
                if (currentLayout.lineCount <= 4 || size <= side * 0.055f) break
                size *= 0.92f
            }
            if (currentLayout.lineCount > 4) currentLayout = layout(current, textPaint(headline, palette.ink.toArgb(), size), width, 4)

            val gap = side * 0.045f
            val total = (prevLayout?.height?.plus(gap) ?: 0f).toFloat() + currentLayout.height + (nextLayout?.height?.plus(gap) ?: 0f).toFloat()
            var y = max(pad * 1.6f, (side - total) / 2f + side * 0.03f)
            fun draw(l: StaticLayout) {
                canvas.save()
                canvas.translate(pad, y)
                l.draw(canvas)
                canvas.restore()
                y += l.height + gap
            }
            prevLayout?.let(::draw)
            draw(currentLayout)
            nextLayout?.let(::draw)
            pager(canvas, side, 1, 2, onImage = false, palette = palette)
        }

    /** Niente in riproduzione: il marchio e l'invito ad aprire l'app. */
    fun idle(side: Int, palette: XaosPalette): Bitmap =
        page(side, palette) { canvas ->
            dotGrid(canvas, side, palette)
            val big = textPaint(dot, palette.ink.toArgb(), side * 0.16f).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("XAOS", side / 2f, side * 0.5f, big)
            val small = textPaint(mono, palette.inkTertiary.toArgb(), side * 0.055f).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("TOCCA PER APRIRE", side / 2f, side * 0.66f, small)
            canvas.drawCircle(side * 0.5f, side * 0.25f, side * 0.022f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent.toArgb() })
        }

    // ------------------------------------------------------------------ comuni

    /** Un quadrato stondato col fondo del tema; fuori dagli angoli è trasparente. */
    private inline fun page(side: Int, palette: XaosPalette, content: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = side * CORNER
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), r, r, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        val second = palette.background2
        if (second != null) {
            bg.shader = LinearGradient(0f, 0f, side.toFloat(), side.toFloat(), palette.background.toArgb(), second.toArgb(), Shader.TileMode.CLAMP)
        } else {
            bg.color = palette.background.toArgb()
        }
        canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), bg)
        content(canvas)
        canvas.restore()
        // Il filetto delle card dell'app, per staccarlo da sfondi dello stesso colore.
        val stroke = max(1f, side / 220f)
        canvas.drawRoundRect(
            RectF(stroke / 2, stroke / 2, side - stroke / 2, side - stroke / 2), r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = palette.line.toArgb() },
        )
        return bitmap
    }

    private fun dotGrid(canvas: Canvas, side: Int, palette: XaosPalette) {
        val color = palette.dot.toArgb()
        if (color ushr 24 == 0) return
        val step = side / 26f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val r = max(0.8f, side / 420f)
        var y = step / 2
        while (y < side) {
            var x = step / 2
            while (x < side) {
                canvas.drawCircle(x, y, r, paint)
                x += step
            }
            y += step
        }
    }

    /** I due punti sul bordo destro: dove si è, e che si può scorrere. */
    private fun pager(canvas: Canvas, side: Int, current: Int, count: Int, onImage: Boolean, palette: XaosPalette) {
        val r = side * 0.014f
        val gap = r * 3.2f
        val x = side - side * 0.055f
        val top = side / 2f - (count - 1) * gap / 2f
        val on = if (onImage) 0xFFFFFFFF.toInt() else palette.ink.toArgb()
        val off = if (onImage) 0x80FFFFFF.toInt() else palette.inkTertiary.toArgb()
        for (i in 0 until count) {
            canvas.drawCircle(x, top + i * gap, if (i == current) r * 1.25f else r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (i == current) on else off })
        }
    }

    private fun textPaint(typeface: Typeface, color: Int, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        this.color = color
        textSize = size
    }

    private fun layout(text: String, paint: TextPaint, width: Float, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.05f)
            .build()

    private fun drawBlock(canvas: Canvas, text: String, paint: TextPaint, left: Float, centerY: Float, width: Float, maxLines: Int, center: Boolean) {
        val l = layout(text, paint, width, maxLines)
        canvas.save()
        canvas.translate(left, centerY - l.height / 2f)
        l.draw(canvas)
        canvas.restore()
    }
}
