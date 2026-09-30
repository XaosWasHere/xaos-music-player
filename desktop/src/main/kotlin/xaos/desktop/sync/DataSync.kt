package xaos.desktop.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xaos.desktop.CustomTheme
import xaos.desktop.ThemePreset
import xaos.desktop.Settings
import xaos.desktop.library.LibrarySnapshot
import xaos.desktop.library.PlayEntry
import xaos.desktop.library.Playlist
import xaos.desktop.library.Track
import xaos.desktop.library.UserData
import xaos.desktop.library.UserDataState
import java.io.File

/** Una playlist come la salva il telefono: id di MediaStore. */
@Serializable
data class PhonePlaylist(
    val id: String,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val description: String = "",
    /** L'impronta della copertina, il cui file viaggia a parte in sync/covers. */
    val cover: String? = null,
)

/** Quello che l'app Android esporta su richiesta (`phone-state.json`). */
@Serializable
data class PhoneData(
    val format: Int = 1,
    /** L'impronta dei dati: il telefono accetta le modifiche solo se nel frattempo non è cambiato nulla. */
    val stamp: String = "",
    val favorites: List<Long> = emptyList(),
    val playlists: List<PhonePlaylist> = emptyList(),
    /** Coppie [id, istante]. */
    val plays: List<List<Long>> = emptyList(),
    val historyClearedAt: Long = 0,
    /** Il tema personalizzato in uso; manca nelle app più vecchie della 1.5. */
    val theme: CustomTheme? = null,
    /** I preset dei temi; mancano nelle app più vecchie della 1.5.1. */
    val themePresets: List<ThemePreset>? = null,
)

/** Quello che il PC manda al telefono (`desktop-inbox.json`): lo stato finale, non le differenze. */
@Serializable
data class PhoneInbox(
    val format: Int = 1,
    val syncId: String,
    val forStamp: String,
    val favorites: List<Long>,
    val playlists: List<PhonePlaylist>,
    val plays: List<List<Long>>,
    val historyClearedAt: Long,
    /** Il tema da mettere in uso sul telefono; null lascia quello che c'è. */
    val theme: CustomTheme? = null,
    /** I preset concordati; null lascia quelli che ci sono. */
    val themePresets: List<ThemePreset>? = null,
)

/**
 * Com'erano le due parti alla fine dell'ultima sincronizzazione riuscita con
 * un certo telefono. È il punto di riferimento per capire chi ha cambiato
 * cosa: senza, un preferito tolto su un lato tornerebbe dall'altro.
 */
@Serializable
data class SyncBase(
    val phoneFavorites: Set<Long> = emptySet(),
    val phonePlaylists: List<PhonePlaylist> = emptyList(),
    val desktopFavorites: Set<String> = emptySet(),
    val desktopPlaylists: List<Playlist> = emptyList(),
    /** I preset dei temi concordati l'ultima volta. */
    val themePresets: List<ThemePreset> = emptyList(),
    val at: Long = 0,
)

data class DataSyncReport(
    val favoritesToPc: Int,
    val favoritesToPhone: Int,
    val favoritesRemoved: Int,
    val playlists: Int,
    val playsToPc: Int,
    val playsToPhone: Int,
    val unmatched: Int,
    val themeToPc: Boolean = false,
    val themeToPhone: Boolean = false,
    val presetsChanged: Boolean = false,
) {
    val nothingChanged: Boolean
        get() = favoritesToPc + favoritesToPhone + favoritesRemoved + playsToPc + playsToPhone == 0 &&
            !themeToPc && !themeToPhone && !presetsChanged
}

/** In che senso copiare il tema in uso, quando lo si chiede. */
enum class ThemeTransfer { PC_TO_PHONE, PHONE_TO_PC }

sealed interface DataSyncStatus {
    data object Idle : DataSyncStatus
    /** L'app sul telefono è troppo vecchia per scambiare i dati: va aggiornata. */
    data object NeedsAppUpdate : DataSyncStatus
    data object NoApp : DataSyncStatus
    data class Running(val step: String) : DataSyncStatus
    data class Done(val report: DataSyncReport, val at: Long) : DataSyncStatus
    data class Failed(val message: String) : DataSyncStatus
}

