package fr.eemicraft.hideseek;

import fr.eemicraft.hideseek.build.B;
import fr.eemicraft.hideseek.build.RecordingSink;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Banc de test hors serveur de la regle anti-perchoir (1.6.0, hors jar) : construit supermarche et port en memoire,
 * calcule les cases debout atteintes a pied depuis le point des cacheurs (marche + saut de 1, chute, echelles : meme
 * parcours que TestMaps), puis compte celles que {@link Perch} interdit, regroupees par bloc porteur et par zone.
 *
 * <p>Arguments : [max-roof-height] [id de map]. Le village (MapBuilder, BlockData Bukkit) ne se construit pas hors serveur.
 */
public final class PerchTest {
    public static void main(String[] args) {
        double maxH = args.length > 0 ? Double.parseDouble(args[0]) : 3.0;
        String only = args.length > 1 ? args[1] : "";
        for (HsMap m : new HsMap[]{new SuperMap(), new PortMap()}) {
            if (!only.isEmpty() && !only.equals(m.id)) continue;
            RecordingSink s = new RecordingSink();
            m.build(s);
            run(m, s, maxH);
        }
    }

    static boolean covers(RecordingSink s, int x, int y, int z) {
        String st = s.get(x, y, z);
        if (TestMaps.free(st)) return false;
        String n = B.name(st);
        return !n.endsWith("_leaves") && !n.equals("barrier") && !n.equals("light");
    }

    static double feet(RecordingSink s, int x, int y, int z) {
        String below = s.get(x, y - 1, z);
        String n = B.name(below);
        if (n.endsWith("_slab") && "bottom".equals(B.prop(below, "type"))) return y - 0.5;
        return y;
    }

