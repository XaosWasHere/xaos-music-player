#pragma once

#include <3ds.h>
#include <stdbool.h>

/*
 * L'animazione di avvio: i puntini di sfondo si accendono a onda su tutti e
 * due gli schermi, la X si forma punto per punto, diventa la prima lettera di
 * XAOS, una fila di puntini si riempie come un nastro e il player emerge.
 * Si disegna sopra il player, che intanto è già pronto sotto.
 */

void intro_start(void);
bool intro_active(void);
/* Un tasto o un tocco la fanno finire subito (con la dissolvenza). */
void intro_input(u32 down);

void intro_draw_top(float eye, float depth);
void intro_draw_bottom(void);
