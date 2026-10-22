#pragma once

#include <3ds.h>
#include <citro2d.h>
#include "dotfont.h"
#include "settings.h"

#define TOP_W 400
#define BOTTOM_W 320
#define SCREEN_H 240

/*
 * Gli stessi colori di Xaos: scala di grigi Nothing, rosso sul tema scuro e
 * giallo sul chiaro. Come su desktop l'accento ha due ruoli: accent per ciò
 * che si riempie, accent_ink per testo e icone sul fondo (sul chiaro il giallo
 * come testo non si leggerebbe).
 */
typedef struct {
    bool dark;
    u32 bg;
    u32 surface;
    u32 surface2;
    u32 line;
    u32 ink;
    u32 ink2;
    u32 ink3;
    u32 accent;
    u32 accent_ink;
    u32 on_accent;
    /* I puntini dello sfondo: presenti ma sotto il testo (trasparenti se spenti). */
    u32 dot;
    /* Il secondo colore dello sfondo, in diagonale, se scelto. */
    bool has_bg2;
    u32 bg2;
} Palette;

extern Palette g_pal;

#define COL_BG       (g_pal.bg)
#define COL_BG_CLEAR (g_pal.bg & 0x00FFFFFF)
#define COL_SURFACE  (g_pal.surface)
#define COL_SURFACE2 (g_pal.surface2)
#define COL_LINE     (g_pal.line)
#define COL_INK      (g_pal.ink)
#define COL_INK2     (g_pal.ink2)
#define COL_INK3     (g_pal.ink3)
#define COL_ACCENT   (g_pal.accent)
#define COL_ACC_INK  (g_pal.accent_ink)
#define COL_ON_ACC   (g_pal.on_accent)
#define COL_DOT      (g_pal.dot)

/* La tavolozza dal tema chiaro o scuro, con i colori scelti dall'utente sopra. */
void ui_set_palette(bool dark, const CustomTheme *custom);
/* ARGB (come su desktop) -> colore citro2d, e ritorno. */
u32 argb_to_c2d(u32 argb);
u32 c2d_to_argb(u32 c);

/* Lo sfondo: il colore, o la sfumatura in diagonale se scelta. */
void ui_background(float w, float h);

void ui_init(void);
void ui_exit(void);

/* Da chiamare a inizio fotogramma: libera il testo del fotogramma prima. */
void ui_frame_begin(void);

/* Su quale schermo si sta disegnando, per i ritagli. */
void ui_set_screen(gfxScreen_t screen);

/* Testo con il carattere di sistema (accenti e tutto). size 1 = 30 px. */
void ui_text(const char *s, float x, float y, float size, u32 color, u32 align);
/* Come ui_text, ma va a capo entro width. */
void ui_text_wrap(const char *s, float x, float y, float size, u32 color, u32 align, float width);
float ui_text_width(const char *s, float size);
float ui_text_height(const char *s, float size, float width);

/* Testo a punti: pitch è la distanza fra due punti (una riga è alta 7 pitch). */
void dot_text(const char *s, float x, float y, float pitch, u32 color);
float dot_text_width(const char *s, float pitch);
void dot_icon(DotIcon icon, float x, float y, float pitch, u32 color);

/* Disegna solo dentro il rettangolo, fino a ui_unclip. */
void ui_clip(float x, float y, float w, float h);
void ui_unclip(void);
/* Ritagli annidati: una zona di base (la lista) e, dentro, ritagli più piccoli (una colonna). */
void ui_clip_base(float x, float y, float w, float h);
void ui_clip_within(float x, float y, float w, float h);
void ui_clip_restore(void);
void ui_clip_end(void);

/* Il riquadro di un album senza copertina: una griglia di punti e le iniziali. */
void ui_placeholder_cover(const char *name, float x, float y, float size);

/* Lo sfondo a puntini; eye -1/+1 per gli occhi del 3D, depth lo spostamento massimo. */
#define DOT_STEP 8.0f
void ui_dot_background(float width, float height, float eye, float depth);
/* Dove cade un puntino della griglia con il 3D: x spostata, grandezza, lontananza (0..1). */
void ui_dot_place(float x, float y, float width, float height, float eye, float depth, float *sx, float *size, float *far);
/* Il fondo (o la sfumatura) con trasparenza a, sopra quello che c'è. */
void ui_overlay(float w, float h, float a);

/* "3:07", "1:02:03". */
void ui_format_time(char *out, size_t n, u32 ms);
