#include "library.h"

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

#include "decoder.h"

#define CACHE_PATH DATA_DIR "/library.db"
#define CACHE_MAGIC "XAOSLIB1"
#define MAX_DEPTH 16

static Library *s_lib;
static Library *s_ready;
static LightLock s_lock;
/* Per s_groups, separato da s_lock: ordinare può durare, la cartella si legge a ogni fotogramma. */
static LightLock s_sort_lock;
static Thread s_thread;
static volatile bool s_scanning;
static volatile int s_found;
static char s_folder[256];

static const char VARIOUS[] = "Artisti vari";

/* ------------------------------------------------------------ ordinamento */

typedef int (*IdxCmp)(const Library *lib, int a, int b);

/* Merge sort stabile di indici, con la libreria come contesto. */
static void sort_idx(int *a, int n, IdxCmp cmp, const Library *lib) {
    if (n < 2) return;
    int *tmp = malloc(sizeof(int) * n);
    if (!tmp) return;
    for (int width = 1; width < n; width *= 2) {
        for (int lo = 0; lo < n; lo += 2 * width) {
            int mid = lo + width < n ? lo + width : n;
            int hi = lo + 2 * width < n ? lo + 2 * width : n;
            int i = lo, j = mid, k = lo;
            while (i < mid && j < hi) tmp[k++] = cmp(lib, a[i], a[j]) <= 0 ? a[i++] : a[j++];
            while (i < mid) tmp[k++] = a[i++];
            while (j < hi) tmp[k++] = a[j++];
        }
        memcpy(a, tmp, sizeof(int) * n);
    }
    free(tmp);
}

static int by_title(const Library *lib, int a, int b) {
    const Song *x = &lib->songs[a], *y = &lib->songs[b];
    int c = strcasecmp(x->title, y->title);
    return c ? c : strcasecmp(x->artist, y->artist);
}

/* Album: stesso titolo e stesso artista dell'album, o, se manca, stessa cartella. */
static char **s_groups;

static int by_album(const Library *lib, int a, int b) {
    const Song *x = &lib->songs[a], *y = &lib->songs[b];
    int c = strcasecmp(x->album, y->album);
    if (c) return c;
    c = strcasecmp(s_groups[a], s_groups[b]);
    if (c) return c;
    if (x->disc != y->disc) return x->disc - y->disc;
    if (x->track != y->track) return x->track - y->track;
    return strcasecmp(x->title, y->title);
}

static int album_cmp(const void *pa, const void *pb) {
    const Album *a = pa, *b = pb;
    int c = strcasecmp(a->title, b->title);
    return c ? c : strcasecmp(a->artist, b->artist);
}

static u32 fnv(const char *s) {
    u32 h = 2166136261u;
    for (; *s; s++) h = (h ^ (u8)*s) * 16777619u;
    return h;
}

static char *dir_of(const char *path) {
    const char *slash = strrchr(path, '/');
    size_t n = slash ? (size_t)(slash - path) : 0;
    char *d = malloc(n + 1);
    memcpy(d, path, n);
    d[n] = 0;
    return d;
}

