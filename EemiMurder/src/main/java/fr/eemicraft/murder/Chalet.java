package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Dir;
import fr.eemicraft.murder.build.Facade;
import fr.eemicraft.murder.build.Mat;
import fr.eemicraft.murder.build.Palette;
import fr.eemicraft.murder.build.Sink;
import fr.eemicraft.murder.build.Style;

/**
 * Map « Chalet de montagne » (id chalet, monde mm_chalet, 1.12.0) : PETITE map de 2 a 5 joueurs (size: small), chalet
 * alpin enneige, de nuit, dessine par code dans un {@link Sink} (aucun acces Bukkit).
 *
 * <p>Boite x -35..35, z -30..28. Chalet x -16..16 / z -8..9 (facade principale au sud, sur la vallee), trois niveaux :
 * cave (sol y 58, pieds 59), rez-de-chaussee (sol y 64, pieds 65) et etage sous le toit (plancher y 70, pieds 71) ;
 * grand toit a deux pans couvert de neige (faitage est-ouest), debord de 3 au-dessus du balcon.
 * <ul>
 * <li>cave : cave a vin (escalier de la cuisine, descente exterieure ouest) et salle de jeux (billard, bar,
 * chaudiere) ;</li>
 * <li>rez-de-chaussee : grand salon a double hauteur avec la cheminee et l'escalier principal, entree, cuisine,
 * salle a manger, buanderie (escalier de service), bureau ; local a skis (nord-ouest), porche (nord), garage a
 * motoneiges (nord-est) ;</li>
 * <li>etage : palier-mezzanine sur le salon, chambre des parents, chambre des enfants (lits superposes), chambre
 * d'amis, salle de bains et lingerie ; balcon continu au sud, escalier exterieur a l'est ;</li>
 * <li>dehors : terrasse sous le balcon, sauna et bain nordique (ouest), abri de bois (est), etang gele (sud-est),
 * parking, sapins, congeres, cloture de ranch ; foret de sapins et collines tout autour.</li>
 * </ul>
 * Trois escaliers relient le rez et l'etage (principal, service, exterieur), deux relient le rez et la cave
 * (cuisine, descente exterieure).
 */
final class Chalet extends MapKit {
    static final int VERSION = 2; // 1.13.0 : vitres dans le mur, 3 cases libres au-dessus des marches
    static final String ID = "chalet";
    static final String WORLD = "mm_chalet";
    static final String NAME = "Chalet de montagne";

    static final int BX1 = -35, BX2 = 35, BZ1 = -30, BZ2 = 28, MIN_Y = 58, MAX_Y = 84;
    static final int AREA = 52, Y_LO = 50, Y_HI = 98;
    /** Sol de la cave, du rez, plancher de l'etage, corniche des murs de l'etage. */
    static final int C = 58, G = 64, U = 70, E = 73;
    /** Lignes exterieures des facades du chalet et interieur (murs sur 2 rangs). */
    static final int X1 = -16, X2 = 16, Z1 = -8, Z2 = 9;
    static final int IX1 = -14, IX2 = 14, IZ1 = -6, IZ2 = 7;
    /** Toit : hauteur au droit des facades, pente (blocs par bloc). */
    static final double ROOF0 = 75, PITCH = 0.5;
    static final int[] WORLD_SPAWN = {1, 65, -24};

    static final int[][] SPAWNS = {
            {-1, 65, 2},     // salon
            {-3, 65, -4},    // entree
            {-9, 65, -3},    // cuisine
            {-12, 65, 6},    // salle a manger
            {10, 65, 5},     // bureau
            {-9, 65, -11},   // local a skis
            {10, 65, -12},   // garage
            {-10, 59, 3},    // cave a vin
            {-2, 59, 4},     // salle de jeux
            {2, 71, -4},     // palier
            {-11, 71, 5},    // chambre des parents
            {11, 71, 3},     // chambre d'amis
            {-24, 65, 3},    // jardin ouest
            {24, 65, -3},    // abri de bois
    };

    static final int[][] GOLD = {
            // rez-de-chaussee
            {3, 65, 1}, {-3, 65, 6}, {-5, 65, -3}, {5, 65, -5}, {-13, 65, -4}, {-8, 65, -5}, {-9, 65, 2},
            {-13, 65, 6}, {9, 65, 1}, {12, 65, 6}, {12, 65, -5}, {14, 65, -2},
            // annexes
            {-11, 65, -14}, {-7, 65, -10}, {10, 65, -14}, {13, 65, -10},
            // cave
            {-14, 59, -3}, {-7, 59, 6}, {-3, 59, -5}, {0, 59, 4}, {4, 59, 2},
            // etage
            {-4, 71, -1}, {5, 71, 0}, {-13, 71, 6}, {-10, 71, -3}, {-12, 71, -2}, {9, 71, 6}, {13, 71, 1}, {9, 71, -5},
            // dehors
            {-27, 65, -16}, {-6, 65, 13}, {14, 65, 14}, {25, 65, -6}, {-25, 64, 24}, {29, 63, 25},
            {-28, 65, 9}, {20, 65, -24}, {-2, 65, -20},
    };

    private Chalet(Sink s) {
        super(s, 1987);
    }

    static void build(Sink s) {
        new Chalet(s).run();
    }

    // ================================================================ palettes

    private static final Palette RUBBLE = Palette.of(101, "minecraft:cobblestone", 80, "minecraft:mossy_cobblestone", 20).period(12);
    private static final Palette PLINTH = Palette.of(102, "minecraft:mossy_cobblestone", 45, "minecraft:cobblestone", 35,
            "minecraft:cobbled_deepslate", 20).period(5);
    private static final Palette BOARDS = Palette.of("minecraft:spruce_planks");
    private static final Palette PANEL = Palette.of("minecraft:spruce_planks");
    private static final Palette PARQUET = Palette.of(105, "minecraft:dark_oak_planks", 70, "minecraft:spruce_planks", 30).period(9);
    private static final Palette FLOORUP = Palette.of(106, "minecraft:spruce_planks", 70, "minecraft:oak_planks", 30).period(6);
    private static final Palette TILE = Palette.of(107, "minecraft:smooth_stone", 52, "minecraft:polished_andesite", 34,
            "minecraft:andesite", 14).period(5);
    private static final Palette SLATE = Palette.of(108, "minecraft:polished_deepslate", 55, "minecraft:deepslate_tiles", 30,
            "minecraft:cobbled_deepslate", 15).period(5);
    private static final Palette CAVEWALL = Palette.of(109, "minecraft:stone_bricks", 72, "minecraft:cracked_stone_bricks", 14,
            "minecraft:mossy_stone_bricks", 14).period(10);
    private static final Palette CELLAR = Palette.of(110, "minecraft:bricks", 60, "minecraft:mud_bricks", 25,
            "minecraft:packed_mud", 15).period(5);
    private static final Palette SNOW = Palette.of(111, "minecraft:snow_block", 82, "minecraft:white_concrete_powder", 11,
            "minecraft:calcite", 7).period(6);
    private static final Palette PATH = Palette.of(112, "minecraft:gravel", 45, "minecraft:coarse_dirt", 35,
            "minecraft:packed_mud", 20).period(4);
    private static final Palette ROCK = Palette.of(113, "minecraft:stone", 62, "minecraft:andesite", 24,
            "minecraft:tuff", 14).period(9);
    private static final Palette ROOFWOOD = Palette.of(114, "minecraft:spruce_planks", 75, "minecraft:dark_oak_planks", 25).period(10);
    private static final Palette DECK = Palette.of(115, "minecraft:spruce_planks", 66, "minecraft:stripped_spruce_wood[axis=y]", 20,
            "minecraft:dark_oak_planks", 14).period(5);

    private static final String DOOR = "minecraft:spruce_door";
    private static final String LANTERN = "minecraft:lantern";

    /** Hauteur du sol (bloc du dessus) par colonne de la zone posee. */
    private final int[][] hm = new int[2 * AREA + 1][2 * AREA + 1];

    // ================================================================ deroule

    private void run() {
        terrain();
        cave();
        bulkhead();
        shell();
        skiRoom();
        garage();
        porch();
        partitions();
        fireplace();
        mainStair();
        flight(8, -2, Dir.EAST, 2, G, 6, "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:spruce_fence"); // service
        flight(-8, -1, Dir.WEST, 2, C, 6, "minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:spruce_fence"); // cave
        roof();
        chimney();
        balcony();
        outerStair();
        terrace();
        // interieurs
        salon();
        hall();
        kitchen();
        dining();
        laundry();
        study();
        skiRoomInside();
        garageInside();
        wineCellar();
        gameRoom();
        landing();
        parentsRoom();
        kidsRoom();
        bathroom();
        guestRoom();
        // dehors
        paths();
        sauna();
        hotTub();
        woodshed();
        pond();
        props();
        trees();
        forest();
        fence(); // apres la foret : les feuilles sur la ligne de cloture deviennent des barrieres
        drifts();
        clearDoors();
        fillDarkSpots();
        finish(-AREA, Y_LO + 2, -AREA, AREA, Y_HI - 1, AREA);
    }

    private boolean inBox(int x, int z) {
        return x >= BX1 && x <= BX2 && z >= BZ1 && z <= BZ2;
    }

    private boolean inArea(int x, int z) {
        return Math.abs(x) <= AREA && Math.abs(z) <= AREA;
    }

    /** Bloc du dessus du sol en (x, z). */
    int top(int x, int z) {
        return inArea(x, z) ? hm[x + AREA][z + AREA] : G;
    }

    @Override
    void set(int x, int y, int z, String st) {
        if (st != null) super.set(x, y, z, st);
    }

    // ================================================================ terrain

    /** Hauteur du sol : plat dans la boite (jardin sud un cran plus bas, etang encore un), collines autour. */
    private int surf(int x, int z) {
        if (inBox(x, z)) {
            if (inPond(x, z)) return G - 2;
            return z >= 20 ? G - 1 : G;
        }
        double n = noise.fbm2(x / 11.0, z / 11.0, 3, 0.5, 2.0);
        int dx = Math.max(Math.max(BX1 - x, x - BX2), 0);
        int dzS = z - BZ2, dzN = BZ1 - z;
        // route au nord, le long de la cloture
        if (dzN >= 2 && dzN <= 7) return G;
        if (dzS > 0) {
            double h = G - 1 - Math.min(5, dzS * 0.4) + n * 1.5 + Math.max(0, dx - 2) * 0.5;
            return (int) Math.round(h);
        }
        int dN = dzN > 7 ? dzN - 7 : 0;
        int d = Math.max(dx, dN);
        double h = G + d * 0.75 + n * 2.5 * Math.min(1, d / 4.0);
        return (int) Math.round(Math.min(G + 22, Math.max(G - 1, h)));
    }

    private boolean inPond(int x, int z) {
        double dx = (x - 24) / 6.5, dz = (z - 23.5) / 3.8;
        return dx * dx + dz * dz <= 1.0;
    }

    private boolean onRoad(int x, int z) {
        int dzN = BZ1 - z;
        return dzN >= 3 && dzN <= 6;
    }

