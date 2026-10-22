#include "intro.h"

#include <math.h>
#include <stdlib.h>

#include "ui.h"

/* I tempi, in secondi dall'avvio. */
#define T_X_START 0.30f   /* la X comincia a formarsi */
#define T_X_DOT 0.035f    /* fra un punto della X e il successivo */
#define T_POP 0.20f       /* quanto dura il rimbalzo di un punto */
#define T_MORPH 1.00f     /* la X si rimpicciolisce e va al suo posto */
#define T_MORPH_END 1.45f
#define T_LETTERS 1.10f   /* A, O, S si accendono */
#define T_BAR 1.50f       /* il nastro sotto la scritta */
#define T_BAR_END 1.95f
#define T_REVEAL 2.05f    /* il player emerge */
#define T_END 2.50f

/* L'onda dei puntini di sfondo: velocità e morbidezza del fronte, in pixel. */
#define WAVE_SPEED 560.0f
#define WAVE_SOFT 70.0f
#define WAVE_FRONT 26.0f
/* Il centro dell'onda, sullo schermo sopra. */
#define WAVE_X 200.0f
#define WAVE_Y 112.0f
/* Lo schermo sotto sta 40 pixel più a destra e sotto quello di sopra. */
#define BOTTOM_DX 40.0f
#define BOTTOM_DY 264.0f

/* La X grande al centro, e la scritta finale. */
#define P_BIG 16.0f
#define P_WORD 8.0f
#define WORD_Y 84.0f

static bool s_active;
static u64 s_start;
static float s_skip;

static float now(void) {
    return (osGetTime() - s_start) / 1000.0f + s_skip;
}

static float clamp01(float x) {
    return x < 0 ? 0 : x > 1 ? 1 : x;
}

static float smooth(float x) {
    x = clamp01(x);
    return x * x * (3 - 2 * x);
}

/* Supera un po' la misura e ci torna: il "pop" di un punto che si accende. */
static float ease_out_back(float x) {
    x = clamp01(x);
    const float c1 = 1.70158f, c3 = c1 + 1;
    float y = x - 1;
    return 1 + c3 * y * y * y + c1 * y * y;
}

void intro_start(void) {
    s_active = true;
    s_start = osGetTime();
    s_skip = 0;
}

bool intro_active(void) {
    if (s_active && now() >= T_END) s_active = false;
    return s_active;
}

void intro_input(u32 down) {
    if (!s_active || !down) return;
    float t = now();
    if (t < T_REVEAL) s_skip += T_REVEAL - t;
}

/* ------------------------------------------------------------ puntini di sfondo */

/* La griglia di sfondo che si accende a onda; ox, oy portano lo schermo sotto sotto quello di sopra. */
static void wave_dots(float width, float height, float ox, float oy, float eye, float depth, float t, float fade) {
    u32 base = COL_DOT;
    u32 alpha = base >> 24;
    float front = t * WAVE_SPEED;
    for (float y = DOT_STEP / 2; y < height; y += DOT_STEP) {
        for (float x = DOT_STEP / 2; x < width; x += DOT_STEP) {
            float dx = x + ox - WAVE_X, dy = y + oy - WAVE_Y;
            float dist = sqrtf(dx * dx + dy * dy);
            float on = clamp01((front - dist) / WAVE_SOFT) * fade;
            float crest = (1 - fabsf(front - dist) / WAVE_FRONT) * fade;
            if (on <= 0 && crest <= 0) continue;
            float sx, size, far;
            ui_dot_place(x, y, width, height, eye, depth, &sx, &size, &far);
            if (on > 0 && alpha) {
                u32 a = (u32)(alpha * on * (1.0f - 0.5f * far));
                C2D_DrawRectSolid(sx, y, 0, size, size, (base & 0x00FFFFFF) | a << 24);
            }
            /* Il fronte dell'onda passa nel colore d'accento. */
            if (crest > 0) {
                u32 a = (u32)(230 * crest);
                C2D_DrawRectSolid(sx - 0.5f, y - 0.5f, 0, 2, 2, (COL_ACCENT & 0x00FFFFFF) | a << 24);
            }
        }
    }
}

/* ------------------------------------------------------------ il logo */

static float word_x(void) {
    float w = (4 * (DOT_COLS + 1) - 1) * P_WORD;
    return (TOP_W - w) / 2;
}

/* Un punto del logo, con il suo rimbalzo; dx sposta il logo davanti allo schermo col 3D. */
static void logo_dot(float x, float y, float pitch, float scale, float dx) {
    if (scale <= 0.01f) return;
    C2D_DrawCircleSolid(x + pitch / 2 + dx, y + pitch / 2, 0, pitch * 0.38f * scale, COL_ACCENT);
}

