package com.example.xaosmusicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.xaosmusicplayer.audio.AudioLevels
import com.example.xaosmusicplayer.data.GlowMode
import com.example.xaosmusicplayer.data.Lyrics
import com.example.xaosmusicplayer.data.LyricsState
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.ui.components.Artwork
import com.example.xaosmusicplayer.ui.components.DotProgressLine
import com.example.xaosmusicplayer.ui.components.SwipeableSong
import com.example.xaosmusicplayer.ui.components.formatDuration
import com.example.xaosmusicplayer.ui.effect.ArtworkBackdrop
import com.example.xaosmusicplayer.ui.effect.ReactiveGlow
import com.example.xaosmusicplayer.ui.effect.rememberArtworkColors
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.components.AccentDot
import com.example.xaosmusicplayer.ui.theme.Xaos
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.lerp as lerpSp

/**
 * Il player a tutto schermo, con il testo del brano sotto.
 *
 * La schermata ha un solo asse: `collapse`, da 0 a 1. A 0 è il player di sempre
 * e del testo si vede la fascia in fondo; a 1 copertina e titolo si sono
 * compattati in una riga alta quanto una miniatura e il testo occupa tutto.
 * Fra i due estremi non c'è nessuno scambio di composabili: sono gli stessi
 * elementi che cambiano dimensione e posizione, perché una copertina che sparisce
 * per far comparire una miniatura si vede, e si vede male.
 *
 * I comandi in basso non partecipano: restano dove sono in entrambi gli stati.
 */
