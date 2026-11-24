/*
 * Decompiled with CFR 0.152.
 */
package fr.eemicraft.boatrace;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

final class Track {
    static final double[][] CONTROL = new double[][]{{0.0, 0.0}, {40.0, 0.0}, {70.0, 0.0}, {92.0, 4.0}, {106.0, 18.0}, {110.0, 40.0}, {104.0, 62.0}, {88.0, 76.0}, {65.0, 80.0}, {50.0, 80.0}, {40.0, 80.0}, {32.0, 90.0}, {20.0, 90.0}, {12.0, 80.0}, {0.0, 80.0}, {-20.0, 80.0}, {-31.0, 76.0}, {-36.0, 67.0}, {-31.0, 58.0}, {-20.0, 55.0}, {5.0, 55.0}, {16.0, 55.0}, {27.0, 51.0}, {31.0, 41.5}, {27.0, 32.0}, {16.0, 28.0}, {0.0, 28.0}, {-40.0, 28.0}, {-69.0, 28.0}, {-75.0, 28.0}, {-75.0, 22.0}, {-75.0, 6.0}, {-75.0, 0.0}, {-69.0, 0.0}, {-40.0, 0.0}};
    final double step;
    final double length;
    final int n;
    final double[] x;
    final double[] z;
    final double[] tx;
    final double[] tz;
    final double[] y;
    final boolean[] solid;

    Track(double scale, double targetStep) {
        this(Track.scaled(scale), 3, 0.5, 0.5, targetStep);
    }

    void setFloor(double floor) {
        Arrays.fill(this.y, floor);
    }

    private static double[][] scaled(double scale) {
        double[][] pts = new double[CONTROL.length][2];
        for (int i = 0; i < pts.length; ++i) {
            pts[i][0] = CONTROL[i][0] * scale + 0.5;
            pts[i][1] = CONTROL[i][1] * scale + 0.5;
        }
        return pts;
    }

