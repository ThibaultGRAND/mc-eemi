# Hors Cours (titre de travail) – document de conception

Mode horreur / exploration en campagne, solo ou coop (1 à 4), dans l'esprit des jeux de Backrooms
mais avec nos propres niveaux : chaque niveau est un lieu de l'EEMI qui a « mal tourné ».
Plugin maison Minecraft (Paper 26.3) + pack de textures/sons EEMICRAFT.

Statut : conception. Rien de codé, rien de construit.

## 1. Principe

Esprit Escape the Backrooms, version EEMI : exploration, survie, énigmes et monstres, pas
d'escape game ni de minuteur (Thibault, 06/11/2026).

Un soir, on reste tard à l'EEMI. Une voix nous parle : c'est la Moustache, mais on ne le
découvre qu'au combat final (validé par Thibault le 06/11/2026). On explore l'école vide, qui se dérègle de plus en plus, jusqu'à
passer à travers un mur : début des niveaux.

Règle de conception : **que des vrais éléments de l'école**. Seul lieu inventé : la salle serveur
(école d'informatique), qui part de la vraie baie réseau du local imprimante.

## 2. Les pièces (seule référence retenue)

Visuels pixel art de la page Notion « Pièces » (06/11/2026), copiés dans `references/salles/`.
Thibault : ne prendre en référence que ces pièces. Seules exceptions gardées du Notion :
la Moustache (boss de fin) et Louis (PNJ amical avec ses bières). Rien d'autre (pas de code du
coffre, pas d'énigmes ni de trame de l'ancien escape game).

| Fichier | Pièce | Éléments notables |
|---|---|---|
| 01 | Couloir côté #Directeur / #Staff | Portes bois « #Directeur » et « #Staff », télé murale, fauteuils jaune/bleu, poufs en S bleu/orange, vitrages à pois, frise de ville au mur, sortie de secours |
| 02 | Couloir côté #Cuisine | Porte « #CUISINE » avec affiches (tri, QR), vitre sur la cuisine, kakémono eemi « DARE TO BE », poufs |
| 03 | Bureau du directeur | Bureau noir, double écran, carte encadrée, machine à café, portemanteau, fauteuil noir, balcon |
| 04 | Salle de réunion | Grande table noire, chaises hautes, télé sur pied, tableau jaune mobile, vue sur la cuisine |
| 05 | Salle 02 (salle de cours) | Tables blanches sur roulettes, grand écran sur pied, porte « 2 », fenêtres à balcon |
| 06 | Bureau du staff | Bureau en bench avec séparateur jaune, cartons, bouteilles d'eau |
| 07 | Salle de pause / zone repas | Cuisine noire, 2 micro-ondes, frigo, machine à café, tables bois, chaises blanches, canapé orange |
| 08 | Local imprimante | Grande imprimante, **baie réseau murale**, réserve encombrée de cartons |
| 09 | Salle 01 (salle de cours) | PC portables, écran mobile |

Ce sont des rendus, pas des photos : bons pour le décor et les couleurs (moquette bleu nuit, faux
plafond, portes bois, jaune/bleu/orange), pas pour les dimensions. Plan à l'échelle encore à trouver
(plans d'évacuation).

## 3. Campagne

| # | Niveau | Pièces | Monstre / mécanique |
|---|---|---|---|
| 0 | Prologue : l'EEMI | Toutes (01 à 09) | Aucun ; exploration, la voix, « bugs » de plus en plus visibles |
| 1 | Le Couloir | Couloirs 01-02, répétés à l'infini | **Loïc** (le commercial) : ne jamais le quitter des yeux |
| 2 | La Salle Serveur | Part de la baie du local imprimante (08) | **Le Câbleur** : il chasse au bruit |
| 3 | La Salle de cours | Salles 01 et 05, enchaînées et déformées | **L'Intervenant** : « 1, 2, 3 soleil » + énigmes au tableau |
| 4 | La Zone repas | Salle de pause (07) | **Le Double** ; zone de repos, **Louis** |
| 5 | Le Bureau du directeur | Bureau du directeur (03) | Boss final : **la Moustache** |

### 3.0 Prologue – l'EEMI
- Court niveau d'introduction (tutoriel) : on explore l'école vide le soir, on apprend la lampe,
  la craie, le talkie. Petites énigmes d'environnement avec les objets des pièces (télés, PC,
  imprimante, machine à café, frigo, tableau jaune, cartons).
