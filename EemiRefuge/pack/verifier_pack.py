"""Vérification automatique d'un ou plusieurs packs de ressources (CONCEPTION 12.2).

Usage :
    python verifier_pack.py <pack.zip> [<autre_pack.zip> ...] [--vanilla <client.jar>]
                            [--item-models <liste.txt>] [--strict-display <ns>[,<ns>...]] [--vanilla-overrides]

Code de sortie 0 si aucune ERREUR, 1 sinon (la liste précise est affichée). Les AVERTISSEMENTS ne bloquent pas.

Erreurs :
- JSON (ou .mcmeta) invalide ;
- définition d'objet (assets/<ns>/items/*.json) qui référence un modèle introuvable (ni dans les packs donnés,
  ni dans le jar vanilla) ;
- modèle dont le parent est introuvable, ou dont une texture est introuvable ;
- modèle utilisé par un objet sans rendu possible (pas d'éléments, chaîne de parents qui n'aboutit ni à
  builtin/generated avec layer0 ni à des éléments) ;
- texture utilisée par un modèle d'objet hors de textures/item ou textures/block (ex. textures/entity) ;
- texture d'objet ou de bloc qui n'est pas un PNG, ou dont la largeur ou la hauteur n'est pas un multiple
  de 16 (animation : hauteur multiple de la largeur) ;
- avec --item-models : un item_model attendu (refuge:pistolet...) sans fichier assets/<ns>/items/<id>.json ;
- avec --strict-display : modèle d'objet de cet espace de noms à qui il manque une position `display`
  (main droite/gauche 1re et 3e personne, tête, cadre, sol, menu), parents compris ;
- avec plusieurs packs : un même fichier présent dans deux packs (collision ; pack.mcmeta et pack.png exceptés) ;
- fichiers en double à la casse près ;
- avec --vanilla-overrides (pack qui remplace des fichiers vanilla) : tout fichier `assets/minecraft/...` du pack
  doit exister dans le jar 26.3 (sinon faute de frappe : le fichier ne remplacerait rien), un `.png.mcmeta` doit
  accompagner un PNG vanilla existant ; une texture d'entité ou une colormap remplacée doit avoir EXACTEMENT la
  taille de l'originale (mise en page UV) ; une autre texture vanilla remplacée doit garder sa largeur (la hauteur
  peut changer seulement pour une animation déclarée dans le pack).
Avertissements : textures orphelines (référencées par aucun modèle, police, atlas ni équipement).
"""
import io
import json
import os
import re
import sys
import zipfile

try:
    from PIL import Image
except ImportError:  # la taille sera lue dans l'en-tête PNG
    Image = None

DISPLAY_KEYS = ["thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand",
                "head", "fixed", "ground", "gui"]
# Positions que le jeu fournit par défaut (identité) si personne ne les définit : seulement `gui` pour
# builtin/generated ; et les mains gauches retombent sur les mains droites (ItemTransforms).
DEFAULT_OK = {"gui"}
LEFT_FALLBACK = {"thirdperson_lefthand": "thirdperson_righthand", "firstperson_lefthand": "firstperson_righthand"}


def norm(ref, default_kind=""):
    """'item/x' -> 'minecraft:item/x' ; 'ns:x' inchangé."""
    ref = ref.strip()
    if ":" not in ref:
        ref = "minecraft:" + ref
    return ref


def split(ref):
    ns, path = norm(ref).split(":", 1)
    return ns, path


class Pack:
    def __init__(self, path):
        self.path = path
        self.name = os.path.basename(path)
        self.files = {}
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                if not n.endswith("/"):
                    self.files[n] = z.read(n)


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    if Image is not None:
        try:
            return Image.open(io.BytesIO(data)).size
        except Exception:
            return None
    w = int.from_bytes(data[16:20], "big")
    h = int.from_bytes(data[20:24], "big")
    return (w, h)


def find_vanilla():
    for cand in [os.path.expandvars(r"%APPDATA%\.minecraft\versions\26.3\26.3.jar"),
                 os.path.expanduser("~/.minecraft/versions/26.3/26.3.jar")]:
        if os.path.isfile(cand):
            return cand
    return None


