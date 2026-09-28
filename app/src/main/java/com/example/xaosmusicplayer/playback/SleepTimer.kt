package com.example.xaosmusicplayer.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Timer di spegnimento condiviso fra UI e servizio di riproduzione.
 *
 * È un singleton di processo: l'app gira in un unico processo, quindi il conto
 * alla rovescia sopravvive alla chiusura dell'Activity finché il servizio è
 * vivo, ed è la stessa istanza che la UI osserva quando l'utente riapre l'app.
 */
object SleepTimer {

    private val scope = CoroutineScope(SupervisorJob())
    private var countdown: Job? = null

    /** Millisecondi mancanti, oppure null se nessun timer è attivo. */
    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    /** Se true, allo scadere si attende la fine del brano invece di tagliarlo. */
    private val _finishCurrentTrack = MutableStateFlow(false)
    val finishCurrentTrack: StateFlow<Boolean> = _finishCurrentTrack.asStateFlow()

    /**
     * Invocato allo scadere. Lo imposta [PlaybackService], che è l'unico a
     * poter mettere in pausa il player.
     */
    @Volatile
    var onExpired: (() -> Unit)? = null

    fun start(durationMs: Long, finishCurrentTrack: Boolean = false) {
        countdown?.cancel()
        _finishCurrentTrack.value = finishCurrentTrack
        _remainingMs.value = durationMs

        countdown = scope.launch {
            var left = durationMs
            while (isActive && left > 0) {
                delay(TICK_MS)
                left -= TICK_MS
                _remainingMs.value = left.coerceAtLeast(0)
            }
            if (isActive) {
                onExpired?.invoke()
                _remainingMs.value = null
            }
        }
    }

    fun cancel() {
        countdown?.cancel()
        countdown = null
        _remainingMs.value = null
    }

    val isActive: Boolean get() = _remainingMs.value != null

    /** Chiamato quando il servizio muore: evita che un timer orfano resti appeso. */
    fun release() {
        cancel()
        onExpired = null
    }

    private const val TICK_MS = 1_000L

    /** Durate proposte nella UI, in minuti. */
    val PRESETS_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)
}
