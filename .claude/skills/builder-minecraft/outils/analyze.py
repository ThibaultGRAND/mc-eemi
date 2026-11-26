"""Mesures d'une map (Vol) : palette, murs, relief, densite, symetrie, toits + rendus PNG.
Usage : python -I analyze.py <map_id>   (voir MAPS)
        python -I analyze.py <fichier.schem|fichier.pbm> [joueurs]   (n'importe quelle map exportee)
Les donnees (schem extraits) vont dans ../data, les resultats (json, png) dans ../out (crees au besoin).
Dead Center : chemin du dossier l4d_analysis dans la variable d'environnement EEMI_DC."""
import sys, os, json, random, collections, math, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vol, colors
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
DATA = os.path.join(ROOT, 'data'); OUT = os.path.join(ROOT, 'out')
DC = os.environ.get('EEMI_DC', os.path.join(ROOT, 'data', 'l4d_analysis'))
PB = r'C:\Users\esteb\EEMICRAFT\EemiPaintball\src\main\resources\maps'
MAPS = {
    'sakura': (lambda: vol.load_schem(os.path.join(DATA, 'sakura-e2900', 'Sakura.schem'), 'Sakura (BedWars 8 eq.)'), 16, False),
    'frostvale': (lambda: vol.load_schem(os.path.join(DATA, 'frostvale', 'Frostvale.schem'), 'Frostvale (BedWars 4 eq.)'), 16, False),
    'emberforge': (lambda: vol.load_schem(os.path.join(DATA, 'emberforge', 'Emberforge.schem'), 'Emberforge (BedWars 4 eq.)'), 16, False),
    'paintball': (lambda: vol.load_schem(os.path.join(DATA, 'paintball', 'Paintball_Map_1_1.18.schem'), 'Paintball foret (schem 1.18)'), 16, False),
    'fort': (lambda: vol.load_pbm(os.path.join(PB, 'fort.pbm'), 'Fort de Battlebound (CTF)'), 16, False),
    'deadcenter': (lambda: vol.load_dc(DC, (-362, 0, 340, -180, 49, 482), 'Dead Center ch.2 rues (L4D 1.12)'), 8, True),
}

# ---------- classification ----------
DET = [('escalier', ('_stairs',)), ('dalle', ('_slab',)), ('panneau/tete/banniere/pot', ('sign', 'banner', 'skull', '_head', 'flower_pot', 'potted_')),
       ('muret', ('_wall',)), ('barriere', ('fence',)), ('trappe', ('trapdoor',)), ('porte', ('_door',)),
       ('vitre/barreaux', ('pane', 'iron_bars')), ('tapis', ('carpet',)), ('bouton/plaque', ('button', 'pressure_plate')),
       ('eclairage', ('lantern', 'torch', 'candle', 'end_rod', 'sea_pickle')), ('chaine', ('chain',)),
       ('divers', ('ladder', 'lever', 'cobweb', 'anvil', 'cauldron', 'hopper', 'bell', 'scaffolding', 'rail', 'tripwire',
                   'brewing_stand', 'enchanting_table', 'lectern', 'grindstone', 'campfire', 'bed', 'pointed_dripstone',
                   'amethyst_cluster', 'lightning_rod', 'chest', 'decorated_pot', 'cake', 'stonecutter', 'snow_layer',
                   'cocoa', 'frame', 'web', 'daylight_detector', 'repeater', 'comparator', 'redstone_wire', 'dragon_egg', 'conduit'))]
VEG = ('sapling', 'flower', 'tulip', 'orchid', 'allium', 'bluet', 'daisy', 'cornflower', 'lily', 'vine', 'fern', 'short_grass',
       'tall_grass', 'tallgrass', 'bush', 'petals', 'kelp', 'seagrass', 'sugar_cane', 'reeds', 'bamboo', 'azalea', 'dripleaf',
       'propagule', 'poppy', 'dandelion', 'double_plant', 'waterlily', 'wheat', 'carrots', 'potatoes', 'beetroot', 'cactus',
       'hanging_roots', 'lichen', 'blossom', 'leaf_litter', 'wildflowers', 'cane', 'sunflower', 'lilac', 'peony', 'rose_bush')

