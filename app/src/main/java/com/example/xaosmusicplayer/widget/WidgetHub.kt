package com.example.xaosmusicplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.xaosmusicplayer.MainActivity
import com.example.xaosmusicplayer.R
import com.example.xaosmusicplayer.beam.BeamConnect
import com.example.xaosmusicplayer.data.Lyrics
import com.example.xaosmusicplayer.data.LyricsReader
import com.example.xaosmusicplayer.i18n.I18n
import com.example.xaosmusicplayer.ui.theme.DarkPalette
import com.example.xaosmusicplayer.ui.theme.LightPalette
import com.example.xaosmusicplayer.ui.theme.ThemeStore
import com.example.xaosmusicplayer.ui.theme.XaosPalette
import com.example.xaosmusicplayer.ui.theme.customized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Il collegamento fra il player e il widget.
 *
 * Vive nel servizio di riproduzione: segue il brano, la pausa e la posizione,
 * e ridisegna una pagina solo quando cambia davvero (un altro brano, play o
 * pausa, il verso successivo). Senza widget sulla schermata non fa niente.
 *
 * Il widget ha due pagine che si scorrono in verticale, copertina e testo; se
 * il brano non ha un testo sincronizzato ne ha una sola, e non scorre. Le
 * pagine viaggiano dentro l'aggiornamento stesso: con una lista affidata a un
 * servizio Android si limita ad avvisare il launcher, e quello di Nothing non
 * le ricaricava.
 */
object WidgetHub {

    /** Il player del servizio, finché è vivo: il tocco sulla copertina lo usa. */
    @Volatile
    var player: Player? = null
        private set

    /** Le pagine da mostrare: la copertina, e il testo se c'è. */
    @Volatile
    var pages: List<Bitmap> = emptyList()
        private set

    private var context: Context? = null
    private var scope: CoroutineScope? = null
    private var trackJob: Job? = null
    private val main = Handler(Looper.getMainLooper())

    private var songId: Long? = null
    private var title: String? = null
    private var artwork: Bitmap? = null
    private var lyrics: Lyrics? = null
    private var lineIndex = Int.MIN_VALUE
    private var playing = false
    private var stacked = false

