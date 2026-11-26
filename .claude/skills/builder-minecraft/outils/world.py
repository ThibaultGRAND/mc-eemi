import os, pickle, zlib, array, json
D = os.path.dirname(os.path.abspath(__file__))
_secs = pickle.load(open(os.path.join(D, 'sections.pkl'), 'rb'))
_cache = {}
reg = json.load(open(os.path.join(D, 'fml_registry.json')))['/Registries/minecraft:blocks/ids']
ID2NAME = {v: k for k, v in reg.items()}

def section(cx, cy, cz):
    k = (cx, cy, cz)
    s = _cache.get(k)
    if s is None:
        raw = _secs.get(k)
        if raw is None:
            s = False
        else:
            b = zlib.decompress(raw)
            ids = array.array('H'); ids.frombytes(b[:8192])
            s = (ids, b[8192:])
        if len(_cache) > 60000: _cache.clear()
        _cache[k] = s
    return s

def get(x, y, z):
    if y < 0 or y > 255: return 0
    s = section(x >> 4, y >> 4, z >> 4)
    if not s: return 0
    return s[0][((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]

def getd(x, y, z):
    s = section(x >> 4, y >> 4, z >> 4)
    if not s or y < 0 or y > 255: return (0, 0)
    i = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
    n = s[1][i >> 1]
    return s[0][i], ((n & 15) if i % 2 == 0 else (n >> 4))

def name(i): return ID2NAME.get(i, str(i))

def sections_keys(): return _secs.keys()

# --- walkability (pre-1.13 ids) ---
# passable (no collision) blocks
PASS = set([0, 6, 8, 9, 10, 11, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 51, 55, 59, 63, 65, 66, 68, 69, 70, 72, 75, 76, 77, 83, 90, 104, 105, 106, 115, 119, 131, 132, 141, 142, 143, 147, 148, 157, 171, 175, 176, 177, 207, 209])
# doors (vanilla) are passable for routing (they can be opened); mod doors too
DOORS = set([64, 71, 193, 194, 195, 196, 197, 96, 167, 107, 183, 184, 185, 186, 187])
CLIMB = set([65, 106])  # ladder, vine
LIQUID = set([8, 9, 10, 11])
DANGER = set([10, 11, 51, 81])
# mod blocks: decide below (filled by caller): left2mine fake walls etc
MODPASS = set()
for n, i in reg.items():
    if n.startswith('left2mine:') or n.startswith('customnpcs:'):
        if n in ('left2mine:fakewall', 'left2mine:lightgraywalls', 'left2mine:lightgraypole', 'left2mine:saferoomdoor', 'left2mine:gamedoor1', 'left2mine:gamedoorsaferoom', 'customnpcs:npcscripteddoor', 'customnpcs:npcborder'):
            continue
        MODPASS.add(i)
DOORS |= set(reg[n] for n in ('left2mine:saferoomdoor', 'left2mine:gamedoor1', 'left2mine:gamedoorsaferoom') if n in reg)
SLABS = set([44, 126, 182, 205])
# blocks whose top half-height (slab bottom): treat as full for support
def passable(i):
    return i in PASS or i in MODPASS or i in DOORS

def solid_support(i):
    if i == 0 or i in LIQUID or i in DANGER: return False
    if i in PASS or i in MODPASS: return False
    if i in DOORS: return False
    return True
