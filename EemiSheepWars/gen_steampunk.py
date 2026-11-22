"""Map Sheep Wars « Cite des engrenages » (ville steampunk), format SWM1 (ids de blocs 1.8, lus par fromLegacy).

Map originale. Par camp (Rouge a l'ouest, Bleu = miroir en x) :
  - quartier de la place (niveau G) : place de depart 15 x 15, tour de l'horloge, maisons a colombages ;
  - usine surelevee (G + 5) au nord : toit en dents de scie, cheminees, grand engrenage sur la facade ;
  - quai des dirigeables (G - 3) au sud : dirigeable amarre, grue, caisses ;
  - deux ilots a l'ouest (chateau d'eau, eolienne) et deux pres du vide (reservoir sous pression, tour radio) ;
au centre, une plate-forme en engrenage avec une bobine Tesla, et un grand dirigeable au-dessus du vide.

Usage : python gen_steampunk.py <dossier des ressources maps> [dossier des apercus]
"""
import gzip
import math
import random
import struct
import sys

W, H, L = 113, 64, 113
MID, ZC = W // 2, L // 2
G = 26  # sol de la place (relatif au bas de la map)
RED_MAX = MID - 8  # derniere colonne du camp rouge ; vide de MID-7 a MID+7 (sauf le centre)

# --- blocs (id, data) 1.8
AIR = (0, 0)
STONE, ANDESITE, POL_ANDESITE, COBBLE = (1, 0), (1, 5), (1, 6), (4, 0)
SBRICK, SBRICK_MOSS, SBRICK_CRACK, SBRICK_CHIS = (98, 0), (98, 1), (98, 2), (98, 3)
BRICK, NBRICK = (45, 0), (112, 0)
SPRUCE, DARKOAK, OAK = (5, 1), (5, 5), (5, 0)
DO_LOG, DO_LOG_X, DO_LOG_Z = (162, 1), (162, 5), (162, 9)
SP_LOG, SP_LOG_X, SP_LOG_Z = (17, 1), (17, 5), (17, 9)
IRON, GOLD, COAL_BLOCK, REDSTONE_BLOCK = (42, 0), (41, 0), (173, 0), (152, 0)
COPPER = (159, 1)  # argile orange = cuivre
BRONZE = (159, 12)  # argile marron
VERDIGRIS, VERDI_BRICK, VERDI_DARK = (168, 0), (168, 1), (168, 2)  # prismarine = cuivre oxyde
QUARTZ, QUARTZ_PILLAR, QUARTZ_CHIS = (155, 0), (155, 2), (155, 1)
GLASS_PANE, IRON_BARS = (102, 0), (101, 0)
GLOW, SEA_LANTERN = (89, 0), (169, 0)
DO_FENCE, SP_FENCE, NB_FENCE = (191, 0), (188, 0), (113, 0)
COBWALL = (139, 0)
CAULDRON, HOPPER, ANVIL, BREWING = (118, 0), (154, 0), (145, 0), (117, 0)
BOOKSHELF, CRAFTING = (47, 0), (58, 0)
WOOL_BROWN, WOOL_ORANGE, WOOL_WHITE, WOOL_GRAY, WOOL_BLACK = (35, 12), (35, 1), (35, 0), (35, 7), (35, 15)
CLAY_BLACK, CLAY_GRAY = (159, 15), (159, 7)
COAL_ORE, IRON_ORE = (16, 0), (15, 0)
WATER = (9, 0)
# escaliers : data 0 monte vers l'est, 1 ouest, 2 sud, 3 nord, +4 a l'envers
ST_BRICK, ST_SBRICK, ST_DARKOAK, ST_SPRUCE, ST_NBRICK, ST_QUARTZ, ST_COBBLE = 108, 109, 164, 134, 114, 156, 67
SLAB_SB, SLAB_BRICK, SLAB_SB_TOP = (44, 5), (44, 4), (44, 13)
SLAB_SPRUCE, SLAB_DO, SLAB_DO_TOP = (126, 1), (126, 5), (126, 13)
STAIRS = {53, 67, 108, 109, 114, 128, 134, 135, 136, 156, 163, 164, 180}
FACING_45 = {61, 23, 158, 33, 29, 54, 146}  # fours, distributeurs, pistons, coffres : 4 ouest / 5 est


def noise2(seed):
    rnd = random.Random(seed)
    pts = {}

    def at(i, j):
        if (i, j) not in pts:
            pts[(i, j)] = rnd.random()
        return pts[(i, j)]

    def f(x, z, s=6.0):
        gx, gz = x / s, z / s
        i, j = math.floor(gx), math.floor(gz)
        fx, fz = gx - i, gz - j
        fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
        a = at(i, j) * (1 - fx) + at(i + 1, j) * fx
        b = at(i, j + 1) * (1 - fx) + at(i + 1, j + 1) * fx
        return a * (1 - fz) + b * fz

    return f


class Grid:
    def __init__(self):
        self.ids = bytearray(W * H * L)
        self.data = bytearray(W * H * L)

    def idx(self, x, y, z):
        return (y * L + z) * W + x

    def ok(self, x, y, z):
        return 0 <= x < W and 0 <= y < H and 0 <= z < L

    def set(self, x, y, z, b, d=None):
        if isinstance(b, tuple):
            b, d = b
        if d is None:
            d = 0
        if self.ok(x, y, z):
            i = self.idx(x, y, z)
            self.ids[i] = b
            self.data[i] = d

    def get(self, x, y, z):
        return self.ids[self.idx(x, y, z)] if self.ok(x, y, z) else 0

    def air(self, x, y, z):
        return self.get(x, y, z) == 0

    def put_air(self, x, y, z, b, d=None):
        if self.air(x, y, z):
            self.set(x, y, z, b, d)

    def write(self, path):
        out = bytearray(b"SWM1") + struct.pack(">hhh", W, H, L)
        for i in range(W * H * L):
            out.append(self.ids[i])
            out.append(self.data[i])
        with gzip.open(path, "wb") as f:
            f.write(bytes(out))


def mirror_data(b, d):
    if b in STAIRS:
        return {0: 1, 1: 0}.get(d & 3, d & 3) | (d & 4)
    if b == 65:  # echelle
        return {4: 5, 5: 4}.get(d, d)
    if b == 50:  # torche
        return {1: 2, 2: 1}.get(d, d)
    if b in FACING_45:
        return {4: 5, 5: 4}.get(d & 7, d & 7) | (d & 8)
    return d


def mirror_camp(g):
    for y in range(H):
        for z in range(L):
            for x in range(MID):
                i = g.idx(x, y, z)
                b = g.ids[i]
                if b:
                    g.set(2 * MID - x, y, z, b, mirror_data(b, g.data[i]))


def box(g, x0, x1, y0, y1, z0, z1, blk, hollow=False):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                if hollow and x0 < x < x1 and z0 < z < z1:
                    g.set(x, y, z, AIR)
                else:
                    g.set(x, y, z, blk)


def clear(g, x0, x1, y0, y1, z0, z1):
    box(g, x0, x1, y0, y1, z0, z1, AIR)


# ---------------------------------------------------------------------------- plates-formes flottantes


