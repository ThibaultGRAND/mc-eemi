# -*- coding: utf-8 -*-
"""Generateur de l'arene Build Battle DUO Â« Jardins d'EEMICRAFT - Duo Â» (monde buildbattle).

Lancer :  python -I gen_arena_duo.py [chemin/vers/blocks.json]
Sorties (dans ce dossier ; serveur/ = chemins exacts a poser a la racine du serveur) :
  serveur/lobby/datapacks/eemibb/data/eemibb/function/build_duo.mcfunction  (ile, jardins, avenue, fontaine ; appelle walls_duo)
  serveur/lobby/datapacks/eemibb/data/eemibb/function/walls_duo.mcfunction  (murs pleins entre les terrains)
  serveur/lobby/datapacks/eemibb/data/eemibb/function/clear_duo.mcfunction  (vide la zone : seulement pour refaire l'arene)
  arenas_duo.yml                                        (section a ajouter sous instances: de BuildBattle/arenas.yml)
  rapport_duo.txt                                       (controles : etats, flottants, comptes)
  jardins_duo.pbm                                       (export pour .claude/skills/builder-minecraft/outils/analyze.py)

Meme principe que l'arene solo `eemicraft` (gen_arena.py, absent de ce PC) : 8 terrains 25x25, sol Y 64,
hauteur 30, 4 colonnes x 2 rangees, avenue centrale + fontaine, murs pleins en quartz lisse sur le liserÃ©
de beton (1 bloc hors du terrain), y 65..110. Tout est decale de +300 en z : aucune case commune avec l'arene
solo (qui tient dans z -64..64).
"""
import gzip, json, math, os, struct, sys
from collections import defaultdict, deque

HERE = os.path.dirname(os.path.abspath(__file__))
WORLD = 'buildbattle'
DZ = 300                        # decalage par rapport a l'arene solo
G = 64                          # sol des terrains
PLOT = 25                       # terrains 25x25
HEIGHT = 30                     # le terrain monte a G+30 = 94
WALL_TOP = 110
XS = [-70, -35, 0, 35]          # x min des 4 colonnes (comme le solo)
ZS = [-40 + DZ, 15 + DZ]        # z min des 2 rangees -> 260 et 315
CX, CZ = -6, 0 + DZ             # centre de la fontaine
X1, X2 = -84, 73                # ile
Z1, Z2 = 246, 354
COLORS = ['red', 'orange', 'yellow', 'lime', 'light_blue', 'blue', 'purple', 'pink']

LOBBY = (-5.5, 65.0, 9.5 + DZ, 180.0, 0.0)      # regarde vers la fontaine (-z)
SPECT = (-5.5, 89.0, 0.5 + DZ, 0.0, 60.0)

