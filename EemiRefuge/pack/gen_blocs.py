"""Retextures de blocs VANILLA du pack Dernier Refuge (appelé par gen_refuge.py).

Le pack Refuge n'est chargé que dans les mondes du mode (retiré en sortant) : il peut donc remplacer des
fichiers `assets/minecraft/...` sans toucher au reste du serveur.

Deux façons de faire, choisies bloc par bloc (table complète dans BLOCS.md) :
- TEXTURE remplacée au chemin vanilla exact (`assets/minecraft/textures/...`) quand la texture n'appartient
  qu'à ce bloc (étagères, tonneau, coffres, pot, bibliothèque ciselée, vault, blocs suspects, lanternes) ou
  quand on VEUT que toute la famille suive (planches, briques, vitres, herbe, feuilles : escaliers, dalles,
  barrières et panneaux prennent la même usure). Un seul fichier couvre le bloc, tous ses états, et l'objet.
- MODÈLE vanilla remplacé (`assets/minecraft/models/block/<x>.json`) qui pointe vers une texture dessinée
  `refuge:block/<x>` pour les « blocs de jeu » (chêne pâle = barricade, magnétite = radio, cible = balise,
  bloc de redstone = générateur) : la texture vanilla reste intacte (escaliers en chêne pâle inchangés).

Aucune forme (élément de modèle) n'est changée : les objets posés par le jeu (étagères, vault, bibliothèque)
restent exactement où le vanilla les dessine. Les textures d'entité (coffres, pot) gardent la taille et la
mise en page UV d'origine : on ne fait que recolorer / redessiner dans le masque alpha d'origine.

Tout est dessiné à partir de la texture vanilla 26.3 lue dans le jar client (palette et ombrages vanilla),
avec un hasard à graine fixe (pack reproductible).
"""
import io
import json
import math
import os
import random
import zipfile

from PIL import Image, ImageDraw

NS = "refuge"
WOODS = ["acacia", "bamboo", "birch", "cherry", "crimson", "dark_oak", "jungle", "mangrove", "oak", "pale_oak",
         "poplar", "spruce", "warped"]
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]


def find_vanilla():
    for cand in [os.path.expandvars(r"%APPDATA%\.minecraft\versions\26.3\26.3.jar"),
                 os.path.expanduser("~/.minecraft/versions/26.3/26.3.jar")]:
        if os.path.isfile(cand):
            return cand
    raise SystemExit("ERREUR : jar client 26.3 introuvable (textures vanilla de base)")


class Jar:
    def __init__(self, path):
        self.z = zipfile.ZipFile(path)
        self.names = set(self.z.namelist())

    def tex(self, ref):
        """'block/oak_planks' ou 'entity/chest/normal' -> Image RGBA (copie)."""
        return Image.open(io.BytesIO(self.z.read(f"assets/minecraft/textures/{ref}.png"))).convert("RGBA")

    def has(self, path):
        return path in self.names


# ------------------------------------------------------------------ outils de couleur

def hx(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3)) + (a[3] if len(a) > 3 else 255,)


def mul(c, f):
    return tuple(max(0, min(255, int(round(c[i] * f)))) for i in range(3)) + (c[3],)


def opaque(c):
    return c[3] > 0


def ramp(img, colors, box=None, lo=None, hi=None, mask=None):
    """Recolore chaque pixel opaque selon sa luminance (sombre -> clair) dans la rampe `colors` (hex)."""
    cols = [hx(c) if isinstance(c, str) else c for c in colors]
    x0, y0, x1, y1 = box or (0, 0, img.width, img.height)
    px = img.load()
    ls = [lum(px[x, y]) for x in range(x0, x1) for y in range(y0, y1) if opaque(px[x, y])]
    if not ls:
        return img
    lo = min(ls) if lo is None else lo
    hi = max(ls) if hi is None else hi
    span = max(1.0, hi - lo)
    for x in range(x0, x1):
        for y in range(y0, y1):
            c = px[x, y]
            if not opaque(c) or (mask is not None and not mask(x, y)):
                continue
            t = max(0.0, min(1.0, (lum(c) - lo) / span))
            k = min(len(cols) - 1, int(t * (len(cols) - 1) + 0.5))
            px[x, y] = cols[k][:3] + (c[3],)
    return img


def desat(img, amount, dark=1.0, tint=None, box=None):
    """Désature (0..1), assombrit et teinte légèrement (vers `tint`)."""
    px = img.load()
    x0, y0, x1, y1 = box or (0, 0, img.width, img.height)
    for x in range(x0, x1):
        for y in range(y0, y1):
            c = px[x, y]
            if not opaque(c):
                continue
            l = lum(c)
            n = tuple(c[i] * (1 - amount) + l * amount for i in range(3))
            if tint:
                n = tuple(n[i] * 0.85 + tint[i] * 0.15 * l / 255 * 1.6 for i in range(3))
            px[x, y] = tuple(max(0, min(255, int(n[i] * dark))) for i in range(3)) + (c[3],)
    return img


def grime(img, rnd, n, f=0.8, box=None, color=None, alpha=None):
    """Taches : n pixels (et parfois leur voisin) assombris ou teintés."""
    px = img.load()
    x0, y0, x1, y1 = box or (0, 0, img.width, img.height)
    for _ in range(n):
        x, y = rnd.randrange(x0, x1), rnd.randrange(y0, y1)
        for dx, dy in [(0, 0)] + ([(rnd.choice([-1, 0, 1]), rnd.choice([-1, 0, 1]))] if rnd.random() < 0.5 else []):
            xx, yy = x + dx, y + dy
            if x0 <= xx < x1 and y0 <= yy < y1 and opaque(px[xx, yy]):
                c = px[xx, yy]
                if alpha is not None and c[3] < 255:
                    c = c[:3] + (min(255, c[3] + alpha),)
                px[xx, yy] = mix(c, color, 0.45)[:3] + (c[3],) if color else mul(c, f)
    return img


def streaks(img, rnd, n, f=0.82, box=None, length=(3, 8)):
    """Coulures verticales sombres partant du haut d'une zone."""
    px = img.load()
    x0, y0, x1, y1 = box or (0, 0, img.width, img.height)
    for _ in range(n):
        x = rnd.randrange(x0, x1)
        y = y0 + rnd.randrange(0, 3)
        for k in range(rnd.randint(*length)):
            if y + k >= y1:
                break
            if opaque(px[x, y + k]):
                px[x, y + k] = mul(px[x, y + k], f + (1 - f) * k / 10)
    return img


def crack(img, rnd, start, steps, color, box=None, alpha=None):
    """Fissure : marche aléatoire de `steps` pixels."""
    px = img.load()
    x0, y0, x1, y1 = box or (0, 0, img.width, img.height)
    x, y = start
    dx, dy = rnd.choice([(1, 1), (1, 0), (0, 1), (-1, 1), (1, -1)])
    for _ in range(steps):
        if x0 <= x < x1 and y0 <= y < y1:
            c = px[x, y]
            a = c[3] if alpha is None else alpha
            if c[3] > 0 or alpha is not None:
                px[x, y] = color[:3] + (a,)
        if rnd.random() < 0.35:
            dx, dy = rnd.choice([(1, 1), (1, 0), (0, 1), (-1, 1), (1, -1), (0, -1), (-1, 0)])
        x, y = x + dx, y + dy
    return img


def rect(img, box, c):
    d = ImageDraw.Draw(img)
    d.rectangle(box, fill=hx(c) if isinstance(c, str) else c)


def put(img, pts, c):
    px = img.load()
    cc = hx(c) if isinstance(c, str) else c
    for x, y in pts:
        if 0 <= x < img.width and 0 <= y < img.height:
            px[x, y] = cc


