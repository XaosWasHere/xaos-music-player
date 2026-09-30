package xaos.desktop.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

/** I campi modificabili di un brano. null = lascia com'è. */
data class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    /** Un'immagine da incorporare come copertina. */
    val artwork: File? = null,
)

/**
 * Scrive i tag direttamente nei file.
 *
 * A differenza del telefono, dove le correzioni restano nell'app, qui si
 * modifica il file vero: è il PC che fa da originale. Ogni modifica va anche
 * sulla copia MP3 del brano, se c'è, perché è quella che arriva al telefono;
 * lì la dimensione cambia e la sincronizzazione successiva la rimanda.
 */
object TagEditor {

    suspend fun write(track: Track, edit: TagEdit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            filesOf(track).forEach { writeFile(it, edit) }
            ArtworkLoader.invalidate(track)
        }
    }

    /**
     * Il testo del brano: quello incorporato nel file, altrimenti quello della
     * copia MP3, altrimenti il file .lrc accanto al brano.
     */
    suspend fun readLyrics(track: Track): String? = withContext(Dispatchers.IO) {
        filesOf(track).firstNotNullOfOrNull { file ->
            runCatching { AudioFileIO.read(file).tag?.getFirst(FieldKey.LYRICS) }.getOrNull()?.takeIf { it.isNotBlank() }
        } ?: sidecarLrc(track)?.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
    }

    /**
     * Salva il testo nel tag del file e della sua copia MP3 (sul telefono si
     * legge da lì), e aggiorna il .lrc accanto al brano se esiste già.
     */
    suspend fun writeLyrics(track: Track, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = text.replace("\r\n", "\n").trim()
            filesOf(track).forEach { file ->
                val audio = AudioFileIO.read(file)
                val tag = audio.tagOrCreateAndSetDefault
                if (clean.isEmpty()) tag.deleteField(FieldKey.LYRICS) else tag.setField(FieldKey.LYRICS, clean)
                audio.commit()
            }
            sidecarLrc(track)?.takeIf { it.isFile }?.writeText(clean, Charsets.UTF_8)
            Unit
        }
    }

    /**
     * Incorpora nei file tutti i testi che oggi stanno solo nei .lrc accanto ai
     * brani. Restituisce quanti brani ha aggiornato.
     */
    suspend fun embedSidecarLyrics(tracks: List<Track>, onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        var updated = 0
        val candidates = tracks.filter { sidecarLrc(it)?.isFile == true }
        candidates.forEachIndexed { i, track ->
            onProgress(i, candidates.size)
            val embedded = filesOf(track).all { file ->
                runCatching { AudioFileIO.read(file).tag?.getFirst(FieldKey.LYRICS) }.getOrNull()?.isNotBlank() == true
            }
            if (!embedded) {
                val text = sidecarLrc(track)!!.readText(Charsets.UTF_8)
                if (writeLyrics(track, text).isSuccess) updated++
            }
        }
        onProgress(candidates.size, candidates.size)
        updated
    }

    private fun filesOf(track: Track): List<File> =
        listOfNotNull(File(track.path), track.mobilePath?.let(::File)).filter { it.isFile }

    private fun sidecarLrc(track: Track): File? {
        val f = File(track.path)
        return File(f.parentFile ?: return null, f.nameWithoutExtension + ".lrc")
    }

    private fun writeFile(file: File, edit: TagEdit) {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        fun put(key: FieldKey, value: String?) {
            value ?: return
            val v = value.trim()
            if (v.isEmpty()) runCatching { tag.deleteField(key) } else tag.setField(key, v)
        }
        put(FieldKey.TITLE, edit.title)
        put(FieldKey.ARTIST, edit.artist)
        put(FieldKey.ALBUM_ARTIST, edit.albumArtist)
        put(FieldKey.ALBUM, edit.album)
        put(FieldKey.GENRE, edit.genre)
        put(FieldKey.YEAR, edit.year)
        put(FieldKey.TRACK, edit.trackNumber)
        put(FieldKey.DISC_NO, edit.discNumber)
        edit.artwork?.let { image ->
            val art = ArtworkFactory.createArtworkFromFile(image)
            tag.deleteArtworkField()
            tag.setField(art)
        }
        audio.commit()
    }
}
