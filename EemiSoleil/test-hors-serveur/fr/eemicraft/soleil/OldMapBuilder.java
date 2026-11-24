package fr.eemicraft.soleil;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;




/**
 * Construction de la map : terrain de terre battue entre deux hauts murs peints en ciel,
 * lignes de depart et d'arrivee, estrades des qualifies, arbre sec derriere la poupee,
 * et dortoir aux lits superposes (salle d'attente).
 */
final class OldMapBuilder {
    /** Sol du terrain (les joueurs marchent en y FY + 1). */
    static final int FY = 100;
    /** Demi-largeur du terrain (x de -HW a HW). */
    static final int HW = 15;
    /** Ligne de depart (z 0..1) et d'arrivee (z FINISH..FINISH+1). */
    static final int START_Z = 0;
    static final int FINISH_Z = 66;
    /** Fond du terrain (derriere le depart) et bout (derriere l'arbre). */
    static final int BACK_Z = -12;
    static final int END_Z = 90;
    /** Hauteur des murs (au-dessus du sol). */
    static final int WALL_H = 26;
    /** Poupee et arbre. */
    static final double DOLL_X = 0.5;
    static final double DOLL_Z = 76.5;
    static final double TREE_X = 0.5;
    static final double TREE_Z = 84.5;
    /** Dortoir. */
    static final int HALL_Z1 = -80;
    static final int HALL_Z2 = -40;
    static final int HALL_TOP = FY + 13;

