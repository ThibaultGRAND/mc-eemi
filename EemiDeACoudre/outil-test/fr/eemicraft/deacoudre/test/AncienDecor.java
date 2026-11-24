package fr.eemicraft.deacoudre.test;

import fr.eemicraft.deacoudre.build.Sink;

/** Copie fidele de la construction 1.1.0 (EemiDeACoudre.build), pour la mesure "avant". */
final class AncienDecor {
    static final int WY = 100;

    static void build(Sink s, int r, int by) {
        int far = r + 12;
        for (int x = -far; x <= far; x++) {
            for (int z = -(r + 13); z <= far; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > far + 0.5 && z > -(r + 2)) continue;
                if (x * x + z * z <= r * r + r) {
                    s.set(x, WY - 3, z, "minecraft:prismarine_bricks");
                    s.set(x, WY - 2, z, "minecraft:sea_lantern");
                    s.set(x, WY - 1, z, "minecraft:water");
                    s.set(x, WY, z, "minecraft:water");
                } else {
                    for (int y = WY - 3; y <= WY - 1; y++) s.set(x, y, z, "minecraft:sandstone");
                    boolean rim = d <= r + 2.2;
                    boolean path = Math.abs(x) <= 1 && z < 0;
                    s.set(x, WY, z, rim ? "minecraft:smooth_quartz" : path ? "minecraft:cut_sandstone" : "minecraft:sand");
                }
            }
        }
        int[][] palms = {{-(r + 6), r + 3}, {r + 6, r + 3}, {-(r + 7), -2}, {r + 7, -2}, {0, r + 8}};
        for (int[] p : palms) {
            for (int y = WY + 1; y <= WY + 5; y++) s.set(p[0], y, p[1], "minecraft:jungle_log");
            int top = WY + 6;
            s.set(p[0], top, p[1], "minecraft:jungle_leaves[persistent=true]");
            int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] a : arms) {
                s.set(p[0] + a[0], top, p[1] + a[1], "minecraft:jungle_leaves[persistent=true]");
                s.set(p[0] + 2 * a[0], top, p[1] + 2 * a[1], "minecraft:jungle_leaves[persistent=true]");
                s.set(p[0] + 3 * a[0], top - 1, p[1] + 3 * a[1], "minecraft:jungle_leaves[persistent=true]");
            }
        }
        int[][] lamps = {{-(r + 3), -(r + 1)}, {r + 3, -(r + 1)}, {-(r + 3), r + 3}, {r + 3, r + 3}};
        for (int[] l : lamps) {
            s.set(l[0], WY + 1, l[1], "minecraft:spruce_fence");
            s.set(l[0], WY + 2, l[1], "minecraft:spruce_fence");
            s.set(l[0], WY + 3, l[1], "minecraft:lantern");
        }
        int z1 = -(r + 11), z2 = -(r + 3), t2 = z1 + 4;
        for (int y = WY + 1; y < by; y++) {
            boolean stripe = (y - WY) % 6 == 0;
            for (int x = -2; x <= 2; x++)
                for (int z = z1 + 1; z <= t2; z++)
                    if (Math.abs(x) == 2 || z == z1 + 1 || z == t2)
                        s.set(x, y, z, stripe ? "minecraft:light_blue_terracotta" : "minecraft:white_terracotta");
        }
        for (int x = -4; x <= 4; x++)
            for (int z = z1; z <= z2; z++) {
                boolean glassFloor = z >= z2 - 3;
                s.set(x, by, z, glassFloor ? "minecraft:glass" : (x + z) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:light_blue_concrete");
                boolean edge = Math.abs(x) == 4 || z == z1 || z == z2;
                boolean gate = x == 0 && z == z2;
                if (edge && !gate) {
                    s.set(x, by + 1, z, "minecraft:glass");
                    s.set(x, by + 2, z, "minecraft:glass");
                }
            }
        s.set(-4, by + 3, z1, "minecraft:lantern");
        s.set(4, by + 3, z1, "minecraft:lantern");
        s.set(-4, by + 1, z1, "minecraft:white_concrete");
        s.set(-4, by + 2, z1, "minecraft:white_concrete");
        s.set(4, by + 1, z1, "minecraft:white_concrete");
        s.set(4, by + 2, z1, "minecraft:white_concrete");
        for (int z = z2 + 1; z <= -2; z++) s.set(0, by, z, z == -2 ? "minecraft:yellow_concrete" : "minecraft:spruce_slab[type=top]");
        s.set(0, by - 1, z2 + 1, "minecraft:spruce_fence");
        for (int x = -far; x <= far; x += 6)
            for (int z = -(r + 12); z <= far; z += 6)
                if (s.isAir(x, WY + 4, z)) s.set(x, WY + 4, z, "minecraft:light[level=15]");
    }
}
