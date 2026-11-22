"""Prépare les données des maps Survival Games de Quillmark pour EemiHungerGames.

Datapack eemihg (src/main/resources/datapack/eemihg, installé par le plugin) : uniquement les tables de loot
des maps, sans fonctions, recettes ni avancements (les datapacks d'origine agiraient sur tout le serveur).
Chaque map a son dossier : sg:X devient eemihg:<map>/X.

- desert (Desolate Desert) : les conditions qui lisaient le scoreboard / le stockage du datapack sont
  résolues ici (loot « crafted » = matériaux + objets prêts, porte de la bibliothèque fermée, deux variantes
  de la corne : premier remplissage / réapprovisionnement) ; minecraft:overworld -> minecraft:hungergames.
  Coffres : stockage sg:chests de data/sg/command_storage.dat (lu en JSON par scripts/nbtread.py).
- altrac (Ruins of Altrac) : tables « normal » telles quelles. Coffres : marqueurs des fonctions
  game/generate/chests/summon/*.mcfunction (coffres déjà posés dans la map), enclumes de anvils/summon.

Sorties : src/main/resources/chests_<map>.yml (« x y z orientation qualite chance », orientation « keep » =
garder le coffre de la map).

Usage : python -I gen_data.py <desolate-desert extrait> <command_storage.json> <ruins-of-altrac extrait>
"""
import json, os, re, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, 'src', 'main', 'resources')
OUT = os.path.join(RES, 'datapack', 'eemihg')
LOOT = os.path.join(OUT, 'data', 'eemihg', 'loot_table')

KEY_NAME = {"text": "Cle de la bibliotheque", "color": "yellow", "italic": False}
KEY_LORE = [{"text": "Ouvre la porte de la bibliotheque (clic droit sur la vitre)", "color": "gray", "italic": False}]


def check(cond, ctx):
    """Valeur d'une condition int_value_check connue, None si ce n'est pas une condition à résoudre."""
    if cond.get('type') not in ('int_value_check', 'minecraft:int_value_check'):
        return None
    v = cond['value']
    if v['type'].endswith('storage'):
        val = ctx['loot_type']
    else:
        val = ctx[v['target']['name']]
    return val == cond['test']


def resolve_condition(c, ctx):
    """Renvoie (garder, condition restante ou None)."""
    r = check(c, ctx)
    if r is not None:
        return r, None
    if c.get('type') in ('all_of', 'minecraft:all_of'):
        rest = []
        for t in c['terms']:
            ok, left = resolve_condition(t, ctx)
            if not ok:
                return False, None
            if left is not None:
                rest.append(left)
        if not rest:
            return True, None
        return True, rest[0] if len(rest) == 1 else {"type": "minecraft:all_of", "terms": rest}
    return True, c


def walk(o, ctx):
    if isinstance(o, list):
        out = []
        for x in o:
            if isinstance(x, dict) and 'condition' in x:
                ok, left = resolve_condition(x['condition'], ctx)
                if not ok:
                    continue
                x = dict(x)
                if left is None:
                    del x['condition']
                else:
                    x['condition'] = left
            out.append(walk(x, ctx))
        return out
    if isinstance(o, dict):
        if 'condition' in o:  # pool avec condition
            ok, left = resolve_condition(o['condition'], ctx)
            if not ok:
                return None
            o = dict(o)
            if left is None:
                del o['condition']
            else:
                o['condition'] = left
        res = {}
        for k, v in o.items():
            w = walk(v, ctx)
            if w is None and isinstance(v, dict) and 'condition' in v:
                continue
            res[k] = w
        if 'pools' in res:
            res['pools'] = [p for p in res['pools'] if p is not None]
        # clé de la bibliothèque : texte en clair, pas de modèle du pack de la map
        comps = res.get('components')
        if isinstance(comps, dict) and isinstance(comps.get('custom_data'), dict) and comps['custom_data'].get('LibraryKey'):
            comps.pop('item_model', None)
            comps['item_name'] = KEY_NAME
            comps['lore'] = KEY_LORE
        return res
    if isinstance(o, str):
        if o == 'minecraft:overworld':
            return 'minecraft:' + ctx['world']
        return o
    return o


def rename(o, prefix):
    s = json.dumps(o)
    return json.loads(s.replace('"sg:', '"eemihg:' + prefix).replace('"#sg:', '"#eemihg:' + prefix))


def copy_tables(src, dst, prefix, ctx, skip=()):
    n = 0
    for root, _, files in os.walk(src):
        rel = os.path.relpath(root, src)
        if any(rel.startswith(s) for s in skip):
            continue
        for f in files:
            d = json.load(open(os.path.join(root, f), encoding='utf-8'))
            out = os.path.join(dst, rel)
            os.makedirs(out, exist_ok=True)
            json.dump(rename(walk(d, ctx), prefix), open(os.path.join(out, f), 'w', encoding='utf-8'), indent=1)
            n += 1
    return n


