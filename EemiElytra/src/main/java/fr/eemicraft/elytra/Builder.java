package fr.eemicraft.elytra;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.noise.SimplexNoiseGenerator;

/**
 * Construction d'un parcours, decoupee en petites etapes executees quelques millisecondes par tick :
 * effacement de l'ancienne version, terrain tuile par tuile (16 x 16), objets (iles, ponts, cascades),
 * puis tour de depart, anneaux et terrasse d'arrivee. Le decor n'est jamais pose a moins de CLEAR blocs
 * de la ligne de vol.
 */
final class Builder {
    static final double CLEAR = 9;
    static final int Y_MIN = -64, Y_MAX = 319;

    final EemiElytra pl;
    final World w;
    final Course c;
    final SimplexNoiseGenerator nz;
    final List<Runnable> steps = new ArrayList<>();
    final Set<Long> chunks = new LinkedHashSet<>();
    int pos;
    long ops;
    long nanos;
    final long started = System.currentTimeMillis();
    /** Rayon du tunnel (grotte) par echantillon. */
    double[] tunnelR;
    double sEnter, sExit;
    int cloudY;
    final List<Double> arches = new ArrayList<>();

    Builder(EemiElytra pl, World w, Course c, List<Long> oldChunks, int oldY0, int oldY1) {
        this.pl = pl;
        this.w = w;
        this.c = c;
        this.nz = new SimplexNoiseGenerator(c.seed);
        prepareTheme();
        for (long k : oldChunks) {
            int cx = (int) (k >> 32), cz = (int) k;
            steps.add(() -> clearChunk(cx, cz, oldY0, oldY1));
        }
        int q = extent();
        for (int i = 0; i < c.n; i += 4) {
            int x1 = Math.floorDiv((int) Math.floor(c.sx[i] - q), 16), x2 = Math.floorDiv((int) Math.floor(c.sx[i] + q), 16);
            int z1 = Math.floorDiv((int) Math.floor(c.sz[i] - q), 16), z2 = Math.floorDiv((int) Math.floor(c.sz[i] + q), 16);
            for (int cx = x1; cx <= x2; cx++) {
                for (int cz = z1; cz <= z2; cz++) {
                    chunks.add(Course.key(cx, cz));
                }
            }
        }
        // tour de depart (derriere le point de depart)
        for (int cx = Math.floorDiv(c.ox - 20, 16); cx <= Math.floorDiv(c.ox + 4, 16); cx++) {
            for (int cz = Math.floorDiv(c.oz - 10, 16); cz <= Math.floorDiv(c.oz + 10, 16); cz++) {
                chunks.add(Course.key(cx, cz));
            }
        }
        for (long k : chunks) {
            int cx = (int) (k >> 32), cz = (int) k;
            steps.add(() -> tile(cx * 16, cz * 16));
        }
        if (c.theme.equals("archipel")) {
            planIslands();
        }
        steps.add(this::tower);
        steps.add(this::terrace);
        steps.add(this::rings);
        if (c.theme.equals("grotte")) {
            steps.add(this::caveLights);
        }
    }

    int yLow() {
        return Math.max(Y_MIN, (int) c.minY - 70);
    }

    int yHigh() {
        return Math.min(Y_MAX, (int) c.maxY + 45);
    }

    /** Distance au trace couverte par le decor. */
    int extent() {
        return switch (c.theme) {
            case "canyon" -> 46;
            case "archipel" -> 58;
            default -> 42;
        };
    }

    /** Execute des etapes pendant budget nanosecondes ; vrai quand tout est fini. */
    boolean run(long budget) {
        long t0 = System.nanoTime();
        while (pos < steps.size() && System.nanoTime() - t0 < budget) {
            Runnable r = steps.get(pos++);
            try {
                r.run();
            } catch (RuntimeException e) {
                pl.getLogger().warning("Construction de " + c.name + " : etape " + pos + " en erreur : " + e);
            }
        }
        nanos += System.nanoTime() - t0;
        return pos >= steps.size();
    }

    int percent() {
        return steps.isEmpty() ? 100 : pos * 100 / steps.size();
    }

    // ================================================================ blocs

    void put(int x, int y, int z, String b) {
        if (y < Y_MIN || y > Y_MAX) {
            return;
        }
        w.getBlockAt(x, y, z).setBlockData(pl.data(b), false);
        ops++;
    }

    /** Pose seulement hors du couloir de vol. */
    void tput(int x, int y, int z, String b) {
        if (c.inTube(x + 0.5, y + 0.5, z + 0.5, CLEAR)) {
            return;
        }
        put(x, y, z, b);
    }

    double N(double a, double b) {
        return nz.noise(a, b);
    }

