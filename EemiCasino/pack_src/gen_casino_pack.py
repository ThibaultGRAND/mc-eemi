# -*- coding: utf-8 -*-
"""Genere les textures, modeles et definitions d'objets du casino 3D pour le pack EEMICRAFT.

Sortie : EemiCasino/pack/assets/eemicraft/... (a fusionner dans le pack en ligne).
Toutes les images sont dessinees ici (aucune reprise d'un autre pack).
Conventions (voir RouletteWheel.java / Fortune.java / Plinko.java) :
 - roulette : face du dessus, haut de l'image = nord, case i de WHEEL centree a i*360/37 degres
   dans le sens horaire depuis le haut ;
 - grande roue : face avant (sud du modele), segment j centre a j*360/50 degres, sens horaire depuis le haut ;
 - plinko : planche 4 x 5 blocs, 102.4 px par bloc, centre de la planche au centre de l'image.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pack", "assets", "eemicraft")
TEX = os.path.join(ROOT, "textures", "item", "casino")
MOD = os.path.join(ROOT, "models", "item", "casino")
ITM = os.path.join(ROOT, "items", "casino")

WHEEL = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}

FONT_B = "C:/Windows/Fonts/arialbd.ttf"
FONT_BLACK = "C:/Windows/Fonts/ariblk.ttf"

GOLD = (236, 186, 52)
GOLD_D = (168, 118, 22)
RED = (190, 24, 30)
BLACK = (24, 24, 28)
GREEN = (0, 140, 66)
WOOD = (96, 52, 22)
WOOD_D = (60, 30, 12)


def font(size, path=FONT_B):
    return ImageFont.truetype(path, size)


def mkdirs():
    for d in (TEX, MOD, ITM, os.path.join(TEX, "carte"), os.path.join(MOD, "carte"), os.path.join(ITM, "carte")):
        os.makedirs(d, exist_ok=True)


def save_json(path, obj):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def item_def(name):
    save_json(os.path.join(ITM, name + ".json"), {"model": {"type": "minecraft:model", "model": "eemicraft:item/casino/" + name}})


def wedge(d, cx, cy, r1, r2, a1, a2, fill, steps=12):
    """Secteur d'anneau entre les angles a1..a2 (degres, sens horaire depuis le haut)."""
    pts = []
    for k in range(steps + 1):
        a = math.radians(a1 + (a2 - a1) * k / steps)
        pts.append((cx + r2 * math.sin(a), cy - r2 * math.cos(a)))
    for k in range(steps, -1, -1):
        a = math.radians(a1 + (a2 - a1) * k / steps)
        pts.append((cx + r1 * math.sin(a), cy - r1 * math.cos(a)))
    d.polygon(pts, fill=fill)


def radial_text(img, text, cx, cy, r, ang, size, color, fnt=FONT_B, outline=None, along=False):
    """Texte centre sur le rayon r a l'angle ang, lisible depuis l'exterieur (haut du texte vers le bord)."""
    f = font(size, fnt)
    bb = f.getbbox(text)
    w, h = bb[2] - bb[0], bb[3] - bb[1]
    pad = 6
    t = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    td = ImageDraw.Draw(t)
    if outline:
        for ox in (-2, -1, 0, 1, 2):
            for oy in (-2, -1, 0, 1, 2):
                if ox or oy:
                    td.text((pad - bb[0] + ox, pad - bb[1] + oy), text, font=f, fill=outline)
    td.text((pad - bb[0], pad - bb[1]), text, font=f, fill=color)
    # along : texte dans le sens du rayon (lu du centre vers le bord)
    t = t.rotate(-ang + (90 if along else 0), resample=Image.BICUBIC, expand=True)
    a = math.radians(ang)
    px = cx + r * math.sin(a)
    py = cy - r * math.cos(a)
    img.alpha_composite(t, (int(round(px - t.width / 2)), int(round(py - t.height / 2))))


# ------------------------------------------------------------------ roulette

