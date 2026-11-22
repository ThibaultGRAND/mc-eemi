"""Interfaces du mode Dernier Refuge (pack RefugePack, espace de noms `refuge:`). Appelé par gen_refuge.py.

Style demandé par Thibault (references/style_navigator.png) : panneau gris clair à bord sombre façon menu Minecraft,
onglet de titre en haut (petites capitales pixel), boutons ✕ et i en haut, GROS boutons avec une icône et un nom
dessous, aucune vitre, inventaire du joueur visible en bas.

Technique (même méthode que les fonds du lobby EEMICRAFT, art_lobby.py / build_pack.py) :
  - le titre de l'inventaire est un composant dans la police `refuge:gui` : espace -8, puis le glyphe du fond
    (bitmap 256x256, hauteur 256, ascent 13 + T : la ligne T de l'image tombe sur le haut du coffre, les T lignes
    du dessus dessinent l'onglet qui dépasse), puis des calques d'état (même taille) ramenés à x = 0 par des
    espaces négatifs, puis des textes dynamiques ;
  - pixel (x, y) du fond = pixel (x, y - T) du coffre vanilla : cases en (7 + 18*col, 17 + 18*ligne) ;
  - la colonne x = 175 est opaque dans chaque glyphe (bord noir du coffre) : avance = 177 pour tous ;
  - le fond ne couvre que la partie haute du coffre (lignes du menu) ; l'inventaire du joueur reste vanilla ;
  - les cases d'objets (listes, réserve) sont transparentes : la case vanilla et l'objet restent visibles même si
    le titre est dessiné au-dessus des objets ;
  - textes dynamiques : petites capitales 5 px (même dessin que les libellés du fond) dans les polices
    `refuge:t<y>` (une par hauteur y, ascent = 13 - (y - 2)), largeurs calculées comme le client (colonne opaque
    la plus à droite + 1) et recopiées dans Fonds.java.
  - cases cliquables : objet invisible `refuge:gui/vide` (texture 16x16 transparente) avec nom et description.

Écrit : les fichiers du pack, ../src/main/java/fr/eemicraft/refuge/ui/Fonds.java (généré, ne pas modifier à la
main) et ../apercus/menus.png (chaque menu sur un coffre vanilla, avec ses icônes, calques et textes d'exemple).
Nécessite Pillow et le jar client 26.3 (icônes vanilla, coffre vanilla des aperçus).
"""
import io
import json
import os
import zipfile

from PIL import Image, ImageDraw

ICI = os.path.dirname(os.path.abspath(__file__))
RACINE = os.path.dirname(ICI)
NS = "refuge"
T = 14                 # dépassement de l'onglet au-dessus du coffre (px)
ASCENT_BG = 13 + T
ADV_BG = 177
JAVA = os.path.join(RACINE, "src", "main", "java", "fr", "eemicraft", "refuge", "ui", "Fonds.java")

# ------------------------------------------------------------------ couleurs (coffre vanilla 26.3, relevées)
PANEL = (198, 198, 198, 255)
WHITE = (255, 255, 255, 255)
SHADOW = (85, 85, 85, 255)
BLACK = (0, 0, 0, 255)
TXT = (63, 63, 63, 255)          # libellés (gris foncé des titres vanilla)
BTN = (208, 208, 208, 255)       # bouton (un peu plus clair que le panneau)
BTN_EDGE = (34, 34, 34, 255)
BTN_SH = (139, 139, 139, 255)
FIELD = (178, 178, 178, 255)     # champ creux de l'onglet
GOLD = (232, 176, 32, 255)
GOLD_D = (150, 104, 10, 255)
GREEN = (72, 190, 72, 255)
GREEN_D = (30, 110, 30, 255)
RED = (214, 64, 64, 255)
RED_D = (120, 24, 24, 255)

