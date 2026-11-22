"""Construit le pack de textures EEMICRAFT : EemicraftPack.zip + son SHA-1 (à mettre dans server.properties).

MODE « BASE + SURCOUCHE » (10/11/2026)
--------------------------------------
D'autres sessions ont ajouté au pack publié des fichiers que ce script ne génère pas (karts, paintball,
casino, Hors Cours, icônes et fonds de menus du lobby, polices...). Pour ne jamais les perdre :
  1. la BASE = le dernier pack publié, rangé dans publie/ (fichier BASE_ZIP ci-dessous). Il est extrait dans
     base/ (re-extrait automatiquement quand le zip change : base/.source_sha1 garde le SHA-1 du zip extrait) ;
  2. tous les fichiers de base/ sont copiés dans le zip final ;
  3. ce que build_pack.py génère est écrit PAR-DESSUS (surcouche). Pour les fichiers assets/*/items/*.json
     qui existent des deux côtés, les modèles range_dispatch / select sont FUSIONNÉS (entrées de la base
     gardées, entrées du script ajoutées ou remplacées au même seuil / même cas) au lieu d'être écrasés.
     Chaque fichier de la base remplacé par un contenu différent est listé à la fin (« modifiés »).
  Ne jamais modifier à la main les fichiers de base/ : ils sont réécrits à chaque changement de BASE_ZIP.

Remettre la base à jour (quand quelqu'un a publié un pack fait ailleurs) :
  télécharger https://eemicraft.strator.gg/pack/EemicraftPack.zip dans publie/ (ex. publie/EemicraftPack-publie.zip),
  mettre son nom dans BASE_ZIP si besoin, relancer `python build_pack.py`, puis vérifier la liste « modifiés »
  et « absents de la base » affichée.

Contenu généré : objets du lobby DeluxeHub (moustache, groupe, boutique), têtes de mouton de Sheep Wars, boss du
skyblock, objets et machines de l'Abysse (art_abysse.py), convoyeur, boutons lobby/precedent|confirmer|annuler,
police eemicraft:icones, sons eemicraft:ui.*, gadgets EemiCore (art_ui.py). Table des icônes et sons : ICONES.md.
Lancer : python build_pack.py   (Pillow requis)
"""
import hashlib
import json
import os
import struct
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
CMD = 1001  # custom_model_data de l'objet « Mini-jeux » de DeluxeHub

# moustache 16x16 : D = brun foncé, M = brun moyen, H = reflet, . = transparent
PIXELS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".D............D.",
    "D..............D",
    "D...DDD..DDD...D",
    "DD.DMMMDDMMMD.DD",
    ".DDMMMMMMMMMMDD.",
    "..DMMHMMMMHMMD..",
    "...DDDD..DDDD...",
    "................",
    "................",
    "................",
    "................",
]
COLORS = {"D": (43, 26, 16, 255), "M": (74, 44, 26, 255), "H": (120, 80, 50, 255), ".": (0, 0, 0, 0)}


def png(pixels, scale=1, colors=COLORS):
    h, w = len(pixels) * scale, len(pixels[0]) * scale
    assert all(len(r) == len(pixels[0]) for r in pixels), "lignes de longueurs differentes"
    rows = []
    for y in range(h):
        row = bytearray([0])
        for x in range(w):
            row += bytes(colors[pixels[y // scale][x // scale]])
        rows.append(bytes(row))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b""))


files = {
    "pack.mcmeta": json.dumps({"pack": {
        "description": "EEMICRAFT",
        "pack_format": 88,
        "min_format": [84, 0],
        "max_format": [200, 0],
    }}, indent=2),
    "pack.png": png(PIXELS, 4),
    # papier : moustache si custom_model_data >= 1001, sinon papier normal
    "assets/minecraft/items/paper.json": json.dumps({"model": {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "entries": [{"threshold": CMD, "model": {"type": "minecraft:model", "model": "eemicraft:item/moustache"}}],
        "fallback": {"type": "minecraft:model", "model": "minecraft:item/paper"},
    }}, indent=2),
    "assets/eemicraft/models/item/moustache.json": json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "eemicraft:item/moustache"},
    }, indent=2),
    "assets/eemicraft/textures/item/moustache.png": png(PIXELS),
}

# Objets du lobby DeluxeHub (29/10/2026) : laisse « Groupe » (custom_model_data 1002) = trois joueurs,
# émeraude « Profil et boutique » (custom_model_data 1003) = bourse de pièces d'or.
GROUP = [
    "................",
    "......DDDD......",
    ".....DSSSSD.....",
    ".DDD.DSSSSD.DDD.",
    "DSSSDDSSSSDDSSSD",
    "DSSSD.DSSD.DSSSD",
    ".DDD.DDDDDD.DDD.",
    ".DGGDDBBBBDDRRD.",
    "DGGGDBBBBBBDRRRD",
    "DGGGDBBWBBBDRRRD",
    "DGGGDBBBBBBDRRRD",
    "DGGGDBBBBBBDRRRD",
    "DDDDDDDDDDDDDDDD",
    "................",
    "................",
    "................",
]
GROUP_COLORS = {"D": (28, 30, 40, 255), "S": (236, 188, 150, 255), "B": (64, 116, 214, 255),
                "W": (150, 190, 255, 255), "G": (76, 170, 86, 255), "R": (206, 66, 66, 255), ".": (0, 0, 0, 0)}