def roulette():
    S = 2048  # dessin a 4x puis reduction a 512
    k = S / 512.0
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2
    seg = 360.0 / 37

    def R(px):
        return px * k

    # bord en bois + liseres dores
    d.ellipse([c - R(256), c - R(256), c + R(256), c + R(256)], fill=WOOD_D)
    d.ellipse([c - R(250), c - R(250), c + R(250), c + R(250)], fill=WOOD)
    d.ellipse([c - R(238), c - R(238), c + R(238), c + R(238)], fill=GOLD)
    d.ellipse([c - R(234), c - R(234), c + R(234), c + R(234)], fill=(40, 22, 10))  # piste de la bille
    d.ellipse([c - R(218), c - R(218), c + R(218), c + R(218)], fill=GOLD_D)
    # anneau des numeros
    for i, n in enumerate(WHEEL):
        col = GREEN if n == 0 else RED if n in REDS else BLACK
        wedge(d, c, c, R(176), R(216), i * seg - seg / 2, i * seg + seg / 2, col, 8)
    # anneau des cases (plus sombre) + frettes
    for i, n in enumerate(WHEEL):
        col = (0, 96, 44) if n == 0 else (130, 14, 20) if n in REDS else (12, 12, 14)
        wedge(d, c, c, R(150), R(176), i * seg - seg / 2, i * seg + seg / 2, col, 8)
    for i in range(37):
        a = math.radians(i * seg + seg / 2)
        d.line([(c + R(150) * math.sin(a), c - R(150) * math.cos(a)), (c + R(216) * math.sin(a), c - R(216) * math.cos(a))], fill=GOLD, width=int(R(2.2)))
    d.ellipse([c - R(176) - 3, c - R(176) - 3, c + R(176) + 3, c + R(176) + 3], outline=GOLD, width=int(R(1.6)))
    # cone central en bois, losanges dores, rayons
    d.ellipse([c - R(150), c - R(150), c + R(150), c + R(150)], fill=GOLD)
    d.ellipse([c - R(146), c - R(146), c + R(146), c + R(146)], fill=(120, 66, 28))
    for rr, colr in ((130, (132, 74, 32)), (110, (112, 60, 24)), (90, (128, 70, 30))):
        d.ellipse([c - R(rr), c - R(rr), c + R(rr), c + R(rr)], fill=colr)
    for j in range(8):
        a = math.radians(j * 45 + 22.5)
        x, y = c + R(128) * math.sin(a), c - R(128) * math.cos(a)
        s = R(9)
        d.polygon([(x, y - s), (x + s * 0.6, y), (x, y + s), (x - s * 0.6, y)], fill=GOLD)
    for j in range(4):
        a = math.radians(j * 90)
        d.line([(c, c), (c + R(84) * math.sin(a), c - R(84) * math.cos(a))], fill=GOLD, width=int(R(7)))
        d.ellipse([c + R(84) * math.sin(a) - R(7), c - R(84) * math.cos(a) - R(7), c + R(84) * math.sin(a) + R(7), c - R(84) * math.cos(a) + R(7)], fill=GOLD)
    d.ellipse([c - R(40), c - R(40), c + R(40), c + R(40)], fill=GOLD_D)
    d.ellipse([c - R(32), c - R(32), c + R(32), c + R(32)], fill=GOLD)
    d.ellipse([c - R(12), c - R(12), c + R(12), c + R(12)], fill=(255, 230, 140))
    # numeros
    for i, n in enumerate(WHEEL):
        radial_text(img, str(n), c, c, R(197), i * seg, int(R(17)), (255, 255, 255))
    img = img.resize((512, 512), Image.LANCZOS)
    img.save(os.path.join(TEX, "roulette.png"))
    # modele : disque (dessus + dessous) et croisillon central qui montre la rotation
    gold_uv = [7.85, 7.85, 8.15, 8.15]
    tex = {"roue": "eemicraft:item/casino/roulette", "particle": "eemicraft:item/casino/roulette"}
    els = [
        {"from": [0, 7.96, 0], "to": [16, 8.04, 16], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#roue"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#roue"}}},
    ]
    for (fx, fz, tx, tz) in ((7.55, 6.2, 8.45, 9.8), (6.2, 7.55, 9.8, 8.45)):
        els.append({"from": [fx, 8.04, fz], "to": [tx, 8.55, tz], "faces": {f: {"uv": gold_uv, "texture": "#roue"} for f in ("north", "south", "east", "west", "up")}})
    els.append({"from": [7.3, 8.04, 7.3], "to": [8.7, 9.3, 8.7], "faces": {f: {"uv": gold_uv, "texture": "#roue"} for f in ("north", "south", "east", "west", "up")}})
    save_json(os.path.join(MOD, "roulette.json"), {"credit": "EEMICRAFT - EemiCasino", "textures": tex, "elements": els,
                                                   "display": {"gui": {"rotation": [90, 0, 0], "scale": [0.9, 0.9, 0.9]}}})
    item_def("roulette")


# ------------------------------------------------------------------ grande roue

# Roue de la fortune : une seule mise, le segment multiplie la mise (x0 = perdu). Retour 96 %.
# code -> (texte, couleur, couleur sombre) ; doit rester aligne avec Fortune.java (SEQ, MULT).
TIERS = {"0": ("x0", (40, 40, 46), (20, 20, 24)), "1": ("x0,5", (120, 80, 50), (70, 45, 25)),
         "2": ("x1", (150, 150, 158), (90, 90, 98)), "3": ("x1,5", (40, 110, 220), (20, 60, 140)),
         "4": ("x2", (40, 170, 70), (20, 100, 40)), "5": ("x3", (150, 60, 200), (90, 30, 130)),
         "6": ("x5", (240, 120, 30), (160, 70, 10)), "7": ("x10", (245, 200, 40), (180, 120, 10))}
COUNTS = [("7", 1), ("6", 1), ("5", 2), ("4", 4), ("3", 6), ("2", 6), ("1", 8), ("0", 22)]
N_FORTUNE = 50


def fortune_sequence():
    seq = [None] * N_FORTUNE
    for code, n in COUNTS:
        free_offset = {"7": 0.0, "6": 0.5, "5": 0.25, "4": 0.125, "3": 0.6, "2": 0.33, "1": 0.8, "0": 0.0}[code]
        for k in range(n):
            target = (k + free_offset) * N_FORTUNE / n
            best = None
            for delta in range(N_FORTUNE):
                for sgn in (1, -1):
                    p = int(round(target + sgn * delta)) % N_FORTUNE
                    if seq[p] is None:
                        best = p
                        break
                if best is not None:
                    break
            seq[best] = code
    return "".join(seq)


def fortune():
    seq = fortune_sequence()
    S = 2048
    k = S / 512.0
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2
    seg = 360.0 / N_FORTUNE

    def R(px):
        return px * k

    d.ellipse([c - R(256), c - R(256), c + R(256), c + R(256)], fill=GOLD_D)
    d.ellipse([c - R(248), c - R(248), c + R(248), c + R(248)], fill=(70, 18, 24))
    for j, code in enumerate(seq):
        name, col, dark = TIERS[code]
        wedge(d, c, c, R(70), R(228), j * seg - seg / 2, j * seg + seg / 2, col, 6)
        wedge(d, c, c, R(70), R(110), j * seg - seg / 2, j * seg + seg / 2, dark, 6)
    for j in range(N_FORTUNE):
        a = math.radians(j * seg + seg / 2)
        d.line([(c + R(70) * math.sin(a), c - R(70) * math.cos(a)), (c + R(228) * math.sin(a), c - R(228) * math.cos(a))], fill=(255, 236, 170), width=int(R(1.6)))
    d.ellipse([c - R(229), c - R(229), c + R(229), c + R(229)], outline=GOLD, width=int(R(3)))
    # ampoules autour
    for j in range(N_FORTUNE):
        a = math.radians(j * seg + seg / 2)
        x, y = c + R(239) * math.sin(a), c - R(239) * math.cos(a)
        d.ellipse([x - R(4.5), y - R(4.5), x + R(4.5), y + R(4.5)], fill=(255, 245, 190) if j % 2 == 0 else (255, 200, 80))
    for j, code in enumerate(seq):
        name = TIERS[code][0]
        size = 19 if code in "567" else 15 if len(name) > 3 else 17
        radial_text(img, name, c, c, R(178), j * seg, int(R(size)), (255, 255, 255), FONT_BLACK, outline=(0, 0, 0, 200), along=True)
    # moyeu
    d.ellipse([c - R(72), c - R(72), c + R(72), c + R(72)], fill=GOLD)
    d.ellipse([c - R(64), c - R(64), c + R(64), c + R(64)], fill=(110, 16, 26))
    d.ellipse([c - R(54), c - R(54), c + R(54), c + R(54)], outline=GOLD, width=int(R(3)))
    f = font(int(R(20)), FONT_BLACK)
    for txt, dy in (("EEMI", -12), ("CRAFT", 12)):
        bb = f.getbbox(txt)
        d.text((c - (bb[2] - bb[0]) / 2 - bb[0], c + R(dy) - (bb[3] - bb[1]) / 2 - bb[1]), txt, font=f, fill=GOLD)
    img = img.resize((512, 512), Image.LANCZOS)
    img.save(os.path.join(TEX, "fortune.png"))
    tex = {"roue": "eemicraft:item/casino/fortune", "particle": "eemicraft:item/casino/fortune"}
    gold_uv = [8.0, 0.1, 8.2, 0.3]
    els = [{"from": [0, 0, 7.96], "to": [16, 16, 8.04], "faces": {
        "south": {"uv": [0, 0, 16, 16], "texture": "#roue"},
        "north": {"uv": [0, 0, 16, 16], "texture": "#roue"}}},
        {"from": [7.2, 7.2, 8.04], "to": [8.8, 8.8, 8.8], "faces": {f: {"uv": gold_uv, "texture": "#roue"} for f in ("south", "east", "west", "up", "down")}}]
    save_json(os.path.join(MOD, "fortune.json"), {"credit": "EEMICRAFT - EemiCasino", "textures": tex, "elements": els})
    item_def("fortune")
    return seq


def fleche():
    S = 256
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(40, 30), (216, 30), (128, 236)], fill=GOLD_D)
    d.polygon([(62, 44), (194, 44), (128, 204)], fill=(220, 30, 40))
    d.ellipse([108, 50, 148, 90], fill=GOLD)
    img = img.resize((64, 64), Image.LANCZOS)
    img.save(os.path.join(TEX, "fleche.png"))
    tex = {"f": "eemicraft:item/casino/fleche", "particle": "eemicraft:item/casino/fleche"}
    els = [{"from": [0, 0, 7.9], "to": [16, 16, 8.1], "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#f"}, "north": {"uv": [0, 0, 16, 16], "texture": "#f"}}}]
    save_json(os.path.join(MOD, "fleche.json"), {"credit": "EEMICRAFT - EemiCasino", "textures": tex, "elements": els})
    item_def("fleche")


