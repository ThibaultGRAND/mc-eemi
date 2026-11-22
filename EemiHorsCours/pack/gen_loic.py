"""Modèle 3D de Loïc (Hors Cours) à partir de son skin 64x64, et ajout au pack publié.

Texture dans textures/item/ : les modèles d'objets ne voient que les textures
des dossiers block/ et item/ (atlas des blocs) ; dans entity/ elle s'affichait violet et noir.

Usage : python -I gen_loic.py <pack_publie.zip> <sortie.zip>
Écrit aussi les fichiers générés dans pack/assets/ (pour garder une trace).
Le pack publié contient des ajouts absents de EemicraftPack/build_pack.py : on repart donc
toujours du zip publié et on n'ajoute que nos fichiers.
"""
import hashlib
import json
import os
import shutil
import sys
import zipfile

ICI = os.path.dirname(os.path.abspath(__file__))
SKIN = os.path.join(ICI, "..", "references", "skins", "loic.png")
NS = "assets/eemicraft"

# Parties du skin classique : (U, V, w, h, d)
PARTS = {
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "arm_r": (40, 16, 4, 12, 4),
    "arm_l": (32, 48, 4, 12, 4),
    "leg_r": (0, 16, 4, 12, 4),
    "leg_l": (16, 48, 4, 12, 4),
    "sleeve_r": (40, 32, 4, 12, 4),
    "sleeve_l": (48, 48, 4, 12, 4),
}
# Position dans le modèle (Loïc regarde vers le sud, +z ; sa droite est à l'ouest, -x)
BOXES = {
    "head": ((4, 24, 4), (12, 32, 12)),
    "body": ((4, 12, 6), (12, 24, 10)),
    "arm_r": ((0, 12, 6), (4, 24, 10)),
    "arm_l": ((12, 12, 6), (16, 24, 10)),
    "leg_r": ((4, 0, 6), (8, 12, 10)),
    "leg_l": ((8, 0, 6), (12, 12, 10)),
}


def faces(part):
    U, V, w, h, d = PARTS[part]
    q = lambda u1, v1, u2, v2: [u1 / 4, v1 / 4, u2 / 4, v2 / 4]
    return {
        "up": {"uv": q(U + d, V, U + d + w, V + d), "texture": "#skin"},
        "down": {"uv": q(U + d + w, V, U + d + 2 * w, V + d), "texture": "#skin"},
        "west": {"uv": q(U, V + d, U + d, V + d + h), "texture": "#skin"},
        "south": {"uv": q(U + d, V + d, U + d + w, V + d + h), "texture": "#skin"},
        "east": {"uv": q(U + d + w, V + d, U + 2 * d + w, V + d + h), "texture": "#skin"},
        "north": {"uv": q(U + 2 * d + w, V + d, U + 2 * d + 2 * w, V + d + h), "texture": "#skin"},
    }


def element(box, part, inflate=0.0, rot=None):
    (x1, y1, z1), (x2, y2, z2) = box
    e = {
        "from": [x1 - inflate, y1 - inflate, z1 - inflate],
        "to": [x2 + inflate, y2 + inflate, z2 + inflate],
        "faces": faces(part),
    }
    if rot:
        e["rotation"] = rot
    return e


def model(swing):
    """swing : angle des jambes/bras (0 = debout)."""
    def rot(origin, angle):
        return {"origin": origin, "axis": "x", "angle": angle} if angle else None

    els = [
        element(BOXES["head"], "head"),
        element(BOXES["body"], "body"),
        element(BOXES["leg_r"], "leg_r", rot=rot([6, 12, 8], swing)),
        element(BOXES["leg_l"], "leg_l", rot=rot([10, 12, 8], -swing)),
        element(BOXES["arm_r"], "arm_r", rot=rot([2, 22, 8], -swing)),
        element(BOXES["arm_l"], "arm_l", rot=rot([14, 22, 8], swing)),
        element(BOXES["arm_r"], "sleeve_r", 0.25, rot([2, 22, 8], -swing)),
        element(BOXES["arm_l"], "sleeve_l", 0.25, rot([14, 22, 8], swing)),
    ]
    return {
        "textures": {"skin": "eemicraft:item/horscours/loic", "particle": "eemicraft:item/horscours/loic"},
        "elements": els,
    }


