package com.example.xaosmusicplayer.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Energia dell'audio in un istante, normalizzata su 0..1.
 *
 * Le tre bande servono all'effetto grafico: il basso muove il respiro del
 * gradiente, i medi la sua saturazione, gli alti gli scintillii.
 */
data class AudioLevels(
    val amplitude: Float = 0f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
) {
    companion object {
        val Silent = AudioLevels()
    }
}

/**
 * Legge l'output audio del player tramite [Visualizer] e ne ricava i livelli
 * che pilotano il gradiente reattivo dietro la copertina.
 *
 * Nota importante: l'API Visualizer di Android richiede il permesso RECORD_AUDIO
 * anche quando cattura solo la propria sessione. Se il permesso manca, o se il
 * device non espone l'effetto, [start] fallisce in silenzio e i livelli restano
 * a zero — la UI in quel caso ricade su un'animazione autonoma.
 */
class AudioVisualizer {

    private var visualizer: Visualizer? = null
    private var sessionId = 0

    private val _levels = MutableStateFlow(AudioLevels.Silent)
    val levels: StateFlow<AudioLevels> = _levels.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    /** Valori smussati fra un frame e l'altro, per evitare uno sfarfallio nervoso. */
    private var smoothAmplitude = 0f
    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f

    fun start(newSessionId: Int) {
        if (newSessionId == 0) {
            stop()
            return
        }
        if (newSessionId == sessionId && visualizer != null) return
        stop()
        sessionId = newSessionId

        visualizer = runCatching {
            Visualizer(newSessionId).apply {
                // La capture size più piccola disponibile basta e costa poco:
                // ci serve l'inviluppo, non uno spettrogramma preciso.
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(
                    captureListener,
                    Visualizer.getMaxCaptureRate() / 2,
                    /* waveform = */ false,
                    /* fft = */ true,
                )
                enabled = true
                Log.d(TAG, "Visualizer avviato su sessione $newSessionId, captureSize=$captureSize")
            }
        }.onFailure {
            Log.w(TAG, "Visualizer non disponibile (permesso RECORD_AUDIO o device)", it)
            _isCapturing.value = false
        }.getOrNull()

        _isCapturing.value = visualizer != null
    }

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer?,
            waveform: ByteArray?,
            samplingRate: Int,
        ) = Unit

        override fun onFftDataCapture(
            visualizer: Visualizer?,
            fft: ByteArray?,
            samplingRate: Int,
        ) {
            if (fft == null || fft.size < 4) return
            _levels.value = analyze(fft)
        }
    }

    /**
     * Converte il buffer FFT in energie di banda.
     *
     * Il buffer è una sequenza di coppie (reale, immaginario): il modulo di ogni
     * coppia è l'ampiezza del bin corrispondente, dal più grave al più acuto.
     */
    private fun analyze(fft: ByteArray): AudioLevels {
        val binCount = fft.size / 2
        val magnitudes = FloatArray(binCount)
        var total = 0f

        for (i in 1 until binCount) {
            val real = fft[i * 2].toFloat()
            val imaginary = fft[i * 2 + 1].toFloat()
            val magnitude = hypot(real, imaginary)
            magnitudes[i] = magnitude
            total += magnitude * magnitude
        }

        // Divisione grossolana dello spettro. I bassi occupano pochi bin ma
        // portano quasi tutta l'energia percepita, da cui le soglie asimmetriche.
        val bassEnd = (binCount * 0.10f).toInt().coerceAtLeast(2)
        val midEnd = (binCount * 0.45f).toInt().coerceAtLeast(bassEnd + 1)

        val bass = magnitudes.averageOver(1, bassEnd)
        val mid = magnitudes.averageOver(bassEnd, midEnd)
        val treble = magnitudes.averageOver(midEnd, binCount)
        val rms = sqrt(total / binCount)

        smoothBass = smooth(smoothBass, bass.normalize(BASS_CEILING))
        smoothMid = smooth(smoothMid, mid.normalize(MID_CEILING))
        smoothTreble = smooth(smoothTreble, treble.normalize(TREBLE_CEILING))
        smoothAmplitude = smooth(smoothAmplitude, rms.normalize(RMS_CEILING))

        return AudioLevels(
            amplitude = smoothAmplitude,
            bass = smoothBass,
            mid = smoothMid,
            treble = smoothTreble,
        )
    }

    private fun FloatArray.averageOver(from: Int, until: Int): Float {
        if (from >= until || until > size) return 0f
        var sum = 0f
        for (i in from until until) sum += this[i]
        return sum / (until - from)
    }

    /**
     * Porta l'ampiezza grezza in 0..1 con una curva di potenza: comprime i picchi
     * e apre la parte bassa, così anche i brani tranquilli fanno respirare l'effetto.
     */
    private fun Float.normalize(ceiling: Float): Float =
        (this / ceiling).coerceIn(0f, 1f).pow(RESPONSE_CURVE)

    /**
     * Interpolazione asimmetrica: l'effetto scatta in fretta sull'attacco e
     * rientra piano, come il decay di un VU meter analogico.
     */
    private fun smooth(current: Float, target: Float): Float {
        val factor = if (target > current) ATTACK else RELEASE
        return current + (target - current) * factor
    }

    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        sessionId = 0
        _isCapturing.value = false
        _levels.value = AudioLevels.Silent
        smoothAmplitude = 0f
        smoothBass = 0f
        smoothMid = 0f
        smoothTreble = 0f
    }

    private companion object {
        const val TAG = "AudioVisualizer"

        // Soglie misurate sull'FFT reale del dispositivo, non stimate: durante
        // un brano rock i moduli medi stanno intorno a 37-95 sui bassi, 8-21 sui
        // medi, 1-3.6 sugli alti, con un RMS di 13-30. Il tetto va poco sopra il
        // picco osservato: troppo basso e la banda resta incollata a 1, troppo
        // alto e non si muove mai dal fondo. In entrambi i casi l'effetto
        // sembra statico, che è il difetto che stiamo combattendo.
        const val BASS_CEILING = 110f
        const val MID_CEILING = 26f
        const val TREBLE_CEILING = 5f
        const val RMS_CEILING = 36f

        /**
         * Sotto 1 la dinamica bassa viene sollevata. Valori molto piccoli però
         * schiacciano tutto verso l'alto e tolgono escursione: 0.8 lascia
         * respirare i passaggi quieti senza appiattire i forti.
         */
        const val RESPONSE_CURVE = 0.80f

        const val ATTACK = 0.70f
        const val RELEASE = 0.16f
    }
}
