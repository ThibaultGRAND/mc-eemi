package fr.eemicraft.build;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Bruit simplex 2D / 3D avec graine (portage de « Simplex noise demystified », Stefan Gustavson, 2005,
 * domaine public) + FBM, crêtes, domain warp et une version « uniforme » (valeurs réparties
 * à peu près uniformément dans [0,1], pratique pour fixer une proportion : u &lt; 0.15 → 15 % des cases).
 *
 * <p>noise2 / noise3 rendent environ [-1, 1]. Une période P (en blocs) = coordonnées divisées par P.
 */
public final class Noise {
    private static final int[][] GRAD3 = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    public final long seed;
    private final short[] perm = new short[512];
    private final short[] permMod12 = new short[512];

    public Noise(long seed) {
        this.seed = seed;
        short[] p = new short[256];
        for (int i = 0; i < 256; i++) p[i] = (short) i;
        SplittableRandom r = new SplittableRandom(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            short t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = (short) (perm[i] % 12);
        }
    }

    /** Nouveau bruit indépendant dérivé de celui-ci (autre canal). */
    public Noise derive(long salt) {
        return new Noise(seed * 0x9E3779B97F4A7C15L + salt);
    }

    private static int fastFloor(double x) {
        int i = (int) x;
        return x < i ? i - 1 : i;
    }

    private static double dot(int[] g, double x, double y) { return g[0] * x + g[1] * y; }
    private static double dot(int[] g, double x, double y, double z) { return g[0] * x + g[1] * y + g[2] * z; }

    /** Simplex 2D, environ [-1, 1]. */
    public double noise2(double xin, double yin) {
        double n0, n1, n2;
        double s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        double t = (i + j) * G2;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2, y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255, jj = j & 255;
        int gi0 = permMod12[ii + perm[jj]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1]];
        int gi2 = permMod12[ii + 1 + perm[jj + 1]];
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 < 0) n0 = 0.0; else { t0 *= t0; n0 = t0 * t0 * dot(GRAD3[gi0], x0, y0); }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0) n1 = 0.0; else { t1 *= t1; n1 = t1 * t1 * dot(GRAD3[gi1], x1, y1); }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 < 0) n2 = 0.0; else { t2 *= t2; n2 = t2 * t2 * dot(GRAD3[gi2], x2, y2); }
        return 70.0 * (n0 + n1 + n2);
    }

    /** Simplex 3D, environ [-1, 1]. */
    public double noise3(double xin, double yin, double zin) {
        double n0, n1, n2, n3;
        double s = (xin + yin + zin) * F3;
        int i = fastFloor(xin + s), j = fastFloor(yin + s), k = fastFloor(zin + s);
        double t = (i + j + k) * G3;
        double x0 = xin - (i - t), y0 = yin - (j - t), z0 = zin - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3, y2 = y0 - j2 + 2.0 * G3, z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3, y3 = y0 - 1.0 + 3.0 * G3, z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & 255, jj = j & 255, kk = k & 255;
        int gi0 = permMod12[ii + perm[jj + perm[kk]]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]];
        int gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]];
        int gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]];
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 < 0) n0 = 0.0; else { t0 *= t0; n0 = t0 * t0 * dot(GRAD3[gi0], x0, y0, z0); }
        double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 < 0) n1 = 0.0; else { t1 *= t1; n1 = t1 * t1 * dot(GRAD3[gi1], x1, y1, z1); }
        double t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 < 0) n2 = 0.0; else { t2 *= t2; n2 = t2 * t2 * dot(GRAD3[gi2], x2, y2, z2); }
        double t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 < 0) n3 = 0.0; else { t3 *= t3; n3 = t3 * t3 * dot(GRAD3[gi3], x3, y3, z3); }
        return 32.0 * (n0 + n1 + n2 + n3);
    }

    /** FBM 2D normalisé (environ [-1, 1]). Coordonnées déjà divisées par la période. */
    public double fbm2(double x, double z, int octaves, double persistence, double lacunarity) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise2(x * f + o * 17.31, z * f - o * 9.73);
            norm += amp; amp *= persistence; f *= lacunarity;
        }
        return sum / norm;
    }

    public double fbm3(double x, double y, double z, int octaves, double persistence, double lacunarity) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise3(x * f + o * 17.31, y * f + o * 5.17, z * f - o * 9.73);
            norm += amp; amp *= persistence; f *= lacunarity;
        }
        return sum / norm;
    }

    /** Crêtes : somme de (1-|n|)², normalisée dans [0, 1] (1 = sommet de crête). */
    public double ridge2(double x, double z, int octaves, double persistence, double lacunarity) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < octaves; o++) {
            double n = 1 - Math.abs(noise2(x * f + o * 17.31, z * f - o * 9.73));
            sum += amp * n * n;
            norm += amp; amp *= persistence; f *= lacunarity;
        }
        return sum / norm;
    }

    /**
     * Domain warp : renvoie {x', z'} = (x, z) décalés de ±amp blocs par un bruit de période {@code period}.
     * À appliquer AVANT l'échantillonnage d'un autre bruit (amp 10-20 = formes organiques).
     */
    public double[] warp2(double x, double z, double amp, double period) {
        double wx = noise2(x / period + 31.7, z / period + 11.3);
        double wz = noise2(x / period - 17.1, z / period + 47.9);
        return new double[]{x + amp * wx, z + amp * wz};
    }

    // ---------- valeurs uniformes ----------

    private static double[] Q2, Q3;

    private static synchronized void initQuantiles() {
        if (Q2 != null) return;
        Noise n = new Noise(0x5EEDL);
        SplittableRandom r = new SplittableRandom(42);
        int N = 1 << 15;
        double[] a = new double[N], b = new double[N];
        for (int i = 0; i < N; i++) {
            a[i] = n.noise2(r.nextDouble() * 4096 - 2048, r.nextDouble() * 4096 - 2048);
            b[i] = n.noise3(r.nextDouble() * 4096 - 2048, r.nextDouble() * 4096 - 2048, r.nextDouble() * 4096 - 2048);
        }
        Arrays.sort(a); Arrays.sort(b);
        Q3 = b; Q2 = a;
    }

    private static double cdf(double[] q, double v) {
        int i = Arrays.binarySearch(q, v);
        if (i < 0) i = -i - 1;
        return Math.min(0.999999, i / (double) q.length);
    }

    /** noise2 ramené à une répartition uniforme dans [0,1). */
    public double uniform2(double x, double z) {
        if (Q2 == null) initQuantiles();
        return cdf(Q2, noise2(x, z));
    }

    /** noise3 ramené à une répartition uniforme dans [0,1). */
    public double uniform3(double x, double y, double z) {
        if (Q3 == null) initQuantiles();
        return cdf(Q3, noise3(x, y, z));
    }

    // ---------- hachage déterministe (petits décalages, PAS pour choisir une variante bloc par bloc) ----------

    public static double hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x632BE59BD9B4E019L) ^ (y * 0x9E3779B97F4A7C15L) ^ (z * 0x85157AF5L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
