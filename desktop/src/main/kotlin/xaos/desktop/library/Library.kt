package xaos.desktop.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import xaos.desktop.sync.SongMatcher
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger

/** Un file audio della libreria, con i tag già letti. */
@Serializable
data class Track(
    val path: String,
    val size: Long,
    val modified: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String? = null,
    val durationMs: Long,
    val hasEmbeddedArt: Boolean,
    /** Se il file ha un testo incorporato (o la sua copia MP3 ne ha uno). */
    val hasLyrics: Boolean = false,
    /**
     * La copia MP3 dello stesso brano, se la libreria ne ha una (nella
     * sottocartella `MP3` accanto ai FLAC). Sul PC si ascolta l'originale;
     * al telefono si può mandare questa, che pesa un quinto.
     */
    @Transient val mobilePath: String? = null,
    /**
     * Gli altri file che la libreria ha riconosciuto come questo stesso brano
     * (la copia MP3, un doppione in un'altra cartella). Preferiti, playlist e
     * ascolti salvati con uno di questi percorsi valgono per questo brano.
     */
    @Transient val aliases: List<String> = emptyList(),
) {
    val file: File get() = File(path)

    /** Il percorso principale e tutti quelli dei file uniti a lui. */
    val allPaths: List<String> get() = listOf(path) + aliases
}

/** Se accanto al brano c'è un file .lrc con il testo. */
fun Track.hasLyricsFile(): Boolean =
    File(file.parentFile, file.nameWithoutExtension + ".lrc").isFile

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
    val folder: File? get() = tracks.firstOrNull()?.file?.parentFile
}

data class Artist(val name: String, val albums: List<Album>) {
    val trackCount: Int get() = albums.sumOf { it.tracks.size }
}

/** Un brano presente in più file: quello che la libreria mostra e le copie che nasconde. */
data class DuplicateGroup(val kept: Track, val copies: List<Track>)

