package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.Mat;
import fr.eemicraft.paintball.build.Palette;
import fr.eemicraft.paintball.build.RoofStyle;
import fr.eemicraft.paintball.build.Sink;
import fr.eemicraft.paintball.build.Style;
import fr.eemicraft.paintball.build.Tree;

/**
 * Petite map « Terrain de tournoi » (1.6.0) : un terrain de speedball de competition (facon NXL / Millennium Series),
 * dans le style de la toute premiere map du plugin : gazon synthetique vert raye, lignes blanches, structures
 * gonflables arrondies en laine aux couleurs des equipes (rouge / blanc a l'ouest, bleu / blanc a l'est), obstacles du
 * milieu neutres (jaune / noir), filet noir sur poteaux metalliques, panneaux de sponsors au pied du filet.
 *
 * <p>Plan de jeu en miroir x / -x (memes obstacles, memes hauteurs pour les deux equipes), comme sur un vrai terrain :
 * le cote serpent (snake) est le long de la ligne sud (z &gt; 0), le cote dorito le long de la ligne nord (z &lt; 0).
 * Par moitie : serpent bas (2) qui longe la ligne de cote avec son coin (snake corner) et une poutre (snake beam),
 * trois doritos (D1, D2, D-wire), temple, Maya (+4), gateaux (cakes), boites de conserve (cans), Mini-A et Big-A
 * (tentes en A), arbres (5), brique, bunker de depart (home) ; au centre un grand X neutre (repere), le Snake 50 et le
 * Dorito 50 neutres, deux « Gods » neutres. Terrain plat : sur un vrai terrain on rampe derriere le serpent ; les
 * niveaux viennent des hauteurs des gonflables. Hors filet : cabanes d'equipe, stands (pits) differents par equipe,
 * arche gonflable, drapeaux de sponsor, gradins et projecteurs communs.
 */
final class Tournoi extends Field {
    /** Couleurs d'un gonflable : corps, bande (couture), accent (sommet), tapis du dessus. */
    private record T(String main, String band, String accent, String cap) {
    }

    private static final T RED = new T("minecraft:red_wool", "minecraft:white_wool", "minecraft:pink_wool",
        "minecraft:red_carpet");
    private static final T BLUE = new T("minecraft:blue_wool", "minecraft:white_wool", "minecraft:light_blue_wool",
        "minecraft:blue_carpet");
    private static final T NEUTRAL = new T("minecraft:yellow_wool", "minecraft:black_wool", "minecraft:white_wool",
        "minecraft:yellow_carpet");

    private interface Hf {
        int h(int dx, int dz);
    }

    /** Role du bloc : 0 corps, 1 bande, 2 accent. */
    private interface Bf {
        int role(int dx, int dz, int y, int h);
    }

    /** Couture blanche au 2e rang des gonflables de 3 et plus, sommet des plus hauts en accent. */
    private static final Bf SEAM = (dx, dz, y, h) -> h >= 3 && y == 2 ? 1 : h >= 4 && y == h ? 2 : 0;

    Tournoi(Sink s) {
        super(s, 20261121L);
    }

    private static T team(int side) {
        return side < 0 ? RED : BLUE;
    }

    private static String mat(T t, int role) {
        return role == 1 ? t.band() : role == 2 ? t.accent() : t.main();
    }

    // ------------------------------------------------------------------ gabarit (terrain plat)

