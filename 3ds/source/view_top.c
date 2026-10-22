#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "decoder.h"
#include "library.h"
#include "lyrics.h"
#include "player.h"
#include "settings.h"
#include "ui.h"
#include "views.h"

#define MARGIN 16
#define COVER 112
#define COL_X (MARGIN + COVER + 16)
#define COL_W (TOP_W - COL_X - MARGIN)
#define TITLE_Y 152
#define TITLE_PITCH 3
#define LYRIC_BIG 0.6f
#define LYRIC_SMALL 0.5f
#define LYRIC_GAP 6

static Lyrics s_lyrics;
/* Le altezze delle righe a capo, misurate una volta sola (-1 finché non servono). */
static float *s_h_big, *s_h_small;
static char s_lyrics_for[512];
static int s_lyric_index = -1;
/* Lo scorrimento verso la riga nuova, in pixel, che torna a zero. */
static float s_lyric_shift;
static u64 s_shift_frame;

static char s_title_for[512];
static u64 s_title_since;

static void ensure_lyrics(const Song *s) {
    if (!strcmp(s_lyrics_for, s->path)) return;
    lyrics_free(&s_lyrics);
    free(s_h_big);
    free(s_h_small);
    s_h_big = s_h_small = NULL;
    lyrics_load(s->path, &s_lyrics);
    if (s_lyrics.count) {
        s_h_big = malloc(sizeof(float) * s_lyrics.count);
        s_h_small = malloc(sizeof(float) * s_lyrics.count);
        for (int i = 0; i < s_lyrics.count; i++) s_h_big[i] = s_h_small[i] = -1;
    }
    strncpy(s_lyrics_for, s->path, sizeof(s_lyrics_for) - 1);
    s_lyric_index = -1;
    s_lyric_shift = 0;
}

static const char *lyric_text(int i) {
    const char *t = s_lyrics.lines[i].text;
    return *t ? t : "· · ·";
}

static float lyric_height(int i, bool big) {
    float *cache = big ? s_h_big : s_h_small;
    if (cache[i] < 0) cache[i] = ui_text_height(lyric_text(i), big ? LYRIC_BIG : LYRIC_SMALL, COL_W);
    return cache[i];
}

static u32 shown_position(void) {
    u32 ms;
    return views_scrub_preview(&ms) ? ms : player_position_ms();
}

static void draw_header(void) {
    dot_text("XAOS", MARGIN, 10, 2, COL_ACC_INK);
    const char *state = player_is_playing() ? "IN RIPRODUZIONE" : player_has_track() ? "IN PAUSA" : "";
    float w = dot_text_width(state, 2);
    float x = TOP_W - MARGIN - w;
    dot_text(state, x, 10, 2, COL_INK3);
    /* Casuale e ripeti, quando sono accesi. */
    if (player_repeat() != REPEAT_OFF) {
        x -= 22;
        dot_icon(ICON_REPEAT, x, 10, 2, COL_ACC_INK);
        if (player_repeat() == REPEAT_ONE) dot_text("1", x + 15, 8, 1, COL_ACC_INK);
    }
    if (player_shuffle()) {
        x -= 22;
        dot_icon(ICON_SHUFFLE, x, 10, 2, COL_ACC_INK);
    }
}

/* Il titolo a punti scorre se non ci sta: fermo, avanti, fermo, da capo. */
static void draw_title(const char *title) {
    float w = dot_text_width(title, TITLE_PITCH);
    float room = TOP_W - 2 * MARGIN;
    float off = 0;
    if (strcmp(s_title_for, title)) {
        strncpy(s_title_for, title, sizeof(s_title_for) - 1);
        s_title_since = osGetTime();
    }
    if (w > room) {
        float travel = w - room;
        float t = (osGetTime() - s_title_since) / 1000.0f;
        float move = travel / 40.0f;
        float cycle = 2.5f + move + 2.0f;
        float c = t - (int)(t / cycle) * cycle;
        if (c < 2.5f) off = 0;
        else if (c < 2.5f + move) off = (c - 2.5f) * 40.0f;
        else off = travel;
    }
    ui_clip(MARGIN, TITLE_Y - 2, room, DOT_ROWS * TITLE_PITCH + 4);
    dot_text(title, MARGIN - off, TITLE_Y, TITLE_PITCH, COL_INK);
    ui_unclip();
}

