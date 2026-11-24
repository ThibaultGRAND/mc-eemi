package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.Connect;
import fr.eemicraft.paintball.build.Dir;
import fr.eemicraft.paintball.build.Facade;
import fr.eemicraft.paintball.build.Noise;
import fr.eemicraft.paintball.build.Roof;
import fr.eemicraft.paintball.build.RoofStyle;
import fr.eemicraft.paintball.build.Sink;
import fr.eemicraft.paintball.build.Stairs;
import fr.eemicraft.paintball.build.Style;

/**
 * Socle commun des petites maps a theme (1.4.0) : meme contrat que {@link Speedball} (zone de peinture |x| &lt;= 35,
 * |z| &lt;= 23, FY..FY+12 ; apparitions sur la dalle plate verrouillee x = +-29, |z| &lt;= 5 ; cabane d'equipe en
 * |x| 31..37, |z| &lt;= 8 ; filet en |x| = 35 / |z| = 23, barrieres invisibles derriere ; gradins en |z| 26..31 avec
 * le point spectateurs en 0.5, FY + 6.5, 29.5 ; le sol ne descend jamais sous FY). Sans Bukkit (Sink).
 *
 * <p>La sous-classe fournit le relief, les sols, le decor et les couverts. Le plan de jeu est en miroir x / -x,
 * le decor differe d'un cote a l'autre (bruit propre a chaque cote, couleurs, objets). La salle d'attente n'est pas
 * construite ici (voir {@link Speedball#waitingOnly()}).
 */
abstract class Field {
    static final int FY = Speedball.FY;
    static final int NX = Speedball.NX;
    static final int NZ = Speedball.NZ;
    static final int BX = Speedball.BX;
    static final int BZ = Speedball.BZ;
    static final int GX0 = -44, GZ0 = -34, GW = 89, GL = 69;

    final Sink s;
    final Noise noise;
    final int[][] hm = new int[GW][GL];
    final boolean[][] lock = new boolean[GW][GL];
    final boolean[][] path = new boolean[GW][GL];
    final boolean[][] bare = new boolean[GW][GL];

    Field(Sink s, long seed) {
        this.s = s;
        this.noise = new Noise(seed);
    }

    // ------------------------------------------------------------------ gabarit

    void build() {
        // zone videe = les chunks forces du terrain (-3..2) : les arbres du speedball debordent jusqu'en x 47
        fill(-46, FY - 5, -36, 46, FY + 22, 36, "minecraft:air");
        fill(-48, FY, -48, -47, FY + 16, 47, "minecraft:air");
        fill(47, FY, -48, 47, FY + 16, 47, "minecraft:air");
        fill(-46, FY, -48, 46, FY + 16, -37, "minecraft:air");
        fill(-46, FY, 37, 46, FY + 16, 47, "minecraft:air");
        heights();
        paths(pathNorth(), pathSouth());
        ground();
        edges();
        for (int side : new int[]{-1, 1}) {
            base(side);
            obstacles(side);
            lamps(side);
        }
        center();
        stands(1);
        stands(-1);
        for (int[] t : new int[][]{{-39, -28}, {39, -28}, {-39, 28}, {39, 28}}) {
            floodlight(t[0], t[1]);
        }
        outside();
        vegetation();
        Stairs.fix(s, -44, FY - 1, -34, 44, FY + 18, 34);
        Connect.fix(s, -44, FY - 1, -34, 44, FY + 18, 34);
    }

    /** Hauteur brute (au-dessus de FY) dans le terrain |x| &lt;= 34, |z| &lt;= 22, hors talus de bord. */
    abstract double raw(int x, int z);

    /** Sols plats verrouilles propres a la map (la dalle de base est deja posee). */
    abstract void pads();

    abstract int[][] pathNorth();

    abstract int[][] pathSouth();

    /** Bloc de surface du terrain jouable (hors dalle de base, cabane et chemins). */
    abstract String fieldTop(int x, int z, int h);

    abstract String pathTop(int x, int z, double t2);

    /** Interieur de la dalle de base (la bordure est aux couleurs de l'equipe). */
    abstract String baseFloor(int x, int z, double t2);

    abstract String cabinFloor(int x, int z);

    /** Sol hors du terrain ; walk = allee de gravier entre le filet et les gradins. */
    abstract String outsideTop(int x, int z, double t2, boolean walk);

