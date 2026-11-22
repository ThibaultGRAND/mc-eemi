# EemiCore 1.6.0 : à appliquer dans `plugins/EemiCore/config.yml` du serveur

Le config du serveur garde ses propres valeurs : les nouveaux prix et les nouveaux cosmétiques du jar ne
s'appliquent PAS tout seuls. Modifier le fichier existant ligne par ligne (ne pas le réécrire), puis
`/eemi reload` (ou le redémarrage qui installe la 1.6.0).

Toutes les nouvelles clés (sections 2 et 3) ont une valeur par défaut dans le code : les ajouter n'est utile
que pour les régler. Les nouveaux cosmétiques (section 4), eux, n'existent que s'ils sont dans le config.

## 1. Prix à changer (clé `price:` de chaque ligne de `cosmetics:`)

| Clé | Ancien | Nouveau |
|---|---|---|
| trail_flammes | 300 | 100 |
| trail_coeurs | 300 | 100 |
| trail_notes | 300 | 100 |
| trail_ames | 400 | 125 |
| trail_neige | 300 | 100 |
| trail_enchant | 400 | 125 |
| trail_fumee | 250 | 75 |
| trail_arcenciel | 800 | 275 |
| hat_citrouille | 200 | 75 |
| hat_creeper | 350 | 125 |
| hat_wither | 500 | 175 |
| hat_dragon | 1000 | 350 |
| hat_astronaute | 400 | 125 |
| hat_diamant | 750 | 250 |
| title_debutant | 50 | 20 |
| title_fantome | 300 | 100 |
| title_citrouille | 300 | 100 |
| title_champion | 600 | 200 |
| title_tryhard | 500 | 175 |
| title_batisseur | 400 | 125 |
| title_mystere | 700 | 250 |
| title_legende | 2000 | 650 |
| title_mouton | 300 | 100 |
| title_nuit | 350 | 125 |
| title_artiste | 400 | 125 |
| title_explorateur | 400 | 125 |
| title_sniper | 500 | 175 |
| title_stratege | 500 | 175 |
| title_roi | 3000 | 1000 |
| title_diamant | 5000 | 1700 |
| title_h_sorciere | 400 | 125 |
| title_h_vampire | 400 | 125 |
| title_h_zombie | 300 | 100 |
| title_h_2026 | 666 | 222 |
| title_n_lutin | 300 | 100 |
| title_n_renne | 400 | 125 |
| title_n_pere | 800 | 275 |
| title_n_2026 | 666 | 222 |
| join_fracas | 400 | 125 |
| join_fantome | 400 | 125 |
| join_star | 800 | 275 |
| gadget_feux | 2000 | 650 |
| gadget_peinture | 2500 | 850 |
| gadget_grappin | 3500 | 1200 |
| gadget_jetpack | 6000 | 2000 |

Les cosmétiques du casino (`casino: true`, prix 0) ne changent pas. Si le config du serveur a d'autres prix que
la colonne « Ancien » (modifiés à la main), diviser la valeur du serveur par 3 environ.

## 2. Nouvelles clés à AJOUTER au premier niveau (n'importe où, par exemple avant `levels:`)

```yaml
# 1.6.0 : bonus de la premiere partie et de la premiere victoire de la journee (heure de Paris), en pieces. 0 = coupe.
daily-bonus:
  first-play: 15
  first-win: 50

# 1.6.0 : recompense de la premiere connexion de la journee (heure de Paris). coins[n-1] = jour n de la serie
# de jours consecutifs ; apres le dernier palier on reste au max ; un jour manque remet la serie a 1.
daily-reward:
  enabled: true
  coins: [20, 25, 30, 35, 40, 45, 50]

# 1.6.0 : fuseau horaire de la journee (recompense du jour, bonus du jour).
timezone: "Europe/Paris"

# 1.6.0 : ligne cliquable a la connexion [Quetes x/3] [Jouer] [Boutique].
join-links:
  enabled: true

# 1.6.0 : boutique. Un achat au-dessus de ce prix passe par un ecran Oui / Annuler.
shop:
  confirm-above: 500

# 1.6.0 : sons d'interface (cle du pack de textures ; "" = aucun son).
sounds:
  achat: "eemicraft:ui.achat"
  quete: "eemicraft:ui.quete"
  niveau: "eemicraft:ui.niveau"
  recompense: "eemicraft:ui.recompense"
  erreur: "eemicraft:ui.erreur"
```

