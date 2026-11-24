# -*- coding: utf-8 -*-
"""Genere les fichiers de pack d'EemiInfection (namespace eemicraft, dossier infection/).

Usage : python -I gen_infection_pack.py
Ecrit dans EemiInfection/pack/assets/eemicraft/... et les apercus dans EemiInfection/pack_src/apercu/.

- 5 armes en modeles 3D en cubes (meme convention que le marqueur du Paintball : canon vers -Z,
  crosse vers +Z, axe du canon vers y 12, poignee vers z 12) : fusil d'assaut, fusil a pompe,
  fusil de precision (lunette), mitraillette, pistolet ;
- sprites 16x16 : grenade, caisse d'armes (choix du kit), machette, griffes (zombie alpha),
  pouvoirs (bond, langue, crachat acide, escalade), boule d'acide en vol, icones de la tenue ;
- equipement : tenue de zombie complete (asset eemicraft:infection_zombie, couches humanoid +
  humanoid_leggings) et gilet tactique des survivants (asset eemicraft:infection_survivant).
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

TEX_ITEM = os.path.join(PACK, "textures", "item", "infection")
MODELS = os.path.join(PACK, "models", "item", "infection")
ITEMS = os.path.join(PACK, "items", "infection")
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


def clamp(v, a=0, b=255):
    return max(a, min(b, int(round(v))))


def mix(c1, c2, t):
    return tuple(clamp(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def jitter(c, rnd, amt):
    d = rnd.randint(-amt, amt)
    return tuple(clamp(x + d) for x in c[:3])


# ============================================================ textures de matiere (armes 3D)

N = 32  # resolution des matieres : 2 texels par unite de modele (UV toujours en 0..16)


def grad_tile(light, dark, seed, noise=5, highlight=True):
    """Degrade vertical (clair en haut, sombre en bas) : v suit la hauteur absolue du modele."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        base = mix(light, dark, y / (N - 1.0))
        for x in range(N):
            c = jitter(base, rnd, noise)
            img.putpixel((x, y), c + (255,))
    if highlight:
        for x in range(N):
            if rnd.random() < 0.8:
                img.putpixel((x, 2), mix(light, (255, 255, 255), 0.25) + (255,))
    return img


def tile_metal():
    return grad_tile((118, 124, 132), (36, 39, 44), 11, 4)


def tile_steel():
    # acier bronze (canon du fusil a pompe) : reflets bleutes
    return grad_tile((128, 138, 156), (34, 38, 50), 12, 4)


def tile_black():
    img = grad_tile((70, 70, 72), (20, 20, 22), 13, 3, highlight=False)
    rnd = random.Random(131)
    for _ in range(90):
        x, y = rnd.randrange(N), rnd.randrange(N)
        c = img.getpixel((x, y))
        img.putpixel((x, y), tuple(clamp(v + 9) for v in c[:3]) + (255,))
    return img


def tile_tan():
    return grad_tile((204, 178, 128), (118, 96, 64), 14, 5)


def tile_olive():
    return grad_tile((118, 126, 78), (48, 54, 32), 15, 5)


def tile_wood():
    rnd = random.Random(16)
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        base = mix((164, 102, 56), (80, 44, 22), y / (N - 1.0))
        streak = rnd.random() < 0.35
        for x in range(N):
            c = jitter(base, rnd, 4)
            if streak and rnd.random() < 0.75:
                c = tuple(clamp(v * 0.82) for v in c)
            img.putpixel((x, y), c + (255,))
    for (x, y) in ((10, 12), (11, 12), (12, 12), (10, 13), (11, 13), (22, 22), (23, 22)):
        img.putpixel((x, y), (70, 38, 18, 255))
    return img


def tile_grip():
    rnd = random.Random(17)
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            c = (46, 46, 48) if (x + y) % 2 == 0 else (28, 28, 30)
            img.putpixel((x, y), jitter(c, rnd, 3) + (255,))
    return img


def tile_rubber():
    return grad_tile((48, 48, 50), (24, 24, 26), 18, 2, highlight=False)


def tile_rail():
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            c = (78, 80, 84) if x % 3 != 0 else (26, 27, 30)
            if y < 3:
                c = tuple(clamp(v + 20) for v in c)
            img.putpixel((x, y), c + (255,))
    return img


def tile_shroud():
    img = tile_metal()
    for y in range(N):
        for x in range(N):
            if x % 3 == 1 and y % 3 == 1:
                img.putpixel((x, y), (8, 8, 10, 255))
    return img


def tile_glass():
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            t = (x + y) / (2.0 * N - 2)
            c = mix((70, 170, 210), (12, 40, 70), t)
            img.putpixel((x, y), c + (255,))
    for (x, y) in ((6, 6), (7, 6), (6, 7), (8, 6), (18, 4), (19, 4)):
        img.putpixel((x, y), (225, 245, 255, 255))
    return img


def tile_flat(c, seed, amt=6):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        base = mix(c, tuple(v * 0.6 for v in c), y / (N - 1.0))
        for x in range(N):
            img.putpixel((x, y), jitter(base, rnd, amt) + (255,))
    return img

MATS = {
    "metal": tile_metal,
    "steel": tile_steel,
    "black": tile_black,
    "tan": tile_tan,
    "olive": tile_olive,
    "wood": tile_wood,
    "grip": tile_grip,
    "rubber": tile_rubber,
    "rail": tile_rail,
    "shroud": tile_shroud,
    "glass": tile_glass,
    "brass": lambda: tile_flat((222, 178, 86), 21, 5),
    "copper": lambda: tile_flat((206, 118, 72), 22, 5),
    "red": lambda: tile_flat((230, 40, 30), 23, 3),
}
MAT_IMG = {k: f() for k, f in MATS.items()}
for k, im in MAT_IMG.items():
    save_png(im, os.path.join(TEX_ITEM, "mat_" + k + ".png"))


# ============================================================ modeles 3D

VALID_ANGLES = (-45, -22.5, 0, 22.5, 45)


