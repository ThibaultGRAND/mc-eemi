"""Pack de textures AUTONOME du mode Dernier Refuge (RefugePack.zip), espace de noms `refuge:`.

Usage :
    python gen_refuge.py <pack_reference.zip> <sortie.zip>

- <pack_reference.zip> : le pack EEMICRAFT publié ; on n'y prend QUE pack.mcmeta (même pack_format que le
  serveur, 26.3). Le pack produit ne contient que les fichiers du mode (aucune fusion) : il est envoyé
  par le plugin EN PLUS du pack EEMICRAFT (packs empilés).
- Les ids des objets du pack sont lus dans ../src/main/java/fr/eemicraft/refuge/Catalogue.java (objets
  marqués custom) : le script échoue si un id n'a pas de dessin ici, ou si un dessin n'a plus d'id.
- Écrit aussi item_models.txt (liste refuge:<id>) et ../apercus/planche.png, puis lance verifier_pack.py.
- Ajoute les retextures de blocs vanilla de gen_blocs.py (table : BLOCS.md) et la planche ../apercus/blocs.png.
- Ajoute les interfaces de gen_ui.py (fonds de menus, polices, objet invisible refuge:gui/vide), écrit
  ../src/main/java/fr/eemicraft/refuge/ui/Fonds.java et la planche ../apercus/menus.png.

Chaque objet : assets/refuge/items/<id>.json -> modèle refuge:item/<id> -> texture refuge:item/<id>
(textures/item, jamais textures/entity), 16x16, palettes reprises des objets vanilla.
"""
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import zipfile

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocs  # noqa: E402  retextures de blocs vanilla (BLOCS.md)
import gen_ui  # noqa: E402  interfaces (fonds de menus, polices, objet invisible) : voir gen_ui.py

ICI = os.path.dirname(os.path.abspath(__file__))
RACINE = os.path.dirname(ICI)
CATALOGUE = os.path.join(RACINE, "src", "main", "java", "fr", "eemicraft", "refuge", "Catalogue.java")
NS = "refuge"
DATE = (2026, 11, 13, 0, 0, 0)

# ------------------------------------------------------------------ palettes (relevées dans les textures vanilla 26.3)
T = (0, 0, 0, 0)
PAL = {
    ".": T,
    # fer (iron_sword / iron_nugget)
    "W": (255, 255, 255, 255), "L": (216, 216, 216, 255), "M": (190, 190, 190, 255), "G": (150, 150, 150, 255),
    "D": (107, 107, 107, 255), "K": (68, 68, 68, 255), "X": (24, 24, 24, 255),
    # bois (manche d'épée, arbalète)
    "h": (137, 103, 39, 255), "j": (104, 78, 30, 255), "k": (73, 54, 21, 255), "l": (40, 30, 11, 255),
    "o": (181, 115, 47, 255), "p": (144, 88, 31, 255), "q": (109, 66, 24, 255),
    # or (gold_nugget)
    "y": (249, 249, 105, 255), "Y": (248, 210, 106, 255), "z": (233, 177, 21, 255), "Z": (220, 150, 19, 255),
    "b": (127, 82, 12, 255),
    # cuivre (copper_ingot)
    "c": (231, 124, 86, 255), "C": (193, 90, 54, 255), "v": (138, 58, 33, 255),
    # redstone
    "r": (255, 0, 0, 255), "R": (171, 0, 0, 255), "n": (105, 0, 0, 255),
    # verre (glass_bottle)
    "g": (212, 229, 247, 255), "a": (179, 207, 236, 255), "s": (139, 173, 208, 255), "t": (93, 143, 194, 255),
    # bouchon / tissu
    "e": (204, 175, 165, 255), "f": (212, 109, 73, 255),
    # flamme
    "F": (255, 220, 80, 255), "O": (255, 150, 30, 255), "E": (220, 70, 20, 255),
    # papier / tissu
    "P": (252, 252, 242, 255), "Q": (233, 234, 235, 255), "U": (214, 214, 214, 255), "V": (174, 174, 174, 255),
    "B": (135, 135, 135, 255),
    # pierre (flint / cobblestone)
    "1": (168, 168, 168, 255), "2": (127, 127, 127, 255), "3": (86, 86, 86, 255), "4": (61, 60, 60, 255),
    # améthyste
    "A": (207, 160, 243, 255), "H": (141, 106, 204, 255), "I": (84, 57, 138, 255),
    # vert (étiquette)
    "x": (94, 124, 22, 255), "w": (60, 84, 14, 255),
}


