#include "decoder.h"

#include <stdlib.h>
#include <string.h>
#include <strings.h>

static const char *ext_of(const char *path) {
    const char *dot = strrchr(path, '.');
    const char *slash = strrchr(path, '/');
    if (!dot || (slash && dot < slash)) return "";
    return dot + 1;
}

static const DecoderVt *vt_for(const char *path) {
    const char *e = ext_of(path);
    if (!strcasecmp(e, "mp3")) return &DEC_MP3;
    if (!strcasecmp(e, "flac")) return &DEC_FLAC;
    if (!strcasecmp(e, "ogg") || !strcasecmp(e, "oga")) return &DEC_VORBIS;
    if (!strcasecmp(e, "opus")) return &DEC_OPUS;
    if (!strcasecmp(e, "wav")) return &DEC_WAV;
    return NULL;
}

const char *decoder_format_name(const char *path) {
    const DecoderVt *vt = vt_for(path);
    if (vt == &DEC_MP3) return "MP3";
    if (vt == &DEC_FLAC) return "FLAC";
    if (vt == &DEC_VORBIS) return "OGG";
    if (vt == &DEC_OPUS) return "OPUS";
    if (vt == &DEC_WAV) return "WAV";
    return "";
}

bool decoder_supported(const char *path) {
    return vt_for(path) != NULL;
}

bool decoder_open(Decoder *d, const char *path) {
    memset(d, 0, sizeof(*d));
    d->vt = vt_for(path);
    if (!d->vt) return false;
    if (!d->vt->open(d, path)) {
        d->vt = NULL;
        return false;
    }
    return true;
}

int decoder_read(Decoder *d, s16 *out, int frames) {
    return d->vt ? d->vt->read(d, out, frames) : -1;
}

bool decoder_seek(Decoder *d, u64 frame) {
    return d->vt ? d->vt->seek(d, frame) : false;
}

void decoder_close(Decoder *d) {
    if (d->vt) d->vt->close(d);
    memset(d, 0, sizeof(*d));
}

/* Il nome del file senza estensione, e quello della cartella. */
static void fallback_names(const char *path, TrackTags *t) {
    if (!t->title[0]) {
        const char *base = strrchr(path, '/');
        base = base ? base + 1 : path;
        const char *dot = strrchr(base, '.');
        size_t n = dot ? (size_t)(dot - base) : strlen(base);
        if (n >= sizeof(t->title)) n = sizeof(t->title) - 1;
        memcpy(t->title, base, n);
        t->title[n] = 0;
    }
    if (!t->album[0]) {
        const char *end = strrchr(path, '/');
        if (end) {
            const char *start = end;
            while (start > path && start[-1] != '/') start--;
            size_t n = end - start;
            if (n >= sizeof(t->album)) n = sizeof(t->album) - 1;
            memcpy(t->album, start, n);
            t->album[n] = 0;
        }
    }
}

bool decoder_read_tags(const char *path, TrackTags *out) {
    memset(out, 0, sizeof(*out));
    const DecoderVt *vt = vt_for(path);
    if (!vt) return false;
    bool ok = vt->tags(path, out);
    fallback_names(path, out);
    return ok;
}

int tag_number(const char *s) {
    return s ? atoi(s) : 0;
}

static void set(char *dst, size_t n, const char *src, int len) {
    if (len < 0) len = strlen(src);
    if ((size_t)len >= n) len = n - 1;
    memcpy(dst, src, len);
    dst[len] = 0;
}

void tags_vorbis_comment(TrackTags *t, const char *c, int len) {
    const char *eq = memchr(c, '=', len);
    if (!eq) return;
    int klen = eq - c;
    const char *v = eq + 1;
    int vlen = len - klen - 1;
    char num[16];
#define KEY(k) (klen == (int)strlen(k) && !strncasecmp(c, k, klen))
    if (KEY("TITLE")) set(t->title, sizeof(t->title), v, vlen);
    else if (KEY("ARTIST") && !t->artist[0]) set(t->artist, sizeof(t->artist), v, vlen);
    else if (KEY("ALBUM")) set(t->album, sizeof(t->album), v, vlen);
    else if (KEY("ALBUMARTIST") || KEY("ALBUM ARTIST")) set(t->album_artist, sizeof(t->album_artist), v, vlen);
    else if (KEY("TRACKNUMBER")) { set(num, sizeof(num), v, vlen); t->track = tag_number(num); }
    else if (KEY("DISCNUMBER")) { set(num, sizeof(num), v, vlen); t->disc = tag_number(num); }
    else if (KEY("DATE") || KEY("YEAR")) { set(num, sizeof(num), v, vlen); t->year = tag_number(num); }
#undef KEY
}
