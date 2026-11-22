"""Dessins des objets et machines de l'Abysse (importé par build_pack.py).

Chaque objet est une grille 16x16 de tuples RGBA. Les ids viennent de EemiAbysse/ids_modeles.txt
(« id matériau catégorie »). build_pack.py vérifie que chaque id de ce fichier a un dessin ici.
"""
import math

T = (0, 0, 0, 0)


def blank(c=T):
    return [[c for _ in range(16)] for _ in range(16)]


def rgba(c):
    return tuple(c) if len(c) == 4 else tuple(c) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


def noise(x, y, salt=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFF) / 255.0


def rect(g, x1, y1, x2, y2, c):
    for y in range(max(0, y1), min(15, y2) + 1):
        for x in range(max(0, x1), min(15, x2) + 1):
            g[y][x] = rgba(c)


def circle(g, cx, cy, r, color_at):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                c = color_at(x, y, d)
                if c:
                    g[y][x] = rgba(c)


def draw(rows, pal, g=None):
    """rows : jusqu'à 16 lignes d'au plus 16 caractères ; '.' = transparent (ou rien changé si g est donné)."""
    assert len(rows) <= 16, rows
    g = g or blank()
    for y, r in enumerate(rows):
        assert len(r) <= 16, r
        for x, ch in enumerate(r):
            if ch != ".":
                g[y][x] = rgba(pal[ch])
    return g


def P(pal, **extra):
    """Palette métal (sombre, moyen, clair, reflet) -> dictionnaire D/M/L/H (+ extra)."""
    d = dict(zip("DMLH", (rgba(c) for c in pal)))
    d.update({k: rgba(v) for k, v in extra.items()})
    return d


def brighter(pal, f=1.18):
    return tuple(tuple(min(255, int(v * f + 12)) for v in c) for c in pal)


# ---------------------------------------------------------------------------------------------------------
# palettes : (sombre, moyen, clair, reflet). Chaque matière a la sienne pour qu'on les distingue d'un coup d'œil.
PAL = {
    "cuivre": ((110, 50, 30), (180, 90, 50), (225, 130, 80), (255, 190, 140)),
    "fer": ((80, 80, 85), (160, 160, 165), (210, 210, 215), (245, 245, 250)),
    "etain": ((70, 92, 118), (150, 176, 200), (204, 222, 240), (250, 252, 255)),      # gris bleuté
    "argent": ((112, 104, 132), (196, 192, 214), (234, 232, 246), (255, 255, 255)),   # blanc lilas, très brillant
    "bronze": ((100, 60, 20), (175, 120, 45), (215, 160, 70), (250, 215, 130)),
    "acier": ((22, 25, 32), (70, 78, 90), (110, 120, 135), (170, 180, 195)),
    "cobalt": ((16, 34, 104), (36, 76, 190), (76, 128, 236), (170, 206, 255)),       # bleu roi
    "enderite": ((12, 62, 56), (24, 124, 108), (58, 192, 166), (170, 252, 228)),      # vert d'ender
    "neant": ((14, 8, 24), (52, 24, 84), (102, 52, 158), (214, 156, 255)),            # violet noir
    "cristal": ((116, 26, 96), (198, 76, 176), (240, 148, 222), (255, 232, 250)),     # rose magenta
    "soufre": ((136, 104, 8), (216, 184, 28), (248, 226, 76), (255, 250, 180)),
    "charbon": ((14, 14, 16), (36, 36, 40), (64, 64, 70), (118, 118, 128)),
    "spore": ((70, 110, 20), (150, 200, 40), (210, 245, 90), (250, 255, 200)),        # jaune-vert lumineux
    "pierre": ((58, 58, 58), (108, 108, 108), (146, 146, 146), (190, 190, 190)),
    "abyssal": ((10, 18, 30), (26, 50, 70), (48, 88, 116), (110, 226, 236)),          # bleu des profondeurs
    "forge": ((32, 20, 18), (84, 52, 40), (150, 82, 40), (255, 172, 60)),
    "glace": ((40, 82, 112), (90, 160, 202), (160, 216, 242), (236, 250, 255)),
    "fer_brut": ((120, 90, 70), (188, 148, 118), (220, 186, 156), (246, 222, 200)),  # minerai de fer : beige
    "or": ((120, 80, 10), (210, 160, 30), (245, 210, 70), (255, 245, 170)),
}
WOOD, WOOD_D = (140, 100, 55), (80, 55, 30)
STEEL_D, STEEL, STEEL_L, RIVET = (34, 36, 44, 255), (78, 82, 92, 255), (108, 113, 124, 255), (166, 170, 178, 255)

# ---------------------------------------------------------------------------------------------------------
# formes : D sombre (contour), M moyen, L clair, H reflet, A accent (pierre / lueur), W bois, K bois sombre
INGOT = ["", "", "", "",
         ".....DDDDDDDD", "....DHHLLLLLMD", "...DHLLLLLLMMD", "..DLLLLLLLLMMD",
         "..DMMMMMMMMMMD", "..DMMMMMMMMMDD", "..DDDDDDDDDDD"]
POWDER = ["", "", "", "", "", "", "......DD", ".....DLLD",
          "....DLHLMD", "...DLLLLMMD", "..DLLHLLLMMD", ".DLLLLLLLMMMD",
          ".DMMMMMMMMMMD", "..DDDDDDDDDD"]
LUMP = ["", "", "", ".....DDDD", "....DLLHLD", "...DLLLLLMDD", "..DLHLLLLLLMD", "..DLLLLMLLLMD",
        "..DMLLLLLLMMD", "...DMMLLLMMMD", "....DMMMMMMD", ".....DDDDDD"]
ORE = ["", "", "....dddddd", "...dsHLsLLdd", "..dsLLMssHLMd", "..dsLMssLLMsd", ".dssssHLLMsssd", ".dsHLsLMMssLsd",
       ".dsLMssssHLMsd", "..dssLLsLLMssd", "..dsHLMsLMsdd", "...dsMssssd", "....dddddd"]
PICKAXE = ["", "...DDDDDD", "..DHLLLLLDD", "...DDDDMLLLD", "........DMLD", ".......DKDMLD", "......DWKD.DLD",
           ".....DWKD..DMD", "....DWKD....DD", "...DWKD", "..DWKD", ".DWKD", "DWKD", "DDD"]
SWORD = ["", "............DDD", "...........DLHD", "..........DLHMD", ".........DLHMD", "........DLHMD",
         ".......DLHMD", "..DD..DLHMD", "..DAD.DHMD", "...DADHMD", "....DAMD", "....DWDAD", "...DWD.DAD",
         ".DDWD...DD", ".DKD", ".DD"]
