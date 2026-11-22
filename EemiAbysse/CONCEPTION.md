# L'Abysse – document de conception

Mode de jeu persistant d'EEMICRAFT, à jouer seul ou à plusieurs (« Compagnie » jusqu'à 4).
Décidé avec Thibault le 01/11/2026 : monnaie propre au mode, progression permanente (pas de reset),
base dans un monde à part, nom « L'Abysse ».

## 1. Le principe

Au bord d'un gouffre se dresse Bordgouffre, une ville minière. Sous elle, l'Abysse descend étage par
étage (les **strates**) : chaque strate est un monde à elle, avec ses minerais, ses monstres, une
contrainte d'environnement et un **gardien** à vaincre pour ouvrir la suivante.

La boucle de jeu :

1. **Descendre** dans une strate, explorer, miner, combattre.
2. **Ramener** le minerai (à la main, ou par des foreuses posées sur les filons).
3. **Transformer** le minerai dans son usine, à sa **Concession** : chaque étape de transformation
   multiplie sa valeur.
4. **Vendre** à la bourse (les prix bougent selon ce que tout le serveur vend) ou **fabriquer**
   l'équipement qui permet de survivre plus bas.
5. **Redescendre** plus loin.

On ne peut sauter ni l'exploration ni l'usine : l'équipement de la strate N se fabrique avec les
produits raffinés des strates d'avant.

Durée visée : 30 à 40 heures de jeu pour atteindre le fond (strate 7).

## 2. Les lieux

| Lieu | Monde | Rôle |
|---|---|---|
| Bordgouffre | `abysse` | Ville-hub : PNJ, bourse, quêtes, monte-charge, le gouffre au centre. |
| Les Concessions | `abysse_concessions` | Une parcelle par Compagnie, agrandissable ; on y construit son usine. |
| Strate 1 à 7 | `abysse_s1` … `abysse_s7` | Mondes souterrains générés par le plugin au fur et à mesure qu'on les explore. |

- Tous ces mondes partagent le **même inventaire** (groupe Multiverse-Inventories `abysse`), séparé du
  reste du serveur.
- Bordgouffre : construite par le plugin en version simple (place autour du gouffre, échoppes, PNJ,
  tableau de la bourse) ; les builders pourront la remplacer (le plugin ne reconstruit que si
  `hub.build: true`).
- Sauter dans le gouffre = arriver au camp de la strate 1. Le monte-charge (cloche) mène aux strates
  déjà débloquées.

## 3. Les strates

Chaque strate est une grotte infinie : sol et plafond de bedrock (y 0 et y 118), cavernes et salles
générées par bruit, une **galerie principale** éclairée qui va du camp d'arrivée (0, 0) à l'antre du
gardien (à ~320 blocs), des **campements abandonnés** (tonneau avec du butin et parfois une page des
carnets de l'expédition Varenne) et des **filons** (gisements visibles au sol où poser une foreuse).

| # | Strate | Contrainte | Minerais / ressources | Monstres | Gardien |
|---|---|---|---|---|---|
| 1 | Les Galeries | Obscurité : la Lampe de mineur (main gauche, se recharge au charbon) donne la vision nocturne | charbon, cuivre, fer | Rôdeur, Mineur squelette, Ver de roche | Le Contremaître |
| 2 | La Champignonnière | Spores : Poison sans Masque filtrant | étain, cuivre, spores lumineuses | Archer moisi, Tisseuse, Gelée de spores | La Reine Mycélienne |
| 3 | Les Forges Englouties (fait, 2.0.0) | Chaleur : Infusion glacée, Plastron réfrigéré | argent, soufre | Forgeron calciné, Braise vivante, Souffle des forges | Le Forgeron Oublié |
| 4 | La Mer de Cristal (fait, voir 11 octies) | Résonance : Élixir de silence, Heaume de cristal | cobalt, cristal résonant | Noyé des cristaux, Archer de cristal, Tourbillon d'éclats | Le Colosse de Cristal |
| 5 | Le Néant (fait, voir 11 octies) | Aspiration du vide : Sérum de gravité, Bottes du Néant | endérite, éclats de néant | Rôdeur du Néant, Mite du vide, Tourelle du Néant | Varenne, le Veilleur du Néant |

| 6 | Le Vide inversé (façon End) | Chute : grappin, bottes | endérite, chorus | Endermen, shulkers | (à venir) |
| 7 | Le Cœur (inventé) | Corruption qui monte avec le temps | cardium | Créatures organiques | Le Cœur de l'Abysse |

Remonter : gratuit depuis le monte-charge du camp ; ailleurs, il faut une **Fusée de détresse**
(5 s immobile). Mourir dans une strate : on garde son équipement mais on **perd tout le butin
transporté** (minerais, drops), qui tombe au sol ; réapparition à Bordgouffre.

## 4. L'usine (la Concession)

### 4.1 Machines

Les machines sont des blocs posés à la Concession, avec un nom flottant au-dessus. Clic droit = menu
(déposer, récupérer, choisir la recette, état). Elles s'achètent à l'Ingénieure de Bordgouffre.

| Machine | Bloc | Fait |
|---|---|---|
| Générateur | lanterne marine | Brûle du charbon → énergie pour toute la Concession |
| Concasseur | meule | 1 minerai → 2 poudres |
| Laveur | chaudron (eau à côté) | poudre → poudre lavée |
| Fonderie | briques du Nether sculptées | poudre lavée → lingot (2 poudres non lavées → 1 lingot, 1 minerai → 1 lingot) ; 4 lingots de cuivre + 2 de fer → 1 bronze |
| Raffinerie | quartz sculpté | lingot → lingot pur |
| Alliageur | table de forgeron | fer pur + 2 charbon → acier ; 3 cuivre pur + 1 étain pur → 2 bronze (vendu une fois la strate 2 ouverte) |
| Assembleur | table d'archerie | composants et équipement (recette choisie) |
| Entrepôt | grille de cuivre | stockage de 2 000 objets, peut renvoyer vers une autre machine |
| Quai d'expédition | tuf ciselé | vend automatiquement à la bourse tout ce qui y arrive |
| Quai de réception | grille de cuivre oxydée | reçoit le minerai des foreuses |
| Foreuse | magnétite | posée sur un filon dans une strate, extrait en continu |

### 4.2 Logistique : les entonnoirs

Un entonnoir **sous** une machine récupère ce qu'elle produit ; il suit sa direction (et celle des
entonnoirs suivants, 32 au plus) jusqu'à la première machine rencontrée. Un entonnoir qui pointe
**dans** une machine la remplit avec ce qu'il contient (pour l'alimenter depuis un coffre). Le plugin mémorise ces
liaisons : ensuite tout se passe dans le plugin (rien ne transite vraiment dans les entonnoirs), ce
qui permet à l'usine de tourner sans que ses chunks soient chargés.

**Convoyeurs (1.1.0)** : objet « Convoyeur » (Margaux, 16 pour 80 éclats), posé dans la Concession
comme une terracotta émaillée magenta tournée dans le sens où regarde le joueur (la flèche du bloc montre
le sens). Une ligne qui part collée à une machine, dans son sens, est suivie (virages libres, ±1 bloc de
hauteur, 96 blocs au plus) jusqu'à la machine du bout : débit 8 objets/s (`machines.debit-convoyeur`),
contre 4 pour les entonnoirs, et les objets défilent sur le tapis (item_display) quand un joueur est à
moins de 48 blocs. Les entonnoirs sous la machine gardent la priorité s'il y en a.

