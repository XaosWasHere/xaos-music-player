#include "ui.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

static C2D_TextBuf s_buf;
static gfxScreen_t s_screen = GFX_TOP;

/* Come C2D_Color32, ma costante: serve negli inizializzatori. */
#define RGBA(r, g, b, a) ((u32)(r) | (u32)(g) << 8 | (u32)(b) << 16 | (u32)(a) << 24)

static const Palette DARK = {
    .dark = true,
    .bg = RGBA(0x00, 0x00, 0x00, 0xFF),
    .surface = RGBA(0x1C, 0x1C, 0x1C, 0xFF),
    .surface2 = RGBA(0x29, 0x29, 0x29, 0xFF),
    .line = RGBA(0x32, 0x32, 0x32, 0xFF),
    .ink = RGBA(0xF2, 0xF2, 0xF2, 0xFF),
    .ink2 = RGBA(0x92, 0x92, 0x92, 0xFF),
    .ink3 = RGBA(0x60, 0x60, 0x60, 0xFF),
    .accent = RGBA(0xD7, 0x19, 0x21, 0xFF),
    .accent_ink = RGBA(0xD7, 0x19, 0x21, 0xFF),
    .on_accent = RGBA(0xFF, 0xFF, 0xFF, 0xFF),
    .dot = RGBA(0xFF, 0xFF, 0xFF, 0x30),
};

static const Palette LIGHT = {
    .dark = false,
    .bg = RGBA(0xF2, 0xF2, 0xF2, 0xFF),
    .surface = RGBA(0xFF, 0xFF, 0xFF, 0xFF),
    .surface2 = RGBA(0xE3, 0xE3, 0xE3, 0xFF),
    .line = RGBA(0xDA, 0xDA, 0xDA, 0xFF),
    .ink = RGBA(0x1C, 0x1C, 0x1C, 0xFF),
    .ink2 = RGBA(0x60, 0x60, 0x60, 0xFF),
    .ink3 = RGBA(0x92, 0x92, 0x92, 0xFF),
    .accent = RGBA(0xFF, 0xC7, 0x00, 0xFF),
    .accent_ink = RGBA(0x1C, 0x1C, 0x1C, 0xFF),
    .on_accent = RGBA(0x1C, 0x1C, 0x1C, 0xFF),
    .dot = RGBA(0x92, 0x92, 0x92, 0x60),
};

Palette g_pal = DARK;

u32 argb_to_c2d(u32 argb) {
    return RGBA((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >> 24);
}

u32 c2d_to_argb(u32 c) {
    return (c & 0xFF000000) | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF);
}

/* La luminanza relativa, come Color.luminance() di Compose. */
static float lum(u32 c) {
    float ch[3];
    for (int i = 0; i < 3; i++) {
        float v = ((c >> (8 * i)) & 0xFF) / 255.0f;
        ch[i] = v <= 0.04045f ? v / 12.92f : powf((v + 0.055f) / 1.055f, 2.4f);
    }
    return 0.2126f * ch[0] + 0.7152f * ch[1] + 0.0722f * ch[2];
}

static u32 mix(u32 a, u32 b, float f) {
    u32 out = 0;
    for (int i = 0; i < 4; i++) {
        float x = (a >> (8 * i)) & 0xFF, y = (b >> (8 * i)) & 0xFF;
        out |= (u32)(x + (y - x) * f + 0.5f) << (8 * i);
    }
    return out;
}

static u32 with_alpha(u32 c, u32 a) {
    return (c & 0x00FFFFFF) | a << 24;
}

/*
 * Come XaosPalette.customized() su desktop: si scelgono sfondo, pannelli,
 * testo e accento, e il resto se ne ricava.
 */
