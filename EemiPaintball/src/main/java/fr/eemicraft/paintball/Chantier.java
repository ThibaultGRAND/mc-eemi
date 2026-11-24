package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.Mat;
import fr.eemicraft.paintball.build.Noise;
import fr.eemicraft.paintball.build.Palette;
import fr.eemicraft.paintball.build.RoofStyle;
import fr.eemicraft.paintball.build.Sink;
import fr.eemicraft.paintball.build.Style;
import fr.eemicraft.paintball.build.Tree;

/**
 * Petite map « Chantier » (1.4.0) : terrain de gravier et de terre battue, grue a tour au centre (plate-forme a +5
 * sous le mat, abri dessous), conteneurs (toit a +5), immeuble en construction avec echafaudage (dalle a +5),
 * tas de gravier / sable et remblai (+3), buses en beton qu'on traverse, palettes de briques, parpaings, bobines,
 * baraques de chantier aux couleurs des equipes, palissade de chantier + filet en bordure.
 */
final class Chantier extends Field {
    private final Palette hoard = Palette.of(71, "minecraft:white_concrete", 60, "minecraft:white_terracotta", 25,
        "minecraft:light_gray_concrete", 15).period(7);

    Chantier(Sink s) {
        super(s, 20261117L);
    }

    // ------------------------------------------------------------------ relief et sols

    @Override
    double raw(int x, int z) {
        int ax = Math.abs(x);
        double n = noise.derive(1).fbm2((x + 300) / 12.0, (z - 120) / 12.0, 3, 0.5, 2.0);
        double v = Math.max(0, n - 0.1) * 1.7;
        v = Math.max(v, bump(ax, z, 21, -15, 4.2, 3.0, 3.0));   // tas de gravier / sable
        v = Math.max(v, bump(ax, z, 12, 12, 4.6, 4.2, 3.0));    // remblai sud (+3)
        v = Math.max(v, bump(ax, z, 5, 9, 3.4, 2.4, 1.0));
        v = Math.max(v, bump(ax, z, 27, -15, 2.4, 2.0, 1.5));   // petit tas pres de la base
        return v;
    }

    @Override
    void pads() {
        pad(7, -16, 17, -11, FY, true);       // conteneur + palettes
        pad(17, 10, 24, 17, FY, true);        // immeuble en construction
        pad(12, -3, 20, 2, FY, true);         // buses
        pad(8, -2, 11, 2, FY, true);          // bobine
        pad(-2, 9, 2, 13, FY, false);         // bobine centrale sud
        pad(-4, -13, 4, -10, FY, false);      // parpaings centraux
    }

    @Override
    int[][] pathNorth() {
        return new int[][]{{26, -1}, {21, -4}, {17, -9}, {11, -10}, {6, -9}, {3, -7}, {0, -7}};
    }

    @Override
    int[][] pathSouth() {
        return new int[][]{{26, 1}, {21, 4}, {17, 7}, {12, 7}, {7, 7}, {3, 7}, {0, 7}};
    }

    @Override
    String fieldTop(int x, int z, int h) {
        Noise sn = noise.derive(3);
        int ax = Math.abs(x);
        double wear = sn.fbm2(x / 11.0 + 40, z / 11.0, 2, 0.45, 2.0);
        double alt = noise.derive(6).fbm2(x / 11.0 - 70, z / 11.0, 2, 0.45, 2.0);
        if (h >= FY + 2 && z < 0 && ax >= 16) {
            // tas : sable a l'ouest, gravier a l'est (decor different par equipe)
            return x < 0 ? (alt > 0.35 ? "minecraft:gravel" : "minecraft:sand") : (alt > 0.4 ? "minecraft:sand" : "minecraft:gravel");
        }
        if (h >= FY + 2) {
            return alt > 0.3 ? "minecraft:rooted_dirt" : "minecraft:coarse_dirt";
        }
        double edge = noise.derive(4).noise2(x / 7.0 + 15, z / 7.0) + (Math.max(Math.abs(z), ax - 12) - 18) * 0.3;
        if (edge > 0.1 && (Math.abs(z) >= 16 || ax >= 30)) {
            return "minecraft:grass_block";
        }
        if (wear > 0.5) {
            return "minecraft:mud";
        }
        if (wear > 0.25) {
            return "minecraft:packed_mud";
        }
        if (alt > 0.32) {
            return "minecraft:gravel";
        }
        if (wear < -0.5) {
            return "minecraft:dirt";
        }
        return "minecraft:coarse_dirt";
    }