    Track(double[][] control, int smoothing, double fx, double fz, double targetStep) {
        int k;
        double[][] pts = Track.chaikin(control, smoothing);
        int m = pts.length;
        double[] cum = new double[m + 1];
        for (int i = 0; i < m; ++i) {
            double[] a = pts[i];
            double[] b = pts[(i + 1) % m];
            cum[i + 1] = cum[i] + Math.hypot(b[0] - a[0], b[1] - a[1]);
        }
        double total = cum[m];
        double s0 = 0.0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < m; ++i) {
            double pz;
            double[] b = pts[(i + 1) % m];
            double[] a = pts[i];
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double len2 = dx * dx + dz * dz;
            double t = len2 == 0.0 ? 0.0 : ((fx - a[0]) * dx + (fz - a[1]) * dz) / len2;
            double px = a[0] + (t = Math.max(0.0, Math.min(1.0, t))) * dx;
            double d = Math.hypot(px - fx, (pz = a[1] + t * dz) - fz);
            if (!(d < best)) continue;
            best = d;
            s0 = cum[i] + t * (cum[i + 1] - cum[i]);
        }
        this.n = Math.max(16, (int)Math.round(total / targetStep));
        this.step = total / (double)this.n;
        this.length = total;
        this.x = new double[this.n];
        this.z = new double[this.n];
        this.y = new double[this.n];
        this.solid = new boolean[this.n];
        int seg = 0;
        for (k = 0; k < this.n; ++k) {
            double s = (s0 + (double)k * this.step) % total;
            for (seg = 0; seg < m - 1 && cum[seg + 1] < s; ++seg) {
            }
            double[] a = pts[seg];
            double[] b = pts[(seg + 1) % m];
            double segLen = cum[seg + 1] - cum[seg];
            double t = segLen == 0.0 ? 0.0 : (s - cum[seg]) / segLen;
            this.x[k] = a[0] + t * (b[0] - a[0]);
            this.z[k] = a[1] + t * (b[1] - a[1]);
            if (a.length >= 4) {
                this.y[k] = a[2] + t * (b[2] - a[2]);
                this.solid[k] = a[3] > 0.99 && b[3] > 0.99;
                continue;
            }
            this.solid[k] = true;
        }
        this.tx = new double[this.n];
        this.tz = new double[this.n];
        for (k = 0; k < this.n; ++k) {
            double dx = this.x[(k + 1) % this.n] - this.x[(k - 1 + this.n) % this.n];
            double dz = this.z[(k + 1) % this.n] - this.z[(k - 1 + this.n) % this.n];
            double l = Math.hypot(dx, dz);
            this.tx[k] = l == 0.0 ? 1.0 : dx / l;
            this.tz[k] = l == 0.0 ? 0.0 : dz / l;
        }
    }

    static double[][] chaikin(double[][] pts, int iterations) {
        for (int it = 0; it < iterations; ++it) {
            int m = pts.length;
            int c = pts[0].length;
            double[][] out = new double[m * 2][c];
            for (int i = 0; i < m; ++i) {
                double[] a = pts[i];
                double[] b = pts[(i + 1) % m];
                for (int k = 0; k < c; ++k) {
                    out[2 * i][k] = 0.75 * a[k] + 0.25 * b[k];
                    out[2 * i + 1][k] = 0.25 * a[k] + 0.75 * b[k];
                }
            }
            pts = out;
        }
        return pts;
    }

    double mod(double s) {
        double r = s % this.length;
        return r < 0.0 ? r + this.length : r;
    }

    double wrap(double d) {
        double r = this.mod(d + this.length / 2.0) - this.length / 2.0;
        return r;
    }

    int index(double s) {
        return (int)Math.floor(this.mod(s) / this.step) % this.n;
    }

    double[] at(double s) {
        s = this.mod(s);
        int i = (int)Math.floor(s / this.step) % this.n;
        int j = (i + 1) % this.n;
        double t = s / this.step - Math.floor(s / this.step);
        double px = this.x[i] + t * (this.x[j] - this.x[i]);
        double pz = this.z[i] + t * (this.z[j] - this.z[i]);
        double dx = this.tx[i] + t * (this.tx[j] - this.tx[i]);
        double dz = this.tz[i] + t * (this.tz[j] - this.tz[i]);
        double l = Math.hypot(dx, dz);
        return new double[]{px, pz, dx / l, dz / l};
    }

    double yAt(double s) {
        s = this.mod(s);
        int i = (int)Math.floor(s / this.step) % this.n;
        int j = (i + 1) % this.n;
        double t = s / this.step - Math.floor(s / this.step);
        return this.y[i] + t * (this.y[j] - this.y[i]);
    }

    double minY(int i, int radius) {
        double m = Double.MAX_VALUE;
        for (int k = -radius; k <= radius; ++k) {
            m = Math.min(m, this.y[Math.floorMod(i + k, this.n)]);
        }
        return m;
    }

    double minY() {
        double m = Double.MAX_VALUE;
        for (double v : this.y) {
            m = Math.min(m, v);
        }
        return m;
    }

    double solidBack(double s) {
        for (int k = 0; k < this.n; ++k) {
            double t = s - (double)k * this.step;
            if (!this.solid[this.index(t)] || !this.solid[this.index(t + this.step)]) continue;
            return t;
        }
        return s;
    }

    double distSq(int i, double px, double pz) {
        double dx = px - this.x[i];
        double dz = pz - this.z[i];
        return dx * dx + dz * dz;
    }

    int nearest(double px, double pz, int from, int back, int ahead) {
        int best = from;
        double bd = Double.MAX_VALUE;
        for (int k = -back; k <= ahead; ++k) {
            int i = Math.floorMod(from + k, this.n);
            double d = this.distSq(i, px, pz);
            if (!(d < bd)) continue;
            bd = d;
            best = i;
        }
        return best;
    }

    int nearestGlobal(double px, double pz) {
        return this.nearest(px, pz, 0, 0, this.n - 1);
    }

    double position(int i, double px, double pz) {
        double f = (px - this.x[i]) * this.tx[i] + (pz - this.z[i]) * this.tz[i];
        f = Math.max(-this.step / 2.0, Math.min(this.step / 2.0, f));
        return this.mod((double)i * this.step + f);
    }

    static float yaw(double dx, double dz) {
        return (float)Math.toDegrees(Math.atan2(-dx, dz));
    }

    Grid grid(int margin, double reach) {
        double mnx = Double.MAX_VALUE;
        double mnz = Double.MAX_VALUE;
        double mxx = -1.7976931348623157E308;
        double mxz = -1.7976931348623157E308;
        for (int i = 0; i < this.n; ++i) {
            mnx = Math.min(mnx, this.x[i]);
            mnz = Math.min(mnz, this.z[i]);
            mxx = Math.max(mxx, this.x[i]);
            mxz = Math.max(mxz, this.z[i]);
        }
        int minX = (int)Math.floor(mnx) - margin;
        int minZ = (int)Math.floor(mnz) - margin;
        int maxX = (int)Math.ceil(mxx) + margin;
        int maxZ = (int)Math.ceil(mxz) + margin;
        Grid g = new Grid(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
        for (int i = 0; i < this.n; ++i) {
            int j = (i + 1) % this.n;
            double ax = this.x[i];
            double az = this.z[i];
            double dx = this.x[j] - ax;
            double dz = this.z[j] - az;
            double len2 = dx * dx + dz * dz;
            int x0 = (int)Math.floor(Math.min(ax, this.x[j]) - reach);
            int x1 = (int)Math.ceil(Math.max(ax, this.x[j]) + reach);
            int z0 = (int)Math.floor(Math.min(az, this.z[j]) - reach);
            int z1 = (int)Math.ceil(Math.max(az, this.z[j]) + reach);
            for (int bx = x0; bx <= x1; ++bx) {
                for (int bz = z0; bz <= z1; ++bz) {
                    int k;
                    double pz;
                    if (!g.in(bx, bz)) continue;
                    double cx = (double)bx + 0.5;
                    double cz = (double)bz + 0.5;
                    double t = len2 == 0.0 ? 0.0 : ((cx - ax) * dx + (cz - az) * dz) / len2;
                    double px = ax + (t = Math.max(0.0, Math.min(1.0, t))) * dx;
                    float dd = (float)Math.hypot(cx - px, cz - (pz = az + t * dz));
                    if (!(dd < g.d[k = bx - g.minX + (bz - g.minZ) * g.w])) continue;
                    g.d[k] = dd;
                    g.s[k] = (float)this.mod(((double)i + t) * this.step);
                }
            }
        }
        return g;
    }

    public static void main(String[] args) throws Exception {
        int bx;
        int bz;
        double scale = args.length > 0 ? Double.parseDouble(args[0]) : 1.4;
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 7;
        String out = args.length > 2 ? args[2] : "trace.png";
        Track t = new Track(scale, 1.0);
        double half = (double)width / 2.0;
        double wall = half + 1.5;
        System.out.printf(Locale.ROOT, "Echelle %.2f, largeur %d : longueur %.1f blocs, %d echantillons (pas %.3f)%n", scale, width, t.length, t.n, t.step);
        System.out.printf(Locale.ROOT, "Ligne d'arrivee : (%.2f, %.2f), direction (%.2f, %.2f)%n", t.x[0], t.z[0], t.tx[0], t.tz[0]);
        double minGap = Double.MAX_VALUE;
        int gi = 0;
        int gj = 0;
        for (int i = 0; i < t.n; ++i) {
            for (int j = i + 1; j < t.n; ++j) {
                double d;
                double arc = (double)Math.min(j - i, t.n - (j - i)) * t.step;
                if (arc < 40.0 || !((d = Math.sqrt(t.distSq(i, t.x[j], t.z[j]))) < minGap)) continue;
                minGap = d;
                gi = i;
                gj = j;
            }
        }
        System.out.printf(Locale.ROOT, "Ecart minimal entre deux portions : %.1f blocs (s=%.0f et s=%.0f, en %.0f %.0f / %.0f %.0f) ; il faut > %.1f (piste + 2 murets)%n", minGap, (double)gi * t.step, (double)gj * t.step, t.x[gi], t.z[gi], t.x[gj], t.z[gj], 2.0 * wall);
        int crossings = 0;
        for (int i = 0; i < t.n; ++i) {
            for (int j = i + 2; j < t.n; ++j) {
                if (i == 0 && j == t.n - 1 || !Track.intersects(t.x[i], t.z[i], t.x[(i + 1) % t.n], t.z[(i + 1) % t.n], t.x[j], t.z[j], t.x[(j + 1) % t.n], t.z[(j + 1) % t.n])) continue;
                ++crossings;
            }
        }
        System.out.println("Croisements du trace : " + crossings);
        System.out.println("Virages (rayon < 45 blocs) :");
        int win = Math.max(2, (int)Math.round(2.0 / t.step));
        boolean inTurn = false;
        double turnAngle = 0.0;
        double minR = Double.MAX_VALUE;
        int start = 0;
        for (int k = 0; k <= t.n; ++k) {
            double r;
            double da;
            int i = k % t.n;
            double a1 = Math.atan2(t.tz[Math.floorMod(i - win, t.n)], t.tx[Math.floorMod(i - win, t.n)]);
            double a2 = Math.atan2(t.tz[(i + win) % t.n], t.tx[(i + win) % t.n]);
            for (da = a2 - a1; da > Math.PI; da -= Math.PI * 2) {
            }
            while (da < -Math.PI) {
                da += Math.PI * 2;
            }
            double d = r = Math.abs(da) < 1.0E-6 ? Double.MAX_VALUE : (double)(2 * win) * t.step / Math.abs(da);
            if (r < 45.0) {
                if (!inTurn) {
                    inTurn = true;
                    turnAngle = 0.0;
                    minR = Double.MAX_VALUE;
                    start = i;
                }
                minR = Math.min(minR, r);
                turnAngle += da / (double)(2 * win);
                continue;
            }
            if (!inTurn) continue;
            inTurn = false;
            System.out.printf(Locale.ROOT, "  s %4.0f -> %4.0f (%4.0f %4.0f) : %4.0f deg, rayon min %.1f%n", (double)start * t.step, (double)i * t.step, t.x[start], t.z[start], Math.toDegrees(turnAngle), minR);
        }
        Grid g = t.grid(6, wall + 2.0);
        int px = 3;
        BufferedImage img = new BufferedImage(g.w * px, g.h * px, 1);
        StringBuilder ascii = new StringBuilder();
        for (bz = g.minZ; bz < g.minZ + g.h; ++bz) {
            for (bx = g.minX; bx < g.minX + g.w; ++bx) {
                boolean line;
                float d = g.dist(bx, bz);
                float s = g.pos(bx, bz);
                boolean bl = line = (double)d <= wall && (s < 1.0f || (double)s > t.length - 1.0);
                int rgb = line ? ((bx + bz & 1) == 0 ? 0 : 0xFFFFFF) : ((double)d <= half ? 7645436 : ((double)d <= wall ? (((int)(s / 2.0f) & 1) == 0 ? 0xC03030 : 0xF0F0F0) : 15265520));
                for (int a = 0; a < px; ++a) {
                    for (int b = 0; b < px; ++b) {
                        img.setRGB((bx - g.minX) * px + a, (bz - g.minZ) * px + b, rgb);
                    }
                }
            }
        }
        for (int c = 0; c < 16; ++c) {
            int i = (int)Math.round((double)(c * t.n) / 16.0) % t.n;
            int cx = (int)((t.x[i] - (double)g.minX) * (double)px);
            int cz = (int)((t.z[i] - (double)g.minZ) * (double)px);
            for (int a = -3; a <= 3; ++a) {
                for (int b = -3; b <= 3; ++b) {
                    int X = cx + a;
                    int Z = cz + b;
                    if (X < 0 || Z < 0 || X >= img.getWidth() || Z >= img.getHeight()) continue;
                    img.setRGB(X, Z, 0xFF0000);
                }
            }
        }
        ImageIO.write((RenderedImage)img, "png", new File(out));
        for (bz = g.minZ; bz < g.minZ + g.h; bz += 4) {
            for (bx = g.minX; bx < g.minX + g.w; bx += 2) {
                float d = g.dist(bx, bz);
                float s = g.pos(bx, bz);
                int ch = 32;
                if ((double)d <= wall && (s < 2.0f || (double)s > t.length - 2.0)) {
                    ch = 124;
                } else if ((double)d <= half) {
                    ch = 35;
                } else if ((double)d <= wall) {
                    ch = 46;
                }
                ascii.append((char)ch);
            }
            ascii.append('\n');
        }
        System.out.println("Vue du dessus (x vers la droite, z vers le bas ; # piste, . muret, | ligne) :");
        System.out.print(ascii);
        System.out.println("Image : " + new File(out).getAbsolutePath() + " (" + g.w + " x " + g.h + " blocs, de " + g.minX + " " + g.minZ + " a " + (g.minX + g.w - 1) + " " + (g.minZ + g.h - 1) + ")");
    }

    private static boolean intersects(double ax, double az, double bx, double bz, double cx, double cz, double dx, double dz) {
        double d1 = Track.cross(cx, cz, dx, dz, ax, az);
        double d2 = Track.cross(cx, cz, dx, dz, bx, bz);
        double d3 = Track.cross(ax, az, bx, bz, cx, cz);
        double d4 = Track.cross(ax, az, bx, bz, dx, dz);
        return d1 > 0.0 != d2 > 0.0 && d3 > 0.0 != d4 > 0.0;
    }

    private static double cross(double ax, double az, double bx, double bz, double px, double pz) {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }

    static final class Grid {
        final int minX;
        final int minZ;
        final int w;
        final int h;
        final float[] d;
        final float[] s;

        Grid(int minX, int minZ, int w, int h) {
            this.minX = minX;
            this.minZ = minZ;
            this.w = w;
            this.h = h;
            this.d = new float[w * h];
            this.s = new float[w * h];
            Arrays.fill(this.d, Float.MAX_VALUE);
        }

        boolean in(int bx, int bz) {
            return bx >= this.minX && bz >= this.minZ && bx < this.minX + this.w && bz < this.minZ + this.h;
        }

        float dist(int bx, int bz) {
            return this.in(bx, bz) ? this.d[bx - this.minX + (bz - this.minZ) * this.w] : Float.MAX_VALUE;
        }

        float pos(int bx, int bz) {
            return this.in(bx, bz) ? this.s[bx - this.minX + (bz - this.minZ) * this.w] : 0.0f;
        }
    }
}

