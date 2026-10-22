#include "view_settings.h"

#include <math.h>
#include <stdio.h>
#include <string.h>
#include <strings.h>

#include "library.h"
#include "settings.h"
#include "spatial.h"
#include "ui.h"
#include "views.h"

#define W BOTTOM_W

typedef enum {
    R_HEADER,
    R_THEME,
    R_CUSTOM,
    R_SLOT,
    R_DOTS,
    R_RESET,
    R_PRESET,
    R_SAVE_PRESET,
    R_DEPTH,
    R_VIRT,
    R_TAPE,
    R_INTRO,
    R_HELP,
    R_LIBRARY,
    R_ABOUT,
} RowKind;

typedef struct {
    RowKind kind;
    int arg;
    const char *title;
} Row;

#define MAX_ROWS (32 + MAX_PRESETS)

static Row s_rows[MAX_ROWS];
static int s_row_count;

static bool s_help;
/* Il preset da eliminare alla seconda pressione di X, e fino a quando. */
static int s_delete_armed = -1;
static u64 s_delete_until;

static void add(RowKind kind, int arg, const char *title) {
    if (s_row_count < MAX_ROWS) s_rows[s_row_count++] = (Row){ kind, arg, title };
}

/* Le righe dipendono dallo stato (i colori si vedono solo col tema personalizzato). */
static void build(void) {
    s_row_count = 0;
    add(R_HEADER, 0, "ASPETTO");
    add(R_THEME, 0, NULL);
    add(R_CUSTOM, 0, NULL);
    if (g_settings.custom.enabled) {
        for (int i = 0; i < SLOT_COUNT; i++) add(R_SLOT, i, NULL);
        add(R_DOTS, 0, NULL);
        add(R_RESET, 0, NULL);
    }
    add(R_HEADER, 0, "TEMI SALVATI");
    for (int p = 0; p < g_settings.preset_count; p++) add(R_PRESET, p, NULL);
    add(R_SAVE_PRESET, 0, NULL);
    add(R_HEADER, 0, "3D E SUONO");
    add(R_DEPTH, 0, NULL);
    add(R_VIRT, 0, NULL);
    add(R_TAPE, 0, NULL);
    add(R_HEADER, 0, "ALTRO");
    add(R_INTRO, 0, NULL);
    add(R_HELP, 0, NULL);
    add(R_LIBRARY, 0, NULL);
    add(R_ABOUT, 0, NULL);
}

int settings_count(void) {
    build();
    return s_row_count;
}

static void changed(void) {
    settings_apply_theme();
    settings_save();
}

/* ------------------------------------------------------------ nomi dei preset */

/* La tastiera del 3DS. false se annullata. */
static bool ask_name(const char *hint, const char *initial, char *out, size_t n) {
    SwkbdState kb;
    swkbdInit(&kb, SWKBD_TYPE_NORMAL, 2, (int)n - 1);
    swkbdSetHintText(&kb, hint);
    if (initial) swkbdSetInitialText(&kb, initial);
    swkbdSetValidation(&kb, SWKBD_NOTEMPTY_NOTBLANK, 0, 0);
    return swkbdInputText(&kb, out, n) == SWKBD_BUTTON_CONFIRM;
}

/* Come su desktop: con un nome già usato il preset prende i colori di adesso. */
static void save_preset(void) {
    char name[PRESET_NAME_LEN];
    if (!ask_name("Nome del preset", NULL, name, sizeof(name))) return;
    CustomTheme t = g_settings.custom;
    t.enabled = true;
    for (int p = 0; p < g_settings.preset_count; p++) {
        if (!strcasecmp(g_settings.presets[p].name, name)) {
            g_settings.presets[p].theme = t;
            presets_save();
            views_toast("PRESET AGGIORNATO");
            return;
        }
    }
    if (g_settings.preset_count >= MAX_PRESETS) {
        views_toast("TROPPI PRESET");
        return;
    }
    ThemePreset *pr = &g_settings.presets[g_settings.preset_count++];
    snprintf(pr->name, PRESET_NAME_LEN, "%s", name);
    pr->theme = t;
    presets_save();
    views_toast("PRESET SALVATO");
}