# ---------------------------------------------------------------- bruit (value noise lisse, deterministe)
def _h(ix, iz, s):
    n = (ix * 374761393 + iz * 668265263 + s * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0

def noise(x, z, per, s=0):
    fx, fz = x / per, z / per
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = fx - ix, fz - iz
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a = _h(ix, iz, s) + (_h(ix + 1, iz, s) - _h(ix, iz, s)) * tx
    b = _h(ix, iz + 1, s) + (_h(ix + 1, iz + 1, s) - _h(ix, iz + 1, s)) * tx
    return a + (b - a) * tz

def fbm(x, z, per, s=0):
    return (noise(x, z, per, s) * 0.6 + noise(x, z, per / 2, s + 7) * 0.3 + noise(x, z, per / 4, s + 13) * 0.1)

# ---------------------------------------------------------------- modele
W = {}                 # (x,y,z) -> etat ; absent = air
PHASE = {}             # (x,y,z) -> 0 decor/terrain, 1 murs
def put(x, y, z, st, phase=0):
    W[(x, y, z)] = st
    PHASE[(x, y, z)] = phase

def get(x, y, z):
    return W.get((x, y, z))

def plot_boxes():
    out = []
    i = 0
    for zi, z0 in enumerate(ZS):
        for xi, x0 in enumerate(XS):
            out.append((i, x0, z0, COLORS[i]))
            i += 1
    return out
PLOTS = plot_boxes()

def ring_dist(x, z):
    """distance de Chebyshev au terrain le plus proche (0 = dans un terrain), et le terrain"""
    best = (99, None)
    for p in PLOTS:
        _, x0, z0, _c = p
        dx = max(x0 - x, 0, x - (x0 + PLOT - 1))
        dz = max(z0 - z, 0, z - (z0 + PLOT - 1))
        d = max(dx, dz)
        if d < best[0]:
            best = (d, p)
    return best

def edge_dist(x, z):
    return min(x - X1, X2 - x, z - Z1, Z2 - z)

AV_Z1, AV_Z2 = ZS[0] + PLOT + 2, ZS[1] - 3      # avenue entre les marges de quartz : 287..312
def in_avenue(x, z):
    return AV_Z1 <= z <= AV_Z2

def plaza_r(x, z):
    return math.hypot(x - CX, z - CZ)

# ---------------------------------------------------------------- hauteur du sol
def ground(x, z):
    d, _p = ring_dist(x, z)
    if d <= 3 or in_avenue(x, z):
        return G
    e = edge_dist(x, z)
    # buttes douces dans les jardins exterieurs, a plat pres des terrains et du bord
    t = fbm(x, z, 14, 3)
    h = int(round(max(0.0, t - 0.42) * 7))
    h = min(h, 3, max(0, d - 3), max(0, e - 1))
    return G + h

# ---------------------------------------------------------------- 1. ile (cone inverse)
STONE = ['stone', 'andesite', 'tuff', 'deepslate']
def build_island():
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 1):
            top = ground(x, z)
            e = edge_dist(x, z)
            depth = 3 + min(e, 40) * 0.55 + fbm(x, z, 9, 5) * 6
            bot = int(G - depth)
            for y in range(bot, top + 1):
                if y == top:
                    st = 'grass_block'
                elif y >= top - 3:
                    st = 'dirt'
                else:
                    # strates en grappes : clair en haut, sombre en bas
                    t = (G - y) / 26.0 + (fbm(x + y * 3, z, 7, 11) - 0.5) * 0.5
                    st = STONE[max(0, min(3, int(t * 4)))]
                put(x, y, z, st)
            # stalactites sous l'ile
            if e > 4 and fbm(x, z, 5, 21) > 0.68:
                for k in range(1, 1 + int((fbm(x, z, 3, 22)) * 5)):
                    put(x, bot - k, z, 'tuff' if k < 3 else 'deepslate')

# ---------------------------------------------------------------- 2. terrains, liserÃ©, marge
def build_plots():
    for (i, x0, z0, col) in PLOTS:
        for x in range(x0 - 2, x0 + PLOT + 2):
            for z in range(z0 - 2, z0 + PLOT + 2):
                inside = x0 <= x < x0 + PLOT and z0 <= z < z0 + PLOT
                d = max(max(x0 - x, 0, x - (x0 + PLOT - 1)), max(z0 - z, 0, z - (z0 + PLOT - 1)))
                if inside:
                    put(x, G, z, 'grass_block')             # remis par Build Battle a chaque partie
                elif d == 1:
                    put(x, G, z, col + '_concrete')          # liserÃ© sous le mur
                elif d == 2:
                    corner = (x in (x0 - 2, x0 + PLOT + 1)) and (z in (z0 - 2, z0 + PLOT + 1))
                    put(x, G, z, 'chiseled_quartz_block' if corner else 'smooth_quartz')