def grid(rows):
    assert len(rows) == 16, (len(rows), rows)
    out = []
    for r in rows:
        assert len(r) == 16, (len(r), r)
        out.append([PAL[ch] for ch in r])
    return out


# ------------------------------------------------------------------ dessins (16 lignes de 16 caractères)
S = {}

S["dague"] = grid([
    "................",
    "................",
    "................",
    "...........XX...",
    "..........XWLX..",
    ".........XWLGX..",
    "........XWLGX...",
    ".......XWLGX....",
    "..XX..XWLGX.....",
    "..XKXXWLGX......",
    "...XKDLGX.......",
    "....XKKX........",
    "...XjhXKX.......",
    "..XjhX.XX.......",
    "..XkX...........",
    "...X............",
])

S["couteau_lancer"] = grid([
    "................",
    "................",
    "................",
    "................",
    "...........XX...",
    "..........XWX...",
    ".........XWLX...",
    "........XWLX....",
    ".......XWGX.....",
    "......XDGX......",
    ".....XKKX.......",
    "....XKXX........",
    "...XKX..........",
    "..XKKX..........",
    "..XXX...........",
    "................",
])

S["bouteille_incendiaire"] = grid([
    "......O.........",
    ".....OFO........",
    "......E.........",
    ".....XPX........",
    ".....XUX........",
    ".....XgX........",
    ".....XaX........",
    "....XgsaX.......",
    "...XgOOOaX......",
    "...XgOFOsX......",
    "...XaOOOsX......",
    "...XaEOEtX......",
    "...XsEEEtX......",
    "...XtsssTX......".replace("T", "t"),
    "....XXXXX.......",
    "................",
])

S["fusee_appat"] = grid([
    "................",
    ".........F......",
    "........FOF.....",
    ".........O......",
    "........XKX.....",
    ".......XRrRX....",
    ".......XrPRX....",
    ".......XRrRX....",
    ".......XrRnX....",
    ".......XPPPX....",
    ".......XRrRX....",
    ".......XrRnX....",
    ".......XRrnX....",
    ".......XkjkX....",
    "........XXX.....",
    "................",
])

S["pistolet"] = grid([
    "................",
    "................",
    "................",
    "................",
    "..XXXXXXXXXXXX..",
    ".XLWWLLLLLLLLMX.",
    ".XGMMMMMMMMMMDXX",
    ".XDGGcCDDDDDDKX.",
    ".XKjhhhKXXXXXX..",
    "..XjhhjXDKX.....",
    "..XjhhjX.KX.....",
    ".XjhhjXXXX......",
    ".XjhhjX.........",
    "XkjhjX..........",
    "XlkkX...........",
    ".XXX............",
])

S["tromblon"] = grid([
    "................",
    "................",
    "................",
    "............XXX.",
    "...........XcCvX",
    "XXXXXXXXXXXcCCvX",
    "XLLMMMMMMMMMCCvX",
    "XGGDDDDDDDDCCCvX",
    "XKhhhhhKXXXXcvX.",
    "XjhohhjXRX.XXX..",
    ".XjhhhjX.RX.....",
    "..XjhhhXXXX.....",
    "..XkjhhhX.......",
    "...XkjhhX.......",
    "....XkkkX.......",
    ".....XXX........",
])

S["carabine"] = grid([
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....XXXXXXXXXXX",
    "XXXXXXLMMMMMMMMX",
    "XohhhXzGDDDDDDDX",
    "XjhhhjXZzXXXXXX.",
    ".XjhhhXbZXjhhX..",
    "..XkjhXzX.XXX...",
    "...XXXXzX.......",
    "......XzZX......",
    ".......XX.......",
    "................",
    "................",
])

