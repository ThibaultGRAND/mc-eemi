package fr.eemicraft.murder.build;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Arbre sur mesure (règles G du skill) : tronc décalé d'un bloc tous les 3 à 5, racines à la base des grands
 * arbres, 2 ou 3 branches de longueurs et hauteurs différentes (majoritairement du côté où il penche), boules de
 * feuilles bruitées au bout des branches et au sommet (plus hautes que basses), deux types de feuilles en
 * grappes, feuilles persistantes. Le hasard est tiré PAR ARBRE (graine), pas par bloc.
 *
 * <pre>
 * new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(9).place(sink, x, y, z, seed);
 * </pre>
 * (x, y, z) = premier bloc du tronc (au-dessus du sol).
 */
public final class Tree {
    public String log, leaves, leaves2;
    public int height = 8;
    /** Rayon des boules de feuilles (sommet ; les branches ont ~80 %). */
    public double crown = 3.2;
    /** Part de la 2e feuille (en grappes). */
    public double leaves2Share = 0.25;
    public boolean roots = true;

    public Tree(String log, String leaves, String leaves2) {
        this.log = B.id(log);
        this.leaves = B.id(leaves);
        this.leaves2 = leaves2 == null ? null : B.id(leaves2);
    }

    public Tree height(int h) { height = Math.max(4, h); return this; }
    public Tree crown(double r) { crown = r; return this; }
    public Tree roots(boolean b) { roots = b; return this; }

    /** Pose l'arbre ; renvoie le nombre de blocs posés. */
    public int place(Sink s, int x, int y, int z, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        Noise leafNoise = new Noise(seed ^ 0x5DEECE66DL);
        int[] cnt = {0};
        Dir lean = Dir.values()[r.nextInt(4)];
        List<int[]> trunk = new ArrayList<>();
        int cx = x, cz = z;
        int next = 3 + r.nextInt(3);
        for (int i = 0; i < height; i++) {
            int yy = y + i;
            put(s, cx, yy, cz, B.axis(log, 'y'), cnt, true);
            trunk.add(new int[]{cx, yy, cz});
            if (i == next && i < height - 2) {
                Dir d = r.nextDouble() < 0.7 ? lean : (r.nextBoolean() ? lean.cw() : lean.ccw());
                cx += d.dx; cz += d.dz;
                put(s, cx, yy, cz, B.axis(log, d.axis()), cnt, true);
                next += 3 + r.nextInt(3);
            }
        }
        // racines / base évasée
        if (roots && height >= 6) {
            for (Dir d : Dir.values()) {
                if (r.nextDouble() > 0.65) continue;
                int rx = x + d.dx, rz = z + d.dz;
                put(s, rx, y, rz, B.axis(log, d.axis()), cnt, false);
                if (r.nextDouble() < 0.4) put(s, rx, y + 1, rz, B.axis(log, 'y'), cnt, false);
                if (s.isSolid(rx, y - 1, rz)) { s.set(rx, y - 1, rz, "minecraft:rooted_dirt"); cnt[0]++; }
            }
        }
        // branches
        List<double[]> blobs = new ArrayList<>();
        int nb = 2 + (height >= 8 && r.nextDouble() < 0.6 ? 1 : 0);
        double a0 = Math.atan2(lean.dz, lean.dx);
        for (int b = 0; b < nb; b++) {
            // majorité du côté de la pente : écart ±80° autour de lean pour les deux premières
            double ang = b < 2 ? a0 + (b == 0 ? -1 : 1) * (0.5 + r.nextDouble() * 0.9) : a0 + Math.PI + (r.nextDouble() - 0.5);
            int hi = (int) Math.round(height * (0.45 + 0.4 * (b + r.nextDouble() * 0.5) / nb));
            hi = Math.max(2, Math.min(height - 2, hi));
            int[] base = trunk.get(Math.min(trunk.size() - 1, hi));
            int len = 2 + r.nextInt(3) + (b == 0 ? 1 : 0);
            double ca = Math.cos(ang), sa = Math.sin(ang);
            int px = base[0], py = base[1], pz = base[2];
            for (int st = 1; st <= len; st++) {
                int nx = base[0] + (int) Math.round(ca * st), nz = base[2] + (int) Math.round(sa * st);
                int ny = base[1] + st / 2;
                char ax = Math.abs(ca) > Math.abs(sa) ? 'x' : 'z';
                if (ny != py && (nx != px || nz != pz)) put(s, nx, py, nz, B.axis(log, ax), cnt, true); // coude
                put(s, nx, ny, nz, B.axis(log, ax), cnt, true);
                px = nx; py = ny; pz = nz;
            }
            blobs.add(new double[]{px, py + 1, pz, crown * (0.75 + 0.1 * r.nextDouble())});
        }
        int[] tt = trunk.get(trunk.size() - 1);
        blobs.add(new double[]{tt[0], tt[1] + 1, tt[2], crown});
        for (double[] bl : blobs) blob(s, bl[0], bl[1], bl[2], bl[3], leafNoise, cnt);
        return cnt[0];
    }

    private void blob(Sink s, double bx, double by, double bz, double R, Noise nz, int[] cnt) {
        int Rr = (int) Math.ceil(R) + 1;
        String l1 = B.leaves(leaves), l2 = leaves2 == null ? l1 : B.leaves(leaves2);
        for (int dy = -Rr; dy <= Rr; dy++)
            for (int dz = -Rr; dz <= Rr; dz++)
                for (int dx = -Rr; dx <= Rr; dx++) {
                    int x = (int) Math.round(bx) + dx, y = (int) Math.round(by) + dy, z = (int) Math.round(bz) + dz;
                    double fy = dy < 0 ? dy * 1.6 : dy * 0.95; // plus haut que bas
                    double rr = R * (0.7 + 0.3 * nz.uniform3(x / 2.5, y / 2.5, z / 2.5));
                    if (dx * dx + fy * fy + dz * dz > rr * rr) continue;
                    if (!s.isAir(x, y, z)) continue;
                    boolean second = nz.uniform3(x / 3.0 + 50, y / 3.0, z / 3.0 - 20) < leaves2Share;
                    s.set(x, y, z, second ? l2 : l1);
                    cnt[0]++;
                }
    }

    private static void put(Sink s, int x, int y, int z, String st, int[] cnt, boolean force) {
        if (!force && !s.isAir(x, y, z)) return;
        String cur = s.get(x, y, z);
        if (!B.isAir(cur) && B.isSolid(cur) && !B.name(cur).contains("leaves") && !force) return;
        s.set(x, y, z, st);
        cnt[0]++;
    }
}