SHOP = [
    "................",
    "......DDDD......",
    ".....DLLLLD.....",
    "......DTTD......",
    ".....DDDDDD.....",
    "....DBBBBBBD....",
    "...DBBBBBBBBD...",
    "..DBBBYYYYBBBD..",
    "..DBBYYOOYYBBD..",
    "..DBBYOYYYYBBD..",
    "..DBBYYOOYYBBD..",
    "..DBBYYYYOYBBD..",
    "..DBBYYOOYYBBD..",
    "...DBBBYYBBBD...",
    "....DDDDDDDD....",
    "................",
]
SHOP_COLORS = {"D": (54, 32, 16, 255), "B": (140, 92, 46, 255), "L": (182, 124, 66, 255), "T": (236, 196, 64, 255),
               "Y": (246, 204, 58, 255), "O": (176, 120, 20, 255), ".": (0, 0, 0, 0)}
for item, cmd, name, pixels, colors in (("lead", 1002, "groupe", GROUP, GROUP_COLORS),
                                        ("emerald", 1003, "boutique", SHOP, SHOP_COLORS)):
    files["assets/minecraft/items/%s.json" % item] = json.dumps({"model": {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "entries": [{"threshold": cmd, "model": {"type": "minecraft:model", "model": "eemicraft:item/" + name}}],
        "fallback": {"type": "minecraft:model", "model": "minecraft:item/" + item},
    }}, indent=2)
    files["assets/eemicraft/models/item/%s.json" % name] = json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "eemicraft:item/" + name},
    }, indent=2)
    files["assets/eemicraft/textures/item/%s.png" % name] = png(pixels, colors=colors)
    with open(os.path.join(HERE, "%s_preview.png" % name), "wb") as f:
        f.write(png(pixels, 16, colors))

# Sheep Wars : chaque mouton en inventaire (EemiSheepWars, composant item_model eemicraft:sheep_<type>)
# est une tête de mouton 3D teintée de sa couleur. Textures vanilla copiées dans vanilla/
# (client 26.3 : entity/sheep/sheep.png et sheep_wool.png, 64x32), recopiées dans item/ car les
# modèles d'objets ne lisent que les atlas blocks et items.
SHEEP_COLORS = {  # couleurs de teinture vanilla (DyeColor.getTextureDiffuseColor)
    "explosif": 0xB02E26, "explosif_fort": 0x474F52, "incendiaire": 0xF9801D, "glace": 0x3AB3DA, "aveuglant": 0x1D1D21,
    "trou_noir": 0x8932B8, "seisme": 0x835432, "soin": 0xF38BAA, "chercheur": 0x80C71F,
    "foudre": 0xFED83D, "intergalactique": 0x3C44AA, "poison": 0x5E7C16, "ender": 0x169C9C,
    "abordage": 0xF9FFFE, "echangeur": 0xC74EBD,
}


def uv(x1, y1, x2, y2):
    # pixels d'une texture 64x32 -> coordonnées 0..16 du modèle
    return [x1 / 4, y1 / 2, x2 / 4, y2 / 2]


def faces(texture, u, tint):
    # boîte de mouton (SheepModel / SheepFurModel) : u = {face: (x1, y1, x2, y2) en pixels}
    out = {}
    for face, box in u.items():
        out[face] = {"uv": uv(*box), "texture": texture}
        if tint:
            out[face]["tintindex"] = 0
    return out


# tête : boîte 6x6x8 en texOffs(0,0) ; laine : boîte 6x6x6 en texOffs(0,0) gonflée de 0,6 ; échelle x2
SHEEP_HEAD = {
    "parent": "minecraft:block/block",
    "textures": {"skin": "eemicraft:item/sheep_skin", "wool": "eemicraft:item/sheep_wool", "particle": "eemicraft:item/sheep_wool"},
    "elements": [
        {"from": [2, 2, 0], "to": [14, 14, 16], "faces": faces("#skin", {
            "north": (8, 8, 14, 14), "south": (22, 8, 28, 14), "west": (0, 8, 8, 14),
            "east": (14, 8, 22, 14), "up": (8, 0, 14, 8), "down": (14, 0, 20, 8)}, False)},
        {"from": [0.8, 0.8, 2.8], "to": [15.2, 15.2, 17.2], "faces": faces("#wool", {
            "north": (6, 6, 12, 12), "south": (18, 6, 24, 12), "west": (0, 6, 6, 12),
            "east": (12, 6, 18, 12), "up": (6, 0, 12, 6), "down": (12, 0, 18, 6)}, True)},
    ],
}
files["assets/eemicraft/models/item/sheep_head.json"] = json.dumps(SHEEP_HEAD, indent=2)
with open(os.path.join(HERE, "vanilla", "sheep.png"), "rb") as f:
    files["assets/eemicraft/textures/item/sheep_skin.png"] = f.read()
with open(os.path.join(HERE, "vanilla", "sheep_wool.png"), "rb") as f:
    files["assets/eemicraft/textures/item/sheep_wool.png"] = f.read()
