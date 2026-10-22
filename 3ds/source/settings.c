#include "settings.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "library.h"
#include "ui.h"

#define SETTINGS_PATH DATA_DIR "/settings.txt"
#define THEMES_PATH DATA_DIR "/themes.txt"

const float DEPTH_PX[DEPTH_LEVELS] = { 10, 22, 34, 48 };
const char *const DEPTH_NAMES[DEPTH_LEVELS] = { "LEGGERA", "MEDIA", "FORTE", "ESTREMA" };

Settings g_settings = {
    .dark = true,
    .custom = { .dots = true },
    .depth = 2,
    .virt = 0,
    .intro = true,
};

static const char *const SLOT_NAMES[SLOT_COUNT] = { "SFONDO", "SFUMATURA", "PANNELLI", "TESTO", "ACCENTO" };
static const char *const SLOT_HINTS[SLOT_COUNT] = {
    "Il fondo di tutta l'app",
    "Il secondo colore dello sfondo, in diagonale",
    "Righe selezionate, riquadri e avvisi",
    "Testo e icone; le tonalità tenui se ne ricavano",
    "Play, pallini d'accento, selezioni",
};
static const char *const SLOT_KEYS[SLOT_COUNT] = { "background", "background2", "panels", "ink", "accent" };

const char *slot_name(ThemeSlot slot) {
    return SLOT_NAMES[slot];
}

const char *slot_hint(ThemeSlot slot) {
    return SLOT_HINTS[slot];
}

u32 slot_fallback(ThemeSlot slot) {
    /* I colori del tema di base, con il tema personalizzato spento. */
    ui_set_palette(g_settings.dark, NULL);
    u32 c;
    switch (slot) {
    case SLOT_BACKGROUND:
    case SLOT_BACKGROUND2: c = COL_BG; break;
    case SLOT_PANELS: c = COL_SURFACE; break;
    case SLOT_INK: c = COL_INK; break;
    default: c = COL_ACCENT; break;
    }
    if (slot == SLOT_BACKGROUND2 && g_settings.custom.has[SLOT_BACKGROUND])
        c = argb_to_c2d(g_settings.custom.color[SLOT_BACKGROUND]);
    settings_apply_theme();
    return c2d_to_argb(c);
}

void settings_apply_theme(void) {
    ui_set_palette(g_settings.dark, &g_settings.custom);
}

bool theme_equal(const CustomTheme *a, const CustomTheme *b) {
    if (a->dots != b->dots) return false;
    for (int i = 0; i < SLOT_COUNT; i++) {
        if (a->has[i] != b->has[i]) return false;
        if (a->has[i] && (a->color[i] & 0xFFFFFF) != (b->color[i] & 0xFFFFFF)) return false;
    }
    return true;
}

/* ------------------------------------------------------------ file */

static void trim(char *s) {
    size_t n = strlen(s);
    while (n && (s[n - 1] == '\n' || s[n - 1] == '\r' || s[n - 1] == ' ')) s[--n] = 0;
}

/* "FF112233" o "-" (nessun colore scelto). */
static void parse_color(const char *v, bool *has, u32 *color) {
    if (!v || *v == '-' || !*v) {
        *has = false;
        return;
    }
    *has = true;
    *color = (u32)strtoul(v, NULL, 16);
    if (strlen(v) <= 6) *color |= 0xFF000000;
}

static void write_color(FILE *f, bool has, u32 color) {
    if (has) fprintf(f, "%08lX", color);
    else fputc('-', f);
}

void settings_load(void) {
    FILE *f = fopen(SETTINGS_PATH, "r");
    if (f) {
        char line[128];
        while (fgets(line, sizeof(line), f)) {
            trim(line);
            char *eq = strchr(line, '=');
            if (!eq) continue;
            *eq = 0;
            const char *k = line, *v = eq + 1;
            if (!strcmp(k, "dark")) g_settings.dark = atoi(v) != 0;
            else if (!strcmp(k, "custom")) g_settings.custom.enabled = atoi(v) != 0;
            else if (!strcmp(k, "dots")) g_settings.custom.dots = atoi(v) != 0;
            else if (!strcmp(k, "depth")) g_settings.depth = atoi(v) % DEPTH_LEVELS;
            else if (!strcmp(k, "virt")) g_settings.virt = atoi(v) % VIRT_LEVELS;
            else if (!strcmp(k, "tape")) g_settings.tape = atoi(v) != 0;
            else if (!strcmp(k, "intro")) g_settings.intro = atoi(v) != 0;
            else
                for (int i = 0; i < SLOT_COUNT; i++)
                    if (!strcmp(k, SLOT_KEYS[i])) parse_color(v, &g_settings.custom.has[i], &g_settings.custom.color[i]);
        }
        fclose(f);
    }

    /* I preset: nome, poi i cinque colori e i pallini, separati da tabulazioni. */
    f = fopen(THEMES_PATH, "r");
    if (f) {
        char line[256];
        g_settings.preset_count = 0;
        while (g_settings.preset_count < MAX_PRESETS && fgets(line, sizeof(line), f)) {
            trim(line);
            if (!line[0] || line[0] == '#') continue;
            char *fields[8];
            int n = 0;
            for (char *p = line; n < 8;) {
                fields[n++] = p;
                char *tab = strchr(p, '\t');
                if (!tab) break;
                *tab = 0;
                p = tab + 1;
            }
            if (n < 7) continue;
            ThemePreset *pr = &g_settings.presets[g_settings.preset_count++];
            memset(pr, 0, sizeof(*pr));
            strncpy(pr->name, fields[0], PRESET_NAME_LEN - 1);
            pr->theme.enabled = true;
            for (int i = 0; i < SLOT_COUNT; i++) parse_color(fields[1 + i], &pr->theme.has[i], &pr->theme.color[i]);
            pr->theme.dots = atoi(fields[6]) != 0;
        }
        fclose(f);
    }
    settings_apply_theme();
}

void settings_save(void) {
    FILE *f = fopen(SETTINGS_PATH, "w");
    if (!f) return;
    const Settings *s = &g_settings;
    fprintf(f, "dark=%d\ncustom=%d\ndots=%d\ndepth=%d\nvirt=%d\ntape=%d\nintro=%d\n", s->dark, s->custom.enabled,
            s->custom.dots, s->depth, s->virt, s->tape, s->intro);
    for (int i = 0; i < SLOT_COUNT; i++) {
        fprintf(f, "%s=", SLOT_KEYS[i]);
        write_color(f, s->custom.has[i], s->custom.color[i]);
        fputc('\n', f);
    }
    fclose(f);
}

void presets_save(void) {
    FILE *f = fopen(THEMES_PATH, "w");
    if (!f) return;
    fputs("# Preset dei temi di Xaos: nome, sfondo, sfumatura, pannelli, testo, accento (ARGB o -), pallini\n", f);
    for (int p = 0; p < g_settings.preset_count; p++) {
        const ThemePreset *pr = &g_settings.presets[p];
        fputs(pr->name, f);
        for (int i = 0; i < SLOT_COUNT; i++) {
            fputc('\t', f);
            write_color(f, pr->theme.has[i], pr->theme.color[i]);
        }
        fprintf(f, "\t%d\n", pr->theme.dots);
    }
    fclose(f);
}