    /**
     * Xaos Beam Connect: quando il dispositivo attivo è il PC, il widget mostra
     * il brano del PC (copertina e testo), e il tocco mette in pausa il PC.
     */
    @Volatile private var remote: BeamConnect.PcNow? = null
    private var remoteKey: Long? = null
    private var remoteScope: CoroutineScope? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (remote == null) loadTrack(mediaItem)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            BeamConnect.localPlaying.value = isPlaying
            if (remote != null) return
            playing = isPlaying
            render(pushLayout = false)
            scheduleTick()
        }
    }

    /** La posizione di chi suona: il player del telefono, o il PC. */
    private fun positionNow(): Long? = remote?.positionNow() ?: player?.currentPosition

    private fun isPlayingNow(): Boolean = remote?.playing ?: (player?.isPlaying == true)

    private val tick = object : Runnable {
        override fun run() {
            val position = positionNow() ?: return
            val l = lyrics ?: return
            val index = l.indexAt(position)
            if (index != lineIndex) {
                lineIndex = index
                render(pushLayout = false)
            }
            if (isPlayingNow()) main.postDelayed(this, TICK_MS)
        }
    }

    /** Da chiamare quando il servizio crea il player. */
    fun attach(context: Context, player: Player) {
        I18n.init(context)
        this.context = context.applicationContext
        this.player = player
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            // Il tema dell'app è anche quello del widget.
            val store = ThemeStore.get(context)
            s.launch { combine(store.isDark, store.custom) { _, _ -> }.collect { render(pushLayout = true) } }
        }
        player.addListener(listener)
        watchRemote(context)
        if (remote != null) return
        playing = player.isPlaying
        loadTrack(player.currentMediaItem)
    }

    /**
     * Segue il PC: quando è lui il dispositivo attivo (o il telefono non ha
     * niente da suonare) il widget mostra il suo brano. Si chiama più volte
     * senza danni.
     */
    @Synchronized
    fun watchRemote(context: Context) {
        I18n.init(context)
        if (this.context == null) this.context = context.applicationContext
        if (remoteScope != null) return
        remoteScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch {
                combine(BeamConnect.pc, BeamConnect.remoteActive) { pc, active -> pc to active }.collect { (pc, active) ->
                    val show = pc?.takeIf { it.hasTrack && (active || player?.currentMediaItem == null) }
                    showRemote(show)
                }
            }
        }
    }

    private fun showRemote(pc: BeamConnect.PcNow?) {
        val ctx = context ?: return
        if (pc == null) {
            if (remote == null) return
            // Si torna al telefono: il suo brano, com'è adesso.
            remote = null
            remoteKey = null
            playing = player?.isPlaying == true
            loadTrack(player?.currentMediaItem)
            return
        }
        val key = pc.pcId ?: Long.MIN_VALUE
        val sameTrack = remote != null && remoteKey == key && title == pc.title
        remote = pc
        playing = pc.playing
        if (sameTrack) {
            // Stesso brano: cambia solo play/pausa o la posizione.
            lineIndex = lyrics?.indexAt(pc.positionNow()) ?: lineIndex
            render(pushLayout = false)
            scheduleTick()
            return
        }
        remoteKey = key
        trackJob?.cancel()
        songId = pc.pcId?.let { -it } ?: Long.MIN_VALUE
        title = pc.title
        artwork = null
        lyrics = null
        lineIndex = Int.MIN_VALUE
        trackJob = remoteScope?.launch {
            if (!hasWidgets(ctx)) return@launch
            val art = pc.pcId?.let(BeamConnect::artworkUrl)?.let(Uri::parse)
            artwork = withContext(Dispatchers.IO) { decodeArtwork(ctx, art) }
            render(pushLayout = true)
            val loaded = pc.pcId?.let { id -> runCatching { LyricsReader(ctx).load(-id) }.getOrNull() }
            if (remoteKey != key) return@launch
            lyrics = loaded?.takeIf { it.synced && !it.isEmpty }
            lineIndex = lyrics?.indexAt(remote?.positionNow() ?: 0L) ?: Int.MIN_VALUE
            render(pushLayout = true)
            scheduleTick()
        }
    }

    /** Da chiamare quando il servizio si ferma: il widget torna a "apri Xaos". */
    fun detach() {
        player?.removeListener(listener)
        player = null
        main.removeCallbacks(tick)
        scope?.cancel()
        scope = null
        songId = null
        title = null
        artwork = null
        lyrics = null
        playing = false
        // Il telefono ha smesso, ma se suona il PC il widget resta su di lui.
        val r = remote
        if (r != null) {
            remoteKey = null
            showRemote(r)
            return
        }
        context?.let { refresh(it) }
    }

    /** Il widget è stato aggiunto o ridimensionato: ridisegna tutto. */
    fun refresh(context: Context) {
        I18n.init(context)
        if (this.context == null) this.context = context.applicationContext
        // Un widget appena aggiunto: copertina e testo del brano vanno letti ora,
        // perché senza widget non li si prepara.
        val p = player
        val r = remote
        if (r != null && artwork == null && trackJob?.isActive != true) {
            // Un widget appena aggiunto mentre suona il PC.
            remoteKey = null
            showRemote(r)
        } else if (r == null && p != null && songId != null && artwork == null && trackJob?.isActive != true) {
            loadTrack(p.currentMediaItem)
        } else {
            render(pushLayout = true)
        }
    }

    // ---------------------------------------------------------------- brano

    private fun loadTrack(item: MediaItem?) {
        val ctx = context ?: return
        val id = item?.mediaId?.toLongOrNull()
        trackJob?.cancel()
        songId = id
        title = item?.mediaMetadata?.title?.toString()
        lyrics = null
        lineIndex = Int.MIN_VALUE
        if (id == null) {
            artwork = null
            render(pushLayout = true)
            return
        }
        // Prima la copertina, poi il testo: la pagina principale arriva subito.
        trackJob = scope?.launch {
            if (!hasWidgets(ctx)) return@launch
            artwork = withContext(Dispatchers.IO) { decodeArtwork(ctx, item.mediaMetadata.artworkUri) }
            render(pushLayout = true)
            val loaded = runCatching { LyricsReader(ctx).load(id) }.getOrNull()
            if (songId != id || remote != null) return@launch
            lyrics = loaded?.takeIf { it.synced && !it.isEmpty }
            lineIndex = lyrics?.indexAt(player?.currentPosition ?: 0L) ?: Int.MIN_VALUE
            render(pushLayout = true)
            scheduleTick()
        }
    }

    private fun scheduleTick() {
        main.removeCallbacks(tick)
        if (lyrics != null && isPlayingNow()) main.post(tick)
    }

    private fun decodeArtwork(ctx: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            com.example.xaosmusicplayer.beam.Beam.openStream(ctx, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = max(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null
            var sample = 1
            while (longest / (sample * 2) >= MAX_SIDE_PX) sample *= 2
            com.example.xaosmusicplayer.beam.Beam.openStream(ctx, uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------------- disegno

    private fun palette(ctx: Context): XaosPalette {
        val store = ThemeStore.get(ctx)
        return (if (store.isDark.value) DarkPalette else LightPalette).customized(store.custom.value)
    }

    /**
     * Ridisegna le pagine e le manda al launcher. Con [pushLayout] si rimanda
     * tutta la vista (serve quando cambia il numero di pagine); altrimenti si
     * aggiornano solo le pagine della lista, e lo scorrimento resta dov'è.
     */
    private fun render(pushLayout: Boolean) {
        val ctx = context ?: return
        val manager = AppWidgetManager.getInstance(ctx)
        val ids = manager.getAppWidgetIds(ComponentName(ctx, XaosWidget::class.java))
        if (ids.isEmpty()) return
        val sideDp = sideDp(manager, ids)
        val side = (sideDp * ctx.resources.displayMetrics.density).toInt().coerceIn(160, MAX_SIDE_PX)
        val palette = palette(ctx)
        val l = lyrics
        // Due pagine solo da Android 12: prima i widget non possono ricevere
        // una lista già pronta, e resta la sola copertina.
        val paged = songId != null && l != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        pages = when {
            songId == null -> listOf(WidgetRenderer.idle(side, palette))
            !paged -> listOf(WidgetRenderer.cover(side, artwork, title, playing, 1, palette))
            else -> listOf(
                WidgetRenderer.cover(side, artwork, title, playing, 2, palette),
                WidgetRenderer.lyrics(side, l!!, lineIndex, palette),
            )
        }
        if (paged && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (pushLayout || !stacked) {
                stacked = true
                manager.updateAppWidget(ids, pagedViews(ctx, sideDp, full = true))
            } else {
                // Solo le pagine: la lista resta dov'è stata scorsa.
                manager.partiallyUpdateAppWidget(ids, pagedViews(ctx, sideDp, full = false))
            }
        } else {
            stacked = false
            manager.updateAppWidget(ids, singleView(ctx))
        }
    }

    /**
     * La lista delle due pagine, alta e larga esattamente un quadrato: così
     * lo scorrimento è di una pagina sola, e un gesto finisce su una delle due.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun pagedViews(ctx: Context, sideDp: Int, full: Boolean): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.widget_xaos_pages).apply {
            val items = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
            pages.forEachIndexed { i, page ->
                items.addItem(
                    i.toLong(),
                    RemoteViews(ctx.packageName, R.layout.widget_xaos_page).apply {
                        setImageViewBitmap(R.id.widget_page_image, page)
                        setViewLayoutWidth(R.id.widget_page_image, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                        setViewLayoutHeight(R.id.widget_page_image, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                        setOnClickFillInIntent(R.id.widget_page_image, Intent().putExtra(XaosWidget.EXTRA_PAGE, i))
                    },
                )
            }
            setRemoteAdapter(R.id.widget_pages, items.build())
            if (full) {
                setViewLayoutWidth(R.id.widget_pages, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.widget_pages, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                setPendingIntentTemplate(R.id.widget_pages, tapIntent(ctx))
            }
        }

    private fun singleView(ctx: Context): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.widget_xaos_single).apply {
            pages.firstOrNull()?.let { setImageViewBitmap(R.id.widget_image, it) }
            setOnClickPendingIntent(
                R.id.widget_image,
                if ((player != null || remote != null) && songId != null) tapIntent(ctx, Intent().putExtra(XaosWidget.EXTRA_PAGE, 0)) else openApp(ctx),
            )
        }

    /** Il tocco arriva al provider, che mette in pausa o riprende (o apre l'app). */
    private fun tapIntent(ctx: Context, fillIn: Intent? = null): PendingIntent {
        val intent = Intent(ctx, XaosWidget::class.java).setAction(XaosWidget.ACTION_TAP)
        fillIn?.extras?.let(intent::putExtras)
        // Il modello della pila dev'essere modificabile, per ricevere la pagina toccata.
        val mutability = if (fillIn == null) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, if (fillIn == null) 1 else 2, intent, mutability or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Mette in pausa o riprende, sul thread del player (o sul PC, se suona lui). */
    fun togglePlayback(): Boolean {
        if (remote != null) {
            BeamConnect.control("toggle")
            return true
        }
        val p = player ?: return false
        Handler(p.applicationLooper).post {
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
                p.play()
            }
        }
        return true
    }

    /**
     * Il lato del quadrato in dp: il più piccolo fra larghezza e altezza del
     * riquadro (in verticale valgono larghezza minima e altezza massima), e fra
     * più widget il più piccolo, perché ci stia in tutti.
     */
    private fun sideDp(manager: AppWidgetManager, ids: IntArray): Int =
        ids.minOf { id ->
            val o = manager.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (w > 0 && h > 0) min(w, h) else DEFAULT_SIDE_DP
        }

    private fun hasWidgets(ctx: Context): Boolean =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, XaosWidget::class.java)).isNotEmpty()

    private const val TICK_MS = 250L
    private const val DEFAULT_SIDE_DP = 170
    /** Il tetto delle immagini delle pagine: nitide su un 2x2, leggere da mandare al launcher. */
    private const val MAX_SIDE_PX = 420
}
