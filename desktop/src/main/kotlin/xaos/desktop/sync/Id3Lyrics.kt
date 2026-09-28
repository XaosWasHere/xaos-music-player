package xaos.desktop.sync

/**
 * Legge il testo (frame `USLT`) dall'intestazione ID3 di un MP3.
 *
 * È la stessa logica dell'app Android, che legge i testi proprio da lì: serve a
 * sapere cosa vedrà il telefono senza scaricare il brano intero, perché il tag
 * sta in testa al file e dichiara la propria lunghezza.
 */
object Id3Lyrics {

    const val HEADER_SIZE = 10

    /** La lunghezza totale di intestazione + tag, o null se non c'è un tag ID3. */
    fun tagLength(header: ByteArray): Int? {
        if (header.size < HEADER_SIZE) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val size = header.syncSafeInt(6)
        if (size <= 0 || size > MAX_TAG_BYTES) return null
        return HEADER_SIZE + size
    }

    /** Il testo, dai byte che contengono almeno intestazione e tag interi. */
    fun read(bytes: ByteArray): String? {
        val total = tagLength(bytes) ?: return null
        if (bytes.size < total) return null
        val major = bytes[3].toInt()
        val flags = bytes[5].toInt()
        var body = bytes.copyOfRange(HEADER_SIZE, total)
        if (flags and UNSYNCHRONISATION != 0) body = body.deunsynchronise()

        var pos = if (flags and EXTENDED_HEADER != 0 && body.size >= 4) {
            if (major >= 4) body.syncSafeInt(0) else body.beInt(0) + 4
        } else 0

        val idLength = if (major <= 2) 3 else 4
        val sizeLength = if (major <= 2) 3 else 4
        val flagsLength = if (major <= 2) 0 else 2
        val frameHeader = idLength + sizeLength + flagsLength
        val wanted = if (major <= 2) "ULT" else "USLT"

        while (pos + frameHeader <= body.size) {
            val id = String(body, pos, idLength, Charsets.ISO_8859_1)
            if (id.isBlank() || id[0] == ' ' || id[0] == '\u0000') return null
            val size = when {
                major <= 2 -> body.beInt24(pos + idLength)
                major >= 4 -> body.syncSafeInt(pos + idLength)
                else -> body.beInt(pos + idLength)
            }
            if (size <= 0 || pos + frameHeader + size > body.size) return null
            if (id == wanted) return decode(body, pos + frameHeader, size)
            pos += frameHeader + size
        }
        return null
    }

    private fun decode(body: ByteArray, start: Int, size: Int): String? {
        if (size < 5) return null
        val encoding = body[start].toInt()
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        val wide = encoding == 1 || encoding == 2
        val end = start + size
        var pos = start + 1 + LANGUAGE_BYTES
        while (pos < end) {
            if (wide) {
                if (pos + 1 < end && body[pos] == ZERO && body[pos + 1] == ZERO) { pos += 2; break }
                pos += 2
            } else {
                if (body[pos] == ZERO) { pos += 1; break }
                pos += 1
            }
        }
        if (pos >= end) return null
        return String(body, pos, end - pos, charset).trimEnd('\u0000')
    }

    private fun ByteArray.syncSafeInt(o: Int): Int =
        (this[o].toInt() and 0x7F shl 21) or (this[o + 1].toInt() and 0x7F shl 14) or
            (this[o + 2].toInt() and 0x7F shl 7) or (this[o + 3].toInt() and 0x7F)

    private fun ByteArray.beInt(o: Int): Int =
        (this[o].toInt() and 0xFF shl 24) or (this[o + 1].toInt() and 0xFF shl 16) or
            (this[o + 2].toInt() and 0xFF shl 8) or (this[o + 3].toInt() and 0xFF)

    private fun ByteArray.beInt24(o: Int): Int =
        (this[o].toInt() and 0xFF shl 16) or (this[o + 1].toInt() and 0xFF shl 8) or (this[o + 2].toInt() and 0xFF)

    private fun ByteArray.deunsynchronise(): ByteArray {
        val out = ByteArray(size)
        var w = 0
        var r = 0
        while (r < size) {
            out[w++] = this[r]
            r += if (this[r] == 0xFF.toByte() && r + 1 < size && this[r + 1] == ZERO) 2 else 1
        }
        return out.copyOf(w)
    }

    private const val LANGUAGE_BYTES = 3
    private const val UNSYNCHRONISATION = 0x80
    private const val EXTENDED_HEADER = 0x40
    private const val ZERO: Byte = 0
    private const val MAX_TAG_BYTES = 16 * 1024 * 1024
}