    private void terrain() {
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                int h = surf(x, z);
                hm[x + AREA][z + AREA] = h;
                boolean box = inBox(x, z);
                for (int y = Y_LO; y < h; y++) set(x, y, z, y >= h - 2 ? "minecraft:dirt" : (box ? "minecraft:stone" : ROCK.at(x, y, z)));
                String topB;
                if (inPond(x, z)) topB = ((x * 7 + z * 3) & 7) == 0 ? "minecraft:ice" : "minecraft:packed_ice";
                else if (onRoad(x, z)) {
                    int dzN = BZ1 - z;
                    topB = (dzN == 4 || dzN == 5) && Math.floorMod(x, 9) != 0 ? "minecraft:light_gray_concrete_powder" : SNOW.at(x, h, z);
                } else if (!inBox(x, z) && slope(x, z) >= 2 && noise.uniform2(x / 4.0, z / 4.0) < 0.55) topB = ROCK.at(x, h, z);
                else topB = SNOW.at(x, h, z);
                set(x, h, z, topB);
            }
        // talus de neige entre le jardin et le jardin bas (z 19 -> 20) : quelques marches de pierre
        for (int x = BX1 + 1; x <= BX2 - 1; x++) {
            if (inPond(x, 20)) continue;
            if (x >= -2 && x <= 1 || x >= -24 && x <= -22 || x >= 12 && x <= 14)
                set(x, G, 20, stairs("minecraft:cobblestone_stairs", Dir.NORTH, false));
        }
    }

    private int slope(int x, int z) {
        int h = surf(x, z), m = 0;
        for (Dir d : Dir.values()) m = Math.max(m, Math.abs(surf(x + d.dx, z + d.dz) - h));
        return m;
    }

    // ================================================================ cave

    /** Cave sous la moitie ouest du chalet : cave a vin (x -14..-6) et salle de jeux (x -4..1). */
    private void cave() {
        int cx1 = -14, cx2 = 5, cz1 = -6, cz2 = 7;
        for (int x = cx1 - 1; x <= cx2 + 1; x++)
            for (int z = cz1 - 1; z <= cz2 + 1; z++) {
                boolean wall = x == cx1 - 1 || x == cx2 + 1 || z == cz1 - 1 || z == cz2 + 1;
                set(x, C, z, wall ? CAVEWALL.at(x, C, z) : CELLAR.at(x, C, z));
                for (int y = C + 1; y < G; y++) set(x, y, z, wall ? CAVEWALL.at(x, y, z) : B.AIR);
            }
        // cloison cave a vin / salle de jeux, deux arches
        for (int z = cz1; z <= cz2; z++)
            for (int y = C + 1; y < G; y++) set(-5, y, z, CAVEWALL.at(-5, y, z));
        for (int[] a : new int[][]{{-3, -2}, {4, 5}}) {
            for (int z = a[0]; z <= a[1]; z++) for (int y = C + 1; y <= C + 3; y++) set(-5, y, z, B.AIR);
            set(-5, C + 4, a[0], stairs("minecraft:stone_brick_stairs", Dir.SOUTH, true));
            set(-5, C + 4, a[1], stairs("minecraft:stone_brick_stairs", Dir.NORTH, true));
        }
        // nervures de la voute (escaliers retournes contre les murs, seulement dans l'air)
        for (int x = cx1; x <= cx2; x++)
            for (int z = cz1; z <= cz2; z++) {
                if (!isAir(x, C + 5, z)) continue;
                for (Dir d : Dir.values()) {
                    if (B.isSolid(get(x + d.dx, C + 5, z + d.dz)) && B.name(get(x + d.dx, C + 5, z + d.dz)).contains("brick")) {
                        if (Math.floorMod(x + z, 3) != 0) set(x, C + 5, z, stairs("minecraft:stone_brick_stairs", d, true));
                        break;
                    }
                }
            }
    }

    /** Descente exterieure de la cave, le long de la facade ouest : tranchee, palier couvert, couloir, porte. */
    private void bulkhead() {
        // murs de soutenement
        for (int z = -4; z <= 5; z++)
            for (int y = C; y <= G; y++) {
                set(-21, y, z, y == G ? "minecraft:cobblestone" : CAVEWALL.at(-21, y, z));
                set(-18, y, z, y == G ? "minecraft:cobblestone" : CAVEWALL.at(-18, y, z));
            }
        // tranchee a ciel ouvert (z -3..2) au-dessus des marches, palier et couloir couverts (z 3..4)
        for (int z = -3; z <= 2; z++) for (int x = -20; x <= -19; x++) for (int y = C + 1; y <= G + 3; y++) set(x, y, z, B.AIR);
        flight(-20, 2, Dir.NORTH, 2, C, 6, "minecraft:cobblestone", "minecraft:cobblestone_stairs", "minecraft:spruce_fence");
        for (int x = -20; x <= -15; x++)
            for (int z = 3; z <= 4; z++) {
                set(x, C, z, CELLAR.at(x, C, z));
                for (int y = C + 1; y <= C + 4; y++) set(x, y, z, B.AIR);
                set(x, C + 5, z, x >= -20 && x <= -19 ? "minecraft:spruce_planks" : CAVEWALL.at(x, C + 5, z));
            }
        for (int x = -20; x <= -15; x++) {
            for (int y = C; y <= C + 5; y++) {
                if (x >= -17) set(x, y, 2, CAVEWALL.at(x, y, 2));
                set(x, y, 5, CAVEWALL.at(x, y, 5));
            }
        }
        // poutres du palier
        set(-20, C + 4, 3, log("minecraft:spruce_log", 'x'));
        set(-19, C + 4, 3, log("minecraft:spruce_log", 'x'));
        set(-17, C + 3, 4, hanging(LANTERN));
        set(-17, C + 4, 4, chain('y'));
        set(-15, C + 1, 3, B.state(B.door(DOOR, Dir.EAST, false, false), "open", "false"));
        set(-15, C + 2, 3, B.state(B.door(DOOR, Dir.EAST, true, false), "open", "false"));
        set(-15, C + 1, 4, CAVEWALL.at(-15, C + 1, 4));
        set(-15, C + 2, 4, CAVEWALL.at(-15, C + 2, 4));
        // garde-corps autour de la tranchee
        for (int z = -3; z <= 3; z++) {
            set(-21, G + 1, z, "minecraft:spruce_fence");
            set(-18, G + 1, z, "minecraft:spruce_fence");
        }
        set(-20, G + 1, 3, "minecraft:spruce_fence");
        set(-19, G + 1, 3, "minecraft:spruce_fence");
        set(-21, G + 1, -4, "minecraft:spruce_log[axis=y]");
        set(-21, G + 2, -4, standing(LANTERN));
        set(-18, G + 1, -4, "minecraft:spruce_log[axis=y]");
        set(-18, G + 2, -4, standing(LANTERN));
        // petit auvent au-dessus du haut de l'escalier
        for (int x = -21; x <= -18; x++) set(x, G + 4, -4, slab("minecraft:spruce_slab", "bottom"));
        for (int x : new int[]{-21, -18}) set(x, G + 3, -4, "minecraft:spruce_fence");
    }

    // ================================================================ enveloppe

    private Style rezStyle() {
        return new Style().wall(RUBBLE).plinth(PLINTH).plinthStairs("minecraft:cobblestone_stairs")
                .post("minecraft:stripped_spruce_log").corner(Mat.of("minecraft:stone_bricks")).pane("minecraft:glass_pane")
                .sill("minecraft:spruce_stairs").lintel("minecraft:spruce_slab").shutter("minecraft:crimson_trapdoor")
                .band(null).cornice("minecraft:dark_oak_stairs").door(DOOR).spacing(5).storey(6).shutters(0.85)
                .blankBays(0).innerPosts(false).seed(41);
    }

    private Style upStyle() {
        return new Style().wall(BOARDS).plinth(Mat.of("minecraft:dark_oak_planks")).plinthStairs("minecraft:dark_oak_stairs")
                .post("minecraft:stripped_spruce_log").corner(Mat.of("minecraft:spruce_log[axis=y]")).pane("minecraft:glass_pane")
                .sill("minecraft:spruce_stairs").lintel("minecraft:spruce_slab").shutter("minecraft:crimson_trapdoor")
                .band(null).cornice("minecraft:spruce_stairs").door(DOOR).spacing(5).storey(3).shutters(0.6)
                .blankBays(0.1).innerPosts(false).seed(43);
    }

    private void shell() {
        for (int x = IX1; x <= IX2; x++)
            for (int z = IZ1; z <= IZ2; z++) {
                set(x, G, z, groundFloor(x, z));
                set(x, U, z, FLOORUP.at(x, U, z));
                for (int y = G + 1; y < U; y++) set(x, y, z, B.AIR);
                for (int y = U + 1; y <= 82; y++) set(x, y, z, B.AIR);
            }
        Style r = rezStyle(), u = upStyle();
        u.groundDepth = 0; // l'etage ne descend pas de fondation dans les murs du rez
        int lx = X2 - X1, lz = Z2 - Z1;
        Facade.wall(s, r, X1, Z1, Dir.EAST, lx, Dir.NORTH, G, U, -1);
        Facade.wall(s, r, X1, Z2, Dir.EAST, lx, Dir.SOUTH, G, U, -1);
        Facade.wall(s, r, X1, Z1, Dir.SOUTH, lz, Dir.WEST, G, U, -1);
        Facade.wall(s, r, X2, Z1, Dir.SOUTH, lz, Dir.EAST, G, U, -1);
        Facade.wall(s, u, X1, Z1, Dir.EAST, lx, Dir.NORTH, U, E, -1);
        Facade.wall(s, u, X1, Z2, Dir.EAST, lx, Dir.SOUTH, U, E, -1);
        Facade.wall(s, u, X1, Z1, Dir.SOUTH, lz, Dir.WEST, U, E, -1);
        Facade.wall(s, u, X2, Z1, Dir.SOUTH, lz, Dir.EAST, U, E, -1);
        // pignons est et ouest (au-dessus de la corniche, jusqu'au toit) : poteaux en saillie, planches, fenetre
        for (int gx : new int[]{X1, X2}) {
            int in = gx < 0 ? 1 : -1;
            for (int z = Z1; z <= Z2; z++) {
                int topY = roofPlank(z) - 1;
                for (int y = E + 1; y <= topY; y++) {
                    boolean post = Math.floorMod(z, 3) == 0 || z == Z1 || z == Z2;
                    set(gx, y, z, post ? log("minecraft:stripped_spruce_log", 'y') : B.AIR);
                    boolean win = z >= -1 && z <= 2 && y >= E + 2 && y <= E + 4;
                    set(gx + in, y, z, win ? "minecraft:glass_pane" : BOARDS.at(gx + in, y, z));
                }
                // croix de saint andre sous la fenetre du pignon
                if (z >= -3 && z <= 4 && (z < -1 || z > 2)) set(gx + in, E + 3, z, log("minecraft:stripped_dark_oak_log", 'z'));
            }
            for (int z = -1; z <= 2; z++) {
                set(gx, E + 1, z, stairs("minecraft:spruce_stairs", in > 0 ? Dir.EAST : Dir.WEST, true)); // appui
                set(gx + in, E + 5, z, log("minecraft:stripped_dark_oak_log", 'z')); // linteau
            }
            // poinçon et coeur sous le faitage
            set(gx, roofPlank(0) - 1, 0, "minecraft:stripped_dark_oak_log[axis=y]");
            set(gx, roofPlank(1) - 1, 1, "minecraft:stripped_dark_oak_log[axis=y]");
        }
        // bandeau du plancher de l'etage : corniche en escaliers retournes entre les poteaux (le socle de l'etage
        // l'avait remplacee par des planches)
        for (int x = X1 + 1; x < X2; x++)
            for (int z : new int[]{Z1, Z2}) band(x, z, z == Z1 ? Dir.SOUTH : Dir.NORTH);
        for (int z = Z1 + 1; z < Z2; z++)
            for (int x : new int[]{X1, X2}) band(x, z, x == X1 ? Dir.EAST : Dir.WEST);
        southGlassWall();
        doors();
    }

    private void band(int x, int z, Dir in) {
        if (B.name(get(x, U - 1, z)).equals("stripped_spruce_log")) set(x, U, z, log("minecraft:stripped_spruce_log", 'y'));
        else set(x, U, z, stairs("minecraft:dark_oak_stairs", in, true));
    }

    private String groundFloor(int x, int z) {
        if (x <= -8 && z <= -1) return TILE.at(x, G, z);                 // cuisine
        if (x >= -6 && x <= 6 && z <= -2) return SLATE.at(x, G, z);       // entree
        if (x >= 8 && z <= -1) return TILE.at(x, G, z);                   // buanderie
        return PARQUET.at(x, G, z);
    }

    /** Grande baie du salon (x -4..4) sur deux niveaux, poutre a la hauteur du plancher. */
    private void southGlassWall() {
        for (int x = -4; x <= 4; x++) {
            boolean post = B.name(get(x, G + 2, Z2)).equals("stripped_spruce_log");
            for (int y = G + 1; y <= E - 1; y++) {
                if (y == U) {
                    set(x, y, Z2 - 1, log("minecraft:stripped_dark_oak_log", 'x'));
                    continue;
                }
                set(x, y, Z2 - 1, "minecraft:glass_pane");
                set(x, y, Z2 - 2, B.AIR);
                if (!post) set(x, y, Z2, B.AIR);
            }
            if (!post) set(x, G, Z2, PLINTH.at(x, G, Z2));
        }
        // porte-fenetre double du salon (x 1..2) sous la baie
        for (int x = 1; x <= 2; x++) {
            set(x, G + 1, Z2 - 1, B.state(B.door(DOOR, Dir.NORTH, false, x == 2), "open", "false"));
            set(x, G + 2, Z2 - 1, B.state(B.door(DOOR, Dir.NORTH, true, x == 2), "open", "false"));
            set(x, G, Z2, stairs("minecraft:spruce_stairs", Dir.NORTH, false));
        }
    }

    /** Porte dans un mur exterieur de 2 rangs : P (air), P-1 (porte), P-2 (degage). */
    private void wallDoor(int x, int z, Dir out, int base) {
        Dir in = out.opposite();
        for (int d = -1; d <= 2; d++) for (int y = base + 1; y <= base + 2; y++) set(x + in.dx * d, y, z + in.dz * d, B.AIR);
        set(x + in.dx, base + 1, z + in.dz, B.state(B.door(DOOR, in, false, false), "open", "false"));
        set(x + in.dx, base + 2, z + in.dz, B.state(B.door(DOOR, in, true, false), "open", "false"));
        set(x + in.dx, base + 3, z + in.dz, log("minecraft:stripped_dark_oak_log", out.axis() == 'x' ? 'z' : 'x'));
        if (base == G) set(x, base, z, stairs("minecraft:cobblestone_stairs", in, false));
    }

    private void doors() {
        wallDoor(-11, Z2, Dir.SOUTH, G);   // salle a manger -> terrasse
        wallDoor(11, Z2, Dir.SOUTH, G);    // bureau -> terrasse
        wallDoor(-11, Z2, Dir.SOUTH, U);   // chambre des parents -> balcon
        wallDoor(11, Z2, Dir.SOUTH, U);    // chambre d'amis -> balcon
        wallDoor(X1, -3, Dir.WEST, G);     // cuisine -> jardin
        // nord : porte d'entree double (porche), local a skis, garage
        for (int x = 1; x <= 2; x++) {
            for (int z = Z1 - 1; z <= Z1 + 2; z++) for (int y = G + 1; y <= G + 2; y++) set(x, y, z, B.AIR);
            set(x, G + 1, Z1 + 1, B.state(B.door(DOOR, Dir.SOUTH, false, x == 1), "open", "false"));
            set(x, G + 2, Z1 + 1, B.state(B.door(DOOR, Dir.SOUTH, true, x == 1), "open", "false"));
            set(x, G + 3, Z1 + 1, log("minecraft:stripped_dark_oak_log", 'x'));
            set(x, G, Z1, "minecraft:spruce_planks");
        }
        for (int x : new int[]{-6, 11}) {
            for (int z = Z1 - 1; z <= Z1 + 2; z++) for (int y = G + 1; y <= G + 2; y++) set(x, y, z, B.AIR);
            set(x, G + 1, Z1 + 1, B.state(B.door(DOOR, Dir.SOUTH, false, false), "open", "false"));
            set(x, G + 2, Z1 + 1, B.state(B.door(DOOR, Dir.SOUTH, true, false), "open", "false"));
            set(x, G, Z1, "minecraft:spruce_planks");
        }
    }

    // ================================================================ annexes

    private Style annexStyle(long seed) {
        return rezStyle().storey(5).seed(seed).shutters(0.7);
    }

    /** Murs nord, ouest et est d'une annexe accolee au mur nord du chalet (x1..x2, z1 = mur nord de l'annexe). */
    private void annex(int x1, int x2, int z1, long seed) {
        int top = G + 5;
        for (int x = x1 + 2; x <= x2 - 2; x++)
            for (int z = z1 + 2; z <= Z1 - 1; z++) {
                for (int y = G + 1; y <= 76; y++) set(x, y, z, B.AIR);
            }
        Style st = annexStyle(seed);
        Facade.wall(s, st, x1, z1, Dir.EAST, x2 - x1, Dir.NORTH, G, top, -1);
        Facade.wall(s, st, x1, z1, Dir.SOUTH, Z1 - z1, Dir.WEST, G, top, -1);
        Facade.wall(s, st, x2, z1, Dir.SOUTH, Z1 - z1, Dir.EAST, G, top, -1);
        // toit a deux pans, faitage nord-sud, s'arrete au mur du chalet
        gableZ(x1, x2, z1 - 1, Z1 - 1, top + 2, 1);
        // pignon nord
        for (int x = x1; x <= x2; x++) {
            int py = gzPlank(x, x1, x2, top + 2) - 1;
            for (int y = top + 1; y <= py; y++) {
                boolean post = x == x1 || x == x2 || Math.floorMod(x, 3) == 0;
                set(x, y, z1, post ? log("minecraft:stripped_spruce_log", 'y') : B.AIR);
                set(x, y, z1 + 1, BOARDS.at(x, y, z1 + 1));
            }
        }
    }

    private int gzPlank(int x, int x1, int x2, double s0) {
        int d = Math.min(x - x1, x2 - x);
        return (int) Math.floor(s0 + PITCH * d + 1e-9) - 1;
    }

    /** Toit enneige a deux pans, faitage nord-sud, sur x1..x2 (debord ox), z de za a zb. */
    private void gableZ(int x1, int x2, int za, int zb, double s0, int ox) {
        for (int x = x1 - ox; x <= x2 + ox; x++)
            for (int z = za; z <= zb; z++) {
                int d = Math.min(x - x1, x2 - x);
                double sv = s0 + PITCH * d;
                int fl = (int) Math.floor(sv + 1e-9);
                boolean edgeX = x == x1 - ox || x == x2 + ox, edgeZ = z == za;
                String pl = edgeZ ? log("minecraft:stripped_spruce_log", 'x') : edgeX ? log("minecraft:stripped_spruce_log", 'z')
                        : (Math.floorMod(z, 4) == 0 ? log("minecraft:dark_oak_log", 'x') : ROOFWOOD.at(x, fl - 1, z));
                set(x, fl - 1, z, pl);
                set(x, fl, z, snow(sv - fl));
                if ((edgeX || edgeZ) && isAir(x, fl - 2, z) && h(x, fl, z) < 0.3) icicle(x, fl - 2, z);
            }
    }

    private static String snow(double frac) {
        int l = Math.max(1, Math.min(8, (int) Math.round(frac * 8)));
        return "minecraft:snow[layers=" + l + "]";
    }

    /** Stalactite de glace (pointe de dripstone) sous un debord, 1 ou 2 blocs. */
    private void icicle(int x, int y, int z) {
        if (!isAir(x, y, z)) return;
        boolean two = h(x, y + 7, z) < 0.4 && isAir(x, y - 1, z);
        if (two) {
            set(x, y, z, "minecraft:pointed_dripstone[thickness=frustum,vertical_direction=down,waterlogged=false]");
            set(x, y - 1, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]");
        } else set(x, y, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]");
    }

    private void skiRoom() {
        annex(-14, -4, -17, 51);
        for (int x = -12; x <= -6; x++) for (int z = -15; z <= -9; z++) set(x, G, z, SLATE.at(x, G, z));
        // portes : nord (parking), ouest (jardin)
        wallDoor(-9, -17, Dir.NORTH, G);
        wallDoor(-14, -12, Dir.WEST, G);
    }

    private void garage() {
        annex(5, 16, -18, 52);
        for (int x = 7; x <= 14; x++) for (int z = -16; z <= -9; z++) set(x, G, z, SLATE.at(x, G, z));
        // grande ouverture nord (motoneiges), linteau et rideau releve
        for (int x = 9; x <= 12; x++) {
            for (int z = -18; z <= -16; z++) for (int y = G + 1; y <= G + 3; y++) set(x, y, z, B.AIR);
            set(x, G, -18, "minecraft:polished_andesite");
            set(x, G, -17, "minecraft:polished_andesite");
            set(x, G + 4, -17, trap("minecraft:spruce_trapdoor", Dir.NORTH, true, false));
            set(x, G + 4, -18, log("minecraft:stripped_dark_oak_log", 'x'));
        }
        for (int y = G + 1; y <= G + 4; y++) {
            set(8, y, -18, log("minecraft:stripped_dark_oak_log", 'y'));
            set(13, y, -18, log("minecraft:stripped_dark_oak_log", 'y'));
        }
    }

    private void porch() {
        for (int x = -1; x <= 4; x++)
            for (int z = -13; z <= -9; z++) {
                set(x, G, z, DECK.at(x, G, z));
                for (int y = G + 1; y <= G + 4; y++) set(x, y, z, B.AIR);
            }
        for (int[] p : new int[][]{{-1, -13}, {4, -13}, {-1, -9}, {4, -9}}) {
            for (int y = G + 1; y <= G + 3; y++) set(p[0], y, p[1], log("minecraft:spruce_log", 'y'));
            set(p[0], G, p[1], "minecraft:cobblestone");
        }
        // aisseliers
        set(0, G + 3, -13, stairs("minecraft:spruce_stairs", Dir.WEST, true));
        set(3, G + 3, -13, stairs("minecraft:spruce_stairs", Dir.EAST, true));
        gableZ(-1, 4, -14, -9, G + 5, 0); // sans debord : le garage est colle a l'est
        set(1, G + 1, -9, "minecraft:brown_carpet");
        set(2, G + 1, -9, "minecraft:brown_carpet");
        set(0, G + 1, -12, stairs("minecraft:spruce_stairs", Dir.WEST, false)); // banc
        set(0, G + 1, -11, stairs("minecraft:spruce_stairs", Dir.WEST, false));
        set(3, G + 1, -11, "minecraft:decorated_pot[facing=north,cracked=false,waterlogged=false]");
        // marches devant le porche
        for (int x = 1; x <= 2; x++) set(x, G, -14, stairs("minecraft:spruce_stairs", Dir.SOUTH, false));
    }

    // ================================================================ cloisons

    private void wallX(int x, int z1, int z2, int base, int top) {
        for (int z = z1; z <= z2; z++) for (int y = base + 1; y <= top; y++) set(x, y, z, PANEL.at(x, y, z));
    }

    private void wallZ(int z, int x1, int x2, int base, int top) {
        for (int x = x1; x <= x2; x++) for (int y = base + 1; y <= top; y++) set(x, y, z, PANEL.at(x, y, z));
    }

    /** Cloison de l'etage jusqu'au toit (rampants). */
    private void wallUpX(int x, int z1, int z2) {
        for (int z = z1; z <= z2; z++) for (int y = U + 1; y < roofPlank(z); y++) set(x, y, z, PANEL.at(x, y, z));
    }

    private void wallUpZ(int z, int x1, int x2) {
        for (int x = x1; x <= x2; x++) for (int y = U + 1; y < roofPlank(z); y++) set(x, y, z, PANEL.at(x, y, z));
    }

    private void door(int x, int z, Dir facing, int base) {
        set(x, base + 1, z, B.state(B.door(DOOR, facing, false, false), "open", "false"));
        set(x, base + 2, z, B.state(B.door(DOOR, facing, true, false), "open", "false"));
        set(x, base + 3, z, log("minecraft:stripped_dark_oak_log", facing.axis() == 'x' ? 'z' : 'x'));
    }

    private void opening(int x1, int z1, int x2, int z2, int base, int h) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) for (int y = base + 1; y <= base + h; y++) set(x, y, z, B.AIR);
    }

    private void partitions() {
        int top = U - 1;
        wallX(-7, IZ1, IZ2, G, top);
        wallZ(0, IX1, -8, G, top);
        wallZ(-1, -6, 6, G, top);
        wallX(7, IZ1, IZ2, G, top);
        wallZ(0, 8, IX2, G, top);
        door(-7, -4, Dir.WEST, G);       // entree -> cuisine
        door(-9, 0, Dir.SOUTH, G);       // cuisine -> salle a manger
        door(7, -4, Dir.EAST, G);        // entree -> buanderie
        door(14, 0, Dir.SOUTH, G);       // buanderie -> bureau
        // entree -> escalier de service : passage de 3 de haut (on monte sur la 1re marche juste derriere,
        // un linteau a G+3 cognait la tete)
        opening(7, -2, 7, -1, G, 3);
        set(7, G + 4, -2, log("minecraft:stripped_dark_oak_log", 'z'));
        set(7, G + 4, -1, log("minecraft:stripped_dark_oak_log", 'z'));
        // entree -> salon : deux passages de 2 sous des linteaux
        opening(-5, -1, -4, -1, G, 3);
        opening(4, -1, 6, -1, G, 3);
        for (int x : new int[]{-5, -4, 4, 5, 6}) set(x, G + 4, -1, log("minecraft:stripped_dark_oak_log", 'x'));
        // salon -> bureau : arche de 3
        opening(7, 2, 7, 4, G, 3);
        set(7, G + 4, 2, stairs("minecraft:spruce_stairs", Dir.SOUTH, true));
        set(7, G + 4, 4, stairs("minecraft:spruce_stairs", Dir.NORTH, true));
        set(7, G + 4, 3, log("minecraft:stripped_dark_oak_log", 'z'));
        // passe-plat cuisine / salle a manger
        set(-13, G + 2, 0, slab("minecraft:spruce_slab", "bottom"));
        set(-13, G + 3, 0, B.AIR);

        // etage
        wallUpX(-7, IZ1, IZ2);
        wallUpZ(0, IX1, -8);
        wallUpX(7, IZ1, IZ2);
        wallUpZ(0, 8, IX2);
        door(-7, 1, Dir.WEST, U);        // palier -> chambre des parents
        door(-7, -4, Dir.WEST, U);       // palier -> chambre des enfants
        door(-11, 0, Dir.SOUTH, U);      // parents <-> enfants
        door(7, 1, Dir.EAST, U);         // palier -> chambre d'amis
        door(7, -4, Dir.EAST, U);        // palier -> salle de bains
        door(9, 0, Dir.SOUTH, U);        // salle de bains <-> chambre d'amis
        // vide du salon (x -4..6, z 3..7) et garde-corps
        for (int x = -4; x <= 6; x++) for (int z = 3; z <= IZ2; z++) set(x, U, z, B.AIR);
        for (int x = -4; x <= 6; x++) {
            set(x, U, 2, FLOORUP.at(x, U, 2));
            set(x, U + 1, 2, "minecraft:spruce_fence");
            set(x, U - 1, 2, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true)); // nez du plancher
        }
        set(-4, U + 2, 2, standing(LANTERN));
        set(-4, U + 1, 2, "minecraft:spruce_log[axis=y]");
        set(6, U + 2, 2, standing(LANTERN));
        set(6, U + 1, 2, "minecraft:spruce_log[axis=y]");
    }

    // ================================================================ escaliers

    /**
     * Volee droite de n marches depuis (x0, z0) vers dir (largeur w vers la droite de dir), du niveau base au niveau
     * base + n : remplissage sous les marches, tremie dans le plancher du dessus, barrieres autour de la tremie.
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
                // 3e case libre (Esteban, 17/11) : en passant de la marche du dessus a celle-ci, la tete est encore
                // a la hauteur du dessus -> la tremie du plancher s'ouvre une marche plus tot
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

    /** Escalier principal du salon : x -6..-5, monte vers le nord de z 6 a z 1, palier de l'etage en z 0. */
    private void mainStair() {
        flight(-6, 6, Dir.NORTH, 2, G, 6, "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:spruce_fence");
        // limon cote salon (x -4) : planches sous les marches, rampe de barrieres
        for (int k = 0; k < 6; k++) {
            int z = 6 - k, y = G + 1 + k;
            for (int yy = G + 1; yy <= y - 1; yy++) set(-4, yy, z, log("minecraft:stripped_spruce_log", 'z'));
            if (k <= 3) set(-4, y, z, "minecraft:spruce_fence");
        }
        set(-4, G + 2, 6, standing(LANTERN));
    }

    // ================================================================ cheminee

    private void fireplace() {
        // massif de pierre x -2..2, z -2..-1 sur tout le rez, foyer ouvert au sud (salon)
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= -1; z++)
                for (int y = G + 1; y <= U - 1; y++) set(x, y, z, Math.abs(x) == 2 ? "minecraft:stone_bricks" : RUBBLE.at(x, y, z));
        for (int x = -1; x <= 1; x++) {
            set(x, G + 1, -1, x == 0 ? "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]" : log("minecraft:spruce_log", 'z'));
            set(x, G + 2, -1, B.AIR);
            set(x, G, -1, "minecraft:polished_blackstone");
            set(x, G + 3, -1, x == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
        }
        // manteau et ornements
        for (int x = -2; x <= 2; x++) set(x, G + 3, 0, stairs("minecraft:spruce_stairs", Dir.NORTH, true));
        set(-2, G + 4, 0, candles(3, "white"));
        set(2, G + 4, 0, candles(2, "white"));
        set(0, G + 4, 0, "minecraft:potted_spruce_sapling");
        set(-1, G + 4, 0, standing("minecraft:soul_lantern"));
        set(1, G + 4, 0, "minecraft:decorated_pot[facing=south,cracked=false,waterlogged=false]");
        // atre en dalles devant le foyer
        for (int x = -2; x <= 2; x++) set(x, G, 0, "minecraft:polished_deepslate");
        // dos du conduit cote entree : niche a bois
        set(-1, G + 1, -3, log("minecraft:oak_log", 'x'));
        set(0, G + 1, -3, log("minecraft:birch_log", 'x'));
        set(1, G + 1, -3, log("minecraft:oak_log", 'x'));
        set(0, G + 2, -3, log("minecraft:spruce_log", 'x'));
    }

    /** Conduit : traverse l'etage et le toit, souche couronnee, feu de camp au sommet (fumee). */
    private void chimney() {
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= -1; z++)
                for (int y = U; y <= 76; y++) set(x, y, z, Math.abs(x) == 2 ? "minecraft:stone_bricks" : RUBBLE.at(x, y, z));
        for (int x = -1; x <= 1; x++)
            for (int z = -2; z <= -1; z++)
                for (int y = 77; y <= 82; y++) set(x, y, z, y == 82 ? "minecraft:stone_bricks" : RUBBLE.at(x, y, z));
        // couronne : dalles autour du feu de camp (fumee visible la nuit), neige sur les dalles
        for (int x = -1; x <= 1; x++)
            for (int z = -2; z <= -1; z++)
                set(x, 83, z, x == 0 && z == -2 ? "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"
                        : slab("minecraft:cobblestone_slab", "bottom"));
        // a l'etage, face du conduit cote palier : poele en fonte et tuyau jusqu'au rampant
        set(0, U + 1, -3, "minecraft:blast_furnace[facing=north,lit=true]");
        for (int y = U + 2; y < roofPlank(-3); y++) set(0, y, -3, "minecraft:iron_chain[axis=y]");
    }

    // ================================================================ toit

    double roofS(int z) {
        int d = Math.min(z - Z1, Z2 - z);
        return ROOF0 + PITCH * d;
    }

    int roofPlank(int z) {
        return (int) Math.floor(roofS(z) + 1e-9) - 1;
    }

    private void roof() {
        for (int x = X1 - 2; x <= X2 + 1; x++)
            for (int z = Z1 - 1; z <= Z2 + 3; z++) {
                double sv = roofS(z);
                int fl = (int) Math.floor(sv + 1e-9);
                boolean edgeX = x == X1 - 2 || x == X2 + 1, edgeZ = z == Z1 - 1 || z == Z2 + 3;
                boolean inside = x > X1 && x < X2 && z > Z1 && z < Z2;
                String pl;
                if (edgeZ) pl = log("minecraft:stripped_spruce_log", 'x');
                else if (edgeX) pl = log("minecraft:stripped_spruce_log", 'z');
                else if (inside && Math.floorMod(x, 4) == 0) pl = log("minecraft:dark_oak_log", 'z'); // chevrons
                else pl = ROOFWOOD.at(x, fl - 1, z);
                set(x, fl - 1, z, pl);
                set(x, fl, z, snow(sv - fl));
                boolean overBalcony = z > Z2 && x >= X1 - 2;
                if ((edgeX && !overBalcony || z == Z1 - 1) && h(x, fl, z) < 0.35) icicle(x, fl - 2, z);
            }
        // consoles sous le debord sud (au-dessus du balcon) et nord
        for (int x = X1; x <= X2; x++) {
            if (Math.floorMod(x, 5) != 0) continue;
            set(x, roofPlank(Z2 + 1) - 1, Z2 + 1, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
            set(x, roofPlank(Z1 - 1) - 1, Z1 - 1, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, true));
        }
        // abouts de chevrons en saillie sous les rives (cassent la ligne du bord du toit)
        for (int x = X1; x <= X2; x++) {
            if (Math.floorMod(x, 4) != 0) continue;
            setIfAir(x, roofPlank(Z2 + 3), Z2 + 4, log("minecraft:dark_oak_log", 'z'));
            setIfAir(x, roofPlank(Z1 - 1), Z1 - 2, log("minecraft:dark_oak_log", 'z'));
        }
        // entraits apparents a hauteur de tete + 2 (traversent les cloisons et le vide du salon)
        for (int x = IX1; x <= IX2; x++)
            for (int z : new int[]{-4, 3}) setIfAir(x, U + 4, z, log("minecraft:stripped_dark_oak_log", 'x'));
        // panne faitiere apparente dans les combles
        for (int x = IX1; x <= IX2; x++) {
            if (isAir(x, roofPlank(0) - 1, 0)) set(x, roofPlank(0) - 1, 0, log("minecraft:stripped_dark_oak_log", 'x'));
            if (isAir(x, roofPlank(1) - 1, 1)) set(x, roofPlank(1) - 1, 1, log("minecraft:stripped_dark_oak_log", 'x'));
        }
    }

    // ================================================================ balcon, terrasse, escalier exterieur

    private void balcony() {
        for (int x = X1 - 1; x <= 20; x++)
            for (int z = Z2 + 1; z <= Z2 + 3; z++) set(x, U, z, slab("minecraft:spruce_slab", "top"));
        // garde-corps : barrieres + planches decoupees (trappes) facon chalet
        for (int x = X1 - 2; x <= 17; x++) {
            set(x, U, Z2 + 3, slab("minecraft:spruce_slab", "top"));
            set(x, U + 1, Z2 + 3, Math.floorMod(x, 5) == 0 ? "minecraft:spruce_log[axis=y]" : "minecraft:spruce_fence");
        }
        for (int z = Z2 + 1; z <= Z2 + 3; z++) {
            set(X1 - 2, U, z, slab("minecraft:spruce_slab", "top"));
            set(X1 - 2, U + 1, z, "minecraft:spruce_fence");
            set(20, U + 1, z, "minecraft:spruce_fence");
        }
        // poteaux sur la terrasse et aisseliers
        for (int x = X1; x <= X2; x += 5) {
            int px = x == X2 + 0 ? X2 : x;
            for (int y = G + 1; y <= U - 1; y++) set(px, y, Z2 + 3, log("minecraft:spruce_log", 'y'));
            setIfAir(px - 1, U - 1, Z2 + 3, stairs("minecraft:spruce_stairs", Dir.EAST, true));
            setIfAir(px + 1, U - 1, Z2 + 3, stairs("minecraft:spruce_stairs", Dir.WEST, true));
        }
        for (int y = G + 1; y <= U - 1; y++) set(19, y, Z2 + 3, log("minecraft:spruce_log", 'y'));
        // lanternes sous le balcon, jardinieres de sapins nains sur le balcon
        for (int x = X1 + 2; x <= X2; x += 6) hangLamp(x, U, Z2 + 2, LANTERN);
        for (int x : new int[]{-14, -6, 6, 14}) set(x, U + 1, Z2 + 1, "minecraft:potted_spruce_sapling");
        set(-3, U + 1, Z2 + 1, "minecraft:barrel[facing=up,open=false]");
        set(-3, U + 2, Z2 + 1, standing(LANTERN));
        armchair(4, U + 1, Z2 + 1, Dir.SOUTH, "spruce");
    }

    /** Escalier exterieur de la terrasse au balcon, a l'est (x 18..19, monte vers le nord de z 18 a z 13). */
    private void outerStair() {
        flight(18, 18, Dir.NORTH, 2, G, 6, "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:spruce_fence");
        for (int k = 0; k < 6; k++) {
            int z = 18 - k, y = G + 1 + k;
            for (int x : new int[]{17, 20}) {
                for (int yy = G + 1; yy <= y; yy++) set(x, yy, z, log("minecraft:stripped_spruce_log", 'z'));
                set(x, y + 1, z, "minecraft:spruce_fence");
            }
        }
        set(17, G + 2, 19, "minecraft:spruce_fence");
        set(17, G + 1, 19, "minecraft:spruce_log[axis=y]");
        set(20, G + 1, 19, "minecraft:spruce_log[axis=y]");
        set(20, G + 2, 19, standing(LANTERN));
    }

    private void terrace() {
        for (int x = X1 - 1; x <= 17; x++)
            for (int z = Z2 + 1; z <= 16; z++) set(x, G, z, z == 16 ? log("minecraft:stripped_spruce_log", 'x') : DECK.at(x, G, z));
        // tables, chaises longues, brasero, sapin decore
        cafeTable(-8, G + 1, 14, "spruce", true);
        cafeTable(8, G + 1, 14, "spruce", true);
        for (int x : new int[]{-13, -11}) {
            set(x, G + 1, 14, stairs("minecraft:spruce_stairs", Dir.NORTH, false));
            set(x, G + 1, 15, slab("minecraft:spruce_slab", "bottom"));
        }
        set(0, G + 1, 14, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        for (Dir d : Dir.values()) set(d.dx, G + 1, 14 + d.dz, slab("minecraft:cobblestone_slab", "bottom"));
        set(14, G + 1, 12, "minecraft:barrel[facing=up,open=false]");
        set(14, G + 2, 12, "minecraft:potted_fern");
        set(-15, G + 1, 12, "minecraft:spruce_log[axis=y]");
        set(-15, G + 2, 12, "minecraft:snow_block");
    }

    // ================================================================ interieurs : rez

    private void salon() {
        bench(0, G + 1, 4, Dir.NORTH, "spruce");
        armchair(-2, G + 1, 2, Dir.EAST, "dark_oak");
        armchair(3, G + 1, 2, Dir.WEST, "dark_oak");
        rug(-1, 1, 1, 2, G + 1, "minecraft:brown_carpet", "minecraft:white_carpet");
        set(0, G + 1, 2, trap("minecraft:spruce_trapdoor", Dir.NORTH, false, false)); // table basse
        set(3, G + 1, 0, log("minecraft:spruce_log", 'x'));
        set(3, G + 2, 0, log("minecraft:oak_log", 'x'));
        // lampadaire, piano (bloc musical + jukebox), plantes
        set(6, G + 1, 7, "minecraft:spruce_fence");
        set(6, G + 2, 7, "minecraft:spruce_fence");
        set(6, G + 3, 7, standing(LANTERN));
        set(6, G + 1, 1, "minecraft:note_block");
        set(5, G + 1, 1, "minecraft:jukebox");
        set(5, G + 2, 1, candles(2, "red"));
        bigPlant(6, G + 1, 5, "spruce");
        // tete de cerf (os) au-dessus de la baie interieure du conduit
        set(0, G + 5, 0, "minecraft:bone_block[axis=y]");
        set(-1, G + 5, 0, trap("minecraft:dark_oak_trapdoor", Dir.SOUTH, true, true));
        set(1, G + 5, 0, trap("minecraft:dark_oak_trapdoor", Dir.SOUTH, true, true));
        // grand lustre en bois de cerf, suspendu au faitage au-dessus du vide
        int ly = roofPlank(5) - 1;
        chandelier(1, ly, 5, ly - (U + 3), true, "minecraft:dark_oak_trapdoor", LANTERN);
        hangLamp(-1, U, 1, LANTERN);
    }

    private void hall() {
        bench(-3, G + 1, IZ1, Dir.SOUTH, "spruce");
        rug(-4, -4, 4, -3, G + 1, "minecraft:red_carpet", "minecraft:red_carpet");
        set(4, G + 1, IZ1, log("minecraft:stripped_dark_oak_log", 'y'));
        set(4, G + 2, IZ1, log("minecraft:stripped_dark_oak_log", 'y'));
        set(4, G + 3, IZ1, "minecraft:dark_oak_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]");
        set(5, G + 1, IZ1, "minecraft:decorated_pot[facing=south,cracked=false,waterlogged=false]");
        set(5, G + 2, IZ1, "minecraft:end_rod[facing=up]");
        set(-1, G + 1, IZ1, "minecraft:barrel[facing=up,open=false]");
        set(-1, G + 2, IZ1, "minecraft:potted_spruce_sapling");
        hangLamp(-3, U, -4, LANTERN);
        hangLamp(4, U, -4, LANTERN);
    }

    private void kitchen() {
        // plan de travail le long du mur nord (sous les fenetres)
        set(-14, G + 1, IZ1, "minecraft:smoker[facing=south,lit=true]");
        set(-13, G + 1, IZ1, "minecraft:furnace[facing=south,lit=false]");
        set(-12, G + 1, IZ1, "minecraft:water_cauldron[level=3]");
        set(-11, G + 1, IZ1, stairs("minecraft:spruce_stairs", Dir.NORTH, true));
        set(-10, G + 1, IZ1, stairs("minecraft:spruce_stairs", Dir.NORTH, true));
        set(-11, G + 2, IZ1, "minecraft:potted_red_tulip");
        set(-10, G + 2, IZ1, candles(2, "yellow"));
        set(-14, G + 1, -5, "minecraft:barrel[facing=east,open=false]");
        // refrigerateur
        set(-9, G + 1, IZ1, "minecraft:iron_block");
        set(-9, G + 2, IZ1, "minecraft:iron_block");
        set(-9, G + 3, IZ1, "minecraft:iron_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]");
        // ilot
        set(-11, G + 1, -4, stairs("minecraft:dark_oak_stairs", Dir.WEST, true));
        set(-10, G + 1, -4, stairs("minecraft:dark_oak_stairs", Dir.EAST, true));
        set(-11, G + 2, -4, "minecraft:white_carpet");
        // casseroles suspendues au-dessus de l'ilot, lustre
        set(-10, U - 1, -4, chain('y'));
        set(-10, U - 2, -4, hanging(LANTERN));
        hangLamp(-12, U, -2, "minecraft:soul_lantern");
    }

    private void dining() {
        for (int x = -12; x <= -10; x++)
            for (int z = 3; z <= 4; z++) set(x, G + 1, z, slab("minecraft:dark_oak_slab", "top"));
        for (int x = -12; x <= -10; x++) {
            set(x, G + 1, 2, stairs("minecraft:spruce_stairs", Dir.NORTH, false));
            set(x, G + 1, 5, stairs("minecraft:spruce_stairs", Dir.SOUTH, false));
        }
        set(-9, G + 1, 4, stairs("minecraft:spruce_stairs", Dir.EAST, false));
        set(-11, G + 2, 3, candles(3, null));
        set(-12, G + 2, 4, "minecraft:white_carpet");
        set(-10, G + 2, 3, "minecraft:white_carpet");
        // buffet contre le mur ouest (entre les fenetres)
        for (int z = 3; z <= 4; z++) {
            set(-14, G + 1, z, "minecraft:barrel[facing=east,open=false]");
            set(-14, G + 2, z, stairs("minecraft:spruce_stairs", Dir.WEST, true));
        }
        set(-14, G + 3, 3, "minecraft:potted_azalea_bush");
        set(-14, G + 3, 4, candles(2, "red"));
        chandelier(-11, U - 1, 4, 1, false, "minecraft:spruce_trapdoor", LANTERN);
        set(-9, G + 1, 7, "minecraft:spruce_log[axis=y]");
        set(-9, G + 2, 7, standing(LANTERN));
    }

    private void laundry() {
        set(13, G + 1, IZ1, "minecraft:blast_furnace[facing=south,lit=true]");
        set(14, G + 1, IZ1, "minecraft:water_cauldron[level=2]");
        set(8, G + 1, IZ1, "minecraft:barrel[facing=south,open=false]");
        set(9, G + 1, IZ1, "minecraft:composter[level=0]");
        set(11, G + 1, IZ1, "minecraft:barrel[facing=south,open=false]");
        // fil a linge (chaine) avec draps (banniere impossible : laine blanche suspendue)
        for (int x = 9; x <= 13; x++) set(x, G + 4, -5, chain('x'));
        set(10, G + 3, -5, "minecraft:white_wool");
        set(12, G + 3, -5, "minecraft:light_blue_wool");
        set(8, G + 4, -5, "minecraft:spruce_planks");
        set(14, G + 4, -5, "minecraft:spruce_planks");
        hangLamp(11, U, -3, LANTERN);
    }

    private void study() {
        // la fenetre est (z 0..1) donnait dans la cloison et sur la porte : rebouchee
        for (int y = G + 1; y <= G + 4; y++)
            for (int z = 0; z <= 1; z++) {
                if (z == 1) set(14, y, z, PANEL.at(14, y, z));
                set(15, y, z, RUBBLE.at(15, y, z));
                if (B.name(get(16, y, z)).endsWith("trapdoor") || B.name(get(16, y, z)).endsWith("stairs")) set(16, y, z, B.AIR);
            }
        set(14, G + 1, 1, B.AIR);
        set(14, G + 2, 1, B.AIR);
        // bureau contre la cloison nord, bibliotheques sur le mur est
        for (int x = 10; x <= 12; x++) set(x, G + 1, 1, stairs("minecraft:dark_oak_stairs", Dir.NORTH, true));
        set(12, G + 2, 1, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        set(10, G + 2, 1, candles(2, "white"));
        armchair(11, G + 1, 2, Dir.NORTH, "dark_oak");
        for (int y = G + 1; y <= G + 3; y++)
            for (int z = 3; z <= 4; z++) set(14, y, z, "minecraft:bookshelf");
        for (int y = G + 1; y <= G + 3; y++) set(14, y, 7, "minecraft:bookshelf");
        set(9, G + 1, 7, "minecraft:bookshelf");
        set(9, G + 2, 7, "minecraft:decorated_pot[facing=north,cracked=false,waterlogged=false]");
        armchair(10, G + 1, 6, Dir.NORTH, "spruce");
        rug(9, 2, 12, 5, G + 1, "minecraft:green_carpet", "minecraft:lime_carpet");
        set(8, G + 1, 1, "minecraft:spruce_fence");
        set(8, G + 2, 1, standing(LANTERN));
        hangLamp(11, U, 3, LANTERN);
    }

    private void skiRoomInside() {
        String[] skis = {"minecraft:birch_trapdoor", "minecraft:crimson_trapdoor", "minecraft:warped_trapdoor", "minecraft:spruce_trapdoor"};
        int i = 0;
        for (int x = -12; x <= -6; x++) {
            if (x == -9 || x == -11 || x == -7) continue; // porte et fenetres
            String t = skis[i++ % skis.length];
            set(x, G + 1, -15, trap(t, Dir.SOUTH, false, true));
            set(x, G + 2, -15, trap(t, Dir.SOUTH, true, true));
            set(x, G + 3, -15, "minecraft:spruce_fence");
        }
        bench(-9, G + 1, -12, Dir.NORTH, "spruce");
        set(-12, G + 1, -9, "minecraft:blast_furnace[facing=east,lit=true]");
        set(-12, G + 2, -9, "minecraft:iron_bars");
        set(-6, G + 1, -11, "minecraft:barrel[facing=west,open=false]");
        set(-6, G + 1, -10, "minecraft:barrel[facing=west,open=false]");
        set(-6, G + 2, -11, "minecraft:white_carpet");
        set(-6, G + 1, -14, "minecraft:spruce_fence");
        set(-12, G + 1, -14, "minecraft:spruce_fence");
        hangLamp(-9, gzPlank(-9, -14, -4, G + 7), -12, LANTERN);
    }

    private void garageInside() {
        snowmobile(8, -15, Dir.SOUTH, "red");
        snowmobile(13, -15, Dir.SOUTH, "black");
        set(7, G + 1, -11, "minecraft:crafting_table");
        set(7, G + 1, -10, "minecraft:smithing_table");
        set(7, G + 1, -12, "minecraft:grindstone[face=floor,facing=east]");
        set(7, G + 1, -9, "minecraft:anvil[facing=east]");
        for (int z = -12; z <= -9; z++) if (isAir(7, G + 3, z)) set(7, G + 3, z, trap("minecraft:spruce_trapdoor", Dir.EAST, true, false));
        set(14, G + 1, -10, "minecraft:barrel[facing=up,open=false]");
        set(14, G + 1, -9, "minecraft:barrel[facing=up,open=false]");
        set(14, G + 2, -10, "minecraft:red_carpet");
        set(14, G + 1, -11, "minecraft:cauldron");
        hangLamp(10, gzPlank(10, 5, 16, G + 7), -12, LANTERN);
        hangLamp(11, gzPlank(11, 5, 16, G + 7), -14, LANTERN);
    }

    /** Motoneige : capot de couleur, pare-brise, selle, chenille, skis. */
    private void snowmobile(int x, int z, Dir dir, String color) {
        Dir side = dir.cw();
        String body = "minecraft:" + color + "_concrete";
        // avant : skis (trappes de fer a plat) de chaque cote, capot et phare
        set(x + side.dx, G + 1, z + side.dz, "minecraft:iron_trapdoor[facing=" + dir.id() + ",half=bottom,open=false,powered=false,waterlogged=false]");
        set(x - side.dx, G + 1, z - side.dz, "minecraft:iron_trapdoor[facing=" + dir.id() + ",half=bottom,open=false,powered=false,waterlogged=false]");
        set(x, G + 1, z, slab("minecraft:" + (color.equals("black") ? "blackstone" : "red_nether_brick") + "_slab", "bottom"));
        int x1 = x + dir.dx, z1 = z + dir.dz;
        set(x1, G + 1, z1, body);
        set(x1, G + 2, z1, "minecraft:glass_pane");
        int x2 = x + 2 * dir.dx, z2 = z + 2 * dir.dz;
        set(x2, G + 1, z2, stairs("minecraft:blackstone_stairs", dir, false));
        int x3 = x + 3 * dir.dx, z3 = z + 3 * dir.dz;
        set(x3, G + 1, z3, slab("minecraft:polished_blackstone_slab", "bottom"));
    }

    // ================================================================ interieurs : cave

    private void wineCellar() {
        // casiers a bouteilles le long du mur ouest (passage libre sous l'escalier en z -2..-1)
        for (int z : new int[]{-6, -5, -4, 0, 1, 5, 6, 7}) {
            set(-14, C + 1, z, "minecraft:barrel[facing=east,open=false]");
            set(-14, C + 2, z, "minecraft:barrel[facing=east,open=false]");
            set(-14, C + 3, z, slab("minecraft:spruce_slab", "bottom"));
        }
        // foudres couches au sud
        for (int x = -12; x <= -8; x += 2) {
            set(x, C + 1, 7, "minecraft:barrel[facing=north,open=false]");
            set(x, C + 2, 7, "minecraft:barrel[facing=north,open=false]");
            set(x + 1, C + 1, 7, log("minecraft:stripped_spruce_log", 'z'));
        }
        // casier central (deux faces) au nord, table de degustation
        for (int x = -12; x <= -9; x++) {
            set(x, C + 1, -5, "minecraft:barrel[facing=south,open=false]");
            set(x, C + 2, -5, trap("minecraft:spruce_trapdoor", Dir.NORTH, false, false));
        }
        for (int x = -11; x <= -10; x++) {
            set(x, C + 1, 5, "minecraft:barrel[facing=north,open=false]");
            set(x, C + 2, 5, "minecraft:barrel[facing=north,open=false]");
        }
        set(-8, C + 1, 3, "minecraft:barrel[facing=up,open=false]");
        set(-8, C + 2, 3, candles(3, "red"));
        set(-8, C + 1, 2, stairs("minecraft:spruce_stairs", Dir.NORTH, false));
        set(-8, C + 1, 4, stairs("minecraft:spruce_stairs", Dir.SOUTH, false));
        hangLamp(-11, G, -3, LANTERN);
        hangLamp(-11, G, 4, LANTERN);
        hangLamp(-7, G, -2, LANTERN);
    }

    private void gameRoom() {
        // billard
        for (int x = -2; x <= 0; x++)
            for (int z = -1; z <= 2; z++) {
                boolean rim = x == -2 || x == 0 || z == -1 || z == 2;
                set(x, C + 1, z, rim ? "minecraft:dark_oak_planks" : "minecraft:green_wool");
            }
        set(-1, C + 2, 0, "minecraft:white_carpet");
        set(-4, C + 2, 0, B.AIR);
        hangLamp(-1, G, -1, LANTERN);
        hangLamp(-1, G, 2, LANTERN);
        // queues au mur, cible de flechettes
        for (int z = -5; z <= -3; z++) set(-4, C + 1, z, "minecraft:spruce_fence");
        set(6, C + 2, 3, "minecraft:target");
        // bar contre le mur sud
        for (int x = 1; x <= 3; x++) {
            set(x, C + 1, 6, stairs("minecraft:dark_oak_stairs", Dir.SOUTH, true));
            set(x, C + 1, 7, "minecraft:barrel[facing=up,open=false]");
            set(x, C + 1, 5, stairs("minecraft:spruce_stairs", Dir.SOUTH, false));
        }
        set(1, C + 2, 6, candles(2, "yellow"));
        set(3, C + 2, 6, "minecraft:potted_fern");
        set(2, C + 2, 7, "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]");
        // table de ping-pong
        for (int x = 3; x <= 4; x++)
            for (int z = -3; z <= -1; z++) set(x, C + 1, z, z == -2 ? "minecraft:white_wool" : "minecraft:green_concrete");
        // chaudiere et tuyaux
        set(4, C + 1, -6, "minecraft:blast_furnace[facing=south,lit=true]");
        set(5, C + 1, -6, "minecraft:copper_block");
        for (int y = C + 2; y <= G - 1; y++) set(5, y, -6, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        set(-1, C + 1, -6, "minecraft:jukebox");
        set(-1, C + 2, -6, candles(1, "red"));
        bench(-3, C + 1, 4, Dir.EAST, "dark_oak");
        // skis et luges rangees
        set(5, C + 1, 1, trap("minecraft:birch_trapdoor", Dir.WEST, false, true));
        set(5, C + 2, 1, trap("minecraft:birch_trapdoor", Dir.WEST, true, true));
        set(5, C + 1, 2, trap("minecraft:crimson_trapdoor", Dir.WEST, false, true));
        set(5, C + 2, 2, trap("minecraft:crimson_trapdoor", Dir.WEST, true, true));
        set(5, C + 1, -3, "minecraft:barrel[facing=west,open=false]");
        hangLamp(-3, G, -4, "minecraft:soul_lantern");
        hangLamp(3, G, -2, LANTERN);
        hangLamp(3, G, 4, LANTERN);
    }

    // ================================================================ interieurs : etage

    private void landing() {
        armchair(-5, U + 1, IZ1, Dir.SOUTH, "spruce");
        armchair(5, U + 1, IZ1, Dir.SOUTH, "spruce");
        for (int x = -2; x <= 2; x++) set(x, U + 1, IZ1, "minecraft:bookshelf");
        rug(-4, -5, -2, -4, U + 1, "minecraft:blue_carpet", "minecraft:light_blue_carpet");
        set(3, U + 1, -5, "minecraft:chest[facing=south,type=single,waterlogged=false]");
        // cheval a bascule
        set(-5, U + 1, -1, "minecraft:spruce_fence");
        set(-5, U + 2, -1, "minecraft:brown_wool");
        set(-5, U + 3, -1, "minecraft:brown_carpet");
        set(4, U + 1, 0, "minecraft:barrel[facing=up,open=false]");
        set(4, U + 2, 0, standing(LANTERN));
        hangLamp(-3, roofPlank(-3), -3, LANTERN);
        hangLamp(4, roofPlank(-3), -3, LANTERN);
        hangLamp(-1, roofPlank(1), 1, LANTERN);
    }

    private void bed(int x, int y, int zFoot, Dir head, String color) {
        set(x, y, zFoot, "minecraft:" + color + "_bed[facing=" + head.id() + ",occupied=false,part=foot]");
        set(x + head.dx, y, zFoot + head.dz, "minecraft:" + color + "_bed[facing=" + head.id() + ",occupied=false,part=head]");
    }

    private void parentsRoom() {
        bed(-13, U + 1, 3, Dir.WEST, "red");
        bed(-13, U + 1, 4, Dir.WEST, "red");
        set(-14, U + 1, 2, "minecraft:barrel[facing=up,open=false]");
        set(-14, U + 2, 2, candles(2, "white"));
        set(-14, U + 1, 5, "minecraft:barrel[facing=up,open=false]");
        set(-14, U + 2, 5, standing(LANTERN));
        set(-12, U + 1, 3, "minecraft:white_carpet");
        rug(-12, 2, -9, 6, U + 1, "minecraft:red_carpet", "minecraft:white_carpet");
        set(-8, U + 1, 7, log("minecraft:stripped_dark_oak_log", 'y'));
        set(-8, U + 2, 7, log("minecraft:stripped_dark_oak_log", 'y'));
        set(-9, U + 1, 7, "minecraft:chest[facing=north,type=single,waterlogged=false]");
        hangLamp(-11, roofPlank(4), 4, LANTERN);
    }

    private void kidsRoom() {
        for (int bx : new int[]{-14, -10}) {
            bed(bx, U + 1, -5, Dir.NORTH, bx == -14 ? "blue" : "yellow");
            bed(bx, U + 3, -5, Dir.NORTH, bx == -14 ? "light_blue" : "orange");
            set(bx + 1, U + 1, -5, "minecraft:spruce_fence");
            set(bx + 1, U + 2, -5, "minecraft:spruce_fence");
            set(bx + 1, U + 3, -5, slab("minecraft:spruce_slab", "bottom"));
        }
        set(-13, U + 1, -1, "minecraft:chest[facing=north,type=single,waterlogged=false]");
        set(-9, U + 1, -1, "minecraft:barrel[facing=up,open=false]");
        set(-9, U + 2, -1, "minecraft:potted_cornflower");
        rug(-13, -3, -10, -2, U + 1, "minecraft:yellow_carpet", "minecraft:lime_carpet");
        hangLamp(-11, roofPlank(-3), -3, LANTERN);
    }

    private void bathroom() {
        for (int x = 8; x <= IX2; x++) for (int z = IZ1; z <= -3; z++) set(x, U, z, TILE.at(x, U, z));
        set(13, U + 1, IZ1, "minecraft:water_cauldron[level=3]");
        set(14, U + 1, IZ1, "minecraft:water_cauldron[level=3]");
        set(8, U + 1, IZ1, "minecraft:cauldron");
        set(8, U + 2, IZ1, "minecraft:light_blue_stained_glass_pane");
        set(10, U + 1, IZ1, "minecraft:barrel[facing=south,open=false]");
        set(11, U + 1, IZ1, "minecraft:barrel[facing=south,open=false]");
        set(10, U + 2, IZ1, "minecraft:white_carpet");
        set(11, U + 2, IZ1, "minecraft:light_blue_carpet");
        set(14, U + 1, -4, "minecraft:potted_fern");
        hangLamp(11, roofPlank(-5), -5, LANTERN);
    }

    private void guestRoom() {
        bed(13, U + 1, 3, Dir.EAST, "green");
        bed(13, U + 1, 5, Dir.EAST, "green");
        set(14, U + 1, 4, "minecraft:barrel[facing=up,open=false]");
        set(14, U + 2, 4, standing(LANTERN));
        set(8, U + 1, 6, stairs("minecraft:spruce_stairs", Dir.WEST, true));
        set(8, U + 2, 6, candles(1, "green"));
        set(14, U + 1, 7, log("minecraft:stripped_dark_oak_log", 'y'));
        set(14, U + 2, 7, log("minecraft:stripped_dark_oak_log", 'y'));
        rug(10, 2, 12, 6, U + 1, "minecraft:green_carpet", "minecraft:white_carpet");
        hangLamp(11, roofPlank(4), 4, LANTERN);
    }

    // ================================================================ dehors

    private void paths() {
        // allee du portail (route) au porche, et du garage a la route ; sentier autour du chalet
        for (int z = BZ1 + 1; z <= -15; z++)
            for (int x = 0; x <= 3; x++) pathAt(x, z);
        for (int z = BZ1 + 1; z <= -19; z++)
            for (int x = 8; x <= 13; x++) pathAt(x, z);
        for (int x = -30; x <= -18; x++) for (int z = -27; z <= -20; z++) pathAt(x, z); // parking
        for (int x = -17; x <= 30; x++) for (int z = 17; z <= 18; z++) pathAt(x, z);
        for (int z = -16; z <= 18; z++) for (int x = -24; x <= -23; x++) pathAt(x, z);
        for (int z = -24; z <= 18; z++) for (int x = 22; x <= 23; x++) if (!(z >= -8 && z <= 2)) pathAt(x, z);
    }

    private void pathAt(int x, int z) {
        if (!inBox(x, z)) return;
        int t = top(x, z);
        String cur = get(x, t, z);
        if (!cur.contains("snow") && !cur.contains("concrete_powder") && !cur.contains("calcite")) return;
        if (!isAir(x, t + 1, z)) return;
        set(x, t, z, Math.floorMod((int) (noise.uniform2(x / 3.0, z / 3.0) * 100), 7) == 0 ? SNOW.at(x, t, z) : PATH.at(x, t, z));
    }

    /** Sauna en rondins (x -30..-22, z 11..18), poele, gradins ; bain nordique a cote. */
    private void sauna() {
        int x1 = -30, x2 = -22, z1 = 11, z2 = 18;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                set(x, G, z, wall ? PLINTH.at(x, G, z) : "minecraft:spruce_planks");
                for (int y = G + 1; y <= G + 4; y++) {
                    if (corner) set(x, y, z, log("minecraft:spruce_log", 'y'));
                    else if (wall) set(x, y, z, log("minecraft:spruce_log", x == x1 || x == x2 ? 'z' : 'x'));
                    else set(x, y, z, B.AIR);
                }
            }
        // toit a deux pans, faitage est-ouest, debord 1
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                int d = Math.min(z - z1, z2 - z);
                double sv = G + 6 + PITCH * d;
                int fl = (int) Math.floor(sv + 1e-9);
                set(x, fl - 1, z, z == z1 - 1 || z == z2 + 1 ? log("minecraft:stripped_spruce_log", 'x') : ROOFWOOD.at(x, fl - 1, z));
                set(x, fl, z, snow(sv - fl));
                for (int y = G + 5; y < fl - 1; y++) if (x == x1 || x == x2) set(x, y, z, log("minecraft:spruce_log", 'z'));
            }
        for (int z = z1; z <= z2; z++) for (int x = x1 + 1; x <= x2 - 1; x++) set(x, G + 5, z, "minecraft:spruce_planks");
        // portes : est (vers le bain), nord (vers le jardin)
        for (int y = G + 1; y <= G + 2; y++) { set(x2, y, 14, B.AIR); set(-26, y, z1, B.AIR); }
        set(x2, G + 1, 14, B.state(B.door(DOOR, Dir.WEST, false, false), "open", "false"));
        set(x2, G + 2, 14, B.state(B.door(DOOR, Dir.WEST, true, false), "open", "false"));
        set(-26, G + 1, z1, B.state(B.door(DOOR, Dir.SOUTH, false, false), "open", "false"));
        set(-26, G + 2, z1, B.state(B.door(DOOR, Dir.SOUTH, true, false), "open", "false"));
        // fenetre
        set(-27, G + 2, z2, "minecraft:glass_pane");
        set(-25, G + 2, z2, "minecraft:glass_pane");
        // gradins, poele a pierres, seau
        for (int x = -29; x <= -24; x++) {
            set(x, G + 1, 17, slab("minecraft:spruce_slab", "top"));
            set(x, G + 1, 16, slab("minecraft:spruce_slab", "bottom"));
        }
        set(-29, G + 1, 12, "minecraft:blast_furnace[facing=east,lit=true]");
        set(-29, G + 2, 12, "minecraft:cobblestone_wall");
        set(-28, G + 1, 12, "minecraft:water_cauldron[level=3]");
        hangLamp(-26, G + 5, 14, LANTERN);
        // souche de cheminee
        for (int y = G + 5; y <= G + 10; y++) set(-29, y, 12, y == G + 10 ? "minecraft:cobblestone_slab[type=bottom]" : "minecraft:cobblestone");
        set(-21, G + 1, 12, "minecraft:spruce_log[axis=y]");
        set(-21, G + 2, 12, standing(LANTERN));
    }

    private void hotTub() {
        int x1 = -21, x2 = -17, z1 = 13, z2 = 17;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, G, z, rim ? "minecraft:spruce_planks" : B.WATER);
                set(x, G - 1, z, "minecraft:spruce_planks");
                set(x, G + 1, z, rim ? slab("minecraft:spruce_slab", "bottom") : B.AIR);
            }
        set(-19, G - 1, 15, "minecraft:sea_lantern");
        set(-17, G + 1, 13, "minecraft:spruce_log[axis=y]");
        set(-17, G + 2, 13, standing(LANTERN));
    }

    /** Abri de bois ouvert a l'ouest (x 21..28, z -8..2) : poteaux, appentis enneige, piles de buches. */
    private void woodshed() {
        int x1 = 21, x2 = 28, z1 = -8, z2 = 2;
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) set(x, G, z, x == x2 ? "minecraft:cobblestone" : PATH.at(x, G, z));
        for (int z = z1; z <= z2; z += 5)
            for (int x : new int[]{x1, x2})
                for (int y = G + 1; y <= G + 4; y++) set(x, y, z, log("minecraft:spruce_log", 'y'));
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                double sv = G + 5 + (x - x1) * 0.35;
                int fl = (int) Math.floor(sv + 1e-9);
                set(x, fl - 1, z, x == x1 - 1 ? log("minecraft:stripped_spruce_log", 'z') : ROOFWOOD.at(x, fl - 1, z));
                set(x, fl, z, snow(sv - fl));
                if (x == x1 - 1 && h(x, fl, z) < 0.4) icicle(x, fl - 2, z);
            }
        // planches du fond et des cotes
        for (int z = z1; z <= z2; z++) for (int y = G + 1; y <= G + 6; y++) if (isAir(x2, y, z) && y < G + 5 + (x2 - x1) * 0.35 - 1) set(x2, y, z, BOARDS.at(x2, y, z));
        for (int x = x1 + 1; x < x2; x++) for (int y = G + 1; y <= G + 2; y++) { set(x, y, z1, BOARDS.at(x, y, z1)); set(x, y, z2, BOARDS.at(x, y, z2)); }
        // piles de buches contre le fond, pile isolee, billot
        String[] logs = {"minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log"};
        for (int z = z1 + 1; z <= z2 - 1; z++)
            for (int y = G + 1; y <= G + 3; y++) {
                if (y == G + 3 && h(27, y, z) < 0.3) continue;
                set(27, y, z, log(logs[(int) (h(27, y, z) * 3)], 'z'));
                if (y <= G + 2) set(26, y, z, log(logs[(int) (h(26, y, z) * 3)], 'z'));
            }
        for (int z = -5; z <= -3; z++) for (int y = G + 1; y <= G + 2; y++) set(23, y, z, log(logs[(int) (h(23, y, z) * 3)], 'x'));
        set(24, G + 1, 0, log("minecraft:stripped_oak_log", 'y'));
        set(24, G + 2, 0, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]"); // hache plantee (manche)
        set(22, G + 1, 1, "minecraft:cauldron"); // brouette

        hangLamp(24, (int) Math.floor(G + 5 + 3 * 0.35 + 1e-9) - 1, -3, LANTERN);
    }

    private void pond() {
        // trou de peche dans la glace, siege, bordure de joncs morts
        set(24, G - 2, 23, B.WATER);
        set(25, G - 1, 23, "minecraft:barrel[facing=up,open=false]");
        set(23, G - 1, 22, "minecraft:spruce_fence");
        set(23, G, 22, standing(LANTERN));
        // berge : petites bordures de neige tassee
        for (int x = 15; x <= 33; x++)
            for (int z = 18; z <= 28; z++) {
                if (inPond(x, z) || !inBox(x, z)) continue;
                boolean edge = false;
                for (Dir d : Dir.values()) if (inPond(x + d.dx, z + d.dz)) edge = true;
                if (edge && h(x, 3, z) < 0.3 && isAir(x, top(x, z) + 1, z)) set(x, top(x, z) + 1, z, "minecraft:snow[layers=2]");
            }
    }

    private void props() {
        // bonhommes de neige
        snowman(-6, 23, Dir.NORTH);
        snowman(19, -21, Dir.WEST);
        // luge
        sled(9, 22, Dir.EAST);
        // voiture au parking
        car(-23, -24, Dir.EAST, "blue");
        // motoneiges devant le garage
        snowmobile(18, -16, Dir.NORTH, "yellow");
        snowmobile(21, -16, Dir.NORTH, "red");
        // bancs, tables de pique-nique, panneau
        bench(-10, top(-10, 22) + 1, 22, Dir.SOUTH, "spruce");
        bench(10, top(10, 26) + 1, 26, Dir.NORTH, "spruce");
        cafeTable(-14, top(-14, 24) + 1, 24, "spruce", true);
        set(2, G + 1, -27, "minecraft:spruce_fence");
        set(2, G + 2, -27, "minecraft:spruce_fence");
        set(2, G + 3, -27, "minecraft:spruce_planks");
        set(3, G + 3, -27, trap("minecraft:spruce_trapdoor", Dir.EAST, false, true));
        // boite aux lettres
        set(-2, G + 1, -28, "minecraft:spruce_fence");
        set(-2, G + 2, -28, "minecraft:barrel[facing=north,open=false]");
        // rochers enneiges
        rock(-28, -5, 2);
        rock(28, 19, 2);
        rock(-14, -24, 1);
        rock(28, -26, 2);
        rock(-29, 24, 2);
        rock(5, 25, 1);
        // tas de buches le long du chalet (ouest) et du garage
        for (int z = 5; z <= 7; z++) for (int y = G + 1; y <= G + 2; y++) set(-18, y, z, log("minecraft:spruce_log", 'x'));
        for (int x = 18; x <= 20; x++) for (int y = G + 1; y <= G + 2; y++) set(x, y, -11, log("minecraft:oak_log", 'z'));
        // tremplin de neige pour les luges
        for (int x = -8; x <= -5; x++) set(x, top(x, 26) + 1, 26, x == -5 ? "minecraft:snow_block" : "minecraft:snow[layers=4]");
        // lampadaires
        int[][] lamps = {{-3, -24}, {6, -24}, {-16, -20}, {16, -22}, {-27, -9}, {-26, 6}, {26, -14}, {27, 10}, {-14, 18},
                {12, 18}, {-2, 25}, {16, 26}, {-30, 21}};
        for (int[] l : lamps) {
            int t = top(l[0], l[1]);
            if (!isAir(l[0], t + 1, l[1])) continue;
            lampPost(l[0], t + 1, l[1], 3, "minecraft:spruce_fence", "minecraft:cobblestone_wall", false);
        }
    }

    private void snowman(int x, int z, Dir face) {
        int t = top(x, z);
        set(x, t + 1, z, "minecraft:snow_block");
        set(x, t + 2, z, "minecraft:snow_block");
        set(x, t + 3, z, "minecraft:carved_pumpkin[facing=" + face.id() + "]");
        Dir s = face.cw();
        setIfAir(x + s.dx, t + 2, z + s.dz, "minecraft:spruce_fence");
        setIfAir(x - s.dx, t + 2, z - s.dz, "minecraft:spruce_fence");
    }

    private void sled(int x, int z, Dir dir) {
        int t = top(x, z);
        for (int i = 0; i < 3; i++) set(x + dir.dx * i, t + 1, z + dir.dz * i, i == 2 ? stairs("minecraft:spruce_stairs", dir.opposite(), false) : slab("minecraft:spruce_slab", "bottom"));
        set(x + dir.dx, t + 2, z + dir.dz, "minecraft:red_carpet");
    }

    /** 4x4 garee : carrosserie, vitres, toit enneige, phares. */
    private void car(int x, int z, Dir dir, String color) {
        Dir side = dir.cw();
        String body = "minecraft:" + color + "_concrete";
        for (int i = 0; i < 5; i++)
            for (int j = -1; j <= 1; j++) {
                int px = x + dir.dx * i + side.dx * j, pz = z + dir.dz * i + side.dz * j;
                boolean wheel = (i == 0 || i == 4) && j != 0;
                set(px, G + 1, pz, wheel ? "minecraft:polished_blackstone" : body);
                if (i >= 1 && i <= 3) set(px, G + 2, pz, j == 0 ? body : "minecraft:glass_pane");
            }
        for (int i = 1; i <= 3; i++) set(x + dir.dx * i, G + 2, z + dir.dz * i, body);
        for (int i = 1; i <= 3; i++) set(x + dir.dx * i, G + 3, z + dir.dz * i, "minecraft:snow[layers=3]");
        for (int j : new int[]{-1, 1}) {
            int px = x + dir.dx * 5 + side.dx * j, pz = z + dir.dz * 5 + side.dz * j;
            setIfAir(px, G + 1, pz, "minecraft:end_rod[facing=" + dir.id() + "]");
        }
    }

    private void rock(int cx, int cz, int r) {
        int t = top(cx, cz);
        for (int x = cx - r - 1; x <= cx + r + 1; x++)
            for (int z = cz - r - 1; z <= cz + r + 1; z++)
                for (int y = t + 1; y <= t + r + 1; y++) {
                    double dx = x - cx, dy = (y - t - 0.5) * 1.3, dz = z - cz;
                    double rr = r + 0.6 + 0.6 * (noise.uniform3(x / 2.0, y / 2.0, z / 2.0) - 0.5);
                    if (dx * dx + dy * dy + dz * dz <= rr * rr && inBox(x, z)) set(x, y, z, ROCK.at(x, y, z));
                }
        for (int x = cx - r - 1; x <= cx + r + 1; x++)
            for (int z = cz - r - 1; z <= cz + r + 1; z++) {
                int y = t + r + 2;
                while (y > t && isAir(x, y - 1, z)) y--;
                if (y > t + 1 && isAir(x, y, z) && B.name(get(x, y - 1, z)).matches("stone|andesite|cobblestone|tuff")) set(x, y, z, "minecraft:snow[layers=2]");
            }
    }

    /** Sapin en cone a etages, neige sur les branches. */
    private void spruce(int x, int y, int z, int hgt, long seed) {
        double maxR = 1.4 + hgt * 0.32;
        for (int i = 0; i < hgt; i++) set(x, y + i, z, log("minecraft:spruce_log", 'y'));
        String leaf = B.leaves("minecraft:spruce_leaves");
        for (int i = 2; i <= hgt + 1; i++) {
            int yy = y + i;
            double t = (i - 2) / (double) (hgt - 1);
            double r = maxR * (1 - t) + 0.4;
            if ((i & 1) == 1) r -= 0.9; // etages
            int R = (int) Math.ceil(r);
            for (int dx = -R; dx <= R; dx++)
                for (int dz = -R; dz <= R; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz) + 0.35 * (Noise01(seed, x + dx, yy, z + dz) - 0.5);
                    if (d > r || (dx == 0 && dz == 0 && i < hgt)) continue;
                    setIfAir(x + dx, yy, z + dz, leaf);
                }
        }
        set(x, y + hgt + 2, z, leaf);
        // neige sur les branches exposees
        for (int dx = -(int) maxR - 1; dx <= maxR + 1; dx++)
            for (int dz = -(int) maxR - 1; dz <= maxR + 1; dz++)
                for (int yy = y + hgt + 3; yy >= y + 2; yy--) {
                    String b = get(x + dx, yy, z + dz);
                    if (B.isAir(b)) continue;
                    if (B.name(b).equals("spruce_leaves") && isAir(x + dx, yy + 1, z + dz) && Noise01(seed + 5, x + dx, yy, z + dz) < 0.8)
                        set(x + dx, yy + 1, z + dz, "minecraft:snow[layers=" + (1 + (int) (Noise01(seed + 9, x + dx, yy, z + dz) * 2)) + "]");
                    break;
                }
    }

    private double Noise01(long seed, int x, int y, int z) {
        return fr.eemicraft.murder.build.Noise.hash(seed, x, y, z);
    }

    private void trees() {
        int[][] t = {{-28, -17, 9}, {-20, -12, 7}, {-28, 1, 9}, {-27, 22, 8}, {-16, 23, 7}, {7, 24, 7},
                {14, 22, 6}, {28, 5, 8}, {28, -20, 9}, {24, -25, 8}, {-12, -25, 7}, {16, -25, 6}, {28, 13, 7},
                {-9, 19, 5}};
        for (int[] p : t) {
            int y = top(p[0], p[1]) + 1;
            set(p[0], y - 1, p[1], "minecraft:podzol");
            spruce(p[0], y, p[1], p[2], p[0] * 31L + p[1]);
        }
        // sapin decore pres de la terrasse (boules lumineuses)
        int y = G + 1;
        spruce(-4, y, 19, 7, 777);
        for (int i = 0; i < 40; i++) {
            int dx = (int) (h(i, 1, 0) * 7) - 3, dz = (int) (h(i, 2, 0) * 7) - 3, dy = 2 + (int) (h(i, 3, 0) * 7);
            if (B.name(get(-4 + dx, y + dy, 19 + dz)).equals("spruce_leaves") && isAir(-4 + dx + 1, y + dy, 19 + dz))
                set(-4 + dx, y + dy, 19 + dz, i % 3 == 0 ? "minecraft:shroomlight" : (i % 3 == 1 ? "minecraft:ochre_froglight" : "minecraft:pearlescent_froglight"));
        }
        set(-4, y + 10, 19, "minecraft:gold_block");
    }

    /** Cloture de ranch sur le pourtour de la boite ; barrieres invisibles seulement au-dessus. */
    private void fence() {
        for (int x = BX1; x <= BX2; x++)
            for (int z = BZ1; z <= BZ2; z++) {
                if (x != BX1 && x != BX2 && z != BZ1 && z != BZ2) continue;
                int t = top(x, z);
                int k = x == BX1 || x == BX2 ? z : x;
                boolean post = Math.floorMod(k, 6) == 0 || ((x == BX1 || x == BX2) && (z == BZ1 || z == BZ2));
                for (int y = t + 1; y <= t + 3; y++) set(x, y, z, B.AIR);
                if (post) {
                    set(x, t + 1, z, log("minecraft:spruce_log", 'y'));
                    set(x, t + 2, z, log("minecraft:spruce_log", 'y'));
                    set(x, t + 3, z, "minecraft:snow[layers=2]");
                } else {
                    set(x, t + 1, z, "minecraft:spruce_fence");
                    set(x, t + 2, z, "minecraft:spruce_fence");
                }
                for (int y = t + (post ? 4 : 3); y <= MAX_Y; y++) {
                    String b = get(x, y, z);
                    if (B.isAir(b) || B.name(b).endsWith("leaves") || B.name(b).equals("snow")) set(x, y, z, "minecraft:barrier");
                }
            }
        // portique d'entree face a l'allee (la cloture reste fermee)
        for (int x : new int[]{-1, 4}) {
            int t = top(x, BZ1);
            for (int y = t + 1; y <= t + 4; y++) set(x, y, BZ1, log("minecraft:spruce_log", 'y'));
            set(x, t + 5, BZ1, standing(LANTERN));
        }
        for (int x = -1; x <= 4; x++) set(x, top(x, BZ1) + 4, BZ1, log("minecraft:spruce_log", 'x'));
        set(-1, top(-1, BZ1) + 5, BZ1, standing(LANTERN));
        set(4, top(4, BZ1) + 5, BZ1, standing(LANTERN));
        for (int x = 0; x <= 3; x++) set(x, top(x, BZ1) + 3, BZ1, "minecraft:barrier");
    }

    /** Foret de sapins sur les collines autour de la boite. */
    private void forest() {
        for (int x = -AREA + 6; x <= AREA - 6; x += 3)
            for (int z = -AREA + 6; z <= AREA - 6; z += 3) {
                int jx = x + (int) (h(x, 11, z) * 3) - 1, jz = z + (int) (h(x, 12, z) * 3) - 1;
                if (inArea(jx, jz) == false) continue;
                int dx = Math.max(Math.max(BX1 - jx, jx - BX2), 0), dz = Math.max(Math.max(BZ1 - jz, jz - BZ2), 0);
                if (Math.max(dx, dz) < 4) continue;
                if (BZ1 - jz >= 1 && BZ1 - jz <= 8) continue; // route
                double dens = noise.uniform2(jx / 14.0 + 40, jz / 14.0 - 20);
                if (jz > BZ2) dens -= 0.25; // vallee plus degagee
                if (dens < 0.42) continue;
                int t = top(jx, jz);
                if (!isAir(jx, t + 1, jz)) continue;
                spruce(jx, t + 1, jz, 6 + (int) (h(jx, 13, jz) * 7), jx * 131L + jz);
            }
        // lumieres de fenetres de chalets voisins dans la vallee (silhouettes simples)
        neighbour(-24, 44);
        neighbour(18, 46);
    }

    private void neighbour(int cx, int cz) {
        int t = top(cx, cz);
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = t + 1; y <= t + 3; y++) set(x, y, z, x == cx - 3 || x == cx + 3 || z == cz - 2 || z == cz + 2 ? BOARDS.at(x, y, z) : "minecraft:spruce_planks");
                int d = Math.min(z - (cz - 2), (cz + 2) - z);
                set(x, t + 4 + d, z, "minecraft:snow_block");
                for (int y = t + 4; y < t + 4 + d; y++) set(x, y, z, "minecraft:spruce_planks");
            }
        set(cx - 1, t + 2, cz - 2, "minecraft:ochre_froglight");
        set(cx + 1, t + 2, cz - 2, "minecraft:ochre_froglight");
    }

    /** Congeres dans les coins degages de la boite (relief et couverts). */
    private void drifts() {
        for (int x = BX1 + 1; x <= BX2 - 1; x++)
            for (int z = BZ1 + 1; z <= BZ2 - 1; z++) {
                int t = top(x, z);
                if (!B.name(get(x, t, z)).equals("snow_block")) continue;
                double n = noise.uniform2(x / 5.0 + 9, z / 5.0 - 3);
                boolean nearFence = x - BX1 <= 3 || BX2 - x <= 3 || z - BZ1 <= 3 || BZ2 - z <= 3;
                double thr = nearFence ? 0.55 : 0.8;
                if (n < thr) continue;
                boolean clear = true;
                for (int dx = -2; dx <= 2 && clear; dx++)
                    for (int dz = -2; dz <= 2 && clear; dz++)
                        for (int y = t + 1; y <= t + 3; y++) {
                            String b = get(x + dx, y, z + dz);
                            if (!B.isAir(b) && !b.startsWith("minecraft:barrier") && !B.name(b).equals("snow_block")) clear = false;
                        }
                if (!clear) continue;
                set(x, t + 1, z, "minecraft:snow_block");
                if (n > thr + 0.15 && nearFence) set(x, t + 2, z, "minecraft:snow_block");
                else if (n > thr + 0.08) set(x, t + 2, z, "minecraft:snow[layers=3]");
            }
    }

    // ================================================================ finitions

    /** Degage les deux cotes de chaque porte (2 de haut) : meubles poses trop pres. */
    private void clearDoors() {
        for (int x = BX1; x <= BX2; x++)
            for (int z = BZ1; z <= BZ2; z++)
                for (int y = C + 1; y <= U + 1; y++) {
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
                    }
                }
    }

    private static boolean isFurniture(String st) {
        String n = B.name(st);
        return n.equals("barrel") || n.contains("wool") || n.equals("bookshelf") || n.equals("note_block") || n.equals("jukebox")
                || n.equals("crafting_table") || n.equals("smithing_table") || n.equals("iron_block") || n.equals("chest");
    }

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
        return n.endsWith("lantern") || n.endsWith("froglight") || n.equals("glowstone") || n.equals("shroomlight")
                || (n.contains("candle") && st.contains("lit=true")) || ((n.equals("smoker") || n.equals("furnace") || n.equals("blast_furnace")) && st.contains("lit=true"))
                || n.equals("end_rod") || (n.equals("campfire") && st.contains("lit=true"));
    }

    /**
     * Complement discret : sur les positions debout de la boite sans source de lumiere visible a moins de 6 blocs, un
     * bloc de lumiere invisible (niveau 11) au-dessus de la tete (le meurtrier ne doit pas se cacher dans le noir).
     */
    private void fillDarkSpots() {
        java.util.List<int[]> lights = new java.util.ArrayList<>();
        for (int x = BX1 - 6; x <= BX2 + 6; x++)
            for (int z = BZ1 - 6; z <= BZ2 + 6; z++)
                for (int y = C; y <= 84; y++) if (emits(get(x, y, z))) lights.add(new int[]{x, y, z});
        for (int x = BX1 + 1; x <= BX2 - 1; x++)
            for (int z = BZ1 + 1; z <= BZ2 - 1; z++)
                for (int y = C + 1; y <= U + 4; y++) {
                    if (!isAir(x, y, z) || !isAir(x, y + 1, z) || isAir(x, y - 1, z)) continue;
                    String below = get(x, y - 1, z);
                    if (below.startsWith("minecraft:barrier") || B.name(below).endsWith("_fence") || B.name(below).endsWith("_wall")) continue;
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
