"""Localise les pans coplanaires longs (meme definition qu'analyze.py). Usage : runs.py <schem> <x0> <y0> <z0> [seuil]"""
import sys
sys.path.insert(0, r'C:\Users\esteb\EEMICRAFT\.claude\skills\builder-minecraft\outils')
import vol
import analyze as A

V = vol.load_schem(sys.argv[1], 'x')
ox, oy, oz = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
thr = int(sys.argv[5]) if len(sys.argv) > 5 else 13
W, H, L, D, N = V.W, V.H, V.L, V.data, V.names
cat = [A.classify(n, False) for n in N]
full = [c == 'full' for c in cat]
nat = [A.natural(n, False) for n in N]
wallb = [f and not t for f, t in zip(full, nat)]
smallpass = ('det:tapis', 'det:bouton/plaque', 'det:eclairage', 'det:panneau/tete/banniere/pot')
passable = [c in ('air', 'veg') or c in smallpass or (c == 'invis' and N[i] != 'barrier') for i, c in enumerate(cat)]
sx, sz, sy = 1, W, W * L
for (ax, s) in (('z', -1), ('z', 1), ('x', -1), ('x', 1)):
    dn = (sz if ax == 'z' else sx) * s
    TL = W if ax == 'z' else L
    AL = L if ax == 'z' else W
    for y in range(1, H - 1):
        for a in range(AL):
            if not (0 <= a + s < AL): continue
            run = 0; start = None
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
                    if run == 0: start = t
                    run += 1
                else:
                    if run >= thr:
                        if ax == 'z':
                            print('face %s%+d y=%d z=%d x=%d..%d (%d) %s' % (ax, s, y + oy, a + oz, start + ox, start + run - 1 + ox, run, N[D[y * sy + a * sz + start]]))
                        else:
                            print('face %s%+d y=%d x=%d z=%d..%d (%d) %s' % (ax, s, y + oy, a + ox, start + oz, start + run - 1 + oz, run, N[D[y * sy + start * sz + a]]))
                    run = 0
