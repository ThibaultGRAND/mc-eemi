"""Skins des zombies du mode « Dernier Refuge » (CONCEPTION section 8). Méthode et pièces : ZOMBIES.md.

- Textures de BASE des entités (le pack n'est chargé que dans les mondes du mode) :
  zombie.png (corps commun + tenue du Rôdeur), husk.png (Cracheur), drowned.png + drowned_outer_layer.png (Noyé),
  zombie_villager.png (Hurleur ; les surcouches de type/métier/niveau de villageois zombie sont rendues vides).
- Tenues propres à chaque type = équipement à composant `equippable` avec `asset_id refuge:zombie_<type>[_elite]` :
  assets/refuge/equipment/zombie_<id>.json
  assets/refuge/textures/entity/equipment/humanoid/zombie_<id>.png           (casque, plastron, bottes : 64×32)
  assets/refuge/textures/entity/equipment/humanoid_leggings/zombie_<id>.png  (jambières : 64×32)
  Élites : mêmes tenues + yeux dorés (casque) + brassard doré (bras du plastron).

Usage :
    ajouter(files, jar_path)   # ajoute les fichiers au dict files (chemin dans le zip -> bytes)
    python gen_zombies.py      # zip de test pack/test_zombies.zip + vérification + aperçu apercus/zombies.png
"""
import hashlib
import io
import json
import os
import subprocess
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

ICI = os.path.dirname(os.path.abspath(__file__))
JAR = r"C:\Users\Utilisateur\AppData\Roaming\.minecraft\versions\26.3\26.3.jar"
MCMETA_SRC = os.path.join(ICI, "..", "..", "a_deposer_installes", "EemicraftPack-b8b58bca.zip")

# ------------------------------------------------------------------------------------------------ outils


def R(*key):
    """Bruit déterministe 0..1."""
    return hashlib.md5(repr(key).encode()).digest()[0] / 255.0


def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def pal(base, light, dark, deep=None):
    p = [hx(base), hx(light), hx(dark)]
    p.append(hx(deep) if deep else tuple(int(c * 0.8) for c in p[2][:3]) + (255,))
    return p


def tone(p, *key, wl=0.2, wd=0.22):
    r = R(*key)
    return p[1] if r < wl else p[2] if r > 1 - wd else p[0]


# Palettes (teintes ternes, niveau de détail vanilla)
SKIN = pal("687658", "778664", "56634a", "434e3a")          # infecté gris-vert
SKIN_PALE = pal("7f8a6e", "8f9a7c", "6c775c", "56604a")     # Hurleur
SKIN_SICK = pal("7f9a46", "92ad57", "68813a", "52682e")     # Cracheur
SKIN_BLUE = pal("5f7f86", "71939a", "4c6870", "3b535a")     # Noyé
SKIN_DARK = pal("4a5340", "57614b", "3c4434", "2e3428")     # Rampant
SKIN_SWOL = pal("8a6a84", "a07e98", "70566c", "5a4456")     # Gonflé (violacé)
RAIN = pal("1c1d21", "2c2e33", "141518")                   # traces de la pluie noire
BLOOD = pal("7c1a16", "9a2a22", "5a1010")
HAIR_BR = pal("3c2c21", "4e3b2c", "2e221a")
HAIR_BK = pal("1d1b1e", "2b292d", "141315")
SHIRT = pal("6b8db5", "82a3c8", "557499")
JEANS = pal("3a5076", "486088", "2e4060")
SHOE = pal("3a2e26", "4a3c32", "2a211b")
HOOD = pal("b0362e", "c64a3e", "8c2a24")
JOG = pal("5a5d63", "6e7177", "46494e")
WHITE = pal("dcdcd4", "ececE4", "bebeb6")
ORANGE = pal("cc6b26", "e08038", "a8541c")
YELLOW = pal("d9b02a", "ecc94a", "b38c1c")
UNDER = pal("c8c3b4", "d6d1c2", "aaa596")
BOOT_BR = pal("4a3424", "5c4430", "38271a")
REFLECT = pal("b4b8bc", "cfd3d6", "959a9e")
BLOUSE = pal("c6c4ba", "d8d6cc", "a8a69c")
TROUS_GR = pal("3c3a40", "4a484f", "2f2d32")
TANK = pal("6b6458", "7a7366", "57514a")
TROUS_BR = pal("5c4a36", "6d5942", "4a3b2b")
ACID = pal("9fe03a", "c6ff5a", "74b028")
PUS = pal("d4cf5a", "eae68a", "a8a03a")
VEIN = pal("aef04a", "d8ff7a", "86c434")
NAVY = pal("2a3a5e", "364a72", "1f2c48")
MARIN_W = pal("d2d2c8", "e0e0d8", "b8b8ae")
CABAN = pal("253048", "2f3c58", "1b2437")
BEANIE = pal("7a2a24", "8f342c", "62201b")
ALGAE = pal("3d7a34", "4f9440", "2e5e28")
WET_PANTS = pal("2e3448", "3a4158", "232838")
RAG = pal("40382f", "4f463b", "332c25", "241f1a")
CRS = pal("26304a", "33405e", "1a2236")
VEST = pal("2f3134", "3d4044", "232427")
VISOR = pal("7d93a6", "9fb4c4", "5e7385")
BLACK = pal("1c1c1e", "2a2a2e", "141416")
GOLD = pal("e0b030", "f7d96a", "b8891c")

T = None  # pixel transparent


def rain(seed, sx, y, density=0.28, maxlen=5):
    """Coulure noire qui part du haut d'une face (même colonne = même trace)."""
    if R("rain", seed, sx) < density and y < 1 + int(R("rl", seed, sx) * maxlen):
        return tone(RAIN, "rc", seed, sx, y)
    return None


def grid(rows, colors):
    return [[colors.get(ch) if ch != "." else T for ch in row] for row in rows]


# ------------------------------------------------------------------------------------------------ cubes


def faces_of(sx, w, d):
    if sx < d:
        return "right", sx
    if sx < d + w:
        return "front", sx - d
    if sx < 2 * d + w:
        return "left", sx - d - w
    return "back", sx - 2 * d - w


def box(img, u, v, w, h, d, fn):
    """Peint un cube (mise en page UV vanilla). fn(face, fx, y, sx) -> couleur ou None.
    Bande : droite, avant, gauche, arrière ; sx = position dans la bande (continuité autour du cube)."""
    for y in range(h):
        for sx in range(2 * d + 2 * w):
            face, fx = faces_of(sx, w, d)
            c = fn(face, fx, y, sx)
            if c:
                img.putpixel((u + sx, v + d + y), c)
    for x in range(w):
        for z in range(d):
            c = fn("top", x, z, None)
            if c:
                img.putpixel((u + d + x, v + z), c)
            c = fn("bottom", x, z, None)
            if c:
                img.putpixel((u + d + w + x, v + z), c)


