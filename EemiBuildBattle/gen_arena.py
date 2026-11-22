"""Génère le datapack de l'arène Build Battle d'EEMICRAFT (monde « buildbattle ») et le bloc arenas.yml.

8 terrains de 25x25 (sol à Y 64, hauteur 30), 4 colonnes x 2 rangées, chacun avec sa couleur, autour
d'une avenue centrale avec une place à fontaine ; jardins, arbres et lanternes entre les terrains.
Lancer : python gen_arena.py -> eemibb/ (datapack) + arena.yml (section à mettre dans arenas.yml).
"""
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
Y = 64            # sol des terrains et du terrain alentour
SIZE = 25         # intérieur d'un terrain
HEIGHT = 30       # hauteur constructible
COLS = [-70, -35, 0, 35]          # x du coin de chaque colonne
ROWS = [-40, 15]                  # z du coin de chaque rangée (avenue et place entre les deux)
COLORS = ["red", "orange", "yellow", "lime", "light_blue", "blue", "purple", "pink"]
X1, X2, Z1, Z2 = COLS[0] - 14, COLS[-1] + SIZE + 13, ROWS[0] - 12, ROWS[1] + SIZE + 11
CX, CZ = (COLS[0] + COLS[-1] + SIZE - 1) // 2, 0   # centre de la place

rnd = random.Random(2026)
cmds = []


def fill(x1, y1, z1, x2, y2, z2, block, mode=""):
    cmds.append(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}{(' ' + mode) if mode else ''}")


def setb(x, y, z, block):
    cmds.append(f"setblock {x} {y} {z} {block}")


plots = []
for r, z0 in enumerate(ROWS):
    for c, x0 in enumerate(COLS):
        plots.append((x0, z0, COLORS[r * 4 + c]))


def in_plot_zone(x, z, margin):
    for x0, z0, _ in plots:
        if x0 - margin <= x <= x0 + SIZE - 1 + margin and z0 - margin <= z <= z0 + SIZE - 1 + margin:
            return True
    return False


# --- nettoyage et sol (pelouse sur terre et pierre)
fill(X1, Y + 1, Z1, X2, Y + 40, Z2, "air")
fill(X1, Y - 6, Z1, X2, Y - 4, Z2, "stone")
fill(X1, Y - 3, Z1, X2, Y - 1, Z2, "dirt")
fill(X1, Y, Z1, X2, Y, Z2, "grass_block")
# bordure de la zone : haie
fill(X1, Y + 1, Z1, X2, Y + 2, Z1, "oak_leaves[persistent=true]")
fill(X1, Y + 1, Z2, X2, Y + 2, Z2, "oak_leaves[persistent=true]")
fill(X1, Y + 1, Z1, X1, Y + 2, Z2, "oak_leaves[persistent=true]")
fill(X2, Y + 1, Z1, X2, Y + 2, Z2, "oak_leaves[persistent=true]")

