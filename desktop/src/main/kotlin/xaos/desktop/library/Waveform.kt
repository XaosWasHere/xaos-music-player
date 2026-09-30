package xaos.desktop.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xaos.desktop.Settings
import xaos.desktop.player.Player
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Le onde di un brano, alla SoundCloud: [BINS] valori fra 0 e 1, l'intensità
 * di ogni tratto della canzone dall'inizio alla fine.
 *
 * Si calcolano una volta sola per file — VLC lo converte in un WAV mono a
 * [SAMPLE_RATE] Hz, in meno di un secondo — e restano in cache in
 * `~/.xaos/waveforms`, un file da [BINS] byte per brano.
 */
object Waveforms {

    const val BINS = 320
    private const val SAMPLE_RATE = 2000

    private val dir = File(Settings.appDir, "waveforms")
    private val memory = object : LinkedHashMap<String, FloatArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 64
    }
    private val mutex = Mutex()

    suspend fun load(track: Track, player: Player): FloatArray? = withContext(Dispatchers.IO) {
        val key = keyOf(track)
        synchronized(memory) { memory[key] }?.let { return@withContext it }
        mutex.withLock {
            val cached = File(dir, "$key.bin")
            val bins = if (cached.length() == BINS.toLong()) {
                cached.readBytes().map { (it.toInt() and 0xFF) / 255f }.toFloatArray()
            } else {
                compute(track, player)?.also { values ->
                    dir.mkdirs()
                    cached.writeBytes(ByteArray(BINS) { (values[it] * 255).toInt().coerceIn(0, 255).toByte() })
                }
            } ?: return@withContext null
            synchronized(memory) { memory[key] = bins }
            bins
        }
    }

    private fun compute(track: Track, player: Player): FloatArray? {
        val wav = File.createTempFile("xaos-wave-", ".wav")
        try {
            if (!player.decodeForWaveform(File(track.path), wav, SAMPLE_RATE)) return null
            val samples = readPcm(wav) ?: return null
            if (samples.isEmpty()) return null
            // RMS di ogni tratto, poi una radice che alza i passaggi piani: senza,
            // un brano ben masterizzato sembrerebbe un blocco uniforme.
            val per = (samples.size / BINS).coerceAtLeast(1)
            val rms = FloatArray(BINS) { b ->
                val from = b * per
                val to = minOf(from + per, samples.size)
                if (from >= to) 0f else {
                    var sum = 0.0
                    for (i in from until to) sum += samples[i].toDouble() * samples[i]
                    sqrt(sum / (to - from)).toFloat()
                }
            }
            // Si stende la gamma fra i tratti più piani e il più forte: la musica
            // compressa sta quasi tutta vicino al massimo, e senza questo sembrerebbe
            // un blocco piatto. Il silenzio resta a zero.
            val sorted = rms.sorted()
            val hi = sorted.last().coerceAtLeast(1f)
            val lo = sorted[(BINS * 0.05).toInt()] * 0.6f
            return FloatArray(BINS) { i ->
                val v = ((rms[i] - lo) / (hi - lo).coerceAtLeast(1f)).coerceIn(0f, 1f)
                Math.pow(v.toDouble(), 1.35).toFloat()
            }
        } finally {
            wav.delete()
        }
    }

    /** I campioni a 16 bit del blocco "data" di un WAV. */
    private fun readPcm(file: File): ShortArray? {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            val start = pos + 8
            if (id == "data") {
                val end = if (size <= 0 || start + size > bytes.size) bytes.size else start + size
                val count = (end - start) / 2
                return ShortArray(count) { buf.getShort(start + it * 2) }
            }
            pos = start + size + (size and 1)
        }
        return null
    }

    private fun keyOf(track: Track): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("v2|${track.path}|${track.size}|${track.modified}".toByteArray())
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }
}