def png(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()


def keep_alpha(new, ref):
    """Recopie le masque alpha de `ref` (même taille) : le rendu d'entité garde exactement ses zones."""
    a = ref.getchannel("A")
    out = new.copy()
    out.putalpha(a)
    return out


# ------------------------------------------------------------------ Groupe 1 : étagères

# bois -> (type de pièce, rampe du meuble (sombre -> clair), rampe du fond, décor du fond, décor des planches)
SHELVES = {
    "oak": ("Cuisine", ["2e2618", "6b5a40", "b9a77e", "ddd0a8", "efe6c6"], ["5a6670", "93a6b0", "c9d6dc", "e2eaee"],
            "carreaux", "ecaille"),
    "spruce": ("Atelier", ["1f160d", "3d2b19", "5b4127", "74553a", "8a6a4a"], ["3a2a1a", "6e5232", "8e6c44", "a78456"],
               "perfore", "use"),
    "birch": ("Pharmacie", ["3a4040", "8a9494", "c8d0cf", "e4eae8", "f6f8f6"], ["7d8a86", "b7c4bf", "dde6e2", "f0f5f2"],
              "croix", "etiquettes_vertes"),
    "jungle": ("Épicerie", ["2a0e0a", "5c1d15", "8e2c1e", "b04532", "c8664a"], ["3c3c3c", "6c6c6c", "8c8c8c", "a4a4a4"],
               "perfore", "prix"),
    "acacia": ("Quincaillerie", ["131c2a", "263a56", "3b5a80", "52759c", "6f8fb0"], ["333638", "5f6467", "838a8d", "9ca3a6"],
               "perfore", "prix_orange"),
    "dark_oak": ("Bureau", ["120c08", "2a1c12", "41301f", "56402b", "6a5139"], ["3d3426", "6c5c43", "857252", "9b8762"],
                 "papier_peint", "laiton"),
    "mangrove": ("Chambre", ["2a0f0c", "5a221a", "7e3324", "9a4630", "ad5c43"], ["4a3a36", "7d6660", "a08883", "b8a29c"],
                 "fleurs", "ecaille"),
    "cherry": ("Café", ["3c2a2c", "7d5e60", "b3949a", "d2b8bc", "e6d4d6"], ["3a3030", "6e6262", "c9c0b8", "ece6dc"],
               "damier", "ecaille"),
    "bamboo": ("Garage", ["1c1e20", "3e4246", "6a7076", "8e959b", "aeb5ba"], ["262626", "3e3e3e", "585858", "6e6e6e"],
               "grillage", "huile"),
    "crimson": ("Armurerie", ["15190d", "2c3519", "434f27", "5a6634", "6f7a45"], ["1d2214", "343c22", "4a5432", "5d6840"],
                "kaki", "pochoir"),
    "warped": ("Hôpital", ["0e2224", "1f4a4c", "347174", "4f9295", "73b0b0"], ["5e7e80", "9ab8b8", "c6dcdc", "deeeee"],
               "carreaux", "etiquettes_blanches"),
    "pale_oak": ("Entrepôt", ["2a1a08", "6a3e0c", "b0640e", "d8841a", "eca33a"], ["1a2a44", "2c4670", "3d5f94", "5379ae"],
                 "montants", "danger"),
    "poplar": ("Grange", ["262420", "4c4840", "6e695e", "8a857a", "a29d92"], ["2c2620", "4e443a", "6a5e50", "817465"],
               "planches", "corde"),
}

# Zones de la texture 32x32 des étagères (lues dans models/block/template_shelf_*.json, uv x2) :
FRONT_TOP = (0, 0, 16, 4)        # face avant de la planche du haut
FRONT_BOTTOM = (0, 12, 16, 16)   # face avant de la planche du bas (les objets sont posés derrière, dessus)
BACKS = [(0, 4, 16, 12), (0, 16, 16, 24), (16, 16, 32, 24), (0, 24, 16, 32), (16, 24, 32, 32)]  # fonds (unpowered, left, right, center, unconnected)
BACK_FACE = (16, 0, 32, 16)      # dos du meuble (contre le mur), dessus/dessous des planches en y 7..12


def _back_pattern(img, box, kind, back, rnd):
    x0, y0, x1, y1 = box
    px = img.load()
    cols = [hx(c) for c in back]
    for x in range(x0, x1):
        for y in range(y0, y1):
            if not opaque(px[x, y]):
                continue
            lx, ly = x - x0, y - y0
            c = cols[2]
            if kind == "carreaux":
                c = cols[1] if (lx % 4 == 0 or ly % 4 == 0) else cols[3 if (lx + ly) % 7 else 2]
            elif kind == "croix":
                c = cols[1] if (lx % 4 == 0 or ly % 4 == 0) else cols[3]
            elif kind == "perfore":
                c = cols[0] if (lx % 2 == 1 and ly % 2 == 1) else cols[2 if (lx + ly) % 5 else 3]
            elif kind == "papier_peint":
                c = cols[1] if lx % 4 in (0, 1) else cols[2]
                if lx % 4 == 2 and ly % 3 == 1:
                    c = cols[3]
            elif kind == "fleurs":
                c = cols[2]
                if (lx % 4, ly % 4) in ((1, 1), (3, 3)):
                    c = cols[0]
                elif (lx % 4, ly % 4) in ((2, 1), (1, 2), (0, 3)):
                    c = cols[1]
            elif kind == "damier":
                c = cols[3] if ((lx // 2) + (ly // 2)) % 2 else cols[1]
            elif kind == "grillage":
                c = cols[3] if (lx % 3 == 0 or ly % 3 == 0) else cols[0]
            elif kind == "kaki":
                c = cols[1] if ly in (0, y1 - y0 - 1) else cols[2 if (lx * 7 + ly * 3) % 9 else 1]
            elif kind == "montants":
                c = cols[2] if lx in (0, 1, x1 - x0 - 2, x1 - x0 - 1) else hx("2a2a2e")
                if lx in (0, x1 - x0 - 2):
                    c = cols[3]
                if c == hx("2a2a2e") and (lx + ly * 2) % 6 == 0:
                    c = hx("35353a")
            elif kind == "planches":
                c = cols[1] if ly % 4 == 0 else cols[2 if (lx * 5 + ly) % 7 else 3]
            px[x, y] = c[:3] + (px[x, y][3],)
    # ombre portée par la planche du haut sur le fond
    for x in range(x0, x1):
        if opaque(px[x, y0]):
            px[x, y0] = mul(px[x, y0], 0.62)
        if y0 + 1 < y1 and opaque(px[x, y0 + 1]):
            px[x, y0 + 1] = mul(px[x, y0 + 1], 0.8)
    if kind == "croix" and box == BACKS[0]:
        g = hx("2f9e4a")
        cx, cy = x0 + 7, y0 + 3
        put(img, [(cx, cy - 1), (cx - 1, cy), (cx, cy), (cx + 1, cy), (cx, cy + 1), (cx + 1, cy - 1), (cx + 1, cy + 1),
                  (cx + 2, cy)], g)
    grime(img, rnd, 3, 0.85, box=box)


def _front(img, box, kind, rnd, furniture):
    x0, y0, x1, y1 = box
    cols = [hx(c) for c in furniture]
    if kind == "ecaille":   # peinture écaillée : le bois apparaît par plaques
        wood = [hx("5b4127"), hx("7a5a38")]
        for _ in range(4):
            x, y = rnd.randrange(x0 + 1, x1 - 2), rnd.randrange(y0 + 1, y1 - 1)
            put(img, [(x, y), (x + 1, y)], wood[rnd.randrange(2)])
    elif kind == "use":
        for x in range(x0 + 1, x1 - 1, 5):
            put(img, [(x, y0 + 1)], cols[4])
    elif kind in ("prix", "prix_orange", "etiquettes_vertes", "etiquettes_blanches"):
        if kind == "etiquettes_vertes":
            rect(img, (x0, y0 + 1, x1 - 1, y1 - 2), "2f7d3e")
        lab = {"prix": "f2efe2", "prix_orange": "f0a030", "etiquettes_vertes": "eef4ee", "etiquettes_blanches": "f4f8f8"}[kind]
        for lx in (x0 + 2, x0 + 10):
            rect(img, (lx, y0 + 1, lx + 3, y0 + 2), lab)
            put(img, [(lx + 1, y0 + 1)], "3a3a3a")
            if kind == "prix":
                put(img, [(lx + 3, y0 + 2)], "c02020")
    elif kind == "laiton":
        for lx in (x0 + 3, x0 + 10):
            rect(img, (lx, y0 + 1, lx + 2, y0 + 2), "c8a040")
            put(img, [(lx + 1, y0 + 1)], "f2eccb")
    elif kind == "huile":
        for _ in range(3):
            x = rnd.randrange(x0, x1)
            put(img, [(x, y0 + rnd.randrange(0, 3))], "15161a")
    elif kind == "pochoir":
        # numéros au pochoir blanc (deux barres et un point)
        for lx, pat in ((x0 + 2, [(0, 1), (0, 2), (2, 1), (2, 2), (1, 1)]), (x0 + 11, [(0, 1), (1, 1), (1, 2), (2, 2)])):
            put(img, [(lx + a, y0 + b) for a, b in pat], "d8d8c8")
    elif kind == "danger":   # bandes jaune et noir sur la poutre
        for x in range(x0, x1):
            for y in range(y0, y1):
                if opaque(img.getpixel((x, y))):
                    img.putpixel((x, y), hx("f2c21b") if ((x + y) // 2) % 2 else hx("26221c"))
    elif kind == "corde":
        for x in range(x0, x1, 3):
            put(img, [(x, y0 + 1), (x + 1, y0 + 2)], "b89a62")


def shelf(jar, wood, rnd):
    room, furn, back, bk, fk = SHELVES[wood]
    van = jar.tex(f"block/{wood}_shelf")
    img = ramp(van.copy(), furn)
    for b in BACKS:
        _back_pattern(img, b, bk, back, rnd)
    for b in (FRONT_TOP, FRONT_BOTTOM):
        _front(img, b, fk, rnd, furn)
    grime(img, rnd, 10, 0.86, box=BACK_FACE)
    streaks(img, rnd, 3, box=FRONT_BOTTOM, length=(1, 2))
    # bord supérieur éclairé des planches (lisibilité : on voit où sont posés les objets)
    for b in (FRONT_TOP, FRONT_BOTTOM):
        for x in range(b[0], b[2]):
            c = img.getpixel((x, b[1]))
            if opaque(c):
                img.putpixel((x, b[1]), mix(c, (255, 255, 255, 255), 0.18)[:3] + (c[3],))
    return van, img


# ------------------------------------------------------------------ Groupe 1 : tonneau -> caisse clouée

WOOD = {"o": hx("2c1f12"), "a": hx("5a3f22"), "b": hx("7a5732"), "c": hx("94703f"), "d": hx("ad874f"),
        "n": hx("5d5d5d"), "N": hx("9a9a9a"), "s": hx("e6e0cc"), "k": hx("1a120a")}


def crate_side(rnd):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            board = y // 4
            base = [WOOD["b"], WOOD["c"], WOOD["b"], WOOD["c"]][board]
            c = base
            if y % 4 == 3:
                c = WOOD["a"]
            elif (x * 3 + y * 7 + board * 5) % 11 == 0:
                c = WOOD["d"] if rnd.random() < 0.5 else WOOD["a"]
            px[x, y] = c
    # cadre : montants verticaux plus sombres + diagonale
    for y in range(16):
        for x in (0, 1, 14, 15):
            px[x, y] = WOOD["a"] if x in (1, 14) else WOOD["o"]
    for i in range(2, 14):
        px[i, 15 - i] = WOOD["d"]
        px[i, 16 - i if 16 - i < 16 else 15] = WOOD["a"]
    # clous
    for x, y in [(1, 1), (14, 1), (1, 14), (14, 14), (1, 7), (14, 7)]:
        px[x, y] = WOOD["N"]
    # pochoir (petite flèche « haut » blanche, à moitié effacée)
    for x, y in [(5, 3), (4, 4), (5, 4), (6, 4), (5, 5), (5, 6)]:
        if rnd.random() < 0.85:
            px[x, y] = WOOD["s"]
    grime(img, rnd, 10, 0.82)
    return img


def crate_top(rnd, open_=False):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            board = x // 4
            c = [WOOD["c"], WOOD["b"], WOOD["c"], WOOD["b"]][board]
            if x % 4 == 3:
                c = WOOD["a"]
            elif (x * 5 + y * 3) % 13 == 0:
                c = WOOD["d"]
            px[x, y] = c
    for i in range(16):  # cadre
        for j in (0, 15):
            px[i, j] = WOOD["o"]
            px[j, i] = WOOD["o"]
    for i in range(1, 15):
        px[i, 1] = WOOD["a"]
        px[i, 14] = WOOD["a"]
    if open_:
        # couvercle retiré : intérieur sombre, paille, bord de planches
        for y in range(2, 14):
            for x in range(2, 14):
                px[x, y] = WOOD["k"] if (x + y) % 5 else hx("2a1e10")
        for x, y in [(3, 4), (4, 4), (5, 5), (9, 3), (10, 4), (7, 9), (8, 9), (11, 11), (4, 11), (5, 12), (12, 7)]:
            px[x, y] = hx("b8963c")
            if x + 1 < 14:
                px[x + 1, y + (1 if y < 13 else 0)] = hx("8a6c28")
        for i in range(2, 14):
            px[i, 2] = mul(WOOD["a"], 0.7)
            px[2, i] = mul(WOOD["a"], 0.8)
    else:
        for x, y in [(1, 1), (14, 1), (1, 14), (14, 14), (5, 1), (10, 1), (5, 14), (10, 14)]:
            px[x, y] = WOOD["N"]
        # pochoir : bloc de lettres « RAT. » simplifié (traits blancs)
        for x, y in [(4, 6), (4, 7), (4, 8), (5, 6), (6, 7), (5, 7), (6, 8), (8, 6), (8, 7), (8, 8), (9, 6), (9, 7), (10, 6),
                     (10, 7), (10, 8)]:
            if rnd.random() < 0.8:
                px[x, y] = WOOD["s"]
        put(img, [(4, 10), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10)], hx("b02a20"))
    grime(img, rnd, 8, 0.83)
    return img


def crate_bottom(rnd):
    img = crate_top(rnd)
    px = img.load()
    for y in range(3, 13):
        for x in range(3, 13):
            if WOOD["s"][:3] == px[x, y][:3] or px[x, y][:3] == hx("b02a20")[:3]:
                px[x, y] = WOOD["b"]
    return mul_img(img, 0.85)


def mul_img(img, f):
    out = img.copy()
    px = out.load()
    for x in range(out.width):
        for y in range(out.height):
            if opaque(px[x, y]):
                px[x, y] = mul(px[x, y], f)
    return out


# ------------------------------------------------------------------ Groupe 1 : coffres (entité)

def _segments(img, row_y, y0, y1):
    """Bornes x des faces d'une bande de la texture de coffre (séparées par des colonnes sombres ou du vide)."""
    w = img.width
    xs = [x for x in range(w) if opaque(img.getpixel((x, row_y)))]
    if not xs:
        return []
    segs, start = [], xs[0]
    for a, b in zip(xs, xs[1:]):
        if b != a + 1:
            segs.append((start, a + 1))
            start = b
    segs.append((start, xs[-1] + 1))
    out = []
    for s0, s1 in segs:          # découpe par faces de 14 (ou 15 pour les doubles) si la bande est continue
        width = s1 - s0
        if width > 20:
            n = round(width / 14.5)
            step = width / n
            for k in range(n):
                out.append((s0 + int(round(k * step)), s0 + int(round((k + 1) * step))))
        else:
            out.append((s0, s1))
    return out


def military_chest(van, rnd):
    green = ["10140a", "232b14", "36421f", "4a582b", "5f6e38", "73824a"]
    img = ramp(van.copy(), green)
    px = img.load()
    # loquet en acier
    ramp(img, ["1c1c1c", "4a4a4a", "7a7a7a", "a8a8a8"], box=(0, 0, 6, 6))
    # bandes et coins métalliques sur chaque face de côté, pochoir sur les faces du corps
    for (ry0, ry1) in ((14, 19), (33, 43)):
        for s0, s1 in _segments(img, ry0 + 2, ry0, ry1):
            if s1 - s0 < 10:
                continue
            for y in range(ry0, ry1):
                for x in (s0 + 1, s1 - 2):
                    if opaque(px[x, y]):
                        px[x, y] = hx("5d625a")[:3] + (px[x, y][3],)
            if ry0 == 33:
                cx = (s0 + s1) // 2 - 2
                for dx, dy in [(0, 2), (1, 2), (2, 2), (3, 2), (4, 2), (2, 1), (2, 3), (0, 4), (4, 4), (0, 0), (4, 0)]:
                    if rnd.random() < 0.85 and opaque(px[cx + dx, ry0 + 2 + dy]):
                        px[cx + dx, ry0 + 2 + dy] = hx("dcd8c0")[:3] + (255,)
                grime(img, rnd, 4, 0.8, box=(s0, ry0, s1, ry1))
    # couvercle : deux poignées de transport sombres sur le dessus
    for s0, s1 in _segments(img, 6, 0, 14):
        if s1 - s0 >= 10:
            for x in range(s0 + 3, s1 - 3):
                if x in (s0 + 3, s1 - 4) or True:
                    pass
            put(img, [(s0 + 4, 6), (s0 + 5, 6), (s0 + 6, 6), (s1 - 7, 6), (s1 - 6, 6), (s1 - 5, 6)], "1d2210")
            grime(img, rnd, 5, 0.82, box=(s0, 0, s1, 14))
    return keep_alpha(img, van)


COPPER_STAGE = {"": 0, "_exposed": 1, "_weathered": 2, "_oxidized": 3}


def trunk_chest(van, rnd, stage):
    steel = ["15191c", "2c3439", "46525a", "617079", "7e8e96", "9aa9b0"]
    img = ramp(van.copy(), steel)
    px = img.load()
    ramp(img, ["2a2116", "6b5530", "a8894c", "d0b066"], box=(0, 0, 6, 6))   # fermoir en laiton
    w, h = img.size
    # cabossé : creux (sombre) avec un reflet clair au-dessus
    for _ in range(14):
        x, y = rnd.randrange(w), rnd.randrange(h)
        if opaque(px[x, y]) and y > 0 and opaque(px[x, y - 1]):
            px[x, y] = mul(px[x, y], 0.72)
            px[x, y - 1] = mix(px[x, y - 1], (210, 220, 225, 255), 0.35)[:3] + (px[x, y - 1][3],)
    # rouille selon l'oxydation (cuivre neuf -> oxydé)
    rust = [hx("6e2e12"), hx("8e4318"), hx("ad5e26")]
    for _ in range([6, 22, 45, 80][stage]):
        x, y = rnd.randrange(w), rnd.randrange(h)
        if opaque(px[x, y]):
            px[x, y] = rust[rnd.randrange(3)][:3] + (px[x, y][3],)
            if stage >= 2 and y + 1 < h and opaque(px[x, y + 1]) and rnd.random() < 0.6:
                px[x, y + 1] = mix(px[x, y + 1], rust[0], 0.6)[:3] + (px[x, y + 1][3],)
    if stage >= 2:
        streaks(img, rnd, 6 * stage, 0.8)
    if stage == 3:
        desat(img, 0.2, 0.92)
    return keep_alpha(img, van)


# ------------------------------------------------------------------ Groupe 1 : pot décoré -> jerrican

RED = ["2a0806", "54100c", "7e1a12", "a32a1c", "c23f2a", "d65a40"]


def jerrican_side(van, rnd):
    img = Image.new("RGBA", van.size)
    px = img.load()
    w, h = van.size
    xs = [x for x in range(w) if any(opaque(van.getpixel((x, y))) for y in range(h))]
    ys = [y for y in range(h) if any(opaque(van.getpixel((x, y))) for x in range(w))]
    x0, x1, y0, y1 = xs[0], xs[-1], ys[0], ys[-1]
    R = [hx(c) for c in RED]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = R[3]
            if x in (x0, x1) or y in (y0, y1):
                c = R[1]
            elif x == x0 + 1 or y == y0 + 1:
                c = R[4]
            px[x, y] = c
    # nervure en X emboutie (clair au-dessus, sombre en dessous)
    cx0, cy0, cx1, cy1 = x0 + 2, y0 + 3, x1 - 2, y1 - 2
    n = min(cx1 - cx0, cy1 - cy0)
    for i in range(n + 1):
        for (x, y) in ((cx0 + i, cy0 + i), (cx1 - i, cy0 + i)):
            px[x, y] = R[5]
            if y + 1 <= cy1:
                px[x, y + 1] = R[2]
    for x in range(cx0, cx1 + 1):   # bourrelet horizontal du haut
        px[x, y0 + 2] = R[2]
    # éraflures et peinture partie (métal nu)
    for _ in range(9):
        x, y = rnd.randrange(x0 + 1, x1), rnd.randrange(y0 + 1, y1)
        px[x, y] = hx("8d8d86") if rnd.random() < 0.5 else R[1]
    grime(img, rnd, 6, 0.8, box=(x0, y0, x1 + 1, y1 + 1))
    return keep_alpha(img, van)


def jerrican_base(van, rnd):
    img = ramp(van.copy(), RED)
    # bouchon noir sur les ouvertures (zones les plus sombres de la texture d'origine)
    px, vp = img.load(), van.load()
    for x in range(van.width):
        for y in range(van.height):
            if opaque(vp[x, y]) and lum(vp[x, y]) < 70:
                px[x, y] = hx("1d1d1d")[:3] + (vp[x, y][3],)
    grime(img, rnd, 12, 0.82)
    return keep_alpha(img, van)


def jerrican_pattern(van, rnd):
    """Motifs des tessons : gardés en relief sur la tôle rouge (pots à motifs des maps)."""
    img = ramp(van.copy(), RED)
    grime(img, rnd, 5, 0.85)
    return keep_alpha(img, van)


# ------------------------------------------------------------------ Groupe 1 : bibliothèque ciselée -> classeur

STEEL = {"o": hx("1c2023"), "a": hx("3a4248"), "b": hx("56616a"), "c": hx("74808a"), "d": hx("95a1aa"), "e": hx("b5c0c7")}


def cabinet_frame(rnd):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = STEEL["c"] if (x + y * 3) % 11 else STEEL["d"]
    for i in range(16):
        px[i, 0] = STEEL["d"]
        px[i, 15] = STEEL["a"]
        px[0, i] = STEEL["d"]
        px[15, i] = STEEL["a"]
    return img


# cases des tiroirs (texture des emplacements) : x 0-6 / 6-11 / 11-16, y 0-8 / 8-16
CELLS = [(0, 0, 6, 8), (6, 0, 11, 8), (11, 0, 16, 8), (0, 8, 6, 16), (6, 8, 11, 16), (11, 8, 16, 16)]
LABELS = ["e8e2c8", "f0d68a", "cfe3f0", "e8c0b0", "d6e8c8", "f0f0e8"]


def cabinet_slots(rnd, occupied):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for k, (x0, y0, x1, y1) in enumerate(CELLS):
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
                if edge:
                    px[x, y] = STEEL["a"] if (x == x1 - 1 or y == y1 - 1) else STEEL["d"]
                elif occupied:
                    px[x, y] = STEEL["c"]
                else:
                    px[x, y] = hx("0e1012") if y > y0 + 1 else hx("1a1d20")
        if occupied:
            # façade du tiroir : étiquette + poignée, chemises qui dépassent en haut
            put(img, [(x, y0 + 1) for x in range(x0 + 1, x1 - 1)], STEEL["e"])
            lab = hx(LABELS[k])
            put(img, [(x, y0 + 3) for x in range(x0 + 1, x1 - 1)], lab)
            put(img, [(x0 + 1 + (x1 - x0 - 2) // 2, y0 + 3)], "4a4030")
            put(img, [(x, y0 + 5) for x in range(x0 + 1, x1 - 1)], STEEL["o"])
            put(img, [(x, y0 + 6) for x in range(x0 + 2, x1 - 2)], STEEL["b"])
            put(img, [(x0 + 1, y0 + 2), (x1 - 2, y0 + 2)], "d8cfa8")
        else:
            # tiroir arraché : glissières visibles
            put(img, [(x0 + 1, y) for y in range(y0 + 2, y1 - 1)], STEEL["b"])
            put(img, [(x1 - 2, y) for y in range(y0 + 2, y1 - 1)], STEEL["b"])
            if rnd.random() < 0.6:
                put(img, [(x0 + 2, y1 - 2)], "c8c0a0")   # feuille oubliée au fond
    grime(img, rnd, 6, 0.85)
    return img


# ------------------------------------------------------------------ Groupe 1 : vault -> coffre-fort à molette

def safe_face(rnd, front, state="off", ominous=False):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    base = ["16191b", "262b2f", "394147", "4b555d", "5f6a72", "7a868e"]
    B = [hx(c) for c in base]
    for y in range(16):
        for x in range(16):
            px[x, y] = B[3] if (x * 7 + y * 5) % 13 else B[4]
    for i in range(16):
        px[i, 0] = B[5]
        px[0, i] = B[5]
        px[i, 15] = B[0]
        px[15, i] = B[0]
        px[i, 1] = B[4]
        px[1, i] = B[4]
        px[i, 14] = B[1]
        px[14, i] = B[1]
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:   # rivets
        px[x, y] = B[5]
        px[x + 1, y + 1] = B[1]
    if not front:
        for x, y in [(7, 2), (7, 13), (2, 7), (13, 7)]:
            px[x, y] = B[5]
        grime(img, rnd, 8, 0.85)
        return img
    # porte : rainure, molette, poignée, voyant
    door = (3, 3, 12, 12)
    for i in range(door[0], door[2] + 1):
        px[i, door[1]] = B[1]
        px[door[0], i] = B[1]
        px[i, door[3]] = B[5]
        px[door[2], i] = B[5]
    cx, cy = 6, 7
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            d = dx * dx + dy * dy
            if d <= 5:
                px[cx + dx, cy + dy] = hx("b8bec2") if d >= 4 else hx("8e979d")
    px[cx, cy] = hx("1d2124")
    for a in range(0, 8):
        ang = a * math.pi / 4
        x, y = cx + round(2.4 * math.cos(ang)), cy + round(2.4 * math.sin(ang))
        if (x - cx) ** 2 + (y - cy) ** 2 <= 6:
            px[x, y] = hx("e6eaec") if a % 2 == 0 else hx("8e979d")
    for y in range(5, 10):   # poignée
        px[10, y] = hx("c8cdd0")
        px[11, y] = B[1]
    px[9, 5] = hx("c8cdd0")
    px[9, 9] = hx("c8cdd0")
    led = {"off": "4a1410", "on": "38e04a", "ejecting": "f2c21b"}[state]
    if ominous:
        led = {"off": "3a0c0c", "on": "e03030", "ejecting": "ff7020"}[state]
    px[10, 3 + 1] = hx(led)
    if state == "ejecting":   # porte entrouverte : liseré sombre
        for y in range(4, 12):
            px[12, y] = hx("0b0d0e")
    grime(img, rnd, 6, 0.85)
    return img


def safe_top(rnd, ejecting=False, ominous=False):
    img = safe_face(rnd, False)
    if ejecting:
        put(img, [(7, 7), (8, 7), (7, 8), (8, 8)], "f2c21b" if not ominous else "ff7020")
    return img


# ------------------------------------------------------------------ Groupe 1 : sable / gravier suspects -> gravats

def rubble(jar, kind, stage, rnd):
    van = jar.tex(f"block/suspicious_{kind}_{stage}")
    if kind == "gravel":   # béton gris, armatures rouillées
        img = ramp(van.copy(), ["2e2e2e", "4c4c4b", "6b6a68", "878684", "a2a19e", "bcbbb7"])
        bits = [hx("8a8f94"), hx("a8a49a")]
        rebar = hx("7a3a18")
    else:                  # plâtre et briques cassées
        img = ramp(van.copy(), ["4a4036", "6e6253", "938674", "b0a48f", "c8bea8", "dcd4c0"])
        bits = [hx("8e3a2a"), hx("a8483a"), hx("6c2a20")]
        rebar = hx("5a5a5a")
    px = img.load()
    for _ in range(10 + stage * 4):   # morceaux (2 px) de brique ou de béton
        x, y = rnd.randrange(15), rnd.randrange(15)
        c = bits[rnd.randrange(len(bits))]
        px[x, y] = c
        px[x + 1, y] = mul(c, 0.8)
    for _ in range(stage + 1):        # tige d'armature
        x, y = rnd.randrange(2, 12), rnd.randrange(2, 12)
        for k in range(3 + stage):
            if x + k < 16 and y + k // 2 < 16:
                px[x + k, y + k // 2] = rebar
    for _ in range(stage):            # fissures/creux qui s'élargissent à chaque stade du pinceau
        crack(img, rnd, (rnd.randrange(16), rnd.randrange(16)), 6, hx("1e1c1a"))
    return van, img


# ------------------------------------------------------------------ Groupe 2 : blocs de jeu (refuge:block/...)

def barricade(jar, rnd):
    """Chêne pâle -> planches de barricade clouées sur un fond sombre."""
    van = jar.tex("block/pale_oak_planks")
    img = Image.new("RGBA", (16, 16), hx("17120c"))
    px = img.load()
    for y in range(16):      # fond : interstices et mur noirci derrière
        for x in range(16):
            if (x * 3 + y * 5) % 9 == 0:
                px[x, y] = hx("221a10")
    planks = [(1, 4, "b"), (6, 9, "c"), (11, 14, "b")]
    for y0, y1, tone in planks:
        dx = rnd.randrange(-1, 2)
        for y in range(y0, y1 + 1):
            for x in range(16):
                c = WOOD[tone]
                if y == y0:
                    c = WOOD["d"]
                elif y == y1:
                    c = WOOD["a"]
                elif (x * 7 + y * 3 + dx) % 10 == 0:
                    c = WOOD["a"]
                px[x, y] = c
        for x in (2 + dx, 13 + dx):   # clous
            if 0 <= x < 16:
                px[x, y0 + 1] = WOOD["N"]
                px[x, y0 + 2] = WOOD["n"]
    for i in range(16):      # planche en diagonale par-dessus
        for t in (0, 1):
            x, y = i, 15 - i + t - 1
            if 0 <= y < 16:
                px[x, y] = WOOD["d"] if t == 0 else WOOD["a"]
    put(img, [(3, 11), (12, 2)], WOOD["N"])
    grime(img, rnd, 12, 0.8)
    return van, img


def radio(rnd, top):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    O = [hx(c) for c in ["181a12", "2d3122", "434832", "585e43", "6e7555", "868d6b"]]
    for y in range(16):
        for x in range(16):
            px[x, y] = O[3] if (x * 5 + y * 3) % 11 else O[4]
    for i in range(16):
        px[i, 0] = O[5]
        px[0, i] = O[5]
        px[i, 15] = O[0]
        px[15, i] = O[0]
    if top:
        # poignée de transport + pied d'antenne + bouton
        for x in range(4, 12):
            px[x, 5] = O[1]
            px[x, 4] = hx("2a2a2a")
        px[4, 6] = hx("2a2a2a")
        px[11, 6] = hx("2a2a2a")
        for x, y in [(12, 11), (13, 11), (12, 12), (13, 12)]:
            px[x, y] = hx("1e1e1e")
        px[12, 11] = hx("9a9a9a")
        put(img, [(3, 11), (4, 11), (3, 12)], "3c3c3c")
        put(img, [(7, 10), (8, 10)], "c8c0a0")
        grime(img, rnd, 5, 0.85)
        return img
    # face : grille de haut-parleur, cadran éclairé, deux boutons
    for y in range(3, 13):
        for x in range(2, 8):
            px[x, y] = hx("1d1f17") if (x + y) % 2 == 0 else O[1]
    for x in range(9, 14):
        for y in range(3, 7):
            px[x, y] = hx("c9a648") if y in (4, 5) else hx("6b5a28")
    px[11, 4] = hx("d63a20")
    px[11, 5] = hx("d63a20")
    for x, y in [(10, 4), (12, 5), (13, 4)]:
        px[x, y] = hx("3a3020")
    for cx in (10, 13):
        for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1)]:
            px[cx + dx - 1, 9 + dy] = hx("2a2a2a")
        px[cx - 1, 9] = hx("8a8a8a")
    put(img, [(9, 12), (10, 12), (11, 12), (12, 12), (13, 12)], O[1])
    put(img, [(10, 12)], "38e04a")      # voyant marche
    grime(img, rnd, 6, 0.85)
    return img


def beacon(rnd, top):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    if top:
        for y in range(16):
            for x in range(16):
                px[x, y] = hx("3a3a3a") if (x + y) % 3 else hx("454545")
        for i in range(16):
            px[i, 0] = hx("5a5a5a")
            px[0, i] = hx("5a5a5a")
            px[i, 15] = hx("1e1e1e")
            px[15, i] = hx("1e1e1e")
        for dx in range(-3, 4):
            for dy in range(-3, 4):
                d = dx * dx + dy * dy
                if d <= 10:
                    px[8 + dx, 8 + dy] = hx("ff3a1a") if d <= 2 else (hx("d02010") if d <= 6 else hx("7a1008"))
        px[7, 7] = hx("ffd060")
        px[8, 7] = hx("fff0a0")
        return img
    for y in range(16):
        for x in range(16):
            band = ((x + y) // 3) % 2
            px[x, y] = hx("c22a1a") if band else hx("e8e2d0")
    for i in range(16):
        px[i, 0] = hx("4a4a4a")
        px[i, 1] = hx("6a6a6a")
        px[i, 15] = hx("2a2a2a")
        px[i, 14] = hx("4a4a4a")
    # étiquette « SOS » sur fond noir : lettres de 3 x 5 px
    for x in range(2, 14):
        for y in range(4, 11):
            px[x, y] = hx("1a1a1a")
    S = ["###", "#..", "###", "..#", "###"]
    O = ["###", "#.#", "#.#", "#.#", "###"]
    for ox, glyph in ((3, S), (7, O), (11, S)):
        for gy, row in enumerate(glyph):
            for gx, ch in enumerate(row):
                if ch == "#" and ox + gx < 14:
                    px[ox + gx, 5 + gy] = hx("f2c21b")
    grime(img, rnd, 8, 0.84)
    return img


def generator(rnd):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = hx("3a3d40") if (x * 3 + y) % 7 else hx("44484c")
    for i in range(16):   # bandes jaune/noir en haut et en bas
        for y in (0, 1, 14, 15):
            px[i, y] = hx("f2c21b") if ((i + y) // 2) % 2 else hx("1e1c18")
    for i in range(16):
        px[0, i] = hx("1e1e1e") if i not in (0, 1, 14, 15) else px[0, i]
        px[15, i] = hx("1e1e1e") if i not in (0, 1, 14, 15) else px[15, i]
    # grille d'aération à gauche
    for y in range(4, 12):
        for x in range(2, 6):
            px[x, y] = hx("121314") if y % 2 == 0 else hx("5a5e62")
    # pile : corps rouge, borne, éclair
    for y in range(4, 12):
        for x in range(8, 13):
            px[x, y] = hx("b0261a") if x < 12 else hx("7a160e")
    for x in (9, 10, 11):
        px[x, 3] = hx("9a9a9a")
    for x, y in [(10, 5), (9, 6), (10, 6), (9, 7), (10, 7), (11, 7), (10, 8), (11, 8), (10, 9)]:
        px[x, y] = hx("ffe066")
    # voyants
    px[2, 13 - 1] = hx("38e04a")
    px[4, 13 - 1] = hx("d02010")
    grime(img, rnd, 8, 0.85)
    return img


# ------------------------------------------------------------------ Ambiance

def worn_planks(jar, wood, rnd):
    van = jar.tex(f"block/{wood}_planks")
    img = desat(van.copy(), 0.25, 0.9)
    px = img.load()
    for _ in range(14):     # rayures et usure claire
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[x, y] = mix(px[x, y], (220, 210, 190, 255), 0.25)[:3] + (255,)
    grime(img, rnd, 18, 0.78)
    streaks(img, rnd, 3, 0.85)
    # trous de clous au bout des planches (rangées de 4 px, comme le vanilla)
    for y in (1, 5, 9, 13):
        x = rnd.choice([1, 2, 13, 14])
        px[x, y] = mul(px[x, y], 0.45)
    return van, img


def worn_bricks(jar, name, rnd):
    van = jar.tex(f"block/{name}")
    img = desat(van.copy(), 0.25, 0.9)
    grime(img, rnd, 20, 0.78)
    streaks(img, rnd, 4, 0.82, length=(4, 10))   # traces de suie
    if rnd.random() < 0.7:
        crack(img, rnd, (rnd.randrange(16), rnd.randrange(8)), 5, mul(img.getpixel((8, 8)), 0.45))
    return van, img


def dirty_glass(jar, ref, rnd, cracked=True):
    """Vitres : poussière dans les coins, coulures, fissure blanche (lisible de loin)."""
    van = jar.tex(ref)
    img = van.copy()
    px = img.load()
    dirt = (92, 84, 70)
    for x in range(1, 15):
        for y in range(1, 15):
            d = min(x, y, 15 - x, 15 - y)
            p = {1: 0.5, 2: 0.3, 3: 0.14}.get(d, 0.0) + (0.12 if y > 11 else 0)
            if rnd.random() < p:
                c = px[x, y]
                base = c if c[3] > 0 else (dirt[0], dirt[1], dirt[2], 0)
                px[x, y] = mix(base, dirt + (255,), 0.6)[:3] + (max(c[3], 170),)
    if cracked:
        c0 = (rnd.randrange(4, 12), rnd.randrange(3, 8))
        for _ in range(3):
            crack(img, rnd, c0, rnd.randint(4, 7), (235, 240, 242, 255), box=(1, 1, 15, 15), alpha=200)
    return van, img


def dirty_pane_top(jar, ref, rnd):
    van = jar.tex(ref)
    img = van.copy()
    grime(img, rnd, 5, 0.75)
    return van, img


def lantern_anim(jar, name, rnd):
    """Lanterne -> ampoule grillée qui vacille : 4 images (allumée, faible, allumée, éteinte), ordre irrégulier."""
    van = jar.tex(f"block/{name}")
    fw = van.width
    frame0 = van.crop((0, 0, fw, fw))
    glow_cols = {}
    fp = frame0.load()
    # les pixels « lumineux » (saturés / clairs) de la cage sont dans x 0..5, y 2..8 (UV du modèle)
    for x in range(0, 6):
        for y in range(2, 9):
            c = fp[x, y]
            if opaque(c) and (max(c[:3]) - min(c[:3]) > 60 or lum(c) > 170):
                glow_cols[(x, y)] = c
    soul = name.startswith("soul")
    lit = [hx("fff4c0"), hx("f2dc8a"), hx("c8a858")] if not soul else [hx("e0fbff"), hx("9fe6ee"), hx("5aa8b0")]
    dim = [hx("b8a878"), hx("8c7c50"), hx("5e5236")] if not soul else [hx("88b4b8"), hx("5e8c90"), hx("3c5e62")]
    off = [hx("6e6a60"), hx("54504a"), hx("3e3b36")]
    frames = []
    for pal in (lit, dim, off):
        fr = frame0.copy()
        p = fr.load()
        for (x, y), c in glow_cols.items():
            l = lum(c)
            p[x, y] = (pal[0] if l > 200 else pal[1] if l > 150 else pal[2])[:3] + (c[3],)
        # filament
        if (2, 5) in glow_cols:
            p[2, 5] = hx("4a3a20") if pal is not lit else hx("ffffff")
        if (3, 5) in glow_cols:
            p[3, 5] = hx("4a3a20") if pal is not lit else hx("ffffe0")
        grime(fr, rnd, 2, 0.8, box=(0, 0, 6, 9))
        frames.append(fr)
    strip = Image.new("RGBA", (fw, fw * 3))
    for k, fr in enumerate(frames):
        strip.alpha_composite(fr, (0, k * fw))
    meta = {"animation": {"frametime": 2, "interpolate": False, "frames": [
        {"index": 0, "time": 40}, {"index": 1, "time": 2}, {"index": 0, "time": 6}, {"index": 2, "time": 3},
        {"index": 0, "time": 70}, {"index": 1, "time": 4}, {"index": 2, "time": 2}, {"index": 1, "time": 3},
        {"index": 0, "time": 30}, {"index": 2, "time": 12}, {"index": 1, "time": 2}]}}
    return van, strip, meta, frames[0]


def lantern_item(jar, name, rnd):
    van = jar.tex(f"item/{name}")
    img = van.copy()
    px = img.load()
    soul = name.startswith("soul")
    lit = [hx("fff4c0"), hx("f2dc8a"), hx("c8a858")] if not soul else [hx("e0fbff"), hx("9fe6ee"), hx("5aa8b0")]
    for x in range(16):
        for y in range(16):
            c = px[x, y]
            if opaque(c) and (max(c[:3]) - min(c[:3]) > 60):
                l = lum(c)
                px[x, y] = (lit[0] if l > 200 else lit[1] if l > 150 else lit[2])[:3] + (c[3],)
    return van, img


def dull_colormap(jar, name):
    van = jar.tex(f"colormap/{name}")
    img = desat(van.copy(), 0.38, 0.86, tint=(150, 130, 80))
    return van, img.convert("RGB")


def dull_grass_side(jar, rnd):
    van = jar.tex("block/grass_block_side")
    img = van.copy()
    desat(img, 0.45, 0.85, tint=(140, 120, 70), box=(0, 0, 16, 5))
    grime(img, rnd, 10, 0.82, box=(0, 4, 16, 16))
    return van, img


def patchy_gray(jar, ref, rnd, holes=0, n=16):
    """Textures grises teintées par le biome (dessus de l'herbe, feuilles) : plaques sèches plus sombres."""
    van = jar.tex(ref)
    img = van.copy()
    px = img.load()
    for _ in range(n):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if opaque(px[x, y]):
            px[x, y] = mul(px[x, y], rnd.choice([0.7, 0.78, 0.86]))
    for _ in range(holes):   # feuilles : quelques trous (feuilles tombées)
        x, y = rnd.randrange(16), rnd.randrange(16)
        if opaque(px[x, y]):
            px[x, y] = (0, 0, 0, 0)
    return van, img


# ------------------------------------------------------------------ construction

def model_json(parent, textures):
    return json.dumps({"parent": parent, "textures": textures}, indent=1).encode("utf-8")


def build(jar_path=None):
    """Renvoie (fichiers {chemin: octets}, aperçus [(groupe, nom, avant, après)], cubes [(nom, haut, côté, face)])."""
    jar = Jar(jar_path or find_vanilla())
    files, prev, cubes = {}, [], []

    def tex(ref, img, before=None, group=None, name=None, meta=None):
        path = f"assets/minecraft/textures/{ref}.png" if ":" not in ref else \
            f"assets/{ref.split(':')[0]}/textures/{ref.split(':')[1]}.png"
        files[path] = png(img)
        if meta is not None:
            files[path + ".mcmeta"] = json.dumps(meta, indent=1).encode("utf-8")
        if group:
            prev.append((group, name or ref.split("/")[-1], before, img))

    # --- étagères
    for wood in WOODS:
        rnd = random.Random("shelf" + wood)
        van, img = shelf(jar, wood, rnd)
        tex(f"block/{wood}_shelf", img, van, "Étagères", f"{wood} : {SHELVES[wood][0]}")

    # --- tonneau -> caisse
    rnd = random.Random("crate")
    side, top, opn, bot = crate_side(rnd), crate_top(rnd), crate_top(rnd, True), crate_bottom(rnd)
    for ref, img in (("block/barrel_side", side), ("block/barrel_top", top), ("block/barrel_top_open", opn),
                     ("block/barrel_bottom", bot)):
        tex(ref, img, jar.tex(ref), "Tonneau -> caisse", ref.split("/")[-1])
    cubes.append(("caisse (tonneau)", top, side, side))
    cubes.append(("caisse ouverte", opn, side, side))

    # --- coffres
    for part in ("normal", "normal_left", "normal_right", "christmas", "christmas_left", "christmas_right"):
        van = jar.tex(f"entity/chest/{part}")
        img = military_chest(van, random.Random("chest" + part.replace("christmas", "normal")))
        tex(f"entity/chest/{part}", img, van, "Coffres" if part.startswith("normal") else None, part)
    for st, k in COPPER_STAGE.items():
        for side_ in ("", "_left", "_right"):
            ref = f"entity/chest/copper{st}{side_}"
            van = jar.tex(ref)
            img = trunk_chest(van, random.Random("trunk" + st + side_), k)
            tex(ref, img, van, "Coffres en cuivre" if side_ == "" else None, ref.split("/")[-1])

    # --- pot décoré -> jerrican
    rnd = random.Random("pot")
    van = jar.tex("entity/decorated_pot/decorated_pot_side")
    tex("entity/decorated_pot/decorated_pot_side", jerrican_side(van, rnd), van, "Pot -> jerrican", "pot_side")
    van = jar.tex("entity/decorated_pot/decorated_pot_base")
    tex("entity/decorated_pot/decorated_pot_base", jerrican_base(van, rnd), van, "Pot -> jerrican", "pot_base")
    for n in sorted(jar.names):
        if n.startswith("assets/minecraft/textures/entity/decorated_pot/") and n.endswith("_pottery_pattern.png"):
            ref = n[len("assets/minecraft/textures/"):-4]
            van = jar.tex(ref)
            img = jerrican_pattern(van, random.Random(ref))
            tex(ref, img, van, "Pot -> jerrican" if "blade" in ref or "skull" in ref else None, ref.split("/")[-1])

    # --- bibliothèque ciselée -> classeur
    rnd = random.Random("cabinet")
    frame = cabinet_frame(rnd)
    topc = cabinet_frame(rnd)
    put(topc, [(x, 7) for x in range(2, 14)], STEEL["a"])
    occ, emp = cabinet_slots(rnd, True), cabinet_slots(rnd, False)
    for ref, img in (("block/chiseled_bookshelf_side", frame), ("block/chiseled_bookshelf_top", topc),
                     ("block/chiseled_bookshelf_occupied", occ), ("block/chiseled_bookshelf_empty", emp)):
        tex(ref, img, jar.tex(ref), "Bibliothèque ciselée -> classeur", ref.split("/")[-1])
    cubes.append(("classeur (plein)", topc, frame, occ))

    # --- vault -> coffre-fort
    for om in (False, True):
        sfx = "_ominous" if om else ""
        rnd = random.Random("vault" + sfx)
        defs = [(f"block/vault_front_off{sfx}", safe_face(rnd, True, "off", om)),
                (f"block/vault_front_on{sfx}", safe_face(rnd, True, "on", om)),
                (f"block/vault_front_ejecting{sfx}", safe_face(rnd, True, "ejecting", om)),
                (f"block/vault_side_off{sfx}", safe_face(rnd, False)),
                (f"block/vault_side_on{sfx}", safe_face(rnd, False)),
                (f"block/vault_top{sfx}", safe_top(rnd, False, om)),
                (f"block/vault_top_ejecting{sfx}", safe_top(rnd, True, om)),
                (f"block/vault_bottom{sfx}", mul_img(safe_face(rnd, False), 0.8))]
        for ref, img in defs:
            tex(ref, img, jar.tex(ref), "Vault -> coffre-fort" if not om else None, ref.split("/")[-1])
        if not om:
            cubes.append(("coffre-fort (vault)", defs[5][1], defs[3][1], defs[0][1]))
            cubes.append(("coffre-fort actif", defs[5][1], defs[4][1], defs[1][1]))

    # --- blocs suspects -> gravats
    for kind in ("sand", "gravel"):
        for st in range(4):
            van, img = rubble(jar, kind, st, random.Random(f"rubble{kind}{st}"))
            tex(f"block/suspicious_{kind}_{st}", img, van, "Blocs suspects -> gravats", f"{kind}_{st}")

    # --- groupe 2 : modèles vanilla redirigés vers refuge:block/...
    rnd = random.Random("barricade")
    van, img = barricade(jar, rnd)
    tex(f"{NS}:block/barricade", img, van, "Blocs de jeu", "chêne pâle -> barricade")
    files["assets/minecraft/models/block/pale_oak_planks.json"] = model_json(
        "minecraft:block/cube_all", {"all": f"{NS}:block/barricade"})
    cubes.append(("barricade (chêne pâle)", img, img, img))

    rnd = random.Random("radio")
    rs, rt = radio(rnd, False), radio(rnd, True)
    tex(f"{NS}:block/radio_side", rs, jar.tex("block/lodestone_side"), "Blocs de jeu", "magnétite -> radio (côté)")
    tex(f"{NS}:block/radio_top", rt, jar.tex("block/lodestone_top"), "Blocs de jeu", "magnétite -> radio (dessus)")
    files["assets/minecraft/models/block/lodestone.json"] = model_json(
        "minecraft:block/cube_column", {"end": f"{NS}:block/radio_top", "side": f"{NS}:block/radio_side"})
    cubes.append(("poste radio (magnétite)", rt, rs, rs))

    rnd = random.Random("beacon")
    bs, bt = beacon(rnd, False), beacon(rnd, True)
    tex(f"{NS}:block/balise_side", bs, jar.tex("block/target_side"), "Blocs de jeu", "cible -> balise (côté)")
    tex(f"{NS}:block/balise_top", bt, jar.tex("block/target_top"), "Blocs de jeu", "cible -> balise (dessus)")
    files["assets/minecraft/models/block/target.json"] = model_json(
        "minecraft:block/cube_column", {"end": f"{NS}:block/balise_top", "side": f"{NS}:block/balise_side"})
    cubes.append(("balise d'extraction (cible)", bt, bs, bs))

    rnd = random.Random("generator")
    g = generator(rnd)
    tex(f"{NS}:block/generateur", g, jar.tex("block/redstone_block"), "Blocs de jeu", "bloc de redstone -> générateur")
    files["assets/minecraft/models/block/redstone_block.json"] = model_json(
        "minecraft:block/cube_all", {"all": f"{NS}:block/generateur"})
    cubes.append(("générateur (redstone)", g, g, g))

    # --- ambiance
    for wood in WOODS:
        van, img = worn_planks(jar, wood, random.Random("planks" + wood))
        tex(f"block/{wood}_planks", img, van, "Planches usées" if wood in ("oak", "spruce", "birch", "dark_oak") else None,
            f"{wood}_planks")
    for name in ("bricks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"):
        van, img = worn_bricks(jar, name, random.Random(name))
        tex(f"block/{name}", img, van, "Briques sales", name)
    rnd = random.Random("glass")
    van, img = dirty_glass(jar, "block/glass", rnd)
    tex("block/glass", img, van, "Vitres", "glass")
    van, img = dirty_pane_top(jar, "block/glass_pane_top", rnd)
    tex("block/glass_pane_top", img, van, "Vitres", "glass_pane_top")
    for col in COLORS:
        van, img = dirty_glass(jar, f"block/{col}_stained_glass", random.Random("glass" + col))
        tex(f"block/{col}_stained_glass", img, van, "Vitres" if col in ("red", "light_blue") else None,
            f"{col}_stained_glass")
        van, img = dirty_pane_top(jar, f"block/{col}_stained_glass_pane_top", random.Random("pane" + col))
        tex(f"block/{col}_stained_glass_pane_top", img, van)
    for name in ("lantern", "soul_lantern"):
        van, strip, meta, f0 = lantern_anim(jar, name, random.Random(name))
        tex(f"block/{name}", strip, None, None, None, meta)
        prev.append(("Lanternes", name + " (image 1)", van.crop((0, 0, 16, 16)), f0))
        prev.append(("Lanternes", name + " (anim.)", van, strip))
        vi, ii = lantern_item(jar, name, random.Random(name + "item"))
        tex(f"item/{name}", ii, vi, "Lanternes", name + " (objet)")
    rnd = random.Random("grass")
    van, img = patchy_gray(jar, "block/grass_block_top", rnd, 0, 22)
    tex("block/grass_block_top", img, van, "Herbe et feuilles", "grass_block_top (gris, teinté)")
    van, img = patchy_gray(jar, "block/grass_block_side_overlay", rnd, 0, 4)
    tex("block/grass_block_side_overlay", img, van)
    van, img = dull_grass_side(jar, rnd)
    tex("block/grass_block_side", img, van, "Herbe et feuilles", "grass_block_side")
    for leaf in ("oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "mangrove"):
        van, img = patchy_gray(jar, f"block/{leaf}_leaves", random.Random("leaf" + leaf), 5, 14)
        tex(f"block/{leaf}_leaves", img, van, "Herbe et feuilles" if leaf == "oak" else None, f"{leaf}_leaves")
    for cm in ("grass", "foliage"):
        van, img = dull_colormap(jar, cm)
        files[f"assets/minecraft/textures/colormap/{cm}.png"] = png(img)
        prev.append(("Herbe et feuilles", f"colormap {cm}", van, img.convert("RGBA")))

    # vérification interne : chaque fichier minecraft remplacé existe dans le jar (pas de faute de frappe)
    bad = [p for p in files if p.startswith("assets/minecraft/") and not p.endswith(".mcmeta") and not jar.has(p)]
    bad += [p for p in files if p.endswith(".mcmeta") and p.startswith("assets/minecraft/")
            and not jar.has(p[:-len(".mcmeta")])]
    if bad:
        raise SystemExit("ERREUR : chemins vanilla inexistants dans le jar 26.3 : " + ", ".join(bad))
    return files, prev, cubes, jar


# ------------------------------------------------------------------ aperçus

def _iso(top, left, right, s=6):
    """Petit rendu isométrique d'un cube (dessus, face gauche, face droite)."""
    n = 16 * s
    W = int(n * 1.74) + 4
    H = int(n * 2.0) + 4
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx = W / 2

    def face(img, origin, ux, uy, shade):
        big = img.convert("RGBA").resize((16, 16), Image.NEAREST)
        src = big.load()
        dst = out.load()
        # parcours des pixels de destination dans la boîte englobante, inverse de la transformation affine
        pts = [origin, (origin[0] + ux[0] * 16, origin[1] + ux[1] * 16), (origin[0] + uy[0] * 16, origin[1] + uy[1] * 16),
               (origin[0] + ux[0] * 16 + uy[0] * 16, origin[1] + ux[1] * 16 + uy[1] * 16)]
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        det = ux[0] * uy[1] - ux[1] * uy[0]
        for X in range(int(min(xs)), int(max(xs)) + 1):
            for Y in range(int(min(ys)), int(max(ys)) + 1):
                dx, dy = X + 0.5 - origin[0], Y + 0.5 - origin[1]
                u = (dx * uy[1] - dy * uy[0]) / det
                v = (ux[0] * dy - ux[1] * dx) / det
                if 0 <= u < 16 and 0 <= v < 16 and 0 <= X < W and 0 <= Y < H:
                    c = src[int(u), int(v)]
                    if c[3] > 0:
                        dst[X, Y] = mul(c, shade)[:3] + (255,)

    a = s * 0.866
    b = s * 0.5
    topo = (cx, 2)
    face(top, topo, (a, b), (-a, b), 1.0)
    face(left, (cx - 16 * a, 2 + 16 * b), (a, b), (0, s), 0.78)
    face(right, (cx, 2 + 32 * b), (a, -b), (0, s), 0.62)
    return out


def _font(size):
    from PIL import ImageFont
    for f in ("arial.ttf", "C:/Windows/Fonts/arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def _scale(im, maxw, maxh):
    return max(1, min(6, int(maxw // im.width), int(maxh // im.height)))


def planche_blocs(prev, cubes, path):
    from collections import OrderedDict
    groups = OrderedDict()
    for g, n, a, b in prev:
        if g:
            groups.setdefault(g, []).append((n, a, b))
    cellw, pad = 300, 8
    cols = 6
    rows_h = []
    for g, items in groups.items():
        r = (len(items) + cols - 1) // cols
        hmax = max(max(a.height * _scale(a, cellw / 2 - 12, 200) if a else 16,
                       b.height * _scale(b, cellw / 2 - 12, 200)) for _, a, b in items)
        rows_h.append((g, items, r, hmax + 34, 1))
    iso_h = 230
    H = 30 + sum(22 + r * h for _, _, r, h, _ in rows_h) + 40 + ((len(cubes) + 6) // 7) * iso_h
    W = cols * cellw
    sheet = Image.new("RGBA", (W, H), (198, 198, 198, 255))
    d = ImageDraw.Draw(sheet)
    d.font = _font(13)
    d.text((6, 6), "RefugePack - retextures de blocs vanilla : AVANT (gauche) / APRÈS (droite), agrandies, fond rose = transparent",
           fill=(30, 30, 30, 255))
    y = 30
    for g, items, r, h, scale in rows_h:
        d.text((6, y + 4), g, fill=(20, 20, 120, 255))
        y += 22
        for k, (n, a, b) in enumerate(items):
            x = (k % cols) * cellw + pad
            yy = y + (k // cols) * h
            d.text((x, yy), n[:46], fill=(20, 20, 20, 255))
            xx = x
            for im in (a, b):
                if im is None:
                    continue
                bg = Image.new("RGBA", im.size, (255, 120, 220, 255))
                bg.alpha_composite(im.convert("RGBA"))
                sc = _scale(im, cellw / 2 - 12, 200)
                if im.width > cellw / 2 - 12:
                    bg = bg.resize((int(cellw / 2 - 12), int((cellw / 2 - 12) * im.height / im.width)), Image.BILINEAR)
                    sc = 1
                big = bg.resize((bg.width * sc, bg.height * sc), Image.NEAREST)
                sheet.paste(big, (xx, yy + 14))
                xx += big.width + 8
        y += r * h
    d.text((6, y + 10), "Rendus isométriques simples (dessus / côté / face avant)", fill=(20, 20, 120, 255))
    y += 34
    for k, (n, t, l, r) in enumerate(cubes):
        x = (k % 7) * (W // 7) + 10
        yy = y + (k // 7) * iso_h
        iso = _iso(t, l, r, 6)
        sheet.alpha_composite(iso, (x, yy))
        d.text((x, yy + iso.height + 2), n, fill=(20, 20, 20, 255))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)
