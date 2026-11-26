"""Menus du lobby EEMICRAFT au style des interfaces de Dernier Refuge (EemiCore, police `eemicraft:nav`).

Demande de Thibault (15/11/2026) : navigateur des mini-jeux, boutique, profil, groupe... au même style que les
menus du Refuge (EemiRefuge/pack/gen_ui.py, référence EemiRefuge/references/style_navigator.png) : panneau gris
clair façon Minecraft, onglet de titre, boutons ✕ et i, GROS boutons avec icône + nom (+ statut en direct).

Technique reprise telle quelle de gen_ui.py (voir son en-tête) :
  - titre de l'inventaire = police `eemicraft:nav` : espaces négatifs/positifs, glyphe du fond (256x256,
    ascent 13 + T), calques d'état au même endroit, puis textes dynamiques dans les polices `eemicraft:nt<y>` ;
  - cases cliquables = objet invisible `eemicraft:nav/vide` avec nom et description ;
  - cases d'objets réels (têtes, cosmétiques) transparentes dans le fond.
Icônes : celles du pack lobby (`eemicraft:textures/item/lobby/<nom>.png`, 64x64 dessinées en 32x32), réduites en
16x16 (bloc 2x2, contour sombre prioritaire).

Usage : python gen_nav.py <pack publié.zip> <sortie.zip>
Écrit aussi ../src/main/java/fr/eemicraft/core/ui/NavFonds.java (généré) et ../apercus/menus.png.
Le pack publié n'est PAS régénéré : on ajoute seulement les fichiers `eemicraft:nav*` (refus s'ils existent déjà
avec un autre contenu que celui d'une version précédente de ce script, c.-à-d. hors préfixes réservés).
"""
import hashlib
import io
import json
import os
import sys
import zipfile
from collections import Counter

from PIL import Image, ImageDraw, ImageFont

ICI = os.path.dirname(os.path.abspath(__file__))
RACINE = os.path.dirname(ICI)
NS = "eemicraft"
T = 14
ASCENT_BG = 13 + T
ADV_BG = 177
JAVA = os.path.join(RACINE, "src", "main", "java", "fr", "eemicraft", "core", "ui", "NavFonds.java")
JAR = os.path.join(os.environ.get("APPDATA", ""), ".minecraft", "versions", "26.3", "26.3.jar")
# fichiers de ce script dans le pack (remplacés à chaque génération)
RESERVES = (f"assets/{NS}/font/nav.json", f"assets/{NS}/font/nav/", f"assets/{NS}/textures/font/nav/", f"assets/{NS}/items/nav/",
            f"assets/{NS}/models/item/nav/", f"assets/{NS}/textures/item/nav/")


def reserve(name):
    if name.startswith(f"assets/{NS}/font/nt") and name.endswith(".json"):
        return True
    return any(name.startswith(r) for r in RESERVES)


# ------------------------------------------------------------------ couleurs (coffre vanilla 26.3)
# 15/11 : violet et or, comme les menus des mini-jeux (fonds du pack lobby, art_lobby.py)
PANEL = (34, 26, 74, 255)        # fond violet foncé
FRAME = (42, 47, 58, 255)        # cadre gris bleuté des fonds du lobby
OUTLINE = (18, 20, 26, 255)
WHITE = (255, 255, 255, 255)     # glyphes de la police (teintés par la couleur du texte)
HI = (120, 104, 200, 255)        # reflet des boutons
SHADOW = (16, 11, 40, 255)
BLACK = (0, 0, 0, 255)
TXT = (238, 240, 246, 255)       # libellés en blanc cassé
TITLE = (242, 182, 50, 255)      # titre de l'onglet en or
BTN = (66, 52, 138, 255)
BTN_EDGE = (14, 10, 34, 255)
BTN_SH = (40, 30, 92, 255)
FIELD = (22, 15, 56, 255)
TILE = (28, 20, 62, 255)
GRID = (22, 15, 56, 255)         # contour des cases (comme les fonds du lobby)
CELL = (43, 31, 94, 128)         # intérieur des cases, translucide
INV_IMG = None                   # bas du coffre (inventaire du joueur) repris de bg_profile du pack lobby
GOLD = (232, 176, 32, 255)
GOLD_D = (150, 104, 10, 255)
GREEN = (72, 190, 72, 255)
GREEN_D = (30, 110, 30, 255)
RED = (214, 64, 64, 255)
RED_D = (120, 24, 24, 255)
BLUE = (80, 140, 230, 255)
BLUE_D = (30, 60, 140, 255)
# couleur des catégories (même code que les icônes du pack lobby)
ACC = {"iles": (40, 190, 210, 255), "survie": (70, 180, 60, 255), "equipes": (60, 110, 220, 255),
       "combat": (210, 50, 50, 255), "tnt": (240, 130, 20, 255), "ambiance": (150, 80, 210, 255),
       "casino": (230, 180, 30, 255), "gris": (120, 120, 120, 255)}

# ------------------------------------------------------------------ petites capitales 5 px (gen_ui.py)
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
    "#": [".#.#.", "#####", ".#.#.", "#####", ".#.#."],
    "⛃": [".###.", "#...#", "#.#.#", "#...#", ".###."],      # pièce
    "★": ["..#..", "#####", ".###.", ".#.#.", "#...#"],
    "❤": [".#.#.", "#####", "#####", ".###.", "..#.."],
    "⚔": ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"],
}
ACCENTS = {"É": ("E", ["..#", ".#."]), "È": ("E", ["#..", ".#."]), "Ê": ("E", [".#.", "#.#"]),
           "À": ("A", ["#..", ".#."]), "Ô": ("O", [".#.", "#.#"]), "Â": ("A", [".#.", "#.#"]),
           "Î": ("I", ["#", "."]), "Û": ("U", [".#.", "#.#"]), "Ù": ("U", ["#..", ".#."])}
for ch, (base, acc) in ACCENTS.items():
    F5[ch] = base


def glyph_rows(ch):
    g = F5[ch]
    if isinstance(g, str):
        base, acc = ACCENTS[ch]
        rows = F5[base]
        w = len(rows[0])
        acc = [a.ljust(w, ".")[:w] for a in acc]
        return acc + rows + ["." * w]
    w = len(g[0])
    tail = "." * w
    if ch == ",":
        return ["."] * 6 + ["#", "#"]
    return [tail, tail] + g + [tail]