/* ------------------------------------------------------------ selettore di colore */

static int s_picker = -1;
static float s_h, s_s, s_v;
static CustomTheme s_backup;
static int s_drag_zone = -1;

#define SV_X 12
#define SV_Y 36
#define SV_SIZE 148
#define HUE_X 168
#define HUE_W 22
#define BTN_Y 196
#define BTN_H 36

static u32 hsv_argb(float h, float s, float v) {
    float c = v * s;
    float hh = fmodf(h, 360) / 60;
    float x = c * (1 - fabsf(fmodf(hh, 2) - 1));
    float r = 0, g = 0, b = 0;
    if (hh < 1) r = c, g = x;
    else if (hh < 2) r = x, g = c;
    else if (hh < 3) g = c, b = x;
    else if (hh < 4) g = x, b = c;
    else if (hh < 5) r = x, b = c;
    else r = c, b = x;
    float m = v - c;
    return 0xFF000000 | (u32)((r + m) * 255 + 0.5f) << 16 | (u32)((g + m) * 255 + 0.5f) << 8 | (u32)((b + m) * 255 + 0.5f);
}

static void argb_hsv(u32 c, float *h, float *s, float *v) {
    float r = ((c >> 16) & 0xFF) / 255.0f, g = ((c >> 8) & 0xFF) / 255.0f, b = (c & 0xFF) / 255.0f;
    float mx = fmaxf(r, fmaxf(g, b)), mn = fminf(r, fminf(g, b)), d = mx - mn;
    *v = mx;
    *s = mx > 0 ? d / mx : 0;
    /* Su un grigio la tinta non conta: resta quella di prima. */
    if (d == 0) return;
    if (mx == r) *h = 60 * fmodf((g - b) / d, 6);
    else if (mx == g) *h = 60 * ((b - r) / d + 2);
    else *h = 60 * ((r - g) / d + 4);
    if (*h < 0) *h += 360;
}

static void open_picker(int slot) {
    s_backup = g_settings.custom;
    s_picker = slot;
    s_h = 0;
    u32 c = g_settings.custom.has[slot] ? g_settings.custom.color[slot] : slot_fallback(slot);
    argb_hsv(c, &s_h, &s_s, &s_v);
}

/* Il colore si vede subito su tutta l'app, mentre si sceglie. */
static void apply_pick(void) {
    g_settings.custom.has[s_picker] = true;
    g_settings.custom.color[s_picker] = hsv_argb(s_h, s_s, s_v);
    settings_apply_theme();
}

static void close_picker(bool keep) {
    if (!keep) g_settings.custom = s_backup;
    changed();
    s_picker = -1;
}

bool picker_active(void) {
    return s_picker >= 0;
}

static int button_at(int x, int y) {
    if (y < BTN_Y || y > BTN_Y + BTN_H) return -1;
    return x < W / 3 ? 0 : x < 2 * W / 3 ? 1 : 2;
}

static void press_button(int b) {
    if (b == 0) {
        g_settings.custom.has[s_picker] = false;
        close_picker(true);
    } else if (b == 1) {
        close_picker(false);
    } else if (b == 2) {
        close_picker(true);
    }
}

