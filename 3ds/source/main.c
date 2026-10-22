/*
 * Xaos per Nintendo 3DS: il player di Xaos, senza la rete.
 *
 * Gira sul 3DS vecchio (268 MHz) e sul New 3DS, dove accende gli 804 MHz.
 * La musica sta in sdmc:/music; l'indice della libreria e lo stato in
 * sdmc:/3ds/xaos.
 */
#include <3ds.h>
#include <citro2d.h>
#include <mpg123.h>
#include <stdio.h>
#include <sys/stat.h>

#include "audio.h"
#include "intro.h"
#include "library.h"
#include "player.h"
#include "settings.h"
#include "spatial.h"
#include "ui.h"
#include "views.h"

/*
 * HOME, tastiera e standby: il sistema sospende l'app e mette a dormire il
 * DSP. Il thread audio si ferma prima, così non lo tocca mentre dorme.
 */
static void on_apt(APT_HookType hook, void *param) {
    (void)param;
    switch (hook) {
    case APTHOOK_ONSUSPEND:
    case APTHOOK_ONSLEEP:
        audio_set_suspended(true);
        break;
    case APTHOOK_ONRESTORE:
    case APTHOOK_ONWAKEUP:
        audio_set_suspended(false);
        break;
    default:
        break;
    }
}

/*
 * Lo stato dello sportello. La sessione di sistema si apre solo per la
 * domanda: tenerla aperta sempre può bloccare il menu HOME, che la usa.
 */
static bool lid_is_open(void) {
    u8 open = 1;
    if (R_SUCCEEDED(ptmuInit())) {
        if (R_FAILED(PTMU_GetShellState(&open))) open = 1;
        ptmuExit();
    }
    return open != 0;
}

static void backlights(bool on) {
    if (R_FAILED(gspLcdInit())) return;
    if (on) GSPLCD_PowerOnAllBacklights();
    else GSPLCD_PowerOffAllBacklights();
    gspLcdExit();
}

int main(void) {
    gfxInitDefault();
    gfxSet3D(false);
    C3D_Init(C3D_DEFAULT_CMDBUF_SIZE);
    /* Con il 3D lo schermo sopra si disegna due volte, puntini di sfondo compresi. */
    C2D_Init(16384);
    C2D_Prepare();
    osSetSpeedupEnable(true);
    hidSetRepeatParameters(320, 60);
    mkdir("sdmc:/3ds", 0777);
    mkdir(DATA_DIR, 0777);
    mpg123_init();

    C3D_RenderTarget *top = C2D_CreateScreenTarget(GFX_TOP, GFX_LEFT);
    C3D_RenderTarget *top_right = C2D_CreateScreenTarget(GFX_TOP, GFX_RIGHT);
    C3D_RenderTarget *bottom = C2D_CreateScreenTarget(GFX_BOTTOM, GFX_LEFT);

    settings_load();
    spatial_set_strength(g_settings.virt / 4.0f);
    ui_init();
    bool audio_ok = audio_init();
    aptHookCookie apt_cookie;
    aptHook(&apt_cookie, on_apt, NULL);
    player_init();
    library_start();
    if (audio_ok) player_restore();

    bool screens_off = false;
    bool sleep_allowed = true;
    bool stereo = false;
    bool lid_open = true;
    u32 frame = 0;
    /* Si apre in un attimo: l'animazione di avvio dà il tempo di vedere il logo. */
    if (g_settings.intro) intro_start();

    while (aptMainLoop()) {
        hidScanInput();
        u32 down = hidKeysDown();
        u32 held = hidKeysHeld();
        u32 up = hidKeysUp();
        if (down & KEY_START) break;
        touchPosition touch;
        hidTouchRead(&touch);
        circlePosition pad;
        hidCircleRead(&pad);

        /* La scansione ha finito: la libreria nuova al posto della vecchia. */
        Library *old = library_poll();
        if (old) {
            player_remap(old, library_get());
            library_free(old);
            views_library_changed();
            char msg[64];
            snprintf(msg, sizeof(msg), "%d BRANI", library_get()->count);
            views_toast(msg);
        }

        if (audio_ok) player_update();
        /* Durante l'animazione un tasto o un tocco la chiudono e basta. */
        if (intro_active()) intro_input(down);
        else if (!screens_off) view_bottom_input(down, held, up, &touch, &pad);

        /*
         * Musica a console chiusa: mentre suona il 3DS non va in standby e gli
         * schermi si spengono. Chiuso, il 3DS spegne gli altoparlanti: si
         * continua solo nelle cuffie (come l'app Sound di Nintendo). Senza
         * cuffie si mette in pausa e lo standby torna normale.
         */
        /* Lo sportello si controlla quattro volte al secondo, o subito a schermi spenti. */
        if (screens_off || frame++ % 15 == 0) lid_open = lid_is_open();
        if (!lid_open && player_is_playing()) {
            bool headphones = true;
            if (audio_ok && R_SUCCEEDED(DSP_GetHeadphoneStatus(&headphones)) && !headphones) player_pause();
        }
        bool playing = player_is_playing();
        if (playing == sleep_allowed) {
            sleep_allowed = !playing;
            aptSetSleepAllowed(sleep_allowed);
        }
        if (!lid_open && playing && !screens_off) {
            backlights(false);
            screens_off = true;
        } else if ((lid_open || !playing) && screens_off) {
            backlights(true);
            screens_off = false;
        }
        if (screens_off) {
            /* Niente da disegnare: si risparmia batteria. */
            svcSleepThread(50 * 1000 * 1000);
            continue;
        }

        /* Il 3D: con il cursore alzato lo schermo sopra si disegna per i due occhi. */
        float slider = osGet3DSliderState();
        if ((slider > 0.01f) != stereo) {
            stereo = slider > 0.01f;
            gfxSet3D(stereo);
        }
        const float depth = DEPTH_PX[g_settings.depth] * slider;

        ui_frame_begin();
        C3D_FrameBegin(C3D_FRAME_SYNCDRAW);
        C2D_TargetClear(top, COL_BG);
        C2D_SceneBegin(top);
        view_top_draw(audio_ok, stereo ? -1 : 0, depth);
        intro_draw_top(stereo ? -1 : 0, depth);
        if (stereo) {
            C2D_TargetClear(top_right, COL_BG);
            C2D_SceneBegin(top_right);
            view_top_draw(audio_ok, 1, depth);
            intro_draw_top(1, depth);
        }
        C2D_TargetClear(bottom, COL_BG);
        C2D_SceneBegin(bottom);
        view_bottom_draw();
        intro_draw_bottom();
        C3D_FrameEnd(0);
        /*
         * Un attimo di pausa a ogni fotogramma: il thread di libctru che
         * gestisce HOME ha priorità più bassa di questo, e se il disegno non
         * si ferma mai non riceve tempo e il tasto HOME blocca l'app.
         */
        svcSleepThread(1000 * 1000);
    }

    if (audio_ok) player_save();
    if (screens_off) backlights(true);
    aptUnhook(&apt_cookie);
    audio_exit();
    library_exit();
    player_exit();
    ui_exit();
    C2D_Fini();
    C3D_Fini();
    gfxExit();
    return 0;
}