def rrect_mask(x0, x1, z0, z1, r, seed, rough=2.0):
    """Rectangle aux coins arrondis, bords irreguliers. Renvoie un ensemble de (x, z)."""
    n = noise2(seed)
    cells = set()
    for x in range(x0 - 3, x1 + 4):
        for z in range(z0 - 3, z1 + 4):
            dx = max(x0 + r - x, 0, x - (x1 - r))
            dz = max(z0 + r - z, 0, z - (z1 - r))
            d = math.hypot(dx, dz) - r
            if d <= rough * (n(x, z, 4) - 0.5) * 2 and x <= RED_MAX:
                cells.add((x, z))
    return cells


def disc_mask(cx, cz, r, seed, rough=1.2):
    n = noise2(seed)
    cells = set()
    R = int(r + rough + 2)
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            if math.hypot(x - cx, z - cz) <= r + rough * (n(x, z, 3) - 0.5) * 2 and x <= RED_MAX:
                cells.add((x, z))
    return cells


def dist_in(cells):
    """Distance (pas de 4 voisins) de chaque case au bord du masque."""
    from collections import deque
    dist = {}
    q = deque()
    for (x, z) in cells:
        if any((x + a, z + b) not in cells for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            dist[(x, z)] = 0
            q.append((x, z))
    while q:
        x, z = q.popleft()
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + a, z + b)
            if p in cells and p not in dist:
                dist[p] = dist[(x, z)] + 1
                q.append(p)
    return dist


def platform(g, cells, top, deck, seed, depth_k=1.25, max_depth=19, band=True):
    """Sol de ville sur une masse rocheuse en pointe. deck(x, z) -> bloc du dessus.
    Bord : liseré de pierre taillée puis bandeau de cuivre, roche dessous avec minerais, tuyaux qui pendent."""
    n = noise2(seed)
    rnd = random.Random(seed)
    dist = dist_in(cells)
    for (x, z) in cells:
        d = dist[(x, z)]
        depth = min(max_depth, int(3 + d * depth_k + 4 * n(x, z, 5)))
        for y in range(top - depth, top + 1):
            if y == top:
                b = deck(x, z)
            elif y >= top - 1:
                b = SBRICK if d == 0 else STONE
            elif band and y == top - 2 and d == 0:
                b = COPPER if (x + z) % 6 else GOLD
            elif band and y == top - 3 and d == 0:
                b = SBRICK_CHIS if (x + z) % 4 == 0 else SBRICK
            else:
                r = rnd.random()
                b = (COAL_ORE if r < 0.04 else IRON_ORE if r < 0.06 else COBBLE if r < 0.25 else
                     ANDESITE if r < 0.45 else STONE)
            g.set(x, y, z, b)
        # tuyaux et lanternes qui pendent sous les bords
        if d == 1 and rnd.random() < 0.05:
            yb = top - depth - 1
            ln = rnd.randint(3, 7)
            for k in range(ln):
                g.put_air(x, yb - k, z, IRON_BARS if k < ln - 1 else SEA_LANTERN)


def rail_edge(g, cells, top, blk=DO_FENCE, every_post=None):
    """Garde-corps sur le pourtour d'une plate-forme (laisse les passages marques)."""
    for (x, z) in cells:
        if any((x + a, z + b) not in cells for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            if g.air(x, top + 1, z):
                g.set(x, top + 1, z, blk)


def lamp(g, x, y, z, tall=3):
    for k in range(tall):
        g.set(x, y + k, z, DO_FENCE)
    g.set(x, y + tall, z, GLOW)
    g.set(x, y + tall + 1, z, SLAB_DO)


def pipe(g, pts, blk=COPPER, joint=GOLD):
    """Tuyau en segments droits entre les points (x, y, z) successifs, raccords dores."""
    for (x0, y0, z0), (x1, y1, z1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0))
        for k in range(n + 1):
            x = x0 + (x1 - x0) * k // max(n, 1)
            y = y0 + (y1 - y0) * k // max(n, 1)
            z = z0 + (z1 - z0) * k // max(n, 1)
            g.set(x, y, z, blk)
    for p in pts:
        g.set(*p, joint)


# ---------------------------------------------------------------------------- batiments


def roof_x_ridge(g, x0, x1, z0, z1, y, stair, full, overhang=1):
    """Toit a deux pans, faitage le long de x (pentes au nord et au sud)."""
    z0 -= overhang
    z1 += overhang
    k = 0
    while z0 + k <= z1 - k:
        for x in range(x0 - overhang, x1 + overhang + 1):
            if z0 + k == z1 - k:
                g.set(x, y + k, z0 + k, full)
            else:
                g.set(x, y + k, z0 + k, stair, 2)
                g.set(x, y + k, z1 - k, stair, 3)
        k += 1
    return y + k


def gable_fill(g, x, z0, z1, y, blk):
    """Pignon (mur triangulaire) au bout d'un toit a faitage en x."""
    k = 0
    while z0 + k <= z1 - k:
        for z in range(z0 + k, z1 - k + 1):
            g.set(x, y + k, z, blk)
        k += 1