void picker_input(u32 down, u32 held, u32 up, const touchPosition *t) {
    if (down & KEY_A) {
        press_button(2);
        return;
    }
    if (down & KEY_B) {
        press_button(1);
        return;
    }
    u32 rep = hidKeysDownRepeat();
    bool moved = false;
    if (rep & KEY_DUP) s_v = fminf(1, s_v + 0.02f), moved = true;
    if (rep & KEY_DDOWN) s_v = fmaxf(0, s_v - 0.02f), moved = true;
    if (rep & KEY_DRIGHT) s_s = fminf(1, s_s + 0.02f), moved = true;
    if (rep & KEY_DLEFT) s_s = fmaxf(0, s_s - 0.02f), moved = true;
    if (held & KEY_R) s_h = fmodf(s_h + 2, 360), moved = true;
    if (held & KEY_L) s_h = fmodf(s_h + 358, 360), moved = true;

    if (down & KEY_TOUCH) {
        if (t->px >= SV_X && t->px < SV_X + SV_SIZE && t->py >= SV_Y && t->py < SV_Y + SV_SIZE) s_drag_zone = 0;
        else if (t->px >= HUE_X - 6 && t->px < HUE_X + HUE_W + 6 && t->py >= SV_Y && t->py < SV_Y + SV_SIZE) s_drag_zone = 1;
        else s_drag_zone = 10 + button_at(t->px, t->py);
    }
    if ((held & KEY_TOUCH) && (s_drag_zone == 0 || s_drag_zone == 1)) {
        float fx = fminf(1, fmaxf(0, (t->px - SV_X) / (float)SV_SIZE));
        float fy = fminf(1, fmaxf(0, (t->py - SV_Y) / (float)SV_SIZE));
        if (s_drag_zone == 0) {
            s_s = fx;
            s_v = 1 - fy;
        } else {
            s_h = fy * 359.9f;
        }
        moved = true;
    }
    if (up & KEY_TOUCH) {
        if (s_drag_zone >= 10) press_button(s_drag_zone - 10);
        s_drag_zone = -1;
    }
    if (moved && s_picker >= 0) apply_pick();
}

void picker_draw(void) {
    if (s_picker < 0) return;
    dot_text(slot_name(s_picker), 12, 10, 2, COL_INK);
    ui_text(slot_hint(s_picker), 12, 22, 0.38f, COL_INK3, C2D_AlignLeft);

    /* Saturazione in orizzontale, luminosità in verticale. */
    u32 hue = argb_to_c2d(hsv_argb(s_h, 1, 1));
    u32 white = C2D_Color32(0xFF, 0xFF, 0xFF, 0xFF);
    u32 black = C2D_Color32(0, 0, 0, 0xFF), clear = C2D_Color32(0, 0, 0, 0);
    C2D_DrawRectangle(SV_X, SV_Y, 0, SV_SIZE, SV_SIZE, white, hue, white, hue);
    C2D_DrawRectangle(SV_X, SV_Y, 0, SV_SIZE, SV_SIZE, clear, clear, black, black);
    /* La tinta: sei tratti fra i colori puri. */
    for (int i = 0; i < 6; i++) {
        u32 a = argb_to_c2d(hsv_argb(i * 60, 1, 1)), b = argb_to_c2d(hsv_argb((i + 1) * 60, 1, 1));
        float y = SV_Y + i * SV_SIZE / 6.0f;
        C2D_DrawRectangle(HUE_X, y, 0, HUE_W, SV_SIZE / 6.0f + 0.5f, a, a, b, b);
    }
    /* I segni di dove si è. */
    float cx = SV_X + s_s * SV_SIZE, cy = SV_Y + (1 - s_v) * SV_SIZE;
    C2D_DrawCircleSolid(cx, cy, 0, 6, s_v > 0.5f ? black : white);
    C2D_DrawCircleSolid(cx, cy, 0, 4, argb_to_c2d(hsv_argb(s_h, s_s, s_v)));
    float hy = SV_Y + s_h / 360 * SV_SIZE;
    C2D_DrawRectSolid(HUE_X - 4, hy - 1.5f, 0, HUE_W + 8, 3, COL_INK);

    /* L'anteprima, con il codice del colore. */
    const float px = HUE_X + HUE_W + 12, pw = W - px - 12;
    u32 argb = hsv_argb(s_h, s_s, s_v);
    C2D_DrawRectSolid(px - 1, SV_Y - 1, 0, pw + 2, 62, COL_LINE);
    C2D_DrawRectSolid(px, SV_Y, 0, pw, 60, argb_to_c2d(argb));
    char hex[12];
    snprintf(hex, sizeof(hex), "%06lX", argb & 0xFFFFFF);
    dot_text(hex, px, SV_Y + 68, 2, COL_INK);
    ui_text("Croce: saturazione e luce", px, SV_Y + 90, 0.36f, COL_INK3, C2D_AlignLeft);
    ui_text("L / R: tinta", px, SV_Y + 104, 0.36f, COL_INK3, C2D_AlignLeft);

    static const char *const LABELS[3] = { "AZZERA", "ANNULLA", "OK" };
    for (int b = 0; b < 3; b++) {
        float bx = b * W / 3.0f + 6, bw = W / 3.0f - 12;
        bool primary = b == 2;
        C2D_DrawRectSolid(bx, BTN_Y, 0, bw, BTN_H, primary ? COL_ACCENT : COL_SURFACE);
        float tw = dot_text_width(LABELS[b], 2);
        dot_text(LABELS[b], bx + (bw - tw) / 2, BTN_Y + 11, 2, primary ? COL_ON_ACC : COL_INK);
    }
}

