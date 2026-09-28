package com.example.xaosmusicplayer.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Espone l'audio session id di ExoPlayer al resto dell'app.
 *
 * Equalizzatore e Visualizer si agganciano a quella sessione, ma vivono fuori
 * dal servizio: questo è il punto in cui i due mondi si incontrano. Il valore è
 * 0 finché il player non ha aperto una sessione audio.
 */
object AudioSessionHolder {

    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    fun update(id: Int) {
        _sessionId.value = id
    }

    fun clear() {
        _sessionId.value = 0
    }
}
