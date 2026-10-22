#include "player.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "audio.h"

#define STATE_PATH DATA_DIR "/state.bin"
#define STATE_MAGIC "XAOSST01"
#define PENDING 8

/* La coda: i brani nell'ordine in cui sono stati scelti, e l'ordine di ascolto. */
static int *s_items;
static int s_count;
static int *s_order;
static int s_pos = -1;

static bool s_shuffle;
static RepeatMode s_repeat;

/* Ogni brano mandato al motore audio ha un numero: si sa così quale si sente. */
static u32 s_serials;
static u32 s_cur_serial;
static struct {
    u32 serial;
    int pos;
} s_pending[PENDING];
static int s_pending_next;

/* Cosa vuole l'utente: musica o pausa. */
static bool s_want_play;
static bool s_has_track;
static u32 s_last_failed;
static int s_failures;
/* Il brano parte da qui quando il motore non sa ancora dove sia (ripresa all'avvio). */
static u32 s_resume_ms;

static AudioStatus s_st;

static int song_at(int pos) {
    return s_items[s_order[pos]];
}

static const char *path_at(int pos) {
    return library_get()->songs[song_at(pos)].path;
}

static void remember(u32 serial, int pos) {
    s_pending[s_pending_next].serial = serial;
    s_pending[s_pending_next].pos = pos;
    s_pending_next = (s_pending_next + 1) % PENDING;
}

static int lookup(u32 serial) {
    for (int i = 0; i < PENDING; i++)
        if (s_pending[i].serial == serial) return s_pending[i].pos;
    return -1;
}

/* Il brano che segue da solo: ripeti uno lo rifà, ripeti tutto ricomincia. */
static int auto_next(int pos) {
    if (s_repeat == REPEAT_ONE) return pos;
    if (pos + 1 < s_count) return pos + 1;
    if (s_repeat == REPEAT_ALL && s_count > 0) return 0;
    return -1;
}

static void schedule_next(void) {
    int n = s_pos >= 0 ? auto_next(s_pos) : -1;
    if (n < 0) {
        audio_set_next(NULL, 0);
        return;
    }
    u32 serial = ++s_serials;
    remember(serial, n);
    audio_set_next(path_at(n), serial);
}

static void start(int pos, u32 start_ms) {
    if (pos < 0 || pos >= s_count) return;
    s_pos = pos;
    s_cur_serial = ++s_serials;
    remember(s_cur_serial, pos);
    s_has_track = true;
    s_resume_ms = start_ms;
    audio_set_paused(!s_want_play);
    audio_play(path_at(pos), s_cur_serial, start_ms);
    schedule_next();
}

static void shuffle_order(int first_item) {
    for (int i = 0; i < s_count; i++) s_order[i] = i;
    for (int i = s_count - 1; i > 0; i--) {
        int j = rand() % (i + 1);
        int t = s_order[i];
        s_order[i] = s_order[j];
        s_order[j] = t;
    }
    /* Il brano scelto resta il primo. */
    for (int i = 0; i < s_count; i++) {
        if (s_order[i] == first_item) {
            s_order[i] = s_order[0];
            s_order[0] = first_item;
            break;
        }
    }
}

static bool set_queue(const int *songs, int count) {
    int *items = malloc(sizeof(int) * (count ? count : 1));
    int *order = malloc(sizeof(int) * (count ? count : 1));
    if (!items || !order) {
        free(items);
        free(order);
        return false;
    }
    memcpy(items, songs, sizeof(int) * count);
    free(s_items);
    free(s_order);
    s_items = items;
    s_order = order;
    s_count = count;
    return true;
}

void player_init(void) {
    srand(osGetTime());
}

void player_exit(void) {
    free(s_items);
    free(s_order);
    s_items = s_order = NULL;
    s_count = 0;
}

void player_play(const int *songs, int count, int start_at) {
    if (count <= 0 || !set_queue(songs, count)) return;
    if (start_at < 0 || start_at >= count) start_at = 0;
    if (s_shuffle) {
        shuffle_order(start_at);
        s_pos = 0;
    } else {
        for (int i = 0; i < count; i++) s_order[i] = i;
        s_pos = start_at;
    }
    s_want_play = true;
    s_failures = 0;
    start(s_pos, 0);
}

void player_toggle(void) {
    if (!s_has_track || s_count == 0) return;
    if (!s_st.active) {
        /* Finita la coda: si riparte dal brano mostrato. */
        s_want_play = true;
        start(s_pos, 0);
        return;
    }
    s_want_play = !s_want_play;
    audio_set_paused(!s_want_play);
}

