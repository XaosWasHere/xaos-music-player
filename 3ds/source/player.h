#pragma once

#include <3ds.h>
#include <stdbool.h>

#include "library.h"

/* La coda e i comandi del player, sopra il motore audio. */

typedef enum { REPEAT_OFF, REPEAT_ALL, REPEAT_ONE } RepeatMode;

void player_init(void);
void player_exit(void);

/* Suona la lista di brani (indici della libreria) partendo da start. */
void player_play(const int *songs, int count, int start);
void player_toggle(void);
void player_pause(void);
void player_next(void);
void player_prev(void);
void player_seek(u32 ms);
void player_seek_by(int delta_ms);
/* Salta a una posizione della coda, nell'ordine in cui si ascolta. */
void player_jump(int pos);

void player_set_shuffle(bool on);
bool player_shuffle(void);
void player_cycle_repeat(void);
RepeatMode player_repeat(void);

/* Ogni fotogramma: segue il motore audio e passa al brano dopo. */
void player_update(void);

/* La libreria è cambiata: la coda si ritrova i brani per percorso. */
void player_remap(const Library *old_lib, const Library *new_lib);

int player_current(void);
bool player_is_playing(void);
bool player_has_track(void);
u32 player_position_ms(void);
u32 player_duration_ms(void);
u32 player_rate(void);

/* La coda nell'ordine di ascolto. */
int player_queue_count(void);
int player_queue_song(int pos);
int player_queue_pos(void);

/* Lo stato da ritrovare alla prossima apertura. */
void player_save(void);
void player_restore(void);
