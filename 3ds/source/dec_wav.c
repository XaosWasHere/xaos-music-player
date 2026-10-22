#include "decoder.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* WAV PCM a 16 o 24 bit, mono o stereo. */
typedef struct {
    FILE *f;
    u32 data_start;
    u32 data_size;
    int channels;
    int bytes_per_sample;
    u8 *tmp;
    int tmp_frames;
} Wav;

static u32 le32(const u8 *p) { return p[0] | p[1] << 8 | p[2] << 16 | (u32)p[3] << 24; }
static u16 le16(const u8 *p) { return p[0] | p[1] << 8; }

static bool wav_parse(FILE *f, Wav *w, u32 *rate) {
    u8 h[12];
    if (fread(h, 1, 12, f) != 12 || memcmp(h, "RIFF", 4) || memcmp(h + 8, "WAVE", 4)) return false;
    bool fmt = false;
    for (;;) {
        u8 c[8];
        if (fread(c, 1, 8, f) != 8) return false;
        u32 size = le32(c + 4);
        if (!memcmp(c, "fmt ", 4)) {
            u8 b[16];
            if (size < 16 || fread(b, 1, 16, f) != 16) return false;
            u16 format = le16(b);
            w->channels = le16(b + 2);
            *rate = le32(b + 4);
            int bits = le16(b + 14);
            if ((format != 1 && format != 0xFFFE) || (bits != 16 && bits != 24) || w->channels < 1) return false;
            w->bytes_per_sample = bits / 8;
            fmt = true;
            fseek(f, size - 16 + (size & 1), SEEK_CUR);
        } else if (!memcmp(c, "data", 4)) {
            if (!fmt) return false;
            w->data_start = ftell(f);
            w->data_size = size;
            return true;
        } else {
            fseek(f, size + (size & 1), SEEK_CUR);
        }
    }
}

static bool wav_open(Decoder *d, const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return false;
    Wav *w = calloc(1, sizeof(Wav));
    u32 rate = 0;
    if (!w || !wav_parse(f, w, &rate)) {
        fclose(f);
        free(w);
        return false;
    }
    w->f = f;
    d->impl = w;
    d->rate = rate;
    d->total_frames = w->data_size / (w->channels * w->bytes_per_sample);
    return true;
}

static int wav_read(Decoder *d, s16 *out, int frames) {
    Wav *w = d->impl;
    int frame_bytes = w->channels * w->bytes_per_sample;
    u32 pos = ftell(w->f) - w->data_start;
    u32 left = (w->data_size - pos) / frame_bytes;
    if ((u32)frames > left) frames = left;
    if (frames <= 0) return 0;
    if (w->tmp_frames < frames) {
        u8 *grown = realloc(w->tmp, (size_t)frames * frame_bytes);
        if (!grown) return -1;
        w->tmp = grown;
        w->tmp_frames = frames;
    }
    int n = fread(w->tmp, frame_bytes, frames, w->f);
    for (int i = 0; i < n; i++) {
        const u8 *p = w->tmp + i * frame_bytes;
        s16 l, r;
        if (w->bytes_per_sample == 2) {
            l = (s16)le16(p);
            r = w->channels > 1 ? (s16)le16(p + 2) : l;
        } else {
            l = (s16)(p[1] | p[2] << 8);
            r = w->channels > 1 ? (s16)(p[4] | p[5] << 8) : l;
        }
        out[i * 2] = l;
        out[i * 2 + 1] = r;
    }
    return n;
}

static bool wav_seek(Decoder *d, u64 frame) {
    Wav *w = d->impl;
    u64 off = frame * w->channels * w->bytes_per_sample;
    if (off > w->data_size) off = w->data_size;
    return fseek(w->f, w->data_start + (long)off, SEEK_SET) == 0;
}

static void wav_close(Decoder *d) {
    Wav *w = d->impl;
    if (!w) return;
    fclose(w->f);
    free(w->tmp);
    free(w);
    d->impl = NULL;
}

static bool wav_tags(const char *path, TrackTags *t) {
    Decoder d = {0};
    if (!wav_open(&d, path)) return false;
    if (d.rate) t->duration_ms = (u32)(d.total_frames * 1000 / d.rate);
    wav_close(&d);
    return true;
}

const DecoderVt DEC_WAV = { wav_open, wav_read, wav_seek, wav_close, wav_tags };
