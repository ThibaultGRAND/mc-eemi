package fr.eemicraft.elytra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Trace d'un parcours : anneaux places de facon deterministe (graine fixe), puis ligne de vol lissee
 * (spline de Catmull-Rom echantillonnee tous les 1,5 bloc). Tout le reste (decor, detection) s'appuie dessus.
 */
final class Course {
    static final double SPACING = 1.5;
    static final int CELL = 8;
    /** Pente de plane : 1 bloc de descente pour GLIDE blocs parcourus. */
    static final double GLIDE = 4.5;
    /** Montee prevue sur le troncon qui suit un anneau boost. */
    static final double CLIMB = 18;
    static final double MIN_Y = 95;

    static final class Ring {
        final int index;
        final double x, y, z;
        double nx, ny, nz;
        final double r;
        final boolean boost, finish;
        double s;

        Ring(int index, double x, double y, double z, double r, boolean boost, boolean finish) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.boost = boost;
            this.finish = finish;
        }
    }

    final String id, name, theme;
    final int ox, oz, ys;
    final long seed;
    final List<Ring> rings = new ArrayList<>();
    double[] sx, sy, sz, ss;
    int n;
    double length;
    double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
    double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
    /** Point de depart de la ligne (devant la porte de la tour). */
    double p0x, p0y, p0z;
    /** Fin de la ligne : terrasse d'arrivee. */
    double endX, endY, endZ, endDirX, endDirZ;
    private final Map<Long, int[]> grid = new HashMap<>();

    Course(String id, String name, String theme, int ox, int oz, int ys, long seed, int count) {
        this.id = id;
        this.name = name;
        this.theme = theme;
        this.ox = ox;
        this.oz = oz;
        this.ys = ys;
        this.seed = seed;
        plan(count);
    }

    private void plan(int count) {
        Random rnd = new Random(seed);
        List<double[]> pts = new ArrayList<>();
        double x = ox + 2.5, y = ys + 1.0, z = oz + 0.5;
        p0x = x;
        p0y = y;
        p0z = z;
        pts.add(new double[]{x, y, z});
        double h = 0;
        boolean[] boost = new boolean[count + 1];
        for (int i = 1; i <= count; i++) {
            boolean fin = i == count;
            double len = i == 1 ? 42 : fin ? 55 : 56 + rnd.nextDouble() * 20;
            if (!fin) {
                h = h * 0.55 + (rnd.nextDouble() * 2 - 1) * 0.6;
                h = Math.max(-1.0, Math.min(1.0, h));
            } else {
                h *= 0.5;
            }
            x += Math.cos(h) * len;
            z += Math.sin(h) * len;
            double dy;
            if (i == 1) {
                dy = -len / 4.0;
            } else if (boost[i - 1]) {
                dy = CLIMB;
            } else {
                dy = -len / GLIDE;
            }
            y = Math.max(MIN_Y, y + dy);
            boost[i] = !fin && i % 3 == 0 && i < count - 1;
            pts.add(new double[]{x, y, z});
            rings.add(new Ring(i - 1, x, y, z, fin ? 6.0 : boost[i] ? 4.5 : 4.0, boost[i], fin));
        }
        endDirX = Math.cos(h);
        endDirZ = Math.sin(h);
        endX = x + endDirX * 45;
        endZ = z + endDirZ * 45;
        endY = y - 4;
        pts.add(new double[]{endX, endY, endZ});

        // points de controle virtuels aux deux bouts
        int m = pts.size();
        List<double[]> c = new ArrayList<>();
        c.add(new double[]{p0x - 20, p0y, p0z});
        c.addAll(pts);
        double[] a = pts.get(m - 2), b = pts.get(m - 1);
        c.add(new double[]{2 * b[0] - a[0], 2 * b[1] - a[1], 2 * b[2] - a[2]});

        List<double[]> samples = new ArrayList<>();
        double[] ringS = new double[count];
        double s = 0;
        double[] prev = null;
        for (int k = 1; k < c.size() - 2; k++) {
            double[] q0 = c.get(k - 1), q1 = c.get(k), q2 = c.get(k + 1), q3 = c.get(k + 2);
            // ce point de controle est l'anneau k-1 (k = 1 : depart)
            if (k >= 2 && k - 2 < count) {
                Ring r = rings.get(k - 2);
                double tx = 0.5 * (q2[0] - q0[0]), ty = 0.5 * (q2[1] - q0[1]), tz = 0.5 * (q2[2] - q0[2]);
                double l = Math.sqrt(tx * tx + ty * ty + tz * tz);
                r.nx = tx / l;
                r.ny = ty / l;
                r.nz = tz / l;
            }
            double segLen = dist(q1, q2);
            int steps = Math.max(1, (int) Math.ceil(segLen / SPACING));
            for (int j = 0; j < steps; j++) {
                double t = (double) j / steps;
                double[] p = cr(q0, q1, q2, q3, t);
                if (prev != null) {
                    s += dist(prev, p);
                }
                if (j == 0 && k >= 2 && k - 2 < count) {
                    ringS[k - 2] = s;
                }
                samples.add(p);
                prev = p;
            }
        }
        double[] last = c.get(c.size() - 2);
        s += dist(prev, last);
        samples.add(last);
        n = samples.size();
        sx = new double[n];
        sy = new double[n];
        sz = new double[n];
        ss = new double[n];
        double acc = 0;
        for (int i = 0; i < n; i++) {
            double[] p = samples.get(i);
            if (i > 0) {
                acc += dist(samples.get(i - 1), p);
            }
            sx[i] = p[0];
            sy[i] = p[1];
            sz[i] = p[2];
            ss[i] = acc;
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[2]);
            maxZ = Math.max(maxZ, p[2]);
            long key = key(Math.floorDiv((int) Math.floor(p[0]), CELL), Math.floorDiv((int) Math.floor(p[2]), CELL));
            int[] arr = grid.get(key);
            if (arr == null) {
                arr = new int[]{i};
            } else {
                arr = java.util.Arrays.copyOf(arr, arr.length + 1);
                arr[arr.length - 1] = i;
            }
            grid.put(key, arr);
        }
        length = acc;
        for (int i = 0; i < count; i++) {
            rings.get(i).s = ringS[i];
        }
    }

    static double[] cr(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        double[] o = new double[3];
        for (int i = 0; i < 3; i++) {
            o[i] = 0.5 * ((2 * p1[i]) + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2 + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3);
        }
        return o;
    }

    static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** Echantillons dont la position (x, z) tombe dans le rectangle donne. */
    int[] near(double x1, double z1, double x2, double z2) {
        int cx1 = Math.floorDiv((int) Math.floor(x1), CELL), cx2 = Math.floorDiv((int) Math.floor(x2), CELL);
        int cz1 = Math.floorDiv((int) Math.floor(z1), CELL), cz2 = Math.floorDiv((int) Math.floor(z2), CELL);
        int[] out = new int[64];
        int k = 0;
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                int[] arr = grid.get(key(cx, cz));
                if (arr == null) {
                    continue;
                }
                for (int i : arr) {
                    if (k == out.length) {
                        out = java.util.Arrays.copyOf(out, out.length * 2);
                    }
                    out[k++] = i;
                }
            }
        }
        return java.util.Arrays.copyOf(out, k);
    }

    /** Vrai si le point est a moins de r de la ligne de vol (le decor n'y est jamais pose). */
    boolean inTube(double x, double y, double z, double r) {
        double r2 = r * r;
        int cx1 = Math.floorDiv((int) Math.floor(x - r), CELL), cx2 = Math.floorDiv((int) Math.floor(x + r), CELL);
        int cz1 = Math.floorDiv((int) Math.floor(z - r), CELL), cz2 = Math.floorDiv((int) Math.floor(z + r), CELL);
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                int[] arr = grid.get(key(cx, cz));
                if (arr == null) {
                    continue;
                }
                for (int i : arr) {
                    double dx = sx[i] - x, dy = sy[i] - y, dz = sz[i] - z;
                    if (dx * dx + dy * dy + dz * dz < r2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Indice de l'echantillon le plus proche de l'abscisse s. */
    int sampleAt(double s) {
        int lo = 0, hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (ss[mid] < s) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    int boosts() {
        int b = 0;
        for (Ring r : rings) {
            if (r.boost) {
                b++;
            }
        }
        return b;
    }
}
