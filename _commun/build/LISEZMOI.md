# Bibliothèque commune de construction de maps (`fr.eemicraft.build`)

Cette bibliothèque Java 21 sert à appliquer les règles du skill `builder-minecraft` (façades en 3 plans, toits en pente avec débord, variantes en grappes, terrain continu, arbres sur mesure, contrôles chiffrés) dans les plugins dont la map est générée par code.

- **Cœur** (`src/fr/eemicraft/build/`) : aucune dépendance Bukkit ; tout s'écrit dans un `Sink`.
- **Adaptateur Paper** : `bukkit/fr/eemicraft/build/bukkit/BukkitSink.java`.
- **Démo et test hors serveur** : `demo/fr/eemicraft/build/demo/Demo.java`.

À lire avant de s'en servir : `.claude/skills/builder-minecraft/SKILL.md` et `reference/regles.md`. La bibliothèque applique les règles C, D, E, F, G et I de ce fichier, mais ne choisit ni la composition, ni le gabarit de jeu, ni la palette.

## API

| Classe | Rôle |
|---|---|
| `Sink` | `set(x,y,z,String state)` / `get(x,y,z)`. Les états sont vanilla : `"minecraft:oak_stairs[facing=north,half=top]"`, et `"minecraft:air"` pour vide. On trouve aussi `set(x,y,z,Mat)`, `isAir`, `isSolid`, `offset(dx,dy,dz)` et `Sink.of(setter, getter)` pour envelopper un builder existant. |
| `Mat` | Donne l'état à poser à une position. `Mat.of("minecraft:stone")`, `Mat.AIR`, une `Palette` ou un dégradé. |
| `B` | Fabrique les chaînes d'état : `stairs(id, facing, top)`, `slab(id, "bottom"/"top"/"double")`, `axis(log, 'y')`, `trapdoor`, `door`, `pane(id, n, e, s, w)`, `leaves` (persistent=true), `with(state, clé, valeur)`, `prop`, `name`, `isSolid` (heuristique sur le nom). |
| `Dir` | NORTH/EAST/SOUTH/WEST, avec `opposite()`, `cw()`, `ccw()` (même sens que Minecraft), `axis()`, `id()`. |
| `Noise` | Simplex 2D/3D de Gustavson (domaine public), avec graine. On y trouve aussi : `fbm2/fbm3(x,z,oct,pers,lac)`, `ridge2`, `warp2(x,z,amp,période)`, `uniform2/uniform3` (bruit ramené à une répartition uniforme dans [0,1], pour fixer une proportion), `derive(sel)` et `hash`. |
| `Palette` | Liste ordonnée **clair → sombre** avec poids. `at`/`clustered(x,y,z)` donne le bloc principal plus des variantes en grappes : chaque variante a son propre canal de bruit 3D de période `period(…)`, à la proportion de son poids, jamais tirée bloc par bloc. `pick(x,y,z,t)` donne un dégradé : t vaut 0 pour le clair et 1 pour le sombre, plus un bruit basse fréquence ±`amp`. On trouve aussi `vertical(y0,y1)` (sombre en bas) et `gradient(tField)`. |
| `Shapes` | `fill`, `box` (coque), `hollow`, `walls`, `cylinder` (plein ou creux), `sphere`, `ellipsoid`, `cone` (r1 > r0 donne un cône inversé), `line`, `arch` (plein cintre ou brisé, pour percer avec `Mat.AIR` ou pour remplir). |
| `Sdf` | Champ de distance : `sphere`, `ellipsoid`, `box`, `cylinder`, `cone`, avec `union`, `smoothUnion`, `minus`, `intersect`, `shell(t)`, `deform(noise, amp, période)`, `translate`. Se pose avec `fill(sink, mat)` ou `fillLayered(sink, profondeur -> Mat)` (herbe, terre, pierre). |
| `Style` | Réglages d'une façade : Mat du mur, du socle, des angles et des planchers ; identifiants des poteaux, vitres, appuis, linteaux, volets, bandeaux, corniche et porte ; écart des poteaux (4-6), hauteur d'étage (4-5), part des volets, part des travées aveugles, pilastres intérieurs. Les setters sont chaînables. |
| `Facade` | `wall(sink, style, x0, z0, along, len, out, yBase, yTop, doorT)` pose un mur droit en 3 plans. `building(sink, style, x1,z1,x2,z2, yBase, yTop, côtéPorte, doorT, murs à sauter…)` pose un bâtiment complet : intérieur, planchers, plafond, 4 façades et pilastres. `postPositions(len, écart)` donne la place des poteaux. |
| `RoofStyle` | Palette d'escaliers (claire au faîte, sombre à l'égout), dalle de faîte, corps, pignon, poteaux de pignon, débord, mode dalles à 22,5°. |
| `Roof` | Parties `gableX` / `gableZ` / `hip(x1,z1,x2,z2,yTop)`, avec `slope(0.5)` en option. `build(sink, style, parties…)` compose les parties par `max` (toits en L, en T, en croix, avec noue) et renvoie le Y du faîte. `flat(...)` pose un toit plat avec acrotère et couvertine (avec des éléments techniques en option), `chimney(...)` une cheminée. |
| `Terrain` | Heightmap sur un rectangle : `fbm(base, amp, période, octaves, warp)`, `ridges`, `dig(cx,cz,r,prof)`, `pad(x1,z1,x2,z2,y,raccord)` (plateforme verrouillée), `smooth(passes)`, `meltFill()`, `water(niveau)`. Ensuite `place(sink, Surface, yBas)` : la pente donne la roche, la proximité de l'eau donne la plage, il y a des taches nues et un substrat de 2 à 4 couches. `vegetation(sink, graine, densité)` pose des plantes en grappes sur l'herbe. On lit le résultat avec `height(x,z)`, `slope`, `surfaceType`. |
| `Tree` | `new Tree(log, leaves, leaves2).height(h).crown(r).place(sink, x, y, z, graine)`. Le tronc se décale tous les 3 à 5 blocs ; il a des racines, 2 ou 3 branches inégales, et des boules de feuilles bruitées et persistantes de 2 types. |
| `Stairs` | `fix(sink, boîte)` calcule les `shape` outer/inner comme le jeu (posés sans physique, les escaliers restent sinon droits). |
| `Connect` | `fix(sink, boîte)` relie les vitres, barreaux, barrières et murets **isolés** (toutes connexions à false/none). Il ne touche pas aux blocs déjà reliés. |
| `RecordingSink` | Sink en mémoire, par sections 16³ (tient des millions de blocs). Fournit `bounds()`, `count()`, `operations()`. |
| `SchemWriter` | `write(recordingSink, fichier)` écrit un Sponge v2 `.schem`, lisible par `analyze.py` et par WorldEdit/FAWE. DataVersion vaut 4671 par défaut (réglable). |
| `Check` | `Check.run(recordingSink)` rend un rapport avec les mêmes définitions qu'`analyze.py` : types, détail sur le bâti visible, mélange local (total et bâti seul), pan coplanaire et pan d'un même bloc, zones 10×10 de façade dominées à plus de 70 %, marches entre colonnes. `report.ok(7)` vérifie détail ≥ 15, mélange < 15 et pan ≤ 7. |
| `BukkitSink` | Adaptateur Paper : met en cache `Bukkit.createBlockData`, pose par `setBlockData(d, false)`, compte les opérations. Un état invalide devient de l'air, avec l'avertissement `Bloc invalide ignore : <état>` une seule fois par état. |

