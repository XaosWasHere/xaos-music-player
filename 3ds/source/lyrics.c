#include "lyrics.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAX_LRC_BYTES (256 * 1024)

static int by_time(const void *a, const void *b) {
    const LyricLine *x = a, *y = b;
    return x->ms < y->ms ? -1 : x->ms > y->ms;
}

static void add(Lyrics *l, int *cap, u32 ms, const char *text, size_t n) {
    if (l->count == *cap) {
        *cap = *cap ? *cap * 2 : 64;
        LyricLine *grown = realloc(l->lines, sizeof(LyricLine) * *cap);
        if (!grown) return;
        l->lines = grown;
    }
    char *t = malloc(n + 1);
    memcpy(t, text, n);
    t[n] = 0;
    l->lines[l->count].ms = ms;
    l->lines[l->count].text = t;
    l->count++;
}

/* "[01:23.45]" -> ms; false se non è un tempo (es. "[ar:Artista]"). */
static bool parse_time(const char *s, size_t n, u32 *out) {
    int min = 0, i = 0;
    while (i < (int)n && isdigit((u8)s[i])) min = min * 10 + (s[i++] - '0');
    if (i == 0 || i >= (int)n || s[i] != ':') return false;
    i++;
    int sec = 0, digits = 0;
    while (i < (int)n && isdigit((u8)s[i])) {
        sec = sec * 10 + (s[i++] - '0');
        digits++;
    }
    if (!digits) return false;
    int frac = 0, scale = 1000;
    if (i < (int)n && (s[i] == '.' || s[i] == ':')) {
        i++;
        while (i < (int)n && isdigit((u8)s[i])) {
            scale /= 10;
            frac += (s[i++] - '0') * scale;
            if (scale == 1) break;
        }
    }
    *out = (u32)((min * 60 + sec) * 1000 + frac);
    return true;
}

bool lyrics_load(const char *audio_path, Lyrics *out) {
    out->lines = NULL;
    out->count = 0;
    char path[600];
    strncpy(path, audio_path, sizeof(path) - 5);
    path[sizeof(path) - 5] = 0;
    char *dot = strrchr(path, '.');
    char *slash = strrchr(path, '/');
    if (dot && (!slash || dot > slash)) *dot = 0;
    strcat(path, ".lrc");

    FILE *f = fopen(path, "rb");
    if (!f) return false;
    char *buf = malloc(MAX_LRC_BYTES + 1);
    size_t len = fread(buf, 1, MAX_LRC_BYTES, f);
    fclose(f);
    buf[len] = 0;

    char *p = buf;
    if (len >= 3 && (u8)p[0] == 0xEF && (u8)p[1] == 0xBB && (u8)p[2] == 0xBF) p += 3;
    int cap = 0;
    long offset = 0;
    while (*p) {
        char *end = strpbrk(p, "\r\n");
        size_t n = end ? (size_t)(end - p) : strlen(p);
        /* Una riga può avere più tempi davanti: [00:12.00][01:30.00]testo */
        u32 times[16];
        int nt = 0;
        size_t i = 0;
        while (i < n && p[i] == '[') {
            char *close = memchr(p + i, ']', n - i);
            if (!close) break;
            size_t tag = close - (p + i) - 1;
            u32 ms;
            if (parse_time(p + i + 1, tag, &ms)) {
                if (nt < 16) times[nt++] = ms;
            } else if (tag > 7 && !strncmp(p + i + 1, "offset:", 7)) {
                offset = strtol(p + i + 8, NULL, 10);
            }
            i = close - p + 1;
        }
        const char *text = p + i;
        size_t tn = n - i;
        while (tn && (*text == ' ' || *text == '\t')) text++, tn--;
        while (tn && (text[tn - 1] == ' ' || text[tn - 1] == '\t')) tn--;
        for (int k = 0; k < nt; k++) add(out, &cap, times[k], text, tn);
        p += n;
        while (*p == '\r' || *p == '\n') p++;
    }
    free(buf);
    if (!out->count) return false;
    /* Un offset positivo fa comparire le righe prima. */
    for (int k = 0; k < out->count; k++) {
        long ms = (long)out->lines[k].ms - offset;
        out->lines[k].ms = ms < 0 ? 0 : (u32)ms;
    }
    qsort(out->lines, out->count, sizeof(LyricLine), by_time);
    return true;
}

void lyrics_free(Lyrics *l) {
    for (int i = 0; i < l->count; i++) free(l->lines[i].text);
    free(l->lines);
    l->lines = NULL;
    l->count = 0;
}

int lyrics_index(const Lyrics *l, u32 ms) {
    int lo = 0, hi = l->count - 1, best = -1;
    while (lo <= hi) {
        int mid = (lo + hi) / 2;
        if (l->lines[mid].ms <= ms) {
            best = mid;
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
    return best;
}
