import json, os, sys
O = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out')
ms = sys.argv[1:]
J = {m: json.load(open(os.path.join(O, m + '.json'), encoding='utf-8')) for m in ms}
rows = [('dims', lambda r: 'x'.join(map(str, r['dims'])))]
for sec in ('palette', 'murs', 'relief', 'densite', 'symetrie_%', 'reperes', 'toits'):
    for k in J[ms[0]][sec]:
        if k in ('top25', 'top_visibles', 'familles_multi_variantes', 'histo_hauteur_surface_par_4', 'part_visible_par_categorie_%', 'materiaux_top8', 'categories_%'): continue
        rows.append((sec[:5] + '.' + k, (lambda s, k: lambda r: r[s][k])(sec, k)))
print('| mesure | ' + ' | '.join(ms) + ' |')
for n, f in rows:
    print('| ' + n + ' | ' + ' | '.join(str(f(J[m])) for m in ms) + ' |')
for m in ms:
    r = J[m]
    print('\n##', m, r['map'])
    print('top25', r['palette']['top25'])
    print('cat visibles', r['palette']['part_visible_par_categorie_%'])
    print('familles', [(a, b) for a, b, c in r['palette']['familles_multi_variantes']])
    print('toits', r['toits']['categories_%'], r['toits']['materiaux_top8'])
    print('histo', r['relief']['histo_hauteur_surface_par_4'])