### Conventions de hauteur

- `Terrain.height(x,z)` est le Y du bloc de surface : on se tient en h+1.
- Pour un bâtiment posé sur une plateforme `pad(..., G, ...)` : `yBase = G + 1`, qui est le rang du socle et du sol intérieur. La porte est en yBase+1..+2 avec une marche dans le socle. `yTop = yBase + n × étage`, qui est le rang de la corniche.
- `Roof` reçoit **le même yTop** que la façade. Le toit touche le haut du mur au droit de la façade et descend d'un bloc par bloc de débord.

### Façade en 3 plans

La ligne donnée est la face la plus extérieure, et rien ne sort de l'emprise.

- **Plan P (saillie)** :
  - le socle, avec la fondation descendue jusqu'à un bloc plein (`groundDepth`, 16 au plus) ;
  - le chaînage d'angle (l'angle et un poteau de chaque côté) et les poteaux tous les 4 à 6 blocs ;
  - le bandeau à chaque plancher et la corniche, en escaliers retournés ;
  - les appuis de fenêtre, en escalier retourné ;
  - les volets, des trappes ouvertes posées contre le mur.
- **Plan P-1 (le mur)** : le remplissage, l'embrasure des fenêtres (laissée vide) et le linteau en dalle du haut.
- **Plan P-2 (l'intérieur)** : les vitres en retrait et les pilastres intérieurs.

Les fenêtres sont décidées par travée et par étage (hachage), jamais bloc par bloc.

## Exemple d'usage dans un builder

```java
Sink s = new BukkitSink(world, getLogger());          // ou un RecordingSink pour le test
Terrain t = new Terrain(seed, -60, -60, 60, 60).fbm(64, 5, 40, 4, 12)
        .dig(30, 25, 9, 5).smooth(2).pad(-8, -6, 8, 10, 64, 6).meltFill().water(61);
t.place(s, new Terrain.Surface(), 40);

Palette pierre = Palette.of(21, "minecraft:stone_bricks", 62, "minecraft:cracked_stone_bricks", 22,
        "minecraft:mossy_stone_bricks", 16).period(9).amp(0.2);
Style st = new Style().wall(pierre.vertical(65, 73)).spacing(4).storey(4);
Facade.building(s, st, -5, -4, 5, 2, 65, 73, Dir.NORTH, -1);
int faite = Roof.build(s, new RoofStyle().stairs("minecraft:spruce_stairs").overhang(1).spacing(4),
        Roof.gableX(-5, -4, 5, 2, 73));
new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(9)
        .place(s, 20, t.height(20, 5) + 1, 5, seed);
t.vegetation(s, seed, 0.7);                            // en dernier : ne pose que dans l'air
Stairs.fix(s, -60, 40, -60, 60, faite + 3, 60);
Connect.fix(s, -60, 40, -60, 60, faite + 3, 60);
```

Conseils tirés de la démo :

- **Mur d'un bloc d'épaisseur** (ses deux faces sont visibles) : prendre une palette de période **8-9** et `amp` 0,2. À la période 6, le mélange local sur le bâti montait à 18 %.
- **Roche cachée** sous le substrat : un seul bloc. Des variantes à cet endroit ne se voient qu'au bord d'un `.schem`, et y font monter le mélange.
- **Une zone de façade dominée à plus de 70 %** par le bloc principal reste possible quand le dégradé met tout le haut en bloc propre. Pour l'éviter, baisser le poids du principal ou ajouter une variante.

## Brancher dans un plugin

1. **Copier** `src/fr/eemicraft/build/*.java` (sans `demo/`) dans le plugin, sous `src/main/java/<package du plugin>/build/`, et **changer la ligne `package`** de chaque fichier. Par exemple, dans PowerShell, remplacer `package fr.eemicraft.build;` par `package fr.eemicraft.eemiinfection.build;`. Il n'y a aucun import croisé à corriger en dehors de ce package.
2. **Copier `BukkitSink.java`** dans `<package>/build/bukkit/`, en changeant son `package` et son `import fr.eemicraft.build.Sink;`.
3. **Adapter un builder existant** qui a déjà `set(int x, int y, int z, String state)` (et souvent un compteur d'opérations, des forceload, un découpage en ticks) :
   - le plus simple : `Sink s = Sink.of(this::set, this::get);`, ce qui garde son `set`, ses compteurs et sa journalisation. Sans `get`, passer `null` : tout est alors vu comme de l'air. Dans ce cas, la fondation de `Facade` descend toujours de `groundDepth` blocs (mettre `style.groundDepth = 0`), `Tree` écrase ce qui est en place et `Stairs.fix` ne voit pas les voisins ;
   - sinon : `Sink s = new BukkitSink(world, getLogger());`, puis lire `s.operations()` et `s.invalidStates()` à la fin.

   Ensuite, remplacer ce qui suit :

   | Code actuel du builder | Remplacement |
   |---|---|
   | murs `x==x1\|\|x==x2…` | `Facade.wall` / `Facade.building` |
   | toits plats | `Roof.build` |
   | `chance(0.04) ? a : b` | `Palette.clustered` |
   | sol à `y = G` | `Terrain` |
4. **Construction en plusieurs ticks** : les classes travaillent par appels. On peut donc appeler `Facade.building` pour un bâtiment par tick, ou faire écrire un `RecordingSink` puis vider ses sections petit à petit vers le `BukkitSink`.
5. **Contrôle** : à la fin du build, journaliser les types de blocs et `BukkitSink.invalidStates()`.

## Test hors serveur (obligatoire avant de livrer une map)

1. Dans le plugin, écrire une classe `main` de test, par exemple `src/test/java/.../TestMap.java` ou un fichier lancé à part, qui appelle **le builder du plugin** avec un `RecordingSink`. Le builder doit donc prendre un `Sink` en paramètre au lieu d'un `World` :
   ```java
   RecordingSink s = new RecordingSink();
   new MapBuilder(config).build(s);                     // le vrai builder du jeu
   System.out.println(Check.run(s));
   SchemWriter.write(s, new File("map.schem"));
   ```
2. Compiler et lancer sans serveur. Le JDK 25 se trouve dans le scratchpad (`tools/jdk/jdk-25.0.5+8`). Ne mettre sur le classpath que les classes du cœur, pas `BukkitSink` :
   ```powershell
   $jdk = "<scratchpad>\tools\jdk\jdk-25.0.5+8\bin"
   & "$jdk\javac.exe" --release 21 -encoding UTF-8 -d classes (Get-ChildItem -Recurse src,demo -Filter *.java).FullName
   & "$jdk\java.exe" -cp classes fr.eemicraft.build.demo.Demo out
   ```
3. Mesurer avec le script du skill : `python -I C:\Users\esteb\EEMICRAFT\.claude\skills\builder-minecraft\outils\analyze.py map.schem <joueurs>`. Les résultats arrivent dans `.claude/skills/builder-minecraft/out/<nom>.json`, `<nom>_dessus.png` et `<nom>_coupe.png`. **Regarder les deux PNG** et comparer au tableau §2 du skill.
4. **BukkitSink**, pour vérifier qu'il compile : le compiler contre `~/.m2/repository/io/papermc/paper/paper-api/26.3.build.218-stable/paper-api-26.3.build.218-stable.jar`. Il faut aussi les jars d'Adventure (`net/kyori/*`) et `org/jetbrains/annotations` sur le classpath, avec `-proc:none`.

## Démo (16/11/2026)

`Demo.main` construit une maison en L dans un `RecordingSink` :

- deux corps, l'un à 2 étages (11 × 7, toit à pignons est-ouest), l'autre à 1 étage (7 × 7, pignons nord-sud) ;
- un toit composé et une cheminée ;
- un terrain de 48 × 48 avec un étang, une plage, un chemin, deux arbres, un lampadaire et de la végétation.

Elle écrit `out/demo.schem`, lance `analyze.py demo.schem 4`, puis vérifie les cibles. Résultats :

| Mesure | Démo | Cible |
|---|---|---|
| Détail sur le bâti visible | 38,7 % | ≥ 15 % |
| Mélange local (total) | 1,06 % | < 15 % |
| Mélange local (bâti seul, `Check`) | 13,7 % | < 15 % |
| Pan coplanaire max | 3 | ≤ 7 |
| Types de blocs | 43 (29 à 0,1 % ou plus) | |
| Marches de 1 | 18,7 % | 12-30 % |
| Ressauts de 2-4 | 0,9 % | 4-13 % (terrain doux ; monter `amp` ou ajouter `ridges`) |
| Végétation visible | 8,0 % | |

Les noms de blocs utilisés par défaut (53) ont été vérifiés contre l'enum `Material` de paper-api 26.3.build.218 (javap). `BukkitSink` compile contre ce jar. **Rien n'a été posé sur un serveur**, et le rendu en jeu n'est pas vérifié, notamment :

- l'orientation des volets ;
- le sens des portes ;
- l'aspect des vitres posées en P-2 avec leurs connexions forcées.
