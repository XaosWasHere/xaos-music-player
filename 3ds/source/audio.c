#include "audio.h"

#include <string.h>

#include "decoder.h"
#include "spatial.h"

#define CHANNEL 0
/* Otto buffer da 4096 frame: a 44,1 kHz quasi tre quarti di secondo in coda,
 * abbastanza per non sentire i rallentamenti della SD. */
#define NBUFS 8
#define BUF_FRAMES 4096
#define SLOTS 4
#define PATH_MAX_LEN 512

/* I brani che hanno dei buffer in coda: il corrente e quello attaccato dopo. */
typedef struct {
    u32 serial;
    u32 rate;
    u64 total;
    char path[PATH_MAX_LEN];
} Slot;

typedef struct {
    int slot;
    u64 start;
} BufMeta;

static ndspWaveBuf s_buf[NBUFS];
static BufMeta s_meta[NBUFS];
static s16 *s_pcm;
static int s_fill;

static Slot s_slots[SLOTS];
static int s_slot_next;

/* Il thread. */
static Thread s_thread;
static LightLock s_lock;
static LightEvent s_event;
static volatile bool s_quit;
/*
 * HOME o standby: il sistema mette a dormire il DSP. Il thread si ferma e non
 * tocca più la coda finché l'app non torna in primo piano.
 */
static volatile bool s_suspended;
static volatile bool s_parked;

/* Solo il thread tocca il decoder. */
static Decoder s_dec;
static bool s_dec_open;
static int s_dec_slot = -1;
static u64 s_dec_frame;
static bool s_eof;
static bool s_wait_rate;
static u32 s_rate;

static char s_next_path[PATH_MAX_LEN];
static u32 s_next_serial;

/* I comandi, sotto s_lock. */
static struct {
    bool play;
    char path[PATH_MAX_LEN];
    u32 serial;
    u32 start_ms;
    bool seek;
    u32 seek_ms;
    bool stop;
    bool next;
    char next_path[PATH_MAX_LEN];
    u32 next_serial;
} s_cmd;

/* Lo stato per chi chiede. */
static volatile bool s_active;
static volatile bool s_paused;
static volatile u32 s_failed_serial;
/* Dove siamo quando nessun buffer sta suonando (appena dopo un salto). */
static volatile u32 s_hint_serial;
static volatile u32 s_hint_ms;
static volatile int s_hint_slot = -1;

static void on_frame(void *arg) {
    (void)arg;
    LightEvent_Signal(&s_event);
}

/* Svuota la coda del DSP; i buffer tornano tutti liberi. */
static void clear_channel(void) {
    ndspChnWaveBufClear(CHANNEL);
    svcSleepThread(6 * 1000 * 1000);
    for (int i = 0; i < NBUFS; i++) s_buf[i].status = NDSP_WBUF_DONE;
    s_fill = 0;
    spatial_reset();
}

static bool all_done(void) {
    for (int i = 0; i < NBUFS; i++)
        if (s_buf[i].status == NDSP_WBUF_QUEUED || s_buf[i].status == NDSP_WBUF_PLAYING) return false;
    return true;
}

static void close_decoder(void) {
    if (s_dec_open) decoder_close(&s_dec);
    s_dec_open = false;
    s_dec_slot = -1;
}

static int new_slot(const char *path, u32 serial) {
    int i = s_slot_next;
    s_slot_next = (s_slot_next + 1) % SLOTS;
    Slot *s = &s_slots[i];
    s->serial = serial;
    s->rate = s_dec.rate;
    s->total = s_dec.total_frames;
    strncpy(s->path, path, PATH_MAX_LEN - 1);
    s->path[PATH_MAX_LEN - 1] = 0;
    return i;
}