static void draw_progress(void) {
    u32 pos = shown_position();
    u32 dur = player_duration_ms();
    float frac = dur ? (float)pos / dur : 0;
    if (frac > 1) frac = 1;
    const float y = 206;
    const float step = 4;
    int dots = (int)((TOP_W - 2 * MARGIN) / step) + 1;
    int lit = (int)(frac * (dots - 1) + 0.5f);
    for (int i = 0; i < dots; i++)
        C2D_DrawRectSolid(MARGIN + i * step, y, 0, 2, 2, i <= lit ? COL_ACCENT : COL_LINE);
    C2D_DrawCircleSolid(MARGIN + lit * step + 1, y + 1, 0, 3.5f, COL_ACCENT);

    char a[16], b[16];
    ui_format_time(a, sizeof(a), pos);
    ui_format_time(b, sizeof(b), dur);
    dot_text(a, MARGIN, 218, 2, views_scrub_preview(NULL) ? COL_INK : COL_INK2);
    dot_text(b, TOP_W - MARGIN - dot_text_width(b, 2), 218, 2, COL_INK3);
}

static void draw_info(const Library *lib, const Song *s) {
    ui_clip(COL_X, 30, COL_W, 112);
    ui_text_wrap(s->album, COL_X, 32, 0.6f, COL_INK, C2D_AlignLeft, COL_W);
    float h = ui_text_height(s->album, 0.6f, COL_W);
    if (h > 36) h = 36;
    ui_text(s->artist, COL_X, 34 + h, 0.5f, COL_INK2, C2D_AlignLeft);
    ui_unclip();

    char line[64];
    const Album *al = s->album_idx >= 0 && s->album_idx < lib->album_count ? &lib->albums[s->album_idx] : NULL;
    if (al) {
        int n = 0;
        for (int k = 0; k < al->count; k++)
            if (&lib->songs[al->songs[k]] == s) n = k + 1;
        if (s->year) snprintf(line, sizeof(line), "BRANO %d DI %d · %d", n, al->count, s->year);
        else snprintf(line, sizeof(line), "BRANO %d DI %d", n, al->count);
        dot_text(line, COL_X, 104, 2, COL_INK3);
    }
    u32 rate = player_rate();
    if (rate) snprintf(line, sizeof(line), "%s · %lu.%lu KHZ", decoder_format_name(s->path), rate / 1000, (rate % 1000) / 100);
    else snprintf(line, sizeof(line), "%s", decoder_format_name(s->path));
    dot_text(line, COL_X, 124, 2, COL_INK3);
}

/* La riga che si canta al centro, quelle intorno più spente. */
static void draw_lyrics(void) {
    u32 pos = shown_position();
    int idx = lyrics_index(&s_lyrics, pos + 150);
    if (idx != s_lyric_index) {
        if (s_lyric_index >= 0 && idx == s_lyric_index + 1) s_lyric_shift += lyric_height(s_lyric_index, true) + LYRIC_GAP;
        else s_lyric_shift = 0;
        s_lyric_index = idx;
    }
    /* Con il 3D lo schermo si disegna due volte: lo scorrimento va avanti una volta per fotogramma. */
    u64 frame = osGetTime() / 16;
    if (frame != s_shift_frame) {
        s_shift_frame = frame;
        s_lyric_shift *= 0.82f;
        if (s_lyric_shift < 0.5f) s_lyric_shift = 0;
    }

    ui_clip(COL_X, 28, COL_W, 118);
    float center = 64 + s_lyric_shift;
    /* Prima la riga corrente e quelle sotto, poi quelle sopra. */
    float y = center;
    for (int i = idx < 0 ? 0 : idx; i < s_lyrics.count && y < 150; i++) {
        bool current = i == idx;
        ui_text_wrap(lyric_text(i), COL_X, y, current ? LYRIC_BIG : LYRIC_SMALL, current ? COL_INK : COL_INK3, C2D_AlignLeft,
                     COL_W);
        y += lyric_height(i, current) + LYRIC_GAP;
    }
    y = center;
    for (int i = idx - 1; i >= 0 && y > 20; i--) {
        y -= lyric_height(i, false) + LYRIC_GAP;
        ui_text_wrap(lyric_text(i), COL_X, y, LYRIC_SMALL, COL_INK3, C2D_AlignLeft, COL_W);
    }
    ui_unclip();
}

