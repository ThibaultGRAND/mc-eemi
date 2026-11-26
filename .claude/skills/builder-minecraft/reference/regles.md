# Règles de construction (version détaillée)

Chaque règle est formulée pour être appliquée par un programme. « [synthèse] » = recommandation croisée, pas de source Minecraft chiffrée : à valider en jeu.

## A. Workflow (ordre des équipes de build)

1. **Gabarit d'abord** : volumes en un seul bloc témoin, sans détail. Valider emprise, silhouette, gameplay (spawns, lignes de vue, chemins). Ensuite seulement : profondeur → matériaux → usure → déco.
2. **Pipeline** : heightmap → masques (pente, hauteur, distance à l'eau / aux chemins) → bâtiments en gabarit → façades et toits → palette en dégradé → usure → végétation et détails → éclairage → contrôles automatiques (C10).
3. **L'usure vient après une géométrie propre**, jamais à la place.
4. **Modules** : une fenêtre, un pilier, une lanterne se définit une fois et s'instancie avec rotation, miroir et petite variation. Jamais de copier-coller identique visible.

## B. Volumes, silhouette, composition

1. Jamais une boîte unique : 3 à 4 parallélépipèdes de hauteurs et largeurs différentes ; volumes secondaires = 1/3 à 1/2 du principal.
2. Plans en L, en T, ou avec décalages ; chaque aile a une fonction.
3. **Point focal** par map / quartier : ≥ 25 % plus haut ou plus large que ses voisins, matériau ou lumière unique ; la densité de détails augmente en s'en approchant.
4. **Silhouette** : lisible en ombre noire. Test : projection latérale (max Y par colonne), aucun segment de ligne de toit > ~12 blocs à hauteur constante.
5. Composition : détails denses à 20-80 blocs du point de vue, formes simplifiées à 80-200, silhouette au-delà ; point focal vers 1/3 du champ ; 20-40 % d'espace négatif (ciel, eau, plaine).
6. **Largeurs impaires** (5, 7, 9) pour un axe et un faîtage uniques. Travée de 5 = pilier 1 + fenêtre 3 + pilier 1 ; travée de 7 = 2 fenêtres + pilier central. Porte double → largeur paire.
7. **Asymétrie maîtrisée** : un relief retourné sur son axe doit différer sur ≥ 30 % des points. Bâtiments : symétrie sur la façade d'apparat, le reste cassé (annexe, cheminée décalée).

## C. Murs et profondeur

1. **Façade en 3 plans** : remplissage en retrait, plan principal, saillies. Poteaux tous les 3 à 6 blocs, en saillie de 1, matériau secondaire (bûche écorcée `axis=y`). Vitres au centre du bloc = déjà 0,44 bloc d'ombre.
2. Tout mur plus large que 5 blocs a au moins une couche de profondeur.
3. La profondeur est structurelle : débords, contreforts, embrasures, appuis — pas de relief gratuit.
4. Bandeau horizontal (dalles / escaliers) à chaque plancher et sous le toit ; chaînage d'angle contrasté aux 4 coins.
5. **Socle** de 1 à 3 rangs, plus sombre, dépassant de 0 à 1 ; fondation descendue jusqu'à ce qu'aucune terre ne se voie sous les murs, y compris côté bas d'une pente. Couronnement : toit en débord.
6. Encorbellement (médiéval) : étage supérieur en saillie de 1, escaliers retournés en corbeaux dessous.
7. **Fenêtres** enfoncées d'au moins 1, appui en dalle / escalier, linteau en escalier retourné ; volets en trappes ouvertes ; jardinière (dalle + pots) à 30 %.
8. Micro-relief (escaliers, dalles, trappes, murets, boutons, chaînes, barreaux, clôtures) pour les **bordures**, pas pour le remplissage.
9. Espace négatif dans le détail : des parties sobres encadrent les zones riches.
10. **Contrôles automatiques après génération** (à coder et journaliser) :
    - mixité : dans toute zone 10×10×1, aucun bloc > 70 % des cellules ;
    - bords : pas plus de 7 blocs alignés sur le même X ou Z sur un rivage ou une crête ;
    - silhouette : sur un relief ≥ 20 blocs, 8 échantillons espacés de ≥ 5 varient de ≥ 3 en Y ;
    - fondation : chaque échantillon de paroi a ≥ 3 types de blocs ;
    - accès : pas de pièce de 2 de haut, 2 blocs libres au-dessus des escaliers, aucune porte sur le vide.

## D. Toits

1. Pentes : blocs pleins 63,4° ; escaliers 45° ; dalles haute/basse alternées 22,5°. Motif dalle + escalier retourné + escalier = 5 de montée pour 7. Médiéval / nordique 45-60°, japonais 20-30°, chaume ≥ 45°.
2. Hauteur du toit = 1 à 2 étages (médiéval : 40-50 % de la hauteur totale). Au-delà de 15×15 d'emprise, découper en plusieurs toits (T, L, croix, H).
3. **Débord obligatoire** de 1 à 2 (japonais 2 à 3), escaliers retournés en sous-face.
4. Toits composés : générer le plus haut d'abord ; hauteur combinée = `max(h1(x,z), h2(x,z))` ; noue en V.
5. Faîte en bloc plein ou dalle ; pignon fermé en matériau du mur ; coins de croupe en escaliers `shape=outer_*`.
6. Courbure : profil `h(d) = a·d^p` (p 1,3-1,6, rives relevées) ou Bézier, discrétisé par la partie fractionnaire (I4).
7. Usure logique : plus clair au faîte exposé, plus sombre / moussu à l'égout et dans les noues ; pas de taches aléatoires.
8. Cheminée 2×2 ou 3×3 dépassant le faîte de 2 à 3 ; lucarnes sur les toits de plus de 7 de large.
9. Intérieur : chevrons en escaliers, poutres dans la PETITE largeur.

## E. Matériaux et dégradés

1. 60/30/10 ; 3-4 matériaux de base (2-3 petit bâtiment, 4-5 grand).
2. Chaque matériau = famille de 4 à 8 variantes de même valeur (ex. 80 % `stone_bricks`, 15 % `cracked_`, 5 % `mossy_`), ou 5-15 % de bruit de variante sur toute surface uniforme.
3. Palette ordonnée par valeur (clair → sombre), puis teinte, puis texture ; un dégradé ne saute jamais d'échelon.
4. Dégradé vertical : sombre en bas, clair en haut (cuivre : l'inverse, plus oxydé en bas).
5. **Dégradé en amas** : centres irréguliers, le plus clair sur le point à mettre en valeur ; ni rangées, ni colonnes, ni motif répété, ni bandes horizontales.
6. Usure ciblée : coulures sous les fenêtres, angles, base, sous les débords ; fissuré près du sol ; bords d'arches plus clairs.
7. Contraste : un accent complémentaire (terracotta orange + prismarine), le reste en harmonie ; deux essences de bois complémentaires ; une seule pierre par bâtiment.
8. Sol de rue : ornières sombres au centre ou l'inverse selon la teinte de l'herbe.

## F. Terrain organique

1. Pas de « ziggourat » (couches rectangulaires empilées) : un seul champ de hauteur continu.
2. Empreinte bosselée (ni cercle ni ovale) ; avant le sommet chaque palier rentre de 1 et monte de 2 à 4 ; grandes formes arrondies, dents à petite échelle.
3. Fréquence selon la pente : pente douce = basses fréquences (détails ≥ 15-20 blocs) ; paroi verticale = haute fréquence permise ; **interdit** : hauteur aléatoire par colonne.
4. Surface par masques : roche si pente > ~1,2 (> 50°), herbe sur le plat, plage à 2-4 de l'eau, neige au-dessus d'une ligne bruitée ; substrat ≥ 2 couches.
5. Transitions de biome / palette sur 10 à 30 blocs, par bruit.
6. Falaises : saillie aléatoire à chaque Y, encoche de 1-2 à la ligne d'eau, retrait de 1 tous les 4-6 Y, éboulis (gravier, pavé) au pied.
7. Rivières : tracé sinueux, berges douces, eau continue jusqu'au fond, sable et argile sur 1-3 blocs, végétation dense.
8. Végétation : grappes de 3 à 7 plantes espacées de 2 à 8 ; > 40 % de couverture près de l'eau, < 5 % sur les pentes exposées ; lisière sur 4-12 blocs ; 5-15 % de taches nues dans l'herbe ; bûches tombées de 3-6 en lisière.
9. Espacement des arbres : forêt 3, bosquet 6, parc 10, sujet isolé 15.
10. Érosion par code : lissage = moyenne 3×3 de la heightmap en 1-3 passes (modérée sur les falaises) ; melt = retirer un bloc de surface ayant ≥ 4 faces à l'air ; fill = remplir un trou d'air entouré de ≥ 4 faces pleines.

## G. Arbres sur mesure

1. Tronc jamais droit : décalage de 1 tous les 3 à 5 Y ; base évasée (3×3 pour un grand arbre), racines (bûches, `mangrove_roots`, `rooted_dirt`) sur 2-3 de haut.
2. 2 ou 3 branches de longueurs et hauteurs différentes ; si l'arbre penche, la plupart des branches du côté de la pente.
3. Coudes en escaliers / dalles de bois écorcé ; rameaux en clôtures.
4. Feuillage en boules au bout des branches, plus haut que bas, contour bruité : rayon effectif `R·(0,7 + 0,3·bruit)` ; 2 types de feuilles + azalées.
5. Feuilles `persistent=true` ; arbres héros uniques réservés aux points focaux.

## H. Éclairage

1. En 26.x les monstres n'apparaissent qu'à lumière bloc 0 : l'éclairage est esthétique.
2. Chaud : `lantern`, torche, `shroomlight`, `glowstone` ; froid : `soul_lantern`, `sea_lantern`, `end_rod` ; une dominante par zone ; chaud + froid = ambiance magique.
3. Éclairage caché : sous tapis / dalle, derrière trappe, sous verre ; toujours une source derrière un vitrail. Bloc `light[level=N]` invisible pour les rues de nuit.
4. Rythme : lanternes sur les axes, lampadaires tous les 8 à 12 blocs [synthèse] ; la lumière guide vers les objectifs.

## I. Traduction en code (Java)

1. **Bruit** : OpenSimplex2 (domaine public). Heightmap = FBM 4-5 octaves, persistance 0,5, lacunarité 2 ; crêtes = `(1 - |n|)²` ; domain warp léger (x, z décalés par un autre bruit × 10-20).
2. **Dégradé de palette** : `t = clamp(base(y) + 0.25·simplex(x/6, y/6, z/6) + 0.1·rand)`, indice = `floor(t·(n-1))`. La partie déterministe donne le sens, le bruit basse fréquence fait les amas. Jamais de `Random` par bloc seul.
3. **Formes via `Shape`** (`sdf(x,y,z)`) : union = min, différence = max(a, −b), `NoiseDeformed` = sdf + amplitude·bruit, `Shell` = |sdf| < épaisseur. Rocher = ellipsoïde déformé ; statue = union d'ovoïdes aux articulations.
4. **Bézier pour toits et arches** : échantillonner tous les 0,1 bloc ; par colonne, partie fractionnaire de h : < 0,25 rien, 0,25-0,75 dalle `type=bottom` (ou escalier), au-delà bloc plein ; escalier orienté vers la descente, `half=top` en sous-face. Arche gothique = deux arcs de rayon = largeur, centrés aux pieds opposés.
5. **Boucle de vérification** (C10) avant de déclarer la map construite, avec log.
6. États de bloc via `BlockData` (`facing/half/shape/axis/waterlogged`) ; IDs vérifiés contre `Material` de paper-api 26.3.

## J. Level design de mini-jeux

1. Gabarit jouable avant le décor : spawns, objectifs, chemins en blocs témoins.
2. Symétrie : miroir ou rotation 180° en équipes ; rotation 90° ×4 pour 4 équipes ; le décor peut différer par équipe (« symétrie asymétrique »). FFA : pas de symétrie stricte.
3. Au moins 2 chemins vers chaque objectif (rapide et exposé / lent et couvert) ; depuis le milieu, même temps vers chaque base.
4. Lignes de vue : longues vers les objectifs, courtes près des combats ; pas de ligne dégagée > ~40 blocs entre spawns adverses en jeu d'arc ; couper les diagonales par un obstacle ≥ 2 de haut tous les 15-25 blocs [synthèse].
5. Couverts : bas (1 bloc, on tire par-dessus) et haut (≥ 2, on bloque) ; paintball / arc : un couvert tous les 6-10 blocs de parcours, en alternant [synthèse].
6. Spawns protégés : hors du feu ennemi immédiat, couvert proche, zone de 8×8 à 10×10, 3 à 5 points de réapparition par équipe.
7. Repères : chaque zone a un repère unique ; couleur d'équipe sur les bases ; lumière et contraste vers les objectifs.
8. Verticalité : 2 à 3 niveaux, avantage de hauteur compensé par l'exposition ; objectif jamais derrière un saut difficile ; chutes mortelles limitées.
9. Taille : CTF 100×100 à 200×200 ; BedWars ~300×300, îles de 45×45 espacées d'~32. [synthèse] 150-250 m² jouables par joueur en mêlée, 300-500 à l'arc / paintball ; traversée base-à-base de 20-40 s (4,3 blocs/s à pied, 5,6 en sprint).
10. **Limites naturelles** (falaise, eau profonde, vide, haie dense, ruines, montagnes) ; la barrière invisible ne sert que de garde-fou derrière une limite visible.
11. Loot fort en zone risquée mais visible, loot courant à l'abri ; traverser la map pour tout récupérer.
12. Lisibilité : sol de jeu calme ; détails denses sur les murs, en hauteur, hors des chemins ; objets de gameplay très contrastés.

## K. Styles (valeurs)

- **Médiéval** : étage 3-4, bâtiment 6-12 de large ; colombage (poutres à chaque plancher, poteaux aux angles et tous les 3-4, diagonales dans les grands panneaux) ; fenêtres 1-2 de haut ; toit 45-60°, débord 1-2.
- **Gothique** : hauteur ≥ 3 × largeur ; fenêtres à 60-70 % de la hauteur du mur, lancettes 1:3 ; contreforts tous les 4-8 ; rosace de 5-9 ; arches de largeur impaire.
- **Japonais** : plancher surélevé de 1-2, véranda 2-3 ; pente 2:3 (~34°), débord 2-3 ; temples à toits étagés de 2 à 5 niveaux.
- **Moderne** : horizontalité, 40-60 % de vitrage, porte-à-faux de 3-5, étage 4-5, toit plat, alignements parfaits, une seule espèce végétale.
- **Château** : murs de 3-5 d'épaisseur et 6-10 de haut, base en talus ; chemin de ronde 2-3 ; créneaux 2 pleins / 1 vide ; tours de 5-12 de diamètre, 1,5-2 × la hauteur du mur, tous les 15-25 ; meurtrières 1×2 ou 1×3.
- **Tour de mage** : diamètre 7-11, hauteur 3-5 × diamètre, léger effilement ; un niveau tous les 5-6 ; toit conique = 1/3 de la tour, débord 2-3 ; dégradé de couleur bas → haut, éléments lumineux.
- **Désert / méditerranée** : murs 2-3, toit plat avec parapet 1-2 ; coupole de hauteur = rayon ; cour de 1/4 à 1/3 de l'emprise avec fontaine ; arcs en fer à cheval et brisés.
- **Nordique** : maison longue largeur:longueur ~1:3 ; toit très pentu descendant presque au sol, faîtage croisé aux pignons ; soubassement en pierre, sol en terre battue.
- **Steampunk / industriel** : base brique / pierre symétrique, accents cuivre ; un tuyau relie toujours deux choses ; cuivre vieilli en dégradé.
- **Ruines / post-apo** : partir d'un bâtiment complet puis le dégrader : retrait par masque bruité plus fort en haut et aux angles, bords cassés en escaliers / dalles, éboulis proportionnels au volume retiré, variantes fissurées / moussues, lierre, mobilier cassé, éclairage faible.

## L. Échelle de référence (blocs)

| Élément | Valeurs |
|---|---|
| Plafond | minimum 3, confortable 4, grand 5-8, monumental 9-12 |
| Couloir | 3×3 standard, 4×3 large |
| Porte | 1×2, double 2×2, portail 3×3 |
| Pièce | chambre 5×6, salon 8×10, grand hall 15×20×8 |
| Étage | 4-5 |
| Colonnes | espacement 6-8 |
| Rue | sentier 1, allée 2, principale 3, route 5 |
| Fontaine (diamètre) | 3, 5 ou 7 |