/* Apre path e lo prepara a partire da start_ms. */
static bool start_track(const char *path, u32 serial, u32 start_ms) {
    close_decoder();
    s_eof = false;
    s_wait_rate = false;
    if (!decoder_open(&s_dec, path)) {
        s_failed_serial = serial;
        s_active = false;
        return false;
    }
    s_dec_open = true;
    s_dec_slot = new_slot(path, serial);
    s_dec_frame = 0;
    if (start_ms > 0) {
        u64 frame = (u64)start_ms * s_dec.rate / 1000;
        if (decoder_seek(&s_dec, frame)) s_dec_frame = frame;
    }
    s_rate = s_dec.rate;
    ndspChnSetRate(CHANNEL, (float)s_rate);
    s_hint_serial = serial;
    s_hint_slot = s_dec_slot;
    s_hint_ms = (u32)(s_dec_frame * 1000 / s_rate);
    s_active = true;
    return true;
}

/* Finito un brano: si passa al successivo, se c'è, senza fermare il DSP. */
static void chain_next(void) {
    if (!s_next_serial) {
        s_eof = true;
        return;
    }
    char path[PATH_MAX_LEN];
    strcpy(path, s_next_path);
    u32 serial = s_next_serial;
    s_next_serial = 0;
    close_decoder();
    if (!decoder_open(&s_dec, path)) {
        s_failed_serial = serial;
        s_eof = true;
        return;
    }
    s_dec_open = true;
    s_dec_slot = new_slot(path, serial);
    s_dec_frame = 0;
    /* Con un'altra frequenza si aspetta che la coda finisca: il DSP ne usa una per volta. */
    if (s_dec.rate != s_rate) s_wait_rate = true;
}

static void fill_buffers(void) {
    if (s_wait_rate) {
        if (!all_done()) return;
        s_rate = s_dec.rate;
        ndspChnSetRate(CHANNEL, (float)s_rate);
        s_wait_rate = false;
    }
    while (s_dec_open && !s_eof && !s_wait_rate && s_buf[s_fill].status == NDSP_WBUF_DONE) {
        ndspWaveBuf *wb = &s_buf[s_fill];
        int n = decoder_read(&s_dec, wb->data_pcm16, BUF_FRAMES);
        if (n <= 0) {
            chain_next();
            continue;
        }
        spatial_process(wb->data_pcm16, n, s_dec.rate);
        s_meta[s_fill].slot = s_dec_slot;
        s_meta[s_fill].start = s_dec_frame;
        s_dec_frame += n;
        wb->nsamples = n;
        DSP_FlushDataCache(wb->data_pcm16, n * 2 * sizeof(s16));
        ndspChnWaveBufAdd(CHANNEL, wb);
        s_fill = (s_fill + 1) % NBUFS;
    }
}

/* Il brano che si sente in questo momento, dai buffer. */
static int playing_buffer(void) {
    for (int i = 0; i < NBUFS; i++)
        if (s_buf[i].status == NDSP_WBUF_PLAYING) return i;
    return -1;
}

