package xaos.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import xaos.desktop.library.UserData
import xaos.desktop.sync.DataSync
import androidx.compose.runtime.CompositionLocalProvider
import xaos.desktop.theme.DotText
import xaos.desktop.theme.Xaos
import xaos.desktop.theme.appBackground

/** Le sezioni della barra laterale. */
enum class Section(val label: String, val icon: ImageVector) {
    ALBUMS("ALBUM", XaosIcons.Album),
    SONGS("BRANI", XaosIcons.MusicNote),
    ARTISTS("ARTISTI", XaosIcons.Person),
    FAVORITES("PREFERITI", XaosIcons.Favorite),
    PLAYLISTS("PLAYLIST", XaosIcons.PlaylistMusic),
    STATS("ASCOLTI", XaosIcons.Stats),
    PHONE("TELEFONO", XaosIcons.Phone),
    SETTINGS("IMPOSTAZIONI", XaosIcons.Settings),
}

/** Le sezioni della navigazione principale; le impostazioni stanno in fondo, a parte. */
private val MainSections = listOf(
    Section.ALBUMS, Section.SONGS, Section.ARTISTS,
    Section.FAVORITES, Section.PLAYLISTS, Section.STATS,
    Section.PHONE,
)

/** Le schermate di dettaglio, impilate sopra la sezione. */
sealed interface Detail {
    data class AlbumDetail(val key: String) : Detail
    data class ArtistDetail(val name: String) : Detail
    data class EditTrack(val path: String) : Detail
    data class EditAlbum(val key: String) : Detail
    data class Lyrics(val path: String) : Detail
    data class PlaylistDetail(val id: String) : Detail
}