**Aspect des convoyeurs** : le pack ne remplace plus le modèle vanilla de la terracotta émaillée magenta (il
changeait toutes celles du serveur, maps du Hunger Games comprises). Classe `Belts` : chaque terracotta
émaillée magenta du monde des Concessions est recouverte d'une item_display du cube `eemicraft:abysse/convoyeur`
(modèle `eemicraft:block/convoyeur`, échelle 1,004, lumière = max des voisins), tournée autour de Y pour que les
chevrons du dessus montrent le sens du transport (`Machines.beltDir`, comme la flèche vanilla avant). Le client
retourne toute item_display de 180° autour de Y (vu dans `DisplayRenderer$ItemDisplayRenderer` du jar 26.3),
d'où l'angle atan2(dx, dz). Positions gardées dans le PDC du chunk (`belts`, relevé complet une fois par chunk
`belts_scan`, puis pose/casse), habillages créés seulement pour les chunks chargés, vérifiés toutes les 2 s
(convoyeur disparu, sens ou lumière changés), supprimés au déchargement du chunk ; non persistants, clé
`machine_label` + `belt_skin` (anti-doublon). L'objet Convoyeur porte le même modèle (`Items.MODELS`). Les
icônes des menus (Margaux, Guide, liaison) restent la terracotta vanilla.

**Logistique (1.4.0)**, débloquée par le niveau d'Ingénieur (achat refusé avant, chez Margaux ; affiché
dans l'écran des niveaux) : **Groupeur** (niv. 5, 400, cuivre ciselé ciré) : tampon de 64 qui renvoie tout ce
qui arrive par sa sortie unique ; **Séparateur** (niv. 10, 900, cuivre ciselé exposé ciré) : jusqu'à 4 sorties
(une ligne de convoyeurs par côté) + un entonnoir dessous, objets répartis à tour de rôle, sortie pleine sautée ;
**Trieur** (niv. 15, 2 500, cuivre ciselé oxydé ciré) : idem, avec un filtre (un objet) par sortie, choisi dans
son menu (cases 19/21/23/25 = Nord/Est/Sud/Ouest) ; un objet va vers une sortie à son filtre, sinon vers une
sortie sans filtre, sinon il attend. Débit des sorties : celui des convoyeurs (8/s au total).

