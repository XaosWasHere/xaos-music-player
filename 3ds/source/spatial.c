#include "spatial.h"

#include <math.h>
#include <string.h>

/* Sotto questa frequenza i lati non si allargano: il basso resta pieno e al centro. */
#define SIDE_CUTOFF_HZ 180.0f
/* A intensità piena i lati sopra i bassi valgono 2,5 volte. */
#define MAX_WIDEN 1.5f
/* La prima riflessione: abbastanza tardi da dare spazio, non da fare eco. */
#define ECHO_SECONDS 0.014f
/* La riflessione perde le alte: suona come una stanza, non come uno sdoppiamento. */
#define ECHO_TONE_HZ 5000.0f
#define MAX_ECHO 0.45f
/* Riduzione del volume a intensità piena, per tenere i picchi sotto controllo. */
#define GAIN_COMPENSATION 0.35f
/* Quanto in fretta l'intensità effettiva insegue quella impostata, per campione. */
#define SMOOTHING 0.0005f
#define MIN_STRENGTH 0.001f

/* 14 ms a 48 kHz. */
#define ECHO_MAX 720

static volatile float s_target;
static float s_current;
static float s_echo[ECHO_MAX];
static int s_echo_len = 1, s_echo_index;
static float s_side_low, s_echo_tone;
static float s_side_coeff, s_tone_coeff;
static u32 s_rate;
static volatile bool s_reset;

void spatial_set_strength(float strength) {
    s_target = strength < 0 ? 0 : strength > 1 ? 1 : strength;
}

void spatial_reset(void) {
    s_reset = true;
}

/* Passa-basso a un polo: y += a·(x − y). */
static float one_pole(float cutoff, u32 rate) {
    return 1.0f - expf(-2.0f * (float)M_PI * cutoff / rate);
}

static s16 clip(float v) {
    if (v > 32767) return 32767;
    if (v < -32768) return -32768;
    return (s16)v;
}

void spatial_process(s16 *pcm, int frames, u32 rate) {
    float target = s_target;
    if (target <= 0 && s_current <= MIN_STRENGTH) {
        s_current = 0;
        return;
    }
    if (rate != s_rate || s_reset) {
        s_rate = rate;
        s_echo_len = (int)(rate * ECHO_SECONDS + 0.5f);
        if (s_echo_len < 1) s_echo_len = 1;
        if (s_echo_len > ECHO_MAX) s_echo_len = ECHO_MAX;
        s_side_coeff = one_pole(SIDE_CUTOFF_HZ, rate);
        s_tone_coeff = one_pole(ECHO_TONE_HZ, rate);
        memset(s_echo, 0, sizeof(s_echo));
        s_echo_index = 0;
        s_side_low = s_echo_tone = 0;
        s_reset = false;
    }
    for (int i = 0; i < frames; i++) {
        /* Cambiando intensità si scivola verso quella nuova: un salto si sentirebbe come un clic. */
        s_current += (target - s_current) * SMOOTHING;
        float left = pcm[i * 2], right = pcm[i * 2 + 1];
        float mid = (left + right) * 0.5f;
        float side = (left - right) * 0.5f;

        s_side_low += s_side_coeff * (side - s_side_low);
        float side_high = side - s_side_low;

        float delayed = s_echo[s_echo_index];
        s_echo[s_echo_index] = side_high;
        if (++s_echo_index >= s_echo_len) s_echo_index = 0;
        s_echo_tone += s_tone_coeff * (delayed - s_echo_tone);

        float widened = s_side_low + side_high * (1 + MAX_WIDEN * s_current) + s_echo_tone * (MAX_ECHO * s_current);
        float gain = 1 / (1 + GAIN_COMPENSATION * s_current);
        pcm[i * 2] = clip((mid + widened) * gain);
        pcm[i * 2 + 1] = clip((mid - widened) * gain);
    }
}