    @Override
    String pathTop(int x, int z, double t2) {
        return t2 > 0.5 ? "minecraft:mud" : "minecraft:dirt_path";
    }

    @Override
    String baseFloor(int x, int z, double t2) {
        return t2 > 0.3 ? "minecraft:andesite" : "minecraft:polished_andesite";
    }

    @Override
    String cabinFloor(int x, int z) {
        return "minecraft:smooth_stone";
    }

    @Override
    String outsideTop(int x, int z, double t2, boolean walk) {
        if (walk) {
            return t2 > 0.3 ? "minecraft:gravel" : "minecraft:coarse_dirt";
        }
        double n = noise.derive(8).fbm2(x / 9.0, z / 9.0, 2, 0.5, 2.0);
        return n > 0.35 ? "minecraft:coarse_dirt" : n < -0.45 ? "minecraft:podzol" : "minecraft:grass_block";
    }

    // ------------------------------------------------------------------ palissade de chantier

    @Override
    String edgeMat(int x, int y, int z) {
        return hoard.at(x, y, z);
    }

    @Override
    String edgeCap(int x, int y, int z) {
        return "minecraft:smooth_stone_slab[type=bottom]";
    }

    @Override
    void edgeExtra(int x, int z, int h, int top) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        // poteaux en saillie cote terrain tous les 6 (cassent le grand pan), panneau vert au milieu de chaque travee
        if (az == 21 && Math.floorMod(x, 6) == 0 && ax <= 32) {
            int zi = z - Integer.signum(z);
            int yi = g(x, zi);
            fill(x, yi + 1, zi, x, top, zi, "minecraft:stripped_spruce_log[axis=y]");
        } else if (az == 21 && Math.floorMod(x, 6) == 3 && ax <= 30) {
            set(x, top - 1, z, "minecraft:green_terracotta");
        }
        if (ax == 33 && az >= 10 && Math.floorMod(z, 6) == 0) {
            int xi = x - Integer.signum(x);
            int yi = g(xi, z);
            fill(xi, yi + 1, z, xi, top, z, "minecraft:stripped_spruce_log[axis=y]");
        }
    }

    @Override
    String netPane() {
        return "minecraft:iron_bars";
    }

    @Override
    String netPost() {
        return "minecraft:stripped_spruce_log";
    }

    @Override
    String netCap() {
        return "minecraft:smooth_stone_slab[type=bottom]";
    }

    @Override
    String netTop() {
        return "minecraft:iron_bars";
    }

    // ------------------------------------------------------------------ bases

    @Override
    void base(int side) {
        String team = side < 0 ? "red" : "blue";
        Palette wall = Palette.of(side < 0 ? 141 : 143, "minecraft:" + team + "_concrete", 72,
            "minecraft:" + team + "_terracotta", 28).period(8).amp(0.2);
        Style st = new Style().wall(wall).plinth(Mat.of("minecraft:smooth_stone"))
            .plinthStairs("minecraft:stone_stairs").post("minecraft:stripped_birch_log")
            .sill("minecraft:stone_stairs").lintel("minecraft:smooth_stone_slab").band(null)
            .cornice("minecraft:polished_andesite_stairs").door("minecraft:birch_door").floor(Mat.of("minecraft:birch_planks"))
            .shutter(null).spacing(4).storey(4).blankBays(0.25).seed(side < 0 ? 15 : 19);
        RoofStyle rs = new RoofStyle()
            .stairs(Palette.of(13, "minecraft:waxed_exposed_cut_copper_stairs", 70, "minecraft:waxed_weathered_cut_copper_stairs", 30).period(7))
            .ridge("minecraft:waxed_exposed_cut_copper_slab").body(Mat.of("minecraft:waxed_exposed_cut_copper"))
            .gable(wall).gablePost("minecraft:stripped_birch_log").overhang(1).spacing(4);
        cabin(side, st, rs);
        // casques de chantier sur un rack devant la porte (decor : un seul cote)
        if (side > 0) {
            set(side * 30, FY + 1, -7, "minecraft:spruce_fence");
            set(side * 30, FY + 2, -7, "minecraft:yellow_carpet");
        } else {
            set(side * 30, FY + 1, 7, "minecraft:barrel[facing=up]");
        }
        // ecrans devant l'apparition : barriere en beton + panneau de palissade a la couleur de l'equipe
        for (int[] r : new int[][]{{-9, -5}, {5, 9}}) {
            int x = side * 24;
            for (int z = r[0]; z <= r[1]; z++) {
                boolean post = z == r[0] || z == r[1];
                if (post) {
                    fill(x, FY + 1, z, x, FY + 3, z, "minecraft:stripped_birch_log[axis=y]");
                    set(x, FY + 4, z, "minecraft:smooth_stone_slab[type=bottom]");
                } else {
                    set(x, FY + 1, z, "minecraft:smooth_stone");
                    set(x, FY + 2, z, "minecraft:white_concrete");
                    set(x, FY + 3, z, "minecraft:" + team + "_concrete");
                }
            }
            set(side * 25, FY + 1, (r[0] + r[1]) / 2, "minecraft:stone_stairs[facing=" + fx("west", side) + "]");
        }
    }

    // ------------------------------------------------------------------ couverts

    @Override
    void obstacles(int side) {
        long sd = side < 0 ? 301 : 302;
        container(side, 8, -15, 15, -12, side < 0 ? "minecraft:yellow_terracotta" : "minecraft:green_terracotta");
        // palettes de briques en escalier vers le toit du conteneur
        bricks(side, 17, -13, 1, sd);
        bricks(side, 16, -13, 2, sd);
        bricks(side, 16, -14, 3, sd);
        bricks(side, 16, -15, 4, sd);
        // barrieres en beton sur le tas de gravier
        for (int x = 19; x <= 23; x++) {
            jersey(side, x, -14, x == 23 ? 2 : 1);
        }
        // nord, milieu
        bricks(side, 5, -6, 2, sd);
        bricks(side, 6, -6, 2, sd);
        bricks(side, 6, -7, 3, sd);
        stack(side, 10, -9, 2, "minecraft:polished_andesite");
        stack(side, 11, -9, 2, "minecraft:polished_andesite");
        stack(side, 10, -8, 2, "minecraft:polished_andesite");
        stack(side, 11, -8, hash(sd, 11, -8) < 0.5 ? 1 : 2, "minecraft:polished_andesite");
        bags(side, 19, -9, sd);
        bags(side, 19, -8, sd);
        bags(side, 20, -8, sd);
        drum(side, 14, -6, sd);
        drum(side, 15, -6, sd);
        crate(side, 15, -5, 2, sd);
        bricks(side, 3, -17, 2, sd);
        bricks(side, 4, -17, 2, sd);
        planks(side, 25, -15, 2);
        planks(side, 26, -15, 2);
        planks(side, 26, -14, 1);
        // couloir du milieu : bobine, buses en beton (on passe dedans), coffrages
        spool(side * 10, 0, "minecraft:spruce_planks", "minecraft:spruce_slab", "minecraft:stripped_oak_log[axis=y]");
        pipe(side, 14, 18, 0);
        stack(side, 21, -1, 1, "minecraft:spruce_planks");
        stack(side, 21, 1, 1, "minecraft:spruce_planks");
        stack(side, 21, 0, 2, "minecraft:spruce_planks");
        set(side * 21, g(side * 21, 0) + 3, 0, "minecraft:spruce_trapdoor[half=bottom,facing=north]");
        // sud, milieu
        crate(side, 5, 4, 2, sd);
        crate(side, 6, 4, 2, sd);
        crate(side, 5, 5, 1, sd);
        drum(side, 7, 4, sd);
        stack(side, 13, 4, 1, "minecraft:polished_andesite");
        stack(side, 14, 4, 2, "minecraft:polished_andesite");
        stack(side, 13, 5, 1, "minecraft:polished_andesite");
        for (int[] c : new int[][]{{18, 7}, {19, 7}, {20, 6}, {21, 6}, {22, 6}}) {
            jersey(side, c[0], c[1], hash(sd, c[0], c[1]) < 0.45 ? 2 : 1);
        }
        embankment(side, sd);
        frame(side);
        for (int x = 3; x <= 7; x++) {
            set(side * x, g(side * x, 16) + 1, 16, "minecraft:stripped_spruce_log[axis=x]");
        }
        for (int x = 4; x <= 6; x++) {
            set(side * x, g(side * x, 16) + 2, 16, "minecraft:stripped_spruce_log[axis=x]");
        }
        crate(side, 25, 14, 2, sd);
        toilet(side, 26, 15);
    }

    /** Palette de briques : palette (dalle haute) puis h - 1 rangs de briques, dessus en dalle de temps en temps. */
    private void bricks(int side, int x, int z, int h, long sd) {
        int X = side * x;
        int y0 = g(X, z);
        set(X, y0 + 1, z, "minecraft:spruce_slab[type=top]");
        for (int y = 2; y <= h; y++) {
            set(X, y0 + y, z, "minecraft:bricks");
        }
        if (h >= 2 && hash(sd, x, z) < 0.3) {
            set(X, y0 + h + 1, z, "minecraft:brick_slab[type=bottom]");
        }
    }

    private void crate(int side, int x, int z, int h, long sd) {
        int X = side * x;
        int y0 = g(X, z);
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, y == h && hash(sd, x, z * 7) < 0.35 ? "minecraft:barrel[facing=up]" : "minecraft:spruce_planks");
        }
        if (hash(sd, z, x) < 0.5) {
            set(X, y0 + h + 1, z, "minecraft:spruce_trapdoor[half=bottom,facing=north]");
        }
    }

    private void drum(int side, int x, int z, long sd) {
        int X = side * x;
        set(X, g(X, z) + 1, z, hash(sd, x, z) < 0.6 ? "minecraft:barrel[facing=up]" : "minecraft:cauldron");
    }

    /** Sacs de ciment : laine gris clair, tapis au-dessus une fois sur deux. */
    private void bags(int side, int x, int z, long sd) {
        int X = side * x;
        int y0 = g(X, z);
        set(X, y0 + 1, z, "minecraft:light_gray_wool");
        if (hash(sd, x, z) < 0.5) {
            set(X, y0 + 2, z, "minecraft:light_gray_carpet");
        }
    }

    /** Barriere en beton (type « jersey ») : 1 ou 2 de haut, dalle de beton lisse au sommet. */
    private void jersey(int side, int x, int z, int h) {
        int X = side * x;
        int y0 = g(X, z);
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, y == 1 ? "minecraft:smooth_stone" : "minecraft:polished_andesite");
        }
        set(X, y0 + h + 1, z, "minecraft:smooth_stone_slab[type=bottom]");
    }

    private void planks(int side, int x, int z, int h) {
        int X = side * x;
        int y0 = g(X, z);
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, "minecraft:stripped_spruce_log[axis=x]");
        }
    }

    /** Buse en beton couchee le long de x (section ronde 4x4, on la traverse debout : interieur 2 de large, 2 de haut). */
    private void pipe(int side, int x1, int x2, int c) {
        for (int x = x1; x <= x2; x++) {
            int X = side * x;
            boolean ring = x == x1 || x == x2;
            String body = ring ? "minecraft:polished_andesite" : "minecraft:light_gray_concrete";
            for (int z : new int[]{c - 1, c}) {
                set(X, FY + 1, z, body);
                set(X, FY + 4, z, body);
            }
            for (int y = FY + 2; y <= FY + 3; y++) {
                set(X, y, c - 2, body);
                set(X, y, c + 1, body);
            }
            // angles arrondis
            set(X, FY + 1, c - 2, "minecraft:polished_andesite_stairs[facing=south,half=bottom]");
            set(X, FY + 1, c + 1, "minecraft:polished_andesite_stairs[facing=north,half=bottom]");
            set(X, FY + 4, c - 2, "minecraft:polished_andesite_stairs[facing=south,half=top]");
            set(X, FY + 4, c + 1, "minecraft:polished_andesite_stairs[facing=north,half=top]");
        }
        // cales en bois sous la buse
        set(side * (x1 + 1), FY + 1, c - 3, "minecraft:spruce_slab[type=bottom]");
        set(side * (x2 - 1), FY + 1, c + 2, "minecraft:spruce_slab[type=bottom]");
    }

    private void container(int side, int x1, int z1, int x2, int z2, String body) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int X = side * x;
                boolean edgeX = x == x1 || x == x2;
                boolean edgeZ = z == z1 || z == z2;
                set(X, FY, z, "minecraft:spruce_planks");
                for (int y = FY + 1; y <= FY + 4; y++) {
                    if (edgeX && edgeZ) {
                        set(X, y, z, "minecraft:polished_andesite");
                    } else if (edgeX || edgeZ) {
                        set(X, y, z, body);
                    }
                }
                set(X, FY + 5, z, edgeX && edgeZ ? "minecraft:polished_andesite" : body);
            }
        }
        for (int x = x1 + 1; x < x2; x += 2) {
            int X = side * x;
            for (int z : new int[]{z1 - 1, z2 + 1}) {
                String f = z < z1 ? "north" : "south";
                // pas de nervure devant la porte laterale (x2 - 2, cote z2)
                if (s.isAir(X, FY + 1, z) && !(z > z2 && x == x2 - 2)) {
                    for (int y = FY + 1; y <= FY + 4; y++) {
                        set(X, y, z, "minecraft:iron_trapdoor[open=true,facing=" + f + ",half=bottom]");
                    }
                }
            }
        }
        int open = side * x1;
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            fill(open, FY + 1, z, open, FY + 3, z, "minecraft:air");
        }
        int dx = side * (x2 - 2);
        fill(dx, FY + 1, z2, dx, FY + 2, z2, "minecraft:air");
        // battant ouvert rabattu contre la face exterieure du poteau d'angle (x1, z1) : panneau cote poteau
        String leaf = side > 0 ? "west" : "east";
        set(open - side, FY + 1, z1, "minecraft:iron_trapdoor[open=true,facing=" + leaf + ",half=bottom]");
        set(open - side, FY + 2, z1, "minecraft:iron_trapdoor[open=true,facing=" + leaf + ",half=bottom]");
        set(side * (x2 - 1), FY + 6, z1 + 1, "minecraft:lantern");
    }

    /** Remblai sud (+3) : coffrages en planches en L sur le sommet, betonniere, un arbre a l'ouest seulement. */
    private void embankment(int side, long sd) {
        int topY = FY + 5;
        int[][] cells = {{8, 10}, {8, 11}, {8, 12}, {8, 14}, {8, 15}, {9, 9}, {10, 8}, {11, 8}, {13, 8}, {14, 8}};
        for (int[] c : cells) {
            int X = side * c[0];
            int z = c[1];
            int y0 = g(X, z);
            boolean tall = (c[0] + c[1]) % 2 == 0;
            int top = tall ? topY : topY - 1;
            for (int y = y0 + 1; y <= top; y++) {
                set(X, y, z, tall ? "minecraft:stripped_spruce_log[axis=y]" : "minecraft:spruce_planks");
            }
            if (tall) {
                set(X, top + 1, z, "minecraft:iron_bars");
            }
        }
        // betonniere : cuve grise, goulotte, chassis
        int bx = side * 13, bz = 14;
        int y0 = g(bx, bz);
        set(bx, y0 + 1, bz, "minecraft:polished_andesite");
        set(bx + side, y0 + 1, bz, "minecraft:polished_andesite");
        set(bx, y0 + 2, bz, "minecraft:light_gray_concrete");
        set(bx + side, y0 + 2, bz, "minecraft:cauldron");
        set(bx, y0 + 3, bz, "minecraft:orange_terracotta");
        set(bx, y0 + 1, bz + 1, "minecraft:polished_andesite_stairs[facing=north,half=bottom]");
        if (side < 0) {
            new Tree("minecraft:birch_log", "minecraft:birch_leaves", "minecraft:oak_leaves").height(7).crown(2.4)
                .place(s, bx - 1, g(bx - 1, bz + 2) + 1, bz + 2, 177);
        } else {
            set(bx + side, g(bx + side, bz + 1) + 1, bz + 1, "minecraft:bricks");
        }
    }

    /** Immeuble en construction : 6 poteaux en beton, dalle a +5, garde-corps, echafaudage au sud, fers a beton. */
    private void frame(int side) {
        int x1 = 18, x2 = 23, z1 = 11, z2 = 15, py = FY + 5;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int X = side * x;
                boolean col = (x == x1 || x == x2 || x == 20) && (z == z1 || z == z2);
                if (col) {
                    fill(X, FY + 1, z, X, py - 1, z, "minecraft:polished_andesite");
                }
                set(X, py, z, (x + z) % 5 == 0 ? "minecraft:smooth_stone" : "minecraft:light_gray_concrete");
                boolean east = x == x2, south = z == z2, north = z == z1;
                if (east || south) {
                    set(X, py + 1, z, (x + z) % 2 == 0 ? "minecraft:polished_andesite" : "minecraft:smooth_stone");
                } else if (north && x > x1) {
                    set(X, py + 1, z, "minecraft:iron_bars");
                }
            }
        }
        // poutres retombantes sous la dalle entre les poteaux (cassent le pan)
        for (int x = x1 + 1; x < x2; x++) {
            if (x != 20) {
                set(side * x, py - 1, z2, "minecraft:smooth_stone_slab[type=top]");
                set(side * x, py - 1, z1, "minecraft:smooth_stone_slab[type=top]");
            }
        }
        // fers a beton qui depassent des poteaux
        for (int[] c : new int[][]{{x2, z2}, {x2, z1}, {20, z2}}) {
            fill(side * c[0], py + 2, c[1], side * c[0], py + 3, c[1], "minecraft:iron_bars");
        }
        // echafaudage contre la face sud
        for (int x : new int[]{20, 21}) {
            for (int y = FY + 1; y <= py + 1; y++) {
                set(side * x, y, z2 + 2, "minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]");
            }
        }
        // palette de briques a l'abri sous la dalle
        bricks(side, 21, 13, 3, 77);
        // echelle cote centre accrochee au poteau d'angle
        ladder(side * (x1 - 1), z1, FY + 1, py, fx("west", side));
        set(side * x2, py + 2, z1 + 2, "minecraft:lantern");
    }

    private void toilet(int side, int x, int z) {
        int X = side * x;
        int y0 = g(X, z);
        String c = side < 0 ? "minecraft:lime_terracotta" : "minecraft:cyan_terracotta";
        set(X, y0 + 1, z, c);
        set(X, y0 + 2, z, c);
        set(X, y0 + 3, z, "minecraft:smooth_stone_slab[type=bottom]");
        set(X - side, y0 + 2, z, "minecraft:iron_trapdoor[open=true,facing=" + fx("west", side) + ",half=bottom]");
    }

    // ------------------------------------------------------------------ centre : grue a tour

    @Override
    void center() {
        String y = "minecraft:yellow_concrete";
        // massif de fondation sous le mat
        fill(-1, FY + 1, -2, 1, FY + 2, 2, "minecraft:smooth_stone");
        // poteaux de la plate-forme
        for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}, {-3, 0}, {3, 0}}) {
            fill(p[0], FY + 1, p[1], p[0], FY + 4, p[1], y);
        }
        // plate-forme a +5 (7x7) autour du mat
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                set(x, FY + 5, z, (Math.abs(x) == 3 || Math.abs(z) == 3) ? "minecraft:yellow_terracotta" : "minecraft:smooth_stone");
            }
        }
        // garde-corps : muret au nord / sud (couvert bas), barreaux a l'est / ouest, ouvertures des echelles
        for (int x = -3; x <= 3; x++) {
            set(x, FY + 6, -3, Math.abs(x) % 2 == 1 ? "minecraft:polished_andesite" : "minecraft:smooth_stone");
            set(x, FY + 6, 3, Math.abs(x) % 2 == 1 ? "minecraft:polished_andesite" : "minecraft:smooth_stone");
        }
        for (int z : new int[]{-2, -1, 1, 2}) {
            set(-3, FY + 6, z, "minecraft:iron_bars");
            set(3, FY + 6, z, "minecraft:iron_bars");
        }
        ladder(4, 0, FY + 1, FY + 5, "east");
        ladder(-4, 0, FY + 1, FY + 5, "west");
        // palettes de parpaings au pied (ferment la ligne base a base) et sous la plate-forme
        for (int z : new int[]{-5, -4, -3, 3, 4, 5}) {
            set(0, FY + 1, z, Math.abs(z) == 5 ? "minecraft:spruce_slab[type=top]" : "minecraft:polished_andesite");
            set(0, FY + 2, z, Math.abs(z) == 5 ? "minecraft:bricks" : "minecraft:smooth_stone");
        }
        set(0, FY + 3, 5, "minecraft:brick_slab[type=bottom]");
        // mat en treillis jaune (3x3) de FY+1 a FY+18
        for (int yy = FY + 1; yy <= FY + 18; yy++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
                    boolean face = (x == 0) != (z == 0);
                    if (yy <= FY + 2 && !corner) {
                        continue;
                    }
                    if (corner) {
                        set(x, yy, z, y);
                    } else if (face) {
                        set(x, yy, z, yy % 3 == 0 ? y : "minecraft:iron_bars");
                    } else if (yy > FY + 5) {
                        set(x, yy, z, "minecraft:air");
                    }
                }
            }
        }
        // fleche (le long de x) et contre-fleche, cabine, contrepoids, chariot, crochet et sa charge
        for (int x = -9; x <= 15; x++) {
            set(x, FY + 19, 0, Math.abs(x) <= 1 ? y : (x % 3 == 0 ? y : "minecraft:yellow_terracotta"));
            if (Math.abs(x) > 1 && x % 2 == 0) {
                set(x, FY + 18, 0, "minecraft:iron_bars");
            }
        }
        fill(-1, FY + 20, -1, 1, FY + 20, 1, y);
        set(0, FY + 21, 0, "minecraft:lightning_rod");
        fill(-9, FY + 17, -1, -7, FY + 18, 1, "minecraft:smooth_stone");
        fill(2, FY + 16, -1, 3, FY + 17, 1, "minecraft:white_concrete");
        set(3, FY + 17, 0, "minecraft:glass");
        set(3, FY + 16, 0, "minecraft:glass");
        set(10, FY + 18, 0, "minecraft:iron_block"); // chariot de la fleche : bloc plein, la chaine du crochet y pend sans vide
        fill(10, FY + 13, 0, 10, FY + 17, 0, "minecraft:iron_chain[axis=y]");
        set(10, FY + 12, 0, "minecraft:spruce_slab[type=top]");
        // lanternes pendues a une console en beton scellee dans le mat (rang de beton en FY + 8)
        for (int z : new int[]{2, -2}) {
            set(0, FY + 8, z, y);
            set(0, FY + 7, z, "minecraft:lantern[hanging=true]");
        }
        // bobine centrale sud et parpaings au nord
        spool(0, 11, "minecraft:spruce_planks", "minecraft:spruce_slab", "minecraft:stripped_oak_log[axis=y]");
        for (int x = -3; x <= 3; x++) {
            int z = Math.abs(x) >= 2 ? -10 : -11;
            set(x, FY + 1, z, "minecraft:polished_andesite");
            if (Math.abs(x) == 1 || Math.abs(x) == 3) {
                set(x, FY + 2, z, "minecraft:smooth_stone_slab[type=bottom]");
            }
        }
        set(0, FY + 2, -11, "minecraft:bricks");
        set(0, FY + 1, -12, "minecraft:barrel[facing=up]");
    }

    // ------------------------------------------------------------------ eclairage et hors terrain

    @Override
    void lamppost(int x, int z, int zs) {
        int y0 = g(x, z);
        set(x, y0 + 1, z, "minecraft:andesite_wall");
        fill(x, y0 + 2, z, x, y0 + 4, z, "minecraft:iron_bars");
        int az = z - zs;
        set(x, y0 + 4, az, "minecraft:iron_bars");
        set(x, y0 + 3, az, "minecraft:lantern[hanging=true]");
    }

    @Override
    String[] standMats() {
        return new String[]{"minecraft:spruce_stairs", "minecraft:spruce_planks", "minecraft:smooth_stone",
            "minecraft:stripped_birch_log", "minecraft:smooth_stone_slab"};
    }

    @Override
    void outside() {
        int[][] list = {{-31, 27, 7}, {33, 29, 7}, {-41, -19, 6}, {41, 14, 6}, {-41, 7, 7}, {38, -31, 6}, {-37, 31, 6},
            {41, -15, 6}}; // (41, -8) : les branches traversaient le toit de la cabane bleue
        for (int i = 0; i < list.length; i++) {
            int[] t = list[i];
            boolean birch = i % 2 == 0;
            new Tree(birch ? "minecraft:birch_log" : "minecraft:oak_log", birch ? "minecraft:birch_leaves" : "minecraft:oak_leaves",
                "minecraft:oak_leaves").height(t[2]).crown(2.5).place(s, t[0], g(t[0], t[1]) + 1, t[1], 600 + i);
        }
        // stock de buses et de palettes derriere les baraques
        for (int z = -6; z <= -2; z++) {
            set(-41, FY + 1, z, "minecraft:light_gray_concrete");
        }
        for (int z = 2; z <= 5; z++) {
            set(41, FY + 1, z, "minecraft:spruce_slab[type=top]");
            set(41, FY + 2, z, "minecraft:bricks");
        }
        set(-41, FY + 2, -4, "minecraft:light_gray_concrete");
    }

    @Override
    void vegetation() {
        Noise vn = noise.derive(7);
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                if (bare[x - GX0][z - GZ0]) {
                    continue;
                }
                int h = g(x, z);
                String top = s.get(x, h, z);
                if (!s.isAir(x, h + 1, z)) {
                    continue;
                }
                boolean grass = top.startsWith("minecraft:grass_block") || top.startsWith("minecraft:podzol");
                boolean dirt = top.startsWith("minecraft:coarse_dirt") || top.startsWith("minecraft:gravel");
                double v = vn.noise2(x / 5.0, z / 5.0) + 0.35 * (hash(91, x, z) - 0.5);
                double r = hash(92, x, z);
                if (grass && v > 0.25) {
                    set(x, h + 1, z, r < 0.2 ? "minecraft:fern" : r < 0.3 ? "minecraft:dandelion" : "minecraft:short_grass");
                } else if (dirt && v > 0.7 && r < 0.5) {
                    set(x, h + 1, z, r < 0.25 ? "minecraft:dead_bush" : "minecraft:short_grass");
                }
            }
        }
    }
}