@Composable
fun XaosDesktopApp(
    settings: Settings,
    library: Library,
    player: Player,
    phone: PhoneSync,
    ytdlp: YtDlp,
    userData: UserData,
    dataSync: DataSync,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    /** Lo schermo su cui sta la finestra principale: il player a tutto schermo si apre lì. */
    screenBounds: () -> java.awt.Rectangle?,
    appIcon: androidx.compose.ui.graphics.painter.Painter,
    onOpenMini: () -> Unit,
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
    val data by userData.state.collectAsState()
    // I preferiti risolti sui brani della libreria: un preferito salvato con il
    // percorso della copia MP3 o di un doppione vale per il brano mostrato.
    val favorites = remember(data.favorites, snapshot) {
        data.favorites.mapNotNull { snapshot.byPath[it]?.path }.toSet()
    }
    var lyricsOpen by remember { mutableStateOf(false) }
    // Una volta all'avvio: c'è una release più nuova di questa su GitHub?
    val latest by androidx.compose.runtime.produceState<xaos.desktop.online.ReleaseCheck.Latest?>(null) {
        value = xaos.desktop.online.ReleaseCheck.latest()
    }
    var addToPlaylist by remember { mutableStateOf<List<xaos.desktop.library.Track>?>(null) }

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

    // Portano all'album o all'artista di un brano, anche dallo schermo intero.
    fun goToAlbum(t: xaos.desktop.library.Track) {
        val album = library.snapshot.value.albums.firstOrNull { a -> a.tracks.any { it.path == t.path } } ?: return
        onFullscreenChange(false)
        select(Section.ALBUMS)
        details += Detail.AlbumDetail(album.key)
    }

    fun goToArtist(t: xaos.desktop.library.Track) {
        val snap = library.snapshot.value
        val artist = snap.artists.firstOrNull { a -> a.albums.any { al -> al.tracks.any { it.path == t.path } } } ?: return
        onFullscreenChange(false)
        select(Section.ARTISTS)
        details += Detail.ArtistDetail(artist.name)
    }

    /** "In riproduzione da": si torna dove la coda è partita. */
    fun openSource(source: xaos.desktop.player.PlaySource?, current: xaos.desktop.library.Track?) {
        when (source?.kind) {
            xaos.desktop.player.PlaySource.Kind.ALBUM -> { onFullscreenChange(false); select(Section.ALBUMS); details += Detail.AlbumDetail(source.id) }
            xaos.desktop.player.PlaySource.Kind.PLAYLIST -> { onFullscreenChange(false); select(Section.PLAYLISTS); details += Detail.PlaylistDetail(source.id) }
            xaos.desktop.player.PlaySource.Kind.FAVORITES -> { onFullscreenChange(false); select(Section.FAVORITES) }
            xaos.desktop.player.PlaySource.Kind.SONGS -> { onFullscreenChange(false); select(Section.SONGS) }
            xaos.desktop.player.PlaySource.Kind.ARTIST -> { onFullscreenChange(false); select(Section.ARTISTS); details += Detail.ArtistDetail(source.id) }
            xaos.desktop.player.PlaySource.Kind.STATS -> { onFullscreenChange(false); select(Section.STATS) }
            xaos.desktop.player.PlaySource.Kind.SEARCH -> { onFullscreenChange(false); select(Section.SONGS); query = source.id }
            null -> current?.let { goToAlbum(it) }
        }
    }

    // Le azioni del menu dei brani, uguali in ogni elenco.
    val trackActions = remember {
        TrackActions(
            onEdit = { details += Detail.EditTrack(it.path) },
            onLyrics = { details += Detail.Lyrics(it.path) },
            onShowInFolder = { t ->
                runCatching { ProcessBuilder("explorer.exe", "/select,", t.path).start() }
            },
            onToggleFavorite = { userData.toggleFavorite(it) },
            onAddToPlaylist = { addToPlaylist = it },
            onGoToAlbum = { t -> goToAlbum(t) },
            onGoToArtist = { t -> goToArtist(t) },
        )
    }
    /** Dopo una modifica: si torna indietro e la libreria rilegge i file toccati. */
    fun afterEdit() {
        details.removeLastOrNull()
        onRescan()
    }

    addToPlaylist?.let { tracks ->
        AddToPlaylistDialog(tracks, data, userData) { addToPlaylist = null }
    }

    val colors = Xaos.colors
    if (fullscreen) {
        // Una finestra a parte, senza bordi e grande quanto lo schermo. Lo
        // schermo intero "esclusivo" di Java si riduce a icona appena si passa a
        // un'altra app; questa invece resta lì, come qualunque finestra.
        val bounds = remember {
            screenBounds() ?: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.bounds
        }
        val state = androidx.compose.ui.window.rememberWindowState(
            position = androidx.compose.ui.window.WindowPosition(bounds.x.dp, bounds.y.dp),
            size = androidx.compose.ui.unit.DpSize(bounds.width.dp, bounds.height.dp),
        )
        androidx.compose.ui.window.Window(
            onCloseRequest = { onFullscreenChange(false) },
            state = state,
            title = "Xaos",
            icon = appIcon,
            undecorated = true,
            resizable = false,
            onPreviewKeyEvent = { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@Window false
                when (event.key) {
                    androidx.compose.ui.input.key.Key.Escape, androidx.compose.ui.input.key.Key.F11 -> { onFullscreenChange(false); true }
                    androidx.compose.ui.input.key.Key.Spacebar -> { player.togglePlayPause(); true }
                    androidx.compose.ui.input.key.Key.DirectionRight -> { player.seekBy(10_000); true }
                    androidx.compose.ui.input.key.Key.DirectionLeft -> { player.seekBy(-10_000); true }
                    else -> false
                }
            },
        ) {
            val base = androidx.compose.ui.platform.LocalDensity.current
            val scaled = androidx.compose.ui.unit.Density(base.density * prefs.uiScale, base.fontScale)
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides scaled,
                LocalTrackActions provides trackActions,
                LocalFavorites provides favorites,
            ) {
                xaos.desktop.theme.XaosTheme(dark = prefs.dark, custom = prefs.customTheme) {
                    FullscreenPlayer(
                        player = player,
                        background = prefs.fullscreenBackground,
                        onBackgroundChange = { mode -> settings.update { it.copy(fullscreenBackground = mode) } },
                        onClose = { onFullscreenChange(false) },
                        lyricsOpen = lyricsOpen,
                        onToggleLyrics = { lyricsOpen = !lyricsOpen },
                        onEditLyrics = { t -> onFullscreenChange(false); details += Detail.Lyrics(t.path) },
                        onOpenSource = { openSource(player.source.value, player.current.value) },
                    )
                }
            }
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val windowWidth = maxWidth
    Column(Modifier.fillMaxSize().appBackground()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(
                latest = latest,
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
                    .dotGrid(colors.dot),
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (section != Section.PHONE && section != Section.SETTINGS && section != Section.STATS) {
                        TopBar(
                            query = query,
                            onQueryChange = { query = it },
                            canGoBack = details.isNotEmpty(),
                            onBack = { details.removeLastOrNull() },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val detail = details.lastOrNull()
                        CompositionLocalProvider(LocalTrackActions provides trackActions, LocalFavorites provides favorites) {
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
                            detail is Detail.PlaylistDetail -> PlaylistDetailScreen(
                                id = detail.id,
                                snapshot = snapshot,
                                data = data,
                                userData = userData,
                                player = player,
                                onDeleted = { details.removeLastOrNull() },
                            )
                            section == Section.PHONE -> PhoneScreen(
                                phone = phone,
                                dataSync = dataSync,
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
                                deskTheme = prefs.customTheme,
                                dark = prefs.dark,
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
                            section == Section.SONGS -> SongsScreen(
                                snapshot, player,
                                sort = prefs.songSort,
                                descending = prefs.songSortDescending,
                                // Un clic ordina, il secondo sulla stessa colonna inverte.
                                onSort = { key ->
                                    settings.update {
                                        if (it.songSort == key) it.copy(songSortDescending = !it.songSortDescending)
                                        else it.copy(songSort = key, songSortDescending = false)
                                    }
                                },
                            )
                            section == Section.ARTISTS -> ArtistsScreen(snapshot) {
                                details += Detail.ArtistDetail(it.name)
                            }
                            section == Section.FAVORITES -> FavoritesScreen(snapshot, data, player)
                            section == Section.PLAYLISTS -> PlaylistsScreen(snapshot, data, userData) {
                                details += Detail.PlaylistDetail(it)
                            }
                            section == Section.STATS -> StatsScreen(snapshot, data, userData, player)
                        }
                        }
                    }
                }
            }
            // La barra dei testi si fa da parte quando al contenuto resterebbe
            // troppo poco spazio, e mentre si sta già modificando un testo.
            val panelWidth = if (windowWidth < 1180.dp) 280.dp else 360.dp
            val panelFits = windowWidth - 236.dp - panelWidth >= 560.dp
            if (lyricsOpen && panelFits && details.lastOrNull() !is Detail.Lyrics) {
                LyricsSidePanel(
                    player = player,
                    width = panelWidth,
                    onClose = { lyricsOpen = false },
                    onEdit = { t -> details += Detail.Lyrics(t.path) },
                )
            }
        }
        CompositionLocalProvider(LocalTrackActions provides trackActions, LocalFavorites provides favorites) {
            PlayerBar(
                player = player,
                onVolumeChange = { v -> settings.update { it.copy(volume = v) } },
                onOpenFullscreen = { onFullscreenChange(true) },
                lyricsOpen = lyricsOpen,
                onToggleLyrics = { lyricsOpen = !lyricsOpen },
                onOpenMini = onOpenMini,
            )
        }
    }
    }
}

