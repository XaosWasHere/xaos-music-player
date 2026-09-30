package xaos.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icone dell'app, definite come path vettoriali.
 *
 * Il set `material-icons-core` incluso in Material 3 non contiene i simboli di
 * trasporto (pausa, brano precedente/successivo) né shuffle, repeat e affini, e
 * `material-icons-extended` non è più allineato alle BOM recenti. Definirle qui
 * costa poche righe ed elimina una dipendenza fragile.
 */
object XaosIcons {

    val Play: ImageVector by lazy { icon("M8 5v14l11-7z") }
    val Pause: ImageVector by lazy { icon("M6 19h4V5H6v14zm8-14v14h4V5h-4z") }
    val Next: ImageVector by lazy { icon("M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z") }
    val Previous: ImageVector by lazy { icon("M6 6h2v12H6zm3.5 6l8.5 6V6z") }

    val Shuffle: ImageVector by lazy {
        icon(
            "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 " +
                "5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 " +
                "20H20v-5.5l-2.04 2.04-3.13-3.13z"
        )
    }

    val Repeat: ImageVector by lazy {
        icon("M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z")
    }

    val RepeatOne: ImageVector by lazy {
        icon(
            "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1" +
                "l-2 1v1h1.5v4H13z"
        )
    }

    val Timer: ImageVector by lazy {
        icon(
            "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41" +
                "l-1.42 1.42A8.962 8.962 0 0012 4a9 9 0 109 9c0-2.12-.74-4.07-1.97-5.61z" +
                "M12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"
        )
    }

    val Equalizer: ImageVector by lazy {
        icon("M10 20h4V4h-4v16zm-6 0h4v-8H4v8zM16 9v11h4V9h-4z")
    }

    val Queue: ImageVector by lazy {
        icon(
            "M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18" +
                "-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z"
        )
    }

    val PlaylistAdd: ImageVector by lazy {
        icon(
            "M14 10H2v2h12v-2zm0-4H2v2h12V6zm4 8v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2 16h8v-2H2v2z"
        )
    }

    val Sort: ImageVector by lazy {
        icon("M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z")
    }

    val ChevronDown: ImageVector by lazy {
        icon("M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z")
    }

    val MusicNote: ImageVector by lazy {
        icon("M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z")
    }

    val Search: ImageVector by lazy {
        icon(
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 " +
                "5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 " +
                "19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"
        )
    }

    val Close: ImageVector by lazy {
        icon(
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 " +
                "17.59 19 19 17.59 13.41 12z"
        )
    }

