#include "decoder.h"

#include <FLAC/metadata.h>
#include <FLAC/stream_decoder.h>
#include <stdlib.h>
#include <string.h>

/*
 * FLAC fino a 24 bit. Sopra i 48 kHz (hi-res) si dimezza la frequenza qui,
 * mediando i campioni a coppie: il 3DS esce comunque a 32 kHz, e il DSP non
 * deve ricampionare da 96 o 192 kHz.
 */
typedef struct {
    FLAC__StreamDecoder *dec;
    s16 *buf;
    int cap;
    int len;
    int pos;
    u32 src_rate;
    int decim;
    u64 total;
} Flac;

static FLAC__StreamDecoderWriteStatus on_write(const FLAC__StreamDecoder *dec, const FLAC__Frame *frame,
                                               const FLAC__int32 *const buffer[], void *client) {
    (void)dec;
    Flac *f = client;
    int n = frame->header.blocksize;
    int ch = frame->header.channels;
    int bps = frame->header.bits_per_sample;
    if (n > f->cap) {
        s16 *grown = realloc(f->buf, (size_t)n * 2 * sizeof(s16));
        if (!grown) return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        f->buf = grown;
        f->cap = n;
    }
    int shift = bps - 16;
    const FLAC__int32 *l = buffer[0];
    const FLAC__int32 *r = ch > 1 ? buffer[1] : buffer[0];
    int decim = f->decim;
    int out = 0;
    for (int i = 0; i + decim <= n; i += decim) {
        s32 sl = 0, sr = 0;
        for (int k = 0; k < decim; k++) {
            sl += l[i + k];
            sr += r[i + k];
        }
        sl /= decim;
        sr /= decim;
        if (shift > 0) {
            sl >>= shift;
            sr >>= shift;
        } else if (shift < 0) {
            sl <<= -shift;
            sr <<= -shift;
        }
        f->buf[out * 2] = (s16)sl;
        f->buf[out * 2 + 1] = (s16)sr;
        out++;
    }
    f->len = out;
    f->pos = 0;
    return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}

static void on_metadata(const FLAC__StreamDecoder *dec, const FLAC__StreamMetadata *m, void *client) {
    (void)dec;
    Flac *f = client;
    if (m->type == FLAC__METADATA_TYPE_STREAMINFO) {
        f->src_rate = m->data.stream_info.sample_rate;
        f->total = m->data.stream_info.total_samples;
    }
}

static void on_error(const FLAC__StreamDecoder *dec, FLAC__StreamDecoderErrorStatus status, void *client) {
    (void)dec;
    (void)status;
    (void)client;
}

static bool flac_open(Decoder *d, const char *path) {
    Flac *f = calloc(1, sizeof(Flac));
    if (!f) return false;
    f->decim = 1;
    f->dec = FLAC__stream_decoder_new();
    if (!f->dec) goto fail;
    FLAC__stream_decoder_set_md5_checking(f->dec, false);
    if (FLAC__stream_decoder_init_file(f->dec, path, on_write, on_metadata, on_error, f) != FLAC__STREAM_DECODER_INIT_STATUS_OK)
        goto fail;
    if (!FLAC__stream_decoder_process_until_end_of_metadata(f->dec) || !f->src_rate) goto fail;
    while (f->src_rate / f->decim > 48000) f->decim *= 2;
    d->impl = f;
    d->rate = f->src_rate / f->decim;
    d->total_frames = f->total / f->decim;
    return true;
fail:
    if (f->dec) FLAC__stream_decoder_delete(f->dec);
    free(f);
    return false;
}

static int flac_read(Decoder *d, s16 *out, int frames) {
    Flac *f = d->impl;
    int got = 0;
    while (got < frames) {
        if (f->pos < f->len) {
            int n = f->len - f->pos;
            if (n > frames - got) n = frames - got;
            memcpy(out + got * 2, f->buf + f->pos * 2, (size_t)n * 2 * sizeof(s16));
            f->pos += n;
            got += n;
            continue;
        }
        if (FLAC__stream_decoder_get_state(f->dec) == FLAC__STREAM_DECODER_END_OF_STREAM) break;
        if (!FLAC__stream_decoder_process_single(f->dec)) return got > 0 ? got : -1;
        if (f->pos >= f->len && FLAC__stream_decoder_get_state(f->dec) == FLAC__STREAM_DECODER_END_OF_STREAM) break;
    }
    return got;
}

static bool flac_seek(Decoder *d, u64 frame) {
    Flac *f = d->impl;
    f->len = f->pos = 0;
    if (FLAC__stream_decoder_seek_absolute(f->dec, frame * f->decim)) return true;
    if (FLAC__stream_decoder_get_state(f->dec) == FLAC__STREAM_DECODER_SEEK_ERROR) FLAC__stream_decoder_flush(f->dec);
    return false;
}

static void flac_close(Decoder *d) {
    Flac *f = d->impl;
    if (!f) return;
    FLAC__stream_decoder_finish(f->dec);
    FLAC__stream_decoder_delete(f->dec);
    free(f->buf);
    free(f);
    d->impl = NULL;
}

static bool flac_tags(const char *path, TrackTags *t) {
    FLAC__StreamMetadata info;
    if (!FLAC__metadata_get_streaminfo(path, &info)) return false;
    if (info.data.stream_info.sample_rate)
        t->duration_ms = (u32)(info.data.stream_info.total_samples * 1000 / info.data.stream_info.sample_rate);
    FLAC__StreamMetadata *tags = NULL;
    if (FLAC__metadata_get_tags(path, &tags) && tags) {
        const FLAC__StreamMetadata_VorbisComment *vc = &tags->data.vorbis_comment;
        for (u32 i = 0; i < vc->num_comments; i++)
            tags_vorbis_comment(t, (const char *)vc->comments[i].entry, vc->comments[i].length);
        FLAC__metadata_object_delete(tags);
    }
    return true;
}

const DecoderVt DEC_FLAC = { flac_open, flac_read, flac_seek, flac_close, flac_tags };