# 26/11/2026 (Esteban) : textes en police lisse Inter UI Medium (SIL OFL 1.1, police/OFL.txt), fournisseur « ttf »
# du client, rendue à OVS texels par pixel d'interface. Les petites capitales F5 ci-dessus ne servent plus qu'à
# définir le jeu de caractères. Les symboles absents d'Inter (pièce, étoile, cœur, épées) sont un bitmap HD.
POLICE = os.path.join(ICI, "police", "Inter-UI-Medium.ttf")
POLICE_LICENCE = os.path.join(ICI, "police", "OFL.txt")
TTF_SIZE = 6.75          # taille en pixels d'interface : majuscules d'environ 5 px, comme avant
OVS = 4                  # texels par pixel d'interface (GUI x4 = 1:1)
FONT = ImageFont.truetype(POLICE, round(TTF_SIZE * OVS))
# un libellé fixe trop large pour son bouton passe à la taille suivante (jamais au-delà du bouton)
TAILLES = [6.75, 6.25, 5.75, 5.25]
FONTS = [ImageFont.truetype(POLICE, round(t * OVS)) for t in TAILLES]
SYMBOLES = "⛃★❤⚔"
SYM_ADV = 6              # 20 texels d'encre -> round(5) + 1
EXTRA = "ÇËÏÜŒÆ"
CHARS = [c for c in F5 if c not in SYMBOLES] + [c for c in EXTRA if c not in F5]
MARGE = 2                # aucun libellé à moins de 2 px du bord de son bouton (Esteban : « jamais dépasser »)


def adv(ch, k=0):
    """Avance d'un caractère en pixels d'interface (float), comme le client (pas de crénage)."""
    if ch in SYMBOLES:
        return SYM_ADV
    return FONTS[k].getlength(ch) / OVS


def width_k(s, k=0):
    return sum(adv(c, k) for c in s)


def font_key(prefix, y, k):
    return f"{prefix}{y}" if k == 0 else f"{prefix}{y}_{k}"


def shift_y(y, k):
    """Décalage vertical : pied des majuscules en y + 5, les petites tailles centrées sur la même ligne."""
    cap = 5 * TAILLES[k] / TAILLES[0]
    return round(y - 8 - (5 - cap) / 2, 2)


def text_width(s):
    # +1 : les appelants retirent 1 (héritage de la police bitmap)
    return sum(adv(c) for c in s) + 1


def sym_cell(ch):
    """Symbole en 32x32 (une case = 8x8 pixels d'interface), encre de x 0 à 19, lignes 8 à 27."""
    k = 4  # sur-échantillonnage
    im = Image.new("L", (32 * k, 32 * k), 0)
    d = ImageDraw.Draw(im)
    x0, y0, x1, y1 = 0, 8 * k, 20 * k - 1, 28 * k - 1
    if ch == "⛃":
        d.ellipse([x0, y0, x1, y1], outline=255, width=3 * k)
        d.ellipse([x0 + 7 * k, y0 + 7 * k, x1 - 7 * k, y1 - 7 * k], fill=255)
    elif ch == "★":
        import math
        pts = []
        for i in range(10):
            r = (10 * k) if i % 2 == 0 else (4 * k)
            a = -math.pi / 2 + i * math.pi / 5
            pts.append((10 * k + r * math.cos(a), 18.5 * k + r * math.sin(a)))
        d.polygon(pts, fill=255)
    elif ch == "❤":
        d.ellipse([x0, y0, x0 + 10 * k, y0 + 10 * k], fill=255)
        d.ellipse([x0 + 10 * k - 1, y0, x1, y0 + 10 * k], fill=255)
        d.polygon([(x0 + k, y0 + 7 * k), (x1 - k, y0 + 7 * k), (10 * k, y1)], fill=255)
    else:  # épées croisées
        d.line([(x0 + k, y0 + k), (x1 - k, y1 - k)], fill=255, width=3 * k)
        d.line([(x1 - k, y0 + k), (x0 + k, y1 - k)], fill=255, width=3 * k)
    im = im.resize((32, 32), Image.LANCZOS)
    out = Image.new("RGBA", (32, 32), (255, 255, 255, 0))
    out.putalpha(im)
    return out


def draw_text(img, x, y, s, color, oy=0, k=0):
    """Dessine en HD sur une image à l'échelle OVS (aperçus) ; x, y en pixels d'interface (même ancre qu'avant :
    haut des majuscules en y, pied en y + 5)."""
    d = ImageDraw.Draw(img)
    for ch in s:
        if ch in SYMBOLES:
            cell = sym_cell(ch)
            tint = Image.new("RGBA", cell.size, tuple(color[:3]) + (255,))
            tint.putalpha(cell.getchannel("A"))
            img.alpha_composite(tint, (round(x * OVS), round((y - 2 + oy) * OVS)))
        elif ch != " ":
            base = y + 5 + oy - (5 - 5 * TAILLES[k] / TAILLES[0]) / 2
            d.text((x * OVS, base * OVS), ch, font=FONTS[k], fill=tuple(color), anchor="ls")
        x += adv(ch, k)


def norm(s):
    out = []
    for ch in s.upper():
        if ch == " " or ch in CHARS or ch in SYMBOLES:
            out.append(ch)
        else:
            raise SystemExit(f"ERREUR : caractère sans glyphe dans un libellé : {ch!r} ({s!r})")
    return "".join(out)


def text_fonts(prefix, yks, sym_file):
    """Polices de texte <prefix><y>[_k].json (une par hauteur et par taille) : Inter décalée pour que le pied des
    majuscules tombe en y + 5, puis les symboles HD (taille normale seulement). Partagé avec gen_droits.py."""
    files = {}
    for y, k in sorted(set(yks)):
        prov = [{"type": "ttf", "file": f"{NS}:nav/inter.ttf", "size": TAILLES[k], "oversample": OVS,
                 "shift": [0, shift_y(y, k)], "skip": SYMBOLES}]
        asc = 15 - y
        if k == 0 and asc <= 8:
            prov.append({"type": "bitmap", "file": sym_file, "ascent": asc, "height": 8, "chars": [SYMBOLES]})
        files[f"assets/{NS}/font/{font_key(prefix, y, k)}.json"] = json.dumps({"providers": prov}, indent=1,
                                                                              ensure_ascii=True).encode()
    return files


def symbols_png():
    img = Image.new("RGBA", (32 * len(SYMBOLES), 32), (0, 0, 0, 0))
    for i, ch in enumerate(SYMBOLES):
        img.alpha_composite(sym_cell(ch), (32 * i, 0))
    return img


def java_avances():
    """Lignes Java des avances, en quarts de pixel (UNITE = 4)."""
    return [f"        AVANCES.put('{jchar(ch)}', {round(adv(ch) * 4)});" for ch in CHARS + list(SYMBOLES)]