S["balle_pistolet"] = grid([
    "................",
    "................",
    "................",
    "................",
    "....XX....XX....",
    "...XMLX..XMLX...",
    "...XGMX..XGMX...",
    "...XzYX..XzYX...",
    "...XZzX..XZzX...",
    "...XZzX..XZzX...",
    "...XbZX..XbZX...",
    "...XXXX..XXXX...",
    "................",
    "................",
    "................",
    "................",
])

S["cartouche_tromblon"] = grid([
    "................",
    "................",
    "................",
    "...XXXX.........",
    "..XrrRRX..XXXX..",
    "..XrRRnX.XrrRRX.",
    "..XrRRnX.XrRRnX.",
    "..XrRRnX.XrRRnX.",
    "..XRRnnX.XrRRnX.",
    "..XYzzZX.XRRnnX.",
    "..XzZZbX.XYzzZX.",
    "...XXXX..XzZZbX.",
    "..........XXXX..",
    "................",
    "................",
    "................",
])

S["balle_carabine"] = grid([
    "................",
    "................",
    "...X.....X......",
    "..XcX...XcX.....",
    "..XCX...XCX.....",
    "..XYX...XYX.....",
    ".XYzzX.XYzzX....",
    ".XYzZX.XYzZX....",
    ".XYzZX.XYzZX....",
    ".XYzZX.XYzZX....",
    ".XzZbX.XzZbX....",
    ".XzZbX.XzZbX....",
    ".XXXXX.XXXXX....",
    "................",
    "................",
    "................",
])

GRAVURE_BASE = [
    "................",
    "................",
    "................",
    ".....XXXXX......",
    "...XX11112XX....",
    "..X11222222X....",
    "..X1222222223X..",
    ".X12222222223X..",
    ".X12222222233X..",
    ".X22222222333X..",
    "..X222222333X...",
    "..X3223333334X..",
    "...XX333334XX...",
    ".....XXXXXX.....",
    "................",
    "................",
]
GLYPHES = {
    # saignement : goutte rouge
    "saignement": ({(6, 6): "r", (6, 7): "r", (5, 8): "r", (6, 8): "R", (7, 8): "r", (5, 9): "R", (6, 9): "R", (7, 9): "n",
                    (6, 10): "n"}),
    # feu : flamme
    "feu": ({(6, 6): "F", (5, 7): "O", (6, 7): "F", (7, 7): "O", (5, 8): "O", (6, 8): "F", (7, 8): "E", (5, 9): "E",
             (6, 9): "O", (7, 9): "E", (8, 8): "O"}),
    # recul : flèche blanche vers la droite
    "recul": ({(4, 8): "W", (5, 8): "W", (6, 8): "W", (7, 8): "W", (8, 8): "W", (7, 7): "W", (7, 9): "W", (6, 6): "L",
               (6, 10): "L"}),
    # vol de vie : cœur
    "vol_de_vie": ({(5, 7): "r", (7, 7): "r", (4, 8): "r", (5, 8): "r", (6, 8): "r", (7, 8): "r", (8, 8): "r", (5, 9): "R",
                    (6, 9): "r", (7, 9): "R", (6, 10): "n"}),
    # fendoir : arc cyan
    "fendoir": ({(4, 9): "t", (5, 8): "s", (6, 7): "a", (7, 7): "g", (8, 7): "a", (9, 8): "s", (10, 9): "t"}),
    # silence : trait barré violet
    "silence": ({(4, 8): "I", (5, 8): "H", (6, 8): "A", (7, 8): "A", (8, 8): "H", (9, 8): "I", (5, 6): "H", (8, 10): "H",
                 (6, 7): "A", (7, 9): "A"}),
}


def gravure(key):
    rows = [list(r) for r in GRAVURE_BASE]
    for (x, y), ch in GLYPHES[key].items():
        rows[y][x] = ch
    return grid(["".join(r) for r in rows])


for _k in GLYPHES:
    S["gravure_" + _k] = gravure(_k)

S["pierre_amelioration"] = grid([
    "................",
    "................",
    "................",
    ".......XX.......",
    "......XyyX......",
    ".....XyYYzX.....",
    "....XXXYzXXX....",
    ".......Yz.......",
    "...XXXXXXXXXX...",
    "..XAAAAAHHHHHX..",
    "..XAAAAHHHHHIX..",
    "..XHHHHHHIIIIX..",
    "...XXXXXXXXXX...",
    "................",
    "................",
    "................",
])

