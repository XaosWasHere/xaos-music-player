"""Porta i preset dei temi di Xaos per PC sul 3DS.

Legge ~/.xaos/settings.json e scrive themes.txt nella cartella dei dati di
Xaos sulla SD (per esempio E:/3ds/xaos/themes.txt):

    python export_themes.py E:/3ds/xaos/themes.txt
"""
import json
import os
import sys


def color(v):
    return "-" if v is None else "%08X" % (v & 0xFFFFFFFF)


settings = json.load(open(os.path.expanduser("~/.xaos/settings.json"), encoding="utf-8"))
out = sys.argv[1]
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf-8", newline="\n") as f:
    f.write("# Preset dei temi di Xaos: nome, sfondo, sfumatura, pannelli, testo, accento (ARGB o -), pallini\n")
    for p in settings.get("themePresets", []):
        t = p["theme"]
        fields = [p["name"][:31].replace("\t", " ")]
        fields += [color(t.get(k)) for k in ("background", "background2", "panels", "ink", "accent")]
        fields.append("1" if t.get("dots", True) else "0")
        f.write("\t".join(fields) + "\n")
print(len(settings.get("themePresets", [])), "preset ->", out)