    private final fr.eemicraft.soleil.build.Sink s;
    int ops;
    OldMapBuilder(fr.eemicraft.soleil.build.Sink s) { this.s = s; }
    void set(int x, int y, int z, String b) { s.set(x, y, z, b); ops++; }
    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, b);
                }
            }
        }
    }

    void build() {
        ops = 0;
        fill(-HW - 5, FY - 4, HALL_Z1 - 2, HW + 5, FY + WALL_H + 4, END_Z + 2, "minecraft:air");
        field();
        walls();
        lines();
        stands();
        tree();
        hall();
    }

    // ------------------------------------------------------------------ terrain

    private void field() {
        Random r = new Random(1231);
        for (int x = -HW; x <= HW; x++) {
            for (int z = BACK_Z + 1; z <= END_Z - 1; z++) {
                fill(x, FY - 3, z, x, FY - 1, z, "minecraft:dirt");
                double v = r.nextDouble();
                String top = v < 0.62 ? "minecraft:sand" : v < 0.80 ? "minecraft:smooth_sandstone"
                        : v < 0.92 ? "minecraft:packed_mud" : "minecraft:coarse_dirt";
                set(x, FY, z, top);
            }
        }
        // terre sombre et racines autour de l'arbre
        for (int x = -5; x <= 6; x++) {
            for (int z = 79; z <= END_Z - 1; z++) {
                double dx = x - TREE_X + 0.5;
                double dz = z - TREE_Z + 0.5;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 4.5) {
                    set(x, FY, z, d < 2.5 ? "minecraft:rooted_dirt" : "minecraft:coarse_dirt");
                }
            }
        }
    }

    /** Lignes rouge et blanc : depart et arrivee. */
    private void lines() {
        for (int x = -HW; x <= HW; x++) {
            for (int dz = 0; dz <= 1; dz++) {
                boolean red = ((x + dz) & 1) == 0;
                set(x, FY, START_Z + dz, red ? "minecraft:red_concrete" : "minecraft:white_concrete");
                set(x, FY, FINISH_Z + dz, red ? "minecraft:red_concrete" : "minecraft:white_concrete");
            }
        }
        // petits poteaux aux bouts des lignes
        for (int side = -1; side <= 1; side += 2) {
            int x = side * HW;
            fill(x, FY + 1, START_Z, x, FY + 3, START_Z, "minecraft:red_concrete");
            set(x, FY + 4, START_Z, "minecraft:white_concrete");
            fill(x, FY + 1, FINISH_Z, x, FY + 3, FINISH_Z, "minecraft:red_concrete");
            set(x, FY + 4, FINISH_Z, "minecraft:white_concrete");
        }
    }

    // ------------------------------------------------------------------ murs peints en ciel

    private void walls() {
        int len = END_Z - BACK_Z;
        wall(new Random(11), len + 1, (u, v, b) -> set(-HW - 1, FY - 3 + v, BACK_Z + u, b));
        wall(new Random(22), len + 1, (u, v, b) -> set(HW + 1, FY - 3 + v, BACK_Z + u, b));
        wall(new Random(33), 2 * HW + 3, (u, v, b) -> set(-HW - 1 + u, FY - 3 + v, BACK_Z, b));
        wall(new Random(44), 2 * HW + 3, (u, v, b) -> set(-HW - 1 + u, FY - 3 + v, END_Z, b));
    }

    interface Painter {
        void put(int u, int v, String block);
    }

    /** Mur de ciel bleu avec des nuages blancs (ellipses qui se chevauchent). */
    private void wall(Random r, int len, Painter p) {
        int h = WALL_H + 3;
        int n = Math.max(2, len / 9);
        double[][] blobs = new double[n * 3][];
        for (int i = 0; i < n; i++) {
            double cu = r.nextDouble() * len;
            double cv = 3 + 6 + r.nextDouble() * (WALL_H - 9);
            double rx = 3.5 + r.nextDouble() * 3;
            double ry = 1.4 + r.nextDouble() * 1.1;
            blobs[i * 3] = new double[]{cu, cv, rx, ry};
            blobs[i * 3 + 1] = new double[]{cu - rx * 0.7, cv - ry * 0.5, rx * 0.6, ry * 0.8};
            blobs[i * 3 + 2] = new double[]{cu + rx * 0.6, cv + ry * 0.3, rx * 0.65, ry * 0.95};
        }
        for (int u = 0; u < len; u++) {
            for (int v = 0; v < h; v++) {
                boolean cloud = false;
                boolean edge = false;
                for (double[] b : blobs) {
                    double dx = (u - b[0]) / b[2];
                    double dy = (v - b[1]) / b[3];
                    double d = dx * dx + dy * dy;
                    if (d <= 1) {
                        cloud = true;
                        // bas du nuage legerement grise : du relief
                        if (v < b[1] - b[3] * 0.55) {
                            edge = true;
                        }
                    }
                }
                String block;
                if (v < 3) {
                    block = "minecraft:light_blue_concrete";
                } else if (cloud) {
                    block = edge ? "minecraft:snow_block" : "minecraft:white_concrete";
                } else {
                    block = "minecraft:light_blue_concrete";
                }
                p.put(u, v, block);
            }
        }
    }

    // ------------------------------------------------------------------ estrades des qualifies

    private void stands() {
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 9; i++) {
                int x = side * (5 + i);
                for (int step = 0; step < 4; step++) {
                    int z = FINISH_Z + 3 + step;
                    int top = FY + step;
                    fill(x, FY, z, x, top, z, "minecraft:spruce_planks");
                    if (step > 0) {
                        set(x, top, z, "minecraft:spruce_planks");
                    }
                }
                // dossier
                fill(x, FY + 1, FINISH_Z + 7, x, FY + 3, FINISH_Z + 7, "minecraft:spruce_planks");
                set(x, FY + 4, FINISH_Z + 7, "minecraft:spruce_fence");
            }
            int x = side * 14;
            fill(x, FY + 1, FINISH_Z + 3, x, FY + 4, FINISH_Z + 6, "minecraft:spruce_fence");
        }
    }

    // ------------------------------------------------------------------ arbre sec

    private void tree() {
        String wood = "minecraft:spruce_wood";
        double cx = TREE_X - 0.5;
        double cz = TREE_Z - 0.5;
        int base = FY + 1;
        int height = 15;
        for (int y = 0; y < height; y++) {
            double rad = 1.9 - 1.3 * y / height;
            for (int x = (int) Math.floor(cx - 2); x <= (int) Math.ceil(cx + 2); x++) {
                for (int z = (int) Math.floor(cz - 2); z <= (int) Math.ceil(cz + 2); z++) {
                    double dx = x - cx;
                    double dz = z - cz;
                    if (dx * dx + dz * dz <= rad * rad) {
                        set(x, base + y, z, wood);
                    }
                }
            }
        }
        // racines
        int[][] roots = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};
        for (int[] rt : roots) {
            set((int) Math.round(cx + rt[0]), base, (int) Math.round(cz + rt[1]), wood);
        }
        // branches nues
        Random r = new Random(77);
        for (int i = 0; i < 9; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double y0 = base + 6 + r.nextDouble() * (height - 7);
            branch(r, cx, y0, cz, a, 7 + r.nextInt(4), 2, wood);
        }
    }

    private void branch(Random r, double x, double y, double z, double a, int len, int depth, String wood) {
        for (int s = 0; s < len; s++) {
            x += Math.cos(a);
            z += Math.sin(a);
            y += 0.45 + r.nextDouble() * 0.35;
            a += (r.nextDouble() - 0.5) * 0.4;
            int bx = (int) Math.floor(x + 0.5);
            int bz = (int) Math.floor(z + 0.5);
            int by = (int) Math.floor(y);
            if (Math.abs(bx) >= HW || bz <= 79 || bz >= END_Z || by >= FY + WALL_H + 2) {
                return;
            }
            set(bx, by, bz, wood);
            if (depth > 0 && s == len / 2) {
                branch(r, x, y, z, a + (r.nextBoolean() ? 0.8 : -0.8), Math.max(2, len / 2), depth - 1, wood);
            }
        }
    }

    // ------------------------------------------------------------------ dortoir

    private void hall() {
        int x1 = -HW;
        int x2 = HW;
        for (int x = x1; x <= x2; x++) {
            for (int z = HALL_Z1; z <= HALL_Z2; z++) {
                set(x, FY - 1, z, "minecraft:stone");
                boolean aisle = x >= -5 && x <= 5;
                set(x, FY, z, aisle ? ((x + z) % 2 == 0 ? "minecraft:polished_andesite" : "minecraft:smooth_stone") : "minecraft:polished_andesite");
                set(x, HALL_TOP, z, "minecraft:smooth_stone");
                boolean edge = x == x1 || x == x2 || z == HALL_Z1 || z == HALL_Z2;
                if (edge) {
                    for (int y = FY + 1; y < HALL_TOP; y++) {
                        String b = y <= FY + 2 ? "minecraft:cyan_terracotta"
                                : y == FY + 3 ? "minecraft:light_gray_terracotta" : "minecraft:light_gray_concrete";
                        set(x, y, z, b);
                    }
                }
            }
        }
        // eclairage invisible
        for (int x = x1 + 2; x < x2; x += 5) {
            for (int z = HALL_Z1 + 2; z < HALL_Z2; z += 5) {
                set(x, HALL_TOP - 1, z, "minecraft:light[level=15]");
            }
        }
        // lits superposes : 4 rangees de 4 etages
        int[] cols = {-13, -9, 8, 12};
        for (int x0 : cols) {
            for (int z = HALL_Z1 + 2; z <= HALL_Z2 - 2; z++) {
                for (int t = 0; t < 4; t++) {
                    int y = FY + 1 + 3 * t;
                    if (t > 0) {
                        set(x0, y - 1, z, "minecraft:smooth_stone_slab[type=top]");
                        set(x0 + 1, y - 1, z, "minecraft:smooth_stone_slab[type=top]");
                    }
                    set(x0, y, z, "minecraft:white_bed[part=foot,facing=east]");
                    set(x0 + 1, y, z, "minecraft:white_bed[part=head,facing=east]");
                }
                if ((z - HALL_Z1) % 4 == 2) {
                    for (int y = FY + 1; y < HALL_TOP - 1; y++) {
                        set(x0 - 1, y, z, "minecraft:iron_bars");
                        set(x0 + 2, y, z, "minecraft:iron_bars");
                    }
                }
            }
        }
    }
}
