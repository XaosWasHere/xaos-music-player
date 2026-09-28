package com.example.xaosmusicplayer.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Intensità della spazializzazione, condivisa con il processore audio.
 *
 * Sostituisce il Virtualizer di Android: su questo chipset quell'effetto passa
 * per un percorso in tunnel hardware che introduce crepitii, e nessuna
 * impostazione lato app lo evita. Facendolo noi il DSP del produttore esce
 * completamente dalla catena.
 */
object Crossfeed {

    /** 0 = nessun effetto, 1 = massimo. Letto dal thread audio. */
    @Volatile
    var strength: Float = 0f
        private set

    /** Accetta la stessa scala 0..1000 usata dagli AudioEffect di Android. */
    fun setStrength(perMille: Int) {
        strength = perMille.coerceIn(0, 1000) / 1000f
    }
}

/**
 * Crossfeed: simula l'ascolto in ambiente facendo arrivare a ciascun orecchio
 * anche il canale opposto, ma più tardi e più scuro.
 *
 * È ciò che rende meno faticoso l'ascolto in cuffia, dove i due canali sono
 * altrimenti separati in modo innaturale. Il ritardo corrisponde al tempo che
 * il suono impiega ad aggirare la testa, e il filtro passa-basso al fatto che
 * la testa attenua soprattutto le alte frequenze.
 */
@OptIn(UnstableApi::class)
class CrossfeedProcessor : BaseAudioProcessor() {

    private var channelCount = 2

    /** Linee di ritardo circolari, una per canale. */
    private var delayLeft = ShortArray(0)
    private var delayRight = ShortArray(0)
    private var delayIndex = 0

    /** Stato dei due filtri passa-basso a un polo. */
    private var lowpassLeft = 0f
    private var lowpassRight = 0f
    private var lowpassCoeff = 0f

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount

        val delaySamples = max(1, (inputAudioFormat.sampleRate * DELAY_SECONDS).roundToInt())
        delayLeft = ShortArray(delaySamples)
        delayRight = ShortArray(delaySamples)
        delayIndex = 0

        // Passa-basso a un polo: y += a·(x − y), con a ricavato dalla frequenza
        // di taglio e dalla frequenza di campionamento.
        lowpassCoeff = (1.0 - exp(-2.0 * PI * CUTOFF_HZ / inputAudioFormat.sampleRate)).toFloat()
        lowpassLeft = 0f
        lowpassRight = 0f

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val strength = Crossfeed.strength
        val output = replaceOutputBuffer(remaining)

        // Su materiale mono non c'è nulla da incrociare.
        if (channelCount != 2 || strength <= 0f) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val feed = strength * MAX_FEED
        // Compensa il guadagno introdotto dalla somma: senza, alzare l'effetto
        // alzerebbe anche il volume e porterebbe a troncare i picchi.
        val normalize = 1f / (1f + feed)

        while (inputBuffer.remaining() >= BYTES_PER_FRAME) {
            val left = inputBuffer.short
            val right = inputBuffer.short

            // Si legge il campione più vecchio prima di sovrascriverlo.
            val delayedLeft = delayLeft[delayIndex].toFloat()
            val delayedRight = delayRight[delayIndex].toFloat()
            lowpassLeft += lowpassCoeff * (delayedLeft - lowpassLeft)
            lowpassRight += lowpassCoeff * (delayedRight - lowpassRight)

            delayLeft[delayIndex] = left
            delayRight[delayIndex] = right
            delayIndex = (delayIndex + 1) % delayLeft.size

            val outLeft = (left + feed * lowpassRight) * normalize
            val outRight = (right + feed * lowpassLeft) * normalize
            output.putShort(outLeft.toShortSample())
            output.putShort(outRight.toShortSample())
        }

        // Un frame incompleto non è utilizzabile.
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        delayLeft.fill(0)
        delayRight.fill(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    private fun Float.toShortSample(): Short =
        toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private companion object {
        /** Tempo che il suono impiega ad aggirare la testa. */
        const val DELAY_SECONDS = 0.0003

        /** Sopra questa soglia la testa attenua fortemente: il crossfeed è scuro. */
        const val CUTOFF_HZ = 700.0

        /** Quota massima del canale opposto, a intensità piena (circa −6 dB). */
        const val MAX_FEED = 0.5f

        /** Due canali da 16 bit. */
        const val BYTES_PER_FRAME = 4
    }
}