# ---------------------------------------------------------------- 3. murs (fonction separee)
def build_walls():
    for (i, x0, z0, col) in PLOTS:
        xa, xb, za, zb = x0 - 1, x0 + PLOT, z0 - 1, z0 + PLOT
        for x in range(xa, xb + 1):
            for z in range(za, zb + 1):
                if not (x in (xa, xb) or z in (za, zb)):
                    continue
                for y in range(G + 1, WALL_TOP + 1):
                    st = col + '_concrete' if y in (G + 1, G + 8, G + 38) else 'smooth_quartz'
                    put(x, y, z, st, 1)
                put(x, WALL_TOP + 1, z, 'smooth_quartz_slab[type=bottom]', 1)
        # pilastres exterieurs sur la marge, tous les 6 blocs (casse le grand pan), angles en quartz cisele
        xa2, xb2, za2, zb2 = x0 - 2, x0 + PLOT + 1, z0 - 2, z0 + PLOT + 1
        for x in range(xa2, xb2 + 1):
            for z in range(za2, zb2 + 1):
                if not (x in (xa2, xb2) or z in (za2, zb2)):
                    continue
                corner = x in (xa2, xb2) and z in (za2, zb2)
                k = (x - xa2) if z in (za2, zb2) else (z - za2)
                if not corner and (k % 6 != 0):
                    continue
                if corner:
                    for y in range(G + 1, WALL_TOP + 1):
                        put(x, y, z, 'quartz_pillar[axis=y]', 1)
                    put(x, WALL_TOP + 1, z, 'chiseled_quartz_block', 1)
                    put(x, WALL_TOP + 2, z, 'lantern[hanging=false]', 1)
                else:
                    for y in range(G + 1, WALL_TOP - 1):
                        put(x, y, z, 'quartz_pillar[axis=y]', 1)
                    put(x, WALL_TOP - 1, z, 'chiseled_quartz_block', 1)

# ---------------------------------------------------------------- 4. avenue, chemins, place
PAVE = ['stone_bricks', 'polished_andesite', 'mossy_stone_bricks']
def build_paths():
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 1):
            if get(x, G, z) not in ('grass_block',) or ground(x, z) != G:
                continue
            d, _p = ring_dist(x, z)
            if d == 0:
                continue
            r = plaza_r(x, z)
            if in_avenue(x, z) and 293 <= z <= 307 or (in_avenue(x, z) and r < 12):
                if r < 12:
                    ring = int(r)
                    st = 'smooth_stone' if ring % 3 else 'polished_andesite'
                    if 10.5 <= r < 12:
                        st = 'polished_diorite'
                elif z in (293, 307):
                    st = 'polished_diorite'                  # bordures de l'allee
                else:
                    t = noise(x, z, 6, 31)
                    st = PAVE[0] if t < 0.55 else (PAVE[1] if t < 0.85 else PAVE[2])
                put(x, G, z, st)
            elif d in (3, 4, 5) and not in_avenue(x, z) and abs(x - X1) > 2 and abs(X2 - x) > 2 \
                    and z - Z1 > 2 and Z2 - z > 2:
                # allees de gravier fin autour des terrains (en terre battue sur les cotes)
                put(x, G, z, 'dirt_path' if noise(x, z, 5, 41) < 0.7 else 'coarse_dirt')

def build_fountain():
    for x in range(CX - 8, CX + 9):
        for z in range(CZ - 8, CZ + 9):
            r = plaza_r(x, z)
            if r < 6.5:
                put(x, G - 1, z, 'sea_lantern' if (abs(x - CX) == 4 and z == CZ) or (abs(z - CZ) == 4 and x == CX)
                    else 'dark_prismarine')
                if abs(x - CX) + abs(z - CZ) > 1:   # la colonne et ses 4 voisins sont poses ensuite
                    put(x, G, z, 'water')
            elif r < 7.5:
                put(x, G, z, 'quartz_bricks')
                put(x, G + 1, z, 'quartz_slab[type=bottom]')
    # colonne centrale a trois etages
    for y in range(G, G + 6):
        put(CX, y, CZ, 'quartz_pillar[axis=y]')
    for dx, dz, f in ((1, 0, 'east'), (-1, 0, 'west'), (0, 1, 'south'), (0, -1, 'north')):
        put(CX + dx, G, CZ + dz, 'quartz_bricks')
        put(CX + dx, G + 1, CZ + dz, 'quartz_stairs[facing=%s,half=top]' % {'east': 'west', 'west': 'east', 'south': 'north', 'north': 'south'}[f])
        put(CX + dx, G + 3, CZ + dz, 'quartz_slab[type=bottom]')
    put(CX, G + 6, CZ, 'chiseled_quartz_block')
    put(CX, G + 7, CZ, 'lantern[hanging=false]')