def main(argv):
    packs_paths, vanilla, item_models_file, strict, overrides = [], None, None, set(), False
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--vanilla":
            vanilla = argv[i + 1]; i += 2; continue
        if a == "--item-models":
            item_models_file = argv[i + 1]; i += 2; continue
        if a == "--vanilla-overrides":
            overrides = True; i += 1; continue
        if a == "--strict-display":
            strict |= set(x for x in argv[i + 1].split(",") if x); i += 2; continue
        packs_paths.append(a); i += 1
    if not packs_paths:
        print(__doc__)
        return 2
    vanilla = vanilla or find_vanilla()
    errors, warns = [], []

    vfiles = set()
    vmodels = {}
    vsizes = {}
    if vanilla and os.path.isfile(vanilla):
        with zipfile.ZipFile(vanilla) as z:
            for n in z.namelist():
                if n.startswith("assets/"):
                    vfiles.add(n)
                    if overrides and n.startswith("assets/minecraft/textures/") and n.endswith(".png"):
                        vsizes[n] = png_size(z.read(n))
                    if n.startswith("assets/minecraft/models/") and n.endswith(".json"):
                        try:
                            vmodels[n] = json.loads(z.read(n).decode("utf-8"))
                        except Exception:
                            pass
    else:
        warns.append("jar vanilla introuvable (--vanilla) : les références minecraft: ne sont pas vérifiées")

    packs = [Pack(p) for p in packs_paths]

    # --- collisions entre packs et doublons de casse
    seen = {}
    for pk in packs:
        lower = {}
        for n in pk.files:
            if n.lower() in lower and lower[n.lower()] != n:
                errors.append(f"[{pk.name}] fichiers en double à la casse près : {lower[n.lower()]} / {n}")
            lower[n.lower()] = n
            if n in ("pack.mcmeta", "pack.png"):
                continue
            if n in seen:
                errors.append(f"collision : {n} est dans {seen[n]} ET {pk.name} (le pack du dessus masque l'autre)")
            else:
                seen[n] = pk.name

    for pk in packs:
        f = pk.files
        tag = f"[{pk.name}]"
        js = {}
        for n, data in f.items():
            if n.endswith(".json") or n.endswith(".mcmeta"):
                try:
                    js[n] = json.loads(data.decode("utf-8-sig"))
                except Exception as ex:
                    errors.append(f"{tag} JSON invalide : {n} ({str(ex)[:80]})")
        if "pack.mcmeta" not in js:
            errors.append(f"{tag} pack.mcmeta absent ou invalide")

        all_files = set(f) | vfiles
        other = set()
        for op in packs:
            other |= set(op.files)

        def model_path(ref):
            ns, p = split(ref)
            return f"assets/{ns}/models/{p}.json"

        def tex_path(ref):
            ns, p = split(ref)
            return f"assets/{ns}/textures/{p}.png"

        def load_model(ref):
            mp = model_path(ref)
            if mp in js:
                return js[mp]
            for op in packs:
                if mp in op.files:
                    try:
                        return json.loads(op.files[mp].decode("utf-8-sig"))
                    except Exception:
                        return None
            return vmodels.get(mp)

        def exists_model(ref):
            ns, p = split(ref)
            if ns == "minecraft" and p.startswith("builtin/"):
                return True
            mp = model_path(ref)
            return mp in all_files or mp in other

        def exists_tex(ref):
            tp = tex_path(ref)
            return tp in all_files or tp in other

        # --- modèles référencés par les définitions d'objets
        item_models = {}  # modèle -> définition qui l'utilise

        def walk(node, src):
            if isinstance(node, dict):
                t = norm(node.get("type", "")) if isinstance(node.get("type"), str) else ""
                if t == "minecraft:model" and isinstance(node.get("model"), str):
                    item_models.setdefault(norm(node["model"]), src)
                for v in node.values():
                    walk(v, src)
            elif isinstance(node, list):
                for v in node:
                    walk(v, src)

        for n, j in js.items():
            if re.match(r"assets/[^/]+/items/.+\.json$", n):
                if "model" not in j:
                    errors.append(f"{tag} {n} : pas de clé 'model'")
                walk(j.get("model"), n)
        for m, src in sorted(item_models.items()):
            if not exists_model(m):
                errors.append(f"{tag} {src} : modèle introuvable {m}")

        # --- tous les modèles du pack : parents et textures
        used_tex = set()
        for n, j in js.items():
            mm = re.match(r"assets/([^/]+)/models/(.+)\.json$", n)
            if not mm:
                continue
            ref = f"{mm.group(1)}:{mm.group(2)}"
            par = j.get("parent")
            if isinstance(par, str) and not exists_model(par):
                errors.append(f"{tag} {n} : parent introuvable {par}")
            for k, v in (j.get("textures") or {}).items():
                if isinstance(v, str) and not v.startswith("#"):
                    used_tex.add(tex_path(v))
                    if not exists_tex(v):
                        errors.append(f"{tag} {n} : texture introuvable {v} ({k})")

        # --- rendu possible et emplacement des textures des modèles d'objets
        def chain(ref):
            out, cur, guard = [], norm(ref), 0
            while cur and guard < 30:
                guard += 1
                ns, p = split(cur)
                if ns == "minecraft" and p.startswith("builtin/"):
                    out.append((cur, {"__builtin__": p}))
                    break
                j = load_model(cur)
                if j is None:
                    out.append((cur, None))
                    break
                out.append((cur, j))
                cur = norm(j["parent"]) if isinstance(j.get("parent"), str) else None
            return out

        for m, src in sorted(item_models.items()):
            ch = chain(m)
            if not ch or ch[-1][1] is None:
                if ch and ch[-1][0] != m:
                    errors.append(f"{tag} {src} : chaîne de parents cassée pour {m} ({ch[-1][0]})")
                continue
            textures, has_elements, builtin = {}, False, None
            for _, j in reversed(ch):
                if "__builtin__" in j:
                    builtin = j["__builtin__"]
                    continue
                textures.update(j.get("textures") or {})
                if j.get("elements"):
                    has_elements = True
            def resolve(v, depth=0):
                while isinstance(v, str) and v.startswith("#") and depth < 10:
                    v = textures.get(v[1:]); depth += 1
                return v
            if builtin == "builtin/generated" and not has_elements:
                if not resolve(textures.get("layer0")):
                    errors.append(f"{tag} {src} : {m} sans layer0 (rien à dessiner)")
            elif not has_elements and builtin != "builtin/entity":
                errors.append(f"{tag} {src} : {m} n'a ni éléments ni builtin/generated (rendu impossible)")
            for k, v in textures.items():
                rv = resolve(v)
                if not isinstance(rv, str):
                    continue
                ns, p = split(rv)
                if not (p.startswith("item/") or p.startswith("block/")):
                    errors.append(f"{tag} {src} : {m} utilise la texture {rv} hors de textures/item|block "
                                  f"(atlas des objets : violet et noir en jeu)")
            # positions display
            ns_m = split(m)[0]
            if ns_m in strict:
                disp = {}
                for _, j in reversed(ch):
                    if "__builtin__" in j:
                        continue
                    disp.update(j.get("display") or {})
                for key in DISPLAY_KEYS:
                    if key in disp or key in DEFAULT_OK:
                        continue
                    if key in LEFT_FALLBACK and LEFT_FALLBACK[key] in disp:
                        continue
                    errors.append(f"{tag} {m} : position display '{key}' absente")

        # --- textures : format et taille
        for n, data in f.items():
            mm = re.match(r"assets/[^/]+/textures/(item|block)/.+\.png$", n)
            if not mm:
                continue
            size = png_size(data)
            if size is None:
                errors.append(f"{tag} {n} : PNG illisible")
                continue
            w, h = size
            if w == 0 or h == 0 or w % 16 != 0 or h % 16 != 0:
                errors.append(f"{tag} {n} : taille {w}x{h} (attendu 16x16 ou multiple de 16)")
            elif (n + ".mcmeta") in f and h % w != 0:
                errors.append(f"{tag} {n} : animation {w}x{h} dont la hauteur n'est pas un multiple de la largeur")
            elif h != w and (n + ".mcmeta") not in f:
                warns.append(f"{tag} {n} : {w}x{h} non carrée (normal pour un modèle 3D à UV, à vérifier sinon)")
        for n in f:
            if re.match(r"assets/[^/]+/textures/entity/", n) and not (overrides and n in vfiles):
                # Tenues (composant equippable) : légitimes si assets/<ns>/equipment/<id>.json existe.
                eq = re.match(r"assets/([^/]+)/textures/entity/equipment/[^/]+/([^/]+)\.png$", n)
                if eq:
                    if f"assets/{eq.group(1)}/equipment/{eq.group(2)}.json" not in f:
                        errors.append(f"{tag} {n} : texture de tenue sans assets/{eq.group(1)}/equipment/{eq.group(2)}.json")
                    continue
                warns.append(f"{tag} {n} : texture d'entité (inutilisable par un modèle d'objet)")

        # --- remplacements de fichiers vanilla
        if overrides:
            nb = 0
            for n, data in f.items():
                if not n.startswith("assets/minecraft/"):
                    continue
                nb += 1
                if n.endswith(".mcmeta"):
                    if n[:-len(".mcmeta")] not in vfiles:
                        errors.append(f"{tag} {n} : .mcmeta d'une texture absente du jar 26.3")
                    elif n[:-len(".mcmeta")] not in f:
                        errors.append(f"{tag} {n} : .mcmeta sans la texture qu'il anime dans le pack")
                    continue
                if n not in vfiles:
                    errors.append(f"{tag} {n} : absent du jar 26.3 (faute de frappe ? ce fichier ne remplace rien)")
                    continue
                if n.endswith(".png") and n.startswith("assets/minecraft/textures/"):
                    size, orig = png_size(data), vsizes.get(n)
                    if size is None or orig is None:
                        errors.append(f"{tag} {n} : PNG illisible")
                        continue
                    strict_size = "/textures/entity/" in n or "/textures/colormap/" in n
                    if strict_size and size != orig:
                        errors.append(f"{tag} {n} : {size[0]}x{size[1]} au lieu de {orig[0]}x{orig[1]} (mise en page UV vanilla)")
                    elif not strict_size and size[0] != orig[0]:
                        errors.append(f"{tag} {n} : largeur {size[0]} au lieu de {orig[0]}")
                    elif not strict_size and size[1] != orig[1] and (n + ".mcmeta") not in f:
                        errors.append(f"{tag} {n} : hauteur {size[1]} au lieu de {orig[1]} sans animation déclarée")
            print(f"{tag} {nb} fichier(s) vanilla remplacé(s), tous contrôlés contre le jar 26.3.")

        # --- orphelines (références : modèles, polices, atlas, équipements)
        refs_text = set()
        for n, j in js.items():
            if "/font/" in n or "/atlases/" in n or "/equipment/" in n or "/particles/" in n:
                for s in re.findall(r'"([a-z0-9_.\-]+:[a-z0-9_./\-]+|[a-z0-9_./\-]+\.png)"', json.dumps(j)):
                    s = s[:-4] if s.endswith(".png") else s
                    refs_text.add(norm(s))
        for n in f:
            mm = re.match(r"assets/([^/]+)/textures/(.+)\.png$", n)
            if not mm or n in used_tex or (overrides and n in vfiles):
                continue
            ref = f"{mm.group(1)}:{mm.group(2)}"
            if ref in refs_text or any(r.endswith(":" + mm.group(2).split("/")[-1]) for r in refs_text if "/" not in r.split(":")[1]):
                continue
            if mm.group(2).startswith(("font/", "gui/", "entity/", "particle/", "misc/", "colormap/", "trims/",
                                       "painting/", "environment/", "effect/", "map/", "mob_effect/")):
                continue
            warns.append(f"{tag} texture orpheline : {n}")

        # --- item_model attendus par le plugin
        if item_models_file:
            for line in open(item_models_file, encoding="utf-8"):
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                ns, p = split(line)
                path = f"assets/{ns}/items/{p}.json"
                if not any(path in op.files for op in packs) and path not in vfiles:
                    errors.append(f"item_model {line} attendu par le plugin : {path} absent")
        item_models_file = None  # une seule fois

    for w in warns:
        print("AVERTISSEMENT :", w)
    for e in errors:
        print("ERREUR :", e)
    print(f"{len(packs)} pack(s) vérifié(s) : {len(errors)} erreur(s), {len(warns)} avertissement(s).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
