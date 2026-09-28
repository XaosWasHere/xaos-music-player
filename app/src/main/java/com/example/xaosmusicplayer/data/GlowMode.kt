package com.example.xaosmusicplayer.data

/** Come si comporta lo sfondo della schermata di riproduzione. */
enum class GlowMode {
    /** Matrice di punti che deriva e pulsa con l'audio. */
    ANIMATED,

    /** Matrice ferma nei colori della copertina, senza reazione né deriva. */
    STATIC,

    /** La copertina ingrandita a tutto schermo, sfumata nel fondo. */
    ARTWORK,

    /** Solo la griglia di puntini, spenta. */
    OFF;

    val label: String
        get() = when (this) {
            ANIMATED -> "MATRICE REATTIVA"
            STATIC -> "MATRICE FISSA"
            ARTWORK -> "COPERTINA"
            OFF -> "MATRICE SPENTA"
        }
}
