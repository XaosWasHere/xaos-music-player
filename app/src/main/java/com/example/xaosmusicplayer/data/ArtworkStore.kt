package com.example.xaosmusicplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Custodisce le copertine scelte dall'utente.
 *
 * L'immagine viene copiata nello spazio privato dell'app: l'URI restituito dal
 * selettore è un permesso temporaneo che scade, quindi conservarlo così com'è
 * significherebbe ritrovarsi una copertina rotta al riavvio.
 */
class ArtworkStore(private val context: Context) {

    suspend fun save(source: Uri, songId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val target = File(dir, "$songId.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target.absolutePath
        }.onFailure { Log.e(TAG, "Copertina non salvata", it) }.getOrNull()
    }

    /** Rimuove la copertina personalizzata di un brano, se esiste. */
    fun delete(songId: Long) {
        runCatching { File(File(context.filesDir, DIR), "$songId.jpg").delete() }
    }

    private companion object {
        const val TAG = "ArtworkStore"
        const val DIR = "artwork"
    }
}
