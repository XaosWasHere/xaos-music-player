package com.example.xaosmusicplayer.data

/**
 * Correzioni ai metadati di un brano, applicate sopra quelli letti da MediaStore.
 *
 * I tag dei file non vengono riscritti. È una scelta: modificare il file
 * richiederebbe il permesso di scrittura su media di altre app, un consenso di
 * sistema per ogni brano e una libreria di tagging, e un riscan potrebbe
 * comunque sovrascrivere il risultato. Una sovrascrittura lato app funziona su
 * qualunque file, è reversibile e non può corrompere nulla — in cambio resta
 * visibile solo dentro Xaos.
 *
 * Un campo a null significa "lascia quello originale".
 */
data class SongOverride(
    val title: String? = null,
    val artist: String? = null,
    /** L'artista sotto cui raggruppare, se quello dedotto e' sbagliato. */
    val albumArtist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    /** Posizione nell'album. Decide l'ordine dei brani al suo interno. */
    val trackNumber: Int? = null,
    /** Percorso di una copertina scelta dall'utente, copiata nello spazio dell'app. */
    val artworkPath: String? = null,
) {
    val isEmpty: Boolean
        get() = title == null && artist == null && albumArtist == null &&
            album == null && genre == null && trackNumber == null && artworkPath == null
}
