#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "library.h"
#include "player.h"
#include "settings.h"
#include "ui.h"
#include "view_settings.h"
#include "views.h"

#define W BOTTOM_W
#define TAB_H 28
/* L'ingranaggio delle impostazioni, a destra delle schede. */
#define GEAR_W 32
/* La barra del tempo, trascinabile, e sotto i comandi. */
#define BAR_Y 190
#define SCRUB_H 16
#define TRACK_X0 46
#define TRACK_X1 (W - 46)
#define BUTTONS_Y 223
#define HEADER_H 40
#define ROW_H 32
#define ALBUM_ROW_H 40
#define TOUCH_SLOP 8

typedef enum { TAB_ALBUMS, TAB_SONGS, TAB_QUEUE, TAB_SETTINGS, TAB_COUNT } Tab;
enum { LIST_ALBUMS, LIST_SONGS, LIST_QUEUE, LIST_ALBUM, LIST_SETTINGS, LIST_HELP, LIST_COUNT };

typedef struct {
    float scroll;
    float vel;
    int sel;
} List;

static const char *const TAB_NAMES[3] = { "ALBUM", "BRANI", "CODA" };

static Tab s_tab = TAB_ALBUMS;
static int s_album = -1;
static List s_lists[LIST_COUNT];

/* Il dito sullo schermo. */
static bool s_touching;
static bool s_dragging;
static int s_tx0, s_ty0, s_tx, s_ty;
static float s_drag_vel;
static int s_pressed = -1;

/* La barra del tempo trascinata: il punto si mostra subito, il salto si fa lasciando. */
static bool s_scrubbing;
static u32 s_scrub_ms;

/* I tasti tenuti premuti: L e R saltano dentro il brano, SELECT aggiorna. */
static u64 s_l_since, s_r_since, s_select_since;
static bool s_l_scrubbed, s_r_scrubbed, s_select_long;
/* L e R premuti insieme: la modalità cassetta, senza saltare di brano. */
static bool s_combo;
static u64 s_last_scrub;

static char s_toast[96];
static u64 s_toast_until;

void views_toast(const char *text) {
    strncpy(s_toast, text, sizeof(s_toast) - 1);
    s_toast[sizeof(s_toast) - 1] = 0;
    s_toast_until = osGetTime() + 1800;
}

bool views_scrub_preview(u32 *ms) {
    if (!s_scrubbing) return false;
    if (ms) *ms = s_scrub_ms;
    return true;
}

/* ------------------------------------------------------------ liste */

static int list_id(void) {
    switch (s_tab) {
    case TAB_ALBUMS: return s_album >= 0 ? LIST_ALBUM : LIST_ALBUMS;
    case TAB_SONGS: return LIST_SONGS;
    case TAB_QUEUE: return LIST_QUEUE;
    default: return help_active() ? LIST_HELP : LIST_SETTINGS;
    }
}

static List *cur_list(void) {
    return &s_lists[list_id()];
}

static int list_count(void) {
    const Library *lib = library_get();
    switch (list_id()) {
    case LIST_ALBUMS: return lib->album_count;
    case LIST_SONGS: return lib->count;
    case LIST_QUEUE: return player_queue_count();
    case LIST_SETTINGS: return settings_count();
    case LIST_HELP: return help_count();
    default: return s_album >= 0 && s_album < lib->album_count ? lib->albums[s_album].count : 0;
    }
}

static int row_h(void) {
    switch (list_id()) {
    case LIST_ALBUMS: return ALBUM_ROW_H;
    case LIST_SETTINGS: return SETTINGS_ROW_H;
    case LIST_HELP: return HELP_ROW_H;
    default: return ROW_H;
    }
}

static bool has_header(void) {
    return list_id() == LIST_ALBUM || list_id() == LIST_HELP;
}

static float list_top(void) {
    return has_header() ? TAB_H + HEADER_H : TAB_H;
}

static float list_height(void) {
    return BAR_Y - list_top();
}

static float max_scroll(void) {
    float m = list_count() * row_h() - list_height();
    return m > 0 ? m : 0;
}

static void clamp(List *l) {
    float m = max_scroll();
    if (l->scroll < 0) {
        l->scroll = 0;
        l->vel = 0;
    }
    if (l->scroll > m) {
        l->scroll = m;
        l->vel = 0;
    }
}

