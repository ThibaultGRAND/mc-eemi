package fr.eemicraft.dropper.build;

/** Copie fidele de l'ancienne salle d'attente (EemiDropper 1.0.x), pour la mesure AVANT. */
final class OldRoom {
    static final String[] RAINBOW = {"red", "orange", "yellow", "lime", "light_blue", "blue", "purple", "magenta"};

    static void fill(Sink s, int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) s.set(x, y, z, b);
    }

    static void build(Sink s, int RY) {
        int r = 7;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                boolean edge = Math.abs(x) == r || Math.abs(z) == r;
                s.set(x, RY, z, edge ? "minecraft:smooth_quartz" : ((x + z) & 1) == 0 ? "minecraft:white_concrete" : "minecraft:light_blue_concrete");
            }
        for (int y = RY - 14; y <= RY - 1; y++) {
            String col = "minecraft:" + RAINBOW[(y - RY + 14) % RAINBOW.length] + "_concrete";
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++)
                    if (Math.abs(x) == 2 || Math.abs(z) == 2) s.set(x, y, z, col);
        }
        fill(s, -2, RY - 16, -2, 2, RY - 16, 2, "minecraft:prismarine_bricks");
        fill(s, -1, RY - 15, -1, 1, RY - 15, 1, "minecraft:sea_lantern");
        fill(s, -1, RY - 14, -1, 1, RY - 14, 1, "minecraft:water");
        fill(s, -1, RY, -1, 1, RY, 1, "minecraft:glass");
        s.set(-2, RY, -2, "minecraft:sea_lantern");
        s.set(2, RY, -2, "minecraft:sea_lantern");
        s.set(-2, RY, 2, "minecraft:sea_lantern");
        s.set(2, RY, 2, "minecraft:sea_lantern");
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                if (Math.abs(x) != r && Math.abs(z) != r) continue;
                boolean pillar = (x == -r || x == 0 || x == r) && (z == -r || z == 0 || z == r);
                for (int y = RY + 1; y <= RY + 4; y++)
                    s.set(x, y, z, pillar ? "minecraft:quartz_pillar" : y == RY + 1 ? "minecraft:light_blue_stained_glass" : "minecraft:glass");
                s.set(x, RY + 5, z, "minecraft:smooth_quartz");
            }
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                boolean edge = Math.abs(x) == r || Math.abs(z) == r;
                s.set(x, RY + 6, z, edge ? "minecraft:smooth_quartz" : "minecraft:glass");
            }
        int[][] lamps = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        for (int[] l : lamps) s.set(l[0], RY + 6, l[1], "minecraft:sea_lantern");
        int[][] corners = {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
        for (int[] l : corners) s.set(l[0], RY + 1, l[1], "minecraft:lantern");
        for (int x = -5; x <= -3; x++) {
            s.set(x, RY + 1, -6, "minecraft:spruce_stairs[facing=north]");
            s.set(x + 8, RY + 1, -6, "minecraft:spruce_stairs[facing=north]");
        }
        int[][] lights = {{-3, 0}, {3, 0}, {0, -4}, {0, 4}};
        for (int[] l : lights) s.set(l[0], RY + 4, l[1], "minecraft:light[level=15]");
    }
}