def copy_mirrored(img, src, dst, w, h, d):
    """Copie le cube src vers dst en miroir (comme CubeListBuilder.mirror()) : pour les membres gauches séparés."""
    su, sv = src
    du, dv = dst

    def crop(x, y, cw, ch):
        return img.crop((x, y, x + cw, y + ch)).transpose(Image.FLIP_LEFT_RIGHT)

    parts = [
        ((su + d, sv, w, d), (du + d, dv)),                     # dessus
        ((su + d + w, sv, w, d), (du + d + w, dv)),             # dessous
        ((su + d + w, sv + d, d, h), (du, dv + d)),             # gauche -> droite
        ((su + d, sv + d, w, h), (du + d, dv + d)),             # avant
        ((su, sv + d, d, h), (du + d + w, dv + d)),             # droite -> gauche
        ((su + 2 * d + w, sv + d, w, h), (du + 2 * d + w, dv + d)),  # arrière
    ]
    tiles = [(crop(*a), b) for a, b in parts]
    for t, b in tiles:
        img.paste(t, b)


def layer(*fns):
    """Superpose plusieurs peintres : le premier qui rend une couleur gagne."""
    def f(face, fx, y, sx):
        for fn in fns:
            c = fn(face, fx, y, sx)
            if c:
                return c
        return None
    return f


# ------------------------------------------------------------------------------------------------ têtes (avant 8×8)

C_FACE = {
    "H": HAIR_BR[0], "h": HAIR_BR[2], "k": hx("141412"), "b": RAIN[0], "m": hx("321818"), "t": hx("b0a88c"),
    "r": BLOOD[0],
}


def face_painter(rows, skin, extra=None, seed="f"):
    cols = dict(C_FACE)
    if extra:
        cols.update(extra)

    def f(x, y):
        ch = rows[y][x]
        if ch == "s":
            return tone(skin, seed, x, y)
        if ch == "S":
            return skin[1]
        if ch == "d":
            return skin[2]
        if ch == "D":
            return skin[3]
        if ch == ".":
            return None
        return cols[ch]
    return f


def head_std(seed, skin, hair, face_rows, hair_side=2, hair_back=4, extra=None, rain_d=0.3, hair_top=True):
    fp = face_painter(face_rows, skin, extra, seed)

    def fn(face, fx, y, sx):
        if face == "front":
            return fp(fx, y)
        if face in ("top", "bottom"):
            if face == "top" and hair_top:
                return RAIN[0] if R("rt", seed, fx, y) < 0.18 else tone(hair, "ht", seed, fx, y)
            return tone(skin, "hb", seed, fx, y)
        rows = hair_back if face == "back" else hair_side
        if hair and y < rows - (1 if R("hr", seed, sx) < 0.4 else 0):
            return tone(hair, "hs", seed, sx, y)
        r = rain(seed, sx, y, rain_d, 6)
        if r:
            return r
        return tone(skin, "hk", seed, sx, y, wd=0.3 if y > 5 else 0.2)
    return fn


# ------------------------------------------------------------------------------------------------ BASE : zombie.png (Rôdeur)

RODEUR_FACE = [
    "HHhHHhHH",
    "hHsHhsHh",
    "sbsssssS",
    "sddssdds",
    "skksskks",
    "sssddsbs",
    "sdmtmmds",
    "sbsddsss",
]


def rodeur_body(face, fx, y, sx, seed="rb"):
    if face == "top":
        return tone(SKIN, "nk", fx, y) if 2 <= fx <= 5 and 1 <= y <= 2 else tone(SHIRT, "st", fx, y)
    if face == "bottom":
        return tone(JEANS, "jb", fx, y)
    if y == 9:  # ceinture
        if face == "front" and fx in (3, 4):
            return hx("b0a070")
        return hx("3a2a1e")
    if y >= 10:
        return tone(JEANS, "jw", sx, y)
    if face == "front":
        if (y == 0 and fx in (3, 4)) or (y == 1 and fx == 4):
            return tone(SKIN, "col", fx, y)
        if fx == 4 and y in (3, 6):
            return hx("d8d8d0")
        if y in (5, 6) and fx in (1, 2):
            return tone(SKIN, "tr", fx, y) if y == 6 or fx == 2 else BLOOD[2]
        if y in (2, 3) and fx in (5, 6):
            return SHIRT[2] if y == 2 else SHIRT[0]
    if face == "back" and 3 <= y <= 6 and 2 <= fx <= 4 and (fx + y) % 3:
        return tone(SKIN, "bt", fx, y) if (fx + y) % 2 else BLOOD[0]
    r = rain(seed, sx, y, 0.3, 6)
    if r:
        return r
    if R("dirt", seed, sx, y) < 0.06:
        return hx("4a4a3a")
    return tone(SHIRT, "sh", sx, y)


def rodeur_arm(face, fx, y, sx):
    if face == "top":
        return tone(SHIRT, "at", fx, y)
    if face == "bottom":
        return SKIN[3]
    if y < 5:
        r = rain("ra", sx, y, 0.3, 3)
        return r or tone(SHIRT, "as", sx, y)
    if y == 5:
        return SHIRT[1]
    if face == "front" and y == 8 and fx in (1, 2):
        return BLOOD[0]
    return tone(SKIN, "ak", sx, y, wd=0.35 if y >= 10 else 0.2)


def rodeur_leg(face, fx, y, sx):
    if face == "top":
        return tone(JEANS, "lt", fx, y)
    if face == "bottom":
        return SHOE[2]
    if y >= 10:
        return SHOE[2] if y == 11 else tone(SHOE, "sh", sx, y)
    if face == "front" and fx in (1, 2):
        if y == 5:
            return hx("cfcfc4")
        if y == 6:
            return tone(SKIN, "kn", fx, y)
    if face == "right" and fx == 0 or face == "left" and fx == 3:
        return JEANS[1]
    return tone(JEANS, "lj", sx, y)


def tex_zombie():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    box(img, 0, 0, 8, 8, 8, head_std("zr", SKIN, HAIR_BR, RODEUR_FACE))
    box(img, 16, 16, 8, 12, 4, rodeur_body)
    box(img, 40, 16, 4, 12, 4, rodeur_arm)
    box(img, 0, 16, 4, 12, 4, rodeur_leg)
    return img


# ------------------------------------------------------------------------------------------------ BASE : husk.png (Cracheur)

CRACHEUR_FACE = [
    "hsshsDsh",
    "spsssspS",
    "sddssdds",
    "syksskys",
    "ssspssss",
    "dDmmmmDd",
    "sDaAAaDs",
    "spsaAsds",
]
CR_X = {"p": PUS[0], "y": hx("c8c050"), "a": ACID[0], "A": ACID[1], "h": HAIR_BK[0]}


def pustule(seed, sx, y, density=0.07):
    r = R("pus", seed, sx, y)
    if r < density * 0.55:
        return PUS[1] if r < density / 5 else PUS[0]
    return None


def cr_head(face, fx, y, sx):
    if face == "front":
        return face_painter(CRACHEUR_FACE, SKIN_SICK, CR_X, "cf")(fx, y)
    if face == "top":
        return HAIR_BK[0] if R("chh", fx, y) < 0.3 else (pustule("ct", fx, y) or tone(SKIN_SICK, "ct", fx, y))
    if face == "bottom":
        return SKIN_SICK[3]
    if face != "front" and y < 2 and R("chs", sx, y) < 0.35:
        return HAIR_BK[1]
    return pustule("ch", sx, y) or rain("crh", sx, y, 0.18, 4) or tone(SKIN_SICK, "chk", sx, y)