static void reveal(List *l, int i) {
    float top = i * row_h();
    if (top < l->scroll) l->scroll = top;
    if (top + row_h() > l->scroll + list_height()) l->scroll = top + row_h() - list_height();
    clamp(l);
}

static void center_on(List *l, int i) {
    l->sel = i;
    l->scroll = i * row_h() - (list_height() - row_h()) / 2;
    l->vel = 0;
    clamp(l);
}

static void move_sel(int d) {
    List *l = cur_list();
    int n = list_count();
    if (!n) return;
    int first = (int)(l->scroll / row_h());
    int last = (int)((l->scroll + list_height()) / row_h()) - 1;
    /* Dopo aver scorso col dito la selezione può essere fuori vista: riparte da lì. */
    if (l->sel < first || l->sel > last) l->sel = d > 0 ? first : (last < n ? last : n - 1);
    else l->sel += d;
    if (l->sel < 0) l->sel = 0;
    if (l->sel >= n) l->sel = n - 1;
    l->vel = 0;
    reveal(l, l->sel);
}

static void switch_tab(Tab t) {
    s_tab = t;
    s_album = -1;
    help_close();
    if (t == TAB_QUEUE && player_queue_pos() >= 0) center_on(&s_lists[LIST_QUEUE], player_queue_pos());
}

static void open_album(int a) {
    s_album = a;
    memset(&s_lists[LIST_ALBUM], 0, sizeof(List));
}

static void rescan(void) {
    if (!library_scanning()) {
        library_rescan();
        views_toast("AGGIORNO LA LIBRERIA");
    }
}

static void toggle_tape(void) {
    g_settings.tape = !g_settings.tape;
    settings_save();
    views_toast(g_settings.tape ? "MODALITA CASSETTA" : "MODALITA NORMALE");
}

static void activate(int i) {
    const Library *lib = library_get();
    if (i < 0 || i >= list_count()) return;
    switch (list_id()) {
    case LIST_ALBUMS:
        s_lists[LIST_ALBUMS].sel = i;
        open_album(i);
        break;
    case LIST_ALBUM:
        player_play(lib->albums[s_album].songs, lib->albums[s_album].count, i);
        break;
    case LIST_SONGS:
        player_play(lib->by_title, lib->count, i);
        break;
    case LIST_QUEUE:
        player_jump(i);
        break;
    case LIST_SETTINGS:
        settings_row_activate(i);
        break;
    }
}

void views_library_changed(void) {
    /* Gli album si rinumerano: meglio chiuderne il dettaglio. */
    s_album = -1;
    for (int i = 0; i < LIST_COUNT; i++) {
        if (i == LIST_SETTINGS || i == LIST_HELP) continue;
        s_lists[i].vel = 0;
        s_lists[i].sel = 0;
        s_lists[i].scroll = 0;
    }
}

/* ------------------------------------------------------------ comandi */

static float bar_x(int i) {
    return 40 + i * 60;
}

static int bar_button_at(int x, int y) {
    if (y < BAR_Y + SCRUB_H) return -1;
    for (int i = 0; i < 5; i++)
        if (fabsf(x - bar_x(i)) < 30) return i;
    return -1;
}

static void press_bar(int i) {
    char msg[48];
    switch (i) {
    case 0:
        player_set_shuffle(!player_shuffle());
        views_toast(player_shuffle() ? "CASUALE ACCESO" : "CASUALE SPENTO");
        break;
    case 1: player_prev(); break;
    case 2: player_toggle(); break;
    case 3: player_next(); break;
    case 4:
        player_cycle_repeat();
        snprintf(msg, sizeof(msg), "RIPETI: %s",
                 player_repeat() == REPEAT_OFF ? "NO" : player_repeat() == REPEAT_ALL ? "TUTTO" : "QUESTO BRANO");
        views_toast(msg);
        break;
    }
}

static u32 scrub_at(int x) {
    float f = (float)(x - TRACK_X0) / (TRACK_X1 - TRACK_X0);
    if (f < 0) f = 0;
    if (f > 1) f = 1;
    return (u32)(f * player_duration_ms());
}