/* Ordina, raggruppa in album e indicizza i percorsi. */
static void finalize(Library *lib) {
    int n = lib->count;
    lib->by_title = malloc(sizeof(int) * (n ? n : 1));
    for (int i = 0; i < n; i++) lib->by_title[i] = i;
    sort_idx(lib->by_title, n, by_title, lib);

    /* s_groups è condiviso: si finalizza una libreria per volta. */
    LightLock_Lock(&s_sort_lock);
    s_groups = malloc(sizeof(char *) * (n ? n : 1));
    for (int i = 0; i < n; i++) {
        const Song *s = &lib->songs[i];
        s_groups[i] = s->album_artist[0] ? strdup(s->album_artist) : dir_of(s->path);
    }
    int *order = malloc(sizeof(int) * (n ? n : 1));
    for (int i = 0; i < n; i++) order[i] = i;
    sort_idx(order, n, by_album, lib);

    lib->albums = malloc(sizeof(Album) * (n ? n : 1));
    lib->album_count = 0;
    for (int i = 0; i < n;) {
        int j = i + 1;
        const Song *first = &lib->songs[order[i]];
        while (j < n && !strcasecmp(lib->songs[order[j]].album, first->album) &&
               !strcasecmp(s_groups[order[j]], s_groups[order[i]]))
            j++;
        Album *al = &lib->albums[lib->album_count++];
        al->title = first->album;
        al->count = j - i;
        al->songs = malloc(sizeof(int) * al->count);
        memcpy(al->songs, order + i, sizeof(int) * al->count);
        al->year = 0;
        al->duration_ms = 0;
        bool same_artist = true;
        for (int k = 0; k < al->count; k++) {
            const Song *s = &lib->songs[al->songs[k]];
            if (s->year > al->year) al->year = s->year;
            al->duration_ms += s->duration_ms;
            if (strcasecmp(s->artist, first->artist)) same_artist = false;
        }
        if (first->album_artist[0]) al->artist = first->album_artist;
        else if (same_artist) al->artist = first->artist;
        else al->artist = VARIOUS;
        i = j;
    }
    for (int i = 0; i < n; i++) free(s_groups[i]);
    free(s_groups);
    s_groups = NULL;
    LightLock_Unlock(&s_sort_lock);
    free(order);

    qsort(lib->albums, lib->album_count, sizeof(Album), album_cmp);
    for (int a = 0; a < lib->album_count; a++)
        for (int k = 0; k < lib->albums[a].count; k++) lib->songs[lib->albums[a].songs[k]].album_idx = a;

    int size = 16;
    while (size < n * 2) size *= 2;
    lib->table_size = size;
    lib->table = malloc(sizeof(int) * size);
    for (int i = 0; i < size; i++) lib->table[i] = -1;
    for (int i = 0; i < n; i++) {
        u32 h = fnv(lib->songs[i].path) & (size - 1);
        while (lib->table[h] >= 0) h = (h + 1) & (size - 1);
        lib->table[h] = i;
    }
}

int library_find(const Library *lib, const char *path) {
    if (!lib || !lib->table_size) return -1;
    u32 h = fnv(path) & (lib->table_size - 1);
    while (lib->table[h] >= 0) {
        if (!strcmp(lib->songs[lib->table[h]].path, path)) return lib->table[h];
        h = (h + 1) & (lib->table_size - 1);
    }
    return -1;
}

void library_free(Library *lib) {
    if (!lib) return;
    for (int i = 0; i < lib->count; i++) {
        Song *s = &lib->songs[i];
        free(s->path);
        free(s->title);
        free(s->artist);
        free(s->album);
        free(s->album_artist);
    }
    for (int a = 0; a < lib->album_count; a++) free(lib->albums[a].songs);
    free(lib->songs);
    free(lib->by_title);
    free(lib->albums);
    free(lib->table);
    free(lib);
}

/* ------------------------------------------------------------ indice su SD */

static void put_str(FILE *f, const char *s) {
    u16 n = (u16)strlen(s);
    fwrite(&n, 2, 1, f);
    fwrite(s, 1, n, f);
}

static char *get_str(FILE *f) {
    u16 n;
    if (fread(&n, 2, 1, f) != 1) return NULL;
    char *s = malloc(n + 1);
    if (fread(s, 1, n, f) != n) {
        free(s);
        return NULL;
    }
    s[n] = 0;
    return s;
}

