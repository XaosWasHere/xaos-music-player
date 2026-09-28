package xaos.desktop.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xaos.desktop.Settings
import xaos.desktop.library.Library
import xaos.desktop.library.ScanState
import xaos.desktop.player.Player
import xaos.desktop.sync.PhoneState
import xaos.desktop.sync.PhoneSync
import xaos.desktop.online.OnlineTrack
import xaos.desktop.sync.PhoneFile
import xaos.desktop.online.YtDlp
import androidx.compose.runtime.CompositionLocalProvider
import xaos.desktop.theme.DotText
import xaos.desktop.theme.Xaos

/** Le sezioni della barra laterale. */
enum class Section(val label: String, val icon: ImageVector) {
    ALBUMS("ALBUM", XaosIcons.Album),
    SONGS("BRANI", XaosIcons.MusicNote),
    ARTISTS("ARTISTI", XaosIcons.Person),
    PHONE("TELEFONO", XaosIcons.Phone),
    SETTINGS("IMPOSTAZIONI", XaosIcons.Settings),
}

/** Le sezioni della navigazione principale; le impostazioni stanno in fondo, a parte. */
private val MainSections = listOf(Section.ALBUMS, Section.SONGS, Section.ARTISTS, Section.PHONE)

/** Le schermate di dettaglio, impilate sopra la sezione. */
sealed interface Detail {
    data class AlbumDetail(val key: String) : Detail
    data class ArtistDetail(val name: String) : Detail
    data class EditTrack(val path: String) : Detail
    data class EditAlbum(val key: String) : Detail
    data class Lyrics(val path: String) : Detail
}

@Composable
fun XaosDesktopApp(
    settings: Settings,
    library: Library,
    player: Player,
    phone: PhoneSync,
    ytdlp: YtDlp,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onRescan: () -> Unit,
    onPickDownloadFolder: () -> Unit,
    onDownload: (OnlineTrack) -> Unit,
    onImport: (List<PhoneFile>) -> Unit,
    onPickImportFolder: () -> Unit,
) {
    val prefs by settings.data.collectAsState()
    val snapshot by library.snapshot.collectAsState()
    val scan by library.scan.collectAsState()
    val phoneState by phone.state.collectAsState()

    // XAOS_START apre direttamente una sezione: serve solo per provare l'app
    // durante lo sviluppo, senza dover navigare a mano.
    var section by remember {
        mutableStateOf(Section.entries.firstOrNull { it.name == System.getenv("XAOS_START") } ?: Section.ALBUMS)
    }
    val details = remember { mutableStateListOf<Detail>() }
    var query by remember { mutableStateOf("") }

    fun select(target: Section) {
        section = target
        details.clear()
        query = ""
    }

    // Le azioni del menu dei brani, uguali in ogni elenco.
    val trackActions = remember {
        TrackActions(
            onEdit = { details += Detail.EditTrack(it.path) },
            onLyrics = { details += Detail.Lyrics(it.path) },
            onShowInFolder = { t ->
                runCatching { ProcessBuilder("explorer.exe", "/select,", t.path).start() }
            },
        )
    }
    /** Dopo una modifica: si torna indietro e la libreria rilegge i file toccati. */
    fun afterEdit() {
        details.removeLastOrNull()
        onRescan()
    }

    val colors = Xaos.colors
    if (fullscreen) {
        FullscreenPlayer(
            player = player,
            background = prefs.fullscreenBackground,
            onBackgroundChange = { mode -> settings.update { it.copy(fullscreenBackground = mode) } },
            onClose = { onFullscreenChange(false) },
        )
        return
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(
                selected = section,
                phoneState = phoneState,
                scan = scan,
                trackCount = snapshot.tracks.size,
                onSelect = ::select,
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colors.background)
                    .dotGrid(colors.dot),
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (section != Section.PHONE && section != Section.SETTINGS) {
                        TopBar(
                            query = query,
                            onQueryChange = { query = it },
                            canGoBack = details.isNotEmpty(),
                            onBack = { details.removeLastOrNull() },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val detail = details.lastOrNull()
                        CompositionLocalProvider(LocalTrackActions provides trackActions) {
                        when {
                            detail is Detail.EditTrack -> EditTrackScreen(
                                track = snapshot.tracks.firstOrNull { it.path == detail.path },
                                onSaved = ::afterEdit,
                                onCancel = { details.removeLastOrNull() },
                            )
                            detail is Detail.EditAlbum -> EditAlbumScreen(
                                album = snapshot.albums.firstOrNull { it.key == detail.key },
                                onSaved = ::afterEdit,
                                onCancel = { details.removeLastOrNull() },
                            )
                            detail is Detail.Lyrics -> LyricsScreen(
                                track = snapshot.tracks.firstOrNull { it.path == detail.path },
                                onSaved = ::afterEdit,
                                onCancel = { details.removeLastOrNull() },
                            )
                            section == Section.PHONE -> PhoneScreen(
                                phone = phone,
                                snapshot = snapshot,
                                preferMp3 = prefs.preferMp3OnPhone,
                                phoneFolder = prefs.phoneFolder,
                                excluded = prefs.syncExcluded,
                                onExcludedChange = { set -> settings.update { it.copy(syncExcluded = set) } },
                                onOpenSettings = { select(Section.SETTINGS) },
                                importFolder = prefs.effectiveImportFolder,
                                importExcluded = prefs.importExcluded,
                                onImportExcludedChange = { set -> settings.update { it.copy(importExcluded = set) } },
                                onImport = onImport,
                            )
                            section == Section.SETTINGS -> SettingsScreen(
                                settings = settings,
                                library = library,
                                player = player,
                                phone = phone,
                                ytdlp = ytdlp,
                                onAddFolder = onAddFolder,
                                onRescan = onRescan,
                                onPickDownloadFolder = onPickDownloadFolder,
                                onPickImportFolder = onPickImportFolder,
                            )
                            query.isNotBlank() -> SearchResults(
                                query = query,
                                snapshot = snapshot,
                                player = player,
                                ytdlp = ytdlp,
                                onDownload = onDownload,
                                onOpenAlbum = { details += Detail.AlbumDetail(it.key); query = "" },
                                onOpenArtist = { details += Detail.ArtistDetail(it.name); query = "" },
                            )
                            detail is Detail.AlbumDetail -> AlbumDetailScreen(
                                album = snapshot.albums.firstOrNull { it.key == detail.key },
                                player = player,
                                onOpenArtist = { details += Detail.ArtistDetail(it) },
                                onEdit = { details += Detail.EditAlbum(it.key) },
                            )
                            detail is Detail.ArtistDetail -> ArtistDetailScreen(
                                artist = snapshot.artists.firstOrNull { it.name == detail.name },
                                player = player,
                                onOpenAlbum = { details += Detail.AlbumDetail(it.key) },
                            )
                            section == Section.ALBUMS -> AlbumsScreen(snapshot, scan) {
                                details += Detail.AlbumDetail(it.key)
                            }
                            section == Section.SONGS -> SongsScreen(snapshot, player)
                            section == Section.ARTISTS -> ArtistsScreen(snapshot) {
                                details += Detail.ArtistDetail(it.name)
                            }
                        }
                        }
                    }
                }
            }
        }
        PlayerBar(
            player = player,
            onVolumeChange = { v -> settings.update { it.copy(volume = v) } },
            onOpenFullscreen = { onFullscreenChange(true) },
        )
    }
}