static void draw_logo(float t, float eye, float depth) {
    float reveal = smooth((t - T_REVEAL) / (T_END - T_REVEAL));
    float shrink = 1 - reveal;
    float drift = -6 * reveal;
    /* Con il 3D il logo esce un po' dallo schermo. */
    float dx = -eye * depth * 0.3f;
    float m = smooth((t - T_MORPH) / (T_MORPH_END - T_MORPH));
    float wx = word_x();

    /* La X: da grande e sola al centro, a prima lettera della scritta. */
    float pitch = P_BIG + (P_WORD - P_BIG) * m;
    float x0 = (TOP_W - DOT_COLS * P_BIG) / 2 + (wx - (TOP_W - DOT_COLS * P_BIG) / 2) * m;
    float y0 = (WORD_Y + DOT_ROWS * P_WORD / 2 - DOT_ROWS * P_BIG / 2) * (1 - m) + WORD_Y * m + drift;
    const u8 *gx = DOT_GLYPHS['X'];
    /* I punti della X si accendono dagli angoli verso il centro. */
    int order = 0;
    for (int ring = 3; ring >= 0; ring--) {
        for (int row = 0; row < DOT_ROWS; row++) {
            for (int col = 0; col < DOT_COLS; col++) {
                if (!(gx[row] & (0x10 >> col))) continue;
                int dist = abs(row - 3) > abs(col - 2) ? abs(row - 3) : abs(col - 2);
                if (dist != ring) continue;
                float appear = T_X_START + order++ * T_X_DOT;
                float s = ease_out_back((t - appear) / T_POP) * shrink;
                logo_dot(x0 + col * pitch, y0 + row * pitch, pitch, s, dx);
            }
        }
    }

    /* A, O, S: colonna per colonna, da sinistra. */
    static const char LETTERS[] = "AOS";
    for (int l = 0; l < 3; l++) {
        const u8 *g = DOT_GLYPHS[(u8)LETTERS[l]];
        float lx = wx + (l + 1) * (DOT_COLS + 1) * P_WORD;
        for (int col = 0; col < DOT_COLS; col++) {
            for (int row = 0; row < DOT_ROWS; row++) {
                if (!(g[row] & (0x10 >> col))) continue;
                float appear = T_LETTERS + l * 0.09f + col * 0.025f + row * 0.008f;
                float s = ease_out_back((t - appear) / T_POP) * shrink;
                logo_dot(lx + col * P_WORD, WORD_Y + row * P_WORD + drift, P_WORD, s, dx);
            }
        }
    }

    /* Il nastro: una fila di puntini sotto la scritta che si riempie. */
    float bar_in = smooth((t - T_BAR) / 0.15f) * shrink;
    if (bar_in > 0) {
        float w = (4 * (DOT_COLS + 1) - 1) * P_WORD;
        float y = WORD_Y + DOT_ROWS * P_WORD + 20 + drift;
        float fill = smooth((t - T_BAR) / (T_BAR_END - T_BAR));
        int n = (int)(w / 6) + 1;
        for (int i = 0; i < n; i++) {
            bool lit = i < fill * n;
            u32 c = lit ? COL_ACCENT : COL_LINE;
            u32 a = (u32)(((c >> 24) & 0xFF) * bar_in);
            C2D_DrawRectSolid(wx + i * 6 + dx, y, 0, 2, 2, (c & 0x00FFFFFF) | a << 24);
        }
    }
}

/* ------------------------------------------------------------ disegno */

void intro_draw_top(float eye, float depth) {
    if (!s_active) return;
    float t = now();
    float reveal = smooth((t - T_REVEAL) / (T_END - T_REVEAL));
    /* Il player è già disegnato sotto: un velo del colore di fondo lo copre e poi si apre. */
    ui_overlay(TOP_W, SCREEN_H, 1 - reveal);
    wave_dots(TOP_W, SCREEN_H, 0, 0, eye, depth, t, 1 - reveal);
    draw_logo(t, eye, depth);
}

void intro_draw_bottom(void) {
    if (!s_active) return;
    float t = now();
    float reveal = smooth((t - T_REVEAL) / (T_END - T_REVEAL));
    ui_overlay(BOTTOM_W, SCREEN_H, 1 - reveal);
    wave_dots(BOTTOM_W, SCREEN_H, BOTTOM_DX, BOTTOM_DY, 0, 0, t, 1 - reveal);
}
