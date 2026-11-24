"""Tableau de synthese : mesures Sheep Wars (sw_tool) + mesures du skill (analyze.py).
Usage : python -I resume.py id1 id2 ..."""
import json, sys, os
H = os.path.dirname(os.path.abspath(__file__))
SK = os.path.join(H, '..', '..', '.claude', 'skills', 'builder-minecraft', 'out')
for m in sys.argv[1:]:
    d = json.load(open(os.path.join(H, 'out', 'sw_%s.json' % m), encoding='utf-8-sig'))
    a = json.load(open(os.path.join(SK, 'swa_%s.json' % m), encoding='utf-8'))
    r = d['rouge']; v = d['vide']; p = a['palette']; w = a['murs']; de = a['densite']; re = a['relief']
    print('%-10s surf %4d (bleu %4d) camp %2dx%3d y_debout %s | vide min %2s med %2s d3 %5s | traversee %d/%d | departs KO %d | blocs %6d types %3d/%3d | detail bati %5.1f%% vis %4.1f%% veg %4.1f%% | melange %5.1f%% | pan max %2d 3couches %4.1f%% | couvert<=3 %4.1f%% ldv %4.1f | marches %4.1f/%4.1f'
          % (m, r['surface_jouable_colonnes'], d['bleu']['surface_jouable_colonnes'], r['longueur_x'], r['largeur_z'], r['y_debout'],
             v['largeur_x_min_par_rangee'], v['largeur_x_mediane'], v['dist_3d_min_tous_blocs'],
             d['parcours_depuis_rouge']['cases_dans_camp_adverse'], d['parcours_depuis_bleu']['cases_dans_camp_adverse'],
             len(d['apparitions_problemes']), d['blocs_total'], p['types'], p['types_>=0.1%'], p['part_detail_visible_sur_bati_%'],
             p['part_detail_visible_%'], p['part_vegetation_visible_%'], p['melange_local_%'], w['pan_coplanaire_max'],
             w['part_facade_3+_couches_%'], de['part_couvert_a_<=3_blocs_%'], de['ligne_de_vue_mediane'],
             re['part_marches_1_%'], re['part_ressauts_2-4_%']))
