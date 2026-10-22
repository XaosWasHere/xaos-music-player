#pragma once

#include <3ds.h>
#include <stdbool.h>

/*
 * Lo schermo sopra: il brano in riproduzione, con i testi. Con il 3D si
 * disegna due volte: eye -1 per l'occhio sinistro, +1 per il destro, 0 senza
 * 3D; depth è lo spostamento massimo dei puntini di sfondo.
 */
void view_top_draw(bool audio_ok, float eye, float depth);

/* Lo schermo sotto, a sfioramento: libreria, coda e comandi. */
void view_bottom_input(u32 down, u32 held, u32 up, const touchPosition *touch, const circlePosition *pad);
void view_bottom_draw(void);

/* La libreria è stata sostituita: gli indici di prima non valgono più. */
void views_library_changed(void);

/* Un avviso breve sopra i comandi. */
void views_toast(const char *text);

/* Mentre si trascina la barra del tempo: il punto in cui si salterà. */
bool views_scrub_preview(u32 *ms);
