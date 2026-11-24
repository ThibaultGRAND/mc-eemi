"""Verifie chaque etat de bloc des maps generees contre le blocks.json du data generator 26.3.
Usage : python -I valider_etats.py <blocks.json> out/sw_<id>.states.txt ..."""
import sys, json
B = json.load(open(sys.argv[1], encoding='utf-8'))
bad = 0
for f in sys.argv[2:]:
    for line in open(f, encoding='utf-8'):
        st = line.strip()
        if not st: continue
        bid = st.split('[')[0]
        props = {}
        if '[' in st:
            for kv in st[st.index('[') + 1:-1].split(','):
                k, v = kv.split('=')
                props[k] = v
        d = B.get(bid)
        if d is None:
            print(f, 'BLOC INCONNU', st); bad += 1; continue
        allowed = d.get('properties', {})
        for k, v in props.items():
            if k not in allowed:
                print(f, 'PROPRIETE INCONNUE', k, 'dans', st); bad += 1
            elif v not in allowed[k]:
                print(f, 'VALEUR INVALIDE', k, '=', v, 'dans', st); bad += 1
print('etats invalides :', bad)
sys.exit(1 if bad else 0)