def write_chests(name, title, rows):
    lines = ['# Coffres de %s (genere par gen_data.py) : x y z orientation qualite chance' % title, 'chests:']
    lines += ['  - "%s"' % r for r in rows]
    open(os.path.join(RES, 'chests_%s.yml' % name), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print(title, ':', len(rows), 'coffres/enclumes')


def desert(path, storage):
    src = os.path.join(path, 'datapacks', 'desolate-desert', 'data', 'sg', 'loot_table')
    dst = os.path.join(LOOT, 'desert')
    base = {'loot_type': 1, '$LibraryDoor': 0, '$CornucopiaLootState': 0, 'world': 'hungergames'}
    n = copy_tables(src, dst, 'desert/', base, skip=('lobby',))
    # corne d'abondance : réapprovisionnement = loot « rare » (comme $CornucopiaLootState 1)
    for kind in ('materials', 'crafted'):
        d = json.load(open(os.path.join(src, 'chests', 'base', kind, 'check_corn_restock.json'), encoding='utf-8'))
        r = rename(walk(d, dict(base, **{'$CornucopiaLootState': 1})), 'desert/')
        json.dump(r, open(os.path.join(dst, 'chests', 'base', kind, 'check_corn_restock_restock.json'), 'w'), indent=1)
    c = json.load(open(os.path.join(src, 'chests', 'base', 'cornucopia.json'), encoding='utf-8'))
    c = json.loads(json.dumps(rename(walk(c, base), 'desert/')).replace('check_corn_restock"', 'check_corn_restock_restock"'))
    json.dump(c, open(os.path.join(dst, 'chests', 'base', 'cornucopia_restock.json'), 'w'), indent=1)
    print('Desolate Desert :', n + 3, 'tables de loot')
    st = json.load(open(storage, encoding='utf-8'))['data']['contents']['chests']['Chunks']
    rows = []
    for ch in st:
        for c in ch['Chests']:
            x, y, z = c['xyz'].split()
            rows.append('%s %s %s %s %s %.3f' % (x, y, z, c['Facing'], c['Quality'], c['SpawnChance']))
    write_chests('desert', 'Desolate Desert', rows)


def altrac(path):
    dp = os.path.join(path, 'datapacks', 'ruins-of-altrac', 'data', 'sg')
    n = copy_tables(os.path.join(dp, 'loot_table'), os.path.join(LOOT, 'altrac'), 'altrac/', {'world': 'hg_altrac'})
    print('Ruins of Altrac :', n, 'tables de loot')
    tags = {'SGCommonChest': 'common_chest', 'SGRareChest': 'rare_chest', 'SGEpicChest': 'epic_chest',
            'SGCornucopiaChest': 'cornucopia_chest'}
    rows, seen = [], set()
    summon = os.path.join(dp, 'function', 'game', 'generate', 'chests', 'summon')
    pat = re.compile(r'positioned (-?\d+) (-?\d+) (-?\d+) run summon marker .*"(SG\w+Chest)"\]')
    for f in sorted(os.listdir(summon)):
        for line in open(os.path.join(summon, f), encoding='utf-8'):
            m = pat.search(line)
            if not m or m.group(4) not in tags:
                continue
            k = m.group(1, 2, 3)
            if k in seen:
                continue
            seen.add(k)
            rows.append('%s %s %s keep %s 1.000' % (k[0], k[1], k[2], tags[m.group(4)]))
    apat = re.compile(r'summon marker (-?\d+) (-?\d+) (-?\d+) \{Tags:\["SGAnvil","SGAnvil(\w+)"\]')
    for line in open(os.path.join(dp, 'function', 'game', 'generate', 'anvils', 'summon.mcfunction'), encoding='utf-8'):
        m = apat.search(line)
        if m:
            rows.append('%s %s %s %s anvil 1.000' % (m.group(1), m.group(2), m.group(3), m.group(4).lower()))
    write_chests('altrac', 'Ruins of Altrac', rows)
    spawns = []
    sp = re.compile(r'summon marker (-?[\d.]+) 35 (-?[\d.]+) \{Tags:\["SGSpawnMarker","SGSpawn(\d+)".*Rotation:\[(-?[\d.]+)f')
    for line in open(os.path.join(dp, 'function', 'game', 'spawning', 'summon', 'all.mcfunction'), encoding='utf-8'):
        m = sp.search(line)
        if m:
            spawns.append((int(m.group(3)), '%s,46.5,%s,%s' % (m.group(1), m.group(2), m.group(4))))
    print('Ruins of Altrac : spawns (ordre du datapack)')
    for _, s in sorted(spawns):
        print('      - "%s"' % s)


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(LOOT, exist_ok=True)
    for old in ('chests.yml',):
        if os.path.exists(os.path.join(RES, old)):
            os.remove(os.path.join(RES, old))
    desert(sys.argv[1], sys.argv[2])
    altrac(sys.argv[3])
    json.dump({"pack": {"description": "EEMICRAFT Hunger Games : tables de loot des maps Quillmark",
                        "min_format": 121, "max_format": 121}},
              open(os.path.join(OUT, 'pack.mcmeta'), 'w'), indent=1)


main()