class Model:
    def __init__(self, name):
        self.name = name
        self.el = []
        self.used = []

    def box(self, f, t, mat, rot=None):
        x0, y0, z0 = f
        x1, y1, z1 = t
        for v in (x0, y0, z0, x1, y1, z1):
            assert -16 <= v <= 32, (self.name, f, t)
        assert x1 > x0 and y1 > y0 and z1 > z0, (self.name, f, t)
        if mat not in self.used:
            self.used.append(mat)

        def fit(u0, w):
            w = min(w, 16.0)
            u0 = max(0.0, min(u0, 16.0 - w))
            return round(u0, 3), round(w, 3)

        wx, hy, lz = x1 - x0, y1 - y0, z1 - z0
        vtop = 16.0 - y1
        u_z, w_z = fit(z0 % 16, lz)
        u_x, w_x = fit(x0 % 16, wx)
        v_y, h_y = fit(vtop, hy)
        faces = {}
        faces["north"] = {"uv": [u_x, v_y, u_x + w_x, v_y + h_y]}
        faces["south"] = {"uv": [u_x, v_y, u_x + w_x, v_y + h_y]}
        faces["east"] = {"uv": [u_z, v_y, u_z + w_z, v_y + h_y]}
        faces["west"] = {"uv": [u_z, v_y, u_z + w_z, v_y + h_y]}
        # dessus/dessous : texture tournee pour que le grain suive la longueur de l'arme
        _, wz2 = fit(0, lz)
        _, wx2 = fit(0, wx)
        faces["up"] = {"uv": [u_z, 1.0, u_z + wz2, 1.0 + wx2], "rotation": 90}
        faces["down"] = {"uv": [u_z, 16.0 - wx2, u_z + wz2, 16.0], "rotation": 90}
        for fc in faces.values():
            fc["uv"] = [round(v, 3) for v in fc["uv"]]
            fc["texture"] = "#" + mat
        e = {"from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": faces}
        if rot:
            ang, axis, origin = rot
            assert ang in VALID_ANGLES, (self.name, ang)
            e["rotation"] = {"angle": ang, "axis": axis, "origin": list(origin)}
        self.el.append(e)
        return e

    def json(self, display):
        tex = {m: NS + ":item/infection/mat_" + m for m in self.used}
        tex["particle"] = tex[self.used[0]]
        return {
            "credit": "EEMICRAFT - EemiInfection",
            "texture_size": [16, 16],
            "textures": tex,
            "elements": self.el,
            "gui_light": "front",
            "display": display,
        }


def gun_display(fp=0.6, tp=0.75, gui=0.5, ground=0.4, fixed=0.5, fp_t=(0, 1.5, -1), tp_t=(0, 3.5, 2.5)):
    """Reglages repris du marqueur du Paintball (rendu valide en jeu), echelle par arme."""
    def s(v):
        return [v, v, v]
    return {
        "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": list(tp_t), "scale": s(tp)},
        "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": list(tp_t), "scale": s(tp)},
        "firstperson_righthand": {"rotation": [0, 6, 0], "translation": list(fp_t), "scale": s(fp)},
        "firstperson_lefthand": {"rotation": [0, -6, 0], "translation": list(fp_t), "scale": s(fp)},
        "gui": {"rotation": [90, -70, 90], "translation": [0, 0, 0], "scale": s(gui)},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": s(ground)},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": s(fixed)},
        "head": {"rotation": [0, 0, 0], "translation": [0, 6, 0], "scale": s(0.8)},
    }


def fusil_assaut():
    m = Model("fusil_assaut")
    m.box((7.1, 11.1, -14), (8.9, 12.9, -11.5), "black")            # cache-flamme
    m.box((7.35, 11.35, -11.5), (8.65, 12.65, -3), "metal")          # canon
    m.box((7.2, 12.6, -5), (8.8, 13.4, -3.8), "black")               # bloc de gaz
    m.box((7.7, 13.4, -4.8), (8.3, 15.2, -4.2), "black")             # guidon
    m.box((6.6, 10.0, -3), (9.4, 13.6, 6), "tan")                    # garde-main
    m.box((6.9, 13.6, -3), (9.1, 14.2, 6), "rail")                   # rail du dessus
    m.box((6.9, 9.4, -2), (9.1, 10.0, 4), "rail")                    # rail du dessous
    for z in (-1.5, 0.5, 2.5):                                       # events du garde-main
        m.box((9.4, 11.0, z), (9.5, 12.4, z + 1.0), "black")
        m.box((6.5, 11.0, z), (6.6, 12.4, z + 1.0), "black")
    m.box((6.75, 10.6, 6), (9.25, 13.9, 14.5), "black")              # carcasse superieure
    m.box((6.9, 13.9, 6.5), (9.1, 14.4, 14), "rail")
    m.box((7.0, 14.4, 8), (9.0, 16.6, 11), "black")                  # viseur point rouge
    m.box((7.3, 14.8, 7.8), (8.7, 16.2, 8), "glass")
    m.box((7.3, 14.8, 11), (8.7, 16.2, 11.2), "glass")
    m.box((7.75, 15.0, 10.8), (8.25, 15.4, 11.0), "red")
    m.box((7.4, 13.2, 14.5), (8.6, 13.8, 15.3), "black")             # levier d'armement
    m.box((9.25, 11.4, 8), (9.35, 12.6, 11), "metal")                # fenetre d'ejection
    m.box((6.85, 8.4, 6.5), (9.15, 10.6, 14), "tan")                 # carcasse inferieure
    m.box((6.95, 8.0, 7.2), (9.05, 8.6, 10.4), "tan")                # puits du chargeur
    m.box((7.05, 3.0, 7.4), (8.95, 8.4, 10.2), "black", (22.5, "x", (8, 8.4, 8.8)))  # chargeur courbe
    m.box((7.6, 7.4, 10.4), (8.4, 7.9, 12.4), "black")               # pontet
    m.box((7.75, 7.9, 10.9), (8.25, 8.5, 11.3), "metal")             # detente
    m.box((7.05, 3.4, 12.2), (8.95, 8.4, 14.2), "grip", (-22.5, "x", (8, 8.4, 13.2)))  # poignee
    m.box((7.25, 11.0, 14.5), (8.75, 12.6, 18.5), "black")           # tube de crosse
    m.box((6.9, 8.6, 18.0), (9.1, 13.0, 24.0), "tan")                # crosse
    m.box((7.1, 13.0, 18.5), (8.9, 13.6, 23.5), "tan")               # appui-joue
    m.box((6.8, 8.4, 24), (9.2, 13.2, 25), "rubber")                 # plaque de couche
    return m, gun_display(fp=0.58, gui=0.46)


