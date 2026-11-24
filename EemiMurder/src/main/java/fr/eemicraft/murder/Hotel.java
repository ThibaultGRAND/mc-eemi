package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Dir;
import fr.eemicraft.murder.build.Facade;
import fr.eemicraft.murder.build.Mat;
import fr.eemicraft.murder.build.Palette;
import fr.eemicraft.murder.build.Roof;
import fr.eemicraft.murder.build.RoofStyle;
import fr.eemicraft.murder.build.Sdf;
import fr.eemicraft.murder.build.Sink;
import fr.eemicraft.murder.build.Style;
import fr.eemicraft.murder.build.Tree;

/**
 * Map « Grand Hôtel » (id hotel, monde mm_hotel, 1.10.0) : PETITE map de 2 a 5 joueurs (size: small), petit palace
 * Art deco des annees 1925-1930 en soiree, dessine par code dans un {@link Sink} (aucun acces Bukkit).
 *
 * <p>Boite x -34..34, z -29..29. Batiment x -22..22 / z -17..9 (facade principale au sud), deux niveaux : rez-de-
 * chaussee (sol y 64, pieds 65) et etage des chambres (plancher y 70, pieds 71) ; toit plat non accessible en y 76,
 * attique a gradins et lettres lumineuses « HOTEL » au-dessus du hall.
 * <ul>
 * <li>rez-de-chaussee : hall a double hauteur (reception, grand escalier, lustre), restaurant (ouest) et cuisines
 * (nord-ouest), bureau de la direction et salle du personnel (nord), bar-salon a piano (est), buanderie avec
 * l'escalier de service (nord-est) ;</li>
 * <li>etage : galerie autour du vide du hall, couloir des chambres, 6 chambres (4 au sud sur le balcon, 2 au nord),
 * suite (chambre, salon, salle de bains), office d'etage / lingerie avec l'escalier de service ;</li>
 * <li>dehors : cour d'honneur au sud (allee en boucle, fontaine, voiture), marquise et balcon continu de l'etage,
 * terrasse du restaurant (ouest) avec l'escalier exterieur du balcon, jardin a la francaise et kiosque (est), cour
 * de service (nord) avec camionnette de livraison et garage.</li>
 * </ul>
 * Trois escaliers relient les niveaux (grand escalier, escalier de service, escalier exterieur) ; chaque piece a au
 * moins deux sorties, sauf les petites chambres du nord (elles communiquent entre elles et avec l'office).
 */
final class Hotel extends MapKit {
    static final int VERSION = 2; // 1.13.0 : vitres dans le mur, 3 cases libres au-dessus des marches
    static final String ID = "hotel";
    static final String WORLD = "mm_hotel";
    static final String NAME = "Grand Hôtel";

    static final int BX1 = -34, BX2 = 34, BZ1 = -29, BZ2 = 29, MIN_Y = 62, MAX_Y = 84;
    static final int AREA = 52, Y_LO = 56, Y_HI = 98;
    /** Sol du rez-de-chaussee, plancher de l'etage, toit. */
    static final int G = 64, U = 70, R = 76;
    /** Lignes exterieures des facades. */
    static final int X1 = -22, X2 = 22, Z1 = -17, Z2 = 9;
    /** Interieur (les murs exterieurs prennent 2 rangs). */
    static final int IX1 = -20, IX2 = 20, IZ1 = -15, IZ2 = 7;
    /** Vide du hall (de U a R). */
    static final int VX1 = -5, VX2 = 5, VZ1 = 0, VZ2 = 4;
    static final int[] WORLD_SPAWN = {0, 65, 26};

    static final int[][] SPAWNS = {
            {-3, 65, 6},     // hall
            {-15, 65, 2},    // restaurant
            {-15, 65, -8},   // cuisines
            {-4, 65, -10},   // bureau
            {14, 65, -9},    // buanderie
            {15, 65, 4},     // bar
            {-12, 65, 20},   // cour d'honneur
            {-28, 65, -3},   // terrasse
            {28, 65, 8},     // jardin
            {6, 65, -23},    // cour de service
            {-14, 71, -2},   // couloir
            {-18, 71, 4},    // chambre 1
            {-12, 71, -7},   // suite
            {14, 71, -9},    // office d'etage
    };

    static final int[][] GOLD = {
            // rez-de-chaussee
            {4, 65, 6}, {-6, 65, 3}, {7, 65, -5}, {-19, 65, 6}, {-11, 65, -2}, {-19, 65, -1}, {-12, 65, -13},
            {-19, 65, -10}, {-7, 65, -14}, {-3, 65, -10}, {7, 65, -14}, {2, 65, -10}, {11, 65, -13}, {16, 65, -6},
            {17, 65, 3}, {11, 65, 1},
            // etage
            {-7, 71, 2}, {7, 71, 6}, {0, 71, -4}, {-19, 71, -2}, {19, 71, -2}, {-11, 71, 6}, {12, 71, 3},
            {19, 71, 6}, {-19, 71, -13}, {-11, 71, -13}, {-6, 71, -10}, {6, 71, -13}, {11, 71, -6},
            {-22, 71, 10}, {14, 71, 11},
            // dehors
            {-30, 65, 8}, {-27, 65, -12}, {29, 65, -9}, {27, 65, 18}, {13, 65, 24}, {-15, 65, 25},
            {-20, 65, -24}, {22, 65, -22}, {3, 65, 13},
    };

    private Hotel(Sink s) {
        super(s, 1925);
    }

    static void build(Sink s) {
        new Hotel(s).run();
    }

    // ================================================================ palettes

    private static final Palette WALL = Palette.of(81, "minecraft:white_terracotta", 62, "minecraft:calcite", 24,
            "minecraft:polished_diorite", 14).period(7);
    private static final Palette PLINTH = Palette.of(82, "minecraft:polished_blackstone_bricks", 62,
            "minecraft:polished_blackstone", 30, "minecraft:cracked_polished_blackstone_bricks", 8).period(6);
    private static final Palette PLASTER = Palette.of(83, "minecraft:smooth_quartz", 72, "minecraft:calcite", 28).period(8);
    private static final Palette PARQUET = Palette.of(84, "minecraft:dark_oak_planks", 70, "minecraft:spruce_planks", 30).period(6);
    private static final Palette OAKFLOOR = Palette.of(85, "minecraft:oak_planks", 70, "minecraft:birch_planks", 30).period(6);
    private static final Palette TILE = Palette.of(86, "minecraft:smooth_stone", 60, "minecraft:polished_andesite", 28,
            "minecraft:stone_bricks", 12).period(5);
    private static final Palette GRASS = Palette.of(87, "minecraft:grass_block", 82, "minecraft:moss_block", 8,
            "minecraft:podzol", 5, "minecraft:coarse_dirt", 5).period(5);
    private static final Palette PAVE = Palette.of(88, "minecraft:stone_bricks", 58, "minecraft:polished_andesite", 26,
            "minecraft:cracked_stone_bricks", 9, "minecraft:mossy_stone_bricks", 7).period(7);
    private static final Palette GRAVEL = Palette.of(89, "minecraft:gravel", 58, "minecraft:coarse_dirt", 24,
            "minecraft:packed_mud", 18).period(4);
    private static final Palette ASPHALT = Palette.of(90, "minecraft:gray_concrete", 68, "minecraft:gray_concrete_powder", 20,
            "minecraft:cyan_terracotta", 12).period(6);
    private static final Palette HEDGE = Palette.of(91, "minecraft:azalea_leaves[persistent=true]", 66,
            "minecraft:oak_leaves[persistent=true]", 34).period(4);
    private static final Palette TOWN = Palette.of(92, "minecraft:bricks", 55, "minecraft:mud_bricks", 25, "minecraft:terracotta", 20).period(8);
    private static final Palette DECO_DARK = Palette.of(93, "minecraft:polished_deepslate", 64, "minecraft:deepslate_tiles", 36).period(5);

    private static final String GOLD_B = "minecraft:gold_block";
    private static final String DOOR = "minecraft:dark_oak_door";

    // ================================================================ deroule

    private void run() {
        terrain();
        shell();
        partitionsGround();
        partitionsUpper();
        hallVoid();
        grandStair();
        flight(18, -7, Dir.NORTH, 2, G, 6, "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:spruce_fence"); // service
        outerStair();
        balcony();
        trims();
        hall();
        restaurant();
        kitchen();
        office();
        staffRoom();
        laundry();
        bar();
        gallery();
        corridor();
        rooms();
        suite();
        linenRoom();
        roof();
        forecourt();
        terrace();
        eastGarden();
        serviceCourt();
        fence();
        city();
        clearDoors();
        fillDarkSpots();
        finish(-AREA, G - 2, -AREA, AREA, Y_HI - 1, AREA);
    }

    private boolean inBox(int x, int z) {
        return x >= BX1 && x <= BX2 && z >= BZ1 && z <= BZ2;
    }

    private boolean inBuilding(int x, int z) {
        return x >= X1 && x <= X2 && z >= Z1 && z <= Z2;
    }

    @Override
    void set(int x, int y, int z, String st) {
        if (st != null) super.set(x, y, z, st);
    }

    // ================================================================ terrain

