#pragma once

#include <3ds.h>
#include <stdbool.h>

/*
 * I decoder dei formati. Escono sempre in stereo a 16 bit interlacciato: i
 * brani mono vengono raddoppiati, quelli multicanale ridotti ai primi due
 * canali. Così il motore audio deve badare solo alla frequenza.
 */

typedef struct {
    char title[160];
    char artist[160];
    char album[160];
    char album_artist[160];
    int track;
    int disc;
    int year;
    u32 duration_ms;
} TrackTags;

typedef struct Decoder Decoder;

typedef struct {
    bool (*open)(Decoder *d, const char *path);
    /* Frame (coppie di campioni) letti, 0 alla fine, negativo se errore. */
    int (*read)(Decoder *d, s16 *out, int frames);
    bool (*seek)(Decoder *d, u64 frame);
    void (*close)(Decoder *d);
    bool (*tags)(const char *path, TrackTags *out);
} DecoderVt;

struct Decoder {
    const DecoderVt *vt;
    void *impl;
    u32 rate;
    /* 0 se non si sa. */
    u64 total_frames;
};

bool decoder_supported(const char *path);
bool decoder_open(Decoder *d, const char *path);
int decoder_read(Decoder *d, s16 *out, int frames);
bool decoder_seek(Decoder *d, u64 frame);
void decoder_close(Decoder *d);

/* Legge i tag e la durata. Dove mancano, titolo e album vengono dal nome del file e della cartella. */
bool decoder_read_tags(const char *path, TrackTags *out);

/* "FLAC", "MP3"... per mostrarlo. */
const char *decoder_format_name(const char *path);

/* Per i decoder: "3/12" -> 3. */
int tag_number(const char *s);
/* Per i decoder: copia un valore "CHIAVE=valore" dei commenti Vorbis nei tag. */
void tags_vorbis_comment(TrackTags *t, const char *comment, int len);

extern const DecoderVt DEC_MP3, DEC_FLAC, DEC_VORBIS, DEC_OPUS, DEC_WAV;
