"""Outil hors serveur pour les maps Sheep Wars (EemiSheepWars).
Lit les .swm (SWM1 = ids 1.8, SWM2 = palette d'etats modernes), les place comme le plugin (centre x/z 0, y relatif
au bas de map), ecrit un .schem lisible par analyze.py, mesure les grandeurs propres au Sheep Wars, verifie au
parcours a pied (BFS avec sauts) qu'aucun camp n'atteint l'autre, et rend des vues PNG (dessus, iso, profil, coupe).

Usage : python -I sw_tool.py <id> [<id> ...]      (ids de MAPS ci-dessous)
Sorties : .claude/skills/builder-minecraft/out/sw_<id>_*.png, outil-test/out/sw_<id>.schem, sw_<id>.json
"""
import sys, os, gzip, struct, json, math, collections, array
HERE = os.path.dirname(os.path.abspath(__file__))
SKILL = os.path.join(HERE, '..', '..', '.claude', 'skills', 'builder-minecraft')
sys.path.insert(0, os.path.join(SKILL, 'outils'))
import colors
from PIL import Image, ImageDraw

RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'maps')
OUTPNG = os.path.join(SKILL, 'out')
OUTD = os.path.join(HERE, 'out')

# Parametres Imported du plugin : name, ox, oy, oz, mid, zc, rotate, voidRel, magicRel, red, blue
MAPS = json.load(open(os.path.join(HERE, 'maps_params.json'), encoding='utf-8'))
for _f in os.listdir(os.path.join(HERE, 'out')) if os.path.isdir(os.path.join(HERE, 'out')) else []:
    if _f.startswith('params_') and _f.endswith('.json'):
        MAPS[_f[7:-5]] = json.load(open(os.path.join(HERE, 'out', _f), encoding='utf-8-sig'))

# coupes transversales par map (z) : souterrain, points hauts
CUTS = {'pirates': (0, -5, 22), 'ruines': (0, 5, -7), 'spatiale': (0, 5, 13)}

DYES = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan',
        'purple', 'blue', 'brown', 'green', 'red', 'black']
WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak']

