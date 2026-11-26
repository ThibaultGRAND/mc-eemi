package fr.eemicraft.fete;

import fr.eemicraft.fete.build.Sink;

import java.util.ArrayList;
import java.util.List;

/**
 * Manège animé (sans Bukkit) : des PIÈCES mobiles, chacune faite de cubes, qui tournent ou bougent autour d'un pivot.
 * Le même modèle sert (1) au rendu statique hors serveur (cubes posés en vrais blocs à un instant t) et (2) en jeu,
 * où chaque cube devient un BlockDisplay posé au pivot, dont la transformation (rotation + translation) est mise à
 * jour par paliers et interpolée par le client : le manège tourne de façon fluide sans téléporter d'entité.
 *
 * <p>Repère : LOCAL du parc (u, y, v), y absolu. Les angles sont en degrés.
 */
public final class Ride {

    /** Axe de rotation d'une pièce. */
    public enum Axis { Y, U, V }

    /**
     * Cube : centre (relatif au pivot, dans le repère de la pièce), taille, angle propre autour de l'axe de la pièce,
     * état de bloc, lumineux (luminosité forcée au maximum en jeu).
     */
    public record Cube(double x, double y, double z, double sx, double sy, double sz, double angle, String state,
                       boolean glow, String model, int tint, int variant) {
        Cube(double x, double y, double z, double sx, double sy, double sz, double angle, String state, boolean glow) {
            this(x, y, z, sx, sy, sz, angle, state, glow, null, -1, 0);
        }

        /** Affiché selon le pack : variant 0 = toujours, 1 = sans le pack (blocs), 2 = avec le pack (modèle). */
        public boolean shown(boolean pack) {
            return variant == 0 || (variant == 1) != pack;
        }
    }

    /** Position d'une pièce à l'instant t : angle autour de l'axe et décalage (repère du parc). */
    public record Pose(double angle, double dx, double dy, double dz, double yaw) {
        public static final Pose ZERO = new Pose(0, 0, 0, 0, 0);
    }

    /** Mouvement d'une pièce en fonction du temps (secondes). */
    public interface Motion {
        Pose at(double t);
    }

    public static final class Part {
        public final String name;
        public final double pu, py, pv;
        public final Axis axis;
        public final Motion motion;
        public final List<Cube> cubes = new ArrayList<>();
        /** Places assises (relatives au pivot, repère de la pièce) : on s'y assoit au-dessus de ce point. */
        public final List<double[]> seats = new ArrayList<>();

        public Part seat(double x, double y, double z) {
            seats.add(new double[]{x, y, z});
            return this;
        }
        /** Variante des cubes ajoutés ensuite (voir {@link Cube#shown}). */
        private int variant;

        /** Les cubes suivants ne s'affichent que sans le pack (repli en blocs d'un modèle). */
        public Part blocksOnly() {
            variant = 1;
            return this;
        }

        public Part always() {
            variant = 0;
            return this;
        }

        /** Modèle du pack (fete:&lt;id&gt;) centré en (x, y, z), échelle, cap, couleur teinte (-1 = aucune). */
        public Part model(double x, double y, double z, double scale, double angle, String id, int tint) {
            cubes.add(new Cube(x, y, z, scale, scale, scale, angle, null, false, id, tint, 2));
            return this;
        }

        public Part(String name, double pu, double py, double pv, Axis axis, Motion motion) {
            this.name = name;
            this.pu = pu;
            this.py = py;
            this.pv = pv;
            this.axis = axis;
            this.motion = motion;
        }

        public Part cube(double x, double y, double z, double sx, double sy, double sz, String state) {
            cubes.add(new Cube(x, y, z, sx, sy, sz, 0, state, false, null, -1, variant));
            return this;
        }

        public Part cube(double x, double y, double z, double sx, double sy, double sz, double angle, String state) {
            cubes.add(new Cube(x, y, z, sx, sy, sz, angle, state, false, null, -1, variant));
            return this;
        }

