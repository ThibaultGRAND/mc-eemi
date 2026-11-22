"""Génère deux maps Sheep Wars au format SWM1 (ids de blocs 1.8, lus par EemiSheepWars via fromLegacy).

Même plan que les maps importées (Îles vertes, Désert rose) mais plus grandes : par camp, une île de départ
au centre, une île à l'avant et à l'arrière reliées par des ponts, deux îlots extérieurs, deux îlots près du
vide central ; un îlot au milieu. Rouge à l'ouest (x < mid), Bleu = miroir.

Usage : python gen_maps.py <dossier des ressources maps> [dossier des aperçus]
Écrit <id>.swm et affiche la ligne Java Imported(...) à copier dans EemiSheepWars.
"""
import gzip
import math
import random
import struct
import sys

W, H, L = 93, 52, 113
MID, ZC = W // 2, L // 2
G = 22  # niveau du sol des îles (relatif au bas de la map)


def noise2(seed):
    """Bruit lisse 2D (valeurs interpolées sur une grille de 6)."""
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

    def set(self, x, y, z, b, d=0):
        if self.ok(x, y, z):
            i = self.idx(x, y, z)
            self.ids[i] = b
            self.data[i] = d

    def get(self, x, y, z):
        return self.ids[self.idx(x, y, z)] if self.ok(x, y, z) else 0

    def air(self, x, y, z):
        return self.get(x, y, z) == 0

    def write(self, path):
        out = bytearray(b"SWM1") + struct.pack(">hhh", W, H, L)
        for i in range(W * H * L):
            out.append(self.ids[i])
            out.append(self.data[i])
        with gzip.open(path, "wb") as f:
            f.write(bytes(out))


# orientation 1.8 : escaliers 0 = monte vers l'est, 1 = ouest, 2 = sud, 3 = nord (+4 à l'envers)
# échelle : 2 = face au nord (mur au sud), 3 = sud, 4 = ouest, 5 = est ; torche : 1 est, 2 ouest, 3 sud, 4 nord, 5 debout
STAIRS = {53, 67, 108, 109, 114, 128, 134, 135, 136, 156, 163, 164, 180}


def mirror_data(b, d):
    """Données d'un bloc dont l'ouest et l'est sont échangés (miroir en x)."""
    if b in STAIRS:
        base, top = d & 3, d & 4
        return {0: 1, 1: 0}.get(base, base) | top
    if b == 65:
        return {4: 5, 5: 4}.get(d, d)
    if b == 50:
        return {1: 2, 2: 1}.get(d, d)
    if b == 17 or b == 162:  # bûche couchée x/z : rien à faire en miroir
        return d
    return d


def mirror_camp(g):
    """Recopie le camp rouge (x < MID) en miroir dans le camp bleu."""
    for y in range(H):
        for z in range(L):
            for x in range(MID):
                i = g.idx(x, y, z)
                b = g.ids[i]
                if b:
                    g.set(2 * MID - x, y, z, b, mirror_data(b, g.data[i]))


class Theme:
    pass


def island(g, th, cx, cz, r, top_y, seed, flat=None, hills=2.5):
    """Île flottante : dessus légèrement vallonné, dessous en pointe rocheuse. Renvoie {(x,z): y du sol}."""
    n = noise2(seed)
    n2 = noise2(seed + 7)
    surf = {}
    R = int(r * 1.25) + 2
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            if x >= MID - 6:  # vide central
                continue
            dx, dz = x - cx, z - cz
            d = math.hypot(dx, dz)
            edge = r * (0.85 + 0.3 * n(x, z, 5))
            if d > edge:
                continue
            k = d / edge
            h = top_y + int(round(hills * n2(x, z, 7) * (1 - k * k)))
            if flat and abs(x - flat[0]) <= flat[2] and abs(z - flat[1]) <= flat[2]:
                h = top_y
            depth = int((1 - k ** 1.6) * (r * 0.95) + 3 * n(x + 40, z + 40, 4)) + 1
            for y in range(h - depth, h + 1):
                if y == h:
                    b, dd = th.top
                elif y >= h - 3:
                    b, dd = th.sub
                else:
                    b, dd = th.rock(x, y, z)
                g.set(x, y, z, b, dd)
            surf[(x, z)] = h
    return surf


