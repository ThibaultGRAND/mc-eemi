# -*- coding: utf-8 -*-
"""Fusionne le pack EN LIGNE (zip donne en argument) avec les fichiers du casino (EemiCasino/pack/) -> nouveau zip.
Usage : python -I build_pack.py <pack_en_ligne.zip> <sortie.zip>
Chemins avec '/', date fixe : le SHA-1 ne change que si le contenu change.
"""
import hashlib
import os
import sys
import zipfile

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pack")
DATE = (2026, 11, 10, 12, 0, 0)


def main(online, out):
    files = {}
    with zipfile.ZipFile(online) as z:
        for n in z.namelist():
            if not n.endswith("/"):
                files[n] = z.read(n)
    base = len(files)
    added = replaced = 0
    for root, _, names in os.walk(SRC):
        for nm in names:
            full = os.path.join(root, nm)
            rel = os.path.relpath(full, SRC).replace("\\", "/")
            with open(full, "rb") as f:
                data = f.read()
            if rel in files:
                replaced += 1
            else:
                added += 1
            files[rel] = data
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for n in sorted(files):
            zi = zipfile.ZipInfo(n, DATE)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, files[n])
    sha = hashlib.sha1(open(out, "rb").read()).hexdigest()
    print("pack en ligne : %d fichiers, casino : +%d nouveaux, %d remplaces -> %d fichiers" % (base, added, replaced, len(files)))
    print("taille %d octets, SHA-1 %s" % (os.path.getsize(out), sha))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
