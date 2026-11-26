"""Rendu isométrique (jour ou nuit) d'un vidage de blocs « x y z etat » écrit par TestRender.java.

usage : python -I render_iso.py <vidage.txt|dossier de vidages> <sortie.png|.gif> [--nuit] [--px N] [--vue no|ne|so|se]
        [--yaw-z] ; un dossier = toutes les images parc_*.txt en GIF animé.
Les couleurs viennent de .claude/skills/builder-minecraft/outils/colors.py (textures du jar client 26.3).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageChops

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ICI, '..', '..', '.claude', 'skills', 'builder-minecraft', 'outils'))
import colors  # noqa: E402

LIGHT = ('froglight', 'sea_lantern', 'shroomlight', 'glowstone', 'lantern', 'end_rod', 'redstone_lamp', 'lava',
         'jack_o_lantern', 'torch', 'campfire')
THIN = ('chain', 'bars', 'fence', '_wall', 'lantern', 'end_rod', 'pane', 'rail', 'torch', 'flower', 'poppy',
        'dandelion', 'cornflower', 'allium', 'daisy', 'short_grass', 'tall_grass', 'fern', 'bush')


def parse(path):
    """Blocs {(x, y, z): etat} et boîtes [(etat, glow, 8 coins)]."""
    blocks, boxes = {}, []
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('box '):
                p = line.split()
                pts = [float(v) for v in p[3:27]]
                boxes.append((p[1], p[2] == '1', [tuple(pts[i:i + 3]) for i in range(0, 24, 3)]))
                continue
            p = line.split(' ', 3)
            if len(p) < 4:
                continue
            blocks[(int(p[0]), int(p[1]), int(p[2]))] = p[3].strip()
    return blocks, boxes


def name_of(st):
    n = st.split('[')[0]
    return n.split(':')[-1]


_ccache = {}


def col(n):
    c = _ccache.get(n)
    if c is None:
        c = colors.color(n) or (90, 90, 90)
        _ccache[n] = c
    return c


def shape(st, n):
    """(hauteur, largeur) du cube dessiné."""
    if n.endswith('_slab'):
        return (0.5 if 'type=top' not in st and 'type=double' not in st else 1.0), 1.0
    if n.endswith('_carpet') or n == 'rail' or n.endswith('pressure_plate'):
        return 0.12, 1.0
    if any(t in n for t in THIN):
        return 1.0, 0.4
    if n.endswith('_stairs'):
        return 0.75, 1.0
    return 1.0, 1.0


def rot(vue, x, y, z):
    if vue == 'se':
        return x, y, z
    if vue == 'no':
        return -x, y, -z
    if vue == 'ne':
        return -z, y, x
    return z, y, -x  # so


def transform(scene, vue):
    """Tourne la scène pour que la caméra regarde depuis le coin demandé (rotation, jamais un miroir)."""
    blocks, boxes = scene
    nb = {}
    for (x, y, z), st in blocks.items():
        # un bloc occupe [x, x+1] : après rotation, on garde le coin minimal
        cx, cy, cz = rot(vue, x + 0.5, y, z + 0.5)
        nb[(int(round(cx - 0.5)), y, int(round(cz - 0.5)))] = st
    nbox = [(st, gl, [rot(vue, *c) for c in cs]) for st, gl, cs in boxes]
    return nb, nbox


FACES = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]


def render(scene, px, nuit):
    blocks, boxes = scene
    pts = [k for k in blocks] + [c for _, _, cs in boxes for c in cs]
    if not pts:
        return Image.new('RGB', (10, 10))
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    zs = [p[2] for p in pts]
    a, b, c = px, px / 2, px
    x0, x1, z0, z1, y0, y1 = min(xs), max(xs) + 1, min(zs), max(zs) + 1, min(ys), max(ys) + 1
    W = int((x1 - x0 + z1 - z0 + 2) * a) + 4 * px
    H = int((x1 - x0 + z1 - z0 + 2) * b + (y1 - y0 + 2) * c) + 4 * px
    ox = (z1 - z0 + 1) * a + 2 * px
    oy = (y1 - y0 + 1) * c + 2 * px
    sky = (12, 14, 32) if nuit else (170, 200, 235)
    img = Image.new('RGB', (W, H), sky)
    glow = Image.new('RGB', (W, H), (0, 0, 0))
    d = ImageDraw.Draw(img)
    dg = ImageDraw.Draw(glow)

    def P(x, y, z):
        return (ox + (x - x0 - (z - z0)) * a, oy + (x - x0 + z - z0) * b - (y - y0) * c)

    def tint(n, st, lit):
        r, g, bl = col(n)
        if nuit and not lit:
            r, g, bl = int(r * 0.30 + 6), int(g * 0.30 + 7), int(bl * 0.34 + 16)
        return r, g, bl

    faces = []  # (profondeur, polygone, couleur)
    lights = []
    occ = set(blocks)
    for k, st in blocks.items():
        n = name_of(st)
        x, y, z = k
        h, w = shape(st, n)
        lit = any(t in n for t in LIGHT) or 'lit=true' in st
        r, g, bl = tint(n, st, lit)
        m = (1 - w) / 2
        xa, xb, za, zb = x + m, x + 1 - m, z + m, z + 1 - m
        full = w == 1.0 and h == 1.0
        base = x + z + y
        if not full or (x, y + 1, z) not in occ:
            faces.append((base + 2 + h - 1, [P(xa, y + h, za), P(xb, y + h, za), P(xb, y + h, zb), P(xa, y + h, zb)],
                          (min(255, int(r * 1.08)), min(255, int(g * 1.08)), min(255, int(bl * 1.08)))))
        if not full or (x + 1, y, z) not in occ:
            faces.append((base + 2 - m, [P(xb, y, za), P(xb, y, zb), P(xb, y + h, zb), P(xb, y + h, za)],
                          (int(r * 0.78), int(g * 0.78), int(bl * 0.78))))
        if not full or (x, y, z + 1) not in occ:
            faces.append((base + 2 - m, [P(xa, y, zb), P(xb, y, zb), P(xb, y + h, zb), P(xa, y + h, zb)],
                          (int(r * 0.62), int(g * 0.62), int(bl * 0.62))))
        if nuit and lit:
            lights.append((P(x + 0.5, y + 0.5, z + 0.5), (r, g, bl), 1.0))
    for st, gl, cs in boxes:
        n = name_of(st)
        lit = gl or any(t in n for t in LIGHT)
        r, g, bl = tint(n, st, lit)
        cx = sum(p[0] for p in cs) / 8
        cy = sum(p[1] for p in cs) / 8
        cz = sum(p[2] for p in cs) / 8
        for f in FACES:
            q = [cs[i] for i in f]
            fx = sum(p[0] for p in q) / 4
            fy = sum(p[1] for p in q) / 4
            fz = sum(p[2] for p in q) / 4
            nx, ny, nz = fx - cx, fy - cy, fz - cz
            ln = (nx * nx + ny * ny + nz * nz) ** 0.5 or 1
            nx, ny, nz = nx / ln, ny / ln, nz / ln
            if nx + ny + nz <= 0.02:
                continue
            sh = 0.62 + 0.46 * max(0.0, ny) + 0.16 * max(0.0, nx)
            faces.append((fx + fy + fz, [P(*p) for p in q],
                          (min(255, int(r * sh)), min(255, int(g * sh)), min(255, int(bl * sh)))))
        if nuit and lit:
            lights.append((P(cx, cy, cz), (r, g, bl), 0.7))
    faces.sort(key=lambda t: t[0])
    for _, poly, colr in faces:
        d.polygon(poly, fill=colr)
    if nuit:
        for (cx, cy), (r, g, bl), k in lights:
            rad = px * 1.3 * k
            dg.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=(int(r * 0.55), int(g * 0.48), int(bl * 0.36)))
        g1 = glow.filter(ImageFilter.GaussianBlur(px * 3))
        g2 = glow.filter(ImageFilter.GaussianBlur(px * 9))
        img = ImageChops.add(img, g1)
        img = ImageChops.add(img, ImageChops.multiply(g2, Image.new('RGB', g2.size, (90, 90, 90))))
    return img


def main():
    a = sys.argv[1:]
    src, dst = a[0], a[1]
    nuit = '--nuit' in a
    px = int(a[a.index('--px') + 1]) if '--px' in a else 6
    vue = a[a.index('--vue') + 1] if '--vue' in a else 'no'
    if os.path.isdir(src):
        files = sorted(f for f in os.listdir(src) if f.startswith('parc_') and f.endswith('.txt'))
        frames = []
        for f in files:
            im = render(transform(parse(os.path.join(src, f)), vue), px, nuit)
            frames.append(im.convert('P', palette=Image.ADAPTIVE, colors=255))
            print(f, im.size)
        frames[0].save(dst, save_all=True, append_images=frames[1:], duration=110, loop=0, optimize=False)
    else:
        im = render(transform(parse(src), vue), px, nuit)
        im.save(dst)
        print(dst, im.size)
    colors.save()


if __name__ == '__main__':
    main()
