package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Check;
import fr.eemicraft.murder.build.RecordingSink;
import fr.eemicraft.murder.build.SchemWriter;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;

/**
 * Test hors serveur des maps construites de la 1.8.0 (non embarque dans le jar) : construit la map en memoire,
 * ecrit un .schem (boite de jeu) et la liste des etats, verifie l'accessibilite a pied de toutes les apparitions
 * et de tous les points d'or depuis l'apparition 0, cherche les impasses, dessine les plans par niveau, une coupe
 * et une vue isometrique. Usage : java -cp classes fr.eemicraft.murder.TestMaps <fete|musee> <dossier> [niveaux]
 */
public final class TestMaps {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("sonde")) { // sonde <id> x1 z1 x2 z2 y1 y2
            RecordingSink s = new RecordingSink();
            MapDef.byId(a[1]).builder.accept(s);
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(a[i + 2]);
            for (int y = v[5]; y >= v[4]; y--)
                for (int x = v[0]; x <= v[2]; x++)
                    for (int z = v[1]; z <= v[3]; z++) {
                        String st = s.get(x, y, z);
                        if (!st.equals("minecraft:air")) System.out.println(x + " " + y + " " + z + " " + st);
                    }
            return;
        }
        MapDef d = MapDef.byId(a[0]);
        File out = new File(a.length > 1 ? a[1] : "out");
        out.mkdirs();
        int[] levels = a.length > 2 ? java.util.Arrays.stream(a[2].split(",")).mapToInt(Integer::parseInt).toArray() : new int[]{65};
        String id = "mm_" + d.id;
        long t0 = System.currentTimeMillis();
        RecordingSink s = new RecordingSink();
        java.util.Arrays.fill(fr.eemicraft.murder.build.Details.STATS, 0);
        d.builder.accept(s);
        long t1 = System.currentTimeMillis();
        int[] ds = fr.eemicraft.murder.build.Details.STATS;
        System.out.println("Passe Details : " + ds[0] + " trappes retournees, " + ds[1] + " lanternes (suspendue/posee), " + ds[2] + " paratonnerres reorientes");
        System.out.println("Construction en memoire : " + (t1 - t0) + " ms, " + s.operations() + " operations, "
                + s.count() + " blocs, " + s.paletteSize() + " etats, bornes " + java.util.Arrays.toString(s.bounds()));
        TreeSet<String> states = new TreeSet<>();
        for (int i = 1; i < s.paletteSize(); i++) states.add(s.state(i));
        try (PrintWriter w = new PrintWriter(new File(out, id + ".states.txt"), StandardCharsets.UTF_8)) {
            for (String st : states) w.println(st);
        }
        // schem : boite de jeu (sans barrieres invisibles), et tout
        RecordingSink play = new RecordingSink();
        for (int x = d.bx1; x <= d.bx2; x++)
            for (int z = d.bz1; z <= d.bz2; z++)
                for (int y = d.minY; y <= d.yHi; y++) {
                    int i = s.id(x, y, z);
                    if (i != 0 && !s.state(i).startsWith("minecraft:barrier")) play.set(x, y, z, s.state(i));
                }
        SchemWriter.write(play, new File(out, id + ".schem"));
        SchemWriter.write(s, new File(out, id + "_tout.schem"));
        System.out.println(Check.run(play));
        TestManoir.mixingTally(play);

        // ---------------------------------------------------------------- accessibilite
        int x1 = d.bx1, x2 = d.bx2, z1 = d.bz1, z2 = d.bz2, y0 = d.minY - 4, y1 = d.maxY;
        int nx = x2 - x1 + 1, ny = y1 - y0 + 1, nz = z2 - z1 + 1;
        byte[] kind = new byte[nx * ny * nz];
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y0; y <= y1; y++) kind[((x - x1) * ny + (y - y0)) * nz + (z - z1)] = (byte) TestManoir.kindOf(s, x, y, z);
        TestManoir.Reach r = new TestManoir.Reach(kind, x1, y0, z1, nx, ny, nz);
        boolean ship = d.id.equals(Paquebot.ID);
        if (ship) r.deadY = d.minY - 1; // paquebot : tomber a la mer tue (on ne compte pas la nage)
        r.run(new int[][]{d.spawns[0]});
        if (ship) {
            // diagnostic : positions debout atteintes hors de la coque (par-dessus bord, sur une preceinte...)
            int n = 0;
            for (int y = y1; y >= y0; y--)
                for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++)
                        if (r.reached(x, y, z) && !Paquebot.in(x, z, Paquebot.F2)) {
                            if (n++ < 12) System.out.println("HORS COQUE " + x + "," + y + "," + z + " (" + (r.supported(x, y, z) ? "debout" : "en l'air") + ") sur " + s.get(x, y - 1, z));
                        }
            System.out.println("Positions atteintes hors coque : " + n);
            int m = 0;
            for (int y = y1; y >= Paquebot.F3 + 2; y--)
                for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++)
                        if (r.reached(x, y, z) && r.supported(x, y, z) && m++ < 25)
                            System.out.println("HAUT " + x + "," + y + "," + z + " sur " + s.get(x, y - 1, z));
        }
        int bad = 0;
        for (int[] p : d.spawns) bad += check(r, s, d, p, "apparition");
        for (int[] p : d.gold) bad += check(r, s, d, p, "or");
        System.out.println(d.spawns.length + " apparitions, " + d.gold.length + " points d'or, " + bad + " probleme(s)");
        int details = new Controles(s, r, d).run(out);
        System.out.println("Problemes de details : " + details);
        bad += details;
        int[] perY = new int[ny];
        int below = 0, maxY = 0, total = 0;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y0; y <= y1; y++)
                    if (r.reached(x, y, z) && r.supported(x, y, z)) {
                        perY[y - y0]++;
                        total++;
                        if (y < d.minY - 1) below++;
                        maxY = Math.max(maxY, y);
                    }
        StringBuilder sb = new StringBuilder("Positions debout atteintes (" + total + ") par hauteur des pieds :");
        for (int i = 0; i < ny; i++) if (perY[i] > 0) sb.append(' ').append(y0 + i).append('=').append(perY[i]);
        System.out.println(sb);
        System.out.println("Pieds les plus hauts atteints : " + maxY + " (max config " + d.maxY + "), positions sous le sol mortel : " + below);
        walkDistances(r, d, x1, x2, z1, z2, y0, y1);
        TestManoir.deadEnds(r, x1, x2, z1, z2, y0, y1);
        for (int fy : levels) TestManoir.deadEnds(r, x1, x2, z1, z2, fy, fy);
        // plans, coupes, iso
        for (int fy : levels) plan(s, r, d, fy, new File(out, id + "_niveau" + fy + ".png"), d.small ? 8 : 4);
        section(s, d, (d.bz1 + d.bz2) / 2, new File(out, id + "_coupe_z.png"), true);
        section(s, d, 0, new File(out, id + "_coupe_x0.png"), false);
        if (d.small) section(s, d, 3, new File(out, id + "_coupe_x3.png"), false);
        iso(s, d, new File(out, id + "_iso.png"), d.area, 2, false);
        iso(s, d, new File(out, id + "_iso_zoom.png"), Math.max(d.bx2, d.bz2) + 2, d.small ? 5 : 4, false);
        iso(s, d, new File(out, id + "_iso_nord.png"), Math.max(d.bx2, d.bz2) + 2, 4, true);
        if (ship) {
            elevation(s, d, new File(out, id + "_elevation_sud.png"), 1);
            elevation(s, d, new File(out, id + "_elevation_nord.png"), -1);
            for (int fy : levels) plan(s, r, d, fy, new File(out, id + "_niveau" + fy + "_zoom.png"), 7);
        }
        if (bad > 0) System.exit(2);
    }

    /**
     * 1.10.0 : distances a pied (BFS sur les positions debout, pas horizontaux avec marche de +1 ou chute de 4 au
     * plus, echelles) : moyenne entre toutes les paires d'apparitions, et entre 600 paires de positions au hasard.
     */
    static void walkDistances(TestManoir.Reach r, MapDef d, int x1, int x2, int z1, int z2, int y0, int y1) {
        java.util.HashMap<Long, Integer> idx = new java.util.HashMap<>();
        java.util.ArrayList<int[]> nodes = new java.util.ArrayList<>();
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y0; y <= y1; y++)
                    if (r.reached(x, y, z) && r.supported(x, y, z)) { idx.put(TestManoir.key(x, y, z), nodes.size()); nodes.add(new int[]{x, y, z}); }
        int n = nodes.size();
        int[][] adj = new int[n][];
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < n; i++) {
            int[] p = nodes.get(i);
            java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
            for (int[] dd : dirs)
                for (int dy = 1; dy >= -4; dy--) {
                    Integer j = idx.get(TestManoir.key(p[0] + dd[0], p[1] + dy, p[2] + dd[1]));
                    if (j != null) { l.add(j); break; }
                }
            for (int dy : new int[]{-1, 1}) { Integer j = idx.get(TestManoir.key(p[0], p[1] + dy, p[2])); if (j != null) l.add(j); }
            adj[i] = l.stream().mapToInt(Integer::intValue).toArray();
        }
        java.util.function.IntFunction<int[]> bfs = src -> {
            int[] dist = new int[n];
            java.util.Arrays.fill(dist, -1);
            int[] q = new int[n];
            int h = 0, t = 0;
            dist[src] = 0; q[t++] = src;
            while (h < t) { int u = q[h++]; for (int v : adj[u]) if (dist[v] < 0) { dist[v] = dist[u] + 1; q[t++] = v; } }
            return dist;
        };
        double sumS = 0; int cntS = 0, maxS = 0;
        for (int a = 0; a < d.spawns.length; a++) {
            Integer ia = idx.get(TestManoir.key(d.spawns[a][0], d.spawns[a][1], d.spawns[a][2]));
            if (ia == null) continue;
            int[] dist = bfs.apply(ia);
            for (int b = a + 1; b < d.spawns.length; b++) {
                Integer ib = idx.get(TestManoir.key(d.spawns[b][0], d.spawns[b][1], d.spawns[b][2]));
                if (ib == null || dist[ib] < 0) continue;
                sumS += dist[ib]; cntS++; maxS = Math.max(maxS, dist[ib]);
            }
        }
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(42);
        double sumR = 0; int cntR = 0;
        for (int s = 0; s < 30 && n > 0; s++) {
            int[] dist = bfs.apply(rnd.nextInt(n));
            for (int k = 0; k < 20; k++) { int b = rnd.nextInt(n); if (dist[b] >= 0) { sumR += dist[b]; cntR++; } }
        }
        System.out.printf(java.util.Locale.ROOT, "Distances a pied : entre apparitions moyenne %.1f (max %d), entre positions au hasard moyenne %.1f blocs, %d positions debout%n",
                cntS == 0 ? 0 : sumS / cntS, maxS, cntR == 0 ? 0 : sumR / cntR, n);
    }

    /** Elevation exterieure (vue depuis le sud si dir = 1, depuis le nord si dir = -1), eau comprise, de nuit. */
    static void elevation(RecordingSink s, MapDef d, File f, int dir) throws Exception {
        int S = 5, X1 = d.bx1, X2 = d.bx2, Y0 = 50, Y1 = d.yHi;
        TestManoir.Img img = new TestManoir.Img((X2 - X1 + 1) * S, (Y1 - Y0 + 1) * S);
        for (int x = X1; x <= X2; x++)
            for (int y = Y0; y <= Y1; y++) {
                int c = 0x0c1424, depth = 0;
                for (int k = 0; k < 2 * d.area; k++) {
                    int z = dir > 0 ? d.area - k : -d.area + k;
                    String st = s.get(dir > 0 ? x : -x + (X1 + X2), y, z);
                    if (B.isAir(st) || st.startsWith("minecraft:barrier") || st.startsWith("minecraft:light[")) continue;
                    String n = B.name(st);
                    c = TestManoir.color(st);
                    if (n.contains("wool") || n.contains("concrete") || n.contains("terracotta")) c = dyeColor(n, c);
                    if (n.equals("water")) c = 0x1a3050;
                    if (n.contains("glass")) c = 0x9fc8e0;
                    if (n.contains("lantern") || n.contains("froglight") || n.contains("shroomlight")) c = 0xffe070;
                    depth = k;
                    break;
                }
                int base = c;
                if (depth > 0) c = TestManoir.darken(base, Math.max(0.55, 1 - (depth - (d.area - 14)) * 0.02));
                for (int i = 0; i < S; i++) for (int j = 0; j < S; j++) img.setRGB((x - X1) * S + i, (Y1 - y) * S + j, c);
            }
        img.write(f);
    }

    static int check(TestManoir.Reach r, RecordingSink s, MapDef d, int[] p, String what) {
        int x = p[0], y = p[1], z = p[2];
        String why = null;
        if (!r.body(x, y, z)) why = "pas de place (" + s.get(x, y, z) + " / " + s.get(x, y + 1, z) + ")";
        else if (!r.supported(x, y, z)) why = "pas de sol (" + s.get(x, y - 1, z) + ")";
        else if (!r.reached(x, y, z)) why = "inatteignable";
        if (x < d.bx1 || x > d.bx2 || z < d.bz1 || z > d.bz2) why = "hors de la boite";
        if (why != null) {
            String sug = "-";
            search:
            for (int k = 1; k <= 5; k++)
                for (int dx = -k; dx <= k; dx++)
                    for (int dz = -k; dz <= k; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != k) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            int ax = x + dx, az = z + dz, ay = y + dy;
                            if (r.body(ax, ay, az) && r.supported(ax, ay, az) && r.reached(ax, ay, az)) {
                                sug = ax + "," + ay + "," + az;
                                break search;
                            }
                        }
                    }
            System.out.println("PROBLEME " + what + " " + x + "," + y + "," + z + " : " + why + "  -> proposition " + sug);
            return 1;
        }
        return 0;
    }

    static void plan(RecordingSink s, TestManoir.Reach r, MapDef d, int y, File f, int S) throws Exception {
        int X1 = d.bx1, Z1 = d.bz1, X2 = d.bx2, Z2 = d.bz2;
        int W = (X2 - X1 + 1), L = (Z2 - Z1 + 1);
        TestManoir.Img img = new TestManoir.Img(W * S, L * S);
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                int c;
                String body = s.get(x, y, z), head = s.get(x, y + 1, z);
                String obstacle = !B.isAir(body) ? body : (!B.isAir(head) ? head : null);
                boolean reached = false;
                for (int dy = -1; dy <= 1; dy++) if (r.reached(x, y + dy, z) && r.supported(x, y + dy, z)) reached = true;
                if (obstacle != null && TestManoir.kindOf(s, x, y, z) == 0) c = TestManoir.color(obstacle);
                else {
                    int fy = y - 1;
                    while (fy > y - 6 && B.isAir(s.get(x, fy, z))) fy--;
                    c = TestManoir.darken(TestManoir.color(s.get(x, fy, z)), y - 1 - fy > 0 ? 0.35 : 0.6);
                    if (reached) c = TestManoir.mix(c, 0x40ff60, 0.35);
                }
                for (int i = 0; i < S; i++) for (int k = 0; k < S; k++) img.setRGB((x - X1) * S + i, (z - Z1) * S + k, c);
            }
        TestManoir.mark(img, d.spawns, y, 0xff2020, S, X1, Z1);
        TestManoir.mark(img, d.gold, y, 0xffe000, S, X1, Z1);
        img.write(f);
    }

    static void section(RecordingSink s, MapDef d, int at, File f, boolean alongX) throws Exception {
        int S = d.small ? 8 : 3, A = d.small ? Math.max(d.bx2, d.bz2) + 2 : d.area, H = d.yHi - d.yLo + 1;
        TestManoir.Img img = new TestManoir.Img((2 * A + 1) * S, H * S);
        for (int u = -A; u <= A; u++)
            for (int y = d.yLo; y <= d.yHi; y++) {
                String st = alongX ? s.get(u, y, at) : s.get(at, y, u);
                int c = B.isAir(st) || st.startsWith("minecraft:barrier") ? 0xc8e0f0 : TestManoir.color(st);
                for (int i = 0; i < S; i++) for (int k = 0; k < S; k++) img.setRGB((u + A) * S + i, (d.yHi - y) * S + k, c);
            }
        img.write(f);
    }

    /** Vue isometrique ; north = vue depuis le nord-ouest (rotation de 180 degres). */
    static void iso(RecordingSink s, MapDef d, File f, int A, int S, boolean north) throws Exception {
        int Y0 = d.minY - 2;
        int W = (4 * A + 8) * S, H = (4 * A + (d.yHi - Y0) * 2 + 20) * S;
        TestManoir.Img img = new TestManoir.Img(W, H);
        int[] depth = new int[W * H];
        java.util.Arrays.fill(depth, Integer.MIN_VALUE);
        for (int i = 0; i < W * H; i++) img.setRGB(i % W, i / W, 0x101828);
        for (int vx = -A; vx <= A; vx++)
            for (int vz = -A; vz <= A; vz++)
                for (int y = Y0; y <= d.yHi; y++) {
                    int x = north ? -vx : vx, z = north ? -vz : vz, sgn = north ? -1 : 1;
                    String st = s.get(x, y, z);
                    if (B.isAir(st) || st.startsWith("minecraft:barrier") || st.startsWith("minecraft:light[")) continue;
                    if (!B.isAir(s.get(x, y + 1, z)) && !B.isAir(s.get(x + sgn, y, z)) && !B.isAir(s.get(x, y, z + sgn))) continue;
                    int sx = (vx - vz + 2 * A) * S + 2, sy = ((vx + vz + 2 * A) - 2 * (y - Y0) + 2 * (d.yHi - Y0)) * S;
                    int dd = vx + vz + 2 * y;
                    int c = TestManoir.color(st);
                    String n = B.name(st);
                    if (n.contains("wool") || n.contains("concrete") || n.contains("terracotta")) c = dyeColor(n, c);
                    if (n.contains("froglight") || n.contains("lantern") || n.contains("glowstone") || n.contains("shroomlight")) c = 0xffe070;
                    if (!B.isAir(s.get(x, y + 1, z))) c = TestManoir.darken(c, 0.75);
                    for (int i = 0; i < 2 * S; i++)
                        for (int k = 0; k < 2 * S; k++) {
                            int px = sx + i - S, py = sy + k;
                            if (px < 0 || py < 0 || px >= W || py >= H) continue;
                            int id = py * W + px;
                            if (dd >= depth[id]) { depth[id] = dd; img.setRGB(px, py, c); }
                        }
                }
        img.write(f);
    }

    static int dyeColor(String n, int def) {
        String[][] t = {{"light_blue", "70b0e0"}, {"light_gray", "a0a0a0"}, {"white", "f0f0f0"}, {"orange", "f08020"}, {"magenta", "c040c0"},
                {"yellow", "f0d020"}, {"lime", "70d020"}, {"pink", "f090b0"}, {"gray", "505050"}, {"cyan", "20a0a0"}, {"purple", "8030b0"},
                {"blue", "3040b0"}, {"brown", "704020"}, {"green", "407020"}, {"red", "c02020"}, {"black", "181818"}};
        for (String[] e : t) if (n.startsWith(e[0] + "_")) return Integer.parseInt(e[1], 16);
        return def;
    }
}
