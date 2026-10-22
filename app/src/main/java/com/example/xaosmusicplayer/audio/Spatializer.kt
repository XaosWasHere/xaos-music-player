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
 * Intensità della spazializzazione (il "virtualizer" dell'equalizzatore),
 * condivisa con il processore audio.
 *
 * Sostituisce il Virtualizer di Android: su questo chipset quell'effetto passa
 * per un percorso in tunnel hardware che introduce crepitii, e nessuna
 * impostazione lato app lo evita. Facendolo noi il DSP del produttore esce
 * completamente dalla catena.
 */
object Spatializer {

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
 * La spazializzazione: allarga la scena stereo e le dà un po' d'aria.
 *
 * Il segnale si divide in centro (quello che i due canali hanno in comune: la
 * voce, la cassa, il basso) e lati (la differenza fra i due: chitarre aperte,
 * riverberi, l'ambiente della registrazione). Allargare vuol dire alzare i
 * lati rispetto al centro, ma solo sopra i bassi: un basso allargato perde
 * corpo e suona vuoto, quindi sotto [SIDE_CUTOFF_HZ] i lati restano com'erano.
 * Ai lati si aggiunge poi una loro copia ritardata e un po' scurita, come una
 * prima riflessione della stanza: è quella che dà profondità, oltre che
 * larghezza. Il centro non si tocca, così la voce resta davanti e a fuoco.
 *
 * Il crossfeed di prima (il canale opposto, ritardato, in ciascun orecchio)
 * faceva il contrario: restringeva un po' la scena per riposare le orecchie
 * in cuffia, e al 50% non si sentiva quasi.
 */
@OptIn(UnstableApi::class)
class SpatializerProcessor : BaseAudioProcessor() {

    private var channelCount = 2

    /** I lati sopra i bassi, ritardati: la "prima riflessione". */
    private var echo = FloatArray(0)
    private var echoIndex = 0

    /** Passa-basso a un polo sui lati: quello che resta è la parte da allargare. */
    private var sideLow = 0f
    private var sideLowCoeff = 0f

    /** Passa-basso sull'eco: una riflessione è più scura del suono diretto. */
    private var echoTone = 0f
    private var echoToneCoeff = 0f

    /** L'intensità effettiva, che insegue quella impostata senza scatti. */
    private var current = 0f

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate
        echo = FloatArray(max(1, (rate * ECHO_SECONDS).roundToInt()))
        echoIndex = 0
        // Passa-basso a un polo: y += a·(x − y), con a ricavato dalla frequenza
        // di taglio e dalla frequenza di campionamento.
        sideLowCoeff = onePole(SIDE_CUTOFF_HZ, rate)
        echoToneCoeff = onePole(ECHO_TONE_HZ, rate)
        sideLow = 0f
        echoTone = 0f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val target = Spatializer.strength
        val output = replaceOutputBuffer(remaining)

        // Su materiale mono non c'è una scena da allargare; spento, si passa oltre.
        if (channelCount != 2 || (target <= 0f && current <= MIN_STRENGTH)) {
            current = 0f
            output.put(inputBuffer)
            output.flip()
            return
        }

        while (inputBuffer.remaining() >= BYTES_PER_FRAME) {
            // Cambiando intensità si scivola verso quella nuova: un salto
            // secco della larghezza si sentirebbe come un clic.
            current += (target - current) * SMOOTHING

            val left = inputBuffer.short.toFloat()
            val right = inputBuffer.short.toFloat()
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            sideLow += sideLowCoeff * (side - sideLow)
            val sideHigh = side - sideLow

            // L'eco dei lati, più scuro, letto prima di essere sovrascritto.
            val delayed = echo[echoIndex]
            echo[echoIndex] = sideHigh
            echoIndex = (echoIndex + 1) % echo.size
            echoTone += echoToneCoeff * (delayed - echoTone)

            val widened = sideLow + sideHigh * (1f + MAX_WIDEN * current) + echoTone * (MAX_ECHO * current)
            // I lati più forti alzano i picchi: si compensa, così il volume
            // percepito resta quello e non si tronca.
            val gain = 1f / (1f + GAIN_COMPENSATION * current)
            output.putShort(((mid + widened) * gain).toShortSample())
            output.putShort(((mid - widened) * gain).toShortSample())
        }

        // Un frame incompleto non è utilizzabile.
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        echo.fill(0f)
        echoIndex = 0
        sideLow = 0f
        echoTone = 0f
    }

    private fun onePole(cutoffHz: Double, sampleRate: Int): Float =
        (1.0 - exp(-2.0 * PI * cutoffHz / sampleRate)).toFloat()

    private fun Float.toShortSample(): Short =
        toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private companion object {
        /** Sotto questa frequenza i lati non si allargano: il basso resta pieno e al centro. */
        const val SIDE_CUTOFF_HZ = 180.0

        /** A intensità piena i lati sopra i bassi valgono 2,5 volte. */
        const val MAX_WIDEN = 1.5f

        /** La prima riflessione: abbastanza tardi da dare spazio, non da fare eco. */
        const val ECHO_SECONDS = 0.014

        /** La riflessione perde le alte: suona come una stanza, non come uno sdoppiamento. */
        const val ECHO_TONE_HZ = 5_000.0

        /** Quanto conta la riflessione a intensità piena. */
        const val MAX_ECHO = 0.45f

        /** Riduzione del volume a intensità piena, per tenere i picchi sotto controllo. */
        const val GAIN_COMPENSATION = 0.35f

        /** Quanto in fretta l'intensità effettiva insegue quella impostata, per campione. */
        const val SMOOTHING = 0.0005f

        /** Sotto questo valore l'effetto è spento davvero. */
        const val MIN_STRENGTH = 0.001f

        /** Due canali da 16 bit. */
        const val BYTES_PER_FRAME = 4
    }
}