def cr_body(face, fx, y, sx):
    if face == "top":
        return tone(SKIN_SICK, "cbt", fx, y)
    if face == "bottom":
        return tone(TROUS_BR, "cbb", fx, y)
    if y >= 10:
        return tone(TROUS_BR, "cbw", sx, y) if y == 11 or R("cbl", sx) < 0.8 else hx("2e2418")
    # bave d'acide qui coule du menton
    if face == "front" and fx in (3, 4, 5) and y < (7 if fx == 4 else 4 if fx == 3 else 2):
        return ACID[1] if y % 3 == 0 else ACID[0]
    if face == "front" and fx == 4 and y == 7:
        return ACID[2]
    tank = y >= 2 or face in ("front", "back") and 2 <= fx <= 5
    if tank and not (face == "front" and 6 <= y <= 7 and fx in (1, 2)) and R("ctr", sx, y) > 0.07:
        if face == "front" and fx in (3, 4, 5) and y < 8 and R("ast", sx, y) < 0.5:
            return ACID[2]
        return rain("crb", sx, y, 0.2, 5) or tone(TANK, "ctk", sx, y)
    return pustule("cb", sx, y, 0.1) or tone(SKIN_SICK, "cbs", sx, y)


def cr_arm(face, fx, y, sx):
    if face == "top":
        return tone(SKIN_SICK, "cat", fx, y)
    if face == "bottom":
        return SKIN_SICK[3]
    return pustule("ca", sx, y, 0.1) or rain("cra", sx, y, 0.15, 3) or tone(SKIN_SICK, "cas", sx, y,
                                                                               wd=0.35 if y > 9 else 0.2)


def cr_leg(face, fx, y, sx):
    if face == "top":
        return tone(TROUS_BR, "clt", fx, y)
    if face == "bottom":
        return SKIN_SICK[3]
    cut = 6 + int(R("clc", sx) * 3)  # pantalon déchiré à mi-mollet
    if y < cut:
        return tone(TROUS_BR, "clp", sx, y)
    if y == cut:
        return TROUS_BR[2]
    return pustule("cl", sx, y, 0.08) or tone(SKIN_SICK, "cls", sx, y, wd=0.4 if y > 9 else 0.2)


def tex_husk():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    box(img, 0, 0, 8, 8, 8, cr_head)
    box(img, 16, 16, 8, 12, 4, cr_body)
    box(img, 40, 16, 4, 12, 4, cr_arm)
    box(img, 0, 16, 4, 12, 4, cr_leg)
    return img


# ------------------------------------------------------------------------------------------------ BASE : drowned (Noyé)

NOYE_FACE = [
    "ssbsssss",
    "sSsssbsS",
    "sddssdds",
    "sccsscc.",
    "ssssssss",
    "sssDdsss",
    "sdmmmmds",
    "sbssdsbs",
]
NO_X = {"c": hx("9fe8e0")}


def no_head(face, fx, y, sx):
    if face == "front":
        rows = [r.replace(".", "s") for r in NOYE_FACE]
        return face_painter(rows, SKIN_BLUE, NO_X, "nf")(fx, y)
    if face in ("top", "bottom"):
        return tone(SKIN_BLUE, "nt", face, fx, y)
    return rain("nrh", sx, y, 0.25, 5) or tone(SKIN_BLUE, "nh", sx, y)


def no_body(face, fx, y, sx):
    if face == "top":
        return tone(MARIN_W, "nbt", fx, y)
    if face == "bottom":
        return tone(WET_PANTS, "nbb", fx, y)
    if y >= 10:
        return hx("1e1e22") if y == 10 else tone(WET_PANTS, "nbp", sx, y)
    if face == "front" and y == 0 and fx in (3, 4):
        return tone(SKIN_BLUE, "nn", fx, y)
    if face == "front" and y in (6, 7) and fx in (5, 6):
        return tone(SKIN_BLUE, "ntr", fx, y)
    stripe = NAVY if y % 3 == 1 else MARIN_W
    c = tone(stripe, "nbs", sx, y)
    if R("wet", sx, y) < 0.15:
        c = tuple(int(v * 0.82) for v in c[:3]) + (255,)
    return c


def no_arm(face, fx, y, sx):
    if face == "top":
        return MARIN_W[0]
    if face == "bottom":
        return SKIN_BLUE[3]
    if y < 5:
        return tone(NAVY if y % 3 == 1 else MARIN_W, "nas", sx, y)
    return tone(SKIN_BLUE, "nak", sx, y, wd=0.35 if y > 9 else 0.2)


def no_leg(face, fx, y, sx):
    if face == "top":
        return WET_PANTS[0]
    if face == "bottom":
        return hx("141416")
    if y >= 8:
        return tone(BLACK, "nbo", sx, y)  # bottes en caoutchouc
    return tone(WET_PANTS, "nlp", sx, y)


def algae(seed, sx, y, density=0.3, maxlen=7, start=0):
    if R("alg", seed, sx) < density and start <= y < start + 1 + int(R("al", seed, sx) * maxlen):
        return tone(ALGAE, "alc", seed, sx, y)
    return None


def no_out_head(face, fx, y, sx):
    """Bonnet de docker + algues (couche extérieure, gonflée de 0,25)."""
    if face == "top":
        return tone(BEANIE, "obt", fx, y) if R("oba", fx, y) > 0.15 else ALGAE[0]
    if face == "bottom":
        return None
    if y < 2:
        return tone(BEANIE, "obs", sx, y)
    if y == 2:
        return BEANIE[1]
    if face == "front":
        return algae("ohf", fx, y, 0.15, 3, 3)
    return algae("ohs", sx, y, 0.45, 5, 3)


def no_out_body(face, fx, y, sx):
    if face == "top":
        return tone(CABAN, "oct", fx, y) if fx in (0, 1, 6, 7) else None
    if face == "bottom":
        return None
    a = algae("obo", sx, y, 0.25, 4)
    if a and y < 4:
        return a
    if face == "front":
        if fx in (0, 1, 6, 7) and y < 11:
            if fx in (1, 6) and y in (3, 6, 9):
                return hx("a08a40")
            return tone(CABAN, "ocf", fx, y)
        return None
    if y < 11 or face != "back":
        return tone(CABAN, "ocb", sx, y)
    return tone(CABAN, "ocb2", sx, y) if R("och", sx) < 0.6 else None


def no_out_arm(face, fx, y, sx):
    if face == "top":
        return tone(CABAN, "oat", fx, y)
    if face == "bottom":
        return None
    a = algae("oar", sx, y, 0.3, 4)
    if a:
        return a
    if y < 8:
        return tone(CABAN, "oas", sx, y)
    if y == 8:
        return CABAN[2]
    return None


def no_out_leg(face, fx, y, sx):
    if face in ("top", "bottom"):
        return None
    return algae("ole", sx, y, 0.2, 3, 3)


def tex_drowned():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    box(img, 0, 0, 8, 8, 8, no_head)
    box(img, 16, 16, 8, 12, 4, no_body)
    box(img, 40, 16, 4, 12, 4, no_arm)
    box(img, 0, 16, 4, 12, 4, no_leg)
    copy_mirrored(img, (40, 16), (32, 48), 4, 12, 4)
    copy_mirrored(img, (0, 16), (16, 48), 4, 12, 4)
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    box(out, 0, 0, 8, 8, 8, no_out_head)
    box(out, 16, 16, 8, 12, 4, no_out_body)
    box(out, 40, 16, 4, 12, 4, no_out_arm)
    box(out, 0, 16, 4, 12, 4, no_out_leg)
    copy_mirrored(out, (40, 16), (32, 48), 4, 12, 4)
    copy_mirrored(out, (0, 16), (16, 48), 4, 12, 4)
    return img, out


