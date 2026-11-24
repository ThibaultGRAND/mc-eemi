package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Dir;
import fr.eemicraft.murder.build.Mat;
import fr.eemicraft.murder.build.Palette;
import fr.eemicraft.murder.build.Shapes;
import fr.eemicraft.murder.build.Sink;
import fr.eemicraft.murder.build.Tree;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Map « Fete foraine » (id fete, monde mm_fete), refonte de Carnival (1.8.0), dessinee par code dans un {@link Sink}.
 *
 * <p>Meme gabarit que Carnival : boite x -56..56, z -55..56, sol en y 64 (pieds en 65), de nuit. Meme plan : entree
 * monumentale au sud, grande allee nord-sud bordee de stands, place centrale, boucle de chemins au nord autour du
 * chapiteau (centre), du carrousel (ouest) et de la grande roue (est), palais des glaces au sud-ouest et maison
 * hantee au sud-est. Ajouts : kiosque a musique sur la place (casse la ligne de vue de l'allee), chaises volantes,
 * diseuse de bonne aventure, auto-tamponneuses, gare des montagnes russes (quai en hauteur, deux escaliers), tasses,
 * toboggan geant (helter-skelter : echelle dedans, spirale dehors), roulottes et coins pique-nique, guirlandes
 * lumineuses partout. La palissade du bord et les montagnes russes hors de la boite font la limite.
 */
final class Fete extends MapKit {
    static final int VERSION = 2; // 1.9.1 : trappes, escaliers, portes, supports
    static final String ID = "fete";
    static final String WORLD = "mm_fete";
    static final String NAME = "Fête foraine";

    static final int BX1 = -56, BX2 = 56, BZ1 = -55, BZ2 = 56, MIN_Y = 62, MAX_Y = 92;
    static final int AREA = 74, Y_LO = 46, Y_HI = 96;
    static final int G = 64;
    static final int[] WORLD_SPAWN = {0, 65, 46};

    static final int[][] SPAWNS = {
            {-5, 65, 24},    // allee des stands
            {5, 65, 44},     // bas de l'allee
            {-40, 65, -50},  // auto-tamponneuses
            {40, 65, -36},   // chemin est
            {-4, 65, -29},   // piste du chapiteau
            {-27, 65, -13},  // devant le carrousel
            {28, 65, -13},   // devant la grande roue
            {-30, 65, 1},    // chemin sud-ouest
            {8, 65, -44},    // chemin nord
            {-33, 65, 21},   // palais des glaces
            {30, 65, 16},    // maison hantee
            {30, 70, 26},    // grenier de la maison hantee
            {-32, 65, 48},   // allee du sud-ouest
            {41, 65, 46},    // pied du toboggan
            {-15, 65, 31},   // arriere des stands ouest
            {15, 65, 24},    // arriere des stands est
            {-50, 65, -20},  // roulottes ouest
            {46, 65, -14},   // roulottes est
    };

    static final int[][] GOLD = {
            // allee, place, entree
            {-5, 65, 17}, {5, 65, 33}, {0, 65, 47}, {-9, 65, 3}, {9, 65, 13}, {0, 66, 8}, {-12, 65, 52},
            // stands (arrieres)
            {-14, 65, 21}, {14, 65, 38}, {-14, 65, 44}, {14, 65, 16},
            // chapiteau (piste, gradins)
            {4, 65, -21}, {-5, 65, -25}, {-4, 69, -36}, {-11, 69, -30}, {10, 69, -31},
            // carrousel, diseuse, chaises
            {-28, 66, -30}, {-15, 65, -8}, {16, 65, -15},
            // grande roue (nacelle du bas), chemin nord
            {27, 65, -24}, {-20, 65, -44}, {25, 65, -44},
            // gare des montagnes russes (quai), auto-tamponneuses
            {41, 69, -49}, {-34, 65, -50},
            // palais des glaces
            {-40, 65, 12}, {-27, 65, 27}, {-36, 65, 26},
            // maison hantee (bas, grenier)
            {37, 65, 14}, {25, 65, 28}, {36, 70, 15}, {23, 70, 19},
            // tasses, toboggan
            {-32, 65, 33}, {-38, 65, 45}, {33, 77, 40}, {28, 65, 40},
            // roulottes, pique-nique, bords
            {-50, 65, -32}, {50, 65, -30}, {-48, 65, 40}, {48, 65, 30}, {-41, 65, -12}, {42, 65, -2}, {-21, 65, 14},
    };

    private Fete(Sink s) {
        super(s, 9090);
    }

    static void build(Sink s) {
        new Fete(s).run();
    }

    // ================================================================ palettes

    private static final Palette GRASS = Palette.of(31, "minecraft:grass_block", 80, "minecraft:moss_block", 8,
            "minecraft:podzol", 6, "minecraft:coarse_dirt", 6).period(5);
    private static final Palette PATH = Palette.of(32, "minecraft:dirt_path", 46, "minecraft:coarse_dirt", 22,
            "minecraft:packed_mud", 18, "minecraft:gravel", 14).period(4);
    private static final Palette PAVE = Palette.of(33, "minecraft:stone_bricks", 62, "minecraft:polished_andesite", 22,
            "minecraft:cracked_stone_bricks", 10, "minecraft:mossy_stone_bricks", 6).period(7);
    private static final Palette DECK = Palette.of(34, "minecraft:polished_andesite", 70, "minecraft:andesite", 18,
            "minecraft:polished_tuff", 12).period(6);
    private static final Palette SAWDUST = Palette.of(35, "minecraft:sand", 55, "minecraft:stripped_birch_wood[axis=y]", 30,
            "minecraft:sandstone", 15).period(4);
    private static final Palette PLANKS = Palette.of(36, "minecraft:spruce_planks", 72, "minecraft:dark_oak_planks", 28).period(6);
    private static final Palette FENCEWOOD = Palette.of(37, "minecraft:spruce_planks", 70, "minecraft:stripped_spruce_wood[axis=y]", 18,
            "minecraft:dark_oak_planks", 12).period(5);
    private static final Palette HAUNT = Palette.of(38, "minecraft:deepslate_bricks", 64, "minecraft:cracked_deepslate_bricks", 18,
            "minecraft:deepslate_tiles", 18).period(6);
    private static final Palette HAUNT_UP = Palette.of(39, "minecraft:dark_oak_planks", 70, "minecraft:spruce_planks", 30).period(5);
    private static final Palette ROCK = Palette.of(40, "minecraft:stone", 50, "minecraft:andesite", 18, "minecraft:tuff", 14,
            "minecraft:cobblestone", 10, "minecraft:diorite", 8).period(6);

    // ================================================================ sol : plan des surfaces

    private static final int GRASS_K = 0, PATH_K = 1, PAVE_K = 2, DECK_K = 3, KEEP_K = 9;
    private final int W = 2 * AREA + 1;
    private final byte[] surf = new byte[W * W];
    private final int[] gh = new int[W * W];

    private int si(int x, int z) {
        return (x + AREA) * W + (z + AREA);
    }

    private boolean inArea(int x, int z) {
        return Math.abs(x) <= AREA && Math.abs(z) <= AREA;
    }

    private void paint(int x1, int z1, int x2, int z2, int k) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                if (inArea(x, z)) surf[si(x, z)] = (byte) k;
    }

    private void paintDisc(double cx, double cz, double r, int k) {
        for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++)
            for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++)
                if (inArea(x, z) && Math.hypot(x - cx, z - cz) <= r) surf[si(x, z)] = (byte) k;
    }

    private boolean inBox(int x, int z) {
        return x >= BX1 && x <= BX2 && z >= BZ1 && z <= BZ2;
    }

    /** Bord de l'ile flottante (bruite) : distance de Chebyshev arrondie. */
    private boolean onIsland(int x, int z) {
        double r = Math.pow(Math.pow(Math.abs(x), 5) + Math.pow(Math.abs(z), 5), 0.2);
        double lim = 70 + 2.5 * noise.noise2(x / 14.0, z / 14.0);
        return r <= lim;
    }

    int ground(int x, int z) {
        return inArea(x, z) ? gh[si(x, z)] : G;
    }

    private void planSurfaces() {
        // chemins de la boucle nord, place, allee, ruelles, chemin du sud
        paint(-44, -46, 44, -43, PATH_K);
        paint(-44, -46, -41, 2, PATH_K);
        paint(41, -46, 44, 2, PATH_K);
        paint(-44, -1, 44, 2, PATH_K);
        paint(-7, 12, 7, 50, PATH_K);
        paint(-2, -12, 2, -2, PATH_K);
        paint(-21, 2, -18, 50, PATH_K);
        paint(18, 2, 21, 50, PATH_K);
        paint(-16, 13, -13, 47, PATH_K);
        paint(13, 13, 16, 47, PATH_K);
        paint(-44, 47, 44, 50, PATH_K);
        paint(-44, 2, -41, 50, PATH_K);
        paint(41, 2, 44, 50, PATH_K);
        // anneau autour du chapiteau et acces lateraux
        for (int x = -18; x <= 18; x++)
            for (int z = -43; z <= -7; z++) {
                double d = Math.hypot(x, z + 25);
                if (d > 13.5 && d <= 17) surf[si(x, z)] = PATH_K;
            }
        paint(-40, -26, -35, -22, PATH_K);
        paint(-19, -26, -17, -22, PATH_K);
        paint(17, -26, 19, -22, PATH_K);
        paint(-27, -16, -26, -2, PATH_K);
        paint(26, -18, 28, -2, PATH_K);
        paint(-12, -2, -10, -6, PATH_K);
        // place, entree, ponton des attractions
        paintDisc(0, 8, 10.5, PAVE_K);
        paint(-12, 50, 12, 56, PAVE_K);
        paintDisc(-27, -24, 9.5, PATH_K);
        paint(19, -30, 35, -17, DECK_K);
        paintDisc(16, -9, 7.5, DECK_K);
        paint(-50, -54, -30, -47, KEEP_K);
        paint(30, -54, 53, -47, PATH_K);
        paintDisc(-32, 39, 7.5, KEEP_K);
        paint(29, 36, 37, 44, PATH_K);
        // sous les batiments (pose par les modules)
        paint(-44, 10, -24, 30, KEEP_K);
        paint(22, 12, 40, 30, KEEP_K);
        paintDisc(0, -25, 13.5, KEEP_K);
        // hauteurs : buttes douces hors des chemins (dans la boite), collines hors de la boite
        int[][] mounds = {{-50, 46, 6, 2}, {50, 42, 6, 2}, {-50, 10, 5, 1}, {50, 8, 5, 1}, {-30, 8, 4, 1}, {10, 24, 0, 0},
                {-6, -50, 4, 1}, {14, -50, 4, 1}, {-36, -36, 4, 1}, {35, -36, 4, 1}, {-27, 30, 3, 1}, {48, -42, 4, 1}};
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                int i = si(x, z);
                double hh = 0;
                if (!inBox(x, z)) {
                    int d = Math.max(Math.max(BX1 - x, x - BX2), Math.max(BZ1 - z, z - BZ2));
                    hh = Math.min(4, d * 0.3 + 1.4 * (noise.fbm2(x / 16.0, z / 16.0, 3, 0.5, 2) + 0.3));
                    if (hh < 0) hh = 0;
                } else if (surf[i] == GRASS_K) {
                    for (int[] m : mounds) {
                        if (m[2] == 0) continue;
                        double d = Math.hypot(x - m[0], z - m[1]) / m[2];
                        if (d < 1) hh = Math.max(hh, m[3] * (0.5 + 0.5 * Math.cos(Math.PI * d)) + 0.2 * noise.noise2(x / 5.0, z / 5.0));
                    }
                }
                gh[i] = G + (int) Math.round(hh);
            }
        // une butte ne touche jamais un chemin : bord a G a un bloc des chemins
        for (int x = -AREA + 1; x < AREA; x++)
            for (int z = -AREA + 1; z < AREA; z++) {
                if (!inBox(x, z) || gh[si(x, z)] == G) continue;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (surf[si(x + dx, z + dz)] != GRASS_K) gh[si(x, z)] = G;
            }
    }

    // ================================================================ deroule

    private void run() {
        planSurfaces();
        terrain();
        bigTop();
        carousel();
        ferrisWheel();
        swings();
        fortuneTeller();
        bumperCars();
        coasterStation();
        coasterTrack();
        mirrorPalace();
        hauntedHouse();
        teacups();
        helterSkelter();
        bandstand();
        boothRows();
        entrance();
        caravans();
        props();
        boundary();
        lamps();
        garlands();
        trees();
        vegetation();
        finish(-AREA, G - 1, -AREA, AREA, Y_HI - 1, AREA);
    }

    // ================================================================ terrain

    private void terrain() {
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                if (!onIsland(x, z) && !inBox(x, z)) continue;
                int top = ground(x, z);
                // dessous de l'ile : cone renverse bruite
                double edge = 72 - Math.pow(Math.pow(Math.abs(x), 5) + Math.pow(Math.abs(z), 5), 0.2);
                int depth = 5 + (int) Math.min(14, Math.max(0, edge) * 0.45 + 3 * noise.noise2(x / 6.0, z / 6.0));
                int bottom = Math.max(Y_LO + 1, G - depth);
                for (int y = bottom; y <= top; y++) {
                    String b;
                    if (y == top) b = topBlock(x, z, y);
                    else if (y >= top - 2) b = h(x, y, z) < 0.5 ? "minecraft:dirt" : "minecraft:coarse_dirt";
                    else b = ROCK.at(x, y, z);
                    set(x, y, z, b);
                }
                // stalactites sous le bord
                if (edge < 6 && h(x, 3, z) < 0.25) {
                    int len = 1 + (int) (h(x, 4, z) * 4);
                    for (int y = bottom - 1; y >= bottom - len && y > Y_LO; y--) set(x, y, z, ROCK.at(x, y, z));
                }
            }
    }

    private String topBlock(int x, int z, int y) {
        if (!inArea(x, z)) return GRASS.at(x, y, z);
        switch (surf[si(x, z)]) {
            case PATH_K: return PATH.at(x, y, z);
            case PAVE_K: return PAVE.at(x, y, z);
            case DECK_K: return DECK.at(x, y, z);
            default: return GRASS.at(x, y, z);
        }
    }

    // ================================================================ chapiteau

    /**
     * Toit conique a bandes : profil (1 - r/R)^p (rives plates, sommet pointu), laine + tapis pour les demi-pas,
     * couleurs par secteur angulaire. Coque d'un bloc (dessous creux).
     */
    private void cone(double cx, double cz, double rBase, int yBase, double height, double p, String[] colors, int sectors) {
        for (int x = (int) Math.floor(cx - rBase); x <= (int) Math.ceil(cx + rBase); x++)
            for (int z = (int) Math.floor(cz - rBase); z <= (int) Math.ceil(cz + rBase); z++) {
                double r = Math.hypot(x - cx, z - cz);
                if (r > rBase) continue;
                double hr = yBase + height * Math.pow(1 - r / rBase, p);
                double ho = yBase + height * Math.pow(Math.max(0, 1 - Math.min(rBase, r + 1) / rBase), p);
                int top = (int) Math.floor(hr), low = Math.max(yBase, (int) Math.floor(ho));
                String c = colors[(int) Math.floor(angle01(x - cx, z - cz) * sectors) % colors.length];
                for (int y = low; y <= top; y++) set(x, y, z, "minecraft:" + c + "_wool");
                if (hr - top >= 0.5 && isAir(x, top + 1, z)) set(x, top + 1, z, "minecraft:" + c + "_carpet");
            }
    }

    /** Lambrequin festonne autour d'un toit rond : un bloc sur deux pend d'un rang de plus. */
    private void valance(double cx, double cz, double r0, double r1, int y, String[] colors) {
        for (int x = (int) Math.floor(cx - r1); x <= (int) Math.ceil(cx + r1); x++)
            for (int z = (int) Math.floor(cz - r1); z <= (int) Math.ceil(cz + r1); z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= r0 || d > r1) continue;
                String c = colors[Math.floorMod(x + z, colors.length)];
                set(x, y, z, "minecraft:" + c + "_wool");
                if (Math.floorMod(x + z, 2) == 0 && isAir(x, y - 1, z)) set(x, y - 1, z, "minecraft:" + c + "_wool");
            }
    }

    private void bigTop() {
        final int cx = 0, cz = -25;
        final double R = 13.5;
        String[] canvas = {"red", "white"};
        // sol : piste en sciure, bordure, allee, gradins
        for (int x = -14; x <= 14; x++)
            for (int z = cz - 14; z <= cz + 14; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > R) continue;
                if (d <= 6) set(x, G, z, SAWDUST.at(x, G, z));
                else if (d <= 7) set(x, G, z, "minecraft:red_concrete");
                else set(x, G, z, PLANKS.at(x, G, z));
            }
        // gradins : marches de 1 de d 8.5 a 12.5, couloirs vers les 4 entrees
        for (int x = -13; x <= 13; x++)
            for (int z = cz - 13; z <= cz + 13; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= 8.5 || d > 12.5) continue;
                int dx = x - cx, dz = z - cz;
                if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) continue; // couloirs N S E O
                int L = Math.min(4, (int) Math.ceil(d - 8.5));
                Dir out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Dir.EAST : Dir.WEST) : (dz > 0 ? Dir.SOUTH : Dir.NORTH);
                for (int y = G + 1; y < G + L; y++) set(x, y, z, "minecraft:dark_oak_planks");
                // 1.9.1 : toujours une marche (plus de planches pleines au milieu des rangs : il fallait sauter) ;
                // le dernier rang s'adosse a la toile
                String seat = Math.floorMod(x * 3 + z, 5) == 0 ? "minecraft:dark_oak_stairs"
                        : L % 2 == 0 ? "minecraft:red_nether_brick_stairs" : "minecraft:spruce_stairs";
                set(x, G + L, z, stairs(seat, out, false));
            }
        // bordure de piste : dalles de quartz avec 4 ouvertures
        for (int x = -7; x <= 7; x++)
            for (int z = cz - 7; z <= cz + 7; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= 6 || d > 7) continue;
                if (Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1) continue;
                set(x, G + 1, z, Math.floorMod(x + z, 4) == 0 ? "minecraft:yellow_concrete" : slab("minecraft:smooth_quartz_slab", "bottom"));
            }
        // mur de toile (bandes verticales) avec 4 portes de 3
        for (int x = -14; x <= 14; x++)
            for (int z = cz - 14; z <= cz + 14; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= R - 1 || d > R) continue;
                int dx = x - cx, dz = z - cz;
                boolean door = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
                String c = canvas[(int) Math.floor(angle01(dx, dz) * 40) % 2];
                for (int y = G + 1; y <= 70; y++) {
                    if (door && y <= G + 3) { set(x, y, z, B.AIR); continue; }
                    set(x, y, z, y == G + 1 ? "minecraft:" + c + "_terracotta" : "minecraft:" + c + "_wool");
                }
            }
        // toit et lambrequin
        cone(cx, cz, R + 0.6, 71, 15, 1.3, canvas, 40);
        valance(cx, cz, R + 0.6, R + 1.6, 70, new String[]{"yellow", "red"});
        // mat central sur un podium, drapeau
        disc(cx, cz, G + 1, 2.2, Mat.of("minecraft:yellow_concrete"));
        ring(cx, cz, G + 1, 1.5, 2.2, Mat.of("minecraft:red_concrete"));
        for (int y = G + 2; y <= 90; y++) set(cx, y, cz, y % 6 == 0 ? "minecraft:red_concrete" : log("minecraft:stripped_birch_log", 'y'));
        set(cx, 91, cz, standing("minecraft:lantern"));
        for (int y = 87; y <= 89; y++) for (int i = 1; i <= 3; i++) set(cx + i, y, cz, y == 88 ? "minecraft:yellow_wool" : "minecraft:red_wool");
        // ballon d'equilibriste et tabourets d'animaux
        Shapes.sphere(s, cx + 4, G + 2, cz + 2, 1.2, (x, y, z) -> (y + x) % 2 == 0 ? "minecraft:blue_concrete" : "minecraft:yellow_concrete");
        for (int[] t : new int[][]{{-4, -3}, {3, -4}, {-3, 4}}) {
            set(cx + t[0], G + 1, cz + t[1], "minecraft:red_terracotta");
            set(cx + t[0], G + 2, cz + t[1], slab("minecraft:smooth_quartz_slab", "bottom"));
        }
        // trapeze et filet (decor hors d'atteinte)
        for (int sx : new int[]{-5, 5}) {
            fill(sx - 1, 79, cz - 1, sx, 79, cz, slab("minecraft:spruce_slab", "top"));
            for (int y = 80; y <= 83; y++) set(sx, y, cz, chain('y'));
        }
        for (int x = -3; x <= 3; x++) set(x, 78, cz, x == -3 || x == 3 ? chain('y') : "minecraft:iron_bars");
        // 1.9.1 : les chaines du filet montent jusqu'a la toile (elles s'arretaient dans le vide)
        for (int x : new int[]{-3, 3}) for (int y = 79; y < 95 && isAir(x, y, cz); y++) set(x, y, cz, chain('y'));
        for (int x = -6; x <= 6; x++) for (int z = cz - 2; z <= cz + 2; z++) if ((x + z) % 2 == 0) set(x, 74, z, "minecraft:cobweb");
        // lumieres : lanternes suspendues au toit, projecteurs autour de la piste
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = cx + (int) Math.round(10 * Math.sin(a)), z = cz - (int) Math.round(10 * Math.cos(a));
            // 1.9.1 : la chaine monte jusqu'a la toile, quelle que soit sa hauteur a cet endroit
            for (int y = 73; y < 95 && isAir(x, y, z); y++) set(x, y, z, chain('y'));
            set(x, 72, z, hanging("minecraft:lantern"));
        }
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            int x = cx + (int) Math.round(7.5 * Math.sin(a)), z = cz - (int) Math.round(7.5 * Math.cos(a));
            if (Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1) continue;
            set(x, G, z, "minecraft:sea_lantern");
        }
        // auvents d'entree rayes (4)
        for (Dir d : Dir.values()) entranceCanopy(cx, cz, d);
    }

    private void entranceCanopy(int cx, int cz, Dir d) {
        Dir t = d.cw();
        for (int k = 13; k <= 16; k++)
            for (int i = -2; i <= 2; i++) {
                int x = cx + d.dx * k + t.dx * i, z = cz + d.dz * k + t.dz * i;
                int y = k <= 14 ? 70 : 69;
                set(x, y, z, "minecraft:" + ((i + 10) % 2 == 0 ? "red" : "white") + "_wool");
                if (k == 16) set(x, y - 1, z, "minecraft:" + ((i + 10) % 2 == 0 ? "yellow" : "red") + "_wool");
                if (k == 16 && Math.abs(i) == 2) {
                    for (int yy = G + 1; yy <= 67; yy++) set(x, yy, z, yy % 2 == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete");
                }
            }
        set(cx + d.dx * 15, 68, cz + d.dz * 15, hanging("minecraft:lantern"));
        // panneau « CIRQUE » : bandeau de terre cuite emaillee au-dessus de la porte
        for (int i = -1; i <= 1; i++) set(cx + d.dx * 13 + t.dx * i, 69, cz + d.dz * 13 + t.dz * i, facing("minecraft:yellow_glazed_terracotta", d));
    }

    // ================================================================ carrousel

    private void carousel() {
        final int cx = -27, cz = -24;
        // plancher : marche en dalles puis disque plein (pieds en 66), secteurs bois clair / sombre
        for (int x = cx - 8; x <= cx + 8; x++)
            for (int z = cz - 8; z <= cz + 8; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 7.5) continue;
                if (d > 6.5) set(x, G + 1, z, slab("minecraft:dark_oak_slab", "bottom"));
                else set(x, G + 1, z, (int) (angle01(x - cx, z - cz) * 16) % 2 == 0 ? "minecraft:birch_planks" : "minecraft:stripped_dark_oak_wood[axis=y]");
            }
        // tambour central a miroirs
        for (int y = G + 2; y <= 71; y++)
            for (int x = cx - 2; x <= cx + 2; x++)
                for (int z = cz - 2; z <= cz + 2; z++) {
                    double d = Math.hypot(x - cx, z - cz);
                    if (d > 1.9) continue;
                    boolean shell = d > 0.9;
                    if (!shell) set(x, y, z, "minecraft:glowstone");
                    else if (y == G + 2 || y == 71) set(x, y, z, "minecraft:gold_block");
                    else if (y == 70) set(x, y, z, "minecraft:ochre_froglight");
                    else set(x, y, z, (x + z) % 2 == 0 ? "minecraft:light_blue_stained_glass" : "minecraft:smooth_quartz");
                }
        // chevaux sur deux cercles
        String[] coats = {"white", "brown", "black", "light_gray"};
        String[] saddles = {"red", "blue", "purple", "lime", "orange", "magenta"};
        int n = 0;
        List<int[]> poles = new ArrayList<>();
        for (double[] ring : new double[][]{{3.6, 8, 0}, {5.6, 12, 0.5}}) {
            for (int k = 0; k < (int) ring[1]; k++) {
                double a = (k + ring[2]) * 2 * Math.PI / ring[1];
                int x = cx + (int) Math.round(ring[0] * Math.sin(a)), z = cz - (int) Math.round(ring[0] * Math.cos(a));
                Dir tan = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? Dir.EAST : Dir.WEST) : (Math.sin(a) > 0 ? Dir.SOUTH : Dir.NORTH);
                String coat = "minecraft:" + coats[n % coats.length] + "_wool";
                set(x, G + 2, z, coat);
                set(x + tan.dx, G + 2, z + tan.dz, coat);
                set(x + tan.dx, G + 3, z + tan.dz, stairs("minecraft:" + (coats[n % coats.length].equals("brown") ? "dark_oak" : "birch") + "_stairs", tan.opposite(), false));
                set(x, G + 3, z, "minecraft:" + saddles[n % saddles.length] + "_carpet");
                poles.add(new int[]{x, z});
                n++;
            }
        }
        // toit conique bleu ciel / blanc, lambrequin, lanternes
        String[] roof = {"light_blue", "white"};
        cone(cx, cz, 8.5, 72, 6, 1.0, roof, 16);
        valance(cx, cz, 7.5, 8.5, 71, new String[]{"yellow", "light_blue"});
        // barres des chevaux : chaine jusqu'a la toile du toit (1.9.1 : elle s'arretait 2 blocs en dessous)
        for (int[] p : poles)
            for (int y = G + 4; y < 90 && (y <= 71 || isAir(p[0], y, p[1])); y++) set(p[0], y, p[1], chain('y'));
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6 + 0.26;
            int x = cx + (int) Math.round(6.9 * Math.sin(a)), z = cz - (int) Math.round(6.9 * Math.cos(a));
            if (isAir(x, 71, z)) set(x, 71, z, hanging("minecraft:lantern"));
        }
        set(cx, 79, cz, "minecraft:gold_block");
        set(cx, 80, cz, standing("minecraft:lantern"));
        // orgue de barbarie au sud du manege
        int ox = cx + 1, oz = cz + 9;
        fill(ox - 1, G + 1, oz, ox + 1, G + 2, oz, "minecraft:red_terracotta");
        set(ox, G + 2, oz, "minecraft:note_block");
        for (int i = -1; i <= 1; i++) set(ox + i, G + 3, oz, stairs("minecraft:smooth_quartz_stairs", Dir.NORTH, false));
        set(ox - 1, G + 1, oz + 1, trap("minecraft:dark_oak_trapdoor", Dir.SOUTH, false, true));
        set(ox + 1, G + 1, oz + 1, trap("minecraft:dark_oak_trapdoor", Dir.SOUTH, false, true));
    }

    // ================================================================ grande roue

    private void ferrisWheel() {
        final int cx = 27, hy = 80, zc = -24;
        final double R = 11;
        // pietements en A (plans z -27 et -21), axe
        for (int zz : new int[]{zc - 3, zc + 3}) {
            Shapes.line(s, cx - 8, G + 1, zz, cx, hy, zz, Mat.of("minecraft:white_concrete"));
            Shapes.line(s, cx + 8, G + 1, zz, cx, hy, zz, Mat.of("minecraft:white_concrete"));
            Shapes.line(s, cx - 8, G + 1, zz, cx - 7, G + 1, zz, Mat.of("minecraft:polished_andesite"));
            for (int x = cx - 5; x <= cx + 5; x++) set(x, 71, zz, "minecraft:iron_bars");
            for (int x = cx - 8; x <= cx + 8; x++) if (Math.abs(x - cx) == 8 || Math.abs(x - cx) == 7) set(x, G, zz, "minecraft:polished_andesite");
        }
        for (int z = zc - 3; z <= zc + 3; z++) set(cx, hy, z, "minecraft:iron_block");
        set(cx, hy, zc - 1, "minecraft:sea_lantern");
        set(cx, hy, zc + 1, "minecraft:sea_lantern");
        // jantes (plans z -26 et -22), ampoules
        for (int zz : new int[]{zc - 2, zc + 2}) {
            int step = 0;
            for (double a = 0; a < 2 * Math.PI; a += 0.01) {
                int x = cx + (int) Math.round(R * Math.cos(a)), y = hy + (int) Math.round(R * Math.sin(a));
                if (!isAir(x, y, zz) && !get(x, y, zz).contains("iron_bars")) continue;
                step++;
                set(x, y, zz, step % 5 == 0 ? (step % 10 == 0 ? "minecraft:pearlescent_froglight" : "minecraft:ochre_froglight") : "minecraft:white_concrete");
            }
            // rayons
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                Shapes.line(s, cx, hy, zz, cx + (int) Math.round((R - 1) * Math.cos(a)), hy + (int) Math.round((R - 1) * Math.sin(a)), zz,
                        (x, y, z) -> "minecraft:iron_bars");
            }
            set(cx, hy, zz, "minecraft:gold_block");
        }
        // 8 nacelles (une au sol, ouverte)
        String[] cols = {"red", "yellow", "lime", "light_blue", "orange", "magenta", "cyan", "pink"};
        for (int k = 0; k < 8; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 4;
            int ax = cx + (int) Math.round(R * Math.cos(a)), ay = hy + (int) Math.round(R * Math.sin(a));
            for (int z = zc - 1; z <= zc + 1; z++) set(ax, ay, z, chain('z'));
            gondola(ax, ay - 1, zc, cols[k]);
        }
        // quai d'embarquement : barrieres de file, cabine, guichet
        for (int x = cx - 6; x <= cx + 6; x++) {
            if (Math.abs(x - cx) <= 1) continue;
            setIfAir(x, G + 1, zc + 5, "minecraft:spruce_fence");
            if (Math.abs(x - cx) >= 3 && Math.abs(x - cx) <= 5) setIfAir(x, G + 1, zc + 7, "minecraft:spruce_fence");
        }
        booth(cx + 6, zc + 9, Dir.WEST, 0, new String[]{"red", "white"}, 3);
        // cabine du forain (levier)
        fill(cx - 8, G + 1, zc + 7, cx - 7, G + 1, zc + 8, "minecraft:white_terracotta");
        set(cx - 8, G + 2, zc + 7, "minecraft:lever[face=floor,facing=east,powered=false]");
        set(cx - 7, G + 2, zc + 8, standing("minecraft:lantern"));
    }

    /** Nacelle : toit a (x, yTop, zc), 3 x 3, cabine ouverte de 3 de haut, garde-corps bas, banquettes. */
    private void gondola(int x, int yTop, int zc, String col) {
        int floor = yTop - 4;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                set(x + dx, yTop, zc + dz, "minecraft:" + col + "_concrete");
                set(x + dx, floor, zc + dz, floor <= G ? "minecraft:" + col + "_concrete" : "minecraft:" + col + "_terracotta");
            }
        set(x, yTop + 0, zc, "minecraft:" + col + "_concrete");
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}})
            for (int y = floor + 1; y < yTop; y++) set(x + c[0], y, zc + c[1], "minecraft:iron_bars");
        set(x, floor + 1, zc - 1, stairs("minecraft:spruce_stairs", Dir.NORTH, false));
        set(x - 1, floor + 1, zc, "minecraft:" + col + "_stained_glass_pane");
        set(x + 1, floor + 1, zc, "minecraft:" + col + "_stained_glass_pane");
        set(x, yTop - 1, zc, hanging("minecraft:lantern"));
    }

    // ================================================================ chaises volantes

    private void swings() {
        final int cx = 16, cz = -9;
        // socle et barriere avec 2 ouvertures
        disc(cx, cz, G + 1, 2.2, Mat.of("minecraft:yellow_terracotta"));
        ring(cx, cz, G + 1, 1.5, 2.2, Mat.of("minecraft:orange_terracotta"));
        for (int x = cx - 8; x <= cx + 8; x++)
            for (int z = cz - 8; z <= cz + 8; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= 6.6 || d > 7.4) continue;
                if (Math.abs(x - cx) <= 1 && z > cz) continue;
                if (Math.abs(z - cz) <= 1 && x < cx) continue;
                set(x, G + 1, z, "minecraft:birch_fence");
            }
        // mat a bandes
        for (int y = G + 2; y <= 77; y++) {
            String c = (y / 2) % 2 == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete";
            set(cx, y, cz, c);
            if (y <= 72) for (Dir d : Dir.values()) set(cx + d.dx, y, cz + d.dz, y % 3 == 0 ? "minecraft:yellow_concrete" : c);
        }
        // parapluie : disque + cone, lambrequin
        String[] um = {"red", "yellow"};
        cone(cx, cz, 5.5, 77, 3, 1.0, um, 12);
        valance(cx, cz, 4.5, 5.6, 76, new String[]{"white", "red"});
        set(cx, 81, cz, "minecraft:gold_block");
        set(cx, 82, cz, standing("minecraft:lantern"));
        // 10 chaines + sieges (hors d'atteinte, y 69)
        for (int k = 0; k < 10; k++) {
            double a = k * 2 * Math.PI / 10;
            int x = cx + (int) Math.round(5 * Math.sin(a)), z = cz - (int) Math.round(5 * Math.cos(a));
            for (int y = 70; y <= 75; y++) set(x, y, z, chain('y'));
            Dir out = Math.abs(Math.sin(a)) > Math.abs(Math.cos(a)) ? (Math.sin(a) > 0 ? Dir.EAST : Dir.WEST) : (Math.cos(a) > 0 ? Dir.NORTH : Dir.SOUTH);
            set(x, 69, z, stairs("minecraft:" + (k % 2 == 0 ? "red_nether_brick" : "spruce") + "_stairs", out, false));
        }
    }

    // ================================================================ diseuse de bonne aventure

    private void fortuneTeller() {
        final int cx = -15, cz = -8;
        for (int x = cx - 4; x <= cx + 4; x++)
            for (int z = cz - 4; z <= cz + 4; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 3.5) continue;
                set(x, G, z, "minecraft:dark_oak_planks");
                if (d > 2.5) {
                    boolean door = z == cz && Math.abs(x - cx) >= 3;
                    for (int y = G + 1; y <= 67; y++) {
                        if (door && y <= G + 2) { set(x, y, z, B.AIR); continue; }
                        set(x, y, z, (int) (angle01(x - cx, z - cz) * 12) % 2 == 0 ? "minecraft:purple_wool" : "minecraft:magenta_wool");
                    }
                } else if (d <= 2.2) set(x, G + 1, z, h(x, 1, z) < 0.5 ? "minecraft:purple_carpet" : "minecraft:magenta_carpet");
            }
        cone(cx, cz, 4.3, 68, 6, 1.1, new String[]{"purple", "magenta"}, 12);
        valance(cx, cz, 3.5, 4.4, 67, new String[]{"yellow", "purple"});
        set(cx, 75, cz, "minecraft:gold_block");
        // table, boule de cristal, chaises, bougies
        set(cx, G + 1, cz - 1, "minecraft:polished_deepslate");
        set(cx, G + 2, cz - 1, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        set(cx - 1, G + 1, cz - 1, stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
        set(cx + 1, G + 1, cz - 1, stairs("minecraft:dark_oak_stairs", Dir.EAST, false));
        set(cx, G + 1, cz + 2, "minecraft:cauldron");
        set(cx - 2, G + 1, cz + 1, "minecraft:bookshelf");
        set(cx - 2, G + 2, cz + 1, candles(3, "purple"));
        set(cx + 2, G + 1, cz - 2, "minecraft:chiseled_bookshelf[facing=west,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]");
        set(cx + 2, G + 2, cz - 2, candles(2, "white"));
        set(cx, 70, cz, hanging("minecraft:soul_lantern"));
        set(cx, 71, cz, chain('y'));
        // enseigne : main sur une barriere devant la porte est
        set(cx + 5, G + 1, cz + 1, "minecraft:dark_oak_fence");
        set(cx + 5, G + 2, cz + 1, "minecraft:dark_oak_fence");
        set(cx + 5, G + 3, cz + 1, "minecraft:purple_glazed_terracotta[facing=east]");
    }

    // ================================================================ auto-tamponneuses

    private void bumperCars() {
        final int x1 = -50, x2 = -30, z1 = -54, z2 = -47;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, G, z, (x + z) % 2 == 0 ? "minecraft:gray_concrete" : "minecraft:light_gray_concrete");
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                boolean gap = z == z2 && (Math.abs(x + 44) <= 1 || Math.abs(x + 36) <= 1);
                if (rim && !gap) set(x, G + 1, z, "minecraft:black_concrete");
            }
        // poteaux, toit plat a bord lumineux, grillage du plafond
        for (int x = x1; x <= x2; x += 5)
            for (int z : new int[]{z1, z2})
                for (int y = G + 1; y <= 69; y++) set(x, y, z, y == 69 ? "minecraft:yellow_concrete" : log("minecraft:stripped_birch_log", 'y'));
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean edge = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
                if (edge) {
                    set(x, 70, z, Math.floorMod(x + z, 3) == 0 ? "minecraft:sea_lantern" : "minecraft:red_concrete");
                    set(x, 71, z, Math.floorMod(x + z, 2) == 0 ? "minecraft:white_concrete" : "minecraft:red_concrete");
                } else {
                    set(x, 71, z, slab("minecraft:smooth_quartz_slab", "bottom"));
                    if ((x + z) % 2 == 0) set(x, 70, z, "minecraft:iron_bars");
                }
            }
        // voitures (2 x 3) avec perche et volant
        String[] cols = {"red", "blue", "lime", "yellow", "magenta", "orange"};
        int[][] cars = {{-47, -52, 0}, {-42, -50, 1}, {-37, -52, 2}, {-33, -49, 3}, {-45, -49, 1}, {-39, -49, 0}};
        for (int i = 0; i < cars.length; i++) {
            int x = cars[i][0], z = cars[i][1];
            boolean alongX = cars[i][2] % 2 == 0;
            String c = "minecraft:" + cols[i] + "_concrete";
            for (int k = 0; k < 3; k++) {
                int xx = alongX ? x + k : x, zz = alongX ? z : z + k;
                if (k == 1) set(xx, G + 1, zz, stairs("minecraft:quartz_stairs", alongX ? Dir.WEST : Dir.NORTH, false));
                else set(xx, G + 1, zz, c);
            }
            int fx = alongX ? x + 2 : x, fz = alongX ? z : z + 2;
            set(fx, G + 2, fz, "minecraft:lever[face=floor,facing=" + (alongX ? "east" : "south") + ",powered=false]");
            for (int y = G + 2; y <= 69; y++) set(x, y, z, chain('y'));
        }
        // guichet et enseigne
        booth(-26, -50, Dir.EAST, 0, new String[]{"blue", "white"}, 2);
    }

    // ================================================================ montagnes russes

    private void coasterStation() {
        final int x1 = 33, x2 = 49, z1 = -53, z2 = -48;
        // quai (dessus en y 68, pieds en 69) sur piliers, voie, rampe et escaliers
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, 68, z, z >= z1 + 1 && z <= z1 + 2 ? "minecraft:dark_oak_planks" : PLANKS.at(x, 68, z));
                boolean pillar = (x - x1) % 4 == 0 && (z == z1 || z == z2);
                for (int y = G + 1; y < 68; y++) {
                    if (pillar) set(x, y, z, log("minecraft:stripped_spruce_log", 'y'));
                    else if (z == z1 && y >= 66) set(x, y, z, "minecraft:spruce_fence");
                }
            }
        // garde-corps cote sud sauf aux escaliers
        for (int x = x1; x <= x2; x++) set(x, 69, z2, "minecraft:spruce_fence");
        set(x1, 69, z2, B.AIR);
        set(x2, 69, z2, B.AIR);
        // escaliers : ouest (monte vers l'est) et est (monte vers l'ouest), 2 de large
        for (int k = 0; k < 4; k++) {
            for (int z = z2 - 1; z <= z2; z++) {
                int xw = x1 - 4 + k, xe = x2 + 4 - k;
                for (int y = G + 1; y < G + 1 + k; y++) { set(xw, y, z, "minecraft:spruce_planks"); set(xe, y, z, "minecraft:spruce_planks"); }
                set(xw, G + 1 + k, z, stairs("minecraft:spruce_stairs", Dir.EAST, false));
                set(xe, G + 1 + k, z, stairs("minecraft:spruce_stairs", Dir.WEST, false));
            }
        }
        // rails sur le quai et train de 3 voitures
        for (int x = x1 - 4; x <= x2 + 6; x++) {
            if (x < x1 || x > x2) { set(x, 68, z1 + 1, "minecraft:dark_oak_planks"); set(x, 68, z1 + 2, "minecraft:dark_oak_planks"); }
            set(x, 69, z1 + 1, "minecraft:rail[shape=east_west,waterlogged=false]");
            set(x, 69, z1 + 2, "minecraft:rail[shape=east_west,waterlogged=false]");
        }
        String[] cols = {"red", "yellow", "red"};
        for (int i = 0; i < 3; i++) {
            int x = 37 + i * 4;
            for (int dx = 0; dx <= 2; dx++)
                for (int z = z1 + 1; z <= z1 + 2; z++)
                    set(x + dx, 69, z, dx == 1 ? stairs("minecraft:quartz_stairs", Dir.EAST, false) : "minecraft:" + cols[i] + "_concrete");
            set(x + 2, 70, z1 + 1, "minecraft:iron_bars");
            set(x + 2, 70, z1 + 2, "minecraft:iron_bars");
        }
        // toit de la gare : poteaux, toit en dents de scie raye
        for (int x = x1; x <= x2; x += 4)
            for (int z : new int[]{z1, z2})
                for (int y = 69; y <= 72; y++) if (!(z == z2 && (x == x1 || x == x2) && y <= 70)) set(x, y, z, log("minecraft:stripped_spruce_log", 'y'));
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                int y = 73 + (z - z1 + 1) / 3;
                set(x, y, z, Math.floorMod(x, 4) < 2 ? "minecraft:red_wool" : "minecraft:white_wool");
                if (z == z2 + 1) set(x, y - 1, z, Math.floorMod(x, 2) == 0 ? "minecraft:yellow_wool" : B.AIR);
            }
        for (int x = x1 + 2; x <= x2; x += 4) { set(x, 73, (z1 + z2) / 2, chain('y')); set(x, 72, (z1 + z2) / 2, hanging("minecraft:lantern")); }
        // enseigne sur le toit
        for (int x = 38; x <= 44; x++) set(x, 76, z1, facing(x % 2 == 0 ? "minecraft:red_glazed_terracotta" : "minecraft:yellow_glazed_terracotta", Dir.SOUTH));
    }

    /** Voie decorative hors de la boite (montee, chute, looping), sur poteaux blancs. */
    private void coasterTrack() {
        int[][] pts = {{56, 69, -52}, {62, 69, -54}, {66, 69, -59}, {66, 84, -68}, {40, 84, -68}, {24, 70, -68}, {10, 68, -68},
                {-10, 68, -68}, {-28, 76, -66}, {-46, 70, -66}, {-62, 72, -62}, {-66, 74, -50}, {-66, 70, -36}, {-62, 69, -30}};
        for (int i = 0; i + 1 < pts.length; i++) track(pts[i], pts[i + 1]);
        // depart cote ouest de la gare jusqu'a la palissade
        for (int x = 29; x <= 32; x++) { set(x, 68, -52, "minecraft:dark_oak_planks"); set(x, 68, -51, "minecraft:dark_oak_planks"); }
        // looping au centre nord (plan x-y, z -68/-67)
        for (int zz : new int[]{-69, -67}) {
            for (double a = 0; a < 2 * Math.PI; a += 0.02) {
                int x = (int) Math.round(8 * Math.cos(a)), y = 77 + (int) Math.round(8 * Math.sin(a));
                set(x, y, zz, "minecraft:red_concrete");
            }
        }
        for (int y = 64; y <= 68; y++) for (int x : new int[]{-1, 1}) set(x, y, -68, "minecraft:white_concrete");
        // lumieres le long du looping
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            set((int) Math.round(8.6 * Math.cos(a)), 77 + (int) Math.round(8.6 * Math.sin(a)), -68, "minecraft:ochre_froglight");
        }
    }

    private void track(int[] a, int[] b) {
        int n = Math.max(Math.abs(b[0] - a[0]), Math.max(Math.abs(b[1] - a[1]), Math.abs(b[2] - a[2])));
        boolean alongX = Math.abs(b[0] - a[0]) >= Math.abs(b[2] - a[2]);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) Math.max(1, n);
            int x = (int) Math.round(a[0] + (b[0] - a[0]) * t), y = (int) Math.round(a[1] + (b[1] - a[1]) * t), z = (int) Math.round(a[2] + (b[2] - a[2]) * t);
            if (inBox(x, z)) continue;
            int ox = alongX ? 0 : 1, oz = alongX ? 1 : 0;
            set(x, y, z, "minecraft:red_concrete");
            set(x + ox, y, z + oz, "minecraft:red_concrete");
            if (isAir(x, y + 1, z)) set(x, y + 1, z, (i % 3 == 0 ? "minecraft:iron_bars" : B.AIR));
            if (i % 5 == 0) {
                int g = onIsland(x, z) ? ground(x, z) : Y_LO + 8;
                for (int yy = g + 1; yy < y; yy++) {
                    set(x, yy, z, yy % 4 == 0 ? "minecraft:light_gray_concrete" : "minecraft:white_concrete");
                    if ((yy - g) % 6 == 3) set(x + ox, yy, z + oz, "minecraft:iron_bars");
                }
            }
        }
    }

    // ================================================================ palais des glaces

    private void mirrorPalace() {
        final int x1 = -44, x2 = -24, z1 = 10, z2 = 30;
        // sol en damier, plafond sombre a lumieres, murs
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, G, z, (x + z) % 2 == 0 ? "minecraft:black_concrete" : "minecraft:white_concrete");
                set(x, 68, z, (Math.floorMod(x + 43, 3) == 2 && Math.floorMod(z - 11, 3) == 2) ? "minecraft:sea_lantern" : "minecraft:black_concrete");
                for (int y = 69; y <= 71; y++) set(x, y, z, "minecraft:smooth_quartz");
                set(x, 72, z, slab("minecraft:smooth_quartz_slab", "bottom"));
            }
        Mat wallMat = (x, y, z) -> y == G + 1 ? "minecraft:polished_blackstone_bricks" : (y % 4 == 0 ? "minecraft:light_blue_terracotta" : "minecraft:white_terracotta");
        for (int y = G + 1; y <= 71; y++) {
            for (int x = x1; x <= x2; x++) { set(x, y, z1, wallMat); set(x, y, z2, wallMat); }
            for (int z = z1; z <= z2; z++) { set(x1, y, z, wallMat); set(x2, y, z, wallMat); }
        }
        // pilastres dores en saillie, corniche, acrotere a creneaux, tourelles d'angle a coupoles
        for (int z = z1; z <= z2; z += 4) pilaster(x2 + 1, z, Dir.EAST);
        for (int z = z1; z <= z2; z += 5) pilaster(x1 - 1, z, Dir.WEST);
        for (int x = x1; x <= x2; x += 5) { pilaster(x, z1 - 1, Dir.NORTH); pilaster(x, z2 + 1, Dir.SOUTH); }
        for (int x = x1 - 1; x <= x2 + 1; x++) for (int z : new int[]{z1 - 1, z2 + 1}) setIfAir(x, 71, z, stairs("minecraft:quartz_stairs", z == z1 - 1 ? Dir.SOUTH : Dir.NORTH, true));
        for (int z = z1; z <= z2; z++) for (int x : new int[]{x1 - 1, x2 + 1}) setIfAir(x, 71, z, stairs("minecraft:quartz_stairs", x == x1 - 1 ? Dir.EAST : Dir.WEST, true));
        for (int x = x1; x <= x2; x++) for (int z : new int[]{z1, z2}) set(x, 73, z, x % 2 == 0 ? "minecraft:quartz_bricks" : slab("minecraft:quartz_slab", "bottom"));
        for (int z = z1; z <= z2; z++) for (int x : new int[]{x1, x2}) set(x, 73, z, z % 2 == 0 ? "minecraft:quartz_bricks" : slab("minecraft:quartz_slab", "bottom"));
        for (int[] c : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) {
            fill(c[0] - 1, 72, c[1] - 1, c[0] + 1, 75, c[1] + 1, "minecraft:quartz_bricks");
            for (int y = 73; y <= 74; y++) for (Dir d : Dir.values()) set(c[0] + d.dx, y, c[1] + d.dz, "minecraft:light_blue_stained_glass");
            Shapes.sphere(s, c[0], 76, c[1], 1.6, Mat.of("minecraft:gold_block"));
            set(c[0], 78, c[1], "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        }
        // labyrinthe de vitres-miroirs (cellules de 2, couloirs sans impasse)
        maze(x1 + 1, z1 + 1);
        // portes : est (3 de large), nord, sud ; le labyrinthe s'ouvre aussi sur les couloirs est et sud
        for (int y = G + 1; y <= G + 3; y++) {
            for (int z = 19; z <= 21; z++) set(-26, y, z, B.AIR);
            // bouts de couloirs sans issue entre le labyrinthe et les murs : fermes par des miroirs
            for (int x = -43; x <= -32; x++) set(x, y, 29, "minecraft:light_gray_stained_glass_pane");
            for (int z = 11; z <= 17; z++) set(-25, y, z, "minecraft:light_gray_stained_glass_pane");
            set(-31, y, 28, B.AIR); set(-30, y, 28, B.AIR);
            for (int z = 19; z <= 21; z++) { set(x2, y, z, B.AIR); set(x2 - 1, y, z, B.AIR); }
            set(-37, y, z1, B.AIR); set(-36, y, z1, B.AIR); set(-37, y, z1 + 1, B.AIR); set(-36, y, z1 + 1, B.AIR);
            set(-31, y, z2, B.AIR); set(-30, y, z2, B.AIR); set(-31, y, z2 - 1, B.AIR); set(-30, y, z2 - 1, B.AIR);
        }
        // facade d'entree : arc a miroirs, ampoules, enseigne, guichet
        for (int z = 17; z <= 23; z++) {
            int top = 68 + (3 - Math.abs(z - 20)) / 2;
            set(x2 + 1, top + 1, z, Math.abs(z - 20) % 2 == 0 ? "minecraft:ochre_froglight" : "minecraft:gold_block");
            for (int y = G + 4; y <= top; y++) set(x2, y, z, "minecraft:light_blue_stained_glass");
        }
        for (int z = 13; z <= 27; z++) set(x2 + 1, 70, z, facing(z % 2 == 0 ? "minecraft:light_blue_glazed_terracotta" : "minecraft:magenta_glazed_terracotta", Dir.EAST));
        for (int z = 15; z <= 25; z += 2) setIfAir(x2 + 1, 72, z, standing("minecraft:lantern"));
        for (int z : new int[]{17, 23}) {
            set(x2 + 2, G + 1, z, "minecraft:quartz_pillar[axis=y]");
            set(x2 + 2, G + 2, z, "minecraft:gold_block");
            set(x2 + 2, G + 3, z, standing("minecraft:lantern"));
        }
    }

    private void pilaster(int x, int z, Dir out) {
        for (int y = G + 1; y <= 70; y++) setIfAir(x, y, z, y == G + 1 ? "minecraft:polished_blackstone_bricks" : (y == 70 ? "minecraft:gold_block" : "minecraft:quartz_pillar[axis=y]"));
    }

    private void maze(int ox, int oz) {
        final int C = 6; // 6 x 6 cellules de 2 (+ murs) = 19 x 19
        boolean[][] eWall = new boolean[C][C], sWall = new boolean[C][C];
        for (int i = 0; i < C; i++) for (int j = 0; j < C; j++) { eWall[i][j] = true; sWall[i][j] = true; }
        boolean[][] seen = new boolean[C][C];
        SplittableRandom r = new SplittableRandom(4711);
        int[] stack = new int[C * C * 2];
        int sp = 0;
        stack[sp++] = 0;
        seen[0][0] = true;
        while (sp > 0) {
            int cur = stack[sp - 1], i = cur / C, j = cur % C;
            List<int[]> nb = new ArrayList<>();
            if (i + 1 < C && !seen[i + 1][j]) nb.add(new int[]{i + 1, j});
            if (i > 0 && !seen[i - 1][j]) nb.add(new int[]{i - 1, j});
            if (j + 1 < C && !seen[i][j + 1]) nb.add(new int[]{i, j + 1});
            if (j > 0 && !seen[i][j - 1]) nb.add(new int[]{i, j - 1});
            if (nb.isEmpty()) { sp--; continue; }
            int[] n = nb.get(r.nextInt(nb.size()));
            if (n[0] != i) eWall[Math.min(i, n[0])][j] = false;
            else sWall[i][Math.min(j, n[1])] = false;
            seen[n[0]][n[1]] = true;
            stack[sp++] = n[0] * C + n[1];
        }
        // tresse : aucune impasse (chaque cellule a au moins 2 ouvertures)
        for (int i = 0; i < C; i++)
            for (int j = 0; j < C; j++) {
                int open = 0;
                if (i + 1 < C && !eWall[i][j]) open++;
                if (i > 0 && !eWall[i - 1][j]) open++;
                if (j + 1 < C && !sWall[i][j]) open++;
                if (j > 0 && !sWall[i][j - 1]) open++;
                if (open >= 2) continue;
                if (i + 1 < C && eWall[i][j]) eWall[i][j] = false;
                else if (j + 1 < C && sWall[i][j]) sWall[i][j] = false;
                else if (i > 0) eWall[i - 1][j] = false;
                else if (j > 0) sWall[i][j - 1] = false;
            }
        // pose : lignes de murs tous les 3 blocs, poteaux aux croisements
        for (int gx = 0; gx <= 3 * C; gx++)
            for (int gz = 0; gz <= 3 * C; gz++) {
                boolean lx = gx % 3 == 0, lz = gz % 3 == 0;
                if (!lx && !lz) continue;
                int i = gx / 3, j = gz / 3;
                boolean wall;
                if (lx && lz) wall = true;
                else if (lx) wall = gx == 0 || gx == 3 * C || eWall[i - 1][j];
                else wall = gz == 0 || gz == 3 * C || sWall[i][j - 1];
                int x = ox + gx - 1, z = oz + gz - 1;
                if (x < ox || z < oz || x > ox + 18 || z > oz + 18) continue;
                boolean outer = gx == 0 || gz == 0 || gx == 3 * C || gz == 3 * C;
                if (outer && !(lx && lz)) wall = true;
                if (!wall) continue;
                for (int y = G + 1; y <= G + 3; y++) {
                    if (lx && lz) set(x, y, z, y == G + 3 ? "minecraft:gold_block" : "minecraft:quartz_pillar[axis=y]");
                    else set(x, y, z, Math.floorMod(x + z + y, 3) == 0 ? "minecraft:glass_pane" : "minecraft:light_gray_stained_glass_pane");
                }
            }
    }

    // ================================================================ maison hantee

    private void hauntedHouse() {
        final int x1 = 22, x2 = 40, z1 = 12, z2 = 30;
        final int U = 69, T = 74;
        // sols, plancher du grenier (tremie au milieu), murs
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, G, z, (x + 2 * z) % 7 == 0 ? "minecraft:cracked_deepslate_tiles" : "minecraft:deepslate_tiles");
                boolean hole = x >= 28 && x <= 34 && z >= 18 && z <= 24;
                set(x, U, z, hole ? B.AIR : HAUNT_UP.at(x, U, z));
                set(x, T, z, "minecraft:dark_oak_planks");
            }
        for (int y = G + 1; y < T; y++) {
            Mat m = y < U ? HAUNT : HAUNT_UP;
            for (int x = x1; x <= x2; x++) { set(x, y, z1, m); set(x, y, z2, m); }
            for (int z = z1; z <= z2; z++) { set(x1, y, z, m); set(x2, y, z, m); }
        }
        // colombages sombres, contreforts a la base
        for (int x = x1; x <= x2; x += 3)
            for (int z : new int[]{z1, z2})
                for (int y = U; y < T; y++) set(x, y, z, log("minecraft:dark_oak_log", 'y'));
        for (int z = z1; z <= z2; z += 3)
            for (int x : new int[]{x1, x2})
                for (int y = U; y < T; y++) set(x, y, z, log("minecraft:dark_oak_log", 'y'));
        for (int x = x1; x <= x2; x += 3) for (int z : new int[]{z1 - 1, z2 + 1}) {
            if (z == z1 - 1 && x == 34) continue; // porte nord
            for (int y = G + 1; y <= U - 1; y++) set(x, y, z, y <= G + 1 ? "minecraft:cobbled_deepslate" : "minecraft:polished_deepslate");
            set(x, U, z, stairs("minecraft:cobbled_deepslate_stairs", z == z1 - 1 ? Dir.SOUTH : Dir.NORTH, false));
        }
        // fenetres (vitraux violets / rouges) en retrait
        for (int x = x1 + 2; x <= x2 - 2; x += 3)
            for (int z : new int[]{z1, z2})
                for (int y : new int[]{G + 2, U + 2}) {
                    set(x, y, z, x % 2 == 0 ? "minecraft:purple_stained_glass_pane" : "minecraft:red_stained_glass_pane");
                    set(x, y + 1, z, x % 2 == 0 ? "minecraft:purple_stained_glass_pane" : "minecraft:red_stained_glass_pane");
                    setIfAir(x, y - 1, z + (z == z1 ? -1 : 1), stairs("minecraft:deepslate_brick_stairs", z == z1 ? Dir.SOUTH : Dir.NORTH, true));
                }
        // toit a deux pans (faitage nord-sud), pignons, cheminee de travers
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            int d = Math.min(x - (x1 - 1), (x2 + 1) - x);
            int y = T + 1 + d;
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (x == (x1 + x2) / 2) set(x, y, z, "minecraft:polished_deepslate");
                else set(x, y, z, stairs("minecraft:deepslate_tile_stairs", x < (x1 + x2) / 2 ? Dir.EAST : Dir.WEST, false));
                if (z == z1 - 1 || z == z2 + 1) continue;
                if (z == z1 || z == z2) for (int yy = T; yy < y; yy++) set(x, yy, z, HAUNT_UP.at(x, yy, z));
            }
        }
        for (int y = T + 3; y <= 86; y++) { set(36, y, 26, "minecraft:cracked_stone_bricks"); set(37, y, 26, "minecraft:stone_bricks"); }
        set(36, 87, 26, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        // gueule du monstre : porte ouest de 5, dents, yeux
        for (int y = G + 1; y <= G + 3; y++) for (int z = 19; z <= 23; z++) set(x1, y, z, B.AIR);
        for (int z = 18; z <= 24; z++) set(x1, G + 4, z, "minecraft:red_concrete");
        for (int z = 19; z <= 23; z++) set(x1 - 1, G + 3, z, stairs("minecraft:quartz_stairs", Dir.EAST, true));
        for (int z = 18; z <= 24; z++) set(x1 - 1, G + 4, z, "minecraft:red_terracotta");
        for (int z : new int[]{16, 26}) {
            fill(x1 - 1, U + 1, z - 1, x1 - 1, U + 2, z, "minecraft:shroomlight");
            set(x1 - 1, U + 3, z - 1, stairs("minecraft:blackstone_stairs", Dir.EAST, true));
            set(x1 - 1, U + 3, z, stairs("minecraft:blackstone_stairs", Dir.EAST, true));
        }
        // autres portes : nord et est
        for (int y = G + 1; y <= G + 2; y++) { set(34, y, z1, B.AIR); set(35, y, z1, B.AIR); set(x2, y, 26, B.AIR); set(x2, y, 27, B.AIR); }
        // rails du train fantome et wagonnets a l'entree
        int[][] rail = {{18, 21}, {23, 21}, {26, 21}, {26, 15}, {31, 15}, {31, 16}, {37, 16}, {37, 27}, {41, 27}};
        for (int i = 0; i + 1 < rail.length; i++) {
            int ax = rail[i][0], az = rail[i][1], bx = rail[i + 1][0], bz = rail[i + 1][1];
            for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
                for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) setIfAir(x, G + 1, z, "minecraft:rail[shape=" + (ax == bx ? "north_south" : "east_west") + ",waterlogged=false]");
        }
        for (int x = 18; x <= 20; x++) set(x, G + 1, 22, x == 19 ? stairs("minecraft:red_nether_brick_stairs", Dir.WEST, false) : "minecraft:black_concrete");
        // cloisons interieures (decor de scenes) avec passages
        for (int y = G + 1; y < U; y++) {
            // (1.9.1 : passages des cloisons decales d'une case : les escaliers ont recule d'un bloc)
            for (int z = z1 + 1; z <= 17; z++) if (z != 15 && z != 16) set(28, y, z, HAUNT.at(28, y, z));
            for (int x = 28; x <= 35; x++) if (x != 31 && x != 30) set(x, y, 17, HAUNT.at(x, y, 17));
            for (int z = 25; z < z2; z++) if (z != 27 && z != 26) set(34, y, z, HAUNT.at(34, y, z));
        }
        // escaliers vers le grenier : nord-ouest (monte vers l'est) et sud-est (monte vers l'ouest) ; 1.9.1 : la
        // premiere marche n'est plus collee au mur exterieur (on l'aborde de face)
        for (int k = 0; k < 5; k++) {
            for (int z = z1 + 1; z <= z1 + 2; z++) {
                for (int y = G + 1; y < G + 1 + k; y++) set(24 + k, y, z, "minecraft:dark_oak_planks");
                set(24 + k, G + 1 + k, z, stairs("minecraft:dark_oak_stairs", Dir.EAST, false));
                if (k < 4) set(24 + k, U, z, B.AIR);
                set(24 + k + 1, U, z, k == 4 ? "minecraft:dark_oak_planks" : B.AIR);
            }
            for (int z = z2 - 2; z <= z2 - 1; z++) {
                for (int y = G + 1; y < G + 1 + k; y++) set(38 - k, y, z, "minecraft:dark_oak_planks");
                set(38 - k, G + 1 + k, z, stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
                if (k < 4) set(38 - k, U, z, B.AIR);
                set(38 - k - 1, U, z, k == 4 ? "minecraft:dark_oak_planks" : B.AIR);
            }
        }
        // garde-corps autour de la tremie et des tremies d'escalier
        for (int x = 28; x <= 34; x++) for (int z : new int[]{17, 25}) setIfAir(x, U + 1, z, "minecraft:dark_oak_fence");
        for (int z = 17; z <= 25; z++) for (int x : new int[]{27, 35}) setIfAir(x, U + 1, z, "minecraft:dark_oak_fence");
        for (int x = 23; x <= 28; x++) setIfAir(x, U + 1, z1 + 3, "minecraft:dark_oak_fence");
        for (int x = 34; x <= 39; x++) setIfAir(x, U + 1, z2 - 3, "minecraft:dark_oak_fence");
        // scenes du bas
        coffin(24, G + 1, 25, Dir.NORTH);
        coffin(26, G + 1, 27, Dir.NORTH);
        skeleton(32, G + 1, 13);
        skeleton(39, G + 1, 19);
        // 1.9.1 : grille ouverte en x 37 (elle coupait la voie du train fantome et barrait le couloir)
        for (int x = 35; x <= 39; x++) if (x != 37) set(x, G + 1, 23, "minecraft:iron_bars");
        set(37, G + 1, 25, "minecraft:skeleton_skull[rotation=8]");
        set(30, G + 1, 27, "minecraft:cauldron");
        set(31, G + 1, 27, "minecraft:water_cauldron[level=2]");
        set(30, G + 1, 28, candles(4, "black")); // 1.9.1 : au sol (flottait un bloc au-dessus)
        set(23, G + 1, 19, "minecraft:carved_pumpkin[facing=east]");
        set(23, G + 1, 23, "minecraft:jack_o_lantern[facing=east]");
        for (int[] w : new int[][]{{23, 68, 13}, {39, 68, 13}, {23, 68, 29}, {27, 68, 16}, {39, 68, 29}, {33, 68, 18}, {29, 68, 24}})
            setIfAir(w[0], w[1], w[2], "minecraft:cobweb");
        for (int[] l : new int[][]{{25, 16}, {33, 14}, {38, 21}, {31, 26}, {25, 22}})
            setIfAir(l[0], U - 1, l[1], hanging("minecraft:soul_lantern"));
        for (int[] t : new int[][]{{29, 13}, {39, 24}, {23, 28}}) set(t[0], G + 1, t[1], "minecraft:redstone_torch[lit=true]");
        // grenier : orgue, miroir, malles, portrait, lumieres
        for (int x = 30; x <= 34; x++) {
            set(x, U + 1, z1 + 1, "minecraft:note_block");
            for (int y = U + 2; y <= U + 2 + (x % 3) + 1; y++) set(x, y, z1 + 1, "minecraft:iron_bars");
        }
        set(32, U + 1, z1 + 2, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
        for (int[] c : new int[][]{{24, 20}, {25, 26}, {37, 15}, {38, 20}}) set(c[0], U + 1, c[1], "minecraft:chest[facing=east,type=single,waterlogged=false]");
        fill(x2 - 1, U + 1, 17, x2 - 1, U + 3, 18, "minecraft:tinted_glass");
        set(x1 + 1, U + 2, 18, "minecraft:red_glazed_terracotta[facing=east]");
        set(x1 + 1, U + 2, 24, "minecraft:purple_glazed_terracotta[facing=east]");
        for (int[] l : new int[][]{{26, 22}, {37, 24}, {30, 14}, {33, 27}}) setIfAir(l[0], T - 1, l[1], hanging("minecraft:soul_lantern"));
        for (int[] w : new int[][]{{23, 73, 13}, {39, 73, 29}, {23, 73, 29}, {39, 73, 13}}) setIfAir(w[0], w[1], w[2], "minecraft:cobweb");
        set(31, U + 1, 29, "minecraft:jack_o_lantern[facing=north]");
        // tombes devant
        for (int[] g : new int[][]{{24, 33}, {28, 34}, {33, 33}, {37, 34}}) {
            set(g[0], G + 1, g[1], "minecraft:mossy_stone_bricks");
            set(g[0], G + 2, g[1], stairs("minecraft:mossy_stone_brick_stairs", Dir.SOUTH, false));
        }
    }

    private void coffin(int x, int y, int z, Dir along) {
        for (int i = 0; i < 2; i++) {
            set(x + along.dx * i, y, z + along.dz * i, "minecraft:dark_oak_planks");
            set(x + along.dx * i, y + 1, z + along.dz * i, trap("minecraft:dark_oak_trapdoor", along, false, false));
        }
    }

    private void skeleton(int x, int y, int z) {
        set(x, y, z, "minecraft:bone_block[axis=y]");
        set(x, y + 1, z, "minecraft:skeleton_skull[rotation=8]");
        setIfAir(x + 1, y, z, "minecraft:bone_block[axis=x]");
    }

    // ================================================================ tasses

    private void teacups() {
        final int cx = -32, cz = 39;
        String[] pastel = {"pink", "light_blue", "lime", "yellow"};
        for (int x = cx - 8; x <= cx + 8; x++)
            for (int z = cz - 8; z <= cz + 8; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 7.5) continue;
                set(x, G, z, d > 6.5 ? "minecraft:white_concrete" : "minecraft:" + pastel[(int) (d / 1.7) % 4] + "_concrete");
                if (d > 7 && !(Math.abs(x - cx) <= 1) && !(Math.abs(z - cz) <= 1)) set(x, G + 1, z, "minecraft:white_stained_glass_pane");
            }
        // theiere au centre
        for (int y = G + 1; y <= G + 3; y++) disc(cx, cz, y, y == G + 2 ? 1.6 : 1.2, Mat.of(y == G + 2 ? "minecraft:light_blue_terracotta" : "minecraft:white_terracotta"));
        set(cx, G + 4, cz, slab("minecraft:smooth_quartz_slab", "bottom"));
        set(cx, G + 5, cz, "minecraft:light_blue_stained_glass");
        set(cx + 2, G + 2, cz, stairs("minecraft:quartz_stairs", Dir.WEST, false));
        set(cx + 3, G + 3, cz, slab("minecraft:quartz_slab", "bottom"));
        set(cx - 2, G + 2, cz, "minecraft:white_terracotta");
        set(cx - 2, G + 3, cz, trap("minecraft:birch_trapdoor", Dir.WEST, false, true));
        // 4 tasses : rebord d'un bloc, banquette, soucoupe
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            int ux = cx + (int) Math.round(4.3 * Math.sin(a)), uz = cz - (int) Math.round(4.3 * Math.cos(a));
            for (int x = ux - 2; x <= ux + 2; x++)
                for (int z = uz - 2; z <= uz + 2; z++) {
                    double d = Math.hypot(x - ux, z - uz);
                    if (d > 1.0 && d <= 1.9) set(x, G + 1, z, "minecraft:" + pastel[k] + "_terracotta");
                    else if (d <= 1.0) set(x, G + 1, z, k % 2 == 0 ? stairs("minecraft:birch_stairs", Dir.NORTH, false) : "minecraft:" + pastel[(k + 1) % 4] + "_carpet");
                }
        }
        // panneau d'entree
        set(cx, G + 1, cz + 8, "minecraft:birch_fence");
        set(cx, G + 2, cz + 8, "minecraft:birch_fence");
        set(cx, G + 3, cz + 8, "minecraft:pink_glazed_terracotta[facing=south]");
    }

    // ================================================================ toboggan geant

    private void helterSkelter() {
        final int cx = 33, cz = 40;
        // tour 5 x 5 rayee rouge / jaune, interieur 3 x 3
        for (int y = G + 1; y <= 79; y++)
            for (int x = cx - 2; x <= cx + 2; x++)
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean wall = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
                    boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
                    if (!wall) { if (y != 76) set(x, y, z, B.AIR); continue; }
                    set(x, y, z, corner ? log("minecraft:stripped_birch_log", 'y') : ((y / 2) % 2 == 0 ? "minecraft:red_concrete" : "minecraft:yellow_concrete"));
                }
        // plancher haut (pieds en 77), echelle sur le mur nord, porte en bas au sud, sortie en haut a l'est
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) set(x, 76, z, "minecraft:spruce_planks");
        for (int y = G + 1; y <= 76; y++) set(cx, y, cz - 1, "minecraft:ladder[facing=south,waterlogged=false]");
        set(cx, G + 1, cz + 2, B.AIR); set(cx, G + 2, cz + 2, B.AIR);
        set(cx + 2, 77, cz, B.AIR); set(cx + 2, 78, cz, B.AIR);
        set(cx - 2, 78, cz, "minecraft:glass_pane"); set(cx, 78, cz + 2, "minecraft:glass_pane");
        set(cx, 79, cz, hanging("minecraft:lantern"));
        set(cx, 80, cz, "minecraft:spruce_planks");
        // 1.9.1 : lanternes accrochees (avant : posees dans le vide au milieu de la cage) : une au sol dans l'angle,
        // une suspendue par une chaine sous le plancher haut
        set(cx + 1, G + 1, cz + 1, standing("minecraft:lantern"));
        for (int y = 72; y <= 75; y++) set(cx + 1, y, cz + 1, chain('y'));
        set(cx + 1, 71, cz + 1, hanging("minecraft:lantern"));
        cone(cx, cz, 3.6, 80, 5, 1.0, new String[]{"red", "yellow"}, 8);
        set(cx, 86, cz, "minecraft:red_wool");
        set(cx, 87, cz, standing("minecraft:lantern"));
        // spirale exterieure : 24 marches de la sortie haute (y 76) jusqu'au sol, garde-corps
        int[][] cells = spiralCells(cx, cz);
        for (int i = 0; i < cells.length; i++) {
            int x = cells[i][0], z = cells[i][1];
            int fy = 76 - (i + 1) / 2;
            if (fy <= G) continue;
            int prev = i == 0 ? 77 : 76 - i / 2;
            Dir up = i == 0 ? Dir.WEST : Dir.fromDelta(cells[i - 1][0] - x, cells[i - 1][1] - z);
            boolean step = prev > fy;
            set(x, fy, z, step ? stairs("minecraft:spruce_stairs", up, false) : "minecraft:spruce_planks");
            set(x, fy - 1, z, slab("minecraft:spruce_slab", "top"));
            // garde-corps sur l'anneau exterieur
            int ox = x + Integer.signum(x - cx) * (Math.abs(x - cx) == 3 ? 1 : 0), oz = z + Integer.signum(z - cz) * (Math.abs(z - cz) == 3 ? 1 : 0);
            if (i < 21 && isAir(ox, fy + 1, oz)) set(ox, fy + 1, oz, "minecraft:birch_fence");
            if (i % 4 == 2 && isAir(ox, fy + 2, oz) && i < 21) set(ox, fy + 2, oz, standing("minecraft:lantern"));
        }
    }

    private static int[][] spiralCells(int cx, int cz) {
        List<int[]> l = new ArrayList<>();
        for (int z = cz; z <= cz + 3; z++) l.add(new int[]{cx + 3, z});
        for (int x = cx + 2; x >= cx - 3; x--) l.add(new int[]{x, cz + 3});
        for (int z = cz + 2; z >= cz - 3; z--) l.add(new int[]{cx - 3, z});
        for (int x = cx - 2; x <= cx + 3; x++) l.add(new int[]{x, cz - 3});
        for (int z = cz - 2; z <= cz - 1; z++) l.add(new int[]{cx + 3, z});
        return l.toArray(new int[0][]);
    }

    // ================================================================ kiosque a musique

    private void bandstand() {
        final int cx = 0, cz = 8;
        // pavage rayonnant de la place
        for (int x = -10; x <= 10; x++)
            for (int z = cz - 10; z <= cz + 10; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 10.5 || d <= 5.5) continue;
                if ((int) (angle01(x - cx, z - cz) * 24) % 2 == 0 && d > 7) set(x, G, z, "minecraft:polished_andesite");
                if (d > 9.5) set(x, G, z, "minecraft:chiseled_stone_bricks");
            }
        // estrade octogonale (pieds en 66), marches aux 4 points cardinaux
        for (int x = cx - 5; x <= cx + 5; x++)
            for (int z = cz - 5; z <= cz + 5; z++) {
                int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
                if (dx + dz > 7) continue;
                set(x, G + 1, z, dx + dz == 7 || dx == 5 || dz == 5 ? "minecraft:polished_andesite" : ((x + z) % 2 == 0 ? "minecraft:birch_planks" : "minecraft:stripped_birch_wood[axis=y]"));
            }
        for (Dir d : Dir.values())
            for (int i = -1; i <= 1; i++) set(cx + d.dx * 6 + d.cw().dx * i, G + 1, cz + d.dz * 6 + d.cw().dz * i, stairs("minecraft:polished_andesite_stairs", d.opposite(), false));
        // 8 colonnes, garde-corps entre elles (sauf aux marches), toit en pagode
        int[][] posts = {{-4, -2}, {-2, -4}, {2, -4}, {4, -2}, {4, 2}, {2, 4}, {-2, 4}, {-4, 2}};
        for (int[] p : posts) for (int y = G + 2; y <= 69; y++) set(cx + p[0], y, cz + p[1], y == 69 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
        for (int i = 0; i < posts.length; i++) {
            int[] a = posts[i], b = posts[(i + 1) % posts.length];
            if (i % 2 == 1) continue; // ouvertures nord, est, sud, ouest
            Shapes.line(s, cx + a[0], G + 2, cz + a[1], cx + b[0], G + 2, cz + b[1], (x, y, z) -> "minecraft:white_stained_glass_pane");
        }
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 6; z <= cz + 6; z++) {
                int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
                int m = Math.max(dx, dz) + Math.min(dx, dz) / 2;
                if (m > 6) continue;
                int y = 70 + (6 - m) / 2;
                set(x, y, z, m >= 5 ? stairs("minecraft:dark_prismarine_stairs", Math.abs(x - cx) > Math.abs(z - cz) ? (x > cx ? Dir.WEST : Dir.EAST) : (z > cz ? Dir.NORTH : Dir.SOUTH), false) : "minecraft:dark_prismarine");
            }
        set(cx, 74, cz, "minecraft:gold_block");
        set(cx, 75, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        chandelier(cx, 72, cz, 1, true, "minecraft:dark_oak_trapdoor", "minecraft:lantern");
        // pupitres, instruments
        for (int[] m : new int[][]{{-2, -1}, {2, -1}, {0, -3}}) {
            set(cx + m[0], G + 2, cz + m[1], "minecraft:note_block");
            setIfAir(cx + m[0], G + 2, cz + m[1] + 1, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
        }
        set(cx + 3, G + 2, cz + 1, "minecraft:jukebox");
        set(cx - 3, G + 2, cz + 1, "minecraft:lectern[facing=east,has_book=false,powered=false]");
    }

    // ================================================================ stands

    /**
     * Stand de foire : 5 de large, 4 de profondeur, ouvert vers {@code front} ; comptoir, mur du fond, auvent raye a
     * deux couleurs, lambrequin, lumiere. {@code theme} choisit l'interieur. (x, z) = milieu du comptoir.
     * Renvoie this (chainable dans le decor).
     */
    private Fete booth(int x, int z, Dir front, int theme, String[] stripes, int width2) {
        Dir along = front.cw(), back = front.opposite();
        int hw = width2;
        // sol en planches
        for (int d = 0; d <= 3; d++)
            for (int i = -hw; i <= hw; i++) set(x + back.dx * d + along.dx * i, G, z + back.dz * d + along.dz * i, PLANKS.at(x, G, z + i));
        // poteaux d'angle a bandes, murs lateraux et du fond
        for (int y = G + 1; y <= 67; y++)
            for (int d = 0; d <= 3; d++)
                for (int i = -hw; i <= hw; i++) {
                    int xx = x + back.dx * d + along.dx * i, zz = z + back.dz * d + along.dz * i;
                    boolean side = Math.abs(i) == hw, rear = d == 3;
                    if (side && d == 0) set(xx, y, zz, y % 2 == 0 ? "minecraft:" + stripes[0] + "_concrete" : "minecraft:white_concrete");
                    else if (rear || side) set(xx, y, zz, y == G + 1 ? "minecraft:stripped_spruce_wood[axis=y]" : FENCEWOOD.at(xx, y, zz));
                }
        // comptoir
        for (int i = -hw + 1; i <= hw - 1; i++) {
            int xx = x + along.dx * i, zz = z + along.dz * i;
            set(xx, G + 1, zz, i == 0 && theme == 2 ? "minecraft:water_cauldron[level=3]" : slab("minecraft:spruce_slab", "top"));
            setIfAir(xx + front.dx, G + 1, zz + front.dz, B.AIR);
        }
        // auvent : rangs y 68 (avant) a 69 (fond), bandes perpendiculaires au comptoir
        for (int d = -1; d <= 3; d++)
            for (int i = -hw; i <= hw; i++) {
                int xx = x + back.dx * d + along.dx * i, zz = z + back.dz * d + along.dz * i;
                String c = Math.floorMod(i, 2) == 0 ? stripes[0] : stripes[1];
                set(xx, d <= 0 ? 68 : 69, zz, "minecraft:" + c + "_wool");
                if (d == 1) set(xx, 68, zz, "minecraft:" + c + "_wool");
                if (d == -1 && Math.floorMod(i, 2) == 0) set(xx, 67, zz, trap("minecraft:spruce_trapdoor", front, true, false));
            }
        set(x + back.dx, 67, z + back.dz, hanging("minecraft:lantern"));
        set(x + back.dx * 2, 67, z + back.dz * 2, "minecraft:light[level=12,waterlogged=false]");
        // fond : themes
        int bx = x + back.dx * 2, bz = z + back.dz * 2;
        switch (theme) {
            case 0 -> { // tir a la carabine : cibles, lots
                for (int i = -hw + 1; i <= hw - 1; i++) {
                    int xx = x + back.dx * 3 + along.dx * i, zz = z + back.dz * 3 + along.dz * i;
                    set(xx, G + 2, zz, "minecraft:target");
                    set(xx, G + 3, zz, i % 2 == 0 ? "minecraft:target" : "minecraft:white_concrete");
                }
                set(bx + along.dx, G + 1, bz + along.dz, "minecraft:player_head[rotation=" + rot(front) + "]");
                set(bx - along.dx, G + 1, bz - along.dz, "minecraft:creeper_head[rotation=" + rot(front) + "]");
            }
            case 1 -> { // barbe a papa : nuages roses sur batons
                for (int i = -1; i <= 1; i += 2) {
                    int xx = bx + along.dx * i, zz = bz + along.dz * i;
                    set(xx, G + 1, zz, "minecraft:birch_fence");
                    set(xx, G + 2, zz, i < 0 ? "minecraft:pink_wool" : "minecraft:magenta_wool");
                }
                set(bx, G + 1, bz, "minecraft:cauldron");
                set(bx, G + 2, bz, "minecraft:pink_stained_glass");
            }
            case 2 -> { // peche aux canards : bassin
                for (int i = -1; i <= 1; i++) set(bx + along.dx * i, G + 1, bz + along.dz * i, "minecraft:water_cauldron[level=3]");
                set(bx, G + 2, bz + 0, "minecraft:yellow_candle[candles=1,lit=false]");
                set(bx + back.dx, G + 2, bz + back.dz, "minecraft:player_head[rotation=" + rot(front) + "]");
            }
            case 3 -> { // confiserie : gateaux, bocaux
                for (int i = -1; i <= 1; i++) set(bx + along.dx * i, G + 1, bz + along.dz * i, i == 0 ? "minecraft:cake" : "minecraft:honey_block");
                set(bx + back.dx, G + 2, bz + back.dz, "minecraft:decorated_pot[facing=" + front.id() + ",cracked=false,waterlogged=false]");
                set(x + along.dx, G + 2, z + along.dz, candles(3, "pink"));
            }
            case 4 -> { // chamboule-tout : pyramide de pots
                set(bx, G + 1, bz, "minecraft:decorated_pot[facing=" + front.id() + ",cracked=false,waterlogged=false]");
                set(bx + along.dx, G + 1, bz + along.dz, "minecraft:decorated_pot[facing=" + front.id() + ",cracked=false,waterlogged=false]");
                set(bx - along.dx, G + 1, bz - along.dz, "minecraft:decorated_pot[facing=" + front.id() + ",cracked=true,waterlogged=false]");
                set(bx, G + 2, bz, "minecraft:decorated_pot[facing=" + front.id() + ",cracked=false,waterlogged=false]");
            }
            case 5 -> { // crepes et frites : fourneaux
                set(bx, G + 1, bz, "minecraft:smoker[facing=" + front.id() + ",lit=true]");
                set(bx + along.dx, G + 1, bz + along.dz, "minecraft:campfire[facing=" + front.id() + ",lit=true,signal_fire=false,waterlogged=false]");
                set(bx - along.dx, G + 1, bz - along.dz, "minecraft:barrel[facing=up,open=false]");
            }
            case 6 -> { // pommes d'amour et ballons
                for (int i = -1; i <= 1; i++) {
                    int xx = bx + along.dx * i, zz = bz + along.dz * i;
                    set(xx, G + 1, zz, "minecraft:end_rod[facing=up]");
                    set(xx, G + 2, zz, i == 0 ? "minecraft:red_concrete" : "minecraft:red_terracotta");
                }
            }
            default -> { // lancer d'anneaux : bouteilles
                for (int i = -1; i <= 1; i++) set(bx + along.dx * i, G + 1, bz + along.dz * i, "minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]");
            }
        }
        return this;
    }

    private static int rot(Dir d) {
        return switch (d) {
            case SOUTH -> 0;
            case WEST -> 4;
            case NORTH -> 8;
            default -> 12;
        };
    }

    private void boothRows() {
        String[][] stripes = {{"red", "white"}, {"blue", "white"}, {"yellow", "red"}, {"lime", "white"},
                {"pink", "white"}, {"orange", "white"}, {"purple", "yellow"}, {"cyan", "white"}};
        int[] zs = {17, 24, 31, 38};
        for (int k = 0; k < zs.length; k++) {
            booth(-8, zs[k], Dir.EAST, k * 2 % 8, stripes[k], 2);
            booth(8, zs[k], Dir.WEST, (k * 2 + 1) % 8, stripes[(k + 4) % 8], 2);
        }
        // arriere des stands : caisses, groupes electrogenes, poubelles
        for (int z = 15; z <= 44; z += 7) {
            crates(-15, G + 1, z, 2);
            crates(15, G + 1, z + 3, 2);
        }
        for (int z : new int[]{20, 34}) {
            set(-14, G + 1, z, "minecraft:blast_furnace[facing=east,lit=false]");
            set(14, G + 1, z, "minecraft:blast_furnace[facing=west,lit=false]");
        }
        // stands isoles sur les chemins
        booth(-36, -36, Dir.SOUTH, 6, new String[]{"red", "yellow"}, 2);
        booth(36, -36, Dir.SOUTH, 7, new String[]{"blue", "yellow"}, 2);
        booth(-24, 46, Dir.NORTH, 3, new String[]{"pink", "white"}, 2);
        booth(24, 46, Dir.NORTH, 5, new String[]{"orange", "white"}, 2);
    }

    // ================================================================ entree

    private void entrance() {
        // tours rayees a coupole, arche lumineuse, tourniquets, grille fermee
        for (int sx : new int[]{-8, 8}) {
            for (int y = G + 1; y <= 75; y++)
                for (int x = sx - 1; x <= sx + 1; x++)
                    for (int z = 52; z <= 54; z++) {
                        boolean c = Math.abs(x - sx) == 1 && Math.abs(z - 53) == 1;
                        set(x, y, z, c ? "minecraft:quartz_pillar[axis=y]" : ((y / 2) % 2 == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete"));
                    }
            for (int x = sx - 2; x <= sx + 2; x++) for (int z = 51; z <= 55; z++) set(x, 76, z, (x + z) % 2 == 0 ? "minecraft:gold_block" : "minecraft:yellow_concrete");
            cone(sx, 53, 2.6, 77, 4, 1.0, new String[]{"red", "yellow"}, 8);
            for (int y = 82; y <= 84; y++) set(sx, y, 53, log("minecraft:stripped_birch_log", 'y'));
            fill(sx + 1, 83, 53, sx + 2, 84, 53, "minecraft:red_wool");
            for (Dir d : Dir.values()) set(sx + 2 * d.dx, 73, 53 + 2 * d.dz, "minecraft:ochre_froglight");
        }
        for (int x = -6; x <= 6; x++) {
            set(x, 70, 53, "minecraft:yellow_concrete");
            set(x, 71, 53, facing(x % 2 == 0 ? "minecraft:red_glazed_terracotta" : "minecraft:yellow_glazed_terracotta", Dir.NORTH));
            set(x, 72, 53, facing(x % 2 == 0 ? "minecraft:yellow_glazed_terracotta" : "minecraft:red_glazed_terracotta", Dir.NORTH));
            int crest = 73 + (6 - Math.abs(x)) / 3;
            for (int y = 73; y <= crest; y++) set(x, y, 53, y == crest ? "minecraft:gold_block" : "minecraft:red_concrete");
            if (x % 2 == 0) set(x, 69, 53, hanging("minecraft:lantern"));
            set(x, 70, 52, x % 3 == 0 ? "minecraft:pearlescent_froglight" : "minecraft:yellow_concrete");
        }
        for (int x = -5; x <= 5; x += 2) set(x, G + 1, 54, "minecraft:iron_bars");
        for (int x = -6; x <= 6; x++) for (int y = G + 1; y <= 68; y++) set(x, y, 56, y == 68 ? "minecraft:gold_block" : "minecraft:iron_bars");
        // guichets
        booth(-13, 52, Dir.EAST, 99, new String[]{"red", "white"}, 1);
        booth(13, 52, Dir.WEST, 99, new String[]{"red", "white"}, 1);
    }

    // ================================================================ roulottes, pique-nique

    /** Roulotte de 7 x 3 (axe along), caisse peinte, toit arrondi, roues, marches a l'arriere ; open = comptoir. */
    private void caravan(int x, int z, Dir along, String paint, boolean open) {
        Dir side = along.cw();
        for (int i = 0; i < 7; i++)
            for (int j = -1; j <= 1; j++) {
                int xx = x + along.dx * i + side.dx * j, zz = z + along.dz * i + side.dz * j;
                set(xx, G + 1, zz, "minecraft:dark_oak_planks");
                for (int y = G + 2; y <= G + 4; y++) {
                    boolean wall = Math.abs(j) == 1 || i == 0 || i == 6;
                    if (!wall) { set(xx, y, zz, B.AIR); continue; }
                    boolean frame = i == 0 || i == 6 || y == G + 4;
                    boolean win = !frame && (i == 2 || i == 4) && y == G + 3;
                    set(xx, y, zz, frame ? log("minecraft:stripped_dark_oak_log", 'y') : (win ? "minecraft:yellow_stained_glass_pane" : "minecraft:" + paint + "_terracotta"));
                }
                set(xx, G + 5, zz, Math.abs(j) == 1 ? stairs("minecraft:dark_oak_stairs", j > 0 ? side.opposite() : side, false) : "minecraft:dark_oak_planks");
                if (j == 0) set(xx, G + 6, zz, slab("minecraft:dark_oak_slab", "bottom"));
            }
        // roues (trappes ouvertes contre la caisse)
        for (int i : new int[]{1, 5})
            for (int j : new int[]{-2, 2}) {
                int xx = x + along.dx * i + side.dx * j, zz = z + along.dz * i + side.dz * j;
                set(xx, G + 1, zz, trap("minecraft:dark_oak_trapdoor", j > 0 ? side : side.opposite(), false, true));
            }
        // marches et porte arriere, lanterne
        int rx = x - along.dx, rz = z - along.dz;
        set(rx, G + 1, rz, stairs("minecraft:dark_oak_stairs", along, false));
        set(x, G + 2, z, B.AIR);
        set(x, G + 3, z, B.AIR);
        // 1.9.1 : lanterne suspendue a une potence (barriere reliee au toit) au lieu de flotter au-dessus de la marche
        set(rx, G + 5, rz, "minecraft:dark_oak_fence");
        set(rx, G + 4, rz, hanging("minecraft:lantern"));
        if (open) {
            int sx = x + along.dx * 3 + side.dx, sz = z + along.dz * 3 + side.dz;
            for (int i = -1; i <= 1; i++) {
                int xx = sx + along.dx * i, zz = sz + along.dz * i;
                set(xx, G + 3, zz, B.AIR);
                set(xx, G + 2, zz, slab("minecraft:spruce_slab", "top"));
                set(xx + side.dx, G + 4, zz + side.dz, trap("minecraft:spruce_trapdoor", side.opposite(), true, false));
            }
            set(x + along.dx * 3, G + 2, z + along.dz * 3, "minecraft:smoker[facing=" + side.id() + ",lit=true]");
        }
    }

    private void picnic(int x, int z, String parasol) {
        for (int i = -1; i <= 1; i++) set(x + i, G + 1, z, slab("minecraft:spruce_slab", "top"));
        for (int i = -1; i <= 1; i++) {
            setIfAir(x + i, G + 1, z - 1, stairs("minecraft:spruce_stairs", Dir.NORTH, false));
            setIfAir(x + i, G + 1, z + 1, stairs("minecraft:spruce_stairs", Dir.SOUTH, false));
        }
        set(x, G + 2, z, "minecraft:spruce_fence");
        set(x, G + 3, z, "minecraft:spruce_fence");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            set(x + dx, G + 4, z + dz, (dx == 0 && dz == 0) ? "minecraft:" + parasol + "_wool" : "minecraft:" + ((dx + dz) % 2 == 0 ? parasol : "white") + "_wool");
        set(x, G + 5, z, "minecraft:" + parasol + "_carpet");
    }

    private void caravans() {
        // bande est (dans la boite)
        caravan(48, -40, Dir.SOUTH, "light_blue", true);
        caravan(52, -26, Dir.SOUTH, "yellow", true);
        caravan(50, -8, Dir.NORTH, "red", false);
        picnic(48, -21, "red");
        picnic(48, -31, "yellow");
        // bande ouest : roulottes, marteau de force
        caravan(-52, -40, Dir.SOUTH, "green", false);
        caravan(-48, -8, Dir.NORTH, "purple", true);
        picnic(-48, -30, "blue");
        highStriker(-52, -21);
        // coins sud
        picnic(-50, 38, "lime");
        picnic(-48, 28, "orange");
        picnic(48, 22, "pink");
        caravan(50, 32, Dir.SOUTH, "orange", true);
        // camp des forains hors de la boite
        caravan(-64, -20, Dir.SOUTH, "cyan", false);
        caravan(-62, 0, Dir.NORTH, "magenta", false);
        caravan(-64, 20, Dir.SOUTH, "light_blue", false);
        caravan(62, -10, Dir.NORTH, "lime", false);
        caravan(64, 12, Dir.SOUTH, "red", false);
        caravan(-20, 62, Dir.EAST, "yellow", false);
        caravan(18, 64, Dir.WEST, "blue", false);
        // tente des artistes (hors boite, sud-ouest)
        cone(-40, 64, 5.5, G + 1, 7, 1.1, new String[]{"blue", "white"}, 12);
    }

    private void highStriker(int x, int z) {
        fill(x - 1, G + 1, z - 1, x + 1, G + 1, z + 1, "minecraft:polished_andesite");
        set(x, G + 2, z, "minecraft:target");
        String[] band = {"lime", "lime", "yellow", "yellow", "orange", "orange", "red", "red"};
        for (int i = 0; i < band.length; i++) {
            set(x, G + 3 + i, z - 1, "minecraft:" + band[i] + "_concrete");
            set(x, G + 3 + i, z, log("minecraft:stripped_spruce_log", 'y'));
        }
        set(x, G + 11, z, "minecraft:bell[attachment=floor,facing=east,powered=false]");
        set(x, G + 12, z, "minecraft:gold_block");
        set(x + 1, G + 2, z + 1, "minecraft:lever[face=floor,facing=east,powered=false]");
    }

    // ================================================================ petits decors

    /** Chariot de marchand 2 x 3 (pop-corn, ballons...) avec auvent. */
    private void cart(int x, int z, boolean alongX, String color, int kind) {
        for (int i = -1; i <= 1; i++)
            for (int j = 0; j <= 1; j++) {
                int xx = alongX ? x + i : x + j, zz = alongX ? z + j : z + i;
                set(xx, G + 1, zz, i == 0 && j == 0 ? "minecraft:" + color + "_terracotta" : "minecraft:" + color + "_concrete");
                set(xx, G + 4, zz, "minecraft:" + ((i + 1) % 2 == 0 ? color : "white") + "_wool");
            }
        for (int[] c : new int[][]{{-1, 0}, {1, 1}}) {
            int xx = alongX ? x + c[0] : x + c[1], zz = alongX ? z + c[1] : z + c[0];
            set(xx, G + 2, zz, "minecraft:spruce_fence");
            set(xx, G + 3, zz, "minecraft:spruce_fence");
        }
        if (kind == 0) { set(x, G + 2, z, "minecraft:white_stained_glass"); set(x, G + 3, z, B.AIR); }
        else if (kind == 1) {
            String[] b = {"red", "yellow", "lime", "light_blue", "magenta"};
            for (int k = 0; k < 3; k++) {
                int xx = x + (alongX ? k - 1 : 0), zz = z + (alongX ? 0 : k - 1);
                for (int y = G + 5; y <= G + 6; y++) set(xx, y, zz, chain('y'));
                set(xx, G + 7, zz, "minecraft:" + b[Math.floorMod(x + z + k, b.length)] + "_wool");
            }
        } else {
            set(x, G + 2, z, "minecraft:cake");
        }
    }

    private void props() {
        // chariots sur les chemins (cassent les longues lignes de vue)
        cart(-28, -44, true, "red", 0);
        cart(14, -45, true, "yellow", 1);
        cart(-42, -20, false, "blue", 2);
        cart(42, -24, false, "lime", 0);
        cart(0, 29, true, "red", 1);
        cart(0, 42, true, "yellow", 0);
        cart(-30, 48, true, "pink", 1);
        cart(32, 48, true, "orange", 2);
        cart(-35, 0, true, "lime", 1);
        cart(34, 0, true, "magenta", 0);
        // bottes de foin, poubelles, panneaux indicateurs
        int[][] hay = {{-23, 4}, {-22, 5}, {23, 4}, {-45, 3}, {45, 3}, {-17, 48}, {17, 48}, {-40, -41}, {40, -41}, {6, -47}};
        for (int[] p : hay) setIfAir(p[0], G + 1, p[1], "minecraft:hay_block[axis=" + (p[0] % 2 == 0 ? "x" : "z") + "]");
        int[][] bins = {{-8, 12}, {8, 12}, {-17, 2}, {17, 2}, {-40, -47}, {40, -12}, {-20, -40}, {20, 47}};
        for (int[] p : bins) {
            setIfAir(p[0], G + 1, p[1], "minecraft:composter[level=0]");
        }
        int[][] signs = {{-11, 3}, {11, 3}, {-3, -9}, {4, 50}};
        for (int[] p : signs) {
            setIfAir(p[0], G + 1, p[1], "minecraft:spruce_fence");
            setIfAir(p[0], G + 2, p[1], "minecraft:spruce_fence");
            setIfAir(p[0], G + 3, p[1], "minecraft:spruce_sign[rotation=" + (int) (h(p[0], 0, p[1]) * 16) + ",waterlogged=false]");
        }
        // jardinieres fleuries sur la place
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(9 * Math.sin(a)), z = 8 - (int) Math.round(9 * Math.cos(a));
            if (Math.abs(x) <= 2 || Math.abs(z - 8) <= 2) continue;
            set(x, G + 1, z, "minecraft:moss_block");
            setIfAir(x, G + 2, z, h(x, 1, z) < 0.5 ? "minecraft:rose_bush[half=lower]" : "minecraft:peony[half=lower]");
            if (get(x, G + 2, z).contains("half=lower")) set(x, G + 3, z, get(x, G + 2, z).replace("half=lower", "half=upper"));
        }
    }

    // ================================================================ limite : palissade

    private void boundary() {
        for (int t = -56; t <= 56; t++) {
            int[][] cells = {{t, BZ1}, {t, BZ2}, {BX1, t}, {BX2, t}};
            for (int[] c : cells) {
                int x = c[0], z = c[1];
                if (x < BX1 || x > BX2 || z < BZ1 || z > BZ2) continue;
                if (z == BZ2 && Math.abs(x) <= 6) continue; // grille d'entree
                boolean post = Math.floorMod(t, 6) == 0 || Math.abs(t) == 56;
                int g = ground(x, z);
                if (post) {
                    for (int y = g + 1; y <= g + 4; y++) set(x, y, z, log("minecraft:stripped_spruce_log", 'y'));
                    set(x, g + 5, z, standing("minecraft:lantern"));
                    // contrefort vers l'interieur (casse le pan de la palissade)
                    int ix = x == BX1 ? x + 1 : (x == BX2 ? x - 1 : x), iz = z == BZ1 ? z + 1 : (z == BZ2 ? z - 1 : z);
                    if ((ix != x || iz != z) && isAir(ix, g + 1, iz)) {
                        set(ix, g + 1, iz, "minecraft:cobblestone");
                        set(ix, g + 2, iz, log("minecraft:spruce_log", 'y'));
                        set(ix, g + 3, iz, stairs("minecraft:spruce_stairs", x == BX1 ? Dir.WEST : x == BX2 ? Dir.EAST : z == BZ1 ? Dir.NORTH : Dir.SOUTH, false));
                    }
                } else {
                    for (int y = g + 1; y <= g + 3; y++) set(x, y, z, FENCEWOOD.at(x, y, z));
                    set(x, g + 4, z, Math.floorMod(t, 2) == 0 ? "minecraft:spruce_fence" : slab("minecraft:spruce_slab", "bottom"));
                }
                // affiches de cirque sur le parement interieur
                if (!post && Math.floorMod(t * 7 + x + z, 23) == 0) {
                    String[] col = {"yellow", "orange", "red", "light_blue"};
                    set(x, g + 2, z, "minecraft:" + col[Math.floorMod(t, 4)] + "_glazed_terracotta");
                }
                // rideau invisible au-dessus (garde-fou derriere la palissade)
                for (int y = g + 5 + (post ? 1 : 0); y <= MAX_Y; y++) if (isAir(x, y, z)) set(x, y, z, "minecraft:barrier");
            }
        }
        for (int x = -6; x <= 6; x++) for (int y = 69; y <= MAX_Y; y++) if (isAir(x, y, BZ2)) set(x, y, BZ2, "minecraft:barrier");
    }

    // ================================================================ lumieres

    private void lamps() {
        List<int[]> l = new ArrayList<>();
        for (int z : new int[]{14, 21, 28, 35, 42, 49}) { l.add(new int[]{-6, z}); l.add(new int[]{6, z}); }
        for (int x = -36; x <= 36; x += 12) { l.add(new int[]{x, -42}); l.add(new int[]{x, 3}); }
        for (int z = -40; z <= 40; z += 10) { l.add(new int[]{-45, z}); l.add(new int[]{45, z}); }
        for (int z = 6; z <= 46; z += 10) { l.add(new int[]{-22, z}); l.add(new int[]{22, z}); }
        for (int[] p : new int[][]{{-9, -11}, {9, -11}, {-12, 6}, {12, 6}, {-38, -30}, {38, -30}, {-20, -20}, {20, -32}, {-11, -39}, {11, -39}}) l.add(p);
        for (int[] p : l) {
            int x = p[0], z = p[1];
            int g = ground(x, z);
            if (!isAir(x, g + 1, z) || !isAir(x, g + 2, z)) continue;
            lampPost(x, g + 1, z, 6, "minecraft:dark_oak_fence", "minecraft:polished_blackstone_wall", (x + z) % 3 == 0);
        }
    }

    private void garlands() {
        int p = 0;
        // au-dessus de l'allee, d'un lampadaire a l'autre
        for (int z : new int[]{14, 21, 28, 35, 42, 49}) garland(-6, 71, z, 6, 71, z, 2, p++);
        // le long de l'allee, en zigzag
        for (int z = 14; z < 49; z += 7) { garland(-6, 71, z, 6, 71, z + 7, 2, p++); }
        // chemins de la boucle
        for (int x = -36; x < 36; x += 12) { garland(x, 71, -42, x + 12, 71, -42, 2, p++); garland(x, 71, 3, x + 12, 71, 3, 2, p++); }
        for (int z = -40; z < 40; z += 10) { garland(-45, 71, z, -45, 71, z + 10, 2, p++); garland(45, 71, z, 45, 71, z + 10, 2, p++); }
        // du sommet du chapiteau vers les lampadaires de son anneau (rayons de lumiere)
        for (int[] e : new int[][]{{-9, -11}, {9, -11}, {-11, -39}, {11, -39}}) garland(0, 87, -25, e[0], 72, e[1], 3, p++);
        // de l'entree vers les tours
        garland(-8, 75, 52, -6, 71, 49, 1, p++);
        garland(8, 75, 52, 6, 71, 49, 1, p++);
        // place : couronne entre les lampadaires
        garland(-12, 71, 6, 12, 71, 6, 2, p++); // 1.9.1 : fleche 2 (lanternes a hauteur de tete avec 3)
    }

    // ================================================================ arbres, vegetation

    private void trees() {
        int[][] in = {{-50, 46, 8}, {50, 42, 8}, {-54, 22, 7}, {54, 4, 7}, {-30, 8, 6}, {-6, -51, 7}, {14, -51, 6}, {-30, -37, 6},
                {30, -37, 6}, {-27, 31, 5}, {48, -44, 7}, {-24, -8, 6}, {24, -6, 6}, {-50, 2, 6}, {-52, 52, 7}, {53, 53, 7}};
        long sd = 300;
        for (int[] t : in) {
            int g = ground(t[0], t[1]);
            if (!isAir(t[0], g + 1, t[1])) continue;
            String[] k = sd % 3 == 0 ? new String[]{"minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves"}
                    : sd % 3 == 1 ? new String[]{"minecraft:dark_oak_log", "minecraft:dark_oak_leaves", "minecraft:oak_leaves"}
                    : new String[]{"minecraft:birch_log", "minecraft:birch_leaves", "minecraft:oak_leaves"};
            new Tree(k[0], k[1], k[2]).height(t[2]).crown(t[2] >= 7 ? 3.0 : 2.4).place(s, t[0], g + 1, t[1], sd++);
        }
        // ceinture boisee hors de la boite
        long s2 = 900;
        for (int x = -AREA + 6; x <= AREA - 6; x += 6)
            for (int z = -AREA + 6; z <= AREA - 6; z += 6) {
                if (inBox(x + 2, z + 2) && inBox(x - 2, z - 2)) continue;
                int jx = x + (int) (h(x, 9, z) * 4) - 2, jz = z + (int) (h(x, 10, z) * 4) - 2;
                if (Math.abs(jx) <= 58 && Math.abs(jz) <= 58 && jz >= -57) continue;
                if (!onIsland(jx, jz)) continue;
                int g = ground(jx, jz);
                if (!isAir(jx, g + 1, jz) || !isAir(jx, g + 3, jz)) continue;
                double r = h(jx, 11, jz);
                String[] k = r < 0.35 ? new String[]{"minecraft:spruce_log", "minecraft:spruce_leaves", null}
                        : r < 0.7 ? new String[]{"minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves"}
                        : new String[]{"minecraft:dark_oak_log", "minecraft:dark_oak_leaves", "minecraft:oak_leaves"};
                new Tree(k[0], k[1], k[2]).height(6 + (int) (h(jx, 12, jz) * 5)).crown(2.8).place(s, jx, g + 1, jz, s2++);
            }
    }

    private void vegetation() {
        String[] low = {"minecraft:short_grass", "minecraft:short_grass", "minecraft:fern", "minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower"};
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                int g = ground(x, z);
                String b = get(x, g, z);
                if (!b.contains("grass_block") && !b.contains("moss_block") && !b.contains("podzol")) continue;
                if (!isAir(x, g + 1, z)) continue;
                double u = noise.uniform2(x / 4.0 + 13, z / 4.0 - 7);
                double density = inBox(x, z) ? 0.32 : 0.5;
                if (u > 1 - density && h(x, 5, z) < 0.6) set(x, g + 1, z, low[(int) (h(x, 6, z) * low.length)]);
            }
    }
}
