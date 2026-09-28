package com.example.xaosmusicplayer.online

/** Un risultato di ricerca online, prima che diventi un file locale. */
data class OnlineTrack(
    val id: String,
    val title: String,
    val uploader: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
) {
    /** URL canonico da passare a yt-dlp per il download. */
    val pageUrl: String
        get() = "https://www.youtube.com/watch?v=$id"
}

/** Avanzamento del download di un singolo brano. */
sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(val progress: Float, val etaSeconds: Long) : DownloadState
    data object Completed : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Stato della ricerca online, che a differenza di quella locale può fallire. */
sealed interface OnlineSearchState {
    data object Idle : OnlineSearchState
    data object Preparing : OnlineSearchState
    data object Searching : OnlineSearchState
    data class Results(val tracks: List<OnlineTrack>) : OnlineSearchState
    data class Failed(val message: String) : OnlineSearchState
}
