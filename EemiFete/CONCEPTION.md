# Tycoon Fête foraine (EemiFete) : conception

Demande d'Esteban (26/11/2026) : un 2e Tycoon après le resto. « Tycoon Resto » devient « Tycoon » dans la boussole, avec le choix entre plusieurs Tycoons. On commence par la fête foraine, pour voir ce que ça donne (« si c'est bien fait ça peut mettre une très grosse claque »). Pas d'objets cachés pour l'instant (pas de références).

## Principe
- **Réclamer et construire.** On réclame un terrain vide de l'allée foraine et on construit son parc avec des boutons d'achat au sol, comme le resto.
- **Gagner des tickets.** Chaque attraction rapporte des tickets à chaque tour.
- **Encaisser.** On encaisse en marchant sur la dalle d'or du guichet.
- **Progresser.** Environ 3 h pour finir le 1er parc, revenus réduits hors ligne, renaissance, classement, nom du parc.
- **Animer.** Des visiteurs se promènent et montent dans les manèges (étape 2).

## Manèges animés
- **Principe.** Chaque manège est un socle en vrais blocs plus des pièces mobiles en `BlockDisplay`, posées au pivot du manège. Leur transformation (rotation + translation) est mise à jour par paliers, puis le client interpole. Le mouvement est fluide, sans mod et sans téléporter d'entité.
- **Code.** `Ride.java` décrit les pièces, cubes et mouvements, sans Bukkit. `Rides.java` contient les 8 manèges et les stands.
- **Les 8 manèges :**
  - carrousel (8 chevaux qui montent et descendent) ;
  - grande roue (12 nacelles qui restent droites) ;
  - chaises volantes ;
  - bateau pirate (balancement de 55°) ;
  - tour de chute (montée lente, chute) ;
  - auto-tamponneuses (6 voitures) ;
  - train fantôme (fantôme flottant, wagonnet) ;
  - montagnes russes (tout autour du parc, train de 3 wagons).
- **Budget.** Un parc complet fait environ 1 020 `BlockDisplay`. Seuls les parcs proches d'un joueur seront animés (étape 2).

## Map
- **Allée foraine commune**, de 23 blocs de large, en terre battue et pavés en grappes. Des guirlandes lumineuses traversent l'allée tous les 9 blocs et la longent de poteau en poteau.
- **Place d'entrée** avec une rosace au sol, un kiosque à musique et deux grandes arches.
- **10 parcs de 48 × 48**, 5 de chaque côté (`Geo`). Un parc du côté nord est tourné de 180° (rotation, jamais un miroir).
- **Plan d'un parc** (`Defs`) :
  - promenade, guichet et 4 stands (pêche aux canards, barbe à papa, chamboule-tout, tir) devant ;
  - carrousel et auto-tamponneuses au milieu ;
  - chaises volantes, bateau pirate, train fantôme et grande roue au fond ;
  - tour de chute à droite ;
  - montagnes russes en tour de parc, au-dessus de tout.

## Économie (Defs, à équilibrer par simulation comme le resto)
- **12 attractions**, de 2 tickets par tour de 4 s (pêche aux canards) à 40 000 par tour de 5 s (montagnes russes).
- **5 améliorations** qui multiplient les tickets : orgue de barbarie, mascotte, néons, pass VIP, arche lumineuse.
- **3 accélérateurs** : les tours vont 15 % plus vite.
- **31 achats** au total, dont 4 donnés à la réclamation, et le feu d'artifice final.

## Outils hors serveur
- `outil-test/fr/eemicraft/fete/TestRender.java` construit l'allée et 10 parcs à différents stades. Il écrit des vidages de blocs et les boîtes tournées des pièces mobiles, à 24 instants pour un parc complet.
- `outil-test/render_iso.py` fait un rendu isométrique de jour ou de nuit (lueur des ampoules) et un GIF animé. Les pièces mobiles y sont dessinées comme le client affichera les `BlockDisplay`.
- Rendus dans `apercus/`.

## Étapes
1. **Rendus de la map et des manèges** (fait le 26/11, en attente de validation par Esteban).
2. **Jeu**, à partir du moteur d'EemiResto :
   - commandes, réclamation, boutons, caisse, sauvegarde ;
   - animation des `BlockDisplay`, lumière forcée au maximum de nuit ;
   - visiteurs (Mannequin) qui se promènent et montent dans les manèges ;
   - simulation de l'économie ;
   - entrée par la boussole (sous-menu « Tycoon »), bêta par `/droits jeu fete beta`.
3. **Tests à quelques joueurs**, puis ouverture à tous.

## Non vu en jeu
Rien de tout cela n'a été vu en jeu : les rendus sont calculés hors serveur.