# ------------------------------------------------------------------------------------------------ BASE : zombie_villager.png (Hurleur)

# Tête 8×10. Le nez (cube 2×4×2, colonnes 3-4, lignes 7 à 10) devient une mâchoire béante qui dépasse.
HURLEUR_FACE = [
    "HHHHHHHH",
    "HHHHHHHH",
    "HHssssHH",
    "HddssddH",
    "HwkssKwH",
    "HssddssH",
    "HsDssDsH",
    "HrMMMMrH",
    "HrMMMMrH",
    "HsrMMrsH",
]
HU_X = {"H": HAIR_BK[0], "w": hx("d8d8c8"), "k": hx("141414"), "K": hx("141414"), "M": hx("0a0a0c"), "r": hx("5a1a1a")}


def hu_head(face, fx, y, sx):
    if face == "front":
        rows = HURLEUR_FACE
        c = rows[y][fx]
        if c == "H" and R("hhl", fx, y) < 0.2:
            return HAIR_BK[1]
        return face_painter(rows, SKIN_PALE, HU_X, "hf")(fx, y)
    if face == "top":
        return tone(HAIR_BK, "hut", fx, y)
    if face == "bottom":
        return SKIN_PALE[3]
    return tone(HAIR_BK, "hus", sx, y)  # cheveux longs tout autour


def hu_nose(face, fx, y, sx):
    """Mâchoire béante : bouche noire, gorge noire, bords rouge sombre, dents."""
    if face == "front":
        if y == 0:
            return hx("cfc8b0")
        return hx("0a0a0c") if y < 3 else hx("cfc8b0") if fx == 0 else hx("0a0a0c")
    if face == "top":
        return SKIN_PALE[2]
    if face == "bottom":
        return hx("3a1010")
    return hx("5a1a1a") if y < 3 else SKIN_PALE[3]


def hu_hat(face, fx, y, sx):
    """Couche « chapeau » (gonflée de 0,5) : volume des cheveux longs, mèches qui retombent."""
    if face == "top":
        return tone(HAIR_BK, "hht", fx, y)
    if face == "bottom":
        return None
    if face == "front":
        if y == 0 or (y == 1 and fx not in (3, 4)) or fx in (0, 7) and y < 9:
            return tone(HAIR_BK, "hhf", fx, y)
        return None
    if face == "back":
        return tone(HAIR_BK, "hhb", fx, y) if y < 9 or R("hhe", fx) < 0.6 else None
    return tone(HAIR_BK, "hhs", sx, y) if y < 8 or R("hhe2", sx) < 0.5 else None


def blouse(seed, face, fx, y, sx, h):
    if face == "front" and ((y == 0 and fx in (3, 4)) or (y == 1 and fx == 4)):
        return tone(SKIN_PALE, "bcol", fx, y)
    if face == "front" and fx == 4 and y in (3, 7, 11):
        return hx("8a887e")
    r = rain(seed, sx, y, 0.35, 7)
    if r:
        return r
    if R("bst", seed, sx, y) < 0.05:
        return BLOOD[2]
    return tone(BLOUSE, "bl", seed, sx, y)


def hu_body(face, fx, y, sx):
    if face in ("top", "bottom"):
        return tone(BLOUSE, "hbt", face, fx, y)
    return blouse("hb", face, fx, y, sx, 12)


def hu_robe(face, fx, y, sx):
    """Grande blouse (8×20×6) : descend aux genoux, bas déchiré."""
    if face == "top":
        return None
    if face == "bottom":
        return None
    hem = 13 + int(R("hem", sx) * 3)
    if y > hem:
        return None
    if y == hem:
        return BLOUSE[2]
    return blouse("hr", face, fx, y, sx, 20)


def hu_arm(face, fx, y, sx):
    if face == "top":
        return BLOUSE[0]
    if face == "bottom":
        return SKIN_PALE[3]
    if y < 6:
        return rain("har", sx, y, 0.3, 3) or tone(BLOUSE, "has", sx, y)
    if y == 6:
        return BLOUSE[2]
    if face == "front" and y == 9 and fx == 2:
        return BLOOD[0]
    return tone(SKIN_PALE, "hak", sx, y, wd=0.35 if y > 9 else 0.2)


def hu_leg(face, fx, y, sx):
    if face == "top":
        return TROUS_GR[0]
    if face == "bottom":
        return SKIN_PALE[3]
    if y >= 10:
        return tone(SKIN_PALE, "hpf", sx, y, wd=0.4)  # pieds nus
    return tone(TROUS_GR, "hlg", sx, y)


def tex_zombie_villager():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    box(img, 0, 0, 8, 10, 8, hu_head)
    box(img, 24, 0, 2, 4, 2, hu_nose)
    box(img, 32, 0, 8, 10, 8, hu_hat)
    box(img, 16, 20, 8, 12, 6, hu_body)
    box(img, 0, 38, 8, 20, 6, hu_robe)
    box(img, 44, 22, 4, 12, 4, hu_arm)
    box(img, 0, 22, 4, 12, 4, hu_leg)
    return img


# ------------------------------------------------------------------------------------------------ TENUES (équipement 64×32)
# Casque = tête (0,0), plastron = corps (16,16) + bras (40,16), bottes = jambes (0,16) (bas seulement) ;
# jambières (texture humanoid_leggings) = corps (16,16) + jambes (0,16).

def armor(head=None, body=None, arm=None, feet=None):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    if head:
        box(img, 0, 0, 8, 8, 8, head)
    if body:
        box(img, 16, 16, 8, 12, 4, body)
    if arm:
        box(img, 40, 16, 4, 12, 4, arm)
    if feet:
        box(img, 0, 16, 4, 12, 4, feet)
    return img


def leggings(body=None, leg=None):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    if body:
        box(img, 16, 16, 8, 12, 4, body)
    if leg:
        box(img, 0, 16, 4, 12, 4, leg)
    return img


# --- Coureur : sweat à capuche rouge, bandes blanches, jogging gris, baskets ; griffures sanglantes

def co_head(face, fx, y, sx):
    if face == "top":
        return tone(HOOD, "cht", fx, y)
    if face == "bottom":
        return None
    if face == "front":
        if y <= 1 or fx in (0, 7):
            return HOOD[2] if y == 1 and 0 < fx < 7 else tone(HOOD, "chf", fx, y)
        if y == 7 and fx in (2, 5):
            return WHITE[0]  # cordons
        return None
    if face == "back" and y >= 6:
        return HOOD[2]
    return tone(HOOD, "chs", sx, y)


def gash(seed, face, fx, y):
    """Trois griffures en diagonale sur l'avant."""
    if face != "front":
        return None
    for k in range(3):
        if fx - y // 2 == 1 + 2 * k - 2 and 3 <= y <= 8:
            return BLOOD[2] if (y + k) % 2 else BLOOD[0]
    return None


