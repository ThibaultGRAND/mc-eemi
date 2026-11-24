package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Check;
import fr.eemicraft.murder.build.RecordingSink;
import fr.eemicraft.murder.build.SchemWriter;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.TreeSet;

/**
 * Test hors serveur de la map Manoir-bibliotheque (non embarque dans le jar) :
 * construit la map en memoire, ecrit un .schem et la liste des etats, verifie l'accessibilite a pied de toutes
 * les apparitions et de tous les points d'or, cherche les culs-de-sac, dessine un plan par niveau.
 * Usage : java -cp classes;test fr.eemicraft.murder.TestManoir <dossier de sortie>
 */
public final class TestManoir {
    static final int PASS = 1, CLIMB = 2;

    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "out");
        out.mkdirs();
        long t0 = System.currentTimeMillis();
        RecordingSink s = new RecordingSink();
        Manoir.build(s);
        long t1 = System.currentTimeMillis();
        System.out.println("Construction en memoire : " + (t1 - t0) + " ms, " + s.operations() + " operations, "
                + s.count() + " blocs, " + s.paletteSize() + " etats, bornes " + java.util.Arrays.toString(s.bounds()));
        // etats
        TreeSet<String> states = new TreeSet<>();
        for (int i = 1; i < s.paletteSize(); i++) states.add(s.state(i));
        try (PrintWriter w = new PrintWriter(new File(out, "mm_bibliotheque.states.txt"), StandardCharsets.UTF_8)) {
            for (String st : states) w.println(st);
        }
        // schem : boite de jeu + 4 (sans la ceinture boisee) et tout
        RecordingSink play = new RecordingSink();
        for (int x = Manoir.BX1; x <= Manoir.BX2; x++)
            for (int z = Manoir.BZ1; z <= Manoir.BZ2; z++)
                for (int y = 58; y <= Manoir.Y_HI; y++) {
                    int id = s.id(x, y, z);
                    if (id != 0) play.set(x, y, z, s.state(id));
                }
        SchemWriter.write(play, new File(out, "mm_bibliotheque.schem"));
        SchemWriter.write(s, new File(out, "mm_bibliotheque_tout.schem"));
        System.out.println(Check.run(play));
        mixingTally(play);

        // ---------------------------------------------------------------- accessibilite
        int x1 = Manoir.BX1, x2 = Manoir.BX2, z1 = Manoir.BZ1, z2 = Manoir.BZ2, y0 = 58, y1 = Manoir.MAX_Y;
        int nx = x2 - x1 + 1, ny = y1 - y0 + 1, nz = z2 - z1 + 1;
        byte[] kind = new byte[nx * ny * nz];
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y0; y <= y1; y++) kind[((x - x1) * ny + (y - y0)) * nz + (z - z1)] = (byte) kindOf(s, x, y, z);
        Reach r = new Reach(kind, x1, y0, z1, nx, ny, nz);
        int[][] seeds = Manoir.SPAWNS;
        int n = r.run(seeds);
        System.out.println("Positions atteintes a pied : " + n);
        int bad = 0;
        for (int[] p : Manoir.SPAWNS) bad += check(r, s, p, "apparition");
        for (int[] p : Manoir.GOLD) bad += check(r, s, p, "or");
        // chaque apparition atteint toutes les autres (composante unique)
        Reach r1 = new Reach(kind, x1, y0, z1, nx, ny, nz);
        r1.run(new int[][]{Manoir.SPAWNS[0]});
        for (int[] p : Manoir.SPAWNS)
            if (!r1.reached(p[0], p[1], p[2])) {
                System.out.println("NON RELIEE a l'apparition 0 : " + java.util.Arrays.toString(p));
                bad++;
            }
        for (int[] p : Manoir.GOLD)
            if (!r1.reached(p[0], p[1], p[2])) {
                System.out.println("OR NON RELIE a l'apparition 0 : " + java.util.Arrays.toString(p));
                bad++;
            }
        System.out.println(Manoir.SPAWNS.length + " apparitions, " + Manoir.GOLD.length + " points d'or, " + bad + " probleme(s)");
        // positions debout atteintes : par niveau, hors boite, sous le sol mortel
        int[] perY = new int[ny];
        int below = 0, maxY = 0;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y0; y <= y1; y++)
                    if (r1.reached(x, y, z) && r1.supported(x, y, z)) {
                        perY[y - y0]++;
                        if (y < Manoir.MIN_Y - 1) below++;
                        maxY = Math.max(maxY, y);
                    }
        StringBuilder sb = new StringBuilder("Positions debout par hauteur des pieds :");
        for (int i = 0; i < ny; i++) if (perY[i] > 0) sb.append(' ').append(y0 + i).append('=').append(perY[i]);
        System.out.println(sb);
        System.out.println("Pieds les plus hauts atteints : " + maxY + " (max config " + Manoir.MAX_Y + "), positions sous le sol mortel : " + below);
        // culs-de-sac : graphe des positions debout, on epluche les noeuds de degre <= 1
        deadEnds(r1, x1, x2, z1, z2, y0, y1);
        for (int fy : new int[]{Manoir.G + 1, Manoir.F + 1, Manoir.U + 1}) deadEnds(r1, x1, x2, z1, z2, fy, fy);
        deadEnds(r1, -32, 45, -24, 18, Manoir.F + 1, Manoir.F + 1);
        deadEnds(r1, -32, 45, -24, 18, Manoir.U + 1, Manoir.U + 1);
        // plans
        plan(s, r1, Manoir.F + 1, new File(out, "mm_bibliotheque_rdc.png"));
        plan(s, r1, Manoir.U + 1, new File(out, "mm_bibliotheque_etage.png"));
        plan(s, r1, Manoir.F + 1, new File(out, "mm_bibliotheque_rdc_zoom.png"), -38, -25, 46, 19, 9);
        plan(s, r1, Manoir.U + 1, new File(out, "mm_bibliotheque_etage_zoom.png"), -38, -25, 46, 19, 9);
        section(s, -12, new File(out, "mm_bibliotheque_coupe_z-12.png"), true);
        section(s, 0, new File(out, "mm_bibliotheque_coupe_x0.png"), false);
        iso(s, new File(out, "mm_bibliotheque_iso.png"));
        if (bad > 0) System.exit(2);
    }

    /** Qui fait monter le melange local : paires (bloc, voisin de la meme famille) sur les blocs pleins visibles. */
    static void mixingTally(RecordingSink r) throws Exception {
        java.lang.reflect.Method fam = Check.class.getDeclaredMethod("family", String.class);
        java.lang.reflect.Method cls = Check.class.getDeclaredMethod("classify", String.class);
        java.lang.reflect.Method nat = Check.class.getDeclaredMethod("natural", String.class);
        fam.setAccessible(true); cls.setAccessible(true); nat.setAccessible(true);
        int[] b = r.bounds();
        java.util.HashMap<String, Integer> pairs = new java.util.HashMap<>();
        int total = 0, mixed = 0;
        for (int x = b[0]; x <= b[3]; x++)
            for (int y = b[1]; y <= b[4]; y++)
                for (int z = b[2]; z <= b[5]; z++) {
                    String n = B.name(r.get(x, y, z));
                    if ((int) cls.invoke(null, n) != 5 || (boolean) nat.invoke(null, n)) continue;
                    boolean vis = false;
                    String mx = null;
                    for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                        String q = B.name(r.get(x + d[0], y + d[1], z + d[2]));
                        int c = (int) cls.invoke(null, q);
                        if (c == 0 || c == 1 || c == 4) vis = true;
                        else if (c == 5 && !q.equals(n) && fam.invoke(null, q).equals(fam.invoke(null, n))) mx = q;
                    }
                    if (!vis) continue;
                    total++;
                    if (mx != null) {
                        mixed++;
                        pairs.merge(n + " / " + mx, 1, Integer::sum);
                    }
                }
        // pans coplanaires les plus longs (murs bati pleins dont la face avant est libre)
        java.util.ArrayList<String> longPans = new java.util.ArrayList<>();
        int[][] nrm = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] nv : nrm)
            for (int y = b[1] + 1; y < Math.min(b[4], Manoir.T); y++) {
                boolean alongX = nv[0] == 0;
                int aMin = alongX ? b[2] : b[0], aMax = alongX ? b[5] : b[3];
                int tMin = alongX ? b[0] : b[2], tMax = alongX ? b[3] : b[5];
                for (int a = aMin; a <= aMax; a++) {
                    int run = 0, start = 0;
                    for (int tt = tMin; tt <= tMax + 1; tt++) {
                        boolean face = false;
                        if (tt <= tMax) {
                            int x = alongX ? tt : a, z = alongX ? a : tt;
                            String n = B.name(r.get(x, y, z));
                            String fr = B.name(r.get(x + nv[0], y, z + nv[1]));
                            int cf = (int) cls.invoke(null, fr);
                            boolean passF = cf == 0 || cf == 1 || cf == 4 || (cf == 6 && (fr.contains("carpet") || fr.contains("lantern") || fr.contains("candle") || fr.contains("potted") || fr.contains("torch")));
                            boolean inB = x + nv[0] >= b[0] && x + nv[0] <= b[3] && z + nv[1] >= b[2] && z + nv[1] <= b[5];
                            face = inB && (int) cls.invoke(null, n) == 5 && !(boolean) nat.invoke(null, n) && passF;
                        }
                        if (face) { if (run == 0) start = tt; run++; }
                        else {
                            if (run >= 9) longPans.add(run + " : y " + y + (alongX ? " z " + a + " x " + start + ".." + (tt - 1) : " x " + a + " z " + start + ".." + (tt - 1)) + " normale " + nv[0] + "," + nv[1]);
                            run = 0;
                        }
                    }
                }
            }
        longPans.sort((p, q) -> Integer.parseInt(q.split(" ")[0]) - Integer.parseInt(p.split(" ")[0]));
        for (int i = 0; i < Math.min(10, longPans.size()); i++) System.out.println("   pan " + longPans.get(i));
        System.out.printf("Melange (bati, recompte) : %d / %d = %.1f %%%n", mixed, total, 100.0 * mixed / Math.max(1, total));
        pairs.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(14)
                .forEach(e -> System.out.println("   " + e.getValue() + "  " + e.getKey()));
    }

    static int check(Reach r, RecordingSink s, int[] p, String what) {
        int x = p[0], y = p[1], z = p[2];
        String why = null;
        if (!r.body(x, y, z)) why = "pas de place (" + s.get(x, y, z) + " / " + s.get(x, y + 1, z) + ")";
        else if (!r.supported(x, y, z)) why = "pas de sol (" + s.get(x, y - 1, z) + ")";
        else if (!r.reached(x, y, z)) why = "inatteignable";
        if (x < Manoir.BX1 || x > Manoir.BX2 || z < Manoir.BZ1 || z > Manoir.BZ2) why = "hors de la boite";
        if (why != null) {
            String sug = "-";
            search:
            for (int d = 1; d <= 5; d++)
                for (int dx = -d; dx <= d; dx++)
                    for (int dz = -d; dz <= d; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                        int ax = x + dx, az = z + dz;
                        if (r.body(ax, y, az) && r.supported(ax, y, az) && r.reached(ax, y, az)
                                && r.body(ax + 1, y, az) && r.body(ax - 1, y, az) && r.body(ax, y, az + 1) && r.body(ax, y, az - 1)) {
                            sug = ax + "," + y + "," + az;
                            break search;
                        }
                    }
            System.out.println("PROBLEME " + what + " " + x + "," + y + "," + z + " : " + why + "  -> proposition " + sug);
            return 1;
        }
        return 0;
    }

    /** Classe un etat : bit PASS (le corps peut y etre), bit CLIMB (echelle, liane, eau). */
    static int kindOf(RecordingSink s, int x, int y, int z) {
        String st = s.get(x, y, z);
        String n = B.name(st);
        if (B.isAir(st) || n.equals("light")) {
            // impossible de se tenir sur une barriere / muret (1,5 de haut)
            String below = B.name(s.get(x, y - 1, z));
            if (below.endsWith("_fence") || below.endsWith("_wall") || below.endsWith("fence_gate")) return 0;
            return PASS;
        }
        if (n.equals("water")) return PASS | CLIMB;
        if (n.equals("ladder") || n.equals("vine") || n.contains("scaffolding")) return PASS | CLIMB;
        if (n.contains("_door")) return PASS; // portes en bois : s'ouvrent
        if (n.endsWith("_carpet") || n.endsWith("_button") || n.endsWith("pressure_plate") || n.contains("torch")
                || n.endsWith("sapling") || n.equals("short_grass") || n.equals("fern") || n.equals("dandelion")
                || n.equals("poppy") || n.contains("tulip") || n.equals("allium") || n.equals("azure_bluet")
                || n.equals("oxeye_daisy") || n.equals("cornflower") || n.equals("lily_of_the_valley")
                || n.equals("blue_orchid") || n.equals("lily_pad") || n.equals("sugar_cane") || n.equals("large_fern")
                || n.equals("rose_bush") || n.equals("peony") || n.contains("sign") || n.contains("banner")
                || n.equals("redstone_wire") || n.equals("cobweb") || n.equals("dead_bush") || n.contains("coral")
                || n.equals("candle") || n.contains("_candle") || n.endsWith("rail"))
            return PASS;
        if (n.endsWith("_trapdoor") && "true".equals(B.prop(st, "open"))) return PASS;
        return 0;
    }

    static final class Reach {
        final byte[] kind;
        final boolean[] seen;
        final int x1, y0, z1, nx, ny, nz;

        Reach(byte[] k, int x1, int y0, int z1, int nx, int ny, int nz) {
            this.kind = k; this.x1 = x1; this.y0 = y0; this.z1 = z1; this.nx = nx; this.ny = ny; this.nz = nz;
            seen = new boolean[k.length];
        }

        int idx(int x, int y, int z) { return ((x - x1) * ny + (y - y0)) * nz + (z - z1); }
        boolean in(int x, int y, int z) { return x >= x1 && x < x1 + nx && y >= y0 && y < y0 + ny && z >= z1 && z < z1 + nz; }
        boolean pass(int x, int y, int z) { return !in(x, y, z) ? (y >= y0 + ny) : (kind[idx(x, y, z)] & PASS) != 0; }
        boolean climb(int x, int y, int z) { return in(x, y, z) && (kind[idx(x, y, z)] & CLIMB) != 0; }
        boolean body(int x, int y, int z) { return in(x, y, z) && pass(x, y, z) && pass(x, y + 1, z); }
        boolean supported(int x, int y, int z) { return climb(x, y, z) || (in(x, y - 1, z) && !pass(x, y - 1, z)); }
        boolean reached(int x, int y, int z) { return in(x, y, z) && seen[idx(x, y, z)]; }

        int run(int[][] seeds) {
            ArrayDeque<int[]> q = new ArrayDeque<>();
            int n = 0;
            for (int[] p : seeds)
                if (body(p[0], p[1], p[2]) && !seen[idx(p[0], p[1], p[2])]) {
                    seen[idx(p[0], p[1], p[2])] = true;
                    q.add(p);
                    n++;
                }
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!q.isEmpty()) {
                int[] p = q.poll();
                int x = p[0], y = p[1], z = p[2];
                if (!supported(x, y, z)) {
                    n += push(q, x, y - 1, z);
                    continue;
                }
                for (int[] d : dirs) {
                    n += push(q, x + d[0], y, z + d[1]);
                    if (pass(x, y + 2, z)) n += push(q, x + d[0], y + 1, z + d[1]);
                }
                if (climb(x, y, z) || climb(x, y + 1, z)) n += push(q, x, y + 1, z);
                if (climb(x, y - 1, z)) n += push(q, x, y - 1, z);
            }
            return n;
        }

        /** Pieds sous ce rang = mort (GameMap.isBelowFloor) : le parcours s'arrete (par defaut : aucun). */
        int deadY = Integer.MIN_VALUE;

        int push(ArrayDeque<int[]> q, int x, int y, int z) {
            if (y < deadY) return 0;
            if (!body(x, y, z)) return 0;
            int i = idx(x, y, z);
            if (seen[i]) return 0;
            seen[i] = true;
            q.add(new int[]{x, y, z});
            return 1;
        }
    }

    /** Graphe des positions debout atteintes (voisins horizontaux a +-1 en hauteur) : on retire en boucle les
     *  positions de degre <= 1 ; ce qui part ainsi = impasses d'un bloc de large. */
    static void deadEnds(Reach r, int x1, int x2, int z1, int z2, int y0, int y1) {
        java.util.HashMap<Long, Integer> deg = new java.util.HashMap<>();
        java.util.ArrayList<int[]> nodes = new java.util.ArrayList<>();
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y0; y <= y1; y++)
                    if (r.reached(x, y, z) && r.supported(x, y, z)) nodes.add(new int[]{x, y, z});
        java.util.HashSet<Long> alive = new java.util.HashSet<>();
        for (int[] p : nodes) alive.add(key(p[0], p[1], p[2]));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] p : nodes) deg.put(key(p[0], p[1], p[2]), neighbours(alive, p, dirs).size());
        ArrayDeque<long[]> q = new ArrayDeque<>();
        java.util.HashMap<Long, Integer> depth = new java.util.HashMap<>();
        for (int[] p : nodes) if (deg.get(key(p[0], p[1], p[2])) <= 1) q.add(new long[]{p[0], p[1], p[2], 1});
        int peeled = 0, longest = 0;
        int[] at = null;
        while (!q.isEmpty()) {
            long[] e = q.poll();
            int[] p = {(int) e[0], (int) e[1], (int) e[2]};
            long k = key(p[0], p[1], p[2]);
            if (!alive.contains(k)) continue;
            alive.remove(k);
            peeled++;
            int d = (int) e[3];
            if (d > longest) { longest = d; at = p; }
            for (long nk : neighbours(alive, p, dirs)) {
                int nd = deg.merge(nk, -1, Integer::sum);
                if (nd <= 1) q.add(new long[]{(int) (nk >> 40) - 512, (int) ((nk >> 20) & 0xFFFFF) - 512, (int) (nk & 0xFFFFF) - 512, d + 1});
            }
        }
        System.out.println("Impasses d'un bloc de large : " + peeled + " positions, la plus longue " + longest
                + " (fond vers " + (at == null ? "-" : java.util.Arrays.toString(at)) + ")");
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    static java.util.List<Long> neighbours(java.util.HashSet<Long> alive, int[] p, int[][] dirs) {
        java.util.ArrayList<Long> l = new java.util.ArrayList<>();
        for (int[] d : dirs)
            for (int dy = -1; dy <= 1; dy++) {
                long k = key(p[0] + d[0], p[1] + dy, p[2] + d[1]);
                if (alive.contains(k)) { l.add(k); break; }
            }
        for (int dy : new int[]{-1, 1}) {
            long k = key(p[0], p[1] + dy, p[2]);
            if (alive.contains(k)) l.add(k);
        }
        return l;
    }

    // ---------------------------------------------------------------- rendus

    static int color(String st) {
        String n = B.name(st);
        if (B.isAir(st)) return 0x101010;
        if (n.contains("water")) return 0x3060d0;
        if (n.equals("snow") || n.equals("snow_block") || n.equals("white_concrete_powder") || n.equals("powder_snow")) return 0xeef2f8;
        if (n.endsWith("ice")) return 0xa8c8f0;
        if (n.equals("light_gray_concrete_powder")) return 0xc8c8c8;
        if (n.contains("dark_oak")) return 0x3e2814;
        if (n.contains("leaves") || n.contains("grass") || n.contains("fern") || n.contains("azalea")) return 0x3f8a3a;
        if (n.contains("bookshelf")) return 0x8a5a2a;
        if (n.contains("brick") && !n.contains("stone") && !n.contains("deepslate") && !n.contains("nether") && !n.contains("blackstone")) return 0xa04030;
        if (n.contains("quartz") || n.contains("diorite") || n.contains("calcite")) return 0xe8e4dc;
        if (n.contains("glass") || n.contains("pane")) return 0x9fd8e8;
        if (n.contains("carpet")) return 0xc03050;
        if (n.contains("dark_oak") || n.contains("spruce")) return 0x5a3a20;
        if (n.contains("oak") || n.contains("birch")) return 0xb8945a;
        if (n.contains("gravel") || n.contains("coarse") || n.contains("packed_mud")) return 0x9a9088;
        if (n.contains("deepslate") || n.contains("blackstone") || n.contains("basalt")) return 0x404048;
        if (n.contains("stone") || n.contains("andesite")) return 0x8a8a8a;
        if (n.contains("copper")) return 0x4fa88a;
        if (n.contains("lantern") || n.contains("candle") || n.contains("campfire")) return 0xffd060;
        if (n.contains("terracotta")) return 0xb06a50;
        return 0xc0a080;
    }

    /** Plan d'un niveau (hauteur des pieds y) : le bloc a hauteur de corps, ou le sol en plus sombre ; positions
     *  atteintes teintees en vert, apparitions en rouge, or en jaune. 4 px par bloc. */
    static void plan(RecordingSink s, Reach r, int y, File f) throws Exception {
        plan(s, r, y, f, Manoir.BX1, Manoir.BZ1, Manoir.BX2, Manoir.BZ2, 4);
    }

    static void plan(RecordingSink s, Reach r, int y, File f, int X1, int Z1, int X2, int Z2, int S) throws Exception {
        int W = (X2 - X1 + 1), L = (Z2 - Z1 + 1);
        Img img = new Img(W * S, L * S);
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                int c;
                String body = s.get(x, y, z), head = s.get(x, y + 1, z);
                String obstacle = !B.isAir(body) ? body : (!B.isAir(head) ? head : null);
                boolean reached = false;
                for (int dy = -1; dy <= 1; dy++) if (r.reached(x, y + dy, z) && r.supported(x, y + dy, z)) reached = true;
                if (obstacle != null && TestManoir.kindOf(s, x, y, z) == 0) c = color(obstacle);
                else {
                    int fy = y - 1;
                    while (fy > y - 6 && B.isAir(s.get(x, fy, z))) fy--;
                    int base = color(s.get(x, fy, z));
                    c = darken(base, y - 1 - fy > 0 ? 0.35 : 0.6);
                    if (reached) c = mix(c, 0x40ff60, 0.35);
                }
                for (int i = 0; i < S; i++) for (int k = 0; k < S; k++) img.setRGB((x - X1) * S + i, (z - Z1) * S + k, ((i == 0 || k == 0) && S >= 6) ? darken(c, 0.85) : c);
            }
        mark(img, Manoir.SPAWNS, y, 0xff2020, S, X1, Z1);
        mark(img, Manoir.GOLD, y, 0xffe000, S, X1, Z1);
        img.write(f);
    }

    static void mark(Img img, int[][] pts, int y, int c, int S, int X1, int Z1) {
        for (int[] p : pts) {
            if (Math.abs(p[1] - y) > 1) continue;
            int px = (p[0] - X1) * S, pz = (p[2] - Z1) * S;
            for (int i = S / 4; i < S - S / 4; i++) for (int k = S / 4; k < S - S / 4; k++) {
                int ax = px + i, az = pz + k;
                if (ax >= 0 && az >= 0 && ax < img.getWidth() && az < img.getHeight()) img.setRGB(ax, az, c);
            }
        }
    }

    /** Coupe verticale (z fixe si alongX, sinon x fixe). */
    static void section(RecordingSink s, int at, File f, boolean alongX) throws Exception {
        int S = 4, W = 2 * Manoir.AREA + 1, H = Manoir.Y_HI - 56 + 1;
        Img img = new Img(W * S, H * S);
        for (int u = -Manoir.AREA; u <= Manoir.AREA; u++)
            for (int y = 56; y <= Manoir.Y_HI; y++) {
                String st = alongX ? s.get(u, y, at) : s.get(at, y, u);
                int c = B.isAir(st) ? 0xc8e0f0 : color(st);
                for (int i = 0; i < S; i++) for (int k = 0; k < S; k++) img.setRGB((u + Manoir.AREA) * S + i, (Manoir.Y_HI - y) * S + k, c);
            }
        img.write(f);
    }

    /** Vue isometrique simple (depuis le sud-est), 2 px par bloc. */
    static void iso(RecordingSink s, File f) throws Exception {
        int A = Manoir.AREA, S = 2;
        int W = (4 * A + 4) * S, H = (2 * A * 2 + (Manoir.Y_HI - 50) * 2 + 20) * S;
        Img img = new Img(W, H);
        int[] depth = new int[W * H];
        java.util.Arrays.fill(depth, Integer.MIN_VALUE);
        for (int i = 0; i < W * H; i++) img.setRGB(i % W, i / W, 0xd8e8f4);
        for (int x = -A; x <= A; x++)
            for (int z = -A; z <= A; z++)
                for (int y = 56; y <= Manoir.Y_HI; y++) {
                    String st = s.get(x, y, z);
                    if (B.isAir(st)) continue;
                    // le bloc n'est dessine que s'il a une face visible vers le haut / sud / est
                    if (!B.isAir(s.get(x, y + 1, z)) && !B.isAir(s.get(x + 1, y, z)) && !B.isAir(s.get(x, y, z + 1))) continue;
                    int sx = (x - z + 2 * A) * S + 2, sy = ((x + z + 2 * A) - 2 * (y - 50) + 2 * (Manoir.Y_HI - 50)) * S / 1;
                    int d = x + z + 2 * y;
                    int c = color(st);
                    boolean top = B.isAir(s.get(x, y + 1, z));
                    if (!top) c = darken(c, 0.75);
                    for (int i = 0; i < 2 * S; i++)
                        for (int k = 0; k < 2 * S; k++) {
                            int px = sx + i - S, py = sy + k;
                            if (px < 0 || py < 0 || px >= W || py >= H) continue;
                            int id = py * W + px;
                            if (d >= depth[id]) {
                                depth[id] = d;
                                img.setRGB(px, py, c);
                            }
                        }
                }
        img.write(f);
    }

    /** Image RGB minimale + encodeur PNG (le JDK du scratchpad n'a pas AWT). */
    static final class Img {
        final int w, h;
        final int[] px;

        Img(int w, int h) { this.w = w; this.h = h; px = new int[w * h]; }
        int getWidth() { return w; }
        int getHeight() { return h; }
        void setRGB(int x, int y, int c) { px[y * w + x] = c; }

        void write(File f) throws java.io.IOException {
            java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
            for (int y = 0; y < h; y++) {
                raw.write(0);
                for (int x = 0; x < w; x++) {
                    int c = px[y * w + x];
                    raw.write((c >> 16) & 255); raw.write((c >> 8) & 255); raw.write(c & 255);
                }
            }
            java.io.ByteArrayOutputStream z = new java.io.ByteArrayOutputStream();
            try (java.util.zip.DeflaterOutputStream d = new java.util.zip.DeflaterOutputStream(z)) { raw.writeTo(d); }
            try (java.io.DataOutputStream o = new java.io.DataOutputStream(new java.io.FileOutputStream(f))) {
                o.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
                java.io.ByteArrayOutputStream ih = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream ihd = new java.io.DataOutputStream(ih);
                ihd.writeInt(w); ihd.writeInt(h); ihd.write(new byte[]{8, 2, 0, 0, 0});
                chunk(o, "IHDR", ih.toByteArray());
                chunk(o, "IDAT", z.toByteArray());
                chunk(o, "IEND", new byte[0]);
            }
        }

        static void chunk(java.io.DataOutputStream o, String type, byte[] data) throws java.io.IOException {
            o.writeInt(data.length);
            byte[] t = type.getBytes(StandardCharsets.US_ASCII);
            o.write(t); o.write(data);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(t); crc.update(data);
            o.writeInt((int) crc.getValue());
        }
    }

    static int darken(int c, double f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int c, int d, double t) {
        int r = (int) (((c >> 16) & 255) * (1 - t) + ((d >> 16) & 255) * t);
        int g = (int) (((c >> 8) & 255) * (1 - t) + ((d >> 8) & 255) * t);
        int b = (int) ((c & 255) * (1 - t) + (d & 255) * t);
        return (r << 16) | (g << 8) | b;
    }
}
