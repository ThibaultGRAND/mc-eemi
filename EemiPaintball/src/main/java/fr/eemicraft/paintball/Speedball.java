package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.B;
import fr.eemicraft.paintball.build.Connect;
import fr.eemicraft.paintball.build.Dir;
import fr.eemicraft.paintball.build.Facade;
import fr.eemicraft.paintball.build.Mat;
import fr.eemicraft.paintball.build.Noise;
import fr.eemicraft.paintball.build.Palette;
import fr.eemicraft.paintball.build.Roof;
import fr.eemicraft.paintball.build.RoofStyle;
import fr.eemicraft.paintball.build.Sink;
import fr.eemicraft.paintball.build.Stairs;
import fr.eemicraft.paintball.build.Style;
import fr.eemicraft.paintball.build.Tree;
import java.util.Random;

/**
 * Terrain de speedball en plein air (version 2, 16/11/2026), sans dependance Bukkit : tout passe par un {@link Sink}
 * (BukkitSink sur le serveur, RecordingSink pour le test hors serveur).
 *
 * <p>Contrat garde avec la 1.x : axe long = x (Rouge a l'ouest, Bleu a l'est), zone jouable dans le filet
 * |x| &lt; {@link #NX}, |z| &lt; {@link #NZ}, sol de reference {@link #FY} (on marche en FY + 1), apparitions sur les
 * bases plates en x = -29 / 29, |z| &lt;= 5 (dalle verrouillee a FY, |x| 25..30, |z| &lt;= 9), gradins en z = +-26..31
 * (spectateurs en 0.5, FY + 6.5, 29.5), salle d'attente en WY. Le terrain ne descend jamais sous FY (chute = FY - 5).
 *
 * <p>Plan de jeu en miroir x / -x (memes couverts et memes hauteurs pour les deux equipes) ; decor asymetrique :
 * relief fin, sols, chemins, vegetation, couleurs des conteneurs, arbres et drapeaux tires par du bruit propre a
 * chaque cote. Limites : talus + haie, filet derriere, barrieres invisibles seulement derriere le filet.
 */
final class Speedball {
    static final int FY = 100;
    static final int HX = 30;
    static final int HZ = 18;
    /** Ligne du filet (le jeu ne peint rien au-dela). */
    static final int NX = 35;
    static final int NZ = 23;
    /** Barrieres invisibles, derriere le filet. */
    static final int BX = 37;
    static final int BZ = 24;
    static final int WY = 140;

    private static final int GX0 = -44, GZ0 = -34, GW = 89, GL = 69;

    private final Sink s;
    private final Noise noise = new Noise(20261116L);
    private final int[][] hm = new int[GW][GL];
    private final boolean[][] lock = new boolean[GW][GL];
    private final boolean[][] path = new boolean[GW][GL];
    private final boolean[][] bare = new boolean[GW][GL];
    private int gy = FY;

    private final Palette hedge = Palette.of(31, B.leaves("minecraft:oak_leaves"), 62,
        B.leaves("minecraft:azalea_leaves"), 26, B.leaves("minecraft:flowering_azalea_leaves"), 12).period(5);

    Speedball(Sink s) {
        this.s = s;
    }

    // ------------------------------------------------------------------ outils

