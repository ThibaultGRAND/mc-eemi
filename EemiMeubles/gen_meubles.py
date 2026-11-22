"""Meubles 3D de l'EEMI (d'après les visuels de la page Notion « Pièces », EemiHorsCours/references/salles/).

Écrit :
  - les modèles et textures du pack (assets/eemicraft/{items,models,textures}/meubles/...) ;
  - le catalogue du plugin : src/main/resources/meubles.yml ;
  - des aperçus en perspective : apercus/<id>.png et apercus/planche.png.
Puis, si on donne un pack publié et une sortie, ajoute les fichiers au pack (rien d'autre ne change).

Usage : python -I gen_meubles.py [pack_publie.zip sortie.zip]

Repère d'un meuble : pixels (16 = 1 bloc) à partir du coin nord-ouest du bloc d'ancrage, y 0 = sol.
L'AVANT du meuble est vers +z (sud) ; une chaise a son dossier côté z petit et on s'y assoit face au sud.
Places assises : (x, y, z) en pixels (y = hauteur de l'assise).
Les meubles larges s'étendent vers +x (cases 0, 1...). Objet mural : dos contre z = 0.
"""
import hashlib
import json
import math
import os
import shutil
import struct
import sys
import zipfile
import zlib

ICI = os.path.dirname(os.path.abspath(__file__))
NS = "assets/eemicraft"

# ---------------------------------------------------------------- couleurs
C = {
    "navy": (38, 48, 96), "navy2": (52, 64, 120), "orange": (232, 140, 42), "orange2": (245, 165, 70),
    "mustard": (214, 160, 42), "white": (236, 236, 232), "white2": (214, 214, 210), "lgray": (190, 190, 192),
    "gray": (128, 128, 134), "dgray": (72, 72, 78), "black": (30, 30, 34), "black2": (46, 46, 52),
    "wood": (196, 146, 90), "woodd": (150, 104, 60), "yellow": (238, 190, 48), "red": (196, 40, 40),
    "green": (36, 150, 72), "purple": (104, 58, 150), "card": (196, 152, 96), "card2": (176, 132, 78),
    "leaf": (58, 128, 58), "leaf2": (84, 158, 74), "metal": (172, 176, 182), "blue": (40, 70, 140),
    "cream": (240, 232, 210), "brown": (110, 70, 40),
    "steel": (104, 122, 134), "steel2": (126, 146, 158), "steeld": (62, 74, 84), "steell": (168, 186, 196),
    "rust": (128, 74, 44),
}
PAL = list(C)  # indice = position dans la palette 16x16


