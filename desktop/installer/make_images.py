"""
Le immagini dell'installer di Xaos, nello stile dell'app.

Una variante scura (nero, punti bianchi, accento rosso) e una chiara (grigio
chiarissimo, punti neri, accento giallo), come i due temi dell'app: l'installer
sceglie quella che corrisponde al tema di Windows.

Si rigenerano con:  python make_images.py
"""
from PIL import Image, ImageDraw, ImageFont
import os

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = r"C:\Windows\Fonts\CascadiaMono.ttf"

THEMES = {
    "dark": dict(bg=(0, 0, 0), ink=(242, 242, 242), dim=(34, 34, 34), sub=(146, 146, 146), accent=(215, 25, 33)),
    "light": dict(bg=(242, 242, 242), ink=(28, 28, 28), dim=(214, 214, 214), sub=(96, 96, 96), accent=(255, 199, 0)),
}

# La matrice 5x7 dell'app (DotText.kt).
GLYPHS = {
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
}

# Le dimensioni che Inno Setup si aspetta a 100%, 150% e 200% di scala.
BIG = [(164, 314), (246, 459), (328, 604)]
SMALL = [(55, 58), (83, 80), (110, 106)]


def dot(d, cx, cy, r, color):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)


def dot_grid(d, w, h, pitch, r, color):
    y = pitch / 2
    while y < h:
        x = pitch / 2
        while x < w:
            dot(d, x, y, r, color)
            x += pitch
        y += pitch


def big(size, t):
    w, h = size
    s = 4  # si disegna in grande e si riduce: i punti vengono tondi
    img = Image.new("RGB", (w * s, h * s), t["bg"])
    d = ImageDraw.Draw(img)
    unit = w * s / 164
    dot_grid(d, w * s, h * s, 9 * unit, 0.7 * unit, t["dim"])

    # XAOS a matrice di punti, e il punto d'accento dopo, come nella barra laterale.
    word = "XAOS"
    cols = len(word) * 6 - 1
    pitch = (w * s * 0.70) / (cols + 2)
    x0 = w * s * 0.14
    y0 = h * s * 0.36
    r = pitch * 0.36
    for i, ch in enumerate(word):
        for row, bits in enumerate(GLYPHS[ch]):
            for col, bit in enumerate(bits):
                if bit == "1":
                    dot(d, x0 + (i * 6 + col) * pitch + pitch / 2, y0 + row * pitch + pitch / 2, r, t["ink"])
    dot(d, x0 + (cols + 1.2) * pitch, y0 + 6.5 * pitch, pitch * 0.55, t["accent"])

    font = ImageFont.truetype(FONT, int(7.2 * unit))
    d.text((x0, y0 + 9.5 * pitch), "MUSIC PLAYER · DESKTOP", font=font, fill=t["sub"])
    # In fondo, la X dell'icona in piccolo.
    cx, cy, k = w * s * 0.5, h * s * 0.84, 5.2 * unit
    for a in (-2, -1, 1, 2):
        for b in (-1, 1):
            dot(d, cx + a * k, cy + a * b * k, (1.6 if abs(a) == 2 else 2.2) * unit, t["ink"])
    dot(d, cx, cy, 2.8 * unit, t["accent"])
    return img.resize((w, h), Image.LANCZOS)


def small(size, t):
    w, h = size
    s = 4
    img = Image.new("RGB", (w * s, h * s), t["bg"])
    d = ImageDraw.Draw(img)
    cx, cy = w * s / 2, h * s / 2
    k = min(w, h) * s * 0.105
    for a in (-3, -2, -1, 1, 2, 3):
        for b in (-1, 1):
            r = k * (0.30 if abs(a) == 3 else 0.42 if abs(a) == 2 else 0.55)
            dot(d, cx + a * k, cy + a * b * k, r, t["ink"])
    dot(d, cx, cy, k * 0.68, t["accent"])
    return img.resize((w, h), Image.LANCZOS)


for name, t in THEMES.items():
    for (w, h) in BIG:
        big((w, h), t).save(os.path.join(HERE, f"wizard-{name}-{w}.bmp"))
    for (w, h) in SMALL:
        small((w, h), t).save(os.path.join(HERE, f"small-{name}-{w}.bmp"))
print("ok")
