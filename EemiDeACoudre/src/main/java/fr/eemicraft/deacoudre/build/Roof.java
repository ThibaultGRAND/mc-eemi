package fr.eemicraft.deacoudre.build;

import java.util.ArrayList;
import java.util.List;

/**
 * Toits (règles D1-D8 du skill). Un toit est un CHAMP DE HAUTEUR : chaque partie (pignon ou croupe sur un
 * rectangle) donne une hauteur par colonne, les parties se composent par {@code max} (toits en L, T, croix, noues).
 * La hauteur est ensuite discrétisée : escaliers à 45° tournés vers la montée (ou dalles hautes/basses à 22,5°),
 * faîte en dalles, débord de {@code overhang} avec escaliers retournés en sous-face, une couche de « corps »
 * sous les escaliers (combles creux), pignons fermés (en retrait derrière des poteaux si {@code gablePost}).
 * Les formes d'angle (outer/inner) sont calculées comme Minecraft par {@link Stairs#fix}.
 *
 * <p>Convention de hauteur : {@code yTop} = rang de la corniche de la façade (le même que pour {@link Facade}).
 * Le toit touche le haut du mur au droit de la façade et descend d'un bloc par bloc de débord.
 */
public final class Roof {
    private Roof() {}

    public enum Type { GABLE_X, GABLE_Z, HIP }

    /** Une partie de toit sur l'emprise (x1..x2, z1..z2) d'un bâtiment dont la corniche est en yTop. */
    public static final class Part {
        public final int x1, z1, x2, z2, yTop;
        public final Type type;
        public double slope = 1.0;

        public Part(Type type, int x1, int z1, int x2, int z2, int yTop) {
            this.type = type;
            this.x1 = Math.min(x1, x2); this.x2 = Math.max(x1, x2);
            this.z1 = Math.min(z1, z2); this.z2 = Math.max(z1, z2);
            this.yTop = yTop;
        }

        /** Pente en blocs par bloc (1 = 45°, 0,5 = 22,5°, &gt;1 impossible en escaliers). */
        public Part slope(double s) { slope = s; return this; }

        boolean contains(int x, int z) { return x >= x1 && x <= x2 && z >= z1 && z <= z2; }
    }

    /** Pignon, faîtage parallèle à X (pignons sur les murs ouest et est). */
    public static Part gableX(int x1, int z1, int x2, int z2, int yTop) { return new Part(Type.GABLE_X, x1, z1, x2, z2, yTop); }
    /** Pignon, faîtage parallèle à Z (pignons sur les murs nord et sud). */
    public static Part gableZ(int x1, int z1, int x2, int z2, int yTop) { return new Part(Type.GABLE_Z, x1, z1, x2, z2, yTop); }
    /** Croupe (4 pans). */
    public static Part hip(int x1, int z1, int x2, int z2, int yTop) { return new Part(Type.HIP, x1, z1, x2, z2, yTop); }

    private static final double EPS = 1e-6;

