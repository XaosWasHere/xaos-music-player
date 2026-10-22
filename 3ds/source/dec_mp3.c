#include "decoder.h"

#include <mpg123.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static mpg123_handle *new_handle(void) {
    int err = 0;
    mpg123_handle *mh = mpg123_new(NULL, &err);
    if (!mh) return NULL;
    /* Sempre stereo: i brani mono li raddoppia mpg123. */
    mpg123_param(mh, MPG123_ADD_FLAGS, MPG123_QUIET | MPG123_FORCE_STEREO, 0);
    return mh;
}

static bool mp3_open(Decoder *d, const char *path) {
    mpg123_handle *mh = new_handle();
    if (!mh) return false;
    long rate;
    int channels, enc;
    if (mpg123_open(mh, path) != MPG123_OK || mpg123_getformat(mh, &rate, &channels, &enc) != MPG123_OK) {
        mpg123_delete(mh);
        return false;
    }
    mpg123_format_none(mh);
    mpg123_format(mh, rate, MPG123_STEREO, MPG123_ENC_SIGNED_16);
    d->impl = mh;
    d->rate = rate;
    off_t len = mpg123_length(mh);
    d->total_frames = len > 0 ? (u64)len : 0;
    return true;
}

static int mp3_read(Decoder *d, s16 *out, int frames) {
    mpg123_handle *mh = d->impl;
    for (int attempt = 0; attempt < 8; attempt++) {
        size_t done = 0;
        int r = mpg123_read(mh, (unsigned char *)out, (size_t)frames * 4, &done);
        if (done > 0) return (int)(done / 4);
        if (r == MPG123_DONE) return 0;
        if (r == MPG123_NEW_FORMAT || r == MPG123_OK) continue;
        return -1;
    }
    return 0;
}

static bool mp3_seek(Decoder *d, u64 frame) {
    return mpg123_seek(d->impl, (off_t)frame, SEEK_SET) >= 0;
}

static void mp3_close(Decoder *d) {
    if (!d->impl) return;
    mpg123_close(d->impl);
    mpg123_delete(d->impl);
    d->impl = NULL;
}

static void copy_str(char *dst, size_t n, const mpg123_string *s) {
    if (!s || !s->p || !s->fill) return;
    size_t len = strnlen(s->p, s->fill);
    if (len >= n) len = n - 1;
    memcpy(dst, s->p, len);
    dst[len] = 0;
}

/* I campi ID3v1 sono a lunghezza fissa e non sempre terminati. */
static void copy_fixed(char *dst, size_t n, const char *src, size_t len) {
    size_t i = 0;
    while (i < len && src[i]) i++;
    while (i > 0 && src[i - 1] == ' ') i--;
    if (i >= n) i = n - 1;
    memcpy(dst, src, i);
    dst[i] = 0;
}

static bool mp3_tags(const char *path, TrackTags *t) {
    mpg123_handle *mh = new_handle();
    if (!mh) return false;
    long rate = 0;
    int channels, enc;
    if (mpg123_open(mh, path) != MPG123_OK || mpg123_getformat(mh, &rate, &channels, &enc) != MPG123_OK) {
        mpg123_delete(mh);
        return false;
    }
    mpg123_id3v1 *v1 = NULL;
    mpg123_id3v2 *v2 = NULL;
    if (mpg123_id3(mh, &v1, &v2) == MPG123_OK) {
        if (v2) {
            copy_str(t->title, sizeof(t->title), v2->title);
            copy_str(t->artist, sizeof(t->artist), v2->artist);
            copy_str(t->album, sizeof(t->album), v2->album);
            if (v2->year && v2->year->p) t->year = atoi(v2->year->p);
            for (size_t i = 0; i < v2->texts; i++) {
                const mpg123_text *x = &v2->text[i];
                char num[16] = {0};
                if (!memcmp(x->id, "TRCK", 4)) { copy_str(num, sizeof(num), &x->text); t->track = tag_number(num); }
                else if (!memcmp(x->id, "TPOS", 4)) { copy_str(num, sizeof(num), &x->text); t->disc = tag_number(num); }
                else if (!memcmp(x->id, "TPE2", 4)) copy_str(t->album_artist, sizeof(t->album_artist), &x->text);
                else if (!t->year && (!memcmp(x->id, "TDRC", 4) || !memcmp(x->id, "TYER", 4))) {
                    copy_str(num, sizeof(num), &x->text);
                    t->year = atoi(num);
                }
            }
        }
        if (v1) {
            if (!t->title[0]) copy_fixed(t->title, sizeof(t->title), v1->title, sizeof(v1->title));
            if (!t->artist[0]) copy_fixed(t->artist, sizeof(t->artist), v1->artist, sizeof(v1->artist));
            if (!t->album[0]) copy_fixed(t->album, sizeof(t->album), v1->album, sizeof(v1->album));
            if (!t->year) {
                char y[5] = {0};
                memcpy(y, v1->year, 4);
                t->year = atoi(y);
            }
        }
    }
    off_t len = mpg123_length(mh);
    if (len > 0 && rate > 0) t->duration_ms = (u32)((u64)len * 1000 / rate);
    mpg123_close(mh);
    mpg123_delete(mh);
    return true;
}

const DecoderVt DEC_MP3 = { mp3_open, mp3_read, mp3_seek, mp3_close, mp3_tags };
