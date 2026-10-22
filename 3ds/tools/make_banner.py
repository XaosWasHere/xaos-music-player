"""Il banner della schermata HOME (256x128) e il suo suono, per il pacchetto .cia.

Lo stesso stile dell'app: nero, puntini spenti di sfondo, XAOS a punti rossi.
"""
import math
import struct
import sys
import wave

from PIL import Image, ImageDraw

GLYPHS = {
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "Y": ["10001", "10001", "10001", "01010", "00100", "00100", "00100"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "I": ["01110", "00100", "00100", "00100", "00100", "00100", "01110"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    " ": ["00000"] * 7,
}
RED = (0xD7, 0x19, 0x21, 255)
INK3 = (0x92, 0x92, 0x92, 255)


def dot_text(d, text, x0, y0, pitch, color, radius):
    for ch in text:
        for row, bits in enumerate(GLYPHS[ch]):
            for col, b in enumerate(bits):
                if b == "1":
                    cx = x0 + col * pitch + pitch / 2
                    cy = y0 + row * pitch + pitch / 2
                    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=color)
        x0 += 6 * pitch


def width(text, pitch):
    return (len(text) * 6 - 1) * pitch


def banner(path):
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    for y in range(4, 128, 8):
        for x in range(4, 256, 8):
            d.point((x, y), fill=(255, 255, 255, 60))
    p = 8
    dot_text(d, "XAOS", (256 - width("XAOS", p)) / 2, 22, p, RED, p * 0.38)
    p = 2
    dot_text(d, "PLAYER MUSICALE", (256 - width("PLAYER MUSICALE", p)) / 2, 94, p, INK3, p * 0.45)
    img.save(path)


def chime(path):
    """Tre note che si spengono, come un display che si accende."""
    rate = 22050
    notes = [(0.00, 659.25), (0.12, 987.77), (0.24, 1318.51)]
    length = 1.6
    n = int(rate * length)
    samples = []
    for i in range(n):
        t = i / rate
        v = 0.0
        for start, f in notes:
            if t >= start:
                tt = t - start
                v += math.sin(2 * math.pi * f * tt) * math.exp(-tt * 4.5) * 0.28
        samples.append(int(max(-1, min(1, v)) * 32767))
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(struct.pack("<%dh" % n, *samples))


banner(sys.argv[1])
chime(sys.argv[2])