def classify(n, legacy):
    b = n.split('#')[0]
    if b in ('air', 'cave_air', 'void_air'): return 'air'
    if b in ('barrier', 'light', 'structure_void', 'moving_piston', 'piston_extension'): return 'invis'
    if b in ('water', 'lava', 'flowing_water', 'flowing_lava', 'bubble_column'): return 'liquid'
    if 'leaves' in b: return 'leaves'
    if ':' in b: return 'full'  # bloc de mod (faux murs L4D...)
    if b == 'grass' and not legacy: return 'veg'
    if b == 'snow' and not legacy: return 'det:divers'
    if 'mushroom' in b and 'block' not in b and 'stem' not in b: return 'veg'
    for name, subs in DET:
        for s in subs:
            if s in b:
                if s == '_slab' and b.startswith('double_'): return 'full'
                if s == '_wall' and not b.endswith('_wall'): continue
                if s == 'bed' and not b.endswith('bed') or (s == 'bed' and b == 'bedrock'): continue
                if s == 'frame' and b in ('end_portal_frame',): continue
                return 'det:' + name
    for s in VEG:
        if s in b: return 'veg'
    return 'full'

NATURAL = {'stone','dirt','grass_block','andesite','diorite','granite','gravel','sand','red_sand','deepslate','tuff','coarse_dirt','podzol','mycelium','clay','calcite','netherrack','end_stone','bedrock','mud','rooted_dirt','moss_block','dirt_path','farmland','snow_block','powder_snow','ice','packed_ice','blue_ice','basalt','blackstone','soul_sand','soul_soil','magma_block','obsidian','dripstone_block','smooth_basalt','grass_path'}
def natural(n, legacy):
    b = n.split('#')[0]
    if b.endswith('_ore'): return True
    if legacy and b == 'grass': return True
    return b in NATURAL

def family(n):
    b = n.split('#')[0]
    if ':' in b: return 'mod'
    for suf in ('_stairs', '_slab', '_wall', '_fence_gate', '_fence', '_button', '_pressure_plate', '_trapdoor', '_door'):
        if b.endswith(suf): b = b[:-len(suf)]; break
    for pre in ('waxed_', 'exposed_', 'weathered_', 'oxidized_', 'cracked_', 'mossy_', 'chiseled_', 'polished_', 'smooth_', 'cut_', 'stripped_', 'infested_', 'double_'):
        if b.startswith(pre): b = b[len(pre):]
    if b.endswith('terracotta') or b in ('stained_hardened_clay', 'hardened_clay'): return 'terracotta'
    for c in ('wool', 'concrete_powder', 'concrete', 'stained_glass', 'glass'):
        if b.endswith(c): return c
    for s in ('_bricks', '_brick', '_tiles', '_planks', '_log', '_wood', '_block', 's'):
        if b.endswith(s) and len(b) > len(s) + 2: b = b[:-len(s)]; break
    if b in ('cobblestone', 'stonebrick', 'andesite', 'diorite', 'granite', 'tuff', 'stone', 'cobbled_deepslate', 'stone_brick', 'smooth_stone'):
        return 'stone' if 'deepslate' not in b else 'deepslate'
    if 'deepslate' in b: return 'deepslate'
    return b

