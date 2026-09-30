package xaos.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xaos.desktop.library.Lyrics
import xaos.desktop.library.TagEditor
import xaos.desktop.library.Track
import xaos.desktop.player.Player
import xaos.desktop.theme.Xaos

/** Cosa sappiamo del testo del brano: "non ancora letto" e "non c'è" sono diversi. */
sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data object Missing : LyricsLoad
    data class Ready(val lyrics: Lyrics) : LyricsLoad
}

/** Il testo di [track], riletto quando il brano cambia o viene modificato. */
@Composable
fun rememberLyrics(track: Track?): State<LyricsLoad> = produceState<LyricsLoad>(LyricsLoad.Loading, track) {
    value = LyricsLoad.Loading
    val raw = track?.let { TagEditor.readLyrics(it) }
    value = raw?.let { Lyrics.parse(it) }?.takeUnless { it.isEmpty }?.let { LyricsLoad.Ready(it) } ?: LyricsLoad.Missing
}

/**
 * Il testo che scorre con la musica.
 *
 * Con i tempi, la riga in corso è accesa e resta a un terzo dall'alto; le
 * altre si attenuano con la distanza. Un clic su una riga ci porta la
 * riproduzione. Se l'utente scorre a mano, il testo smette di seguirlo per
 * qualche secondo: rincorrerlo mentre legge sarebbe fastidioso.
 */
@Composable
fun LyricsView(
    player: Player,
    track: Track?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onEdit: ((Track) -> Unit)? = null,
) {
    val colors = Xaos.colors
    val load by rememberLyrics(track)
    val position by player.positionMs.collectAsState()

    when (val l = load) {
        LyricsLoad.Loading -> Box(modifier, contentAlignment = Alignment.Center) { DotSpinner(size = 20.dp) }
        LyricsLoad.Missing -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (track == null) "NIENTE IN RIPRODUZIONE" else "NESSUN TESTO", style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
                if (track != null && onEdit != null) {
                    Text(
                        "Puoi cercarlo online o scriverlo tu: finisce nel file, e arriva anche sul telefono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkTertiary,
                        modifier = Modifier.width(240.dp),
                    )
                    PillButton("AGGIUNGI IL TESTO", onClick = { onEdit(track) }, icon = XaosIcons.Mic)
                }
            }
        }
        is LyricsLoad.Ready -> {
            val lyrics = l.lyrics
            val current = lyrics.indexAt(position)
            val list = rememberLazyListState()
            var autoScrolling by remember { mutableStateOf(false) }
            var userScrolledAt by remember { mutableLongStateOf(0L) }

            // Chi scorre la lista? Se non siamo noi, è l'utente.
            LaunchedEffect(list) {
                snapshotFlow { list.isScrollInProgress }.collect { scrolling ->
                    if (scrolling && !autoScrolling) userScrolledAt = System.currentTimeMillis()
                }
            }
            LaunchedEffect(current, track?.path) {
                if (!lyrics.synced || current < 0) return@LaunchedEffect
                if (System.currentTimeMillis() - userScrolledAt < FOLLOW_PAUSE_MS) return@LaunchedEffect
                autoScrolling = true
                val visible = list.layoutInfo.visibleItemsInfo.size.coerceAtLeast(6)
                list.animateScrollToItem((current - visible / 3).coerceAtLeast(0))
                autoScrolling = false
            }
            LaunchedEffect(track?.path) { list.scrollToItem(0) }

            val base = if (large) MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 34.sp)
            else MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp)
            LazyColumn(
                modifier,
                state = list,
                contentPadding = PaddingValues(top = 12.dp, bottom = if (large) 160.dp else 80.dp),
                verticalArrangement = Arrangement.spacedBy(if (large) 14.dp else 8.dp),
            ) {
                if (!lyrics.synced) {
                    item {
                        Text("TESTO NON SINCRONIZZATO", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
                    }
                }
                itemsIndexed(lyrics.lines) { i, line ->
                    LyricRow(
                        text = line.text.ifBlank { "♪" },
                        style = base,
                        state = when {
                            !lyrics.synced -> LineState.PLAIN
                            i == current -> LineState.CURRENT
                            i < current -> LineState.PAST
                            else -> LineState.FUTURE
                        },
                        onClick = if (lyrics.synced) ({ player.seekToMs(line.timeMs) }) else null,
                    )
                }
            }
        }
    }
}

private enum class LineState { CURRENT, PAST, FUTURE, PLAIN }

@Composable
private fun LyricRow(text: String, style: TextStyle, state: LineState, onClick: (() -> Unit)?) {
    val colors = Xaos.colors
    val color by animateColorAsState(
        when (state) {
            LineState.CURRENT -> colors.ink
            LineState.PLAIN -> colors.inkSecondary
            LineState.PAST -> colors.inkTertiary.copy(alpha = 0.55f)
            LineState.FUTURE -> colors.inkTertiary
        },
        label = "lyric",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == LineState.CURRENT) {
            AccentDot(size = 6.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            style = style.copy(fontWeight = if (state == LineState.CURRENT) FontWeight.SemiBold else FontWeight.Normal),
            color = color,
        )
    }
}

/**
 * La barra laterale dei testi nella finestra normale, aperta dal microfono
 * della barra del player.
 */
@Composable
fun LyricsSidePanel(player: Player, width: Dp, onClose: () -> Unit, onEdit: (Track) -> Unit) {
    val colors = Xaos.colors
    val track by player.current.collectAsState()
    Row(Modifier.width(width).fillMaxHeight()) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
        Column(Modifier.fillMaxSize().background(colors.sidebar).padding(start = 20.dp, end = 16.dp, top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentDot(size = 6.dp)
                Spacer(Modifier.width(8.dp))
                Text("TESTO", style = MaterialTheme.typography.labelLarge, color = colors.ink, modifier = Modifier.weight(1f))
                track?.let { t ->
                    CircleIconButton(XaosIcons.Edit, "Modifica il testo", { onEdit(t) }, size = 30.dp, outlined = false)
                }
                CircleIconButton(XaosIcons.Close, "Chiudi", onClose, size = 30.dp, outlined = false)
            }
            track?.let { t ->
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Hairline()
            LyricsView(player, track, Modifier.fillMaxSize(), onEdit = onEdit)
        }
    }
}

private const val FOLLOW_PAUSE_MS = 4_000L
