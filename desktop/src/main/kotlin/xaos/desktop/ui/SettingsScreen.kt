package xaos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xaos.desktop.EqualizerSettings
import xaos.desktop.FullscreenBackground
import xaos.desktop.Settings
import xaos.desktop.SettingsData
import xaos.desktop.library.Library
import xaos.desktop.library.ScanState
import xaos.desktop.library.hasLyricsFile
import xaos.desktop.player.Player
import xaos.desktop.sync.PhoneSync
import xaos.desktop.online.YtDlp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import xaos.desktop.theme.Xaos
import java.io.File
import java.util.Locale

/**
 * Le impostazioni, divise per argomento in card. Ogni modifica vale subito e
 * si salva da sola: non c'è un "salva" da ricordarsi di premere.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    library: Library,
    player: Player,
    phone: PhoneSync,
    ytdlp: YtDlp,
    onAddFolder: () -> Unit,
    onRescan: () -> Unit,
    onPickDownloadFolder: () -> Unit,
    onPickImportFolder: () -> Unit,
) {
    val prefs by settings.data.collectAsState()
    val scan by library.scan.collectAsState()
    val snapshot by library.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var embedding by remember { mutableStateOf<String?>(null) }
    val lrcCount = remember(snapshot) { snapshot.tracks.count { it.hasLyricsFile() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 40.dp)) {
        item { ScreenTitle("IMPOSTAZIONI", caption = "OGNI MODIFICA VALE SUBITO") }

        item {
            SettingsCard("LIBRERIA") {
                Text(
                    "Le cartelle da cui Xaos legge la musica. Le sottocartelle sono comprese.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.inkSecondary,
                )
                prefs.roots.forEach { root ->
                    FolderRow(
                        path = root,
                        canRemove = prefs.roots.size > 1,
                        onRemove = { settings.setRoots(prefs.roots - root) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("AGGIUNGI CARTELLA", onClick = onAddFolder, icon = XaosIcons.Add, filled = true)
                    PillButton(
                        "RISCANSIONA",
                        onClick = onRescan,
                        icon = XaosIcons.Sync,
                        enabled = scan !is ScanState.Scanning,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (val s = scan) {
                            is ScanState.Scanning -> "SCANSIONE ${s.done}/${s.total}"
                            is ScanState.Failed -> "ERRORE: ${s.message}"
                            ScanState.Idle -> "[${snapshot.tracks.size}] BRANI · [${snapshot.albums.size}] ALBUM"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Xaos.colors.inkTertiary,
                    )
                }
                if (lrcCount > 0) {
                    Hairline()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("[$lrcCount] TESTI IN FILE .LRC", style = MaterialTheme.typography.labelLarge, color = Xaos.colors.ink)
                            Text(
                                "Il telefono legge solo i testi dentro i file: incorporali per vederli anche lì.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Xaos.colors.inkTertiary,
                            )
                        }
                        val status = embedding
                        if (status != null) {
                            Text(status, style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkSecondary)
                        }
                        PillButton(
                            "INCORPORA I TESTI",
                            onClick = {
                                scope.launch {
                                    val n = xaos.desktop.library.TagEditor.embedSidecarLyrics(snapshot.tracks) { done, total ->
                                        embedding = "$done/$total"
                                    }
                                    embedding = "[$n] AGGIORNATI"
                                    onRescan()
                                }
                            },
                            icon = XaosIcons.Download,
                            enabled = embedding == null || embedding!!.startsWith("["),
                        )
                    }
                }
            }
        }

        if (snapshot.duplicates.isNotEmpty()) {
            item { DuplicatesCard(snapshot.duplicates, onRescan) }
        }

        item {
            SettingsCard("DOWNLOAD") {
                Text(
                    "I brani scaricati dalla ricerca in rete, in MP3 con copertina e metadati.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.inkSecondary,
                )
                prefs.effectiveDownloadFolder?.let { folder ->
                    FolderRow(path = folder, canRemove = false, onRemove = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("CAMBIA CARTELLA", onClick = onPickDownloadFolder, icon = XaosIcons.Folder)
                    if (prefs.downloadFolder != null) {
                        PillButton("PREDEFINITA", onClick = { settings.update { it.copy(downloadFolder = null) } })
                    }
                }
                InfoLine("YT-DLP", ytdlp.exe?.path ?: "non trovato — winget install yt-dlp.yt-dlp")
                InfoLine("FFMPEG", ytdlp.ffmpeg?.path ?: "non trovato — winget install Gyan.FFmpeg")
            }
        }

        item { OutputCard(prefs, player) { id -> settings.update { it.copy(outputDevice = id) } } }

        item {
            EqualizerCard(
                eq = prefs.equalizer,
                player = player,
                onChange = { eq -> settings.update { it.copy(equalizer = eq) } },
            )
        }

        item {
            SettingsCard("SCHERMO INTERO") {
                Text(
                    "Lo sfondo del player a schermo intero. Si cambia anche da lì, con il bottone in alto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.inkSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FullscreenBackground.entries.forEach { mode ->
                        PillButton(
                            mode.label,
                            onClick = { settings.update { it.copy(fullscreenBackground = mode) } },
                            filled = prefs.fullscreenBackground == mode,
                        )
                    }
                }
            }
        }

        item { PhoneCard(prefs, settings, onPickImportFolder) }

        item {
            SettingsCard("ASPETTO") {
                Text("TEMA", style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("SCURO", onClick = { settings.update { it.copy(dark = true) } }, filled = prefs.dark)
                    PillButton("CHIARO", onClick = { settings.update { it.copy(dark = false) } }, filled = !prefs.dark)
                }
                Text("DIMENSIONE INTERFACCIA", style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0f to "100%", 1.1f to "110%", 1.25f to "125%").forEach { (scale, label) ->
                        PillButton(
                            label,
                            onClick = { settings.update { it.copy(uiScale = scale) } },
                            filled = kotlin.math.abs(prefs.uiScale - scale) < 0.01f,
                        )
                    }
                }
            }
        }

        item {
            SettingsCard("INFORMAZIONI") {
                InfoLine("VERSIONE", "Xaos desktop " + (System.getProperty("xaos.version") ?: "in sviluppo"))
                InfoLine("VLC", player.vlcVersion?.let { v -> "$v — ${player.vlcPath.orEmpty()}" } ?: "non ancora caricato")
                InfoLine("ADB", phone.adbPath ?: "non trovato — serve per la sincronizzazione")
                InfoLine("DATI", Settings.appDir.path)
                InfoLine("PROGETTO", "github.com/XaosWasHere/xaos-music-player")
            }
        }
    }
}

/**
 * I file che la libreria ha riconosciuto come copie di un altro brano dello
 * stesso album e che quindi non mostra. Restano sul disco finché l'utente non
 * decide: spostarli nel cestino è reversibile, cancellarli no.
 */
