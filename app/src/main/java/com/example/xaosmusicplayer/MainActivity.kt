package com.example.xaosmusicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.ui.components.CircleIconButton
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xaosmusicplayer.data.Playlist
import com.example.xaosmusicplayer.data.DeleteOutcome
import com.example.xaosmusicplayer.data.MediaDeleter
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.data.SongOverride
import com.example.xaosmusicplayer.online.DownloadState
import com.example.xaosmusicplayer.online.OnlineSearchState
import com.example.xaosmusicplayer.playback.PlayerViewModel
import com.example.xaosmusicplayer.ui.components.BottomBar
import com.example.xaosmusicplayer.ui.components.MiniPlayer
import com.example.xaosmusicplayer.ui.components.Section
import com.example.xaosmusicplayer.ui.screens.AddToPlaylistSheet
import com.example.xaosmusicplayer.data.Album
import com.example.xaosmusicplayer.ui.screens.AlbumOptionsSheet
import com.example.xaosmusicplayer.ui.screens.EditAlbumScreen
import com.example.xaosmusicplayer.ui.screens.EditSongScreen
import com.example.xaosmusicplayer.ui.screens.EqualizerScreen
import com.example.xaosmusicplayer.ui.screens.HomeScreen
import com.example.xaosmusicplayer.ui.screens.LibraryScreen
import com.example.xaosmusicplayer.ui.screens.NowPlayingScreen
import com.example.xaosmusicplayer.ui.screens.PlaylistsScreen
import com.example.xaosmusicplayer.ui.screens.CollectionHero
import com.example.xaosmusicplayer.ui.screens.DeletePlaylistDialog
import com.example.xaosmusicplayer.ui.screens.PlaylistInfoDialog
import com.example.xaosmusicplayer.ui.screens.coverUri
import com.example.xaosmusicplayer.ui.screens.SearchScreen
import com.example.xaosmusicplayer.ui.screens.SleepTimerSheet
import com.example.xaosmusicplayer.ui.screens.SongListScreen
import com.example.xaosmusicplayer.ui.screens.SongOptionsSheet
import com.example.xaosmusicplayer.ui.components.screenBackground
import com.example.xaosmusicplayer.ui.theme.DarkPalette
import com.example.xaosmusicplayer.ui.theme.LightPalette
import com.example.xaosmusicplayer.ui.theme.ThemeStore
import com.example.xaosmusicplayer.ui.theme.CustomTheme
import com.example.xaosmusicplayer.ui.theme.customized
import com.example.xaosmusicplayer.ui.screens.ThemeScreen
import com.example.xaosmusicplayer.ui.screens.SettingsScreen
import com.example.xaosmusicplayer.ui.theme.XaosMusicPlayerTheme
import android.graphics.drawable.ColorDrawable
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {

    private val themeStore by lazy { ThemeStore.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(themeStore.isDark.value, themeStore.custom.value)
        setContent {
            val dark by themeStore.isDark.collectAsStateWithLifecycle()
            val custom by themeStore.custom.collectAsStateWithLifecycle()
            LaunchedEffect(dark, custom) { applySystemBars(dark, custom) }
            XaosMusicPlayerTheme(darkTheme = dark, custom = custom) {
                XaosApp()
            }
        }
    }

    /**
     * Icone di stato scure sul tema chiaro e chiare sullo scuro, e il fondo della
     * finestra dello stesso colore dell'app: è quello che si vede per un istante
     * all'avvio e durante le transizioni di sistema.
     */
    private fun applySystemBars(dark: Boolean, custom: CustomTheme) {
        val transparent = android.graphics.Color.TRANSPARENT
        // Con il tema personalizzato conta lo sfondo scelto, non l'interruttore.
        val palette = (if (dark) DarkPalette else LightPalette).customized(custom)
        val style = if (palette.isDark) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
    }
}

/**
 * Le schermate di dettaglio, impilate sopra la sezione corrente.
 *
 * Non includono Home, Libreria e Cerca: quelle sono sezioni permanenti,
 * raggiungibili dalla barra in basso, e non fanno parte della pila.
 */
private sealed interface Destination {
    data class Album(val albumId: Long, val title: String) : Destination
    data class Artist(val name: String) : Destination
    data class PlaylistDetail(val id: String, val name: String) : Destination
    data class AutoPlaylist(val kind: AutoPlaylistKind) : Destination
    data object Favorites : Destination
    data object Playlists : Destination
    data object Equalizer : Destination
    data object Theme : Destination
    data object Settings : Destination
    data object Queue : Destination
    data class EditSong(val songId: Long) : Destination
    data class EditAlbum(val albumId: Long, val title: String) : Destination
}

