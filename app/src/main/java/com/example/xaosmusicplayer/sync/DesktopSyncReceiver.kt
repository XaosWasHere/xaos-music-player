package com.example.xaosmusicplayer.sync

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.xaosmusicplayer.data.PlayEvent
import com.example.xaosmusicplayer.data.Playlist
import com.example.xaosmusicplayer.data.PlaylistCovers
import com.example.xaosmusicplayer.data.UserPreferences
import com.example.xaosmusicplayer.ui.theme.CustomTheme
import com.example.xaosmusicplayer.ui.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lo scambio di preferiti, playlist, ascolti e tema con Xaos desktop.
 *
 * È il PC a fare tutto il lavoro — riconoscere i brani, unire le modifiche
 * dei due lati — e a chiamare questo ricevitore via adb, con due comandi:
 *
 * - [ACTION_EXPORT]: scrive lo stato attuale in `phone-state.json`;
 * - [ACTION_APPLY]: adotta lo stato calcolato dal PC, `desktop-inbox.json`,
 *   ma solo se nel frattempo qui non è cambiato niente (l'impronta combacia).
 *
 * I file stanno nella cartella dell'app su /sdcard/Android/data, dove adb può
 * leggere e scrivere e le altre app no. Il ricevitore è protetto dal permesso
 * DUMP, che hanno solo il sistema e adb: nessuna app può invocarlo.
 */
class DesktopSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            val (code, message) = runCatching {
                when (intent.action) {
                    ACTION_EXPORT -> Activity.RESULT_OK to export(app)
                    ACTION_APPLY -> apply(app)
                    else -> RESULT_ERROR to "azione sconosciuta"
                }
            }.getOrElse { RESULT_ERROR to (it.message ?: it.javaClass.simpleName) }
            pending.setResult(code, message, null)
            pending.finish()
        }
    }

    private suspend fun export(context: Context): String {
        val snapshot = UserPreferences(context).syncSnapshot()
        val root = JSONObject().apply {
            put("format", FORMAT)
            put("stamp", snapshot.stamp)
            put("favorites", JSONArray().also { arr -> snapshot.favorites.forEach(arr::put) })
            put("playlists", JSONArray().also { arr -> snapshot.playlists.forEach { arr.put(it.toJson(context)) } })
            put("plays", JSONArray().also { arr ->
                snapshot.events.forEach { arr.put(JSONArray().put(it.songId).put(it.timestampMs)) }
            })
            put("historyClearedAt", snapshot.historyClearedAt)
            put("theme", ThemeStore.get(context).custom.value.toJson())
        }
        writeAtomically(File(dir(context), STATE_FILE), root.toString())
        return snapshot.stamp
    }

    private suspend fun apply(context: Context): Pair<Int, String> {
        val inbox = File(dir(context), INBOX_FILE)
        if (!inbox.isFile) return RESULT_ERROR to "nessun dato dal PC"
        val obj = JSONObject(inbox.readText())
        if (obj.optInt("format") != FORMAT) return RESULT_ERROR to "formato non supportato"

        val favorites = obj.getJSONArray("favorites").let { arr -> (0 until arr.length()).map { arr.getLong(it) }.toSet() }
        val playlists = obj.getJSONArray("playlists").let { arr ->
            (0 until arr.length()).map { i ->
                val pl = arr.getJSONObject(i)
                val ids = pl.getJSONArray("songIds")
                Playlist(
                    id = pl.getString("id"),
                    name = pl.getString("name"),
                    songIds = (0 until ids.length()).map { ids.getLong(it) },
                    description = pl.optString("description"),
                    coverPath = coverFromDesktop(context, pl.optString("cover").takeIf { it.isNotBlank() && it != "null" }),
                )
            }
        }
        val events = obj.getJSONArray("plays").let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val pair = arr.optJSONArray(i) ?: return@mapNotNull null
                if (pair.length() < 2) null else PlayEvent(pair.getLong(0), pair.getLong(1))
            }
        }

        val prefs = UserPreferences(context)
        val applied = prefs.applySync(
            expectedStamp = obj.getString("forStamp"),
            favorites = favorites,
            playlists = playlists,
            events = events,
            historyClearedAt = obj.optLong("historyClearedAt"),
            // Un PC più vecchio non manda il tema: allora resta quello di qui.
            theme = obj.optJSONObject("theme")?.let(CustomTheme::fromJson),
        )
        inbox.delete()
        if (!applied) return RESULT_STALE to "dati cambiati nel frattempo"
        // Lo stato nuovo, così il PC può verificarlo e ripartire da qui.
        return Activity.RESULT_OK to export(context)
    }

    /**
     * La copertina viaggia a parte, come file in sync/covers col nome della sua
     * impronta; nel JSON c'è solo l'impronta.
     */
    private fun Playlist.toJson(context: Context) = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("songIds", JSONArray().also { arr -> songIds.forEach(arr::put) })
        put("description", description)
        val cover = coverPath?.let(::File)?.takeIf { it.isFile }
        if (cover != null) {
            val hash = PlaylistCovers.hashOf(cover.path)!!
            val shared = File(coversDir(context), "$hash.jpg")
            if (!shared.isFile) cover.copyTo(shared, overwrite = true)
            put("cover", hash)
        }
    }

    private fun coversDir(context: Context) = File(dir(context), "covers").apply { mkdirs() }

    /** La copertina [hash] arrivata dal PC, copiata fra quelle dell'app. */
    private fun coverFromDesktop(context: Context, hash: String?): String? {
        if (hash.isNullOrBlank()) return null
        val local = PlaylistCovers.fileFor(context, hash)
        if (!local.isFile) {
            val shared = File(coversDir(context), "$hash.jpg")
            if (!shared.isFile) return null
            shared.copyTo(local, overwrite = true)
        }
        return local.absolutePath
    }

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: error("memoria non disponibile"), "sync").apply { mkdirs() }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        const val ACTION_EXPORT = "com.example.xaosmusicplayer.sync.EXPORT"
        const val ACTION_APPLY = "com.example.xaosmusicplayer.sync.APPLY"
        private const val STATE_FILE = "phone-state.json"
        private const val INBOX_FILE = "desktop-inbox.json"
        private const val FORMAT = 1
        private const val RESULT_STALE = 2
        private const val RESULT_ERROR = 3

        /** I broadcast finiscono presto: basta un ambito condiviso, senza legarlo a un'activity. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