# ------------------------------------------------------------------ plinko

PLINKO_MULT = [5.6, 2.1, 1.1, 0.9, 0.5, 0.9, 1.1, 2.1, 5.6]
PPB = 102.4  # pixels par bloc (image 512 = 5 blocs de haut)
ROWS = 8
SPACING = 0.4
ROW_TOP = 1.9
ROW_STEP = 0.45


def mult_text(m):
    return ("x%.1f" % m).replace(".0", "") if m != int(m) or True else "x%d" % m


def plinko():
    S = 2048
    k = S / 512.0
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = S / 2

    def P(u, v):
        return (cx + u * PPB * k, (2.5 - v) * PPB * k)

    x0, y0 = P(-2, 2.5)
    x1, y1 = P(2, -2.5)
    # fond degrade bleu nuit -> violet
    for yy in range(int(y0), int(y1)):
        t = (yy - y0) / (y1 - y0)
        col = (int(18 + 40 * t), int(16 + 6 * t), int(52 + 40 * t), 255)
        d.line([(x0, yy), (x1, yy)], fill=col)
    d.rectangle([x0, y0, x1 - 1, y1 - 1], outline=GOLD, width=int(18))
    d.rectangle([x0 + 22, y0 + 22, x1 - 23, y1 - 23], outline=GOLD_D, width=int(6))
    # titre
    f = font(int(0.26 * PPB * k), FONT_BLACK)
    bb = f.getbbox("PLINKO")
    tx, ty = P(0, 2.25)
    for ox, oy in ((4, 4),):
        d.text((tx - (bb[2] - bb[0]) / 2 - bb[0] + ox, ty - (bb[3] - bb[1]) / 2 - bb[1] + oy), "PLINKO", font=f, fill=(0, 0, 0, 160))
    d.text((tx - (bb[2] - bb[0]) / 2 - bb[0], ty - (bb[3] - bb[1]) / 2 - bb[1]), "PLINKO", font=f, fill=GOLD)
    # picots
    pr = 0.05 * PPB * k
    for r in range(ROWS):
        v = ROW_TOP - ROW_STEP * r
        for j in range(r + 3):
            u = (j - (r + 2) / 2.0) * SPACING
            x, y = P(u, v)
            d.ellipse([x - pr * 1.9, y - pr * 1.9, x + pr * 1.9, y + pr * 1.9], fill=(120, 110, 200, 90))
            d.ellipse([x - pr, y - pr, x + pr, y + pr], fill=(250, 250, 255))
    # cases du bas
    for j, m in enumerate(PLINKO_MULT):
        u = (j - 4) * SPACING
        dist = abs(j - 4)
        col = [(250, 200, 40), (250, 160, 40), (245, 110, 40), (230, 60, 50), (210, 30, 70)][dist]
        a, b = P(u - SPACING / 2 + 0.025, -1.55)
        c2, e = P(u + SPACING / 2 - 0.025, -2.15)
        d.rounded_rectangle([a, b, c2, e], radius=int(18), fill=col, outline=(255, 255, 255), width=4)
        txt = ("x%g" % m)
        fs = font(int(0.13 * PPB * k), FONT_BLACK)
        bb = fs.getbbox(txt)
        mx, my = P(u, -1.85)
        d.text((mx - (bb[2] - bb[0]) / 2 - bb[0], my - (bb[3] - bb[1]) / 2 - bb[1]), txt, font=fs, fill=(30, 10, 20))
    # entonnoir
    a, b = P(-0.25, 2.12)
    c2, e = P(0.25, 2.02)
    d.rounded_rectangle([a, b, c2, e], radius=10, fill=GOLD)
    img = img.resize((512, 512), Image.LANCZOS)
    img.save(os.path.join(TEX, "plinko.png"))
    tex = {"p": "eemicraft:item/casino/plinko", "particle": "eemicraft:item/casino/plinko"}
    uv = [1.6, 0, 14.4, 16]
    els = [{"from": [1.6, 0, 7.96], "to": [14.4, 16, 8.04], "faces": {"south": {"uv": uv, "texture": "#p"}, "north": {"uv": uv, "texture": "#p"}}}]
    save_json(os.path.join(MOD, "plinko.json"), {"credit": "EEMICRAFT - EemiCasino", "textures": tex, "elements": els})
    item_def("plinko")