static void draw_empty(bool audio_ok) {
    float w = dot_text_width("XAOS", 6);
    dot_text("XAOS", (TOP_W - w) / 2, 70, 6, COL_ACC_INK);
    const Library *lib = library_get();
    char line[96];
    const char *msg;
    if (!audio_ok) {
        msg = "AUDIO NON DISPONIBILE";
    } else if (library_scanning()) {
        snprintf(line, sizeof(line), "CERCO LA MUSICA · %d BRANI", library_scan_found());
        msg = line;
    } else if (lib->count == 0) {
        msg = "NESSUN BRANO";
    } else {
        msg = "SCEGLI COSA ASCOLTARE";
    }
    w = dot_text_width(msg, 2);
    dot_text(msg, (TOP_W - w) / 2, 136, 2, COL_INK2);
    if (!audio_ok) {
        ui_text_wrap("Manca il firmware del DSP. Avvia una volta DSP1 dall'Homebrew Launcher: crea sdmc:/3ds/dspfirm.cdc.",
                     TOP_W / 2, 164, 0.45f, COL_INK3, C2D_AlignCenter, TOP_W - 2 * (MARGIN + 24));
    } else if (lib->count == 0 && !library_scanning()) {
        ui_text_wrap("Copia la musica nella cartella music della SD, poi tieni premuto SELECT per aggiornare.",
                     TOP_W / 2, 164, 0.45f, COL_INK3, C2D_AlignCenter, TOP_W - 2 * (MARGIN + 24));
    }
}

/* ------------------------------------------------------------ modalità cassetta */

/*
 * Ispirata all'OP-1: due bobine che girano, il nastro che passa sulla
 * testina, il tempo in alto e sotto il nastro che scorre con la testina
 * ferma al centro. Le bobine si svuotano e si riempiono con il brano.
 */
#define REEL_Y 100
#define REEL_L 128
#define REEL_R 272
#define REEL_MIN 22.0f
#define REEL_MAX 58.0f
/* La velocità del nastro sulle bobine, in pixel al secondo. */
#define TAPE_SPEED 70.0f
#define STRIP_Y 214
/* Il nastro di sotto: pixel per secondo di musica. */
#define STRIP_PX_PER_S 5.0f

static float s_angle_l, s_angle_r;
static u64 s_reel_time;

static float pack_radius(float fill) {
    /* L'area del nastro avvolto è proporzionale a quanto ne resta. */
    return sqrtf(REEL_MIN * REEL_MIN + (REEL_MAX * REEL_MAX - REEL_MIN * REEL_MIN) * fill);
}

static void draw_reel(float cx, float cy, float r, float angle) {
    /* Il nastro avvolto, con il bordo. */
    C2D_DrawCircleSolid(cx, cy, 0, r + 1.5f, COL_INK3);
    C2D_DrawCircleSolid(cx, cy, 0, r, COL_SURFACE2);
    if (r > REEL_MIN + 8) C2D_DrawCircleSolid(cx, cy, 0, r - 7, COL_SURFACE);
    /* Due segni sul nastro: fanno vedere che gira. */
    for (int k = 0; k < 2; k++) {
        float a = angle + k * (float)M_PI;
        float c = cosf(a), s = sinf(a);
        C2D_DrawLine(cx + c * (r - 9), cy + s * (r - 9), COL_INK2, cx + c * (r - 2), cy + s * (r - 2), COL_INK2, 2, 0);
    }
    /* Il mozzo: anello pieno, foro, tre denti che girano, centro d'accento. */
    C2D_DrawCircleSolid(cx, cy, 0, 16, COL_INK);
    C2D_DrawCircleSolid(cx, cy, 0, 11, COL_BG);
    for (int k = 0; k < 3; k++) {
        float a = angle + k * 2.0944f;
        C2D_DrawCircleSolid(cx + cosf(a) * 10, cy + sinf(a) * 10, 0, 3, COL_INK);
    }
    C2D_DrawCircleSolid(cx, cy, 0, 4, COL_ACCENT);
}

