# Retextures de blocs vanilla du pack Dernier Refuge

Générées par `gen_blocs.py` (appelé par `gen_refuge.py`), aperçus avant / après : `../apercus/blocs.png`.
Le pack Refuge n'est chargé que dans les mondes du mode : ces remplacements ne touchent pas le reste du serveur.
Aucune forme de bloc n'est changée (modèles et UV vanilla gardés) : les objets posés par le jeu (étagère,
bibliothèque ciselée, vault) restent à la place exacte du vanilla. Rien n'a encore été vu en jeu.

Le pack EEMICRAFT publié (b8b58bca, et `EemicraftPack.zip` d'a_deposer_installes) ne remplace AUCUN bloc vanilla
(seulement `font/default|uniform`, `items/emerald|lead|paper`) : aucune collision.

## Méthode, bloc par bloc

| Bloc | Fichiers remplacés | Méthode |
|---|---|---|
| Étagères (13 essences) | `textures/block/<bois>_shelf.png` (32×32) | texture vanilla recolorée + décor dans les zones UV (voir table) |
| Tonneau | `textures/block/barrel_side|top|top_open|bottom.png` | redessiné (caisse clouée, pochoir) ; le blockstate vanilla oriente déjà les 6 directions × ouvert/fermé |
| Coffre | `textures/entity/chest/normal|_left|_right.png` + `christmas*` (24-26/12) | même mise en page UV 64×64, recoloré kaki, coins métal, pochoir |
| Coffre en cuivre (4 oxydations × simple/gauche/droite) | `textures/entity/chest/copper[_exposed|_weathered|_oxidized][_left|_right].png` | acier bleu-gris cabossé, rouille croissante avec l'oxydation, fermoir laiton |
| Pot décoré | `textures/entity/decorated_pot/decorated_pot_side|base.png` + les 23 `*_pottery_pattern.png` | jerrican rouge : nervure en X, bouchon noir ; les motifs des tessons restent en relief sur la tôle rouge |
| Bibliothèque ciselée | `textures/block/chiseled_bookshelf_side|top|occupied|empty.png` | classeur d'archives : case pleine = tiroir avec étiquette et chemises, case vide = tiroir arraché (trou noir + glissières) |
| Vault (normal et sinistre) | `textures/block/vault_*` (16 fichiers) | coffre-fort à molette : porte, molette, poignée, voyant (éteint = rouge sombre, actif = vert, éjection = jaune et porte entrouverte ; sinistre : rouge/orange). Texture opaque : l'objet affiché à l'intérieur du vault n'est plus visible |
| Sable / gravier suspects (stades 0-3) | `textures/block/suspicious_sand_N|suspicious_gravel_N.png` | gravats : plâtre + briques cassées (sable), béton + armatures rouillées (gravier), plus de fissures à chaque stade du pinceau |
| Chêne pâle (planches) → barricade | `models/block/pale_oak_planks.json` → `refuge:block/barricade` | modèle vanilla redirigé : les escaliers, dalles, barrières en chêne pâle gardent la texture vanilla |
| Magnétite → poste radio | `models/block/lodestone.json` → `refuge:block/radio_side|radio_top` | modèle `cube_column` redirigé |
| Cible → balise de signal | `models/block/target.json` → `refuge:block/balise_side|balise_top` | modèle `cube_column` redirigé (bandes rouge/blanc, « SOS », fusée rouge sur le dessus). Le comportement redstone de la cible ne change pas |
| Bloc de redstone → générateur / batterie | `models/block/redstone_block.json` → `refuge:block/generateur` | modèle `cube_all` redirigé (il émet toujours du courant) |
| Vitres (verre + 16 verres teintés, et leurs vitres) | `textures/block/glass.png`, `<couleur>_stained_glass.png`, `glass_pane_top.png`, `<couleur>_stained_glass_pane_top.png` | poussière dans les coins, fissure blanche ; le verre teinté (`tinted_glass`) n'est pas touché |
| Herbe | `textures/block/grass_block_top.png`, `grass_block_side_overlay.png`, `grass_block_side.png`, `textures/colormap/grass.png`, `colormap/foliage.png` | plaques sèches dans les textures grises ; colormaps désaturées et tirées vers l'olive (toute l'herbe et le feuillage teintés par le biome du monde deviennent plus ternes) |
| Feuilles teintées (chêne, bouleau, sapin, acajou, acacia, chêne noir, palétuvier) | `textures/block/<bois>_leaves.png` | quelques trous et taches sombres ; les `.mcmeta` vanilla des feuilles restent ceux du jeu |
| Lanterne, lanterne des âmes | `textures/block/lantern.png` + `.mcmeta`, `soul_lantern.png` + `.mcmeta`, `textures/item/lantern|soul_lantern.png` | ampoule pâle à filament, animation irrégulière (allumée / faible / éteinte, un « clignotement » toutes les 2-4 s). Seule l'image clignote : la lumière émise ne change pas |
| Planches (13 essences) | `textures/block/<bois>_planks.png` | désaturées, rayures, taches, trous de clous. Escaliers, dalles, barrières, portillons et panneaux en bois suivent (même texture) |
| Briques, briques de pierre (+ moussues, craquelées) | `textures/block/bricks|stone_bricks|mossy_stone_bricks|cracked_stone_bricks.png` | désaturées, suie, coulures, petite fissure |

