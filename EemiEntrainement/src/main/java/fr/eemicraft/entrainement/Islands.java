package fr.eemicraft.entrainement;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Dessin (en memoire, sans acces au monde) des iles d'entrainement.
 *
 * <p>Ile = cone inverse : herbe, 1-2 couches de terre, puis un degrade pierre / andesite / tuf / deepslate
 * pilote par un bruit 3D (grappes, jamais tire bloc par bloc). Garde-corps en barrieres d'epicea sur le
 * bord, sauf l'ouverture vers l'autre ile ; petite butte a l'arriere ; deux lampadaires ; herbes et fleurs
 * en touffes. Les pistes sont plates et lisibles (dalles de depart et d'arrivee contrastees).
 */
final class Islands {
    private Islands() {
    }

    static final String GRASS = "minecraft:grass_block[snowy=false]";
    static final String DIRT = "minecraft:dirt";
    static final String COARSE = "minecraft:coarse_dirt";
    static final String STONE = "minecraft:stone";
    static final String ANDESITE = "minecraft:andesite";
    static final String TUFF = "minecraft:tuff";
    static final String DEEP = "minecraft:cobbled_deepslate";
    static final String FENCE = "minecraft:spruce_fence";
    static final String LANTERN = "minecraft:lantern[hanging=false,waterlogged=false]";
    static final String PLANKS = "minecraft:spruce_planks";
    static final String BEAM = "minecraft:stripped_spruce_log[axis=z]";

    /** Positions -> etat de bloc. */
    static final class Rec {
        final Map<Long, String> blocks = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        void set(int x, int y, int z, String s) {
            blocks.put(key(x, y, z), s);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        String get(int x, int y, int z) {
            return blocks.get(key(x, y, z));
        }

        void remove(int x, int y, int z) {
            blocks.remove(key(x, y, z));
        }
    }

    /** Ce que le jeu doit savoir d'une piste dessinee. */
    static final class Layout {
        final Rec rec = new Rec();
        double spawnX, spawnY, spawnZ;
        float spawnYaw;
        double npcX, npcY, npcZ;
        float npcYaw;
        int finishX, finishY, finishZ;
        double finishR;
        /** Dessus le plus bas des iles (chute mesuree sous ce niveau). */
        int lowTop;
        int highTop;
        /** Bout du ponton hors de l'ile (cible de la perle difficile) : blocs x pontonX1..pontonX2, z pontonZ1..pontonZ2. */
        int pontonX1, pontonX2, pontonZ1, pontonZ2;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int kx(long k) {
        return (int) (k >> 38);
    }

    static int kz(long k) {
        return (int) ((k << 26) >> 38);
    }

    static int ky(long k) {
        return (int) (k & 0xFFF);
    }

    // ================================================================ pistes

    /** Piste de bridge : ile de depart en (ox, y, 0), ile d'arrivee a {@code dist} blocs de vide. */
    static Layout bridge(Noise n, int ox, int y, int dist, String style, double incline) {
        Layout l = new Layout();
        double r = 4.0;
        double ux = 0, uz = 1;
        int rise = 0;
        if ("diagonale".equals(style)) {
            ux = Math.sqrt(0.5);
            uz = Math.sqrt(0.5);
        } else if ("montee".equals(style)) {
            rise = (int) Math.round(dist * incline);
        }
        double c = 2 * r + 1 + dist;
        int fx = ox + (int) Math.round(ux * c);
        int fz = (int) Math.round(uz * c);
        int sx = ox + (int) Math.round(ux * (r - 1.5));
        int sz = (int) Math.round(uz * (r - 1.5));
        island(l.rec, n, ox, ox, y, 0, r, ux, uz, sx, sz, "minecraft:polished_andesite", "minecraft:smooth_stone");
        island(l.rec, n, ox, fx, y + rise, fz, r, -ux, -uz, fx, fz, "minecraft:gold_block", "minecraft:yellow_concrete");
        l.rec.set(fx, y + rise + 1, fz, "minecraft:light_weighted_pressure_plate[power=0]");
        fences(l.rec);
        l.spawnX = sx + 0.5;
        l.spawnY = y + 1;
        l.spawnZ = sz + 0.5;
        l.spawnYaw = (float) Math.toDegrees(Math.atan2(-ux, uz));
        l.finishX = fx;
        l.finishY = y + rise;
        l.finishZ = fz;
        l.finishR = r + 0.8;
        l.lowTop = y;
        l.highTop = y + rise;
        return l;
    }

    /** Piste de knockback : ile en (ox, y, 0) prolongee d'un ponton vers +z ; le joueur au bout, le PNJ 2 blocs devant. */
    static Layout knockback(Noise n, int ox, int y) {
        Layout l = new Layout();
        double r = 5.0;
        int end = (int) r + 3;
        island(l.rec, n, ox, ox, y, 0, r, 0, 1, Integer.MIN_VALUE, 0, null, null);
        // ponton de 3 de large, plat, sans garde-corps (c'est le principe)
        for (int z = 2; z <= end; z++) {
            for (int dx = -1; dx <= 1; dx++) {
                l.rec.set(ox + dx, y, z, PLANKS);
                l.rec.remove(ox + dx, y + 1, z);
                l.rec.remove(ox + dx, y + 2, z);
                if (dx != 0 && l.rec.get(ox + dx, y - 1, z) == null) {
                    l.rec.set(ox + dx, y - 1, z, BEAM);
                }
            }
        }
        // marque au sol de la place du joueur et de celle de l'entraineur
        l.rec.set(ox, y, end, "minecraft:red_concrete");
        l.rec.set(ox, y, end - 2, "minecraft:polished_andesite");
        fences(l.rec);
        l.spawnX = ox + 0.5;
        l.spawnY = y + 1;
        l.spawnZ = end + 0.5;
        l.spawnYaw = 180f;
        l.npcX = ox + 0.5;
        l.npcY = y + 1;
        l.npcZ = end - 2 + 0.5;
        l.npcYaw = 0f;
        l.pontonX1 = ox - 1;
        l.pontonX2 = ox + 1;
        l.pontonZ1 = (int) r + 1; // hors de l'ile (rayon 5 + bruit 0,7) : seule la partie du ponton au-dessus du vide
        l.pontonZ2 = end;
        l.lowTop = y;
        l.highTop = y;
        return l;
    }

    /**
     * Piste de MLG : grande ile d'atterrissage en (ox, y, 3) et tour 3 x 3 de {@code h} blocs en (ox, -1..1).
     * Le joueur part du sommet, face au sud ; en descendant du bord sud il tombe le long de la face de la tour
     * (z = 1) : une echelle s'y accroche, l'eau et la toile se posent sur l'herbe en dessous.
     */
    static Layout mlg(Noise n, int ox, int y, int h) {
        Layout l = new Layout();
        island(l.rec, n, ox, ox, y, 3, 7.0, 0, -1, Integer.MIN_VALUE, 0, null, null);
        // zone d'arrivee sous le bord : herbe nue, rien de pose dessus
        for (int dx = -2; dx <= 2; dx++) {
            for (int z = 2; z <= 5; z++) {
                l.rec.remove(ox + dx, y + 1, z);
                l.rec.set(ox + dx, y, z, GRASS);
            }
        }
        int top = y + h;
        for (int dx = -1; dx <= 1; dx++) {
            for (int z = -1; z <= 1; z++) {
                for (int yy = y + 1; yy <= top; yy++) {
                    String m;
                    int k = yy - y;
                    if (yy == top) {
                        m = "minecraft:polished_andesite";
                    } else if (k % 6 == 0) {
                        m = "minecraft:chiseled_stone_bricks";
                    } else if (k <= 4 && n.noise3((ox + dx - ox) / 2.5, yy / 2.5, z / 2.5) > 0.1) {
                        m = "minecraft:mossy_stone_bricks";
                    } else if (n.noise3((dx) / 3.0 + 40, yy / 3.0, z / 3.0) > 0.45) {
                        m = "minecraft:cracked_stone_bricks";
                    } else {
                        m = "minecraft:stone_bricks";
                    }
                    l.rec.set(ox + dx, yy, z, m);
                }
            }
        }
        // garde-corps au sommet sauf le bord sud (celui du saut), lanterne sur un poteau d'angle
        int[][] f = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}};
        for (int[] c : f) {
            l.rec.set(ox + c[0], top + 1, c[1], FENCE);
        }
        l.rec.set(ox - 1, top + 2, -1, LANTERN);
        // repere du bord : dalle rouge au milieu du bord sud
        l.rec.set(ox, top, 1, "minecraft:red_concrete");
        fences(l.rec);
        l.spawnX = ox + 0.5;
        l.spawnY = top + 1;
        l.spawnZ = 0.5;
        l.spawnYaw = 0f;
        l.lowTop = y;
        l.highTop = top;
        return l;
    }

    // ================================================================ ile

    /**
     * Ile en cone inverse.
     *
     * @param ux,uz direction de l'ouverture (vers l'autre ile ou le ponton)
     * @param px,pz centre de la dalle 3x3 (Integer.MIN_VALUE = pas de dalle)
     */
    static void island(Rec r, Noise n0, int nx, int cx, int top, int cz, double rad, double ux, double uz,
                       int px, int pz, String padCenter, String padRing) {
        // bruit lu en coordonnees locales a la piste (nx = x d'origine de la piste) : memes iles sur toutes les pistes
        Noise n = n0;
        int m = (int) Math.ceil(rad) + 2;
        Set<Long> cells = new HashSet<>();
        Map<Long, Integer> heights = new LinkedHashMap<>();
        Map<Long, Double> norm = new LinkedHashMap<>();
        for (int dx = -m; dx <= m; dx++) {
            for (int dz = -m; dz <= m; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                double rr = rad + 0.7 * n.noise2((x - nx) / 4.5, z / 4.5);
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rr && !(dx == 0 && dz == 0)) {
                    continue;
                }
                long k = key(x, 0, z);
                cells.add(k);
                double along = dx * ux + dz * uz;
                int h = top;
                boolean pad = px != Integer.MIN_VALUE && Math.abs(x - px) <= 1 && Math.abs(z - pz) <= 1;
                if (!pad && along < -1.5 && d < rr - 1.2 && n.noise2((x - nx) / 3.5 + 50, z / 3.5) > 0.25) {
                    h = top + 1; // petite butte a l'arriere
                }
                heights.put(k, h);
                norm.put(k, Math.max(0, 1 - d / Math.max(1, rr)));
            }
        }
        for (long k : cells) {
            int x = kx(k);
            int z = kz(k);
            int h = heights.get(k);
            double t0 = norm.get(k);
            int depth = 2 + (int) Math.round(rad * 1.6 * Math.pow(t0, 0.8)) + (int) Math.round(1.2 * (n.noise2((x - nx) / 2.5 + 9, z / 2.5) + 1));
            int dirt = n.noise2((x - nx) / 5.0 - 20, z / 5.0) > 0 ? 2 : 1;
            for (int y = h; y >= top - depth; y--) {
                int layer = h - y;
                String s;
                if (layer == 0) {
                    s = GRASS;
                } else if (layer <= dirt) {
                    s = n.noise3((x - nx) / 5.0, y / 5.0, z / 5.0) > 0.45 ? COARSE : DIRT;
                } else {
                    double t = (top - y) / (double) Math.max(1, depth) + 0.25 * n.noise3((x - nx) / 6.0, y / 6.0, z / 6.0);
                    s = t < 0.45 ? STONE : t < 0.7 ? ANDESITE : t < 0.9 ? TUFF : DEEP;
                }
                r.set(x, y, z, s);
            }
        }
        // dalle de depart / d'arrivee
        if (px != Integer.MIN_VALUE) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    r.set(px + dx, top, pz + dz, dx == 0 && dz == 0 ? padCenter : padRing);
                    if (r.get(px + dx, top - 1, pz + dz) == null) {
                        r.set(px + dx, top - 1, pz + dz, STONE);
                    }
                }
            }
        }
        // garde-corps sur le bord, sauf l'ouverture
        for (long k : cells) {
            int x = kx(k);
            int z = kz(k);
            boolean ring = !cells.contains(key(x + 1, 0, z)) || !cells.contains(key(x - 1, 0, z))
                    || !cells.contains(key(x, 0, z + 1)) || !cells.contains(key(x, 0, z - 1));
            if (!ring) {
                continue;
            }
            int dx = x - cx;
            int dz = z - cz;
            double along = dx * ux + dz * uz;
            double perp = Math.abs(dx * uz - dz * ux);
            if (along > 0 && perp <= 1.7) {
                continue;
            }
            if (px != Integer.MIN_VALUE && Math.abs(x - px) <= 1 && Math.abs(z - pz) <= 1) {
                continue;
            }
            r.set(x, heights.get(k) + 1, z, FENCE);
        }
        // deux lampadaires a l'arriere
        for (int side = -1; side <= 1; side += 2) {
            double bx = -ux * (rad - 1.6) + side * 2 * uz;
            double bz = -uz * (rad - 1.6) - side * 2 * ux;
            int x = cx + (int) Math.round(bx);
            int z = cz + (int) Math.round(bz);
            Integer h = heights.get(key(x, 0, z));
            if (h == null) {
                continue;
            }
            r.set(x, h + 1, z, FENCE);
            r.set(x, h + 2, z, FENCE);
            r.set(x, h + 3, z, LANTERN);
        }
        // herbes et fleurs en touffes (pas sur la dalle, pas sur l'axe de l'ouverture)
        String[] flowers = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy"};
        for (long k : cells) {
            int x = kx(k);
            int z = kz(k);
            int h = heights.get(k);
            if (r.get(x, h + 1, z) != null || !GRASS.equals(r.get(x, h, z))) {
                continue;
            }
            int dx = x - cx;
            int dz = z - cz;
            if (Math.abs(dx * uz - dz * ux) <= 1.7 && dx * ux + dz * uz > -1) {
                continue; // axe de depart degage
            }
            if (n.uniform2((x - nx) / 2.7, z / 2.7) < 0.12) {
                r.set(x, h + 1, z, "minecraft:short_grass");
            } else if (n.uniform2((x - nx) / 2.1 + 31, z / 2.1) < 0.05) {
                int f = (int) Math.floor((n.noise2((x - nx) / 9.0 + 7, z / 9.0) + 1) / 2 * flowers.length);
                r.set(x, h + 1, z, flowers[Math.max(0, Math.min(flowers.length - 1, f))]);
            }
        }
    }

    /** Raccords des barrieres (entre barrieres et vers les lanternes posees dessus : rien). */
    static void fences(Rec r) {
        Map<Long, String> upd = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : r.blocks.entrySet()) {
            if (!FENCE.equals(e.getValue())) {
                continue;
            }
            long k = e.getKey();
            int x = kx(k);
            int y = ky(k);
            int z = kz(k);
            upd.put(k, FENCE + "[east=" + isFence(r, x + 1, y, z) + ",north=" + isFence(r, x, y, z - 1)
                    + ",south=" + isFence(r, x, y, z + 1) + ",waterlogged=false,west=" + isFence(r, x - 1, y, z) + "]");
        }
        r.blocks.putAll(upd);
    }

    private static boolean isFence(Rec r, int x, int y, int z) {
        String s = r.get(x, y, z);
        return s != null && s.startsWith(FENCE);
    }
}
