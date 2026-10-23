@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import xaos.desktop.i18n.Language
import xaos.desktop.i18n.t
import xaos.desktop.i18n.tDot
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
    beam: xaos.desktop.beam.BeamServer,
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
        item { ScreenTitle(tDot("IMPOSTAZIONI"), caption = t("OGNI MODIFICA VALE SUBITO")) }

        item {
            SettingsCard(t("LIBRERIA")) {
                Text(
                    t("Le cartelle da cui Xaos legge la musica. Le sottocartelle sono comprese."),
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
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(t("AGGIUNGI CARTELLA"), onClick = onAddFolder, icon = XaosIcons.Add, filled = true)
                    PillButton(
                        t("RISCANSIONA"),
                        onClick = onRescan,
                        icon = XaosIcons.Sync,
                        enabled = scan !is ScanState.Scanning,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (val s = scan) {
                            is ScanState.Scanning -> t("SCANSIONE %d/%d", s.done, s.total)
                            is ScanState.Failed -> t("ERRORE: %s", s.message)
                            ScanState.Idle -> t("[%d] BRANI · [%d] ALBUM", snapshot.tracks.size, snapshot.albums.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Xaos.colors.inkTertiary,
                    )
                }
                if (lrcCount > 0) {
                    Hairline()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(t("[%d] TESTI IN FILE .LRC", lrcCount), style = MaterialTheme.typography.labelLarge, color = Xaos.colors.ink)
                            Text(
                                t("Il telefono legge solo i testi dentro i file: incorporali per vederli anche lì."),
                                style = MaterialTheme.typography.labelSmall,
                                color = Xaos.colors.inkTertiary,
                            )
                        }
                        val status = embedding
                        if (status != null) {
                            Text(status, style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkSecondary)
                        }
                        PillButton(
                            t("INCORPORA I TESTI"),
                            onClick = {
                                scope.launch {
                                    val n = xaos.desktop.library.TagEditor.embedSidecarLyrics(snapshot.tracks) { done, total ->
                                        embedding = "$done/$total"
                                    }
                                    // Deve cominciare con "[" in ogni lingua: è il segno che ha finito.
                                    embedding = t("[%d] AGGIORNATI", n)
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

        item { LyricsCard(prefs, settings) }

        item {
            SettingsCard(t("DOWNLOAD")) {
                Text(
                    t("I brani scaricati dalla ricerca in rete, in MP3 con copertina e metadati."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.inkSecondary,
                )
                prefs.effectiveDownloadFolder?.let { folder ->
                    FolderRow(path = folder, canRemove = false, onRemove = {})
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(t("CAMBIA CARTELLA"), onClick = onPickDownloadFolder, icon = XaosIcons.Folder)
                    if (prefs.downloadFolder != null) {
                        PillButton(t("PREDEFINITA"), onClick = { settings.update { it.copy(downloadFolder = null) } })
                    }
                }
                val (ytExe, ffExe) = ytdlp.tools.collectAsState().value
                InfoLine("YT-DLP", ytExe?.path ?: t("ricerca in corso…"))
                InfoLine("FFMPEG", ffExe?.path ?: t("ricerca in corso…"))
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
            SettingsCard(t("SCHERMO INTERO")) {
                Text(
                    t("Lo sfondo del player a schermo intero. Si cambia anche da lì, con il bottone in alto."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.inkSecondary,
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        item { BeamCard(prefs, settings, beam) }

        item {
            SettingsCard(t("ASPETTO")) {
                Text(t("TEMA"), style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkSecondary)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(t("SCURO"), onClick = { settings.update { it.copy(dark = true) } }, filled = prefs.dark)
                    PillButton(t("CHIARO"), onClick = { settings.update { it.copy(dark = false) } }, filled = !prefs.dark)
                }
                // I nomi delle lingue restano sempre nella lingua stessa.
                Text(t("LINGUA"), style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkSecondary)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(t("AUTOMATICA"), onClick = { settings.update { it.copy(language = null) } }, filled = prefs.language == null)
                    Language.entries.forEach { lang ->
                        PillButton(
                            lang.nativeName,
                            onClick = { settings.update { it.copy(language = lang.code) } },
                            filled = prefs.language == lang.code,
                        )
                    }
                }
                Text(
                    t("La scelta automatica segue la lingua di Windows."),
                    style = MaterialTheme.typography.labelSmall,
                    color = Xaos.colors.inkTertiary,
                )
                Text(t("DIMENSIONE INTERFACCIA"), style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkSecondary)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0f to "100%", 1.1f to "110%", 1.25f to "125%").forEach { (scale, label) ->
                        PillButton(
                            label,
                            onClick = { settings.update { it.copy(uiScale = scale) } },
                            filled = kotlin.math.abs(prefs.uiScale - scale) < 0.01f,
                        )
                    }
                }
                Hairline()
                ThemeEditor(prefs, settings)
            }
        }

        item {
            SettingsCard(t("FINESTRA")) {
                Text(t("RIDUCI A ICONA"), style = MaterialTheme.typography.labelMedium, color = Xaos.colors.inkSecondary)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(t("NELLA BARRA DELLE APPLICAZIONI"), onClick = { settings.update { it.copy(minimizeToMini = false) } }, filled = !prefs.minimizeToMini)
                    PillButton(t("DIVENTA MINIPLAYER"), onClick = { settings.update { it.copy(minimizeToMini = true) } }, filled = prefs.minimizeToMini)
                }
                Text(
                    if (prefs.minimizeToMini) t("Riducendo Xaos a icona, la finestra diventa il miniplayer: resta in primo piano con copertina o testo e i comandi. Un doppio clic lo riporta a finestra intera.")
                    else t("Riducendo Xaos a icona, finisce nella barra delle applicazioni come ogni programma. Il miniplayer si apre dal suo pulsante nella barra del player."),
                    style = MaterialTheme.typography.labelSmall,
                    color = Xaos.colors.inkTertiary,
                )
            }
        }

        item {
            SettingsCard(t("INFORMAZIONI")) {
                InfoLine(t("VERSIONE"), "Xaos desktop " + (System.getProperty("xaos.version") ?: t("in sviluppo")))
                InfoLine("VLC", player.vlcVersion?.let { v -> "$v — ${player.vlcPath.orEmpty()}" } ?: t("non ancora caricato"))
                InfoLine("ADB", phone.adbPath ?: t("non trovato — serve per la sincronizzazione"))
                InfoLine(t("DATI"), Settings.appDir.path)
                InfoLine(t("PROGETTO"), "github.com/XaosWasHere/xaos-music-player")
            }
        }
    }
}

/** I testi cercati da soli su LRCLIB per tutta la libreria, all'avvio. */
@Composable
private fun LyricsCard(prefs: SettingsData, settings: Settings) {
    val scanner = xaos.desktop.online.LocalLibraryLyrics.current
    val progress = scanner?.progress?.collectAsState()?.value
    SettingsCard(t("TESTI")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t("All'avvio, i brani senza testo lo cercano su LRCLIB. Si aggiunge al file solo se la durata coincide: è il modo di essere certi che sia la stessa incisione. Se non coincide, non si tocca niente."),
                style = MaterialTheme.typography.bodyMedium,
                color = Xaos.colors.inkSecondary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            XaosSwitch(prefs.autoLyrics, onChange = { on -> settings.update { it.copy(autoLyrics = on) } })
        }
        if (prefs.autoLyrics && progress != null && progress.total > 0) {
            Text(
                when {
                    progress.offline -> t("LRCLIB NON RISPONDE: SI RIPROVA AL PROSSIMO AVVIO · [%d] AGGIUNTI", progress.added)
                    progress.running -> t("RICERCA [%d/%d] · [%d] AGGIUNTI", progress.done, progress.total, progress.added)
                    else -> t("[%d] BRANI CERCATI · [%d] TESTI AGGIUNTI", progress.total, progress.added)
                },
                style = MaterialTheme.typography.labelMedium,
                color = Xaos.colors.inkTertiary,
            )
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
            title = { Text(t("Spostare %d file nel cestino?", files.size), style = MaterialTheme.typography.titleLarge, color = colors.ink) },
            text = {
                Text(
                    t("Sono copie identiche per formato di brani che restano in libreria (%s in tutto). Finiscono nel Cestino di Windows, da cui puoi recuperarli. Preferiti e playlist passano da soli alla copia che resta.", formatBytes(bytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                PillButton(t("SPOSTA NEL CESTINO"), filled = true, onClick = {
                    confirm = false
                    val desktop = java.awt.Desktop.getDesktop()
                    val moved = files.count { runCatching { desktop.moveToTrash(it) }.getOrDefault(false) }
                    result = if (moved < files.size) t("[%d] SPOSTATI NEL CESTINO · [%d] NON RIUSCITI", moved, files.size - moved)
                    else t("[%d] SPOSTATI NEL CESTINO", moved)
                    onRescan()
                })
            },
            dismissButton = { PillButton(t("ANNULLA"), onClick = { confirm = false }) },
        )
    }

    SettingsCard(t("DOPPIONI")) {
        Text(
            t("Lo stesso brano dello stesso album, nello stesso formato, in più file: di solito copie tornate dal telefono. Le versioni in formati diversi (FLAC, MP3, M4A) non sono doppioni e qui non compaiono. In libreria il brano si vede una volta sola; le copie restano sul disco finché non decidi tu."),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSecondary,
        )
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(if (open) t("NASCONDI ELENCO") else t("MOSTRA ELENCO"), onClick = { open = !open })
            if (trashSupported && files.isNotEmpty()) {
                PillButton(t("SPOSTA LE COPIE NEL CESTINO"), onClick = { confirm = true }, icon = XaosIcons.Delete)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                result ?: t("[%d] BRANI · [%d] FILE IN PIÙ · %s", groups.size, files.size, formatBytes(bytes)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkTertiary,
            )
        }
        if (open) {
            groups.forEach { g ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${g.kept.title} · ${g.kept.artist}", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                    Text(t("TIENE  %s", g.kept.path), style = MaterialTheme.typography.labelSmall, color = colors.inkSecondary)
                    g.copies.forEach { c ->
                        Text(t("COPIA  %s", c.path), style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
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

/** Un dispositivo audio: una riga intera, col pallino d'accento su quello in uso. */
@Composable
private fun DeviceRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Xaos.colors
    Row(
        Modifier.fillMaxWidth().hoverRow(selected).pressable(onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp)) {
            if (selected) AccentDot(size = 8.dp) else AccentDot(size = 8.dp, color = colors.track)
        }
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.ink else colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                if (exists) path else t("%s — NON TROVATA", path),
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Almeno una cartella deve restare: senza, la libreria sarebbe vuota
        // e non ci sarebbe da dove ripartire.
        if (canRemove) CircleIconButton(XaosIcons.Close, t("Togli cartella"), onRemove, size = 30.dp)
    }
}

@Composable
private fun OutputCard(prefs: SettingsData, player: Player, onSelect: (String?) -> Unit) {
    var devices by remember { mutableStateOf(player.outputDevices()) }
    SettingsCard(t("USCITA AUDIO")) {
        Text(
            t("Dove suona Xaos. \"Predefinita\" segue l'uscita scelta in Windows."),
            style = MaterialTheme.typography.bodyMedium,
            color = Xaos.colors.inkSecondary,
        )
        // Un elenco, non una fila: i nomi dei dispositivi sono lunghi e in fila
        // uscivano dalla card.
        Column(Modifier.fillMaxWidth()) {
            DeviceRow(t("Predefinita di Windows"), selected = prefs.outputDevice == null) {
                onSelect(null); player.setOutputDevice(null)
            }
            devices.forEach { (id, name) ->
                DeviceRow(name, selected = prefs.outputDevice == id) { onSelect(id); player.setOutputDevice(id) }
            }
        }
        PillButton(t("AGGIORNA ELENCO"), onClick = { devices = player.outputDevices() }, icon = XaosIcons.Sync)
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
    SettingsCard(t("EQUALIZZATORE")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (eq.enabled) t("ATTIVO") else t("SPENTO"),
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            XaosSwitch(eq.enabled, onChange = { onChange(eq.copy(enabled = it)) })
        }

        if (player.eqPresets.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text(t("PREAMP"), style = MaterialTheme.typography.labelLarge, color = colors.ink, modifier = Modifier.width(90.dp))
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
                t("AZZERA"),
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
    SettingsCard(t("TELEFONO")) {
        Text(t("CARTELLA DI DESTINAZIONE"), style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
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
                t("APPLICA"),
                onClick = { settings.update { it.copy(phoneFolder = folder.trim().trimEnd('/').ifEmpty { SettingsData.DEFAULT_PHONE_FOLDER }) } },
                filled = folder.trim() != prefs.phoneFolder,
                enabled = folder.isNotBlank(),
            )
            PillButton(t("PREDEFINITA"), onClick = {
                folder = SettingsData.DEFAULT_PHONE_FOLDER
                settings.update { it.copy(phoneFolder = SettingsData.DEFAULT_PHONE_FOLDER) }
            })
        }
        Text(
            t("È dove Xaos per Android salva anche i download: così i brani del PC finiscono nella sua libreria."),
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkTertiary,
        )

        Spacer(Modifier.height(4.dp))
        Text(t("IMPORTATI DAL TELEFONO, SUL PC IN"), style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                prefs.effectiveImportFolder ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PillButton(t("CAMBIA"), onClick = onPickImportFolder, icon = XaosIcons.Folder)
            if (prefs.importFolder != null) {
                PillButton(t("PREDEFINITA"), onClick = { settings.update { it.copy(importFolder = null) } })
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(t("VERSIONE DA INVIARE"), style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(t("MP3 QUANDO C'È"), onClick = { settings.update { it.copy(preferMp3OnPhone = true) } }, filled = prefs.preferMp3OnPhone)
            PillButton(t("ORIGINALE"), onClick = { settings.update { it.copy(preferMp3OnPhone = false) } }, filled = !prefs.preferMp3OnPhone)
            Spacer(Modifier.width(8.dp))
            Text(
                if (prefs.preferMp3OnPhone) t("Se un brano ha anche la copia MP3, va quella: pesa circa un quinto del FLAC.")
                else t("Sempre il file originale: qualità piena, molto più spazio."),
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
            )
        }

        if (prefs.syncExcluded.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    t("[%d] BRANI ESCLUSI DALLA SINCRONIZZAZIONE", prefs.syncExcluded.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkSecondary,
                )
                PillButton(t("RIPRISTINA TUTTI"), onClick = { settings.update { it.copy(syncExcluded = emptySet()) } })
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

/**
 * Xaos Beam: la libreria del PC ascoltata dal telefono, in streaming sulla
 * rete di casa. Qui si accende, si legge il PIN per abbinare un telefono e si
 * tolgono quelli che non devono più entrare.
 */
@Composable
private fun BeamCard(prefs: SettingsData, settings: Settings, beam: xaos.desktop.beam.BeamServer) {
    val colors = Xaos.colors
    val state by beam.state.collectAsState()
    val pin by beam.pin.collectAsState()
    val active by beam.active.collectAsState()
    // L'elenco "in ascolto ora" si rinfresca da solo anche senza richieste nuove.
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { kotlinx.coroutines.delay(5_000); value = System.currentTimeMillis() }
    }
    SettingsCard("XAOS BEAM") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (val s = state) {
                        is xaos.desktop.beam.BeamState.Running -> t("ATTIVO")
                        is xaos.desktop.beam.BeamState.Failed -> t("NON PARTITO: %s", s.message)
                        xaos.desktop.beam.BeamState.Off -> t("SPENTO")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                )
                Text(
                    t("Ascolta la libreria del PC dal telefono, in streaming sulla rete di casa, senza copiarla."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            }
            XaosSwitch(prefs.beamEnabled, onChange = { on -> settings.update { it.copy(beamEnabled = on) } })
        }
        val running = state as? xaos.desktop.beam.BeamState.Running
        if (running != null) {
            Hairline()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("PIN PER ABBINARE UN TELEFONO"), style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
                    xaos.desktop.theme.DotText(pin, color = colors.ink, pitch = 4.dp)
                    Text(
                        t("Sul telefono: Impostazioni → Xaos Beam → Dispositivi, tocca questo PC e scrivi il PIN. Dopo l'abbinamento il PIN cambia."),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkTertiary,
                    )
                }
                PillButton(t("NUOVO PIN"), onClick = beam::newPin, icon = XaosIcons.Sync)
            }
            InfoLine(t("INDIRIZZO"), running.addresses.ifEmpty { listOf("—") }.joinToString("  ·  ") { "$it:${running.port}" })
        }
        if (prefs.beamDevices.isNotEmpty()) {
            Hairline()
            Text(t("TELEFONI ABBINATI"), style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
            Column(Modifier.fillMaxWidth()) {
                prefs.beamDevices.forEach { device ->
                    val listening = active[device.name]?.let { now - it < 45_000 } == true
                    Row(
                        Modifier.fillMaxWidth().hoverRow().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(20.dp)) {
                            AccentDot(size = 8.dp, color = if (listening) colors.accent else colors.track)
                        }
                        Icon(XaosIcons.Phone, null, tint = colors.ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (listening) t("COLLEGATO ORA") else t("ABBINATO"),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkTertiary,
                            )
                        }
                        PillButton(t("TOGLI"), onClick = { beam.forget(device) })
                    }
                }
            }
        }
        Text(
            t("Il PC e il telefono devono stare sulla stessa rete Wi-Fi. La prima volta Windows chiede se Xaos può usare la rete: consenti le reti private."),
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkTertiary,
        )
    }
}