void ui_set_palette(bool dark, const CustomTheme *t) {
    Palette p = dark ? DARK : LIGHT;
    if (t && t->enabled) {
        u32 bg = t->has[SLOT_BACKGROUND] ? argb_to_c2d(t->color[SLOT_BACKGROUND]) : p.bg;
        u32 panel = t->has[SLOT_PANELS] ? argb_to_c2d(t->color[SLOT_PANELS]) : p.surface;
        u32 text = t->has[SLOT_INK] ? argb_to_c2d(t->color[SLOT_INK]) : p.ink;
        u32 acc = t->has[SLOT_ACCENT] ? argb_to_c2d(t->color[SLOT_ACCENT]) : p.accent;
        bool dark_bg = lum(bg) < 0.4f;
        float la = lum(acc), lb = lum(bg);
        float contrast = ((la > lb ? la : lb) + 0.05f) / ((la < lb ? la : lb) + 0.05f);
        p.dark = dark_bg;
        p.bg = bg;
        p.has_bg2 = t->has[SLOT_BACKGROUND2];
        p.bg2 = p.has_bg2 ? argb_to_c2d(t->color[SLOT_BACKGROUND2]) : bg;
        p.surface = panel;
        p.surface2 = mix(panel, text, 0.08f);
        p.line = mix(panel, text, 0.14f);
        p.ink = text;
        p.ink2 = mix(bg, text, 0.62f);
        p.ink3 = mix(bg, text, 0.42f);
        p.accent = acc;
        /* Sull'accento il nero o il bianco, quello che si legge meglio. */
        p.on_accent = la > 0.45f ? RGBA(0x11, 0x11, 0x11, 0xFF) : RGBA(0xFF, 0xFF, 0xFF, 0xFF);
        /* L'accento come testo solo se sul fondo si legge; altrimenti il testo. */
        p.accent_ink = contrast >= 3 ? acc : text;
        p.dot = t->dots ? with_alpha(text, dark_bg ? 0x33 : 0x4D) : 0;
    }
    g_pal = p;
}

void ui_background(float w, float h) {
    if (!g_pal.has_bg2) return;
    u32 mid = mix(g_pal.bg, g_pal.bg2, 0.5f);
    C2D_DrawRectangle(0, 0, 0, w, h, g_pal.bg, mid, mid, g_pal.bg2);
}

void ui_init(void) {
    s_buf = C2D_TextBufNew(8192);
}

void ui_exit(void) {
    C2D_TextBufDelete(s_buf);
}

void ui_frame_begin(void) {
    C2D_TextBufClear(s_buf);
}

void ui_set_screen(gfxScreen_t screen) {
    s_screen = screen;
}

/* ------------------------------------------------------------ testo di sistema */

static bool parse(C2D_Text *t, const char *s) {
    if (!s) s = "";
    if (!C2D_TextParse(t, s_buf, s)) return false;
    C2D_TextOptimize(t);
    return true;
}

void ui_text(const char *s, float x, float y, float size, u32 color, u32 align) {
    C2D_Text t;
    if (!parse(&t, s)) return;
    C2D_DrawText(&t, C2D_WithColor | align, x, y, 0, size, size, color);
}

void ui_text_wrap(const char *s, float x, float y, float size, u32 color, u32 align, float width) {
    C2D_Text t;
    if (!parse(&t, s)) return;
    C2D_DrawText(&t, C2D_WithColor | C2D_WordWrap | align, x, y, 0, size, size, color, width);
}

float ui_text_width(const char *s, float size) {
    C2D_Text t;
    if (!parse(&t, s)) return 0;
    float w, h;
    C2D_TextGetDimensions(&t, size, size, &w, &h);
    return w;
}

float ui_text_height(const char *s, float size, float width) {
    /* citro2d non misura il testo a capo: si conta riga per riga con le parole. */
    if (!s || !*s) return 0;
    float line = 30.0f * size;
    float space = ui_text_width(" ", size);
    float x = 0;
    int lines = 1;
    char word[256];
    const char *p = s;
    while (*p) {
        while (*p == ' ') p++;
        if (!*p) break;
        size_t n = 0;
        while (p[n] && p[n] != ' ' && n < sizeof(word) - 1) n++;
        memcpy(word, p, n);
        word[n] = 0;
        p += n;
        float w = ui_text_width(word, size);
        if (x > 0 && x + space + w > width) {
            lines++;
            x = w;
        } else {
            x += (x > 0 ? space : 0) + w;
        }
    }
    return lines * line;
}

/* ------------------------------------------------------------ testo a punti */

/* Le lettere accentate perdono l'accento, come sui display veri. */
static const char LATIN1[] =
    "AAAAAAACEEEEIIIIDNOOOOOXOUUUUYPS"
    "AAAAAAACEEEEIIIIDNOOOOO/OUUUUYPY";

static u32 next_codepoint(const char **p) {
    const u8 *s = (const u8 *)*p;
    u32 c = s[0];
    int extra = 0;
    if (c < 0x80) extra = 0;
    else if ((c & 0xE0) == 0xC0) { c &= 0x1F; extra = 1; }
    else if ((c & 0xF0) == 0xE0) { c &= 0x0F; extra = 2; }
    else if ((c & 0xF8) == 0xF0) { c &= 0x07; extra = 3; }
    else { *p += 1; return '?'; }
    int i = 1;
    for (; i <= extra && (s[i] & 0xC0) == 0x80; i++) c = (c << 6) | (s[i] & 0x3F);
    *p += i;
    return c;
}