Sans le pack qui contient `eemicraft:ui.*`, ces sons sont muets : mettre par exemple
`achat: "minecraft:entity.experience_orb.pickup"` pour revenir à un son vanilla.

## 3. Clés à ajouter dans des sections existantes

Dans `lobby-ui:` (sous `enabled:`) :

```yaml
  # 1.6.0 : modeles eemicraft:gadget/<feux|peinture|grappin|jetpack> sur les gadgets (seulement si enabled est true).
  gadget-models: true
```

Section `lobby-guard:` au premier niveau (si elle n'existe pas déjà dans le config du serveur ; sinon la garder telle quelle) :

```yaml
# 1.6.0 : filet de securite du hub (#50/#53). Au lobby, retire de l'inventaire les objets marques (PersistentDataContainer)
# par ces plugins de jeu. Jamais les objets DeluxeHub, ni les chapeaux/gadgets d'EemiCore, ni les outils d'EemiAdmin.
# Chaque retrait est ecrit dans la console.
lobby-guard:
  enabled: true
  interval-ticks: 100
  namespaces: [eemiabysse, eemiballsofsteel, eemibingo, eemiblockparty, eemiboatrace, eemibowspleef, eemibridge,
    eemibuildbattle, eemihideseek, eemihorscours, eemihungergames, eemijumpleague, eemimurder, eemipitchout,
    eemipvprun, eemisheepwars, eemiskywars, eemitnttag, eemitntwars, eemitonte, eemitowers, eemiuhc, eemiwizards]
```

## 4. Nouveaux cosmétiques à AJOUTER dans `cosmetics:`

À coller dans la section `cosmetics:`, avec 2 espaces d'indentation, par exemple juste avant la ligne
`# Exclusivites du casino (casino: true)` :

```yaml
  # 1.6.0 : nouveaux cosmetiques (prix a l'echelle des gains : partie 5, victoire 25, bonus du jour 15/50).
  trail_etoiles: {type: trail, name: "&fTraînée d'étoiles", icon: END_ROD, price: 150, particle: END_ROD}
  trail_electrique: {type: trail, name: "&eTraînée électrique", icon: LIGHTNING_ROD, price: 200, particle: ELECTRIC_SPARK, description: ["Le Wi-Fi de l'EEMI en particules"]}
  hat_tnt: {type: hat, name: "&cTête explosive", icon: TNT, price: 200, material: TNT, description: ["Pour les pros des Jeux TNT"]}
  title_alternant: {type: title, name: "&bTitre Alternant", icon: BOOK, price: 100, text: "&bAlternant"}
  title_deadline: {type: title, name: "&cTitre Rendu à 23h59", icon: CLOCK, price: 150, text: "&cRendu à 23h59"}
  title_cafe: {type: title, name: "&6Titre Accro au café", icon: COCOA_BEANS, price: 125, text: "&6Accro au café"}
  title_moustache: {type: title, name: "&fTitre Moustachu", icon: PAPER, price: 175, text: "&fMoustachu"}
  title_404: {type: title, name: "&7Titre Erreur 404", icon: STRUCTURE_VOID, price: 150, text: "&7Erreur 404"}
  title_pont: {type: title, name: "&9Titre Poseur de ponts", icon: TERRACOTTA, price: 200, text: "&9Poseur de ponts"}
  join_alternance: {type: join, name: "&bRetour d'alternance", icon: WRITABLE_BOOK, price: 150, message: "&b%player% &7revient d'alternance. Courage à tous !"}
  join_cafe: {type: join, name: "&6Tournée de cafés", icon: COCOA_BEANS, price: 175, message: "&6%player% &7arrive avec des cafés pour tout le monde."}
```

Le fichier doit rester en UTF-8 (accents). Matériaux et particules vérifiés contre paper-api 26.3.

## 5. Après application

- `/eemi reload`, puis `/boutique` : prix divisés, 11 nouveautés, un achat > 500 pièces demande confirmation.
- Les titres Halloween / Noël sont aussi pilotés par EemiHalloween / EemiNoel (en mémoire) : seuls leurs prix changent ici.