for key, color in SHEEP_COLORS.items():
    files["assets/eemicraft/items/sheep_%s.json" % key] = json.dumps({"model": {
        "type": "minecraft:model",
        "model": "eemicraft:item/sheep_head",
        "tints": [{"type": "minecraft:constant", "value": color}],
    }}, indent=2)

# Boss de l'arène du skyblock (29/10/2026) : objet porté sur la tête (humanoïdes) ou affiché au-dessus
# du boss (item_display), composant item_model eemicraft:boss_<id>. Cube 16x16x16 : face avant au
# nord (comme la citrouille sur la tête), côtés, dessus, arrière, dessous.
SKIN = (230, 186, 150, 255)
NOSE = (205, 150, 115, 255)
BOSSES = {
    "correcteur": {  # Le Correcteur : prof à lunettes
        "colors": {"H": (88, 58, 34, 255), "S": SKIN, "N": NOSE, "K": (20, 20, 24, 255), "W": (250, 250, 250, 255),
                   "E": (60, 92, 172, 255), "M": (150, 68, 60, 255)},
        "front": ["HHHHHHHHHHHHHHHH", "HHHHHHHHHHHHHHHH", "HHHHHHHHHHHHHHHH", "HSSSSSSSSSSSSSSH",
                  "SSSSSSSSSSSSSSSS", "SKKKKKSSSSKKKKKS", "SKWWWKKKKKKWWWKS", "SKWEWKSSSSKWEWKS",
                  "SKKKKKSSSSKKKKKS", "SSSSSSSSSSSSSSSS", "SSSSSSSNNSSSSSSS", "SSSSSSSSSSSSSSSS",
                  "SSSSSMMMMMMSSSSS", "SSSSSSSSSSSSSSSS", "SSSSSSSSSSSSSSSS", "SSSSSSSSSSSSSSSS"],
        "side": ["H" * 16] * 5 + ["SSSSSSSSSSSSSSSS"] * 11,
        "back": ["H" * 16] * 13 + ["S" * 16] * 3,
        "top": ["H" * 16] * 16, "bottom": ["S" * 16] * 16,
    },
    "president": {  # Le Président du jury : perruque de juge
        "colors": {"W": (244, 244, 240, 255), "G": (196, 196, 190, 255), "S": SKIN, "N": NOSE, "B": (110, 100, 90, 255),
                   "K": (20, 20, 24, 255), "M": (140, 64, 58, 255)},
        "front": ["WWWWWWWWWWWWWWWW", "WGWWGWWGWWGWWGWW", "WWWWWWWWWWWWWWWW", "WWSSSSSSSSSSSSWW",
                  "WWSSSSSSSSSSSSWW", "WWSBBBSSSSBBBSWW", "WWSWKSSSSSSWKSWW", "WWSSSSSSSSSSSSWW",
                  "GWSSSSSNNSSSSSWG", "WWSSSSSSSSSSSSWW", "GWSSSMMMMMMSSSWG", "WWSSSSSSSSSSSSWW",
                  "GWWSSSSSSSSSSWWG", "WWGWSSSSSSSSWGWW", "WGWWGWWWWWWGWWGW", "WWWWGWWGWWGWWWWW"],
        "side": ["WWGWWGWWGWWGWWGW", "WGWWGWWGWWGWWGWW", "GWWGWWGWWGWWGWWG"] * 5 + ["WWGWWGWWGWWGWWGW"],
        "back": ["WGWWGWWGWWGWWGWW", "WWGWWGWWGWWGWWGW", "GWWGWWGWWGWWGWWG"] * 5 + ["WGWWGWWGWWGWWGWW"],
        "top": ["WWGWWGWWGWWGWWGW", "GWWGWWGWWGWWGWWG"] * 8, "bottom": ["S" * 16] * 16,
    },
    "examinateur": {  # L'Examinateur : lunettes rectangulaires, air sévère
        "colors": {"D": (34, 30, 30, 255), "S": (214, 170, 136, 255), "N": (190, 140, 108, 255), "K": (18, 18, 20, 255),
                   "C": (170, 220, 236, 255)},
        "front": ["DDDDDDDDDDDDDDDD", "DDDDDDDDDDDDDDDD", "DDDDDDDDDDDDDDDD", "DDSSSSSSSSSSSSDD",
                  "SSSSSSSSSSSSSSSS", "SKKKKKKSSKKKKKKS", "SKCCCCKKKKCCCCKS", "SKCKCCKSSKCCKCKS",
                  "SKKKKKKSSKKKKKKS", "SSSSSSSNNSSSSSSS", "SSSSSSSSSSSSSSSS", "SSSSKKKKKKKKSSSS",
                  "SSSKSSSSSSSSKSSS", "SSSSSSSSSSSSSSSS", "SSSSSSSSSSSSSSSS", "SSSSSSSSSSSSSSSS"],
        "side": ["D" * 16] * 4 + ["S" * 16] * 12,
        "back": ["D" * 16] * 12 + ["S" * 16] * 4,
        "top": ["D" * 16] * 16, "bottom": ["S" * 16] * 16,
    },
    "directeur": {  # Le Directeur des études : cheveux gris, moustache
        "colors": {"G": (150, 150, 150, 255), "L": (196, 196, 196, 255), "S": SKIN, "N": NOSE, "B": (96, 96, 96, 255),
                   "W": (250, 250, 250, 255), "K": (20, 20, 24, 255), "D": (110, 110, 110, 255), "M": (150, 68, 60, 255)},
        "front": ["GGGGGGGGGGGGGGGG", "GGGGGGGGGGGGGGGG", "GGGLLGGGGGGLLGGG", "GGSSSSSSSSSSSSGG",
                  "GSSSSSSSSSSSSSSG", "GSSBBBSSSSBBBSSG", "SSSWKSSSSSSWKSSS", "SSSSSSSSSSSSSSSS",
                  "SSSSSSSNNSSSSSSS", "SSSSSSSNNSSSSSSS", "SSSDDDDDDDDDDSSS", "SSDDDDSSSSDDDDSS",
                  "SSSSSSMMMMSSSSSS", "SSSSSSSSSSSSSSSS", "SSSSSSSSSSSSSSSS", "SSSSSSSSSSSSSSSS"],
        "side": ["GGGGGGGGGGGGGGGG", "GGGLGGGGLGGGGLGG"] * 3 + ["S" * 16] * 10,
        "back": ["GGGGGGGGGGGGGGGG", "GGLGGGGLGGGGLGGG"] * 6 + ["S" * 16] * 4,
        "top": ["GGGGGGGGGGGGGGGG", "GLGGGGLGGGGLGGGG"] * 8, "bottom": ["S" * 16] * 16,
    },
    "bug": {  # Le Bug en prod : insecte vert aux yeux rouges
        "colors": {"K": (28, 30, 34, 255), "G": (84, 200, 74, 255), "D": (36, 110, 40, 255), "R": (230, 40, 40, 255)},
        "front": ["KKKKKKKKKKKKKKKK", "KKKGKKKKKKKKGKKK", "KKKKGKKKKKKGKKKK", "KKKKKGGGGGGKKKKK",
                  "KKKKGGGGGGGGKKKK", "KKKGRRGGGGRRGKKK", "KGKGRRGGGGRRGKGK", "KKGGGGGGGGGGGGKK",
                  "KKKGDDDDDDDDGKKK", "KGGGDGGGGGGDGGGK", "KKKGDDDDDDDDGKKK", "KKGGGGGGGGGGGGKK",
                  "KGKGDDDDDDDDGKGK", "KKKKGGGGGGGGKKKK", "KKKKKGGGGGGKKKKK", "KKKKKKKKKKKKKKKK"],
    },
    "machine": {  # La Machine à café déréglée
        "colors": {"L": (190, 190, 196, 255), "G": (120, 122, 130, 255), "K": (26, 26, 30, 255), "O": (255, 150, 30, 255),
                   "R": (230, 40, 40, 255), "B": (110, 62, 28, 255), "W": (245, 245, 245, 255)},
        "front": ["LLLLLLLLLLLLLLLL", "LGGGGGGGGGGGGGGL", "LGKKKKKKKKKKKKGL", "LGKOOOKKKKRKKKGL",
                  "LGKKKKKKKKKKKKGL", "LGGGGGGGGGGGGGGL", "LGGGGGKKKKGGGGGL", "LGGGGGGKKGGGGGGL",
                  "LGGKKKKKKKKKKGGL", "LGGKKKKBBKKKKGGL", "LGGKKKWWWWKKKGGL", "LGGKKKWBBWKKKGGL",
                  "LGGKKKWWWWKKKGGL", "LGGKKKKKKKKKKGGL", "LGGGGGGGGGGGGGGL", "LLLLLLLLLLLLLLLL"],
        "top": ["LLLLLLLLLLLLLLLL"] + ["LGGGGGGGGGGGGGGL"] * 14 + ["LLLLLLLLLLLLLLLL"],
    },
    "wifi": {  # Le Wi-Fi de l'école : routeur
        "colors": {"N": (24, 34, 70, 255), "A": (60, 220, 240, 255), "K": (20, 20, 24, 255), "G": (60, 230, 80, 255),
                   "R": (240, 50, 50, 255)},
        "front": ["NNNNNNNNNNNNNNNN", "NNNNNNNNNNNNNNNN", "NNNAAAAAAAAAANNN", "NNANNNNNNNNNNANN",
                  "NANNNAAAAAANNNAN", "NNNNANNNNNNANNNN", "NNNANNAAAANNANNN", "NNNNNANNNNANNNNN",
                  "NNNNNNNAANNNNNNN", "NNNNNNNAANNNNNNN", "NNNNNNNNNNNNNNNN", "NNNNNNNNNNNNNNNN",
                  "KKKKKKKKKKKKKKKK", "KGKGKGKKKKKKKRKK", "KKKKKKKKKKKKKKKK", "KKKKKKKKKKKKKKKK"],
    },
}
for boss, d in BOSSES.items():
    tex = {}
    for face in ("front", "side", "back", "top", "bottom"):
        pixels = d.get(face) or d.get("side") or d["front"]
        assert len(pixels) == 16 and all(len(r) == 16 for r in pixels), "%s/%s : 16x16 attendu" % (boss, face)
        files["assets/eemicraft/textures/item/boss_%s_%s.png" % (boss, face)] = png(pixels, colors=d["colors"])
        tex[face] = "eemicraft:item/boss_%s_%s" % (boss, face)
    # les objets (bug, machine, wifi) ont la face avant sur les 4 côtés : vus de partout pareil
    back = tex["front"] if "back" not in d and "side" not in d else tex["back"]
    files["assets/eemicraft/models/item/boss_%s.json" % boss] = json.dumps({
        "parent": "minecraft:block/block",
        "textures": {"front": tex["front"], "side": tex["side"], "back": back, "top": tex["top"],
                     "bottom": tex["bottom"], "particle": tex["front"]},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#back"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"}}}],
    }, indent=2)
    files["assets/eemicraft/items/boss_%s.json" % boss] = json.dumps(
        {"model": {"type": "minecraft:model", "model": "eemicraft:item/boss_%s" % boss}}, indent=2)
    with open(os.path.join(HERE, "boss_%s_preview.png" % boss), "wb") as f:
        f.write(png(d["front"], 12, d["colors"]))

