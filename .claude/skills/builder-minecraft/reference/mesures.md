# Mesures de maps faites par des builders (16/11/2026)

Mesurées par `outils/analyze.py` sur 6 maps. Références fiables : **fort** (Fort de Battlebound, CTF château), **paintball** (Stade de la forêt), **deadcenter** (Dead Center ch. 2, ville). Les 3 BedWars (sakura, frostvale, emberforge) viennent d'un même pack, dessous d'îles en bruit aléatoire : références « moyennes » ; **emberforge = contre-exemple** (rendu procédural poivre et sel).
Rendus et chiffres complets : `exemples/<id>_dessus.png`, `<id>_coupe.png`, `<id>.json`.

Définitions : « visible » = au moins une face à l'air ; « bâti » = hors blocs naturels, végétation et liquides ; « mélange local » = part des blocs bâtis dont un voisin est de la même famille mais d'un autre bloc (fort = poivre et sel).

## Palette

| mesure | sakura | frost | ember | paintball | fort | deadcenter |
|---|---|---|---|---|---|---|
| types de blocs | 69 | 67 | 43 | 82 | 135 | 213 |
| types ≥ 0,1 % | 32 | 21 | 18 | 26 | 40 | 50 |
| détail visible / bâti | 20,2 % | 56,9 %* | 5,6 % | 13,8 % | 14,0 % | 21,1 % |
| végétation visible | 1,7 % | 6,6 % | 0 | 9,0 % | 6,5 % | 5,3 % |
| mélange local | 42 % | 47 % | 32 % | **7 %** | 29 % | **13 %** |

\* gonflé par les couches de neige. Le **mélange local faible (7-13 %) est la signature d'un build à la main** : variantes en grappes ou en bandes. 30-47 % = bruit poivre et sel (défaut).

## Murs (façades construites)

| mesure | sakura | frost | ember | paintball | fort | deadcenter |
|---|---|---|---|---|---|---|
| pan coplanaire médian / p90 / max | 3/4/6 | 3/5/25 | 5/23/23 | 4/15/27 | **3/5/14** | 6/19/46 |
| couches de profondeur (fenêtre 9) | 3,25 | 3,29 | 3,19 | 2,94 | 2,52 | 2,04 |
| façade à 1 seule couche | 0,8 % | 5,6 % | 9,4 % | 19 % | 26 % | 37 % |
| façade à ≥ 3 couches | 64 % | 73 % | 67 % | 58 % | 43 % | 30 % |

La ville (deadcenter) est la plus plate (toits plats) mais compense par une palette propre à chaque immeuble et 43 hauteurs de toit.

## Relief et niveaux

| mesure | sakura | frost | ember | paintball | fort | deadcenter |
|---|---|---|---|---|---|---|
| voisins à la même hauteur | 71 % | 63 % | 89 % | 57 % | 76 % | 76 % |
| marches de 1 | 16 % | 19 % | 3 % | **30 %** | 12 % | 12 % |
| ressauts de 2 à 4 | 5 % | 13 % | 4 % | 7 % | 5 % | 4 % |
| niveaux de jeu (≥ 3 % des cases) | 3 | 4 | 4 | 3 | **5** | 3 |
| colonnes à ≥ 2 niveaux | 35 % | 27 % | 12 % | 7 % | **74 %** | 17 % |

## Couverts et lignes de vue (3 000 cases, rayons à hauteur des yeux)

| mesure | sakura | frost | ember | paintball | fort | deadcenter |
|---|---|---|---|---|---|---|
| surface par joueur | 439 | 179 | 364 | 3 111 | 1 643 | 2 597 |
| couvert à ≤ 3 blocs | 74 % | 73 % | 55 % | 76 % | 78 % | 73 % |
| ligne de vue médiane / p90 | 77/139 | 64/136 | 66/133 | 26/113 | **10/57** | **12/50** |
| lignes ≥ 32 blocs | 64 % | 64 % | 66 % | 44 % | 22 % | 20 % |

## Symétrie, repères, toits

| mesure | sakura | frost | ember | paintball | fort | deadcenter |
|---|---|---|---|---|---|---|
| symétrie du bâti (miroir x / rot. 180°) | 57/79 | – | – | 39/41 | 35/26 | 10/6 |
| colonnes hautes (≥ médiane + 12) | 7 % | 10 % | 9 % | 11 % | 25 % | 27 % |
| toits : escaliers / dalles / pleins | 14/9/74 % | 36/0/19 % | 0/5/80 % | 1/11/29 % | 8/6/70 % | 0/20/58 % |
| débord (air sous le toit) | 39 % | 35 % | 33 % | 63 % | 23 % | 49 % |
| hauteurs de toit différentes | 15 | 15 | 9 | 37 | 37 | 43 |

Paintball et Fort : **plan de jeu en miroir** (bases, apparitions), **décor asymétrique** (26-41 % seulement des blocs en miroir : végétation, mare, ruines).

## Ce qui se voit sur les rendus

- **Fort** : remparts sombres avec bande de briques rouges tous les ~6 et créneaux ; tours en saillie de 1 à 3 ; drapeau au point le plus haut (repère) ; cour pavée, allées en courbe ; moitiés nord/sud en miroir mais île asymétrique (mare au sud-ouest, bosquets) ; bord = forêt dense, pas de mur.
- **Paintball** : limite = collines + forêt en ceinture, aucun mur visible ; stade central (repère) ; maisons à toit rouge / bleu (couleurs d'équipe) ; routes en courbe ; tours en ruine avec échafaudages et étages ouverts.
- **Dead Center** : chaque immeuble = une couleur principale + une couleur de finition ; hauteurs très variées ; fissures, voitures, gravats ; trottoirs, bordures, arbres en grappes, canal.
- **Sakura** : pagode à 5 niveaux, avant-toit en escaliers de 1-2 à chaque niveau, flèche ; îles en cône inversé avec stalactites.
- **Emberforge** (à ne pas reproduire) : bruit poivre et sel sur toute la pierre, cône de pierre lisse.
