# Palettes de blocs (IDs 1.21+ / 26.x)

Règle : **principal 60 % / secondaire 30 % / accent 10 %**. Chaque « bloc » d'une palette est en fait une famille de variantes de même valeur, jamais un bloc unique à 100 % sur une grande surface.
Vérifier chaque ID contre l'enum `Material` de paper-api 26.3 avant de compiler. Pièges connus : `chain` → `iron_chain`, `grass` → `short_grass` ; les blocs de cuivre (`copper_bulb`, `copper_grate`, `waxed_*`) sont à revérifier.

| # | Palette | Principal (60 %) | Secondaire (30 %) | Accent (10 %) | Usure |
|---|---|---|---|---|---|
| 1 | Village médiéval | `oak_planks`, `stripped_oak_log` (poteaux), `white_terracotta` / `calcite` (torchis) | `cobblestone`, `stone_bricks` (socle), `dark_oak_stairs` / `spruce_stairs` (toit) | `lantern`, `oak_trapdoor`, `flower_pot`, `iron_bars` | `mossy_cobblestone`, `cracked_stone_bricks` |
| 2 | Château gris | `stone_bricks`, `cracked_stone_bricks`, `andesite`, `polished_andesite` | `cobblestone`, `stone_brick_stairs`, `deepslate_tile_stairs` | `iron_bars`, `iron_chain`, `soul_lantern`, bannières | socle `cobbled_deepslate` |
| 3 | Cathédrale gothique | `deepslate_bricks`, `deepslate_tiles`, `polished_deepslate` | `stone_bricks`, `tuff_bricks`, `polished_tuff` | `black_stained_glass_pane`, vitraux colorés, `candle`, `soul_lantern` | `cracked_deepslate_bricks` |
| 4 | Nordique / montagne | `spruce_planks`, `spruce_log`, `stripped_spruce_log` | `cobblestone`, `mossy_cobblestone`, `stone` ; toit `spruce_stairs` + `dark_oak_stairs` ou `moss_block` | `campfire`, `lantern`, `white_wool`, `barrel` | `mossy_cobblestone` |
| 5 | Temple japonais | `dark_oak_planks`, `stripped_dark_oak_log`, `white_concrete` / `calcite` (shoji) | `deepslate_tile_stairs`, `deepslate_tile_slab` (toit), `smooth_stone` | `red_terracotta` / `crimson_planks` (torii), `lantern`, `bamboo_mosaic`, `cherry_leaves` | — |
| 6 | Jardin des cerisiers | `cherry_planks`, `stripped_cherry_log`, `pink_terracotta` | `quartz_block`, `smooth_quartz`, `mud_bricks` | `cherry_leaves`, `pink_petals`, `lantern`, `pink_stained_glass` | — |
| 7 | Désert / méditerranée | `smooth_sandstone`, `sandstone`, `cut_sandstone` | `terracotta`, `orange_terracotta`, `packed_mud`, `mud_bricks` | `cyan_terracotta`, `blue_stained_glass`, `light_blue_glazed_terracotta`, eau | `sand` au pied des murs |
| 8 | Moderne blanc | `white_concrete`, `smooth_quartz`, `quartz_bricks` | `light_gray_concrete`, `gray_concrete`, `glass`, `tinted_glass` | `black_concrete`, `sea_lantern`, `iron_trapdoor`, `birch_planks` | aucune (le moderne reste propre) |
| 9 | Steampunk / industriel | `bricks`, `mud_bricks`, `polished_blackstone_bricks` | `cut_copper` → `exposed_` → `weathered_` → `oxidized_cut_copper` (préfixe `waxed_` pour figer), `copper_grate` | `copper_bulb`, `iron_chain`, `lightning_rod`, `hopper`, `cauldron` | cuivre plus oxydé en bas |
| 10 | Forteresse du Nether | `nether_bricks`, `blackstone`, `polished_blackstone_bricks` | `red_nether_bricks`, `basalt`, `polished_basalt` | `magma_block`, `shroomlight`, `soul_lantern`, `crimson_planks`, `gilded_blackstone` | `cracked_nether_bricks` |
| 11 | Fantasy elfique | `calcite`, `quartz_block`, `smooth_quartz` | `prismarine_bricks`, `warped_planks`, `dark_prismarine` | `amethyst_block`, `end_rod`, `sea_lantern`, `purple_stained_glass` | — |
| 12 | Pirate / port | `spruce_planks`, `dark_oak_planks`, `stripped_spruce_log` | `cobblestone`, `stone_bricks`, `mud_bricks` (quai) | `white_wool` (voiles), `iron_chain`, `lantern`, `barrel`, `red_terracotta` | `mossy_cobblestone` à la ligne d'eau |
| 13 | Ruines envahies | `mossy_stone_bricks`, `cracked_stone_bricks`, `stone_bricks` | `mossy_cobblestone`, `cobblestone`, `gravel`, `coarse_dirt` | `vine`, `moss_carpet`, `oak_leaves[persistent=true]`, `cobweb`, `glow_lichen` | — |
| 14 | Ville post-apo | `gray_concrete`, `light_gray_concrete`, `andesite`, `polished_andesite` | `cracked_deepslate_bricks`, `iron_bars`, `cobbled_deepslate`, `gravel` | `weathered_cut_copper` (rouille), `soul_lantern`, `moss_block`, `dead_bush` | — |

## Terrain tempéré (pourcentages)

- Sol : `grass_block` 60 %, `dirt` 15 %, `podzol` 8 %, `coarse_dirt` 7 %, `moss_block` 5 %, `rooted_dirt` 3 %, `mud` 2 %.
- Roche de pente : `stone` 50 %, `andesite` 15 %, `diorite` 10 %, `granite` 10 %, `cobblestone` 8 %, `gravel` 5 %.
- Plage : `sand` 70 %, `gravel` 15 %, le reste `clay` / `coarse_dirt`.
- Chemin de terre : 40 % `coarse_dirt`, 30 % `dirt_path` / `dirt`, 20 % `podzol`, 10 % `gravel`.
- Au sol : `short_grass`, `fern`, `dead_bush` ; couches de neige `snow`.

## Dégradés prêts à l'emploi (clair → sombre)

- Pierre : `calcite` › `diorite` › `andesite` › `stone` › `cobblestone` › `tuff` › `cobbled_deepslate` › `deepslate`.
- Bois : `pale_oak` › `birch` › `oak` › `jungle` › `spruce` › `dark_oak`.
- Sable / terre : `sandstone` › `smooth_sandstone` › `terracotta` › `packed_mud` › `mud_bricks`.
- Briques de pierre : `stone_bricks` › `cracked_stone_bricks` › `mossy_stone_bricks` (usure, pas valeur).
- Deepslate : `polished_deepslate` › `deepslate_bricks` › `deepslate_tiles` › `cracked_deepslate_tiles`.

## Accords de bois (deux essences par bâtiment)

bouleau + chêne, chêne + jungle, jungle + épicéa, épicéa + chêne noir, cerisier + chêne noir. Un seul type de pierre par bâtiment.
