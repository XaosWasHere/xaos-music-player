#!/bin/sh
# Compila Xaos per 3DS: ./build.sh (o ./build.sh clean).
# Il risultato è xaos.3dsx (Homebrew Launcher) e xaos.cia (schermata HOME).
#
# Gli strumenti di devkitPro non reggono gli spazi nei percorsi, e questa
# cartella ne ha: si compila in una copia in ~/.xaos-3ds-build.
#
# Per il .cia servono makerom e bannertool in $CTRTOOLS (~/Tools/ctrtools):
# https://github.com/3DSGuy/Project_CTR e https://github.com/Epicpkmn11/bannertool
set -e
here="$(cd "$(dirname "$0")" && pwd)"
out="$HOME/.xaos-3ds-build"
tools="${CTRTOOLS:-$HOME/Tools/ctrtools}"
mkdir -p "$out/source"
cp -u "$here"/source/*.c "$here"/source/*.h "$out/"source/
# Un sorgente cancellato qui sparisce anche dalla copia.
for f in "$out"/source/*; do [ -e "$here/source/$(basename "$f")" ] || rm -f "$f"; done
cp -u "$here/Makefile" "$here/icon.png" "$out/"
DEVKITPRO=/opt/devkitpro DEVKITARM=/opt/devkitpro/devkitARM \
    /c/devkitPro/msys2/usr/bin/bash.exe -lc "cd '$(cygpath -u "$out")' && make $*"
[ "$1" = "clean" ] && { rm -f "$here/xaos.3dsx" "$here/xaos.cia"; exit 0; }
cp "$out/xaos.3dsx" "$here/"

if [ ! -x "$tools/makerom.exe" ] || [ ! -x "$tools/bannertool.exe" ]; then
    echo "makerom o bannertool mancanti in $tools: niente .cia"
    exit 0
fi
cd "$out"
cp -u "$here"/cia/* .
"$tools/bannertool.exe" makebanner -i banner.png -a banner.wav -o banner.bnr > /dev/null
"$tools/bannertool.exe" makesmdh -s "Xaos" -l "Il player musicale di Xaos" -p "XaosWasHere" -i icon.png -o icon.icn > /dev/null
"$tools/makerom.exe" -f cia -o xaos.cia -elf xaos.elf -rsf xaos.rsf -icon icon.icn -banner banner.bnr \
    -exefslogo -target t -major 1 -minor 6 -micro 0
cp xaos.cia "$here/"
echo "built ... xaos.cia"