def co_body(face, fx, y, sx):
    if face == "top":
        return HOOD[2] if fx in (2, 3, 4, 5) else tone(HOOD, "cbt", fx, y)
    if face == "bottom":
        return HOOD[2]
    if face == "back" and y < 3 and 1 <= fx <= 6:
        return HOOD[2] if y < 2 else HOOD[3]  # capuche rabattue
    g = gash("cg", face, fx, y)
    if g:
        return g
    if face == "front":
        if fx == 4 and y < 10:
            return hx("5e1c18")  # fermeture éclair
        if 7 <= y <= 9 and 1 <= fx <= 6:
            return HOOD[2] if y == 7 else tone(HOOD, "cpk", fx, y)  # poche kangourou
    if y == 11:
        return HOOD[2]
    if face in ("right", "left") and fx in (1, 2):
        return WHITE[0]
    return rain("cbr", sx, y, 0.2, 4) or tone(HOOD, "cbs", sx, y)


def co_arm(face, fx, y, sx):
    if face == "top":
        return tone(HOOD, "cat", fx, y)
    if face == "bottom":
        return None
    if y >= 10:
        return None  # mains (peau du zombie)
    if y == 9:
        return HOOD[2]
    if face == "right" and fx in (1, 2) or face == "left" and fx in (1, 2):
        return WHITE[0] if fx == 1 else WHITE[2]
    if face == "front" and y in (6, 7) and fx in (1, 2):
        return BLOOD[0] if y == 6 else BLOOD[2]
    return tone(HOOD, "cas", sx, y)


def co_feet(face, fx, y, sx):
    if face == "top":
        return None
    if face == "bottom":
        return hx("8a8a86")
    if y < 9:
        return None
    if y == 11:
        return hx("8a8a86")  # semelle
    if face in ("right", "left") and y == 10 and fx in (1, 2):
        return HOOD[0]
    return tone(WHITE, "csn", sx, y)


def co_legs_body(face, fx, y, sx):
    if face in ("top", "bottom"):
        return JOG[0]
    if y < 8:
        return None
    if y == 8 and face == "front" and fx in (3, 4):
        return WHITE[0]
    return tone(JOG, "cjb", sx, y)


def co_legs_leg(face, fx, y, sx):
    if face == "top":
        return JOG[0]
    if face == "bottom":
        return None
    if y >= 10:
        return None
    if y == 9:
        return JOG[2]  # chevilles resserrées
    if face == "right" and fx == 1 or face == "left" and fx == 2:
        return WHITE[1]
    if face == "front" and y in (4, 5) and fx == 2:
        return BLOOD[2]
    return tone(JOG, "cjl", sx, y)


# --- Brute : casque de chantier, salopette orange à bande réfléchissante, maillot de corps, bras énormes

def br_head(face, fx, y, sx):
    if face == "top":
        if fx in (3, 4):
            return YELLOW[1]  # nervure du casque
        return tone(YELLOW, "bht", fx, y)
    if face == "bottom":
        return None
    if y < 2:
        return tone(YELLOW, "bhs", sx, y) if not (face == "front" and y == 1 and fx in (3, 4)) else hx("3a3a3a")
    if y == 2:
        return YELLOW[2]  # visière
    return None


def br_body(face, fx, y, sx):
    if face == "top":
        return tone(UNDER, "bbt", fx, y)
    if face == "bottom":
        return ORANGE[2]
    if y == 9:
        return tone(REFLECT, "brf", sx, y)
    if face == "front":
        if fx in (1, 6) and y < 3:
            return hx("8a8a8a") if y == 2 else ORANGE[0]  # bretelles et boucles
        if 1 <= fx <= 6 and y >= 3:
            if y == 3:
                return ORANGE[2]
            if 4 <= y <= 5 and 2 <= fx <= 5:
                return ORANGE[2] if y == 4 or fx in (2, 5) else ORANGE[0]  # poche de bavette
            return tone(ORANGE, "bbf", fx, y)
    if face == "back":
        if (fx in (2, 5) and y < 8) or (y >= 8):
            return tone(ORANGE, "bbb", fx, y)
    if face in ("right", "left") and y >= 8:
        return tone(ORANGE, "bbs", sx, y)
    if R("btr", sx, y) < 0.08:
        return tone(SKIN, "bsk", sx, y)
    return rain("bbr", sx, y, 0.35, 6) or tone(UNDER, "bun", sx, y)


def br_arm(face, fx, y, sx):
    """Bras nus : la peau sur la couche d'armure (gonflée d'un pixel) épaissit les bras."""
    if face == "top":
        return tone(SKIN, "bat", fx, y)
    if face == "bottom":
        return SKIN[3]
    if face == "front" and fx in (1, 2) and y in (4, 5):
        return SKIN[1]  # biceps
    if face in ("right",) and y in (6,) and fx == 2:
        return hx("3c4a6a")  # vieux tatouage
    if R("bvn", sx, y) < 0.05:
        return SKIN[3]
    return rain("bar", sx, y, 0.3, 4) or tone(SKIN, "bas", sx, y, wd=0.35 if y > 9 else 0.22)


def br_feet(face, fx, y, sx):
    if face == "top":
        return None
    if face == "bottom":
        return BOOT_BR[3]
    if y < 8:
        return None
    if face == "front" and y >= 10:
        return hx("7a7a7a") if y == 10 else hx("5a5a5a")  # coque de sécurité
    if y == 11:
        return BOOT_BR[3]
    return tone(BOOT_BR, "bbo", sx, y)


def br_legs_body(face, fx, y, sx):
    if face in ("top", "bottom"):
        return ORANGE[0]
    return tone(ORANGE, "blb", sx, y) if y >= 6 else None


def br_legs_leg(face, fx, y, sx):
    if face == "top":
        return ORANGE[0]
    if face == "bottom" or y >= 8:
        return None
    if y == 6:
        return tone(REFLECT, "blr", sx, y)
    if face == "front" and y in (3, 4) and fx in (1, 2):
        return ORANGE[3]  # pièce au genou
    return rain("blra", sx, y, 0.15, 3) or tone(ORANGE, "bll", sx, y)


# --- Gonflé : peau violacée tendue, veines vertes lumineuses, débardeur qui craque

_VEINS = {}


def vein_map(seed, n=4, length=6, w=24):
    """Veines = petits chemins continus (marche au hasard vers le bas), comme des lézardes lumineuses."""
    if (seed, w) in _VEINS:
        return _VEINS[(seed, w)]
    m = {}
    for i in range(n):
        x = int(R("vx", seed, i) * w)
        y = int(R("vy", seed, i) * 8)
        for j in range(length):
            m[(x % w, y)] = VEIN[1] if j == 0 else VEIN[0] if j < length - 2 else VEIN[2]
            step = R("vs", seed, i, j)
            x += -1 if step < 0.3 else 1 if step > 0.7 else 0
            y += 1
    _VEINS[(seed, w)] = m
    return m


def veins(seed, sx, y, density=0.11, w=24):
    n = max(2, int(density * 40))
    return vein_map(seed, n, 6, w).get((sx, y))


GONFLE_FACE = [
    "sSsvssSs",
    "SsssvsSs",
    "sdssssds",
    "SDDssDDS",
    "SDkSSkDS",
    "SsddddsS",
    "sdDmmDds",
    "svsddsvs",
]


def go_head(face, fx, y, sx):
    if face == "front":
        return face_painter(GONFLE_FACE, SKIN_SWOL, {"v": VEIN[1], "k": hx("101010")}, "gf")(fx, y)
    if face in ("top", "bottom"):
        return veins("gt", fx, y) or tone(SKIN_SWOL, "gt", face, fx, y)
    return veins("gh", sx, y, 0.1, 32) or tone(SKIN_SWOL, "gh", sx, y)