@Composable
private fun Sidebar(
    latest: xaos.desktop.online.ReleaseCheck.Latest?,
    selected: Section,
    phoneState: PhoneState,
    scan: ScanState,
    trackCount: Int,
    onSelect: (Section) -> Unit,
) {
    val colors = Xaos.colors
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.width(236.dp).fillMaxHeight()) {
    // Finestra bassa: voci più strette e niente scheda del telefono (la
    // raggiunge comunque la voce Telefono), così tutto resta visibile.
    val compact = maxHeight < 600.dp
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.sidebar)
            .padding(horizontal = 16.dp, vertical = if (compact) 14.dp else 20.dp),
    ) {
        // Il logo porta al progetto su GitHub; se c'è una versione più nuova il
        // pallino pulsa e il clic apre direttamente la sua pagina.
        val update = latest?.takeIf { it.isNewer }
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .pressable {
                    xaos.desktop.online.ReleaseCheck.open(update?.url ?: xaos.desktop.online.ReleaseCheck.REPO_URL)
                },
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                DotText("XAOS", color = colors.ink, pitch = 4.dp)
                Spacer(Modifier.width(8.dp))
                if (update != null) PulsingDot(Modifier.padding(bottom = 2.dp))
                else AccentDot(size = 7.dp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (update != null) "NUOVA VERSIONE ${update.version} · SCARICALA" else "MUSIC PLAYER · DESKTOP",
                style = MaterialTheme.typography.labelSmall,
                color = if (update != null) colors.accentInk else colors.inkTertiary,
            )
        }

        Spacer(Modifier.height(if (compact) 16.dp else 28.dp))

        MainSections.forEach { entry ->
            NavItem(
                section = entry,
                selected = entry == selected,
                badge = if (entry == Section.PHONE) phoneState is PhoneState.Connected else false,
                onClick = { onSelect(entry) },
                compact = compact,
            )
            Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        }

        Spacer(Modifier.weight(1f))

        if (!compact) {
            PhoneStatusCard(phoneState, onClick = { onSelect(Section.PHONE) })
            Spacer(Modifier.height(12.dp))
        }

        NavItem(
            section = Section.SETTINGS,
            selected = selected == Section.SETTINGS,
            badge = false,
            onClick = { onSelect(Section.SETTINGS) },
            compact = compact,
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
}

/** Il pallino del logo quando c'è un aggiornamento: respira, con un alone. */
@Composable
private fun PulsingDot(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "update")
    val t by transition.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "pulse",
    )
    val colors = Xaos.colors
    Box(modifier.size(7.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(7.dp)) {
            val r = size.minDimension / 2
            drawCircle(colors.accent.copy(alpha = (1f - t) * 0.5f), r * (1f + t * 1.6f))
            drawCircle(colors.accent, r * (0.85f + 0.15f * kotlin.math.sin(t * Math.PI.toFloat())))
        }
    }
}

@Composable
private fun NavItem(section: Section, selected: Boolean, badge: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    val colors = Xaos.colors
    val tint by animateColorAsState(if (selected) colors.ink else colors.inkSecondary, label = "nav")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverRow(selected)
            .pressable(onClick)
            .padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 10.dp),
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
    // Con poco spazio i comandi a destra scendono sotto il titolo invece di schiacciarlo.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 860.dp
        val heading = @Composable {
            DotText(title, color = colors.ink, pitch = 5.dp)
            if (caption != null) {
                Spacer(Modifier.height(10.dp))
                Text(caption, style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 18.dp)) {
                heading()
                if (trailing != null) {
                    Spacer(Modifier.height(14.dp))
                    trailing()
                }
            }
        } else Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) { heading() }
            trailing?.invoke()
        }
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