def leg_name(i, d):
    """Nom moderne (sans proprietes) d'un bloc 1.8, suffisant pour la mesure et le rendu."""
    T = {1: ['stone', 'granite', 'polished_granite', 'diorite', 'polished_diorite', 'andesite', 'polished_andesite'],
         3: ['dirt', 'coarse_dirt', 'podzol'], 5: [w + '_planks' for w in WOODS],
         17: [w + '_log' for w in WOODS[:4]], 18: [w + '_leaves' for w in WOODS[:4]],
         162: ['acacia_log', 'dark_oak_log'], 31: ['dead_bush', 'short_grass', 'fern'],
         35: [c + '_wool' for c in DYES], 95: [c + '_stained_glass' for c in DYES],
         159: [c + '_terracotta' for c in DYES], 160: [c + '_stained_glass_pane' for c in DYES],
         171: [c + '_carpet' for c in DYES],
         38: ['poppy', 'blue_orchid', 'allium', 'azure_bluet', 'red_tulip', 'orange_tulip', 'white_tulip', 'pink_tulip', 'oxeye_daisy'],
         43: ['smooth_stone', 'sandstone', 'oak_planks', 'cobblestone', 'bricks', 'stone_bricks', 'nether_bricks', 'quartz_block',
              'smooth_stone', 'smooth_sandstone', 'oak_planks', 'cobblestone', 'bricks', 'stone_bricks', 'nether_bricks', 'quartz_block'],
         44: ['smooth_stone_slab', 'sandstone_slab', 'oak_slab', 'cobblestone_slab', 'brick_slab', 'stone_brick_slab', 'nether_brick_slab', 'quartz_slab'],
         98: ['stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks', 'chiseled_stone_bricks'],
         125: [w + '_planks' for w in WOODS], 126: [w + '_slab' for w in WOODS],
         155: ['quartz_block', 'chiseled_quartz_block', 'quartz_pillar'],
         168: ['prismarine', 'prismarine_bricks', 'dark_prismarine'],
         175: ['sunflower', 'lilac', 'tall_grass', 'large_fern', 'rose_bush', 'peony'],
         139: ['cobblestone_wall', 'mossy_cobblestone_wall'], 12: ['sand', 'red_sand'], 24: ['sandstone', 'chiseled_sandstone', 'cut_sandstone'],
         179: ['red_sandstone', 'chiseled_red_sandstone', 'cut_red_sandstone']}
    S = {2: 'grass_block', 4: 'cobblestone', 7: 'bedrock', 8: 'water', 9: 'water', 10: 'lava', 11: 'lava', 13: 'gravel',
         14: 'gold_ore', 15: 'iron_ore', 16: 'coal_ore', 20: 'glass', 21: 'lapis_ore', 22: 'lapis_block', 23: 'dispenser',
         25: 'note_block', 30: 'cobweb', 32: 'dead_bush', 33: 'piston', 37: 'dandelion', 39: 'brown_mushroom', 40: 'red_mushroom',
         41: 'gold_block', 42: 'iron_block', 45: 'bricks', 46: 'tnt', 47: 'bookshelf', 48: 'mossy_cobblestone', 49: 'obsidian',
         50: 'torch', 51: 'fire', 53: 'oak_stairs', 54: 'chest', 56: 'diamond_ore', 57: 'diamond_block', 58: 'crafting_table',
         59: 'wheat', 60: 'farmland', 61: 'furnace', 62: 'furnace', 63: 'oak_sign', 64: 'oak_door', 65: 'ladder', 66: 'rail',
         67: 'cobblestone_stairs', 68: 'oak_wall_sign', 69: 'lever', 70: 'stone_pressure_plate', 71: 'iron_door',
         72: 'oak_pressure_plate', 73: 'redstone_ore', 74: 'redstone_ore', 75: 'redstone_torch', 76: 'redstone_torch',
         77: 'stone_button', 78: 'snow', 79: 'ice', 80: 'snow_block', 81: 'cactus', 82: 'clay', 83: 'sugar_cane',
         85: 'oak_fence', 86: 'carved_pumpkin', 87: 'netherrack', 88: 'soul_sand', 89: 'glowstone', 91: 'jack_o_lantern',
         96: 'oak_trapdoor', 101: 'iron_bars', 102: 'glass_pane', 103: 'melon', 106: 'vine', 107: 'oak_fence_gate',
         108: 'brick_stairs', 109: 'stone_brick_stairs', 110: 'mycelium', 111: 'lily_pad', 112: 'nether_bricks',
         113: 'nether_brick_fence', 114: 'nether_brick_stairs', 117: 'brewing_stand', 118: 'cauldron', 121: 'end_stone',
         123: 'redstone_lamp', 124: 'redstone_lamp', 128: 'sandstone_stairs', 133: 'emerald_block', 134: 'spruce_stairs',
         135: 'birch_stairs', 136: 'jungle_stairs', 140: 'flower_pot', 143: 'oak_button', 144: 'skeleton_skull', 145: 'anvil',
         147: 'light_weighted_pressure_plate', 148: 'heavy_weighted_pressure_plate', 151: 'daylight_detector',
         152: 'redstone_block', 154: 'hopper', 156: 'quartz_stairs', 163: 'acacia_stairs', 164: 'dark_oak_stairs',
         165: 'slime_block', 166: 'barrier', 167: 'iron_trapdoor', 169: 'sea_lantern', 170: 'hay_block', 172: 'terracotta',
         173: 'coal_block', 174: 'packed_ice', 176: 'white_banner', 177: 'white_wall_banner', 180: 'red_sandstone_stairs',
         183: 'spruce_fence_gate', 184: 'birch_fence_gate', 185: 'jungle_fence_gate', 186: 'dark_oak_fence_gate',
         187: 'acacia_fence_gate', 188: 'spruce_fence', 189: 'birch_fence', 190: 'jungle_fence', 191: 'dark_oak_fence',
         192: 'acacia_fence', 193: 'spruce_door', 194: 'birch_door', 195: 'jungle_door', 196: 'acacia_door', 197: 'dark_oak_door'}
    if i in (17, 18, 162, 161):
        d &= 3
    if i in (44, 126):
        d &= 7
    if i == 175:
        d &= 7
        if d > 5: d = 2
    if i in T:
        lst = T[i]
        return lst[d] if d < len(lst) else lst[0]
    return S.get(i, 'unknown_%d' % i)

