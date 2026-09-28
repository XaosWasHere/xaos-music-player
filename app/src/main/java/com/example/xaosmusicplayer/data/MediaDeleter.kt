package com.example.xaosmusicplayer.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.util.Log

/** Esito del tentativo di eliminare un brano dalla memoria del dispositivo. */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    /**
     * Il file non è dell'app: serve il consenso esplicito dell'utente, che
     * Android chiede con una propria finestra da lanciare con questo sender.
     */
    data class NeedsConsent(val intentSender: IntentSender) : DeleteOutcome

    data class Failed(val message: String) : DeleteOutcome
}

/**
 * Elimina brani da MediaStore.
 *
 * I file scaricati da Xaos sono suoi e si cancellano subito; quelli messi lì da
 * altre app richiedono un consenso di sistema. Da Android 11 quel consenso si
 * ottiene con `createDeleteRequest`, che è anche la conferma dell'operazione:
 * per questo l'app non ne mostra una propria, che sarebbe una doppia domanda.
 */
class MediaDeleter(private val context: Context) {

    fun delete(song: Song): DeleteOutcome {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Su Android 11+ il sistema decide da solo se serve il consenso:
            // se il file è nostro la finestra non compare.
            return DeleteOutcome.NeedsConsent(
                MediaStore.createDeleteRequest(resolver, listOf(song.uri)).intentSender
            )
        }

        return try {
            val removed = resolver.delete(song.uri, null, null)
            if (removed > 0) DeleteOutcome.Deleted
            else DeleteOutcome.Failed("Il brano non è stato trovato")
        } catch (security: SecurityException) {
            val recoverable = security as? RecoverableSecurityException
                ?: return DeleteOutcome.Failed("Permesso negato")
            DeleteOutcome.NeedsConsent(recoverable.userAction.actionIntent.intentSender)
        } catch (error: Exception) {
            Log.e(TAG, "Eliminazione fallita", error)
            DeleteOutcome.Failed(error.message ?: "Eliminazione fallita")
        }
    }

    private companion object {
        const val TAG = "MediaDeleter"
    }
}