static void tap(int x, int y) {
    if (y < TAB_H) {
        if (x >= W - GEAR_W) {
            switch_tab(TAB_SETTINGS);
        } else {
            int t = x * 3 / (W - GEAR_W);
            switch_tab((Tab)(t > 2 ? 2 : t));
        }
        return;
    }
    if (y >= BAR_Y) {
        int b = bar_button_at(x, y);
        if (b >= 0) press_bar(b);
        return;
    }
    if (list_id() == LIST_ALBUM && y < TAB_H + HEADER_H) {
        if (x < 44) s_album = -1;
        else activate(0);
        return;
    }
    if (list_id() == LIST_HELP && y < TAB_H + HEADER_H) {
        help_close();
        return;
    }
    List *l = cur_list();
    int i = (int)((y - list_top() + l->scroll) / row_h());
    if (i >= 0 && i < list_count()) {
        l->sel = i;
        activate(i);
    }
}

/* L e R: premuti saltano di brano, tenuti scorrono dentro il brano. */
static void shoulder(u32 down, u32 held, u32 up, u32 key, u64 *since, bool *scrubbed, int dir) {
    u64 now = osGetTime();
    if (down & key) {
        *since = now;
        *scrubbed = false;
    }
    if ((held & key) && *since && now - *since > 400 && now - s_last_scrub > 200) {
        player_seek_by(dir * 5000);
        s_last_scrub = now;
        *scrubbed = true;
    }
    if (up & key) {
        if (*since && !*scrubbed) {
            if (dir > 0) player_next();
            else player_prev();
        }
        *since = 0;
    }
}

void view_bottom_input(u32 down, u32 held, u32 up, const touchPosition *touch, const circlePosition *pad) {
    /* Il selettore di colore prende tutto lo schermo e tutti i tasti. */
    if (picker_active()) {
        picker_input(down, held, up, touch);
        return;
    }
    u32 rep = hidKeysDownRepeat();
    List *l = cur_list();

    if (rep & KEY_DUP) move_sel(-1);
    if (rep & KEY_DDOWN) move_sel(1);
    if (down & KEY_DLEFT) {
        if (s_album >= 0) s_album = -1;
        else switch_tab((s_tab + TAB_COUNT - 1) % TAB_COUNT);
    }
    if (down & KEY_DRIGHT) switch_tab((s_tab + 1) % TAB_COUNT);
    if (down & KEY_A) activate(l->sel);
    if (down & KEY_B) {
        if (s_album >= 0) s_album = -1;
        else if (help_active()) help_close();
    }
    /* Su un preset X e Y eliminano e rinominano; altrove sono play e casuale. */
    bool preset_key = list_id() == LIST_SETTINGS && (down & (KEY_X | KEY_Y)) && settings_row_key(l->sel, down);
    if (!preset_key && (down & KEY_X)) player_toggle();
    if (!preset_key && (down & KEY_Y)) press_bar(0);
    if (down & KEY_ZL) player_seek_by(-10000);
    if (down & KEY_ZR) player_seek_by(10000);

    shoulder(down, held, up, KEY_L, &s_l_since, &s_l_scrubbed, -1);
    shoulder(down, held, up, KEY_R, &s_r_since, &s_r_scrubbed, 1);
    /* L e R insieme: niente salti di brano, si cambia modalità. */
    if ((held & KEY_L) && (held & KEY_R) && (down & (KEY_L | KEY_R)) && !s_combo) {
        s_combo = true;
        toggle_tape();
    }
    if (s_combo) {
        s_l_since = s_r_since = 0;
        if (!(held & (KEY_L | KEY_R))) s_combo = false;
    }

    /* SELECT: premuto cambia la ripetizione, tenuto aggiorna la libreria. */
    u64 now = osGetTime();
    if (down & KEY_SELECT) {
        s_select_since = now;
        s_select_long = false;
    }
    if ((held & KEY_SELECT) && s_select_since && !s_select_long && now - s_select_since > 1000) {
        s_select_long = true;
        rescan();
    }
    if (up & KEY_SELECT) {
        if (s_select_since && !s_select_long) press_bar(4);
        s_select_since = 0;
    }

    /* Il pad circolare scorre la lista, più veloce quanto più è spinto. */
    if (abs(pad->dy) > 20) {
        float v = pad->dy / 156.0f;
        l->scroll -= v * fabsf(v) * 18;
        l->vel = 0;
        clamp(l);
    }

    if (down & KEY_TOUCH) {
        s_touching = true;
        s_dragging = false;
        s_tx0 = s_tx = touch->px;
        s_ty0 = s_ty = touch->py;
        s_drag_vel = 0;
        l->vel = 0;
        s_pressed = bar_button_at(touch->px, touch->py);
        /* Sulla barra del tempo: si trascina il punto. */
        if (touch->py >= BAR_Y && touch->py < BAR_Y + SCRUB_H && player_has_track() && player_duration_ms()) {
            s_scrubbing = true;
            s_scrub_ms = scrub_at(touch->px);
        }
    } else if ((held & KEY_TOUCH) && s_touching) {
        if (s_scrubbing) {
            s_scrub_ms = scrub_at(touch->px);
        } else {
            int dy = touch->py - s_ty;
            bool in_list = s_ty0 >= list_top() && s_ty0 < BAR_Y;
            if (!s_dragging && in_list && abs(touch->py - s_ty0) > TOUCH_SLOP) s_dragging = true;
            if (s_dragging) {
                l->scroll -= dy;
                s_drag_vel = s_drag_vel * 0.5f - dy * 0.5f;
                clamp(l);
            }
        }
        s_tx = touch->px;
        s_ty = touch->py;
    } else if ((up & KEY_TOUCH) && s_touching) {
        s_touching = false;
        s_pressed = -1;
        if (s_scrubbing) {
            s_scrubbing = false;
            player_seek(s_scrub_ms);
        } else if (s_dragging) {
            l->vel = s_drag_vel;
        } else {
            tap(s_tx0, s_ty0);
        }
    }

    /* L'inerzia dopo aver lanciato la lista col dito. */
    if (!s_touching && l->vel != 0) {
        l->scroll += l->vel;
        l->vel *= 0.94f;
        if (fabsf(l->vel) < 0.2f) l->vel = 0;
        clamp(l);
    }
}

