# Serveur EEMICRAFT : règles à connaître avant de toucher à quoi que ce soit

Tiré du CLAUDE.md du projet (17/11/2026). Le détail de chaque jeu est dans le CLAUDE.md.

## 1. Configs et fichiers du serveur

- **Ne jamais réécrire une config en entier** : on modifie l'existant ligne par ligne (une réécriture complète a déjà fait planter DeluxeHub). Faire une sauvegarde avant (`/.ai-backups/`).
- DeluxeHub : noms d'objets sans accents ; `invert: true` ne marche pas, on liste les mondes désactivés (`invert: false`).
- FancyNpcs : `fancynpcs save` AVANT de modifier `npcs.yml` à la main (sinon les déplacements récents sont perdus), puis `fancynpcs reload`. Sinon, passer par les commandes `npc ...`.
- Commandes vanilla tapées à la console : elles visent le monde `tntwars` par défaut. Pour le lobby : `execute in minecraft:overworld run ...`. Après un `forceload add`, toujours `forceload remove all`.
- Multiverse-Inventories : `default-ungrouped-worlds` doit rester à **false** (à true, tout monde hors groupe partage l'inventaire du lobby → objets des jeux qui arrivent au hub).

## 2. Déposer un plugin

- Monter le numéro de version à chaque compilation (aussi dans `plugin.yml` : `${project.version}`, pas en dur).
- Les jars compilés vont dans `a_deposer\`. Une fois installés et vérifiés, ils passent dans `a_deposer_installes\`.
- **Renommer l'ancienne version en `.jar.old` AVANT de redémarrer, dans un appel séparé** (renommage et redémarrage en même temps = serveur bloqué en « stopping », il a fallu le tuer).
- Vérifier que le jar est bien dans `/plugins` et pas à la racine (arrivé plusieurs fois).
- Au démarrage, chercher « Ambiguous plugin name » dans la console : deux versions du même plugin sont présentes, c'est n'importe laquelle qui se charge.
- Prévenir les joueurs connectés avant un redémarrage.

## 3. Ajouter un nouveau jeu (liste complète)

1. **Monde** : créé par le plugin (WorldCreator) ou importé (`mv import <nom> normal`). Pour les parties en parallèle : copies `<monde>_2`, `_3` et modèle `<monde>_modele` (utilitaire `_commun/WorldClone.java`, qui ne doit pas copier `data/paper/metadata.dat`).
2. **EemiCore** (`plugins/EemiCore/config.yml`, section `games.<id>`) : `name`, `icon`, `join`, `spectate`, `worlds` (base + `_2` + `_3`), `min`, `max` ; ajouter les commandes de join dans `join-aliases`. Puis `eemi reload`.
3. **Le jeu parle à EemiCore** par la console : `eemi status <jeu> <waiting|starting|playing|ending> <attente> <en_jeu> <max>` et `eemi reward <pseudo> <jeu> <play|win|kill>`.
4. **EemiDroits** : il lit la section `games` d'EemiCore → `droits reload` et le jeu apparaît dans `/droits`. Si la commande pour quitter n'est pas « `<join>` → `<leave>` », la déclarer dans `sortie.<jeu>` du config d'EemiDroits.
5. **Déclarer le monde partout** (base, `_2`, `_3`, et `_modele` quand c'est indiqué) :
   - DeluxeHub : mondes désactivés (+ `_modele`), `deluxehub reload`
   - TAB : `scoreboard-teams.disable-condition`, `tab reload`
   - AuraSkills : `disabled_worlds`, `skills reload` (**obligatoire**, sinon mana et XP de compétence s'affichent en jeu)
   - OldCombatMechanics : modeset `old` si PvP 1.8 (pris au redémarrage)
   - EemiBlocage : `worlds` si blocage à l'épée (pris au redémarrage)
   - EemiAdmin : `blocked-commands` (join bloqué en vanish)
   - Multiverse-Inventories : groupe si le jeu a plusieurs mondes qui partagent l'inventaire
6. **Entrée** : boussole / sous-menu DeluxeHub ou PNJ, en `eemi join <id>` (emmène le groupe, respecte maintenance et bêta).
7. Noter dans le CLAUDE.md : version, installé ou non, testé ou non.

## 4. Bêta, maintenance, droits (EemiDroits, `/droits`)

- **Mettre un jeu en bêta** : `/droits jeu <jeu> beta` → seuls les bêta-testeurs et les admins y entrent. `ouvert` pour le rouvrir.
- **Maintenance** : `/droits jeu <jeu> maintenance <raison>` (passe par `eemi maintenance`, affiché « Maintenance » partout, réservé aux admins ; les parties en cours continuent).
- **Donner le tag Bêta** : `/droits beta <pseudo> on` (permission LuckPerms `eemidroits.beta`). Les ops ne l'ont pas d'office mais passent grâce à `eemidroits.bypass`.
- Un nouveau mode en test → le mettre en bêta plutôt que bricoler une permission à part.
- Interdire un monde ou un jeu à un joueur : fiche du joueur dans `/droits` (un monde de base couvre ses copies `_2`, `_3`, `_modele` ; le lobby n'est jamais interdit).
- Les droits donnés dans `/droits` sont des permissions LuckPerms **globales**.

## 5. Pack de textures

- **Un seul pack EEMICRAFT pour tout le serveur.** Toujours repartir du **dernier pack publié** (`a_deposer_installes/EemicraftPack-b05fe66f.zip` à ce jour), jamais d'une vieille copie locale.
- `EemicraftPack/build_pack.py` ne contient PAS tout (karts, paintball...) : le relancer tel quel efface ces ajouts.
- Menus : `EemiCore/pack/gen_nav.py` PUIS `EemiDroits/pack/gen_droits.py`, dans cet ordre.
- **Ne jamais retexturer un bloc ou un objet vanilla dans le pack global** : ça change son aspect sur tout le serveur (le convoyeur de l'Abysse s'affichait dans le Hunger Games). À la place :
  - un modèle dans notre namespace (`item_model eemicraft:...` / `item_display`), ou
  - **un pack séparé chargé seulement dans les mondes du mode** et retiré en sortant (modèle : RefugePack, `Player.addResourcePack` / `removeResourcePack(UUID)` dans les mondes `refuge*`). C'est là qu'on a le droit de retexturer du vanilla.
- Textures des modèles d'objets dans `textures/item/` (ou `block/`), pas `textures/entity/` (sinon violet et noir).
- Vérifier avec `EemiRefuge/pack/verifier_pack.py <zip>` (0 erreur, aucune collision).
- Publication : déposer le zip → `publish_server_file_to_webhosting` vers `pack/EemicraftPack.zip` → vérifier que le SHA-1 est identique → `resource-pack-sha1` dans `server.properties` → redémarrer. Un jar qui utilise de nouvelles textures s'installe **en même temps** que son pack.

## 6. Inventaires (bug du stuff SkyBlock perdu)

- Un plugin ne vide l'inventaire que dans **son** monde ou au hub.
- Quitter le jeu à cause d'un changement de monde = **ne rien toucher** (le joueur est déjà dans le monde d'arrivée).
- Téléporter AVANT de vider, jamais l'inverse. Revérifier le monde dans les tâches différées.
- Marquer les objets de jeu avec une clé PDC du plugin (LobbyGuard les retire au lobby).
- Changer de monde pendant une partie (/lobby) = quitter la partie.

## 7. Pièges Paper 26.3 déjà rencontrés

- `max_health` plafonné à 1 024.
- `Material.CHAIN` → `IRON_CHAIN`. `Material.values()` est réécrit par Paper (pas de LEGACY_) : utiliser `getEnumConstants()`.
- WorldGuard bloque les apparitions de mobs des plugins (`block-plugin-spawning`) : rétablir les siennes en `CreatureSpawnEvent` HIGHEST.
- Refuser le bloc cliqué (`useInteractedBlock DENY`) empêche aussi de poser l'objet tenu : mettre `useItemInHand ALLOW` si on veut qu'il soit posé.
- `setInvulnerable` ne protège pas d'un joueur en créatif : annuler `EntityDamageEvent`.
- `getSpawnLocation()` du lobby sert de retour au hub pour tous les plugins.

## 8. Textes affichés aux joueurs

- Accents dans les textes joueurs (pas dans la console).
- Préfixe : `§8[` + couleur de la catégorie + nom + `§8] §7` (Équipes §9, Combat §c, TNT §6, Ambiance §d, Survie §a).
- Titre ≤ 16 caractères, sous-titre ≤ 38, le reste dans le chat.

## 9. Méthode

- Toute affirmation sur le comportement d'un plugin doit être vérifiée (doc, bytecode, test) avant d'être notée.
- Noter dans le CLAUDE.md ce qui est fait, installé, et ce qui n'est **pas encore testé en jeu**.
- Thibault (Enosik en jeu) décide ; ne rien supprimer sur le serveur sans son accord.