def fusil_pompe():
    m = Model("fusil_pompe")
    m.box((7.25, 12.0, -15), (8.75, 13.5, 7), "steel")               # canon
    m.box((7.75, 13.5, -14.6), (8.25, 14.0, -14.1), "brass")         # guidon (bille)
    m.box((7.35, 10.4, -12.5), (8.65, 11.9, 6), "steel")             # magasin tubulaire
    m.box((7.25, 10.3, -13.2), (8.75, 12.0, -12.5), "black")         # bouchon du magasin
    m.box((7.1, 10.2, -11.5), (8.9, 13.7, -10.7), "black")           # collier
    m.box((6.55, 9.6, -8.5), (9.45, 12.4, -1), "wood")               # pompe
    for z in (-7.5, -6.0, -4.5, -3.0):                               # rainures de la pompe
        m.box((9.45, 10.2, z), (9.5, 11.8, z + 0.5), "black")
        m.box((6.5, 10.2, z), (6.55, 11.8, z + 0.5), "black")
    m.box((6.8, 9.4, 6), (9.2, 13.8, 13), "steel")                   # boitier
    m.box((9.2, 11.6, 7.5), (9.3, 12.8, 10.5), "black")              # fenetre d'ejection
    m.box((7.3, 13.8, 6.5), (8.7, 14.1, 12.5), "black")
    m.box((7.55, 8.0, 9.5), (8.45, 8.5, 12), "black")                # pontet
    m.box((7.55, 8.0, 11.6), (8.45, 9.4, 12.0), "black")
    m.box((7.75, 8.5, 10.2), (8.25, 9.3, 10.6), "steel")             # detente
    m.box((6.95, 8.6, 13), (9.05, 12.6, 17), "wood")                 # poignet de crosse
    m.box((6.85, 7.6, 16.5), (9.15, 12.6, 21), "wood")               # crosse
    m.box((6.85, 6.6, 21), (9.15, 12.4, 26), "wood")
    m.box((6.75, 6.4, 26), (9.25, 12.6, 27), "rubber")               # plaque de couche
    return m, gun_display(fp=0.58, gui=0.44)


def sniper():
    m = Model("sniper")
    m.box((7.0, 11.6, -16), (9.0, 13.6, -13.5), "black")             # frein de bouche
    m.box((9.0, 12.1, -15.4), (9.05, 13.1, -14.8), "metal")
    m.box((6.95, 12.1, -15.4), (7.0, 13.1, -14.8), "metal")
    m.box((7.4, 12.0, -13.5), (8.6, 13.2, 2), "metal")               # canon
    m.box((6.6, 9.6, -4), (9.4, 12.6, 9), "olive")                   # fut
    m.box((7.0, 9.0, -3), (9.0, 9.6, 8), "olive")
    m.box((7.0, 9.0, -4), (9.0, 9.6, -3), "black")                   # bipied replie
    m.box((6.5, 9.0, -3.5), (7.0, 9.6, 1.5), "black")
    m.box((9.0, 9.0, -3.5), (9.5, 9.6, 1.5), "black")
    m.box((6.9, 11.2, 9), (9.1, 13.9, 16), "metal")                  # boitier de culasse
    m.box((9.1, 12.2, 14), (11.2, 12.8, 14.6), "metal")              # levier de culasse
    m.box((10.8, 11.6, 13.8), (11.8, 12.6, 14.8), "black")
    m.box((7.0, 13.9, 10), (9.0, 15.2, 11), "black")                 # colliers de lunette
    m.box((7.0, 13.9, 14), (9.0, 15.2, 15), "black")
    m.box((7.2, 15.0, 7), (8.8, 16.6, 18), "black")                  # tube de la lunette
    m.box((6.7, 14.6, 3), (9.3, 17.0, 7), "black")                   # objectif
    m.box((6.9, 14.8, 2.9), (9.1, 16.8, 3.0), "glass")
    m.box((7.6, 16.6, 11.8), (8.4, 17.4, 12.8), "black")             # tourelles
    m.box((8.8, 15.4, 11.8), (9.6, 16.2, 12.8), "black")
    m.box((6.85, 14.7, 18), (9.15, 16.9, 20.5), "black")             # oculaire
    m.box((7.0, 14.9, 20.5), (9.0, 16.7, 20.6), "glass")
    m.box((7.2, 7.6, 10.5), (8.8, 9.6, 13), "black")                 # chargeur
    m.box((7.55, 8.4, 13), (8.45, 8.9, 15.5), "black")               # pontet
    m.box((7.75, 8.9, 13.8), (8.25, 9.6, 14.2), "metal")             # detente
    m.box((7.0, 4.0, 15.6), (9.0, 9.6, 17.6), "olive", (-22.5, "x", (8, 9.6, 16.6)))  # poignee
    m.box((6.8, 9.6, 16), (9.2, 13.0, 28), "olive")                  # crosse
    m.box((6.8, 6.4, 19), (9.2, 9.6, 28), "olive")
    m.box((7.0, 13.0, 19), (9.0, 14.0, 26), "black")                 # appui-joue
    m.box((6.7, 6.2, 28), (9.3, 13.2, 29.2), "rubber")
    return m, gun_display(fp=0.5, tp=0.68, gui=0.36, ground=0.34, fixed=0.42)


def mitraillette():
    m = Model("mitraillette")
    m.box((7.3, 11.3, -7), (8.7, 12.7, -5.5), "black")               # bouche
    m.box((7.0, 11.0, -5.5), (9.0, 13.0, 1), "shroud")               # manchon perce
    m.box((6.75, 10.0, 1), (9.25, 13.8, 6), "black")                 # corps avant
    m.box((6.8, 9.6, 6), (9.2, 13.9, 13), "black")                   # carcasse
    m.box((6.95, 13.9, 2), (9.05, 14.4, 12.5), "rail")
    m.box((7.2, 14.4, 11), (8.8, 15.4, 12), "black")                 # hausse
    m.box((7.5, 14.4, 2.4), (8.5, 15.6, 3.2), "black")               # guidon
    m.box((9.25, 12.4, 3), (9.9, 13.0, 4.2), "metal")                # levier d'armement
    m.box((9.2, 11.0, 7.5), (9.3, 12.6, 10), "metal")                # fenetre d'ejection
    m.box((7.1, 1.2, 5.6), (8.9, 9.6, 7.9), "metal")                 # chargeur droit
    m.box((6.95, 0.6, 5.4), (9.05, 1.2, 8.1), "black")
    m.box((7.1, 5.5, -1.0), (8.9, 10.0, 0.8), "grip")                # poignee avant
    m.box((7.55, 8.4, 8.2), (8.45, 8.9, 10.6), "black")              # pontet
    m.box((7.75, 8.9, 8.8), (8.25, 9.6, 9.2), "metal")               # detente
    m.box((7.05, 3.6, 10.4), (8.95, 9.6, 12.4), "grip", (-22.5, "x", (8, 9.6, 11.4)))  # poignee
    m.box((7.0, 12.0, 13), (7.6, 12.7, 20.5), "metal")               # crosse filaire
    m.box((8.4, 12.0, 13), (9.0, 12.7, 20.5), "metal")
    m.box((7.0, 9.3, 13), (7.6, 9.9, 20.5), "metal")
    m.box((8.4, 9.3, 13), (9.0, 9.9, 20.5), "metal")
    m.box((6.8, 8.8, 20.5), (9.2, 13.0, 21.5), "grip")               # plaque de crosse
    return m, gun_display(fp=0.62, gui=0.55)