/** Le due playlist che la Home costruisce dagli ascolti. */
private enum class AutoPlaylistKind(val title: String) {
    MOST_PLAYED("I più ascoltati"),
    LEAST_PLAYED("Da riscoprire"),
}

@Composable
private fun XaosApp(
    viewModel: PlayerViewModel = viewModel(),
) {
    val context = LocalContext.current

    var section by remember { mutableStateOf(Section.HOME) }
    val backStack = remember { mutableListOf<Destination>().toMutableStateList() }
    var nowPlayingOpen by remember { mutableStateOf(false) }

    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val allSongs by viewModel.songs.collectAsStateWithLifecycle()
    val visibleSongs by viewModel.visibleSongs.collectAsStateWithLifecycle()
    val searchArtists by viewModel.searchArtists.collectAsStateWithLifecycle()
    val searchAlbums by viewModel.searchAlbums.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val levels by viewModel.audioLevels.collectAsStateWithLifecycle()
    val glowMode by viewModel.glowMode.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val sleepRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val eqEnabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
    val eqLevels by viewModel.eqLevels.collectAsStateWithLifecycle()
    val eqPreset by viewModel.eqPreset.collectAsStateWithLifecycle()
    val bassBoost by viewModel.bassBoost.collectAsStateWithLifecycle()
    val virtualizer by viewModel.virtualizer.collectAsStateWithLifecycle()
    val preampDb by viewModel.preampDb.collectAsStateWithLifecycle()
    val onlineSearch by viewModel.onlineSearch.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val recentAlbums by viewModel.recentAlbums.collectAsStateWithLifecycle()
    val recap by viewModel.monthlyRecap.collectAsStateWithLifecycle()
    val yearRecap by viewModel.yearlyRecap.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val leastPlayed by viewModel.leastPlayed.collectAsStateWithLifecycle()

    val knownArtists by viewModel.knownArtists.collectAsStateWithLifecycle()
    val knownAlbums by viewModel.knownAlbums.collectAsStateWithLifecycle()
    val knownGenres by viewModel.knownGenres.collectAsStateWithLifecycle()
    val songOverrides by viewModel.songOverrides.collectAsStateWithLifecycle()

    var songMenuTarget by remember { mutableStateOf<Song?>(null) }
    var albumMenuTarget by remember { mutableStateOf<Album?>(null) }
    var addToPlaylistTarget by remember { mutableStateOf<Song?>(null) }
    var sleepSheetOpen by remember { mutableStateOf(false) }
    // L'ingranaggio di ogni sezione: le impostazioni una volta sola in cima alla pila.
    val openSettings = { if (backStack.lastOrNull() != Destination.Settings) backStack += Destination.Settings }

    // ---------- Eliminazione ----------

    // Il brano da cancellare resta in sospeso finché il sistema non risponde:
    // solo allora si sa se il file è davvero sparito.
    var pendingDeletion by remember { mutableStateOf<Song?>(null) }

    val deleteConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val song = pendingDeletion
        pendingDeletion = null
        if (result.resultCode == android.app.Activity.RESULT_OK && song != null) {
            viewModel.clearSongEdit(song.id)
            viewModel.refresh()
        }
    }

    fun deleteSong(song: Song) {
        when (val outcome = MediaDeleter(context).delete(song)) {
            is DeleteOutcome.NeedsConsent -> {
                pendingDeletion = song
                deleteConsent.launch(
                    androidx.activity.result.IntentSenderRequest
                        .Builder(outcome.intentSender)
                        .build()
                )
            }

            DeleteOutcome.Deleted -> {
                viewModel.clearSongEdit(song.id)
                viewModel.refresh()
            }

            is DeleteOutcome.Failed -> android.widget.Toast
                .makeText(context, outcome.message, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    // ---------- Permessi ----------

    val libraryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[libraryPermission] == true) viewModel.onPermissionGranted()
        else viewModel.onPermissionDenied()
    }

    fun requestLibraryPermissions() {
        val toRequest = mutableListOf(libraryPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            toRequest += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(toRequest.toTypedArray())
    }

    // Il microfono serve solo al Visualizer: lo chiediamo alla prima apertura
    // della schermata di riproduzione, non all'avvio dell'app.
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* concesso o no, l'effetto ha comunque un fallback */ }

    var micAsked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.connect()
        val granted = ContextCompat.checkSelfPermission(context, libraryPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onPermissionGranted() else requestLibraryPermissions()
    }

    LaunchedEffect(nowPlayingOpen, glowMode) {
        if (!nowPlayingOpen || glowMode != com.example.xaosmusicplayer.data.GlowMode.ANIMATED) {
            return@LaunchedEffect
        }
        if (micAsked) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            micAsked = true
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // L'esito dell'aggiornamento del motore è un fatto momentaneo, non uno
    // stato della schermata: un toast basta e non ruba spazio alla UI.
    val engineMessage by viewModel.engineMessage.collectAsStateWithLifecycle()
    LaunchedEffect(engineMessage) {
        val message = engineMessage ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        viewModel.clearEngineMessage()
    }

    // Riscansione al rientro: raccoglie i brani comparsi nel frattempo.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---------- Navigazione all'indietro ----------

    BackHandler(enabled = nowPlayingOpen || backStack.isNotEmpty() || section != Section.HOME) {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            backStack.isNotEmpty() -> backStack.removeLastOrNull()
            // Dall'ultima sezione si torna a Home invece di uscire dall'app.
            else -> section = Section.HOME
        }
    }

    /** Cambiare sezione svuota la pila: ogni sezione riparte dalla sua radice. */
    fun selectSection(target: Section) {
        if (target == section) {
            backStack.clear()
        } else {
            section = target
            backStack.clear()
        }
    }

    // ---------- Contenuto ----------

    Box(modifier = Modifier.fillMaxSize().screenBackground()) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            Box(modifier = Modifier.weight(1f)) {
                when (section) {
                    Section.HOME -> HomeScreen(
                        recentAlbums = recentAlbums,
                        recap = recap,
                        yearRecap = yearRecap,
                        mostPlayed = mostPlayed,
                        leastPlayed = leastPlayed,
                        onOpenAlbum = { id, title -> backStack += Destination.Album(id, title) },
                        onOpenArtist = { backStack += Destination.Artist(it) },
                        onPlaySong = { viewModel.play(listOf(it), 0) },
                        onPlayMostPlayed = { viewModel.play(mostPlayed, 0) },
                        onPlayLeastPlayed = { viewModel.play(leastPlayed, 0) },
                        onOpenMostPlayed = {
                            backStack += Destination.AutoPlaylist(AutoPlaylistKind.MOST_PLAYED)
                        },
                        onOpenLeastPlayed = {
                            backStack += Destination.AutoPlaylist(AutoPlaylistKind.LEAST_PLAYED)
                        },
                        onExportRecap = viewModel::exportYearlyRecap,
                        onOpenSettings = openSettings,
                    )

                    Section.LIBRARY -> LibraryScreen(
                        state = libraryState,
                        tab = tab,
                        songs = allSongs,
                        albums = albums,
                        artists = artists,
                        currentSongId = currentSong?.id,
                        onTabChange = viewModel::setTab,
                        onSongClick = { index -> viewModel.play(allSongs, index) },
                        onSongMenu = { songMenuTarget = it },
                        onAlbumClick = { backStack += Destination.Album(it.id, it.title) },
                        onAlbumLongClick = { albumMenuTarget = it },
                        onArtistClick = { backStack += Destination.Artist(it.name) },
                        onOpenSettings = openSettings,
                        onRequestPermission = { requestLibraryPermissions() },
                    )

                    Section.PLAYLISTS -> PlaylistsScreen(
                        playlists = playlists,
                        favoritesCount = favoriteSongs.size,
                        artworkOf = { pl -> viewModel.songsOfPlaylist(pl.id).firstOrNull()?.artworkUri },
                        onOpenFavorites = { backStack += Destination.Favorites },
                        onOpen = { backStack += Destination.PlaylistDetail(it.id, it.name) },
                        onCreate = { viewModel.createPlaylist(it) },
                        onDelete = { viewModel.deletePlaylist(it.id) },
                        onOpenSettings = openSettings,
                    )

                    Section.SEARCH -> SearchScreen(
                        query = query,
                        localResults = visibleSongs,
                        artistResults = searchArtists,
                        albumResults = searchAlbums,
                        currentSongId = currentSong?.id,
                        onlineState = onlineSearch,
                        downloads = downloads,
                        onQueryChange = {
                            viewModel.setQuery(it)
                            viewModel.clearOnlineSearch()
                        },
                        onPlayLocal = { index -> viewModel.play(visibleSongs, index) },
                        onSongMenu = { songMenuTarget = it },
                        onArtistClick = { backStack += Destination.Artist(it.name) },
                        onAlbumClick = { backStack += Destination.Album(it.id, it.title) },
                        onSearchOnline = { viewModel.searchOnline(query) },
                        onDownload = viewModel::downloadTrack,
                        onCancelDownload = viewModel::cancelDownload,
                        onOpenSettings = openSettings,
                    )
                }

                // Le schermate di dettaglio coprono la sezione ma non la barra
                // in basso né il mini-player, che stanno fuori da quest'area.
                backStack.lastOrNull()?.let { destination ->
                    Box(modifier = Modifier.fillMaxSize().screenBackground()) {
                        DestinationContent(
                            destination = destination,
                            viewModel = viewModel,
                            backStack = backStack,
                            currentSongId = currentSong?.id,
                            playlists = playlists,
                            favoriteSongs = favoriteSongs,
                            queue = queue,
                            mostPlayed = mostPlayed,
                            leastPlayed = leastPlayed,
                            eqEnabled = eqEnabled,
                            eqLevels = eqLevels,
                            eqPreset = eqPreset,
                            bassBoost = bassBoost,
                            virtualizer = virtualizer,
                            preampDb = preampDb,
                            knownArtists = knownArtists,
                            knownAlbums = knownAlbums,
                            knownGenres = knownGenres,
                            songOverrides = songOverrides,
                            onSongMenu = { songMenuTarget = it },
                            sleepRemainingMs = sleepRemaining,
                            onOpenSleepTimer = { sleepSheetOpen = true },
                            onOpenSettings = openSettings,
                        )
                    }
                }
            }

            MiniPlayer(
                song = currentSong,
                isPlaying = isPlaying,
                progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onClick = { nowPlayingOpen = true },
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
            )

            BottomBar(selected = section, onSelect = ::selectSection)
        }

        AnimatedVisibility(
            visible = nowPlayingOpen && currentSong != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            currentSong?.let { song ->
                NowPlayingScreen(
                    song = song,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    levels = levels,
                    glowMode = glowMode,
                    isFavorite = song.id in favorites,
                    shuffleEnabled = shuffle,
                    repeatMode = repeatMode,
                    sleepTimerRemainingMs = sleepRemaining,
                    lyricsState = lyrics,
                    onCollapse = { nowPlayingOpen = false },
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeek = viewModel::seekTo,
                    onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onSetGlowMode = viewModel::setGlowMode,
                    onOpenEqualizer = {
                        nowPlayingOpen = false
                        backStack += Destination.Equalizer
                    },
                    onOpenSleepTimer = { sleepSheetOpen = true },
                    onOpenQueue = {
                        nowPlayingOpen = false
                        backStack += Destination.Queue
                    },
                    onAddToPlaylist = { addToPlaylistTarget = song },
                    onGoToAlbum = {
                        nowPlayingOpen = false
                        backStack += Destination.Album(song.albumId, song.album)
                    },
                    // Lo stesso raggruppamento della libreria: per artista
                    // principale, non per il credito con i featuring.
                    onGoToArtist = {
                        nowPlayingOpen = false
                        backStack += Destination.Artist(song.albumArtist)
                    },
                )
            }
        }
    }

    // ---------- Fogli modali ----------

    songMenuTarget?.let { song ->
        SongOptionsSheet(
            song = song,
            isFavorite = song.id in favorites,
            onDismiss = { songMenuTarget = null },
            onPlayNext = { viewModel.playNext(song) },
            onAddToQueue = { viewModel.addToQueue(song) },
            onToggleFavorite = { viewModel.toggleFavorite(song.id) },
            onAddToPlaylist = { addToPlaylistTarget = song },
            onEdit = { backStack += Destination.EditSong(song.id) },
            onDelete = { deleteSong(song) },
        )
    }

    albumMenuTarget?.let { album ->
        val albumSongs = viewModel.songsOfAlbum(album.id)
        AlbumOptionsSheet(
            album = album,
            onDismiss = { albumMenuTarget = null },
            onPlay = { viewModel.play(albumSongs, 0) },
            onShuffle = { viewModel.play(albumSongs.shuffled(), 0) },
            onAddToQueue = { viewModel.addToQueue(albumSongs) },
            onEdit = { backStack += Destination.EditAlbum(album.id, album.title) },
        )
    }

    addToPlaylistTarget?.let { song ->
        AddToPlaylistSheet(
            playlists = playlists,
            onDismiss = { addToPlaylistTarget = null },
            onSelect = { viewModel.addToPlaylist(it.id, listOf(song.id)) },
            onCreateAndAdd = { name -> viewModel.createPlaylist(name, listOf(song.id)) },
        )
    }

    if (sleepSheetOpen) {
        SleepTimerSheet(
            remainingMs = sleepRemaining,
            onDismiss = { sleepSheetOpen = false },
            onStart = viewModel::startSleepTimer,
            onCancel = viewModel::cancelSleepTimer,
        )
    }
}