# ---------------------------------------------------------------- 5. vegetation
def tree(x, z, kind, h):
    y0 = ground(x, z) + 1
    log = {'oak': 'oak_log', 'birch': 'birch_log', 'cherry': 'cherry_log'}[kind]
    leaf = {'oak': 'oak_leaves', 'birch': 'birch_leaves', 'cherry': 'cherry_leaves'}[kind] + '[persistent=true]'
    put(x, y0 - 1, z, 'dirt')
    tx, tz = x, z
    for k in range(h):
        if k == h - 3 and kind != 'birch':          # tronc decale d'un bloc
            tx += 1 if (x + z) % 2 else 0
            tz += 0 if (x + z) % 2 else 1
            put(tx, y0 + k - 1, tz, log + '[axis=y]')
        put(tx, y0 + k, tz, log + '[axis=y]')
    top = y0 + h - 1
    rad = 2.6 if kind != 'birch' else 1.9
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-2, 3):
                d = math.sqrt(dx * dx + dz * dz + (dy * 1.4) ** 2)
                if d <= rad + (noise(tx + dx, tz + dz + dy * 5, 2, 51) - 0.5) * 1.2:
                    p = (tx + dx, top + dy, tz + dz)
                    if p not in W:
                        put(*p, leaf)
    # une branche courte
    bx = 1 if (x * 7 + z) % 2 else -1
    put(tx + bx, top - 2, tz, log + '[axis=x]')

FLOWERS = ['poppy', 'dandelion', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet', 'short_grass', 'fern']
def build_garden():
    # haie continue en bordure (limite naturelle, on ne tombe pas de l'ile pendant l'attente)
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 1):
            e = edge_dist(x, z)
            gy = ground(x, z)
            if e == 0:
                for y in (gy + 1, gy + 2, gy + 3):
                    put(x, y, z, 'oak_leaves[persistent=true]')
                for y in range(gy + 4, G + 12):          # derriere la haie : on ne saute pas dans le vide
                    put(x, y, z, 'barrier')
            elif e == 1 and noise(x, z, 3, 61) > 0.62:
                put(x, gy + 1, z, 'azalea_leaves[persistent=true]')
    # arbres : avenue (alternÃ©s, decor non symetrique) + jardins exterieurs
    trees = []
    for i, x in enumerate(range(X1 + 6, X2 - 3, 13)):
        if abs(x - CX) < 14:
            continue
        trees.append((x, AV_Z1 + 1, 'cherry' if i % 3 == 0 else 'oak', 6))
        trees.append((x + 6, AV_Z2 - 1, 'birch' if i % 2 else 'oak', 6 + (i % 2)))
    import itertools
    cand = []
    for x in range(X1 + 3, X2 - 2):
        for z in range(Z1 + 3, Z2 - 2):
            d, _p = ring_dist(x, z)
            if d >= 7 and edge_dist(x, z) >= 6 and not in_avenue(x, z) and _h(x, z, 71) > 0.985:
                cand.append((x, z))
    for (x, z) in cand:
        if all(abs(x - a) + abs(z - b) > 7 for (a, b, _k, _h2) in trees):
            k = ['oak', 'birch', 'cherry'][int(_h(x, z, 72) * 3) % 3]
            trees.append((x, z, k, 5 + int(_h(x, z, 73) * 3)))
    for (x, z, k, h) in trees:
        tree(x, z, k, h)
    # massifs de fleurs en grappes, buissons
    for x in range(X1 + 1, X2):
        for z in range(Z1 + 1, Z2):
            gy = ground(x, z)
            if get(x, gy, z) != 'grass_block' or (x, gy + 1, z) in W:
                continue
            d, _p = ring_dist(x, z)
            if d < 3:
                continue
            if in_avenue(x, z) and not (z <= AV_Z1 + 2 or z >= AV_Z2 - 2):
                continue
            n = noise(x, z, 5, 81)
            if n > 0.66:
                put(x, gy + 1, z, FLOWERS[int(noise(x, z, 9, 82) * 6) % 6])
            elif n < 0.2 and _h(x, z, 83) < 0.5:
                put(x, gy + 1, z, 'short_grass')
            elif 0.45 < n < 0.47 and d > 6:
                put(x, gy + 1, z, 'oak_leaves[persistent=true]')

