# Xaos per Nintendo 3DS

Il player di Xaos per 3DS con homebrew, senza la parte di rete. Stesso aspetto
delle altre versioni: fondo nero, rosso Nothing, titoli a matrice di punti
(le lettere sono generate da quelle della versione desktop).

Gira sia sul 3DS (e 2DS) sia sul New 3DS, dove usa la CPU a 804 MHz.

## Cosa fa

- MP3, FLAC (fino a 24 bit; sopra i 48 kHz viene dimezzato), Ogg Vorbis, Opus, WAV.
- Schermo sopra: brano in riproduzione, testi sincronizzati da un file `.lrc`
  accanto al brano (stesso nome), barra del tempo a punti.
- Schermo sotto, a sfioramento: Album, Brani, Coda, Impostazioni e i comandi,
  con la barra del tempo da toccare o trascinare.
- Tema scuro (rosso) o chiaro (giallo) e tema personalizzato come su desktop:
  sfondo (anche sfumato), pannelli, testo, accento, pallini, e preset con un
  nome. `tools/export_themes.py` porta sulla SD i preset di Xaos per PC.
- Sfondo a puntini; con il 3D i puntini stanno su una conca dietro lo schermo
  (profondità regolabile), mentre testi e comandi restano sul piano.
- Modalità cassetta (L+R): bobine che girano e nastro che scorre, come l'OP-1.
- Virtualizzazione: la stessa dell'app Android, regolabile.
- Passaggio fra i brani senza pause (gapless), anche per gli album dal vivo.
- Musica a console chiusa, nelle cuffie: mentre suona il 3DS non va in standby
  e spegne gli schermi. Chiuso, il 3DS spegne gli altoparlanti (come per l'app
  Sound di Nintendo): senza cuffie Xaos si mette in pausa e la console va in
  standby.
- All'uscita ricorda coda, posizione, casuale e ripeti.

## Installazione

1. Per l'icona nella schermata HOME installa `xaos.cia` con FBI. Oppure copia
   `xaos.3dsx` in `sdmc:/3ds/` e avvialo dall'Homebrew Launcher.
2. Copia la musica in `sdmc:/music/`, anche in sottocartelle.
3. Solo la prima volta, se non l'hai mai fatto: avvia **DSP1** dall'Homebrew
   Launcher. Crea `sdmc:/3ds/dspfirm.cdc`, senza cui nessuna app homebrew ha l'audio.

Al primo avvio Xaos legge tutta la musica e salva un indice in
`sdmc:/3ds/xaos/library.db`. Dalle volte dopo parte subito. Dopo aver copiato
musica nuova tieni premuto SELECT (o Impostazioni › Libreria): rilegge solo i
file nuovi o cambiati.

## Comandi

| Tasto | |
|---|---|
| Croce su/giù, pad circolare | scorre la lista |
| Croce sinistra/destra | cambia scheda (sinistra chiude l'album) |
| A | apre l'album / suona il brano |
| B | torna agli album |
| X | play/pausa |
| Y | casuale |
| L / R | brano precedente / successivo; tenuti, indietro/avanti di 5 secondi |
| L + R insieme | modalità cassetta |
| ZL / ZR (New 3DS) | indietro/avanti di 10 secondi |
| SELECT | ripeti (no, tutto, questo brano); tenuto, aggiorna la libreria |
| START | esce |

Sullo schermo sotto si tocca tutto: schede, righe, comandi. Le liste si
scorrono col dito.

## Compilare

Serve [devkitPro](https://devkitpro.org) con le librerie audio per 3DS:

```
pacman -S 3ds-mpg123 3ds-flac 3ds-libvorbisidec 3ds-libogg 3ds-opusfile 3ds-libopus
```

Poi, da Git Bash:

```
./build.sh
```

Gli strumenti di devkitPro non reggono gli spazi nei percorsi, quindi
`build.sh` compila una copia in `~/.xaos-3ds-build` e riporta qui `xaos.3dsx`.
Per il `.cia` servono anche [makerom](https://github.com/3DSGuy/Project_CTR) e
[bannertool](https://github.com/Epicpkmn11/bannertool) in `~/Tools/ctrtools`
(o nella cartella indicata da `CTRTOOLS`). Banner e suono della schermata HOME
si rigenerano con `tools/make_banner.py`, l'icona con `tools/make_icon.py`.

Per provarla sulla console senza togliere la SD: nell'Homebrew Launcher premi
Y, poi `3dslink xaos.3dsx -a <indirizzo del 3DS>`. Per l'emulatore
([Azahar](https://azahar-emu.org)) serve un `dspfirm.cdc` nella sua SD.

## Com'è fatto

| File | |
|---|---|
| `main.c` | avvio, ciclo principale, console chiusa |
| `audio.c` | il motore: un thread decodifica e tiene pieni otto buffer del DSP |
| `decoder.c`, `dec_*.c` | un decoder per formato, tutti escono in stereo a 16 bit |
| `library.c` | scansione, album, indice su SD |
| `player.c` | coda, casuale, ripeti, stato salvato |
| `lyrics.c` | i file `.lrc` |
| `ui.c`, `dotfont*.c` | temi, testo, testo e icone a punti, sfondo in 3D |
| `settings.c` | impostazioni e preset dei temi (file di testo sulla SD) |
| `view_settings.c` | la scheda Impostazioni, il selettore di colore, i comandi |
| `spatial.c` | la virtualizzazione |
| `cia/` | banner, suono e configurazione del pacchetto `.cia` |
| `view_top.c`, `view_bottom.c` | i due schermi |