    /** Remblai sous la surface. */
    String subsoil(int x, int y, int z) {
        return "minecraft:dirt";
    }

    /** Bloc de la haie / palissade de bordure. */
    abstract String edgeMat(int x, int y, int z);

    /** Couronnement eventuel de la bordure (au-dessus du dernier bloc), null = rien. */
    String edgeCap(int x, int y, int z) {
        return null;
    }

    /** Apres chaque colonne de bordure (poteaux en saillie, etc.). */
    void edgeExtra(int x, int z, int h, int top) {
    }

    abstract void base(int side);

    abstract void obstacles(int side);

    abstract void center();

    abstract void lamppost(int x, int z, int zs);

    abstract void outside();

    abstract void vegetation();

    /** Gradins : marche, allee, dos, piliers, toit en dalles. */
    abstract String[] standMats();

    String netPost() {
        return "minecraft:dark_oak_log";
    }

    String netCap() {
        return "minecraft:dark_oak_slab[type=bottom]";
    }

    String netPane() {
        return "minecraft:black_stained_glass_pane";
    }

    String netTop() {
        return "minecraft:dark_oak_fence";
    }

    void floodlight(int tx, int tz) {
        fill(tx, FY, tz, tx, FY + 14, tz, "minecraft:polished_andesite");
        fill(tx - 1, FY + 15, tz - 1, tx + 1, FY + 16, tz + 1, "minecraft:sea_lantern");
        fill(tx - 1, FY + 17, tz - 1, tx + 1, FY + 17, tz + 1, "minecraft:polished_andesite_slab[type=bottom]");
        set(tx, FY + 1, tz, "minecraft:chiseled_stone_bricks");
    }

    // ------------------------------------------------------------------ outils