- La voix guide d'abord gentiment, puis se contredit. Lumières qui clignotent, portes qui ne mènent
  plus au même endroit, un couloir un peu trop long.
- Fin : on « traverse » un mur (noclip) → Niveau 1.

### 3.1 Le Couloir – Loïc
- Les deux couloirs (portes #Directeur / #Staff / #Cuisine, poufs, télé, frise de ville) découpés en
  modules et assemblés en labyrinthe généré ; néons qui grésillent, portes qui donnent sur le même couloir.
- **Loïc, le commercial** (skin `references/skins/loic.png`, 64×64, reçu le 06/11/2026) : immobile tant qu'un joueur le regarde, avance dès que
  personne ne le voit. S'il t'attrape : écran noir, son pitch (« Vous avez deux minutes ? »),
  retour au checkpoint.
- Objectif : trouver N fusibles / badges pour ouvrir la sortie. Craie indispensable.

### 3.2 La Salle Serveur – Le Câbleur
- La baie du local imprimante s'ouvre sur des rangées de baies à perte de vue : câbles, chaleur,
  ventilation, imprimantes qui impriment toutes seules.
- **Le Câbleur** : aveugle, attiré par le sprint, les portes, le talkie, les imprimantes.
  S'accroupir = silencieux, le flash l'étourdit.
- Énigmes « info » : rebrancher des câbles, redémarrer des serveurs, couper la clim pour couvrir le bruit.

### 3.3 La Salle de cours – L'Intervenant
- Salles 01 et 02 enchaînées, de plus en plus déformées (tables au plafond, écran qui écrit seul).
- **L'Intervenant** : dos tourné (il écrit), on bouge ; il se retourne, il faut être assis et
  immobile. Les questions au tableau sont les énigmes.

### 3.4 La Zone repas – Le Double
- La salle de pause : micro-ondes, frigo, machine à café, canapé orange. Sauvegarde, recharge, café.
- **Le Double** (coop) : prend le pseudo et le skin d'un coéquipier ; indices : ne répond pas au
  talkie, lampe éteinte, ne marque pas à la craie.
- **Louis** : seul PNJ amical du jeu, posé dans la zone repas avec ses bières. Il discute, donne
  des indices sur la suite et sur la Moustache ; une bière = petit bonus (peur qui baisse).

### 3.5 Le Bureau du directeur – la Moustache
- Le bureau du directeur, qui grandit à chaque phase.
- **La Moustache** : boss de fin (moustache géante en 3D). C'est aussi la voix qui nous guide depuis
  le début : la révélation arrive ici.
- Combat en phases qui reprend les mécaniques des niveaux (regard, bruit, immobilité, faux
  coéquipier). Fin : retour dans la vraie école.

## 4. Objets
Lampe torche (batterie), craie, talkie-walkie, appareil photo à flash, café de la machine (peur et
endurance), clés USB (histoire), badge.

## 5. Systèmes
Jauge de peur, endurance, coop 1-4 avec énigmes réparties, checkpoints, voix (répliques .ogg + sous-titres).

## 6. Technique (à vérifier avant de coder)
- Plugin `EemiHorsCours`, parties en parallèle avec `WorldClone`, statut / récompenses EemiCore.
- Prologue, zone repas, bureau : construits à la main par les builders d'après les visuels. Couloir,
  salle serveur, salles de cours : modules copiés de la vraie école, assemblés par un générateur
  (comme l'Abysse).
- Monstres 3D : modèles Blockbench en `item_display` animés (comme EemiRing) ; Loïc en Mannequin
  avec son skin.
- Ambiance : obscurité, brouillard, sons du pack.

## 7. Reste à recevoir / décider
- Plan à l'échelle (plans d'évacuation).
- Voix de la Moustache : Thibault l'enregistre. Textes dans `VOIX_MOUSTACHE.md` (72 répliques
  numérotées, un fichier, 3 s de silence entre chaque) ; découpage auto puis sons
  `eemicraft:horscours.moustache.NN` dans le pack, filtre talkie sur 1-62, voix nue sur 63-72.
- Skins de Louis et modèle 3D de la Moustache.
- Ton (ambiance / jump scares), durée visée.

## 8. Étapes
1. Ce document validé.
2. Première version jouable : prologue + Couloir + Loïc.
3. Salle serveur, salles de cours, zone repas, bureau, histoire complète.