def build_lamps():
    for i, x in enumerate(range(X1 + 4, X2 - 2, 10)):
        for z in (AV_Z1 + 4, AV_Z2 - 4):
            if abs(x - CX) < 3 and z == AV_Z2 - 4:
                continue                                  # laisse libre le point d'arrivee
            for y in (G + 1, G + 2, G + 3):
                put(x, y, z, 'dark_oak_fence')
            put(x, G + 4, z, 'lantern[hanging=false]')
        # banc a cote d'un lampadaire sur deux
        if i % 2 == 0:
            put(x + 2, G + 1, AV_Z1 + 4, 'spruce_stairs[facing=north]')
            put(x + 3, G + 1, AV_Z1 + 4, 'spruce_stairs[facing=north]')

# ---------------------------------------------------------------- controles
def load_blocks(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)

def check_state(blocks, st):
    name = st.split('[')[0]
    key = 'minecraft:' + name
    if key not in blocks:
        return 'bloc inconnu'
    if '[' in st:
        props = blocks[key].get('properties', {})
        for kv in st[st.index('[') + 1:-1].split(','):
            k, v = kv.split('=')
            if k not in props or v not in props[k]:
                return 'propriete %s=%s' % (k, v)
    return None

PLANTS = set(FLOWERS) | {'short_grass'}
def floating():
    """composantes non reliees a l'ile + plantes sans terre + lanternes sans support"""
    seen = set()
    start = [p for p in W if p[1] <= G - 2]
    dq = deque(start)
    seen.update(start)
    while dq:
        x, y, z = dq.popleft()
        for d in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
            q = (x+d[0], y+d[1], z+d[2])
            if q in W and q not in seen:
                seen.add(q); dq.append(q)
    loose = [p for p in W if p not in seen]
    bad = []
    for p, st in W.items():
        b = st.split('[')[0]
        below = W.get((p[0], p[1]-1, p[2]), '')
        if b in PLANTS and below.split('[')[0] not in ('grass_block', 'dirt', 'coarse_dirt'):
            bad.append((p, st, 'plante sur ' + below))
        if b == 'lantern' and 'hanging=false' in st and not below:
            bad.append((p, st, 'lanterne sans support'))
    return loose, bad

# ---------------------------------------------------------------- export commandes (fusion en boites)
ORDER_LAST = ('water', 'lantern', 'poppy', 'dandelion', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet',
              'short_grass', 'fern', 'spruce_stairs')
def boxes(cells):
    """cells : dict (x,y,z)->etat d'une phase. Fusion gloutonne x puis z par couche et par etat."""
    out = []
    by = defaultdict(set)
    for (x, y, z), st in cells.items():
        by[(y, st)].add((x, z))
    for (y, st), pts in sorted(by.items(), key=lambda kv: (kv[0][1].split('[')[0] in ORDER_LAST, kv[0][0])):
        pts = set(pts)
        while pts:
            x, z = min(pts)
            x2 = x
            while (x2 + 1, z) in pts: x2 += 1
            z2 = z
            while all((xx, z2 + 1) in pts for xx in range(x, x2 + 1)): z2 += 1
            for xx in range(x, x2 + 1):
                for zz in range(z, z2 + 1):
                    pts.discard((xx, zz))
            out.append((x, y, z, x2, y, z2, st))
    # fusion verticale des boites identiques empilees (murs, piliers)
    key = defaultdict(list)
    for b in out:
        key[(b[0], b[2], b[3], b[5], b[6])].append(b[1])
    merged = []
    for (x, z, x2, z2, st), ys in key.items():
        ys.sort()
        s = ys[0]; prev = s
        for y in ys[1:] + [None]:
            if y is not None and y == prev + 1:
                prev = y; continue
            merged.append((x, s, z, x2, prev, z2, st))
            if y is not None: s = prev = y
    merged.sort(key=lambda b: (b[6].split('[')[0] in ORDER_LAST, b[1]))
    return merged

