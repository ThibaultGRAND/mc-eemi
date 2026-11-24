package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Dir;
import fr.eemicraft.murder.build.Facade;
import fr.eemicraft.murder.build.Mat;
import fr.eemicraft.murder.build.Palette;
import fr.eemicraft.murder.build.Roof;
import fr.eemicraft.murder.build.RoofStyle;
import fr.eemicraft.murder.build.Sdf;
import fr.eemicraft.murder.build.Shapes;
import fr.eemicraft.murder.build.Sink;
import fr.eemicraft.murder.build.Style;
import fr.eemicraft.murder.build.Tree;

/**
 * Map « Grand Musee » (id musee, monde mm_musee), refonte de Museum (1.8.0), dessinee par code dans un {@link Sink}.
 *
 * <p>Meme gabarit que Museum : boite x -48..48, z -41..50, batiment x -34..34 / z -26..26 au milieu d'un jardin
 * clos, parvis et fontaine au sud, cour de service au nord. Rez-de-chaussee : sol en y 64 (pieds en 65) ; etage :
 * plancher en y 71 (pieds en 72) ; plafond de l'etage en y 78 (toit non accessible). Plan (meme grille que
 * l'original : 2 ailes de 2 salles + une travee centrale) :
 * <ul>
 * <li>rez-de-chaussee : vestibule (sud), grand hall sous verriere avec le squelette de T. rex et deux escaliers
 * d'honneur, salle des mineraux (nord, geode), Egypte (nord-ouest), armures (sud-ouest), ocean (nord-est,
 * aquariums, baleine), salle du tresor (sud-est) ;</li>
 * <li>etage : galerie autour du vide du hall, peinture (nord-ouest), sculptures (sud-ouest), astronomie (nord-est,
 * planetarium), arts d'Asie (sud-est, porte de lune), cartes et globes (nord), cafe (sud) ;</li>
 * <li>5 escaliers relient les niveaux (2 dans le hall, nord-ouest, nord-est, sud-est) ; toutes les salles ont au
 * moins deux sorties ; 7 portes vers le jardin.</li>
 * </ul>
 */
final class Musee extends MapKit {
    static final int VERSION = 3; // 1.13.0 : vitres dans le mur (Facade)
    static final String ID = "musee";
    static final String WORLD = "mm_musee";
    static final String NAME = "Grand Musée";

    static final int BX1 = -48, BX2 = 48, BZ1 = -41, BZ2 = 50, MIN_Y = 62, MAX_Y = 86;
    static final int AREA = 66, Y_LO = 54, Y_HI = 96;
    static final int G = 64, U = 71, T = 78;
    static final int[] WORLD_SPAWN = {0, 65, 35};

    static final int[][] SPAWNS = {
            {6, 65, 21},     // vestibule
            {0, 65, 11},     // grand hall
            {-20, 65, -4},   // Egypte
            {-22, 65, 8},    // armures
            {20, 65, -5},    // ocean
            {18, 65, 14},    // tresor
            {-8, 65, -19},   // mineraux
            {-14, 65, 40},   // parvis
            {16, 65, -36},   // cour de service
            {-41, 65, -4},   // jardin ouest
            {41, 65, 10},    // jardin est
            {-22, 72, -8},   // peinture
            {-22, 72, 16},   // sculptures
            {22, 72, -8},    // astronomie
            {22, 72, 10},    // arts d'Asie
            {6, 72, -19},    // cartes
            {-6, 72, 20},    // cafe
            {-10, 72, -4},   // galerie
    };

    static final int[][] GOLD = {
            // grand hall, vestibule
            {-9, 65, -10}, {9, 65, 12}, {0, 65, -10}, {-3, 65, 6}, {-9, 65, 20}, {8, 65, 22}, {-1, 65, 16},
            // mineraux, Egypte, armures
            {6, 65, -19}, {-6, 65, -15}, {-30, 65, -20}, {-15, 65, -10}, {-27, 65, -3}, {-30, 65, 5}, {-15, 65, 22}, {-26, 65, 22},
            // ocean, tresor
            {15, 65, -19}, {26, 65, -8}, {15, 65, 3}, {27, 65, 20}, {24, 65, 14},
            // etage : galerie, peinture, sculptures
            {9, 72, 0}, {0, 72, -11}, {-2, 72, 12}, {-30, 72, -20}, {-15, 72, -3}, {-29, 72, 3}, {-16, 72, 22},
            // astronomie, Asie, cartes, cafe
            {27, 72, -20}, {15, 72, -4}, {29, 72, 6}, {16, 72, 22}, {-8, 72, -22}, {9, 72, -16}, {9, 72, 22}, {-9, 72, 16},
            // dehors
            {-24, 65, 47}, {27, 65, 38}, {-44, 65, 30}, {44, 65, -24}, {-30, 65, -36}, {32, 65, -38}, {0, 65, -30},
    };

    private Musee(Sink s) {
        super(s, 7171);
    }

    static void build(Sink s) {
        new Musee(s).run();
    }

    // ================================================================ palettes

    private static final Palette STONE = Palette.of(41, "minecraft:smooth_sandstone", 62, "minecraft:cut_sandstone", 22,
            "minecraft:sandstone", 16).period(7);
    private static final Palette PLINTH = Palette.of(42, "minecraft:polished_andesite", 72, "minecraft:stone_bricks", 18,
            "minecraft:andesite", 10).period(8);
    private static final Palette MARBLE = Palette.of(43, "minecraft:polished_diorite", 64, "minecraft:calcite", 24,
            "minecraft:smooth_quartz", 12).period(8);
    private static final Palette PARQUET = Palette.of(44, "minecraft:oak_planks", 70, "minecraft:birch_planks", 30).period(6);
    private static final Palette PARQUET_D = Palette.of(45, "minecraft:dark_oak_planks", 70, "minecraft:spruce_planks", 30).period(6);
    private static final Palette GRASS = Palette.of(46, "minecraft:grass_block", 82, "minecraft:moss_block", 8,
            "minecraft:podzol", 5, "minecraft:coarse_dirt", 5).period(5);
    private static final Palette PAVE = Palette.of(47, "minecraft:stone_bricks", 60, "minecraft:polished_andesite", 24,
            "minecraft:cracked_stone_bricks", 10, "minecraft:mossy_stone_bricks", 6).period(7);
    private static final Palette GRAVEL = Palette.of(48, "minecraft:gravel", 60, "minecraft:coarse_dirt", 22,
            "minecraft:packed_mud", 18).period(4);
    private static final Palette ASPHALT = Palette.of(49, "minecraft:gray_concrete", 70, "minecraft:gray_concrete_powder", 18,
            "minecraft:cyan_terracotta", 12).period(6);
    private static final Palette HEDGE = Palette.of(50, "minecraft:azalea_leaves[persistent=true]", 68,
            "minecraft:oak_leaves[persistent=true]", 32).period(4);
    private static final Palette SAND_FLOOR = Palette.of(51, "minecraft:smooth_sandstone", 70, "minecraft:sandstone", 18,
            "minecraft:sand", 12).period(5);
    private static final Palette OCEAN_FLOOR = Palette.of(52, "minecraft:prismarine_bricks", 64, "minecraft:dark_prismarine", 24,
            "minecraft:prismarine", 12).period(6);
    private static final Palette TOWN = Palette.of(53, "minecraft:bricks", 55, "minecraft:mud_bricks", 25, "minecraft:terracotta", 20).period(8);

    // ================================================================ deroule

    private void run() {
        terrain();
        building();
        floors();
        partitions();
        stairs();
        atrium();
        vestibule();
        minerals();
        egypt();
        armory();
        ocean();
        treasure();
        paintings();
        sculptures();
        astronomy();
        asia();
        maps();
        cafe();
        roof();
        portico();
        plaza();
        gardens();
        backyard();
        fence();
        city();
        finish(-AREA, G - 1, -AREA, AREA, Y_HI - 1, AREA);
    }

    private boolean inBox(int x, int z) {
        return x >= BX1 && x <= BX2 && z >= BZ1 && z <= BZ2;
    }

    // ================================================================ terrain