def pistolet():
    m = Model("pistolet")
    m.box((6.9, 10.6, 0), (9.1, 13.6, 13), "metal")                  # culasse
    for z in (10.6, 11.4, 12.2):                                     # stries
        m.box((9.1, 11.0, z), (9.15, 13.2, z + 0.4), "black")
        m.box((6.85, 11.0, z), (6.9, 13.2, z + 0.4), "black")
    m.box((7.7, 13.6, 0.6), (8.3, 14.2, 1.3), "black")               # guidon
    m.box((7.3, 13.6, 11.8), (8.7, 14.3, 12.8), "black")             # hausse
    m.box((7.45, 11.4, -0.1), (8.55, 12.6, 0), "black")              # bouche
    m.box((9.1, 12.0, 5.5), (9.15, 13.0, 8.5), "black")              # fenetre d'ejection
    m.box((7.0, 9.2, 0.6), (9.0, 10.6, 12), "black")                 # carcasse
    m.box((7.0, 8.8, 1), (9.0, 9.2, 4), "black")                     # rail
    m.box((7.5, 7.0, 4.5), (8.5, 7.6, 9.0), "black")                 # pontet
    m.box((7.5, 7.0, 4.5), (8.5, 9.2, 5.1), "black")
    m.box((7.75, 7.6, 7.2), (8.25, 9.2, 7.7), "copper")              # detente
    m.box((6.95, 2.6, 9.0), (9.05, 9.2, 12.4), "grip", (-22.5, "x", (8, 9.2, 10.7)))  # poignee
    m.box((6.85, 2.0, 9.0), (9.15, 2.6, 12.6), "copper", (-22.5, "x", (8, 9.2, 10.7)))  # talon du chargeur
    m.box((7.2, 9.2, 12.4), (8.8, 10.2, 14), "black")                # queue de castor
    m.box((9.0, 9.8, 6), (9.2, 10.4, 8), "copper")                   # arretoir de culasse
    return m, gun_display(fp=0.78, tp=0.9, gui=0.72, ground=0.5, fixed=0.65, fp_t=(0, 2.5, -1))


GUNS = [fusil_assaut, fusil_pompe, sniper, mitraillette, pistolet]
GUN_MODELS = {}
for fn in GUNS:
    mdl, disp = fn()
    GUN_MODELS[mdl.name] = mdl
    write_json(os.path.join(MODELS, mdl.name + ".json"), mdl.json(disp))


# ============================================================ sprites 16x16

OUT = (24, 26, 20, 255)


def outline(img, col=OUT):
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] != 0:
                    img.putpixel((x, y), col)
                    break
    return img


def shade(img, light_dir=(-1, -1), amt=0.18):
    """Assombrit les pixels du bas/droite, eclaircit le haut/gauche (volume simple)."""
    w, h = img.size
    out = img.copy()
    for y in range(h):
        for x in range(w):
            p = img.getpixel((x, y))
            if p[3] == 0:
                continue
            ux, uy = x - light_dir[0], y - light_dir[1]
            dx, dy = x + light_dir[0], y + light_dir[1]
            lit = not (0 <= ux < w and 0 <= uy < h) or img.getpixel((ux, uy))[3] == 0
            dark = not (0 <= dx < w and 0 <= dy < h) or img.getpixel((dx, dy))[3] == 0
            if lit and not dark:
                out.putpixel((x, y), tuple(clamp(v + (255 - v) * amt) for v in p[:3]) + (255,))
            elif dark and not lit:
                out.putpixel((x, y), tuple(clamp(v * (1 - amt)) for v in p[:3]) + (255,))
    return out


def new16():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def sp_grenade():
    im = new16()
    d = ImageDraw.Draw(im)
    d.ellipse((3, 5, 12, 15), fill=(92, 112, 58, 255))
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] and ((x - 3) % 3 == 0 or (y - 5) % 3 == 0):
                im.putpixel((x, y), (62, 78, 38, 255))
    im = shade(im, amt=0.22)
    for p in ((5, 7), (5, 8), (6, 7)):
        im.putpixel(p, (160, 182, 108, 255))
    d = ImageDraw.Draw(im)
    d.rectangle((6, 3, 9, 4), fill=(132, 134, 140, 255))
    d.rectangle((7, 2, 8, 2), fill=(96, 98, 104, 255))
    for p in ((9, 3), (10, 3), (10, 4), (11, 5), (11, 6), (11, 7), (11, 8)):
        im.putpixel(p, (182, 186, 190, 255))
    for p in ((3, 1), (4, 1), (5, 2), (4, 3), (3, 3), (2, 2), (5, 3)):
        im.putpixel(p, (196, 198, 202, 255))
    return outline(im)


def sp_caisse():
    im = new16()
    d = ImageDraw.Draw(im)
    d.rectangle((1, 4, 14, 14), fill=(150, 104, 60, 255))
    d.rectangle((1, 3, 14, 4), fill=(178, 132, 82, 255))
    for y in (7, 10):
        d.line((1, y, 14, y), fill=(108, 72, 40, 255))
    for x in (1, 14):
        d.line((x, 4, x, 14), fill=(116, 78, 44, 255))
    for (x, y) in ((1, 3), (14, 3), (1, 14), (14, 14), (2, 3), (13, 3), (2, 14), (13, 14)):
        im.putpixel((x, y), (120, 124, 130, 255))
    # pochoir : silhouette de fusil jaune
    for x in range(3, 13):
        im.putpixel((x, 8), (230, 204, 92, 255))
    for p in ((10, 9), (11, 9), (12, 9), (8, 9), (6, 9), (12, 7), (3, 7)):
        im.putpixel(p, (230, 204, 92, 255))
    for p in ((4, 12), (5, 12), (6, 12), (9, 12), (10, 12), (11, 12)):
        im.putpixel(p, (222, 196, 86, 255))
    return outline(shade(im, amt=0.12), (46, 32, 18, 255))


def sp_machette():
    im = new16()
    d = ImageDraw.Draw(im)
    d.line((5, 10, 14, 1), fill=(178, 182, 188, 255), width=3)
    d.line((4, 9, 13, 0), fill=(112, 116, 122, 255), width=1)
    d.line((6, 11, 15, 2), fill=(232, 236, 240, 255), width=1)
    im.putpixel((15, 0), (0, 0, 0, 0))
    for p in ((10, 5), (11, 5), (11, 4), (9, 7)):
        im.putpixel(p, (140, 24, 24, 255))
    d.line((3, 9, 7, 13), fill=(52, 52, 56, 255), width=1)
    d.line((4, 11, 1, 14), fill=(72, 46, 30, 255), width=2)
    for p in ((3, 12), (2, 13)):
        im.putpixel(p, (108, 74, 46, 255))
    return outline(im)