def load(mid):
    p = MAPS[mid]
    b = gzip.decompress(open(os.path.join(RES, mid + '.swm'), 'rb').read())
    mg = b[:4]
    W, H, L = struct.unpack_from('>hhh', b, 4)
    names = ['air']; key = {}
    data = array.array('H', bytes(2 * W * H * L))
    if mg == b'SWM1':
        raw = b[10:]
        for i in range(W * H * L):
            idv = raw[2 * i]
            if idv == 0 or idv == 166: continue   # le plugin saute l'air et les barrieres
            d = raw[2 * i + 1] & 15
            k = (idv, d)
            j = key.get(k)
            if j is None:
                j = key[k] = len(names); names.append(leg_name(idv, d))
            data[i] = j
        states = list(names)
    elif mg == b'SWM2':
        n = struct.unpack_from('>i', b, 10)[0]; o = 14; pal = []
        for _ in range(n):
            ln = struct.unpack_from('>H', b, o)[0]; o += 2
            pal.append(b[o:o + ln].decode('utf-8')); o += ln
        idx = array.array('H'); idx.frombytes(b[o:o + 2 * W * H * L]); idx.byteswap()
        states = ['air']; names = ['air']; remap = []
        for s in pal:
            bn = s.split('[')[0].split(':')[-1]
            if bn in ('air', 'barrier'): remap.append(0); continue
            remap.append(len(states)); states.append(s if ':' in s else 'minecraft:' + s); names.append(bn)
        for i in range(W * H * L):
            data[i] = remap[idx[i]]
    else:
        raise ValueError('format inconnu')
    return p, W, H, L, data, names, states

# ---------- classification pour la marche ----------
PASS_SUB = ('torch', 'flower', 'tulip', 'orchid', 'allium', 'bluet', 'daisy', 'poppy', 'dandelion', 'short_grass', 'fern',
            'tall_grass', 'dead_bush', 'sapling', 'rail', 'button', 'pressure_plate', 'lever', 'sign', 'banner', 'vine',
            'ladder', 'lily_pad', 'cobweb', 'mushroom', 'wheat', 'sugar_cane', 'redstone_wire', 'sunflower', 'lilac',
            'rose_bush', 'peony', 'seagrass', 'kelp', 'snow', 'carpet', 'tripwire', 'fire', 'lantern', 'chain', 'end_rod',
            'candle', 'pickle', 'moss_carpet', 'glow_lichen', 'hanging_roots', 'leaf_litter', 'petals', 'bush', 'coral',
            'water', 'lava', 'structure_void', 'cave_air', 'void_air', 'bubble_column', 'item_frame', 'roots',
            'spore_blossom', 'dripleaf', 'cocoa', 'skull', 'head', 'flower_pot', 'potted_')
NOPASS_EXACT = {'snow_block', 'mushroom_stem', 'brown_mushroom_block', 'red_mushroom_block', 'moss_block', 'mud', 'lantern_block',
                'sea_lantern', 'jack_o_lantern', 'carved_pumpkin', 'coral_block', 'chain_command_block', 'end_rod_block',
                'dead_brain_coral_block', 'dead_tube_coral_block', 'dead_horn_coral_block', 'dead_bubble_coral_block', 'dead_fire_coral_block',
                'brain_coral_block', 'tube_coral_block', 'horn_coral_block', 'bubble_coral_block', 'fire_coral_block', 'dried_kelp_block'}

def passable_name(n):
    if n in ('air', 'light'): return True
    if n in NOPASS_EXACT or n.endswith('_block') and ('coral' in n or 'mushroom' in n): return False
    if n.endswith('_door') or n.endswith('_fence_gate') or n.endswith('_trapdoor'): return True  # ouvrables
    for s in PASS_SUB:
        if s in n: return True
    return False

def tall_name(n):  # barrieres et murets : 1,5 de haut, on ne monte pas dessus d'un saut
    return n.endswith('_fence') or n.endswith('_wall') or n == 'iron_bars' or n.endswith('_pane') or n == 'cobblestone_wall'

