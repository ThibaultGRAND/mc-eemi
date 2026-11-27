# CLAUDE.md – Serveur communautaire Minecraft EEMICRAFT

Fichier commun à Thibault (admin, Enosik en jeu) et Esteban (BanTeEs en jeu), fusionné et nettoyé le 22/11/2026. Il décrit l'ÉTAT ACTUEL ; l'historique détaillé (versions successives, heures de redémarrage, backups) est archivé tel quel dans `docs/historique/CLAUDE-thibault-20261122.md` et `docs/historique/CLAUDE-esteban-20261122.md` : y chercher quand un détail manque ici.

Le Fallen Kingdom est abandonné. Tout ce qui le concernait (FKCam, ImprovedFactions, assauts, claims, setup stream FK) est hors sujet.

## Contexte

- Serveur communautaire de mini-jeux « EEMICRAFT » (école EEMI), MineStrator (MyBox, 8 Go), Paper 26.3 (Java 25), adresse eemicraft.mine.fun, Discord discord.gg/UuDGVB7Q38. Hébergement web : https://eemicraft.strator.gg (pack de textures, skins, fichiers temporaires). Reset le 25/10/2026.
- Un seul serveur (lobby + mondes de mini-jeux), pas de réseau Velocity.
- Thibault décide ; ne rien supprimer sur le serveur sans son accord. Ops : Enosik, BanTeEs (Qorwinn à déop ; droits par LuckPerms et /droits).

## Chemins

- Chemins locaux relatifs à la racine du dépôt (ex. `EemiAbysse/CONCEPTION.md`, `a_deposer/`), quel que soit le PC. Chemins commençant par `/` (`/plugins`, `/lobby/...`, `/.ai-backups`) = serveur MineStrator.
- « Téléchargements » = dossier personnel de Thibault ou d'Esteban, hors dépôt. « scratchpad » = dossier temporaire d'une ancienne session Claude, hors dépôt, souvent effacé : un script qui doit resservir va dans le dossier du plugin (ou `_commun/`) et est commité.
- `%APPDATA%\.minecraft\versions\26.3\26.3.jar` = jar du client 26.3, présent sur tout PC où le jeu est installé.
- Jamais de chemin propre à un PC (`C:\Users\...`) dans ce fichier.

## Plugins tiers

| Rôle | Plugins |
|---|---|
| Lobby / hub | DeluxeHub 3.8.6, DeluxeMenus 1.14.1, FancyNpcs 2.12.1, DecentHolograms 2.10.2, TAB |
| Mondes | Multiverse-Core 5.8.1, Multiverse-Inventories 5.3.6, WorldGuard 7.0.20, FAWE 2.16.1 |
| Base | LuckPerms 5.5.88, PlaceholderAPI 2.12.3, Apollo (Lunar) 1.2.9 |
| PvP | OldCombatMechanics 2.7.0 |
| BedWars | Screaming BedWars 0.2.44 + SBA 1.5.21 |
| Loup-Garou | LoupGarou 1.0.5 (5 rôles) + LGPlus 1.1.0 (maison) |
| TNT Run | TNTRun 2.8.9 (Despical) |
| Build Battle | Plugily BuildBattle (+ EemiBuildBattle) |
| SkyBlock | BentoBox 3.23.4 (BSkyBlock, AOneBlock), AuraSkills 2.4.0, AxMinions 1.0.21, AuctionHouse 1.5.6, S-Economy |

Les plugins maison `Eemi*` ont chacun leur source dans un dossier du même nom à la racine (Maven, paper-api 26.3) ; leur version installée est dans leur section plus bas.

## Règles (à respecter avant de toucher à quoi que ce soit)

### 1. Git : travail à deux (OBLIGATOIRE)

Le dépôt est partagé sur GitHub ; chacun a son Claude. Le serveur MineStrator n'est pas dans git : il est commun aux deux.

- **Avant de travailler** : au début de chaque session, puis AVANT de toucher un plugin, un générateur, un pack ou une config qui existe déjà : `git pull --rebase`, même pour une petite retouche. Modifs locales non commitées → les commiter d'abord. Après le pull, relire la section du plugin ici : l'autre a pu la changer.
- **Version** : après le pull, comparer la version du `pom.xml` ET celle installée dans `/plugins`. Toujours repartir de la plus haute, +1. Ne jamais réutiliser un numéro.
- **Après chaque modification** (jar compilé, plugin, générateur, pack, ce fichier) : commit puis `git push` tout de suite, sans demander (autorisé par Thibault). Message en français : `EemiAbysse 2.5.1 : monstres S4 moins forts (#57)`. Une modif = un commit ; le jar de `a_deposer/` va dans le même commit que sa source. Push refusé → `git pull --rebase` puis `git push`. Jamais de `--force`.
- **Conflits** : `.md` en fusion automatique (`merge=union` dans `.gitattributes`) ; après chaque pull, relire les sections touchées, fondre deux versions d'une même ligne en une seule ligne datée, supprimer les doublons exacts, ne jamais effacer une info de l'autre. Code : garder les deux changements si possible ; si les deux ont changé la même logique dans des sens contraires, s'arrêter et demander. Version en conflit : la plus haute +1. Jar en conflit : recompiler depuis la source fusionnée. Jamais de `git reset --hard`, `git checkout -- .`, `git clean` ni suppression de branche sans accord.
- **Ne jamais commiter** : mots de passe, jetons, clés d'API, `server.properties`, zips de maps (>100 Mo), dossiers `target/`.
- `maps/` est ignoré par `.gitignore`, SAUF `**/src/main/resources/maps/` (maps embarquées dans les jars : Sheep Wars `.swm`, Paintball `.pbm`) : sans elles, un jar compilé depuis le dépôt n'a plus de map (piège trouvé le 26/11).

### 2. Configs et fichiers du serveur

- Jamais de réécriture complète d'une config (une config DeluxeHub réécrite en entier l'a fait planter) : modifier ligne par ligne (edit_file, backup dans `/.ai-backups/`).
- DeluxeHub : noms d'objets sans accents ; `invert: true` ne marche pas → liste des mondes désactivés (`invert: false`) ; vérifier qu'il n'y a pas de doublon de jar.
- FancyNpcs : `fancynpcs save` AVANT d'éditer npcs.yml, puis `fancynpcs reload` ; sinon commandes `npc …` (à la console : `npc copy` réservé aux joueurs, `npc create <nom> --type PLAYER --location x y z --world <monde>` marche).
- Commandes vanilla à la console : le monde visé par défaut n'est pas fiable (`tntwars` le 28/10, l'overworld le 17/11) → toujours préfixer `execute in minecraft:<monde> run …` (`minecraft:overworld` pour le lobby) (`minecraft:lobby` n'existe pas). Après `forceload add`, toujours `forceload remove all`. Les entités d'un chunk non chargé ne sont pas vues par les sélecteurs. Les commandes qui modifient le monde (setworldspawn, gamerule) peuvent être refusées à l'outil : les faire taper par Thibault ou passer par un plugin.
- Multiverse-Inventories : `default-ungrouped-worlds` doit rester à **false** (à true, tout monde hors groupe partage l'inventaire du lobby → objets des jeux au hub ; corrigé le 10/11, vérifié le 17/11). Groupes modifiés dans `groups.yml` + `/mvinv reload` (`/mvinv group` est un assistant interactif). Un monde créé après le démarrage n'est connu du groupe qu'après un `mvinv reload`.
- L'API MineStrator (read_file) renvoie parfois une copie vieille de quelques secondes : relire avant de conclure.

### 3. Déposer et installer un plugin

- Monter la version à chaque compilation (plugin.yml en `${project.version}`, jamais en dur).
- Avant de livrer : valider `plugin.yml` et `config.yml` du jar avec SnakeYAML (un « : » dans un texte non cité casse le plugin.yml et le plugin n'est pas chargé ; EemiMurder 1.7.0, 16/11).
- Jars compilés dans `a_deposer/` (le seul dossier où Thibault prend tout pour le panel) ; une fois installés et vérifiés, les DÉPLACER dans `a_deposer_installes/` (ne plus les supprimer). Zips de maps : `a_deposer/maps/` avec leur destination indiquée.
- Les jars déposés sur le panel atterrissent parfois à la RACINE du serveur : vérifier qu'ils sont dans `/plugins`. Les suppressions sur le panel laissent parfois l'ancien jar : vérifier avec `search_files` avant de redémarrer.
- Renommer l'ancienne version en `.jar.old` AVANT de redémarrer, dans un appel séparé (renommer et redémarrer en même temps a bloqué le serveur en « stopping »). Un seul `.old` par plugin : supprimer les plus anciens.
- Au démarrage, chercher « Ambiguous plugin name » (deux jars d'un même plugin) et les exceptions du plugin dans la console.
- Joueurs connectés : annonce en `/title` 10 s avant, pas en `say` : `title @a times 10 60 10`, `title @a subtitle {...raison...}`, puis `title @a title {"text":"Redemarrage dans 10 s","color":"red","bold":true}`. Jamais de commande de test `say` (elle s'affiche dans le chat des joueurs).
- Un jar qui utilise de nouvelles textures s'installe en même temps que son pack (voir la section Pack).

### 4. Ajouter un nouveau jeu (liste complète)

1. Monde créé par le plugin (WorldCreator) ou importé : dossier du monde à la racine puis `mv import <nom> normal` (Paper le migre dans `lobby/dimensions/minecraft/<nom>`), gamemode, WorldGuard `__global__`. Parties en parallèle : copies `<monde>_2`, `_3` et `<monde>_modele` (`_commun/WorldClone.java`, qui ne doit pas copier `data/paper/metadata.dat`).
2. EemiCore `games.<id>` : name, icon, join, spectate, worlds (base + _2 + _3), min, max ; commandes de join dans `join-aliases` ; `eemi reload`.
3. Le jeu parle à EemiCore par la console : `eemi status <jeu> <waiting|starting|playing|ending> <attente> <en_jeu> <max>` et `eemi reward <pseudo> <jeu> <play|win|kill>`.
4. EemiDroits lit `games` d'EemiCore → `droits reload`. Si la sortie n'est pas « `<x> join` → `<x> leave` », la déclarer dans `sortie.<jeu>` du config d'EemiDroits.
5. Déclarer le monde partout (base, _2, _3, et _modele quand c'est indiqué) : DeluxeHub mondes désactivés (+ _modele) + `deluxehub reload` ; TAB `scoreboard-teams.disable-condition` + `tab reload` ; AuraSkills `disabled_worlds` + `skills reload` (obligatoire, sinon XP et mana du skyblock dans le jeu) ; OldCombatMechanics modeset old si PvP 1.8 (au redémarrage) ; EemiBlocage `worlds` si blocage à l'épée (au redémarrage) ; EemiAdmin `blocked-commands` ; Multiverse-Inventories : groupe si le jeu a plusieurs mondes qui partagent l'inventaire.
6. Entrée : boussole / sous-menu DeluxeHub (ou PNJ) en `eemi join <id>` (emmène le groupe, respecte maintenance et bêta).
7. Noter ici : version, installé ou non, testé ou non.

### 5. Bêta, maintenance, droits (EemiDroits, /droits)

- `/droits jeu <jeu> beta` → seuls bêta-testeurs et admins entrent ; `ouvert` pour rouvrir. Un nouveau mode en test → bêta plutôt qu'une permission bricolée.
- `/droits jeu <jeu> maintenance <raison>` (passe par `eemi maintenance`, réservé aux admins, les parties en cours continuent).
- Tag Bêta : `/droits beta <pseudo> on` (LuckPerms `eemidroits.beta`) ; les ops passent grâce à `eemidroits.bypass`.
- Interdire un monde ou un jeu à un joueur : sa fiche dans /droits (un monde de base couvre `_2`, `_3`, `_modele` ; le lobby n'est jamais interdit). Droits = permissions LuckPerms globales.

### 6. Pack de textures (résumé ; détails dans la section Pack)

- Un seul pack pour tout le serveur. Toujours repartir du DERNIER pack publié (vérifier le SHA-1 de `server.properties` et retélécharger le pack en ligne avant toute modif) : `EemicraftPack/build_pack.py` ne contient PAS tout, le relancer tel quel efface des ajouts.
- Ne jamais retexturer un bloc ou un objet vanilla dans le pack global : modèle dans notre namespace (`item_model eemicraft:…` / item_display), ou pack séparé chargé seulement dans les mondes du mode (modèle : RefugePack, `addResourcePack` / `removeResourcePack(UUID)`).
- Textures des modèles d'objets dans `textures/item/` (ou `block/`), jamais `textures/entity/` (violet et noir).
- Vérifier avec `EemiRefuge/pack/verifier_pack.py <zip>` (0 erreur, aucune collision).
- Publication : déposer le zip → `publish_server_file_to_webhosting` vers `pack/EemicraftPack.zip` → vérifier le SHA-1 → `resource-pack-sha1` dans server.properties → redémarrer.

### 7. Inventaires (bug du stuff SkyBlock perdu)

- Un plugin ne vide l'inventaire que dans son monde ou au hub (`mayTouch`).
- Quitter le jeu à cause d'un changement de monde = ne rien toucher (le joueur est déjà arrivé ailleurs).
- Téléporter AVANT de vider, jamais l'inverse ; revérifier le monde dans les tâches différées.
- Marquer les objets de jeu avec une clé PDC du plugin (LobbyGuard les retire au lobby).
- Changer de monde pendant une partie (/lobby) = quitter la partie.

### 8. Pièges Paper 26.3

- `max_health` plafonné à 1 024.
- `Material.CHAIN` → `IRON_CHAIN` ; `Material.values()` réécrit par Paper (pas de LEGACY_) → `getEnumConstants()`. `Material.isAir()/isSolid()` et les constantes de `Biome` passent par le registre (absent hors serveur, à éviter dans un initialiseur statique).
- WorldGuard bloque les apparitions de mobs des plugins (`mobs.block-plugin-spawning`) → rétablir les siennes en `CreatureSpawnEvent` HIGHEST.
- Refuser le bloc cliqué (`useInteractedBlock DENY`) empêche aussi de poser l'objet tenu : mettre `useItemInHand ALLOW`.
- `setInvulnerable` ne protège pas d'un joueur en créatif : annuler `EntityDamageEvent`.
- `getSpawnLocation()` du lobby sert de retour au hub pour tous les plugins.
- Un joueur qui porte une entité en passager ne peut plus être téléporté.
- Gamerules par monde : un monde importé garde les siennes (ex. `spawn_monsters`).

### 9. Textes affichés aux joueurs

- Accents dans les textes joueurs (pas dans la console).
- Préfixe : `§8[` + couleur de la catégorie + nom + `§8] §7` (Équipes §9, Combat §c, TNT §6, Ambiance §d, Survie §a).
- Titre ≤ 16 caractères, sous-titre ≤ 38, le reste dans le chat.
- Mention « NOUVEAU » dans un menu : 5 jours maximum, avec un commentaire YAML daté juste au-dessus (`# NOUVEAU jusqu'au JJ/MM`), retirée à la date dite.

### 10. Maps générées

- Escaliers : 3 cases libres au-dessus de chaque marche suivie d'une marche plus haute, et au-dessus de la case d'approche devant la 1re marche. Vitres toujours DANS le plan du mur. Contrôles automatiques : `EemiMurder/outil-test/controles.ps1` et `_commun/build/outils/.../Audit.java` (catégories ESCALIER « tete cognee » et VITRE).
- Coller un schematic laisse clôtures, vitres, barreaux et murets dans leur état isolé : `fill <boîte> <bloc> replace <bloc>` recalcule les raccords (les blocs `waterlogged` repassent à false).

### 11. Méthode

- Toute affirmation sur un plugin est vérifiée (doc, bytecode, source, test) avant d'être notée ici ; sinon écrire « non vérifié ».
- Noter ici ce qui est fait, installé, et pas encore testé en jeu. Une nouvelle info qui en remplace une ancienne la REMPLACE (pas d'ajout chronologique) : ce fichier décrit l'état actuel.

# Plugins centraux

## EemiCore (plugin central : registre des jeux, groupes, progression, boutique)

**État**
- Installé : EemiCore 1.9.7 (26/11, redémarrage 15:20 sans erreur, `a_deposer_installes/`, avec le pack db80f8a6 et EemiDroits 1.1.1 : les trois vont ensemble, sinon menus sans textes) : (1) case Saison de la boutique en « achetés / total » de saison, cadre doré si un cosmétique de saison est porté ou en vente (calque `sel:quetes`, ignoré si le pack ne l'a pas) ; page Saison = en vente en haut, puis les achats de saison rangés par événement (clé `season`), chaque événement sur une nouvelle ligne ; (2) police lisse Inter UI Medium (SIL OFL, `EemiCore/pack/police/`) dans tous les menus du navigateur (Esteban : « on dirait un enfant qui a écrit ») : fournisseur `ttf` du client (`eemicraft:nav/inter.ttf`, taille 6.75, oversample 4), libellés fixes retirés des fonds et écrits par `NavUi` (`NavFonds.Libelle`), un texte trop large passe aux tailles 6.25/5.75/5.25 (polices `nt<y>_<k>`), jamais au-delà de son bouton (contrôle de gen_nav.py, marge 2 px). Aperçu `EemiCore/apercus/menus.png` ; placement vertical calculé d'après le bytecode du client 26.3, pas encore vu en jeu. 1.9.6 jamais installée (remplacée).
- Installé : EemiCore 1.9.5 (26/11, redémarrage 12:29 sans erreur, `a_deposer_installes/EemiCore-1.9.5.jar`) : textes de la case et de la page Saison sans « ✦ » ni « Ë » (les polices des menus `eemicraft:nt*` n'ont pas ces glyphes : « SAISON : 10 », « NOEL 2026 »). Indépendante du pack (elle corrige l'affichage avec n'importe quel fond) : installée sans attendre la publication du pack 556434b8.
- Avant : EemiCore 1.9.4 (en `.jar.old` ; 26/11, redémarrage 12:07 sans erreur, `a_deposer_installes/`, demande d'Esteban) : dans la boutique (style refuge), la case « Quêtes » de l'accueil devient « ✦ Saison » (les quêtes restent dans le profil) : elle ouvre une page avec les cosmétiques de saison EN VENTE et PAS ENCORE ACHETÉS (clé `season`), qui ne sont plus dans leur catégorie ; une fois achetés ils vont dans leur catégorie (ex. Halloween 2026). Retour à la page Saison après un achat (`saisonView`). Le dessin de la case (icône et mot « QUÊTES ») est dans le pack : le changer = `gen_nav.py` + `gen_droits.py` + publication (non fait). Style classique : rien de changé.
- Avant : EemiCore 1.9.3 (en `.jar.old` ; 26/11, redémarrage 11:36 sans erreur, `a_deposer_installes/EemiCore-1.9.3.jar`, installée avec EemiNoel 1.4.1 et EemiHalloween 1.2.1, demande d'Esteban). Le compteur de `/noel status` a montré « 0/10 » : cause trouvée (vérifiée hors serveur avec l'API Bukkit) = `getConfig()` d'EemiCore a les valeurs par défaut de son jar ; `Saison` RETIRAIT `available-from` pour mettre en vente, et EemiCore relisait alors la date du jar (« 2026-12-01 ») : rien de Noël n'était en vente avant décembre. Corrigé dans EemiNoel 1.4.2 / EemiHalloween 1.2.2 (installées le 26/11 à 11:46 ; vérifié : `/noel status` = 10/10) : `available-from: 2000-01-01` au lieu de supprimer la clé. Règle : ne jamais retirer une clé de la config en mémoire d'EemiCore pour la « vider », lui donner une valeur.
- Contenu de la 1.9.3 : un cosmétique de saison ACHETÉ reste visible, équipable et porté hors saison (titre compris) ; il reste invisible et non vendu pour ceux qui ne l'ont pas (`hide-off-season`). Nouvelle clé `season` (ex. « Noël 2026 ») affichée dans la description : « ✦ Édition Noël 2026 (temporaire) » en vente, « ✦ Édition Halloween 2026 » ensuite ; ajoutée sur le serveur aux 17 cosmétiques d'Halloween et de Noël (backups `/.ai-backups/plugins__EemiCore__config__20261126-1031*`). `Saison.java` (`_commun`, EemiNoel, EemiHalloween) : `off` ne vide plus le texte des titres (il le remet s'il avait été vidé) ; `/noel status` affiche « n/10 cosmétiques de saison en vente dans EemiCore » (compte réel en mémoire).
- Installé : EemiCore 1.9.2 (26/11, redémarrage 11:24 sans erreur, `a_deposer_installes/EemiCore-1.9.2.jar`) : un cosmétique saisonnier hors saison restait visible pour ceux qui l'ont acheté, mais grisé « Hors saison » et non équipable (remplacé par la 1.9.3).
- Avant : EemiCore 1.9.1 (en `.jar.old` ; 26/11, redémarrage 10:54 sans erreur, `a_deposer_installes/EemiCore-1.9.1.jar`, carte #114) : menu « Top 10 des niveaux » du navigateur (style refuge) rangé en pyramide, lue de haut en bas et de gauche à droite (#1 au centre de la 1re ligne, #2-#3, #4-#6, #7-#10 ; cases `liste` 3, 9, 11, 15, 17, 19, 21, 23, 25, 27). Avant, le podium était centré sur la 1re ligne (#1 au milieu, #2 à gauche, #3 à droite…) : « l'ordre commence au milieu ». Les placeholders `%eemi_rank_level_<n>%` étaient déjà justes (vérifié au `papi parse`).
- Plus avant : EemiCore 1.9.0 (supprimée ; 22/11/2026, « 40 jeux » au `eemi reload`). `EemiCore/A_APPLIQUER_1.9.0.md` appliqué au config du serveur (accents, Halloween `available-until: "2026-11-03"` + `hide-off-season`, 6 cosmétiques de Noël). Rien de la 1.9.0 vu en jeu.
- Jamais vérifié en jeu : chapeaux, gadgets, titre au-dessus de la tête (hauteur), anti-vol, menus `/jeux` (alignement), blocage des commandes tapées en maintenance pour un non-op, conflits d'alias `/p`, `/shop`, `/play`.
- Ouvert : niveau dans TAB (optionnel), meilleur joueur dans les hologrammes ; Esteban (17/11) n'aime pas les contours rouges/verts/dorés autour des cosmétiques de la boutique (aucune option dans le config) : à trancher avec Thibault.

**Fonctionnement**
- Source `EemiCore/` (Thibault). Ancienne source 1.5.0 rangée dans `EemiCore-1.5.0-ancien/`. La source de la 1.6.0 vient d'un jar DÉCOMPILÉ par CFR puis retouché (`summary.txt`) ; compiler avec `-Dmaven.repo.local=<tools>\m2` (PlaceholderAPI). Version dans plugin.yml = `${project.version}` (elle était écrite en dur : le jar 1.3.2 se déclarait 1.3.1). `loadbefore` tous les jeux maison.
- Registre `games.<id>` du config : name, icon, join (`console:` + `%player%` possible), spectate, worlds (base + `_2` + `_3`), min, max, multiplier ; `join-aliases` (commandes qui comptent comme un join : maintenance, suivi du groupe, EemiDroits). Jeux tiers (bedwars, tntrun, buildbattle, loupgarou) : statut déduit des joueurs dans leurs mondes. Ajouter un jeu : `games.<id>` + `join-aliases` + `eemi reload` (puis `droits reload`).
- Contrat avec les jeux (console, silencieux, op/console) : `eemi status <jeu> <waiting|starting|playing|ending|nobody> <attente> <en_jeu> <max>` (expire après 10 min) et `eemi reward <pseudo> <jeu> <play|win|kill>`. Un /stop admin ne donne rien. Les jeux multi-parties envoient un statut agrégé (plus d'état `starting`, donc plus d'annonce « La partie démarre »). Exemple de détournement : le Paintball donne ses kills par `eemi give <joueur> coins <kill-coins>` (ne compte pas dans les stats kills).
- Commandes : `/eemi join <jeu>` (point d'entrée unique, emmène le groupe, refusé pour un membre non chef), `/eemi spectate <jeu|joueur>`, `/eemi appels` (couper les appels), `/eemi jeux`, `/eemi top <jeu|level> [wins|kills|plays]`, admin `reload`, `give <joueur> coins|xp <n>`, `cosmetic`, `maintenance`. `/spectate <joueur>` (`/spec`, `/regarder`) pour tous : jeu trouvé par le monde (FFA si z < -150 dans `duels`), s'appuie sur `/duel spectate [joueur]`. `/jouer` (`/play`) : jeu le plus rempli non plein, sinon au hasard dans `random-games` (jeux en maintenance exclus). `/profil`, `/boutique` (`/shop`), `/quetes` (`/quests`, 3 quêtes du jour), `/party` (`/groupe`, `/p`) + `/pc`, `/jeux [catégorie]` (`/minijeux`, `/navigateur`).
- Appels : jeu en attente sous son minimum → [REJOINDRE] cliquable toutes les 60 s au lobby.
- Groupes : `/party` sans argument ou `/party menu` = menu (membres, chef, où ils sont, invitations, parties à regarder, inviter, quitter ; shift-clic du chef = exclure/dissoudre), `/party help` = aide texte. Suivi automatique (`party.auto-follow: true`, `party.no-follow: [skyblock, oneblock, abysse]`) : la commande de join du chef (`isJoinCommand`) est mémorisée 60 s (`LeaderCmd`) ; quand il entre dans un monde du jeu, chaque membre absent de ce jeu rejoue la même commande (`performCommand`), sinon le join du jeu ; jeux à menu (join `console:dm open ...` : BedWars, UHC, speedrun) : les membres attendent le choix du chef. [SUIVRE] reste en secours. `/eemi join` emmène aussi les membres hors du lobby.
- Progression : `plugins/EemiCore/players/<uuid>.yml` (pièces, xp, niveau = 100 + 50×(n-1) xp, stats par jeu). Gains play 5/20, win 25/100, kill 3/10 (pièces/xp) ; bonus 1re partie (+15) et 1re victoire (+50) du jour ; récompense quotidienne par série (20 → 50 au 7e jour, `daily-reward.*`, fuseau `timezone`) ; ligne cliquable `[Quêtes x/3] [Jouer] [Boutique]` à la connexion. Chat « [Niv N] [titre] Nom » (pas dans murder, `chat.disabled-worlds`). Les clés daily-reward, daily-bonus, shop.confirm-above, sounds, lobby-guard ne sont pas dans le config du serveur : défauts du code.
- Boutique : catégories Traînées, Chapeaux, Titres, Messages d'arrivée, Gadgets (actifs seulement au lobby, sauf titres) ; confirmation d'achat au-delà de 500 (`shop.confirm-above`). Prix : ceux d'origine (Thibault, 10/11 : la division par 3 de la 1.6.0 a été ANNULÉE ; les 11 cosmétiques ajoutés en 1.6.0 coûtent 300 à 600). Les pages montrent le vrai objet du cosmétique (`cosmeticItem`), jamais une icône lobby.
- Gadgets en case `gadget-slot` (7) au hub : feux d'artifice 2000 (3 s), pistolet à peinture 2500 (faux blocs de laine `sendMultiBlockChange` 3 s, 64 blocs), grappin 3500, jetpack 6000 (12 s, arrêt au-dessus de `jetpack-max-y` 120) ; pas de dégâts de chute 6-8 s après grappin/jetpack, projectiles sans dégâts ni recul, objet non déplaçable, retiré hors du hub. Modèles des gadgets (`eemicraft:gadget/*`) si `lobby-ui.gadget-models: true`.
- Titres : affichés au-dessus du pseudo au hub (TextDisplay par joueur, PDC `eemicore:tag`, non persistant, `title-tag.height` 2.55 / -0.3 accroupi, `setTeleportDuration(2)`, caché au joueur lui-même ; retiré hors hub, en vanish, spectateur, invisible). Saisonniers par `available-from` / `available-until` (AAAA-MM-JJ) : hors saison caché et non achetable, gardé par ceux qui l'ont (« Édition limitée ») ; avec `hide-off-season: true` (1.9.0) il n'est plus porté non plus. Halloween (Sorcière, Vampire, Zombie, Halloween 2026) : fin au 03/11/2026 dans le config actuel (Esteban l'avait prolongé au 15/11 le 03/11 ; écrasé le 22/11 par l'application de la 1.9.0). Noël 01/12/2026 → 02/01/2027 (Lutin, Renne, Père Noël, Noël 2026 + 6 cosmétiques de la 1.9.0). EemiHalloween / EemiNoel modifient `available-*` EN MÉMOIRE via `/halloween|noel on|off` (un `/eemi reload` remet le fichier).
- Cosmétiques donnés hors boutique (Jump) : `available-until: "2000-01-01"` (pas `casino: true`, qui affiche « Exclusivité du casino » en dur). Particules déjà vendues, à ne pas réutiliser en récompense : FLAME, HEART, NOTE, SOUL, SNOWFLAKE, ENCHANT, SMOKE, RAINBOW, END_ROD, ELECTRIC_SPARK ; casino WAX_ON ; Jump HAPPY_VILLAGER, BUBBLE_POP, DRIPPING_LAVA (`trail_j_flammes` = « Gouttes de lave (Jump) »).
- Classement des niveaux (1.9.0) : par uuid, cache 30 s, place calculée sur la même liste, comptes vides exclus, xp affichée.
- Maintenance : `/eemi maintenance <jeu> on [raison]|off|list` (perm `eemicore.maintenance`, op ; contournement `eemicore.maintenance.bypass`). `plugins/EemiCore/maintenance.yml` (`<jeu>: {reason, by, since}`). Refuse `eemi join` (boussole, PNJ, groupes, [SUIVRE], [REJOINDRE]) et les commandes tapées (`PlayerCommandPreprocessEvent` LOW) commençant par le join du jeu ou un `join-aliases` ; un join d'UN mot (`duel`, `ffa`, `island`) ne bloque que la commande seule. Exclu de `/jouer` et des appels, annonce au hub, parties en cours continuent. Ne bloque pas les commandes lancées par la console (PNJ `console_command`, `dm open`).
- `plugins/EemiCore/status.json` (pour le bot Discord et le site, lus via l'API MineStrator) : `{version: 1, updated_at, games: [{id, name, maintenance, maintenance_reason, state, waiting, playing, max}]}`, écrit au démarrage, toutes les `status-file.interval` (30) s, à chaque maintenance et au reload, par fichier `.tmp` + déplacement atomique. `state` : starting/playing/ending tels quels ; waiting si attente > 0, sinon playing si joueurs en partie, sinon nobody ; jeu maison expiré = unknown ; jeux tiers = playing ou nobody. Pas réécrit à l'arrêt (bot : « pas à jour » après 3 min).
- Placeholders `eemi` : `status_<jeu>` (`status-text.*`, « &7Aucun joueur » pour personne, « &8Maintenance »), `players_<jeu>`, `name_<jeu>`, `maintenance_<jeu>`, `level`, `level_colored`, `coins`, `xp`, `xp_needed`, `title`, `title_prefix`, `team` (préfixe de l'équipe du joueur sur SON scoreboard, `getEntryTeam`), `wins|kills|plays`, `top_<jeu>[_wins|kills|plays]_<rang>`, `top_level_<rang>` (top 10, 30 s). LobbyLive : `feed_1..8` (victoires reçues par reward win, regroupées sur 15 s ; « partie lancée » quand un jeu gagne ≥ 2 joueurs d'un coup, 30 s mini ; paliers de niveau tous les `live.feed-level-every` (5) ; `plugins/EemiCore/feed.yml`), `cat_<cat>` / `catplayers_<cat>` (catégories `live.categories`, entrées `world:nom` ou `world:prefixe*`), `toprot_title` / `toprot_<rang>` (top victoires qui tourne toutes les `live.top-rotation-seconds` 8), `rank_level_<rang>`, `rank_<jeu>[_kills|_plays]_<rang>`. Les jeux tiers n'envoient pas de victoire (absents du fil). « 0 placeholder hook(s) » de PAPI au démarrage est normal (EemiCore s'enregistre lui-même).
- TAB : `_DEFAULT_ tabprefix` de `TAB/groups.yml` = `%eemi_team%%luckperms-prefix%%eemi_title_prefix%` ; tableau `lobby` (`display-condition: "%world%=lobby"`) : date, joueur, niveau + xp, pièces, titre, en ligne, victoires, discord.gg/UuDGVB7Q38, eemicraft.mine.fun.
- Anti-vol : toutes les 10 ticks hors du hub, un joueur en survie/aventure non vanish qui peut voler perd le vol, sauf dans `flight-worlds` (tntrun, hideseek, hideseek_2, hideseek_3, pvprun, bowspleef + copies). Cause : le double saut DeluxeHub donne `allowFlight` et les jeux ne le retirent pas.
- LobbyGuard (`lobby-guard.*`) : retire au lobby les objets marqués PDC des plugins de jeu (`lobby-guard.namespaces`, exclut deluxehub/eemicore/eemiadmin), log de chaque retrait. Tout objet de jeu doit porter une clé PDC de son plugin.
- Filtre des glyphes (depuis la 1.5.3 d'Esteban) : U+E300..E3FF retirés du chat, des commandes, panneaux, enclume et livres (les fonds de menus sont dans `minecraft:default`).
- Menus : `lobby-ui.enabled: true` (fonds + icônes `eemicraft:lobby/*`), `lobby-ui.style` = `refuge` (défaut, navigateur violet et or) ou `classique` (anciens fonds violet/or du pack lobby). Navigateur (18 menus) : `jeux` (6 catégories, Hasard, Casino, Groupe), `jeux_<cat>` (Ambiance sur 2 pages ; Hors Cours grisé « EN TEST » sans `eemihorscours.play`), profil, boutique, page de catégorie (28 cosmétiques/page, cadre or équipé, vert acheté, rouge trop cher), confirmation, quêtes, groupe, listes inviter/joueurs/regarder/top 10. Cadres des jeux : vert = joueurs en attente, or = bêta, grisé = maintenance ou bêta sans accès ; EemiDroits lu par `%eemidroits_etat_<id>%` / `%eemidroits_acces_<id>%` (ignoré s'il manque). Clic sur un jeu = `p.chat("/eemi join <id>")` (ou `duel`, `ob`, `abysse`, `horscours join`) ; clic droit sur un jeu en cours = regarder.
- Générateur des menus : `EemiCore/pack/gen_nav.py <pack publié.zip> <sortie.zip>` : ajoute seulement `eemicraft:nav` (fonds U+E000.., calques U+E400..), `eemicraft:nt<y>` (textes), objet invisible `eemicraft:nav/vide` ; écrit `EemiCore/src/main/java/fr/eemicraft/core/ui/NavFonds.java` et `EemiCore/apercus/menus.png` ; titre géré par `ui/NavUi.java` (copie de celle du Refuge ; texte clair 0xEEF0F6). Jeux par catégorie dans `CATEGORIES` de gen_nav.py : ajouter un jeu = l'y ajouter, régénérer pack + NavFonds, recompiler. Couleurs : panneau 34,26,74, cadre 42,47,58, onglet doré, bas = `bg_profile` du pack lobby. TOUJOURS gen_nav.py PUIS `EemiDroits/pack/gen_droits.py`. Base = dernier pack publié (b05fe66f contenait nav + droits).
- Objets DeluxeHub du hub qui ouvrent EemiCore : Groupe (PAPER cmd 2056, `[COMMAND] party menu`, slot 2), Profil et boutique (PAPER cmd 2050, `[COMMAND] profil`, slot 6). La moustache reste sur `[MENU] serverselector` (Thibault, 15/11 : « remets les mini-jeux comme avant ») : `/jeux` existe mais rien ne l'ouvre.

**Pièges**
- Ne jamais recompiler une ancienne source par-dessus la version du serveur : la 1.5.3 d'Esteban (LobbyUI) puis la 1.6.0 décompilée ont remplacé des versions dont la source était ailleurs ; un `EemiCore/target/EemiCore-1.5.3.jar` local (LobbyGuard) n'a jamais été livré. Que les changements des 1.5.1/1.5.2 soient dans la source décompilée n'a pas été vérifié.
- EemiCore garde les joueurs en mémoire et réécrit leur fichier à l'arrêt : pour modifier/restaurer un fichier joueur, ARRÊTER le serveur d'abord. Copie de test : `/sauvegardes/eemicore-Enosik-avant-test-20261028.yml` (Enosik = `f99125bc-4e15-4172-8408-50f31be2601d`). Reset de toute la progression le 28/10 (Thibault) : ancien dossier `/plugins/EemiCore/players-avant-reset-20261028` (stats Duels, records bateaux, inventaires non touchés).
- Le TextDisplay du titre n'est PAS un passager : un joueur qui porte une entité ne peut plus être téléporté.
- WorldGuard bloque les apparitions des plugins au hub : EntitySpawnEvent en HIGHEST rétablit celles taguées par EemiCore.
- `%eemi_team%` ne voit que les équipes avec le pseudo en entrée et un préfixe : rien pour Murder ni pour les équipes de lignes de scoreboard. Sans lui, TAB écrase le nom des équipes dans la liste.
- Ne pas mettre un placeholder EemiCore dans TAB/DeluxeHub avant que la version qui le fournit soit installée (TAB affiche le texte brut).
- Suivi du groupe : un jeu « menu » ne suit qu'au changement de monde du chef ; une file au lobby (Speedrun) ne suit pas → EemiSpeedrun ajoute lui-même les membres en lisant le groupe par réflexion (`partyOf` → `leader`, `members`, noms de la 1.5.0 ; à revérifier si renommés).
- Depuis le navigateur, les clics passent par le chat du joueur : en vanish, EemiAdmin les bloque aussi.
- L'API MineStrator (read_file) peut renvoyer une copie de fichier vieille de quelques secondes (status.json, maintenance.yml) : relire avant de conclure.
- `join-aliases` copiés des `blocked-commands` d'EemiAdmin, sans `roulette objet|lettre` (rattachement non vérifié).
- Objets d'autres modes au lobby (#50/#53) : cause = `default-ungrouped-worlds: true` de Multiverse-Inventories (doit rester `false`, vérifié le 17/11) ; LobbyGuard est le filet. Les objets déjà sauvés dans les inventaires du lobby y restent.
- Mention « NOUVEAU » dans un menu : 5 jours max, avec un commentaire YAML daté (`# NOUVEAU jusqu'au JJ/MM`), retirée à la date (Esteban, 17/11).
- Titres affichés par les jeux : titre ≤ 16 caractères, sous-titre ≤ 38, le reste dans le chat.
- Proposé pour l'Abysse, pas fait : drapeau à la déconnexion dans une strate → renvoi à Bordgouffre à la reconnexion.

**À vérifier en jeu**
- Chapeaux face à DeluxeHub / MV-Inventories ; rendu du chat avec TAB ; alias `/p`, `/shop`, `/play` sans conflit.
- Hauteur du titre au-dessus de la tête ; gadgets (dont jetpack et pistolet à peinture) ; anti-vol en FFA/PvP.
- Maintenance : refus d'un non-op, commandes tapées bloquées ; menus DeluxeMenus `[player] <join>` (passent par l'événement seulement si DeluxeMenus utilise `player.chat`).
- Navigateur `/jeux`, profil, boutique : alignement des fonds et textes, lisibilité des icônes 16 px, ascent négatif des polices.
- 1.9.0 : classement des niveaux, cadre rouge « trop cher » en style classique, cosmétiques hors saison retirés (chapeau citrouille), cosmétiques de Noël au 01/12.
- `/spectate` refusé à Antonin (29/10) : cause non trouvée, les commandes spectate ne sont pas réservées aux ops.
- Suivi du groupe automatique dans un jeu à menu (BedWars) et en multi-parties.

## EemiDroits (/droits, bêta, maintenance, interdictions)

**État**
- Installé : EemiDroits 1.1.1 (26/11, redémarrage 15:20 sans erreur, avec EemiCore 1.9.7 et le pack db80f8a6) : même police lisse que le navigateur (polices `dt<y>[_k]`, libellés `Fonds.Libelle`). Compilation : l'API LuckPerms 5.4 se télécharge (mvn sans `-o`).
- Installé : EemiDroits 1.1.0 (interface au style des menus du lobby). `/droits` jamais vu en jeu.

**Fonctionnement**
- Source `EemiDroits/` (LuckPerms API 5.4 en `provided`). `depend: [LuckPerms]`, `softdepend: [EemiCore, PlaceholderAPI]`.
- `/droits` (`/gestion`, perm `eemidroits.admin`, op) : accueil (Joueurs, Jeux, Bêta, Interdits avec statut en direct), listes de 28 cases (joueurs, bêta-testeurs, interdits, groupes, mondes, jeux du joueur, état des jeux), fiche d'un joueur (bouton Bêta, tête, 18 cases de droits = icônes du catalogue `droits` du config, cadre vert accordé / rouge refusé / or « a déjà le droit », boutons Groupes, Mondes, Jeux). Clic gauche accorder/retirer, clic droit refuser/retirer : permissions LuckPerms GLOBALES. Au-delà de 18 droits dans le catalogue, les suivants ne s'affichent pas (avertissement). Catalogue actuel inclut « Se renommer » (`eeminoms.self`) et « Renommer les autres » (`eeminoms.admin`).
- Commandes : `joueur <pseudo>` (hors ligne aussi), `beta <pseudo> [on|off]`, `jeu <jeu> <ouvert|beta|maintenance> [raison]`, `jeux`, `monde <pseudo> <monde> [on|off]`, `interdire <pseudo> <jeu> [on|off]`, `reload`.
- Tag Bêta = `eemidroits.beta` (default false, les ops ne l'ont pas d'office). Admin = `eemidroits.bypass` (op, enfant de `eemidroits.admin`). Le droit « Jeux en maintenance et en bêta » du catalogue donne `eemidroits.bypass` + `eemicore.maintenance.bypass`.
- Jeux = section `games` de `plugins/EemiCore/config.yml` (+ `jeux-supplementaires`), relue par `droits reload` (à faire après chaque ajout de jeu). États dans `plugins/EemiDroits/jeux.yml`. Maintenance = `eemi maintenance <jeu> on|off` lancé par EemiDroits (une maintenance posée directement par `/eemi maintenance` est aussi lue dans `EemiCore/maintenance.yml`). Bêta : géré par EemiDroits seul, annoncé à tous.
- Refus : (1) commandes tapées (`PlayerCommandPreprocessEvent` LOWEST, avant EemiCore) : `eemi join|spectate <jeu>`, join / join-aliases / spectate du jeu ; (2) filet : `PlayerTeleportEvent` HIGH vers un monde d'un jeu refusé ou un monde interdit = annulé, puis le joueur lance la commande de sortie (`sortie.<jeu>`, sinon « X join » → « X leave ») ; déclarée pour `entrainement` et `speedrun`. Un joueur déjà dans le jeu peut changer de monde du jeu. Un monde de base couvre `_2`, `_3`, `_modele` ; `mondes-proteges` [lobby] jamais interdits. Monde interdit : renvoi à `position-hub` (722.5 51 650.5) à la connexion, au changement de monde, à la réapparition.
- Placeholders : `%eemidroits_etat_<jeu>%` (vide / §dBêta / §cMaintenance), `%eemidroits_acces_<jeu>%`, `%eemidroits_beta%`, `%eemidroits_tag%` (`tag-beta`, pour TAB). Lus par le navigateur d'EemiCore ; pas encore branchés dans DeluxeHub ni TAB.
- Fonds : police `eemicraft:droits`, textes `eemicraft:dt<y>`, `ui/Ui.java` = copie de NavUi. Générateur `EemiDroits/pack/gen_droits.py <pack.zip> <sortie.zip>` (importe `EemiCore/pack/gen_nav.py`, ajoute `font/droits.json`, `font/dt*.json`, `textures/font/droits/`, écrit `EemiDroits/src/main/java/fr/eemicraft/droits/ui/Fonds.java` et `EemiDroits/apercus/menus.png`) ; il faut `eemicraft:nav/vide` dans le pack d'entrée.

**Pièges**
- `performCommand` (PNJ, menus) ne déclenche pas l'événement de commande : c'est le filet de téléportation qui refuse, puis la commande de sortie désinscrit.
- Ordre des générateurs : gen_nav.py PUIS gen_droits.py ; un pack refait par gen_nav depuis un pack sans les fichiers droits les perd.
- Un admin non-op avec seulement `eemidroits.bypass` reste bloqué par la maintenance d'EemiCore sans `eemicore.maintenance.bypass`.

**À vérifier en jeu**
- Rendu des 9 écrans de `/droits`.
- Clics de la boussole DeluxeHub (`[COMMAND]`) et des PNJ FancyNpcs : refusés par l'événement de commande ou par le filet.
- La commande de sortie ne laisse pas un joueur coincé dans la file d'un jeu tiers (BedWars `bw leave`).

## EemiAdmin (vanish et outils d'admin)

**État**
- Installé : EemiAdmin 1.1.2. Jamais testé en jeu dans son ensemble.
- Ouvert (Esteban, 15/11) : sortir du vanish et retirer l'invulnérabilité quand un joueur perd `eemiadmin.vanish` (voir Pièges).

**Fonctionnement**
- Source `EemiAdmin/`. `/vanish` (`/v`, `/vanish list`), `/admintp <joueur>` (`/atp`), `/invsee` (`/inv`), `/endersee` (`/ec`) ; permissions `eemiadmin.vanish`, `eemiadmin.see` (op). Vanish persistant (`data.yml`) : invisible, invulnérable, vol, pas de ramassage ni de ciblage, métadonnée Bukkit `vanished` (lue par d'autres plugins).
- En vanish, l'inventaire est mis de côté PAR MONDE (`saved-inventories.<uuid>.<monde>`, portails gérés, rendu au retour dans le bon monde) et remplacé par les outils : boussole « Changer de monde » (menu des mondes, clic = console `mv tp <joueur> <monde>`), boussole de récupération « Joueurs en ligne » (clic gauche tp, droit inventaire, shift coffre de l'Ender), colorant vert « Quitter le vanish ». Outils redonnés 10 ticks après chaque changement de monde (DeluxeHub redonne ses objets 5 ticks après l'arrivée), non déplaçables, jamais enregistrés par MV-Inventories (inventaire réel rendu avant la téléportation), retirés partout à la sortie du vanish et nettoyés hors vanish (connexion, changement de monde, toutes les 2 s).
- `blocked-commands` : commandes d'inscription aux jeux bloquées en vanish quand elles sont tapées (les menus/PNJ qui lancent la commande à la place du joueur passent, sauf le navigateur `/jeux` d'EemiCore qui passe par le chat). Inclut `eemi join`, `jouer`, `play`, `bw autojoin` et les joins de chaque jeu (à compléter pour tout nouveau jeu ; pris au redémarrage). `versus/l4d/left4dead join` retirés (Dead Center supprimé).
- Loup-Garou : spectateur forcé dans `lg` ; `lg-start-guard` sort les admins en vanish de la région pendant `/lg start` (LoupGarou 1.0.5 compte tout joueur de la région).

**Pièges**
- Joueur resté invulnérable (LaBaleineMC, 15/11) : il était en vanish et a été déop pendant ce temps ; sans la permission il n'a pas pu sortir proprement et le drapeau d'entité `Invulnerable` est resté dans son .dat (vu par `data get entity <j> Invulnerable` = 1b). Avec ce drapeau, les coups ne déclenchent même pas d'événement. Correctif proposé par Esteban, jamais installé : EemiReparer 1.0.0 (source `EemiReparer/`, retire l'invulnérabilité d'un joueur non vanish à la connexion et au hub, `/reparer <joueur>`).
- Boussoles du vanish retrouvées dans l'inventaire du lobby (29/10) : cause supposée, changement de monde en vanish sauvé par MV-Inventories (non relu) ; corrigé par le rendu de l'inventaire avant la téléportation.

**À vérifier en jeu**
- Outils du vanish rendus/retirés correctement en changeant de monde (dont skyblock et portails).
- Commandes de join bloquées en vanish, y compris depuis `/jeux`.

## Pack de textures EEMICRAFT

**État**
- Pack EN LIGNE depuis le 26/11 15:20 (publié par Claude, SHA-1 `db80f8a679faf0f60fab66fedb93dc910f4c5894` vérifié en le retéléchargeant, `resource-pack-sha1` mis à jour, backup `/.ai-backups/server__20261126-141929.properties`) : `a_deposer_installes/EemicraftPack-db80f8a6.zip` = pack 556434b8 + `gen_nav.py` + `gen_droits.py` nouvelle version (police lisse Inter `eemicraft:font/nav/inter.ttf` + `OFL.txt`, symboles HD `textures/font/nav/symboles.png`, polices `nt<y>[_k]` / `dt<y>[_k]` en fournisseur `ttf`, fonds sans libellés, calque `boutique:sel:quetes`). Seuls des fichiers `eemicraft:nav`/`droits`/`nt*`/`dt*` changent. `verifier_pack.py` : la même erreur déjà connue (`squid_garde`). La source de la publication `/EemicraftPack.zip` reste à la racine du serveur. Le pack 556434b8 ci-dessous n'est plus la base.
- Pack EN LIGNE depuis le 26/11 (publié par Esteban à 14:44, redémarrage 14:45 sans erreur) : `a_deposer_installes/EemicraftPack-556434b8.zip`, SHA-1 `556434b8f93c338596e7a0ba0f521bf733baf97b` (vérifié en le retéléchargeant après publication ; `resource-pack-sha1` mis à jour, backup `/.ai-backups/server__20261126-134459.properties`) = pack en ligne c9a5ab6f (vérifié en le retéléchargeant) + `gen_nav.py` (case « quetes » de l'accueil de la boutique dessinée « SAISON » avec l'icône `horloge`, accent ambiance) puis `gen_droits.py`. Seule `textures/font/nav/bg_boutique.png` change (comparé pixel par pixel) ; `NavFonds.java` et `Fonds.java` identiques. `verifier_pack.py` : 1 erreur déjà présente dans le pack en ligne (`squid_garde.png` sans `equipment/squid_garde.json`). Copie de l'ancien : `_sauvegardes/EemicraftPack-en-ligne-c9a5ab6f.zip` (non suivie par git). Aperçu : `EemiCore/apercus/boutique-saison-avant-apres.png`. Copie déposée à la racine du serveur `/EemicraftPack.zip` (source de la publication, laissée en place).
- Pack en ligne : https://eemicraft.strator.gg/pack/EemicraftPack.zip (hébergement web 2336 de la MyBox, HTTPS Let's Encrypt). `server.properties` : `require-resource-pack=true`, prompt JSON « Pack de textures EEMICRAFT (moustache du lobby) », `resource-pack-sha1=556434b8f93c338596e7a0ba0f521bf733baf97b` (26/11 ; avant : c9a5ab6f, copie `_sauvegardes/EemicraftPack-en-ligne-c9a5ab6f.zip`, vérifiée en ligne le 26/11).
- c9a5ab6f = pack du resto (Esteban, 17/11) : pack en ligne 47db51ec (Squid Game) + 42 fichiers `eemicraft:resto/…`, 2 588 fichiers. Lignée des packs publiés précédents : b05fe66f (15/11, menus violet + /droits) → 348826e3 (16/11) → 47db51ec → c9a5ab6f. Que le fichier en ligne corresponde bien à c9a5ab6f n'a pas été revérifié en le téléchargeant ; après publication il fallait aussi `textures: true` dans `plugins/EemiResto/config.yml` + `/resto admin reload` (fait ou non : inconnu).
- Copie locale de l'ancien pack en ligne : `_sauvegardes/EemicraftPack-en-ligne-47db51ec.zip`. `a_deposer_installes/EemicraftPack-b05fe66f.zip` n'est PLUS la dernière base.

**Fonctionnement**
- Un seul pack pour tout le serveur. Base pour toute modif : le pack EN LIGNE (vérifier le SHA-1 de server.properties, retélécharger le pack en ligne), jamais une copie locale.
- Publication (règle de Thibault, 17/11) : déposer le zip sur le serveur → `publish_server_file_to_webhosting` vers `pack/EemicraftPack.zip` (`/httpdocs/pack/`) → vérifier le SHA-1 → `resource-pack-sha1` → redémarrer. Un jar qui utilise de nouvelles textures s'installe en même temps que son pack. Vérifier avant avec `EemiRefuge/pack/verifier_pack.py <zip>` (0 erreur, aucune collision).
- Accès à l'hébergement web (id 2336) : Thibault, et Esteban depuis le 26/11 (droits donnés par Thibault ; `list_webhosting_files` et `publish_server_file_to_webhosting` marchent). Un zip ne peut pas être envoyé sur le serveur par les outils : le déposer par le panel (à la racine `/`, pas dans `/plugins` où traînent d'anciens `EemicraftPack.zip`), vérifier sa taille, puis publier. Ne JAMAIS changer `resource-pack-sha1` avant la publication (pack obligatoire → joueurs expulsés).
- Générateurs (dans le dépôt) :
  - `EemicraftPack/build_pack.py` : part de la base `EemicraftPack/publie/EemicraftPack-lobby.zip` (pack « lobby » fourni par Thibault le 10/11, icônes et fonds de menus d'une autre personne ; extraite dans `EemicraftPack/base/`, fusion des range_dispatch/select), ajoute : têtes de mouton Sheep Wars (`assets/eemicraft/items/sheep_*.json`, modèle `item/sheep_head`, textures vanilla copiées dans `EemicraftPack/vanilla/`), objets DeluxeHub, boss du skyblock (section BOSSES), section Abysse, 147 textures Abysse (`art_abysse.py`, planche `abysse_planche.png`), boutons et police `eemicraft:icones` U+E400..E409 (`art_ui.py`), fonds et icônes du lobby (`art_lobby.py`), sons `eemicraft:ui.*` (sons vanilla rejoués), gadgets `eemicraft:gadget/feux|peinture|grappin|jetpack`. Table : `EemicraftPack/ICONES.md`. Date fixe, sort zip + SHA-1.
  - NE contient PAS : karts, paintball (`EemiPaintball/pack/`), Hors Cours (`EemiHorsCours/pack/gen_loic.py`), meubles (`EemiMeubles/gen_meubles.py`), resto, Squid Game, menus `eemicraft:nav` et `droits`. Le relancer tel quel EFFACE tout ça.
  - `EemiCore/pack/gen_nav.py <pack.zip> <sortie.zip>` (menus `eemicraft:nav`, textes `eemicraft:nt<y>`, objet `eemicraft:nav/vide`, écrit `NavFonds.java` + `EemiCore/apercus/menus.png`) PUIS `EemiDroits/pack/gen_droits.py` (`font/droits.json`, `font/dt*.json`, `textures/font/droits/`, écrit `Fonds.java`), toujours dans cet ordre. Ils n'ajoutent que leurs fichiers au pack d'entrée.
  - gen_loic.py, gen_meubles.py, `EemiPaintball/pack/` : ajoutent leurs fichiers au pack publié (pas à build_pack).
- Modèles maison :
  - Moustache du lobby : PAPER `custom_model_data: 1001` (`assets/minecraft/items/paper.json` en range_dispatch, repli sur le papier). Icônes `eemicraft:lobby/*` du pack lobby dans le même `paper.json`, cmd 2001..2067 (2048 info, 2050 boutique, 2056 groupe) : objets DeluxeHub Infos / Groupe / Profil en PAPER 2048 / 2056 / 2050. Anciens modèles LEAD 1002 (`eemicraft:item/groupe`) et EMERALD 1003 (`eemicraft:item/boutique`) remplacés. Boussole non utilisée (modèle vanilla complexe).
  - Fonds des menus DeluxeHub : titre `&f` + U+E300 + glyphe (selector E310, iles E311, survie E312, equipes E313, combat E314, tnt E315, ambiance E316, dans `minecraft:default`) + U+E301 + texte ; caractères réels UTF-8 dans les yml. Fonds actuels violet nuit et or (cadre #2A2F3A, cases #160F38 / #2B1F5E à 50 %, `CELL_ALPHA` 128 dans art_lobby.py), icônes au style `EemicraftPack/references/style_icones.png`.
  - Boss du skyblock : 7 cubes `eemicraft:boss_<id>` (correcteur, president, examinateur, directeur, bug, machine, wifi), aperçus `EemicraftPack/boss_*_preview.png` ; posés par le datapack du skyblock.
  - Convoyeur de l'Abysse : `eemicraft:block/convoyeur` + `items/abysse/convoyeur.json`, affiché en item_display par EemiAbysse (plus de retexture de `magenta_glazed_terracotta`).
  - Objets Abysse : `item_model eemicraft:abysse/<id>` (`EemiAbysse/ids_modeles.txt`).
- Packs séparés : RefugePack (Dernier Refuge) et tout pack de mode = chargé seulement dans les mondes du mode (`addResourcePack` / `removeResourcePack(UUID)`).

**Pièges**
- Ne jamais retexturer un bloc ou objet vanilla dans le pack global (règle de Thibault, 17/11) : la retexture de `magenta_glazed_terracotta` (convoyeur) s'affichait dans le Hunger Games, d'où son retrait. Utiliser un `item_model eemicraft:…` / item_display, ou un pack séparé par mode.
- Une texture dans `textures/entity/` n'est pas vue par un modèle d'objet (violet/noir) : `textures/item/` ou `block/`.
- Un jar qui pose `item_model` sans le pack publié = objets violet/noir.
- Le 06/11, le pack paintball existait en deux versions (avec karts 2906e739, publiée ; sans karts 7595d061, jamais en ligne) : leçon = toujours repartir du pack EN LIGNE.

**À vérifier en jeu**
- Textures du resto (pack c9a5ab6f) et `textures: true` d'EemiResto.
- Icônes du lobby en 16 px, alignement des fonds `eemicraft:nav` / `droits`, opacité des cases (monter `CELL_ALPHA` si le fond passe bien sous les objets).
- Orientation et taille des cubes des boss.

## PvP 1.8 : OldCombatMechanics et EemiBlocage

**État**
- OldCombatMechanics installé (jar `OldCombatMechanicsDisablehitcooldown.jar`, base 2.7.0 SpigotMC, compatible 26.3) : seul responsable du combat 1.8. EemiBlocage 1.1.0 installé. Ni l'un ni l'autre testé en combat depuis les dernières versions.
- Modeset `old` (config du serveur lue le 22/11) : bw2..bw8, bw2_solo..bw8_solo, bw3_duo, bw4_duo, bw2_4, bw5_4..bw8_4, thetowers, sheepwars, ballsofsteel, duels, hungergames, hg_altrac, skywars, sw_twister, thebridge, tonteroyale, uhc, pitchout, blocksumo, pvprun, tntwizards, jumpleague, + copies `_2`/`_3` de thetowers, sheepwars, ballsofsteel, hungergames, hg_altrac, skywars, sw_twister, thebridge, tonteroyale, uhc, pitchout, pvprun, tntwizards, jumpleague. `__default__: [ "new" ]`. bw1 n'y est plus.
- EemiBlocage `worlds` (lu le 22/11) : les mêmes mondes BedWars + thetowers, sheepwars, ballsofsteel, duels, uhc, thebridge, jumpleague, pvprun, tntwizards (+ `_2`/`_3`) ; PAS hungergames, hg_altrac, skywars, sw_twister, tonteroyale, pitchout, blocksumo. `block-base: -0.5`, `block-factor: 0.5` ; `log-blocks` absent du config (défaut du code).

**Fonctionnement**
- Nouveau monde de jeu PvP : l'ajouter au modeset old d'OCM (`OldCombatMechanics/config.yml`, pris au redémarrage) et, si on veut le blocage à l'épée, aux `worlds` d'EemiBlocage (redémarrage).
- OCM : vitesse d'attaque 40 (pas de recharge ; `attribute <joueur> minecraft:attack_speed get`), critiques 1.8 (×1,5, permis en sprint), dégâts 1.8 quasi ceux de 26.3 (épée diamant 8 contre 7). Recul : valeurs 1.8 par défaut ; valeurs « practice » en commentaire dans son config. `sword-blocking` dans `disabled_modules`. `disable-crafting` désactivé (le Bingo peut demander un bouclier). Vérifier le modeset : `papi parse <joueur> %ocm_modeset%`.
- EemiBlocage (source `EemiBlocage/`) : UNIQUEMENT la garde à l'épée. Composant vanilla `blocks_attacks` posé sur les épées dans ses mondes (animation seulement, réduction neutre), retiré ailleurs et à l'arrêt. Sur `EntityDamageByEntityEvent` en HIGHEST (après OCM), si la victime a une épée levée (`hasActiveItem` + épée) : base = (1 + base) × `block-factor` via `setDamage` (armure recalculée), modificateur BLOCKING à 0. Annule le recul « bouclier » moderne subi par l'attaquant. `log-blocks: true` écrit chaque coup bloqué en console (« Garde de X : base a -> b, degats finaux c »). Ne touche ni dégâts, ni recul, ni vitesse d'attaque.

**Pièges**
- Le `sword-blocking` d'OCM est cassé en 26.3 (« Paper sword blocking components unavailable; falling back to legacy offhand shield swap », NoSuchMethodException DataComponentPatch.entrySet) : il met un bouclier en main gauche. Le laisser désactivé.
- EemiBlocage 1.0.0 annulait tous les dégâts en garde (réduction bouclier vanilla du composant + module bouclier d'OCM cumulés, probablement) : d'où le composant neutre + calcul manuel.
- bw4/bw5 inexistants ou `duels` créé après OCM : simple avertissement au démarrage puis « Loaded configured world duels ».
- EemiCombat (source `EemiCombat/`, dossier gardé, jar retiré) : plugin maison qui refaisait tout le combat 1.8 (dégâts, recul, garde, regen). RETIRÉ le 27/10 par Thibault (« EEMI COMBAT EST HORRIBLE PASSE A OCM ») : ne pas le remettre. Leçons : recul refait par `setVelocity` = double envoi avec `io.papermc.paper.event.entity.EntityKnockbackEvent` ; un recul doit partir de la vitesse actuelle divisée par la friction (2) ; en 1.8 pas de recul sur un coup en combo ; la garde 1.8 = objet en cours d'utilisation (`hasActiveItem`), pas `Player.isBlocking()` de Paper. Code 1.8.9 de référence : miroir non officiel https://github.com/Marcelektro/MCP-919.
- Quand on « supprime » un vieux jar sur le panel, il est déjà resté dans /plugins : vérifier avec `search_files` avant de redémarrer.

**À vérifier en jeu**
- Garde à l'épée : réduction réelle (activer `log-blocks`), effet du module `shield-damage-reduction` d'OCM sur une épée qui bloque, recul d'un coup bloqué.
- Trancher : faut-il ajouter hungergames, skywars, pitchout, blocksumo, tonteroyale (en old) aux `worlds` d'EemiBlocage ?

## PvP : Duels et FFA (EemiDuels)

**État**
- EemiDuels 1.2.1 installé. Pas encore testé en partie ; ouverture du menu `/duel` depuis la boussole non revérifiée.

**Fonctionnement**
- Source `EemiDuels/`, modèle Hypixel Duels / serveurs practice (Minemen, Lunar). Combat 1.8 = OCM (`duels` en old) + garde EemiBlocage.
- Monde `duels` vide créé par le plugin (pas Multiverse). Arènes construites au premier démarrage, si `ARENA_VERSION` ou leur nombre change, ou par `/duel build` : 6 arènes de duel 41×41 (sol y 100, x = i × 150, z 0, murs et plafond de barrières), 3 plateformes Sumo (disque r 6, z 300), FFA 71×71 avec 8 piliers (centre 0, -300). État : `plugins/EemiDuels/arenas.yml`. Tester des blocs : `execute in minecraft:duels run forceload add <x> <z>` (256 chunks max) puis `forceload remove all`.
- Kits de duel : Classic (fer, arc, canne), NoDebuff (diamant Prot II, épée Tranchant III Aura de feu II, 16 perles, potions de soin II jetables, Vitesse II, résistance au feu), Sumo (rien, tomber = perdre), Boxing (épée, Vitesse II, aucun dégât, 100 coups), BuildUHC (diamant Prot II, blocs, seaux, 6 pommes d'or, pas de regen naturelle ; blocs posés et liquides retirés à la fin). Objets incassables.
- Files non classées et classées (Elo, K 32, écart accepté 100 + 20 par seconde d'attente), défis `/duel <joueur> [kit]` avec [ACCEPTER] (30 s), 5 s immobile, 10 min puis égalité, coup mortel annulé = défaite, chute = défaite, déconnexion / changement de monde / `/duel leave` = abandon. Résumé façon practice, retour au hub après 3 s.
- FFA : `/ffa`, kit FFA (`Kit.giveFfa` : fer Prot I, épée en diamant, canne, arc + 16 flèches, 3 pommes d'or, 16 steaks), 3 s de protection, réapparition immédiate, séries annoncées tous les 5 kills ; kill = soin complet + 1 pomme d'or (5 max) + 4 flèches (`ffaKillReward`). `/ffa leave` ou /lobby.
- Commandes : `/duel` (menu, alias `/pvp`), `duel open <joueur>` (console), `/duel stats [joueur]`, `/duel top <kit|ffa>`, `/duel spectate [joueur]` (utilisé par `/spectate` d'EemiCore ; FFA = z < -150 dans `duels`), `/ffa spectate`. Stats (Elo) : `plugins/EemiDuels/stats.yml` (non remises à zéro par le reset EemiCore).
- Préfixe « Duels » (§c, catégorie Combat), préfixe FFA à part. Boussole : sous-menu `combat.yml`, PvP case 15. `duels` dans les mondes désactivés de DeluxeHub.

**Pièges**
- Menu `/duel` qui s'ouvrait puis se refermait depuis la boussole (cause supposée : inventaire ouvert pendant le clic) : tous les menus s'ouvrent 2 ticks plus tard. Si ça revient, tester `/duel` tapé dans le chat.

**À vérifier en jeu**
- Ouverture du menu depuis la boussole ; files classées ; kits ; FFA (kill reward, séries) ; `duels` à ajouter à la disable-condition TAB si les scoreboards se gênent.

## Charte des textes des jeux (passe UX du 10/11)

- Consigne commune : `_commun/BRIEF_UX_20261110.md`.
- Préfixe : `§8[` + couleur de la catégorie de la boussole + nom + `§8] §7`. Équipes §9 (Towers, Bridge, Balls of Steel, Sheep Wars) ; Combat §c (SkyWars, Hunger Games, Duels/FFA, Pitchout, JumpLeague) ; TNT §6 (TNT Tag, Bow Spleef, PVP Run, TNT Wars, TNT Wizards) ; Ambiance §d (Murder, Cache-cache, Block Party, Tonte) ; Survie §a (UHC Run, EEMINGO). Saison : `§8[§aNoël§8] §7`.
- Accents dans tous les textes joueurs (aucun « Ã© ») ; messages de console sans accents.
- Titres : titre ≤ 16 caractères, sous-titre ≤ 38, le reste dans le chat. Égalités : titre « Égalité ! » + pseudos ou « N gagnants » (jamais « Gagnants dans le chat »).
- Menus : Retour/Fermer en bas de tous les menus (`eemicraft:lobby/retour|fermer`), boutons `eemicraft:lobby/precedent|confirmer|annuler`.

# Lobby

## Lobby (monde, spawn, WorldGuard, sauvegarde)

**État**
- Nouveau lobby en service ; ancien lobby (vers x 0..60, z -130..-20) abandonné : ses PNJ ont été supprimés (10/11) et EemiRing retiré (16/11).
- Ouvert : spawn du monde `lobby` peut-être encore en 8 -63 8 (voir Pièges) ; ménage restant (fichiers `.old`, déop BanTeEs/Qorwinn, monde `towers`).

**Fonctionnement**
- `server.properties` : `level-name=lobby`, monde vide, `allow-nether=false` ; `bukkit.yml` : `allow-end: false`. Joueurs en survie. Dossier du lobby (overworld) : `/lobby/dimensions/minecraft/overworld` ; les autres mondes sont à côté dans `/lobby/dimensions/minecraft/<monde>` (ne pas y toucher).
- Nouveau lobby, dans le même monde `lobby` (overworld) : île ronde d'environ 300 blocs de diamètre centrée vers 663 650, sur une prairie carrée x 500..827, z 500..803 (y 0..140). Spawn DeluxeHub 722 51 650 (yaw ≈ 86,6, dans `DeluxeHub/data.yml`). Place ronde centrée vers 670 653, 11 dalles doubles de pierre lisse en y 49 en cercle (socles des PNJ, y 50) ; fontaine au centre (aujourd'hui sous le sapin de Noël).
- Analyse hors ligne du lobby (1,43 M blocs, 141 types) : `lobby_analyse/lobby_top.png`, `lobby_analyse/lobby_blocs.txt` ; script `scratchpad/scripts/lobby_scan.py` (hors dépôt).
- Liaisons des clôtures/vitres/barreaux/murets après le `//paste` FAWE (~4 300 blocs restés « isolés ») : corrigées par `fill <boîte> <bloc> replace <bloc>` (fill recalcule la forme d'après les voisins) via la fonction `eemibb:lobby_conn` (264 fill), lancée avec `forceload add 576 528 767 751` puis `forceload remove all`. Ne couvre pas ce qui a été collé après le 28/10 16:02 ; pour refaire : relire les régions et régénérer (`scratchpad/scripts/conn_scan.py`). Les blocs `waterlogged` repassent à false.
- WorldGuard `__global__` du monde lobby : passthrough deny + membres admins ; pvp, item-drop, mob-spawning deny. `mobs.block-plugin-spawning: true` dans `WorldGuard/config.yml` : tout plugin qui fait apparaître une entité au lobby doit la rétablir (CreatureSpawnEvent/EntitySpawnEvent en HIGHEST), comme EemiCreateurs, EemiVie, EemiHalloween, EemiNoel.
- Sauvegarde « lobby final » (28/10/2026, avant Halloween) : `/sauvegardes/lobby-final-20261028.zip` = `lobby/dimensions/minecraft/overworld`, `plugins/FancyNpcs/npcs.yml`, `plugins/DecentHolograms/holograms`, `plugins/DeluxeHub`, `plugins/EemiMusique`, `plugins/EemiCreateurs`, `plugins/EemiRing`. Procédure : `/sauvegardes/LISEZMOI-lobby.txt` (serveur arrêté, renommer les versions actuelles pour les garder, extraire à la racine, redémarrer). npcs.yml contient aussi les boutiques BedWars : ne le remettre que si des PNJ du lobby ont changé. `lobby/level.dat` (spawn du monde) n'est pas dans le zip. Elle date d'avant la refonte des PNJ/hologrammes du 10-11/11 : la restaurer ramènerait les anciens PNJ de jeux.
- Description du serveur (Thibault, 01/11) : `motd=§6§lBienvenue sur le serveur Minecraft\n§eofficiel de l'EEMI` (2 lignes pour tenir dans la liste des serveurs).
- Icône du serveur : `server-icon.png` 64×64 px à la racine (réduite depuis un original 1254×1254 par `scratchpad/scripts/Resize.java`) ; copie de l'original sur l'hébergement web (`tmp/server-icon.png`, choix de Thibault).

**Pièges**
- Spawn du monde `lobby` (`mv info lobby`) = 8 -63 8 (ancien endroit) au 28/10. Tous les plugins maison renvoient au hub par `getSpawnLocation()` → fin de partie téléportée là. Correctif : `/execute in minecraft:overworld run setworldspawn 722 51 650`, refusé à l'outil (classifieur) → à taper par Thibault ; non confirmé fait.
- Commandes vanilla tapées à la console : elles visent le monde `tntwars` par défaut → préfixer `execute in minecraft:overworld run …` pour le lobby (`minecraft:lobby` n'existe pas). Après `forceload add`, toujours `forceload remove all`.
- Commandes console qui modifient le monde (setworldspawn, gamerule) refusées à l'outil : ce qui pose des blocs au lobby passe par un plugin lancé en jeu par Thibault.
- Une commande de test `say` à la console s'affiche dans le chat des joueurs (« [Server] … ») : ne pas en faire.
- Une icône qui ne fait pas 64×64 → « Couldn't load server icon ».
- Lecteur de régions `scratchpad/tools/mca.py` : en 26.x une entrée de palette avec propriétés est `{'id': ..., 'properties': ...}`, que `section_blocks` lisait comme de l'air → lire `id`.

**À vérifier en jeu**
- Spawn du monde corrigé en 722 51 650 (fin de partie d'un jeu maison).
- Motd en 2 lignes dans la liste des serveurs.

## DeluxeHub (config, objets du lobby, boussole et sous-menus)

**État**
- DeluxeHub 3.8.6. Boussole en 7 menus + `buildbattle.yml` (8 menus chargés au 17/11). Fonds violet et or et textes de survol pas revus en jeu.

**Fonctionnement**
- Mondes désactivés : liste noire (`invert: false`, `worlds: [...]`) ; chaque nouveau monde de jeu (et ses copies `_2`, `_3`, `_modele`) doit y être ajouté, puis `deluxehub reload`.
- `join_message` / `quit_message` en français ; `join_events` : Bienvenue sur EEMICRAFT, IP eemicraft.mine.fun, Discord discord.gg/UuDGVB7Q38, « prends la moustache », titre EEMICRAFT / Bienvenue %player%. Livre « Infos du serveur » refait le 10/11 (règles, commandes, jeux du moment, Discord ; plus de « FALLEN »).
- `scoreboard.enabled: false` (module incompatible 26.3, NoSuchMethodError en boucle) ; le scoreboard du hub est fait par TAB. `announcements` plante aussi (`Player.sendMessage(Component)`) : aucune annonce affichée ; erreur AutoBroadcast toutes les minutes en console (ménage à faire). Module AntiSwear : NoClassDefFoundError PlainTextComponentSerializer à chaque message, sans effet sur le chat (à couper via `anti_swear` si on veut une console propre).
- `double_jump.enabled: true` : donne `allowFlight` au hub (EemiCore retire le vol hors du hub, voir core).
- Objets du lobby (`custom_join_items`, icônes du pack `eemicraft:lobby/*` = PAPER en `custom_model_data` 2001..2067 dans `assets/minecraft/items/paper.json`) :
  - moustache `server_selector` = PAPER `custom_model_data: 1001`, action `[MENU] serverselector` (le navigateur `/jeux` d'EemiCore a été essayé puis retiré de la moustache le 15/11 à la demande de Thibault : « remets les mini-jeux comme avant ») ;
  - « Groupe » slot 2 = PAPER cmd 2056, `[COMMAND] party menu` ;
  - « Profil et boutique » slot 6 = PAPER cmd 2050, `[COMMAND] profil` ;
  - livre « Infos du serveur » = PAPER cmd 2048 ;
  - cache-joueurs `player_hider.slot: 8` (essayé en 1 puis remis en 8 à la demande d'Esteban, 17/11) ; case 7 = gadget d'EemiCore.
- Commandes `menu*` (custom_commands de `plugins/DeluxeHub/commands.yml`, `[MENU] <sous-menu>`) : `menuiles`, `menusurvie`, `menuequipes`, `menucombat`, `menutnt`, `menuambiance`, + `menujeux` (= boussole). Utilisées par les PNJ de catégories.
- Boussole (structure actuelle, sous-menus = fichiers de `DeluxeHub/menus/`, chargés automatiquement ; Retour = `[MENU] serverselector`) :
  - Titres : `&f` + U+E300 + glyphe du fond + U+E301 + `&fMini-jeux ...` (fonds : selector E310, iles E311, survie E312, equipes E313, combat E314, tnt E315, ambiance E316, dans `minecraft:default` du pack). Les caractères réels (UTF-8) sont écrits dans les yml. Aucune vitre de remplissage (style « Game Menu » d'Hypixel voulu par Thibault).
  - `serverselector.yml` (accueil, 45 cases) : Partie au hasard 4 (`[COMMAND] jouer`), Îles 11, Survie 13, Équipes 15, Combat 29, Jeux TNT 31, Ambiance 33, Casino 22 au centre (`[COMMAND] eemi join casino`, mis là par Esteban le 10/11), Fermer 40.
  - `iles.yml` (36 cases, Retour 31) : SkyBlock 11, L'Abysse 13 (`[COMMAND] abysse`), OneBlock 15, Jump 16 (`[COMMAND] eemi join jump`), Dernier Refuge 21 (tête de zombie, `[COMMAND] refuge`), Tycoon 23 (gâteau, `[MENU] tycoon`, 26/11) ; sous-menu `tycoon.yml` (27 cases) : Resto 11 (`eemi join resto`), Fête foraine 15 (fusée, `eemi join fete`, « NOUVEAU » jusqu'au 01/12, bêta), Retour 22.
  - `survie.yml` : UHC Run 11, Speedrun 13, EEMINGO 15.
  - `equipes.yml` : Towers, Bridge, BedWars, Balls of Steel, Sheep Wars, Paintball (cases non relevées).
  - `combat.yml` : SkyWars 11, Hunger Games 13, PvP 15 (`duel`), Entraînement 22 (laine blanche), Pitchout 29, JumpLeague 31 ; case 33 libre (Block Sumo retiré le 17/11, Esteban).
  - `tnt.yml` : TNT Run, TNT Tag, Bow Spleef, PVP Run, TNT Wars, TNT Wizards, Spleef 22 (pelle en diamant).
  - `ambiance.yml` (damier d'Esteban, 10/11 ; Retour 40) : Hors Cours 4 ; Murder 10, Loup-Garou 12, Cache-cache 14, Squid Game 16 (ex « 1 2 3 Soleil ») ; Dé à coudre 20, Course en élytres 22, Infection 24 (`eemi join infection`) ; Block Party 28, Build Battle 30 (`[MENU] buildbattle`), Mario Barque 32, Dropper 34. Tonte Royale n'a pas d'entrée (retirée).
  - `buildbattle.yml` (27 cases) : Solo 11 (`eemi join buildbattle`), Duo 15 (`buildbattle join eemicraft_duo`), Retour 22.
  - Les jeux passent par `[COMMAND] eemi join <id>` (emmène le groupe, respecte maintenance et bêta), sauf menus notés. Ancienne boussole gardée : `DeluxeHub/menus/serverselector-ancien-20261101.yml.bak`.
  - Menus DeluxeMenus encore existants (hors boussole) : `gui_menus/jeux_tnt.yml` (/jeuxtnt, 45 cases : TNT Run 11 `tr join tntrun1`, TNT Tag 13, TNT Wars 15, Bow Spleef 29, PVP Run 31, TNT Wizards 33), `gui_menus/wool_games.yml` (/woolgames, Sheep Wars 11), `uhc.yml`, `eemingo.yml`, `speedrun.yml`. Un menu DeluxeMenus doit être déclaré dans `DeluxeMenus/config.yml` puis `dm reload`, sinon « Could not find menu ».

**Pièges**
- Ne jamais réécrire une config DeluxeHub en entier (elle a planté) : modifier le fichier d'origine ligne par ligne.
- `invert: true` ne marche pas → liste noire.
- Noms d'objets sans accents. Vérifier qu'il n'y a pas de doublon de .jar.
- Mention « NOUVEAU » dans un nom de la boussole (Esteban, 17/11) : 5 jours max, avec un commentaire YAML daté au-dessus (`# NOUVEAU jusqu'au JJ/MM`), à retirer à la date.
- Les custom_commands `menu*` ne sont PAS enregistrées par `deluxehub reload` (« No help for menuiles ») : il faut un redémarrage ; à la console elles donnent « Unknown command » (gérées sans doute par l'événement de commande des joueurs).
- La « boussole » qui téléportait les ops au clic droit était la baguette de navigation de FAWE : `navigation-wand.item: minecraft:structure_void` dans `worldedit-config.yml`.
- Cache-joueurs en case 8 = même case que le lit « Retour au lobby » des jeux : cause probable (non prouvée) des « joueurs cachés en revenant au lobby » (17/11) ; non corrigé.
- Laissés en anglais : messages de `DeluxeHub/messages.yml`.

**À vérifier en jeu**
- Les 8 menus (fonds violet et or, alignement des titres, textes de survol du 17/11).
- Clic sur un PNJ de catégorie / `/menuiles` après redémarrage.
- Joueurs cachés au retour au lobby (case 8).

## PNJ FancyNpcs du lobby

**État**
- FancyNpcs 2.12.1. PNJ actuels : 7 PNJ de catégories, Père Noël, Nael et Tristan. Pas revus en jeu après les déplacements.

**Fonctionnement**
- PNJ de catégories (créés le 10/11, skins de minecraftskins.com via `npc skin <npc> <url>`, tournés vers le centre de la place 670.5 652.5) — positions du 11/11 :
  - `cat_iles` 671.5 50 631.5 (explorateur) → `player_command menuiles`
  - `cat_casino` 663.5 50 635.5 (skin pseudo « croupier », remis par BanTeEs) → `eemi join casino`
  - `cat_ambiance` 659.5 50 645.5 (détective) → `menuambiance`
  - `cat_survie` 657.4 50 654.4 (survivant) → `menusurvie`
  - `cat_tnt` 657.5 50 663.5 (TNT guy) → `menutnt`
  - `cat_equipes` 663.3 50 673.4 (chevalier noir) → `menuequipes`
  - `cat_combat` 671.5 50 673.5 (guerrier Warden) → `menucombat`
  - (Thibault avait d'abord posé `cat_survie` en 685.5 639.5 et `cat_equipes` en 685.5 667.5 ; les positions d'Esteban du 11/11 sont plus récentes.)
- `pere_noel` : 676.4 49.125 650.6 (relevé du 11/11 ; d'abord posé en 679.13 49 647.47, yaw -47.1), skin « santa claus », action `player_command noel cadeau`.
- `nael` (730.51 52.5 649.55) et `tristan` (730.56 52.5 651.43), skins skin_calvitie.png / Tristan-TOUZOT.png, regard 90 (vers le spawn), sans action.
- Réglages globaux : `visibility_distance: 64` dans `FancyNpcs/config.yml` (défaut 20) et tous les PNJ à 64 ; `turnToPlayer: false` partout (Thibault ne veut pas qu'ils suivent du regard) ; tous en `pitch 0` (`npc rotate <npc> <yaw> 0`).
- Supprimés le 10/11 (Thibault) : les 19 PNJ de jeux (Ball-Of-Steel, Block-Party, Build-Battle, Cache-Cache, EEMINGO, Fallen-Kingdom, Hunger-Games, Murder, OneBlock, SkyWars, TheBridge, Towers, UHC-Run, Wool-Games, bedwars, comingsoon1 (« SKYBLOCK »), comingsoon2 (Jeux TNT), loupgarou, pvp). Esteban (06/11) : pas de PNJ pour le Casino « dans la boussole, pas dans un PNJ » — remplacé depuis par `cat_casino`.
- Commandes utiles (relevées par `npc help`) : move_here, move_to, teleport, rotate, center, scale, equipment, glowing, attribute, `visibility_distance` ; `pose` accepte standing/crouching/sitting (`dancing` refusé) ; aucune animation en boucle (d'où les plugins à Mannequin). À la console : `npc create <nom> --type PLAYER --location x y z --world <monde>` marche, `npc copy` est réservé aux joueurs ; lever un bouclier : `npc attribute <npc> set use_item off_hand` (`offhand` refusé).
- Skin servi depuis l'hébergement web en PHP qui renvoie le PNG (on ne peut pas déposer de binaire avec les outils), ex. https://eemicraft.strator.gg/skins/pvp.php.

**Pièges**
- `fancynpcs save` AVANT d'éditer npcs.yml (sinon les déplacements récents en mémoire sont perdus), puis `fancynpcs reload` ; sinon passer par les commandes `npc …`. npcs.yml est réécrit par FancyNpcs.
- npcs.yml contient aussi ~97 boutiques BedWars.
- Les PNJ FancyNpcs sont invisibles pour le serveur (pas d'entités) : un plugin qui doit les éviter (EemiHalloween/EemiNoel `keep-clear`) a besoin de leurs coordonnées en config.

**À vérifier en jeu**
- Les 7 PNJ de catégories ouvrent bien leur sous-menu (après redémarrage, voir DeluxeHub).
- Père Noël : clic → `/noel cadeau`.

## Hologrammes DecentHolograms du lobby

**État**
- 13 hologrammes au 11/11 (après `dh reload`) ; aucun revu en jeu.

**Fonctionnement**
- `regles_iles|casino|ambiance|survie|tnt|equipes|combat|perenoel` (remis le 11/11, Esteban : « remets les textes au-dessus de tous les PNJ comme avant ») : 3 lignes blanches (&f), hauteur de ligne 0.25, `down-origin: true` (la position est le bas, les lignes montent), 3 blocs au-dessus des pieds du PNJ, visibles à 16 blocs (`display-range`) ; jeux de la catégorie + « Clique pour choisir ton jeu » ; le casino affiche le jackpot (`%eemicasino_jackpot%`).
- `arbre_createurs` (fichier `holograms/arbre_createurs.yml`) : titre « &6&lArbre des créateurs » (redevenu ce nom le 10/11 après un passage en « Fontaine des créateurs ») puis une ligne par créateur « &ePseudo &8» &bRôle » ; rôles (Thibault, 28/10) : Enosik Développeur & Builder, LABaleineMC Développeur, BanTeEs Développeur & Builder, Qorwinn Buildeuse & Testeuse, LivrePoche Testeuse, MaZd_ Builder & Testeur (majuscule au 2e mot voulue). Position 656.9 76.8 669.1, à ~20 blocs des créateurs.
- `bienvenue` 730.53 53.9 650.49 (entre Nael et Tristan, baissé de 6 blocs à la demande de Thibault), visible à 48 blocs : « Bienvenue sur EEMICRAFT ! » / « Prends la moustache pour choisir un mini-jeu » / PNJ / « Bon jeu ! ».
- `vie_top_niveaux` (718.5 56 643.5), `vie_top_victoires` (718.5 56 657.5), `vie_direct` (700.5 55 652.5) : classements et fil en direct d'EemiCore (placeholders `%eemi_rank_level_<n>%`, `%eemi_toprot_*%`, `%eemi_feed_<n>%`). Copies dans `EemiVie/hologrammes/`.
- Supprimés : les 18 `regles_<jeu>` des anciens PNJ de jeux (10/11, accord de Thibault) ; les 7 `vie_cat_*` (« Aucun joueur » au-dessus des PNJ de catégories, `%eemi_cat_<cat>%`, retirés à la demande de Thibault le 11/11).
- Commandes : `dh create <nom> -l:lobby:x:y:z <texte>`, `/dh h movehere <nom>` (en jeu), `/dh h move <nom> x y z`, `dh h delete <nom>`, `dh reload`.

**Pièges**
- Avec `down-origin: false`, les lignes descendent depuis la position et tombent sur le nom du PNJ.
- Un hologramme qui utilise un placeholder d'un plugin pas encore installé doit rester `enabled: false` (sinon placeholder brut).

**À vérifier en jeu**
- Chevauchement hologramme / nom du PNJ ; place des 3 hologrammes `vie_*`.

## TAB (scoreboard du hub, noms, conditions)

**État**
- Branché ; liste des joueurs, pseudos et tableau du hub pas revus à l'écran.

**Fonctionnement**
- Tableau `lobby` dans `TAB/config.yml` (`display-condition: "%world%=lobby"`) : date, joueur, niveau + xp, pièces, titre, en ligne, victoires, discord.gg/UuDGVB7Q38, eemicraft.mine.fun (adresse DNS du serveur ; l'ancienne `eemifk.mine.fun` des tableaux BedWars a été corrigée). Footer : « IP eemicraft.mine.fun  DISCORD discord.gg/UuDGVB7Q38 ».
- `_DEFAULT_` de `TAB/groups.yml` : `tabprefix: "%eemi_team%%luckperms-prefix%%eemi_title_prefix%"`, `customtabname: "%player%"`, `tabsuffix` et `tagsuffix: "%luckperms-suffix%%condition:prenom%"`. Condition `prenom` dans `conditions` de `TAB/config.yml` : `%eeminoms_renomme%=oui` → ` &7(&f%eeminoms_nom%&7)`, sinon vide → « Pseudo (Prénom) » dans la liste et au-dessus de la tête.
- Admins (`TAB/users.yml`, choix de Thibault) : Enosik et BanTeEs `tabprefix: "%eemi_team%&c&l[Admin] &c%eemi_title_prefix%&c"`.
- `scoreboard-teams.disable-condition` : liste des mondes de jeux (et `_2`, `_3`) où les jeux posent leurs propres équipes (pseudos cachés, couleurs d'équipe) ; tout nouveau monde de jeu y va, puis `tab reload`. TAB belowname est désactivé.
- Vérifier un rendu : `tab parse <joueur> <placeholder>`.

**Pièges**
- Un placeholder dans TAB avant l'installation du plugin qui le fournit s'affiche brut : brancher APRÈS l'installation.
- Le prénom n'apparaît que là où TAB gère les noms (pas dans les mondes de la disable-condition).

**À vérifier en jeu**
- Tableau du hub, liste des joueurs « Pseudo (Prénom) », préfixe [Admin], équipe dans les jeux en équipe.

## Noms affichés (EemiNoms, /rename)

**État**
- EemiNoms 1.0.0 installé ; ouvert à tous (`lp group default permission set eeminoms.self true`, Thibault 15/11). Chat et liste pas vus à l'écran.

**Fonctionnement**
- Source `EemiNoms/`, `softdepend: [PlaceholderAPI, LuckPerms]`. Le pseudo Minecraft ne change pas (commandes, jeux, sauvegardes) : seul le nom affiché change.
- `/rename <prénom>` (alias `/prenom`, `/surnom` ; perm `eeminoms.self`, default false), `/rename reset`. Admin `eeminoms.admin` (op, donne aussi `eeminoms.self`) : `/rename <joueur> <nom>` (1er mot = joueur connu suivi d'un nom, sinon renommage de soi), `/rename reset <joueur>`, `/rename droit <joueur> [on|off]` (lance `lp user <j> permission set eeminoms.self true` / `unset`), `/rename liste`, `/rename reload`. Pour tous : `/vraipseudo <nom>` (`/realname`).
- Règles (config) : 2 à 16 caractères, lettres avec accents, espace, tiret, apostrophe ; majuscule au début de chaque mot ; mots interdits (admin, modo, staff, eemicraft...) sauf admins ; refusé si c'est le pseudo ou le nom affiché d'un autre ; 60 s entre deux /rename (pas pour les admins). Données : `plugins/EemiNoms/noms.yml` (uuid : nom, pseudo, par, date).
- Affichage : `displayName` (chat d'EemiCore : « [Niv N] [titre] Prénom », vrai pseudo au survol) et `playerListName` ; liste et tête via TAB (condition `prenom`). Placeholders `%eeminoms_nom%` (nom ou pseudo), `%eeminoms_pseudo%`, `%eeminoms_renomme%`.
- Catalogue de `/droits` : « Se renommer » (`eeminoms.self`) et « Renommer les autres » (`eeminoms.admin`).
- Renommés le 15/11 : Enosik = Thibault, BanTeEs = Esteban A, Qorwinn = Mathilde, Livrepoche = Paloma, LeChatLyon = Rémi, MaZd_ = Frecina.

**Pièges**
- Le nom au-dessus de la tête du joueur lui-même reste le pseudo Minecraft (changer le profil côté client n'est pas fait) : le prénom y est ajouté en suffixe par TAB.

**À vérifier en jeu**
- Chat avec le prénom, liste « Pseudo (Prénom) », suffixe au-dessus de la tête.

## Halloween (EemiHalloween)

**État**
- EemiHalloween 1.2.0 installé. Saison terminée : `/halloween remove` et `/halloween off` faits le 10/11 ; plugin gardé (Esteban, 16/11). Titres Halloween masqués (boutique coupée, et `hide-off-season` d'EemiCore).

**Fonctionnement**
- Source `EemiHalloween/` ; permission op. Zone `area` x 630..760, z 600..700, y 40..100 ; rien à moins de 3 blocs des points `keep-clear` (spawn DeluxeHub, PNJ, Nael/Tristan).
- `/halloween build` : décor UNIQUEMENT dans des cases d'air (citrouilles / lanternes-citrouilles, buissons morts, crânes, bougies, toiles dans coins/feuilles/plafonds/murs, lanternes des âmes ; les lanternes existantes passent en lanternes des âmes, `replace-torches` en option). Chaque bloc noté dans `plugins/EemiHalloween/placed.yml` (5e champ = état d'origine). `/halloween remove` remet tout (seulement les blocs encore là).
- `/halloween maison` (maison en ruine 9×7 autour du joueur, porte vers le regard, cimetière de 5 tombes, sorcière dedans), `/halloween sorciere` (Witch sans IA, invulnérable, clic = réplique).
- `/halloween on|off` : nuit côté client (`setPlayerTime`), cendres + fumée, 25 chauves-souris taguées invulnérables, tonnerre `strikeLightningEffect` (sans dégâts) toutes les 30-90 s, sons inquiétants 15-35 s + 15 % de Darkness 5 s, titre « Joyeux Halloween ». `status`, `reload`.
- `/halloween music on|off` (playlist `songs_halloween.yml` du jar, générée par `EemiMusique/gen_halloween.py`) ; boutique branchée sur on/off (`_commun/Saison.java`, voir Noël). `softdepend: [EemiCore, EemiMusique]`.
- Fin de saison : `/halloween remove`, `/halloween off`, `/halloween music off` ; si décoré à la main, restaurer la sauvegarde « lobby final ».

**Pièges**
- Bug corrigé : orienter un crâne avec `BlockFace.values()` (contient UP/DOWN) lève « Invalid face » ; l'exception laissait `building = true` et bloquait tous les `build` suivants sans message. Liste `SKULL_FACES` de 16 faces horizontales + try/catch qui remet `building` à false.
- `build` refuse de tourner tant qu'un ancien décor est posé : `remove` d'abord.

## Noël (EemiNoel : décor, sapin, traîneau, cadeau)

**État**
- EemiNoel 1.4.2 installée (26/11, redémarrage 11:46 sans erreur, `a_deposer_installes/`) ; `/noel status` : « 10/10 cosmétiques de saison en vente dans EemiCore » : #150, l'annonce du traîneau va à TOUS les joueurs connectés (ceux hors du lobby ont « (/lobby pour y aller) », les sons restent au lobby) ; + correctif de la boutique de saison (voir EemiCore). 1.4.1 installée le 26/11 (11:36).
- EemiNoel 1.4.0 installé (22/11). En place : ambiance activée (`/noel on`), musique de Noël (`/noel music on`), décor `/noel build`, sapin posé (`/noel sapin confirmer` en 672 49 650). Traîneau testé à la console (passage OK) mais rendu, ramassage, annonce, bilan et cadeau pas vus en jeu.

**Fonctionnement**
- Source `EemiNoel/` (modèle EemiHalloween, même zone et `keep-clear`) ; perm `eeminoel.admin` (op). Partie commune `_commun/Saison.java`.
- `/noel build` : neige 1-2 couches au sol et sur les feuilles (`chance.snow-ground` 0.45), cadeaux (laine/béton + bougie), bougies, houx (baies sucrées), petits sapins, lanternes suspendues, sucres d'orge, bonhommes de neige en blocs, tas de cadeaux, lanternes sur poteaux/toits/arbres, couronnes sur les portes ; uniquement dans l'air, noté dans `placed.yml` ; `/noel remove` remet tout (et retire le sapin).
- `/noel sapin` : sapin ~23 blocs + étoile, guirlandes, boules, 14 cadeaux, debout au centre de la fontaine (sauvegarde d'un cylindre r 7, pieds-3..+30 dans `sapin.yml`, fontaine retirée) ; `/noel sapin remove` remet la fontaine.
- `/noel maison` (chalet en épicéa 9×7, toit enneigé, cheminée, sapin, traîneau, Père Noël dedans).
- `/noel on|off` : nuit côté client (`night-time: 18000` dans le config du serveur, demande de Thibault), flocons, clochettes, 6 bonhommes de neige sans IA, titre « Joyeux Noël » ; boutique `title_n_*` jusqu'au 2027-01-02.
- Père Noël = PNJ FancyNpcs `pere_noel` ; le villageois du plugin est coupé (`santa.enabled: false`, `santa.villager: false`). `/noel perenoel` existe encore (villageois neige sans IA).
- `/noel cadeau` : 300 pièces une fois par joueur et par saison (`cadeau.saison` noel2026, `cadeau.pieces` 300, `cadeaux.yml`), interface du cadeau ; « déjà ouvert (N pièces reçues) » sinon.
- Traîneau volant (`Traineau.java`, ~90 BlockDisplay, 4 rennes dont un au nez rouge, Père Noël en blocs ou Mannequin si `traineau.skin`) : passage auto toutes les 600 s ±20 % si l'ambiance est activée et qu'un joueur est au lobby ; annonce dans le chat `traineau.annonce-secondes` (10) s avant, titre « Ho ho ho ! » + clochettes ; 12 cadeaux en BlockDisplay qui tombent, ramassés à 1,3 bloc = 1 ou 2 pièces (via `eemi give`), max 5 par joueur et par passage ; bilan par joueur à la fin. Admin `/noel traineau` (annonce puis lance, même ambiance coupée), `/noel traineau maintenant`.
- Préfixe `§8[§aNoël§8] §7` ; textes `messages.*` (défauts dans le code, absents du config du serveur). `/noel status`, `reload`.
- Listes `boutique.cosmetiques` (26/11, carte #88) : Noël = 4 titres + `hat_n_sapin`, `hat_n_bonhomme`, `trail_n_flocons`, `trail_n_clochettes`, `join_n_traineau`, `join_n_hotte` ; Halloween = 4 titres `title_h_*` + `title_citrouille`, `hat_citrouille`, `join_fantome` (section `boutique` ajoutée au config du serveur d'EemiHalloween, `jusqu-au: ""` = sans fin). Ainsi `on`/`off` pilotent TOUS les cosmétiques de la saison, quelle que soit l'année (les dates fixes 2026 du config d'EemiCore ne servent plus qu'en dehors). `/noel status` du 26/11 : « titres de Noël en vente » (ambiance activée), Halloween coupé. Piège : la liste `boutique.cosmetiques` n'est relue qu'au `/noel reload` ou au redémarrage (cause supposée, non revérifiée en jeu : le 26/11 `/noel on` ne semblait remettre que les titres avant le redémarrage de 11:17) ; le message de `/noel status` dit « titres » même quand toute la liste est en vente.
- Boutique saisonnière (`_commun/Saison.java`, Halloween et Noël) : `on|off` met en vente / retire les cosmétiques de `boutique.cosmetiques` (défauts `title_h_*`, `title_n_*`). Coupé = `available-from: 2099-01-01` + `text: ""` (titres équipés cachés, achats gardés) ; activé = `available-until` = `boutique.jusqu-au`. Ne modifie que la config EN MÉMOIRE d'EemiCore, réappliquée 1 s après le démarrage puis toutes les 10 s (un `/eemi reload` relit le fichier).
- Musique de saison : `music on` copie la playlist du jar dans `plugins/EemiMusique/songs.yml` (une playlist perso, sans 1re ligne « # Playlist », est d'abord copiée dans `songs-perso.yml`) ; `music off` renomme `songs.yml` en `songs.yml.off` ; puis `musique reload`. Playlist `songs_noel.yml` (`EemiMusique/gen_noel.py`, 8 morceaux ~8 min 30 : Douce nuit, Jingle Bells, Mon beau sapin, We Wish You a Merry Christmas, Deck the Halls, Joy to the World, 2 originaux ; de mémoire, jamais écoutée).
- Fin de saison : `/noel remove`, `/noel off`, `/noel music off`.

**Pièges**
- Le sapin occupe la fontaine où se trouvent les mannequins d'EemiCreateurs : ils n'ont pas été déplacés (`/createurs pos`).
- La neige au sol génère beaucoup de lignes dans `placed.yml` (1 848 neiges au sol + 696 sur les arbres au premier build).

**À vérifier en jeu**
- Traîneau (rendu, cadeaux, ramassage, annonce, bilan), `/noel cadeau` via le PNJ, sapin et créateurs, rendu du chalet, bonhommes de neige sans trace, playlist de Noël.

## Musique du lobby (EemiMusique)

**État**
- EemiMusique 2.0.0 installé. Playlist de Noël active (`songs.yml` posé par `/noel music on`). Jamais écoutée en jeu.

**Fonctionnement**
- Source `EemiMusique/`. Séquenceur de blocs musicaux (sons `block.note_block.*` via `Registry.SOUND_EVENT`, catégorie RECORDS = curseur « Juke-box/Blocs musicaux » du joueur), plusieurs pistes par morceau, un seul morceau commun à tout le lobby, 3 s de pause, ordre mélangé, titre dans la barre d'action ; s'arrête hors des mondes de `worlds` (`[lobby]`).
- Morceaux de base : `songs.yml` du jar (8 morceaux, générés et vérifiés par `EemiMusique/gen_songs.py`, notes F#3..F#5) : Canon en ré (Pachelbel), Gymnopédie n°1, Prélude en do (Bach), Lettre à Élise, Greensleeves, Au matin (Grieg), « Lobby EEMICRAFT » et « Nuit au lobby » (originaux). Un `songs.yml` dans le dossier du plugin remplace celui du jar.
- Commandes : `/musique [on|off]` (`/music`, choix mémorisé dans data.yml), `liste`, `info`, admin `suivant [morceau]` (pour tout le lobby), `reload`. Le config.yml du serveur garde d'anciennes clés (playlist ignorée, volume 0.6).

**Pièges**
- Thibault ne veut PAS des disques officiels (« des musiques cool comme Block Party, libres de droit ») : version à disques abandonnée, ne pas refaire.
- Un son de bloc musical ne tient pas : un « bourdon » doit être une note répétée (ex. D4:8 en boucle), pas une note longue.
- Arrangements écrits de mémoire, pas relus sur partition.

## Créateurs (EemiCreateurs)

**État**
- EemiCreateurs 1.2.0 installé ; 6 mannequins présents (vérifié à la console) ; gestes et skins pas vus à l'écran.

**Fonctionnement**
- Source `EemiCreateurs/`. Les 6 créateurs (Enosik, LABaleineMC, BanTeEs, Qorwinn, LivrePoche, MaZd_) en entités Mannequin (skin + pseudo, invulnérables, sans gravité, non sauvegardées, tag `eemicreateurs`), posés sur la fontaine (x 670..676, y 56..61, z 647..653). Ils remplacent les anciens PNJ FancyNpcs `skin_*` (supprimés).
- Seul geste tiré (Thibault) : s'accroupir/se relever (4 fois, toutes les 3 à 8 s), seulement si un joueur est à moins de 40 blocs. Les autres gestes (coucou, saut, course `run-distance`, recul…) restent dans le code mais ne sont plus tirés.
- `/createurs pos <pseudo>` (en jeu), `/createurs reload|pause`. Vérif console : `execute in minecraft:overworld if entity @e[type=minecraft:mannequin,tag=eemicreateurs]` (chunks forceload le temps du test).

**Pièges**
- `setInvulnerable` ne protège pas des joueurs en créatif : tout EntityDamageEvent annulé en HIGHEST (sans ignoreCancelled) + EntityCombustEvent.
- Pose accroupie : `setPose(SNEAKING, true)`, `Mannequin.validPoses()` testé avant.

## Ring (EemiRing) — retiré

- Supprimé (jar + dossier) le 16/11 (Esteban). Il animait deux Mannequin combattants à l'ancien lobby (puis déplacés vers 667 51 616). Source `EemiRing/` gardée ; encore dans la sauvegarde « lobby final ».
- Leçons à garder : `EntityEquipment.setDropChance` lève « Cannot set drop chance for non-Mob entity » sur un Mannequin ; WorldGuard annule l'apparition des entités de plugins au lobby (rétablir en CreatureSpawnEvent HIGHEST) ; API Mannequin vérifiée au javap : `setProfile` (skin), `swingMainHand`, `playHurtAnimation`, `startUsingItem(OFF_HAND)`.

## Lobby vivant (EemiVie : animaux)

**État**
- EemiVie 1.0.0 installé ; ne fait plus que les animaux (14 : 6 chats, 8 perroquets ; aucun poisson). Pas vu à l'écran.

**Fonctionnement**
- Source `EemiVie/`, monde `lobby` seulement, `/vie status|reload|scan|pause`, perm `eemivie.admin` (op).
- Animaux : fouille de la zone `animaux.scan` (config du serveur : x 630..760, z 600..705, y 40..90 ; chunks chargés en asynchrone) → toits (bloc au-dessus ≥ y 56), cimes d'arbres, eau profonde de 2 ; points à 7 blocs d'écart gardés dans `plugins/EemiVie/spots.yml` (`/vie scan` pour refaire). Chats couchés/assis et perroquets sans IA, poissons tropicaux qui nagent ; sons, clic = ronron / note ; invulnérables, non sauvegardés, recréés toutes les 2 s si le chunk est chargé ; apparition rétablie malgré WorldGuard.
- Passants retirés (Thibault, 11/11 : « c'est terrifiant ») : `passants.enabled: false`. Ne pas remettre de mannequins qui marchent.
- Assise sur escaliers/dalles retirée (Thibault, 11/11) : `assise.enabled: false`.

**Pièges**
- Aucun poisson : pas d'eau profonde de 2 dans la zone (la fontaine est sous le sapin).

## Stand de tir (EemiTir)

**État**
- EemiTir 1.0.0 installé ; zones PAS réglées (« Zones : tir A REGLER, cibles A REGLER ») donc inactif ; rien testé.

**Fonctionnement**
- Source `EemiTir/`, monde `lobby` (`world` du config).
- Admin (`eemitir.admin`, op), en jeu : `/tir zone <tir|cibles|interdite> <pos1|pos2|effacer>` (position des pieds, sauvée par `saveConfig`), `/tir voir` (bords en particules 20 s : vert tir, orange cibles, rouge interdite), `/tir info`, `/tir reload`, `/tir reset confirmer` (records). Zone interdite vide = zone des cibles + `marge-interdite` (1). Tant que `tir` et `cibles` valent « 0 0 0 », rien ne se passe.
- Jeu : entrer dans la zone de tir = « Arc du stand de tir » (Infinité, incassable) case 1 + flèche case 9 (objets d'avant rendus en sortant ; PDC `eemitir:objet`, ni jetables ni déplaçables, retirés à la connexion/changement de monde/arrêt). Décompte 3 s, manche 45 s (barre de boss), pause 5 s puis nouvelle manche ; sortir = arrêter. Cibles = vrais blocs posés dans l'air de la zone (`setType` sans physique) : TARGET +1 (70 %), GOLD_BLOCK +3 (15 %, vit moins longtemps), TNT -2 (15 %) ; 4 max, une tentée toutes les 15 ticks, 2,5 blocs d'écart, durée par type (`cibles.types`). Cibles partagées, touche comptée au tireur. Flèches sans ramassage ni dégâts. Blocs notés dans `cibles-posees.yml` (retirés au démarrage après un arrêt brutal). Zone interdite : joueur remis où il était + recul.
- Fin de manche : titre, résumé, record par joueur (`records.yml`), annonce si nouveau n°1 ; `/tir top`. PlaceholderAPI : `%eemitir_top_<n>%`, `%eemitir_best%`, `%eemitir_rank%` (pour un hologramme de classement).

**À vérifier en jeu**
- Régler les zones ; inventaire rendu en se téléportant vers un autre monde depuis le stand (PlayerTeleportEvent MONITOR, avant MV-Inventories en principe) ; case 1 libre au lobby (DeluxeHub 0/2/4/6/8, gadget 7).

## Feux d'artifice du lobby (EemiFeux)

- `EemiFeux.jar` installé et utilisé (feux d'artifice du lobby, `actif: true`, constat d'Esteban le 16/11). Aucun autre détail connu (source et réglages non relevés).

## Lunar Client (Apollo)

**État**
- `apollo-bukkit-1.2.9.jar` dans /plugins ; rien réglé (config créée au premier démarrage).

**Fonctionnement**
- Demande de Thibault (« un module Lunar ») : on ne peut pas créer de mod Lunar ; Apollo est le plugin officiel côté serveur (repères, coéquipiers, notifications, recharges, textes sur les pseudos, statut Discord, désactiver des mods Lunar par monde). Ne touche que les joueurs sur Lunar. Source Modrinth (version OhLDr7Mj).

**Pièges**
- Sur Modrinth seule la variante Folia de la 1.2.9 est marquée 26.3 ; la variante Bukkit l'est jusqu'à 26.2 (même version) : compatibilité 26.3 non vérifiée. `apollo-folia-1.2.9.jar` installé par erreur au départ, renommé `.jar.old`.

**À vérifier en jeu**
- Chargement sans erreur au démarrage, puis réglages.

# Jeux d'équipe et de combat

## Jeux d'équipe et de combat : socle commun

- **État** : les jeux maison de ce groupe tournent tous en parties parallèles (refonte du 29/10/2026) ; aucune partie 2 n'avait été vue jouée au dernier relevé.
- **Fonctionnement** :
  - Utilitaire `_commun/WorldClone.java` (copié dans chaque plugin) : partie 1 = monde d'origine, partie n = copie `<monde>_n` faite depuis `<monde>_modele` (copie propre faite au démarrage), détruite quand elle ne sert plus ; copie aussi `plugins/WorldGuard/worlds/<monde>`. Consigne : `_commun/BRIEF_MULTIPARTIE.md`. UHC Run ne copie pas : chaque partie génère `uhc_n`.
  - Commun : clé `max-instances` (3), `/<jeu> parties` (admin), `start|stop [n]`, `spectate [joueur]`. On ne s'inscrit plus « pour la suivante » : nouvelle partie si possible, sinon « Toutes les parties sont pleines ». Statut EemiCore agrégé (plus d'état `starting`, donc plus d'annonce « La partie démarre »).
  - Contrat EemiCore : `eemi status <jeu> ...` et `eemi reward <pseudo> <jeu> <play|win|kill>` ; spectateurs à part des inscrits (SPECTATOR, renvoyés au hub en fin de partie) ; un `/stop` admin ne donne aucune récompense.
  - Chaque monde de jeu (et ses `_2`, `_3`, `_modele`) doit être déclaré : mondes désactivés de DeluxeHub, disable-condition `scoreboard-teams` de TAB, `disabled_worlds` d'AuraSkills (sinon mana/XP de compétence du SkyBlock s'affichent en jeu), EemiCore `games.<id>.worlds`, OCM `old` + EemiBlocage `worlds` pour les jeux PvP 1.8, commandes de join dans les `blocked-commands` d'EemiAdmin (vanish).
  - `%eemi_team%` (EemiCore) affiche dans la liste TAB le préfixe d'équipe posé par les jeux en équipe (Sheep Wars, Towers, Bridge, Balls of Steel, UHC...) sur le scoreboard du joueur.
  - Charte des préfixes de chat (passe UX du 10/11) : Équipes `§9` (Towers, Bridge, BoS, Sheep Wars), Combat `§c` (SkyWars, HG, Pitchout, JumpLeague), Survie `§a` (UHC Run). Titres courts (titre ≤ 16, sous-titre ≤ 38, le reste dans le chat). Égalité : « Égalité ! » + pseudos ou « N gagnants ».
  - Boussole (fait foi au 17/11, Esteban) : `combat.yml` SkyWars 11, Hunger Games 13, PvP 15, Entraînement 22, Pitchout 29, JumpLeague 31 ; `equipes.yml` Towers, Bridge, BedWars, Balls of Steel 29, Sheep Wars 31, Paintball 33 ; `survie.yml` UHC Run ; `ambiance.yml` Squid Game (entrée `soleil`).
- **Pièges** :
  - `_modele` refusé par Paper 26.3 (« duplicate of another world ») si `data/paper/metadata.dat` est recopié : `WorldClone` l'ignore à la copie. Fichiers d'un monde 26.x : `region/ entities/ poi/ data/minecraft/*.dat data/paper/{metadata,level_overrides,persistent_data_container}.dat paper-world.yml`.
  - Inventaires (consigne `_commun/BRIEF_INVENTAIRE.md`) : ne toucher à l'inventaire que dans le monde du jeu ou au hub (`mayTouch`) ; sur `PlayerChangedWorldEvent`, ne rien vider ; téléporter AVANT de vider. Bugs réels trouvés le 29/10 : Pitchout et UHC (`dropAndEliminate`) vidaient dans le monde d'arrivée, `spectate` vidait avant de téléporter (Bridge, Pitchout, UHC), `sendToHub` d'un inscrit resté au skyblock (SkyWars, HG), HG `leave` lâchait au sol l'inventaire du monde d'arrivée (duplication). MV-Inv 5.3.6 traite `PlayerChangedWorldEvent` en LOW (avant les jeux) ; DeluxeHub redonne ses objets 5 ticks après l'arrivée.
  - /lobby (tout changement de monde) pendant une partie = le joueur quitte la partie (corrigé dans Sheep Wars, Towers... : avant, le test du vide tuait au hub et renvoyait dans le jeu).
  - Mémoire : chaque modèle/copie chargé coûte (94 % de 4 Go vu au démarrage à l'époque ; RAM passée depuis à 8 Go). Les modèles HG font ~430 Mo, recopiés à chaque démarrage.
- **À vérifier en jeu** : une partie 2 en parallèle sur chaque jeu. « Retour au salon d'attente en fin de partie » (demande d'Esteban, 17/11) fait seulement pour Sheep Wars, Paintball et Squid Game : PAS fait pour SkyWars, HG, Bridge, Towers, BoS, Pitchout, UHC, JumpLeague (sources chez Thibault) ni BedWars (jeu tiers).

## BedWars (Screaming BedWars 0.2.44 + SBA 1.5.21)

- **État** : 21 arènes (7 maps × 3 modes) depuis le 29/10/2026 ; Noël solo validé en jeu par Esteban, coffres et côtés vus par Esteban le 03/11 ; le reste pas joué depuis la rotation des îles. Correctif « entity_position_sync » à confirmer.
- **Fonctionnement** :
  - Modes Solo / Duo / Équipes de 4 (29/10, snapshot `avant-bedwars-modes-20261029`). Arène bw1 et son monde SUPPRIMÉS. Chaque map existe dans les 3 modes ; la version hors format d'origine est une COPIE : monde par `mv clone bwN bwN_<mode>` (pas de région WG sur les mondes bw), arène = fichier d'origine copié sous un nouvel UUID avec seulement `name`, `world`/`lobbySpawnWorld`, `maxPlayers` changés.
  - Arènes : Solo `halloween_solo` (bw2_solo), `noel_solo` (bw3_solo), `emberforge_solo` (bw4_solo), `sakura_solo` (bw5_solo), `lagoon_solo` (bw6_solo), `playground_solo` (bw7_solo), `inca_solo` (bw8_solo) ; Duo `halloween` (bw2), `noel_duo` (bw3_duo), `emberforge_duo` (bw4_duo), `sakura` (bw5), `lagoon` (bw6), `playground` (bw7), `inca` (bw8) ; Équipes de 4 `halloween_4` (bw2_4), `noel` (bw3), `emberforge` (bw4), `sakura_4` (bw5_4), `lagoon_4` (bw6_4), `playground_4` (bw7_4), `inca_4` (bw8_4). Maps Frostvale/Lagoon/Sakura/Emberforge : LucasTHCR (Planet Minecraft).
  - Fichiers d'arène modifiés lors de la rotation : noel_solo 265756cd, noel_duo 982098e2, noel 8a7f9da8, emberforge_solo ffcd0c27, emberforge_duo 94d2bcd8, emberforge 10d97aaa, sakura_solo 5792b911, sakura 618b778a, sakura_4 53f1c0d7, lagoon_solo f439b175, lagoon 6412c132, lagoon_4 94ddb89f.
  - Entrée : menus DeluxeMenus `bedwars` (/bwmenu : Solo 11, Duo 13, Équipes de 4 15) → `bedwars_solo|bedwars_duo|bedwars_4` (maps en 10..16, `bw join <arène>`, joueurs via `%bedwars_game_<arène>_players%/_maxplayers%`, Retour 31). EemiCore `games.bedwars.join` = `console:dm open bedwars %player%` (boussole, /jouer). Plus de `bw autojoin` (remplacé le 29/10 ; `default-permissions.autojoin: true` reste). Un PNJ FancyNpcs se modifie à la console (`npc action ...`), pas dans npcs.yml (réécrit par FancyNpcs).
  - Boutiques SBA : `store add Store shop.yml false` et `store add TeamUpgrades upgradeShop.yml false`. Config SBA sans entités paquets (floating-generator, npc, replace-stores-with-npc, editing-hologram, lobby-scoreboard à false) contre le kick « entity_position_sync ».
  - Réglages : TNT `specials.auto-igniteable-tnt` → `damage: 3.0` (puissance d'explosion `setYield`, donc rayon et casse réduits aussi) et `damage-placer: false` (vérifié dans le source ver/0.2.x, `AutoIgniteableTNT*`) ; amélioration de forge à 4 diamants (`upgradeShop.yml`) ; recul TNT/boules de feu `SBA/sbaconfig.yml` `tnt-fireball-jumping.launch-multiplier` 1.2 (Thibault, 01/11 : « réduis les KB sur toutes les maps » ; si trop fort encore : `acceleration-y` 0.1, `reduce-y` 2.0) ; `allowed-commands` + /lobby, /hub, /p, /pc, /groupe (le spectateur ne pouvait pas faire /lobby) ; `specials.teamchest.turn-all-enderchests-to-teamchests: true` (coffres de l'End = coffres d'équipe partagés) ; `preventArenaFromGriefing: true`.
  - TAB : `per-world-playerlist` activé, groupe `general` = `regex:^(?!bw[0-9]).*`, chaque monde bw* isolé, `ignore-effect-in-worlds: []` (le lobby ne voit plus les joueurs en BedWars) ; équipe devant le pseudo par la condition `bw_equipe` (`%bedwars_current_team%!=none` → `[%bedwars_current_team_colored%]`) en tête des `tabprefix` de `_DEFAULT_` et des admins ; tableau `bedwars8` détecté par la condition texte `bw_8equipes` (OR =1/=2/=4). Adresse des tableaux : `eemicraft.mine.fun`.
  - Coffres et coffres de l'End des bases : Playground (bw7*) et Inca (bw8*) d'origine ; Halloween, Noël, Emberforge, Sakura, Lagoon posés à la main par Esteban dans les versions solo, recopiés dans les 10 autres mondes. Côtés (vus depuis le spawn vers le mid, Esteban) : coffre et boutique à gauche, coffre de l'End et améliorations à droite. Sakura/Lagoon : coffres entre les piliers de l'entrée et le générateur, face au générateur (Rouge Sakura coffre -28 65 -92 / EC -32 65 -92, Lagoon z -93).
  - Îles des bases tournées de 180° (03/11, Esteban : lit derrière, générateurs devant) sur Noël, Emberforge, Sakura, Lagoon (12 mondes) ; mid, diamants, émeraudes inchangés ; spawns tournés face au lit et au mid (z− yaw 0, x+ 90, z+ 180, x− -90). Noël/Emberforge : boîtes x -23..23 / z -99..-62 (et symétriques), lits ±73, spawns ±81,5, générateurs ±88,5, villageois ±83,5. Sakura (8 bases en (±30, ±95)/(±95, ±30), lits y 66) : boîtes 31×29, lits ±79, spawns ±87,5, générateurs ±92,5, villageois ±88,5/±89,5. Lagoon (lits y 65, îles asymétriques) : boîtes 33×30, lits ±80, spawns ±88,5, générateurs ±93,5, villageois ±89,5/±90,5.
  - Datapack `lobby/datapacks/eemicraft` : `eemicraft:bwcoffres/copier_halloween|quatre|huit {src:"<solo>",dst:"<monde>"}` (copie des coffres depuis la version solo vers une nouvelle copie de map ; aussi `retirer_*`), `eemicraft:bwrot/*` (`charger {w}`, `save`, `place`, `annuler`, `coquille`, `verif_*`, `quatre|huit|lagoon`, `echange_monde`, `coffres_avant`, `orienter`, `scan`).
- **Pièges** :
  - Screaming BedWars vide l'inventaire AVANT de téléporter : un groupe EemiCore qui suit son chef dans `bw join` depuis le OneBlock/SkyBlock fait perdre le stuff (MV-Inv enregistre le profil vide). Les jeux maison, eux, téléportent puis vident.
  - `preventArenaFromGriefing: true` annule poses et casses dans les arènes même hors partie (« le bloc se retire ») : le passer à false + `bw reload` le temps d'un build, puis le remettre. Ne pas faire `bw reload` pendant des parties (il les casse).
  - Valeurs de TNT figées à l'achat : seules les TNT achetées après reload/redémarrage changent. `shop.yml` : une entrée avec nom/lore différente de celle de l'accueil donne des objets non empilables (laine corrigée).
  - Rotation d'îles par structures : gardées en MÉMOIRE seulement (annulation impossible après redémarrage) ; un `minecraft:reload` entre `save` et `place` VIDE les structures (incident du 03/11, réparé depuis bw3) ; `place template` exige le chunk de (pos + taille) chargé ; vider la boîte avec `fill … air strict` avant de coller (sinon bannières, lits, tapis tombent en objets) ; `if blocks` ne sert pas à comparer une île à sa copie recollée (états recalculés).
  - Commande `>=1` en condition numérique TAB hors partie = spam « numeric condition » : utiliser une condition texte.
  - Réglage SBA `tnt-fireball-jumping.source-damage` : effet non vérifié (source introuvable) ; sens exact de `launch-multiplier` non relu.
- **À vérifier en jeu** : les 21 arènes après rotation (seul Noël solo joué), coffres de Sakura/Lagoon face au générateur, kick « entity_position_sync » disparu. Archive inutile `/lobby/dimensions/minecraft/bw-regions-scan.zip` (15 Mo) encore sur le serveur.

## The Towers (EemiTowers)

- **État** : 1.4.3 installée ; pas encore testé en jeu.
- **Fonctionnement** :
  - Source `EemiTowers/`. Map choisie par Thibault : CurseForge « The Towers Remastered » (CrossFace22, d'après Quijx), v2.0.2 pour 26.3, monde `thetowers` (dossier 26.x remis à plat puis `mv import`). Son datapack n'est PAS utilisé (il agirait sur tout le serveur) : le plugin reprend règles et coordonnées.
  - Coordonnées (datapack) : lobby de la map 0,64,1024 (salle en quartz) ; spawn Rouge 84,192,1152 (yaw 90), Bleu -84,192,1152 (yaw -90) ; bassins au-dessus des spawns (x ±83..±85, y 200..202, z 1151..1153), l'équipe adverse marque en y sautant ; générateurs lapis 0,206,1166 et fer 0,206,1138 ; zone de jeu -103,0,1122 → 104,268,1182 ; vide sous y 160.
  - Remise à zéro après chaque partie : 8 `clone` de la copie de sauvegarde (-13,0,36 → 194,268,96) vers la zone de jeu (`/towers reset` à la main) ; gamerule `max_block_modifications` à 1 000 000. Le plugin retire aussi les entités de la zone (objets, projectiles, véhicules, mobs, TNT, blocs qui tombent, XP) au début de partie et après la remise à zéro.
  - Jeu : Rouge contre Bleu, 10 points ou 25 min, construction libre dans la zone (sauf spawns et bassins), kit de réapparition du datapack (cuir teint, patates) + 3 stacks de verre de l'équipe, 4 niveaux d'XP par kill, mort vanilla (objets non-kit au sol), scoreboard latéral. Commande `towers join`. PvP 1.8 (OCM old).
  - TAB `scoreboard-teams.disable-condition` doit inclure `thetowers` (pseudos colorés).
- **Pièges** :
  - Gamerules par monde en 26.3 : la map importée avait `spawn_monsters` true → false dans thetowers + WG `__global__` mob-spawning deny + le plugin annule les apparitions naturelles. Le `clone` ne restaure que les blocs (d'où le retrait des entités).
  - Lobby de la map : 16 text_display et 9 entités interaction du menu du datapack supprimés (`kill` avec forceload : sans chunks chargés, le sélecteur ne trouve rien). Les 165 chunks forceload de la map retirés.
- **À vérifier en jeu** : partie complète. Ancien monde généré `towers` inutilisé : à supprimer avec l'accord de Thibault.

## The Bridge (EemiBridge)

- **1.6.0 installée le 27/11** (redémarrage 09:26 sans erreur, `a_deposer_installes/`, demande d'Esteban : « on réactive le build » pour monter un pont jusqu'au portail adverse) : zone de départ protégée réduite à 3 blocs autour du point de départ (`spawn-protection: -1` = 3 ; avant, jusqu'au bord de l'île, ce qui couvrait l'avant du but adverse) et `enemy-goal-radius` 0 (fait aussi dans le config du serveur le 27/11) ; le trou du but reste interdit. Nouvelle règle anti-muraille : une équipe ne peut pas avoir plus de `defense-max-blocks` (24) blocs posés à moins de `defense-radius` (10) blocs de SON but (message « Pas de muraille »), clés ajoutées au config du serveur.
- **État** : 1.5.2 installée (22/11) ; pas jouée.
- **Fonctionnement** :
  - Source `EemiBridge/`, monde `thebridge` créé par le plugin (vide, map construite au démarrage), `/bridge` (alias `/tbridge`), admin `start|stop|build|reload`. Salle d'attente 0.5 141 0.5.
  - Jeu : Rouge contre Bleu 1v1 à 4v4 (2-8), îles centrées x ±30, buts 3×3 en x ±35 (but adverse = 1 point, le sien = mort), cages 5 s à chaque manche, 5 buts ou 15 min, kit Hypixel (épée fer, arc + 1 flèche rendue en 3,5 s, pioche diamant, 128 terracotta, 8 pommes dorées, cuir teint), vide sous y 84 = retour base (kill crédité), réapparition après `respawn-delay` 3 s (spectateur 8 blocs au-dessus de sa base, annulé par une nouvelle manche).
  - Pont (Thibault, capture Hypixel du 28/10 : « 1 bloc de large et 10 de haut ») : une ligne d'un bloc (`bridge-half-width: 0`) entre les îles, sans plateforme au milieu, posée sur un mur plein (`bridge-depth: 10`, y 91..100) en terracotta rouge côté Rouge, bleue côté Bleu, blanche en x 0. Construction autorisée y 88..108, pas près des buts.
  - Casse : on casse les blocs posés et, depuis la 1.5.0 (#55), le pont + mur entre les îles (x -19..19, z 0, y 91..100 ; îles, buts, cages intouchables ; terracotta rendue, rien au sol) ; blocs cassés notés par partie (`broken`), remis en fin de partie, au début de partie et à l'arrêt, pas entre les manches ; `breakable-middle` (défaut true).
  - Zone de départ (#87, 1.5.2) : pose interdite dans un cylindre autour du spawn de chaque équipe (x ±27, z 0), toutes hauteurs, rayon `spawn-protection` (-1 = auto = 8 avec la map de base) ; effet de bord : l'avant de son propre but n'est plus bloquable.
  - `no-critical-hits` : coup d'épée critique (ou attaquant qui tombe) divisé par 1,5 en HIGHEST pour annuler le ×1,5 d'OCM `old-critical-hits`. PvP 1.8 (OCM old), `thebridge` dans EemiBlocage.
- **Pièges** : la 1.1.0 avait mal compris « 10 de haut » (hauteur de construction y 95..104) : remise à 88..108, ne pas refaire.
- **À vérifier en jeu** : ordre de `no-critical-hits` par rapport à OCM ; casse du pont et remise en fin de partie ; zone de départ ; WorldGuard dans `thebridge*` non vérifié.

## Balls of Steel (EemiBallsOfSteel)

- **État** : 1.2.3 installée ; pas joué au dernier relevé.
- **Fonctionnement** :
  - Monde `ballsofsteel` importé (map Planet Minecraft « 1.16.5 Balls of Steel beta », page inaccessible aux outils ; zip source `Téléchargements/Balls-of-Steel.zip`, extrait dans `/maps-sources/ballsofsteel`). Datapacks de la map (armor statues, villager shops, cutclean) sans logique de jeu.
  - Map : arène centrée 1000,1000 ; 4 bases octaèdres de quartz (y 113..150) Rouge 900/900, Jaune 1100/900, Vert 1100/1100, Bleu 900/1100, spawn au sol y 128 (ex. 904.5 128 904.5) ; dépôts = dalles 3×3 en y 128 (Rouge 991..993/991..993, Jaune 1007..1009/991..993, Bleu 991..993/1007..1009, Vert 1007..1009/1007..1009) ; générateur de flèches (901.5 128 901.5 etc.) ; 46 boules (21 714 minerais de diamant) ; centre (disque de quartz, y 108..150) avec marchand en 1000.5 130 1000.5 ; lobby de la map -36 48 -2 (spawn du monde).
  - Bordure : centre 1000.5 1000.5, `border-size: 226` (couvre les bases entières). Seul le disque de la plateforme centrale (`platform-radius` 21.5, y 108..150) est intouchable ; autour on construit (ponts).
  - Jeu (règles de Thibault, 27/10) : jusqu'à 4 équipes (Rouge, Vert, Jaune, Bleu), 30 min, scores cachés après 20 min, kit de la map (épée pierre, arc, pioche fer, hache et pelle pierre incassables, plastron d'équipe, 16 terre infinie en main gauche toujours complétée, seau d'eau slot 5 qui tombe à la mort), kit redonné 1 s après la réapparition si absent, 4 flèches/s au générateur (max 64), regen seulement dans sa base, faim coupée, pas de regen naturelle, +1 niveau d'XP à l'équipe par kill, faiblesse en tenant une hache, cut clean (fer/or en lingots, drop normal au sol), bases ennemies incassables (coffres pillables), marchand recréé avec les 8 échanges d'origine. Tout bloc modifié est mémorisé et remis en fin de partie, coffres des bases vidés. `/ballsofsteel` (`/bos`), admin `start|stop|reset|reload`. PvP 1.8 (OCM old).
- **Pièges** :
  - `arena-center` est x,z (bug 1.0.0 : lu x,y,z → arène hors bordure).
  - Les 250 blocs de commande de la map (sous le lobby, x -42..56 y 6..15 z -25..16) ont été vidés : ils visaient @a et auraient touché tout le serveur. Panneaux du lobby (équipes, START, reset, `/team join`) bloqués sauf en créatif.
  - Ramassage direct dans l'inventaire (1.0.2) abandonné, jugé bizarre par Thibault : drop normal. En créatif rien ne tombe (les tests des ops en créatif sont trompeurs).
  - Le dossier `/maps-sources/frostvale` est une map BedWars, pas BoS.
- **À vérifier en jeu** : partie complète.

## Sheep Wars (EemiSheepWars)

- **4.7.2 installée le 27/11** (redémarrage 09:26 sans erreur, `a_deposer_installes/`) : bouton Prêt, anti-rebond de 350 ms dans `toggleReady` (carte #157 « on clique une fois, ça met prêt puis plus prêt » : un clic sur un bloc envoie deux événements, bloc puis objet).
- **4.7.0 installée** le 26/11 (`a_deposer_installes/EemiSheepWars-4.7.0.jar`, carte #134), redémarrage 10:23 sans erreur : objet « Prêt » (colorant gris, case 4 de la salle d'attente, PDC `eemisheepwars:ready`, seul objet protégé contre les clics d'inventaire), tous prêts pendant le décompte → `ready-skip-to` (5). Les 9 `.swm` de `src/main/resources/maps/` sont indispensables au build (jar de 80 Ko sans map sinon).
- **Avant** : 4.6.0 (17/11, en `.jar.old`) ; Esteban a vu les nouvelles maps : « parfait comme ça ». Non vérifié : camping dans les souterrains, éclairage des nouvelles maps.
- **Fonctionnement** :
  - Monde `sheepwars` (vide, créé par le plugin ; spawn du monde 0 141 0), salle d'attente y 140. `/sheepwars` (`/sw`) `join|leave`, admin `start|stop|build|reload|give <type>|map [id]` (`map <id>` impose la prochaine, reconstruite tout de suite hors partie). Fin de partie : les joueurs restent en file (retour au salon) ; `/sheepwars stop` garde aussi en file. Wool Games : menu DeluxeMenus `gui_menus/wool_games.yml` (/woolgames), Sheep Wars seul (slot 11).
  - Règles (refonte d'après Hypixel, wiki hypixel.fandom.com) : Rouge contre Bleu, une vie (mort → spectateur), dernière équipe en vie, 10 min max puis le plus de vivants. Kit : épée pierre, arc Infinity, laine d'équipe, pomme dorée ; Haste III ; pas de faim ; rien ne tombe au sol dans le monde. Cages de départ 5 s, AUCUN mouton au départ (`start-sheep: 0` dans le config du serveur), puis un mouton toutes les 12/10/8/6 s selon les vivants de l'équipe (+4 laine, +2 flèches), minuteur arrêté pendant la cage. Clic gauche = tir droit sans gravité, clic droit = tir en cloche (explosifs +25 %). Moutons à 20 PV (`sheep-health`) ; explosif, très explosif et chercheur explosent à l'impact, les autres attendent le sol (`isOnGround`, 5 s max). Tuer un mouton lancé à l'épée/arc le rend au tueur (pas en explosion). Laine magique au-dessus du vide toutes les 30 s (bonus selon la couleur).
  - Moutons (poids dans `weights`) : explosif, très explosif, incendiaire, glace, aveuglant, trou noir, séisme, soigneur, chercheur, foudroyant, intergalactique (8 boules de feu), poison (nuage 6 s), ender (téléporte les ennemis proches, jamais dans l'eau ni sous la hauteur de mort), abordage (le lanceur voyage sur le mouton), échangeur (échange avec l'ennemi le plus proche). Rinaorc/Gaspezia ont inspiré les 5 derniers (effets interprétés). Têtes 3D via `item_model eemicraft:sheep_<type>` (pack) ; sans pack, laine de la couleur.
  - Maps (config du serveur `maps: [rose, vert, vaisseau, pirates, ruines, spatiale]`, tirage au hasard à chaque reconstruction, jamais deux fois de suite, annoncée) : **Désert rose**, **Îles vertes**, **Vaisseau** (extraites du monde 1.8 `Téléchargements/SheepWars.zip` fourni par Thibault, en `src/main/resources/maps/<id>.swm`, format gzip « SWM1 », ids 1.8 convertis au chargement ; Vaisseau : l'eau tue) ; **Port pirate** `pirates` (la mer tue), **Ruines antiques** `ruines`, **Base spatiale** `spatiale` (format « SWM2 », générées par code hors jar : `EemiSheepWars/outil-test/src/.../gen/`, `gen.ps1` avec audit automatique ; ~+21 à +26 % de surface vs maps 1.8, vide ~19 blocs, pas d'île centrale, 12 places par camp, miroir ; souterrains et tours à +13 depuis la 4.6.0). Analyse : `outil-test/analyse_maps.md`. Vues `.claude/skills/builder-minecraft/out/sw_<id>_*`.
  - Traversée : on ne franchit pas la ligne x = 0 à pied vers le camp adverse (seul le mouton d'abordage), SAUF sur les maps de `free-crossing-maps` (`[rose, vert, vaisseau]`, demande d'Esteban). Construction seulement dans son camp à 2 blocs de la séparation (`x * dir >= 2`) et dans la zone de la map. Barrières et bedrock fixes (`isFixed`) ; plus aucune barrière posée (Thibault, 28/10 : murs invisibles retirés, l'îlot central des maps 1.8 est accessible).
  - Pose : bas de la map en center-y - 30 (70), centre en x/z 0 ; boîte vidée x ±60, z ±58, y cy-35..cy+38. Par map (record `Imported`) : places de départ, hauteur de mort, laine magique à 10-14 blocs du centre en z, point des spectateurs. `void-y` du config n'est plus lu. Désert rose tourné de 180° (Rouge à l'ouest).
  - Règles des futures maps (Esteban, 16/11) : PAS d'île centrale, aucun moyen simple de rejoindre l'autre camp sauf le mouton d'abordage, taille des maps actuelles.
  - Retirées du tirage (ne pas remettre) : Pics gelés `glacier`, Temple de jade `jade` (Thibault 15/11 : « elles sont nulles »), Cité des engrenages `engrenages` (`EemiSheepWars/gen_steampunk.py`) ; trop grandes, île centrale, engrenages traversable à pied, dirigeables qui bloquent les tirs. Leur code reste dans le jar (`/sheepwars map glacier` les construit encore). Les maps procédurales (ferme, hiver, désert, château, cerisiers, nether, puis tenozia, abbaye, forteresse, aquarium, pirates de la 3.0.0) sont supprimées du code. Recopier une map Hypixel refusé par Thibault (travail de leurs builders).
- **Pièges** :
  - Deux sources : `EemiSheepWars/` sur le PC d'Esteban est DÉCOMPILÉE depuis la 4.4.0 (jar d'origine dans `_origine/`) ; prévenir Thibault avant de modifier.
  - Paper réécrit `Material.values()` des plugins (Commodore) sans les LEGACY_ : table id 1.8 → matériau vide (bug 4.0.0). Utiliser `Material.class.getEnumConstants()` ; conversion par `Bukkit.getUnsafe().fromLegacy`, log « N ids de blocs 1.8 connus » et « Initializing Legacy Material Support » (normal). Surveiller « Bloc 1.8 inconnu » / « Conversion impossible ».
  - `maps` absent du config du serveur = défaut du JAR (pas « toutes ») : Pics gelés et Temple de jade n'étaient jamais tirés pour cette raison. Garder la clé dans le config du serveur.
  - `sheepwars reload` est refusé pendant une partie.
  - EemiSheepWars 2.0.0 appelait `setDropItems(false)` sur tous les blocs du serveur (rien ne tombait ailleurs) : limité à son monde depuis.
  - Mouton échangeur : abus connu (sauter dans le vide puis échanger) ; correctif écrit puis ANNULÉ à la demande de Thibault (09/11 : « on garde »). Pour le couper : `weights.echangeur: 0` + `sheepwars reload` hors partie.
  - Toujours renommer l'ancien jar AVANT de redémarrer, en deux appels séparés (incident : serveur bloqué « stopping » puis `kill`).
- **À vérifier en jeu** : camping dans les souterrains, éclairage (pirates, ruines, spatiale).

## SkyWars (EemiSkyWars)

- **État** : 1.2.3 installée ; pas encore testé en partie au dernier relevé.
- **Fonctionnement** :
  - `/skywars` (`/skw` ; `/sw` est pris par Sheep Wars) `join|leave|kit`, admin `start|stop|reload|map [id]|editmap <id>|setcenter|addcage|scanchests`. Multi-map : section `maps.<id>` (le config du serveur n'en a pas → maps du jar ; anciennes clés world/cages/chests ignorées) ; cages manquantes construites (air seulement) au choix de la map.
  - Maps : « Sugar Rush » (Ilehon, CurseForge 851519, fichier 4494368, 1.19, aucune mention de licence), monde `skywars` : 16 îles en anneau autour de (0,162), cages de verre d'origine (plancher y 111, murs 112-114, toit 115), 2 coffres par île, 10 « mid », 6 « center » (sommet vers 0 146 162), bordure 260 centrée 0,162. « Twister » (CurseForge 851886, même série), monde `sw_twister` : 16 îles (r≈43 autour de 0,160, sol y 102), pas de cages d'origine (cages calculées sur un sol 3×3 plein), 60 coffres (46 île, 10 mid, 4 centre), bordure 130 centrée 0,160, void-y 60. Écartée : « Mineshaft » (KronixLP, 4 îles). Mondes en adventure.
  - Règles (wiki Hypixel ; timings choisis) : cages 2 à 16 joueurs (cage libre la plus éloignée), 30 s de décompte (10 s si plein), ouverture = verre retiré (plancher compris, 6 s sans dégâts de chute), loot généré par le plugin (île faible, îlots, centre fort), remplissages à 5:00 et 10:00 (le 2e ajoute des perles), « Doom » à 15:00 = bordure qui se resserre à 24 en 3 min + Glowing (pas de dragons : ils tourneraient autour de 0,0), fin 20:00. Vide sous y 40 (Sugar Rush) = mort créditée au dernier frappeur (10 s), mort = spectateur au centre, objets au sol. Retour en arrière de tous les blocs modifiés (cages comprises). Coffres remplis via `getState(false)`.
  - Kits : objet « Choisir un kit » (slot 0) en attente, choix dans `choix-kits.yml`, section `kits` + `default-kit` (jar si absente), `permission:` optionnelle : defaut, archer, batisseur, fermier, enchanteur, pyro, tank.
  - PvP 1.8 (OCM old sur `skywars`, `sw_twister`).
- **Pièges** : passer d'un monde du jeu à un autre désinscrivait le joueur (corrigé).
- **À vérifier en jeu** : partie complète sur les deux maps, cages de Twister.

## Hunger Games (EemiHungerGames)

- **État** : 1.5.3 installée ; pas encore testé en partie au dernier relevé.
- **Fonctionnement** :
  - `/hungergames` (`/hg`, `/sg`) `join|leave|kit`, admin `start|stop|reload|chests|map [id]`. Map tirée au hasard à chaque partie (jamais deux fois de suite), annoncée en fin de partie. Config `maps.<id>` (desert + altrac présentes dans le config du serveur).
  - Datapack `lobby/datapacks/eemihg` installé par le plugin dans `onLoad` (`eemihg:desert/...`, `eemihg:altrac/...` ; anciennes versions supprimées ; s'il manque : `Bukkit.reloadData()`). Généré par `EemiHungerGames/gen_data.py` (lecteur NBT du scratchpad) : tables de loot sg → eemihg avec conditions résolues, `chests.yml`, variante `cornucopia_restock`.
  - **Desolate Desert** v1.0.10 [26.3] (Quillmark), monde `hungergames` : 1000×1000 centrée 0,0, corne au centre, plateformes de départ en cercle r 31 autour de 0.5 69 0.5, lobby 35531.5 60 35496.5 (bordure 500 centrée 35549 35496 en attente), 2 072 coffres/enclumes (stockage `sg:chests` du datapack d'origine, qualités common/uncommon/rare/epic/cornucopia/anvil). Événements repris : porte de la bibliothèque (vitres marron -335/-372 82..83 -296, clé = membrane de phantom), zones mortelles, sommet de la pyramide interdit (Wither III), eau bouillante de la caldera.
  - **Ruins of Altrac** v1.0.18 (Quillmark, lien Dropbox officiel de l'auteur ; zip local `maps/Ruins-of-Altrac-v1.0.18-26.3.zip`), monde `hg_altrac` : 384 coffres + 6 enclumes déjà posés (`chests_altrac.yml`, orientation « keep »), corne -219.5 46 115.5, 32 départs posés directement en y 46.5 dans une cage de barrières sur dalle (au lieu de l'ascenseur), bordure 1000 → 50 centrée -219.5 115.5, lobby -222 15.06 70, toit de la corne interdit (Wither).
  - Datapacks d'origine NON utilisés (tellraw/bossbar @a, recettes, fonctions tick), mis de côté dans `/maps-sources/hungergames/datapacks-origine` et `/maps-sources/altrac/datapacks-origine`. Écartées : Nocturne Crypts (map non publiée), maps SG CurseForge 1.17-1.18 (pas de données de coffres/spawns).
  - Jeu : min 2, max 24, 30 s de décompte, 15 s en cage, coffres replacés à chaque partie (chunks en asynchrone, cages ouvertes après), 30 s sans PvP puis survie et pose libre (blocs, seaux, feu ; on ne casse que ce qui a été posé ; map, coffres, cadres intacts même aux explosions ; tout est remis en fin de partie), bordure 1000 → 40 de 5 à 15 min, bordure accélérée (`border-rush.*` : ≤ 4 vivants → taille finale en 120 s, 2 → 60 s, jamais avant 2 min, max 4 blocs/s), deathmatch 120 s après la fin réelle du rétrécissement (Poison I à 90, II à 45, fin sans vainqueur à 0 ; égalité « N survivants »), corne remplie à 10 min, boussole traqueur. Marqueurs TextDisplay « Ouvert » puis « Vide » (tag `eemihungergames:chest_marker`, `chest-markers`) ; loot bonus à la 1re ouverture (`bonus-loot.*`). Mort = spectateur sur place, éclair + canon, objets au sol ; abandon = éliminé.
  - Kits : defaut, archer, chasseur, cuisinier, explorateur, mineur (+16 pierres), soigneur (même système que SkyWars, `/hungergames kit`).
- **Pièges** :
  - Sans section `maps` dans le config du serveur, `getConfigurationSection("maps")` rendait une section VIDE (pas celle du jar) → aucune map (corrigé dans le code depuis la 1.2.1, et section présente sur le serveur).
  - Les datapacks sont lus AVANT `onLoad` des plugins : au tout premier démarrage le plugin doit faire `reloadData()`.
  - Modèles HG ~430 Mo recopiés à chaque démarrage (~0,7 s par modèle).
- **À vérifier en jeu** : partie complète sur les deux maps, bordure accélérée, marqueurs de coffres.

## UHC Run (EemiUHC)

- **État** : 1.4.0 installée ; pas encore testé en partie au dernier relevé.
- **Fonctionnement** :
  - Source `EemiUHC/` (modèle EemiBingo). Règles validées par Thibault (28/10) : ~25 min, solo/duo/trio (une file par mode, équipes équilibrées, au moins 2 équipes), 2 à 10 joueurs, Timber + Hastey Boys + kit de départ + cut clean.
  - Monde neuf `uhc` (`uhc_n` en parallèle) à chaque partie : graine au hasard (jusqu'à 3 essais si < 75 % de terre), supprimé 2 s après la fin et au démarrage ; non déclaré dans Multiverse.
  - Déroulement (secondes dans le config) : dispersion des équipes (150 blocs d'écart), 10 s d'invincibilité, Célérité I, régénération naturelle jusqu'à `regen-until` (défaut = final heal 540 s), final heal 9:00, PvP 10:00, bordure 1000 → 50 de 10:00 à 20:00, Glowing à 20:00, bordure 50 → 10 à 25:00 (2 min), fin 35:00 (le plus de vivants puis de kills, sinon égalité). Toujours jour, Nether bloqué, difficulté normale. Monstres limités (`monster-spawn-limit` 25), minerais ×`ore-multiplier` (2).
  - Têtes d'or (tête de joueur + 8 lingots d'or, recette `eemiuhc:golden_head` : Régénération II 10 s + Absorption 2 min) ; cut clean (lingots, silex, viande cuite, pommes 3 %) ; Timber (`OVERWORLD_NATURAL_LOGS`, 256 bûches max) ; Hastey Boys (Efficacité III + Solidité III) ; kit pioche pierre, 16 steaks, 3 livres. Coéquipiers sans dégâts entre eux. Mort / déconnexion / `/uhc leave` = éliminé, objets + tête au sol.
  - File au hub : compte dans la barre d'action, lit « Quitter la file » (case 8, sinon 7/5/3/1) ; quitter le hub = quitter la file. Fin : top 3 des kills, retour au hub après 10 s, bouton [Rejouer].
  - Commandes `/uhc` (menu cliquable) `join <solo|duo|trio>`, `solo|duo|trio`, `leave`, `files` ; admin `start <mode>` (1 joueur possible pour tester, fin au temps max), `stop`, `reload`. Entrée : `[CONSOLE] dm open uhc %player%` → sous-menu DeluxeMenus `gui_menus/uhc.yml` (/uhcmenu : solo 11, duo 13, trio 15, quitter 22).
  - En partie : barre de boss, scoreboard latéral, pseudos colorés en duo/trio, vie dans la liste des joueurs (objectif HEALTH, peut être masqué par le playerlist-objective de TAB). PvP 1.8 (OCM old, EemiBlocage).
- **À vérifier en jeu** : partie complète ; que OCM applique bien le modeset au chargement du monde `uhc` (non vérifié).

## JumpLeague (EemiJumpLeague)

- **État** : 1.0.1 installée ; rien testé en jeu (faisabilité des sauts non vérifiée).
- **Fonctionnement** :
  - Règles de la JumpLeague de GommeHD (demande de Thibault). Source `EemiJumpLeague/` (modèle Pitchout). `/jumpleague` (`/jl`) `join|leave|spectate [joueur]`, admin `start|stop|skip [n]|parties|build|reload` (`skip` : parcours → deathmatch, ou deathmatch → phare dans 1 s).
  - Monde `jumpleague` (+ `_2`, `_3`, `jumpleague_modele`) : 12 couloirs en x -110..110 (tous les 20 blocs, béton de couleur), départ z 0 y 100, vers +z (~270 blocs) ; deathmatch centre 0 100 -100 rayon 30 ; salle d'attente y 170 en z -30.
  - Parcours généré par `CourseGen.java` (testé hors serveur sur 2000 graines : 11 checkpoints, 10 coffres, y 94..135), même parcours pour tous, nouvelle graine après chaque partie (PDC du monde `course_seed`, `course_gen` = `CourseGen.VERSION`). 10 modules : 3 faciles (vert), 3 normaux (bleu clair), 3 difficiles (orange), 1 ultra (obsidienne pleureuse). Écart max 3, montée max 1, 3 cases en montant seulement après une piste d'élan.
  - Jeu : 2-12 joueurs, parcours 11 min (`jump-time`), 3 s immobile au départ. Kit : cuir de la couleur du couloir, épée en bois, « Retour au checkpoint » (boule de slime = chute). Chute sous le point bas du module - 3 = retour au checkpoint. Coffre de l'Ender par module (inventaire propre, butin selon difficulté, jamais bottes/casque diamant). Fini = bottes diamant, sans chute = casque diamant en plus. Premier arrivé = deathmatch tout de suite (`finish-grace: 0`). Deathmatch 5 min : 12 apparitions en anneau, 5 s immobile, 3 vies (coup mortel annulé = -1 vie), protection 3 s, faim seulement là ; à 60 s de la fin phare sur l'un des 3 socles de fer (0 -83, -15 -108, 15 -108), cercle r 6 ; gagne le joueur dans le cercle avec le plus de kills (égalité possible, personne = pas de vainqueur).
  - Déclaré : EemiCore `games.jumpleague` (min 2, max 12), boussole `combat.yml` 31, DeluxeHub, TAB, AuraSkills, OCM old, EemiBlocage, EemiAdmin (`jumpleague join`, `jl join`). Pas de PNJ.
- **À vérifier en jeu** : faisabilité des sauts, coffres de l'Ender, deathmatch et phare.

## Pitchout (EemiPitchout)

- **2.2.4 installée le 27/11** (redémarrage 09:26 sans erreur, `a_deposer_installes/`, carte #158 « ça éjecte pas au milieu ») : (1) un joueur immobile dans le centre interdit ne déclenchait pas `PlayerMoveEvent` et y restait : `safety()` (toutes les 5 ticks) le pousse vers l'extérieur et le remet sur une case de réapparition après 2 s ; (2) l'arbre de la Jungle déborde jusqu'à 11 blocs du centre et la butte du Glace jusqu'à 5,5 (mesuré dans les `.pom`) : rayons par défaut 14 (Jungle) et 7 (Glace). Le config du serveur n'a pas de section `middle-forbidden` : les défauts du code s'appliquent.
- **État** : 2.2.2 installée (22/11) ; rien vu en jeu depuis.
- **Fonctionnement** :
  - Source `EemiPitchout/`, monde `pitchout` créé par le plugin, `/pitchout` (alias `/pitch`) `join|leave|spectate`, admin `start|stop|build|reload|map [id]`. Salle d'attente y 140 (arrivée 0.5 141 4.5).
  - Jeu : FFA 2-16, 5 vies, aucun dégât (coup/flèche à 0 en HIGHEST, vie remise au max), le recul seul compte ; réapparition sur la case libre la plus éloignée + 3 s protégé ; dernier en vie ou 10 min (plus de vies). Kit : bâton de recul (Recul 3, Thibault : « trop de KB »), arc de recul (Frappe II, Infini), perle rendue toutes les 15 s. Bonus flottants toutes les 20 s (3 max) : Vitesse II, Saut II, bâton surpuissant Recul 6 pendant 10 s, perle, +1 vie (7 max). Kill crédité au dernier frappeur < 10 s.
  - Arènes : map « PitchOut 1.20-1.21 by Missid » (monde 1.20.4 non importé : ses 168 blocs de commande ne gèrent qu'un salon), 3 arènes extraites hors ligne en `src/main/resources/arenas/<id>.pom` (gzip « POM1 ») : `desert` (colisée de grès, glace, fosse de lave), `jungle` (île et anneau de terracotta sous une couronne de mangrove), `glace` (disques de glace séparés par le vide). Posée au centre 0,0 (y 0 d'origine en floor-y 100) au démarrage et après chaque partie, jamais deux fois la même, annoncée.
  - Mort : `void-depth` (15) blocs sous floor-y (Thibault : zone de mort trop basse, pas le temps de mettre la perle) ; la lave du désert fait aussi perdre une vie. Cases de réapparition : dans le rayon, sol plein, 3 d'air au-dessus, 4 voisines pleines.
  - Centre surélevé interdit (#86) : arbre de la Jungle (rayon 9, au-dessus de floor-y + 2), butte du Glace (rayon 5, au-dessus de floor-y + 3), rien au Désert : repoussé vers l'extérieur, perle qui y atterrit annulée et rendue, cases de réapparition/bonus exclues ; config `middle-forbidden.<arène>.radius|min-height`.
  - Déclaré : EemiCore `games.pitchout`, boussole `combat.yml` 29, OCM `old`, DeluxeHub, TAB, EemiAdmin (`pitchout join`, `pitch join`).
- **Pièges** : l'ancienne plateforme procédurale et ses îlots sont retirés du code ; `void-y` du config n'est plus lu.
- **À vérifier en jeu** : que le recul reste bien avec OCM old et dégâts à 0, centre interdit, zone de mort à -15.

## Paintball (EemiPaintball)

- **État** : 1.9.0 installée (17/11, avec la nuke) ; rien testé en jeu depuis (charge de l'onde de la nuke sur les grandes maps à surveiller). Fait par Esteban.
- **Fonctionnement** :
  - `/paintball` (`/pb`), monde `paintball`. Modèles 3D (marqueur, billes, grenade) dans le pack EEMICRAFT ; sans pack, objets vanilla (armure de cheval en fer, étoile de feu d'artifice, colorants). Aperçus `EemiPaintball/apercu/`.
  - Petites maps (même contrat, tirées au hasard, jamais deux fois de suite ; `small-maps: []` = toutes) : `speedball` (la toute première, refaite avec le skill builder), `chantier`, `western`, `tournoi` « Terrain de tournoi » (`Tournoi.java` : speedball de compétition, côté snake z > 0, côté dorito z < 0, milieu, centre neutre ; gonflables grimpables). Socle `Field.java`, ids dans `SmallMaps.java`, map posée notée `small-map` dans data.yml. Au démarrage une petite map est tirée et posée (`random-start-map`, défaut true). Grandes maps (`bigs`, une tirée au hasard dès `big-map.min-players` 8 joueurs) : « Stade de la forêt » (`maps/foret.pbm`, depuis `Paintball_Map_1_1.18.schem` d'Esteban, coin x 1000 y 40 z -151, 73 apparitions par équipe) et « Fort de Battlebound » (CurseForge « Battlebound: Capture the Flag » 1543604/9220692, île centrale en `maps/fort.pbm`, coin x 2000 y 30 z -112 ; `battlebound_core:push_zone` → air). Scripts `pkscripts/gen_pbm.py`, `world_pbm.py`. data.yml `bigs.<id>.version|dirty`.
  - `/pb map [id]` (admin non inscrit, hors partie : téléporte sur la map ; 4 petites + foret/fort), `/pb build`.
  - Règles notées : invincibilité de 3 s à la réapparition (`spawn-protection: 3`, bille sur un joueur protégé annulée, sans recul), pseudos cachés (équipes `NAME_TAG_VISIBILITY NEVER` ; `enforceHiddenNames()` remet chaque seconde le tableau du jeu s'il est remplacé et logue « Tableau de X remplacé par un autre plugin »), élimination = `eemi give <joueur> coins <kill-coins>` (1 par défaut ; ne compte pas dans les kills EemiCore), `play`/`win` normaux, `reward-min-seconds` 60 (pas de récompense en rejoignant juste avant la fin). Peinture remise en fin de partie (blocs de `PAINTABLE` ; laine des gonflables peignable seulement sur le tournoi, `PAINTABLE_TOURNOI` ; filet jamais peint).
  - NUKE : `nuke-streak` (10, 0 = coupé) éliminations sans mourir → annonce, titre « ☢ NUKE ☢ », `nuke-coins` (20) au tireur, bombe (BlockDisplay TNT) au centre, onde de peinture, victoire forcée de l'équipe du tireur ; rappel à 3, 2, 1 éliminations.
  - Fin de partie : retour en salle d'attente (même depuis une grande map), lit « Retour au lobby » dès l'inscription.
  - EemiCore `games.paintball`, boussole `equipes.yml` 33.
- **Pièges** :
  - Abandonné, ne pas refaire : map `neige` (Esteban : « enlève la map neige ») et « Terrain du club » `scenario` (refusé : il voulait un terrain de tournoi en speedball).
  - Build : supprimer `target/` avant `mvn -o package` (sinon d'anciennes classes restent dans le jar). Un `Set.of` avec un doublon (`GREEN_TERRACOTTA`) plante au démarrage.
  - Un joueur déjà sur le terrain (admin venu par `/pb map`) pendant un changement de map peut se retrouver dans des blocs.
  - Le coupable qui remplace le tableau des joueurs en partie n'est pas identifié (log ci-dessus).
- **À vérifier en jeu** : nuke (charge de l'onde), petites maps, grandes maps (apparitions dans le Fort).
- **Carte #51** : pseudos cachés (équipes en `NAME_TAG_VISIBILITY NEVER`).

## Squid Game (EemiSoleil)

- **État** : 2.1.0 installée (17/11), textures actives ; jamais joué en partie normale (tests à 2 par Esteban). Fait par Esteban.
- **Fonctionnement** :
  - Ex-« 1 2 3 Soleil » devenu « Squid Game » (demande d'Esteban). `/soleil` (alias `/squidgame`, `/squid`, `/123soleil`), id EemiCore `soleil` (name « Squid Game », min 4, max 16). Admin : `/soleil start` (dès 2), `test <épreuve>` (sans récompense, se joue dès 2 joueurs), `passer`, `build [zone|tout]`, `programme`, `stop` (= renvoi au hub). Anciennes clés `min-players`/`max-players` ignorées.
  - Programme selon les joueurs (`programme.*`) : 2 → finale ; 3 → pont → finale ; 4-5 → Soleil → pont → finale ; 6-7 → Soleil → dalgona → billes → pont → finale ; 8-16 → Soleil → dalgona → corde → billes → pont → finale. Quotas d'éliminés en % (`elimination.<épreuve>.min/max` : Soleil 0-60, dalgona 0-50, corde 25-60, billes 25-60, pont 0-100), plafonnés à N-2 (« gracié » au-delà), dernière épreuve avant la finale = exactement 2 survivants. Éliminés en spectateur. Entracte `partie.entracte` 25 s.
  - Épreuves : Soleil (poupée = Mannequin ×3,8 au skin signé MineSkin dans le config, chant 1,5-5 s dont 15 % rapides, bouger > 0,15 bloc ou sauter = éliminé, pousser permis, 150 s) ; dalgona (biscuit 11×11 en `honeycomb_block`, clic DROIT sur le trait orange, fissures max 3/3/2/2, 90 s) ; corde au spam (chaque clic = +1 et 0,05 bloc, `corde.clics-max-par-seconde` 20, victoire à 6 blocs, 60 s ; `corde.periode` plus lue) ; billes (paires, pair/impair par menus, 10 billes, 180 s) ; pont de verre (18 rangées 2×2, 45 s par joueur, 420 s ; en test à 2, on traverse et une chute renvoie au départ) ; finale calamar (cloche-pied, couteau sans dégâts qui repousse, 150 s, manche retour si égalité). Gagnant : `win` + `eemi give coins` 300 + 50 × (joueurs-1).
  - Zones du monde `soleil` : terrain + dortoir en x 0, dalgona 200, corde 400, billes 600, pont 800, finale 1000 ; versions dans `data.yml` `zones.<id>`. Vues `.claude/skills/builder-minecraft/out/squid_*`. Fin de partie : retour au dortoir, lit « Retour au lobby » (PDC `eemisoleil:leave`).
  - Textures `eemicraft:squid/` dans le pack (tenues vertes 01-30, gardes roses, biscuits, aiguille, billes, couteau) ; `textures: true` dans le config du serveur.
- **À vérifier en jeu** : clic gauche en aventure, ~1 900 ItemDisplay du biscuit, corde (BlockDisplay), détection des dalles du pont, partie complète à 4+.

# Jeux TNT et divers

## Commun aux jeux TNT et divers

- **État** : rien de cette section n'a été vérifié en jeu de façon systématique ; voir chaque jeu.
- **Fonctionnement** :
  - Préfixe de chat de la catégorie TNT : `§8[§6<Nom>§8] §7` (TNT Tag, Bow Spleef, PVP Run, TNT Wars, TNT Wizards). Survie (§a) : UHC, EEMINGO. Accents dans tous les textes joueurs, pas dans la console. Titres courts (titre ≤ 16, sous-titre ≤ 38, le reste dans le chat) ; égalité = « Égalité ! » + pseudos ou « N gagnants ».
  - Parties en parallèle (jeux maison de Thibault : TNT Tag, TNT Wars, Bow Spleef, PVP Run, TNT Wizards, Course de bateaux, EEMINGO) : `_commun/WorldClone.java` copié dans chaque plugin ; partie 1 = monde d'origine, partie n = copie `<monde>_n` faite depuis `<monde>_modele` (refait au démarrage). Clé `max-instances` (3), admin `/<jeu> parties`, `start|stop [n]`, `spectate [joueur]`. Plus d'inscription « pour la suivante » : nouvelle partie si possible, sinon « Toutes les parties sont pleines ». EEMINGO ne copie pas : chaque partie génère `bingo_n`. Consigne : `_commun/BRIEF_MULTIPARTIE.md`.
  - Chaque monde de jeu (et `_2`, `_3`, `_modele`) doit être dans : mondes désactivés de DeluxeHub, disable-condition TAB, `disabled_worlds` d'AuraSkills (sinon HUD du SkyBlock en partie), EemiCore `games.<id>.worlds`, commandes bloquées en vanish d'EemiAdmin. EemiDroits lit `games` d'EemiCore ; si la sortie n'est pas « `<x> join` → `<x> leave` », la déclarer dans `sortie.<jeu>` (fait pour `entrainement` et `speedrun`).
  - EemiCore `flight-worlds` : `tntrun`, `hideseek*`, `pvprun*`, `bowspleef*` (doubles sauts) ; partout ailleurs le vol est retiré hors du hub.
  - Retour au salon d'attente en fin de partie (Esteban, 17/11 : « relancer direct ») : fait seulement pour ses jeux à lui (dans ce périmètre : Course de bateaux, Speedrun, Dé à coudre) : joueurs gardés inscrits, objet « Retour au lobby » case 8 (PDC), `/<jeu> stop` = ancien comportement. PAS fait pour les jeux de Thibault (TNT Tag, TNT Wars, Bow Spleef, PVP Run, Wizards, EEMINGO) ni TNT Run (tiers).
- **Pièges** :
  - Règle d'inventaire (`_commun/BRIEF_INVENTAIRE.md`) : ne toucher à l'inventaire que dans le monde du jeu ou au hub (`mayTouch`) ; un leave sur changement de monde ne touche à rien ; téléporter AVANT de vider. Bugs réels trouvés : Course de bateaux vidait l'inventaire dans le monde d'arrivée (perte du stuff SkyBlock) et `spectate` vidait avant de téléporter ; EEMINGO vidait avant de téléporter un joueur venant du SkyBlock. MV-Inventories traite `PlayerChangedWorldEvent` en LOW (avant les jeux) ; DeluxeHub redonne ses objets 5 ticks après l'arrivée.
  - /lobby ou changement de monde pendant une partie = quitter la partie (`PlayerChangedWorldEvent`), corrigé dans TNT Wars et TNT Tag (avant, le test du vide tuait au hub).
  - Commandes vanilla tapées à la console : elles visent le monde `tntwars` par défaut → `execute in minecraft:overworld run …` pour le lobby ; après `forceload add`, toujours `forceload remove all`.

## TNT Run (plugin tiers TNTRun)

- **État** : TNTRun 2.8.9 (Despical, Modrinth `tnt-run-minigame`, compatible 26.3). Joué.
- **Fonctionnement** :
  - Monde `tntrun` (vide, `the_void`). Arène construite à la console : 3 étages 41×41 centrés en 0,0 — sable Y 100, sable rouge Y 90, gravier Y 80, TNT dessous ; salle d'attente en verre + barrières à Y 115.
  - WorldGuard `__global__` de `tntrun` : block-break, block-place, pvp, mob-spawning, item-drop, tnt deny.
  - Arène `tntrun1` : min 2, max 24. Lobby d'attente = salle en verre (tntrun 0.5 116 0.5), start = étage du haut (0.5 101 0.5), end = spawn du hub. Le lobby d'arène doit être dans `tntrun`, sinon `tr join` envoie attendre au hub. Entrée : `tr join tntrun1` (menu Jeux TNT).
  - Monde en ADVENTURE dans Multiverse (`mv modify tntrun set gamemode adventure`) : Multiverse impose le mode à chaque changement de monde (`enforce-gamemode: true`) et TNTRun ne gère le double saut qu'en aventure (source : `GameItemEvents.onDoubleJump`) ; en survie le double saut ne marchait pas et l'allowFlight de TNTRun permettait de voler.
  - `tntrun` est dans le groupe `default` de Multiverse-Inventories (avec lobby), sinon MV-Inv et la sauvegarde/restauration d'inventaire de TNTRun se marchaient dessus (papier « Play Again » resté au hub). Groupes : `groups.yml` + `/mvinv reload` (`/mvinv group` est un assistant interactif, inutilisable à la console).
  - Double saut : 5 par partie, 4 s de délai ; `double-jumps.permissions` vidé : TNTRun prend le premier palier `tntrun.doublejumps.N` que le joueur possède et les ops ont toutes les permissions non déclarées → ils n'avaient que 3 sauts (source : `Statistics.getDoubleJumps`). « Plus jamais de double saut » = sauts épuisés. Compteur restant/max en tête du scoreboard (`scoreboard.yml`, section `in-game`, `%double_jumps%/%max_double_jumps%`, traduit en français).
- **Pièges** :
  - `/tr create` et `/tr edit` : joueur uniquement (pas la console). Min-players par défaut à 12.
  - `arenas.yml` n'est écrit qu'à l'arrêt propre (source : `onDisable` → `saveAllArenas`) : après une modif d'arène, redémarrer proprement ; un crash/kill perd les modifs ; éditer le fichier serveur allumé = écrasé à l'arrêt. `/tr reload` ne touche pas aux arènes.

## TNT Tag (EemiTntTag)

- **État** : 1.4.3 installée. Pas de trace de partie jouée.
- **Fonctionnement** :
  - Source `EemiTntTag/`. Monde `tnttag` (vide) + `_2`, `_3`, `_modele` ; WG : block-break, block-place, mob-spawning, item-drop, tnt deny.
  - Arène « chantier / ville abandonnée » de rayon 30 (config `radius: 30`, à mettre à la main dans un config existant) construite par `/tnttag build` autour de 0,100,0 : place centrale, route sous un anneau surélevé (marche y 107, escaliers, échelles), 4 quartiers (maison en ruine, échafaudage relié à l'anneau, butte à tunnels, dépôt de caisses), cabanes/haies/conteneurs en bordure, façades en ruine + barrières, plafond y 116. Lobby d'attente 17×17 à y 130 avec vitre au sol. Spawns de manche calculés par le plugin (sol dégagé jusqu'à +8).
  - Jeu : min 3 (admin `/tnttag start` dès 2), manches de 30 s (−3 s par manche, min 15), ~1 porteur pour 4 joueurs, frapper = refiler la TNT, porteurs explosent à 0. Celui qui vient de refiler ne peut pas la reprendre pendant 0,5 s ; aucun dégât entre inscrits ; scoreboard latéral.
  - EemiCore : status + reward play/win (pas de « kill »), `tnttag spectate`. Menu Jeux TNT : `[player] tnttag join`.

## TNT Wars (EemiTntWars)

- **État** : 1.4.0 installée. Pas encore testée en partie (une partie 1.0.0 avait été stoppée par le bug /lobby).
- **Fonctionnement** :
  - Source `EemiTntWars/` (même structure que Sheep Wars). Monde vide `tntwars` (spawn 0 141 0) + copies. Map construite par le plugin (démarrage, fin de partie, `/tntwars build`) : camp Rouge x < -6, Bleu x > 6 (jusqu'à ±42, z ±26, sol y 100 en pierre lisse sur 4 couches puis le vide), canal d'eau entre les deux, zone de réapparition en laine au fond de chaque camp, salle d'attente y 140.
  - Règles : le canal ne se traverse pas (PlayerMoveEvent), on ne construit que dans son camp, tout se joue aux canons à TNT ; sol et décor indestructibles (blocs posés par la construction, filtres des explosions et de la pioche) ; sortie du monde = sortie de la partie. Vies partagées par équipe (15) : mort = −1 vie, retour au camp après 5 s ; à 0 vie la mort élimine ; dernière équipe en jeu gagne, sinon après 15 min celle qui a le plus de vies. La TNT d'une équipe ne blesse pas ses joueurs (équipe et poseur attachés à la TNT allumée via la position du bloc posé).
  - Kit nommé ; barre : TNT, redstone, répéteurs, bouton, levier, seau d'eau infini (un seul, le 2e a été retiré), pierre, pioche en fer, briquet ; le reste rangé (distributeurs, sable, pistons collants, observateurs, comparateurs, slime, plaques, échelles, dalles, laine, torches de redstone). TNT/redstone/pierre/dalles/laine remis à 64 toutes les 10 s.
  - Commandes `/tntwars` (`/tw`) `join|leave|spectate`, admin `start|stop|build|reload|parties`. Menu Jeux TNT case 15 (`[player] tntwars join`). Pas de PvP 1.8 (pas de mêlée) : pas dans OCM old.
- **Pièges** : la 1.1.0 appelait `setDropItems(false)` sur TOUS les blocs cassés du serveur (`canEdit` renvoyait true hors de son monde) → plus rien ne tombait dans EEMINGO, Balls of Steel… ; corrigé (seulement dans son monde). À garder en tête pour tout listener de casse.

## Bow Spleef, PVP Run, TNT Wizards (plugins maison, catégorie TNT)

Communs : choix de Thibault (29/10) d'après les TNT Games d'Hypixel, modèle Pitchout (consigne `BRIEF_JEUX_TNT.md` des notes de session). Chaque plugin crée son monde vide, construit son arène (signature/version gardée dans le PDC du monde, reconstruite si elle change), salle d'attente en y 140, `<monde>_modele` + `_2`/`_3`, `join|leave|spectate [joueur]`, admin `start [n]|stop [n]|parties|build|reload`. Menu `gui_menus/jeux_tnt.yml` (45 cases) : TNT Run 11, TNT Tag 13, TNT Wars 15 ; Bow Spleef 29 (arc), PVP Run 31 (épée en pierre), TNT Wizards 33 (bâton de blaze), en `[player] eemi join <id>` (emmène le groupe). EemiCore `games.bowspleef|pvprun|tntwizards` (min 2, max 16). OCM `old` et EemiBlocage `worlds` : pvprun et tntwizards (+ `_2`, `_3`). Pas de PNJ ni d'hologramme de règles. Logs attendus au démarrage : « Arene Bow Spleef construite », « Arene PVP Run construite », « Maps TNT Wizards construites », « Modele … pret », aucun « Bloc invalide ignore ».

### Bow Spleef (EemiBowSpleef)
- **État** : 1.0.1 installée ; pas encore jouée.
- **Fonctionnement** : source `EemiBowSpleef/`, monde `bowspleef`, `/bowspleef` alias `/bspleef`, perm `eemibowspleef.admin`. FFA 2-16, 3 disques de TNT de rayon 18 en y 100/90/80 sous un chapiteau de cirque (rayon 36, gradins, toit rayé), élimination sous y 75. Arc Infinité + Flamme : une flèche sur la TNT retire 3×3 cases du même étage (`break-radius` 1, `break-shape` carre|croix|rond), impact vanilla annulé (ni allumage ni explosion). Aucun dégât ; flèche sur un joueur = petit recul. Triple tir (bâton de blaze, recharge 10 s), 3 doubles sauts. Mort subite à 3 min (TNT au hasard qui disparaît, de plus en plus vite), 8 min max puis égalité des survivants. Kill au dernier tireur (flèche ou bloc cassé sous lui) dans les 8 s. Arrivée salle d'attente 0.5 141 4.5.

### PVP Run (EemiPvpRun)
- **État** : 1.0.1 installée ; pas encore jouée.
- **Fonctionnement** : source `EemiPvpRun/`, monde `pvprun`, `/pvprun` alias `/prun`, perm `eemipvprun.admin`. TNT Run + PvP, FFA 2-16, 3 étages sur TNT : y 120 (étoile sable/sable rouge), 110 (fleur en poudre de béton), 100 (disque en spirale gravier/poudre), piliers rayés façon bâtons de TNT. Bloc sous les pieds retiré 0,5 s après (boîte de collision). Épée en pierre incassable (combat = OCM old), pas de régénération naturelle, kill = +6 PV (`kill-heal`), pas de dégâts de chute. Coup mortel ou chute sous y 94 = éliminé (kill au dernier frappeur < 10 s). 5 doubles sauts (4 s). 10 min max puis le plus de PV. Arrivée 0.5 141 4.5.

### TNT Wizards (EemiWizards)
- **État** : 1.4.1 installée. 1.0.0 testée par Thibault (retours appliqués) ; classes 7 à 10, changement de classe, barrière des bases, 2e map et vie sous le pseudo pas encore testés en partie.
- **Fonctionnement** :
  - Source `EemiWizards/`, monde `tntwizards`, id EemiCore `tntwizards`, `/tntwizards` alias `/wizards` `join|leave|spectate|classe [id]|map [id]`, perm `eemiwizards.admin`. Rouge contre Bleu 2-16.
  - 2 maps construites dans le même monde (`Arena.LAYOUTS`, `Arena.VERSION` 2), salle d'attente commune (arrivée 0.5 141 70.5) ; map tirée au hasard à chaque partie (`maps`, jamais deux fois de suite), annoncée ; `/tntwizards map <id>` (admin) impose la prochaine. « Île magique » 89×59 (sol y 100), bases |x| 30..38 avec tour de sorcier, points A Autel Ouest (-20,0), B Cristal Nord (0,-14), C Cristal Sud (0,14), D Autel Est (20,0), rayon 4,5. « Grande Vallée » (décalée de 400 en z) : même plan à l'échelle 1,45, île 129×85, bases |x| 45..55 (|z| ≤ 9, porte de 7), 5 points (A -29,0 ; B 0,-20 ; C 0,20 ; D 29,0 ; E « Cœur de la vallée » 0,0 à la place de l'obélisque, 4 petits obélisques autour), arbres en cerisier.
  - Capture à la majorité (neutraliser puis capturer), balise colorée ; 1 pt/s par point tenu, 5 par kill, 1000 pour gagner ou 15 min. Mêlée faible (2), mort = 5 s spectateur puis base, 2 s de protection. Règle AJOUTÉE par l'agent, à valider par Thibault : un ennemi dans une cour de base perd 1 cœur/s (`base-damage: 0` pour couper).
  - 10 classes, choix dans la salle d'attente (gardé dans `classes.yml`), menu de 36 cases (11-15, 20-24) ; clic droit = sort (mana 100, +9/s, barre d'XP), clic gauche = sort secondaire (recharge). Sorts en particules, dégâts MAGIC par le plugin, ne cassent rien. Valeurs dans `classes.<id>` du config (défauts aussi dans le code). Kinétique, Glace (40 mana, 3 dégâts, onde 2), Feu, Wither (crâne rapide 3,0, 20 mana, `hitbox` 0,6, 4 dégâts zone 2,5 + Wither I 4 s + 1 PV volé par cible ; secondaire = volée de 3 crânes ±12°, recharge 8 s, `secondary: aura` remet l'ancienne aura), Sang (vol de vie 1,5, soin de zone 3, `regen-time: 0`), Tempête, Toxique (fiole acide 25 mana : 3 + Poison II 3 s rayon 2,5 ; nuage recharge 12 s : rayon 4, 5 s, 1,5/s + Poison I, liste `zones`), Hydro (jet d'eau 25 mana : 3 + recul 0,9, éteint ; vague recharge 10 s : cône 6 blocs/40°, 2 + recul 1,3, alliés éteints et +2 PV), Terre (éclat 35 mana, lent 2,0 : 6 dégâts rayon 1,8 + Lenteur II 2 s ; séisme recharge 12 s : ennemis au sol à 6 blocs, 3 dégâts, projetés 0,9 + Lenteur II), Arcane (missile 15 mana, 3,4 : 3,5 rayon 1,2 ; clignement recharge 6 s : 8 blocs devant, s'arrête avant un mur, 2 blocs libres exigés). Projectiles orbe/boule de feu à 2,6 blocs/tick.
  - Régénération naturelle coupée (`natural-regen: false`) ; régénération hors combat après `combat-regen-delay` (4) s sans coup donné ni reçu (feu et Wither comptent) : Régénération `combat-regen-level` (2) jusqu'au prochain coup.
  - Objet « Changer de classe » (livre, case 9) : menu des classes seulement dans sa base (`inBase`), nouvelle baguette tout de suite (recharge du secondaire remise à zéro). Barrière des bases (`base-barrier`) : toutes les 2 ticks, un ennemi dans la cour/l'embrasure (|x| baseFront-0,3..baseBack+1, |z| ≤ baseHalfZ+0,5, y < floor+8) est renvoyé devant la porte (|x| baseFront-1,5) avec un petit recul.
  - Vie sous le pseudo (objectif HEALTH en BELOW_NAME sur le tableau de chaque joueur, `show-health`) et dans la barre d'action (« ❤ 18/20 | Mana … ») ; belowname de TAB désactivé, pas de conflit.
- **À vérifier en jeu** : les 4 dernières classes, barrière des bases, Grande Vallée, équilibrage.

## EEMINGO (EemiBingo)

- **État** : 1.5.2 installée. Pas de partie testée connue.
- **Fonctionnement** :
  - Source `EemiBingo/`. Nom affiché partout EEMINGO (préfixe, titres, grille, vert). `/bingo`, alias `/eemingo`. Admin `/bingo start <mode>`, `stop`, `reset`, `buildlobby`, `parties`.
  - Jeu en ÉQUIPES (Thibault s'était trompé en 1.1) : le mode fixe la taille d'équipe (solo 1, duo 2, quatuor 4). Une file par mode, décompte 30 s dès min-players (2) ; équipes équilibrées, au moins 2 équipes, 8 couleurs max. Parties en parallèle : chaque partie génère `bingo_n` (pas de copie).
  - Monde de jeu neuf `bingo`/`bingo_n` (WorldCreator, graine aléatoire) créé au lancement, supprimé 2 s après la fin (et au démarrage s'il en reste) ; bordure 400, centre choisi sur la terre ferme avec le plus de biomes (3 essais de graine). Salon d'attente commun dans le monde vide `eemingo` (salle des cartes construite par `/bingo buildlobby`, cartes encadrées sauvées dans `lobby.yml`), retour au salon en fin de partie.
  - Grille 5×5 tirée des réserves `items.easy|medium|hard` (8 faciles, 6 durs, le reste moyen ; items de biomes). Détection : scan des inventaires toutes les 10 ticks + au ramassage/craft. Règles validées par Thibault : un item trouvé par une équipe est bloqué pour les autres ; première ligne/colonne/diagonale gagne ; à la fin des 20 min l'équipe avec le plus d'items gagne (égalité possible). Saturation infinie, PvE oui, PvP non. Scoreboard latéral.
  - Carte en 2e main bloquée avec les vraies icônes 16×16 (117 PNG dans `EemiBingo/src/main/resources/icons/`, extraits du client 26.3 par `Icons.java` du scratchpad, blocs rendus en cube), bordure d'état (gris libre, vert ton équipe, couleur + croix équipe adverse) ; objet « Voir la grille » (livre, case 8) qui ouvre le GUI. Log attendu : « 117 icones chargees. ».
  - Entrée : sous-menu DeluxeMenus `gui_menus/eemingo.yml` (1/2/4 joueurs + quitter la file), donc join EemiCore de type menu. TAB `scoreboard-teams.disable-condition` inclut `bingo`. AuraSkills : `bingo`, `_2`, `_3`.
  - EemiCore : status + reward play/win (pas de « kill »).
- **Pièges** : les noms d'items du config ne sont vérifiés qu'au lancement d'une partie (avertissement « Item inconnu ») ; la liste d'origine a été contrôlée contre paper-api 26.3. Direction artistique : Thibault ne veut plus la palette rose/bleu/jaune EEMI (26/10) ; lobbys d'attente jolis et thématiques, construits par les plugins.

## Speedrun d'objets (EemiSpeedrun)

- **1.2.1 installée le 27/11** (redémarrage 09:26 sans erreur, `a_deposer_installes/`) : bouton Prêt, anti-rebond de 350 ms dans `toggleReady` (carte #157 « on clique une fois, ça met prêt puis plus prêt » : un clic sur un bloc envoie deux événements, bloc puis objet).
- **1.2.0 installée** le 26/11 (`a_deposer_installes/EemiSpeedrun-1.2.0.jar`, carte #134), redémarrage 10:23 sans erreur : objet « Prêt » donné au lobby aux joueurs en file (case 3, sinon 5 ou 1 ; PDC `eemispeedrun:pret`), synchronisé chaque seconde comme le lit « Quitter la file », retiré avant la téléportation ; toute la file du mode en décompte prête → `ready-skip-to` (5). Non vérifié : interaction avec LobbyGuard d'EemiCore au lobby.
- **Avant** : 1.1.0 (en `.jar.old`, retour au salon d'attente). Joué au moins en solo/2 joueurs le 30/10 ; suivi de groupe (1.0.3) et papier Rejouer pas encore testés.
- **Fonctionnement** :
  - Source `EemiSpeedrun/` (faite sur le PC d'Esteban). Build : JDK 25 + Maven, paper-api `26.3.build.218-stable`, `release 21`.
  - Demande d'Esteban : « speedrun aléatoire d'objet », 2 sous-modes, tout en français, survie dans une map aléatoire. Mode `objet` : la roulette tire un objet, le premier qui l'obtient marque. Mode `lettre` : la roulette tire une lettre, le premier qui obtient un objet dont le NOM FRANÇAIS commence par cette lettre marque (accents ignorés, Œ = OE).
  - Noms français : `noms_fr.tsv` dans le jar (1 799 objets, tiré du `fr_fr.json` officiel 26.3) : la lettre ne dépend pas de la langue du client. Objets tirables : `objets.yml` du jar, 172 objets de l'Overworld (57 `facile`, 78 `moyen`, 37 `difficile`), vérifiés contre Material de paper-api 26.3. Lettres jouables = au moins `letter-min-items` (3) objets : A B C D E F G H L M O P R S T. Log attendu : « N noms francais charges ; objets tirables : 57 faciles, 78 moyens, 37 difficiles ; lettres jouables : ABCDEFGHLMOPRST ».
  - Une partie à la fois (choix d'Esteban), une file par mode au lobby (min 2, max 16, décompte 30 s ; admin `/speedrun start <mode>` dès 1 joueur). Monde neuf `speedrun` (graine au hasard, bordure 1000, keep_inventory, PvP coupé, Nether/End bloqués) créé au lancement, supprimé à la fin et au démarrage. UNE manche gagnante (`rounds: 1`), `round-difficulty: [moyen]`, roulette animée (70 ticks), 20 min max (`round-time: 1200`, personne ne gagne si écoulé). Seuls comptent les objets obtenus APRÈS l'arrêt de la roulette (inventaire photographié). Détection toutes les 10 ticks + après ramassage/craft/clic/four/seau. Barre de boss, scoreboard latéral. Fin : le plus de points gagne, récompenses EemiCore play/win ; `hub` du config 722.5 51 650.5, `hub.yaw` 90.
  - Fin de partie : papier « Rejouer » (PDC `eemispeedrun:replay`) + bouton [REJOUER] ; depuis la 1.1.0 retour au salon d'attente (voir Commun).
  - Suivi de groupe : quand le chef d'un groupe EemiCore rejoint une file, ses membres au lobby y sont ajoutés (« Tu suis le chef »), les autres prévenus. Groupe lu par réflexion dans EemiCore (`partyOf` → `leader`, `members`) ; si renommés : un seul avertissement « Groupe EemiCore illisible ».
  - Commandes `/speedrun` (`/srun`, `/roulette`) `objet|lettre|leave|files|spectate|lettres`, admin `start <mode>|stop|reload`. Quitter le lobby = sortir de la file ; quitter le monde du jeu = abandon.
  - Intégrations : menu DeluxeMenus `gui_menus/speedrun.yml` (/speedrunmenu : objet 11, regarder 13, lettre 15, quitter la file 22) déclaré dans `DeluxeMenus/config.yml` ; EemiCore `games.speedrun` (join `console:dm open speedrun %player%`, spectate `speedrun spectate`, worlds [speedrun]) ; EemiDroits `sortie.speedrun` ; mondes désactivés DeluxeHub, AuraSkills, EemiAdmin.
- **Pièges** :
  - Téléporter PUIS vider l'inventaire 2 ticks après (sinon MV-Inventories enregistre un inventaire vide pour le lobby).
  - Bug 1.0.0 : `cancelCountdown()` remettait `countdownMode` à null AVANT `launch(countdownMode)` → NPE, décompte relancé en boucle (corrigé).
  - Le décompte part à 2 joueurs DANS LA MÊME file : « il attend tout le monde » = joueurs répartis entre objet et lettre, ou partie déjà en cours.
  - Le groupe ne suivait pas le chef (EemiCore ne fait suivre un jeu « menu » qu'au changement de monde du chef, or la file est au lobby ; un membre qui clique est refusé « seul le chef ») → suivi fait dans le plugin. Changement d'alias essayé puis annulé.

## Course de bateaux « Mario Barque » (EemiBoatRace)

- **État** : 2.4.0 installée (retour au salon d'attente). Joué ; sauts d'Adventure, tirage du circuit, objets Mario Kart et départ sur les grilles pas confirmés en jeu.
- **Fonctionnement** :
  - Source `EemiBoatRace/`. Renommée « Mario Barque » (Esteban) : préfixe `[Mario Barque]`, tableau « MARIO BARQUE », alias `/mariobarque` `/kart` ; boussole `DeluxeHub/menus/ambiance.yml` (entrée `boatrace`) et EemiCore `games.boatrace.name`. Commande `/boatrace` (alias `/course`, `/br`) `join|leave|spectate|record`, admin `start|stop|build|reload|parties`, id EemiCore `boatrace`.
  - 2-12 pilotes, points de contrôle dans l'ordre (pas de raccourci), cages + feux de départ, retour au dernier point (F / objet « Revenir » case 8 / chute / sorti du bateau / bloqué 10 s), 10 min max, records par circuit dans `records.yml` (`circuit-<id>`, `/boatrace record`). `finish-grace: 0` (plus de délai après l'arrivée du 1er, demande d'Esteban).
  - Un circuit PAR PARTIE (`Course` : tracé, monde d'origine, modèle `<monde>_modele`, tours/points/colonnes de grille). Nouvelle partie = monde d'origine d'un circuit libre au hasard, sinon copie `<monde>_n`. Config : `circuit`, `circuits` (absent = tous), `rotation`. Avec `rotation: true` : circuit de la partie 1 tiré au hasard au démarrage, changé en fin de course (même vide), et nouveau tirage quand le premier joueur entre dans une partie vide en attente (le circuit actuel garde 1 chance sur 3).
  - Circuits (tracés `src/main/resources/tracks/<id>.txt`, « x z sol posé » ; posé 0 = saut/ascenseur/zone libre) :
    - `cosmic` « Cosmic Cove » (Ice Boat Track, monde 1.21, monde `boat_cosmic`, importé tel quel, adventure, WG mob-spawning/item-drop/pvp deny) : glace en y 101-102, boucle de 1 475 blocs, 3 tours, 2 colonnes, arrivée en zigzag de boue x 121-122 sur la ligne droite z -35..-15, course vers +x, voie des stands z > -14 exclue ; ligne médiane `cosmic.txt` (498 points, `swscripts/centerline.py`) ; pas de « hors piste », pas de feux de départ ; salle d'attente en y 220 au-dessus de 0,0.
    - `adventure` « The Adventure » (MuscplaysYT, CurseForge 1514002, fichier 7925952, 1.21.11, monde `boat_adventure`) : course à redstone (pistons, observateurs, slimes, miel), ~3 390 blocs, 1 tour, 40 points, 1 colonne (voie de départ x -260, ligne z -36, vers le nord), colonnes de bulles (x -410 z -129, x -160 z -367) vers des ponts en y -51, slimes, anneau en hélice, labyrinthe, croix centrale à fosse murée contournée par le nord OU le sud (segment libre).
    - `stadium` « Mario Kart Stadium » (Silinox_, « boat ice race », CurseForge 1331248, fichier 6907948, 1.20.6, monde `boat_stadium`) : copie du spawn (-368 514), ~590 blocs, 3 tours, 2 étages (colonne de bulles x -343..-341 z 540..550, pont, saut x -241 → -253 z ~457), ligne en damier z 520 (x -326..-314), départ vers le sud.
    - `procedural` = ancien circuit construit dans le monde `boatrace` (glace bleue 7 de large, 843 blocs/tour ; vue `EemiBoatRace/trace.png`).
    - Lignes médianes calculées hors ligne (scratchpad : `vox.py`, `path3d.py`, `adv_wp.txt`, `race_wp.txt`).
  - Objets « Mario Kart » (`Kart.java`, même plugin) : boîtes = TextDisplay « ? » arc-en-ciel (aucun pack), rangées de 3 (`box-lateral`) tous les `box-spacing` 220 blocs (3 à 16 rangées, jamais à moins de 20 blocs de la ligne, posées sur du solide via `solidBack`), réapparition 3 s, prise seulement mains vides, roulette 1,5 s en case 0. Tirage selon la place (`kart.weights.<objet>: [premier, dernier]`), l'éclair jamais au 1er. Champignon (1,5 s, ≥ 1,8 b/t), Peau de banane (piège 2,5 blocs derrière, 60 s, étourdit 1,25 s + nausée), Boule de neige (étourdit 1,25 s), Nuage d'encre (cécité 3 s aux autres), Turbo maîtrisé (10 s, vitesse réalignée sur le regard), Éclair du leader (1,5 s d'arrêt du 1er), Étoile (5 s insensible + vitesse ≥ 1,5 + Glowing). `max-speed` 2,6 ; vitesse qui retombe peu à peu après champignon/étoile (`Racer.carry`, × `kart.boost-decay` 0,96/tick). `kart.enabled: false` remet la course classique. Réglages `kart.*` (défauts dans le jar/code).
  - Intégrations : EemiCore `games.boatrace.worlds` (`boat_*` + `_2`, `_3`), DeluxeHub mondes désactivés (+ `_modele`), TAB, AuraSkills, EemiAdmin `boatrace join`, `course join`, `br join`.
- **Pièges** :
  - DEUX SOURCES : Esteban a reconstruit `EemiBoatRace/` en décompilant la 2.2.0 de Thibault (CFR 0.152 ; corrections : casts `(Object)`, `toArray((T[])…)`, variables `rad`/`col`, `(byte)1`) ; les 2.3.x et 2.4.0 sont faites sur cette source. À trancher avec Thibault : quelle source fait référence (celle d'Esteban, qui a la version installée).
  - Abandonné : kart en modèle 3D (2.3.4, `kart-model`, Esteban 06/11 : « pas envie d'avoir les karts ») ; version gardée dans le scratchpad (`boatrace_234_annulee/`). Ne pas refaire.
  - Abandonné : Drehmal PRIME MΩTOR OIL v1 (jeu de course en datapack `pmo` dans la dimension `lodahr`, 4 courses point à point à des milliers de blocs, portes par advancements) : ne rentre pas dans le modèle « boucle fermée » ; supprimé du serveur (Esteban, 02/11). Écartés aussi : « Ice_track_BETA » (pas de glace) ; candidats non pris : « ice_boat_racing_NeW » (1271350), « Ice Boat Madness » (244244).
  - Chute « hors piste » d'un circuit importé = point le plus bas de TOUT le circuit moins `fall-depth` (Adventure −62, Stadium −63, Cosmic 100), parce que les slimes de réception d'Adventure sont en y −60..−45 : une chute d'un passage haut n'est remise que par le blocage (10 s) ou F. Replacement jamais dans un saut/ascenseur ; blocage non compté dans ces zones.
  - Cage de départ posée au-dessus du vrai sol de chaque case (cherché de floorY+3 à floorY−4), 2 de haut ; pendant les feux (`holdBoats`, chaque tick) un bateau à plus de 0,5 bloc de sa case est ramené (`TeleportFlag.EntityState.RETAIN_PASSENGERS`). Cosmic Cove est rempli de blocs `light` au-dessus de la glace → `placeCage` (air seulement) n'y pose rien ; `holdBoats` suffit.
  - The Adventure : la barrière de départ d'origine (5 glaces en `-266..-252, y -59, z -36`) a été retirée à la console dans `boat_adventure` ET `boat_adventure_modele` (les copies partent du modèle).
  - Bateaux vides restés sur la piste (cause non trouvée ; `removeBoat` ne trouve un bateau que par UUID et chargé) → `purgeBoats()` chaque seconde.
  - Toute commande tapée par un coureur (sauf /boatrace) autorise la descente du bateau pour le tick, pour ne pas bloquer `/lobby`.
- **À vérifier en jeu** : départ et cages sur les grilles d'Adventure et Stadium, bulles et saut du Stadium, pistons/slimes d'Adventure avec des bateaux, contournement de la croix des deux côtés, rotation, `setVelocity` sur un bateau piloté (turbo/champignon/étourdissement).
- **Carte #54** : Turbo maîtrisé retiré (poids forcé à 0, plus montré dans la roulette) ; `last-racer-grace` (60 s, défaut du jar) quand il ne reste qu'un pilote, sinon un AFK bloquait 10 min.

## Dé à coudre (EemiDeACoudre)

- **État** : 1.3.0 installée (retour au salon d'attente). Pas de partie testée connue.
- **Fonctionnement** :
  - Source `EemiDeACoudre/`. Demande d'Esteban, règles classiques (wiki MinecraftOnline, plugin Modrinth « Dé à coudre ») : chacun son tour on saute du plongeoir ; dans l'eau, un bloc de béton à sa couleur remplace l'eau ; tomber sur un bloc, la margelle ou hors de la piscine = −1 vie ; un trou d'eau entouré de 4 blocs non-eau (margelle comprise) = « dé à coudre » : bloc d'émeraude et +1 vie (5 max) ; dernier en vie gagne, piscine pleine = le plus de vies (égalité possible).
  - Config : `start-lives` 1, `jump-time` 15 s (sinon −1 vie), 2 à 16 joueurs, 20 s de décompte, `pool-radius` 4 (~60-69 cases d'eau ; Esteban : piscine trop large à 6), `board-height` 40 (15 à 80). Une seule partie à la fois.
  - Monde `deacoudre` vide, créé et construit par le plugin (chunks −2..1 en forceload ; version de construction et rayon/hauteur dans `data.yml`, reconstruit s'ils changent ou par `/dac build`). Décor refait avec la bibliothèque `_commun/build/` (skill builder-minecraft, VERSION 3 : piscine ronde eau y 99-100 sur lanternes marines, margelle de quartz, plage, palmiers, parasols, serviettes, lampadaires, ponton, escalier) ; plongeoir d'un bloc de large (x 0, z −8..−2) ; salle d'attente vitrée en y 140 (sol en verre sur les 4 rangées de devant, murs en verre clair, tour ne soutenant que l'arrière) pour voir les sauts. Rendus : `EemiDeACoudre/outil-test/out/dac_vue_*.png`.
  - Détection : pieds dans l'eau = réussi ; posé sur un bloc (bords compris, `isOnGround`) = raté ; plus de 7 s sans résultat = raté. Le sauteur a un contour lumineux (`setGlowing`). Un joueur qui n'est pas en train de sauter est renvoyé dans la salle s'il va sur le plongeoir ou tombe. Pendant la partie : objets du lobby, projectiles et élytre bloqués, tout dégât annulé.
  - Commandes `/dac` (`/deacoudre`) `join|leave|spectate` (depuis le lobby seulement, `join-from`), admin `start|stop|build|reload|info`. EemiCore `games.deacoudre` (join `deacoudre join`, spectate `deacoudre spectate`, worlds [deacoudre], min 2, max 16), `eemi status deacoudre …`, `eemi reward <joueur> deacoudre play|win`. Boussole `ambiance.yml` (`[COMMAND] eemi join deacoudre`, seau d'eau). Pas de PNJ (règle d'Esteban). AuraSkills, DeluxeHub, EemiAdmin (`deacoudre join`, `dac join`) déclarés.
- **Pièges** (revue Opus, corrigés) : tour gelé quand un joueur se posait sur le bord d'un bloc (et triche du pas dans le trou) ; rayon changé jamais reconstruit ; joueurs partis encore pilotés (scoreboard d'un autre jeu effacé) ; tâches d'une ancienne partie (compteur `gen`) ; objets du lobby utilisables ; `/dac spectate` depuis n'importe quel monde ; spectateur ne pouvant pas rejoindre pendant l'attente.

## Jump solo (EemiJump)

- **1.6.5 installée le 27/11** (redémarrage 09:26 sans erreur, `a_deposer_installes/`, carte #159, Esteban : « il faut qu'on gagne la même chose à chaque fois ») : refinir un parcours déjà payé rapporte la MÊME récompense que la 1re fin (pièces et xp ; les cosmétiques ne sont donnés qu'une fois). Base = `finish-coins` + bonus de fin du parcours ; en sans chute = bonus `no-fall` (2000 / 2000 sur Masters à chaque fin). Avant (1.6.3), une nouvelle fin ne rapportait rien. Compteurs `courses.<id>.replays` et `courses.<id>.nofall.replays` (journal seulement). 1.6.4 (moitié à chaque fin, mauvaise lecture de la carte) jamais installée, supprimée.
- **1.6.3 installée** (26/11, redémarrage 12:29 sans erreur, `a_deposer_installes/`, #149 encore vu par Dylan) : le classement n'affiche PLUS JAMAIS le temps de jeu total (il grossissait à chaque partie pour ceux qui avaient fini sans chrono, ex. Dylan sur Masters : 4 fins, aucun chrono, fins commencées avant 12:07 ou reprises en cours de route) ; à la place : chrono complet, sinon « 1re fin en X de jeu » (figé) si connu, sinon « fini » seul. Tri inchangé (chrono puis 1re fin).
- **Avant** : 1.6.2 (en `.jar.old` ; 26/11, redémarrage 12:07 sans erreur, `a_deposer_installes/`) : plus d'annonce à tout le serveur des records par niveau (spam, Esteban) : `announce-from-level` à 0 (jar ET config du serveur, déjà actif par `/jump reload`) ; à la place, annonce quand un chrono complet bat le meilleur chrono de TOUS les joueurs du parcours (« ★ X bat le record de … »).
- **1.6.1 installée** (26/11, redémarrage 11:46 sans erreur, `a_deposer_installes/`, #149 « les temps s'additionnent ») : tout passage complet depuis le début (niveau 0 → 1 sans changer de niveau par le menu ou `/jump niveau`) est chronométré, plus besoin de « Recommencer depuis le début » ; le classement montre le MEILLEUR chrono et n'affiche plus le temps de jeu cumulé quand un chrono existe (avant, ceux sans chrono voyaient leur temps de jeu total grandir à chaque nouvelle partie).
- **État** : 1.6.0 installée le 26/11 (`a_deposer_installes/EemiJump-1.6.0.jar`). Pas testés en jeu : slime du niveau 32, Balade, niveau 74 refait, mode sans chute, `/jump regarder`, et tout le classement par niveau de la 1.6.0.
- **Classement par niveau (1.6.0, carte #117)** : temps par niveau, en normal et en sans chute (classements séparés). Le chrono part à la validation de la plateforme précédente, ou en quittant la plateforme après un retour, une chute ou une reprise ; une chute le relance. Pas chronométré : niveau rejoint par `/jump niveau`, le menu ou après `/jump set` (marqueur `lt-skip`), ni en créatif/spectateur ; temps < `min-ms` (300) ignorés. Barre d'action « Niveau n réussi (mm:ss.cc) » + « Record du niveau ! » / « Record perso ! » ; annonce à tous seulement si un record existant est battu à partir de `level-times.announce-from-level` (default 50, balade 30, 0 = jamais). `/jump top <parcours> niveau <n> [sanschute]` (top 10), records dans le lore des groupes du menu des niveaux, tab-complétion de `/jump`. Données `plugins/EemiJump/levels/<id>[-sanschute].txt` (`uuid<TAB>pseudo<TAB>niveau=ms,…`), écrites toutes les 30 s hors fil principal, à l'arrêt et au reload.
- **Fonctionnement** :
  - Source `EemiJump/`, `/jump` alias `/parkour`, id EemiCore `jump` (join `jump join`). `/jump` ouvre le choix des parcours (menu 10/12/14/16, sans chute en 22) ; `/jump top|stats|niveau <n>|leave|sanschute` (alias nofall, hc) `|regarder <joueur>` (alias spectate/watch/spec), admin `reload|info|set <joueur> <n>`.
  - Parcours (fichiers `src/main/resources/courses/<id>.yml`, mondes chargés par le plugin via WorldCreator, pas de `mv import`) :
    - `overgrown` (DIFFICILE) : « Overgrown Parkour » (CraftGlow, CurseForge 1675767, fichier 8780489, 26.3), monde `jump_overgrown` ; couloir de 250 niveaux de 12 blocs, panneaux 1..250, plateforme d'arrivée y 99 (on se tient en y 100), sol de forêt y 97, rangées z 1, 15, 29… avec 9 virages en U ; 251 points `[x, z, axe, début, fin, yaw]` ; 35 blocs de commande retirés au 1er chargement (`neutralized` dans data.yml). Analyse hors ligne : scratchpad `pkscripts/` (nbt.py, inv.py, course.py, gen_course.py).
    - `balade` « Balade tranquille » (FACILE) : GÉNÉRÉE par le plugin (`Tranquille.java`, monde `jump_tranquille`, 50 étapes, 241 sauts, vide ≤ 2 à plat / ≤ 1 en montée, eau = retour à l'île, 5 ambiances) : aucune map facile trouvée ne passait le contrôle des sauts.
    - `masters` (MOYEN) : « Parkour Masters » (Nordic_Studios, CurseForge 1020454/5353095, licence MIT, crédit dans le menu et à l'entrée, `LICENCE-ParkourMasters.txt` dans le jar et le monde), monde `jump_masters` (1.20.1, couloir droit vers −z, 100 niveaux de 18 blocs, plateformes y −60, seul le couloir du mode 1 gardé ; 11 blocs de commande retirés) ; `fail-y -64.5`, `bounds [-4,-70,-1794,0,-54,9]`.
  - Chute = pieds sous `fail-y` (overgrown 99.0) ou sortie de `bounds` → retour à la plateforme. Plateforme validée seulement posé dessus (`isOnGround`, y ≤ floor+0.6), seulement la suivante. Reprise au dernier niveau joué. Objets : revenir (0), menu des dizaines (3), cacher/voir les joueurs (5, cachés par défaut), quitter (8). Chrono complet (« Recommencer depuis le début ») annulé par un changement de niveau. Dégâts coupés.
  - Progression par joueur et par parcours `plugins/EemiJump/players/<uuid>.yml` (max, current, falls, finished, played-ms, best-run-ms, first-finish-ms, finish-paid ; sans chute `courses.masters.nofall.*`), écriture atomique (.tmp puis move), fichier illisible jamais réécrit (.bak).
  - `/jump top` : `first-finish-ms` = temps de jeu à la 1re fin (anciens finisseurs : temps total « au total ») ; `best-run-ms` seulement via « Recommencer depuis le début » ; format mm:ss / h:mm:ss.
  - Récompenses : record multiple de 10 → `eemi give` 5 pièces + 20 xp (Balade : 3 + 15) ; 1re fin → `eemi reward … win` + bonus `finish-bonus` (section par parcours, prioritaire sur `rewards.finish-bonus` ; marqueur `courses.<id>.finish-bonus` ; rattrapé à la connexion) : Overgrown 200 pièces + « Maître du Jump » ; Balade 250 pièces + 300 xp, `title_j_marelle` « Champion de la marelle » + `trail_j_bulles` (BUBBLE_POP) ; Masters 600 pièces + 800 xp, `title_j_masters` « Parkour Master » + `trail_j_flammes` « Gouttes de lave (Jump) » (DRIPPING_LAVA, icône MAGMA_CREAM). Cosmétiques déclarés dans le config d'EemiCore.
  - Feu : `hazards` (lave) = toute la boîte du joueur ; `hazards-center` (défaut fire, soul_fire) = seulement bloc des pieds + celui au-dessus. Masters : `hazards: [lava, fire, soul_fire]` + `hazards-center: [fire, soul_fire]` ; feu éteint aux retours, pas de propagation, bascule des grandes feuilles autorisée.
  - Mode « sans chute » de Masters (section `no-fall` du parcours) : chute/feu/lave/sortie/mort/changement de mode = niveau 1 ; chrono barre d'action + tableau ; « SANS FAUTE ! », annonce à la 1re réussite ; bonus unique 2000 pièces + 2000 xp + `title_j_masters_hc` « Sans Faute » + `trail_j_hc` « Éclats du totem » (TOTEM_OF_UNDYING). Accès : case 22 de `/jump` (bottes en netherite), clic droit sur Masters, case 47 du menu des niveaux, `/jump sanschute` ; `/jump top masters sanschute`. Passer un niveau de `no-fall.announce-levels` ([70]) annonce à tous avec [REGARDER] → `/jump regarder` : spectateur collé à la caméra, renvoyé au hub quand le joueur quitte/se déconnecte/change de monde.
  - Retouches de map (`patches.version` + `patches.blocks` du fichier de parcours, lignes « x y z état » ou boîtes « x1 y1 z1 x2 y2 z2 état », appliquées une fois par version, data.yml `patched.<id>`). Masters niveau 74 refait (original = 5 demi-dalles séparées par 2 feux au-dessus du vide, jugé impossible par Esteban) : épreuve « Braises », zone x -4..0 y -60..-59 z -1322..-1308 vidée, 4 plots de pierre noire en zigzag (z -1310 x -2 ; z -1313 x -3 +1 ; z -1316 x -1 +1 ; z -1320 x -2), lanternes des âmes dessous, braseros de feu des âmes en 0 -59 -1312 et -4 -59 -1317. Log : « Parcours Parkour Masters : retouches version 1 appliquees (… blocs) ».
  - Intégrations : `jump_overgrown`, `jump_tranquille`, `jump_masters` dans AuraSkills, DeluxeHub, TAB, EemiCore `games.jump.worlds` ; EemiAdmin `jump join`, `parkour join`. Boussole `iles.yml` case 16 (`[COMMAND] eemi join jump`).
- **Pièges** :
  - Slime/miel sous `fail-y` (13 blocs de slime d'Overgrown en y 97, niveaux 30, 33, 41, 133, 212, 223, 239, 245) : `nearBounce` = pas de chute si slime/miel dans le carré 3×3 sous le joueur entre fail-y−3 et fail-y−1 et pieds pas plus bas que son dessus.
  - Le plugin copie `courses/<id>.yml` dans `plugins/EemiJump/courses/` et lit cette copie : depuis la 1.4.0 le fichier du jar est COMPLÉTÉ par la copie du serveur (la copie gagne ; pour couper une clé, l'écrire vide). Un changement d'une clé existante dans le jar doit donc être recopié à la main dans la copie serveur (`patches` vient du jar, absent de la copie).
  - Parkour Challenge (Faentum) non intégrée : pièges au sol (plaques sur TNT/lave, spawners), pas un jump.
  - Particules déjà vendues en boutique (ne pas réutiliser en récompense) : FLAME, HEART, NOTE, SOUL, SNOWFLAKE, ENCHANT, SMOKE, RAINBOW, END_ROD, ELECTRIC_SPARK ; casino WAX_ON ; Jump HAPPY_VILLAGER, BUBBLE_POP, DRIPPING_LAVA (`trail_j_flammes` doublonnait `trail_flammes` FLAME, d'où le changement).

## Entraînement (EemiEntrainement)

- **État** : 1.2.0 installée. Pas encore joué en vrai (vitesse du coup de perle, détection de l'échelle, équilibrage du difficile, pose de blocs à confirmer).
- **Fonctionnement** :
  - Source `EemiEntrainement/`. Monde `entrainement` créé par le plugin, une piste par joueur tous les 200 blocs en x (posée à l'entrée, effacée à la sortie, `pistes.yml` nettoyé après crash), joueurs cachés entre eux. Liste blanche de commandes en jeu (`allowed-commands`).
  - Menu : Bridge 10, Knockback 12, MLG 14, Perle 16 (sous-menu de difficulté, aussi par l'objet Réglages), Records 21, Quitter 23.
  - Bridge : vide 16/32/50/100, droit/diagonale/montée, blocs infinis (laine/terre cuite/béton, 16 couleurs), chrono du 1er bloc à l'île d'arrivée, chute = retour instantané, records.
  - Knockback : Mannequin « Entraineur » sur un ponton, coup = `setVelocity` sans dégât (OCM n'agit pas), Facile/Moyen/Difficile, réussite = tenir 1 s sur ses blocs, échec = 12 blocs sous le ponton, séries par difficulté.
  - MLG : tour 3×3 de 10/20/30/50 blocs (`mlg.heights`, min 10) sur une grande île (`Islands.mlg`), départ au sommet face au sud ; un objet par essai : seau d'eau (seau vide gardé), toile, ou échelle à accrocher à la tour ; réussi si les pieds passent dans l'eau/toile/échelle avant de toucher l'île, et seulement à ≤ `mlg.max-cushion-height` (3) blocs au-dessus du sol (sinon « TROP HAUT ! ») ; échelle attrapée à > `mlg.ladder-min-speed` (0,5 b/t, moyenne sur 4 ticks) ; l'eau ne coule pas (BlockFromToEvent annulé), tout est effacé à la remise en place.
  - Perle : même ponton et entraîneur, coup fort, une perle par essai lançable après l'éjection ; réussi si retour par la perle sur l'île/le ponton et tenir 1 s. `pearl.difficulties.facile|moyen|difficile` (facile = anciennes clés `pearl.*` : 1,1-1,45 b/t, 0,55 vers le haut, 25°, prévenu ; moyen 1,45-1,8 b/t, vertical 0,30-0,55, ±50°, sans avertissement, fall-limit 22 ; difficile 1,7-2,1 b/t, vertical 0,12-0,45, ±80°, 40 % de 2e coup en l'air, fall-limit 16, arrivée valide seulement au bout du ponton 3×3). Perles en vol retirées à la remise en place, pas d'endermite.
  - Séries et records par mode (`mlg.<objet>.<hauteur>`, perle par difficulté). Commandes `/entrainement` (`/training`, `/practice`) `bridge|knockback|kb|mlg [hauteur] [eau|toile|echelle]|perle [diff]|top [mode]|leave`, admin `reload|info`.
  - EemiCore `games.entrainement` (join `entrainement` = menu, min 1, max 30), `reward play` si session ≥ 120 s (max 1/30 min), status toutes les 60 s ; EemiDroits `sortie.entrainement`. Boussole `combat.yml` case 22 (laine blanche). Déclaré dans AuraSkills, DeluxeHub (OBLIGATOIRE, sinon ses objets écrasent le kit), TAB, EemiAdmin (`entrainement|training|practice bridge/knockback/mlg/perle/pearl`). PAS dans OCM old ni EemiBlocage.
- **Pièges** :
  - Blocs posés qui disparaissent : le clic droit n'émet aucun BlockPlaceEvent (`useItemInHand=ALLOW, useInteractedBlock=DENY`). Le DENY vient de NOTRE `onInteract` (`isCancelled()` d'un PlayerInteractEvent = `useInteractedBlock == DENY`), donc « annule=true » ne prouve PAS qu'un autre plugin intervient : la vraie cause reste inconnue. Contournement : repli MONITOR (si la case visée est vide au tick suivant et aucun BlockPlaceEvent, le plugin pose le bloc lui-même, mêmes règles) ; `setBuild(true)` + `setCancelled(false)` ; journal `[pose] <joueur> : …` (`debug-place: true`). Pas WorldGuard (`build-permission-nodes: false`), pas EemiDroits, `spawn-protection=0`.
  - Perle impossible à lancer : repli `pearlFallback` (le plugin lance la perle au tick suivant si elle est encore en main) ; lancers/téléportations rétablis en HIGHEST même si un autre plugin les annule ; log « [pose] … perle non lancee par le jeu ».

# Jeux d'ambiance

## Murder Mystery (EemiMurder)

**État**
- 1.14.2 installée le 27/11 (redémarrage 09:26 sans erreur, `a_deposer_installes/`) : bouton Prêt, anti-rebond de 350 ms dans `toggleReady` (carte #157 « on clique une fois, ça met prêt puis plus prêt » : un clic sur un bloc envoie deux événements, bloc puis objet).
- 1.14.0 installée le 26/11 (`a_deposer_installes/EemiMurder-1.14.0.jar`, carte #134), redémarrage 10:23 sans erreur : objet « Prêt » (colorant gris → vert au clic droit, case 4 du salon, PDC `eemimurder:ready`, non déplaçable) ; tous les inscrits prêts pendant le décompte (au moins le minimum) → temps ramené à `ready-skip-to` (5 ; 0 = coupé ; absent du config serveur = défaut du code). État par partie, effacé au lancement, au départ d'un joueur et au retour au salon. Pas testé en jeu ; le saut à 5 s peut tomber avant que la copie de map soit prête (le code attend alors « Préparation de la map »).
- Avant (1.13.0, en `.jar.old`). Rotation de 6 maps construites par le plugin : 4 grandes (`bibliotheque`, `fete`, `musee`, `paquebot`) et 2 petites (`hotel`, `chalet`). Aucune de ces maps n'a encore été jouée d'après les notes (dernier fait : reconstruites sans état invalide le 17/11).
- Deux sources du Murder ont existé : celle de Thibault et `EemiMurder/`, décompilée par Esteban depuis le jar 1.6.0 du serveur le 16/11 (jar d'origine dans `EemiMurder/_origine/`). Toutes les versions depuis la 1.7.0 partent de `EemiMurder/` : ne rien sortir depuis une autre source.

**Fonctionnement**
- Source `EemiMurder/` (Maven, paper-api 26.3, release 21). Correctifs de décompilation (CFR 0.152) : `buildLobby` (`void var9_*` → `int`), `Comparator.<Game>comparingInt`, tir de flèche (`getShooter()`), `Tag.isTagged(t)` sans cast `Keyed`, `(byte)1` pour le PDC, `m2.worldName` dans le lambda de copie. `plugin.yml` en `${project.version}`. Indépendant de LGPlus.
- Commandes : `/murder join|leave|maps`, admin `start|stop [n]|reload|setlobby|createmap|setpos|addspawn|addgold|buildlobby|buildmap <id>|parties`, `spectate [joueur]`. Taper `/murder` seul affiche l'aide (filtrée par `eemimurder.admin`) au lieu de rejoindre ; `murder join` rejoint.
- Règles : min 3 joueurs, 30 s de décompte, map tirée au hasard (jamais deux fois de suite), sortie de la zone impossible, 10 or = un arc, tuer un innocent à l'arc tue aussi le tireur, `kill-cooldown` 3 s entre deux kills au couteau (coup annulé, recharge visible sur l'épée de fer, temps en barre d'action), tomber sous le `min y` de la map = mort. Pseudos cachés.
- Durée : `game-time: 720` (12 min ; Esteban, 16/11 : « sur des maps comme ça faut jouer 10 à 15 min »), `small-map-game-time: 360` sur les petites maps (choix d'Esteban, 17/11).
- Tirage par taille (Esteban, 17/11 : « à 4 c'est interminable ») : chaque map a `size: small|large` ; `small-map-max-players` (5) → jusqu'à 5 inscrits, petites maps seulement ; nouveau tirage pendant le décompte si le seuil est franchi ; les petites maps alternent (`lastSmallMapId`).
- Fin de partie : les joueurs restent inscrits et reviennent au salon d'attente (message « Tu restes dans la file… »).
- Parties en parallèle : `max-instances` (3), salon d'attente commun (monde `murder`), maps copiées depuis `<monde>_modele` en `<monde>_2`, `_3`. Plus d'inscription « pour la suivante » : nouvelle partie si possible, sinon « Toutes les parties sont pleines ».
- Salon d'attente : monde `murder`, salon de manoir construit par `/murder buildlobby` autour du point lobby (zone de l'ancienne salle EEMI, x 110..130, y -61, z -130..-110 ; spawn du monde 120 -60 -120). Aucun dégât entre inscrits au salon. Hors créatif, portes, trappes, portillons, boutons, leviers et plaques y sont refusés (`onLobbyBlockUse`) ; l'objet tenu et le lit « Quitter » restent utilisables. Pour construire dans le salon : ne pas être inscrit (`/murder leave`), op en créatif. Scoreboard latéral.
- Config du serveur : `maps: {}` ; les maps construites sont injectées en mémoire par le plugin si absentes (le config serveur n'est pas réécrit ; une map rangée sous `maps-retirees:` n'est pas remise). `anciennes-maps:` (non lue) garde `manoir` et `farwest`.
- Maps construites par le code (`BuiltMap` + `MapDef`, une map posée à la fois, 8 ms/tick, tickets de chunks, dessin hors fil principal ; reconstruction au démarrage ou au `/murder reload` si la version change, si la pose est inachevée ou si le sol manque ; map exclue du tirage et des copies pendant la construction ; passe de finition `build/Details.java`, aides `MapKit`). `RANDOM_TICK_SPEED = 0` dans `BuiltMap.world()` pour toutes (neige et glace fondaient). Après la pose, pré-génération de chunks vides -14..13 (784 chunks, blocs -224..223), reprise via `<id>.pregen-done`. Versions de map actuelles : Chalet et Hôtel 2, Manoir, Musée et Paquebot 3, Fête 2.
  - `bibliotheque` « Manoir-bibliothèque » (`Manoir.java`, monde `mm_bibliotheque`, spawn 0 65 22) : boîte x/z -50..50, y 60..84, rez y 64, étage y 71, jardin clos 101×101 en y 63 ; hall double hauteur, grande bibliothèque à galeries et lanterneau, salle à manger, cuisine, salon, salle de lecture, serre, tourelle ; étage : galeries, bureau, archives, salon de musique, salle des cartes ; 7 escaliers ; 18 apparitions, 42 or. Clic droit refusé sur les blocs qui ne sont pas des passages.
  - `fete` « Fête foraine » (monde `mm_fete`, de nuit) : refaite d'après Carnival (même boîte, plan, hauteurs) : chapiteau, grande roue, carrousel, palais des glaces, maison hantée, chaises volantes, auto-tamponneuses, montagnes russes, labyrinthe, 8 stands ; 18 apparitions, 42 or. Seules portes et portillons s'actionnent.
  - `musee` « Grand Musée » (monde `mm_musee`) : refait d'après le Musée (sol y 64, étage y 71), T. rex dans le hall (on peut y grimper depuis un escalier d'honneur, laissé), géode contre le mur nord (x 4..8, z -24..-21), lances des chevaliers = paratonnerres au sol ; 5 escaliers ; 18 apparitions, 42 or. Seules portes et portillons s'actionnent.
  - `paquebot` « Paquebot » (`Paquebot.java`, monde `mm_paquebot`, de nuit) : coque le long de x (proue x 76, poupe x -72, largeur 27), mer décorative (eau y 52..54, anneau de barrières à ±92, iceberg), ponts aux pieds y 58 / 64 / 70 / 76, 10+ escaliers, 18 apparitions, 42 or. Règle propre : pieds hors de la coque = éliminé (« Tu es tombé à la mer ! »). `clearHeadroom()` revide les cases au-dessus des marches après le décor. Pose ~2 M cases (surtout la mer).
  - `hotel` « Grand Hôtel » (`Hotel.java`, monde `mm_hotel`, petite map 2-5 joueurs) : boîte x -34..34, z -29..29, rez y 64, étage y 70, 3 escaliers, 14 apparitions, 40 or.
  - `chalet` « Chalet de montagne » (`Chalet.java`, monde `mm_chalet`, de nuit, petite map) : cave, rez, étage sous le toit, sauna, bain nordique, étang gelé ; 3 escaliers rez-étage, 2 rez-cave ; 14 apparitions, 38 or.
- Chaque monde de map : `mv import <monde> normal`, gamemode adventure, WorldGuard `__global__` block-break, block-place, chest-access, mob-spawning, item-drop, tnt deny ; puis `mvinv reload`. Le monde `murder` a les mêmes drapeaux WG. `/mvtp <monde>` marche.
- Déclarations pour chaque monde de map (base, `_2`, `_3`, `_modele`) : disable-condition des `scoreboard-teams` de TAB (sinon TAB réaffiche les pseudos ; le plugin pose son équipe sur le scoreboard de chaque joueur), mondes désactivés de DeluxeHub, EemiCore `games.murder.worlds` et `chat.disabled-worlds`, AuraSkills `disabled_worlds`.
- Outils : contrôle automatique des maps `EemiMurder/outil-test/controles.ps1` (+ `Controles.java` : escaliers, trappes, portes, supports, vitres) ; analyse des anciennes maps `outil-test/analyse_carnival_musee.md` ; rendus `.claude/skills/builder-minecraft/out/mm_*`. Charger le skill `builder-minecraft` avant de modifier un builder.
- Maps candidates : LucasTHCR (Planet Minecraft, format `locations.txt` + `.schem`, 1.21.11, auteur des anciennes Carnival et Musée) a aussi Ravenwood (manoir victorien, 20 apparitions, 42 or, https://www.planetminecraft.com/project/ravenwood-murdermystery/) et Bazaar.
- Anciennes maps : `manoir` (-48,-60,-34 → -11,-44,7) et `farwest` (-80,-60,-303 → -21,-41,-239) dans le monde `murder` (map CurseForge « Murder Mystery java map », xxmustafastaxx, All Rights Reserved ; ses 24 blocs de commande supprimés ; fosses-pièges du manoir en béton rouge à y -64) ; zone C (village -224,-208 → -113,-113) jamais utilisée. Pour les rejouer : les remettre sous `maps:`.

**Pièges**
- Esteban, 16/11 : Carnival, Musée, Cheap House et Camp Blood SUPPRIMÉES (mondes et modèles effacés). Ne pas les remettre : refaites par code en Fête et Grand Musée. Cheap House (aventure horreur LGSC, nettoyée hors ligne) et Camp Blood (schéma Litematica de Kowal_96) avaient déjà été retirées par Thibault le 15/11. Les mentions de ces mondes dans TAB, EemiCore, DeluxeHub, AuraSkills restent (sans effet).
- `maps: {}` et pas `maps:` vide : sinon Bukkit prend les maps par défaut du jar (Manoir, Far West).
- Une version qui change le numéro d'une map la reconstruit au démarrage et écrase les retouches faites à la main dans son monde (celles d'Esteban dans `mm_chalet` sont reprises dans le code 1.13.0).
- Multiverse charge les mondes de map au démarrage avec un générateur NORMAL (avant le Murder) : les chunks hors map doivent déjà avoir été générés vides, sinon du terrain naturel apparaît autour. Si une map grandit, pré-générer plus large AVANT l'import. Avertissement attendu et sans conséquence : « charge avec un autre generateur (Multiverse ?) : chunks non pre-generes ».
- 1.7.0 cassée : un « : » non cité dans le `usage` du `plugin.yml` a empêché le chargement (« Invalid plugin.yml »). Valider `plugin.yml` et `config.yml` avec SnakeYAML avant de livrer.
- Portes du salon : la porte sud et 2 trappes posées par `buildLobby` n'étaient pas bloquées (WG `use` non refusé), d'où le refus dans le plugin.
- Trappes à l'envers : sens inversé dans les aides fauteuil/banc/bac (`MapKit`, `Manoir`), corrigé. Vitres : `Facade` les posait une case à l'intérieur du mur, corrigé (règle : vitre DANS le plan du mur). Escaliers : 3 cases libres au-dessus de chaque marche et devant la 1re (on se cognait la tête), contrôlé par `controles.ps1`.
- Restes connus : Paquebot, verrière du fumoir accessible, parkour sur les mains courantes, passerelle des machines sans garde-corps ; vitres restantes = décor voulu (anneaux de la Fête, vitrines du Musée, pupitre du Paquebot, voitures et motoneiges du Chalet).

**À vérifier en jeu**
- Une vraie partie sur chacune des 6 maps (apparitions, or, durée, tirage petite/grande map selon le nombre de joueurs).
- Copie `WorldCreator.copy` des maps pour les parties 2 et 3.
- Règle de la mer du Paquebot ; retour au salon en fin de partie.

## Cache-cache (EemiHideSeek)

**État**
- 1.6.0 installée le 26/11 (`a_deposer_installes/EemiHideSeek-1.6.0.jar`, carte #135 « ne pas pouvoir se cacher sur les maisons ou en hauteur à base de jump »), redémarrage 10:23 sans erreur : un cacheur posé au sol, à l'air libre (rien de solide sur `roof-cover-scan` 24 blocs ; feuilles, barrières et blocs de cacheurs ne comptent pas) et à plus de `max-roof-height` (3) blocs au-dessus du sol de la map (`HsMap.roofGround`, max des 3×3 colonnes autour) ne peut pas devenir solide ; après `roof-return-delay` (2 s), ramené au dernier point valide au sol (sinon au départ des cacheurs). Chercheurs, spectateurs et cacheurs déjà solides non concernés. `roof-block: false` coupe la règle. Sols : village y 64 (toits ≥ +5 interdits, auvents des étals +3 permis, haut du puits interdit) ; port = terrain du générateur + ponts des bateaux, estacade, plancher de la galerie du phare (`perchAllowed`) ; supermarché entièrement couvert (gondoles et mezzanine permises). Banc `outil-test/.../PerchTest.java` : port 820 cases interdites sur 7 943, toutes des toits. Toute nouvelle passerelle extérieure en hauteur doit être déclarée dans `roofGround`/`perchAllowed`.
- Avant (1.5.1, en `.jar.old`). Maps en rotation : `village`, `supermarche`, `port`. Les nouveautés depuis la 1.3.0 (vrais blocs, onde de choc, port) n'ont pas été vues jouées dans les notes.
- Deux sources ont existé : `EemiHideSeek/` est décompilée par Esteban depuis la 1.2.4 (jar d'origine dans `_origine/`) et sert depuis.

**Fonctionnement**
- Source `EemiHideSeek/`, monde `hideseek` (créé par le plugin, + `_2`, `_3`, `hideseek_modele` ; les 3 maps sont dans le même monde et donc dans le modèle et les copies). `/hideseek` (alias `/hns`, `/cachecache`) `join|leave|spectate`, admin `start|stop [n]|parties|map [id]|build [id|tout]|reload`.
- Règles : 3-20 joueurs, 1 chercheur pour 5, 30 s pour se cacher (chercheurs aveugles en salle noire), recherche 7 min (`game-time: 420`), cacheur trouvé = chercheur, provocation (+1 point, -5 s). Chercheurs en Vitesse (`seeker-speed`, 2 = Vitesse II, 0 = aucune ; Thibault, 01/11 : « le chasseur doit courir plus vite »), donnée par `equipSeeker`.
- Cacheurs : 40 blocs de déguisement par défaut (27 à 38 selon la map, surcharge `map-blocks.<id>`), BlockDisplay qui suit le cacheur ; immobile 3,5 s = devient solide. Solide = VRAI bloc posé sans physique, le cacheur reçoit de l'air dans sa case et garde son BlockDisplay visible de lui seul (`setVisibleByDefault(false)` + `showEntity`), redevient visible de tous quand il bouge. Journal `plugins/EemiHideSeek/blocs-cacheurs.yml` rejoué au démarrage. Objet « Tourner le bloc » (bâton de blaze, case 2) : clic droit = quart de tour (`BlockData.rotate(CLOCKWISE_90)`), clic gauche = retourner (dalle, escalier, trappe, axe des bûches, façade). Un cacheur ne voit pas les autres cacheurs (`refreshVisibility` à la découverte). Bruit forcé de chaque cacheur toutes les 45-60 s.
- Chercheurs (suggestion #103) : 10 cœurs, -1 par coup d'épée dans le décor (seule l'épée compte ; 250 ms ignorées après un clic droit ou un objet lâché), +2 par cacheur trouvé, pas de régénération ; à 0 : salle noire 15 s puis retour. Onde de choc (baguette de breeze, case 1, `shockwave-radius: 5.0`, `shockwave-cooldown: 15`, à travers les murs, déloge et repousse). Radar (boussole de récupération, case 2) : Brûlant/Chaud/Tiède/Froid, paliers 10/20/35, `radar-cooldown: 4` (Esteban, 26/11 : « environ 4 s » ; 1 s avant).
- Maps (tirées au hasard, jamais deux fois de suite ; `maps: []` = toutes) :
  - `village` : sol y 64, barrières à ±45 (manoir, maisons, grange, forge, taverne, marché) ; attente 0.5 101 75.5, salle noire 0.5 101 -74.5.
  - `supermarche` : x 752..847 (90×90) : réserve, chambre froide, mezzanine de bureaux, 12 gondoles escaladables, caisses, parking couvert.
  - `port` « Port de pêche » (`PortMap.java`) : boîte x 340..520, y 50..112, z -48..59 ; falaise, terrasse haute (maisons, chapelle, lavoir, calvaire, fumerie), venelles, quai, criée, entrepôt 3 niveaux, bassin à marée basse (pas de noyade), 3 bateaux, épave, digue et phare ; 35 déguisements.
- Construction des maps au démarrage si absente, version changée ou bloc témoin vide, hors fil principal (20 000 cases/tick), `map.yml` écrit après sauvegarde du monde. Points d'attente, salle noire et apparitions par map.
- Fin de partie : retour au salon d'attente, joueurs gardés inscrits.
- Intégrations : `hideseek` (+ copies) dans `flight-worlds` d'EemiCore, mondes désactivés de DeluxeHub, disable-condition TAB, join bloqué en vanish (EemiAdmin) ; dans aucun groupe Multiverse-Inventories (le plugin vide l'inventaire lui-même). Outil d'audit `EemiHideSeek/outil-test` ; rendus `.claude/skills/builder-minecraft/out/hs_*`.

**Pièges**
- Map `eemi` « L'EEMI géante » abandonnée (Esteban, 17/11 : « la map EEMI est pas du tout bien, on enlève l'idée ») : retirée du code (`RETIRED_MAPS`), sa zone est réutilisée par le port. Ne pas la refaire.
- Faux blocs (`sendBlockChange`) : un clic droit ou gauche du chercheur faisait renvoyer l'air réel par le serveur (`handleUseItemOn`), le faux bloc disparaissait ; d'où les vrais blocs.
- Une fois solide, le cacheur ne voyait plus son bloc (le bloc fantôme ne lui est pas envoyé et le BlockDisplay était supprimé) : d'où le BlockDisplay gardé pour lui seul.
- Les effets du jeu restaient au hub après /lobby (corrigé).
- Fuite connue : message « Impossible de devenir solide ici » sur la case d'un autre cacheur.

**À vérifier en jeu**
- Partie complète sur les 3 maps, vrais blocs des cacheurs (et nettoyage après un arrêt brutal), onde de choc, radar, cœurs du chercheur.

## Block Party (EemiBlockParty)

**État**
- Installé : EemiBlockParty 1.2.3. Jamais vu joué dans ces notes.

**Fonctionnement**
- Source `EemiBlockParty/`, monde `blockparty` créé par le plugin (+ copies `_2`, `_3`, modèle ; `max-instances` 3). `/blockparty` (alias `/bparty`) `join|leave|spectate`, admin `start|stop [n]|parties|build|reload`.
- 2-30 joueurs, piste 48×48 en y 100 (x/z -24..23), 8 motifs, 7 airs en blocs musicaux, temps de réaction 5 s → 1,5 s, bonus (vitesse, saut, Oracle, Sauveur), 25 manches max, chute sous y 97 = éliminé. Salle d'attente y 130 (arrivée 0 131 2). Log attendu « Arene construite : piste 48x48 en y 100 ».
- Intégrations : mondes désactivés de DeluxeHub, disable-condition TAB, join bloqué en vanish, aucun groupe Multiverse-Inventories. Pas de « kill » envoyé à EemiCore.

**À vérifier en jeu**
- Une partie complète.

## Tonte Royale (EemiTonte)

**État**
- Retirée du jeu par Esteban le 01/11 : plus dans le menu Wool Games (case 13 en vitre), ni dans `games` ni `random-games` d'EemiCore, hologramme de règles sans elle. MAIS le jar EemiTonte 1.2.3 est de nouveau dans /plugins (origine inconnue) et a été gardé au ménage du 16/11 : il tourne sans entrée. À trancher avec Esteban : supprimer le jar ou remettre le jeu.

**Fonctionnement**
- Source `EemiTonte/`, monde `tonteroyale` (créé par le plugin, + copies). `/tonteroyale` (alias `/tonte`). Wool Game inventé : 2-16 joueurs, 2-4 équipes (`team-size` 2), toison de 5 couches (la vie vanilla n'est jamais touchée), cisailles ennemies = -1 couche et +4 laines au tondeur (seule ressource de construction, pas de laine au départ), tondu = le coup suivant élimine, brouter accroupi 2 s sur l'herbe = +1 couche (l'herbe devient terre), Berger toutes les 120 s (loup 30 s), après 15 min plus de couches. Auto-tonte (accroupi + clic droit cisailles = -1 couche, +4 laines, jamais sous 1 couche), ajoutée par un sous-agent, jamais validée par Thibault : `self-shear: false` pour la couper. Îles aux coins (±32, ±32), pré central rayon 14,5, salle d'attente y 140.
- Modeset `old` d'OCM ; mentions dans DeluxeHub, TAB, OCM, AuraSkills laissées.

**Pièges**
- « Je peux pas poser de bloc » : `onInteract` mettait `useInteractedBlock` à DENY sur tout clic droit (pour ne pas ouvrir portillons et chaudrons) ; Paper ne pose alors l'objet tenu que si `useItemInHand` vaut ALLOW. Règle pour tout plugin : ne jamais refuser le bloc cliqué sans autoriser explicitement l'objet qu'on veut pouvoir poser.

## Build Battle (Plugily + EemiBuildBattle)

**État**
- Installés : Build Battle | GuessTheBuild de Plugily (`BuildBattleGuessTheBuild.jar`, BuildBattle 5.1.8, SpigotMC 44703) et EemiBuildBattle 1.1.0. Arènes `eemicraft` (solo) et `eemicraft_duo` démarrées (« instance successfully started »). Aucune partie vue dans les notes ; murs et duo jamais vus en jeu.
- `EemiBuildBattle/` (sur le PC d'Esteban) est décompilée depuis la 1.0.0 (`_origine/`) ; `gen_arena.py` d'origine (solo) n'est pas sur ce PC. Deux sources ont existé : partir de celle-ci.

**Fonctionnement**
- Monde vide `buildbattle`. Joueurs : `/buildbattle join <arène>`, `/buildbattle randomjoin` ; admin `/buildbattleadmin` (`/bba`) : `setup create <arène>`, `setup edit`, `addplot`, `reload` (à taper deux fois, confirmation). Arènes dans `BuildBattle/arenas.yml` : 4 positions obligatoires (lobby, start, end, spectator), plots `<n>.1` et `<n>.2`, sol = Y minimal du plot (format vérifié dans le source Plugily). `locale: fr_FR` dans `BuildBattle/config.yml`.
- Arène `eemicraft` « Jardins d'EEMICRAFT » : 8 terrains 25×25, sol y 64, hauteur 30, 4 colonnes × 2 rangées (x -70/-35/0/35, z -40 et 15), avenue et place à fontaine (centre -6 64 0) ; lobby/départ/fin -5.5 65 9.5, spectateur -5.5 89 0.5. Construite par le datapack `lobby/datapacks/eemibb` (`execute in minecraft:buildbattle run function eemibb:build` après `gamerule max_block_modifications` et `forceload add -96 -64 80 64`, puis `forceload remove all`) ; générateur `EemiBuildBattle/gen_arena.py` (datapack + section arenas.yml).
- Murs (Thibault, 27/10 : ne plus voir les constructions des autres) : quartz lisse plein autour de chaque terrain sur le liseré de béton (1 bloc hors du terrain, jamais remis à zéro par Plugily), y 65 à 110 ; fonction `eemibb:walls` (32 `fill`, appelée à la fin de `eemibb:build`). Ils ferment aussi la vue depuis l'avenue et le point spectateur.
- Arène `eemicraft_duo` (« Jardins d'EEMICRAFT - Duo ») : 8 terrains en z 260/315 (le solo décalé de +300 en z), mêmes murs ; `plotmembersize: 2` (passe en TEAM, `gametype team`), min 4, max 16, plots 0..7, fin = spawn du lobby ; fonctions `eemibb:build_duo|walls_duo|clear_duo` dans `/lobby/datapacks/eemibb/data/eemibb/function/` (forceload -96 240 79 367) ; générateur `EemiBuildBattle/duo/gen_arena_duo.py`, section `arenas_duo.yml`, étapes dans `EemiBuildBattle/duo/LISEZMOI.md`. Temps : `Time-Manager.Teams` (Theme 25, Plot 20, In-Game 540).
- Entrée : boussole `ambiance.yml` → sous-menu DeluxeHub `menus/buildbattle.yml` (27 cases : Solo 11 `eemi join buildbattle`, Duo 15 `buildbattle join eemicraft_duo`, Retour 22). `buildbattle` dans les mondes désactivés de DeluxeHub. Plusieurs arènes possibles en même temps (gérées par Plugily).
- EemiBuildBattle (source `EemiBuildBattle/`) : appelle Plugily par réflexion (`Main#getArenaRegistry()`, `getArenas()`, `getPlayersLeft()`, `getTimer()/setTimer(int)`, `getArenaState()` = IN_GAME, `BaseArena#getArenaInGameState()` = THEME_VOTING/BUILD_TIME/PLOT_VOTING). Toutes les 10 ticks en BUILD_TIME : joueurs cachés entre eux (réaffichés au vote), coéquipiers visibles entre eux (`show-teammates`), objet « Je suis prêt » (colorant gris/vert, case 7) ; tous prêts → temps restant ramené à 5 s (`ready-timer`). `fix-orphans` : un joueur laissé sans terrain par Plugily est placé sur le terrain incomplet le plus rempli.

**Pièges**
- Plugily n'a aucune option pour cacher les terrains (`Plot:` n'a que Hide-Owner et Move-Outside), ni pour cacher les joueurs, ni de commande pour changer le temps (vérifié : config et `/bba`) : d'où les murs et EemiBuildBattle.

**À vérifier en jeu**
- Partie solo et duo (murs, traduction fr_FR, joueurs cachés, « Je suis prêt », coéquipiers visibles, orphelins).
- Gestion d'inventaire de Plugily face à Multiverse-Inventories.

## Loup-Garou (LoupGarou + LGPlus)

**État**
- Installés : `LoupGarou-1.0.5.jar` et LGPlus 1.1.0. Testé avec de vrais joueurs le 26/10 (dalles, rôles, nuit, votes par l'objet et les menus) avec une version antérieure de LGPlus ; les ajouts de la 1.1.0 n'ont pas été vus testés.
- À trancher avec Thibault : version de LoupGarou. Le jar installé (1.0.5, 394 531 octets) n'a que 5 rôles dans l'enum `PlayerRole` (loup, villageois, voyante, garde, sorcière), vérifié au javap ; le jar Modrinth « loup-garou » 1.0.0 (Gh0st-cmd) à 10 rôles est dans Téléchargements/LoupGarou-1.0.0.jar et n'est pas installé.

**Fonctionnement**
- Monde `lg` (importé), région WorldGuard `loupgarou` : seuls les joueurs dans cette région du bon monde comptent. Faim coupée (`mv modify lg set hunger false`). Une seule partie à la fois.
- Config LoupGarou : `scoreboard.enabled` obligatoire (le lancement auto est vérifié par la tâche du scoreboard), `freeze-players-at-night` et `freeze-during-vote` à false (sinon téléportation en cercle), Sorcière min-players 6, petite fille 10.
- Entrée : boussole `ambiance.yml` → `mv tp lg`. Retour au lobby depuis lg : PNJ `console_command lglobby {player}` (`mv tp {player} lobby` ne marchait pas, cause non trouvée ; pas testé). `lg` dans la liste noire des mondes de DeluxeHub.
- LGPlus (source `LGPlus/`, reconstituée en décompilant la 1.0.6 avec CFR, 5 erreurs corrigées ; LoupGarou en dépendance système `lib/LoupGarou-1.0.5.jar`, compilé contre ce jar) :
  - `/lgdalle add|list|remove <n>|clear` : dalles de départ (position + regard), placement aléatoire 0,5 s après le début, joueurs gelés toute la partie (tête libre), morts en spectateur figés.
  - Nuit tour par tour (le minuteur de LoupGarou est annulé) : Cupidon (nuit 1) → Voyante → Garde → Loups (+ petite fille) → Sorcière → Assassin ; 20 s par tour, 30 s pour les loups, fin dès que le rôle a joué (min 5 s) ; rôle mort ou non distribué : attente factice de 5 à 8 s. La nuit : vivants invisibles entre eux, Cécité pour les non-loups, barre de boss avec compte à rebours.
  - Menus de têtes : horloge « Nuit » identique pour tous, bulletin pour le vote, casque doré pour l'élection du maire au jour 1 ; votes affichés dans le menu et en texte flottant ; chasseur mort : noms cliquables dans le chat. Papier « Passer a la suite » (case 8, `passer.ratio`), livre des rôles (case 7, rôles distribués seulement). `/lgnext` passe le tour ou la phase. `/lglobby [joueur]`.
  - `/lg tuer|voir|proteger|...` tapées à la main : bloquées pendant la partie. Messages de LoupGarou limités aux participants et aux joueurs de `lg`, titres limités aux participants, texte doublement encodé réparé, messages répétitifs filtrés (broadcast + filtre réseau par réflexion, `chat.filtre-paquets: false` pour le couper), plus de « [DEBUG] » en console.
  - Ligne « Joueurs: » du scoreboard d'attente réécrite avec les joueurs qui peuvent jouer ; région testée (`WorldGuardIntegration.canPlayerPlay`) seulement pour les joueurs du monde lg, toutes les 5 ticks.
  - Admins en vanish (`vanished`) exclus via une sous-classe de `WorldGuardIntegration` ; côté EemiAdmin, spectateur forcé dans `lg` et parade `lg-start-guard` (sort les admins en vanish de la région pendant `/lg start`).

**Pièges**
- leomelki/LoupGarou (1.15.1, archivé) est incompatible 26.3. Le dépôt GitHub Gh0st-cmd/Plugin-LoupGarou (1.0.5) n'a que 5 rôles.
- À l'arrêt, LoupGarou réécrit sa config avec un en-tête aux caractères cassés (0x8F) qu'il ne relit plus → il retombe sur le monde minijeux, min 4 ; une clé manquante déclenche aussi une réécriture. LGPlus nettoie l'en-tête au démarrage. Ne pas utiliser `/lg setspawn`.
- `Bukkit.getOnlinePlayers()` dans le scoreboard d'attente de LoupGarou (bytecode) : comptait tous les connectés. `canPlayerPlay` écrit « [DEBUG] Monde incorrect » à chaque appel hors de lg (inondait les logs).
- LoupGarou 1.0.5 compte tout joueur de la région, sans tester mode ni visibilité (bytecode) : d'où l'exclusion des admins en vanish.
- Limite connue : si le maire provisoire meurt la nuit 1, LoupGarou lance sa propre transmission du maire. Les titres d'élimination et de victoire de LoupGarou pouvaient partir à tout le serveur (limités depuis LGPlus 1.1.0).

**À vérifier en jeu**
- Ajouts de LGPlus 1.1.0 (papier, livre des rôles, filtre, accents, `/lglobby`, vanish) ; PNJ de retour `lglobby`.

## Infection (EemiInfection)

**État**
- 1.9.0 installée le 26/11 (`a_deposer_installes/EemiInfection-1.9.0.jar`, carte #136 « on spawn dans des zones qui bloquent »), redémarrage 10:23 sans erreur : une case de dispersion (survivants et réapparition des zombies, liste `spots`) doit être reliée à la rue dans les deux sens (`Reach.run` + `runBack` ; avant, une case atteinte seulement en tombant était gardée), clôtures et murets ne servent plus de marche (`TALL`), seules portes/trappes/portillons ouvrables sont traversables, marge `roomy` (3 cases de haut, 4 voisines praticables). Mesuré hors serveur : 9 305 cases, 0 piégée (1.8.0 : 26 423 dont 232 piégées). `BUILD_VERSION` inchangée (ville non reconstruite). Au démarrage, « Accessibilite : … cases de dispersion gardees » ≈ 9 000. Audit : `EemiInfection/outils/src/fr/eemicraft/infection/ReachAudit.java` (le `spots()` de `TestCity` ne vaut rien pour l'accessibilité).
- Avant (1.8.0, en `.jar.old`). Une 1re partie a été jouée le 16/11 (avec la 1.3.0) ; les versions suivantes (pouvoirs au menu, Charge, textures, retour au salon) n'ont pas été vues jouées dans les notes. Textures activées (`textures: true`) mais rendu en jeu pas confirmé.

**Fonctionnement**
- Source `EemiInfection/`, `/infection` (alias `/zombie`), id EemiCore `infection`. Entrée : bouton Infection d'`ambiance.yml` en direct (`eemi join infection`).
- Règles : ville abandonnée de nuit, 3-20 joueurs, 20 s de fuite, 1 zombie pour 6, coup de zombie = infecté, durée 4 min. Survivants : arc (flèche = zombie renvoyé à la crypte), épée qui repousse, armes à feu (fusil d'assaut, pompe, sniper à lunette, mitraillette, pistolet), grenades, caisse d'armes ; balles sans recul ni animation de dégât (`weapons.bullet-knockback: false`). La vie des zombies est un compteur du plugin, affiché en cœurs (`zombie-hearts: true`, mise à jour au plus toutes les `zombie-hearts-interval` 10 ticks) ; régénération vanilla annulée pour les inscrits.
- Zombies : contour VERT, survivants BLANC (couleur de l'équipe de scoreboard, demande d'Esteban) ; tous brillent (`zombie-glow`). Chaque nouveau zombie choisit son pouvoir dans un menu (pouvoir pris = grisé avec le pseudo, 10 s puis tirage d'un libre ; étoile ou `/infection pouvoir` pour rouvrir ; au-delà des 4 pouvoirs = zombie classique). Pouvoirs dont : Langue (tir instantané sur le survivant visé, tiré vers le zombie + Lenteur II 1,5 s, `tongue-cooldown: 18`, 2 s si raté, `tongue-range: 14` dans le config serveur, demande d'Esteban), Charge (bâton de blaze, ~10 blocs en 0,8 s, survivant projeté et étourdi 1,5 s, mur = arrêt, recharge 20 s), un pouvoir à projectile d'acide. Machette, griffes.
- Fin de partie : `finishToWaiting()`, joueurs gardés inscrits en salle d'attente, kit gardé, lit « leave » case 8 ; un spectateur qui fait join joue la suivante (`wantsNext`) ; `/infection stop` = tout le monde au hub.
- Ville : `City.java` (BUILD_VERSION 5), 77 bâtiments (église à flèche, crypte des zombies sous le cimetière, supermarché, parc, station-service, maisons), 54 accès aux toits ; ~3,28 M blocs posés au démarrage quand la version change. Cases de dispersion filtrées par accessibilité à pied (cases enfermées refusées). Outils `EemiInfection/outils` (+ `AuditCity.java`), rendus `EemiInfection/outils/infection_old|new_*.png`.
- Textures (Esteban : « des vraies textures, comme Thibault dans Refuge ») : dans le pack principal, pas de pack séparé ni de retexture de blocs : 71 fichiers `eemicraft:infection/` (armes en modèles 3D, sprites, pouvoirs, tenue zombie par `equipment` `eemicraft:infection_zombie` sur un LEATHER_HELMET à 0 armure à la place de la ZOMBIE_HEAD, gilet des survivants `infection_survivant`). Générateur `EemiInfection/pack_src/gen_infection_pack.py` (aperçus `pack_src/apercu/`), fusion par `build_pack.py`. Tout derrière la clé `textures` (défaut false = jeu sans textures ; `true` sur le serveur, effet à la partie suivante après `/infection reload`).

**Pièges**
- La caméra penche à chaque baisse des cœurs des zombies : inévitable côté serveur (`LocalPlayer.hurtTo`, vérifié au javap du client 26.3).
- La prise maintenue de la Langue (1.4.0) a été supprimée, retour au tir instantané d'origine. L'Escalade a été remplacée par la Charge.
- `/infection build` en pleine partie bloque le serveur 5 à 15 s.
- Règles des maps générées : vitres dans le plan du mur, tête libre devant la 1re marche (contrôlé par l'audit).

**À vérifier en jeu**
- Rendu des armes en main, tenues, projectiles ; menu des pouvoirs, Charge, Langue à 14 blocs ; retour au salon en fin de partie.

## Versus Left 4 Dead (EemiVersus) : abandonné

- Abandonné le 15/11 (Esteban : « très complexe à faire fonctionner et pas sûr que ça plaise ») ; tout supprimé du serveur le 16/11 (monde `l4d_deadcenter`, dossier du plugin, entrées EemiCore, DeluxeHub, TAB, AuraSkills, EemiAdmin) et la source `EemiVersus/` n'existe plus. Ne pas refaire sans nouvelle demande.

# SkyBlock, OneBlock, casino

## SkyBlock (BentoBox / BSkyBlock) : inventaires, économie, bazar, boss

- **État**
  - BentoBox 3.23.4 (addon BSkyBlock), AuraSkills 2.4.0, AuctionHouse 1.5.6, AxMinions 1.0.21 installés. S-Economy 1.2.3, RoyalBazaar et Vault (VaultUnlocked 2.20.3) sont cités dans les notes mais absents de `installes.txt` : à vérifier.
  - Skyblock codé par une autre session (BanTeEs = auteur du skyblock) ; source envoyée par Thibault : `Téléchargements/eemicraft-skyblock-source-2026-10-29.zip` (copie aussi à la racine du serveur, `/eemicraft-skyblock-source-2026-10-29.zip`, gardée).
  - Ouvert : skins des boss jamais vus en jeu ; `startingBalance` 4 500 jamais vérifié sur un nouveau compte ; locales BentoBox corrigées le 01/11, prises au redémarrage suivant (non revérifiées).
- **Fonctionnement**
  - Mondes : `bskyblock_world`, `bskyblock_world_nether`, `bskyblock_world_the_end`, `arene_boss`.
  - Multiverse-Inventories : groupe `skyblock` (ces 4 mondes, shares all), groupe `oneblock` à part, groupe `default` (lobby, lobby_nether, tntrun, buildbattle), les autres mondes ont chacun leur inventaire. `default-ungrouped-worlds: false` (règle de Thibault, vérifié le 17/11). BSkyBlock `reset.on-join/on-leave.inventory: false` : le skyblock ne vide rien.
  - `gamerule keep_inventory` = false dans `bskyblock_world` (et `oneblock_world`) : une mort fait perdre le stuff, sauf dans le vide (géré par EemiBank, voir plus bas). La gamerule n'a pas été changée (commandes console qui modifient un monde refusées à l'outil).
  - AuraSkills : actif SEULEMENT au skyblock (`bskyblock_world*`, `arene_boss`). Tout autre monde de jeu va dans `disabled_worlds` de `plugins/AuraSkills/config.yml` + `skills reload` (sinon XP et mana du skyblock ailleurs ; demande d'Esteban, 01/11). Messages FR : `messages/messages_fr.yml` (orthographe relue le 01/11).
  - Économie = S-Economy (coins ⛃, comptes dans `plugins/S-Economy/data.yml`), via Vault ; partagée avec le OneBlock. `economy.startingBalance: 4500.0` dans `plugins/S-Economy/config.yml` (Thibault, 29/10) : nouveaux comptes seulement. Pas de reload (changement pris au redémarrage). Console : `/economy give|take|set|reset|check <joueur> [montant]` (alias `/eco`, `/econ`). Permission des non-op non relevée.
  - Bazar = RoyalBazaar : `categories/mining.yml`, groupe `stone` contient `pointed_dripstone` (5) et `dripstone_block` (20) (lave infinie ; le chaudron est dans oddities). `bazaar reload` marche (non listée) → « 5 bazaar categories, 336 items ».
  - Menu `/sbmenu` = DeluxeMenus `gui_menus/skyblock.yml` (entrée Banque case 25, fiche profil avec portefeuille + banque). Tableau TAB `skyblock` (« Portefeuille », « Banque : %eemibank_bank% »).
  - PNJ lobby `SKYBLOCK` (709.5 49 665.5, ex-`comingsoon1`, lance `/island`) + hologramme `regles_skyblock` ; boussole : SkyBlock case 45 de `serverselector.yml`, sous-menu `iles.yml` (`[CONSOLE] dm execute %player% [player] ...`). Texte « Ton ile » du PNJ laissé sans accent.
  - Locales BentoBox : `locales/{BentoBox,Level,Warps,Challenges}/fr.yml` relues (01/11). Ne pas faire `bbox reload` (instabilité annoncée) : redémarrer.
  - Boss (datapack `/lobby/datapacks/eemicraft/.../function/boss/`) : 7 boss avec skin `eemicraft:boss_<id>` (correcteur, president, examinateur, directeur, bug, machine, wifi), cubes 16×16×16 dessinés par `EemicraftPack/build_pack.py` (section BOSSES, aperçus `EemicraftPack/boss_*_preview.png`), face avant au nord. Humanoïdes : papier avec `minecraft:item_model` en tête (Correcteur : casque de netherite remplacé, armure 8 → 11 ; Président : casque doré remplacé, armure +2 ; Examinateur, Directeur : ajouté, drop 0). Bug (silverfish), Machine (ravageur), Wi-Fi (breeze) : `Passengers` item_display tags `eemi_tout`,`boss_skin` (retiré par les `kill @e[tag=eemi_tout]` existants) ; `wifi/tick` met `view_range` à 0 pendant la coupure (#phase 1). `minecraft:reload` après modif ; un boss déjà en vie garde son ancienne apparence.
  - EemiCore (groupes) : `party.protected-worlds` (défaut `bskyblock_world*`, `oneblock_world*`, `abysse*`, `arene_boss`) : un membre de groupe qui s'y trouve n'est jamais emmené automatiquement (seulement « [SUIVRE] ») ; toute commande d'entrée dans un jeu tapée depuis ces mondes passe d'abord par le lobby (`party.lobby-command` `lobby`, relance après `party.lobby-delay-ticks` 30, annulée si le joueur n'est pas sorti). `party.no-follow: [skyblock, oneblock, abysse]`.
- **Pièges**
  - Stuff skyblock perdu après un mini-jeu (29/10) : cause = plugins qui vident l'inventaire dans le monde d'arrivée (ex. EemiBlockParty `leave(p, false)` sur `PlayerChangedWorldEvent` : `/is` depuis Block Party effaçait le stuff, puis MV-Inv sauvait le vide). Règle commune (`_commun/BRIEF_INVENTAIRE.md`) : `mayTouch(p)` = monde du jeu ou hub ; leave sur changement de monde = ne rien toucher ; téléporter AVANT de vider ; vérifier les tâches différées. Vérifié dans les jars : MV-Inv 5.3.6 traite `PlayerChangedWorldEvent` en LOW (avant les jeux) ; DeluxeHub redonne ses objets 5 ticks après l'arrivée.
  - Screaming BedWars vide l'inventaire AVANT de téléporter (les jeux maison téléportent puis vident) : un joueur emmené par son groupe depuis un monde protégé perdait son stuff → d'où `party.protected-worlds` d'EemiCore.
  - `default-ungrouped-worlds: true` (essayé le 03/11 pour le OneBlock) faisait partager l'inventaire du lobby à tous les mondes hors groupe (objets des jeux au hub) : remis à false, ne pas remettre.
  - BentoBox/fr.yml : une valeur contenant « : » sans quotes (ligne `protection-range-bonus: Bonus : [number]`, espace insécable) casse le YAML → mettre entre apostrophes ; valider avec SnakeYAML. Placeholders corrigés : date `dd`/`yyyy` (pas `jj`/`aaaa`) dans `commands.admin.info.last-login-date-time-format`, Warps `[number]` (pas `[numéro]`).
  - Laissés tels quels (01/11) : descriptions AuraSkills repairing/anvil_master incompréhensibles, éditeur admin de Challenges en traduction automatique.
- **À vérifier en jeu**
  - Skins des boss (orientation sur la tête, taille des cubes au-dessus des non-humanoïdes).
  - Nouveau compte S-Economy à 4 500.

## EemiSkyGarde (filet de sécurité de l'inventaire skyblock)

- **1.0.1 installée** le 26/11 (`a_deposer_installes/EemiSkyGarde-1.0.1.jar`), avec EemiIles : `/skygarde oublier <joueur> [raison]` (console/op) met `restore: false` et vide la copie d'avant commande, pour qu'un reset d'île ne soit pas annulé par la restauration automatique.
- **Avant** : 1.0.0 (en `.jar.old`) ; une restauration réelle vue dans les logs. Non vérifié : ordre réel avec MV-Inv, commandes lancées par DeluxeMenus `[player]` (peut-être pas vues par `PlayerCommandPreprocessEvent`).
- **Fonctionnement**
  - Source `EemiSkyGarde/`. Demande de Thibault : « sauvegarder puis remettre l'inventaire avec un loading sûr », indépendant des jeux. Mondes protégés : `bskyblock_world`, `_nether`, `_the_end`, `arene_boss`.
  - Départ du skyblock par téléportation (`PlayerTeleportEvent` LOWEST, avant MV-Inv) = sauvegarde complète (41 cases, coffre de l'Ender, XP, vie, faim) dans `plugins/EemiSkyGarde/players/<uuid>.yml` (`ItemStack.serializeItemsAsBytes` en base64) + historique de 10. Si l'inventaire est déjà vide au départ et qu'une commande a été tapée au skyblock moins de 3 s avant, on garde la copie prise juste avant la commande (`PlayerCommandPreprocessEvent` LOWEST).
  - Retour au skyblock depuis un autre monde : sauvegarde remise 3 ticks après (après les autres plugins), une seule fois par départ. Sauvegarde auto toutes les 60 s et à la déconnexion au skyblock. Joueurs en vanish ignorés.
  - Admin : `/skygarde liste <joueur>`, `restaurer <joueur> <n>` (joueur au skyblock ; l'inventaire actuel passe d'abord dans l'historique), `sauver <joueur>`, `reload`.
- **Pièges** : anti-duplication = pas de restauration après une mort au skyblock sans keepInventory, ni après un départ sans téléportation (réapparition, portail).

## EemiBank (banque du skyblock, deux monnaies façon Hypixel)

- **État** : 1.1.0 installée ; `/bank` ouvert par BanTeEs. Garde du stuff jamais vérifiée par une vraie mort dans le vide.
- **Fonctionnement**
  - Source `EemiBank/` (sur le PC d'Esteban). Dépend de Vault (VaultUnlocked 2.20.3 s'appelle « Vault », API MilkBowl 1.7 en provided), PlaceholderAPI en softdepend.
  - Portefeuille = solde Vault (S-Economy, bazar, HDV). Banque = solde à part dans `plugins/EemiBank/data.yml` (`<uuid>.bank`, `.name`, `.last-interest`), jamais perdu.
  - `worlds` = les 3 mondes `bskyblock_world*` seulement (Esteban, 01/11 : le OneBlock n'a ni banque ni argent, on y perd son stuff normalement).
  - Mort DANS LE VIDE (`only-void: true`, cause VOID de la dernière blessure) dans `worlds` : −50 % du portefeuille (`death-penalty.percent`), message + log console ; et stuff gardé (`keep-inventory.enabled`, `keep-inventory.all-deaths: false` = vide seulement : `setKeepInventory(true)`, drops vidés, `setKeepLevel(true)`, XP non lâchée, message « …mais tu gardes ton stuff »). Les autres morts au skyblock font perdre le stuff (gamerule inchangée). Avant ce plugin, aucune perte d'argent à la mort n'existait.
  - Intérêts (Esteban : « petits intérêts ») : 2 % de la banque toutes les 24 h réelles, versés à la connexion ou en jeu (contrôle chaque minute), une seule période à la fois (pas de cumul d'absence), plafond 10 000 par versement, 1re période au 1er dépôt.
  - `/bank` (`/banque`) : menu 27 cases (infos 4 ; déposer tout 10 / moitié 11 / montant 12 ; retirer montant 14 / moitié 15 / tout 16 ; fermer 22) ; montant tapé dans le chat (1500, 2k, 1,5m, tout, moitie, annuler). `/bank deposer|retirer <montant|tout|moitie>`. Admin : `/bank voir <joueur>`, `/bank set <joueur> <montant>`, `/bank reload`.
  - PAPI : `%eemibank_bank%`, `%eemibank_bank_raw%`, `%eemibank_wallet%`, `%eemibank_total%`. Branché : tableau TAB `skyblock`, `/sbmenu` case 25 (`[player] bank`).
- **À vérifier en jeu** : mort dans le vide au skyblock (stuff gardé, −50 % du portefeuille), versement des intérêts.

## EemiStagiaires (stockage des stagiaires AxMinions + XP AuraSkills)

- **État** : 1.0.0 installée. Rien de testé en jeu noté.
- **Fonctionnement**
  - Constat (bytecode d'AxMinions 1.0.21 décompilé CFR) : un stagiaire n'a AUCUN inventaire propre ; il remplit le coffre relié (champ privé `linkedInventory` de `com.artillexstudios.axminions.minions.Minion`), sinon `addToContainerOrDrop` jette au sol. Aucun événement de retrait. Le slayer ne stocke rien (le collector ramasse).
  - Source `EemiStagiaires/` (AxMinions en dépendance système `lib/`, API AuraSkills 2.4.0 de Maven Central). Toutes les 10 ticks, chaque stagiaire des `types` du config (miner, lumber, farmer, fisher, collector) SANS coffre relié reçoit par réflexion un inventaire de `taille` (9) cases ; AxMinions s'arrête avec « coffre plein » quand il est plein (`firstEmpty() == -1`). Avec un coffre relié : comportement normal, pas d'XP. crafter et seller exclus (besoin du coffre).
  - Contenu dans `plugins/EemiStagiaires/stockage.yml` (clé `monde;x;y;z`, toutes les 60 s et à l'arrêt).
  - Ouverture : bouton `stockage` (tonneau, slot 22) dans `gui.items` de `plugins/AxMinions/config.yml` (clé PDC `axminions:gui_item` = `stockage`) ; le plugin réécrit nom/lore (cases x/9, nombre d'objets).
  - Retrait seulement (clic gauche/shift = pile, droit = moitié). XP = `ratio-xp` (0.4) × table `types.<type>.xp` dans `types.<type>.competence` (miner → mining, lumber → foraging, farmer → farming, fisher → fishing, collector → `aucune` car il ramasse aussi ce que jettent les joueurs). Tables tirées de `plugins/AuraSkills/sources/*.yml`, divisées par le nombre moyen d'objets par bloc. XP via `AuraSkillsApi.get().getUser(uuid).addSkillXp(...)` (multiplicateurs appliqués), barre d'action. Ramasser le stagiaire (`PreMinionPickupEvent`) rend le stockage avec l'XP.
  - Logs : « Aucun stockage enregistre. » ; « Impossible de donner un stockage aux stagiaires » = champ AxMinions introuvable.
- **Pièges** : non vérifié que l'ordonnanceur d'AxMinions tourne sur le fil principal (sinon accès concurrent). Les stockages d'un stagiaire supprimé autrement qu'en le ramassant (île supprimée) restent dans stockage.yml.
- **À vérifier en jeu** : remplissage des 9 cases puis arrêt ; bouton slot 22 ; retrait + XP visible dans `/skills` ; impossible de déposer ; ramasser le stagiaire rend le contenu ; relier/délier un coffre ; survie au redémarrage.

## OneBlock (addon AOneBlock de BentoBox)

- **État** : AOneBlock 1.28.0 (Modrinth `aoneblock`), dans `/plugins/BentoBox/addons/` (addon, pas plugin). Joué par des joueurs. Ouvert : `/ob phases` et changement de phase jamais essayés par un non-op.
- **Fonctionnement**
  - Demande d'Esteban : un mode solo en plus du skyblock. Mondes `oneblock_world`, `_nether`, `_the_end`. Config `BentoBox/addons/AOneBlock/config.yml` ; locale `BentoBox/locales/AOneBlock/fr.yml` (non relue).
  - Commandes : `/ob` (`/oneblock`) créer/aller sur son île, `/ob phases`, `/ob count` ; admin `/obadmin` (`/oba`).
  - Permissions données au groupe `default` (LuckPerms, vérifiées) : `aoneblock.phases` (default FALSE dans l'addon.yml) et `aoneblock.island.setcount` (clic sur une phase dans `/ob phases` = `/ob setCount`, default OP). On ne revient qu'à une phase déjà atteinte, 5 min entre deux changements (`set-count-cooldown: 5`).
  - Équipes : `on-join.inventory: false` (on garde son stuff en rejoignant une équipe), `on-leave.inventory: true`, `kicked-keep-inventory: false` (un membre qui part ou est exclu perd son inventaire OneBlock) ; la propriétaire n'est jamais touchée.
  - Intégrations : groupe MV-Inventories `oneblock` (séparé du skyblock et du lobby ; « worlds does not exist » au démarrage car BentoBox crée les mondes après MV-Inv → `mvinv reload`) ; mondes désactivés de DeluxeHub ; AuraSkills désactivé dans les 3 mondes (+ `the_nether`) (Esteban : « je gagne de l'xp skyblock et de la mana ») ; tableau TAB `oneblock` (`%world%|-oneblock_world` : phase, blocs cassés, prochaine phase, niveau d'île ; pas de portefeuille ni banque). Économie Vault partagée avec le skyblock mais rien à acheter.
  - Lobby : PNJ FancyNpcs `OneBlock` (717.6 49 665.5, à côté du SKYBLOCK), nom `<gold><b>ONEBLOCK</b></gold> <gray>Un seul bloc</gray>`, `player_command ob`, yaw 180 ; hologramme `regles_oneblock` (717.58 52 665.55, `down-origin: true`) ; boussole `serverselector.yml` case 46 (bûche de chêne, `[CONSOLE] dm execute %player% [player] ob <delay=5>`).
  - Sauvegarde de l'île de Paloma (compte Livrepoche, 17/11, avant l'arrivée de Zhxn_ dans son équipe) : `/sauvegardes/oneblock-paloma-livrepoche-20261117.zip` = régions/entités/poi `r.-2.-1` et `r.-2.0` de `oneblock_world` (île centre -800 80 0, protection x -850..-751 z -50..49, phase Underground, bloc 1044) + `database/{Island,OneBlockIslands,IslandLevels,ChallengesPlayerData}/AOneBlocke4f1548b-24a8-4532-92bd-8686cc39308e.json` ; `/sauvegardes/oneblock-paloma-inventaire-20261117.zip` = profils MV-Inv `groups/oneblock` et `worlds/oneblock_world` (au dernier changement de monde). Restaurer serveur ARRÊTÉ ; les 2 régions contiennent les îles voisines : ne les remettre qu'avec l'accord des voisins, sinon recoller seulement l'île via un schematic tiré de la sauvegarde.
  - Morts (26/11, #152, choix d'Esteban) : `island.deaths.max` d'AOneBlock 10 → 999999 (le compteur `%aoneblock_deaths%` s'arrêtait à 10) et `deathpenalty` du Level 100 → 1 (une mort = 1 point = 1/100 de niveau ; à 0, `/ob level` n'affiche plus « (N morts) », Esteban la veut ; Level commun au OneBlock et au SkyBlock ; 1 actif depuis le redémarrage de 12:07, affichage de « (N morts) » dans `/ob level` pas encore vu en jeu). Ligne « Morts : %aoneblock_deaths% » ajoutée au tableau TAB `oneblock` (`tab reload` fait). Level réécrit son config au démarrage (commentaires perdus). Les morts au-delà de 10 avant le 26/11 n'ont jamais été comptées et sont perdues (Qorwinn : 10 au OneBlock, 442 morts vanilla tous jeux confondus, inutilisable) ; corriger à la main si besoin : `obadmin deaths set <joueur> <n>`. Pris au redémarrage de 11:46. Backups `/.ai-backups/plugins__BentoBox__addons__*__20261126-104402.yml`.
- Coffres des phases (26/11, carte #139 « rien dans les coffres ob desolation ») : AOneBlock tire une rareté puis un coffre de cette rareté dans `phases/<n>_<phase>_chests.yml` ; une rareté sans coffre donne un COFFRE VIDE. Désolation n'avait que RARE/EPIC → ajout de 3 COMMON + 3 UNCOMMON (clés '4'..'9', os, charbon, fer, nourriture, pousses…) ; Plaines, Grottes à stalactites, Mangrove, Grottes de soufre, Forêt pommelée n'avaient pas d'EPIC → coffre '50' EPIC ajouté (2 diamants, 2 pommes d'or, 12 fer, 8 fioles d'XP, 4 émeraudes). Backups dans `/.ai-backups/`. Pris au redémarrage suivant (non vérifié en jeu). Toute phase doit avoir au moins un coffre de chaque rareté.
- **Pièges**
  - TNT coupée sur les îles (27/11, 14:20, serveur arrêté) : `TNT_DAMAGE` et `BLOCK_EXPLODE_DAMAGE` à `false` dans `world.default-island-settings` d'AOneBlock (futures îles) et à `-1` (coupé ; 0 = autorisé) dans les 9 fichiers `database/Island/AOneBlock*.json` (backups `/.ai-backups/*20261127-1320*`). Avant, une TNT venue de l'extérieur cassait n'importe quelle île. Its_DyDi (île 800 -800) a cassé l'île de Thomasuuu (-800 -800, `AOneBlock6076f93a…`, avec LaBaleineMC) vers 13:40 avec une machine volante à TNT. Île RESTAURÉE le 27/11 vers 14:35 (serveur arrêté) : `r.-2.-2.mca` (region, entities, poi) de la sauvegarde auto de 13:11 (région écrite le 26/11 vers 23:15) remis dans `oneblock_world` ; fichiers détruits gardés dans `/sauvegardes/ile-thomasuuu-detruite-20261127/`. Pas encore revue en jeu ; île de MaZd_ (0 -800, `r.-1.-2` modifié à 13:43) peut-être touchée aussi, à faire vérifier. Îles OneBlock : 0 0 BanTeEs, 800 0 Qorwinn, -800 0 Livrepoche, 0 -800 MaZd_, 800 -800 Its_DyDi, -800 -800 Thomasuuu, 800 800 LeChatLyon, -800 800 Loulouteeee, 0 800 Enosik.
  - `papi parse` sur un joueur hors ligne fait planter Level (NPE normale).
  - Pertes de stuff du 03/11 (Qorwinn) : (1) téléportation OneBlock → monde hors groupe : le stuff partait dans le profil du monde d'arrivée ; (2) chef de groupe qui fait `/bw join` : EemiCore emmenait le membre, BedWars vidait avant de téléporter et MV-Inv enregistrait `groups/oneblock` vide. Corrigé par `party.protected-worlds` d'EemiCore (voir SkyBlock), validé par Esteban (emmenée au SkyWars sans rien perdre).

## EemiIles (reset d'île SkyBlock, paliers et MultiTool du OneBlock)

- **État** : 1.0.0 installée le 26/11 (`a_deposer_installes/EemiIles-1.0.0.jar`, cartes #138, #133, #140), avec EemiSkyGarde 1.0.1 ; branchement sur BentoBox et AOneBlock vu au démarrage. Rien testé en jeu.
- **Fonctionnement** :
  - Source `EemiIles/` (`fr.eemicraft.iles`, paper-api seul). Branché par réflexion (aucune dépendance de compilation) sur BentoBox 3.23.4 `world.bentobox.bentobox.api.events.island.IslandResettedEvent` et AOneBlock 1.28.0 `world.bentobox.aoneblock.events.MagicBlockEvent`, compteur `AOneBlock.getOneBlocksIsland(Island)` → max(`getLifetime`, `getBlockNumber`) (noms vérifiés au javap). Branchement retenté toutes les 5 s pendant 1 min ; logs « Branche sur … », état par `/iles info [joueur]` ; admin `/iles reload` (perm `eemiiles.admin`, op). Données `plugins/EemiIles/data.yml` (`reset-en-attente`, `paliers-payes`, `recompenses-en-attente`).
  - #138 reset d'île BSkyBlock : seul le joueur qui fait le reset est vidé (41 cases, curseur, coffre de l'Ender, XP), 10 ticks après son arrivée dans un monde `skyblock.mondes` (tout de suite s'il y est), après `skygarde oublier <joueur>`. Portefeuille S-Economy, banque EemiBank et entrées/sorties d'équipe jamais touchés (choix d'Esteban, 26/11 : « inventaire au reset seul »). À trancher : les membres de l'équipe, expulsés par BentoBox au reset, ne sont pas vidés.
  - #133 OneBlock : tous les 1 000 blocs magiques de l'île (`oneblock.paliers.tous-les`), 50 pièces + 100 xp EemiCore (`eemi give`) au joueur qui casse le bloc (`destinataire: casseur` ; machine = propriétaire, payé à la connexion s'il est hors ligne ; `membres` = chaque membre connecté). Anti-doublon par île ; les paliers déjà passés avant l'installation ne sont pas rattrapés.
  - #140 MultiTool : `/multitool` (`/mt`, perm `eemiiles.multitool` vraie par défaut), OneBlock seulement, un seul exemplaire (PDC `eemiiles:multitool`), incassable, composant `tool` (mineable pickaxe/shovel/axe + tags INCORRECT_FOR_<tier>). Niveau selon les blocs de l'île, sans paiement (choix d'Esteban : « le plus intéressant pour un joueur OB ») : 0 Bois, 250 Pierre, 1 000 Fer, 5 000 Diamant, 15 000 Netherite, puis 30 000 / 45 000 / 60 000 / 80 000 / 100 000 = Efficacité I à V (`multitool.niveaux`). Mis à jour au bloc magique, au changement de monde, à la connexion et à la réapparition. Hors OneBlock « en sommeil » (mine comme une main). Ne se jette pas, ne se range pas (coffre, cadre, porte-armure), doublon retiré, rendu après la mort.
  - Rien à déclarer dans DeluxeHub, TAB, AuraSkills ni MV-Inventories.
- **À vérifier en jeu** : logs de branchement, reset depuis le skyblock ET depuis le lobby (`/skygarde liste` : « restauration auto : non, reset d'ile »), palier 1000 payé une seule fois, vitesses et drops du MultiTool (obsidienne seulement au niveau diamant), mise en sommeil, bundle et stagiaires AxMinions non couverts.

## EemiAntiTnt (machines volantes à TNT sur les îles)

- **État** : 1.0.0 compilée le 27/11 (`a_deposer/EemiAntiTnt-1.0.0.jar`), PAS installée, rien testé en jeu. Demande d'Esteban après la destruction de l'île de Thomasuuu. Relue par un agent indépendant (18 points, les graves corrigés) ; restent à vérifier en jeu : tableaux 2×2/4×4 recréés au bon endroit, passagers (cheval monté, bateau) pas en double.
- **Fonctionnement** :
  - Source `EemiAntiTnt/` (BentoBox par réflexion, `Iles.java`, comme EemiIles). Mondes `mondes` : oneblock_world, bskyblock_world. Inactif tant que BentoBox n'est pas branché (rien n'est retiré).
  - Détection : un piston dont les blocs déplacés contiennent un piston/piston collant/observeur ET de la TNT (machine volante qui transporte de la TNT), ou une TNT allumée sans joueur à moins de 8 blocs d'une machine à slime/miel en mouvement (duplicateur embarqué). La machine est FIGÉE (pistons annulés dans `rayon-gel` 10), titre « Machine à TNT ! / Démonte-la : 10 s » aux membres de l'île, alerte aux admins (`eemiantitnt.admin`), compteur `recidives.yml`.
  - Après `delai-secondes` (10) : si la machine (blocs reliés à ceux de l'événement, 10 blocs max) contient encore de la TNT, elle est retirée (sans drop) et l'île du propriétaire est punie ; sinon rien.
  - Filet : aucun bloc poussé par piston ne peut sortir de la zone protégée de son île ; hors île, aucun assemblage slime/miel/piston ne bouge.
  - Punition (`Punition.java`) : joueurs présents en spectateur ; chunks chargés ; blocs sauvés en structures NBT (tuiles 32³, `punitions/<id>/tuiles/*.nbt`, contenus des coffres compris) ; entités copiées (`Entity#createSnapshot`, `entites.yml`, toute la hauteur) puis retirées ; pluie de TNT `pluie-secondes` (30) qui casse vraiment l'île (sphère `pluie-rayon` 3, `setType AIR` sans physique ni drop) ; puis 2 passes de pose des tuiles, entités recréées (passagers remontés), joueurs rendus sur une case sûre dans leur mode. Pendant toute la punition, dans la zone : pas d'eau qui coule, de sable qui tombe, d'objet au sol, de hopper/dropper. Dossier archivé dans `archives/` (gardé `garder-jours` 7).
  - Reprise : `state.yml` (phases SAUVEGARDE → PLUIE → RESTAURATION → FINI) ; au démarrage, PLUIE/RESTAURATION = restauration automatique, SAUVEGARDE = annulée (rien n'était cassé). Un joueur qui se connecte sur une île en attente passe spectateur ; mode de jeu rendu à la reconnexion (`retours.yml`). Une punition illisible n'est jamais effacée (nouvel essai chaque minute, nouvelle punition de la même île refusée).
  - `/antitnt` (op) : `statut`, `punir <joueur> confirmer` (test : sauvegarde, pluie, restauration), `restaurer <joueur> confirmer` (remet la dernière archive ; tout ce qui a changé depuis est perdu), `reprendre`, `annuler` (dégèle les machines), `reload`.
- **À vérifier en jeu (avant d'y compter)** : `/antitnt punir BanTeEs confirmer` sur l'île d'Esteban (0 0) avec coffres pleins, villageois, animaux, cadres, tableaux, torches murales, eau ; puis une vraie machine volante à TNT sur une île de test.

## EemiTresor (coffre au trésor du lobby)

- **État** : 1.1.0 installée (les notes décrivent la 1.0.0 ; contenu de la 1.1.0 non noté). Pose testée à la console ; ouverture et récompense pas encore vues.
- **Fonctionnement**
  - Source `EemiTresor/` (PC d'Esteban). Demande d'Esteban : coffre caché au hasard dans le lobby toutes les ~2 h, message sans coordonnées, 300 XP + 100 pièces.
  - Toutes les `interval-minutes` (120 ; 1er coffre 10 min après le démarrage ; il faut `min-players` 1 au lobby, sinon nouvel essai 5 min plus tard) : CHEST dans une case d'air du lobby, disque de rayon 130 autour de 663 650, y 40..120, à plus de 20 blocs du spawn DeluxeHub, dans un chunk déjà chargé, sol plein (pas feuilles/barrière/liquide), « caché » = bloc plein ou feuilles dans les 12 blocs au-dessus, « accessible » = chemin à pied (marches de 1) de moins de 60 cases vers l'air libre (BFS).
  - Message à tous, indice de direction depuis le spawn au bout de 20 min ; l'ancien coffre disparaît au suivant (« Personne n'a trouvé… »). Clic = coffre retiré, `eemi give %player% xp 300` + `eemi give %player% coins 100`, annonce, particules ; refusé en vanish (métadonnée `vanished`) ; incassable. Retiré à l'arrêt et au démarrage (`data.yml`).
  - `/tresor` (op) : `info`, `spawn` (relance aussi le minuteur), `remove`, `reload`. Tout réglable dans le config.
- **À vérifier en jeu** : ouverture du coffre et récompense.

## EemiCasino (Casino)

- **État** : 1.4.0 installée. 1.2.0 vue en jeu par Esteban (« c'est incroyable ») ; 1.3.x (nouvelle salle, éclairage, relance au Hold'em) et 1.4.0 (top des gros gains) pas encore vues en jeu.
- **Fonctionnement**
  - Source `EemiCasino/` (PC d'Esteban). Demande d'Esteban : casino « hyper addictif ». Jetons achetés en pièces EemiCore (non reconvertibles), boutique de cosmétiques exclusifs, classement des plus riches, bonus du jour, jackpot progressif.
  - Monde vide `casino` créé par le plugin, chunks -2..1 en forceload. Salle construite par le plugin (`Builder.java` + MemSink, bibliothèque `_commun/build` ; `buildSignature()` = `Layout.BUILD_VERSION` × 10 + modèles, salle reconstruite si elle change, au démarrage ou au `/casino admin reload`, ou par `/casino admin build`). Salle x/z -29..29, sol y 100, plafond y 113 ; machines à sous mur nord (z -29), video poker mur est (x 29), 3 blackjack à l'ouest (z -14/-2/10), roulette au centre (coupole et lustre), 2 Hold'em à l'est (z -12/4), caisse sud-ouest, boutique sud-est, galerie ouest + salon, bar, kiosques, portail sud ; arrivée 0.5 101 26.5. Éclairage : `light[level=15]` tous les 4 blocs en y 104, tous les 6 en y 111, et sous la galerie (Esteban : « ça manque de lumière »).
  - Aucune map de casino gratuite exploitable trouvée (CurseForge « Babydoekjes1's casino » CC 4.0 trop petite, les autres avec mod ou « All Rights Reserved »).
  - Croupiers : Mannequin non persistants, skin fourni par Esteban (texture signée MineSkin dans `dealer.texture/signature`) : 3 blackjack, 1 roulette, 1 caissier, 1 boutiquier. Postes = entités Interaction, panneaux = TextDisplay. Log attendu « croupiers : 6/6 ».
  - Jeux : 15 machines à sous 3D (retour 95,25 % hors jackpot, jackpot 1/8 000 alimenté par 3 % des mises ; 9 symboles 3D, levier) ; 6 video poker 3D Jacks or Better 8/5 (5 cartes à l'écran) ; blackjack aux règles du Casino de Monte-Carlo (6 jeux rebattus à chaque main, 3:2 ; croupier une seule carte au départ, tire à 16, reste à 17 ; son blackjack prend toutes les mises sauf face à un blackjack du joueur ; séparation jusqu'à 3 jeux, as séparés une carte chacun, as+10 = 21 ; assurance moitié de la mise payée 2 pour 1 ; quantité affichée de la carte = sa valeur en points ; partir en pleine main = rester ; cartes 3D posées sur la table) ; roulette européenne à mises multiples avec cylindre 3D (disque de 3 blocs en -1,5 / 0,5, bille) ; Hold'em 2 tables de 2 à 6 (10/20 et 50/100), pots annexes, 30 s pour jouer, relance au choix (case 51, montant dans le chat : 350, 2k, tapis, annuler) ; roue de la fortune (`Fortune.java`, une mise, 50 segments x0 (22), x0,5 (8), x1 (6), x1,5 (6), x2 (4), x3 (2), x5 (1), x10 (1), retour 96 %, `SEQ` = sortie de `fortune_sequence()` du générateur) ; Grande roue (4,5 blocs en x -8,93 z 13,5, x2/x4/x6/x12/x24 = 24/12/8/4/2 segments, roue partagée, menu `/casino` case 20) ; Plinko (planche 4 × 5 contre le mur x 10, z 11,5..15,5, 8 rangées, x5,6 / 2,1 / 1,1 / 0,9 / 0,5, retour 94,6 %, clic droit = bille, gauche = menu, 6 billes max, menu case 24).
  - Top « Plus gros gains » (`BigWins.java`, data.yml `big-top` + `big-top-init`) : une entrée par joueur (meilleur coup net, calculé dans `win()`), poker non compté. Menu `/casino` case 42, `/casino gains|records`, panneau TextDisplay en 0.5 105.7 21.5. Annonces : entrée au top au casino, record n°1 à tout le serveur + titre « ★ RECORD ★ ». Admin `/casino admin bigwins [remove <pseudo>|reset]`.
  - Config : `modeles-3d: true`, `modeles-flip: true`, `max-bet.roue/plinko: 5000` (`maxBet`), `plinko.multipliers`, `plinko.max-balls`, `join-from`.
  - Données `plugins/EemiCasino/data.yml` (jetons, stats, jackpot, `on-table` = tapis de poker rendus au démarrage après un arrêt brutal).
  - Commandes : `/casino` (depuis le lobby seulement), `/jetons`, admin `/casino admin give|take|set|jackpot|build|entities|reload|save|info|bigwins`. PAPI `%eemicasino_chips|chips_raw|jackpot|rank|daily|top_<n>[_name|_chips]%`, `%eemicasino_bigwin_<n>[_name|_amount|_amount_raw|_game]%`.
  - Achat de jetons : lecture de `%eemi_coins%` puis `eemi give <joueur> coins -N` à la console, contrôle avant/après. Boutique : cosmétiques EemiCore `casino: true` (`title_c_*`, `trail_c_or`, `hat_c_or`, `join_c_jackpot`), cachés et non achetables dans /boutique, donnés seulement par `eemi cosmetic <joueur> <id>`.
  - Intégrations : `games.casino` dans EemiCore (join `casino`) ; boussole `DeluxeHub/menus/ambiance.yml` case 22 (bloc d'or, `[COMMAND] eemi join casino`) ; AuraSkills `disabled_worlds`, mondes désactivés de DeluxeHub, tableau TAB `casino`. PAS de PNJ au lobby (Esteban : « dans la boussole, pas dans un PNJ »).
  - Pack : modèles et textures dessinés par nous (`EemiCasino/pack_src/gen_casino_pack.py`, puis `gen_casino_ui.py` pour 14 fonds de menus d'après SPECS, à garder alignés avec le code ; `pack_src/build_pack.py <pack en ligne> <sortie>` fusionne avec le pack EN LIGNE). Rien repris du mod « MineVegas Casino » (licence inconnue) : tout passe par pack + ItemDisplay, sans mod côté joueur. Icônes `eemicraft:casino/icone/<nom>` (`Icons.java`, 140) ; fonds dans le titre (`Gui.java`, police `eemicraft:casino`, U+E001 = -8, glyphe U+E101 + index, U+E002 = -169). La boutique garde les icônes Minecraft de son config.
- **Pièges**
  - Client 26.3 (javap de `DisplayRenderer` / `ItemDisplayRenderer`) : l'entité applique sa transformation PUIS `rotate(YP, π)` → `Models.right()` compense (`modeles-flip: true`) ; calculs dans le repère du modèle.
  - Sans le pack, `modeles-3d: true` donne des cubes violets.
  - Une machine (à sous ou video poker) n'a qu'un titulaire de son écran 3D ; un 2e joueur joue par le menu.
  - Le texte « Exclusivité du casino » est écrit en dur dans EemiCore pour `casino: true` : pour cacher un cosmétique non-casino, utiliser `available-until: "2000-01-01"`.
  - Limite connue : des comptes secondaires peuvent faire passer des jetons en perdant volontairement au poker.
- **À vérifier en jeu** : salle reconstruite (éclairage autour de la roue et du plinko), relance au Hold'em, top des gros gains et son panneau, orientation des cartes, numéro sous la bille, segment sous la flèche, superposition des fonds de menus.
- **Grade « Dieu du casino » (#143, 26/11)** : titre `title_c_dieu` (« &c&lDieu du casino », étoile du Nether) à 1 000 000 jetons, le plus cher de la boutique du casino (Roi du casino : 250 000). Ajouté dans `plugins/EemiCore/config.yml` (`cosmetics`, `casino: true`) et dans `shop` de `plugins/EemiCasino/config.yml` sur le serveur, puis `eemi reload` + `casino admin reload` ; aussi dans la config par défaut du jar (`EemiCasino/src/main/resources/config.yml`, pris à la prochaine version). Pas vu en jeu.
- **Poker (#52)** : la tranche du pot misée par un seul joueur (mise non suivie, adversaire à tapis plus court) est rendue comme « mise non suivie » à l'abattage (`refunds`) au lieu d'être annoncée comme un gain ; même chose dans `winByFold` quand le gros tapis quitte la table (`deposit` s'il est parti) ; on ne peut plus se coucher quand il n'y a rien à suivre (bouton gris, un FOLD sans mise devient une parole).

## EemiResto (Tycoon restaurant, carte #115)

- **1.2.0 installée** le 26/11 (`a_deposer_installes/EemiResto-1.2.0.jar`, carte #144, demande de Rémi ; redémarrage 11:17 sans erreur) : `/resto nom <nom>` (alias `renommer`, `name`) donne un nom au restaurant, affiché sur l'arche (« Nom » + « de Pseudo »), l'enseigne, `/resto visiter` et le classement (lore « Patron : … ») ; `/resto nom reset` = retour à « Chez Pseudo ». 2-24 caractères (lettres avec accents, chiffres, espace, ' & . ! -), codes couleur retirés, 1re lettre en majuscule, mots interdits (admin, modo, staff, eemicraft, serveur, owner, op), nom unique, 60 s entre deux changements. Admin : `/resto admin nom <joueur> <nom|reset>` (sans délai ni filtre). Fiche joueur : clé `resto-name`. Astuce donnée à la réclamation. Pas testé en jeu.
- **Avant** : 1.1.0 (en `.jar.old`, carte #133), redémarrage 10:23 sans erreur : renaissance = `eemi reward win` + `eemi give` 250 pièces + 300 xp (`renaissance.coins` / `renaissance.xp`, défauts du code tant que le config serveur ne les a pas) ; l'ancienne `rewards.rebirth-coins` (75) n'est plus lue.
- **Avant** : 1.0.1 (en `.jar.old`), pack du resto publié (`textures: true`). Rendu et correctif des portes pas encore confirmés en jeu.
- **Fonctionnement**
  - Tycoon « façon Roblox » (précision d'une joueuse). `/resto` (alias `/tycoon`, `/restaurant`), id EemiCore `resto`. Source `EemiResto/` (package `fr.eemicraft.resto`, bibliothèque `_commun/build` copiée en `fr.eemicraft.resto.build`).
  - Monde vide `resto` créé par le plugin (pas Multiverse), 120 chunks forceload (x -157..157, z -48..47), randomTickSpeed 0, midi fixe. Rue piétonne le long de x (chaussée y 63, trottoirs et lots y 64), place centrale x -13..12 avec fontaine et tour de l'horloge (toit G+18), collines. Arrivée 0.5 65 8.5. 16 parcelles 24×30 (paires au sud z 9..38, impaires au nord z -9..-38, tournées de 180°), colonnes x -116, -90, -64, -38, 14, 40, 66, 92.
  - Jeu : on réclame une parcelle en passant sous son arche « Réclamer ce restaurant ». Départ : dalle, tapis A, comptoir + caisse (dalle d'or), four à croque. 9 droppers (2 lignes de tapis), 5 améliorateurs (×1,5/×2), 41 boutons d'achat au sol (tapis rouge/vert + TextDisplay) qui construisent le resto. Plats = ItemDisplay non ramassables (40/resto, 400 au total, au-delà vendus sans affichage). On encaisse en marchant sur la dalle d'or. Revenu quand le proprio est connecté ailleurs, 40 % hors ligne plafonné à 6 h. Renaissance quand tout est acheté (×1,5 puis ×2…, prix +25 %, `eemi reward win` + 75 pièces). Simulation : 1er resto complet ~3 h.
  - Plats secrets : 1/250 par plat posé par un dropper concerné (`legendary-chance` 0.004), valeur ×100 (`legendMult`), annonce + 25 pièces à la 1re découverte : Brownie magique de Mehdi (dalle violette derrière la tour, 6 65 -35 → fours), BK d'Esteban (paillasson de la cuisine, local 19 25, 5 000 € → friteuse/grill), Saucisse de veau de Dylan (grill, 12 h-14 h Paris), Sushi de Mathilde (machine à sushis après 1 renaissance), Le p'tit wrap (bar à salades après 5 000 plats vendus).
  - Admin/tests : `/resto admin legendaire <id>`, `secret`, `acheter <joueur> <n|tout>`, `give`.
  - Pack : 14 sprites (`EemiResto/pack_src/gen_resto_pack.py`, aperçu `pack_src/apercu/apercu.png`), sous `assets/eemicraft/{items,models/item,textures/item}/resto/` ; fusion `build_pack.py <pack en ligne> <sortie>` (refuse toute collision). Pack publié de référence SHA-1 c9a5ab6f5d828189b54e2383e18614603e36ad1e.
  - Audit hors serveur `EemiResto/outil-audit/audit.ps1`.
  - Intégrations : AuraSkills `disabled_worlds`, mondes désactivés de DeluxeHub, TAB disable-condition, EemiAdmin `resto`/`tycoon`/`restaurant` bloqués en vanish, EemiCore `games.resto` (« &6Tycoon resto », CAKE, join `resto`, 1-16), boussole `menus/iles.yml` case 23 « Tycoon » → sous-menu `tycoon.yml` case 11 (`eemi join resto`). MV-Inventories : `resto` hors de tout groupe.
- **Pièges** : `onInteract` refusait tout clic droit sur un bloc (`useInteractedBlock DENY`), portes comprises (« on reste bloqué dedans ») → portes, portillons, trappes (tags DOORS / FENCE_GATES / TRAPDOORS, sauf fer) autorisés ; le reste (coffres, fours, plaques…) bloqué.
- **À vérifier en jeu** : ouverture des portes, textures du pack, plats secrets.

## Tycoon Fête foraine (EemiFete)

**État**
- **EemiFete 0.4.4 installée le 27/11** (redémarrage 13:53 sans erreur, `a_deposer_installes/` ; `fete admin reset Its_DyDi garder` fait à 13:54, il garde le parc 1) : faille exploitée par Its_DyDi : 5 coups sur la porte des toilettes = jackpot de François à CHAQUE fois (seule l'annonce était unique) ; maintenant une seule fois (« C'est libre maintenant… » ensuite). Même faille pour Fluffy (jackpot à chaque capture) : elle ne s'échappe plus une fois trouvée. Doigt de pied et crêpe au Nutella restent des lots rares répétables (0,4 % par tour). Nouvelle option `/fete admin reset <joueur> garder` : tout à zéro (argent, achats sauf ceux offerts, renaissances, objets perdus, nom) mais le joueur garde son parc.
- **EemiFete 0.4.3 installée le 27/11** (redémarrage 10:22 sans erreur, `a_deposer_installes/`) : textes du doigt de pied et du pot de Nutella de Julian réécrits en sous-entendus légers (demande d'Esteban, références internes connues et acceptées par la bande ; rien d'explicite, pas de mention de tiers). `Defs.SECRETS` (indices et messages de découverte) et messages du pot dans `clickEgg`.
- **Annonce Discord du 27/11** postée par Claude dans #🎲・update d'EEMICRAFT (10:19, mention @🔔 Annonces update) : fête foraine, maps au hasard, Bridge, Pitchout, bouton Prêt, Jump, page Saison et police ; texte dans `docs/discord/update-20261127.md`. L'annonce précédente (26/11 10:40) couvrait MultiTool, reset d'île, bouton Prêt, cache-cache, Infection, records du Jump, casino, boutique de saison, top 10, Pitchout, Bridge, radar, resto.
- **Ouverte à tous depuis le 27/11 (10:08)** : `droits jeu fete ouvert` ; ligne « En beta » retirée du survol de `DeluxeHub/menus/tycoon.yml` + `deluxehub reload`. Tags Bêta retirés à Livrepoche et Its_DyDi (`droits beta <j> off`) : plus aucun bêta-testeur (`lp search eemidroits.beta` = 0).
- **EemiFete 0.4.2 installée le 27/11** (redémarrage 10:10 sans erreur, `a_deposer_installes/` ; sortie de Fluffy pas encore vue dans la console) : VRAIE cause de #162 vue en console après l'installation de la 0.4.1 : `NoSuchMethodError AbstractCow.setVariant(Cow$Variant)` à chaque sortie de Fluffy (l'API de compilation a la méthode, pas le Paper du serveur) : Fluffy n'est jamais apparue depuis la 0.2.0. `setVariant` dans un try/catch `LinkageError` (Fluffy sans variante si absente).
- **EemiFete 0.4.1 installée le 27/11** (redémarrage 10:02 sans erreur, `a_deposer_installes/`, cartes #162 et #163 de BanTeEs) : (1) Fluffy jamais vue : le message de fuite partait en barre d'action, réécrite chaque seconde par le revenu, et le tirage était rare (12 % toutes les 45 s, 30 s dehors) ; maintenant message dans le chat + meuglement, tirage `fluffy-chance` 0.25 toutes les `fluffy-every` 20 s, `fluffy-seconds` 60 dehors (clés ajoutées au config du serveur le 27/11, backup `/.ai-backups/plugins__EemiFete__config__20261127-085838.yml`). Fluffy n'apparaît que si le propriétaire est connecté, a la mini-ferme et que son parc est animé (joueur à moins de 80 blocs). (2) Chèvre naine sur un toit, hors de portée : un clic (gauche ou droit, même objet du kit en main) en la regardant jusqu'à 48 blocs, sans mur entre les deux (`clickGoatFar`, `world.rayTrace`), compte comme un clic dessus. Pas testé en jeu.
- **EemiFete 0.4.0 + FetePack 030d810d** : installés le 27/11 (redémarrage 09:26 sans erreur ; allée version 310 et les 10 parcs reposés ; `a_deposer_installes/EemiFete-0.4.0.jar` et `a_deposer_installes/FetePack-030d810d.zip`). FetePack publié sur https://eemicraft.strator.gg/pack/FetePack.zip, SHA-1 vérifié en le retéléchargeant, `pack.sha1` mis à jour dans `/plugins/EemiFete/config.yml` (backup `/.ai-backups/plugins__EemiFete__config__20261127-082529.yml`). Stands pas encore vus en jeu. Demande d'Esteban : « l'eau, y'a des cases vides à côté » et stands en resource pack.
  - Cause de l'eau : `Rides.stand` posait de la vraie eau dans le stand de pêche, avec de l'air en u0+1 et u0+5 : elle coulait dans ces cases. Plus aucune eau en blocs.
  - Décor des stands en modèles du FetePack (`Rides.standProps`, `Rides.crepesProps`, ajoutés à `Draw.rides`, donc affichés par `Show` comme les manèges, à moins de `show-distance` d'un joueur) : pêche = bassin rayé et 5 canards qui tournent (`spinBob`) + peluches sur une étagère ; barbe à papa = machine + 4 barbes à papa ; chamboule-tout = 2 pyramides de 6 conserves + 2 paniers de balles ; tir = 5 cibles rondes, 2 carabines, peluches ; crêpes = 2 crêpières sur un plan de travail, assiettes de crêpes, pot de confiture (pas le pot de Nutella, objet secret). 12 modèles ajoutés dans `Models`, repli en blocs sommaire sans le pack.
  - `Draw.VERSION` = 3 : au démarrage, l'allée puis tous les parcs sont reposés en entier (l'eau disparaît).
  - Contrôles hors serveur : `essais.sh show` 123 408 coins, écart max 0,00003 ; `verifier_pack.py` 0 erreur ; aperçus `EemiFete/apercus/stand_*_pack.png`, `stands_pack.png`. Rien vu en jeu (orientation réelle des peluches et carabines, taille des canards).
  - Installation : déposer `FetePack.zip` à la racine du serveur, `publish_server_file_to_webhosting` vers `/httpdocs/pack/FetePack.zip`, vérifier le SHA-1 (030d810d7ce15eb9773b232ac715b02e72118a17) en le retéléchargeant, puis `pack.sha1` dans `/plugins/EemiFete/config.yml` (edit_file), jar 0.3.2 en `.jar.old`, redémarrage.
- **EemiFete 0.3.2** : installée le 26/11 (redémarrage 22:01, sans erreur, `a_deposer_installes/`, 0.3.1 en `.jar.old`) : « Julien » devient « Julian » dans les textes des objets perdus (doigt de pied, pot de Nutella ; correction d'Esteban). Les découvertes sont enregistrées par identifiant (`doigt_pied`, `nutella`) : rien à migrer.
- **EemiFete 0.3.1** : installée le 26/11 (redémarrage 20:41, sans erreur, `a_deposer_installes/`, 0.3.0 en `.jar.old` ; allée reposée en version 210 puis les 10 parcs en entier, 108 288 blocs chacun). Nettoyage du parc d'Esteban pas encore revu en jeu. Bug vu par Esteban après la reconstruction de 20:00 : herbes et fleurs sur les bâtiments de son parc. Cause : `Draw.street` sème la végétation de `Terrain` sur tout le terrain, parcs compris, et un parc n'est reposé que par différence avec son plan précédent (`lastPlans`) ; son parc était en cours de dessin à 20:00, la végétation est restée. Correctif : après chaque reconstruction de l'allée, `lastPlans` vidé et chaque parc marqué `forceFull` (reposé en entier, même s'il était en cours de dessin). Avec `Draw.VERSION` = 2, l'allée puis tous les parcs sont reposés au redémarrage d'installation.
- **EemiFete 0.3.0** : installée le 26/11 (redémarrage 19:52, sans erreur ni exception, `a_deposer_installes/`, 0.2.0 en `.jar.old`, même FetePack 154829bf ; toujours en bêta). Retours d'Esteban sur la 0.2.0 (« gros souci avec le truc au milieu niveau design » ; modèles 3D : RAS) :
  - **Kiosque de la place refait** : socle octogonal, marches, plancher et garde-corps en blocs (`Draw.kiosque`) ; colonnettes en fer forgé à chapiteau doré, toit conique rayé rouge et blanc à 32 secteurs, lambrequin, ampoules et flèche en pièces fines fixes (`Rides.kiosque`, posées par `EemiFete.spawnPlaza` via `Show(pl, List<Ride>)`, coordonnées monde). Vu en jeu et validé par Esteban le 26/11 (après la reconstruction de 20:00).
  - **Manèges montables** (« que les PNJ montent dans les manèges et nous aussi, sans rien contrôler ») : 148 places sur 8 manèges (`Ride.Part.seat`, contrôle hors serveur `essais.sh seats` : chaque place reste sur une pièce, écart max 0,57). Devant chaque manège, panneau « MONTER » (Interaction + TextDisplay au point `Ride.board`) : clic → place libre au hasard ; le joueur est passager d'un ItemDisplay vide (tag `fete_siege`) téléporté tous les 2 ticks (`RETAIN_PASSENGERS`, interpolation 2 ticks) ; accroupi = descente au point d'embarquement ; changement de monde ou déconnexion = place libérée. Visiteurs : à un carrefour, 1 fois sur 3, vont tout droit vers un manège à moins de 22 blocs et font un tour de 15 à 40 s. Config `rides.enabled|visitors|seat-y`.
  - Piège (26/11) : l'allée et la place ne sont reposées que si `Draw.VERSION` change ; la 0.3.0 ne l'avait pas monté, l'ancien kiosque en quartz est resté sous le nouveau toit jusqu'à `fete admin build maintenant` (console, 20:00, sans redémarrage). `Draw.VERSION` = 2 dans la source (prochain jar). Toujours le monter quand on touche à `Draw.street`/`plaza`/`kiosque`.
  - Limites connues : un achat dans le parc relance le spectacle et fait descendre tout le monde ; les visiteurs vont en ligne droite vers l'embarquement (peuvent traverser un décor) ; points d'embarquement jamais vus en jeu (peut-être dans un décor).
- **EemiFete 0.2.0** : installé le 26/11 (redémarrage 19:27, sans erreur ni exception, `a_deposer_installes/`, 0.1.0 en `.jar.old`). Mise en bêta au départ ; ouverte à tous le 27/11.
- **0.1.0** (16:49) : allée foraine posée au 1er démarrage (1 087 163 blocs en 1,6 s), puis les 10 parcs (108 288 blocs chacun). Esteban l'a testé (3 premiers achats) : RAS, sauf les visiteurs tous en skin Alex.
- **Nouveautés de la 0.2.0** (demandes d'Esteban, 26/11) :
  - **Skins variés** : 2 visiteurs sur 3 prennent le skin d'un joueur déjà venu sur le serveur (`visitors.max-skins` 40, liste `visitors.skins` en plus), les autres un skin par défaut du jeu (profil sans textures, UUID au hasard).
  - **3 attractions** : toilettes publiques (`Defs.TOILETTES`, cabine du milieu en porte de fer « OCCUPÉ »), crêpes (`Defs.CREPES`), mini-ferme (`Defs.FERME`, vache, moutons et chèvre sans IA). Il y a donc 34 achats ; économie `base-wait` 25, `growth` 1.155, soit environ 3 h 20 pour le 1er parc (aussi réglé dans le config du serveur).
  - **5 objets perdus** (`Defs.SECRETS`, `PData.secrets`) : à la 1re découverte, annonce à tous, 25 pièces EEMICRAFT, jackpot (100 tours de l'attraction, au moins 2 min de revenu) et +10 % de tickets pour toujours. Livre « Objets perdus de la fête » en case 7 du kit, avec les indices.
    - doigt de pied de Julian : lot rare de la pêche (`secrets.chance` 0,004 par tour) ;
    - pot de Nutella de Julian : pot caché au pied de l'arche nord de la place d'entrée (ItemDisplay + Interaction, `fete_nutella`), qui ouvre la crêpe légendaire au Nutella ;
    - toilettes de François : 5 coups sur la porte de fer en 15 s ;
    - vache Fluffy de Clarisse : vache laineuse (variante COLD) qui sort 30 s toutes les 45 s avec 12 % de chance, si la ferme est achetée et le propriétaire connecté ;
    - chèvre naine de Mathilde : chevreau perché sur le faîte du train fantôme (sinon sur le toit des toilettes) après une renaissance.
    - Admin : `/fete admin secret <joueur> <id>`.
  - **Pack séparé FetePack.zip**, namespace `fete:`. Généré par `EemiFete/outil-test/fr/eemicraft/fete/GenPack.java`, PNG écrit sans AWT, source `EemiFete/pack/FetePack.zip`.
    - Publié sur https://eemicraft.strator.gg/pack/FetePack.zip, SHA-1 `154829bfa667d402ce693bdcf088c987bbfd2d6b`, vérifié en le retéléchargeant.
    - Envoyé en entrant dans le monde `fete` (UUID fixe), retiré en sortant, comme RefugePack.
    - Contenu : modèles 3D de `Models` (cheval aux 5 robes teintes, nacelle, auto-tamponneuse, wagon, pot de Nutella, doigt de pied), atlas 64 × 64, teinte « dye ».
    - Modèle = ItemDisplay ; le client le tourne de 180° autour de Y juste après la transformation (vérifié au javap de `DisplayRenderer$ItemDisplayRenderer`), ce que compense la rotation de droite.
    - Sans le pack (`pack.enabled: false` + `/fete admin reload`), les manèges reviennent en blocs. `TestShow` vérifie les deux cas : 105 888 coins, écart max 0,00003 bloc.
  - Aperçus : `EemiFete/apercus/*_pack*`.
- **Réglages du serveur** (faits le 26/11, actifs au redémarrage) :
  - `games.fete` d'EemiCore (« &dTycoon fête foraine », FIREWORK_ROCKET, join `fete`, join-aliases `foire`/`feteforaine`, monde `fete`, 1-20) ;
  - sous-menu `tycoon.yml` de la boussole ;
  - monde `fete` dans AuraSkills `disabled_worlds`, dans les mondes désactivés de DeluxeHub et dans la disable-condition de TAB ;
  - EemiAdmin : `fete`, `foire` et `feteforaine` bloqués en vanish.
  - Backups dans `/.ai-backups/*20261126-1544*`.

**Fonctionnement**
- **Origine.** Demande d'Esteban (26/11) : un 2e Tycoon (« si c'est bien fait ça peut mettre une très grosse claque »). Source `EemiFete/`, conception `EemiFete/CONCEPTION.md`, rendus `EemiFete/apercus/` (parc animé, nuit, jour, vue d'ensemble).
- **Moteur.** Repris d'EemiResto : réclamation par l'arche, boutons d'achat au sol (fenêtre de 2), caisse (dalle d'or du guichet), renaissance, nom du parc (`/fete nom`), classement, visite, revenu hors ligne.
- **Différences avec le resto.**
  - Le revenu est théorique (tickets « ✪ »), versé chaque seconde au propriétaire connecté, où qu'il soit. Il n'y a pas d'objets qui circulent.
  - Des « +tickets » apparaissent au-dessus de chaque attraction à chaque tour.
  - Un parc complet tire des feux d'artifice.
- **Monde.** `fete` (vide, créé par le plugin, nuit fixe 17000, chunks forceload). Allée foraine de 23 de large avec guirlandes, place d'entrée (kiosque, arches), 10 parcs de 48 × 48 (`Geo`). Côté nord : rotation de 180°, jamais un miroir.
- **Économie** (`Econ`, simulée par `outil-test/.../TestEcon.java`) : `base-wait` 30, `growth` 1.17, environ 3 h 20 pour le 1er parc à plein revenu.
  - 12 attractions (`Defs.ATTRACTIONS`) ;
  - 5 multiplicateurs (orgue, mascotte, néons, VIP, arche) ;
  - 3 accélérateurs de 15 % ;
  - 31 achats, dont 4 donnés à la réclamation.
- **Manèges animés** (`Ride`, `Rides`, `Show`).
  - **Principe.** Chaque cube d'une pièce mobile est un BlockDisplay posé au pivot. Sa transformation (rotation JOML + translation) est renvoyée tous les `Show.STEP` = 10 ticks, pour la pose à t + 0,5 s, et le client interpole. Les pièces fixes (pieds de la grande roue, portique du bateau) sont posées une fois.
  - **Les 8 manèges :** carrousel, grande roue (nacelles droites), chaises volantes, bateau pirate, tour de chute, auto-tamponneuses, train fantôme, montagnes russes en tour de parc.
  - **Budget.** Environ 1 020 pièces pour un parc complet. Seuls les parcs à moins de `show-distance` (80) blocs d'un joueur sont animés, `max-shows` (6) au plus.
  - **Contrôle hors serveur.** `TestShow` vérifie que les coins des BlockDisplay tombent sur les boîtes du modèle (côtés nord et sud) : écart max 0,00003 bloc.
- **Visiteurs** (`Crowd`). Des Mannequin sans IA se déplacent par téléportation courte tous les 2 ticks, en marche aléatoire sur le graphe des allées. Leur nombre vaut 2 + le nombre d'attractions, plafonné à `visitors.max-per-park` (10).
- **Commandes.** `/fete` (`/foire`, `/feteforaine`) `menu|aller|visiter <joueur>|top|nom <nom>|quitter`.
  - Admin (`eemifete.admin`) : `/fete admin reload|info [joueur]|reset <joueur>|nom|give <joueur> <tickets>|acheter <joueur> <n|tout>|build [maintenant]`.
  - EemiCore : `eemi reward <j> fete play` toutes les 30 min, `win` à chaque renaissance (+250 pièces, +300 xp), paliers de tickets.
- **Outils** (`EemiFete/outil-test/`) : `TestRender.java` (vidages de blocs et boîtes tournées), `render_iso.py` (rendu isométrique jour/nuit et GIF), `TestEcon`, `TestShow`. Les 191 états de blocs ont été comparés aux blockstates du client 26.3 : tous existent.

**À vérifier en jeu**
- Charge réseau et serveur des BlockDisplay (environ 100 mises à jour par tick et par parc animé).
- Fluidité des manèges, rendu de nuit (luminosité forcée à 13, ampoules à 15) et sens de rotation des parcs du côté nord.
- Visiteurs : skins par défaut, description vide, rien de coincé.
- Construction de l'allée au premier démarrage : environ 1 million de blocs à 8 ms/tick.
- Popups de tickets et feux d'artifice.
- Bêta par EemiDroits.

# Modes persistants et en développement

## L'Abysse (EemiAbysse)

**État**
- Installé : EemiAbysse 2.5.1. Joué par plusieurs joueurs (Compagnies existantes), mais la plupart des ajouts depuis la 1.2.0 n'ont pas été vus en jeu (voir « À vérifier »).
- Ouvert : en strate 3, les petites Braises nées d'une division n'ont pas la marque de l'Abysse et meurent dans les murs (vu, non corrigé). Pas de PNJ au lobby ni d'entrée dans EemiCore (statut, /jouer). Les ~45 objets/machines des strates 4-5 et les objets ajoutés depuis la 1.1.0 n'avaient pas de texture avant la 2.5.0 (147 modèles depuis) : à vérifier en jeu.

**Fonctionnement**
- Mode persistant choisi par Thibault (01/11) : on descend strate par strate, on mine, on transforme à son usine (façon Satisfactory), on vend à une bourse commune. Monnaie propre « éclats » ⬥ (sans lien avec EemiCore), progression permanente, nom « L'Abysse ». Conception détaillée (machines, recettes, quêtes, métiers, paliers, strates, bourse, menus) : `EemiAbysse/CONCEPTION.md` (sections 4.2, 4.2 bis, 6, 11 bis, 11 bis², 11 ter, 11 quater, 11 quinquies, 11 sexies, 11 undecies, 11 duodecies). Ne pas la recopier ici.
- Source `EemiAbysse/` : `Gen` (générateur), `Worlds`, `Store` (YAML), `Items`, `Bourse`, `Machines`, `Mobs`, `Quests` (quêtes, métiers, carnets), `Npcs`, `Menu`, `Txt`, `Reset`, `Belts`, `Campements`, `Profondeurs`.
- Commandes : `/abysse` (`/aby`), `/bourse`, `/abysse guide`, `/abysse tpa [joueur]` / `tpaccept` / `tpdeny`, `/abysse recommencer` (alias `reinitialiser`). Admin `eemiabysse.admin` : `reload` (affiche « gains xN »), `give <j> <objet> [n]`, `donner <j> <éclats>`, `debloquer <j> <strate>`, `quete <j> <n>`, `skip <j>` (alias `passer` : termine la quête en cours avec sa récompense), `page <j> <strate> <page>` (vraie page lisible), `gardien <strate>`, `tp <n|antre>`, `build`, `bourse reset`, `reset <strate>`, `reinit <j> [confirmer]` (`confirmer` obligatoire à la console).
- Mondes créés par le plugin (WorldCreator, pas Multiverse) : `abysse` (Bordgouffre : place ronde autour d'un gouffre de rayon 11 en 0 0, sol y 64, nuit fixe ; reconstruite si `HUB_VERSION` change et `hub.build: true` ; tomber dans le gouffre y < 40 = camp de la strate 1), `abysse_concessions` (parcelles tous les 256 blocs, 16 par ligne, sol y 63, 33×33 puis 49/65/97 pour 2 000/8 000/25 000 éclats), strates `abysse_s1` Les Galeries, `abysse_s2` La Champignonnière, `abysse_s3` Les Forges Englouties, `abysse_s4` La Mer de Cristal, `abysse_s5` Le Néant (fin du contenu, gardien Varenne). Strates chargées à la demande, déchargées après 5 min sans joueur. Dossiers serveur : `/lobby/dimensions/minecraft/abysse_s<n>`.
- Générateur : bedrock en y 0 et 117-119, camp en y 59 (cloche = remonter), antre à 320 blocs du camp par la galerie principale (jamais bouchée), sol y 40, rayon 20, autel = deepslate renforcée (clic = réveiller le gardien) ; filons = blocs bruts au sol, incassables à la main ; campements = tonneau en (8, y, 8) du chunk. Seuil de minerai `Gen.oreAt` 0,76 (Thibault, « un peu plus de minerai »). Argent et soufre (S3) cassables seulement à la pioche en acier (Mineur 10) ou abyssale (`Gen.hardness`, `Gen.pickTier`). Harnais de test hors serveur dans le scratchpad (`gentest/GenTest.java`, faux ChunkData par Proxy) : ~5-6 ms/chunk, ~20 % de vide.
- Minerai : repousse (bedrock pendant 180 s ± 25 %, `minerais.*`, `minerais.yml`) ; cassé à la pioche ou à la dynamite = directement dans le sac (au sol si plein). L'Aimant de mineur n'a plus d'effet (retiré de la brocante, ceux achetés restent).
- Remise à neuf des strates à 12:00 et 21:30 heure de Paris (`reset.*`, classe `Reset`) : joueurs remontés, monde déchargé sans sauvegarde, dossier supprimé, régénéré à la descente suivante (même graine), foreuses reposées (`Machines.restoreDrills`). Garde-fou : suppression seulement si `getWorldFolder()` s'appelle exactement `abysse_s<n>` (testé OK avec `abysse reset 1`). Les dossiers WorldGuard/AuraSkills/MV-Inv de la strate ne sont pas touchés.
- Machines = blocs vanilla non-conteneurs (lanterne marine générateur, meule concasseur, chaudron laveur, briques du Nether sculptées fonderie, quartz sculpté raffinerie, table de forgeron alliageur, table d'archerie assembleur, grille de cuivre entrepôt, tuf sculpté quai d'expédition, grille de cuivre oxydée quai de réception, magnétite foreuse, mousse + eau Serre, établi Cuisine, cuivre ciselé ciré normal/exposé/oxydé Groupeur/Séparateur/Trieur, table de cartographie Table des foreuses). Simulation chaque seconde sans charger de chunk ; entonnoir sous la machine = liaison (mémorisée), entonnoir qui pointe dedans = alimentation (vidé toutes les 2 s). Énergie 1 000 par générateur, 40 par charbon. Usine active tant qu'un membre est connecté + `offline-hours` (12). Entrée plafonnée à 64 PAR OBJET (`machines.input-cap`). Modules de l'Assembleur sauvés dans `modules`, filtres du trieur dans `filters`. Déblocages par niveau d'Ingénieur (`Machines.UNLOCK`, niveau du joueur qui achète) et par strate ouverte (`Machines.STRATA_UNLOCK`, ex. Alliageur après la strate 2). Recettes à niveau requis : `Machines.RECIPE_REQ`.
- Foreuses : 4 modèles (normale, renforcée, induction, Vide), « niveau N/4 » affiché ; renforcée obligatoire à partir de la strate 3 (`Machines.REINFORCED_FROM`), `Machine.tier` ; texte au-dessus = modèle, membres et nom de la Compagnie, état. Champ `dest` : quai de réception choisi (menu du quai « Foreuses reliées »), sinon monte-charge commun ; casser un quai remet ses foreuses au commun. Table des foreuses (3 500 chez Margaux) : liste, téléportation, ramassage (Maj + clic).
- Convoyeurs : terracotta émaillée magenta posée dans le sens du regard, 8 objets/s, objets en item_display ; rendu par item_display `eemicraft:abysse/convoyeur` (classe `Belts`, positions dans le PDC des chunks, monde `abysse_concessions` seulement).
- Bourse : plancher doux à 62 % (`plancher-doux`), remontée demi-vie 2,5 h, pression ÷ (1 + 0,5 × (joueurs actifs - 1)), plafond horaire, l'achat ne bouge plus le cours, achat à 125 %, 2 commandes du jour. Les anciennes clés `plancher` / `demi-vie-heures` du config serveur sont ignorées. `plugins/EemiAbysse/bourse.json` (toutes les minutes, lu par le bot/site) : `base` = base × gains, + `market_index`, `market_change_pct`, `change_pct`, `buy_price`, `base_raw`.
- `gains` (config, défaut 0,6, absent du config serveur) multiplie tous les éclats gagnés (bourse, monstres, gardiens, quêtes, paliers, carnets) ; prix d'achat des boutiques et agrandissements inchangés (Thibault, 02/11 : « baisse le niveau d'argent gagné »).
- PNJ de Bordgouffre : Gaspard (quêtes, kit), Bastien (pioche et épée de base gratuites, une à la fois ; lampe, fusées, kit de réparation, blocs ; plus d'équipement bronze/acier, fabriqué à l'Assembleur), Margaux (machines, convoyeurs 16 pour 80, quai de réception), Prosper (brocante : Totem de garde 5 usages = TOTEM_OF_UNDYING sans `death_protection`, Larme de guérison, Trèfle du prospecteur, Boussole du dernier souffle, Lampe éternelle ; usages = max_damage + lore), Léon (monte-charge, `npcs.lift`). Liftier (villageois `liftier`) dans chaque Concession revendiquée, 3 blocs à l'est de l'arrivée, recréé toutes les 5 s.
- Étoile du Nether « Menu de l'Abysse » : toujours dans le sac dans l'Abysse, un seul exemplaire (doublons retirés partout chaque seconde, retirée hors de l'Abysse), indéplaçable, gardée à la mort ; menu en 3 lignes (Se déplacer / Progresser / Commerce et Compagnie) : Changer de strate, Rejoindre un joueur, Bourse filtrée « Mon sac », Compagnie (membres, inviter, quitter/exclure avec confirmation), Éclats, Guide. Retour/Fermer en bas de tous les menus (`eemicraft:lobby/retour|fermer`). Bilan de retour après `bilan.minutes-min` (30) min d'absence. Repère vers Gaspard tant qu'on ne lui a pas parlé.
- Quêtes, métiers (Mineur / Ingénieur / Chasseur, 1 à 50, courbe `Quests.need` = 100 × 1,15^(L-1), paliers tous les 5 niveaux `Quests.PALIERS`) : voir CONCEPTION.md. Indice des gardiens (`guardianHint`) dans la barre d'action quand la quête en cours est un gardien.
- Carnets : tonneaux de campement PERSONNELS (classe `Campements`, `campements/<uuid>.yml`, époque par strate remise à zéro par la remise à neuf) : butin propre à chaque joueur, page non lue garantie dans un tonneau à page, page d'office après 3 tonneaux sans page (`carnets.garantie`), flèche vers le campement le plus proche pendant la quête des carnets.
- Monstres : dégâts et ciblage entre monstres de l'Abysse annulés, renforts zombies/noyés (`REINFORCEMENTS`) refusés, vie du monstre frappé dans la barre d'action. Strate 3+ (`Mobs.HARD_FROM`) : armure, résistance au recul, monstres en plus autour de chaque joueur ; armure non-Abysse percée (+15 % de dégâts par pièce, +30 % plus bas ; acier/abyssal non concernés). Valeurs actuelles (Thibault 22/11, #57) : S4 PV 72/54/60, armure 8, recul 0,4, +2 par joueur ; S5 PV 90/35/70, armure 10. Réglables : `mobs.pv.<id>`, `mobs.armure.<strate>`, `mobs.recul.<strate>`, `mobs.bonus-par-joueur.<strate>`. S3 : Forgeron calciné 80 PV, Braise vivante 50, Souffle des forges 60 (effets Wither/feu/Faiblesse).
- Gardiens : Contremaître (S1), Reine Mycélienne (S2), Forgeron Oublié (S3, 1 800 PV voulus, offrande à l'autel 16 lingots d'argent pur + 4 circuits, `Mobs.OFFERINGS`), Colosse de Cristal (S4), Varenne (S5). PV par joueur en plus : +60 % (+80 % pour le Forgeron). Sbires marqués `sbire`, plafonnés (`gardiens.max-sbires` 6 + `sbires-par-joueur` 2, `max-sbires-absolu` 12), retirés en fin de combat ; pas d'apparition naturelle dans l'antre pendant un combat.
- Crafts vanilla d'armes/outils/armures (tags SWORDS, PICKAXES… + arc, arbalète, bouclier, masse, trident, réparation) et table de forgeron vanilla bloqués dans les mondes de l'Abysse ; le stuff vanilla déjà possédé n'est pas retiré.
- `/abysse recommencer` : écran de confirmation (Maj + clic). Seul dans sa Compagnie : Compagnie, éclats, machines ET foreuses supprimées, Liftier retiré, parcelle abandonnée telle quelle (`nextPlot`). Avec d'autres membres : on quitte seulement la Compagnie.
- Intégrations : groupe Multiverse-Inventories `abysse` (abysse, abysse_concessions, abysse_s1..s5, shares all) ; mondes désactivés de DeluxeHub, disable-condition TAB (tableau latéral propre), `disabled_worlds` d'AuraSkills ; EemiCore `party.protected-worlds` contient `abysse*` (un membre de groupe dans l'Abysse n'est jamais emmené, `party.no-follow` aussi). Boussole : sous-menu `iles.yml`, L'Abysse (magnétite, `[COMMAND] abysse`) ; mention « NOUVEAU » retirée le 17/11.
- Pack : 147 objets avec `item_model eemicraft:abysse/<id>` (liste `EemiAbysse/ids_modeles.txt`, dessins `EemicraftPack/art_abysse.py`, planche `EemicraftPack/abysse_planche.png`) ; chaque machine posée reçoit une item_display de son cube (échelle 1,004, centrée sur le bloc vanilla qui garde collision, minage, entonnoirs ; lumière = max des voisins, générateur 15, recalculée toutes les 2 s). Les minerais des strates restent vanilla.

**Pièges**
- MV-Inventories charge avant EemiAbysse, qui crée ses mondes ensuite : avertissement « group 'abysse' does not exist » au démarrage. Faire `mvinv reload` après chaque redémarrage et après la première descente dans une strate neuve, sinon le partage d'inventaire n'est peut-être pas appliqué.
- Paper plafonne max_health à 1 024 : PV des gardiens plafonnés (`Mobs.MAX_HP`) et dégâts reçus divisés par PV voulus / PV réels (`Fight.toughness`). Avant ce correctif, l'autel plantait (`IllegalArgumentException: Health value`).
- `Material.isAir()/isSolid()` et les constantes de `Biome` / `Villager.Profession` passent par le registre (absent hors serveur, à éviter dans un initialiseur statique) : le générateur a ses propres tests d'air. `Material.CHAIN` n'existe plus en 26.3 (`IRON_CHAIN`).
- Convoyeur : ne JAMAIS remplacer le modèle vanilla de `magenta_glazed_terracotta` dans le pack global (fait en 1.4.x, il s'affichait dans le Hunger Games) ; règle générale de Thibault (17/11) : pas de retexture vanilla dans le pack global. Sens : flèche du dessus vanilla vers le nord pour facing=south = opposé de `facing` = sens du transport (vérifié dans le jar client).
- `/abysse give <j> page_carnet` donne une page SANS référence, illisible : utiliser `/abysse page`.
- Menu d'une recette sans ingrédient (Serre) : `setLength(length - 3)` plantait (`StringIndexOutOfBoundsException`), corrigé (« rien (eau à côté) »).
- Textes en double au-dessus des machines (cause supposée : chunk « non chargé » avant le déchargement des entités) : `Machines.single()` adopte ou supprime les TextDisplay/ItemDisplay existants (clé `machine_label`).
- Doublons de l'étoile : cause non prouvée (étoile au curseur ou dans la grille d'artisanat) ; d'où le nettoyage de tous les emplacements.
- Une fouille de tonneau à la fois (ancienne `Fouille` retirée au profit de `Campements`).
- Le minerai plus dense ne touche que les chunks générés après le changement (les strates sont de toute façon régénérées deux fois par jour).
- Abandonné : Aimant de mineur (inutile, minerai direct au sac) ; vente d'équipement bronze/acier chez Bastien (Thibault : « enlève les tools payants du shop si on peut les crafter »).
- Choix de Thibault : strates 1-2 rapides (2-3 h, on laisse), strates 3+ longues façon Satisfactory / Hypixel SkyBlock ; métiers jugés « trop cheatés » → rééquilibrés (repère : juste avant la Reine, Ingénieur 13, Chasseur 8, Mineur 6). Strate 3 jugée trop facile (« easy avec une armure en diamant ») → durcie.

**À vérifier en jeu**
- Inventaire partagé en descendant en strate / en allant à la Concession (groupe MV-Inv `abysse`).
- Serre, Cuisine, modules, dynamite, étoile unique, guide, convoyeurs (rendu item_display, sens), Groupeur/Séparateur/Trieur, Table des foreuses, quais reliés, Liftier, tpa, `/abysse recommencer`, indice des gardiens.
- Strates 3, 4, 5 (difficulté, gardiens, offrande), bourse rééquilibrée, campements personnels, textures des 147 objets.

## Hors Cours (EemiHorsCours)

**État**
- Installé : EemiHorsCours 1.2.1 (avec EemiMeubles 1.0.2 et le pack qui contient le vestiaire refait et les 21 premières répliques de la voix). Sessions lancées par Enosik sans exception (« Ecole : 26 lumieres, 10 portes », « Niveau 1 : 564 meubles, 5 fusibles ») ; voix, vestiaire, screamer, ambiance pas encore vus/entendus en jeu.
- Ouvert : à l'arrêt du serveur pendant une session, `forceLoad(false)` n'est pas appelé (172 chunks restés forceload une fois → `forceload remove all`) ; à corriger dans le plugin. Niveaux après le Couloir pas codés (« Fin de la version de test »). Répliques 22 à 72 en sous-titres.

**Fonctionnement**
- Campagne horreur façon Escape the Backrooms dans les lieux de l'EEMI, solo/coop 1-4, une session à la fois. Conception : `EemiHorsCours/CONCEPTION.md`. Source `EemiHorsCours/`.
- Choix de Thibault (06/11) : que des vrais éléments de l'école. Niveaux prévus : prologue (vraie map), Couloir, Salle Serveur (fictive, gardée car école d'info), Salle de cours, Zone repas, Bureau du directeur. Monstres : Loïc (le commercial) et l'Intervenant. Pas d'Open Space ni d'amphi, pas de minuteur, pas de version escape game. Référence Notion « jeux de l'eemi » : UNIQUEMENT la page « Pièces » (9 visuels dans `EemiHorsCours/references/salles/`) ; rien d'autre du Notion (ancien escape game, code du coffre, énigmes, trame) sauf la Moustache (boss de fin et voix qui guide, révélée seulement au combat final) et Louis (PNJ amical avec ses bières, zone repas).
- Commandes : `/horscours` (`/hors`) `join|leave` ; admin `reset|niveau <0|1>|loic`, `point <depart|lampe|craie|cafe|passage>` (position du joueur ou bloc visé, sauvé dans `ecole.points`), `peur [joueur]` (screamer de test). Join réservé à `eemihorscours.play` (op) = verrou admin. Boussole : sous-menu `ambiance.yml` (`[COMMAND] horscours join`, lore « EN TEST »).
- Monde `horscours` créé par le plugin (vide) ; déclaré dans les mondes désactivés de DeluxeHub, la disable-condition TAB, `disabled_worlds` d'AuraSkills. Pas géré par MV-Inventories : le plugin retire lui-même ses objets marqués en quittant le monde ou à la connexion ailleurs, et remet la survie au hub.
- École OFFICIELLE (build fini par Thibault le 12/11, d'après le plan d'évacuation « EEMI 59 rue Denuzière Lyon, Hall 3, 2e étage », 24 px = 1 bloc) : bâtiment x 300..337, z 0..29, sol y 63, intérieur y 64..67, plafond y 68. Salle 01 x 301..314 z 1..11, Salle 02 x 301..311 z 13..28, bureau du directeur x 316..322 z 1..7, bureau du staff x 324..330 z 1..7, cagibi de l'imprimante x 332..336 z 1..7, sas de l'escalier x 332..336 z 9..12, couloir x 313..336 z 9..15, réunion x 313..321 z 17..28, cuisine x 323..336 z 17..28, petit local x 334..336 z 17..21. Sauvegarde : `/sauvegardes/horscours-ecole-officielle-20261112.zip`. Générateur d'origine de la base : `EemiHorsCours/ecole/gen_base.py`.
- Prologue (classe `Ecole`) : le plugin ne modifie aucun bloc de l'école, tout passe par `sendBlockChange`. Relevé des lumières et portes au début de session. Points par défaut : départ couloir 324.5 64 12.5, lampe salle 02, craie salle 01, café cuisine (brewing stand), passage cagibi. Prise de la lampe → coupure de courant en cascade ; café → télés qui grésillent ; passage dans la baie réseau. Chunks de l'école forceload pendant la session.
- Ambiance (classe `Ambiance`) : néons qui grésillent, éclairs, stroboscope au café, frayeurs toutes les 20-45 s (porte qui claque, chuchotements, Loïc au loin visible par un seul joueur). Screamer façon FNAF (police `eemicraft:horscours_screamer`, U+E000 tête de Loïc, U+E001..E004 frames, U+E005 parasites, U+E006 visage rouge, U+E00A/E00B tremblement ; nausée, cris). Musique en couches (catégorie AMBIENT : deep dark, disques 13/11, nappes), battements de cœur quand Loïc est à moins de 16 blocs.
- Niveau 1 « Le Couloir » (x 1000.., labyrinthe 11×11 cellules de 5×5, généré à chaque session ; idée C « labyrinthe vivant » choisie par Thibault) : `Maze` (cellules à thème couloir/bureau/salle de cours/réserve/coin pause, meublées avec EemiMeubles en ItemDisplay non persistantes + barrières, seulement contre des murs fixes), ~28 % de cloisons mobiles déplacées toutes les 18-40 s (`maze.walls-min/max`) hors de vue, à plus de 7 blocs, sans couper le labyrinthe (BFS). `Niveau1` : 5 fusibles sur des étagères, armoires à cadenas (pince coupante dans un tiroir, 2,5 s, bruit qui attire Loïc via `Loic.hear`) ou à digicode (code à 3 chiffres sur un tableau blanc d'une autre cellule), tiroirs à fouiller, armoires vides = cachettes (accroupi pour sortir, joueur caché masqué aux autres par `hidePlayer`, Loïc l'ignore sauf s'il l'a vu entrer), 7 mannequins (`maze.mannequins`), chaises qui se tournent. Sortie = porte en fer.
- Loïc : ItemDisplay `eemicraft:horscours/loic_idle|walk1|walk2`, avance seulement si personne ne le regarde, plus court chemin dans la grille, 6,2 blocs/s (config speed 0.62, catch-up 0.8, `loic.yaw-offset: 180`) ; attrape = « Vous avez deux minutes ? » + screamer + retour au départ. Skin source : `EemiHorsCours/references/skins/loic.png` ; modèles générés par `EemiHorsCours/pack/gen_loic.py` (repart du pack publié).
- Lampe : bloc de lumière envoyé aux joueurs, batterie 4 min. Sans lampe allumée après la coupure et au niveau 1 : Cécité (`lamp.blind-without-lamp`), parade contre luminosité max / fullbright Lunar (le brouillard ignore le gamma). Plus aucun objet en surbrillance : reflet (particule) seulement sous le faisceau.
- Voix de la Moustache : textes `EemiHorsCours/VOIX_MOUSTACHE.md` (72 répliques), sons `eemicraft:horscours.moustache.NN` (sous-titres dans le chat si le son manque), durées dans `src/main/resources/voix_durees.txt` (log « N enregistrees »). Chaîne : `EemiHorsCours/voix/traiter_voix.py <mp3> <sortie> --libs <pylib> [--n N]` (Python numpy/scipy/soundfile, ffmpeg refusé par les garde-fous ; 1-62 effet « talkie », 63-72 « révélation » ; OGG mono 44,1 kHz + `durees.txt` + `rapport.txt`), puis `voix/ajouter_au_pack.py <pack> <dossier_ogg> <sortie>` (sons dans `assets/eemicraft/sounds/horscours/moustache/NN.ogg`, fusion de `sounds.json` en gardant les `ui.*`). Répliques 1-21 tirées de `Sans titre 1.mp3` (corrélation 0,64) ; extraits sans effet dans `EemiHorsCours/voix/decoupe/sans_effet/` pour vérifier à l'oreille.

**Pièges**
- Ne JAMAIS relancer `eemibb:ecole_base` (désactivée, ne fait plus qu'un `say` ; elle effaçait la boîte x 298..339 z -2..31) ni poser/casser de blocs dans cette boîte.
- Texture d'un modèle d'objet dans `textures/entity/` = violet et noir : la mettre dans `textures/item/` (ou `block/`) ; textures Hors Cours dans `textures/item/horscours/`.
- Les objets du jeu restaient dans l'inventaire en quittant (monde hors MV-Inv) : d'où le retrait des objets marqués.
- Abandonné : ancienne école générée en x 0..56 (Thibault : « maps nulles »), remplacée par l'école officielle.
- Armoire d'origine « ignoble, trop petite » (le joueur dépassait) : refaite en vestiaire métallique de 2,4 blocs.

**À vérifier en jeu**
- Voix (découpage jamais écouté), screamer, ambiance, lumières qui clignotent, vestiaire et cachette (vue de l'intérieur), orientation des meubles et du tableau, hauteur des objets sur les étagères, sens de Loïc.

## Meubles 3D (EemiMeubles)

**État**
- Installé : EemiMeubles 1.0.2 (45 meubles au catalogue, dont les `horscours_*`). Utilisé par Hors Cours ; pose manuelle pas testée en jeu.

**Fonctionnement**
- Demande de Thibault : meubles 3D des visuels Notion « Pièces », avec assise. Source `EemiMeubles/` ; `gen_meubles.py` génère les modèles (`eemicraft:meubles/<id>`, boîtes colorées par une palette 16×16 + textures de face), le catalogue `src/main/resources/meubles.yml` et les aperçus `EemiMeubles/apercus/` (planche.png). Nouvelles couleurs de palette : toujours ajoutées EN FIN (steel...).
- `/meubles` (perm `eemimeubles.build`, op) = catalogue + Marteau du builder. Meuble en main + clic droit sur un bloc = posé face au joueur (yaw à 90° près) ; mural (télé, baie, panneau sortie, affiche) dos au mur cliqué ; « surface » (écran, portable, machine à café, micro-ondes) sur le meuble cliqué (descendu de 3 px). Marteau : clic droit = tourner de 90°, accroupi + clic droit = retirer.
- Un meuble = ItemDisplay persistante (PDC meuble, uid, yaw, cases + blocs d'origine) + barrières sur ses `cases` (remplacent air/tapis, remis au retrait) + une Interaction par case. Assise (poufs, banquettes, fauteuils, chaises, tabouret, canapé) : siège invisible (ItemDisplay non persistante) + passager, s'accroupir = se lever ; hauteur via `assise.decalage`.

**Pièges**
- Pack : on repart TOUJOURS du pack publié, puis `gen_loic.py` et `gen_meubles.py` ajoutent leurs fichiers ; `EemicraftPack/build_pack.py` ne connaît ni horscours ni meubles.

**À vérifier en jeu**
- Rotation des cases des meubles de 2 cases (table, bureau, écran sur pied, canapé), hauteur de l'assise, échelle dans le menu (display gui).

## Dernier Refuge (EemiRefuge, pack Refuge)

**État**
- Installé : EemiRefuge 0.2.4 (zone `bourgade`, modèle prêt). Ouvert à tous depuis le 15/11 (Thibault). Étape 1 « socle jouable » assemblée ; peu joué : colonnes d'extraction, panneaux, vague unique, kits, menus pas encore vus en jeu.
- Ouvert : objet `seconde_chance` absent (solo = mort directe), plans d'armes absents. Bugs et retouches notés au fil des tests, à corriger en une grosse repasse (Thibault) : `EemiRefuge/A_CORRIGER.md`.

**Fonctionnement**
- Mode zombie d'extraction en COOP PUR (pas de PvP), progression permanente : Refuge commun, planque perso, expéditions de 15-25 min en escouade de 1 à 4 dans une copie neuve d'une zone, fouille, bruit, extraction (sinon le sac reste au sol à la mort). Conception : `EemiRefuge/CONCEPTION.md` (section 0 = décisions de Thibault du 13/11, elles priment ; questions ouvertes à la fin). Contrat entre chantiers : `EemiRefuge/ETAPE1_CONTRAT.md` + interfaces `fr.eemicraft.refuge.api` ; compilation par chantier avec `-DbuildDir=target_<x>`.
- Décisions de Thibault (13/11) : épée en bois au départ, améliorable ; arme à feu tardive avec très peu de balles (l'épée reste l'arme principale) ; plusieurs familles d'armes ; DA Minecraft ; 25 min ; jour/nuit au choix du chef ; vrai matchmaking (party auto, sinon interface inviter/rejoindre) ; planque visitable par paliers + déco libre ; partage seulement par `/trade` (refusé si l'autre n'a pas l'acte), objets liés à l'acte (anti-smurf) ; Ferraille ; mort = sac qui reste + assurance ; mise à terre ; PAS d'infection ; combat moderne (pas d'OCM, pas d'EemiBlocage) ; +0..+5 et gravures ; sac à cases verrouillées ; maps importées (Hameau/Bourgade style Minecraft, Port/Hôpital/Épicentre plus modernes). Fouille « poussée » sur de vrais supports (étagères `*_SHELF`, cadres, pots décorés, bibliothèques ciselées, porte-armures), AUCUN objet qui flotte, aucune texture cassée ; ItemDisplay acceptés s'ils passent la salle d'essai.
- Monde `refuge` (construit par le plugin) : Passeur, Armurier, Notaire (Mannequin), réserve virtuelle, boutique `refuge-shop.yml`, assurance ; escouades = groupe EemiCore lu par réflexion, sinon escouades du mode ; file publique ; menus au style « navigator » (`EemiRefuge/references/style_navigator.png`, fonds en police du pack dessinés par `pack/gen_ui.py`, planche `apercus/menus.png`, menus seulement dans les mondes `refuge*`).
- Expéditions (`expe/`) : zones `zones/*.yml`, instances WorldClone `refuge_bourgade_modele` / `_2` / `_3`, butin `loot.yml`, Marée, extraction, mise à terre, sacs persistants `sacs.yml`, assurance ; `/refuge expe|zone`. Bilan.ferraille = 0 (on vend au Refuge), XP créditée par l'expédition.
- Zone 1 `refuge_bourgade` = Mosslorn quartier A (x -380..-131, z -350..-101 ; crédit milkshake_mp3, usage autorisé avec crédit), importée par `mv import` ; magnétites/cibles/blocs de redstone/chêne pâle du décor remplacés par des blocs neutres dans la copie du mode. `/refuge_bourgade.zip` gardé à la racine du serveur. Fichier de zone serveur : `/plugins/EemiRefuge/zones/bourgade.yml`. Maps candidates : `EemiRefuge/MAPS_CANDIDATES.md` (Gendry's Tavern et Hospital : licence pas claire ; Mosslorn, Tideworn, Abandoned City : autorisées avec crédit), analyse dans `maps_refuge/`.
- Extraction : vagues plafonnées (`extraction.vagues` 1, `extraction.zombies` 8, surchargeables par zone `extraction-vagues` / `extraction-zombies` ; Thibault 15/11 : « une seule vague de 8, pas de spawn infini ») ; pas d'apparitions d'ambiance dans le cercle ni de horde pendant l'extraction. Repérage : colonne de particules de 40 blocs (verte, orange pendant l'extraction), panneau flottant TextDisplay ×2,5, boussole vanilla pointée sur l'extraction la plus proche (`setCompassTarget`, aucune boussole donnée).
- Kits : kit de départ à la 1re arrivée (épée en bois, 4 conserves, 3 bandages, 8 torches ; profil `kit.recu`) ; kit de secours (épée en bois + 2 conserves) si on revient sans arme de corps à corps, au plus toutes les `kit.secours-minutes` (30).
- Zombies : 9 types (+ élites), skins `pack/gen_zombies.py` (`ZOMBIES.md`, `apercus/zombies.png` : corps commun + tenues `equippable` `refuge:zombie_<type>[_elite]` ; Blindé = attribut ARMOR 15 au lieu de l'armure de fer). Saignement des Coureurs (Thibault : « hyper violent ») : 35 % par coup (50 % élite), 9 s (12 s élite), 1 PV / 3 s ; clés `saignement.*` (défauts dans le code). La gravure Saignement sur les zombies reste à 1 dégât/s.
- Armes : à feu sur armures de cheval, dague/couteaux/ferraille sur pépite de fer (`item_model refuge:<id>`, liste `EemiRefuge/pack/item_models.txt`) ; rechargement touche F. Tenue en main validée par Thibault (« variante 1 », 3e personne rotation [0,90,0] translation [0,3.5,0] échelle 0.7 ; 1re personne [0,90,10] [1.13,3.2,1.13] 0.6 ; `GUN` dans `EemiRefuge/pack/gen_refuge.py`, `VARIANTES` pour refaire un essai).
- Salle d'essai (monde `refuge_essai`, midi fixe) : `/refuge essai [quitter|armes|build]` ; armurerie, arène (zombies + élites, horde, défilé, figer, tout tuer, bassin), stand de tir, salle des meubles (étagères, coffres, pots, bibliothèque ciselée, sable/gravier suspects, coffre-fort, porte barricadée, cadavre, vitrine des blocs), galerie du pack. Liste : `EemiRefuge/A_VERIFIER.md`.
- Permissions : `eemirefuge.play` (vrai par défaut ; un refus LuckPerms sur `default` existait pendant les tests, retiré le 15/11), `eemirefuge.essai` (op ; `/refuge essai` sans give/reload/expe/zone ni `essai build`, donnée à MaZd_ et Qorwinn), `eemirefuge.admin` (`/refuge give <j> <objet>[+niv] [n]`, `reload`, `essai build`, `expe`, `zone`).
- Pack séparé `refuge:` (`RefugePack.zip`, publié sur https://eemicraft.strator.gg/pack/RefugePack.zip), empilé sur le pack EEMICRAFT, envoyé par le plugin (40 ticks après connexion, UUID fixe) SEULEMENT dans les mondes du mode et retiré en sortant (`pack.mode`, `pack.mondes`, `addResourcePack` / `removeResourcePack(UUID)`). Mise à jour : déposer le zip (`/plugins/RefugePack.zip`), `publish_server_file_to_webhosting` vers `/httpdocs/pack/RefugePack.zip`, SHA-1 dans `pack.sha1` du config, `refuge reload`. Vérification obligatoire : `EemiRefuge/pack/verifier_pack.py <zip> [zip...]` (0 erreur, aucune collision).
- Retextures de blocs vanilla (seulement dans ce pack séparé, voulu par Thibault) : `EemiRefuge/pack/gen_blocs.py` (part des textures du jar client 26.3), table `EemiRefuge/pack/BLOCS.md`, planche `EemiRefuge/apercus/blocs.png`. Étagères = un type de pièce par essence, tonneau = caisse clouée, coffre = caisse militaire, coffre en cuivre = cantine, pot décoré = jerrican, bibliothèque ciselée = classeur, vault = coffre-fort (inventaire virtuel), sable/gravier suspects = gravats, chêne pâle (planches) = barricade, magnétite = radio, cible = balise SOS, bloc de redstone = générateur ; ambiance : verres sales, végétation terne, lanternes qui clignotent, planches et briques usées.
- Intégrations : groupe MV-Inventories `refuge` (refuge, refuge_bourgade, _2, _3 ; `mvinv reload` après création du monde `refuge`) ; DeluxeHub mondes désactivés et AuraSkills (+ `_modele`, `refuge_essai`), TAB, EemiAdmin. Boussole : `iles.yml` (36 cases) « Dernier Refuge » (tête de zombie, `[COMMAND] refuge`) slot 22, Retour 31 ; mention « NOUVEAU » retirée le 17/11. Pas de PNJ au lobby.

**Pièges**
- Ne jamais reconstruire la salle d'essai automatiquement au démarrage (entités des chunks pas encore chargées → doublons) : seulement `/refuge essai build` tapé EN JEU dans la salle.
- Étagères vanilla présentes dans paper-api 26.3 (`org.bukkit.block.Shelf`, `ShelfInventory`, `OAK_SHELF`...) ; leur interaction (prendre, échanger) n'est pas vérifiée en jeu.
- Une map qui contient magnétite, cibles, blocs de redstone ou chêne pâle en décor les verrait devenir radios / balises / générateurs / barricades : les remplacer dans la copie du mode.

**À vérifier en jeu**
- Liste complète dans `EemiRefuge/A_VERIFIER.md` (sections Étape 1) ; notamment : interaction des étagères, salle d'essai reconstruite (lumière, étiquettes), retextures de blocs, tenue des armes, extraction (colonnes, panneaux, boussole, vague unique), kits, menus, escouades, assurance, sacs persistants.

# Transversal

## Parties en parallèle (WorldClone, modèles, max-instances)

- **État** : en place dans tous les jeux maison concernés depuis le 29/10/2026 ; les modèles `<monde>_modele` se créent au démarrage (« Copie <x>_modele prete »). Aucune partie 2 n'a été vue en jeu (au dernier fait connu). Liste de tests : `A_VERIFIER.md`.
- **Fonctionnement** :
  - Choix de Thibault : gestionnaire commun + refonte de chaque jeu, un seul serveur. Utilitaire `_commun/WorldClone.java`, copié dans chaque plugin ; consigne complète `_commun/BRIEF_MULTIPARTIE.md`.
  - Partie 1 = monde d'origine ; partie n = copie `<monde>_n` faite depuis `<monde>_modele` (copie propre refaite à chaque démarrage), détruite quand elle ne sert plus ; copie aussi `plugins/WorldGuard/worlds/<monde>`.
  - Commun à tous les jeux : clé `max-instances` (3), admin `/<jeu> parties`, `start|stop [n]`, `spectate [joueur]`. Plus d'inscription « pour la suivante » : nouvelle partie si possible, sinon « Toutes les parties sont pleines ». Statut EemiCore agrégé (plus d'état `starting` envoyé, donc plus d'annonce « La partie démarre »).
  - Cas particuliers : Murder = salon d'attente commun (monde `murder`), seules les maps sont copiées ; Bingo = salon `eemingo` commun ; UHC/EEMINGO = pas de copie, chaque partie génère `uhc_n` / `bingo_n` ; HG = modèles ~430 Mo, recopiés à chaque démarrage (~0,7 s par modèle, « Modele … pret en N ms »).
  - Chaque copie se déclare partout : mondes désactivés de DeluxeHub (`_2`, `_3`, `_modele`), disable-condition TAB (`_2`, `_3`), OCM `old` et EemiBlocage `worlds` (`_2`, `_3` des mondes déjà en old), EemiCore `games.<id>.worlds`, `chat.disabled-worlds` (Murder), `flight-worlds` (ex. `hideseek_2`, `hideseek_3`), AuraSkills `disabled_worlds`.
  - Structure d'un monde 26.x : `region/ entities/ poi/ data/minecraft/*.dat data/paper/{metadata,level_overrides,persistent_data_container}.dat paper-world.yml`.
  - Jeux tiers : BedWars (Screaming) gère plusieurs arènes en même temps ; TNT Run et Build Battle aussi par arènes ; Loup-Garou : une seule partie.
- **Pièges** :
  - Paper 26.3 refuse une copie qui garde `data/paper/metadata.dat` (« World minecraft:<x>_modele is a duplicate of another world and has been prevented from loading ») → `WorldClone` ne copie jamais ce fichier.
  - Mémoire : 90-94 % de 4 Go juste après le démarrage (avec le skyblock) ; chaque copie chargée en ajoute. La RAM est passée à 8 Go (vu le 01/11).
- **À vérifier en jeu** : lancement réel d'une partie 2 / 3 ; que le générateur vide est bien repris par `WorldCreator.copy` (non vérifié) ; avertissement « La copie … a ete chargee depuis … » à surveiller (signale un mauvais dossier ; jamais vu jusqu'ici).

## Inventaires perdus entre mondes (skyblock / mini-jeux) — méthode

- **État** : correctif appliqué à tous les jeux maison concernés (29/10/2026) + filet EemiSkyGarde 1.0.0 installé. Ordre réel avec MV-Inv non vérifié en jeu.
- **Fonctionnement** :
  - Skyblock = BentoBox/BSkyBlock ; source envoyée par Thibault (`Téléchargements/eemicraft-skyblock-source-2026-10-29.zip`). BSkyBlock `reset.on-join/on-leave.inventory: false` : le skyblock ne vide rien.
  - Multiverse-Inventories : groupe `skyblock` (bskyblock_world, _nether, _the_end, arene_boss, shares all), groupe `default` (lobby, lobby_nether, tntrun, buildbattle), les autres mondes ont chacun leur inventaire ; `default-ungrouped-worlds: false` (doit le rester : à `true`, tout monde absent de groups.yml rejoint le groupe du lobby et ses objets s'y retrouvent — cause des tickets #50/#53, remis à false le 10/11).
  - Règles (consigne `_commun/BRIEF_INVENTAIRE.md`) : `mayTouch(p)` = monde du jeu ou hub ; quitter le jeu à cause d'un changement de monde = ne rien toucher ; téléporter AVANT de vider ; revérifier le monde dans les tâches différées.
  - Vérifié dans les jars : MV-Inv 5.3.6 traite `PlayerChangedWorldEvent` en priorité LOW (avant les jeux) ; DeluxeHub redonne ses objets 5 ticks après l'arrivée. EemiAdmin met l'inventaire de côté PAR MONDE (`saved-inventories.<uuid>.<monde>`).
- **Pièges** (bugs réels trouvés le 29/10) : vidage dans le monde d'arrivée sur leave (cause initiale : EemiBlockParty `leave(p, false)` sur `PlayerChangedWorldEvent` → `/is` depuis Block Party effaçait le stuff skyblock, puis MV-Inv le sauvegardait vide ; idem Bateaux, Pitchout, UHC `dropAndEliminate`, Murder `eliminate`) ; `spectate` qui vidait avant de téléporter ; join depuis le skyblock qui vidait avant de téléporter (EEMINGO) ; `sendToHub` d'un inscrit resté au skyblock (SkyWars, HG) ; HG `leave` qui lâchait au sol l'inventaire du monde d'arrivée (duplication) ; spectateurs remis en survie dans le monde d'arrivée.
- **EemiSkyGarde 1.0.0** (source `EemiSkyGarde/`, demande de Thibault « sauvegarder puis remettre l'inventaire avec un loading sûr ») : filet indépendant des jeux.
  - Mondes protégés : arene_boss, bskyblock_world, _nether, _the_end. Départ du skyblock par téléportation (`PlayerTeleportEvent` LOWEST, avant MV-Inv) = sauvegarde complète (41 cases, coffre de l'Ender, XP, vie, faim) dans `plugins/EemiSkyGarde/players/<uuid>.yml` (`ItemStack.serializeItemsAsBytes` en base64) + historique (10). Si l'inventaire est déjà vide au départ et qu'une commande a été tapée dans le skyblock < 3 s avant, la copie prise juste avant la commande (`PlayerCommandPreprocessEvent` LOWEST) est gardée.
  - Retour au skyblock depuis un autre monde : sauvegarde remise 3 ticks après, une seule fois par départ. Pas de restauration (anti-duplication) après une mort au skyblock sans keepInventory ni après un départ sans téléportation (réapparition, portail). Sauvegarde auto toutes les 60 s et à la déconnexion. Joueurs en vanish ignorés.
  - Admin : `/skygarde liste <joueur>`, `restaurer <joueur> <n>` (joueur dans le skyblock ; l'inventaire actuel part d'abord dans l'historique), `sauver <joueur>`, `reload`.
- **À vérifier en jeu** : ordre réel avec MV-Inv ; commandes lancées par les menus (DeluxeMenus `[player]`) qui ne passent peut-être pas par `PlayerCommandPreprocessEvent`.

## Pièges transversaux trouvés en cours de route

- Tirage des maps (26/11, demande d'Esteban : « tout bien en aléatoire dans TOUS les modes ») : dans les jeux à plusieurs maps, la map est tirée au hasard quand le 1er joueur entre dans une salle d'attente VIDE (nouveau tirage si elle se vide puis se remplit) et jamais la dernière jouée, même après un redémarrage (clé `last-map` sauvée). Installées le 26/11 (redémarrage 15:20, toutes chargées sans erreur, `a_deposer_installes/`) : EemiHideSeek 1.6.1 (cause du « toujours le village » : map reconstruite au démarrage → repli forcé sur `village` ; `map.yml`), EemiElytra 1.1.1 (`data.yml`), EemiBoatRace 2.4.1 (`records.yml`), EemiSheepWars 4.7.1 (« rose » n'est plus exclue ; « Chargement de la map » pendant la reconstruction ; `data.yml` nouveau), EemiPitchout 2.2.3 (« desert » n'est plus exclu ; `data.yml` nouveau), EemiPaintball 1.9.1 (`last-small`, `last-big`), EemiMurder 1.14.1 (`last-map`, `last-small-map`, `data.yml` nouveau ; partie 1 seulement), EemiSkyWars 1.2.4, EemiHungerGames 1.5.4, EemiWizards 1.4.2 (`data.yml` nouveaux ; copies `_2`/`_3` inchangées sauf Wizards). Rien testé en jeu. Une seule map (rien à faire) : Spleef, Dé à coudre, Infection, Soleil, Bridge, Towers, Block Party, TNT Tag/Wars, Bow Spleef, PvP Run, Balls of Steel ; JumpLeague, UHC, Bingo, Speedrun = monde/parcours neuf à chaque partie.

- Blocs qui ne tombaient plus nulle part (28/10) : EemiTntWars et EemiSheepWars appelaient `setDropItems(false)` sur TOUS les blocs cassés du serveur (leur `canEdit` renvoyait true hors de leur monde). Corrigé (seulement dans leur monde). Leçon : tout filtre d'événement global doit d'abord tester le monde du jeu.
- `forceload remove all` tapé à la console SANS `execute in` a visé l'OVERWORLD (lobby), pas `tntwars` comme on le croyait (17/11) : ce qui y était forceloadé avant est inconnu. Toujours préfixer `execute in minecraft:<monde>`.
- Un fichier de données copié du jar vers le dossier du plugin une seule fois (ex. parcours du Jump) n'est plus relu depuis le jar : un changement dans le jar doit être reporté dans la copie du serveur.

## Joueur invulnérable et EemiReparer

- **État** : EemiReparer 1.0.0 compilé (source `EemiReparer/`), JAMAIS installé (absent de /plugins au 22/11). Correctif EemiAdmin demandé à Thibault, non fait à ce jour (EemiAdmin installé = 1.1.2).
- **Fonctionnement** : à la connexion (2 s après), au démarrage pour les connectés et à chaque arrivée dans un monde de `hub-worlds` ([lobby]), un joueur en survie/aventure non vanish (métadonnée `vanished`) perd l'invulnérabilité d'entité, avec un avertissement console « Invulnerabilite retiree a X (…) : laissee par un autre plugin ». `/reparer <joueur>` (op/console).
- **Pièges** : cas LaBaleineMC (15/11) : `data get entity LaBaleineMC Invulnerable` = 1b (drapeau d'entité, sauvé dans son .dat), `abilities.invulnerable` 0. CAUSE : il était en vanish (EemiAdmin rend invulnérable) et a été déop pendant ce temps ; sans la permission il n'a pas pu sortir proprement du vanish. EemiRefuge et EemiAbysse innocentés. À faire dans EemiAdmin : sortir du vanish (et retirer l'invulnérabilité) quand un joueur perd `eemiadmin.vanish`. Avec ce drapeau les coups ne déclenchent même pas d'événement ; un /kill raté peut venir d'un monde où un plugin annule tous les dégâts (ex. `infection`).

## Skill « builder-minecraft », bibliothèque de construction et audits

- **État** : skill et bibliothèque en place (16-17/11, Esteban). Esteban : « rien à redire » sur les maps refaites (détails à retravailler plus tard).
- **Fonctionnement** :
  - Skill du projet `.claude/skills/builder-minecraft/` : `SKILL.md` (méthode, cibles chiffrées, interdits, boucle de contrôle, boîte à outils Java), `reference/regles.md` (règles A-L : workflow, volumes, façades, toits, dégradés, terrain, arbres, éclairage, code, level design, 10 styles, échelle), `reference/palettes.md` (14 palettes), `reference/mesures.md` (6 maps de builders mesurées), `reference/sources.md`, `reference/budgets.md` (règles et gabarits de la veille), `exemples/`, `outils/analyze.py` (`python -I analyze.py <fichier.schem|.pbm> [joueurs]`), rendus dans `out/`. À CHARGER avant d'écrire ou de modifier un builder de map. Skills gratuits évalués (Vibecraft, Schematica, minecraft-world-generation, chapmanjw/minecraft-java-fabric-claude-plugin) : aucun ne génère en Java dans un plugin, rien installé.
  - Bibliothèque `_commun/build/` (package `fr.eemicraft.build` : Noise simplex, Palette en grappes, Shapes/Sdf, Facade 3 plans, Roof avec débord, Stairs/Connect.fix, Terrain, Tree, RecordingSink, SchemWriter, Check, `bukkit/BukkitSink` ; mode d'emploi `_commun/build/LISEZMOI.md`), copiée dans chaque plugin sous `fr.eemicraft.<jeu>.build` (une bibliothèque par jar). Modèle de builder : `EemiElytra/.../Builder.java` (strates par simplex).
  - Chaque plugin refait a un test hors serveur (non embarqué dans le jar) qui construit la map en mémoire, écrit un .schem et le mesure avec `analyze.py` (avant/après). États de blocs validés contre le `blocks.json` du data generator 26.3.
  - Outil d'audit commun hors jar : `_commun/build/outils/fr/eemicraft/build/Audit.java` (`sync_audit.ps1`, `<plugin>/outil-audit/audit.ps1`) — dépend d'un JDK et d'un blocks.json en dossier temporaire de session. Copies : `EemiHideSeek/outil-test` (avec `roofStairs`), `EemiInfection/outils` + `AuditCity.java`. Contrôles Murder : `EemiMurder/outil-test/controles.ps1` + `Controles.java` (escaliers, trappes, portes, supports). Catégories ESCALIER « tete cognee » et VITRE.
  - Maps refaites avec le skill (16/11, choix d'Esteban) : ville d'Infection, salle du Casino, Speedball (Paintball), 1 2 3 Soleil, Dé à coudre, salle d'attente du Dropper (pas la Course en élytres). Ensuite : petites maps Paintball, maps Murder, Cache-cache, Sheep Wars (détails dans leurs sections).
- **Pièges / règles de construction** :
  - Défauts relevés dans les anciens builders : murs d'un seul plan, variantes tirées bloc par bloc (poivre et sel), toits plats sans débord, terrain plat, limites en barrières invisibles.
  - Escaliers (Esteban, 17/11) : 3 cases libres au-dessus de chaque marche suivie d'une marche plus haute, et au-dessus de la case d'approche devant la 1re marche. Vitres toujours DANS le plan du mur (Facade les posait une case à l'intérieur : elles flottaient). Audits du 17/11 : 0 escalier en défaut sur Paintball, Casino, Dé à coudre, Dropper, Cache-cache, Murder.
  - Pose sans physique, feuilles persistantes ; une reconstruction en pleine partie (ex. `/infection build`) bloque le serveur 5-15 s.
  - Points de réapparition : ne garder que les cases atteintes à pied depuis les rues (`Reach.java` d'Infection), sinon des joueurs naissent enfermés (combles, pentes de toit).

## Veille builder de nuit

- **État** : tâche planifiée locale `veille-builder-nuit` (Claude Code, tous les jours vers 2 h, PC d'Esteban allumé + app ouverte ; sinon au prochain lancement). Désactivée la nuit du 16 au 17/11, à réactiver (non confirmé depuis).
- **Fonctionnement** : une map réputée d'un de nos modes par nuit, mesurée et COMPARÉE à nos maps du même mode (tolérance +30 %), techniques ajoutées au skill builder, rapport `veille/RAPPORT_NUIT_<date>.md`. Ne touche jamais au serveur ni aux plugins. Gabarits : `.claude/skills/builder-minecraft/reference/budgets.md`.
- Maps prévues après le Squid Game (choix d'Esteban, 16/11) — faites depuis (voir sections des jeux) : Murder (refaire Carnival et Musée, « map de 2012 », + manoir-bibliothèque, paquebot, hôtel), Sheep Wars (port pirate, ruines, base spatiale ; PAS d'île centrale, aucun moyen simple de rejoindre l'autre camp sauf le mouton d'abordage, taille des maps actuelles), Cache-cache (école EEMI — abandonnée le 17/11 —, supermarché).

## Sources en double entre les deux PC

- Plugins faits sur le poste de Thibault puis repris par Esteban depuis une source décompilée (jar d'origine dans `_origine/`) : `EemiSheepWars/` (depuis 4.4.0), `EemiHideSeek/` (depuis 1.2.4), `EemiBuildBattle/` (depuis 1.0.0). Depuis le 24/11/2026, le dépôt contient la source d'Esteban (versions installées) ; la source d'origine de Thibault reste dans l'historique git. EemiCore : la 1.9.0 du dépôt fait foi (Esteban n'avait qu'une 1.5.x décompilée). Prévenir l'autre avant d'y toucher ; partir de la plus haute version (pom + /plugins).
- Zip d'échange du 06/11 : `a_deposer/pour-thibault-20261106.zip` (sources sans `target/`, jars des 12 plugins faits chez Esteban, CLAUDE.md, LISEZMOI.md) ; `.mcp.json` exclu (clé MineStrator).
- Jeux sans source chez Esteban (au 17/11) : SkyWars, HG, Bridge, Towers, TNT Tag, TNT Wars, BoS, Pitchout, UHC, Bingo, Block Party, Wizards, PvP Run, JumpLeague, Bow Spleef, Duels ; EemiCore 1.8+ (seule une 1.5.x décompilée), EemiVie, EemiNoel.
- Sources d'Esteban versées dans le dépôt le 24/11/2026 depuis son ancien dossier local (pom = version installée ; l'ancienne source du dépôt reste dans l'historique git) :
  - `EemiMurder/` 1.13.0 (remplace la 1.6.0).
  - `EemiBoatRace/` 2.4.0 (remplace la 2.2.0 ; il n'y a plus deux sources).
  - `EemiSheepWars/` 4.6.0 (base décompilée de la 4.4.0 de Thibault ; gen_maps.py, gen_steampunk.py et aperçus de Thibault gardés).
  - `EemiHideSeek/` 1.5.1 (base décompilée de la 1.2.4 de Thibault).
  - `EemiBuildBattle/` 1.1.0 (base décompilée de la 1.0.0 de Thibault ; arena.yml, gen_arena.py et datapack `eemibb/` de Thibault gardés).
  - `EemiBank/` 1.1.0 (absent du dépôt jusque-là).
  - `EemiCasino/` 1.4.0 (absent du dépôt jusque-là).
  - `EemiDeACoudre/` 1.3.0 (absent du dépôt jusque-là).
  - `EemiDropper/` 1.3.0 (absent du dépôt jusque-là).
  - `EemiElytra/` 1.1.0 (absent du dépôt jusque-là).
  - `EemiEntrainement/` 1.2.0 (absent du dépôt jusque-là).
  - `EemiInfection/` 1.8.0 (absent du dépôt jusque-là).
  - `EemiJump/` 1.5.1 (absent du dépôt jusque-là).
  - `EemiPaintball/` 1.9.0 (absent du dépôt jusque-là).
  - `EemiReparer/` 1.0.0 (absent du dépôt jusque-là ; jamais installée).
  - `EemiResto/` 1.0.1 (absent du dépôt jusque-là).
  - `EemiSoleil/` 2.1.0 (absent du dépôt jusque-là).
  - `EemiSpeedrun/` 1.1.0 (absent du dépôt jusque-là).
  - `EemiSpleef/` 1.2.0 (absent du dépôt jusque-là).
  - `EemiTresor/` 1.1.0 (absent du dépôt jusque-là).

## Retour au salon d'attente en fin de partie

- **État** : demande d'Esteban (17/11) « quand la partie se finit on reste dans le spawn du jeu pour relancer direct ». Fait seulement dans les jeux d'Esteban (confirmé par lui, pas de consigne pour Thibault) : Murder, Cache-cache, Infection, Soleil, Paintball, Sheep Wars, Spleef, Dé à coudre, Dropper, Élytres, Course de bateaux, Speedrun. Rien testé en jeu.
- **Fonctionnement** : joueurs gardés inscrits, renvoyés dans la salle d'attente du jeu, objet « Retour au lobby » (lit, PDC) en case 8 ; `/<jeu> stop` = ancien comportement (retour au hub).
- **Pièges** : l'anti-AFK ajouté par un agent (inscrit immobile renvoyé au hub) a été RETIRÉ à la demande d'Esteban : tous les joueurs restent en file (le suivi `active` reste dans le code, inutilisé). Le lit en case 8 est sur la même case que le cache-joueurs de DeluxeHub : cause probable (non prouvée) de joueurs cachés en revenant au lobby ; Esteban veut garder le cache-joueurs en case 8, non corrigé.

## Dropper (EemiDropper)

- **État** : 1.3.0 installée. Pas encore joué (au dernier fait connu).
- **Fonctionnement** : source `EemiDropper/`, `/dropper`, id EemiCore `dropper`. Salle d'attente dans le monde vide `dropper` (refaite avec le skill : tête de puits suspendue, galerie à toit de cuivre, nef à lanterneau, balcon vitré ; `WaitingRoom.java`, VERSION 2, étanchéité vérifiée à pied). 5 niveaux tirés par partie parmi 28, eau = niveau suivant, toucher autre chose = retour au départ du niveau, 6 min, records par niveau. Fin de partie : retour en salle d'attente (`backToWaiting`), `/dropper join` en spectateur = gardé.
  - Maps (CurseForge) : Delightful Dropper 1 et 2 + Dropper Map (In Progress), « All Rights Reserved » (celles d'Hypixel ne sont pas téléchargeables), mondes `dropper_dd1|dd2|dm3` ; « Dropper Challenge » (1668929/8738014, 6 niveaux, monde `dropper_dc`) et « just another dropper » (BatEagles, 250963/2331553, 1.10.2, 3 niveaux, monde `dropper_jad`), sans licence indiquée. Dossiers déposés à la racine du serveur, migrés par Paper dans `lobby/dimensions/minecraft/` au chargement par le plugin (pas de `mv import`).
  - `mergeLevels()` ajoute les niveaux manquants au `levels.yml` du serveur (copie `levels.yml.avant-ajout`), respecte `retired-levels`.
  - Déclaré : AuraSkills, DeluxeHub mondes désactivés, TAB, EemiCore `games.dropper.worlds` (tous les mondes dropper*), EemiAdmin.
- **Pièges** : blocs de commande retirés hors ligne pour dd1/dd2/dm3, neutralisés au chargement pour dc/jad (42 + 77, panneaux à commande compris). La conversion 1.10 de `dropper_jad` écrit une série d'ERROR « Invalid block entity minecraft:command_block » au 1er chargement, sans effet. Bugs corrigés après revue : objets du lobby effacés après /lobby, spectateurs remis en survie par Multiverse.

## Course en élytres (EemiElytra)

- **État** : 1.1.0 installée. Pas encore joué.
- **Fonctionnement** : source `EemiElytra/`, `/elytra` (alias `/courseelytre`), id `elytra`. 3 parcours construits en fond au premier démarrage (25 ms/tick, ~25 s à 2 min ; `/elytra join` répond « en construction » avant) : Canyon rouge (x 0), Archipel céleste (z 6000), Grotte de cristal (z 12000) ; 32-36 anneaux, 1 sur 3 = boost vert, crash = retour au dernier anneau, records par parcours. Fin de partie : retour sur la tour d'attente du prochain parcours, lit « Retour au lobby » (PDC `leave`). Son `Builder.java` (strates par simplex) sert de modèle au skill builder.

## Spleef (EemiSpleef)

- **1.3.1 installée le 27/11** (redémarrage 09:26 sans erreur, `a_deposer_installes/`) : anti-rebond de 350 ms dans `toggleReady` (carte #157 « on clique une fois, ça met prêt puis plus prêt » : un clic sur un bloc envoie deux événements, bloc puis objet). Même correctif dans Murder 1.14.2, Sheep Wars 4.7.2, Speedrun 1.2.1.
- **1.3.0 installée** le 26/11 (`a_deposer_installes/EemiSpleef-1.3.0.jar`, carte #134), redémarrage 10:23 sans erreur : objet « Prêt » (colorant gris, case 4 du chalet, tag PDC `eemispleef:item = ready`, retiré par `clearGameItems`), tous prêts pendant le décompte → `ready-skip-to` (5). Pas testé en jeu.
- **Avant** : 1.2.0 (en `.jar.old`). Pas encore joué. (Spleef avait d'abord été reporté à Noël, 06/11.)
- **Fonctionnement** : source `EemiSpleef/`, monde `spleef` créé par le plugin, 3 arènes côte à côte (x 0/400/800, `max-instances` 3) préparées au démarrage ; 2-16 joueurs ; `/spleef join|leave|spectate`, admin `start|stop|build|reload|parties`.
  - Décor v2 (Esteban : « une grande map tout droit avec que de la neige ») : un seul terrain de neige plat 37 × 25 (HX 18, HZ 12) enfoncé dans un stade, mur de 4 blocs (andésite polie, glace tassée à pilastres de glace bleue, chaperon de quartz), fosse de 13 blocs à lit de lave, gradins au nord, obélisques de glace, chalet d'attente au sud (z -31..-21, sol y 105). ~207 000 blocs par arène. Rendus `EemiSpleef/apercu_dessus.png`, `apercu_coupe.png`.
  - Pelle diamant Efficacité V incassable (un clic casse, rien ne tombe), boules de neige (1 par bloc cassé, max 16, cassent un bloc + petit recul), 5 s de grâce, chute ou > 22 blocs du centre = spectateur, fonte d'un anneau rectangulaire toutes les 3 s après 5 min, bonus toutes les 45 s. Fin : retour au chalet de la même arène, terrain remis à neuf.
  - Déclaré : AuraSkills, DeluxeHub, TAB, EemiAdmin `spleef join`, EemiCore `games.spleef` (min 2, max 16) ; boussole `tnt.yml` case 22 (pelle en diamant).
- **Pièges** : `layers` forcé à 1 dans le code : les clés `layers`/`radius` du config du serveur ne sont plus lues. Le décor se reconstruit au démarrage si sa VERSION change.

## Block Sumo (EemiBlockSumo) — RETIRÉ

- Retiré le 17/11 (Esteban : « dégage-moi Block Sumo ») : entrée supprimée de la boussole (`combat.yml`) et de `games.blocksumo` d'EemiCore ; jar plus chargé. Ne pas remettre.
- Restes sans effet : monde `blocksumo`, mentions dans AuraSkills/DeluxeHub/TAB/OCM/EemiAdmin (`blocksumo join`, `bsumo join`), source `EemiBlockSumo/`.
- Pour mémoire (dernière version 1.1.0) : FFA sur plateforme 25×25 au-dessus du vide, laine/TNT/recul, départ au sommet d'un pilier de laine de sa couleur (`pillar-height` 6). Cause du « on ne peut pas poser de blocs » : notre propre `onInteract` qui mettait `useInteractedBlock = DENY` sur tout clic droit (le bloc cliqué ne doit être refusé que s'il est actionnable, `isInteractable()`).

## Nouveaux jeux du 06/11/2026 (faits transversaux)

- Demande d'Esteban : 1 2 3 Soleil, Dropper, Course en élytres, Infection, Paintball (vrai design d'arme via le pack, aucun mod). Codés par 5 sous-agents sur le modèle d'EemiDeACoudre ; sources `EemiSoleil/`, `EemiInfection/`, `EemiDropper/`, `EemiElytra/`, `EemiPaintball/`. Snapshot MineStrator `avant-5-nouveaux-jeux-20261106` pris avant.
- Mondes déclarés : `soleil`, `infection`, `elytra`, `paintball`, `dropper`, `dropper_dd1|dd2|dm3` dans AuraSkills `disabled_worlds`, mondes désactivés de DeluxeHub, disable-condition TAB `scoreboard-teams` ; joins bloqués en vanish (EemiAdmin) ; EemiCore `games.soleil|infection|dropper|elytra|paintball`. Pas de PNJ.
- Revue adversariale Opus faite avant installation sur Infection, Dropper, Paintball et Élytres (méthode reprise ensuite).

## Ménage du serveur (16/11/2026, demande d'Esteban)

- Il reste UN `.jar.old` par plugin (le précédent) ; 118 plus anciens supprimés. Règle redite par Esteban le 26/11 : à chaque installation, supprimer les `.old` plus anciens sans redemander (14 supprimés ce jour-là).
- Supprimés : zips de maps déjà importées à la racine (Ruins of Altrac, pitchout, 6 zips BedWars, carnival, museum, mm_campblood, mm_cheaphouse, Paintball_Map_1), /plugins/Balls-of-Steel.zip, ancien /EemicraftPack.zip de la racine, /EemiStagiaires-1.0.0.jar en double, /server-icon-ancienne.png, /tmp_sbwjar, /maps-sources/jarscan ; plus tard les zips dropper et `/buildbattle_duo_datapack` (accord d'Esteban).
- Plugins supprimés (jar + dossier) : EemiTaxe, EemiRing (ring de l'ancien lobby).
- Gardés après question : /refuge_bourgade.zip et /plugins/RefugePack.zip (Thibault), /plugins/EemicraftPack.zip, /eemicraft-skyblock-source-2026-10-29.zip, /hungerGames-map, /map boat, /maps-sources, /sauvegardes ; EemiCombat (dossier seul), EemiTonte 1.2.3 (de nouveau en place, origine inconnue), EemiHalloween, EemiFeux.

## En attente (liste ouverte)

- Session de test des jeux jamais joués avec la console suivie en direct.
- Spawn du monde lobby : `/execute in minecraft:overworld run setworldspawn 722 51 650` (si pas fait ; la commande était refusée à l'outil, à taper par Thibault).
- Déop BanTeEs/Qorwinn, droits via LuckPerms (au 13/11, seuls Enosik et BanTeEs étaient op).
- Monde `towers` à supprimer (accord de Thibault).
- Erreurs DeluxeHub AntiSwear/AutoBroadcast toujours dans la console (vues le 17/11).
- Message Discord (Halloween + nouveaux jeux). Choix de la version de LoupGarou (5 ou 10 rôles). Mise à jour Paper (9 builds de retard le 28/10).
- EemiAdmin : sortir du vanish quand un joueur perd `eemiadmin.vanish` (voir Joueur invulnérable).
- Fait depuis la liste du 28/10 : `.old` et zips de la racine nettoyés (16/11), ring de l'ancien lobby supprimé (EemiRing, 16/11), PNJ Fallen-Kingdom supprimé (10/11).
