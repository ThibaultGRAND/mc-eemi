package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Connect;
import fr.eemicraft.murder.build.Details;
import fr.eemicraft.murder.build.Dir;
import fr.eemicraft.murder.build.Facade;
import fr.eemicraft.murder.build.Mat;
import fr.eemicraft.murder.build.Noise;
import fr.eemicraft.murder.build.Palette;
import fr.eemicraft.murder.build.Roof;
import fr.eemicraft.murder.build.RoofStyle;
import fr.eemicraft.murder.build.Shapes;
import fr.eemicraft.murder.build.Sink;
import fr.eemicraft.murder.build.Stairs;
import fr.eemicraft.murder.build.Style;
import fr.eemicraft.murder.build.Tree;

/**
 * Map « Manoir-bibliotheque » (id bibliotheque, monde mm_bibliotheque), entierement dessinee par code dans un
 * {@link Sink} (aucun acces Bukkit : testable hors serveur, construite en memoire puis posee par {@link ManoirMap}).
 *
 * <p>Manoir victorien en briques rouges et pierre claire, toits a la Mansart en ardoise, au milieu d'un jardin clos
 * de 101 x 101 (x/z -50..50). Rez-de-chaussee : sol en y 64 (pieds en 65) ; etage : plancher en y 71 (pieds en 72) ;
 * jardin : herbe en y 63 (pieds en 64). Plan :
 * <ul>
 * <li>corps central : grande bibliotheque sur deux niveaux (nef ouverte sous un lanterneau vitre, galeries a l'etage
 * tout autour, deux escaliers en colimacon), hall d'entree a double hauteur (deux volees vers le palier) et pavillon
 * d'entree au sud ;</li>
 * <li>aile ouest : salle a manger et cuisine + office (escalier de service) ; a l'etage bureau et archives ; tourelle
 * ronde au sud-ouest avec un escalier en colimacon ;</li>
 * <li>aile est : salon et salle de lecture (escalier) ; a l'etage salon de musique et salle des cartes ;
 * serre (jardin d'hiver) accolee a l'est ;</li>
 * <li>jardin : parterres et fontaine devant, potager a l'ouest, kiosque, bassin et allees de haies derriere.</li>
 * </ul>
 * Toutes les pieces ont au moins deux sorties ; 7 escaliers relient les deux niveaux.
 */
final class Manoir {
    /** Version de construction : la monter force la reconstruction du monde (data.yml). */
    static final int VERSION = 3; // 1.13.0 : vitres dans le mur (Facade)
    static final String ID = "bibliotheque";
    static final String WORLD = "mm_bibliotheque";
    static final String NAME = "Manoir-biblioth\u00e8que";

    /** Boite de jeu (config min/max) : sortir de x/z ou tomber sous MIN_Y - 1 = elimine (regles du plugin). */
    static final int BX1 = -50, BX2 = 50, BZ1 = -50, BZ2 = 50, MIN_Y = 60, MAX_Y = 84;
    /** Zone posee / effacee par la construction (decor hors de la boite compris). */
    static final int AREA = 62, Y_LO = 52, Y_HI = 100;

    /** Point d'arrivee du monde (terrasse devant l'entree). */
    static final int[] WORLD_SPAWN = {0, 65, 22};

    /** 18 apparitions (pieds) : 9 au rez-de-chaussee / jardin, 9 a l'etage. */
    static final int[][] SPAWNS = {
            // rez-de-chaussee et jardin
            {-2, 65, 2},     // hall d'entree
            {-23, 65, 7},    // salle a manger
            {-26, 65, -12},  // cuisine
            {0, 65, -15},    // nef de la bibliotheque
            {21, 65, -8},    // salle de lecture
            {24, 65, 5},     // salon
            {39, 65, -10},   // serre
            {-12, 64, 33},   // jardin de devant
            {14, 64, -38},   // jardin de derriere
            // etage
            {-22, 72, 6},    // bureau
            {-24, 72, -10},  // archives
            {22, 72, 6},     // salon de musique
            {24, 72, -10},   // salle des cartes
            {-9, 72, -12},   // galerie ouest
            {9, 72, -14},    // galerie est
            {0, 72, -20},    // galerie nord
            {0, 72, -1},     // palier
            {-35, 72, 13},   // tourelle
    };

    /** 42 points d'or (pieds) repartis dans toutes les pieces et le jardin. */
    static final int[][] GOLD = {
            // bibliotheque, rez-de-chaussee
            {-9, 65, -14}, {9, 65, -13}, {-5, 65, -18}, {5, 65, -18}, {0, 65, -9}, {-4, 65, -5},
            // hall, pavillon
            {-6, 65, 6}, {6, 65, 3}, {0, 65, 14},
            // aile ouest
            {-28, 65, 1}, {-15, 65, 8}, {-28, 65, -15}, {-23, 65, -5}, {-17, 65, -14}, {-16, 65, -6},
            // aile est
            {26, 65, 6}, {15, 65, 1}, {28, 65, -14}, {15, 65, -11},
            // serre
            {34, 65, 2}, {43, 65, -12},
            // tourelle (rdc)
            {-35, 65, 14},
            // galeries
            {-10, 72, -5}, {9, 72, -11}, {-5, 72, -20}, {4, 72, -4}, {8, 72, -1},
            // etage ouest
            {-28, 72, 8}, {-16, 72, 1}, {-26, 72, -12}, {-19, 72, -7},
            // etage est
            {28, 72, 1}, {16, 72, 8}, {28, 72, -8}, {15, 72, -5},
            // jardin
            {-30, 64, 30}, {30, 64, 40}, {44, 64, 30}, {-44, 64, -30}, {-24, 64, -46}, {29, 64, -29}, {-44, 64, 10},
    };

    // hauteurs
    static final int G = 63;   // herbe du jardin (pieds en 64)
    static final int F = 64;   // sol du rez-de-chaussee (pieds en 65)
    static final int U = 71;   // plancher de l'etage (pieds en 72)
    static final int T = 78;   // corniche, plafond de l'etage
    static final int DECK = 84; // terrasse des toits a la Mansart

    private final Sink s;
    private final Noise noise = new Noise(4242);

    private Manoir(Sink s) {
        this.s = s;
    }

    static void build(Sink s) {
        new Manoir(s).run();
    }

    // ================================================================ palettes

    private static final Palette BRICK = Palette.of(11, "minecraft:bricks", 60, "minecraft:mud_bricks", 20,
            "minecraft:terracotta", 20).period(8).amp(0.2);
    private static final Palette PLINTH = Palette.of(12, "minecraft:stone_bricks", 80, "minecraft:mossy_stone_bricks", 12,
            "minecraft:cracked_stone_bricks", 8).period(11);
    private static final Mat QUOIN = Mat.of("minecraft:quartz_bricks");
    private static final Palette PARQUET = Palette.of(13, "minecraft:spruce_planks", 72, "minecraft:jungle_planks", 28).period(6);
    private static final Palette PARQUET_SPRUCE = Palette.of(14, "minecraft:spruce_planks", 78, "minecraft:jungle_planks", 22).period(7);
    private static final Palette OAK_FLOOR = Palette.of(15, "minecraft:oak_planks", 75, "minecraft:birch_planks", 25).period(5);
    private static final Palette GRASS = Palette.of(16, "minecraft:grass_block", 82, "minecraft:moss_block", 8,
            "minecraft:podzol", 5, "minecraft:coarse_dirt", 5).period(5);
    private static final Palette GRAVEL = Palette.of(17, "minecraft:gravel", 62, "minecraft:coarse_dirt", 20,
            "minecraft:packed_mud", 18).period(4);
    private static final Palette PAVING = Palette.of(18, "minecraft:stone_bricks", 80, "minecraft:polished_andesite", 12,
            "minecraft:cracked_stone_bricks", 8).period(11);
    private static final Palette HEDGE = Palette.of(19, "minecraft:azalea_leaves[persistent=true]", 68,
            "minecraft:oak_leaves[persistent=true]", 32).period(4);
    private static final Palette ROOF_STAIRS = Palette.of(21, "minecraft:polished_deepslate_stairs", 22,
            "minecraft:deepslate_tile_stairs", 58, "minecraft:deepslate_brick_stairs", 20).period(6).amp(0.15);

    // ================================================================ outils

    private void set(int x, int y, int z, String st) {
        s.set(x, y, z, B.id(st));
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, String st) {
        Shapes.fill(s, x1, y1, z1, x2, y2, z2, Mat.of(st));
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Mat m) {
        Shapes.fill(s, x1, y1, z1, x2, y2, z2, m);
    }