def png(w, h, px):
    """px[y][x] = (r, g, b, a)."""
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def palette_png():
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for i, name in enumerate(PAL):
        px[i // 16][i % 16] = C[name] + (255,)
    return png(16, 16, px)


# ---------------------------------------------------------------- textures de face (16x16 ou 16x32)
def tex_screen():
    px = [[(24, 26, 34, 255)] * 16 for _ in range(16)]
    for i in range(6):
        for j in range(16):
            if 0 <= j - i * 2 < 3 and i + j < 16:
                px[j][i + 9] = (48, 54, 70, 255)  # reflet en diagonale
    return png(16, 16, px)


def tex_rack():
    px = [[(28, 28, 32, 255)] * 16 for _ in range(16)]
    for y in range(1, 16, 3):
        for x in range(1, 15):
            px[y][x] = (52, 52, 58, 255)
        px[y + 1 if y + 1 < 16 else y][3] = (60, 220, 90, 255)
        px[y + 1 if y + 1 < 16 else y][5] = (240, 170, 40, 255) if y % 2 else (60, 220, 90, 255)
        for x in range(8, 14, 2):
            px[y + 1 if y + 1 < 16 else y][x] = (90, 90, 96, 255)
    return png(16, 16, px)


def tex_micro():
    px = [[(30, 30, 34, 255)] * 16 for _ in range(16)]
    for y in range(3, 13):
        for x in range(2, 11):
            px[y][x] = (40, 46, 56, 255) if (x + y) % 4 else (56, 64, 78, 255)
    for y in (4, 7, 10):
        for x in range(12, 15):
            px[y][x] = (150, 150, 155, 255)
    px[4][13] = (90, 220, 120, 255)
    return png(16, 16, px)


def tex_exit():
    g, w = (36, 150, 72, 255), (245, 245, 240, 255)
    rows = [
        "................",
        "................",
        "...........w....",
        "..........www...",
        "...........w....",
        "..w.......www...",
        ".ww......w.w.w..",
        "wwwwwww.w..ww...",
        ".ww........w.w..",
        "..w.......w...w.",
        "..........w....w",
        "................",
    ]
    px = [[g] * 16 for _ in range(16)]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "w":
                px[y + 2][x] = w
    return png(16, 16, px)


FONT = {  # 3x5
    "e": ["111", "101", "111", "100", "111"], "m": ["000", "111", "111", "101", "101"],
    "i": ["010", "000", "010", "010", "010"], "D": ["110", "101", "101", "101", "110"],
    "A": ["010", "101", "111", "101", "101"], "R": ["110", "101", "110", "101", "101"],
    "E": ["111", "100", "110", "100", "111"], "T": ["111", "010", "010", "010", "010"],
    "O": ["111", "101", "101", "101", "111"], "B": ["110", "101", "110", "101", "110"],
}


def text(px, s, x, y, col):
    for ch in s:
        g = FONT.get(ch)
        if g:
            for dy, row in enumerate(g):
                for dx, b in enumerate(row):
                    if b == "1" and 0 <= y + dy < len(px) and 0 <= x + dx < len(px[0]):
                        px[y + dy][x + dx] = col
        x += 4


def tex_kakemono():
    px = []
    for y in range(32):
        k = y / 31
        px.append([(int(90 + 40 * k), int(50 + 10 * k), int(140 + 30 * k), 255)] * 16)
    text(px, "eemi", 1, 2, (255, 255, 255, 255))
    # silhouette sombre
    for y in range(10, 22):
        for x in range(4, 12):
            if (x - 8) ** 2 / 9 + (y - 15) ** 2 / 30 < 1:
                px[y][x] = (40, 20, 70, 255)
    for y in range(9, 12):
        for x in range(6, 10):
            px[y][x] = (210, 40, 70, 255)
    text(px, "DARE", 0, 23, (255, 255, 255, 255))
    text(px, "TO", 4, 28 - 1, (255, 255, 255, 255)) if False else None
    for x in range(2, 14):
        px[30][x] = (255, 255, 255, 255)
    return png(16, 32, px)


def tex_board():
    px = [[C["mustard"] + (255,)] * 16 for _ in range(16)]
    for y in range(2, 16, 4):
        for x in range(2, 16, 4):
            px[y][x] = (255, 236, 200, 255)
            if x + 1 < 16:
                px[y][x + 1] = (255, 236, 200, 255)
    return png(16, 16, px)


def tex_poster():
    px = [[(28, 40, 96, 255)] * 16 for _ in range(16)]
    text(px, "BE", 4, 9, (255, 255, 255, 255))
    for y in range(2, 7):
        for x in range(4, 12):
            px[y][x] = (255, 255, 255, 255) if (x + y) % 2 else (230, 230, 230, 255)
    return png(16, 16, px)


TEXTURES = {"palette": palette_png, "ecran": tex_screen, "baie": tex_rack, "micro": tex_micro,
            "sortie": tex_exit, "kakemono": tex_kakemono, "tableau": tex_board, "affiche": tex_poster}

# ---------------------------------------------------------------- meubles
# b(x0,y0,z0,x1,y1,z1,couleur, avant=texture_de_la_face_sud)


def b(x0, y0, z0, x1, y1, z1, col, front=None):
    return (x0, y0, z0, x1, y1, z1, col, front)


def legs(x0, z0, x1, z1, h, col, t=1):
    return [b(x0, 0, z0, x0 + t, h, z0 + t, col), b(x1 - t, 0, z0, x1, h, z0 + t, col),
            b(x0, 0, z1 - t, x0 + t, h, z1, col), b(x1 - t, 0, z1 - t, x1, h, z1, col)]


M = {}


def meuble(mid, nom, cat, boxes, cells=((0, 0, 1),), seats=(), mur=False, surface=False):
    M[mid] = dict(nom=nom, cat=cat, boxes=list(boxes), cells=list(cells), seats=list(seats), mur=mur,
                  surface=surface)


# Couloir et coin détente
meuble("pouf_bleu", "Pouf bleu", "couloir",
       [b(2, 0, 2, 14, 7, 14, "navy"), b(3, 7, 3, 13, 8, 13, "navy2")], cells=(), seats=[(8, 8, 8)])
meuble("pouf_orange", "Pouf orange", "couloir",
       [b(2, 0, 2, 14, 7, 14, "orange"), b(3, 7, 3, 13, 8, 13, "orange2")], cells=(), seats=[(8, 8, 8)])
meuble("banquette_bleue", "Banquette bleue", "couloir",
       [b(0, 0, 2, 16, 7, 15, "navy"), b(0, 7, 2, 16, 14, 5, "navy2")], cells=(), seats=[(8, 7, 9)])
meuble("banquette_orange", "Banquette orange", "couloir",
       [b(0, 0, 2, 16, 7, 15, "orange"), b(0, 7, 2, 16, 14, 5, "navy")], cells=(), seats=[(8, 7, 9)])
for mid, nom, col in (("fauteuil_jaune", "Fauteuil jaune", "mustard"), ("fauteuil_bleu", "Fauteuil bleu", "navy")):
    meuble(mid, nom, "couloir",
           legs(3, 4, 13, 14, 4, "woodd") + [b(2, 4, 3, 14, 7, 14, col), b(2, 7, 2, 14, 16, 5, col),
                                             b(1, 6, 4, 3, 11, 13, col), b(13, 6, 4, 15, 11, 13, col)],
           cells=(), seats=[(8, 7, 9)])
meuble("table_basse", "Table basse ronde", "couloir",
       [b(7, 0, 7, 9, 7, 9, "white2"), b(5, 0, 5, 11, 1, 11, "white2"), b(3, 7, 3, 13, 8, 13, "white")])
meuble("tele_murale", "Télé murale", "couloir",
       [b(0, 4, 0, 32, 20, 2, "black", "ecran"), b(29, 5, 2, 31, 6, 3, "red")], cells=(), mur=True)
meuble("kakemono", "Kakémono eemi", "couloir",
       [b(3, 0, 6, 13, 1, 10, "metal"), b(3, 1, 7, 13, 31, 8, "purple", "kakemono")], cells=((0, 0, 2),))
meuble("panneau_sortie", "Panneau sortie", "couloir",
       [b(2, 6, 0, 14, 12, 1, "green", "sortie")], cells=(), mur=True)
meuble("extincteur", "Extincteur", "couloir",
       [b(6, 0, 6, 10, 11, 10, "red"), b(7, 11, 7, 9, 13, 9, "black"), b(9, 6, 9, 10, 11, 11, "black")],
       cells=())
meuble("plante", "Plante en pot", "couloir",
       [b(4, 0, 4, 12, 6, 12, "dgray"), b(3, 6, 3, 13, 12, 13, "leaf"), b(5, 12, 5, 11, 17, 11, "leaf2"),
        b(2, 9, 6, 4, 14, 9, "leaf2"), b(12, 8, 7, 14, 13, 10, "leaf")])
meuble("affiche", "Affiche (QR / tri)", "couloir",
       [b(3, 4, 0, 13, 18, 1, "navy", "affiche")], cells=(), mur=True)

# Bureaux
meuble("bureau", "Bureau noir (2 places)", "bureaux",
       legs(1, 1, 31, 15, 12, "white") + [b(0, 12, 0, 32, 13, 16, "black"), b(0, 13, 0, 32, 20, 1, "yellow")],
       cells=((0, 0, 1), (1, 0, 1)))
meuble("chaise_bureau", "Chaise de bureau", "bureaux",
       [b(5, 0, 5, 11, 1, 11, "black"), b(7, 1, 7, 9, 6, 9, "metal"), b(3, 6, 3, 13, 8, 13, "black"),
        b(3, 8, 2, 13, 19, 4, "dgray"), b(2, 10, 5, 3, 11, 12, "black"), b(13, 10, 5, 14, 11, 12, "black")],
       cells=(), seats=[(8, 8, 8)])
meuble("ecran_pc", "Écran d'ordinateur (sur un bureau)", "bureaux",
       [b(6, -3, 6, 10, -2, 10, "black"), b(7, -2, 7, 9, 2, 8, "black"), b(2, 1, 7, 14, 9, 8, "black", "ecran")],
       cells=(), surface=True)
meuble("portable", "Ordinateur portable (sur une table)", "bureaux",
       [b(3, -3, 4, 13, -2, 12, "dgray"), b(3, -2, 11, 13, 4, 12, "black", "ecran")], cells=(), surface=True)
meuble("armoire_basse", "Meuble bas blanc", "bureaux",
       [b(0, 0, 3, 16, 12, 16, "white"), b(0, 12, 3, 16, 13, 16, "white2"), b(7, 4, 16, 9, 8, 16.5, "metal")])
meuble("portemanteau", "Portemanteau", "bureaux",
       [b(5, 0, 5, 11, 1, 11, "dgray"), b(7, 1, 7, 9, 28, 9, "dgray"), b(5, 25, 7, 11, 26, 9, "dgray"),
        b(5, 12, 5, 11, 25, 11, "navy")], cells=((0, 0, 2),))
meuble("cartons", "Cartons", "bureaux",
       [b(1, 0, 1, 15, 8, 15, "card"), b(2, 8, 3, 13, 15, 13, "card2"), b(7, 15, 3, 8, 15.5, 13, "lgray")])
meuble("machine_cafe", "Machine à café (sur un meuble)", "cuisine",
       [b(4, -3, 4, 12, 7, 13, "black"), b(6, 0, 10, 10, 2, 13, "metal"), b(7, -3, 10, 9, -1, 12, "white")],
       cells=(), surface=True)

# Salles de cours et réunion
meuble("table_cours", "Table de cours (2 places)", "salles",
       [b(2, 0, 4, 4, 1, 12, "lgray"), b(28, 0, 4, 30, 1, 12, "lgray"), b(2, 1, 7, 3, 12, 9, "lgray"),
        b(29, 1, 7, 30, 12, 9, "lgray"), b(0, 12, 2, 32, 13, 14, "white")], cells=((0, 0, 1), (1, 0, 1)))
meuble("chaise_cours", "Chaise de cours", "salles",
       legs(3, 4, 13, 13, 7, "gray") + [b(3, 7, 3, 13, 8, 13, "gray"), b(3, 8, 2, 13, 16, 3, "white2")],
       cells=(), seats=[(8, 8, 8)])
meuble("ecran_pied", "Grand écran sur pied", "salles",
       [b(8, 0, 5, 24, 1, 11, "black"), b(15, 1, 7, 17, 12, 9, "black"), b(0, 12, 7, 32, 30, 9, "black", "ecran")],
       cells=((0, 0, 2), (1, 0, 2)))
meuble("table_reunion", "Table de réunion", "salles",
       legs(1, 2, 31, 14, 12, "white") + [b(0, 12, 1, 32, 13, 15, "black"), b(14, 13, 7, 18, 13.3, 9, "dgray")],
       cells=((0, 0, 1), (1, 0, 1)))
meuble("tabouret_haut", "Chaise haute", "salles",
       legs(3, 4, 13, 13, 12, "white") + [b(3, 5, 4, 13, 6, 5, "white"), b(3, 12, 3, 13, 13, 13, "mustard"),
                                          b(3, 13, 2, 13, 20, 4, "white")], cells=(), seats=[(8, 13, 8)])
meuble("tableau_jaune", "Tableau jaune mobile", "salles",
       [b(1, 0, 6, 3, 1, 10, "white"), b(13, 0, 6, 15, 1, 10, "white"), b(1, 1, 7, 2, 4, 9, "white"),
        b(14, 1, 7, 15, 4, 9, "white"), b(1, 4, 7, 15, 30, 9, "mustard", "tableau")], cells=((0, 0, 2),))

# Cuisine et salle de pause
meuble("table_haute", "Table haute ronde", "cuisine",
       [b(4, 0, 4, 12, 1, 12, "white"), b(7, 1, 7, 9, 17, 9, "white"), b(1, 17, 1, 15, 18, 15, "wood")])
meuble("table_cuisine", "Table en bois (2 places)", "cuisine",
       [b(5, 0, 5, 11, 1, 11, "white"), b(21, 0, 5, 27, 1, 11, "white"), b(7, 1, 7, 9, 12, 9, "white"),
        b(23, 1, 7, 25, 12, 9, "white"), b(0, 12, 1, 32, 13, 15, "wood")], cells=((0, 0, 1), (1, 0, 1)))
meuble("chaise_blanche", "Chaise blanche", "cuisine",
       legs(4, 4, 12, 12, 7, "metal") + [b(3, 7, 3, 13, 9, 13, "white"), b(3, 9, 2, 13, 16, 4, "white"),
                                         b(2, 9, 4, 3, 13, 11, "white"), b(13, 9, 4, 14, 13, 11, "white")],
       cells=(), seats=[(8, 9, 8)])
meuble("canape_orange", "Canapé orange", "cuisine",
       [b(0, 0, 2, 32, 7, 15, "orange"), b(0, 7, 1, 32, 16, 5, "orange2"), b(0, 7, 5, 3, 11, 15, "orange"),
        b(29, 7, 5, 32, 11, 15, "orange")], cells=(), seats=[(8, 7, 9), (24, 7, 9)])
meuble("frigo", "Frigo", "cuisine",
       [b(1, 0, 1, 15, 30, 15, "white"), b(1, 19, 15, 15, 20, 15.5, "lgray"), b(12, 12, 15, 13, 17, 16, "metal"),
        b(12, 22, 15, 13, 27, 16, "metal")], cells=((0, 0, 2),))
meuble("micro_ondes", "Micro-ondes (sur un meuble)", "cuisine",
       [b(1, -3, 3, 15, 6, 14, "black", "micro")], cells=(), surface=True)
meuble("meuble_cuisine", "Meuble de cuisine noir", "cuisine",
       [b(0, 0, 2, 16, 13, 16, "black"), b(0, 13, 1, 16, 14, 16, "wood"), b(7, 6, 16, 9, 7, 16.5, "wood")])
meuble("evier", "Évier", "cuisine",
       [b(0, 0, 2, 16, 13, 16, "black"), b(0, 13, 1, 16, 14, 16, "wood"), b(3, 13.2, 5, 13, 14.1, 13, "metal"),
        b(7, 14, 3, 9, 20, 5, "black2"), b(7, 18, 5, 9, 19, 9, "black2")])
meuble("poubelles", "Poubelles de tri", "cuisine",
       [b(1, 0, 4, 7, 10, 12, "black"), b(1, 10, 4, 7, 11, 12, "yellow"), b(9, 0, 4, 15, 10, 12, "black"),
        b(9, 10, 4, 15, 11, 12, "green")])

# Reprographie
meuble("imprimante", "Grande imprimante", "reprographie",
       [b(1, 0, 2, 15, 13, 15, "lgray"), b(1, 4, 15, 15, 5, 15.3, "gray"), b(1, 8, 15, 15, 9, 15.3, "gray"),
        b(1, 13, 2, 15, 18, 15, "blue"), b(2, 18, 3, 14, 19, 14, "blue"), b(3, 18, 11, 9, 20, 14, "dgray")])
meuble("baie_reseau", "Baie réseau murale", "reprographie",
       [b(1, 4, 0, 15, 22, 7, "black", "baie")], cells=(), mur=True)

# Hors Cours (niveau 1) : étagère, armoire métallique (fermée / ouverte), cadenas et digicode à poser
# sur l'armoire, tableau blanc, mannequin de bureau
meuble("horscours_etagere", "Étagère métallique", "horscours",
       legs(0, 3, 16, 16, 31, "gray") + [b(1, 1, 4, 15, 2, 15, "lgray"), b(1, 11, 4, 15, 12, 15, "lgray"),
                                        b(1, 21, 4, 15, 22, 15, "lgray"), b(1, 30, 4, 15, 31, 15, "lgray"),
                                        b(1, 2, 3, 15, 30, 4, "dgray"),
                                        b(2, 12, 6, 6, 16, 12, "card"), b(9, 22, 5, 14, 25, 13, "card2"),
                                        b(10, 2, 7, 14, 6, 13, "white2")],
       cells=((0, 0, 2),))
def _casier(open_):
    """Vestiaire métallique de 2,4 blocs (38 px) : socle, caisse, chapeau, deux portes à panneaux,
    aérations en haut et en bas, porte-étiquettes, poignées. L'avant est vers +z."""
    bx = [b(0, 0, 2, 16, 2.5, 16, "black2"),                      # socle
          b(0, 2.5, 2, 16, 37, 3, "steel"),                      # fond
          b(0, 2.5, 2, 1, 37, 16, "steel"), b(15, 2.5, 2, 16, 37, 16, "steel"),   # côtés
          b(-0.5, 37, 1.5, 16.5, 38.5, 16.5, "steeld"),          # chapeau
          b(0.5, 36.2, 16, 15.5, 37, 16.4, "steeld")]            # bandeau du haut
    if open_:
        bx += [b(1, 2.5, 3, 15, 3.2, 15, "steeld"), b(1, 15.5, 3, 15, 16.5, 15, "steell"),
               b(1, 26, 3, 15, 27, 15, "steell"), b(2, 31, 4, 14, 31.6, 5, "metal"),   # étagères, tringle
               b(-7, 2.8, 15, 1, 36, 16, "steel2"), b(15, 2.8, 15, 23, 36, 16, "steel2"),   # portes ouvertes
               b(-6, 30, 16, -2, 34, 16.3, "steeld"), b(17, 30, 16, 21, 34, 16.3, "steeld"),
               b(3, 3.2, 4, 7, 10, 8, "rust"), b(9, 3.2, 5, 13, 6, 11, "dgray")]          # vieux sac, boîte
        return bx
    for x0, x1 in ((1, 7.9), (8.1, 15)):
        bx += [b(x0, 2.8, 15, x1, 36, 16, "steel2"),                     # porte
               b(x0 + 0.8, 4, 16, x1 - 0.8, 4.4, 16.2, "steeld"),         # panneau : bord bas
               b(x0 + 0.8, 34.4, 16, x1 - 0.8, 34.8, 16.2, "steeld")]     # bord haut
        for y in (30, 31.4, 32.8):                                        # aérations hautes
            bx.append(b(x0 + 1.4, y, 16, x1 - 1.4, y + 0.6, 16.3, "steeld"))
        for y in (6, 7.4):                                                # aérations basses
            bx.append(b(x0 + 1.4, y, 16, x1 - 1.4, y + 0.6, 16.3, "steeld"))
        bx.append(b(x0 + 1.5, 25.5, 16, x1 - 1.5, 27.5, 16.25, "cream"))  # porte-étiquette
    bx += [b(7.9, 2.8, 16, 8.1, 36, 16.2, "black"),                       # jointure
           b(6.2, 16, 16, 6.8, 22, 16.8, "steell"), b(9.2, 16, 16, 9.8, 22, 16.8, "steell"),   # poignées
           b(2.5, 26, 16.25, 5, 26.4, 16.3, "dgray"), b(10.6, 26, 16.25, 13.5, 26.4, 16.3, "dgray")]
    return bx


meuble("horscours_armoire", "Vestiaire métallique", "horscours", _casier(False), cells=((0, 0, 2),))
meuble("horscours_armoire_ouverte", "Vestiaire métallique (ouvert)", "horscours", _casier(True), cells=((0, 0, 2),))
meuble("horscours_cadenas", "Cadenas (sur un vestiaire)", "horscours",
       [b(5.4, 17.2, 16.8, 10.6, 19.2, 17.4, "metal"),                       # moraillon sur les poignées
        b(6.6, 13.6, 16.9, 9.4, 17, 18.1, "yellow"), b(6.9, 17, 17.3, 7.5, 19.4, 17.7, "metal"),
        b(8.5, 17, 17.3, 9.1, 19.4, 17.7, "metal"), b(6.9, 19, 17.3, 9.1, 19.6, 17.7, "metal"),
        b(7.6, 14.2, 18.1, 8.4, 15.4, 18.2, "black")], cells=())
meuble("horscours_digicode", "Digicode (sur un vestiaire)", "horscours",
       [b(10.4, 17, 16, 14.2, 24, 16.7, "black"), b(10.9, 22.4, 16.7, 13.7, 23.4, 16.8, "green"),
        b(10.9, 20.6, 16.7, 11.9, 21.6, 16.8, "metal"), b(12.7, 20.6, 16.7, 13.7, 21.6, 16.8, "metal"),
        b(10.9, 19, 16.7, 11.9, 20, 16.8, "metal"), b(12.7, 19, 16.7, 13.7, 20, 16.8, "metal"),
        b(10.9, 17.4, 16.7, 11.9, 18.4, 16.8, "red"), b(12.7, 17.4, 16.7, 13.7, 18.4, 16.8, "metal")],
       cells=())
meuble("horscours_tableau_blanc", "Tableau blanc mural", "horscours",
       [b(0, 2, 0, 32, 22, 1, "lgray"), b(1, 3, 0.5, 31, 21, 1.2, "white"), b(4, 2, 1, 28, 2.6, 2.5, "lgray"),
        b(6, 2.6, 1.2, 9, 3.2, 1.8, "red"), b(11, 2.6, 1.2, 14, 3.2, 1.8, "blue")], cells=(), mur=True)
meuble("horscours_mannequin", "Mannequin de bureau", "horscours",
       [b(5, 0, 6, 11, 1, 10, "black"), b(7.5, 1, 7.5, 8.5, 12, 8.5, "metal"),
        b(5, 12, 6, 11, 24, 10, "dgray"), b(3, 13, 6.5, 5, 23, 9.5, "dgray"), b(11, 13, 6.5, 13, 23, 9.5, "dgray"),
        b(7, 18, 9.8, 9, 23, 10.2, "white"), b(7.5, 24, 7, 8.5, 25, 9, "white2"),
        b(5.5, 25, 5.5, 10.5, 31, 10.5, "white2")], cells=())


# ---------------------------------------------------------------- modèles
def bounds(boxes):
    xs = [v for bx in boxes for v in (bx[0], bx[3])]
    ys = [v for bx in boxes for v in (bx[1], bx[4])]
    zs = [v for bx in boxes for v in (bx[2], bx[5])]
    return min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)


