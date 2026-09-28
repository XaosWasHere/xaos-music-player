package com.example.xaosmusicplayer.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Legge la libreria musicale del dispositivo tramite MediaStore.
 *
 * Non tiene stato: chi chiama decide quando ri-scansionare (all'avvio, dopo
 * aver ottenuto il permesso, o su pull-to-refresh).
 */
class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(pathColumn)
            // Il genere è esposto da MediaStore solo da Android 11: sotto
            // servirebbe una query separata su Audio.Genres per ogni brano,
            // che costa troppo per una statistica accessoria.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
            }
        }.toTypedArray()

        // Due filtri, con compiti diversi: la cartella tiene fuori l'audio che
        // non è musica anche quando il sistema lo etichetta come tale, la
        // durata scarta i frammenti. Esente da entrambi ciò che l'utente ha
        // scaricato con l'app: un brano chiesto esplicitamente non è mai un
        // suono di sistema, anche se dura pochi secondi, e vederlo sparire
        // senza spiegazione sarebbe incomprensibile.
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND (${MediaStore.Audio.Media.DURATION} >= ? OR $pathColumn LIKE ?)")
            repeat(EXCLUDED_DIRS.size) { append(" AND $pathColumn NOT LIKE ?") }
        }
        val selectionArgs = (
            listOf(MIN_DURATION_MS.toString(), pathPattern(DOWNLOAD_DIR)) +
                EXCLUDED_DIRS.map(::pathPattern)
            ).toTypedArray()
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()
        context.contentResolver.query(
            Song.AUDIO_COLLECTION, projection, selection, selectionArgs, sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathCol = cursor.getColumnIndexOrThrow(pathColumn)
            val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            } else -1
            val albumArtistCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            } else -1

            while (cursor.moveToNext()) {
                val credit = cursor.getString(artistCol)
                    ?.takeUnless { it == MEDIASTORE_UNKNOWN } ?: UNKNOWN_ARTIST
                // Il tag album_artist è la fonte migliore quando c'è; altrimenti
                // si ricava il primario dal credito togliendo i featuring.
                // Anche il tag album_artist va normalizzato: capita che elenchi
                // a sua volta più artisti, e in quel caso non è un proprietario.
                val owner = primaryArtistOf(
                    albumArtistCol.takeIf { it >= 0 }
                        ?.let { cursor.getString(it) }
                        ?.takeUnless { it.isBlank() || it == MEDIASTORE_UNKNOWN }
                        ?: credit
                )

                songs += Song(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: UNKNOWN_TITLE,
                    artist = credit,
                    albumArtist = owner,
                    album = cursor.getString(albumCol) ?: UNKNOWN_ALBUM,
                    albumId = cursor.getLong(albumIdCol),
                    durationMs = cursor.getLong(durationCol),
                    trackNumber = cursor.getInt(trackCol),
                    year = cursor.getInt(yearCol),
                    dateAdded = cursor.getLong(addedCol),
                    relativePath = cursor.getString(pathCol).orEmpty().toFolder(),
                    genre = if (genreCol >= 0) {
                        cursor.getString(genreCol)?.takeUnless { it.isBlank() }
                    } else null,
                )
            }
        }
        songs
    }

    /** Raggruppa i brani per album. Evita una seconda query su MediaStore. */
    fun albumsOf(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }
            .map { (albumId, tracks) ->
                val first = tracks.first()
                Album(
                    id = albumId,
                    title = first.album,
                    artist = tracks.map { it.albumArtist }.distinct().singleOrNull()
                        ?: VARIOUS_ARTISTS,
                    songCount = tracks.size,
                    // Dalla prima traccia, non dall'id: così una copertina
                    // scelta a mano vale anche per l'album.
                    artworkUri = first.artworkUri,
                )
            }
            .sortedBy { it.title.lowercase() }

    fun artistsOf(songs: List<Song>): List<Artist> =
        songs.groupBy { it.albumArtist }
            .map { (name, tracks) ->
                Artist(
                    name = name,
                    songCount = tracks.size,
                    albumCount = tracks.map { it.albumId }.distinct().size,
                )
            }
            .sortedBy { it.name.lowercase() }

    /** Cartelle in cui risiedono i brani, utili come vista alternativa alla libreria. */
    fun foldersOf(songs: List<Song>): List<String> =
        songs.map { it.relativePath }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    /**
     * RELATIVE_PATH esiste solo da Android 10. Sotto si usa DATA, che contiene
     * il percorso completo del file ed è disponibile ovunque.
     */
    private val pathColumn: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.DATA
        }

    /** Il pattern LIKE cambia di conseguenza: percorso relativo o assoluto. */
    private fun pathPattern(dir: String): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "$dir%" else "%/$dir%"

    /** Da DATA arriva il percorso del file: alla vista cartelle serve la sua directory. */
    private fun String.toFolder(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) this
        else substringBeforeLast('/', missingDelimiterValue = "")

    companion object {
        /**
         * Marcatori che separano il proprietario dagli ospiti. Barra e punto
         * e virgola ci sono perche' i tag li usano spesso per elencare piu'
         * artisti; virgola e "&" no, perche' fanno parte di nomi legittimi.
         */
        private val FEATURE_MARKERS = Regex(
            """\s*(?:[/;]|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b)\s*""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Ricava l'artista principale da un credito.
         *
         * Taglia solo sui marcatori espliciti di collaborazione e sulla barra:
         * virgola e "&" fanno parte di nomi legittimi — "Simon & Garfunkel" non
         * va spezzato — quindi restano fuori.
         */
        fun primaryArtistOf(credit: String): String =
            credit.split(FEATURE_MARKERS).firstOrNull()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: credit

        /**
         * Soglia sotto cui un file non viene considerato musica.
         *
         * Tenuta bassa apposta: gli interludi degli album ci passano sotto con
         * facilità — "[ost] dreamseeker" di POST HUMAN: NeX GEn dura 19,3
         * secondi, "Foreword" di Meteora 13,4 — e vedersi sparire una traccia
         * del disco è peggio che ritrovarsi in libreria un frammento di troppo.
         * A tenere fuori ciò che non è musica pensa EXCLUDED_DIRS, non questa.
         */
        const val MIN_DURATION_MS = 10_000L

        /**
         * Cartelle escluse dalla libreria, per quanto ci sia dentro.
         *
         * IS_MUSIC non basta: le note vocali di WhatsApp e le suonerie generate
         * dal telefono risultano tutte "musica" per il sistema. Sono migliaia,
         * e senza questo filtro sommergerebbero la libreria e falserebbero il
         * riepilogo dell'anno. Si escludono per posizione e non per durata
         * perché è la posizione a dire cosa sono: una nota vocale lunga cinque
         * minuti resta una nota vocale.
         */
        private val EXCLUDED_DIRS = listOf(
            // Media privati delle app: WhatsApp, Telegram e simili.
            "Android/media/",
            // Suonerie e notifiche generate da Nothing OS.
            "NTGenerateRingtone/",
            "NTGenerateNotification/",
        )

        /** Cartella in cui l'app deposita i brani scaricati. */
        const val DOWNLOAD_DIR = "Music/Xaos/"
        private const val MEDIASTORE_UNKNOWN = "<unknown>"
        const val UNKNOWN_TITLE = "Titolo sconosciuto"
        const val UNKNOWN_ARTIST = "Artista sconosciuto"
        const val UNKNOWN_ALBUM = "Album sconosciuto"
        const val VARIOUS_ARTISTS = "Artisti vari"
    }
}
