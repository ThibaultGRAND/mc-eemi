# Analyse des maps Sheep Wars (16/11/2026)

Les six maps du jar 4.4.0 ont été mesurées hors serveur par `sw_tool.py` (placement du plugin, parcours à pied avec sauts sprintés et chute planée) et par `analyze.py` du skill builder-minecraft. Toutes sont des `.swm` en ids 1.8 (SWM1). Les trois retirées ont été produites hors ligne par le Claude de Thibault : il n'en reste pas de code, seulement les données.

## Chiffres (camp Rouge ; « surface » = colonnes où l'on tient debout)

| Map | Surface/camp | Camp x × z | Vide médian (min) | Île centrale | Types | Détail bâti | Couvert ≤ 3 | Ligne de vue méd. | Traversée au BFS |
|---|---|---|---|---|---|---|---|---|---|
| rose (aimée) | 1 518 | 31 × 85 | 16 (6) | oui, petite (318 blocs) | 49 | 8,5 % | 90 % | 18 | oui, par l'îlot |
| vert (aimée) | 1 122 | 26 × 92 | 21 (6) | oui, petite (253 blocs) | 102 | 72,9 % | 89 % | 17 | oui, par l'îlot |
| vaisseau (aimée) | 1 221 (Bleu 1 398) | 32 × 48 | 9 (1) | structure au milieu | 53 | 53,1 % | 74 % | 30 | oui |
| glacier (retirée) | 2 338 | 45 × 103 | 23 (1) | oui | 20 | 21,3 % | 89 % | 18 | non |
| jade (retirée) | 2 265 | 46 × 104 | 25 (1) | oui | 25 | 3,4 % | 82 % | 25 | non |
| engrenages (retirée) | 3 693 | 55 × 107 | 15 (1) | oui, grande (1 672 blocs) | 53 | 22,1 % | 76 % | 17 | oui, 4 611 cases |

## Pourquoi les trois maps retirées sont mauvaises

- **Taille.** Glacier et jade font 1,8 fois la surface des maps aimées ; engrenages, 2,9 fois. Avec 45 à 55 blocs de profondeur par camp, les moutons tombent dans le vide, on ne voit plus l'adversaire et les parties traînent.
- **Île centrale et liaisons.** Les trois ont un îlot en x = 0 qui touche presque les camps (largeur minimale du vide : 1 bloc). Sur engrenages, le parcours à pied passe d'un camp à l'autre (4 611 cases adverses atteintes), par l'îlot et par les dirigeables.
- **Décor géant au milieu des tirs (engrenages).** Dirigeables, usines à cheminées et beffrois montent jusqu'à y 62. Il y a 1 451 colonnes « repère » sur 6 681, et des murs coplanaires jusqu'à 30 blocs. On ne voit ni ne vise rien, d'où « horrible à jouer ».
- **Pauvreté (glacier, jade).**
  - Seulement 20 à 25 types de blocs.
  - Jade n'a que 3,4 % de détail.
  - Les arbres sont des sucettes de feuilles (75 % des « toits » sont des feuilles).
  - 5 îles par camp, identiques en miroir parfait (99,9 %), toutes au même niveau (8 % de colonnes à plusieurs niveaux).
  - Le mélange de blocs est élevé (18 à 28 %), et jusqu'à 53 % sur engrenages.
- **Ce que font les maps aimées.**
  - Camps compacts : 26-32 × 48-92, 1 100 à 1 500 colonnes.
  - Vide médian de 9 à 21 blocs.
  - Couvert proche (74-90 %).
  - Plusieurs niveaux (multi-niveaux 34-37 %).
  - Un repère par camp (tour, château d'eau).

## À signaler (hors de cette tâche)

Avec le même critère de parcours (généreux : sauts de 5,5 blocs, chute planée), **rose, vert et vaisseau sont traversables par leur îlot central**. Ces îlots étaient fermés de barrières dans les maps 1.8, et ces barrières ont été retirées en 4.0.2. Le correctif « ligne x = 0 » de la 4.5.0 bloque désormais cette traversée sur toutes les maps (voir le rapport).
