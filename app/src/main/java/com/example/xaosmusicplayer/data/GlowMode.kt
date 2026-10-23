package com.example.xaosmusicplayer.data

import com.example.xaosmusicplayer.i18n.t

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
            ANIMATED -> t("MATRICE REATTIVA")
            STATIC -> t("MATRICE FISSA")
            ARTWORK -> t("COPERTINA")
            OFF -> t("MATRICE SPENTA")
        }
}