void player_pause(void) {
    if (!s_want_play) return;
    s_want_play = false;
    audio_set_paused(true);
}

void player_next(void) {
    if (!s_has_track || s_count == 0) return;
    int n = s_pos + 1;
    if (n >= s_count) {
        /* Oltre l'ultimo: si torna al primo, fermi se non si ripete. */
        n = 0;
        if (s_repeat == REPEAT_OFF) s_want_play = false;
    }
    start(n, 0);
}

void player_prev(void) {
    if (!s_has_track || s_count == 0) return;
    if (player_position_ms() > 3000) {
        player_seek(0);
        return;
    }
    int p = s_pos - 1;
    if (p < 0) p = s_repeat == REPEAT_ALL ? s_count - 1 : 0;
    start(p, 0);
}

void player_seek(u32 ms) {
    if (!s_has_track) return;
    if (!s_st.active) {
        start(s_pos, ms);
        return;
    }
    audio_seek(ms);
}

void player_seek_by(int delta_ms) {
    s32 target = (s32)player_position_ms() + delta_ms;
    if (target < 0) target = 0;
    u32 dur = player_duration_ms();
    if (dur && (u32)target >= dur) target = dur > 1000 ? dur - 1000 : 0;
    player_seek((u32)target);
}

void player_jump(int pos) {
    if (pos < 0 || pos >= s_count) return;
    s_want_play = true;
    start(pos, 0);
}

void player_set_shuffle(bool on) {
    if (on == s_shuffle) return;
    s_shuffle = on;
    if (s_count == 0 || s_pos < 0) return;
    int current = s_order[s_pos];
    if (on) {
        shuffle_order(current);
        s_pos = 0;
    } else {
        for (int i = 0; i < s_count; i++) s_order[i] = i;
        s_pos = current;
    }
    /* Il brano dopo non è più lo stesso. */
    remember(s_cur_serial, s_pos);
    schedule_next();
}

bool player_shuffle(void) {
    return s_shuffle;
}

void player_cycle_repeat(void) {
    s_repeat = (s_repeat + 1) % 3;
    if (s_has_track) schedule_next();
}

RepeatMode player_repeat(void) {
    return s_repeat;
}

void player_update(void) {
    audio_status(&s_st);
    if (!s_has_track) return;

    /* Un brano che non si apre si salta; se nessuno si apre ci si ferma. */
    if (s_st.failed_serial && s_st.failed_serial != s_last_failed) {
        s_last_failed = s_st.failed_serial;
        if (s_st.failed_serial == s_cur_serial) {
            if (++s_failures < s_count && s_pos + 1 < s_count) {
                start(s_pos + 1, 0);
            } else {
                s_want_play = false;
                audio_stop();
            }
            return;
        }
    }

    /* Il motore è passato da solo al brano dopo. */
    if (s_st.serial != s_cur_serial) {
        int p = lookup(s_st.serial);
        if (p >= 0 && p < s_count) {
            s_pos = p;
            s_cur_serial = s_st.serial;
            s_failures = 0;
            s_resume_ms = 0;
            schedule_next();
        }
    }

    /* Finita la coda. */
    if (!s_st.active && s_want_play && s_st.serial == s_cur_serial) s_want_play = false;
}

void player_remap(const Library *old_lib, const Library *new_lib) {
    if (!s_count) return;
    int *map = malloc(sizeof(int) * s_count);
    int kept = 0;
    for (int i = 0; i < s_count; i++) {
        int found = library_find(new_lib, old_lib->songs[s_items[i]].path);
        map[i] = found >= 0 ? kept : -1;
        if (found >= 0) s_items[kept++] = found;
    }
    int current = s_pos >= 0 ? s_order[s_pos] : -1;
    int n = 0, new_pos = 0;
    for (int i = 0; i < s_count; i++) {
        int m = map[s_order[i]];
        if (m < 0) continue;
        if (s_order[i] == current) new_pos = n;
        s_order[n++] = m;
    }
    free(map);
    s_count = kept;
    s_pos = s_count ? new_pos : -1;
    if (!s_count) {
        s_has_track = false;
        audio_stop();
    }
}

int player_current(void) {
    return s_has_track && s_pos >= 0 && s_pos < s_count ? song_at(s_pos) : -1;
}

bool player_is_playing(void) {
    return s_has_track && s_want_play && s_st.active;
}