/**
 * Preferiti, playlist, ascolti e tema personalizzato, nei due sensi.
 *
 * Il telefono conosce i brani per id di MediaStore, il PC per percorso: a
 * ogni giro si ricostruisce la corrispondenza (il percorso con cui Xaos ha
 * mandato il file, altrimenti titolo, artista e durata) e ciascuna parte
 * riceve le modifiche dell'altra tradotte nei propri termini.
 *
 * Chi ha cambiato cosa si capisce confrontando ogni lato con [SyncBase], lo
 * stato concordato l'ultima volta. Se un brano è stato toccato da entrambe le
 * parti vince il telefono. Ciò che una parte non può rappresentare (un brano
 * che l'altra non ha) resta dov'è, senza perdersi.
 *
 * Lo scambio con l'app passa da due file nella sua cartella privata su
 * /sdcard/Android/data e da due broadcast che solo adb può inviare (sono
 * protetti dal permesso DUMP): EXPORT fa scrivere lo stato al telefono, APPLY
 * gli fa adottare quello calcolato qui. APPLY viene rifiutato se nel frattempo
 * sul telefono è cambiato qualcosa; in quel caso sul PC non si tocca nulla e si
 * riprova al giro dopo.
 */
class DataSync(
    private val scope: CoroutineScope,
    private val phone: PhoneSync,
    private val userData: UserData,
    private val baseDir: File,
    private val settings: Settings,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _status = MutableStateFlow<DataSyncStatus>(DataSyncStatus.Idle)
    val status: StateFlow<DataSyncStatus> = _status.asStateFlow()

    private var job: Job? = null

    /** Il trasferimento del tema chiesto dall'utente, fatto al prossimo scambio. */
    private val pendingTransfer = java.util.concurrent.atomic.AtomicReference<ThemeTransfer?>(null)

    private val _phoneTheme = MutableStateFlow<CustomTheme?>(null)
    /** Il tema in uso sul telefono, com'era all'ultimo scambio; null se non si sa. */
    val phoneTheme: StateFlow<CustomTheme?> = _phoneTheme.asStateFlow()

    /** Copia il tema in uso da una parte all'altra, con uno scambio completo. */
    fun transferTheme(direction: ThemeTransfer, snapshot: LibrarySnapshot, roots: List<File>) {
        pendingTransfer.set(direction)
        run(snapshot, roots)
    }

    fun reset() {
        if (job?.isActive != true) _status.value = DataSyncStatus.Idle
    }

    fun run(snapshot: LibrarySnapshot, roots: List<File>) {
        val connected = phone.state.value as? PhoneState.Connected ?: return
        if (job?.isActive == true) return
        if (!connected.appInstalled) { _status.value = DataSyncStatus.NoApp; return }
        if ((connected.appVersionCode ?: 0) < MIN_APP_VERSION) { _status.value = DataSyncStatus.NeedsAppUpdate; return }
        job = scope.launch(Dispatchers.IO) {
            _status.value = runCatching { DataSyncStatus.Done(exchange(connected.serial, snapshot, roots), System.currentTimeMillis()) }
                .getOrElse { DataSyncStatus.Failed(it.message ?: "sincronizzazione non riuscita") }
        }
    }

    private fun exchange(serial: String, snapshot: LibrarySnapshot, roots: List<File>): DataSyncReport {
        // 1. Il telefono scrive i suoi dati, e noi li leggiamo.
        _status.value = DataSyncStatus.Running("LETTURA DEI DATI DEL TELEFONO")
        broadcast(serial, ACTION_EXPORT).let { (code, data) ->
            if (code != RESULT_OK) error("l'app sul telefono non ha risposto (${data ?: code})")
        }
        val raw = phone.run(listOf("-s", serial, "exec-out", "cat $PHONE_DIR/$STATE_FILE"), timeoutS = 30)
            ?: error("impossibile leggere i dati del telefono")
        val p = runCatching { json.decodeFromString<PhoneData>(raw) }.getOrElse { error("dati del telefono illeggibili") }

        // Le copertine delle playlist del telefono che qui non ci sono ancora.
        p.playlists.mapNotNull { it.cover }.distinct()
            .filter { xaos.desktop.library.PlaylistCovers.fileOf(it) == null }
            .forEach { hash ->
                val target = File(xaos.desktop.library.PlaylistCovers.dir, "$hash.jpg")
                phone.run(listOf("-s", serial, "pull", "$PHONE_DIR/covers/$hash.jpg", target.path), timeoutS = 60)
            }

        // 2. La corrispondenza fra i brani dei due lati.
        _status.value = DataSyncStatus.Running("CONFRONTO DEI BRANI")
        val map = SongMap(phone.listPhoneSongs(serial), snapshot) { phone.remotePathOf(roots, it) }

        // 3. L'unione.
        val d = userData.state.value
        val baseFile = File(baseDir, "${serial.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")
        val base = runCatching { json.decodeFromString<SyncBase>(baseFile.readText()) }.getOrNull() ?: SyncBase()
        val merge = Merge(p, d, base, map, snapshot)

        // Il tema in uso non si sincronizza da solo: ognuno tiene il suo, e
        // lo si copia da una parte all'altra solo quando l'utente lo chiede.
        // Un'app troppo vecchia non manda il tema: la richiesta aspetta.
        val deskTheme = settings.data.value.customTheme.normalized()
        val phoneTheme = p.theme?.normalized()
        _phoneTheme.value = phoneTheme
        val transfer = if (phoneTheme != null) pendingTransfer.getAndSet(null) else null
        // I preset invece sì, come le playlist.
        val deskPresets = settings.data.value.themePresets.map { it.normalized() }
        val presets = p.themePresets?.map { it.normalized() }?.let { mergePresets(it, base.themePresets, deskPresets) }

        // 4. Il telefono adotta il risultato; il PC solo se il telefono ha accettato.
        _status.value = DataSyncStatus.Running("SCRITTURA SUL TELEFONO")
        val syncId = "s${System.currentTimeMillis()}"
        val inbox = PhoneInbox(
            syncId = syncId,
            forStamp = p.stamp,
            favorites = merge.phoneFavorites.toList(),
            playlists = merge.phonePlaylists,
            plays = merge.phonePlays,
            historyClearedAt = merge.clearedAt,
            theme = if (transfer == ThemeTransfer.PC_TO_PHONE) deskTheme else null,
            themePresets = presets,
        )
        // Le copertine scelte sul PC, che il telefono non ha.
        val phoneCovers = p.playlists.mapNotNull { it.cover }.toSet()
        merge.phonePlaylists.mapNotNull { it.cover }.distinct().filter { it !in phoneCovers }.forEach { hash ->
            xaos.desktop.library.PlaylistCovers.fileOf(hash)?.let { phone.push(serial, it, "$PHONE_DIR/covers/$hash.jpg") }
        }
        val tmp = File.createTempFile("xaos-inbox-", ".json")
        try {
            tmp.writeText(json.encodeToString(inbox), Charsets.UTF_8)
            if (!phone.push(serial, tmp, "$PHONE_DIR/$INBOX_FILE")) error("impossibile scrivere sul telefono")
        } finally {
            tmp.delete()
        }
        val (code, message) = broadcast(serial, ACTION_APPLY)
        when (code) {
            RESULT_OK -> Unit
            RESULT_STALE -> error("sul telefono qualcosa è cambiato proprio ora: riprova")
            else -> error("il telefono non ha accettato i dati (${message ?: code})")
        }

        // Le modifiche fatte sul PC mentre si sincronizzava non vanno perse.
        userData.update { now -> merge.desktopResult(now, d) }
        // Quello che è cambiato qui durante lo scambio resta: andrà al prossimo giro.
        settings.update { s ->
            var next = s
            if (transfer == ThemeTransfer.PHONE_TO_PC && phoneTheme != null && s.customTheme.normalized() == deskTheme) {
                next = next.copy(customTheme = phoneTheme)
            }
            if (presets != null && s.themePresets.map { it.normalized() } == deskPresets) {
                next = next.copy(themePresets = presets)
            }
            next
        }
        if (transfer == ThemeTransfer.PC_TO_PHONE) _phoneTheme.value = deskTheme
        baseDir.mkdirs()
        baseFile.writeText(
            json.encodeToString(
                SyncBase(
                    phoneFavorites = merge.phoneFavorites,
                    phonePlaylists = merge.phonePlaylists,
                    desktopFavorites = merge.desktopFavorites.toSet(),
                    desktopPlaylists = merge.desktopPlaylists,
                    themePresets = presets ?: base.themePresets,
                    at = System.currentTimeMillis(),
                )
            )
        )
        return merge.report.copy(
            themeToPc = transfer == ThemeTransfer.PHONE_TO_PC,
            themeToPhone = transfer == ThemeTransfer.PC_TO_PHONE,
            presetsChanged = presets != null && (presets != deskPresets || presets != p.themePresets?.map { it.normalized() }),
        )
    }

    /**
     * I preset delle due parti, uniti rispetto a quelli dell'ultimo accordo:
     * uno eliminato da una parte sparisce anche dall'altra, uno cambiato
     * (nome o colori) prende la modifica; se è cambiato da entrambe le parti
     * vince il telefono.
     */
    private fun mergePresets(phone: List<ThemePreset>, base: List<ThemePreset>, desk: List<ThemePreset>): List<ThemePreset> =
        (phone.map { it.id } + desk.map { it.id }).distinct().mapNotNull { id ->
            val p = phone.firstOrNull { it.id == id }
            val d = desk.firstOrNull { it.id == id }
            val b = base.firstOrNull { it.id == id }
            when {
                b != null && (p == null || d == null) -> null
                p != null && d != null -> if (b == null || p != b) p else d
                else -> p ?: d
            }
        }

    /** Invia un broadcast all'app e restituisce codice e dati del risultato. */
    private fun broadcast(serial: String, action: String): Pair<Int, String?> {
        // 32 = FLAG_INCLUDE_STOPPED_PACKAGES: funziona anche se l'app non è mai stata aperta da allora.
        val out = phone.shell(
            serial,
            "am broadcast -f 32 -a $action -n ${PhoneSync.APP_PACKAGE}/$RECEIVER",
            timeoutS = 30,
        ) ?: return RESULT_NONE to "adb non risponde"
        val m = Regex("""result=(-?\d+)(?:, data="(.*)")?""").find(out) ?: return RESULT_NONE to out.trim().take(120)
        return m.groupValues[1].toInt() to m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
    }

    companion object {
        /** La prima versione dell'app Android che sa scambiare questi dati (1.3.0). */
        const val MIN_APP_VERSION = 5
        const val ACTION_EXPORT = "com.example.xaosmusicplayer.sync.EXPORT"
        const val ACTION_APPLY = "com.example.xaosmusicplayer.sync.APPLY"
        const val RECEIVER = ".sync.DesktopSyncReceiver"
        const val PHONE_DIR = "/sdcard/Android/data/com.example.xaosmusicplayer/files/sync"
        const val STATE_FILE = "phone-state.json"
        const val INBOX_FILE = "desktop-inbox.json"
        const val RESULT_OK = -1
        const val RESULT_STALE = 2
        const val RESULT_NONE = 0
        const val MAX_PLAYS = 8000
    }
}