# ------------------------------------------------------------------ petites capitales 5 px (+2 lignes d'accent)
F5 = {
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    "C": [".##", "#..", "#..", "#..", ".##"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "E": ["###", "#..", "##.", "#..", "###"], "F": ["###", "#..", "##.", "#..", "#.."],
    "G": [".##", "#..", "#.#", "#.#", ".##"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "I": ["#", "#", "#", "#", "#"], "J": ["..#", "..#", "..#", "#.#", ".#."],
    "K": ["#.#", "#.#", "##.", "#.#", "#.#"], "L": ["#..", "#..", "#..", "#..", "###"],
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#"], "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
    "O": [".#.", "#.#", "#.#", "#.#", ".#."], "P": ["##.", "#.#", "##.", "#..", "#.."],
    "Q": [".#.", "#.#", "#.#", "##.", ".##"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "U": ["#.#", "#.#", "#.#", "#.#", "###"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "W": ["#...#", "#...#", "#.#.#", "##.##", "#...#"], "X": ["#.#", "#.#", ".#.", "#.#", "#.#"],
    "Y": ["#.#", "#.#", ".#.", ".#.", ".#."], "Z": ["###", "..#", ".#.", "#..", "###"],
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#", "##", ".#", ".#", ".#"],
    "2": ["##.", "..#", ".#.", "#..", "###"], "3": ["##.", "..#", ".#.", "..#", "##."],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "##.", "..#", "##."],
    "6": [".##", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "##."],
    ".": [".", ".", ".", ".", "#"], ":": [".", "#", ".", "#", "."], "!": ["#", "#", "#", ".", "#"],
    "?": ["##.", "..#", ".#.", "...", ".#."], "'": ["#", "#", ".", ".", "."],
    "-": ["...", "...", "###", "...", "..."], "+": ["...", ".#.", "###", ".#.", "..."],
    "/": ["..#", "..#", ".#.", "#..", "#.."], "(": [".#", "#.", "#.", "#.", ".#"],
    ")": ["#.", ".#", ".#", ".#", "#."], "%": ["#.#", "..#", ".#.", "#..", "#.#"],
    "_": ["...", "...", "...", "...", "###"], "·": [".", ".", "#", ".", "."],
    "×": ["...", "#.#", ".#.", "#.#", "..."], ",": [".", ".", ".", ".", "#"],
    "⚙": [".#.#.", "#####", "##.##", "#####", ".#.#."],      # ⚙ Ferraille
}
ACCENTS = {"É": ("E", ["..#", ".#."]), "È": ("E", ["#..", ".#."]), "Ê": ("E", [".#.", "#.#"]),
           "À": ("A", ["#..", ".#."]), "Ô": ("O", [".#.", "#.#"]), "Â": ("A", [".#.", "#.#"]),
           "Î": ("I", ["#", "."])}
for ch, (base, acc) in ACCENTS.items():
    F5[ch] = base  # marqué, résolu dans glyph_rows
SPACE_ADV = 3


def glyph_rows(ch):
    """8 lignes : 0-1 accent, 2-6 lettre, 7 jambage (cédille)."""
    g = F5[ch]
    if isinstance(g, str):  # lettre accentuée
        base, acc = ACCENTS[ch]
        rows = F5[base]
        w = len(rows[0])
        acc = [a.ljust(w, ".")[:w] if len(a) <= w else a[:w] for a in acc]
        return acc + rows + ["." * w]
    w = len(g[0])
    tail = "." * w
    if ch == ",":
        return ["."] * 2 + ["."] * 4 + ["#", "#"]
    return [tail, tail] + g + [tail]


def glyph_width(ch):
    if ch == " ":
        return SPACE_ADV
    rows = glyph_rows(ch)
    w = 0
    for r in rows:
        for i, c in enumerate(r):
            if c == "#":
                w = max(w, i + 1)
    return w + 1  # avance client = largeur réelle + 1


def text_width(s):
    return sum(glyph_width(c) for c in s)


def draw_text(img, x, y, s, color, oy=0):
    """y = haut des capitales (gui) ; oy = décalage image (T pour les fonds)."""
    for ch in s:
        if ch == " ":
            x += SPACE_ADV
            continue
        rows = glyph_rows(ch)
        for j, r in enumerate(rows):
            for i, c in enumerate(r):
                if c == "#":
                    px, py = x + i, y - 2 + j + oy
                    if 0 <= px < img.width and 0 <= py < img.height:
                        img.putpixel((px, py), color)
        x += glyph_width(ch)


def norm(s):
    out = []
    for ch in s.upper():
        if ch == " " or ch in F5:
            out.append(ch)
        else:
            raise SystemExit(f"ERREUR : caractère sans glyphe dans un libellé : {ch!r} ({s!r})")
    return "".join(out)


# ------------------------------------------------------------------ icônes 16x16
IP = {".": (0, 0, 0, 0), "X": (24, 24, 24, 255), "K": (60, 60, 60, 255), "D": (100, 100, 100, 255),
      "G": (150, 150, 150, 255), "L": (210, 210, 210, 255), "W": (255, 255, 255, 255),
      "y": (255, 236, 110, 255), "Y": (250, 200, 40, 255), "z": (214, 150, 20, 255), "Z": (150, 96, 10, 255),
      "g": (120, 220, 90, 255), "G2": None, "v": (60, 170, 50, 255), "V": (30, 100, 30, 255),
      "r": (240, 80, 70, 255), "R": (170, 30, 30, 255), "n": (100, 14, 14, 255),
      "b": (120, 170, 240, 255), "B": (60, 100, 200, 255), "N": (30, 50, 120, 255),
      "h": (180, 130, 70, 255), "H": (130, 88, 42, 255), "j": (86, 56, 26, 255),
      "s": (236, 200, 160, 255), "S": (200, 156, 110, 255), "o": (250, 250, 236, 255), "O": (215, 215, 200, 255),
      "p": (150, 110, 200, 255), "P": (96, 64, 150, 255)}
IP.pop("G2")


def grid(rows):
    assert len(rows) == 16, rows
    im = Image.new("RGBA", (16, 16))
    for y, r in enumerate(rows):
        assert len(r) == 16, (y, r)
        for x, c in enumerate(r):
            im.putpixel((x, y), IP[c])
    return im


OWN = {
    "jour": [
        "................", ".......YY.......", "...Y...YY...Y...", "....Y......Y....", ".......XX.......",
        ".....XXyyXX.....", "....XyyyyYYX....", "YY..XyyyYYYX..YY", "YY..XyyYYYzX..YY", "....XyYYYzzX....",
        ".....XXzzXX.....", ".......XX.......", "....Y......Y....", "...Y...YY...Y...", ".......YY.......",
        "................"],
    "nuit": [
        "................", "..W.........b...", ".......XXXX.....", ".....XXbbbX.....", "....XbbbXX...W..",
        "....XbbX........", "...XbbX.........", "...XbBX.....b...", "...XbBX.........", "...XbBBX........",
        "....XBBBXX......", "....XBBBNNXX..X.", ".....XXNNNNNXX..", ".W.....XXXXX....", "..........W.....",
        "................"],
    "pret": [
        "................", "................", "................", "............XX..", "...........XgX..",
        "..........XgvX..", ".........XgvX...", "..XX....XgvX....", ".XgvX..XgvX.....", "..XgvXXgvX......",
        "...XgvgvX.......", "....XgvX........", ".....XX.........", "................", "................",
        "................"],
    "croix": [
        "................", "................", "..XX........XX..", ".XrRX......XrRX.", "..XrRX....XrRX..",
        "...XrRX..XrRX...", "....XrRXXrRX....", ".....XrRrRX.....", ".....XrRrRX.....", "....XrRXXrRX....",
        "...XrRX..XrRX...", "..XrRX....XrRX..", ".XrRX......XrRX.", "..XX........XX..", "................",
        "................"],
    "escouade": [
        "................", "......XXXX......", ".....XssssX.....", "..XX.XsSsSX.XX..", ".XssXXssssXXssX.",
        ".XsSXXXXXXXXsSX.", ".XssXbbbbbbXssX.", "XXXXXbBBBBbXXXXX", "XgggXbBBBBbXrrrX", "XgVVXbBBBBbXrRRX",
        "XgVVXbBBBBbXrRRX", "XgVVXbBNNBbXrRRX", "XgVVXBNXXNBXrRRX", ".XXX.XX..XX.XXX.", "................",
        "................"],
    "inviter": [
        "................", "....XXXX........", "...XssssX.......", "...XsSsSX.......", "...XssssX.......",
        "....XXXX........", "..XbbbbbbX......", ".XbBBBBBBbX.....", ".XbBBBBBBbX.XgX.", ".XbBBBBBBbXXgggX",
        ".XbBBBBBBbX.XgX.", ".XbBBNNBBbX.....", ".XBNNXXNNBX.....", ".XXX....XXX.....", "................",
        "................"],
    "creer": [
        "................", "...XXXXXXXXX....", "...XrrrrrrrRX...", "...XrRRRRRRRX...", "...XrRrrrrRRX...",
        "...XrRRRRRRX....", "...XRRRRRRX.....", "...XXXXXXX......", "...XH...........", "...XH...........",
        "...XH...........", "...XH...........", "...XH...........", "..XXHX..........", ".XjjjjX.........",
        "................"],
    "drapeau_vert": [
        "................", "...XXXXXXXXX....", "...XgggggggVX...", "...XgVVVVVVVX...", "...XgVgggggVX...",
        "...XgVVVVVVX....", "...XVVVVVVX.....", "...XXXXXXX......", "...XH...........", "...XH...........",
        "...XH...........", "...XH...........", "...XH...........", "..XXHX..........", ".XjjjjX.........",
        "................"],
    "suiv": [
        "................", "................", "........XX......", "........XbX.....", "........XbbX....",
        "..XXXXXXXbbbX...", "..XbbbbbbbbbbX..", "..XbBBBBBBBBBbX.", "..XBBBBBBBBBBNX.", "..XNNNNNNNBBNX..",
        "........XBNX....", "........XNX.....", "........XX......", "................", "................",
        "................"],
    "couronne": [
        "................", "................", "................", "..X....XX....X..", ".XyX..XyyX..XyX.",
        ".XyX..XyYX..XyX.", ".XyYXXyYYzXXYzX.", ".XyYYyYYYzzYYzX.", ".XyYYYYrYYYYzzX.", ".XyYYYrRrYYYzzX.",
        ".XYYYYYrYYYzzZX.", ".XzzzzzzzzzzZZX.", ".XZZZZZZZZZZZZX.", "..XXXXXXXXXXXX..", "................",
        "................"],
    "coffre": [
        "................", "................", "..XXXXXXXXXXXX..", ".XhhhhhhhhhhhhX.", ".XhHHHHHHHHHHjX.",
        ".XhHHHHHHHHHHjX.", ".XXXXXXGGXXXXXX.", ".XhhhhhXLXhhhhX.", ".XhHHHHXDXHHHjX.", ".XhHHHHHXHHHHjX.",
        ".XhHHHHHHHHHHjX.", ".XhHHHHHHHHHHjX.", ".XjjjjjjjjjjjjX.", "..XXXXXXXXXXXX..", "................",
        "................"],
    "bouclier": [
        "................", "..XXXXXXXXXXXX..", "..XLLLLWLLLLGX..", "..XLGGGWGGGGDX..", "..XLGbbWbbbGDX..",
        "..XLGbBWBBbGDX..", "..XLGWWWWWWGDX..", "..XLGbBWBBbGDX..", "..XLGbBWBBbGDX..", "...XLGbWBbGDX...",
        "...XLGGWGGGDX...", "....XLGWGGDX....", ".....XLGGDX.....", "......XDDX......", ".......XX.......",
        "................"],
    "crane": [
        "................", "................", "....XXXXXXXX....", "...XOooooooOX...", "..XOooooooooOX..",
        "..XooooooooooX..", "..XoXXXooXXXoX..", "..XoXXXooXXXoX..", "..XoXXXooXXXoX..", "..XOooooXoooOX..",
        "...XOooXXoooX...", "....XoOoOoOX....", "....XoXoXoXX....", ".....XXXXXX.....", "................",
        "................"],
    "tete": [
        "................", "................", "....XXXXXXXX....", "...XjjjjjjjjX...", "...XjjjjjjjjX...",
        "...XsjjsjjjsX...", "...XssssssssX...", "...XsWBssBWsX...", "...XssssssssX...", "...XssShhSssX...",
        "...XsshhhhssX...", "...XssssssssX...", "....XXXXXXXX....", "................", "................",
        "................"],
    "etoile": [
        "................", ".......XX.......", "......XyyX......", "......XyYX......", ".XXXXXXyYXXXXXX.",
        ".XyyyyyyYYYYYzX.", "..XyyyyYYYYYzX..", "...XyyYYYYYzX...", "....XyYYYYzX....", "...XyYYYzYYzX...",
        "...XyYYXXzYzX...", "..XyYXX..XXzzX..", "..XXX......XXX..", "................", "................",
        "................"],
    "parchemin": [
        "................", "..XXXXXXXXXXX...", ".XSoooooooooSX..", ".XSXXXXXXXXXSX..", "..XooooooooooX..",
        "..XoKKKKKKKKoX..", "..XooooooooooX..", "..XoKKKKKKoooX..", "..XooooooooooX..", "..XoKKKKKKKKoX..",
        "..XooooooooooX..", "..XoKKKKKooooX..", ".XSXXXXXXXXXSX..", ".XSoooooooooSX..", "..XXXXXXXXXXX...",
        "................"],
}

VANILLA_ICONS = {"partir": "item/compass_00", "file": "item/spyglass", "quitter": "item/oak_door",
                 "temps": "item/clock_00", "zombies": "item/rotten_flesh", "releves": "item/totem_of_undying",
                 "xp": "item/experience_bottle", "expedition": "item/map", "acheter": "item/bundle",
                 "bilan": "item/written_book", "rejoindre": "item/lead"}


def icons(jar, item_images):
    out = {}
    for k, rows in OWN.items():
        if rows is not None:
            out[k] = grid(rows)
    out["prec"] = out["suiv"].transpose(Image.FLIP_LEFT_RIGHT)
    out["retour"] = out["prec"]
    out["exclure"] = out["croix"]
    out["chef"] = out["couronne"]
    out["reserve"] = out["coffre"]
    out["assurance"] = out["bouclier"]
    out["extrait"] = out["drapeau_vert"]
    out["mort"] = out["crane"]
    out["profil"] = out["tete"]
    out["acte"] = out["parchemin"]
    out["niveau"] = out["etoile"]
    out["ferraille"] = item_images["ferraille"]
    out["vendre"] = item_images["ferraille"]
    for k, ref in VANILLA_ICONS.items():
        im = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/{ref}.png"))).convert("RGBA")
        out[k] = im.crop((0, 0, 16, 16)) if im.height > 16 else im
    return out


# ------------------------------------------------------------------ dispositions
# Bouton : (id, col, ligne, largeur, hauteur, icône, libellé ou None). Cases : listes de (col, ligne).
# Texte : (id, x, y haut des capitales, alignement 'g'|'c'|'d', largeur max). Tuile : bouton non cliquable.
def btn(i, c, r, w, h, icon, label=None):
    return ("btn", i, c, r, w, h, icon, label)


HEAD = [btn("fermer", 0, 0, 1, 1, "x"), btn("info", 8, 0, 1, 1, "i")]


def row_y(r):
    return 17 + 18 * r


def cx(c):
    return 7 + 18 * c + 9


MENUS = [
    dict(id="expedition", titre="EXPÉDITION", rows=6, el=HEAD + [
        ("cells", "zones", [(c, 1) for c in range(1, 8)]),
        ("text", "zone", 88, row_y(2) + 6, "c", 160),
        btn("jour", 0, 3, 2, 2, "jour", "JOUR"), btn("nuit", 2, 3, 2, 2, "nuit", "NUIT"),
        btn("pret", 5, 3, 2, 2, "pret", "PRÊT"), btn("partir", 7, 3, 2, 2, "partir", "PARTIR"),
        ("cells", "membres", [(c, 5) for c in range(0, 4)]),
        btn("escouade", 7, 5, 1, 1, "escouade"), btn("file", 8, 5, 1, 1, "file"),
    ], overlays=[("sel", "jour"), ("sel", "nuit"), ("on", "pret"), ("on", "file"), ("off", "partir")]
        + [("cellsel", "zones", i) for i in range(7)]
        + [(k, "membres", i) for i in range(4) for k in ("chef", "ok", "non")]),
    dict(id="escouade", titre="ESCOUADE", rows=5, el=HEAD + [
        btn("creer", 0, 1, 2, 2, "creer", "CRÉER"), btn("inviter", 2, 1, 2, 2, "inviter", "INVITER"),
        btn("rejoindre", 5, 1, 2, 2, "rejoindre", "ENTRER"), btn("quitter", 7, 1, 2, 2, "quitter", "QUITTER"),
        ("cells", "membres", [(c, 3) for c in (1, 3, 5, 7)]),
    ] + [("text", f"nom{i}", cx(c), row_y(4) + 6, "c", 34) for i, c in enumerate((1, 3, 5, 7))],
        overlays=[("chef", "membres", i) for i in range(4)] + [("off", b) for b in ("creer", "inviter", "rejoindre",
                                                                                    "quitter")]),
    dict(id="liste_inviter", titre="INVITER", rows=6, el=HEAD + [
        ("cells", "liste", [(c, r) for r in range(1, 5) for c in range(1, 8)]),
        btn("retour", 0, 5, 1, 1, "retour"), btn("prec", 3, 5, 1, 1, "prec"), btn("suiv", 5, 5, 1, 1, "suiv"),
        ("text", "page", cx(4), row_y(5) + 7, "c", 18)], overlays=[]),
    dict(id="liste_rejoindre", titre="REJOINDRE", rows=6, el=HEAD + [
        ("cells", "liste", [(c, r) for r in range(1, 5) for c in range(1, 8)]),
        btn("retour", 0, 5, 1, 1, "retour"), btn("prec", 3, 5, 1, 1, "prec"), btn("suiv", 5, 5, 1, 1, "suiv"),
        ("text", "page", cx(4), row_y(5) + 7, "c", 18)], overlays=[]),
    dict(id="membre", titre="MEMBRE", rows=4, el=HEAD + [
        ("cells", "tete", [(1, 1)]), ("text", "nom", cx(1), row_y(2) + 6, "c", 52),
        btn("exclure", 3, 1, 2, 2, "exclure", "EXCLURE"), btn("chef", 6, 1, 2, 2, "chef", "CHEF"),
        btn("retour", 0, 3, 1, 1, "retour")], overlays=[]),
    dict(id="reserve", titre="RÉSERVE", rows=4, el=HEAD + [
        ("cells", "stock", [(c, r) for r in range(1, 4) for c in range(9)])], overlays=[]),
    dict(id="boutique", titre="ARMURIER", rows=6, el=HEAD + [
        btn("acheter", 0, 1, 2, 2, "acheter", "ACHETER"), btn("vendre", 2, 1, 2, 2, "vendre", "VENDRE"),
        ("label", "TA FERRAILLE", 133, row_y(1) + 6, "c"),
        ("text", "solde", 133, row_y(2) - 2, "c", 70),
        ("cells", "articles", [(c, r) for r in range(3, 6) for c in range(1, 8)]),
        btn("prec", 0, 5, 1, 1, "prec"), btn("suiv", 8, 5, 1, 1, "suiv")],
        overlays=[("sel", "acheter"), ("sel", "vendre")]),
    dict(id="assurance", titre="ASSURANCE", rows=5, el=HEAD + [
        ("cells", "assures", [(2, 1), (4, 1), (6, 1)])]
        + [("text", f"prix{i}", cx(c), row_y(2) + 6, "c", 34) for i, c in enumerate((2, 4, 6))] + [
        ("label", "CLIQUE UN OBJET DE TON SAC", 88, row_y(3) + 3, "c"),
        ("label", "POUR L'ASSURER (3 AU PLUS)", 88, row_y(3) + 11, "c"),
        ("text", "solde", 88, row_y(4) + 6, "c", 150)], overlays=[]),
    dict(id="bilan", titre="BILAN", rows=6, el=HEAD + [
        ("tile", "issue", 0, 1, 2, 2, None, "ISSUE"), ("tile", "temps", 2, 1, 2, 2, "temps", "TEMPS"),
        ("tile", "zombies", 5, 1, 2, 2, "zombies", "ZOMBIES"), ("tile", "releves", 7, 1, 2, 2, "releves", "RELÈVES"),
        ("text", "issue", cx(0) + 9, row_y(1) + 28, "c", 34), ("text", "temps", cx(2) + 9, row_y(1) + 28, "c", 34),
        ("text", "zombies", cx(5) + 9, row_y(1) + 28, "c", 34),
        ("text", "releves", cx(7) + 9, row_y(1) + 28, "c", 34),
        ("icon", "ferraille", 1, 3), ("text", "ferraille", 7 + 18 * 2 + 2, row_y(3) + 6, "g", 70),
        ("icon", "xp", 5, 3), ("text", "xp", 7 + 18 * 6 + 2, row_y(3) + 6, "g", 52),
        ("cells", "butin", [(c, r) for r in (4, 5) for c in range(9)])],
        overlays=[("icon", "issue", "extrait"), ("icon", "issue", "mort")]),
    dict(id="profil", titre="PROFIL", rows=5, el=HEAD + [
        btn("expedition", 0, 1, 2, 2, "expedition", "DÉPART"), btn("escouade", 2, 1, 2, 2, "escouade", "ESCOUADE"),
        btn("reserve", 5, 1, 2, 2, "reserve", "RÉSERVE"), btn("bilan", 7, 1, 2, 2, "bilan", "BILAN"),
        ("text", "ligne1", 88, row_y(3) + 6, "c", 160), ("text", "ligne2", 88, row_y(4) + 6, "c", 160)],
        overlays=[("off", "reserve"), ("off", "bilan")]),
]

# ------------------------------------------------------------------ dessin


class Canvas:
    def __init__(self):
        self.img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def rect(self, x0, y0, x1, y1, c):  # coordonnées gui, bornes incluses
        self.d.rectangle([x0, y0 + T, x1, y1 + T], fill=c)

    def px(self, x, y, c):
        self.img.putpixel((x, y + T), c)

    def clear(self, x0, y0, x1, y1):
        self.d.rectangle([x0, y0 + T, x1, y1 + T], fill=(0, 0, 0, 0))

    def paste(self, im, x, y, scale=1):
        if scale != 1:
            im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        self.img.alpha_composite(im, (x, y + T))

    def text(self, x, y, s, c, align="g"):
        w = text_width(s) - 1
        if align == "c":
            x = x - w // 2
        elif align == "d":
            x = x - w
        draw_text(self.img, x, y, s, c, oy=T)


def raised(cv, x0, y0, x1, y1, fill=BTN, edge=BTN_EDGE):
    """Bouton façon Minecraft : contour sombre, reflet blanc en haut à gauche, ombre en bas à droite."""
    cv.rect(x0, y0, x1, y1, edge)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
    cv.rect(x0 + 1, y0 + 1, x1 - 2, y0 + 1, WHITE)
    cv.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 2, WHITE)
    cv.rect(x0 + 2, y1 - 1, x1 - 1, y1 - 1, BTN_SH)
    cv.rect(x1 - 1, y0 + 2, x1 - 1, y1 - 1, BTN_SH)


def sunken(cv, x0, y0, x1, y1, fill=FIELD):
    cv.rect(x0, y0, x1, y1, SHADOW)
    cv.rect(x0 + 1, y0 + 1, x1, y1, WHITE)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)


def panel(cv, vanilla54, rows, title):
    h = 17 + 18 * rows  # partie haute du coffre (lignes 0 .. h-1)
    top = vanilla54.crop((0, 0, 176, h))
    cv.img.alpha_composite(top, (0, T))
    cv.rect(4, 4, 171, h - 1, PANEL)  # efface la grille vanilla, garde les bords
    # onglet de titre (petites capitales), centré, qui dépasse de T px au-dessus du coffre
    tw = text_width(title) - 1
    w = max(64, tw + 24)
    x0 = 88 - w // 2
    x1 = x0 + w - 1
    y0, y1 = -T + 1, 12
    cv.rect(x0, y0, x1, y1, BLACK)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL)
    cv.rect(x0 + 1, y0 + 1, x1 - 2, y0 + 2, WHITE)
    cv.rect(x0 + 1, y0 + 1, x0 + 2, y1 - 2, WHITE)
    cv.rect(x1 - 2, y0 + 2, x1 - 1, y1 - 1, SHADOW)
    cv.rect(x0 + 2, y1 - 2, x1 - 1, y1 - 1, SHADOW)
    sunken(cv, x0 + 5, y0 + 4, x1 - 5, y1 - 4)
    cv.text(88, (y0 + y1) // 2 - 2, title, TXT, "c")
    assert cv.img.getpixel((175, T + 40))[3] == 255
    return h


def small_symbol(cv, x, y, kind):
    """✕ (fermer) ou i (infos) dessinés en pixels au centre d'un petit bouton 16x16."""
    if kind == "x":
        for k in range(7):
            for dx in (0, 1):
                cv.px(x + 4 + k + dx, y + 4 + k, RED_D)
                cv.px(x + 10 - k + dx, y + 4 + k, RED_D)
    else:
        cv.rect(x + 7, y + 3, x + 8, y + 4, (40, 70, 160, 255))
        cv.rect(x + 7, y + 6, x + 8, y + 12, (40, 70, 160, 255))
        cv.rect(x + 6, y + 6, x + 6, y + 6, (40, 70, 160, 255))
        cv.rect(x + 6, y + 12, x + 9, y + 12, (40, 70, 160, 255))


def button_box(c, r, w, h):
    x0, y0 = 7 + 18 * c, 17 + 18 * r
    return x0 + 1, y0 + 1, x0 + 18 * w - 2, y0 + 18 * h - 2


def draw_button(cv, el, ic, tile=False):
    _, bid, c, r, w, h, icon, label = el
    x0, y0, x1, y1 = button_box(c, r, w, h)
    if tile:
        sunken(cv, x0, y0, x1, y1, (186, 186, 186, 255))
    else:
        raised(cv, x0, y0, x1, y1)
    iw = x1 - x0 + 1
    if w == 1 and h == 1:
        if icon in ("x", "i"):
            small_symbol(cv, x0, y0, icon)
        else:
            cv.paste(ic[icon], x0, y0)
        return
    if tile:
        cv.text((x0 + x1 + 1) // 2, y0 + 4, label, TXT, "c")
        if icon:
            cv.paste(ic[icon], (x0 + x1 + 1) // 2 - 8, y0 + 10)
        return
    lw = text_width(label) - 1
    if lw > iw - 3:
        raise SystemExit(f"ERREUR : libellé trop long pour le bouton {bid} : {label} ({lw} px > {iw - 3})")
    scale = 2 if (h >= 3 and w >= 3) else 1
    ih = 16 * scale
    gap = 3
    total = ih + gap + 5
    top = y0 + (y1 - y0 + 1 - total) // 2
    cv.paste(ic[icon], (x0 + x1 + 1) // 2 - ih // 2, top, scale)
    cv.text((x0 + x1 + 1) // 2, top + ih + gap, label, TXT, "c")


def frame(cv, x0, y0, x1, y1, c, dark, width=2):
    for k in range(width):
        col = c if k == 0 else dark
        cv.rect(x0 + k, y0 + k, x1 - k, y0 + k, col)
        cv.rect(x0 + k, y1 - k, x1 - k, y1 - k, col)
        cv.rect(x0 + k, y0 + k, x0 + k, y1 - k, col)
        cv.rect(x1 - k, y0 + k, x1 - k, y1 - k, col)


def cell_box(c, r):
    x0, y0 = 7 + 18 * c, 17 + 18 * r
    return x0, y0, x0 + 17, y0 + 17


def build_menus(vanilla54, ic):
    bases, overlays, layouts = {}, {}, {}
    for m in MENUS:
        cv = Canvas()
        title = norm(m["titre"])
        panel(cv, vanilla54, m["rows"], title)
        lay = {"rows": m["rows"], "buttons": {}, "cells": {}, "texts": {}}
        btns = {}
        for el in m["el"]:
            kind = el[0]
            if kind in ("btn", "tile"):
                draw_button(cv, el, ic, tile=(kind == "tile"))
                _, bid, c, r, w, h = el[:6]
                btns[bid] = (c, r, w, h)
                if kind == "btn":
                    lay["buttons"][bid] = [(r + j) * 9 + c + i for j in range(h) for i in range(w)]
            elif kind == "cells":
                lay["cells"][el[1]] = [r * 9 + c for c, r in el[2]]
                for c, r in el[2]:
                    x0, y0, x1, y1 = cell_box(c, r)
                    cv.clear(x0, y0, x1, y1)  # case vanilla visible (objet réel)
            elif kind == "text":
                _, tid, x, y, align, maxw = el
                lay["texts"][tid] = (x, y, align, maxw)
            elif kind == "label":
                _, s, x, y, align = el
                cv.text(x, y, norm(s), TXT, align)
            elif kind == "icon":
                _, name, c, r = el
                x0, y0, _, _ = cell_box(c, r)
                cv.paste(ic[name], x0 + 1, y0 + 1)
        # garde-fou : chaque case du menu appartient à un seul bouton ou une seule liste
        used = {}
        for k, sl in list(lay["buttons"].items()) + list(lay["cells"].items()):
            for s in sl:
                if s in used:
                    raise SystemExit(f"ERREUR : case {s} du menu {m['id']} utilisée par {used[s]} et {k}")
                if s >= 9 * m["rows"]:
                    raise SystemExit(f"ERREUR : case {s} hors du menu {m['id']}")
                used[s] = k
        bases[m["id"]] = cv.img
        # calques
        for ov in m["overlays"]:
            ocv = Canvas()
            ocv.px(175, 40, BLACK)  # même pixel que le bord du fond : avance identique (177)
            kind = ov[0]
            if kind in ("sel", "on", "off"):
                c, r, w, h = btns[ov[1]]
                x0, y0, x1, y1 = button_box(c, r, w, h)
                if kind == "sel":
                    frame(ocv, x0 - 1, y0 - 1, x1 + 1, y1 + 1, GOLD, GOLD_D)
                elif kind == "on":
                    frame(ocv, x0 - 1, y0 - 1, x1 + 1, y1 + 1, GREEN, GREEN_D)
                    if w > 1:
                        ocv.paste(ic["pret"].resize((10, 10), Image.NEAREST), x1 - 11, y0 + 1)
                else:
                    ocv.d.rectangle([x0, y0 + T, x1, y1 + T], fill=(40, 40, 40, 120))
                name = f"{m['id']}:{ov[0]}:{ov[1]}"
            elif kind in ("cellsel", "chef", "ok", "non"):
                cells = [(cc, rr) for e in m["el"] if e[0] == "cells" and e[1] == ov[1] for cc, rr in e[2]]
                c, r = cells[ov[2]]
                x0, y0, x1, y1 = cell_box(c, r)
                col = {"cellsel": (GOLD, GOLD_D), "chef": (GOLD, GOLD_D), "ok": (GREEN, GREEN_D),
                       "non": (RED, RED_D)}[kind]
                frame(ocv, x0 - 1, y0 - 1, x1 + 1, y1 + 1, *col)
                if kind == "chef":
                    ocv.paste(ic["couronne"].resize((8, 8), Image.NEAREST), x1 - 6, y0 - 5)
                name = f"{m['id']}:{kind}:{ov[1]}:{ov[2]}"
            elif kind == "icon":
                c, r, w, h = btns[ov[1]]
                x0, y0, x1, y1 = button_box(c, r, w, h)
                ocv.paste(ic[ov[2]], (x0 + x1 + 1) // 2 - 8, y0 + 10)
                name = f"{m['id']}:icon:{ov[1]}:{ov[2]}"
            else:
                raise SystemExit(f"calque inconnu {ov}")
            if ocv.img.getpixel((175, T + 40)) != BLACK or bases[m["id"]].getpixel((175, T + 40)) != BLACK:
                raise SystemExit("ERREUR : pixel d'avance des calques")
            overlays[name] = ocv.img
        layouts[m["id"]] = lay
    return bases, overlays, layouts


# ------------------------------------------------------------------ police des textes

TEXT_CHARS = [c for c in F5 if c != ","] + [","]


def text_font_png():
    cols = 16
    rows = (len(TEXT_CHARS) + cols - 1) // cols
    img = Image.new("RGBA", (cols * 8, rows * 8), (0, 0, 0, 0))
    lines = []
    for i, ch in enumerate(TEXT_CHARS):
        x0, y0 = (i % cols) * 8, (i // cols) * 8
        for j, r in enumerate(glyph_rows(ch)):
            for k, c in enumerate(r):
                if c == "#":
                    img.putpixel((x0 + k, y0 + j), WHITE)
    for r in range(rows):
        s = "".join(TEXT_CHARS[r * cols:(r + 1) * cols])
        s = s + "\u0000" * (cols - len(s))
        lines.append(s)
    return img, lines


def png_bytes(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()


NEG = [(0xF800 + i, -(1 << i)) for i in range(9)]   # -1 .. -256
POS = [(0xF810 + i, (1 << i)) for i in range(9)]     # +1 .. +256


def build(jar_path, item_images):
    """Renvoie (fichiers du pack, données des menus pour l'aperçu et le Java)."""
    jar = zipfile.ZipFile(jar_path)
    vanilla54 = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/gui/container/generic_54.png"))).convert("RGBA")
    ic = icons(jar, item_images)
    bases, overlays, layouts = build_menus(vanilla54, ic)
    files = {}
    providers = [{"type": "space", "advances": {chr(c): v for c, v in NEG + POS}}]
    glyphs = {}
    for i, (mid, img) in enumerate(bases.items()):
        ch = chr(0xE000 + i)
        glyphs["bg:" + mid] = ch
        files[f"assets/{NS}/textures/font/gui/bg_{mid}.png"] = png_bytes(img)
        providers.append({"type": "bitmap", "file": f"{NS}:font/gui/bg_{mid}.png", "ascent": ASCENT_BG,
                          "height": 256, "chars": [ch]})
    for i, (oid, img) in enumerate(overlays.items()):
        ch = chr(0xE100 + i)
        glyphs["ov:" + oid] = ch
        fname = oid.replace(":", "_")
        files[f"assets/{NS}/textures/font/gui/ov_{fname}.png"] = png_bytes(img)
        providers.append({"type": "bitmap", "file": f"{NS}:font/gui/ov_{fname}.png", "ascent": ASCENT_BG,
                          "height": 256, "chars": [ch]})
    if len(overlays) > 255 or len(bases) > 255:
        raise SystemExit("ERREUR : trop de glyphes")
    files[f"assets/{NS}/font/gui.json"] = json.dumps({"providers": providers}, indent=1, ensure_ascii=True).encode()
    # textes : une police par hauteur
    tpng, tlines = text_font_png()
    files[f"assets/{NS}/textures/font/gui/texte.png"] = png_bytes(tpng)
    ys = sorted({t[1] for lay in layouts.values() for t in lay["texts"].values()})
    for y in ys:
        asc = 13 - (y - 2)
        if asc > 8:
            raise SystemExit(f"ERREUR : texte trop haut (y {y})")
        f = {"providers": [{"type": "space", "advances": {" ": SPACE_ADV}},
                           {"type": "bitmap", "file": f"{NS}:font/gui/texte.png", "ascent": asc, "height": 8,
                            "chars": tlines}]}
        files[f"assets/{NS}/font/t{y}.json"] = json.dumps(f, indent=1, ensure_ascii=True).encode()
    # objet invisible des cases cliquables
    files[f"assets/{NS}/textures/item/gui/vide.png"] = png_bytes(Image.new("RGBA", (16, 16), (0, 0, 0, 0)))
    data = dict(bases=bases, overlays=overlays, layouts=layouts, glyphs=glyphs, ys=ys, icons=ic, jar=jar,
                vanilla54=vanilla54)
    return files, data


# ------------------------------------------------------------------ Fonds.java

def jchar(c):
    """Littéral Java d'un caractère. Les \\uXXXX sont remplacés AVANT la compilation : ', " et \\ gardent leur
    échappement simple (jamais \\u0027, \\u0022, \\u005c)."""
    if c in "'\"\\":
        return "\\" + c
    return c if 32 <= ord(c) < 127 else "\\u%04x" % ord(c)


def jstr(s):
    return "\"" + "".join(jchar(c) for c in s) + "\""


def write_java(data):
    L = []
    L.append("package fr.eemicraft.refuge.ui;")
    L.append("")
    L.append("import java.util.HashMap;")
    L.append("import java.util.Map;")
    L.append("")
    L.append("/** GÉNÉRÉ par pack/gen_ui.py (ne pas modifier à la main) : glyphes et dispositions des menus du pack Refuge. */")
    L.append("public final class Fonds {")
    L.append(f"    public static final int AVANCE_FOND = {ADV_BG};")
    L.append("    /** Espaces de la police refuge:gui : NEG[i] = -2^i, POS[i] = +2^i. */")
    L.append("    public static final char[] NEG = {" + ", ".join("'\\u%04x'" % c for c, _ in NEG) + "};")
    L.append("    public static final char[] POS = {" + ", ".join("'\\u%04x'" % c for c, _ in POS) + "};")
    L.append(f"    public static final int ESPACE = {SPACE_ADV};")
    L.append("    /** Glyphes : « bg:&lt;menu&gt; » (fond) et « ov:&lt;menu&gt;:&lt;type&gt;:&lt;élément&gt;[:n] » (calques). */")
    L.append("    public static final Map<String, Character> GLYPHES = new HashMap<>();")
    L.append("    /** Avance (px) de chaque caractère des polices de texte refuge:t&lt;y&gt;. */")
    L.append("    public static final Map<Character, Integer> AVANCES = new HashMap<>();")
    L.append("    public record Texte(int x, int y, char align, int max) { }")
    L.append("    public record Disposition(int lignes, Map<String, int[]> boutons, Map<String, int[]> cases,")
    L.append("                              Map<String, Texte> textes) { }")
    L.append("    public static final Map<String, Disposition> MENUS = new HashMap<>();")
    L.append("")
    L.append("    static {")
    for k, ch in data["glyphs"].items():
        L.append(f"        GLYPHES.put({jstr(k)}, '{jchar(ch)}');")
    for ch in TEXT_CHARS:
        L.append(f"        AVANCES.put('{jchar(ch)}', {glyph_width(ch)});")
    for mid, lay in data["layouts"].items():
        L.append(f"        {{ // {mid}")
        L.append("            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();")
        L.append("            Map<String, Texte> t = new HashMap<>();")
        for k, sl in lay["buttons"].items():
            L.append(f"            b.put({jstr(k)}, new int[]{{{', '.join(map(str, sl))}}});")
        for k, sl in lay["cells"].items():
            L.append(f"            c.put({jstr(k)}, new int[]{{{', '.join(map(str, sl))}}});")
        for k, (x, y, al, mx) in lay["texts"].items():
            L.append(f"            t.put({jstr(k)}, new Texte({x}, {y}, '{al}', {mx}));")
        L.append(f"            MENUS.put({jstr(mid)}, new Disposition({lay['rows']}, b, c, t));")
        L.append("        }")
    L.append("    }")
    L.append("")
    L.append("    private Fonds() { }")
    L.append("}")
    src = "\n".join(L) + "\n"
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)


# ------------------------------------------------------------------ aperçus

EXEMPLES = {
    "expedition": dict(ov=["expedition:sel:jour", "expedition:on:pret", "expedition:cellsel:zones:0",
                           "expedition:chef:membres:0", "expedition:ok:membres:1", "expedition:non:membres:2"],
                       items={10: "zone", 46: "tete", 47: "tete", 48: "tete"},
                       txt={"zone": ("LA BOURGADE · ACTE 1 · 1 À 4 JOUEURS", TXT)}),
    "escouade": dict(ov=["escouade:chef:membres:0"], items={28: "tete", 30: "tete"},
                     txt={"nom0": ("ENOSIK", (90, 60, 0, 255)), "nom1": ("MAZD_", TXT)}),
    "liste_inviter": dict(ov=[], items={10: "tete", 11: "tete", 12: "tete"}, txt={"page": ("1/1", TXT)}),
    "liste_rejoindre": dict(ov=[], items={10: "tete"}, txt={"page": ("1/1", TXT)}),
    "membre": dict(ov=[], items={10: "tete"}, txt={"nom": ("LABALEINEMC", TXT)}),
    "reserve": dict(ov=[], items={9: "epee", 10: "ferraille", 19: "conserve"}, txt={}),
    "boutique": dict(ov=["boutique:sel:acheter"], items={28: "epee", 29: "conserve", 30: "ferraille"},
                     txt={"solde": ("1 250 ⚙", (120, 80, 0, 255))}),
    "assurance": dict(ov=[], items={11: "epee"}, txt={"prix0": ("30 ⚙", TXT), "prix1": ("LIBRE", (120, 120, 120, 255)),
                                                       "prix2": ("LIBRE", (120, 120, 120, 255)),
                                                       "solde": ("FERRAILLE : 1 220 ⚙", (120, 80, 0, 255))}),
    "bilan": dict(ov=["bilan:icon:issue:extrait"], items={36: "epee", 37: "ferraille", 38: "conserve"},
                  txt={"issue": ("EXTRAIT", (30, 110, 30, 255)), "temps": ("18:42", TXT), "zombies": ("37", TXT),
                       "releves": ("2", TXT), "ferraille": ("+ 145", (120, 80, 0, 255)), "xp": ("+ 60 XP", (40, 110, 40, 255))}),
    "profil": dict(ov=["profil:off:bilan"], items={},
                   txt={"ligne1": ("1 250 ⚙ · ACTE 1 · NIVEAU 3", TXT), "ligne2": ("XP : 120 / 300", (40, 110, 40, 255))}),
}


def vanilla_chest(v54, rows):
    h = 114 + 18 * rows
    img = Image.new("RGBA", (176, h), (0, 0, 0, 0))
    img.alpha_composite(v54.crop((0, 0, 176, 17 + 18 * rows)), (0, 0))
    img.alpha_composite(v54.crop((0, 126, 176, 222)), (0, 17 + 18 * rows))
    return img


def sample_items(jar, ic):
    def tex(ref):
        return Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/{ref}.png"))).convert("RGBA").crop((0, 0, 16, 16))
    return {"tete": ic["tete"], "zone": tex("item/filled_map"), "epee": tex("item/iron_sword"),
            "ferraille": ic["ferraille"], "conserve": tex("item/bread")}


def menu_preview(data, mid, scale=3):
    lay = data["layouts"][mid]
    rows = lay["rows"]
    chest = vanilla_chest(data["vanilla54"], rows)
    W, H = 176, chest.height + T
    img = Image.new("RGBA", (W, H), (60, 66, 60, 255))
    img.alpha_composite(chest, (0, T))
    items = sample_items(data["jar"], data["icons"])
    ex = EXEMPLES.get(mid, {})
    for slot, name in ex.get("items", {}).items():
        x, y = 7 + 18 * (slot % 9) + 1, 17 + 18 * (slot // 9) + 1 + T
        img.alpha_composite(items[name], (x, y))
    # le titre (fond + calques + textes) est dessiné au-dessus des objets
    img.alpha_composite(data["bases"][mid].crop((0, 0, 176, H)), (0, 0))
    for ov in ex.get("ov", []):
        img.alpha_composite(data["overlays"][ov].crop((0, 0, 176, H)), (0, 0))
    for tid, (s, col) in ex.get("txt", {}).items():
        x, y, al, mx = lay["texts"][tid]
        w = text_width(s) - 1
        if w > mx:
            print(f"  (aperçu) texte {mid}:{tid} plus large que prévu : {w} > {mx}")
        x0 = x - w // 2 if al == "c" else (x - w if al == "d" else x)
        draw_text(img, x0, y, s, col, oy=T)
    d = ImageDraw.Draw(img)
    d.text((8, chest.height - 94 + T), "Inventaire", fill=(64, 64, 64, 255))
    return img.resize((W * scale, H * scale), Image.NEAREST)


def planche(data, path):
    ims = [(mid, menu_preview(data, mid)) for mid in data["layouts"]]
    cols = 4
    cw = max(im.width for _, im in ims) + 24
    ch = max(im.height for _, im in ims) + 40
    rows = (len(ims) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw, rows * ch + 30), (34, 38, 34, 255))
    d = ImageDraw.Draw(sheet)
    d.text((10, 8), "Dernier Refuge - menus (aperçu x3 : coffre vanilla + fond + calques + textes d'exemple)",
           fill=(230, 230, 230, 255))
    for n, (mid, im) in enumerate(ims):
        x, y = (n % cols) * cw + 12, (n // cols) * ch + 34
        d.text((x, y), mid, fill=(230, 230, 230, 255))
        sheet.alpha_composite(im, (x, y + 14))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)