# ---------------------------------------------------------------------------------------------------------
# L'Abysse : machines (cubes, aussi affichées par-dessus le bloc posé grâce à une item_display) et objets,
# composant item_model eemicraft:abysse/<id> posé par EemiAbysse. Dessins dans art_abysse.py.
# Liste des ids : ../EemiAbysse/ids_modeles.txt (« id matériau catégorie ») ; chaque id doit avoir un dessin.
import art_abysse as A
from art_abysse import (blank, shade, noise, rect, circle, panel, STEEL_D, STEEL, STEEL_L, RIVET)


def png_rgba(grid, scale=1):
    """grid : lignes de tuples (r, g, b, a)."""
    h, w = len(grid) * scale, len(grid[0]) * scale
    rows = []
    for y in range(h):
        row = bytearray([0])
        for x in range(w):
            row += bytes(grid[y // scale][x // scale])
        rows.append(bytes(row))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b""))


def flat_item(path_id, grid):
    """Objet plat : texture + modèle item/generated + définition items/<path_id>.json."""
    files["assets/eemicraft/textures/item/%s.png" % path_id] = png_rgba(grid)
    files["assets/eemicraft/models/item/%s.json" % path_id] = json.dumps({
        "parent": "minecraft:item/generated", "textures": {"layer0": "eemicraft:item/%s" % path_id}}, indent=2)
    files["assets/eemicraft/items/%s.json" % path_id] = json.dumps(
        {"model": {"type": "minecraft:model", "model": "eemicraft:item/%s" % path_id}}, indent=2)


