# EemiCore 1.9.0 : à appliquer dans le config du SERVEUR

Fichier : `/plugins/EemiCore/config.yml` du serveur. Modifier ligne par ligne (jamais réécrire le fichier),
backup dans `/.ai-backups/` avant, puis `eemi reload`. À faire APRÈS l'installation du jar 1.9.0
(la clé `hide-off-season` n'est lue qu'à partir de la 1.9.0).

Les prix du serveur ne sont PAS ceux du jar (le serveur garde les anciens prix, environ ×3) : ne changer
que les champs indiqués (`name`, `text`, `message`, `description`, dates), jamais `price`.

## 1. Textes réécrits (#102)

Format : `cosmetics.<id>.<champ>` : ancien → nouveau

- `cosmetics.trail_flammes.name` : `&6Trainee de flammes` → `&6Traînée de flammes`
- `cosmetics.trail_coeurs.name` : `&cTrainee de coeurs` → `&cTraînée de cœurs`
- `cosmetics.trail_notes.name` : `&aTrainee de notes` → `&aTraînée de notes`
- `cosmetics.trail_ames.name` : `&3Trainee d'ames` → `&3Traînée d'âmes`
- `cosmetics.trail_neige.name` : `&fTrainee de neige` → `&fTraînée de neige`
- `cosmetics.trail_enchant.name` : `&dTrainee magique` → `&dTraînée magique`
- `cosmetics.trail_fumee.name` : `&8Trainee de fumee` → `&8Traînée de fumée`
- `cosmetics.hat_creeper.name` : `&aTete de Creeper` → `&aTête de Creeper`
- `cosmetics.hat_wither.name` : `&8Crane de Wither` → `&8Crâne de Wither`
- `cosmetics.hat_dragon.name` : `&5Tete de dragon` → `&5Tête de dragon`
- `cosmetics.title_debutant.name` : `&7Titre Debutant` → `&7Titre Débutant`
- `cosmetics.title_debutant.text` : `&7Debutant` → `&7Débutant`
- `cosmetics.title_fantome.name` : `&fTitre Fantome` → `&fTitre Fantôme`
- `cosmetics.title_fantome.text` : `&fFantome` → `&fFantôme`
- `cosmetics.title_batisseur.name` : `&aTitre Batisseur` → `&aTitre Bâtisseur`
- `cosmetics.title_batisseur.text` : `&aBatisseur` → `&aBâtisseur`
- `cosmetics.title_mystere.name` : `&5Titre Mystere` → `&5Titre Mystère`
- `cosmetics.title_legende.name` : `&6&lTitre Legende` → `&6&lTitre Légende`
- `cosmetics.title_legende.text` : `&6&lLegende` → `&6&lLégende`
- `cosmetics.title_stratege.name` : `&9Titre Stratege` → `&9Titre Stratège`
- `cosmetics.title_stratege.text` : `&9Stratege` → `&9Stratège`
- `cosmetics.title_h_sorciere.name` : `&5&lTitre Sorciere` → `&5&lTitre Sorcière`
- `cosmetics.title_h_sorciere.text` : `&5&lSorciere` → `&5&lSorcière`
- `cosmetics.title_n_pere.name` : `&c&lTitre Pere Noel` → `&c&lTitre Père Noël`
- `cosmetics.title_n_pere.text` : `&c&lPere Noel` → `&c&lPère Noël`
- `cosmetics.title_n_2026.name` : `&f&lTitre Noel 2026` → `&f&lTitre Noël 2026`
- `cosmetics.title_n_2026.text` : `&f&lNoel 2026` → `&f&lNoël 2026`
- `cosmetics.join_fracas.name` : `&eEntree fracassante` → `&eEntrée fracassante`
- `cosmetics.join_fracas.message` : `&e%player% &7fait une entree fracassante !` → `&e%player% &7fait une entrée fracassante !`
- `cosmetics.join_fantome.name` : `&fApparition fantome` → `&fApparition fantôme`
- `cosmetics.join_star.name` : `&6Arrivee de star` → `&6Arrivée de star`
- `cosmetics.join_star.message` : `&6&l%player% &eest arrive ! Place a la star !` → `&6&l%player% &eest arrivé ! Place à la star !`
- `cosmetics.hat_c_or.name` : `&6Lingot geant` → `&6Lingot géant`
- `cosmetics.join_c_jackpot.name` : `&6Entree Jackpot` → `&6Entrée Jackpot`
- `cosmetics.gadget_peinture.name` : `&dPistolet a peinture` → `&dPistolet à peinture`
- `cosmetics.gadget_peinture.description` : `["Repeint les blocs touches (3 s)"]` → `["Repeint les blocs touchés (3 s)"]`
- `cosmetics.gadget_grappin.description` : `["Lance l'hamecon sur un bloc,", "relance pour t'y propulser"]` → `["Lance l'hameçon sur un bloc,", "relance pour t'y propulser"]`
- `cosmetics.gadget_jetpack.description` : `["3 secondes de vol propulse", "dans la direction du regard"]` → `["3 secondes de vol propulsé", "dans la direction du regard"]`

