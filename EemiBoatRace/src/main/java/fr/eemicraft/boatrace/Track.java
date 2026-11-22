package fr.eemicraft.boatrace;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Géométrie du circuit (sans Bukkit, testable à part avec {@link #main}).
 * Le tracé est une polyligne fermée de points de contrôle (en blocs, échelle 1), multipliée par
 * l'échelle, lissée (Chaikin) puis rééchantillonnée tous les ~1 bloc. L'échantillon 0 est sur la
 * ligne d'arrivée (0.5, 0.5), la course part vers +x.
 * Sert à la fois à construire la piste (distance de chaque case à la ligne médiane) et à suivre
 * la progression des bateaux (position le long du tracé).
 */
final class Track {

    /**
     * Ligne médiane du circuit, dans le sens de la course. Ligne droite des stands en z = 0
     * (ligne d'arrivée en x = 0), grand virage relevé à l'est, chicane au nord, deux épingles,
     * deux virages serrés à angle droit à l'ouest. Les points rapprochés autour d'un angle
     * gardent le virage serré après lissage.
     */
    static final double[][] CONTROL = {
            {0, 0}, {40, 0}, {70, 0},
            // grand virage (large, à droite puis vers le nord)
            {92, 4}, {106, 18}, {110, 40}, {104, 62}, {88, 76}, {65, 80},
            // chicane
            {50, 80}, {40, 80}, {32, 90}, {20, 90}, {12, 80}, {0, 80},
            // épingle ouest
            {-20, 80}, {-31, 76}, {-36, 67}, {-31, 58}, {-20, 55},
            // ligne droite du milieu
            {5, 55}, {16, 55},
            // épingle est
            {27, 51}, {31, 41.5}, {27, 32}, {16, 28},
            // ligne droite du dos
            {0, 28}, {-40, 28},
            // deux virages serrés à angle droit
            {-69, 28}, {-75, 28}, {-75, 22},
            {-75, 6}, {-75, 0}, {-69, 0},
            {-40, 0},
    };

    final double step;
    final double length;
    final int n;
    final double[] x, z, tx, tz;
    /**
     * Niveau du sol de chaque échantillon (y où repose le bateau) et échantillons « posés » : false dans
     * un saut, un ascenseur à bulles ou une zone libre (on n'y replace personne). Circuit procédural :
     * niveau constant fixé par {@link #setFloor}.
     */
    final double[] y;
    final boolean[] solid;

    Track(double scale, double targetStep) {
        this(scaled(scale), 3, 0.5, 0.5, targetStep);
    }

    void setFloor(double floor) {
        java.util.Arrays.fill(y, floor);
    }

    private static double[][] scaled(double scale) {
        double[][] pts = new double[CONTROL.length][2];
        for (int i = 0; i < pts.length; i++) {
            pts[i][0] = CONTROL[i][0] * scale + 0.5;
            pts[i][1] = CONTROL[i][1] * scale + 0.5;
        }
        return pts;
    }

    /**
     * Tracé quelconque (circuit importé) : polyligne fermée dans le sens de la course, en coordonnées
     * monde, lissée « smoothing » fois ; la ligne d'arrivée est le point du tracé le plus proche de (fx, fz).
     * Chaque point est {x, z} ou {x, z, sol, posé (1/0)}.
     */
    Track(double[][] control, int smoothing, double fx, double fz, double targetStep) {
        double[][] pts = chaikin(control, smoothing);
        // longueur de la polyligne lissée
        int m = pts.length;
        double[] cum = new double[m + 1];
        for (int i = 0; i < m; i++) {
            double[] a = pts[i], b = pts[(i + 1) % m];
            cum[i + 1] = cum[i] + Math.hypot(b[0] - a[0], b[1] - a[1]);
        }
        double total = cum[m];
        // position de la ligne d'arrivée (point le plus proche de fx, fz)
        double s0 = 0, best = Double.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            double[] a = pts[i], b = pts[(i + 1) % m];
            double dx = b[0] - a[0], dz = b[1] - a[1], len2 = dx * dx + dz * dz;
            double t = len2 == 0 ? 0 : ((fx - a[0]) * dx + (fz - a[1]) * dz) / len2;
            t = Math.max(0, Math.min(1, t));
            double px = a[0] + t * dx, pz = a[1] + t * dz;
            double d = Math.hypot(px - fx, pz - fz);
            if (d < best) { best = d; s0 = cum[i] + t * (cum[i + 1] - cum[i]); }
        }
        n = Math.max(16, (int) Math.round(total / targetStep));
        step = total / n;
        length = total;
        x = new double[n];
        z = new double[n];
        y = new double[n];
        solid = new boolean[n];
        int seg = 0;
        for (int k = 0; k < n; k++) {
            double s = (s0 + k * step) % total;
            // segment qui contient s (recherche linéaire circulaire)
            seg = 0;
            while (seg < m - 1 && cum[seg + 1] < s) seg++;
            double[] a = pts[seg], b = pts[(seg + 1) % m];
            double segLen = cum[seg + 1] - cum[seg];
            double t = segLen == 0 ? 0 : (s - cum[seg]) / segLen;
            x[k] = a[0] + t * (b[0] - a[0]);
            z[k] = a[1] + t * (b[1] - a[1]);
            if (a.length >= 4) {
                y[k] = a[2] + t * (b[2] - a[2]);
                // posé seulement si les deux points du segment le sont (le lissage mélange les drapeaux)
                solid[k] = a[3] > 0.99 && b[3] > 0.99;
            } else solid[k] = true;
        }
        tx = new double[n];
        tz = new double[n];
        for (int k = 0; k < n; k++) {
            double dx = x[(k + 1) % n] - x[(k - 1 + n) % n], dz = z[(k + 1) % n] - z[(k - 1 + n) % n];
            double l = Math.hypot(dx, dz);
            tx[k] = l == 0 ? 1 : dx / l;
            tz[k] = l == 0 ? 0 : dz / l;
        }
    }

    /** Lissage de Chaikin d'une polyligne fermée. */
    static double[][] chaikin(double[][] pts, int iterations) {
        for (int it = 0; it < iterations; it++) {
            int m = pts.length;
            int c = pts[0].length;
            double[][] out = new double[m * 2][c];
            for (int i = 0; i < m; i++) {
                double[] a = pts[i], b = pts[(i + 1) % m];
                for (int k = 0; k < c; k++) {
                    out[2 * i][k] = 0.75 * a[k] + 0.25 * b[k];
                    out[2 * i + 1][k] = 0.25 * a[k] + 0.75 * b[k];
                }
            }
            pts = out;
        }
        return pts;
    }

    /** s ramené dans [0, length). */
    double mod(double s) {
        double r = s % length;
        return r < 0 ? r + length : r;
    }

    /** Écart de position le long du tracé, ramené dans [-length/2, length/2). */
    double wrap(double d) {
        double r = mod(d + length / 2) - length / 2;
        return r;
    }

    int index(double s) {
        return (int) Math.floor(mod(s) / step) % n;
    }

    /** Point de la ligne médiane à la position s : {x, z, tx, tz}. */
    double[] at(double s) {
        s = mod(s);
        int i = (int) Math.floor(s / step) % n, j = (i + 1) % n;
        double t = s / step - Math.floor(s / step);
        double px = x[i] + t * (x[j] - x[i]), pz = z[i] + t * (z[j] - z[i]);
        double dx = tx[i] + t * (tx[j] - tx[i]), dz = tz[i] + t * (tz[j] - tz[i]);
        double l = Math.hypot(dx, dz);
        return new double[]{px, pz, dx / l, dz / l};
    }

    /** Niveau du sol à la position s. */
    double yAt(double s) {
        s = mod(s);
        int i = (int) Math.floor(s / step) % n, j = (i + 1) % n;
        double t = s / step - Math.floor(s / step);
        return y[i] + t * (y[j] - y[i]);
    }

    /** Sol le plus bas autour de l'échantillon i (± radius échantillons) : un bateau plus bas est tombé. */
    double minY(int i, int radius) {
        double m = Double.MAX_VALUE;
        for (int k = -radius; k <= radius; k++) m = Math.min(m, y[Math.floorMod(i + k, n)]);
        return m;
    }

    double minY() {
        double m = Double.MAX_VALUE;
        for (double v : y) m = Math.min(m, v);
        return m;
    }

    /** Position posée la plus proche en reculant depuis s (pour replacer un bateau hors d'un saut ou d'un ascenseur). */
    double solidBack(double s) {
        for (int k = 0; k < n; k++) {
            double t = s - k * step;
            if (solid[index(t)] && solid[index(t + step)]) return t;
        }
        return s;
    }

    double distSq(int i, double px, double pz) {
        double dx = px - x[i], dz = pz - z[i];
        return dx * dx + dz * dz;
    }

    /** Échantillon le plus proche, cherché seulement de from - back à from + ahead (anti-raccourci). */
    int nearest(double px, double pz, int from, int back, int ahead) {
        int best = from;
        double bd = Double.MAX_VALUE;
        for (int k = -back; k <= ahead; k++) {
            int i = Math.floorMod(from + k, n);
            double d = distSq(i, px, pz);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    int nearestGlobal(double px, double pz) {
        return nearest(px, pz, 0, 0, n - 1);
    }

    /** Position s (en blocs) d'un point, projeté sur la tangente de l'échantillon i. */
    double position(int i, double px, double pz) {
        double f = (px - x[i]) * tx[i] + (pz - z[i]) * tz[i];
        f = Math.max(-step / 2, Math.min(step / 2, f));
        return mod(i * step + f);
    }

    /** Yaw Minecraft (0 = +z, -90 = +x) pour regarder dans la direction (dx, dz). */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ------------------------------------------------------------------ grille des distances

    /** Distance de chaque case (centre) à la ligne médiane, et position s du point le plus proche. */
    static final class Grid {
        final int minX, minZ, w, h;
        final float[] d, s;

        Grid(int minX, int minZ, int w, int h) {
            this.minX = minX;
            this.minZ = minZ;
            this.w = w;
            this.h = h;
            d = new float[w * h];
            s = new float[w * h];
            java.util.Arrays.fill(d, Float.MAX_VALUE);
        }

        boolean in(int bx, int bz) {
            return bx >= minX && bz >= minZ && bx < minX + w && bz < minZ + h;
        }

        float dist(int bx, int bz) {
            return in(bx, bz) ? d[(bx - minX) + (bz - minZ) * w] : Float.MAX_VALUE;
        }

        float pos(int bx, int bz) {
            return in(bx, bz) ? s[(bx - minX) + (bz - minZ) * w] : 0f;
        }
    }

    /** Calcule la grille sur le rectangle englobant le tracé + margin ; distances exactes jusqu'à reach. */
    Grid grid(int margin, double reach) {
        double mnx = Double.MAX_VALUE, mnz = Double.MAX_VALUE, mxx = -Double.MAX_VALUE, mxz = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            mnx = Math.min(mnx, x[i]);
            mnz = Math.min(mnz, z[i]);
            mxx = Math.max(mxx, x[i]);
            mxz = Math.max(mxz, z[i]);
        }
        int minX = (int) Math.floor(mnx) - margin, minZ = (int) Math.floor(mnz) - margin;
        int maxX = (int) Math.ceil(mxx) + margin, maxZ = (int) Math.ceil(mxz) + margin;
        Grid g = new Grid(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ax = x[i], az = z[i], dx = x[j] - ax, dz = z[j] - az, len2 = dx * dx + dz * dz;
            int x0 = (int) Math.floor(Math.min(ax, x[j]) - reach), x1 = (int) Math.ceil(Math.max(ax, x[j]) + reach);
            int z0 = (int) Math.floor(Math.min(az, z[j]) - reach), z1 = (int) Math.ceil(Math.max(az, z[j]) + reach);
            for (int bx = x0; bx <= x1; bx++)
                for (int bz = z0; bz <= z1; bz++) {
                    if (!g.in(bx, bz)) continue;
                    double cx = bx + 0.5, cz = bz + 0.5;
                    double t = len2 == 0 ? 0 : ((cx - ax) * dx + (cz - az) * dz) / len2;
                    t = Math.max(0, Math.min(1, t));
                    double px = ax + t * dx, pz = az + t * dz;
                    float dd = (float) Math.hypot(cx - px, cz - pz);
                    int k = (bx - g.minX) + (bz - g.minZ) * g.w;
                    if (dd < g.d[k]) {
                        g.d[k] = dd;
                        g.s[k] = (float) mod((i + t) * step);
                    }
                }
        }
        return g;
    }

    // ------------------------------------------------------------------ vérification hors serveur

    /**
     * Vérifie le tracé et dessine une vue du dessus.
     * Usage : java -cp target/classes fr.eemicraft.boatrace.Track [echelle] [largeur] [image.png]
     */
    public static void main(String[] args) throws Exception {
        double scale = args.length > 0 ? Double.parseDouble(args[0]) : 1.4;
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 7;
        String out = args.length > 2 ? args[2] : "trace.png";
        Track t = new Track(scale, 1.0);
        double half = width / 2.0, wall = half + 1.5;
        System.out.printf(Locale.ROOT, "Echelle %.2f, largeur %d : longueur %.1f blocs, %d echantillons (pas %.3f)%n",
                scale, width, t.length, t.n, t.step);
        System.out.printf(Locale.ROOT, "Ligne d'arrivee : (%.2f, %.2f), direction (%.2f, %.2f)%n", t.x[0], t.z[0], t.tx[0], t.tz[0]);

        // 1) écart minimal entre deux portions du tracé éloignées le long du parcours (> 40 blocs)
        double minGap = Double.MAX_VALUE;
        int gi = 0, gj = 0;
        for (int i = 0; i < t.n; i++)
            for (int j = i + 1; j < t.n; j++) {
                double arc = Math.min(j - i, t.n - (j - i)) * t.step;
                if (arc < 40) continue;
                double d = Math.sqrt(t.distSq(i, t.x[j], t.z[j]));
                if (d < minGap) { minGap = d; gi = i; gj = j; }
            }
        System.out.printf(Locale.ROOT, "Ecart minimal entre deux portions : %.1f blocs (s=%.0f et s=%.0f, en %.0f %.0f / %.0f %.0f) ; il faut > %.1f (piste + 2 murets)%n",
                minGap, gi * t.step, gj * t.step, t.x[gi], t.z[gi], t.x[gj], t.z[gj], 2 * wall);

        // 2) croisements de segments
        int crossings = 0;
        for (int i = 0; i < t.n; i++)
            for (int j = i + 2; j < t.n; j++) {
                if (i == 0 && j == t.n - 1) continue;
                if (intersects(t.x[i], t.z[i], t.x[(i + 1) % t.n], t.z[(i + 1) % t.n], t.x[j], t.z[j], t.x[(j + 1) % t.n], t.z[(j + 1) % t.n]))
                    crossings++;
            }
        System.out.println("Croisements du trace : " + crossings);

        // 3) virages : rayon de courbure sur une fenêtre de 4 blocs
        System.out.println("Virages (rayon < 45 blocs) :");
        int win = Math.max(2, (int) Math.round(2 / t.step));
        boolean inTurn = false;
        double turnAngle = 0, minR = Double.MAX_VALUE;
        int start = 0;
        // on commence au milieu de la ligne droite des stands
        for (int k = 0; k <= t.n; k++) {
            int i = k % t.n;
            double a1 = Math.atan2(t.tz[Math.floorMod(i - win, t.n)], t.tx[Math.floorMod(i - win, t.n)]);
            double a2 = Math.atan2(t.tz[(i + win) % t.n], t.tx[(i + win) % t.n]);
            double da = a2 - a1;
            while (da > Math.PI) da -= 2 * Math.PI;
            while (da < -Math.PI) da += 2 * Math.PI;
            double r = Math.abs(da) < 1e-6 ? Double.MAX_VALUE : (2 * win * t.step) / Math.abs(da);
            if (r < 45) {
                if (!inTurn) { inTurn = true; turnAngle = 0; minR = Double.MAX_VALUE; start = i; }
                minR = Math.min(minR, r);
                turnAngle += da / (2 * win);
            } else if (inTurn) {
                inTurn = false;
                System.out.printf(Locale.ROOT, "  s %4.0f -> %4.0f (%4.0f %4.0f) : %4.0f deg, rayon min %.1f%n",
                        start * t.step, i * t.step, t.x[start], t.z[start], Math.toDegrees(turnAngle), minR);
            }
        }

        // 4) image du dessus (3 px par bloc) + plan ASCII (1 caractère = 4 blocs)
        Grid g = t.grid(6, wall + 2);
        int px = 3;
        BufferedImage img = new BufferedImage(g.w * px, g.h * px, BufferedImage.TYPE_INT_RGB);
        StringBuilder ascii = new StringBuilder();
        for (int bz = g.minZ; bz < g.minZ + g.h; bz++) {
            for (int bx = g.minX; bx < g.minX + g.w; bx++) {
                float d = g.dist(bx, bz), s = g.pos(bx, bz);
                int rgb;
                boolean line = d <= wall && (s < 1 || s > t.length - 1);
                if (line) rgb = ((bx + bz) & 1) == 0 ? 0x000000 : 0xFFFFFF;
                else if (d <= half) rgb = 0x74A8FC;
                else if (d <= wall) rgb = ((int) (s / 2) & 1) == 0 ? 0xC03030 : 0xF0F0F0;
                else rgb = 0xE8EEF0;
                for (int a = 0; a < px; a++)
                    for (int b = 0; b < px; b++) img.setRGB((bx - g.minX) * px + a, (bz - g.minZ) * px + b, rgb);
            }
        }
        // points de contrôle de la course (16) en rouge
        for (int c = 0; c < 16; c++) {
            int i = (int) Math.round(c * t.n / 16.0) % t.n;
            int cx = (int) ((t.x[i] - g.minX) * px), cz = (int) ((t.z[i] - g.minZ) * px);
            for (int a = -3; a <= 3; a++)
                for (int b = -3; b <= 3; b++) {
                    int X = cx + a, Z = cz + b;
                    if (X >= 0 && Z >= 0 && X < img.getWidth() && Z < img.getHeight()) img.setRGB(X, Z, 0xFF0000);
                }
        }
        javax.imageio.ImageIO.write(img, "png", new File(out));
        for (int bz = g.minZ; bz < g.minZ + g.h; bz += 4) {
            for (int bx = g.minX; bx < g.minX + g.w; bx += 2) {
                float d = g.dist(bx, bz), s = g.pos(bx, bz);
                char ch = ' ';
                if (d <= wall && (s < 2 || s > t.length - 2)) ch = '|';
                else if (d <= half) ch = '#';
                else if (d <= wall) ch = '.';
                ascii.append(ch);
            }
            ascii.append('\n');
        }
        System.out.println("Vue du dessus (x vers la droite, z vers le bas ; # piste, . muret, | ligne) :");
        System.out.print(ascii);
        System.out.println("Image : " + new File(out).getAbsolutePath() + " (" + g.w + " x " + g.h + " blocs, de "
                + g.minX + " " + g.minZ + " a " + (g.minX + g.w - 1) + " " + (g.minZ + g.h - 1) + ")");
    }

    private static boolean intersects(double ax, double az, double bx, double bz, double cx, double cz, double dx, double dz) {
        double d1 = cross(cx, cz, dx, dz, ax, az), d2 = cross(cx, cz, dx, dz, bx, bz);
        double d3 = cross(ax, az, bx, bz, cx, cz), d4 = cross(ax, az, bx, bz, dx, dz);
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0));
    }

    private static double cross(double ax, double az, double bx, double bz, double px, double pz) {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }
}