# ---------- placement monde ----------
class Map:
    def __init__(s, mid):
        s.mid = mid
        s.p, s.W, s.H, s.L, s.data, s.names, s.states = load(mid)
        p = s.p
        s.rot = p['rotate']
        s.pas = [passable_name(n) for n in s.names]
        s.liq = [n in ('water', 'lava') for n in s.names]
        s.tall = [tall_name(n) for n in s.names]
        s.ladder = [n in ('ladder', 'vine') for n in s.names]
        # monde : x in [x0..x1], z in [z0..z1], y relatif in [0..H-1]
        xs = [s.wx(0), s.wx(s.W - 1)]; zs = [s.wz(0), s.wz(s.L - 1)]
        s.x0, s.x1 = min(xs), max(xs); s.z0, s.z1 = min(zs), max(zs)

    def wx(s, lx):
        d = s.p['ox'] + lx - s.p['mid']
        return -d if s.rot else d
    def wz(s, lz):
        d = s.p['oz'] + lz - s.p['zc']
        return -d if s.rot else d
    def lx(s, wx):
        d = -wx if s.rot else wx
        return d + s.p['mid'] - s.p['ox']
    def lz(s, wz):
        d = -wz if s.rot else wz
        return d + s.p['zc'] - s.p['oz']
    def get(s, x, y, z):
        """index palette au point monde (y relatif)"""
        lx = s.lx(x); lz = s.lz(z)
        if 0 <= lx < s.W and 0 <= lz < s.L and 0 <= y < s.H:
            return s.data[(y * s.L + lz) * s.W + lx]
        return 0
    def world_pt(s, pt):
        """point d'apparition du plugin (coordonnees d'origine) -> monde"""
        d = pt[0] - s.p['mid']; e = pt[2] - s.p['zc']
        if s.rot: d, e = -d, -e
        return (d, pt[1] - s.p['oy'], e)

def solid(m, j):
    return j != 0 and not m.pas[j]

def standable(m, x, y, z):
    """pieds en y : bloc porteur en y-1, 2 cases libres"""
    if y < 1: return False
    s = m.get(x, y - 1, z)
    if not solid(m, s): return False
    a = m.get(x, y, z); b = m.get(x, y + 1, z)
    return m.pas[a] and not m.liq[a] and m.pas[b]