S["bandage"] = grid([
    "................",
    "................",
    "................",
    "................",
    "....XXXXXXXX....",
    "...XPPPPPPQUX...",
    "..XPPPPrPPQUVX..",
    "..XPPPrrrPQUVX..",
    "..XPPPPrPPQUVX..",
    "..XQQQQQQQUUVX..",
    "..XUUUUUUUUVBX..",
    "...XVVVVVVVBXXX.",
    "....XXXXXXXXPQX.",
    "...........XUVX.",
    "............XX..",
    "................",
])

S["conserve"] = grid([
    "................",
    "................",
    "................",
    ".....XXXXXX.....",
    "....XWLLLLMX....",
    "....XLMMMGDX....",
    "....XXXXXXXX....",
    "....XxxxxxwX....",
    "....XxPPPxwX....",
    "....XxPrPxwX....",
    "....XxPPPxwX....",
    "....XxxxxxwX....",
    "....XXXXXXXX....",
    "....XGMMMDKX....",
    ".....XXXXXX.....",
    "................",
])

S["ferraille"] = grid([
    "................",
    "................",
    "................",
    "................",
    "........XX......",
    "......XXLMX.....",
    "...XX.XMGDX.....",
    "..XLMXXGDXXX....",
    "..XGDXcCXLMDX...",
    "...XXXCvXGDKX...",
    "..XMLGXXXXXX....",
    ".XLGDDKX.XcX....",
    ".XGDKKX..XvX....",
    "..XXXX....X.....",
    "................",
    "................",
])

S["piece_lame"] = grid([
    "................",
    "................",
    "............XX..",
    "...........XWX..",
    "..........XWLX..",
    ".........XWLX...",
    "........XWLGX...",
    ".......XWLGX....",
    "......XWLGX.....",
    ".....XWLGX......",
    "....XLLGX.......",
    "...XDGGX........",
    "...XKDX.........",
    "....XX..........",
    "................",
    "................",
])

S["piece_ressort"] = grid([
    "................",
    "................",
    "................",
    "....XXXXXXX.....",
    "...XLWLLLMGX....",
    "....XXXXXXGX....",
    "...XGMLLLMDX....",
    "...XDXXXXXX.....",
    "...XGMLLLMGX....",
    "....XXXXXXGX....",
    "...XGMLLLMDX....",
    "...XDXXXXXX.....",
    "...XGMMMMDKX....",
    "....XXXXXXX.....",
    "................",
    "................",
])

S["piece_canon"] = grid([
    "................",
    "................",
    "................",
    "................",
    "................",
    "..XXXXXXXXXXXX..",
    ".XLWWLLLLLLLLMX.",
    ".XMLLMMMMMMMMGXX",
    ".XGMMGGGGGGGGDKX",
    ".XKDDKKKKKKKKKXX",
    "..XXXXXXXXXXXX..",
    "................",
    "................",
    "................",
    "................",
    "................",
])

S["piece_mecanisme"] = grid([
    "................",
    "................",
    "......XXXX......",
    "...XX.XLMX.XX...",
    "..XLMXXMGXXMGX..",
    "..XMGLLMMGGDKX..",
    "...XLMMGGDDDX...",
    ".XXXMGGXXDDKXXX.",
    ".XLMMGX..XDKKDX.",
    ".XMGGDX..XKKDKX.",
    ".XXXGDDXXKKDXXX.",
    "...XGDDKKKDKX...",
    "..XMGDDKKDDKKX..",
    "..XGDXXKDXXKKX..",
    "...XX.XKKX.XX...",
    "......XXXX......",
])

# ------------------------------------------------------------------ ids du catalogue

def ids_du_catalogue():
    src = open(CATALOGUE, encoding="utf-8").read()
    ids = re.findall(r'new Def\("(\w+)",\s*Material\.\w+,\s*\w+,\s*"[^"]*",\s*Kind\.\w+,\s*true', src)
    m = re.search(r'ENGRAVINGS\.put\("(\w+)"', src)
    if 'add(new Def("gravure_" + e.getKey()' in src and re.search(r'"gravure_" \+ e\.getKey\(\),[^;]*Kind\.ENGRAVING,\s*true', src):
        ids += ["gravure_" + k for k in re.findall(r'ENGRAVINGS\.put\("(\w+)"', src)]
    return ids