def bille():
    S = 64
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(32):
        t = i / 31
        v = int(255 - 70 * t)
        d.ellipse([i, i, S - 1 - i, S - 1 - i], fill=(v, v, min(255, v + 6), 255))
    img = img.resize((16, 16), Image.LANCZOS)
    img.save(os.path.join(TEX, "bille.png"))
    # meme forme que la bille du paintball (deja dans le pack)
    save_json(os.path.join(MOD, "bille.json"), {"parent": "eemicraft:item/paintball/bille",
                                               "textures": {"bille": "eemicraft:item/casino/bille", "particle": "eemicraft:item/casino/bille"}})
    item_def("bille")
    # bille doree du plinko
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(32):
        t = i / 31
        d.ellipse([i, i, S - 1 - i, S - 1 - i], fill=(int(255 - 40 * t), int(220 - 90 * t), int(90 - 70 * t), 255))
    img = img.resize((16, 16), Image.LANCZOS)
    img.save(os.path.join(TEX, "bille_or.png"))
    save_json(os.path.join(MOD, "bille_or.json"), {"parent": "eemicraft:item/paintball/bille",
                                                  "textures": {"bille": "eemicraft:item/casino/bille_or", "particle": "eemicraft:item/casino/bille_or"}})
    item_def("bille_or")


