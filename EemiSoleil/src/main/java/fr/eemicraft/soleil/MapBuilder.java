package fr.eemicraft.soleil;

import fr.eemicraft.soleil.build.B;
import fr.eemicraft.soleil.build.Connect;
import fr.eemicraft.soleil.build.Dir;
import fr.eemicraft.soleil.build.Noise;
import fr.eemicraft.soleil.build.Palette;
import fr.eemicraft.soleil.build.Sink;
import fr.eemicraft.soleil.build.Stairs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Construction de la map (version 2, skill builder-minecraft) :
 * <ul>
 * <li>piste de terre battue PLATE (variantes en grappes, aucun relief), lignes de depart / d'arrivee, bordures peintes ;</li>
 * <li>quatre murs-decors de ciel en relief : collines lointaines et proches, nuages en saillie biseautes, pilastres et
 * corniche en quartz, portes de service, poteaux de ligne, dos du decor en charpente ;</li>
 * <li>passerelles en haut des murs, cabines de gardes (silhouettes rose et noir), projecteurs, mats d'eclairage aux coins ;</li>
 * <li>gradins des qualifies en escaliers / dalles avec rambardes et auvent raye ;</li>
 * <li>arbre sec heros derriere la poupee (tronc evase et tordu, racines, branches et rameaux), sol et lumieres au sol ;</li>
 * <li>dortoir : lits superposes verts sur 4 niveaux, passerelles et escaliers roses, mezzanine, eclairage visible.</li>
 * </ul>
 * Tout passe par un {@link Sink} : le plugin ecrit d'abord dans un RecordingSink, puis le recopie dans le monde.
 * Contrat garde : sol en y FY, piste x -HW..HW, depart z 0-1, arrivee z FINISH_Z, poupee et arbre, places des gradins
 * (y FY+3 plein en z FINISH_Z+6), dortoir et point d'apparition (0.5, FY+1, -60.5).
 */
final class MapBuilder {
    /** Sol du terrain (les joueurs marchent en y FY + 1). */
    static final int FY = 100;
    /** Demi-largeur du terrain (x de -HW a HW). */
    static final int HW = 15;
    /** Ligne de depart (z 0..1) et d'arrivee (z FINISH..FINISH+1). */
    static final int START_Z = 0;
    static final int FINISH_Z = 66;
    /** Fond du terrain (derriere le depart) et bout (derriere l'arbre) : plans interieurs des murs. */
    static final int BACK_Z = -12;
    static final int END_Z = 90;
    /** Hauteur des murs (au-dessus du sol). */
    static final int WALL_H = 26;
    /** Poupee et arbre. */
    static final double DOLL_X = 0.5;
    static final double DOLL_Z = 76.5;
    static final double TREE_X = 0.5;
    static final double TREE_Z = 84.5;
    /** Dortoir (murs en z HALL_Z1 et HALL_Z2, plafond en HALL_TOP). */
    static final int HALL_Z1 = -80;
    static final int HALL_Z2 = -40;
    static final int HALL_TOP = FY + 15;
    /** Boite reconstruite (dans les chunks forceload x -32..31, z -96..95). */
    static final int BX1 = -24, BX2 = 24, BY1 = FY - 4, BY2 = FY + 42, BZ1 = -88, BZ2 = 95;

    private static final int TOP = FY + WALL_H; // 126 : platelage de la passerelle
    private final Sink s;
    final List<String> warnings = new ArrayList<>();

    MapBuilder(Sink s) {
        this.s = s;
    }