        /**
         * Poutre fine d'un point à un autre, dans le plan perpendiculaire à l'axe de la pièce (axe V : plan u-y,
         * axe U : plan v-y, axe Y : plan horizontal). Coordonnées relatives au pivot.
         */
        public Part strut(double x1, double y1, double z1, double x2, double y2, double z2, double w, String state) {
            double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2, cz = (z1 + z2) / 2;
            double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
            if (axis == Axis.V) {
                double len = Math.hypot(dx, dy);
                // cube vertical tourné de a autour de V : sa direction devient (-sin a, cos a)
                double ang = Math.toDegrees(Math.atan2(-dx, dy));
                cubes.add(new Cube(cx, cy, cz, w, len, w, ang, state, false, null, -1, variant));
            } else if (axis == Axis.U) {
                double len = Math.hypot(dz, dy);
                double ang = Math.toDegrees(Math.atan2(dz, dy));
                cubes.add(new Cube(cx, cy, cz, w, len, w, ang, state, false, null, -1, variant));
            } else {
                double len = Math.hypot(dx, dz);
                double ang = Math.toDegrees(Math.atan2(dz, dx));
                cubes.add(new Cube(cx, cy, cz, len, w, w, ang, state, false, null, -1, variant));
            }
            return this;
        }

        public Part light(double x, double y, double z, double size, String state) {
            cubes.add(new Cube(x, y, z, size, size, size, 0, state, true, null, -1, variant));
            return this;
        }
    }

    public final String id;
    public final List<Part> parts = new ArrayList<>();
    /** Point d'embarquement (u, v) au sol, au bord du manège ; on y redescend. null = pas montable. */
    public double[] board;

    public Ride board(double u, double v) {
        board = new double[]{u, v};
        return this;
    }

    /** Position (u, y, v) d'une place à l'instant t. */
    public static double[] seatAt(Part p, double[] s, Pose pose) {
        double[] r = rot(p.axis, pose.angle(), s[0], s[1], s[2]);
        return new double[]{p.pu + pose.dx() + r[0], p.py + pose.dy() + r[1], p.pv + pose.dz() + r[2]};
    }

    public Ride(String id) {
        this.id = id;
    }

    public Part part(String name, double pu, double py, double pv, Axis axis, Motion motion) {
        Part p = new Part(name, pu, py, pv, axis, motion);
        parts.add(p);
        return p;
    }

    // ------------------------------------------------------------------ mouvements types

    /** Pièce fixe (structure fine en BlockDisplay : pieds, haubans). */
    public static Motion fixed() {
        return t -> Pose.ZERO;
    }

    /** Rotation continue : un tour en {@code period} secondes (négatif = sens inverse). */
    public static Motion spin(double period) {
        return t -> new Pose(360.0 * t / period, 0, 0, 0, 0);
    }

    /** Rotation continue + montée/descente sinusoïdale (chevaux du carrousel). */
    public static Motion spinBob(double period, double amp, double bobPeriod, double phase) {
        return t -> new Pose(360.0 * t / period, 0, amp * Math.sin(2 * Math.PI * (t / bobPeriod + phase)), 0, 0);
    }

    /** Balancement (bateau pirate) : amplitude en degrés. */
    public static Motion swing(double amp, double period) {
        return t -> new Pose(amp * Math.sin(2 * Math.PI * t / period), 0, 0, 0, 0);
    }

    /**
     * Nacelle qui reste droite sur une roue : translation seule sur un cercle de rayon r autour de l'axe U ou V,
     * départ à l'angle a0.
     */
    public static Motion orbit(Axis axis, double r, double period, double a0) {
        return t -> {
            double a = Math.toRadians(a0 + 360.0 * t / period);
            double c = Math.cos(a), s = Math.sin(a);
            return axis == Axis.V ? new Pose(0, r * s, -r * c, 0, 0) : new Pose(0, 0, -r * c, r * s, 0);
        };
    }

    /** Tour de chute : montée lente, attente, chute rapide, attente. */
    public static Motion drop(double height, double up, double waitTop, double fall, double waitBottom) {
        double cycle = up + waitTop + fall + waitBottom;
        return t -> {
            double k = ((t % cycle) + cycle) % cycle;
            double h;
            if (k < up) h = height * smooth(k / up);
            else if (k < up + waitTop) h = height;
            else if (k < up + waitTop + fall) {
                double f = (k - up - waitTop) / fall;
                h = height * (1 - f * f);
            } else h = 0;
            return new Pose(0, 0, h, 0, 0);
        };
    }