ESPACE_Q = round(adv(" ") * 4)


def fit_box(boxes, x, y, align):
    """Place disponible dans le bouton qui contient l'ancre (x, y), marge comprise ; None hors bouton."""
    for bx0, by0, bx1, by1 in boxes.values():
        if bx0 <= x <= bx1 and by0 <= y <= by1:
            if align == "c":
                return 2 * min(x - (bx0 + MARGE), (bx1 - MARGE) - x)
            if align == "d":
                return x - (bx0 + MARGE)
            return (bx1 - MARGE) - x
    return None


# ------------------------------------------------------------------ icônes
def reduce16(im):
    """64x64 (dessin 32x32 x2) -> 16x16 : bloc 2x2 du dessin 32, contour sombre prioritaire."""
    im = im.convert("RGBA").resize((32, 32), Image.NEAREST)
    o = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px = [im.getpixel((2 * x + i, 2 * y + j)) for i in (0, 1) for j in (0, 1)]
            op = [p for p in px if p[3] > 128]
            if len(op) < 2:
                continue
            dark = [p for p in op if sum(p[:3]) < 120]
            if len(dark) >= 2:
                o.putpixel((x, y), dark[0][:3] + (255,))
                continue
            c = Counter(p[:3] for p in op).most_common(1)[0][0]
            o.putpixel((x, y), c + (255,))
    return o


def load_icons(pack):
    ic = {}
    for n in pack.namelist():
        pre = f"assets/{NS}/textures/item/lobby/"
        if n.startswith(pre) and n.endswith(".png"):
            ic[n[len(pre):-4]] = reduce16(Image.open(io.BytesIO(pack.read(n))))
    return ic


# ------------------------------------------------------------------ données des menus
# Catégories du navigateur : (id, titre de l'onglet, libellé du bouton, icône, jeux).
# Jeu : (id, libellé, icône). L'action et le statut sont décidés côté Java (NavMenus.java) à partir de l'id.
CATEGORIES = [
    ("iles", "ÎLES ET AVENTURE", "ÎLES", "cat_iles", [
        ("skyblock", "SKYBLOCK", "skyblock"), ("abysse", "L'ABYSSE", "abysse"), ("oneblock", "ONEBLOCK", "oneblock"),
        ("jump", "JUMP", "jump")]),
    ("survie", "SURVIE", "SURVIE", "cat_survie", [
        ("uhc", "UHC RUN", "uhc"), ("speedrun", "SPEEDRUN", "speedrun"), ("bingo", "EEMINGO", "bingo")]),
    ("equipes", "ÉQUIPES", "ÉQUIPES", "cat_equipes", [
        ("bedwars", "BEDWARS", "bedwars"), ("thebridge", "THE BRIDGE", "thebridge"), ("towers", "THE TOWERS", "towers"),
        ("sheepwars", "SHEEP WARS", "sheepwars"), ("ballsofsteel", "B. OF STEEL", "ballsofsteel"),
        ("paintball", "PAINTBALL", "paintball")]),
    ("combat", "COMBAT", "COMBAT", "cat_combat", [
        ("skywars", "SKYWARS", "skywars"), ("hungergames", "HUNGER GAME", "hungergames"),
        ("duels", "DUELS · FFA", "pvp"), ("pitchout", "PITCHOUT", "pitchout"), ("jumpleague", "JUMPLEAGUE", "jumpleague")]),
    ("tnt", "JEUX TNT", "TNT", "cat_tnt", [
        ("tntrun", "TNT RUN", "tntrun"), ("tnttag", "TNT TAG", "tnttag"), ("bowspleef", "BOW SPLEEF", "bowspleef"),
        ("pvprun", "PVP RUN", "pvprun"), ("tntwars", "TNT WARS", "tntwars"), ("tntwizards", "TNT WIZARDS", "tntwizards")]),
    ("ambiance", "AMBIANCE", "AMBIANCE", "cat_ambiance", [
        ("murder", "MURDER", "murder"), ("loupgarou", "LOUP-GAROU", "loupgarou"), ("hideseek", "CACHE-CACHE", "hideseek"),
        ("blockparty", "BLOCK PARTY", "blockparty"), ("buildbattle", "BUILD BATTLE", "buildbattle"),
        ("boatrace", "MARIO BARQUE", "boatrace"),
        ("deacoudre", "DÉ À COUDRE", "deacoudre"), ("soleil", "1 2 3 SOLEIL", "soleil"), ("infection", "INFECTION", "infection"),
        ("elytra", "ÉLYTRES", "elytra"), ("dropper", "DROPPER", "dropper"), ("horscours", "HORS COURS", "horscours")]),
]
POS6 = [(0, 1), (3, 1), (6, 1), (0, 3), (3, 3), (6, 3)]
SHOP_CATS = [("trail", "TRAÎNÉES", "trainee"), ("hat", "CHAPEAUX", "chapeau"), ("title", "TITRES", "titre"),
             ("join", "ARRIVÉES", "arrivee"), ("gadget", "GADGETS", "gadget")]


def row_y(r):
    return 17 + 18 * r


def cx(c):
    return 7 + 18 * c + 9


def btn(i, c, r, w, h, icon, label=None, accent=None, status=False):
    return ("btn", i, c, r, w, h, icon, label, accent, status)


def tile(i, c, r, w, h, icon, label, click=True):
    return ("tile", i, c, r, w, h, icon, label, click)


HEAD = [btn("fermer", 0, 0, 1, 1, "x"), btn("info", 8, 0, 1, 1, "i"), ("text", "haut", 88, row_y(0) + 6, "c", 118)]
LIST_CELLS = [(c, r) for r in range(1, 5) for c in range(1, 8)]
PAGER = [btn("retour", 0, 5, 1, 1, "retour"), btn("prec", 3, 5, 1, 1, "precedent"), btn("suiv", 5, 5, 1, 1, "suivant"),
         ("text", "page", cx(4), row_y(5) + 7, "c", 18)]


def card_overlays(prefix_ids):
    """Calques des 6 gros boutons : partie en attente (vert), bêta (or), fermé (gris)."""
    out = []
    for b in prefix_ids:
        out += [("on", b), ("sel", b), ("off", b)]
    return out