/* ------------------------------------------------------------ righe */

static void row_text(const char *label, const char *hint, float y) {
    dot_text(label, 12, y + 8, 2, COL_INK);
    ui_clip_within(0, y, W - 110, SETTINGS_ROW_H);
    ui_text(hint, 12, y + 23, 0.38f, COL_INK3, C2D_AlignLeft);
    ui_clip_restore();
}

static void row_value(const char *value, float y, u32 color) {
    float vw = dot_text_width(value, 2);
    dot_text(value, W - 12 - vw, y + 14, 2, color);
}

static void swatch(float x, float y, float size, u32 argb, bool has) {
    C2D_DrawRectSolid(x - 1, y - 1, 0, size + 2, size + 2, COL_LINE);
    C2D_DrawRectSolid(x, y, 0, size, size, argb_to_c2d(argb));
    if (!has) C2D_DrawLine(x, y + size, COL_INK3, x + size, y, COL_INK3, 1, 0);
}

void settings_row_draw(int i, float y, bool selected) {
    if (i < 0 || i >= s_row_count) return;
    const Row *r = &s_rows[i];
    if (r->kind == R_HEADER) {
        dot_text(r->title, 12, y + 22, 2, COL_ACC_INK);
        C2D_DrawRectSolid(12, y + SETTINGS_ROW_H - 2, 0, W - 24, 1, COL_LINE);
        return;
    }
    if (selected) C2D_DrawRectSolid(0, y, 0, W, SETTINGS_ROW_H, COL_SURFACE);
    char value[48];
    const Library *lib = library_get();
    const CustomTheme *t = &g_settings.custom;
    switch (r->kind) {
    case R_THEME:
        row_text("TEMA", "La base: scuro (rosso) o chiaro (giallo)", y);
        row_value(g_settings.dark ? "SCURO" : "CHIARO", y, COL_ACC_INK);
        break;
    case R_CUSTOM:
        row_text("COLORI", "Sfondo, pannelli, testo e accento a scelta", y);
        row_value(t->enabled ? "MIEI" : "XAOS", y, COL_ACC_INK);
        break;
    case R_SLOT: {
        bool has = t->has[r->arg];
        row_text(slot_name(r->arg), has ? slot_hint(r->arg) : r->arg == SLOT_BACKGROUND2 ? "Nessuna: tinta unita" : "Predefinito", y);
        u32 c = has ? t->color[r->arg] : slot_fallback(r->arg);
        swatch(W - 34, y + 9, 22, c, has || r->arg != SLOT_BACKGROUND2);
        if (has) {
            snprintf(value, sizeof(value), "%06lX", c & 0xFFFFFF);
            float vw = dot_text_width(value, 1.5f);
            dot_text(value, W - 44 - vw, y + 15, 1.5f, COL_INK3);
        }
        break;
    }
    case R_DOTS:
        row_text("PALLINI DI SFONDO", "La griglia di punti dietro le schermate", y);
        row_value(t->dots ? "SI" : "NO", y, COL_ACC_INK);
        break;
    case R_RESET:
        row_text("RIPRISTINA I COLORI", "Torna ai colori di Xaos", y);
        break;
    case R_PRESET: {
        const ThemePreset *p = &g_settings.presets[r->arg];
        bool in_use = t->enabled && theme_equal(&p->theme, t);
        char name[PRESET_NAME_LEN];
        snprintf(name, sizeof(name), "%s", p->name);
        dot_text(name, 12, y + 8, 2, in_use ? COL_ACC_INK : COL_INK);
        bool armed = s_delete_armed == r->arg && osGetTime() < s_delete_until;
        ui_text(armed ? "Premi di nuovo X per eliminarlo" : in_use ? "In uso · Y rinomina · X elimina" : "Tocca per usarlo · Y rinomina · X elimina",
                12, y + 23, 0.38f, armed ? COL_ACC_INK : COL_INK3, C2D_AlignLeft);
        /* L'anteprima: sfondo, pannelli, testo, accento. */
        static const ThemeSlot SHOW[4] = { SLOT_BACKGROUND, SLOT_PANELS, SLOT_INK, SLOT_ACCENT };
        for (int k = 0; k < 4; k++) {
            ThemeSlot sl = SHOW[k];
            u32 c = p->theme.has[sl] ? p->theme.color[sl] : slot_fallback(sl);
            swatch(W - 84 + k * 18, y + 13, 14, c, true);
        }
        break;
    }
    case R_SAVE_PRESET:
        row_text("SALVA COME PRESET", "I colori di adesso, con un nome", y);
        row_value("+", y, COL_ACC_INK);
        break;
    case R_DEPTH:
        row_text("PROFONDITA 3D", "Quanto sprofonda lo sfondo col cursore 3D", y);
        row_value(DEPTH_NAMES[g_settings.depth], y, COL_ACC_INK);
        break;
    case R_VIRT:
        row_text("VIRTUALIZZAZIONE", "Allarga la scena stereo, come su Android", y);
        if (g_settings.virt == 0) snprintf(value, sizeof(value), "SPENTA");
        else snprintf(value, sizeof(value), "%d%%", g_settings.virt * 25);
        row_value(value, y, COL_ACC_INK);
        break;
    case R_TAPE:
        row_text("MODALITA CASSETTA", "Bobine e nastro sopra (anche con L+R)", y);
        row_value(g_settings.tape ? "SI" : "NO", y, COL_ACC_INK);
        break;
    case R_INTRO:
        row_text("ANIMAZIONE DI AVVIO", "La X che diventa XAOS, all'apertura", y);
        row_value(g_settings.intro ? "SI" : "NO", y, COL_ACC_INK);
        break;
    case R_HELP:
        row_text("COMANDI", "Tutti i tasti e i gesti", y);
        row_value("APRI", y, COL_ACC_INK);
        break;
    case R_LIBRARY:
        row_text("LIBRERIA", "Tocca per rileggere sdmc:/music", y);
        if (library_scanning()) snprintf(value, sizeof(value), "%d...", library_scan_found());
        else snprintf(value, sizeof(value), "%d BRANI", lib->count);
        row_value(value, y, COL_ACC_INK);
        break;
    default:
        row_text("XAOS PER 3DS", "Senza rete · la musica sta sulla SD", y);
        row_value("1.6.0", y, COL_INK3);
        break;
    }
}

