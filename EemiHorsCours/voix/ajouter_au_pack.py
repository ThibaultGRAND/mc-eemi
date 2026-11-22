"""Ajoute les répliques de la Moustache (NN.ogg) au pack, sans toucher au reste.

Usage : python -I ajouter_au_pack.py <pack_publie.zip> <dossier_ogg> <sortie.zip>

Sons : assets/eemicraft/sounds/horscours/moustache/NN.ogg
Événements : eemicraft:horscours.moustache.NN (déjà joués par le plugin), fusionnés dans
assets/eemicraft/sounds.json (les autres sons du pack sont gardés).
"""
import hashlib
import json
import os
import sys
import zipfile

src, ogg_dir, out = sys.argv[1], sys.argv[2], sys.argv[3]
NS = "assets/eemicraft"
zin = zipfile.ZipFile(src)
names = zin.namelist()
sounds = json.loads(zin.read(f"{NS}/sounds.json")) if f"{NS}/sounds.json" in names else {}
files = {}
n = 0
for f in sorted(os.listdir(ogg_dir)):
    if not f.endswith(".ogg"):
        continue
    k = f[:-4]
    files[f"{NS}/sounds/horscours/moustache/{k}.ogg"] = open(os.path.join(ogg_dir, f), "rb").read()
    sounds[f"horscours.moustache.{k}"] = {
        "subtitle": "La Moustache parle",
        "sounds": [{"name": f"eemicraft:horscours/moustache/{k}", "stream": True}],
    }
    n += 1
files[f"{NS}/sounds.json"] = json.dumps(sounds, indent=1, ensure_ascii=False).encode("utf-8")
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        if info.filename in files or info.filename.startswith(f"{NS}/sounds/horscours/moustache/"):
            continue
        zout.writestr(info, zin.read(info.filename))
    for path in sorted(files):
        zi = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_STORED if path.endswith(".ogg") else zipfile.ZIP_DEFLATED
        zout.writestr(zi, files[path])
print(n, "repliques ajoutees,", len(sounds), "evenements sonores dans", f"{NS}/sounds.json")
print("SHA-1", hashlib.sha1(open(out, "rb").read()).hexdigest())
