package com.example.xaosmusicplayer.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log

/**
 * Equalizzatore e bass boost agganciati alla sessione audio del player.
 *
 * La spazializzazione non passa più di qui: il Virtualizer di Android usa il
 * DSP del produttore, che su questo chipset gracchia. Ora è [CrossfeedProcessor],
 * dentro la pipeline del player.
 *
 * Gli AudioEffect sono una delle parti più fragili di Android: su alcuni device
 * mancano del tutto, su altri lanciano eccezioni all'istanziazione. Ogni
 * operazione è quindi difensiva e un fallimento degrada silenziosamente a
 * "effetto non disponibile" invece di far crashare la riproduzione.
 */
class EqualizerController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private var sessionId: Int = 0

    /** Stato desiderato degli effetti, da cui si ricava quali inserire davvero. */
    private var effectsEnabled = false
    private var bassBoostStrength = 0

    /** Frequenze centrali delle bande, in Hz. Vuoto se l'equalizzatore manca. */
    var bandFrequencies: List<Int> = emptyList()
        private set

    /** Estremi di guadagno supportati dal device, in millibel. */
    var minLevel: Short = -1500
        private set
    var maxLevel: Short = 1500
        private set

    /** Nomi dei preset di fabbrica, nell'ordine in cui il device li espone. */
    var presetNames: List<String> = emptyList()
        private set

    val isAvailable: Boolean get() = equalizer != null
    val bandCount: Int get() = bandFrequencies.size

    val bassBoostSupported: Boolean get() = bassBoost?.strengthSupported == true

    /**
     * Aggancia gli effetti a una nuova sessione audio. Sicuro da richiamare:
     * se la sessione è la stessa non fa nulla, altrimenti rilascia la precedente.
     */
    fun bind(newSessionId: Int) {
        if (newSessionId == 0) {
            release()
            return
        }
        if (newSessionId == sessionId && equalizer != null) return

        release()
        sessionId = newSessionId

        equalizer = runCatching {
            Equalizer(EFFECT_PRIORITY, newSessionId).apply {
                val range = bandLevelRange
                minLevel = range[0]
                maxLevel = range[1]
                bandFrequencies = (0 until numberOfBands)
                    .map { getCenterFreq(it.toShort()) / 1000 } // µHz -> Hz
                presetNames = (0 until numberOfPresets)
                    .map { getPresetName(it.toShort()) }
            }
        }.onFailure { Log.w(TAG, "Equalizzatore non disponibile", it) }.getOrNull()

        bassBoost = runCatching { BassBoost(EFFECT_PRIORITY, newSessionId) }
            .onFailure { Log.w(TAG, "BassBoost non disponibile", it) }.getOrNull()

    }

    /**
     * Accende o spegne la catena di effetti.
     *
     * Il bass boost resta spento finché la sua intensità è zero:
     * su molti dispositivi l'effetto continua a elaborare il segnale anche a
     * intensità nulla, introducendo distorsione. Con tutto a zero l'audio deve
     * essere indistinguibile da quello non processato, e l'unico modo per
     * garantirlo è non inserire affatto l'effetto nella catena.
     */
    fun setEnabled(enabled: Boolean) {
        effectsEnabled = enabled
        runCatching { equalizer?.enabled = enabled }
        syncBassBoost()
    }

    /** Livelli correnti delle bande in millibel, per ripopolare la UI. */
    fun currentLevels(): List<Int> =
        equalizer?.let { eq ->
            runCatching {
                (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
            }.getOrDefault(emptyList())
        } ?: emptyList()

    fun setBandLevel(band: Int, millibel: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.setBandLevel(
                band.toShort(),
                millibel.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort(),
            )
        }
    }

    fun applyLevels(levels: List<Int>) {
        levels.forEachIndexed { index, millibel ->
            if (index < bandCount) setBandLevel(index, millibel)
        }
    }

    /** Applica un preset di fabbrica e restituisce i livelli risultanti. */
    fun applyPreset(presetIndex: Int): List<Int> {
        val eq = equalizer ?: return emptyList()
        runCatching { eq.usePreset(presetIndex.toShort()) }
        return currentLevels()
    }

    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        syncBassBoost()
    }

    private fun syncBassBoost() {
        val effect = bassBoost ?: return
        runCatching {
            val active = effectsEnabled && bassBoostStrength > 0
            if (active) effect.setStrength(bassBoostStrength.toShort())
            effect.enabled = active
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        bandFrequencies = emptyList()
        presetNames = emptyList()
        sessionId = 0
        effectsEnabled = false
    }

    private companion object {
        const val TAG = "EqualizerController"

        /** Priorità sopra lo zero: se un'altra app contende l'effetto, vinciamo noi. */
        const val EFFECT_PRIORITY = 10
    }
}

/** Formatta una frequenza di banda come la mostra la UI: "60", "1.2K", "16K". */
fun formatBandFrequency(hz: Int): String = when {
    hz >= 1000 -> {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}K" else String.format("%.1fK", k)
    }
    else -> hz.toString()
}