def sp_griffes(blood=True):
    im = new16()
    d = ImageDraw.Draw(im)
    d.rectangle((2, 11, 13, 15), fill=(92, 128, 72, 255))
    for i, x in enumerate((3, 7, 11)):
        pts = [(x, 12), (x, 9), (x + 1, 6), (x + 2, 4), (x + 3, 2 + i % 2)]
        d.line(pts, fill=(216, 206, 172, 255), width=2)
        tip = pts[-1]
        col = (150, 22, 22, 255) if blood else (60, 56, 46, 255)
        im.putpixel(tip, col)
        im.putpixel((tip[0] - 1, tip[1] + 1), col)
    for p in ((4, 13), (8, 14), (12, 13)):
        im.putpixel(p, (66, 96, 52, 255))
    return outline(shade(im, amt=0.15))


def sp_escalade():
    im = new16()
    d = ImageDraw.Draw(im)
    d.rectangle((0, 10, 15, 15), fill=(124, 122, 118, 255))
    for x in range(16):
        im.putpixel((x, 12), (86, 84, 80, 255))
    for p in ((5, 10), (5, 11), (11, 13), (11, 14), (11, 15), (5, 15)):
        im.putpixel(p, (86, 84, 80, 255))
    d.rectangle((2, 3, 13, 6), fill=(92, 128, 72, 255))
    for x in (3, 7, 11):
        d.line((x, 6, x, 9, x + 1, 10, x + 1, 11), fill=(216, 206, 172, 255), width=1)
        d.line((x + 1, 6, x + 1, 9), fill=(196, 186, 150, 255), width=1)
    return outline(shade(im, amt=0.15))


def sp_bond():
    im = new16()
    d = ImageDraw.Draw(im)
    pts = []
    for i in range(12):
        t = i / 11.0
        x = 2 + 10 * t
        y = 13 - 11 * math.sin(t * math.pi * 0.62)
        pts.append((x, y))
    d.line(pts, fill=(150, 226, 86, 255), width=2)
    end = pts[-1]
    d.polygon([(end[0] + 3, end[1] + 2), (end[0] - 1, end[1] - 2), (end[0] - 1, end[1] + 3)], fill=(150, 226, 86, 255))
    # empreinte de patte au depart
    d.ellipse((1, 12, 5, 15), fill=(92, 128, 72, 255))
    for p in ((1, 11), (3, 10), (5, 11)):
        im.putpixel(p, (216, 206, 172, 255))
    return outline(shade(im, amt=0.15))


def sp_langue():
    im = new16()
    d = ImageDraw.Draw(im)
    d.ellipse((0, 11, 6, 16), fill=(70, 18, 22, 255))
    for p in ((1, 12), (3, 11), (5, 12)):
        im.putpixel(p, (228, 222, 196, 255))
    pts = [(3, 13), (6, 10), (9, 10), (11, 8), (12, 5), (11, 3), (9, 3), (9, 5)]
    d.line(pts, fill=(226, 112, 132, 255), width=3, joint="curve")
    d.line(pts, fill=(194, 80, 102, 255), width=1)
    im = shade(im, amt=0.2)
    return outline(im, (60, 14, 22, 255))


def sp_acide():
    im = new16()
    d = ImageDraw.Draw(im)
    d.ellipse((3, 6, 13, 15), fill=(110, 220, 60, 255))
    d.polygon([(8, 1), (4, 9), (12, 9)], fill=(110, 220, 60, 255))
    im = shade(im, amt=0.25)
    for p in ((6, 8), (6, 9), (7, 7), (6, 10)):
        im.putpixel(p, (206, 255, 156, 255))
    for p in ((12, 2), (13, 3), (2, 4)):
        im.putpixel(p, (150, 240, 100, 255))
    return outline(im, (24, 70, 20, 255))


def sp_acide_vol():
    im = new16()
    d = ImageDraw.Draw(im)
    d.ellipse((4, 4, 12, 12), fill=(104, 214, 56, 255))
    for (x, y, r) in ((11, 5, 2), (5, 11, 2), (12, 11, 1)):
        d.ellipse((x - r, y - r, x + r, y + r), fill=(104, 214, 56, 255))
    for p in ((2, 13), (1, 14), (14, 3)):
        im.putpixel(p, (130, 230, 80, 255))
    im = shade(im, amt=0.25)
    for p in ((6, 6), (7, 6), (6, 7)):
        im.putpixel(p, (214, 255, 168, 255))
    return outline(im, (24, 70, 20, 255))


SPRITES = {
    "grenade": (sp_grenade, "generated"),
    "caisse_armes": (sp_caisse, "generated"),
    "machette": (sp_machette, "handheld"),
    "griffes": (lambda: sp_griffes(True), "handheld"),
    "bond": (sp_bond, "generated"),
    "langue": (sp_langue, "generated"),
    "acide": (sp_acide, "generated"),
    "escalade": (sp_escalade, "generated"),
    "acide_vol": (sp_acide_vol, "generated"),
}


# ============================================================ equipement (64x32, format armure humanoide)

SKIN = [(96, 132, 76), (88, 124, 70), (104, 140, 82), (82, 116, 66)]
ROT = (112, 118, 70)
WOUND = (118, 22, 22)
WOUND2 = (82, 16, 16)
BONE = (214, 208, 182)


def skin_c(rnd):
    if rnd.random() < 0.08:
        return jitter(ROT, rnd, 6)
    return jitter(rnd.choice(SKIN), rnd, 4)


def fill(img, x0, y0, w, h, colfn):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c = colfn(x - x0, y - y0)
            if c is not None:
                img.putpixel((x, y), tuple(c[:3]) + (255,))