def build_menu_defs():
    menus = []
    # accueil du navigateur
    el = list(HEAD)
    for (cid, _tab, label, icon, _g), (c, r) in zip(CATEGORIES, POS6):
        el.append(btn(cid, c, r, 3, 2, icon, label, ACC[cid], status=True))
    el += [btn("hasard", 0, 5, 3, 1, "hasard", "HASARD", ACC["gris"]),
           btn("casino", 3, 5, 3, 1, "cat_casino", "CASINO", ACC["casino"]),
           btn("groupe", 6, 5, 3, 1, "groupe", "GROUPE", ACC["equipes"])]
    menus.append(dict(id="jeux", titre="MINI-JEUX", rows=6, el=el,
                      overlays=card_overlays([c[0] for c in CATEGORIES]) + [("on", "casino")]))
    # pages des catégories (6 jeux par page)
    for cid, tab, _label, _icon, games in CATEGORIES:
        pages = [games[i:i + 6] for i in range(0, len(games), 6)]
        for n, page in enumerate(pages):
            mid = f"jeux_{cid}" + (f"_{n + 1}" if len(pages) > 1 else "")
            el = list(HEAD)
            ids = []
            for k, ((gid, label, icon), (c, r)) in enumerate(zip(page, POS6)):
                el.append(btn(f"g{k}", c, r, 3, 2, icon, label, ACC[cid], status=True))
                ids.append(f"g{k}")
            el.append(btn("retour", 0, 5, 1, 1, "retour"))
            if len(pages) > 1:
                if n > 0:
                    el.append(btn("prec", 3, 5, 1, 1, "precedent"))
                if n < len(pages) - 1:
                    el.append(btn("suiv", 5, 5, 1, 1, "suivant"))
                el.append(("text", "page", cx(4), row_y(5) + 7, "c", 18))
            menus.append(dict(id=mid, titre=tab, rows=6, el=el, overlays=card_overlays(ids),
                              games=[g[0] for g in page], cat=cid, pages=len(pages), page=n))
    # profil
    el = list(HEAD) + [
        tile("parties", 0, 1, 2, 2, "horloge", "PARTIES"), tile("victoires", 2, 1, 2, 2, "classement", "VICTOIRES"),
        ("cells", "tete", [(4, 1)]),
        tile("kills", 5, 1, 2, 2, "cat_combat", "KILLS"), tile("serie", 7, 1, 2, 2, "quete_ok", "SÉRIE"),
        ("text", "niveau", cx(4), row_y(2) + 8, "c", 32),
        ("text", "parties", cx(0) + 9, row_y(1) + 27, "c", 30), ("text", "victoires", cx(2) + 9, row_y(1) + 27, "c", 30),
        ("text", "kills", cx(5) + 9, row_y(1) + 27, "c", 30), ("text", "serie", cx(7) + 9, row_y(1) + 27, "c", 30),
        ("text", "xp", 88, row_y(3) + 2, "c", 150), ("bar", "xp", 12, row_y(3) + 9, 163, row_y(3) + 15),
        btn("boutique", 0, 4, 2, 2, "boutique", "BOUTIQUE"), btn("quetes", 2, 4, 2, 2, "quetes", "QUÊTES"),
        btn("joueurs", 5, 4, 2, 2, "joueurs", "JOUEURS"), btn("classement", 7, 4, 2, 2, "classement", "TOP 10"),
    ]
    menus.append(dict(id="profil", titre="PROFIL", rows=6, el=el,
                      overlays=[("bar", "xp", i) for i in range(21)]))
    # boutique (accueil)
    el = list(HEAD)
    for (cat, label, icon), (c, r) in zip(SHOP_CATS, POS6):
        el.append(btn(cat, c, r, 3, 2, icon, label, ACC["casino"], status=True))
    # 26/11 (Esteban) : la case « quetes » de l'accueil de la boutique ouvre la page Saison (EemiCore 1.9.4) ;
    # meme id « quetes » (NavFonds inchange), icone horloge (temps limite)
    el.append(btn("quetes", 6, 3, 3, 2, "horloge", "SAISON", ACC["ambiance"], status=True))
    el.append(btn("retour", 0, 5, 1, 1, "retour"))
    el.append(("text", "bas", 96, row_y(5) + 7, "c", 130))
    menus.append(dict(id="boutique", titre="BOUTIQUE", rows=6, el=el,
                      overlays=[("sel", c[0]) for c in SHOP_CATS] + [("sel", "quetes")]))
    # page d'une catégorie de la boutique
    el = list(HEAD) + [("cells", "articles", LIST_CELLS)] + PAGER + [("text", "solde", 167, row_y(5) + 7, "d", 48)]
    menus.append(dict(id="boutique_cat", titre="BOUTIQUE", rows=6, el=el,
                      overlays=[(k, "articles", i) for i in range(len(LIST_CELLS)) for k in ("ok", "chef", "non")]))
    # confirmation d'achat
    el = [btn("fermer", 0, 0, 1, 1, "x"), ("cells", "objet", [(4, 1)]),
          ("text", "nom", 88, row_y(2) + 4, "c", 160), ("text", "prix", 88, row_y(2) + 12, "c", 160),
          btn("annuler", 1, 3, 3, 2, "annuler", "ANNULER", ACC["combat"]),
          btn("acheter", 5, 3, 3, 2, "confirmer", "ACHETER", ACC["survie"])]
    menus.append(dict(id="confirmer", titre="CONFIRMER", rows=5, el=el, overlays=[]))
    # quêtes
    el = list(HEAD)
    for i in range(3):
        el.append(tile(f"q{i}", 1, 1 + i, 8, 1, None, None))
        el.append(("text", f"q{i}", 30, row_y(1 + i) + 6, "g", 96))
        el.append(("text", f"p{i}", 164, row_y(1 + i) + 6, "d", 34))
        el.append(("cells", f"ic{i}", [(0, 1 + i)]))
    el.append(btn("retour", 0, 4, 1, 1, "retour"))
    el.append(("text", "bas", 96, row_y(4) + 7, "c", 130))
    menus.append(dict(id="quetes", titre="QUÊTES DU JOUR", rows=5, el=el,
                      overlays=[("on", f"q{i}") for i in range(3)]))
    # groupe
    el = list(HEAD) + [
        ("cells", "membres", [(c, 1) for c in range(0, 8)]),
        ("text", "info", 88, row_y(2) + 6, "c", 160),
        btn("inviter", 0, 3, 2, 2, "inviter", "INVITER"), btn("regarder", 2, 3, 2, 2, "regarder", "PARTIES"),
        btn("accepter", 5, 3, 2, 2, "confirmer", "ENTRER"), btn("quitter", 7, 3, 2, 2, "quitter", "QUITTER"),
        ("icon", "inviter", 0, 5), ("cells", "envoyees", [(c, 5) for c in range(1, 8)]),
        btn("retour", 8, 5, 1, 1, "retour"),
    ]
    menus.append(dict(id="groupe", titre="GROUPE", rows=6, el=el,
                      overlays=[("chef", "membres", i) for i in range(8)]
                      + [("off", b) for b in ("inviter", "regarder", "accepter", "quitter")] + [("on", "accepter")]))
    # listes (même disposition, onglets différents)
    for lid, titre in (("liste_inviter", "INVITER"), ("liste_joueurs", "JOUEURS"), ("liste_regarder", "REGARDER"),
                       ("classement", "TOP 10")):
        el = list(HEAD) + [("cells", "liste", LIST_CELLS)] + PAGER
        menus.append(dict(id=lid, titre=titre, rows=6, el=el, overlays=[]))
    return menus