    private void terrain() {
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                int depth = 5 + (int) Math.round(2 * noise.noise2(x / 7.0, z / 7.0));
                for (int y = G - depth; y < G; y++) set(x, y, z, y >= G - 2 ? "minecraft:dirt" : "minecraft:stone");
                String top;
                if (inBox(x, z)) top = GRASS.at(x, G, z);
                else if (Math.abs(x) <= BX2 + 2 && z >= BZ1 - 2 && z <= BZ2 + 2) top = PAVE.at(x, G, z); // trottoir
                else if (Math.abs(x) <= BX2 + 9 && z >= BZ1 - 9 && z <= BZ2 + 9) top = ASPHALT.at(x, G, z); // rue
                else top = PAVE.at(x, G, z);
                set(x, G, z, top);
            }
        // marquages de la rue : tirets blancs
        for (int t = -60; t <= 60; t++) {
            if (Math.floorMod(t, 6) >= 3) continue;
            setStreet(t, BZ2 + 6);
            setStreet(t, BZ1 - 6);
            setStreetZ(BX1 - 6, t);
            setStreetZ(BX2 + 6, t);
        }
    }

    private void setStreet(int x, int z) {
        if (Math.abs(x) <= AREA && Math.abs(z) <= AREA && get(x, G, z).contains("concrete")) set(x, G, z, "minecraft:white_concrete");
    }

    private void setStreetZ(int x, int z) {
        if (Math.abs(x) <= AREA && Math.abs(z) <= AREA && get(x, G, z).contains("concrete") && z > BZ1 - 6 && z < BZ2 + 6) set(x, G, z, "minecraft:white_concrete");
    }

    // ================================================================ enveloppe

    private Style style() {
        return new Style().wall(STONE).plinth(PLINTH).plinthStairs("minecraft:polished_andesite_stairs")
                .post("minecraft:quartz_pillar").corner(Mat.of("minecraft:quartz_bricks")).pane("minecraft:glass_pane")
                .sill("minecraft:smooth_sandstone_stairs").lintel("minecraft:smooth_sandstone_slab").shutter(null)
                .band("minecraft:smooth_quartz_stairs").cornice("minecraft:quartz_stairs").spacing(6).storey(7)
                .blankBays(0).innerPosts(false).seed(17);
    }

    private void building() {
        Style st = style();
        Facade.wall(s, st, -34, -26, Dir.EAST, 68, Dir.NORTH, G, T, -1);
        Facade.wall(s, st, -34, 26, Dir.EAST, 68, Dir.SOUTH, G, T, -1);
        Facade.wall(s, st, -34, -26, Dir.SOUTH, 52, Dir.WEST, G, T, -1);
        Facade.wall(s, st, 34, -26, Dir.SOUTH, 52, Dir.EAST, G, T, -1);
        // pavillons d'angle en saillie (+1) : chainages en quartz (l'attique est pose avec le toit)
        for (int[] c : new int[][]{{-34, -26}, {34, -26}, {-34, 26}, {34, 26}}) {
            int sx = Integer.signum(c[0]), sz = Integer.signum(c[1]);
            for (int y = G; y <= T; y++)
                for (int i = 0; i <= 7; i++) {
                    if (y != G && i != 0 && i != 7) continue;
                    set(c[0] + sx, y, c[1] - sz * i, y == G ? PLINTH.at(0, y, 0) : "minecraft:quartz_bricks");
                    set(c[0] - sx * i, y, c[1] + sz, y == G ? PLINTH.at(0, y, 0) : "minecraft:quartz_bricks");
                }
        }
        // portes : grande porte sud (5), nord (3), laterales (2)
        door(-2, 2, 26, Dir.SOUTH, 69);
        door(-1, 1, -26, Dir.NORTH, 68);
        doorX(-34, -12, -11, Dir.WEST);
        doorX(-34, 12, 13, Dir.WEST);
        doorX(34, -12, -11, Dir.EAST);
        doorX(34, 12, 13, Dir.EAST);
    }

    /** Voids the null-safe set used above (null = ne rien poser). */
    @Override
    void set(int x, int y, int z, String st) {
        if (st != null) super.set(x, y, z, st);
    }

    private void door(int xa, int xb, int z, Dir out, int top) {
        int in = -out.dz;
        for (int x = xa; x <= xb; x++)
            for (int y = G + 1; y <= top; y++)
                for (int d = 0; d <= 2; d++) set(x, y, z + in * d, B.AIR);
        for (int x = xa - 1; x <= xb + 1; x++) set(x, top + 1, z, x == xa - 1 || x == xb + 1 ? "minecraft:chiseled_quartz_block" : facing("minecraft:light_blue_glazed_terracotta", out));
        for (int x = xa; x <= xb; x++) set(x, G, z + out.dz, stairs("minecraft:polished_andesite_stairs", out.opposite(), false));
        for (int y = G + 1; y <= top; y++) { set(xa - 1, y, z, "minecraft:quartz_pillar[axis=y]"); set(xb + 1, y, z, "minecraft:quartz_pillar[axis=y]"); }
    }

    private void doorX(int x, int za, int zb, Dir out) {
        int in = -out.dx;
        for (int z = za; z <= zb; z++)
            for (int y = G + 1; y <= G + 3; y++)
                for (int d = 0; d <= 2; d++) set(x + in * d, y, z, B.AIR);
        for (int z = za - 1; z <= zb + 1; z++) set(x, G + 4, z, z == za - 1 || z == zb + 1 ? "minecraft:chiseled_quartz_block" : slab("minecraft:smooth_quartz_slab", "top"));
        for (int z = za; z <= zb; z++) set(x + out.dx, G, z, stairs("minecraft:polished_andesite_stairs", out.opposite(), false));
        set(x + out.dx, G + 3, za - 1, standing("minecraft:lantern"));
        set(x + out.dx, G + 3, zb + 1, standing("minecraft:lantern"));
        setIfAir(x + out.dx, G + 2, za - 1, "minecraft:andesite_wall");
        setIfAir(x + out.dx, G + 2, zb + 1, "minecraft:andesite_wall");
        setIfAir(x + out.dx, G + 1, za - 1, "minecraft:polished_andesite");
        setIfAir(x + out.dx, G + 1, zb + 1, "minecraft:polished_andesite");
    }

    // ================================================================ sols, plafonds, poutres

    /** Interieur : x -32..32, z -24..24 (les murs exterieurs prennent 2 rangs). */
    static final int IX1 = -32, IX2 = 32, IZ1 = -24, IZ2 = 24;
    /** Vide du grand hall (de U a T). */
    static final int VX1 = -7, VX2 = 7, VZ1 = -8, VZ2 = 9;

    private boolean inVoid(int x, int z) {
        return x >= VX1 && x <= VX2 && z >= VZ1 && z <= VZ2;
    }

    private void floors() {
        for (int x = IX1; x <= IX2; x++)
            for (int z = IZ1; z <= IZ2; z++) {
                set(x, G, z, groundFloor(x, z));
                set(x, U, z, inVoid(x, z) ? B.AIR : upperFloor(x, z));
                set(x, T, z, inVoid(x, z) ? B.AIR : ceiling(x, z));
            }
        // poutres du rez-de-chaussee (sous le plancher, dans la petite largeur des salles) et de l'etage
        for (int x = IX1; x <= IX2; x++)
            for (int z = IZ1; z <= IZ2; z++) {
                boolean wing = Math.abs(x) >= 13;
                if (wing && Math.floorMod(z, 4) == 0 && !(z == 0)) {
                    set(x, U - 1, z, log("minecraft:stripped_dark_oak_log", 'x'));
                    if (Math.floorMod(x, 6) == 0) set(x, T - 1, z, log("minecraft:stripped_birch_log", 'x'));
                }
                if (!wing && !inVoid(x, z) && Math.floorMod(x, 4) == 0 && (z < -12 || z > 14)) set(x, U - 1, z, log("minecraft:stripped_dark_oak_log", 'z'));
            }
    }

    private String groundFloor(int x, int z) {
        if (x <= -13 && z <= -1) return (x + z) % 9 == 0 ? "minecraft:chiseled_sandstone" : SAND_FLOOR.at(x, G, z);
        if (x <= -13) return ((x / 2 + z / 2) & 1) == 0 ? "minecraft:polished_andesite" : "minecraft:stone_bricks";
        if (x >= 13 && z <= -1) return OCEAN_FLOOR.at(x, G, z);
        if (x >= 13) return ((x + z) & 1) == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:polished_blackstone";
        if (z <= -13) return ((x + z) & 1) == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles";
        if (z >= 14) return ((x + z) & 1) == 0 ? "minecraft:polished_diorite" : "minecraft:polished_andesite";
        // grand hall : marbre clair, bandes de basalte poli en croix
        if (Math.abs(x) <= 1 || Math.abs(z - 1) <= 0) return "minecraft:polished_basalt[axis=y]";
        return MARBLE.at(x, G, z);
    }

    private String upperFloor(int x, int z) {
        if (Math.abs(x) <= 11 && z >= -12 && z <= 13) return ((x + z) & 1) == 0 ? "minecraft:smooth_quartz" : "minecraft:polished_diorite";
        if (x >= 13 && z >= 1) return ((x + z) % 4 == 0) ? "minecraft:bamboo_planks" : "minecraft:bamboo_mosaic";
        if (x >= 13) return "minecraft:black_concrete";
        if (z <= -13 || z >= 14) return PARQUET_D.at(x, U, z);
        return PARQUET.at(x, U, z);
    }

    private String ceiling(int x, int z) {
        if (x >= 13 && z <= -1) return Math.floorMod(x * 7 + z * 13, 29) == 0 ? "minecraft:sea_lantern" : "minecraft:black_concrete";
        boolean grid = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
        if (!grid && Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2) return "minecraft:pearlescent_froglight";
        return grid ? "minecraft:quartz_bricks" : "minecraft:smooth_quartz";
    }

    // ================================================================ cloisons et passages

    private void partitions() {
        // murs porteurs : x -12 et x 12 (toute la profondeur), z 0 dans les ailes, z -13 et z 14 au centre
        Mat wall = Mat.of("minecraft:smooth_quartz");
        for (int y = G + 1; y < T; y++) {
            if (y == U) continue;
            for (int z = IZ1; z <= IZ2; z++) { set(-12, y, z, wall); set(12, y, z, wall); }
            for (int x = IX1; x <= -13; x++) set(x, y, 0, wall);
            for (int x = 13; x <= IX2; x++) set(x, y, 0, wall);
            for (int x = -11; x <= 11; x++) { set(x, y, -13, wall); set(x, y, 14, wall); }
        }
        // passages en arc (3 de large, 4 de haut) aux deux niveaux
        for (int base : new int[]{G, U}) {
            archX(-12, -8, base); archX(-12, 6, base); archX(12, -8, base); archX(12, 6, base);
            archX(-12, -20, base); archX(12, -20, base); archX(-12, 18, base); archX(12, 18, base);
            archZ(0, -24, base); archZ(0, 22, base);
            archZ(-13, -6, base); archZ(-13, 4, base);
            archZ(14, -6, base); archZ(14, 4, base); archZ(14, -1, base);
        }
        archZ(-13, -1, G);
        // porte de lune entre l'astronomie et l'Asie (cercle de 5)
        for (int x = 20; x <= 26; x++)
            for (int y = U + 1; y <= U + 6; y++) {
                double d = Math.hypot(x - 23, y - (U + 3.5));
                if (d <= 2.9) set(x, y, 0, B.AIR);
                else if (d <= 3.6) set(x, y, 0, "minecraft:stripped_cherry_wood[axis=y]");
            }
        // plinthes et cimaises le long des cloisons (cassent les pans interieurs)
        for (int z = IZ1; z <= IZ2; z++) for (int x : new int[]{-13, -11, 11, 13}) trim(x, z);
        for (int x = IX1; x <= IX2; x++) for (int z : new int[]{-1, 1}) if (Math.abs(x) >= 13) trim(x, z);
        for (int x = -11; x <= 11; x++) for (int z : new int[]{-14, -12, 13, 15}) trim(x, z);
        for (int x = IX1; x <= IX2; x++) for (int z : new int[]{IZ1, IZ2}) trim(x, z);
        for (int z = IZ1; z <= IZ2; z++) for (int x : new int[]{IX1, IX2}) trim(x, z);
    }

    /** Pilastres tous les 4 et cimaise le long d'un mur, depuis la case de salle (x, z) qui le touche. */
    private void trim(int x, int z) {
        Dir toWall = null;
        for (Dir d : Dir.values()) {
            String n = get(x + d.dx, G + 3, z + d.dz);
            if (n.contains("smooth_quartz") || n.contains("sandstone") || n.contains("quartz_pillar")) { toWall = d; break; }
        }
        if (toWall == null) return;
        for (int base : new int[]{G, U}) {
            if (base == U && inVoid(x, z)) continue;
            if (!isAir(x, base + 1, z)) continue;
            int t = toWall.axis() == 'x' ? z : x;
            if (Math.floorMod(t, 4) == 0 && isAir(x, base + 2, z)) {
                // pilastre jusqu'au plafond, chapiteau sous la corniche
                for (int y = base + 1; y <= base + 6; y++) if (isAir(x, y, z)) set(x, y, z, y == base + 6 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
            } else {
                // plinthe, cimaise, corniche (trois rangs de modenature)
                setIfAir(x, base + 1, z, stairs(base == G ? "minecraft:dark_oak_stairs" : "minecraft:spruce_stairs", toWall, false));
                setIfAir(x, base + 4, z, trap("minecraft:dark_oak_trapdoor", toWall.opposite(), true, false));
                setIfAir(x, base + 6, z, stairs("minecraft:smooth_quartz_stairs", toWall, true));
            }
        }
    }

    private void archX(int x, int zc, int base) {
        for (int z = zc - 1; z <= zc + 1; z++) for (int y = base + 1; y <= base + 4; y++) set(x, y, z, B.AIR);
        set(x, base + 4, zc - 1, stairs("minecraft:quartz_stairs", Dir.SOUTH, true));
        set(x, base + 4, zc + 1, stairs("minecraft:quartz_stairs", Dir.NORTH, true));
        set(x, base + 5, zc, "minecraft:chiseled_quartz_block");
    }

    private void archZ(int z, int xc, int base) {
        for (int x = xc - 1; x <= xc + 1; x++) for (int y = base + 1; y <= base + 4; y++) set(x, y, z, B.AIR);
        set(xc - 1, base + 4, z, stairs("minecraft:quartz_stairs", Dir.EAST, true));
        set(xc + 1, base + 4, z, stairs("minecraft:quartz_stairs", Dir.WEST, true));
        set(xc, base + 5, z, "minecraft:chiseled_quartz_block");
    }

    // ================================================================ escaliers

    private void stairs() {
        // nord-ouest (Egypte -> peinture) : monte vers l'est le long du mur nord
        // (1.9.1 : chaque volee commence un bloc plus loin du mur d'about : la premiere marche etait collee a la
        // fenetre, on ne pouvait l'aborder que de cote)
        flight(-30, -23, Dir.EAST, 2, "minecraft:spruce");
        // nord-est (ocean -> astronomie) : monte vers le sud le long du mur est
        flight(30, -22, Dir.SOUTH, 2, "minecraft:spruce");
        // sud-est (tresor -> Asie) : monte vers l'ouest le long du mur sud
        flight(30, 23, Dir.WEST, 2, "minecraft:dark_oak");
    }

    /**
     * Volee droite de 7 marches de (x0, z0) vers dir, largeur w (vers la droite de dir), tremie dans le plancher et
     * garde-corps autour a l'etage.
     */
    private void flight(int x0, int z0, Dir dir, int w, String wood) {
        Dir side = dir.cw();
        for (int k = 0; k < 7; k++)
            for (int j = 0; j < w; j++) {
                int x = x0 + dir.dx * k + side.dx * j, z = z0 + dir.dz * k + side.dz * j;
                for (int y = G + 1; y < G + 1 + k; y++) set(x, y, z, wood + "_planks");
                set(x, G + 1 + k, z, stairs(wood + "_stairs", dir, false));
                for (int y = G + 2 + k; y <= U; y++) set(x, y, z, B.AIR);
            }
        // tremie au-dessus des marches 2..6 + garde-corps sur le cote libre
        for (int k = 0; k < 7; k++) {
            int x = x0 + dir.dx * k + side.dx * w, z = z0 + dir.dz * k + side.dz * w;
            setIfAir(x, U + 1, z, wood + "_fence");
            
        }
        for (int j = 0; j < w; j++) {
            int x = x0 - dir.dx + side.dx * j, z = z0 - dir.dz + side.dz * j;
            setIfAir(x, U + 1, z, wood + "_fence");
        }
        // palier : le haut de la volee debouche sur le plancher
        int tx = x0 + dir.dx * 7, tz = z0 + dir.dz * 7;
        for (int j = 0; j < w; j++) {
            set(tx + side.dx * j, U + 1, tz + side.dz * j, B.AIR);
            set(tx + side.dx * j, U + 2, tz + side.dz * j, B.AIR);
        }
    }

    // ================================================================ grand hall

    private void atrium() {
        // galerie : balustrade autour du vide (verre + quartz), ouvertures en haut des escaliers d'honneur
        for (int x = VX1 - 1; x <= VX2 + 1; x++)
            for (int z = VZ1 - 1; z <= VZ2 + 1; z++) {
                boolean edge = x == VX1 - 1 || x == VX2 + 1 || z == VZ1 - 1 || z == VZ2 + 1;
                if (!edge) continue;
                if (Math.abs(x) == 8 && z >= 2 && z <= 4) continue; // haut des escaliers
                boolean post = Math.floorMod(x + z, 3) == 0;
                set(x, U + 1, z, post ? "minecraft:quartz_pillar[axis=y]" : "minecraft:white_stained_glass_pane");
                set(x, U + 2, z, slab("minecraft:smooth_quartz_slab", "bottom"));
                set(x, U - 1, z, stairs("minecraft:quartz_stairs", x == VX1 - 1 ? Dir.WEST : x == VX2 + 1 ? Dir.EAST : z == VZ1 - 1 ? Dir.NORTH : Dir.SOUTH, true));
            }
        // escaliers d'honneur : 7 marches du sud au nord le long du vide, joue pleine cote hall, palier
        for (int sx : new int[]{-1, 1}) {
            int xa = sx < 0 ? -7 : 5, xb = sx < 0 ? -5 : 7, cheek = sx < 0 ? -4 : 4;
            for (int k = 0; k < 7; k++) {
                int z = 9 - k;
                for (int x = xa; x <= xb; x++) {
                    for (int y = G + 1; y < G + 1 + k; y++) set(x, y, z, "minecraft:polished_diorite");
                    set(x, G + 1 + k, z, stairs("minecraft:polished_diorite_stairs", Dir.NORTH, false));
                }
                for (int y = G + 1; y <= G + 2 + k; y++) set(cheek, y, z, y == G + 2 + k ? slab("minecraft:smooth_quartz_slab", "bottom") : "minecraft:smooth_quartz");
            }
            for (int x = xa; x <= xb; x++) {
                set(x, U, 2, "minecraft:polished_diorite");
                // 1.9.1 : la 7e marche (z 3, au niveau du plancher) reste un escalier : un bloc plein ici obligeait
                // a sauter depuis la 6e marche
                set(x, U + 1, 1, x == xa || x == xb ? "minecraft:quartz_pillar[axis=y]" : "minecraft:white_stained_glass_pane");
                set(x, U + 2, 1, slab("minecraft:smooth_quartz_slab", "bottom"));
                for (int y = G + 1; y < U; y++) set(x, y, 2, y == G + 1 ? "minecraft:polished_andesite" : "minecraft:smooth_quartz");
            }
            for (int z = 1; z <= 3; z++) {
                set(cheek, U, z, "minecraft:polished_diorite");
                set(cheek, U + 1, z, z == 1 ? "minecraft:quartz_pillar[axis=y]" : "minecraft:white_stained_glass_pane");
                set(cheek, U + 2, z, slab("minecraft:smooth_quartz_slab", "bottom"));
                for (int y = G + 1; y < U; y++) set(cheek, y, z, "minecraft:smooth_quartz");
            }
            // tapis rouge sur les marches
            for (int k = 0; k < 7; k++) {
                int x = sx < 0 ? -6 : 6;
                set(x, G + 1 + k, 9 - k, stairs("minecraft:red_nether_brick_stairs", Dir.NORTH, false));
            }
        }
        // squelette de T. rex sur socle (tete au nord, queue au sud au-dessus du passage)
        for (int x = -2; x <= 2; x++)
            for (int z = -6; z <= 2; z++) set(x, G + 1, z, Math.abs(x) == 2 || z == -6 || z == 2 ? "minecraft:polished_andesite" : "minecraft:smooth_stone");
        for (int x = -2; x <= 2; x++) { set(x, G + 1, -7, stairs("minecraft:polished_andesite_stairs", Dir.SOUTH, false)); set(x, G + 1, 3, stairs("minecraft:polished_andesite_stairs", Dir.NORTH, false)); }
        trex();
        // bancs, palmiers, borne
        bench(-9, G + 1, -3, Dir.EAST, "dark_oak");
        bench(9, G + 1, -3, Dir.WEST, "dark_oak");
        bigPlant(-10, G + 1, 12, "dark_oak");
        bigPlant(10, G + 1, -11, "dark_oak");
        bigPlant(-10, G + 1, -11, "dark_oak");
        bigPlant(10, G + 1, 12, "dark_oak");
        set(0, G + 1, -11, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        // lustres depuis la verriere
        chandelier(0, 83, -5, 7, true, "minecraft:spruce_trapdoor", "minecraft:lantern");
        chandelier(0, 83, 7, 7, true, "minecraft:spruce_trapdoor", "minecraft:lantern");
        // appliques de la galerie
        for (int z = -11; z <= 12; z += 4) { sconce(-11, U + 3, z, Dir.EAST, "minecraft:quartz_stairs", "minecraft:lantern"); sconce(11, U + 3, z, Dir.WEST, "minecraft:quartz_stairs", "minecraft:lantern"); }
        // bustes sur la galerie
        for (int[] b : new int[][]{{-10, -11}, {10, 12}, {-10, 7}, {10, -6}}) bust(b[0], U + 1, b[1]);
    }

    private void trex() {
        Mat bone = Mat.of("minecraft:bone_block[axis=y]");
        // pattes
        for (int sx : new int[]{-1, 1}) {
            for (int y = G + 2; y <= G + 5; y++) set(sx, y, 0, bone);
            set(sx, G + 2, -1, "minecraft:bone_block[axis=z]");
            set(sx, G + 6, 1, bone);
        }
        // bassin, colonne vertebrale (monte des hanches au cou), cotes
        int[][] spine = {{8, 68}, {7, 68}, {6, 69}, {5, 69}, {4, 70}, {3, 70}, {2, 71}, {1, 71}, {0, 71}, {-1, 72}, {-2, 72}, {-3, 72}, {-4, 73}, {-5, 73}};
        for (int[] p : spine) set(0, p[1], p[0], "minecraft:bone_block[axis=z]");
        for (int z = -4; z <= 0; z++) for (int sx : new int[]{-1, 1}) { set(sx, 70, z, "minecraft:bone_block[axis=y]"); set(sx, 69, z, z % 2 == 0 ? "minecraft:bone_block[axis=y]" : B.AIR); }
        set(0, 69, -3, "minecraft:bone_block[axis=y]");
        // bras courts
        for (int sx : new int[]{-1, 1}) { set(sx * 2, 71, -4, "minecraft:bone_block[axis=x]"); set(sx * 2, 70, -5, "minecraft:bone_block[axis=y]"); }
        // crane (x -1..1, z -8..-6), machoire, dents
        for (int x = -1; x <= 1; x++) {
            for (int z = -8; z <= -6; z++) set(x, 74, z, "minecraft:bone_block[axis=z]");
            set(x, 73, -6, "minecraft:bone_block[axis=y]");
            set(x, 72, -8, "minecraft:bone_block[axis=z]");
            set(x, 73, -8, stairs("minecraft:quartz_stairs", Dir.NORTH, true));
        }
        set(-1, 74, -7, "minecraft:coal_block");
        set(1, 74, -7, "minecraft:coal_block");
        // ecriteau
        set(0, G + 2, 3, "minecraft:dark_oak_sign[rotation=0,waterlogged=false]");
    }

    private void bust(int x, int y, int z) {
        set(x, y, z, "minecraft:quartz_pillar[axis=y]");
        set(x, y + 1, z, "minecraft:calcite");
        set(x, y + 2, z, "minecraft:player_head[rotation=" + (int) (h(x, y, z) * 16) + "]");
    }

    // ================================================================ verriere et toit

    private void roof() {
        // terrasse (non accessible) : dalles de pierre sur le plafond de l'etage ; acrotere a balustres, pots a feu
        for (int x = IX1; x <= IX2; x++)
            for (int z = IZ1; z <= IZ2; z++)
                if (Math.abs(x) > VX2 + 1 || z < VZ1 - 1 || z > VZ2 + 1) set(x, T + 1, z, (Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0)
                        ? slab("minecraft:smooth_stone_slab", "bottom") : slab("minecraft:stone_brick_slab", "bottom"));
        for (int x = -33; x <= 33; x++) for (int z : new int[]{-25, 25}) if (Math.abs(x) < 26) balustrade(x, z);
        for (int z = -25; z <= 25; z++) for (int x : new int[]{-33, 33}) if (Math.abs(z) < 18) balustrade(x, z);
        // lanterneaux vitres au-dessus des galeries de l'etage (le plafond est ouvert dessous)
        skylight(-27, -21, -19, -7);
        skylight(-27, 5, -19, 21);
        skylight(18, 5, 26, 21);
        skylight(-5, -23, 5, -15);
        chandelier(0, T + 4, -19, 6, true, "minecraft:dark_oak_trapdoor", "minecraft:lantern"); // salle des cartes, sous le lanterneau
        // ventilations et edicules techniques sur la terrasse
        for (int[] v : new int[][]{{-12, -20}, {12, -20}, {-12, 20}, {12, 20}, {-30, 0}, {30, 0}}) {
            fill(v[0] - 1, T + 1, v[1] - 1, v[0] + 1, T + 2, v[1] + 1, "minecraft:stone_bricks");
            for (int x = v[0] - 1; x <= v[0] + 1; x++) for (int z = v[1] - 1; z <= v[1] + 1; z++) set(x, T + 3, z, (x + z) % 2 == 0 ? "minecraft:iron_bars" : slab("minecraft:stone_brick_slab", "bottom"));
        }
        // attiques des pavillons d'angle
        for (int[] c : new int[][]{{-34, -26}, {34, -26}, {-34, 26}, {34, 26}}) {
            int sx = Integer.signum(c[0]), sz = Integer.signum(c[1]);
            for (int y = T + 1; y <= T + 4; y++)
                for (int i = -1; i <= 7; i++)
                    for (int j = -1; j <= 7; j++) {
                        int x = c[0] - sx * i, z = c[1] - sz * j;
                        boolean quoin = (i == -1 || i == 7) && (j == -1 || j == 7);
                        boolean face = i == -1 || i == 7 || j == -1 || j == 7;
                        if (!face) { set(x, y, z, "minecraft:smooth_stone"); continue; }
                        boolean win = !quoin && y >= T + 2 && y <= T + 3 && Math.floorMod(i + j, 3) == 1;
                        set(x, y, z, quoin ? "minecraft:quartz_bricks" : (win ? "minecraft:glass" : STONE.at(x, y, z)));
                    }
        }
        // toits a la Mansart des pavillons d'angle
        RoofStyle rs = new RoofStyle().stairs("minecraft:deepslate_tile_stairs").slab("minecraft:deepslate_tile_slab")
                .ridge("minecraft:deepslate_tile_slab").body(Mat.of("minecraft:deepslate_tiles")).gable(STONE).overhang(1);
        Roof.build(s, rs, Roof.hip(-35, -27, -27, -19, T + 4), Roof.hip(27, -27, 35, -19, T + 4),
                Roof.hip(-35, 19, -27, 27, T + 4), Roof.hip(27, 19, 35, 27, T + 4));
        for (int[] c : new int[][]{{-31, -23}, {31, -23}, {-31, 23}, {31, 23}}) {
            for (int y = T + 8; y <= T + 12; y++) set(c[0], y, c[1], y == T + 12 ? "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]" : "minecraft:waxed_oxidized_cut_copper");
        }
        // verriere en berceau sur le vide du hall : arcs en cuivre tous les 3, verre entre
        for (int z = VZ1 - 1; z <= VZ2 + 1; z++)
            for (int x = VX1 - 1; x <= VX2 + 1; x++) {
                double u = x / 8.5;
                int top = T + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - u * u)));
                boolean rib = Math.floorMod(z, 3) == 0 || z == VZ1 - 1 || z == VZ2 + 1;
                set(x, top, z, rib ? "minecraft:waxed_oxidized_cut_copper" : (Math.abs(x) <= 1 ? "minecraft:light_blue_stained_glass" : "minecraft:glass"));
                for (int y = T; y < top; y++) if (Math.abs(x) == VX2 + 1) set(x, y, z, rib ? "minecraft:waxed_oxidized_cut_copper" : "minecraft:glass");
                if (z == VZ1 - 1 || z == VZ2 + 1) for (int y = T; y < top; y++) set(x, y, z, Math.abs(x) % 3 == 0 ? "minecraft:waxed_oxidized_cut_copper" : "minecraft:glass");
            }
        // lanterneau au sommet de la verriere
        for (int y = 85; y <= 87; y++)
            for (int x = -1; x <= 1; x++)
                for (int z = 0; z <= 2; z++) set(x, y, z, y == 87 ? "minecraft:waxed_oxidized_cut_copper" : (Math.abs(x) == 1 && (z == 0 || z == 2) ? "minecraft:waxed_oxidized_copper" : "minecraft:glass"));
        set(0, 88, 1, "minecraft:waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]");
        set(0, 89, 1, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    /** Lanterneau : plafond ouvert sur (x1+1..x2-1, z1+1..z2-1), pyramide de verre a gradins, aretes en cuivre. */
    private void skylight(int x1, int z1, int x2, int z2) {
        for (int x = x1 + 1; x < x2; x++) for (int z = z1 + 1; z < z2; z++) set(x, T, z, B.AIR);
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) if (x == x1 || x == x2 || z == z1 || z == z2) set(x, T, z, "minecraft:quartz_bricks");
        for (int k = 0; ; k++) {
            int ax = x1 + k, bx = x2 - k, az = z1 + k, bz = z2 - k;
            if (ax > bx || az > bz) break;
            for (int x = ax; x <= bx; x++)
                for (int z = az; z <= bz; z++) {
                    boolean edge = x == ax || x == bx || z == az || z == bz;
                    if (!edge && !(ax + 1 > bx - 1 || az + 1 > bz - 1)) { set(x, T + 1 + k, z, B.AIR); continue; }
                    boolean arete = (x == ax || x == bx) && (z == az || z == bz);
                    set(x, T + 1 + k, z, arete || k == 0 ? "minecraft:waxed_oxidized_cut_copper" : "minecraft:glass");
                }
        }
    }

    private void balustrade(int x, int z) {
        boolean ped = Math.floorMod(x + z, 6) == 0 || Math.abs(x) == 33 && Math.abs(z) == 25;
        set(x, T + 1, z, ped ? "minecraft:quartz_bricks" : "minecraft:diorite_wall");
        set(x, T + 2, z, ped ? "minecraft:chiseled_quartz_block" : slab("minecraft:smooth_quartz_slab", "bottom"));
        if (ped && Math.floorMod(x * 3 + z, 4) == 0) set(x, T + 3, z, "minecraft:decorated_pot[facing=south,cracked=false,waterlogged=false]");
    }

    // ================================================================ portique

    private void portico() {
        // sol, 4 colonnes cannelees, entablement, fronton, marches
        for (int x = -12; x <= 12; x++) for (int z = 27; z <= 32; z++) set(x, G, z, (x + z) % 2 == 0 ? "minecraft:polished_diorite" : "minecraft:polished_andesite");
        for (int x = -13; x <= 13; x++) set(x, G, 33, stairs("minecraft:polished_andesite_stairs", Dir.NORTH, false));
        for (int cx : new int[]{-10, -4, 4, 10}) column(cx, 31, G + 1, T - 2);
        for (int cx : new int[]{-10, -4, 4, 10}) for (int y = G + 1; y < T - 1; y++) setIfAir(cx, y, 27, y == G + 1 ? "minecraft:quartz_bricks" : "minecraft:quartz_pillar[axis=y]");
        for (int x = -12; x <= 12; x++)
            for (int z = 27; z <= 32; z++) {
                set(x, T - 1, z, z == 32 ? (x % 2 == 0 ? "minecraft:chiseled_quartz_block" : stairs("minecraft:quartz_stairs", Dir.NORTH, true)) : "minecraft:quartz_bricks");
                set(x, T, z, z == 32 && x % 2 == 0 ? facing("minecraft:light_blue_glazed_terracotta", Dir.SOUTH) : "minecraft:smooth_quartz");
                set(x, T + 1, z, z == 32 ? stairs("minecraft:quartz_stairs", Dir.NORTH, true) : "minecraft:smooth_quartz");
            }
        // fronton triangulaire (toit a deux pans le long de z, faitage en x 0)
        for (int x = -13; x <= 13; x++) {
            int y = T + 2 + (13 - Math.abs(x)) / 2;
            for (int z = 26; z <= 33; z++) {
                set(x, y, z, x == 0 ? "minecraft:smooth_quartz" : stairs("minecraft:smooth_quartz_stairs", x < 0 ? Dir.EAST : Dir.WEST, false));
                if (z == 32) for (int yy = T + 2; yy < y; yy++) set(x, yy, z, Math.abs(x) <= 3 && yy == T + 3 ? "minecraft:gold_block" : "minecraft:smooth_quartz");
            }
        }
        set(0, T + 6, 33, "minecraft:gold_block");
        // lanternes du portique
        for (int x : new int[]{-7, 0, 7}) { set(x, T - 2, 29, chain('y')); set(x, T - 3, 29, hanging("minecraft:lantern")); }
    }

    /** Colonne : base 3 x 3, fut en croix cannele, chapiteau 3 x 3. */
    private void column(int cx, int cz, int y0, int y1) {
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int z = cz - 1; z <= cz + 1; z++) {
                set(x, y0, z, "minecraft:quartz_bricks");
                set(x, y1, z, "minecraft:chiseled_quartz_block");
            }
        for (int y = y0 + 1; y < y1; y++) {
            set(cx, y, cz, "minecraft:quartz_pillar[axis=y]");
            // cannelures : escaliers tournes vers le fut, alternes haut / bas
            for (Dir d : Dir.values()) set(cx + d.dx, y, cz + d.dz, y == y0 + 1 ? "minecraft:smooth_quartz" : stairs("minecraft:smooth_quartz_stairs", d.opposite(), y % 2 != 0));
        }
    }

    // ================================================================ vestibule (rez, sud)

    private void vestibule() {
        // banque d'accueil en arc, colonnes, vestiaire, boutique
        for (int x = -4; x <= 4; x++) {
            int z = 18 + (Math.abs(x) >= 3 ? 1 : 0);
            set(x, G + 1, z, "minecraft:polished_diorite");
            set(x, G + 2, z, slab("minecraft:dark_oak_slab", "top"));
        }
        set(-1, G + 3, 18, standing("minecraft:lantern"));
        set(2, G + 3, 18, "minecraft:potted_flowering_azalea_bush");
        set(0, G + 1, 16, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
        for (int[] c : new int[][]{{-7, 17}, {7, 17}, {-7, 22}, {7, 22}}) for (int y = G + 1; y < U; y++) set(c[0], y, c[1], y == U - 1 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
        // vestiaire (ouest) : penderies
        for (int z = 16; z <= 23; z += 2) {
            set(-10, G + 1, z, "minecraft:dark_oak_fence");
            set(-10, G + 2, z, "minecraft:dark_oak_fence");
            set(-10, G + 3, z, "minecraft:" + (z % 4 == 0 ? "brown" : "gray") + "_banner[rotation=4]");
        }
        // boutique (est) : etageres de souvenirs
        for (int z = 16; z <= 23; z++) {
            set(10, G + 1, z, z % 3 == 0 ? "minecraft:chiseled_bookshelf[facing=west,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]" : "minecraft:bookshelf");
            set(10, G + 2, z, z % 2 == 0 ? candles(2, "white") : "minecraft:decorated_pot[facing=west,cracked=false,waterlogged=false]");
        }
        set(9, G + 1, 23, "minecraft:barrel[facing=up,open=false]");
        chandelier(0, U - 1, 21, 1, true, "minecraft:dark_oak_trapdoor", "minecraft:lantern");
        rug(-3, 20, 3, 24, G + 1, "minecraft:red_carpet", "minecraft:black_carpet");
        bigPlant(-5, G + 1, 23, "dark_oak");
        bigPlant(5, G + 1, 23, "dark_oak");
        // plan du musee au mur nord (terre cuite)
        for (int x = -3; x <= 3; x++) for (int y = G + 2; y <= G + 4; y++) setIfAir(x, y, 15, x == 0 || y == G + 3 ? "minecraft:orange_terracotta" : "minecraft:white_terracotta");
    }

    // ================================================================ mineraux (rez, nord)

    private void minerals() {
        // 1.9.1 : geode coupee en deux deplacee contre le mur nord, a l'est de la porte (x 4..8, z -24..-21),
        // ouverte au sud, et reduite : au centre elle bouchait la grande porte nord (x -1..1) et l'axe porte -> arcs
        // du hall. Restent libres : l'allee x -3..2 et le couloir x 9..11 de l'arche est (z -21..-19).
        final double gx = 6, gz = -22;
        Sdf outer = Sdf.ellipsoid(gx, G + 3, gz, 2.8, 3.2, 2.4);
        Sdf inner = Sdf.ellipsoid(gx, G + 3, gz, 1.7, 2.2, 1.4);
        outer.minus(inner).intersect(Sdf.box(3, G + 1, -24, 9, G + 7, -21)).fill(s, (x, y, z) -> {
            double d = Math.sqrt((x - gx) * (x - gx) / 7.84 + (y - G - 3) * (y - G - 3) / 10.24 + (z - gz) * (z - gz) / 5.76);
            return d > 0.92 ? "minecraft:smooth_basalt" : d > 0.8 ? "minecraft:calcite" : (Math.floorMod(x + y + z, 5) == 0 ? "minecraft:budding_amethyst" : "minecraft:amethyst_block");
        });
        for (int[] c : new int[][]{{5, G + 1, -22}, {7, G + 1, -22}, {6, G + 1, -23}, {5, G + 2, -23}, {7, G + 3, -23}})
            if (!isAir(c[0], c[1] - 1, c[2])) setIfAir(c[0], c[1], c[2], "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        set(6, G, -22, "minecraft:sea_lantern");
        // vitrines a minerais sur socles (4 a l'ouest, 2 a l'est devant la geode)
        String[] ores = {"diamond_ore", "emerald_ore", "gold_ore", "lapis_ore", "redstone_ore", "deepslate_diamond_ore", "copper_ore", "raw_gold_block"};
        int i = 0;
        for (int[] v : new int[][]{{-9, -22}, {-6, -22}, {-9, -17}, {-6, -17}, {7, -15}, {10, -15}}) {
            int x = v[0], z = v[1];
            set(x, G + 1, z, "minecraft:polished_deepslate");
            set(x, G + 2, z, "minecraft:" + ores[i++ % ores.length]);
            set(x, G + 3, z, "minecraft:glass");
        }
        // cuivre en degrade d'oxydation le long du mur nord, meteorite
        String[] cu = {"waxed_copper_block", "waxed_exposed_copper", "waxed_weathered_copper", "waxed_oxidized_copper"};
        for (int x = -10; x <= 10; x++) if (Math.abs(x) >= 5 && x % 2 != 0 && (x < 3 || x > 9)) set(x, G + 1, -24, "minecraft:" + cu[Math.min(3, (Math.abs(x) - 5) / 2)]);
        Shapes.sphere(s, -8, G + 2, -15, 1.1, (x, y, z) -> Math.floorMod(x + y + z, 3) == 0 ? "minecraft:crying_obsidian" : "minecraft:obsidian");
        set(-8, G, -15, "minecraft:magma_block");
        for (int x = -11; x <= 11; x += 11) lampUnder(x, -19, "minecraft:soul_lantern");
        lampUnder(4, -16, "minecraft:lantern");
        lampUnder(-4, -22, "minecraft:lantern");
        // stalagmites dans les coins sud (avant : devant les arcs x -5 et 5)
        for (int[] p : new int[][]{{10, -14}, {-10, -14}}) set(p[0], G + 1, p[1], "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]");
    }

    // ================================================================ Egypte (rez, nord-ouest)

    private void egypt() {
        // pyramide a gradins (7 x 7) au centre, pointe doree
        int px = -21, pz = -12;
        for (int lvl = 0; lvl < 4; lvl++) {
            int r = 3 - lvl;
            for (int x = px - r; x <= px + r; x++)
                for (int z = pz - r; z <= pz + r; z++) {
                    boolean edge = Math.abs(x - px) == r || Math.abs(z - pz) == r;
                    set(x, G + 1 + lvl, z, edge && r > 0 ? stairs("minecraft:sandstone_stairs", Math.abs(x - px) == r ? (x > px ? Dir.WEST : Dir.EAST) : (z > pz ? Dir.NORTH : Dir.SOUTH), false) : "minecraft:cut_sandstone");
                }
        }
        set(px, G + 5, pz, "minecraft:gold_block");
        // sarcophages
        sarcophagus(-16, -21, Dir.SOUTH);
        sarcophagus(-29, -8, Dir.EAST);
        // obelisques
        for (int[] o : new int[][]{{-15, -4}, {-29, -16}, {-15, -16}}) {
            for (int y = G + 1; y <= G + 4; y++) set(o[0], y, o[1], y == G + 1 ? "minecraft:smooth_sandstone" : "minecraft:chiseled_sandstone");
            set(o[0], G + 5, o[1], "minecraft:gold_block");
        }
        // statue d'Anubis
        int ax = -28, az = -3;
        set(ax, G + 1, az, "minecraft:smooth_sandstone");
        set(ax, G + 2, az, "minecraft:black_terracotta");
        set(ax, G + 3, az, "minecraft:blackstone");
        set(ax, G + 4, az, "minecraft:blackstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        set(ax, G + 5, az, "minecraft:polished_blackstone_button[face=floor,facing=north,powered=false]");
        set(ax, G + 3, az + 1, stairs("minecraft:smooth_sandstone_stairs", Dir.NORTH, true));
        // frise de hieroglyphes (terre cuite emaillee) et braseros
        for (int x = -31; x <= -14; x++) frieze(x, G + 4, -1, x, Dir.NORTH);
        for (int z = -23; z <= -2; z++) frieze(IX1, G + 4, z, z, Dir.EAST);
        for (int x = -31; x <= -14; x++) frieze(x, G + 4, IZ1, x, Dir.SOUTH);
        for (int[] b : new int[][]{{-25, -19}, {-17, -9}, {-25, -6}}) {
            set(b[0], G + 1, b[1], "minecraft:smooth_sandstone_slab[type=double,waterlogged=false]");
            set(b[0], G + 2, b[1], "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        }
        // vases et palmiers en pots
        for (int[] v : new int[][]{{-14, -23}, {-14, -13}, {-31, -2}, {-31, -12}}) set(v[0], G + 1, v[1], "minecraft:decorated_pot[facing=east,cracked=false,waterlogged=false]");
        for (int[] p : new int[][]{{-19, -2}, {-24, -23}}) bigPlant(p[0], G + 1, p[1], "jungle");
        for (int z = -21; z <= -4; z += 6) lampUnder(-21, z, "minecraft:lantern");
    }

    /** Frise egyptienne : remplace la cimaise (trappe) ou l'air contre le mur par de la terre cuite emaillee. */
    private void frieze(int x, int y, int z, int t, Dir faces) {
        String cur = get(x, y, z);
        if (!B.isAir(cur) && !cur.contains("trapdoor")) return;
        String[] k = {"minecraft:orange_glazed_terracotta", "minecraft:blue_glazed_terracotta", "minecraft:yellow_glazed_terracotta"};
        set(x, y, z, facing(k[Math.floorMod(t, 3)], faces));
    }

    /** Lanterne sous le plafond du rez : directement sous une poutre, sinon au bout d'une chaine. */
    private void lampUnder(int x, int z, String lantern) {
        if (isAir(x, U - 1, z)) hangLamp(x, U, z, lantern);
        else setIfAir(x, U - 2, z, hanging(lantern));
    }

    private void sarcophagus(int x, int z, Dir along) {
        for (int i = 0; i < 3; i++) {
            int xx = x + along.dx * i, zz = z + along.dz * i;
            set(xx, G + 1, zz, "minecraft:lapis_block");
            set(xx, G + 2, zz, i == 0 ? "minecraft:gold_block" : "minecraft:" + (i == 1 ? "yellow" : "blue") + "_glazed_terracotta");
        }
    }

    // ================================================================ armures (rez, sud-ouest)

    private void armory() {
        // chevaliers en armure sur socles, en deux rangs
        for (int z = 4; z <= 22; z += 6) {
            knight(-29, z, Dir.EAST);
            knight(-16, z, Dir.WEST);
        }
        // maquette de chateau sur table
        int cx = -23, cz = 13;
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) set(x, G + 1, z, "minecraft:spruce_planks");
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
            boolean wall = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
            if (wall) set(x, G + 2, z, (Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2) ? "minecraft:stone_bricks" : "minecraft:stone_brick_wall");
        }
        set(cx, G + 2, cz, "minecraft:chiseled_stone_bricks");
        set(cx, G + 3, cz, "minecraft:red_banner[rotation=0]");
        // canon
        // 1.9.1 : roues (trappes) plaquees contre un essieu plein (avant : contre le vide, a cote d'un muret)
        set(-23, G + 1, 4, "minecraft:dark_oak_trapdoor[facing=west,half=bottom,open=true,waterlogged=false,powered=false]");
        set(-22, G + 1, 4, "minecraft:polished_blackstone");
        set(-22, G + 2, 4, "minecraft:polished_blackstone_bricks");
        set(-21, G + 2, 4, "minecraft:polished_blackstone_stairs[facing=west,half=top,shape=straight,waterlogged=false]");
        set(-21, G + 1, 4, "minecraft:dark_oak_trapdoor[facing=east,half=bottom,open=true,waterlogged=false,powered=false]");
        // ratelier d'armes au mur, enclume, meule
        for (int z = 2; z <= 23; z += 3) {
            setIfAir(IX1, G + 3, z, "minecraft:lightning_rod[facing=east,powered=false,waterlogged=false]");
            setIfAir(IX1, G + 4, z, "minecraft:" + (z % 2 == 0 ? "red" : "blue") + "_wall_banner[facing=east]");
        }
        set(-14, G + 1, 23, "minecraft:anvil[facing=north]");
        set(-14, G + 1, 2, "minecraft:grindstone[face=floor,facing=north]");
        set(-31, G + 1, 23, "minecraft:smithing_table");
        for (int z = 6; z <= 20; z += 7) lampUnder(-23, z, "minecraft:lantern");
        rug(-25, 18, -21, 22, G + 1, "minecraft:red_carpet", "minecraft:gray_carpet");
    }

    private void knight(int x, int z, Dir faces) {
        set(x, G + 1, z, "minecraft:polished_andesite");
        set(x, G + 2, z, "minecraft:iron_bars");
        set(x, G + 3, z, "minecraft:iron_block");
        set(x, G + 4, z, "minecraft:cauldron");
        // 1.9.1 : la lance (2 paratonnerres) se tient debout sur le sol a cote du chevalier, au lieu de flotter a
        // hauteur de main ; le bouclier (trappe) se plaque contre l'armure (panneau du cote oppose a facing)
        setIfAir(x + faces.cw().dx, G + 1, z + faces.cw().dz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        setIfAir(x + faces.cw().dx, G + 2, z + faces.cw().dz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        setIfAir(x + faces.ccw().dx, G + 3, z + faces.ccw().dz, trap("minecraft:spruce_trapdoor", faces.ccw(), false, true));
    }

    // ================================================================ ocean (rez, nord-est)

    private void ocean() {
        // aquariums encastres le long du mur nord (vitre en z -21, eau z -24..-22)
        for (int x = 14; x <= 28; x++) {
            boolean frame = (x - 14) % 5 == 0 || x == 28;
            for (int y = G + 1; y <= U - 1; y++) {
                if (frame) { for (int z = -24; z <= -21; z++) set(x, y, z, "minecraft:dark_prismarine"); continue; }
                set(x, y, -21, y == U - 1 ? "minecraft:dark_prismarine" : "minecraft:glass");
                // fond plein (le plan des fenetres est bouche : l'eau ne doit jamais toucher une embrasure)
                set(x, y, -24, "minecraft:prismarine_bricks");
                for (int z = -23; z <= -22; z++) set(x, y, z, y == U - 1 ? "minecraft:prismarine_bricks" : B.WATER);
            }
            if (!frame) {
                set(x, G, -23, "minecraft:sand");
                set(x, G, -22, "minecraft:sea_lantern");
                String[] life = {"minecraft:seagrass", "minecraft:tube_coral[waterlogged=true]", "minecraft:brain_coral[waterlogged=true]",
                        "minecraft:sea_pickle[pickles=3,waterlogged=true]", "minecraft:fire_coral[waterlogged=true]", "minecraft:kelp_plant"};
                set(x, G + 1, -23, life[Math.floorMod(x, life.length)]);
                if (x % 3 == 0) { set(x, G + 1, -22, "minecraft:kelp_plant"); set(x, G + 2, -22, "minecraft:kelp_plant"); set(x, G + 3, -22, "minecraft:kelp[age=25]"); }
            }
        }
        // aquarium cylindrique au centre
        int cx = 22, cz = -11;
        for (int y = G + 1; y <= U - 1; y++)
            for (int x = cx - 3; x <= cx + 3; x++)
                for (int z = cz - 3; z <= cz + 3; z++) {
                    double d = Math.hypot(x - cx, z - cz);
                    if (d > 2.6) continue;
                    if (y == U - 1 || y == G + 1) set(x, y, z, "minecraft:dark_prismarine");
                    else if (d > 1.8) set(x, y, z, "minecraft:glass");
                    else set(x, y, z, B.WATER);
                }
        set(cx, G + 2, cz, "minecraft:brain_coral_block");
        set(cx, G + 3, cz, "minecraft:tube_coral_fan[waterlogged=true]");
        set(cx + 1, G + 2, cz, "minecraft:sea_pickle[pickles=4,waterlogged=true]");
        set(cx, G + 1, cz, "minecraft:sea_lantern");
        // squelette de baleine suspendu sous les poutres (y 69-70, hors d'atteinte)
        for (int x = 15; x <= 30; x++) set(x, 70, -11, "minecraft:bone_block[axis=x]");
        for (int x = 18; x <= 27; x += 2) for (int dz : new int[]{-1, 1}) set(x, 69, -11 + dz, "minecraft:bone_block[axis=y]");
        for (int x = 28; x <= 30; x++) for (int dz = -1; dz <= 1; dz++) set(x, 69, -11 + dz, "minecraft:bone_block[axis=x]");
        set(15, 69, -12, "minecraft:bone_block[axis=z]");
        set(15, 69, -10, "minecraft:bone_block[axis=z]");
        // ancre, scaphandre, banc, lumieres
        for (int y = G + 1; y <= G + 3; y++) set(15, y, -3, y == G + 3 ? "minecraft:iron_block" : "minecraft:iron_chain[axis=y]");
        set(14, G + 1, -3, "minecraft:anvil[facing=east]");
        set(16, G + 1, -3, "minecraft:anvil[facing=east]");
        set(29, G + 1, -4, "minecraft:polished_andesite");
        set(29, G + 2, -4, "minecraft:cyan_terracotta");
        set(29, G + 3, -4, "minecraft:waxed_copper_bulb[lit=true,powered=false]");
        bench(22, G + 1, -5, Dir.NORTH, "dark_oak");
        for (int[] l : new int[][]{{17, -17}, {27, -17}, {17, -6}, {27, -6}}) lampUnder(l[0], l[1], "minecraft:soul_lantern");
    }

    // ================================================================ tresor (rez, sud-est)

    private void treasure() {
        // porte de coffre-fort ronde au mur est
        int dz = 12;
        for (int y = G + 1; y <= U - 1; y++)
            for (int z = dz - 3; z <= dz + 3; z++) {
                double d = Math.hypot(z - dz, y - (G + 3.5));
                if (d <= 2.9) set(IX2, y, z, d <= 0.9 ? "minecraft:gold_block" : (d <= 2.0 ? "minecraft:iron_block" : "minecraft:polished_blackstone"));
            }
        for (int z = dz - 2; z <= dz + 2; z += 2) setIfAir(IX2 - 1, G + 3, z, "minecraft:lever[face=wall,facing=west,powered=false]");
        // casiers de depot (trappes de fer) au mur sud
        for (int x = 14; x <= 22; x++) for (int y = G + 1; y <= G + 4; y++) if (isAir(x, y, IZ2)) set(x, y, IZ2, y == G + 1 ? "minecraft:iron_block" : "minecraft:iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]");
        // couronne sous cloche au centre, cercle de lasers (2 ouvertures)
        int cx = 22, cz = 11;
        set(cx, G + 1, cz, "minecraft:gold_block");
        set(cx, G + 2, cz, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        set(cx, G + 3, cz, "minecraft:glass");
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d < 2.5 || d > 3.2) continue;
                if (Math.abs(x - cx) <= 0 || Math.abs(z - cz) <= 0) continue;
                boolean post = Math.abs(x - cx) == Math.abs(z - cz);
                set(x, G + 1, z, post ? "minecraft:iron_bars" : "minecraft:red_stained_glass_pane");
            }
        // tas d'or et vitrines a joyaux
        for (int[] p : new int[][]{{30, 4}, {31, 5}, {30, 22}, {31, 21}})
            set(p[0], G + 1, p[1], (p[0] + p[1]) % 2 == 0 ? "minecraft:gold_block" : "minecraft:raw_gold_block");
        for (int[] v : new int[][]{{16, 6}, {16, 16}, {28, 16}, {28, 6}}) {
            set(v[0], G + 1, v[1], "minecraft:polished_blackstone_bricks");
            set(v[0], G + 2, v[1], (v[0] + v[1]) % 3 == 0 ? "minecraft:emerald_block" : "minecraft:diamond_block");
            set(v[0], G + 3, v[1], "minecraft:glass");
        }
        // velours rouge et lustres
        rug(18, 2, 26, 7, G + 1, "minecraft:yellow_carpet", "minecraft:red_carpet");
        for (int[] l : new int[][]{{18, 18}, {26, 4}}) chandelier(l[0], U - 1, l[1], 1, false, "minecraft:dark_oak_trapdoor", "minecraft:lantern");
    }

    // ================================================================ peinture (etage, nord-ouest)

    private void paintings() {
        int y0 = U + 2;
        // tableaux sur les murs (toile en terre cuite / beton, cadre en bois sombre en saillie)
        int k = 0;
        for (int x = -30; x <= -14; x += 4) { painting(x, y0, IZ1 - 1, Dir.SOUTH, k++); }
        for (int z = -18; z <= -2; z += 8) painting(IX1 - 1, y0, z, Dir.EAST, k++);
        // cimaises (cloisons) au milieu, tableaux des deux cotes
        for (int x = -27; x <= -19; x++)
            for (int y = U + 1; y <= U + 4; y++) set(x, y, -12, y == U + 4 ? slab("minecraft:dark_oak_slab", "bottom") : "minecraft:red_terracotta");
        painting(-25, y0 - 1, -12, Dir.NORTH, k++);
        painting(-21, y0 - 1, -12, Dir.SOUTH, k++);
        // bancs, pupitre, lampes
        for (int z : new int[]{-18, -7}) {
            for (int x = -24; x <= -22; x++) set(x, U + 1, z, slab("minecraft:dark_oak_slab", "top"));
        }
        set(-14, U + 1, -2, "minecraft:lectern[facing=west,has_book=false,powered=false]");
        bigPlant(-31, U + 1, -2, "dark_oak");
        for (int[] l : new int[][]{{-29, -18}, {-16, -18}, {-29, -5}, {-16, -5}}) { setIfAir(l[0], T - 1, l[1], chain('y')); setIfAir(l[0], T - 2, l[1], hanging("minecraft:lantern")); }
        rug(-27, -19, -19, -17, U + 1, "minecraft:red_carpet", "minecraft:brown_carpet");
    }

    /** Tableau 3 x 2 contre un mur (face vers faces) : cadre de trappes, toile de couleurs, lampe au-dessus. */
    private void painting(int wx, int y, int wz, Dir faces, int kind) {
        Dir along = faces.cw();
        String[][] art = {
                {"light_blue", "light_blue", "yellow", "green", "lime", "green"},      // paysage au soleil
                {"blue", "cyan", "light_blue", "blue", "white", "blue"},               // marine
                {"black", "yellow", "black", "orange", "red", "orange"},               // nuit etoilee
                {"pink", "white", "pink", "purple", "magenta", "purple"},              // fleurs
                {"brown", "orange", "brown", "black", "brown", "black"},               // portrait
                {"white", "red", "white", "blue", "white", "yellow"},                  // abstrait
        };
        String[] a = art[Math.floorMod(kind, art.length)];
        int x0 = wx + faces.dx, z0 = wz + faces.dz;
        for (int i = -1; i <= 1; i++)
            for (int j = 0; j <= 1; j++) {
                int x = x0 + along.dx * i, z = z0 + along.dz * i;
                set(x, y + j, z, "minecraft:" + a[(1 - j) * 3 + (i + 1)] + (kind % 2 == 0 ? "_terracotta" : "_concrete"));
            }
        for (int i = -2; i <= 2; i++) {
            int x = x0 + along.dx * i, z = z0 + along.dz * i;
            // cadre : montants (trappes ouvertes plaquees au mur), traverses (trappes fermees)
            if (Math.abs(i) == 2) for (int j = 0; j <= 1; j++) setIfAir(x, y + j, z, trap("minecraft:dark_oak_trapdoor", faces, false, true));
            setIfAir(x, y + 2, z, trap("minecraft:dark_oak_trapdoor", faces, false, false));
            setIfAir(x, y - 1, z, trap("minecraft:dark_oak_trapdoor", faces, true, false));
        }
        setIfAir(x0 + faces.dx, y + 2, z0 + faces.dz, "minecraft:end_rod[facing=" + faces.id() + "]");
    }

    // ================================================================ sculptures (etage, sud-ouest)

    private void sculptures() {
        int y = U + 1;
        // statues de marbre sur socles (formes douces)
        int[][] spots = {{-28, 5}, {-17, 5}, {-28, 20}, {-17, 20}};
        for (int i = 0; i < spots.length; i++) statue(spots[i][0], y, spots[i][1], i);
        // grande statue sombre au centre (« Le Penseur »)
        int cx = -22, cz = 13;
        fill(cx - 1, y, cz - 1, cx + 1, y, cz + 1, "minecraft:polished_andesite");
        Sdf body = Sdf.ellipsoid(cx, y + 2.2, cz, 1.0, 1.4, 0.9).union(Sdf.sphere(cx, y + 4.0, cz - 0.3, 0.75))
                .union(Sdf.box(cx - 1, y + 1, cz - 1, cx + 1, y + 1.4, cz + 0.4));
        body.fill(s, Mat.of("minecraft:polished_deepslate"));
        // socles de bustes le long des murs
        for (int z = 3; z <= 23; z += 5) { bust(IX1 + 1, y, z); bust(-14, y, z); }
        bigPlant(-31, y, 23, "spruce");
        bigPlant(-14, y, 2, "spruce");
        for (int[] l : new int[][]{{-29, 9}, {-16, 9}, {-29, 17}, {-16, 17}}) { setIfAir(l[0], T - 1, l[1], chain('y')); setIfAir(l[0], T - 2, l[1], hanging("minecraft:lantern")); }
    }

    private void statue(int x, int y, int z, int kind) {
        set(x, y, z, "minecraft:quartz_bricks");
        set(x, y + 1, z, "minecraft:chiseled_quartz_block");
        Mat m = Mat.of(kind % 2 == 0 ? "minecraft:calcite" : "minecraft:smooth_quartz");
        Sdf f;
        if (kind == 0) f = Sdf.ellipsoid(x, y + 3, z, 0.7, 1.2, 0.6).union(Sdf.sphere(x, y + 4.6, z, 0.55));
        else if (kind == 1) f = Sdf.ellipsoid(x, y + 2.8, z, 0.9, 0.9, 0.9).union(Sdf.ellipsoid(x, y + 4.1, z, 0.5, 0.8, 0.5));
        else if (kind == 2) f = Sdf.box(x, y + 2, z, x, y + 4, z).union(Sdf.sphere(x, y + 4.5, z, 0.6));
        else f = Sdf.ellipsoid(x, y + 3.2, z, 0.6, 1.3, 0.6);
        f.fill(s, m, x - 1, y + 2, z - 1, x + 1, y + 5, z + 1);
        setIfAir(x, y + 2, z, kind % 2 == 0 ? "minecraft:calcite" : "minecraft:smooth_quartz");
    }

    // ================================================================ astronomie (etage, nord-est)

    private void astronomy() {
        // plafond etoile (deja noir + lumieres) ; systeme solaire suspendu
        int sx = 22, sy = 75, sz = -12;
        Shapes.sphere(s, sx, sy, sz, 1.4, Mat.of("minecraft:shroomlight"));
        for (int y = sy + 2; y < T; y++) set(sx, y, sz, chain('y'));
        String[] planets = {"red_terracotta", "orange_concrete", "blue_concrete", "brown_terracotta", "yellow_terracotta", "light_blue_concrete", "white_concrete"};
        for (int i = 0; i < planets.length; i++) {
            double a = i * 0.9 + 0.4, r = 3 + i * 1.2;
            int x = sx + (int) Math.round(r * Math.cos(a)), z = sz + (int) Math.round(r * Math.sin(a) * 0.8);
            if (x < 14 || x > 31 || z < -23 || z > -2) continue;
            int y = 75 - (i % 2);
            for (int yy = y + 1; yy < T; yy++) set(x, yy, z, chain('y'));
            set(x, y, z, "minecraft:" + planets[i]);
            if (i == 4) { set(x + 1, y, z, slab("minecraft:smooth_sandstone_slab", "bottom")); set(x - 1, y, z, slab("minecraft:smooth_sandstone_slab", "bottom")); }
        }
        // telescope en laiton sur trepied, globe celeste, carte du ciel
        int tx = 28, tz = -4;
        set(tx, U + 1, tz, "minecraft:spruce_fence");
        set(tx, U + 2, tz, "minecraft:waxed_cut_copper");
        set(tx, U + 3, tz - 1, "minecraft:lightning_rod[facing=north,powered=false,waterlogged=false]");
        set(tx, U + 3, tz, "minecraft:waxed_cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
        set(16, U + 1, -20, "minecraft:dark_oak_fence");
        set(16, U + 2, -20, "minecraft:conduit[waterlogged=false]");
        for (int x = 15; x <= 29; x++) for (int y = U + 2; y <= U + 5; y++) if (isAir(x, y, IZ1)) set(x, y, IZ1, Math.floorMod(x * 5 + y * 3, 11) == 0 ? "minecraft:white_concrete" : "minecraft:black_concrete");
        // meteorite et gradins de projection
        for (int z = -8; z <= -5; z++) for (int x = 15; x <= 19; x++) if (z == -6 || z == -8) set(x, U + 1, z, stairs("minecraft:dark_oak_stairs", Dir.NORTH, false));
        Shapes.sphere(s, 30, U + 2, -21, 1.0, Mat.of("minecraft:blackstone"));
        set(30, U + 1, -21, "minecraft:magma_block");
    }

    // ================================================================ Asie (etage, sud-est)

    private void asia() {
        int y = U + 1;
        // bonsai (cerisier) sur socles
        for (int[] b : new int[][]{{16, 4}, {29, 18}}) {
            set(b[0], y, b[1], "minecraft:dark_oak_planks");
            new Tree("minecraft:cherry_log", "minecraft:cherry_leaves", null).height(4).crown(1.6).roots(false).place(s, b[0], y + 1, b[1], b[0] * 31L + b[1]);
        }
        // pagode miniature
        int px = 22, pz = 14;
        fill(px - 2, y, pz - 2, px + 2, y, pz + 2, "minecraft:polished_andesite");
        for (int lvl = 0; lvl < 3; lvl++) {
            int yy = y + 1 + lvl * 2, r = 2 - (lvl == 2 ? 1 : 0);
            set(px, yy, pz, "minecraft:red_terracotta");
            for (int x = px - r; x <= px + r; x++) for (int z = pz - r; z <= pz + r; z++) if (Math.abs(x - px) == r || Math.abs(z - pz) == r) set(x, yy + 1, z, slab("minecraft:deepslate_tile_slab", "bottom"));
            set(px, yy + 1, pz, "minecraft:red_terracotta");
        }
        set(px, y + 7, pz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        // armure de samourai
        set(28, y, 6, "minecraft:dark_oak_planks");
        set(28, y + 1, 6, "minecraft:red_nether_bricks");
        set(28, y + 2, 6, "minecraft:red_nether_brick_wall");
        set(28, y + 3, 6, "minecraft:black_terracotta");
        // paravents (toile blanche, cadre en cerisier)
        for (int z = 19; z <= 23; z++) {
            for (int yy = y; yy <= y + 2; yy++) set(18, yy, z, z % 2 == 0 ? "minecraft:stripped_cherry_wood[axis=y]" : "minecraft:white_wool");
        }
        // vases, lanternes de papier, tatamis
        for (int[] v : new int[][]{{14, 23}, {31, 10}, {31, 2}, {14, 12}}) set(v[0], y, v[1], "minecraft:decorated_pot[facing=north,cracked=false,waterlogged=false]");
        for (int[] l : new int[][]{{18, 8}, {26, 8}, {18, 16}, {26, 20}}) {
            set(l[0], T - 1, l[1], chain('y'));
            set(l[0], T - 2, l[1], "minecraft:white_stained_glass");
            set(l[0], T - 3, l[1], "minecraft:pearlescent_froglight");
        }
        for (int x = 23; x <= 27; x++) for (int z = 19; z <= 22; z++) if (!isAir(x, y - 1, z)) setIfAir(x, y, z, (x + z) % 2 == 0 ? "minecraft:green_carpet" : "minecraft:lime_carpet");
    }

    // ================================================================ cartes et globes (etage, nord)

    private void maps() {
        int y = U + 1;
        // grande carte du monde au mur nord (ocean + continents)
        for (int x = -10; x <= 10; x++)
            for (int yy = y + 1; yy <= y + 4; yy++) {
                if (!isAir(x, yy, IZ1)) continue;
                double n = noise.fbm2(x / 4.0, yy / 2.0, 2, 0.5, 2);
                set(x, yy, IZ1, n > 0.15 ? (n > 0.4 ? "minecraft:brown_terracotta" : "minecraft:lime_terracotta") : "minecraft:light_blue_terracotta");
            }
        // rayonnages, tables a cartes, globes
        for (int z = -22; z <= -15; z++) { if (z == -19 || z == -20 || z == -21) continue; set(-10, y, z, "minecraft:bookshelf"); set(-10, y + 1, z, "minecraft:bookshelf"); set(10, y, z, "minecraft:bookshelf"); set(10, y + 1, z, "minecraft:bookshelf"); }
        set(-3, y, -18, "minecraft:cartography_table");
        set(-2, y, -18, "minecraft:cartography_table");
        set(3, y, -18, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        for (int[] g : new int[][]{{-6, -22}, {6, -22}, {0, -15}}) { set(g[0], y, g[1], "minecraft:dark_oak_fence"); set(g[0], y + 1, g[1], "minecraft:conduit[waterlogged=false]"); }
        armchair(-6, y, -16, Dir.NORTH, "dark_oak");
        armchair(6, y, -16, Dir.NORTH, "dark_oak");
        rug(-4, -20, 4, -16, y, "minecraft:blue_carpet", "minecraft:light_blue_carpet");
    }

    // ================================================================ cafe (etage, sud)

    private void cafe() {
        int y = U + 1;
        for (int[] t : new int[][]{{-8, 17}, {-4, 21}, {3, 17}, {8, 21}, {-8, 23}}) cafeTable(t[0], y, t[1], "spruce", true);
        // comptoir
        for (int x = -2; x <= 4; x++) { set(x, y, 23, "minecraft:polished_andesite"); set(x, y + 1, 23, slab("minecraft:spruce_slab", "bottom")); }
        // 1.9.1 : meuble arriere sous la machine et le gateau (ils flottaient)
        set(0, y, 24, "minecraft:polished_andesite");
        set(1, y, 24, "minecraft:barrel[facing=up,open=false]");
        set(2, y, 24, "minecraft:polished_andesite");
        set(0, y + 1, 24, "minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=true,has_bottle_2=false]");
        set(2, y + 1, 24, "minecraft:cake");
        set(4, y + 1, 24, "minecraft:water_cauldron[level=2]");
        bigPlant(-10, y, 15, "spruce");
        bigPlant(10, y, 15, "spruce");
        chandelier(0, T - 1, 19, 1, true, "minecraft:spruce_trapdoor", "minecraft:lantern");
        set(-1, y, 15, "minecraft:potted_flowering_azalea_bush");
    }

    // ================================================================ parvis et fontaine

    private void plaza() {
        for (int x = -32; x <= 32; x++)
            for (int z = 33; z <= 49; z++) {
                double d = Math.hypot(x, z - 41);
                String b = PAVE.at(x, G, z);
                if (d <= 7.5 && (int) (angle01(x, z - 41) * 16) % 2 == 0) b = "minecraft:polished_andesite";
                if (Math.abs(x) <= 2 && z <= 49) b = "minecraft:smooth_stone";
                set(x, G, z, b);
            }
        // fontaine a vasques et statue
        int cx = 0, cz = 41;
        for (int x = cx - 5; x <= cx + 5; x++)
            for (int z = cz - 5; z <= cz + 5; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= 3.6) { set(x, G - 1, z, "minecraft:stone_bricks"); set(x, G, z, B.WATER); }
                else if (d <= 4.6) { set(x, G, z, "minecraft:polished_andesite"); set(x, G + 1, z, slab("minecraft:smooth_stone_slab", "bottom")); }
            }
        for (int y = G; y <= G + 2; y++) set(cx, y, cz, "minecraft:chiseled_stone_bricks");
        for (Dir d : Dir.values()) set(cx + d.dx, G + 2, cz + d.dz, "minecraft:water_cauldron[level=3]");
        Sdf.ellipsoid(cx, G + 4.2, cz, 0.6, 1.3, 0.6).union(Sdf.sphere(cx, G + 5.8, cz, 0.5)).fill(s, Mat.of("minecraft:waxed_oxidized_copper"));
        // parterres bordes de haies basses, fleurs
        for (int sx : new int[]{-1, 1}) {
            int x1 = sx < 0 ? -24 : 10, x2 = sx < 0 ? -10 : 24;
            for (int x = x1; x <= x2; x++)
                for (int z = 36; z <= 46; z++) {
                    boolean edge = x == x1 || x == x2 || z == 36 || z == 46;
                    boolean gap = z == 41 || Math.abs(x - (x1 + x2) / 2) <= 0;
                    set(x, G, z, GRASS.at(x, G, z));
                    if (edge && !gap) { set(x, G + 1, z, HEDGE.at(x, G + 1, z)); if ((x + z) % 4 == 0) set(x, G + 2, z, HEDGE.at(x, G + 2, z)); }
                    else if (!edge && h(x, 2, z) < 0.35) setIfAir(x, G + 1, z, h(x, 3, z) < 0.5 ? "minecraft:red_tulip" : "minecraft:white_tulip");
                }
            new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(7).crown(2.6).place(s, (x1 + x2) / 2, G + 1, 41, sx * 77L);
            set((x1 + x2) / 2 - 3, G + 1, 41, stairs("minecraft:spruce_stairs", Dir.EAST, false));
        }
        // lampadaires, bancs, mats a bannieres
        for (int[] l : new int[][]{{-6, 35}, {6, 35}, {-6, 47}, {6, 47}, {-28, 35}, {28, 35}, {-28, 47}, {28, 47}})
            lampPost(l[0], G + 1, l[1], 5, "minecraft:dark_oak_fence", "minecraft:polished_blackstone_wall", true);
        for (int x : new int[]{-16, 16}) { bench(x, G + 1, 34, Dir.SOUTH, "spruce"); bench(x, G + 1, 48, Dir.NORTH, "spruce"); }
        for (int x : new int[]{-30, 30}) {
            for (int y = G + 1; y <= G + 8; y++) set(x, y, 41, y == G + 8 ? "minecraft:gold_block" : "minecraft:polished_blackstone_wall");
            set(x, G + 7, 42, "minecraft:" + (x < 0 ? "red" : "blue") + "_wall_banner[facing=south]");
        }
        // marchand de glaces et abribus
        iceCream(-20, 48);
        for (int x = 20; x <= 24; x++) { set(x, G + 4, 48, slab("minecraft:smooth_quartz_slab", "bottom")); set(x, G + 1, 49, "minecraft:glass_pane"); }
        for (int x : new int[]{20, 24}) for (int y = G + 1; y <= G + 3; y++) set(x, y, 48, "minecraft:iron_bars");
        bench(22, G + 1, 48, Dir.SOUTH, "spruce");
    }

    private void iceCream(int x, int z) {
        for (int i = -1; i <= 1; i++) {
            set(x + i, G + 1, z, i == 0 ? "minecraft:white_terracotta" : "minecraft:pink_concrete");
            set(x + i, G + 4, z, i == 0 ? "minecraft:white_wool" : "minecraft:pink_wool");
        }
        set(x - 1, G + 2, z, "minecraft:spruce_fence"); set(x - 1, G + 3, z, "minecraft:spruce_fence");
        set(x + 1, G + 2, z, "minecraft:spruce_fence"); set(x + 1, G + 3, z, "minecraft:spruce_fence");
        set(x, G + 2, z, "minecraft:cake");
    }

    // ================================================================ jardins lateraux

    private void gardens() {
        // allees de gravier tout autour du batiment
        for (int x = -46; x <= 46; x++)
            for (int z = -39; z <= 48; z++) {
                boolean ring = (Math.abs(x) >= 37 && Math.abs(x) <= 39) || (z >= 29 && z <= 32 && Math.abs(x) > 13) || (z <= -28 && z >= -30);
                if (!ring) continue;
                if (z > 32 && Math.abs(x) <= 32) continue;
                if (Math.abs(x) <= 34 && z >= -26 && z <= 26) continue;
                set(x, G, z, GRAVEL.at(x, G, z));
            }
        for (int z = -13; z <= -10; z++) for (int x = -38; x <= -35; x++) set(x, G, z, GRAVEL.at(x, G, z));
        for (int z = 11; z <= 14; z++) for (int x = -38; x <= -35; x++) set(x, G, z, GRAVEL.at(x, G, z));
        for (int z = -13; z <= -10; z++) for (int x = 35; x <= 38; x++) set(x, G, z, GRAVEL.at(x, G, z));
        for (int z = 11; z <= 14; z++) for (int x = 35; x <= 38; x++) set(x, G, z, GRAVEL.at(x, G, z));
        // jardin des sculptures (ouest) et kiosque
        for (int[] st : new int[][]{{-43, -20}, {-43, 20}, {-43, 38}}) statue(st[0], G + 1, st[1], (st[1] + 40) % 4);
        gazebo(-43, 1);
        // jardin est : topiaire de dinosaure, bosquet, bancs
        int dx = 43, dz = -6;
        Sdf.ellipsoid(dx, G + 3, dz, 1.6, 1.4, 3.0).union(Sdf.ellipsoid(dx, G + 4.5, dz - 3.5, 1.0, 1.6, 1.0))
                .union(Sdf.ellipsoid(dx, G + 2.5, dz + 3.5, 0.8, 0.8, 2.0)).fill(s, HEDGE);
        for (int[] l : new int[][]{{-1, 1}, {1, 1}, {-1, -1}, {1, -1}}) for (int y = G + 1; y <= G + 2; y++) set(dx + l[0], y, dz + l[1] * 2, log("minecraft:oak_log", 'y'));
        bench(43, G + 1, 20, Dir.WEST, "spruce");
        bench(-43, G + 1, -30, Dir.EAST, "spruce");
        // arbres
        int[][] trees = {{-44, -35, 8}, {44, -35, 8}, {-44, 46, 7}, {44, 46, 7}, {-42, -10, 7}, {42, 30, 7}, {44, 4, 6}, {-36, 44, 6}, {36, 44, 6}, {-44, 12, 6}};
        long sd = 50;
        for (int[] t : trees) {
            if (!isAir(t[0], G + 1, t[1])) continue;
            new Tree(sd % 2 == 0 ? "minecraft:oak_log" : "minecraft:birch_log", sd % 2 == 0 ? "minecraft:oak_leaves" : "minecraft:birch_leaves", "minecraft:azalea_leaves")
                    .height(t[2]).crown(2.8).place(s, t[0], G + 1, t[1], sd++);
        }
        // massifs de fleurs le long des facades
        for (int x = -33; x <= 33; x++) for (int z : new int[]{27, -27}) {
            if (Math.abs(x) <= 13 && z > 0) continue;
            if (Math.abs(x) <= 2) continue;
            if (isAir(x, G + 1, z) && get(x, G, z).contains("grass")) set(x, G + 1, z, (x + z) % 3 == 0 ? "minecraft:rose_bush[half=lower]" : "minecraft:flowering_azalea");
            if (get(x, G + 1, z).contains("rose_bush")) set(x, G + 2, z, "minecraft:rose_bush[half=upper]");
        }
        // vegetation basse
        String[] low = {"minecraft:short_grass", "minecraft:short_grass", "minecraft:fern", "minecraft:dandelion", "minecraft:poppy"};
        for (int x = BX1; x <= BX2; x++)
            for (int z = BZ1; z <= BZ2; z++) {
                if (!get(x, G, z).contains("grass_block") || !isAir(x, G + 1, z)) continue;
                if (noise.uniform2(x / 4.0 + 3, z / 4.0 - 9) > 0.8 && h(x, 5, z) < 0.6) set(x, G + 1, z, low[(int) (h(x, 6, z) * low.length)]);
            }
    }

    private void gazebo(int cx, int cz) {
        for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 3; z <= cz + 3; z++) set(x, G, z, "minecraft:polished_andesite");
        for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) for (int y = G + 1; y <= G + 4; y++) set(cx + p[0], y, cz + p[1], "minecraft:birch_fence");
        RoofStyle rs = new RoofStyle().stairs("minecraft:waxed_oxidized_cut_copper_stairs").slab("minecraft:waxed_oxidized_cut_copper_slab")
                .ridge("minecraft:waxed_oxidized_cut_copper_slab").body(Mat.of("minecraft:waxed_oxidized_cut_copper")).gable(Mat.of("minecraft:waxed_oxidized_cut_copper")).overhang(1);
        Roof.build(s, rs, Roof.hip(cx - 3, cz - 3, cx + 3, cz + 3, G + 5));
        bench(cx, G + 1, cz - 2, Dir.SOUTH, "birch");
    }

    // ================================================================ cour de service (nord)

    private void backyard() {
        for (int x = -33; x <= 33; x++) for (int z = -39; z <= -28; z++) set(x, G, z, ASPHALT.at(x, G, z));
        // quai de chargement (pieds en 66) contre la facade nord, marches aux deux bouts
        for (int x = 12; x <= 24; x++) for (int z = -30; z <= -27; z++) set(x, G + 1, z, "minecraft:smooth_stone");
        for (int z = -30; z <= -27; z++) { set(11, G + 1, z, stairs("minecraft:stone_stairs", Dir.EAST, false)); set(25, G + 1, z, stairs("minecraft:stone_stairs", Dir.WEST, false)); }
        for (int x = 12; x <= 24; x++) set(x, G, -31, "minecraft:yellow_concrete");
        // camion de livraison
        truck(-14, -35);
        // caisses sur palettes, bennes, cabine de gardien, lampadaires
        for (int[] c : new int[][]{{14, -29}, {15, -29}, {20, -28}, {22, -29}}) set(c[0], G + 2, c[1], "minecraft:barrel[facing=up,open=false]");
        for (int[] c : new int[][]{{27, -36}, {28, -36}, {27, -37}, {6, -38}}) { set(c[0], G + 1, c[1], "minecraft:spruce_slab[type=bottom,waterlogged=false]"); set(c[0], G + 2, c[1], "minecraft:stripped_spruce_wood[axis=x]"); }
        for (int x = -30; x <= -28; x++) { set(x, G + 1, -38, "minecraft:green_terracotta"); set(x, G + 2, -38, "minecraft:iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]"); }
        for (int x = 3; x <= 4; x++) for (int z = -39; z <= -38; z++) for (int y = G + 1; y <= G + 3; y++) set(x, y, z, y == G + 3 ? "minecraft:white_concrete" : (y == G + 2 ? "minecraft:glass" : "minecraft:white_concrete"));
        for (int[] l : new int[][]{{-24, -33}, {0, -33}, {30, -33}}) lampPost(l[0], G + 1, l[1], 5, "minecraft:iron_bars", "minecraft:polished_blackstone_wall", false);
    }

    private void truck(int x, int z) {
        // caisse 8 x 3, cabine, roues
        for (int i = 0; i < 8; i++)
            for (int j = -1; j <= 1; j++)
                for (int y = G + 2; y <= G + 4; y++) set(x + i, y, z + j, i == 7 ? "minecraft:white_concrete" : (y == G + 4 ? "minecraft:light_gray_concrete" : "minecraft:white_concrete"));
        for (int i = 8; i <= 10; i++)
            for (int j = -1; j <= 1; j++) {
                set(x + i, G + 2, z + j, "minecraft:red_concrete");
                set(x + i, G + 3, z + j, i == 10 ? "minecraft:glass" : (Math.abs(j) == 1 ? "minecraft:glass_pane" : B.AIR));
                if (i <= 9) set(x + i, G + 4, z + j, "minecraft:red_concrete");
            }
        set(x + 9, G + 3, z, stairs("minecraft:spruce_stairs", Dir.WEST, false));
        for (int i : new int[]{1, 6, 9}) for (int j : new int[]{-1, 1}) set(x + i, G + 1, z + j, "minecraft:polished_blackstone");
        for (int i = 0; i <= 10; i++) for (int j = -1; j <= 1; j++) if (isAir(x + i, G + 1, z + j)) set(x + i, G + 1, z + j, B.AIR);
    }

    // ================================================================ cloture

    private void fence() {
        for (int t = -48; t <= 50; t++) {
            int[][] cells = {{t, BZ1}, {t, BZ2}, {BX1, t}, {BX2, t}};
            for (int[] c : cells) {
                int x = c[0], z = c[1];
                if (x < BX1 || x > BX2 || z < BZ1 || z > BZ2) continue;
                boolean gate = (z == BZ2 || z == BZ1) && Math.abs(x) <= 3;
                boolean pillar = Math.floorMod(t, 6) == 0 || (Math.abs(x) == 48 && (z == BZ1 || z == BZ2));
                if (pillar) {
                    for (int y = G + 1; y <= G + 3; y++) set(x, y, z, PLINTH.at(x, y, z));
                    set(x, G + 4, z, "minecraft:chiseled_stone_bricks");
                    set(x, G + 5, z, standing("minecraft:lantern"));
                } else {
                    set(x, G + 1, z, gate ? "minecraft:iron_bars" : "minecraft:stone_brick_wall");
                    set(x, G + 2, z, "minecraft:iron_bars");
                    set(x, G + 3, z, "minecraft:iron_bars");
                    if (gate) set(x, G + 4, z, "minecraft:iron_bars");
                }
                for (int y = G + 6; y <= MAX_Y; y++) if (isAir(x, y, z)) set(x, y, z, "minecraft:barrier");
                if (!pillar) for (int y = G + 4 + (gate ? 1 : 0); y <= G + 5; y++) if (isAir(x, y, z)) set(x, y, z, "minecraft:barrier");
            }
        }
        // haie le long de la cloture (cote interieur), sauf aux portails et aux allees
        for (int t = BX1 + 1; t <= BX2 - 1; t++) {
            if (Math.abs(t) > 5) { hedge(t, BZ1 + 1); }
        }
        for (int t = BZ1 + 1; t <= BZ2 - 1; t++) { hedge(BX1 + 1, t); hedge(BX2 - 1, t); }
        for (int sx : new int[]{-5, 5}) {
            for (int z : new int[]{BZ1, BZ2}) {
                for (int y = G + 1; y <= G + 4; y++) set(sx, y, z, PLINTH.at(sx, y, z));
                set(sx, G + 5, z, "minecraft:chiseled_stone_bricks");
                set(sx, G + 6, z, standing("minecraft:lantern"));
            }
        }
    }

    private void hedge(int x, int z) {
        if (!isAir(x, G + 1, z)) return;
        if (!get(x, G, z).contains("grass") && !get(x, G, z).contains("moss") && !get(x, G, z).contains("podzol") && !get(x, G, z).contains("coarse")) return;
        set(x, G + 1, z, HEDGE.at(x, G + 1, z));
        if (Math.floorMod(x * 3 + z, 5) != 0) set(x, G + 2, z, HEDGE.at(x, G + 2, z));
    }

    // ================================================================ ville autour (decor hors de la boite)

    private void city() {
        // lampadaires de rue
        for (int t = -56; t <= 56; t += 14) {
            for (int[] p : new int[][]{{t, BZ2 + 2}, {t, BZ1 - 2}, {BX1 - 2, t}, {BX2 + 2, t}}) {
                if (Math.abs(p[0]) > AREA || Math.abs(p[1]) > AREA) continue;
                if (isAir(p[0], G + 1, p[1])) lampPost(p[0], G + 1, p[1], 6, "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone", true);
            }
        }
        // facades d'immeubles (5 niveaux, toit mansarde) de l'autre cote de la rue
        Style town = new Style().wall(TOWN).plinth(Mat.of("minecraft:stone_bricks")).post("minecraft:stripped_spruce_log")
                .corner(Mat.of("minecraft:smooth_sandstone")).band("minecraft:smooth_sandstone_stairs").cornice("minecraft:smooth_sandstone_stairs")
                .sill("minecraft:smooth_sandstone_stairs").lintel("minecraft:smooth_sandstone_slab").shutter("minecraft:dark_oak_trapdoor")
                .spacing(4).storey(4).blankBays(0.1).innerPosts(false).seed(99);
        int[][] rows = {{-60, 60, 1}, {-60, -60, 1}};
        int i = 0;
        for (int bx = -60; bx < 60; bx += 12, i++) {
            int top = G + 16 + (i % 3) * 4;
            block(town, bx, 61, Dir.EAST, 11, Dir.NORTH, top, i);
            block(town, bx, -62, Dir.EAST, 11, Dir.SOUTH, top - 4 + (i % 2) * 4, i + 7);
        }
        for (int bz = -54; bz < 54; bz += 12, i++) {
            int top = G + 16 + (i % 3) * 4;
            block(town, -62, bz, Dir.SOUTH, 11, Dir.EAST, top, i);
            block(town, 62, bz, Dir.SOUTH, 11, Dir.WEST, top - 4 + (i % 2) * 4, i + 3);
        }
        // arbres d'alignement sur les trottoirs
        long sd = 700;
        for (int t = -49; t <= 49; t += 14) {
            for (int[] p : new int[][]{{t + 7, BZ2 + 2}, {t + 7, BZ1 - 2}}) {
                if (!isAir(p[0], G + 1, p[1])) continue;
                new Tree("minecraft:oak_log", "minecraft:oak_leaves", null).height(6).crown(2.2).roots(false).place(s, p[0], G + 1, p[1], sd++);
            }
        }
    }

    /** Immeuble de facade seule (une rangee d'appartements de 3 de profondeur, sans interieur jouable). */
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
        // toit : bandeau de zinc (ardoise) en pente vers l'exterieur
        for (int t = -1; t <= len + 1; t++)
            for (int d = 0; d <= 3; d++) {
                int x = x0 + along.dx * t + in.dx * d, z = z0 + along.dz * t + in.dz * d;
                if (Math.abs(x) > AREA || Math.abs(z) > AREA) continue;
                set(x, top + 1 + d / 2, z, d == 3 ? "minecraft:deepslate_tiles" : stairs("minecraft:deepslate_tile_stairs", in, false));
            }
        if (k % 3 == 0) {
            int x = x0 + along.dx * (len / 3) + in.dx * 3, z = z0 + along.dz * (len / 3) + in.dz * 3;
            if (Math.abs(x) <= AREA && Math.abs(z) <= AREA) Roof.chimney(s, x, z, x + along.dx, z + along.dz, top + 1, top + 5, Mat.of("minecraft:bricks"), "minecraft:brick_slab");
        }
    }
}