def analyze(mid):
    t0 = time.time()
    loader, players, legacy = MAPS[mid]
    V = loader()
    W, H, L, D, N = V.W, V.H, V.L, V.data, V.names
    print(V.name, W, H, L, 'charge en %.1fs' % (time.time() - t0)); sys.stdout.flush()
    cat = [classify(n, legacy) for n in N]
    fam = [family(n) for n in N]
    isair = [c in ('air', 'invis') for c in cat]
    full = [c == 'full' for c in cat]
    nat = [natural(n, legacy) for n in N]
    wallb = [f and not t for f, t in zip(full, nat)]
    glass = ['glass' in n for n in N]
    opaque = [(c == 'full' and not g) or c == 'leaves' for c, g in zip(cat, glass)]
    smallpass = ('det:tapis', 'det:bouton/plaque', 'det:eclairage', 'det:panneau/tete/banniere/pot')
    passable = [c in ('air', 'veg') or c in smallpass or (c == 'invis' and N[i] != 'barrier') for i, c in enumerate(cat)]
    support = [c == 'full' or c in ('det:escalier', 'det:dalle') for c in cat]
    relief = [not p and c != 'liquid' for p, c in zip(passable, cat)]
    sx, sz, sy = 1, W, W * L
    R = {'map': V.name, 'dims': [W, H, L], 'joueurs_prevus': players}
    # ---------- 1. palette ----------
    cnt = collections.Counter(); vis = collections.Counter(); detvis = collections.Counter(); mixed = 0; visfull = 0
    for idx in range(W * H * L):
        p = D[idx]
        if not p or isair[p]: continue
        cnt[p] += 1
        x = idx % W; z = (idx // W) % L; y = idx // sy
        e = False; famp = fam[p]; nm = N[p]; mx = False
        for d, ok in ((1, x < W - 1), (-1, x > 0), (sz, z < L - 1), (-sz, z > 0), (sy, y < H - 1), (-sy, y > 0)):
            if not ok: e = True; continue
            q = D[idx + d]
            if isair[q] or cat[q] == 'veg': e = True
            elif full[q] and q != p and fam[q] == famp and N[q] != nm: mx = True
        if e:
            vis[p] += 1
            if full[p]:
                visfull += 1
                if mx: mixed += 1
    tot = sum(cnt.values()); tv = sum(vis.values())
    byname = collections.Counter(); visname = collections.Counter(); viscat = collections.Counter()
    for p, c in cnt.items(): byname[N[p]] += c
    for p, c in vis.items():
        visname[N[p]] += c; viscat[cat[p]] += c
    fams = collections.defaultdict(set)
    for n, c in byname.items():
        if c >= 10: fams[family(n)].add(n)
    R['palette'] = {
        'blocs_non_air': tot, 'types': len(byname), 'types_>=0.1%': sum(1 for c in byname.values() if c >= tot * 0.001),
        'top25': [(n, round(100 * c / tot, 2)) for n, c in byname.most_common(25)],
        'visibles': tv,
        'part_visible_par_categorie_%': {k: round(100 * v / tv, 2) for k, v in viscat.most_common()},
        'part_detail_visible_%': round(100 * sum(v for k, v in viscat.items() if k.startswith('det:')) / tv, 2),
        'part_detail_visible_sur_bati_%': round(100 * sum(c for p, c in vis.items() if cat[p].startswith('det:')) / max(1, sum(c for p, c in vis.items() if not nat[p] and cat[p] not in ('veg', 'leaves', 'liquid'))), 2),
        'part_naturel_visible_%': round(100 * sum(c for p, c in vis.items() if nat[p]) / tv, 2),
        'part_vegetation_visible_%': round(100 * (viscat['veg'] + viscat['leaves']) / tv, 2),
        'top_visibles': [(n, round(100 * c / tv, 2)) for n, c in visname.most_common(15)],
        'familles_multi_variantes': sorted([(f, len(s), sorted(s)) for f, s in fams.items() if len(s) >= 3], key=lambda a: -a[1])[:10],
        'melange_local_%': round(100 * mixed / max(1, visfull), 2),
        'top5_cumul_%': round(100 * sum(c for _, c in byname.most_common(5)) / tot, 1),
    }
    print('palette ok %.1fs' % (time.time() - t0)); sys.stdout.flush()
    # ---------- 2. murs ----------
    runs_plan = []; runs_same = []; segs = []; depths = []; accents = 0; faces = 0
    rnd = random.Random(1)
    for (ax, s) in (('z', -1), ('z', 1), ('x', -1), ('x', 1)):
        dn = (sz if ax == 'z' else sx) * s      # vers l'air
        dt = sx if ax == 'z' else sz             # tangente
        TL = W if ax == 'z' else L
        AL = L if ax == 'z' else W
        for y in range(1, H - 1):
            for a in range(AL):
                if not (0 <= a + s < AL): continue
                run = 0; same = 0; prevn = None; nseg = 0
                for t in range(TL + 1):
                    face = False
                    if t < TL:
                        idx = y * sy + (a * sz + t if ax == 'z' else t * sz + a)
                        p = D[idx]
                        if wallb[p] and passable[D[idx + dn]]:
                            up = D[idx + sy]; dw = D[idx - sy]
                            if (wallb[up] and passable[D[idx + sy + dn]]) or (wallb[dw] and passable[D[idx - sy + dn]]):
                                face = True
                    if face:
                        faces += 1
                        run += 1
                        nm = N[p]
                        if nm == prevn: same += 1
                        else:
                            if same: runs_same.append(same)
                            same = 1; nseg += 1
                        prevn = nm
                        # profondeur (echantillon)
                        if rnd.random() < 0.15:
                            ds = set()
                            for k in range(-4, 5):
                                tt = t + k
                                if not (0 <= tt < TL): continue
                                found = -4
                                for j in range(3, -4, -1):
                                    aa = a + s * j
                                    if not (0 <= aa < AL): continue
                                    q = D[y * sy + (aa * sz + tt if ax == 'z' else tt * sz + aa)]
                                    if relief[q]: found = j; break
                                ds.add(found)
                            depths.append(len(ds))
                        # accent vertical : materiau different des 2 voisins tangents, identique au-dessus et au-dessous
                        if 0 < t < TL - 1:
                            l = D[idx - dt]; r = D[idx + dt]
                            if N[l] != nm and N[r] != nm and full[l] and full[r] and N[D[idx + sy]] == nm and N[D[idx - sy]] == nm:
                                accents += 1
                    else:
                        if run:
                            runs_plan.append(run); segs.append(run / max(1, nseg))
                            if same: runs_same.append(same)
                        run = 0; same = 0; prevn = None; nseg = 0
    def q(a, f):
        if not a: return 0
        a = sorted(a); return a[min(len(a) - 1, int(f * len(a)))]
    rp = [r for r in runs_plan if r >= 3]; rs = [r for r in runs_same if r >= 3]
    wface = sum(runs_plan)
    R['murs'] = {
        'cellules_de_facade': faces,
        'pan_coplanaire_mediane': q(rp, .5), 'pan_coplanaire_p90': q(rp, .9), 'pan_coplanaire_max': max(rp or [0]),
        'pan_meme_bloc_mediane': q(rs, .5), 'pan_meme_bloc_p90': q(rs, .9), 'pan_meme_bloc_max': max(rs or [0]),
        'segment_meme_materiau_moyen': round(sum(segs) / max(1, len(segs)), 2),
        'couches_de_profondeur_fenetre9_moy': round(sum(depths) / max(1, len(depths)), 2),
        'part_facade_1_seule_couche_%': round(100 * sum(1 for d in depths if d == 1) / max(1, len(depths)), 1),
        'part_facade_3+_couches_%': round(100 * sum(1 for d in depths if d >= 3) / max(1, len(depths)), 1),
        'accents_verticaux_pour_100_cellules': round(100 * accents / max(1, wface), 2),
    }
    print('murs ok %.1fs' % (time.time() - t0)); sys.stdout.flush()
    # ---------- 3. relief ----------
    top = [[-1] * L for _ in range(W)]; stand = []; colstand = collections.Counter()
    for x in range(W):
        for z in range(L):
            base = z * sz + x
            for y in range(H - 1, -1, -1):
                p = D[base + y * sy]
                if p and not isair[p] and cat[p] != 'veg':
                    top[x][z] = y; break
            for y in range(1, H - 2):
                i = base + y * sy
                if support[D[i - sy]] and passable[D[i]] and passable[D[i + sy]]:
                    stand.append((x, y, z)); colstand[(x, z)] += 1
    fy = collections.Counter(s[1] for s in stand)
    ns = len(stand)
    levels = sorted(y for y, c in fy.items() if c >= 0.03 * ns)
    # niveaux regroupes (y consecutifs = meme niveau)
    groups = []
    for y in levels:
        if groups and y - groups[-1][-1] <= 1: groups[-1].append(y)
        else: groups.append([y])
    ys = sorted(s[1] for s in stand)
    slopes = []
    for x in range(W - 1):
        for z in range(L - 1):
            h = top[x][z]
            if h < 0: continue
            for h2 in (top[x + 1][z], top[x][z + 1]):
                if h2 >= 0: slopes.append(abs(h2 - h))
    foot = len(colstand)
    hs = [top[x][z] for x in range(W) for z in range(L) if top[x][z] >= 0]
    medh = q(hs, .5)
    hist = collections.Counter((h // 4) * 4 for h in hs)
    R['relief'] = {
        'colonnes_construites': len(hs),
        'histo_hauteur_surface_par_4': sorted(hist.items()),
        'denivele_surface_p5_p95': [q(hs, .05), q(hs, .95)],
        'pente_moy_blocs_par_bloc': round(sum(slopes) / max(1, len(slopes)), 3),
        'part_voisins_meme_hauteur_%': round(100 * sum(1 for v in slopes if v == 0) / max(1, len(slopes)), 1),
        'part_marches_1_%': round(100 * sum(1 for v in slopes if v == 1) / max(1, len(slopes)), 1),
        'part_ressauts_2-4_%': round(100 * sum(1 for v in slopes if 2 <= v <= 4) / max(1, len(slopes)), 1),
        'part_ruptures_5+_%': round(100 * sum(1 for v in slopes if v >= 5) / max(1, len(slopes)), 1),
        'cases_debout': ns, 'niveaux_de_jeu': [(g[0], g[-1], round(100 * sum(fy[y] for y in g) / ns, 1)) for g in groups],
        'nb_niveaux_>=3%': len(groups),
        'y_debout_p5_p50_p95': [q(ys, .05), q(ys, .5), q(ys, .95)],
        'etages_par_colonne_moy': round(ns / max(1, foot), 2),
        'part_colonnes_multi_niveaux_%': round(100 * sum(1 for c in colstand.values() if c >= 2) / max(1, foot), 1),
    }
    print('relief ok %.1fs' % (time.time() - t0)); sys.stdout.flush()
    # ---------- 4. densite / couverts / lignes de vue ----------
    def blocked(x, y, z):
        if not (0 <= x < W and 0 <= y < H and 0 <= z < L): return None
        return opaque[D[(y * L + z) * W + x]]
    samp = rnd.sample(stand, min(3000, ns)) if ns else []
    near = 0; dists = []; rays = []; openrays = 0
    for (x, y, z) in samp:
        e = y + 1
        if any(blocked(x + i, e, z + k) for i in (-1, 0, 1) for k in (-1, 0, 1) if i or k): near += 1
        dmin = 21
        for r in range(1, 21):
            hit = False
            for i in range(-r, r + 1):
                for (a, b) in ((i, -r), (i, r), (-r, i), (r, i)):
                    if blocked(x + a, e, z + b) or blocked(x + a, y, z + b): hit = True; break
                if hit: break
            if hit: dmin = r; break
        dists.append(dmin)
        for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
            k = 1
            while k < 128:
                bb = blocked(x + a * k, e, z + b * k)
                if bb is None: openrays += 1; break
                if bb: break
                k += 1
            rays.append(k * (1.414 if a and b else 1))
    xs = [s[0] for s in stand]; zs = [s[2] for s in stand]
    R['densite'] = {
        'emprise_jouable_colonnes': foot,
        'bbox_jouable_xz': [max(xs) - min(xs) + 1, max(zs) - min(zs) + 1] if stand else [0, 0],
        'surface_jouable_par_joueur_m2': round(foot / players, 1),
        'part_cases_avec_couvert_adjacent_%': round(100 * near / max(1, len(samp)), 1),
        'distance_au_couvert_moy': round(sum(dists) / max(1, len(dists)), 2),
        'distance_au_couvert_mediane': q(dists, .5),
        'part_couvert_a_<=3_blocs_%': round(100 * sum(1 for d in dists if d <= 3) / max(1, len(dists)), 1),
        'ligne_de_vue_mediane': round(q(rays, .5), 1), 'ligne_de_vue_p90': round(q(rays, .9), 1),
        'part_lignes_>=32_%': round(100 * sum(1 for r in rays if r >= 32) / max(1, len(rays)), 1),
        'part_rayons_sortant_de_la_map_%': round(100 * openrays / max(1, len(rays)), 1),
    }
    print('densite ok %.1fs' % (time.time() - t0)); sys.stdout.flush()
    # ---------- 5. symetrie / reperes ----------
    nz = [i for i in range(W * H * L) if D[i] and not isair[D[i]]]
    bx = [i % W for i in nz]; bz = [(i // W) % L for i in nz]
    x0, x1, z0, z1 = min(bx), max(bx), min(bz), max(bz)
    sm = rnd.sample(nz, min(150000, len(nz)))
    cx2, cz2 = x0 + x1, z0 + z1
    def sym(f):
        ok = n = 0
        for i in sm:
            x = i % W; z = (i // W) % L; y = i // sy
            X, Z = f(x, z)
            n += 1
            if 0 <= X < W and 0 <= Z < L and family(N[D[(y * L + Z) * W + X]]) == family(N[D[i]]): ok += 1
        return round(100 * ok / max(1, n), 1)
    hx = (cx2) / 2; hz = (cz2) / 2
    R['symetrie_%'] = {
        'miroir_x': sym(lambda x, z: (cx2 - x, z)), 'miroir_z': sym(lambda x, z: (x, cz2 - z)),
        'rotation_180': sym(lambda x, z: (cx2 - x, cz2 - z)),
        'rotation_90': sym(lambda x, z: (int(round(hx - (z - hz))), int(round(hz + (x - hx))))),
    }
    tall = sum(1 for h in hs if h >= medh + 12)
    R['reperes'] = {'hauteur_surface_mediane': medh, 'hauteur_max': max(hs), 'colonnes_>=_mediane+12': tall,
                    'part_colonnes_hautes_%': round(100 * tall / max(1, len(hs)), 2)}
    # ---------- 6. toits ----------
    rcat = collections.Counter(); rmat = collections.Counter(); eaves = 0; roofs = 0; rheights = collections.Counter()
    for x in range(W):
        for z in range(L):
            h = top[x][z]
            if h < 3 or colstand[(x, z)] == 0: continue
            # il faut un espace debout sous le toit (interieur)
            ins = False
            for y in range(h - 2, 0, -1):
                i = (y * L + z) * W + x
                if passable[D[i]] and passable[D[i + sy]] and support[D[i - sy]] and y + 2 < h:
                    ins = True; break
            if not ins: continue
            p = D[(h * L + z) * W + x]
            roofs += 1; rcat[cat[p]] += 1; rmat[N[p]] += 1; rheights[h] += 1
            below = D[((h - 1) * L + z) * W + x]
            if passable[below] and passable[D[((h - 2) * L + z) * W + x]]: eaves += 1
    R['toits'] = {'colonnes_de_toit': roofs,
                  'categories_%': {k: round(100 * v / max(1, roofs), 1) for k, v in rcat.most_common()},
                  'materiaux_top8': [(n, round(100 * v / max(1, roofs), 1)) for n, v in rmat.most_common(8)],
                  'part_debord_air_dessous_%': round(100 * eaves / max(1, roofs), 1),
                  'hauteurs_distinctes': len(rheights)}
    # ---------- rendus ----------
    render(V, top, cat, mid, x0, x1, z0, z1, isair, passable)
    colors.save()
    R['secondes'] = round(time.time() - t0, 1)
    json.dump(R, open(os.path.join(OUT, mid + '.json'), 'w'), indent=1, ensure_ascii=False)
    print(json.dumps(R, ensure_ascii=False)[:3000])

def render(V, top, cat, mid, x0, x1, z0, z1, isair, passable):
    W, H, L, D, N = V.W, V.H, V.L, V.data, V.names
    w, l = x1 - x0 + 1, z1 - z0 + 1
    hs = [top[x][z] for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if top[x][z] >= 0]
    lo, hi = min(hs), max(hs)
    im = Image.new('RGB', (w, l), (18, 18, 28))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            h = top[x][z]
            if h < 0: continue
            # couleur du bloc du dessus (vegetation basse comprise)
            y = h + 1
            p = D[(min(y, H - 1) * L + z) * W + x]
            if not (y < H and cat[p] == 'veg'): p = D[(h * L + z) * W + x]
            c = colors.color(N[p])
            k = 0.55 + 0.45 * (h - lo) / max(1, hi - lo)
            hw = top[x - 1][z] if x > 0 else h; hn = top[x][z - 1] if z > 0 else h
            sh = 1 + 0.06 * max(-4, min(4, (h - hw) + (h - hn)))
            im.putpixel((x - x0, z - z0), tuple(max(0, min(255, int(v * k * sh))) for v in c))
    sc = max(1, min(6, 1400 // max(w, l)))
    im = im.resize((w * sc, l * sc), Image.NEAREST)
    im.save(os.path.join(OUT, mid + '_dessus.png'))
    # coupe : plan vertical au milieu (z = centre), regard vers +z, profondeur 40
    zc = (z0 + z1) // 2
    ys = [y for y in range(H)]
    cut = Image.new('RGB', (w, H), (135, 170, 220))
    for x in range(x0, x1 + 1):
        for y in range(H):
            for dz in range(0, 40):
                z = zc + dz
                if z >= L: break
                p = D[(y * L + z) * W + x]
                if p and not isair[p] and cat[p] != 'air':
                    c = colors.color(N[p]); k = 1 - 0.6 * dz / 40
                    cut.putpixel((x - x0, H - 1 - y), tuple(int(v * k) for v in c)); break
    # recadrage vertical
    bb = None
    for y in range(H):
        row = [cut.getpixel((x, H - 1 - y)) for x in range(0, w, 2)]
        if any(r != (135, 170, 220) for r in row):
            bb = (y if bb is None else bb[0], y)
    if bb:
        cut = cut.crop((0, H - 1 - min(H - 1, bb[1] + 3), w, H - max(0, bb[0] - 2)))
    sc = max(2, min(6, 1400 // w))
    cut = cut.resize((cut.width * sc, cut.height * sc), Image.NEAREST)
    cut.save(os.path.join(OUT, mid + '_coupe.png'))

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    args = sys.argv[1:]
    if args and args[0].lower().endswith(('.schem', '.pbm')):
        f = os.path.abspath(args[0])
        n = int(args[1]) if len(args) > 1 else 16
        mid = os.path.splitext(os.path.basename(f))[0].lower()
        load = vol.load_pbm if f.lower().endswith('.pbm') else vol.load_schem
        MAPS[mid] = (lambda: load(f, mid), n, False)
        analyze(mid)
    else:
        for m in args:
            analyze(m)
