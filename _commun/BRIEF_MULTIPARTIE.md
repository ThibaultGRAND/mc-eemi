# Refonte multi-partie des jeux EEMICRAFT (consigne commune)

Serveur Paper 26.3 unique (lobby + mondes de mini-jeux). Chaque jeu maison est aujourd'hui
un singleton : une seule partie à la fois, codée en dur sur un nom de monde. Objectif :
**plusieurs parties du même jeu en même temps**, chacune dans sa propre copie du monde.

## Règles d'architecture (à suivre pour tous les jeux)

1. **Utilitaire commun** : recopie `C:\Users\Utilisateur\Desktop\mc\_commun\WorldClone.java`
   dans le package du plugin (change seulement la ligne `package`). Ne le modifie pas
   (s'il te manque quelque chose, ajoute-le dans ton plugin, pas dans l'utilitaire).
   Il compile avec paper-api 26.3. Noms : partie 1 = monde d'origine (`base`),
   partie n ≥ 2 = `base_n` ; `WorldClone.index(base, nom)` > 0 = monde de partie.
2. **Modèle propre** : on ne copie jamais un monde où une partie est en cours. Au démarrage
   du plugin, une fois la map de la partie 1 propre (construite / remise à zéro, comme le
   plugin le fait déjà), fais une copie `base_modele` (WorldClone.create avec le nom
   `base + "_modele"`) qui ne sert qu'à faire les copies ; personne n'y va. Si le plugin
   reconstruit la map (commande build / map <id> qui change de map), refais le modèle
   après (détruis l'ancien `base_modele` puis recrée-le). `index(base, "base_modele")` = 0,
   donc il n'est pas un monde de partie. Au démarrage, `WorldClone.cleanup(...)` supprime
   les copies restées sur le disque (pas le modèle : recrée-le).
3. **Classe de partie** : toute la logique d'état (joueurs, équipes, minuteurs, scoreboard,
   blocs posés, moutons, etc.) passe dans une classe par partie (`Game`/`Arena`/`Instance`),
   avec son `World`. Plus aucun état de partie en champ du plugin. Les tâches répétées
   tournent sur chaque partie. Les écouteurs d'événements retrouvent la partie du joueur
   (map UUID → partie) ou du monde (nom du monde → partie), jamais un nom de monde en dur.
4. **Nombre de parties** : clé de config `max-instances` (défaut **3** dans le code : le
   config du serveur n'a pas la clé). Les noms de copies possibles sont donc `base_2` et
   `base_3` : l'admin les déclare d'avance dans les autres plugins (DeluxeHub, TAB, OCM…).
5. **Rejoindre** : `/<jeu> join` place le joueur dans la partie en attente (ou en décompte)
   qui a de la place, la plus remplie d'abord. S'il n'y en a pas et qu'on est sous
   `max-instances`, crée une nouvelle partie (copie du modèle, asynchrone : message
   « Preparation d'une nouvelle partie... », le joueur attend au hub puis est téléporté
   quand la copie est prête ; s'il a quitté/changé d'avis entre-temps, on n'y va pas).
   Sinon « Toutes les parties sont pleines ». Le groupe (EemiCore `/eemi join`) appelle
   la commande join pour chaque membre : garde-les ensemble si possible (même partie si
   elle a la place).
6. **Fin de partie** : la partie 1 garde son comportement actuel (remise à zéro/rebuild,
   retour au salon d'attente…). Une copie (n ≥ 2), une fois la partie finie et les joueurs
   renvoyés (au hub ou vers une autre partie en attente si le plugin renvoyait au salon),
   est détruite (`WorldClone.destroy`) ; la prochaine copie repartira du modèle propre.
   Ne détruis pas une copie qui a encore des joueurs inscrits en attente : elle devient
   simplement une nouvelle partie en attente (remise à zéro comme la partie 1), ou détruis-la
   seulement si elle est vide.
7. **Admin** : `start`/`stop` agissent sur la partie de l'admin s'il est dedans, sinon sur
   la partie 1, ou `start|stop <n>` pour une partie précise. Ajoute `/<jeu> parties`
   (admin) : liste des parties (numéro, monde, état, joueurs). Les commandes de construction
   (build, map…) ne touchent que la partie 1 puis refont le modèle, et refusent si la
   partie 1 est en cours.
8. **Spectateur** : `/<jeu> spectate [joueur]` : avec un pseudo, la partie de ce joueur ;
   sans, la partie en cours la plus remplie.
9. **Statut EemiCore** : le plugin envoie à la console `eemi status <jeu> <etat> <attente>
   <en_jeu> <max>` ; garde un seul envoi agrégé : état = `waiting` si au moins une partie
   attend ou décompte (sinon `playing` si une partie joue, sinon `waiting`), attente =
   somme des joueurs en attente, en_jeu = somme des joueurs en partie, max = max par partie
   × nombre de parties possibles. Les `eemi reward` restent par joueur.
10. **Changement de monde** : quitter les mondes de SA partie = quitter la partie (comme
    aujourd'hui avec le nom du monde). Aller d'un monde de sa partie à un autre monde de sa
    même partie ne fait pas quitter.
11. **Protections** : tout ce qui filtrait sur le monde du jeu (pas de casse, pas de faim,
    pas de chute, explosions, etc.) doit s'appliquer à tous les mondes de partie
    (`index > 0`) et au modèle (personne n'y est mais par sécurité).
12. **Scoreboard / titres / messages** : par partie (un broadcast ne doit toucher que les
    joueurs de la partie). Titres courts : titre ≤ 16 caractères visibles, sous-titre ≤ 38.
13. **Compatibilité** : le config du serveur n'a pas les nouvelles clés → défauts dans le
    code. Garde les noms de commandes, permissions et clés existants.

## Livraison

- Incrémente la version **mineure** dans `pom.xml` (ex. 4.0.4 → 4.1.0) ; `plugin.yml` doit
  utiliser `${project.version}` avec le filtrage Maven actif (vérifie).
- Compile (bash, depuis le dossier du plugin) :
  ```
  T="C:/Users/UTILIS~1/AppData/Local/Temp/claude/C--Users-Utilisateur-Desktop-mc/9e5d6ec0-ac8b-4981-b8b3-159615a28d8c/scratchpad/tools"; JH=$(ls -d "$T"/jdk/*/|head -1); MV=$(ls -d "$T"/mvn/*/|head -1); JAVA_HOME="$JH" "$MV/bin/mvn" -q -B package -Dmaven.repo.local="$T/m2"
  ```
  (paper-api : `$T/m2/io/papermc/paper/paper-api/26.3.build.218-stable/`, javap dans `$T/jdk/*/bin`).
- Copie le jar dans `C:\Users\Utilisateur\Desktop\mc\a_deposer\` **en remplaçant** l'ancien
  jar du même plugin s'il y est (supprime l'ancien fichier de `a_deposer`).
- Ne touche qu'aux dossiers de tes plugins. Pas au serveur, pas à CLAUDE.md, pas à
  `_commun/`, pas aux autres plugins (d'autres agents travaillent en parallèle).
- Rapport final court en français, par plugin : version, mondes utilisés par partie
  (base et copies), comportement de fin de partie, nouvelles commandes/clés, ce qui n'est
  pas vérifié, et la **liste exacte des noms de mondes** que l'admin doit déclarer ailleurs
  (ex. `sheepwars_2`, `sheepwars_3`, `sheepwars_modele`).