    val Back: ImageVector by lazy {
        icon("M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
    }

    val Favorite: ImageVector by lazy {
        icon(
            "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 " +
                "4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 " +
                "11.54L12 21.35z"
        )
    }

    val FavoriteBorder: ImageVector by lazy {
        icon(
            "M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 " +
                "3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 " +
                "19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 " +
                "5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 " +
                "2.89-3.14 5.74-7.9 10.05z"
        )
    }

    val Download: ImageVector by lazy {
        icon("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
    }

    val DragHandle: ImageVector by lazy {
        icon("M20 9H4v2h16V9zM4 15h16v-2H4v2z")
    }

    val Add: ImageVector by lazy { icon("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }

    val Edit: ImageVector by lazy {
        icon(
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" +
                "M20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0" +
                "l-1.83 1.83 3.75 3.75 1.83-1.83z"
        )
    }

    val Delete: ImageVector by lazy {
        icon(
            "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"
        )
    }

    val Check: ImageVector by lazy {
        icon("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z")
    }

    val Home: ImageVector by lazy { icon("M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z") }

    val Phone: ImageVector by lazy {
        icon("M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z")
    }

    val Album: ImageVector by lazy {
        icon(
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 14.5c-2.49 " +
                "0-4.5-2.01-4.5-4.5S9.51 7.5 12 7.5s4.5 2.01 4.5 4.5-2.01 4.5-4.5 4.5zm0-5.5c-.55 " +
                "0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1z"
        )
    }

    val Volume: ImageVector by lazy {
        icon(
            "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 " +
                "2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 " +
                "7-4.49 7-8.77s-2.99-7.86-7-8.77z"
        )
    }

    val VolumeOff: ImageVector by lazy {
        icon(
            "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 " +
                ".94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06" +
                "c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93" +
                "-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 " +
                "6.09 12 8.18V4z"
        )
    }

    val Folder: ImageVector by lazy {
        icon("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z")
    }

    val Fullscreen: ImageVector by lazy {
        icon("M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z")
    }

    val Settings: ImageVector by lazy {
        icon(
            "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41" +
                ".12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-" +
                ".36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-" +
                "1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l" +
                "2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92" +
                " 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48" +
                ".41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08" +
                ".47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-" +
                "1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"
        )
    }

    /** Altre opzioni: tre punti in fila. */
    val More: ImageVector by lazy {
        icon(
            "M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 " +
                "2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"
        )
    }

    val Person: ImageVector by lazy {
        icon(
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 " +
                "4v2h16v-2c0-2.66-5.33-4-8-4z"
        )
    }

    /**
     * Tema chiaro/scuro: disco pieno a metà. I due sottopercorsi girano in versi
     * opposti, così il secondo buca il primo e resta solo l'anello a destra.
     */
    val Contrast: ImageVector by lazy {
        icon(
            "M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10z" +
                "m1-17.93c3.94.49 7 3.86 7 7.93s-3.06 7.44-7 7.93V4.07z"
        )
    }

    /** Torna al punto: due frecce che si rincorrono. */
    val Sync: ImageVector by lazy {
        icon(
            "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 " +
                "15.03 20 13.57 20 12c0-4.42-3.58-8-8-8z" +
                "M12 18c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 " +
                "4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"
        )
    }

    val Library: ImageVector by lazy {
        icon(
            "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 " +
                "0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9H9V9h10v2zm-4 4H9v-2h6v2zm4-8H9V5h10v2z"
        )
    }

    // I tre stati dello sfondo. Sono disegnati con curve di Bézier e non con
    // archi ellittici: la forma compatta degli archi ("a10 10 0 100 20") rende
    // ambigui i flag large-arc e sweep, e il parser finiva per disegnare
    // mezzelune invece di cerchi.

    /** Gradiente animato: anello con nucleo, come un'onda che si propaga. */
    val GlowAnimated: ImageVector by lazy {
        icon(
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 " +
                "0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" +
                "M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4z"
        )
    }

    /** Gradiente fisso: cerchio pieno. */
    val GlowStatic: ImageVector by lazy {
        icon("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z")
    }

    /** Copertina a tutto schermo: un'immagine con le montagne. */
    val GlowArtwork: ImageVector by lazy {
        icon(
            "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2z" +
                "M8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"
        )
    }

    /** Matrice spenta: cerchio barrato. */
    val GlowOff: ImageVector by lazy {
        icon(
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-4.42 " +
                "3.58-8 8-8 1.85 0 3.55.63 4.9 1.69L5.69 16.9C4.63 15.55 4 13.85 4 12zm8 " +
                "8c-1.85 0-3.55-.63-4.9-1.69L18.31 7.1C19.37 8.45 20 10.15 20 12c0 4.42-3.58 8-8 8z"
        )
    }

    /** Il testo del brano: il microfono, come su Spotify. */
    val Mic: ImageVector by lazy {
        icon(
            "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 " +
                "3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 " +
                "6-3.3 6-6.72h-1.7z"
        )
    }

    val Stats: ImageVector by lazy { icon("M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z") }

    val PlaylistMusic: ImageVector by lazy {
        icon(
            "M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 " +
                "0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z"
        )
    }

    /** Il miniplayer: un riquadro piccolo dentro quello grande. */
    val MiniPlayer: ImageVector by lazy {
        icon(
            "M19 11h-8v6h8v-6zm4 8V4.98C23 3.88 22.1 3 21 3H3c-1.1 0-2 .88-2 1.98V19c0 1.1.9 2 2 " +
                "2h18c1.1 0 2-.9 2-2zm-2 .02H3V4.97h18v14.05z"
        )
    }

    /** Le onde del brano: barre di altezze diverse. */
    val Waveform: ImageVector by lazy {
        icon("M3 10h2v4H3zm4-4h2v12H7zm4-3h2v18h-2zm4 5h2v8h-2zm4 2h2v4h-2z")
    }

    val ArrowUp: ImageVector by lazy { icon("M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z") }
    val ArrowDown: ImageVector by lazy { icon("M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z") }

    private fun icon(pathData: String): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.White),
            )
        }.build()
}
