package com.example.xaosmusicplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Le copertine delle playlist, copiate nello spazio privato dell'app.
 *
 * Ogni file prende il nome dall'impronta del suo contenuto: la stessa immagine
 * ha lo stesso nome qui e su Xaos desktop, e la sincronizzazione capisce da
 * sola se deve trasferirla.
 */
object PlaylistCovers {

    private const val TAG = "PlaylistCovers"
    private const val DIR = "playlist_covers"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** L'impronta di una copertina, cioè il nome del suo file senza estensione. */
    fun hashOf(path: String?): String? = path?.let { File(it).nameWithoutExtension }

    fun fileFor(context: Context, hash: String): File = File(dir(context), "$hash.jpg")

    fun hashOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).take(10).joinToString("") { "%02x".format(it) }

    /** Copia l'immagine scelta dall'utente e ne restituisce il percorso. */
    suspend fun save(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: return@runCatching null
            val target = fileFor(context, hashOf(bytes))
            if (!target.isFile) target.writeBytes(bytes)
            target.absolutePath
        }.onFailure { Log.e(TAG, "Copertina non salvata", it) }.getOrNull()
    }
}