ABYSSE_GRIDS = {}  # id -> grille affichée sur la planche (face avant pour les machines)
for i, mid in enumerate(A.MACHINES):
    faces_tex = A.machine_faces(mid, i)
    for face, grid in faces_tex.items():
        files["assets/eemicraft/textures/item/abysse/%s_%s.png" % (mid, face)] = png_rgba(grid)
    t = lambda f, mid=mid: "eemicraft:item/abysse/%s_%s" % (mid, f)
    files["assets/eemicraft/models/item/abysse/%s.json" % mid] = json.dumps({
        "parent": "minecraft:block/cube",
        "textures": {"north": t("front"), "south": t("front"), "east": t("side"), "west": t("side"),
                     "up": t("top"), "down": t("bottom"), "particle": t("side")},
    }, indent=2)
    files["assets/eemicraft/items/abysse/%s.json" % mid] = json.dumps(
        {"model": {"type": "minecraft:model", "model": "eemicraft:item/abysse/%s" % mid}}, indent=2)
    ABYSSE_GRIDS[mid] = faces_tex["front"]
for f in os.listdir(HERE):  # anciens aperçus machine par machine : remplacés par la planche
    if f.startswith("abysse_m_") and f.endswith("_preview.png"):
        os.remove(os.path.join(HERE, f))

for iid, fn in A.ITEMS.items():
    grid = fn()
    assert len(grid) == 16 and all(len(r) == 16 for r in grid), iid
    flat_item("abysse/" + iid, grid)
    ABYSSE_GRIDS[iid] = grid

# contrôle : tous les ids de l'Abysse ont un modèle (le convoyeur est un bloc, plus bas)
_ids_file = os.path.join(HERE, "..", "EemiAbysse", "ids_modeles.txt")
ABYSSE_IDS = []
if os.path.exists(_ids_file):
    with open(_ids_file, encoding="utf-8") as f:
        ABYSSE_IDS = [l.split()[0] for l in f if l.strip()]
    _missing = [i for i in ABYSSE_IDS if i not in ABYSSE_GRIDS and i != "convoyeur"]
    assert not _missing, "ids de l'Abysse sans dessin : %s" % _missing
    _extra = [i for i in ABYSSE_GRIDS if i not in ABYSSE_IDS]
    if _extra:
        print("Abysse : dessins sans id dans ids_modeles.txt :", _extra)

# ---- convoyeur de l'Abysse : le bloc posé reste la terracotta émaillée magenta VANILLA (son modèle n'est plus
# remplacé : les autres terracottas émaillées magenta du serveur, maps de jeux comprises, gardent leur aspect).
# EemiAbysse (classe Belts) pose sur chaque convoyeur une item_display du modèle eemicraft:block/convoyeur
# (objet eemicraft:abysse/convoyeur, aussi porté par l'objet en main), tournée selon le sens du transport.
# Dessus = tapis à chevrons, flèche vers le haut de la texture (= nord du modèle) ; côtés = châssis à rouleaux.
BELT, BELT_L, CHEVRON = (38, 38, 44, 255), (54, 54, 62, 255), (244, 192, 40, 255)