HELMET = ["", "", "", "....DDDDDDDD", "...DLHHLLLLMD", "..DLHLLAALLLMD", "..DLLLLLLLLLMD", "..DLMDDDDDDMMD",
          "..DLD......DMD", "..DMD......DMD", "..DDD......DDD"]
CHEST = ["", "..DDD......DDD", ".DLHLDDDDDDLLMD", ".DLLLLHLLLLLLMD", ".DLLLLLLLLLLLMD", ".DDDLLLAALLLDDD",
         "...DLLLAALLMD", "...DLLLLLLLMD", "...DLLLLLLLMD", "...DLLLLLLLMD", "...DMMMMMMMMD", "...DDDDDDDDDD"]
LEGS = ["", "", "...DDDDDDDDDD", "...DHLLAALLMD", "...DLLMMMMLMD", "...DLLDDDDLMD", "...DLLD..DLMD",
        "...DLMD..DLMD", "...DLMD..DLMD", "...DLMD..DLMD", "...DLMD..DLMD", "...DDDD..DDDD"]
BOOTS = ["", "", "", "", "", "...DDD....DDD", "...DLD....DLD", "...DLD....DLD", "..DLLD...DLLD",
         ".DHLAD..DHLAD", ".DMMMD..DMMMD", ".DDDDD..DDDDD"]
BOTTLE = ["", "......DDDD", "......DKKD", ".......DD", "......DGGD", ".....DGGGGD", "....DGWLLLLD",
          "...DGWLLLLLLD", "...DGLLLHLLLD", "...DGLLLLLLMD", "...DGLLLLLMMD", "....DLLLLMMD", ".....DDDDDD"]
HEART = ["", "", "...DDD...DDD", "..DLHLD.DLLLD", ".DLHLLLDLLLLMD", ".DLLLLLLLLLLMD", ".DLLLLLALLLLMD",
         "..DLLLLLLLLMD", "...DLLLLLLMD", "....DLLLLMD", ".....DLLMD", "......DMD", ".......D"]
SHARD = ["", "..........DD", ".........DHLD", "........DHLMD", ".......DHLLMD", "......DHLLMD", ".....DLLLMD",
         "....DLLLMD", "...DLLMMD", "..DLMMD", "..DMDD", "..DD"]
CLUSTER = ["", "", ".......DD", "......DHLD", "...DD.DHLD", "..DHLDDHLMD.DD", "..DHLMDLLMDDHLD", "..DLLMDLLMDHLMD",
           "...DLMDLMMDLLD", "...DLMDLMDLMMD", "..DDDDDDDDDDDD", "..DMMMMMMMMMMD", "...DDDDDDDDDD"]
PLATE = ["", "", "", "", "", "..DDDDDDDDDDDD", ".DHLLLLLLLLLLMD", ".DLALLLLLLLLAMD", ".DLLLLLLLLLLLMD",
         ".DLALLLLLLLLAMD", ".DMMMMMMMMMMMMD", "..DDDDDDDDDDDD"]


def ingot(pal, sparkle=False):
    g = draw(INGOT, P(PAL[pal]))
    if sparkle:
        add_sparkle(g)
    return g


def add_sparkle(g, x=12, y=2, c=(255, 255, 255, 255)):
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (0, 0)):
        g[y + dy][x + dx] = c
    g[y + 2][x - 8] = (255, 255, 230, 255)


def powder(pal, washed=False):
    p = PAL[pal]
    if washed:  # poudre lavée : plus claire, brillante, une goutte d'eau
        g = draw(POWDER, P(brighter(p)))
        for x, y in ((12, 3), (11, 4), (12, 4), (13, 4), (11, 5), (12, 5), (13, 5), (12, 6)):
            g[y][x] = (70, 160, 240, 255)
        g[4][12] = (200, 235, 255, 255)
        g[9][6] = g[11][4] = (255, 255, 255, 255)
    else:  # brute : plus terne, grains sombres
        g = draw(POWDER, P(tuple(shade(c, 0.86)[:3] for c in p)))
        for x, y in ((6, 10), (9, 11), (4, 12), (8, 9), (10, 12)):
            if g[y][x][3]:
                g[y][x] = shade(p[0], 0.9)
    return g


def lump(pal):
    return draw(LUMP, P(PAL[pal]))


def ore(pal, stone=((48, 46, 44), (104, 100, 96))):
    """Minerai brut : caillou gris avec des éclats de la couleur du métal."""
    p = PAL[pal]
    return draw(ORE, {"d": stone[0], "s": stone[1], "M": p[1], "L": p[2], "H": p[3]})


def gear_shape(pal, kind):
    rows = {"pickaxe": PICKAXE, "sword": SWORD, "helmet": HELMET, "chest": CHEST, "legs": LEGS, "boots": BOOTS}[kind]
    p = PAL[pal]
    return P(p, A=p[3], W=WOOD, K=WOOD_D), rows


def gear(pal, kind, accent=None, handle=None):
    pal_d, rows = gear_shape(pal, kind)
    if accent:
        pal_d["A"] = rgba(accent)
    if handle:
        pal_d["W"], pal_d["K"] = rgba(handle[0]), rgba(handle[1])
    return draw(rows, pal_d)


def bottle(liquid, glow=None):
    l = rgba(liquid)
    g = draw(BOTTLE, {"D": (40, 44, 56), "K": (120, 82, 46), "G": (200, 225, 235), "W": (255, 255, 255),
                      "L": l, "M": shade(l, 0.7), "H": glow or shade(l, 1.4)})
    return g


def heart(pal, accent=None):
    d = P(PAL[pal])
    d["A"] = rgba(accent) if accent else d["H"]
    return draw(HEART, d)


def orb(core, ring, spark=(255, 255, 255)):
    g = blank()
    core, ring = rgba(core), rgba(ring)
    circle(g, 7.5, 7.5, 6.2, lambda x, y, d: (20, 16, 28) if d > 5.4 else mix(core, ring, d / 5.4))
    g[4][5] = g[5][4] = rgba(spark)
    return g


def plate(pal, accent=RIVET):
    return draw(PLATE, P(PAL[pal], A=accent))


# ---------------------------------------------------------------------------------------------------------
# objets dessinés un par un

def bone():
    return draw(["", "", "..DD", ".DWWD", ".DWWWD", "..DWWWD", "...DWWWD", "....DWWWD", ".....DWWWD",
                 "......DWWWD", ".......DWWWD", "........DWWWDD", ".........DWWWWD", "..........DWWD",
                 "...........DD"], {"D": (90, 84, 70), "W": (232, 226, 206)})