    /** Auto-tamponneuse : trajet de Lissajous dans un rectangle, cap dans le sens de la marche. */
    public static Motion bumper(double ax, double az, double w1, double w2, double ph) {
        return t -> {
            double x = ax * Math.sin(w1 * t + ph), z = az * Math.sin(w2 * t + ph * 1.7);
            double vx = ax * w1 * Math.cos(w1 * t + ph), vz = az * w2 * Math.cos(w2 * t + ph * 1.7);
            double yaw = Math.toDegrees(Math.atan2(-vx, vz));
            return new Pose(yaw, x, 0, z, 0);
        };
    }

    /** Trajet le long d'une polyligne fermée, relatif à un pivot (ou, oy, ov) : le pivot reste près du trajet. */
    public static Motion path(double[][] pts, double period, double ou, double oy, double ov) {
        Motion m = path(pts, period);
        return t -> {
            Pose p = m.at(t);
            return new Pose(p.angle(), p.dx() - ou, p.dy() - oy, p.dz() - ov, 0);
        };
    }

    /** Trajet le long d'une polyligne fermée (wagon des montagnes russes), vitesse variable selon la pente. */
    public static Motion path(double[][] pts, double period) {
        int n = pts.length;
        double[] acc = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double[] a = pts[i], b = pts[(i + 1) % n];
            acc[i + 1] = acc[i] + Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
        }
        double total = acc[n];
        return t -> {
            double d = ((t / period) % 1.0 + 1.0) % 1.0 * total;
            int i = 0;
            while (i < n - 1 && acc[i + 1] < d) i++;
            double[] a = pts[i], b = pts[(i + 1) % n];
            double f = (d - acc[i]) / Math.max(1e-6, acc[i + 1] - acc[i]);
            double yaw = Math.toDegrees(Math.atan2(-(b[0] - a[0]), b[2] - a[2]));
            return new Pose(yaw, a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, 0);
        };
    }

    static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }

    static double sq(double x) {
        return x * x;
    }

    // ------------------------------------------------------------------ géométrie

    /** Rotation d'un vecteur (x, y, z) d'un angle (degrés) autour d'un axe. */
    public static double[] rot(Axis axis, double deg, double x, double y, double z) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return switch (axis) {
            // autour de Y, sens horaire vu du dessus (comme le yaw de Minecraft)
            case Y -> new double[]{x * c - z * s, y, x * s + z * c};
            // autour de U (= x) : roue dans le plan v-y
            case U -> new double[]{x, y * c - z * s, y * s + z * c};
            // autour de V (= z) : roue dans le plan u-y
            case V -> new double[]{x * c - y * s, x * s + y * c, z};
        };
    }

    /** Centre monde-local (u, y, v) d'un cube d'une pièce à l'instant t. */
    public static double[] center(Part p, Cube c, Pose pose) {
        double[] r = rot(p.axis, pose.angle(), c.x(), c.y(), c.z());
        return new double[]{p.pu + pose.dx() + r[0], p.py + pose.dy() + r[1], p.pv + pose.dz() + r[2]};
    }

    /**
     * Rendu statique : pose chaque cube en vrais blocs à l'instant t (échantillonnage de la boîte tournée, pas 0,34).
     * Sert aux aperçus hors serveur ; en jeu les pièces sont des BlockDisplay.
     */
    public void draw(Sink s, double t) {
        for (Part p : parts) {
            Pose pose = p.motion.at(t);
            for (Cube c : p.cubes) {
                if (c.model() != null || !c.shown(false)) continue;
                int nx = Math.max(1, (int) Math.ceil(c.sx() / 0.34)), ny = Math.max(1, (int) Math.ceil(c.sy() / 0.34)),
                        nz = Math.max(1, (int) Math.ceil(c.sz() / 0.34));
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < ny; j++) {
                        for (int k = 0; k < nz; k++) {
                            double lx = (i + 0.5) / nx * c.sx() - c.sx() / 2, ly = (j + 0.5) / ny * c.sy() - c.sy() / 2,
                                    lz = (k + 0.5) / nz * c.sz() - c.sz() / 2;
                            double[] own = rot(p.axis, c.angle(), lx, ly, lz);
                            double[] r = rot(p.axis, pose.angle(), c.x() + own[0], c.y() + own[1], c.z() + own[2]);
                            int bu = (int) Math.floor(p.pu + pose.dx() + r[0]);
                            int by = (int) Math.floor(p.py + pose.dy() + r[1]);
                            int bv = (int) Math.floor(p.pv + pose.dz() + r[2]);
                            s.set(bu, by, bv, c.state());
                        }
                    }
                }
            }
        }
    }

    /** Boîte tournée telle que le client l'affiche : 8 coins (u, y, v), état, lumineuse. */
    public record Box(double[][] corners, String state, boolean glow) {
    }

    /** Les cubes à l'instant t, en boîtes tournées (aperçus fidèles au rendu des BlockDisplay), sans le pack. */
    public List<Box> boxes(double t) {
        return boxes(t, false);
    }

    /** Idem, avec ou sans le pack : un modèle est rendu élément par élément (voir {@link Models}). */
    public List<Box> boxes(double t, boolean pack) {
        List<Box> out = new ArrayList<>();
        for (Part p : parts) {
            Pose pose = p.motion.at(t);
            for (Cube c : p.cubes) {
                if (!c.shown(pack)) continue;
                if (c.model() != null) {
                    modelBoxes(p, c, pose, out);
                    continue;
                }
                double[][] k = new double[8][];
                int i = 0;
                for (int dx = -1; dx <= 1; dx += 2) {
                    for (int dy = -1; dy <= 1; dy += 2) {
                        for (int dz = -1; dz <= 1; dz += 2) {
                            double[] own = rot(p.axis, c.angle(), dx * c.sx() / 2, dy * c.sy() / 2, dz * c.sz() / 2);
                            double[] r = rot(p.axis, pose.angle(), c.x() + own[0], c.y() + own[1], c.z() + own[2]);
                            k[i++] = new double[]{p.pu + pose.dx() + r[0], p.py + pose.dy() + r[1], p.pv + pose.dz() + r[2]};
                        }
                    }
                }
                out.add(new Box(k, c.state(), c.glow()));
            }
        }
        return out;
    }

    /** Couleur (0xRRGGBB) d'un élément d'un modèle pour les aperçus (teinte appliquée). */
    public static String colorState(int rgb) {
        return "#" + String.format("%06x", rgb & 0xFFFFFF);
    }

    private void modelBoxes(Part p, Cube c, Pose pose, List<Box> out) {
        List<Models.El> els = Models.MODELS.get(c.model());
        if (els == null) return;
        for (Models.El e : els) {
            double[][] k = new double[8][];
            int i = 0;
            for (int bx = 0; bx <= 1; bx++) {
                for (int by = 0; by <= 1; by++) {
                    for (int bz = 0; bz <= 1; bz++) {
                        double mx = bx == 0 ? e.x1() : e.x2(), my = by == 0 ? e.y1() : e.y2(), mz = bz == 0 ? e.z1() : e.z2();
                        if (e.axis() != 'n') {
                            double a = Math.toRadians(e.angle()), co = Math.cos(a), si = Math.sin(a);
                            double dx = mx - e.ox(), dy = my - e.oy(), dz = mz - e.oz();
                            switch (e.axis()) {
                                case 'x' -> { my = e.oy() + dy * co - dz * si; mz = e.oz() + dy * si + dz * co; }
                                case 'y' -> { mx = e.ox() + dx * co + dz * si; mz = e.oz() - dx * si + dz * co; }
                                default -> { mx = e.ox() + dx * co - dy * si; my = e.oy() + dx * si + dy * co; }
                            }
                        }
                        // unités du modèle -> blocs, centre (8, 8, 8), échelle, cap propre puis pose
                        double lx = (mx / 16 - 0.5) * c.sx(), ly = (my / 16 - 0.5) * c.sy(), lz = (mz / 16 - 0.5) * c.sz();
                        double[] own = rot(p.axis, c.angle(), lx, ly, lz);
                        double[] r = rot(p.axis, pose.angle(), c.x() + own[0], c.y() + own[1], c.z() + own[2]);
                        k[i++] = new double[]{p.pu + pose.dx() + r[0], p.py + pose.dy() + r[1], p.pv + pose.dz() + r[2]};
                    }
                }
            }
            int col = e.tint() && c.tint() >= 0 ? c.tint() : e.color();
            out.add(new Box(k, colorState(col), false));
        }
    }

    /** Nombre total de cubes (= BlockDisplay en jeu). */
    public int cubes() {
        int n = 0;
        for (Part p : parts) n += p.cubes.size();
        return n;
    }
}