void settings_row_activate(int i) {
    if (i < 0 || i >= s_row_count) return;
    const Row *r = &s_rows[i];
    switch (r->kind) {
    case R_THEME:
        g_settings.dark = !g_settings.dark;
        changed();
        break;
    case R_CUSTOM:
        g_settings.custom.enabled = !g_settings.custom.enabled;
        changed();
        break;
    case R_SLOT:
        open_picker(r->arg);
        break;
    case R_DOTS:
        g_settings.custom.dots = !g_settings.custom.dots;
        changed();
        break;
    case R_RESET: {
        CustomTheme fresh = { .enabled = true, .dots = true };
        g_settings.custom = fresh;
        changed();
        views_toast("COLORI DI XAOS");
        break;
    }
    case R_PRESET:
        g_settings.custom = g_settings.presets[r->arg].theme;
        g_settings.custom.enabled = true;
        changed();
        break;
    case R_SAVE_PRESET:
        save_preset();
        break;
    case R_DEPTH:
        g_settings.depth = (g_settings.depth + 1) % DEPTH_LEVELS;
        settings_save();
        break;
    case R_VIRT:
        g_settings.virt = (g_settings.virt + 1) % VIRT_LEVELS;
        spatial_set_strength(g_settings.virt / 4.0f);
        settings_save();
        break;
    case R_TAPE:
        g_settings.tape = !g_settings.tape;
        settings_save();
        break;
    case R_INTRO:
        g_settings.intro = !g_settings.intro;
        settings_save();
        break;
    case R_HELP:
        s_help = true;
        break;
    case R_LIBRARY:
        if (!library_scanning()) {
            library_rescan();
            views_toast("AGGIORNO LA LIBRERIA");
        }
        break;
    default:
        break;
    }
}