static void save_cache(const Library *lib) {
    mkdir("sdmc:/3ds", 0777);
    mkdir(DATA_DIR, 0777);
    FILE *f = fopen(CACHE_PATH ".tmp", "wb");
    if (!f) return;
    static char vbuf[32 * 1024];
    setvbuf(f, vbuf, _IOFBF, sizeof(vbuf));
    fwrite(CACHE_MAGIC, 1, 8, f);
    u32 count = lib->count;
    fwrite(&count, 4, 1, f);
    for (int i = 0; i < lib->count; i++) {
        const Song *s = &lib->songs[i];
        fwrite(&s->size, 4, 1, f);
        fwrite(&s->duration_ms, 4, 1, f);
        fwrite(&s->track, 2, 1, f);
        fwrite(&s->year, 2, 1, f);
        fwrite(&s->disc, 1, 1, f);
        put_str(f, s->path);
        put_str(f, s->title);
        put_str(f, s->artist);
        put_str(f, s->album);
        put_str(f, s->album_artist);
    }
    bool ok = !ferror(f);
    fclose(f);
    if (!ok) return;
    remove(CACHE_PATH);
    rename(CACHE_PATH ".tmp", CACHE_PATH);
}

static Library *load_cache(void) {
    FILE *f = fopen(CACHE_PATH, "rb");
    if (!f) return NULL;
    static char vbuf[32 * 1024];
    setvbuf(f, vbuf, _IOFBF, sizeof(vbuf));
    char magic[8];
    u32 count;
    if (fread(magic, 1, 8, f) != 8 || memcmp(magic, CACHE_MAGIC, 8) || fread(&count, 4, 1, f) != 1 || count > 200000) {
        fclose(f);
        return NULL;
    }
    Library *lib = calloc(1, sizeof(Library));
    lib->songs = calloc(count ? count : 1, sizeof(Song));
    for (u32 i = 0; i < count; i++) {
        Song *s = &lib->songs[i];
        if (fread(&s->size, 4, 1, f) != 1 || fread(&s->duration_ms, 4, 1, f) != 1 || fread(&s->track, 2, 1, f) != 1 ||
            fread(&s->year, 2, 1, f) != 1 || fread(&s->disc, 1, 1, f) != 1)
            break;
        s->path = get_str(f);
        s->title = get_str(f);
        s->artist = get_str(f);
        s->album = get_str(f);
        s->album_artist = get_str(f);
        if (!s->path || !s->title || !s->artist || !s->album || !s->album_artist) {
            free(s->path);
            free(s->title);
            free(s->artist);
            free(s->album);
            free(s->album_artist);
            break;
        }
        lib->count++;
    }
    fclose(f);
    finalize(lib);
    return lib;
}

/* ------------------------------------------------------------ scansione */

typedef struct {
    Song *songs;
    int count;
    int cap;
    const Library *old;
} Scan;

static void add_file(Scan *sc, const char *path) {
    struct stat st;
    u32 size = stat(path, &st) == 0 ? (u32)st.st_size : 0;
    if (sc->count == sc->cap) {
        int cap = sc->cap ? sc->cap * 2 : 256;
        Song *grown = realloc(sc->songs, sizeof(Song) * cap);
        if (!grown) return;
        sc->songs = grown;
        sc->cap = cap;
    }
    Song *s = &sc->songs[sc->count];
    memset(s, 0, sizeof(*s));

    /* Già conosciuto e non cambiato: niente da rileggere. */
    int known = library_find(sc->old, path);
    if (known >= 0 && sc->old->songs[known].size == size) {
        const Song *o = &sc->old->songs[known];
        s->path = strdup(o->path);
        s->title = strdup(o->title);
        s->artist = strdup(o->artist);
        s->album = strdup(o->album);
        s->album_artist = strdup(o->album_artist);
        s->size = size;
        s->duration_ms = o->duration_ms;
        s->track = o->track;
        s->year = o->year;
        s->disc = o->disc;
    } else {
        TrackTags t;
        if (!decoder_read_tags(path, &t)) return;
        s->path = strdup(path);
        s->title = strdup(t.title);
        s->artist = strdup(t.artist[0] ? t.artist : "Artista sconosciuto");
        s->album = strdup(t.album);
        s->album_artist = strdup(t.album_artist);
        s->size = size;
        s->duration_ms = t.duration_ms;
        s->track = t.track > 0 ? t.track : 0;
        s->year = t.year > 0 ? t.year : 0;
        s->disc = t.disc > 0 ? t.disc : 0;
    }
    sc->count++;
    s_found = sc->count;
}

