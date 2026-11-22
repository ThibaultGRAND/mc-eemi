# Bug : le stuff du skyblock est perdu après un mini-jeu (consigne commune)

Serveur Paper 26.3. Les inventaires sont séparés par **Multiverse-Inventories** (MV-Inv) :
groupe `default` = `lobby` (hub), `tntrun`, `buildbattle` ; groupe `skyblock` = `bskyblock_world`,
`bskyblock_world_nether`, `bskyblock_world_the_end`, `arene_boss` ; chaque autre monde (les mondes
des mini-jeux, leurs copies `<monde>_2`, `_3`, `_modele`) a son propre inventaire. MV-Inv sauvegarde
l'inventaire du monde qu'on quitte et charge celui du monde d'arrivée **au moment du changement de
monde**. Donc :

- Si un plugin vide/modifie l'inventaire d'un joueur **après** son arrivée dans un autre monde
  (ex. skyblock), il efface l'inventaire de CE monde (le stuff skyblock), qui est ensuite sauvegardé vide.
- Si un plugin vide l'inventaire **avant** de téléporter le joueur hors d'un autre monde (ex. le joueur
  fait `/<jeu> join` depuis le skyblock : `resetPlayer` puis `teleport`), MV-Inv sauvegarde un
  inventaire skyblock vide.

Cas trouvé : `EemiBlockParty` `Game.leave(p, false)` (appelé par `PlayerChangedWorldEvent` quand le
joueur quitte le monde du jeu, par ex. `/is`) fait `p.getInventory().clear()` alors que le joueur est
déjà dans le monde d'arrivée.

## Règle à appliquer partout

Un plugin de jeu ne touche à l'inventaire, à l'armure, au coffre de l'Ender, à l'XP, à la vie / faim,
aux effets de potion ou au mode de jeu d'un joueur **que si le joueur se trouve à ce moment dans un
monde de ce jeu** (monde de partie, ses copies, son salon d'attente s'il est dans un monde à lui)
**ou au hub (`lobby`)**. Jamais dans un autre monde.

Concrètement :
1. Ajoute une petite méthode du style `boolean mayTouch(Player p)` (monde du jeu ou monde `lobby`) et
   fais-la vérifier au début de `resetPlayer(...)`, `equip`/`giveKit`, et partout où l'inventaire est
   vidé ou modifié (`getInventory().clear()`, `setItem`, `setArmorContents`, `getEnderChest()`,
   `setLevel/setExp`, `setHealth`, `setFoodLevel`, effets, `setGameMode`). Si le joueur n'y est pas :
   ne rien faire.
2. Quitter la partie suite à un changement de monde (`leave(p, false)` ou équivalent depuis
   `PlayerChangedWorldEvent`) : ne touche plus du tout à l'inventaire ni au joueur (MV-Inv s'en
   occupe) ; garde seulement le ménage interne (listes, scoreboard, barre de boss, visibilité,
   déguisements, équipes).
3. Rejoindre : téléporter **d'abord** dans le monde du jeu, **puis** vider / donner le kit (au tick
   suivant si c'est déjà le cas dans le code). Jamais `clear` puis `teleport`. Si le joueur attend au
   hub (files UHC / EEMINGO, copies en préparation), on ne touche à son inventaire qu'une fois arrivé
   dans le monde du jeu.
4. Tâches différées (`runTaskLater`, `later(...)`) qui touchent au joueur : vérifier `mayTouch(p)` au
   moment de l'exécution (le joueur peut être parti au skyblock entre-temps).
5. Fin de partie / renvoi au hub : téléporter au hub d'abord, puis réinitialiser (déjà au hub = OK).
   Si le joueur n'est plus dans un monde du jeu, ne pas le téléporter ni le réinitialiser.
6. Mort / réapparition : inchangé si c'est dans le monde du jeu.

Ne change rien d'autre (règles, maps, multi-partie). Style existant, commentaires en français.

## Livraison (par plugin)

- Incrémente la version **patch** dans `pom.xml` ; `plugin.yml` utilise déjà `${project.version}`.
- Compile (bash, depuis le dossier du plugin) :
  ```
  T="C:/Users/UTILIS~1/AppData/Local/Temp/claude/C--Users-Utilisateur-Desktop-mc/9e5d6ec0-ac8b-4981-b8b3-159615a28d8c/scratchpad/tools"; JH=$(ls -d "$T"/jdk/*/|head -1); MV=$(ls -d "$T"/mvn/*/|head -1); JAVA_HOME="$JH" "$MV/bin/mvn" -q -B package -Dmaven.repo.local="$T/m2"
  ```
- Copie le jar dans `C:\Users\Utilisateur\Desktop\mc\a_deposer\`.
- Ne touche qu'à tes plugins (d'autres agents travaillent sur les autres), ni au serveur, ni à CLAUDE.md.
- Rapport final court en français, par plugin : version, endroits corrigés (méthodes), ce qui touchait
  l'inventaire hors du jeu (le bug réel s'il y en avait un), ce qui n'est pas vérifié.