# ------------------------------------------------------------------ modèles

GENERATED = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
HANDHELD = dict(GENERATED)
HANDHELD.update({
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
})
# Armes à feu : dessinées à l'horizontale (canon vers la droite). Même chaîne que `handheld`, mais tournées
# de 45° de plus en 3e personne pour que le canon pointe là où pointe une épée (devant), et presque
# à plat en 1re personne. Valeurs de départ, à régler à l'essai (CONCEPTION 12.2).
GUN = dict(HANDHELD)
GUN.update({
    # Variante 1 validée en jeu par Thibault le 13/11/2026.
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3.5, 0], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3.5, 0], "scale": [0.7, 0.7, 0.7]},
    "firstperson_righthand": {"rotation": [0, 90, 10], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, -90, -10], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
})
# (3e personne, 1re personne, translation 1re personne)
# Liste vide une fois le choix fait (la variante 1 est devenue GUN). Remettre des entrées pour un nouvel essai.
VARIANTES = []
TENUS = {"dague": HANDHELD, "couteau_lancer": HANDHELD, "piece_lame": HANDHELD,
         "pistolet": GUN, "tromblon": GUN, "carabine": GUN}


def model_json(i):
    disp = TENUS.get(i, GENERATED)
    parent = "minecraft:item/handheld" if disp is not GENERATED else "minecraft:item/generated"
    return {"parent": parent, "textures": {"layer0": f"{NS}:item/{i}"}, "display": disp}


def item_json(i):
    return {"model": {"type": "minecraft:model", "model": f"{NS}:item/{i}"}}


def png_bytes(g):
    im = Image.new("RGBA", (16, 16))
    im.putdata([c for row in g for c in row])
    b = io.BytesIO()
    im.save(b, "PNG", optimize=True)
    return b.getvalue(), im


