package com.example.xaosmusicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xaosmusicplayer.beam.Beam
import com.example.xaosmusicplayer.i18n.t
import com.example.xaosmusicplayer.ui.components.AccentDot
import com.example.xaosmusicplayer.ui.components.Hairline
import com.example.xaosmusicplayer.ui.components.PillButton
import com.example.xaosmusicplayer.ui.components.SectionHeader
import com.example.xaosmusicplayer.ui.icons.XaosIcons
import com.example.xaosmusicplayer.ui.theme.Xaos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Xaos Beam sul telefono: l'interruttore, il PC abbinato e quelli trovati in
 * rete. Toccando un PC si scrive il PIN che lui mostra nelle impostazioni, e
 * da lì la sua libreria compare in quella del telefono.
 */
@Composable
fun BeamScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Xaos.colors
    val enabled by Beam.enabled.collectAsState()
    val status by Beam.status.collectAsState()
    val pairing by Beam.pairing.collectAsState()
    val found by Beam.found.collectAsState()
    val scanning by Beam.scanning.collectAsState()
    var pinFor by remember { mutableStateOf<Beam.Server?>(null) }
    var askAddress by remember { mutableStateOf(false) }

    // Finché la schermata è aperta, i PC in rete si cercano di continuo.
    LaunchedEffect(enabled) {
        while (enabled) {
            Beam.scan()
            delay(SCAN_EVERY_MS)
        }
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = "XAOS BEAM", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (enabled) t("ACCESO") else t("SPENTO"), style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    Text(
                        t("Ascolta la libreria del PC in streaming, sulla rete di casa, senza copiarla sul telefono."),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = Beam::setEnabled, colors = beamSwitchColors())
            }

            if (enabled) {
                pairing?.let { p ->
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(t("PC ABBINATO"))
                    Hairline()
                    DeviceLine(
                        name = p.name,
                        detail = statusText(status) + " · ${p.host}:${p.port}",
                        live = status is Beam.Status.Connected,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(t("AGGIORNA"), onClick = Beam::refresh, icon = XaosIcons.Sync)
                        PillButton(t("DIMENTICA"), onClick = Beam::forget)
                    }
                    if (status == Beam.Status.Revoked) {
                        Text(
                            t("Il PC ha tolto questo telefono dall'elenco: toccalo qui sotto e scrivi il PIN per abbinarlo di nuovo."),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSecondary,
                        )
                    }
                }

                val others = found.filter { it.id != pairing?.id || status == Beam.Status.Revoked }
                Spacer(Modifier.height(8.dp))
                SectionHeader(t("PC IN RETE"), count = others.size) {
                    if (scanning) Text(t("RICERCA…"), style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
                }
                Hairline()
                if (others.isEmpty()) {
                    Text(
                        if (pairing == null) t("Nessun PC trovato. Su Xaos per PC: Impostazioni → Xaos Beam, accendilo. PC e telefono devono stare sulla stessa rete Wi-Fi.")
                        else t("Nessun altro PC in rete."),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                others.forEach { server ->
                    DeviceLine(
                        name = server.name,
                        detail = t("%s:%d · TOCCA PER ABBINARE", server.host, server.port),
                        live = false,
                        onClick = { pinFor = server },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(t("CERCA"), onClick = Beam::scan, icon = XaosIcons.Search)
                    PillButton(t("INSERISCI INDIRIZZO"), onClick = { askAddress = true })
                }
                Text(
                    t("I brani del PC compaiono nella libreria insieme a quelli del telefono, con lo stesso aspetto: si cercano, si mettono in coda e nelle playlist. Quelli che il telefono ha già suonano dalla copia locale."),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    pinFor?.let { server ->
        PinDialog(server = server, onDismiss = { pinFor = null })
    }
    if (askAddress) {
        AddressDialog(
            onDismiss = { askAddress = false },
            onFound = { askAddress = false; pinFor = it },
        )
    }
}

@Composable
private fun DeviceLine(name: String, detail: String, live: Boolean, onClick: (() -> Unit)? = null) {
    val colors = Xaos.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(18.dp)) { AccentDot(size = 8.dp, color = if (live) colors.accent else colors.line) }
        Icon(XaosIcons.Computer, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name.uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.inkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PinDialog(server: Beam.Server, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val ready = pin.length == PIN_LENGTH && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Xaos.colors.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(t("ABBINA %s", server.name.uppercase()), style = MaterialTheme.typography.titleLarge, color = Xaos.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    t("Scrivi il PIN che vedi sul PC, in Impostazioni → Xaos Beam."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.inkSecondary,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter(Char::isDigit).take(PIN_LENGTH); error = null },
                    singleLine = true,
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    colors = beamFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    busy = true
                    scope.launch {
                        Beam.pair(server, pin)
                            .onSuccess { onDismiss() }
                            .onFailure { error = it.message ?: t("abbinamento non riuscito"); busy = false }
                    }
                },
            ) {
                Text(
                    if (busy) "…" else t("ABBINA"),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ready) Xaos.colors.accentInk else Xaos.colors.inkSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("ANNULLA"), style = MaterialTheme.typography.titleMedium, color = Xaos.colors.inkSecondary)
            }
        },
    )
}

@Composable
private fun AddressDialog(onDismiss: () -> Unit, onFound: (Beam.Server) -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Xaos.colors.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(t("INDIRIZZO DEL PC"), style = MaterialTheme.typography.titleLarge, color = Xaos.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    t("Se il PC non compare da solo: l'indirizzo è nelle sue impostazioni, sotto Xaos Beam."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xaos.colors.inkSecondary,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim(); error = null },
                    singleLine = true,
                    label = { Text("192.168.1.10:47810") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    colors = beamFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = address.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        Beam.serverAt(address)
                            .onSuccess(onFound)
                            .onFailure { error = it.message ?: t("PC non raggiungibile"); busy = false }
                    }
                },
            ) {
                Text(if (busy) "…" else t("AVANTI"), style = MaterialTheme.typography.titleMedium, color = Xaos.colors.accentInk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("ANNULLA"), style = MaterialTheme.typography.titleMedium, color = Xaos.colors.inkSecondary)
            }
        },
    )
}

/** Lo stato di Beam in poche parole, per le impostazioni e per questa schermata. */
fun statusText(status: Beam.Status): String = when (status) {
    Beam.Status.Off -> t("SPENTO")
    Beam.Status.NotPaired -> t("NESSUN PC ABBINATO")
    Beam.Status.Connecting -> t("COLLEGAMENTO…")
    is Beam.Status.Connected -> t("COLLEGATO · %d BRANI", status.trackCount)
    Beam.Status.Unreachable -> t("PC NON RAGGIUNGIBILE")
    Beam.Status.Revoked -> t("DA ABBINARE DI NUOVO")
}

@Composable
fun beamSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Xaos.colors.onAccent,
    checkedTrackColor = Xaos.colors.accent,
    checkedBorderColor = Xaos.colors.accent,
    uncheckedThumbColor = Xaos.colors.inkTertiary,
    uncheckedTrackColor = Xaos.colors.card,
    uncheckedBorderColor = Xaos.colors.line,
)

@Composable
private fun beamFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Xaos.colors.ink,
    unfocusedTextColor = Xaos.colors.ink,
    focusedBorderColor = Xaos.colors.accent,
    unfocusedBorderColor = Xaos.colors.line,
    cursorColor = Xaos.colors.accentInk,
)

private const val PIN_LENGTH = 6
private const val SCAN_EVERY_MS = 6_000L