MENUS = build_menu_defs()


# ------------------------------------------------------------------ dessin
class Canvas:
    def __init__(self):
        self.img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.labels = []

    def rect(self, x0, y0, x1, y1, c):
        self.d.rectangle([x0, y0 + T, x1, y1 + T], fill=c)

    def px(self, x, y, c):
        self.img.putpixel((x, y + T), c)

    def clear(self, x0, y0, x1, y1):
        self.d.rectangle([x0, y0 + T, x1, y1 + T], fill=(0, 0, 0, 0))

    def paste(self, im, x, y):
        self.img.alpha_composite(im, (x, y + T))

    def text(self, x, y, s, c, align="g"):
        # 26/11 : plus dessiné dans le fond, écrit par NavUi en police lisse (libellés fixes de NavFonds)
        self.labels.append((s, x, y, align, c))


def raised(cv, x0, y0, x1, y1, fill=BTN, accent=None):
    cv.rect(x0, y0, x1, y1, BTN_EDGE)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
    cv.rect(x0 + 1, y0 + 1, x1 - 2, y0 + 1, HI)
    cv.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 2, HI)
    cv.rect(x0 + 2, y1 - 1, x1 - 1, y1 - 1, BTN_SH)
    cv.rect(x1 - 1, y0 + 2, x1 - 1, y1 - 1, BTN_SH)
    if accent:
        dark = tuple(max(0, int(v * 0.65)) for v in accent[:3]) + (255,)
        cv.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 1, accent)
        cv.rect(x0 + 1, y0 + 2, x1 - 1, y0 + 2, dark)


def sunken(cv, x0, y0, x1, y1, fill=FIELD):
    cv.rect(x0, y0, x1, y1, SHADOW)
    cv.rect(x0 + 1, y0 + 1, x1, y1, HI)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)