    @Override
    void heights() {
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                hm[x - GX0][z - GZ0] = FY;
                lock[x - GX0][z - GZ0] = true;
            }
        }
    }

    @Override
    double raw(int x, int z) {
        return 0;
    }

    @Override
    void pads() {
    }

    // aucun chemin sur le gazon : un segment hors du terrain (jamais utilise)
    @Override
    int[][] pathNorth() {
        return new int[][]{{0, -30}, {1, -30}};
    }

    @Override
    int[][] pathSouth() {
        return new int[][]{{0, 30}, {1, 30}};
    }

    /** Gazon synthetique : bandes de tonte de 4 (en miroir), lignes de cote, ligne mediane, lignes des 10. */
    @Override
    String fieldTop(int x, int z, int h) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        if (az == 20) {
            return "minecraft:white_concrete";                       // ligne de cote
        }
        if (az > 20) {
            return "minecraft:green_terracotta";                     // hors limites, au pied du filet
        }
        if (x == 0) {
            return "minecraft:white_concrete";                       // ligne mediane (la « 50 »)
        }
        if (ax == 12 && Math.floorMod(z, 4) < 2) {
            return "minecraft:white_concrete";                       // ligne des 10, en tirets
        }
        if (az == 19 && ax % 5 == 0 && ax <= 30) {
            return "minecraft:white_concrete";                       // reperes tous les 5 le long de la ligne de cote
        }
        if (ax == 24 && az <= 19) {
            return "minecraft:white_concrete";                       // ligne de depart
        }
        return Math.floorMod(ax + 2, 8) < 4 ? "minecraft:green_concrete" : "minecraft:lime_terracotta";
    }

    @Override
    String pathTop(int x, int z, double t2) {
        return fieldTop(x, z, FY);
    }

    @Override
    String baseFloor(int x, int z, double t2) {
        return Math.floorMod(Math.abs(x) + 2, 8) < 4 ? "minecraft:green_concrete" : "minecraft:lime_terracotta";
    }

    @Override
    String cabinFloor(int x, int z) {
        return "minecraft:polished_andesite";
    }

    @Override
    String outsideTop(int x, int z, double t2, boolean walk) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        if (walk) {
            return "minecraft:smooth_stone";
        }
        // allee de gravier de l'arche a la cabane, dalle des stands (pits)
        if (ax >= 38 && ax <= 44 && az <= 2) {
            return t2 > 0.4 ? "minecraft:coarse_dirt" : "minecraft:gravel";
        }
        boolean pit = ax >= 38 && ax <= 44 && (x < 0 ? z >= -17 && z <= -9 : z >= 9 && z <= 17);
        if (pit) {
            return "minecraft:gray_concrete";
        }
        double n = noise.derive(8).fbm2(x / 9.0, z / 9.0, 2, 0.5, 2.0);
        return n > 0.5 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
    }

    @Override
    String edgeMat(int x, int y, int z) {
        return "minecraft:white_concrete";
    }

    // ------------------------------------------------------------------ limites : panneaux de sponsors + filet

    @Override
    void edges() {
        // panneaux de sponsors (2 de haut : bande de couleur, haut blanc ou noir) au pied du filet, par segments de 6 couleurs differentes
        String[] boards = {"minecraft:white_concrete", "minecraft:yellow_concrete", "minecraft:black_concrete",
            "minecraft:white_concrete", "minecraft:orange_concrete", "minecraft:cyan_concrete", "minecraft:black_concrete"};
        for (int x = -34; x <= 34; x++) {
            for (int sd : new int[]{-1, 1}) {
                int seg = Math.floorDiv(x + 37 + (sd > 0 ? 3 : 0), 6);
                set(x, FY + 1, sd * 22, boards[Math.floorMod(seg, boards.length)]);
                set(x, FY + 2, sd * 22, boardTop(boards[Math.floorMod(seg, boards.length)]));
                bare[x - GX0][sd * 22 - GZ0] = true;
            }
        }
        for (int z = 10; z <= 21; z++) {
            for (int sd : new int[]{-1, 1}) {
                int seg = Math.floorDiv(z + (sd > 0 ? 2 : 5), 6);
                String b1 = boards[Math.floorMod(seg + 2, boards.length)], b2 = boards[Math.floorMod(seg + 4, boards.length)];
                set(sd * 34, FY + 1, z, b1);
                set(sd * 34, FY + 2, z, boardTop(b1));
                set(sd * 34, FY + 1, -z, b2);
                set(sd * 34, FY + 2, -z, boardTop(b2));
            }
        }
        // jambes de force devant les panneaux, tous les 6 (cassent le grand pan, comme les supports des vrais panneaux)
        for (int x = -33; x <= 33; x++) {
            for (int sd : new int[]{-1, 1}) {
                if (Math.floorMod(x + 37 + (sd > 0 ? 3 : 0), 6) == 0) {
                    set(x, FY + 1, sd * 21, "minecraft:polished_andesite");
                    set(x, FY + 2, sd * 21, "minecraft:polished_andesite_slab[type=bottom]");
                }
            }
        }
        for (int z = 11; z <= 20; z++) {
            for (int sd : new int[]{-1, 1}) {
                if (Math.floorMod(z + (sd > 0 ? 2 : 5), 6) == 0) {
                    for (int zz : new int[]{z, -z}) {
                        set(sd * 33, FY + 1, zz, "minecraft:polished_andesite");
                        set(sd * 33, FY + 2, zz, "minecraft:polished_andesite_slab[type=bottom]");
                    }
                }
            }
        }
        for (int x = -NX; x <= NX; x++) {
            for (int sd : new int[]{-1, 1}) {
                netColumn(x, sd * NZ, (x + NX) % 5 == 0 || Math.abs(x) == NX, "east=true,west=true");
            }
        }
        for (int z = -NZ + 1; z <= NZ - 1; z++) {
            if (Math.abs(z) <= 8) {
                continue;
            }
            for (int sd : new int[]{-1, 1}) {
                netColumn(sd * NX, z, (z + NZ) % 5 == 0, "north=true,south=true");
            }
        }
        // barrieres invisibles UNIQUEMENT derriere le filet
        for (int x = -BX; x <= BX; x++) {
            fill(x, FY + 1, -BZ, x, FY + 20, -BZ, "minecraft:barrier");
            fill(x, FY + 1, BZ, x, FY + 20, BZ, "minecraft:barrier");
        }
        for (int z = -BZ + 1; z <= BZ - 1; z++) {
            if (Math.abs(z) <= 9) {
                continue;
            }
            fill(-BX, FY + 1, z, -BX, FY + 20, z, "minecraft:barrier");
            fill(BX, FY + 1, z, BX, FY + 20, z, "minecraft:barrier");
        }
    }

    /** Haut du panneau : blanc sur un panneau sombre ou colore, noir sur un panneau blanc. */
    private static String boardTop(String panel) {
        return panel.equals("minecraft:white_concrete") ? "minecraft:black_concrete" : "minecraft:white_concrete";
    }

    @Override
    String netPost() {
        return "minecraft:polished_andesite";
    }

    @Override
    String netCap() {
        return "minecraft:polished_andesite_slab[type=bottom]";
    }

    @Override
    String netTop() {
        return "minecraft:iron_bars";
    }

    // ------------------------------------------------------------------ cabane d'equipe

    @Override
    void base(int side) {
        String team = side < 0 ? "red" : "blue";
        Palette wall = Palette.of(side < 0 ? 741 : 743, "minecraft:" + team + "_concrete", 70,
            "minecraft:" + team + "_terracotta", 30).period(8).amp(0.2);
        Style st = new Style().wall(wall).plinth(Mat.of("minecraft:polished_andesite"))
            .plinthStairs("minecraft:polished_andesite_stairs").post("minecraft:quartz_pillar")
            .sill("minecraft:polished_andesite_stairs").lintel("minecraft:smooth_quartz_slab").band(null)
            .cornice("minecraft:quartz_stairs").door("minecraft:dark_oak_door").spacing(4).storey(4)
            .shutters(0).blankBays(0.2).seed(side < 0 ? 75 : 79);
        RoofStyle rs = new RoofStyle()
            .stairs(Palette.of(73, "minecraft:deepslate_tile_stairs", 70, "minecraft:cobbled_deepslate_stairs", 30).period(7))
            .ridge("minecraft:deepslate_tile_slab").body(Mat.of("minecraft:deepslate_tiles"))
            .gable(wall).gablePost("minecraft:quartz_pillar").overhang(1).spacing(4);
        cabin(side, st, rs);
        // boite de depart : plots blanc / couleur de l'equipe aux deux bouts de la ligne d'apparition
        for (int z : new int[]{-7, 7}) {
            set(side * 29, FY + 1, z, "minecraft:" + team + "_carpet");
        }
        set(side * 26, FY + 1, -8, "minecraft:white_carpet");
        set(side * 26, FY + 1, 8, "minecraft:white_carpet");
    }

    // ------------------------------------------------------------------ gonflables (formes)

    /** Forme generale : colonne de laine de hauteur hf(dx, dz) autour de (cx, cz), miroir par side. */
    private void blob(int side, int cx, int cz, int rad, Hf hf, Bf bf, T t, boolean cap) {
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                int h = hf.h(dx, dz);
                if (h <= 0) {
                    continue;
                }
                int X = side * (cx + dx);
                int Z = cz + dz;
                int y0 = g(X, Z);
                for (int y = 1; y <= h; y++) {
                    set(X, y0 + y, Z, mat(t, bf.role(dx, dz, y, h)));
                }
                if (cap) {
                    set(X, y0 + h + 1, Z, t.cap());
                }
            }
        }
    }

    /** Boite de conserve (can) : cylindre de rayon 1 (croix + coins), h blocs, coutures blanches. */
    private void can(int side, int cx, int cz, int h, T t) {
        blob(side, cx, cz, 1, (dx, dz) -> dx * dx + dz * dz <= 1 ? h : 0,
            (dx, dz, y, hh) -> (y == 2 || y == 4) && hh >= 3 ? 1 : y == hh && hh >= 5 ? 2 : 0, t, false);
    }

    /** Arbre (tree) : grande can de 5, sommet en accent. */
    private void tree(int side, int cx, int cz, T t) {
        blob(side, cx, cz, 1, (dx, dz) -> dx * dx + dz * dz <= 2 ? (dx != 0 && dz != 0 ? 4 : 5) : 0,
            (dx, dz, y, h) -> y == 2 || y == 4 ? 1 : y == 5 ? 2 : 0, t, false);
    }

    /** Gateau (cake) : cylindre bas et large (r ~2), dessus blanc sur le pourtour, tapis au centre. */
    private void cake(int side, int cx, int cz, T t) {
        blob(side, cx, cz, 2, (dx, dz) -> {
            int d = dx * dx + dz * dz;
            return d <= 2 ? 2 : d <= 5 ? 2 : 0;
        }, (dx, dz, y, h) -> y == 2 && dx * dx + dz * dz >= 4 ? 1 : 0, t, false);
        set(side * cx, g(side * cx, cz) + 3, cz, t.cap());
    }

    /** Temple : 5 de long, 3 de large, dos rond (3 au milieu, 2 sur les cotes et aux bouts). */
    private void temple(int side, int cx, int cz, boolean alongX, T t) {
        blob(side, cx, cz, 2, (dx, dz) -> {
            int a = alongX ? dx : dz;
            int c = alongX ? dz : dx;
            if (Math.abs(c) > 1) {
                return 0;
            }
            return (c == 0 ? 3 : 2) - (Math.abs(a) == 2 ? 1 : 0);
        }, SEAM, t, false);
    }

    /** Maya : pyramide gonflable a gradins, 5 x 5, 2 / 3 / 4. */
    private void maya(int side, int cx, int cz, T t) {
        blob(side, cx, cz, 2, (dx, dz) -> {
            int m = Math.max(Math.abs(dx), Math.abs(dz));
            if (m == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                return 1;                                            // coins arrondis
            }
            return m == 2 ? 2 : m == 1 ? 3 : 4;
        }, (dx, dz, y, h) -> y == h && h < 4 ? 1 : y == 4 ? 2 : 0, t, false);
    }

    /** Dorito : triangle debout, base vers le centre du terrain, pointe vers tipZ ; 3 de haut a la base, 1 a la pointe. */
    private void dorito(int side, int cx, int cz, int tipZ, int size, T t) {
        int depth = size + 3;                 // rangs de la base a la pointe
        int c = (depth - 1) / 2;
        int hmax = size >= 3 ? 4 : 3;
        blob(side, cx, cz, Math.max(size + 1, c + 1), (dx, dz) -> {
            int v = tipZ < 0 ? c - dz : dz + c;      // 0 = base (cote centre du terrain), depth - 1 = pointe
            if (v < 0 || v >= depth) {
                return 0;
            }
            double half = (size + 0.5) * (1.0 - v / (double) depth);
            if (Math.abs(dx) > half) {
                return 0;
            }
            return Math.max(1, hmax - v * (hmax - 1) / (depth - 1));
        }, SEAM, t, false);
    }

    /** Tente en A (Mini-A / Big-A) : profil en travers, longueur len. */
    private void aFrame(int side, int cx, int cz, boolean alongX, int len, int[] profile, T t) {
        int half = profile.length / 2;
        int rad = Math.max(half, len / 2 + 1);
        blob(side, cx, cz, rad, (dx, dz) -> {
            int a = alongX ? dx : dz;
            int c = alongX ? dz : dx;
            if (a < -len / 2 || a > (len - 1) / 2 || Math.abs(c) > half) {
                return 0;
            }
            int h = profile[c + half];
            boolean end = a == -len / 2 || a == (len - 1) / 2;
            return end && h > 1 ? h - 1 : h;
        }, (dx, dz, y, h) -> {
            int a = alongX ? dx : dz;
            return Math.floorMod(a, 3) == 0 && y == h ? 1 : 0;
        }, t, false);
    }

    /** Brique : boite w x l, h de haut, bande blanche en haut. */
    private void brick(int side, int x1, int z1, int w, int l, int h, T t) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                int X = side * (x1 + dx);
                int Z = z1 + dz;
                int y0 = g(X, Z);
                for (int y = 1; y <= h; y++) {
                    set(X, y0 + y, Z, y == h ? t.band() : t.main());
                }
            }
        }
    }

    /** Serpent : boudin bas le long d'une ligne brisee ; 2 au milieu, 1 sur les flancs ; bande tous les 5. */
    private void snake(int side, int[][] pts, int hc, T t) {
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (int[] p : pts) {
            x0 = Math.min(x0, p[0] - 2);
            x1 = Math.max(x1, p[0] + 2);
            z0 = Math.min(z0, p[1] - 2);
            z1 = Math.max(z1, p[1] + 2);
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double best = 1e9, sAt = 0, acc = 0;
                for (int i = 0; i + 1 < pts.length; i++) {
                    double ax = pts[i][0], az = pts[i][1], bx = pts[i + 1][0], bz = pts[i + 1][1];
                    double vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz, len = Math.sqrt(l2);
                    double u = clamp(((x - ax) * vx + (z - az) * vz) / l2, 0, 1);
                    double d = Math.hypot(x - (ax + u * vx), z - (az + u * vz));
                    if (d < best) {
                        best = d;
                        sAt = acc + u * len;
                    }
                    acc += len;
                }
                int h = best <= 0.75 ? hc : best <= 1.45 ? hc - 1 : 0;
                if (h <= 0) {
                    continue;
                }
                int X = side * x;
                int y0 = g(X, z);
                boolean band = Math.floorMod((int) Math.round(sAt), 5) == 0;
                for (int y = 1; y <= h; y++) {
                    set(X, y0 + y, z, band ? t.band() : y == h && h == hc && hc >= 3 ? t.accent() : t.main());
                }
            }
        }
    }

    // ------------------------------------------------------------------ obstacles d'une moitie (miroir x)

    @Override
    void obstacles(int side) {
        T t = team(side);
        // ---- cote serpent (sud, z > 0)
        snake(side, new int[][]{{5, 16}, {9, 17}, {13, 16}, {17, 17}, {19, 16}, {20, 13}}, 2, t);   // serpent + coin
        snake(side, new int[][]{{23, 11}, {23, 14}}, 2, t);                                            // snake beam
        can(side, 20, 6, 3, t);
        cake(side, 12, 10, t);
        aFrame(side, 6, 9, true, 4, new int[]{1, 2, 1}, t);                                           // Mini-A
        tree(side, 17, 7, t);
        // ---- cote dorito (nord, z < 0)
        dorito(side, 20, -15, -1, 2, t);                                                              // D1
        dorito(side, 14, -16, -1, 2, t);                                                              // D2
        dorito(side, 8, -16, -1, 2, t);                                                               // D-wire
        temple(side, 11, -8, true, t);
        aFrame(side, 18, -9, false, 5, new int[]{1, 2, 3, 2, 1}, t);                                  // Big-A
        brick(side, 4, -13, 2, 2, 2, t);
        cake(side, 20, -4, t);
        tree(side, 23, -12, t);
        can(side, 7, -4, 3, t);
        // ---- milieu
        maya(side, 14, 1, t);
        temple(side, 22, 0, false, t);                                                                // home
        brick(side, 9, -1, 1, 3, 2, t);                                                               // brique du couloir central
        can(side, 5, 4, 2, t);                                                                        // petites cans pres du X
        can(side, 4, 12, 2, t);
        // coins derriere la ligne de depart
        cake(side, 28, 15, t);
        can(side, 28, -14, 3, t);
    }

    // ------------------------------------------------------------------ centre neutre

    @Override
    void center() {
        // grand X (repere central) : deux bras en diagonale, 4 au centre, arrondi sur les bords
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int m = Math.min(Math.abs(dx - dz), Math.abs(dx + dz));
                int r = Math.max(Math.abs(dx), Math.abs(dz));
                if (m > 1 || r > 4) {
                    continue;
                }
                int h = r <= 1 ? 4 : r <= 3 ? 3 : 2;
                if (m == 1) {
                    h--;
                }
                for (int y = 1; y <= h; y++) {
                    String b = y == 2 ? NEUTRAL.band() : (r <= 1 && y == h) ? NEUTRAL.accent() : NEUTRAL.main();
                    set(dx, FY + y, dz, b);
                }
            }
        }
        // ballon d'arbitre au-dessus du X (repere vu de loin) : boule blanche et noire sur un mat
        fill(0, FY + 5, 0, 0, FY + 7, 0, "minecraft:iron_chain[axis=y]");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= 2) {
                        set(dx, FY + 9 + dy, dz, (dx + dy + dz) % 2 == 0 ? "minecraft:white_wool" : "minecraft:black_wool");
                    }
                }
            }
        }
        temple(1, 0, 16, true, NEUTRAL);                 // Snake 50
        dorito(1, 0, -16, -1, 3, NEUTRAL);               // Dorito 50
        can(1, 0, 9, 3, NEUTRAL);                        // Gods
        can(1, 0, -9, 3, NEUTRAL);
    }

    // ------------------------------------------------------------------ eclairage et hors terrain

    @Override
    void lamps(int side) {
        for (int x : new int[]{8, 20}) {
            for (int zs : new int[]{-1, 1}) {
                lamppost(side * x, zs * 24, zs);
            }
        }
    }

    @Override
    void lamppost(int x, int z, int zs) {
        set(x, FY + 1, z, "minecraft:andesite_wall");
        fill(x, FY + 2, z, x, FY + 3, z, "minecraft:iron_bars");
        set(x, FY + 4, z, "minecraft:lantern");
    }

    @Override
    String[] standMats() {
        return new String[]{"minecraft:quartz_stairs", "minecraft:smooth_quartz", "minecraft:smooth_quartz",
            "minecraft:quartz_pillar", "minecraft:smooth_quartz_slab"};
    }

    @Override
    void outside() {
        int[][] list = {{-31, 27, 7}, {33, 29, 8}, {38, -31, 7}, {-29, -30, 6}, {-37, 31, 6}, {30, -27, 6},
            {42, 31, 6}, {-40, -30, 6}, {41, -22, 6}, {-42, 24, 7}};
        for (int i = 0; i < list.length; i++) {
            int[] t = list[i];
            String log = i % 3 == 0 ? "minecraft:birch_log" : "minecraft:oak_log";
            String lv2 = i % 3 == 0 ? "minecraft:birch_leaves" : "minecraft:azalea_leaves";
            new Tree(log, "minecraft:oak_leaves", lv2).height(t[2]).crown(2.6).place(s, t[0], g(t[0], t[1]) + 1, t[1], 600 + i);
        }
        arch(-1);
        arch(1);
        pit(-1);
        pit(1);
    }

    /** Arche gonflable aux couleurs de l'equipe sur l'allee derriere la cabane (bandes blanches). */
    private void arch(int side) {
        T t = team(side);
        int x = side * 41;
        for (int z = -4; z <= 4; z++) {
            int az = Math.abs(z);
            int top = az >= 3 ? 6 : az == 2 ? 7 : 7;
            int bottom = az >= 3 ? 1 : az == 2 ? 6 : 7;
            for (int y = bottom; y <= top; y++) {
                boolean band = (az >= 3 && (y == 2 || y == 4)) || (az < 3 && z == 0);
                set(x, FY + y, z, band ? t.band() : t.main());
                set(x + side, FY + y, z, band ? t.band() : t.main());
            }
        }
        // cordes d'ancrage
        for (int z : new int[]{-5, 5}) {
            set(x, FY + 1, z, "minecraft:polished_andesite_slab[type=bottom]");
        }
    }

    /** Stand d'equipe (pits) : tonnelle, table de chargement, bouteilles d'air, banc, drapeaux. Different par equipe. */
    private void pit(int side) {
        String team = side < 0 ? "red" : "blue";
        int zc = side < 0 ? -13 : 13;
        for (int x : new int[]{39, 43}) {
            for (int z : new int[]{zc - 3, zc + 3}) {
                fill(side * x, FY + 1, z, side * x, FY + 3, z, "minecraft:iron_bars");
            }
        }
        for (int x = 38; x <= 44; x++) {
            for (int z = zc - 3; z <= zc + 3; z++) {
                boolean ridge = x == 41;
                boolean rim = x == 38 || x == 44 || Math.abs(z - zc) == 3;
                set(side * x, FY + 4 + (ridge ? 1 : 0), z, rim && !ridge ? "minecraft:white_wool" : "minecraft:" + team + "_wool");
            }
        }
        // table de chargement (tremies = loaders) et bouteilles d'air
        for (int z = zc - 1; z <= zc + 1; z++) {
            set(side * 40, FY + 1, z, "minecraft:smooth_quartz_slab[type=top]");
        }
        set(side * 40, FY + 2, zc - 1, "minecraft:hopper[facing=down,enabled=true]");
        set(side * 40, FY + 2, zc, "minecraft:lightning_rod[facing=up]"); // bouteille d'air debout entre les tremies
        set(side * 40, FY + 2, zc + 1, "minecraft:hopper[facing=down,enabled=true]");
        // banc face au terrain
        String face = side < 0 ? "east" : "west";
        for (int z = zc - 2; z <= zc + 2; z++) {
            set(side * 43, FY + 1, z, "minecraft:quartz_stairs[facing=" + face + ",half=bottom]");
        }
        // lanterne pendue au faitage de la tonnelle (FY + 5) par une chaine
        set(side * 41, FY + 4, zc, "minecraft:iron_chain[axis=y]");
        set(side * 41, FY + 3, zc, "minecraft:lantern[hanging=true]");
        // decor different : chariot de bouteilles (rouge) / ecran de scores et glaciere (bleu)
        if (side < 0) {
            for (int z = zc + 4; z <= zc + 5; z++) {
                set(side * 42, FY + 1, z, "minecraft:iron_block");
                set(side * 42, FY + 2, z, "minecraft:lightning_rod[facing=up]");
            }
        } else {
            fill(side * 44, FY + 1, zc - 5, side * 44, FY + 3, zc - 5, "minecraft:iron_bars");
            set(side * 44, FY + 4, zc - 5, "minecraft:black_concrete");
            set(side * 44, FY + 4, zc - 6, "minecraft:black_concrete");
            set(side * 44, FY + 4, zc - 4, "minecraft:black_concrete");
            set(side * 42, FY + 1, zc - 5, "minecraft:white_concrete");
            set(side * 42, FY + 2, zc - 5, "minecraft:light_blue_carpet");
        }
        // drapeaux plumes de sponsor (mat + voile de laine), deux par equipe a des endroits differents
        int[][] flags = side < 0 ? new int[][]{{38, -20}, {44, 6}} : new int[][]{{44, 20}, {44, -8}}; // (38, -6) etait dans le debord du toit de la cabane
        String[] cloth = side < 0 ? new String[]{"minecraft:yellow_wool", "minecraft:red_wool"}
            : new String[]{"minecraft:blue_wool", "minecraft:orange_wool"};
        for (int i = 0; i < flags.length; i++) {
            int fx0 = side * flags[i][0], fz = flags[i][1];
            fill(fx0, FY + 1, fz, fx0, FY + 6, fz, "minecraft:iron_bars");
            int cz = fz + (fz < 0 ? 1 : -1);
            for (int y = FY + 3; y <= FY + 6; y++) {
                set(fx0, y, cz, y == FY + 6 ? "minecraft:white_wool" : cloth[i]);
            }
        }
    }

    @Override
    void vegetation() {
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                int h = g(x, z);
                if (!s.get(x, h, z).startsWith("minecraft:grass_block") || !s.isAir(x, h + 1, z)) {
                    continue;
                }
                double v = noise.derive(7).noise2(x / 5.0, z / 5.0) + 0.35 * (hash(91, x, z) - 0.5);
                if (v < 0.3) {
                    continue;
                }
                double r = hash(92, x, z);
                if (r < 0.15 && s.isAir(x, h + 2, z)) {
                    set(x, h + 1, z, "minecraft:tall_grass[half=lower]");
                    set(x, h + 2, z, "minecraft:tall_grass[half=upper]");
                } else if (v > 0.7 && r < 0.4) {
                    set(x, h + 1, z, r < 0.2 ? "minecraft:dandelion" : "minecraft:oxeye_daisy");
                } else {
                    set(x, h + 1, z, "minecraft:short_grass");
                }
            }
        }
    }
}
