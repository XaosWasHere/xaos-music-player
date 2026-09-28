package com.example.xaosmusicplayer.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.pow

/**
 * Guadagno applicato prima della catena di effetti.
 *
 * Il volume di ExoPlayer non serve allo scopo: finisce in AudioTrack, che
 * AudioFlinger applica durante il mix, quindi a valle degli effetti di
 * sessione. Attenuare lì non dà loro alcun margine. Questo processore invece
 * lavora sui campioni dentro la pipeline del player, prima che l'audio
 * raggiunga la sessione: è l'unico punto da cui si può dare headroom a
 * equalizzatore, bass boost e virtualizer.
 */
object Preamp {

    /** Fattore lineare, 1 = nessuna attenuazione. Letto dal thread audio. */
    @Volatile
    var gain: Float = 1f
        private set

    /** Imposta l'attenuazione in decibel (≤ 0). Zero disattiva il processore. */
    fun setDb(db: Int) {
        gain = if (db >= 0) 1f else 10f.pow(db / 20f).coerceIn(0f, 1f)
    }

    fun reset() {
        gain = 1f
    }
}

/**
 * Applica [Preamp.gain] ai campioni PCM a 16 bit.
 *
 * Formati diversi vengono rifiutati: ExoPlayer inserisce allora una conversione
 * a monte, oppure salta il processore. Non tentiamo di gestire il virgola
 * mobile perché con l'output float attivo il percorso cambierebbe comunque.
 */
@OptIn(UnstableApi::class)
class PreampProcessor : BaseAudioProcessor() {

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    // Volutamente niente override di isActive(): il processore resta sempre
    // nella catena. ExoPlayer la ricostruisce solo quando riconfigura il sink,
    // quindi un processore che si disattiva a guadagno pieno smetterebbe di
    // esserci, e alzare il preamp durante la riproduzione non avrebbe effetto
    // fino al brano successivo. A guadagno pieno si limita a copiare.

    override fun queueInput(inputBuffer: ByteBuffer) {
        val gain = Preamp.gain
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)
        if (gain >= 1f) {
            output.put(inputBuffer)
        } else {
            while (inputBuffer.remaining() >= 2) {
                val scaled = (inputBuffer.short * gain).toInt()
                output.putShort(
                    scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                )
            }
            // Un eventuale byte spaiato non è un campione valido: si scarta.
            inputBuffer.position(inputBuffer.limit())
        }
        output.flip()
    }
}
