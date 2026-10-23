package com.example.xaosmusicplayer.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.xaosmusicplayer.MainActivity
import com.example.xaosmusicplayer.beam.BeamConnect
import com.example.xaosmusicplayer.i18n.I18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * "In riproduzione sul PC", come su Spotify: quando il dispositivo attivo è il
 * PC (Xaos Beam Connect), una notifica multimediale con il suo brano e i suoi
 * comandi, anche nella schermata di blocco.
 *
 * È un servizio a parte da quello della musica del telefono, così l'app
 * continua a vedere il proprio player com'è. Finché il PC suona il servizio
 * resta in primo piano, e Android non congela l'app: il collegamento col PC e
 * il widget restano vivi. Quando il dispositivo attivo torna il telefono, o il
 * PC sparisce, il servizio si chiude da solo.
 */
class RemotePlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        I18n.init(this)
        ensurePlaybackChannel(this)
        val player = RemotePcPlayer(Looper.getMainLooper())
        session = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(openAppIntent())
            .build()
        scope.launch {
            combine(BeamConnect.pc, BeamConnect.remoteActive) { pc, active -> pc to active }.collect { (pc, active) ->
                if (!active || pc == null || !pc.hasTrack) {
                    stopSelf()
                } else {
                    player.update(pc)
                }
            }
        }
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        private const val SESSION_ID = "xaos-beam-pc"

        /**
         * Accende la notifica del PC. Si chiama quando il PC diventa il
         * dispositivo attivo; se Android non permette di avviare servizi in
         * quel momento (app in background), si rinuncia senza danni.
         */
        fun start(context: Context) {
            runCatching { context.startService(Intent(context, RemotePlaybackService::class.java)) }
        }
    }
}