    private void terrain() {
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                int depth = 5 + (int) Math.round(2 * noise.noise2(x / 7.0, z / 7.0));
                for (int y = G - depth; y < G; y++) set(x, y, z, y >= G - 2 ? "minecraft:dirt" : "minecraft:stone");
                String top;
                if (inBox(x, z)) top = GRASS.at(x, G, z);
                else if (x >= BX1 - 2 && x <= BX2 + 2 && z >= BZ1 - 2 && z <= BZ2 + 2) top = PAVE.at(x, G, z); // trottoir
                else if (x >= BX1 - 9 && x <= BX2 + 9 && z >= BZ1 - 9 && z <= BZ2 + 9) top = ASPHALT.at(x, G, z); // rue
                else top = PAVE.at(x, G, z);
                set(x, G, z, top);
            }
        // marquages de la rue
        for (int t = -AREA; t <= AREA; t++) {
            if (Math.floorMod(t, 6) >= 3) continue;
            street(t, BZ2 + 6);
            street(t, BZ1 - 6);
            if (t > BZ1 - 6 && t < BZ2 + 6) { street(BX1 - 6, t); street(BX2 + 6, t); }
        }
        // allee couverte au pied de la facade sud et trottoir autour du batiment
        for (int x = X1 - 3; x <= X2 + 3; x++)
            for (int z = Z1 - 2; z <= Z2 + 3; z++)
                if (!inBuilding(x, z)) set(x, G, z, PAVE.at(x, G, z));
    }

    private void street(int x, int z) {
        if (Math.abs(x) <= AREA && Math.abs(z) <= AREA && get(x, G, z).contains("concrete")) set(x, G, z, "minecraft:white_concrete");
    }

    // ================================================================ enveloppe

    private Style style() {
        return new Style().wall(WALL).plinth(PLINTH).plinthStairs("minecraft:polished_blackstone_brick_stairs")
                .post("minecraft:quartz_pillar").corner(Mat.of("minecraft:quartz_bricks")).pane("minecraft:glass_pane")
                .sill("minecraft:polished_blackstone_stairs").lintel("minecraft:smooth_quartz_slab").shutter(null)
                .band("minecraft:dark_prismarine_stairs").cornice("minecraft:smooth_quartz_stairs").spacing(5).storey(6)
                .blankBays(0).innerPosts(false).seed(29);
    }

    private void shell() {
        // planchers et plafond
        for (int x = IX1; x <= IX2; x++)
            for (int z = IZ1; z <= IZ2; z++) {
                set(x, G, z, groundFloor(x, z));
                set(x, U, z, upperFloor(x, z));
                set(x, R, z, ceiling(x, z));
                for (int y = G + 1; y < R; y++) if (y != U) set(x, y, z, B.AIR);
            }
        Style st = style();
        Facade.wall(s, st, X1, Z1, Dir.EAST, X2 - X1, Dir.NORTH, G, R, -1);
        Facade.wall(s, st, X1, Z2, Dir.EAST, X2 - X1, Dir.SOUTH, G, R, -1);
        Facade.wall(s, st, X1, Z1, Dir.SOUTH, Z2 - Z1, Dir.WEST, G, R, -1);
        Facade.wall(s, st, X2, Z1, Dir.SOUTH, Z2 - Z1, Dir.EAST, G, R, -1);
        // pavillons d'angle Art deco : nervures en saillie (+1), qui montent en gradins au-dessus de la corniche
        for (int[] c : new int[][]{{X1, Z1}, {X2, Z1}, {X1, Z2}, {X2, Z2}}) {
            int sx = Integer.signum(c[0]), sz = Integer.signum(c[1]);
            // au sud, le balcon longe la facade et l'escalier exterieur longe la facade ouest : pas de nervure la
            boolean side = !(c[0] == X1 && c[1] == Z2), front = c[1] == Z1;
            for (int i = 0; i <= 4; i++) {
                int top = R + 3 - (i >= 3 ? 1 : 0);
                for (int y = G; y <= top; y++) {
                    String m = y == G ? PLINTH.at(0, y, i) : (y >= R + 1 ? "minecraft:smooth_quartz" : "minecraft:quartz_bricks");
                    if (i == 0 || i == 2 || i == 4 || y == G || y >= R) {
                        if (side) set(c[0] + sx, y, c[1] - sz * i, m);
                        if (front) set(c[0] - sx * i, y, c[1] + sz, m);
                    }
                }
                if (side) set(c[0] + sx, top + 1, c[1] - sz * i, slab("minecraft:smooth_quartz_slab", "bottom"));
                if (front) set(c[0] - sx * i, top + 1, c[1] + sz, slab("minecraft:smooth_quartz_slab", "bottom"));
            }
            if (!front) continue;
            // fleche d'angle doree
            set(c[0] + sx, R + 5, c[1] + sz, "minecraft:quartz_bricks");
            set(c[0] + sx, R + 6, c[1] + sz, GOLD_B);
            set(c[0] + sx, R + 7, c[1] + sz, "minecraft:lightning_rod[facing=up]");
            for (int y = G; y <= R + 4; y++) set(c[0] + sx, y, c[1] + sz, y == G ? PLINTH.at(0, y, 0) : "minecraft:quartz_bricks");
        }
        // entree principale (3 de large, 4 de haut) et portail noir et or
        for (int x = -1; x <= 1; x++)
            for (int y = G + 1; y <= G + 4; y++)
                for (int z = Z2 - 2; z <= Z2; z++) set(x, y, z, B.AIR);
        for (int y = G + 1; y <= G + 5; y++) {
            set(-2, y, Z2, y == G + 5 ? GOLD_B : "minecraft:polished_blackstone_bricks");
            set(2, y, Z2, y == G + 5 ? GOLD_B : "minecraft:polished_blackstone_bricks");
        }
        for (int x = -1; x <= 1; x++) {
            set(x, G + 5, Z2, x == 0 ? "minecraft:chiseled_quartz_block" : stairs("minecraft:smooth_quartz_stairs", x < 0 ? Dir.EAST : Dir.WEST, true));
            set(x, G, Z2 + 1, stairs("minecraft:polished_blackstone_brick_stairs", Dir.NORTH, false));
            set(x, G, Z2, "minecraft:polished_blackstone");
            set(x, G, Z2 - 1, "minecraft:polished_blackstone");
        }
        // portes laterales (2 de large) : restaurant -> terrasse, bar -> jardin
        sideDoor(X1, 0, Dir.WEST);
        sideDoor(X2, 0, Dir.EAST);
        // portes de service au nord : cuisines, buanderie
        northDoor(-14);
        northDoor(13);
        // portes-fenetres de l'etage sur le balcon
        for (int x : new int[]{-18, -13, 13, 18}) frenchDoor(x);
    }

    /** Porte double sur une facade est/ouest (x = ligne P), en z..z+1. */
    private void sideDoor(int x, int z, Dir out) {
        int in = -out.dx;
        for (int dz = 0; dz <= 1; dz++) {
            for (int d = 0; d <= 2; d++) for (int y = G + 1; y <= G + 3; y++) set(x + in * d, y, z + dz, B.AIR);
            set(x + in, G + 1, z + dz, B.state(B.door(DOOR, out.opposite(), false, dz == 1), "open", "false"));
            set(x + in, G + 2, z + dz, B.state(B.door(DOOR, out.opposite(), true, dz == 1), "open", "false"));
            set(x + in, G + 3, z + dz, "minecraft:chiseled_quartz_block");
            set(x + out.dx, G, z + dz, stairs("minecraft:polished_blackstone_brick_stairs", out.opposite(), false));
            set(x, G, z + dz, "minecraft:polished_blackstone");
        }
        set(x + out.dx, G + 3, z - 1, standing("minecraft:lantern"));
        set(x + out.dx, G + 3, z + 2, standing("minecraft:lantern"));
        setIfAir(x + out.dx, G + 2, z - 1, "minecraft:polished_blackstone_wall");
        setIfAir(x + out.dx, G + 2, z + 2, "minecraft:polished_blackstone_wall");
        setIfAir(x + out.dx, G + 1, z - 1, "minecraft:polished_blackstone");
        setIfAir(x + out.dx, G + 1, z + 2, "minecraft:polished_blackstone");
    }

    private void northDoor(int x) {
        for (int d = 0; d <= 2; d++) for (int y = G + 1; y <= G + 2; y++) set(x, y, Z1 + d, B.AIR);
        set(x, G + 1, Z1 + 1, B.state(B.door(DOOR, Dir.SOUTH, false, false), "open", "false"));
        set(x, G + 2, Z1 + 1, B.state(B.door(DOOR, Dir.SOUTH, true, false), "open", "false"));
        set(x, G, Z1 - 1, stairs("minecraft:polished_blackstone_brick_stairs", Dir.SOUTH, false));
        set(x, G, Z1, "minecraft:polished_blackstone");
        // auvent en dalles et lanterne
        for (int dx = -1; dx <= 1; dx++) set(x + dx, G + 4, Z1 - 1, slab("minecraft:dark_oak_slab", "top"));
        set(x + 1, G + 3, Z1 - 1, hanging("minecraft:lantern"));
    }

    /** Porte-fenetre d'une chambre sud sur le balcon (porte dans le plan P-1). */
    private void frenchDoor(int x) {
        for (int y = U + 1; y <= U + 2; y++) {
            set(x, y, Z2, B.AIR);
            set(x, y, Z2 - 2, B.AIR);
        }
        set(x, U + 1, Z2 - 1, B.state(B.door(DOOR, Dir.NORTH, false, false), "open", "false"));
        set(x, U + 2, Z2 - 1, B.state(B.door(DOOR, Dir.NORTH, true, false), "open", "false"));
        set(x, U + 3, Z2 - 1, "minecraft:chiseled_quartz_block");
        set(x, U + 3, Z2 - 2, B.AIR);
    }

    // ================================================================ sols et plafonds

    private boolean inVoid(int x, int z) {
        return x >= VX1 && x <= VX2 && z >= VZ1 && z <= VZ2;
    }

    private String groundFloor(int x, int z) {
        if (x >= -8 && x <= 8 && z >= -6) {
            // hall : soleil Art deco qui rayonne depuis l'entree, bordure noire, centre dore
            if (x == -8 || x == 8 || z == -6) return "minecraft:polished_blackstone";
            double dx = x, dz = z - 8.5;
            double d = Math.hypot(dx, dz);
            if (d < 2.2) return "minecraft:gold_block";
            if (d < 3.2) return "minecraft:polished_blackstone";
            int ray = (int) Math.floor((Math.atan2(dx, -dz) / Math.PI + 1) * 8);
            return (ray & 1) == 0 ? "minecraft:polished_diorite" : "minecraft:smooth_quartz";
        }
        if (x <= -10 && z >= -3) return PARQUET.at(x, G, z);
        if (x <= -10) return ((x + z) & 1) == 0 ? "minecraft:white_concrete" : "minecraft:light_gray_concrete";
        if (x >= 10 && z >= -3) return DECO_DARK.at(x, G, z);
        if (x >= 10) return TILE.at(x, G, z);
        if (x <= -1) return PARQUET.at(x, G, z);
        return OAKFLOOR.at(x, G, z);
    }

    private String upperFloor(int x, int z) {
        if (inVoid(x, z)) return B.AIR;
        if (x >= -8 && x <= 8 && z >= -6) return ((x + z) & 1) == 0 ? "minecraft:polished_diorite" : "minecraft:smooth_quartz";
        if (z >= -3 && z <= -1) return OAKFLOOR.at(x, U, z);
        if (x >= -14 && x <= -10 && z <= -11) return ((x + z) & 1) == 0 ? "minecraft:smooth_quartz" : "minecraft:polished_diorite"; // bains
        if (x <= -10 && z <= -4) return PARQUET.at(x, U, z);
        if (x >= 10 && z <= -4) return "minecraft:spruce_planks";
        return OAKFLOOR.at(x, U, z);
    }

    private String ceiling(int x, int z) {
        // plafond de l'etage : caissons de quartz et dalles lumineuses (froglights) dans les circulations
        boolean circ = (z >= -3 && z <= -1) || (x >= -8 && x <= 8 && z >= -6);
        if (circ && Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 0) return "minecraft:pearlescent_froglight";
        if (!circ && Math.floorMod(x, 5) == 2 && Math.floorMod(z, 5) == 2) return "minecraft:pearlescent_froglight";
        return Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? "minecraft:quartz_bricks" : "minecraft:smooth_quartz";
    }

    // ================================================================ cloisons

    private void wallX(int x, int z1, int z2, int base) {
        for (int z = z1; z <= z2; z++) for (int y = base + 1; y <= base + 5; y++) set(x, y, z, PLASTER.at(x, y, z));
    }

    private void wallZ(int z, int x1, int x2, int base) {
        for (int x = x1; x <= x2; x++) for (int y = base + 1; y <= base + 5; y++) set(x, y, z, PLASTER.at(x, y, z));
    }

    /** Porte en bois dans une cloison (case x, z), cadre en bois sombre. */
    private void door(int x, int z, Dir facing, int base) {
        set(x, base + 1, z, B.state(B.door(DOOR, facing, false, false), "open", "false"));
        set(x, base + 2, z, B.state(B.door(DOOR, facing, true, false), "open", "false"));
        set(x, base + 3, z, log("minecraft:stripped_dark_oak_log", facing.axis() == 'x' ? 'z' : 'x'));
    }

    /** Arche de 3 de large et 4 de haut dans une cloison x = const. */
    private void archX(int x, int zc, int base) {
        for (int z = zc - 1; z <= zc + 1; z++) for (int y = base + 1; y <= base + 4; y++) set(x, y, z, B.AIR);
        set(x, base + 4, zc - 1, stairs("minecraft:smooth_quartz_stairs", Dir.SOUTH, true));
        set(x, base + 4, zc + 1, stairs("minecraft:smooth_quartz_stairs", Dir.NORTH, true));
        set(x, base + 5, zc, GOLD_B);
    }

    private void partitionsGround() {
        wallX(-9, IZ1, IZ2, G);
        wallX(9, IZ1, IZ2, G);
        wallZ(-4, IX1, -10, G);
        wallZ(-4, 10, IX2, G);
        wallZ(-7, -8, 8, G);
        wallX(0, IZ1, -8, G);
        archX(-9, 2, G);
        archX(9, 2, G);
        door(-17, -4, Dir.SOUTH, G);
        door(-12, -4, Dir.SOUTH, G);
        door(-9, -12, Dir.EAST, G);
        door(0, -11, Dir.EAST, G);
        door(9, -12, Dir.EAST, G);
        door(-5, -7, Dir.SOUTH, G);
        door(5, -7, Dir.SOUTH, G);
        door(13, -4, Dir.SOUTH, G);
        // passe-plat entre la cuisine et le restaurant
        for (int x = -15; x <= -14; x++) {
            set(x, G + 2, -4, slab("minecraft:smooth_stone_slab", "bottom"));
            set(x, G + 3, -4, B.AIR);
        }
    }

    private void partitionsUpper() {
        wallZ(-4, IX1, -10, U);
        wallZ(-4, 10, IX2, U);
        wallZ(0, IX1, -10, U);
        wallZ(0, 10, IX2, U);
        wallX(-9, IZ1, -4, U);
        wallX(9, IZ1, -4, U);
        wallX(-9, 0, IZ2, U);
        wallX(9, 0, IZ2, U);
        wallZ(-7, -8, 8, U);
        wallX(0, IZ1, -8, U);
        wallX(-15, 0, IZ2, U);
        wallX(15, 0, IZ2, U);
        wallX(-15, IZ1, -5, U);
        wallZ(-10, -14, -10, U);
        // chambres sud
        door(-18, 0, Dir.SOUTH, U);
        door(-12, 0, Dir.SOUTH, U);
        door(12, 0, Dir.SOUTH, U);
        door(18, 0, Dir.SOUTH, U);
        door(-15, 4, Dir.EAST, U);
        door(15, 4, Dir.EAST, U);
        // suite
        door(-18, -4, Dir.NORTH, U);
        door(-12, -4, Dir.NORTH, U);
        door(-15, -7, Dir.EAST, U);
        door(-15, -13, Dir.EAST, U);
        door(-12, -10, Dir.NORTH, U);
        // chambres nord et office d'etage
        door(-5, -7, Dir.NORTH, U);
        door(5, -7, Dir.NORTH, U);
        door(0, -11, Dir.EAST, U);
        door(9, -11, Dir.EAST, U);
        door(12, -4, Dir.NORTH, U);
        door(16, -4, Dir.NORTH, U);
    }

    // ================================================================ hall a double hauteur et grand escalier

    private void hallVoid() {
        // garde-corps de la galerie autour du vide (murets de deepslate poli, main courante doree aux angles)
        for (int x = VX1 - 1; x <= VX2 + 1; x++) set(x, U + 1, VZ2 + 1, "minecraft:polished_deepslate_wall");
        for (int z = VZ1 - 1; z <= VZ2 + 1; z++) {
            set(VX1 - 1, U + 1, z, "minecraft:polished_deepslate_wall");
            set(VX2 + 1, U + 1, z, "minecraft:polished_deepslate_wall");
        }
        for (int x = VX1; x <= VX2; x++) if (Math.abs(x) >= 3) set(x, U + 1, VZ1 - 1, "minecraft:polished_deepslate_wall");
        for (int[] c : new int[][]{{VX1 - 1, VZ1 - 1}, {VX2 + 1, VZ1 - 1}, {VX1 - 1, VZ2 + 1}, {VX2 + 1, VZ2 + 1}}) {
            set(c[0], U + 1, c[1], "minecraft:polished_blackstone");
            set(c[0], U + 2, c[1], standing("minecraft:lantern"));
        }
        // nez du plancher de la galerie (bandeau dore sous le garde-corps)
        for (int x = VX1; x <= VX2; x++) set(x, U - 1, VZ2 + 1, stairs("minecraft:smooth_quartz_stairs", Dir.SOUTH, true));
        for (int z = VZ1; z <= VZ2; z++) {
            set(VX1 - 1, U - 1, z, stairs("minecraft:smooth_quartz_stairs", Dir.WEST, true));
            set(VX2 + 1, U - 1, z, stairs("minecraft:smooth_quartz_stairs", Dir.EAST, true));
        }
        // grand lustre sous le plafond
        chandelier(0, R - 1, 2, 2, false, "minecraft:birch_trapdoor", "minecraft:lantern");
        set(0, R - 1, 2, GOLD_B);
        for (int x : new int[]{-3, 3}) hangLamp(x, R, 2, "minecraft:lantern");
    }

    private void grandStair() {
        // volee de 3 de large au milieu du hall : de z 5 (pied, sous la galerie) a z 0 (palier de la galerie)
        for (int k = 0; k < 6; k++) {
            int z = 5 - k, y = G + 1 + k;
            for (int x = -1; x <= 1; x++) {
                for (int yy = G + 1; yy < y; yy++) set(x, yy, z, "minecraft:polished_diorite");
                set(x, y, z, stairs("minecraft:smooth_quartz_stairs", Dir.NORTH, false));
                set(x, y + 1, z, B.AIR);
                set(x, y + 2, z, B.AIR);
            }
            // limons en marbre noir et rampes en murets
            for (int x : new int[]{-2, 2}) {
                for (int yy = G + 1; yy <= y; yy++) set(x, yy, z, "minecraft:polished_blackstone_bricks");
                set(x, y + 1, z, "minecraft:polished_blackstone_wall");
            }
        }
        // tapis rouge du pied de l'escalier jusqu'a l'entree
        for (int z = 6; z <= 7; z++) for (int x = -1; x <= 1; x++) set(x, G + 1, z, x == 0 ? "minecraft:red_carpet" : "minecraft:yellow_carpet");
        set(-2, G + 2, 5, standing("minecraft:lantern"));
        set(2, G + 2, 5, standing("minecraft:lantern"));
    }

    /**
     * Volee droite de n marches depuis (x0, z0) vers dir (largeur w vers la droite de dir), du niveau base au niveau
     * base + n : remplissage sous les marches, tremie minimale dans le plancher du dessus, barrieres autour de la tremie.
     */
    private void flight(int x0, int z0, Dir dir, int w, int base, int n, String fill, String stairId, String rail) {
        Dir side = dir.cw();
        for (int k = 0; k < n; k++)
            for (int j = 0; j < w; j++) {
                int x = x0 + dir.dx * k + side.dx * j, z = z0 + dir.dz * k + side.dz * j;
                for (int y = base + 1; y <= base + k; y++) set(x, y, z, fill);
                set(x, base + 1 + k, z, stairs(stairId, dir, false));
                set(x, base + 2 + k, z, B.AIR);
                set(x, base + 3 + k, z, B.AIR);
                // 3e case libre (Esteban, 17/11) : la tremie s'ouvre une marche plus tot, sinon on se cogne la tete
                if (base + 4 + k <= base + n) set(x, base + 4 + k, z, B.AIR);
            }
        int top = base + n;
        for (int k = n - 5; k < n; k++)
            for (int j : new int[]{-1, w}) {
                int x = x0 + dir.dx * k + side.dx * j, z = z0 + dir.dz * k + side.dz * j;
                if (B.isSolid(get(x, top, z))) setIfAir(x, top + 1, z, rail);
            }
        for (int j = 0; j < w; j++) {
            int x = x0 + dir.dx * (n - 5) + side.dx * j, z = z0 + dir.dz * (n - 5) + side.dz * j;
            if (B.isSolid(get(x, top, z))) setIfAir(x, top + 1, z, rail);
        }
    }

    private void outerStair() {
        // escalier exterieur de la terrasse au balcon, le long de la facade ouest (monte vers le sud)
        for (int k = 0; k < 6; k++) {
            int z = 4 + k, y = G + 1 + k;
            for (int x = X1 - 2; x <= X1 - 1; x++) {
                for (int yy = G + 1; yy < y; yy++) set(x, yy, z, PLINTH.at(x, yy, z));
                set(x, y, z, stairs("minecraft:polished_blackstone_brick_stairs", Dir.SOUTH, false));
                set(x, y + 1, z, B.AIR);
                set(x, y + 2, z, B.AIR);
            }
            for (int yy = G + 1; yy <= y; yy++) set(X1 - 3, yy, z, PLINTH.at(X1 - 3, yy, z));
            set(X1 - 3, y + 1, z, "minecraft:polished_blackstone_wall");
        }
        set(X1 - 3, G + 3, 3, standing("minecraft:lantern"));
        set(X1 - 3, G + 2, 3, "minecraft:polished_blackstone_wall");
        set(X1 - 3, G + 1, 3, "minecraft:polished_blackstone");
    }

    // ================================================================ balcon et marquise

    private void balcony() {
        int z1 = Z2 + 1, z2 = Z2 + 2;
        for (int x = X1 - 2; x <= X2 + 2; x++)
            for (int z = z1; z <= z2; z++) set(x, U, z, slab("minecraft:smooth_quartz_slab", "top"));
        // marquise au-dessus de l'entree : avancee jusqu'en z 15, colonnes noires a chapiteau dore
        for (int x = -5; x <= 5; x++)
            for (int z = z2 + 1; z <= 15; z++) set(x, U, z, slab("minecraft:smooth_quartz_slab", "top"));
        for (int x = -5; x <= 5; x++) set(x, U - 1, 15, stairs("minecraft:smooth_quartz_stairs", Dir.NORTH, true));
        for (int z = z2 + 1; z <= 14; z++) {
            set(-5, U - 1, z, stairs("minecraft:smooth_quartz_stairs", Dir.EAST, true));
            set(5, U - 1, z, stairs("minecraft:smooth_quartz_stairs", Dir.WEST, true));
        }
        for (int[] c : new int[][]{{-5, 15}, {5, 15}, {-5, 12}, {5, 12}}) {
            for (int y = G + 1; y <= U - 2; y++) set(c[0], y, c[1], y == G + 1 ? "minecraft:polished_blackstone" : "minecraft:polished_blackstone_wall");
            set(c[0], U - 1, c[1], GOLD_B);
        }
        // lustre de la marquise
        for (int x : new int[]{-2, 2}) {
            set(x, U - 1, 13, chain('y'));
            set(x, U - 2, 13, hanging("minecraft:lantern"));
        }
        // garde-corps : barrieres en fer forge (barreaux) sur une lisse de quartz
        for (int x = X1 - 3; x <= X2 + 3; x++) {
            if (Math.abs(x) <= 5) continue;
            set(x, U + 1, z2 + 1, "minecraft:iron_bars");
            set(x, U, z2 + 1, slab("minecraft:smooth_quartz_slab", "top"));
        }
        for (int x = -6; x <= 6; x++) { set(x, U + 1, 16, "minecraft:iron_bars"); set(x, U, 16, slab("minecraft:smooth_quartz_slab", "top")); }
        for (int z = z2 + 1; z <= 15; z++) for (int x : new int[]{-6, 6}) { set(x, U + 1, z, "minecraft:iron_bars"); set(x, U, z, slab("minecraft:smooth_quartz_slab", "top")); }
        for (int z = z1; z <= z2; z++) {
            set(X2 + 3, U + 1, z, "minecraft:iron_bars");
            set(X2 + 3, U, z, slab("minecraft:smooth_quartz_slab", "top"));
            set(X1 - 3, U + 1, z, "minecraft:iron_bars");
            set(X1 - 3, U, z, slab("minecraft:smooth_quartz_slab", "top"));
        }
        // consoles sous le balcon, jardinieres et lanternes sur le garde-corps
        for (int x = X1 - 1; x <= X2 + 1; x++) {
            if (Math.floorMod(x, 4) == 1 && Math.abs(x) > 5) set(x, U - 1, z1, stairs("minecraft:smooth_quartz_stairs", Dir.NORTH, true));
            if (Math.floorMod(x, 8) == 3 && Math.abs(x) > 6) {
                set(x, U + 1, z2 + 1, "minecraft:polished_blackstone");
                set(x, U + 2, z2 + 1, standing("minecraft:lantern"));
            }
        }
        // jardinieres contre la facade (a 2 cases du garde-corps : pas de marchepied pour sauter par-dessus)
        for (int x : new int[]{-21, -9, 9, 21}) set(x, U + 1, z1, "minecraft:potted_flowering_azalea_bush");
        cafeTable(16, U + 1, z1, "birch", false);
        // marquise : bacs a palmiers loin du garde-corps
        bigPlant(-3, U + 1, 13, "dark_oak");
        bigPlant(3, U + 1, 13, "dark_oak");
        // eclairage de l'allee couverte (lanternes suspendues sous le balcon)
        for (int x = X1 - 2; x <= X2 + 2; x++)
            if (Math.floorMod(x, 6) == 0 && Math.abs(x) > 6) {
                set(x, U - 1, z2, chain('y'));
                set(x, U - 2, z2, hanging("minecraft:lantern"));
            }
    }

    // ================================================================ moulures des pieces

    private void trims() {
        int[][] rooms = {
                // rez : restaurant, cuisines, bureau, personnel, buanderie, bar, hall
                {IX1, -3, -10, IZ2, G}, {IX1, IZ1, -10, -5, G}, {-8, IZ1, -1, -8, G}, {1, IZ1, 8, -8, G},
                {10, IZ1, IX2, -5, G}, {10, -3, IX2, IZ2, G}, {-8, -6, 8, IZ2, G},
                // etage : chambres sud, suite (3 pieces), chambres nord, office, couloir
                {IX1, 1, -16, IZ2, U}, {-14, 1, -10, IZ2, U}, {10, 1, 14, IZ2, U}, {16, 1, IX2, IZ2, U},
                {IX1, IZ1, -16, -5, U}, {-14, -9, -10, -5, U}, {-14, IZ1, -10, -11, U},
                {-8, IZ1, -1, -8, U}, {1, IZ1, 8, -8, U}, {10, IZ1, IX2, -5, U}, {IX1, -3, IX2, -1, U},
        };
        for (int[] r : rooms) trimRoom(r[0], r[1], r[2], r[3], r[4]);
    }

    /** Plinthe, cimaise, corniche et pilastres le long des murs d'une piece (seulement dans l'air). */
    private void trimRoom(int x1, int z1, int x2, int z2, int base) {
        // decisions d'abord (sur les murs seuls), pose ensuite : un pilastre pose ne doit pas servir de « mur » a la
        // case voisine
        java.util.List<int[]> todo = new java.util.ArrayList<>();
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (x != x1 && x != x2 && z != z1 && z != z2) continue;
                if (base == U && inVoid(x, z)) continue;
                if (isAir(x, base, z) || !isAir(x, base + 1, z) || B.name(get(x, base, z)).endsWith("_stairs")) continue; // tremie, marche, case occupee
                Dir toWall = null;
                for (Dir d : Dir.values()) {
                    if (B.isSolid(get(x + d.dx, base + 1, z + d.dz)) && B.isSolid(get(x + d.dx, base + 3, z + d.dz))
                            && B.isSolid(get(x + d.dx, base + 5, z + d.dz))) { toWall = d; break; }
                }
                if (toWall == null) continue;
                int t = toWall.axis() == 'x' ? z : x;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                boolean pil = (Math.floorMod(t, 4) == 0 || corner) && isAir(x, base + 2, z) && isAir(x, base + 5, z);
                todo.add(new int[]{x, z, toWall.ordinal(), pil ? 1 : 0});
            }
        for (int[] c : todo) {
            int x = c[0], z = c[1];
            Dir toWall = Dir.values()[c[2]];
            if (c[3] == 1) {
                for (int y = base + 1; y <= base + 5; y++) if (isAir(x, y, z)) set(x, y, z, y == base + 5 ? "minecraft:chiseled_quartz_block" : log("minecraft:stripped_dark_oak_log", 'y'));
            } else {
                // plinthe dans la cloison elle-meme (la case de la piece reste praticable), cimaise au-dessus de la tete
                int wx = x + toWall.dx, wz = z + toWall.dz;
                String w = B.name(get(wx, base + 1, wz));
                if (w.equals("smooth_quartz") || w.equals("calcite")) set(wx, base + 1, wz, "minecraft:stripped_dark_oak_wood[axis=y]");
                setIfAir(x, base + 3, z, trap("minecraft:dark_oak_trapdoor", toWall.opposite(), true, false));
                setIfAir(x, base + 5, z, stairs("minecraft:smooth_quartz_stairs", toWall, true));
            }
        }
    }

    // ================================================================ rez-de-chaussee

    private void hall() {
        // comptoir de la reception (ouest) : bureau en escaliers retournes, casiers a cles au mur, sonnette
        for (int z = -5; z <= -2; z++) {
            set(-6, G + 1, z, "minecraft:dark_oak_planks");
            set(-6, G + 2, z, stairs("minecraft:dark_oak_stairs", Dir.WEST, true));
            set(-9, G + 2, z, "minecraft:chiseled_bookshelf[facing=east]");
            set(-9, G + 3, z, "minecraft:chiseled_bookshelf[facing=east]");
        }
        set(-6, G + 1, -5, GOLD_B);
        set(-6, G + 1, -2, GOLD_B);
        set(-6, G + 3, -3, "minecraft:bell[attachment=floor,facing=east]");
        set(-6, G + 3, -4, candles(2, "white"));
        // salon d'attente (est) : fauteuils, table basse, grandes plantes, malles sur un chariot
        armchair(6, G + 1, -2, Dir.WEST, "dark_oak");
        armchair(6, G + 1, 1, Dir.WEST, "dark_oak");
        set(4, G + 1, -1, "minecraft:dark_oak_fence");
        set(4, G + 2, -1, "minecraft:dark_oak_pressure_plate");
        set(4, G + 1, 0, "minecraft:dark_oak_fence");
        set(4, G + 2, 0, "minecraft:dark_oak_pressure_plate");
        rug(3, -3, 7, 2, G + 1, "minecraft:yellow_carpet", "minecraft:black_carpet");
        bigPlant(-7, G + 1, 7, "dark_oak");
        bigPlant(7, G + 1, 7, "dark_oak");
        bigPlant(-7, G + 1, -6, "dark_oak");
        set(7, G + 1, -6, "minecraft:barrel[facing=up,open=false]");
        set(7, G + 2, -6, "minecraft:brown_wool");
        set(6, G + 1, -6, "minecraft:barrel[facing=north,open=false]");
        // horloge (cadran dore) au-dessus de la porte du bureau
        set(-5, G + 4, -8, GOLD_B);
        // ascenseur decoratif dans l'angle nord-est (cage en fer forge, fond dore, ne s'ouvre pas)
        for (int y = G + 1; y <= U - 1; y++) {
            set(3, y, -6, "minecraft:iron_bars");
            set(3, y, -5, "minecraft:iron_bars");
            set(2, y, -5, "minecraft:iron_bars");
        }
        set(2, G + 1, -6, "minecraft:waxed_cut_copper");
        set(2, G + 4, -6, "minecraft:waxed_cut_copper");
        for (int y = G + 2; y <= G + 3; y++) set(2, y, -6, GOLD_B);
        set(2, U - 1, -6, "minecraft:polished_blackstone");
        set(3, U - 1, -6, "minecraft:polished_blackstone");
        set(3, U - 1, -5, "minecraft:polished_blackstone");
        set(2, U - 1, -5, "minecraft:polished_blackstone");
        // appliques
        hangLamp(-5, U, 6, "minecraft:lantern");
        hangLamp(5, U, 6, "minecraft:lantern");
        hangLamp(-5, U, -3, "minecraft:lantern");
        hangLamp(5, U, -3, "minecraft:lantern");
        hangLamp(0, U, -4, "minecraft:lantern");
    }

    private void restaurant() {
        // poutres du plafond
        for (int x = IX1; x <= -10; x++) for (int z = -3; z <= IZ2; z++)
            if (Math.floorMod(z, 4) == 1 && isAir(x, U - 1, z)) set(x, U - 1, z, log("minecraft:stripped_dark_oak_log", 'x'));
        // tables rondes nappees de blanc (dalle de quartz) et chaises
        for (int[] t : new int[][]{{-17, 0}, {-12, 0}, {-17, 5}, {-12, 4}}) {
            set(t[0], G + 1, t[1], "minecraft:dark_oak_fence");
            set(t[0], G + 2, t[1], slab("minecraft:smooth_quartz_slab", "bottom")); // nappe blanche
            for (Dir d : Dir.values()) if (h(t[0], d.ordinal(), t[1]) < 0.75) setIfAir(t[0] + d.dx, G + 1, t[1] + d.dz, stairs("minecraft:dark_oak_stairs", d, false));
            set(t[0], G + 3, t[1], candles(1 + (int) (h(t[0], 3, t[1]) * 3), "red"));
        }
        // buffet le long du mur nord (entre les deux portes)
        for (int x = -16; x <= -13; x++) {
            set(x, G + 1, -3, x == -16 || x == -13 ? "minecraft:dark_oak_planks" : stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
            if (x == -15) set(x, G + 2, -3, "minecraft:cake");
        }
        set(-13, G + 2, -3, "minecraft:potted_flowering_azalea_bush");
        // banquette d'angle et piano droit
        for (int z = 3; z <= 6; z++) set(IX1, G + 1, z, stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
        bigPlant(-11, G + 1, IZ2, "dark_oak");
        // lustres
        chandelier(-15, U - 1, 2, 1, false, "minecraft:dark_oak_trapdoor", "minecraft:lantern");
        hangLamp(-18, U, -2, "minecraft:lantern");
        hangLamp(-12, U, 7, "minecraft:lantern");
    }

    private void kitchen() {
        // fourneaux et fours le long du mur nord (sauf devant la porte de service)
        for (int x = IX1; x <= -10; x++) {
            if (Math.abs(x + 14) <= 1) continue;
            String b = switch (Math.floorMod(x, 4)) {
                case 0 -> "minecraft:smoker[facing=south,lit=true]";
                case 1 -> "minecraft:furnace[facing=south,lit=true]";
                case 2 -> "minecraft:water_cauldron[level=3]";
                default -> "minecraft:smooth_stone";
            };
            set(x, G + 1, IZ1, b);
            set(x, G + 3, IZ1, slab("minecraft:smooth_stone_slab", "top"));
            if (Math.floorMod(x, 2) == 0) set(x, G + 4, IZ1, "minecraft:decorated_pot");
        }
        // hotte
        for (int x = -20; x <= -17; x++) set(x, U - 1, IZ1 + 1, slab("minecraft:smooth_stone_slab", "top"));
        // ilot central (plan de travail) et tabourets
        for (int x = -17; x <= -13; x++) for (int z = -11; z <= -10; z++) {
            set(x, G + 1, z, x == -17 || x == -13 ? "minecraft:barrel[facing=up,open=false]" : "minecraft:polished_andesite");
            set(x, G + 2, z, slab("minecraft:smooth_stone_slab", "bottom"));
        }
        set(-15, G + 2, -11, "minecraft:cake");
        set(-16, G + 2, -10, "minecraft:potted_red_tulip");
        // etageres a tonneaux et caisses contre le mur ouest
        for (int z = -13; z <= -6; z++) {
            if (z == -8) continue;
            set(IX1, G + 1, z, "minecraft:barrel[facing=east,open=false]");
            if (Math.floorMod(z, 2) == 0) set(IX1, G + 2, z, "minecraft:barrel[facing=up,open=false]");
        }
        crates(-11, G + 1, -14, 2);
        hangLamp(-15, U, -13, "minecraft:lantern");
        hangLamp(-15, U, -7, "minecraft:lantern");
        hangLamp(-11, U, -8, "minecraft:lantern");
    }

    private void office() {
        // bureau du directeur : table en escaliers, fauteuil, bibliotheques, coffre-fort, globe, tapis vert
        rug(-7, -14, -2, -9, G + 1, "minecraft:yellow_carpet", "minecraft:green_carpet");
        for (int x = -5; x <= -3; x++) {
            set(x, G + 1, -12, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, true));
        }
        set(-4, G + 2, -12, "minecraft:lectern[facing=south]");
        set(-3, G + 2, -12, candles(3, "white"));
        armchair(-4, G + 1, -13, Dir.SOUTH, "dark_oak");
        for (int x = -8; x <= -1; x++) {
            if (x == -5) continue;
            set(x, G + 1, IZ1, "minecraft:bookshelf");
            set(x, G + 2, IZ1, "minecraft:bookshelf");
        }
        set(-1, G + 1, -13, "minecraft:iron_block");
        set(-1, G + 2, -13, "minecraft:iron_trapdoor[facing=west,half=top,open=false]");
        set(-7, G + 1, -9, "minecraft:dark_oak_fence");
        set(-7, G + 2, -9, "minecraft:light_blue_stained_glass");
        armchair(-2, G + 1, -9, Dir.WEST, "dark_oak");
        hangLamp(-4, U, -10, "minecraft:lantern");
    }

    private void staffRoom() {
        // salle du personnel : vestiaires, table des repas, porte-manteaux
        for (int x = 2; x <= 7; x++) {
            set(x, G + 1, IZ1, "minecraft:iron_block");
            set(x, G + 2, IZ1, "minecraft:iron_trapdoor[facing=south,half=bottom,open=false]");
        }
        for (int x = 3; x <= 6; x++) {
            set(x, G + 1, -11, "minecraft:spruce_fence");
            set(x, G + 2, -11, "minecraft:spruce_pressure_plate");
            setIfAir(x, G + 1, -12, stairs("minecraft:spruce_stairs", Dir.NORTH, false));
            setIfAir(x, G + 1, -10, stairs("minecraft:spruce_stairs", Dir.SOUTH, false));
        }
        set(1, G + 1, -9, "minecraft:barrel[facing=up,open=false]");
        set(8, G + 1, -14, "minecraft:barrel[facing=up,open=false]");
        set(8, G + 2, -14, "minecraft:white_wool");
        hangLamp(4, U, -12, "minecraft:lantern");
        hangLamp(4, U, -9, "minecraft:lantern");
    }

    private void laundry() {
        // cuves de lavage, metiers (planches a repasser), draps empiles, paniers, cordes a linge
        for (int x = 10; x <= 16; x++) {
            if (Math.abs(x - 13) <= 1) continue;
            set(x, G + 1, IZ1, x % 2 == 0 ? "minecraft:water_cauldron[level=2]" : "minecraft:cauldron");
        }
        set(10, G + 1, -9, "minecraft:loom[facing=east]");
        set(10, G + 1, -10, "minecraft:smithing_table");
        for (int z = -14; z <= -13; z++) {
            set(10, G + 1, z, "minecraft:white_wool");
            set(10, G + 2, z, z == -14 ? "minecraft:light_blue_wool" : "minecraft:white_wool");
        }
        crates(15, G + 1, -6, 2);
        // corde a linge : poteaux et draps (laine blanche en tranches = tapis tendus sur une chaine)
        for (int x : new int[]{12, 17}) for (int y = G + 1; y <= G + 3; y++) set(x, y, -9, "minecraft:spruce_fence");
        for (int x = 13; x <= 16; x++) {
            set(x, G + 3, -9, chain('x'));
            if (x % 2 == 1) set(x, G + 2, -9, "minecraft:white_wool"); // drap qui seche
        }
        hangLamp(13, U, -12, "minecraft:lantern");
        hangLamp(15, U, -6, "minecraft:lantern");
        hangLamp(11, U, -6, "minecraft:lantern");
    }

    private void bar() {
        // comptoir le long du mur nord (le passage vers la buanderie reste libre), etageres a bouteilles
        for (int x = 15; x <= 19; x++) {
            // comptoir bas (1 de haut) : le barman reste visible depuis la salle
            set(x, G + 1, -2, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
            set(x, G + 1, -3, B.AIR);
            set(x, G + 2, -3, B.AIR);
            set(x, G + 3, -3, slab("minecraft:dark_oak_slab", "top"));
            set(x, G + 4, -3, candles(1 + (x % 3), x % 2 == 0 ? "green" : "brown"));
            if (x >= 16 && x <= 18) setIfAir(x, G + 1, -1, "minecraft:dark_oak_fence");
            if (x >= 16 && isAir(x, G + 2, -1) && get(x, G + 1, -1).contains("fence")) set(x, G + 2, -1, "minecraft:red_carpet");
        }
        set(18, G + 2, -2, "minecraft:brewing_stand");
        set(16, G + 2, -2, candles(2, "yellow"));
        // le bout est derriere le comptoir reste ouvert (pas de cul-de-sac)
        for (int y = G + 1; y <= U - 1; y++) for (int z = -3; z <= -2; z++) if (y <= G + 2 || !B.name(get(20, y, z)).endsWith("slab")) set(20, y, z, B.AIR);
        set(20, G + 3, -3, slab("minecraft:dark_oak_slab", "top"));
        // salon : fauteuils clubs, tables basses, piano a queue (noir) et estrade
        armchair(12, G + 1, 3, Dir.EAST, "dark_oak");
        armchair(14, G + 1, 3, Dir.WEST, "dark_oak");
        cafeTable(13, G + 1, 4, "dark_oak", false);
        armchair(12, G + 1, 6, Dir.EAST, "dark_oak");
        cafeTable(13, G + 1, 6, "dark_oak", false);
        rug(11, 2, 15, 7, G + 1, "minecraft:yellow_carpet", "minecraft:red_carpet");
        for (int x = 17; x <= 20; x++) for (int z = 5; z <= IZ2; z++) set(x, G, z, "minecraft:polished_blackstone");
        for (int x = 18; x <= 19; x++) {
            set(x, G + 1, 6, "minecraft:black_concrete");
            set(x, G + 2, 6, slab("minecraft:polished_blackstone_slab", "bottom"));
        }
        set(20, G + 1, 6, "minecraft:note_block");
        set(20, G + 2, 6, slab("minecraft:polished_blackstone_slab", "bottom"));
        set(18, G + 1, 5, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
        bigPlant(11, G + 1, -2, "dark_oak");
        hangLamp(13, U, 1, "minecraft:lantern");
        hangLamp(18, U, 3, "minecraft:lantern");
        hangLamp(17, U, -1, "minecraft:lantern");
    }

    // ================================================================ etage

    private void gallery() {
        // banquettes et plantes le long des murs de la galerie, tapis
        for (int z = 1; z <= 4; z++) {
            set(-8, U + 1, z, stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
            set(8, U + 1, z, stairs("minecraft:dark_oak_stairs", Dir.EAST, false));
        }
        set(-8, U + 1, 6, "minecraft:moss_block");
        set(-8, U + 2, 6, "minecraft:azalea");
        set(8, U + 1, 6, "minecraft:moss_block");
        set(8, U + 2, 6, "minecraft:flowering_azalea");
        for (int x = -4; x <= 4; x++) setIfAir(x, U + 1, 7, x % 2 == 0 ? "minecraft:yellow_carpet" : "minecraft:black_carpet");
        // console et vase entre les portes nord
        set(0, U + 1, -6, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
        set(0, U + 2, -6, "minecraft:decorated_pot");
        set(-1, U + 1, -6, "minecraft:potted_blue_orchid");
    }

    private void corridor() {
        // tapis rouge a bordure doree sur toute la longueur, consoles et appliques
        for (int x = IX1; x <= IX2; x++) {
            if (x >= -8 && x <= 8) continue;
            for (int z = -3; z <= -1; z++) setIfAir(x, U + 1, z, z == -2 ? "minecraft:red_carpet" : "minecraft:yellow_carpet");
        }
        set(IX1, U + 1, -2, stairs("minecraft:dark_oak_stairs", Dir.WEST, true));
        set(IX1, U + 2, -2, "minecraft:potted_fern");
        set(IX2, U + 1, -2, stairs("minecraft:dark_oak_stairs", Dir.EAST, true));
        set(IX2, U + 2, -2, "minecraft:potted_azalea_bush");
    }

    private void rooms() {
        // chambres sud : lit double, tables de nuit, armoire, bureau, tapis ; numeros dores au-dessus des portes
        room(IX1, -16, "red", true);
        room(-14, -10, "blue", false);
        room(10, 14, "green", true);
        room(16, IX2, "purple", false);
        // chambres nord du centre
        northRoom(-8, -1, "cyan");
        northRoom(1, 8, "orange");
    }

    private void room(int x1, int x2, String color, boolean bedWest) {
        int bx = bedWest ? x1 : x2 - 1;
        // lit double tete contre le mur sud (z 7 = cote fenetre ? non : tete au nord contre la cloison du couloir)
        bed(bx, U + 1, 2, Dir.NORTH, color);
        bed(bx + 1, U + 1, 2, Dir.NORTH, color);
        // commode et lampe de chevet (pas devant la porte, au milieu de la cloison)
        int nt = bedWest ? x1 + 3 : x1 + 1;
        set(nt, U + 1, 1, "minecraft:spruce_planks");
        set(nt, U + 2, 1, standing("minecraft:lantern"));
        rug(x1, 3, x2, 5, U + 1, "minecraft:" + color + "_carpet", "minecraft:white_carpet");
        // armoire (2 de haut) dans l'angle cote fenetre
        int ax = bedWest ? x2 : x1;
        set(ax, U + 1, 6, "minecraft:stripped_dark_oak_wood[axis=y]");
        set(ax, U + 2, 6, "minecraft:stripped_dark_oak_wood[axis=y]");
        // coiffeuse
        int cx = bedWest ? x2 : x1;
        set(cx, U + 1, 3, stairs("minecraft:dark_oak_stairs", bedWest ? Dir.EAST : Dir.WEST, true));
        set(cx, U + 2, 3, "minecraft:potted_" + (color.equals("red") ? "poppy" : "cornflower"));
        // plafonnier
        hangLamp((x1 + x2) / 2, R, 4, "minecraft:lantern");
    }

    private void northRoom(int x1, int x2, String color) {
        bed(x1, U + 1, -14, Dir.NORTH, color);
        bed(x1 + 1, U + 1, -14, Dir.NORTH, color);
        set(x1 + 2, U + 1, -15, "minecraft:spruce_planks");
        set(x1 + 2, U + 2, -15, standing("minecraft:lantern"));
        rug(x1 + 1, -12, x2 - 1, -9, U + 1, "minecraft:" + color + "_carpet", "minecraft:light_gray_carpet");
        set(x2, U + 1, -15, "minecraft:stripped_dark_oak_wood[axis=y]");
        set(x2, U + 2, -15, "minecraft:stripped_dark_oak_wood[axis=y]");
        armchair(x2, U + 1, -9, Dir.NORTH, "dark_oak");
        hangLamp((x1 + x2) / 2, R, -11, "minecraft:lantern");
    }

    private void bed(int x, int y, int zFoot, Dir head, String color) {
        set(x, y, zFoot, "minecraft:" + color + "_bed[facing=" + head.id() + ",part=foot,occupied=false]");
        set(x + head.dx, y, zFoot + head.dz, "minecraft:" + color + "_bed[facing=" + head.id() + ",part=head,occupied=false]");
    }

    private void suite() {
        // chambre : grand lit a baldaquin, coiffeuse, malle
        bed(-19, U + 1, -11, Dir.NORTH, "white");
        bed(-18, U + 1, -11, Dir.NORTH, "white");
        for (int[] p : new int[][]{{-20, -13}, {-17, -13}, {-20, -10}, {-17, -10}})
            for (int y = U + 1; y <= U + 3; y++) set(p[0], y, p[1], y == U + 3 ? "minecraft:dark_oak_planks" : "minecraft:dark_oak_fence");
        for (int x = -20; x <= -17; x++) for (int z = -13; z <= -10; z++) set(x, U + 4, z, x == -20 || x == -17 || z == -13 || z == -10 ? slab("minecraft:dark_oak_slab", "bottom") : "minecraft:red_wool");
        rug(-20, -8, -16, -5, U + 1, "minecraft:yellow_carpet", "minecraft:white_carpet");
        set(-16, U + 1, -15, "minecraft:barrel[facing=up,open=false]");
        set(-20, U + 1, -15, "minecraft:stripped_dark_oak_wood[axis=y]");
        set(-20, U + 2, -15, "minecraft:stripped_dark_oak_wood[axis=y]");
        // salon : canape, fauteuils, table basse, gramophone (jukebox), bar roulant
        bench(-12, U + 1, -6, Dir.NORTH, "dark_oak");
        armchair(-14, U + 1, -8, Dir.EAST, "dark_oak");
        cafeTable(-12, U + 1, -8, "dark_oak", false);
        set(-10, U + 1, -9, "minecraft:jukebox");
        set(-10, U + 2, -9, candles(3, "white"));
        // salle de bains : baignoire, lavabo, miroir (verre), carrelage
        for (int x = -14; x <= -13; x++) {
            set(x, U + 1, IZ1, "minecraft:water_cauldron[level=3]");
        }
        set(-10, U + 1, IZ1, "minecraft:quartz_block");
        set(-10, U + 2, IZ1, "minecraft:light_blue_stained_glass");
        set(-11, U + 1, IZ1, "minecraft:cauldron");
        set(-10, U + 1, -12, "minecraft:potted_fern");
        hangLamp(-18, R, -7, "minecraft:lantern");
        hangLamp(-12, R, -7, "minecraft:lantern");
        hangLamp(-12, R, -13, "minecraft:lantern");
    }

    private void linenRoom() {
        // office d'etage : etageres de draps, chariots de menage, monte-charge
        for (int z = -14; z <= -12; z++) {
            set(10, U + 1, z, "minecraft:white_wool");
            set(10, U + 2, z, z == -13 ? "minecraft:light_blue_wool" : "minecraft:white_wool");
            set(10, U + 3, z, slab("minecraft:spruce_slab", "top"));
        }
        for (int x = 12; x <= 16; x++) {
            if (x == 14) continue;
            set(x, U + 1, IZ1, "minecraft:barrel[facing=south,open=false]");
        }
        set(13, U + 1, -10, "minecraft:spruce_trapdoor[facing=north,half=top,open=false]");
        set(14, U + 1, -10, "minecraft:white_wool");
        hangLamp(13, R, -12, "minecraft:lantern");
        hangLamp(15, R, -7, "minecraft:lantern");
    }

    // ================================================================ toit, attique et lettres lumineuses

    private void roof() {
        // acrotere au-dessus de la corniche, couvertine, toit-terrasse technique (non accessible)
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                boolean edge = x == X1 || x == X2 || z == Z1 || z == Z2;
                boolean edge2 = x == X1 + 1 || x == X2 - 1 || z == Z1 + 1 || z == Z2 - 1;
                if (edge) {
                    set(x, R + 1, z, WALL.at(x, R + 1, z));
                    set(x, R + 2, z, slab("minecraft:smooth_quartz_slab", "bottom"));
                } else if (edge2) {
                    set(x, R, z, WALL.at(x, R, z));
                    set(x, R + 1, z, WALL.at(x, R + 1, z));
                }
            }
        // couverture du toit plat (gravillons / dalles) au-dessus du plafond de l'etage
        for (int x = X1 + 2; x <= X2 - 2; x++)
            for (int z = Z1 + 2; z <= Z2 - 2; z++) set(x, R + 1, z, Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0 ? "minecraft:smooth_stone_slab[type=bottom]" : TILE.at(x, R + 1, z));
        // piliers de la facade prolonges au-dessus de l'acrotere (rythme vertical Art deco)
        for (int x = X1 + 1; x <= X2 - 1; x++)
            for (int z : new int[]{Z1, Z2}) {
                if (B.name(get(x, R - 1, z)).equals("quartz_pillar")) pierTop(x, z);
            }
        for (int z = Z1 + 1; z <= Z2 - 1; z++)
            for (int x : new int[]{X1, X2}) if (B.name(get(x, R - 1, z)).equals("quartz_pillar")) pierTop(x, z);
        // attique au-dessus du hall : 2 gradins + lanterne, nervures, lettres « HOTEL » en froglights au sud
        int ax1 = -10, ax2 = 10, az1 = -9, az2 = 7;
        for (int x = ax1; x <= ax2; x++)
            for (int z = az1; z <= az2; z++) {
                boolean e = x == ax1 || x == ax2 || z == az1 || z == az2;
                for (int y = R + 1; y <= R + 7; y++) if (e) set(x, y, z, ((x + z) & 1) == 0 && y < R + 7 ? "minecraft:quartz_pillar[axis=y]" : WALL.at(x, y, z));
                set(x, R + 8, z, e ? stairs("minecraft:smooth_quartz_stairs", e && z == az2 ? Dir.NORTH : (z == az1 ? Dir.SOUTH : (x == ax1 ? Dir.EAST : Dir.WEST)), false) : "minecraft:smooth_quartz");
            }
        String[] letters = {"H", "O", "T", "E", "L"};
        String[][] font = {
                {"101", "101", "111", "101", "101"},
                {"111", "101", "101", "101", "111"},
                {"111", "010", "010", "010", "010"},
                {"111", "100", "110", "100", "111"},
                {"100", "100", "100", "100", "111"},
        };
        int lx = -9;
        for (int li = 0; li < letters.length; li++) {
            for (int row = 0; row < 5; row++)
                for (int col = 0; col < 3; col++) {
                    int x = lx + li * 4 + col, y = R + 6 - row;
                    set(x, y, az2, font[li][row].charAt(col) == '1' ? "minecraft:ochre_froglight" : "minecraft:polished_blackstone");
                }
            if (li < letters.length - 1) for (int row = 0; row < 5; row++) set(lx + li * 4 + 3, R + 6 - row, az2, "minecraft:polished_blackstone");
        }
        // 2e gradin et lanterne a ailettes
        int bx1 = -6, bx2 = 6, bz1 = -6, bz2 = 3;
        for (int x = bx1; x <= bx2; x++)
            for (int z = bz1; z <= bz2; z++) {
                boolean e = x == bx1 || x == bx2 || z == bz1 || z == bz2;
                for (int y = R + 9; y <= R + 12; y++) if (e) set(x, y, z, (Math.floorMod(x, 3) == 0 || Math.floorMod(z, 3) == 0) ? "minecraft:smooth_quartz" : "minecraft:yellow_stained_glass");
                set(x, R + 13, z, e ? slab("minecraft:smooth_quartz_slab", "bottom") : "minecraft:smooth_quartz");
            }
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 0; z++) set(x, R + 9, z, "minecraft:ochre_froglight");
        for (int y = R + 13; y <= R + 17; y++) {
            int r = y <= R + 15 ? 1 : 0;
            for (int x = -r; x <= r; x++) for (int z = -1 - r; z <= -1 + r; z++) set(x, y, z, y == R + 17 ? GOLD_B : (Math.abs(x) + Math.abs(z + 1) == 2 ? "minecraft:gold_block" : "minecraft:smooth_quartz"));
        }
        for (int y = R + 18; y <= R + 20; y++) set(0, y, -1, y == R + 20 ? "minecraft:lightning_rod[facing=up]" : "minecraft:end_rod[facing=up]");
        // elements techniques du toit plat : souches de cheminee, reservoir d'eau
        Roof.chimney(s, -19, -14, -18, -13, R + 2, R + 5, Mat.of("minecraft:bricks"), "minecraft:brick_slab");
        Roof.chimney(s, 17, -14, 18, -13, R + 2, R + 5, Mat.of("minecraft:bricks"), "minecraft:brick_slab");
        fr.eemicraft.murder.build.Shapes.cylinder(s, 16, 2, R + 3, R + 6, 1.6, Mat.of("minecraft:spruce_planks"), true);
        for (int[] l : new int[][]{{15, 1}, {17, 1}, {15, 3}, {17, 3}}) { set(l[0], R + 2, l[1], "minecraft:spruce_fence"); }
        for (int x = 15; x <= 17; x++) for (int z = 1; z <= 3; z++) set(x, R + 7, z, slab("minecraft:spruce_slab", "bottom"));
    }

    private void pierTop(int x, int z) {
        set(x, R + 1, z, "minecraft:quartz_pillar[axis=y]");
        set(x, R + 2, z, "minecraft:chiseled_quartz_block");
        set(x, R + 3, z, slab("minecraft:smooth_quartz_slab", "bottom"));
    }

    // ================================================================ dehors

    private void forecourt() {
        // allee en boucle autour de la fontaine, jusqu'au portail sud
        int cx = 0, cz = 21;
        for (int x = BX1 + 1; x <= BX2 - 1; x++)
            for (int z = Z2 + 4; z <= BZ2; z++) {
                double e = Math.hypot((x - cx) / 1.35, z - cz);
                boolean drive = (e >= 4.4 && e <= 7.4) || (Math.abs(x) <= 3 && z >= cz + 6) || (Math.abs(x) <= 6 && z <= Z2 + 7);
                if (drive) set(x, G, z, GRAVEL.at(x, G, z));
            }
        for (int x = -6; x <= 6; x++) for (int z = Z2 + 3; z <= 16; z++) set(x, G, z, PAVE.at(x, G, z));
        // fontaine Art deco : bassin octogonal, vasque, jets (lanternes marines sous l'eau)
        for (int x = cx - 4; x <= cx + 4; x++)
            for (int z = cz - 4; z <= cz + 4; z++) {
                double d = Math.max(Math.abs(x - cx), Math.abs(z - cz)) * 0.62 + (Math.abs(x - cx) + Math.abs(z - cz)) * 0.38;
                if (d <= 2.6) { set(x, G - 1, z, (x + z) % 3 == 0 ? "minecraft:sea_lantern" : "minecraft:prismarine_bricks"); set(x, G, z, B.WATER); }
                else if (d <= 3.6) { set(x, G, z, "minecraft:polished_blackstone"); set(x, G + 1, z, slab("minecraft:smooth_quartz_slab", "bottom")); }
                else if (d <= 4.4) set(x, G, z, GRASS.at(x, G, z));
            }
        for (int y = G; y <= G + 2; y++) set(cx, y, cz, "minecraft:polished_blackstone_bricks");
        for (Dir d : Dir.values()) set(cx + d.dx, G + 2, cz + d.dz, "minecraft:water_cauldron[level=3]");
        Sdf.ellipsoid(cx, G + 4.0, cz, 0.6, 1.2, 0.6).union(Sdf.sphere(cx, G + 5.5, cz, 0.5)).fill(s, Mat.of(GOLD_B));
        // pelouses : parterres bordes de buis bas, rosiers, ifs en cone, arbres
        for (int sx : new int[]{-1, 1}) {
            int x1 = sx < 0 ? -30 : 14, x2 = sx < 0 ? -14 : 30;
            for (int x = x1; x <= x2; x++)
                for (int z = 15; z <= 27; z++) {
                    if (!get(x, G, z).contains("grass") && !get(x, G, z).contains("moss") && !get(x, G, z).contains("podzol") && !get(x, G, z).contains("coarse")) continue;
                    boolean edge = x == x1 || x == x2 || z == 15 || z == 27;
                    boolean gap = z == 21 || x == (x1 + x2) / 2;
                    if (edge && !gap) set(x, G + 1, z, HEDGE.at(x, G + 1, z));
                    else if (!edge && h(x, 2, z) < 0.22) setIfAir(x, G + 1, z, h(x, 3, z) < 0.5 ? "minecraft:red_tulip" : "minecraft:white_tulip");
                }
            for (int[] t : new int[][]{{sx * 18, 18}, {sx * 26, 24}}) {
                Sdf.cone(t[0], t[1], G + 1, 5, 1.6, 0.2).fill(s, Mat.of("minecraft:spruce_leaves[persistent=true]"));
                set(t[0], G + 1, t[1], log("minecraft:spruce_log", 'y'));
            }
            new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(7).crown(2.7).place(s, sx * 22, G + 1, 21, sx * 41L + 3);
            bench(sx * 16, G + 1, 21, sx < 0 ? Dir.EAST : Dir.WEST, "spruce");
        }
        // voiture d'epoque garee sur l'allee (noire, calandre doree)
        car(8, 18, Dir.SOUTH, "black");
        // lampadaires a 4 lanternes autour de la boucle
        for (int[] l : new int[][]{{-9, 15}, {9, 15}, {-9, 27}, {9, 27}, {-4, 28}, {4, 28}})
            lampPost(l[0], G + 1, l[1], 4, "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone", true);
    }

    /** Voiture des annees 1920 (5 de long) : capot, habitacle ouvert, garde-boue, roues. */
    private void car(int x, int z, Dir dir, String color) {
        Dir side = dir.cw();
        String body = "minecraft:" + color + "_concrete";
        for (int i = 0; i < 5; i++)
            for (int j = -1; j <= 1; j++) {
                int px = x + dir.dx * i + side.dx * j, pz = z + dir.dz * i + side.dz * j;
                boolean wheel = (i == 0 || i == 4) && j != 0;
                if (wheel) { set(px, G + 1, pz, "minecraft:polished_blackstone"); continue; }
                set(px, G + 1, pz, j == 0 && (i == 0 || i == 4) ? "minecraft:polished_blackstone_slab[type=bottom]" : body);
                if (i == 3 || i == 4) set(px, G + 2, pz, i == 4 && j == 0 ? GOLD_B : slab("minecraft:" + (color.equals("black") ? "polished_blackstone" : "dark_oak") + "_slab", "bottom"));
                if (i == 1 || i == 2) set(px, G + 2, pz, j == 0 ? B.AIR : stairs("minecraft:dark_oak_stairs", j < 0 ? side : side.opposite(), false));
            }
        int fx = x + dir.dx * 2, fz = z + dir.dz * 2;
        set(fx, G + 2, fz, stairs("minecraft:spruce_stairs", dir.opposite(), false));
        set(x + dir.dx * 3, G + 3, z + dir.dz * 3, "minecraft:glass_pane");
        set(x + dir.dx * 5, G + 1, z + dir.dz * 5, B.AIR);
    }

    private void terrace() {
        // terrasse du restaurant (dallee), tables a parasols, pergola de glycine (feuilles d'azalee sur poutres)
        for (int x = BX1 + 1; x <= X1 - 1; x++)
            for (int z = -10; z <= 8; z++) set(x, G, z, PAVE.at(x, G, z));
        for (int[] t : new int[][]{{-30, -6}, {-30, 1}, {-27, 5}}) parasol(t[0], t[1], (t[0] + t[1]) % 2 == 0 ? "red" : "green");
        for (int x = -33; x <= -25; x++)
            for (int z = -9; z <= -8; z++) {
                if ((x == -33 || x == -25) && (z == -9)) for (int y = G + 1; y <= G + 3; y++) set(x, y, z, "minecraft:spruce_fence");
                set(x, G + 4, z, z == -9 ? log("minecraft:stripped_spruce_log", 'x') : HEDGE.at(x, G + 4, z));
            }
        for (int x = -32; x <= -26; x++) if (h(x, 9, -9) < 0.4) set(x, G + 3, -8, "minecraft:azalea_leaves[persistent=true]");
        bench(-29, G + 1, -9, Dir.SOUTH, "spruce");
        // pelouse et bosquet au nord-ouest
        for (int[] t : new int[][]{{-29, -16}, {-31, -24}, {-25, -21}}) {
            new Tree("minecraft:birch_log", "minecraft:birch_leaves", "minecraft:azalea_leaves").height(7).crown(2.6).place(s, t[0], G + 1, t[1], 300L + t[1]);
        }
        lampPost(-26, G + 1, -3, 4, "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone", true);
        lampPost(-31, G + 1, 7, 4, "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone", false);
    }

    private void parasol(int x, int z, String color) {
        set(x, G + 1, z, "minecraft:dark_oak_fence");
        set(x, G + 2, z, "minecraft:dark_oak_fence");
        set(x, G + 3, z, "minecraft:dark_oak_fence");
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) set(x + dx, G + 4, z + dz, (dx == 0 && dz == 0) || (dx + dz) % 2 == 0 ? "minecraft:white_wool" : "minecraft:" + color + "_wool");
        set(x, G + 5, z, "minecraft:" + color + "_carpet");
        for (Dir d : Dir.values()) setIfAir(x + d.dx, G + 1, z + d.dz, stairs("minecraft:dark_oak_stairs", d, false));
    }

    private void eastGarden() {
        // jardin a la francaise : allees de gravier en croix, parterres de buis, kiosque a musique au centre
        int cx = 28, cz = -2;
        for (int x = X2 + 2; x <= BX2 - 1; x++)
            for (int z = Z1 - 1; z <= 12; z++) {
                boolean path = Math.abs(z - cz) <= 1 || Math.abs(x - cx) <= 1 || z == Z1 - 1 || z == 12 || x == X2 + 2;
                if (path) set(x, G, z, GRAVEL.at(x, G, z));
            }
        for (int x = X2 + 3; x <= BX2 - 2; x++)
            for (int z = Z1; z <= 11; z++) {
                if (get(x, G, z).contains("gravel") || get(x, G, z).contains("coarse_dirt") || get(x, G, z).contains("packed_mud")) continue;
                boolean border = false;
                for (Dir d : Dir.values()) { String n = get(x + d.dx, G, z + d.dz); if (n.contains("gravel") || n.contains("coarse_dirt") || n.contains("packed_mud")) border = true; }
                if (border) set(x, G + 1, z, HEDGE.at(x, G + 1, z));
                else if (h(x, 7, z) < 0.25) setIfAir(x, G + 1, z, h(x, 8, z) < 0.5 ? "minecraft:allium" : "minecraft:lily_of_the_valley");
            }
        // kiosque : socle octogonal, colonnes, toit de cuivre vert-de-gris
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++) {
                if (Math.abs(x - cx) + Math.abs(z - cz) > 5) continue;
                set(x, G, z, "minecraft:polished_andesite");
                set(x, G + 1, z, B.AIR);
            }
        for (int[] p : new int[][]{{-3, -1}, {-3, 1}, {3, -1}, {3, 1}, {-1, -3}, {1, -3}, {-1, 3}, {1, 3}})
            for (int y = G + 1; y <= G + 3; y++) set(cx + p[0], y, cz + p[1], "minecraft:birch_fence");
        RoofStyle rs = new RoofStyle().stairs("minecraft:waxed_oxidized_cut_copper_stairs").slab("minecraft:waxed_oxidized_cut_copper_slab")
                .ridge("minecraft:waxed_oxidized_cut_copper_slab").body(Mat.of("minecraft:waxed_oxidized_cut_copper")).gable(Mat.of("minecraft:waxed_oxidized_cut_copper")).overhang(1);
        Roof.build(s, rs, Roof.hip(cx - 3, cz - 3, cx + 3, cz + 3, G + 4));
        set(cx, G + 3, cz, hanging("minecraft:lantern"));
        for (int y = G + 4; y <= G + 9 && isAir(cx, y, cz); y++) set(cx, y, cz, chain('y'));
        set(cx - 1, G + 1, cz, "minecraft:note_block");
        set(cx + 1, G + 1, cz, stairs("minecraft:birch_stairs", Dir.EAST, false));
        bench(31, G + 1, 9, Dir.NORTH, "spruce");
        bench(31, G + 1, -14, Dir.SOUTH, "spruce");
        new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(8).crown(3.0).place(s, 31, G + 1, 4, 911L);
        new Tree("minecraft:oak_log", "minecraft:oak_leaves", null).height(7).crown(2.6).place(s, 25, G + 1, -11, 913L);
        lampPost(X2 + 2, G + 1, -9, 4, "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone", false);
        lampPost(X2 + 2, G + 1, 7, 4, "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone", false);
    }

    private void serviceCourt() {
        for (int x = BX1 + 1; x <= BX2 - 1; x++) for (int z = BZ1 + 1; z <= Z1 - 3; z++) set(x, G, z, ASPHALT.at(x, G, z));
        // camionnette de livraison (blanchisserie)
        truck(-8, -24);
        // caisses de legumes, tonneaux, poubelles, chariot de linge
        for (int[] c : new int[][]{{-16, -21}, {-15, -21}, {-16, -22}, {10, -21}, {11, -21}}) set(c[0], G + 1, c[1], "minecraft:barrel[facing=up,open=false]");
        for (int[] c : new int[][]{{-18, -22}, {-17, -23}}) { set(c[0], G + 1, c[1], slab("minecraft:spruce_slab", "bottom")); set(c[0], G + 2, c[1], "minecraft:stripped_spruce_wood[axis=x]"); }
        for (int x = 15; x <= 17; x++) { set(x, G + 1, -27, "minecraft:green_terracotta"); set(x, G + 2, -27, "minecraft:iron_trapdoor[facing=north,half=bottom,open=false]"); }
        // corde a linge de la blanchisserie
        for (int x : new int[]{4, 12}) for (int y = G + 1; y <= G + 3; y++) set(x, y, -25, "minecraft:spruce_fence");
        for (int x = 5; x <= 11; x++) {
            set(x, G + 3, -25, chain('x'));
            if (x % 2 == 1) set(x, G + 2, -25, x % 4 == 1 ? "minecraft:white_wool" : "minecraft:light_blue_wool");
        }
        // garage ouvert (ouest) avec une 2e voiture
        int gx1 = BX1 + 1, gx2 = BX1 + 9, gz1 = BZ1 + 1, gz2 = BZ1 + 8;
        for (int x = gx1; x <= gx2; x++)
            for (int z = gz1; z <= gz2; z++) {
                boolean wall = x == gx1 || z == gz1 || z == gz2;
                for (int y = G + 1; y <= G + 4; y++) if (wall) set(x, y, z, y == G + 1 ? PLINTH.at(x, y, z) : TOWN.at(x, y, z));
                set(x, G, z, "minecraft:polished_andesite");
            }
        RoofStyle grs = new RoofStyle().stairs("minecraft:deepslate_tile_stairs").slab("minecraft:deepslate_tile_slab")
                .ridge("minecraft:deepslate_tile_slab").body(Mat.of("minecraft:deepslate_tiles")).gable(TOWN).gablePost(null).overhang(1);
        Roof.build(s, grs, Roof.gableX(gx1, gz1, gx2, gz2, G + 5));
        for (int z = gz1 + 1; z <= gz2 - 1; z++) for (int y = G + 1; y <= G + 4; y++) set(gx2, y, z, y == G + 4 ? log("minecraft:stripped_spruce_log", 'z') : B.AIR);
        set(gx2, G + 1, gz1, PLINTH.at(gx2, G + 1, gz1));
        car(gx1 + 2, gz1 + 3, Dir.EAST, "red");
        set(gx1 + 1, G + 1, gz1 + 1, "minecraft:barrel[facing=up,open=false]");
        set(gx1 + 1, G + 2, gz1 + 1, standing("minecraft:lantern"));
        for (int[] l : new int[][]{{-26, -18}, {0, -27}, {26, -18}}) lampPost(l[0], G + 1, l[1], 4, "minecraft:iron_bars", "minecraft:polished_blackstone_wall", false);
        // quai de dechargement devant la porte des cuisines
        for (int x = -17; x <= -11; x++) set(x, G, Z1 - 1, x == -14 ? get(x, G, Z1 - 1) : "minecraft:yellow_concrete");
    }

    private void truck(int x, int z) {
        for (int i = 0; i < 6; i++)
            for (int j = -1; j <= 1; j++)
                for (int y = G + 2; y <= G + 4; y++) set(x + i, y, z + j, y == G + 4 ? "minecraft:light_gray_concrete" : "minecraft:white_concrete");
        for (int i = 6; i <= 8; i++)
            for (int j = -1; j <= 1; j++) {
                set(x + i, G + 2, z + j, "minecraft:blue_concrete");
                set(x + i, G + 3, z + j, i == 8 ? "minecraft:glass" : (Math.abs(j) == 1 ? "minecraft:glass_pane" : B.AIR));
                if (i <= 7) set(x + i, G + 4, z + j, "minecraft:blue_concrete");
            }
        set(x + 7, G + 3, z, stairs("minecraft:spruce_stairs", Dir.WEST, false));
        for (int i : new int[]{1, 4, 7}) for (int j : new int[]{-1, 1}) set(x + i, G + 1, z + j, "minecraft:polished_blackstone");
    }

    // ================================================================ cloture

    private void fence() {
        for (int t = Math.min(BX1, BZ1); t <= Math.max(BX2, BZ2); t++) {
            int[][] cells = {{t, BZ1}, {t, BZ2}, {BX1, t}, {BX2, t}};
            for (int[] c : cells) {
                int x = c[0], z = c[1];
                if (x < BX1 || x > BX2 || z < BZ1 || z > BZ2) continue;
                boolean gate = (z == BZ2 && Math.abs(x) <= 3) || (z == BZ1 && Math.abs(x) <= 3);
                boolean pillar = Math.floorMod(t, 6) == 0 || ((x == BX1 || x == BX2) && (z == BZ1 || z == BZ2));
                if (pillar) {
                    for (int y = G + 1; y <= G + 3; y++) set(x, y, z, PLINTH.at(x, y, z));
                    set(x, G + 4, z, "minecraft:chiseled_quartz_block");
                    set(x, G + 5, z, standing("minecraft:lantern"));
                } else {
                    set(x, G + 1, z, gate ? "minecraft:iron_bars" : "minecraft:polished_blackstone_wall");
                    set(x, G + 2, z, "minecraft:iron_bars");
                    set(x, G + 3, z, "minecraft:iron_bars");
                    if (gate) set(x, G + 4, z, "minecraft:iron_bars");
                }
                for (int y = G + 6; y <= MAX_Y; y++) if (isAir(x, y, z)) set(x, y, z, "minecraft:barrier");
                if (!pillar) for (int y = G + 4 + (gate ? 1 : 0); y <= G + 5; y++) if (isAir(x, y, z)) set(x, y, z, "minecraft:barrier");
            }
        }
        for (int sx : new int[]{-4, 4})
            for (int z : new int[]{BZ1, BZ2}) {
                for (int y = G + 1; y <= G + 4; y++) set(sx, y, z, PLINTH.at(sx, y, z));
                set(sx, G + 5, z, GOLD_B);
                set(sx, G + 6, z, standing("minecraft:lantern"));
            }
        // haie le long de la cloture (cote interieur), hors allees
        for (int t = BX1 + 1; t <= BX2 - 1; t++) if (Math.abs(t) > 4) hedge(t, BZ2 - 1);
        for (int t = BZ1 + 1; t <= BZ2 - 1; t++) { hedge(BX1 + 1, t); hedge(BX2 - 1, t); }
    }

    private void hedge(int x, int z) {
        if (!isAir(x, G + 1, z)) return;
        String g = get(x, G, z);
        if (!g.contains("grass") && !g.contains("moss") && !g.contains("podzol") && !g.contains("coarse")) return;
        set(x, G + 1, z, HEDGE.at(x, G + 1, z));
        if (Math.floorMod(x * 3 + z, 5) != 0) set(x, G + 2, z, HEDGE.at(x, G + 2, z));
    }

    // ================================================================ ville autour (decor hors de la boite)

    private void city() {
        for (int t = -AREA; t <= AREA; t += 13) {
            for (int[] p : new int[][]{{t, BZ2 + 2}, {t, BZ1 - 2}, {BX1 - 2, t}, {BX2 + 2, t}}) {
                if (Math.abs(p[0]) > AREA || Math.abs(p[1]) > AREA) continue;
                if (isAir(p[0], G + 1, p[1])) lampPost(p[0], G + 1, p[1], 5, "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone", true);
            }
        }
        Style town = new Style().wall(TOWN).plinth(Mat.of("minecraft:stone_bricks")).post("minecraft:stripped_spruce_log")
                .corner(Mat.of("minecraft:smooth_sandstone")).band("minecraft:smooth_sandstone_stairs").cornice("minecraft:smooth_sandstone_stairs")
                .sill("minecraft:smooth_sandstone_stairs").lintel("minecraft:smooth_sandstone_slab").shutter("minecraft:dark_oak_trapdoor")
                .spacing(4).storey(4).blankBays(0.1).innerPosts(false).seed(99);
        int i = 0;
        for (int bx = -AREA + 2; bx < AREA - 10; bx += 12, i++) {
            int top = G + 12 + (i % 3) * 4;
            block(town, bx, BZ2 + 10, Dir.EAST, 11, Dir.NORTH, top, i);
            block(town, bx, BZ1 - 10, Dir.EAST, 11, Dir.SOUTH, top - 4 + (i % 2) * 4, i + 7);
        }
        for (int bz = BZ1 - 6; bz < BZ2 - 4; bz += 12, i++) {
            int top = G + 12 + (i % 3) * 4;
            block(town, BX1 - 10, bz, Dir.SOUTH, 11, Dir.EAST, top, i);
            block(town, BX2 + 10, bz, Dir.SOUTH, 11, Dir.WEST, top - 4 + (i % 2) * 4, i + 3);
        }
    }

    /** Immeuble de facade seule (3 de profondeur, sans interieur jouable). */
    private void block(Style st, int x0, int z0, Dir along, int len, Dir out, int top, int k) {
        Dir in = out.opposite();
        for (int t = 0; t <= len; t++)
            for (int d = 0; d <= 3; d++)
                for (int y = G + 1; y <= top; y++) {
                    int x = x0 + along.dx * t + in.dx * d, z = z0 + along.dz * t + in.dz * d;
                    if (Math.abs(x) > AREA || Math.abs(z) > AREA) continue;
                    if (d >= 2) set(x, y, z, TOWN.at(x, y, z));
                }
        st.seed(31L * k);
        Facade.wall(s, st, x0, z0, along, len, out, G, top, len / 2);
        for (int t = -1; t <= len + 1; t++)
            for (int d = 0; d <= 3; d++) {
                int x = x0 + along.dx * t + in.dx * d, z = z0 + along.dz * t + in.dz * d;
                if (Math.abs(x) > AREA || Math.abs(z) > AREA) continue;
                set(x, top + 1 + d / 2, z, d == 3 ? "minecraft:deepslate_tiles" : stairs("minecraft:deepslate_tile_stairs", in, false));
            }
        // fenetres eclairees (soiree)
        for (int t = 1; t < len; t++)
            for (int y = G + 2; y < top; y++) {
                int x = x0 + along.dx * t + in.dx * 2, z = z0 + along.dz * t + in.dz * 2;
                if (Math.abs(x) > AREA || Math.abs(z) > AREA) continue;
                if (B.name(get(x, y, z)).equals("glass_pane") && h(x, y, z) < 0.35) {
                    int bx = x + in.dx, bz = z + in.dz;
                    set(bx, y, bz, "minecraft:ochre_froglight");
                }
            }
    }

    // ================================================================ finitions

    /** Degage les deux cotes de chaque porte (2 de haut) : meubles et moulures poses trop pres. */
    private void clearDoors() {
        for (int x = BX1; x <= BX2; x++)
            for (int z = BZ1; z <= BZ2; z++)
                for (int y = G + 1; y <= U + 1; y++) {
                    String st = get(x, y, z);
                    if (!B.name(st).endsWith("_door") || !"lower".equals(B.prop(st, "half"))) continue;
                    Dir f = Dir.of(B.prop(st, "facing"));
                    for (Dir sd : new Dir[]{f, f.opposite()}) {
                        int ax = x + sd.dx, az = z + sd.dz;
                        for (int dy = 0; dy <= 1; dy++) {
                            String b = get(ax, y + dy, az);
                            if (B.isAir(b) || B.isSolid(b) && !isFurniture(b)) continue;
                            if (B.name(b).endsWith("_carpet")) continue;
                            set(ax, y + dy, az, B.AIR);
                        }
                        String above = get(ax, y + 2, az);
                        String an = B.name(above);
                        if (an.endsWith("pressure_plate") || an.endsWith("_carpet") || an.contains("candle") || an.startsWith("potted_")
                                || (an.endsWith("lantern") && !"true".equals(B.prop(above, "hanging")))) set(ax, y + 2, az, B.AIR);
                    }
                }
    }

    private static boolean isFurniture(String st) {
        String n = B.name(st);
        return n.equals("barrel") || n.contains("wool") || n.equals("bookshelf") || n.equals("iron_block") || n.equals("note_block")
                || n.equals("jukebox") || n.contains("stripped_dark_oak_wood") || n.equals("moss_block")
                || (n.equals("stripped_dark_oak_log") && "y".equals(B.prop(st, "axis")));
    }

    /** Aucune case pleine sur le segment (une lanterne derriere une cloison n'eclaire pas la piece). */
    private boolean clearLine(int x0, int y0, int z0, int x1, int y1, int z1) {
        int steps = 2 * Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(x0 + (x1 - x0) * t), y = (int) Math.round(y0 + (y1 - y0) * t), z = (int) Math.round(z0 + (z1 - z0) * t);
            if ((x == x1 && y == y1 && z == z1) || (x == x0 && y == y0 && z == z0)) continue;
            if (B.isSolid(get(x, y, z))) return false;
        }
        return true;
    }

    private static boolean emits(String st) {
        String n = B.name(st);
        return n.endsWith("lantern") || n.endsWith("froglight") || n.equals("sea_lantern") || n.equals("glowstone") || n.equals("shroomlight")
                || (n.contains("candle") && st.contains("lit=true")) || ((n.equals("smoker") || n.equals("furnace")) && st.contains("lit=true"))
                || n.equals("end_rod");
    }

    /**
     * Complement discret : sur les positions debout de la boite sans source de lumiere visible a moins de 6 blocs,
     * un bloc de lumiere invisible (niveau 11) au-dessus de la tete (le meurtrier ne doit pas pouvoir se cacher dans
     * le noir).
     */
    private void fillDarkSpots() {
        java.util.List<int[]> lights = new java.util.ArrayList<>();
        for (int x = BX1 - 6; x <= BX2 + 6; x++)
            for (int z = BZ1 - 6; z <= BZ2 + 6; z++)
                for (int y = G; y <= R + 2; y++) if (emits(get(x, y, z))) lights.add(new int[]{x, y, z});
        for (int x = BX1 + 1; x <= BX2 - 1; x++)
            for (int z = BZ1 + 1; z <= BZ2 - 1; z++)
                for (int y : new int[]{G + 1, U + 1}) {
                    if (!isAir(x, y, z) || !isAir(x, y + 1, z) || isAir(x, y - 1, z)) continue;
                    boolean lit = false;
                    for (int[] l : lights) {
                        int dx = l[0] - x, dy = l[1] - y, dz = l[2] - z;
                        if (dx * dx + dz * dz + dy * dy * 0.5 <= 36 && clearLine(x, y + 1, z, l[0], l[1], l[2])) { lit = true; break; }
                    }
                    if (lit) continue;
                    int ly = y + 2;
                    if (!isAir(x, ly, z)) continue;
                    set(x, ly, z, "minecraft:light[level=11,waterlogged=false]");
                    lights.add(new int[]{x, ly, z});
                }
    }
}
