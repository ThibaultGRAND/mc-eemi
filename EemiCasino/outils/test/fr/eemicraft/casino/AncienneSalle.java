package fr.eemicraft.casino;

import fr.eemicraft.casino.build.Sink;

/** Copie de la salle 1.2.x (avant refonte), pour la mesure AVANT. */
final class AncienneSalle {
    private final Sink w; int ops, invalid; private final boolean models;
    AncienneSalle(Sink w, boolean models) { this.w = w; this.models = models; }
    void set(int x, int y, int z, String b) { w.set(x, y, z, b); }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {

        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    w.set(x, y, z, b);
                    ops++;
                }
            }
        }
    }

    void build() {
        final int F = Layout.FLOOR;
        // nettoyage
        fill(-31, F - 1, -31, 31, F + 15, 31, "minecraft:air");
        // sol + tapis rouge a croisillons dores
        fill(-29, F, -29, 29, F, 29, "minecraft:polished_blackstone_bricks");
        for (int x = -29; x <= 29; x++) {
            for (int z = -29; z <= 29; z++) {
                boolean gold = Math.floorMod(x + z, 8) == 0 || Math.floorMod(x - z, 8) == 0;
                set(x, F + 1, z, gold ? "minecraft:yellow_carpet" : "minecraft:red_carpet");
            }
        }
        // murs
        for (int i = -30; i <= 30; i++) {
            for (int y = F; y <= F + 13; y++) {
                String b = y <= F + 1 ? "minecraft:polished_blackstone" : (y == F + 6 || y == F + 12) ? "minecraft:gold_block" : "minecraft:dark_oak_planks";
                set(i, y, -30, b);
                set(i, y, 30, b);
                set(-30, y, i, b);
                set(30, y, i, b);
            }
        }
        // plafond : bois sombre et lanternes marines
        for (int x = -29; x <= 29; x++) {
            for (int z = -29; z <= 29; z++) {
                set(x, F + 13, z, Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? "minecraft:sea_lantern" : "minecraft:dark_oak_planks");
            }
        }
        // piliers de quartz
        int[][] pillars = {{-29, -29}, {29, -29}, {-29, 29}, {29, 29}, {-29, 0}, {29, 0}};
        for (int[] p : pillars) {
            fill(p[0], F + 1, p[1], p[0], F + 12, p[1], "minecraft:quartz_pillar");
            set(p[0], F + 1, p[1], "minecraft:chiseled_quartz_block");
            set(p[0], F + 12, p[1], "minecraft:chiseled_quartz_block");
        }
        // lumiere invisible
        for (int x = -27; x <= 27; x += 6) {
            for (int z = -27; z <= 27; z += 6) {
                set(x, F + 6, z, "minecraft:light[level=15]");
            }
        }
        // lustres
        int[][] chand = {{0, 0}, {-15, -16}, {15, -16}, {-15, 14}, {15, 14}};
        for (int[] c : chand) {
            chandelier(c[0], c[1]);
        }
        // machines a sous (mur nord)
        for (int x : Layout.SLOT_X) {
            if (models) {
                continue;
            }
            set(x, F + 1, Layout.SLOT_Z, "minecraft:polished_blackstone_bricks");
            set(x, F + 2, Layout.SLOT_Z, "minecraft:black_stained_glass");
            set(x, F + 3, Layout.SLOT_Z, "minecraft:gold_block");
            set(x, F + 4, Layout.SLOT_Z, "minecraft:redstone_lamp[lit=true]");
            set(x + 1, F + 2, Layout.SLOT_Z, "minecraft:lever[face=wall,facing=east]");
        }
        // video poker (mur est)
        for (int z : Layout.VP_Z) {
            if (models) {
                continue;
            }
            set(Layout.VP_X, F + 1, z, "minecraft:polished_blackstone_bricks");
            set(Layout.VP_X, F + 2, z, "minecraft:blue_stained_glass");
            set(Layout.VP_X, F + 3, z, "minecraft:lapis_block");
            set(Layout.VP_X, F + 4, z, "minecraft:sea_lantern");
        }
        // tables de blackjack (ouest), croupier cote ouest
        for (int cz : Layout.BJ_Z) {
            for (int x = Layout.BJ_X1; x <= Layout.BJ_X1 + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean corner = x == Layout.BJ_X1 + 2 && Math.abs(z - cz) == 2;
                    if (corner) {
                        continue;
                    }
                    boolean inner = x > Layout.BJ_X1 && Math.abs(z - cz) <= 1;
                    set(x, F + 1, z, inner ? "minecraft:green_wool" : "minecraft:dark_oak_planks");
                }
            }
            set(Layout.BJ_X1 + 1, F + 2, cz, "minecraft:white_carpet");
        }
        // roulette (centre)
        for (int x = -4; x <= 4; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean rim = Math.abs(x) == 4 || Math.abs(z) == 2;
                set(x, F + 1, z, rim ? "minecraft:dark_oak_planks" : "minecraft:green_wool");
            }
        }
        // cylindre : centre dore, anneau rouge/noir, zero vert
        set(-2, F + 1, 0, "minecraft:gold_block");
        int[][] ring = {{-3, -1}, {-2, -1}, {-1, -1}, {-1, 0}, {-1, 1}, {-2, 1}, {-3, 1}, {-3, 0}};
        for (int i = 0; i < ring.length; i++) {
            set(ring[i][0], F + 1, ring[i][1], i == 0 ? "minecraft:lime_concrete" : i % 2 == 0 ? "minecraft:black_concrete" : "minecraft:red_concrete");
        }
        // (plus de plaque doree au centre : le cylindre 3D est pose a plat sur la table)
        // grande roue : socle et pied derriere la roue (roue de 4,5 blocs centree en x -8,93, y F+4,5, z 13,5)
        fill(-10, F + 1, 12, -10, F + 1, 15, "minecraft:polished_blackstone_bricks");
        fill(-10, F + 2, 13, -10, F + 4, 14, "minecraft:dark_oak_planks");
        fill(-10, F + 5, 13, -10, F + 5, 14, "minecraft:gold_block");
        set(-11, F + 1, 13, "minecraft:polished_blackstone_bricks");
        set(-11, F + 1, 14, "minecraft:polished_blackstone_bricks");
        // plinko : mur de fond (planche de 4 x 5 blocs en x 9,97, z 11,5..15,5, y F+1,5..F+6,5)
        fill(10, F + 1, 11, 10, F + 7, 15, "minecraft:dark_oak_planks");
        fill(10, F + 1, 11, 10, F + 1, 15, "minecraft:polished_blackstone_bricks");
        fill(10, F + 7, 11, 10, F + 7, 15, "minecraft:gold_block");
        fill(10, F + 2, 11, 10, F + 6, 11, "minecraft:gold_block");
        fill(10, F + 2, 15, 10, F + 6, 15, "minecraft:gold_block");
        for (int x = 1; x <= 3; x++) {
            set(x, F + 2, 0, "minecraft:white_carpet");
        }
        // tables de poker (est)
        for (int cz : Layout.PK_Z) {
            for (int x = 17; x <= 23; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean corner = (x == 17 || x == 23) && Math.abs(z - cz) == 2;
                    if (corner) {
                        continue;
                    }
                    boolean rim = x == 17 || x == 23 || Math.abs(z - cz) == 2;
                    set(x, F + 1, z, rim ? "minecraft:dark_oak_planks" : "minecraft:green_wool");
                }
            }
            set(16, F + 1, cz - 1, "minecraft:dark_oak_stairs[facing=west]");
            set(16, F + 1, cz + 1, "minecraft:dark_oak_stairs[facing=west]");
            set(24, F + 1, cz - 1, "minecraft:dark_oak_stairs[facing=east]");
            set(24, F + 1, cz + 1, "minecraft:dark_oak_stairs[facing=east]");
            set(20, F + 1, cz - 3, "minecraft:dark_oak_stairs[facing=north]");
            set(20, F + 1, cz + 3, "minecraft:dark_oak_stairs[facing=south]");
        }
        // caisse (sud-ouest) et boutique (sud-est)
        counter(-14, -8, "minecraft:gold_block");
        counter(8, 14, "minecraft:amethyst_block");
        // porte de sortie (mur sud)
        fill(-2, F + 1, 30, 2, F + 5, 30, "minecraft:polished_blackstone");
        fill(-1, F + 1, 30, 1, F + 4, 30, "minecraft:black_stained_glass");
        set(0, F + 5, 30, "minecraft:gold_block");
        // plantes
        int[][] plants = {{-27, 27}, {27, 27}, {-27, -27}, {27, -27}, {-6, 27}, {6, 27}};
        for (int[] p : plants) {
            set(p[0], F + 1, p[1], "minecraft:polished_blackstone");
            set(p[0], F + 2, p[1], "minecraft:potted_bamboo");
        }
    }

    private void chandelier(int x, int z) {
        final int F = Layout.FLOOR;
        fill(x, F + 9, z, x, F + 12, z, "minecraft:iron_chain");
        set(x, F + 8, z, "minecraft:gold_block");
        int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] a : arms) {
            set(x + a[0], F + 8, z + a[1], "minecraft:gold_block");
            set(x + a[0], F + 7, z + a[1], "minecraft:lantern[hanging=true]");
        }
        set(x, F + 7, z, "minecraft:end_rod[facing=down]");
    }

    private void counter(int x1, int x2, String top) {
        final int F = Layout.FLOOR;
        for (int x = x1; x <= x2; x++) {
            set(x, F + 1, Layout.COUNTER_Z, "minecraft:dark_oak_planks");
            set(x, F + 2, Layout.COUNTER_Z, x == x1 || x == x2 ? top : "minecraft:dark_oak_slab[type=bottom]");
        }
        // arriere du comptoir
        for (int x = x1; x <= x2; x++) {
            set(x, F + 1, Layout.COUNTER_Z + 4, "minecraft:dark_oak_planks");
            set(x, F + 2, Layout.COUNTER_Z + 4, "minecraft:bookshelf");
        }
    }
}
