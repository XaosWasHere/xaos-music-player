#pragma once

#include <3ds.h>
#include <stdbool.h>

/* I testi sincronizzati: un file .lrc accanto al brano, con lo stesso nome. */

typedef struct {
    u32 ms;
    char *text;
} LyricLine;

typedef struct {
    LyricLine *lines;
    int count;
} Lyrics;

bool lyrics_load(const char *audio_path, Lyrics *out);
void lyrics_free(Lyrics *l);
/* L'ultima riga cominciata a ms, -1 prima della prima. */
int lyrics_index(const Lyrics *l, u32 ms);
