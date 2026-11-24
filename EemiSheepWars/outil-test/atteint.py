"""Verifie au parcours a pied (BFS genereux de sw_tool) que les niveaux ajoutes en 4.6 sont atteints depuis les
departs du camp Rouge : souterrain, niveaux hauts, point haut. Usage : python -I atteint.py
Points en coordonnees MONDE (camp Rouge, x < 0), y = pieds."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sw_tool as T

POINTS = {
    'pirates': {'cale (pieds 8)': (-12, 8, -6), 'salle des contrebandiers': (-23, 8, 2), 'galerie du quai': (-18, 8, 15),
                'embarcadere': (-15, 8, 28), 'reserve nord': (-24, 8, 21), 'soupente (pieds 15)': (-25, 15, 20),
                'gaillard (pieds 16)': (-12, 16, -15), 'fortin (pieds 19)': (-32, 19, 4), 'tourelle (pieds 25)': (-33, 25, 0)},
    'ruines': {'crypte (pieds 14)': (-22, 14, 0), 'loggia': (-9, 14, 1), 'aqueduc (pieds 27)': (-30, 27, 10),
               'castellum toit (pieds 32)': (-31, 32, -7), 'dessus de l\'arc (pieds 26)': (-21, 26, -29)},
    'spatiale': {'salle des machines (pieds 14)': (-21, 14, 0), 'sas-balcon': (-9, 14, 1), 'dome (pieds 20)': (-30, 20, 0),
                 'passerelle (pieds 26)': (-26, 26, 0), 'tour (pieds 32)': (-30, 32, 1)},
}

for mid, pts in POINTS.items():
    m = T.Map(mid)
    void = m.p['voidRel']
    start = [(x, y + 1, z) for (x, y, z) in [m.world_pt(p) for p in m.p['red']]]
    seen = T.bfs(m, start, void)
    print(mid, ': cases atteintes', len(seen), ', y max atteint', max(c[1] for c in seen))
    for name, (x, y, z) in pts.items():
        ok = (x, y, z) in seen
        near = [c for c in seen if abs(c[0] - x) <= 1 and abs(c[2] - z) <= 1 and c[1] == y]
        print('   %-32s %s %s' % (name, 'OK' if ok or near else 'NON ATTEINT', '' if ok else ('(voisin %s)' % (near[0],) if near else '')))