def cmd(b):
    x, y, z, x2, y2, z2, st = b
    if (x, y, z) == (x2, y2, z2):
        return 'setblock %d %d %d minecraft:%s' % (x, y, z, st)
    return 'fill %d %d %d %d %d %d minecraft:%s' % (x, y, z, x2, y2, z2, st)

def write_pbm(path):
    xs = [p[0] for p in W]; ys = [p[1] for p in W]; zs = [p[2] for p in W]
    x0, y0, z0 = min(xs), min(ys), min(zs)
    Wd, H, L = max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1
    pal = ['minecraft:air']; idx = {'minecraft:air': 0}
    def vi(v, buf):
        while True:
            c = v & 0x7F; v >>= 7
            if v: buf.append(c | 0x80)
            else: buf.append(c); return
    body = bytearray()
    run_v, run_n = None, 0
    for y in range(H):
        for z in range(L):
            for x in range(Wd):
                st = W.get((x + x0, y + y0, z + z0))
                k = 'minecraft:' + st if st else 'minecraft:air'
                if k not in idx: idx[k] = len(pal); pal.append(k)
                v = idx[k]
                if v == run_v: run_n += 1
                else:
                    if run_n: vi(run_n, body); vi(run_v, body)
                    run_v, run_n = v, 1
    vi(run_n, body); vi(run_v, body)
    head = bytearray(b'PBM1') + struct.pack('>iiii', Wd, H, L, len(pal))
    for s in pal:
        e = s.encode('utf-8'); head += struct.pack('>H', len(e)) + e
    with open(path, 'wb') as f:
        f.write(gzip.compress(bytes(head + body)))
    return (x0, y0, z0, Wd, H, L)

def loc(t):
    return '%s,%.1f,%.1f,%.1f,%.1f,%.1f' % ((WORLD,) + t)

def arenas_yml():
    lines = ['  eemicraft_duo:',
             '    lobbylocation: ' + loc(LOBBY),
             '    startlocation: ' + loc(LOBBY),
             '    endlocation: ' + loc(LOBBY),
             '    spectatorlocation: ' + loc(SPECT),
             '    minimumplayers: 4',
             '    maximumplayers: 16',
             '    plotmembersize: 2',
             '    mapname: "Jardins d\'EEMICRAFT - Duo"',
             '    world: ' + WORLD,
             '    gametype: team',
             '    isdone: true',
             '    signs: {}',
             '    plots:']
    for (i, x0, z0, _c) in PLOTS:
        lines.append("      '%d':" % (i + 1))
        lines.append('        \'1\': %s,%.1f,%.1f,%.1f,0.0,0.0' % (WORLD, x0, G, z0))
        lines.append('        \'2\': %s,%.1f,%.1f,%.1f,0.0,0.0' % (WORLD, x0 + PLOT - 1, G + HEIGHT, z0 + PLOT - 1))
    return '\n'.join(lines) + '\n'

