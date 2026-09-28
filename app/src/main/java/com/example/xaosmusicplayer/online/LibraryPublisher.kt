package com.example.xaosmusicplayer.online

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Consegna un file scaricato alla libreria musicale del dispositivo.
 *
 * Il percorso cambia radicalmente con lo scoped storage: da Android 10 il file
 * va inserito attraverso MediaStore, prima si scriveva direttamente nella
 * cartella Music pubblica e si notificava lo scanner.
 */
class LibraryPublisher(private val context: Context) {

    fun publish(source: File, title: String, artist: String): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishViaMediaStore(source, title, artist)
        } else {
            publishToPublicDirectory(source, title)
        }
    }

    private fun publishViaMediaStore(source: File, title: String, artist: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_MP3)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.RELATIVE_PATH, MUSIC_SUBDIR)
            // Finché è pending il brano resta invisibile alle altre app: evita
            // che un file a metà compaia nelle librerie.
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore ha rifiutato l'inserimento")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Impossibile scrivere su $uri")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            // Senza questo, un inserimento fallito lascia una riga pending
            // fantasma che nessuno ripulirà.
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun publishToPublicDirectory(source: File, title: String) {
        @Suppress("DEPRECATION")
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val targetDir = File(musicDir, XAOS_FOLDER).apply { mkdirs() }
        val target = File(targetDir, source.name)

        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        // Prima di Android 10 MediaStore non si accorge da solo del file nuovo.
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(MIME_MP3),
            null,
        )
    }

    private companion object {
        const val MIME_MP3 = "audio/mpeg"
        const val XAOS_FOLDER = "Xaos"
        val MUSIC_SUBDIR = Environment.DIRECTORY_MUSIC + "/" + XAOS_FOLDER
    }
}