def gland():
    g = blank()
    circle(g, 7.5, 8.5, 5.5, lambda x, y, d: (40, 70, 20) if d > 4.7 else mix((190, 230, 80), (90, 140, 30), d / 4.7))
    for x, y in ((6, 6), (9, 9), (5, 10), (10, 6)):
        g[y][x] = (230, 255, 150, 255)
    rect(g, 7, 1, 8, 3, (110, 70, 40))
    return g


def combustible():
    g = draw(["", "", "", "", "", "", "", "...DDDDDDDDDD", "..DLLLLLLLLLMD", "..DLHLLLLLLLMD", "..DLLLLLLLLMMD",
              "..DMMMMMMMMMMD", "...DDDDDDDDDD"], P(PAL["charbon"]))
    for x, y, c in ((7, 6, (255, 120, 20)), (6, 5, (255, 170, 40)), (8, 5, (255, 170, 40)), (7, 4, (255, 220, 90)),
                    (7, 5, (255, 220, 90)), (5, 6, (230, 80, 20)), (9, 6, (230, 80, 20)), (7, 3, (255, 240, 160)),
                    (10, 4, (255, 170, 40)), (10, 5, (230, 80, 20)), (4, 5, (230, 80, 20))):
        g[y][x] = rgba(c)
    return g


def circuit():
    g = blank()
    rect(g, 1, 3, 14, 12, (20, 70, 36))
    rect(g, 2, 4, 13, 11, (34, 120, 58))
    for x in range(3, 13, 2):
        g[2][x] = g[13][x] = (200, 190, 120, 255)
    rect(g, 6, 6, 9, 9, (24, 24, 28))
    g[6][6] = (90, 90, 100, 255)
    for x in range(3, 6):
        g[7][x] = (230, 200, 90, 255)
    for x in range(10, 13):
        g[8][x] = (230, 200, 90, 255)
    g[5][4] = g[10][11] = (230, 60, 50, 255)
    g[10][3] = (80, 220, 255, 255)
    return g


def moteur():
    g = blank()
    rect(g, 2, 4, 11, 12, STEEL_D)
    for y in range(5, 12):
        for x in range(3, 11):
            g[y][x] = shade(STEEL, 1.3 if y == 5 else (1.0 if x % 2 else 0.8))
    rect(g, 12, 7, 14, 9, (190, 194, 200))
    rect(g, 4, 2, 9, 3, (200, 80, 40))
    g[2][4] = g[2][9] = (140, 50, 30, 255)
    rect(g, 3, 13, 10, 13, (24, 24, 28))
    return g


def noyau(core, ring):
    g = orb(core, ring)
    for i in range(16):  # cerclage
        if g[i][7][3] and g[i][7] != (20, 16, 28, 255):
            g[i][7] = shade(g[i][7], 0.75)
    return g


def lentille():
    g = blank()
    circle(g, 7.5, 7.5, 6.5, lambda x, y, d: (120, 90, 40) if d > 5.6 else
           mix((240, 210, 255), (150, 90, 210), d / 5.6))
    g[4][5] = g[5][4] = g[4][4] = (255, 255, 255, 255)
    return g


def bobine(core=(225, 130, 80), core2=(170, 85, 45), rod=(120, 124, 132)):
    g = blank()
    rect(g, 7, 1, 8, 14, rod)
    for y in range(3, 13):
        rect(g, 4, y, 11, y, core if y % 2 else core2)
    rect(g, 3, 2, 12, 2, STEEL_D)
    rect(g, 3, 13, 12, 13, STEEL_D)
    return g


def gear_item(teeth=(150, 154, 162), body=(120, 124, 132)):
    g = blank()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            tooth = math.cos(math.atan2(dy, dx) * 8) > 0.3
            if d <= 2:
                pass
            elif d <= 5:
                g[y][x] = rgba(body) if d < 4.4 else STEEL_D
            elif d <= 7 and tooth:
                g[y][x] = rgba(teeth)
    return g


def matiere_noire():
    g = blank()
    circle(g, 7.5, 8, 6, lambda x, y, d: (6, 4, 10) if d > 5 else mix((40, 16, 60), (6, 4, 12), d / 5))
    for x, y in ((5, 6), (9, 10), (10, 5), (6, 11), (8, 8)):
        g[y][x] = (200, 140, 255, 255)
    return g


def charge_vide():
    g = blank()
    rect(g, 5, 2, 10, 13, (30, 20, 44))
    rect(g, 6, 3, 9, 12, (90, 40, 150))
    for y in range(4, 12, 2):
        rect(g, 6, y, 9, y, (190, 130, 255))
    rect(g, 6, 1, 9, 1, (150, 150, 160))
    rect(g, 6, 14, 9, 14, (150, 150, 160))
    return g


def essence_vide():
    g = blank()
    circle(g, 7.5, 8.5, 5, lambda x, y, d: (40, 20, 60) if d > 4.2 else mix((230, 200, 255), (110, 50, 170), d / 4.2))
    for x, y in ((7, 1), (8, 2), (7, 3), (6, 2)):
        g[y][x] = (180, 120, 240, 255)
    return g


def coeur_neant():
    g = heart("neant")
    for x, y in ((6, 5), (9, 7), (5, 8), (8, 10)):
        g[y][x] = (255, 255, 255, 255)
    return g


