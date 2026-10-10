package xaos.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import xaos.desktop.beam.BeamCommand
import xaos.desktop.beam.BeamServer
import xaos.desktop.beam.ConnectedPhone
import xaos.desktop.library.Track
import xaos.desktop.player.Player

/** Xaos Beam, per le schermate che devono sapere chi sta suonando. */
val LocalBeam = compositionLocalOf<BeamServer?> { null }

/** Il telefono che sta suonando al posto del PC (Connect), se c'è. */
val LocalActivePhone = compositionLocalOf<ConnectedPhone?> { null }

/**
 * Quello che l'interfaccia mostra come "in riproduzione": il player del PC,
 * oppure il telefono quando è lui il dispositivo attivo, come su Spotify. I
 * comandi vanno a chi sta suonando.
 */
class ShownPlayback(
    /** Il telefono che suona; null se suona il PC. */
    val phone: ConnectedPhone?,
    /** Il brano, come brano della libreria del PC (null se il PC non ce l'ha). */
    val track: Track?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    private val player: Player,
    private val beam: BeamServer?,
) {
    val remote: Boolean get() = phone != null

    private fun send(action: String, ms: Long? = null) {
        val p = phone ?: return
        beam?.connect?.sendToPhone(p.name, BeamCommand(action, ms = ms))
    }

    fun togglePlayPause() = if (remote) send("toggle") else player.togglePlayPause()
    fun next() = if (remote) send("next") else player.next()
    fun previous() = if (remote) send("previous") else player.previous()
    fun toggleShuffle() = if (remote) send("shuffle") else player.toggleShuffle()
    fun cycleRepeat() = if (remote) send("repeat") else player.cycleRepeat()
    fun seekTo(fraction: Float) = if (remote) send("seek", (durationMs * fraction.coerceIn(0f, 1f)).toLong()) else player.seekTo(fraction)
    fun seekToMs(ms: Long) = if (remote) send("seek", ms) else player.seekToMs(ms)
}

@Composable
fun rememberShownPlayback(player: Player): ShownPlayback {
    val beam = LocalBeam.current
    val phone = LocalActivePhone.current
    val current by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    if (phone == null) return ShownPlayback(null, current, isPlaying, position, duration, player, beam)
    // La posizione del telefono arriva ogni tanto: in mezzo la si fa scorrere.
    val now by produceState(System.currentTimeMillis(), phone) {
        while (true) { value = System.currentTimeMillis(); delay(TICK_MS) }
    }
    val track = phone.now.track
    val phoneDuration = track?.durationMs ?: 0L
    return ShownPlayback(
        phone = phone,
        track = track?.pcId?.let { beam?.trackFor(it) },
        playing = phone.now.playing,
        positionMs = phone.positionNow(now).let { if (phoneDuration > 0) it.coerceAtMost(phoneDuration) else it },
        durationMs = phoneDuration,
        player = player,
        beam = beam,
    )
}

private const val TICK_MS = 200L
