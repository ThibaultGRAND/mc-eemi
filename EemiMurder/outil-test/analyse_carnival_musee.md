# Carnival et Musée : analyse des maps d'origine, refonte 1.8.0

Mondes lus hors ligne (régions 26.x converties par Paper, palette au format mixte : chaînes, `{'': nom}` et `{id, properties}`) par `scratchpad/mmscripts/extract26.py` ; mesures par `analyze.py` sur la boîte de jeu (y 62 et plus). Vues : `.claude/skills/builder-minecraft/out/mm_orig_*`.

## Carnival (mm_carnival)

- Île flottante (cône de pierre jusqu'en y 43), sol plat en y 64, boîte x -56..56, z -55..56, mur de barrières sur le bord, haie de feuilles devant.
- Plan : entrée au sud (x 0), grande allée nord-sud jusqu'au chapiteau, boucle de chemins au nord (x ±40, z -42 / +2).
  - chapiteau rouge et blanc (centre 0,-22, r 13) avec piste ;
  - carrousel bleu ciel (-28,-24), grande roue (28,-22) ;
  - labyrinthe de verre (-40..-24, 12..30), grange sombre avec grenier (22..38, 14..28) ;
  - tente violette, une dizaine de petits stands carrés en laine à damier, rangées de stands à l'entrée.
- Ce qui marche : plan lisible en boucle, repères forts (chapiteau, roue), cachettes du labyrinthe et de la grange.
- Ce qui est pauvre :
  - 1 seul niveau (81 % des positions au sol), 12 % de colonnes à plusieurs niveaux ;
  - grands vides d'herbe : couvert à 3 blocs ou moins sur 59 % des positions seulement, lignes de vue p90 66 blocs, 34 % des lignes à 32 blocs ou plus ;
  - stands = cubes de laine sans auvent ni comptoir, 64 types de blocs, 4,4 % de détail visible ;
  - limite = mur de barrières visible derrière une haie.

## Museum (mm_museum)

- Même île, bâtiment de quartz x -34..34 / z -26..26, deux niveaux (sols y 64 et y 71), toit plat en y 78 (marchable dans l'analyse, inaccessible en jeu), parvis à fontaine au sud, cour avec voiture au nord.
- Plan : grille 2 × 2 salles par aile + travée centrale (escaliers nord et sud, long objet central), vide central à l'étage.
  - rez-de-chaussée : vitrines (nord-ouest), salle sombre (nord-est), sarcophage en grès (sud-ouest), bibliothèque (sud-est) ;
  - étage : salle grise, aquarium en prismarine, salle à damier, salle à colonnes.
- Ce qui marche : boucles par les deux escaliers, salles thématiques, deux niveaux de jeu.
- Ce qui est pauvre :
  - façades plates (47,7 % des cellules d'une seule couche, pan coplanaire de 97 blocs) ;
  - intérieurs presque vides : couvert à 3 blocs ou moins sur 49 % des positions, lignes de vue p90 62 ;
  - aucun repère : 0 colonne à 12 blocs au-dessus de la médiane.

## Ce qui est gardé

- Thème, boîte min/max (identique), plan général et circulations, hauteurs : sol y 64 (pieds en 65), étage du musée y 71 (pieds en 72).
- Fête : allée sud-nord, boucle nord, chapiteau au centre, carrousel à l'ouest, roue à l'est, palais des glaces au sud-ouest, maison hantée (ex-grange) au sud-est.
- Musée : emprise du bâtiment, grille des salles, vide central, parvis à fontaine, cour au nord.

## Ce qui change (level design)

- Fête :
  - kiosque à musique sur la place centrale (casse la ligne de vue de l'allée) ;
  - chariots de marchands sur les chemins longs ;
  - stands à comptoir sur deux rangs avec ruelles derrière ;
  - niveaux en hauteur : gradins du chapiteau, quai des montagnes russes (deux escaliers), grenier de la maison hantée (deux escaliers), toboggan en spirale (échelle dedans, spirale dehors) ;
  - labyrinthe tressé (aucune impasse) avec 3 entrées ;
  - limite = palissade de forain à contreforts, barrières invisibles seulement au-dessus.
- Musée :
  - 5 escaliers au lieu de 2 ;
  - 7 portes vers le jardin, qui fait le tour du bâtiment (boucle extérieure) ;
  - galerie à balustrade autour du vide.
