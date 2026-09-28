package xaos.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xaos.desktop.library.LibrarySnapshot
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xaos.desktop.sync.AppInstall
import xaos.desktop.sync.PhoneState
import xaos.desktop.sync.PhoneSync
import xaos.desktop.sync.SyncItem
import xaos.desktop.sync.LyricsCheck
import xaos.desktop.sync.SyncPlan
import xaos.desktop.library.Library
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.rotate
import xaos.desktop.sync.SyncStatus
import xaos.desktop.theme.DotText
import xaos.desktop.theme.Xaos
import java.util.Locale

@Composable
fun PhoneScreen(
    phone: PhoneSync,
    snapshot: LibrarySnapshot,
    preferMp3: Boolean,
    phoneFolder: String,
    excluded: Set<String>,
    onExcludedChange: (Set<String>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by phone.state.collectAsState()
    val plan by phone.plan.collectAsState()
    val status by phone.status.collectAsState()
    val install by phone.install.collectAsState()
    var confirmInstall by remember { mutableStateOf(false) }
    val roots = snapshot.roots
    val expanded = remember { mutableStateListOf<String>() }

    // Appena il telefono c'è e la libreria è letta, si calcola cosa manca:
    // collegarlo deve bastare a vedere la situazione.
    LaunchedEffect(state, snapshot.tracks.size, preferMp3, phoneFolder) {
        if (state is PhoneState.Connected && roots.isNotEmpty() && snapshot.tracks.isNotEmpty()) {
            phone.plan(roots, snapshot.tracks, preferMp3)
        }
    }

    val pending = plan?.missing.orEmpty()
    val selected = remember(pending, excluded) { pending.filter { it.track.path !in excluded } }

    val connected = state as? PhoneState.Connected
    if (confirmInstall && connected != null) {
        ConfirmInstallDialog(
            phoneName = connected.name,
            isUpdate = connected.appInstalled,
            fromVersion = connected.appVersionName,
            toVersion = phone.bundledVersion?.second,
            apkSize = phone.bundledApk?.length(),
            onConfirm = { confirmInstall = false; phone.installApp() },
            onDismiss = { confirmInstall = false },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 28.dp)) {
        item {
            ScreenTitle(
                "TELEFONO",
                caption = "INVIO VIA USB · SOLO AGGIUNTE, NIENTE VIENE CANCELLATO",
            )
        }
        item {
            when (val s = state) {
                PhoneState.NoAdb -> InfoCard(
                    "ADB NON TROVATO",
                    "Serve l'utilità adb di Android (platform-tools). Installala con Android Studio " +
                        "o scaricala da developer.android.com, poi riavvia Xaos.",
                )
                PhoneState.Disconnected -> InfoCard(
                    "COLLEGA IL TELEFONO",
                    "Collegalo con il cavo USB e tieni attivo il Debug USB nelle Opzioni sviluppatore. " +
                        "Xaos lo riconosce da solo in un paio di secondi.",
                )
                is PhoneState.Unauthorized -> InfoCard(
                    "CONFERMA SUL TELEFONO",
                    "Sul telefono è comparso \"Consentire il debug USB?\": tocca Consenti " +
                        "(e \"Consenti sempre da questo computer\", così non lo chiede più).",
                )
                is PhoneState.Connected -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppCard(
                        phone = s,
                        install = install,
                        canInstall = phone.canInstallOn(s),
                        updateAvailable = phone.updateAvailable(s),
                        bundledVersionName = phone.bundledVersion?.second,
                        apkSize = phone.bundledApk?.length(),
                        onInstall = { confirmInstall = true },
                        onLaunch = phone::launchApp,
                        onDismiss = phone::dismissInstall,
                    )
                    ConnectedPanel(
                        state = s,
                        remoteRoot = phone.remoteRoot,
                        formatLabel = if (preferMp3) "MP3 QUANDO C'È" else "ORIGINALE",
                        plan = plan,
                        selected = selected,
                        status = status,
                        onSync = { phone.sync(roots, snapshot.tracks, preferMp3, selected) },
                        onCancel = phone::cancel,
                        onRefresh = { phone.plan(roots, snapshot.tracks, preferMp3) },
                        onDismiss = phone::dismissResult,
                        onOpenSettings = onOpenSettings,
                    )
                    LyricsPanel(phone)
                }
            }
        }

        if (state is PhoneState.Connected && pending.isNotEmpty() && status !is SyncStatus.Running) {
            val byAlbum = pending
                .groupBy { (Library.collapsedParent(it.track.file)?.path ?: "") + "|" + it.track.album }
                .entries
                .sortedBy { it.value.first().track.album.lowercase() }
            val allPaths = pending.map { it.track.path }.toSet()

            item {
                SelectAllRow(
                    state = checkOf(selected.size, pending.size),
                    selectedCount = selected.size,
                    total = pending.size,
                    selectedBytes = selected.sumOf { it.size },
                    onToggle = {
                        onExcludedChange(
                            if (selected.size == pending.size) excluded + allPaths else excluded - allPaths
                        )
                    },
                )
            }
            byAlbum.forEach { (key, entries) ->
                val paths = entries.map { it.track.path }.toSet()
                val chosen = entries.count { it.track.path !in excluded }
                val isOpen = key in expanded
                item(key = key) {
                    AlbumSelectRow(
                        entries = entries,
                        check = checkOf(chosen, entries.size),
                        chosen = chosen,
                        expanded = isOpen,
                        onToggle = {
                            onExcludedChange(if (chosen == entries.size) excluded + paths else excluded - paths)
                        },
                        onExpand = { if (isOpen) expanded.remove(key) else expanded.add(key) },
                    )
                }
                if (isOpen) {
                    items(entries, key = { "t-" + it.track.path }) { item ->
                        val on = item.track.path !in excluded
                        TrackSelectRow(
                            item = item,
                            checked = on,
                            onToggle = {
                                onExcludedChange(if (on) excluded + item.track.path else excluded - item.track.path)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun checkOf(chosen: Int, total: Int): Check = when (chosen) {
    0 -> Check.OFF
    total -> Check.ON
    else -> Check.PARTIAL
}

@Composable
private fun SelectAllRow(
    state: Check,
    selectedCount: Int,
    total: Int,
    selectedBytes: Long,
    onToggle: () -> Unit,
) {
    val colors = Xaos.colors
    Column(Modifier.padding(top = 28.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XaosCheckbox(state, onToggle)
            Spacer(Modifier.width(14.dp))
            Text(
                if (state == Check.ON) "DESELEZIONA TUTTO" else "SELEZIONA TUTTO",
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
                modifier = Modifier.pressable(onToggle),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "[$selectedCount/$total] SELEZIONATI · ${formatBytes(selectedBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkTertiary,
            )
        }
        Text(
            "Le spunte vengono ricordate: quello che togli resta escluso anche le prossime volte.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkTertiary,
            modifier = Modifier.padding(start = 46.dp, bottom = 8.dp),
        )
        Hairline()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun AlbumSelectRow(
    entries: List<SyncItem>,
    check: Check,
    chosen: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
) {
    val colors = Xaos.colors
    val first = entries.first().track
    Row(
        Modifier.fillMaxWidth().hoverRow().pressable(onExpand).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaosCheckbox(check, onToggle)
        Spacer(Modifier.width(14.dp))
        ArtworkImage(first, size = 38.dp, corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                first.album.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = if (check == Check.OFF) colors.inkTertiary else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(first.albumArtist, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "[$chosen/${entries.size}] · ${formatBytes(entries.sumOf { it.size })}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            XaosIcons.ChevronDown,
            if (expanded) "Chiudi" else "Mostra i brani",
            tint = colors.inkSecondary,
            modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun TrackSelectRow(item: SyncItem, checked: Boolean, onToggle: () -> Unit) {
    val colors = Xaos.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 46.dp)
            .hoverRow()
            .pressable(onToggle)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaosCheckbox(if (checked) Check.ON else Check.OFF, onToggle, size = 16.dp)
        Spacer(Modifier.width(12.dp))
        // Già sul telefono, ma modificato sul PC da allora: va rimandato.
        if (item.isUpdate) {
            Tag("AGGIORNATO")
            Spacer(Modifier.width(8.dp))
        }
        Text(
            item.track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) colors.ink else colors.inkTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(item.file.extension.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
        Spacer(Modifier.width(12.dp))
        Text(formatBytes(item.size), style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, modifier = Modifier.width(64.dp))
    }
}

@Composable
private fun ConnectedPanel(
    state: PhoneState.Connected,
    remoteRoot: String,
    formatLabel: String,
    plan: SyncPlan?,
    selected: List<SyncItem>,
    status: SyncStatus,
    onSync: () -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = Xaos.colors
    Column(Modifier.fillMaxWidth().nothingCard().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(XaosIcons.Phone, null, tint = colors.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.name.uppercase(), style = MaterialTheme.typography.titleLarge, color = colors.ink)
                // Cartella e versione si cambiano dalle impostazioni: un clic ci porta.
                Text(
                    "CARTELLA  $remoteRoot  ·  VERSIONE  $formatLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkTertiary,
                    modifier = Modifier.pressable(onOpenSettings),
                )
            }
            AccentDot(size = 8.dp)
            Spacer(Modifier.width(8.dp))
            Text("COLLEGATO", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        }

        Hairline()

        when {
            status is SyncStatus.Running -> RunningView(status, onCancel)
            plan == null || status is SyncStatus.Planning -> Row(verticalAlignment = Alignment.CenterVertically) {
                DotSpinner(size = 22.dp)
                Spacer(Modifier.width(12.dp))
                Text("CONFRONTO CON LA LIBRERIA DEL TELEFONO…", style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
            }
            else -> PlanView(plan, selected, onSync, onRefresh)
        }

        when (status) {
            is SyncStatus.Done -> ResultLine(
                text = buildString {
                    append(if (status.cancelled) "INTERROTTO · " else "COMPLETATO · ")
                    append("[${status.sent}] INVIATI")
                    if (status.failed > 0) append(" · [${status.failed}] NON RIUSCITI")
                },
                onDismiss = onDismiss,
            )
            is SyncStatus.Failed -> ResultLine("ERRORE · ${status.message}", onDismiss)
            else -> Unit
        }
    }
}

@Composable
private fun PlanView(plan: SyncPlan, selected: List<SyncItem>, onSync: () -> Unit, onRefresh: () -> Unit) {
    val colors = Xaos.colors
    val bytes = selected.sumOf { it.size }
    val fits = plan.freeBytes == null || bytes < plan.freeBytes - SyncPlan.SPACE_MARGIN
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        BigStat(selected.size.toString(), "DA INVIARE")
        BigStat(formatBytes(bytes), "DA COPIARE")
        BigStat(plan.alreadyThere.toString(), "GIÀ SUL TELEFONO")
        plan.freeBytes?.let { BigStat(formatBytes(it), "SPAZIO LIBERO") }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
            text = when {
                plan.missing.isEmpty() -> "TUTTO SINCRONIZZATO"
                selected.isEmpty() -> "NIENTE SELEZIONATO"
                else -> "SINCRONIZZA [${selected.size}]"
            },
            onClick = onSync,
            icon = XaosIcons.Sync,
            filled = true,
            enabled = selected.isNotEmpty() && fits,
        )
        PillButton("RICONTROLLA", onClick = onRefresh)
        if (!fits) {
            Text(
                "NON C'È ABBASTANZA SPAZIO SUL TELEFONO",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentInk,
            )
        }
    }
}

@Composable
private fun RunningView(status: SyncStatus.Running, onCancel: () -> Unit) {
    val colors = Xaos.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DotText("${(status.fraction * 100).toInt()}%", color = colors.ink, pitch = 5.dp)
            Text(
                "[${status.doneFiles}/${status.totalFiles}] · ${formatBytes(status.doneBytes)} / ${formatBytes(status.totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkTertiary,
            )
        }
        DotProgressLine(status.fraction, modifier = Modifier.fillMaxWidth().height(10.dp), spacing = 6.dp, radius = 1.8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotSpinner(size = 16.dp)
            Spacer(Modifier.width(10.dp))
            Text(status.currentName, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            PillButton("ANNULLA", onClick = onCancel, icon = XaosIcons.Close)
        }
    }
}

@Composable
private fun BigStat(value: String, label: String) {
    val colors = Xaos.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DotText(value, color = colors.ink, pitch = 4.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)
    }
}

@Composable
private fun ResultLine(text: String, onDismiss: () -> Unit) {
    val colors = Xaos.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        AccentDot(size = 6.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.ink, modifier = Modifier.weight(1f))
        Icon(XaosIcons.Close, "Chiudi", tint = colors.inkTertiary, modifier = Modifier.size(16.dp).pressable(onDismiss))
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    val colors = Xaos.colors
    Column(Modifier.fillMaxWidth().nothingCard().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentDot(size = 7.dp, color = colors.track)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.ink)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.inkSecondary)
    }
}

/**
 * Lo stato di Xaos per Android sul telefono: se manca, la proposta di
 * installarlo. Chi usa già un altro player può ignorarla e sincronizzare lo
 * stesso: i brani finiscono in Musica, e qualunque app li vede.
 */
@Composable
private fun AppCard(
    phone: PhoneState.Connected,
    install: AppInstall,
    canInstall: Boolean,
    updateAvailable: Boolean,
    bundledVersionName: String?,
    apkSize: Long?,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Xaos.colors
    val justInstalled = install is AppInstall.Done
    val busyOrFailed = install is AppInstall.Installing || install is AppInstall.Failed
    if (phone.appInstalled && !justInstalled && !updateAvailable && !busyOrFailed) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentDot(size = 6.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "XAOS PER ANDROID INSTALLATO SUL TELEFONO" + (phone.appVersionName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
                modifier = Modifier.weight(1f),
            )
            PillButton("APRI SUL TELEFONO", onClick = onLaunch, icon = XaosIcons.Phone)
        }
        return
    }
    val isUpdate = phone.appInstalled
    Row(
        Modifier.fillMaxWidth().nothingCard().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(
                    when {
                        justInstalled && isUpdate -> "AGGIORNATO"
                        justInstalled -> "INSTALLATO"
                        isUpdate -> "AGGIORNAMENTO DISPONIBILE"
                        else -> "NON INSTALLATO"
                    }
                )
                Spacer(Modifier.width(10.dp))
                Text("XAOS PER ANDROID", style = MaterialTheme.typography.labelLarge, color = colors.ink)
            }
            Text(
                text = when {
                    justInstalled && isUpdate -> "Il telefono ha ora la versione ${bundledVersionName.orEmpty()}. Playlist e preferiti sono rimasti."
                    justInstalled -> "Pronto: aprilo sul telefono per dargli l'accesso alla musica."
                    isUpdate && install !is AppInstall.Failed ->
                        "Sul telefono c'è la ${phone.appVersionName ?: "versione precedente"}, qui la ${bundledVersionName.orEmpty()}. " +
                            "L'aggiornamento mantiene playlist, preferiti e impostazioni del telefono."
                    install is AppInstall.Failed -> "Non è andata: ${install.message}"
                    !canInstall && apkSize == null -> "Questa versione di Xaos desktop non include l'app per il telefono."
                    !canInstall -> "L'app inclusa è per processori arm64, il telefono è ${phone.abi}."
                    else -> "Sul telefono non c'è. Puoi installarlo da qui, oppure continuare con il " +
                        "player che usi già: i brani sincronizzati li vede qualunque app."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
            )
        }
        Spacer(Modifier.width(16.dp))
        when {
            install is AppInstall.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                DotSpinner(size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Text("INSTALLAZIONE…", style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
            }
            justInstalled -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("APRI SUL TELEFONO", onClick = onLaunch, icon = XaosIcons.Phone, filled = true)
                PillButton("OK", onClick = onDismiss)
            }
            canInstall -> PillButton(
                text = when {
                    install is AppInstall.Failed -> "RIPROVA"
                    isUpdate -> "AGGIORNA XAOS"
                    else -> "INSTALLA XAOS"
                },
                onClick = onInstall,
                icon = XaosIcons.Download,
                filled = true,
            )
        }
    }
}

@Composable
private fun ConfirmInstallDialog(
    phoneName: String,
    isUpdate: Boolean,
    fromVersion: String?,
    toVersion: String?,
    apkSize: Long?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Xaos.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                if (isUpdate) "Aggiornare Xaos sul telefono?" else "Installare Xaos sul telefono?",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
            )
        },
        text = {
            Text(
                if (isUpdate) {
                    "Xaos su $phoneName passa dalla ${fromVersion ?: "versione installata"} alla ${toVersion.orEmpty()}" +
                        (apkSize?.let { " (${formatBytes(it)})" } ?: "") +
                        ". Playlist, preferiti e impostazioni restano come sono."
                } else {
                    "Xaos Music Player per Android verrà installato su $phoneName" +
                        (apkSize?.let { " (${formatBytes(it)})" } ?: "") +
                        ". Non tocca le altre app né la musica già presente."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
            )
        },
        confirmButton = { PillButton(if (isUpdate) "AGGIORNA" else "INSTALLA", onClick = onConfirm, filled = true) },
        dismissButton = { PillButton("ANNULLA", onClick = onDismiss) },
    )
}

/**
 * I testi aggiunti o cambiati sul PC che sul telefono mancano o sono diversi.
 * Aggiornarli tocca solo il testo dentro il file del telefono.
 */
@Composable
private fun LyricsPanel(phone: PhoneSync) {
    val colors = Xaos.colors
    val check by phone.lyrics.collectAsState()
    val deselected = remember { mutableStateListOf<String>() }

    when (val c = check) {
        LyricsCheck.Idle -> Unit
        is LyricsCheck.Checking -> Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DotSpinner(size = 16.dp)
            Spacer(Modifier.width(10.dp))
            Text("CONTROLLO DEI TESTI SUL TELEFONO  ${c.done}/${c.total}", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
        }
        is LyricsCheck.Updating -> Column(Modifier.fillMaxWidth().nothingCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("AGGIORNAMENTO DEI TESTI", count = c.total)
            DotProgressLine(c.done.toFloat() / c.total.coerceAtLeast(1), modifier = Modifier.fillMaxWidth().height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DotSpinner(size = 14.dp)
                Spacer(Modifier.width(10.dp))
                Text("[${c.done}/${c.total}]  ${c.name}", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        is LyricsCheck.Updated -> Row(Modifier.fillMaxWidth().nothingCard().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentDot(size = 6.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "TESTI AGGIORNATI SUL TELEFONO · [${c.ok}]" + if (c.failed > 0) " · [${c.failed}] NON RIUSCITI" else "",
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(XaosIcons.Close, "Chiudi", tint = colors.inkTertiary, modifier = Modifier.size(16.dp).pressable(phone::dismissLyricsResult))
        }
        is LyricsCheck.Ready -> if (c.updates.isNotEmpty()) {
            val chosen = c.updates.filter { it.track.path !in deselected }
            Column(Modifier.fillMaxWidth().nothingCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("TESTI DA AGGIORNARE SUL TELEFONO", count = c.updates.size) {
                    PillButton(
                        "AGGIORNA TESTI [${chosen.size}]",
                        onClick = { phone.updateLyrics(chosen) },
                        icon = XaosIcons.Sync,
                        filled = true,
                        enabled = chosen.isNotEmpty(),
                    )
                }
                Text(
                    "Sul PC questi brani hanno un testo che sul telefono manca o è diverso. " +
                        "L'aggiornamento scrive solo il testo nel file del telefono: audio, nome e cartella restano quelli.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkTertiary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val all = chosen.size == c.updates.size
                    XaosCheckbox(checkOf(chosen.size, c.updates.size), onClick = {
                        if (all) deselected.addAll(c.updates.map { it.track.path }) else deselected.clear()
                    }, size = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (all) "DESELEZIONA TUTTO" else "SELEZIONA TUTTO", style = MaterialTheme.typography.labelMedium, color = colors.ink)
                }
                c.updates.forEach { u ->
                    val on = u.track.path !in deselected
                    Row(
                        Modifier.fillMaxWidth().hoverRow().pressable {
                            if (on) deselected.add(u.track.path) else deselected.remove(u.track.path)
                        }.padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaosCheckbox(if (on) Check.ON else Check.OFF, onClick = {
                            if (on) deselected.add(u.track.path) else deselected.remove(u.track.path)
                        }, size = 16.dp)
                        Spacer(Modifier.width(12.dp))
                        ArtworkImage(u.track, size = 30.dp, corner = 5.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.track.title, style = MaterialTheme.typography.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(u.phonePath.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            if (u.missingOnPhone) "MANCA SUL TELEFONO" else "DIVERSO",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
        else -> String.format(Locale.US, "%d MB", bytes / 1_048_576)
    }
}