/* ------------------------------------------------------------ disegno */

static void draw_tabs(void) {
    float col = (float)(W - GEAR_W) / 3;
    for (int t = 0; t < 3; t++) {
        float w = dot_text_width(TAB_NAMES[t], 2);
        float x = t * col + (col - w) / 2;
        bool on = t == (int)s_tab;
        dot_text(TAB_NAMES[t], x, 7, 2, on ? COL_INK : COL_INK3);
        if (on)
            for (float d = x; d < x + w; d += 4) C2D_DrawRectSolid(d, 23, 0, 2, 2, COL_ACCENT);
    }
    bool on = s_tab == TAB_SETTINGS;
    float gx = W - GEAR_W + (GEAR_W - 14) / 2;
    dot_icon(ICON_GEAR, gx, 7, 2, on ? COL_INK : COL_INK3);
    if (on)
        for (float d = gx; d < gx + 14; d += 4) C2D_DrawRectSolid(d, 23, 0, 2, 2, COL_ACCENT);
    C2D_DrawRectSolid(0, TAB_H - 1, 0, W, 1, COL_LINE);
}


/* Il brano in riproduzione ha un punto dell'accento a sinistra (sul chiaro il giallo come testo non si legge). */
static void current_mark(float y, float h) {
    C2D_DrawCircleSolid(5, y + h / 2, 0, 2.5f, COL_ACCENT);
}

static void draw_song_row(const Song *s, int song, float y, bool selected, bool dim, const char *number) {
    if (selected) C2D_DrawRectSolid(0, y, 0, W, ROW_H, COL_SURFACE);
    bool current = song == player_current();
    float x = 12;
    if (number) {
        ui_text(number, 12, y + 9, 0.42f, current ? COL_ACC_INK : COL_INK3, C2D_AlignLeft);
        x = 38;
    }
    u32 ink = current ? COL_ACC_INK : dim ? COL_INK3 : COL_INK;
    /* Il testo lungo si taglia prima della durata. */
    ui_clip_within(x, y, W - 56 - x, ROW_H);
    ui_text(s->title, x, y + 3, 0.5f, ink, C2D_AlignLeft);
    char sub[340];
    if (number) snprintf(sub, sizeof(sub), "%s", s->artist);
    else snprintf(sub, sizeof(sub), "%s · %s", s->artist, s->album);
    ui_text(sub, x, y + 18, 0.4f, dim ? COL_INK3 : COL_INK2, C2D_AlignLeft);
    ui_clip_restore();
    char d[16];
    ui_format_time(d, sizeof(d), s->duration_ms);
    ui_text(d, W - 10, y + 10, 0.42f, COL_INK3, C2D_AlignRight);
    if (current) current_mark(y, ROW_H);
}