@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    levels: AudioLevels,
    glowMode: GlowMode,
    isFavorite: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    sleepTimerRemainingMs: Long?,
    lyricsState: LyricsState,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetGlowMode: (GlowMode) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artworkColors by rememberArtworkColors(song.artworkUri)
    // Il fondo insegue la copertina con calma: cambiando brano, un salto secco
    // di tinta a tutto schermo è molto più violento di una copertina che cambia.
    val tint by animateColorAsState(
        targetValue = artworkColors.background,
        animationSpec = tween(500),
        label = "tinta",
    )

    val scope = rememberCoroutineScope()
    var optionsOpen by remember { mutableStateOf(false) }
    var collapse by remember { mutableFloatStateOf(0f) }
    var collapseRangePx by remember { mutableFloatStateOf(1f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // Reagisce alla trascinatura restituendo quanto ne ha consumato, così chi
    // chiama può passare il resto a chi sta sotto.
    fun drag(deltaY: Float): Float {
        val before = collapse
        collapse = (collapse - deltaY / collapseRangePx).coerceIn(0f, 1f)
        return (before - collapse) * collapseRangePx
    }

    // A dito alzato la schermata si decide: o player o testo, mai a metà.
    fun settle() {
        settleJob?.cancel()
        settleJob = scope.launch {
            val target = if (collapse > 0.5f) 1f else 0f
            animate(collapse, target, animationSpec = tween(280)) { value, _ -> collapse = value }
        }
    }

    // Lo scorrimento del testo trascina prima l'intestazione e solo dopo scorre
    // la lista: è quello che fa sembrare le due cose un'unica superficie.
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                settleJob?.cancel()
                // Verso l'alto l'intestazione si comprime prima che il testo scorra.
                return if (available.y < 0) Offset(0f, drag(available.y)) else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                // Verso il basso tocca prima alla lista: l'intestazione si riapre
                // solo quando il testo è già in cima.
                if (available.y > 0) Offset(0f, drag(available.y)) else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                settle()
                return Velocity.Zero
            }
        }
    }

    val palette = Xaos.colors
    val background = lerpColor(palette.background, tint, collapse)
    // Bianco o nero secondo il fondo *corrente*, non secondo la copertina:
    // a metà transizione il fondo è ancora quasi nero, e un testo già scuro
    // sparirebbe.
    //
    // La soglia non è a metà strada: il nero su un fondo medio si legge molto
    // meglio del bianco, e il punto in cui i due si equivalgono sta parecchio
    // più in basso. Con una soglia a occhio, i verdi e i grigi medi finiscono
    // per prendere il bianco quando avrebbero bisogno del nero.
    val content by animateColorAsState(
        targetValue = if (background.luminance() > CONTRAST_CROSSOVER) Color.Black else Color.White,
        animationSpec = tween(180),
        label = "testo",
    )
    val dimmed = content.copy(alpha = 0.45f)

    // Senza testo la schermata non ha una seconda pagina: niente riquadro,
    // niente messaggio, e soprattutto niente gesto. Un trascinamento che apre
    // il vuoto è peggio di un trascinamento che non fa niente.
    val lyrics = (lyricsState as? LyricsState.Ready)?.lyrics
    LaunchedEffect(lyrics) {
        if (lyrics == null) {
            settleJob?.cancel()
            collapse = 0f
        }
    }

    // A schermo intero questa schermata deve fare da diaframma: senza un
    // consumatore di tocchi, Compose prosegue il hit-test verso il basso e i
    // tap sulle zone vuote finiscono sulla libreria che sta sotto.
    val blockTouches = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .clickable(
                interactionSource = blockTouches,
                indication = null,
                onClick = {},
            )
    ) {
        val density = LocalDensity.current
        val artworkMax = maxWidth - HORIZONTAL_PADDING.dp * 2
        val headerMax = artworkMax + TITLE_BLOCK_HEIGHT.dp
        val headerMin = ARTWORK_MIN.dp
        // Serve in pixel: è la distanza che il dito deve percorrere per passare
        // da un estremo all'altro.
        collapseRangePx = with(density) { (headerMax - headerMin).toPx() }
            .coerceAtLeast(1f)

        // Il glow si spegne man mano che il fondo prende il colore del disco:
        // due sfondi sovrapposti si sporcherebbero a vicenda.
        if (glowMode == GlowMode.ARTWORK) {
            ArtworkBackdrop(
                artworkUri = song.artworkUri,
                backdrop = background,
                modifier = Modifier.fillMaxSize().alpha(1f - collapse),
            )
        } else {
            ReactiveGlow(
                colors = artworkColors,
                levels = levels,
                isPlaying = isPlaying,
                mode = glowMode,
                backdrop = background,
                modifier = Modifier.fillMaxSize().alpha(1f - collapse),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = HORIZONTAL_PADDING.dp),
        ) {
            TopBar(
                song = song,
                content = content,
                onCollapse = onCollapse,
                onOpenOptions = { optionsOpen = true },
            )

            Spacer(Modifier.height(12.dp))

            // Copertina, titolo e verso stanno insieme in un blocco centrato
            // nello spazio libero, invece di essere appoggiati in alto con
            // tutto l'avanzo che si accumula sotto. Così l'aria si divide fra
            // sopra e sotto e la schermata resta composta anche quando il verso
            // non c'è — che sia perché il brano non ha testo o perché siamo in
            // uno stacco strumentale.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val free = maxHeight
                // La fascia del testo è alta uguale per tutto il brano, verso o
                // non verso: se sparisse durante gli stacchi, il blocco si
                // sposterebbe ogni pochi secondi.
                val closedPanel = if (lyrics == null) 0.dp else CLOSED_PANEL_HEIGHT.dp
                val gap = if (lyrics == null) 0.dp else PANEL_GAP.dp
                // Su uno schermo basso la copertina cede spazio al resto invece
                // di sbordare oltre i comandi.
                val headerRoom = (free - closedPanel - gap).coerceAtLeast(0.dp)
                val artwork = minOf(artworkMax, headerRoom - TITLE_BLOCK_HEIGHT.dp)
                    .coerceAtLeast(0.dp)
                val headerFull = artwork + TITLE_BLOCK_HEIGHT.dp

                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                ) {
                    MorphingHeader(
                        song = song,
                        collapse = collapse,
                        artworkMax = artwork,
                        height = lerpDp(headerFull, headerMin, collapse),
                        content = content,
                        // Sul tema scuro l'artista resta nel rosso di sempre; sul
                        // chiaro il giallo non si leggerebbe, e scende di tono.
                        artistColor = if (palette.isDark) palette.accentInk
                        else content.copy(alpha = 0.6f),
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        modifier = if (lyrics == null) Modifier else Modifier.draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta -> drag(delta) },
                            onDragStarted = { settleJob?.cancel() },
                            onDragStopped = { settle() },
                        ),
                    )

                    if (lyrics != null) {
                        Spacer(Modifier.height(gap))
                        LyricsPanel(
                            lyrics = lyrics,
                            songId = song.id,
                            positionMs = positionMs,
                            collapse = collapse,
                            content = content,
                            dimmed = dimmed,
                            onSeek = onSeek,
                            modifier = Modifier
                                .fillMaxWidth()
                                // Da aperto il blocco arriva a riempire tutto lo
                                // spazio libero, e a quel punto centrarlo non
                                // sposta più niente.
                                .height(
                                    lerpDp(
                                        closedPanel,
                                        (free - headerMin - gap).coerceAtLeast(0.dp),
                                        collapse,
                                    )
                                )
                                // Da chiuso non c'è nessuna lista a raccogliere
                                // il gesto, quindi lo raccoglie il pannello; da
                                // aperto lo prende la lista, che è più interna,
                                // e da lì passa a `nestedScroll`.
                                .draggable(
                                    orientation = Orientation.Vertical,
                                    state = rememberDraggableState { delta -> drag(delta) },
                                    onDragStarted = { settleJob?.cancel() },
                                    onDragStopped = { settle() },
                                )
                                .nestedScroll(nestedScroll),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SeekBar(
                positionMs = positionMs,
                durationMs = if (durationMs > 0) durationMs else song.durationMs,
                content = content,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(12.dp))

            TransportControls(
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                content = content,
                dimmed = dimmed,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )

            Spacer(Modifier.height(8.dp))

            BottomRow(
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                levels = levels,
                isPlaying = isPlaying,
                glowMode = glowMode,
                content = content,
                onOpenSleepTimer = onOpenSleepTimer,
                onOpenQueue = onOpenQueue,
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    if (optionsOpen) {
        PlayerOptionsSheet(
            song = song,
            glowMode = glowMode,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            onDismiss = { optionsOpen = false },
            onSetGlowMode = onSetGlowMode,
            onAddToPlaylist = onAddToPlaylist,
            onGoToAlbum = onGoToAlbum,
            onGoToArtist = onGoToArtist,
            onOpenSleepTimer = onOpenSleepTimer,
            onOpenEqualizer = onOpenEqualizer,
        )
    }
}

/**
 * Copertina, titolo e artista che passano dal formato grande a una riga.
 *
 * Non ci sono due versioni fra cui scegliere: dimensioni, posizioni e corpo del
 * carattere sono interpolati, quindi a metà strada la schermata è a metà strada
 * davvero.
 */
@Composable
private fun MorphingHeader(
    song: Song,
    collapse: Float,
    artworkMax: Dp,
    height: Dp,
    content: Color,
    artistColor: Color,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artworkSize = lerpDp(artworkMax, ARTWORK_MIN.dp, collapse)
    // Il testo scivola a destra della copertina mentre questa rimpicciolisce, e
    // insieme risale: sono gli stessi due Text dall'inizio alla fine.
    val textStart = lerpDp(0.dp, ARTWORK_MIN.dp + 14.dp, collapse)
    val textTop = lerpDp(artworkMax + 16.dp, 4.dp, collapse)

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        // La copertina si trascina di lato per cambiare brano. Il gesto sta su
        // di lei e non su tutta l'intestazione: il titolo resta fermo e fa da
        // riferimento, e più in basso c'è il testo, dove uno scorrimento
        // laterale non deve voler dire niente.
        SwipeableSong(
            songId = song.id,
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier
                .size(artworkSize)
                .align(Alignment.TopStart)
                // Esce dai propri bordi, non da sopra il titolo.
                .clipToBounds(),
        ) {
            Artwork(
                uri = song.artworkUri,
                // Il raggio scende con la copertina: a miniatura, 24dp di
                // curva la renderebbero un cerchio.
                cornerRadius = (24 - 14 * collapse).toInt(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Il preferito sta accanto al titolo, come su Spotify: è un giudizio sul
        // brano, e va vicino al suo nome invece che fra i comandi.
        FavoriteButton(
            isFavorite = isFavorite,
            content = content,
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = textTop - 4.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = textStart, top = textTop, end = FAVORITE_ROOM.dp),
        ) {
            Text(
                text = song.title.uppercase(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = lerpSp(TITLE_SIZE_MAX.sp, TITLE_SIZE_MIN.sp, collapse),
                ),
                color = content,
                maxLines = 1,
                // Niente ellissi: qui il titolo scorre invece di essere
                // troncato, perché è l'unico posto dove serve leggerlo per
                // intero e c'è tutto il tempo per farlo. Scorre da solo solo
                // quando non ci sta: se ci sta, resta fermo.
                softWrap = false,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = lerpSp(ARTIST_SIZE_MAX.sp, ARTIST_SIZE_MIN.sp, collapse),
                ),
                color = artistColor,
                maxLines = 1,
                // Scorre come il titolo: è la riga che porta i featuring, quindi
                // è lunga almeno quanto lui, e due righe attaccate che si
                // comportano in modo diverso stonerebbero.
                softWrap = false,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            )
        }
    }
}

/**
 * Il testo del brano, in due modi a seconda di quanto è aperto.
 *
 * Chiuso mostra il verso in corso e nient'altro: il player deve restare una
 * schermata da guardare, non da leggere, e tre versi di cui due spenti sono già
 * un blocco di testo. Aperto diventa il testo intero, scorrevole.
 *
 * Lo scambio avviene solo a zero esatto, cioè quando il pannello è già alto una
 * riga e le due versioni si somigliano: a metà strada sarebbe uno stacco, e a
 * gesto in corso toglierebbe di mezzo proprio l'elemento che lo sta ricevendo.
 */
@Composable
private fun LyricsPanel(
    lyrics: Lyrics,
    songId: Long,
    positionMs: Long,
    collapse: Float,
    content: Color,
    dimmed: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nessun fondo proprio, in nessuno dei due stati: il testo sta sulla stessa
    // superficie di tutto il resto. Un riquadro chiaro si farebbe notare
    // soprattutto quando è vuoto — durante uno stacco strumentale resterebbe lì
    // a segnalare il nulla — e darebbe alla fascia un peso che non deve avere.
    Box(modifier = modifier) {
        if (collapse > 0f) {
            FullLyrics(
                lyrics = lyrics,
                songId = songId,
                positionMs = positionMs,
                centred = collapse > 0.5f,
                content = content,
                dimmed = dimmed,
                onSeek = onSeek,
            )
        } else {
            CurrentLine(lyrics = lyrics, positionMs = positionMs, content = content)
        }
    }
}

/**
 * Il solo verso in corso. Prima che il canto cominci non c'è niente da mostrare,
 * e il riquadro resta vuoto invece di riempirsi di segnaposti.
 */
@Composable
private fun CurrentLine(lyrics: Lyrics, positionMs: Long, content: Color) {
    val text = remember(lyrics, positionMs) {
        lyrics.lines.getOrNull(lyrics.indexAt(positionMs))?.text
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        // Allineato a sinistra come il titolo sopra e come i versi del testo
        // aperto: senza un riquadro a delimitarlo, un blocco centrato in mezzo
        // a righe allineate a sinistra si leggerebbe come un elemento estraneo.
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!text.isNullOrBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FullLyrics(
    lyrics: Lyrics,
    songId: Long,
    positionMs: Long,
    centred: Boolean,
    content: Color,
    dimmed: Color,
    onSeek: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    // Richiudendo il testo questo composabile esce di scena e lo stato se ne va
    // con lui: riaprendolo si riparte in sincronia, che è quello che serve.
    var following by remember(songId) { mutableStateOf(true) }
    val current = remember(lyrics, positionMs) { lyrics.indexAt(positionMs) }

    // L'inseguimento si rompe solo se il dito sposta davvero il testo. Non basta
    // che il dito sia sulla lista: il gesto che apre il testo comincia proprio
    // lì, e comprime l'intestazione senza far scorrere niente — spegnere la
    // sincronia in quel momento vorrebbe dire che aprire il testo lo desincronizza
    // sempre. E non basta nemmeno guardare se la lista si muove, perché a
    // muoverla è anche il verso che avanza da solo.
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            dragging = when (interaction) {
                is DragInteraction.Start -> true
                is DragInteraction.Stop, is DragInteraction.Cancel -> false
                else -> dragging
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.drop(1).collect {
            if (dragging) following = false
        }
    }

    LaunchedEffect(current, following) {
        if (!following || current < 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height
        // La riga in corso non sta in mezzo ma a un terzo dall'alto: così sotto
        // resta visibile quello che sta per arrivare, che è ciò che si legge.
        // Quando il pannello è ancora una fascia, invece, il terzo la manderebbe
        // sopra il bordo: lì la riga si appoggia in cima e basta.
        listState.animateScrollToItem(current, if (centred) -viewport / 3 else 0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 4.dp,
                // In fondo resta il posto per il pulsante di ritorno, che
                // altrimenti si siede sull'ultimo verso.
                bottom = if (lyrics.synced) 44.dp else 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isCurrent = lyrics.synced && index == current
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleMedium,
                    // Il colore è l'unico segnale di dove siamo: la riga viva è
                    // piena, tutte le altre sono spente allo stesso modo, anche
                    // quelle già passate. Distinguere anche il passato darebbe
                    // tre livelli di grigio da interpretare invece di uno.
                    color = if (isCurrent || !lyrics.synced) content else dimmed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            // Su un testo a tempo ogni verso è anche un punto del
                            // brano: toccarlo ci porta lì.
                            if (lyrics.synced) {
                                Modifier.clickable {
                                    following = true
                                    onSeek(line.timeMs)
                                }
                            } else Modifier
                        )
                        .padding(vertical = 2.dp),
                )
            }
        }

        if (lyrics.synced && !following) {
            ResyncButton(
                content = content,
                onClick = { following = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

@Composable
private fun ResyncButton(
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Xaos.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(palette.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = XaosIcons.Sync,
            contentDescription = null,
            tint = palette.onAccent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "TORNA AL PUNTO",
            style = MaterialTheme.typography.labelMedium,
            color = palette.onAccent,
        )
    }
}

/**
 * La testata: chiudi, da dove arriva il brano, il menu. Tutte le impostazioni
 * stanno dietro ai tre punti; qui resta solo quello che serve a orientarsi.
 */
@Composable
private fun TopBar(
    song: Song,
    content: Color,
    onCollapse: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                imageVector = XaosIcons.ChevronDown,
                contentDescription = "Chiudi",
                tint = content,
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "IN RIPRODUZIONE DA",
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.55f),
            )
            Text(
                text = song.album.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenOptions) {
            Icon(
                imageVector = XaosIcons.More,
                contentDescription = "Altre opzioni",
                tint = content,
            )
        }
    }
}

/**
 * Le quattro colonne in basso a sinistra, che salgono con le bande audio: una
 * piccola matrice di punti, cinque per colonna, accesi dal basso. Il punto più
 * alto acceso prende l'accento, come la lancetta di picco di un VU meter.
 */
@Composable
private fun LevelMeter(levels: AudioLevels, isPlaying: Boolean, content: Color) {
    val bars = listOf(levels.bass, levels.mid, levels.amplitude, levels.treble)
    val accent = Xaos.colors.accent
    Canvas(modifier = Modifier.size(width = 26.dp, height = 20.dp)) {
        val stepX = size.width / bars.size
        val stepY = size.height / METER_ROWS
        val r = minOf(stepX, stepY) * 0.32f
        bars.forEachIndexed { col, level ->
            val lit = if (isPlaying) (1 + level * (METER_ROWS - 1)).toInt() else 1
            for (row in 0 until METER_ROWS) {
                val center = Offset(
                    x = stepX * (col + 0.5f),
                    y = size.height - stepY * (row + 0.5f),
                )
                val color = when {
                    row >= lit -> content.copy(alpha = 0.18f)
                    row == lit - 1 && lit > 1 -> accent
                    else -> content
                }
                drawCircle(color, r, center)
            }
        }
    }
}

private const val METER_ROWS = 5

/**
 * Seek bar in stile Xaos: traccia piena bianca, resto grigio, cursore a barretta
 * verticale invece del pallino di Material.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    content: Color,
    onSeek: (Long) -> Unit,
) {
    // Mentre l'utente trascina, la posizione mostrata è quella del dito, non
    // quella del player: altrimenti il cursore rimbalza a ogni aggiornamento.
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val accent = Xaos.colors.accent
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = if (isDragging) dragValue else {
        (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    }

    Column {
        Slider(
            value = progress,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * safeDuration).toLong())
                isDragging = false
            },
            modifier = Modifier.fillMaxWidth().height(28.dp),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            },
            // La frazione arriva da `progress`, non dallo SliderState: il range
            // interno dello slider non fa parte dell'API pubblica stabile.
            // La traccia è una fila di punti, come tutto il resto.
            track = {
                DotProgressLine(
                    progress = progress,
                    color = content,
                    trackColor = content.copy(alpha = 0.2f),
                    spacing = 6.dp,
                    radius = 1.6.dp,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            },
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(if (isDragging) (dragValue * safeDuration).toLong() else positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.6f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * La riga dei comandi, come su Spotify: casuale e ripeti ai lati, il play al
 * centro. Gli interruttori accesi hanno il punto d'accento sotto.
 */
@Composable
private fun TransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    content: Color,
    dimmed: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val palette = Xaos.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeToggle(
            icon = XaosIcons.Shuffle,
            description = "Riproduzione casuale",
            active = shuffleEnabled,
            content = content,
            dimmed = dimmed,
            onClick = onToggleShuffle,
        )

        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = XaosIcons.Previous,
                contentDescription = "Brano precedente",
                tint = content,
                modifier = Modifier.size(34.dp),
            )
        }

        // Il disco pieno nel colore d'accento, come il tasto di registrazione
        // di Nothing: rosso sul tema scuro, giallo sul chiaro.
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(palette.accent)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) XaosIcons.Pause else XaosIcons.Play,
                contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                tint = palette.onAccent,
                modifier = Modifier.size(34.dp),
            )
        }

        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = XaosIcons.Next,
                contentDescription = "Brano successivo",
                tint = content,
                modifier = Modifier.size(34.dp),
            )
        }

        ModeToggle(
            icon = if (repeatMode == Player.REPEAT_MODE_ONE) XaosIcons.RepeatOne
            else XaosIcons.Repeat,
            description = "Ripetizione",
            active = repeatMode != Player.REPEAT_MODE_OFF,
            content = content,
            dimmed = dimmed,
            onClick = onCycleRepeat,
        )
    }
}

