package com.example.xaosmusicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.xaosmusicplayer.audio.CrossfeedProcessor
import com.example.xaosmusicplayer.audio.PreampProcessor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.xaosmusicplayer.MainActivity
import com.example.xaosmusicplayer.audio.AudioSessionHolder
import com.example.xaosmusicplayer.widget.WidgetHub

/**
 * Tiene in vita la riproduzione fuori dall'Activity e pubblica la MediaSession
 * da cui arrivano notifica, schermata di blocco, cuffie Bluetooth e Android Auto.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Il preamp deve stare dentro la pipeline del player, non nel volume
        // di AudioTrack: solo così l'attenuazione precede gli effetti di
        // sessione e può dar loro margine.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                // L'ordine conta: prima il margine, poi la spazializzazione.
                .setAudioProcessors(arrayOf(PreampProcessor(), CrossfeedProcessor()))
                // Con l'output in virgola mobile il nostro processore a 16 bit
                // verrebbe scavalcato.
                .setEnableFloatOutput(false)
                .build()
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Mette in pausa quando si staccano le cuffie, invece di sparare
            // musica dall'altoparlante.
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Equalizzatore e Visualizer hanno bisogno di questo id per agganciarsi.
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int,
            ) {
                AudioSessionHolder.update(audioSessionId)
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()

        wireSleepTimer(player)
        WidgetHub.attach(this, player)
    }

    /** Toccare la notifica riporta all'app invece di aprire una nuova istanza. */
    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun wireSleepTimer(player: Player) {
        SleepTimer.onExpired = {
            // onExpired arriva dallo scope del timer: il player va toccato solo
            // dal thread su cui è stato costruito.
            player.applicationLooper.let { looper ->
                android.os.Handler(looper).post {
                    if (SleepTimer.finishCurrentTrack.value) {
                        // Lascia finire il brano in corso e poi fermati. Senza
                        // togliere la ripetizione il brano non finirebbe mai.
                        player.repeatMode = Player.REPEAT_MODE_OFF
                        pauseAtEndOfTrack(player)
                    } else {
                        player.pause()
                    }
                }
            }
        }
    }

    /** Registra un listener usa-e-getta che mette in pausa al cambio di brano. */
    private fun pauseAtEndOfTrack(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    player.pause()
                    player.removeListener(this)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.removeListener(this)
                }
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * L'utente ha rimosso l'app dai recenti. Se non sta suonando nulla, chiudi:
     * lasciare un servizio fermo in foreground è solo una notifica fantasma.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        WidgetHub.detach()
        SleepTimer.release()
        AudioSessionHolder.clear()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