def belt_top():
    g = blank()
    for y in range(16):
        for x in range(16):
            if x in (0, 15):
                g[y][x] = STEEL_D
            elif x in (1, 14):
                g[y][x] = shade(STEEL_L, 0.9 + 0.2 * noise(x, y, 7))
            else:
                g[y][x] = BELT_L if y % 4 == 3 else shade(BELT, 0.9 + 0.2 * noise(x, y, 8))
    for y0 in (2, 9):  # deux chevrons qui pointent vers le haut
        for dx in range(4):
            for t in (0, 1):
                y = y0 + dx + t
                if y < 16:
                    g[y][7 - dx] = CHEVRON
                    g[y][8 + dx] = CHEVRON
    return g


def belt_side():
    g = panel(91)
    rect(g, 1, 5, 14, 10, STEEL_D)
    for cx in (4, 8, 12):
        circle(g, cx - 0.5, 7.5, 1.8, lambda x, y, d: RIVET if d < 0.9 else STEEL_L)
    rect(g, 1, 4, 14, 4, BELT)
    return g


for face, grid in (("top", belt_top()), ("side", belt_side()), ("bottom", panel(92))):
    files["assets/eemicraft/textures/block/convoyeur_%s.png" % face] = png_rgba(grid)
_bt = lambda f: "eemicraft:block/convoyeur_%s" % f
files["assets/eemicraft/models/block/convoyeur.json"] = json.dumps({
    "parent": "minecraft:block/block",
    "textures": {"particle": _bt("side"), "top": _bt("top"), "side": _bt("side"), "bottom": _bt("bottom")},
    "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
        "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
        "north": {"uv": [0, 0, 16, 16], "texture": "#side"},
        "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
        "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
        "east": {"uv": [0, 0, 16, 16], "texture": "#side"}}}],
}, indent=2)
files["assets/eemicraft/items/abysse/convoyeur.json"] = json.dumps(
    {"model": {"type": "minecraft:model", "model": "eemicraft:block/convoyeur"}}, indent=2)
with open(os.path.join(HERE, "convoyeur_preview.png"), "wb") as f:
    f.write(png_rgba(belt_top(), 12))


