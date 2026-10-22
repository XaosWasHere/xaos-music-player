#include "decoder.h"

#include <opus/opusfile.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <tremor/ivorbisfile.h>

/* ------------------------------------------------------------ Ogg Vorbis (Tremor, a virgola fissa) */

typedef struct {
    OggVorbis_File vf;
    int channels;
    s16 *tmp;
    int tmp_frames;
} Vorbis;

static bool vorbis_open(Decoder *d, const char *path) {
    FILE *file = fopen(path, "rb");
    if (!file) return false;
    Vorbis *v = calloc(1, sizeof(Vorbis));
    if (!v) {
        fclose(file);
        return false;
    }
    /* Se va a buon fine il file lo chiude ov_clear. */
    if (ov_open(file, &v->vf, NULL, 0) < 0) {
        fclose(file);
        free(v);
        return false;
    }
    vorbis_info *vi = ov_info(&v->vf, -1);
    v->channels = vi->channels;
    d->impl = v;
    d->rate = vi->rate;
    ogg_int64_t total = ov_pcm_total(&v->vf, -1);
    d->total_frames = total > 0 ? (u64)total : 0;
    return true;
}

/* Legge fino a bytes byte di campioni nel formato del file. */
static int vorbis_fill(Vorbis *v, char *dst, int bytes) {
    int got = 0;
    while (got < bytes) {
        int bitstream;
        long r = ov_read(&v->vf, dst + got, bytes - got, &bitstream);
        if (r == 0) break;
        if (r == OV_HOLE) continue;
        if (r < 0) return got > 0 ? got : -1;
        got += r;
    }
    return got;
}

static int vorbis_read(Decoder *d, s16 *out, int frames) {
    Vorbis *v = d->impl;
    if (v->channels == 2) {
        int got = vorbis_fill(v, (char *)out, frames * 4);
        return got < 0 ? -1 : got / 4;
    }
    if (v->tmp_frames < frames) {
        s16 *grown = realloc(v->tmp, (size_t)frames * v->channels * sizeof(s16));
        if (!grown) return -1;
        v->tmp = grown;
        v->tmp_frames = frames;
    }
    int bytes = vorbis_fill(v, (char *)v->tmp, frames * v->channels * 2);
    if (bytes < 0) return -1;
    int n = bytes / (v->channels * 2);
    for (int i = 0; i < n; i++) {
        s16 l = v->tmp[i * v->channels];
        s16 r = v->channels > 1 ? v->tmp[i * v->channels + 1] : l;
        out[i * 2] = l;
        out[i * 2 + 1] = r;
    }
    return n;
}

static bool vorbis_seek(Decoder *d, u64 frame) {
    Vorbis *v = d->impl;
    return ov_pcm_seek(&v->vf, (ogg_int64_t)frame) == 0;
}

static void vorbis_close(Decoder *d) {
    Vorbis *v = d->impl;
    if (!v) return;
    ov_clear(&v->vf);
    free(v->tmp);
    free(v);
    d->impl = NULL;
}

static bool vorbis_tags(const char *path, TrackTags *t) {
    Decoder d = {0};
    if (!vorbis_open(&d, path)) return false;
    Vorbis *v = d.impl;
    vorbis_comment *vc = ov_comment(&v->vf, -1);
    if (vc) {
        for (int i = 0; i < vc->comments; i++)
            tags_vorbis_comment(t, vc->user_comments[i], vc->comment_lengths[i]);
    }
    if (d.rate && d.total_frames) t->duration_ms = (u32)(d.total_frames * 1000 / d.rate);
    vorbis_close(&d);
    return true;
}

const DecoderVt DEC_VORBIS = { vorbis_open, vorbis_read, vorbis_seek, vorbis_close, vorbis_tags };

/* ------------------------------------------------------------ Opus (sempre 48 kHz) */

static bool opus_open(Decoder *d, const char *path) {
    int err = 0;
    OggOpusFile *of = op_open_file(path, &err);
    if (!of) return false;
    d->impl = of;
    d->rate = 48000;
    ogg_int64_t total = op_pcm_total(of, -1);
    d->total_frames = total > 0 ? (u64)total : 0;
    return true;
}

static int opus_read(Decoder *d, s16 *out, int frames) {
    int got = 0;
    while (got < frames) {
        int r = op_read_stereo(d->impl, out + got * 2, (frames - got) * 2);
        if (r == 0) break;
        if (r == OP_HOLE) continue;
        if (r < 0) return got > 0 ? got : -1;
        got += r;
    }
    return got;
}

static bool opus_seek(Decoder *d, u64 frame) {
    return op_pcm_seek(d->impl, (ogg_int64_t)frame) == 0;
}

static void opus_close(Decoder *d) {
    if (d->impl) op_free(d->impl);
    d->impl = NULL;
}

static bool opus_tags(const char *path, TrackTags *t) {
    Decoder d = {0};
    if (!opus_open(&d, path)) return false;
    const OpusTags *tags = op_tags(d.impl, -1);
    if (tags) {
        for (int i = 0; i < tags->comments; i++)
            tags_vorbis_comment(t, tags->user_comments[i], tags->comment_lengths[i]);
    }
    if (d.total_frames) t->duration_ms = (u32)(d.total_frames * 1000 / 48000);
    opus_close(&d);
    return true;
}

const DecoderVt DEC_OPUS = { opus_open, opus_read, opus_seek, opus_close, opus_tags };
