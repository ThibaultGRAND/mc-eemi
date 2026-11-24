# Build Battle DUO : arène `eemicraft_duo` (préparé le 17/11/2026, rien posé sur le serveur)

Plugily Build Battle 5.1.8 gère nativement les équipes : la clé `plotmembersize: 2` d'une arène met plusieurs joueurs sur un même terrain (type TEAM). Il suffit donc d'une 2e arène, construite par le datapack `eemibb`, plus EemiBuildBattle 1.1.0 pour les coéquipiers et un correctif.

- Source Plugily lue (branche master = 5.1.8) :
  - `ArenaRegistry.additionalValidatorChecks` lit `plotmembersize` (refusée si < 1, et minimumplayers remonté à plotmembersize + 1) ;
  - `BuildArena.setTypeByPlotMembers` passe en TEAM si plotmembersize > 1 ;
  - `distributePlots` place les joueurs par terrain ;
  - le temps de construction vient de `Time-Manager.Teams.In-Game` (540 par défaut) ;
  - `gametype` n'a que deux valeurs utiles : `classic` / `team` → BuildArena, et `gtb` (le mode GTB en équipes n'existe pas).
- Les équipes se forment ainsi :
  - au lobby d'attente, l'objet « Select Plot » (laine, slot 1, commande `bb selectplot`) permet de choisir un terrain ; deux amis choisissent le MÊME terrain ;
  - sans choix, la distribution automatique fait des paires (un joueur seul si le nombre est impair) ;
  - pas de lien avec les groupes EemiCore : le hook de groupes de Plugily ne connaît que des plugins de party connus.
- Défaut de Plugily corrigé dans EemiBuildBattle 1.1.0 : en TEAM, si des choix ont été faits au sélecteur, des joueurs peuvent rester SANS terrain (`BuildArena.java:116-143`, condition `neededDividedPlots > currentDividedPlots`). Exemple : 3 joueurs, A et B sur le terrain 1, C ne choisit rien → C n'est placé nulle part. La 1.1.0 place tout joueur sans terrain sur le terrain incomplet le plus rempli, pendant le vote du thème et la construction (`fix-orphans: true`).

## Fichiers

| Fichier | Rôle |
|---|---|
| `gen_arena_duo.py` | Générateur. Lancer `python -I gen_arena_duo.py <blocks.json du data generator 26.3>`. |
| `serveur/lobby/datapacks/eemibb/data/eemibb/function/build_duo.mcfunction` | Île, jardins, avenue, fontaine (20 005 commandes). Appelle `walls_duo` à la fin. |
| `serveur/lobby/datapacks/eemibb/data/eemibb/function/walls_duo.mcfunction` | Murs pleins en quartz lisse autour de chaque terrain, sur le liseré de béton (1 bloc hors du terrain), y 65..110, avec dalles, pilastres et lanternes. |
| `serveur/lobby/datapacks/eemibb/data/eemibb/function/clear_duo.mcfunction` | Vide la zone, seulement pour refaire l'arène. Une couche par commande, 17 222 blocs par commande. |
| `arenas_duo.yml` | Section à coller sous `instances:` de `plugins/BuildBattle/arenas.yml`. |
| `serveur/plugins/DeluxeHub/menus/buildbattle.yml` | Sous-menu proposé (Solo / Duo / Retour). |
| `rapport_duo.txt` | Contrôles du générateur. |
| `jardins_duo.pbm` | Export pour `analyze.py`. |
| `../a_deposer/EemiBuildBattle-1.1.0.jar` | Le plugin complément. |

## Disposition