def planche(ids, images, path):
    cell, cols = 112, 7
    rows = (len(ids) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell + 20), (198, 198, 198, 255))
    d = ImageDraw.Draw(sheet)
    d.text((4, 4), f"RefugePack - {len(ids)} objets (x5, fond gris inventaire)", fill=(40, 40, 40, 255))
    for n, i in enumerate(ids):
        x, y = (n % cols) * cell, (n // cols) * cell + 20
        d.rectangle([x + 15, y + 5, x + 15 + 81, y + 5 + 81], fill=(139, 139, 139, 255))
        big = images[i].resize((80, 80), Image.NEAREST)
        sheet.alpha_composite(big, (x + 16, y + 6))
        d.text((x + 4, y + 92), i[:18], fill=(20, 20, 20, 255))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    ref, out = sys.argv[1], sys.argv[2]
    ids = ids_du_catalogue()
    manquants = [i for i in ids if i not in S]
    en_trop = [i for i in S if i not in ids]
    if manquants or en_trop:
        print("ERREUR : dessins manquants", manquants, "/ sans id au catalogue", en_trop)
        sys.exit(1)
    with zipfile.ZipFile(ref) as z:
        meta = json.loads(z.read("pack.mcmeta").decode("utf-8"))
    meta["pack"]["description"] = "Dernier Refuge (EEMICRAFT)"
    files = {"pack.mcmeta": json.dumps(meta, indent=2, ensure_ascii=False).encode("utf-8")}
    images = {}
    for i in ids:
        data, im = png_bytes(S[i])
        images[i] = im
        files[f"assets/{NS}/textures/item/{i}.png"] = data
        files[f"assets/{NS}/models/item/{i}.json"] = json.dumps(model_json(i), indent=1).encode("utf-8")
        files[f"assets/{NS}/items/{i}.json"] = json.dumps(item_json(i), indent=1).encode("utf-8")
    # Variantes de tenue des armes à feu, à essayer en jeu (13/11/2026) : même texture que le pistolet,
    # positions différentes. Données par /give avec item_model refuge:essai/vN.
    for n, (tp, fp, fpt) in enumerate(VARIANTES, 1):
        d = dict(GUN)
        d["thirdperson_righthand"] = {"rotation": tp, "translation": [0, 3.5, 0], "scale": [0.7, 0.7, 0.7]}
        d["thirdperson_lefthand"] = {"rotation": [tp[0], -tp[1], -tp[2]], "translation": [0, 3.5, 0], "scale": [0.7, 0.7, 0.7]}
        d["firstperson_righthand"] = {"rotation": fp, "translation": fpt, "scale": [0.6, 0.6, 0.6]}
        d["firstperson_lefthand"] = {"rotation": [fp[0], -fp[1], -fp[2]], "translation": fpt, "scale": [0.6, 0.6, 0.6]}
        m = {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/pistolet"}, "display": d}
        files[f"assets/{NS}/models/item/essai/v{n}.json"] = json.dumps(m, indent=1).encode("utf-8")
        files[f"assets/{NS}/items/essai/v{n}.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"{NS}:item/essai/v{n}"}}, indent=1).encode("utf-8")
    # Retextures de blocs vanilla (pack chargé seulement dans les mondes du mode) : voir gen_blocs.py et BLOCS.md
    bfiles, prev, cubes, _jar = gen_blocs.build()
    doublons = sorted(set(bfiles) & set(files))
    if doublons:
        print("ERREUR : fichiers des blocs en double avec les objets", doublons)
        sys.exit(1)
    files.update(bfiles)
    gen_blocs.planche_blocs(prev, cubes, os.path.join(RACINE, "apercus", "blocs.png"))
    nb_blocs = len(bfiles)

    # Interfaces (gen_ui.py) : fonds et calques des menus (police refuge:gui), polices de texte refuge:t<y>,
    # objet invisible refuge:gui/vide des cases cliquables ; écrit aussi ui/Fonds.java et apercus/menus.png.
    ufiles, udata = gen_ui.build(gen_blocs.find_vanilla(), images)
    vide = "gui/vide"
    ufiles[f"assets/{NS}/models/item/{vide}.json"] = json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{vide}"}, "display": GENERATED},
        indent=1).encode("utf-8")
    ufiles[f"assets/{NS}/items/{vide}.json"] = json.dumps(item_json(vide), indent=1).encode("utf-8")
    doublons = sorted(set(ufiles) & set(files))
    if doublons:
        print("ERREUR : fichiers des interfaces en double", doublons)
        sys.exit(1)
    files.update(ufiles)
    gen_ui.write_java(udata)
    gen_ui.planche(udata, os.path.join(RACINE, "apercus", "menus.png"))
    nb_ui = len(ufiles)

    # Skins des zombies (gen_zombies.py, ZOMBIES.md) : textures de base des entités + tenues equippable refuge:zombie_*.
    import gen_zombies  # noqa: E402
    zfiles = {}
    gen_zombies.ajouter(zfiles, gen_blocs.find_vanilla())
    doublons = sorted(set(zfiles) & set(files))
    if doublons:
        print("ERREUR : fichiers des zombies en double", doublons)
        sys.exit(1)
    files.update(zfiles)

    icon =images["pistolet"].resize((64, 64), Image.NEAREST)
    bg = Image.new("RGBA", (64, 64), (40, 52, 36, 255))
    bg.alpha_composite(icon)
    b = io.BytesIO()
    bg.save(b, "PNG")
    files["pack.png"] = b.getvalue()

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, files[name])
    sha1 = hashlib.sha1(open(out, "rb").read()).hexdigest()
    with open(os.path.join(ICI, "item_models.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(f"{NS}:{i}" for i in ids + ["gui/vide"]) + "\n")
    planche(ids, images, os.path.join(RACINE, "apercus", "planche.png"))
    print(f"{out} : {len(files)} fichiers, {len(ids)} objets, {nb_blocs} fichiers de blocs vanilla, "
          f"{nb_ui} fichiers d'interface, SHA-1 {sha1}")
    r = subprocess.run([sys.executable, os.path.join(ICI, "verifier_pack.py"), out,
                        "--item-models", os.path.join(ICI, "item_models.txt"), "--strict-display", NS,
                        "--vanilla-overrides"])
    if r.returncode != 0:
        print("ERREUR : la vérification du pack a échoué, pack à ne pas livrer.")
        sys.exit(1)


if __name__ == "__main__":
    main()
