"""Base de l'étage de l'EEMI (2e étage, 59 rue Denuzière, Lyon) d'après le plan d'évacuation.

Écrit une fonction de datapack (fill/setblock) à lancer dans le monde `horscours` :
    execute in minecraft:horscours run function eemibb:ecole_base
Seulement la base : sol, murs, plafond, fenêtres, portes, éclairage. Le mobilier est fait à la main.

Échelle : 24 px du plan (photo 2000 px) = 1 bloc, soit environ 1 bloc = 0,8 m (un peu agrandi pour
que ce soit jouable). Bâtiment 37 x 29 blocs, sol en y 63, intérieur y 64..67, plafond y 68.

Usage : python -I gen_base.py <fichier .mcfunction de sortie>
"""
import sys

OX, OY, OZ = 300, 63, 0  # coin nord-ouest du bâtiment (x, sol, z) dans le monde horscours
W, D = 37, 29            # x 0..37, z 0..29 (murs extérieurs compris)
H = 4                    # hauteur intérieure

out = []


def fill(x1, y1, z1, x2, y2, z2, block, extra=""):
    out.append(f"fill {OX + x1} {OY + y1} {OZ + z1} {OX + x2} {OY + y2} {OZ + z2} {block}{extra}")


def setb(x, y, z, block):
    out.append(f"setblock {OX + x} {OY + y} {OZ + z} {block}")


def wall(x1, z1, x2, z2, glass=False):
    """Mur plein (ou vitré : bas plein, vitrage, haut plein) de y 1 à H, d'un point à l'autre."""
    if glass:
        fill(x1, 1, z1, x2, 1, z2, "white_concrete")
        fill(x1, 2, z1, x2, H - 1, z2, "white_stained_glass")
        fill(x1, H, z1, x2, H, z2, "white_concrete")
    else:
        fill(x1, 1, z1, x2, H, z2, "white_concrete")


def door(x, z, facing, block="oak_door", double=False):
    """Porte (2 de haut) dans un mur : facing = côté vers lequel elle s'ouvre en regardant."""
    for i, (dx, dz) in enumerate([(0, 0)] + ([(1, 0) if facing in ("north", "south") else (0, 1)] if double else [])):
        hinge = "left" if i == 0 else "right"
        setb(x + dx, 1, z + dz, f"{block}[facing={facing},half=lower,hinge={hinge}]")
        setb(x + dx, 2, z + dz, f"{block}[facing={facing},half=upper,hinge={hinge}]")


def windows(x1, z1, x2, z2, step=3):
    """Fenêtres sur un mur extérieur : 2 de large tous les `step` blocs, y 2..3."""
    if x1 == x2:
        for z in range(z1 + 1, z2 - 1, step):
            fill(x1, 2, z, x1, 3, min(z + 1, z2 - 1), "light_gray_stained_glass")
    else:
        for x in range(x1 + 1, x2 - 1, step):
            fill(x, 2, z1, min(x + 1, x2 - 1), 3, z1, "light_gray_stained_glass")


# --- vider, sol, plafond
fill(-2, -1, -2, W + 2, H + 3, D + 2, "air")
fill(0, 0, 0, W, 0, D, "smooth_stone")
fill(1, 1, 1, W - 1, 1, D - 1, "gray_carpet")
fill(0, H + 1, 0, W, H + 1, D, "light_gray_concrete")

# --- murs extérieurs (fenêtres sur les façades nord, ouest, sud ; l'est touche l'escalier)
wall(0, 0, W, 0)
wall(0, D, W, D)
wall(0, 0, 0, D)
wall(W, 0, W, D)
windows(0, 0, W, 0)
windows(0, D, W, D)
windows(0, 0, 0, D)

# --- cloisons (relevées sur le plan)
wall(15, 0, 15, 12)                 # Salle 01 / bureau du directeur
wall(0, 12, 15, 12)                 # Salle 01 / Salle 02
wall(12, 12, 12, D)                 # Salle 02 / couloir et réunion
wall(15, 8, 31, 8, glass=True)      # bureaux / couloir (vitré)
wall(23, 0, 23, 8)                  # directeur / staff
wall(31, 0, 31, 13)                 # staff / cagibi et sas
wall(31, 8, W, 8)                   # cagibi / sas de l'escalier
wall(31, 13, W - 1, 13)             # sas / couloir est (porte coupe-feu)
wall(12, 16, W, 16, glass=True)     # couloir / réunion et cuisine (vitré)
wall(22, 16, 22, D, glass=True)     # réunion / cuisine (vitré, on voit la cuisine)
wall(33, 16, 33, 22)                # petit local dans la cuisine
wall(33, 22, W, 22)

# --- portes
door(14, 12, "north")               # Salle 01 (depuis le bout ouest du couloir)
door(12, 14, "west")                # Salle 02
door(20, 8, "north")                # bureau du directeur
door(25, 8, "north")                # bureau du staff
door(33, 8, "north")                # cagibi de l'imprimante (depuis le sas)
door(33, 13, "north")               # sas / couloir (porte coupe-feu)
door(16, 16, "south")               # réunion
door(26, 16, "south")               # cuisine
door(35, 22, "south")               # petit local
door(W, 14, "east", "iron_door", double=True)   # sortie vers l'escalier

# --- escalier / ascenseur (carré noir du plan) et repère « vous êtes ici »
fill(35, 1, 10, 36, H, 12, "black_concrete")

# --- éclairage (néons du plafond, à garder ou déplacer)
for x, z in [(3, 3), (3, 11), (3, 19), (3, 27), (7, 7), (7, 15), (7, 23), (11, 3), (11, 11), (11, 19),
             (11, 27), (19, 4), (27, 4), (34, 4), (34, 10), (18, 12), (24, 12), (30, 12), (34, 14), (14, 14),
             (17, 20), (17, 26), (26, 20), (31, 26), (26, 26), (35, 19)]:
    setb(x, H + 1, z, "sea_lantern")

# --- panneaux de repère au sol (laine de couleur au centre de chaque pièce, à retirer)
ROOMS = {
    "Salle 01": ((1, 1), (14, 11), "red_wool"),
    "Salle 02": ((1, 13), (11, 28), "orange_wool"),
    "Bureau du directeur": ((16, 1), (22, 7), "yellow_wool"),
    "Bureau du staff": ((24, 1), (30, 7), "lime_wool"),
    "Cagibi imprimante": ((32, 1), (36, 7), "cyan_wool"),
    "Sas escalier": ((32, 9), (36, 12), "light_gray_wool"),
    "Couloir": ((13, 9), (36, 15), "white_wool"),
    "Reunion": ((13, 17), (21, 28), "blue_wool"),
    "Cuisine": ((23, 17), (36, 28), "purple_wool"),
    "Petit local": ((34, 17), (36, 21), "pink_wool"),
}
for name, ((x1, z1), (x2, z2), wool) in ROOMS.items():
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    setb(cx, 1, cz, wool)

if __name__ == "__main__":
    dest = sys.argv[1]
    open(dest, "w", encoding="utf-8").write(
        "# Base de l'etage de l'EEMI (genere par EemiHorsCours/ecole/gen_base.py)\n" + "\n".join(out) + "\n")
    print(len(out), "commandes")
    for name, ((x1, z1), (x2, z2), _) in ROOMS.items():
        print(f"{name:20s} x {OX + x1}..{OX + x2}  z {OZ + z1}..{OZ + z2}")
