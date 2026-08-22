"""Generate SpaceKai icons: gradient space background + music note + stars."""
import math
import os
from PIL import Image, ImageDraw, ImageFilter

SIZE = 432
OUT = os.path.dirname(os.path.abspath(__file__))

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def radial_gradient(size, c_center, c_edge):
    """Vertical-ish radial gradient."""
    img = Image.new("RGB", (size, size), c_edge)
    d = ImageDraw.Draw(img)
    cx, cy = size * 0.5, size * 0.42
    max_r = size * 0.75
    for y in range(size):
        for x in range(0, size, 2):
            r = math.sqrt((x - cx) ** 2 + (y - cy) ** 2) / max_r
            t = min(1.0, r)
            d.line([(x, y), (x + 1, y)], fill=lerp(c_center, c_edge, t))
    return img.filter(ImageFilter.GaussianBlur(6))

def draw_stars(d, size, seed=7):
    import random
    rnd = random.Random(seed)
    stars = []
    for _ in range(26):
        x = rnd.uniform(0, size)
        y = rnd.uniform(0, size * 0.92)
        rad = rnd.uniform(1.0, 2.6)
        alpha = rnd.uniform(120, 255)
        stars.append((x, y, rad, int(alpha)))
    for x, y, rad, alpha in stars:
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=(255, 255, 255, alpha))
    # A few 4-point sparkles
    for _ in range(6):
        x = rnd.uniform(size * 0.15, size * 0.85)
        y = rnd.uniform(size * 0.12, size * 0.8)
        r = rnd.uniform(3, 5)
        alpha = rnd.randint(180, 255)
        d.line([(x - r, y), (x + r, y)], fill=(255, 255, 255, alpha), width=2)
        d.line([(x, y - r), (x, y + r)], fill=(255, 255, 255, alpha), width=2)

def draw_music_note(d, cx, cy, s, fill=(255, 255, 255, 255)):
    """Double eighth note: two noteheads + stems + beam."""
    # Note heads (two ellipses at 45deg)
    head_w, head_h = s * 0.52, s * 0.38
    for dx in (-s * 0.62, s * 0.62):
        hx, hy = cx + dx, cy + s * 0.18
        # rotate: draw ellipse manually via polygon approximation
        pts = []
        for i in range(24):
            ang = 2 * math.pi * i / 24
            ex = hx + (head_w * 0.5 * math.cos(ang) - head_h * 0.5 * math.sin(ang)) * 1.1
            ey = hy + (head_w * 0.5 * math.sin(ang) + head_h * 0.5 * math.cos(ang)) * 1.1
            pts.append((ex, ey))
        d.polygon(pts, fill=fill)
        # Stem
        top_y = hy - head_h * 0.5 - s * 1.35
        d.line([(hx + head_w * 0.28, hy - head_h * 0.15), (hx + head_w * 0.28, top_y + s * 0.1)],
               fill=fill, width=max(4, int(s * 0.16)))
        # Beam (double)
        beam_y1 = top_y
        beam_y2 = top_y + s * 0.30
        x1 = cx - s * 0.62 + head_w * 0.28
        x2 = cx + s * 0.62 + head_w * 0.28
        d.polygon([(x1 - s * 0.06, beam_y1), (x2 + s * 0.10, beam_y1),
                   (x2 + s * 0.10, beam_y1 + s * 0.16), (x1 - s * 0.06, beam_y1 + s * 0.16)],
                  fill=fill)
        d.polygon([(x1 - s * 0.06, beam_y1 + s * 0.28), (x2 + s * 0.10, beam_y1 + s * 0.28),
                   (x2 + s * 0.10, beam_y1 + s * 0.44), (x1 - s * 0.06, beam_y1 + s * 0.44)],
                  fill=fill)

def make_icon(size, circle=True):
    img = radial_gradient(size, (108, 92, 231), (10, 10, 40))
    img = img.convert("RGBA")
    d = ImageDraw.Draw(img)
    draw_stars(d, size, seed=7)
    note_s = size * 0.30
    draw_music_note(d, size * 0.5, size * 0.52, note_s)
    if circle:
        # Round mask
        mask = Image.new("L", (size, size), 0)
        md = ImageDraw.Draw(mask)
        md.ellipse([0, 0, size, size], fill=255)
        img.putalpha(mask)
    return img

def make_foreground(size):
    """Adaptive icon foreground: icon occupies central ~66% of canvas, transparent bg."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(size * 0.62)
    icon = make_icon(inner, circle=True)
    img.paste(icon, ((size - inner) // 2, (size - inner) // 2), icon)
    return img

# 1. composeApp source icons (432x432, square with rounded look)
for name in ["app_icon", "circle_app_icon"]:
    icon = make_icon(SIZE, circle=(name == "circle_app_icon"))
    p = os.path.join(OUT, "..", "src", "commonMain", "composeResources", "drawable", f"{name}.png")
    icon.save(os.path.normpath(p), "PNG")
    print("wrote", os.path.normpath(p))

# 2. Conveyor icon (composeApp/icon/circle_app_icon.png)
icon = make_icon(SIZE, circle=True)
icon.save(os.path.join(OUT, "..", "icon", "circle_app_icon.png"), "PNG")
print("wrote composeApp/icon/circle_app_icon.png")

# 3. Android mipmaps
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
res = os.path.normpath(os.path.join(OUT, "..", "..", "androidApp", "src", "main", "res"))
for dens, mult in DENSITIES.items():
    launcher = make_icon(int(48 * mult), circle=False)
    launcher.save(os.path.join(res, f"mipmap-{dens}", "ic_launcher.webp"), "WEBP", quality=90)
    round_ic = make_icon(int(48 * mult), circle=True)
    round_ic.save(os.path.join(res, f"mipmap-{dens}", "ic_launcher_round.webp"), "WEBP", quality=90)
    fg = make_foreground(int(108 * mult))
    fg.save(os.path.join(res, f"mipmap-{dens}", "ic_launcher_foreground.webp"), "WEBP", quality=90)
    print("wrote", dens)

print("DONE")