static const u8 *glyph_for(u32 c) {
    if (c >= 'a' && c <= 'z') c -= 32;
    else if (c >= 0xC0 && c <= 0xFF) c = (u8)LATIN1[c - 0xC0];
    else if (c == 0x2018 || c == 0x2019 || c == '`') c = '\'';
    else if (c == 0x2013 || c == 0x2014 || c == '_') c = '-';
    else if (c == 0x2026) c = '.';
    else if (c == 0x201C || c == 0x201D || c == '"') c = '\'';
    if (c > 0xFF) c = '?';
    const u8 *g = DOT_GLYPHS[c];
    if (c != ' ' && !(g[0] | g[1] | g[2] | g[3] | g[4] | g[5] | g[6])) g = DOT_GLYPHS['?'];
    return g;
}

/* Sotto i tre pixel di passo un cerchio non si vede: un quadratino. */
static void dot(float x, float y, float pitch, u32 color) {
    if (pitch >= 3) {
        C2D_DrawCircleSolid(x + pitch * 0.5f, y + pitch * 0.5f, 0, pitch * 0.36f, color);
    } else {
        float d = pitch > 1.5f ? pitch - 1 : 1;
        C2D_DrawRectSolid(x, y, 0, d, d, color);
    }
}

float dot_text_width(const char *s, float pitch) {
    int n = 0;
    const char *p = s;
    while (*p) {
        next_codepoint(&p);
        n++;
    }
    if (n == 0) return 0;
    return (n * (DOT_COLS + 1) - 1) * pitch;
}

void dot_text(const char *s, float x, float y, float pitch, u32 color) {
    x = floorf(x);
    y = floorf(y);
    const char *p = s;
    while (*p) {
        const u8 *g = glyph_for(next_codepoint(&p));
        for (int row = 0; row < DOT_ROWS; row++) {
            u8 bits = g[row];
            if (!bits) continue;
            for (int col = 0; col < DOT_COLS; col++) {
                if (bits & (0x10 >> col)) dot(x + col * pitch, y + row * pitch, pitch, color);
            }
        }
        x += (DOT_COLS + 1) * pitch;
    }
}

void dot_icon(DotIcon icon, float x, float y, float pitch, u32 color) {
    x = floorf(x);
    y = floorf(y);
    const u8 *g = DOT_ICONS[icon];
    for (int row = 0; row < ICON_SIZE; row++) {
        for (int col = 0; col < ICON_SIZE; col++) {
            if (g[row] & (0x40 >> col)) dot(x + col * pitch, y + row * pitch, pitch, color);
        }
    }
}

/* ------------------------------------------------------------ ritagli */

/*
 * Lo schermo del 3DS è montato ruotato: per la GPU la x va lungo i 240
 * pixel e la y lungo la larghezza, al contrario. Il ritaglio va girato.
 */
void ui_clip(float x, float y, float w, float h) {
    C2D_Flush();
    int width = s_screen == GFX_TOP ? TOP_W : BOTTOM_W;
    int left = SCREEN_H - (int)(y + h);
    int top = width - (int)(x + w);
    int right = SCREEN_H - (int)y;
    int bottom = width - (int)x;
    if (left < 0) left = 0;
    if (top < 0) top = 0;
    C3D_SetScissor(GPU_SCISSOR_NORMAL, left, top, right, bottom);
}

void ui_unclip(void) {
    C2D_Flush();
    C3D_SetScissor(GPU_SCISSOR_DISABLE, 0, 0, 0, 0);
}

static float s_base[4];
static bool s_has_base;

void ui_clip_base(float x, float y, float w, float h) {
    s_base[0] = x;
    s_base[1] = y;
    s_base[2] = w;
    s_base[3] = h;
    s_has_base = true;
    ui_clip(x, y, w, h);
}

void ui_clip_within(float x, float y, float w, float h) {
    if (s_has_base) {
        float x1 = x + w, y1 = y + h;
        float bx1 = s_base[0] + s_base[2], by1 = s_base[1] + s_base[3];
        if (x < s_base[0]) x = s_base[0];
        if (y < s_base[1]) y = s_base[1];
        if (x1 > bx1) x1 = bx1;
        if (y1 > by1) y1 = by1;
        w = x1 > x ? x1 - x : 0;
        h = y1 > y ? y1 - y : 0;
    }
    ui_clip(x, y, w, h);
}