**Plafond d'entrée par objet** : l'entrée d'une machine est un compteur par objet (pas des cases). Avant, les
machines de fabrication avaient un plafond TOTAL de 128 : un ingrédient qui arrivait plus vite que l'autre
(2 charbons + 1 fer pur à l'Alliageur) remplissait les 128 et l'autre ne pouvait plus entrer, machine bloquée.
Maintenant les machines qui fabriquent (concasseur, laveur, fonderie, raffinerie, alliageur, assembleur,
électrolyseur, serre, cuisine) et les générateurs (charbon / combustible) n'ont plus de plafond total mais
**64 de chaque objet** (`machines.input-cap`, jamais sous la quantité qu'une recette demande). Au-delà,
l'objet n'entre pas et attend en amont : dans la sortie de la machine précédente (qui finit par s'arrêter
« Sortie pleine » à 256), dans l'entonnoir, ou dans le sac (dépôt à la main). Rien n'est détruit ni jeté : les
convoyeurs et liaisons sont simulés (aucun objet réel dessus), le transfert prend seulement ce qui a de la place
et passe à l'objet suivant de la sortie. Une machine qui a déjà plus de 64 d'un objet le garde et le consomme ;
les autres ingrédients entrent quand même. Un objet qu'aucune recette de la machine n'utilise est refusé
(c'était déjà le cas) ; à l'Assembleur, seuls les ingrédients de la recette choisie entrent, et ceux d'une
ancienne recette restés dans l'entrée ne bloquent plus rien. Le menu affiche « Quantité : 64/64 (plein) ».
Entrepôt, quais, groupeur, séparateur, trieur : inchangés (plafond total : 2 000, 64...).

### 4.2 bis Guide de l'usine (1.1.0)

`/abysse guide [machine]`, l'étoile (case 20), le « ? » du menu de chaque machine, Margaux : une fiche
par machine (rôle, quoi lui donner, recettes avec durée et énergie), une maquette du menu d'une machine
avec chaque case expliquée, une page convoyeurs/entonnoirs, une page énergie. La première fois qu'un joueur
pose un type de machine, 3 lignes d'explication et un lien [Lire sa fiche] dans le chat.

### 4.3 Énergie

Chaque Concession a une réserve d'énergie (1 000 par générateur posé). Un générateur brûle un charbon
toutes les 10 s s'il en a, et donne 40 d'énergie. Chaque opération de machine coûte de l'énergie.
Pas de câbles dans le premier jet (réseau « sans fil » dans la parcelle).

### 4.4 Usine hors ligne

L'usine tourne tant qu'un membre de la Compagnie est connecté, puis encore `offline-hours` (12) heures
après la dernière déconnexion. Les foreuses aussi.

### 4.5 Foreuses

Posées sur un bloc de filon d'une strate (minerai selon le filon), elles produisent un minerai toutes
les 20 s (plus vite avec le métier d'Ingénieur) et l'envoient par le monte-charge au Quai de réception
de la Compagnie (stock virtuel tant qu'il n'y en a pas). Elles s'usent (1 % toutes les 3 min, plus
vite quand des monstres sont autour) et s'arrêtent à 0 % : Kit de réparation au clic droit.

### 4.6 Chaîne de valeur (prix de base en éclats)

| Produit | Prix | Produit | Prix |
|---|---|---|---|
| Charbon | 2 | Minerai de cuivre / fer / étain | 3 / 4 / 6 |
| Poudre | 2 / 3 / 4 | Poudre lavée | 3 / 4,5 / 6 |
| Lingot | 7 / 10 / 14 | Lingot pur | 12 / 17 / 24 |
| Bronze | 60 | Acier | 55 |
| Engrenage | 45 | Bobine | 32 |
| Filtre | 40 | Spore lumineuse | 8 |

Un minerai de cuivre vaut 3 ; transformé jusqu'au lingot pur (2 lingots purs par minerai) il vaut 24,
et en bronze encore plus.

## 5. La bourse

- Chaque produit a un prix de base et une **pression** : chaque unité vendue par n'importe quel
  joueur augmente la pression. Cours = base × gains × (P + (1 − P) × e^(−pression / élasticité)),
  P = plancher doux (`bourse.plancher-doux`, 0,62) : la baisse ralentit en approchant de −38 % et ne
  l'atteint jamais (pas de mur). La pression est bornée à 3 × l'élasticité (`pression-max`).
- La pression ne grimpe pas avec le nombre de joueurs : une unité vendue ajoute
  1 / (1 + 0,5 × (joueurs actifs dans l'Abysse − 1)) (`normalisation` ; 5 joueurs → 1/3), et au-delà de
  0,5 × élasticité ajoutée dans l'heure (`pression-heure`, mémoire glissante de demi-vie 1 h) chaque
  vente ne compte plus que pour 25 % (`au-dela`).
- La pression revient vers 0 avec une **demi-vie de 2,5 h** (`remontee-heures`) : un produit inondé remonte vite.
- Exemple (calculé hors ligne) : 1 000 unités vendues en 1 h par 5 joueurs → minerai de cuivre (élasticité
  4 000) −3 %, lingot de cuivre pur (1 500) −7 %, bronze (700) −13 %, circuit (400) −18 % (avant : −21 %,
  −47 %, −74 %, −80 %). 5 000 unités en 1 h : −12 % à −27 %.
- **Indice du marché** = moyenne des cours / prix de base (sans le réglage gains) de tous les produits ;
  il ne peut pas descendre sous −38 % et il faut une pression de 1,6 × élasticité sur TOUS les produits
  pour atteindre −30 %. Affiché sur le tableau de Bordgouffre et dans `bourse.json`.
- Acheter à la bourse coûte 125 % du meilleur prix de vente possible (le plus haut entre le cours sans
  gains, la base × gains et le prix d'une commande du jour ouverte) et ne bouge plus le cours : un achat
  revendu, tout de suite ou plus tard, rapporte toujours moins qu'il n'a coûté.
- Au chargement, les pressions d'une ancienne version sont ramenées entre 0 et `pression-max` et les
  relevés de l'historique entre plancher et base × gains.
- **Commandes du jour** : 2 commandes de PNJ pour tout le serveur (« la guilde des forgerons achète
  3 000 lingots de cuivre à +50 % »), remplies à plusieurs, renouvelées chaque jour.
- Menu `/bourse` : prix, tendance sur 6 h (▲ ▼ et mini-courbe), vendre 1 / 16 / tout, acheter 1 / 16.
- Tableau de la bourse en hologramme à Bordgouffre.
- `plugins/EemiAbysse/bourse.json` : cours publics pour le site et le bot Discord. Champs gardés ;
  `base` = base × gains (le prix « normal » réellement payé, pour que price / base donne le vrai écart) ;
  ajoutés : `gains`, `floor`, `market_index`, `market_change_pct` et, par produit, `base_raw`, `ratio`,
  `change_pct`, `buy_price`.

## 6. Les métiers

Niveau 1 à 50 chacun, gagnés en jouant.

| Métier | XP | Bonus |
|---|---|---|
| Mineur | minerais cassés | +1 % de chance de double minerai par niveau ; Célérité I au niveau 15, II au 35 |
| Ingénieur | opérations des machines de la Compagnie | machines et foreuses +1 % plus rapides par niveau (meilleur niveau de la Compagnie) |
| Chasseur | monstres de l'Abysse tués | +1,5 % de dégâts contre eux par niveau |
| Marchand (à venir) | ventes | meilleurs prix, commandes rares |
| Forgeron (à venir) | équipement fabriqué | qualité de l'équipement |
| Explorateur (à venir) | découvertes, carnets | contraintes atténuées |

Paliers (1.0.1), donnés en passant le niveau, visibles niveau par niveau dans le menu des métiers :

| Niveau | Mineur | Ingénieur | Chasseur |
|---|---|---|---|
| 5 | 3 fusées | Entrepôt | 300 éclats |
| 10 | Pioche en bronze | Quai d'expédition | Épée en bronze |
| 15 | Célérité I | 1 000 éclats | Plastron en bronze |
| 20 | 600 éclats | Foreuse | 800 éclats |
| 25 | 64 charbon | 3 kits de réparation | Bottes en bronze |
| 30 | 1 500 éclats | Laveur | Jambières en bronze |
| 35 | Célérité II | 3 000 éclats | 2 500 éclats |
| 40 | Pioche en acier | Foreuse | Épée en acier |
| 45 | 3 000 éclats | 5 kits de réparation | 4 000 éclats |
| 50 | 6 000 éclats | 8 000 éclats | 8 000 éclats |

## 7. Histoire, PNJ et quêtes

**L'intrigue** : il y a 30 ans, l'expédition Varenne est descendue jusqu'au fond et n'est jamais
revenue. Ses carnets sont dispersés dans les strates. Bordgouffre vit de la mine, mais depuis peu
l'Abysse « remonte » : des monstres apparaissent plus haut qu'avant.

**PNJ de Bordgouffre** :

| PNJ | Rôle |
|---|---|
| Le Vieux Mineur (Gaspard) | Fil de l'histoire principale, conseils de départ |
| La Banquière (Odette) | Bourse, commandes du jour |
| L'Ingénieure (Margaux) | Machines |
| Le Marchand (Bastien) | Outils, armes, nourriture, torches, fusées |
| Le Notaire (Maître Lebrun) | Concessions : obtenir, agrandir, s'y rendre |
| Le Liftier (Léon) | Monte-charge vers les strates débloquées |
| La Cartographe (Inès) | Carnets de Varenne (collection), quêtes d'exploration |

**Quêtes** : une chaîne principale (avec dialogues), les commandes du jour à la bourse, et la
collection des carnets (5 pages par strate). À venir : chantiers communautaires (tout le serveur
participe pour réparer le grand monte-charge, etc.).

Chaîne principale du premier jet (strates 1 et 2) :

1. Parler à Gaspard (kit de départ : pioche, épée, Lampe de mineur, pain, fusées).
2. Descendre dans les Galeries.
3. Miner 20 minerais de cuivre.
4. Vendre à la bourse (100 éclats gagnés).
5. Obtenir sa Concession auprès du Notaire.
6. Poser un Générateur et une Fonderie, produire 10 lingots de cuivre.
7. Tuer 15 monstres des Galeries.
8. Acheter un Concasseur et produire 30 poudres.
9. Trouver 2 pages des carnets de Varenne.
10. Vaincre le Contremaître.
11. Fabriquer un Masque filtrant (Assembleur).
12. Descendre dans la Champignonnière.
13. Miner 30 minerais d'étain, produire 10 aciers (« L'âge de l'acier », Alliageur ; « 10 bronzes » avant la refonte de l'équipement).
14. Poser une foreuse sur un filon.
15. Vaincre la Reine Mycélienne.
16. (suite à la strate 3)

## 8. Compagnies

Chaque joueur a une Compagnie (seul au début). Une Compagnie a : ses membres (4 max), sa bourse
d'éclats commune, sa parcelle, ses machines et foreuses, sa réserve d'énergie. Commandes :
`/abysse compagnie inviter|accepter|quitter|expulser|info`. Rejoindre une autre Compagnie impose de
quitter la sienne (ses machines restent à l'ancien chef, ou sont rendues si on était seul).

Parcelle : 32 × 32 au départ, agrandie à 48 (2 000 éclats), 64 (8 000), 96 (25 000). Construction
seulement dans sa parcelle (y 40 à 200). `/abysse visiter <joueur>` pour voir celle d'un autre.

## 9. Monnaie

Les **éclats** (⬥), propres à l'Abysse, sans lien avec les pièces d'EemiCore. Départ : 200 éclats.
`/abysse eclats`, `/abysse payer <joueur> <montant>`.

## 10. Commandes

`/abysse` (alias `/aby`) : sans argument, va à Bordgouffre (ou ouvre le menu si on y est).
`base`, `visiter <joueur>`, `remonter`, `bourse` (aussi `/bourse`), `metiers`, `journal`,
`compagnie …`, `eclats`, `payer`, `carnets`.
Admin (`eemiabysse.admin`) : `reload`, `give <joueur> <objet> [n]`, `donner <joueur> <éclats>`,
`gardien <strate>`, `tp <strate>`, `debloquer <joueur> <strate>`, `build`, `bourse reset`,
`quete <joueur> <n>`.

## 11. Technique

- Plugin `EemiAbysse` (source `EemiAbysse/`), Paper 26.3. Données en YAML dans
  `plugins/EemiAbysse/` (`joueurs/<uuid>.yml`, `compagnies.yml`, `bourse.yml`), sauvegardées toutes
  les 5 min et à l'arrêt.
- Objets de l'Abysse : objets vanilla avec un identifiant dans le PDC (`eemiabysse:id`), nom et
  description ; un `item_model` du pack pourra les habiller ensuite.
- Monstres : entités vanilla avec un nom, des attributs (vie, dégâts, taille) et leur type dans le PDC ;
  les apparitions naturelles sont coupées dans les mondes de l'Abysse, le plugin fait apparaître ses
  monstres autour des joueurs dans le noir.
- Mémoire : les strates vides sont déchargées au bout de 5 min ; l'usine est simulée sans charger
  de chunk. Le serveur étant à ~94 % de ses 4 Go, prévoir plus de RAM quand les strates 3+ arriveront.
- À la mise en place sur le serveur : groupe Multiverse-Inventories `abysse`, mondes ajoutés aux
  mondes désactivés de DeluxeHub, à la disable-condition TAB, aux `disabled_worlds` d'AuraSkills,
  jeu `abysse` dans EemiCore, entrée dans la boussole.

## 11 bis. Ajouts 1.1.0

- **Minerais qui repoussent** : un minerai cassé dans une strate devient de la bedrock (`minerais.bloc-epuise`)
  et redevient le même minerai après 180 s ± 25 % (`minerais.regeneration-secondes`), gardé dans
  minerais.yml ; clic gauche dessus = temps restant.
- **Brocante de Prosper** (nouveau PNJ au nord-ouest de la place, ou l'étoile case 24) : Totem de garde
  (1 500, 5 usages : garde le butin à la mort), Larme de guérison (600, 5 usages, vie pleine, 1 min de
  recharge), Trèfle du prospecteur
  (2 000, 100 usages : 1 chance sur 4 de doubler), Boussole du dernier souffle (800, 3 usages : retour au
  lieu de sa mort < 20 min), Lampe éternelle (4 000, sans charbon). Usages = barre de durabilité.
- **Bastien** : pioche et épée de base gratuites (une à la fois) ; équipement en bronze et en acier à
  vendre (900 à 2 600).
- **Pages de carnet par joueur** : la page d'un campement n'est plus un objet du tonneau ; chaque joueur
  qui ouvre le tonneau la reçoit s'il ne l'a pas (page tirée d'après la position du campement).
- **Tonneaux de campement personnels** (classe `Campements`, remplace la « Fouille » de la 1.3.1) : chaque
  joueur fouille SA copie de chaque tonneau de campement (butin tiré à sa première ouverture, même table
  `fillBarrel`) ; ce qu'il prend ne manque pas aux autres, ce qu'il y laisse reste dans sa copie ; plusieurs
  joueurs peuvent ouvrir le même tonneau en même temps. Le vrai tonneau reste vide (l'ancien contenu partagé
  est retiré à la première ouverture) et ne se casse pas (sauf admin). Un tonneau de campement = tonneau en
  (8, y, 8) d'un chunk `Gen.campChunk`, y noté dans les données du chunk à sa génération (`camp_barrel_y`) :
  un tonneau posé par un joueur reste vanilla. État dans `campements/<uuid>.yml` par strate et par **époque**
  (nombre posé dans les données du monde de la strate, `camp_epoch`) : après une remise à neuf le monde est
  neuf, l'époque change, les copies sont oubliées et tous les tonneaux sont pleins à nouveau ; le fichier ne
  garde que les tonneaux ouverts dans la strate actuelle. Les pages lues (journal) ne sont jamais redonnées.
- **Pages garanties** : à la première ouverture d'un tonneau, s'il porte une page (`carnets.chance`, 40 %) ou
  après `carnets.garantie` (3) tonneaux sans page, le joueur reçoit une page de la strate qu'il n'a ni lue, ni
  sur lui, ni réservée dans un autre tonneau (celle du campement si possible). Page non prise = elle reste
  réservée dans ce tonneau et revient à chaque ouverture. Valable pour toute strate dont `Quests.page` a un
  texte. Pendant une quête `PAGES`, dans une strate, la barre d'action montre (secondes impaires, rayon
  `carnets.indice-rayon` 160) le campement le plus proche pas encore fouillé ou dont la page attend le joueur ;
  journal : indice sous la quête. `carnets.indice: false` coupe la flèche.

- 1.3.1 : le minerai cassé va TOUJOURS directement dans le sac (aux pieds si plein) ; l'Aimant de mineur, devenu inutile, n'est plus vendu.

## 11 bis². Équipement à fabriquer (1.5.0)

À l'Assembleur, verrouillé par niveau de métier (vérifié pour le joueur qui choisit la recette) :
| Pièce | Recette | Métier requis | Effet |
|---|---|---|---|
| Casque filtrant (diamant) | 4 acier, 2 filtres, 2 étain pur | Ingénieur 4 | Prot II, filtre les spores |
| Plastron / jambières / bottes en acier (diamant) | 8/7/4 acier + 4/3/2 bronze | Chasseur 5 | Prot II (bottes : Chute amortie III) |
| Épée abyssale (netherite) | 6 acier, cœur du Contremaître, 16 spores, 2 engrenages | Chasseur 12 | Tranchant V, vol de vie 15 % |
| Pioche abyssale (netherite) | 6 acier, cœur du Contremaître, 4 engrenages, 2 bobines | Mineur 12 | Efficacité V, +15 % de minerai doublé |
| Casque abyssal | 5 acier, cœur de la Reine, 2 filtres | Ingénieur 10 | Prot IV, filtre les spores |
| Plastron / jambières / bottes abyssaux | 10/9/6 acier, cœur de la Reine, bronze | Chasseur 12 | Prot IV (bottes : Chute amortie IV) |
Panoplies (dans les strates) : 4 pièces d'acier ou abyssales = Résistance I ; 4 abyssales = + Régénération I.
Repère : un joueur qui ouvre la strate 2 est vers Chasseur 5 / Ingénieur 3-4 (Enosik : 504 et 263 XP).

**1.7.0 : tout ce qui sert après la strate 2 coûte bien plus cher** (demande de Thibault : la strate 2 est finie en 2-3 h, on la laisse ainsi, mais la suite doit être longue). Trois composants intermédiaires, invendables, à l'Assembleur : Plaque blindée (3 acier + 2 bronze, Ingénieur 6), Moteur (4 engrenages + 4 bobines + 2 fer pur, Ingénieur 8), Noyau de spores (12 spores + 4 glandes + 2 étain pur, Ingénieur 8). Recettes revues :
| Pièce | Recette 1.7.0 | Métier |
|---|---|---|
| Foreuse renforcée | 2 moteurs, 4 plaques, 10 étain pur, 6 bobines (120 s) | Ingénieur 10 |
| Tête de forage | 1 moteur, 3 plaques, 8 étain pur (90 s) | Ingénieur 10 |
| Épée abyssale | cœur du Contremaître, 4 plaques, 3 noyaux, 6 acier | Chasseur 15 |
| Pioche abyssale | cœur du Contremaître, 2 moteurs, 3 plaques, 2 noyaux | Mineur 15 |
| Casque abyssal | cœur de la Reine, 4 plaques, 3 noyaux, 4 filtres | Ingénieur 14 |
| Plastron / jambières / bottes abyssaux | cœur de la Reine, 10/8/5 plaques, 4/4/3 noyaux | Chasseur 15 |
La panoplie d'acier (strate 2) ne change pas.

## 11 quinquies. Principes des strates 3 et suivantes (à construire)

Consigne de Thibault (01/11/2026) : « pour les prochaines strates il faut vraiment devoir farmer à la main puis automatiser longtemps pour avancer, un peu comme Satisfactory : nouvelles machines, nouvelles ressources ». Règles à suivre :
1. **Chaque strate apporte au moins une ressource nouvelle** qu'on ne trouve que là (ex. strate 3 : argent, quartz noir ; strate 4 : or, soufre…), et une **machine nouvelle** indispensable pour la traiter (ex. Électrolyseur, Haut-fourneau, Centrifugeuse, Raffinerie chimique), débloquée par niveau de métier.
2. **Phase à la main d'abord** : la nouvelle ressource se mine et se transporte à la main au début (foreuse impossible tant que la tête de forage de la strate n'est pas fabriquée), puis s'automatise avec des foreuses de palier supérieur.
3. **Chaînes plus longues** : chaque palier de produit demande les produits du palier d'avant (comme Plaque → Moteur → Foreuse), avec des quantités croissantes, pour qu'une usine de la strate 2 doive grandir (séparateurs, trieurs, plusieurs fonderies) avant de nourrir la strate 3.
4. **Énergie** : un nouveau type de générateur (ex. à combustible raffiné) devient nécessaire, les générateurs à charbon ne suffisent plus.
5. **Gardien** : son trophée sert à fabriquer la tête de forage ou la machine clé de la strate suivante, pour qu'on ne saute aucune étape.
6. Repère de durée : plusieurs jours de jeu par strate, pas quelques heures.

## 11 sexies. Strate 3 « Les Forges Englouties » (2.0.0) et rééquilibrage des métiers

Repère donné par Thibault juste avant la Reine (strate 2) : Ingénieur 13, Chasseur 8, Mineur 6 (Enosik : 3 069 / 1 149 / 851 XP).
Chaque nouvelle strate doit d'abord repousser (consigne de Thibault) : minerais trop durs, monstres trop forts, machines verrouillées par le métier.

- **Monde** `abysse_s3` : basalte, pierre noire, deepslate ; sol de basalte lisse et de magma (qui brûle), petites flaques de lave sous y 30, stalactites ; biome Deltas de basalte (cendres). Même plan que les autres strates (camp, galerie, antre à ~320 blocs). Générateur testé hors ligne : ~6 ms/chunk, 20 % de vide, galerie jamais bouchée.
- **Minerais** : argent (`DEEPSLATE_DIAMOND_ORE`, 22 %) et soufre (`NETHER_GOLD_ORE`, 24 %) **durs** : seule une Pioche en acier (Mineur 10) ou abyssale les casse ; la dynamite ne les fend pas. Charbon et fer aussi. Filons : bloc de fer (argent), bloc d'or brut (soufre) ; foreuse renforcée obligatoire.
- **Chaleur** : 1 cœur toutes les 8 s, sauf Résistance au feu (Infusion glacée de la Cuisine : 2 champignons + 2 légumes + 1 spore, 4 min), Plastron réfrigéré, ou panoplie abyssale complète.
- **Chaîne de l'argent** : minerai → Concasseur (2 poudres) → Laveur → **Électrolyseur** (2 poudres lavées = 1 lingot d'argent pur, 30 énergie). La fonderie ne sait pas faire l'argent.
- **Nouvelles machines** (Margaux, Ingénieur 16) : Électrolyseur (15 000 ; aussi 2 soufre + 2 charbon = 3 combustibles) ; Générateur thermique (12 000 ; 1 combustible / 30 s, 12 énergie/s, +3 000 de réserve, 4 cellules possibles).
- **Nouveaux produits** : Circuit d'argent (Assembleur, Ingénieur 17 : 2 argent pur + 2 bobines + 1 cuivre pur, se vend 140) ; Plastron réfrigéré (Chasseur 12 : 6 plaques blindées + 3 circuits + 4 argent pur ; Prot III, Protection contre le feu III, immunité à la chaleur).
- **Monstres** : Forgeron calciné (squelette wither, 48 PV, 9 dégâts), Braise vivante (cube de magma, 30 PV, 7), Souffle des forges (blaze, 38 PV, 8) : soufre, charbon, argent.
- **Gardien** : Le Forgeron Oublié (squelette wither ×1,9, 1 000 PV, 13 dégâts, hache en netherite) : pluie de braises sur les joueurs (6 dégâts + feu), apprentis calcinés, en phase 2 le sol enflamme ceux qui n'ont pas de résistance au feu. Trophée : Cœur du Forgeron (pour la strate 4).
- **Quêtes** (avant « Plus bas ») : Les Forges Englouties (descendre, 3 infusions offertes), Le métal lunaire (40 argent), La foudre en bouteille (acheter un Électrolyseur), Le feu raffiné (30 combustibles), L'argent pur (40 lingots), Le Forgeron Oublié. Carnets : 5 pages de la strate 3.
- **Métiers rééquilibrés** (Thibault : « trop cheatés », s'inspirer de Hypixel SkyBlock et Satisfactory) : courbe d'XP exponentielle `need(L) = 100 × 1,15^(L-1)` (niveau 10 ≈ 1 700 XP au total, 15 ≈ 4 000, 25 ≈ 18 000, 50 ≈ 630 000 ; les niveaux actuels changent peu jusqu'à ~13, beaucoup plus lents au-delà) ; petits bonus par niveau : Mineur 0,4 % de minerai doublé par niveau et Célérité I seulement au niveau 25 (plus de Célérité II), Ingénieur +0,4 % de vitesse des machines, Chasseur +0,5 % de dégâts. Paliers : plus d'équipement donné qui casse la progression (Pioche en acier du Mineur 40 et Épée en acier du Chasseur 40 remplacées par des éclats / dynamites). Verrous : Pioche en acier Mineur 10.

## 11 ter. Production hors bourse (1.2.0)

Rien de ceci ne se vend (prix 0, refusé par le quai d'expédition) : ça sert à jouer.
- **Serre** (900, bloc de mousse, eau à côté) : 2 légumes / 20 s sans rien lui donner ; avec des spores
  lumineuses, 1 spore = 4 champignons.
- **Cuisine** (700, établi) : Ration (2 légumes + 1 charbon → 2, rassasiement +6), Café (1 légume + 2 charbon → 2,
  Vitesse I 3 min), Ragoût (2 légumes + 2 champignons, Célérité I 3 min), Soupe anti-spores (2 champignons + 1 glande,
  5 min sans poison des spores + vision nocturne).
- **Modules** (Assembleur), 2 places par machine qui produit (cases 6-7 du menu) : vitesse (+50 % vitesse et
  énergie ; seul module accepté par la foreuse), économie (-40 % d'énergie), rendement (20 % de chances de doubler).
  **Cellule d'énergie** : 4 places dans un générateur, +500 de réserve chacune. Un clic sur le module dans son sac
  l'installe, un clic sur le module installé le reprend ; rendus quand on ramasse la machine.
- **Dynamite** (Assembleur, 3 pour 4 charbon + 1 os + 1 cuivre) : clic droit en strate, roche (pierre, deepslate,
  tuf...) et minerai sautent dans une boule de rayon ~2, minerai ramassé (bonus Mineur compris) et qui repousse ;
  ni filons, ni camp, ni antre, ne blesse personne.

## 11 quater. Remise à neuf des strates (1.3.0)

Tous les jours à 12:00 et 21:30 (`reset.horaires`, fuseau `reset.fuseau` Europe/Paris, `reset.actif`) : annonce 5 min
puis 1 min avant (chat de l'Abysse + titre aux joueurs en strate), joueurs en strate renvoyés à Bordgouffre, combat
de gardien arrêté sans récompense, monde déchargé SANS sauvegarde, dossier supprimé 2 s après (hors fil principal).
La strate est régénérée à la descente suivante avec la même graine : terrain neuf, minerai, campements et pages
reviennent, tout ce qui a été posé ou cassé disparaît. Les foreuses (données de la Compagnie) sont reposées sur leur
filon au rechargement ; les minerais en attente de repousse sont oubliés. Pendant la remise à neuf, descente
refusée (message) et chute dans le gouffre = retour sur la place. Admin : `/abysse reset [strate]`.

## 12. Premier jet (version 1.0.0) et suite

Premier jet : Bordgouffre simple, Concessions, strates 1 et 2 avec leurs gardiens, 11 machines, bourse
dynamique avec commandes du jour, 3 métiers, chaîne principale jusqu'à la strate 2, carnets.

Ensuite, par mises à jour : une strate toutes les 1 à 2 semaines (3 à 7), métiers Marchand /
Forgeron / Explorateur, câbles et batteries, ordres d'achat entre joueurs, chantiers communautaires,
habillage des objets et machines par le pack, Bordgouffre construite par les builders.

## 11 octies. Sbires des gardiens plafonnés

Les monstres invoqués par un gardien (Rôdeurs du Contremaître, Gelées et Tisseuse de la Reine, Forgerons calcinés du
Forgeron Oublié, et ceux de tout gardien futur via `Mobs.summon`) portent la marque `sbire` (PDC, = strate du gardien)
et ne sont jamais sauvegardés. Plafond de sbires vivants par combat : `gardiens.max-sbires` (6) + `gardiens.sbires-par-joueur`
(2) par joueur en plus dans l'antre, au plus `gardiens.max-sbires-absolu` (12) ; une invocation n'ajoute que la différence
(rien si l'antre est plein, et la réplique du gardien n'est alors pas dite). Les monstres ordinaires de la strate ne comptent pas.
Les sbires disparaissent à la fin du combat (victoire, abandon après 20 s sans joueur, remise à neuf, arrêt du plugin).
Les Gelées invoquées sont de taille 1 (échelle doublée) pour ne pas se diviser en petits slimes sans marque.
Pendant un combat, aucune apparition naturelle dans l'antre (rayon de l'antre + 4).

## 11 septies. Strate 3 durcie (2.3.0, 02/11/2026)

Retour de Thibault : strate 3 traversée trop vite à deux, monstres faciles en armure de diamant vanilla. Règles valables dès la strate 3 (`Mobs.HARD_FROM`) et pour toutes les suivantes :

| | Avant | 2.3.0 |
|---|---|---|
| Forgeron calciné | 48 PV, 9 dég., seul | 80 PV, 12 dég., par 2, Wither I au coup |
| Braise vivante | 30 PV, 7 dég., par 2 | 50 PV, 9 dég., par 3, feu au coup |
| Souffle des forges | 38 PV, 8 dég., seul | 60 PV, 11 dég., par 2, Faiblesse I au coup |
| Monstres S3+ | — | armure 8, recul -40 %, +3 par joueur |
| Armure ordinaire | — | +15 % de dégâts reçus par pièce non Abysse |
| Forgeron Oublié | 1 000 PV, 13 dég., +60 %/joueur | 1 800 PV, 17 dég., +80 %/joueur, offrande 16 argent pur + 4 circuits |
| Quêtes | 40 argent / 30 combustible / 40 argent pur | 150 / 120 / 160 |

Pour la strate 4 : partir de ces valeurs (monstres ~×1,5, gardien avec offrande plus chère, quêtes de production en centaines), l'armure abyssale devient l'équivalent de l'armure ordinaire d'ici (une armure de la strate 4 à fabriquer).

## 11 octies. Progression de l'équipement : bronze en strate 1, acier en strate 2

Retour de Thibault : le bronze demandait l'étain (strate 2) et l'Alliageur à 6 000, qui débloquait l'acier en même temps ; personne ne faisait le bronze, on passait directement à l'acier. Chaque palier a maintenant sa période :

| Palier | Métal | Où | Débloqué | Pièces (Assembleur) |
|---|---|---|---|---|
| Strate 1 | Bronze | **Fonderie** (offerte à la quête 5) : 4 lingots de cuivre + 2 lingots de fer → 1 bronze (8 s, 5 énergie) | dès la Fonderie ; Assembleur à **1 000** (au lieu de 2 000) | casque 5 (nouveau), plastron 8, jambières 7, bottes 4 bronze (Protection I) ; épée 2 bronze + 1 lingot de fer ; pioche 3 bronze + 2 lingots de fer (plus d'engrenage, donc plus besoin de Raffinerie) |
| Strate 2 | Acier | **Alliageur** : 1 fer pur + 2 charbon → 1 acier | vendu par Margaux **seulement une fois la strate 2 ouverte** (`Machines.STRATA_UNLOCK`, Contremaître vaincu), prix **4 000** (au lieu de 6 000) | casque filtrant (Ingénieur 4), plastron/jambières/bottes (Chasseur 5), épée, pioche (Mineur 10) : inchangés |
| Strate 3 | Abyssal | Assembleur, trophées des gardiens | inchangé | inchangé |

- L'Alliageur garde la recette 3 cuivre pur + 1 étain pur → 2 bronze : le bronze « en grand », avec l'étain de la strate 2, pour les plaques blindées, les modules de rendement et les pièces d'acier.
- Coût estimé de la panoplie de bronze complète (casque, plastron, jambières, bottes, épée, pioche = 29 bronze) : 116 lingots de cuivre + 61 lingots de fer, soit ~177 minerais de la strate 1 et ~20 min de Fonderie, plus l'Assembleur (1 000). Avec les récompenses des quêtes 1 à 9 (~700 éclats avec `gains` 0,6) et le Concasseur payé, il manque ~700 éclats pour l'Assembleur (~170 lingots vendus) : panoplie de bronze vers 1 h à 1 h 30 de strate 1, avant le Contremaître (estimation, non mesurée en jeu). Valeur : 4 cuivre + 2 fer = 48 éclats de base pour un bronze à 60 (+25 %, moins que la Raffinerie) : couler du bronze pour le revendre n'accélère pas beaucoup.
- Quête 13 « L'âge du bronze » devenue « L'âge de l'acier » (10 aciers à l'Alliageur, même place dans la chaîne, même récompense) ; un joueur déjà dessus garde son compteur, qui compte désormais l'acier. Nouvelle réplique de « La chaîne » (quête 8) : la Fonderie coule le bronze.
- Paliers de métier : les pièces de bronze (Mineur 10, Chasseur 10/15/25/30) arrivaient bien après la strate 1 ; remplacées par des éclats (Mineur 10 : 600 ; Chasseur 10 : 500, 15 : 700, 25 : 1 500, 30 : 2 000). Le tableau des paliers de la section 6 est l'ancien (1.0.1).
- Compatibilité : les Alliageurs déjà posés ou dans les sacs restent utilisables (seul l'achat est verrouillé) ; la Fonderie accepte en plus les lingots de cuivre et de fer ; recettes repérées par leur id, aucune donnée de machine ne change.
## 11 nonies. Strates 4 « La Mer de Cristal » et 5 « Le Néant »

Consigne de Thibault (répétée) : chaque strate doit d'abord repousser (minerais trop durs, monstres plus forts, armure percée, machines verrouillées par le métier), chaînes plus longues, une ressource et une machine nouvelles par strate, phase à la main avant l'automatisation, plusieurs jours par strate. Le trophée du gardien N est obligatoire pour la strate N+1. Code commun des deux strates : `Profondeurs.java` (dangers, paliers d'armure, élixirs, fin du contenu) ; le reste suit le modèle de la strate 3 (Gen, Mobs, Quests, Machines, Items).

**Paliers (outil, foreuse, armure)**

| Strate | Pioche requise pour ses minerais neufs | Foreuse minimum sur ses filons | Armure non percée |
|---|---|---|---|
| 3 | Pioche en acier (palier 3) | renforcée (2) | n'importe quelle pièce de l'Abysse |
| 4 | **Pioche de forge** (palier 5, Cœur du Forgeron) | **à induction** (3) | panoplie de cristal |
| 5 | **Pioche de cristal** (palier 6, Cœur de cristal) | **du Vide** (4) | panoplie du Néant |

Armure percée (`Profondeurs.pierce`, remplace le calcul de la 2.3.0) : chaque pièce sous le palier de la strate (vanilla 0, bronze 1, acier 2, abyssale / plastron réfrigéré 3, cristal 4, Néant 5) ajoute +15 % aux coups des monstres, +30 % si elle a deux paliers de retard ou plus. Strate 3 : inchangé (seules les pièces vanilla). Strate 4 en armure abyssale complète : +60 % ; en diamant vanilla : +120 %. Monstres : armure 8 en strate 3, +4 par strate (12, 16), résistance au recul 0,5 / 0,6. Gardiens 4-5 : armure 16.

**Strate 4 « La Mer de Cristal »** (`abysse_s4`, biome grottes luxuriantes)
- Monde : calcite, deepslate, tuf ; grappes et bourgeons d'améthyste au sol et au plafond, flèches de calcite coiffées d'améthyste (jamais à moins de 5 blocs de la galerie), **cavités noyées sous y 30** (la mer, cornichons de mer au fond). Camp, antre et galerie au-dessus de l'eau.
- Minerais : cobalt (`DEEPSLATE_EMERALD_ORE`) et cristal résonant (`BUDDING_AMETHYST`), durs (palier 5) ; argent (palier 3), charbon, fer. Filons : bloc d'émeraude (cobalt), bloc d'améthyste (cristal).
- Danger « Résonance » : toutes les 8 s, 1,5 cœur et Fatigue de minage I (10 s), sauf Élixir de silence (Cuisine : 2 champignons + 1 légume + 1 cristal, 4 min), Heaume de cristal (ou Casque du Néant), ou 4 pièces de cristal.
- Machines (Margaux) : **Établi de précision** (tailleur de pierre, 25 000, Ingénieur 18 ; recette au choix comme l'Assembleur, qui est plein) et **Four à induction** (deepslate ciselée, 40 000, Ingénieur 20 : 2 poudres de cobalt lavées + 1 combustible raffiné = 1 lingot de cobalt pur, 20 s, 45 énergie). Concasseur et Laveur traitent le cobalt.
- Chaîne : minerai de cobalt → Concasseur → Laveur → Four à induction (+ combustible de l'Électrolyseur) ; cristal ×3 + poudre d'argent lavée → Lentille (Établi) ; 3 cobalt pur + 2 plaques blindées → Plaque de cobalt ; 2 cobalt pur + 2 circuits + 1 lentille → Bobine résonante.
- Établi, strate 4 : Pioche de forge (Cœur du Forgeron, 24 argent pur, 8 circuits, 3 moteurs, 6 plaques blindées ; Mineur 18), Foreuse à induction (4 moteurs, 10 circuits, 4 plaques de cobalt, 16 cobalt pur ; Ingénieur 21) et sa Tête de forage (Ingénieur 21), panoplie de cristal (Protection IV ; heaume Ingénieur 21, le reste Chasseur 18 ; plaques de cobalt, lentilles, bobines), Épée de cristal (Tranchant VI, vol de vie 10 %, Chasseur 20), Pioche de cristal (Cœur de cristal, Mineur 22). Panoplie de cristal complète = Résistance I + Régénération I partout dans les strates, et protège aussi de la chaleur de la strate 3.
- Monstres (≈ ×1,5 la strate 3) : Noyé des cristaux (noyé, 120 PV, 16 dég., par 2, Lenteur II au coup), Archer de cristal (vagabond, 90 / 13, par 2, Faiblesse), Tourbillon d'éclats (breeze, 100 / 14, Fatigue de minage II). Butin : cristal, cobalt, argent.
- Gardien : **Le Colosse de Cristal** (ravageur ×1,3, 3 000 PV voulus, 24 dég., +80 %/joueur, PV au-delà de 1 024 par `toughness`). Offrande : 24 lingots de cobalt pur, 8 lentilles, 2 bobines résonantes. Capacités : pluie d'éclats (8 dég. + Lenteur II), onde de choc (7 dég. + projection), noyés (+ archer en phase 2), phase 2 carapace (Résistance IV 5 s toutes les 20 s). Trophée : Cœur de cristal.
- Quêtes : L'outil de précision (acheter l'Établi), La Pioche de forge (la fabriquer), La Mer de Cristal (descendre, 3 élixirs), Le métal bleu (300 cobalt à la main), Le Four à induction (400 lingots de cobalt pur), La résonance domptée (40 bobines résonantes), Le Colosse de Cristal. 5 pages de carnet (jours 61-77).

**Strate 5 « Le Néant »** (`abysse_s5`, biome terres stériles de l'End)
- Monde : pierre de l'End, veines d'obsidienne, sol en briques de pierre de l'End, bâtons de l'End au sol et au plafond, piliers d'obsidienne.
- Minerais : endérite (`NETHER_QUARTZ_ORE`) et éclat de néant (`ANCIENT_DEBRIS`, rare), palier 6 ; cobalt (palier 5), argent, charbon. Filons : bloc de quartz (endérite), bloc de netherite (néant). Essence du vide : butin des monstres.
- Danger « Aspiration du vide » : toutes les 10 s, Lévitation 1,5 s, Ténèbres 4 s et 1 cœur, sauf Sérum de gravité (Cuisine : 2 champignons + 1 légume + 1 essence du vide), Bottes du Néant, ou 4 pièces du Néant.
- Machines : **Condenseur du Vide** (obsidienne pleureuse, 120 000, Ingénieur 24 : 2 poudres d'endérite lavées + 1 lentille = 1 lingot d'endérite pur ; 1 éclat de néant + 2 cobalt pur = 2 matières noires ; 1 matière noire + 2 combustibles + 1 essence = 4 charges du vide) et **Réacteur du Vide** (grenouillelampe perlescente, 90 000, Ingénieur 24 : 1 charge par minute, 40 énergie/s, +10 000 de réserve, 4 cellules) : nouvelle énergie, en boucle avec le Condenseur.
- Établi, strate 5 : Plaque de néant (Ingénieur 24), Noyau du vide (Ingénieur 25), Foreuse du Vide et sa tête (Ingénieur 26), panoplie du Néant (Protection V ; 4 pièces = Résistance II + Régénération I ; casque Ingénieur 26, le reste Chasseur 24), Lame du Néant (Cœur de cristal, Tranchant VII, vol de vie 15 %, Chasseur 25).
- Monstres (≈ ×1,5 la strate 4) : Rôdeur du Néant (enderman qui attaque tout de suite, 180 / 22, Ténèbres), Mite du vide (endermite, 70 / 14, par 4, Wither II), Tourelle du Néant (shulker, 140 / 16, Nausée).
- Gardien : **Varenne, le Veilleur du Néant** (évocateur ×1,8, 5 000 PV voulus, 26 dég.). Offrande : 24 lingots d'endérite pur, 6 matières noires, 2 noyaux du vide. Capacités : aspiration d'un joueur (Lévitation II + Ténèbres), orbes du vide (9 dég.), mites et rôdeur ; phase 2 : il se téléporte derrière un joueur, et le vide ronge (Wither II) ceux qui restent à plus de 12 blocs de lui. Ses propres sorts d'évocateur (crocs, vex) restent.
- **Fin actuelle** : le battre la première fois donne la Médaille de Varenne, 20 000 éclats (× `gains`), un titre et une annonce à toute l'Abysse ; `unlocked` passe à 6 (« pas encore creusée »). Les fois suivantes : butin normal (éclats + Cœur du Néant).
- Quêtes : La Pioche de cristal, Le Néant (descendre, 3 sérums), L'endérite (400 à la main), Le Condenseur du Vide (l'acheter), L'énergie du vide (200 charges), Le métal du fond (500 lingots d'endérite pur), Le Veilleur du Néant, puis « Plus bas » (attente). 5 pages de carnet (jours 80 à la fin). Un joueur qui attendait sur « Plus bas » passe directement à « L'outil de précision ».

**Divers** : bourse paginée (45 produits par page, flèches en 46/52 : 16 produits en plus) ; carnets d'Inès sur 6 lignes ; boutique de Margaux sur 5 lignes ; guide : 29 cases de machines, 18 recettes par fiche ; foreuses : `tier` 3 (×2 vitesse, usure ÷2,5) et 4 (×2,5, usure ÷3).

Générateur testé hors serveur (harnais du scratchpad, faux ChunkData par Proxy, 144 chunks) : strate 4 ≈ 5,8 ms/chunk, 15,9 % d'air + 3,3 % d'eau, 2,9 % de minerai (cobalt 0,52 %, cristal 0,58 %) ; strate 5 ≈ 5,7 ms/chunk, 20,0 % d'air, 2,8 % de minerai (endérite 0,50 %, néant 0,17 %). Galerie camp → antre complète (261 points, la case et celle du dessus) : 0 case bouchée et 0 dans l'eau pour les strates 4 et 5 (2 cases bouchées en strate 2, déjà là avant).

## 11 decies. Correctifs 2.4.1 (10/11/2026, tickets #57 et #58)

- #57 « vie des mobs dans la strate 4 » : aucune exception dans les logs (pas de dépassement du plafond 1 024 : le plus gros monstre ordinaire a 180 PV, les gardiens passent déjà par `Fight.toughness`). Cause trouvée dans les logs du 07/11 et du 09/11 : les monstres de la strate 4 se tuaient entre eux (« Tourbillon d'éclats was slain by Noyé des cristaux », « Archer de cristal was slain by Noyé des cristaux » ; aucun Tourbillon tué par un joueur) : flèches des archers et rafales des tourbillons touchaient les noyés, qui ripostaient (16 dégâts). Correctif : `EntityDamageByEntityEvent` entre deux monstres de l'Abysse annulé (projectiles compris), `EntityTargetLivingEntityEvent` d'un monstre de l'Abysse vers un autre annulé, renforts vanilla des zombies/noyés (`SpawnReason.REINFORCEMENTS`, monstres sans marque ni butin) refusés dans les mondes de l'Abysse. Vie du monstre frappé affichée dans la barre d'action du joueur (nom, PV/PV max, barre ; pas pour les gardiens, qui ont leur barre de boss). PV des monstres inchangés (strate 4 : 120 / 90 / 100, armure 12 ; strate 5 : 180 / 70 / 140, armure 16).
- #58 : Table des foreuses et menu « Foreuses reliées » des quais : nom de l'icône = modèle en couleur (« Foreuse renforcée n°3 »), 1re ligne « Modèle : Foreuse renforcée (niveau 2/4) », une icône par modèle (magnétite = normale, bloc de fer = renforcée, bloc de lapis = à induction, obsidienne pleureuse = du Vide ; bloc de redstone si usée à 0 %, « (presque usée) » sous 25 %). Texte au-dessus de la foreuse : « (niveau N/4) » après le nom du modèle.

## 11 undecies. Passe UX 2.5.0 (10/11/2026)

- **Menu de l'étoile** (5 lignes, aéré) : ligne 2 « se déplacer » (Bordgouffre 10, Ma Concession 12, Changer de strate 14, Rejoindre un joueur 16) ; ligne 3 « progresser » (Journal 19, Métiers 21, Carnets 23, Guide 25) ; ligne 4 « commerce et Compagnie » (Bourse 28, Éclats 30, Compagnie 32, Brocante 34) ; Fermer 40, Quitter l'Abysse 44. La case décorative « Monte-charge » du hub et le bouton « Remonter » (fusée) sont retirés (Bordgouffre téléporte déjà depuis une strate ; `/abysse remonter` reste).
- **Éclats** : lore = éclats, ventes automatiques des quais (`Company.autoSold`), objets produits (`Company.produced`) ; clic = bourse.
- **Compagnie** (`companyMenu`, 4 lignes) : infos, têtes des membres (Chef / Membre, où ils sont ; Maj + clic du chef = exclure, avec confirmation), invitation reçue (clic = accepter), « Inviter un joueur » (sous-menu des têtes des joueurs présents dans l'Abysse hors Compagnie), « Quitter la Compagnie » avec confirmation. Tout passe par les commandes existantes (`/abysse compagnie inviter|accepter|quitter|expulser`).
- **Gaspard** : une seule formulation, `EemiAbysse.GASPARD_WHERE` = « le vieux mineur, au sud de la place (juste devant toi en arrivant) » (Gaspard en 0,5 65 22,5 ; arrivée 0,5 65 30,5 regard au nord). Tant que la quête vaut 0 et que le joueur est à Bordgouffre : colonne de particules (end rod + pointe de flèche) au-dessus de Gaspard envoyée au seul joueur, et « Gaspard ↗ N blocs » dans la barre d'action (même calcul que `guardianHint`).
- **Bourse** : filtre « Mon sac » (défaut, seulement ce que le joueur porte) / « Tout » (case 46, mémorisé jusqu'au redémarrage) ; « Tout vendre » et clic droit (tout vendre d'un produit) passent par un écran de confirmation avec le total prévu (`Bourse.quote`, même calcul que `sell` sans rien modifier) ; cours en vert au prix de base, rouge en dessous ; lore raccourci. Dernière ligne : 45 Tout vendre, 46 filtre, 47/50 commandes, 48 Retour, 49 Fermer, 51 éclats, 52/53 pages.
- **Bilan d'absence** : au départ (déconnexion dans l'Abysse ou sortie de l'Abysse), `PlayerData.away*` = heure + compteurs de la Compagnie ; à l'arrivée dans l'Abysse après au moins `bilan.minutes-min` (30) min, si la Compagnie a des machines : « Pendant ton absence (durée) : +N objets produits, +N ⬥ au quai d'expédition », puis la durée de mise en veille de l'usine pendant l'absence (`Company.sleepStart/sleepEnd`, notés par `Machines.tick`). Compteurs à zéro pour les Compagnies existantes : le premier bilan n'arrive qu'après un premier départ avec la 2.5.0.
- **Boutons de bas de menu** (`Menu.nav`) : dernière ligne, Retour (flèche, `item_model eemicraft:lobby/retour`) juste à gauche du centre, Fermer (barrière, `eemicraft:lobby/fermer`) au centre. Menus de machines et Table des foreuses : Retour = menu de l'étoile. Confirmations (`Menu.confirm`) : Annuler `eemicraft:lobby/annuler`, Confirmer `eemicraft:lobby/confirmer`. Menu des niveaux d'un métier : niveaux 49-50 décalés en 50-51, XP en 53. Menu d'une machine : Liaison passée de 31 à 33.
- **Modèles** : `Items.modeled(id)` = tous les objets (147 ids, liste `EemiAbysse/ids_modeles.txt`), y compris l'étoile `menu` et l'item_display des machines ; icônes des machines dans le menu d'une machine, chez Margaux et dans le Guide aussi habillées. Les objets déjà dans les sacs ne changent qu'une fois refaits.

## 11 duodecies. Monstres des strates 4 et 5 adoucis (2.5.1, ticket #57 « vie des mobs strate 4 trop abusée »)

- Strate 4 : PV -40 % (Noyé 120 → 72, Archer de cristal 90 → 54, Tourbillon 100 → 60), armure 12 → 8 (comme la strate 3), recul 0,5 → 0,4, monstres en plus autour d'un joueur +3 → +2. Strate 5 : PV -50 % (Rôdeur 180 → 90, Mite 70 → 35, Tourelle 140 → 70), armure 16 → 10, recul 0,6 → 0,5, +3 → +2. Strate 3 et gardiens inchangés. Réglable : `mobs.pv.<id>`, `mobs.armure.<n>`, `mobs.recul.<n>`, `mobs.bonus-par-joueur.<n>` (défauts dans `Mobs.hp/armor/knockback/nearBonus`). Calcul : Épée abyssale (netherite 8 + Tranchant V 3 = 11, × 1,075 au Chasseur 15 = 11,8 ; coup chargé, sans critique), armure 8 → 11,8 × (1 - max(8/5, 8 - 4×11,8/8)/25) = 10,8 par coup : Noyé 6,7 coups, Archer 5,0, Tourbillon 5,6 (avant : 8,9 par coup contre armure 12 → 13,5 / 10,1 / 11,2). Strate 5 à l'Épée de cristal (12,4 par coup brut, armure 10 → 10,5) : Rôdeur 8,6, Mite 3,3, Tourelle 6,7.
