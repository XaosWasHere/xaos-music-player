package com.example.xaosmusicplayer.playback

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.xaosmusicplayer.audio.AudioLevels
import com.example.xaosmusicplayer.audio.AudioVisualizer
import com.example.xaosmusicplayer.audio.AudioSessionHolder
import com.example.xaosmusicplayer.audio.EqualizerController
import com.example.xaosmusicplayer.audio.Crossfeed
import com.example.xaosmusicplayer.audio.Preamp
import com.example.xaosmusicplayer.data.Album
import com.example.xaosmusicplayer.data.Artist
import com.example.xaosmusicplayer.data.GlowMode
import com.example.xaosmusicplayer.data.LyricsReader
import com.example.xaosmusicplayer.data.LyricsState
import com.example.xaosmusicplayer.data.MonthlyRecap
import com.example.xaosmusicplayer.data.MusicRepository
import com.example.xaosmusicplayer.data.PlayEvent
import com.example.xaosmusicplayer.data.Playlist
import com.example.xaosmusicplayer.data.RankedAlbum
import com.example.xaosmusicplayer.data.RankedName
import com.example.xaosmusicplayer.data.RankedSong
import com.example.xaosmusicplayer.data.RecentEntry
import com.example.xaosmusicplayer.data.YearlyRecap
import com.example.xaosmusicplayer.data.Song
import com.example.xaosmusicplayer.data.SongOverride
import com.example.xaosmusicplayer.data.UserPreferences
import com.example.xaosmusicplayer.online.DownloadState
import com.example.xaosmusicplayer.online.OnlineSearchState
import com.example.xaosmusicplayer.online.OnlineTrack
import com.example.xaosmusicplayer.online.YtdlpEngine
import com.example.xaosmusicplayer.share.RecapCardRenderer
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.Calendar

/** Cosa mostra la libreria: i tab TITLE / ARTIST / ALBUM della schermata principale. */
enum class LibraryTab { TITLE, ARTIST, ALBUM }

