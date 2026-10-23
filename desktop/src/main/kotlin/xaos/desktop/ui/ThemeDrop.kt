package xaos.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xaos.desktop.Settings
import xaos.desktop.ThemePreset
import xaos.desktop.i18n.t
import xaos.desktop.theme.DarkPalette
import xaos.desktop.theme.LightPalette
import xaos.desktop.theme.XTheme
import xaos.desktop.theme.XThemeFile
import xaos.desktop.theme.Xaos
import xaos.desktop.theme.customized
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Aggiunge ai preset il tema di un file .xtheme e lo applica. Con un nome già
 * usato aggiorna quel preset, come "Salva come preset".
 */
fun importTheme(settings: Settings, file: XThemeFile) {
    lastImported.value = file.name
    settings.update { s ->
        val same = s.themePresets.firstOrNull { it.name.equals(file.name, ignoreCase = true) }
        s.copy(
            themePresets = if (same != null) s.themePresets.map { if (it.id == same.id) it.copy(theme = file.theme) else it }
            else s.themePresets + ThemePreset("tp_${System.currentTimeMillis()}", file.name, file.theme),
            customTheme = file.theme.copy(enabled = true),
        )
    }
}

/** L'ultimo tema importato (trascinato, aperto con doppio clic o da file): la conferma lo mostra. */
private val lastImported = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

/** Cosa si sta trascinando sopra la finestra. */
private sealed interface Dragging {
    data class Theme(val file: XThemeFile) : Dragging
    /** Un file che non è un tema (o non si è potuto leggere mentre si trascina). */
    data object Other : Dragging
}

/**
 * Tutta la finestra accetta un file .xtheme trascinato da Esplora risorse, in
 * qualunque schermata: mentre lo si trascina compare l'anteprima del tema, e
 * rilasciandolo il tema si aggiunge ai preset e si applica.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThemeDropZone(settings: Settings, content: @Composable () -> Unit) {
    var dragging by remember { mutableStateOf<Dragging?>(null) }
    val imported by lastImported.collectAsState()

    fun filesOf(event: DragAndDropEvent): List<File> = runCatching {
        val transfer = event.awtTransferable
        if (!transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
        (transfer.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
    }.getOrDefault(emptyList())

    fun themeOf(event: DragAndDropEvent): XThemeFile? =
        filesOf(event).firstOrNull { it.extension.equals(XTheme.EXTENSION, ignoreCase = true) }?.let(XTheme::read)

    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                dragging = themeOf(event)?.let { Dragging.Theme(it) } ?: Dragging.Other
            }

            override fun onExited(event: DragAndDropEvent) {
                dragging = null
            }

            override fun onEnded(event: DragAndDropEvent) {
                dragging = null
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = null
                val theme = themeOf(event) ?: return false
                importTheme(settings, theme)
                return true
            }
        }
    }

    Box(
        Modifier.fillMaxSize().dragAndDropTarget(
            // Solo i file: un testo trascinato non c'entra.
            shouldStartDragAndDrop = { event -> runCatching { event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }.getOrDefault(false) },
            target = target,
        ),
    ) {
        content()
        AnimatedVisibility(dragging != null, enter = fadeIn(), exit = fadeOut()) {
            DropOverlay(dragging, settings)
        }
        AnimatedVisibility(imported != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val colors = Xaos.colors
            Text(
                t("TEMA «%s» AGGIUNTO AI PRESET E APPLICATO", imported.orEmpty().uppercase()),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onAccent,
                modifier = Modifier
                    .padding(bottom = 120.dp)
                    .background(colors.accent, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
    LaunchedEffect(imported) {
        if (imported != null) {
            delay(2_800)
            lastImported.value = null
        }
    }
}

@Composable
private fun DropOverlay(dragging: Dragging?, settings: Settings) {
    val colors = Xaos.colors
    val theme = (dragging as? Dragging.Theme)?.file
    Box(
        Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.9f)).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Il bordo dell'area di rilascio, su tutta la finestra.
        Box(
            Modifier.fillMaxSize().border(BorderStroke(2.dp, if (theme != null) colors.accent else colors.line), RoundedCornerShape(24.dp)),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (theme != null) {
                BigThemePreview(theme, settings.data.value.dark)
                xaos.desktop.theme.DotText(xaos.desktop.i18n.tDot("NUOVO TEMA"), color = colors.accentInk, pitch = 4.dp)
                Text(theme.name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = colors.ink)
                Text(t("Rilascia per aggiungerlo ai preset e applicarlo."), style = MaterialTheme.typography.bodyMedium, color = colors.inkSecondary)
            } else {
                Text(
                    t("Trascina qui un file .xtheme per aggiungere il tema."),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** L'anteprima grande di un tema: sfondo, pannello, righe di testo e accento. */
@Composable
private fun BigThemePreview(file: XThemeFile, dark: Boolean) {
    val p = (if (dark) DarkPalette else LightPalette).customized(file.theme)
    val shape = RoundedCornerShape(18.dp)
    val second = p.background2
    Box(
        Modifier
            .size(width = 300.dp, height = 190.dp)
            .background(if (second != null) Brush.linearGradient(listOf(p.background, second)) else Brush.linearGradient(listOf(p.background, p.background)), shape)
            .border(1.dp, Xaos.colors.line, shape)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(120.dp).height(10.dp).background(p.ink, RoundedCornerShape(4.dp)))
            Box(Modifier.width(80.dp).height(6.dp).background(p.inkSecondary, RoundedCornerShape(3.dp)))
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(0.7f).height(70.dp).background(p.surface, RoundedCornerShape(12.dp)).padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(90.dp).height(6.dp).background(p.ink, RoundedCornerShape(3.dp)))
                Box(Modifier.width(60.dp).height(5.dp).background(p.inkTertiary, RoundedCornerShape(3.dp)))
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).size(44.dp).background(p.accent, RoundedCornerShape(50)))
    }
}