@Composable
private fun DestinationContent(
    destination: Destination,
    viewModel: PlayerViewModel,
    backStack: SnapshotStateList<Destination>,
    currentSongId: Long?,
    playlists: List<Playlist>,
    favoriteSongs: List<Song>,
    queue: List<Song>,
    mostPlayed: List<Song>,
    leastPlayed: List<Song>,
    eqEnabled: Boolean,
    eqLevels: List<Int>,
    eqPreset: Int,
    bassBoost: Int,
    virtualizer: Int,
    preampDb: Int,
    knownArtists: List<String>,
    knownAlbums: List<String>,
    knownGenres: List<String>,
    songOverrides: Map<Long, SongOverride>,
    onSongMenu: (Song) -> Unit,
    sleepRemainingMs: Long?,
    onOpenSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pop = { backStack.removeLastOrNull(); Unit }

    /** Tutte le liste di brani si comportano allo stesso modo. */
    @Composable
    fun songList(
        title: String,
        songs: List<Song>,
        emptyMessage: String = "Nessun brano",
        hero: CollectionHero? = null,
    ) {
        SongListScreen(
            title = title,
            songs = songs,
            currentSongId = currentSongId,
            onBack = pop,
            onPlay = { viewModel.play(songs, it) },
            onShuffleAll = { viewModel.play(songs.shuffled(), 0) },
            onSongMenu = onSongMenu,
            emptyMessage = emptyMessage,
            hero = hero,
        )
    }

    when (destination) {
        is Destination.Album -> {
            val songs = viewModel.songsOfAlbum(destination.albumId)
            val artist = songs.map { it.albumArtist }.distinct().singleOrNull() ?: "Artisti vari"
            val year = songs.maxOfOrNull { it.year }?.takeIf { it > 0 }
            songList(
                destination.title,
                songs,
                hero = CollectionHero(
                    artwork = songs.firstOrNull()?.artworkUri,
                    kind = listOfNotNull("ALBUM", year?.toString()).joinToString(" · "),
                    subtitle = artist,
                    actions = {
                        CircleIconButton(
                            icon = XaosIcons.Edit,
                            contentDescription = "Modifica album",
                            onClick = { backStack += Destination.EditAlbum(destination.albumId, destination.title) },
                        )
                    },
                ),
            )
        }

        is Destination.Artist ->
            songList(destination.name, viewModel.songsOfArtist(destination.name))

        is Destination.PlaylistDetail -> {
            val playlist = playlists.firstOrNull { it.id == destination.id }
            val songs = viewModel.songsOfPlaylist(destination.id)
            var editing by remember { mutableStateOf(false) }
            var deleting by remember { mutableStateOf(false) }
            // La copertina si sceglie dal selettore di foto di sistema.
            val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) viewModel.setPlaylistCover(destination.id, uri)
            }
            fun launchPicker() = pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            songList(
                playlist?.name ?: destination.name,
                songs,
                "Playlist vuota",
                hero = playlist?.let { pl ->
                    CollectionHero(
                        artwork = pl.coverUri(songs.firstOrNull()?.artworkUri),
                        kind = "PLAYLIST",
                        description = pl.description,
                        onArtworkClick = ::launchPicker,
                        actions = {
                            CircleIconButton(icon = XaosIcons.Edit, contentDescription = "Modifica playlist", onClick = { editing = true })
                            Spacer(Modifier.width(10.dp))
                            CircleIconButton(icon = XaosIcons.Delete, contentDescription = "Elimina playlist", onClick = { deleting = true })
                        },
                    )
                },
            )
            if (editing && playlist != null) {
                PlaylistInfoDialog(
                    playlist = playlist,
                    onDismiss = { editing = false },
                    onConfirm = { name, description -> editing = false; viewModel.updatePlaylistInfo(playlist.id, name, description) },
                    onPickCover = { editing = false; launchPicker() },
                    onRemoveCover = { editing = false; viewModel.setPlaylistCover(playlist.id, null) },
                )
            }
            if (deleting && playlist != null) {
                DeletePlaylistDialog(
                    playlist,
                    onDismiss = { deleting = false },
                    onConfirm = { deleting = false; viewModel.deletePlaylist(playlist.id); pop() },
                )
            }
        }

        is Destination.AutoPlaylist -> when (destination.kind) {
            AutoPlaylistKind.MOST_PLAYED ->
                songList(destination.kind.title, mostPlayed, "Nessun ascolto registrato")

            AutoPlaylistKind.LEAST_PLAYED ->
                songList(destination.kind.title, leastPlayed, "Libreria vuota")
        }

        Destination.Favorites -> songList(
            "Preferiti",
            favoriteSongs,
            "Nessun preferito",
            hero = CollectionHero(artwork = null, kind = "PLAYLIST", description = "I brani col cuore, qui e sul PC."),
        )

        Destination.Queue -> songList("In coda", queue, "Coda vuota")

        Destination.Playlists -> PlaylistsScreen(
            playlists = playlists,
            favoritesCount = favoriteSongs.size,
            artworkOf = { pl -> viewModel.songsOfPlaylist(pl.id).firstOrNull()?.artworkUri },
            onOpenFavorites = { backStack += Destination.Favorites },
            onOpen = { backStack += Destination.PlaylistDetail(it.id, it.name) },
            onCreate = { viewModel.createPlaylist(it) },
            onDelete = { viewModel.deletePlaylist(it.id) },
            onOpenSettings = onOpenSettings,
        )

        is Destination.EditSong -> {
            val song = viewModel.songById(destination.songId)
            val rawSong = viewModel.rawSongById(destination.songId)
            if (song == null || rawSong == null) {
                // Il brano può essere sparito mentre la schermata era aperta.
                pop()
            } else {
                EditSongScreen(
                    song = song,
                    rawSong = rawSong,
                    knownArtists = knownArtists,
                    knownAlbums = knownAlbums,
                    knownGenres = knownGenres,
                    hasEdits = songOverrides.containsKey(song.id),
                    onBack = pop,
                    onSave = { override ->
                        viewModel.saveSongEdit(song.id, override)
                        pop()
                    },
                    onReset = {
                        viewModel.clearSongEdit(song.id)
                        pop()
                    },
                )
            }
        }

        is Destination.EditAlbum -> {
            val albumSongs = viewModel.songsOfAlbum(destination.albumId)
            if (albumSongs.isEmpty()) {
                pop()
            } else {
                EditAlbumScreen(
                    albumTitle = destination.title,
                    songs = albumSongs,
                    knownArtists = knownArtists,
                    knownGenres = knownGenres,
                    hasEdits = albumSongs.any { songOverrides.containsKey(it.id) },
                    onBack = pop,
                    onReset = {
                        viewModel.clearAlbumEdits(albumSongs)
                        pop()
                    },
                    onSave = { edit ->
                        viewModel.saveAlbumEdit(
                            songsInOrder = edit.songsInOrder,
                            title = edit.title,
                            artist = edit.artist,
                            genre = edit.genre,
                            artworkPath = edit.artworkPath,
                        )
                        pop()
                    },
                )
            }
        }

        Destination.Equalizer -> EqualizerScreen(
            controller = viewModel.equalizer,
            enabled = eqEnabled,
            levels = eqLevels,
            selectedPreset = eqPreset,
            bassBoost = bassBoost,
            virtualizer = virtualizer,
            preampDb = preampDb,
            onBack = pop,
            onEnabledChange = viewModel::setEqualizerEnabled,
            onBandChange = viewModel::setBandLevel,
            onPresetSelected = viewModel::applyPreset,
            onBassBoostChange = viewModel::setBassBoost,
            onVirtualizerChange = viewModel::setVirtualizer,
            onPreampChange = viewModel::setPreampDb,
        )

        Destination.Theme -> ThemeScreen(onBack = pop)

        Destination.Settings -> SettingsScreen(
            sleepRemainingMs = sleepRemainingMs,
            onBack = pop,
            onOpenTheme = { backStack += Destination.Theme },
            onOpenEqualizer = { backStack += Destination.Equalizer },
            onOpenSleepTimer = onOpenSleepTimer,
            onRescan = viewModel::refresh,
            onUpdateEngine = viewModel::updateEngine,
        )
    }
}
