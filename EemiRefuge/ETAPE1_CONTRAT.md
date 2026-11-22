# Dernier Refuge – étape 1 « socle jouable » : contrat entre les trois chantiers (13/11/2026)

But : une vraie expédition de bout en bout, en escouade, dans la zone « La Bourgade » (Mosslorn, quartier A), au départ d'un Refuge.
Conception : `CONCEPTION.md` (la section 0 prime). Bugs connus à NE PAS corriger maintenant : `A_CORRIGER.md`.

## Qui touche à quoi (ne jamais modifier les fichiers d'un autre chantier)

| Chantier | Fichiers à lui |
|---|---|
| **ZONE** (map) | `maps_refuge/`, `EemiRefuge/src/main/resources/zones/bourgade.yml`, `a_deposer/maps/` |
| **REFUGE** (monde du Refuge, PNJ, interfaces, escouades et matchmaking, réserve, marchand, profils, bilan) | `src/main/java/fr/eemicraft/refuge/refuge/**`, `src/main/java/fr/eemicraft/refuge/ui/**`, `Catalogue.java` (ajouts seulement, en fin de liste), `pack/gen_refuge.py`, `pack/gen_ui.py` (nouveau), textures du pack |
| **EXPÉ** (expédition : instances de zone, butin, zombies en zone, Marée, bruit en zone, extraction, mise à terre et relève, sac, assurance, fin) | `src/main/java/fr/eemicraft/refuge/expe/**`, `Zombies.java`, `Bruit.java`, `Armes.java`, `EemiRefuge.java`, `plugin.yml`, `config.yml` du plugin |

Le contrat Java est dans `src/main/java/fr/eemicraft/refuge/api/` (`ExpeditionApi`, `RefugeApi`, `Profile`, `Bilan`, `ZoneInfo`) : personne ne le modifie sans le signaler dans son rapport. `EemiRefuge.java` crée déjà `new refuge.Refuge(this)` et `new expe.Expeditions(this)`, appelle `enable()/disable()`, et passe `/refuge ...` d'abord à `refuge.handleCommand`, puis à `expe.handleCommand`, puis aux commandes admin de la salle d'essai. Accès : `plugin.refuge()`, `plugin.expe()`.

Compilation (plusieurs chantiers en même temps) : chacun dans son dossier de build :
`bash C:/Users/Utilisateur/AppData/Local/Temp/claude/55dd9ace3d9c62d0/12904808-f2d9-496a-9ca1-f63828824815/scratchpad/tools/build.sh C:/Users/Utilisateur/Desktop/mc/EemiRefuge "-DbuildDir=target_<chantier> package"`
(l'autre chantier peut être en train d'écrire : si la compilation casse dans SES fichiers, attends un peu et recompile, ne les touche pas).

## Noms et règles communs
- Mondes : `refuge` (le Refuge, créé et construit par le chantier REFUGE), `refuge_bourgade` (monde source de la zone, importé), `refuge_bourgade_modele` et copies `refuge_bourgade_2`, `_3`… (chantier EXPÉ, via `_commun/WorldClone.java` copié dans `expe/`, comme les autres plugins maison : voir `C:\Users\Utilisateur\Desktop\mc\_commun\BRIEF_MULTIPARTIE.md`). Tous les mondes du mode commencent par `refuge` : le pack du mode n'est chargé que là (`pack.mondes: [refuge]`).
- Inventaire : Multiverse-Inventories aura un groupe `refuge` = `refuge` + toutes les zones et leurs copies (inventaire partagé : on part avec ce qu'on porte, on revient avec ce qu'on ramène). `refuge_essai` reste à part. La réserve est un coffre virtuel du plugin (chantier REFUGE). Respecter `_commun/BRIEF_INVENTAIRE.md` (ne jamais vider un inventaire dans le monde d'arrivée).
- Monnaie : Ferraille (⚙), propre au mode, dans `Profile`.
- Combat moderne (pas d'OldCombatMechanics) ; armes, zombies, bruit de la salle d'essai réutilisés.
- Textes joueurs en français avec accents, préfixe `§8[§aDernier Refuge§8] §7` (`Txt`). Console sans accents.
- Toute API Paper utilisée est vérifiée contre `C:\Users\Utilisateur\.m2\repository\io\papermc\paper\paper-api\26.3.build.218-stable\paper-api-26.3.build.218-stable.jar` (javap).
- Aucune action sur le serveur, rien de publié, CLAUDE.md non modifié.

## Interfaces (exigence de Thibault)
Style de `references/style_navigator.png` : panneau gris clair à bord sombre façon menu Minecraft, onglet de titre en haut (petites capitales), boutons ✕ (fermer) et i (infos) en haut, GROS boutons (2×2 ou 3×2 cases) avec une icône pixel art et un nom dessous, pas de vitres de remplissage, inventaire du joueur visible en bas. Technique : fond dessiné dans une police du pack Refuge (glyphe dans le titre, comme les fonds du lobby `font/lobby/bg_*` du pack EEMICRAFT, voir `C:\Users\Utilisateur\Desktop\mc\EemicraftPack\art_lobby.py`), cases cliquables = objets invisibles (`item_model` transparent `refuge:gui/vide`) avec nom et description au survol. Ces menus ne s'ouvrent que dans les mondes `refuge*` (le pack n'est pas chargé ailleurs).

## Décisions REFUGE (13/11/2026)
- **Bilan** : `showBilan` crédite `bilan.ferraille()` au profil (0 pour l'instant : EXPÉ met la valeur estimée du butin dans `notes`, on vend à l'Armurier). L'XP n'est PAS recréditée : EXPÉ l'a déjà ajoutée par `profile.addXp`. L'assurance est soldée par EXPÉ (`setInsured(List.of())` à l'extraction et à la mort) ; Refuge ne la touche pas dans `showBilan`. Si le joueur n'est pas au Refuge quand `showBilan` arrive, le menu Bilan s'ouvre à son arrivée (30 ticks après) ; `/refuge bilan` le rouvre.
- **Assurance** : payée à la Notaire (3 objets max, prix selon la rareté × paquets de 16, `refuge-shop.yml`), copie stockée dans `Profile.insured()`. Au départ (`Escouades.depart`, avant `expe().start`), un objet assuré qui n'est plus sur le joueur est retiré et remboursé.
- **Escouade** : le chef de l'escouade (ou du groupe `/party`) appelle `expe().start(leader, squad, zoneId, night)` ; la liste passée contient tous les membres, tous au Refuge, prêts, sans expédition en cours, acte ≥ acte de la zone, nombre entre `minPlayers` et `min(4, maxPlayers)`, `freeInstances > 0`. Menus des membres fermés avant l'appel.
- **Groupes EemiCore** : lus par réflexion (`EemiCore.partyOf`, `Party.leader`, `Party.members`), toutes les secondes ; si la lecture échoue, escouades du mode seulement (avertissement unique dans la console).
- **Commandes** prises par Refuge (le reste revient à `false`) : `/refuge` (seul : aller au Refuge, ou menu Profil si déjà au Refuge), `escouade [accepter <joueur>|refuser|quitter]`, `reserve`, `profil`, `bilan` ; admin (`eemirefuge.admin`) : `refuge build|boutique|tp`, `ferraille <joueur> <+n|-n|=n>`, `acte <joueur> <n>`.
- **Interfaces** : sans pack (`pack.enabled: false`, pack refusé ou monde hors `refuge*`), mêmes menus en objets vanilla avec titre simple.