def zombie_layer():
    rnd = random.Random(404)
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # ---- tete (casque) : dessus, dessous, droite, face, gauche, arriere
    hair = (44, 36, 26)

    def head_side(x, y):
        if y == 0 and rnd.random() < 0.7:
            return jitter(hair, rnd, 5)
        return skin_c(rnd)
    fill(im, 8, 0, 8, 8, lambda x, y: jitter(hair, rnd, 6) if rnd.random() < 0.35 else skin_c(rnd))  # dessus
    fill(im, 16, 0, 8, 8, lambda x, y: tuple(int(v * 0.8) for v in skin_c(rnd)))                     # dessous
    fill(im, 0, 8, 8, 8, head_side)
    fill(im, 16, 8, 8, 8, head_side)
    fill(im, 24, 8, 8, 8, lambda x, y: jitter(hair, rnd, 6) if y < 3 and rnd.random() < 0.6 else skin_c(rnd))
    # oreilles et entailles
    for (x, y) in ((4, 12), (4, 13), (19, 12), (19, 13)):
        im.putpixel((x, y), (62, 92, 50, 255))
    for (x, y) in ((1, 10), (2, 11), (3, 12), (27, 10), (28, 11), (12, 2), (13, 3)):
        im.putpixel((x, y), WOUND + (255,))
    # visage (8..15, 8..15)
    face = [
        "hhsHhshh",
        "sssssWWs",
        "sDDsssDD",
        "sXEsssEX",
        "sddsssdd",
        "sssNNsss",
        "sMTMTMMs",
        "sssBsBss",
    ]
    pal = {
        "h": hair, "H": (60, 48, 34), "W": WOUND, "D": (64, 92, 50), "X": (20, 24, 16), "E": (206, 236, 112),
        "d": (56, 82, 44), "N": (40, 50, 30), "M": (36, 16, 16), "T": BONE, "B": (132, 24, 24),
    }
    for y, row in enumerate(face):
        for x, ch in enumerate(row):
            c = skin_c(rnd) if ch == "s" else pal[ch]
            im.putpixel((8 + x, 8 + y), tuple(c) + (255,))
    # ---- corps (chemise dechiree) : 16..40 x 16..32
    shirt = (172, 160, 128)

    def shirt_c(dark=1.0):
        c = jitter(shirt, rnd, 8)
        if rnd.random() < 0.10:
            c = mix(c, (110, 96, 70), 0.6)
        return tuple(int(v * dark) for v in c)
    fill(im, 20, 16, 8, 4, lambda x, y: shirt_c())          # dessus (epaules)
    fill(im, 28, 16, 8, 4, lambda x, y: shirt_c(0.75))      # dessous
    fill(im, 16, 20, 4, 12, lambda x, y: shirt_c(0.92))     # cote droit
    fill(im, 28, 20, 4, 12, lambda x, y: shirt_c(0.92))     # cote gauche

    def front(x, y):
        if y == 0:
            return shirt_c(0.8)
        # dechirure au flanc : peau + cotes
        if 3 <= y <= 9 and 1 <= x <= 4 and not (y in (3, 9) and x in (1, 4)):
            if y in (5, 7) and x in (2, 3):
                return BONE
            return skin_c(rnd)
        if (y >= 8 and 5 <= x <= 7 and rnd.random() < 0.7) or (y == 11 and x % 3 == 0):
            return jitter(WOUND, rnd, 10)
        if y == 11 and x % 2 == 1:
            return skin_c(rnd)
        return shirt_c()
    fill(im, 20, 20, 8, 12, front)

    def back(x, y):
        if 6 <= y <= 9 and 4 <= x <= 6:
            return skin_c(rnd)
        if 2 <= y <= 4 and 1 <= x <= 2:
            return jitter(WOUND2, rnd, 8)
        if y == 11 and x % 2 == 0:
            return skin_c(rnd)
        return shirt_c(0.95)
    fill(im, 32, 20, 8, 12, back)
    # ---- bras : 40..56 x 16..32 (manches arrachees)
    fill(im, 44, 16, 4, 4, lambda x, y: shirt_c())
    fill(im, 48, 16, 4, 4, lambda x, y: tuple(int(v * 0.75) for v in skin_c(rnd)))

    def arm(x, y):
        if y < 3 or (y == 3 and (x + rnd.randint(0, 1)) % 2 == 0):
            return shirt_c(0.95)
        if y == 11:
            return tuple(int(v * 0.8) for v in skin_c(rnd))
        if rnd.random() < 0.06:
            return jitter(WOUND, rnd, 10)
        return skin_c(rnd)
    for x0 in (40, 44, 48, 52):
        fill(im, x0, 20, 4, 12, arm)
    for (x, y) in ((45, 26), (45, 27), (46, 27), (53, 24), (41, 29)):
        im.putpixel((x, y), WOUND + (255,))
    # ---- jambes (bottes) : seulement le bas (chaussures abimees), le reste transparent pour le pantalon
    leather = (70, 50, 34)

    def shoe(x, y):
        if y < 8:
            return None
        if y == 11:
            return jitter((40, 30, 22), rnd, 4)
        return jitter(leather, rnd, 6)
    for x0 in (0, 4, 8, 12):
        fill(im, x0, 20, 4, 12, shoe)
    im.putpixel((5, 29), tuple(skin_c(rnd)) + (255,))   # orteil qui depasse
    im.putpixel((6, 30), tuple(skin_c(rnd)) + (255,))
    fill(im, 8, 16, 4, 4, lambda x, y: jitter((36, 28, 20), rnd, 4))  # semelle
    return im


def zombie_leggings():
    rnd = random.Random(405)
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    jean = (54, 70, 100)

    def jc(dark=1.0):
        c = jitter(jean, rnd, 6)
        if rnd.random() < 0.08:
            c = mix(c, (86, 80, 66), 0.6)
        return tuple(int(v * dark) for v in c)

    def leg(face):
        def f(x, y):
            if face == "front" and ((3 <= y <= 5 and 1 <= x <= 2) or (y == 6 and x == 2)):
                return skin_c(rnd)
            if face == "front" and y == 7:
                return mix(jc(), (120, 130, 150), 0.4)
            if face == "right" and 8 <= y <= 9 and x in (1, 2):
                return skin_c(rnd)
            if rnd.random() < 0.04:
                return jitter(WOUND2, rnd, 8)
            return jc()
        return f
    fill(im, 4, 16, 4, 4, lambda x, y: jc())
    fill(im, 8, 16, 4, 4, lambda x, y: jc(0.7))
    fill(im, 0, 20, 4, 12, leg("right"))
    fill(im, 4, 20, 4, 12, leg("front"))
    fill(im, 8, 20, 4, 12, leg("left"))
    fill(im, 12, 20, 4, 12, leg("back"))
    # taille du pantalon + ceinture (bas du torse)
    belt = (58, 40, 26)
    for x0, w in ((16, 4), (20, 8), (28, 4), (32, 8)):
        def waist(x, y, x0=x0):
            if y < 9:
                return None
            if y == 9:
                if x0 == 20 and x in (3, 4):
                    return (150, 152, 156)
                return jitter(belt, rnd, 4)
            return jc()
        fill(im, x0, 20, w, 12, waist)
    fill(im, 28, 16, 8, 4, lambda x, y: jc(0.8))
    return im


