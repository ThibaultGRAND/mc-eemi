package fr.eemicraft.jump;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Parcours Â« Balade tranquille Â» genere par code (aucune dependance a Bukkit : testable hors serveur).
 * Une suite d'iles (une par etape, point de controle au centre) reliees par 3 a 5 sauts faciles au-dessus d'un lagon :
 * vide de 2 blocs au plus a plat, 1 bloc au plus pour une montee de 1, jamais plus de 1 de montee, aucun saut au pixel
 * (plots de 3x3, 2x2 ou 1x1 seulement pour les sauts de 1). Tomber = eau du lagon = retour a l'ile.
 * Cinq ambiances de 10 etapes : prairie, cerisiers, plage, automne, neige.
 */
final class Tranquille {
    static final int VERSION = 1;
    static final int SEA = 60;           // dessus de l'eau
    static final int BASE_Y = 66;        // pieds au depart

    interface Sink {
        void set(int x, int y, int z, String state);
    }

    /** Point de controle : centre de l'ile, pieds, regard, hauteur de chute. */
    static final class Point {
        final int x, z;
        final double y, failY;
        final float yaw;

        Point(int x, double y, int z, float yaw, double failY) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.failY = failY;
        }
    }

    /** Plot de saut : rectangle x1..x2, z1..z2, dessus en y (pieds en y + 1). */
    record Pad(int x1, int z1, int x2, int z2, int y) {
        double cx() {
            return (x1 + x2 + 1) / 2.0;
        }

        double cz() {
            return (z1 + z2 + 1) / 2.0;
        }

        /** Distance horizontale (vide) entre deux rectangles. */
        double gap(Pad o) {
            double dx = Math.max(0, Math.max(o.x1 - (x2 + 1), x1 - (o.x2 + 1)));
            double dz = Math.max(0, Math.max(o.z1 - (z2 + 1), z1 - (o.z2 + 1)));
            return Math.hypot(dx, dz);
        }
    }

    record Island(int cx, int cz, int r, int y, int zone) {
        Pad box() {
            return new Pad(cx - r + 1, cz - r + 1, cx + r - 1, cz + r - 1, y);
        }
    }

    final List<Point> points = new ArrayList<>();
    final List<Pad> pads = new ArrayList<>();
    final List<Island> islands = new ArrayList<>();
    final List<String> problems = new ArrayList<>();
    double maxFlatGap, maxUpGap;
    int jumps;
    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
    private final Random rnd;
    private final int steps;

    static final String[][] ZONES = {
        // dessus d'ile, sous-couche, plot principal, plot secondaire, dessous de plot
        {"grass_block", "dirt", "moss_block", "oak_planks", "rooted_dirt"},
        {"grass_block", "dirt", "cherry_planks", "pink_terracotta", "cherry_wood"},
        {"sand", "sandstone", "smooth_sandstone", "cut_sandstone", "sandstone"},
        {"podzol", "coarse_dirt", "spruce_planks", "orange_terracotta", "stripped_spruce_wood"},
        {"snow_block", "dirt", "snow_block", "spruce_planks", "packed_ice"},
    };
    static final String[] ZONE_NAMES = {"Prairie", "Cerisiers", "Plage", "Automne", "Neige"};

    Tranquille(long seed, int steps) {
        this.rnd = new Random(seed);
        this.steps = steps;
        layout();
    }

    int zone(int step) {
        return Math.min(ZONES.length - 1, step * ZONES.length / Math.max(1, steps));
    }

    // ------------------------------------------------------------------ trace

    private void layout() {
        int y = BASE_Y - 1;
        Island cur = new Island(0, 0, 4, y, 0);
        islands.add(cur);
        double angle = 0;
        for (int step = 0; step < steps; step++) {
            int zone = zone(step);
            // direction douce : toujours vers +x, jamais plus de 55 degres de cote
            angle += (rnd.nextDouble() - 0.5) * 0.9;
            angle = Math.max(-0.95, Math.min(0.95, angle * 0.85 + Math.sin(step * 0.55) * 0.18));
            double dx = Math.cos(angle), dz = Math.sin(angle);
            Pad prev = cur.box();
            double minFeet = cur.y + 1;
            int n = 3 + rnd.nextInt(3);
            for (int j = 0; j < n; j++) {
                int dy = 0;
                double roll = rnd.nextDouble();
                if (roll < 0.28 && y < BASE_Y + 9) {
                    dy = 1;
                } else if (roll < 0.45 && y > BASE_Y - 3) {
                    dy = -1;
                }
                int size = dy == 1 ? (rnd.nextDouble() < 0.6 ? 2 : 3) : (rnd.nextDouble() < 0.2 ? 1 : rnd.nextDouble() < 0.6 ? 2 : 3);
                double maxGap = dy == 1 ? 1.0 : dy == -1 ? 2.0 : (size == 1 ? 1.0 : 2.0);
                double want = dy == 1 ? (rnd.nextBoolean() ? 0.0 : 1.0) : 1 + rnd.nextInt((int) maxGap);
                Pad p = place(prev, size, dx, dz, y + dy, want, maxGap);
                if (p == null) {
                    // repli : plot de 2x2 au meme niveau, vide de 1
                    p = place(prev, 2, dx, dz, y, 1.0, 1.0);
                    dy = 0;
                }
                if (p == null) {
                    problems.add("etape " + step + " : plot force");
                    double ph = (prev.x2() - prev.x1() + 1) / 2.0;
                    int x1 = (int) Math.round(prev.cx() + dx * (ph + 2) - 1), z1 = (int) Math.round(prev.cz() + dz * (ph + 2) - 1);
                    p = new Pad(x1, z1, x1 + 1, z1 + 1, y);
                    dy = 0;
                }
                y += dy;
                pads.add(p);
                record(prev, p);
                minFeet = Math.min(minFeet, p.y + 1);
                prev = p;
            }
            int r = step == steps - 1 ? 5 : 3 + rnd.nextInt(2);
            Island next = null;
            for (double want = 1.0; want <= 2.0 && next == null; want += 1.0) {
                for (int d = r; d < r + 8 && next == null; d++) {
                    int cx = (int) Math.round(prev.cx() + dx * (d + 0.5));
                    int cz = (int) Math.round(prev.cz() + dz * (d + 0.5));
                    Island isl = new Island(cx, cz, r, y, zone(step + 1));
                    double g = prev.gap(isl.box());
                    if (g >= want - 0.01 && g <= 2.0 && clearOf(isl.box(), prev)) {
                        next = isl;
                    }
                }
            }
            if (next == null) {
                problems.add("etape " + step + " : ile introuvable");
                next = new Island((int) Math.round(prev.cx() + dx * (r + 2)), (int) Math.round(prev.cz() + dz * (r + 2)), r, y, zone(step + 1));
            }
            record(prev, next.box());
            islands.add(next);
            double failY = Math.min(minFeet, next.y + 1) - 2.5;
            Pad first = pads.get(pads.size() - n);
            points.add(new Point(cur.cx, cur.y + 1, cur.cz, yaw(first.cx() - (cur.cx + 0.5), first.cz() - (cur.cz + 0.5)), failY));
            cur = next;
        }
        // arrivee
        points.add(new Point(cur.cx, cur.y + 1, cur.cz, yaw(1, 0), cur.y + 1 - 2.5));
        for (Pad p : pads) {
            bound(p);
        }
        for (Island i : islands) {
            bound(i.box());
        }
    }

    private void bound(Pad p) {
        minX = Math.min(minX, p.x1 - 6);
        maxX = Math.max(maxX, p.x2 + 6);
        minZ = Math.min(minZ, p.z1 - 6);
        maxZ = Math.max(maxZ, p.z2 + 6);
    }

    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Pose un plot apres prev dans la direction (dx, dz), vide visÃ© Â« want Â», au plus maxGap. */
    private Pad place(Pad prev, int size, double dx, double dz, int top, double want, double maxGap) {
        double half = size / 2.0;
        double ph = (prev.x2 - prev.x1 + 1) / 2.0;
        for (double extra = 0; extra < 4; extra += 0.5) {
            double d = ph + half + want + extra - 0.5;
            if (d < 0.5) {
                continue;
            }
            double cx = prev.cx() + dx * d, cz = prev.cz() + dz * d;
            int x1 = (int) Math.round(cx - half), z1 = (int) Math.round(cz - half);
            Pad p = new Pad(x1, z1, x1 + size - 1, z1 + size - 1, top);
            double g = prev.gap(p);
            if (g > maxGap + 0.01) {
                return null;
            }
            if (g >= want - 0.01 && (top <= prev.y || g >= 0) && clearOf(p, prev)) {
                return p;
            }
        }
        return null;
    }

    /** Le plot ne touche aucun plot / ile deja pose, sauf le precedent (pas de raccourci). */
    private boolean clearOf(Pad p, Pad prev) {
        // les 3 derniers plots et la derniere ile sont voisins par construction
        for (int i = pads.size() - 4; i >= 0 && i >= pads.size() - 400; i--) {
            Pad o = pads.get(i);
            if (o.equals(prev)) {
                continue;
            }
            if (p.gap(o) < 3.5) {
                return false;
            }
        }
        for (int i = islands.size() - 2; i >= 0; i--) {
            Island is = islands.get(i);
            // boite reelle de l'ile (bord bruite compris), pas seulement son coeur
            Pad o = new Pad(is.cx - is.r - 1, is.cz - is.r - 1, is.cx + is.r + 1, is.cz + is.r + 1, is.y);
            if (is.box().equals(prev)) {
                continue;
            }
            if (p.gap(o) < 3.5) {
                return false;
            }
        }
        return true;
    }

    private void record(Pad a, Pad b) {
        double g = a.gap(b);
        int dy = b.y - a.y;
        jumps++;
        if (dy > 1) {
            problems.add("montee de " + dy + " en " + b);
        }
        if (dy == 1) {
            maxUpGap = Math.max(maxUpGap, g);
            if (g > 1.01) {
                problems.add("montee avec vide " + g + " en " + b);
            }
        } else {
            maxFlatGap = Math.max(maxFlatGap, g);
            if (g > 2.01) {
                problems.add("vide " + g + " en " + b);
            }
        }
    }

    // ------------------------------------------------------------------ construction

    void build(Sink out) {
        // lagon : fond de sable, 3 de profondeur, bord de plage
        Map<Long, Integer> dist = distances(14);
        for (Map.Entry<Long, Integer> e : dist.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = e.getKey().intValue();
            int d = e.getValue();
            if (d <= 10) {
                out.set(x, SEA - 4, z, noise(x, z, 7) > 0.3 ? "gravel" : "sand");
                for (int yy = SEA - 3; yy <= SEA - 1; yy++) {
                    out.set(x, yy, z, "water");
                }
                if (d <= 9 && noise(x * 3, z * 3, 3) > 0.62) {
                    out.set(x, SEA - 3, z, "seagrass");
                }
            } else {
                int h = SEA - 1 + (d - 10) / 2;
                for (int yy = SEA - 5; yy <= h; yy++) {
                    out.set(x, yy, z, yy == h ? (d >= 13 && noise(x, z, 5) > 0.1 ? "grass_block" : "sand") : yy > h - 2 ? "sand" : "sandstone");
                }
                if (d >= 13 && noise(x * 2, z * 2, 9) > 0.55) {
                    out.set(x, h + 1, z, noise(x, z, 11) > 0.5 ? "short_grass" : "dead_bush");
                }
            }
        }
        for (int i = 0; i < islands.size(); i++) {
            island(out, islands.get(i), i);
        }
        for (Pad p : pads) {
            int zone = zoneAt(p);
            String[] z = ZONES[zone];
            boolean alt = noise(p.x1, p.z1, 13) > 0.55;
            for (int x = p.x1; x <= p.x2; x++) {
                for (int zz = p.z1; zz <= p.z2; zz++) {
                    out.set(x, p.y, zz, alt ? z[3] : z[2]);
                    // petite stalactite sous le plot
                    int len = 1 + (int) (Math.abs(noise(x, zz, 17)) * 3);
                    for (int k = 1; k <= len && p.y - k > SEA; k++) {
                        out.set(x, p.y - k, zz, z[4]);
                    }
                }
            }
            // plots bas : pilotis jusqu'a l'eau
            if (p.y - SEA <= 5) {
                for (int yy = p.y - 1; yy >= SEA - 3; yy--) {
                    out.set(p.x1, yy, p.z1, z[4]);
                }
            }
            if (zone == 4 && p.x2 > p.x1) {
                out.set(p.x1, p.y + 1, p.z1, "snow[layers=1]");
            }
        }
    }

    int zoneAt(Pad p) {
        // zone de l'ile la plus proche en x
        int best = 0;
        double bd = 1e9;
        for (Island i : islands) {
            double d = Math.abs(i.cx - p.cx()) + Math.abs(i.cz - p.cz());
            if (d < bd) {
                bd = d;
                best = i.zone;
            }
        }
        return best;
    }

    private void island(Sink out, Island is, int idx) {
        String[] z = ZONES[is.zone];
        int r = is.r;
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int zz = -r - 1; zz <= r + 1; zz++) {
                double d = Math.hypot(x, zz) + noise(is.cx + x, is.cz + zz, 4) * 0.6;
                // le carre central (zone du point et bords de saut) est toujours plein
                boolean core = Math.abs(x) <= r - 1 && Math.abs(zz) <= r - 1;
                if (!core && d > r + 0.4) {
                    continue;
                }
                int wx = is.cx + x, wz = is.cz + zz;
                out.set(wx, is.y, wz, z[0]);
                out.set(wx, is.y - 1, wz, z[1]);
                int depth = (int) Math.max(1, (r + 1.5 - d) * 1.4) + 1;
                for (int k = 2; k <= depth + 1 && is.y - k > SEA - 4; k++) {
                    out.set(wx, is.y - k, wz, k <= 2 ? z[1] : noise(wx, wz + k, 6) > 0.4 ? "andesite" : "stone");
                }
            }
        }
        // repere du point : dalle doree au centre, lanterne sur un poteau
        out.set(is.cx, is.y, is.cz, idx == islands.size() - 1 ? "gold_block" : "chiseled_stone_bricks");
        out.set(is.cx, is.y + 1, is.cz, "light_weighted_pressure_plate");
        // poteau a lanterne sur le bord oppose a la sortie
        int[] corner = sideSpot(is, idx, 0);
        out.set(corner[0], is.y + 1, corner[1], is.zone == 2 ? "birch_fence" : is.zone == 4 ? "spruce_fence" : "oak_fence");
        out.set(corner[0], is.y + 2, corner[1], "oak_fence");
        out.set(corner[0], is.y + 3, corner[1], "lantern");
        // un arbre ou un decor sur un cote (pas dans l'axe des sauts)
        int[] t = sideSpot(is, idx, 1);
        if (r >= 4 || idx % 2 == 0) {
            tree(out, t[0], is.y + 1, t[1], is.zone);
        }
        // fleurs / herbes
        for (int x = -r + 1; x <= r - 1; x++) {
            for (int zz = -r + 1; zz <= r - 1; zz++) {
                int wx = is.cx + x, wz = is.cz + zz;
                if (Math.abs(x) <= 1 && Math.abs(zz) <= 1 || wx == corner[0] && wz == corner[1]) {
                    continue;
                }
                double n = noise(wx * 2, wz * 2, 21);
                if (n > 0.45) {
                    out.set(wx, is.y + 1, wz, flower(is.zone, wx, wz));
                }
            }
        }
        if (idx == islands.size() - 1) {
            // arrivee : anneau d'or et lanternes
            for (int a = 0; a < 8; a++) {
                int wx = is.cx + (int) Math.round(Math.cos(a * Math.PI / 4) * 3);
                int wz = is.cz + (int) Math.round(Math.sin(a * Math.PI / 4) * 3);
                out.set(wx, is.y, wz, "gold_block");
            }
        }
    }

    /** Case sur le cote de l'ile, perpendiculaire a l'axe entree / sortie (k = 0 a gauche, 1 a droite). */
    private int[] sideSpot(Island is, int idx, int k) {
        double ax = 1, az = 0;
        if (idx + 1 < islands.size()) {
            ax = islands.get(idx + 1).cx - is.cx;
            az = islands.get(idx + 1).cz - is.cz;
        } else if (idx > 0) {
            ax = is.cx - islands.get(idx - 1).cx;
            az = is.cz - islands.get(idx - 1).cz;
        }
        double l = Math.hypot(ax, az);
        ax /= l;
        az /= l;
        double px = -az, pz = ax;
        if (k == 1) {
            px = -px;
            pz = -pz;
        }
        int d = is.r - 1;
        return new int[] {is.cx + (int) Math.round(px * d), is.cz + (int) Math.round(pz * d)};
    }

    private String flower(int zone, int x, int z) {
        double n = noise(x, z, 31);
        return switch (zone) {
            case 0 -> n > 0.2 ? "poppy" : n > -0.2 ? "dandelion" : n > -0.5 ? "cornflower" : "short_grass";
            case 1 -> n > 0 ? "pink_petals" : "short_grass";
            case 2 -> n > 0.3 ? "dead_bush" : "short_grass";
            case 3 -> n > 0.3 ? "leaf_litter" : n > -0.1 ? "fern" : "short_grass";
            default -> "snow[layers=1]";
        };
    }

    private void tree(Sink out, int x, int y, int z, int zone) {
        String log, leaves;
        int h;
        switch (zone) {
            case 1 -> { log = "cherry_log"; leaves = "cherry_leaves[persistent=true]"; h = 4; }
            case 2 -> { log = "jungle_log"; leaves = "jungle_leaves[persistent=true]"; h = 5; }
            case 3 -> { log = "dark_oak_log"; leaves = "dark_oak_leaves[persistent=true]"; h = 4; }
            case 4 -> { log = "spruce_log"; leaves = "spruce_leaves[persistent=true]"; h = 5; }
            default -> { log = "oak_log"; leaves = "oak_leaves[persistent=true]"; h = 4; }
        }
        for (int k = 0; k < h; k++) {
            out.set(x, y + k, z, log);
        }
        int top = y + h;
        if (zone == 4) {
            // sapin en cone
            for (int k = 0; k <= 3; k++) {
                int rr = 2 - k / 2;
                for (int a = -rr; a <= rr; a++) {
                    for (int b = -rr; b <= rr; b++) {
                        if (Math.abs(a) + Math.abs(b) <= rr + 1 && !(a == 0 && b == 0 && k < 3)) {
                            out.set(x + a, top - 2 + k, z + b, leaves);
                        }
                    }
                }
            }
            out.set(x, top + 2, z, leaves);
            return;
        }
        if (zone == 2) {
            // palmier : couronne en croix
            out.set(x, top, z, leaves);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                out.set(x + d[0], top, z + d[1], leaves);
                out.set(x + 2 * d[0], top, z + 2 * d[1], leaves);
                out.set(x + 3 * d[0], top - 1, z + 3 * d[1], leaves);
            }
            return;
        }
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                for (int c = -1; c <= 1; c++) {
                    double d = Math.sqrt(a * a + b * b + c * c * 2.2) + noise(x + a, z + b + c, 41) * 0.5;
                    if (d <= 2.3) {
                        out.set(x + a, top + c, z + b, leaves);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ lagon

    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** Distance (en cases, 8 voisins) de chaque colonne au plus proche plot / ile, jusqu'a max. */
    Map<Long, Integer> distances(int max) {
        Map<Long, Integer> d = new HashMap<>();
        ArrayList<long[]> q = new ArrayList<>();
        for (Pad p : pads) {
            seed(d, q, p);
        }
        for (Island i : islands) {
            seed(d, q, new Pad(i.cx - i.r - 1, i.cz - i.r - 1, i.cx + i.r + 1, i.cz + i.r + 1, i.y));
        }
        int head = 0;
        while (head < q.size()) {
            long[] c = q.get(head++);
            int x = (int) c[0], z = (int) c[1], v = (int) c[2];
            if (v >= max) {
                continue;
            }
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    long k = key(x + a, z + b);
                    if (!d.containsKey(k)) {
                        d.put(k, v + 1);
                        q.add(new long[] {x + a, z + b, v + 1});
                    }
                }
            }
        }
        return d;
    }

    private void seed(Map<Long, Integer> d, ArrayList<long[]> q, Pad p) {
        for (int x = p.x1; x <= p.x2; x++) {
            for (int z = p.z1; z <= p.z2; z++) {
                long k = key(x, z);
                if (!d.containsKey(k)) {
                    d.put(k, 0);
                    q.add(new long[] {x, z, 0});
                }
            }
        }
    }

    /** Bruit de valeur lisse (-1..1), deterministe. */
    static double noise(int x, int z, int salt) {
        double fx = x / 5.0, fz = z / 5.0;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = hash(ix, iz, salt), b = hash(ix + 1, iz, salt), c = hash(ix, iz + 1, salt), e = hash(ix + 1, iz + 1, salt);
        return (a + (b - a) * tx) + ((c + (e - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    static double hash(int x, int z, int salt) {
        long h = x * 374761393L + z * 668265263L + salt * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return ((h & 0xffff) / 32767.5) - 1;
    }
}
