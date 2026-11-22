"""Interface (boutons manquants du lobby), police d'icônes, sons d'interface, gadgets EemiCore.

Importé par build_pack.py. Les boutons repartent des textures 64x64 de la base (lobby/suivant, lobby/fermer)
pour garder exactement le même style ; nécessite Pillow.
"""
import io
import json

from PIL import Image, ImageDraw

from art_abysse import blank, rect, circle, draw, rgba, shade, mix

# ---------------------------------------------------------------------------------------------------------
# boutons : eemicraft:lobby/precedent, confirmer, annuler (retour, fermer, suivant existent déjà dans la base)


def _img(data):
    return Image.open(io.BytesIO(data)).convert("RGBA")


def _blank_button(img):
    """Efface le symbole central en recopiant, ligne par ligne, la couleur du bouton prise à gauche du symbole."""
    out = img.copy()
    px = out.load()
    for y in range(12, 52):
        ref = px[13, y]
        for x in range(14, 51):
            px[x, y] = ref
    return out


def _png(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()


def _symbol(btn, polys, color, shadow=(30, 30, 40, 150), width=None):
    """Dessine un symbole (polygones ou ligne épaisse) avec une ombre portée décalée de (2, 2)."""
    layer = Image.new("RGBA", btn.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for off, col in (((2, 3), shadow), ((0, 0), color)):
        for kind, pts in polys:
            p = [(x + off[0], y + off[1]) for x, y in pts]
            if kind == "poly":
                d.polygon(p, fill=col)
            else:
                d.line(p, fill=col, width=width, joint="curve")
    return Image.alpha_composite(btn, layer)


def ui_buttons(base_files):
    suivant = _img(base_files["assets/eemicraft/textures/item/lobby/suivant.png"])
    fermer = _img(base_files["assets/eemicraft/textures/item/lobby/fermer.png"])
    grey = _blank_button(suivant)
    red = _blank_button(fermer)
    # bouton vert : le rouge avec les canaux rouge et vert échangés (même relief, même liseré)
    green = Image.merge("RGBA", (red.getchannel("G"), red.getchannel("R"), red.getchannel("B"), red.getchannel("A")))

    white = (255, 255, 255, 255)
    out = {}
    # précédent (page précédente) : double chevron « pour ne pas le confondre avec lobby/retour (flèche)
    out["precedent"] = _symbol(grey, [("line", [(31, 20), (20, 32), (31, 44)]), ("line", [(45, 20), (34, 32), (45, 44)])],
                               white, width=6)
    out["confirmer"] = _symbol(green, [("line", [(19, 33), (28, 42), (45, 22)])], white, width=7)
    out["annuler"] = _symbol(grey, [("line", [(21, 21), (43, 43)]), ("line", [(43, 21), (21, 43)])],
                             (232, 60, 56, 255), width=7)
    return {k: _png(v) for k, v in out.items()}, {k: v for k, v in out.items()}


# ---------------------------------------------------------------------------------------------------------
# police d'icônes eemicraft:icones (8x8 par glyphe). Caractères U+E400..U+E409 : la base occupe déjà
# U+E001-E002 et U+E101-E10E (casino), U+E300-E301 et U+E310-E31E (lobby, default, uniform).

ICON_ROWS = {  # 8x8 ; palette ci-dessous
    "piece": ["..YYYY..", ".YOOOOY.", "YOYYYYOY", "YOYOOYOY", "YOYOOYOY", "YOYYYYOY", ".YOOOOY.", "..YYYY.."],
    "eclat": ["...HH...", "..HLLM..", ".HLLLMM.", "HLLLLMMD", ".LLLMMD.", "..LMMD..", "...MD...", "........"],
    "cadenas": ["..GGGG..", ".G....G.", ".G....G.", "YYYYYYYY", "YOOOOOOY", "YOOKKOOY", "YOOKKOOY", "YYYYYYYY"],
    "retour": ["...W....", "..WW....", ".WWWWWW.", "WWWWWWWW", ".WWWWWWW", "..WW..WW", "...W..WW", "......WW"],
    "coche": [".......V", "......VV", ".....VV.", "V...VV..", "VV.VV...", ".VVV....", "..V.....", "........"],
    "croix": ["R......R", "RR....RR", ".RR..RR.", "..RRRR..", "..RRRR..", ".RR..RR.", "RR....RR", "R......R"],
    "etoile": ["...Y....", "...Y....", "..YYY...", "YYYYYYY.", ".YYYYY..", "..YYY...", ".YY.YY..", ".Y...Y.."],
    "coeur": [".RR.RR..", "RHRRRRR.", "RRRRRRR.", "RRRRRRR.", ".RRRRR..", "..RRR...", "...R....", "........"],
    "epee": [".......W", "......WG", ".....WG.", "....WG..", ".O.WG...", "..OG....", ".KOO....", "K..O...."],
    "trophee": ["YYYYYYYY", "OYYYYYYO", "O.YYYY.O", ".OYYYYO.", "...YY...", "...YY...", "..OOOO..", ".OOOOOO."],
}
ICON_PAL = {"Y": (250, 205, 50), "O": (190, 130, 20), "H": (220, 250, 255), "L": (110, 220, 240),
            "M": (60, 150, 210), "D": (30, 80, 150), "G": (170, 170, 180), "K": (60, 60, 66), "W": (240, 240, 240),
            "V": (80, 220, 90), "R": (230, 60, 56)}
ICON_ORDER = list(ICON_ROWS)
ICON_FIRST = 0xE400


def icon_font():
    sheet = Image.new("RGBA", (8 * len(ICON_ORDER), 8), (0, 0, 0, 0))
    px = sheet.load()
    for i, name in enumerate(ICON_ORDER):
        rows = ICON_ROWS[name]
        assert len(rows) == 8 and all(len(r) == 8 for r in rows), name
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch != ".":
                    px[i * 8 + x, y] = ICON_PAL[ch] + (255,)
    chars = "".join(chr(ICON_FIRST + i) for i in range(len(ICON_ORDER)))
    font = {"providers": [{"type": "bitmap", "file": "eemicraft:font/icones.png", "ascent": 7, "height": 8,
                           "chars": [chars]}]}
    table = [(chr(ICON_FIRST + i), "U+%04X" % (ICON_FIRST + i), n) for i, n in enumerate(ICON_ORDER)]
    return _png(sheet), json.dumps(font, indent=2, ensure_ascii=True), table, sheet


# ---------------------------------------------------------------------------------------------------------
# sons : événements eemicraft:ui.* qui rejouent des événements vanilla (type « event » : pas de fichier .ogg)

def ev(event, pitch=1.0, volume=1.0):
    return {"name": "minecraft:" + event, "type": "event", "pitch": pitch, "volume": volume}


SOUNDS = {
    "ui.achat": {"sounds": [ev("entity.experience_orb.pickup", 1.4, 0.8), ev("block.note_block.pling", 1.6, 0.6)],
                 "subtitle": "Achat"},
    "ui.quete": {"sounds": [ev("ui.toast.challenge_complete", 1.2, 0.5)], "subtitle": "Quete terminee"},
    "ui.niveau": {"sounds": [ev("entity.player.levelup", 1.0, 0.8), ev("entity.player.levelup", 1.12, 0.8)],
                  "subtitle": "Niveau superieur"},
    "ui.clic": {"sounds": [ev("ui.button.click", 1.3, 0.35), ev("ui.button.click", 1.45, 0.35)]},
    "ui.erreur": {"sounds": [ev("block.note_block.bass", 0.6, 0.8), ev("entity.villager.no", 1.0, 0.6)],
                  "subtitle": "Refuse"},
    "ui.recompense": {"sounds": [ev("block.amethyst_block.chime", 1.0, 1.0), ev("block.note_block.chime", 1.5, 0.7)],
                      "subtitle": "Recompense"},
}

# ---------------------------------------------------------------------------------------------------------
# gadgets EemiCore (lobby) : eemicraft:gadget/feux|peinture|grappin|jetpack


def gadget_feux():
    g = blank()
    rect(g, 3, 6, 9, 13, (40, 44, 56))
    for i, x in enumerate((4, 6, 8)):
        c = ((230, 60, 56), (70, 140, 240), (250, 205, 50))[i]
        rect(g, x, 3 + i % 2, x, 12, c)
        g[2 + i % 2][x] = (250, 250, 250, 255)
    rect(g, 3, 13, 10, 14, (90, 70, 50))
    for x, y, c in ((12, 2, (250, 205, 50)), (13, 4, (230, 60, 56)), (11, 4, (70, 140, 240)), (12, 5, (255, 255, 255)),
                    (14, 1, (255, 255, 255)), (10, 1, (230, 60, 56))):
        g[y][x] = rgba(c)
    return g


def gadget_peinture():
    g = draw(["", "", "....BB", "...BCCB", "...BCCB", "....BB", "..DDDDDDDDDD", ".DLLLLLLLLLLMD", ".DLHHLLLLLLLMDGG",
              ".DMMMMMMMMMMDD", "..DDDKKD", "....DKKD", "....DKKD", "....DKKD", ".....DD"],
             {"D": (30, 30, 36), "L": (230, 80, 160), "H": (255, 170, 210), "M": (160, 40, 110), "K": (60, 60, 66),
              "B": (40, 40, 46), "C": (70, 200, 240), "G": (120, 230, 90)})
    g[8][15] = (250, 205, 50, 255)
    return g


def gadget_grappin():
    g = blank()
    rect(g, 7, 2, 8, 9, (150, 154, 162))
    for i in range(3):  # trois crochets
        g[9 + i][6 - i] = g[9 + i][9 + i] = (150, 154, 162, 255)
        g[10 + i][7] = (150, 154, 162, 255) if i < 2 else (110, 114, 122, 255)
    g[11][3] = g[10][3] = g[11][12] = g[10][12] = (200, 204, 210, 255)
    rect(g, 6, 1, 9, 2, (60, 60, 66))
    for i, (x, y) in enumerate(((10, 1), (11, 0), (12, 1), (13, 2), (14, 3), (14, 4))):
        g[y][x] = (190, 160, 110, 255)
    return g


def gadget_jetpack():
    g = blank()
    for x0 in (3, 9):
        rect(g, x0, 2, x0 + 3, 10, (70, 78, 90))
        rect(g, x0, 2, x0 + 3, 3, (230, 60, 56))
        rect(g, x0 + 1, 4, x0 + 1, 9, (130, 140, 155))
        rect(g, x0 + 1, 11, x0 + 2, 11, (40, 44, 56))
        g[12][x0 + 1] = g[12][x0 + 2] = (255, 200, 60, 255)
        g[13][x0 + 1] = (255, 120, 30, 255)
        g[14][x0 + 2] = (230, 60, 30, 255)
    rect(g, 7, 4, 8, 8, (40, 44, 56))
    return g


GADGETS = {"feux": gadget_feux, "peinture": gadget_peinture, "grappin": gadget_grappin, "jetpack": gadget_jetpack}
