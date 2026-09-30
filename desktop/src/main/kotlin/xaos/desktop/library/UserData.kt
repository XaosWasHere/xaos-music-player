package xaos.desktop.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xaos.desktop.sync.PhoneSong
import xaos.desktop.sync.SongMatcher
import java.io.File
import java.util.concurrent.Executors

/**
 * Come riconoscere un brano se il suo file cambia nome o cartella: gli stessi
 * dati che usa il confronto con il telefono.
 */
@Serializable
data class SongRef(val title: String, val artist: String, val durationMs: Long)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val paths: List<String> = emptyList(),
    /** Due righe scritte dall'utente, mostrate sotto la copertina. */
    val description: String = "",
    /** L'impronta della copertina scelta: il file è `covers/<impronta>.jpg` ([PlaylistCovers]). */
    val cover: String? = null,
)

/**
 * Le copertine delle playlist, in `~/.xaos/covers`, ognuna col nome della sua
 * impronta: la stessa immagine ha lo stesso nome qui e sul telefono, così la
 * sincronizzazione sa da sola quali file mandare e quali ricevere.
 */
object PlaylistCovers {
    val dir: File get() = File(xaos.desktop.Settings.appDir, "covers").apply { mkdirs() }

    fun fileOf(hash: String?): File? = hash?.let { File(dir, "$it.jpg") }?.takeIf { it.isFile }

