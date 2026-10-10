package com.example.xaosmusicplayer.data

import android.content.ContentUris
import android.net.Uri

/**
 * Un brano: della memoria del dispositivo, oppure della libreria del PC
 * ascoltata con Xaos Beam. Questi ultimi hanno l'id negativo, per non
 * scontrarsi mai con quelli di MediaStore, e un indirizzo da cui suonarli.
 */
data class Song(
    val id: Long,
    val title: String,
    /** Il credito completo come appare sulla traccia, feat. inclusi. */
    val artist: String,
    /**
     * Chi "possiede" la traccia, cioè l'artista sotto cui va raggruppata.
     *
     * Serve a evitare che "Tizio" e "Tizio feat. Caio" diventino due artisti
     * distinti e spacchino l'album in due: il credito resta quello completo
     * sulla riga del brano, il raggruppamento usa questo.
     */
    val albumArtist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAdded: Long,
    val relativePath: String,
    /** Disponibile solo da Android 11, e solo se il file lo dichiara nei tag. */
    val genre: String? = null,
    /** Copertina scelta dall'utente, che ha la precedenza su quella dell'album. */
    val artworkPath: String? = null,
    /** Per i brani del PC: l'indirizzo da cui il player lo scarica mentre suona. */
    val remoteUrl: String? = null,
    /** Per i brani del PC: la copertina, servita dal PC. */
    val remoteArtworkUrl: String? = null,
    /**
     * L'album com'è in MediaStore, da cui viene la copertina. Resta quello
     * anche quando il brano finisce sotto un altro id d'album (album uniti,
     * album rinominati, album completati dai brani del PC): MediaStore la
     * copertina la conosce solo col suo id.
     */
    val artworkAlbumId: Long = albumId,
) {
    /** Un brano della libreria del PC, ascoltato in streaming con Xaos Beam. */
    val isRemote: Boolean get() = remoteUrl != null

    val uri: Uri
        get() = remoteUrl?.let(Uri::parse) ?: ContentUris.withAppendedId(AUDIO_COLLECTION, id)

    /** Copertina dell'album servita da MediaStore (o dal PC). Può non esistere: Coil gestisce il fallback. */
    val artworkUri: Uri
        get() = artworkPath?.let { Uri.fromFile(java.io.File(it)) }
            ?: remoteArtworkUrl?.let(Uri::parse)
            ?: ContentUris.withAppendedId(ALBUM_ART_COLLECTION, artworkAlbumId)

    companion object {
        val AUDIO_COLLECTION: Uri =
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val ALBUM_ART_COLLECTION: Uri =
            Uri.parse("content://media/external/audio/albumart")
    }
}

/** Un album, ricavato raggruppando i brani. */
data class Album(
    val id: Long,
    val title: String,
    /** L'artista proprietario, o "Artisti vari" se le tracce non concordano. */
    val artist: String,
    val songCount: Int,
    val artworkUri: Uri,
    /** Quanti brani dell'album stanno solo sul PC, con Xaos Beam. */
    val pcOnlyCount: Int = 0,
)

/** Un artista, ricavato raggruppando i brani. */
data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

/** Una playlist creata dall'utente. Contiene gli id dei brani, non i brani stessi. */
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    /** Due righe scritte dall'utente, mostrate sotto la copertina. */
    val description: String = "",
    /** La copertina scelta dall'utente, copiata nello spazio dell'app ([PlaylistCovers]). */
    val coverPath: String? = null,
)