bool player_has_track(void) {
    return s_has_track && s_pos >= 0;
}

u32 player_position_ms(void) {
    if (!s_has_track) return 0;
    if (s_st.serial == s_cur_serial) return s_st.position_ms;
    return s_resume_ms;
}

u32 player_duration_ms(void) {
    if (s_st.serial == s_cur_serial && s_st.duration_ms) return s_st.duration_ms;
    int song = player_current();
    return song >= 0 ? library_get()->songs[song].duration_ms : 0;
}

u32 player_rate(void) {
    return s_st.serial == s_cur_serial ? s_st.rate : 0;
}

int player_queue_count(void) {
    return s_count;
}

int player_queue_song(int pos) {
    return pos >= 0 && pos < s_count ? song_at(pos) : -1;
}

int player_queue_pos(void) {
    return s_pos;
}

/* ------------------------------------------------------------ stato salvato */

static void put_str(FILE *f, const char *s) {
    u16 n = (u16)strlen(s);
    fwrite(&n, 2, 1, f);
    fwrite(s, 1, n, f);
}

void player_save(void) {
    const Library *lib = library_get();
    FILE *f = fopen(STATE_PATH, "wb");
    if (!f) return;
    static char vbuf[16 * 1024];
    setvbuf(f, vbuf, _IOFBF, sizeof(vbuf));
    fwrite(STATE_MAGIC, 1, 8, f);
    u8 flags[2] = { s_shuffle, (u8)s_repeat };
    fwrite(flags, 1, 2, f);
    u32 pos_ms = player_position_ms();
    s32 pos = s_has_track ? s_pos : -1;
    u32 count = s_has_track ? s_count : 0;
    fwrite(&pos_ms, 4, 1, f);
    fwrite(&pos, 4, 1, f);
    fwrite(&count, 4, 1, f);
    for (u32 i = 0; i < count; i++) put_str(f, lib->songs[s_items[i]].path);
    for (u32 i = 0; i < count; i++) fwrite(&s_order[i], 4, 1, f);
    fclose(f);
}

void player_restore(void) {
    const Library *lib = library_get();
    FILE *f = fopen(STATE_PATH, "rb");
    if (!f) return;
    static char vbuf[16 * 1024];
    setvbuf(f, vbuf, _IOFBF, sizeof(vbuf));
    char magic[8];
    u8 flags[2];
    u32 pos_ms, count;
    s32 pos;
    if (fread(magic, 1, 8, f) != 8 || memcmp(magic, STATE_MAGIC, 8) || fread(flags, 1, 2, f) != 2 ||
        fread(&pos_ms, 4, 1, f) != 1 || fread(&pos, 4, 1, f) != 1 || fread(&count, 4, 1, f) != 1 || count > 200000) {
        fclose(f);
        return;
    }
    s_shuffle = flags[0];
    s_repeat = flags[1] % 3;
    if (count == 0 || pos < 0 || (u32)pos >= count) {
        fclose(f);
        return;
    }
    int *items = malloc(sizeof(int) * count);
    int *map = malloc(sizeof(int) * count);
    int *order = malloc(sizeof(int) * count);
    int kept = 0;
    char path[1024];
    bool ok = items && map && order;
    for (u32 i = 0; ok && i < count; i++) {
        u16 n;
        if (fread(&n, 2, 1, f) != 1 || n >= sizeof(path) || fread(path, 1, n, f) != n) {
            ok = false;
            break;
        }
        path[n] = 0;
        int found = library_find(lib, path);
        map[i] = found >= 0 ? kept : -1;
        if (found >= 0) items[kept++] = found;
    }
    for (u32 i = 0; ok && i < count; i++)
        if (fread(&order[i], 4, 1, f) != 1 || order[i] < 0 || (u32)order[i] >= count) ok = false;
    fclose(f);

    if (ok && kept > 0 && map[order[pos]] >= 0) {
        int current = map[order[pos]];
        int n = 0, new_pos = 0;
        for (u32 i = 0; i < count; i++) {
            int m = map[order[i]];
            if (m < 0) continue;
            if (m == current) new_pos = n;
            order[n++] = m;
        }
        if (set_queue(items, kept)) {
            memcpy(s_order, order, sizeof(int) * kept);
            /* Si ritrova il brano dov'era, in pausa. */
            s_want_play = false;
            start(new_pos, pos_ms);
        }
    }
    free(items);
    free(map);
    free(order);
}