static void draw_roller(float x, float y) {
    C2D_DrawCircleSolid(x, y, 0, 5, COL_INK2);
    C2D_DrawCircleSolid(x, y, 0, 2.5f, COL_BG);
}

/* Il nastro di sotto: scorre verso sinistra, la testina sta ferma al centro. */
static void draw_tape_strip(u32 pos, u32 dur) {
    const float cx = TOP_W / 2;
    const float half_s = (TOP_W / 2 - MARGIN) / STRIP_PX_PER_S;
    float now = pos / 1000.0f;
    float total = dur / 1000.0f;
    float from = now - half_s, to = now + half_s;
    if (from < 0) from = 0;
    if (to > total) to = total;
    float x_from = cx + (from - now) * STRIP_PX_PER_S;
    float x_to = cx + (to - now) * STRIP_PX_PER_S;
    /* Il nastro: già suonato a sinistra della testina, da suonare a destra. */
    if (x_from < cx) C2D_DrawRectSolid(x_from, STRIP_Y - 1, 0, cx - x_from, 3, COL_ACCENT);
    if (x_to > cx) C2D_DrawRectSolid(cx, STRIP_Y, 0, x_to - cx, 1, COL_INK3);
    /* Tacche ogni 5 secondi, più lunghe ai 30 e al minuto, con il minuto scritto. */
    for (int t = ((int)from / 5) * 5; t <= (int)to; t += 5) {
        if (t < from) continue;
        float x = cx + (t - now) * STRIP_PX_PER_S;
        bool minute = t % 60 == 0, half = t % 30 == 0;
        float h = minute ? 12 : half ? 7 : 3;
        u32 c = t <= now ? COL_ACC_INK : COL_INK3;
        C2D_DrawRectSolid(floorf(x), STRIP_Y - h - 2, 0, 1, h, c);
        if (minute && t > 0) {
            char label[8];
            snprintf(label, sizeof(label), "%d", t / 60);
            dot_text(label, floorf(x) + 3, STRIP_Y - 13, 1, c);
        }
    }
    /* Inizio e fine del brano. */
    if (from <= 0.01f) C2D_DrawRectSolid(floorf(x_from), STRIP_Y - 14, 0, 2, 20, COL_INK2);
    if (to >= total - 0.01f) C2D_DrawRectSolid(floorf(x_to) - 1, STRIP_Y - 14, 0, 2, 20, COL_INK2);
    /* La testina. */
    C2D_DrawRectSolid(cx - 1, STRIP_Y - 18, 0, 2, 26, COL_ACCENT);
    C2D_DrawTriangle(cx - 5, STRIP_Y - 22, COL_ACCENT, cx + 5, STRIP_Y - 22, COL_ACCENT, cx, STRIP_Y - 16, COL_ACCENT, 0);
}

