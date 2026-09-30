package xaos.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Lo sfondo del player a schermo intero: le stesse scelte del telefono. */
@Serializable
enum class FullscreenBackground(val label: String) {
    /** Le onde di tutto il brano, alla SoundCloud, che si accendono man mano. */
    WAVEFORM("ONDE"),
    ARTWORK("COPERTINA"),
    OFF("SPENTA"),
}

/** L'equalizzatore di VLC: dieci bande e un preamplificatore, in dB (±20). */
@Serializable
data class EqualizerSettings(
    val enabled: Boolean = false,
    /** Il preset da cui si è partiti, se non è stato ritoccato a mano. */
    val preset: String? = null,
    val preamp: Float = 0f,
    val bands: List<Float> = List(10) { 0f },
)

/**
 * Il tema scelto dall'utente, sopra quello chiaro o scuro. Ogni colore è un
 * ARGB; null lascia quello del tema di base. Da questi pochi colori si ricavano
 * tutti gli altri (testo secondario, bordi, pallini…), così la tavolozza resta
 * coerente qualunque cosa si scelga.
 */
@Serializable
data class CustomTheme(
    val enabled: Boolean = false,
    val background: Long? = null,
    /** Il secondo colore dello sfondo: se c'è, lo sfondo è una sfumatura. */
    val background2: Long? = null,
    val panels: Long? = null,
    val ink: Long? = null,
    val accent: Long? = null,
    val dots: Boolean = true,
) {
    /**
     * Gli stessi colori scritti sempre allo stesso modo, come interi ARGB con
     * segno: 0xFF000000 e -16777216 sono lo stesso nero, e confrontando il
     * tema del PC con quello del telefono non devono sembrare diversi.
     */
    fun normalized(): CustomTheme {
        fun n(v: Long?) = v?.toInt()?.toLong()
        return copy(background = n(background), background2 = n(background2), panels = n(panels), ink = n(ink), accent = n(accent))
    }
}

/** Come si ordinano i brani nella schermata Brani. */
@Serializable
enum class SongSort { LIBRARY, TITLE, ARTIST, ALBUM, DURATION }

@Serializable
data class SettingsData(
    /** Vecchio campo a cartella singola: letto solo per migrare le impostazioni. */
    val libraryRoot: String? = null,
    val libraryRoots: List<String> = emptyList(),
    val dark: Boolean = true,
    val volume: Int = 80,
    /** Al telefono va la copia MP3 quando la libreria ne ha una. */
    val preferMp3OnPhone: Boolean = true,
    /** Dove finiscono i brani sul telefono. */
    val phoneFolder: String = DEFAULT_PHONE_FOLDER,
    /** I brani tolti dalla sincronizzazione: le spunte si ricordano. */
    val syncExcluded: Set<String> = emptySet(),
    val equalizer: EqualizerSettings = EqualizerSettings(),
    /** Il dispositivo d'uscita audio; null = quello predefinito di Windows. */
    val outputDevice: String? = null,
    val fullscreenBackground: FullscreenBackground = FullscreenBackground.WAVEFORM,
    /** Dove finiscono i brani scaricati; null = "Xaos" dentro la prima cartella della libreria. */
    val downloadFolder: String? = null,
    /** Dove finiscono i brani importati dal telefono; null = "Dal telefono" nella prima cartella. */
    val importFolder: String? = null,
    /** I brani del telefono da non importare: le spunte si ricordano anche qui. */
    val importExcluded: Set<String> = emptySet(),
    /** Ingrandimento di tutta l'interfaccia, testo compreso. */
    val uiScale: Float = 1.1f,
    val customTheme: CustomTheme = CustomTheme(),
    val songSort: SongSort = SongSort.TITLE,
    val songSortDescending: Boolean = false,
    /** Il "riduci a icona" della finestra la trasforma nel miniplayer. */
    val minimizeToMini: Boolean = false,
    /** Il miniplayer mostra il testo al posto della copertina. */
    val miniShowsLyrics: Boolean = false,
) {
    /** Le cartelle da scansionare, compresa quella del vecchio formato. */
    val roots: List<String> get() = libraryRoots.ifEmpty { listOfNotNull(libraryRoot) }

    val effectiveDownloadFolder: String?
        get() = downloadFolder ?: roots.firstOrNull()?.let { File(it, "Xaos").path }

    val effectiveImportFolder: String?
        get() = importFolder ?: roots.firstOrNull()?.let { File(it, "Dal telefono").path }

    companion object {
        const val DEFAULT_PHONE_FOLDER = "/sdcard/Music/Xaos"
    }
}

/**
 * Le preferenze dell'app, in un file JSON nella cartella dell'utente
 * (`~/.xaos`), accanto all'indice della libreria.
 */
class Settings(private val file: File) {

    // coerceInputValues: un valore che non esiste più (lo sfondo "animata",
    // per esempio) torna al predefinito invece di far perdere tutte le impostazioni.
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; coerceInputValues = true }

    private val _data = MutableStateFlow(read())
    val data: StateFlow<SettingsData> = _data.asStateFlow()

    /**
     * Cambia le impostazioni: l'app si aggiorna subito, il file si scrive poco
     * dopo, in background e una volta sola anche se le modifiche sono tante
     * (per esempio trascinando un colore nella tavolozza).
     */
    @Synchronized
    fun update(transform: (SettingsData) -> SettingsData) {
        val next = transform(_data.value)
        if (next == _data.value) return
        _data.value = next
        if (!writePending) {
            writePending = true
            writer.schedule({
                synchronized(this) { writePending = false }
                runCatching {
                    file.parentFile?.mkdirs()
                    val tmp = File(file.path + ".tmp")
                    tmp.writeText(json.encodeToString(_data.value))
                    java.nio.file.Files.move(
                        tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    )
                }
            }, 300, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    private var writePending = false
    private val writer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        // Non daemon: un salvataggio in coda deve finire anche se l'app si sta chiudendo.
        Thread(r, "xaos-settings")
    }

    /** Scrive subito quello che è in coda: da chiamare prima di uscire. */
    fun flush() {
        writer.shutdown()
        writer.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun setRoots(roots: List<String>) = update { it.copy(libraryRoots = roots.distinct(), libraryRoot = null) }

    private fun read(): SettingsData = runCatching {
        json.decodeFromString<SettingsData>(file.readText())
    }.getOrElse {
        // Primo avvio: la cartella Musica dell'utente, se c'è.
        val music = File(System.getProperty("user.home"), "Music")
        SettingsData(libraryRoots = listOfNotNull(music.takeIf { it.isDirectory }?.path))
    }

    companion object {
        /**
         * Dove stanno impostazioni, indice e dati dell'utente. XAOS_HOME serve
         * solo a chi sviluppa: un'istanza di prova con dati suoi, che non tocca
         * quelli veri né il telefono.
         */
        val appDir: File = System.getenv("XAOS_HOME")?.let(::File) ?: File(System.getProperty("user.home"), ".xaos")

        /** Un'istanza di prova (XAOS_HOME) non si collega al telefono. */
        val isTestInstance: Boolean = System.getenv("XAOS_HOME") != null
    }
}