void ui_clip_restore(void) {
    if (s_has_base) ui_clip(s_base[0], s_base[1], s_base[2], s_base[3]);
    else ui_unclip();
}

void ui_clip_end(void) {
    s_has_base = false;
    ui_unclip();
}

/* ------------------------------------------------------------ copertina vuota */

static void initials(const char *name, char out[3]) {
    out[0] = out[1] = out[2] = 0;
    int n = 0;
    bool start = true;
    for (const char *p = name; *p && n < 2;) {
        const char *before = p;
        u32 c = next_codepoint(&p);
        bool letter = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c >= 0xC0;
        if (letter && start) {
            /* L'iniziale, senza accento. */
            if (c < 0x80) out[n++] = (char)c;
            else if (c >= 0xC0 && c <= 0xFF) out[n++] = LATIN1[c - 0xC0];
            else out[n++] = before[0];
        }
        start = !letter;
    }
    if (n == 0) out[0] = '?';
}

void ui_placeholder_cover(const char *name, float x, float y, float size) {
    C2D_DrawRectSolid(x, y, 0, size, size, COL_SURFACE);
    /* Griglia di punti spenti, come un display a riposo. */
    float step = size >= 60 ? 6 : 4;
    for (float py = y + step / 2; py < y + size - 1; py += step)
        for (float px = x + step / 2; px < x + size - 1; px += step)
            C2D_DrawRectSolid(floorf(px), floorf(py), 0, 1, 1, COL_DOT);
    char ini[3];
    initials(name ? name : "", ini);
    float pitch = floorf(size / 22);
    if (pitch < 1) pitch = 1;
    float w = dot_text_width(ini, pitch);
    dot_text(ini, x + (size - w) / 2, y + (size - DOT_ROWS * pitch) / 2, pitch, COL_INK);
}

/*
 * Lo sfondo a puntini. Con il 3D i puntini stanno su una conca dietro lo
 * schermo: lontani al centro, a filo ai bordi, curva sia in orizzontale sia
 * (meno) in verticale. Ogni occhio li vede spostati di metà della profondità,
 * in versi opposti; quelli lontani sono più piccoli e più spenti, così la
 * curva si legge anche a colpo d'occhio. Il resto dell'interfaccia non si
 * sposta e resta sul piano dello schermo.
 */
void ui_dot_place(float x, float y, float width, float height, float eye, float depth, float *sx, float *size, float *far) {
    float u = (x - width / 2) / (width / 2);
    float v = (y - height / 2) / (height / 2);
    /* 1 in fondo alla conca, 0 sul bordo. */
    float d = (1 - u * u) * (1 - 0.45f * v * v);
    if (depth <= 0) {
        *sx = x;
        *size = 1;
        *far = 0;
        return;
    }
    *sx = floorf(x + eye * depth * d * 0.5f);
    *size = d < 0.35f ? 2 : 1;
    *far = d;
}

void ui_dot_background(float width, float height, float eye, float depth) {
    u32 base = COL_DOT;
    u32 alpha = base >> 24;
    if (!alpha) return;
    for (float y = DOT_STEP / 2; y < height; y += DOT_STEP) {
        for (float x = DOT_STEP / 2; x < width; x += DOT_STEP) {
            float sx, size, far;
            ui_dot_place(x, y, width, height, eye, depth, &sx, &size, &far);
            u32 c = far > 0 ? (base & 0x00FFFFFF) | (u32)(alpha * (1.0f - 0.5f * far)) << 24 : base;
            C2D_DrawRectSolid(sx, y, 0, size, size, c);
        }
    }
}

void ui_overlay(float w, float h, float a) {
    if (a <= 0) return;
    if (a > 1) a = 1;
    u32 alpha = (u32)(a * 255) << 24;
    u32 c0 = (g_pal.bg & 0x00FFFFFF) | alpha;
    if (!g_pal.has_bg2) {
        C2D_DrawRectSolid(0, 0, 0, w, h, c0);
        return;
    }
    u32 c1 = (g_pal.bg2 & 0x00FFFFFF) | alpha;
    u32 mid = (mix(g_pal.bg, g_pal.bg2, 0.5f) & 0x00FFFFFF) | alpha;
    C2D_DrawRectangle(0, 0, 0, w, h, c0, mid, mid, c1);
}

void ui_format_time(char *out, size_t n, u32 ms) {
    u32 s = ms / 1000;
    if (s >= 3600) snprintf(out, n, "%lu:%02lu:%02lu", s / 3600, (s / 60) % 60, s % 60);
    else snprintf(out, n, "%lu:%02lu", s / 60, s % 60);
}
