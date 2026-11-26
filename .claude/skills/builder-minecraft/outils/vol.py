"""Chargeurs de volumes : schem Sponge v2/v3, pbm (EemiPaintball), Dead Center (sections.pkl 1.12).
Volume : W,H,L, data array('H') idx=(y*L+z)*W+x, names (nom de base sans namespace ni proprietes)."""
import struct, gzip, zlib, array, os, sys

class R:
    def __init__(s, b): s.b = b; s.i = 0
    def r(s, n):
        v = s.b[s.i:s.i+n]; s.i += n; return v
    def u(s, f):
        v = struct.unpack_from(f, s.b, s.i); s.i += struct.calcsize(f); return v[0]

def _str(r):
    n = r.u('>H'); return r.r(n).decode('utf-8', 'replace')

def _pl(r, t):
    if t == 1: return r.u('>b')
    if t == 2: return r.u('>h')
    if t == 3: return r.u('>i')
    if t == 4: return r.u('>q')
    if t == 5: return r.u('>f')
    if t == 6: return r.u('>d')
    if t == 7: n = r.u('>i'); return r.r(n)
    if t == 8: return _str(r)
    if t == 9:
        et = r.u('>b'); n = r.u('>i'); return [_pl(r, et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = r.u('>b')
            if tt == 0: return d
            k = _str(r); d[k] = _pl(r, tt)
    if t == 11: n = r.u('>i'); return list(struct.unpack_from('>%di' % n, r.r(4*n)))
    if t == 12: n = r.u('>i'); return list(struct.unpack_from('>%dq' % n, r.r(8*n)))
    raise ValueError(t)

def nbt(b):
    try: b = gzip.decompress(b)
    except Exception: pass
    r = R(b); t = r.u('>b'); _str(r); return _pl(r, t)

def base(state):
    s = state.split('[')[0]
    if ':' in s and not s.startswith('minecraft:'):
        return s  # bloc de mod
    return s.split(':')[-1]

class Vol:
    def __init__(s, name, W, H, L, data, names, states=None):
        s.name, s.W, s.H, s.L, s.data, s.names = name, W, H, L, data, names
        s.states = states or names
    def get(s, x, y, z):
        if 0 <= x < s.W and 0 <= y < s.H and 0 <= z < s.L:
            return s.data[(y*s.L+z)*s.W+x]
        return 0

def _norm(states):
    """palette -> index 0 = air"""
    names = ['air']; st = ['air']; remap = []
    seen = {'air': 0}
    for full in states:
        b = base(full)
        if b in ('air', 'cave_air', 'void_air'):
            remap.append(0); continue
        key = full
        if key not in seen:
            seen[key] = len(names); names.append(b); st.append(full)
        remap.append(seen[key])
    return names, st, remap

def load_schem(path, name):
    t = nbt(open(path, 'rb').read())
    if 'Schematic' in t: t = t['Schematic']
    W, H, L = t['Width'] & 0xFFFF, t['Height'] & 0xFFFF, t['Length'] & 0xFFFF
    if 'Blocks' in t:
        pal = t['Blocks']['Palette']; raw = t['Blocks']['Data']
    else:
        pal = t['Palette']; raw = t['BlockData']
    states = [None] * (max(pal.values()) + 1)
    for k, v in pal.items(): states[v] = k
    states = [s or 'minecraft:air' for s in states]
    names, st, remap = _norm(states)
    data = array.array('H', bytes(2 * W * H * L))
    i = 0; n = 0; b = raw
    total = W * H * L
    while n < total:
        v = 0; sh = 0
        while True:
            c = b[i] & 0xFF; i += 1
            v |= (c & 0x7F) << sh
            if not c & 0x80: break
            sh += 7
        data[n] = remap[v]; n += 1
    return Vol(name, W, H, L, data, names, st)

def load_pbm(path, name):
    b = gzip.decompress(open(path, 'rb').read())
    assert b[:4] == b'PBM1'
    W, H, L, n = struct.unpack_from('>iiii', b, 4)
    i = 20; states = []
    for _ in range(n):
        ln = struct.unpack_from('>H', b, i)[0]; i += 2
        states.append(b[i:i+ln].decode('utf-8')); i += ln
    names, st, remap = _norm(states)
    total = W * H * L
    data = array.array('H', bytes(2 * total)); done = 0
    def vi():
        nonlocal i
        v = 0; sh = 0
        while True:
            c = b[i]; i += 1
            v |= (c & 0x7F) << sh
            if not c & 0x80: return v
            sh += 7
    while done < total:
        ln = vi(); val = remap[vi()]
        if val:
            for k in range(done, done + ln): data[k] = val
        done += ln
    return Vol(name, W, H, L, data, names, st)

DATA_IDS = {'stone', 'stonebrick', 'planks', 'log', 'log2', 'wool', 'stained_hardened_clay', 'concrete',
            'sandstone', 'quartz_block', 'red_sandstone', 'stained_glass', 'stained_glass_pane', 'prismarine',
            'double_stone_slab', 'leaves', 'leaves2', 'carpet', 'dirt', 'sand', 'cobblestone_wall', 'red_flower',
            'double_plant', 'tallgrass'}

def load_dc(world_dir, box, name):
    sys.path.insert(0, world_dir)
    import world
    x0, y0, z0, x1, y1, z1 = box
    W, H, L = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    data = array.array('H', bytes(2 * W * H * L))
    names = ['air']; key2i = {}
    for y in range(H):
        for z in range(L):
            o = (y * L + z) * W
            for x in range(W):
                i, d = world.getd(x0 + x, y0 + y, z0 + z)
                if i == 0: continue
                nm = world.name(i)
                b = base(nm)
                if b in ('log', 'log2', 'leaves', 'leaves2'): d &= 3
                k = b + ('#%d' % d if (b in DATA_IDS and d) else '')
                j = key2i.get(k)
                if j is None:
                    j = key2i[k] = len(names); names.append(k)
                data[o + x] = j
    return Vol(name, W, H, L, data, names)
