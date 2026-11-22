"""DA des menus du lobby (10/11/2026) : fonds de menus (police eemicraft:lobby) et icônes eemicraft:lobby/<nom>.

Importé par build_pack.py, qui écrit ces fichiers PAR-DESSUS ceux de la base (mêmes chemins, mêmes glyphes) :
  - assets/eemicraft/textures/font/lobby/bg_<menu>.png  (256x256, contenu 176 px de large en haut à gauche,
    pixel (x, y) = pixel (x, y) du coffre vanilla : cases en (7 + 18*col, 17 + 18*ligne), 18x18) ;
  - assets/eemicraft/textures/item/lobby/<icone>.png    (64x64 = dessin 32x32 agrandi x2, pixel art net).

Principe des fonds : panneau clair uni (léger dégradé), bandeau de titre de la couleur du menu, cases utilisées
cerclées de la couleur du menu avec l'intérieur presque transparent (la case vanilla et l'objet restent visibles
même si le titre est dessiné au-dessus des objets), boutons de navigation cerclés de gris, inventaire du joueur
= cases vanilla (transparentes). La colonne x = 175 doit rester opaque : la largeur du glyphe (176) fixe le
décalage du texte du titre (U+E301 = -169).

Principe des icônes (refait le 11/11/2026, référence references/style_icones.png) : pictogramme seul, SANS
tuile ni fond (transparent), épais contour presque noir (2 px autour de la forme, 1 px entre les calques),
couleurs plates avec relief automatique par calque (bord haut/gauche éclairci, bas/droite assombri, 1 px de
reflet). Interface en couleurs (11/11/2026, plus de gris) : flèches bleu clair, info/œil bleus, personnages
bleu/vert/orange, porte brune + flèche rouge, horloge or, podium or/argent/bronze, étiquette jaune, haut-de-forme
noir à ruban rouge, traînée arc-en-ciel, haut-parleur orange, baguette violette, parchemins beiges, sac vert,
panneau « aucun » rouge, cintre bleu ; états : coche verte (confirmer, etat_equipe, quete_ok), croix rouge (fermer,
annuler), cadenas doré à anse en fer (etat_cher), pièces dorées (pieces, etat_prix), ? doré (hasard). Jeux et catégories :
accent de la catégorie dans le pictogramme (Equipes bleu, Combat rouge, TNT orange, Ambiance violet, Survie
vert, Iles cyan, Casino or).
Nécessite Pillow.
"""
import io
import math

from PIL import Image, ImageChops, ImageDraw, ImageFilter

# =========================================================================================================
# couleurs


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


WHITE, BLACK = (255, 255, 255), (0, 0, 0)

CAT_COLORS = {
    "equipes": (66, 118, 214),
    "combat": (204, 66, 66),
    "tnt": (228, 124, 36),
    "ambiance": (170, 78, 186),
    "survie": (60, 162, 82),
    "iles": (30, 156, 176),
    "casino": (212, 160, 34),
    "neutre": (102, 112, 132),
}

# couleur d'accent des fonds (bandeau, cases) : même logique que les fonds d'origine
BG_ACCENTS = {
    "or": (186, 134, 26),
    "vert": (46, 142, 68),
    "bleu": (56, 100, 190),
    "rouge": (186, 56, 56),
    "orange": (206, 106, 26),
    "violet": (158, 66, 164),
    "cyan": (26, 136, 156),
}

# =========================================================================================================
# Style des icônes (11/11/2026, référence references/style_icones.png) : pictogramme seul sur fond transparent,
# épais contour presque noir (1 px entre les calques, 2 px autour de la forme), couleurs plates avec un relief
# simple calculé automatiquement par calque : bord haut/gauche éclairci, bord bas/droite assombri, 1 px de reflet.
# Interface : vraies couleurs (voir NAV_C...) ; états (coche verte, croix rouge, pièces dorées, ? doré) ; jeux : accent de leur catégorie.

OUT = (22, 22, 30)        # contour
INK = (40, 42, 54)        # détails sombres (jamais ombrés)
G = (164, 170, 186)       # gris interface
G2 = (122, 128, 146)      # gris moyen (plans arrière)
G3 = (204, 208, 220)      # gris clair
WH = (238, 240, 246)      # blanc cassé
WOOD = (186, 124, 66)
WOOD_D = (140, 90, 50)
GRASS = (100, 198, 84)
LEAF = (126, 214, 92)
DIRT = (158, 104, 62)
STONE = (172, 178, 192)
GOLD = (250, 194, 46)
GOLD_D = (196, 132, 26)
OKG = (72, 206, 98)       # coche verte
NOR = (234, 68, 90)       # croix rouge
PINK = (246, 122, 192)
CYAN = (104, 220, 236)
GREEN = (118, 214, 92)

A_EQ = (70, 134, 238)     # Equipes
A_CO = (228, 64, 76)      # Combat
A_TNT = (246, 130, 34)    # TNT
A_AMB = (166, 88, 218)    # Ambiance
A_SUR = (74, 190, 94)     # Survie
A_ILE = (38, 186, 206)    # Iles
A_CAS = GOLD              # Casino
A_AMB_L = (204, 170, 244)
A_EQ_L = (136, 180, 250)


def C(c, a=255):
    return tuple(c) + (a,)


def _lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


# =========================================================================================================
# pinceau 32x32 : chaque calque reçoit son relief et son contour, ce qui sépare les formes superposées


