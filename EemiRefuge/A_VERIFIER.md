# Dernier Refuge 0.1.0 – à vérifier en jeu (salle d'essai)

Rien n'a été lancé sur un serveur : tout ce qui suit est à regarder. Cocher, et envoyer une capture pour chaque ligne « rendu ».

## Installation
- [ ] `EemiRefuge-0.1.0.jar` dans /plugins, redémarrage. Logs attendus : « Monde refuge_essai cree (vide) », « Construction de la salle d'essai (version 1...) », « Salle d'essai construite en N ms », « Salle d'essai prete : 53 objets au catalogue, 9 types de zombies ». Aucun « Bloc ... impossible », « Etat de bloc ... », « Cadre impossible », « Cible impossible », « Pas d'inventaire en ... ».
- [ ] `RefugePack.zip` publié sur https://eemicraft.strator.gg/pack/RefugePack.zip, SHA-1 identique à celui du config (`pack.sha1`), puis `pack.enabled: true` + `/refuge reload`. À la connexion : le pack EEMICRAFT ET le pack Refuge sont chargés (Options > Packs de ressources : deux packs serveur). Changer de monde ne retire pas le pack Refuge.
- [ ] `refuge_essai` n'est PAS dans le modeset `old` d'OldCombatMechanics ni dans les `worlds` d'EemiBlocage (combat moderne, recharge de l'épée visible).
- [ ] Un joueur non op qui arrive dans `refuge_essai` (tp) est refusé / renvoyé au spawn du lobby avec le message.
- [ ] `/refuge essai` : téléporté au hub de la salle (0.5 65 0.5), passé en survie ; `/refuge essai quitter` : retour à l'endroit et au mode de jeu d'avant.
- [ ] Inventaire : le monde n'est dans aucun groupe Multiverse-Inventories ; vérifier qu'on ne ramène rien de la salle au lobby.

## Hub
- [ ] Sol, 4 lampadaires, titre « Salle d'essai », 4 panneaux de direction lisibles (Armurerie, Arène, Stand de tir, Meubles) : rien ne flotte de travers.
- [ ] On ne peut pas tomber dans le vide (muret + barrières au bord).

