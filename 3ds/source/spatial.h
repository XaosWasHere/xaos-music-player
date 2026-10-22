#pragma once

#include <3ds.h>

/*
 * La virtualizzazione: la stessa dell'app Android. Allarga la scena stereo
 * sopra i bassi e aggiunge ai lati una prima riflessione, più scura; il
 * centro (voce, cassa, basso) resta com'è.
 */

/* 0 = spenta, 1 = piena. Si può cambiare in qualunque momento. */
void spatial_set_strength(float strength);

/* Dal thread audio: elabora frames campioni stereo, sul posto. */
void spatial_process(s16 *pcm, int frames, u32 rate);

/* Dopo un salto o un brano nuovo: niente code del suono di prima. */
void spatial_reset(void);