def townhouse(g, x0, x1, z0, z1, y, rnd, door_south=True, floors=2, wall_low=BRICK, chimney_x=None):
    """Maison a colombages : rez-de-chaussee en brique, etages en chene noir encadre de rondins,
    fenetres, toit a deux pans en escaliers de chene noir, cheminee. Portes traversantes."""
    top = y + 4 * floors
    for yy in range(y + 1, top + 1):
        level = (yy - y - 1) // 4
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    g.set(x, yy, z, AIR)
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                if level == 0:
                    b = SBRICK if corner else wall_low
                else:
                    frame = corner or (yy - y - 1) % 4 == 0 or (x - x0) % 3 == 0 and z in (z0, z1) \
                        or (z - z0) % 3 == 0 and x in (x0, x1)
                    b = DO_LOG if frame else (DARKOAK if (yy - y - 1) % 4 != 2 else SPRUCE)
                win = (yy - y - 1) % 4 in (1, 2) and not corner and (
                    (z in (z0, z1) and (x - x0) % 3 == 2 and x0 + 1 < x < x1) or
                    (x in (x0, x1) and (z - z0) % 3 == 2 and z0 + 1 < z < z1))
                g.set(x, yy, z, GLASS_PANE if win else b)
        if (yy - y - 1) % 4 == 0 and yy > y + 1:  # plancher d'etage
            for x in range(x0 + 1, x1):
                for z in range(z0 + 1, z1):
                    g.set(x, yy, z, SPRUCE)
    # portes traversantes (nord et sud) au milieu
    mx = (x0 + x1) // 2
    for zz in (z0, z1):
        for yy in (y + 1, y + 2):
            g.set(mx, yy, zz, AIR)
        g.set(mx, y + 3, zz, SLAB_DO_TOP)
    # auvent au-dessus de la porte cote place
    az = z1 + 1 if door_south else z0 - 1
    for x in range(mx - 1, mx + 2):
        g.set(x, y + 4, az, ST_DARKOAK, (7 if door_south else 6))
    # echelle vers l'etage
    for yy in range(y + 1, top + 1):
        g.set(x0 + 1, yy, z0 + 1, 65, 5)
    g.set(x0 + 1, top, z0 + 1, 65, 5)
    # toit
    ry = roof_x_ridge(g, x0, x1, z0, z1, top + 1, ST_DARKOAK, DARKOAK)
    for xx in (x0, x1):
        gable_fill(g, xx, z0, z1, top + 1, BRICK)
    # cheminee
    cx = chimney_x if chimney_x is not None else x1 - 2
    cz = z0 + 1
    for yy in range(top + 1, ry + 3):
        g.set(cx, yy, cz, BRICK)
    g.set(cx, ry + 3, cz, NB_FENCE)
    # interieur : quelques meubles
    g.set(x1 - 1, y + 1, z0 + 1, BOOKSHELF)
    g.set(x1 - 1, y + 1, z1 - 1, CRAFTING)
    g.set(x0 + 1, y + 1, z1 - 1, CAULDRON)
    g.set(x1 - 1, y + 5, z1 - 1, BOOKSHELF)
    g.set(mx, y + 3, (z0 + z1) // 2, GLOW)


def clock_face(g, plane, cx, cy, cz, axis):
    """Cadran d'horloge de rayon 3 sur un mur. axis 'x' : cadran dans le plan x = plane (vu de l'est/ouest)."""
    for a in range(-3, 4):
        for b in range(-3, 4):
            d = math.hypot(a, b)
            if d > 3.4:
                continue
            blk = GOLD if d > 2.6 else QUARTZ
            if d > 2.6 and (a == 0 or b == 0):
                blk = COAL_BLOCK  # graduations 12, 3, 6, 9
            if axis == "x":
                g.set(plane, cy + b, cz + a, blk)
            else:
                g.set(cx + a, cy + b, plane, blk)
    # aiguilles : grande vers midi (2 cases), petite vers 3 h (1 case), axe dore
    for a, b in ((0, 1), (0, 2), (1, 0)):
        if axis == "x":
            g.set(plane, cy + b, cz + a, COAL_BLOCK)
        else:
            g.set(cx + a, cy + b, plane, COAL_BLOCK)
    if axis == "x":
        g.set(plane, cy, cz, GOLD)
    else:
        g.set(cx, cy, plane, GOLD)


def clock_tower(g, x0, z0, y):
    """Tour de l'horloge 7 x 7 : brique et pierre taillee, cadrans sur 4 faces, beffroi, fleche de cuivre oxyde."""
    x1, z1 = x0 + 6, z0 + 6
    wall_top = y + 21
    for yy in range(y + 1, wall_top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x0 < x < x1 and z0 < z < z1:
                    g.set(x, yy, z, AIR)
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                b = SBRICK if corner else BRICK
                if yy in (y + 8, y + 14):
                    b = SBRICK_CHIS if corner else SBRICK
                if (yy - y) % 6 in (3, 4) and not corner and (x == x0 + 3 or z == z0 + 3):
                    b = IRON_BARS
                g.set(x, yy, z, b)
    # corniches
    for yy in (y + 8, y + 14):
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                    g.set(x, yy, z, SLAB_SB_TOP)
    # porte cote est (vers la place)
    for yy in (y + 1, y + 2, y + 3):
        g.set(x1, yy, z0 + 3, AIR)
    g.set(x1, y + 4, z0 + 3, ST_SBRICK, 5)
    # cadrans (centre a y + 18), un pas devant chaque face
    clock_face(g, x1 + 1, 0, y + 18, z0 + 3, "x")
    clock_face(g, x0 - 1, 0, y + 18, z0 + 3, "x")
    clock_face(g, z1 + 1, x0 + 3, y + 18, 0, "z")
    clock_face(g, z0 - 1, x0 + 3, y + 18, 0, "z")
    # echelle interieure contre le mur ouest
    for yy in range(y + 1, wall_top + 1):
        g.set(x0 + 1, yy, z0 + 2, 65, 5)
    # beffroi : plancher, piliers d'angle, garde-corps, cloche (bloc d'or) suspendue
    fy = wall_top + 1
    box(g, x0 - 1, x1 + 1, fy, fy, z0 - 1, z1 + 1, SBRICK)
    g.set(x0 + 1, fy, z0 + 2, 65, 5)
    for x in (x0, x1):
        for z in (z0, z1):
            box(g, x, x, fy + 1, fy + 4, z, z, QUARTZ_PILLAR)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if (x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)):
                g.put_air(x, fy + 1, z, IRON_BARS)
    g.set(x0 + 3, fy + 4, z0 + 3, IRON_BARS)
    g.set(x0 + 3, fy + 3, z0 + 3, GOLD)
    # toit en pyramide de cuivre oxyde, fleche doree
    ry = fy + 5
    for k in range(5):
        for x in range(x0 - 1 + k, x1 + 2 - k):
            for z in range(z0 - 1 + k, z1 + 2 - k):
                edge = x in (x0 - 1 + k, x1 + 1 - k) or z in (z0 - 1 + k, z1 + 1 - k)
                g.set(x, ry + k, z, VERDI_DARK if edge else VERDIGRIS)
    for k in range(5, 9):
        g.set(x0 + 3, ry + k, z0 + 3, GOLD if k < 7 else IRON_BARS)
    return ry + 9


def gear(g, cx, cy, cz, r, axis, rim=IRON, spoke=DO_LOG, hub=GOLD, teeth=8):
    """Engrenage dans un plan vertical. axis 'x' : plan x = cx."""
    def put(a, b, blk):
        if axis == "x":
            g.set(cx, cy + b, cz + a, blk)
        else:
            g.set(cx + a, cy + b, cz, blk)
    R = r + 2
    for a in range(-R, R + 1):
        for b in range(-R, R + 1):
            d = math.hypot(a, b)
            ang = math.atan2(b, a)
            if r - 1.0 <= d <= r + 0.4:
                put(a, b, rim)
            elif r + 0.4 < d <= r + 1.5:
                ph = (ang / (2 * math.pi) * teeth) % 1
                if ph < 0.4:
                    put(a, b, rim)
            elif d < r - 1 and (abs(a) <= 0 or abs(b) <= 0 or abs(abs(a) - abs(b)) <= 0) and d > 1.2:
                put(a, b, spoke)
    for a in range(-1, 2):
        for b in range(-1, 2):
            put(a, b, hub)


def factory(g, x0, x1, z0, z1, y, rnd):
    """Usine : murs de brique a pilastres, grandes verrieres, toit en dents de scie (vitrage au nord),
    trois cheminees, chaudiere, fours, passerelle, grand engrenage sur la facade est."""
    hgt = 8
    for yy in range(y + 1, y + hgt + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x0 < x < x1 and z0 < z < z1:
                    g.set(x, yy, z, AIR)
                    continue
                pil = (x - x0) % 4 == 0 if z in (z0, z1) else (z - z0) % 4 == 0
                b = SBRICK if pil else BRICK
                if yy == y + hgt:
                    b = SBRICK
                if not pil and 3 <= yy - y <= 6 and ((z in (z0, z1) and 0 < x - x0 < x1 - x0) or
                                                     (x in (x0, x1) and 0 < z - z0 < z1 - z0)):
                    b = IRON_BARS if yy - y == 3 else GLASS_PANE
                g.set(x, yy, z, b)
    # toit en dents de scie : pente qui monte vers l'est, vitrage vertical, sur toute la profondeur
    ry = y + hgt + 1
    xs = x0
    while xs + 4 <= x1:
        for z in range(z0, z1 + 1):
            for k in range(4):
                g.set(xs + k, ry + k, z, ST_BRICK, 0)
                for yy in range(ry, ry + k):
                    g.set(xs + k, yy, z, BRICK if z in (z0, z1) else AIR)
            for yy in range(ry, ry + 4):
                g.set(xs + 4, yy, z, BRICK if z in (z0, z1) else GLASS_PANE)
            g.set(xs + 4, ry + 4, z, SLAB_SB)
        xs += 4
    for z in range(z0, z1 + 1):  # bord est
        for yy in range(ry, ry + 1):
            g.set(x1, yy, z, SBRICK)
    # portes : deux grands portails au sud, un a l'est
    for px in (x0 + 6, x1 - 6):
        clear(g, px - 1, px + 1, y + 1, y + 4, z1, z1)
        for xx in range(px - 2, px + 3):
            g.set(xx, y + 5, z1, SBRICK_CHIS)
    clear(g, x1, x1, y + 1, y + 4, (z0 + z1) // 2 - 1, (z0 + z1) // 2 + 1)
    # chaudiere couchee (cylindre de fer) au fond
    bz = z0 + 4
    for x in range(x0 + 3, x0 + 13):
        for a in range(-2, 3):
            for b in range(-2, 3):
                if a * a + b * b <= 5:
                    g.set(x, y + 3 + b, bz + a, COPPER if x in (x0 + 3, x0 + 12) or (x - x0) % 3 == 0 else IRON)
    for x in range(x0 + 4, x0 + 12, 3):
        g.set(x, y + 1, bz, NBRICK)
    # fours et enclumes le long du mur sud, chaudrons
    for x in range(x0 + 2, x1 - 1, 3):
        if abs(x - (x0 + 6)) > 1 and abs(x - (x1 - 6)) > 1:
            g.set(x, y + 1, z1 - 1, 61, 2)
            g.set(x, y + 2, z1 - 1, HOPPER)
    g.set(x1 - 3, y + 1, z0 + 2, ANVIL)
    g.set(x1 - 4, y + 1, z0 + 2, CAULDRON)
    g.set(x1 - 2, y + 1, z0 + 2, BREWING)
    # passerelle interieure le long du mur nord, avec echelle
    for x in range(x0 + 1, x1):
        for zz in (z0 + 1, z0 + 2, z0 + 3):
            if g.air(x, y + 5, zz):
                g.set(x, y + 5, zz, SLAB_SB_TOP)
        if x < x1 - 1:
            g.put_air(x, y + 6, z0 + 4, IRON_BARS)
    for yy in range(y + 1, y + 7):
        g.set(x1 - 1, yy, z0 + 4, 65, 4)
    # cheminees 3 x 3 qui traversent le toit
    tops = []
    for cx in (x0 + 3, x0 + 11, x0 + 19):
        if cx + 2 >= x1:
            continue
        top = ry + 14 + rnd.randint(0, 4)
        for yy in range(y + 1, top + 1):
            for a in range(3):
                for b in range(3):
                    blk = BRICK if (yy - y) % 7 else SBRICK
                    if yy > top - 2:
                        blk = NBRICK
                    g.set(cx + a, yy, z0 + 1 + b, blk)
            if yy > y + 1:
                g.set(cx + 1, yy, z0 + 2, AIR)
        for a in range(-1, 4):
            for b in range(-1, 4):
                if a in (-1, 3) or b in (-1, 3):
                    g.set(cx + a, top - 1, z0 + 1 + b, SLAB_BRICK)
        for a in range(3):
            for b in range(3):
                if (a, b) != (1, 1):
                    g.set(cx + a, top + 1, z0 + 1 + b, NB_FENCE)
        tops.append(top)
    # grand engrenage sur la facade est, et un petit qui s'y engrene
    gear(g, x1 + 1, y + 6, (z0 + z1) // 2 - 3, 5, "x")
    gear(g, x1 + 1, y + 10, (z0 + z1) // 2 + 6, 3, "x", rim=COPPER, spoke=DO_LOG, hub=IRON, teeth=6)
    # tuyaux de cuivre sur la facade sud
    pipe(g, [(x0 + 2, y + 1, z1 + 1), (x0 + 2, y + 7, z1 + 1), (x0 + 10, y + 7, z1 + 1), (x0 + 10, y + 9, z1 + 1)])
    pipe(g, [(x1 - 2, y + 1, z1 + 1), (x1 - 2, y + 6, z1 + 1)])
    g.set(x0 + (x1 - x0) // 2, y + 4, z1 - 1, GLOW)
    g.set(x0 + 6, y + 4, z0 + 6, GLOW)
    g.set(x1 - 6, y + 4, z0 + 6, GLOW)


def airship(g, x0, x1, zc, y, balloon_dy=7, rz=5, ry=4, along="x", rnd=None):
    """Dirigeable : nacelle-bateau en epicea (proue vers +x), cabine, ballon rayé brun/orange, cordages, helice."""
    xc = (x0 + x1) // 2
    hl = (x1 - x0) // 2
    # coque : largeur 5 au milieu, effilee aux bouts
    for x in range(x0, x1 + 1):
        t = abs(x - xc) / hl
        half = 2 if t < 0.6 else 1 if t < 0.9 else 0
        for z in range(zc - half, zc + half + 1):
            g.set(x, y, z, SPRUCE)
            if t < 0.95:
                g.set(x, y - 1, z, DO_LOG_X if abs(z - zc) < half or half == 0 else DARKOAK)
        for z in (zc - half - 1, zc + half + 1):
            g.set(x, y, z, DARKOAK)
            g.set(x, y + 1, z, DO_FENCE)
    g.set(x1 + 1, y, zc, DARKOAK)
    g.set(x1 + 2, y + 1, zc, DO_FENCE)
    g.set(x0 - 1, y + 1, zc, DARKOAK)
    # cabine
    for x in range(xc - 3, xc + 2):
        for z in range(zc - 2, zc + 3):
            for yy in range(y + 1, y + 4):
                edge = x in (xc - 3, xc + 1) or z in (zc - 2, zc + 2)
                g.set(x, yy, z, (GLASS_PANE if yy == y + 2 and not (x in (xc - 3, xc + 1) and z in (zc - 2, zc + 2))
                                 else SPRUCE) if edge else AIR)
    g.set(xc + 1, y + 1, zc, AIR)
    g.set(xc + 1, y + 2, zc, AIR)
    box(g, xc - 4, xc + 2, y + 4, y + 4, zc - 3, zc + 3, SLAB_DO)
    # ballon
    by = y + balloon_dy
    rx = hl + 2
    for x in range(xc - rx, xc + rx + 1):
        for yy in range(by - ry, by + ry + 1):
            for z in range(zc - rz, zc + rz + 1):
                d = ((x - xc) / rx) ** 2 + ((yy - by) / ry) ** 2 + ((z - zc) / rz) ** 2
                if d <= 1.0:
                    shell = d > 0.62
                    if shell:
                        stripe = (x - xc) % 6 in (0, 1)
                        blk = WOOL_ORANGE if stripe else WOOL_BROWN
                        if yy == by and abs(z - zc) >= rz - 1:
                            blk = BRONZE  # ceinture
                        g.set(x, yy, z, blk)
    # gouvernail et helice a l'arriere
    tx = xc - rx - 1
    for yy in range(by - 2, by + 3):
        g.set(tx, yy, zc, DARKOAK)
    for z in range(zc - 2, zc + 3):
        g.set(tx, by, z, DARKOAK)
    g.set(tx - 1, by, zc, IRON)
    for k in (-2, -1, 1, 2):
        g.set(tx - 2, by + k, zc, SPRUCE)
        g.set(tx - 2, by, zc + k, SPRUCE)
    g.set(tx - 2, by, zc, GOLD)
    # cordages (barreaux) de la coque au ballon
    for x in (x0 + 2, x1 - 2):
        for z in (zc - 2, zc + 2):
            for yy in range(y + 1, by - ry + 1):
                if g.air(x, yy, z):
                    g.set(x, yy, z, DO_FENCE)


# ---------------------------------------------------------------------------- petits decors


def water_tower(g, cx, cz, y):
    for a in (-2, 2):
        for b in (-2, 2):
            for yy in range(y + 1, y + 9):
                g.set(cx + a, yy, cz + b, DO_LOG)
    for yy in (y + 4,):
        for a in range(-2, 3):
            for b in range(-2, 3):
                if abs(a) == 2 or abs(b) == 2:
                    g.put_air(cx + a, yy, cz + b, DO_FENCE)
    for yy in range(y + 9, y + 15):
        for a in range(-3, 4):
            for b in range(-3, 4):
                d = math.hypot(a, b)
                if d <= 3.3:
                    inner = d < 2.3 and y + 9 < yy
                    hoop = yy in (y + 10, y + 13) and not inner
                    g.set(cx + a, yy, cz + b, AIR if inner else IRON if hoop else SPRUCE)
    for a in range(-1, 2):
        for b in range(-1, 2):
            if y + 10 <= y + 13:
                g.set(cx + a, y + 13, cz + b, WATER)
    for k in range(3):
        for a in range(-3 + k, 4 - k):
            for b in range(-3 + k, 4 - k):
                if math.hypot(a, b) <= 3.3 - k:
                    g.set(cx + a, y + 15 + k, cz + b, VERDI_DARK if k < 2 else VERDIGRIS)
    g.set(cx, y + 18, cz, GOLD)
    for yy in range(y + 1, y + 15):
        g.set(cx + 3, yy, cz - 2, 65, 5)  # echelle contre le pied est... cote exterieur
    g.set(cx + 2, y + 14, cz - 2, SPRUCE)


def wind_turbine(g, cx, cz, y):
    """Eolienne : tour de pierre taillee, nacelle de cuivre, quatre pales de laine dans le plan x = cx - 2."""
    for yy in range(y + 1, y + 14):
        for a in range(-1, 2):
            for b in range(-1, 2):
                edge = abs(a) == 1 or abs(b) == 1
                g.set(cx + a, yy, cz + b, (SBRICK if (yy - y) % 5 else SBRICK_CHIS) if edge else AIR)
    for yy in range(y + 1, y + 14):
        g.set(cx, yy, cz, 65, 4)
    g.set(cx, y + 1, cz + 1, AIR)
    g.set(cx, y + 2, cz + 1, AIR)
    box(g, cx - 2, cx + 2, y + 14, y + 16, cz - 2, cz + 2, COPPER)
    box(g, cx - 1, cx + 1, y + 15, y + 16, cz - 1, cz + 1, AIR)
    g.set(cx, y + 14, cz, AIR)
    box(g, cx - 2, cx + 2, y + 17, y + 17, cz - 2, cz + 2, VERDI_DARK)
    hx = cx - 3
    g.set(hx, y + 15, cz, GOLD)
    for k in range(1, 8):
        w = 1 if k < 6 else 0
        for s in range(0, w + 1):
            g.set(hx, y + 15 + k, cz - s, WOOL_WHITE)
            g.set(hx, y + 15 - k, cz + s, WOOL_WHITE)
            g.set(hx, y + 15 + s, cz + k, WOOL_WHITE)
            g.set(hx, y + 15 - s, cz - k, WOOL_WHITE)
        g.set(hx, y + 15 + k, cz + 1 if k > 1 else cz, DO_FENCE if k == 7 else WOOL_WHITE)


def pressure_tank(g, cx, cz, y):
    """Reservoir sous pression : cylindre de fer cercle de cuivre, manometres, soupapes."""
    for yy in range(y + 1, y + 10):
        for a in range(-3, 4):
            for b in range(-3, 4):
                d = math.hypot(a, b)
                if d <= 3.2:
                    if d >= 2.2:
                        blk = COPPER if (yy - y) % 4 == 1 else IRON
                        g.set(cx + a, yy, cz + b, blk)
                    else:
                        g.set(cx + a, yy, cz + b, AIR)
    for a in range(-2, 3):
        for b in range(-2, 3):
            if math.hypot(a, b) <= 2.5:
                g.set(cx + a, y + 10, cz + b, IRON)
    box(g, cx - 1, cx + 1, y + 11, y + 11, cz - 1, cz + 1, COPPER)
    g.set(cx, y + 12, cz, GOLD)
    g.set(cx, y + 13, cz, IRON_BARS)
    g.set(cx, y + 5, cz - 4, (151, 0))  # manometre (capteur de lumiere)
    g.set(cx, y + 5, cz + 4, (151, 0))
    for yy in (y + 1, y + 2):
        g.set(cx + 3, yy, cz, AIR)
        g.set(cx + 2, yy, cz, AIR)
    g.set(cx, y + 1, cz, GLOW)


def radio_tower(g, cx, cz, y):
    """Tour de telegraphe en treillis (barreaux), plates-formes, boule de cuivre en haut."""
    for yy in range(y + 1, y + 20):
        w = 2 if yy < y + 8 else 1
        for a in (-w, w):
            for b in (-w, w):
                g.set(cx + a, yy, cz + b, IRON_BARS)
        if yy in (y + 7, y + 14):
            box(g, cx - w - 1, cx + w + 1, yy, yy, cz - w - 1, cz + w + 1, SLAB_SB_TOP)
            for a in range(-w - 1, w + 2):
                for b in range(-w - 1, w + 2):
                    if abs(a) == w + 1 or abs(b) == w + 1:
                        g.set(cx + a, yy + 1, cz + b, IRON_BARS)
    for yy in range(y + 1, y + 19):
        g.set(cx, yy, cz, 65, 3)
        g.set(cx, yy, cz - 1, IRON)
    for a in range(-1, 2):
        for b in range(-1, 2):
            for c in range(-1, 2):
                if abs(a) + abs(b) + abs(c) <= 1:
                    g.set(cx + a, y + 21 + b, cz + c, COPPER)
    g.set(cx, y + 20, cz, IRON)
    g.set(cx, y + 23, cz, REDSTONE_BLOCK)


def crane(g, x, z, y, arm_dz=1, length=8, height=11):
    for yy in range(y + 1, y + height + 1):
        g.set(x, yy, z, DO_LOG)
    for k in range(-2, length + 1):
        g.set(x, y + height + 1, z + k * arm_dz, DO_LOG_Z)
    g.set(x, y + height + 2, z, DARKOAK)
    box(g, x - 1, x + 1, y + height + 1, y + height + 1, z - 3 * arm_dz, z - 3 * arm_dz, IRON)  # contrepoids
    hz = z + (length - 1) * arm_dz
    for yy in range(y + height - 4, y + height + 1):
        g.set(x, yy, hz, IRON_BARS)
    g.set(x, y + height - 5, hz, ANVIL)


def crates(g, x, z, y, rnd):
    for a in range(2):
        for b in range(2):
            h = rnd.randint(1, 2)
            for k in range(h):
                g.put_air(x + a, y + 1 + k, z + b, SP_LOG if (a + b + k) % 2 else SPRUCE)


def steam_engine(g, x, z, y):
    """Machine a vapeur sur la promenade : volant, piston, chaudron."""
    box(g, x, x + 2, y + 1, y + 1, z, z + 4, SBRICK)
    box(g, x, x + 2, y + 2, y + 3, z, z + 1, COPPER)
    g.set(x + 1, y + 4, z, IRON_BARS)
    g.set(x + 1, y + 5, z, IRON_BARS)
    g.set(x + 1, y + 2, z + 3, (33, 4))
    g.set(x + 1, y + 2, z + 4, IRON)
    gear(g, x + 1, y + 4, z + 4, 2, "x", rim=IRON, spoke=IRON_BARS, hub=GOLD, teeth=5)
    g.set(x + 2, y + 2, z + 2, CAULDRON)


# ---------------------------------------------------------------------------- camp rouge


def camp(g, rnd):
    sx, sz = 30, ZC  # place de depart

    # --- quartier de la place (G)
    A = rrect_mask(13, RED_MAX - 1, 35, 77, 6, 11)

    def deck_a(x, z):
        if abs(x - sx) <= 7 and abs(z - sz) <= 7:
            ring = max(abs(x - sx), abs(z - sz))
            if ring == 7:
                return SBRICK_CHIS if (x + z) % 2 == 0 else SBRICK
            if ring == 0:
                return GOLD
            if ring in (2, 5):
                return COPPER
            return POL_ANDESITE if (x + z) % 2 == 0 else ANDESITE
        r = (x * 31 + z * 17) % 11
        return SBRICK_CRACK if r == 0 else SBRICK_MOSS if r == 1 else COBBLE if r < 4 else SBRICK

    platform(g, A, G, deck_a, 101)

    # --- usine (G + 5)
    B = rrect_mask(4, RED_MAX - 9, 3, 29, 5, 22)
    platform(g, B, G + 5, lambda x, z: SBRICK if (x // 3 + z // 3) % 2 else STONE, 202, max_depth=21)

    # --- quai des dirigeables (G - 3)
    C = rrect_mask(8, RED_MAX - 9, 81, 108, 5, 33)

    def deck_c(x, z):
        return DO_LOG_X if z % 5 == 0 else SPRUCE

    platform(g, C, G - 3, deck_c, 303, max_depth=15)

    # --- ilots : ouest (chateau d'eau, eolienne), pres du vide (reservoir, tour radio)
    I1 = disc_mask(5, 43, 4.6, 404)
    I2 = disc_mask(5, 69, 4.6, 505)
    I3 = disc_mask(RED_MAX - 3, 17, 4.4, 606)
    I4 = disc_mask(RED_MAX - 3, 95, 4.4, 707)
    for cells, top, seed in ((I1, G + 2, 404), (I2, G + 2, 505), (I3, G + 5, 606), (I4, G - 3, 707)):
        platform(g, cells, top, lambda x, z: SBRICK if (x + z) % 3 else POL_ANDESITE, seed, depth_k=1.6, max_depth=11)

    # --- place de depart : rien au-dessus (15 x 15, 6 de haut)
    clear(g, sx - 7, sx + 7, G + 1, G + 7, sz - 7, sz + 7)
    for x, z in ((sx - 7, sz - 7), (sx + 7, sz - 7), (sx - 7, sz + 7), (sx + 7, sz + 7)):
        lamp(g, x, G + 1, z, tall=4)

    # --- tour de l'horloge (ouest de la place)
    clock_tower(g, 14, sz - 3, G)

    # --- maisons au nord et au sud de la place (portes vers la place)
    townhouse(g, 21, 29, 37, 44, G, rnd, door_south=True, floors=2)
    townhouse(g, 32, 40, 38, 45, G, rnd, door_south=True, floors=3, wall_low=SBRICK)
    townhouse(g, 21, 29, 68, 75, G, rnd, door_south=False, floors=3, wall_low=SBRICK)
    townhouse(g, 32, 40, 67, 74, G, rnd, door_south=False, floors=2)
    # passage couvert de tuyaux entre les maisons du sud et du nord ? non : rue degagee
    # lampadaires des rues
    for x, z in ((20, 47), (41, 47), (20, 65), (41, 65), (44, 56), (16, 46), (16, 66)):
        lamp(g, x, G + 1, z)

    # --- promenade est (face au vide) : garde-corps, machine a vapeur, bancs
    rail_edge(g, A, G, DO_FENCE)
    steam_engine(g, 43, 49, G)
    steam_engine(g, 43, 59, G)
    for z in (52, 53, 60, 61):
        pass
    # garde-corps : ouvertures vers les ponts et les escaliers
    for x, z, w in ((10, 43, 1), (10, 69, 1)):
        pass

    # --- escalier place -> usine (z 30..34, monte vers le nord)
    for k, z in enumerate(range(34, 29, -1)):
        for x in range(24, 31):
            g.set(x, G + 1 + k, z, ST_SBRICK, 3)
            for yy in range(G - 3, G + 1 + k):
                g.set(x, yy, z, SBRICK)
            for yy in range(G + 2 + k, G + 8):
                g.set(x, yy, z, AIR)
        g.set(23, G + 2 + k, z, IRON_BARS)
        g.set(31, G + 2 + k, z, IRON_BARS)
    for x in range(24, 31):  # ouvre le garde-corps en haut et en bas
        for z in (34, 35, 29, 28):
            for yy in (G + 1, G + 6):
                if g.get(x, yy, z) == DO_FENCE[0]:
                    g.set(x, yy, z, AIR)

    # --- escalier place -> quai (z 78..80, descend vers le sud)
    for k, z in enumerate(range(78, 81)):
        for x in range(25, 32):
            g.set(x, G - k, z, ST_SBRICK, 3)
            for yy in range(G - 8, G - k):
                g.set(x, yy, z, SBRICK)
            for yy in range(G - k + 1, G + 4):
                g.set(x, yy, z, AIR)
    for x in range(25, 32):
        for z in (76, 77, 81, 82):
            for yy in (G + 1, G - 2):
                if g.get(x, yy, z) == DO_FENCE[0]:
                    g.set(x, yy, z, AIR)

    # --- usine
    factory(g, 8, 36, 7, 24, G + 5, rnd)
    rail_edge(g, B, G + 5, COBWALL)
    for x in range(24, 31):
        for z in (28, 29, 30):
            if g.get(x, G + 6, z) == COBWALL[0]:
                g.set(x, G + 6, z, AIR)
    # cour de l'usine : caisses, rails de wagonnets (sur pierre), lampadaires
    crates(g, 6, 26, G + 5, rnd)
    crates(g, 38, 6, G + 5, rnd)
    lamp(g, 20, G + 6, 27)
    lamp(g, 33, G + 6, 27)

    # --- quai : dirigeable amarre, grue, caisses, mat d'amarrage
    rail_edge(g, C, G - 3, SP_FENCE)
    airship(g, 14, 36, 100, G - 2, balloon_dy=11, rz=5, ry=5)
    crane(g, 11, 86, G - 3, arm_dz=1, length=8, height=12)
    crates(g, 38, 84, G - 3, rnd)
    crates(g, 16, 84, G - 3, rnd)
    crates(g, 40, 104, G - 3, rnd)
    for x in (20, 34):
        lamp(g, x, G - 2, 88)
    # ouvre le garde-corps du quai le long de la coque (embarquement)
    for x in range(14, 37):
        for z in range(94, 107):
            if g.get(x, G - 2, z) == SP_FENCE[0] and abs(z - 100) <= 3:
                g.set(x, G - 2, z, AIR)

    # --- ilots
    water_tower(g, 5, 43, G + 2)
    wind_turbine(g, 5, 69, G + 2)
    pressure_tank(g, RED_MAX - 3, 17, G + 5)
    radio_tower(g, RED_MAX - 3, 95, G - 3)

    # --- ponts (passerelles de fer et de bois)
    def bridge_x(z, xa, xb, y, deck=SPRUCE, rail=IRON_BARS):
        for x in range(min(xa, xb), max(xa, xb) + 1):
            for dz in (-1, 0, 1):
                if g.air(x, y, z + dz) or g.get(x, y, z + dz) == DO_FENCE[0]:
                    g.set(x, y, z + dz, deck if dz == 0 else SBRICK)
                for yy in range(y + 1, y + 4):
                    if g.get(x, yy, z + dz) in (DO_FENCE[0], SP_FENCE[0], COBWALL[0]):
                        g.set(x, yy, z + dz, AIR)
            for dz in (-2, 2):
                if g.air(x, y + 1, z + dz):
                    g.set(x, y, z + dz, IRON)
                    g.set(x, y + 1, z + dz, rail)
            if x % 4 == 0:
                g.set(x, y - 1, z, COPPER)

    def bridge_z(x, za, zb, y, deck=SPRUCE, rail=IRON_BARS):
        for z in range(min(za, zb), max(za, zb) + 1):
            for dx in (-1, 0, 1):
                if g.air(x + dx, y, z) or g.get(x + dx, y, z) == DO_FENCE[0]:
                    g.set(x + dx, y, z, deck if dx == 0 else SBRICK)
                for yy in range(y + 1, y + 4):
                    if g.get(x + dx, yy, z) in (DO_FENCE[0], SP_FENCE[0], COBWALL[0]):
                        g.set(x + dx, yy, z, AIR)
            for dx in (-2, 2):
                if g.air(x + dx, y + 1, z):
                    g.set(x + dx, y, z, IRON)
                    g.set(x + dx, y + 1, z, rail)

    # place (G) -> ilots ouest (G + 2) : rampe de 2 marches
    for z in (43, 69):
        bridge_x(z, 9, 15, G + 1)
        for dz in (-1, 0, 1):
            g.set(15, G + 1, z + dz, SLAB_SB)
            g.set(14, G + 1, z + dz, SLAB_SB_TOP)
            g.set(13, G + 1, z + dz, SLAB_SB_TOP)
            g.set(9, G + 2, z + dz, SBRICK)
    # usine -> reservoir (G + 5)
    bridge_x(17, 37, RED_MAX - 6, G + 5)
    # quai -> tour radio (G - 3)
    bridge_x(95, 38, RED_MAX - 6, G - 3)
    # usine -> quai par l'ouest : longue passerelle qui descend en escalier le long du bord ouest ?
    # non : on garde le passage par la place (plus lisible)

    # --- tuyau de cuivre qui court de l'usine a la maison nord-est
    pipe(g, [(37, G + 8, 26), (37, G + 8, 33), (37, G + 9, 33), (37, G + 9, 37)])

    # --- zone de depart : 12 places en grille 3 x 4
    pts = []
    for dz in (-3, -1, 1, 3):
        for dx in (-2, 0, 2):
            pts.append((sx + dx, G, sz + dz))
    pts.sort(key=lambda p: abs(p[0] - sx) + abs(p[2] - sz))
    return pts


def middle(g):
    """Plate-forme centrale en engrenage, bobine Tesla."""
    y = G + 1
    r = 6
    for x in range(MID - r - 2, MID + r + 3):
        for z in range(ZC - r - 2, ZC + r + 3):
            d = math.hypot(x - MID, z - ZC)
            ang = math.atan2(z - ZC, x - MID)
            tooth = (ang / (2 * math.pi) * 12) % 1 < 0.45
            if d <= r or (d <= r + 1.4 and tooth):
                depth = int((1 - min(d, r) / (r + 1)) * 8) + 1
                for yy in range(y - depth, y):
                    g.set(x, yy, z, STONE if yy < y - 2 else SBRICK)
                rim = d > r - 1.2
                g.set(x, y, z, IRON if rim else (COPPER if abs(d - 3.5) < 0.6 else POL_ANDESITE))
    # bobine Tesla
    box(g, MID - 1, MID + 1, y + 1, y + 1, ZC - 1, ZC + 1, SBRICK_CHIS)
    for yy in range(y + 2, y + 8):
        g.set(MID, yy, ZC, IRON_BARS if yy % 2 else IRON)
    for yy in (y + 3, y + 6):
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            g.set(MID + a, yy, ZC + b, COPPER)
    g.set(MID, y + 8, ZC, SEA_LANTERN)
    for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        g.set(MID + a, y + 8, ZC + b, IRON_BARS)
    for a, b in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
        lamp(g, MID + a, y + 1, ZC + b, tall=2)


def big_airship(g):
    """Grand dirigeable au-dessus du vide central, le long de z (symetrique en x)."""
    by = G + 27
    rx, ry, rz = 6, 5, 22
    for x in range(MID - rx, MID + rx + 1):
        for yy in range(by - ry, by + ry + 1):
            for z in range(ZC - rz, ZC + rz + 1):
                d = ((x - MID) / rx) ** 2 + ((yy - by) / ry) ** 2 + ((z - ZC) / rz) ** 2
                if 0.6 < d <= 1.0:
                    stripe = (z - ZC) % 7 in (0, 1)
                    blk = WOOL_ORANGE if stripe else WOOL_BROWN
                    if yy == by and abs(x - MID) >= rx - 1:
                        blk = BRONZE
                    g.set(x, yy, z, blk)
    # nacelle
    ny = by - ry - 4
    for z in range(ZC - 9, ZC + 10):
        t = abs(z - ZC) / 9
        half = 2 if t < 0.7 else 1
        for x in range(MID - half, MID + half + 1):
            g.set(x, ny, z, SPRUCE)
        for x in (MID - half - 1, MID + half + 1):
            g.set(x, ny, z, DARKOAK)
            g.set(x, ny + 1, z, DO_FENCE)
    for z in (ZC - 7, ZC - 2, ZC + 2, ZC + 7):
        for x in (MID - 2, MID + 2):
            for yy in range(ny + 1, by - ry + 2):
                if g.air(x, yy, z):
                    g.set(x, yy, z, DO_FENCE)
    g.set(MID, ny - 1, ZC, SEA_LANTERN)
    # empennage et helice (sud)
    tz = ZC + rz + 1
    for k in range(-3, 4):
        g.set(MID, by + k, tz, DARKOAK)
        g.set(MID + k, by, tz, DARKOAK)
    g.set(MID, by, tz + 1, IRON)
    for k in (-2, -1, 1, 2):
        g.set(MID + k, by + k, tz + 2, SPRUCE)
        g.set(MID + k, by - k, tz + 2, SPRUCE)
    g.set(MID, by, tz + 2, GOLD)
    # nez dore (nord)
    g.set(MID, by, ZC - rz - 1, GOLD)


def build():
    g = Grid()
    rnd = random.Random(2026)
    red = camp(g, rnd)
    mirror_camp(g)
    middle(g)
    big_airship(g)
    blue = [(2 * MID - x, y, z) for x, y, z in red]
    return g, red, blue


# ---------------------------------------------------------------------------- apercus

PAL = {
    1: (125, 125, 125), 4: (110, 110, 110), 98: (120, 120, 118), 45: (150, 75, 60), 112: (60, 30, 35),
    5: (75, 55, 35), 17: (60, 45, 30), 162: (60, 45, 30), 42: (215, 215, 215), 41: (245, 205, 60),
    173: (20, 20, 20), 152: (180, 20, 10), 159: (160, 85, 40), 168: (80, 150, 135), 155: (235, 230, 225),
    102: (200, 220, 230), 101: (140, 140, 140), 89: (250, 220, 130), 169: (190, 230, 230), 191: (60, 45, 30),
    188: (80, 60, 40), 113: (60, 30, 35), 139: (110, 110, 110), 118: (60, 60, 60), 154: (70, 70, 70),
    145: (70, 70, 70), 117: (120, 110, 90), 47: (110, 80, 50), 58: (120, 90, 55), 35: (120, 80, 50),
    16: (90, 90, 90), 15: (140, 125, 115), 9: (60, 90, 200), 44: (160, 160, 160), 126: (75, 55, 35),
    108: (150, 75, 60), 109: (120, 120, 118), 164: (60, 45, 30), 134: (80, 60, 40), 114: (60, 30, 35),
    65: (130, 100, 60), 61: (100, 100, 100), 151: (180, 170, 140), 33: (130, 120, 100),
}
DATA_PAL = {(35, 1): (230, 120, 30), (35, 12): (110, 70, 40), (35, 0): (240, 240, 240), (159, 12): (80, 55, 35),
            (159, 1): (165, 85, 40), (1, 5): (135, 135, 130), (1, 6): (140, 140, 138), (168, 2): (50, 90, 80),
            (168, 1): (90, 160, 145), (98, 3): (110, 110, 110), (5, 1): (95, 70, 45)}


def color(b, d):
    return DATA_PAL.get((b, d), PAL.get(b, (200, 120, 200)))


def preview_top(g, path):
    from PIL import Image
    img = Image.new("RGB", (W, L), (150, 190, 235))
    for x in range(W):
        for z in range(L):
            for y in range(H - 1, -1, -1):
                i = g.idx(x, y, z)
                if g.ids[i]:
                    c = color(g.ids[i], g.data[i])
                    s = 0.55 + 0.45 * y / H
                    img.putpixel((x, z), tuple(int(v * s) for v in c))
                    break
    img.resize((W * 5, L * 5), Image.NEAREST).save(path)


def preview_iso(g, path, flip=False, s=6):
    """Vue isometrique (camera au sud-est, ou au sud-ouest si flip)."""
    from PIL import Image, ImageDraw
    ow = (W + L) * s + 20
    oh = (W + L) * s // 2 + H * s + 20
    img = Image.new("RGB", (ow, oh), (160, 200, 240))
    dr = ImageDraw.Draw(img)

    def X(x):
        return (W - 1 - x) if flip else x

    cells = []
    for y in range(H):
        for z in range(L):
            for x in range(W):
                i = g.idx(x, y, z)
                if g.ids[i]:
                    cells.append((X(x), y, z, g.ids[i], g.data[i]))

    def solid(x, y, z):
        xx = (W - 1 - x) if flip else x
        return g.ok(xx, y, z) and g.ids[g.idx(xx, y, z)] not in (0,)

    cells.sort(key=lambda c: (c[0] + c[2], c[1]))
    for x, y, z, b, d in cells:
        top, east, south = not solid(x, y + 1, z), not solid(x + 1, y, z), not solid(x, y, z + 1)
        if not (top or east or south):
            continue
        cx = (x - z) * s + L * s + 10
        cy = (x + z) * s // 2 - y * s + H * s + 10
        c = color(b, d)
        t = [(cx, cy - s // 2), (cx + s, cy), (cx, cy + s // 2), (cx - s, cy)]
        if top:
            dr.polygon(t, fill=c)
        if south:
            dr.polygon([(cx - s, cy), (cx, cy + s // 2), (cx, cy + s // 2 + s), (cx - s, cy + s)],
                       fill=tuple(int(v * 0.72) for v in c))
        if east:
            dr.polygon([(cx, cy + s // 2), (cx + s, cy), (cx + s, cy + s), (cx, cy + s // 2 + s)],
                       fill=tuple(int(v * 0.85) for v in c))
    img.save(path)


def main():
    out = sys.argv[1]
    prev = sys.argv[2] if len(sys.argv) > 2 else None
    g, red, blue = build()
    g.write(f"{out}/engrenages.swm")
    if prev:
        preview_top(g, f"{prev}/apercu_engrenages_dessus.png")
        preview_iso(g, f"{prev}/apercu_engrenages_iso.png")
        preview_iso(g, f"{prev}/apercu_engrenages_iso2.png", flip=True)
    fmt = lambda pts: ", ".join("{%d, %d, %d}" % p for p in pts)
    n = sum(1 for b in g.ids if b)
    used = sorted({(g.ids[i], g.data[i]) for i in range(len(g.ids)) if g.ids[i]})
    print(f"// engrenages : {W}x{H}x{L}, {n} blocs, {len(used)} etats : {used}")
    print(f'IMPORTED.put("engrenages", new Imported("Cite des engrenages", 0, 0, 0, {MID}, {ZC}, false, -3, {G + 8},\n'
          f'        new int[][]{{{fmt(red)}}},\n        new int[][]{{{fmt(blue)}}}));')


if __name__ == "__main__":
    main()