161 fichiers vanilla remplacés, tous contrôlés contre le jar 26.3 par `verifier_pack.py --vanilla-overrides`
(existence, taille identique pour les entités et colormaps, largeur identique sinon).

## Étagères : essence → type de pièce

Les objets posés sur une étagère sont dessinés par le jeu sur le dessus de la planche du bas : la planche reste où
elle est, seul l'habillage change (rampe de couleurs + fond + face avant des planches).

| Essence | Type de pièce | Meuble | Fond (derrière les objets) | Planches |
|---|---|---|---|---|
| Chêne (oak) | Cuisine | bois peint crème | carrelage blanc-bleu | peinture écaillée |
| Sapin (spruce) | Atelier | bois brun sombre | panneau perforé (outils) | usé |
| Bouleau (birch) | Pharmacie | blanc laqué | carrelage blanc, croix verte | bande verte, étiquettes |
| Acajou (jungle) | Épicerie | métal peint rouge | tôle perforée grise | étiquettes de prix |
| Acacia | Quincaillerie | métal peint bleu | tôle perforée | étiquettes orange |
| Chêne noir (dark_oak) | Bureau | bois verni sombre | papier peint à rayures | porte-étiquettes en laiton |
| Palétuvier (mangrove) | Chambre | bois peint rouge-brun | papier peint à fleurs | peinture écaillée |
| Cerisier (cherry) | Café / boulangerie | bois peint rose délavé | damier noir et blanc | peinture écaillée |
| Bambou (bamboo) | Garage | rayonnage galvanisé | grillage | taches d'huile |
| Carmin (crimson) | Armurerie / gendarmerie | métal kaki militaire | tôle kaki | numéros au pochoir |
| Biscornu (warped) | Hôpital | acier bleu-vert | carrelage clair | étiquettes blanches |
| Chêne pâle (pale_oak) | Entrepôt | poutres orange | montants bleus, fond sombre | bandes jaune et noir |
| Peuplier (poplar) | Grange / cabane de chasse | bois gris brut | planches | corde |

Attention : le chêne pâle sert aussi à la barricade (planches de chêne pâle) ; l'étagère en chêne pâle n'est pas
concernée (texture `pale_oak_shelf` à part).

## Comptes dans les maps à importer (scans `maps_refuge/<id>/scan.pkl`, blocs de la zone scannée)

| Bloc | Bourgade | Hameau | Épicentre | Port | Hôpital |
|---|---|---|---|---|---|
| **Magnétite** (→ radio) | **1 858** | 0 | 0 | 35 | 0 |
| **Cible** (→ balise) | **183** | 0 | 0 | 1 | 0 |
| **Bloc de redstone** (→ générateur) | **167** | 0 | 0 | 2 | 0 |
| Chêne pâle (planches) | 0 | 0 | 0 | 0 | 0 |
| Étagères `*_shelf` | 0 | 0 | 0 | 0 | 0 |
| Tonneau (→ caisse) | 60 219 | 251 | 1 037 | 2 486 | 0 |
| Coffre | 1 559 | 850 | 6 643 | 153 | 0 |
| Pot décoré (→ jerrican) | 1 | 604 | 2 583 | 24 | 0 |
| Bibliothèque ciselée (→ classeur) | 0 | 0 | 1 076 | 288 | 0 |
| Vault (→ coffre-fort) | 0 | 334 | 289 | 0 | 0 |
| Sable / gravier suspects (→ gravats) | 12 / 0 | 0 / 882 | 53 / 16 | 0 | 0 |
| Lanterne / lanterne des âmes | 252 / 791 | 104 / 12 | 0 / 643 | 204 / 20 | 0 |
| Verre / vitre | 78 122 / 27 260 | 0 / 163 | 0 / 63 | 198 / 14 456 | 0 / 44 |

**À traiter avant l'import de la Bourgade (Mosslorn)** : ses 1 858 magnétites, 183 cibles et 167 blocs de redstone
(décor du builder d'origine) deviendraient des radios, balises et générateurs partout. Il faut les remplacer par
d'autres blocs (FAWE `//replace`) dans la copie du mode, ou choisir d'autres blocs hôtes pour ces objets de jeu.
Idem, en plus petit, pour le Port (35 magnétites). Les tonneaux (60 219 dans la Bourgade) deviennent tous des caisses
clouées : accepté.
