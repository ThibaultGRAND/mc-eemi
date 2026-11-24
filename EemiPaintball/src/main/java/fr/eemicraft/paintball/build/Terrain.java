package fr.eemicraft.paintball.build;

import java.util.ArrayDeque;

/**
 * Terrain organique (règles F du skill) sur un rectangle : un seul champ de hauteur continu (FBM simplex + domain
 * warp + crêtes optionnelles), creusements et plateformes raccordés en douceur, lissage 3×3, melt/fill, puis pose
 * avec une surface pilotée par masques (pente → roche, près de l'eau → plage, taches nues dans l'herbe), un
 * substrat de 2+ couches d'épaisseur variable et la roche dessous. Ordre conseillé :
 *
 * <pre>
 * Terrain t = new Terrain(seed, x1, z1, x2, z2).fbm(64, 5, 40, 4, 12)
 *         .dig(cx, cz, 7, 4).smooth(2).pad(bx1, bz1, bx2, bz2, 64, 5).meltFill().water(62);
 * t.place(sink, new Terrain.Surface(), 40);
 * t.vegetation(sink, seed, 0.35);
 * </pre>
 * Hauteur h(x,z) = Y du bloc de surface (on se tient en h+1).
 */
public final class Terrain {
    public final int x1, z1, x2, z2, w, l;
    private final double[][] h;
    private final boolean[][] locked;
    private final Noise n;
    private int water = Integer.MIN_VALUE;
    private int[][] surf; // 0 herbe, 1 nu, 2 roche, 3 plage, 4 fond d'eau

    public Terrain(long seed, int x1, int z1, int x2, int z2) {
        this.x1 = Math.min(x1, x2); this.x2 = Math.max(x1, x2);
        this.z1 = Math.min(z1, z2); this.z2 = Math.max(z1, z2);
        this.w = this.x2 - this.x1 + 1; this.l = this.z2 - this.z1 + 1;
        this.h = new double[w][l];
        this.locked = new boolean[w][l];
        this.n = new Noise(seed);
    }

    /** Ajoute base + amp·FBM (période en blocs, 4-5 octaves), coordonnées déformées par un warp de ±warp blocs. */
    public Terrain fbm(double base, double amp, double period, int octaves, double warp) {
        Noise wn = n.derive(1);
        for (int i = 0; i < w; i++)
            for (int k = 0; k < l; k++) {
                double x = x1 + i, z = z1 + k;
                if (warp > 0) { double[] p = wn.warp2(x, z, warp, period * 1.5); x = p[0]; z = p[1]; }
                h[i][k] += base + amp * n.fbm2(x / period, z / period, octaves, 0.5, 2.0);
            }
        return this;
    }

    /** Ajoute des crêtes (montagnes) : amp·ridge, période en blocs. */
    public Terrain ridges(double amp, double period) {
        Noise rn = n.derive(2);
        for (int i = 0; i < w; i++)
            for (int k = 0; k < l; k++)
                h[i][k] += amp * rn.ridge2((x1 + i) / period, (z1 + k) / period, 4, 0.5, 2.0);
        return this;
    }

    /** Creuse une cuvette (étang, fosse) au bord bosselé : profondeur max au centre, raccord en cosinus. */
    public Terrain dig(int cx, int cz, double radius, double depth) {
        Noise dn = n.derive(3);
        int R = (int) Math.ceil(radius * 1.4);
        for (int i = Math.max(0, cx - R - x1); i <= Math.min(w - 1, cx + R - x1); i++)
            for (int k = Math.max(0, cz - R - z1); k <= Math.min(l - 1, cz + R - z1); k++) {
                double dx = x1 + i - cx, dz = z1 + k - cz;
                double r = radius * (0.8 + 0.4 * dn.uniform2((x1 + i) / 6.0, (z1 + k) / 6.0));
                double d = Math.sqrt(dx * dx + dz * dz) / r;
                if (d < 1) h[i][k] -= depth * 0.5 * (1 + Math.cos(Math.PI * d));
            }
        return this;
    }

