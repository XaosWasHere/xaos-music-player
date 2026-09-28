package com.example.xaosmusicplayer.share

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import com.example.xaosmusicplayer.data.YearlyRecap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Genera l'immagine del riepilogo annuale e la salva in galleria.
 *
 * È disegnata direttamente su un Canvas invece che componendo una schermata e
 * catturandola: la cattura di una composable richiede che sia sulla gerarchia
 * visibile e ne eredita le dimensioni dello schermo, mentre qui serve una tela
 * fissa e prevedibile, generabile anche in background.
 */
class RecapCardRenderer(private val context: Context) {

    suspend fun render(recap: YearlyRecap): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val artwork = recap.topArtistTopSong?.song?.artworkUri?.let(::loadBitmap)
            val bitmap = draw(recap, artwork)
            val uri = save(bitmap, recap.year)
            bitmap.recycle()
            artwork?.recycle()
            uri
        }.onFailure { Log.e(TAG, "Card non generata", it) }
    }

    // ---------- Disegno ----------

    private fun draw(recap: YearlyRecap, artwork: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, artwork)

        var y = 165f
        y = drawTitle(canvas, "IL MIO ${recap.year}", y)

        // Il totale dell'anno sta in testa, sotto il titolo: i minuti che
        // compaiono più sotto sono quelli del solo artista di punta, e due
        // numeri uguali di aspetto ma di significato diverso confonderebbero.
        y += 58f
        y = drawYearTotals(canvas, recap, y)

        y += 40f
        y = drawCover(canvas, artwork, y)

        y += 56f
        y = drawLeader(canvas, recap, y)

        // Le classifiche sono ancorate al terzo inferiore invece di seguire il
        // blocco sopra: così l'immagine resta bilanciata anche quando l'artista
        // non ha un brano di punta e la parte alta si accorcia.
        drawColumns(canvas, recap, COLUMNS_TOP)

        drawGenres(canvas, recap)
        drawSignature(canvas)

        return bitmap
    }

    /** Copertina sfocata a tutto campo, con un velo scuro per far leggere il testo. */
    private fun drawBackground(canvas: Canvas, artwork: Bitmap?) {
        canvas.drawColor(Color.BLACK)
        if (artwork == null) return

        // Ridurre e reingrandire è una sfocatura povera ma efficace, e non
        // dipende da RenderEffect, che esiste solo da Android 12.
        val tiny = Bitmap.createScaledBitmap(artwork, BLUR_SIZE, BLUR_SIZE, true)
        canvas.drawBitmap(
            tiny,
            null,
            RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        tiny.recycle()

        canvas.drawColor(Color.argb(200, 0, 0, 0))
    }

    private fun drawTitle(canvas: Canvas, text: String, y: Float): Float {
        val paint = textPaint(64f, Color.WHITE, bold = true).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText(text, WIDTH / 2f, y, paint)
        return y
    }

    /** Quanto ha suonato l'anno in totale, non il solo artista di punta. */
    private fun drawYearTotals(canvas: Canvas, recap: YearlyRecap, y: Float): Float {
        val paint = textPaint(34f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
        val plays = if (recap.playCount == 1) "1 ASCOLTO" else "${recap.playCount} ASCOLTI"
        canvas.drawText("${recap.minutesListened} MINUTI · $plays", WIDTH / 2f, y, paint)
        return y
    }

    private fun drawCover(canvas: Canvas, artwork: Bitmap?, y: Float): Float {
        val left = (WIDTH - COVER) / 2f
        val rect = RectF(left, y, left + COVER, y + COVER)

        if (artwork != null) {
            canvas.drawBitmap(artwork, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawRect(rect, Paint().apply { color = Color.argb(255, 30, 30, 30) })
        }
        return rect.bottom
    }

    /** Artista dell'anno, i suoi minuti e il suo brano più ascoltato. */
    private fun drawLeader(canvas: Canvas, recap: YearlyRecap, y: Float): Float {
        val leader = recap.topArtists.firstOrNull() ?: return y
        var cursor = y

        // L'etichetta chiarisce che tutto il blocco — minuti compresi — parla
        // di questo artista e non dell'anno intero.
        val heading = textPaint(26f, GREY).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("ARTISTA DELL'ANNO", WIDTH / 2f, cursor, heading)
        cursor += 50f

        val name = textPaint(52f, RED, bold = true).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(ellipsize(leader.name.uppercase(), name, WIDTH - 120f), WIDTH / 2f, cursor, name)

        cursor += 56f
        val minutes = textPaint(34f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("${recap.topArtistMinutes} MINUTI", WIDTH / 2f, cursor, minutes)

        recap.topArtistTopSong?.let { top ->
            cursor += 64f
            val label = textPaint(26f, GREY).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("BRANO PIÙ ASCOLTATO", WIDTH / 2f, cursor, label)

            cursor += 44f
            val title = textPaint(36f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(
                ellipsize(top.song.title.uppercase(), title, WIDTH - 120f),
                WIDTH / 2f,
                cursor,
                title,
            )
        }
        return cursor
    }

    /** Due classifiche affiancate: gli altri artisti e i brani dell'anno. */
    private fun drawColumns(canvas: Canvas, recap: YearlyRecap, y: Float) {
        val leftX = MARGIN
        val rightX = WIDTH / 2f + 20f
        val columnWidth = WIDTH / 2f - MARGIN - 20f

        val header = textPaint(26f, GREY)
        canvas.drawText("ALTRI ARTISTI", leftX, y, header)
        canvas.drawText("BRANI DELL'ANNO", rightX, y, header)

        val entry = textPaint(30f, Color.WHITE)
        val rank = textPaint(30f, RED, bold = true)

        // Il primo artista è già il protagonista sopra: qui si parte dal secondo.
        val others = recap.topArtists.drop(1).take(COLUMN_ROWS)
        val songs = recap.topSongs.take(COLUMN_ROWS)

        for (i in 0 until COLUMN_ROWS) {
            val rowY = y + 64f + i * 64f
            others.getOrNull(i)?.let {
                canvas.drawText("${i + 2}", leftX, rowY, rank)
                canvas.drawText(
                    ellipsize(it.name.uppercase(), entry, columnWidth - 40f),
                    leftX + 40f, rowY, entry,
                )
            }
            songs.getOrNull(i)?.let {
                canvas.drawText("${i + 1}", rightX, rowY, rank)
                canvas.drawText(
                    ellipsize(it.song.title.uppercase(), entry, columnWidth - 40f),
                    rightX + 40f, rowY, entry,
                )
            }
        }
    }

    private fun drawGenres(canvas: Canvas, recap: YearlyRecap) {
        if (recap.topGenres.isEmpty()) return
        val paint = textPaint(26f, GREY).apply { textAlign = Paint.Align.CENTER }
        val text = recap.topGenres.joinToString(" · ") { it.name.uppercase() }
        canvas.drawText(
            ellipsize(text, paint, WIDTH - 80f),
            WIDTH / 2f,
            HEIGHT - 130f,
            paint,
        )
    }

    private fun drawSignature(canvas: Canvas) {
        val paint = textPaint(28f, RED, bold = true).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.3f
        }
        canvas.drawText("XAOS", WIDTH / 2f, HEIGHT - 70f, paint)
    }

    // ---------- Utilità ----------

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint().apply {
        isAntiAlias = true
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String =
        TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    /**
     * Carica la copertina ridotta: serve al massimo a riempire un quadrato di
     * 640 px, decodificarla intera sarebbe spreco di memoria.
     */
    private fun loadBitmap(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > COVER) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()

    // ---------- Salvataggio ----------

    private fun save(bitmap: Bitmap, year: Int): Uri {
        val name = "Xaos $year.png"
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, PICTURES_SUBDIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            ) ?: error("MediaStore ha rifiutato l'inserimento")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("Impossibile scrivere su $uri")
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (error: Throwable) {
                // Senza questo resterebbe una riga pending invisibile e inutile.
                resolver.delete(uri, null, null)
                throw error
            }
            return uri
        }

        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            FOLDER,
        ).apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(file.absolutePath), arrayOf("image/png"), null,
        )
        return Uri.fromFile(file)
    }

    private companion object {
        const val TAG = "RecapCardRenderer"

        /** Formato verticale da storia, che è come si condivide questa roba. */
        const val WIDTH = 1080
        const val HEIGHT = 1920
        const val COVER = 640
        const val MARGIN = 80f
        const val COLUMN_ROWS = 4

        /** Da dove partono le due classifiche, misurato dall'alto. */
        const val COLUMNS_TOP = 1310f

        /** Quanto ridurre la copertina prima di reingrandirla, per sfocarla. */
        const val BLUR_SIZE = 24

        val RED = Color.rgb(255, 45, 45)
        val GREY = Color.rgb(150, 150, 150)

        const val FOLDER = "Xaos"
        val PICTURES_SUBDIR = Environment.DIRECTORY_PICTURES + "/" + FOLDER
    }
}