def bridge(g, th, x, z0, z1, y, half=1):
    for z in range(min(z0, z1), max(z0, z1) + 1):
        for dx in range(-half, half + 1):
            if g.air(x + dx, y, z):
                g.set(x + dx, y, z, *th.path)
            if g.air(x + dx, y - 1, z) and abs(dx) == half:
                g.set(x + dx, y - 1, z, *th.trim)
        for dx in (-half - 1, half + 1):
            if g.air(x + dx, y + 1, z) and g.air(x + dx, y, z):
                g.set(x + dx, y, z, *th.trim)
                if z % 4 == 0:
                    g.set(x + dx, y + 1, z, *th.rail)


def tree(g, th, x, y, z, rnd, tall=None):
    h = tall or rnd.randint(6, 9)
    if th.pine:
        for k in range(h):
            g.set(x, y + k, z, *th.log)
        for k in range(2, h + 2):
            rr = max(0, (h + 1 - k) // 2)
            if k == h + 1:
                rr = 0
            for dx in range(-rr, rr + 1):
                for dz in range(-rr, rr + 1):
                    if abs(dx) + abs(dz) <= rr + (1 if rr > 1 else 0) and g.air(x + dx, y + k, z + dz):
                        g.set(x + dx, y + k, z + dz, *th.leaves)
        g.set(x, y + h + 1, z, *th.leaves)
    else:
        for k in range(h):
            g.set(x, y + k, z, *th.log)
        for dy in range(-2, 3):
            rr = 3 if abs(dy) <= 1 else 2
            for dx in range(-rr, rr + 1):
                for dz in range(-rr, rr + 1):
                    if dx * dx + dz * dz + dy * dy * 2 <= rr * rr + 1 and g.air(x + dx, y + h + dy, z + dz):
                        g.set(x + dx, y + h + dy, z + dz, *th.leaves)


def decorate(g, th, surf, rnd, avoid, density=0.16, trees=0.035):
    for (x, z), h in surf.items():
        if any(abs(x - ax) <= ar and abs(z - az) <= ar for ax, az, ar in avoid):
            continue
        if not g.air(x, h + 1, z):
            continue
        r = rnd.random()
        if r < trees and all(g.air(x + a, h + 1, z + c) for a in (-2, 0, 2) for c in (-2, 0, 2)):
            tree(g, th, x, h + 1, z, rnd)
        elif r < density:
            b, d = rnd.choice(th.plants)
            g.set(x, h + 1, z, b, d)
        elif r < density + 0.006:
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    if rnd.random() < 0.7:
                        g.set(x + dx, h + 1, z + dz, *th.boulder)
            g.set(x, h + 2, z, *th.boulder)


def roof_x(g, x0, x1, z0, z1, y, stair, full):
    """Toit à deux pans le long de z (pente vers l'ouest et l'est)."""
    k = 0
    while x0 + k <= x1 - k:
        for z in range(z0, z1 + 1):
            if x0 + k == x1 - k:
                g.set(x0 + k, y + k, z, *full)
            else:
                g.set(x0 + k, y + k, z, stair, 0)
                g.set(x1 - k, y + k, z, stair, 1)
        k += 1


def box(g, x0, x1, y0, y1, z0, z1, b, d=0, hollow=False):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                if hollow and x0 < x < x1 and z0 < z < z1:
                    g.set(x, y, z, 0)
                else:
                    g.set(x, y, z, b, d)


def ladder_tower(g, x0, z0, y0, height, wall, floor, top, window):
    """Tour 5x5 creuse, échelle contre le mur sud, plate-forme crénelée au sommet."""
    box(g, x0, x0 + 4, y0, y0 + height, z0, z0 + 4, *wall, hollow=True)
    for y in range(y0 + 3, y0 + height, 4):
        g.set(x0 + 2, y, z0, *window)
        g.set(x0, y, z0 + 2, *window)
    g.set(x0 + 2, y0 + 1, z0 + 4, 0)
    g.set(x0 + 2, y0 + 2, z0 + 4, 0)  # porte
    for y in range(y0 + 1, y0 + height + 1):
        g.set(x0 + 2, y, z0 + 1, 65, 3)  # échelle contre le mur nord, face au sud
    box(g, x0 - 1, x0 + 5, y0 + height + 1, y0 + height + 1, z0 - 1, z0 + 5, *floor)
    g.set(x0 + 2, y0 + height + 1, z0 + 1, 65, 3)
    for x in range(x0 - 1, x0 + 6):
        for z in range(z0 - 1, z0 + 6):
            if (x in (x0 - 1, x0 + 5) or z in (z0 - 1, z0 + 5)) and (x + z) % 2 == 0:
                g.set(x, y0 + height + 2, z, *top)


# ---------------------------------------------------------------------------- thèmes


def theme_glacier():
    th = Theme()
    th.name, th.id = "Pics geles", "glacier"
    th.top, th.sub, th.path, th.trim, th.rail = (80, 0), (3, 0), (98, 0), (98, 2), (139, 0)
    th.log, th.leaves, th.pine = (17, 1), (18, 1), True
    th.boulder = (4, 0)
    th.plants = [(78, 0), (78, 0), (78, 1), (31, 2), (174, 0)]
    rnd = random.Random(11)

    def rock(x, y, z):
        r = rnd.random()
        if r < 0.18:
            return 174, 0  # glace compacte
        if r < 0.3:
            return 1, 5  # andésite
        if r < 0.34:
            return 16, 0
        return 1, 0

    th.rock = rock
    return th


def theme_jade():
    th = Theme()
    th.name, th.id = "Temple de jade", "jade"
    th.top, th.sub, th.path, th.trim, th.rail = (2, 0), (3, 0), (168, 1), (168, 2), (139, 1)
    th.log, th.leaves, th.pine = (17, 3), (18, 3), False
    th.boulder = (48, 0)
    th.plants = [(31, 1), (31, 1), (31, 1), (31, 2), (38, 0), (38, 3), (38, 8), (38, 2), (37, 0)]
    rnd = random.Random(12)

    def rock(x, y, z):
        r = rnd.random()
        if r < 0.2:
            return 48, 0  # pierre moussue
        if r < 0.3:
            return 98, 1
        if r < 0.33:
            return 168, 0
        return 1, 0

    th.rock = rock
    return th


# ---------------------------------------------------------------------------- construction


def camp(g, th, rnd):
    """Camp rouge (ouest). Renvoie les places de départ."""
    sx, sz = MID - 26, ZC  # île de départ
    bx, fx = MID - 23, MID - 23
    bz, fz = ZC - 39, ZC + 39
    spawn = island(g, th, sx, sz, 15, G, 101, flat=(sx, sz, 6))
    back = island(g, th, bx, bz, 12, G + 2, 202, hills=3)
    front = island(g, th, fx, fz, 12, G + 1, 303, hills=3)
    outer1 = island(g, th, MID - 39, ZC - 19, 6, G + 1, 404)
    outer2 = island(g, th, MID - 39, ZC + 19, 6, G + 1, 505)
    inner1 = island(g, th, MID - 11, ZC - 24, 5, G + 3, 606, hills=1)
    inner2 = island(g, th, MID - 11, ZC + 24, 5, G + 3, 707, hills=1)
    surfs = [spawn, back, front, outer1, outer2, inner1, inner2]

    # place de départ
    plaza_r = 6
    for x in range(sx - plaza_r, sx + plaza_r + 1):
        for z in range(sz - plaza_r, sz + plaza_r + 1):
            ring = max(abs(x - sx), abs(z - sz))
            b = th.path if ring < plaza_r else th.trim
            g.set(x, G, z, *b)
            for y in range(G + 1, G + 6):
                g.set(x, y, z, 0)
    g.set(sx, G, sz, *th.light)

    # ponts
    bridge(g, th, sx + 2, sz - 15, bz + 9, G)
    bridge(g, th, sx + 2, sz + 15, fz - 9, G)
    bridge(g, th, MID - 39, ZC - 6, ZC - 14, G + 1, half=0)
    bridge(g, th, MID - 39, ZC + 6, ZC + 14, G + 1, half=0)

    for oz in (ZC - 19, ZC + 19):
        th.build_islet(g, MID - 39, oz)
    th.build_back(g, bx, bz, back)
    th.build_front(g, fx, fz, front)
    th.build_spawn(g, sx, sz)

    for s in surfs:
        decorate(g, th, s, rnd, [(sx, sz, plaza_r + 1), (bx, bz, 7), (fx, fz, 7)])
    # zone de départ : 12 places en grille 3 x 4
    pts = []
    for dz in (-3, -1, 1, 3):
        for dx in (-2, 0, 2):
            pts.append((sx + dx, G, sz + dz))
    pts.sort(key=lambda p: abs(p[0] - sx) + abs(p[2] - sz))
    return pts


def glacier_structures(th):
    def back(g, x, z, surf):  # chalet
        y = G + 2
        box(g, x - 4, x + 4, y - 1, y - 1, z - 5, z + 5, 5, 1)
        box(g, x - 4, x + 4, y, y + 4, z - 5, z + 5, 5, 1, hollow=True)
        for cx in (x - 4, x + 4):
            for cz in (z - 5, z + 5):
                box(g, cx, cx, y, y + 4, cz, cz, 17, 1)
        for zz in (z - 2, z + 2):
            g.set(x - 4, y + 2, zz, 102)
            g.set(x + 4, y + 2, zz, 102)
        g.set(x + 4, y, z, 0)
        g.set(x + 4, y + 1, z, 0)
        roof_x(g, x - 5, x + 5, z - 6, z + 6, y + 5, 134, (5, 1))
        for zz in (z - 5, z + 5):  # pignons
            for k in range(1, 5):
                for xx in range(x - 4 + k, x + 5 - k):
                    g.set(xx, y + 4 + k, zz, 5, 1)
        box(g, x - 2, x - 2, y + 4, y + 10, z + 3, z + 3, 4)
        g.set(x, y + 3, z, 89)
        # étang gelé devant
        for xx in range(x + 5, x + 12):
            for zz in range(z - 4, z + 5):
                if (xx - x - 8) ** 2 + (zz - z) ** 2 <= 12:
                    for yy in range(G + 3, G + 8):
                        g.set(xx, yy, zz, 0)
                    g.set(xx, G + 2, zz, 79)

    def front(g, x, z, surf):  # tour de guet
        ladder_tower(g, x - 2, z - 2, G + 1, 12, (98, 0), (98, 0), (139, 0), (102, 0))
        g.set(x, G + 15, z + 1, 89)

    def spawn(g, x, z):  # arche de glace à l'entrée de la place
        for zz in (z - 7, z + 7):
            for k in range(6):
                g.set(x + 7, G + 1 + k, zz, 174)
        for zz in range(z - 7, z + 8):
            g.set(x + 7, G + 7, zz, 174)
        for zz in (z - 7, z + 7):
            g.set(x + 7, G + 8, zz, 79)

    def islet(g, x, z):  # igloo
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(0, 4):
                    d = dx * dx + dz * dz + dy * dy * 1.6
                    if 7 <= d <= 12:
                        g.set(x + dx, G + 2 + dy, z + dz, 80)
                    elif d < 7:
                        g.set(x + dx, G + 2 + dy, z + dz, 0)
        g.set(x + 3, G + 2, z, 0)
        g.set(x + 3, G + 3, z, 0)
        g.set(x, G + 2, z, 89)

    th.build_back, th.build_front, th.build_spawn, th.build_islet = back, front, spawn, islet
    th.light = (89, 0)


def jade_structures(th):
    def back(g, x, z, surf):  # pagode à trois toits
        y = G + 2
        for tier, (half, hgt) in enumerate(((5, 4), (4, 3), (3, 3))):
            box(g, x - half, x + half, y, y, z - half, z + half, 168, 1)
            for cx in (x - half + 1, x + half - 1):
                for cz in (z - half + 1, z + half - 1):
                    box(g, cx, cx, y + 1, y + hgt, cz, cz, 159, 14)
            box(g, x - half + 2, x + half - 2, y + 1, y + hgt, z - half + 2, z + half - 2, 168, 0, hollow=True)
            g.set(x + half - 2, y + 1, z, 0)
            g.set(x + half - 2, y + 2, z, 0)
            ry = y + hgt + 1
            box(g, x - half - 1, x + half + 1, ry, ry, z - half - 1, z + half + 1, 168, 2)
            for cx in (x - half - 1, x + half + 1):
                for cz in (z - half - 1, z + half + 1):
                    g.set(cx, ry + 1, cz, 44, 5)
            g.set(x, ry - 1, z, 169)
            y = ry
        box(g, x, x, y + 1, y + 3, z, z, 41)
        # échelle intérieure du rez-de-chaussée au sommet
        for yy in range(G + 3, y + 1):
            g.set(x - 1, yy, z, 65, 5)
            g.set(x - 2, yy, z, 168, 1)

    def front(g, x, z, surf):  # torii et bassin aux nénuphars
        for zz in (z - 3, z + 3):
            box(g, x + 4, x + 4, G + 2, G + 7, zz, zz, 159, 14)
        box(g, x + 4, x + 4, G + 8, G + 8, z - 5, z + 5, 159, 14)
        box(g, x + 4, x + 4, G + 6, G + 6, z - 3, z + 3, 168, 2)
        for xx in range(x - 6, x + 1):
            for zz in range(z - 4, z + 5):
                if (xx - x + 3) ** 2 + (zz - z) ** 2 <= 12:
                    for yy in range(G + 2, G + 7):
                        g.set(xx, yy, zz, 0)
                    g.set(xx, G + 1, zz, 9)
                    g.set(xx, G, zz, 168, 0)
                    if (xx + zz) % 5 == 0:
                        g.set(xx, G + 2, zz, 111)
        g.set(x - 3, G + 1, z, 169)

    def spawn(g, x, z):  # lanternes de jade aux coins de la place
        for cx in (x - 6, x + 6):
            for cz in (z - 6, z + 6):
                box(g, cx, cx, G + 1, G + 3, cz, cz, 168, 2)
                g.set(cx, G + 4, cz, 169)
                g.set(cx, G + 5, cz, 44, 5)

    def islet(g, x, z):  # petit sanctuaire
        box(g, x - 1, x + 1, G + 2, G + 2, z - 1, z + 1, 168, 1)
        box(g, x, x, G + 3, G + 4, z, z, 159, 14)
        g.set(x, G + 5, z, 169)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            g.set(x + dx, G + 5, z + dz, 44, 5)

    th.build_back, th.build_front, th.build_spawn, th.build_islet = back, front, spawn, islet
    th.light = (169, 0)


def middle(g, th, rnd):
    """Îlot central (au-dessus de la séparation) avec un monument."""
    surf = {}
    n = noise2(999)
    for x in range(MID - 5, MID + 6):
        for z in range(ZC - 5, ZC + 6):
            d = math.hypot(x - MID, z - ZC)
            if d > 5.2:
                continue
            depth = int((1 - d / 5.2) * 7 + 2 * n(x, z))
            for y in range(G + 1 - depth, G + 2):
                g.set(x, y, z, *(th.top if y == G + 1 else th.rock(x, y, z)))
            surf[(x, z)] = G + 1
    if th.id == "glacier":
        for k in range(10):
            rr = max(0, 2 - k // 4)
            for dx in range(-rr, rr + 1):
                for dz in range(-rr, rr + 1):
                    if abs(dx) + abs(dz) <= rr:
                        g.set(MID + dx, G + 2 + k, ZC + dz, 174)
    else:
        box(g, MID, MID, G + 2, G + 6, ZC, ZC, 168, 2)
        g.set(MID, G + 7, ZC, 169)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            g.set(MID + dx, G + 6, ZC + dz, 44, 5)
    return surf


def build(th):
    g = Grid()
    rnd = random.Random(hash(th.id) & 0xFFFF)
    red = camp(g, th, rnd)
    mirror_camp(g)
    middle(g, th, rnd)
    blue = [(2 * MID - x, y, z) for x, y, z in red]
    return g, red, blue


def preview(g, path):
    from PIL import Image
    pal = {80: (240, 245, 250), 2: (95, 160, 70), 3: (130, 90, 60), 98: (130, 130, 130), 168: (90, 170, 150),
           174: (160, 190, 230), 79: (150, 180, 240), 9: (60, 90, 200), 17: (100, 75, 50), 18: (40, 110, 50),
           5: (120, 90, 60), 159: (170, 60, 50), 1: (120, 120, 120), 169: (220, 240, 240)}
    img = Image.new("RGB", (W, L), (170, 200, 240))
    for x in range(W):
        for z in range(L):
            for y in range(H - 1, -1, -1):
                b = g.get(x, y, z)
                if b:
                    c = pal.get(b, (200, 120, 200))
                    s = 0.6 + 0.4 * y / H
                    img.putpixel((x, z), tuple(int(v * s) for v in c))
                    break
    img.resize((W * 5, L * 5), Image.NEAREST).save(path)


def main():
    out = sys.argv[1]
    prev = sys.argv[2] if len(sys.argv) > 2 else None
    for mk, struct_fn in ((theme_glacier, glacier_structures), (theme_jade, jade_structures)):
        th = mk()
        struct_fn(th)
        g, red, blue = build(th)
        g.write(f"{out}/{th.id}.swm")
        if prev:
            preview(g, f"{prev}/sw_{th.id}.png")
        fmt = lambda pts: ", ".join("{%d, %d, %d}" % p for p in pts)
        n = sum(1 for b in g.ids if b)
        print(f'// {th.id} : {W}x{H}x{L}, {n} blocs')
        print(f'IMPORTED.put("{th.id}", new Imported("{th.name}", 0, 0, 0, {MID}, {ZC}, false, -3, {G + 8},\n'
              f'        new int[][]{{{fmt(red)}}},\n        new int[][]{{{fmt(blue)}}}));')


if __name__ == "__main__":
    main()