def png16(pal, rows):
    """PNG RGBA à partir d'une grille de caractères (palette : car -> (r, g, b, a))."""
    import struct
    import zlib
    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(bytes(pal.get(c, (0, 0, 0, 0))) for c in row)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    w, h = len(rows[0]), len(rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png(path):
    """Lecteur PNG minimal (8 bits, RGB/RGBA, non entrelacé) -> (w, h, pixels[y][x] = (r,g,b,a))."""
    import struct
    import zlib
    d = open(path, "rb").read()
    pos, idat = 8, b""
    while pos < len(d):
        n, t = struct.unpack(">I4s", d[pos:pos + 8])
        c = d[pos + 8:pos + 8 + n]
        pos += 12 + n
        if t == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", c[:10])
        elif t == b"IDAT":
            idat += c
    raw = zlib.decompress(idat)
    bpp = {6: 4, 2: 3}[ct]
    st, out, prev, i = w * bpp, [], bytearray(w * bpp), 0
    for _ in range(h):
        f = raw[i]
        i += 1
        line = bytearray(raw[i:i + st])
        i += st
        for x in range(st):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out.append([tuple(line[x * bpp:x * bpp + bpp]) + ((255,) if bpp == 3 else ()) for x in range(w)])
        prev = line
    return w, h, out


def screamer_face(scale=16):
    """Visage de Loïc (face avant de la tête + calque du chapeau) agrandi, un peu assombri sur les bords."""
    _, _, px = read_png(SKIN)
    face = []
    for y in range(8):
        row = []
        for x in range(8):
            base, hat = px[8 + y][8 + x], px[8 + y][40 + x]
            row.append(hat if hat[3] > 0 else base)
        face.append(row)
    pal, rows, keys = {}, [], {}
    size = 8 * scale
    for Y in range(size):
        line = ""
        for X in range(size):
            r, g, b, a = face[Y // scale][X // scale]
            # vignette : plus sombre vers les bords, teinte rouge
            dx, dy = (X - size / 2) / (size / 2), (Y - size / 2) / (size / 2)
            k = max(0.35, 1 - 0.55 * (dx * dx + dy * dy))
            col = (min(255, int(r * k * 1.08)), int(g * k * 0.85), int(b * k * 0.85), a)
            if col not in keys:
                keys[col] = chr(0x100 + len(keys))
                pal[keys[col]] = col
            line += keys[col]
        rows.append(line)
    return png16(pal, rows)


def png_rgba(w, h, px):
    """px[y][x] = (r, g, b, a)."""
    import struct
    import zlib
    raw = b"".join(b"\x00" + b"".join(bytes(c) for c in row) for row in px)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def blank(w=16, h=16):
    return [[(0, 0, 0, 0)] * w for _ in range(h)]


def stick(px, A, B, radius_at, color_at, outline=(22, 22, 28, 255)):
    """Objet allongé en diagonale (lampe, craie) : couleur par (t le long de l'axe, r de côté)."""
    vx, vy = B[0] - A[0], B[1] - A[1]
    L2 = vx * vx + vy * vy
    L = L2 ** 0.5
    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5 - A[0], y + 0.5 - A[1]
            t = (cx * vx + cy * vy) / L2
            r = (cx * vy - cy * vx) / L  # >0 = côté éclairé
            rad = radius_at(t)
            if rad is None:
                continue
            if abs(r) <= rad:
                px[y][x] = color_at(t, r / rad)
            elif abs(r) <= rad + 0.85 and px[y][x][3] < 200:
                px[y][x] = outline


def item_lampe():
    px = blank()
    A, B = (3.0, 12.6), (11.2, 4.4)
    vx, vy = B[0] - A[0], B[1] - A[1]
    L2 = vx * vx + vy * vy
    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5 - A[0], y + 0.5 - A[1]
            t = (cx * vx + cy * vy) / L2
            r = abs(cx * vy - cy * vx) / L2 ** 0.5
            if 1.02 < t < 1.55 and r < 2.2 + (t - 1) * 5:
                a = int(150 * (1.55 - t) / 0.53)
                px[y][x] = (255, 236, 150, max(30, a))

    def rad(t):
        if t < -0.02 or t > 1.0:
            return None
        if t < 0.62:
            return 1.45
        if t < 0.74:
            return 1.45 + (t - 0.62) / 0.12 * 0.95
        return 2.4

    def col(t, r):
        if t > 0.93:
            return (255, 252, 225, 255) if r > -0.1 else (255, 214, 90, 255)
        if t > 0.62:
            return (190, 196, 210, 255) if r > 0.45 else (70, 74, 86, 255) if r < -0.45 else (120, 126, 140, 255)
        if 0.46 < t < 0.56 and r > -0.2:
            return (225, 60, 45, 255)
        if t < 0.42 and int(t * 16) % 2 == 0:
            return (40, 42, 50, 255) if r < 0.4 else (95, 98, 110, 255)
        return (150, 154, 168, 255) if r > 0.45 else (48, 50, 58, 255) if r < -0.45 else (78, 80, 92, 255)
    stick(px, A, B, rad, col)
    return png_rgba(16, 16, px)


def item_craie():
    px = blank()
    A, B = (3.2, 12.8), (12.6, 3.4)

    def rad(t):
        return None if t < 0 or t > 1 else 1.25

    def col(t, r):
        if t > 0.9:
            return (196, 196, 190, 255) if r < 0 else (226, 226, 220, 255)
        return (255, 255, 250, 255) if r > 0.3 else (205, 205, 198, 255) if r < -0.4 else (238, 238, 232, 255)
    stick(px, A, B, rad, col, outline=(110, 110, 108, 255))
    for (x, y, a) in ((14, 2, 160), (13, 1, 110), (15, 4, 90), (12, 1, 70)):
        px[y][x] = (240, 240, 235, a)
    return png_rgba(16, 16, px)


def item_fusible():
    px = blank()
    K = (35, 35, 42, 255)
    for y in range(1, 15):
        for x in range(4, 12):
            if x in (4, 11) or y in (1, 14):
                px[y][x] = K
                continue
            if y <= 3 or y >= 12:
                c = (240, 208, 120, 255) if x == 6 else (140, 100, 42, 255) if x == 10 else (205, 165, 72, 255)
                if y in (3, 12):
                    c = tuple(int(v * 0.8) for v in c[:3]) + (255,)
                px[y][x] = c
            else:
                px[y][x] = (232, 248, 255, 220) if x == 6 else (165, 210, 230, 150)
    for (x, y) in [(7, 4), (8, 5), (7, 6), (8, 7), (7, 8), (8, 9), (7, 10), (8, 11)]:
        px[y][x] = (255, 140, 30, 255)
    for (x, y) in ((8, 6), (7, 9)):
        px[y][x] = (255, 210, 130, 230)
    return png_rgba(16, 16, px)


def item_pile():
    px = blank()
    K = (22, 22, 28, 255)
    for y in range(1, 3):
        for x in range(6, 10):
            px[y][x] = K if x in (6, 9) or y == 1 else (215, 140, 70, 255)
    for y in range(3, 15):
        for x in range(4, 12):
            if x in (4, 11) or y in (3, 14):
                px[y][x] = K
                continue
            if y == 4:
                c = (225, 225, 232, 255)
            elif y == 13:
                c = (170, 170, 178, 255)
            elif 6 <= y <= 10:
                c = (50, 190, 175, 255)
            else:
                c = (38, 42, 52, 255)
            if x == 5:
                c = tuple(min(255, v + 55) for v in c[:3]) + (255,)
            if x == 10:
                c = tuple(int(v * 0.7) for v in c[:3]) + (255,)
            px[y][x] = c
    for (x, y) in ((7, 7), (6, 8), (7, 8), (8, 8), (7, 9)):
        px[y][x] = (255, 255, 255, 255)
    return png_rgba(16, 16, px)


def item_pince():
    """Pince coupante : poignées rouges écartées en bas, pivot, deux mâchoires d'acier en haut à droite."""
    rows = [
        "..........KK....",
        ".........KlsK...",
        "........KlsdKK..",
        ".......KlsdKlsK.",
        "......KlsdKlsdK.",
        ".....KKsdKlsdK..",
        "....KlpPpsdKK...",
        "...KrRPPpdK.....",
        "..KrRRKpKK......",
        ".KrRRKKrRK......",
        "KrRRK.KrRRK.....",
        "KrRK..KrRRK.....",
        "KRRK...KrRRK....",
        "KKK....KrRRK....",
        ".......KRRRK....",
        "........KKK.....",
    ]
    pal = {"K": (22, 22, 28, 255), "l": (215, 220, 228, 255), "s": (160, 166, 178, 255), "d": (96, 100, 110, 255),
           "p": (120, 124, 134, 255), "P": (190, 194, 204, 255), "r": (236, 96, 86, 255), "R": (190, 34, 34, 255)}
    px = blank()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in pal:
                px[y][x] = pal[c]
    return png_rgba(16, 16, px)


def item_code():
    """Post-it jaune griffonné (objet « code » : on ne le garde pas, il sert d'icône)."""
    px = blank()
    for y in range(2, 14):
        for x in range(2, 14):
            px[y][x] = (250, 226, 90, 255) if (x + y) % 7 else (236, 206, 70, 255)
    for x in range(4, 12):
        px[5][x] = (40, 40, 60, 255) if x % 3 else px[5][x]
        px[8][x] = (40, 40, 60, 255) if x % 2 else px[8][x]
        px[11][x - 1] = (180, 30, 30, 255) if x % 4 else px[11][x - 1]
    for y in range(2, 14):
        px[y][13] = (210, 180, 60, 255)
    return png_rgba(16, 16, px)


OBJETS = {"lampe": item_lampe, "craie": item_craie, "fusible": item_fusible, "pile": item_pile,
          "pince": item_pince, "postit": item_code}


def screamer_frames():
    """Visage rouge (flash) et parasites (bruit) pour le screamer."""
    import random
    rng = random.Random(7)
    _, _, px = read_png(SKIN)
    face = [[(px[8 + y][40 + x] if px[8 + y][40 + x][3] > 0 else px[8 + y][8 + x]) for x in range(8)] for y in range(8)]
    S = 128
    red, noise = [], []
    for Y in range(S):
        rrow, nrow = [], []
        for X in range(S):
            r, g, b, a = face[Y * 8 // S][X * 8 // S]
            rrow.append((min(255, int(r * 0.6 + 140)), int(g * 0.25), int(b * 0.25), 255))
            v = rng.randint(0, 255)
            nrow.append((int(v * 0.6 + r * 0.4), int(v * 0.6 + g * 0.4), int(v * 0.6 + b * 0.4), 255))
        red.append(rrow)
        noise.append(nrow)
    return png_rgba(S, S, red), png_rgba(S, S, noise)


def main():
    src, out = sys.argv[1], sys.argv[2]
    files = {}
    files[f"{NS}/textures/item/horscours/loic.png"] = open(SKIN, "rb").read()
    for name, swing in (("loic_idle", 0), ("loic_walk1", 22.5), ("loic_walk2", -22.5)):
        files[f"{NS}/models/horscours/{name}.json"] = json.dumps(model(swing), indent=1).encode()
        files[f"{NS}/items/horscours/{name}.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"eemicraft:horscours/{name}"}}, indent=1).encode()
    # Screamer : grosse tête de Loïc affichée en titre (police bitmap, caractère U+E000)
    files[f"{NS}/textures/font/horscours/loic_face.png"] = screamer_face()
    red, noise = screamer_frames()
    files[f"{NS}/textures/font/horscours/loic_rouge.png"] = red
    files[f"{NS}/textures/font/horscours/parasites.png"] = noise
    prov = []
    for ch, h in (("", 110), ("", 40), ("", 70), ("", 110), ("", 175)):
        prov.append({"type": "bitmap", "file": "eemicraft:font/horscours/loic_face.png", "height": h,
                     "ascent": int(h * 0.62), "chars": [ch]})
    prov.append({"type": "bitmap", "file": "eemicraft:font/horscours/parasites.png", "height": 175,
                 "ascent": int(175 * 0.62), "chars": [""]})
    prov.append({"type": "bitmap", "file": "eemicraft:font/horscours/loic_rouge.png", "height": 190,
                 "ascent": int(190 * 0.62), "chars": [""]})
    prov.append({"type": "space", "advances": {"": 10, "": -10}})
    files[f"{NS}/font/horscours_screamer.json"] = json.dumps({"providers": prov}, indent=1).encode()
    # Objets du jeu (pixel art 16x16)
    for name, fn in OBJETS.items():
        files[f"{NS}/textures/item/horscours/{name}.png"] = fn()
        files[f"{NS}/models/horscours/{name}.json"] = json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"eemicraft:item/horscours/{name}"}},
            indent=1).encode()
        files[f"{NS}/items/horscours/{name}.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"eemicraft:horscours/{name}"}}, indent=1).encode()

    trace = os.path.join(ICI, "assets_generes")
    shutil.rmtree(trace, ignore_errors=True)
    for path, data in files.items():
        p = os.path.join(trace, *path.split("/"))
        os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "wb").write(data)

    zin = zipfile.ZipFile(src)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            if info.filename in files or "/horscours/" in info.filename:
                continue
            zout.writestr(info, zin.read(info.filename))
        for path in sorted(files):
            zi = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(zi, files[path])
    print("SHA-1", hashlib.sha1(open(out, "rb").read()).hexdigest())


if __name__ == "__main__":
    main()
