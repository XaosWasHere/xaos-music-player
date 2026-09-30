package com.example.xaosmusicplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xaos_prefs")

/**
 * Stato persistente dell'utente: preferiti, playlist, equalizzatore, riproduzione.
 *
 * Le collezioni sono serializzate in JSON dentro una singola chiave stringa. Per i
 * volumi in gioco (qualche migliaio di id) è più che sufficiente ed evita di
 * introdurre un database con annotation processing.
 */
class UserPreferences(private val context: Context) {

    // ---------- Preferiti ----------

    val favorites: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        prefs[KEY_FAVORITES].toLongSet()
    }

    suspend fun toggleFavorite(songId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES].toLongSet().toMutableSet()
            if (!current.add(songId)) current.remove(songId)
            prefs[KEY_FAVORITES] = current.toJsonArrayString()
        }
    }

    // ---------- Playlist ----------

    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { prefs ->
        parsePlaylists(prefs[KEY_PLAYLISTS])
    }

    suspend fun createPlaylist(name: String): String {
        val id = "pl_${System.currentTimeMillis()}"
        editPlaylists { it + Playlist(id, name, emptyList()) }
        return id
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        editPlaylists { list ->
            list.map { if (it.id == playlistId) it.copy(name = newName) else it }
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        editPlaylists { list -> list.filterNot { it.id == playlistId } }
    }

    /** Aggiunge in coda, ignorando i brani già presenti nella playlist. */
    suspend fun addToPlaylist(playlistId: String, songIds: List<Long>) {
        editPlaylists { list ->
            list.map { pl ->
                if (pl.id != playlistId) pl
                else pl.copy(songIds = pl.songIds + songIds.filterNot { it in pl.songIds })
            }
        }
    }

    suspend fun removeFromPlaylist(playlistId: String, songId: Long) {
        editPlaylists { list ->
            list.map { pl ->
                if (pl.id == playlistId) pl.copy(songIds = pl.songIds - songId) else pl
            }
        }
    }

    /** Riordina un brano dentro la playlist, per il drag-and-drop nella UI. */
    suspend fun movePlaylistItem(playlistId: String, from: Int, to: Int) {
        editPlaylists { list ->
            list.map { pl ->
                if (pl.id != playlistId) return@map pl
                val ids = pl.songIds.toMutableList()
                if (from !in ids.indices || to !in ids.indices) return@map pl
                ids.add(to, ids.removeAt(from))
                pl.copy(songIds = ids)
            }
        }
    }

    private suspend fun editPlaylists(transform: (List<Playlist>) -> List<Playlist>) {
        context.dataStore.edit { prefs ->
            val updated = transform(parsePlaylists(prefs[KEY_PLAYLISTS]))
            prefs[KEY_PLAYLISTS] = serializePlaylists(updated)
        }
    }

    // ---------- Equalizzatore ----------

    val equalizerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_EQ_ENABLED] ?: false }

    /** Livello di ciascuna banda in millibel, nell'ordine restituito dal device. */
    val equalizerBands: Flow<List<Int>> =
        context.dataStore.data.map { it[KEY_EQ_BANDS].toIntList() }

    val equalizerPreset: Flow<Int> =
        context.dataStore.data.map { it[KEY_EQ_PRESET] ?: PRESET_CUSTOM }

    /**
     * Attenuazione applicata prima della catena di effetti, in decibel (≤ 0).
     *
     * Virtualizer e bass boost saturano quando il segnale entra a piena
     * ampiezza, e la distorsione cresce con la loro intensità. Qualche dB di
     * margine a monte è l'unico rimedio: gli AudioEffect di Android non hanno
     * un guadagno d'ingresso proprio.
     */
    val preampDb: Flow<Int> = context.dataStore.data.map { it[KEY_PREAMP_DB] ?: DEFAULT_PREAMP_DB }

    suspend fun setPreampDb(db: Int) {
        context.dataStore.edit { it[KEY_PREAMP_DB] = db.coerceIn(MIN_PREAMP_DB, 0) }
    }

    val bassBoost: Flow<Int> = context.dataStore.data.map { it[KEY_BASS_BOOST] ?: 0 }
    val virtualizer: Flow<Int> = context.dataStore.data.map { it[KEY_VIRTUALIZER] ?: 0 }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EQ_ENABLED] = enabled }
    }

    suspend fun setEqualizerBands(levels: List<Int>) {
        context.dataStore.edit {
            it[KEY_EQ_BANDS] = levels.toJsonArrayString()
            it[KEY_EQ_PRESET] = PRESET_CUSTOM
        }
    }

    suspend fun setEqualizerPreset(preset: Int, resultingLevels: List<Int>) {
        context.dataStore.edit {
            it[KEY_EQ_PRESET] = preset
            it[KEY_EQ_BANDS] = resultingLevels.toJsonArrayString()
        }
    }

    suspend fun setBassBoost(strength: Int) {
        context.dataStore.edit { it[KEY_BASS_BOOST] = strength.coerceIn(0, 1000) }
    }

    suspend fun setVirtualizer(strength: Int) {
        context.dataStore.edit { it[KEY_VIRTUALIZER] = strength.coerceIn(0, 1000) }
    }

    // ---------- Riproduzione e aspetto ----------

    val shuffleEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHUFFLE] ?: false }

    /** 0 = off, 1 = ripeti tutto, 2 = ripeti brano. Corrisponde a Player.RepeatMode. */
    val repeatMode: Flow<Int> = context.dataStore.data.map { it[KEY_REPEAT] ?: 0 }

    val glowMode: Flow<GlowMode> = context.dataStore.data.map { prefs ->
        prefs[KEY_GLOW_MODE]
            ?.let { name -> runCatching { GlowMode.valueOf(name) }.getOrNull() }
            ?: GlowMode.ANIMATED
    }

    suspend fun setGlowMode(mode: GlowMode) {
        context.dataStore.edit { it[KEY_GLOW_MODE] = mode.name }
    }

    // ---------- Cronologia degli ascolti ----------

    /**
     * Gli ascolti registrati, dal più vecchio al più recente.
     *
     * La lista è limitata a [MAX_EVENTS]: serve ad alimentare "riprendi da qui",
     * il riepilogo del mese e le playlist automatiche, non a essere un archivio
     * completo. Tenerla corta mantiene anche piccola la scrittura su DataStore,
     * che avviene a ogni cambio di brano.
     */
    val playEvents: Flow<List<PlayEvent>> = context.dataStore.data.map { prefs ->
        parseEvents(prefs[KEY_PLAY_EVENTS])
    }

    suspend fun recordPlay(songId: Long, timestampMs: Long) {
        context.dataStore.edit { prefs ->
            val events = parseEvents(prefs[KEY_PLAY_EVENTS]) + PlayEvent(songId, timestampMs)
            prefs[KEY_PLAY_EVENTS] = serializeEvents(events.takeLast(MAX_EVENTS))
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit {
            it.remove(KEY_PLAY_EVENTS)
            // Il PC deve saperlo, altrimenti alla prossima sincronizzazione
            // rimanderebbe indietro tutti gli ascolti appena cancellati.
            it[KEY_HISTORY_CLEARED_AT] = System.currentTimeMillis()
        }
    }

    // ---------- Sincronizzazione con Xaos desktop ----------

    /** Preferiti, playlist e ascolti così come sono ora, con la loro impronta. */
    data class SyncSnapshot(
        val favorites: Set<Long>,
        val playlists: List<Playlist>,
        val events: List<PlayEvent>,
        val historyClearedAt: Long,
        val stamp: String,
    )

    suspend fun syncSnapshot(): SyncSnapshot {
        val prefs = context.dataStore.data.first()
        return SyncSnapshot(
            favorites = prefs[KEY_FAVORITES].toLongSet(),
            playlists = parsePlaylists(prefs[KEY_PLAYLISTS]),
            events = parseEvents(prefs[KEY_PLAY_EVENTS]),
            historyClearedAt = prefs[KEY_HISTORY_CLEARED_AT] ?: 0L,
            stamp = stampOf(prefs),
        )
    }

    /**
     * Sostituisce preferiti, playlist e ascolti con quelli calcolati dal PC, ma
     * solo se da quando il PC li ha letti ([expectedStamp]) non è cambiato
     * niente: una modifica fatta in quell'istante non va persa. Restituisce
     * false se i dati erano cambiati.
     */
    suspend fun applySync(
        expectedStamp: String,
        favorites: Set<Long>,
        playlists: List<Playlist>,
        events: List<PlayEvent>,
        historyClearedAt: Long,
    ): Boolean {
        var applied = false
        context.dataStore.edit { prefs ->
            if (stampOf(prefs) != expectedStamp) return@edit
            prefs[KEY_FAVORITES] = favorites.toJsonArrayString()
            prefs[KEY_PLAYLISTS] = serializePlaylists(playlists)
            prefs[KEY_PLAY_EVENTS] = serializeEvents(events.sortedBy { it.timestampMs }.takeLast(MAX_EVENTS))
            prefs[KEY_HISTORY_CLEARED_AT] = historyClearedAt
            applied = true
        }
        return applied
    }

    /** L'impronta dei dati sincronizzati: cambia con qualunque modifica. */
    private fun stampOf(prefs: Preferences): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        listOf(
            prefs[KEY_FAVORITES].orEmpty(),
            prefs[KEY_PLAYLISTS].orEmpty(),
            prefs[KEY_PLAY_EVENTS].orEmpty(),
            (prefs[KEY_HISTORY_CLEARED_AT] ?: 0L).toString(),
        ).forEach { digest.update(it.toByteArray()); digest.update(0) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------- Correzioni ai metadati ----------

    val songOverrides: Flow<Map<Long, SongOverride>> = context.dataStore.data.map { prefs ->
        parseOverrides(prefs[KEY_OVERRIDES])
    }

    suspend fun setSongOverride(songId: Long, override: SongOverride) {
        context.dataStore.edit { prefs ->
            val current = parseOverrides(prefs[KEY_OVERRIDES]).toMutableMap()
            // Una correzione vuota equivale a nessuna correzione: non la teniamo.
            if (override.isEmpty) current.remove(songId) else current[songId] = override
            prefs[KEY_OVERRIDES] = serializeOverrides(current)
        }
    }

    /**
     * Scrive più correzioni in un colpo solo.
     *
     * Riordinare un album tocca tutte le sue tracce: farlo con una chiamata per
     * brano significherebbe altrettante riscritture del file di preferenze, e
     * altrettante emissioni verso la UI.
     */
    suspend fun setSongOverrides(updates: Map<Long, SongOverride>) {
        context.dataStore.edit { prefs ->
            val current = parseOverrides(prefs[KEY_OVERRIDES]).toMutableMap()
            updates.forEach { (id, override) ->
                if (override.isEmpty) current.remove(id) else current[id] = override
            }
            prefs[KEY_OVERRIDES] = serializeOverrides(current)
        }
    }

    suspend fun clearSongOverride(songId: Long) {
        setSongOverride(songId, SongOverride())
    }

    suspend fun setShuffle(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHUFFLE] = enabled }
    }

    suspend fun setRepeatMode(mode: Int) {
        context.dataStore.edit { it[KEY_REPEAT] = mode }
    }


    // ---------- Serializzazione ----------

    private fun Set<Long>.toJsonArrayString(): String =
        JSONArray().also { arr -> forEach { arr.put(it) } }.toString()

    @JvmName("intListToJson")
    private fun List<Int>.toJsonArrayString(): String =
        JSONArray().also { arr -> forEach { arr.put(it) } }.toString()

    private fun String?.toLongSet(): Set<Long> {
        if (this.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(this)
            buildSet { for (i in 0 until arr.length()) add(arr.getLong(i)) }
        }.getOrDefault(emptySet())
    }

    private fun String?.toIntList(): List<Int> {
        if (this.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(this)
            buildList { for (i in 0 until arr.length()) add(arr.getInt(i)) }
        }.getOrDefault(emptyList())
    }

    private fun parsePlaylists(raw: String?): List<Playlist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val idsArr = obj.optJSONArray("songIds") ?: JSONArray()
                    add(
                        Playlist(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            songIds = buildList {
                                for (j in 0 until idsArr.length()) add(idsArr.getLong(j))
                            },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Ogni evento è una coppia [id, timestamp]: più compatta di un oggetto. */
    private fun parseEvents(raw: String?): List<PlayEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val pair = arr.optJSONArray(i) ?: continue
                    if (pair.length() < 2) continue
                    add(PlayEvent(pair.getLong(0), pair.getLong(1)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeEvents(events: List<PlayEvent>): String {
        val arr = JSONArray()
        events.forEach { event ->
            arr.put(JSONArray().put(event.songId).put(event.timestampMs))
        }
        return arr.toString()
    }

    private fun parseOverrides(raw: String?): Map<Long, SongOverride> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val id = key.toLongOrNull() ?: return@forEach
                    val entry = obj.getJSONObject(key)
                    put(
                        id,
                        SongOverride(
                            title = entry.optStringOrNull("title"),
                            artist = entry.optStringOrNull("artist"),
                            albumArtist = entry.optStringOrNull("albumArtist"),
                            album = entry.optStringOrNull("album"),
                            genre = entry.optStringOrNull("genre"),
                            trackNumber = if (entry.has("track")) entry.optInt("track") else null,
                            artworkPath = entry.optStringOrNull("artwork"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun serializeOverrides(overrides: Map<Long, SongOverride>): String {
        val root = JSONObject()
        overrides.forEach { (id, override) ->
            root.put(
                id.toString(),
                JSONObject().apply {
                    override.title?.let { put("title", it) }
                    override.artist?.let { put("artist", it) }
                    override.albumArtist?.let { put("albumArtist", it) }
                    override.album?.let { put("album", it) }
                    override.genre?.let { put("genre", it) }
                    override.trackNumber?.let { put("track", it) }
                    override.artworkPath?.let { put("artwork", it) }
                },
            )
        }
        return root.toString()
    }

    /** `optString` restituisce "" per le chiavi assenti, che qui non va bene. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key)) optString(key).takeUnless { it.isBlank() } else null

    private fun serializePlaylists(playlists: List<Playlist>): String {
        val arr = JSONArray()
        playlists.forEach { pl ->
            arr.put(
                JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("songIds", JSONArray().also { ids -> pl.songIds.forEach(ids::put) })
                }
            )
        }
        return arr.toString()
    }

    companion object {
        const val PRESET_CUSTOM = -1

        /**
         * Quanti ascolti tenere prima di scartare i più vecchi.
         *
         * Il riepilogo annuale ha senso solo se l'anno ci sta dentro: a 2000
         * eventi un ascolto quotidiano assiduo avrebbe già perso i primi mesi.
         * Ogni evento sono due numeri, quindi anche 8000 restano poche centinaia
         * di kilobyte riscritti a ogni cambio di brano.
         */
        const val MAX_EVENTS = 8000

        /** Margine predefinito, sufficiente al caso comune. */
        const val DEFAULT_PREAMP_DB = -3
        const val MIN_PREAMP_DB = -15


        private val KEY_FAVORITES = stringPreferencesKey("favorites")
        private val KEY_PLAYLISTS = stringPreferencesKey("playlists")
        private val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        private val KEY_EQ_BANDS = stringPreferencesKey("eq_bands")
        private val KEY_EQ_PRESET = intPreferencesKey("eq_preset")
        private val KEY_BASS_BOOST = intPreferencesKey("bass_boost")
        private val KEY_PREAMP_DB = intPreferencesKey("preamp_db")
        private val KEY_VIRTUALIZER = intPreferencesKey("virtualizer")
        private val KEY_SHUFFLE = booleanPreferencesKey("shuffle")
        private val KEY_REPEAT = intPreferencesKey("repeat")
        private val KEY_GLOW_MODE = stringPreferencesKey("glow_mode")
        private val KEY_PLAY_EVENTS = stringPreferencesKey("play_events")
        private val KEY_HISTORY_CLEARED_AT = androidx.datastore.preferences.core.longPreferencesKey("history_cleared_at")
        private val KEY_OVERRIDES = stringPreferencesKey("song_overrides")
    }
}