static void scan_dir(Scan *sc, char *path, size_t len, int depth) {
    if (depth > MAX_DEPTH) return;
    DIR *d = opendir(path);
    if (!d) return;
    LightLock_Lock(&s_lock);
    const char *name = strrchr(path, '/');
    strncpy(s_folder, name ? name + 1 : path, sizeof(s_folder) - 1);
    s_folder[sizeof(s_folder) - 1] = 0;
    LightLock_Unlock(&s_lock);
    struct dirent *e;
    while ((e = readdir(d))) {
        if (e->d_name[0] == '.') continue;
        size_t n = strlen(e->d_name);
        if (len + 1 + n + 1 > 1024) continue;
        path[len] = '/';
        memcpy(path + len + 1, e->d_name, n + 1);
        bool dir = e->d_type == DT_DIR;
        if (e->d_type == DT_UNKNOWN) {
            struct stat st;
            dir = stat(path, &st) == 0 && S_ISDIR(st.st_mode);
        }
        if (dir) scan_dir(sc, path, len + 1 + n, depth + 1);
        else if (decoder_supported(path)) add_file(sc, path);
        path[len] = 0;
    }
    closedir(d);
}

static void scan_thread(void *arg) {
    const Library *old = arg;
    Scan sc = { .old = old };
    char path[1024];
    strcpy(path, MUSIC_DIR);
    scan_dir(&sc, path, strlen(path), 0);

    Library *lib = calloc(1, sizeof(Library));
    lib->songs = sc.songs;
    lib->count = sc.count;
    if (!lib->songs) lib->songs = calloc(1, sizeof(Song));
    finalize(lib);
    save_cache(lib);

    LightLock_Lock(&s_lock);
    s_ready = lib;
    LightLock_Unlock(&s_lock);
    s_scanning = false;
}

void library_rescan(void) {
    if (s_scanning) return;
    if (s_thread) {
        threadJoin(s_thread, U64_MAX);
        threadFree(s_thread);
        s_thread = NULL;
    }
    s_scanning = true;
    s_found = 0;
    s_folder[0] = 0;
    /* Sotto il thread principale: la grafica resta fluida mentre cerca. */
    s32 prio = 0x30;
    svcGetThreadPriority(&prio, CUR_THREAD_HANDLE);
    prio += 1;
    if (prio > 0x3F) prio = 0x3F;
    s_thread = threadCreate(scan_thread, s_lib, 128 * 1024, prio, -2, false);
    if (!s_thread) s_scanning = false;
}

void library_start(void) {
    LightLock_Init(&s_lock);
    LightLock_Init(&s_sort_lock);
    s_lib = load_cache();
    if (!s_lib) {
        s_lib = calloc(1, sizeof(Library));
        s_lib->songs = calloc(1, sizeof(Song));
        finalize(s_lib);
        library_rescan();
    }
}

void library_exit(void) {
    if (s_thread) {
        threadJoin(s_thread, U64_MAX);
        threadFree(s_thread);
        s_thread = NULL;
    }
    library_free(s_ready);
    s_ready = NULL;
    library_free(s_lib);
    s_lib = NULL;
}

const Library *library_get(void) {
    return s_lib;
}

bool library_scanning(void) {
    return s_scanning;
}

int library_scan_found(void) {
    return s_found;
}

void library_scan_folder(char *out, size_t n) {
    LightLock_Lock(&s_lock);
    strncpy(out, s_folder, n - 1);
    out[n - 1] = 0;
    LightLock_Unlock(&s_lock);
}

Library *library_poll(void) {
    LightLock_Lock(&s_lock);
    Library *ready = s_ready;
    s_ready = NULL;
    LightLock_Unlock(&s_lock);
    if (!ready) return NULL;
    Library *old = s_lib;
    s_lib = ready;
    return old;
}