## Armurerie (nord)
- [ ] Rangée du haut (11 cadres) : épées bois→netherite, dague, hache, lance, masse, arc, arbalète. Chaque objet bien centré dans le cadre, nom affiché en le regardant.
- [ ] Rangée du milieu (11 cadres) : couteau de lancer, bouteille incendiaire, fusée d'appât, TNT à mèche, pistolet, tromblon, carabine, 3 munitions, flèches.
- [ ] Rangée du bas : épée en fer +0, +5, dague +5, pistolet +5, 6 épées gravées. Le nom porte « +5 » en jaune, la description affiche niveau et gravures.
- [ ] Clic droit sur un cadre = l'objet arrive dans le sac (le cadre ne tourne pas).
- [ ] 6 étagères en sapin (mur ouest) : 3 objets visibles par étagère, posés droits (rendu `on_shelf`). Clic droit = les 3 objets dans le sac, l'étagère ne change pas.
- [ ] 4 porte-armures (cuir, mailles, fer, diamant) : clic droit = l'armure complète, le porte-armure reste habillé.
- [ ] Bouton « Menu des armes » : menu 2 pages, flèches, Fermer ; un clic = l'objet. `/refuge essai armes` ouvre le même menu.
- [ ] Levier « Munitions infinies » : message activé/désactivé ; actif = balles, flèches, lancers, gravures non consommés (barre d'action « Munitions infinies »).
- [ ] Pierre d'amélioration : clic avec la pierre sur une arme dans l'inventaire = +1 (après +5 retour à +0), le nom et la description changent. Pareil sur une armure.
- [ ] Pierre de gravure : clic sur une arme de corps à corps = gravure ajoutée (1 place, 2 à +5 ; si plein, la plus ancienne est remplacée), la pierre est consommée.

## Chaque objet du pack (galerie, salle des meubles, mur sud) – rendu
Pour CHACUN des 24 objets (dague, couteau de lancer, bouteille incendiaire, fusée d'appât, pistolet, tromblon, carabine, balles de pistolet, cartouches de plombs, balles de carabine, 6 pierres de gravure, pierre d'amélioration, bandage, conserve, ferraille, lame, ressort, canon, mécanisme) :
- [ ] sur l'étagère en chêne (3 exemplaires) : posé, pas violet et noir, pas de travers ;
- [ ] dans le pot décoré au-dessus (le pot ne montre pas son contenu en vanilla : vérifier seulement qu'il bouge quand on clique) ;
- [ ] dans le cadre ;
- [ ] dans le cadre lumineux ;
- [ ] au sol (objet qui tourne, ne disparaît pas, ne se ramasse pas) ;
- [ ] en main du mannequin (3e personne vue de face) ;
- [ ] dans l'inventaire et la barre d'action (menu) ;
- [ ] en main, 1re personne (main droite ET main gauche) ;
- [ ] en main, 3e personne (F5) ;
- [ ] sur la tête (/item replace entity @s armor.head with ... : position `head`).
Armes à feu en particulier : le canon doit pointer DEVANT en 3e personne et à peu près droit en 1re personne (valeurs de départ `display` dans `pack/gen_refuge.py`, section GUN, à régler).
- [ ] Planche d'aperçus `EemiRefuge/apercus/planche.png` : relire chaque icône.

## Arène des zombies (est)
- [ ] Portillons à l'entrée : un zombie ne sort pas de l'arène.
- [ ] Pupitre : 9 boutons « normal » (devant) et 9 « élite » (derrière), étiquettes lisibles au-dessus.
Pour chaque zombie, normal puis élite (nom blanc / nom doré « élite ») :
- [ ] Rôdeur : lent, 20 PV.
- [ ] Coureur : sprinte, 12 PV, un coup = saignement (barre d'action « Saignement », particules rouges, 1 dégât/s), le bandage l'arrête.
- [ ] Brute : 1,4 fois plus grande, 60 PV, gros recul ; la hache l'étourdit (message « Étourdie ! », ralentie 1,5 s).
- [ ] Hurleur (villageois zombie) : en te voyant, cri (rugissement) + message, tous les zombies à 30 blocs te prennent pour cible.
- [ ] Cracheur (husk) : crache à 3-16 blocs, nuage vert de poison au point d'impact.
- [ ] Gonflé (armure verte) : à moins de 2 blocs, il brille, siffle et explose 1,25 s après (dégâts, pas de bloc cassé, bruit 64).
- [ ] Noyé : apparaît dans le bassin, lance son trident.
- [ ] Rampant : pose « couché » ([à valider] : rendu de la pose nage sur un zombie, hitbox), ralentit au contact.
- [ ] Blindé (armure de fer) : épée ×0,5, hache ×1,5, balles ×1,5.
- [ ] Bouton « Horde de 10 » ; « Défilé figé » (un de chaque, normal + élite, en ligne, figés) ; « Figer / défiger » ; « Tout tuer ».
- [ ] Aucun zombie ne brûle au soleil, ne se transforme (noyade), ne lâche rien en mourant.
- [ ] Chaque coup sur un zombie : barre d'action « -X | Nom ❤ PV/max » (MORT quand il meurt).

## Bruit
- [ ] Barre d'action : « Bruit ▌▌▌ N blocs » : accroupi 0, marche 4, course/saut 10, coup d'épée 6, arc 5, porte 6, vitre 16, barricade 16, pistolet 40, tromblon 50, carabine 60, explosion 64.
- [ ] Un zombie loin (> 20 blocs) ne vient pas si on marche accroupi ; il vient voir l'endroit d'un tir.
- [ ] Gravure Silence : coups d'épée sans bruit.
- [ ] Fusée d'appât lancée : fumée/étincelles rouges 15 s, tous les zombies à 48 blocs y vont et ignorent les joueurs pendant 15 s.

## Armes
- [ ] Dégâts des épées 4/5/6/7/8 (+0,5 par niveau), balayage vanilla.
- [ ] Dague : 3 dégâts, très rapide ; dans le dos d'un zombie qui ne te cible pas : « FURTIF ×3 ».
- [ ] Hache : forte et lente ; casse la barricade en 3 coups.
- [ ] Lance (vanilla 26.x) et masse (vanilla) : fonctionnent comme en vanilla, niveau ajouté.
- [ ] Gravures : Saignement (zombie qui saigne), Feu (prend feu), Recul (repoussé fort), Vol de vie (cœurs, PV rendus), Fendoir (zombies voisins touchés à 50 %).
- [ ] Arc / arbalète (perce 1) : bruit 5, dégâts affichés sur les zombies, niveau ajouté à la flèche.
- [ ] Couteau de lancer : lancé au clic droit, l'objet se voit en vol, 5 dégâts.
- [ ] Bouteille incendiaire : verre brisé, flaque de flammes 5 s, ce qui est dedans prend feu.
- [ ] TNT à mèche : lancée, explose après 2,5 s, aucun bloc cassé.
- [ ] Pistolet : clic droit tire (traînée grise, fumée), 6 balles, F = recharger 2 s (barre de recharge sur l'objet), chargeur vide = recharge auto ; tête = 1,5×.
- [ ] Tromblon : 5 plombs dispersés, courte portée (16), recule le joueur, 2 cartouches.
- [ ] Carabine : 80 blocs, tête ×2, lente (1,5 s entre deux tirs), 5 balles.
- [ ] Munitions consommées (sauf levier) ; message « Plus de munitions » sans balles.
- [ ] Tir à travers une vitre de la pièce fermée : la vitre casse.
- [ ] Rien ne s'use (durabilité), on garde tout en mourant, réapparition au hub de la salle.

## Stand de tir (sud)
- [ ] Trois cibles (mannequins en rouge) à 10, 20 et 40 m, mur de cibles derrière.
- [ ] Chaque tir (arme à feu, flèche, couteau, coup d'épée) : barre d'action « Cible N m | X dégâts | TÊTE ou corps | tir à N m » et texte au-dessus de la cible mis à jour. Les cibles ne meurent pas et ne bougent pas.

## Meubles et fouille (ouest, pièce fermée au plafond)
- [ ] Porte en bois d'entrée (bruit 6).
- [ ] Une étagère de CHAQUE bois (chêne, sapin, bouleau, acajou, acacia, chêne noir, palétuvier, cerisier, chêne pâle, bambou, carmin, biscornu, peuplier) avec son nom : 1 ou 2 objets sur 3. Interaction VANILLA (prendre, échanger) : à essayer pour décider de la fouille.
- [ ] Coffre, coffre en cuivre, 2 tonneaux remplis ; 3 pots décorés (bandage, fusée d'appât, clé du coffre-fort) : on casse le pot ou on clique.
- [ ] Bibliothèque ciselée (4 livres dont un rapport écrit), pupitre avec « Carnet de route », feu de camp avec 4 viandes qui ne cuisent jamais.
- [ ] Sable et gravier suspects : au pinceau, on trouve des balles / un ressort.
- [ ] 2 porte-armures habillés : on prend les pièces (vanilla).
- [ ] Cadre et cadre lumineux (hache, pistolet) : un coup fait tomber l'arme, le cadre reste.
- [ ] Cadavre (mannequin couché ; pose SLEEPING si valide) : clic droit = « Sac d'un survivant ». Vérifier qu'il est bien couché, pas debout ni dans le sol.
- [ ] Coffre-fort : verrouillé ; avec la clé (pot) → déverrouillé ; sinon accroupi + clic droit 5 s (bruyant), arrêt si on se relève.
- [ ] Porte barricadée : 3 coups de hache, sinon message « Il faut une hache » ; derrière, la pièce fermée (coffre avec le tromblon).
- [ ] Vitres de la pièce fermée : se cassent à la main ou au tir (bruit 16).
- [ ] Bouton « Remettre les objets » : tout revient (contenus, barricade, vitres, coffre-fort verrouillé, cadres, porte-armures).
- [ ] `/refuge essai build` reconstruit toute la salle (efface et repose tout, munitions infinies coupées).

## Commandes
- [ ] `/refuge give <joueur> <objet>[+niveau] [n]` (ex. `pistolet+3`, `balle_pistolet 32`), complétion des ids.
- [ ] `/refuge reload` relit le config et renvoie le pack aux joueurs connectés.

## Étape 1 – Expédition (chantier EXPÉ, rien n'a tourné sur un serveur)

### Installation et zone
- [ ] Au démarrage : « Butin : 14 tables, ... entrees », « Zone bourgade (La Bourgade) : monde refuge_bourgade, 4 arrivee(s), 3 extraction(s), 10 point(s) de horde, 2870 conteneur(s) », puis « Copie refuge_bourgade_modele prete » et « Modele refuge_bourgade_modele pret ». Aucun « Zone bourgade : ... » d'avertissement, aucun « Monde refuge_bourgade absent ».
- [ ] `plugins/EemiRefuge/zones/bourgade.yml`, `bourgade_containers.yml`, `exemple.yml` (désactivé) et `loot.yml` créés au premier démarrage.
- [ ] `/refuge zone verifier bourgade` : chargement des chunks puis « Aucun problème » (ou liste lisible) ; fichier `verification_bourgade.txt` écrit. Arrivées et extractions : air aux pieds et à la tête, sol dessous.
- [ ] `/refuge zone liste` : bourgade, « modèle prêt ».
- [ ] Monde de zone : pas de cycle jour/nuit, pas de mob naturel, spawners éteints, bordure du monde au bord exact du quartier (-380..-131 / -350..-101).

### Lancement
- [ ] `/refuge expe start bourgade <toi>` (jour) puis `... <toi> nuit` : « Préparation de La Bourgade », téléporté sur une des 4 arrivées (toute l'escouade au même point), titre « La Bourgade », messages d'arrivée et liste des extractions.
- [ ] Deuxième expédition en même temps (autre joueur) : copie `refuge_bourgade_2` créée (« Copie refuge_bourgade_2 prete »), zone neuve ; à la fin, la copie est supprimée. Une 4e expédition simultanée est refusée (« Toutes les instances... »).
- [ ] `/refuge expe liste` et `/refuge expe stop [bourgade] [n]` : arrêt propre, joueurs renvoyés au Refuge avec ce qu'ils portent.
- [ ] Barre de boss : « ⌚ 00:42 / 25:00 │ Marée : Calme », verte, puis jaune (15 min), rouge (20 min), violette (25 min).
- [ ] Tableau latéral : zone + jour/nuit, temps, Marée, escouade (❤ PV des coéquipiers, « À TERRE 32 s », Extrait, Mort), zombies tués, extraction la plus proche avec distance et flèche (la flèche pointe dans la bonne direction quand on tourne).

### Butin et fouille
- [ ] Tonneaux, coffres : vides à l'origine, remplis au départ selon la pièce (salon, maison, cuisine, bureau, garage...) ; environ 40 % de cases vides ; la nuit un peu plus de rare.
- [ ] Première ouverture : barre « Fouille ▌▌▌ » 1 à 2 s puis le menu s'ouvre ; annulée si on bouge ou si on prend un coup ; deuxième ouverture immédiate.
- [ ] Fours, fumoirs, hauts-fourneaux : au plus un objet dans la case du résultat.
- [ ] Entonnoirs, distributeurs, droppers, pupitres, porte-armures, cadres : jamais remplis.
- [ ] Aucune « objet inconnu » sauf `plan_epee_pierre` / `plan_pistolet` (plans pas encore au Catalogue). Aucune « pas de conteneur en ... ».
- [ ] On ne peut ni casser ni poser de bloc, ni utiliser un seau ; cadres et porte-armures du décor protégés ; portes ouvertes refermées à la fin dans le monde d'origine.

### Zombies, bruit, Marée
- [ ] Zombies d'ambiance (Rôdeur, Coureur, Brute) qui apparaissent dans la rue, hors de vue, à plus de 24 blocs ; pas plus de ~8 par joueur seul (+50 % par joueur en plus, ×1,5 la nuit et plus rapides).
- [ ] Bruit : barre d'action « Bruit ▌▌ » comme dans la salle d'essai ; les zombies viennent voir.
- [ ] Hordes : première vers 4 min (« Une horde approche ! » + cor), aux points `hordes` à 30-80 blocs ; les zombies de horde foncent sur le joueur le plus proche.
- [ ] Marée à 15 min (hordes de plus en plus fréquentes), horde permanente à 20 min, brume noire à 25 min (Darkness, fumée, dégâts croissants), mort de tous à 27 min.
- [ ] Les zombies disparaissent à la fin de l'expédition ; aucun zombie ne brûle, ne lâche d'objet.

### Extraction
- [ ] Cercle de particules vertes autour des 3 extractions (rayon 6), message « accroupis-toi 3 s ».
- [ ] Accroupi 3 s dans le cercle (ou clic droit avec une fusée de feu d'artifice) : fusée rouge tirée, barre « Extraction ... 45 à 90 s », cercle orange, vagues de zombies toutes les 10 s.
- [ ] À la fin : ceux qui sont dans le cercle partent (téléportés au Refuge, bilan « extrait »), les autres restent ; on peut repartir par un autre point.
- [ ] Le butin ramené (objets en plus du départ) apparaît dans le bilan, avec la valeur estimée en notes.

### À terre, mort, sac, assurance
- [ ] En escouade, à 0 PV : « À TERRE » (pose couchée, ralenti, 40 s, coups annulés, les zombies l'ignorent) ; un coéquipier accroupi à côté 4 s le relève à 30 % des PV ; la 3e mise à terre tue.
- [ ] Seul (ou plus personne debout) : mort directe ; avec l'objet `seconde_chance` (s'il existe au Catalogue) : relevé à 30 %.
- [ ] Mort : tout ce que portait le joueur va dans un tonneau « Sac de <pseudo> » posé là où il est mort (au dernier sol sûr s'il est tombé) ; l'escouade peut l'ouvrir et se servir ; le mort est renvoyé au Refuge (bilan « mort »).
- [ ] Le sac reste pour l'expédition suivante : à la prochaine expédition de ce joueur dans la Bourgade, le tonneau est reposé au même endroit avec ce qu'il en restait (message avec les coordonnées) ; perdu à la fin de celle-ci. Fichier `plugins/EemiRefuge/sacs.yml`.
- [ ] Assurance : les objets assurés (profil) ne sont pas dans le sac et sont rendus au Refuge (« Assurance : N objet(s) ») ; l'assurance est vidée après l'expédition (extraction ou mort).
- [ ] Déconnexion en expédition = mort : sac posé, inventaire vide à la reconnexion, objets assurés et bilan rendus en arrivant au Refuge.
- [ ] `/lobby` (ou toute téléportation hors de la zone) en expédition = mort, sac posé.
- [ ] Mort vanilla (`/kill`) : même résultat (sac, réapparition au Refuge).
- [ ] Monde d'origine (partie 1) : après l'expédition, les tonneaux des sacs ont disparu (bloc d'origine remis) ; après un redémarrage en pleine expédition, remis au démarrage (`restauration.yml`).

### Inventaire et EemiCore
- [ ] Groupe Multiverse-Inventories `refuge` (refuge, refuge_bourgade, _2, _3) : on part avec son stuff, on revient avec le butin ; rien ne passe au lobby.
- [ ] EemiCore : `eemi status refuge playing 0 N M` pendant une expédition ; `reward ... refuge kill` (10 max par expédition), `win` à l'extraction, `play` à la fin.

## Étape 1 – Refuge (13/11/2026, rien testé en jeu)

### Monde et construction
- [ ] Logs au premier démarrage : « Monde refuge cree (vide). », « Construction du Refuge (version 1). », « Refuge construit en N ms. », « Boutique du Refuge : 17 articles, 18 rachats. », « Groupes EemiCore lus par reflexion », « Refuge pret. ». Aucun « Refuge : bloc ... impossible », « PNJ ... impossible », « Panneau ... impossible ».
- [ ] `/refuge` depuis le lobby : téléporté au Refuge (0.5 65 9.5, face au feu), pack Refuge chargé ; `/refuge` au Refuge = menu Profil ; refusé en expédition.
- [ ] Rendu du poste (captures) : palissade, portail fermé au nord avec lanternes, 2 miradors (échelle au sud, garde-corps, projecteur), feu de camp + bancs, Armurerie à l'est, Notaire à l'ouest, Réserve au sud, 2 tentes, sacs de sable gris, lampadaires. Rien ne flotte, pas de demi-lit, chemins propres.
- [ ] On ne peut pas sortir (palissade, miradors) ; une chute sous y 44 ramène au spawn.
- [ ] Jour fixe, aucun monstre, aucun dégât (chute, joueurs), faim gelée ; pas de casse/pose de blocs hors créatif ; on ne peut rien jeter ; tonneaux/coffres/enclume/table du décor ne s'ouvrent pas.
- [ ] 3 PNJ (Le Passeur, L'Armurier, La Notaire) : skins vanilla Kai / Makena / Efe (clé `entity/player/...` du profil, à vérifier), veste de cuir teinte, objet en main, nom + rôle. Clic droit ET clic gauche = leur menu. Panneaux texte au-dessus du portail, de l'Armurerie, de la Notaire, de la Réserve.
- [ ] Après un redémarrage, PNJ et panneaux réapparaissent une seule fois (pas de doublon). `/refuge refuge build` reconstruit (efface tout le poste).

### Menus (avec le pack) – captures de chacun
- [ ] Fond aligné sur le coffre (cases du fond = cases vanilla, onglet de titre centré qui dépasse en haut, ✕ et i en haut), aucun décalage d'un pixel ; inventaire du joueur visible et normal dessous.
- [ ] Textes dynamiques (petites capitales) à leur place : zone choisie (Expédition), pseudos sous les têtes (Escouade), Ferraille (Armurier, Assurance), chiffres du Bilan, lignes du Profil. Couleurs lisibles.
- [ ] Calques : bouton choisi encadré d'or (jour/nuit, acheter/vendre, zone), cadre vert « prêt », cadres des membres (or chef, vert prêt, rouge pas prêt), PARTIR grisé pour un non-chef.
- [ ] Survol d'un bouton : nom + description ; objets invisibles bien invisibles (pas de violet/noir).
- [ ] Clics : maj-clic, glisser, touches 1-9, double-clic, Q dans un menu : rien ne bouge, rien n'est volé ni dupliqué.
- [ ] Sans pack (`pack.enabled: false`) : mêmes menus en objets vanilla lisibles.

### Escouades et départ
- [ ] Solo : Passeur → Expédition → Partir lance `expe().start` (seul).
- [ ] Groupe `/party` de 2 au Refuge : l'escouade = le groupe, le chef du groupe est chef ; le membre doit cliquer Prêt avant que le chef puisse partir ; « X n'est pas prêt » sinon.
- [ ] Sans groupe : Créer, Inviter (liste des joueurs présents ; invitation cliquable [REJOINDRE]/[REFUSER] 60 s), Entrer (liste des escouades ouvertes), Quitter, clic sur un membre (chef) → Exclure / Passer chef.
- [ ] Jour/Nuit et zone choisis par le chef seulement ; changer remet les « prêt » à zéro.
- [ ] File publique (loupe) : deux joueurs seuls qui cherchent la même zone sont réunis (« Coéquipiers trouvés ! »).
- [ ] Déconnexion d'un membre : il quitte l'escouade (sauf groupe /party), le chef passe au suivant.

### Réserve, Armurier, Notaire, Bilan
- [ ] Réserve (clic sur un tonneau/coffre du fond de la Réserve, ou `/refuge reserve`) : 27 cases, dépôt/retrait, maj-clic depuis le sac va dans les 27 cases (jamais dans l'en-tête), contenu gardé après déconnexion et redémarrage ; ouverture impossible hors du Refuge ; téléportation pendant l'ouverture = menu fermé, rien perdu.
- [ ] Armurier : acheter (Ferraille débitée, objet reçu, refus si sac plein ou pas assez), vendre (clic 1, maj-clic tout ; objets améliorés/gravés jamais rachetés). `plugins/EemiRefuge/refuge-shop.yml` créé, `/refuge refuge boutique` le relit.
- [ ] Notaire : clic sur un objet du sac = assuré (3 max, prix selon rareté), clic sur un objet assuré = annulé et remboursé ; objet assuré absent au départ = remboursé.
- [ ] Bilan à l'arrivée au Refuge (extrait / mort), chiffres justes, butin en icônes ; Ferraille du bilan créditée une seule fois ; `/refuge bilan` le rouvre.
- [ ] Admin : `/refuge ferraille <j> +100`, `=0`, `/refuge acte <j> 2` ; profils dans `plugins/EemiRefuge/joueurs/<uuid>.yml`.
