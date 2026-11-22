# Dernier Refuge (titre de travail) – document de conception

> Mode zombie en coopération, avec une progression permanente. Conception commencée le 13/11/2026 avec Thibault. Rien n'est codé.
> Les passages marqués **[à trancher]** attendent une décision de Thibault. Tout le reste est une proposition à valider.

---

## 0. Décisions de Thibault (13/11/2026) — elles priment sur le reste du document

| Sujet | Décision |
|---|---|
| Nom | **Dernier Refuge** |
| Durée | Expédition de **25 min** max (Marée à 15 min, horde permanente à 20, brume mortelle à 25) |
| Jour / nuit | **Au choix du chef** d'escouade au départ |
| Escouades | **Vrai matchmaking** : un `/party` est mis automatiquement dans le même groupe, sinon une interface pour inviter ou rejoindre. Interfaces dans le style de la capture de Thibault (`references/style_navigator.png` : panneau gris clair à bord sombre, gros boutons avec une icône et un nom en petites capitales, onglet de titre, boutons ✕ et i, aucune vitre de remplissage) |
| Pack de textures | **Pack séparé** « Dernier Refuge », espace de noms `refuge:`, empilé sur le pack EEMICRAFT. **Chargé seulement dans les mondes du mode et retiré en sortant** (correction du 13/11/2026 : c'est ce que voulait Thibault, pour pouvoir **retexturer des blocs vanilla** sans toucher au reste du serveur ; écran de chargement de quelques secondes à l'entrée et à la sortie). `pack.mode: monde`, `pack.mondes: [refuge]` dans le config |
| Planque | **Perso**, on peut la **visiter**. Le bâtiment monte **par paliers** (cabane → maison → bunker), **déco et meubles libres** à l'intérieur |
| Échanges | Aucun partage de stuff sauf **`/trade`**. Le trade **refuse** un objet d'un acte que l'autre joueur n'a pas débloqué |
| Anti-smurf | Objets **liés à un acte** : impossibles à utiliser sans avoir fini l'acte précédent |
| Monnaie | **Ferraille** à part (pas les pièces EemiCore) |
| Mort | **Sac qui reste + assurance** : le sac reste dans la zone pour l'expédition suivante (une chance de revenir le chercher), et une assurance payée en Ferraille avant le départ garantit certains objets |
| À terre | Comme proposé : rampe 40 s, relève 4 s accroupi à 30 % des PV, mort à la 3e mise à terre, « Seconde chance » pour les solos |
| Infection | **Aucune**. Seul le saignement reste |
| Munitions | Comme proposé : 6 à 12 balles par expédition au mieux, chères, bruyantes |
| Boss | **Les deux** : salle du boss dans la zone, puis une expédition « boss » en raccourci une fois le boss battu |
| Combat | **Moderne** (recharge, balayage), pas d'OldCombatMechanics |
| Améliorations | +0 à +5, paliers par plans, gravures (1 emplacement, 2 à +5) |
| Sac | Cases verrouillées au départ, débloquées par les sacs à dos |
| Maps | **Importées**, style **mélange** : Hameau et Bourgade en style village Minecraft, Port, Hôpital et Épicentre plus modernes. Retouchées puis marquées |
| Salle d'essai | Monde admin `refuge_essai` : tous les meubles, toutes les armes, tous les zombies à faire apparaître avec un bouton (EemiRefuge 0.1.0) |

---

## 1. Le principe

Tu vis dans un **refuge**. Tu pars en **expédition** dans une zone infestée, seul ou en escouade de 2 à 4. Sur place, tu fouilles, tu te bats contre les zombies et tu remplis des contrats. Ensuite tu dois **rentrer** par un point d'extraction. Ce que tu ramènes est à toi : tu le ranges dans ta planque, tu le vends, tu t'en sers pour améliorer tes armes et ta planque. Si tu meurs, tu perds ce que tu portais.