    double hash(int x, int y, int z) {
        long h = c.seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    void clearChunk(int cx, int cz, int y0, int y1) {
        Chunk ch = w.getChunkAt(cx, cz);
        ChunkSnapshot snap = ch.getChunkSnapshot(false, false, false);
        int lo = Math.max(Y_MIN, y0), hi = Math.min(Y_MAX, y1);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = lo; y <= hi; y++) {
                    if (!snap.getBlockType(x, y, z).isAir()) {
                        ch.getBlock(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    // ================================================================ themes

    private void prepareTheme() {
        if (c.theme.equals("canyon")) {
            int[] at = {5, 12, 19, 26};
            for (int i : at) {
                if (i + 1 < c.rings.size() - 1) {
                    arches.add((c.rings.get(i).s + c.rings.get(i + 1).s) / 2);
                }
            }
        } else if (c.theme.equals("archipel")) {
            cloudY = (int) Math.floor(c.minY) - 28;
        } else if (c.theme.equals("grotte")) {
            sEnter = c.rings.get(1).s + 12;
            sExit = c.rings.get(c.rings.size() - 1).s - 4;
            tunnelR = new double[c.n];
            double total = c.length;
            for (int i = 0; i < c.n; i++) {
                double s = c.ss[i];
                double r = 13 + 2 * N(s / 40.0, 3.3);
                r += 10 * Math.exp(-Math.pow((s - total * 0.35) / 45.0, 2));
                r += 10 * Math.exp(-Math.pow((s - total * 0.7) / 45.0, 2));
                tunnelR[i] = r;
            }
        }
    }

    void tile(int bx, int bz) {
        switch (c.theme) {
            case "canyon" -> canyonTile(bx, bz);
            case "archipel" -> cloudTile(bx, bz);
            default -> caveTile(bx, bz);
        }
    }

    /** Plus proche echantillon (horizontal) parmi cand ; d2[0] recoit la distance au carre. */
    int nearest(int[] cand, double x, double z, double[] d2) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i : cand) {
            double dx = c.sx[i] - x, dz = c.sz[i] - z;
            double d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        d2[0] = bd;
        return best;
    }

    // ---------------------------------------------------------------- canyon rouge

    static final String[] STRATA = {"red_terracotta", "orange_terracotta", "terracotta", "yellow_terracotta", "red_terracotta",
        "white_terracotta", "orange_terracotta", "brown_terracotta", "red_sandstone", "light_gray_terracotta", "red_terracotta", "orange_terracotta"};

    String strata(int x, int y, int z) {
        int band = Math.floorDiv(y + (int) Math.round(2 * N(x / 30.0, z / 30.0)), 2);
        return STRATA[Math.floorMod(band, STRATA.length)];
    }

    void canyonTile(int bx, int bz) {
        final double W = 44;
        int q = 46;
        int[] cand = c.near(bx - q, bz - q, bx + 16 + q, bz + 16 + q);
        if (cand.length == 0) {
            return;
        }
        final int S = 18;
        final int NONE = Integer.MIN_VALUE;
        int[] top = new int[S * S], flo = new int[S * S], idx = new int[S * S];
        double[] dd = new double[S * S], rim = new double[S * S], w0a = new double[S * S];
        double[] d2 = new double[1];
        for (int i = 0; i < S; i++) {
            for (int j = 0; j < S; j++) {
                int x = bx - 1 + i, z = bz - 1 + j, k = i * S + j;
                int n = nearest(cand, x + 0.5, z + 0.5, d2);
                double d = Math.sqrt(d2[0]);
                idx[k] = n;
                dd[k] = d;
                if (n < 0 || d > W) {
                    top[k] = NONE;
                    continue;
                }
                double py = c.sy[n], s = c.ss[n];
                int floorY = (int) Math.floor(py - 24 + 3 * N(s / 50.0, 7.3));
                double w0 = 12 + 4 * N(s / 70.0, 31.1);
                double rm = py + 15 + 8 * N(x / 45.0, z / 45.0) + 3 * N(x / 12.0, z / 12.0);
                rm = Math.floor(rm / 3) * 3;
                flo[k] = floorY;
                rim[k] = rm;
                w0a[k] = w0;
                top[k] = d <= w0 ? floorY : (int) Math.floor(Math.min(rm, floorY + (d - w0) * 2.6));
            }
        }
        for (int i = 1; i <= 16; i++) {
            for (int j = 1; j <= 16; j++) {
                int k = i * S + j;
                if (top[k] == NONE) {
                    continue;
                }
                int x = bx - 1 + i, z = bz - 1 + j;
                int minN = Integer.MAX_VALUE;
                int[] nb = {k - S, k + S, k - 1, k + 1};
                for (int o : nb) {
                    minN = Math.min(minN, top[o] == NONE ? -10000 : top[o]);
                }
                int n = idx[k];
                double d = dd[k], py = c.sy[n], s = c.ss[n];
                boolean floor = d <= w0a[k];
                double river = 3 + 1.5 * N(s / 30.0, 91.7);
                int t = top[k], floorY = flo[k];
                int lo = Math.min(Math.max(floorY - 5, minN - 1), t - 3);
                boolean nearPath = d < CLEAR + 2;
                for (int y = lo; y <= t; y++) {
                    String b;
                    if (floor) {
                        if (y == t) {
                            b = d < river ? "water" : d < river + 1.6 ? "sand" : "red_sand";
                        } else if (y == t - 1 && d < river - 1) {
                            b = "water";
                        } else {
                            b = strata(x, y, z);
                        }
                    } else {
                        b = y == t && t >= rim[k] - 0.5 ? "red_sand" : strata(x, y, z);
                    }
                    if (nearPath) {
                        tput(x, y, z, b);
                    } else {
                        put(x, y, z, b);
                    }
                }
                // decor du fond et des plateaux
                double h = hash(x, 7, z);
                if (floor) {
                    if (d > river + 3 && h < 0.025) {
                        put(x, t + 1, z, "dead_bush");
                    } else if (d > river + 3 && h < 0.033) {
                        int ch = 1 + (int) (hash(x, 9, z) * 3);
                        for (int y = 1; y <= ch; y++) {
                            put(x, t + y, z, "cactus");
                        }
                    } else if (d >= river && d < river + 1.6 && h < 0.2) {
                        int ch = 2 + (int) (hash(x, 11, z) * 2);
                        for (int y = 1; y <= ch; y++) {
                            put(x, t + y, z, "sugar_cane");
                        }
                    }
                } else if (t >= rim[k] - 0.5 && h < 0.012) {
                    put(x, t + 1, z, "dead_bush");
                }
                // arches naturelles au-dessus du trace
                for (double sa : arches) {
                    if (Math.abs(s - sa) < 2.3 && d < w0a[k] + 16) {
                        double yb = py + 10.5 + 0.035 * d * d;
                        if (yb > rim[k] + 4) {
                            continue;
                        }
                        for (int y = (int) Math.ceil(yb); y <= yb + 4; y++) {
                            tput(x, y, z, hash(x, y, z) < 0.5 ? "smooth_red_sandstone" : "cut_red_sandstone");
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- archipel celeste

    void cloudTile(int bx, int bz) {
        int q = 42;
        int[] cand = c.near(bx - q, bz - q, bx + 16 + q, bz + 16 + q);
        if (cand.length == 0) {
            return;
        }
        double[] d2 = new double[1];
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                int n = nearest(cand, x + 0.5, z + 0.5, d2);
                if (n < 0 || d2[0] > 40 * 40) {
                    continue;
                }
                double v = N(x / 28.0, z / 28.0) + 0.5 * N(x / 9.0, z / 9.0);
                if (v > 0.35) {
                    put(x, cloudY, z, "white_wool");
                }
                if (v > 0.6) {
                    put(x, cloudY + 1, z, "white_wool");
                }
                if (v > 0.85) {
                    put(x, cloudY + 2, z, "white_wool");
                }
            }
        }
    }

    record Island(double x, double y, double z, double r, boolean trees, int side, double s) {
    }

    private void planIslands() {
        Random rnd = new Random(c.seed + 1);
        List<Island> list = new ArrayList<>();
        // ile sous la tour de depart et sous la terrasse d'arrivee
        list.add(new Island(c.ox - 6, c.ys - 42, c.oz, 15, true, 0, -1));
        list.add(new Island(c.endX, Math.floor(c.endY) - 3, c.endZ, 11, false, 0, c.length + 1));
        double s = 25;
        while (s < c.length - 40) {
            int k = c.sampleAt(s);
            int k2 = Math.min(c.n - 1, k + 1);
            double tx = c.sx[k2] - c.sx[k], tz = c.sz[k2] - c.sz[k];
            double tl = Math.sqrt(tx * tx + tz * tz);
            if (tl < 1e-6) {
                tl = 1;
            }
            double lx = -tz / tl, lz = tx / tl;
            int count = rnd.nextDouble() < 0.35 ? 2 : 1;
            int side = rnd.nextDouble() < 0.5 ? -1 : 1;
            for (int i = 0; i < count; i++) {
                int sd = i == 0 ? side : -side;
                double r = 6 + rnd.nextDouble() * 7;
                double off = r * 1.15 + 10 + rnd.nextDouble() * 14;
                double v = -14 + rnd.nextDouble() * 18;
                list.add(new Island(c.sx[k] + lx * off * sd, c.sy[k] + v, c.sz[k] + lz * off * sd, r, true, sd, s));
            }
            if (rnd.nextDouble() < 0.18) {
                double r = 7 + rnd.nextDouble() * 5;
                list.add(new Island(c.sx[k], c.sy[k] - 13 - rnd.nextDouble() * 6, c.sz[k], r, false, 0, s));
            }
            s += 28 + rnd.nextDouble() * 16;
        }
        for (Island is : list) {
            long seed = rnd.nextLong();
            steps.add(() -> island(is, new Random(seed)));
        }
        // ponts de cordes entre iles voisines du meme cote
        for (int i = 2; i < list.size(); i++) {
            Island a = list.get(i);
            if (a.side == 0) {
                continue;
            }
            for (int j = i + 1; j < Math.min(list.size(), i + 4); j++) {
                Island b = list.get(j);
                if (b.side != a.side) {
                    continue;
                }
                double dx = b.x - a.x, dz = b.z - a.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 50 && dist > a.r + b.r + 4) {
                    steps.add(() -> bridge(a, b));
                }
                break;
            }
        }
    }

    static final String[] FLOWERS = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "allium", "azure_bluet", "lily_of_the_valley"};

    void island(Island is, Random r) {
        int R = (int) Math.ceil(is.r * 1.2) + 1;
        int cx = (int) Math.floor(is.x), cy = (int) Math.floor(is.y), cz = (int) Math.floor(is.z);
        int size = 2 * R + 1;
        int[] tops = new int[size * size];
        int[] depths = new int[size * size];
        java.util.Arrays.fill(tops, Integer.MIN_VALUE);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int x = cx + dx, z = cz + dz;
                double rr = Math.sqrt(dx * dx + dz * dz);
                double rEff = is.r * (0.85 + 0.3 * (N(x / 6.0, z / 6.0) + 1) / 2);
                if (rr > rEff) {
                    continue;
                }
                int topY = (int) Math.floor(cy + 1.2 * N(x / 8.0, z / 8.0)) + (rr < is.r * 0.35 ? 1 : 0);
                double q = 1 - (rr / rEff) * (rr / rEff);
                int depth = Math.max(1, (int) (is.r * 1.4 * Math.pow(q, 0.7) + 1 + 2 * N(x / 5.0, z / 5.0)));
                tops[(dx + R) * size + dz + R] = topY;
                depths[(dx + R) * size + dz + R] = depth;
                for (int y = topY - depth; y <= topY; y++) {
                    String b;
                    if (y == topY) {
                        b = "grass_block";
                    } else if (y >= topY - 3) {
                        b = "dirt";
                    } else {
                        double h = hash(x, y, z);
                        b = h < 0.07 ? "andesite" : h < 0.1 ? "coal_ore" : h < 0.13 ? "mossy_cobblestone" : "stone";
                    }
                    tput(x, y, z, b);
                }
                double h = hash(x, topY, z);
                if (h < 0.14) {
                    tput(x, topY + 1, z, "short_grass");
                } else if (h < 0.19) {
                    tput(x, topY + 1, z, FLOWERS[(int) (hash(z, x, 3) * FLOWERS.length)]);
                }
            }
        }
        if (is.trees) {
            int count = 1 + (int) (is.r / 5);
            for (int t = 0; t < count; t++) {
                double a = r.nextDouble() * Math.PI * 2, rad = r.nextDouble() * is.r * 0.55;
                int dx = (int) Math.round(Math.cos(a) * rad), dz = (int) Math.round(Math.sin(a) * rad);
                int top = tops[(dx + R) * size + dz + R];
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                tree(cx + dx, top + 1, cz + dz, r.nextInt(4), r);
            }
        }
        // cascade : une source au bord, une colonne d'eau qui tombe dans le vide
        if (r.nextDouble() < 0.45) {
            double a = r.nextDouble() * Math.PI * 2;
            int lastIn = -1, lx = 0, lz = 0;
            for (int step = 0; step <= R; step++) {
                int dx = (int) Math.round(Math.cos(a) * step), dz = (int) Math.round(Math.sin(a) * step);
                int k = (dx + R) * size + dz + R;
                if (tops[k] == Integer.MIN_VALUE) {
                    if (lastIn >= 0) {
                        int x = cx + dx, z = cz + dz;
                        int topY = tops[lastIn];
                        if (!c.inTube(cx + lx + 0.5, topY + 0.5, cz + lz + 0.5, CLEAR)) {
                            put(cx + lx, topY, cz + lz, "water");
                            int len = depths[lastIn] + 14;
                            boolean ok = true;
                            for (int y = topY; y >= topY - len; y--) {
                                if (c.inTube(x + 0.5, y + 0.5, z + 0.5, CLEAR)) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) {
                                for (int y = topY; y >= topY - len; y--) {
                                    put(x, y, z, "water[level=8]");
                                }
                            }
                        }
                    }
                    break;
                }
                lastIn = k;
                lx = dx;
                lz = dz;
            }
        }
    }

    static final String[][] TREES = {
        {"oak_log", "oak_leaves[persistent=true]"},
        {"birch_log", "birch_leaves[persistent=true]"},
        {"cherry_log", "cherry_leaves[persistent=true]"},
        {"oak_log", "flowering_azalea_leaves[persistent=true]"}};

    void tree(int x, int y, int z, int type, Random r) {
        int h = 4 + r.nextInt(3);
        if (c.inTube(x + 0.5, y + h, z + 0.5, CLEAR + 3) || c.inTube(x + 0.5, y + 0.5, z + 0.5, CLEAR + 1)) {
            return;
        }
        String[] t = TREES[type];
        double rad = type == 2 ? 3.2 : 2.6;
        int cy = y + h - 1;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dz == 0 && dy <= 0) {
                        continue;
                    }
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (d <= rad && hash(x + dx, cy + dy, z + dz) < 0.92) {
                        tput(x + dx, cy + dy, z + dz, t[1]);
                    }
                }
            }
        }
        for (int i = 0; i < h; i++) {
            tput(x, y + i, z, t[0]);
        }
    }

    void bridge(Island a, Island b) {
        double dx = b.x - a.x, dz = b.z - a.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / dist, uz = dz / dist, px = -uz, pz = ux;
        double sxp = a.x + ux * a.r * 0.75, szp = a.z + uz * a.r * 0.75;
        double exp = b.x - ux * b.r * 0.75, ezp = b.z - uz * b.r * 0.75;
        double y0 = Math.floor(a.y) + 1, y1 = Math.floor(b.y) + 1;
        double len = Math.sqrt((exp - sxp) * (exp - sxp) + (ezp - szp) * (ezp - szp));
        int steps = (int) Math.ceil(len * 2);
        int lastPost = -10;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = sxp + (exp - sxp) * t, z = szp + (ezp - szp) * t;
            double y = y0 + (y1 - y0) * t - 2 * Math.sin(Math.PI * t);
            int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
            tput(ix, iy, iz, "spruce_planks");
            tput((int) Math.floor(x + px), iy, (int) Math.floor(z + pz), "spruce_slab[type=top]");
            tput((int) Math.floor(x - px), iy, (int) Math.floor(z - pz), "spruce_slab[type=top]");
            if (i - lastPost >= 6) {
                lastPost = i;
                tput((int) Math.floor(x + px * 1.6), iy + 1, (int) Math.floor(z + pz * 1.6), "spruce_fence");
                tput((int) Math.floor(x - px * 1.6), iy + 1, (int) Math.floor(z - pz * 1.6), "spruce_fence");
                if (i % 18 == 0) {
                    tput((int) Math.floor(x + px * 1.6), iy + 2, (int) Math.floor(z + pz * 1.6), "lantern");
                }
            }
        }
    }

    // ---------------------------------------------------------------- grotte de cristal

    void caveTile(int bx, int bz) {
        int q = 42;
        int[] cand = c.near(bx - q, bz - q, bx + 16 + q, bz + 16 + q);
        if (cand.length == 0) {
            return;
        }
        double[] d2 = new double[1];
        int[] tc = new int[cand.length];
        double[] tdh = new double[cand.length];
        double[] mArr = new double[512];
        int[] bestArr = new int[512];
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                int n = nearest(cand, x + 0.5, z + 0.5, d2);
                if (n < 0) {
                    continue;
                }
                double s = c.ss[n];
                if (s < sEnter || s > sExit) {
                    continue;
                }
                double d = Math.sqrt(d2[0]);
                double py = c.sy[n], rn = tunnelR[n];
                double W = Math.max(30 + 6 * N(x / 40.0, z / 40.0), rn + 7);
                if (d > W) {
                    continue;
                }
                double f = (d / W) * (d / W);
                int top = (int) Math.floor(Math.max(py + 22 + 8 * N(x / 35.0, z / 35.0) - f * 12, py + rn + 6));
                int bottom = (int) Math.floor(Math.min(py - 24 - 4 * N(x / 25.0, z / 25.0) + f * 10, py - rn - 6));
                boolean edge = d > W - 3 || s - sEnter < 5 || sExit - s < 5;
                // echantillons assez proches pour que leur tunnel touche cette colonne
                int k = 0;
                double bandLo = Double.MAX_VALUE, bandHi = -Double.MAX_VALUE;
                for (int i : cand) {
                    double dx = c.sx[i] - (x + 0.5), dz = c.sz[i] - (z + 0.5);
                    double h2 = dx * dx + dz * dz;
                    double lim = tunnelR[i] + 5;
                    if (h2 < lim * lim) {
                        tc[k] = i;
                        tdh[k] = h2;
                        k++;
                        bandLo = Math.min(bandLo, c.sy[i] - lim);
                        bandHi = Math.max(bandHi, c.sy[i] + lim);
                    }
                }
                int span = top - bottom + 1;
                if (span <= 0 || span > 500) {
                    continue;
                }
                for (int y = bottom; y <= top; y++) {
                    double m = 99;
                    int best = -1;
                    if (k > 0 && y >= bandLo && y <= bandHi) {
                        for (int a = 0; a < k; a++) {
                            int i = tc[a];
                            double dy = y + 0.5 - c.sy[i];
                            double v = Math.sqrt(tdh[a] + dy * dy) - tunnelR[i];
                            if (v < m) {
                                m = v;
                                best = i;
                            }
                        }
                    }
                    mArr[y - bottom] = m;
                    bestArr[y - bottom] = best;
                }
                for (int y = bottom; y <= top; y++) {
                    double m = mArr[y - bottom];
                    int best = bestArr[y - bottom];
                    if (m < 0) {
                        // interieur du tunnel : riviere souterraine au fond
                        double sb = c.ss[best];
                        boolean wet = y + 0.5 < c.sy[best] - tunnelR[best] + 3;
                        if (wet) {
                            boolean dam = sb < sEnter + 10 || sb > sExit - 10;
                            tput(x, y, z, dam ? rock(x, y, z, py) : "water");
                        }
                    } else if (m < 4) {
                        String b = rock(x, y, z, py);
                        double h = hash(x, y, z);
                        if (m < 1 && h < 0.012) {
                            b = h < 0.006 ? "sea_lantern" : "glowstone";
                        }
                        tput(x, y, z, b);
                        if (m < 1.2 && best >= 0) {
                            caveDecor(x, y, z, best, mArr, bottom, top);
                        }
                    } else if (edge || y > top - 3 || y < bottom + 3) {
                        String b;
                        if (y == top) {
                            b = top > 228 ? "snow_block" : "grass_block";
                        } else if (y > top - 3) {
                            b = top > 228 ? "snow_block" : "dirt";
                        } else {
                            b = hash(x, y, z) < 0.2 ? "andesite" : "stone";
                        }
                        tput(x, y, z, b);
                    }
                }
                // decor du sommet
                if (top <= 228) {
                    double h = hash(x, top, z);
                    if (h < 0.005 && d < W - 4) {
                        spruce(x, top + 1, z);
                    } else if (h < 0.1) {
                        tput(x, top + 1, z, "short_grass");
                    }
                }
            }
        }
    }

    String rock(int x, int y, int z, double py) {
        double v = nz.noise(x / 9.0, y / 9.0, z / 9.0);
        if (v > 0.55) {
            return hash(x, y, z) < 0.06 ? "budding_amethyst" : "amethyst_block";
        }
        if (v < -0.55) {
            return "calcite";
        }
        double v2 = nz.noise(x / 14.0 + 50, y / 7.0, z / 14.0);
        if (y < py - 6) {
            return v2 > 0.3 ? "tuff" : "deepslate";
        }
        return v2 > 0.4 ? "andesite" : v2 < -0.4 ? "tuff" : "stone";
    }

    /** Cristaux au sol, stalactites et cristaux au plafond. */
    void caveDecor(int x, int y, int z, int best, double[] mArr, int bottom, int top) {
        double sy = c.sy[best], r = tunnelR[best];
        double h = hash(x, y + 1000, z);
        if (y + 0.5 < sy - 3) {
            // sol : la case du dessus doit etre l'interieur, hors de l'eau
            int up = y + 1 - bottom;
            if (up >= 0 && up <= top - bottom && mArr[up] < 0 && y + 1.5 >= sy - r + 3) {
                if (h < 0.05) {
                    tput(x, y + 1, z, "amethyst_cluster[facing=up]");
                } else if (h < 0.08) {
                    tput(x, y + 1, z, "large_amethyst_bud[facing=up]");
                }
            }
        } else if (y + 0.5 > sy + 3) {
            int dn = y - 1 - bottom;
            if (dn < 0 || mArr[dn] >= 0) {
                return;
            }
            if (h < 0.06) {
                int len = 1 + (int) (hash(z, y, x) * 3);
                for (int i = 1; i <= len; i++) {
                    if (y - i - bottom < 0 || mArr[y - i - bottom] >= 0) {
                        len = i - 1;
                        break;
                    }
                }
                for (int i = 1; i <= len; i++) {
                    String th = i == len ? "tip" : i == len - 1 ? "frustum" : "base";
                    tput(x, y - i, z, "pointed_dripstone[vertical_direction=down,thickness=" + th + "]");
                }
            } else if (h < 0.085) {
                tput(x, y - 1, z, "amethyst_cluster[facing=down]");
            }
        }
    }

    void spruce(int x, int y, int z) {
        int h = 6 + (int) (hash(x, y, z) * 3);
        for (int i = 0; i < h; i++) {
            tput(x, y + i, z, "spruce_log");
        }
        for (int dy = 2; dy <= h; dy++) {
            double rad = Math.max(0.6, (h - dy) * 0.45);
            int ir = (int) Math.ceil(rad);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    if ((dx != 0 || dz != 0 || dy >= h) && dx * dx + dz * dz <= rad * rad + 0.5) {
                        tput(x + dx, y + dy, z + dz, "spruce_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    /** Eclairage invisible le long du trace dans la grotte. */
    void caveLights() {
        for (int i = 0; i < c.n; i += 4) {
            if (c.ss[i] < sEnter - 10 || c.ss[i] > sExit + 10) {
                continue;
            }
            int x = (int) Math.floor(c.sx[i]), y = (int) Math.floor(c.sy[i]) + 3, z = (int) Math.floor(c.sz[i]);
            if (w.getBlockAt(x, y, z).getType().isAir()) {
                put(x, y, z, "light[level=15]");
            }
        }
    }

    // ================================================================ structures communes

    String[] towerPalette() {
        return switch (c.theme) {
            case "canyon" -> new String[]{"smooth_sandstone", "red_sandstone", "orange_terracotta", "cut_sandstone"};
            case "archipel" -> new String[]{"smooth_quartz", "light_blue_terracotta", "white_terracotta", "quartz_bricks"};
            default -> new String[]{"polished_deepslate", "amethyst_block", "deepslate_bricks", "deepslate_tiles"};
        };
    }

    static final String[] SLOT_COLORS = {"red_concrete", "blue_concrete", "lime_concrete", "yellow_concrete", "orange_concrete", "purple_concrete",
        "cyan_concrete", "pink_concrete", "white_concrete", "black_concrete", "magenta_concrete", "light_blue_concrete"};

    /** Tour de lancement : salle d'attente vitree au sommet, porte devant, fut jusqu'a 60 blocs plus bas. */
    void tower() {
        String[] p = towerPalette();
        int ox = c.ox, oz = c.oz, ys = c.ys;
        // vide la salle
        for (int x = ox - 15; x <= ox + 2; x++) {
            for (int z = oz - 8; z <= oz + 8; z++) {
                for (int y = ys; y <= ys + 6; y++) {
                    put(x, y, z, "air");
                }
            }
        }
        // fut
        int bottom = ys - 60;
        for (int y = bottom; y <= ys - 2; y++) {
            boolean stripe = Math.floorMod(ys - y, 6) == 0;
            for (int x = ox - 12; x <= ox - 2; x++) {
                for (int z = oz - 5; z <= oz + 5; z++) {
                    boolean edge = x == ox - 12 || x == ox - 2 || z == oz - 5 || z == oz + 5;
                    boolean corner = (x == ox - 12 || x == ox - 2) && (z == oz - 5 || z == oz + 5);
                    if (edge) {
                        put(x, y, z, corner ? p[3] : stripe ? p[1] : p[0]);
                    }
                }
            }
        }
        // pied en pointe
        for (int i = 1; i <= 8; i++) {
            int rr = 6 - i * 6 / 8;
            for (int x = -rr; x <= rr; x++) {
                for (int z = -rr; z <= rr; z++) {
                    if (x * x + z * z <= rr * rr + 1) {
                        put(ox - 7 + x, bottom - i, oz + z, i % 2 == 0 ? p[2] : p[0]);
                    }
                }
            }
        }
        // plancher
        for (int x = ox - 14; x <= ox; x++) {
            for (int z = oz - 7; z <= oz + 7; z++) {
                boolean border = x == ox - 14 || x == ox || z == oz - 7 || z == oz + 7;
                put(x, ys - 1, z, border ? "gold_block" : ((x + z) & 1) == 0 ? p[0] : p[3]);
            }
        }
        // cases de depart
        for (int i = 0; i < 12; i++) {
            int[] sl = slot(i);
            put(sl[0], ys - 1, sl[1], SLOT_COLORS[i]);
        }
        // murs vitres, piliers, toit
        for (int y = ys; y <= ys + 3; y++) {
            for (int x = ox - 14; x <= ox; x++) {
                put(x, y, oz - 7, "glass");
                put(x, y, oz + 7, "glass");
            }
            for (int z = oz - 7; z <= oz + 7; z++) {
                put(ox - 14, y, z, "glass");
            }
            int[][] corners = {{ox - 14, oz - 7}, {ox - 14, oz + 7}, {ox, oz - 7}, {ox, oz + 7}};
            for (int[] k : corners) {
                put(k[0], y, k[1], p[3]);
            }
        }
        for (int x = ox - 14; x <= ox; x++) {
            for (int z = oz - 7; z <= oz + 7; z++) {
                boolean border = x == ox - 14 || x == ox || z == oz - 7 || z == oz + 7;
                put(x, ys + 4, z, border ? p[3] : "glass");
            }
        }
        // lanternes et drapeaux
        put(ox - 14, ys + 5, oz - 7, "lantern");
        put(ox - 14, ys + 5, oz + 7, "lantern");
        for (int[] f : new int[][]{{ox, oz - 7}, {ox, oz + 7}}) {
            put(f[0], ys + 5, f[1], "oak_fence");
            put(f[0], ys + 6, f[1], "oak_fence");
            put(f[0], ys + 7, f[1], "oak_fence");
            put(f[0], ys + 7, f[1] + (f[1] < oz ? -1 : 1), "white_wool");
            put(f[0], ys + 6, f[1] + (f[1] < oz ? -1 : 1), "black_wool");
        }
        for (int x = ox - 12; x <= ox - 4; x += 4) {
            put(x, ys + 3, oz, "light[level=15]");
        }
        gate(true);
    }

    /** Porte vitree devant les cases de depart. */
    void gate(boolean closed) {
        for (int y = c.ys; y <= c.ys + 3; y++) {
            for (int z = c.oz - 6; z <= c.oz + 6; z++) {
                put(c.ox + 1, y, z, closed ? "light_blue_stained_glass" : "air");
            }
        }
    }

    /** Case de depart i : {x, z} du bloc. 6 en premiere ligne, 6 derriere. */
    int[] slot(int i) {
        int row = i / 6, col = i % 6;
        return new int[]{row == 0 ? c.ox : c.ox - 2, c.oz - 5 + col * 2};
    }

    int terraceY() {
        return (int) Math.floor(c.endY) - 2;
    }

    void terrace() {
        int cx = (int) Math.floor(c.endX), cz = (int) Math.floor(c.endZ), ty = terraceY();
        String[] p = towerPalette();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 8.4) {
                    for (int y = 1; y <= 5; y++) {
                        put(cx + x, ty + y, cz + z, "air");
                    }
                }
                if (d <= 7.4) {
                    String b = d > 6.5 ? "gold_block" : ((Math.floorDiv(x, 2) + Math.floorDiv(z, 2)) & 1) == 0 ? "white_concrete" : "black_concrete";
                    put(cx + x, ty, cz + z, b);
                    int depth = (int) ((7.4 - d) * 1.4) + 1;
                    for (int y = 1; y <= depth; y++) {
                        put(cx + x, ty - y, cz + z, y == depth ? p[1] : p[0]);
                    }
                    if (d > 6.5 && ((x + z) & 3) == 0) {
                        put(cx + x, ty + 1, cz + z, "oak_fence");
                        put(cx + x, ty + 2, cz + z, "lantern");
                    }
                }
            }
        }
        put(cx, ty + 4, cz, "light[level=15]");
    }

    static final int SEG = 16;

    /** Anneaux en blocs, perpendiculaires a la ligne de vol. */
    void rings() {
        for (Course.Ring g : c.rings) {
            String solid, glass, light;
            if (g.finish) {
                solid = "white_concrete";
                glass = "black_concrete";
                light = "gold_block";
            } else if (g.boost) {
                solid = "lime_concrete";
                glass = "lime_stained_glass";
                light = "verdant_froglight";
            } else {
                switch (c.theme) {
                    case "canyon" -> {
                        solid = "light_blue_concrete";
                        glass = "light_blue_stained_glass";
                    }
                    case "archipel" -> {
                        solid = "orange_concrete";
                        glass = "yellow_stained_glass";
                    }
                    default -> {
                        solid = "yellow_concrete";
                        glass = "yellow_stained_glass";
                    }
                }
                light = "sea_lantern";
            }
            // base orthonormee du plan de l'anneau
            double nx = g.nx, ny = g.ny, nzv = g.nz;
            double rx = 0, ry = 1, rz = 0;
            if (Math.abs(ny) > 0.9) {
                rx = 1;
                ry = 0;
            }
            double ux = ry * nzv - rz * ny, uy = rz * nx - rx * nzv, uz = rx * ny - ry * nx;
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul;
            uy /= ul;
            uz /= ul;
            double vx = ny * uz - nzv * uy, vy = nzv * ux - nx * uz, vz = nx * uy - ny * ux;
            double outer = g.r + 1.3;
            int steps = (int) Math.ceil(2 * Math.PI * outer * 3);
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps;
                int seg = (int) (a / (2 * Math.PI) * SEG);
                String b = seg % 4 == 0 ? light : (seg & 1) == 0 ? solid : glass;
                double ca = Math.cos(a), sa = Math.sin(a);
                for (double rad = g.r + 0.6; rad <= outer + 0.01; rad += 0.35) {
                    for (double al = -0.35; al <= 0.36; al += 0.35) {
                        double x = g.x + ux * ca * rad + vx * sa * rad + nx * al;
                        double y = g.y + uy * ca * rad + vy * sa * rad + ny * al;
                        double z = g.z + uz * ca * rad + vz * sa * rad + nzv * al;
                        put((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), b);
                    }
                }
            }
        }
    }
}