    void set(int x, int y, int z, String b) {
        s.set(x, y, z, b);
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    s.set(x, y, z, b);
                }
            }
        }
    }

    static boolean inGrid(int x, int z) {
        return x >= GX0 && x < GX0 + GW && z >= GZ0 && z < GZ0 + GL;
    }

    /** Y du bloc de surface (on se tient en g + 1). */
    int g(int x, int z) {
        return inGrid(x, z) ? hm[x - GX0][z - GZ0] : FY;
    }

    static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    static double hash(long salt, int x, int z) {
        return Noise.hash(salt, x, 0, z);
    }

    /** Bosse lisse : hauteur h au centre, rayon rx/rz, flanc de largeur relative 'soft'. */
    static double bump(int ax, int z, double cx, double cz, double rx, double rz, double h) {
        double d = Math.hypot((ax - cx) / rx, (z - cz) / rz);
        return h * clamp((1.45 - d) / 0.95, 0, 1);
    }

    /** Orientation est/ouest retournee pour le cote ouest (miroir x). */
    static String fx(String facing, int side) {
        if (side > 0) {
            return facing;
        }
        return switch (facing) {
            case "east" -> "west";
            case "west" -> "east";
            default -> facing;
        };
    }

    void pad(int x1, int z1, int x2, int z2, int y, boolean mirror) {
        for (int side : mirror ? new int[]{1, -1} : new int[]{1}) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    int X = side * x;
                    if (inGrid(X, z)) {
                        hm[X - GX0][z - GZ0] = y;
                        lock[X - GX0][z - GZ0] = true;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ relief

    void heights() {
        Noise a = noise.derive(1);
        double[][] v = new double[GW][GL];
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                int ax = Math.abs(x);
                int az = Math.abs(z);
                double val = 0;
                if (ax <= 34 && az <= 22) {
                    val = Math.max(0, raw(x, z));
                    double foot = 1.8 * a.noise2(x / 6.0 + 50, z / 6.0 - 20);
                    if (az >= 17) {
                        val = Math.max(val, clamp(az - 18.6 + foot, 0, 3.2));
                    }
                    if (ax >= 30 && az >= 9) {
                        val = Math.max(val, clamp(ax - 31.6 + foot, 0, 3.2));
                    }
                }
                v[x - GX0][z - GZ0] = val;
            }
        }
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                double sum = 0;
                int n = 0;
                for (int i = -1; i <= 1; i++) {
                    for (int k = -1; k <= 1; k++) {
                        if (inGrid(x + i, z + k)) {
                            sum += v[x + i - GX0][z + k - GZ0];
                            n++;
                        }
                    }
                }
                double sm = 0.5 * v[x - GX0][z - GZ0] + 0.5 * sum / n;
                hm[x - GX0][z - GZ0] = FY + (int) Math.round(sm);
            }
        }
        pad(25, -9, 37, 9, FY, true);
        pad(22, -10, 24, 10, FY, true);
        pad(-4, -6, 4, 6, FY, false);
        pads();
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                if (Math.abs(x) > NX || Math.abs(z) >= NZ) {
                    hm[x - GX0][z - GZ0] = FY;
                    lock[x - GX0][z - GZ0] = true;
                }
            }
        }
        for (int pass = 0; pass < 4; pass++) {
            for (int x = GX0 + 1; x < GX0 + GW - 1; x++) {
                for (int z = GZ0 + 1; z < GZ0 + GL - 1; z++) {
                    if (lock[x - GX0][z - GZ0] || Math.abs(z) >= 20 || Math.abs(x) >= 33) {
                        continue;
                    }
                    int h = hm[x - GX0][z - GZ0];
                    int lo = Math.min(Math.min(hm[x + 1 - GX0][z - GZ0], hm[x - 1 - GX0][z - GZ0]),
                        Math.min(hm[x - GX0][z + 1 - GZ0], hm[x - GX0][z - 1 - GZ0]));
                    if (h > lo + 1) {
                        if (h - FY >= 2) {
                            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                int nx = x + d[0], nz = z + d[1];
                                if (!lock[nx - GX0][nz - GZ0] && hm[nx - GX0][nz - GZ0] < h - 1) {
                                    hm[nx - GX0][nz - GZ0] = h - 1;
                                }
                            }
                        } else {
                            hm[x - GX0][z - GZ0] = lo + 1;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ chemins

    void paths(int[][] north, int[][] south) {
        Noise jn = noise.derive(2);
        for (int side : new int[]{-1, 1}) {
            for (int[][] line : new int[][][]{north, south}) {
                for (int i = 0; i + 1 < line.length; i++) {
                    double x1 = side * line[i][0], z1 = line[i][1];
                    double x2 = side * line[i + 1][0], z2 = line[i + 1][1];
                    for (int x = (int) Math.min(x1, x2) - 3; x <= (int) Math.max(x1, x2) + 3; x++) {
                        for (int z = (int) Math.min(z1, z2) - 3; z <= (int) Math.max(z1, z2) + 3; z++) {
                            double vx = x2 - x1, vz = z2 - z1;
                            double t = clamp(((x - x1) * vx + (z - z1) * vz) / (vx * vx + vz * vz), 0, 1);
                            double dx = x - (x1 + t * vx), dz = z - (z1 + t * vz);
                            double w = 0.85 + 0.55 * jn.noise2(x / 4.0, z / 4.0);
                            if (Math.hypot(dx, dz) <= w && inGrid(x, z)) {
                                path[x - GX0][z - GZ0] = true;
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ sol

    void ground() {
        Noise sn2 = noise.derive(4);
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                int ax = Math.abs(x);
                int az = Math.abs(z);
                int h = g(x, z);
                String top;
                double t2 = sn2.noise2(x / 6.0, z / 6.0);
                boolean field = ax <= 34 && az <= 22;
                if (ax >= 25 && ax <= 30 && az <= 9) {
                    String team = x < 0 ? "red" : "blue";
                    if (ax == 25 || az == 9) {
                        top = "minecraft:" + team + "_concrete";
                    } else if (ax == 29 && az <= 5) {
                        top = "minecraft:" + team + "_terracotta";
                    } else {
                        top = baseFloor(x, z, t2);
                    }
                    bare[x - GX0][z - GZ0] = true;
                } else if (ax >= 31 && ax <= 37 && az <= 8) {
                    top = cabinFloor(x, z);
                    bare[x - GX0][z - GZ0] = true;
                } else if (path[x - GX0][z - GZ0] && field && az <= 18) {
                    top = pathTop(x, z, t2);
                    bare[x - GX0][z - GZ0] = true;
                } else if (!field) {
                    boolean walk = az >= 24 && az <= 25 && ax <= 26;
                    top = outsideTop(x, z, t2, walk);
                    if (walk) {
                        bare[x - GX0][z - GZ0] = true;
                    }
                } else {
                    top = fieldTop(x, z, h);
                }
                for (int y = FY; y < h; y++) {
                    set(x, y, z, subsoil(x, y, z));
                }
                set(x, h, z, top);
                // sable / gravier : toujours un bloc plein dessous (ils tomberaient a la premiere mise a jour)
                if (h == FY && (top.endsWith("sand") || top.endsWith("gravel") || top.endsWith("_powder"))) {
                    set(x, FY - 1, z, subsoil(x, FY - 1, z));
                }
            }
        }
    }

    // ------------------------------------------------------------------ limites

    void edges() {
        Noise hn = noise.derive(5);
        for (int x = -34; x <= 34; x++) {
            for (int z = -22; z <= 22; z++) {
                int ax = Math.abs(x);
                int az = Math.abs(z);
                if (!(az >= 21) && !(ax >= 33 && az >= 9)) {
                    continue;
                }
                int h = g(x, z);
                int hb = h;
                for (int i = -1; i <= 1; i++) {
                    for (int k = -1; k <= 1; k++) {
                        hb = Math.max(hb, g(x + i, z + k));
                    }
                }
                int top = hb + 2 + (hn.noise2(x / 3.5, z / 3.5) > 0.35 ? 1 : 0);
                for (int y = h + 1; y <= top; y++) {
                    set(x, y, z, edgeMat(x, y, z));
                }
                String cap = edgeCap(x, top + 1, z);
                if (cap != null) {
                    set(x, top + 1, z, cap);
                }
                edgeExtra(x, z, h, top);
                bare[x - GX0][z - GZ0] = true;
            }
        }
        for (int x = -NX; x <= NX; x++) {
            for (int sd : new int[]{-1, 1}) {
                netColumn(x, sd * NZ, (x + NX) % 5 == 0 || Math.abs(x) == NX, "east=true,west=true");
            }
        }
        for (int z = -NZ + 1; z <= NZ - 1; z++) {
            if (Math.abs(z) <= 8) {
                continue;
            }
            for (int sd : new int[]{-1, 1}) {
                netColumn(sd * NX, z, (z + NZ) % 5 == 0, "north=true,south=true");
            }
        }
        for (int x = -BX; x <= BX; x++) {
            fill(x, FY + 1, -BZ, x, FY + 20, -BZ, "minecraft:barrier");
            fill(x, FY + 1, BZ, x, FY + 20, BZ, "minecraft:barrier");
        }
        for (int z = -BZ + 1; z <= BZ - 1; z++) {
            if (Math.abs(z) <= 9) {
                continue;
            }
            fill(-BX, FY + 1, z, -BX, FY + 20, z, "minecraft:barrier");
            fill(BX, FY + 1, z, BX, FY + 20, z, "minecraft:barrier");
        }
    }

    void netColumn(int x, int z, boolean post, String conn) {
        if (post) {
            fill(x, FY + 1, z, x, FY + 8, z, netPost());
            set(x, FY + 9, z, netCap());
        } else {
            fill(x, FY + 1, z, x, FY + 7, z, netPane() + "[" + conn + "]");
            set(x, FY + 8, z, netTop() + "[" + conn + "]");
        }
    }

    // ------------------------------------------------------------------ cabane d'equipe (commune)

    /**
     * Cabane de l'equipe en |x| 31..37, |z| &lt;= 8, porte vers le terrain, toit a pignons, drapeau sur le faitage.
     * Renvoie le Y du faitage.
     */
    int cabin(int side, Style st, RoofStyle rs) {
        String team = side < 0 ? "red" : "blue";
        int x1 = side * 31, x2 = side * 37;
        Dir door = side < 0 ? Dir.EAST : Dir.WEST;
        Facade.building(s, st, x1, -8, x2, 8, FY + 1, FY + 5, door, -1);
        int ridge = Roof.build(s, rs, Roof.gableZ(Math.min(x1, x2), -8, Math.max(x1, x2), 8, FY + 5));
        int fz = side < 0 ? -4 : 4;
        int px = side * 34;
        fill(px, ridge + 1, fz, px, ridge + 5, fz, "minecraft:dark_oak_fence");
        for (int y = ridge + 3; y <= ridge + 5; y++) {
            for (int k = 1; k <= 3; k++) {
                int z = fz + (side < 0 ? -k : k);
                boolean stripe = y == ridge + 4 && k == 2;
                set(px, y, z, stripe ? "minecraft:white_wool" : "minecraft:" + team + "_wool");
            }
        }
        for (int z : new int[]{-3, 3}) {
            set(side * 30, FY + 4, z, "minecraft:lantern[hanging=true]");
        }
        return ridge;
    }

    void lamps(int side) {
        long sd = side < 0 ? 11 : 13;
        for (int x : new int[]{6, 18, 28}) {
            for (int zs : new int[]{-1, 1}) {
                int X = side * (x + (hash(sd, x, zs) < 0.5 ? 0 : 1));
                lamppost(X, zs * 19, zs);
            }
        }
        if (side > 0) {
            lamppost(0, 19, 1);
            lamppost(0, -19, -1);
        }
    }

    // ------------------------------------------------------------------ gradins (meme geometrie que le speedball)

    void stands(int s0) {
        String[] m = standMats();
        String seat = m[0], aisleB = m[1], back = m[2], pillar = m[3], roofSlab = m[4];
        String face = s0 > 0 ? "south" : "north";
        for (int k = 0; k <= 4; k++) {
            int z = s0 * (26 + k);
            for (int x = -24; x <= 24; x++) {
                String riser = x < 0 ? "minecraft:red_concrete" : x > 0 ? "minecraft:blue_concrete" : "minecraft:white_concrete";
                fill(x, FY, z, x, FY + k, z, riser);
                boolean aisle = x == -12 || x == 12;
                set(x, FY + 1 + k, z, aisle ? aisleB : seat + "[facing=" + face + "]");
            }
        }
        int zb = s0 * 31;
        fill(-25, FY, zb, 25, FY + 7, zb, back);
        for (int x = -21; x <= 21; x += 6) {
            fill(x, FY + 5, zb - s0, x, FY + 7, zb - s0, pillar);
        }
        for (int x = -24; x <= 24; x += 6) {
            fill(x, FY, zb + s0, x, FY + 6, zb + s0, pillar);
            set(x, FY + 7, zb + s0, seat + "[facing=" + face + ",half=bottom]");
        }
        for (int x : new int[]{-25, 25}) {
            for (int k = 0; k <= 5; k++) {
                fill(x, FY, s0 * (26 + k), x, FY + 7, s0 * (26 + k), pillar);
            }
        }
        for (int x = -25; x <= 25; x++) {
            for (int k = 1; k <= 5; k++) {
                set(x, FY + 8, s0 * (26 + k), roofSlab + "[type=bottom]");
            }
            set(x, FY + 8, s0 * 26, x < 0 ? "minecraft:red_concrete" : x > 0 ? "minecraft:blue_concrete" : "minecraft:white_concrete");
        }
        for (int x = -20; x <= 20; x += 8) {
            set(x, FY + 7, s0 * 28, "minecraft:lantern[hanging=true]");
        }
    }

    // ------------------------------------------------------------------ petits objets communs

    /** Bobine de cable debout (3x3x3). */
    void spool(int cx, int cz, String wood, String slab, String core) {
        int y0 = g(cx, cz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean c = dx != 0 && dz != 0;
                set(cx + dx, y0 + 1, cz + dz, c ? slab + "[type=top]" : wood);
                set(cx + dx, y0 + 3, cz + dz, c ? slab + "[type=bottom]" : wood);
            }
        }
        set(cx, y0 + 2, cz, core);
        set(cx + 1, y0 + 2, cz, "minecraft:black_wool");
        set(cx - 1, y0 + 2, cz, "minecraft:black_wool");
    }

    /** Colonne de h blocs pleins posee sur le sol (coordonnee x du cote est, miroir par side). */
    void stack(int side, int x, int z, int h, String b) {
        int X = side * x;
        int y0 = g(X, z);
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, b);
        }
    }

    /** Echelle collee a un bloc plein, de y1 a y2. */
    void ladder(int x, int z, int y1, int y2, String facing) {
        for (int y = y1; y <= y2; y++) {
            set(x, y, z, "minecraft:ladder[facing=" + facing + "]");
        }
    }
}
