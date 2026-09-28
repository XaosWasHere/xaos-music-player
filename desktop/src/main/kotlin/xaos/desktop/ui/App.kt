package xaos.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xaos.desktop.Settings
import xaos.desktop.library.Library
import xaos.desktop.library.ScanState
import xaos.desktop.player.Player
import xaos.desktop.sync.PhoneState
import xaos.desktop.sync.PhoneSync
import xaos.desktop.theme.DotText
import xaos.desktop.theme.Xaos

/** Le sezioni della barra laterale. */
enum class Section(val label: String, val icon: ImageVector) {
    ALBUMS("ALBUM", XaosIcons.Album),
    SONGS("BRANI", XaosIcons.MusicNote),
    ARTISTS("ARTISTI", XaosIcons.Person),
    PHONE("TELEFONO", XaosIcons.Phone),
}

/** Le schermate di dettaglio, impilate sopra la sezione. */
sealed interface Detail {
    data class AlbumDetail(val key: String) : Detail
    data class ArtistDetail(val name: String) : Detail
}

@Composable
fun XaosDesktopApp(
    settings: Settings,
    library: Library,
    player: Player,
    phone: PhoneSync,
    onPickLibrary: () -> Unit,
) {
    val prefs by settings.data.collectAsState()
    val snapshot by library.snapshot.collectAsState()
    val scan by library.scan.collectAsState()
    val phoneState by phone.state.collectAsState()

    var section by remember { mutableStateOf(Section.ALBUMS) }
    val details = remember { mutableStateListOf<Detail>() }
    var query by remember { mutableStateOf("") }

    fun select(target: Section) {
        section = target
        details.clear()
        query = ""
    }

    val colors = Xaos.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(
                selected = section,
                phoneState = phoneState,
                scan = scan,
                libraryRoot = prefs.libraryRoot,
                trackCount = snapshot.tracks.size,
                isDark = prefs.dark,
                onSelect = ::select,
                onToggleTheme = { settings.update { it.copy(dark = !it.dark) } },
                onPickLibrary = onPickLibrary,
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colors.background)
                    .dotGrid(colors.dot),
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (section != Section.PHONE) {
                        TopBar(
                            query = query,
                            onQueryChange = { query = it },
                            canGoBack = details.isNotEmpty(),
                            onBack = { details.removeLastOrNull() },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val detail = details.lastOrNull()
                        when {
                            section == Section.PHONE -> PhoneScreen(
                                phone = phone,
                                snapshot = snapshot,
                                preferMp3 = prefs.preferMp3OnPhone,
                                onPreferMp3Change = { v -> settings.update { it.copy(preferMp3OnPhone = v) } },
                            )
                            query.isNotBlank() -> SearchResults(
                                query = query,
                                snapshot = snapshot,
                                player = player,
                                onOpenAlbum = { details += Detail.AlbumDetail(it.key); query = "" },
                                onOpenArtist = { details += Detail.ArtistDetail(it.name); query = "" },
                            )
                            detail is Detail.AlbumDetail -> AlbumDetailScreen(
                                album = snapshot.albums.firstOrNull { it.key == detail.key },
                                player = player,
                                onOpenArtist = { details += Detail.ArtistDetail(it) },
                            )
                            detail is Detail.ArtistDetail -> ArtistDetailScreen(
                                artist = snapshot.artists.firstOrNull { it.name == detail.name },
                                player = player,
                                onOpenAlbum = { details += Detail.AlbumDetail(it.key) },
                            )
                            section == Section.ALBUMS -> AlbumsScreen(snapshot, scan) {
                                details += Detail.AlbumDetail(it.key)
                            }
                            section == Section.SONGS -> SongsScreen(snapshot, player)
                            section == Section.ARTISTS -> ArtistsScreen(snapshot) {
                                details += Detail.ArtistDetail(it.name)
                            }
                        }
                    }
                }
            }
        }
        PlayerBar(player = player, onVolumeChange = { v -> settings.update { it.copy(volume = v) } })
    }
}

