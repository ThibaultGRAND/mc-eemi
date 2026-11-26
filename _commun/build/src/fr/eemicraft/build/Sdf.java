package fr.eemicraft.build;

/**
 * Forme par champ de distance signée (négatif = dedans). Se combine (union, différence, intersection,
 * coque, déformation par bruit) puis se rasterise dans un Sink : rochers (ellipsoïde déformé), îles
 * (cône inversé + stalactites), coupoles, statues (union d'ovoïdes)…
 *
 * <pre>
 * Sdf ile = Sdf.cone(0, 0, 100, -14, 3, 16).union(Sdf.cylinder(0, 0, 98, 100, 16))
 *              .deform(new Noise(7), 2.5, 6);
 * ile.fill(sink, terre);
 * </pre>
 */
public final class Sdf {
    @FunctionalInterface
    public interface F { double d(double x, double y, double z); }

    private final F f;
    /** Boîte englobante {x1,y1,z1,x2,y2,z2} (inclusive) ou null si inconnue. */
    private final int[] b;

    public Sdf(F f, int[] bounds) { this.f = f; this.b = bounds; }

    public double d(double x, double y, double z) { return f.d(x, y, z); }
    public int[] bounds() { return b == null ? null : b.clone(); }

    private static int[] bb(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new int[]{(int) Math.floor(x1) - 1, (int) Math.floor(y1) - 1, (int) Math.floor(z1) - 1,
                (int) Math.ceil(x2) + 1, (int) Math.ceil(y2) + 1, (int) Math.ceil(z2) + 1};
    }

    private static int[] merge(int[] a, int[] c) {
        if (a == null || c == null) return null;
        return new int[]{Math.min(a[0], c[0]), Math.min(a[1], c[1]), Math.min(a[2], c[2]),
                Math.max(a[3], c[3]), Math.max(a[4], c[4]), Math.max(a[5], c[5])};
    }

    // ---------- primitives ----------

