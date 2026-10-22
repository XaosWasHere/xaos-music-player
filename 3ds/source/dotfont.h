#pragma once

#include <3ds.h>

/*
 * Le lettere a matrice di punti di Xaos: cinque colonne per sette righe, una
 * riga per byte (il bit 4 è la colonna a sinistra). Sono le stesse della
 * versione desktop, da cui vengono generate.
 */
extern const u8 DOT_GLYPHS[256][7];

#define DOT_COLS 5
#define DOT_ROWS 7

/* Le icone dei comandi, sette punti per sette come le lettere. */
typedef enum {
    ICON_PLAY,
    ICON_PAUSE,
    ICON_PREV,
    ICON_NEXT,
    ICON_SHUFFLE,
    ICON_REPEAT,
    ICON_BACK,
    ICON_NOTE,
    ICON_GEAR,
    ICON_COUNT,
} DotIcon;

#define ICON_SIZE 7

extern const u8 DOT_ICONS[ICON_COUNT][ICON_SIZE];
