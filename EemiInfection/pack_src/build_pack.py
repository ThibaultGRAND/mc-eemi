# -*- coding: utf-8 -*-
"""Fusionne le pack EN LIGNE (zip donne en argument) avec les fichiers d'EemiInfection (EemiInfection/pack/) -> nouveau zip.
Usage : python -I build_pack.py <pack_en_ligne.zip> <sortie.zip>
Ne remplace AUCUN fichier du pack en ligne : en cas de collision, arret sans rien ecrire.
Verifie ensuite le zip : JSON valides, items -> modeles -> textures existants, equipement -> textures, pack.mcmeta inchange.
Chemins avec '/', date fixe : le SHA-1 ne change que si le contenu change.
"""
import hashlib
import json
import os
import sys
import zipfile

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pack")
DATE = (2026, 11, 16, 12, 0, 0)


def res(ref, kind, ext):
    ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    return "assets/%s/%s/%s%s" % (ns, kind, path, ext)


def check(files, mcmeta_before):
    errors = []
    if files.get("pack.mcmeta") != mcmeta_before:
        errors.append("pack.mcmeta modifie")
    for n, data in files.items():
        if n.endswith(".json") or n.endswith(".mcmeta"):
            try:
                json.loads(data.decode("utf-8"))
            except Exception as e:  # noqa
                errors.append("JSON invalide %s : %s" % (n, e))
    ours = [n for n in files if "/infection/" in n or "/infection_" in n]
    for n in ours:
        if "/items/infection/" in n:
            m = json.loads(files[n])["model"]["model"]
            if res(m, "models", ".json") not in files:
                errors.append("%s -> modele absent %s" % (n, m))
        if "/models/item/infection/" in n:
            mj = json.loads(files[n])
            parent = mj.get("parent", "")
            if parent and not parent.startswith("minecraft:") and res(parent, "models", ".json") not in files:
                errors.append("%s -> parent absent %s" % (n, parent))
            for k, t in mj.get("textures", {}).items():
                if not t.startswith("#") and res(t, "textures", ".png") not in files:
                    errors.append("%s -> texture absente %s (%s)" % (n, t, k))
            for e in mj.get("elements", []):
                for f in e["faces"].values():
                    if f["texture"][1:] not in mj["textures"]:
                        errors.append("%s -> face sans texture %s" % (n, f["texture"]))
        if "/equipment/infection_" in n:
            for layer, lst in json.loads(files[n])["layers"].items():
                for l in lst:
                    ns, _, p = l["texture"].partition(":")
                    path = "assets/%s/textures/entity/equipment/%s/%s.png" % (ns, layer, p)
                    if path not in files:
                        errors.append("%s -> texture d'equipement absente %s" % (n, path))
    return ours, errors


def main(online, out):
    files = {}
    with zipfile.ZipFile(online) as z:
        for n in z.namelist():
            if not n.endswith("/"):
                files[n] = z.read(n)
    mcmeta = files.get("pack.mcmeta")
    base = len(files)
    added = []
    collisions = []
    for root, _, names in os.walk(SRC):
        for nm in names:
            full = os.path.join(root, nm)
            rel = os.path.relpath(full, SRC).replace("\\", "/")
            with open(full, "rb") as f:
                data = f.read()
            if rel in files:
                collisions.append(rel)
            else:
                added.append((rel, data))
    if collisions:
        print("COLLISION, rien n'est ecrit :")
        for c in collisions:
            print("  " + c)
        sys.exit(2)
    for rel, data in added:
        files[rel] = data
    ours, errors = check(files, mcmeta)
    if errors:
        print("ERREURS, rien n'est ecrit :")
        for e in errors:
            print("  " + e)
        sys.exit(3)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for n in sorted(files):
            zi = zipfile.ZipInfo(n, DATE)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, files[n])
    sha = hashlib.sha1(open(out, "rb").read()).hexdigest()
    print("pack en ligne : %d fichiers, infection : +%d nouveaux, 0 remplace -> %d fichiers" % (base, len(added), len(files)))
    print("verifications OK (%d fichiers infection controles)" % len(ours))
    print("taille %d octets, SHA-1 %s" % (os.path.getsize(out), sha))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
