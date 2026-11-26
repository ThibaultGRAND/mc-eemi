package fr.eemicraft.fete.build;

/**
 * Formes simples posées dans un Sink avec un Mat (bloc fixe, palette, dégradé ou Mat.AIR pour creuser).
 * Les bornes peuvent être données dans n'importe quel ordre. Le centre d'un bloc = ses coordonnées entières.
 */
public final class Shapes {
    private Shapes() {}

    /** Parallélépipède plein. */
    public static void fill(Sink s, int x1, int y1, int z1, int x2, int y2, int z2, Mat m) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2);
        int ay = Math.min(y1, y2), by = Math.max(y1, y2);
        int az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int y = ay; y <= by; y++)
            for (int z = az; z <= bz; z++)
                for (int x = ax; x <= bx; x++) s.set(x, y, z, m);
    }

    /** Coque : les 6 faces de la boîte, intérieur non touché. */
    public static void box(Sink s, int x1, int y1, int z1, int x2, int y2, int z2, Mat m) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2);
        int ay = Math.min(y1, y2), by = Math.max(y1, y2);
        int az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int y = ay; y <= by; y++)
            for (int z = az; z <= bz; z++)
                for (int x = ax; x <= bx; x++)
                    if (x == ax || x == bx || y == ay || y == by || z == az || z == bz) s.set(x, y, z, m);
    }

    /** Coque remplie d'air à l'intérieur. */
    public static void hollow(Sink s, int x1, int y1, int z1, int x2, int y2, int z2, Mat m) {
        fill(s, x1, y1, z1, x2, y2, z2, Mat.AIR);
        box(s, x1, y1, z1, x2, y2, z2, m);
    }

    /**
     * Les 4 murs seulement (pas de sol ni de plafond). ATTENTION : un mur d'un seul plan est interdit
     * sur un bâtiment visible (skill §3) — utiliser {@link Facade} pour les façades.
     */
    public static void walls(Sink s, int x1, int y1, int z1, int x2, int y2, int z2, Mat m) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2);
        int az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = az; z <= bz; z++)
                for (int x = ax; x <= bx; x++)
                    if (x == ax || x == bx || z == az || z == bz) s.set(x, y, z, m);
    }

    /** Cylindre vertical de rayon r (bords lissés à +0,35), plein ou creux (paroi d'épaisseur 1). */
    public static void cylinder(Sink s, int cx, int cz, int y1, int y2, double r, Mat m, boolean hollow) {
        int R = (int) Math.ceil(r);
        double rr = (r + 0.35) * (r + 0.35), in = (r - 0.65) * (r - 0.65);
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int dz = -R; dz <= R; dz++)
                for (int dx = -R; dx <= R; dx++) {
                    double d = dx * dx + dz * dz;
                    if (d > rr) continue;
                    if (hollow && d < in) continue;
                    s.set(cx + dx, y, cz + dz, m);
                }
    }

    public static void sphere(Sink s, int cx, int cy, int cz, double r, Mat m) {
        ellipsoid(s, cx, cy, cz, r, r, r, m);
    }

    public static void ellipsoid(Sink s, int cx, int cy, int cz, double rx, double ry, double rz, Mat m) {
        int X = (int) Math.ceil(rx), Y = (int) Math.ceil(ry), Z = (int) Math.ceil(rz);
        for (int dy = -Y; dy <= Y; dy++)
            for (int dz = -Z; dz <= Z; dz++)
                for (int dx = -X; dx <= X; dx++) {
                    double a = dx / (rx + 0.35), b = dy / (ry + 0.35), c = dz / (rz + 0.35);
                    if (a * a + b * b + c * c <= 1) s.set(cx + dx, cy + dy, cz + dz, m);
                }
    }

    /** Cône (ou tronc de cône) vertical : rayon r0 en y0, r1 en y0+height-1. r1 &gt; r0 = cône inversé (île). */
    public static void cone(Sink s, int cx, int cz, int y0, int height, double r0, double r1, Mat m) {
        int sgn = height >= 0 ? 1 : -1;
        int n = Math.abs(height);
        for (int i = 0; i < n; i++) {
            double r = n == 1 ? r0 : r0 + (r1 - r0) * i / (double) (n - 1);
            if (r < 0.2) { s.set(cx, y0 + sgn * i, cz, m); continue; }
            cylinder(s, cx, cz, y0 + sgn * i, y0 + sgn * i, r, m, false);
        }
    }

    /** Ligne 3D (pas de 0,25 bloc). */
    public static void line(Sink s, int x1, int y1, int z1, int x2, int y2, int z2, Mat m) {
        int n = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 0; i <= n * 4; i++) {
            double t = n == 0 ? 0 : i / (n * 4.0);
            int x = (int) Math.round(x1 + (x2 - x1) * t), y = (int) Math.round(y1 + (y2 - y1) * t), z = (int) Math.round(z1 + (z2 - z1) * t);
            if (x == lx && y == ly && z == lz) continue;
            s.set(x, y, z, m);
            lx = x; ly = y; lz = z;
        }
    }

    /**
     * Arche (ouverture ou plein) dans un mur. (x, y, z) = pied gauche de l'arche ; elle s'étend sur
     * {@code width} blocs vers {@code across}, sur {@code thickness} blocs vers {@code depth}.
     * Partie droite jusqu'à {@code spring} (hauteur des naissances), puis arc plein cintre
     * (rayon width/2) ou brisé/gothique (deux arcs de rayon = width centrés aux pieds opposés).
     * Mat.AIR = percer ; un matériau = remplir la forme.
     */
    public static void arch(Sink s, int x, int y, int z, Dir across, Dir depth, int width, int spring,
                            int thickness, Mat m, boolean pointed) {
        double c = (width - 1) / 2.0;
        int maxH = spring + (pointed ? (int) Math.ceil(width * 0.87) : (int) Math.ceil(width / 2.0)) + 1;
        for (int u = 0; u < width; u++)
            for (int v = 0; v < maxH; v++) {
                boolean in;
                if (v < spring) in = true;
                else {
                    double dv = v - spring + 0.5;
                    if (pointed) {
                        double r = width;
                        double dl = (u + 0.5) * (u + 0.5) + dv * dv;              // pied gauche en u = -0.5
                        double dr = (width - u - 0.5) * (width - u - 0.5) + dv * dv; // pied droit en u = width-0.5
                        in = dl <= r * r && dr <= r * r;
                    } else {
                        double r = width / 2.0;
                        in = (u - c) * (u - c) + dv * dv <= r * r;
                    }
                }
                if (!in) continue;
                for (int d = 0; d < thickness; d++)
                    s.set(x + across.dx * u + depth.dx * d, y + v, z + across.dz * u + depth.dz * d, m);
            }
    }
}