def medaille():
    g = blank()
    for i in range(6):  # ruban
        g[i][4 + i // 2] = g[i][5 + i // 2] = (40, 80, 200, 255)
        g[i][11 - i // 2] = g[i][10 - i // 2] = (200, 40, 50, 255)
    circle(g, 7.5, 10, 4.5, lambda x, y, d: (120, 80, 10) if d > 3.7 else mix((255, 240, 150), (210, 160, 30), d / 3.7))
    g[10][7] = g[10][8] = g[9][7] = (140, 40, 160, 255)
    return g


def legumes():
    g = blank()
    for i in range(8):  # carotte
        rect(g, 2 + i, 12 - i, 3 + i, 13 - i, (240, 130, 30))
    g[12][3] = g[9][6] = (200, 100, 20, 255)
    for x, y in ((11, 3), (12, 2), (10, 2), (12, 4), (13, 3)):
        g[y][x] = (70, 160, 40, 255)
    circle(g, 11, 11, 3, lambda x, y, d: (60, 120, 30) if d > 2.3 else (110, 190, 60))  # chou
    return g


def champignons():
    g = blank()
    for cx, cy, cap, r in ((5, 6, (190, 120, 70), 3.6), (11, 8, (200, 50, 40), 3.2)):
        circle(g, cx, cy, r, lambda x, y, d, cy=cy, cap=cap: (cap if y <= cy else None))
        rect(g, int(cx) - 1, cy + 1, int(cx), cy + 6, (232, 222, 200))
    g[5][4] = g[7][11] = g[6][12] = (255, 255, 255, 255)
    return g


def ration():
    g = blank()
    rect(g, 2, 5, 13, 12, (60, 70, 40))
    rect(g, 3, 6, 12, 11, (110, 124, 70))
    rect(g, 5, 7, 10, 10, (220, 210, 170))
    rect(g, 6, 8, 9, 8, (90, 60, 40))
    rect(g, 6, 9, 8, 9, (90, 60, 40))
    rect(g, 2, 4, 13, 4, (150, 150, 140))
    return g


def bol(content, bits=()):
    g = draw(["", "", "", "", "", "", "..DCCCCCCCCCCD", ".DCCCCCCCCCCCCD", ".DBBBBBBBBBBBBD", "..DBBBBBBBBBBD",
              "...DBBBBBBBBD", "....DDDDDDDD"], {"D": (60, 40, 22), "B": (130, 90, 50), "C": content})
    for x, y, c in bits:
        g[y][x] = rgba(c)
    return g


def cafe():
    g = blank()
    rect(g, 3, 5, 10, 13, (230, 230, 225))
    rect(g, 4, 5, 9, 6, (90, 52, 26))
    rect(g, 11, 7, 12, 11, (230, 230, 225))
    g[7][11] = g[11][11] = (230, 230, 225, 255)
    rect(g, 12, 8, 12, 10, (230, 230, 225))
    g[8][11] = g[9][11] = g[10][11] = T
    rect(g, 3, 13, 10, 13, (170, 170, 165))
    for x, y in ((5, 3), (6, 2), (7, 3), (8, 1), (8, 2)):
        g[y][x] = (210, 210, 215, 200)
    return g


def module(symbol_color, symbol):
    """Puce d'optimisation : circuit noir à pattes dorées, symbole coloré au centre."""
    g = blank()
    rect(g, 3, 3, 12, 12, (28, 28, 34))
    rect(g, 4, 4, 11, 11, (44, 44, 52))
    for i in range(4, 12, 2):
        g[2][i] = g[13][i] = g[i][2] = g[i][13] = (220, 180, 70, 255)
    c = rgba(symbol_color)
    if symbol == "vitesse":  # chevrons
        for dx in range(3):
            g[5 + dx][5 + dx] = g[10 - dx][5 + dx] = c
            g[5 + dx][8 + dx] = g[10 - dx][8 + dx] = c
    elif symbol == "economie":  # éclair
        for x, y in ((9, 4), (8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10)):
            g[y][x] = c
    else:  # plus
        rect(g, 7, 5, 8, 10, c)
        rect(g, 5, 7, 10, 8, c)
    return g


def cellule():
    g = blank()
    rect(g, 4, 2, 11, 14, (30, 34, 44))
    rect(g, 5, 3, 10, 13, (60, 66, 80))
    rect(g, 6, 1, 9, 1, (180, 184, 190))
    for i, y in enumerate((11, 9, 7, 5)):
        rect(g, 6, y, 9, y + 1, (60, 230, 250) if i < 3 else (40, 100, 120))
    return g


def dynamite():
    g = blank()
    for x0 in (3, 7, 11):
        rect(g, x0 - 1, 5, x0 + 1, 13, (200, 40, 36))
        rect(g, x0 - 1, 5, x0 - 1, 13, (240, 90, 80))
    rect(g, 2, 9, 13, 10, (60, 40, 20))
    for x, y in ((7, 4), (8, 3), (8, 2), (9, 1)):
        g[y][x] = (200, 200, 190, 255)
    g[0][10] = g[1][10] = (255, 210, 60, 255)
    return g


def tete_forage(pal, tip):
    g = blank()
    p = PAL[pal]
    rect(g, 5, 1, 10, 3, STEEL_D)
    rect(g, 6, 2, 9, 2, STEEL_L)
    for i, y in enumerate(range(4, 15)):
        half = max(0, 4 - i // 2)
        for x in range(8 - half - 1, 8 + half + 1):
            g[y][x] = rgba(p[2] if (x + y) % 3 else p[1])
    g[14][7] = g[14][8] = rgba(tip)
    return g


def lampe(light=(255, 210, 90), frame=(60, 60, 66)):
    g = blank()
    rect(g, 6, 1, 9, 2, frame)
    rect(g, 4, 3, 11, 13, frame)
    rect(g, 5, 4, 10, 12, light)
    rect(g, 6, 5, 9, 11, shade(light, 1.2))
    rect(g, 4, 8, 11, 8, frame)
    rect(g, 3, 13, 12, 14, frame)
    return g


def masque():
    g = blank()
    circle(g, 7.5, 7, 6, lambda x, y, d: (40, 50, 40) if d > 5.2 else (80, 96, 70))
    for cx in (5, 10):
        circle(g, cx, 6, 1.8, lambda x, y, d: (40, 40, 40) if d > 1.2 else (170, 220, 230))
    circle(g, 7.5, 12, 2.5, lambda x, y, d: (30, 30, 30) if d > 1.7 else (120, 124, 130))
    g[12][7] = g[12][8] = (40, 40, 40, 255)
    return g


def etoile():
    g = blank()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            a = math.atan2(dy, dx)
            lim = 3 + 4 * max(0.0, math.cos(a * 2)) ** 3 + 4 * max(0.0, math.cos(a * 2 + math.pi)) ** 3
            lim = 2.5 + 5 * abs(math.cos(a * 2)) ** 4
            if r <= lim:
                g[y][x] = mix((255, 255, 255), (150, 90, 230), min(1, r / 6))
    g[7][7] = g[8][8] = (110, 230, 240, 255)
    return g


def totem():
    g = draw(["", "....DDDDDDD", "...DGGGGGGGD", "...DGEGGGEGD", "...DGGGGGGGD", "...DGGMMMGGD", "....DGGGGGD",
              "..DDGGGGGGGDD", ".DGGDGGGGGDGGD", "..DD.DGGGD.DD", ".....DGGGD", ".....DGDGD", ".....DD.DD"],
             {"D": (120, 80, 10), "G": (240, 200, 60), "E": (40, 200, 90), "M": (150, 100, 20)})
    return g


def larme():
    g = blank()
    for y in range(2, 14):
        w = 0 if y < 5 else min(4, (y - 4))
        if y > 11:
            w = 4 - (y - 11)
        rect(g, 7 - w, y, 8 + w, y, (150, 220, 240))
    rect(g, 7, 2, 8, 4, (150, 220, 240))
    g[8][6] = g[9][6] = g[7][7] = (255, 255, 255, 255)
    for y in range(4, 14):
        for x in range(16):
            if g[y][x][3] and (x + 1 > 15 or not g[y][x + 1][3]):
                g[y][x] = (90, 160, 200, 255)
    return g


def aimant():
    g = blank()
    circle(g, 7.5, 7.5, 6, lambda x, y, d: (200, 40, 40) if d > 3 and y <= 8 else None)
    rect(g, 2, 8, 4, 11, (200, 40, 40))
    rect(g, 11, 8, 13, 11, (200, 40, 40))
    rect(g, 2, 12, 4, 13, (200, 200, 210))
    rect(g, 11, 12, 13, 13, (200, 200, 210))
    g[3][5] = g[2][7] = (255, 120, 110, 255)
    return g


def trefle():
    g = blank()
    for cx, cy in ((5, 5), (10, 5), (5, 10), (10, 10)):
        circle(g, cx, cy, 2.7, lambda x, y, d: (40, 140, 40) if d > 1.9 else (90, 200, 70))
    rect(g, 7, 7, 8, 8, (40, 140, 40))
    for i in range(4):
        g[11 + i][8 + i // 2] = (60, 110, 30, 255)
    g[4][4] = g[4][9] = (200, 255, 170, 255)
    return g


def boussole():
    g = blank()
    circle(g, 7.5, 7.5, 6.5, lambda x, y, d: (40, 44, 56) if d > 5.6 else (24, 60, 70))
    for i in range(5):
        g[3 + i][7] = g[3 + i][8] = (70, 230, 230, 255)
        g[8 + i][7] = g[8 + i][8] = (140, 140, 150, 255)
    g[7][7] = g[8][8] = (255, 255, 255, 255)
    return g


def page():
    g = blank()
    rect(g, 3, 1, 12, 14, (226, 210, 168))
    for y in range(3, 13, 2):
        rect(g, 4, y, 11 if y % 4 else 9, y, (120, 100, 70))
    g[11][10] = g[12][11] = (150, 110, 70, 255)
    rect(g, 3, 1, 12, 1, (196, 176, 130))
    return g


def fusee():
    g = blank()
    for i in range(9):
        x, y = 4 + i, 12 - i
        g[y][x] = (200, 40, 40, 255)
        g[y][x - 1] = (140, 24, 24, 255)
    g[3][12] = g[4][13] = (250, 250, 250, 255)
    for x, y in ((14, 1), (13, 0), (15, 2), (12, 1)):
        g[y][x] = (255, 220, 80, 255)
    rect(g, 2, 13, 3, 14, (90, 70, 50))
    return g


def kit_reparation():
    g = gear_item()
    for i in range(10):
        x, y = 2 + i, 13 - i
        g[y][x] = (190, 60, 50, 255)
        g[y][min(15, x + 1)] = (140, 40, 34, 255)
    g[3][12] = g[2][13] = g[2][11] = (210, 212, 220, 255)
    return g


def filtre():
    g = blank()
    circle(g, 7.5, 7.5, 6.5, lambda x, y, d: (60, 64, 72) if d > 5.6 else (240, 240, 232) if (x + y) % 3
           else (200, 200, 190))
    return g


def spores():
    g = powder("spore")
    for x, y in ((3, 4), (12, 6), (5, 2), (10, 3)):
        g[y][x] = (230, 255, 140, 255)
    return g


def coeur_reine():
    g = heart("cristal", accent=(140, 220, 60))
    for x, y in ((4, 4), (10, 4), (7, 8)):
        g[y][x] = (230, 255, 150, 255)
    return g


def charbon():
    return lump("charbon")


def plastron_refrigere():
    g = gear("glace", "chest", accent=(255, 255, 255))
    for x, y in ((4, 3), (10, 7), (6, 9)):
        g[y][x] = (255, 255, 255, 255)
    return g


def casque_filtrant():
    g = gear("acier", "helmet", accent=(90, 220, 110))
    rect(g, 6, 8, 9, 9, (90, 220, 110))
    return g


# ---------------------------------------------------------------------------------------------------------
# machines : cubes, face avant propre à chaque machine, côtés à bande de couleur, dessus à grille

def panel(salt=0, base=STEEL):
    g = blank()
    for y in range(16):
        for x in range(16):
            c = shade(base, 0.92 + 0.16 * noise(x, y, salt))
            if x in (0, 15) or y in (0, 15):
                c = STEEL_D
            elif x == 1 or y == 1:
                c = shade(base, 1.25)
            elif x == 14 or y == 14:
                c = shade(base, 0.72)
            g[y][x] = c
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        g[y][x] = RIVET
    return g


def side(accent, salt, base=STEEL):
    g = panel(salt, base)
    rect(g, 2, 7, 13, 8, accent)
    rect(g, 2, 7, 13, 7, shade(accent, 1.25))
    for y in (10, 12):
        rect(g, 4, y, 11, y, STEEL_D)
    return g


def top(accent, salt, base=STEEL):
    g = panel(salt, base)
    for y in range(4, 12, 2):
        rect(g, 4, y, 11, y, STEEL_D)
    g[7][1] = g[8][1] = g[7][14] = g[8][14] = rgba(accent)
    return g


def front_generateur():
    g = panel(1)
    circle(g, 7.5, 7.5, 5.5, lambda x, y, d: (28, 40, 52) if d > 4.4 else shade((60, 210, 240), 1.6 - d / 5))
    for x, y in ((8, 4), (7, 5), (6, 6), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10), (7, 10)):
        g[y][x] = (255, 240, 90, 255)
    return g


def front_concasseur():
    g = panel(2)
    for cx in (4.5, 10.5):
        circle(g, cx, 8.5, 3.6, lambda x, y, d: (60, 62, 70) if d > 3 else (150, 152, 160) if (x + y) % 2
               else (120, 122, 130))
    for i, y in enumerate(range(2, 5)):
        rect(g, 3 + i, y, 12 - i, y, (40, 40, 46))
    rect(g, 2, 13, 13, 13, (240, 140, 40))
    return g


def front_laveur():
    g = panel(3)
    rect(g, 3, 3, 12, 12, (24, 30, 40))
    for y in range(4, 12):
        for x in range(4, 12):
            g[y][x] = shade((60, 140, 225), 1.4 - 0.06 * (y - 4))
    for x, y in ((5, 9), (6, 6), (9, 8), (10, 5), (7, 10)):
        g[y][x] = (220, 240, 255, 255)
    rect(g, 4, 4, 11, 4, (140, 200, 250))
    return g


def front_fonderie():
    g = panel(4, (96, 70, 66, 255))
    for y in range(3, 7):
        for x in range(2, 14):
            if (x + (y // 2) * 2) % 4 == 0:
                g[y][x] = (64, 44, 40, 255)
    rect(g, 3, 8, 12, 13, (30, 22, 20))
    for y in range(9, 13):
        for x in range(4, 12):
            g[y][x] = shade((255, 120, 30), 0.8 + 0.4 * noise(x, y, 9) + (12 - y) * 0.05)
    return g


def front_raffinerie():
    g = panel(5, (190, 188, 200, 255))
    rect(g, 7, 2, 8, 6, (200, 225, 235))
    rect(g, 6, 2, 9, 2, (120, 130, 140))
    circle(g, 7.5, 10, 4.2, lambda x, y, d: (90, 90, 110) if d > 3.5 else (180, 90, 230) if y > 9
           else (215, 235, 245))
    g[8][6] = g[9][5] = (255, 255, 255, 255)
    return g


def front_alliageur():
    g = panel(6)
    rect(g, 2, 4, 7, 6, (220, 120, 60))
    rect(g, 2, 4, 7, 4, (255, 175, 120))
    rect(g, 8, 4, 13, 6, (210, 222, 234))
    rect(g, 8, 4, 13, 4, (250, 252, 255))
    rect(g, 3, 9, 12, 10, (50, 52, 58))
    rect(g, 6, 11, 9, 12, (50, 52, 58))
    rect(g, 4, 13, 11, 13, (50, 52, 58))
    g[8][7] = g[7][8] = (255, 230, 80, 255)
    return g


def front_assembleur():
    g = panel(7)
    for gy in range(3):
        for gx in range(3):
            rect(g, 3 + gx * 4, 3 + gy * 4, 5 + gx * 4, 5 + gy * 4, (40, 42, 50))
    circle(g, 8, 8, 1.6, lambda x, y, d: (250, 205, 60))
    return g


def front_entrepot():
    g = blank()
    for y in range(16):
        for x in range(16):
            g[y][x] = shade((150, 104, 60), 0.85 + 0.25 * noise(x // 3, y, 8))
    for y in (0, 1, 7, 8, 14, 15):
        rect(g, 0, y, 15, y, (196, 118, 72) if y not in (1, 8, 15) else (150, 84, 50))
    for x, y in ((2, 0), (13, 0), (2, 7), (13, 7), (2, 14), (13, 14)):
        g[y][x] = RIVET
    return g


def arrow(g, up, color):
    for i in range(5):
        y = (3 + i) if up else (12 - i)
        rect(g, 7 - i, y, 8 + i, y, color)
    for y in (range(8, 13) if up else range(3, 8)):
        rect(g, 6, y, 9, y, color)


def front_quai_expedition():
    g = panel(10)
    arrow(g, True, (70, 205, 100))
    return g


def front_quai_reception():
    g = panel(11, (70, 96, 92, 255))
    arrow(g, False, (50, 205, 195))
    rect(g, 3, 13, 12, 13, (30, 36, 40))
    return g


def foreuse_front(stripe, base=STEEL, bit=((180, 182, 190), (100, 102, 110)), salt=12):
    def fn():
        g = panel(salt, base)
        for x in range(1, 15):
            c = rgba(stripe) if ((x // 2) % 2 == 0) else (24, 24, 28, 255)
            g[2][x] = g[3][x] = c
        for i, y in enumerate(range(5, 14)):
            half = max(0, 4 - i // 2)
            for x in range(8 - half - 1, 8 + half + 1):
                g[y][x] = rgba(bit[0] if (x + y) % 3 else bit[1])
        return g
    return fn


def front_serre():
    g = panel(13, (70, 90, 70, 255))
    rect(g, 2, 2, 13, 13, (150, 200, 210))
    rect(g, 7, 2, 8, 13, (60, 80, 60))
    rect(g, 2, 7, 13, 8, (60, 80, 60))
    for x0 in (3, 9):
        for y0 in (3, 9):
            g[y0 + 3][x0 + 1] = g[y0 + 2][x0 + 1] = (60, 150, 40, 255)
            g[y0 + 1][x0] = g[y0 + 1][x0 + 2] = (100, 200, 60, 255)
            g[y0 + 3][x0 + 3] = (60, 150, 40, 255)
    rect(g, 2, 2, 13, 2, (210, 240, 245))
    return g


def front_cuisine():
    g = panel(14, (200, 200, 196, 255))
    for cx in (4.5, 10.5):
        circle(g, cx, 4.5, 2.4, lambda x, y, d: (30, 30, 34) if d > 1.4 else (230, 70, 30))
    rect(g, 3, 8, 12, 13, (30, 30, 34))
    rect(g, 4, 9, 11, 12, (240, 150, 50))
    rect(g, 4, 9, 11, 9, (255, 200, 120))
    rect(g, 5, 7, 10, 7, (120, 120, 126))
    return g


def front_groupeur():
    g = panel(15, (120, 90, 60, 255))
    c = (230, 150, 90)
    for y in (3, 8, 12):  # trois flèches qui se rejoignent
        rect(g, 2, y, 6, y, c)
    for i in range(4):
        g[3 + i][7 + i // 2] = g[12 - i][7 + i // 2] = rgba(c)
    rect(g, 8, 7, 11, 8, c)
    for i in range(3):
        rect(g, 11 + i, 5 + i, 11 + i, 10 - i, c)
    return g


def front_separateur():
    g = panel(16, (110, 120, 96, 255))
    c = (110, 220, 160)
    rect(g, 2, 7, 6, 8, c)
    for i in range(5):
        g[7 - i][7 + i] = g[8 + i][7 + i] = rgba(c)
        g[6 - i][7 + i] = g[9 + i][7 + i] = rgba(c)
    for i in range(3):
        g[2][11 + i] = g[2 + i][13] = rgba(c)
        g[13][11 + i] = g[13 - i][13] = rgba(c)
    return g


def front_trieur():
    g = panel(17, (60, 110, 110, 255))
    for i, y in enumerate(range(3, 9)):
        rect(g, 2 + i, y, 13 - i, y, (40, 44, 50))
    rect(g, 7, 9, 8, 10, (40, 44, 50))
    for x, c in ((3, (230, 70, 60)), (7, (80, 200, 90)), (11, (70, 140, 230))):
        rect(g, x, 11, x + 2, 13, c)
    g[4][5] = (230, 70, 60, 255)
    g[5][9] = (80, 200, 90, 255)
    g[6][7] = (70, 140, 230, 255)
    return g


def front_table_foreuses():
    g = panel(18, (110, 84, 56, 255))
    rect(g, 2, 2, 13, 11, (30, 50, 40))
    for y in range(3, 11):
        for x in range(3, 13):
            g[y][x] = (40, 90, 60, 255) if (x + y * 3) % 7 else (50, 110, 70, 255)
    for x, y in ((5, 4), (10, 6), (6, 9), (11, 9)):
        g[y][x] = (255, 210, 60, 255)
    g[6][7] = (240, 60, 50, 255)
    rect(g, 3, 13, 12, 13, (60, 44, 30))
    return g


def front_electrolyseur():
    g = panel(19, (110, 120, 120, 255))
    rect(g, 2, 5, 13, 13, (30, 40, 44))
    rect(g, 3, 7, 12, 12, (90, 200, 180))
    rect(g, 3, 7, 12, 7, (150, 240, 220))
    rect(g, 5, 2, 5, 11, (200, 90, 50))
    rect(g, 10, 2, 10, 11, (120, 124, 132))
    rect(g, 4, 2, 6, 2, (230, 60, 50))
    rect(g, 9, 2, 11, 2, (40, 40, 44))
    for x, y in ((6, 9), (9, 10), (6, 11), (11, 8)):
        g[y][x] = (230, 255, 250, 255)
    return g


def front_generateur_thermique():
    g = panel(20, (44, 38, 40, 255))
    rect(g, 3, 6, 12, 13, (24, 18, 18))
    for y in range(7, 13):
        for x in range(4, 12):
            g[y][x] = shade((255, 110, 20), 0.75 + 0.45 * noise(x, y, 21) + (y - 7) * 0.04)
    for x in range(4, 12, 2):
        rect(g, x, 7, x, 12, (40, 34, 34))
    for i, x in enumerate((4, 7, 10)):  # vagues de chaleur
        g[2 + i % 2][x] = g[3 - i % 2][x + 1] = g[2 + i % 2][x + 2] = (240, 190, 60, 255)
    return g


def front_etabli_precision():
    g = panel(21, (90, 100, 120, 255))
    circle(g, 7.5, 7.5, 5.5, lambda x, y, d: (210, 214, 222) if d > 4.6 else (30, 34, 44))
    rect(g, 7, 3, 8, 12, (60, 230, 255))
    rect(g, 3, 7, 12, 8, (60, 230, 255))
    g[7][7] = g[8][8] = g[7][8] = g[8][7] = (255, 255, 255, 255)
    return g


def front_four_induction():
    g = panel(22, (60, 70, 96, 255))
    circle(g, 7.5, 7.5, 6, lambda x, y, d: (225, 130, 80) if int(d) in (5, 3) else
           (255, 240, 200) if d < 1.6 else (255, 160, 60) if d < 2.6 else (30, 34, 50))
    return g


def front_condenseur():
    g = panel(23, (40, 30, 60, 255))
    rect(g, 3, 2, 12, 13, (20, 14, 30))
    for y in range(3, 13):
        for x in range(4, 12):
            a = math.atan2(y - 7.5, x - 7.5) + ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 * 0.8
            g[y][x] = (150, 80, 220, 255) if math.cos(a * 2) > 0.4 else (60, 30, 100, 255)
    rect(g, 3, 2, 12, 2, (120, 124, 132))
    rect(g, 3, 13, 12, 13, (120, 124, 132))
    return g


def front_reacteur():
    g = panel(24, (30, 26, 44, 255))
    circle(g, 7.5, 7.5, 6.5, lambda x, y, d: (90, 90, 110) if d > 5.8 else
           (255, 255, 255) if d < 1.5 else mix((230, 180, 255), (80, 30, 150), (d - 1.5) / 4.3))
    for x, y in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        g[y][x] = (200, 140, 255, 255)
    return g


MACHINES = {  # id -> (face avant, couleur d'accent des côtés, teinte de la carrosserie)
    "m_generateur": (front_generateur, (60, 210, 240), STEEL),
    "m_concasseur": (front_concasseur, (240, 140, 40), STEEL),
    "m_laveur": (front_laveur, (60, 140, 225), STEEL),
    "m_fonderie": (front_fonderie, (230, 80, 40), STEEL),
    "m_raffinerie": (front_raffinerie, (180, 90, 230), STEEL),
    "m_alliageur": (front_alliageur, (220, 160, 70), STEEL),
    "m_assembleur": (front_assembleur, (250, 205, 60), STEEL),
    "m_entrepot": (front_entrepot, (196, 118, 72), STEEL),
    "m_quai_expedition": (front_quai_expedition, (70, 205, 100), STEEL),
    "m_quai_reception": (front_quai_reception, (50, 205, 195), STEEL),
    "m_foreuse": (foreuse_front((240, 200, 40)), (240, 200, 40), STEEL),
    "m_foreuse_r": (foreuse_front((240, 120, 30), (70, 64, 60, 255), ((215, 160, 70), (140, 90, 40)), 31),
                    (240, 120, 30), (70, 64, 60, 255)),
    "m_foreuse_3": (foreuse_front((70, 140, 240), (50, 60, 90, 255), ((120, 160, 240), (40, 76, 190)), 32),
                    (70, 140, 240), (50, 60, 90, 255)),
    "m_foreuse_4": (foreuse_front((190, 120, 255), (34, 24, 50, 255), ((150, 90, 220), (60, 30, 100)), 33),
                    (190, 120, 255), (34, 24, 50, 255)),
    "m_serre": (front_serre, (100, 200, 60), (70, 90, 70, 255)),
    "m_cuisine": (front_cuisine, (240, 150, 50), (150, 150, 150, 255)),
    "m_groupeur": (front_groupeur, (230, 150, 90), (120, 90, 60, 255)),
    "m_separateur": (front_separateur, (110, 220, 160), (110, 120, 96, 255)),
    "m_trieur": (front_trieur, (80, 200, 200), (60, 110, 110, 255)),
    "m_table_foreuses": (front_table_foreuses, (255, 210, 60), (110, 84, 56, 255)),
    "m_electrolyseur": (front_electrolyseur, (90, 200, 180), (110, 120, 120, 255)),
    "m_generateur_thermique": (front_generateur_thermique, (255, 110, 20), (44, 38, 40, 255)),
    "m_etabli_precision": (front_etabli_precision, (60, 230, 255), (90, 100, 120, 255)),
    "m_four_induction": (front_four_induction, (225, 130, 80), (60, 70, 96, 255)),
    "m_condenseur": (front_condenseur, (150, 80, 220), (40, 30, 60, 255)),
    "m_reacteur": (front_reacteur, (230, 180, 255), (30, 26, 44, 255)),
}


def machine_faces(mid, salt):
    front_fn, accent, base = MACHINES[mid]
    faces = {"front": front_fn(), "side": side(rgba(accent), 20 + salt, base),
             "top": top(accent, 40 + salt, base), "bottom": panel(60 + salt, base)}
    if mid == "m_entrepot":
        faces["side"] = faces["top"] = front_entrepot()
    return faces


# ---------------------------------------------------------------------------------------------------------
ITEMS = {
    # ressources
    "charbon": charbon,
    "minerai_cuivre": lambda: ore("cuivre"),
    "minerai_fer": lambda: ore("fer_brut"),
    "minerai_etain": lambda: ore("etain"),
    "minerai_argent": lambda: ore("argent"),
    "minerai_cobalt": lambda: ore("cobalt"),
    "minerai_enderite": lambda: ore("enderite", ((20, 16, 30), (60, 52, 76))),
    "spore_lumineuse": spores,
    "soufre": lambda: lump("soufre"),
    "cristal_resonant": lambda: draw(CLUSTER, P(PAL["cristal"])),
    "eclat_neant": lambda: draw(SHARD, P(PAL["neant"])),
    "essence_vide": essence_vide,
    "os_mineur": bone,
    "glande_spores": gland,
    "coeur_forgeron": lambda: heart("forge", accent=(255, 240, 160)),
    "coeur_contremaitre": lambda: heart("acier", accent=(240, 200, 40)),
    "coeur_reine": coeur_reine,
    "coeur_cristal": lambda: heart("cristal", accent=(255, 255, 255)),
    "coeur_neant": coeur_neant,
    # produits
    "lingot_cuivre": lambda: ingot("cuivre"),
    "lingot_fer": lambda: ingot("fer"),
    "lingot_etain": lambda: ingot("etain"),
    "bronze": lambda: ingot("bronze", True),
    "acier": lambda: ingot("acier", True),
    "engrenage": gear_item,
    "bobine": bobine,
    "bobine_resonante": lambda: bobine((240, 148, 222), (198, 76, 176), (200, 200, 220)),
    "combustible": combustible,
    "circuit": circuit,
    "plaque_blindee": lambda: plate("acier"),
    "plaque_cobalt": lambda: plate("cobalt", (220, 230, 255)),
    "plaque_neant": lambda: plate("neant", (214, 156, 255)),
    "moteur": moteur,
    "noyau_spores": lambda: noyau((230, 255, 140), (80, 140, 30)),
    "noyau_vide": lambda: noyau((240, 220, 255), (70, 30, 120)),
    "filtre": filtre,
    "lentille_cristal": lentille,
    "matiere_noire": matiere_noire,
    "charge_vide": charge_vide,
    "medaille_varenne": medaille,
    # objets à utiliser
    "infusion_glacee": lambda: bottle((120, 210, 245)),
    "elixir_silence": lambda: bottle((210, 120, 220)),
    "serum_gravite": lambda: bottle((70, 30, 120), (200, 150, 255, 255)),
    "legumes": legumes,
    "champignons": champignons,
    "ration": ration,
    "ragout": lambda: bol((120, 70, 40), ((5, 6, (240, 130, 30)), (9, 6, (190, 120, 70)), (7, 7, (110, 190, 60)))),
    "soupe_spores": lambda: bol((150, 200, 40), ((5, 6, (230, 255, 140)), (10, 7, (230, 255, 140)))),
    "cafe": cafe,
    "opt_vitesse": lambda: module((240, 70, 60), "vitesse"),
    "opt_economie": lambda: module((80, 220, 90), "economie"),
    "opt_rendement": lambda: module((180, 110, 255), "rendement"),
    "cellule": cellule,
    "dynamite": dynamite,
    "tete_forage": lambda: tete_forage("bronze", (255, 140, 40)),
    "tete_forage_3": lambda: tete_forage("cobalt", (170, 206, 255)),
    "tete_forage_4": lambda: tete_forage("neant", (214, 156, 255)),
    "fusee": fusee,
    "kit_reparation": kit_reparation,
    "menu": etoile,
    "page_carnet": page,
    # brocante
    "totem_garde": totem,
    "larme_soin": larme,
    "aimant": aimant,
    "trefle": trefle,
    "boussole_retour": boussole,
    "lampe": lampe,
    "lampe_eternelle": lambda: lampe((110, 230, 240), (40, 50, 60)),
    "masque": masque,
    # équipement
    "pioche_fer": lambda: gear("fer", "pickaxe"),
    "pioche_bronze": lambda: gear("bronze", "pickaxe"),
    "pioche_acier": lambda: gear("acier", "pickaxe"),
    "pioche_abyssale": lambda: gear("abyssal", "pickaxe", handle=((40, 70, 90), (20, 34, 46))),
    "pioche_forge": lambda: gear("forge", "pickaxe", handle=((70, 60, 60), (40, 34, 34))),
    "pioche_cristal": lambda: gear("cristal", "pickaxe", handle=((200, 200, 220), (120, 120, 140))),
    "epee_pierre": lambda: gear("pierre", "sword", accent=(110, 80, 50)),
    "epee_bronze": lambda: gear("bronze", "sword", accent=(120, 70, 30)),
    "epee_acier": lambda: gear("acier", "sword", accent=(170, 180, 195)),
    "epee_abyssale": lambda: gear("abyssal", "sword", accent=(110, 226, 236), handle=((40, 70, 90), (20, 34, 46))),
    "epee_cristal": lambda: gear("cristal", "sword", accent=(255, 255, 255), handle=((200, 200, 220), (120, 120, 140))),
    "lame_neant": lambda: gear("neant", "sword", accent=(214, 156, 255), handle=((40, 30, 60), (20, 14, 30))),
}
for _set, _pal, _acc in (("bronze", "bronze", None), ("acier", "acier", (110, 220, 230)),
                         ("abyssal", "abyssal", (110, 226, 236)), ("cristal", "cristal", (255, 255, 255)),
                         ("neant", "neant", (230, 190, 255))):
    for _kind, _fr in (("helmet", "casque"), ("chest", "plastron"), ("legs", "jambieres"), ("boots", "bottes")):
        _name = "%s_%s" % (_fr, _set)
        if _set == "abyssal" and _kind in ("legs", "boots"):
            _name = "%s_abyssales" % _fr
        if _set == "cristal" and _kind == "helmet":
            _name = "heaume_cristal"
        ITEMS[_name] = (lambda p=_pal, k=_kind, a=_acc: gear(p, k, accent=a))
ITEMS.pop("casque_acier")  # l'Abysse n'a pas de casque d'acier : c'est le casque filtrant
ITEMS["casque_filtrant"] = casque_filtrant
ITEMS["plastron_refrigere"] = plastron_refrigere
# poudres et lingots purs, une palette par métal
for _m in ("cuivre", "fer", "etain", "argent", "cobalt", "enderite"):
    ITEMS["poudre_" + _m] = (lambda m=_m: powder(m))
    ITEMS["poudre_%s_lavee" % _m] = (lambda m=_m: powder(m, True))
    ITEMS["lingot_%s_pur" % _m] = (lambda m=_m: ingot(m, True))
