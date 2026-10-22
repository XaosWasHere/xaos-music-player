#pragma once

#include <3ds.h>
#include <stdbool.h>

/*
 * Il motore audio: un thread decodifica e tiene pieni i buffer del DSP.
 * Ogni brano ha un numero (serial) scelto da chi lo chiede: lo stato dice
 * quale si sta sentendo davvero, anche quando il successivo è già partito
 * senza pause.
 */

/* false se il DSP non parte: di solito manca sdmc:/3ds/dspfirm.cdc. */
bool audio_init(void);
void audio_exit(void);

/* Suona path da start_ms, al posto di qualunque cosa. */
void audio_play(const char *path, u32 serial, u32 start_ms);
/* Il brano da attaccare senza pause alla fine di quello in corso (NULL: nessuno). */
void audio_set_next(const char *path, u32 serial);
void audio_stop(void);
void audio_set_paused(bool paused);
void audio_seek(u32 ms);

/* HOME o standby: il thread audio si ferma finché l'app non torna in primo piano. */
void audio_set_suspended(bool suspended);

typedef struct {
    /* Il brano che si sente (o l'ultimo sentito). */
    u32 serial;
    u32 position_ms;
    u32 duration_ms;
    u32 rate;
    /* false quando la musica è finita o fermata. */
    bool active;
    bool paused;
    /* L'ultimo brano che non si è riuscito ad aprire (0 nessuno). */
    u32 failed_serial;
} AudioStatus;

void audio_status(AudioStatus *out);