# ------------------------------------------------------------------ cartes

SUITS = ["pique", "coeur", "carreau", "trefle"]
RANKS = ["2", "3", "4", "5", "6", "7", "8", "9", "10", "v", "d", "r", "a"]
RANK_TXT = ["2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R", "A"]
CW, CH = 88, 128  # carte dans une image 128 x 128 (x 20..108)


def suit_shape(d, s, x, y, size, col):
    r = size / 2.0
    if s == "coeur":
        d.ellipse([x - r, y - r * 0.9, x, y + r * 0.1], fill=col)
        d.ellipse([x, y - r * 0.9, x + r, y + r * 0.1], fill=col)
        d.polygon([(x - r * 0.98, y - r * 0.25), (x + r * 0.98, y - r * 0.25), (x, y + r)], fill=col)
    elif s == "carreau":
        d.polygon([(x, y - r), (x + r * 0.72, y), (x, y + r), (x - r * 0.72, y)], fill=col)
    elif s == "pique":
        d.polygon([(x, y - r), (x + r * 0.98, y + r * 0.2), (x - r * 0.98, y + r * 0.2)], fill=col)
        d.ellipse([x - r, y - r * 0.15, x, y + r * 0.62], fill=col)
        d.ellipse([x, y - r * 0.15, x + r, y + r * 0.62], fill=col)
        d.polygon([(x, y + r * 0.2), (x + r * 0.35, y + r), (x - r * 0.35, y + r)], fill=col)
    else:  # trefle
        rr = r * 0.48
        d.ellipse([x - rr, y - r, x + rr, y - r + 2 * rr], fill=col)
        d.ellipse([x - r, y - r * 0.25, x - r + 2 * rr, y - r * 0.25 + 2 * rr], fill=col)
        d.ellipse([x + r - 2 * rr, y - r * 0.25, x + r, y - r * 0.25 + 2 * rr], fill=col)
        d.polygon([(x, y), (x + r * 0.35, y + r), (x - r * 0.35, y + r)], fill=col)


