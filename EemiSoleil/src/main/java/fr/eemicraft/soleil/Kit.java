package fr.eemicraft.soleil;

import fr.eemicraft.soleil.build.B;
import fr.eemicraft.soleil.build.Dir;
import fr.eemicraft.soleil.build.Noise;
import fr.eemicraft.soleil.build.Palette;
import fr.eemicraft.soleil.build.Sink;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Outils de construction partages par les zones de Squid Game (skill builder-minecraft) :
 * murs-decors de ciel en relief (4-5 plans, nuages en saillie, pilastres), murs a panneaux (pilastres en saillie,
 * panneaux en retrait, bandeaux tous les 7, corniche en escaliers retournes), mats a projecteurs, escaliers « Escher ».
 * Aucun pan coplanaire de plus de 12 blocs : pilastres tous les 6 a 10.
 */
abstract class Kit {
    final Sink s;
    final List<String> warnings = new ArrayList<>();

    Kit(Sink s) {
        this.s = s;
    }

    void set(int x, int y, int z, String st) {
        s.set(x, y, z, B.id(st));
    }

    void set(int x, int y, int z, Palette p) {
        s.set(x, y, z, p.at(x, y, z));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String st) {
        String id = B.id(st);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) s.set(x, y, z, id);
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Palette p) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) s.set(x, y, z, p.at(x, y, z));
    }

    boolean air(int x, int y, int z) {
        return s.isAir(x, y, z);
    }

    // ================================================================ repere de mur

    /** Repere d'un mur : u le long du mur, d la profondeur (0 = face interieure, croissant vers l'exterieur). */
    static final class Frame {
        final boolean xFixed;
        final int base, sign;
        final Dir out;

        Frame(boolean xFixed, int base, int sign, Dir out) {
            this.xFixed = xFixed;
            this.base = base;
            this.sign = sign;
            this.out = out;
        }

        int x(int u, int d) { return xFixed ? base + sign * d : u; }
        int z(int u, int d) { return xFixed ? u : base + sign * d; }

        static Frame west(int x) { return new Frame(true, x, -1, Dir.WEST); }
        static Frame east(int x) { return new Frame(true, x, 1, Dir.EAST); }
        static Frame north(int z) { return new Frame(false, z, -1, Dir.NORTH); }
        static Frame south(int z) { return new Frame(false, z, 1, Dir.SOUTH); }
    }

    void put(Frame f, int u, int d, int y, String st) {
        set(f.x(u, d), y, f.z(u, d), st);
    }

    void put(Frame f, int u, int d, int y, Palette p) {
        int x = f.x(u, d), z = f.z(u, d);
        s.set(x, y, z, p.at(x, y, z));
    }

    static boolean near(int[] a, int v, int r) {
        for (int i : a) if (Math.abs(i - v) <= r) return true;
        return false;
    }

    static int[] steps(int u1, int u2, int step, int off) {
        List<Integer> l = new ArrayList<>();
        for (int u = u1 + off; u <= u2; u += step) l.add(u);
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    // ================================================================ mur-decor de ciel

    /** Couleurs d'un ciel peint. */
    static final class Sky {
        final String low, high, hillFar, hillNear, hillTop, cloudEdge, cloud, cloudCore;

        Sky(String low, String high, String hillFar, String hillNear, String hillTop, String cloudEdge, String cloud, String cloudCore) {
            this.low = low;
            this.high = high;
            this.hillFar = hillFar;
            this.hillNear = hillNear;
            this.hillTop = hillTop;
            this.cloudEdge = cloudEdge;
            this.cloud = cloud;
            this.cloudCore = cloudCore;
        }
    }

    static final Sky DAY = new Sky("light_blue_wool", "light_blue_concrete", "cyan_terracotta", "green_wool", "moss_block",
            "snow_block", "white_wool", "smooth_quartz");
    static final Sky SUNSET = new Sky("orange_terracotta", "pink_terracotta", "brown_terracotta", "red_terracotta", "terracotta",
            "white_terracotta", "pink_wool", "smooth_quartz");
    static final Sky DUSK = new Sky("light_blue_concrete", "blue_concrete", "blue_terracotta", "cyan_terracotta", "warped_wart_block",
            "light_gray_wool", "white_wool", "smooth_quartz");

    private static final class Cloud {
        final double[][] blobs;
        final double vb;

        Cloud(double[][] blobs, double vb) {
            this.blobs = blobs;
            this.vb = vb;
        }
    }

    /**
     * Mur peint en ciel entre u1 et u2, sol en y0, partie peinte v = 1..h-3, frise h-2..h-1, passerelle en y0+h.
     * Plans : d-1 corniche, d0 pilastres / collines proches / coeurs des nuages, d1 ciel, d2-d3 ame, d4 poteaux.
     */
    void skyWall(Frame f, int u1, int u2, int y0, int h, long seed, Sky sky, int[] pil) {
        int len = u2 - u1 + 1;
        int vMax = h - 3;
        int top = y0 + h;
        Noise hn = new Noise(seed);
        Noise fn = hn.derive(1);
        int[] hb = new int[len];
        int[] hf = new int[len];
        for (int i = 0; i < len; i++) {
            int u = u1 + i;
            hb[i] = (int) Math.max(2, Math.min(vMax / 2, Math.round(vMax * 0.22 + vMax * 0.16 * hn.fbm2(u / 19.0, 0.37, 3, 0.5, 2))));
            int hh = (int) Math.floor(1.2 + 3.8 * fn.fbm2(u / 8.0, 4.2, 2, 0.5, 2));
            hf[i] = near(pil, u, 1) || hh < 1 ? 0 : Math.min(hh, 5);
        }
        Random r = new Random(seed * 31 + 7);
        List<Cloud> clouds = new ArrayList<>();
        int n = Math.max(2, len / 10);
        for (int k = 0; k < n * 3 && clouds.size() < n; k++) {
            double cu = u1 + 4 + r.nextDouble() * Math.max(1, len - 8);
            double cv = vMax * 0.5 + r.nextDouble() * vMax * 0.35;
            boolean close = false;
            for (Cloud c : clouds) if (Math.abs(c.blobs[0][0] - cu) < 7 && Math.abs(c.blobs[0][1] - cv) < 5) close = true;
            if (close) continue;
            double rx = 3.6 + r.nextDouble() * 2.8;
            double ry = 1.8 + r.nextDouble() * 0.8;
            clouds.add(new Cloud(new double[][]{
                    {cu, cv, rx, ry},
                    {cu - rx * 0.62, cv - ry * 0.15, rx * 0.6, ry * 0.85},
                    {cu + rx * 0.58, cv + ry * 0.05, rx * 0.62, ry * 0.9},
                    {cu + rx * 0.12, cv + ry * 0.75, rx * 0.5, ry * 0.75}}, cv - ry * 0.6));
        }
        boolean[][] cloud = new boolean[len][vMax + 2];
        boolean[][] core = new boolean[len][vMax + 2];
        for (int i = 0; i < len; i++) {
            for (int v = 1; v <= vMax; v++) {
                double best = 9;
                boolean in = false;
                for (Cloud c : clouds) {
                    if (v < c.vb) continue;
                    for (int b = 0; b < c.blobs.length; b++) {
                        double[] bl = c.blobs[b];
                        double dx = (u1 + i - bl[0]) / bl[2];
                        double dy = (v - bl[1]) / bl[3];
                        double d = dx * dx + dy * dy;
                        if (d <= 1) in = true;
                        best = Math.min(best, d / (b == 0 ? 0.42 : 0.3));
                    }
                }
                cloud[i][v] = in && v > hb[i] + 1;
                core[i][v] = cloud[i][v] && best <= 1 && !near(pil, u1 + i, 1);
            }
        }
        String bevelUp = B.stairs("smooth_quartz_stairs", f.out, false);
        String bevelDown = B.stairs("smooth_quartz_stairs", f.out, true);
        for (int i = 0; i < len; i++) {
            int u = u1 + i;
            for (int y = y0 - 1; y <= top - 1; y++) {
                put(f, u, 2, y, "spruce_planks");
                put(f, u, 3, y, "spruce_planks");
            }
            put(f, u, 0, y0 - 1, "spruce_planks");
            put(f, u, 1, y0 - 1, "spruce_planks");
            put(f, u, 0, y0, "polished_andesite");
            put(f, u, 1, y0, "polished_andesite");
            for (int v = 1; v <= vMax; v++) {
                int y = y0 + v;
                double horizon = vMax * 0.42 + 2.5 * hn.noise2(u / 9.0, 17.3);
                String d1 = v <= hb[i] ? sky.hillFar : v < horizon ? sky.low : sky.high;
                String d0 = null;
                if (v <= hf[i]) {
                    d0 = v <= hf[i] - 2 ? sky.hillNear : sky.hillTop;
                } else if (cloud[i][v]) {
                    d1 = !cloud[i][v - 1] ? sky.cloudEdge : sky.cloud;
                    if (core[i][v]) d0 = sky.cloudCore;
                }
                put(f, u, 1, y, d1);
                if (d0 != null) put(f, u, 0, y, d0);
            }
            for (int v = 2; v < vMax; v++) {
                if (!core[i][v]) continue;
                if (!core[i][v + 1] && cloud[i][v + 1]) put(f, u, 0, y0 + v + 1, bevelUp);
                if (!core[i][v - 1] && v - 1 > hf[i] + 1) put(f, u, 0, y0 + v - 1, bevelDown);
            }
            for (int d = 0; d <= 1; d++) {
                put(f, u, d, top - 2, "smooth_quartz");
                put(f, u, d, top - 1, "smooth_quartz");
            }
            put(f, u, -1, top - 1, B.stairs("smooth_quartz_stairs", f.out, true));
            put(f, u, 0, top, "smooth_quartz_slab[type=top]");
            put(f, u, 1, top, "spruce_slab[type=top]");
            put(f, u, 2, top, "spruce_slab[type=top]");
            put(f, u, 3, top, "smooth_quartz_slab[type=top]");
            put(f, u, 0, top + 1, "dark_oak_fence");
            put(f, u, 3, top + 1, "dark_oak_fence");
            if (Math.floorMod(u, 4) == 0) {
                for (int y = y0 - 1; y <= top - 1; y++) put(f, u, 4, y, "stripped_dark_oak_log[axis=y]");
                put(f, u, 4, top, "dark_oak_slab[type=bottom]");
            } else {
                for (int y = y0 + 4; y < top; y += 8) put(f, u, 4, y, "dark_oak_fence");
            }
        }
        for (int k = 0; k < pil.length; k++) {
            int u = pil[k];
            if (u < u1 || u > u2) continue;
            put(f, u, 0, y0 + 1, "chiseled_quartz_block");
            for (int y = y0 + 2; y <= top - 5; y++) put(f, u, 0, y, "quartz_pillar[axis=y]");
            put(f, u, 0, top - 4, "chiseled_quartz_block");
            put(f, u, 0, top - 3, "quartz_pillar[axis=y]");
            put(f, u, -1, top - 4, bevelDown);
            put(f, u, -1, top - 3, "chiseled_quartz_block");
            put(f, u, -1, top - 2, "chiseled_quartz_block");
            put(f, u, 0, top + 1, "quartz_pillar[axis=y]");
            put(f, u, 0, top + 2, "quartz_pillar[axis=y]");
            if (k % 2 == 0) put(f, u, 0, top + 3, "lantern[hanging=false]");
        }
    }

    // ================================================================ mur a panneaux

    /**
     * Mur a panneaux de y0 a y1 : d0 pilastres (tous les pas de pil[]) et bandeaux tous les 7, d1 panneaux (palette),
     * d2 ame ; socle sombre sur 2 rangs, corniche en escaliers retournes en d-1. Lampes en applique sur un pilastre sur deux.
     */
    void panelWall(Frame f, int u1, int u2, int y0, int y1, Palette panel, String pilaster, String band, String socle,
                   String bandStair, int[] pil, String lamp, String grille) {
        for (int u = u1; u <= u2; u++) {
            boolean isPil = near(pil, u, 0);
            // grille de ventilation au centre d'une travee sur deux (en damier), en retrait devant l'ame sombre
            int prev = u1 - 1, next = u2 + 1;
            for (int p : pil) {
                if (p <= u && p > prev) prev = p;
                if (p > u && p < next) next = p;
            }
            boolean midU = Math.abs(u - (prev + next) / 2.0) <= 0.6;
            int bayU = 0;
            for (int p : pil) if (p <= u) bayU++;
            for (int y = y0; y <= y1; y++) {
                put(f, u, 2, y, socle);
                boolean bandRow = y > y0 + 2 && (y - y0) % 7 == 0 && y < y1 - 1;
                if (y <= y0 + 1) {
                    put(f, u, 1, y, socle);
                    put(f, u, 0, y, socle);
                } else if (isPil) {
                    put(f, u, 1, y, pilaster);
                    put(f, u, 0, y, pilaster);
                } else if (bandRow) {
                    put(f, u, 1, y, band);
                    put(f, u, 0, y, B.stairs(bandStair, f.out, true));
                } else {
                    int rv = (y - y0) % 7;
                    int bayV = (y - y0) / 7;
                    boolean g = grille != null && midU && (rv == 3 || rv == 4) && ((bayU + bayV) & 1) == 0 && y < y1 - 2;
                    put(f, u, 1, y, g ? grille : panel.at(f.x(u, 1), y, f.z(u, 1)));
                }
            }
            put(f, u, 0, y1, B.stairs(bandStair, f.out, true));
            put(f, u, -1, y1, B.stairs(bandStair, f.out, true));
            put(f, u, 0, y1 + 1, B.slab(bandStair.replace("_stairs", "_slab"), "bottom"));
        }
        for (int k = 0; k < pil.length; k++) {
            int u = pil[k];
            if (u < u1 || u > u2) continue;
            put(f, u, -1, y0 + 2, B.stairs(bandStair, f.out, false));
            if (lamp != null && k % 2 == 0) {
                // applique : lanterne posee sur un corbeau (escalier retourne colle au pilastre)
                put(f, u, -1, y0 + 4, B.stairs(bandStair, f.out, true));
                put(f, u, -1, y0 + 5, lamp);
            }
        }
    }

    // ================================================================ mats et projecteurs

    /** Mat a projecteurs : fut en muret noir de y0 a y0+h, tete 3x2 de lanternes marines tournee vers face. */
    void mast(int x, int y0, int z, int h, Dir face) {
        for (int y = y0; y < y0 + h; y++) set(x, y, z, "polished_blackstone_wall");
        int hy = y0 + h;
        Dir side = face.cw();
        for (int k = -1; k <= 1; k++) {
            int ax = x + side.dx * k, az = z + side.dz * k;
            set(ax, hy - 1, az, "polished_blackstone_slab[type=top]");
            set(ax, hy, az, "black_concrete");
            set(ax, hy + 1, az, "black_concrete");
            set(ax + face.dx, hy, az + face.dz, "sea_lantern");
            set(ax + face.dx, hy + 1, az + face.dz, "sea_lantern");
            set(ax + face.dx, hy + 2, az + face.dz, B.trapdoor("iron_trapdoor", face, false, false));
            set(ax, hy + 2, az, "polished_blackstone_slab[type=bottom]");
        }
    }

    /** Lampadaire : socle, fut de murets, bras et lanterne suspendue. */
    void lampPost(int x, int y, int z, Dir arm, String lantern) {
        set(x, y, z, "polished_andesite");
        for (int k = 1; k <= 4; k++) set(x, y + k, z, "polished_blackstone_wall");
        set(x, y + 5, z, "polished_blackstone");
        set(x + arm.dx, y + 5, z + arm.dz, B.stairs("polished_blackstone_stairs", arm.opposite(), true));
        set(x + arm.dx, y + 4, z + arm.dz, lantern);
    }

    // ================================================================ escaliers « Escher »

    /**
     * Volee d'escaliers pastel collee a un mur (decor des couloirs de la serie) : marches de couleur qui montent vers
     * up sur n marches a partir de (x, y, z), limons en beton du cote du vide (side), palier a la fin.
     */
    void escher(int x, int y, int z, Dir up, Dir side, int n, String color, String stairs) {
        for (int k = 0; k < n; k++) {
            int sx = x + up.dx * k, sz = z + up.dz * k, sy = y + k;
            for (int w = 0; w < 2; w++) {
                int ax = sx - side.dx * w, az = sz - side.dz * w;
                set(ax, sy, az, B.stairs(stairs, up, false));
                for (int yy = y; yy < sy; yy++) set(ax, yy, az, color);
            }
            set(sx + side.dx, sy, sz + side.dz, color);
            set(sx + side.dx, sy + 1, sz + side.dz, "white_concrete");
        }
        int lx = x + up.dx * n, lz = z + up.dz * n;
        for (int w = -1; w <= 1; w++) {
            int ax = lx - side.dx * w, az = lz - side.dz * w;
            for (int yy = y; yy < y + n; yy++) set(ax, yy, az, color);
            set(ax, y + n - 1, az, "smooth_quartz");
        }
    }
}