def go_body(face, fx, y, sx):
    if face in ("top", "bottom"):
        return tone(SKIN_SWOL, "gbt", face, fx, y)
    if face == "front":
        # ventre bombé : reflet au centre, nombril
        if (fx, y) == (4, 8):
            return SKIN_SWOL[3]
        if 2 <= fx <= 5 and 5 <= y <= 10 and (fx in (3, 4) or y in (7, 8)):
            return veins("gbv", fx, y, 0.18) or SKIN_SWOL[1]
    if y < 3 and not (face == "front" and fx in (3, 4)):
        # débardeur trop petit, déchiré
        if R("gtk", sx, y) < 0.8:
            return tone(UNDER, "gtk", sx, y)
    if y == 3 and R("gtk2", sx) < 0.4:
        return UNDER[2]
    return veins("gb", sx, y) or tone(SKIN_SWOL, "gbs", sx, y)


def go_arm(face, fx, y, sx):
    if face in ("top", "bottom"):
        return tone(SKIN_SWOL, "gat", face, fx, y)
    return veins("ga", sx, y, 0.1, 16) or tone(SKIN_SWOL, "gas", sx, y, wd=0.35 if y > 9 else 0.2)


def go_legs_body(face, fx, y, sx):
    if face in ("top", "bottom"):
        return TROUS_BR[0]
    return tone(TROUS_BR, "glb", sx, y) if y >= 7 else None


def go_legs_leg(face, fx, y, sx):
    if face == "top":
        return TROUS_BR[0]
    if face == "bottom":
        return SKIN_SWOL[3]
    if y < 4:
        return tone(TROUS_BR, "gls", sx, y)  # short
    if y == 4:
        return TROUS_BR[2] if R("glf", sx) < 0.7 else tone(SKIN_SWOL, "glf2", sx, y)
    return veins("gl", sx, y, 0.07, 16) or tone(SKIN_SWOL, "glk", sx, y, wd=0.4 if y > 9 else 0.2)


# --- Rampant : haillons sombres, peau foncée, cheveux filasse, yeux pâles

RAMPANT_FACE = [
    "HHHHHHHH",
    "HHHHHsHH",
    "HHHsDsdH",
    "HHHddDDH",
    "HwHsskwH",
    "HHssddsH",
    "HHdmMmdH",
    "HHsdDsdH",
]


def ra_head(face, fx, y, sx):
    hk = {"H": HAIR_BK[0], "w": hx("dfe6c8"), "k": hx("0c0c0c"), "M": hx("0a0a0a")}
    if face == "front":
        c = RAMPANT_FACE[y][fx]
        if c == "H" and R("rhh", fx, y) < 0.25:
            return HAIR_BK[2]
        return face_painter(RAMPANT_FACE, SKIN_DARK, hk, "rf")(fx, y)
    if face == "top":
        return tone(HAIR_BK, "rht", fx, y)
    if face == "bottom":
        return SKIN_DARK[3]
    if face == "back" or y < 5 + int(R("rhs", sx) * 3):
        return tone(HAIR_BK, "rhs", sx, y)
    return tone(SKIN_DARK, "rhk", sx, y)