data class LibrarySnapshot(
    val roots: List<File>,
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    /** I file doppi nascosti dalla libreria, per poterli ripulire dalle impostazioni. */
    val duplicates: List<DuplicateGroup> = emptyList(),
) {
    /** Percorso (anche di una copia) → brano mostrato in libreria. */
    val byPath: Map<String, Track> by lazy {
        buildMap { tracks.forEach { t -> t.allPaths.forEach { put(it, t) } } }
    }

    companion object {
        val Empty = LibrarySnapshot(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val done: Int, val total: Int) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * La libreria del PC: tutti i file audio sotto una o più cartelle.
 *
 * Leggere i tag di migliaia di file costa qualche secondo, quindi il risultato
 * resta in un indice su disco: al riavvio si rileggono solo i file nuovi o
 * cambiati (stessa dimensione e stessa data di modifica vuol dire stesso file).
 */
class Library(private val indexFile: File) {

    private val _snapshot = MutableStateFlow(LibrarySnapshot.Empty)
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        // jaudiotagger scrive nel log ogni blocco che legge: migliaia di righe
        // inutili a ogni scansione. Il logger va tenuto in un campo: quelli di
        // java.util.logging sono riferimenti deboli, e un logger raccolto dal
        // garbage collector torna al livello predefinito.
        quietLogger.level = Level.OFF
    }

    /**
     * Legge le cartelle [roots]. Con [force] si rileggono i tag di tutti i
     * file, anche di quelli che l'indice dà per invariati: serve quando un
     * programma esterno ritocca i tag senza cambiare la data del file.
     */
    suspend fun load(roots: List<File>, force: Boolean = false) = withContext(Dispatchers.IO) {
        val cached = if (force) emptyMap() else readIndex()
        fun inRoots(path: String) = roots.any { path.startsWith(it.path + File.separator) }
        // Si mostra subito quello che c'era, poi si aggiorna.
        if (cached.isNotEmpty()) publish(roots, cached.values.filter { inRoots(it.path) })

        val files = roots.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS } }
            .distinctBy { it.path }
        _scan.value = ScanState.Scanning(0, files.size)

        val done = AtomicInteger()
        val gate = Semaphore(PARALLEL_READS)
        val tracks = coroutineScope {
            files.map { file ->
                async {
                    val hit = cached[file.path]
                    val track = if (hit != null && hit.size == file.length() && hit.modified == file.lastModified()) {
                        hit
                    } else {
                        gate.withPermit { readTrack(file) }
                    }
                    val n = done.incrementAndGet()
                    if (n % 25 == 0) _scan.value = ScanState.Scanning(n, files.size)
                    track
                }
            }.awaitAll().filterNotNull()
        }

        publish(roots, tracks)
        writeIndex(tracks)
        _scan.value = ScanState.Idle
    }

    private fun publish(roots: List<File>, all: List<Track>) {
        val duplicates = mutableListOf<DuplicateGroup>()
        val tracks = mergeDuplicates(mergeTwins(all), duplicates)
        val albums = tracks
            .groupBy { albumKey(it) }
            .map { (key, list) ->
                val sorted = list.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.title.lowercase() }))
                Album(
                    key = key,
                    title = sorted.first().album,
                    // L'artista dell'album è quello più frequente fra le tracce:
                    // le colonne sonore hanno un artista diverso per brano.
                    artist = sorted.groupingBy { it.albumArtist }.eachCount().maxBy { it.value }.key,
                    year = sorted.maxOf { it.year },
                    tracks = sorted,
                )
            }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))

        val artists = albums
            .groupBy { it.artist }
            .map { (name, list) -> Artist(name, list.sortedByDescending { it.year }) }
            .sortedBy { it.name.lowercase() }

        val ordered = albums.flatMap { it.tracks }
        _snapshot.value = LibrarySnapshot(roots, ordered, albums, artists, duplicates.sortedBy { it.kept.title.lowercase() })
    }

    /**
     * Unisce i file che sono lo stesso brano dello stesso album, ovunque
     * stiano: la versione iTunes in M4A accanto al FLAC, la copia tornata dal
     * telefono accanto all'originale. Si tiene il formato migliore; gli altri
     * restano sul disco ma non compaiono due volte, e finiscono in
     * [duplicates] perché l'utente possa decidere se eliminarli.
     */
    private fun mergeDuplicates(tracks: List<Track>, duplicates: MutableList<DuplicateGroup>): List<Track> {
        val result = ArrayList<Track>(tracks.size)
        tracks.groupBy { albumKey(it) + "|" + songKey(it) }.values.forEach { group ->
            if (group.size == 1) { result += group.first(); return@forEach }
            val clusters = mutableListOf<MutableList<Track>>()
            group.sortedWith(preference).forEach { t ->
                clusters.firstOrNull { SongMatcher.sameSong(it.first(), t) }?.add(t) ?: clusters.add(mutableListOf(t))
            }
            clusters.forEach { cluster ->
                var kept = cluster.first()
                if (cluster.size == 1) { result += kept; return@forEach }
                // Un MP3 nella sottocartella "MP3" dello stesso album è la copia
                // per il telefono anche se ha un altro nome: non è un doppione.
                val twin = cluster.drop(1).firstOrNull { c ->
                    kept.mobilePath == null && c.mobilePath == null &&
                        File(c.path).extension.equals("mp3", true) &&
                        File(c.path).parentFile?.name.equals("mp3", true) &&
                        collapsedParent(File(c.path)) == File(kept.path).parentFile
                }
                if (twin != null) kept = kept.copy(mobilePath = twin.path)
                val copies = cluster.drop(1).filter { it !== twin }
                if (copies.isNotEmpty()) duplicates += DuplicateGroup(kept, copies)
                result += kept.copy(
                    aliases = kept.aliases + cluster.drop(1).flatMap { it.allPaths },
                    hasLyrics = cluster.any { it.hasLyrics },
                )
            }
        }
        return result
    }

    /** Prima il formato migliore, poi il percorso più corto: di solito è l'originale. */
    private val preference = compareBy<Track>(
        { FORMAT_RANK[File(it.path).extension.lowercase()] ?: 50 },
        { it.path.count { c -> c == File.separatorChar } },
        { it.path },
    )

    private fun songKey(track: Track): String =
        SongMatcher.asciiKey(SongMatcher.bareTitle(track.title)).takeIf { it.length >= 4 }
            ?: SongMatcher.bareKey(track.title)

    /**
     * Unisce le due versioni dello stesso brano: `Album/brano.flac` e
     * `Album/MP3/brano.mp3` diventano un solo brano, che si ascolta in FLAC e
     * che al telefono può andare in MP3. Senza questo ogni album comparirebbe
     * due volte.
     */
    private fun mergeTwins(tracks: List<Track>): List<Track> =
        tracks.groupBy { twinKey(File(it.path)) }.values.map { group ->
            if (group.size == 1) return@map group.first()
            val primary = group.minBy { FORMAT_RANK[File(it.path).extension.lowercase()] ?: 50 }
            val mp3 = group.firstOrNull { it !== primary && File(it.path).extension.equals("mp3", true) }
            primary.copy(
                mobilePath = mp3?.path,
                hasLyrics = group.any { it.hasLyrics },
                aliases = group.filter { it !== primary }.map { it.path },
            )
        }

    /**
     * Un album è il suo nome, a prescindere da cartella e artisti: le tracce
     * dello stesso disco sparse in più cartelle (iTunes, la copia tornata dal
     * telefono) o con artisti diversi (colonne sonore, featuring) restano un
     * album solo. Maiuscole e punteggiatura non contano: "Clair Obscur:
     * Expedition 33" e "Clair Obscur_ Expedition 33" sono lo stesso disco.
     */
    private fun albumKey(track: Track): String =
        SongMatcher.compact(track.album).ifEmpty { track.album.trim().lowercase() }

    private fun readTrack(file: File): Track? = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        fun field(key: FieldKey): String? =
            runCatching { tag?.getFirst(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val artist = field(FieldKey.ARTIST) ?: UNKNOWN_ARTIST
        Track(
            path = file.path,
            size = file.length(),
            modified = file.lastModified(),
            title = field(FieldKey.TITLE) ?: file.nameWithoutExtension,
            artist = artist,
            albumArtist = field(FieldKey.ALBUM_ARTIST) ?: artist,
            album = field(FieldKey.ALBUM) ?: file.parentFile?.name ?: UNKNOWN_ALBUM,
            trackNumber = field(FieldKey.TRACK)?.substringBefore('/')?.toIntOrNull() ?: 0,
            discNumber = field(FieldKey.DISC_NO)?.substringBefore('/')?.toIntOrNull() ?: 1,
            year = field(FieldKey.YEAR)?.take(4)?.toIntOrNull() ?: 0,
            genre = field(FieldKey.GENRE),
            durationMs = (audio.audioHeader?.preciseTrackLength ?: 0.0).times(1000).toLong(),
            hasEmbeddedArt = runCatching { tag?.firstArtwork != null }.getOrDefault(false),
            hasLyrics = runCatching { tag?.getFirst(FieldKey.LYRICS)?.isNotBlank() == true }.getOrDefault(false),
        )
    }.getOrElse {
        // Un file illeggibile non deve sparire: lo si mostra col nome del file.
        Track(
            path = file.path, size = file.length(), modified = file.lastModified(),
            title = file.nameWithoutExtension, artist = UNKNOWN_ARTIST, albumArtist = UNKNOWN_ARTIST,
            album = file.parentFile?.name ?: UNKNOWN_ALBUM, trackNumber = 0, discNumber = 1, year = 0,
            durationMs = 0, hasEmbeddedArt = false,
        )
    }

    private fun readIndex(): Map<String, Track> = runCatching {
        if (!indexFile.exists()) return emptyMap()
        json.decodeFromString<List<Track>>(indexFile.readText()).associateBy { it.path }
    }.getOrDefault(emptyMap())

    private fun writeIndex(tracks: List<Track>) {
        runCatching {
            indexFile.parentFile?.mkdirs()
            val tmp = File(indexFile.path + ".tmp")
            tmp.writeText(json.encodeToString(tracks))
            tmp.copyTo(indexFile, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        private val quietLogger: Logger = Logger.getLogger("org.jaudiotagger")

        /** Quale versione tenere quando lo stesso brano c'è in più formati: prima il lossless. */
        private val FORMAT_RANK = mapOf("flac" to 0, "wav" to 1, "aiff" to 2, "m4a" to 3, "ogg" to 4, "opus" to 5, "mp3" to 6)

        /** La cartella del brano, saltando la sottocartella "MP3" delle copie compresse. */
        fun collapsedParent(file: File): File? {
            val parent = file.parentFile ?: return null
            return if (parent.name.equals("mp3", ignoreCase = true)) parent.parentFile ?: parent else parent
        }

        private fun twinKey(file: File): String =
            (collapsedParent(file)?.path ?: "") + "|" + file.nameWithoutExtension.lowercase()

        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "aiff", "wma")
        const val UNKNOWN_ARTIST = "Artista sconosciuto"
        const val UNKNOWN_ALBUM = "Album sconosciuto"
        private const val PARALLEL_READS = 6
    }
}