/**
 * La corrispondenza fra i brani del telefono (id) e quelli del PC (percorso).
 *
 * Prima il percorso: un file che Xaos ha mandato sta esattamente dove Xaos lo
 * ha messo. Poi titolo, artista e durata, per tutto il resto.
 */
private class SongMap(
    phoneSongs: List<PhoneSong>,
    private val snapshot: LibrarySnapshot,
    /** Dove Xaos manda un file del PC sul telefono. */
    expectedPhonePath: (File) -> String?,
) {
    private val phoneById = phoneSongs.filter { it.id >= 0 }.associateBy { it.id }
    private val phoneMatcher = SongMatcher(phoneSongs)
    private val pcMatcher = SongMatcher(snapshot.tracks.map { PhoneSong(it.title, it.artist, it.durationMs, it.path) })
    private val phoneByPath = phoneSongs.associateBy { norm(it.path) }

    /** Dove Xaos avrebbe messo ogni brano sul telefono → brano. */
    private val sentPaths: Map<String, Track> = buildMap {
        snapshot.tracks.forEach { t ->
            listOfNotNull(t.path, t.mobilePath).forEach { p -> expectedPhonePath(File(p))?.let { put(it, t) } }
        }
    }

    private val trackCache = HashMap<Long, Track?>()
    private val idsCache = HashMap<String, List<Long>>()

    fun trackOf(id: Long): Track? = trackCache.getOrPut(id) {
        val song = phoneById[id] ?: return@getOrPut null
        sentPaths[norm(song.path)]
            ?: pcMatcher.find(song.title, song.artist, song.durationMs)?.let { snapshot.byPath[it.path] }
    }

    /** Gli id del telefono che corrispondono a [track], il preferito per primo. */
    fun idsOf(track: Track): List<Long> = idsCache.getOrPut(track.path) {
        val byPath = sentPaths.entries.filter { it.value.path == track.path }.mapNotNull { phoneByPath[it.key] }
        val byTags = phoneMatcher.findAll(track.title, track.artist, track.durationMs)
        (byPath + byTags).distinct()
            .filter { trackOf(it.id)?.path == track.path }
            .sortedWith(compareBy<PhoneSong>({ "/Android/" in it.path }, { it.path.count { c -> c == '/' } }, { it.id }))
            .map { it.id }
    }

    private fun norm(path: String) = path.replace("/storage/emulated/0/", "/sdcard/")
}

