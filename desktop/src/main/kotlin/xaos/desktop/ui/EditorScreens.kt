package xaos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xaos.desktop.library.Album
import xaos.desktop.library.TagEdit
import xaos.desktop.library.TagEditor
import xaos.desktop.library.Track
import xaos.desktop.online.LrcLib
import xaos.desktop.online.LrcResult
import xaos.desktop.theme.Xaos
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Modifica di un brano: i tag scritti nel file (e nella sua copia MP3, se c'è).
 * [onSaved] riceve il via libera a rileggere la libreria.
 */
@Composable
fun EditTrackScreen(track: Track?, onSaved: () -> Unit, onCancel: () -> Unit) {
    if (track == null) { EmptyMessage("BRANO NON TROVATO"); return }
    val colors = Xaos.colors
    val scope = rememberCoroutineScope()
    var title by remember(track.path) { mutableStateOf(track.title) }
    var artist by remember(track.path) { mutableStateOf(track.artist) }
    var albumArtist by remember(track.path) { mutableStateOf(track.albumArtist) }
    var album by remember(track.path) { mutableStateOf(track.album) }
    var genre by remember(track.path) { mutableStateOf(track.genre.orEmpty()) }
    var year by remember(track.path) { mutableStateOf(track.year.takeIf { it > 0 }?.toString().orEmpty()) }
    var number by remember(track.path) { mutableStateOf(track.trackNumber.takeIf { it > 0 }?.toString().orEmpty()) }
    var artwork by remember(track.path) { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    EditorFrame(
        tag = "MODIFICA BRANO",
        title = track.title,
        subtitle = File(track.path).name + (track.mobilePath?.let { "  +  copia MP3" } ?: ""),
        saving = saving,
        error = error,
        onSave = {
            saving = true
            scope.launch {
                val edit = TagEdit(
                    title = title.takeIf { it != track.title },
                    artist = artist.takeIf { it != track.artist },
                    albumArtist = albumArtist.takeIf { it != track.albumArtist },
                    album = album.takeIf { it != track.album },
                    genre = genre.takeIf { it != track.genre.orEmpty() },
                    year = year.takeIf { it != (track.year.takeIf { y -> y > 0 }?.toString().orEmpty()) },
                    trackNumber = number.takeIf { it != (track.trackNumber.takeIf { n -> n > 0 }?.toString().orEmpty()) },
                    artwork = artwork,
                )
                TagEditor.write(track, edit)
                    .onSuccess { onSaved() }
                    .onFailure { error = it.message ?: "Scrittura non riuscita"; saving = false }
            }
        },
        onCancel = onCancel,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ArtworkPicker(track, artwork) { artwork = it }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                XaosTextField("TITOLO", title, { title = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    XaosTextField("ARTISTA", artist, { artist = it }, Modifier.weight(1f))
                    XaosTextField("ARTISTA DELL'ALBUM", albumArtist, { albumArtist = it }, Modifier.weight(1f))
                }
                XaosTextField("ALBUM", album, { album = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    XaosTextField("GENERE", genre, { genre = it }, Modifier.weight(1f))
                    XaosTextField("ANNO", year, { year = it.filter(Char::isDigit).take(4) }, Modifier.width(110.dp))
                    XaosTextField("N. TRACCIA", number, { number = it.filter(Char::isDigit).take(3) }, Modifier.width(110.dp))
                }
                Text(
                    "Le modifiche vanno nel file" + (if (track.mobilePath != null) " e nella sua copia MP3" else "") +
                        ". Al prossimo collegamento il telefono riceve la versione aggiornata.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkTertiary,
                )
            }
        }
    }
}

/** Modifica di un album intero: i campi comuni, scritti in tutte le tracce. */
@Composable
fun EditAlbumScreen(album: Album?, onSaved: () -> Unit, onCancel: () -> Unit) {
    if (album == null) { EmptyMessage("ALBUM NON TROVATO"); return }
    val colors = Xaos.colors
    val scope = rememberCoroutineScope()
    val first = album.tracks.first()
    var title by remember(album.key) { mutableStateOf(album.title) }
    var artist by remember(album.key) { mutableStateOf(album.artist) }
    val commonGenre = album.tracks.mapNotNull { it.genre }.distinct().singleOrNull().orEmpty()
    var genre by remember(album.key) { mutableStateOf(commonGenre) }
    var year by remember(album.key) { mutableStateOf(album.year.takeIf { it > 0 }?.toString().orEmpty()) }
    var artwork by remember(album.key) { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    // L'ordine delle tracce, come nell'editor del telefono: si trascina.
    val ordered = remember(album.key) { androidx.compose.runtime.mutableStateListOf(*album.tracks.toTypedArray()) }
    val multiDisc = album.tracks.map { it.discNumber }.distinct().size > 1

    EditorFrame(
        tag = "MODIFICA ALBUM",
        title = album.title,
        subtitle = "[${album.tracks.size}] BRANI",
        saving = saving,
        savingLabel = "SCRITTURA $progress/${album.tracks.size}",
        error = error,
        onSave = {
            saving = true
            scope.launch {
                val edit = TagEdit(
                    album = title.takeIf { it != album.title },
                    albumArtist = artist.takeIf { it != album.artist },
                    genre = genre.takeIf { it != commonGenre },
                    year = year.takeIf { it != (album.year.takeIf { y -> y > 0 }?.toString().orEmpty()) },
                    artwork = artwork,
                )
                var failure: Throwable? = null
                // Il nuovo ordine diventa il numero di traccia; con più dischi
                // diventa un'unica sequenza, altrimenti l'ordine per disco
                // prevarrebbe su quello scelto.
                val reordered = ordered.toList() != album.tracks
                ordered.forEachIndexed { i, t ->
                    progress = i + 1
                    val number = i + 1
                    val trackEdit = if (!reordered || (t.trackNumber == number && !multiDisc)) edit
                    else edit.copy(trackNumber = number.toString(), discNumber = if (multiDisc) "1" else null)
                    TagEditor.write(t, trackEdit).onFailure { failure = it }
                }
                if (failure == null) onSaved()
                else { error = failure?.message ?: "Alcuni file non sono stati scritti"; saving = false }
            }
        },
        onCancel = onCancel,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ArtworkPicker(first, artwork) { artwork = it }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                XaosTextField("ALBUM", title, { title = it })
                XaosTextField("ARTISTA DELL'ALBUM", artist, { artist = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    XaosTextField("GENERE", genre, { genre = it }, Modifier.weight(1f), placeholder = if (commonGenre.isEmpty()) "diversi" else "")
                    XaosTextField("ANNO", year, { year = it.filter(Char::isDigit).take(4) }, Modifier.width(110.dp))
                }
                Text(
                    "Titolo e artista del singolo brano non cambiano: per quelli si modifica il brano.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkTertiary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ORDINE DELLE TRACCE", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, modifier = Modifier.weight(1f))
            if (ordered.toList() != album.tracks) {
                PillButton("RIPRISTINA ORDINE", onClick = { ordered.clear(); ordered.addAll(album.tracks) })
            }
        }
        Text(
            "Trascina la maniglia o usa le frecce. Salvando, il numero di traccia di ogni brano diventa la sua posizione" +
                if (multiDisc) " (i dischi diventano un'unica sequenza)." else ".",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        ReorderableTracks(ordered)
    }
}

/**
 * Le tracce riordinabili: la maniglia si trascina come sul telefono, le
 * frecce spostano di un posto. Righe ad altezza fissa, così dal trascinamento
 * si ricava la posizione d'arrivo con una divisione.
 */
@Composable
private fun ReorderableTracks(ordered: androidx.compose.runtime.snapshots.SnapshotStateList<Track>) {
    val colors = Xaos.colors
    val rowHeight = 52.dp
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowHeight.toPx() }
    var dragged by remember { mutableStateOf(-1) }
    var offset by remember { mutableStateOf(0f) }

    Column(Modifier.fillMaxWidth()) {
        ordered.forEachIndexed { index, track ->
            val isDragged = index == dragged
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) offset else 0f }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDragged) colors.surfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
                    .hoverRow(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    XaosIcons.DragHandle,
                    "Trascina per riordinare",
                    tint = colors.inkSecondary,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(20.dp)
                        .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.N_RESIZE_CURSOR)))
                        .pointerInput(ordered.size) {
                            detectDragGestures(
                                onDragStart = { dragged = index; offset = 0f },
                                onDragEnd = { dragged = -1; offset = 0f },
                                onDragCancel = { dragged = -1; offset = 0f },
                            ) { change, amount ->
                                change.consume()
                                offset += amount.y
                                val steps = (offset / rowHeightPx).toInt()
                                if (steps == 0) return@detectDragGestures
                                val from = dragged
                                val to = (from + steps).coerceIn(ordered.indices)
                                if (to == from) return@detectDragGestures
                                ordered.add(to, ordered.removeAt(from))
                                dragged = to
                                offset -= (to - from) * rowHeightPx
                            }
                        },
                )
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accentInk,
                    modifier = Modifier.width(36.dp),
                )
                ArtworkImage(track, size = 34.dp, corner = 6.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary, modifier = Modifier.padding(horizontal = 10.dp))
                CircleIconButton(XaosIcons.ArrowUp, "Sposta su", {
                    if (index > 0) ordered.add(index - 1, ordered.removeAt(index))
                }, size = 30.dp, outlined = false, tint = if (index > 0) colors.inkSecondary else colors.track)
                CircleIconButton(XaosIcons.ArrowDown, "Sposta giù", {
                    if (index < ordered.lastIndex) ordered.add(index + 1, ordered.removeAt(index))
                }, size = 30.dp, outlined = false, tint = if (index < ordered.lastIndex) colors.inkSecondary else colors.track)
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

