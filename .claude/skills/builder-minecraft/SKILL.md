---
name: builder-minecraft
description: Construire des maps et bâtiments Minecraft de niveau builder professionnel, surtout générés par code dans un plugin Paper (fill/set de blocs) — mini-jeux, lobbys, villes, châteaux, terrain, arbres, intérieurs. À charger AVANT d'écrire ou de modifier un builder de map (City.java, Builder.java, MapBuilder…), de dessiner une arène, de choisir une palette, ou de juger la qualité d'une map existante.
---

# Builder Minecraft professionnel

Le but : des maps qui ressemblent à celles des équipes de build (Fort de Battlebound, Stade de la forêt, Dead Center), pas à des boîtes procédurales. Ce fichier donne la méthode, les cibles chiffrées et les pièges ; le détail est dans `reference/`.

- `reference/regles.md` — toutes les règles (workflow, volumes, façades, toits, dégradés, terrain, arbres, éclairage, code Java, level design, 10 styles, échelle).
- `reference/palettes.md` — 14 palettes 60/30/10, terrain chiffré, dégradés clair → sombre.
- `reference/mesures.md` — mesures de 6 maps de builders (palette, murs, relief, couverts, symétrie, toits).
- `exemples/*.png` — vues du dessus et coupes de ces maps : **les regarder** avant de dessiner une map du même genre.
- `outils/analyze.py` — le script de mesure (schem v2/v3, pbm, sections) : sert aussi à mesurer MES maps générées.
- `reference/sources.md` — d'où viennent les règles.
- `reference/budgets.md` — À LIRE EN PREMIER pour une map d'EEMICRAFT : nos maps sont la référence (taille, tolérance +30 %), règles strictes par mode (Sheep Wars sans île centrale…), détail sur les côtés sans casser la jouabilité.

## 1. Méthode (toujours dans cet ordre)

1. **Brief de jeu** : nombre de joueurs, équipes ou FFA, armes (mêlée / arc / paintball), durée, objectif. En déduire la taille (surface par joueur : mêlée 150-250 m², arc/paintball 300-500 m², grandes maps d'équipe jusqu'à 1 600-3 100 m² mesurés), la traversée base-à-base (20-40 s, 4,3 blocs/s à pied).
2. **Thème et palette** : choisir un style (`regles.md` §K) et une palette (`palettes.md`) ; un point focal / repère central ; une couleur par équipe sur un grand volume.
3. **Gabarit jouable** (blocs témoins, aucun détail) : spawns protégés, objectifs, au moins 2 chemins par objectif, couverts, niveaux. Vérifier lignes de vue et symétrie de gameplay AVANT tout décor.
4. **Volumes** : 3-4 volumes par bâtiment, plans en L/T, hauteurs variées, silhouette lisible.
5. **Façades et toits** : 3 plans de profondeur, socle, bandeaux, fenêtres en retrait, toits en pente avec débord.
6. **Matériaux** : 60/30/10, variantes **en grappes par bruit**, dégradé vertical sombre → clair.
7. **Terrain** : heightmap continue (simplex FBM), surface pilotée par masques, transitions bruitées, limites naturelles.
8. **Usure, végétation, détails, éclairage** — dans cet ordre, jamais à la place d'une géométrie propre.
9. **Contrôle** : mesurer (§4), regarder un rendu du dessus + une coupe, corriger, puis seulement livrer.

## 2. Cibles chiffrées (tirées des maps de builders)

| Domaine | Cible |
|---|---|
| Variété | ≥ 40 types de blocs sur la map, dont ≥ 20 à 0,1 % ou plus |
| Palette d'un bâtiment | 1 principal 55-70 %, 2-3 variantes de la même famille 10-20 % chacune, 1 finition contrastée 5-10 % (angles, bandeaux, encadrements) |
| Mélange local | **< 15 %** sur le bâti (variantes en grappes / bandes / règles, jamais tirées bloc par bloc) |
| Détail | ≥ 15 % des blocs visibles du bâti en escaliers, dalles, murets, barrières, trappes, vitres, tapis, lanternes, chaînes ; 5-9 % de végétation visible au sol |
| Façades | aucun pan coplanaire > 7 blocs (fantasy, château) ou > 12 (urbain) ; ≥ 50 % des cellules de façade à 3 couches ou plus |
| Toits | en pente (escaliers) sur ≥ 50 % des maisons, débord de 1 sur les 4 côtés ; ≥ 1 hauteur de toit différente pour 4 bâtiments, ±1 à 3 entre voisins ; toits plats seulement en ville (acrotère en dalles + éléments techniques) |
| Relief | 12-30 % de marches de 1 entre voisins, 4-13 % de ressauts de 2-4 ; ≥ 3 niveaux de jeu (sol, terrasses +3/+5, toits / remparts +8/+12), chacun ≥ 3 % de la surface |
| Verticalité (maps de combat) | 25-75 % de colonnes à plusieurs niveaux (passerelles, balcons, ponts, remparts) |
| Couverts | 75 % des positions avec un couvert à ≤ 3 blocs (un couvert au moins tous les 6 blocs) |
| Lignes de vue (combat rapproché) | médiane ≤ 25, p90 ≤ 60, < 25 % de lignes ≥ 32 blocs ; casser les axes droits (courbes, décrochements, obstacles) |
| Symétrie | plan de jeu en miroir / rotation ; **décor asymétrique** (~60 % des blocs non en miroir) |
| Repères | 7-27 % des colonnes ≥ 12 au-dessus de la médiane ; 1 repère central unique |
| Limites | collines, forêt dense, eau, falaise, vide ; barrières invisibles seulement derrière ce décor |
| Îles | cône inversé avec stalactites, herbe + 1-2 couches de terre, sans poivre et sel |