static void handle_commands(void) {
    LightLock_Lock(&s_lock);
    bool stop = s_cmd.stop;
    bool play = s_cmd.play;
    bool seek = s_cmd.seek;
    bool next = s_cmd.next;
    char path[PATH_MAX_LEN], next_path[PATH_MAX_LEN];
    u32 serial = s_cmd.serial, start_ms = s_cmd.start_ms, seek_ms = s_cmd.seek_ms, next_serial = s_cmd.next_serial;
    strcpy(path, s_cmd.path);
    strcpy(next_path, s_cmd.next_path);
    s_cmd.stop = s_cmd.play = s_cmd.seek = s_cmd.next = false;
    LightLock_Unlock(&s_lock);

    if (stop) {
        clear_channel();
        close_decoder();
        s_next_serial = 0;
        s_active = false;
    }
    if (play) {
        clear_channel();
        s_next_serial = 0;
        start_track(path, serial, start_ms);
    }
    if (next) {
        strcpy(s_next_path, next_path);
        s_next_serial = next_path[0] ? next_serial : 0;
        /* Il decoder aveva finito senza un seguito: ora ce l'ha. */
        if (s_eof && s_next_serial && s_dec_open) {
            s_eof = false;
            chain_next();
        }
    }
    if (seek && s_active) {
        /* Si salta nel brano che si sente, che può non essere quello che si sta decodificando. */
        int b = playing_buffer();
        int slot = b >= 0 ? s_meta[b].slot : s_hint_slot;
        if (slot >= 0 && slot != s_dec_slot) {
            Slot heard = s_slots[slot];
            clear_channel();
            u32 keep_next = s_next_serial;
            char keep_path[PATH_MAX_LEN];
            strcpy(keep_path, s_next_path);
            start_track(heard.path, heard.serial, seek_ms);
            s_next_serial = keep_next;
            strcpy(s_next_path, keep_path);
        } else if (s_dec_open) {
            clear_channel();
            u64 frame = (u64)seek_ms * s_dec.rate / 1000;
            if (s_dec.total_frames && frame >= s_dec.total_frames) frame = s_dec.total_frames - 1;
            if (decoder_seek(&s_dec, frame)) s_dec_frame = frame;
            s_eof = false;
            s_wait_rate = false;
            if (s_rate != s_dec.rate) {
                s_rate = s_dec.rate;
                ndspChnSetRate(CHANNEL, (float)s_rate);
            }
            s_hint_serial = s_slots[s_dec_slot].serial;
            s_hint_slot = s_dec_slot;
            s_hint_ms = (u32)(s_dec_frame * 1000 / s_rate);
        }
    }
}

static void thread_main(void *arg) {
    (void)arg;
    while (!s_quit) {
        if (s_suspended) {
            s_parked = true;
            LightEvent_WaitTimeout(&s_event, 20 * 1000 * 1000);
            continue;
        }
        s_parked = false;
        handle_commands();
        fill_buffers();
        if (s_active && s_dec_open && s_eof && all_done()) s_active = false;
        if (s_active && !s_dec_open) s_active = false;
        /* Ricordarsi l'ultimo punto sentito, per quando la coda è vuota. */
        int b = playing_buffer();
        if (b >= 0) {
            Slot *s = &s_slots[s_meta[b].slot];
            u64 pos = s_meta[b].start + ndspChnGetSamplePos(CHANNEL);
            s_hint_serial = s->serial;
            s_hint_slot = s_meta[b].slot;
            s_hint_ms = s->rate ? (u32)(pos * 1000 / s->rate) : 0;
        }
        LightEvent_WaitTimeout(&s_event, 20 * 1000 * 1000);
    }
}

bool audio_init(void) {
    if (R_FAILED(ndspInit())) return false;
    ndspSetOutputMode(NDSP_OUTPUT_STEREO);
    ndspChnReset(CHANNEL);
    ndspChnSetInterp(CHANNEL, NDSP_INTERP_POLYPHASE);
    ndspChnSetFormat(CHANNEL, NDSP_FORMAT_STEREO_PCM16);
    ndspChnSetRate(CHANNEL, 44100);
    float mix[12] = {0};
    mix[0] = mix[1] = 1.0f;
    ndspChnSetMix(CHANNEL, mix);

    s_pcm = linearAlloc(NBUFS * BUF_FRAMES * 2 * sizeof(s16));
    if (!s_pcm) {
        ndspExit();
        return false;
    }
    memset(s_buf, 0, sizeof(s_buf));
    for (int i = 0; i < NBUFS; i++) {
        s_buf[i].data_pcm16 = s_pcm + i * BUF_FRAMES * 2;
        s_buf[i].status = NDSP_WBUF_DONE;
    }

    LightLock_Init(&s_lock);
    LightEvent_Init(&s_event, RESET_ONESHOT);
    ndspSetCallback(on_frame, NULL);

    /* Un gradino sopra il thread principale: la musica prima della grafica. */
    s32 prio = 0x30;
    svcGetThreadPriority(&prio, CUR_THREAD_HANDLE);
    prio -= 1;
    if (prio < 0x18) prio = 0x18;
    if (prio > 0x3F) prio = 0x3F;
    s_quit = false;
    s_thread = threadCreate(thread_main, NULL, 64 * 1024, prio, -2, false);
    return s_thread != NULL;
}

