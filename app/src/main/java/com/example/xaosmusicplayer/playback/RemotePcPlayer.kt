package com.example.xaosmusicplayer.playback

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.example.xaosmusicplayer.beam.Beam
import com.example.xaosmusicplayer.beam.BeamConnect
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Il PC visto come un player: per Android è un player qualunque, quindi la
 * notifica, la schermata di blocco e i comandi delle cuffie lo comandano come
 * se suonasse qui, ma ogni comando va al PC con Xaos Beam Connect.
 *
 * La coda mostrata è di tre brani al più (precedente, in corso, successivo):
 * basta perché "avanti" e "indietro" abbiano senso, senza mandare al sistema
 * code da mille brani.
 */
@OptIn(UnstableApi::class)
class RemotePcPlayer(looper: Looper) : SimpleBasePlayer(looper) {

    /** Lo stato del PC da mostrare; si aggiorna con [update]. */
    private var pc: BeamConnect.PcNow? = null

    fun update(now: BeamConnect.PcNow?) {
        pc = now
        invalidateState()
    }

    override fun getState(): State {
        val now = pc
        val builder = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_METADATA,
                    )
                    .build(),
            )
        if (now == null || !now.hasTrack) {
            return builder.setPlaybackState(Player.STATE_IDLE).build()
        }
        // Precedente, in corso e successivo, quando ci sono.
        val window = listOfNotNull(
            now.queue.getOrNull(now.index - 1)?.let { it to false },
            (now.pcId ?: now.queue.getOrNull(now.index))?.let { it to true },
            now.queue.getOrNull(now.index + 1)?.let { it to false },
        )
        val items = window.mapIndexed { i, (id, current) ->
            MediaItemData.Builder("pc-$i-$id")
                .setMediaItem(mediaItemFor(id, if (current) now else null))
                .setDurationUs(if (current && now.durationMs > 0) now.durationMs * 1000 else C.TIME_UNSET)
                .build()
        }
        val currentIndex = window.indexOfFirst { it.second }.coerceAtLeast(0)
        return builder
            .setPlaylist(items)
            .setCurrentMediaItemIndex(currentIndex)
            .setContentPositionMs { pc?.positionNow() ?: 0L }
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(now.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    /** Il brano del catalogo del PC come MediaItem, con titolo e copertina. */
    private fun mediaItemFor(pcId: Long, now: BeamConnect.PcNow?): MediaItem {
        val song = Beam.songs.value.firstOrNull { it.id == -pcId }
        val metadata = MediaMetadata.Builder()
            .setTitle(now?.title ?: song?.title)
            .setArtist(now?.artist ?: song?.artist)
            .setAlbumTitle(now?.album ?: song?.album)
            .setArtworkUri(BeamConnect.artworkUrl(pcId)?.let(Uri::parse))
            .build()
        return MediaItem.Builder().setMediaId("pc:$pcId").setMediaMetadata(metadata).build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        BeamConnect.control(if (playWhenReady) "play" else "pause")
        pc = pc?.copy(playing = playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val current = currentMediaItemIndex
        when {
            mediaItemIndex > current -> BeamConnect.control("next")
            mediaItemIndex < current && mediaItemIndex != C.INDEX_UNSET -> BeamConnect.control("previous")
            else -> BeamConnect.control("seek", ms = positionMs.coerceAtLeast(0))
        }
        return Futures.immediateVoidFuture()
    }
}