def panel(cv, v54, rows, title):
    h = 17 + 18 * rows
    cv.rect(0, 0, 175, h - 1, OUTLINE)
    cv.rect(1, 1, 174, h - 1, FRAME)
    cv.rect(4, 4, 171, h - 1, PANEL)
    if INV_IMG is not None:  # inventaire du joueur : même dessin que les menus des mini-jeux
        cv.img.alpha_composite(INV_IMG, (0, T + h))
    tw = text_width(title) - 1
    w = max(64, tw + 24)
    x0 = 88 - w // 2
    x1 = x0 + w - 1
    y0, y1 = -T + 1, 12
    cv.rect(x0, y0, x1, y1, OUTLINE)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, FRAME)
    cv.rect(x0 + 1, y0 + 1, x1 - 2, y0 + 1, (74, 82, 99, 255))
    cv.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 2, (74, 82, 99, 255))
    cv.rect(x0 + 5, y0 + 4, x1 - 5, y1 - 4, GOLD_D)
    cv.rect(x0 + 6, y0 + 5, x1 - 6, y1 - 5, FIELD)
    cv.text(88, (y0 + y1) // 2 - 2, title, TITLE, "c")
    assert cv.img.getpixel((175, T + 40))[3] == 255
    return h


def small_symbol(cv, x, y, kind):
    if kind == "x":
        for k in range(7):
            for dx in (0, 1):
                cv.px(x + 4 + k + dx, y + 4 + k, (234, 68, 90, 255))
                cv.px(x + 10 - k + dx, y + 4 + k, (234, 68, 90, 255))
    else:
        col = (110, 170, 250, 255)
        cv.rect(x + 7, y + 3, x + 8, y + 4, col)
        cv.rect(x + 7, y + 6, x + 8, y + 12, col)
        cv.rect(x + 6, y + 6, x + 6, y + 6, col)
        cv.rect(x + 6, y + 12, x + 9, y + 12, col)


def button_box(c, r, w, h):
    x0, y0 = 7 + 18 * c, 17 + 18 * r
    return x0 + 1, y0 + 1, x0 + 18 * w - 2, y0 + 18 * h - 2


def cell_box(c, r):
    x0, y0 = 7 + 18 * c, 17 + 18 * r
    return x0, y0, x0 + 17, y0 + 17


def draw_btn(cv, el, ic, texts):
    _, bid, c, r, w, h, icon, label, accent, status = el
    x0, y0, x1, y1 = button_box(c, r, w, h)
    raised(cv, x0, y0, x1, y1, accent=accent)
    mid = (x0 + x1 + 1) // 2
    if w == 1 and h == 1:
        if icon in ("x", "i"):
            small_symbol(cv, x0, y0, icon)
        else:
            cv.paste(ic[icon], x0, y0)
        return
    if h == 1:  # bouton large d'une ligne : icône à gauche, libellé à côté
        cv.paste(ic[icon], x0 + 2, y0)
        lw = text_width(label) - 1
        cv.text(x0 + 21, y0 + 6, label, TXT)
        return
    lw = text_width(label) - 1
    if status:  # icône, nom, puis ligne de statut dynamique
        cv.paste(ic[icon], mid - 8, y0 + 3)
        cv.text(mid, y0 + 20, label, TXT, "c")
        texts["s_" + bid] = (mid, y0 + 27, "c", (x1 - x0) - 4)
        return
    total = 16 + 3 + 5
    top = y0 + (y1 - y0 + 1 - total) // 2
    cv.paste(ic[icon], mid - 8, top)
    cv.text(mid, top + 19, label, TXT, "c")


def draw_tile(cv, el, ic):
    _, tid, c, r, w, h, icon, label, _click = el
    x0, y0, x1, y1 = button_box(c, r, w, h)
    sunken(cv, x0, y0, x1, y1, TILE)
    if label:
        mid = (x0 + x1 + 1) // 2
        cv.text(mid, y0 + 4, label, TXT, "c")
        if icon:
            cv.paste(ic[icon], mid - 8, y0 + 9)


def frame(cv, x0, y0, x1, y1, c, dark, width=2):
    for k in range(width):
        col = c if k == 0 else dark
        cv.rect(x0 + k, y0 + k, x1 - k, y0 + k, col)
        cv.rect(x0 + k, y1 - k, x1 - k, y1 - k, col)
        cv.rect(x0 + k, y0 + k, x0 + k, y1 - k, col)
        cv.rect(x1 - k, y0 + k, x1 - k, y1 - k, col)


TROP = []


def build_menus(v54, ic):
    bases, overlays, layouts = {}, {}, {}
    for m in MENUS:
        cv = Canvas()
        panel(cv, v54, m["rows"], norm(m["titre"]))
        lay = {"rows": m["rows"], "buttons": {}, "cells": {}, "texts": {}}
        boxes, bars = {}, {}
        for el in m["el"]:
            kind = el[0]
            if kind == "btn":
                draw_btn(cv, el, ic, lay["texts"])
                _, bid, c, r, w, h = el[:6]
                boxes[bid] = button_box(c, r, w, h)
                lay["buttons"][bid] = [(r + j) * 9 + c + i for j in range(h) for i in range(w)]
            elif kind == "tile":
                draw_tile(cv, el, ic)
                _, tid, c, r, w, h = el[:6]
                boxes[tid] = button_box(c, r, w, h)
                if el[8]:
                    lay["buttons"][tid] = [(r + j) * 9 + c + i for j in range(h) for i in range(w)]
            elif kind == "cells":
                lay["cells"][el[1]] = [r * 9 + c for c, r in el[2]]
                for c, r in el[2]:
                    x0, y0, x1, y1 = cell_box(c, r)
                    cv.rect(x0, y0, x1, y1, GRID)
                    cv.d.rectangle([x0 + 1, y0 + 1 + T, x1 - 1, y1 - 1 + T], fill=CELL)
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
            elif kind == "bar":
                _, bid, x0, y0, x1, y1 = el
                sunken(cv, x0, y0, x1, y1, (20, 14, 48, 255))
                bars[bid] = (x0, y0, x1, y1)
        # les cases d'une liste ou d'un bouton cliquable : jamais deux fois la même
        used = {}
        for k, sl in list(lay["buttons"].items()) + list(lay["cells"].items()):
            for s in sl:
                if s in used:
                    raise SystemExit(f"ERREUR : case {s} du menu {m['id']} utilisée par {used[s]} et {k}")
                if s >= 9 * m["rows"]:
                    raise SystemExit(f"ERREUR : case {s} hors du menu {m['id']}")
                used[s] = k
        bases[m["id"]] = cv.img
        labels = []
        for (lab, x, y, al, col) in cv.labels:
            room = fit_box(boxes, x, y + 2, al)
            k = 0
            while room is not None and k < len(TAILLES) - 1 and width_k(lab, k) > room:
                k += 1
            if room is not None and width_k(lab, k) > room:
                TROP.append(f"{m['id']}: « {lab} » {width_k(lab, k):.1f} px > {room} px")
            labels.append((lab, x, y, al, col, k))
        lay["labels"] = labels
        for tid, (x, y, al, mx) in list(lay["texts"].items()):
            room = fit_box(boxes, x, y + 2, al)
            if room is not None and room < mx:
                lay["texts"][tid] = (x, y, al, int(room))
        for ov in m["overlays"]:
            ocv = Canvas()
            ocv.px(175, 40, BLACK)
            kind = ov[0]
            if kind in ("sel", "on", "off"):
                x0, y0, x1, y1 = boxes[ov[1]]
                if kind == "sel":
                    frame(ocv, x0 - 1, y0 - 1, x1 + 1, y1 + 1, GOLD, GOLD_D)
                elif kind == "on":
                    frame(ocv, x0 - 1, y0 - 1, x1 + 1, y1 + 1, GREEN, GREEN_D)
                else:
                    ocv.d.rectangle([x0, y0 + T, x1, y1 + T], fill=(8, 6, 18, 160))
                name = f"{m['id']}:{kind}:{ov[1]}"
            elif kind in ("ok", "chef", "non"):
                cells = [(cc, rr) for e in m["el"] if e[0] == "cells" and e[1] == ov[1] for cc, rr in e[2]]
                c, r = cells[ov[2]]
                x0, y0, x1, y1 = cell_box(c, r)
                col = {"ok": (GREEN, GREEN_D), "chef": (GOLD, GOLD_D), "non": (RED, RED_D)}[kind]
                frame(ocv, x0 - 1, y0 - 1, x1 + 1, y1 + 1, *col)
                if kind == "chef" and "couronne" in ic:
                    pass
                name = f"{m['id']}:{kind}:{ov[1]}:{ov[2]}"
            elif kind == "bar":
                x0, y0, x1, y1 = bars[ov[1]]
                n = ov[2]
                inner = (x1 - 1) - (x0 + 1)
                fill = (inner * n) // 20
                if fill > 0:
                    ocv.rect(x0 + 1, y0 + 1, x0 + fill, y1 - 1, (90, 200, 90, 255))
                    ocv.rect(x0 + 1, y0 + 1, x0 + fill, y0 + 1, (160, 240, 150, 255))
                    ocv.rect(x0 + 1, y1 - 1, x0 + fill, y1 - 1, (40, 120, 40, 255))
                name = f"{m['id']}:bar:{ov[1]}:{n}"
            else:
                raise SystemExit(f"calque inconnu {ov}")
            overlays[name] = ocv.img
        layouts[m["id"]] = lay
    if TROP:
        raise SystemExit("ERREUR : libellés qui dépassent leur bouton (marge 2 px) :\n  " + "\n  ".join(TROP))
    return bases, overlays, layouts


# ------------------------------------------------------------------ polices
TEXT_CHARS = [c for c in F5 if c != ","] + [","]
NEG = [(0xF800 + i, -(1 << i)) for i in range(9)]
POS = [(0xF810 + i, (1 << i)) for i in range(9)]


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
        lines.append(s + "\u0000" * (cols - len(s)))
    return img, lines


def png_bytes(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()


def build_files(v54, ic):
    bases, overlays, layouts = build_menus(v54, ic)
    files = {}
    providers = [{"type": "space", "advances": {chr(c): v for c, v in NEG + POS}}]
    glyphs = {}
    if len(bases) > 0x400 or len(overlays) > 0x2000:
        raise SystemExit("ERREUR : trop de glyphes")
    for i, (mid, img) in enumerate(bases.items()):
        ch = chr(0xE000 + i)
        glyphs["bg:" + mid] = ch
        files[f"assets/{NS}/textures/font/nav/bg_{mid}.png"] = png_bytes(img)
        providers.append({"type": "bitmap", "file": f"{NS}:font/nav/bg_{mid}.png", "ascent": ASCENT_BG,
                          "height": 256, "chars": [ch]})
    for i, (oid, img) in enumerate(overlays.items()):
        ch = chr(0xE400 + i)
        glyphs["ov:" + oid] = ch
        fname = oid.replace(":", "_")
        files[f"assets/{NS}/textures/font/nav/ov_{fname}.png"] = png_bytes(img)
        providers.append({"type": "bitmap", "file": f"{NS}:font/nav/ov_{fname}.png", "ascent": ASCENT_BG,
                          "height": 256, "chars": [ch]})
    files[f"assets/{NS}/font/nav.json"] = json.dumps({"providers": providers}, indent=1, ensure_ascii=True).encode()
    with open(POLICE, "rb") as f:
        files[f"assets/{NS}/font/nav/inter.ttf"] = f.read()
    with open(POLICE_LICENCE, "rb") as f:
        files[f"assets/{NS}/font/nav/OFL.txt"] = f.read()
    files[f"assets/{NS}/textures/font/nav/symboles.png"] = png_bytes(symbols_png())
    yks = {(t[1], k) for lay in layouts.values() for t in lay["texts"].values() for k in range(len(TAILLES))} \
        | {(lb[2], lb[5]) for lay in layouts.values() for lb in lay["labels"]}
    ys = sorted({y for y, _ in yks})
    files.update(text_fonts("nt", yks, f"{NS}:font/nav/symboles.png"))
    # objet invisible des cases cliquables
    files[f"assets/{NS}/textures/item/nav/vide.png"] = png_bytes(Image.new("RGBA", (16, 16), (0, 0, 0, 0)))
    files[f"assets/{NS}/models/item/nav/vide.json"] = json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/nav/vide"}}, indent=1).encode()
    files[f"assets/{NS}/items/nav/vide.json"] = json.dumps(
        {"model": {"type": "minecraft:model", "model": f"{NS}:item/nav/vide"}}, indent=1).encode()
    return files, dict(bases=bases, overlays=overlays, layouts=layouts, glyphs=glyphs, ys=ys)


# ------------------------------------------------------------------ NavFonds.java
def jchar(c):
    if c in "'\"\\":
        return "\\" + c
    return c if 32 <= ord(c) < 127 else "\\u%04x" % ord(c)


def jstr(s):
    return "\"" + "".join(jchar(c) for c in s) + "\""


def write_java(data):
    L = ["package fr.eemicraft.core.ui;", "", "import java.util.HashMap;", "import java.util.List;",
         "import java.util.Map;", "",
         "/** GÉNÉRÉ par pack/gen_nav.py (ne pas modifier à la main) : glyphes et dispositions des menus du lobby. */",
         "public final class NavFonds {",
         f"    public static final int AVANCE_FOND = {ADV_BG};",
         "    public static final char[] NEG = {" + ", ".join("'\\u%04x'" % c for c, _ in NEG) + "};",
         "    public static final char[] POS = {" + ", ".join("'\\u%04x'" % c for c, _ in POS) + "};",
         "    /** Avances en quarts de pixel (police lisse, 26/11). */",
         "    public static final int UNITE = 4;",
         "    /** Tailles de la police (la 1re = AVANCES) : un texte trop large passe à la suivante (police nt<y>_<k>). */",
         "    public static final float[] TAILLES = {" + ", ".join(f"{t}f" for t in TAILLES) + "};",
         f"    public static final int ESPACE = {ESPACE_Q};",
         "    public static final Map<String, Character> GLYPHES = new HashMap<>();",
         "    public static final Map<Character, Integer> AVANCES = new HashMap<>();",
         "    public record Texte(int x, int y, char align, int max) { }",
         "    /** Libellé fixe d'un menu (écrit par le client en police lisse, plus dans le fond). */",
         "    public record Libelle(String texte, int x, int y, char align, int couleur, String police, int largeur) { }",
         "    public record Disposition(int lignes, Map<String, int[]> boutons, Map<String, int[]> cases,",
         "                              Map<String, Texte> textes, List<Libelle> libelles) { }",
         "    public static final Map<String, Disposition> MENUS = new HashMap<>();",
         "    /** Catégorie -> pages ; page -> ids des jeux dans l'ordre des boutons g0..g5. */",
         "    public static final Map<String, List<String>> PAGES = new HashMap<>();",
         "    public static final Map<String, List<String>> JEUX = new HashMap<>();",
         "    public static final List<String> CATEGORIES = List.of(" + ", ".join(jstr(c[0]) for c in CATEGORIES) + ");",
         "    public static final Map<String, String> NOMS = new HashMap<>();",
         "", "    static {"]
    for k, ch in data["glyphs"].items():
        L.append(f"        GLYPHES.put({jstr(k)}, '{jchar(ch)}');")
    L += java_avances()
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
        libs = ", ".join(f"new Libelle({jstr(lb[0])}, {lb[1]}, {lb[2]}, '{lb[3]}', 0x{lb[4][0]:02X}{lb[4][1]:02X}{lb[4][2]:02X}, "
                         f"{jstr(font_key('nt', lb[2], lb[5]))}, {round(width_k(lb[0], lb[5]) * 4)})"
                         for lb in lay["labels"])
        L.append(f"            MENUS.put({jstr(mid)}, new Disposition({lay['rows']}, b, c, t, List.of({libs})));")
        L.append("        }")
    pages = {}
    for m in MENUS:
        if "games" in m:
            pages.setdefault(m["cat"], []).append(m["id"])
            L.append(f"        JEUX.put({jstr(m['id'])}, List.of({', '.join(jstr(g) for g in m['games'])}));")
    for cat, ids in pages.items():
        L.append(f"        PAGES.put({jstr(cat)}, List.of({', '.join(jstr(i) for i in ids)}));")
    for cid, tab, _l, _i, games in CATEGORIES:
        L.append(f"        NOMS.put({jstr(cid)}, {jstr(tab)});")
    L += ["    }", "", "    private NavFonds() { }", "}"]
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L) + "\n")