static void draw_album_row(const Album *al, int a, float y, bool selected) {
    if (selected) C2D_DrawRectSolid(0, y, 0, W, ALBUM_ROW_H, COL_SURFACE);
    ui_placeholder_cover(al->title, 10, y + 4, 32);
    int cur = player_current();
    bool playing_here = cur >= 0 && library_get()->songs[cur].album_idx == a;
    ui_clip_within(52, y, W - 60, ALBUM_ROW_H);
    ui_text(al->title, 52, y + 5, 0.5f, playing_here ? COL_ACC_INK : COL_INK, C2D_AlignLeft);
    char sub[200];
    if (al->year) snprintf(sub, sizeof(sub), "%s · %d", al->artist, al->year);
    else snprintf(sub, sizeof(sub), "%s", al->artist);
    ui_text(sub, 52, y + 21, 0.4f, COL_INK2, C2D_AlignLeft);
    ui_clip_restore();
    if (playing_here) current_mark(y, ALBUM_ROW_H);
}

static void draw_album_header(const Album *al) {
    float y = TAB_H;
    dot_icon(ICON_BACK, 14, y + 13, 2, COL_INK2);
    ui_clip(44, y, W - 52, HEADER_H);
    ui_text(al->title, 44, y + 4, 0.55f, COL_INK, C2D_AlignLeft);
    char sub[200];
    snprintf(sub, sizeof(sub), "%s · %d brani · %lu min", al->artist, al->count, (al->duration_ms + 30000) / 60000);
    ui_text(sub, 44, y + 22, 0.4f, COL_INK2, C2D_AlignLeft);
    ui_unclip();
    C2D_DrawRectSolid(0, y + HEADER_H - 1, 0, W, 1, COL_LINE);
}

static void draw_help_header(void) {
    float y = TAB_H;
    dot_icon(ICON_BACK, 14, y + 13, 2, COL_INK2);
    dot_text("COMANDI", 44, y + 8, 2, COL_INK);
    ui_text("Tasti, tocchi e gesti di Xaos", 44, y + 23, 0.4f, COL_INK2, C2D_AlignLeft);
    C2D_DrawRectSolid(0, y + HEADER_H - 1, 0, W, 1, COL_LINE);
}

static void draw_list(void) {
    const Library *lib = library_get();
    List *l = cur_list();
    int n = list_count();
    int id = list_id();
    float top = list_top();
    float h = list_height();
    int rh = row_h();

    ui_clip_base(0, top, W, h);
    int first = (int)(l->scroll / rh);
    int queue_pos = player_queue_pos();
    for (int i = first; i < n; i++) {
        float y = top + i * rh - l->scroll;
        if (y >= BAR_Y) break;
        bool sel = i == l->sel;
        if (id == LIST_ALBUMS) {
            draw_album_row(&lib->albums[i], i, y, sel);
        } else if (id == LIST_SONGS) {
            int s = lib->by_title[i];
            draw_song_row(&lib->songs[s], s, y, sel, false, NULL);
        } else if (id == LIST_QUEUE) {
            int s = player_queue_song(i);
            if (s >= 0) draw_song_row(&lib->songs[s], i == queue_pos ? s : -2, y, sel, i < queue_pos, NULL);
        } else if (id == LIST_SETTINGS) {
            settings_row_draw(i, y, sel);
        } else if (id == LIST_HELP) {
            help_row_draw(i, y);
        } else {
            const Album *al = &lib->albums[s_album];
            int s = al->songs[i];
            char num[8];
            if (lib->songs[s].track) snprintf(num, sizeof(num), "%d", lib->songs[s].track);
            else snprintf(num, sizeof(num), "%d", i + 1);
            draw_song_row(&lib->songs[s], s, y, sel, false, num);
        }
    }
    ui_clip_end();

    /* La barra di scorrimento, sottile, solo se la lista non ci sta. */
    float content = (float)n * rh;
    if (content > h) {
        float th = h * h / content;
        if (th < 12) th = 12;
        float ty = top + (l->scroll / (content - h)) * (h - th);
        C2D_DrawRectSolid(W - 3, ty, 0, 2, th, COL_INK3);
    }
}

