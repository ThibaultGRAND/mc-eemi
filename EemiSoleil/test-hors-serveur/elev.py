"""Elevations (vues a hauteur d'oeil, premier bloc non-air le long d'un axe), couleurs du jar client.
Usage : elev.py <schem> <x0> <y0> <z0> <sortie_prefixe>"""
import sys
sys.path.insert(0, r'C:\Users\esteb\EEMICRAFT\.claude\skills\builder-minecraft\outils')
import vol, colors
from PIL import Image

V = vol.load_schem(sys.argv[1], 'x')
ox, oy, oz = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
out = sys.argv[5]
W, H, L, D, N = V.W, V.H, V.L, V.data, V.names
AIR = {i for i, n in enumerate(N) if n.split('#')[0] in ('air', 'cave_air', 'void_air', 'light', 'barrier')}
col = [colors.color(n.split('#')[0]) if i not in AIR else None for i, n in enumerate(N)]

def g(x, y, z):
    x -= ox; y -= oy; z -= oz
    if not (0 <= x < W and 0 <= y < H and 0 <= z < L): return 0
    return D[y * W * L + z * W + x]

def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)

def view(name, us, ys, ray, scale=6):
    img = Image.new('RGB', (len(us) * scale, len(ys) * scale), (140, 180, 230))
    hits = {}
    for i, u in enumerate(us):
        for j, y in enumerate(ys):
            for depth, (x, z) in enumerate(ray(u)):
                p = g(x, y, z)
                if p and p not in AIR and col[p]:
                    hits[(i, j)] = (depth, p); break
    import collections
    base = collections.Counter(d for d, _ in hits.values()).most_common(1)[0][0] if hits else 0
    for i, u in enumerate(us):
        for j, y in enumerate(ys):
            if (i, j) in hits:
                    depth, p = hits[(i, j)]
                    k = 1 + 0.07 * max(-6, min(4, base - depth))
                    nm = N[p]
                    if '_stairs' in nm or '_slab' in nm: k *= 0.85
                    c = shade(col[p], k)
                    for a in range(scale):
                        for b in range(scale):
                            img.putpixel((i * scale + a, (len(ys) - 1 - j) * scale + b), c)
    img.save(out + '_' + name + '.png')

ys = list(range(99, 140))
# 1. depuis la ligne de depart vers l'arbre (vue du joueur, regard +z)
view('vers_arbre', list(range(-21, 22)), ys, lambda x: [(x, z) for z in range(2, 96)])
# 2. mur est vu de la piste (x croissant), z de -16 a 95
view('mur_est', list(range(-16, 96)), ys, lambda z: [(x, z) for x in range(0, 24)], scale=4)
# 3. dortoir : vue depuis l'apparition vers le nord (regard -z)
view('dortoir_nord', list(range(-18, 19)), list(range(99, 118)), lambda x: [(x, z) for z in range(-61, -90, -1)])
# 4. dortoir : coupe vue vers l'est depuis x = 0
view('dortoir_est', list(range(-82, -37)), list(range(99, 118)), lambda z: [(x, z) for x in range(1, 20)], scale=5)
colors.save()