bool settings_row_key(int i, u32 down) {
    if (i < 0 || i >= s_row_count || s_rows[i].kind != R_PRESET) return false;
    int p = s_rows[i].arg;
    if (down & KEY_X) {
        if (s_delete_armed == p && osGetTime() < s_delete_until) {
            memmove(&g_settings.presets[p], &g_settings.presets[p + 1], sizeof(ThemePreset) * (g_settings.preset_count - p - 1));
            g_settings.preset_count--;
            presets_save();
            s_delete_armed = -1;
            views_toast("PRESET ELIMINATO");
        } else {
            s_delete_armed = p;
            s_delete_until = osGetTime() + 2500;
        }
        return true;
    }
    if (down & KEY_Y) {
        char name[PRESET_NAME_LEN];
        if (ask_name("Nuovo nome", g_settings.presets[p].name, name, sizeof(name))) {
            snprintf(g_settings.presets[p].name, PRESET_NAME_LEN, "%s", name);
            presets_save();
        }
        return true;
    }
    return false;
}

/* ------------------------------------------------------------ comandi */

static const char *const HELP[][2] = {
    { "A", "Apre l'album, suona il brano, sceglie" },
    { "B", "Torna indietro" },
    { "X", "Play e pausa" },
    { "Y", "Casuale acceso o spento" },
    { "L  R", "Brano precedente e successivo" },
    { "L  R TENUTI", "Indietro e avanti di 5 secondi" },
    { "L + R INSIEME", "Modalità cassetta, e ritorno" },
    { "ZL  ZR", "Indietro e avanti di 10 secondi (New 3DS)" },
    { "CROCE SU GIU", "Scorre la lista una riga per volta" },
    { "CROCE SX DX", "Cambia scheda; sinistra chiude l'album" },
    { "PAD CIRCOLARE", "Scorre la lista, più veloce se spinto" },
    { "SELECT", "Ripeti: no, tutto, questo brano" },
    { "SELECT TENUTO", "Rilegge la musica sulla SD" },
    { "START", "Esce e ricorda dove eri" },
    { "TOCCO", "Tocca una riga per sceglierla" },
    { "TRASCINA", "Scorre la lista; lanciala per farla correre" },
    { "BARRA DEL TEMPO", "Tocca o trascina per saltare nel brano" },
    { "CURSORE 3D", "Lo sfondo a puntini sprofonda" },
    { "CONSOLE CHIUSA", "Con le cuffie la musica continua" },
    { "HOME", "Torna al menu; la musica si ferma" },
    { "ALL'AVVIO", "Un tasto o un tocco saltano l'animazione" },
};

bool help_active(void) {
    return s_help;
}

void help_close(void) {
    s_help = false;
}

int help_count(void) {
    return sizeof(HELP) / sizeof(HELP[0]);
}

void help_row_draw(int i, float y) {
    dot_text(HELP[i][0], 12, y + 6, 2, COL_ACC_INK);
    ui_text(HELP[i][1], 12, y + 19, 0.42f, COL_INK, C2D_AlignLeft);
}