/** Stato di caricamento della libreria, per distinguere "vuota" da "non ancora letta". */
enum class LibraryState { NEEDS_PERMISSION, LOADING, READY }

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    private val preferences = UserPreferences(app)
    private val lyricsReader = LyricsReader(app)
    private var lyricsJob: Job? = null
    private val visualizer = AudioVisualizer()
    val equalizer = EqualizerController()

    private var controller: MediaController? = null

    // ---------- Libreria ----------

    /** Quello che dice MediaStore, prima delle correzioni dell'utente. */
    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    val songOverrides: StateFlow<Map<Long, SongOverride>> = preferences.songOverrides
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Valori già presenti in libreria, per il completamento nella schermata di modifica. */
    val knownArtists: StateFlow<List<String>> = _songs
        .map { songs -> (songs.map { it.artist } + songs.map { it.albumArtist }).distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val knownAlbums: StateFlow<List<String>> = _songs
        .map { songs -> songs.map { it.album }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * I generi già usati, scomposti come nel riepilogo: se un file dichiara
     * "Rock, Blues" devono comparire come due voci selezionabili.
     */
    val knownGenres: StateFlow<List<String>> = _songs
        .map { songs ->
            songs.flatMap { it.genre.splitGenres() }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun songById(id: Long): Song? = _songs.value.firstOrNull { it.id == id }

    /**
     * Il brano come lo vede MediaStore, senza correzioni.
     *
     * Serve alla schermata di modifica: un campo va salvato come correzione solo
     * se si discosta dall'originale, non da quello che si vedeva prima. Con il
     * confronto sull'originale, riscrivere il nome di partenza cancella la
     * correzione e il brano torna esattamente dov'era.
     */
    fun rawSongById(id: Long): Song? = _rawSongs.value.firstOrNull { it.id == id }

    private val _libraryState = MutableStateFlow(LibraryState.NEEDS_PERMISSION)
    val libraryState: StateFlow<LibraryState> = _libraryState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(LibraryTab.TITLE)
    val tab: StateFlow<LibraryTab> = _tab.asStateFlow()

    /**
     * Risultati locali della ricerca. Riguardano solo la schermata Cerca: la
     * Libreria mostra sempre tutto, indipendentemente da cosa si stia cercando
     * altrove.
     */
    val visibleSongs: StateFlow<List<Song>> =
        combine(_songs, _query) { songs, q -> songs.matching(q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = _songs
        .map { repository.albumsOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<Artist>> = _songs
        .map { repository.artistsOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Artisti e album che corrispondono alla ricerca.
     *
     * Sono liste corte per costruzione, quindi nella schermata Cerca stanno
     * sopra i brani: un elenco di tracce lungo le seppellirebbe.
     */
    val searchArtists: StateFlow<List<Artist>> =
        combine(artists, _query) { all, q ->
            if (q.isBlank()) emptyList()
            else all.filter { it.name.contains(q, ignoreCase = true) }.take(SEARCH_GROUP_LIMIT)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchAlbums: StateFlow<List<Album>> =
        combine(albums, _query) { all, q ->
            if (q.isBlank()) emptyList()
            else all.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true)
            }.take(SEARCH_GROUP_LIMIT)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- Preferiti e playlist ----------

    val favorites: StateFlow<Set<Long>> = preferences.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val playlists: StateFlow<List<Playlist>> = preferences.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> =
        combine(_songs, favorites) { songs, favs -> songs.filter { it.id in favs } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- Cronologia e statistiche ----------

    private val playEvents: StateFlow<List<PlayEvent>> = preferences.playEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ultimo brano registrato, per non contare due volte lo stesso ascolto. */
    /**
     * Un ascolto si conta solo dopo che il brano è stato riprodotto davvero per
     * un po'. Prima bastava che diventasse il brano corrente, e questo faceva
     * sì che qualunque scorrimento rapido — o una cascata di errori del player,
     * che salta da una traccia all'altra una al secondo — riempisse la
     * cronologia di ascolti mai avvenuti.
     *
     * Si accumula il tempo di riproduzione effettivo, non la posizione: saltare
     * avanti nel brano non è averlo ascoltato.
     */
    private var listeningSongId: Long? = null
    private var listenedMs = 0L
    private var listenRecorded = false

    /**
     * Album ripresi di recente, dal più recente. Un album compare una volta
     * sola, con l'istante dell'ultimo brano ascoltato al suo interno.
     */
    val recentAlbums: StateFlow<List<RecentEntry>> =
        combine(_songs, playEvents) { songs, events ->
            if (songs.isEmpty() || events.isEmpty()) return@combine emptyList()
            val byId = songs.associateBy { it.id }
            val albums = repository.albumsOf(songs).associateBy { it.id }

            events.asReversed()
                .mapNotNull { event ->
                    val song = byId[event.songId] ?: return@mapNotNull null
                    val album = albums[song.albumId] ?: return@mapNotNull null
                    RecentEntry(album, event.timestampMs)
                }
                .distinctBy { it.album.id }
                .take(RECENT_LIMIT)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Riepilogo del mese solare in corso. */
    val monthlyRecap: StateFlow<MonthlyRecap> =
        combine(_songs, playEvents) { songs, events ->
            if (songs.isEmpty()) return@combine MonthlyRecap.Empty
            val byId = songs.associateBy { it.id }
            val monthStart = startOfCurrentMonth()

            val played = events
                .filter { it.timestampMs >= monthStart }
                .mapNotNull { byId[it.songId] }

            if (played.isEmpty()) return@combine MonthlyRecap.Empty

            val topArtist = played
                .filterNot { it.albumArtist == MusicRepository.UNKNOWN_ARTIST }
                .groupingBy { it.albumArtist }.eachCount()
                .maxByOrNull { it.value }

            MonthlyRecap(
                playCount = played.size,
                minutesListened = played.sumOf { it.durationMs } / 60_000,
                distinctSongs = played.map { it.id }.distinct().size,
                topArtist = topArtist?.key,
                topArtistPlays = topArtist?.value ?: 0,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlyRecap.Empty)

    /**
     * Il riepilogo dell'anno solare in corso: brano e album dell'anno, minuti
     * totali, e le classifiche di brani, artisti e generi.
     */
    val yearlyRecap: StateFlow<YearlyRecap> =
        combine(_songs, playEvents) { songs, events ->
            val year = Calendar.getInstance().get(Calendar.YEAR)
            if (songs.isEmpty()) return@combine YearlyRecap.empty(year)

            val byId = songs.associateBy { it.id }
            val played = events
                .filter { it.timestampMs >= startOfCurrentYear() }
                .mapNotNull { byId[it.songId] }

            if (played.isEmpty()) return@combine YearlyRecap.empty(year)

            val songPlays = played.groupingBy { it.id }.eachCount()
            val rankedSongs = songPlays.entries
                .mapNotNull { (id, plays) -> byId[id]?.let { RankedSong(it, plays) } }
                .sortedByDescending { it.plays }

            val albumsById = repository.albumsOf(songs).associateBy { it.id }
            val rankedAlbums = played.groupingBy { it.albumId }.eachCount().entries
                .mapNotNull { (id, plays) -> albumsById[id]?.let { RankedAlbum(it, plays) } }
                .sortedByDescending { it.plays }

            // Il segnaposto dei file senza tag non è un artista: in una
            // classifica occuperebbe un posto senza dire nulla.
            val rankedArtists = played
                .filterNot { it.albumArtist == MusicRepository.UNKNOWN_ARTIST }
                .groupingBy { it.albumArtist }.eachCount().entries
                .map { RankedName(it.key, it.value) }
                .sortedByDescending { it.plays }

            // Il genere manca su Android 10 e su tutti i file senza tag: la
            // sezione si nasconde da sola quando non c'è nulla da mostrare.
            val rankedGenres = played
                .flatMap { it.genre.splitGenres() }
                .groupingBy { it }.eachCount().entries
                .map { RankedName(it.key, it.value) }
                .sortedByDescending { it.plays }

            // Tutto ciò che riguarda l'artista di punta si calcola sulle sue
            // tracce soltanto: minuti e brano più ascoltato della card.
            val leader = rankedArtists.firstOrNull()?.name
            val leaderPlays = played.filter { it.albumArtist == leader }
            val leaderTop = leaderPlays.groupingBy { it.id }.eachCount()
                .maxByOrNull { it.value }
                ?.let { (id, plays) -> byId[id]?.let { RankedSong(it, plays) } }

            YearlyRecap(
                year = year,
                topArtistMinutes = leaderPlays.sumOf { it.durationMs } / 60_000,
                topArtistTopSong = leaderTop,
                playCount = played.size,
                minutesListened = played.sumOf { it.durationMs } / 60_000,
                distinctSongs = songPlays.size,
                topSong = rankedSongs.firstOrNull(),
                topAlbum = rankedAlbums.firstOrNull(),
                topSongs = rankedSongs.take(YearlyRecap.TOP_SIZE),
                topArtists = rankedArtists.take(YearlyRecap.TOP_SIZE),
                topGenres = rankedGenres.take(YearlyRecap.TOP_SIZE),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            YearlyRecap.empty(Calendar.getInstance().get(Calendar.YEAR)),
        )

    /** Playlist automatica: i brani con più ascolti registrati. */
    val mostPlayed: StateFlow<List<Song>> =
        combine(_songs, playEvents) { songs, events ->
            val counts = events.groupingBy { it.songId }.eachCount()
            songs.filter { counts.containsKey(it.id) }
                .sortedByDescending { counts[it.id] ?: 0 }
                .take(AUTO_PLAYLIST_SIZE)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Playlist automatica speculare: i brani meno ascoltati.
     *
     * A parità di conteggio vengono prima quelli mai riprodotti, che sono il
     * vero contenuto interessante di una sezione "da riscoprire".
     */
    val leastPlayed: StateFlow<List<Song>> =
        combine(_songs, playEvents) { songs, events ->
            // Senza cronologia questa sarebbe solo la libreria in ordine
            // alfabetico spacciata per consiglio: meglio non mostrarla affatto.
            if (songs.isEmpty() || events.isEmpty()) return@combine emptyList()
            val counts = events.groupingBy { it.songId }.eachCount()
            songs.sortedWith(
                compareBy({ counts[it.id] ?: 0 }, { it.title.lowercase() })
            ).take(AUTO_PLAYLIST_SIZE)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- Stato di riproduzione ----------

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /**
     * Il testo del brano in ascolto, null finché non si sa (o se non c'è).
     *
     * Si ricarica a ogni cambio di brano e non si tiene in cache: leggere un tag
     * costa qualche decina di millisecondi e una cache andrebbe invalidata a
     * ogni modifica del file, per risparmiare una lettura ogni tre minuti.
     */
    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    /** Coda corrente, per la schermata "in riproduzione". */
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    // ---------- Effetto grafico ----------

    val audioLevels: StateFlow<AudioLevels> = visualizer.levels
    val visualizerActive: StateFlow<Boolean> = visualizer.isCapturing
    val glowMode: StateFlow<GlowMode> = preferences.glowMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlowMode.ANIMATED)

    val sleepTimerRemaining: StateFlow<Long?> = SleepTimer.remainingMs

    // ---------- Ricerca e download online ----------

    private val ytdlp = YtdlpEngine(app)

    private val _onlineSearch = MutableStateFlow<OnlineSearchState>(OnlineSearchState.Idle)
    val onlineSearch: StateFlow<OnlineSearchState> = _onlineSearch.asStateFlow()

    /** Stato di download per id di brano online. */
    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()

    /**
     * Cerca online con yt-dlp. La prima invocazione è più lenta delle altre:
     * deve estrarre l'interprete Python e i binari, da cui lo stato Preparing.
     */
    fun searchOnline(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _onlineSearch.value = OnlineSearchState.Preparing
            ytdlp.ensureReady().onFailure {
                _onlineSearch.value = OnlineSearchState.Failed(
                    "Impossibile avviare yt-dlp: ${it.readableMessage()}"
                )
                return@launch
            }

            _onlineSearch.value = OnlineSearchState.Searching
            ytdlp.search(query)
                .onSuccess { tracks -> _onlineSearch.value = OnlineSearchState.Results(tracks) }
                .onFailure {
                    _onlineSearch.value = OnlineSearchState.Failed(
                        "Ricerca fallita: ${it.readableMessage()}"
                    )
                }
        }
    }

    /** Salva in galleria l'immagine del riepilogo annuale. */
    fun exportYearlyRecap() {
        viewModelScope.launch {
            _engineMessage.value = "Creazione immagine..."
            RecapCardRenderer(getApplication())
                .render(yearlyRecap.value)
                .onSuccess { _engineMessage.value = "Salvata in Immagini/Xaos" }
                .onFailure {
                    _engineMessage.value = "Immagine non salvata: ${it.readableMessage()}"
                }
        }
    }

    fun clearOnlineSearch() {
        _onlineSearch.value = OnlineSearchState.Idle
    }

    /** Messaggio una tantum sull'esito di un'operazione sul motore. */
    private val _engineMessage = MutableStateFlow<String?>(null)
    val engineMessage: StateFlow<String?> = _engineMessage.asStateFlow()

    fun updateEngine() {
        viewModelScope.launch {
            _engineMessage.value = "Aggiornamento del motore in corso..."
            ytdlp.updateYtdlp()
                .onSuccess { _engineMessage.value = it }
                .onFailure { _engineMessage.value = "Aggiornamento fallito: ${it.readableMessage()}" }
        }
    }

    fun clearEngineMessage() {
        _engineMessage.value = null
    }

    fun downloadTrack(track: OnlineTrack) {
        if (downloadJobs[track.id]?.isActive == true) return

        downloadJobs[track.id] = viewModelScope.launch {
            setDownloadState(track.id, DownloadState.Queued)
            ytdlp.download(track) { progress, eta ->
                setDownloadState(track.id, DownloadState.Running(progress, eta))
            }
                .onSuccess {
                    setDownloadState(track.id, DownloadState.Completed)
                    // Il brano è ora in MediaStore: rileggendo la libreria
                    // compare fra i risultati locali.
                    refresh()
                }
                .onFailure {
                    setDownloadState(track.id, DownloadState.Failed(it.readableMessage()))
                }
            downloadJobs.remove(track.id)
        }
    }

    fun cancelDownload(trackId: String) {
        ytdlp.cancel(trackId)
        downloadJobs.remove(trackId)?.cancel()
        _downloads.value = _downloads.value - trackId
    }

    private fun setDownloadState(trackId: String, state: DownloadState) {
        _downloads.value = _downloads.value + (trackId to state)
    }

    /** I messaggi di yt-dlp sono lunghi e tecnici: in UI ne basta l'ultima riga. */
    private fun Throwable.readableMessage(): String =
        message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.trim()
            ?: this::class.simpleName
            ?: "errore sconosciuto"

    // ---------- Equalizzatore ----------

    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _eqLevels = MutableStateFlow<List<Int>>(emptyList())
    val eqLevels: StateFlow<List<Int>> = _eqLevels.asStateFlow()

    val eqPreset: StateFlow<Int> = preferences.equalizerPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences.PRESET_CUSTOM)

    val bassBoost: StateFlow<Int> = preferences.bassBoost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val virtualizer: StateFlow<Int> = preferences.virtualizer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val preampDb: StateFlow<Int> = preferences.preampDb
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UserPreferences.DEFAULT_PREAMP_DB,
        )

    fun setPreampDb(db: Int) {
        viewModelScope.launch { preferences.setPreampDb(db) }
    }


    init {
        observeAudioSession()
        observePreamp()
        observeOverrides()
        restorePlaybackPreferences()
        trackPosition()
    }

    /**
     * La libreria visibile è sempre MediaStore più le correzioni dell'utente:
     * ricalcolarla qui evita che ogni schermata debba ricordarsene.
     */
    private fun observeOverrides() {
        viewModelScope.launch {
            combine(_rawSongs, preferences.songOverrides) { raw, overrides ->
                mergeAlbums(applyOverrides(raw, overrides))
            }.collect {
                _songs.value = it
                syncCurrentSong()
            }
        }
    }

    /**
     * Unisce sotto un solo id gli album che MediaStore ha spezzato.
     *
     * L'id d'album di MediaStore dipende anche dall'artista della traccia, non
     * solo dal titolo: in un disco dove qualche brano ha un ospite nel credito
     * — "Bring Me The Horizon/AURORA" invece di "Bring Me The Horizon" — quelle
     * tracce finiscono in un album separato con lo stesso identico nome. Non è
     * correggibile dalla schermata di modifica, perché il titolo dell'album è
     * già giusto: non c'è niente da riscrivere.
     *
     * Qui l'album torna a essere ciò che è per chi ascolta, cioè un titolo di
     * un artista: stesso titolo e stesso proprietario, stesso album. Vince l'id
     * più basso del gruppo, così la scelta è stabile fra una scansione e
     * l'altra e i preferiti o le playlist non si accorgono di nulla.
     *
     * Gli album senza titolo restano fuori: accorpare per titolo mancante
     * ammucchierebbe brani che non hanno niente in comune.
     */
    private fun mergeAlbums(songs: List<Song>): List<Song> {
        val canonicalId = songs
            .filter { it.album != MusicRepository.UNKNOWN_ALBUM && it.album.isNotBlank() }
            .groupBy { albumKeyOf(it) }
            .mapValues { (_, tracks) -> tracks.minOf { it.albumId } }
        if (canonicalId.isEmpty()) return songs

        return songs.map { song ->
            val id = canonicalId[albumKeyOf(song)] ?: song.albumId
            if (id == song.albumId) song else song.copy(albumId = id)
        }
    }

    /**
     * Titolo e proprietario, normalizzati: due album così sono lo stesso album.
     *
     * Il separatore è un carattere che in un tag non può comparire, altrimenti
     * "A B" con "C" e "A" con "B C" darebbero la stessa chiave.
     */
    private fun albumKeyOf(song: Song): String =
        song.album.trim().lowercase() + '\u0000' + song.albumArtist.trim().lowercase()

    /**
     * Applica le correzioni, riassegnando l'album quando serve.
     *
     * Il raggruppamento degli album resta quello di MediaStore, per id: se
     * l'utente rinomina l'album di un brano con il nome di un album che ha già,
     * il brano ne eredita anche l'id — altrimenti resterebbe un album a sé con
     * lo stesso nome, che è l'opposto dell'accorpamento richiesto.
     */
    private fun applyOverrides(
        raw: List<Song>,
        overrides: Map<Long, SongOverride>,
    ): List<Song> {
        if (overrides.isEmpty()) return raw
        val albumIdByName = raw.associate { it.album.trim().lowercase() to it.albumId }

        return raw.map { song ->
            val override = overrides[song.id] ?: return@map song
            val album = override.album ?: song.album
            song.copy(
                title = override.title ?: song.title,
                artist = override.artist ?: song.artist,
                // Correggendo solo il credito, il proprietario si ricalcola da
                // quello: altrimenti resterebbe agganciato al vecchio nome.
                albumArtist = override.albumArtist
                    ?: override.artist?.let { MusicRepository.primaryArtistOf(it) }
                    ?: song.albumArtist,
                album = album,
                albumId = if (override.album != null) {
                    albumIdByName[album.trim().lowercase()] ?: song.albumId
                } else {
                    song.albumId
                },
                genre = override.genre ?: song.genre,
                trackNumber = override.trackNumber ?: song.trackNumber,
                artworkPath = override.artworkPath ?: song.artworkPath,
            )
        }
    }

    fun saveSongEdit(songId: Long, override: SongOverride) {
        viewModelScope.launch { preferences.setSongOverride(songId, override) }
    }

    /**
     * Applica una modifica d'album a tutte le sue tracce.
     *
     * La posizione viene riassegnata da 1 in avanti secondo l'ordine ricevuto:
     * è l'ordine mostrato all'utente a fare fede, non i numeri che c'erano nei
     * tag, che spesso mancano o sono incoerenti.
     */
    fun saveAlbumEdit(
        songsInOrder: List<Song>,
        title: String?,
        artist: String?,
        genre: String?,
        artworkPath: String?,
    ) {
        viewModelScope.launch {
            val existing = preferences.songOverrides.first()
            val updates = songsInOrder.mapIndexed { index, song ->
                val current = existing[song.id] ?: SongOverride()
                song.id to current.copy(
                    album = title ?: current.album,
                    albumArtist = artist ?: current.albumArtist,
                    genre = genre ?: current.genre,
                    artworkPath = artworkPath ?: current.artworkPath,
                    trackNumber = index + 1,
                )
            }.toMap()
            preferences.setSongOverrides(updates)
        }
    }

    /** Toglie ogni correzione dalle tracce di un album, ordine compreso. */
    fun clearAlbumEdits(songs: List<Song>) {
        viewModelScope.launch {
            preferences.setSongOverrides(songs.associate { it.id to SongOverride() })
        }
    }

    fun addToQueue(songs: List<Song>) {
        val player = controller ?: return
        songs.forEach { player.addMediaItem(it.toMediaItem()) }
    }

    fun clearSongEdit(songId: Long) {
        viewModelScope.launch { preferences.clearSongOverride(songId) }
    }

    /**
     * Il margine serve solo quando la catena di effetti è accesa: a effetti
     * spenti attenuare significherebbe solo suonare più piano per niente.
     */
    private fun observePreamp() {
        viewModelScope.launch {
            combine(_eqEnabled, preferences.preampDb) { on, db -> on to db }
                .collect { (on, db) -> Preamp.setDb(if (on) db else 0) }
        }
        viewModelScope.launch {
            // La spazializzazione è nostra, quindi non dipende più dagli
            // AudioEffect: basta tenere allineata l'intensità.
            combine(_eqEnabled, preferences.virtualizer) { on, v -> on to v }
                .collect { (on, v) -> Crossfeed.setStrength(if (on) v else 0) }
        }
    }


    // ---------- Connessione al servizio ----------

    /** Da chiamare quando la UI entra in primo piano. */
    fun connect() {
        if (controller != null) return
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java),
        )
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()?.also { attach(it) }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun attach(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncCurrentSong()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffle.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                }
            }
        })

        // Riallinea lo stato: il servizio può già star suonando da prima che la
        // UI esistesse, ad esempio dopo un riavvio dell'Activity.
        _isPlaying.value = controller.isPlaying
        _shuffle.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        syncCurrentSong()

        viewModelScope.launch {
            controller.shuffleModeEnabled = preferences.shuffleEnabled.first()
            controller.repeatMode = preferences.repeatMode.first()
        }
    }

    private fun syncCurrentSong() {
        val mediaId = controller?.currentMediaItem?.mediaId?.toLongOrNull()
        val previousId = _currentSong.value?.id
        _currentSong.value = _songs.value.firstOrNull { it.id == mediaId }
        if (mediaId != previousId) loadLyrics(mediaId)
        _durationMs.value = (controller?.duration ?: 0L).coerceAtLeast(0L)
        _positionMs.value = controller?.currentPosition ?: 0L
        // Il conteggio riparte solo quando cambia davvero il brano: questo
        // metodo viene richiamato anche quando la libreria si ricarica, e
        // azzerare lì butterebbe via l'ascolto in corso.
        if (mediaId != listeningSongId) {
            listeningSongId = mediaId
            listenedMs = 0L
            listenRecorded = false
        }
    }

    /**
     * Il testo si azzera subito e arriva dopo: la lettura del tag è su disco, e
     * lasciare a schermo quello del brano precedente sarebbe peggio di un vuoto.
     */
    private fun loadLyrics(songId: Long?) {
        lyricsJob?.cancel()
        if (songId == null) {
            _lyrics.value = LyricsState.Missing
            return
        }
        _lyrics.value = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            _lyrics.value = lyricsReader.load(songId)
                ?.let(LyricsState::Ready)
                ?: LyricsState.Missing
        }
    }

    /**
     * Registra l'ascolto al cambio di brano.
     *
     * Il filtro sull'ultimo id evita che un semplice riallineamento dello stato
     * — che avviene anche quando la UI si ricollega al servizio — venga contato
     * come un nuovo ascolto.
     */
    /**
     * Somma il tempo riprodotto e, alla soglia, registra l'ascolto.
     *
     * I brani più corti della soglia — gli interludi degli album — si contano
     * quando sono finiti: chiedere loro trenta secondi che non hanno vorrebbe
     * dire non contarli mai.
     */
    private fun accumulateListening(durationMs: Long) {
        val songId = listeningSongId ?: return
        if (listenRecorded) return

        listenedMs += POSITION_POLL_MS
        val needed = if (durationMs in 1 until PLAY_THRESHOLD_MS) durationMs else PLAY_THRESHOLD_MS
        if (listenedMs < needed) return

        listenRecorded = true
        viewModelScope.launch {
            preferences.recordPlay(songId, System.currentTimeMillis())
        }
    }

    /**
     * Scompone il tag del genere nei generi che contiene.
     *
     * Nei file veri il campo è spesso una lista — "Rock, Blues", "Rock; Alternative"
     * — e contarla come un'etichetta unica farebbe comparire lo stesso genere
     * più volte sotto nomi diversi. Il confronto è a minuscole perché la UI
     * rimette comunque tutto in maiuscolo.
     */
    private fun String?.splitGenres(): List<String> =
        this.orEmpty()
            .split(',', ';', '/', '|')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun startOfCurrentYear(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfCurrentMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ---------- Caricamento libreria ----------

    fun onPermissionGranted() {
        viewModelScope.launch {
            _libraryState.value = LibraryState.LOADING
            _rawSongs.value = repository.loadSongs()
            _libraryState.value = LibraryState.READY
        }
    }

    fun onPermissionDenied() {
        _libraryState.value = LibraryState.NEEDS_PERMISSION
    }

    /**
     * Riscansione silenziosa, usata al rientro nell'app per raccogliere i brani
     * scaricati nel frattempo. Non passa da LOADING: la libreria è già a schermo
     * e farla sparire dietro uno spinner a ogni resume sarebbe uno sfarfallio.
     */
    fun refresh() {
        if (_libraryState.value == LibraryState.NEEDS_PERMISSION) return
        viewModelScope.launch { _rawSongs.value = repository.loadSongs() }
    }

    // ---------- Comandi di riproduzione ----------

    /** Fa partire [songs] a partire da [startIndex], sostituendo la coda. */
    fun play(songs: List<Song>, startIndex: Int) {
        val player = controller ?: return
        if (songs.isEmpty() || startIndex !in songs.indices) return

        _queue.value = songs
        player.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        player.prepare()
        player.play()
        syncCurrentSong()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val player = controller ?: return
        // Come sui lettori fisici: entro i primi secondi torni indietro, dopo
        // il tasto riavvia il brano corrente.
        if (player.currentPosition > RESTART_THRESHOLD_MS) player.seekTo(0)
        else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun toggleShuffle() {
        val player = controller ?: return
        val enabled = !player.shuffleModeEnabled
        player.shuffleModeEnabled = enabled
        viewModelScope.launch { preferences.setShuffle(enabled) }
    }

    /** Cicla off -> ripeti tutto -> ripeti brano. */
    fun cycleRepeatMode() {
        val player = controller ?: return
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = next
        viewModelScope.launch { preferences.setRepeatMode(next) }
    }

    fun playNext(song: Song) {
        val player = controller ?: return
        val insertAt = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertAt, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        controller?.addMediaItem(song.toMediaItem())
    }

    // ---------- Ricerca e navigazione libreria ----------

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setTab(value: LibraryTab) {
        _tab.value = value
    }

    fun songsOfAlbum(albumId: Long): List<Song> =
        _songs.value.filter { it.albumId == albumId }
            .sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))

    fun songsOfArtist(name: String): List<Song> =
        _songs.value.filter { it.albumArtist == name }
            .sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))

    fun songsOfPlaylist(playlistId: String): List<Song> {
        val ids = playlists.value.firstOrNull { it.id == playlistId }?.songIds ?: return emptyList()
        val byId = _songs.value.associateBy { it.id }
        // Mantiene l'ordine della playlist e scarta gli id di file rimossi dal device.
        return ids.mapNotNull { byId[it] }
    }

    // ---------- Preferiti e playlist ----------

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { preferences.toggleFavorite(songId) }
    }

    fun createPlaylist(name: String, initialSongs: List<Long> = emptyList()) {
        viewModelScope.launch {
            val id = preferences.createPlaylist(name)
            if (initialSongs.isNotEmpty()) preferences.addToPlaylist(id, initialSongs)
        }
    }

    fun addToPlaylist(playlistId: String, songIds: List<Long>) {
        viewModelScope.launch { preferences.addToPlaylist(playlistId, songIds) }
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch { preferences.removeFromPlaylist(playlistId, songId) }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch { preferences.renamePlaylist(playlistId, name) }
    }

    fun updatePlaylistInfo(playlistId: String, name: String, description: String) {
        viewModelScope.launch { preferences.updatePlaylistInfo(playlistId, name, description) }
    }

    /** La copertina scelta dal selettore: la si copia nell'app, poi la si assegna. */
    fun setPlaylistCover(playlistId: String, uri: android.net.Uri?) {
        viewModelScope.launch {
            val path = uri?.let { com.example.xaosmusicplayer.data.PlaylistCovers.save(getApplication<android.app.Application>(), it) }
            if (uri != null && path == null) return@launch
            preferences.setPlaylistCover(playlistId, path)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { preferences.deletePlaylist(playlistId) }
    }

    // ---------- Sleep timer ----------

    fun startSleepTimer(minutes: Int, finishCurrentTrack: Boolean = false) {
        SleepTimer.start(minutes * 60_000L, finishCurrentTrack)
    }

    fun cancelSleepTimer() = SleepTimer.cancel()

    // ---------- Equalizzatore ----------

    fun setEqualizerEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        equalizer.setEnabled(enabled)
        viewModelScope.launch { preferences.setEqualizerEnabled(enabled) }
    }

    fun setBandLevel(band: Int, millibel: Int) {
        equalizer.setBandLevel(band, millibel)
        val updated = _eqLevels.value.toMutableList().also {
            if (band in it.indices) it[band] = millibel
        }
        _eqLevels.value = updated
        viewModelScope.launch { preferences.setEqualizerBands(updated) }
    }

    fun applyPreset(presetIndex: Int) {
        val levels = equalizer.applyPreset(presetIndex)
        _eqLevels.value = levels
        viewModelScope.launch { preferences.setEqualizerPreset(presetIndex, levels) }
    }

    fun setBassBoost(strength: Int) {
        equalizer.setBassBoost(strength)
        viewModelScope.launch { preferences.setBassBoost(strength) }
    }

    fun setVirtualizer(strength: Int) {
        viewModelScope.launch { preferences.setVirtualizer(strength) }
    }

    /** Sfondo della schermata di riproduzione, scelto dal menu del player. */
    fun setGlowMode(mode: GlowMode) {
        viewModelScope.launch { preferences.setGlowMode(mode) }
    }

    // ---------- Wiring interno ----------

    /**
     * Ogni volta che il player apre una nuova sessione audio, riaggancia
     * equalizzatore e visualizer e ripristina le impostazioni salvate.
     */
    private fun observeAudioSession() {
        viewModelScope.launch {
            combine(
                AudioSessionHolder.sessionId,
                preferences.glowMode,
            ) { id, mode -> id to mode }.collect { (id, mode) ->
                equalizer.bind(id)
                restoreEqualizerSettings()
                // Solo il gradiente animato consuma i livelli audio: negli altri
                // due stati tenere acceso il Visualizer sarebbe batteria buttata.
                if (mode == GlowMode.ANIMATED) visualizer.start(id) else visualizer.stop()
            }
        }
    }

    private suspend fun restoreEqualizerSettings() {
        if (!equalizer.isAvailable) return
        val saved = preferences.equalizerBands.first()
        if (saved.isNotEmpty()) equalizer.applyLevels(saved)
        equalizer.setBassBoost(preferences.bassBoost.first())
        val enabled = preferences.equalizerEnabled.first()
        equalizer.setEnabled(enabled)
        _eqEnabled.value = enabled
        _eqLevels.value = equalizer.currentLevels()
    }

    private fun restorePlaybackPreferences() {
        viewModelScope.launch {
            _shuffle.value = preferences.shuffleEnabled.first()
            _repeatMode.value = preferences.repeatMode.first()
        }
    }

    /**
     * La posizione non arriva da un callback: il player va interrogato. Si
     * aggiorna solo durante la riproduzione, per non tenere sveglia la CPU.
     */
    private fun trackPosition() {
        viewModelScope.launch {
            while (true) {
                val player = controller
                if (player != null && player.isPlaying) {
                    _positionMs.value = player.currentPosition
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                    // Solo qui, cioè solo mentre suona davvero: in pausa il
                    // tempo non passa.
                    accumulateListening(player.duration)
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    override fun onCleared() {
        visualizer.stop()
        equalizer.release()
        controller?.release()
        controller = null
        super.onCleared()
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun List<Song>.matching(query: String): List<Song> {
        if (query.isBlank()) return this
        return filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.albumArtist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }

    private companion object {
        const val POSITION_POLL_MS = 500L

        /**
         * Quanto va riprodotto un brano perché conti come ascolto.
         *
         * Trenta secondi è la convenzione dei servizi di streaming, e serve a
         * distinguere l'ascolto dallo scorrere la libreria: senza una soglia,
         * passare in rassegna venti brani ne conta venti.
         */
        const val PLAY_THRESHOLD_MS = 30_000L
        const val RESTART_THRESHOLD_MS = 3_000L

        /** Quanti album mostrare in "riprendi da qui". */
        const val RECENT_LIMIT = 12

        /** Lunghezza delle playlist automatiche della Home. */
        const val AUTO_PLAYLIST_SIZE = 30

        /** Quanti artisti e album mostrare fra i risultati di ricerca. */
        const val SEARCH_GROUP_LIMIT = 8
    }
}