    private void set(int x, int y, int z, String b) {
        s.set(x, y, z, b);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    s.set(x, y, z, b);
                }
            }
        }
    }

    private static boolean inGrid(int x, int z) {
        return x >= GX0 && x < GX0 + GW && z >= GZ0 && z < GZ0 + GL;
    }

    /** Y du bloc de surface (on se tient en g + 1). */
    int g(int x, int z) {
        return inGrid(x, z) ? hm[x - GX0][z - GZ0] : FY;
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double hash(long salt, int x, int z) {
        return Noise.hash(salt, x, 0, z);
    }

    /** Orientation est/ouest retournee pour le cote ouest (miroir x). */
    private static String fx(String facing, int side) {
        if (side > 0) {
            return facing;
        }
        return switch (facing) {
            case "east" -> "west";
            case "west" -> "east";
            default -> facing;
        };
    }

    /** Hauteur de jeu : sol plat verrouille sur un rectangle (coordonnees du cote est, recopiees en miroir). */
    private void pad(int x1, int z1, int x2, int z2, int y, boolean mirror) {
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

    // ------------------------------------------------------------------ construction

    void build() {
        fill(-46, 95, -36, 46, 122, 36, "minecraft:air");
        // memes bandes exterieures que Field.build : rien d'une map a theme (ou d'une map retiree) ne reste autour
        fill(-48, FY, -48, -47, FY + 16, 47, "minecraft:air");
        fill(47, FY, -48, 47, FY + 16, 47, "minecraft:air");
        fill(-46, FY, -48, 46, FY + 16, -37, "minecraft:air");
        fill(-46, FY, 37, 46, FY + 16, 47, "minecraft:air");
        fill(-12, WY - 2, -10, 12, WY + 8, 10, "minecraft:air");
        heights();
        paths();
        ground();
        edges();
        for (int side : new int[]{-1, 1}) {
            base(side);
            obstacles(side);
            lamps(side);
        }
        tower();
        centerCovers();
        stands(1);
        stands(-1);
        int[][] lights = {{-39, -28}, {39, -28}, {-39, 28}, {39, 28}};
        for (int[] t : lights) {
            floodlight(t[0], t[1]);
        }
        trees();
        vegetation();
        Stairs.fix(s, -44, FY - 1, -34, 44, FY + 18, 34);
        Connect.fix(s, -44, FY - 1, -34, 44, FY + 18, 34);
        waitingRoom();
    }

    // ------------------------------------------------------------------ relief

    private void heights() {
        Noise a = noise.derive(1);
        double[][] v = new double[GW][GL];
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                int ax = Math.abs(x);
                int az = Math.abs(z);
                double val = 0;
                if (ax <= 34 && az <= 22) {
                    double n = a.fbm2((x + 300) / 13.0, (z - 120) / 13.0, 3, 0.5, 2.0);
                    // ondulation fine, propre a chaque cote (decor asymetrique)
                    val = Math.max(0, n - 0.05) * 2.0;
                    // butte sud (miroir) : sommet de rayon ~2, pentes de 4-5 blocs
                    double d = Math.hypot((ax - 12) / 4.6, (z - 12) / 4.2);
                    val = Math.max(val, 3.0 * clamp((1.45 - d) / 0.95, 0, 1));
                    // dune nord (miroir)
                    double d2 = Math.hypot((ax - 20) / 4.4, (z + 15) / 2.6);
                    val = Math.max(val, 2.0 * clamp((1.4 - d2) / 0.9, 0, 1));
                    // butte basse au centre-sud-ouest de chaque moitie
                    double d3 = Math.hypot((ax - 5) / 3.4, (z - 9) / 2.4);
                    val = Math.max(val, 1.0 * clamp((1.3 - d3) / 0.6, 0, 1));
                    // talus vers la haie
                    // talus vers la haie, pied bruite (pas de crete droite)
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
        // lissage 3x3 (une passe), puis arrondi
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
        // sols plats verrouilles (memes hauteurs des deux cotes)
        pad(25, -9, 37, 9, FY, true);          // bases + cabanes
        pad(-3, -5, 3, 5, FY, false);           // mirador central et ses caisses
        pad(7, -16, 17, -11, FY, true);         // conteneur + marches
        pad(19, 12, 24, 16, FY, true);          // echafaudage
        pad(8, -2, 12, 2, FY, true);            // bobines
        pad(-2, 9, 2, 13, FY, false);           // bobine centrale sud
        pad(-4, -13, 4, -10, FY, false);        // sacs de sable centraux
        pad(22, -10, 24, 10, FY, true);         // boucliers devant la base
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                if (Math.abs(x) > NX || Math.abs(z) >= NZ) {
                    hm[x - GX0][z - GZ0] = FY;
                    lock[x - GX0][z - GZ0] = true;
                }
            }
        }
        // marches praticables : jamais plus d'un bloc au-dessus du voisin le plus bas (hors talus de bord)
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
                        // la butte garde sa forme : on remonte les voisins bas plutot que d'araser
                        boolean feature = h - FY >= 2;
                        if (feature) {
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

    private void paths() {
        int[][] north = {{26, -1}, {21, -4}, {17, -9}, {11, -10}, {6, -9}, {3, -6}, {0, -5}};
        int[][] south = {{26, 1}, {21, 4}, {17, 8}, {12, 6}, {7, 6}, {3, 4}, {0, 4}};
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

    private void ground() {
        Noise sn = noise.derive(3);
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
                    // dalle de base : terre battue, bordure aux couleurs de l'equipe
                    String team = x < 0 ? "red" : "blue";
                    if (ax == 25 || az == 9) {
                        top = "minecraft:" + team + "_concrete";
                    } else if (ax == 29 && az <= 5) {
                        top = "minecraft:" + team + "_terracotta";
                    } else {
                        top = t2 > 0.35 ? "minecraft:coarse_dirt" : "minecraft:packed_mud";
                    }
                    bare[x - GX0][z - GZ0] = true;
                } else if (ax >= 31 && ax <= 37 && az <= 8) {
                    top = "minecraft:polished_andesite";
                    bare[x - GX0][z - GZ0] = true;
                } else if (path[x - GX0][z - GZ0] && field && az <= 18) {
                    top = t2 > 0.55 ? "minecraft:coarse_dirt" : "minecraft:dirt_path";
                    bare[x - GX0][z - GZ0] = true;
                } else if (!field) {
                    boolean walk = az >= 24 && az <= 25 && ax <= 26;
                    top = walk ? (t2 > 0.3 ? "minecraft:gravel" : "minecraft:coarse_dirt") : "minecraft:grass_block";
                    if (walk) {
                        bare[x - GX0][z - GZ0] = true;
                    }
                } else {
                    // grandes plaques d'usure (terre, boue au coeur), mousse, podzol en lisiere : bruit propre a chaque cote
                    double wear = sn.fbm2(x / 13.0 + 40, z / 13.0, 2, 0.45, 2.0);
                    double moss = sn2.fbm2(x / 10.0 - 70, z / 10.0, 2, 0.5, 2.0);
                    double edge = sn2.noise2(x / 7.0 + 15, z / 7.0) + (Math.max(az, ax - 12) - 18) * 0.3;
                    if (edge > 0.1 && (az >= 16 || ax >= 30)) {
                        top = "minecraft:podzol";
                    } else if (wear > 0.5) {
                        top = "minecraft:mud";
                    } else if (wear > 0.28) {
                        top = "minecraft:coarse_dirt";
                    } else if (moss > 0.38) {
                        top = "minecraft:moss_block";
                    } else if (wear < -0.45 && moss > 0.05) {
                        top = "minecraft:rooted_dirt";
                    } else {
                        top = "minecraft:grass_block";
                    }
                }
                for (int y = FY; y < h; y++) {
                    set(x, y, z, "minecraft:dirt");
                }
                set(x, h, z, top);
            }
        }
    }

    // ------------------------------------------------------------------ limites : talus, haie, filet, barrieres

    private void edges() {
        Noise hn = noise.derive(5);
        for (int x = -34; x <= 34; x++) {
            for (int z = -22; z <= 22; z++) {
                int ax = Math.abs(x);
                int az = Math.abs(z);
                boolean hz = az >= 21;
                boolean hx = ax >= 33 && az >= 9;
                if (!hz && !hx) {
                    continue;
                }
                int h = g(x, z);
                // la haie depasse toujours de 2 le sol voisin le plus haut : on ne peut pas monter dessus
                int hb = h;
                for (int i = -1; i <= 1; i++) {
                    for (int k = -1; k <= 1; k++) {
                        hb = Math.max(hb, g(x + i, z + k));
                    }
                }
                int top = hb + 2 + (hn.noise2(x / 3.5, z / 3.5) > 0.35 ? 1 : 0);
                for (int y = h + 1; y <= top; y++) {
                    set(x, y, z, hedge.at(x, y, z));
                }
                bare[x + -GX0][z - GZ0] = true;
            }
        }
        // filet noir (vitres reliees) et poteaux, derriere la haie
        for (int x = -NX; x <= NX; x++) {
            for (int sd : new int[]{-1, 1}) {
                netColumn(x, sd * NZ, (x + NX) % 5 == 0 || Math.abs(x) == NX, "east=true,west=true");
            }
        }
        for (int z = -NZ + 1; z <= NZ - 1; z++) {
            if (Math.abs(z) <= 8) {
                continue; // la cabane de l'equipe ferme ce cote
            }
            for (int sd : new int[]{-1, 1}) {
                netColumn(sd * NX, z, (z + NZ) % 5 == 0, "north=true,south=true");
            }
        }
        // barrieres invisibles UNIQUEMENT derriere le filet (garde-fou)
        for (int x = -BX; x <= BX; x++) {
            fill(x, FY + 1, -BZ, x, FY + 20, -BZ, "minecraft:barrier");
            fill(x, FY + 1, BZ, x, FY + 20, BZ, "minecraft:barrier");
        }
        for (int z = -BZ + 1; z <= BZ - 1; z++) {
            if (Math.abs(z) <= 9) {
                continue; // le fond de la cabane (x = +-37) ferme ce cote
            }
            fill(-BX, FY + 1, z, -BX, FY + 20, z, "minecraft:barrier");
            fill(BX, FY + 1, z, BX, FY + 20, z, "minecraft:barrier");
        }
    }

    private void netColumn(int x, int z, boolean post, String conn) {
        if (post) {
            fill(x, FY + 1, z, x, FY + 8, z, "minecraft:dark_oak_log");
            set(x, FY + 9, z, "minecraft:dark_oak_slab[type=bottom]");
        } else {
            fill(x, FY + 1, z, x, FY + 7, z, "minecraft:black_stained_glass_pane[" + conn + "]");
            set(x, FY + 8, z, "minecraft:dark_oak_fence[" + conn + "]");
        }
    }

    // ------------------------------------------------------------------ bases

    private void base(int side) {
        String team = side < 0 ? "red" : "blue";
        int x1 = side * 31, x2 = side * 37;
        Palette wall = Palette.of(side < 0 ? 41 : 43, "minecraft:" + team + "_concrete", 70,
            "minecraft:" + team + "_terracotta", 30).period(8).amp(0.2);
        Style st = new Style().wall(wall).plinth(Mat.of("minecraft:polished_andesite"))
            .plinthStairs("minecraft:polished_andesite_stairs").post("minecraft:stripped_dark_oak_log")
            .sill("minecraft:polished_andesite_stairs").lintel("minecraft:spruce_slab").band(null)
            .cornice("minecraft:dark_oak_stairs").door("minecraft:spruce_door").spacing(4).storey(4)
            .shutters(0.7).blankBays(0.2).seed(side < 0 ? 5 : 9);
        Dir door = side < 0 ? Dir.EAST : Dir.WEST;
        Facade.building(s, st, x1, -8, x2, 8, FY + 1, FY + 5, door, -1);
        RoofStyle rs = new RoofStyle()
            .stairs(Palette.of(3, "minecraft:deepslate_tile_stairs", 70, "minecraft:cobbled_deepslate_stairs", 30).period(7))
            .ridge("minecraft:deepslate_tile_slab").body(Mat.of("minecraft:deepslate_tiles"))
            .gable(wall).gablePost("minecraft:stripped_dark_oak_log").overhang(1).spacing(4);
        int ridge = Roof.build(s, rs, Roof.gableZ(Math.min(x1, x2), -8, Math.max(x1, x2), 8, FY + 5));
        // drapeau de l'equipe sur le faitage (repere de la base), cote different pour chaque equipe
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
        // lanternes sous le debord, de part et d'autre de la porte
        for (int z : new int[]{-3, 3}) {
            set(side * 30, FY + 4, z, "minecraft:lantern[hanging=true]");
        }
        // boucliers devant l'apparition (couvert haut, couleur de l'equipe)
        for (int[] r : new int[][]{{-9, -5}, {5, 9}}) {
            int x = side * 24;
            for (int z = r[0]; z <= r[1]; z++) {
                boolean post = z == r[0] || z == r[1];
                if (post) {
                    fill(x, FY + 1, z, x, FY + 3, z, "minecraft:dark_oak_log");
                    set(x, FY + 4, z, "minecraft:dark_oak_slab[type=bottom]");
                } else {
                    fill(x, FY + 1, z, x, FY + 2, z, "minecraft:spruce_planks");
                    set(x, FY + 3, z, "minecraft:" + team + "_wool");
                }
            }
            // contrefiche cote base
            int bz = (r[0] + r[1]) / 2;
            set(side * 25, FY + 1, bz, "minecraft:spruce_stairs[facing=" + fx("west", side) + "]");
        }
    }

    // ------------------------------------------------------------------ couverts (un cote, recopie en miroir)

    private void obstacles(int side) {
        long sd = side < 0 ? 101 : 202;
        // conteneur maritime (niveau +4 sur le toit), porte ouverte vers le centre
        container(side, 8, -15, 15, -12, side < 0 ? "minecraft:orange_terracotta" : "minecraft:cyan_terracotta");
        hay(side, 17, -13, 1, sd);
        crate(side, 16, -13, 2, sd);
        crate(side, 16, -14, 3, sd);
        crate(side, 16, -15, 4, sd);
        // dune nord : rangee de pneus
        for (int x = 19; x <= 23; x++) {
            tires(side, x, -14, x == 23 ? 2 : 1);
        }
        // cote nord, milieu
        hay(side, 5, -6, 1, sd);
        hay(side, 6, -6, 1, sd);
        hay(side, 6, -7, 2, sd);
        tires(side, 10, -9, 2);
        tires(side, 11, -9, 2);
        tires(side, 10, -8, 2);
        tires(side, 11, -8, hash(sd, 11, -8) < 0.5 ? 1 : 2);
        tires(side, 19, -9, 1);
        tires(side, 19, -8, 1);
        tires(side, 20, -8, 1);
        barrel(side, 14, -6, sd);
        barrel(side, 15, -6, sd);
        crate(side, 15, -5, 2, sd);
        hay(side, 3, -17, 1, sd);
        hay(side, 4, -17, 1, sd);
        hay(side, 25, -15, 2, sd);
        hay(side, 26, -15, 2, sd);
        hay(side, 26, -14, 1, sd);
        // couloir du milieu
        spool(side * 10, 0);
        hay(side, 16, -2, 1, sd);
        hay(side, 16, -1, 1, sd);
        hay(side, 16, 1, 1, sd);
        hay(side, 16, 2, 1, sd);
        hay(side, 17, -2, 2, sd);
        tires(side, 21, -1, 1);
        tires(side, 21, 1, 1);
        tires(side, 21, 0, 2);
        // cote sud, milieu
        crate(side, 5, 4, 2, sd);
        crate(side, 6, 4, 2, sd);
        crate(side, 5, 5, 1, sd);
        barrel(side, 7, 4, sd);
        tires(side, 13, 4, 1);
        tires(side, 14, 4, 2);
        tires(side, 13, 5, 1);
        sandbags(side, new int[][]{{18, 7}, {19, 7}, {20, 6}, {21, 6}, {22, 6}}, sd);
        // butte sud : fortin de pieux (niveau +3, ouvert a l'arriere), buisson en haut
        fort(side, sd);
        // echafaudage (niveau +4)
        scaffold(side, 20, 13);
        // tas de bois au sud, caisses au coin
        for (int x = 3; x <= 7; x++) {
            set(side * x, g(side * x, 16) + 1, 16, "minecraft:spruce_log[axis=x]");
        }
        for (int x = 4; x <= 6; x++) {
            set(side * x, g(side * x, 16) + 2, 16, "minecraft:spruce_log[axis=x]");
        }
        crate(side, 25, 14, 2, sd);
        crate(side, 26, 14, 2, sd);
        barrel(side, 25, 15, sd);
    }

    private void hay(int side, int x, int z, int h, long sd) {
        int X = side * x;
        int y0 = g(X, z);
        String axis = hash(sd, x, z) < 0.5 ? "x" : "z";
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, "minecraft:hay_block[axis=" + (y == 1 ? axis : "y") + "]");
        }
    }

    /** Caisse : planches, dessus en trappe fermee ou tonneau. */
    private void crate(int side, int x, int z, int h, long sd) {
        int X = side * x;
        int y0 = g(X, z);
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, y == h && hash(sd, x, z * 7) < 0.35 ? "minecraft:barrel[facing=up]" : "minecraft:spruce_planks");
        }
        if (hash(sd, z, x) < 0.5) {
            set(X, y0 + h + 1, z, "minecraft:spruce_trapdoor[half=bottom,facing=north]");
        }
    }

    private void barrel(int side, int x, int z, long sd) {
        int X = side * x;
        String f = hash(sd, x, z) < 0.6 ? "up" : (hash(sd, z, x) < 0.5 ? "north" : fx("east", side));
        set(X, g(X, z) + 1, z, "minecraft:barrel[facing=" + f + "]");
    }

    /** Pile de pneus (1x1). */
    private void tires(int side, int x, int z, int h) {
        int X = side * x;
        int y0 = g(X, z);
        for (int y = 1; y <= h; y++) {
            set(X, y0 + y, z, "minecraft:black_concrete");
        }
        set(X, y0 + h + 1, z, "minecraft:black_carpet");
    }

    private void sandbags(int side, int[][] cells, long sd) {
        for (int[] c : cells) {
            int X = side * c[0];
            int y0 = g(X, c[1]);
            set(X, y0 + 1, c[1], "minecraft:mud_bricks");
            if (hash(sd, c[0], c[1]) < 0.55) {
                set(X, y0 + 2, c[1], "minecraft:mud_brick_slab[type=bottom]");
            }
        }
    }

    private void container(int side, int x1, int z1, int x2, int z2, String body) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int X = side * x;
                boolean edgeX = x == x1 || x == x2;
                boolean edgeZ = z == z1 || z == z2;
                set(X, FY, z, "minecraft:spruce_planks");
                for (int y = FY + 1; y <= FY + 4; y++) {
                    if (edgeX && edgeZ) {
                        set(X, y, z, "minecraft:polished_andesite");
                    } else if (edgeX || edgeZ) {
                        // nervures : une colonne en retrait d'un cran sur deux (trappe de fer posee devant)
                        set(X, y, z, body);
                    }
                }
                set(X, FY + 5, z, edgeX && edgeZ ? "minecraft:polished_andesite" : body);
            }
        }
        // nervures en saillie sur les grands cotes
        for (int x = x1 + 1; x < x2; x += 2) {
            int X = side * x;
            for (int z : new int[]{z1 - 1, z2 + 1}) {
                String f = z < z1 ? "north" : "south";
                // pas de nervure devant la porte laterale (x2 - 2, cote z2)
                if (s.isAir(X, FY + 1, z) && !(z > z2 && x == x2 - 2)) {
                    for (int y = FY + 1; y <= FY + 4; y++) {
                        set(X, y, z, "minecraft:iron_trapdoor[open=true,facing=" + f + ",half=bottom]");
                    }
                }
            }
        }
        // ouverture cote centre (2 x 3) et porte laterale
        int open = side * x1;
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            fill(open, FY + 1, z, open, FY + 3, z, "minecraft:air");
        }
        int dx = side * (x2 - 2);
        fill(dx, FY + 1, z2, dx, FY + 2, z2, "minecraft:air");
        // battant ouvert contre la paroi
        // battant ouvert rabattu contre la face exterieure du poteau d'angle (x1, z1) : panneau cote poteau
        String leaf = side > 0 ? "west" : "east";
        set(open - side, FY + 1, z1, "minecraft:iron_trapdoor[open=true,facing=" + leaf + ",half=bottom]");
        set(open - side, FY + 2, z1, "minecraft:iron_trapdoor[open=true,facing=" + leaf + ",half=bottom]");
        set(side * (x2 - 1), FY + 6, z1 + 1, "minecraft:lantern");
    }

    /** Bobine de cable debout (3x3x3, vide autour du noyau au milieu). */
    private void spool(int cx, int cz) {
        int y0 = g(cx, cz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean c = dx != 0 && dz != 0;
                set(cx + dx, y0 + 1, cz + dz, c ? "minecraft:spruce_slab[type=top]" : "minecraft:spruce_planks");
                set(cx + dx, y0 + 3, cz + dz, c ? "minecraft:spruce_slab[type=bottom]" : "minecraft:spruce_planks");
            }
        }
        set(cx, y0 + 2, cz, "minecraft:stripped_oak_log[axis=y]");
        set(cx + 1, y0 + 2, cz, "minecraft:black_wool");
        set(cx - 1, y0 + 2, cz, "minecraft:black_wool");
    }

    private void fort(int side, long sd) {
        // pieux en L sur l'arete ouest et nord du sommet, creneaux un sur deux
        int topY = FY + 5;
        int[][] cells = {{8, 10}, {8, 11}, {8, 12}, {8, 14}, {8, 15}, {9, 9}, {10, 8}, {11, 8}, {13, 8}, {14, 8}};
        for (int[] c : cells) {
            int X = side * c[0];
            int z = c[1];
            int y0 = g(X, z);
            int top = (c[0] + c[1]) % 2 == 0 ? topY : topY - 1;
            for (int y = y0 + 1; y <= top; y++) {
                set(X, y, z, "minecraft:spruce_log[axis=y]");
            }
            if (top == topY) {
                set(X, top + 1, z, "minecraft:spruce_fence");
            }
        }
        // buisson de couvert au sommet (les deux cotes), un arbre en plus a l'ouest (decor)
        int bx = side * 13, bz = 14;
        int y0 = g(bx, bz);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int X = bx + side * dx;
                for (int y = 1; y <= 2; y++) {
                    set(X, g(X, bz + dz) + y, bz + dz, hedge.at(X, y0 + y, bz + dz));
                }
            }
        }
        if (side < 0) {
            new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(7).crown(2.6)
                .place(s, bx - 1, g(bx - 1, bz + 2) + 1, bz + 2, 77);
        } else {
            set(bx + 1, y0 + 3, bz + 1, "minecraft:flowering_azalea_leaves[persistent=true]");
        }
    }

    private void scaffold(int side, int x1, int z1) {
        int py = FY + 5;
        for (int dx = 0; dx <= 3; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                int X = side * (x1 + dx);
                int z = z1 + dz;
                boolean corner = (dx == 0 || dx == 3) && (dz == 0 || dz == 2);
                if (corner) {
                    fill(X, FY + 1, z, X, py, z, "minecraft:dark_oak_log");
                } else {
                    set(X, py, z, "minecraft:spruce_planks");
                }
                // garde-corps : muret de planches cote ennemi et sud, barriere au nord, entree a l'ouest
                boolean east = dx == 3, south = dz == 2, north = dz == 0;
                if (east || south) {
                    set(X, py + 1, z, "minecraft:spruce_planks");
                } else if (north && dx > 0) {
                    set(X, py + 1, z, "minecraft:spruce_fence");
                }
            }
        }
        // croisillons sous la plate-forme
        set(side * (x1 + 1), FY + 1, z1 + 2, "minecraft:spruce_fence");
        set(side * (x1 + 1), py - 1, z1 + 2, "minecraft:spruce_fence");
        // echelle cote centre, accrochee au poteau d'angle
        int lx = side * (x1 - 1);
        String f = fx("west", side);
        for (int y = FY + 1; y <= py; y++) {
            set(lx, y, z1, "minecraft:ladder[facing=" + f + "]");
        }
        set(side * (x1 + 3), py + 2, z1 + 2, "minecraft:lantern");
    }

    // ------------------------------------------------------------------ centre

    private void tower() {
        // poteaux d'angle
        for (int x : new int[]{-2, 2}) {
            for (int z : new int[]{-2, 2}) {
                fill(x, FY + 1, z, x, FY + 8, z, "minecraft:dark_oak_log");
                set(x, FY + 9, z, "minecraft:lantern");
            }
        }
        // noyau de bottes de foin (couvert haut, coupe la ligne base a base)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fill(x, FY + 1, z, x, FY + 2, z, "minecraft:hay_block[axis=y]");
            }
        }
        for (int x : new int[]{-1, 1}) {
            fill(x, FY + 3, 0, x, FY + 4, 0, "minecraft:stripped_dark_oak_log[axis=y]");
        }
        fill(0, FY + 3, 0, 0, FY + 4, 0, "minecraft:stripped_dark_oak_log[axis=y]");
        // plate-forme (niveau +5)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                    continue;
                }
                if (Math.abs(x) == 2 && z == 0) {
                    continue;
                }
                set(x, FY + 5, z, "minecraft:spruce_planks");
            }
        }
        // echelles est et ouest
        for (int y = FY + 1; y <= FY + 5; y++) {
            set(2, y, 0, "minecraft:ladder[facing=east]");
            set(-2, y, 0, "minecraft:ladder[facing=west]");
        }
        // garde-corps : muret de planches au nord/sud (couvert bas), barrieres a l'est/ouest
        for (int x = -1; x <= 1; x++) {
            set(x, FY + 6, -2, "minecraft:spruce_planks");
            set(x, FY + 6, 2, "minecraft:spruce_planks");
        }
        for (int z : new int[]{-1, 1}) {
            set(-2, FY + 6, z, "minecraft:spruce_fence");
            set(2, FY + 6, z, "minecraft:spruce_fence");
        }
        RoofStyle rs = new RoofStyle()
            .stairs(Palette.of(4, "minecraft:spruce_stairs", 75, "minecraft:dark_oak_stairs", 25).period(6))
            .ridge("minecraft:spruce_slab").body(Mat.of("minecraft:spruce_planks")).overhang(1).spacing(4);
        int top = Roof.build(s, rs, Roof.hip(-2, -2, 2, 2, FY + 9));
        // mat et drapeau a damier (repere central, flotte vers l'est)
        fill(0, top + 1, 0, 0, top + 5, 0, "minecraft:dark_oak_fence");
        set(1, top + 4, 0, "minecraft:white_wool");
        set(2, top + 4, 0, "minecraft:black_wool");
        set(1, top + 5, 0, "minecraft:black_wool");
        set(2, top + 5, 0, "minecraft:white_wool");
        set(3, top + 5, 0, "minecraft:black_wool");
    }

    private void centerCovers() {
        // sacs de sable en arc au nord, caisse haute au milieu
        for (int x = -3; x <= 3; x++) {
            int z = Math.abs(x) >= 2 ? -10 : -11;
            set(x, FY + 1, z, "minecraft:mud_bricks");
            if (Math.abs(x) == 1 || Math.abs(x) == 3) {
                set(x, FY + 2, z, "minecraft:mud_brick_slab[type=bottom]");
            }
        }
        fill(0, FY + 2, -11, 0, FY + 2, -11, "minecraft:spruce_planks");
        set(0, FY + 1, -12, "minecraft:barrel[facing=up]");
        // bobine centrale au sud
        spool(0, 11);
        // caisse et bottes hautes au pied du mirador : ferment les dernieres lignes de tir base a base
        fill(0, FY + 1, -4, 0, FY + 2, -3, "minecraft:spruce_planks");
        set(0, FY + 3, -4, "minecraft:spruce_trapdoor[half=bottom,facing=north]");
        fill(0, FY + 1, 3, 0, FY + 2, 4, "minecraft:hay_block[axis=y]");
        // trois pneus empiles au nord du mirador (decor, un seul cote)
        set(-1, FY + 1, -5, "minecraft:black_concrete");
        set(-1, FY + 2, -5, "minecraft:black_carpet");
    }

    // ------------------------------------------------------------------ eclairage visible

    private void lamps(int side) {
        long sd = side < 0 ? 11 : 13;
        for (int x : new int[]{6, 18, 28}) {
            for (int zs : new int[]{-1, 1}) {
                int X = side * (x + (hash(sd, x, zs) < 0.5 ? 0 : 1));
                int z = zs * 19;
                lamppost(X, z, zs);
            }
        }
        if (side > 0) {
            lamppost(0, 19, 1);
            lamppost(0, -19, -1);
        }
    }

    /** Lampadaire : poteau de 4, bras vers le terrain, lanterne suspendue. */
    private void lamppost(int x, int z, int zs) {
        int y0 = g(x, z);
        set(x, y0 + 1, z, "minecraft:cobblestone_wall");
        fill(x, y0 + 2, z, x, y0 + 4, z, "minecraft:dark_oak_fence");
        int az = z - zs;
        set(x, y0 + 4, az, "minecraft:dark_oak_fence");
        set(x, y0 + 3, az, "minecraft:lantern[hanging=true]");
    }

    // ------------------------------------------------------------------ hors terrain

    private void stands(int s0) {
        String face = s0 > 0 ? "south" : "north";
        for (int k = 0; k <= 4; k++) {
            int z = s0 * (26 + k);
            for (int x = -24; x <= 24; x++) {
                String riser = x < 0 ? "minecraft:red_concrete" : x > 0 ? "minecraft:blue_concrete" : "minecraft:white_concrete";
                fill(x, FY, z, x, FY + k, z, riser);
                boolean aisle = x == -12 || x == 12;
                set(x, FY + 1 + k, z, aisle ? "minecraft:smooth_quartz" : "minecraft:quartz_stairs[facing=" + face + "]");
            }
        }
        int zb = s0 * 31;
        fill(-25, FY, zb, 25, FY + 7, zb, "minecraft:smooth_quartz");
        for (int x = -24; x <= 24; x += 4) {
            set(x, FY + 7, zb, "minecraft:white_stained_glass");
        }
        // pilastres cote gradins et contreforts au dos (cassent le grand pan)
        for (int x = -21; x <= 21; x += 6) {
            fill(x, FY + 5, zb - s0, x, FY + 7, zb - s0, "minecraft:quartz_pillar");
        }
        for (int x = -24; x <= 24; x += 6) {
            fill(x, FY, zb + s0, x, FY + 6, zb + s0, "minecraft:quartz_pillar");
            set(x, FY + 7, zb + s0, "minecraft:quartz_stairs[facing=" + face + ",half=bottom]");
        }
        for (int x : new int[]{-25, 25}) {
            for (int k = 0; k <= 5; k++) {
                fill(x, FY, s0 * (26 + k), x, FY + 7, s0 * (26 + k), "minecraft:quartz_pillar");
            }
        }
        for (int x = -25; x <= 25; x++) {
            for (int k = 1; k <= 5; k++) {
                set(x, FY + 8, s0 * (26 + k), "minecraft:smooth_quartz_slab[type=bottom]");
            }
        }
        for (int x = -25; x <= 25; x++) {
            set(x, FY + 8, s0 * 26, x < 0 ? "minecraft:red_concrete" : x > 0 ? "minecraft:blue_concrete" : "minecraft:white_concrete");
        }
        for (int x = -20; x <= 20; x += 8) {
            set(x, FY + 7, s0 * 28, "minecraft:lantern[hanging=true]");
        }
    }

    private void floodlight(int tx, int tz) {
        fill(tx, FY, tz, tx, FY + 14, tz, "minecraft:polished_andesite");
        fill(tx - 1, FY + 15, tz - 1, tx + 1, FY + 16, tz + 1, "minecraft:sea_lantern");
        fill(tx - 1, FY + 17, tz - 1, tx + 1, FY + 17, tz + 1, "minecraft:polished_andesite_slab[type=bottom]");
        set(tx, FY + 1, tz, "minecraft:chiseled_stone_bricks");
    }

    private void trees() {
        int[][] list = {{-31, 27, 7}, {33, 29, 8}, {-41, -19, 7}, {41, 14, 6}, {-42, 7, 8}, {38, -31, 7},
            {-29, -30, 6}, {43, -7, 7}, {-37, 31, 6}, {30, -27, 6}, {-43, -4, 6}, {42, 31, 6}};
        for (int i = 0; i < list.length; i++) {
            int[] t = list[i];
            String leaves2 = i % 3 == 0 ? "minecraft:birch_leaves" : "minecraft:azalea_leaves";
            String log = i % 3 == 0 ? "minecraft:birch_log" : "minecraft:oak_log";
            new Tree(log, "minecraft:oak_leaves", leaves2).height(t[2]).crown(2.8)
                .place(s, t[0], g(t[0], t[1]) + 1, t[1], 500 + i);
        }
    }

    private void vegetation() {
        Noise vn = noise.derive(7);
        String[] flowers = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy",
            "minecraft:azure_bluet"};
        for (int x = GX0; x < GX0 + GW; x++) {
            for (int z = GZ0; z < GZ0 + GL; z++) {
                if (bare[x - GX0][z - GZ0]) {
                    continue;
                }
                int h = g(x, z);
                String top = s.get(x, h, z);
                if (!top.startsWith("minecraft:grass_block") && !top.startsWith("minecraft:podzol")) {
                    continue;
                }
                if (!s.isAir(x, h + 1, z)) {
                    continue;
                }
                boolean field = Math.abs(x) <= 34 && Math.abs(z) <= 22;
                double v = vn.noise2(x / 5.0, z / 5.0) + 0.35 * (hash(91, x, z) - 0.5);
                boolean nearEdge = !field || Math.abs(z) >= 18 || Math.abs(x) >= 31;
                double thr = nearEdge ? 0.3 : 0.52;
                if (v < thr) {
                    continue;
                }
                double r = hash(92, x, z);
                if (nearEdge && r < 0.25 && s.isAir(x, h + 2, z)) {
                    set(x, h + 1, z, "minecraft:tall_grass[half=lower]");
                    set(x, h + 2, z, "minecraft:tall_grass[half=upper]");
                } else if (nearEdge && r < 0.4) {
                    set(x, h + 1, z, "minecraft:fern");
                } else if (v > 0.75 && r < 0.6) {
                    double fsel = vn.derive(1).noise2(x / 7.0, z / 7.0) * 0.5 + 0.5;
                    set(x, h + 1, z, flowers[(int) clamp(fsel * flowers.length, 0, flowers.length - 1)]);
                } else {
                    set(x, h + 1, z, "minecraft:short_grass");
                }
            }
        }
    }

    /** Salle d'attente seule (pour une petite map a theme, qui ne la construit pas). */
    void waitingOnly() {
        fill(-12, WY - 2, -10, 12, WY + 8, 10, "minecraft:air");
        waitingRoom();
    }

    // ------------------------------------------------------------------ salle d'attente (inchangee)

    private void waitingRoom() {
        Random rnd = new Random(42);
        for (int x = -9; x <= 9; x++) {
            for (int z = -7; z <= 7; z++) {
                String b;
                if (x == 0) {
                    b = "minecraft:black_concrete";
                } else if ((x + z) % 2 == 0) {
                    b = x < 0 ? "minecraft:red_concrete" : "minecraft:blue_concrete";
                } else {
                    b = "minecraft:white_concrete";
                }
                set(x, WY, z, b);
                set(x, WY - 1, z, "minecraft:smooth_stone");
                boolean edge = Math.abs(x) == 9 || Math.abs(z) == 7;
                boolean corner = Math.abs(x) == 9 && Math.abs(z) == 7;
                if (edge) {
                    for (int y = 1; y <= 4; y++) {
                        set(x, WY + y, z, corner ? "minecraft:quartz_pillar" : x < 0 && Math.abs(x) == 9 ? "minecraft:red_stained_glass"
                            : x > 0 && Math.abs(x) == 9 ? "minecraft:blue_stained_glass" : "minecraft:white_stained_glass");
                    }
                }
                boolean lamp = Math.abs(x) % 4 == 2 && Math.abs(z) % 4 == 2;
                set(x, WY + 5, z, edge ? "minecraft:smooth_quartz" : lamp ? "minecraft:sea_lantern" : "minecraft:glass");
            }
        }
        for (int i = 0; i < 14; i++) {
            int x = rnd.nextInt(17) - 8;
            int z = rnd.nextInt(13) - 6;
            String c = x < 0 ? "minecraft:pink_concrete" : "minecraft:light_blue_concrete";
            if (x == 0) {
                continue;
            }
            set(x, WY, z, c);
            if (rnd.nextBoolean() && Math.abs(x + 1) <= 8 && x + 1 != 0) {
                set(x + 1, WY, z, c);
            }
        }
        gy = WY;
        can(-6, 4, 1.0, 2, "minecraft:red_wool", "minecraft:white_wool");
        can(6, -4, 1.0, 2, "minecraft:blue_wool", "minecraft:white_wool");
        cone(-6, -4, 2, "minecraft:red_wool", "minecraft:white_wool");
        cone(6, 4, 2, "minecraft:blue_wool", "minecraft:white_wool");
        gy = FY;
        for (int x = -3; x <= 3; x++) {
            set(x, WY + 1, -6, "minecraft:quartz_stairs[facing=north]");
        }
    }

    private void can(int cx, int cz, double r, int h, String main, String band) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > r * r + 0.6) {
                    continue;
                }
                for (int y = 1; y <= h; y++) {
                    set(cx + dx, gy + y, cz + dz, y == 2 || y == h && h > 2 ? band : main);
                }
            }
        }
    }

    private void cone(int cx, int cz, int h, String main, String accent) {
        for (int i = 0; i < h; i++) {
            double r = 2.2 - i * (2.0 / h);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz <= r * r + 0.3) {
                        set(cx + dx, gy + 1 + i, cz + dz, i % 2 == 0 ? main : accent);
                    }
                }
            }
        }
        set(cx, gy + 1 + h, cz, accent);
    }
}