# planche d'aperçu de tous les objets de l'Abysse (dans l'ordre de ids_modeles.txt, 16 px x 4)
ABYSSE_GRIDS["convoyeur"] = belt_top()
_order = [i for i in ABYSSE_IDS if i in ABYSSE_GRIDS] + [i for i in ABYSSE_GRIDS if i not in ABYSSE_IDS]
_cols = 12
sheet = [[(40, 40, 40, 255)] * (_cols * 18) for _ in range(((len(_order) + _cols - 1) // _cols) * 18)]
for n, iid in enumerate(_order):
    grid = ABYSSE_GRIDS[iid]
    ox, oy = (n % _cols) * 18 + 1, (n // _cols) * 18 + 1
    for y in range(16):
        for x in range(16):
            if grid[y][x][3]:
                sheet[oy + y][ox + x] = grid[y][x]
with open(os.path.join(HERE, "abysse_planche.png"), "wb") as f:
    f.write(png_rgba(sheet, 4))
with open(os.path.join(HERE, "abysse_planche.txt"), "w", encoding="utf-8") as f:
    f.write("Ordre de abysse_planche.png (%d par ligne, de gauche à droite) :\n" % _cols)
    for r in range(0, len(_order), _cols):
        f.write("%d. %s\n" % (r // _cols + 1, ", ".join(_order[r:r + _cols])))
print("Abysse :", len(ABYSSE_GRIDS), "objets et machines habillés")

# ---------------------------------------------------------------------------------------------------------
# BASE : dernier pack publié (voir l'en-tête). Lue ici parce que les boutons ci-dessous partent de ses textures.
BASE_ZIP = os.path.join(HERE, "publie", "EemicraftPack-lobby.zip")
BASE_DIR = os.path.join(HERE, "base")


def load_base():
    with open(BASE_ZIP, "rb") as f:
        zsha = hashlib.sha1(f.read()).hexdigest()
    marker = os.path.join(BASE_DIR, ".source_sha1")
    current = open(marker).read().strip() if os.path.exists(marker) else None
    if current != zsha:
        import shutil
        if os.path.isdir(BASE_DIR):
            shutil.rmtree(BASE_DIR)
        os.makedirs(BASE_DIR)
        with zipfile.ZipFile(BASE_ZIP) as z:
            for n in z.namelist():
                if n.endswith("/"):
                    continue
                assert ".." not in n.split("/") and not n.startswith("/"), n
                dest = os.path.join(BASE_DIR, *n.split("/"))
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                with open(dest, "wb") as out_f:
                    out_f.write(z.read(n))
        with open(marker, "w") as f:
            f.write(zsha + "\n" + os.path.basename(BASE_ZIP) + "\n")
        print("base/ extraite de", os.path.basename(BASE_ZIP), zsha)
    base = {}
    for root, _, names in os.walk(BASE_DIR):
        for n in names:
            if n == ".source_sha1":
                continue
            p = os.path.join(root, n)
            with open(p, "rb") as f:
                base[os.path.relpath(p, BASE_DIR).replace(os.sep, "/")] = f.read()
    return base, zsha


base, base_sha = load_base()

# ---------------------------------------------------------------------------------------------------------
# interface, police d'icônes, sons, gadgets (art_ui.py)
import art_ui as U

# DA des menus du lobby (art_lobby.py, 10/11/2026) : les 67 icônes eemicraft:lobby/<nom> et les 15 fonds de la base
# sont redessinés et ÉCRASENT ceux de la base aux mêmes chemins ; ajoutés : lobby/precedent|confirmer|annuler
# (remplacent les boutons de art_ui.ui_buttons, plus appelé), lobby/etat_* (cases d'état de la boutique) et le fond
# bg_confirm (glyphe U+E31F de la police eemicraft:lobby). Aperçus : apercus_lobby/.
import art_lobby as L

_LOBBY_ICONS = L.icons()
_base_icons = sorted(p.rsplit("/", 1)[1][:-4] for p in base if p.startswith("assets/eemicraft/textures/item/lobby/"))
assert set(_base_icons) <= set(_LOBBY_ICONS), "icônes de la base non redessinées : %s" % (set(_base_icons) - set(_LOBBY_ICONS))
assert set(_LOBBY_ICONS) - set(_base_icons) == set(L.NEW_ICONS), set(_LOBBY_ICONS) - set(_base_icons)
for name, im in _LOBBY_ICONS.items():
    path = "lobby/" + name
    files["assets/eemicraft/textures/item/%s.png" % path] = L.png_bytes(im)
    if name in L.NEW_ICONS:
        files["assets/eemicraft/models/item/%s.json" % path] = json.dumps({
            "parent": "minecraft:item/generated", "textures": {"layer0": "eemicraft:item/" + path}}, indent=2)
        files["assets/eemicraft/items/%s.json" % path] = json.dumps(
            {"model": {"type": "minecraft:model", "model": "eemicraft:item/" + path}}, indent=2)

_LOBBY_BGS = L.backgrounds()
_font = json.loads(base["assets/eemicraft/font/lobby.json"].decode("utf-8"))
_bmp = {p["file"]: p["chars"] for p in _font["providers"] if p.get("type") == "bitmap"}
for i, name in enumerate(L.BG_NAMES):
    fpath = "eemicraft:font/lobby/bg_%s.png" % name
    ch = chr(0xE310 + i)
    if fpath in _bmp:
        assert _bmp[fpath] == [ch], (name, _bmp[fpath])  # même glyphe que la base
    else:
        _font["providers"].append({"type": "bitmap", "file": fpath, "ascent": 13, "height": 256, "chars": [ch]})
    files["assets/eemicraft/textures/font/lobby/bg_%s.png" % name] = L.png_bytes(_LOBBY_BGS[name])
assert all(("eemicraft:font/lobby/bg_%s.png" % n) in [p.get("file") for p in _font["providers"]] for n in L.BG_NAMES)
files["assets/eemicraft/font/lobby.json"] = json.dumps(_font, indent=2, ensure_ascii=True)

_AP = os.path.join(HERE, "apercus_lobby")
os.makedirs(_AP, exist_ok=True)
for name in L.BG_NAMES:
    L.preview(name, _LOBBY_BGS[name], _LOBBY_ICONS).save(os.path.join(_AP, "menu_%s.png" % name))
L.planche(_LOBBY_ICONS).save(os.path.join(_AP, "icones.png"))
print("Lobby :", len(_LOBBY_ICONS), "icônes,", len(_LOBBY_BGS), "fonds ; aperçus dans", _AP)

_prev = U.Image.new("RGBA", (6 * 68, 64), (60, 60, 60, 255))
for k, n in enumerate(("retour", "fermer", "suivant", "precedent", "confirmer", "annuler")):
    _prev.alpha_composite(_LOBBY_ICONS[n], (k * 68, 0))
_prev.resize((_prev.width * 2, _prev.height * 2), U.Image.NEAREST).save(os.path.join(HERE, "ui_boutons_preview.png"))

_font_png, _font_json, ICON_TABLE, _font_img = U.icon_font()
taken = set()
for p, data in base.items():
    if "/font/" in p and p.endswith(".json"):
        for prov in json.loads(data.decode("utf-8")).get("providers", []):
            for s in prov.get("chars", []) + list(prov.get("advances", {}).keys()):
                taken.update(s)
_clash = [c for c, _, _ in ICON_TABLE if c in taken]
assert not _clash, "caractères d'icônes déjà pris dans la base : %s" % [hex(ord(c)) for c in _clash]
files["assets/eemicraft/textures/font/icones.png"] = _font_png
files["assets/eemicraft/font/icones.json"] = _font_json
_font_img.resize((_font_img.width * 8, _font_img.height * 8), U.Image.NEAREST).save(
    os.path.join(HERE, "icones_preview.png"))

files["assets/eemicraft/sounds.json"] = json.dumps(U.SOUNDS, indent=2)

_gprev = [[(40, 40, 40, 255)] * (len(U.GADGETS) * 18) for _ in range(18)]
for n, (gid, fn) in enumerate(U.GADGETS.items()):
    grid = fn()
    flat_item("gadget/" + gid, grid)
    for y in range(16):
        for x in range(16):
            if grid[y][x][3]:
                _gprev[1 + y][n * 18 + 1 + x] = grid[y][x]
with open(os.path.join(HERE, "gadgets_preview.png"), "wb") as f:
    f.write(png_rgba(_gprev, 8))

# ---------------------------------------------------------------------------------------------------------
# assemblage : base + surcouche, fusion des définitions d'objets (range_dispatch / select)


def merge_model(b, g):
    """Fusionne deux modèles d'objet. Garde les entrées de la base, ajoute/remplace celles du script."""
    if not (isinstance(b, dict) and isinstance(g, dict)) or b.get("type") != g.get("type"):
        return g
    t = b.get("type", "").replace("minecraft:", "")
    if t == "range_dispatch" and b.get("property") == g.get("property") and b.get("index", 0) == g.get("index", 0):
        entries = {e["threshold"]: e for e in b.get("entries", [])}
        for e in g.get("entries", []):
            entries[e["threshold"]] = e
        out = dict(b)
        out["entries"] = [entries[k] for k in sorted(entries)]
        if "fallback" in g:
            out["fallback"] = merge_model(b.get("fallback"), g["fallback"]) if "fallback" in b else g["fallback"]
        return out
    if t == "select" and b.get("property") == g.get("property"):
        cases = []
        for c in b.get("cases", []):
            cases.append(c)
        gwhen = [json.dumps(c["when"], sort_keys=True) for c in g.get("cases", [])]
        cases = [c for c in cases if json.dumps(c["when"], sort_keys=True) not in gwhen] + g.get("cases", [])
        out = dict(b)
        out["cases"] = cases
        if "fallback" in g:
            out["fallback"] = g["fallback"]
        return out
    return g


final = dict(base)
modified, added = [], []
for name, data in files.items():
    data = data if isinstance(data, bytes) else data.encode("utf-8")
    if name in base and "/items/" in name and name.endswith(".json"):
        bj, gj = json.loads(base[name].decode("utf-8")), json.loads(data.decode("utf-8"))
        merged = dict(bj)
        merged["model"] = merge_model(bj.get("model"), gj.get("model"))
        for k, v in gj.items():
            if k != "model":
                merged[k] = v
        data = json.dumps(merged, indent=2).encode("utf-8") if merged != bj else base[name]
    if name in base:
        if base[name] != data:
            modified.append(name)
    else:
        added.append(name)
    final[name] = data
assert set(base) <= set(final)

# vérifications : JSON valides, modèles et textures eemicraft référencés présents
_bad = []
for name, data in final.items():
    if name.endswith((".json", ".mcmeta")):
        try:
            json.loads(data.decode("utf-8"))
        except ValueError as e:
            _bad.append("JSON invalide %s : %s" % (name, e))


def _refs(o, key=None):
    if isinstance(o, dict):
        for k, v in o.items():
            yield from _refs(v, k)
    elif isinstance(o, list):
        for v in o:
            yield from _refs(v, key)
    elif isinstance(o, str) and o.startswith("eemicraft:"):
        yield key, o


for name, data in final.items():
    if not name.endswith(".json") or not name.startswith("assets/"):
        continue
    kind = name.split("/")[2]
    try:
        js = json.loads(data.decode("utf-8"))
    except ValueError:
        continue
    for key, ref in _refs(js):
        rid = ref.split(":", 1)[1]
        if kind == "items" and key == "model":
            want = "assets/eemicraft/models/%s.json" % rid
        elif kind == "models" and (key in ("parent",) ):
            want = "assets/eemicraft/models/%s.json" % rid
        elif kind == "models":
            want = "assets/eemicraft/textures/%s.png" % rid
        elif kind == "font" and key == "file":
            want = "assets/eemicraft/textures/%s" % rid
        else:
            continue
        if want not in final:
            _bad.append("%s -> %s introuvable" % (name, ref))
for b in _bad[:30]:
    print("ATTENTION", b)
print("vérification :", len(_bad), "problème(s)")

out = os.path.join(HERE, "EemicraftPack.zip")
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for name in sorted(final):
        info = zipfile.ZipInfo(name, date_time=(2026, 10, 26, 0, 0, 0))  # date fixe : même contenu = même SHA-1
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, final[name])
with open(out, "rb") as f:
    sha1 = hashlib.sha1(f.read()).hexdigest()
with open(os.path.join(HERE, "moustache_preview.png"), "wb") as f:
    f.write(png(PIXELS, 16))
with open(os.path.join(HERE, "build_rapport.txt"), "w", encoding="utf-8") as f:
    f.write("base %s (%s), %d fichiers\nzip %s, %d fichiers\n" % (os.path.basename(BASE_ZIP), base_sha, len(base),
                                                                  sha1, len(final)))
    f.write("\n== modifiés par rapport à la base (%d)\n" % len(modified) + "\n".join(sorted(modified)))
    f.write("\n\n== ajoutés (%d)\n" % len(added) + "\n".join(sorted(added)) + "\n")
print("base", len(base), "fichiers ; zip", len(final), "fichiers ;", len(modified), "modifiés,", len(added), "ajoutés")
print(out)
print("sha1", sha1)