@Composable
private fun Sidebar(
    selected: Section,
    phoneState: PhoneState,
    scan: ScanState,
    trackCount: Int,
    onSelect: (Section) -> Unit,
) {
    val colors = Xaos.colors
    Column(
        Modifier
            .width(236.dp)
            .fillMaxHeight()
            .background(colors.sidebar)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            DotText("XAOS", color = colors.ink, pitch = 4.dp)
            Spacer(Modifier.width(8.dp))
            AccentDot(size = 7.dp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("MUSIC PLAYER · DESKTOP", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)

        Spacer(Modifier.height(28.dp))

        MainSections.forEach { entry ->
            NavItem(
                section = entry,
                selected = entry == selected,
                badge = if (entry == Section.PHONE) phoneState is PhoneState.Connected else false,
                onClick = { onSelect(entry) },
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.weight(1f))

        PhoneStatusCard(phoneState, onClick = { onSelect(Section.PHONE) })
        Spacer(Modifier.height(12.dp))

        NavItem(
            section = Section.SETTINGS,
            selected = selected == Section.SETTINGS,
            badge = false,
            onClick = { onSelect(Section.SETTINGS) },
        )
        Text(
            text = when (scan) {
                is ScanState.Scanning -> "SCANSIONE ${scan.done}/${scan.total}"
                is ScanState.Failed -> "ERRORE DI LETTURA"
                ScanState.Idle -> "[$trackCount] BRANI IN LIBRERIA"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkTertiary,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp),
        )
    }
}

@Composable
private fun NavItem(section: Section, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val colors = Xaos.colors
    val tint by animateColorAsState(if (selected) colors.ink else colors.inkSecondary, label = "nav")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverRow(selected)
            .pressable(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(section.label, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.weight(1f))
        if (selected) AccentDot(size = 6.dp)
        else if (badge) AccentDot(size = 6.dp, color = colors.inkSecondary)
    }
}

@Composable
private fun PhoneStatusCard(state: PhoneState, onClick: () -> Unit) {
    val colors = Xaos.colors
    val (title, subtitle, live) = when (state) {
        is PhoneState.Connected -> Triple(state.name.uppercase(), "COLLEGATO", true)
        is PhoneState.Unauthorized -> Triple("TELEFONO", "CONFERMA SUL TELEFONO", false)
        PhoneState.Disconnected -> Triple("NESSUN TELEFONO", "COLLEGA VIA USB", false)
        PhoneState.NoAdb -> Triple("ADB NON TROVATO", "SERVE ANDROID PLATFORM-TOOLS", false)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .nothingCard(RoundedCornerShape(14.dp))
            .pressable(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XaosIcons.Phone, null, tint = if (live) colors.ink else colors.inkTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, maxLines = 1)
        }
        AccentDot(size = 7.dp, color = if (live) colors.accent else colors.track)
    }
}

@Composable
private fun TopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    val colors = Xaos.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            CircleIconButton(XaosIcons.Back, "Indietro", onBack)
            Spacer(Modifier.width(12.dp))
        }
        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .width(360.dp)
                .clip(shape)
                .background(colors.card, shape)
                .border(1.dp, if (query.isNotEmpty()) colors.accent else colors.line, shape)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(XaosIcons.Search, null, tint = colors.inkSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Cerca brani, album, artisti", style = MaterialTheme.typography.bodyMedium, color = colors.inkTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accentInk),
                    keyboardActions = KeyboardActions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    XaosIcons.Close, "Cancella",
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(16.dp).pressable { onQueryChange("") },
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Titolo di schermata: la parola in matrice di punti e una riga tecnica sotto. */
@Composable
fun ScreenTitle(title: String, caption: String? = null, trailing: @Composable (() -> Unit)? = null) {
    val colors = Xaos.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            DotText(title, color = colors.ink, pitch = 5.dp)
            if (caption != null) {
                Spacer(Modifier.height(10.dp))
                Text(caption, style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyMessage(title: String, body: String? = null) {
    val colors = Xaos.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.inkTertiary)
        }
    }
}