/**
 * Il testo di un brano: si scrive a mano o si prende da LRCLIB, semplice o
 * sincronizzato. Si salva nel tag del file e della copia MP3, da dove lo legge
 * l'app sul telefono.
 */
@Composable
fun LyricsScreen(track: Track?, onSaved: () -> Unit, onCancel: () -> Unit) {
    if (track == null) { EmptyMessage("BRANO NON TROVATO"); return }
    val colors = Xaos.colors
    val scope = rememberCoroutineScope()
    var text by remember(track.path) { mutableStateOf("") }
    var loaded by remember(track.path) { mutableStateOf(false) }
    var results by remember(track.path) { mutableStateOf<List<LrcResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(track.path) {
        text = TagEditor.readLyrics(track).orEmpty()
        loaded = true
    }
    val synced = remember(text) { TIMESTAMP.containsMatchIn(text) }

    EditorFrame(
        tag = "TESTO",
        title = track.title,
        subtitle = track.artist,
        saving = saving,
        error = error,
        onSave = {
            saving = true
            scope.launch {
                TagEditor.writeLyrics(track, text)
                    .onSuccess { onSaved() }
                    .onFailure { error = it.message ?: "Scrittura non riuscita"; saving = false }
            }
        },
        onCancel = onCancel,
        scrollable = false,
    ) {
        // Largo: testo a sinistra e risultati di LRCLIB a destra. Stretto: i
        // risultati scendono sotto il testo, invece di schiacciarlo.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 780.dp
        val resultsWidth = minOf(380.dp, maxWidth * 0.42f)
        val editor = @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (text.isNotBlank()) Tag(if (synced) "SINCRONIZZATO" else "SENZA TEMPI")
                    Spacer(Modifier.weight(1f))
                    PillButton(
                        if (searching) "RICERCA…" else "CERCA ONLINE",
                        onClick = {
                            searching = true
                            scope.launch {
                                results = LrcLib.search(track.title, track.artist, track.durationMs)
                                searching = false
                            }
                        },
                        icon = XaosIcons.Search,
                        enabled = !searching,
                    )
                    Spacer(Modifier.width(8.dp))
                    PillButton("SVUOTA", onClick = { text = "" }, enabled = text.isNotEmpty())
                }
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.card, shape)
                        .border(1.dp, colors.line, shape)
                        .padding(16.dp),
                ) {
                    if (loaded && text.isEmpty()) {
                        Text(
                            "Nessun testo. Scrivilo qui, oppure cercalo online.\n\n" +
                                "Per un testo sincronizzato ogni riga comincia col suo tempo:\n[01:23.45] così",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkTertiary,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.accentInk),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        val found = @Composable { modifier: Modifier ->
            results?.let { list ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("TROVATI SU LRCLIB", count = list.size)
                    if (list.isEmpty()) {
                        Text("Niente per questo brano.", style = MaterialTheme.typography.bodyMedium, color = colors.inkTertiary)
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { r ->
                            LrcCard(r, trackDurationMs = track.durationMs) { chosen -> text = chosen }
                        }
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                editor(Modifier.weight(1f).fillMaxHeight())
                if (results != null) found(Modifier.width(resultsWidth).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                editor(Modifier.weight(1f).fillMaxWidth())
                if (results != null) found(Modifier.fillMaxWidth().weight(0.8f))
            }
        }
        }
    }
}

@Composable
private fun LrcCard(result: LrcResult, trackDurationMs: Long, onUse: (String) -> Unit) {
    val colors = Xaos.colors
    val diff = ((result.duration ?: 0.0) * 1000 - trackDurationMs).let { kotlin.math.abs(it) / 1000 }
    Column(Modifier.fillMaxWidth().nothingCard(RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(result.trackName.orEmpty(), style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(result.artistName, result.albumName).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatDuration(((result.duration ?: 0.0) * 1000).toLong()) +
                // La durata dice se è la stessa versione del brano.
                if (diff <= 2) "  ·  STESSA DURATA" else "  ·  ${diff.toInt()}s DI DIFFERENZA",
            style = MaterialTheme.typography.labelSmall,
            color = if (diff <= 2) colors.inkSecondary else colors.inkTertiary,
        )
        val preview = (result.plainLyrics ?: result.syncedLyrics.orEmpty().replace(TIMESTAMP, "")).lines()
            .map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(" / ")
        if (preview.isNotEmpty()) {
            Text(preview, style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            result.syncedLyrics?.takeIf { it.isNotBlank() }?.let { PillButton("USA SINCRONIZZATO", onClick = { onUse(it) }, filled = true) }
            result.plainLyrics?.takeIf { it.isNotBlank() }?.let { PillButton("USA TESTO", onClick = { onUse(it) }) }
        }
    }
}

/** Intestazione, contenuto e bottoni comuni alle tre schermate di modifica. */
@Composable
private fun EditorFrame(
    tag: String,
    title: String,
    subtitle: String,
    saving: Boolean,
    error: String?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    savingLabel: String = "SALVATAGGIO…",
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = Xaos.colors
    Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(tag)
                Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (saving) {
                    DotSpinner(size = 20.dp)
                    Text(savingLabel, style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
                } else {
                    PillButton("ANNULLA", onClick = onCancel)
                    PillButton("SALVA", onClick = onSave, icon = XaosIcons.Check, filled = true)
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text("ERRORE · $error", style = MaterialTheme.typography.labelLarge, color = colors.accentInk)
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) {
            Box(Modifier.widthIn(max = if (scrollable) 980.dp else 1600.dp)) { content() }
        }
    }
}

/** La copertina, cliccabile per sceglierne un'altra dal disco. */
@Composable
private fun ArtworkPicker(track: Track, chosen: File?, onPick: (File) -> Unit) {
    val colors = Xaos.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.pressable { pickImage()?.let(onPick) }) {
            if (chosen != null) {
                val bitmap = remember(chosen) {
                    runCatching { org.jetbrains.skia.Image.makeFromEncoded(chosen.readBytes()) }.getOrNull()
                        ?.toComposeImageBitmap()
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap, null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(20.dp)),
                    )
                }
            } else {
                ArtworkImage(track, size = 220.dp, corner = 20.dp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(XaosIcons.Edit, null, tint = colors.inkTertiary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (chosen != null) "NUOVA: ${chosen.name.uppercase()}" else "CLICCA PER CAMBIARE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

internal fun pickImage(): File? {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val chooser = JFileChooser().apply {
        dialogTitle = "Scegli la copertina"
        fileFilter = FileNameExtensionFilter("Immagini (JPG, PNG)", "jpg", "jpeg", "png")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private val TIMESTAMP = Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]""")