def scale_for(boxes):
    x0, x1, y0, y1, z0, z1 = bounds(boxes)
    for k in (1, 2, 3, 4):
        ok = all(-16 <= 8 + (v - 8) / k <= 32 for v in (x0, x1, y0 + 8, y1 + 8, z0, z1))
        # y : le centre de l'entité est au milieu du bloc, donc y modèle = 8 + (y - 8) / k
        ok = ok and all(-16 <= 8 + (v - 8) / k <= 32 for v in (y0, y1))
        if ok:
            return k
    raise SystemExit("meuble trop grand")


def uv_color(name):
    i = PAL.index(name)
    u, v = i % 16, i // 16
    return [u + 0.25, v + 0.25, u + 0.75, v + 0.75]


def model(m):
    k = scale_for(m["boxes"])
    tex = {"p": "eemicraft:item/meubles/palette", "particle": "eemicraft:item/meubles/palette"}
    els = []
    for (x0, y0, z0, x1, y1, z1, col, front) in m["boxes"]:
        f = lambda v: round(8 + (v - 8) / k, 4)
        faces = {d: {"uv": uv_color(col), "texture": "#p"} for d in ("north", "east", "south", "west", "up", "down")}
        if front:
            tex[front] = f"eemicraft:item/meubles/{front}"
            faces["south"] = {"uv": [0, 0, 16, 16], "texture": "#" + front}
        els.append({"from": [f(x0), f(y0), f(z0)], "to": [f(x1), f(y1), f(z1)], "faces": faces})
    x0, x1, y0, y1, z0, z1 = bounds(m["boxes"])
    size = max(x1 - x0, y1 - y0, z1 - z0) / 16 * k
    gs = round(0.62 / max(size / k, 1) * 1, 3)
    return k, {"textures": tex, "elements": els,
               "display": {"gui": {"rotation": [30, 225, 0], "scale": [gs, gs, gs]},
                           "ground": {"scale": [0.3, 0.3, 0.3]},
                           "thirdperson_righthand": {"rotation": [75, 45, 0], "scale": [0.3, 0.3, 0.3]},
                           "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.35, 0.35, 0.35]}}}


# ---------------------------------------------------------------- aperçus (perspective isométrique)
def render(m, S=6):
    boxes = m["boxes"]
    x0, x1, y0, y1, z0, z1 = bounds(boxes)
    cw, ch = 1.0, 0.5
    pts = [((x - z) * cw * S, (-(y) + (x + z) * ch) * S) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    minx, maxx = min(p[0] for p in pts) - 4, max(p[0] for p in pts) + 4
    miny, maxy = min(p[1] for p in pts) - 4, max(p[1] for p in pts) + 4
    W, H = int(maxx - minx) + 1, int(maxy - miny) + 1
    img = [[(255, 255, 255, 0)] * W for _ in range(H)]

    def P(x, y, z):
        return ((x - z) * cw * S - minx, (-y + (x + z) * ch) * S - miny)

    def poly(pts, col):
        ys = [p[1] for p in pts]
        for yy in range(max(0, int(min(ys))), min(H, int(max(ys)) + 1)):
            xs = []
            n = len(pts)
            for i in range(n):
                (ax, ay), (bx, by) = pts[i], pts[(i + 1) % n]
                if (ay <= yy + 0.5 < by) or (by <= yy + 0.5 < ay):
                    xs.append(ax + (yy + 0.5 - ay) * (bx - ax) / (by - ay))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                for xx in range(max(0, int(xs[i] + 0.5)), min(W, int(xs[i + 1] + 0.5))):
                    img[yy][xx] = col

    def shade(c, f):
        return tuple(min(255, int(v * f)) for v in c) + (255,)

    for (ax, ay, az, bx, by, bz, col, front) in sorted(boxes, key=lambda bb: (bb[0] + bb[2] + bb[1] * 0.5)):
        c = C[col]
        poly([P(ax, by, az), P(bx, by, az), P(bx, by, bz), P(ax, by, bz)], shade(c, 1.05))
        poly([P(ax, ay, bz), P(bx, ay, bz), P(bx, by, bz), P(ax, by, bz)], shade(c, 0.85 if not front else 0.55))
        poly([P(bx, ay, az), P(bx, ay, bz), P(bx, by, bz), P(bx, by, az)], shade(c, 0.68))
    return W, H, img


def sheet(images, cols=6, cell=200):
    rows = (len(images) + cols - 1) // cols
    W, H = cols * cell, rows * cell
    out = [[(242, 242, 245, 255)] * W for _ in range(H)]
    for i, (w, h, img) in enumerate(images):
        ox = (i % cols) * cell + max(0, (cell - w) // 2)
        oy = (i // cols) * cell + max(0, (cell - h) // 2)
        for y in range(min(h, cell)):
            for x in range(min(w, cell)):
                p = img[y][x]
                if p[3]:
                    out[oy + y][ox + x] = p
    return png(W, H, out)


# ---------------------------------------------------------------- sortie
def main():
    files = {}
    for name, fn in TEXTURES.items():
        files[f"{NS}/textures/item/meubles/{name}.png"] = fn()
    catalog = ["# Genere par gen_meubles.py : ne pas modifier a la main.", "meubles:"]
    previews = []
    os.makedirs(os.path.join(ICI, "apercus"), exist_ok=True)
    for mid, m in M.items():
        k, mdl = model(m)
        files[f"{NS}/models/meubles/{mid}.json"] = json.dumps(mdl, indent=1).encode()
        files[f"{NS}/items/meubles/{mid}.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"eemicraft:meubles/{mid}"}}, indent=1).encode()
        x0, x1, y0, y1, z0, z1 = bounds(m["boxes"])
        catalog.append(f"  {mid}:")
        catalog.append(f"    nom: \"{m['nom']}\"")
        catalog.append(f"    categorie: {m['cat']}")
        catalog.append(f"    echelle: {k}")
        catalog.append(f"    hauteur: {round(max(y1, 4) / 16, 3)}")
        catalog.append(f"    largeur: {max(1, math.ceil(x1 / 16))}")
        catalog.append(f"    mur: {str(m['mur']).lower()}")
        catalog.append(f"    surface: {str(m['surface']).lower()}")
        catalog.append("    cases: [" + ", ".join(f"\"{a},{c},{h}\"" for a, c, h in m["cells"]) + "]")
        catalog.append("    places: [" + ", ".join(f"\"{a},{c},{h}\"" for a, c, h in m["seats"]) + "]")
        w, h, img = render(m)
        open(os.path.join(ICI, "apercus", f"{mid}.png"), "wb").write(png(w, h, img))
        previews.append((w, h, img))
    open(os.path.join(ICI, "apercus", "planche.png"), "wb").write(sheet(previews))
    res = os.path.join(ICI, "src", "main", "resources")
    os.makedirs(res, exist_ok=True)
    open(os.path.join(res, "meubles.yml"), "w", encoding="utf-8").write("\n".join(catalog) + "\n")

    trace = os.path.join(ICI, "assets_generes")
    shutil.rmtree(trace, ignore_errors=True)
    for path, data in files.items():
        p = os.path.join(trace, *path.split("/"))
        os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "wb").write(data)
    print(len(M), "meubles,", len(files), "fichiers de pack")

    if len(sys.argv) >= 3:
        src, out = sys.argv[1], sys.argv[2]
        zin = zipfile.ZipFile(src)
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in zin.infolist():
                if info.filename in files or "/meubles/" in info.filename:
                    continue
                zout.writestr(info, zin.read(info.filename))
            for path in sorted(files):
                zi = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
                zi.compress_type = zipfile.ZIP_DEFLATED
                zout.writestr(zi, files[path])
        print("SHA-1", hashlib.sha1(open(out, "rb").read()).hexdigest())


if __name__ == "__main__":
    main()