# ------------------------------------------------------------------ aperçus
EX = {
    "jeux": (["jeux:on:equipes", "jeux:sel:ambiance"], {"haut": "12 EN LIGNE · 5 EN JEU", "s_iles": "3 EN JEU",
             "s_survie": "PERSONNE", "s_equipes": "4 EN ATTENTE", "s_combat": "2 EN JEU", "s_tnt": "PERSONNE",
             "s_ambiance": "6 EN JEU"}),
    "jeux_equipes": (["jeux_equipes:on:g0", "jeux_equipes:off:g4"], {"haut": "4 EN ATTENTE · 2 EN JEU",
                     "s_g0": "3/8 EN ATTENTE", "s_g1": "PERSONNE", "s_g2": "EN PARTIE (4)", "s_g3": "PERSONNE",
                     "s_g4": "MAINTENANCE", "s_g5": "PERSONNE"}),
    "profil": (["profil:bar:xp:8"], {"haut": "ENOSIK · NIVEAU 12 · 2 450 ⛃", "parties": "184", "victoires": "37",
               "kills": "212", "serie": "5 J", "niveau": "NIV 12", "xp": "XP 120 / 300"}),
    "boutique": (["boutique:sel:title"], {"haut": "2 450 ⛃", "s_trail": "3 / 10", "s_hat": "1 / 7", "s_title": "4 / 24",
                 "s_join": "0 / 5", "s_gadget": "0 / 4", "s_quetes": "2 / 10", "bas": "GAGNE DES PIÈCES EN JOUANT"}),
    "boutique_cat": (["boutique_cat:chef:articles:0", "boutique_cat:ok:articles:1", "boutique_cat:non:articles:5"],
                     {"haut": "TITRES", "page": "1/2", "solde": "2 450 ⛃"}),
    "confirmer": ([], {"nom": "TITRE LÉGENDE", "prix": "2 000 ⛃ · IL TE RESTERA 450 ⛃"}),
    "quetes": (["quetes:on:q0"], {"haut": "NOUVELLES DANS 4H12", "q0": "GAGNE 1 PARTIE", "p0": "1/1",
               "q1": "JOUE 6 PARTIES", "p1": "2/6", "q2": "FAIS 15 KILLS", "p2": "4/15", "bas": "50 ⛃ ET 100 XP"}),
    "groupe": (["groupe:chef:membres:0", "groupe:off:accepter"], {"haut": "GROUPE DE ENOSIK · 3/8",
               "info": "CLIQUE UN MEMBRE EN JEU POUR LE REGARDER"}),
    "liste_joueurs": ([], {"haut": "12 JOUEURS EN LIGNE", "page": "1/1"}),
}