    /** Construit le toit composé des parties données. Renvoie le Y le plus haut posé (faîte). */
    public static int build(Sink s, RoofStyle rs, Part... parts) {
        if (parts.length == 0) return Integer.MIN_VALUE;
        int o = rs.overhang;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Part p : parts) {
            minX = Math.min(minX, p.x1 - o); minZ = Math.min(minZ, p.z1 - o);
            maxX = Math.max(maxX, p.x2 + o); maxZ = Math.max(maxZ, p.z2 + o);
        }
        int W = maxX - minX + 1, L = maxZ - minZ + 1;
        double[][] H = new double[W][L];
        int[][] own = new int[W][L];
        double[] y0 = new double[parts.length], yMax = new double[parts.length];
        for (double[] row : H) java.util.Arrays.fill(row, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < parts.length; i++) {
            Part p = parts[i];
            y0[i] = p.yTop + 1 - Math.floor(o * p.slope + EPS);
            yMax[i] = y0[i];
            for (int x = p.x1 - o; x <= p.x2 + o; x++)
                for (int z = p.z1 - o; z <= p.z2 + o; z++) {
                    double dz = Math.min(z - (p.z1 - o), (p.z2 + o) - z);
                    double dx = Math.min(x - (p.x1 - o), (p.x2 + o) - x);
                    double d = switch (p.type) {
                        case GABLE_X -> dz;
                        case GABLE_Z -> dx;
                        case HIP -> Math.min(dx, dz);
                    };
                    double h = y0[i] + d * p.slope;
                    yMax[i] = Math.max(yMax[i], h);
                    if (h > H[x - minX][z - minZ] + EPS) { H[x - minX][z - minZ] = h; own[x - minX][z - minZ] = i; }
                }
        }
        int top = Integer.MIN_VALUE;
        // 1. surface
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                double h = H[x - minX][z - minZ];
                if (h == Double.NEGATIVE_INFINITY) continue;
                int pi = own[x - minX][z - minZ];
                Part p = parts[pi];
                int lv = (int) Math.floor(h + EPS);
                Dir[] pref = p.type == Type.GABLE_Z ? new Dir[]{Dir.EAST, Dir.WEST, Dir.NORTH, Dir.SOUTH}
                        : new Dir[]{Dir.NORTH, Dir.SOUTH, Dir.EAST, Dir.WEST};
                Dir face = null;
                double best = h + EPS;
                for (Dir d : pref) {
                    double hn = at(H, minX, minZ, x + d.dx, z + d.dz);
                    if (hn > best + 1e-9) { best = hn; face = d; }
                }
                if (face == null) { // coin de croupe : seul un voisin en diagonale est plus haut
                    for (int ddx = -1; ddx <= 1 && face == null; ddx += 2)
                        for (int ddz = -1; ddz <= 1 && face == null; ddz += 2)
                            if (at(H, minX, minZ, x + ddx, z + ddz) > h + EPS)
                                face = p.type == Type.GABLE_Z ? Dir.fromDelta(ddx, 0) : Dir.fromDelta(0, ddz);
                }
                boolean in = footprint(parts, x, z);
                int wallTop = in ? topOf(parts, x, z) : p.yTop;
                double t = 1 - (h - y0[pi]) / Math.max(EPS, yMax[pi] - y0[pi]);
                String stairId = rs.stairs.pick(x, lv, z, Math.max(0, Math.min(1, t)));
                if (!rs.slabs) {
                    if (face == null) {
                        s.set(x, lv, z, B.slab(rs.ridgeSlab, "bottom"));
                        if (lv - 1 > wallTop) s.set(x, lv - 1, z, rs.body);
                    } else {
                        s.set(x, lv, z, B.stairs(stairId, face, false));
                        if (in) { if (lv - 1 > wallTop) s.set(x, lv - 1, z, rs.body); }
                        else if (lv > y0[pi] + EPS) s.set(x, lv - 1, z, B.stairs(stairId, face, true));
                    }
                } else {
                    boolean upper = h - lv >= 0.5 - EPS;
                    if (face == null && upper) s.set(x, lv, z, rs.body);
                    else s.set(x, lv, z, B.slab(rs.slab, upper ? "top" : "bottom"));
                    if (in && !upper && lv - 1 > wallTop) s.set(x, lv - 1, z, rs.body);
                    else if (!in && !upper && lv > y0[pi] + EPS) s.set(x, lv - 1, z, B.slab(rs.slab, "top"));
                }
                top = Math.max(top, lv);
            }
        // 2. pignons
        for (Part p : parts) {
            if (p.type == Type.HIP) continue;
            List<int[]> ends = new ArrayList<>(); // {x0, z0, alongOrdinal, len, outOrdinal}
            if (p.type == Type.GABLE_X) {
                ends.add(new int[]{p.x1, p.z1, Dir.SOUTH.ordinal(), p.z2 - p.z1, Dir.WEST.ordinal()});
                ends.add(new int[]{p.x2, p.z1, Dir.SOUTH.ordinal(), p.z2 - p.z1, Dir.EAST.ordinal()});
            } else {
                ends.add(new int[]{p.x1, p.z1, Dir.EAST.ordinal(), p.x2 - p.x1, Dir.NORTH.ordinal()});
                ends.add(new int[]{p.x1, p.z2, Dir.EAST.ordinal(), p.x2 - p.x1, Dir.SOUTH.ordinal()});
            }
            for (int[] e : ends) {
                Dir along = Dir.values()[e[2]], out = Dir.values()[e[4]], in = out.opposite();
                int len = e[3];
                boolean[] post = new boolean[len + 1];
                for (int q : Facade.postPositions(len, rs.postSpacing)) post[q] = true;
                post[0] = true; post[len] = true;
                for (int t = 0; t <= len; t++) {
                    int x = e[0] + along.dx * t, z = e[1] + along.dz * t;
                    if (footprint(parts, x + out.dx, z + out.dz)) continue; // pas une face extérieure
                    double h = at(H, minX, minZ, x, z);
                    if (h == Double.NEGATIVE_INFINITY) continue;
                    int lv = (int) Math.floor(h + EPS);
                    int fillTop = lv - 1;
                    if (fillTop <= p.yTop) continue;
                    if (rs.gablePost != null) {
                        if (post[t]) for (int y = p.yTop + 1; y <= fillTop; y++) s.set(x, y, z, B.axis(rs.gablePost, 'y'));
                        int ix = x + in.dx, iz = z + in.dz;
                        double hi = at(H, minX, minZ, ix, iz);
                        int top2 = hi == Double.NEGATIVE_INFINITY ? fillTop : (int) Math.floor(hi + EPS) - 1;
                        for (int y = p.yTop + 1; y <= top2; y++) s.set(ix, y, iz, rs.gable);
                    } else {
                        for (int y = p.yTop + 1; y <= fillTop; y++) s.set(x, y, z, rs.gable);
                    }
                }
            }
        }
        // 3. formes d'angle des escaliers (croupes, noues, coins de corniche)
        int yLo = Integer.MAX_VALUE;
        for (Part p : parts) yLo = Math.min(yLo, p.yTop - o - 1);
        Stairs.fix(s, minX - 1, yLo, minZ - 1, maxX + 1, top + 1, maxZ + 1);
        return top;
    }

    private static double at(double[][] H, int minX, int minZ, int x, int z) {
        int i = x - minX, k = z - minZ;
        if (i < 0 || k < 0 || i >= H.length || k >= H[0].length) return Double.NEGATIVE_INFINITY;
        return H[i][k];
    }

    private static boolean footprint(Part[] parts, int x, int z) {
        for (Part p : parts) if (p.contains(x, z)) return true;
        return false;
    }

    private static int topOf(Part[] parts, int x, int z) {
        int t = Integer.MIN_VALUE;
        for (Part p : parts) if (p.contains(x, z)) t = Math.max(t, p.yTop);
        return t;
    }

    /**
     * Toit plat (ville / désert seulement) : surface en yTop, acrotère de {@code parapetH} rangs plein(s) sur le
     * pourtour puis couvertine en dalles, et quelques éléments techniques si {@code technical}.
     */
    public static void flat(Sink s, int x1, int z1, int x2, int z2, int yTop, Mat surface, Mat parapet,
                            String copingSlab, int parapetH, boolean technical) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        Shapes.fill(s, ax + 1, yTop, az + 1, bx - 1, yTop, bz - 1, surface);
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++) {
                if (x != ax && x != bx && z != az && z != bz) continue;
                for (int y = yTop + 1; y <= yTop + parapetH; y++) s.set(x, y, z, parapet);
                s.set(x, yTop + parapetH + 1, z, B.slab(copingSlab, "bottom"));
            }
        if (technical && bx - ax >= 6 && bz - az >= 6) {
            // caisson de ventilation 2×2 + grille, et un paratonnerre dans un coin
            int vx = ax + 2, vz = bz - 3;
            Shapes.fill(s, vx, yTop + 1, vz, vx + 1, yTop + 1, vz + 1, Mat.of("minecraft:smooth_stone"));
            Shapes.fill(s, vx, yTop + 2, vz, vx + 1, yTop + 2, vz + 1, Mat.of("minecraft:iron_trapdoor[half=bottom]"));
            s.set(bx - 2, yTop + 1, az + 2, "minecraft:lightning_rod");
        }
    }

    /** Cheminée : bloc plein de yFrom à yTo (dépasse le faîte de 2-3), couronne de dalles au sommet. */
    public static void chimney(Sink s, int x1, int z1, int x2, int z2, int yFrom, int yTo, Mat m, String capSlab) {
        Shapes.fill(s, x1, yFrom, z1, x2, yTo, z2, m);
        if (capSlab != null) Shapes.fill(s, x1, yTo + 1, z1, x2, yTo + 1, z2, Mat.of(B.slab(capSlab, "bottom")));
    }
}
