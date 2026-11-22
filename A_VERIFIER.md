# EEMICRAFT : à vérifier en jeu (29/10/2026, tout est installé depuis 19:35)

Cocher au fur et à mesure.

## Lobby et serveur
- [ ] Spawn du monde lobby : taper `/execute in minecraft:overworld run setworldspawn 722 51 650` (sinon fin de partie = téléporté à l'ancien lobby 8 -63 8)
- [ ] Message d'arrivée dans le chat (Bienvenue sur EEMICRAFT, IP, Discord) et titre EEMICRAFT
- [ ] Tab : en-tête, pied (IP + Discord), « [Admin] » rouge devant Enosik et BanTeEs
- [ ] Tab en partie d'équipe : « [Rouge] » / « [Bleu] » devant le pseudo
- [ ] Scoreboard du hub (niveau, pièces, titre, en ligne, victoires, discord, IP)
- [ ] Titre au-dessus du pseudo au hub (hauteur correcte, pas sur le pseudo)
- [ ] Gadgets du hub : feux d'artifice, pistolet à peinture, grappin, jetpack (pas de dégâts de chute)
- [ ] Objets du lobby : laisse « Groupe » (menu /party), émeraude « Profil et boutique » (menus profil, boutique par catégorie, quêtes, joueurs en ligne, classement)
- [ ] Hologrammes de règles au-dessus des PNJ : 4e ligne de statut lisible, pas sur le nom du PNJ
- [ ] Musique du lobby (playlist Halloween), `/musique off`
- [ ] Décor Halloween, maison, sorcière, tonnerre, chauves-souris
- [ ] Mannequins des créateurs (s'accroupissent, ne prennent pas de dégâts)
- [ ] On ne vole plus hors du hub (FFA, PvP, BedWars…) ; le double saut marche encore en TNT Run et au cache-cache
- [ ] Vanish puis sortie du vanish : plus aucune boussole de vanish gardée au lobby
- [ ] `/spectate <joueur>` par un joueur NON op (ex. Antonin regarde un duel) ; shift-clic dans « Joueurs en ligne »

## Jeux
- [ ] **Sheep Wars** : aucun mouton au départ, le 1er arrive ~12 s après l'ouverture des cages ; plus de murs invisibles, l'îlot central accessible ; maps Désert rose / Îles vertes / Vaisseau ; titres courts
- [ ] **Murder** : maps Carnival et Musée (apparitions, or, mort sous le sol) ; 3 s entre deux kills du meurtrier, cooldown visible sur l'épée
- [ ] **Cache-cache** : un cacheur ne voit plus les autres cacheurs ; un cacheur trouvé voit les blocs des cacheurs restants ; bâton « Tourner le bloc »
- [ ] **Hunger Games** : « Ouvert » puis « Vide » au-dessus des coffres, effacé au refill ; bordure qui accélère à 4 puis 2 survivants ; nouveau loot (nourriture, fer, pommes d'or, diamants rares) ; map Ruins of Altrac
- [ ] **Pitchout** : arènes Glace / Jungle / Désert (fosse de lave), -1 vie sous le sol, recul du bâton et de l'arc
- [ ] **Course de bateaux** : Cosmic Cove (grille, ligne d'arrivée, 3 tours, 16 points de contrôle, replacement en cas de chute ou blocage), `/lobby` en bateau
- [ ] **SkyWars** : maps Sugar Rush et Twister (cages de Twister construites), kits
- [ ] **The Bridge** : pont d'un bloc sur mur de 10, buts, réapparition après 3 s
- [ ] **Block Party**, **TNT Tag**, **TNT Wars**, **Towers**, **Balls of Steel**, **Tonte Royale** (auto-tonte), **UHC Run**, **EEMINGO** : une partie complète chacun
- [ ] **Duels / FFA** : menu `/duel`, kits, files classées, spectateur
- [ ] **BedWars** : `bw autojoin` entre bw1 et halloween, TNT moins forte, forge à 4 diamants
- [ ] **Build Battle** : murs entre les terrains, « Je suis prêt », traduction française
- [ ] **Loup-Garou** : choisir la version (5 ou 10 rôles)
- [ ] Récompenses EemiCore après une partie (pièces, XP, victoires) et statut dans les hologrammes

## Parties en parallèle (nouveau)
- [ ] Lancer une partie (ex. Sheep Wars), puis `/sheepwars join` avec un autre joueur pendant qu'elle tourne : « Preparation d'une nouvelle partie... » puis arrivée dans `sheepwars_2`
- [ ] La 2e partie a bien sa map (pas un monde vide)
- [ ] Les deux parties ne se mélangent pas : chat, scoreboard (« Partie : #2 »), titres, morts, récompenses
- [ ] `/<jeu> parties` (admin) liste les deux parties ; `/<jeu> stop 2` arrête la 2e
- [ ] À la fin de la 2e partie, la copie disparaît (ou redevient une partie en attente s'il reste des inscrits)
- [ ] `/spectate <joueur>` envoie dans la bonne partie
- [ ] Un groupe (`/party`) qui rejoint arrive dans la même partie
- [ ] PvP 1.8 (OCM) et blocage à l'épée actifs dans une copie (ex. `sheepwars_2`, `thebridge_2`)
- [ ] Cache-cache dans une copie : le double saut / vol marche encore
- [ ] Murder : 2 parties en même temps, salon commun, maps séparées
- [ ] UHC / EEMINGO : 2 files lancées en même temps (mondes `uhc_2`, `bingo_2`)
- [ ] Lag et mémoire avec 2-3 parties en même temps (serveur à 90 % de 4 Go au démarrage)
- [ ] « Toutes les parties sont pleines » quand 3 parties d'un même jeu tournent