# --- avenue centrale (entre les rangées) et allées vers chaque terrain
az1, az2 = ROWS[0] + SIZE + 3, ROWS[1] - 4
fill(X1 + 2, Y, az1, X2 - 2, Y, az2, "polished_andesite")
fill(X1 + 2, Y, az1, X2 - 2, Y, az1, "stone_bricks")
fill(X1 + 2, Y, az2, X2 - 2, Y, az2, "stone_bricks")
fill(X1 + 2, Y, (az1 + az2) // 2, X2 - 2, Y, (az1 + az2) // 2, "smooth_stone")
for x in range(X1 + 6, X2 - 4, 9):
    for z in (az1 + 1, az2 - 1):
        if abs(x - CX) <= 11:
            continue
        setb(x, Y + 1, z, "spruce_fence")
        setb(x, Y + 2, z, "spruce_fence")
        setb(x, Y + 3, z, "lantern")
for x0, z0, col in plots:
    mid = x0 + SIZE // 2
    if z0 < 0:
        fill(mid - 1, Y, z0 + SIZE + 2, mid + 1, Y, az1 - 1, "polished_andesite")
    else:
        fill(mid - 1, Y, az2 + 1, mid + 1, Y, z0 - 3, "polished_andesite")

# --- terrains : liseré de béton de la couleur, margelle de quartz, piliers lumineux aux coins
for x0, z0, col in plots:
    xa, xb, za, zb = x0, x0 + SIZE - 1, z0, z0 + SIZE - 1
    fill(xa - 1, Y, za - 1, xb + 1, Y, zb + 1, f"{col}_concrete")
    fill(xa - 2, Y, za - 2, xb + 2, Y, zb + 2, "smooth_quartz", "outline")
    fill(xa - 2, Y + 1, za - 2, xb + 2, Y + 1, zb + 2, "smooth_quartz_slab[type=bottom]", "outline")
    fill(xa, Y, za, xb, Y, zb, "oak_log")          # sol initial (le plugin le repose à chaque partie)
    for px, pz in ((xa - 2, za - 2), (xb + 2, za - 2), (xa - 2, zb + 2), (xb + 2, zb + 2)):
        fill(px, Y + 1, pz, px, Y + 4, pz, "quartz_pillar")
        setb(px, Y + 5, pz, f"{col}_stained_glass")
        setb(px, Y + 6, pz, "sea_lantern")
    # entrée : marche de quartz et deux lanternes du côté de l'avenue
    ez = zb + 2 if z0 < 0 else za - 2
    mid = x0 + SIZE // 2
    fill(mid - 1, Y + 1, ez, mid + 1, Y + 1, ez, "air")
    for dx in (-2, 2):
        setb(mid + dx, Y + 1, ez, "quartz_pillar")
        setb(mid + dx, Y + 2, ez, "lantern")

# --- place centrale : dallage en cercle, fontaine, bancs
R = 10
for x in range(CX - R, CX + R + 1):
    h = int((R * R - (x - CX) ** 2) ** 0.5)
    fill(x, Y, CZ - h, x, Y, CZ + h, "polished_andesite")
    if abs(x - CX) < R - 1:
        h2 = int(((R - 1.5) ** 2 - (x - CX) ** 2) ** 0.5) if (R - 1.5) ** 2 >= (x - CX) ** 2 else -1
        if h2 >= 0:
            fill(x, Y, CZ - h2, x, Y, CZ + h2, "smooth_stone" if x % 2 == 0 else "polished_diorite")
# bassin
fill(CX - 4, Y + 1, CZ - 4, CX + 4, Y + 1, CZ + 4, "quartz_bricks")
fill(CX - 4, Y + 1, CZ - 4, CX - 4, Y + 1, CZ - 4, "polished_andesite")
for x in range(CX - 3, CX + 4):
    h = int((9 - (x - CX) ** 2) ** 0.5)
    fill(x, Y, CZ - h, x, Y, CZ + h, "prismarine_bricks")
    fill(x, Y + 1, CZ - h, x, Y + 1, CZ + h, "water")
for dx in (-4, 4):
    for dz in (-4, 4):
        setb(CX + dx, Y + 1, CZ + dz, "air")
fill(CX, Y + 1, CZ, CX, Y + 4, CZ, "quartz_pillar")
setb(CX, Y + 5, CZ, "water")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    setb(CX + dx, Y + 4, CZ + dz, "quartz_slab[type=top]")
# bancs autour de la place
for dx, dz, facing in ((0, 8, "north"), (0, -8, "south"), (8, 0, "west"), (-8, 0, "east")):
    for o in (-1, 0, 1):
        x = CX + dx + (o if dz else 0)
        z = CZ + dz + (o if dx else 0)
        setb(x, Y + 1, z, f"spruce_stairs[facing={facing}]")
# lampadaires de la place
for dx, dz in ((7, 7), (-7, 7), (7, -7), (-7, -7)):
    fill(CX + dx, Y + 1, CZ + dz, CX + dx, Y + 3, CZ + dz, "dark_oak_fence")
    setb(CX + dx, Y + 4, CZ + dz, "lantern")
# massifs de fleurs aux quatre coins de la place
for dx, dz in ((10, 10), (-10, 10), (10, -10), (-10, -10)):
    fill(CX + dx - 1, Y, CZ + dz - 1, CX + dx + 1, Y, CZ + dz + 1, "moss_block")
    for ox in (-1, 0, 1):
        for oz in (-1, 0, 1):
            setb(CX + dx + ox, Y + 1, CZ + dz + oz, rnd.choice(["allium", "pink_tulip", "oxeye_daisy", "cornflower", "azure_bluet"]))

# --- arbres entre les terrains et au fond, fleurs dans la pelouse
trees = []
for r, z0 in enumerate(ROWS):
    zc = z0 + SIZE // 2
    for i in range(len(COLS) - 1):
        trees.append((COLS[i] + SIZE + 5, zc - 6))
        trees.append((COLS[i] + SIZE + 5, zc + 6))
    trees.append((COLS[0] - 8, zc))
    trees.append((COLS[-1] + SIZE + 7, zc))
for x in range(X1 + 6, X2 - 4, 12):
    trees.append((x, Z1 + 5))
    trees.append((x, Z2 - 5))
kinds = ["fancy_oak", "birch", "cherry", "fancy_oak", "azalea_tree"]
for i, (x, z) in enumerate(trees):
    cmds.append(f"place feature minecraft:{kinds[i % len(kinds)]} {x} {Y + 1} {z}")
flowers = ["short_grass"] * 6 + ["poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium", "lily_of_the_valley"]
for _ in range(120):
    x, z = rnd.randint(X1 + 2, X2 - 2), rnd.randint(Z1 + 2, Z2 - 2)
    if in_plot_zone(x, z, 3) or (az1 - 1 <= z <= az2 + 1) or (x - CX) ** 2 + (z - CZ) ** 2 <= (R + 1) ** 2:
        continue
    cmds.append(f"execute if block {x} {Y} {z} grass_block if block {x} {Y + 1} {z} air run setblock {x} {Y + 1} {z} {rnd.choice(flowers)}")

# --- murs pleins autour de chaque terrain (sur le liseré, hors du terrain) : on ne voit pas les autres constructions
WALL_TOP = Y + HEIGHT + 16
walls = []
for x0, z0, col in plots:
    xa, xb, za, zb = x0 - 1, x0 + SIZE, z0 - 1, z0 + SIZE
    for a in ((xa, za, xb, za), (xa, zb, xb, zb), (xa, za, xa, zb), (xb, za, xb, zb)):
        walls.append(f"fill {a[0]} {Y + 1} {a[1]} {a[2]} {WALL_TOP} {a[3]} smooth_quartz")
cmds.append("function eemibb:walls")

out = os.path.join(HERE, "eemibb")
os.makedirs(os.path.join(out, "data", "eemibb", "function"), exist_ok=True)
with open(os.path.join(out, "pack.mcmeta"), "w", encoding="utf-8") as f:
    f.write('{\n  "pack": {\n    "description": "Construction de l\'arene Build Battle (fonction seulement, rien ne tourne en continu)",\n'
            '    "min_format": 121,\n    "max_format": 121\n  }\n}\n')
with open(os.path.join(out, "data", "eemibb", "function", "build.mcfunction"), "w", encoding="utf-8") as f:
    f.write("\n".join(cmds) + "\n")
with open(os.path.join(out, "data", "eemibb", "function", "walls.mcfunction"), "w", encoding="utf-8") as f:
    f.write("\n".join(walls) + "\n")

W = "buildbattle"
lines = [
    "  eemicraft:",
    f"    lobbylocation: {W},{CX + 0.5},{Y + 1}.0,{CZ + 9.5},180.0,0.0",
    f"    startlocation: {W},{CX + 0.5},{Y + 1}.0,{CZ + 9.5},180.0,0.0",
    f"    endlocation: {W},{CX + 0.5},{Y + 1}.0,{CZ + 9.5},180.0,0.0",
    f"    spectatorlocation: {W},{CX + 0.5},{Y + 25}.0,{CZ + 0.5},0.0,60.0",
    "    minimumplayers: 2",
    "    maximumplayers: 8",
    "    plotmembersize: 1",
    "    mapname: Jardins d'EEMICRAFT",
    f"    world: {W}",
    "    gametype: classic",
    "    isdone: true",
    "    signs: {}",
    "    plots:",
]
for i, (x0, z0, _) in enumerate(plots):
    lines.append(f"      '{i}':")
    lines.append(f"        '1': {W},{x0}.0,{Y}.0,{z0}.0,0.0,0.0")
    lines.append(f"        '2': {W},{x0 + SIZE - 1}.0,{Y + HEIGHT}.0,{z0 + SIZE - 1}.0,0.0,0.0")
with open(os.path.join(HERE, "arena.yml"), "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
print(len(cmds), "commandes ; zone", X1, Z1, X2, Z2, "; centre", CX, CZ)
