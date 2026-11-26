"""Aperçu de 3 polices sur les menus du navigateur (boutique, page de catégorie, profil).
usage : python apercu_polices.py <chemin gen_nav.py> <pack.zip> <sortie.png>"""
import importlib.util, io, json, sys, zipfile
from PIL import Image, ImageDraw, ImageFont

spec = importlib.util.spec_from_file_location("gen_nav", sys.argv[1])
G = importlib.util.module_from_spec(spec)
spec.loader.exec_module(G)
S = 4  # échelle d'interface simulée (GUI scale 4, écran 1080p en auto)

calls = []
orig = G.draw_text
def rec(img, x, y, s, color, oy=0):
    calls.append((id(img), x, y, s, color, oy))
G.draw_text = rec

jar = zipfile.ZipFile(G.JAR)
v54 = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/gui/container/generic_54.png"))).convert("RGBA")
pack = zipfile.ZipFile(sys.argv[2])
lob = Image.open(io.BytesIO(pack.read(f"assets/{G.NS}/textures/font/lobby/bg_profile.png"))).convert("RGBA")
G.INV_IMG = lob.crop((0, 17 + 18 * 6, 176, 222))
ic = G.load_icons(pack)
calls.clear()
bases, overlays, layouts = G.build_menus(v54, ic)
byimg = {}
for c in calls:
    byimg.setdefault(c[0], []).append(c[1:])

# ---- police Minecraft (ascii.png)
mcimg = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
acc = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/font/accented.png"))).convert("RGBA")
dj = json.loads(jar.read("assets/minecraft/font/include/default.json"))
MC = {}
for p in dj["providers"]:
    f = p.get("file", "")
    if f.endswith("ascii.png"):
        im, ch = mcimg, 8
    elif f.endswith("accented.png"):
        im, ch = acc, 12
    else:
        continue
    cw = im.width // len(p["chars"][0])
    for r, line in enumerate(p["chars"]):
        for k, c in enumerate(line):
            if c in MC or c == "\u0000":
                continue
            cell = im.crop((k * cw, r * ch, (k + 1) * cw, (r + 1) * ch))
            bb = cell.getchannel("A").getbbox()
            w = bb[2] if bb else 0
            MC[c] = (cell, w, p["ascent"], ch)

def mc_width(s):
    return sum(3 if c == " " else (MC[c][1] + 1 if c in MC else 6) for c in s) - 1

def mc_draw(dst, x, y, s, col):
    # haut des majuscules en y-1 (7 px de haut au lieu de 5)
    for c in s:
        if c == " ":
            x += 3
            continue
        if c not in MC:
            x += 6
            continue
        cell, w, asc, ch = MC[c]
        top = (y - 1 + 7) - asc  # ligne de base commune
        g = cell.copy()
        px = g.load()
        for i in range(g.width):
            for j in range(g.height):
                if px[i, j][3]:
                    px[i, j] = col[:3] + (px[i, j][3],)
        g = g.resize((g.width * S, g.height * S), Image.NEAREST)
        dst.alpha_composite(g, (x * S, top * S))
        x += w + 1

