import struct, zlib, gzip, os

class R:
    def __init__(s, b): s.b = b; s.i = 0
    def r(s, n):
        v = s.b[s.i:s.i+n]; s.i += n; return v
    def u(s, f):
        n = struct.calcsize(f); v = struct.unpack_from(f, s.b, s.i); s.i += n; return v[0]

def rstr(r):
    n = r.u('>H'); return r.r(n).decode('utf-8', 'replace')

def payload(r, t):
    if t == 1: return r.u('>b')
    if t == 2: return r.u('>h')
    if t == 3: return r.u('>i')
    if t == 4: return r.u('>q')
    if t == 5: return r.u('>f')
    if t == 6: return r.u('>d')
    if t == 7:
        n = r.u('>i'); return r.r(n)
    if t == 8: return rstr(r)
    if t == 9:
        et = r.u('>b'); n = r.u('>i'); return [payload(r, et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = r.u('>b')
            if tt == 0: return d
            k = rstr(r); d[k] = payload(r, tt)
    if t == 11:
        n = r.u('>i'); return list(struct.unpack_from('>%di' % n, r.r(4*n)))
    if t == 12:
        n = r.u('>i'); return list(struct.unpack_from('>%dq' % n, r.r(8*n)))
    raise ValueError('tag %d' % t)

def parse(b):
    r = R(b); t = r.u('>b'); rstr(r); return payload(r, t)

def load_file(p):
    b = open(p, 'rb').read()
    try: b = gzip.decompress(b)
    except Exception: pass
    return parse(b)

def region_chunks(p):
    f = open(p, 'rb').read()
    for i in range(1024):
        off = int.from_bytes(f[i*4:i*4+3], 'big'); cnt = f[i*4+3]
        if off == 0: continue
        o = off*4096; ln = int.from_bytes(f[o:o+4], 'big'); comp = f[o+4]
        data = f[o+5:o+4+ln]
        if comp == 2: data = zlib.decompress(data)
        elif comp == 1: data = gzip.decompress(data)
        yield parse(data)

def all_chunks(world):
    rd = os.path.join(world, 'region')
    for fn in sorted(os.listdir(rd)):
        if fn.endswith('.mca'):
            for c in region_chunks(os.path.join(rd, fn)):
                yield c['Level']