def survivor_vest():
    rnd = random.Random(406)
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    olive = (88, 98, 62)
    strap = (40, 42, 30)

    def oc(dark=1.0):
        return tuple(int(v * dark) for v in jitter(olive, rnd, 5))
    fill(im, 20, 16, 8, 4, lambda x, y: jitter(strap, rnd, 3) if x in (1, 2, 5, 6) else oc(1.08))
    fill(im, 28, 16, 8, 4, lambda x, y: oc(0.7))

    def front(x, y):
        if y <= 1:
            return oc(0.72)
        if x in (3, 4) and y <= 5:
            return jitter((44, 46, 34), rnd, 3)
        if 2 <= y <= 4 and 5 <= x <= 7:      # ecusson blanc a croix rouge
            if (x == 6) or (y == 3):
                return (196, 34, 30)
            return (226, 224, 214)
        if y in (3, 4) and x <= 2:
            return oc(0.8) if x % 2 == 0 else oc(1.05)
        if 6 <= y <= 10 and (x <= 2 or x >= 5):
            if y == 6:
                return oc(1.25)
            return oc(0.82)
        if y == 11:
            return oc(0.7)
        return oc()
    fill(im, 20, 20, 8, 12, front)

    def side(x, y):
        if y in (4, 8):
            return jitter(strap, rnd, 3)
        if y in (5, 9) and x in (1, 2):
            return (120, 122, 126)
        return oc(0.9)
    fill(im, 16, 20, 4, 12, side)
    fill(im, 28, 20, 4, 12, side)

    def back(x, y):
        if y <= 1:
            return oc(0.72)
        if y % 3 == 0 and x % 2 == 0:
            return oc(0.78)
        return oc(0.95)
    fill(im, 32, 20, 8, 12, back)
    # brassard orange des survivants sur les deux bras (reste des bras transparent)
    for x0 in (40, 44, 48, 52):
        fill(im, x0, 20, 4, 12, lambda x, y: (jitter((226, 124, 34), rnd, 6) if y in (1, 2) else None))
    return im


ZOMBIE = zombie_layer()
ZOMBIE_LEG = zombie_leggings()
VEST = survivor_vest()
save_png(ZOMBIE, os.path.join(TEX_EQ, "humanoid", "infection_zombie.png"))
save_png(ZOMBIE_LEG, os.path.join(TEX_EQ, "humanoid_leggings", "infection_zombie.png"))
save_png(VEST, os.path.join(TEX_EQ, "humanoid", "infection_survivant.png"))
write_json(os.path.join(EQUIP, "infection_zombie.json"), {"layers": {
    "humanoid": [{"texture": NS + ":infection_zombie"}],
    "humanoid_leggings": [{"texture": NS + ":infection_zombie"}],
}})
write_json(os.path.join(EQUIP, "infection_survivant.json"), {"layers": {
    "humanoid": [{"texture": NS + ":infection_survivant"}],
}})


# icones de la tenue tirees des textures d'equipement (meme dessin que sur le joueur)
def icon_from(parts, w, h):
    im = new16()
    tmp = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for src, box, at in parts:
        tmp.alpha_composite(src.crop(box), at)
    scale = 2 if w <= 8 and h <= 8 else 1
    tmp = tmp.resize((w * scale, h * scale), Image.NEAREST)
    im.alpha_composite(tmp, ((16 - tmp.width) // 2, (16 - tmp.height) // 2))
    return outline(im, (20, 22, 16, 255))


def mirror(src, box):
    return src.crop(box).transpose(Image.FLIP_LEFT_RIGHT)


def icon_haut(src):
    im = Image.new("RGBA", (16, 12), (0, 0, 0, 0))
    im.alpha_composite(src.crop((44, 20, 48, 32)), (0, 0))
    im.alpha_composite(src.crop((20, 20, 28, 32)), (4, 0))
    im.alpha_composite(mirror(src, (44, 20, 48, 32)), (12, 0))
    out = new16()
    out.alpha_composite(im, (0, 2))
    return outline(out, (20, 22, 16, 255)) if src is not VEST else out


def icon_bas():
    im = Image.new("RGBA", (9, 12), (0, 0, 0, 0))
    im.alpha_composite(ZOMBIE_LEG.crop((4, 20, 8, 32)), (0, 0))
    im.alpha_composite(mirror(ZOMBIE_LEG, (4, 20, 8, 32)), (5, 0))
    belt = ZOMBIE_LEG.crop((20, 29, 28, 30)).resize((9, 1), Image.NEAREST)
    im.alpha_composite(belt, (0, 0))
    out = new16()
    out.alpha_composite(im, (4, 2))
    return outline(out, (20, 22, 16, 255))


def icon_pieds():
    im = Image.new("RGBA", (9, 4), (0, 0, 0, 0))
    im.alpha_composite(ZOMBIE.crop((4, 28, 8, 32)), (0, 0))
    im.alpha_composite(mirror(ZOMBIE, (4, 28, 8, 32)), (5, 0))
    im = im.resize((18, 8), Image.NEAREST).crop((1, 0, 17, 8))
    out = new16()
    out.alpha_composite(im, (0, 5))
    return outline(out, (20, 22, 16, 255))


def icon_gilet():
    im = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    im.alpha_composite(VEST.crop((16, 20, 20, 32)).crop((2, 0, 4, 12)), (0, 0))
    im.alpha_composite(VEST.crop((20, 20, 28, 32)), (2, 0))
    im.alpha_composite(VEST.crop((28, 20, 32, 32)).crop((0, 0, 2, 12)), (10, 0))
    out = new16()
    out.alpha_composite(im, (2, 2))
    return outline(out, (20, 22, 16, 255))


SPRITES.update({
    "zombie_tete": (lambda: icon_from([(ZOMBIE, (8, 8, 16, 16), (0, 0))], 8, 8), "generated"),
    "zombie_haut": (lambda: icon_haut(ZOMBIE), "generated"),
    "zombie_bas": (icon_bas, "generated"),
    "zombie_pieds": (icon_pieds, "generated"),
    "gilet": (icon_gilet, "generated"),
})

SPRITE_IMG = {}
for name, (fn, parent) in SPRITES.items():
    img = fn()
    assert img.size == (16, 16), name
    SPRITE_IMG[name] = img
    save_png(img, os.path.join(TEX_ITEM, name + ".png"))
    write_json(os.path.join(MODELS, name + ".json"), {
        "parent": "minecraft:item/" + parent,
        "textures": {"layer0": NS + ":item/infection/" + name},
    })

# ============================================================ definitions d'objets (items/)

ALL_MODELS = list(GUN_MODELS) + list(SPRITES)
for name in ALL_MODELS:
    write_json(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":item/infection/" + name}})


# ============================================================ apercus