# ---- police lisse (Aileron, CC0, livrée avec Pillow), dessinée à 4 texels par pixel d'interface
import unicodedata
def make_smooth(CAP):
    AIL = ImageFont.load_default(int(CAP * S / 0.70))
    asc = AIL.getbbox("H")[1]
    capH = AIL.getbbox("H")[3] - asc
    def parts(c):
        if c == "⛃": return None, None
        dec = unicodedata.normalize("NFD", c)
        return dec[0], (dec[1] if len(dec) > 1 else None)
    def cw(c):
        b, a = parts(c)
        if b is None: return capH + S * 0.6
        return AIL.getlength(b) + S * 0.35
    def width(s):
        return (sum(cw(c) for c in s) - S * 0.35) / S
    def draw(dst, x, y, s, col):
        d = ImageDraw.Draw(dst)
        X = x * S
        top = (y + 2.5 - CAP / 2) * S
        for c in s:
            b, a = parts(c)
            if b is None:
                r = capH / 2
                d.ellipse([X, top, X + 2 * r, top + 2 * r], outline=col, width=max(2, S // 2))
                d.ellipse([X + r * 0.6, top + r * 0.6, X + r * 1.4, top + r * 1.4], fill=col)
                X += cw(c); continue
            d.text((X, top - asc), b, font=AIL, fill=col)
            if a:
                w = AIL.getlength(b); m = X + w / 2; t = top - S * 1.4; lw = max(2, S // 2)
                if a == "́": d.line([m - S * .4, t + S * 1.0, m + S * .6, t], fill=col, width=lw)
                elif a == "̀": d.line([m - S * .6, t, m + S * .4, t + S * 1.0], fill=col, width=lw)
                elif a == "̂": d.line([m - S * .8, t + S * 1.0, m, t, m + S * .8, t + S * 1.0], fill=col, width=lw, joint="curve")
            X += cw(c)
    return width, draw
w5, d5 = make_smooth(5)
w6, d6 = make_smooth(6)

def f5_draw(dst, x, y, s, col):
    lay = Image.new("RGBA", (dst.width // S, dst.height // S), (0, 0, 0, 0))
    orig(lay, x, y, s, col)
    dst.alpha_composite(lay.resize(dst.size, Image.NEAREST))

STYLES = [("A - actuelle (petites capitales 5 px)", lambda s: G.text_width(s) - 1, f5_draw),
          ("B - lisse, meme taille (5 px)", w5, d5),
          ("C - lisse, un peu plus grande (6 px)", w6, d6)]

def render(mid, style):
    name, width, draw = style
    lay = layouts[mid]
    rows = lay["rows"]
    T = G.T
    h = 114 + 18 * rows
    H = h + T
    img = Image.new("RGBA", (176, H), (60, 66, 60, 255))
    chest = Image.new("RGBA", (176, h), (0, 0, 0, 0))
    chest.alpha_composite(v54.crop((0, 0, 176, 17 + 18 * rows)), (0, 0))
    chest.alpha_composite(v54.crop((0, 126, 176, 222)), (0, 17 + 18 * rows))
    img.alpha_composite(chest, (0, T))
    img.alpha_composite(bases[mid].crop((0, 0, 176, H)), (0, 0))
    ovs, txt = G.EX.get(mid, ([], {}))
    for ov in ovs:
        img.alpha_composite(overlays[ov].crop((0, 0, 176, H)), (0, 0))
    big = img.resize((176 * S, H * S), Image.NEAREST)
    # libellés cuits dans le fond : on les redessine (le fond n'en a plus, draw_text était intercepté)
    for (x, y, s, col, oy) in byimg.get(id(bases[mid]), []):
        # Canvas.text a déjà aligné avec la largeur F5 : on réaligne au centre pour les autres polices
        wf = G.text_width(s) - 1
        w = width(s)
        draw(big, round(x + (wf - w) / 2) if draw is not f5_draw else x, y + oy, s, col)
    for tid, s in txt.items():
        x, y, al, mx = lay["texts"][tid]
        s = G.norm(s)
        w = width(s)
        x0 = x - w / 2 if al == "c" else (x - w if al == "d" else x)
        draw(big, round(x0), y + T, s, G.TXT)
    return big

menus = ["boutique", "boutique_cat", "profil"]
tiles = [[render(m, st) for st in STYLES] for m in menus]
cw = max(t.width for row in tiles for t in row) + 30
ch = max(t.height for row in tiles for t in row) + 50
sheet = Image.new("RGBA", (cw * 3 + 20, ch * len(menus) + 20), (30, 32, 36, 255))
d = ImageDraw.Draw(sheet)
F = ImageFont.load_default(26)
for j, row in enumerate(tiles):
    for i, t in enumerate(row):
        x, y = 20 + i * cw, 20 + j * ch
        d.text((x, y), STYLES[i][0], fill=(235, 235, 235, 255), font=F)
        sheet.alpha_composite(t, (x, y + 40))
sheet.convert("RGB").save(sys.argv[3])
print("ok", sheet.size, [m for m in menus])