- Même gabarit que le solo, décalé de +300 en z : 8 terrains 25×25, sol y 64, hauteur 30 (jusqu'à y 94).
  - Colonnes en x : -70, -35, 0 et 35.
  - Rangées en z : 260 et 315.
  - Avenue en z 287..312, fontaine en -6 64 300.
- Arrivée (lobby/start/end) en -5.5 65 309.5, spectateur en -5.5 89 300.5.
- Île x -84..73, z 246..354, y 30..112 : aucune case en commun avec le solo (z ≤ 79).
- Bord de l'île : haie de 3 blocs, avec des barrières derrière jusqu'à y 75. En attente, Plugily annule les dégâts, donc un joueur qui tomberait ne serait pas renvoyé : il fallait l'empêcher de tomber.

## Étapes serveur (dans l'ordre)

1. **Vérifier le config de Build Battle** (`plugins/BuildBattle/config.yml`) : la section `Time-Manager:` doit avoir `Teams:` avec `Voting: Theme / Plot` et `In-Game`, comme `Classic:`. Sinon le duo aurait un temps de 0.
   - Valeurs par défaut du jar : Theme 25, Plot 20, In-Game 540.
   - Si la section manque, l'ajouter par edit_file, en copiant `Classic:`.
2. **Datapack** : déposer les 3 fichiers `.mcfunction` dans `/lobby/datapacks/eemibb/data/eemibb/function/`, à côté de `build.mcfunction` et `walls.mcfunction`. Si le dossier du solo ne s'appelle pas `function`, les mettre dans le même dossier que `build.mcfunction`.
3. **Console**, une commande à la fois :
   ```
   minecraft:reload
   execute in minecraft:buildbattle run forceload add -96 240 79 367
   ```
   Attendre environ 5 s que les 88 chunks se chargent : un `fill` dans un chunk non chargé échoue sans arrêter la fonction.
   ```
   execute in minecraft:buildbattle run function eemibb:build_duo
   execute in minecraft:buildbattle if block -6 71 300 minecraft:lantern
   execute in minecraft:buildbattle if block -71 100 270 minecraft:smooth_quartz
   execute in minecraft:buildbattle if block 59 64 339 minecraft:grass_block
   execute in minecraft:buildbattle run forceload remove -96 240 79 367
   ```
   - Les 3 `if block` doivent répondre « Test passed ».
   - La plus grosse commande fait 5 512 blocs : pas besoin de toucher à `max_block_modifications` pour `build_duo`.
   - Pour `clear_duo`, chaque commande fait 17 222 blocs, sous la limite elle aussi.
4. **arenas.yml** : ajouter le contenu de `arenas_duo.yml` à la fin de la section `instances:` de `plugins/BuildBattle/arenas.yml` (indentation : 2 espaces devant `eemicraft_duo:`), par edit_file, sans réécrire le fichier. Puis `buildbattleadmin reload`, à taper deux fois (confirmation).
   - Log attendu : « instance … eemicraft_duo … started ».
   - Si une clé est refusée, Plugily réécrit `isdone: false` : corriger, remettre `isdone: true`, puis recharger.
5. **EemiBuildBattle 1.1.0** : déposer `a_deposer/EemiBuildBattle-1.1.0.jar` dans `/plugins`, renommer `EemiBuildBattle-1.0.0.jar` en `.jar.old` (vérifier par search_files), puis redémarrer en annonçant le redémarrage par `/title` 10 s avant, et faire `mvinv reload`.
   - Les nouvelles clés `show-teammates` et `fix-orphans` valent `true` par défaut dans le code : rien à ajouter au config du serveur.
6. **Boussole** :
   - déposer `menus/buildbattle.yml` dans `plugins/DeluxeHub/menus/` ;
   - l'aligner sur les clés d'un menu existant (ambiance.yml) avant de recharger ;
   - dans `ambiance.yml`, passer l'action de l'entrée Build Battle à `[MENU] buildbattle` et mettre à jour son texte de survol (Solo / Duo) ;
   - `deluxehub reload`.
   - Variante sans sous-menu : un 2e bouton `[COMMAND] buildbattle join eemicraft_duo`.
7. **Facultatif, EemiCore** : le bouton duo passe par `buildbattle join eemicraft_duo` directement (pas de suivi de groupe EemiCore). Le statut « buildbattle » d'EemiCore compte déjà les joueurs du monde `buildbattle`, solo et duo confondus.
   - Pour un statut séparé, ajouter `games.buildbattle_duo` (join `buildbattle join eemicraft_duo`, worlds [buildbattle]) puis `eemi reload`.
   - Vérifier que les `blocked-commands` d'EemiAdmin attrapent `buildbattle join` en vanish.

## Non vérifié

- Rien n'a été posé ni joué. Le décor a été jugé sur le rendu du dessus et sur une coupe (`.claude/skills/builder-minecraft/out/jardins_duo_*.png`) et par les contrôles du générateur :
  - 55 états de bloc, 0 invalide contre le `blocks.json` 26.3 ;
  - 0 bloc non relié à l'île, 0 plante ou lanterne sans support ;
  - 0 trou dans les murs.
- Le décor n'est pas le même que celui du solo : `gen_arena.py` n'est pas sur ce PC (il est sur celui de Thibault). C'est un générateur indépendant, sur le même gabarit et le même thème « jardins ».
- Le correctif « joueurs sans terrain » de la 1.1.0 n'a pas été vu en jeu ; il écrit « Joueur sans terrain place : … » dans la console.
- Deux joueurs qui choisissent chacun un terrain vide différent jouent seuls chacun : c'est le comportement de Plugily.
- `Time-Manager.Teams` du config du serveur, et la traduction fr_FR du tableau « Teams », ne sont pas vérifiés.
- Format exact des menus de DeluxeHub 3.8.6 non vérifié : aucun menu du serveur n'est sur ce PC.
- Source d'EemiBuildBattle : reconstruite par décompilation (CFR 0.152) du jar 1.0.0, faute de source sur ce PC. Il existe donc maintenant deux sources (Thibault + ce PC) : prévenir Thibault.