Les autres textes de la boutique (catégories, états, messages d'achat, confirmation) sont dans le code :
rien à faire dans le config.

## 2. Halloween masqué (#88)

Ajouter à la fin de l'accolade de chacune de ces 7 lignes (garder le reste de la ligne tel quel) :

- `hat_citrouille` : ajouter `, available-until: "2026-11-03", hide-off-season: true`
- `title_citrouille` : ajouter `, available-until: "2026-11-03", hide-off-season: true`
- `join_fantome` : ajouter `, available-until: "2026-11-03", hide-off-season: true`
- `title_h_sorciere`, `title_h_vampire`, `title_h_zombie`, `title_h_2026` : ils ont déjà
  `available-until: "2026-11-03"` → ajouter seulement `, hide-off-season: true`

Exemple : `hat_citrouille: {type: hat, name: "&6Citrouille d'Halloween", icon: JACK_O_LANTERN, price: 200, material: JACK_O_LANTERN, available-until: "2026-11-03", hide-off-season: true}`

Effet : plus vendus, plus visibles dans la boutique, plus portés (chapeau retiré de la tête au hub dans les 2 s,
titre retiré du chat / du tab / du dessus de la tête, message d'arrivée coupé) même s'ils sont équipés.
L'achat et l'équipement restent dans les fichiers joueurs. Pour Halloween 2027 : passer leurs dates à
`available-from: "2027-10-15"`, `available-until: "2027-11-03"` (ou `/halloween on`, qui gère déjà les `title_h_*`).

Non touchés (à décider) : `title_fantome` (« Fantôme ») et `trail_ames` (« Traînée d'âmes ») : thème proche
d'Halloween mais vendus toute l'année jusqu'ici.

## 3. Nouveaux cosmétiques de Noël (#88)

Coller ce bloc dans la section `cosmetics:`, juste après la ligne `title_n_2026` (2 espaces d'indentation).
Prix à l'échelle des prix actuels du SERVEUR (titres de Noël 300 à 800) ; le jar a les mêmes avec ses prix ÷3.

```yaml
  # 1.9.0 : Noel (chapeaux, traines, messages), du 01/12/2026 au 02/01/2027, masques hors saison.
  hat_n_sapin: {type: hat, name: "&2Sapin de Noël", icon: SPRUCE_LEAVES, price: 450, material: SPRUCE_LEAVES, description: ["Un sapin sur la tête"], available-from: "2026-12-01", available-until: "2027-01-02", hide-off-season: true}
  hat_n_bonhomme: {type: hat, name: "&fTête de bonhomme de neige", icon: SNOW_BLOCK, price: 500, material: SNOW_BLOCK, description: ["Attention, ça fond au soleil"], available-from: "2026-12-01", available-until: "2027-01-02", hide-off-season: true}
  trail_n_flocons: {type: trail, name: "&fTraînée de flocons", icon: SNOWBALL, price: 450, particle: ITEM_SNOWBALL, description: ["Des boules de neige à chaque pas"], available-from: "2026-12-01", available-until: "2027-01-02", hide-off-season: true}
  trail_n_clochettes: {type: trail, name: "&6Traînée de clochettes", icon: BELL, price: 450, particle: NOTE, description: ["Des notes qui tintent derrière toi"], available-from: "2026-12-01", available-until: "2027-01-02", hide-off-season: true}
  join_n_traineau: {type: join, name: "&cArrivée en traîneau", icon: SPRUCE_SAPLING, price: 500, message: "&c%player% &7arrive en traîneau. Ho ho ho !", available-from: "2026-12-01", available-until: "2027-01-02", hide-off-season: true}
  join_n_hotte: {type: join, name: "&aHotte pleine", icon: CHEST, price: 500, message: "&a%player% &7débarque avec une hotte pleine de cadeaux !", available-from: "2026-12-01", available-until: "2027-01-02", hide-off-season: true}
```

Dates : les titres `title_n_*` existants ont déjà `available-from: "2026-12-01"`, `available-until: "2027-01-02"`
(pas de changement). Les titres de Noël restent sans `hide-off-season` (gardés après la saison en « Édition limitée »,
comme avant), sauf si on veut les masquer aussi en janvier : ajouter `, hide-off-season: true`.

ATTENTION EemiNoel : `/noel on` est actif et met en vente tout de suite les cosmétiques de sa liste
`boutique.cosmetiques` (par défaut les `title_n_*` seulement). Les 6 nouveaux n'y sont pas : ils n'arriveront que
le 01/12. Pour les vendre dès maintenant, les ajouter à `boutique.cosmetiques` dans `/plugins/EemiNoel/config.yml`
puis `noel reload` (comportement de Saison.java lu dans le code, pas testé avec ces ids).
