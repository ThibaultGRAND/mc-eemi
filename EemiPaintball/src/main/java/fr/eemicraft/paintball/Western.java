package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.Mat;
import fr.eemicraft.paintball.build.Noise;
import fr.eemicraft.paintball.build.Palette;
import fr.eemicraft.paintball.build.RoofStyle;
import fr.eemicraft.paintball.build.Sink;
import fr.eemicraft.paintball.build.Style;

/**
 * Petite map « Western » (1.4.0) : terre rouge et sable rouge, chateau d'eau au centre (plate-forme a +5 autour de la
 * cuve, abri dessous), chariots baches, mesa en strates de terre cuite (+4) avec palissade, tour de guet (+5),
 * bottes de foin, tonneaux, abreuvoirs, voie ferree, cabanes d'equipe a fausse facade, palissade de rondins + filet,
 * cactus et rails hors du terrain.
 */
final class Western extends Field {
    private final Palette stockade = Palette.of("minecraft:spruce_log[axis=y]");

    Western(Sink s) {
        super(s, 20261118L);
    }

    // ------------------------------------------------------------------ relief et sols

    @Override
    double raw(int x, int z) {
        int ax = Math.abs(x);
        double n = noise.derive(1).fbm2((x - 200) / 13.0, (z + 70) / 13.0, 3, 0.5, 2.0);
        double v = Math.max(0, n - 0.05) * 2.0;
        v = Math.max(v, bump(ax, z, 12, 12, 4.6, 4.2, 4.0));    // mesa sud (+4)
        v = Math.max(v, bump(ax, z, 21, -15, 4.0, 2.8, 2.5));   // eperon rocheux nord
        v = Math.max(v, bump(ax, z, 5, 9, 3.4, 2.4, 1.0));
        return v;
    }

    @Override
    void pads() {
        pad(6, -16, 16, -11, FY, true);        // chariot bache
        pad(17, 10, 24, 17, FY, true);         // tour de guet
        pad(8, -2, 11, 2, FY, true);           // meule de foin
        pad(-8, -15, 8, -13, FY, false);       // voie ferree centrale
        pad(-2, 9, 2, 13, FY, false);          // charrette centrale sud
        pad(-4, -12, 4, -10, FY, false);       // barricade centrale
    }

    @Override
    int[][] pathNorth() {
        return new int[][]{{26, -1}, {21, -4}, {17, -9}, {11, -9}, {6, -8}, {3, -7}, {0, -7}};
    }

    @Override
    int[][] pathSouth() {
        return new int[][]{{26, 1}, {21, 4}, {17, 8}, {12, 6}, {7, 7}, {3, 7}, {0, 7}};
    }

    /** Strates de terre cuite selon la hauteur (visibles sur les flancs). */
    @Override
    String subsoil(int x, int y, int z) {
        // bandes de familles differentes (pas de melange terre cuite / terre cuite a chaque rang)
        int k = y - FY;
        if (k <= 1) {
            return "minecraft:terracotta";
        }
        return k == 2 ? "minecraft:smooth_red_sandstone" : "minecraft:orange_terracotta";
    }

    @Override
    String fieldTop(int x, int z, int h) {
        int ax = Math.abs(x);
        double wear = noise.derive(3).fbm2(x / 12.0 + 40, z / 12.0, 2, 0.45, 2.0);
        double alt = noise.derive(6).fbm2(x / 11.0 - 70, z / 11.0, 2, 0.45, 2.0);
        if (h >= FY + 2) {
            // sommets : sable rouge sur la mesa, roche nue sur l'eperon (couleur differente par cote)
            if (z < 0) {
                return alt > 0.2 ? "minecraft:smooth_red_sandstone" : (x < 0 ? "minecraft:red_sand" : "minecraft:coarse_dirt");
            }
            return alt > 0.35 ? "minecraft:coarse_dirt" : "minecraft:red_sand";
        }
        double edge = noise.derive(4).noise2(x / 7.0 + 15, z / 7.0) + (Math.max(Math.abs(z), ax - 12) - 18) * 0.3;
        if (edge > 0.1 && (Math.abs(z) >= 16 || ax >= 30)) {
            return "minecraft:coarse_dirt";
        }
        if (wear > 0.45) {
            return "minecraft:terracotta";
        }
        if (wear > 0.2) {
            return "minecraft:coarse_dirt";
        }
        if (alt > 0.38) {
            return "minecraft:packed_mud";
        }
        return "minecraft:red_sand";
    }