def card_base(scale):
    S = 128 * scale
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0 = 20 * scale
    d.rounded_rectangle([x0, 0, x0 + CW * scale - 1, CH * scale - 1], radius=10 * scale, fill=(250, 250, 246), outline=(60, 60, 60), width=2 * scale)
    return img, d, x0


def card(si, ri):
    sc = 4
    img, d, x0 = card_base(sc)
    s = SUITS[si]
    col = (200, 20, 30) if s in ("coeur", "carreau") else (20, 20, 24)
    txt = RANK_TXT[ri]
    f = font(24 * sc if txt != "10" else 21 * sc, FONT_B)
    # coins
    for flip in (False, True):
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        bb = f.getbbox(txt)
        cxl = x0 + 15 * sc
        ld.text((cxl - (bb[2] - bb[0]) / 2 - bb[0], 6 * sc - bb[1]), txt, font=f, fill=col)
        suit_shape(ld, s, cxl, 44 * sc, 16 * sc, col)
        if flip:
            layer = layer.rotate(180)
        img.alpha_composite(layer)
    cx = x0 + CW * sc / 2
    cy = CH * sc / 2
    if ri >= 9 and ri <= 11:
        # figure : cadre + grande lettre + couleur
        d.rounded_rectangle([x0 + 24 * sc, 24 * sc, x0 + (CW - 24) * sc, (CH - 24) * sc], radius=6 * sc, fill=(255, 236, 170), outline=col, width=3 * sc)
        fb = font(46 * sc, FONT_BLACK)
        bb = fb.getbbox(txt)
        d.text((cx - (bb[2] - bb[0]) / 2 - bb[0], cy - 12 * sc - (bb[3] - bb[1]) / 2 - bb[1]), txt, font=fb, fill=col)
        suit_shape(d, s, cx, cy + 26 * sc, 20 * sc, col)
    else:
        suit_shape(d, s, cx, cy, (40 if ri == 12 else 34) * sc, col)
    img = img.resize((128, 128), Image.LANCZOS)
    name = s + "_" + RANKS[ri]
    img.save(os.path.join(TEX, "carte", name + ".png"))
    save_json(os.path.join(MOD, "carte", name + ".json"), {"parent": "eemicraft:item/casino/carte",
                                                          "textures": {"face": "eemicraft:item/casino/carte/" + name, "particle": "eemicraft:item/casino/carte/" + name}})
    save_json(os.path.join(ITM, "carte", name + ".json"), {"model": {"type": "minecraft:model", "model": "eemicraft:item/casino/carte/" + name}})


