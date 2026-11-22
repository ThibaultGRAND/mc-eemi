# Pack EEMICRAFT : icônes, sons, boutons, gadgets

Généré par `build_pack.py` (dessins dans `art_ui.py` et `art_abysse.py`). Aperçus : `icones_preview.png`,
`ui_boutons_preview.png`, `gadgets_preview.png`, `abysse_planche.png` (+ `abysse_planche.txt` pour l'ordre).

## Police d'icônes `eemicraft:icones`

Bitmap 8×8 (`assets/eemicraft/font/icones.json`, texture `eemicraft:font/icones.png`, ascent 7).
À utiliser dans un composant texte avec `"font": "eemicraft:icones"` (Adventure : `.font(Key.key("eemicraft", "icones"))`).
Zone U+E400..U+E409 : la base occupe déjà U+E001-E002 et U+E101-E10E (police casino) et U+E300-E301,
U+E310-E31E (police lobby, et surcharges de `minecraft:default` / `minecraft:uniform`). `build_pack.py` refuse
de construire si un caractère d'icône est déjà pris par une police de la base.

| Caractère | Code | Icône |
|---|---|---|
| `` | U+E400 | pièce (or) |
| `` | U+E401 | éclat (monnaie de l'Abysse, cristal bleu) |
| `` | U+E402 | cadenas |
| `` | U+E403 | flèche retour |
| `` | U+E404 | coche verte |
| `` | U+E405 | croix rouge |
| `` | U+E406 | étoile |
| `` | U+E407 | cœur |
| `` | U+E408 | épée |
| `` | U+E409 | trophée |

## Sons `eemicraft:ui.*`

`assets/eemicraft/sounds.json`. Aucun fichier .ogg : chaque variante rejoue un événement vanilla
(`"type": "event"`) avec sa hauteur et son volume ; quand il y a deux variantes, le client en tire une au hasard.
Jouer : `player.playSound(player, "eemicraft:ui.achat", SoundCategory.MASTER, 1f, 1f)` ou `/playsound eemicraft:ui.achat master @s`.

| Événement | Variantes (événement vanilla, pitch, volume) |
|---|---|
| `eemicraft:ui.achat` | entity.experience_orb.pickup 1.4 / 0.8 ; block.note_block.pling 1.6 / 0.6 |
| `eemicraft:ui.quete` | ui.toast.challenge_complete 1.2 / 0.5 |
| `eemicraft:ui.niveau` | entity.player.levelup 1.0 / 0.8 ; entity.player.levelup 1.12 / 0.8 |
| `eemicraft:ui.clic` | ui.button.click 1.3 / 0.35 ; ui.button.click 1.45 / 0.35 |
| `eemicraft:ui.erreur` | block.note_block.bass 0.6 / 0.8 ; entity.villager.no 1.0 / 0.6 |
| `eemicraft:ui.recompense` | block.amethyst_block.chime 1.0 / 1.0 ; block.note_block.chime 1.5 / 0.7 |

## Icônes et fonds des menus du lobby (art_lobby.py, 11/11/2026)

Les 67 icônes `eemicraft:lobby/<nom>` et les 15 fonds `bg_*` de la base sont redessinés par `art_lobby.py` et
écrasent ceux de la base aux mêmes chemins (mêmes noms, mêmes glyphes U+E310..U+E31E). Icônes (style refait le 11/11/2026,
référence `references/style_icones.png`) : pictogramme seul sur fond transparent, épais contour presque noir,
relief simple ; interface en couleurs (refait le 11/11/2026 : flèches bleu clair, personnages bleu/vert/orange,
porte brune + flèche rouge, podium or/argent/bronze, haut-de-forme noir à ruban rouge, etc.), états en vert/rouge/or, jeux avec l'accent de leur catégorie (Equipes bleu,
Combat rouge, TNT orange, Ambiance violet, Survie vert, Iles cyan, Casino or), dessin 32x32 agrandi x2 (64x64).
Fonds : panneau clair, bandeau de titre coloré, cases utilisées cerclées (intérieur presque transparent).
Ajoutés : `lobby/precedent`, `lobby/confirmer`, `lobby/annuler` (remplacent ceux de art_ui), `lobby/etat_equipe`,
`etat_achete`, `etat_prix`, `etat_cher` (case d'état sous chaque cosmétique de la boutique, EemiCore 1.6.1) et le
fond `bg_confirm` (U+E31F, écran de confirmation d'achat). Aperçus : `apercus_lobby/` (planche `icones.png`,
`menu_<fond>.png` rendus sur un coffre vanilla).

## Gadgets EemiCore

`eemicraft:gadget/feux` (lanceur de feux d'artifice), `eemicraft:gadget/peinture` (pistolet à peinture),
`eemicraft:gadget/grappin`, `eemicraft:gadget/jetpack` : objets plats 16×16.
