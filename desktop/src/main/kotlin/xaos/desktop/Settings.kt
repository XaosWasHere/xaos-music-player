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
    ANIMATED("ANIMATA"),
    STATIC("FISSA"),
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
    val fullscreenBackground: FullscreenBackground = FullscreenBackground.ANIMATED,
    /** Dove finiscono i brani scaricati; null = "Xaos" dentro la prima cartella della libreria. */
    val downloadFolder: String? = null,
    /** Dove finiscono i brani importati dal telefono; null = "Dal telefono" nella prima cartella. */
    val importFolder: String? = null,
    /** I brani del telefono da non importare: le spunte si ricordano anche qui. */
    val importExcluded: Set<String> = emptySet(),
    /** Ingrandimento di tutta l'interfaccia, testo compreso. */
    val uiScale: Float = 1.1f,
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

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private val _data = MutableStateFlow(read())
    val data: StateFlow<SettingsData> = _data.asStateFlow()

    fun update(transform: (SettingsData) -> SettingsData) {
        val next = transform(_data.value)
        _data.value = next
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(next))
        }
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
        val appDir: File = File(System.getProperty("user.home"), ".xaos")
    }
}