/**
 * L'unione vera e propria. Lavora su "unità": un brano che esiste su entrambi
 * i lati (d:percorso), uno che c'è solo sul telefono (p:id) o solo sul PC
 * (x:percorso).
 */
private class Merge(
    private val p: PhoneData,
    private val d: UserDataState,
    private val base: SyncBase,
    private val map: SongMap,
    private val snapshot: LibrarySnapshot,
) {
    private fun unitOfPhone(id: Long): String = map.trackOf(id)?.let { "d:${it.path}" } ?: "p:$id"
    private fun unitOfDesk(path: String): String = snapshot.byPath[path]?.let { "d:${it.path}" } ?: "x:$path"

    /**
     * L'id del telefono per [unit]. Se fra [preferred] (gli id che il telefono
     * usava già in quel punto) ce n'è uno che corrisponde, si tiene quello e lo
     * si consuma: così una playlist del telefono torna indietro identica, con
     * gli stessi id, anche quando contiene due copie dello stesso brano.
     */
    private fun phoneIdOf(unit: String, preferred: MutableList<Long> = mutableListOf()): Long? = when {
        unit.startsWith("p:") -> unit.removePrefix("p:").toLongOrNull()?.also { preferred.remove(it) }
        unit.startsWith("d:") -> {
            preferred.firstOrNull { unitOfPhone(it) == unit }?.also { preferred.remove(it) }
                ?: snapshot.byPath[unit.removePrefix("d:")]?.let(map::idsOf)?.firstOrNull()
        }
        else -> null
    }

    private fun deskPathOf(unit: String): String? = when {
        unit.startsWith("d:") || unit.startsWith("x:") -> unit.drop(2)
        else -> null
    }

    // ---------------------------------------------------------- preferiti

    val phoneFavorites: Set<Long>
    val desktopFavorites: List<String>
    private var favToPc = 0
    private var favToPhone = 0
    private var favRemoved = 0
    private var unmatched = 0

    init {
        val pU = p.favorites.map(::unitOfPhone).toSet()
        val bpU = base.phoneFavorites.map(::unitOfPhone).toSet()
        val dU = d.favorites.map(::unitOfDesk).toSet()
        val bdU = base.desktopFavorites.map(::unitOfDesk).toSet()
        val finalUnits = (pU + bpU + dU + bdU).filter { u ->
            val inP = u in pU
            val inD = u in dU
            when {
                u.startsWith("p:") -> inP
                u.startsWith("x:") -> inD
                inP != (u in bpU) -> inP
                inD != (u in bdU) -> inD
                else -> inP || inD
            }
        }.toSet()
        unmatched = (pU.count { it.startsWith("p:") } + dU.count { it.startsWith("x:") })

        // Gli id e i percorsi già presenti restano quelli: niente giri inutili.
        val keptIds = p.favorites.filter { unitOfPhone(it) in finalUnits }
        val keptUnitsP = keptIds.map(::unitOfPhone).toSet()
        val addedIds = finalUnits.filter { it.startsWith("d:") && it !in keptUnitsP }.mapNotNull { phoneIdOf(it) }
        phoneFavorites = (keptIds + addedIds).toSet()

        val keptPaths = d.favorites.filter { unitOfDesk(it) in finalUnits }
        val keptUnitsD = keptPaths.map(::unitOfDesk).toSet()
        val addedPaths = finalUnits.filter { it.startsWith("d:") && it !in keptUnitsD }.map { it.drop(2) }
        desktopFavorites = (keptPaths + addedPaths).distinct()

        favToPhone = addedIds.size
        favToPc = addedPaths.size
        favRemoved = (p.favorites.size - keptIds.size) + (d.favorites.size - keptPaths.size)
    }

    // ---------------------------------------------------------- playlist

    val phonePlaylists: List<PhonePlaylist>
    val desktopPlaylists: List<Playlist>

    init {
        val ids = (p.playlists.map { it.id } + d.playlists.map { it.id } +
            base.phonePlaylists.map { it.id } + base.desktopPlaylists.map { it.id }).distinct()
        val outP = mutableListOf<PhonePlaylist>()
        val outD = mutableListOf<Playlist>()
        for (id in ids) {
            val pp = p.playlists.firstOrNull { it.id == id }
            val bp = base.phonePlaylists.firstOrNull { it.id == id }
            val dp = d.playlists.firstOrNull { it.id == id }
            val bd = base.desktopPlaylists.firstOrNull { it.id == id }
            // Cancellata da una parte dopo l'ultimo accordo: sparisce da entrambe.
            if ((bp != null && pp == null) || (bd != null && dp == null)) continue
            if (pp == null && dp == null) continue

            // Nome, descrizione e copertina: vale la modifica del telefono se
            // c'è stata dall'ultimo accordo, altrimenti quella del PC.
            fun <T> pick(phoneValue: T?, baseValue: T?, deskValue: T?): T? = when {
                pp == null -> deskValue
                dp == null -> phoneValue
                bp != null && phoneValue != baseValue -> phoneValue
                else -> deskValue
            }
            val name = pick(pp?.name, bp?.name, dp?.name)!!
            val description = pick(pp?.description, bp?.description, dp?.description).orEmpty()
            val cover = pick(pp?.cover, bp?.cover, dp?.cover)
            val pU = pp?.songIds?.map(::unitOfPhone)
            val dU = dp?.paths?.map(::unitOfDesk)
            val merged = when {
                dU == null -> pU!!
                pU == null -> dU
                else -> {
                    val bpU = bp?.songIds?.map(::unitOfPhone) ?: pU
                    val bdU = bd?.paths?.map(::unitOfDesk) ?: dU
                    val phoneChanged = pU != bpU
                    val deskChanged = dU != bdU
                    when {
                        !phoneChanged && !deskChanged -> weave(pU, dU) { !it.startsWith("p:") }
                        !phoneChanged -> weave(dU, pU) { it.startsWith("p:") }
                        !deskChanged -> weave(pU, dU) { it.startsWith("x:") }
                        else -> {
                            val removedOnDesk = (bdU - dU.toSet()).toSet()
                            val addedOnDesk = dU.filter { it !in bdU }
                            pU.filter { it !in removedOnDesk } + addedOnDesk.filter { it !in pU }
                        }
                    }
                }
            }
            val oldIds = pp?.songIds.orEmpty().toMutableList()
            outP += PhonePlaylist(id, name, merged.mapNotNull { phoneIdOf(it, oldIds) }.distinct(), description, cover)
            outD += Playlist(id, name, merged.mapNotNull(::deskPathOf).distinct(), description, cover)
        }
        phonePlaylists = outP
        desktopPlaylists = outD
    }

    /** [primary] con in più gli elementi di [other] che [keep] accetta, ciascuno dopo quello che lo precedeva. */
    private fun weave(primary: List<String>, other: List<String>, keep: (String) -> Boolean): List<String> {
        val result = primary.toMutableList()
        other.forEachIndexed { i, u ->
            if (!keep(u) || u in result) return@forEachIndexed
            val before = (i - 1 downTo 0).map { other[it] }.firstOrNull { it in result }
            result.add(if (before == null) 0 else result.indexOf(before) + 1, u)
        }
        return result
    }

    // ---------------------------------------------------------- ascolti

    val clearedAt = maxOf(p.historyClearedAt, d.historyClearedAt)
    val phonePlays: List<List<Long>>
    val desktopPlays: List<PlayEntry>
    private var playsToPc = 0
    private var playsToPhone = 0

    init {
        // Un ascolto è identificato dal suo istante al millisecondo: due
        // ascolti diversi non cadono mai nello stesso, e così la stessa
        // riproduzione non si conta due volte anche se il brano ha più copie.
        val phoneTimes = p.plays.mapNotNull { it.getOrNull(1) }.toHashSet()
        val deskTimes = d.plays.map { it.at }.toHashSet()
        val toPc = p.plays.mapNotNull { e ->
            val id = e.getOrNull(0) ?: return@mapNotNull null
            val at = e.getOrNull(1) ?: return@mapNotNull null
            if (at in deskTimes || at <= clearedAt) return@mapNotNull null
            map.trackOf(id)?.let { PlayEntry(it.path, at) }
        }
        val toPhone = d.plays.mapNotNull { e ->
            if (e.at in phoneTimes || e.at <= clearedAt) return@mapNotNull null
            val track = snapshot.byPath[e.path] ?: return@mapNotNull null
            map.idsOf(track).firstOrNull()?.let { listOf(it, e.at) }
        }
        playsToPc = toPc.size
        playsToPhone = toPhone.size
        desktopPlays = (d.plays.filter { it.at > clearedAt } + toPc).sortedBy { it.at }.takeLast(DataSync.MAX_PLAYS)
        phonePlays = (p.plays.filter { (it.getOrNull(1) ?: 0) > clearedAt } + toPhone)
            .sortedBy { it.getOrNull(1) ?: 0 }.takeLast(DataSync.MAX_PLAYS)
    }

    val report: DataSyncReport
        get() = DataSyncReport(
            favoritesToPc = favToPc,
            favoritesToPhone = favToPhone,
            favoritesRemoved = favRemoved,
            playlists = desktopPlaylists.size,
            playsToPc = playsToPc,
            playsToPhone = playsToPhone,
            unmatched = unmatched,
        )

    /**
     * Il risultato per il PC, applicato sopra lo stato [now]: se durante la
     * sincronizzazione sul PC è cambiato qualcosa rispetto a [before], quelle
     * modifiche si rimettono sopra.
     */
    fun desktopResult(now: UserDataState, before: UserDataState): UserDataState {
        val addedMeanwhile = now.favorites - before.favorites.toSet()
        val removedMeanwhile = (before.favorites - now.favorites.toSet()).toSet()
        val playsMeanwhile = now.plays.filter { e -> before.plays.none { it.at == e.at } }
        val refs = now.refs + desktopPlays.mapNotNull { e -> snapshot.byPath[e.path]?.let { e.path to it.ref() } } +
            desktopFavorites.mapNotNull { path -> snapshot.byPath[path]?.let { path to it.ref() } } +
            desktopPlaylists.flatMap { it.paths }.mapNotNull { path -> snapshot.byPath[path]?.let { path to it.ref() } }
        return now.copy(
            favorites = (desktopFavorites.filter { it !in removedMeanwhile } + addedMeanwhile).distinct(),
            playlists = if (now.playlists == before.playlists) desktopPlaylists else now.playlists,
            plays = (desktopPlays + playsMeanwhile).sortedBy { it.at }.takeLast(DataSync.MAX_PLAYS),
            historyClearedAt = maxOf(now.historyClearedAt, clearedAt),
            refs = refs,
        )
    }

    private fun Track.ref() = xaos.desktop.library.SongRef(title, artist, durationMs)
}