class Pen:
    def __init__(self):
        self.layers = []
        self.new()

    def new(self):
        self.img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.layers.append(self.img)
        return self

    def rect(self, x0, y0, x1, y1, c):
        self.d.rectangle([x0, y0, x1, y1], fill=C(c))

    def rrect(self, x0, y0, x1, y1, c, r=2):
        self.d.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=C(c))

    def poly(self, pts, c):
        self.d.polygon(pts, fill=C(c))

    def ell(self, x0, y0, x1, y1, c):
        self.d.ellipse([x0, y0, x1, y1], fill=C(c))

    def ring(self, x0, y0, x1, y1, c, w):
        self.d.ellipse([x0, y0, x1, y1], outline=C(c), width=w)

    def line(self, pts, c, w=1):
        self.d.line(pts, fill=C(c), width=w)

    def arc(self, box, a0, a1, c, w):
        self.d.arc(box, a0, a1, fill=C(c), width=w)

    def px(self, x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            self.img.putpixel((x, y), C(c))

    def clear(self, x0, y0, x1, y1):
        self.d.rectangle([x0, y0, x1, y1], fill=(0, 0, 0, 0))

    def clear_poly(self, pts):
        self.d.polygon(pts, fill=(0, 0, 0, 0))

    def bitmap(self, x, y, rows, pal, s=1):
        for j, r in enumerate(rows):
            for i, ch in enumerate(r):
                if ch != ".":
                    self.rect(x + i * s, y + j * s, x + i * s + s - 1, y + j * s + s - 1, pal[ch])

    def mirror(self):
        """Retourne le calque courant gauche/droite."""
        flipped = self.img.transpose(Image.FLIP_LEFT_RIGHT)
        self.img.paste(flipped)


# ---------------------------------------------------------------------------------------------------------
# formes réutilisées


def person(p, cx, top, hr, bw, bottom, c):
    """Silhouette (tête ronde + buste arrondi coupé en bas), sur un calque à part."""
    p.new()
    p.ell(cx - hr, top, cx + hr, top + 2 * hr, c)
    by = top + 2 * hr + 2
    p.d.rounded_rectangle([cx - bw, by, cx + bw, bottom + bw], radius=bw - 1, fill=C(c))
    p.clear(0, bottom + 1, 31, 31)


def star(p, cx, cy, R, r, c, n=5, rot=-90):
    pts = []
    for k in range(2 * n):
        a = math.radians(rot + k * 180 / n)
        rad = R if k % 2 == 0 else r
        pts.append((cx + rad * math.cos(a), cy + rad * math.sin(a)))
    p.poly(pts, c)


def sword(p, x0, y0, x1, y1, blade=G3, guard=A_CO, grip=WOOD_D, gw=4):
    """Epée du pommeau (x0, y0) à la pointe (x1, y1), sur le calque courant."""
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    nx, ny = -uy, ux
    gx, gy = x0 + ux * 6, y0 + uy * 6
    p.line([(gx + ux, gy + uy), (x1, y1)], blade, 4)
    p.line([(x0, y0), (gx, gy)], grip, 3)
    p.line([(gx - nx * gw, gy - ny * gw), (gx + nx * gw, gy + ny * gw)], guard, 3)
    p.rect(round(x0) - 1, round(y0) - 1, round(x0) + 1, round(y0) + 1, guard)


def check(p, c=OKG):
    p.poly([(3, 16), (8, 11), (12, 15), (23, 4), (28, 9), (12, 25)], c)


def xcross(p, c=NOR, a=6, b=25, w=7):
    p.line([(a, a), (b, b)], c, w)
    p.line([(b, a), (a, b)], c, w)


def arrow_right(p, c=G):
    p.poly([(4, 12), (14, 12), (14, 5), (27, 15.5), (14, 26), (14, 19), (4, 19)], c)


def island(p, x0, x1, y, depth, rock=A_ILE):
    """Ile flottante : dessous rocheux (calque) puis herbe (calque)."""
    w = x1 - x0
    p.new()
    p.poly([(x0, y + 2), (x1, y + 2), (x1 - w // 4, y + 2 + depth // 2), ((x0 + x1) / 2, y + 2 + depth),
            (x0 + w // 4, y + 2 + depth // 2)], rock)
    p.new()
    p.rrect(x0, y - 1, x1, y + 3, GRASS, 1)


def coins(p):
    for y in (20, 16, 12):  # pile, de bas en haut
        p.new()
        p.ell(3, y, 17, y + 7, GOLD)
        p.rect(5, y + 3, 15, y + 3, GOLD_D)
    p.new()
    p.ell(12, 5, 28, 21, GOLD)
    p.ring(15, 8, 25, 18, GOLD_D, 2)


# couleurs de l'interface (11/11/2026, « des couleurs, pas tout gris ») : chaque pictogramme a sa vraie couleur
NAV_C = (92, 176, 242)     # flèches retour / précédent / suivant (bleu clair)
INFO_C = (62, 124, 228)    # info, œil (bleu)
P_BLUE = (74, 136, 236)    # personnages
P_GREEN = (76, 188, 92)
P_ORANGE = (244, 146, 48)
P_PINK = (236, 108, 170)
PAPER = (240, 220, 168)    # parchemin
PAPER_R = (214, 174, 108)  # rouleaux du parchemin
PAPER_L = (156, 108, 62)   # lignes écrites
DOOR = (176, 112, 58)
RED = (230, 66, 70)
BRONZE = (212, 130, 62)
SILVER = (196, 204, 220)
TAG = (250, 214, 70)       # étiquette des titres
HAT = (78, 80, 102)        # haut-de-forme noir (assez clair pour garder le relief)
BAG = (70, 176, 88)        # sac de la boutique
BAG_D = (40, 128, 60)
MEGA = (246, 142, 46)      # haut-parleur
MEGA_D = (204, 98, 30)
WAND = (160, 84, 214)      # baguette
IRON = (190, 196, 212)


def scroll(p):
    p.rect(9, 7, 22, 23, PAPER)
    p.rect(11, 11, 20, 12, PAPER_L)
    p.rect(11, 15, 18, 16, PAPER_L)
    p.rect(11, 19, 16, 19, PAPER_L)
    p.new()
    p.rrect(6, 4, 25, 8, PAPER_R, 2)
    p.new()
    p.rrect(6, 22, 25, 26, PAPER_R, 2)


def padlock(p):
    p.arc((9, 3, 22, 18), 180, 360, IRON, 4)
    p.rect(9, 10, 12, 13, IRON)
    p.rect(19, 10, 22, 13, IRON)
    p.new()
    p.rrect(6, 13, 25, 27, GOLD, 2)
    p.ell(13, 16, 18, 21, INK)
    p.rect(14, 19, 17, 24, INK)


# =========================================================================================================
# pictogrammes (32x32). Zone utile x 3..28, y 3..28 (le contour prend 2 px autour).

# --------------------------------------------------------------------------------------------- interface


def ic_groupe(p):
    person(p, 9, 4, 4, 6, 23, P_BLUE)
    person(p, 22, 4, 4, 6, 23, P_ORANGE)
    person(p, 15.5, 8, 4, 8, 27, P_GREEN)


def ic_joueurs(p):
    person(p, 11, 4, 4, 7, 26, P_GREEN)
    for y, c in ((9, P_BLUE), (15, P_ORANGE), (21, P_PINK)):
        p.new()
        p.rrect(20, y, 28, y + 3, c, 1)


def ic_inviter(p):
    person(p, 11, 4, 4, 7, 26, P_BLUE)
    p.new()
    p.rect(21, 13, 25, 27, OKG)
    p.rect(17, 18, 29, 22, OKG)


def ic_quitter(p):
    p.rect(4, 4, 17, 27, mix(DOOR, BLACK, 0.25))
    p.rect(7, 7, 14, 27, INK)
    p.new()
    p.poly([(7, 7), (13, 10), (13, 28), (7, 27)], DOOR)
    p.rect(11, 17, 11, 18, GOLD)
    p.new()
    p.poly([(14, 13), (20, 13), (20, 8), (28, 16), (20, 24), (20, 19), (14, 19)], RED)


def ic_regarder(p):
    p.poly([(3, 16), (9, 9), (15, 7), (22, 9), (28, 16), (22, 23), (15, 25), (9, 23)], WH)
    p.new()
    p.ell(10, 10, 21, 21, INFO_C)
    p.ell(13, 13, 18, 18, INK)
    p.rect(13, 12, 14, 13, WH)


def ic_retour(p):
    p.arc((10, 7, 27, 26), 270, 90, NAV_C, 6)
    p.rect(12, 7, 18, 12, NAV_C)
    p.rect(8, 21, 18, 26, NAV_C)
    p.poly([(3, 9.5), (13, 2), (13, 17)], NAV_C)


def ic_suivant(p):
    arrow_right(p, NAV_C)


def ic_precedent(p):
    arrow_right(p, NAV_C)
    p.mirror()


def ic_fermer(p):
    xcross(p)


def ic_annuler(p):
    p.ring(3, 3, 28, 28, NOR, 4)
    p.new()
    xcross(p, NOR, 10, 21, 5)


def ic_confirmer(p):
    check(p)


def ic_aucun(p):
    p.ring(4, 4, 27, 27, NOR, 4)
    p.line([(9, 22), (22, 9)], NOR, 4)


def ic_info(p):
    p.ell(4, 4, 27, 27, INFO_C)
    p.rect(14, 8, 17, 11, WH)
    p.rect(14, 14, 17, 22, WH)
    p.rect(12, 14, 13, 15, WH)
    p.rect(12, 21, 19, 22, WH)


def ic_pieces(p):
    coins(p)


def ic_quete(p):
    scroll(p)


def ic_quete_ok(p):
    scroll(p)
    p.new()
    p.poly([(12, 21), (16, 17), (19, 20), (25, 13), (29, 17), (19, 28)], OKG)


def ic_quetes(p):
    p.rrect(5, 5, 26, 28, DOOR, 2)
    p.new()
    p.rect(8, 9, 23, 25, PAPER)
    for y, c in ((11, OKG), (16, OKG), (21, PAPER_R)):
        p.rect(10, y, 12, y + 2, c)
        p.rect(15, y + 1, 21, y + 1, PAPER_L)
    p.new()
    p.rrect(11, 3, 20, 7, IRON, 1)


def ic_horloge(p):
    p.ell(3, 3, 28, 28, GOLD)
    p.ell(7, 7, 24, 24, WH)
    for x, y in ((15, 8), (15, 22), (8, 15), (22, 15)):
        p.rect(x, y, x + 1, y + 1, GOLD_D)
    p.line([(15.5, 16), (15.5, 10)], INK, 2)
    p.line([(15.5, 16), (20, 16)], RED, 2)


def ic_classement(p):
    p.rect(4, 18, 11, 27, SILVER)
    p.rect(6, 21, 8, 22, mix(SILVER, BLACK, 0.4))
    p.new()
    p.rect(20, 21, 27, 27, BRONZE)
    p.rect(23, 23, 24, 24, mix(BRONZE, BLACK, 0.4))
    p.new()
    p.rect(11, 15, 20, 27, GOLD)
    p.rect(15, 18, 16, 24, INK)
    p.px(14, 19, INK)
    p.new()
    star(p, 15.5, 8, 6, 2.6, TAG)


def ic_boutique(p):
    p.arc((10, 3, 21, 16), 180, 360, BAG_D, 3)
    p.new()
    p.poly([(6, 11), (25, 11), (27, 27), (4, 27)], BAG)
    p.new()
    p.ell(11, 14, 20, 23, GOLD)
    p.rect(15, 16, 16, 21, GOLD_D)


def ic_titre(p):
    p.poly([(3, 16), (9, 9), (28, 9), (28, 23), (9, 23)], TAG)
    p.rect(7, 15, 8, 16, INK)
    p.rect(12, 13, 24, 14, GOLD_D)
    p.rect(12, 18, 21, 19, GOLD_D)


def ic_chapeau(p):
    p.rect(9, 4, 22, 20, HAT)
    p.rect(9, 15, 22, 18, RED)
    p.new()
    p.ell(3, 18, 28, 26, HAT)


def ic_trainee(p):
    p.line([(3, 20), (10, 13)], RED, 3)
    p.line([(4, 27), (14, 17)], (250, 196, 50), 3)
    p.line([(10, 28), (17, 21)], (80, 150, 240), 3)
    p.new()
    p.line([(7, 24), (12, 19)], (90, 204, 100), 2)
    p.new()
    star(p, 20, 11, 8, 3.6, GOLD)


def ic_arrivee(p):
    p.rect(3, 12, 8, 19, MEGA_D)
    p.rect(9, 19, 12, 25, MEGA_D)
    p.new()
    p.poly([(8, 12), (19, 5), (19, 26), (8, 19)], MEGA)
    p.new()
    p.arc((15, 10, 25, 21), 300, 60, GOLD, 2)
    p.arc((16, 5, 30, 26), 305, 55, GOLD, 2)


def ic_gadget(p):
    p.line([(5, 26), (16, 15)], WAND, 4)
    p.new()
    star(p, 20, 11, 8, 3.6, GOLD)
    p.new()
    p.rect(6, 9, 8, 9, CYAN)
    p.rect(7, 8, 7, 10, CYAN)
    p.new()
    p.rect(25, 23, 27, 23, PINK)
    p.rect(26, 22, 26, 24, PINK)


def ic_etat_achete(p):  # cintre bleu
    p.arc((12, 3, 19, 10), 180, 90, INFO_C, 2)
    p.line([(15.5, 9), (15.5, 11)], INFO_C, 3)
    p.poly([(15.5, 10), (28, 21), (28, 24), (3, 24), (3, 21)], INFO_C)
    p.clear_poly([(15.5, 15), (22, 20.5), (9, 20.5)])


def ic_etat_cher(p):
    padlock(p)


def ic_etat_equipe(p):
    check(p)


def ic_etat_prix(p):
    coins(p)


# ------------------------------------------------------------------------------------------------ casino

QMARK = ["..XXXX..", ".XXXXXX.", "XXX..XXX", "XX....XX", ".....XXX", "....XXX.", "...XXX..", "...XX...",
         "...XX...", "........", "...XX...", "...XX..."]


def ic_hasard(p):
    p.bitmap(8, 4, QMARK, {"X": GOLD}, 2)


def die(p, x0, y0, s, face, pips):
    p.rrect(x0, y0, x0 + s, y0 + s, face, 2)
    for a, b in pips:
        p.rect(x0 + a, y0 + b, x0 + a + 1, y0 + b + 1, INK)


def ic_hasard_de(p):
    die(p, 14, 3, 13, WH, [(3, 3), (9, 9), (6, 6)])
    p.new()
    die(p, 4, 14, 13, GOLD, [(3, 3), (9, 3), (3, 9), (9, 9)])


def ic_cat_casino(p):
    p.ell(3, 3, 28, 28, GOLD)
    for b in ((14, 3, 17, 7), (14, 24, 17, 28), (3, 14, 7, 17), (24, 14, 28, 17)):
        p.rect(*b, WH)
    p.new()
    p.ell(9, 9, 22, 22, GOLD_D)
    star(p, 15.5, 16, 4.5, 1.9, WH)


# --------------------------------------------------------------------------------------------- îles (cyan)


def ic_skyblock(p):
    island(p, 4, 27, 16, 11)
    p.new()
    p.rect(14, 8, 17, 15, WOOD)
    p.new()
    p.ell(9, 2, 22, 11, LEAF)


def ic_cat_iles(p):
    island(p, 3, 18, 18, 9)
    island(p, 17, 28, 9, 7)
    p.new()
    p.rect(9, 12, 11, 17, WOOD)
    p.new()
    p.ell(6, 6, 14, 13, LEAF)


def ic_oneblock(p):
    p.poly([(14, 7), (25, 12), (14, 17), (3, 12)], GRASS)
    p.poly([(3, 12), (14, 17), (14, 28), (3, 23)], DIRT)
    p.poly([(14, 17), (25, 12), (25, 23), (14, 28)], WOOD_D)
    p.poly([(3, 12), (14, 17), (14, 19), (3, 14)], (80, 172, 70))
    p.poly([(14, 17), (25, 12), (25, 14), (14, 19)], (62, 144, 58))
    p.new()
    star(p, 25, 6, 4.5, 1.6, A_ILE, 4, -90)


def ic_abysse(p):
    side = mix(A_ILE, BLACK, 0.25)
    p.poly([(4, 15), (8, 11), (12, 16), (12, 27), (7, 27), (4, 22)], side)
    p.mirror()
    p.poly([(4, 15), (8, 11), (12, 16), (12, 27), (7, 27), (4, 22)], side)
    p.new()
    p.poly([(16, 3), (21, 10), (21, 22), (16, 28), (11, 22), (11, 10)], A_ILE)
    p.poly([(16, 3), (21, 10), (21, 22), (16, 28)], mix(A_ILE, BLACK, 0.12))


def ic_jump(p):
    for x, y in ((3, 21), (12, 16), (21, 11)):
        p.new()
        p.rect(x, y, x + 6, y + 6, A_ILE)
    p.new()
    for x, y in ((5, 15), (8, 11), (12, 9), (16, 8), (20, 7)):
        p.rect(x, y, x + 1, y + 1, WH)


# ------------------------------------------------------------------------------------------- survie (vert)


def ic_uhc(p):
    p.ell(5, 9, 17, 27, GOLD)
    p.ell(14, 9, 26, 27, GOLD)
    p.rect(11, 11, 20, 24, GOLD)
    p.new()
    p.line([(15, 10), (17, 4)], WOOD_D, 2)
    p.new()
    p.poly([(17, 7), (22, 3), (27, 4), (22, 9)], A_SUR)


def ic_speedrun(p):
    p.rect(13, 2, 18, 5, A_SUR)
    p.new()
    p.ell(4, 6, 27, 29, G)
    p.ell(7, 9, 24, 26, WH)
    p.line([(15.5, 17.5), (20, 12)], A_SUR, 2)
    p.rect(15, 17, 16, 18, INK)
    for x, y in ((15, 10), (15, 24), (8, 17), (22, 17)):
        p.rect(x, y, x + 1, y, G2)


def ic_bingo(p):
    p.rrect(4, 4, 27, 27, WH, 2)
    for i, x in enumerate((6, 13, 20)):
        for j, y in enumerate((6, 13, 20)):
            p.rect(x, y, x + 5, y + 5, A_SUR if i == j else G3)


def ic_cat_survie(p):
    p.rect(14, 22, 17, 28, WOOD)
    p.new()
    p.poly([(15.5, 9), (27, 24), (4, 24)], A_SUR)
    p.new()
    p.poly([(15.5, 5), (24, 17), (7, 17)], A_SUR)
    p.new()
    p.poly([(15.5, 2), (21, 10), (10, 10)], A_SUR)


# ---------------------------------------------------------------------------------------- équipes (bleu)


def ic_towers(p):
    p.rect(9, 12, 22, 28, STONE)
    for x in (8, 14, 20):
        p.rect(x, 8, x + 3, 11, STONE)
    p.rect(8, 11, 23, 13, STONE)
    p.rect(13, 22, 18, 28, INK)
    p.ell(13, 19, 18, 24, INK)
    p.rect(14, 15, 17, 16, INK)
    p.new()
    p.rect(14, 2, 15, 8, G2)
    p.new()
    p.rect(17, 2, 24, 6, A_EQ)


def ic_thebridge(p):
    p.rect(4, 22, 27, 28, A_EQ)
    p.new()
    p.rect(3, 9, 28, 19, STONE)
    p.d.ellipse([9, 15, 22, 35], fill=(0, 0, 0, 0))
    p.rect(3, 19, 7, 26, STONE)
    p.rect(24, 19, 28, 26, STONE)
    p.rect(3, 12, 28, 12, G2)
    p.new()
    p.rect(4, 3, 5, 8, G2)
    p.rect(6, 3, 10, 6, A_CO)
    p.new()
    p.rect(26, 3, 27, 8, G2)
    p.rect(21, 3, 25, 6, A_EQ)


def ic_bedwars(p):
    p.rect(4, 8, 7, 27, WOOD)
    p.rect(25, 14, 28, 27, WOOD)
    p.rect(7, 20, 25, 23, WOOD)
    p.new()
    p.rect(8, 13, 13, 19, WH)
    p.new()
    p.rect(13, 12, 27, 19, A_EQ)


def ic_ballsofsteel(p):
    p.ell(3, 3, 28, 28, STONE)
    p.new()
    p.poly([(15.5, 9), (22, 15.5), (15.5, 22), (9, 15.5)], A_EQ)


def ic_sheepwars(p):
    p.rect(10, 20, 12, 27, INK)
    p.rect(20, 20, 22, 27, INK)
    p.new()
    for b in ((8, 9, 17, 18), (13, 6, 23, 16), (18, 9, 28, 19), (8, 12, 26, 22)):
        p.ell(*b, A_EQ_L)
    p.new()
    p.rrect(3, 9, 11, 19, (78, 82, 98), 2)
    p.rect(5, 12, 6, 13, WH)


def ic_paintball(p):
    p.ell(6, 6, 22, 22, A_EQ)
    for b in ((3, 4, 8, 9), (20, 3, 25, 8), (3, 18, 8, 23), (13, 3, 16, 6)):
        p.ell(*b, A_EQ)
    p.new()
    p.ell(15, 15, 27, 27, WH)


def ic_cat_equipes(p):
    shield = [(4, 4), (27, 4), (27, 15), (16, 28), (15, 28), (4, 15)]
    p.poly(shield, G)
    p.poly([(7, 7), (15, 7), (15, 24), (7, 15)], A_CO)
    p.poly([(16, 7), (24, 7), (24, 15), (16, 24)], A_EQ)


# ----------------------------------------------------------------------------------------- combat (rouge)


def ic_skywars(p):
    for b in ((3, 16, 13, 26), (8, 12, 20, 25), (17, 15, 28, 26)):
        p.ell(*b, WH)
    p.rect(6, 21, 25, 26, WH)
    p.new()
    sword(p, 15.5, 25, 15.5, 3)


def ic_hungergames(p):
    p.rect(4, 13, 27, 27, WOOD)
    p.rect(4, 13, 27, 14, WOOD_D)
    p.new()
    p.rrect(4, 5, 27, 12, WOOD, 2)
    p.new()
    p.rect(13, 9, 18, 16, A_CO)
    p.rect(15, 12, 16, 13, INK)


def ic_pvp(p):
    p.poly([(3, 7), (17, 7), (17, 18), (10, 26), (3, 18)], A_CO)
    p.new()
    sword(p, 9, 27, 27, 9)


def ic_pitchout(p):
    p.line([(4, 27), (14, 17)], WOOD, 4)
    p.new()
    star(p, 19, 12, 9, 4, A_CO, 6, -90)
    p.new()
    p.ell(23, 3, 28, 8, WH)


def ic_jumpleague(p):
    p.poly([(11, 4), (20, 4), (20, 16), (28, 19), (28, 27), (11, 27)], A_CO)
    p.rect(11, 24, 28, 27, G2)
    p.new()
    p.poly([(11, 8), (2, 5), (4, 11), (2, 15), (6, 16), (4, 20), (11, 20)], WH)


def ic_cat_combat(p):
    sword(p, 5, 27, 26, 6)
    p.new()
    sword(p, 26, 27, 5, 6)


# --------------------------------------------------------------------------------------------- TNT (orange)


def tnt_block(p, x0, y0, x1, y1):
    p.rect(x0, y0, x1, y1, A_TNT)
    h = (y1 - y0) // 3
    p.rect(x0, y0 + h, x1, y1 - h, WH)


def ic_cat_tnt(p):
    for x0, x1, top in ((5, 10, 11), (21, 26, 11), (12, 19, 8)):
        p.new()
        p.rect(x0, top, x1, 27, A_TNT)
        p.rect(x0, 16, x1, 19, WH)
    p.new()
    p.line([(16, 8), (17, 4), (20, 3)], INK, 2)
    p.new()
    star(p, 22, 4, 3.5, 1.4, GOLD, 4, -45)


def ic_tntrun(p):
    p.ell(16, 3, 22, 9, A_TNT)
    p.line([(18, 10), (14, 18)], A_TNT, 4)
    p.line([(17, 12), (23, 15), (26, 13)], A_TNT, 3)
    p.line([(16, 12), (11, 13), (8, 17)], A_TNT, 3)
    p.line([(14, 18), (20, 21), (21, 27)], A_TNT, 3)
    p.line([(14, 18), (10, 23), (5, 24)], A_TNT, 3)
    p.new()
    for y, x0 in ((8, 3), (13, 3), (18, 4)):
        p.rect(x0, y, x0 + 4, y + 1, G3)


def ic_tnttag(p):
    tnt_block(p, 5, 8, 26, 27)
    for x in (8, 14, 20):
        p.rect(x, 16, x + 3, 18, INK)
    p.new()
    p.line([(15, 8), (16, 4), (19, 3)], INK, 2)
    p.new()
    star(p, 21, 4, 3.5, 1.4, GOLD, 4, -45)


def ic_bowspleef(p):
    p.arc((4, 4, 34, 34), 180, 270, WOOD, 4)
    p.line([(5, 19), (19, 5)], G3, 1)
    p.new()
    p.line([(7, 24), (23, 8)], G3, 2)
    p.poly([(27, 4), (19, 7), (24, 12)], G)
    p.new()
    p.poly([(4, 23), (8, 22), (9, 27), (5, 28)], A_TNT)


def ic_pvprun(p):
    sword(p, 12, 27, 28, 6, guard=A_TNT)
    p.new()
    for y, x0 in ((9, 3), (14, 3), (19, 4)):
        p.rect(x0, y, x0 + 5, y + 1, G3)


def ic_tntwars(p):
    p.line([(9, 21), (24, 11)], G2, 8)
    p.ell(21, 6, 28, 15, G)
    p.ell(23, 8, 26, 13, INK)
    p.new()
    p.ell(5, 18, 15, 28, WOOD)
    p.ell(9, 22, 11, 24, WOOD_D)
    p.new()
    tnt_block(p, 3, 3, 10, 10)


def ic_tntwizards(p):
    p.poly([(17, 3), (21, 7), (24, 21), (8, 21), (13, 9)], A_TNT)
    p.rect(9, 17, 23, 19, GOLD)
    p.new()
    p.ell(3, 19, 28, 26, mix(A_TNT, BLACK, 0.2))
    p.new()
    star(p, 15, 13, 3.5, 1.5, GOLD)


# ------------------------------------------------------------------------------------- ambiance (violet)


def ic_cat_ambiance(p):
    p.ell(5, 3, 26, 24, A_AMB)
    p.rect(5, 12, 26, 16, A_AMB)
    p.poly([(5, 16), (26, 16), (20, 28), (11, 28)], A_AMB)
    p.rect(9, 11, 13, 13, INK)
    p.rect(18, 11, 22, 13, INK)
    p.poly([(10, 18), (21, 18), (18, 23), (13, 23)], INK)


def ic_murder(p):
    p.poly([(13, 18), (25, 4), (28, 5), (17, 21)], G3)
    p.line([(10, 16), (16, 22)], G2, 3)
    p.new()
    p.line([(5, 26), (12, 19)], A_AMB, 4)
    p.new()
    p.ell(3, 8, 10, 15, A_CO)
    p.poly([(6.5, 3), (3.5, 11), (9.5, 11)], A_CO)


def ic_loupgarou(p):
    p.ell(14, 2, 28, 16, A_AMB_L)
    p.new()
    p.poly([(4, 5), (10, 11), (4, 13)], G2)
    p.poly([(21, 5), (15, 11), (21, 13)], G2)
    p.ell(3, 9, 22, 25, G)
    p.poly([(8, 19), (17, 19), (14, 28), (11, 28)], G3)
    p.rect(7, 15, 9, 16, GOLD)
    p.rect(16, 15, 18, 16, GOLD)
    p.rect(11, 25, 14, 26, INK)


def ic_hideseek(p):
    p.rect(4, 6, 27, 27, A_AMB)
    for x in (11, 20):
        p.rect(x, 6, x, 27, mix(A_AMB, BLACK, 0.3))
    p.rect(7, 12, 24, 19, INK)
    p.ell(8, 13, 13, 18, WH)
    p.ell(18, 13, 23, 18, WH)
    p.rect(11, 15, 12, 17, INK)
    p.rect(21, 15, 22, 17, INK)


def ic_blockparty(p):
    for i, col in enumerate((PINK, CYAN, GOLD, GREEN)):
        p.new()
        p.rect(3 + i * 7, 21, 8 + i * 7, 27, col)
    p.new()
    p.rect(18, 3, 20, 15, A_AMB)
    p.poly([(20, 3), (26, 6), (26, 10), (20, 7)], A_AMB)
    p.ell(10, 11, 20, 19, A_AMB)


def ic_buildbattle(p):
    brick = (206, 98, 72)
    p.rect(15, 17, 28, 28, brick)
    for y in (20, 24):
        p.rect(15, y, 28, y, G3)
    for x, y0, y1 in ((21, 17, 19), (18, 21, 23), (25, 21, 23), (21, 25, 28)):
        p.rect(x, y0, x, y1, G3)
    p.new()
    p.line([(4, 26), (14, 16)], A_AMB, 4)
    p.new()
    p.poly([(8, 8), (14, 3), (24, 12), (18, 17)], G)


def ic_boatrace(p):
    p.rect(13, 2, 14, 16, G2)
    p.new()
    p.rect(15, 3, 26, 11, WH)
    for i in range(4):
        for j in range(3):
            if (i + j) % 2 == 0:
                p.rect(15 + 3 * i, 3 + 3 * j, 17 + 3 * i, 5 + 3 * j, INK)
    p.new()
    p.rect(2, 24, 29, 28, A_AMB)
    p.new()
    p.poly([(3, 16), (28, 16), (24, 24), (7, 24)], WOOD)


def ic_horscours(p):
    ghost = A_AMB_L
    p.ell(6, 3, 25, 21, ghost)
    p.rect(6, 12, 25, 26, ghost)
    for pts in ([(6, 27), (6, 23), (10, 27)], [(10, 27), (13, 23), (16, 27)], [(16, 27), (19, 23), (22, 27)],
                [(22, 27), (25, 23), (25, 27)]):
        p.clear_poly(pts)
    p.ell(10, 9, 14, 14, INK)
    p.ell(17, 9, 21, 14, INK)
    p.ell(14, 16, 17, 20, INK)


def ic_deacoudre(p):
    p.rect(3, 20, 28, 28, A_AMB)
    p.new()
    p.poly([(8, 21), (11, 11), (14, 18), (16, 7), (18, 18), (21, 11), (24, 21)], WH)
    p.new()
    for b in ((4, 6, 8, 10), (23, 5, 27, 9)):
        p.ell(*b, WH)


def ic_dropper(p):
    p.ell(5, 21, 26, 28, G)
    p.ell(10, 23, 21, 26, G2)
    p.new()
    p.poly([(12, 3), (19, 3), (19, 12), (25, 12), (15.5, 22), (6, 12), (12, 12)], A_AMB)


def ic_elytra(p):
    wing = [(15, 4), (8, 5), (3, 13), (4, 27), (9, 23), (14, 15)]
    p.poly(wing, A_AMB_L)
    p.line([(13, 8), (6, 18)], A_AMB, 2)
    p.mirror()
    p.poly(wing, A_AMB_L)
    p.line([(13, 8), (6, 18)], A_AMB, 2)


def ic_soleil(p):
    for a in range(0, 360, 45):
        r = math.radians(a)
        p.line([(15.5 + 9 * math.cos(r), 15.5 + 9 * math.sin(r)),
                (15.5 + 12.5 * math.cos(r), 15.5 + 12.5 * math.sin(r))], GOLD, 3)
    p.new()
    p.ell(7, 7, 24, 24, GOLD)
    p.rect(8, 12, 14, 16, A_AMB)
    p.rect(17, 12, 23, 16, A_AMB)
    p.rect(14, 13, 17, 13, A_AMB)
    p.rect(12, 20, 19, 20, GOLD_D)


def ic_infection(p):
    p.rrect(5, 4, 26, 27, (112, 192, 92), 2)
    p.rect(5, 4, 26, 8, (80, 156, 70))
    p.rect(8, 12, 13, 16, A_AMB)
    p.rect(18, 12, 23, 16, A_AMB)
    p.rect(13, 20, 18, 22, INK)


# =========================================================================================================
# catégories (ordre de la planche) et composition


ICON_CATS = {
    "iles": ["skyblock", "oneblock", "abysse", "jump", "cat_iles"],
    "survie": ["uhc", "speedrun", "bingo", "cat_survie", "etat_equipe"],
    "equipes": ["towers", "thebridge", "bedwars", "ballsofsteel", "sheepwars", "paintball", "cat_equipes", "etat_achete"],
    "combat": ["skywars", "hungergames", "pvp", "pitchout", "jumpleague", "cat_combat"],
    "tnt": ["tntrun", "tnttag", "bowspleef", "pvprun", "tntwars", "tntwizards", "cat_tnt"],
    "ambiance": ["murder", "loupgarou", "hideseek", "blockparty", "buildbattle", "boatrace", "horscours", "deacoudre",
                 "elytra", "soleil", "infection", "dropper", "cat_ambiance"],
    "casino": ["cat_casino", "hasard", "hasard_de", "etat_prix"],
    "neutre": ["boutique", "pieces", "quete", "quetes", "quete_ok", "horloge", "info", "inviter", "joueurs", "groupe",
               "classement", "regarder", "quitter", "retour", "fermer", "suivant", "precedent", "confirmer", "annuler",
               "aucun", "chapeau", "trainee", "titre", "arrivee", "gadget", "etat_cher"],
}
CAT_OF = {n: c for c, names in ICON_CATS.items() for n in names}
# icônes qui existent déjà dans la base (67) + ajoutées par ce pack (art_ui puis art_lobby)
NEW_ICONS = ["precedent", "confirmer", "annuler", "etat_equipe", "etat_achete", "etat_prix", "etat_cher"]


def _shade(layer):
    """Relief simple : bord haut/gauche éclairci, bord bas/droite (2 px en bas si la forme est épaisse) assombri,
    1 px de reflet dans le coin haut-gauche. Les détails sombres (INK...) restent plats."""
    a = layer.getchannel("A").load()
    src = layer.load()
    out = layer.copy()
    px = out.load()

    def inside(x, y):
        return 0 <= x < 32 and 0 <= y < 32 and a[x, y] > 0

    lights = []
    count = 0
    for y in range(32):
        for x in range(32):
            if not a[x, y]:
                continue
            count += 1
            c = src[x, y][:3]
            if _lum(c) < 75:
                continue
            if not inside(x, y - 1) or not inside(x - 1, y):
                px[x, y] = C(mix(c, WHITE, 0.42))
                lights.append((x + y, y, x, c))
            elif not inside(x, y + 1) or not inside(x + 1, y) or (not inside(x, y + 2) and inside(x, y - 3)):
                px[x, y] = C(mix(c, BLACK, 0.34))
    if count >= 40 and lights:
        _, y, x, c = min(lights)
        for dx, dy in ((1, 1), (2, 1), (1, 2)):
            if inside(x + dx, y + dy) and _lum(src[x + dx, y + dy][:3]) >= 75:
                px[x + dx, y + dy] = C(mix(c, WHITE, 0.85))
                break
    return out


def _grow(mask, plus=False):
    """Dilate un masque L de 1 px (carré 3x3, ou en croix si plus=True)."""
    if not plus:
        return mask.filter(ImageFilter.MaxFilter(3))
    out = mask.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        sh = Image.new("L", mask.size, 0)
        sh.paste(mask, (dx, dy))
        out = ImageChops.lighter(out, sh)
    return out


def _with_outline(layer):
    a = layer.getchannel("A").point(lambda v: 255 if v else 0)
    ring = Image.new("RGBA", layer.size, C(OUT))
    ring.putalpha(_grow(a))
    ring.alpha_composite(layer)
    return ring


def icon32(name):
    p = Pen()
    globals()["ic_" + name](p)
    sym = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for layer in p.layers:
        if layer.getbbox():
            sym.alpha_composite(_with_outline(_shade(layer)))
    # contour extérieur : 1 px de plus autour de toute la forme (épaisseur totale 2 px)
    a = sym.getchannel("A").point(lambda v: 255 if v else 0)
    under = Image.new("RGBA", (32, 32), C(OUT))
    under.putalpha(_grow(a, plus=True))
    under.alpha_composite(sym)
    return under


def icon(name):
    return icon32(name).resize((64, 64), Image.NEAREST)


def all_icon_names():
    return sorted(CAT_OF)


def icons():
    return {n: icon(n) for n in all_icon_names()}



# =========================================================================================================
# fonds de menus

# 11/11/2026 (référence de l'admin, remplace le vert) : cadre gris-bleu très foncé, toutes les cases (menu ET
# inventaire du joueur) en violet nuit avec une grille plus sombre, barre dorée lumineuse à la place du libellé
# « Inventaire » (pointe de flèche à gauche, encoches rouges sur les bords), hotbar encadrée d'or. Même fond pour
# tous les menus. L'intérieur des cases reste translucide : le glyphe du fond est du texte, dessiné par-dessus
# les objets, qui doivent rester visibles.
FRAME = (42, 47, 58)        # #2A2F3A
FRAME_LINE = (74, 82, 99)   # fin liseré plus clair
FRAME_EDGE = (18, 20, 26)
GRID = (22, 15, 56)         # #160F38 : grille entre les cases (opaque)
CELL = (43, 31, 94)         # #2B1F5E : intérieur des cases
CELL_USED = (58, 44, 122)   # cases utilisées : très légèrement plus claires
CELL_HI = (88, 72, 168)     # reflet en haut à gauche
CELL_ALPHA = 128         # opacité de l intérieur des cases (plus haut = violet plus fidèle, mais objets voilés si le titre passe dessus)
GOLD_BAR = (242, 182, 50)   # #F2B632
GOLD_CORE = (255, 226, 122) # #FFE27A
NOTCH = (128, 30, 38)       # encoches rouge sombre
NAV = CELL_USED

# cases du menu de chaque fond : {style: [cases]}. Styles : item (couleur du menu), nav (boutons gris),
# head (en-tête de ligne, case pleine de la couleur), carte (objet + case d'état dessous, reliées),
# or / argent / bronze (podium), ok / non (confirmer / annuler).
# Menus DeluxeHub (selector ... ambiance) : cases relevées sur les fonds d'origine.
# Menus EemiCore : cases du code (EemiCore 1.6.1). Les boutons qui n'apparaissent que parfois (pages
# précédente/suivante, invitation reçue, quitter le groupe, retour au profil d'un autre) ne sont pas cerclés.
SHOP_CARDS = [9, 11, 13, 15, 17, 28, 30, 32, 34]  # page de catégorie, en quinconce : objets (état en dessous, +9)
BACKGROUNDS = [  # ordre = glyphe U+E310 + index (UI_BACKGROUNDS d'EemiCore)
    ("selector", 5, "or", {"item": [4, 11, 13, 15, 22, 29, 31, 33], "nav": [40]}),
    ("iles", 3, "cyan", {"item": [10, 12, 14, 16], "nav": [22]}),
    ("survie", 3, "vert", {"item": [11, 13, 15], "nav": [22]}),
    ("equipes", 5, "bleu", {"item": [11, 13, 15, 29, 31, 33], "nav": [40]}),
    ("combat", 5, "rouge", {"item": [11, 13, 15, 30, 32], "nav": [40]}),
    ("tnt", 5, "orange", {"item": [11, 13, 15, 29, 31, 33], "nav": [40]}),
    ("ambiance", 5, "violet", {"item": [4, 10, 12, 14, 16, 20, 22, 24, 28, 30, 32, 34], "nav": [40]}),
    ("profile", 6, "or", {"or": [4], "item": list(range(19, 26)) + list(range(28, 35)) + list(range(37, 44)),
                          "head": [45, 46, 47, 52, 53], "nav": [49]}),
    ("players", 6, "bleu", {"item": list(range(0, 45)), "nav": [48, 49]}),
    ("toplevel", 4, "or", {"or": [4], "argent": [12], "bronze": [14], "item": list(range(19, 26)), "nav": [30, 31]}),
    ("shop", 4, "vert", {"or": [8], "item": [2, 4, 6, 12, 14], "nav": [30, 31]}),  # EemiCore 1.6.3 : 36 cases
    ("shopcat", 6, "vert", {"item": [4], "or": [8], "carte": SHOP_CARDS, "nav": [48, 49]}),
    ("quests", 3, "or", {"item": [4, 10, 11, 12, 13, 14, 15, 16], "nav": [21, 22]}),
    ("party", 6, "bleu", {"item": [4] + list(range(10, 18)) + list(range(19, 27)) + list(range(28, 36)),
                          "head": [9, 18, 27, 47], "nav": [48, 49]}),
    ("pinvite", 6, "vert", {"item": list(range(0, 45)), "nav": [48, 49]}),
    ("confirm", 4, "vert", {"ok": [11], "or": [13], "non": [15], "nav": [30, 31]}),
]
BG_NAMES = [b[0] for b in BACKGROUNDS]


def slot_xy(slot):
    return 7 + (slot % 9) * 18, 17 + (slot // 9) * 18


def _rr(d, box, fill=None, outline=None, r=2):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=1)


def _slot(img, x, y, fill):
    """Case 18x18 : bord opaque (grille), intérieur 16x16 translucide, reflet en haut à gauche."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + 17, y + 17], outline=C(GRID))
    d.rectangle([x + 1, y + 1, x + 16, y + 16], fill=C(fill, CELL_ALPHA))
    d.line([(x + 1, y + 1), (x + 4, y + 1)], fill=C(CELL_HI, CELL_ALPHA + 40))
    d.line([(x + 1, y + 2), (x + 1, y + 4)], fill=C(CELL_HI, CELL_ALPHA + 40))


def background(name, rows, accent_name, slots):
    H = 114 + rows * 18
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # cadre gris-bleu très foncé, coins arrondis, fin liseré plus clair (le titre blanc s'écrit dessus en x 8, y 6)
    _rr(d, [0, 0, 175, H - 1], fill=C(FRAME), outline=C(FRAME_EDGE), r=3)
    _rr(d, [1, 1, 174, H - 2], outline=C(FRAME_LINE), r=2)
    for x, y in ((0, 0), (175, 0), (0, H - 1), (175, H - 1)):
        img.putpixel((x, y), (0, 0, 0, 0))
    # toutes les cases : menu, inventaire, hotbar
    used = {s for lst in slots.values() for s in lst}
    for s in list(slots.get("carte", [])):
        used.add(s + 9)
    for r in range(rows):
        for c in range(9):
            _slot(img, 7 + c * 18, 17 + r * 18, CELL_USED if r * 9 + c in used else CELL)
    iy = rows * 18 + 30
    for r in range(3):
        for c in range(9):
            _slot(img, 7 + c * 18, iy + r * 18, CELL)
    hy = rows * 18 + 88
    for c in range(9):
        _slot(img, 7 + c * 18, hy, CELL)
    # barre dorée à la place du libellé « Inventaire » (texte vanilla gris foncé en x 8, y = H - 94 .. H - 87) :
    # le texte s'écrit sur l'or, bien lisible ; cœur plus clair, halo, pointe de flèche vers la gauche
    by0, by1 = H - 96, H - 85
    for y in range(by0, by1 + 1):
        t = abs((y - (by0 + by1) / 2) / ((by1 - by0) / 2))
        d.line([(9, y), (171, y)], fill=C(mix(GOLD_CORE, GOLD_BAR, min(1.0, t * 1.3))))
    d.line([(9, by0 - 1), (171, by0 - 1)], fill=C(GOLD_CORE, 110))
    d.line([(9, by1 + 1), (171, by1 + 1)], fill=C(GOLD_BAR, 110))
    mid = (by0 + by1) / 2
    d.polygon([(2, mid), (9, by0 - 2), (9, by1 + 2)], fill=C(GOLD_BAR))
    d.line([(4, mid), (9, mid)], fill=C(GOLD_CORE))
    for x0 in (0, 174):  # encoches rouge sombre sur les bords
        d.rectangle([x0, by0 + 2, x0 + 1, by1 - 2], fill=C(NOTCH))
    # hotbar encadrée d'or (2 px)
    d.rectangle([5, hy - 2, 170, hy + 19], outline=C(GOLD_BAR), width=2)
    d.line([(6, hy - 2), (169, hy - 2)], fill=C(GOLD_CORE))
    assert img.getpixel((175, 20))[3] == 255
    assert img.getbbox() == (0, 0, 176, H), (name, img.getbbox())
    return img


def backgrounds():
    return {name: background(name, rows, acc, slots) for name, rows, acc, slots in BACKGROUNDS}


def png_bytes(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()


# =========================================================================================================
# aperçus (rendu « comme en jeu » : coffre vanilla gris, fond, icônes dans les cases)


def vanilla_chest(rows):
    H = 114 + rows * 18
    img = Image.new("RGBA", (176, H), (198, 198, 198, 255))
    d = ImageDraw.Draw(img)

    def slot(x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=(139, 139, 139, 255))
        d.line([(x, y), (x + 16, y)], fill=(55, 55, 55, 255))
        d.line([(x, y), (x, y + 16)], fill=(55, 55, 55, 255))
        d.line([(x + 1, y + 17), (x + 17, y + 17)], fill=(255, 255, 255, 255))
        d.line([(x + 17, y + 1), (x + 17, y + 17)], fill=(255, 255, 255, 255))

    for r in range(rows):
        for c in range(9):
            slot(7 + c * 18, 17 + r * 18)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, rows * 18 + 30 + r * 18)
    for c in range(9):
        slot(7 + c * 18, rows * 18 + 88)
    return img


# ---------------------------------------------------------------------------------------------------------

PREVIEW_ITEMS = {  # contenu fictif posé dans les cases pour les aperçus (nom d'icône, ou « tete »)
    "selector": {4: "hasard", 11: "cat_iles", 13: "cat_survie", 15: "cat_equipes", 22: "cat_casino", 29: "cat_combat",
                 31: "cat_tnt", 33: "cat_ambiance", 40: "fermer"},
    "iles": {10: "skyblock", 12: "abysse", 14: "oneblock", 16: "jump", 22: "retour"},
    "survie": {11: "uhc", 13: "speedrun", 15: "bingo", 22: "retour"},
    "equipes": {11: "towers", 13: "thebridge", 15: "bedwars", 29: "ballsofsteel", 31: "sheepwars", 33: "paintball",
                40: "retour"},
    "combat": {11: "skywars", 13: "hungergames", 15: "pvp", 30: "pitchout", 32: "jumpleague", 40: "retour"},
    "tnt": {11: "tntrun", 13: "tnttag", 15: "tntwars", 29: "bowspleef", 31: "pvprun", 33: "tntwizards", 40: "retour"},
    "ambiance": {4: "horscours", 10: "murder", 12: "loupgarou", 14: "hideseek", 16: "blockparty", 20: "buildbattle",
                 22: "boatrace", 24: "deacoudre", 28: "elytra", 30: "soleil", 32: "infection", 34: "dropper",
                 40: "retour"},
    "profile": {**{4: "tete", 45: "boutique", 46: "quetes", 47: "groupe", 49: "fermer", 52: "joueurs",
                     53: "classement"},
                    **{s: g for s, g in zip(list(range(19, 26)) + list(range(28, 35)) + list(range(37, 44)),
                                            ["skyblock", "abysse", "oneblock", "uhc", "speedrun", "bingo", "towers",
                                             "thebridge", "bedwars", "ballsofsteel", "sheepwars", "skywars",
                                             "hungergames", "pvp", "pitchout", "tntrun", "tnttag", "tntwars",
                                             "murder", "loupgarou", "hideseek"])}},
    "players": {48: "retour", 49: "fermer", 53: "suivant", **{s: "tete" for s in range(0, 13)}},
    "toplevel": {30: "retour", 31: "fermer", **{s: "tete" for s in [4, 12, 14, 19, 20, 21, 22, 23, 24, 25]}},
    "shop": {8: "pieces", 2: "trainee", 4: "chapeau", 6: "titre", 12: "arrivee", 14: "gadget", 30: "retour",
             31: "fermer"},
    # 11/11/2026 (EemiCore 1.6.2) : sur une page de catégorie, chaque cosmétique montre son VRAI objet (« item:<nom> »,
    # rendu 16x16 de l'objet vanilla), pas l'icône de la catégorie ; les icônes dessinées restent sur l'en-tête (4),
    # les cases d'état (+9) et les boutons.
    "shopcat": {4: "chapeau", 8: "pieces", 9: "item:pumpkin", 18: "etat_equipe", 11: "item:creeper_head",
                20: "etat_achete", 13: "item:wither_skeleton_skull", 22: "etat_prix", 15: "item:glass",
                24: "etat_prix", 17: "item:diamond_block", 26: "etat_cher", 28: "item:tnt", 37: "etat_prix",
                48: "retour", 49: "fermer", 53: "suivant"},
    "quests": {4: "horloge", 11: "quete", 13: "quete_ok", 15: "quete", 21: "retour", 22: "fermer"},
    "party": {4: "groupe", 9: "joueurs", 10: "tete", 11: "tete", 12: "tete", 18: "inviter", 19: "aucun",
              27: "regarder", 28: "bedwars", 29: "skywars", 47: "inviter", 48: "retour", 49: "fermer", 51: "quitter"},
    "pinvite": {48: "retour", 49: "fermer", **{s: "tete" for s in range(0, 7)}},
    "confirm": {11: "confirmer", 13: "chapeau", 15: "annuler", 30: "retour", 31: "fermer"},
}
PREVIEW_TITLES = {"selector": "Mini-jeux", "iles": "Iles et aventure", "survie": "Survie", "equipes": "Equipes",
                  "combat": "Combat", "tnt": "Jeux TNT", "ambiance": "Ambiance", "profile": "Profil de Enosik",
                  "players": "Joueurs en ligne", "toplevel": "Classement des niveaux", "shop": "Boutique - 2450 pieces",
                  "shopcat": "Chapeaux - 2450 pieces", "quests": "Quetes du jour", "party": "Groupe",
                  "pinvite": "Inviter un joueur", "confirm": "Confirmer l'achat"}


def _head16():
    face = ["SSSSSSSS", "HHHHHHHH", "HKKKKKKH", "KKKKKKKK", "KWBKKBWK", "KKKNNKKK", "KKMMMMKK", "KKKKKKKK"]
    pal = {"S": (60, 40, 25), "H": (80, 52, 30), "K": (214, 160, 120), "W": (255, 255, 255), "B": (60, 80, 180),
           "N": (176, 120, 86), "M": (140, 80, 60)}
    im = Image.new("RGBA", (8, 8))
    for y, r in enumerate(face):
        for x, ch in enumerate(r):
            im.putpixel((x, y), C(pal[ch]))
    return im.resize((16, 16), Image.NEAREST)


_ITEM_DIR = __import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "..",
                                       "EemiBingo", "src", "main", "resources", "icons")
_ITEM_FACES = {  # objets vanilla absents des rendus d'EemiBingo, redessinés en 8x8 pour les aperçus seulement
    "creeper_head": (["GGgGGgGG", "gGGGGGgG", "GKKGGKKG", "GKKGgKKG", "GgGKKGGg", "GGKKKKGG", "GgKKKKgG",
                      "GGKGGKGg"], {"G": (94, 180, 72), "g": (70, 146, 56), "K": (20, 26, 20)}),
    "wither_skeleton_skull": (["DDDDDDDD", "DdDDDDdD", "DKKDDKKD", "DKKDDKKD", "DDDKKDDD", "DdDDDDdD",
                               "DKDKKDKD", "DDDDDDDD"], {"D": (60, 62, 66), "d": (44, 46, 50), "K": (18, 18, 20)}),
    "diamond_block": (["LLLLLLLL", "LddLLddL", "LdLLLLdL", "LLLDDLLL", "LLLDDLLL", "LdLLLLdL", "LddLLddL",
                       "LLLLLLLL"], {"L": (110, 228, 226), "d": (62, 182, 186), "D": (210, 252, 250)}),
}


def _item16(name):
    """Rendu 16x16 d'un objet vanilla pour les aperçus (rendus d'EemiBingo, sinon dessin 8x8 ci-dessus)."""
    import os
    f = os.path.join(_ITEM_DIR, name + ".png")
    if os.path.exists(f):
        return Image.open(f).convert("RGBA").resize((16, 16), Image.NEAREST)
    rows, pal = _ITEM_FACES[name]
    im = Image.new("RGBA", (8, 8))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            im.putpixel((x, y), C(pal[ch]))
    return im.resize((16, 16), Image.NEAREST)


def preview(name, bg, icon_imgs, scale=3):
    from PIL import ImageFont
    rows = next(r for n, r, _, _ in BACKGROUNDS if n == name)
    H = 114 + rows * 18
    img = vanilla_chest(rows)
    img.alpha_composite(bg.crop((0, 0, 176, H)))
    head = _head16()
    items = dict(PREVIEW_ITEMS.get(name, {}))
    hot = {0: "hasard", 2: "groupe", 6: "boutique", 8: "info"}
    for s, ic in items.items():
        x, y = slot_xy(s)
        im = head if ic == "tete" else _item16(ic[5:]) if ic.startswith("item:") else             icon_imgs[ic].resize((16, 16), Image.NEAREST)
        img.alpha_composite(im, (x + 1, y + 1))
    for c, ic in hot.items():
        img.alpha_composite(icon_imgs[ic].resize((16, 16), Image.NEAREST), (7 + c * 18 + 1, rows * 18 + 88 + 1))
    big = img.resize((176 * scale, H * scale), Image.NEAREST)
    d = ImageDraw.Draw(big)
    try:
        font = ImageFont.truetype("consola.ttf", 8 * scale)
    except OSError:
        font = ImageFont.load_default()
    t = PREVIEW_TITLES.get(name, name)
    d.text((8 * scale + scale, 5 * scale + scale), t, font=font, fill=(60, 60, 60, 255))
    d.text((8 * scale, 5 * scale), t, font=font, fill=(255, 255, 255, 255))
    d.text((8 * scale, (H - 95) * scale), "Inventaire", font=font, fill=(64, 64, 64, 255))
    return big


def planche(icon_imgs, cols=6):
    """Planche des icônes : chaque case montre l'icône sur fond sombre (à gauche) et sur le fond clair des menus
    (à droite), en texture 64x64 et en taille réelle x2 (32 px = 16 px du GUI x2), avec son nom."""
    from PIL import ImageFont
    try:
        font = ImageFont.truetype("consola.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    order = []
    for cat in ("iles", "survie", "equipes", "combat", "tnt", "ambiance", "casino", "neutre"):
        order += sorted(ICON_CATS[cat])
    half, top, chh = 112, 76, 96
    cw = 2 * half
    rows = (len(order) + cols - 1) // cols
    img = Image.new("RGBA", (cols * cw, rows * chh), (20, 22, 28, 255))
    d = ImageDraw.Draw(img)
    for i, n in enumerate(order):
        x, y = (i % cols) * cw, (i // cols) * chh
        d.rectangle([x, y, x + half - 1, y + top - 1], fill=(36, 38, 48, 255))
        d.rectangle([x + half, y, x + cw - 1, y + top - 1], fill=C(mix(CELL, (139, 139, 139), 1 - CELL_ALPHA / 255)))  # case violette sur la case vanilla
        for bx in (x, x + half):
            img.alpha_composite(icon_imgs[n], (bx + 6, y + 6))
            img.alpha_composite(icon_imgs[n].resize((32, 32), Image.NEAREST), (bx + 74, y + 22))
        d.text((x + 8, y + top + 3), n, font=font, fill=(220, 224, 232, 255))
        d.rectangle([x, y, x + cw - 1, y + chh - 1], outline=(10, 10, 14, 255))
    return img