/**
 * Casuale e ripeti: acceso è l'icona piena con il punto d'accento sotto,
 * spento è l'icona smorzata. Il punto si legge anche col giallo su chiaro,
 * dove un'icona gialla sparirebbe.
 */
@Composable
private fun ModeToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    content: Color,
    dimmed: Color,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (active) content else dimmed, label = "mode-tint")
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (active) {
            AccentDot(
                size = 4.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Xaos.colors
    // Sul chiaro un cuore giallo pieno si confonderebbe col fondo: lì resta
    // nell'inchiostro del testo, e a dire "preferito" basta che sia pieno.
    val activeTint = if (palette.isDark) palette.accent else content
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (isFavorite) XaosIcons.Favorite else XaosIcons.FavoriteBorder,
            contentDescription = if (isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
            tint = if (isFavorite) activeTint else content.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * La riga in fondo: a sinistra il timer quando corre, altrimenti il
 * misuratore; a destra la coda. Lo stesso posto dove Spotify tiene dispositivi,
 * condivisione e coda.
 */
@Composable
private fun BottomRow(
    sleepTimerRemainingMs: Long?,
    levels: AudioLevels,
    isPlaying: Boolean,
    glowMode: GlowMode,
    content: Color,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            sleepTimerRemainingMs != null -> Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, content.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .clickable(onClick = onOpenSleepTimer)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentDot(size = 5.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = formatDuration(sleepTimerRemainingMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                )
            }

            // Il misuratore ha senso solo quando i livelli audio vengono
            // letti: negli altri stati resterebbe inchiodato al minimo.
            glowMode == GlowMode.ANIMATED -> Box(modifier = Modifier.padding(start = 12.dp)) {
                LevelMeter(levels = levels, isPlaying = isPlaying, content = content)
            }
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onOpenQueue) {
            Icon(
                imageVector = XaosIcons.Queue,
                contentDescription = "Coda di riproduzione",
                tint = content,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Luminanza a cui nero e bianco hanno lo stesso contrasto su un fondo.
 *
 * Non è 0.5: il contrasto si calcola come rapporto fra luminanze aumentate di
 * 0.05, e da quel rapporto viene fuori la radice di 0.05·1.05, cioè circa 0.18.
 * Sopra questo valore va il nero, sotto il bianco.
 */
private const val CONTRAST_CROSSOVER = 0.179f

private const val HORIZONTAL_PADDING = 24

/** Spazio lasciato a destra del titolo per il cuore. */
private const val FAVORITE_ROOM = 48
private const val ARTWORK_MIN = 56

/**
 * Il pannello a player chiuso: due righe di testo e nient'altro.
 *
 * Tenuto al minimo che regge un verso lungo che va a capo. Più basso lo
 * taglierebbe; più alto ruberebbe schermo alla copertina e sposterebbe lì il
 * punto dove il dito comincia il gesto, che invece deve partire dal disco.
 */
private const val CLOSED_PANEL_HEIGHT = 50

/** Distanza fra il titolo e il verso in corso. */
private const val PANEL_GAP = 16
/** Titolo e artista sotto la copertina, quando è al massimo. */
private const val TITLE_BLOCK_HEIGHT = 74
private const val TITLE_SIZE_MAX = 26
private const val TITLE_SIZE_MIN = 16
private const val ARTIST_SIZE_MAX = 18
private const val ARTIST_SIZE_MIN = 12