static void draw_empty(void) {
    const Library *lib = library_get();
    const char *title;
    char line[96] = "";
    if (list_id() == LIST_QUEUE && lib->count) {
        title = "CODA VUOTA";
        snprintf(line, sizeof(line), "Scegli un album o un brano.");
    } else if (library_scanning()) {
        title = "CERCO LA MUSICA";
        char folder[64];
        library_scan_folder(folder, sizeof(folder));
        snprintf(line, sizeof(line), "%d brani · %s", library_scan_found(), folder);
    } else {
        title = "NESSUN BRANO";
        snprintf(line, sizeof(line), "Metti la musica in sdmc:/music");
    }
    float w = dot_text_width(title, 2);
    dot_text(title, (W - w) / 2, 86, 2, COL_INK2);
    ui_text(line, W / 2, 108, 0.45f, COL_INK3, C2D_AlignCenter);
}

/* La barra del tempo: un filo di punti, pieni fin dove si è arrivati. */
static void draw_scrubber(void) {
    C2D_DrawRectSolid(0, BAR_Y, 0, W, 1, COL_LINE);
    u32 dur = player_duration_ms();
    u32 pos = s_scrubbing ? s_scrub_ms : player_position_ms();
    float frac = dur ? (float)pos / dur : 0;
    if (frac > 1) frac = 1;
    const float y = BAR_Y + SCRUB_H / 2;
    float head = TRACK_X0 + frac * (TRACK_X1 - TRACK_X0);
    for (float x = TRACK_X0; x <= TRACK_X1; x += 4) C2D_DrawRectSolid(x, y - 1, 0, 2, 2, x <= head ? COL_ACCENT : COL_LINE);
    if (player_has_track()) C2D_DrawCircleSolid(head + 1, y, 0, s_scrubbing ? 6 : 3.5f, COL_ACCENT);
    char a[16], b[16];
    ui_format_time(a, sizeof(a), pos);
    ui_format_time(b, sizeof(b), dur);
    ui_text(a, TRACK_X0 - 8, y - 6, 0.38f, s_scrubbing ? COL_INK : COL_INK3, C2D_AlignRight);
    ui_text(b, TRACK_X1 + 8, y - 6, 0.38f, COL_INK3, C2D_AlignLeft);
}

static void draw_bar(void) {
    draw_scrubber();
    const float cy = BUTTONS_Y;
    for (int i = 0; i < 5; i++) {
        if (i == s_pressed && i != 2) C2D_DrawCircleSolid(bar_x(i), cy, 0, 16, COL_SURFACE);
    }
    C2D_DrawCircleSolid(bar_x(2), cy, 0, s_pressed == 2 ? 14 : 15, COL_ACCENT);
    dot_icon(ICON_SHUFFLE, bar_x(0) - 7, cy - 7, 2, player_shuffle() ? COL_ACC_INK : COL_INK3);
    dot_icon(ICON_PREV, bar_x(1) - 7, cy - 7, 2, COL_INK);
    dot_icon(player_is_playing() ? ICON_PAUSE : ICON_PLAY, bar_x(2) - 7, cy - 7, 2, COL_ON_ACC);
    dot_icon(ICON_NEXT, bar_x(3) - 7, cy - 7, 2, COL_INK);
    dot_icon(ICON_REPEAT, bar_x(4) - 7, cy - 7, 2, player_repeat() != REPEAT_OFF ? COL_ACC_INK : COL_INK3);
    if (player_repeat() == REPEAT_ONE) dot_text("1", bar_x(4) + 8, cy - 12, 1, COL_ACC_INK);
}

static void draw_toast(void) {
    if (osGetTime() > s_toast_until) return;
    float w = dot_text_width(s_toast, 2) + 20;
    float x = (W - w) / 2;
    C2D_DrawRectSolid(x, BAR_Y - 30, 0, w, 24, COL_SURFACE2);
    dot_text(s_toast, x + 10, BAR_Y - 25, 2, COL_INK);
}

void view_bottom_draw(void) {
    ui_set_screen(GFX_BOTTOM);
    ui_background(W, SCREEN_H);
    ui_dot_background(W, SCREEN_H, 0, 0);
    if (picker_active()) {
        picker_draw();
        return;
    }
    const Library *lib = library_get();
    if (list_id() == LIST_ALBUM && s_album >= lib->album_count) s_album = -1;
    draw_tabs();
    if (list_id() == LIST_ALBUM) draw_album_header(&lib->albums[s_album]);
    if (list_id() == LIST_HELP) draw_help_header();
    if (list_count() == 0) draw_empty();
    else draw_list();
    draw_bar();
    draw_toast();
}