    private void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        Shapes.fill(s, x1, y1, z1, x2, y2, z2, Mat.AIR);
    }

    private boolean isAir(int x, int y, int z) {
        return s.isAir(x, y, z);
    }

    private void setIfAir(int x, int y, int z, String st) {
        if (isAir(x, y, z)) set(x, y, z, st);
    }

    private double h(int a, int b, int c) {
        return Noise.hash(777, a, b, c);
    }

    private static String stairs(String id, Dir facing, boolean top) {
        return B.stairs(B.id(id), facing, top);
    }

    private static String slab(String id, String type) {
        return B.slab(B.id(id), type);
    }

    private static String trap(String id, Dir facing, boolean top, boolean open) {
        return B.trapdoor(B.id(id), facing, top, open);
    }

    private static String log(String id, char axis) {
        return B.axis(B.id(id), axis);
    }

    private static String hanging(String lantern) {
        return B.state(B.id(lantern), "hanging", "true");
    }

    private static String standing(String lantern) {
        return B.state(B.id(lantern), "hanging", "false");
    }

    // ================================================================ deroule

    private void run() {
        terrain();
        terrace();
        shell();
        roofs();
        turret();
        greenhouse();
        library();
        foyer();
        dining();
        kitchen();
        pantry();
        salon();
        reading();
        study();
        archives();
        music();
        mapRoom();
        garden();
        finish();
    }

    // ================================================================ terrain

    /** Hauteur du sol (bloc de surface) en (x, z). */
    int ground(int x, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        if (ax > BX2 || az > BZ2) {
            // ceinture boisee hors de la boite : collines douces qui montent vers l'exterieur
            int d = Math.max(ax - BX2, az - BZ2);
            double n = noise.fbm2(x / 18.0, z / 18.0, 3, 0.5, 2.0);
            return G + (int) Math.round(Math.min(5, d * 0.35 + 1.6 * (n + 0.4)));
        }
        double hh = 0;
        for (int[] m : MOUNDS) {
            double dx = x - m[0], dz = z - m[1];
            double d = Math.sqrt(dx * dx + dz * dz) / m[2];
            if (d < 1) hh = Math.max(hh, m[3] * (0.5 + 0.5 * Math.cos(Math.PI * d)) + 0.25 * noise.noise2(x / 5.0, z / 5.0));
        }
        return G + (int) Math.round(hh);
    }

    /** Buttes du jardin {cx, cz, rayon, hauteur} : sous les arbres, a l'ecart des allees. */
    private static final int[][] MOUNDS = {
            {-40, 40, 9, 2}, {40, 42, 8, 2}, {-42, -40, 8, 2}, {38, -40, 9, 2}, {-26, 44, 5, 1}, {24, 46, 5, 1},
            {44, -20, 4, 1}, {-44, 0, 4, 1},
    };

    /** Bassin du jardin de derriere. */
    static final int POND_X = -22, POND_Z = -38;

    private boolean inPond(int x, int z) {
        double dx = (x - POND_X) / 6.0, dz = (z - POND_Z) / 4.5;
        double r = 1 + 0.12 * noise.noise2(x / 3.0, z / 3.0);
        return dx * dx + dz * dz < r * r;
    }

    private void terrain() {
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                int gh = ground(x, z);
                fill(x, Y_LO + 4, z, x, G - 3, z, "minecraft:stone");
                for (int y = G - 2; y < gh; y++) set(x, y, z, "minecraft:dirt");
                set(x, gh, z, GRASS.at(x, gh, z));
            }
        // bassin : fond en y 61, eau en 62-63, berge en argile / gravier
        for (int x = POND_X - 8; x <= POND_X + 8; x++)
            for (int z = POND_Z - 7; z <= POND_Z + 7; z++) {
                if (inPond(x, z)) {
                    set(x, 61, z, (h(x, 0, z) < 0.5) ? "minecraft:clay" : "minecraft:gravel");
                    set(x, 62, z, B.WATER);
                    set(x, 63, z, B.WATER);
                } else {
                    boolean shore = inPond(x + 1, z) || inPond(x - 1, z) || inPond(x, z + 1) || inPond(x, z - 1);
                    if (shore) set(x, 63, z, noise.uniform2(x / 2.0, z / 2.0) < 0.5 ? "minecraft:mud" : "minecraft:moss_block");
                }
            }
    }

    // ================================================================ terrasse et allees

    private void terrace() {
        // terrasse dallee devant le manoir (au niveau du sol du rez-de-chaussee)
        for (int x = -30; x <= 30; x++)
            for (int z = 13; z <= 24; z++) {
                if (z > 19 && Math.abs(x) > 9) continue; // avancee devant le pavillon seulement
                set(x, F, z, PAVING.at(x, F, z));
                set(x, G, z, "minecraft:dirt");
            }
        // petites cours entre le pavillon et les ailes : dallees au niveau de la terrasse
        for (int x = 7; x <= 11; x++)
            for (int z = 11; z <= 12; z++)
                for (int sx : new int[]{-x, x}) {
                    set(sx, F, z, PAVING.at(sx, F, z));
                    set(sx, G, z, "minecraft:dirt");
                }
        // balustrade (muret + vases) sur le bord, avec les marches
        for (int x = -30; x <= 30; x++) {
            int z = Math.abs(x) <= 9 ? 24 : 19;
            boolean steps = Math.abs(x) <= 3 || (Math.abs(x) >= 18 && Math.abs(x) <= 20);
            if (steps) {
                set(x, F, z, stairs("minecraft:stone_brick_stairs", Dir.NORTH, false));
                continue;
            }
            set(x, F + 1, z, "minecraft:stone_brick_wall");
            if (Math.abs(x) % 5 == 0) {
                set(x, F + 1, z, "minecraft:chiseled_stone_bricks");
                set(x, F + 2, z, "minecraft:potted_azalea_bush");
            }
        }
        for (int z = 20; z <= 24; z++)
            for (int sx : new int[]{-10, 10}) {
                set(sx, F, z, PAVING.at(sx, F, z));
                set(sx, F + 1, z, z % 2 == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_brick_wall");
            }
        for (int sx : new int[]{-30, 30})
            for (int z = 13; z <= 19; z++) set(sx, F + 1, z, z == 16 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_brick_wall");
        // marches laterales vers les jardins
        for (int z = 15; z <= 17; z++) {
            set(-30, F + 1, z, B.AIR);
            set(-30, F, z, stairs("minecraft:stone_brick_stairs", Dir.EAST, false));
            set(30, F + 1, z, B.AIR);
            set(30, F, z, stairs("minecraft:stone_brick_stairs", Dir.WEST, false));
        }
    }

    // ================================================================ gros oeuvre

    private Style facadeStyle(long seed) {
        Style st = new Style().wall(BRICK).plinth(PLINTH).plinthStairs("minecraft:stone_brick_stairs")
                .post("minecraft:quartz_pillar").corner(QUOIN).pane("minecraft:glass_pane").sill("minecraft:quartz_stairs")
                .lintel("minecraft:smooth_quartz_slab").shutter("minecraft:warped_trapdoor").band("minecraft:quartz_stairs")
                .cornice("minecraft:dark_oak_stairs").door("minecraft:dark_oak_door").spacing(4).storey(7)
                .shutters(0.45).blankBays(0.12).innerPosts(false).seed(seed);
        st.groundDepth = 4;
        return st;
    }

    /** Murs exterieurs, sols, cloisons, planchers, ouvertures et escaliers. */
    private void shell() {
        Style st = facadeStyle(1);
        Style pav = facadeStyle(2);
        // sols du rez-de-chaussee (le socle des facades pose le reste)
        fill(-11, F, -23, 11, F, 11, PARQUET);
        fill(-31, F, -17, -13, F, 11, PARQUET);
        fill(13, F, -17, 31, F, 11, PARQUET);
        fill(-31, F - 1, -17, 31, F - 1, 11, "minecraft:dirt");
        fill(-5, F, 10, 5, F, 17, PARQUET);
        fill(-11, F - 1, -23, 11, F - 1, 11, "minecraft:dirt");

        // corps central
        Facade.wall(s, st, -12, -24, Dir.EAST, 24, Dir.NORTH, F, T, 12);
        Facade.wall(s, st, -12, -24, Dir.SOUTH, 6, Dir.WEST, F, T, -1);
        Facade.wall(s, st, 12, -24, Dir.SOUTH, 6, Dir.EAST, F, T, -1);
        Facade.wall(s, st, -12, 10, Dir.EAST, 6, Dir.SOUTH, F, T, -1);
        Facade.wall(s, st, 6, 10, Dir.EAST, 6, Dir.SOUTH, F, T, -1);
        // pavillon d'entree (plus haut : corniche en 80)
        Facade.wall(s, pav, -6, 10, Dir.SOUTH, 8, Dir.WEST, F, T + 2, -1);
        Facade.wall(s, pav, 6, 10, Dir.SOUTH, 8, Dir.EAST, F, T + 2, -1);
        Facade.wall(s, pav, -6, 18, Dir.EAST, 12, Dir.SOUTH, F, T + 2, 6);
        // ailes
        Facade.wall(s, st, -32, -18, Dir.EAST, 20, Dir.NORTH, F, T, 6);
        Facade.wall(s, st, -32, -18, Dir.SOUTH, 30, Dir.WEST, F, T, 18);
        Facade.wall(s, st, -32, 12, Dir.EAST, 20, Dir.SOUTH, F, T, 14);
        Facade.wall(s, st, 12, -18, Dir.EAST, 20, Dir.NORTH, F, T, 14);
        Facade.wall(s, st, 32, -18, Dir.SOUTH, 30, Dir.EAST, F, T, -1);
        Facade.wall(s, st, 12, 12, Dir.EAST, 20, Dir.SOUTH, F, T, 8);

        // grand vitrail du pavillon au-dessus de la porte (lancette en ogive)
        vitrailPavillon();
        // vitraux de la bibliotheque (mur nord, etage)
        for (int x = -10; x <= 10; x++) {
            String cur = s.get(x, 75, -22);
            if (B.name(cur).equals("glass_pane")) set(x, 75, -22, B.with(cur.replace("glass_pane", "purple_stained_glass_pane"), "north", "false"));
        }

        // habillage interieur des murs exterieurs (lambris + papier peint), avant cloisons et planchers
        Mat wainscot = wallpaper("minecraft:spruce_planks", null, 0);
        // aile ouest : salle a manger (papier rouge), cuisine (carreaux), bureau, archives
        lineRoom(-30, -16, -13, 10, F + 1, U - 1, wallpaper("minecraft:red_terracotta", "minecraft:brown_terracotta", 31));
        lineRoom(-30, -16, -13, 10, U + 1, T - 1, wallpaper("minecraft:brown_terracotta", "minecraft:spruce_planks", 32));
        lineRoom(13, -16, 30, 10, F + 1, U - 1, wallpaper("minecraft:green_terracotta", "minecraft:lime_terracotta", 33));
        lineRoom(13, -16, 30, 10, U + 1, T - 1, wallpaper("minecraft:cyan_terracotta", "minecraft:light_blue_terracotta", 34));
        // bibliotheque : rayonnages contre les murs exterieurs (rez-de-chaussee et etage)
        lineBooks(-10, -22, 10, -22, F + 1, T - 1, Dir.SOUTH);
        lineBooks(-10, -22, -10, -19, F + 1, T - 1, Dir.EAST);
        lineBooks(10, -22, 10, -19, F + 1, T - 1, Dir.WEST);
        // hall : lambris
        lineRoom(-11, 8, -7, 8, F + 1, T - 1, wainscot);
        lineRoom(7, 8, 11, 8, F + 1, T - 1, wainscot);
        lineRoom(-4, 9, 4, 16, F + 1, T + 1, wainscot);

        // cloisons (une epaisseur : lambris bas, panneaux, poteaux tous les 4)
        partitionX(-12, -17, 9, F + 1, T - 1);
        partitionX(12, -17, 9, F + 1, T - 1);
        partitionZ(-2, -11, 11, F + 1, T - 1);
        partitionZ(-2, -30, -13, F + 1, T - 1);
        partitionZ(-2, 13, 30, F + 1, T - 1);
        partitionX(-21, -16, -3, F + 1, U - 1);

        // planchers de l'etage (trous pour les escaliers et la nef plus loin)
        fill(-30, U, -16, -13, U, 10, PARQUET_SPRUCE);
        fill(13, U, -16, 30, U, 10, PARQUET_SPRUCE);
        fill(-11, U, -21, 11, U, -3, PARQUET);
        fill(-11, U, -1, 11, U, 0, PARQUET);
        fill(-9, U, -21, 9, U, -19, PARQUET);
        // nef de la bibliotheque ouverte jusqu'au lanterneau
        air(-6, U, -17, 6, U, -7);
        // plafonds de l'etage
        fill(-30, T, -16, -13, T, 10, "minecraft:spruce_planks");
        fill(13, T, -16, 30, T, 10, "minecraft:spruce_planks");
        fill(-11, T, -21, 11, T, 8, "minecraft:spruce_planks");
        fill(-4, T + 2, 9, 4, T + 2, 16, "minecraft:spruce_planks");
        air(-4, T, 9, 4, T + 1, 16);
        // poutres sous les planchers (petite portee)
        for (int z = -14; z <= 8; z += 5) fill(-30, U - 1, z, -13, U - 1, z, log("minecraft:stripped_dark_oak_log", 'x'));
        for (int z = -14; z <= 8; z += 5) fill(13, U - 1, z, 30, U - 1, z, log("minecraft:stripped_dark_oak_log", 'x'));
        for (int z = -14; z <= 8; z += 5) fill(-30, T - 1, z, -13, T - 1, z, log("minecraft:stripped_dark_oak_log", 'x'));
        for (int z = -14; z <= 8; z += 5) fill(13, T - 1, z, 30, T - 1, z, log("minecraft:stripped_dark_oak_log", 'x'));

        // consoles (trappes) de part et d'autre des poutres, tous les 4 : rythment les longues poutres
        for (int z = -14; z <= 8; z += 5)
            for (int x : new int[]{-28, -24, -20, -16, 15, 19, 23, 27})
                for (int y : new int[]{U - 1, T - 1}) {
                    setIfAir(x, y, z - 1, trap("minecraft:dark_oak_trapdoor", Dir.SOUTH, true, false));
                    setIfAir(x, y, z + 1, trap("minecraft:dark_oak_trapdoor", Dir.NORTH, true, false));
                }

        // ouvertures du rez-de-chaussee
        arch(-12, -12, 3, 5, F + 1, 4, 'z');   // hall <-> salle a manger
        arch(-12, -12, -15, -14, F + 1, 3, 'z'); // bibliotheque <-> office
        arch(12, 12, 3, 5, F + 1, 4, 'z');     // hall <-> salon
        arch(12, 12, -9, -8, F + 1, 3, 'z');   // bibliotheque <-> salle de lecture
        arch(-2, 2, -2, -2, F + 1, 5, 'x');    // bibliotheque <-> hall
        arch(-27, -26, -2, -2, F + 1, 3, 'x'); // cuisine <-> salle a manger
        arch(-16, -15, -2, -2, F + 1, 3, 'x'); // office <-> salle a manger
        arch(-21, -21, -9, -8, F + 1, 3, 'z'); // cuisine <-> office
        arch(15, 16, -2, -2, F + 1, 3, 'x');   // salle de lecture <-> salon
        arch(26, 27, -2, -2, F + 1, 3, 'x');
        // ouvertures de l'etage
        arch(-12, -12, -1, 0, U + 1, 3, 'z');   // palier <-> bureau
        arch(-12, -12, -15, -14, U + 1, 3, 'z'); // galerie ouest <-> archives
        arch(12, 12, -1, 0, U + 1, 3, 'z');     // palier <-> salon de musique
        arch(12, 12, -12, -11, U + 1, 3, 'z');  // galerie est <-> salle des cartes
        arch(-2, 2, -2, -2, U + 1, 4, 'x');     // palier <-> galerie sud
        arch(-27, -26, -2, -2, U + 1, 3, 'x');  // bureau <-> archives
        arch(-18, -17, -2, -2, U + 1, 3, 'x');
        arch(15, 16, -2, -2, U + 1, 3, 'x');    // musique <-> cartes
        arch(26, 27, -2, -2, U + 1, 3, 'x');
        // serre : deux passages dans le mur est de l'aile est
        for (int[] zz : new int[][]{{-9, -8}, {3, 4}}) {
            air(30, F + 1, zz[0], 32, F + 3, zz[1]);
            fill(30, F, zz[0], 32, F, zz[1], "minecraft:mossy_stone_bricks");
            for (int x = 30; x <= 32; x++) {
                set(x, F + 4, zz[0] - 1, "minecraft:stripped_dark_oak_log[axis=x]");
                set(x, F + 4, zz[1] + 1, "minecraft:stripped_dark_oak_log[axis=x]");
                set(x, F + 4, zz[0], stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
                set(x, F + 4, zz[1], stairs("minecraft:dark_oak_stairs", Dir.SOUTH, true));
            }
        }

        // petits emmarchements de pierre au pied des poteaux et des angles (le socle n'est plus un seul plan)
        for (int x = -33; x <= 33; x++)
            for (int z = -25; z <= 19; z++) {
                String up = s.get(x, F + 1, z);
                if (!up.contains("quartz_pillar") && !up.contains("quartz_bricks")) continue;
                for (Dir d : Dir.values()) {
                    int ax = x + d.dx, az = z + d.dz;
                    if (isAir(ax, F, az) && isAir(ax, F + 1, az) && B.isSolid(s.get(ax, G, az)) && !s.get(ax, G, az).contains("gravel"))
                        set(ax, F, az, stairs("minecraft:stone_brick_stairs", d.opposite(), false));
                }
            }

        // escaliers
        grandStairs();
        spiral(-8, -20, new int[][]{{1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}});
        spiral(8, -5, new int[][]{{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}});
        straightStairs(-14, -4, Dir.NORTH, 2);  // escalier de service (office -> archives)
        straightStairs(16, -15, Dir.EAST, 2);   // escalier de la salle de lecture -> salle des cartes
    }

    private Mat wallpaper(String main, String alt, long seed) {
        Mat p = Mat.of(main);
        return (x, y, z) -> {
            int ry = y >= U ? y - U : y - F;
            if (ry <= 2) return "minecraft:spruce_planks";
            if (ry == 3) return "minecraft:stripped_dark_oak_wood";
            return p.at(x, y, z);
        };
    }

    /**
     * Habille l'interieur des murs exterieurs d'un rectangle de piece (x1..x2, z1..z2 = premiere rangee
     * interieure, plan P-2 des facades) : seules les cases d'air sont touchees (les vitres restent), et une colonne
     * dont le plan du mur (P-1) est ouvert (porte) n'est pas habillee.
     */
    private void lineRoom(int x1, int z1, int x2, int z2, int y1, int y2, Mat m) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                lineCell(x, y, z1, 0, -1, m);
                lineCell(x, y, z2, 0, 1, m);
            }
            for (int z = z1; z <= z2; z++) {
                lineCell(x1, y, z, -1, 0, m);
                lineCell(x2, y, z, 1, 0, m);
            }
        }
    }

    private void lineCell(int x, int y, int z, int ox, int oz, Mat m) {
        if (!isAir(x, y, z)) return;
        String wall = s.get(x + ox, y, z + oz);
        if (B.isAir(wall) || wall.contains("_door")) return;
        if (B.name(s.get(x + 2 * ox, y, z + 2 * oz)).equals("air")) return; // pas un mur exterieur
        set(x, y, z, m.at(x, y, z));
    }

    /** Rayonnages pleine hauteur contre un mur (cases d'air seulement), etageres sculptees en grappes. */
    private void lineBooks(int x1, int z1, int x2, int z2, int y1, int y2, Dir face) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int y = y1; y <= y2; y++) {
                    if (!isAir(x, y, z)) continue;
                    // 1.9.1 : jamais devant une porte du mur (la porte nord de la bibliotheque etait bouchee)
                    if (y < U && nextToDoor(x, y, z)) continue;
                    set(x, y, z, y == U ? "minecraft:spruce_planks" : book(x, y, z, face));
                }
    }

    private boolean nextToDoor(int x, int y, int z) {
        for (Dir d : Dir.values())
            for (int dy = -1; dy <= 0; dy++)
                if (B.name(s.get(x + d.dx, y + dy, z + d.dz)).endsWith("_door")) return true;
        return false;
    }

    private static final Palette BOOK_SPLIT = Palette.of(22, "minecraft:bookshelf", 86, "minecraft:chiseled_bookshelf", 14).period(5);

    /** Bibliotheque : etagere pleine ou sculptee (tournee vers l'allee, emplacements remplis selon un hachage). */
    private String book(int x, int y, int z, Dir face) {
        String b = BOOK_SPLIT.at(x, y, z);
        if (!b.contains("chiseled")) return b;
        StringBuilder sb = new StringBuilder("minecraft:chiseled_bookshelf[facing=").append(face.id());
        for (int i = 0; i < 6; i++)
            sb.append(",slot_").append(i).append("_occupied=").append(Noise.hash(91, x * 7 + i, y, z) < 0.7);
        return sb.append(']').toString();
    }

    /** Cloison d'une epaisseur le long de z (x fixe), avec poteaux saillants tous les 4. */
    private void partitionX(int x, int z1, int z2, int y1, int y2) {
        if (y1 == F + 1) for (int z = z1; z <= z2; z++) set(x, F, z, "minecraft:dark_oak_planks");
        for (int z = z1; z <= z2; z++)
            for (int y = y1; y <= y2; y++) {
                if (y == U) {
                    set(x, y, z, "minecraft:spruce_planks");
                    continue;
                }
                set(x, y, z, partitionBlock(y, z - z1));
            }
        for (int z = z1 + 2; z <= z2 - 1; z += 4)
            for (int y = y1; y <= y2; y++) {
                if (y == U) continue;
                setIfAir(x - 1, y, z, log("minecraft:stripped_dark_oak_log", 'y'));
                setIfAir(x + 1, y, z, log("minecraft:stripped_dark_oak_log", 'y'));
            }
    }

    private void partitionZ(int z, int x1, int x2, int y1, int y2) {
        if (y1 == F + 1) for (int x = x1; x <= x2; x++) set(x, F, z, "minecraft:dark_oak_planks");
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++) {
                if (y == U) {
                    set(x, y, z, "minecraft:spruce_planks");
                    continue;
                }
                set(x, y, z, partitionBlock(y, x - x1));
            }
        for (int x = x1 + 2; x <= x2 - 1; x += 4)
            for (int y = y1; y <= y2; y++) {
                if (y == U) continue;
                setIfAir(x, y, z - 1, log("minecraft:stripped_dark_oak_log", 'y'));
                setIfAir(x, y, z + 1, log("minecraft:stripped_dark_oak_log", 'y'));
            }
    }

    private String partitionBlock(int y, int t) {
        int ry = y >= U ? y - U : y - F;
        if (ry <= 2) return "minecraft:spruce_planks";
        if (ry == 3) return "minecraft:stripped_dark_oak_wood";
        return (t % 4 == 2) ? "minecraft:stripped_dark_oak_log" : "minecraft:spruce_planks";
    }

    /**
     * Passage en arc dans une cloison : cases x1..x2 / z1..z2 videes sur {@code hgt} de haut a partir de y0,
     * linteau en escaliers retournes aux coins, poteaux colles a la cloison retires.
     */
    private void arch(int x1, int x2, int z1, int z2, int y0, int hgt, char along) {
        air(x1, y0, z1, x2, y0 + hgt - 1, z2);
        // les poteaux saillants devant le passage
        if (along == 'z') {
            for (int z = z1; z <= z2; z++)
                for (int y = y0; y < y0 + hgt; y++) {
                    clearPost(x1 - 1, y, z);
                    clearPost(x1 + 1, y, z);
                }
            set(x1, y0 + hgt - 1, z1, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
            set(x1, y0 + hgt - 1, z2, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, true));
        } else {
            for (int x = x1; x <= x2; x++)
                for (int y = y0; y < y0 + hgt; y++) {
                    clearPost(x, y, z1 - 1);
                    clearPost(x, y, z1 + 1);
                }
            set(x1, y0 + hgt - 1, z1, stairs("minecraft:dark_oak_stairs", Dir.WEST, true));
            set(x2, y0 + hgt - 1, z1, stairs("minecraft:dark_oak_stairs", Dir.EAST, true));
        }
    }

    private void clearPost(int x, int y, int z) {
        if (s.get(x, y, z).startsWith("minecraft:stripped_dark_oak_log")) set(x, y, z, B.AIR);
    }

    /** Lancette en ogive (vitrail) au-dessus de la porte du pavillon, plan P-2 du mur sud (z 16). */
    private void vitrailPavillon() {
        String[] colors = {"minecraft:red_stained_glass_pane", "minecraft:orange_stained_glass_pane",
                "minecraft:yellow_stained_glass_pane", "minecraft:purple_stained_glass_pane", "minecraft:blue_stained_glass_pane"};
        for (int x = -2; x <= 2; x++)
            for (int y = 69; y <= 78; y++) {
                int ax = Math.abs(x);
                if (y >= 76 && ax > (79 - y) / 2) continue; // ogive
                set(x, y, 17, B.AIR);
                set(x, y, 18, B.AIR);
                String c = colors[(ax + (y / 2)) % colors.length];
                if (ax == 0 && y >= 72 && y <= 74) c = "minecraft:white_stained_glass_pane";
                set(x, y, 16, B.pane(c, false, x < 2, false, x > -2));
            }
        for (int x = -3; x <= 3; x++) {
            set(x, 68, 18, stairs("minecraft:quartz_stairs", Dir.NORTH, true));
        }
        set(-3, 69, 18, "minecraft:quartz_pillar");
        set(3, 69, 18, "minecraft:quartz_pillar");
        for (int y = 69; y <= 77; y++) {
            set(-3, y, 18, "minecraft:quartz_pillar");
            set(3, y, 18, "minecraft:quartz_pillar");
            set(-3, y, 17, BRICK.at(-3, y, 17));
            set(3, y, 17, BRICK.at(3, y, 17));
        }
        set(-2, 78, 18, stairs("minecraft:quartz_stairs", Dir.EAST, true));
        set(2, 78, 18, stairs("minecraft:quartz_stairs", Dir.WEST, true));
        set(-1, 79, 18, stairs("minecraft:quartz_stairs", Dir.EAST, true));
        set(1, 79, 18, stairs("minecraft:quartz_stairs", Dir.WEST, true));
        set(0, 80, 18, "minecraft:chiseled_quartz_block");
        // marquise au-dessus de la porte
        for (int x = -2; x <= 2; x++) set(x, 68, 19, stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));
        set(-2, 67, 19, "minecraft:dark_oak_fence");
        set(2, 67, 19, "minecraft:dark_oak_fence");
        set(-2, 66, 19, "minecraft:dark_oak_fence");
        set(2, 66, 19, "minecraft:dark_oak_fence");
        set(-2, 65, 19, "minecraft:dark_oak_fence");
        set(2, 65, 19, "minecraft:dark_oak_fence");
        set(0, 67, 19, hanging("minecraft:lantern"));
    }

    // ---------------------------------------------------------------- escaliers

    /** Escalier d'honneur du hall : deux volees le long des murs ouest et est (z 7 -> 1), limon et rampe. */
    private void grandStairs() {
        for (int side : new int[]{-1, 1}) {
            int xa = side < 0 ? -11 : 10, xb = side < 0 ? -10 : 11, xs = side < 0 ? -9 : 9;
            // 1.9.1 : la volee part de z 6 (et non plus 7) : la premiere marche etait collee au mur sud du hall
            for (int k = 0; k <= 6; k++) {
                int z = 6 - k, y = F + 1 + k;
                for (int x = xa; x <= xb; x++) {
                    fill(x, F + 1, z, x, y - 1, z, "minecraft:dark_oak_planks");
                    set(x, y, z, stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));
                    air(x, y + 1, z, x, y + 3, z);
                }
                if (k >= 2) {
                    fill(xs, F + 1, z, xs, y, z, "minecraft:dark_oak_planks");
                    set(xs, y + 1, z, "minecraft:dark_oak_fence");
                }
            }
            // depart de la rampe : poteau sculpte et lanterne
            set(xs, F + 1, 5, "minecraft:stripped_dark_oak_log");
            set(xs, F + 2, 5, "minecraft:stripped_dark_oak_log");
            set(xs, F + 3, 5, standing("minecraft:lantern"));
            // l'ancienne premiere marche (z 7) redevient du sol libre devant la volee
            for (int x = xa; x <= xb; x++) air(x, F + 1, 7, x, F + 3, 7);
        }
        // balustrade du palier (z 1) au-dessus du hall
        for (int x = -8; x <= 8; x++) set(x, U + 1, 1, "minecraft:dark_oak_fence");
        fill(-8, U, 1, 8, U, 1, "minecraft:dark_oak_planks");
        set(-8, U + 1, 1, "minecraft:stripped_dark_oak_log");
        set(8, U + 1, 1, "minecraft:stripped_dark_oak_log");
        set(0, U + 1, 1, "minecraft:stripped_dark_oak_log");
        for (int x = -8; x <= 8; x++) set(x, U - 1, 1, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, true));
        for (int x = -6; x <= 6; x += 4) set(x, U, 2, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
    }

    /**
     * Escalier en colimacon de 3 x 3 autour d'un pilier : ring[0..6] = marches (y 65..71), ring[7] = sortie a
     * l'etage. Plancher de l'etage ouvert au-dessus des marches 0..5, garde-corps autour du trou.
     */
    private void spiral(int cx, int cz, int[][] ring) {
        for (int y = F + 1; y <= T - 1; y++) set(cx, y, cz, log("minecraft:stripped_dark_oak_log", 'y'));
        for (int k = 0; k <= 7; k++) {
            int x = cx + ring[k][0], z = cz + ring[k][1];
            air(x, F + 1, z, x, T - 1, z);
        }
        for (int k = 0; k <= 6; k++) {
            int x = cx + ring[k][0], z = cz + ring[k][1];
            int nx = cx + ring[k + 1][0], nz = cz + ring[k + 1][1];
            Dir face = Dir.fromDelta(nx - x, nz - z);
            int y = F + 1 + k;
            fill(x, F + 1, z, x, y - 1, z, "minecraft:dark_oak_planks");
            set(x, y, z, stairs("minecraft:dark_oak_stairs", face, false));
        }
        int ex = cx + ring[7][0], ez = cz + ring[7][1];
        set(ex, U, ez, "minecraft:dark_oak_planks");
        // garde-corps a l'etage autour des marches 0..5
        for (int k = 0; k <= 5; k++) {
            int x = cx + ring[k][0], z = cz + ring[k][1];
            for (Dir d : Dir.values()) {
                int ax = x + d.dx, az = z + d.dz;
                if (Math.abs(ax - cx) <= 1 && Math.abs(az - cz) <= 1) continue;
                String below = s.get(ax, U, az);
                if (B.isSolid(below) && isAir(ax, U + 1, az)) set(ax, U + 1, az, "minecraft:dark_oak_fence");
            }
        }
        set(cx, T - 1, cz, log("minecraft:stripped_dark_oak_log", 'y'));
        set(cx, U + 1, cz, log("minecraft:stripped_dark_oak_log", 'y'));
        set(cx, T - 2, cz, "minecraft:chiseled_bookshelf[facing=north,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]");
    }

    /**
     * Escalier droit de 2 de large : marche k en (x0 + k * dir, z0 [+1 de cote]) a y 65 + k (k = 0..6),
     * plancher de l'etage ouvert au-dessus des marches 0..5, garde-corps sur les cotes du trou.
     */
    private void straightStairs(int x0, int z0, Dir dir, int width) {
        Dir side = dir == Dir.NORTH ? Dir.EAST : Dir.SOUTH; // largeur
        for (int k = 0; k <= 6; k++) {
            for (int w = 0; w < width; w++) {
                int x = x0 + dir.dx * k + side.dx * w, z = z0 + dir.dz * k + side.dz * w;
                int y = F + 1 + k;
                air(x, F + 1, z, x, T - 1, z);
                fill(x, F + 1, z, x, y - 1, z, "minecraft:spruce_planks");
                set(x, y, z, stairs("minecraft:spruce_stairs", dir, false));
                if (k <= 5) set(x, U, z, B.AIR);
            }
        }
        // garde-corps : de chaque cote du trou et au depart (cote bas)
        for (int k = 0; k <= 5; k++) {
            for (int w : new int[]{-1, width}) {
                int x = x0 + dir.dx * k + side.dx * w, z = z0 + dir.dz * k + side.dz * w;
                if (B.isSolid(s.get(x, U, z)) && isAir(x, U + 1, z)) set(x, U + 1, z, "minecraft:spruce_fence");
            }
        }
        for (int w = -1; w <= width; w++) {
            int x = x0 - dir.dx + side.dx * w, z = z0 - dir.dz + side.dz * w;
            if (B.isSolid(s.get(x, U, z)) && isAir(x, U + 1, z)) set(x, U + 1, z, "minecraft:spruce_fence");
        }
        // 1.9.1 : rien devant la premiere marche (un poteau de cloison bouchait une des deux voies)
        for (int w = 0; w < width; w++) {
            int x = x0 - dir.dx + side.dx * w, z = z0 - dir.dz + side.dz * w;
            for (int y = F + 1; y < U; y++) clearPost(x, y, z);
            // ... ni sur le palier d'arrivee, a l'etage
            int lx = x0 + dir.dx * 7 + side.dx * w, lz = z0 + dir.dz * 7 + side.dz * w;
            for (int y = U + 1; y < T; y++) clearPost(lx, y, lz);
        }
    }

    // ================================================================ toits

    private void roofs() {
        RoofStyle rs = new RoofStyle().stairs(ROOF_STAIRS).ridge("minecraft:deepslate_tile_slab")
                .body(Mat.of("minecraft:deepslate_tiles")).gable(BRICK).gablePost(null).overhang(1).spacing(4);
        Roof.build(s, rs,
                Roof.hip(-32, -18, -12, 12, T),
                Roof.hip(-12, -24, 12, 10, T),
                Roof.hip(12, -18, 32, 12, T),
                Roof.hip(-6, 10, 6, 18, T + 2));
        // toits a la Mansart : on arase au-dessus de DECK (sauf le pavillon), terrasse et cretes en fer forge
        for (int x = -33; x <= 33; x++)
            for (int z = -25; z <= 13; z++) {
                boolean inPav = x >= -7 && x <= 7 && z >= 9;
                if (inPav) continue;
                int top = -1;
                for (int y = Y_HI; y >= DECK; y--)
                    if (!isAir(x, y, z)) {
                        top = y;
                        break;
                    }
                if (top < 0) continue;
                air(x, DECK, z, x, Y_HI, z);
                set(x, DECK, z, "minecraft:deepslate_tiles");
            }
        // bord de terrasse : cretes en fer forge
        for (int x = -33; x <= 33; x++)
            for (int z = -25; z <= 13; z++) {
                if (!s.get(x, DECK, z).equals("minecraft:deepslate_tiles")) continue;
                boolean edge = false;
                for (Dir d : Dir.values())
                    if (!s.get(x + d.dx, DECK, z + d.dz).equals("minecraft:deepslate_tiles") && isAir(x + d.dx, DECK + 1, z + d.dz)) edge = true;
                if (edge && !(x >= -8 && x <= 8 && z >= 8)) set(x, DECK + 1, z, "minecraft:iron_bars");
            }
        // cheminees (sur les terrasses des ailes et du corps central)
        int[][] chim = {{-24, -6}, {-20, 6}, {22, -9}, {26, 6}, {-7, -21}};
        for (int[] c : chim) {
            Roof.chimney(s, c[0], c[1], c[0] + 1, c[1] + 1, DECK - 3, DECK + 4, BRICK, "minecraft:stone_brick_slab");
            set(c[0], DECK + 5, c[1], "minecraft:flower_pot");
            set(c[0] + 1, DECK + 5, c[1] + 1, "minecraft:flower_pot");
        }
        lantern();
        dormers();
        // epi du pavillon
        int top = 0;
        for (int y = Y_HI; y > T; y--) if (!isAir(0, y, 14)) { top = y; break; }
        if (top > 0) {
            set(0, top + 1, 14, "minecraft:quartz_pillar");
            set(0, top + 2, 14, "minecraft:lightning_rod");
        }
    }

    /** Lanterneau vitre au-dessus de la nef : puits de lumiere y 79..84, verriere y 85..89, toit en croupe. */
    private void lantern() {
        int x1 = -5, x2 = 5, z1 = -16, z2 = -8;
        // puits : on ouvre le plafond et on ferme le comble autour
        air(x1 + 1, T, z1 + 1, x2 - 1, DECK + 6, z2 - 1);
        for (int y = T; y <= DECK; y++)
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    if (x != x1 && x != x2 && z != z1 && z != z2) continue;
                    boolean post = (x == x1 || x == x2) && (z == z1 || z == z2 || (z - z1) % 4 == 0)
                            || (z == z1 || z == z2) && (x - x1) % 5 == 0;
                    set(x, y, z, post ? "minecraft:stripped_dark_oak_log" : "minecraft:dark_oak_planks");
                }
        // verriere : poteaux en quartz, vitres, corniche
        for (int y = DECK + 1; y <= DECK + 5; y++)
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    if (x != x1 && x != x2 && z != z1 && z != z2) continue;
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    boolean post = corner || ((x == x1 || x == x2) && (z - z1) % 4 == 0) || ((z == z1 || z == z2) && (x - x1) % 5 == 0);
                    if (y == DECK + 5) set(x, y, z, "minecraft:smooth_quartz");
                    else set(x, y, z, post ? "minecraft:quartz_pillar" : "minecraft:glass_pane");
                }
        RoofStyle rs = new RoofStyle().stairs(ROOF_STAIRS).ridge("minecraft:deepslate_tile_slab")
                .body(Mat.of("minecraft:deepslate_tiles")).gablePost(null).overhang(1).spacing(4);
        int top = Roof.build(s, rs, Roof.hip(x1, z1, x2, z2, DECK + 5));
        // le comble de la verriere reste vide : on rouvre l'interieur sous les pentes (on voit la charpente)
        for (int y = DECK + 6; y < top; y++)
            for (int x = x1 + 1; x <= x2 - 1; x++)
                for (int z = z1 + 1; z <= z2 - 1; z++)
                    if (s.get(x, y, z).equals("minecraft:deepslate_tiles") && !isAir(x, y + 1, z)) set(x, y, z, "minecraft:dark_oak_planks");
        set(0, top + 1, -12, "minecraft:lightning_rod");
        // lustre de la nef (chaine depuis le faitage)
        chandelier(0, top - 1, -12, top - 1 - (U + 3), true);
    }

    /** Lucarnes sur les pentes sud et nord des ailes (decor : les combles ne sont pas accessibles). */
    private void dormers() {
        int[][] list = {{-26, 12, 1}, {-17, 12, 1}, {19, 12, 1}, {27, 12, 1}, {-22, -18, -1}, {24, -18, -1}};
        for (int[] d : list) {
            int x = d[0], z0 = d[1], dz = d[2];
            Dir out = dz > 0 ? Dir.SOUTH : Dir.NORTH;
            for (int k = 1; k <= 3; k++) {
                int z = z0 - dz * (k - 1);
                for (int xx = x - 1; xx <= x + 1; xx++)
                    for (int y = T + 1; y <= T + 4; y++) {
                        boolean side = xx != x;
                        if (k == 1) set(xx, y, z, side ? "minecraft:smooth_quartz" : (y <= T + 3 ? B.pane("minecraft:glass_pane", false, true, false, true) : "minecraft:smooth_quartz"));
                        else set(xx, y, z, side ? BRICK.at(xx, y, z) : (y <= T + 3 ? B.AIR : "minecraft:dark_oak_planks"));
                    }
            }
            for (int k = 0; k <= 3; k++) {
                int z = z0 - dz * (k - 1);
                set(x - 2, T + 5, z, stairs("minecraft:deepslate_tile_stairs", Dir.EAST, false));
                set(x + 2, T + 5, z, stairs("minecraft:deepslate_tile_stairs", Dir.WEST, false));
                set(x - 1, T + 5, z, stairs("minecraft:deepslate_tile_stairs", Dir.EAST, false));
                set(x + 1, T + 5, z, stairs("minecraft:deepslate_tile_stairs", Dir.WEST, false));
                set(x, T + 5, z, "minecraft:deepslate_tiles");
                set(x, T + 6, z, slab("minecraft:deepslate_tile_slab", "bottom"));
                set(x - 1, T + 4, z, B.isAir(s.get(x - 1, T + 4, z)) ? stairs("minecraft:deepslate_tile_stairs", Dir.EAST, true) : s.get(x - 1, T + 4, z));
            }
            set(x, T + 1, z0 + dz, stairs("minecraft:quartz_stairs", out.opposite(), true));
        }
    }

    // ================================================================ tourelle

    static final int TX = -33, TZ = 13;

    private void turret() {
        int top = T + 4; // corniche en 82
        // on degage la tourelle (toits et murs de l'aile) puis murs ronds
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = TX + dx, z = TZ + dz;
                if (r <= 6.0) air(x, F + 1, z, x, Y_HI, z);
                if (r <= 4.5) {
                    set(x, F, z, (Math.floorMod(dx + dz, 2) == 0) ? "minecraft:polished_deepslate" : "minecraft:polished_diorite");
                    if (x <= -32 || z >= 12) set(x, G, z, "minecraft:dirt");
                } else if (r <= 5.5) {
                    for (int y = G; y <= top; y++) {
                        String b;
                        if (y <= F) b = PLINTH.at(x, y, z);
                        else if (y == U || y == top) b = "minecraft:smooth_quartz";
                        else b = BRICK.at(x, y, z);
                        set(x, y, z, b);
                    }
                    // meurtrieres / fenetres hautes etroites
                    double ang = Math.atan2(dz, dx);
                    int sector = (int) Math.floor((ang + Math.PI) / (Math.PI / 4));
                    if (r > 5.0 && sector % 2 == 0 && Math.abs(dx) != Math.abs(dz)) {
                        for (int y = F + 2; y <= F + 4; y++) set(x, y, z, "minecraft:glass_pane");
                        for (int y = U + 2; y <= U + 5; y++) set(x, y, z, "minecraft:glass_pane");
                    }
                } else if (r <= 6.0) {
                    set(x, top, z, stairs("minecraft:dark_oak_stairs", Dir.fromDelta(-Integer.signum(dx) * (Math.abs(dx) >= Math.abs(dz) ? 1 : 0), -Integer.signum(dz) * (Math.abs(dz) > Math.abs(dx) ? 1 : 0)), true));
                    set(x, U, z, stairs("minecraft:quartz_stairs", Dir.fromDelta(-Integer.signum(dx) * (Math.abs(dx) >= Math.abs(dz) ? 1 : 0), -Integer.signum(dz) * (Math.abs(dz) > Math.abs(dx) ? 1 : 0)), true));
                }
            }
        // toit conique en ardoise (un cran par niveau), epi
        for (int i = 0; i <= 11; i++) {
            double r = 6.3 - i * 0.55;
            int y = top + 1 + i;
            for (int dx = -7; dx <= 7; dx++)
                for (int dz = -7; dz <= 7; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r) continue;
                    boolean shell = d > r - 1.1;
                    set(TX + dx, y, TZ + dz, shell ? (i % 3 == 2 ? "minecraft:deepslate_bricks" : "minecraft:deepslate_tiles") : "minecraft:dark_oak_planks");
                }
        }
        set(TX, top + 13, TZ, "minecraft:quartz_pillar");
        set(TX, top + 14, TZ, "minecraft:lightning_rod");
        // plafond de la tourelle
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                if (dx * dx + dz * dz <= 20.25) set(TX + dx, top, TZ + dz, "minecraft:dark_oak_planks");
        // escalier en colimacon autour du pilier central (anneau de rayon 2)
        int[][] ring = {{-1, 2}, {0, 2}, {1, 2}, {2, 2}, {2, 1}, {2, 0}, {2, -1}, {2, -2}};
        for (int y = F + 1; y < top; y++) set(TX, y, TZ, "minecraft:polished_basalt[axis=y]");
        // plancher de l'etage de la tourelle
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                if (dx * dx + dz * dz <= 20.25 && !(dx == 0 && dz == 0)) set(TX + dx, U, TZ + dz, "minecraft:spruce_planks");
        for (int k = 0; k <= 6; k++) {
            int x = TX + ring[k][0], z = TZ + ring[k][1];
            Dir face = Dir.fromDelta(ring[k + 1][0] - ring[k][0], ring[k + 1][1] - ring[k][1]);
            int y = F + 1 + k;
            fill(x, F + 1, z, x, y - 1, z, "minecraft:spruce_planks");
            set(x, y, z, stairs("minecraft:spruce_stairs", face, false));
            if (k <= 5) set(x, U, z, B.AIR);
        }
        for (int k = 0; k <= 5; k++) {
            int x = TX + ring[k][0], z = TZ + ring[k][1];
            for (Dir d : Dir.values()) {
                int ax = x + d.dx, az = z + d.dz;
                boolean inRing = false;
                for (int j = 0; j <= 7; j++) if (TX + ring[j][0] == ax && TZ + ring[j][1] == az) inRing = true;
                if (inRing || (ax == TX && az == TZ)) continue;
                if (B.isSolid(s.get(ax, U, az)) && isAir(ax, U + 1, az)) set(ax, U + 1, az, "minecraft:spruce_fence");
            }
        }
        // passages : salle a manger / bureau (nord-est) et jardin (sud-ouest)
        for (int[] c : new int[][]{{-30, 9}, {-29, 10}, {-30, 10}, {-31, 9}}) {
            air(c[0], F + 1, c[1], c[0], F + 3, c[1]);
            air(c[0], U + 1, c[1], c[0], U + 3, c[1]);
            set(c[0], F, c[1], "minecraft:polished_deepslate");
            set(c[0], U, c[1], "minecraft:spruce_planks");
            set(c[0], F + 4, c[1], "minecraft:smooth_quartz");
            set(c[0], U + 4, c[1], "minecraft:smooth_quartz");
        }
        for (int[] c : new int[][]{{-37, 16}, {-36, 17}, {-37, 17}, {-36, 16}}) {
            air(c[0], F + 1, c[1], c[0], F + 3, c[1]);
            set(c[0], F, c[1], "minecraft:polished_deepslate");
            set(c[0], F + 4, c[1], "minecraft:smooth_quartz");
        }
        set(-38, F, 18, stairs("minecraft:stone_brick_stairs", Dir.NORTH, false));
        set(-38, F, 17, stairs("minecraft:stone_brick_stairs", Dir.EAST, false));
        set(-37, F, 18, stairs("minecraft:stone_brick_stairs", Dir.NORTH, false));
        // decor : lunette astronomique et carte du ciel a l'etage, lanternes
        set(TX - 3, U + 1, TZ - 1, "minecraft:spruce_fence");
        set(TX - 3, U + 2, TZ - 1, "minecraft:end_rod[facing=up]"); // 1.9.1 : debout sur son support
        set(TX - 4, U + 1, TZ, "minecraft:cartography_table");
        set(TX - 1, top - 1, TZ - 3, hanging("minecraft:lantern"));
        set(TX + 1, top - 1, TZ + 3, hanging("minecraft:lantern"));
        set(TX - 3, U - 1, TZ + 2, hanging("minecraft:lantern"));
        set(TX + 3, U - 1, TZ + 2, hanging("minecraft:lantern"));
        set(TX - 4, F + 1, TZ + 1, "minecraft:potted_fern");
        set(TX - 3, F + 1, TZ - 3, "minecraft:spruce_stairs[facing=west,half=bottom,shape=straight]");
        set(TX - 4, F + 1, TZ - 1, "minecraft:spruce_stairs[facing=west,half=bottom,shape=straight]");
    }

    // ================================================================ serre

    private void greenhouse() {
        int x1 = 32, x2 = 45, z1 = -14, z2 = 6;
        // socle, sol
        for (int x = x1 + 1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, G, z, "minecraft:dirt");
                boolean border = x == x2 || z == z1 || z == z2;
                set(x, F, z, border ? PLINTH.at(x, F, z) : "minecraft:mossy_stone_bricks");
            }
        // murs vitres : poteaux en cuivre vert tous les 3, vitres jusqu'a 70, ceinture en 71
        for (int x = x1 + 1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x2 || z == z1 || z == z2;
                if (!wall) continue;
                boolean post = (x == x2 && (z - z1) % 3 == 0) || ((z == z1 || z == z2) && (x - x1) % 3 == 0) || (x == x2 && (z == z1 || z == z2));
                for (int y = F + 1; y <= U - 1; y++) set(x, y, z, post ? "minecraft:waxed_oxidized_copper" : "minecraft:glass_pane");
                set(x, U, z, "minecraft:waxed_oxidized_cut_copper");
            }
        // contreforts bas (escaliers de pierre) au pied de chaque poteau, cote jardin
        for (int z = z1; z <= z2; z += 3) set(x2 + 1, F, z, stairs("minecraft:stone_brick_stairs", Dir.WEST, false));
        for (int x = x1 + 3; x <= x2; x += 3) {
            set(x, F, z1 - 1, stairs("minecraft:stone_brick_stairs", Dir.SOUTH, false));
            set(x, F, z2 + 1, stairs("minecraft:stone_brick_stairs", Dir.NORTH, false));
        }
        // toit vitre en pente (faitage nord-sud au milieu, x 39)
        int mid = 39;
        for (int x = x1 + 1; x <= x2; x++) {
            int d = Math.min(x - x1, x2 - x);
            int y = U + Math.min(d, 6);
            for (int z = z1; z <= z2; z++) {
                boolean rib = (z - z1) % 4 == 0;
                if (x == mid) set(x, y, z, rib ? "minecraft:waxed_oxidized_copper" : "minecraft:waxed_oxidized_cut_copper");
                else set(x, y, z, rib ? stairs("minecraft:waxed_oxidized_cut_copper_stairs", x < mid ? Dir.EAST : Dir.WEST, false) : "minecraft:glass");
            }
        }
        // cretes de part et d'autre du faitage, au droit des nervures
        for (int z = z1; z <= z2; z += 4) {
            set(mid - 2, U + 6, z, stairs("minecraft:waxed_oxidized_cut_copper_stairs", Dir.EAST, false));
            set(mid + 1, U + 6, z, stairs("minecraft:waxed_oxidized_cut_copper_stairs", Dir.WEST, false));
        }
        // pignons vitres
        for (int x = x1 + 1; x <= x2; x++) {
            int d = Math.min(x - x1, x2 - x);
            for (int y = U + 1; y < U + Math.min(d, 6); y++)
                for (int z : new int[]{z1, z2}) set(x, y, z, (x - x1) % 3 == 0 ? "minecraft:waxed_oxidized_copper" : "minecraft:glass_pane");
        }
        // portes : est (vers l'allee) et sud (vers le jardin de devant)
        for (int z = -4; z <= -3; z++) {
            air(x2, F + 1, z, x2, F + 2, z);
            set(x2, F + 3, z, "minecraft:waxed_oxidized_cut_copper");
            set(x2, F, z, "minecraft:mossy_stone_bricks");
            set(x2 + 1, F, z, stairs("minecraft:stone_brick_stairs", Dir.WEST, false));
        }
        for (int x = 38; x <= 39; x++) {
            air(x, F + 1, z2, x, F + 2, z2);
            set(x, F + 3, z2, "minecraft:waxed_oxidized_cut_copper");
            set(x, F, z2, "minecraft:mossy_stone_bricks");
            set(x, F, z2 + 1, stairs("minecraft:stone_brick_stairs", Dir.NORTH, false));
        }
        // plates-bandes le long des vitres (terre + mousse), allees en boucle, bassin central
        for (int x = 34; x <= 44; x++)
            for (int z = -13; z <= 5; z++) {
                boolean outer = x == 44 || z == -13 || z == 5;
                boolean center = x >= 37 && x <= 41 && z >= -8 && z <= 0;
                boolean door = (z >= -5 && z <= -2 && x >= 43) || (x >= 37 && x <= 40 && z >= 4);
                if ((outer || center) && !door) {
                    set(x, F, z, center ? "minecraft:rooted_dirt" : "minecraft:moss_block");
                }
            }
        // bord du massif central en briques moussues (petit muret de 1), bassin au milieu
        for (int x = 37; x <= 41; x++)
            for (int z = -8; z <= 0; z++) {
                boolean edge = x == 37 || x == 41 || z == -8 || z == 0;
                if (edge && !((x == 39) && (z == -8 || z == 0))) set(x, F + 1, z, "minecraft:mossy_stone_brick_wall");
            }
        for (int x = 38; x <= 40; x++)
            for (int z = -6; z <= -2; z++) {
                set(x, F, z, B.WATER);
                set(x, F - 1, z, "minecraft:mossy_cobblestone");
            }
        set(39, F, -4, "minecraft:mossy_stone_bricks");
        set(39, F + 1, -4, "minecraft:mossy_stone_brick_wall");
        set(39, F + 2, -4, "minecraft:water_cauldron[level=3]");
        set(38, F + 1, -5, "minecraft:lily_pad");
        set(40, F + 1, -3, "minecraft:lily_pad");
        // plantes (en grappes, decidees par emplacement)
        String[] plants = {"minecraft:fern", "minecraft:flowering_azalea", "minecraft:azalea", "minecraft:large_fern",
                "minecraft:lily_of_the_valley", "minecraft:blue_orchid", "minecraft:allium", "minecraft:short_grass"};
        for (int x = 34; x <= 44; x++)
            for (int z = -13; z <= 5; z++) {
                String g = s.get(x, F, z);
                if (!(g.contains("moss_block") || g.contains("rooted_dirt")) || !isAir(x, F + 1, z)) continue;
                double u = noise.uniform2(x / 3.0, z / 3.0);
                if (u < 0.15) continue;
                String p = plants[(int) (noise.uniform2(x / 2.0 + 40, z / 2.0) * plants.length) % plants.length];
                if (p.equals("minecraft:large_fern")) {
                    if (!isAir(x, F + 2, z)) continue;
                    set(x, F + 1, z, "minecraft:large_fern[half=lower]");
                    set(x, F + 2, z, "minecraft:large_fern[half=upper]");
                } else set(x, F + 1, z, p);
            }
        // petits arbres (bouleaux) dans deux coins et palmier-fougere
        new Tree("minecraft:birch_log", "minecraft:birch_leaves", "minecraft:flowering_azalea_leaves").height(5).crown(2.0).roots(false)
                .place(s, 35, F + 1, -12, 77);
        new Tree("minecraft:cherry_log", "minecraft:cherry_leaves", null).height(5).crown(2.2).roots(false)
                .place(s, 43, F + 1, 4, 78);
        // feuillage du toit : rien au-dessus de la ceinture (les arbres restent sous le verre)
        for (int x = x1 + 1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = U + 1; y <= U + 8; y++) {
                    String b = s.get(x, y, z);
                    if (b.contains("leaves") || b.contains("_log")) {
                        int d = Math.min(x - x1, x2 - x);
                        if (y >= U + Math.min(d, 6)) set(x, y, z, B.AIR);
                    }
                }
        // bancs, lanternes suspendues aux pannes, pots
        bench(36, F + 1, 3, Dir.NORTH);
        bench(42, F + 1, -10, Dir.SOUTH);
        // 1.9.1 : la chaine monte jusqu'a la verriere ; les deux lanternes de x 42 (posees au-dessus du verre, hors
        // de la serre, avec une chaine dans le vide) sont ramenees en x 40, sous la verriere
        for (int[] l : new int[][]{{36, -9}, {40, -1}, {36, 2}, {40, -12}, {39, -11}, {39, 3}}) {
            if (!isAir(l[0], U + 4, l[1]) || !isAir(l[0], U + 3, l[1])) continue;
            int top = U + 5;
            while (top <= U + 14 && isAir(l[0], top, l[1])) top++;
            if (top > U + 14) continue; // pas de verriere au-dessus : pas de lanterne
            set(l[0], U + 4, l[1], hanging("minecraft:lantern"));
            for (int y = U + 5; y < top; y++) set(l[0], y, l[1], "minecraft:iron_chain");
        }
        set(34, F + 1, -1, "minecraft:potted_red_tulip");
        set(34, F + 1, 0, "minecraft:potted_oxeye_daisy");
        set(43, F + 1, -7, "minecraft:potted_cactus");
    }

    // ================================================================ mobilier (modules)

    /** Lustre : chaine depuis yTop sur len blocs, puis couronne de lanternes et bougies. */
    private void chandelier(int x, int yTop, int z, int len, boolean big) {
        int yb = yTop - len;
        for (int y = yb + 1; y <= yTop; y++) set(x, y, z, "minecraft:iron_chain");
        set(x, yb, z, "minecraft:iron_chain");
        set(x, yb - 1, z, hanging("minecraft:lantern"));
        for (Dir d : Dir.values()) {
            set(x + d.dx, yb, z + d.dz, trap("minecraft:dark_oak_trapdoor", d, true, false));
            set(x + d.dx, yb + 1, z + d.dz, "minecraft:candle[candles=3,lit=true]");
            if (big) {
                set(x + 2 * d.dx, yb, z + 2 * d.dz, trap("minecraft:dark_oak_trapdoor", d, true, false));
                set(x + 2 * d.dx, yb - 1, z + 2 * d.dz, hanging("minecraft:lantern"));
            }
        }
    }

    private void armchair(int x, int y, int z, Dir faces) {
        // l'escalier « regarde » vers son dossier : le joueur assis fait face a faces
        set(x, y, z, stairs("minecraft:dark_oak_stairs", faces.opposite(), false));
        // accoudoirs : le panneau d'une trappe ouverte est du cote OPPOSE a facing -> facing vers l'exterieur
        set(x + faces.cw().dx, y, z + faces.cw().dz, trap("minecraft:dark_oak_trapdoor", faces.cw(), false, true));
        set(x + faces.ccw().dx, y, z + faces.ccw().dz, trap("minecraft:dark_oak_trapdoor", faces.ccw(), false, true));
    }

    private void bench(int x, int y, int z, Dir faces) {
        Dir along = faces.cw();
        for (int i = -1; i <= 1; i++) set(x + along.dx * i, y, z + along.dz * i, stairs("minecraft:spruce_stairs", faces.opposite(), false));
        setIfAir(x + along.dx * 2, y, z + along.dz * 2, trap("minecraft:spruce_trapdoor", along, false, true));
        setIfAir(x - along.dx * 2, y, z - along.dz * 2, trap("minecraft:spruce_trapdoor", along.opposite(), false, true));
    }

    /** Table de x1..x2 / z1..z2 (plateau en dalles hautes), chaises (escaliers) sur les longs cotes. */
    private void table(int x1, int z1, int x2, int z2, int y, String slabId, boolean chairs) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) set(x, y, z, slab(slabId, "top"));
        if (!chairs) return;
        boolean alongX = (x2 - x1) >= (z2 - z1);
        if (alongX) {
            for (int x = x1; x <= x2; x += 2) {
                setIfAir(x, y, z1 - 1, stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));
                setIfAir(x, y, z2 + 1, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
            }
        } else {
            for (int z = z1; z <= z2; z += 2) {
                setIfAir(x1 - 1, y, z, stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
                setIfAir(x2 + 1, y, z, stairs("minecraft:dark_oak_stairs", Dir.EAST, false));
            }
        }
    }

    /** Rangee de rayonnages d'une epaisseur, de hauteur hgt, extremites en poteaux, corniche en dalles. */
    private void shelfRow(int x1, int z1, int x2, int z2, int y, int hgt, boolean cap) {
        boolean alongX = x1 != x2;
        int len = alongX ? Math.abs(x2 - x1) : Math.abs(z2 - z1);
        for (int i = 0; i <= len; i++) {
            int x = alongX ? Math.min(x1, x2) + i : x1, z = alongX ? z1 : Math.min(z1, z2) + i;
            boolean end = i == 0 || i == len;
            for (int yy = y; yy < y + hgt; yy++) {
                if (end) set(x, yy, z, "minecraft:stripped_dark_oak_log");
                else {
                    Dir face = alongX ? (Noise.hash(5, x, yy, z) < 0.5 ? Dir.NORTH : Dir.SOUTH) : (Noise.hash(5, x, yy, z) < 0.5 ? Dir.EAST : Dir.WEST);
                    set(x, yy, z, book(x, yy, z, face));
                }
            }
            if (cap) set(x, y + hgt, z, end ? "minecraft:dark_oak_slab[type=bottom]" : (i % 3 == 1 ? "minecraft:candle[candles=2,lit=true]" : "minecraft:dark_oak_slab[type=bottom]"));
        }
    }

    private void fireplace(int x, int z, Dir faces, int y, int ceil) {
        Dir along = faces.cw();
        // corps en briques sombres, foyer de 1, manteau en quartz
        for (int i = -2; i <= 2; i++)
            for (int yy = y; yy <= ceil; yy++) {
                int xx = x + along.dx * i, zz = z + along.dz * i;
                set(xx, yy, zz, Math.abs(i) == 2 ? "minecraft:polished_blackstone_bricks" : "minecraft:nether_bricks");
            }
        int fx = x, fz = z;
        set(fx, y, fz, "minecraft:campfire[lit=true,signal_fire=false,facing=" + faces.id() + "]");
        set(fx, y + 1, fz, B.AIR);
        set(fx + along.dx, y, fz + along.dz, "minecraft:polished_blackstone_bricks");
        set(fx - along.dx, y, fz - along.dz, "minecraft:polished_blackstone_bricks");
        for (int i = -2; i <= 2; i++) {
            int xx = x + along.dx * i + faces.dx, zz = z + along.dz * i + faces.dz;
            setIfAir(xx, y + 2, zz, stairs("minecraft:quartz_stairs", faces.opposite(), true));
        }
        setIfAir(x + along.dx * 2 + faces.dx, y + 3, z + along.dz * 2 + faces.dz, "minecraft:candle[candles=3,lit=true]");
        setIfAir(x - along.dx * 2 + faces.dx, y + 3, z - along.dz * 2 + faces.dz, "minecraft:candle[candles=3,lit=true]");
        setIfAir(x + faces.dx, y + 3, z + faces.dz, "minecraft:flower_pot");
    }

    private void clock(int x, int y, int z, Dir faces) {
        set(x, y, z, "minecraft:dark_oak_planks");
        set(x, y + 1, z, log("minecraft:dark_oak_log", 'y'));
        set(x, y + 2, z, "minecraft:white_glazed_terracotta[facing=" + faces.id() + "]");
        set(x, y + 3, z, "minecraft:dark_oak_slab[type=bottom]");
        setIfAir(x + faces.dx, y + 1, z + faces.dz, trap("minecraft:dark_oak_trapdoor", faces, false, true));
    }

    private void globe(int x, int y, int z) {
        set(x, y, z, "minecraft:dark_oak_fence");
        set(x, y + 1, z, "minecraft:conduit[waterlogged=false]");
    }

    private void rug(int x1, int z1, int x2, int z2, int y, String border, String inner) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (!isAir(x, y, z)) continue;
                boolean b = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, y, z, b ? border : inner);
            }
    }

    private void piano(int x, int y, int z, Dir faces) {
        Dir along = faces.cw();
        for (int i = 0; i <= 2; i++) {
            int xx = x + along.dx * i, zz = z + along.dz * i;
            set(xx, y, zz, "minecraft:black_concrete");
            set(xx, y + 1, zz, "minecraft:polished_blackstone_slab[type=bottom]");
            setIfAir(xx + faces.dx, y, zz + faces.dz, i == 1 ? "minecraft:note_block" : "minecraft:polished_blackstone_slab[type=top]");
        }
        setIfAir(x + along.dx + faces.dx * 2, y, z + along.dz + faces.dz * 2, stairs("minecraft:dark_oak_stairs", faces, false));
        setIfAir(x + along.dx * 2, y + 2, z + along.dz * 2, "minecraft:candle[candles=2,lit=true]");
    }

    private void plant(int x, int y, int z) {
        String[] pots = {"minecraft:potted_fern", "minecraft:potted_azalea_bush", "minecraft:potted_flowering_azalea_bush",
                "minecraft:potted_dark_oak_sapling", "minecraft:potted_lily_of_the_valley"};
        set(x, y, z, pots[(int) (h(x, y, z) * pots.length)]);
    }

    private void bigPlant(int x, int y, int z) {
        set(x, y, z, "minecraft:moss_block");
        set(x, y + 1, z, h(x, y, z) < 0.5 ? "minecraft:flowering_azalea" : "minecraft:azalea");
        for (Dir d : Dir.values()) setIfAir(x + d.dx, y, z + d.dz, trap("minecraft:spruce_trapdoor", d, false, true));
    }

    private void sconce(int x, int y, int z, Dir faces) {
        // applique : lanterne posee sur une console (escalier retourne) contre le mur
        set(x, y, z, stairs("minecraft:dark_oak_stairs", faces.opposite(), true));
        setIfAir(x, y + 1, z, standing("minecraft:lantern"));
    }

    private void lamp(int x, int y, int z) {
        // lampe de lecture (lanterne sur la table)
        setIfAir(x, y, z, standing("minecraft:lantern"));
    }

    // ================================================================ bibliotheque

    private void library() {
        int y0 = F + 1;
        // pilastres devant les rayonnages du mur nord (cassent le pan de 21)
        for (int x : new int[]{-3, 3, 7})
            for (int y = y0; y <= T - 1; y++) if (y != U) set(x, y, -21, y == U - 1 || y == T - 1 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_basalt[axis=y]");
        // colonnes sous les galeries (soutien des bords de la nef)
        for (int[] c : new int[][]{{-7, -15}, {-7, -10}, {7, -15}, {7, -10}, {-3, -18}, {3, -18}, {-3, -6}, {3, -6}}) {
            for (int y = y0; y < U; y++) set(c[0], y, c[1], y == U - 1 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_basalt[axis=y]");
        }
        // poutres de rive des galeries + garde-corps a balustres
        for (int x = -7; x <= 7; x++) {
            set(x, U, -18, "minecraft:stripped_dark_oak_log[axis=x]");
            set(x, U, -6, "minecraft:stripped_dark_oak_log[axis=x]");
            set(x, U - 1, -17, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
            set(x, U - 1, -7, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, true));
        }
        for (int z = -18; z <= -6; z++) {
            set(-7, U, z, "minecraft:stripped_dark_oak_log[axis=z]");
            set(7, U, z, "minecraft:stripped_dark_oak_log[axis=z]");
            if (z > -18 && z < -6) {
                set(-6, U - 1, z, stairs("minecraft:dark_oak_stairs", Dir.WEST, true));
                set(6, U - 1, z, stairs("minecraft:dark_oak_stairs", Dir.EAST, true));
            }
        }
        for (int k = -4; k <= 4; k += 4) {
            set(k, U, -17, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
            set(k, U, -7, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, true));
        }
        for (int z = -15; z <= -9; z += 3) {
            set(-6, U, z, stairs("minecraft:dark_oak_stairs", Dir.WEST, true));
            set(6, U, z, stairs("minecraft:dark_oak_stairs", Dir.EAST, true));
        }
        for (int x = -7; x <= 7; x++) {
            railing(x, U + 1, -18, x == -7 || x == 7 || x % 4 == 0);
            railing(x, U + 1, -6, x == -7 || x == 7 || x % 4 == 0);
        }
        for (int z = -17; z <= -7; z++) {
            railing(-7, U + 1, z, z % 4 == 0);
            railing(7, U + 1, z, z % 4 == 0);
        }
        // sol de la nef : parquet + bande de marbre
        for (int x = -6; x <= 6; x++)
            for (int z = -17; z <= -7; z++)
                if (Math.abs(x) == 6 || z == -17 || z == -7) set(x, F, z, "minecraft:polished_deepslate");
        // rayonnages perpendiculaires sous les galeries (ouest, rythme different a l'est)
        for (int z : new int[]{-11, -8}) shelfRow(-11, z, -9, z, y0, 5, false);
        for (int z : new int[]{-14, -11}) shelfRow(9, z, 11, z, y0, 5, false);
        shelfRow(9, -17, 11, -17, y0, 5, false);
        // niches de lecture entre les rayonnages
        armchair(-10, y0, -10, Dir.EAST);
        lamp(-10, y0 + 1, -9);
        set(-10, y0, -9, "minecraft:dark_oak_slab[type=top]");
        armchair(10, y0, -13, Dir.WEST);
        set(10, y0, -12, "minecraft:dark_oak_slab[type=top]");
        lamp(10, y0 + 1, -12);
        set(-10, y0, -6, "minecraft:lectern[facing=east,has_book=true,powered=false]");
        set(10, y0, -16, "minecraft:lectern[facing=west,has_book=true,powered=false]");
        // allee nord sous la galerie : deux alcoves de part et d'autre de la porte
        table(-6, -21, -4, -20, y0, "minecraft:spruce_slab", false);
        set(-5, y0 + 1, -20, standing("minecraft:lantern"));
        set(-6, y0 + 1, -21, "minecraft:candle[candles=1,lit=true]");
        set(-3, y0, -20, stairs("minecraft:dark_oak_stairs", Dir.EAST, false));
        set(-6, y0, -19, stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));
        table(4, -21, 6, -20, y0, "minecraft:spruce_slab", false);
        set(5, y0 + 1, -21, standing("minecraft:lantern"));
        set(3, y0, -20, stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
        set(6, y0, -19, stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));
        clock(-2, y0, -21, Dir.SOUTH);
        globe(2, y0, -21);
        // nef : deux longues tables de lecture, lampes vertes (lanternes), pupitre central et globe
        table(-4, -16, -3, -13, y0, "minecraft:dark_oak_slab", true);
        table(3, -15, 4, -12, y0, "minecraft:dark_oak_slab", true);
        table(-4, -10, -3, -8, y0, "minecraft:dark_oak_slab", true);
        table(3, -9, 4, -8, y0, "minecraft:dark_oak_slab", true);
        lamp(-4, y0 + 1, -15);
        lamp(-3, y0 + 1, -13);
        lamp(4, y0 + 1, -14);
        lamp(3, y0 + 1, -12);
        lamp(-3, y0 + 1, -9);
        lamp(4, y0 + 1, -8);
        set(-4, y0 + 1, -14, "minecraft:candle[candles=1,lit=true]");
        set(3, y0 + 1, -9, "minecraft:candle[candles=2,lit=true]");
        // pupitre central sur une estrade
        for (int x = -1; x <= 1; x++)
            for (int z = -13; z <= -11; z++) set(x, y0, z, "minecraft:dark_oak_slab[type=bottom]");
        set(0, y0, -12, "minecraft:polished_blackstone_bricks");
        set(0, y0 + 1, -12, "minecraft:lectern[facing=south,has_book=true,powered=false]");
        globe(-1, y0 + 1, -13);
        set(1, y0 + 1, -11, "minecraft:flower_pot");
        // petits rayonnages bas (2 de haut) qui coupent l'axe de la nef
        shelfRow(-1, -16, 1, -16, y0, 2, true);
        shelfRow(-1, -8, 1, -8, y0, 2, true);
        // tapis sous l'estrade
        rug(-2, -14, 2, -10, y0, "minecraft:red_carpet", "minecraft:black_carpet");
        // allee sud (vers le hall) : bancs et plantes
        bigPlant(-9, y0, -4);
        bigPlant(5, y0, -4);
        set(-5, y0, -3, stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));
        set(-6, y0, -3, stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));

        // galeries de l'etage : echelles de bibliotheque, pupitres, fauteuils, appliques
        int y1 = U + 1;
        for (int[] l : new int[][]{{-10, -16}, {-10, -7}, {10, -15}, {10, -8}}) {
            Dir face = l[0] < 0 ? Dir.EAST : Dir.WEST;
            for (int y = y1; y <= T - 2; y++)
                if (B.name(s.get(l[0] - face.dx, y, l[1])).contains("bookshelf") && isAir(l[0], y, l[1]))
                    set(l[0], y, l[1], "minecraft:ladder[facing=" + face.id() + "]");
        }
        set(-9, y1, -4, "minecraft:lectern[facing=north,has_book=true,powered=false]");
        armchair(9, y1, -17, Dir.SOUTH);
        armchair(-9, y1, -17, Dir.SOUTH);
        set(-4, y1, -21, "minecraft:dark_oak_slab[type=top]");
        set(-4, y1 + 1, -21, standing("minecraft:lantern"));
        set(4, y1, -21, "minecraft:dark_oak_slab[type=top]");
        set(4, y1 + 1, -21, "minecraft:candle[candles=3,lit=true]");
        globe(0, y1, -21);
        // chicanes sur les galeries laterales (cassent les lignes droites de 16)
        shelfRow(-10, -12, -10, -11, y1, 2, true);
        clock(-8, y1, -8, Dir.WEST);
        shelfRow(10, -10, 10, -9, y1, 2, true);
        clock(8, y1, -14, Dir.EAST);
        for (int z : new int[]{-13, -9}) {
            sconce(-11, y1 + 2, z, Dir.EAST);
            sconce(11, y1 + 2, z, Dir.WEST);
        }
        // rayonnages contre la cloison ouest et est a l'etage (galeries), hors passages
        for (int z = -17; z <= -3; z++) {
            for (int y = y1; y <= T - 1; y++) {
                if (z < -15 || z > -14) if (isAir(-11, y, z)) set(-11, y, z, book(-11, y, z, Dir.EAST));
                if (z < -12 || z > -11) if (isAir(11, y, z)) set(11, y, z, book(11, y, z, Dir.WEST));
            }
        }
        // rez-de-chaussee : etageres contre les cloisons des allees laterales
        for (int z = -17; z <= -3; z++)
            for (int y = y0; y < U; y++) {
                if ((z < -15 || z > -14) && isAir(-11, y, z) && z != -7 && z != -6) set(-11, y, z, book(-11, y, z, Dir.EAST));
                if ((z < -9 || z > -8) && isAir(11, y, z) && z > -17 && z != -7 && z != -6 && z != -5 && z != -4) set(11, y, z, book(11, y, z, Dir.WEST));
            }
        // lustres sous les galeries
        chandelier(-9, U - 1, -13, 1, false);
        chandelier(9, U - 1, -9, 1, false);
        chandelier(0, U - 1, -20, 1, false);
        chandelier(0, U - 1, -4, 1, false);
        chandelier(-9, T - 1, -10, 1, false);
        chandelier(9, T - 1, -12, 1, false);
        chandelier(0, T - 1, -20, 1, false);
        chandelier(0, T - 1, -4, 1, false);
    }

    private void railing(int x, int y, int z, boolean post) {
        if (!isAir(x, y, z)) return;
        set(x, y, z, post ? "minecraft:stripped_dark_oak_log" : "minecraft:dark_oak_fence");
        if (post) setIfAir(x, y + 1, z, "minecraft:candle[candles=1,lit=true]");
    }

    // ================================================================ hall d'entree

    private void foyer() {
        int y0 = F + 1;
        // damier de marbre (regle, pas de hasard) dans le hall et le pavillon
        for (int x = -8; x <= 8; x++)
            for (int z = -1; z <= 16; z++) {
                if (z >= 9 && Math.abs(x) > 3) continue;
                if (!B.name(s.get(x, F, z)).contains("planks")) continue;
                set(x, F, z, Math.floorMod(x + z, 2) == 0 ? "minecraft:polished_diorite" : "minecraft:polished_blackstone");
            }
        // tapis rouge de la porte a la bibliotheque
        for (int z = -1; z <= 15; z++) {
            setIfAir(0, y0, z, "minecraft:red_carpet");
            if (z <= 7) {
                setIfAir(-1, y0, z, "minecraft:red_carpet");
                setIfAir(1, y0, z, "minecraft:red_carpet");
            }
        }
        // gueridon central (coupe l'axe porte -> bibliotheque) : plateau rond, grande composition florale
        for (int x = -1; x <= 1; x++)
            for (int z = 3; z <= 5; z++) set(x, y0, z, (x == 0 && z == 4) ? "minecraft:dark_oak_log[axis=y]" : "minecraft:dark_oak_slab[type=top]");
        set(0, y0 + 1, 4, "minecraft:moss_block");
        set(0, y0 + 2, 4, "minecraft:flowering_azalea");
        set(-1, y0 + 1, 3, "minecraft:candle[candles=3,lit=true]");
        set(1, y0 + 1, 5, "minecraft:candle[candles=2,lit=true]");
        set(1, y0 + 1, 3, "minecraft:potted_white_tulip");
        set(-1, y0 + 1, 5, "minecraft:potted_red_tulip");
        // lustre du hall (double hauteur)
        chandelier(0, T - 1, 4, 4, true);
        chandelier(0, T + 1, 12, 3, false);
        // pavillon : banquettes, porte-manteaux, plantes
        bench(-3, y0, 12, Dir.EAST);
        bench(3, y0, 12, Dir.WEST);
        plant(-3, y0, 15);
        plant(3, y0, 15);
        plant(-3, y0, 9);
        plant(3, y0, 9);
        // hall : horloge, consoles, bustes, grands vases
        clock(-8, y0, 7, Dir.NORTH);
        set(8, y0, 7, "minecraft:dark_oak_planks");
        set(8, y0 + 1, 7, "minecraft:flower_pot");
        set(-6, y0, -1, "minecraft:spruce_slab[type=top]");
        set(-6, y0 + 1, -1, "minecraft:potted_flowering_azalea_bush");
        set(6, y0, -1, "minecraft:spruce_slab[type=top]");
        set(6, y0 + 1, -1, standing("minecraft:lantern"));
        bigPlant(-7, y0, 2);
        bigPlant(7, y0, 2);
        // palier : fauteuils et bustes
        armchair(-5, U + 1, -1, Dir.SOUTH);
        armchair(5, U + 1, -1, Dir.SOUTH);
        set(-9, U + 1, -1, "minecraft:dark_oak_slab[type=top]");
        set(-9, U + 2, -1, standing("minecraft:lantern"));
        set(9, U + 1, -1, "minecraft:dark_oak_slab[type=top]");
        set(9, U + 2, -1, "minecraft:candle[candles=3,lit=true]");
        sconce(-11, U + 3, 4, Dir.EAST);
        sconce(11, U + 3, 4, Dir.WEST);
        sconce(-11, F + 3, 0, Dir.EAST);
        sconce(11, F + 3, 0, Dir.WEST);
    }

    // ================================================================ aile ouest

    private void dining() {
        int y0 = F + 1;
        // sol : parquet clair a bordure sombre
        for (int x = -29; x <= -13; x++)
            for (int z = -1; z <= 9; z++) {
                if (!B.name(s.get(x, F, z)).contains("planks")) continue;
                boolean border = x == -29 || x == -13 || z == -1 || z == 9;
                set(x, F, z, border ? "minecraft:dark_oak_planks" : OAK_FLOOR.at(x, F, z));
            }
        // longue table (deux tronçons, passage au milieu) et chaises
        table(-26, 4, -22, 5, y0, "minecraft:dark_oak_slab", true);
        table(-19, 4, -16, 5, y0, "minecraft:dark_oak_slab", true);
        setIfAir(-27, y0, 4, stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
        setIfAir(-15, y0, 5, stairs("minecraft:dark_oak_stairs", Dir.EAST, false));
        for (int x : new int[]{-25, -23, -18, -16}) set(x, y0 + 1, 4, "minecraft:candle[candles=3,lit=true]");
        set(-24, y0 + 1, 5, "minecraft:flower_pot");
        set(-17, y0 + 1, 5, "minecraft:flower_pot");
        rug(-27, 2, -14, 7, y0, "minecraft:brown_carpet", "minecraft:red_carpet");
        chandelier(-24, U - 1, 4, 1, true);
        chandelier(-17, U - 1, 4, 1, true);
        // cheminee contre le mur ouest
        fireplace(-29, 6, Dir.EAST, y0, U - 1);
        // buffets contre la cloison nord, entre les passages
        for (int x = -24; x <= -18; x++) {
            set(x, y0, -1, x % 2 == 0 ? "minecraft:barrel[facing=south,open=false]" : "minecraft:dark_oak_planks");
            set(x, y0 + 1, -1, "minecraft:dark_oak_slab[type=bottom]");
        }
        set(-23, y0 + 2, -1, "minecraft:candle[candles=2,lit=true]");
        set(-19, y0 + 2, -1, "minecraft:flower_pot");
        clock(-13, y0, 9, Dir.WEST);
        plant(-28, y0, 0);
        plant(-14, y0, -1);
        sconce(-29, y0 + 3, 2, Dir.EAST);
    }

    private void kitchen() {
        int y0 = F + 1;
        // carrelage en damier terre cuite / blanc
        for (int x = -29; x <= -22; x++)
            for (int z = -15; z <= -3; z++) {
                set(x, F, z, Math.floorMod(x + z, 2) == 0 ? "minecraft:white_terracotta" : "minecraft:bricks");
            }
        // grand potager (cuisiniere) contre le mur ouest : hotte en briques
        for (int z = -14; z <= -10; z++) {
            for (int y = y0; y < U; y++) set(-29, y, z, (y >= y0 + 3) ? "minecraft:bricks" : "minecraft:polished_blackstone_bricks");
        }
        set(-28, y0, -13, "minecraft:smoker[facing=east,lit=true]");
        set(-28, y0, -12, "minecraft:campfire[lit=true,signal_fire=false,facing=east]");
        set(-28, y0, -11, "minecraft:furnace[facing=east,lit=true]");
        for (int z = -14; z <= -10; z++) set(-28, y0 + 3, z, stairs("minecraft:brick_stairs", Dir.WEST, true));
        set(-28, y0 + 2, -12, hanging("minecraft:lantern"));
        // plans de travail le long du mur nord
        for (int x = -27; x <= -23; x++) {
            set(x, y0, -15, x == -25 ? "minecraft:water_cauldron[level=2]" : (x % 2 == 0 ? "minecraft:barrel[facing=up,open=false]" : "minecraft:spruce_planks"));
            setIfAir(x, y0 + 2, -15, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]");
        }
        set(-27, y0 + 1, -15, "minecraft:flower_pot");
        set(-23, y0 + 1, -15, "minecraft:brewing_stand");
        // ilot central
        for (int x = -26; x <= -24; x++)
            for (int z = -10; z <= -8; z++) {
                if (z == -9) set(x, y0, z, x == -25 ? "minecraft:crafting_table" : "minecraft:stripped_spruce_wood");
                else set(x, y0, z, "minecraft:spruce_slab[type=top]");
            }
        set(-26, y0 + 1, -9, "minecraft:candle[candles=1,lit=true]");
        set(-24, y0 + 1, -9, "minecraft:flower_pot");
        set(-25, U - 1, -9, "minecraft:iron_chain");
        set(-25, U - 2, -9, hanging("minecraft:lantern"));
        // garde-manger : tonneaux et sacs (bottes de foin) le long de la cloison
        set(-22, y0, -15, "minecraft:hay_block[axis=y]");
        set(-22, y0, -14, "minecraft:barrel[facing=up,open=false]");
        set(-22, y0 + 1, -15, "minecraft:barrel[facing=up,open=false]");
        set(-22, y0, -4, "minecraft:composter[level=4]");
        set(-29, y0, -4, "minecraft:barrel[facing=east,open=false]");
        set(-29, y0, -5, "minecraft:barrel[facing=east,open=false]");
        set(-29, y0 + 1, -4, "minecraft:pumpkin");
        sconce(-29, y0 + 3, -7, Dir.EAST);
        chandelier(-25, U - 1, -5, 1, false);
    }

    private void pantry() {
        int y0 = F + 1;
        for (int x = -20; x <= -13; x++)
            for (int z = -15; z <= -3; z++)
                if (B.name(s.get(x, F, z)).contains("planks")) set(x, F, z, PAVING.at(x, F, z));
        // etageres de rangement (office), vaisselle
        for (int z = -15; z <= -11; z++) {
            set(-20, y0, z, "minecraft:barrel[facing=east,open=false]");
            set(-20, y0 + 1, z, "minecraft:spruce_slab[type=top]");
            set(-20, y0 + 2, z, z % 2 == 0 ? "minecraft:flower_pot" : "minecraft:flower_pot");
        }
        for (int x = -19; x <= -16; x++) {
            set(x, y0, -15, x % 2 == 0 ? "minecraft:chest[facing=south,type=single,waterlogged=false]" : "minecraft:barrel[facing=south,open=false]");
            set(x, y0 + 2, -15, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]");
        }
        set(-17, y0, -6, "minecraft:spruce_slab[type=top]");
        set(-17, y0 + 1, -6, standing("minecraft:lantern"));
        set(-18, y0, -6, stairs("minecraft:spruce_stairs", Dir.WEST, false));
        set(-20, y0, -4, "minecraft:water_cauldron[level=3]");
        chandelier(-17, U - 1, -10, 1, false);
    }

    private void study() {
        int y0 = U + 1;
        // tapis persan, bureau massif, fauteuils, cheminee (au-dessus de celle de la salle a manger)
        rug(-27, 1, -18, 8, y0, "minecraft:red_carpet", "minecraft:orange_carpet");
        for (int x = -24; x <= -21; x++) set(x, y0, 5, "minecraft:dark_oak_slab[type=top]");
        set(-24, y0, 6, "minecraft:dark_oak_planks");
        set(-21, y0, 6, "minecraft:dark_oak_planks");
        set(-23, y0 + 1, 5, standing("minecraft:lantern"));
        set(-22, y0 + 1, 5, "minecraft:candle[candles=2,lit=true]");
        set(-21, y0 + 1, 5, "minecraft:flower_pot");
        set(-22, y0, 7, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
        armchair(-23, y0, 2, Dir.SOUTH);
        armchair(-19, y0, 2, Dir.SOUTH);
        fireplace(-29, 6, Dir.EAST, y0, T - 1);
        globe(-27, y0, 2);
        set(-14, y0, 8, "minecraft:cartography_table");
        set(-14, y0, 7, "minecraft:lectern[facing=west,has_book=true,powered=false]");
        // bibliotheque murale contre la cloison nord du bureau
        for (int x = -25; x <= -19; x++)
            for (int y = y0; y <= y0 + 3; y++) if (isAir(x, y, -1)) set(x, y, -1, book(x, y, -1, Dir.SOUTH));
        clock(-15, y0, 9, Dir.WEST);
        shelfRow(-17, 3, -15, 3, y0, 3, true);
        chandelier(-22, T - 1, 4, 1, true);
        plant(-28, y0, 9);
        sconce(-13, y0 + 2, 4, Dir.WEST);
    }

    private void archives() {
        int y0 = U + 1;
        // rayonnages en epis (3 de haut) avec trouees : boucles dans tous les sens
        int[][] rows = {{-29, -26, -14}, {-24, -20, -14}, {-18, -16, -14},
                {-29, -27, -11}, {-25, -21, -11}, {-19, -17, -11},
                {-28, -25, -8}, {-23, -19, -8}, {-17, -16, -8},
                {-29, -27, -5}, {-25, -22, -5}, {-20, -18, -5}};
        for (int[] r : rows) shelfRow(r[0], r[2], r[1], r[2], y0, 3, true);
        // table de consultation et lampes
        table(-21, -13, -20, -12, y0, "minecraft:spruce_slab", false);
        lamp(-21, y0 + 1, -13);
        set(-20, y0 + 1, -12, "minecraft:candle[candles=1,lit=true]");
        set(-23, y0, -9, "minecraft:lectern[facing=north,has_book=true,powered=false]");
        set(-15, y0, -15, "minecraft:barrel[facing=up,open=false]");
        set(-15, y0 + 1, -15, standing("minecraft:lantern"));
        chandelier(-24, T - 1, -12, 1, false);
        chandelier(-19, T - 1, -6, 1, false);
        chandelier(-27, T - 1, -4, 1, false);
        sconce(-29, y0 + 2, -9, Dir.EAST);
    }

    // ================================================================ aile est

    private void salon() {
        int y0 = F + 1;
        for (int x = 13; x <= 29; x++)
            for (int z = -1; z <= 9; z++) {
                if (!B.name(s.get(x, F, z)).contains("planks")) continue;
                set(x, F, z, Math.floorMod(x - z, 6) < 3 ? "minecraft:spruce_planks" : "minecraft:dark_oak_planks");
            }
        // cheminee contre la cloison nord, entre les passages
        fireplace(21, -1, Dir.SOUTH, y0, U - 1);
        // canapes et fauteuils autour d'un tapis
        rug(17, 2, 25, 7, y0, "minecraft:green_carpet", "minecraft:lime_carpet");
        for (int x = 19; x <= 23; x++) set(x, y0, 7, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
        set(18, y0, 7, trap("minecraft:dark_oak_trapdoor", Dir.WEST, false, true));
        set(24, y0, 7, trap("minecraft:dark_oak_trapdoor", Dir.EAST, false, true));
        armchair(18, y0, 3, Dir.EAST);
        armchair(24, y0, 3, Dir.WEST);
        table(20, 4, 22, 5, y0, "minecraft:spruce_slab", false);
        set(21, y0 + 1, 4, "minecraft:candle[candles=3,lit=true]");
        set(20, y0 + 1, 5, "minecraft:flower_pot");
        piano(27, y0, 8, Dir.NORTH);
        clock(13, y0, 9, Dir.EAST);
        bigPlant(29, y0, 0);
        bigPlant(14, y0, 7);
        plant(29, y0, 6);
        chandelier(21, U - 1, 4, 1, true);
        sconce(29, y0 + 3, 5, Dir.WEST);
    }

    private void reading() {
        int y0 = F + 1;
        for (int x = 13; x <= 29; x++)
            for (int z = -15; z <= -3; z++)
                if (B.name(s.get(x, F, z)).contains("planks")) set(x, F, z, OAK_FLOOR.at(x, F, z));
        // tables de lecture en quinconce, lampes, pupitres
        table(17, -11, 19, -10, y0, "minecraft:spruce_slab", true);
        table(24, -12, 26, -11, y0, "minecraft:spruce_slab", true);
        table(20, -6, 22, -5, y0, "minecraft:spruce_slab", true);
        lamp(18, y0 + 1, -11);
        lamp(25, y0 + 1, -12);
        lamp(21, y0 + 1, -5);
        set(26, y0 + 1, -11, "minecraft:candle[candles=2,lit=true]");
        set(28, y0, -6, "minecraft:lectern[facing=west,has_book=true,powered=false]");
        set(14, y0, -12, "minecraft:lectern[facing=east,has_book=true,powered=false]");
        // rayonnages bas qui separent les tables
        shelfRow(24, -8, 27, -8, y0, 2, true);
        shelfRow(14, -7, 16, -7, y0, 2, true);
        armchair(29, y0, -14, Dir.WEST);
        globe(28, y0, -10);
        chandelier(21, U - 1, -9, 1, true);
        sconce(29, y0 + 3, -12, Dir.WEST);
        plant(29, y0, -4);
        plant(13, y0, -4);
    }

    private void music() {
        int y0 = U + 1;
        rug(16, 1, 27, 8, y0, "minecraft:blue_carpet", "minecraft:light_blue_carpet");
        piano(18, y0, 7, Dir.NORTH);
        piano(25, y0, 2, Dir.SOUTH);
        // pupitres (barrieres + tapis) et chaises en demi-cercle
        for (int[] c : new int[][]{{21, 4}, {23, 5}, {20, 6}}) {
            set(c[0], y0, c[1], "minecraft:dark_oak_fence");
            set(c[0], y0 + 1, c[1], "minecraft:white_carpet");
        }
        for (int[] c : new int[][]{{22, 3}, {24, 4}, {19, 4}}) set(c[0], y0, c[1], stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));
        shelfRow(28, 4, 28, 6, y0, 3, true);
        set(28, y0, 8, "minecraft:jukebox[has_record=false]");
        set(28, y0 + 1, 8, "minecraft:candle[candles=3,lit=true]");
        set(14, y0, 1, "minecraft:note_block");
        set(14, y0 + 1, 1, "minecraft:flower_pot");
        bigPlant(29, y0, 0);
        plant(14, y0, 9);
        clock(29, y0, 9, Dir.WEST);
        chandelier(22, T - 1, 5, 1, true);
        sconce(13, y0 + 2, 5, Dir.EAST);
    }

    private void mapRoom() {
        int y0 = U + 1;
        // grande table des cartes, globes, longue-vue a la fenetre, rayonnages de rouleaux
        for (int x = 20; x <= 24; x++)
            for (int z = -9; z <= -7; z++) set(x, y0, z, (x + z) % 2 == 0 ? "minecraft:cartography_table" : "minecraft:spruce_slab[type=top]");
        set(22, y0 + 1, -8, standing("minecraft:lantern"));
        set(20, y0 + 1, -9, "minecraft:candle[candles=1,lit=true]");
        globe(17, y0, -6);
        globe(27, y0, -12);
        set(29, y0, -10, "minecraft:spruce_fence");
        set(29, y0 + 1, -10, "minecraft:end_rod[facing=up]"); // 1.9.1 : debout sur son support
        shelfRow(25, -5, 28, -5, y0, 3, true);
        shelfRow(14, -9, 14, -6, y0, 3, true);
        armchair(26, y0, -14, Dir.SOUTH);
        set(28, y0, -4, "minecraft:barrel[facing=up,open=false]");
        set(28, y0 + 1, -4, "minecraft:flower_pot");
        chandelier(22, T - 1, -8, 1, true);
        chandelier(18, T - 1, -13, 1, false);
        sconce(29, y0 + 2, -7, Dir.WEST);
    }

    // ================================================================ jardin

    private void garden() {
        boundary();
        drive();
        fountain(0, 36);
        parterres();
        kitchenGarden();
        backGarden();
        eastStrip();
        trees();
        lamps();
        vegetation();
    }

    /** Mur de cloture sur le bord de la boite : socle en pierre, grilles, piliers a lanternes ; haie devant. */
    private void boundary() {
        for (int t = BX1; t <= BX2; t++) {
            int[][] cells = {{t, BZ1}, {t, BZ2}, {BX1, t}, {BX2, t}};
            for (int[] c : cells) {
                int x = c[0], z = c[1];
                int gh = ground(x, z);
                boolean pillar = Math.floorMod(t, 7) == 0 || Math.abs(t) == 50;
                boolean gate = c[1] == BZ2 && Math.abs(x) <= 2;
                fill(x, G - 1, z, x, gh, z, PLINTH);
                if (pillar) {
                    fill(x, gh + 1, z, x, gh + 3, z, PLINTH);
                    set(x, gh + 4, z, "minecraft:stone_brick_slab[type=bottom]");
                    set(x, gh + 5, z, standing("minecraft:lantern"));
                } else {
                    set(x, gh + 1, z, "minecraft:stone_brick_wall");
                    set(x, gh + 2, z, "minecraft:iron_bars");
                    set(x, gh + 3, z, "minecraft:iron_bars");
                }
                if (gate) {
                    set(x, gh + 1, z, "minecraft:iron_bars");
                    set(x, gh + 4, z, Math.abs(x) <= 1 ? "minecraft:iron_bars" : B.AIR);
                    set(x, gh, z, GRAVEL.at(x, gh, z));
                }
            }
        }
        // grand portail : piliers plus hauts en x +-3
        for (int sx : new int[]{-3, 3}) {
            fill(sx, G, BZ2, sx, G + 4, BZ2, PLINTH);
            set(sx, G + 5, BZ2, "minecraft:chiseled_stone_bricks");
            set(sx, G + 6, BZ2, standing("minecraft:lantern"));
        }
        // haie le long du mur (a l'interieur), sauf devant le portail et les allees
        for (int t = BX1 + 1; t <= BX2 - 1; t++) {
            hedgeCell(t, BZ1 + 1, 2);
            if (Math.abs(t) > 6) hedgeCell(t, BZ2 - 1, 2);
            if (Math.abs(t) > 3) hedgeCell(BX1 + 1, t, 2);
            if (t < -16 || t > -6) hedgeCell(BX2 - 1, t, 2);
        }
    }

    private void hedgeCell(int x, int z, int hgt) {
        int gh = ground(x, z);
        for (int y = gh + 1; y <= gh + hgt; y++) if (isAir(x, y, z)) set(x, y, z, HEDGE.at(x, y, z));
    }

    /** Allee de gravier de la terrasse au portail, contre-allees, allee du tour du manoir. */
    private void drive() {
        for (int z = 25; z <= 49; z++)
            for (int x = -2; x <= 2; x++) path(x, z);
        // allee circulaire autour de la fontaine
        for (int x = -9; x <= 9; x++)
            for (int z = 27; z <= 45; z++) {
                double d = Math.sqrt(x * x + (z - 36) * (z - 36));
                if (d >= 6.5 && d <= 8.7) path(x, z);
            }
        // contre-allees est-ouest devant le manoir et le long des facades laterales
        for (int x = -46; x <= 46; x++)
            for (int z = 25; z <= 26; z++) path(x, z);
        for (int z = -46; z <= 26; z++)
            for (int x : new int[]{-42, -41, 47, 48}) path(x, z);
        for (int x = -46; x <= 48; x++)
            for (int z = -29; z <= -28; z++) path(x, z);
        // allees vers les portes laterales
        for (int x = -41; x <= -33; x++) path(x, 0);
        for (int x = -41; x <= -33; x++) path(x, 1);
        for (int z = -27; z <= -19; z++) {
            path(-26, z);
            path(0, z);
            path(-1, z);
            path(1, z);
            path(26, z);
        }
        for (int z = 7; z <= 24; z++) {
            path(38, z);
            path(39, z);
        }
        for (int x = 46; x <= 48; x++) {
            path(x, -4);
            path(x, -3);
        }
    }

    private void path(int x, int z) {
        if (Math.abs(x) > BX2 || Math.abs(z) > BZ2) return;
        int gh = ground(x, z);
        if (gh != G) {
            // l'allee arase la butte
            for (int y = G + 1; y <= gh; y++) set(x, y, z, B.AIR);
        }
        String cur = s.get(x, G, z);
        if (cur.contains("water") || cur.contains("stone_brick")) return;
        set(x, G, z, GRAVEL.at(x, G, z));
    }

    /** Fontaine a vasques (eau seulement dans le bassin bas : rien ne coule). */
    private void fountain(int cx, int cz) {
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 6; z <= cz + 6; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d <= 4.3) {
                    set(x, G - 2, z, "minecraft:stone_bricks");
                    set(x, G - 1, z, B.WATER);
                    set(x, G, z, B.WATER);
                } else if (d <= 5.4) {
                    set(x, G - 1, z, "minecraft:stone_bricks");
                    set(x, G, z, "minecraft:polished_andesite");
                    set(x, G + 1, z, "minecraft:smooth_stone_slab[type=bottom]");
                } else if (d <= 6.4) {
                    set(x, G, z, PAVING.at(x, G, z));
                }
            }
        for (int y = G - 1; y <= G + 2; y++) set(cx, y, cz, "minecraft:chiseled_stone_bricks");
        for (Dir d : Dir.values()) {
            set(cx + d.dx, G + 2, cz + d.dz, "minecraft:water_cauldron[level=3]");
        }
        set(cx, G + 3, cz, "minecraft:stone_brick_wall");
        set(cx, G + 4, cz, "minecraft:water_cauldron[level=3]");
        set(cx, G + 5, cz, "minecraft:stone_brick_wall");
        set(cx, G + 6, cz, "minecraft:sea_lantern");
        set(cx - 2, G + 1, cz + 1, "minecraft:lily_pad");
        set(cx + 2, G + 1, cz - 2, "minecraft:lily_pad");
    }

    /** Quatre parterres a la francaise autour de la fontaine : buis bas, ifs tailles en cone, massifs de fleurs. */
    private void parterres() {
        String[] flowers = {"minecraft:red_tulip", "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:poppy",
                "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:allium", "minecraft:azure_bluet"};
        int[][] beds = {{-28, 29, -11, 43}, {11, 29, 28, 43}, {-28, 44, -11, 48}, {11, 44, 28, 48}};
        int bi = 0;
        for (int[] b : beds) {
            for (int x = b[0]; x <= b[2]; x++)
                for (int z = b[1]; z <= b[3]; z++) {
                    if (s.get(x, G, z).contains("gravel")) continue;
                    boolean edge = x == b[0] || x == b[2] || z == b[1] || z == b[3];
                    boolean gap = (z - b[1]) % 7 == 3 || (x - b[0]) % 9 == 4;
                    int gh = ground(x, z);
                    if (edge && !gap) {
                        setIfAir(x, gh + 1, z, HEDGE.at(x, gh + 1, z));
                    } else if (!edge && ((x - b[0]) % 4 == 2) && ((z - b[1]) % 3 != 0)) {
                        set(x, gh, z, "minecraft:rooted_dirt");
                        String f = flowers[(bi * 3 + (x - b[0]) / 4) % flowers.length];
                        setIfAir(x, gh + 1, z, f);
                    }
                }
            bi++;
        }
        // ifs tailles (3 de haut) : couverts reguliers dans le jardin de devant
        for (int[] c : new int[][]{{-18, 31}, {18, 31}, {-18, 41}, {18, 41}, {-10, 28}, {10, 28}, {-24, 36}, {24, 36},
                {-34, 30}, {34, 30}, {-34, 42}, {34, 42}, {-6, 44}, {6, 44}}) topiary(c[0], c[1]);
        // bancs face a la fontaine, statues sur socle
        bench(-10, G + 1, 36, Dir.EAST);
        bench(10, G + 1, 36, Dir.WEST);
        statue(-14, 26);
        statue(14, 26);
    }

    private void topiary(int x, int z) {
        int gh = ground(x, z);
        for (int y = gh + 1; y <= gh + 3; y++) set(x, y, z, "minecraft:spruce_leaves[persistent=true]");
        for (Dir d : Dir.values()) {
            setIfAir(x + d.dx, gh + 1, z + d.dz, "minecraft:spruce_leaves[persistent=true]");
            if (h(x, d.ordinal(), z) < 0.5) setIfAir(x + d.dx, gh + 2, z + d.dz, "minecraft:spruce_leaves[persistent=true]");
        }
        set(x, gh + 4, z, "minecraft:spruce_leaves[persistent=true]");
    }

    private void statue(int x, int z) {
        int gh = ground(x, z);
        set(x, gh + 1, z, "minecraft:chiseled_stone_bricks");
        set(x, gh + 2, z, "minecraft:stone_brick_wall");
        set(x, gh + 3, z, "minecraft:polished_andesite");
        set(x, gh + 4, z, "minecraft:skeleton_skull[rotation=8]");
    }

    /** Potager a l'ouest : planches surelevees (citrouilles, melons, foin), epouvantail, cabane a outils. */
    private void kitchenGarden() {
        for (int bx = -48; bx <= -45; bx += 3)
            for (int bz = -45; bz <= -32; bz += 5) {
                for (int x = bx; x <= bx + 1; x++)
                    for (int z = bz; z <= bz + 3; z++) {
                        int gh = ground(x, z);
                        set(x, gh + 1, z, (x + z) % 3 == 0 ? "minecraft:pumpkin" : (x + z) % 3 == 1 ? "minecraft:melon" : "minecraft:moss_block");
                        setIfAir(x, gh + 2, z, (x + z) % 3 == 2 ? "minecraft:fern" : B.AIR);
                    }
            }
        // epouvantail
        int gh = ground(-38, -36);
        set(-38, gh + 1, -36, "minecraft:spruce_fence");
        set(-38, gh + 2, -36, "minecraft:hay_block[axis=y]");
        set(-38, gh + 3, -36, "minecraft:carved_pumpkin[facing=east]");
        set(-39, gh + 2, -36, "minecraft:spruce_fence");
        set(-37, gh + 2, -36, "minecraft:spruce_fence");
        // cabane a outils (petit volume, toit en pente, ouverte sur l'allee)
        int x1 = -40, x2 = -35, z1 = -24, z2 = -20, yb = G;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, yb, z, "minecraft:spruce_planks");
                for (int y = yb + 1; y <= yb + 3; y++) {
                    if (!wall) { set(x, y, z, B.AIR); continue; }
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    set(x, y, z, corner ? "minecraft:stripped_spruce_log" : "minecraft:spruce_planks");
                }
            }
        air(-38, yb + 1, z2, -37, yb + 2, z2); // porte sud
        air(x1, yb + 1, -23, x1, yb + 2, -22); // porte ouest
        set(x2, yb + 2, -22, B.pane("minecraft:glass_pane", true, false, true, false));
        RoofStyle rs = new RoofStyle().stairs("minecraft:spruce_stairs").ridge("minecraft:spruce_slab").body(Mat.of("minecraft:spruce_planks"))
                .gable(Mat.of("minecraft:spruce_planks")).gablePost("minecraft:stripped_spruce_log").overhang(1).spacing(3);
        Roof.build(s, rs, Roof.gableX(x1, z1, x2, z2, yb + 4));
        for (int x = x1 + 1; x < x2; x++) set(x, yb + 4, z1 + 1, x == x1 + 1 ? B.AIR : s.get(x, yb + 4, z1 + 1));
        set(x1 + 1, yb + 1, z1 + 1, "minecraft:barrel[facing=up,open=false]");
        set(x2 - 1, yb + 1, z1 + 1, "minecraft:composter[level=7]");
        set(x2 - 1, yb + 3, z1 + 2, hanging("minecraft:lantern"));
        for (int y = yb + 4; y <= yb + 8 && isAir(x2 - 1, y, z1 + 2); y++) set(x2 - 1, y, z1 + 2, "minecraft:iron_chain"); // 1.9.1
        set(x1 + 1, yb + 1, z1 + 2, "minecraft:grindstone[face=floor,facing=east]");
    }

    /** Jardin de derriere : kiosque octogonal, allees de haies avec trouees, bassin, gloriette de roses. */
    private void backGarden() {
        // kiosque (octogone de 9) en (0, -40)
        int cx = 0, cz = -40, yb = G + 1;
        for (int x = cx - 5; x <= cx + 5; x++)
            for (int z = cz - 5; z <= cz + 5; z++) {
                int ax = Math.abs(x - cx), az = Math.abs(z - cz);
                if (ax + az > 7 || ax > 5 || az > 5) continue;
                set(x, G, z, "minecraft:stone_bricks");
                set(x, yb, z, (ax + az == 7 || ax == 5 || az == 5) ? "minecraft:stone_brick_slab[type=bottom]" : "minecraft:smooth_stone_slab[type=bottom]");
            }
        set(cx, G, cz, "minecraft:chiseled_stone_bricks");
        int[][] posts = {{-4, -2}, {-4, 2}, {4, -2}, {4, 2}, {-2, -4}, {2, -4}, {-2, 4}, {2, 4}};
        for (int[] p : posts) {
            for (int y = yb + 1; y <= yb + 4; y++) set(cx + p[0], y, cz + p[1], y == yb + 1 ? "minecraft:quartz_pillar" : "minecraft:birch_fence");
        }
        // toit du kiosque : cone d'escaliers en cuivre vert
        for (int i = 0; i <= 5; i++) {
            int r = 5 - i, y = yb + 5 + i;
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    int ax = Math.abs(x - cx), az = Math.abs(z - cz);
                    if (ax + az > r + 2) continue;
                    boolean rim = ax == r || az == r || ax + az == r + 2;
                    if (!rim && i < 5) continue;
                    if (i == 5) {
                        set(x, y, z, "minecraft:waxed_oxidized_copper");
                        continue;
                    }
                    Dir face = ax >= az ? (x > cx ? Dir.WEST : Dir.EAST) : (z > cz ? Dir.NORTH : Dir.SOUTH);
                    set(x, y, z, stairs("minecraft:waxed_oxidized_cut_copper_stairs", face, false));
                }
        }
        set(cx, yb + 11, cz, "minecraft:lightning_rod");
        set(cx, yb + 4, cz, hanging("minecraft:lantern"));
        for (int y = yb + 5; y <= yb + 9; y++) setIfAir(cx, y, cz, "minecraft:iron_chain");
        bench(cx - 2, yb + 1, cz, Dir.EAST);
        bench(cx + 2, yb + 1, cz, Dir.WEST);
        // allees de haies (2 de haut) avec trouees tous les 6-8 : jamais de couloir ferme
        for (int x = -46; x <= 46; x++) {
            if (Math.abs(x) > 8 && Math.floorMod(x, 9) != 4) hedgeCell(x, -32, 2);
            if (Math.abs(x) > 7 && Math.floorMod(x + 3, 8) != 0 && (x < -31 || x > -13)) hedgeCell(x, -46, 2);
        }
        for (int z = -45; z <= -33; z++) {
            if (Math.floorMod(z, 6) != 2) {
                hedgeCell(-10, z, 2);
                hedgeCell(10, z, 2);
            }
            if (Math.floorMod(z, 5) != 1) hedgeCell(32, z, 2);
        }
        // bassin : roseaux, nenuphars, banc et ponton
        for (int x = POND_X - 8; x <= POND_X + 8; x++)
            for (int z = POND_Z - 7; z <= POND_Z + 7; z++) {
                if (!inPond(x, z)) continue;
                if (h(x, 1, z) < 0.12 && isAir(x, 64, z)) set(x, 64, z, "minecraft:lily_pad");
                boolean shore = !inPond(x + 1, z) || !inPond(x - 1, z) || !inPond(x, z + 1) || !inPond(x, z - 1);
                if (shore && h(x, 2, z) < 0.35) {
                    // roseaux sur la berge voisine
                    for (Dir d : Dir.values()) {
                        int ax = x + d.dx, az = z + d.dz;
                        if (!inPond(ax, az) && isAir(ax, 64, az) && s.get(ax, 63, az).contains("grass") || s.get(ax, 63, az).contains("mud")) {
                            if (isAir(ax, 64, az)) {
                                set(ax, 64, az, "minecraft:sugar_cane[age=0]");
                                if (h(ax, 3, az) < 0.5) set(ax, 65, az, "minecraft:sugar_cane[age=0]");
                            }
                            break;
                        }
                    }
                }
            }
        for (int x = POND_X - 1; x <= POND_X + 1; x++)
            for (int z = POND_Z + 2; z <= POND_Z + 6; z++) set(x, 64, z, x == POND_X ? "minecraft:spruce_planks" : "minecraft:spruce_slab[type=bottom]");
        for (int z = POND_Z + 2; z <= POND_Z + 6; z += 2) {
            set(POND_X - 2, 64, z, "minecraft:spruce_fence");
            set(POND_X + 2, 64, z, "minecraft:spruce_fence");
        }
        bench(POND_X, 64, POND_Z + 8, Dir.NORTH);
        // roseraie : arceaux de barrieres couverts de feuilles
        for (int x = 18; x <= 28; x += 5) {
            int gh = G;
            for (int y = gh + 1; y <= gh + 3; y++) {
                set(x, y, -37, "minecraft:dark_oak_fence");
                set(x, y, -41, "minecraft:dark_oak_fence");
            }
            for (int z = -41; z <= -37; z++) set(x, gh + 4, z, z == -39 ? "minecraft:flowering_azalea_leaves[persistent=true]" : "minecraft:azalea_leaves[persistent=true]");
            setIfAir(x - 1, gh + 1, -37, "minecraft:rose_bush[half=lower]");
            setIfAir(x - 1, gh + 2, -37, "minecraft:rose_bush[half=upper]");
            setIfAir(x + 1, gh + 1, -41, "minecraft:peony[half=lower]");
            setIfAir(x + 1, gh + 2, -41, "minecraft:peony[half=upper]");
        }
        statue(-16, -34);
        statue(16, -44);
    }

    /** Bande est (le long de la serre) : buis, jarres, petit cadran solaire. */
    private void eastStrip() {
        for (int z = -26; z <= 24; z += 6) {
            if (z >= -6 && z <= 0) continue;
            topiary(46, z);
        }
        int gh = ground(47, 12);
        set(47, gh + 1, 12, "minecraft:polished_andesite");
        set(47, gh + 2, 12, "minecraft:daylight_detector[inverted=false,power=0]");
        set(46, ground(46, -8) + 1, -8, "minecraft:flower_pot");
        set(46, ground(46, 2) + 1, 2, "minecraft:flower_pot");
    }

    private void trees() {
        int[][] list = {
                {-40, 40, 9}, {40, 42, 8}, {-42, -40, 9}, {38, -40, 10}, {-26, 45, 6}, {24, 46, 6}, {-44, 0, 6},
                {44, -20, 6}, {-44, 20, 7}, {-30, -38, 7}, {28, -46, 6}};
        long seed = 100;
        for (int[] t : list) {
            int gh = ground(t[0], t[1]);
            String[] kind = (seed % 3 == 0) ? new String[]{"minecraft:dark_oak_log", "minecraft:dark_oak_leaves", "minecraft:azalea_leaves"}
                    : (seed % 3 == 1) ? new String[]{"minecraft:oak_log", "minecraft:oak_leaves", "minecraft:flowering_azalea_leaves"}
                    : new String[]{"minecraft:spruce_log", "minecraft:spruce_leaves", "minecraft:oak_leaves"};
            new Tree(kind[0], kind[1], kind[2]).height(t[2]).crown(t[2] >= 8 ? 3.4 : 2.6).place(s, t[0], gh + 1, t[1], seed);
            seed++;
        }
        // ceinture boisee hors de la boite (decor : limite naturelle derriere le mur)
        long sd = 500;
        for (int x = -AREA + 2; x <= AREA - 2; x += 5)
            for (int z = -AREA + 2; z <= AREA - 2; z += 5) {
                if (Math.abs(x) <= BX2 + 2 && Math.abs(z) <= BZ2 + 2) continue;
                int jx = x + (int) (h(x, 9, z) * 3) - 1, jz = z + (int) (h(x, 10, z) * 3) - 1;
                if (Math.abs(jx) <= BX2 + 2 && Math.abs(jz) <= BZ2 + 2) continue;
                int gh = ground(jx, jz);
                double r = h(jx, 11, jz);
                String[] kind = r < 0.4 ? new String[]{"minecraft:spruce_log", "minecraft:spruce_leaves", null}
                        : r < 0.75 ? new String[]{"minecraft:dark_oak_log", "minecraft:dark_oak_leaves", "minecraft:oak_leaves"}
                        : new String[]{"minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves"};
                new Tree(kind[0], kind[1], kind[2]).height(6 + (int) (h(jx, 12, jz) * 5)).crown(2.8).place(s, jx, gh + 1, jz, sd++);
            }
    }

    private void lamps() {
        // lampadaires le long de l'allee et autour du manoir
        int[][] list = {{-4, 28}, {4, 28}, {-4, 44}, {4, 44}, {-12, 25}, {12, 25}, {-28, 25}, {28, 25}, {-43, 12},
                {-43, -12}, {49, 12}, {49, -12}, {-12, -30}, {12, -30}, {-30, -30}, {30, -30}, {-43, -40}, {43, 10}};
        for (int[] l : list) {
            int gh = ground(l[0], l[1]);
            if (!isAir(l[0], gh + 1, l[1])) continue;
            set(l[0], gh + 1, l[1], "minecraft:polished_blackstone_wall");
            set(l[0], gh + 2, l[1], "minecraft:dark_oak_fence");
            set(l[0], gh + 3, l[1], "minecraft:dark_oak_fence");
            set(l[0], gh + 4, l[1], standing("minecraft:lantern"));
        }
        // lanternes sur la terrasse
        for (int x : new int[]{-25, -15, 15, 25}) set(x, F + 2, 19, standing("minecraft:lantern"));
    }

    private void vegetation() {
        String[] low = {"minecraft:short_grass", "minecraft:fern", "minecraft:short_grass", "minecraft:dandelion", "minecraft:poppy"};
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                int gh = ground(x, z);
                String g = s.get(x, gh, z);
                if (!g.contains("grass_block") && !g.contains("moss_block") && !g.contains("podzol")) continue;
                if (!isAir(x, gh + 1, z)) continue;
                double u = noise.uniform2(x / 4.0 + 13, z / 4.0 - 7);
                boolean outside = Math.abs(x) > BX2 || Math.abs(z) > BZ2;
                double density = outside ? 0.55 : 0.18;
                if (u > 1 - density && h(x, 5, z) < 0.6) {
                    String p = low[(int) (h(x, 6, z) * low.length)];
                    set(x, gh + 1, z, p);
                }
            }
    }

    // ================================================================ finitions

    private void finish() {
        // portes ouvertes (le jeu reste fluide), formes des escaliers, connexions des barrieres et vitres
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++)
                for (int y = F; y <= U + 3; y++) {
                    String b = s.get(x, y, z);
                    if (b.contains("_door[") && b.contains("open=false")) set(x, y, z, b.replace("open=false", "open=true"));
                }
        Details.fix(s, -AREA, G - 1, -AREA, AREA, Y_HI - 1, AREA); // 1.9.1 : trappes, lanternes, paratonnerres
        Stairs.fix(s, -AREA, F, -AREA, AREA, Y_HI - 1, AREA);
        Connect.fix(s, -AREA, G - 1, -AREA, AREA, Y_HI - 1, AREA);
    }
}