    static void run(HsMap m, RecordingSink s, double maxH) {
        int[] pb = m.playBox();
        int ceil = m.measureCeilY(), fy = m.measureFloorY();
        Set<Long> stand = new HashSet<>();
        for (int x = pb[0]; x <= pb[3]; x++)
            for (int z = pb[2]; z <= pb[5]; z++)
                for (int y = fy + 1; y < ceil; y++)
                    if (TestMaps.standable(s, x, y, z)) stand.add(TestMaps.key(x, y, z));
        double[] hs = m.point("hider-spawn");
        int[] start = {(int) Math.floor(hs[0]), (int) Math.floor(hs[1]), (int) Math.floor(hs[2])};
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        seen.add(TestMaps.key(start[0], start[1], start[2]));
        q.add(start);
        int[][] d4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : d4) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                if (d == d4[0]) {
                    if ((TestMaps.ladder(s, c[0], c[1], c[2]) || TestMaps.ladder(s, c[0], c[1] + 1, c[2])) && stand.contains(TestMaps.key(c[0], c[1] + 1, c[2]))) add(seen, q, c[0], c[1] + 1, c[2]);
                    if (TestMaps.ladder(s, c[0], c[1] - 1, c[2]) && stand.contains(TestMaps.key(c[0], c[1] - 1, c[2]))) add(seen, q, c[0], c[1] - 1, c[2]);
                }
                if (TestMaps.free(s.get(c[0], c[1] + 2, c[2])) && stand.contains(TestMaps.key(nx, c[1] + 1, nz))) add(seen, q, nx, c[1] + 1, nz);
                for (int y = c[1]; y >= fy + 1; y--) {
                    if (!TestMaps.free(s.get(nx, y + 1, nz))) break;
                    if (stand.contains(TestMaps.key(nx, y, nz))) { add(seen, q, nx, y, nz); break; }
                    if (!TestMaps.free(s.get(nx, y, nz))) break;
                }
            }
        }
        int bad = 0;
        Map<String, Integer> bySupport = new TreeMap<>();
        Map<String, int[]> byArea = new TreeMap<>();
        for (long k : seen) {
            int x = (int) ((k >> 40) - 30000), z = (int) (((k >> 16) & 0xFFFFFF) - 30000), y = (int) ((k & 0xFFFF) - 1000);
            double f = feet(s, x, y, z);
            if (!Perch.forbidden((cx, cy, cz) -> covers(s, cx, cy, cz), m, x, f, z, Perch.groundNear(m::roofGround, x, z), maxH, 24)) continue;
            bad++;
            bySupport.merge(B.name(s.get(x, y - 1, z)), 1, Integer::sum);
            // zones de 8 x 8 (coordonnees locales a la map), avec la hauteur max au-dessus du sol
            String a = String.format("x%4d z%4d", Math.floorDiv(x, 8) * 8, Math.floorDiv(z, 8) * 8);
            int[] v = byArea.computeIfAbsent(a, kk -> new int[]{0, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE});
            v[0]++;
            v[1] = Math.max(v[1], (int) Math.round(Perch.heightAbove(f, Perch.groundNear(m::roofGround, x, z))));
            v[2] = Math.min(v[2], y);
            v[3] = Math.max(v[3], y);
        }
        System.out.printf("=== %s (max-roof-height %.1f) : %d cases debout, %d atteintes a pied, %d interdites (%.1f %%)%n",
                m.id, maxH, stand.size(), seen.size(), bad, 100.0 * bad / Math.max(1, seen.size()));
        System.out.println("   par bloc porteur : " + bySupport);
        if (System.getenv("PERCH_AREAS") != null)
            for (Map.Entry<String, int[]> e : byArea.entrySet())
                System.out.printf("   %s : %3d cases, y %d..%d, jusqu'a +%d%n", e.getKey(), e.getValue()[0], e.getValue()[2], e.getValue()[3], e.getValue()[1]);
        // composantes connexes des cases interdites (coordonnees locales si la map en a : port X0/Z0)
        int ox = m instanceof PortMap ? PortMap.X0 : m instanceof SuperMap ? SuperMap.X0 : 0;
        int oz = m instanceof PortMap ? PortMap.Z0 : m instanceof SuperMap ? SuperMap.Z0 : 0;
        Set<Long> badSet = new HashSet<>();
        for (long k : seen) {
            int x = (int) ((k >> 40) - 30000), z = (int) (((k >> 16) & 0xFFFFFF) - 30000), y = (int) ((k & 0xFFFF) - 1000);
            if (Perch.forbidden((cx, cy, cz) -> covers(s, cx, cy, cz), m, x, feet(s, x, y, z), z, Perch.groundNear(m::roofGround, x, z), maxH, 24)) badSet.add(k);
        }
        Set<Long> done = new HashSet<>();
        for (long k0 : badSet) {
            if (done.contains(k0)) continue;
            ArrayDeque<Long> qq = new ArrayDeque<>();
            qq.add(k0); done.add(k0);
            int n = 0, x1 = 1 << 30, x2 = -(1 << 30), y1 = x1, y2 = x2, z1 = x1, z2 = x2;
            Map<String, Integer> sup = new TreeMap<>();
            while (!qq.isEmpty()) {
                long c = qq.poll();
                n++;
                int x = (int) ((c >> 40) - 30000), z = (int) (((c >> 16) & 0xFFFFFF) - 30000), y = (int) ((c & 0xFFFF) - 1000);
                x1 = Math.min(x1, x); x2 = Math.max(x2, x); y1 = Math.min(y1, y); y2 = Math.max(y2, y); z1 = Math.min(z1, z); z2 = Math.max(z2, z);
                sup.merge(B.name(s.get(x, y - 1, z)), 1, Integer::sum);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) {
                    long nk = TestMaps.key(x + dx, y + dy, z + dz);
                    if (badSet.contains(nk) && done.add(nk)) qq.add(nk);
                }
            }
            System.out.printf("   bloc de %4d cases : local x %d..%d z %d..%d, y %d..%d (sol %d) %s%n", n, x1 - ox, x2 - ox, z1 - oz, z2 - oz, y1, y2,
                    m.roofGround(x1, z1), sup);
        }
    }

    static void add(Set<Long> seen, ArrayDeque<int[]> q, int x, int y, int z) {
        if (seen.add(TestMaps.key(x, y, z))) q.add(new int[]{x, y, z});
    }
}
