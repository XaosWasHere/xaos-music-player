package xaos.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SettingsData(
    val libraryRoot: String? = null,
    val dark: Boolean = true,
    val volume: Int = 80,
    /** Al telefono va la copia MP3 quando la libreria ne ha una. */
    val preferMp3OnPhone: Boolean = true,
)

/**
 * Le preferenze dell'app, in un file JSON nella cartella dell'utente
 * (`~/.xaos`), accanto all'indice della libreria.
 */
class Settings(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

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

    private fun read(): SettingsData = runCatching {
        json.decodeFromString<SettingsData>(file.readText())
    }.getOrElse {
        // Primo avvio: la cartella Musica dell'utente, se c'è.
        val music = File(System.getProperty("user.home"), "Music")
        SettingsData(libraryRoot = music.takeIf { it.isDirectory }?.path)
    }

    companion object {
        val appDir: File = File(System.getProperty("user.home"), ".xaos")
    }
}