def card_back():
    sc = 4
    img, d, x0 = card_base(sc)
    d.rounded_rectangle([x0 + 7 * sc, 7 * sc, x0 + (CW - 7) * sc, (CH - 7) * sc], radius=6 * sc, fill=(150, 18, 30))
    for yy in range(10 * sc, (CH - 10) * sc, 10 * sc):
        for xx in range(int(x0 + 10 * sc), int(x0 + (CW - 10) * sc), 10 * sc):
            d.polygon([(xx + 5 * sc, yy), (xx + 10 * sc, yy + 5 * sc), (xx + 5 * sc, yy + 10 * sc), (xx, yy + 5 * sc)], outline=(220, 170, 60), width=sc)
    cx = x0 + CW * sc / 2
    d.ellipse([cx - 22 * sc, CH * sc / 2 - 22 * sc, cx + 22 * sc, CH * sc / 2 + 22 * sc], fill=GOLD, outline=(120, 70, 10), width=2 * sc)
    f = font(20 * sc, FONT_BLACK)
    bb = f.getbbox("E")
    d.text((cx - (bb[2] - bb[0]) / 2 - bb[0], CH * sc / 2 - (bb[3] - bb[1]) / 2 - bb[1]), "E", font=f, fill=(120, 16, 26))
    img = img.resize((128, 128), Image.LANCZOS)
    img.save(os.path.join(TEX, "carte", "dos.png"))
    # modele commun : carte debout, face au sud (vue dans les menus), dos au nord
    uv = [2.5, 0, 13.5, 16]
    save_json(os.path.join(MOD, "carte.json"), {
        "credit": "EEMICRAFT - EemiCasino",
        "textures": {"face": "eemicraft:item/casino/carte/dos", "dos": "eemicraft:item/casino/carte/dos", "particle": "eemicraft:item/casino/carte/dos"},
        "elements": [{"from": [2.5, 0, 7.97], "to": [13.5, 16, 8.03], "faces": {
            "south": {"uv": uv, "texture": "#face"},
            "north": {"uv": [13.5, 0, 2.5, 16], "texture": "#dos"}}}],
        "gui_light": "front",
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.05, 1.05, 1.05]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, -20, 0], "translation": [1, 3, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]}}})
    save_json(os.path.join(MOD, "carte", "dos.json"), {"parent": "eemicraft:item/casino/carte",
                                                      "textures": {"face": "eemicraft:item/casino/carte/dos"}})
    save_json(os.path.join(ITM, "carte", "dos.json"), {"model": {"type": "minecraft:model", "model": "eemicraft:item/casino/carte/dos"}})


def main():
    mkdirs()
    roulette()
    seq = fortune()
    fleche()
    plinko()
    bille()
    for si in range(4):
        for ri in range(13):
            card(si, ri)
    card_back()
    print("Sequence grande roue :", seq)
    counts = {c: seq.count(c) for c in "01234567"}
    print("Comptes :", counts)


if __name__ == "__main__":
    sys.exit(main())