    /**
     * Plateforme à hauteur fixe pour un bâtiment / une zone de jeu, raccordée au terrain sur {@code blend} blocs
     * (smoothstep). Les cases de la plateforme sont verrouillées : lissage et melt/fill ne les touchent plus.
     */
    public Terrain pad(int px1, int pz1, int px2, int pz2, int y, int blend) {
        int ax = Math.min(px1, px2), bx = Math.max(px1, px2), az = Math.min(pz1, pz2), bz = Math.max(pz1, pz2);
        for (int i = 0; i < w; i++)
            for (int k = 0; k < l; k++) {
                int x = x1 + i, z = z1 + k;
                double dx = Math.max(0, Math.max(ax - x, x - bx)), dz = Math.max(0, Math.max(az - z, z - bz));
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d == 0) { h[i][k] = y; locked[i][k] = true; }
                else if (d < blend && !locked[i][k]) {
                    double t = d / blend;
                    t = t * t * (3 - 2 * t);
                    h[i][k] = y + (h[i][k] - y) * t;
                }
            }
        return this;
    }

    /** Lissage : moyenne 3×3 en {@code passes} passes (1-3). */
    public Terrain smooth(int passes) {
        for (int p = 0; p < passes; p++) {
            double[][] o = new double[w][l];
            for (int i = 0; i < w; i++)
                for (int k = 0; k < l; k++) {
                    if (locked[i][k]) { o[i][k] = h[i][k]; continue; }
                    double s = 0; int c = 0;
                    for (int a = -1; a <= 1; a++)
                        for (int b = -1; b <= 1; b++) {
                            int ii = i + a, kk = k + b;
                            if (ii < 0 || kk < 0 || ii >= w || kk >= l) continue;
                            s += h[ii][kk]; c++;
                        }
                    o[i][k] = s / c;
                }
            for (int i = 0; i < w; i++) System.arraycopy(o[i], 0, h[i], 0, l);
        }
        return this;
    }

    /**
     * Érosion sur la grille entière : melt = une colonne plus haute que ses 4 voisines descend d'un bloc ;
     * fill = un trou plus bas que ses 4 voisines remonte d'un bloc (pas de pics ni de puits d'une case).
     */
    public Terrain meltFill() {
        int[][] hi = heights();
        for (int i = 1; i < w - 1; i++)
            for (int k = 1; k < l - 1; k++) {
                if (locked[i][k]) continue;
                int c = hi[i][k];
                int a = hi[i - 1][k], b = hi[i + 1][k], d = hi[i][k - 1], e = hi[i][k + 1];
                if (c > a && c > b && c > d && c > e) h[i][k] = c - 1;
                else if (c < a && c < b && c < d && c < e) h[i][k] = c + 1;
            }
        return this;
    }

    /** Niveau de l'eau (Y de la surface de l'eau) ; les colonnes plus basses sont noyées. */
    public Terrain water(int level) { this.water = level; return this; }

    public int height(int x, int z) {
        int i = Math.max(0, Math.min(w - 1, x - x1)), k = Math.max(0, Math.min(l - 1, z - z1));
        return (int) Math.round(h[i][k]);
    }

    public void setHeight(int x, int z, double y) {
        if (x < x1 || x > x2 || z < z1 || z > z2) return;
        h[x - x1][z - z1] = y;
    }

    public int[][] heights() {
        int[][] r = new int[w][l];
        for (int i = 0; i < w; i++) for (int k = 0; k < l; k++) r[i][k] = (int) Math.round(h[i][k]);
        return r;
    }

    /** Plus grand écart de hauteur avec les 4 voisins. */
    public int slope(int x, int z) {
        int c = height(x, z), m = 0;
        for (Dir d : Dir.values()) {
            int xx = x + d.dx, zz = z + d.dz;
            if (xx < x1 || xx > x2 || zz < z1 || zz > z2) continue;
            m = Math.max(m, Math.abs(height(xx, zz) - c));
        }
        return m;
    }

    /** Matériaux et seuils de la surface. */
    public static final class Surface {
        public Mat grass = Mat.of("minecraft:grass_block[snowy=false]");
        /** Taches nues dans l'herbe (5-15 %). */
        public Mat bare = Palette.of(11, "minecraft:coarse_dirt", 60, "minecraft:podzol[snowy=false]", 25, "minecraft:rooted_dirt", 15).period(3);
        public Mat rock = Palette.of(12, "minecraft:stone", 60, "minecraft:andesite", 25, "minecraft:tuff", 15).period(5);
        public Mat beach = Palette.of(13, "minecraft:sand", 75, "minecraft:gravel", 25).period(4);
        public Mat bed = Palette.of(14, "minecraft:gravel", 50, "minecraft:clay", 25, "minecraft:sand", 25).period(3);
        public Mat sub = Mat.of("minecraft:dirt");
        /** Roche sous le substrat : invisible en jeu, un seul bloc (des variantes ici ne feraient que du mélange sur les bords d'un .schem). */
        public Mat deep = Mat.of("minecraft:stone");
        /** Écart avec un voisin à partir duquel la surface devient roche (2 = falaise de 2+). */
        public int rockSlope = 2;
        /** Distance à l'eau (en colonnes) où l'on pose la plage. */
        public int beachDist = 3;
        public double bareFrac = 0.10;
        public int subMin = 2, subMax = 4;
    }

    /** Pose le terrain dans le Sink (de bottomY à la surface) et l'eau. */
    public void place(Sink s, Surface sf, int bottomY) {
        int[][] hi = heights();
        int[][] dw = waterDistance(hi, sf.beachDist + 1);
        surf = new int[w][l];
        Noise bn = n.derive(4), tn = n.derive(5);
        for (int i = 0; i < w; i++)
            for (int k = 0; k < l; k++) {
                int x = x1 + i, z = z1 + k, top = hi[i][k];
                int sl = slope(x, z);
                int type;
                if (water != Integer.MIN_VALUE && top < water) type = 4;
                else if (sl >= sf.rockSlope) type = 2;
                else if (water != Integer.MIN_VALUE && top <= water + 1 && dw[i][k] <= sf.beachDist) type = 3;
                else if (bn.fbm2(x / 11.0, z / 11.0, 2, 0.5, 2.0) < bareThreshold(sf.bareFrac)) type = 1;
                else type = 0;
                surf[i][k] = type;
                int thick = sf.subMin + (int) Math.floor(tn.uniform2(x / 9.0, z / 9.0) * (sf.subMax - sf.subMin + 1));
                for (int y = bottomY; y <= top; y++) {
                    int depth = top - y;
                    Mat m;
                    if (type == 2) m = sf.rock;
                    else if (depth == 0) m = type == 0 ? sf.grass : type == 1 ? sf.bare : type == 3 ? sf.beach : sf.bed;
                    else if (depth <= thick) m = (type == 3 || type == 4) ? sf.beach : sf.sub;
                    else m = sf.deep;
                    s.set(x, y, z, m);
                }
                if (type == 4) for (int y = top + 1; y <= water; y++) s.set(x, y, z, B.WATER);
            }
    }

    /** Seuil du bruit FBM tel qu'environ frac des colonnes soient en dessous (taches de 3 à 8 blocs). */
    private static double bareThreshold(double frac) { return -0.38 + (frac - 0.10) * 2.0; }

    private int[][] waterDistance(int[][] hi, int max) {
        int[][] d = new int[w][l];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int i = 0; i < w; i++)
            for (int k = 0; k < l; k++) {
                if (water != Integer.MIN_VALUE && hi[i][k] < water) { d[i][k] = 0; q.add(new int[]{i, k}); }
                else d[i][k] = Integer.MAX_VALUE;
            }
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (d[c[0]][c[1]] >= max) continue;
            for (Dir dd : Dir.values()) {
                int a = c[0] + dd.dx, b = c[1] + dd.dz;
                if (a < 0 || b < 0 || a >= w || b >= l || d[a][b] <= d[c[0]][c[1]] + 1) continue;
                d[a][b] = d[c[0]][c[1]] + 1;
                q.add(new int[]{a, b});
            }
        }
        return d;
    }

    /** Type de surface posé (après place) : 0 herbe, 1 nu, 2 roche, 3 plage, 4 fond d'eau. */
    public int surfaceType(int x, int z) {
        if (surf == null || x < x1 || x > x2 || z < z1 || z > z2) return -1;
        return surf[x - x1][z - z1];
    }

    /**
     * Végétation basse en GRAPPES (règle F8) sur l'herbe uniquement : taches d'une même espèce (bruit de période
     * ~10), densité {@code density} dans les grappes, plus dense près de l'eau, rien sur les pentes de roche.
     * Ne pose que dans l'air (après les bâtiments, elle ne recouvre rien).
     */
    public void vegetation(Sink s, long seed, double density) {
        if (surf == null) throw new IllegalStateException("appeler place() avant vegetation()");
        Noise cn = n.derive(seed * 7 + 6), sp = n.derive(seed * 7 + 7);
        String[] plants = {"minecraft:short_grass", "minecraft:short_grass", "minecraft:fern", "minecraft:dandelion",
                "minecraft:poppy", "minecraft:short_grass", "minecraft:oxeye_daisy", "minecraft:cornflower",
                "minecraft:short_grass", "minecraft:azure_bluet"};
        for (int i = 0; i < w; i++)
            for (int k = 0; k < l; k++) {
                if (surf[i][k] != 0) continue;
                int x = x1 + i, z = z1 + k, y = (int) Math.round(h[i][k]) + 1;
                if (!s.isAir(x, y, z) || !B.name(s.get(x, y - 1, z)).equals("grass_block")) continue;
                double c = cn.uniform2(x / 7.0, z / 7.0);
                if (c < 0.62) continue; // hors grappe
                if (Noise.hash(seed, x, y, z) > density * Math.min(1, (c - 0.55) * 3)) continue;
                double u = sp.uniform2(x / 10.0, z / 10.0);
                String p = plants[Math.min(plants.length - 1, (int) (u * plants.length))];
                if (p.endsWith("short_grass") && Noise.hash(seed + 1, x, y, z) < 0.18 && s.isAir(x, y + 1, z)) {
                    s.set(x, y, z, "minecraft:tall_grass[half=lower]");
                    s.set(x, y + 1, z, "minecraft:tall_grass[half=upper]");
                } else s.set(x, y, z, p);
            }
    }
}