    public static Sdf sphere(double cx, double cy, double cz, double r) {
        return new Sdf((x, y, z) -> Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz)) - r,
                bb(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r));
    }

    /** Ellipsoïde (distance approchée, suffisante pour rasteriser). */
    public static Sdf ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz) {
        double m = Math.min(rx, Math.min(ry, rz));
        return new Sdf((x, y, z) -> {
            double a = (x - cx) / rx, c = (y - cy) / ry, e = (z - cz) / rz;
            return (Math.sqrt(a * a + c * c + e * e) - 1) * m;
        }, bb(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz));
    }

    /** Boîte par ses coins (inclusifs). */
    public static Sdf box(double x1, double y1, double z1, double x2, double y2, double z2) {
        double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2, cz = (z1 + z2) / 2;
        double hx = Math.abs(x2 - x1) / 2 + 0.5, hy = Math.abs(y2 - y1) / 2 + 0.5, hz = Math.abs(z2 - z1) / 2 + 0.5;
        return new Sdf((x, y, z) -> {
            double qx = Math.abs(x - cx) - hx, qy = Math.abs(y - cy) - hy, qz = Math.abs(z - cz) - hz;
            double ox = Math.max(qx, 0), oy = Math.max(qy, 0), oz = Math.max(qz, 0);
            return Math.sqrt(ox * ox + oy * oy + oz * oz) + Math.min(Math.max(qx, Math.max(qy, qz)), 0);
        }, bb(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
    }

    /** Cylindre vertical fermé, de y1 à y2. */
    public static Sdf cylinder(double cx, double cz, double y1, double y2, double r) {
        double lo = Math.min(y1, y2) - 0.5, hi = Math.max(y1, y2) + 0.5;
        return new Sdf((x, y, z) -> {
            double dr = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - r;
            double dy = Math.max(lo - y, y - hi);
            double ox = Math.max(dr, 0), oy = Math.max(dy, 0);
            return Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(dr, dy), 0);
        }, bb(cx - r, lo, cz - r, cx + r, hi, cz + r));
    }

    /**
     * Cône vertical : rayon r0 en y0, rayon r1 en y0+height (height négatif = vers le bas).
     * cone(cx, cz, 100, -15, 14, 1) = île en cône inversé dont le dessus est en y 100.
     */
    public static Sdf cone(double cx, double cz, double y0, double height, double r0, double r1) {
        double ya = Math.min(y0, y0 + height), yb = Math.max(y0, y0 + height);
        double rm = Math.max(r0, r1);
        double slope = Math.abs(r1 - r0) / Math.max(1e-6, Math.abs(height));
        double k = 1 / Math.sqrt(1 + slope * slope);
        return new Sdf((x, y, z) -> {
            double t = (y - y0) / height;
            double r = r0 + (r1 - r0) * Math.max(0, Math.min(1, t));
            double dr = (Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - r) * k;
            double dy = Math.max(ya - 0.5 - y, y - yb - 0.5);
            return Math.max(dr, dy);
        }, bb(cx - rm, ya, cz - rm, cx + rm, yb, cz + rm));
    }

    // ---------- combinaisons ----------

    public Sdf union(Sdf o) { return new Sdf((x, y, z) -> Math.min(d(x, y, z), o.d(x, y, z)), merge(b, o.b)); }

    /** Union douce (raccord arrondi de rayon k). */
    public Sdf smoothUnion(Sdf o, double k) {
        return new Sdf((x, y, z) -> {
            double a = d(x, y, z), c = o.d(x, y, z);
            double h = Math.max(0, Math.min(1, 0.5 + 0.5 * (c - a) / k));
            return c * (1 - h) + a * h - k * h * (1 - h);
        }, merge(b, o.b));
    }

    public Sdf minus(Sdf o) { return new Sdf((x, y, z) -> Math.max(d(x, y, z), -o.d(x, y, z)), b); }

    public Sdf intersect(Sdf o) {
        int[] nb = (b == null) ? o.b : (o.b == null ? b : new int[]{Math.max(b[0], o.b[0]), Math.max(b[1], o.b[1]),
                Math.max(b[2], o.b[2]), Math.min(b[3], o.b[3]), Math.min(b[4], o.b[4]), Math.min(b[5], o.b[5])});
        return new Sdf((x, y, z) -> Math.max(d(x, y, z), o.d(x, y, z)), nb);
    }

    /** Coque d'épaisseur t (en blocs) autour de la surface. */
    public Sdf shell(double t) {
        return new Sdf((x, y, z) -> Math.abs(d(x, y, z)) - t / 2, grow(b, (int) Math.ceil(t)));
    }

    /** Déformation par bruit 3D : sdf + amp·bruit(période). Rochers, îles, falaises organiques. */
    public Sdf deform(Noise n, double amp, double period) {
        return new Sdf((x, y, z) -> d(x, y, z) + amp * n.fbm3(x / period, y / period, z / period, 2, 0.5, 2),
                grow(b, (int) Math.ceil(amp) + 1));
    }

    public Sdf translate(double dx, double dy, double dz) {
        int[] nb = b == null ? null : new int[]{b[0] + (int) Math.floor(dx), b[1] + (int) Math.floor(dy), b[2] + (int) Math.floor(dz),
                b[3] + (int) Math.ceil(dx), b[4] + (int) Math.ceil(dy), b[5] + (int) Math.ceil(dz)};
        return new Sdf((x, y, z) -> d(x - dx, y - dy, z - dz), nb);
    }

    private static int[] grow(int[] b, int g) {
        if (b == null) return null;
        return new int[]{b[0] - g, b[1] - g, b[2] - g, b[3] + g, b[4] + g, b[5] + g};
    }

    // ---------- rasterisation ----------

    /** Pose m dans toutes les cases où d ≤ 0 (bornes de la forme). */
    public int fill(Sink s, Mat m) {
        if (b == null) throw new IllegalStateException("Sdf sans bornes : utiliser fill(s, m, x1..z2)");
        return fill(s, m, b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public int fill(Sink s, Mat m, int x1, int y1, int z1, int x2, int y2, int z2) {
        int n = 0;
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                    if (d(x, y, z) <= 0) { s.set(x, y, z, m); n++; }
        return n;
    }

    /**
     * Comme fill, mais le Mat reçoit la profondeur sous la surface (0 = bord) : herbe dessus, terre, pierre…
     * {@code layers.at(depth)} doit rendre un Mat.
     */
    public int fillLayered(Sink s, Layers layers) {
        if (b == null) throw new IllegalStateException("Sdf sans bornes");
        int n = 0;
        for (int y = b[1]; y <= b[4]; y++)
            for (int z = b[2]; z <= b[5]; z++)
                for (int x = b[0]; x <= b[3]; x++) {
                    if (d(x, y, z) > 0) continue;
                    int depth = 0;
                    while (depth < 6 && d(x, y + depth + 1, z) <= 0) depth++;
                    s.set(x, y, z, layers.at(depth));
                    n++;
                }
        return n;
    }

    @FunctionalInterface
    public interface Layers { Mat at(int depthFromTop); }
}