def measure(m):
    R = {'id': m.mid, 'nom': m.p['name'], 'dims_fichier': [m.W, m.H, m.L]}
    void = m.p['voidRel']
    sides = {'rouge': -1, 'bleu': 1}
    blocks = {k: [] for k in sides}; mid_blocks = 0; centre = []
    cnt = collections.Counter()
    for y in range(m.H):
        for lz in range(m.L):
            for lx in range(m.W):
                j = m.data[(y * m.L + lz) * m.W + lx]
                if not j: continue
                cnt[m.names[j]] += 1
                x = m.wx(lx); z = m.wz(lz)
                if y < void: continue   # sous la hauteur de mort (mer, pieds des plateformes) : hors jeu
                if abs(x) <= 8 and abs(z) <= 9:   # ilot central des maps 1.8 (hors camps)
                    centre.append((x, y, z, j))
                if abs(x) <= 8 and abs(z) <= 9 and m.mid in ('rose', 'vert', 'vaisseau'): continue
                if x < 0: blocks['rouge'].append((x, y, z, j))
                elif x > 0: blocks['bleu'].append((x, y, z, j))
                else: mid_blocks += 1
    R['blocs_total'] = sum(cnt.values()); R['types'] = len(cnt)
    R['types_>=0.1%'] = sum(1 for c in cnt.values() if c >= 0.001 * R['blocs_total'])
    R['blocs_sur_x0'] = mid_blocks
    R['blocs_zone_centrale_|x|<=8_|z|<=9'] = len(centre)
    R['blocs_bande_|x|<=5'] = sum(1 for b in blocks['rouge'] + blocks['bleu'] if abs(b[0]) <= 5) + mid_blocks
    stand = {}
    for k, sg in sides.items():
        bl = blocks[k]
        if not bl: continue
        xs = [b[0] for b in bl]; ys = [b[1] for b in bl]; zs = [b[2] for b in bl]
        sol = [b for b in bl if solid(m, b[3]) and not m.liq[b[3]]]
        cells = set()
        for (x, y, z, j) in sol:
            if standable(m, x, y + 1, z) and y + 1 >= void:
                cells.add((x, y + 1, z))
        stand[k] = cells
        cols = {(c[0], c[2]) for c in cells}
        lv = collections.Counter(c[1] for c in cells)
        R[k] = {'blocs': len(bl), 'x': [min(xs), max(xs)], 'y': [min(ys), max(ys)], 'z': [min(zs), max(zs)],
                'longueur_x': max(xs) - min(xs) + 1, 'largeur_z': max(zs) - min(zs) + 1,
                'bord_interieur_x': (max(xs) if sg < 0 else min(xs)),
                'surface_jouable_colonnes': len(cols), 'cases_debout': len(cells),
                'niveaux_y_>=3%': sorted([y for y, c in lv.items() if c >= 0.03 * len(cells)]),
                'y_debout': [min(lv), max(lv)] if lv else None}
    # vide entre les camps : distance mini (horizontale, et 3D) entre un bloc rouge et un bloc bleu
    rr = [b for b in blocks['rouge'] if b[0] >= -30]; bb = [b for b in blocks['bleu'] if b[0] <= 30]
    best_h = 1e9; best_3 = 1e9; best_s = 1e9
    rs = [b for b in rr if solid(m, b[3])]; bs = [b for b in bb if solid(m, b[3])]
    for a in rr:
        for b in bb:
            dx = b[0] - a[0]; dz = b[2] - a[2]; dy = b[1] - a[1]
            h = math.hypot(dx, dz)
            if h < best_h: best_h = h
            d3 = math.sqrt(dx * dx + dz * dz + dy * dy)
            if d3 < best_3: best_3 = d3
    for a in rs:
        for b in bs:
            dx = b[0] - a[0]; dz = b[2] - a[2]
            h = math.hypot(dx, dz)
            if h < best_s: best_s = h
    # largeur du vide sur l'axe x (par rangee z) : x_bleu_min - x_rouge_max - 1
    gaps = []
    for z in range(m.z0, m.z1 + 1):
        a = [b[0] for b in rs if b[2] == z]; c = [b[0] for b in bs if b[2] == z]
        if a and c: gaps.append(min(c) - max(a) - 1)
    R['vide'] = {'dist_horiz_min_tous_blocs': round(best_h, 2), 'dist_3d_min_tous_blocs': round(best_3, 2),
                 'dist_horiz_min_blocs_solides': round(best_s, 2),
                 'largeur_x_min_par_rangee': min(gaps) if gaps else None,
                 'largeur_x_mediane': sorted(gaps)[len(gaps) // 2] if gaps else None,
                 'rangees_face_a_face': len(gaps)}
    # zone construction : x*dir >= 2 ; distance entre zones constructibles = 3 (x -1..1) partout
    R['hauteur_mort_rel'] = void; R['laine_magique_rel'] = [m.p['magicRel'], m.p['magicRel'] + 2]
    sp = {k: [m.world_pt(pt) for pt in m.p[('red' if k == 'rouge' else 'blue')]] for k in sides}
    R['apparitions'] = sp
    bad = []
    for k, pts in sp.items():
        for (x, y, z) in pts:
            if not standable(m, x, y + 1, z): bad.append((k, x, y, z, 'pas debout'))
            if (x < 0) != (k == 'rouge'): bad.append((k, x, y, z, 'mauvais camp'))
            # la cage 3x3x4 doit tenir
    R['apparitions_problemes'] = bad
    # ---------- parcours a pied ----------
    allcells = stand.get('rouge', set()) | stand.get('bleu', set())
    for k in sides:
        start = [(x, y + 1, z) for (x, y, z) in sp[k]]
        seen = bfs(m, start, void)
        other = 'bleu' if k == 'rouge' else 'rouge'
        sg = sides[other]
        hit = [c for c in seen if c[0] * sg >= 1]
        closest = max((c[0] * sg for c in seen), default=None)   # avancee max vers l'autre camp (x*dir adverse)
        R['parcours_depuis_' + k] = {'cases_atteintes': len(seen), 'cases_dans_camp_adverse': len(hit),
                                      'avancee_max_x_signee': closest,
                                      'exemple': sorted(hit)[:3]}
    return R, cnt

def jump_reach(dy):
    """Distance horizontale d'un saut en sprint (physique du jeu, genereuse) qui atterrit dy blocs plus bas
    (dy = -1 : un bloc plus haut). Inclut la chute planee : plus on tombe, plus on va loin."""
    y = 0.0; vy = 0.42; x = 0.0; v = 0.48; t = 0
    while t < 600:
        x += v; y += vy; vy = (vy - 0.08) * 0.98; v = v * 0.91 + 0.026; t += 1
        if vy < 0 and y <= -dy:
            return x
    return x

def bfs(m, start, void):
    """Parcours genereux : marche (+1 / chute), echelles, sauts en sprint avec chute planee
    (distance entre centres <= portee(dy) + 1,6 : le joueur deborde de 0,3 au depart et a l'arrivee).
    L'arc du saut n'est pas verifie (plus permissif que le jeu)."""
    cols = collections.defaultdict(list)
    for y in range(max(1, void), m.H + 1):
        for lz in range(m.L):
            for lx in range(m.W):
                x = m.wx(lx); z = m.wz(lz)
                if standable(m, x, y, z): cols[(x, z)].append(y)
    REACH = {dy: jump_reach(dy) + 1.6 for dy in range(-1, m.H + 5)}
    RMAX = int(max(REACH.values())) + 1
    offs = [(dx, dz, math.hypot(dx, dz)) for dx in range(-RMAX, RMAX + 1) for dz in range(-RMAX, RMAX + 1)
            if 0 < dx * dx + dz * dz <= RMAX * RMAX]
    seen = set(); q = collections.deque()
    for c in start:
        if c not in seen: seen.add(c); q.append(c)
    while q:
        x, y, z = q.popleft()
        nxt = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (x + dx, y + 1, z + dz)   # marche montante
            if standable(m, *c) and m.pas[m.get(x, y + 2, z)] and not m.tall[m.get(c[0], y, c[2])]:
                nxt.append(c)
        for dx, dz, d in offs:
            lst = cols.get((x + dx, z + dz))
            if not lst: continue
            for yl in lst:
                dy = y - yl
                if dy < -1: continue
                if d <= REACH[dy] or (d <= 1.01 and dy >= 0):
                    nxt.append((x + dx, yl, z + dz))
        # echelles
        if m.ladder[m.get(x, y, z)] or m.ladder[m.get(x, y + 1, z)]:
            for yy in range(y + 1, min(m.H, y + 40)):
                if not m.ladder[m.get(x, yy, z)] and not m.ladder[m.get(x, yy - 1, z)]: break
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (0, 0)):
                    if standable(m, x + dx, yy, z + dz): nxt.append((x + dx, yy, z + dz))
        for c in nxt:
            if c not in seen:
                seen.add(c); q.append(c)
    return seen