    private void set(int x, int y, int z, String st) {
        s.set(x, y, z, B.id(st));
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, String st) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, st);
    }

    void build() {
        field();
        lines();
        walls();
        piers();
        cabins();
        stands();
        tree();
        hall();
        Stairs.fix(s, BX1, BY1, BZ1, BX2, BY2, BZ2);
        Connect.fix(s, BX1, BY1, BZ1, BX2, BY2, BZ2);
        check();
    }

    // ================================================================== piste (plate)

    private void field() {
        Palette ground = Palette.of(1231, "sand", 52, "smooth_sandstone", 12, "packed_mud", 15, "coarse_dirt", 12,
                "terracotta", 9).period(7);
        Noise wn = new Noise(4242);
        for (int x = -HW; x <= HW; x++) {
            for (int z = BACK_Z + 1; z <= END_Z - 1; z++) {
                set(x, FY - 1, z, "dirt");
                String top = ground.at(x, FY, z);
                // allee pietinee qui serpente au milieu (bande continue, pas de tirage par case)
                double mid = 0.5 + 4.5 * wn.noise2(z / 31.0, 0.3);
                if (z >= -10 && z <= FINISH_Z - 1 && Math.abs(x - mid) < 2.6 + 0.8 * wn.noise2(z / 9.0, 5.1)
                        && wn.uniform2(x / 4.0, z / 6.0) < 0.55) {
                    top = "minecraft:packed_mud";
                }
                if (Math.abs(x) == HW) top = "minecraft:smooth_stone"; // bordure peinte, au ras du sol
                set(x, FY, z, top);
            }
        }
        // reperes de distance (peints, a plat)
        for (int z = 11; z < FINISH_Z - 4; z += 11) {
            for (int side = -1; side <= 1; side += 2) {
                set(side * 13, FY, z, "white_terracotta");
                set(side * 12, FY, z, "white_terracotta");
            }
        }
        // sol de l'arbre : podzol, terre racinee, terre grossiere selon la distance + bruit
        Noise tn = new Noise(808);
        for (int x = -9; x <= 10; x++) {
            for (int z = 79; z <= END_Z - 1; z++) {
                double d = Math.hypot(x + 0.5 - TREE_X, z + 0.5 - TREE_Z) + 1.3 * tn.noise2(x / 4.0, z / 4.0);
                if (d < 3.2) set(x, FY, z, "podzol");
                else if (d < 5.2) set(x, FY, z, "rooted_dirt");
                else if (d < 7.5) set(x, FY, z, "coarse_dirt");
            }
        }
        // cercle autour de la poupee
        for (int x = -4; x <= 5; x++) {
            for (int z = 72; z <= 81; z++) {
                double d = Math.hypot(x + 0.5 - DOLL_X, z + 0.5 - DOLL_Z);
                if (d >= 2.6 && d <= 3.5) set(x, FY, z, "orange_terracotta");
                else if (d < 2.6) set(x, FY, z, "smooth_sandstone");
            }
        }
        // lumieres encastrees au ras du sol (aucun relief) : poupee et arbre
        int[][] lamps = {{-3, 73}, {4, 73}, {-7, 85}, {8, 84}, {0, 80}};
        for (int[] l : lamps) set(l[0], FY, l[1], "ochre_froglight");
    }

    /** Lignes rouge et blanc : depart et arrivee (a plat). */
    private void lines() {
        for (int x = -HW; x <= HW; x++) {
            for (int dz = 0; dz <= 1; dz++) {
                boolean red = ((x + dz) & 1) == 0;
                set(x, FY, START_Z + dz, red ? "red_concrete" : "smooth_quartz");
                set(x, FY, FINISH_Z + dz, red ? "red_concrete" : "smooth_quartz");
            }
        }
    }

    // ================================================================== murs-decors

    /** Repere d'un mur : u le long du mur, d la profondeur (0 = face cote piste, croissant vers l'exterieur). */
    private static final class Frame {
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
    }

    private void put(Frame f, int u, int d, int y, String st) {
        set(f.x(u, d), y, f.z(u, d), st);
    }

    private static final Frame WEST = new Frame(true, -HW - 1, -1, Dir.WEST);
    private static final Frame EAST = new Frame(true, HW + 1, 1, Dir.EAST);
    private static final Frame BACK = new Frame(false, BACK_Z, -1, Dir.NORTH);
    private static final Frame END = new Frame(false, END_Z, 1, Dir.SOUTH);

    private void walls() {
        paintedWall(WEST, BACK_Z + 1, END_Z - 1, 11, new int[]{-6, 8, 16, 24, 32, 40, 48, 56, 74, 82},
                new int[]{0, FINISH_Z}, new int[]{20, 52}, false, false);
        paintedWall(EAST, BACK_Z + 1, END_Z - 1, 22, new int[]{-6, 8, 16, 24, 32, 40, 48, 56, 74, 82},
                new int[]{0, FINISH_Z}, new int[]{28, 61}, false, false);
        paintedWall(BACK, -HW, HW, 33, new int[]{-12, -4, 5, 13}, new int[0], new int[0], true, true);
        paintedWall(END, -HW, HW, 44, new int[]{-11, -5, 6, 12}, new int[0], new int[0], false, false);
        // projecteurs braques sur l'arbre, sur la passerelle du fond
        projector(END, -9, 1, TOP + 1);
        projector(END, 10, 1, TOP + 1);
    }

    private static final class Cloud {
        final double[][] blobs; // {u, v, rx, ry}
        final double vb;

        Cloud(double[][] blobs, double vb) {
            this.blobs = blobs;
            this.vb = vb;
        }
    }

    private static boolean has(int[] a, int v) {
        for (int i : a) if (i == v) return true;
        return false;
    }

    private static boolean near(int[] a, int v, int r) {
        for (int i : a) if (Math.abs(i - v) <= r) return true;
        return false;
    }

    /**
     * Un mur peint en ciel, en 4 a 5 plans : d-1 corniche et chapiteaux, d0 pilastres / collines proches / coeurs de
     * nuages en saillie, d1 ciel / collines lointaines / bords des nuages, d2 ame en charpente, d3 dos du decor,
     * d4 poteaux et lisses exterieurs. Aucun pan du meme plan ne depasse l'ecart entre deux pilastres (10 au plus).
     */
    private void paintedWall(Frame f, int u1, int u2, long seed, int[] pil, int[] posts, int[] doors, boolean gate,
                             boolean symbols) {
        int len = u2 - u1 + 1;
        int vMax = 23; // y FY+1 .. FY+23 peints, FY+24 frise
        Noise hn = new Noise(seed);
        Noise fn = hn.derive(1);
        int[] hb = new int[len];
        int[] hf = new int[len];
        for (int i = 0; i < len; i++) {
            int u = u1 + i;
            hb[i] = (int) Math.max(2, Math.min(9, Math.round(5 + 3.6 * hn.fbm2(u / 19.0, 0.37, 3, 0.5, 2))));
            boolean blocked = near(pil, u, 1) || near(posts, u, 1) || near(doors, u, 2) || (gate && Math.abs(u - 0.5) < 4);
            int h = (int) Math.floor(1.2 + 3.8 * fn.fbm2(u / 8.0, 4.2, 2, 0.5, 2));
            hf[i] = blocked || h < 1 ? 0 : Math.min(h, 5);
        }
        // nuages : positions tirees une fois (formes), jamais de tirage de bloc par case
        Random r = new Random(seed * 31 + 7);
        List<Cloud> clouds = new ArrayList<>();
        int n = Math.max(2, len / 10);
        for (int k = 0; k < n * 3 && clouds.size() < n; k++) {
            double cu = u1 + 4 + r.nextDouble() * (len - 8);
            double cv = 11 + r.nextDouble() * 8;
            if (symbols && Math.abs(cu - 0.5) < 13 && cv < 19) continue;
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
                // pas de coeur en saillie contre un pilastre : sinon le plan d0 courrait d'un panneau a l'autre
                core[i][v] = cloud[i][v] && best <= 1 && !near(pil, u1 + i, 1) && !near(posts, u1 + i, 1);
            }
        }
        String bevelUp = B.stairs("smooth_quartz_stairs", f.out, false);
        String bevelDown = B.stairs("smooth_quartz_stairs", f.out, true);
        for (int i = 0; i < len; i++) {
            int u = u1 + i;
            // ame, dos du decor et fondation
            for (int y = FY - 1; y <= TOP - 1; y++) {
                put(f, u, 2, y, "spruce_planks");
                put(f, u, 3, y, "spruce_planks");
            }
            put(f, u, 0, FY - 1, "spruce_planks");
            put(f, u, 1, FY - 1, "spruce_planks");
            put(f, u, 0, FY, "polished_andesite");
            put(f, u, 1, FY, "polished_andesite");
            // peinture : ciel, collines, nuages
            for (int v = 1; v <= vMax; v++) {
                int y = FY + v;
                // ciel plus clair pres de l'horizon (laine), plus soutenu en haut (beton) ; limite ondulee par bruit
                double horizon = 9.5 + 2.5 * hn.noise2(u / 9.0, 17.3);
                String d1 = v <= hb[i] ? "cyan_terracotta" : v < horizon ? "light_blue_wool" : "light_blue_concrete";
                String d0 = null;
                if (v <= hf[i]) {
                    d0 = v <= hf[i] - 2 ? "green_wool" : "moss_block";
                } else if (cloud[i][v]) {
                    d1 = !cloud[i][v - 1] ? "snow_block" : "white_wool";
                    if (core[i][v]) d0 = "smooth_quartz";
                }
                put(f, u, 1, y, d1);
                if (d0 != null) put(f, u, 0, y, d0);
            }
            if (hf[i] > 0) put(f, u, 0, FY + hf[i] + 1, "moss_carpet");
            // biseaux des nuages en saillie (escaliers sur le dessus et le dessous des coeurs)
            for (int v = 2; v < vMax; v++) {
                if (!core[i][v]) continue;
                if (!core[i][v + 1] && cloud[i][v + 1]) put(f, u, 0, FY + v + 1, bevelUp);
                if (!core[i][v - 1] && v - 1 > hf[i] + 1) put(f, u, 0, FY + v - 1, bevelDown);
            }
            // frise, corniche, passerelle
            for (int d = 0; d <= 1; d++) {
                put(f, u, d, TOP - 2, "smooth_quartz");
                put(f, u, d, TOP - 1, "smooth_quartz");
            }
            put(f, u, -1, TOP - 1, B.stairs("smooth_quartz_stairs", f.out, true));
            put(f, u, 0, TOP, "smooth_quartz_slab[type=top]");
            put(f, u, 1, TOP, "spruce_slab[type=top]");
            put(f, u, 2, TOP, "spruce_slab[type=top]");
            put(f, u, 3, TOP, "smooth_quartz_slab[type=top]");
            put(f, u, 0, TOP + 1, "dark_oak_fence");
            put(f, u, 3, TOP + 1, "dark_oak_fence");
            // dos du decor : poteaux tous les 4, lisses horizontales
            if (Math.floorMod(u, 4) == 0) {
                for (int y = FY - 1; y <= TOP - 1; y++) put(f, u, 4, y, "stripped_dark_oak_log[axis=y]");
                put(f, u, 4, TOP, "dark_oak_slab[type=bottom]");
            } else {
                for (int y : new int[]{FY + 4, FY + 12, FY + 20}) put(f, u, 4, y, "dark_oak_fence");
            }
        }
        // pilastres (les poteaux de ligne sont des pilastres dont le bas est peint)
        int[] all = new int[pil.length + posts.length];
        System.arraycopy(pil, 0, all, 0, pil.length);
        System.arraycopy(posts, 0, all, pil.length, posts.length);
        java.util.Arrays.sort(all);
        for (int k = 0; k < all.length; k++) {
            int u = all[k];
            if (u < u1 || u > u2) continue;
            put(f, u, 0, FY + 1, "chiseled_quartz_block");
            for (int y = FY + 2; y <= FY + 21; y++) put(f, u, 0, y, "quartz_pillar[axis=y]");
            put(f, u, 0, FY + 22, "chiseled_quartz_block");
            put(f, u, 0, FY + 23, "quartz_pillar[axis=y]");
            put(f, u, -1, FY + 22, bevelDown);
            put(f, u, -1, FY + 23, "chiseled_quartz_block");
            put(f, u, -1, FY + 24, "chiseled_quartz_block");
            put(f, u, 0, TOP + 1, "quartz_pillar[axis=y]");
            put(f, u, 0, TOP + 2, "quartz_pillar[axis=y]");
            if (k % 2 == 0) put(f, u, 0, TOP + 3, "lantern[hanging=false]");
        }
        // poteaux de ligne (depart / arrivee) peints rouge et blanc
        for (int u : posts) {
            for (int y = FY + 1; y <= FY + 8; y++) {
                put(f, u, 0, y, ((y - FY - 1) / 2) % 2 == 0 ? "red_concrete" : "smooth_quartz");
            }
            put(f, u, 0, FY + 9, "chiseled_quartz_block");
            put(f, u, -1, FY + 9, "lantern[hanging=true]"); // lanterne pendue sous un corbeau
            put(f, u, -1, FY + 10, bevelDown);
        }
        // portes de service en retrait (porte en fer au plan d2, embrasure, encadrement)
        Dir in = f.out.opposite();
        for (int u : doors) {
            for (int y = FY + 1; y <= FY + 3; y++) {
                put(f, u - 1, 0, y, "smooth_quartz");
                put(f, u + 1, 0, y, "smooth_quartz");
            }
            put(f, u, 0, FY + 3, "smooth_quartz");
            for (int du = -1; du <= 1; du++) put(f, u + du, 0, FY + 4, "smooth_quartz_slab[type=bottom]");
            for (int d = 0; d <= 1; d++) {
                put(f, u, d, FY + 1, "air");
                put(f, u, d, FY + 2, "air");
            }
            put(f, u, 1, FY + 3, "spruce_planks");
            put(f, u, 2, FY + 1, B.door("iron_door", in, false, false));
            put(f, u, 2, FY + 2, B.door("iron_door", in, true, false));
        }
        if (gate) gate(f);
        if (symbols) symbols(f);
    }

    /** Grand portail a deux battants au centre du mur du fond (u 0 et 1). */
    private void gate(Frame f) {
        Dir in = f.out.opposite();
        for (int y = FY + 1; y <= FY + 4; y++) {
            put(f, -1, 0, y, "chiseled_quartz_block");
            put(f, 2, 0, y, "chiseled_quartz_block");
        }
        for (int u = 0; u <= 1; u++) {
            for (int d = 0; d <= 1; d++)
                for (int y = FY + 1; y <= FY + 3; y++) put(f, u, d, y, "air");
            put(f, u, 2, FY + 1, B.door("iron_door", in, false, u == 1));
            put(f, u, 2, FY + 2, B.door("iron_door", in, true, u == 1));
            put(f, u, 2, FY + 3, "iron_bars");
            put(f, u, 0, FY + 4, "smooth_quartz");
            put(f, u, 1, FY + 4, "smooth_quartz");
        }
        for (int u = -2; u <= 3; u++) put(f, u, 0, FY + 5, B.stairs("smooth_quartz_stairs", f.out, false));
        // socles pleins sous les lanternes (sur une marche, la moitie de la lanterne flottait)
        put(f, -1, 0, FY + 5, "chiseled_quartz_block");
        put(f, 2, 0, FY + 5, "chiseled_quartz_block");
        put(f, -1, 0, FY + 6, "lantern[hanging=false]");
        put(f, 2, 0, FY + 6, "lantern[hanging=false]");
    }

    /** Cercle, triangle et carre roses en relief sur le ciel du mur du fond. */
    private void symbols(Frame f) {
        String pk = "pink_wool";
        int vy = FY + 12; // centre vertical
        // cercle (u -8)
        for (int du = -3; du <= 3; du++)
            for (int dv = -3; dv <= 3; dv++) {
                double d = Math.hypot(du, dv);
                if (d >= 2.2 && d <= 3.2) put(f, -8 + du, 0, vy + dv, pk);
            }
        // triangle (u 0..1, pointe en haut)
        for (int dv = -2; dv <= 3; dv++) {
            int half = (3 - dv) / 2 + 1;
            for (int du = -half; du <= half + 1; du++) {
                boolean edge = dv == -2 || du == -half || du == half + 1;
                if (edge) put(f, du, 0, vy + dv, pk);
            }
        }
        // carre (u 9)
        for (int du = -3; du <= 3; du++)
            for (int dv = -3; dv <= 3; dv++)
                if (Math.abs(du) == 3 || Math.abs(dv) == 3) put(f, 9 + du, 0, vy + dv, pk);
    }

    /** Projecteur : pied en muret, tete en lanterne marine avec dos noir et visiere. */
    private void projector(Frame f, int u, int d, int y) {
        put(f, u, d, y, "polished_blackstone_wall");
        put(f, u, d, y + 1, "sea_lantern");
        put(f, u, d + 1, y + 1, "black_concrete");
        put(f, u, d, y + 2, B.trapdoor("iron_trapdoor", f.out.opposite(), false, false));
    }

    // ================================================================== piliers d'angle et mats

    private void piers() {
        pier(HW + 1, BACK_Z - 4, 1);
        pier(-HW - 5, BACK_Z - 4, 1);
        pier(HW + 1, END_Z, -1);
        pier(-HW - 5, END_Z, -1);
    }

    /** Pilier d'angle 5x5 (x0..x0+4, z0..z0+4) surmonte d'un mat a projecteurs tourne vers la piste (dz). */
    private void pier(int x0, int z0, int dz) {
        for (int x = x0; x <= x0 + 4; x++)
            for (int z = z0; z <= z0 + 4; z++)
                for (int y = FY - 1; y <= TOP; y++) {
                    boolean edge = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    String st = "smooth_quartz";
                    if (edge && (y == FY + 4 || y == FY + 12 || y == FY + 20)) st = "white_terracotta";
                    if (edge && y == TOP) st = "chiseled_quartz_block";
                    if (y <= FY && edge) st = "polished_andesite";
                    set(x, y, z, st);
                }
        for (int x = x0 - 1; x <= x0 + 5; x++)
            for (int z = z0 - 1; z <= z0 + 5; z++) {
                boolean ring = x == x0 - 1 || x == x0 + 5 || z == z0 - 1 || z == z0 + 5;
                if (ring) {
                    // corniche en debord du pilier (sauf cote piste et cote murs, deja couverts)
                    if (s.isAir(x, TOP, z)) set(x, TOP, z, "smooth_quartz_slab[type=top]");
                } else set(x, TOP + 1, z, "smooth_quartz_slab[type=bottom]");
            }
        int cx = x0 + 2, cz = z0 + 2;
        for (int y = TOP + 2; y <= TOP + 9; y++) set(cx, y, cz, "polished_blackstone_wall");
        int hy = TOP + 11;
        for (int x = cx - 2; x <= cx + 2; x++)
            for (int z = cz; z != cz + 2 * dz; z += dz)
                set(x, hy - 1, z, "polished_blackstone_slab[type=top]");
        for (int y = hy; y <= hy + 1; y++) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                set(x, y, cz + dz, "sea_lantern");
                set(x, y, cz, "black_concrete");
            }
            for (int z = cz; z != cz + 2 * dz; z += dz) {
                set(cx - 2, y, z, "black_concrete");
                set(cx + 2, y, z, "black_concrete");
            }
        }
        Dir face = dz > 0 ? Dir.SOUTH : Dir.NORTH;
        for (int x = cx - 2; x <= cx + 2; x++)
            for (int z = cz; z != cz + 2 * dz; z += dz)
                set(x, hy + 2, z, B.trapdoor("iron_trapdoor", face, false, false));
    }

    // ================================================================== cabines de gardes

    private void cabins() {
        cabin(WEST, 8, false);
        cabin(WEST, 41, true);
        cabin(WEST, 70, false);
        cabin(EAST, 17, false);
        cabin(EAST, 50, false);
        cabin(EAST, 79, true);
    }

    /** Cabine 5x5 sur la passerelle (u c-2..c+2, d 0..4), vitree cote piste, garde rose au masque noir. */
    private void cabin(Frame f, int c, boolean twoGuards) {
        int y0 = TOP + 1;
        Dir in = f.out.opposite();
        for (int u = c - 2; u <= c + 2; u++) {
            for (int d = 0; d <= 4; d++) put(f, u, d, TOP, "smooth_stone");
            put(f, u, 4, TOP - 1, B.stairs("dark_oak_stairs", in, true)); // corbeaux sous le porte-a-faux
            for (int d = 0; d <= 4; d++)
                for (int y = y0; y <= y0 + 3; y++) put(f, u, d, y, "air");
        }
        for (int u = c - 2; u <= c + 2; u++) {
            for (int d = 0; d <= 4; d++) {
                boolean sideU = u == c - 2 || u == c + 2;
                boolean sideD = d == 0 || d == 4;
                if (!sideU && !sideD) continue;
                for (int y = y0; y <= y0 + 3; y++) {
                    String st = "light_gray_concrete";
                    if (sideU && sideD) st = "polished_blackstone";
                    else if (y == y0 + 3) st = "pink_terracotta";
                    else if (d == 0 && (y == y0 + 1 || y == y0 + 2)) st = "glass_pane";
                    else if (d == 4 && u == c && y == y0 + 1) st = "glass_pane";
                    else if (sideU && (d == 1 || d == 2) && y <= y0 + 1) st = "air"; // passages de la passerelle
                    put(f, u, d, y, st);
                }
            }
            if (u > c - 2 && u < c + 2) put(f, u, -1, y0, "smooth_stone_slab[type=top]"); // appui exterieur
        }
        // gardes (silhouettes) et lanterne
        int[] gu = twoGuards ? new int[]{c - 1, c + 1} : new int[]{c};
        for (int u : gu) {
            put(f, u, 3, y0, "pink_concrete");
            put(f, u, 3, y0 + 1, "pink_concrete");
            put(f, u, 3, y0 + 2, "black_concrete");
        }
        put(f, twoGuards ? c : c - 1, 1, y0 + 3, "lantern[hanging=true]");
        // toit plat en debord avec acrotere, projecteur et antenne
        for (int u = c - 3; u <= c + 3; u++)
            for (int d = -1; d <= 5; d++) {
                boolean ring = u == c - 3 || u == c + 3 || d == -1 || d == 5;
                put(f, u, d, y0 + 4, ring ? "polished_blackstone_slab[type=top]" : "gray_concrete");
                if (ring) put(f, u, d, y0 + 5, "polished_blackstone_slab[type=bottom]");
            }
        projector(f, c, 1, y0 + 5);
        put(f, c + 2, 4, y0 + 5, "iron_bars");
        put(f, c + 2, 4, y0 + 6, "lightning_rod");
    }

    // ================================================================== gradins des qualifies

    private void stands() {
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 4; i <= 14; i++) {
                int x = side * i;
                boolean end = i == 4 || i == 14;
                if (end) {
                    // joues des gradins : planches + rambarde qui suit la pente (3 marches de 1 jusqu'aux places)
                    set(x, FY + 1, FINISH_Z + 3, "spruce_planks");
                    set(x, FY + 2, FINISH_Z + 3, "spruce_fence");
                    fill(x, FY + 1, FINISH_Z + 4, x, FY + 2, FINISH_Z + 4, "spruce_planks");
                    set(x, FY + 3, FINISH_Z + 4, "spruce_fence");
                    fill(x, FY + 1, FINISH_Z + 5, x, FY + 3, FINISH_Z + 5, "spruce_planks");
                    set(x, FY + 4, FINISH_Z + 5, "spruce_fence");
                    fill(x, FY + 1, FINISH_Z + 6, x, FY + 3, FINISH_Z + 6, "spruce_planks");
                    set(x, FY + 4, FINISH_Z + 6, "spruce_fence");
                    fill(x, FY + 1, FINISH_Z + 7, x, FY + 6, FINISH_Z + 7, "stripped_spruce_log[axis=y]");
                    // poteau avant de l'auvent
                    fill(x, FY + 1, FINISH_Z + 2, x, FY + 7, FINISH_Z + 2, "spruce_fence");
                    continue;
                }
                // 3 marches de 1 (y FY+1, +2, +3) : la derniere arrive au niveau des places (on s'y tient en FY+4)
                // (pas de premiere marche en x +-13 : les gardes de l'epreuve s'y tiennent, en FINISH_Z + 3.5)
                if (i != 13) set(x, FY + 1, FINISH_Z + 3, B.stairs("spruce_stairs", Dir.SOUTH, false));
                set(x, FY + 1, FINISH_Z + 4, "spruce_planks");
                set(x, FY + 2, FINISH_Z + 4, B.stairs("spruce_stairs", Dir.SOUTH, false));
                fill(x, FY + 1, FINISH_Z + 5, x, FY + 2, FINISH_Z + 5, "spruce_planks");
                set(x, FY + 3, FINISH_Z + 5, B.stairs("spruce_stairs", Dir.SOUTH, false));
                fill(x, FY + 1, FINISH_Z + 6, x, FY + 2, FINISH_Z + 6, "spruce_planks");
                set(x, FY + 3, FINISH_Z + 6, "spruce_slab[type=top]"); // places : on s'y tient en FY+4
                fill(x, FY + 1, FINISH_Z + 7, x, FY + 3, FINISH_Z + 7, "spruce_planks");
                set(x, FY + 4, FINISH_Z + 7, i == 9 ? "stripped_spruce_log[axis=y]" : "spruce_fence");
                if (i == 9) fill(x, FY + 5, FINISH_Z + 7, x, FY + 6, FINISH_Z + 7, "stripped_spruce_log[axis=y]");
            }
            // auvent raye rose et blanc (dalles basses : lanternes et lambrequin en trappes y pendent sans vide)
            for (int i = 3; i <= 15; i++) {
                int x = side * i;
                boolean pink = ((i - 3) / 2) % 2 == 0;
                for (int z = FINISH_Z + 1; z <= FINISH_Z + 8; z++)
                    set(x, FY + 8, z, pink ? "cherry_slab[type=bottom]" : "smooth_quartz_slab[type=bottom]");
                set(x, FY + 7, FINISH_Z + 1, B.trapdoor(pink ? "cherry_trapdoor" : "birch_trapdoor", Dir.SOUTH, true, true));
            }
            for (int i : new int[]{4, 9, 14}) set(side * i, FY + 7, FINISH_Z + 7, "stripped_spruce_log[axis=y]");
            // lanternes sous l'auvent
            set(side * 7, FY + 7, FINISH_Z + 4, "lantern[hanging=true]");
            set(side * 11, FY + 7, FINISH_Z + 4, "lantern[hanging=true]");
        }
    }

    // ================================================================== arbre sec (point focal)

    private void tree() {
        String wood = "dark_oak_wood";
        double cx = TREE_X - 0.5;
        double cz = TREE_Z - 0.5;
        int base = FY + 1;
        int height = 18;
        double ox = 0, oz = 0;
        double[] trunkX = new double[height];
        double[] trunkZ = new double[height];
        for (int i = 0; i < height; i++) {
            if (i > 0 && i % 4 == 0) {
                ox += i < 9 ? 0.7 : -0.6;
                oz += (i / 4) % 2 == 0 ? -0.35 : 0.3;
            }
            double rad = i == 0 ? 2.6 : i == 1 ? 2.1 : 1.8 - 0.9 * i / height;
            trunkX[i] = cx + ox;
            trunkZ[i] = cz + oz;
            disk(cx + ox, cz + oz, base + i, rad, wood);
        }
        // racines : courent au sol puis s'enfoncent au ras du sol
        Random r = new Random(77);
        for (int k = 0; k < 7; k++) {
            double a = k * Math.PI * 2 / 7 + r.nextDouble() * 0.5;
            int len = 4 + r.nextInt(3);
            for (int st = 2; st <= len; st++) {
                int x = (int) Math.round(cx + Math.cos(a) * st);
                int z = (int) Math.round(cz + Math.sin(a) * st);
                if (z < 80 || z > END_Z - 1) break;
                if (st <= len - 2) set(x, base, z, wood);
                else set(x, FY, z, wood);
            }
        }
        // branches : surtout vers les cotes (la poupee est devant, le mur derriere)
        double[] ang = {0.15, Math.PI - 0.2, 0.75, Math.PI - 0.8, -0.45, Math.PI + 0.5, 1.6};
        int[] hs = {8, 9, 11, 12, 13, 14, 16};
        for (int k = 0; k < ang.length; k++) {
            int i = Math.min(height - 1, hs[k]);
            branch(r, trunkX[i], base + i, trunkZ[i], ang[k], 7 + r.nextInt(4), 2, wood, true);
        }
        // cime : quelques rameaux
        int ti = height - 1;
        branch(r, trunkX[ti], base + ti, trunkZ[ti], 0.4, 4, 0, wood, false);
        branch(r, trunkX[ti], base + ti, trunkZ[ti], Math.PI - 0.4, 4, 0, wood, false);
        // buissons morts en grappes au pied
        Noise vn = new Noise(99);
        for (int x = -9; x <= 10; x++)
            for (int z = 79; z <= END_Z - 1; z++) {
                if (!s.isAir(x, base, z)) continue;
                double d = Math.hypot(x + 0.5 - TREE_X, z + 0.5 - TREE_Z);
                if (d < 2.5 || d > 8.5 || Math.abs(x - 0.5) > 9) continue;
                double u = vn.uniform2(x / 2.5, z / 2.5);
                if (u > 0.86) set(x, base, z, "dead_bush");
                else if (u > 0.74) set(x, base, z, "short_dry_grass");
                else if (u < 0.08) set(x, base, z, "leaf_litter[facing=north,segment_amount=3]");
            }
    }

    private void disk(double cx, double cz, int y, double rad, String b) {
        for (int x = (int) Math.floor(cx - rad - 1); x <= (int) Math.ceil(cx + rad + 1); x++)
            for (int z = (int) Math.floor(cz - rad - 1); z <= (int) Math.ceil(cz + rad + 1); z++) {
                double dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= rad * rad + 0.2) set(x, y, z, b);
            }
    }

    private void branch(Random r, double x, double y, double z, double a, int len, int depth, String wood, boolean thick) {
        for (int st = 0; st < len; st++) {
            x += Math.cos(a);
            z += Math.sin(a) * 0.55; // ecrase en z : la place manque entre la poupee et le mur
            y += 0.35 + r.nextDouble() * 0.4;
            a += (r.nextDouble() - 0.5) * 0.45;
            int bx = (int) Math.floor(x + 0.5);
            int bz = (int) Math.floor(z + 0.5);
            int by = (int) Math.floor(y);
            int zMin = by < FY + 11 ? 80 : 78;
            if (Math.abs(bx) > HW - 2 || bz < zMin || bz > END_Z - 1 || by > FY + WALL_H - 3) return;
            boolean tip = st >= len - 2 && depth == 0;
            set(bx, by, bz, tip ? "dark_oak_fence" : wood);
            if (thick && st < 3) set(bx, by - 1, bz, wood);
            if (depth > 0 && st == len / 2) {
                branch(r, x, y, z, a + (r.nextBoolean() ? 0.9 : -0.9), Math.max(3, len / 2), depth - 1, wood, false);
            }
        }
        if (depth > 0) {
            branch(r, x, y, z, a + 0.5, 3, 0, wood, false);
        }
    }

    // ================================================================== dortoir

    private void hall() {
        int x1 = -HW, x2 = HW, z1 = HALL_Z1 + 1, z2 = HALL_Z2 - 1, top = HALL_TOP;
        // sol : dalles d'andesite, allee centrale en quartz
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, FY - 1, z, "stone");
                set(x, FY, z, Math.abs(x) <= 2 ? "smooth_quartz" : "polished_andesite");
                for (int y = FY + 1; y < top; y++) set(x, y, z, "air");
            }
        // murs (epaisseur 2) : soubassement vert, haut blanc, corniche ; pilastres a l'interieur et a l'exterieur
        for (int x = x1 - 2; x <= x2 + 2; x++)
            for (int z = z1 - 2; z <= z2 + 2; z++) {
                boolean wall = x < x1 || x > x2 || z < z1 || z > z2;
                if (!wall) continue;
                for (int y = FY - 1; y <= top; y++) {
                    String st;
                    if (y <= FY) st = "polished_andesite";
                    else if (y <= FY + 3) st = "green_terracotta";
                    else if (y < top - 1) st = "white_concrete";
                    else st = "smooth_stone";
                    set(x, y, z, st);
                }
            }
        // pilastres interieurs et exterieurs
        for (int z = z1 + 3; z <= z2 - 2; z += 6) {
            for (int side = -1; side <= 1; side += 2) {
                // pas de pilastre devant la premiere marche des volees de la mezzanine (x +-15, z -76)
                if (z != z1 + 3) hallPilaster(side * HW, z, side > 0 ? Dir.EAST : Dir.WEST);
                outerPost(side * (HW + 3), z);
            }
        }
        for (int x : new int[]{-10, -4, 5, 11}) {
            hallPilaster(x, z1, Dir.NORTH);
            hallPilaster(x, z2, Dir.SOUTH);
            outerPost(x, z1 - 3);
            outerPost(x, z2 + 3);
        }
        // plafond avec dalles lumineuses, toit a acrotere
        for (int x = x1 - 2; x <= x2 + 2; x++)
            for (int z = z1 - 2; z <= z2 + 2; z++) {
                boolean lamp = x >= x1 && x <= x2 && z >= z1 && z <= z2 && Math.floorMod(x, 8) == 0
                        && Math.floorMod(z - z1, 6) == 3;
                set(x, top, z, lamp ? "sea_lantern" : "smooth_stone");
                boolean ring = x == x1 - 2 || x == x2 + 2 || z == z1 - 2 || z == z2 + 2;
                if (ring) set(x, top + 1, z, "smooth_stone_slab[type=bottom]");
            }
        // lanternes suspendues dans l'allee et les passages
        for (int z = z1 + 6; z <= z2 - 4; z += 6) {
            hang(0, z, top, 3);
            hang(-11, z + 3, top, 2);
            hang(11, z + 3, top, 2);
        }
        // tapis verts dans les passages
        for (int z = z1 + 6; z <= z2 - 2; z++) {
            set(-11, FY + 1, z, "green_carpet");
            set(11, FY + 1, z, "green_carpet");
        }
        bunks();
        mezzanine();
        piggyBank();
    }

    /** Tirelire geante (Squid Game) : sphere de verre suspendue au plafond, remplie d'or par le jeu a chaque elimine. */
    static final int PIG_X = 0, PIG_Y = FY + 11, PIG_Z = -64;
    static final double PIG_R = 2.4;

    private void piggyBank() {
        for (int x = PIG_X - 3; x <= PIG_X + 3; x++)
            for (int y = PIG_Y - 3; y <= PIG_Y + 3; y++)
                for (int z = PIG_Z - 3; z <= PIG_Z + 3; z++) {
                    double d = Math.sqrt((x - PIG_X) * (x - PIG_X) + (y - PIG_Y) * (y - PIG_Y) + (z - PIG_Z) * (z - PIG_Z));
                    if (d <= PIG_R + 0.5 && d > PIG_R - 0.5) set(x, y, z, "glass");
                    else if (d <= PIG_R - 0.5) set(x, y, z, "air");
                }
        set(PIG_X, PIG_Y - 3, PIG_Z, "gold_block");
        for (int y = PIG_Y + 3; y < HALL_TOP; y++) set(PIG_X, y, PIG_Z, "iron_chain[axis=y]");
    }

    /** Cases interieures de la tirelire, du bas vers le haut (le jeu y pose de l'or). */
    static java.util.List<int[]> piggyCells() {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        for (int y = PIG_Y - 3; y <= PIG_Y + 3; y++)
            for (int x = PIG_X - 3; x <= PIG_X + 3; x++)
                for (int z = PIG_Z - 3; z <= PIG_Z + 3; z++) {
                    double d = Math.sqrt((x - PIG_X) * (x - PIG_X) + (y - PIG_Y) * (y - PIG_Y) + (z - PIG_Z) * (z - PIG_Z));
                    if (d <= PIG_R - 0.5) l.add(new int[]{x, y, z});
                }
        return l;
    }

    /** Pilastre interieur pose en (x, z), contre le mur situe du cote {@code out}. */
    private void hallPilaster(int x, int z, Dir out) {
        set(x, FY + 1, z, "polished_andesite");
        for (int y = FY + 2; y <= HALL_TOP - 3; y++) set(x, y, z, "smooth_stone");
        set(x, HALL_TOP - 2, z, B.stairs("polished_andesite_stairs", out, true));
        set(x, HALL_TOP - 1, z, "polished_andesite");
    }

    private void outerPost(int x, int z) {
        for (int y = FY - 1; y <= HALL_TOP; y++) set(x, y, z, y <= FY + 3 ? "polished_andesite" : "smooth_stone");
    }

    private void hang(int x, int z, int top, int chain) {
        for (int i = 1; i <= chain; i++) set(x, top - i, z, "iron_chain[axis=y]");
        set(x, top - chain - 1, z, "lantern[hanging=true]");
    }

    /**
     * Lits superposes sur 4 niveaux (y FY+1, +4, +7, +10) : deux colonnes contre les murs (x +-13..14) et deux ilots
     * (x +-8..9). Montants verts et echelles tous les 4, garde-corps en barreaux, passerelles roses entre ilots et murs.
     */
    private void bunks() {
        int zA = HALL_Z1 + 7, zB = HALL_Z2 - 5; // -73 .. -45
        for (int side = -1; side <= 1; side += 2) {
            Dir outward = side > 0 ? Dir.EAST : Dir.WEST;
            Dir inward = outward.opposite();
            int[][] cols = {{side * 14, side * 13}, {side * 9, side * 8}}; // {tete, pied}
            for (int c = 0; c < 2; c++) {
                int head = cols[c][0], foot = cols[c][1];
                for (int z = zA; z <= zB; z++) {
                    boolean post = Math.floorMod(z - zA, 4) == 0;
                    if (post) {
                        for (int y = FY + 1; y <= FY + 12; y++) {
                            set(head, y, z, "green_terracotta");
                            set(foot, y, z, "green_terracotta");
                        }
                        set(head, FY + 13, z, "green_terracotta");
                        // echelle cote passage
                        int lx = c == 0 ? foot - side : head + side;
                        Dir lf = c == 0 ? inward : outward;
                        for (int y = FY + 1; y <= FY + 11; y++) set(lx, y, z, B.state("ladder", "facing", lf.id()));
                        continue;
                    }
                    for (int t = 0; t < 4; t++) {
                        int y = FY + 1 + 3 * t;
                        if (t > 0) {
                            set(head, y - 1, z, "spruce_slab[type=top]");
                            set(foot, y - 1, z, "spruce_slab[type=top]");
                        }
                        String bed = Math.floorMod(z - zA, 4) == 2 && t % 2 == 1 ? "lime_bed" : "white_bed";
                        set(foot, y, z, B.state(bed, "part", "foot", "facing", outward.id()));
                        set(head, y, z, B.state(bed, "part", "head", "facing", outward.id()));
                        if (t > 0) {
                            // garde-corps au pied (vers l'allee) et, pour les ilots, cote tete
                            set(foot - side, y, z, "iron_bars");
                            if (c == 1) set(head + side, y, z, "iron_bars");
                        }
                    }
                    set(head, FY + 13, z, "spruce_slab[type=bottom]");
                    set(foot, FY + 13, z, "spruce_slab[type=bottom]");
                }
            }
            // passerelles roses ilot <-> mur au 3e niveau (y FY+6), au milieu d'une travee sur deux
            for (int z = zA + 2; z <= zB; z += 8) {
                for (int i = 10; i <= 12; i++) {
                    int x = side * i;
                    set(x, FY + 6, z, "cherry_slab[type=top]");
                    set(x, FY + 7, z, "air");
                    set(x, FY + 7, z - 1, "cherry_fence");
                    set(x, FY + 7, z + 1, "cherry_fence");
                }
                set(side * 9, FY + 7, z, "air");
                set(side * 8, FY + 7, z, "air"); // pied du lit dont la tete vient d'etre retiree (sinon demi-lit orphelin)
                set(side * 13 - side, FY + 7, z, "air");
                set(side * 12, FY + 7, z, "air");
                set(side * 10, FY + 7, z, "air");
            }
        }
    }

    /** Mezzanine rose au fond (z -79..-77, plancher en y FY+8), deux volees d'escaliers, porte de sortie. */
    private void mezzanine() {
        int zf = HALL_Z1 + 3; // -77 : poutre de rive
        int my = FY + 8;
        for (int x = -HW; x <= HW; x++) {
            for (int z = HALL_Z1 + 1; z < zf; z++)
                if (s.isAir(x, my, z)) set(x, my, z, "cherry_slab[type=top]"); // les pilastres traversent
            set(x, my, zf, "stripped_cherry_log[axis=x]");
            set(x, my + 1, zf, "cherry_fence");
        }
        for (int x : new int[]{-12, -6, 6, 12}) fill(x, FY + 1, zf, x, my - 1, zf, "pink_concrete");
        // volees : est (monte vers l'ouest) et ouest (monte vers l'est), paliers roses
        for (int side = -1; side <= 1; side += 2) {
            Dir up = side > 0 ? Dir.WEST : Dir.EAST;
            for (int k = 0; k <= 7; k++) {
                int x = side * (14 - k);
                int y = FY + 1 + k;
                for (int z = zf + 1; z <= zf + 2; z++) {
                    if (y > FY + 1) fill(x, FY + 1, z, x, y - 1, z, "pink_concrete");
                    set(x, y, z, B.stairs("cherry_stairs", up, false));
                }
                set(x, y + 1, zf + 3, "cherry_fence");
            }
            for (int i = 5; i <= 6; i++) {
                int x = side * i;
                for (int z = zf + 1; z <= zf + 2; z++) {
                    fill(x, FY + 1, z, x, my - 1, z, "pink_concrete");
                    set(x, my, z, "cherry_planks");
                }
                set(x, my + 1, zf + 3, "cherry_fence");
                set(x, my + 1, zf, "air"); // acces a la mezzanine
            }
            set(side * 4, my + 1, zf + 1, "cherry_fence");
            set(side * 4, my + 1, zf + 2, "cherry_fence");
        }
        // porte de sortie au centre du mur du fond
        int wz = HALL_Z1;
        set(0, my + 1, wz, B.door("iron_door", Dir.SOUTH, false, false));
        set(0, my + 2, wz, B.door("iron_door", Dir.SOUTH, true, false));
        for (int y = my + 1; y <= my + 3; y++) {
            set(-1, y, wz + 1, "polished_andesite");
            set(1, y, wz + 1, "polished_andesite");
        }
        set(0, my + 3, wz + 1, "polished_andesite_slab[type=top]");
        set(-2, my + 3, wz + 1, "lantern[hanging=false]");
        set(2, my + 3, wz + 1, "lantern[hanging=false]");
        set(-2, my + 2, wz + 1, "polished_andesite_slab[type=top]");
        set(2, my + 2, wz + 1, "polished_andesite_slab[type=top]");
    }

    // ================================================================== controles

    private void check() {
        // piste plate et degagee : sol plein, rien jusqu'a 4 blocs au-dessus, de derriere le depart a l'arrivee
        int bad = 0;
        for (int x = -HW; x <= HW; x++)
            for (int z = BACK_Z + 1; z <= END_Z - 1; z++) {
                String g = s.get(x, FY, z);
                String n = B.name(g);
                if (!B.isSolid(g) || n.contains("path") || n.contains("farmland") || n.equals("mud")
                        || n.equals("soul_sand") || n.contains("slab") || n.contains("stairs")) {
                    if (bad++ < 5) warnings.add("sol non plein en " + x + " " + FY + " " + z + " : " + g);
                }
                if (z <= FINISH_Z + 1) {
                    for (int y = FY + 1; y <= FY + 5; y++)
                        if (!s.isAir(x, y, z) && bad++ < 5) warnings.add("piste encombree en " + x + " " + y + " " + z + " : " + s.get(x, y, z));
                }
            }
        // poupee : de l'air autour d'elle jusqu'a sa tete
        for (int x = -1; x <= 2; x++)
            for (int z = 75; z <= 78; z++)
                for (int y = FY + 1; y <= FY + 8; y++)
                    if (!s.isAir(x, y, z) && bad++ < 8) warnings.add("place de la poupee encombree en " + x + " " + y + " " + z);
        // places des gradins : on s'y tient en FY+4, z FINISH_Z+6, x +-6..13
        for (int side = -1; side <= 1; side += 2)
            for (int i = 6; i <= 13; i++) {
                int x = side * i;
                String g = s.get(x, FY + 3, FINISH_Z + 6);
                if (!B.isSolid(g) && !"top".equals(B.prop(g, "type"))) warnings.add("place de gradin sans sol en " + x);
                if (!s.isAir(x, FY + 4, FINISH_Z + 6) || !s.isAir(x, FY + 5, FINISH_Z + 6)) warnings.add("place de gradin encombree en " + x);
            }
        // dortoir : point d'apparition et panneau des regles
        for (int y = FY + 1; y <= FY + 3; y++)
            if (!s.isAir(0, y, -61)) warnings.add("apparition du dortoir encombree en y " + y);
        if (!B.isSolid(s.get(0, FY, -61))) warnings.add("apparition du dortoir sans sol");
        for (int y = FY + 4; y <= FY + 8; y++)
            for (int x = -2; x <= 3; x++)
                if (!s.isAir(x, y, -44)) warnings.add("panneau des regles cache en " + x + " " + y);
        // aucun mur ne traverse le point des spectateurs
        if (!s.isAir(0, FY + 18, 22)) warnings.add("point des spectateurs dans un bloc");
        Set<String> seen = new HashSet<>();
        for (String w : warnings) seen.add(w);
        if (seen.size() != warnings.size()) warnings.add("(avertissements en double)");
    }
}