@Composable
private fun DuplicatesCard(groups: List<xaos.desktop.library.DuplicateGroup>, onRescan: () -> Unit) {
    val colors = Xaos.colors
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val files = remember(groups) { groups.flatMap { g -> g.copies.flatMap { it.allPaths } }.distinct().map { java.io.File(it) }.filter { it.isFile } }
    val bytes = remember(files) { files.sumOf { it.length() } }
    val trashSupported = remember {
        runCatching { java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH) }.getOrDefault(false)
    }

    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = colors.surface,
            title = { Text("Spostare ${files.size} file nel cestino?", style = MaterialTheme.typography.titleLarge, color = colors.ink) },
            text = {
                Text(
                    "Sono copie identiche per formato di brani che restano in libreria (${formatBytes(bytes)} in tutto). " +
                        "Finiscono nel Cestino di Windows, da cui puoi recuperarli. Preferiti e playlist passano da soli alla copia che resta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                PillButton("SPOSTA NEL CESTINO", filled = true, onClick = {
                    confirm = false
                    val desktop = java.awt.Desktop.getDesktop()
                    val moved = files.count { runCatching { desktop.moveToTrash(it) }.getOrDefault(false) }
                    result = "[$moved] SPOSTATI NEL CESTINO" + if (moved < files.size) " · [${files.size - moved}] NON RIUSCITI" else ""
                    onRescan()
                })
            },
            dismissButton = { PillButton("ANNULLA", onClick = { confirm = false }) },
        )
    }

    SettingsCard("DOPPIONI") {
        Text(
            "Lo stesso brano dello stesso album, nello stesso formato, in più file: di solito copie tornate dal telefono. " +
                "Le versioni in formati diversi (FLAC, MP3, M4A) non sono doppioni e qui non compaiono. " +
                "In libreria il brano si vede una volta sola; le copie restano sul disco finché non decidi tu.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (open) "NASCONDI ELENCO" else "MOSTRA ELENCO", onClick = { open = !open })
            if (trashSupported && files.isNotEmpty()) {
                PillButton("SPOSTA LE COPIE NEL CESTINO", onClick = { confirm = true }, icon = XaosIcons.Delete)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                result ?: "[${groups.size}] BRANI · [${files.size}] FILE IN PIÙ · ${formatBytes(bytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkTertiary,
            )
        }
        if (open) {
            groups.forEach { g ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${g.kept.title} · ${g.kept.artist}", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                    Text("TIENE  " + g.kept.path, style = MaterialTheme.typography.labelSmall, color = colors.inkSecondary)
                    g.copies.forEach { c ->
                        Text("COPIA  " + c.path, style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 920.dp)
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .nothingCard()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader(title)
        content()
    }
}

@Composable
private fun FolderRow(path: String, canRemove: Boolean, onRemove: () -> Unit) {
    val colors = Xaos.colors
    val exists = remember(path) { File(path).isDirectory }
    Row(
        Modifier.fillMaxWidth().hoverRow().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XaosIcons.Folder, null, tint = if (exists) colors.ink else colors.inkTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(File(path).name.uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (exists) path else "$path — NON TROVATA",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Almeno una cartella deve restare: senza, la libreria sarebbe vuota
        // e non ci sarebbe da dove ripartire.
        if (canRemove) CircleIconButton(XaosIcons.Close, "Togli cartella", onRemove, size = 30.dp)
    }
}

@Composable
private fun OutputCard(prefs: SettingsData, player: Player, onSelect: (String?) -> Unit) {
    var devices by remember { mutableStateOf(player.outputDevices()) }
    SettingsCard("USCITA AUDIO") {
        Text(
            "Dove suona Xaos. \"Predefinita\" segue l'uscita scelta in Windows.",
            style = MaterialTheme.typography.bodyMedium,
            color = Xaos.colors.inkSecondary,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("PREDEFINITA", onClick = { onSelect(null); player.setOutputDevice(null) }, filled = prefs.outputDevice == null)
            devices.forEach { (id, name) ->
                PillButton(
                    name.uppercase(),
                    onClick = { onSelect(id); player.setOutputDevice(id) },
                    filled = prefs.outputDevice == id,
                )
            }
            PillButton("AGGIORNA ELENCO", onClick = { devices = player.outputDevices() }, icon = XaosIcons.Sync)
        }
    }
}

/**
 * L'equalizzatore di VLC: dieci bande da -20 a +20 dB, un preamplificatore,
 * i preset di VLC come punto di partenza. Toccare una banda a mano rende il
 * preset "personalizzato".
 */
@Composable
private fun EqualizerCard(eq: EqualizerSettings, player: Player, onChange: (EqualizerSettings) -> Unit) {
    val colors = Xaos.colors
    val frequencies = player.eqBands.ifEmpty { listOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f) }
    SettingsCard("EQUALIZZATORE") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (eq.enabled) "ATTIVO" else "SPENTO",
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            XaosSwitch(eq.enabled, onChange = { onChange(eq.copy(enabled = it)) })
        }

        if (player.eqPresets.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                player.eqPresets.forEach { name ->
                    PillButton(
                        name.uppercase(),
                        onClick = {
                            player.presetValues(name)?.let { (pre, bands) ->
                                onChange(eq.copy(enabled = true, preset = name, preamp = pre, bands = bands))
                            }
                        },
                        filled = eq.preset == name,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(230.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            frequencies.forEachIndexed { i, hz ->
                val value = eq.bands.getOrElse(i) { 0f }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        String.format(Locale.US, "%+.0f", value),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (value == 0f) colors.inkTertiary else colors.ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    VerticalDotSlider(
                        value = value,
                        enabled = eq.enabled,
                        onChange = { v ->
                            val bands = eq.bands.toMutableList().apply {
                                while (size < frequencies.size) add(0f)
                                this[i] = v
                            }
                            onChange(eq.copy(bands = bands, preset = null))
                        },
                        modifier = Modifier.width(28.dp).weight(1f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(formatHz(hz), style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PREAMP", style = MaterialTheme.typography.labelLarge, color = colors.ink, modifier = Modifier.width(90.dp))
            DotSlider(
                value = (eq.preamp + 20f) / 40f,
                modifier = Modifier.weight(1f),
                color = if (eq.enabled) colors.accent else colors.inkTertiary,
                onChange = { f -> onChange(eq.copy(preamp = kotlin.math.round(f * 40f - 20f), preset = null)) },
            )
            Text(
                String.format(Locale.US, "%+.0f dB", eq.preamp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
                modifier = Modifier.width(70.dp).padding(start = 12.dp),
            )
            PillButton(
                "AZZERA",
                onClick = { onChange(eq.copy(preset = null, preamp = 0f, bands = List(frequencies.size) { 0f })) },
            )
        }
    }
}

private fun formatHz(hz: Float): String =
    if (hz >= 1000f) String.format(Locale.US, "%.0fK", hz / 1000f) else String.format(Locale.US, "%.0f", hz)

@Composable
private fun PhoneCard(prefs: SettingsData, settings: Settings, onPickImportFolder: () -> Unit) {
    val colors = Xaos.colors
    var folder by remember(prefs.phoneFolder) { mutableStateOf(prefs.phoneFolder) }
    SettingsCard("TELEFONO") {
        Text("CARTELLA DI DESTINAZIONE", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(colors.card, shape)
                    .border(1.dp, colors.line, shape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accentInk),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PillButton(
                "APPLICA",
                onClick = { settings.update { it.copy(phoneFolder = folder.trim().trimEnd('/').ifEmpty { SettingsData.DEFAULT_PHONE_FOLDER }) } },
                filled = folder.trim() != prefs.phoneFolder,
                enabled = folder.isNotBlank(),
            )
            PillButton("PREDEFINITA", onClick = {
                folder = SettingsData.DEFAULT_PHONE_FOLDER
                settings.update { it.copy(phoneFolder = SettingsData.DEFAULT_PHONE_FOLDER) }
            })
        }
        Text(
            "È dove Xaos per Android salva anche i download: così i brani del PC finiscono nella sua libreria.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkTertiary,
        )

        Spacer(Modifier.height(4.dp))
        Text("IMPORTATI DAL TELEFONO, SUL PC IN", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                prefs.effectiveImportFolder ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PillButton("CAMBIA", onClick = onPickImportFolder, icon = XaosIcons.Folder)
            if (prefs.importFolder != null) {
                PillButton("PREDEFINITA", onClick = { settings.update { it.copy(importFolder = null) } })
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("VERSIONE DA INVIARE", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("MP3 QUANDO C'È", onClick = { settings.update { it.copy(preferMp3OnPhone = true) } }, filled = prefs.preferMp3OnPhone)
            PillButton("ORIGINALE", onClick = { settings.update { it.copy(preferMp3OnPhone = false) } }, filled = !prefs.preferMp3OnPhone)
            Spacer(Modifier.width(8.dp))
            Text(
                if (prefs.preferMp3OnPhone) "Se un brano ha anche la copia MP3, va quella: pesa circa un quinto del FLAC."
                else "Sempre il file originale: qualità piena, molto più spazio.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
            )
        }

        if (prefs.syncExcluded.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "[${prefs.syncExcluded.size}] BRANI ESCLUSI DALLA SINCRONIZZAZIONE",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkSecondary,
                )
                PillButton("RIPRISTINA TUTTI", onClick = { settings.update { it.copy(syncExcluded = emptySet()) } })
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = Xaos.colors
    Row {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
    }
}
