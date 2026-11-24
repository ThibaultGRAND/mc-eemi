# -*- coding: utf-8 -*-
"""Ajoute les fichiers du Tycoon restaurant (EemiResto/pack/) au pack EN LIGNE -> nouveau zip.
Usage : python -I build_pack.py <pack_en_ligne.zip> <sortie.zip>

- N'ecrase JAMAIS un fichier du pack en ligne : une collision arrete tout (code 2), rien n'est ecrit.
- Chemins avec '/', date fixe : le SHA-1 ne change que si le contenu change.
"""
import hashlib
import os
import sys
import zipfile

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pack")
DATE = (2026, 11, 17, 12, 0, 0)


def main(online, out):
    files = {}
    with zipfile.ZipFile(online) as z:
        for n in z.namelist():
            if not n.endswith("/"):
                files[n] = z.read(n)
    base = len(files)
    mine = {}
    for root, _, names in os.walk(SRC):
        for nm in names:
            full = os.path.join(root, nm)
            rel = os.path.relpath(full, SRC).replace("\\", "/")
            with open(full, "rb") as f:
                mine[rel] = f.read()
    clash = sorted(r for r in mine if r in files)
    if clash:
        print("COLLISION : %d fichier(s) du resto existent deja dans le pack en ligne, rien n'est ecrit :" % len(clash))
        for r in clash[:30]:
            print("  " + r)
        sys.exit(2)
    if not mine:
        sys.exit("pack/ vide : lancer d'abord gen_resto_pack.py")
    for r in mine:
        if r.startswith("assets/minecraft/"):
            sys.exit("refus : %s retexturerait un objet vanilla" % r)
    files.update(mine)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for n in sorted(files):
            zi = zipfile.ZipInfo(n, DATE)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, files[n])
    sha = hashlib.sha1(open(out, "rb").read()).hexdigest()
    print("pack en ligne : %d fichiers, resto : +%d -> %d fichiers" % (base, len(mine), len(files)))
    print("taille %d octets, SHA-1 %s" % (os.path.getsize(out), sha))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
