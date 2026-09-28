package com.example.xaosmusicplayer.data

/** Un ascolto registrato: quale brano, e quando. */
data class PlayEvent(val songId: Long, val timestampMs: Long)

/** Riepilogo degli ascolti di un mese, per la card in Home. */
data class MonthlyRecap(
    val playCount: Int,
    val minutesListened: Long,
    val distinctSongs: Int,
    val topArtist: String?,
    val topArtistPlays: Int,
) {
    val isEmpty: Boolean get() = playCount == 0

    companion object {
        val Empty = MonthlyRecap(0, 0, 0, null, 0)
    }
}

/** Voce della sezione "riprendi da qui": un album o un artista ripreso di recente. */
data class RecentEntry(
    val album: Album,
    val lastPlayedMs: Long,
)

/** Una voce di classifica: l'elemento e quante volte è stato ascoltato. */
data class RankedSong(val song: Song, val plays: Int)
data class RankedAlbum(val album: Album, val plays: Int)
data class RankedName(val name: String, val plays: Int)

/**
 * Il riepilogo dell'anno in corso, in stile "wrapped" ma sempre consultabile.
 *
 * Tutte le classifiche derivano dalla stessa cronologia degli ascolti, quindi
 * coprono solo ciò che è stato riprodotto dentro Xaos e solo finché rientra
 * nel limite di eventi conservati.
 */
data class YearlyRecap(
    val year: Int,
    val playCount: Int,
    val minutesListened: Long,
    val distinctSongs: Int,
    val topSong: RankedSong?,
    /** Minuti totali passati sull'artista più ascoltato dell'anno. */
    val topArtistMinutes: Long,
    /** Il brano più ascoltato dell'artista più ascoltato. */
    val topArtistTopSong: RankedSong?,
    val topAlbum: RankedAlbum?,
    val topSongs: List<RankedSong>,
    val topArtists: List<RankedName>,
    val topGenres: List<RankedName>,
) {
    val isEmpty: Boolean get() = playCount == 0

    companion object {
        /** Quante voci mostrare in ciascuna classifica. */
        const val TOP_SIZE = 5

        fun empty(year: Int) = YearlyRecap(
            year = year,
            playCount = 0,
            minutesListened = 0,
            distinctSongs = 0,
            topSong = null,
            topArtistMinutes = 0,
            topArtistTopSong = null,
            topAlbum = null,
            topSongs = emptyList(),
            topArtists = emptyList(),
            topGenres = emptyList(),
        )
    }
}