@Composable
private fun Sidebar(
    selected: Section,
    phoneState: PhoneState,
    scan: ScanState,
    libraryRoot: String?,
    trackCount: Int,
    isDark: Boolean,
    onSelect: (Section) -> Unit,
    onToggleTheme: () -> Unit,
    onPickLibrary: () -> Unit,
) {
    val colors = Xaos.colors
    Column(
        Modifier
            .width(236.dp)
            .fillMaxHeight()
            .background(colors.sidebar)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            DotText("XAOS", color = colors.ink, pitch = 4.dp)
            Spacer(Modifier.width(8.dp))
            AccentDot(size = 7.dp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("MUSIC PLAYER · DESKTOP", style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary)

        Spacer(Modifier.height(28.dp))

        Section.entries.forEach { entry ->
            NavItem(
                section = entry,
                selected = entry == selected,
                badge = if (entry == Section.PHONE) phoneState is PhoneState.Connected else false,
                onClick = { onSelect(entry) },
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.weight(1f))

        PhoneStatusCard(phoneState, onClick = { onSelect(Section.PHONE) })
        Spacer(Modifier.height(12.dp))

        // La cartella della libreria, con lo stato della scansione.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .pressable(onPickLibrary)
                .padding(vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(XaosIcons.Folder, null, tint = colors.inkSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    libraryRoot?.let { java.io.File(it).name.uppercase() } ?: "SCEGLI CARTELLA",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (scan) {
                    is ScanState.Scanning -> "SCANSIONE ${scan.done}/${scan.total}"
                    is ScanState.Failed -> "ERRORE DI LETTURA"
                    ScanState.Idle -> "[$trackCount] BRANI"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = XaosIcons.Contrast,
                contentDescription = if (isDark) "Tema chiaro" else "Tema scuro",
                onClick = onToggleTheme,
                size = 32.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (isDark) "TEMA SCURO" else "TEMA CHIARO",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun NavItem(section: Section, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val colors = Xaos.colors
    val tint by animateColorAsState(if (selected) colors.ink else colors.inkSecondary, label = "nav")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverRow(selected)
            .pressable(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(section.label, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.weight(1f))
        if (selected) AccentDot(size = 6.dp)
        else if (badge) AccentDot(size = 6.dp, color = colors.inkSecondary)
    }
}

@Composable
private fun PhoneStatusCard(state: PhoneState, onClick: () -> Unit) {
    val colors = Xaos.colors
    val (title, subtitle, live) = when (state) {
        is PhoneState.Connected -> Triple(state.name.uppercase(), "COLLEGATO", true)
        is PhoneState.Unauthorized -> Triple("TELEFONO", "CONFERMA SUL TELEFONO", false)
        PhoneState.Disconnected -> Triple("NESSUN TELEFONO", "COLLEGA VIA USB", false)
        PhoneState.NoAdb -> Triple("ADB NON TROVATO", "SERVE ANDROID PLATFORM-TOOLS", false)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .nothingCard(RoundedCornerShape(14.dp))
            .pressable(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XaosIcons.Phone, null, tint = if (live) colors.ink else colors.inkTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.inkTertiary, maxLines = 1)
        }
        AccentDot(size = 7.dp, color = if (live) colors.accent else colors.track)
    }
}

@Composable
private fun TopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    val colors = Xaos.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            CircleIconButton(XaosIcons.Back, "Indietro", onBack)
            Spacer(Modifier.width(12.dp))
        }
        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .width(360.dp)
                .clip(shape)
                .background(colors.card, shape)
                .border(1.dp, if (query.isNotEmpty()) colors.accent else colors.line, shape)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(XaosIcons.Search, null, tint = colors.inkSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Cerca brani, album, artisti", style = MaterialTheme.typography.bodyMedium, color = colors.inkTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accentInk),
                    keyboardActions = KeyboardActions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    XaosIcons.Close, "Cancella",
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(16.dp).pressable { onQueryChange("") },
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Titolo di schermata: la parola in matrice di punti e una riga tecnica sotto. */
@Composable
fun ScreenTitle(title: String, caption: String? = null, trailing: @Composable (() -> Unit)? = null) {
    val colors = Xaos.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            DotText(title, color = colors.ink, pitch = 5.dp)
            if (caption != null) {
                Spacer(Modifier.height(10.dp))
                Text(caption, style = MaterialTheme.typography.labelMedium, color = colors.inkTertiary)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyMessage(title: String, body: String? = null) {
    val colors = Xaos.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.inkSecondary)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.inkTertiary)
        }
    }
}