# ---------- export schem (Sponge v2) ----------
def write_schem(m, path):
    W = m.x1 - m.x0 + 1; L = m.z1 - m.z0 + 1; H = m.H
    pal = {}; order = []
    def pid(s):
        if s not in pal: pal[s] = len(order); order.append(s)
        return pal[s]
    pid('minecraft:air')
    st = [('minecraft:' + n if ':' not in n else n) if i else 'minecraft:air' for i, n in enumerate(m.states)]
    buf = bytearray()
    for y in range(H):
        for z in range(m.z0, m.z1 + 1):
            for x in range(m.x0, m.x1 + 1):
                v = pid(st[m.get(x, y, z)])
                while True:
                    c = v & 0x7F; v >>= 7
                    if v: buf.append(c | 0x80)
                    else: buf.append(c); break
    def tag_str(s):
        e = s.encode('utf-8'); return struct.pack('>H', len(e)) + e
    out = bytearray()
    out += b'\x0a' + tag_str('Schematic')
    out += b'\x03' + tag_str('Version') + struct.pack('>i', 2)
    out += b'\x03' + tag_str('DataVersion') + struct.pack('>i', 4671)
    out += b'\x02' + tag_str('Width') + struct.pack('>h', W)
    out += b'\x02' + tag_str('Height') + struct.pack('>h', H)
    out += b'\x02' + tag_str('Length') + struct.pack('>h', L)
    out += b'\x03' + tag_str('PaletteMax') + struct.pack('>i', len(order))
    out += b'\x0a' + tag_str('Palette')
    for s in order:
        out += b'\x03' + tag_str(s) + struct.pack('>i', pal[s])
    out += b'\x00'
    out += b'\x07' + tag_str('BlockData') + struct.pack('>i', len(buf)) + bytes(buf)
    out += b'\x00'
    open(path, 'wb').write(gzip.compress(bytes(out)))

# ---------- rendus ----------
def col(n):
    if n == 'air': return None
    c = colors.color(n)
    return c

