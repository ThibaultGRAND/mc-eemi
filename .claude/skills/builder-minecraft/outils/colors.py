"""Couleur moyenne d'un bloc d'apres les textures du jar client 26.3 (cache json)."""
import zipfile, io, json, os, hashlib
from PIL import Image
JAR = os.path.expandvars(r'%APPDATA%\.minecraft\versions\26.3\26.3.jar')
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out', 'colors.json')
DYES = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan',
        'purple', 'blue', 'brown', 'green', 'red', 'black']
OVR = {'grass_block': (96, 150, 58), 'grass': (96, 150, 58), 'water': (50, 90, 200), 'flowing_water': (50, 90, 200),
       'lava': (220, 100, 20), 'short_grass': (90, 140, 50), 'tall_grass': (90, 140, 50), 'tallgrass': (90, 140, 50),
       'vine': (70, 120, 40), 'lily_pad': (40, 120, 40), 'barrier': (255, 0, 255), 'light': (255, 255, 0)}
LEAF = (60, 120, 40)
_jar = None; _tex = None; _cache = None

def _avg(path):
    try:
        im = Image.open(io.BytesIO(_jar.read(path))).convert('RGBA')
    except KeyError:
        return None
    im = im.crop((0, 0, im.width, min(im.height, im.width)))
    px = [p for p in im.getdata() if p[3] > 128]
    if not px: return None
    n = len(px)
    return tuple(sum(p[k] for p in px) // n for k in range(3))

def _find(n):
    pre = 'assets/minecraft/textures/block/'
    if n.startswith('waxed_'):
        n = n[len('waxed_'):]
    n = {'snow_block': 'snow', 'smooth_quartz': 'quartz_block_bottom', 'smooth_quartz_slab': 'quartz_block_bottom',
         'smooth_quartz_stairs': 'quartz_block_bottom'}.get(n, n)
    cands = [n]
    if n.endswith('_carpet'):
        cands.append(n[:-len('_carpet')] + '_wool')
    for suf in ('_stairs', '_slab', '_wall', '_fence_gate', '_fence', '_pressure_plate', '_button', '_wall_sign',
                '_sign', '_hanging_sign', '_carpet', '_pane', '_wall_hanging_sign'):
        if n.endswith(suf):
            b = n[:-len(suf)]
            cands += [b, b + 's', b + '_planks', b + '_block', b.replace('brick', 'bricks')]
    if n.startswith('stripped_') or n.endswith('_wood'):
        cands.append(n.replace('_wood', '_log'))
    if n.endswith('_wood') or n.endswith('_hyphae'):
        cands.append(n.replace('_wood', '_log').replace('_hyphae', '_stem'))
    if n == 'stained_glass_pane': cands.append('glass')
    out = []
    for c in cands:
        out += [c + '_top', c, c + '_side', c + '_front', c + '_0']
    for c in out:
        if pre + c + '.png' in _tex: return pre + c + '.png'
    return None

def color(n):
    global _jar, _tex, _cache
    if _cache is None:
        _cache = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
    if n in _cache: return tuple(_cache[n])
    b = n.split('#')[0]; d = int(n.split('#')[1]) if '#' in n else 0
    c = OVR.get(b)
    if c is None and ('leaves' in b): c = LEAF
    if c is None:
        if _jar is None:
            _jar = zipfile.ZipFile(JAR); _tex = set(_jar.namelist())
        # noms 1.12
        m = {'stonebrick': ['stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks', 'chiseled_stone_bricks'][d % 4],
             'planks': ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak'][d % 6] + '_planks',
             'wool': DYES[d] + '_wool', 'stained_hardened_clay': DYES[d] + '_terracotta', 'concrete': DYES[d] + '_concrete',
             'carpet': DYES[d] + '_wool', 'stained_glass': DYES[d] + '_stained_glass', 'hardened_clay': 'terracotta',
             'log': ['oak', 'spruce', 'birch', 'jungle'][d % 4] + '_log', 'log2': ['acacia', 'dark_oak'][d % 2] + '_log',
             'stone_slab': 'smooth_stone', 'double_stone_slab': 'smooth_stone', 'wooden_slab': 'oak_planks',
             'cobblestone_wall': 'cobblestone', 'fence': 'oak_planks', 'brick_block': 'bricks', 'web': 'cobweb',
             'quartz_block': 'quartz_block_side', 'sandstone': 'sandstone', 'stone': ['stone', 'granite', 'polished_granite', 'diorite', 'polished_diorite', 'andesite', 'polished_andesite'][d % 7],
             'nether_brick': 'nether_bricks', 'red_flower': 'poppy', 'yellow_flower': 'dandelion'}.get(b, b)
        p = _find(m)
        c = _avg(p) if p else None
        if c is None:
            h = hashlib.md5(b.encode()).digest(); c = (h[0], h[1], h[2])
    _cache[n] = list(c)
    return c

def save():
    if _cache is not None:
        json.dump(_cache, open(CACHE, 'w'))