## 3. Interdits (défauts trouvés dans mes builders, à ne plus jamais produire)

- **Mur d'un seul plan** sur tout un périmètre (`x==x1||x==x2||z==z1||z==z2` rempli d'un bloc) — City.java, casino (pan de 61 blocs).
- **Variante tirée au hasard bloc par bloc** (`chance(0.04) ? cobblestone : wall`, `pick(...)` par case) = poivre et sel. Remplacer par un bruit simplex de période 4-8 ou une règle (bas moussu, haut fissuré, bandes).
- **Toit plat sans débord** avec parapet du même matériau sur des maisons.
- **Fenêtre sans relief** (ni allège, ni linteau, ni volet) — MAIS la vitre reste TOUJOURS dans le plan du remplissage du mur (P-1). Le « retrait » vient des poteaux/appuis en saillie (plan P), jamais d'une vitre posée un plan plus à l'intérieur (P-2) : elle flotte alors dans la pièce, mur percé devant (bug de Facade, Chalet, Paquebot, vu par Esteban le 17/11/2026).
- **Sol tout à `y = G`** (0 % de marches) et terrain plat.
- **Rangées de boîtes identiques** à écart constant, alignées sur une grille.
- **Mur de barrières invisibles** comme limite visible.
- **Éclairage uniquement en blocs `light`** : il faut des sources visibles (lanternes, lampadaires) ; `light` seulement en complément discret.
- **Symétrie à 100 % des blocs** (décor compris) : le décor doit différer.
- **Pièce ou couloir de 2 de haut**, porte sur le vide, escalier sans 3 blocs libres au-dessus de chaque marche suivie d'une marche plus haute (en passant d'une marche à l'autre, la tête est encore à la hauteur de la marche du dessus) et au-dessus de la case d'approche (pas de linteau de porte juste devant la 1re marche). La trémie du plancher s'ouvre donc une marche plus tôt.

## 4. Boucle de contrôle (obligatoire avant de livrer une map)

1. **Contrôles dans le code**, journalisés à la fin du build : mixité (aucun bloc > 70 % d'une zone 10×10×1), alignements (≤ 7 blocs alignés sur une crête / un rivage), accès (hauteurs de passage, portes), nombre de types de blocs.
2. **Mesure hors ligne** : exporter la zone en `.schem` (FAWE `//copy` + `//schem save`, ou écrite par le plugin) ou en `.pbm`, puis `python -I outils/analyze.py <fichier.schem|.pbm> [joueurs]` (Pillow requis ; régions de monde : `outils/world.py` + `nbt.py`, à brancher dans `vol.py`). Résultats dans `out/<id>.json`, `out/<id>_dessus.png`, `out/<id>_coupe.png`. Comparer au tableau §2 et à `reference/mesures.md`.
3. **Rendu** : regarder `out/<id>_dessus.png` et `<id>_coupe.png` (Read), à côté des `exemples/` du même genre. Se demander : silhouette lisible ? repère visible ? façades cassées ? limites naturelles ? sol varié ?
4. Écrire dans CLAUDE.md les chiffres obtenus, et ce qui n'a pas été vu en jeu.

## 5. Boîte à outils Java à réutiliser (à écrire une fois, copier dans chaque plugin)

- `Noise` : OpenSimplex2 2D/3D (domaine public, ~100 lignes) ; `fbm(x,z,octaves=4,pers=0.5,lac=2)`, `ridge = (1-|n|)²`, domain warp ×10-20. Modèle existant : `EemiElytra/.../Builder.java` (strates et roche par simplex, le meilleur de mes builders).
- `Palette` : liste ordonnée clair → sombre + poids ; `pick(x,y,z,base)` = `t = clamp(base + 0.25·simplex(x/6,y/6,z/6) + 0.1·rand)`, indice `floor(t·(n-1))`. Jamais `Random` seul.
- `Facade` : pose un mur en 3 plans (socle +1 sur 1-2 rangs, poteaux +1 tous les 4-6, remplissage 0, fenêtres −1 avec allège en escalier retourné et linteau en dalle, corniche en escaliers retournés +1, bandeau tous les 5-7 de haut, angles en finition).
- `Roof` : pignon / croupe / composé (`max(h1,h2)`), pente par profil (bloc 63°, escalier 45°, dalles 22,5°, Bézier), débord 1-2 en escaliers retournés, faîte en dalles, discrétisation par partie fractionnaire.
- `Shape` (sdf) : boîte, cylindre, sphère, ellipsoïde, cône, arche ; union / différence / coque / déformation par bruit — rochers, îles en cône inversé, statues, coupoles.
- `Terrain` : heightmap FBM + masques (pente → roche, eau → plage, altitude → neige) + érosion (lissage 3×3, melt, fill).
- `Tree` : tronc décalé de 1 tous les 3-5, 2-3 branches inégales, boules de feuilles bruitées en bout de branche, `persistent=true`.
- `Check` : les contrôles du §4.1.
- États de bloc via `BlockData` (`facing/half/shape/axis`), IDs vérifiés contre `Material` de paper-api 26.3 (`iron_chain`, `short_grass`…).

## 6. Rester honnête

Je ne vois pas la map pendant que je la construis : je juge sur des rendus et des chiffres. Toujours dire ce qui n'a pas été vu en jeu, demander une capture aux joueurs pour les points de vue importants, et proposer une finition à la main par un builder de l'équipe pour les maps phares. Si une bonne map gratuite existe (CurseForge, licence compatible), l'adapter vaut souvent mieux que tout générer.