    fun hashOf(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(bytes).take(10).joinToString("") { "%02x".format(it) }

    /** Copia [image] fra le copertine e ne restituisce l'impronta. */
    fun import(image: File): String? = runCatching {
        val bytes = image.readBytes()
        val hash = hashOf(bytes)
        val target = File(dir, "$hash.jpg")
        if (!target.isFile) target.writeBytes(bytes)
        hash
    }.getOrNull()
}

@Serializable
data class PlayEntry(val path: String, val at: Long)

/**
 * Preferiti, playlist e ascolti del PC.
 *
 * I brani sono indicati per percorso, come nel resto della libreria; [refs]
 * tiene titolo, artista e durata di ognuno, così un file spostato o rinominato
 * si ritrova ([relink]) invece di sparire dai preferiti.
 */
@Serializable
data class UserDataState(
    val favorites: List<String> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val plays: List<PlayEntry> = emptyList(),
    /** Gli ascolti fino a questo istante sono stati cancellati, qui o sul telefono. */
    val historyClearedAt: Long = 0,
    val refs: Map<String, SongRef> = emptyMap(),
)

class UserData(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<UserDataState> = _state.asStateFlow()

    /** Le scritture su disco in fila, fuori dal thread dell'interfaccia. */
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "xaos-userdata").apply { isDaemon = true } }

    @Synchronized
    fun update(transform: (UserDataState) -> UserDataState) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        writer.execute { write(next) }
    }

    // ------------------------------------------------------------ preferiti

    fun isFavorite(track: Track, state: UserDataState = _state.value): Boolean =
        track.allPaths.any { it in state.favorites }

    fun toggleFavorite(track: Track) = update { s ->
        if (isFavorite(track, s)) s.copy(favorites = s.favorites - track.allPaths.toSet())
        else s.copy(favorites = s.favorites + track.path).withRef(track)
    }

    // ------------------------------------------------------------ playlist

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()): String {
        val id = "pc_${System.currentTimeMillis()}"
        update { s ->
            s.copy(playlists = s.playlists + Playlist(id, name.trim().ifEmpty { "Nuova playlist" }, tracks.map { it.path }.distinct()))
                .withRefs(tracks)
        }
        return id
    }

    fun renamePlaylist(id: String, name: String) = update { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    fun setPlaylistInfo(id: String, name: String, description: String) = update { s ->
        s.copy(playlists = s.playlists.map {
            if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }, description = description.trim()) else it
        })
    }

    /** [image] null toglie la copertina. */
    fun setPlaylistCover(id: String, image: File?) {
        val hash = image?.let { PlaylistCovers.import(it) ?: return }
        update { s -> s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(cover = hash) else it }) }
    }

    fun deletePlaylist(id: String) = update { s -> s.copy(playlists = s.playlists.filterNot { it.id == id }) }

    /** Aggiunge in coda i brani che la playlist non ha già, come sul telefono. */
    fun addToPlaylist(id: String, tracks: List<Track>) = update { s ->
        s.copy(playlists = s.playlists.map { pl ->
            if (pl.id != id) pl
            else pl.copy(paths = pl.paths + tracks.filter { t -> t.allPaths.none { it in pl.paths } }.map { it.path }.distinct())
        }).withRefs(tracks)
    }

    fun removeFromPlaylist(id: String, index: Int) = update { s ->
        s.copy(playlists = s.playlists.map { pl ->
            if (pl.id != id || index !in pl.paths.indices) pl else pl.copy(paths = pl.paths.toMutableList().apply { removeAt(index) })
        })
    }

    fun movePlaylistItem(id: String, from: Int, to: Int) = update { s ->
        s.copy(playlists = s.playlists.map { pl ->
            if (pl.id != id || from !in pl.paths.indices || to !in pl.paths.indices) pl
            else pl.copy(paths = pl.paths.toMutableList().apply { add(to, removeAt(from)) })
        })
    }

    // ------------------------------------------------------------ ascolti

    fun recordPlay(track: Track, at: Long) = update { s ->
        s.copy(plays = (s.plays + PlayEntry(track.path, at)).takeLast(MAX_PLAYS)).withRef(track)
    }

    fun clearHistory() = update { it.copy(plays = emptyList(), historyClearedAt = System.currentTimeMillis()) }

    // ------------------------------------------------------------ manutenzione

    /**
     * Ricollega ai brani della libreria i percorsi che non esistono più (un file
     * spostato, rinominato, o un doppione eliminato), usando titolo, artista e
     * durata salvati. Quelli che non si ritrovano restano: potrebbero tornare.
     */
    fun relink(snapshot: LibrarySnapshot) {
        if (snapshot.tracks.isEmpty()) return
        val s = _state.value
        val known = snapshot.byPath
        val used = (s.favorites + s.playlists.flatMap { it.paths } + s.plays.map { it.path }).toSet()
        val lost = used.filter { it !in known }
        if (lost.isEmpty()) return
        val matcher = SongMatcher(snapshot.tracks.map { PhoneSong(it.title, it.artist, it.durationMs, it.path) })
        val moved = lost.mapNotNull { old ->
            val ref = s.refs[old] ?: return@mapNotNull null
            matcher.find(ref.title, ref.artist, ref.durationMs)?.let { old to it.path }
        }.toMap()
        if (moved.isEmpty()) return
        fun fix(p: String) = moved[p] ?: p
        update { st ->
            st.copy(
                favorites = st.favorites.map(::fix).distinct(),
                playlists = st.playlists.map { pl -> pl.copy(paths = pl.paths.map(::fix).distinct()) },
                plays = st.plays.map { it.copy(path = fix(it.path)) },
                refs = st.refs + moved.mapNotNull { (old, new) -> st.refs[old]?.let { new to it } },
            )
        }
    }

    private fun UserDataState.withRef(track: Track) = withRefs(listOf(track))

    private fun UserDataState.withRefs(tracks: List<Track>): UserDataState {
        val missing = tracks.filter { it.path !in refs }
        if (missing.isEmpty()) return this
        return copy(refs = refs + missing.associate { it.path to SongRef(it.title, it.artist, it.durationMs) })
    }

    private fun read(): UserDataState = runCatching {
        if (!file.isFile) UserDataState() else json.decodeFromString<UserDataState>(file.readText())
    }.getOrDefault(UserDataState())

    private fun write(state: UserDataState) {
        runCatching {
            // Si tengono solo i riferimenti ancora usati: il file non cresce all'infinito.
            val used = (state.favorites + state.playlists.flatMap { it.paths } + state.plays.map { it.path }).toSet()
            val compact = state.copy(refs = state.refs.filterKeys { it in used })
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(compact))
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }

    companion object {
        /** Come sul telefono: abbastanza per un anno di ascolti assidui. */
        const val MAX_PLAYS = 8000
    }
}
