# -*- coding: utf-8 -*-
"""Genere les fichiers de pack de Squid Game (EemiSoleil 2.0, namespace eemicraft, dossier squid/).

Usage : python -I gen_squid_pack.py
Ecrit dans EemiSoleil/pack/assets/eemicraft/... et les apercus dans EemiSoleil/pack_src/apercu/.

- equipement : survetement vert des joueurs (asset eemicraft:squid_joueur generique + squid_joueur_01..30 avec le
  numero sur la poitrine et dans le dos ; couches humanoid + humanoid_leggings) ; combinaison rose des gardes avec
  masque noir au symbole (eemicraft:squid_garde_rond / _triangle / _carre ; casque = masque, bottes noires) ;
- sprites 16x16 (item/generated) : icones des tenues et masques, aiguille, dalgona par forme, symboles des boites,
  corde, sac de billes, bille, pair, impair, couteau, carte d'invitation ;
- plaques 3D fines (16 x 16 x 1) pour les cases du biscuit dalgona (case, trait grave, fissure), vues par ItemDisplay.
Tout est dessine ici (aucune texture reprise d'un autre pack). Graine fixe : sortie identique a chaque lancement.
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.normpath(os.path.join(HERE, "..", "pack", "assets", "eemicraft"))
APERCU = os.path.join(HERE, "apercu")
NS = "eemicraft"

TEX_ITEM = os.path.join(PACK, "textures", "item", "squid")
MODELS = os.path.join(PACK, "models", "item", "squid")
ITEMS = os.path.join(PACK, "items", "squid")
EQUIP = os.path.join(PACK, "equipment")
TEX_EQ = os.path.join(PACK, "textures", "entity", "equipment")
for d in (TEX_ITEM, MODELS, ITEMS, EQUIP, os.path.join(TEX_EQ, "humanoid"), os.path.join(TEX_EQ, "humanoid_leggings"), APERCU):
    os.makedirs(d, exist_ok=True)

WRITTEN = []


def write_json(path, obj):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")
    WRITTEN.append(path)


def save_png(img, path):
    img.save(path, optimize=True)
    WRITTEN.append(path)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def jit(c, rnd, a):
    d = rnd.randint(-a, a)
    return tuple(clamp(v + d) for v in c[:3])


# ============================================================ couleurs de la serie

GREEN = (47, 143, 122)       # survetement
GREEN_D = (30, 104, 88)
WHITE = (238, 238, 232)
PINK = (224, 69, 123)        # gardes
PINK_D = (170, 44, 90)
BLACK = (22, 22, 24)
CARAMEL = (214, 150, 64)     # dalgona
CARAMEL_L = (232, 178, 92)
CARAMEL_D = (150, 92, 34)

DIGITS = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
}


def put(im, x, y, c):
    im.putpixel((x, y), tuple(c[:3]) + (255,))


def rect(im, x0, y0, w, h, fn):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c = fn(x - x0, y - y0)
            if c is not None:
                put(im, x, y, c)


def digits(im, x0, y0, text, col):
    for k, ch in enumerate(text):
        g = DIGITS[ch]
        for r in range(5):
            for c in range(3):
                if g[r][c] == "1":
                    put(im, x0 + k * 4 + c, y0 + r, col)


# ============================================================ equipement (64x32, format armure humanoide)

def tracksuit(num):
    """Couche humanoid du survetement : torse + bras ; numero sur la poitrine et dans le dos."""
    rnd = random.Random(700 + (num or 0))
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    def g(k=1.0):
        return shade(jit(GREEN, rnd, 3), k)
    rect(im, 20, 16, 8, 4, lambda x, y: g(1.08))            # epaules
    rect(im, 28, 16, 8, 4, lambda x, y: g(0.7))
    rect(im, 16, 20, 4, 12, lambda x, y: WHITE if x == 1 else g(0.9))   # cotes : passepoil blanc
    rect(im, 28, 20, 4, 12, lambda x, y: WHITE if x == 2 else g(0.9))

    def front(x, y):
        if y == 0:
            return WHITE                                   # col
        if y == 11:
            return g(0.75)                                 # bas elastique
        if x in (3, 4) and y < 11:
            return shade(GREEN_D, 1.0) if y % 2 else shade(GREEN_D, 0.85)   # fermeture
        return g()
    rect(im, 20, 20, 8, 12, front)
    rect(im, 32, 20, 8, 12, lambda x, y: WHITE if y == 0 else g(0.75) if y == 11 else g(0.95))
    if num:
        txt = "%02d" % (num % 100)
        # poitrine : plaque blanche avec le numero (cote gauche du coeur, sur toute la largeur faute de place)
        rect(im, 20, 21, 8, 7, lambda x, y: WHITE)
        digits(im, 21, 22, txt, BLACK)
        # dos : grand numero blanc
        digits(im, 33, 23, txt, WHITE)
    else:
        rect(im, 21, 22, 2, 3, lambda x, y: WHITE)
    # bras : vert, bande blanche le long du bras, poignet fonce
    rect(im, 44, 16, 4, 4, lambda x, y: g(1.05))
    rect(im, 48, 16, 4, 4, lambda x, y: g(0.7))
    for x0 in (40, 44, 48, 52):
        rect(im, x0, 20, 4, 12, lambda x, y, x0=x0: g(0.7) if y == 11 else WHITE if (x0 == 40 and x == 1) or (x0 == 48 and x == 2) else g())
    return im


def tracksuit_legs():
    rnd = random.Random(760)
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    def g(k=1.0):
        return shade(jit(GREEN, rnd, 3), k)
    rect(im, 4, 16, 4, 4, lambda x, y: g())
    rect(im, 8, 16, 4, 4, lambda x, y: g(0.7))
    for x0, face in ((0, "r"), (4, "f"), (8, "l"), (12, "b")):
        rect(im, x0, 20, 4, 12, lambda x, y, face=face: WHITE if (face == "r" and x == 1) or (face == "l" and x == 2)
             else g(0.72) if y == 11 else g(0.95 if face == "b" else 1.0))
    # ceinture elastique (bas du torse)
    for x0, w in ((16, 4), (20, 8), (28, 4), (32, 8)):
        rect(im, x0, 20, w, 12, lambda x, y: None if y < 9 else (g(0.8) if y == 9 else g()))
    return im


SYMBOLS = {
    "rond": [(1, 0), (2, 0), (3, 0), (0, 1), (4, 1), (0, 2), (4, 2), (0, 3), (4, 3), (1, 4), (2, 4), (3, 4)],
    "triangle": [(2, 0), (1, 1), (3, 1), (1, 2), (3, 2), (0, 3), (4, 3), (0, 4), (1, 4), (2, 4), (3, 4), (4, 4)],
    "carre": [(x, y) for x in range(5) for y in range(5) if x in (0, 4) or y in (0, 4)],
}


def guard(mask):
    rnd = random.Random(800 + len(mask))
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    def p(k=1.0):
        return shade(jit(PINK, rnd, 4), k)
    # capuche rose (dessus, cotes, arriere), masque noir devant avec le symbole blanc
    rect(im, 8, 0, 8, 8, lambda x, y: p(1.05))
    rect(im, 16, 0, 8, 8, lambda x, y: p(0.7))
    rect(im, 0, 8, 8, 8, lambda x, y: p(0.92) if x < 6 else BLACK)
    rect(im, 16, 8, 8, 8, lambda x, y: p(0.92) if x > 1 else BLACK)
    rect(im, 24, 8, 8, 8, lambda x, y: p(0.9) if y < 7 else p(0.75))
    rect(im, 8, 8, 8, 8, lambda x, y: jit(BLACK, rnd, 3))
    for (x, y) in SYMBOLS[mask]:
        put(im, 9 + x + (1 if mask != "rond" or True else 0) - 1 + 1, 9 + y + 1, WHITE)
    put(im, 9, 8, shade(BLACK, 1.6))
    # combinaison
    rect(im, 20, 16, 8, 4, lambda x, y: p(1.08))
    rect(im, 28, 16, 8, 4, lambda x, y: p(0.7))
    rect(im, 16, 20, 4, 12, lambda x, y: BLACK if y == 9 else p(0.9))
    rect(im, 28, 20, 4, 12, lambda x, y: BLACK if y == 9 else p(0.9))
    rect(im, 20, 20, 8, 12, lambda x, y: BLACK if y == 9 else (150, 150, 156) if (y == 9 and x == 4) else
         PINK_D if x in (3, 4) and y < 9 else p())
    rect(im, 32, 20, 8, 12, lambda x, y: BLACK if y == 9 else p(0.92))
    put(im, 23, 29, (170, 170, 176))                     # boucle de ceinture
    rect(im, 44, 16, 4, 4, lambda x, y: p(1.05))
    rect(im, 48, 16, 4, 4, lambda x, y: BLACK)
    for x0 in (40, 44, 48, 52):
        rect(im, x0, 20, 4, 12, lambda x, y: BLACK if y >= 10 else p())   # gants noirs
    # bottes noires (bas des jambes)
    for x0 in (0, 4, 8, 12):
        rect(im, x0, 20, 4, 12, lambda x, y: None if y < 7 else jit(BLACK, rnd, 3) if y < 11 else shade(BLACK, 0.7))
    rect(im, 8, 16, 4, 4, lambda x, y: shade(BLACK, 0.7))
    return im


def guard_legs():
    rnd = random.Random(850)
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    def p(k=1.0):
        return shade(jit(PINK, rnd, 4), k)
    rect(im, 4, 16, 4, 4, lambda x, y: p())
    rect(im, 8, 16, 4, 4, lambda x, y: p(0.7))
    for x0 in (0, 4, 8, 12):
        rect(im, x0, 20, 4, 12, lambda x, y: p(0.95))
    for x0, w in ((16, 4), (20, 8), (28, 4), (32, 8)):
        rect(im, x0, 20, w, 12, lambda x, y: None if y < 10 else p())
    return im


TRACK_LEGS = tracksuit_legs()
save_png(tracksuit(0), os.path.join(TEX_EQ, "humanoid", "squid_joueur.png"))
save_png(TRACK_LEGS, os.path.join(TEX_EQ, "humanoid_leggings", "squid_joueur.png"))
write_json(os.path.join(EQUIP, "squid_joueur.json"), {"layers": {
    "humanoid": [{"texture": NS + ":squid_joueur"}],
    "humanoid_leggings": [{"texture": NS + ":squid_joueur"}]}})
NUMBERED = {}
for n in range(1, 31):
    nm = "squid_joueur_%02d" % n
    im = tracksuit(n)
    NUMBERED[n] = im
    save_png(im, os.path.join(TEX_EQ, "humanoid", nm + ".png"))
    write_json(os.path.join(EQUIP, nm + ".json"), {"layers": {
        "humanoid": [{"texture": NS + ":" + nm}],
        "humanoid_leggings": [{"texture": NS + ":squid_joueur"}]}})
GUARDS = {}
GLEGS = guard_legs()
save_png(GLEGS, os.path.join(TEX_EQ, "humanoid_leggings", "squid_garde.png"))
for mk in ("rond", "triangle", "carre"):
    nm = "squid_garde_" + mk
    GUARDS[mk] = guard(mk)
    save_png(GUARDS[mk], os.path.join(TEX_EQ, "humanoid", nm + ".png"))
    write_json(os.path.join(EQUIP, nm + ".json"), {"layers": {
        "humanoid": [{"texture": NS + ":" + nm}],
        "humanoid_leggings": [{"texture": NS + ":squid_garde"}]}})


# ============================================================ sprites 16x16

OUT = (20, 14, 10)


def new16():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def outline(img, col=OUT):
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] != 0:
                    img.putpixel((x, y), col + (255,))
                    break
    return img


def poly(img, pts, col):
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=col + (255,))


def sp_jacket(main, accent, patch=True):
    im = new16()
    poly(im, [(3, 3), (6, 2), (9, 2), (12, 3), (14, 8), (12, 9), (12, 14), (3, 14), (3, 9), (1, 8)], main)
    for y in range(3, 14):
        put(im, 7, y, shade(main, 0.7))
    for (x, y) in ((6, 2), (7, 3), (8, 2)):
        put(im, x, y, accent)
    for y in range(4, 9):
        put(im, 2 if y < 8 else 2, y, accent) if y > 6 else None
    if patch:
        rect(im, 9, 5, 3, 2, lambda x, y: WHITE)
    return outline(im)


def sp_pants(main, accent):
    im = new16()
    poly(im, [(4, 2), (11, 2), (12, 14), (9, 14), (8, 7), (7, 7), (6, 14), (3, 14)], main)
    for y in range(3, 14):
        put(im, 4 if y > 2 else 4, y, accent) if y % 1 == 0 and accent else None
    rect(im, 4, 2, 8, 1, lambda x, y: shade(main, 0.75))
    return outline(im)


def sp_boots():
    im = new16()
    poly(im, [(3, 6), (6, 6), (6, 12), (8, 12), (8, 14), (3, 14)], BLACK)
    poly(im, [(9, 5), (12, 5), (12, 11), (14, 11), (14, 13), (9, 13)], shade(BLACK, 1.4))
    return outline(im, (60, 60, 64))


def sp_mask(mk):
    im = new16()
    d = ImageDraw.Draw(im)
    d.ellipse([2, 1, 13, 14], fill=BLACK + (255,))
    for (x, y) in SYMBOLS[mk]:
        put(im, 5 + x, 5 + y, WHITE)
    put(im, 4, 3, (70, 70, 74))
    return outline(im, (60, 60, 64))


def sp_needle():
    im = new16()
    for k in range(11):
        put(im, 3 + k, 12 - k, mix((210, 214, 222), (120, 124, 132), k / 10))
    put(im, 13, 2, (90, 92, 98))
    put(im, 12, 2, (200, 204, 210))
    put(im, 13, 3, (200, 204, 210))
    put(im, 2, 13, (250, 250, 250))
    return outline(im, (40, 40, 46))


def shape_cells(forme):
    """Masque 11x11 (meme definition que EpDalgona) -> liste des cases."""
    def star(x, y):
        pts = []
        for k in range(10):
            a = -math.pi / 2 + k * math.pi / 5
            r = 4.6 if k % 2 == 0 else 2.1
            pts.append((5 + r * math.cos(a), 5.3 + r * math.sin(a)))
        inside = False
        j = 9
        for i in range(10):
            if (pts[i][1] > y) != (pts[j][1] > y) and x < (pts[j][0] - pts[i][0]) * (y - pts[i][1]) / (pts[j][1] - pts[i][1]) + pts[i][0]:
                inside = not inside
            j = i
        return inside
    out = set()
    for j in range(11):
        for i in range(11):
            if forme == "rond":
                ok = math.hypot(i - 5, j - 5) <= 3.6
            elif forme == "triangle":
                ok = 2 <= j <= 8 and abs(i - 5) <= (j - 2) * 0.5 + 0.4
            elif forme == "etoile":
                ok = star(i, j)
            else:
                ok = (1 <= j <= 5 and math.hypot(i - 5, (j - 5) * 1.05) <= 4.2) or (i == 5 and 5 <= j <= 8) or (j, i) in ((8, 4), (8, 3), (7, 3))
            if ok:
                out.add((i, j))
    return out


def sp_dalgona(forme):
    rnd = random.Random(900 + len(forme))
    im = new16()
    d = ImageDraw.Draw(im)
    d.ellipse([0, 0, 15, 15], fill=CARAMEL + (255,))
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] and rnd.random() < 0.25:
                put(im, x, y, jit(CARAMEL_L, rnd, 8))
    cells = shape_cells(forme)
    for (i, j) in cells:
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (i + dx, j + dy) not in cells:
                put(im, i + 2 + dx, j + 2 + dy, CARAMEL_D)
    return outline(im, (110, 66, 22))


TIN = {"rond": (236, 196, 60), "triangle": (226, 92, 140), "etoile": (110, 182, 220), "parapluie": (146, 92, 190)}


def sp_symbol(forme):
    im = new16()
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, 15, 15], radius=3, fill=TIN[forme] + (255,))
    d.rounded_rectangle([1, 1, 14, 14], radius=3, outline=shade(TIN[forme], 0.75) + (255,))
    cells = shape_cells(forme)
    for (i, j) in cells:
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (i + dx, j + dy) not in cells:
                put(im, i + 2 + dx, j + 2 + dy, WHITE)
    return im


def sp_cell(kind):
    rnd = random.Random({"case": 1, "trait": 2, "fissure": 3}[kind])
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = mix(CARAMEL_L, CARAMEL, (x + y) / 30.0)
            if rnd.random() < 0.18:
                c = jit(CARAMEL_L, rnd, 10)
            if rnd.random() < 0.04:
                c = (246, 214, 150)       # bulle de sucre
            put(im, x, y, c)
    if kind == "trait":
        for y in range(16):
            for x in range(16):
                if 6 <= x <= 9 or 6 <= y <= 9:
                    put(im, x, y, shade(CARAMEL_D, 0.85 if (x + y) % 2 else 1.0))
    if kind == "fissure":
        x, y = 2, 1
        while y < 15:
            put(im, x, y, (92, 52, 18))
            put(im, min(15, x + 1), y, (120, 70, 26))
            y += 1
            x = max(1, min(14, x + rnd.choice((-1, 0, 1, 1))))
        for k in range(5):
            put(im, 9 + k, 6 + k // 2, (92, 52, 18))
    return im


def sp_rope():
    im = new16()
    d = ImageDraw.Draw(im)
    for r, c in ((6, (150, 108, 64)), (4, (178, 132, 80)), (2, (150, 108, 64))):
        d.ellipse([8 - r, 8 - r, 7 + r, 7 + r], outline=c + (255,), width=2)
    for k in range(4):
        put(im, 12 + (k % 2), 12 + k, (130, 92, 52))
    return outline(im, (70, 46, 22))


def sp_bag():
    im = new16()
    poly(im, [(5, 4), (10, 4), (13, 9), (13, 14), (2, 14), (2, 9)], (164, 120, 78))
    rect(im, 5, 3, 6, 2, lambda x, y: (120, 84, 50))
    for x in range(4, 12):
        put(im, x, 5, (230, 210, 170))
    for (x, y, c) in ((5, 10, (120, 200, 240)), (9, 11, (240, 120, 140)), (7, 12, (250, 220, 90))):
        put(im, x, y, c)
    return outline(im, (70, 46, 22))


def marble(im, cx, cy, r, col):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                c = mix(col, (255, 255, 255), max(0, 0.55 - d / r)) if (x - cx) < 0 and (y - cy) < 0 else shade(col, 1 - 0.35 * d / r)
                put(im, x, y, c)


def sp_marble():
    im = new16()
    marble(im, 7.5, 7.5, 6, (70, 150, 220))
    for k in range(6):
        put(im, 5 + k, 9 - (k % 3), (240, 180, 60))
    put(im, 5, 5, (255, 255, 255))
    return outline(im, (30, 50, 80))


def sp_pair(n):
    im = new16()
    pos = [(5, 8), (11, 8)] if n == 2 else [(4, 10), (11, 10), (7.5, 4.5)]
    cols = [(70, 150, 220), (230, 90, 120), (240, 190, 60)]
    for k, (x, y) in enumerate(pos):
        marble(im, x, y, 3.2, cols[k])
    return outline(im, (30, 30, 40))


def sp_knife():
    im = new16()
    for k in range(8):
        put(im, 6 + k, 9 - k, mix((236, 238, 242), (150, 154, 162), k / 8))
        put(im, 7 + k, 9 - k, (190, 194, 202))
    for k in range(4):
        put(im, 2 + k, 13 - k, (70, 46, 26))
        put(im, 3 + k, 13 - k, (96, 64, 36))
    put(im, 6, 10, (40, 40, 44))
    put(im, 5, 9, (40, 40, 44))
    return outline(im, (30, 30, 34))


def sp_card():
    im = new16()
    rect(im, 1, 4, 14, 9, lambda x, y: (176, 128, 80) if 0 < x < 13 and 0 < y < 8 else (120, 84, 48))
    for (x, y) in SYMBOLS["rond"]:
        put(im, 2 + int(x * 0.6) + 1, 6 + int(y * 0.6), (238, 222, 190))
    for (x, y) in ((7, 9), (6, 10), (8, 10), (6, 11), (7, 11), (8, 11), (5, 11), (9, 11)):
        put(im, x + 0, y - 3, (238, 222, 190))
    rect(im, 11, 6, 3, 3, lambda x, y: (238, 222, 190) if x in (0, 2) or y in (0, 2) else None)
    return outline(im, (60, 40, 20))


SPRITES = {
    "tenue_haut": sp_jacket(GREEN, WHITE),
    "tenue_bas": sp_pants(GREEN, WHITE),
    "garde_haut": sp_jacket(PINK, BLACK, patch=False),
    "garde_bas": sp_pants(PINK, None),
    "garde_pieds": sp_boots(),
    "masque_rond": sp_mask("rond"),
    "masque_triangle": sp_mask("triangle"),
    "masque_carre": sp_mask("carre"),
    "aiguille": sp_needle(),
    "corde": sp_rope(),
    "sac_billes": sp_bag(),
    "bille": sp_marble(),
    "pair": sp_pair(2),
    "impair": sp_pair(3),
    "couteau": sp_knife(),
    "invitation": sp_card(),
}
for f in ("rond", "triangle", "etoile", "parapluie"):
    SPRITES["dalgona_" + f] = sp_dalgona(f)
    SPRITES["symbole_" + f] = sp_symbol(f)

HANDHELD = {"aiguille", "couteau"}
for name, im in SPRITES.items():
    save_png(im, os.path.join(TEX_ITEM, name + ".png"))
    write_json(os.path.join(MODELS, name + ".json"), {
        "parent": "minecraft:item/handheld" if name in HANDHELD else "minecraft:item/generated",
        "textures": {"layer0": NS + ":item/squid/" + name}})
    write_json(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":item/squid/" + name}})

# plaques du biscuit : 16 x 16 x 1 centrees (z 7,5..8,5), meme texture devant et derriere (l'ItemDisplay tourne de 180°)
CELLS = {}
for kind in ("case", "trait", "fissure"):
    name = "dalgona_" + kind
    im = sp_cell(kind)
    CELLS[kind] = im
    save_png(im, os.path.join(TEX_ITEM, name + ".png"))
    face = {"uv": [0, 0, 16, 16], "texture": "#0"}
    edge = {"uv": [0, 0, 16, 1], "texture": "#0"}
    write_json(os.path.join(MODELS, name + ".json"), {
        "textures": {"0": NS + ":item/squid/" + name, "particle": NS + ":item/squid/" + name},
        "elements": [{"from": [0, 0, 7.5], "to": [16, 16, 8.5],
                      "faces": {"north": face, "south": face, "east": edge, "west": edge, "up": edge, "down": edge}}],
        "display": {"fixed": {"scale": [1, 1, 1]}, "gui": {"rotation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}}})
    write_json(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":item/squid/" + name}})


# ============================================================ apercus

def big(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def checker(w, h, c1=(58, 62, 70), c2=(48, 52, 60), step=12):
    im = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            im.putpixel((x, y), (c1 if ((x // step) + (y // step)) % 2 == 0 else c2) + (255,))
    return im


def front_view(layers):
    """Vue de face d'un personnage (tete, torse, bras, jambes) avec les couches d'equipement."""
    S = 8
    out = Image.new("RGBA", (16 * S, 32 * S), (0, 0, 0, 0))
    skin = (196, 150, 120)
    base = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    rect(base, 4, 0, 8, 8, lambda x, y: skin)
    rect(base, 4, 8, 8, 12, lambda x, y: (120, 120, 120))
    rect(base, 0, 8, 4, 12, lambda x, y: skin)
    rect(base, 12, 8, 4, 12, lambda x, y: skin)
    rect(base, 4, 20, 4, 12, lambda x, y: (70, 70, 120))
    rect(base, 8, 20, 4, 12, lambda x, y: (70, 70, 120))
    for lay in layers:
        parts = [((8, 8, 8, 8), (4, 0)), ((20, 20, 8, 12), (4, 8)), ((44, 20, 4, 12), (0, 8)), ((44, 20, 4, 12), (12, 8)),
                 ((4, 20, 4, 12), (4, 20)), ((4, 20, 4, 12), (8, 20))]
        for (sx, sy, w, h), (dx, dy) in parts:
            crop = lay.crop((sx, sy, sx + w, sy + h))
            base.alpha_composite(crop, (dx, dy))
    return big(base, S)


def back_view(layer):
    S = 8
    base = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    crop = layer.crop((32, 20, 40, 32))
    base.alpha_composite(crop, (4, 8))
    return big(base, S)


sheet = checker(16 * 8 * 6 + 70, 32 * 8 + 20)
views = [front_view([NUMBERED[7], TRACK_LEGS]), back_view(NUMBERED[7]), front_view([NUMBERED[16], TRACK_LEGS]),
         front_view([GUARDS["rond"], GLEGS]), front_view([GUARDS["triangle"], GLEGS]), front_view([GUARDS["carre"], GLEGS])]
for k, v in enumerate(views):
    sheet.alpha_composite(v, (10 + k * (16 * 8 + 10), 10))
save_png(sheet, os.path.join(APERCU, "tenues.png"))

names = list(SPRITES.keys())
cols = 8
rows = (len(names) + cols - 1) // cols
sp = checker(cols * 72 + 8, rows * 72 + 8)
for k, n in enumerate(names):
    sp.alpha_composite(big(SPRITES[n], 4), (8 + (k % cols) * 72, 8 + (k // cols) * 72))
save_png(sp, os.path.join(APERCU, "objets.png"))

# biscuit : les 4 formes en plaques (case / trait) comme a l'ecran, plus une fissure
bis = checker(4 * (11 * 16 + 12) + 12, 11 * 16 + 24)
for fi, f in enumerate(("rond", "triangle", "etoile", "parapluie")):
    cells = shape_cells(f)
    for j in range(11):
        for i in range(11):
            line = (i, j) not in cells and any((i + dx, j + dy) in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            im = CELLS["trait"] if line else CELLS["case"]
            if f == "rond" and (i, j) == (5, 5):
                im = CELLS["fissure"]
            bis.alpha_composite(im, (12 + fi * (11 * 16 + 12) + i * 16, 12 + j * 16))
save_png(bis, os.path.join(APERCU, "biscuits.png"))

print("%d fichiers ecrits" % len(WRITTEN))