Références d'esprit (on ne copie rien) : Into the Radius (rentrer à la base, ranger, améliorer), les modes zombies d'extraction en escouade, Escape from Tarkov (la planque qu'on améliore).

### Règles d'or

1. **Coop pur.** Pas de PvP, pas de tir ami, pas de vol entre joueurs.
2. **L'épée d'abord.** On commence avec une épée en bois. Les armes à feu viennent tard, avec très peu de munitions, et elles font du bruit. Le corps à corps reste l'arme principale jusqu'à la fin.
3. **Direction artistique Minecraft.** Blocs, objets et monstres vanilla autant que possible. Les objets du pack de textures restent en pixel art 16×16, avec la palette et le niveau de détail de Minecraft. Pas de modèles « réalistes ».
4. **Le risque fait le jeu.** Plus tu restes longtemps et plus tu vas loin, meilleur est le butin, mais plus tu risques de tout perdre.
5. **Qualité d'un vrai mode.** Aucun objet qui flotte, aucune texture violet et noir, aucun texte mal placé. Voir la section 12 : c'est une condition pour livrer, pas un bonus.

---

## 2. La boucle de jeu

```
REFUGE ──► préparer son équipement (sortir les objets de la réserve)
   ▲        choisir une zone et un contrat, former l'escouade
   │                     │
   │                     ▼
   │        EXPÉDITION (15 à 25 min, copie neuve de la zone)
   │        fouiller · se battre · contrats · bruit · la Marée monte
   │                     │
   │          ┌──────────┴──────────┐
   │          ▼                     ▼
   │     EXTRACTION              MORT
   │     tout est gardé          tout ce que tu portes reste dans ton sac,
   │          │                  au sol (l'escouade peut le récupérer)
   └──────────┴─────────────────────┘
        ranger · vendre · améliorer ses armes et sa planque · contrats
```

Durée visée : une expédition prend 15 à 25 minutes, une soirée en compte 3 ou 4.

---

## 3. Les lieux

| Lieu | Rôle | Monde |
|---|---|---|
| **Le Refuge** | Base commune : marchands, panneau des contrats, départ des expéditions, classements | `refuge`, construit par les builders |
| **Les Planques** | Base perso de chaque joueur (section 9) : réserve, établis, trophées | `refuge_planques`, une parcelle par joueur, comme les Concessions de l'Abysse |
| **Les Zones** | Lieux d'expédition, une copie neuve par expédition | `zone_<id>`, copies `_2`, `_3`… faites par `WorldClone` |

Le Refuge doit raconter quelque chose : un ancien poste fortifié (barricades, murs de sacs de sable en laine, miradors, projecteurs), le marché au centre, le portail de départ au fond. Les PNJ sont FancyNpcs ou des Mannequin, avec des skins originaux.

---

## 4. Les zones et la campagne

La campagne se joue en 5 actes. On débloque un acte en finissant le contrat principal et le boss de l'acte précédent. Chaque zone fait environ 250 × 250 blocs, avec de l'intérieur et de l'extérieur, plusieurs étages, des raccourcis et des pièces fermées.

| Acte | Zone | Ambiance | Nouveautés |
|---|---|---|---|
| 1 | **Le Hameau** | Village et fermes, champs, moulin, église | Tutoriel. Rôdeurs, Coureurs. Boss : la Grange (Brute + horde) |
| 2 | **La Bourgade** | Rues, boutiques, mairie, gendarmerie | Hurleurs, Cracheurs. Premier plan d'arme à feu (gendarmerie). Boss : le Maire |
| 3 | **Le Port** | Docks, entrepôts, conteneurs, grues, quais | Noyés, Gonflés. Zones d'eau. Boss : le Dragueur |
| 4 | **L'Hôpital** | Couloirs, blocs opératoires, laboratoire en sous-sol | Infection renforcée, coupures de courant. Boss : le Patient Zéro |
| 5 | **L'Épicentre** | Cratère de la chute, terre noire, ruines | Toutes les menaces, la Marée permanente. Boss final |

**Histoire (proposition) :** une nuit, une **pluie noire** est tombée sur la région. Le lendemain, les morts se sont relevés. Le Refuge est le dernier poste qui tient encore. En fouillant, on trouve des documents (carnets, rapports, enregistrements) qui expliquent d'où vient la pluie. On l'apprend vraiment à l'Épicentre.

**Qui construit les maps [à trancher] :** les builders (BanTeEs, MaZd_, Qorwinn…) à la main ; ou des maps importées puis retouchées ; ou générées par un script comme Sheep Wars et Hors Cours. Recommandation : **à la main par les builders**, en suivant le cahier de la section 12.3, parce que la fouille et l'ambiance demandent un vrai décor. Le plugin ne pose rien dans le décor : il se contente de lire les marqueurs posés par les builders.

---

## 5. L'expédition

### 5.1 Départ
- Le chef d'escouade choisit la zone (parmi celles débloquées par **tous** les membres), un contrat facultatif et le moment, jour ou **nuit [à trancher]**.
- Chaque joueur part avec ce qu'il a sur lui en passant le portail. La réserve reste à la planque.
- Points d'arrivée tirés au sort parmi 3 ou 4 entrées de la zone.
- Les groupes viennent du `/party` d'EemiCore. Option : une file publique pour jouer avec d'autres **[à trancher]**.

### 5.2 Le temps et la Marée
- Expédition de 25 min au maximum. Le temps est dans la barre de boss.
- **La Marée** : à partir de 15 min, des hordes apparaissent de plus en plus souvent. À 20 min, horde permanente. À 25 min, la brume noire arrive et elle tue. Ça pousse à rentrer sans fixer de minuteur trop strict.
- La nuit, il y a plus de zombies et ils sont plus rapides, mais le butin est meilleur (+1 cran de rareté).

### 5.3 Le bruit (mécanique centrale)
Chaque action fait du bruit, et les zombies dans le rayon viennent voir.

| Action | Rayon |
|---|---|
| Marcher accroupi | 0 |
| Marcher | 4 |
| Courir, sauter | 10 |
| Coup d'épée, porte qui s'ouvre | 6 |
| Casser une vitre, une porte barricadée | 16 |
| Arc, arbalète | 5 |
| **Arme à feu** | **40 à 60** |
| Explosion | 64 |
| Fusée d'appât (lancée) | attire tout à 48 blocs vers elle pendant 15 s |

Un zombie qui ne t'a pas vu prend ×3 dégâts de dague dans le dos (attaque furtive). Le bruit est la vraie raison pour laquelle les armes à feu restent un dernier recours.

### 5.4 L'extraction
- 2 ou 3 points d'extraction fixes par zone (wagonnet de mine, barque au quai, monte-charge, camion), affichés sur la carte de la zone.
- On allume la **fusée de signal** au point d'extraction, puis il faut **tenir 45 à 90 s** contre une vague. Le moyen de transport arrive, et ceux qui sont dans le cercle partent.
- Chacun peut partir par le point qu'il veut. Un joueur parti attend ses coéquipiers au Refuge et les regarde en spectateur.
- Extraction d'urgence : un objet rare (« Fusée rouge ») qui permet de partir de n'importe où, après 30 s de défense.

### 5.5 À terre, mort et sac
- **En escouade :** à 0 PV, on tombe **à terre** (on rampe, 40 s). Un coéquipier accroupi à côté pendant 4 s relève le joueur avec 30 % de ses PV. Après deux mises à terre dans la même expédition, la suivante tue.
- **Seul :** pas de mise à terre, sauf avec l'objet « Seconde chance ».
- **Mort :** tout ce que porte le joueur va dans **son sac**, un vrai bloc posé au sol (voir 12.2), que l'escouade peut fouiller et ramener. Le mort passe en spectateur sur un coéquipier.
- **Toujours gardés :** la **poche sûre** (2 cases, 4 avec une amélioration de planque) et les objets liés (clé de planque, carte).
- Si toute l'escouade meurt, tout ce qui n'est pas dans une poche sûre est perdu.

### 5.6 Fin d'expédition
Écran de bilan (menu du pack) : butin ramené avec sa valeur, zombies tués, XP gagnée, contrats remplis, coéquipiers relevés. Récompenses EemiCore (`eemi reward … play|win|kill`, l'extraction comptant comme une victoire).

---

## 6. La fouille (le cœur du mode)

Le butin est **posé dans le décor avec les mécaniques de Minecraft**, à des endroits qui ont du sens. On ne trouve pas un fusil dans un frigo.

### 6.1 Où on trouve les objets

| Support (vanilla) | Ce qu'on y trouve | Fouille |
|---|---|---|
| **Étagères** (`*_SHELF`, 3 objets visibles posés dessus) | Conserves, outils, munitions, médicaments en boutique ou en réserve | On prend l'objet qu'on voit. Le plugin le met dans le sac (ou l'échange contre l'objet en main, comme en vanilla) |
| **Coffres, coffres en cuivre, tonneaux** | Butin mélangé selon la pièce | Ouverture vanilla. Fouille de 1 à 2 s (barre de progression) la première fois |
| **Pots décorés** | 1 objet caché, parfois rien | Le casser (bruit 6) ou mettre l'objet dans le sac |
| **Bibliothèques ciselées** | Documents de l'histoire, plans d'armes | On prend le livre |
| **Cadres** (normaux et lumineux) | Armes accrochées aux murs (armurerie, chalet de chasse) | On prend l'arme |
| **Porte-armures** | Pièces d'armure sur un mannequin (gendarmerie, caserne) | On prend la pièce |
| **Feux de camp** | Viande qui cuit (campement de survivants) | On la prend |
| **Pupitres** | Journaux, carnets | On lit, puis on prend |
| **Sable et gravier suspects** | Décombres, cratères, éboulis | On fouille au **pinceau** (vanilla) |
| **Cadavres** (Mannequin couché, skin de civil) | Le sac d'un survivant mort | Clic droit = fouiller **[forme à valider à l'essai]** |
| **Coffres-forts** (coffre fort, cadenas) | Gros butin | Il faut une **clé** trouvée dans la zone ou le temps de forcer (bruit) |
| **Portes barricadées** (planches en bois) | Pièces fermées | Les casser à la hache (bruit 16) |

Tout ça est rendu par **Minecraft lui-même** : un objet posé sur une étagère est dessiné à la bonne place, à la bonne taille, et il ne flotte jamais. C'est voulu (voir 12.1).

### 6.2 Tables de butin
- Chaque support a un **type de pièce** (cuisine, garage, armurerie, pharmacie, bureau, chambre, atelier…) qui choisit sa table.
- La zone fixe le **niveau** (acte 1 à 5), la nuit ajoute un cran.
- Raretés aux couleurs de Minecraft : §f commun, §a peu commun, §9 rare, §5 épique, §6 légendaire.
- Placement : les builders posent les supports dans le décor et les marquent (outil `/refuge marqueur <pièce>` : le type est enregistré dans le PDC du chunk). Au début de chaque expédition, le plugin remplit ou vide chaque support au hasard selon sa table. Une étagère n'est jamais pleine partout : environ 40 % de cases occupées, pour que ça fasse « déjà pillé ».

### 6.3 Les objets

| Famille | Exemples | Utilité |
|---|---|---|
| Nourriture | Conserves, pain, pommes, viande | Faim vanilla, petits soins |
| Soins | Bandage, trousse de secours, antidote, potion | Saignement, PV, infection |
| Matériaux | Ferraille, planches, cuir, fils, poudre à canon, pépites | Améliorations, munitions, planque |
| Pièces d'arme | Lame, garde, ressort, canon, mécanisme | Améliorer et monter de palier |
| Plans | « Plan : épée en fer », « Plan : pistolet bricolé » | Débloquent une fabrication pour toujours |
| Objets de valeur | Montre en or, bague, jumelles, radio | Seulement pour la vente |
| Clés | Clé de la mairie, clé du coffre-fort | Ouvrent un endroit précis de la zone |
| Documents | Carnets, rapports | Histoire, débloquent des contrats cachés |

Les objets reprennent un objet vanilla quand il existe (pain, pomme, lingot, poudre à canon, montre, boussole, longue-vue, pinceau…). Un objet du pack (`item_model eemicraft:refuge/<id>`) est seulement prévu quand rien de vanilla ne colle (bandage, conserve, pièces d'arme, armes à feu, munitions).

### 6.4 Le sac
- L'inventaire vanilla, mais **sac à dos améliorable** : au départ, seules la barre d'action et la première ligne sont ouvertes. Les cases fermées sont verrouillées (case grise du pack, impossible à déplacer).
- Sacs : sac de toile (+1 ligne), sac de randonnée (+2), sac militaire (+3). Trouvés ou fabriqués.

---

## 7. Le combat

Combat **moderne** de Minecraft (avec la recharge de l'épée) : bien viser et choisir son moment compte davantage contre des zombies. Les mondes du mode ne sont **pas** dans le modeset `old` d'OldCombatMechanics.

### 7.1 Corps à corps (arme principale)

Paliers d'épée = matériaux de Minecraft. Dégâts de base de la version moderne :

| Épée | Dégâts | Obtention |
|---|---|---|
| Bois | 4 | Départ |
| Pierre | 5 | Plan acte 1 + matériaux |
| Fer | 6 | Plan acte 1-2 |
| Diamant | 7 | Plan acte 3 |
| Netherite | 8 | Plan acte 4 |

Les autres familles se débloquent avec des plans :

| Famille | Jeu | Particularité |
|---|---|---|
| **Épée** | Équilibrée | Balayage : touche plusieurs zombies collés |
| **Dague** | Très rapide, faible | ×3 dans le dos d'un zombie qui ne t'a pas vu (furtif) |
| **Hache** | Lente, très forte | Casse les barricades, étourdit les Brutes |
| **Lance** (vanilla 26.x) | Portée longue | Charge, garde les zombies à distance |
| **Masse** (vanilla) | Spéciale | Coup en tombant d'un étage, onde qui renverse (fin de partie) |

**Améliorations :** chaque arme est un objet unique avec un **niveau +0 à +5**, monté à l'établi de l'armurier avec des pièces et des matériaux (+0,5 dégât par niveau, solidité aussi). Le palier suivant (pierre → fer…) se fabrique en donnant l'arme +5, son plan et des matériaux : on ne repart pas de zéro.

**Gravures** (1 emplacement, 2 au niveau +5) : Saignement, Feu, Recul, Vol de vie, Fendoir (balayage plus large), Silence (coups sans bruit). Trouvées sous forme de « Pierres de gravure » rares.

### 7.2 Distance sans poudre
- **Arc** : silencieux, flèches récupérables (50 %).
- **Arbalète** : lente, forte, perce un zombie.
- **Objets à lancer** : couteaux de lancer, bouteille incendiaire, fusée d'appât, TNT à mèche (bruit 64).

### 7.3 Armes à feu (tard, rares, bruyantes)

Ce sont des armes **bricolées** façon Minecraft (bois, fer, cuivre, redstone), pas des armes modernes réalistes.

| Arme | Débloquée | Dégâts | Chargeur | Particularité |
|---|---|---|---|---|
| Pistolet bricolé | Acte 2 (plan de la gendarmerie) | 10 | 6 | Recharge 2 s |
| Tromblon | Acte 3 | 5 plombs × 3 | 2 | Courte portée, recule le joueur |
| Carabine à levier | Acte 4 | 16 | 5 | Tête ×2, lente |
| Arme légendaire | Acte 5 | à définir | | Récompense de fin |

- **Munitions rares :** 6 à 12 balles trouvées par expédition au mieux, fabrication chère (poudre à canon + pépites + ferraille) à l'atelier de munitions de la planque.
- **Bruit 40 à 60 blocs** à chaque tir : un tir peut amener une horde.
- Technique : tir par lancer de rayon (pas de projectile qui traîne), traînée de particules, coup à la tête si le rayon touche le haut de la hitbox, sons vanilla mélangés (feu d'artifice, arbalète, explosion) ou sons du pack. Recharge visible sur l'objet (barre de recharge vanilla).

### 7.4 Armures
Cuir → maille → fer → diamant (vanilla), avec les mêmes niveaux +0 à +5 que les armes. Bonus spéciaux : casque (réduit le risque de morsure), plastron rembourré (réduit le saignement).

### 7.5 États
- **Saignement** (Coureurs, griffes) : perte de PV dans le temps, arrêté par un bandage.
- ~~Infection~~ : abandonnée (décision du 13/11/2026).
- **Faim** vanilla seulement, sans soif, pour ne pas alourdir.

---

## 8. Les zombies

Tous sont des **monstres vanilla** (zombie, husk, noyé, villageois zombie, zombifié…) avec des attributs, de l'équipement et une IA ajustés : la DA reste Minecraft.

| Zombie | Base vanilla | Comportement |
|---|---|---|
| **Rôdeur** | Zombie | Lent, en groupes, suit le bruit |
| **Coureur** | Zombie (bébé non) plus rapide | Sprinte, fait saigner, peu de PV |
| **Brute** | Zombie géant-ish (taille ×1,4) | Beaucoup de PV, renverse, enfonce les barricades |
| **Hurleur** | Villageois zombie | Crie quand il te voit et attire tout à 30 blocs : à tuer en premier |
| **Cracheur** | Husk | Crache de l'acide à distance (zone de poison) |
| **Gonflé** | Zombie vert | Explose au contact (dégâts + bruit) |
| **Noyé** | Noyé | Dans l'eau du Port, tire parfois au trident |
| **Rampant** | Zombie couché **[à valider]** | Caché sous les tables, attrape les jambes (ralentit) |
| **Blindé** | Zombie en armure | Résiste à l'épée, faible à la hache et aux balles |

- **Élites** (nom doré) : plus de PV, une capacité, un butin garanti.
- **Boss** d'acte : salle dédiée, phases, attaques lisibles (la Brute qui charge en ligne droite, etc.).
- **Nombre** : multiplié par (1 + 0,5 par joueur en plus), et par la Marée et la nuit.
- **IA** : poursuite par le Pathfinder de Paper, ouïe (le bruit), vue (cône, pas à travers les murs), ils ouvrent les portes en bois et cassent les barricades à la longue.

---

## 9. La planque (base perso)

Une parcelle par joueur dans `refuge_planques`. Au début, c'est une cabane vide avec un coffre de réserve. On l'agrandit **avec ce qu'on ramène**, comme une planque Tarkov mais en blocs Minecraft.

| Station | Ce qu'elle débloque | Coût (exemple niveau 1) |
|---|---|---|
| **Réserve** | Stockage (27 → 54 → 108 cases) | — / ferraille + planches |
| **Établi de l'armurier** | Améliorations +1 à +5, gravures | 20 ferraille, 10 planches |
| **Forge** | Palier suivant des armes et armures | 30 ferraille, 5 lingots, plan |
| **Atelier de munitions** | Balles, plombs, flèches | 15 ferraille, 10 poudre à canon |
| **Infirmerie** | Bandages, antidotes, trousses | 10 tissu, 5 fioles |
| **Potager** | Nourriture produite seule | Graines, terre, eau |
| **Établi de sacs** | Sacs à dos, poche sûre agrandie | Cuir, fils |
| **Radio** | Contrats en plus chaque jour | Radio, fils, piles |
| **Stand de tir** | Tester les armes sans munitions perdues | Planches, cibles |
| **Mur des trophées** | Têtes des boss, armes légendaires exposées | Récompenses de boss |

- Chaque station est un **bloc vanilla** qui a du sens (table de forgeron, enclume, table d'archerie, alambic, composteur, établi…) entouré d'un petit décor que le plugin pose quand on la construit (schéma fixe, vérifié à l'essai).
- La réserve et les étagères de la planque **montrent** les objets rangés (vraies étagères, cadres, porte-armures) : on voit ses armes au mur.
- **[à trancher]** Planque perso seulement, ou aussi une planque d'escouade partagée.

---

## 10. Économie et progression

- **Monnaie :** la **Ferraille** (⚙), propre au mode, comme les éclats de l'Abysse. **[à trancher]** Lien ou non avec les pièces d'EemiCore.
- **Marchands du Refuge** (prix fixes, plus lisibles qu'une bourse) :
  - **L'Armurier** : armes de base, pièces, réparations.
  - **La Médecin** : soins, antidotes.
  - **Le Quincaillier** : matériaux, sacs, outils (hache, pinceau, pied-de-biche).
  - **Le Receleur** : rachète les objets de valeur, vend parfois une rareté (stock qui change chaque jour).
- **Réputation** par marchand : monte avec ses contrats et débloque des articles.
- **Contrats** (panneau du Refuge + radio) : récupérer des objets précis, tuer une élite, ouvrir le coffre-fort de la mairie, rallumer un relais radio, survivre à la nuit. 3 contrats du jour + la chaîne principale de la campagne.
- **Niveau du survivant** (XP des expéditions) : seulement un repère et des titres. Pas de bonus de stats : la puissance vient de l'équipement et de la planque.

---

## 11. Commandes (proposition)

- `/refuge` : menu principal (aller au Refuge, à sa planque, contrats, escouade).
- `/refuge planque`, `/refuge contrats`, `/refuge stats [joueur]`.
- Admin (`eemirefuge.admin`) : `reload`, `give <j> <objet> [n]`, `ferraille <j> <n>`, `acte <j> <n>`, `zone start|stop [n]`, `marqueur <pièce>`, `essai` (aller dans la salle d'essai), `reset <j> [confirmer]`.

---

## 12. Qualité : un vrai mode, sans bugs visuels

La leçon de Hors Cours (texture violet et noir de Loïc, objets qui flottaient sur des étagères en `ItemDisplay`) devient une règle.

### 12.1 Règle des objets posés
- **Tout objet posé dans le monde est rendu par Minecraft** : étagère, cadre, pot décoré, bibliothèque ciselée, porte-armure, feu de camp, pupitre, coffre. Ces blocs placent eux-mêmes l'objet à la bonne hauteur, face au bon côté, et il ne flotte jamais.
- Les entités d'affichage (`ItemDisplay`, `BlockDisplay`, `TextDisplay`) sont autorisées (Thibault, 13/11/2026 : « la petite tête d'affichage ne me dérange pas, ce n'était pas le gros bug »), à condition que chacune ait passé la salle d'essai (12.4). Le gros bug de Hors Cours, c'était la texture cassée : c'est ce que la vérification du pack (12.2) doit rendre impossible.
- Les objets du pack posés sur une étagère ou dans un cadre utilisent `item_model` : Minecraft les rend comme n'importe quel objet, à condition que le modèle ait les bons `display` (12.2).

### 12.2 Règle du pack de textures
- Une **vérification automatique** dans le script du pack, qui bloque la construction si un problème est trouvé :
  - chaque modèle référencé par un objet existe ;
  - chaque texture référencée existe, au bon endroit (`textures/item/` ou `textures/block/`, jamais `textures/entity/`), en PNG 16×16 (ou multiple) ;
  - chaque modèle d'objet a ses positions `display` (main droite et gauche à la 1re et 3e personne, tête, cadre (`fixed`), au sol (`ground`), menu (`gui`)), copiées d'un modèle vanilla proche (épée → `item/handheld`, objet plat → `item/generated`, arme à feu → modèle `handheld` réglé à l'essai) ;
  - aucun fichier en double ou orphelin ;
  - le pack part **toujours** du dernier pack publié (les ajouts du lobby, des karts, du paintball et de l'Abysse sont gardés).
- Planche d'aperçus générée à chaque construction (`EemiRefuge/apercus/planche.png`) pour relire toutes les icônes d'un coup.

### 12.3 Cahier des builders
- Une étagère, un cadre ou un pot de butin = un support marqué (`/refuge marqueur <pièce>`). Le plugin ne crée aucun support : s'il n'y a pas de marqueur, rien n'apparaît.
- Aucune entité posée à la main dans les zones, en dehors des porte-armures et des cadres de butin marqués. Les cadavres sont posés par le plugin à des points marqués.
- Points à marquer : arrivées, extractions, salle du boss, apparitions de hordes, clés, coffres-forts.
- Une zone n'est « jouable » que si `/refuge zone verifier <id>` ne signale rien : supports sans table, point d'extraction inaccessible, marqueur hors de la zone.

### 12.4 Salle d'essai et validation
- Monde `refuge_essai` : une salle où **chaque** objet du mode est posé sur chaque support (étagère, cadre, pot, porte-armure, au sol, en main), chaque zombie est figé et chaque station de planque est construite.
- Avant chaque livraison, Thibault (ou un testeur) fait le tour et envoie des captures. Rien n'est noté « fini » dans CLAUDE.md sans ce passage.
- Une liste `EemiRefuge/A_VERIFIER.md` tenue à jour, case par case.

### 12.5 Finitions d'un vrai mode
- Menus avec les fonds du pack (même DA que les menus du lobby), Retour et Fermer à la même place partout, sons `eemicraft:ui.*`.
- Barre de boss (temps, Marée), barre d'action (bruit actuel, infection), tableau latéral (escouade, PV des coéquipiers).
- Messages et titres courts, en français avec accents, préfixe `§8[§aDernier Refuge§8] §7`.
- Tutoriel joué dans l'acte 1 (pas de mur de texte) : la Médecin explique la fouille en montrant une étagère, etc.

---

## 13. Technique

- Plugin **EemiRefuge** (`EemiRefuge/`, Paper 26.3, Java 25), sur le modèle des plugins maison : mondes créés par le plugin, copies des zones par `_commun/WorldClone.java`, `max-instances` (3).
- Étagères : `org.bukkit.block.Shelf` + `ShelfInventory` (présents dans paper-api 26.3, vérifié au contenu du jar le 13/11/2026 : `OAK_SHELF` … `PALE_OAK_SHELF`). Interaction exacte (prendre, échanger) à vérifier à l'essai avant d'écrire la fouille.
- Données : `plugins/EemiRefuge/joueurs/<uuid>.yml` (réserve, planque, plans, réputation, niveau, actes), contenu de la réserve en `ItemStack.serializeItemsAsBytes`.
- Inventaires : groupe Multiverse-Inventories `refuge` (refuge, planques). Les zones sont **hors groupe** : on entre avec ce qu'on porte, on ressort avec ce qu'on ramène, et le plugin gère la perte à la mort. Respecter `_commun/BRIEF_INVENTAIRE.md` (jamais vider l'inventaire dans le monde d'arrivée).
- Branchements : EemiCore (`games.refuge`, statut, récompenses, maintenance), boussole (sous-menu « Survie » ou « Îles et aventure »), DeluxeHub (mondes désactivés), TAB (disable-condition), AuraSkills (`disabled_worlds`), EemiAdmin (`refuge` bloqué en vanish).
- Combat moderne : mondes absents du modeset `old` d'OCM et d'EemiBlocage.
- Performances : un monde de zone chargé par expédition, monstres plafonnés par joueur, IA désactivée loin des joueurs.

---

## 14. Plan de réalisation

| Étape | Contenu | But |
|---|---|---|
| **0. Essais** | Salle d'essai, étagères et cadres remplis par le plugin, 3 objets du pack validés en jeu | Prouver que rien ne flotte et que tout s'affiche |
| **1. Socle jouable** | Refuge minimal, réserve, Le Hameau (version builder), Rôdeur/Coureur/Brute, épée bois → fer, fouille, bruit, extraction, sac à la mort, mise à terre | Première vraie expédition en escouade |
| **2. Progression** | Planque et stations, améliorations +1 à +5, plans, marchands, contrats, sacs à dos | La boucle « rentrer, ranger, améliorer » |
| **3. Poudre** | Pistolet bricolé, munitions, Hurleur, Cracheur, La Bourgade | Les armes à feu à leur juste place |
| **4. Campagne** | Le Port, l'Hôpital, l'Épicentre, boss, histoire, infection complète | Le contenu long |
| **5. Finitions** | Sons, menus, classements, saisons, événements | Le niveau d'un vrai mode |

---

## 15. Questions ouvertes

Les 9 premières questions sont tranchées (section 0). Restent :
1. Contenu exact de l'assurance (quels objets, quel prix, combien de temps le sac reste dans la zone).
2. Liste des maps importées à retenir pour chaque acte (recherche à faire).
3. Interface de matchmaking : maquette à valider d'après `references/style_navigator.png`.