static void draw_tape(const Song *s, float eye, float depth) {
    u32 pos = shown_position();
    u32 dur = player_duration_ms();
    float frac = dur ? (float)pos / dur : 0;
    if (frac > 1) frac = 1;
    float rl = pack_radius(1 - frac), rr = pack_radius(frac);

    /* Le bobine girano una volta per fotogramma, anche se lo schermo si disegna per due occhi. */
    u64 now = osGetTime();
    if (now != s_reel_time) {
        float dt = s_reel_time ? (now - s_reel_time) / 1000.0f : 0;
        if (dt > 0.1f) dt = 0.1f;
        s_reel_time = now;
        if (player_is_playing() && !views_scrub_preview(NULL)) {
            s_angle_l += TAPE_SPEED / rl * dt;
            s_angle_r += TAPE_SPEED / rr * dt;
        }
    }

    /* Il tempo, in alto: ore, minuti, secondi. */
    char t[16];
    u32 sec = pos / 1000;
    snprintf(t, sizeof(t), "%lu:%02lu:%02lu", sec / 3600, (sec / 60) % 60, sec % 60);
    dot_text(t, (TOP_W - dot_text_width(t, 3)) / 2, 10, 3, COL_INK);
    /* Il pallino: pieno mentre suona, vuoto in pausa. */
    C2D_DrawCircleSolid(TOP_W - MARGIN - 2, 20, 0, 4.5f, COL_ACCENT);
    if (!player_is_playing()) C2D_DrawCircleSolid(TOP_W - MARGIN - 2, 20, 0, 2.5f, COL_BG);

    /* Con il 3D le bobine e il nastro sporgono un po' verso chi guarda. */
    float dx = -eye * depth * 0.22f;
    float lx = REEL_L + dx, rx = REEL_R + dx;
    /* Il nastro: dalla bobina di sinistra ai rulli, sulla testina, all'altra bobina. */
    float ax = lx + rl * 0.5f, ay = REEL_Y + rl * 0.866f;
    float bx = rx - rr * 0.5f, by = REEL_Y + rr * 0.866f;
    float r1x = 172 + dx, r2x = 228 + dx, ry = 168;
    C2D_DrawLine(ax, ay, COL_INK2, r1x, ry + 5, COL_INK2, 1.5f, 0);
    C2D_DrawLine(r1x, ry + 5, COL_INK2, r2x, ry + 5, COL_INK2, 1.5f, 0);
    C2D_DrawLine(r2x, ry + 5, COL_INK2, bx, by, COL_INK2, 1.5f, 0);
    /* La testina sul nastro, con la sua guida. */
    C2D_DrawRectSolid(TOP_W / 2 + dx - 0.5f, 138, 0, 1, 26, COL_INK3);
    C2D_DrawRectSolid(TOP_W / 2 + dx - 6, ry, 0, 12, 8, COL_INK);
    draw_roller(r1x, ry);
    draw_roller(r2x, ry);
    draw_reel(lx, REEL_Y, rl, s_angle_l);
    draw_reel(rx, REEL_Y, rr, s_angle_r);

    char sub[400];
    snprintf(sub, sizeof(sub), "%s · %s", s->title, s->artist);
    ui_clip(MARGIN, 180, TOP_W - 2 * MARGIN, 16);
    ui_text(sub, TOP_W / 2, 181, 0.42f, COL_INK2, C2D_AlignCenter);
    ui_unclip();
    draw_tape_strip(pos, dur);
}

void view_top_draw(bool audio_ok, float eye, float depth) {
    ui_set_screen(GFX_TOP);
    ui_background(TOP_W, SCREEN_H);
    ui_dot_background(TOP_W, SCREEN_H, eye, depth);
    const Library *lib = library_get();
    int cur = player_current();
    bool has_song = audio_ok && cur >= 0 && cur < lib->count;
    if (has_song && g_settings.tape) {
        draw_tape(&lib->songs[cur], eye, depth);
        return;
    }
    draw_header();
    if (!has_song) {
        draw_empty(audio_ok);
        return;
    }
    const Song *s = &lib->songs[cur];
    ensure_lyrics(s);

    ui_placeholder_cover(s->album, MARGIN, 30, COVER);
    if (s_lyrics.count) draw_lyrics();
    else draw_info(lib, s);

    draw_title(s->title);
    char sub[400];
    snprintf(sub, sizeof(sub), "%s · %s", s->artist, s->album);
    ui_clip(MARGIN, 176, TOP_W - 2 * MARGIN, 22);
    ui_text(sub, MARGIN, 178, 0.5f, COL_INK2, C2D_AlignLeft);
    ui_unclip();
    draw_progress();
}