def preview(data, v54, mid):
    """Aperçu à l'échelle OVS (GUI x4) : ce que le client affiche, textes en police lisse."""
    lay = data["layouts"][mid]
    rows = lay["rows"]
    h = 114 + 18 * rows
    chest = Image.new("RGBA", (176, h), (0, 0, 0, 0))
    chest.alpha_composite(v54.crop((0, 0, 176, 17 + 18 * rows)), (0, 0))
    chest.alpha_composite(v54.crop((0, 126, 176, 222)), (0, 17 + 18 * rows))
    H = h + T
    img = Image.new("RGBA", (176, H), (60, 66, 60, 255))
    img.alpha_composite(chest, (0, T))
    img.alpha_composite(data["bases"][mid].crop((0, 0, 176, H)), (0, 0))
    big = img.resize((176 * OVS, H * OVS), Image.NEAREST)

    def put(s, x, y, al, col, k=0):
        w = width_k(s, k)
        x0 = x - w / 2 if al == "c" else (x - w if al == "d" else x)
        draw_text(big, x0, y, s, col, oy=T, k=k)

    for (s, x, y, al, col, k) in lay["labels"]:
        put(s, x, y, al, col, k)
    ovs, txt = EX.get(mid, ([], {}))
    for ov in ovs:
        big.alpha_composite(data["overlays"][ov].crop((0, 0, 176, H)).resize((176 * OVS, H * OVS), Image.NEAREST))
    for tid, s in txt.items():
        x, y, al, mx = lay["texts"][tid]
        s = norm(s)
        k = 0
        while k < len(TAILLES) - 1 and width_k(s, k) * 1.03 > mx:
            k += 1
        while len(s) > 1 and width_k(s, k) * 1.03 > mx:
            s = s[:-2].rstrip() + "."
            print(f"  (apercu) texte {mid}:{tid} coupe : {s}")
        put(s, x, y, al, TXT, k)
    return big


def planche(data, v54, path):
    ims = []
    for mid in data["layouts"]:
        im = preview(data, v54, mid)
        ims.append((mid, im.resize((176 * 3, im.height * 3 // OVS), Image.LANCZOS)))
    cols = 5
    cw = max(im.width for _, im in ims) + 24
    ch = max(im.height for _, im in ims) + 40
    rows = (len(ims) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw, rows * ch + 30), (34, 38, 34, 255))
    d = ImageDraw.Draw(sheet)
    d.text((10, 8), "EEMICRAFT - menus du lobby, style Refuge (aperçu x3, textes d'exemple)", fill=(230, 230, 230, 255))
    for n, (mid, im) in enumerate(ims):
        x, y = (n % cols) * cw + 12, (n // cols) * ch + 34
        d.text((x, y), mid, fill=(230, 230, 230, 255))
        sheet.alpha_composite(im, (x, y + 14))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)


# ------------------------------------------------------------------ main
def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    src, dst = sys.argv[1], sys.argv[2]
    jar = zipfile.ZipFile(JAR)
    v54 = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/gui/container/generic_54.png"))).convert("RGBA")
    pack = zipfile.ZipFile(src)
    global INV_IMG
    lob = Image.open(io.BytesIO(pack.read(f"assets/{NS}/textures/font/lobby/bg_profile.png"))).convert("RGBA")
    INV_IMG = lob.crop((0, 17 + 18 * 6, 176, 222))
    ic = load_icons(pack)
    files, data = build_files(v54, ic)
    for n in files:
        if n in pack.namelist() and not reserve(n):
            raise SystemExit(f"ERREUR : {n} existe déjà dans le pack d'origine")
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for info in pack.infolist():
            if reserve(info.filename):
                continue
            zi = zipfile.ZipInfo(info.filename, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, pack.read(info.filename))
        for n in sorted(files):
            zi = zipfile.ZipInfo(n, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, files[n])
    with open(dst, "wb") as f:
        f.write(out.getvalue())
    write_java(data)
    planche(data, v54, os.path.join(RACINE, "apercus", "menus.png"))
    print(f"{len(data['bases'])} fonds, {len(data['overlays'])} calques, polices de texte : {data['ys']}")
    print(f"Pack écrit : {dst}  SHA-1 {hashlib.sha1(out.getvalue()).hexdigest()}")


if __name__ == "__main__":
    main()