def render(m, R):
    names = m.names
    C = [None] + [col(n) for n in names[1:]]
    W = m.x1 - m.x0 + 1; L = m.z1 - m.z0 + 1
    # dessus : x vers la droite, z vers le bas
    sc = 6
    top = Image.new('RGB', (W * sc, L * sc), (16, 18, 30))
    d = ImageDraw.Draw(top)
    for z in range(m.z0, m.z1 + 1):
        for x in range(m.x0, m.x1 + 1):
            for y in range(m.H - 1, -1, -1):
                j = m.get(x, y, z)
                if j and C[j]:
                    f = 0.55 + 0.45 * y / max(1, m.H - 1)
                    c = tuple(min(255, int(v * f)) for v in C[j])
                    d.rectangle([(x - m.x0) * sc, (z - m.z0) * sc, (x - m.x0 + 1) * sc - 1, (z - m.z0 + 1) * sc - 1], fill=c)
                    break
    # repere x = 0 et zones interdites de construction (|x| < 2)
    xm = (0 - m.x0) * sc
    d.line([xm + sc // 2, 0, xm + sc // 2, L * sc], fill=(255, 255, 0), width=1)
    for k, pts in R['apparitions'].items():
        for (x, y, z) in pts:
            cx = (x - m.x0) * sc + sc // 2; cz = (z - m.z0) * sc + sc // 2
            d.ellipse([cx - 2, cz - 2, cx + 2, cz + 2], fill=(255, 60, 60) if k == 'rouge' else (60, 120, 255))
    top.save(os.path.join(OUTPNG, 'sw_%s_dessus.png' % m.mid))
    # profil (vu depuis le sud, projection sur x/y) : montre le vide
    sc2 = 6
    side = Image.new('RGB', (W * sc2, m.H * sc2), (16, 18, 30))
    d2 = ImageDraw.Draw(side)
    for x in range(m.x0, m.x1 + 1):
        for y in range(m.H):
            for z in range(m.z1, m.z0 - 1, -1):
                j = m.get(x, y, z)
                if j and C[j]:
                    f = 0.6 + 0.4 * (z - m.z0) / max(1, L - 1)
                    c = tuple(min(255, int(v * f)) for v in C[j])
                    d2.rectangle([(x - m.x0) * sc2, (m.H - 1 - y) * sc2, (x - m.x0 + 1) * sc2 - 1, (m.H - y) * sc2 - 1], fill=c)
                    break
    vy = m.H - 1 - R['hauteur_mort_rel']
    d2.line([0, vy * sc2, W * sc2, vy * sc2], fill=(255, 40, 40))
    d2.line([xm + sc2 // 2, 0, xm + sc2 // 2, m.H * sc2], fill=(255, 255, 0))
    side.save(os.path.join(OUTPNG, 'sw_%s_profil.png' % m.mid))
    # coupes transversales x/y (z choisis par map : souterrain et points hauts), echelle 8, ligne rouge = mort
    cuts = CUTS.get(m.mid, (0, -8, 8)); sc3 = 8
    cut = Image.new('RGB', (W * sc3, m.H * sc3 * len(cuts) + 12 * len(cuts)), (16, 18, 30))
    d3 = ImageDraw.Draw(cut)
    for k, zz in enumerate(cuts):
        oy = k * (m.H * sc3 + 12)
        for x in range(m.x0, m.x1 + 1):
            for y in range(m.H):
                j = m.get(x, y, zz)
                if not j and any(m.get(x, yy, zz) and not m.pas[m.get(x, yy, zz)] for yy in range(y + 1, min(m.H, y + 5))) \
                        and any(m.get(x, yy, zz) and not m.pas[m.get(x, yy, zz)] for yy in range(max(0, y - 4), y)):
                    # air enferme (souterrain, interieurs) : gris bleute pour le distinguer du ciel
                    d3.rectangle([(x - m.x0) * sc3, oy + (m.H - 1 - y) * sc3, (x - m.x0 + 1) * sc3 - 1, oy + (m.H - y) * sc3 - 1], fill=(58, 64, 92))
                if j and C[j]:
                    d3.rectangle([(x - m.x0) * sc3, oy + (m.H - 1 - y) * sc3, (x - m.x0 + 1) * sc3 - 1, oy + (m.H - y) * sc3 - 1], fill=C[j])
        d3.line([0, oy + vy * sc3, W * sc3, oy + vy * sc3], fill=(255, 40, 40))
        d3.text((4, oy + 2), 'z = %d' % zz, fill=(255, 255, 120))
    cut.save(os.path.join(OUTPNG, 'sw_%s_coupe.png' % m.mid))
    iso(m, C, 'sw_%s_iso.png' % m.mid, False)
    iso(m, C, 'sw_%s_iso2.png' % m.mid, True)

def iso(m, C, fname, flip):
    s = 4  # demi-largeur d'une face
    W = m.x1 - m.x0 + 1; L = m.z1 - m.z0 + 1
    pts = []
    def P(x, y, z):
        # projection isometrique (vue du sud-est, ou du nord-ouest si flip)
        if flip: x, z = W - x, L - z
        return ((x - z) * s * 2, (x + z) * s - y * s * 2.4)
    cs = [P(a, b, c) for a in (0, W) for b in (0, m.H) for c in (0, L)]
    minx = min(p[0] for p in cs) - 10; maxx = max(p[0] for p in cs) + 10
    miny = min(p[1] for p in cs) - 10; maxy = max(p[1] for p in cs) + 10
    img = Image.new('RGB', (int(maxx - minx), int(maxy - miny)), (16, 18, 30))
    d = ImageDraw.Draw(img)
    rng_x = range(m.x0, m.x1 + 1); rng_z = range(m.z0, m.z1 + 1)
    order = []
    for y in range(m.H):
        for z in rng_z:
            for x in rng_x:
                j = m.get(x, y, z)
                if not j or C[j] is None: continue
                lx = x - m.x0; lz = z - m.z0
                if flip:
                    ex = (m.get(x - 1, y, z), m.get(x, y, z - 1))
                    sk = (W - lx) + (L - lz)
                else:
                    ex = (m.get(x + 1, y, z), m.get(x, y, z + 1))
                    sk = lx + lz
                up = m.get(x, y + 1, z)
                if up and not m.pas[up] and ex[0] and not m.pas[ex[0]] and ex[1] and not m.pas[ex[1]]: continue
                order.append((sk, y, lx, lz, j))
    order.sort()
    for sk, y, lx, lz, j in order:
        c = C[j]
        def T(px, py, pz):
            a, b = P(px, py, pz); return (a - minx, b - miny)
        if flip:
            x0, z0 = lx + 1, lz + 1
            top = [T(x0, y + 1, z0), T(x0 - 1, y + 1, z0), T(x0 - 1, y + 1, z0 - 1), T(x0, y + 1, z0 - 1)]
            fa = [T(x0 - 1, y + 1, z0 - 1), T(x0 - 1, y + 1, z0), T(x0 - 1, y, z0), T(x0 - 1, y, z0 - 1)]
            fb = [T(x0 - 1, y + 1, z0 - 1), T(x0, y + 1, z0 - 1), T(x0, y, z0 - 1), T(x0 - 1, y, z0 - 1)]
        else:
            top = [T(lx, y + 1, lz), T(lx + 1, y + 1, lz), T(lx + 1, y + 1, lz + 1), T(lx, y + 1, lz + 1)]
            fa = [T(lx + 1, y + 1, lz), T(lx + 1, y + 1, lz + 1), T(lx + 1, y, lz + 1), T(lx + 1, y, lz)]
            fb = [T(lx, y + 1, lz + 1), T(lx + 1, y + 1, lz + 1), T(lx + 1, y, lz + 1), T(lx, y, lz + 1)]
        d.polygon(fa, fill=tuple(int(v * 0.72) for v in c))
        d.polygon(fb, fill=tuple(int(v * 0.55) for v in c))
        d.polygon(top, fill=c)
    img.save(os.path.join(OUTPNG, fname))

if __name__ == '__main__':
    os.makedirs(OUTD, exist_ok=True); os.makedirs(OUTPNG, exist_ok=True)
    noimg = '--noimg' in sys.argv
    for mid in [a for a in sys.argv[1:] if not a.startswith('--')]:
        m = Map(mid)
        R, cnt = measure(m)
        R['top_blocs'] = cnt.most_common(12)
        write_schem(m, os.path.join(OUTD, 'sw_%s.schem' % mid))
        json.dump(R, open(os.path.join(OUTD, 'sw_%s.json' % mid), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        if not noimg:
            render(m, R)
        colors.save()
        print(json.dumps({k: v for k, v in R.items() if k not in ('apparitions', 'top_blocs')}, ensure_ascii=False))