    @Override
    String pathTop(int x, int z, double t2) {
        return t2 > 0.5 ? "minecraft:packed_mud" : "minecraft:dirt_path";
    }

    @Override
    String baseFloor(int x, int z, double t2) {
        return t2 > 0.3 ? "minecraft:stripped_spruce_log[axis=z]" : "minecraft:spruce_planks";
    }

    @Override
    String cabinFloor(int x, int z) {
        return "minecraft:spruce_planks";
    }

    @Override
    String outsideTop(int x, int z, double t2, boolean walk) {
        if (walk) {
            return t2 > 0.3 ? "minecraft:packed_mud" : "minecraft:coarse_dirt";
        }
        double n = noise.derive(8).noise2(x / 15.0, z / 15.0);
        return n > 0.5 ? "minecraft:terracotta" : n > 0.2 ? "minecraft:coarse_dirt" : "minecraft:red_sand";
    }

    // ------------------------------------------------------------------ palissade de rondins

    @Override
    String edgeMat(int x, int y, int z) {
        return stockade.at(x, y, z);
    }

    @Override
    String edgeCap(int x, int y, int z) {
        return Math.floorMod(x + z, 2) == 0 ? "minecraft:spruce_fence" : null;
    }

    @Override
    void edgeExtra(int x, int z, int h, int top) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        if (az == 21 && Math.floorMod(x, 6) == 0 && ax <= 32) {
            int zi = z - Integer.signum(z);
            fill(x, g(x, zi) + 1, zi, x, top, zi, "minecraft:stripped_dark_oak_log[axis=y]");
        }
        if (ax == 33 && az >= 10 && Math.floorMod(z, 6) == 0) {
            int xi = x - Integer.signum(x);
            fill(xi, g(xi, z) + 1, z, xi, top, z, "minecraft:stripped_dark_oak_log[axis=y]");
        }
    }

    // ------------------------------------------------------------------ bases

    @Override
    void base(int side) {
        String team = side < 0 ? "red" : "blue";
        Palette wall = Palette.of(side < 0 ? 241 : 243, "minecraft:" + team + "_terracotta", 70,
            "minecraft:" + team + "_concrete", 30).period(7).amp(0.2);
        Style st = new Style().wall(wall).plinth(Mat.of("minecraft:packed_mud"))
            .plinthStairs("minecraft:mud_brick_stairs").post("minecraft:stripped_spruce_log")
            .sill("minecraft:spruce_stairs").lintel("minecraft:spruce_slab").band(null)
            .cornice("minecraft:dark_oak_stairs").door("minecraft:spruce_door").shutter("minecraft:spruce_trapdoor")
            .spacing(4).storey(4).shutters(0.8).blankBays(0.2).seed(side < 0 ? 25 : 29);
        RoofStyle rs = new RoofStyle()
            .stairs(Palette.of(23, "minecraft:spruce_stairs", 70, "minecraft:dark_oak_stairs", 30).period(6))
            .ridge("minecraft:spruce_slab").body(Mat.of("minecraft:spruce_planks"))
            .gable(wall).gablePost("minecraft:stripped_spruce_log").overhang(1).spacing(4);
        cabin(side, st, rs);
        // fausse facade en gradins au-dessus de la porte, consoles en saillie (cassent le pan), enseigne
        int fx0 = side * 30;
        for (int z = -6; z <= 6; z++) {
            int az = Math.abs(z);
            int top = az <= 3 ? FY + 9 : az <= 5 ? FY + 8 : FY + 7;
            for (int y = FY + 5; y <= top; y++) {
                boolean sign = y == FY + 7 && az <= 3;
                set(fx0, y, z, sign ? "minecraft:birch_planks" : wall.at(fx0, y, z));
            }
            set(fx0, top + 1, z, "minecraft:dark_oak_slab[type=bottom]");
        }
        // contrefiche au dos de la fausse facade (cote toit)
        for (int y = FY + 5; y <= FY + 9; y++) {
            if (s.isAir(side * 31, y, 0)) {
                set(side * 31, y, 0, "minecraft:stripped_spruce_log[axis=y]");
            }
        }
        for (int z : new int[]{-6, -4, 4, 6}) {
            fill(side * 29, FY + 6, z, side * 29, FY + 7, z, "minecraft:stripped_spruce_log[axis=y]");
            set(side * 29, FY + 5, z, "minecraft:spruce_stairs[facing=" + fx("east", side) + ",half=top]");
        }
        // lettres de l'enseigne (tapis colle a la facade : une barre sombre)
        for (int z = -2; z <= 2; z++) {
            set(side * 29, FY + 7, z, "minecraft:dark_oak_trapdoor[open=true,half=bottom,facing=" + fx("west", side) + "]");
        }
        // decor du perron, different par equipe
        if (side < 0) {
            set(side * 30, FY + 1, 7, "minecraft:barrel[facing=up]");
            set(side * 30, FY + 1, -7, "minecraft:water_cauldron[level=3]");
        } else {
            set(side * 30, FY + 1, -7, "minecraft:hay_block[axis=y]");
            set(side * 30, FY + 1, 7, "minecraft:spruce_fence");
        }
        // barricades devant l'apparition : planches, bande a la couleur de l'equipe
        for (int[] r : new int[][]{{-9, -5}, {5, 9}}) {
            int x = side * 24;
            for (int z = r[0]; z <= r[1]; z++) {
                boolean post = z == r[0] || z == r[1];
                if (post) {
                    fill(x, FY + 1, z, x, FY + 3, z, "minecraft:stripped_spruce_log[axis=y]");
                    set(x, FY + 4, z, "minecraft:spruce_fence");
                } else {
                    fill(x, FY + 1, z, x, FY + 2, z, "minecraft:spruce_planks");
                    set(x, FY + 3, z, "minecraft:" + team + "_wool");
                }
            }
            set(side * 25, FY + 1, (r[0] + r[1]) / 2, "minecraft:spruce_stairs[facing=" + fx("west", side) + "]");
        }
    }

    // ------------------------------------------------------------------ couverts

    @Override
    void obstacles(int side) {
        long sd = side < 0 ? 401 : 402;
        wagon(side, 7, 14, -15, -12, side < 0 ? "minecraft:white_wool" : "minecraft:light_gray_wool");
        // eperon rocheux nord : rochers et crane de vache
        for (int x = 19; x <= 23; x++) {
            int h = x == 23 ? 2 : (x % 2 == 0 ? 1 : 0);
            if (h > 0) {
                stack(side, x, -14, h, "minecraft:terracotta");
            }
        }
        set(side * 21, g(side * 21, -14) + 1, -14, "minecraft:bone_block[axis=x]");
        set(side * 22, g(side * 22, -15) + 1, -15, "minecraft:skeleton_skull[rotation=" + (side < 0 ? 4 : 12) + "]");
        // nord, milieu
        hay(side, 5, -6, 1, sd);
        hay(side, 6, -6, 1, sd);
        hay(side, 6, -7, 2, sd);
        barrels(side, 10, -9, 2);
        barrels(side, 11, -9, 2);
        barrels(side, 10, -8, 2);
        barrels(side, 11, -8, hash(sd, 11, -8) < 0.5 ? 1 : 2);
        trough(side, 19, -9);
        trough(side, 19, -8);
        trough(side, 20, -8);
        crate(side, 14, -6, 1, sd);
        crate(side, 15, -6, 1, sd);
        crate(side, 15, -5, 2, sd);
        hay(side, 3, -17, 1, sd);
        hay(side, 4, -17, 1, sd);
        firewood(side, 25, -15, 2);
        firewood(side, 26, -15, 2);
        firewood(side, 26, -14, 1);
        // couloir du milieu : meule de foin, marchandises du magasin, bottes
        haystack(side, 9, 0);
        crate(side, 16, -2, 2, sd);
        crate(side, 16, -1, 2, sd);
        crate(side, 16, 1, 1, sd);
        crate(side, 16, 2, 1, sd);
        crate(side, 17, -2, 1, sd);
        hay(side, 21, -1, 1, sd);
        hay(side, 21, 1, 1, sd);
        hay(side, 21, 0, 2, sd);
        // sud, milieu
        crate(side, 5, 4, 2, sd);
        crate(side, 6, 4, 2, sd);
        crate(side, 5, 5, 1, sd);
        barrels(side, 7, 4, 1);
        hay(side, 13, 4, 1, sd);
        hay(side, 14, 4, 2, sd);
        hay(side, 13, 5, 1, sd);
        for (int[] c : new int[][]{{18, 7}, {19, 7}, {20, 6}, {21, 6}, {22, 6}}) {
            int X = side * c[0];
            int y0 = g(X, c[1]);
            set(X, y0 + 1, c[1], "minecraft:mud_bricks");
            if (hash(sd, c[0], c[1]) < 0.55) {
                set(X, y0 + 2, c[1], "minecraft:mud_brick_slab[type=bottom]");
            }
        }
        mesa(side, sd);
        watchtower(side, 19, 12);
        for (int x = 3; x <= 7; x++) {
            set(side * x, g(side * x, 16) + 1, 16, "minecraft:spruce_log[axis=x]");
        }
        for (int x = 4; x <= 6; x++) {
            set(side * x, g(side * x, 16) + 2, 16, "minecraft:spruce_log[axis=x]");
        }
        crate(side, 25, 14, 2, sd);
        crate(side, 26, 14, 2, sd);
        barrels(side, 25, 15, 1);
    }

    private void hay(int side, int x, int z, int h, long sd) {
        int X = side * x;
        int y0 = g(X, z);
        String axis = hash(sd, x, z) < 0.5 ? "x" : "z";
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, "minecraft:hay_block[axis=" + (y == 1 ? axis : "y") + "]");
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

    private void barrels(int side, int x, int z, int h) {
        int X = side * x;
        int y0 = g(X, z);
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, y == 1 && h > 1 ? "minecraft:barrel[facing=" + fx("east", side) + "]" : "minecraft:barrel[facing=up]");
        }
    }

    private void trough(int side, int x, int z) {
        int X = side * x;
        set(X, g(X, z) + 1, z, "minecraft:water_cauldron[level=3]");
    }

    private void firewood(int side, int x, int z, int h) {
        int X = side * x;
        int y0 = g(X, z);
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, "minecraft:spruce_log[axis=x]");
        }
    }

    /** Meule de foin ronde 3x3 (centre plus haut). */
    private void haystack(int side, int x, int z) {
        int X = side * x;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int y0 = g(X + dx, z + dz);
                boolean corner = dx != 0 && dz != 0;
                set(X + dx, y0 + 1, z + dz, corner ? "minecraft:hay_block[axis=x]" : "minecraft:hay_block[axis=y]");
                if (!corner) {
                    set(X + dx, y0 + 2, z + dz, dx == 0 && dz == 0 ? "minecraft:hay_block[axis=y]" : "minecraft:yellow_carpet");
                }
            }
        }
        set(X, g(X, z) + 3, z, "minecraft:yellow_carpet");
    }

    /** Chariot bache : roues, plateau a +2, bache en arceau (on peut s'abriter dedans), timon vers le centre. */
    private void wagon(int side, int x1, int x2, int z1, int z2, String canvas) {
        for (int x = x1; x <= x2; x++) {
            int X = side * x;
            boolean wheelX = x == x1 + 1 || x == x2 - 1;
            for (int z = z1; z <= z2; z++) {
                boolean edgeZ = z == z1 || z == z2;
                if (wheelX && edgeZ) {
                    set(X, FY + 1, z, "minecraft:stripped_dark_oak_log[axis=z]");
                }
                set(X, FY + 2, z, "minecraft:spruce_planks");
                if (x == x1 || x == x2) {
                    continue; // bouts ouverts
                }
                if (edgeZ) {
                    set(X, FY + 3, z, canvas);
                    set(X, FY + 4, z, canvas);
                } else {
                    set(X, FY + 5, z, canvas);
                }
            }
            if (x > x1 && x < x2) {
                // arceaux en bois tous les 2 (relief sur la bache)
                if (x % 2 == 0) {
                    set(X, FY + 5, z1, "minecraft:spruce_slab[type=bottom]");
                    set(X, FY + 5, z2, "minecraft:spruce_slab[type=bottom]");
                } else {
                    set(X, FY + 5, z1, "minecraft:" + B_STAIRS + "[facing=south,half=bottom]");
                    set(X, FY + 5, z2, "minecraft:" + B_STAIRS + "[facing=north,half=bottom]");
                }
            }
        }
        // timon et caisses
        set(side * (x1 - 1), FY + 1, (z1 + z2) / 2, "minecraft:spruce_fence");
        set(side * (x1 - 1), FY + 2, (z1 + z2) / 2, "minecraft:spruce_fence");
        set(side * x2, FY + 3, z1 + 1, "minecraft:barrel[facing=up]");
        set(side * (x2 - 1), FY + 3, z2 - 1, "minecraft:spruce_trapdoor[half=bottom,facing=north]");
        set(side * (x2 + 1), FY + 3, z1 + 1, "minecraft:lantern");
        set(side * (x2 + 1), FY + 1, z1 + 1, "minecraft:spruce_fence");
        set(side * (x2 + 1), FY + 2, z1 + 1, "minecraft:spruce_fence");
    }

    private static final String B_STAIRS = "spruce_stairs";

    /** Mesa (+4) : palissade de pieux en L sur l'arete, arbre mort a l'ouest, cactus en pot a l'est. */
    private void mesa(int side, long sd) {
        int topY = FY + 6;
        int[][] cells = {{8, 10}, {8, 11}, {8, 12}, {8, 14}, {8, 15}, {9, 9}, {10, 8}, {11, 8}, {13, 8}, {14, 8}};
        for (int[] c : cells) {
            int X = side * c[0];
            int z = c[1];
            int y0 = g(X, z);
            int top = Math.min(y0 + 2, (c[0] + c[1]) % 2 == 0 ? topY : topY - 1);
            for (int y = y0 + 1; y <= top; y++) {
                set(X, y, z, "minecraft:spruce_log[axis=y]");
            }
            set(X, top + 1, z, "minecraft:spruce_fence");
        }
        int bx = side * 13, bz = 14;
        int y0 = g(bx, bz);
        if (side < 0) {
            // arbre mort : tronc tordu sans feuilles
            fill(bx, y0 + 1, bz, bx, y0 + 4, bz, "minecraft:stripped_dark_oak_log[axis=y]");
            set(bx - 1, y0 + 4, bz, "minecraft:stripped_dark_oak_log[axis=x]");
            set(bx - 2, y0 + 5, bz, "minecraft:dark_oak_fence");
            set(bx, y0 + 5, bz + 1, "minecraft:dark_oak_fence");
            set(bx, y0 + 5, bz, "minecraft:stripped_dark_oak_log[axis=y]");
        } else {
            set(bx, y0 + 1, bz, "minecraft:barrel[facing=up]");
            set(bx, y0 + 2, bz, "minecraft:decorated_pot");
            set(bx + 1, y0 + 1, bz + 1, "minecraft:terracotta");
        }
        // rochers sur le flanc
        set(side * 15, g(side * 15, 11) + 1, 11, "minecraft:terracotta");
        set(side * 10, g(side * 10, 15) + 1, 15, "minecraft:brown_terracotta");
    }

    /** Tour de guet : 4 poteaux, plate-forme a +5 avec muret, petit toit a deux pans, echelle cote centre. */
    private void watchtower(int side, int x1, int z1) {
        int py = FY + 5;
        for (int dx = 0; dx <= 3; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                int X = side * (x1 + dx);
                int z = z1 + dz;
                boolean corner = (dx == 0 || dx == 3) && (dz == 0 || dz == 2);
                if (corner) {
                    fill(X, FY + 1, z, X, py + 3, z, "minecraft:stripped_spruce_log[axis=y]");
                } else {
                    set(X, py, z, "minecraft:spruce_planks");
                }
                boolean east = dx == 3, south = dz == 2, north = dz == 0;
                if (!corner && (east || south)) {
                    set(X, py + 1, z, "minecraft:spruce_planks");
                } else if (!corner && north && dx > 0) {
                    set(X, py + 1, z, "minecraft:spruce_fence");
                }
            }
        }
        // croisillons sous la plate-forme
        set(side * (x1 + 1), FY + 1, z1 + 2, "minecraft:spruce_fence");
        set(side * (x1 + 2), py - 1, z1 + 2, "minecraft:spruce_fence");
        set(side * (x1 + 1), py - 1, z1, "minecraft:spruce_fence");
        // toit a deux pans le long de x (au-dessus des poteaux)
        for (int dx = -1; dx <= 4; dx++) {
            int X = side * (x1 + dx);
            set(X, py + 4, z1 - 1, "minecraft:spruce_stairs[facing=south,half=bottom]");
            set(X, py + 4, z1, "minecraft:spruce_stairs[facing=south,half=bottom]");
            set(X, py + 5, z1 + 1, "minecraft:spruce_slab[type=bottom]");
            set(X, py + 4, z1 + 2, "minecraft:spruce_stairs[facing=north,half=bottom]");
            set(X, py + 4, z1 + 3, "minecraft:spruce_stairs[facing=north,half=bottom]");
            set(X, py + 4, z1 + 1, "minecraft:spruce_planks");
        }
        ladder(side * (x1 - 1), z1, FY + 1, py, fx("west", side));
        set(side * (x1 + 2), py + 3, z1 + 1, "minecraft:lantern[hanging=true]");
        // cloche d'alarme (decor, un seul cote)
        if (side > 0) {
            set(side * (x1 + 1), py + 3, z1 + 1, "minecraft:bell[attachment=ceiling,facing=north]");
        }
    }

    // ------------------------------------------------------------------ centre : chateau d'eau

    @Override
    void center() {
        String leg = "minecraft:stripped_spruce_log[axis=y]";
        // citerne en pierre sous la cuve (ferme la ligne base a base), abreuvoir au milieu
        for (int x = -1; x <= 1; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean mid = x == 0 && Math.abs(z) <= 1;
                set(x, FY + 1, z, "minecraft:cobblestone");
                set(x, FY + 2, z, mid ? "minecraft:water_cauldron[level=3]" : "minecraft:cobblestone");
            }
        }
        for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}, {-3, 0}, {3, 0}}) {
            fill(p[0], FY + 1, p[1], p[0], FY + 4, p[1], leg);
        }
        // entretoises sous la plate-forme
        for (int k = -2; k <= 2; k++) {
            if (k != 0) {
                set(-3, FY + 4, k, "minecraft:spruce_fence");
                set(3, FY + 4, k, "minecraft:spruce_fence");
            }
            set(k, FY + 4, -3, "minecraft:spruce_fence");
            set(k, FY + 4, 3, "minecraft:spruce_fence");
        }
        // plate-forme a +5 et garde-corps
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                set(x, FY + 5, z, (Math.abs(x) == 3 || Math.abs(z) == 3) ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks");
            }
        }
        for (int x = -3; x <= 3; x++) {
            set(x, FY + 6, -3, Math.abs(x) == 3 ? leg : "minecraft:spruce_planks");
            set(x, FY + 6, 3, Math.abs(x) == 3 ? leg : "minecraft:spruce_planks");
        }
        for (int z : new int[]{-2, -1, 1, 2}) {
            set(-3, FY + 6, z, "minecraft:spruce_fence");
            set(3, FY + 6, z, "minecraft:spruce_fence");
        }
        ladder(4, 0, FY + 1, FY + 5, "east");
        ladder(-4, 0, FY + 1, FY + 5, "west");
        // cuve ronde (diametre 5) cerclee de fer, toit conique
        for (int y = FY + 6; y <= FY + 10; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x * x + z * z > 4) {
                        continue;
                    }
                    boolean hoop = y == FY + 7 || y == FY + 9;
                    set(x, y, z, hoop ? "minecraft:stripped_dark_oak_wood[axis=y]" : "minecraft:spruce_planks");
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x * x + z * z > 4) {
                    continue;
                }
                boolean rim = x * x + z * z >= 3;
                if (rim) {
                    String f = Math.abs(x) >= Math.abs(z) ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south");
                    set(x, FY + 11, z, "minecraft:dark_oak_stairs[facing=" + f + ",half=bottom]");
                } else {
                    set(x, FY + 11, z, "minecraft:dark_oak_planks");
                }
            }
        }
        set(0, FY + 12, 0, "minecraft:dark_oak_slab[type=bottom]");
        // girouette (repere central)
        set(0, FY + 13, 0, "minecraft:lightning_rod");
        // bottes de foin et tonneaux au pied (ferment les dernieres lignes base a base)
        for (int z : new int[]{-5, -4, -3, 3, 4, 5}) {
            boolean barrel = Math.abs(z) == 5;
            set(0, FY + 1, z, barrel ? "minecraft:barrel[facing=up]" : "minecraft:hay_block[axis=y]");
            set(0, FY + 2, z, barrel ? "minecraft:barrel[facing=up]" : "minecraft:hay_block[axis=y]");
        }
        set(-1, FY + 4, -1, "minecraft:lantern[hanging=true]");
        set(1, FY + 4, 1, "minecraft:lantern[hanging=true]");
        // voie ferree au nord avec un wagonnet de minerai et des heurtoirs
        for (int x = -7; x <= 7; x++) {
            set(x, FY + 1, -14, "minecraft:rail[shape=east_west]");
        }
        set(-8, FY + 1, -14, "minecraft:spruce_planks");
        set(8, FY + 1, -14, "minecraft:spruce_planks");
        set(-8, FY + 2, -14, "minecraft:spruce_slab[type=bottom]");
        set(8, FY + 2, -14, "minecraft:spruce_slab[type=bottom]");
        set(-3, FY + 1, -14, "minecraft:chest[facing=south]");
        set(-3, FY + 2, -14, "minecraft:raw_iron_block");
        // barricade centrale au nord (planches en arc)
        for (int x = -3; x <= 3; x++) {
            int z = Math.abs(x) >= 2 ? -10 : -11;
            set(x, FY + 1, z, "minecraft:spruce_planks");
            if (Math.abs(x) == 1 || Math.abs(x) == 3) {
                set(x, FY + 2, z, "minecraft:spruce_slab[type=bottom]");
            }
        }
        set(0, FY + 2, -11, "minecraft:barrel[facing=up]");
        // charrette a bras au sud
        for (int x = -1; x <= 1; x++) {
            set(x, FY + 2, 11, "minecraft:spruce_planks");
            // ridelles plaquees contre le chargement de foin (panneau du cote de la charrette)
            set(x, FY + 3, 10, "minecraft:spruce_trapdoor[open=true,half=bottom,facing=north]");
            set(x, FY + 3, 12, "minecraft:spruce_trapdoor[open=true,half=bottom,facing=south]");
            set(x, FY + 3, 11, "minecraft:hay_block[axis=x]");
        }
        set(-1, FY + 1, 11, "minecraft:stripped_dark_oak_log[axis=z]");
        set(1, FY + 1, 11, "minecraft:stripped_dark_oak_log[axis=z]");
        set(2, FY + 2, 11, "minecraft:spruce_fence");
    }

    // ------------------------------------------------------------------ eclairage et hors terrain

    @Override
    void lamppost(int x, int z, int zs) {
        int y0 = g(x, z);
        set(x, y0 + 1, z, "minecraft:spruce_log[axis=y]");
        fill(x, y0 + 2, z, x, y0 + 4, z, "minecraft:spruce_fence");
        int az = z - zs;
        set(x, y0 + 4, az, "minecraft:spruce_fence");
        set(x, y0 + 3, az, "minecraft:lantern[hanging=true]");
    }

    @Override
    void floodlight(int tx, int tz) {
        fill(tx, FY, tz, tx, FY + 13, tz, "minecraft:spruce_log[axis=y]");
        set(tx, FY + 14, tz, "minecraft:spruce_slab[type=bottom]");
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            set(tx + d[0], FY + 13, tz + d[1], "minecraft:spruce_fence");
            set(tx + d[0], FY + 12, tz + d[1], "minecraft:lantern[hanging=true]");
        }
        set(tx, FY + 1, tz, "minecraft:stripped_spruce_log[axis=y]");
    }

    @Override
    String netPost() {
        return "minecraft:spruce_log";
    }

    @Override
    String netCap() {
        return "minecraft:spruce_slab[type=bottom]";
    }

    @Override
    String netTop() {
        return "minecraft:spruce_fence";
    }

    @Override
    String[] standMats() {
        return new String[]{"minecraft:spruce_stairs", "minecraft:spruce_planks", "minecraft:spruce_planks",
            "minecraft:stripped_dark_oak_log", "minecraft:spruce_slab"};
    }

    @Override
    void outside() {
        // cactus en bouquets (hors du terrain seulement : ils blessent)
        int[][] cacti = {{-41, -18, 3}, {-40, -16, 2}, {42, 15, 3}, {40, 13, 1}, {-42, 8, 2}, {39, -30, 3}, {-38, 31, 2},
            {43, -9, 2}, {-30, -33, 3}, {32, 33, 2}, {-43, 20, 1}, {28, -34, 2}};
        for (int[] c : cacti) {
            int y0 = g(c[0], c[1]);
            set(c[0], y0, c[1], "minecraft:sand");
            set(c[0], y0 - 1, c[1], "minecraft:terracotta");
            for (int y = 1; y <= c[2]; y++) {
                set(c[0], y0 + y, c[1], "minecraft:cactus[age=0]");
            }
        }
        // voie ferree derriere les gradins nord
        for (int x = -40; x <= 40; x++) {
            set(x, FY, -34, "minecraft:gravel");
            set(x, FY - 1, -34, "minecraft:terracotta");
            set(x, FY + 1, -34, "minecraft:rail[shape=east_west]");
            if (x % 3 == 0) {
                set(x, FY, -35, "minecraft:spruce_slab[type=top]");
                set(x, FY, -33, "minecraft:spruce_slab[type=top]");
            }
        }
        // rochers et cranes
        int[][] rocks = {{-42, -27}, {41, 22}, {-36, 34}, {36, -25}};
        for (int[] r : rocks) {
            fill(r[0], FY + 1, r[1], r[0] + 1, FY + 1, r[1] + 1, "minecraft:terracotta");
            set(r[0], FY + 2, r[1], "minecraft:brown_terracotta");
        }
        set(-40, FY + 1, 24, "minecraft:skeleton_skull[rotation=6]");
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
                if (!top.startsWith("minecraft:red_sand") && !top.startsWith("minecraft:coarse_dirt") && !top.startsWith("minecraft:terracotta")) {
                    continue;
                }
                boolean field = Math.abs(x) <= 34 && Math.abs(z) <= 22;
                double v = vn.noise2(x / 5.0, z / 5.0) + 0.35 * (hash(91, x, z) - 0.5);
                double thr = field ? 0.3 : 0.2;
                if (v < thr) {
                    continue;
                }
                double r = hash(92, x, z);
                if (r < 0.2) {
                    set(x, h + 1, z, "minecraft:dead_bush");
                } else if (r < 0.32 && !field && s.isAir(x, h + 2, z)) {
                    set(x, h + 1, z, "minecraft:tall_dry_grass");
                } else {
                    set(x, h + 1, z, "minecraft:short_dry_grass");
                }
            }
        }
    }
}