def ra_body(face, fx, y, sx):
    if face in ("top", "bottom"):
        return tone(RAG, "rbt", face, fx, y)
    if R("rho", sx, y // 2) < 0.15:
        return tone(SKIN_DARK, "rbh", sx, y)  # trous dans les haillons
    if R("rst", sx) < 0.3 and y % 4 == 0:
        return RAG[3]
    return tone(RAG, "rbs", sx, y, wl=0.15, wd=0.3)


def ra_arm(face, fx, y, sx):
    if face == "top":
        return tone(RAG, "rat", fx, y)
    if face == "bottom":
        return hx("101010")
    if y < 4 + int(R("rac", sx) * 3):
        return tone(RAG, "ras", sx, y)
    if y == 11 and face == "front":
        return hx("101010")  # ongles noirs
    return tone(SKIN_DARK, "rak", sx, y, wd=0.35)


def ra_legs_body(face, fx, y, sx):
    if face in ("top", "bottom"):
        return RAG[2]
    return tone(RAG, "rlb", sx, y) if y >= 6 else None


def ra_legs_leg(face, fx, y, sx):
    if face == "top":
        return RAG[2]
    if face == "bottom":
        return RAG[3]
    if y >= 10 and R("rlf", sx) < 0.5:
        return None  # lambeaux
    if R("rlh", sx, y // 3) < 0.12:
        return SKIN_DARK[3]
    return tone(RAG, "rll", sx, y, wl=0.1, wd=0.4)


# --- Blindé : tenue de maintien de l'ordre bleu nuit, gilet pare-balles, casque à visière

def bl_head(face, fx, y, sx):
    if face == "top":
        return CRS[1] if fx in (3, 4) else tone(CRS, "lht", fx, y)
    if face == "bottom":
        return None
    if face == "front":
        if y < 2:
            return tone(CRS, "lhf", fx, y)
        if y == 2:
            return CRS[2]
        if 3 <= y <= 5:
            if y == 4 and fx in (1, 2, 5, 6):
                return hx("3a4a58")  # yeux derrière la visière
            return VISOR[1] if (fx + y) % 5 == 0 else VISOR[0] if y < 5 else VISOR[2]
        if fx in (0, 7):
            return hx("151515")  # jugulaire
        return None
    if y < 6:
        return CRS[2] if y == 5 else tone(CRS, "lhs", sx, y)
    if face == "back" and y < 7:
        return CRS[3]
    return None


def bl_body(face, fx, y, sx):
    if face == "top":
        return VEST[0] if fx in (0, 1, 6, 7) else tone(CRS, "lbt", fx, y)
    if face == "bottom":
        return BLACK[2]
    if y >= 10:
        if face == "front" and y == 10 and fx in (3, 4):
            return hx("6a6a6a")
        return BLACK[0] if y == 10 else tone(CRS, "lbw", sx, y)
    if face == "front":
        if y == 2 and 2 <= fx <= 5:
            return hx("cfd0d0")  # bandeau d'identification
        if 5 <= y <= 8 and fx in (1, 2, 5, 6):
            return VEST[2] if y == 5 else VEST[1]  # poches
        if y == 0 and fx in (3, 4):
            return tone(CRS, "lbc", fx, y)
    if face == "back" and 3 <= y <= 4 and 2 <= fx <= 5:
        return hx("cfd0d0")
    if R("lsc", sx, y) < 0.04:
        return BLOOD[2]
    return tone(VEST, "lbv", sx, y)


def bl_arm(face, fx, y, sx):
    if face == "top":
        return VEST[1]
    if face == "bottom":
        return BLACK[2]
    if y < 3:
        return tone(VEST, "las", sx, y)  # épaulière
    if y >= 10:
        return tone(BLACK, "lag", sx, y)  # gants
    return tone(CRS, "lac", sx, y)


def bl_feet(face, fx, y, sx):
    if face == "top":
        return None
    if face == "bottom":
        return BLACK[3]
    if y < 8:
        return None
    if face == "front" and y == 9 and fx in (1, 2):
        return hx("3a3a3e")  # lacets
    return BLACK[3] if y == 11 else tone(BLACK, "lbo", sx, y)


def bl_legs_body(face, fx, y, sx):
    if face in ("top", "bottom"):
        return CRS[0]
    return tone(CRS, "llb", sx, y) if y >= 6 else None


def bl_legs_leg(face, fx, y, sx):
    if face == "top":
        return CRS[0]
    if face == "bottom" or y >= 9:
        return None
    if face == "front" and 3 <= y <= 5:
        return BLACK[1] if y == 3 else BLACK[0]  # genouillères
    return tone(CRS, "lll", sx, y)


# --- Élites : yeux dorés (casque) et brassard doré (bras du plastron)

EYES = {(1, 4): GOLD[0], (2, 4): GOLD[1], (5, 4): GOLD[1], (6, 4): GOLD[0]}


def elite_overlay(img):
    out = img.copy()
    for (x, y), c in EYES.items():
        out.putpixel((8 + x, 8 + y), c)  # avant de la tête : (8,8)

    def band(face, fx, y, sx):
        if face in ("top", "bottom"):
            return None
        if y == 3:
            return GOLD[1]
        if y == 4:
            return GOLD[0] if not (face == "front" and fx == 1) else hx("fff2b0")
        if y == 5:
            return GOLD[2]
        return None
    box(out, 40, 16, 4, 12, 4, band)
    return out


# ------------------------------------------------------------------------------------------------ catalogue

# Pièces d'équipement que le plugin met à chaque type (et pour l'élite).
PIECES = {
    "rodeur": [],
    "coureur": ["HEAD", "CHEST", "LEGS", "FEET"],
    "brute": ["HEAD", "CHEST", "LEGS", "FEET"],
    "hurleur": [],
    "cracheur": [],
    "gonfle": ["HEAD", "CHEST", "LEGS"],
    "noye": [],
    "rampant": ["HEAD", "CHEST", "LEGS"],
    "blinde": ["HEAD", "CHEST", "LEGS", "FEET"],
}
# Élite : + casque (yeux) et plastron (brassard) s'ils manquent ; le Hurleur (tête de villageois) : plastron seul.
PIECES_ELITE = {k: sorted(set(v) | ({"CHEST"} if k == "hurleur" else {"HEAD", "CHEST"}),
                          key=["HEAD", "CHEST", "LEGS", "FEET"].index) for k, v in PIECES.items()}

NOMS = {"rodeur": "Rôdeur", "coureur": "Coureur", "brute": "Brute", "hurleur": "Hurleur", "cracheur": "Cracheur",
        "gonfle": "Gonflé", "noye": "Noyé", "rampant": "Rampant", "blinde": "Blindé"}
BASE = {"rodeur": "zombie", "coureur": "zombie", "brute": "zombie", "hurleur": "zombie_villager", "cracheur": "husk",
        "gonfle": "zombie", "noye": "drowned", "rampant": "zombie", "blinde": "zombie"}


def tenues():
    """{id: (texture humanoid 64×32 ou None, texture humanoid_leggings ou None)} pour chaque type et élite."""
    t = {
        "rodeur": (None, None),
        "coureur": (armor(co_head, co_body, co_arm, co_feet), leggings(co_legs_body, co_legs_leg)),
        "brute": (armor(br_head, br_body, br_arm, br_feet), leggings(br_legs_body, br_legs_leg)),
        "hurleur": (None, None),
        "cracheur": (None, None),
        "gonfle": (armor(go_head, go_body, go_arm), leggings(go_legs_body, go_legs_leg)),
        "noye": (None, None),
        "rampant": (armor(ra_head, ra_body, ra_arm), leggings(ra_legs_body, ra_legs_leg)),
        "blinde": (armor(bl_head, bl_body, bl_arm, bl_feet), leggings(bl_legs_body, bl_legs_leg)),
    }
    out = {}
    for k, (hum, leg) in t.items():
        if hum is not None or leg is not None:
            out["zombie_" + k] = (hum, leg)
        base = hum if hum is not None else Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        el = elite_overlay(base)
        if k == "hurleur":  # pas d'yeux : la tête d'armure du villageois zombie n'a pas la même forme
            for (x, y) in EYES:
                el.putpixel((8 + x, 8 + y), (0, 0, 0, 0))
        out["zombie_" + k + "_elite"] = (el, leg)
    return out


def png(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()


def bases():
    zv = tex_zombie_villager()
    dr, dro = tex_drowned()
    return {
        "zombie/zombie.png": tex_zombie(),
        "zombie/husk.png": tex_husk(),
        "zombie/drowned.png": dr,
        "zombie/drowned_outer_layer.png": dro,
        "zombie_villager/zombie_villager.png": zv,
    }


def ajouter(files, jar_path=JAR):
    """Ajoute au dict files (chemin dans le zip -> bytes) les textures de base, les surcouches vidées et les tenues."""
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        for rel, img in bases().items():
            p = "assets/minecraft/textures/entity/" + rel
            assert p in names, p
            files[p] = png(img)
        # Surcouches de villageois zombie (biome, métier, niveau) rendues transparentes : le Hurleur garde sa blouse
        # quel que soit le métier tiré (le plugin doit quand même mettre Profession.NONE, voir ZOMBIES.md).
        pref = "assets/minecraft/textures/entity/zombie_villager/"
        for n in sorted(names):
            if n.startswith(pref) and n.endswith(".png") and "/baby/" not in n and \
                    any(n.startswith(pref + d + "/") for d in ("type", "profession", "profession_level")):
                size = Image.open(io.BytesIO(jar.read(n))).size
                files[n] = png(Image.new("RGBA", size, (0, 0, 0, 0)))
    for aid, (hum, leg) in tenues().items():
        layers = {}
        if hum is not None:
            files[f"assets/refuge/textures/entity/equipment/humanoid/{aid}.png"] = png(hum)
            layers["humanoid"] = [{"texture": f"refuge:{aid}"}]
        if leg is not None:
            files[f"assets/refuge/textures/entity/equipment/humanoid_leggings/{aid}.png"] = png(leg)
            layers["humanoid_leggings"] = [{"texture": f"refuge:{aid}"}]
        files[f"assets/refuge/equipment/{aid}.json"] = (json.dumps({"layers": layers}, indent=2) + "\n").encode()
    return files


# ------------------------------------------------------------------------------------------------ aperçu


def crop(img, x, y, w, h, flip=False):
    c = img.crop((x, y, x + w, y + h))
    return c.transpose(Image.FLIP_LEFT_RIGHT) if flip else c


def figure(kind, base, armors, back=False):
    """Corps assemblé à plat, de face ou de dos. kind : humanoid | drowned | villager.
    armors : liste de (texture 64×32, liste de parties 'head'|'body'|'arms'|'legs')."""
    hh = 10 if kind == "villager" else 8
    H = hh + 24
    fig = Image.new("RGBA", (16, H), (0, 0, 0, 0))

    def face(u, v, w, h, d):
        return (u + 2 * d + w, v + d) if back else (u + d, v + d)

    def put(img, u, v, w, h, d, pos, flip=False):
        x, y = face(u, v, w, h, d)
        fig.alpha_composite(crop(img, x, y, w, h, flip), pos)

    # membres : de face, le bras droit de l'entité est à gauche de l'image ; de dos, à droite
    L, Rr = (0, 12) if not back else (12, 0)
    if kind == "villager":
        put(base, 0, 0, 8, 10, 8, (4, 0))
        put(base, 16, 20, 8, 12, 6, (4, hh))
        put(base, 44, 22, 4, 12, 4, (L, hh))
        put(base, 44, 22, 4, 12, 4, (Rr, hh), flip=True)
        put(base, 0, 22, 4, 12, 4, (4 if not back else 8, hh + 12))
        put(base, 0, 22, 4, 12, 4, (8 if not back else 4, hh + 12), flip=True)
        put(base, 0, 38, 8, 20, 6, (4, hh))
        put(base, 32, 0, 8, 10, 8, (4, 0))
        if not back:
            fig.alpha_composite(crop(base, 26, 2, 2, 4), (7, 7))
    else:
        put(base, 0, 0, 8, 8, 8, (4, 0))
        put(base, 16, 16, 8, 12, 4, (4, hh))
        put(base, 40, 16, 4, 12, 4, (L, hh))
        if kind == "drowned":
            put(base, 32, 48, 4, 12, 4, (Rr, hh))
            put(base, 16, 48, 4, 12, 4, (8 if not back else 4, hh + 12))
        else:
            put(base, 40, 16, 4, 12, 4, (Rr, hh), flip=True)
            put(base, 0, 16, 4, 12, 4, (8 if not back else 4, hh + 12), flip=True)
        put(base, 0, 16, 4, 12, 4, (4 if not back else 8, hh + 12))
    for tex, parts in armors:
        if tex is None:
            continue
        if "legs" in parts or "feet" in parts:
            if "legs" in parts:
                put(tex, 16, 16, 8, 12, 4, (4, hh))
            put(tex, 0, 16, 4, 12, 4, (4 if not back else 8, hh + 12))
            put(tex, 0, 16, 4, 12, 4, (8 if not back else 4, hh + 12), flip=True)
        if "body" in parts:
            put(tex, 16, 16, 8, 12, 4, (4, hh))
        if "arms" in parts:
            put(tex, 40, 16, 4, 12, 4, (L, hh))
            put(tex, 40, 16, 4, 12, 4, (Rr, hh), flip=True)
        if "head" in parts and kind != "villager":
            put(tex, 0, 0, 8, 8, 8, (4, 0))
    return fig


def apercu(path):
    b = bases()
    tn = tenues()
    kinds = {"zombie": ("humanoid", b["zombie/zombie.png"], None),
             "husk": ("humanoid", b["zombie/husk.png"], None),
             "drowned": ("drowned", b["zombie/drowned.png"], b["zombie/drowned_outer_layer.png"]),
             "zombie_villager": ("villager", b["zombie_villager/zombie_villager.png"], None)}
    S = 9
    cw, ch = 16 * S, 34 * S
    gap = 14
    cell_w = 4 * cw + 3 * gap + 40 + 70
    cell_h = ch + 70
    cols = 3
    W, H = cols * cell_w + 40, 3 * cell_h + 90
    img = Image.new("RGBA", (W, H), hx("2b2d30"))
    d = ImageDraw.Draw(img)
    try:
        f1 = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 26)
        f2 = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 16)
    except OSError:
        f1 = f2 = ImageFont.load_default()
    d.text((20, 18), "Dernier Refuge - zombies (face, dos | élite face, dos)", font=f1, fill=(235, 235, 235))
    for i, k in enumerate(PIECES):
        kind, base, outer = kinds[BASE[k]]
        cx, cy = 20 + (i % cols) * cell_w, 70 + (i // cols) * cell_h
        figs = []
        for elite in (False, True):
            aid = "zombie_" + k + ("_elite" if elite else "")
            hum, leg = tn.get(aid, (None, None))
            pieces = PIECES_ELITE[k] if elite else PIECES[k]
            parts = []
            if "HEAD" in pieces:
                parts.append("head")
            if "CHEST" in pieces:
                parts += ["body", "arms"]
            if "FEET" in pieces:
                parts.append("feet")
            arm = []
            if outer is not None:
                arm.append((outer, ["legs", "body", "arms", "head"]))
            if leg is not None and "LEGS" in pieces:
                arm.append((leg, ["legs"]))
            if hum is not None:
                # bottes : bas des jambes de la texture humanoid
                arm.append((hum, parts))
            for back in (False, True):
                figs.append(figure(kind, base, arm, back))
        for j, fg in enumerate(figs):
            x = cx + j * (cw + gap) + (40 if j >= 2 else 0)
            fg = fg.resize((16 * S, fg.height * S), Image.NEAREST)
            img.alpha_composite(fg, (x, cy + ch - fg.height))
        d.line((cx + 2 * cw + gap + 20, cy, cx + 2 * cw + gap + 20, cy + ch), fill=(80, 80, 84), width=2)
        lab = NOMS[k]
        d.text((cx, cy + ch + 8), lab, font=f1, fill=(240, 240, 240))
        d.text((cx + 2 * cw + 2 * gap + 40, cy + ch + 14), "élite", font=f2, fill=(230, 190, 60))
        d.text((cx + 150, cy + ch + 14), f"base : {BASE[k]}", font=f2, fill=(160, 160, 160))
    img.save(path)


def textures_planche(path):
    """Planche des textures brutes (contrôle de la mise en page UV)."""
    b = bases()
    tn = tenues()
    items = list(b.items()) + [(k + " (armure)", v[0]) for k, v in tn.items() if v[0] is not None] + \
        [(k + " (jambières)", v[1]) for k, v in tn.items() if v[1] is not None and not k.endswith("_elite")]
    S = 5
    cols = 6
    cw, ch = 64 * S + 20, 64 * S + 30
    rows = (len(items) + cols - 1) // cols
    img = Image.new("RGBA", (cols * cw + 20, rows * ch + 20), hx("2b2d30"))
    d = ImageDraw.Draw(img)
    try:
        f = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 14)
    except OSError:
        f = ImageFont.load_default()
    for i, (n, t) in enumerate(items):
        x, y = 10 + (i % cols) * cw, 10 + (i // cols) * ch
        bg = Image.new("RGBA", (t.width * S, t.height * S), hx("5a5d62"))
        bg.alpha_composite(t.resize((t.width * S, t.height * S), Image.NEAREST))
        img.alpha_composite(bg, (x, y + 18))
        d.text((x, y), n, font=f, fill=(230, 230, 230))
    img.save(path)


def main():
    files = {}
    with zipfile.ZipFile(MCMETA_SRC) as z:
        files["pack.mcmeta"] = z.read("pack.mcmeta")
    ajouter(files, JAR)
    out = os.path.join(ICI, "test_zombies.zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for k in sorted(files):
            zi = zipfile.ZipInfo(k, (2026, 11, 13, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, files[k])
    print(f"{out} : {len(files)} fichiers")
    ap = os.path.join(ICI, "..", "apercus")
    apercu(os.path.join(ap, "zombies.png"))
    textures_planche(os.path.join(ap, "zombies_textures.png"))
    print("apercus/zombies.png, apercus/zombies_textures.png")
    r = subprocess.run([sys.executable, os.path.join(ICI, "verifier_pack.py"), out, "--vanilla", JAR,
                        "--vanilla-overrides"], capture_output=True, text=True)
    print(r.stdout[-3000:], r.stderr[-2000:])
    sys.exit(r.returncode)


if __name__ == "__main__":
    main()