void audio_exit(void) {
    if (!s_thread) return;
    s_quit = true;
    LightEvent_Signal(&s_event);
    threadJoin(s_thread, U64_MAX);
    threadFree(s_thread);
    s_thread = NULL;
    ndspChnWaveBufClear(CHANNEL);
    close_decoder();
    ndspExit();
    if (s_pcm) linearFree(s_pcm);
    s_pcm = NULL;
}

void audio_play(const char *path, u32 serial, u32 start_ms) {
    if (!s_thread) return;
    LightLock_Lock(&s_lock);
    s_cmd.play = true;
    s_cmd.stop = false;
    s_cmd.seek = false;
    s_cmd.next = false;
    strncpy(s_cmd.path, path, PATH_MAX_LEN - 1);
    s_cmd.path[PATH_MAX_LEN - 1] = 0;
    s_cmd.serial = serial;
    s_cmd.start_ms = start_ms;
    /* Subito, per chi guarda lo stato prima che il thread parta. */
    s_hint_serial = serial;
    s_hint_ms = start_ms;
    s_hint_slot = -1;
    s_active = true;
    LightLock_Unlock(&s_lock);
    LightEvent_Signal(&s_event);
}

void audio_set_next(const char *path, u32 serial) {
    if (!s_thread) return;
    LightLock_Lock(&s_lock);
    s_cmd.next = true;
    if (path) {
        strncpy(s_cmd.next_path, path, PATH_MAX_LEN - 1);
        s_cmd.next_path[PATH_MAX_LEN - 1] = 0;
    } else {
        s_cmd.next_path[0] = 0;
    }
    s_cmd.next_serial = serial;
    LightLock_Unlock(&s_lock);
    LightEvent_Signal(&s_event);
}

void audio_stop(void) {
    if (!s_thread) return;
    LightLock_Lock(&s_lock);
    s_cmd.stop = true;
    s_cmd.play = s_cmd.seek = s_cmd.next = false;
    LightLock_Unlock(&s_lock);
    LightEvent_Signal(&s_event);
}

void audio_set_paused(bool paused) {
    if (!s_thread) return;
    s_paused = paused;
    ndspChnSetPaused(CHANNEL, paused);
}

void audio_seek(u32 ms) {
    if (!s_thread) return;
    LightLock_Lock(&s_lock);
    s_cmd.seek = true;
    s_cmd.seek_ms = ms;
    s_hint_ms = ms;
    LightLock_Unlock(&s_lock);
    LightEvent_Signal(&s_event);
}

void audio_set_suspended(bool suspended) {
    if (!s_thread) return;
    if (!suspended) {
        s_suspended = false;
        LightEvent_Signal(&s_event);
        return;
    }
    s_parked = false;
    s_suspended = true;
    LightEvent_Signal(&s_event);
    /* Si aspetta che il thread abbia finito il buffer che stava riempiendo. */
    for (int i = 0; i < 100 && !s_parked; i++) svcSleepThread(2 * 1000 * 1000);
}

void audio_status(AudioStatus *out) {
    if (!s_thread) {
        memset(out, 0, sizeof(*out));
        return;
    }
    out->serial = s_hint_serial;
    out->position_ms = s_hint_ms;
    int slot = s_hint_slot;
    if (slot >= 0 && s_slots[slot].serial == out->serial && s_slots[slot].rate) {
        out->rate = s_slots[slot].rate;
        out->duration_ms = (u32)(s_slots[slot].total * 1000 / s_slots[slot].rate);
    } else {
        out->rate = 0;
        out->duration_ms = 0;
    }
    out->active = s_active;
    out->paused = s_paused;
    out->failed_serial = s_failed_serial;
}