def render_side(mdl, S=12):
    """Vue de cote (depuis +X, canon a droite) : faces est, ordre du plus loin au plus proche."""
    zs = [e["from"][2] for e in mdl.el] + [e["to"][2] for e in mdl.el]
    ys = [e["from"][1] for e in mdl.el] + [e["to"][1] for e in mdl.el]
    zmax, zmin = max(zs) + 3, min(zs) - 3
    ymax, ymin = max(ys) + 3, min(ys) - 3
    W, H = int((zmax - zmin) * S), int((ymax - ymin) * S)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for e in sorted(mdl.el, key=lambda e: e["to"][0]):
        f, t = e["from"], e["to"]
        face = e["faces"]["east"]
        mat = face["texture"][1:]
        u0, v0, u1, v1 = face["uv"]
        tex = MAT_IMG[mat].resize((256, 256), Image.NEAREST)
        crop = tex.crop((int(u0 * 16), int(v0 * 16), max(int(u0 * 16) + 1, int(u1 * 16)), max(int(v0 * 16) + 1, int(v1 * 16))))
        w = max(1, int(round((t[2] - f[2]) * S)))
        h = max(1, int(round((t[1] - f[1]) * S)))
        crop = crop.resize((w, h), Image.NEAREST)
        # assombrit legerement les elements plus loin (x faible)
        cz, cy = (f[2] + t[2]) / 2, (f[1] + t[1]) / 2
        if "rotation" in e:
            ang = e["rotation"]["angle"]
            oy, oz = e["rotation"]["origin"][1], e["rotation"]["origin"][2]
            a = math.radians(ang)
            dy, dz = cy - oy, cz - oz
            cy, cz = oy + dy * math.cos(a) - dz * math.sin(a), oz + dy * math.sin(a) + dz * math.cos(a)
            crop = crop.rotate(ang, resample=Image.NEAREST, expand=True)
        sx, sy = (zmax - cz) * S, (ymax - cy) * S
        img.alpha_composite(crop, (int(sx - crop.width / 2), int(sy - crop.height / 2)))
    return img


def checker(w, h, c1=(58, 62, 70), c2=(48, 52, 60), step=12):
    bg = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(bg)
    for y in range(0, h, step):
        for x in range(0, w, step):
            d.rectangle((x, y, x + step - 1, y + step - 1), fill=(c1 if (x // step + y // step) % 2 == 0 else c2) + (255,))
    return bg


def player_front(layers, back=False):
    """Vue de face (ou de dos) 16x32 : peau grise neutre + couches d'equipement par-dessus."""
    base = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(base)
    d.rectangle((4, 0, 11, 7), fill=(170, 170, 170, 255))
    d.rectangle((4, 8, 11, 19), fill=(150, 150, 150, 255))
    d.rectangle((0, 8, 3, 19), fill=(160, 160, 160, 255))
    d.rectangle((12, 8, 15, 19), fill=(160, 160, 160, 255))
    d.rectangle((4, 20, 11, 31), fill=(140, 140, 140, 255))
    hx, bx, ax, lx = (24, 32, 52, 12) if back else (8, 20, 44, 4)
    for lay in layers:
        im = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
        im.alpha_composite(lay.crop((hx, 8, hx + 8, 16)), (4, 0))
        im.alpha_composite(lay.crop((bx, 20, bx + 8, 32)), (4, 8))
        im.alpha_composite(lay.crop((ax, 20, ax + 4, 32)), (0, 8))
        im.alpha_composite(lay.crop((ax, 20, ax + 4, 32)).transpose(Image.FLIP_LEFT_RIGHT), (12, 8))
        im.alpha_composite(lay.crop((lx, 20, lx + 4, 32)), (4, 20))
        im.alpha_composite(lay.crop((lx, 20, lx + 4, 32)).transpose(Image.FLIP_LEFT_RIGHT), (8, 20))
        base.alpha_composite(im)
    return base


# planche des armes
sides = {n: render_side(m) for n, m in GUN_MODELS.items()}
W = max(i.width for i in sides.values()) + 40
H = sum(i.height + 30 for i in sides.values()) + 20
board = checker(W, H)
y = 20
for n, im in sides.items():
    board.alpha_composite(im, (20, y))
    ImageDraw.Draw(board).text((6, y - 14), n, fill=(230, 230, 230, 255))
    y += im.height + 30
board.save(os.path.join(APERCU, "armes_cote.png"))

# planche des sprites (x8)
names = list(SPRITE_IMG)
cols = 7
rows = (len(names) + cols - 1) // cols
sb = checker(cols * 150, rows * 160, step=16)
for i, n in enumerate(names):
    big = SPRITE_IMG[n].resize((128, 128), Image.NEAREST)
    x, y = (i % cols) * 150 + 11, (i // cols) * 160 + 6
    sb.alpha_composite(big, (x, y))
    ImageDraw.Draw(sb).text((x, y + 132), n, fill=(235, 235, 235, 255))
sb.save(os.path.join(APERCU, "sprites.png"))

# matieres des armes (x8)
mb = checker(len(MAT_IMG) * 140, 160, step=16)
for i, (n, im) in enumerate(MAT_IMG.items()):
    mb.alpha_composite(im.resize((128, 128), Image.NEAREST), (i * 140 + 6, 6))
    ImageDraw.Draw(mb).text((i * 140 + 6, 140), n, fill=(235, 235, 235, 255))
mb.save(os.path.join(APERCU, "matieres.png"))

# tenues : textures brutes (x8) + rendu face/dos (x10)
tb = checker(64 * 8 * 3 + 40, 32 * 8 + 30 + 340, step=16)
for i, (n, im) in enumerate((("zombie humanoid", ZOMBIE), ("zombie humanoid_leggings", ZOMBIE_LEG), ("survivant humanoid", VEST))):
    tb.alpha_composite(im.resize((512, 256), Image.NEAREST), (10 + i * 522, 22))
    ImageDraw.Draw(tb).text((10 + i * 522, 6), n, fill=(235, 235, 235, 255))
for i, (lab, layers, back) in enumerate((("zombie face", [ZOMBIE_LEG, ZOMBIE], False), ("zombie dos", [ZOMBIE_LEG, ZOMBIE], True),
                                         ("survivant face", [VEST], False), ("survivant dos", [VEST], True))):
    pf = player_front(layers, back).resize((160, 320), Image.NEAREST)
    x = 30 + i * 220
    tb.alpha_composite(pf, (x, 32 * 8 + 40))
    ImageDraw.Draw(tb).text((x, 32 * 8 + 28), lab, fill=(235, 235, 235, 255))
tb.save(os.path.join(APERCU, "tenues.png"))

print("%d fichiers ecrits dans %s" % (len(WRITTEN), PACK))
print("modeles :", ", ".join(ALL_MODELS))
print("apercus :", APERCU)
