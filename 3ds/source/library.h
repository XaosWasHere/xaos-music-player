#pragma once

#include <3ds.h>
#include <stdbool.h>
#include <stddef.h>

/* La cartella della musica sulla SD, e quella dei dati di Xaos. */
#define MUSIC_DIR "sdmc:/music"
#define DATA_DIR "sdmc:/3ds/xaos"

typedef struct {
    char *path;
    char *title;
    char *artist;
    char *album;
    char *album_artist;
    u32 size;
    u32 duration_ms;
    u16 track;
    u16 year;
    u8 disc;
    int album_idx;
} Song;

typedef struct {
    const char *title;
    const char *artist;
    /* Indici dei brani, in ordine di disco e traccia. */
    int *songs;
    int count;
    u16 year;
    u32 duration_ms;
} Album;

typedef struct {
    Song *songs;
    int count;
    /* Tutti i brani in ordine di titolo. */
    int *by_title;
    Album *albums;
    int album_count;
    /* Per trovare un brano dal percorso. */
    int *table;
    int table_size;
} Library;

/* Carica l'indice salvato; se non c'è, comincia a cercare la musica. */
void library_start(void);
void library_exit(void);

const Library *library_get(void);

/* Rilegge le cartelle in background: i brani già noti non si riaprono. */
void library_rescan(void);
bool library_scanning(void);
/* Quanti brani ha trovato finora la scansione, e la cartella che sta leggendo. */
int library_scan_found(void);
void library_scan_folder(char *out, size_t n);

/*
 * Dal thread principale, ogni fotogramma: se la scansione ha finito, la
 * libreria nuova prende il posto della vecchia. Restituisce la vecchia (da
 * passare a library_free dopo averla usata per rimappare) o NULL.
 */
Library *library_poll(void);
void library_free(Library *lib);

int library_find(const Library *lib, const char *path);
