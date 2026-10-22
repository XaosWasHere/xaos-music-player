"""L'icona dell'app per l'Homebrew Launcher: una X a punti rossi sul nero, 48x48."""
import sys
from PIL import Image, ImageDraw

X = ["10001", "10001", "01010", "00100", "01010", "10001", "10001"]
SIZE, PITCH = 48, 6
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
d = ImageDraw.Draw(img)
x0 = (SIZE - 5 * PITCH) // 2
y0 = (SIZE - 7 * PITCH) // 2
r = PITCH * 0.38
for row, bits in enumerate(X):
    for col, b in enumerate(bits):
        cx = x0 + col * PITCH + PITCH / 2
        cy = y0 + row * PITCH + PITCH / 2
        color = (0xD7, 0x19, 0x21, 255) if b == "1" else (255, 255, 255, 40)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color)
img.save(sys.argv[1] if len(sys.argv) > 1 else "icon.png")
