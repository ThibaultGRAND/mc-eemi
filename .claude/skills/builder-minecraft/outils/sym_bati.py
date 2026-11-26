import sys, os, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analyze as A
for mid in sys.argv[1:]:
    loader, players, legacy = A.MAPS[mid]
    V = loader(); W, H, L, D, N = V.W, V.H, V.L, V.data, V.names
    cat = [A.classify(n, legacy) for n in N]; fam = [A.family(n) for n in N]
    built = [c != 'air' and c != 'invis' and c not in ('veg', 'leaves', 'liquid') and not A.natural(n, legacy) for c, n in zip(cat, N)]
    idx = [i for i in range(W * H * L) if built[D[i]]]
    xs = [i % W for i in idx]; zs = [(i // W) % L for i in idx]
    cx2, cz2 = min(xs) + max(xs), min(zs) + max(zs)
    sm = random.Random(2).sample(idx, min(100000, len(idx)))
    def sym(f):
        ok = 0
        for i in sm:
            x = i % W; z = (i // W) % L; y = i // (W * L)
            X, Z = f(x, z)
            if 0 <= X < W and 0 <= Z < L and fam[D[(y * L + Z) * W + X]] == fam[D[i]] and built[D[(y * L + Z) * W + X]]: ok += 1
        return round(100 * ok / len(sm), 1)
    hx, hz = cx2 / 2, cz2 / 2
    print(mid, 'bati', len(idx), 'bbox', max(xs)-min(xs)+1, max(zs)-min(zs)+1, 'mx', sym(lambda x, z: (cx2 - x, z)), 'mz', sym(lambda x, z: (x, cz2 - z)), 'r180', sym(lambda x, z: (cx2 - x, cz2 - z)), 'r90', sym(lambda x, z: (int(round(hx - (z - hz))), int(round(hz + (x - hx))))))
