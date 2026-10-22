#pragma once

#include <3ds.h>
#include <stdbool.h>

/*
 * Le impostazioni, in sdmc:/3ds/xaos/settings.txt, e i preset dei temi in
 * sdmc:/3ds/xaos/themes.txt (lo stesso formato che esporta tools/export_themes.py
 * dai preset di Xaos per PC).
 */

/* I colori sono ARGB come su desktop (0xAARRGGBB); has_* dice se sono scelti. */
typedef enum { SLOT_BACKGROUND, SLOT_BACKGROUND2, SLOT_PANELS, SLOT_INK, SLOT_ACCENT, SLOT_COUNT } ThemeSlot;

typedef struct {
    bool enabled;
    bool has[SLOT_COUNT];
    u32 color[SLOT_COUNT];
    bool dots;
} CustomTheme;

#define PRESET_NAME_LEN 32
#define MAX_PRESETS 24

typedef struct {
    char name[PRESET_NAME_LEN];
    CustomTheme theme;
} ThemePreset;

/* Profondità dei puntini con il 3D al massimo, in pixel per livello. */
#define DEPTH_LEVELS 4
extern const float DEPTH_PX[DEPTH_LEVELS];
extern const char *const DEPTH_NAMES[DEPTH_LEVELS];

/* Intensità della virtualizzazione, in quarti: 0 spenta, 4 piena. */
#define VIRT_LEVELS 5

typedef struct {
    bool dark;
    CustomTheme custom;
    int depth;
    int virt;
    bool tape;
    /* L'animazione di avvio. */
    bool intro;
    ThemePreset presets[MAX_PRESETS];
    int preset_count;
} Settings;

extern Settings g_settings;

void settings_load(void);
void settings_save(void);
void presets_save(void);

/* La tavolozza dalle impostazioni (tema chiaro/scuro e colori scelti). */
void settings_apply_theme(void);

const char *slot_name(ThemeSlot slot);
const char *slot_hint(ThemeSlot slot);
/* Il colore che il tema userebbe senza scelte, per partire nel selettore. */
u32 slot_fallback(ThemeSlot slot);

/* Due temi uguali (per segnare il preset in uso). */
bool theme_equal(const CustomTheme *a, const CustomTheme *b);