def main():
    bj = sys.argv[1] if len(sys.argv) > 1 else None
    build_island(); build_plots(); build_paths(); build_fountain(); build_lamps(); build_garden(); build_walls()
    # rien de pose dans les terrains au-dessus du sol (Build Battle les vide de toute facon)
    for (i, x0, z0, _c) in PLOTS:
        for p in [p for p in W if x0 <= p[0] < x0 + PLOT and z0 <= p[2] < z0 + PLOT and p[1] > G]:
            del W[p]; PHASE.pop(p, None)
    rep = []
    states = sorted(set(W.values()))
    if bj:
        blocks = load_blocks(bj)
        inval = [(s, check_state(blocks, s)) for s in states if check_state(blocks, s)]
        rep.append('Etats de bloc : %d, invalides : %d %s' % (len(states), len(inval), inval))
    loose, bad = floating()
    rep.append('Blocs non relies a l\'ile : %d %s' % (len(loose), loose[:20]))
    rep.append('Supports manquants : %d %s' % (len(bad), bad[:20]))
    # les murs entourent bien chaque terrain sur toute la hauteur
    holes = 0
    for (i, x0, z0, _c) in PLOTS:
        for x in range(x0 - 1, x0 + PLOT + 1):
            for z in range(z0 - 1, z0 + PLOT + 1):
                if x in (x0 - 1, x0 + PLOT) or z in (z0 - 1, z0 + PLOT):
                    for y in range(G + 1, WALL_TOP + 1):
                        if (x, y, z) not in W: holes += 1
    rep.append('Trous dans les murs (y %d..%d) : %d' % (G + 1, WALL_TOP, holes))
    # aucune case en commun avec l'arene solo (forceload -96 -64 80 64 => z <= 79)
    rep.append('z min de l\'arene duo : %d (solo : z <= 79)' % min(p[2] for p in W))
    # point d'arrivee libre
    lx, ly, lz = int(math.floor(LOBBY[0])), int(LOBBY[1]), int(math.floor(LOBBY[2]))
    rep.append('Arrivee %s : sol=%s, pieds=%s, tete=%s' % ((lx, ly, lz), W.get((lx, ly - 1, lz)), W.get((lx, ly, lz)), W.get((lx, ly + 1, lz))))
    sx, sy, sz = int(math.floor(SPECT[0])), int(SPECT[1]), int(math.floor(SPECT[2]))
    rep.append('Spectateur %s : %s' % ((sx, sy, sz), W.get((sx, sy, sz))))
    cnt = defaultdict(int)
    for st in W.values(): cnt[st.split('[')[0]] += 1
    rep.append('Types de blocs : %d ; total %d' % (len(cnt), len(W)))
    rep.append(', '.join('%s %d' % kv for kv in sorted(cnt.items(), key=lambda kv: -kv[1])))

    fdir = os.path.join(HERE, 'serveur', 'lobby', 'datapacks', 'eemibb', 'data', 'eemibb', 'function')
    os.makedirs(fdir, exist_ok=True)
    deco = {p: s for p, s in W.items() if PHASE.get(p, 0) == 0}
    walls = {p: s for p, s in W.items() if PHASE.get(p, 0) == 1}
    bd, bw = boxes(deco), boxes(walls)
    with open(os.path.join(fdir, 'build_duo.mcfunction'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Arene Build Battle DUO (eemicraft_duo) - genere par EemiBuildBattle/duo/gen_arena_duo.py\n')
        f.write('# A lancer : execute in minecraft:buildbattle run function eemibb:build_duo\n')
        f.write('\n'.join(cmd(b) for b in bd) + '\n')
        f.write('function eemibb:walls_duo\n')
    with open(os.path.join(fdir, 'walls_duo.mcfunction'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Murs pleins entre les terrains de l\'arene duo (1 bloc hors du terrain, y %d..%d)\n' % (G + 1, WALL_TOP + 2))
        f.write('\n'.join(cmd(b) for b in bw) + '\n')
    xs = [p[0] for p in W]; ys = [p[1] for p in W]; zs = [p[2] for p in W]
    with open(os.path.join(fdir, 'clear_duo.mcfunction'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Vide la zone de l\'arene duo (seulement pour la refaire). Boite x %d..%d z %d..%d\n' % (min(xs), max(xs), min(zs), max(zs)))
        for y in range(min(ys), max(ys) + 1):
            f.write('fill %d %d %d %d %d %d minecraft:air\n' % (min(xs), y, min(zs), max(xs), y, max(zs)))
    with open(os.path.join(HERE, 'arenas_duo.yml'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(arenas_yml())
    box = write_pbm(os.path.join(HERE, 'jardins_duo.pbm'))
    rep.insert(0, 'Commandes : build_duo %d, walls_duo %d ; boite %s' % (len(bd), len(bw), box))
    big = max((abs(b[3]-b[0])+1)*(abs(b[4]-b[1])+1)*(abs(b[5]-b[2])+1) for b in bd + bw)
    rep.insert(1, 'Plus grosse commande fill : %d blocs (limite par defaut 32768)' % big)
    with open(os.path.join(HERE, 'rapport_duo.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(rep) + '\n')
    print('\n'.join(rep[:9]))

if __name__ == '__main__':
    main()
