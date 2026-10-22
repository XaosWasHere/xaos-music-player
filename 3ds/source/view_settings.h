#pragma once

#include <3ds.h>
#include <stdbool.h>

/*
 * La scheda Impostazioni dello schermo sotto: le righe (dentro la lista di
 * view_bottom), il selettore di colore e la pagina dei comandi.
 */

#define SETTINGS_ROW_H 40
#define HELP_ROW_H 34

int settings_count(void);
void settings_row_draw(int i, float y, bool selected);
void settings_row_activate(int i);
/* X e Y su un preset: elimina e rinomina. true se il tasto è stato usato. */
bool settings_row_key(int i, u32 down);

/* Il selettore di colore, a schermo intero sullo schermo sotto. */
bool picker_active(void);
void picker_input(u32 down, u32 held, u32 up, const touchPosition *touch);
void picker_draw(void);

/* La pagina dei comandi. */
bool help_active(void);
void help_close(void);
int help_count(void);
void help_row_draw(int i, float y);
