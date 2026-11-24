package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Dir;
import fr.eemicraft.murder.build.Mat;
import fr.eemicraft.murder.build.Palette;
import fr.eemicraft.murder.build.Sink;

/**
 * Map « Paquebot » (id paquebot, monde mm_paquebot, 1.9.0) : grand paquebot transatlantique du debut du XXe siecle
 * (inspiration Belle Epoque / art deco, aucun navire reel reproduit), de nuit, en pleine mer. Dessinee par code dans
 * un {@link Sink} (aucun acces Bukkit).
 *
 * <p>Le navire est couche le long de x, etrave a l'est (x 76), poupe a l'ouest (x -72), largeur 27 (z -13..13). Mer
 * decorative tout autour (eau y 52..54, tenue par un anneau de barrieres au bord de la zone posee). Les pieds les plus
 * bas sont en y 58 : la boite commence en y 57, donc tomber par-dessus bord tue avant meme de toucher l'eau.
 * <ul>
 * <li>pont des machines (plancher y 57, pieds 58) : chaufferie (6 chaudieres), salle des machines (deux machines
 * alternatives qui montent jusqu'au pont superieur, coursives), trois cales (caisses, malles, voiture) ;</li>
 * <li>pont principal (plancher 63, pieds 64) : poste d'equipage, piscine interieure, escalier arriere, coursives
 * de la salle des machines, cuisines, salle a manger de 1re classe, grand escalier, bureau du commissaire, coursive
 * des cabines (cabines ouvertes communicantes, cabines fermees), escalier avant ;</li>
 * <li>pont promenade (plancher 69, pieds 70) : rouf central (veranda, escalier arriere, fumoir, grand salon / salle
 * de bal, grand escalier, bibliotheque) entoure du pont promenade couvert, pont avant (cale, treuils, mat, guindeau)
 * et pont arriere (descente, mat, gouvernail de secours, cabestans) ;</li>
 * <li>pont des embarcations (plancher 75, pieds 76) : passerelle de commandement (timonerie et ailerons), verriere du
 * grand escalier, trois cheminees, salle de gymnastique, verriere du fumoir, rouf de l'escalier arriere, pont soleil
 * a chaises longues, 16 canots de sauvetage sur bossoirs.</li>
 * </ul>
 * Circulations verticales : grand escalier (principal -> promenade -> embarcations, sous verriere), escalier arriere
 * (idem), escalier avant (machines -> principal -> pont avant), escalier des chauffeurs (machines -> principal),
 * deux escaliers de la salle des machines, descente de la poupe (principal -> pont arriere), quatre escaliers
 * exterieurs (ponts avant et arriere -> pont des embarcations).
 */
final class Paquebot extends MapKit {
    static final int VERSION = 3; // 1.13.0 : tete libre dans les escaliers
    static final String ID = "paquebot";
    static final String WORLD = "mm_paquebot";
    static final String NAME = "Paquebot";

    static final int STERN = -72, BOW = 76;
    static final int BX1 = -80, BX2 = 84, BZ1 = -22, BZ2 = 22, MIN_Y = 57, MAX_Y = 90;
    static final int AREA = 92, Y_LO = 49, Y_HI = 108;
    /** Dernier rang d'eau de la mer. */
    static final int SEA = 54;
    /** Planchers : machines, principal, promenade, embarcations ; toits des roufs. */
    static final int F0 = 57, F1 = 63, F2 = 69, F3 = 75, RF = 80;
    /** Rouf du pont promenade (murs exterieurs en z = +-HZ) et pont des embarcations. */
    static final int HX1 = -48, HX2 = 40, HZ = 9, DX1 = -50, DX2 = 42;
    /** Etendue des ponts interieurs. */
    static final int D0X1 = -60, D0X2 = 58, D1X1 = -68, D1X2 = 58;
    static final int[] WORLD_SPAWN = {-60, 70, -8};

    static final int[][] SPAWNS = {
            {-60, 70, -8},   // pont arriere
            {-46, 70, 0},    // veranda
            {-24, 70, 4},    // fumoir
            {4, 70, -4},     // grand salon
            {37, 70, 0},     // bibliotheque
            {60, 70, 4},     // pont avant
            {0, 70, 11},     // promenade tribord
            {-30, 70, -11},  // promenade babord
            {-47, 76, 5},    // pont soleil
            {38, 76, 0},     // timonerie
            {4, 76, 0},      // gymnase
            {-62, 64, -6},   // poste d'equipage
            {-50, 64, 6},    // piscine
            {8, 64, 0},      // salle a manger
            {-8, 64, 4},     // cuisines
            {44, 64, -6},    // cabines
            {-45, 58, 0},    // chaufferie
            {8, 58, 6},      // cale
    };

    static final int[][] GOLD = {
            // pont des machines
            {-59, 58, 7}, {-40, 58, -10}, {-28, 58, -9}, {-20, 58, 9}, {-8, 58, -8}, {14, 58, 9}, {28, 58, -6}, {33, 58, 8}, {48, 58, -4},
            // pont principal
            {-66, 64, 6}, {-45, 64, -9}, {-39, 64, 6}, {-30, 64, 10}, {-20, 64, -10}, {-11, 64, -8}, {6, 64, 7}, {17, 64, -3},
            {27, 64, 0}, {34, 64, 7}, {39, 64, -6}, {47, 64, 7}, {54, 64, -5},
            // pont promenade
            {-45, 70, -5}, {-38, 70, -3}, {-18, 70, -5}, {-10, 70, 5}, {15, 70, -6}, {31, 70, 6}, {38, 70, 5}, {-40, 70, 11},
            {26, 70, -11}, {47, 70, -3}, {66, 70, 1}, {-67, 70, 5}, {-55, 70, -2},
            // pont des embarcations
            {-48, 76, -8}, {-30, 76, 8}, {-12, 76, -8}, {8, 76, 8}, {29, 76, -3}, {40, 76, 10}, {-38, 76, 3},
    };

    private Paquebot(Sink s) {
        super(s, 2511);
    }

    static void build(Sink s) {
        new Paquebot(s).run();
    }

    // ================================================================ palettes

    private static final Palette HULL = Palette.of(61, "minecraft:black_concrete", 80, "minecraft:polished_blackstone", 20).period(9);
    private static final Palette WHITE = Palette.of(62, "minecraft:white_concrete", 62, "minecraft:smooth_quartz", 26,
            "minecraft:calcite", 12).period(7);
    private static final Palette DECK = Palette.of(63, "minecraft:spruce_planks", 66, "minecraft:stripped_spruce_wood[axis=x]", 22,
            "minecraft:oak_planks", 12).period(6);
    private static final Palette STEEL = Palette.of(64, "minecraft:polished_andesite", 58, "minecraft:smooth_stone", 28,
            "minecraft:polished_tuff", 14).period(6);
    private static final Palette STEEL_FLOOR = Palette.of(65, "minecraft:polished_deepslate", 60, "minecraft:deepslate_tiles", 28,
            "minecraft:cracked_deepslate_tiles", 12).period(5);
    private static final Palette CABIN = Palette.of(66, "minecraft:birch_planks", 72, "minecraft:stripped_birch_wood[axis=y]", 28).period(6);
    private static final Palette DARK = Palette.of(67, "minecraft:dark_oak_planks", 70, "minecraft:stripped_dark_oak_wood[axis=y]", 20,
            "minecraft:spruce_planks", 10).period(6);
    private static final Palette MARBLE = Palette.of(68, "minecraft:polished_diorite", 60, "minecraft:calcite", 25,
            "minecraft:smooth_quartz", 15).period(7);
    private static final Palette HOLD_FLOOR = Palette.of(69, "minecraft:spruce_planks", 55, "minecraft:dark_oak_planks", 25,
            "minecraft:stripped_spruce_wood[axis=x]", 20).period(5);
    private static final Palette FUNNEL = Palette.of(70, "minecraft:red_concrete", 75, "minecraft:red_terracotta", 25).period(6);
    private static final Palette ICE = Palette.of(71, "minecraft:snow_block", 40, "minecraft:packed_ice", 40, "minecraft:blue_ice", 20).period(5);

    private static final String FENCE = "minecraft:dark_oak_fence";
    private static final String RAIL = "minecraft:birch_fence";
    private static final String HIDDEN = "minecraft:smooth_stone";

    // ================================================================ deroule

    private void run() {
        sea();
        hull();
        decks();
        houseShell();
        partitionsD0();
        partitionsD1();
        partitionsD2();
        stairsAll();
        pilastersD1();
        boilerRoom();
        engineRoom();
        holds();
        crewMess();
        pool();
        aftHall();
        galley();
        dining();
        grandHall();
        lobby();
        cabins();
        foreHall();
        veranda();
        smokingRoom();
        ballroom();
        library();
        promenade();
        foredeck();
        afterdeck();
        boatDeck();
        bridge();
        domeHouse();
        gym();
        skylight();
        aftHouse();
        funnels();
        lifeboats();
        masts();
        iceberg();
        clearHeadroom();
        finish(BX1 - 2, F0, -16, BX2, Y_HI - 1, 16);
    }

    /** Cases a garder libres au-dessus des marches et devant la 1re marche (x, y, z, hauteur). */
    private final java.util.List<int[]> headroom = new java.util.ArrayList<>();

    /**
     * Esteban, 17/11 : on se cognait la tete dans les escaliers (lanterne posee plus tard au-dessus d'une marche,
     * linteau de porte juste devant la 1re marche). Apres tout le decor, on revide ces cases.
     */
    private void clearHeadroom() {
        for (int[] h : headroom)
            for (int y = h[1]; y < h[1] + h[3]; y++)
                if (!isAir(x(h), y, h[2])) set(x(h), y, h[2], B.AIR);
    }

    private static int x(int[] h) { return h[0]; }

    // ================================================================ geometrie de la coque

    /** Demi-largeur de la coque au niveau des ponts (en blocs, negatif hors du navire). */
    static double hb(double x) {
        if (x > BOW || x < STERN) return -1;
        if (x >= 38) {
            double t = (x - 38) / (double) (BOW - 38);
            return 13 * (1 - Math.pow(t, 1.7));
        }
        if (x >= -50) return 13;
        double t = (-50 - x) / (double) (-50 - STERN);
        return 13 * Math.sqrt(1 - Math.pow(t * 0.82, 2));
    }

    /** Demi-largeur entiere au rang y (la coque se resserre sous la flottaison). */
    static int hw(int x, int y) {
        double h = hb(x);
        if (h < 0) return -1;
        if (y < 56) {
            double f = (56 - y) / 7.0;
            h *= Math.sqrt(Math.max(0, 1 - f * f * 0.7));
        }
        return (int) Math.round(h);
    }

    static boolean in(int x, int z, int y) {
        int w = hw(x, y);
        return w >= 0 && Math.abs(z) <= w;
    }

    /** Pieds hors de la coque (corniche, preceintes, dehors) : tombe a la mer, EemiMurder elimine le joueur. */
    static boolean overboard(int x, int z) {
        int w = hw(x, F2);
        return w < 0 || Math.abs(z) > w;
    }

    static boolean shell(int x, int z, int y) {
        return in(x, z, y) && (!in(x + 1, z, y) || !in(x - 1, z, y) || !in(x, z + 1, y) || !in(x, z - 1, y));
    }

    static boolean lining(int x, int z, int y) {
        return in(x, z, y) && !shell(x, z, y)
                && (shell(x + 1, z, y) || shell(x - 1, z, y) || shell(x, z + 1, y) || shell(x, z - 1, y));
    }

    /** Case interieure (ni bordé ni doublage). */
    static boolean inner(int x, int z, int y) {
        return in(x, z, y) && !shell(x, z, y) && !lining(x, z, y);
    }

    /** Coque « droite » ici : le bordé est parallele a x (portes, hublots, preceintes). */
    private static boolean straightSide(int x, int z, int y) {
        int w = hw(x, y);
        return Math.abs(z) == w && hw(x - 1, y) == w && hw(x + 1, y) == w;
    }

    // ================================================================ mer

    private void sea() {
        for (int x = -AREA; x <= AREA; x++)
            for (int z = -AREA; z <= AREA; z++) {
                boolean edge = Math.abs(x) == AREA || Math.abs(z) == AREA;
                set(x, 51, z, edge ? "minecraft:barrier" : "minecraft:dark_prismarine");
                for (int y = 52; y <= SEA; y++) set(x, y, z, edge ? "minecraft:barrier" : B.WATER);
            }
    }

    // ================================================================ coque

    private void hull() {
        for (int x = STERN; x <= BOW; x++)
            for (int z = -14; z <= 14; z++)
                for (int y = 50; y <= F2 + 1; y++) {
                    if (!in(x, z, y)) continue;
                    if (shell(x, z, y)) {
                        set(x, y, z, shellMat(x, y, z));
                    } else if (y <= F0) {
                        set(x, y, z, HIDDEN);
                    } else if (y <= F2) {
                        boolean lin = lining(x, z, y);
                        if (y == F2) set(x, y, z, deck(x, y, z));
                        else if (y == F1) set(x, y, z, inD1(x) ? HOLD_FLOOR.at(x, y, z) : HIDDEN);
                        else if (y < F1) set(x, y, z, inD0(x) ? (lin ? STEEL.at(x, y, z) : B.AIR) : HIDDEN);
                        else set(x, y, z, inD1(x) ? (lin ? liningD1(x, y, z) : B.AIR) : HIDDEN);
                    }
                }
        // pavois sur le bordé : 2 rangs pleins + main courante (rien de ce qu'on peut escalader ne permet de passer
        // par-dessus), un rang de plus a la proue
        for (int x = STERN; x <= BOW; x++)
            for (int z = -14; z <= 14; z++) {
                if (!shell(x, z, F2)) continue;
                boolean prow = x >= 66;
                int top = F2 + (prow ? 3 : 2);
                for (int y = F2 + 1; y < top; y++) set(x, y, z, WHITE.at(x, y, z));
                set(x, top, z, stairs("minecraft:smooth_quartz_stairs", inward(x, z, top), true)); // lisse du pavois
                set(x, top + 1, z, x >= HX1 && x <= HX2 ? RAIL : FENCE);
            }
        // preceintes (3e plan) et hublots sur les flancs droits
        for (int x = STERN; x <= BOW; x++)
            for (int sz : new int[]{-1, 1}) {
                int w = hw(x, 62);
                if (w < 2) continue;
                int z = sz * w;
                Dir out = sz < 0 ? Dir.NORTH : Dir.SOUTH;
                if (!in(x, z + sz, 62)) {
                    set(x, 62, z + sz, stairs("minecraft:polished_blackstone_stairs", out.opposite(), true));
                    set(x, 68, z + sz, stairs("minecraft:quartz_stairs", out.opposite(), true));
                }
                if (!straightSide(x, z, 60)) continue;
                if (inD0(x) && Math.floorMod(x, 4) == 1 && x > -56 && x < 56) porthole(x, 60, z, sz);
                if (inD1(x) && Math.floorMod(x, 3) == 0 && x > -66 && x < 57) porthole(x, 66, z, sz);
                // joints de tole (couple en creux) au droit des cloisons : le flanc noir n'a plus de pan > 12 ;
                // pilastres blancs en saillie sur le pavois et sur le pavois du pont des embarcations
                if (SEAMS.contains(x)) {
                    for (int y = 69; y <= F2 + 2; y++) set(x, y, z + sz, "minecraft:quartz_pillar[axis=y]");
                    set(x, F2 + 3, z + sz, slab("minecraft:smooth_quartz_slab", "bottom"));
                    if (x >= DX1 && x <= DX2 && hw(x, F3) == w) {
                        set(x, F3 + 1, z + sz, "minecraft:quartz_pillar[axis=y]");
                        set(x, F3 + 2, z + sz, slab("minecraft:smooth_quartz_slab", "bottom"));
                    }
                }
                if (SEAMS.contains(x))
                    for (int y = 56; y <= 67; y++) {
                        if (y == 62 || !shell(x, z, y) || isAir(x, y, z)) continue;
                        String lin = get(x, y, z - sz);
                        if (B.name(lin).equals("glass") || !lining(x, z - sz, y)) continue;
                        set(x, y, z, B.AIR);
                        set(x, y, z - sz, x == 10 ? "minecraft:stripped_dark_oak_log[axis=y]" : "minecraft:polished_blackstone_bricks");
                    }
            }
        // ancres a l'avant
        for (int sz : new int[]{-1, 1}) {
            int x = 69, w = hw(x, 64), z = sz * (w + 1);
            for (int y = 62; y <= 66; y++) set(x, y, z, chain('y'));
            set(x - 1, 66, z, "minecraft:iron_bars");
            set(x + 1, 66, z, "minecraft:iron_bars");
            set(x, 61, z, "minecraft:polished_blackstone");
            set(x, 67, sz * w, "minecraft:polished_blackstone");
        }
    }

    private static final Palette DECK_SLAB = Palette.of(72, "minecraft:spruce_slab[type=top,waterlogged=false]", 78,
            "minecraft:oak_slab[type=top,waterlogged=false]", 22).period(6);

    /**
     * Bordage des ponts decouverts : lattes en dalles hautes (meme hauteur qu'un bloc plein), joints de calfatage
     * sombres tous les 4 blocs dans le sens de la longueur.
     */
    private static String deck(int x, int y, int z) {
        if (Math.floorMod(z, 4) == 0) return "minecraft:dark_oak_slab[type=top,waterlogged=false]";
        return DECK_SLAB.at(x, y, z);
    }

    /** Direction vers l'interieur du navire pour une case du bordé (rang y). */
    private static Dir inward(int x, int z, int y) {
        if (!in(x, z + 1, y)) return Dir.NORTH;
        if (!in(x, z - 1, y)) return Dir.SOUTH;
        if (!in(x + 1, z, y)) return Dir.WEST;
        return Dir.EAST;
    }

    private String shellMat(int x, int y, int z) {
        if (y <= SEA) return "minecraft:red_terracotta";
        if (y == SEA + 1) return "minecraft:white_concrete";
        if (y == 65) return "minecraft:yellow_terracotta";
        if (y >= 68) return WHITE.at(x, y, z);
        return HULL.at(x, y, z);
    }

    /** Couples ou la tole est en creux (cloisons transversales + deux couples intermediaires). */
    private static final java.util.Set<Integer> SEAMS = java.util.Set.of(-56, -43, -35, -25, -14, -1, 10, 21, 33, 41, 49);

    /** Hublot : bordé ouvert (le verre est en retrait d'un plan), sourcil en escalier retourne au-dessus. */
    private void porthole(int x, int y, int z, int sz) {
        if (!lining(x, z - sz, y)) return;
        set(x, y, z, B.AIR);
        set(x, y, z - sz, "minecraft:glass");
        if (!in(x, z + sz, y + 1)) set(x, y + 1, z + sz, stairs("minecraft:polished_blackstone_stairs", sz < 0 ? Dir.SOUTH : Dir.NORTH, true));
    }

    private static boolean inD0(int x) {
        return x >= D0X1 && x <= D0X2;
    }

    private static boolean inD1(int x) {
        return x >= D1X1 && x <= D1X2;
    }

    private String liningD1(int x, int y, int z) {
        if (x >= -55 && x <= -44) return "minecraft:light_blue_terracotta";
        if (x >= -34 && x <= -15) return STEEL.at(x, y, z);
        if (x >= -13 && x <= -2) return "minecraft:white_concrete";
        if (x >= 0 && x <= 32) return MARBLE.at(x, y, z);
        return CABIN.at(x, y, z);
    }

    // ================================================================ planchers des ponts

    private void decks() {
        // pont promenade : rouf (sols par piece ensuite) ; pont des embarcations au-dessus du rouf et des promenades
        for (int x = DX1; x <= DX2; x++)
            for (int z = -14; z <= 14; z++) {
                if (!in(x, z, F3)) continue;
                set(x, F3, z, shell(x, z, F2) ? "minecraft:white_concrete" : deck(x, F3, z));
            }
        // corniche blanche en saillie sous le bord du pont des embarcations (3e plan vu de la mer)
        for (int x = DX1; x <= DX2; x++)
            for (int sz : new int[]{-1, 1}) {
                int w = hw(x, F3);
                if (w < 1 || in(x, sz * (w + 1), F3)) continue;
                set(x, F3, sz * (w + 1), stairs("minecraft:quartz_stairs", sz < 0 ? Dir.SOUTH : Dir.NORTH, true));
            }
        // pavois du pont des embarcations (un rang plein + main courante : depuis une chaise longue ou un banc on ne
        // monte pas sur la main courante), bords exterieurs, extremites avant et arriere
        for (int x = DX1; x <= DX2; x++)
            for (int z = -14; z <= 14; z++) {
                if (!in(x, z, F3)) continue;
                boolean side = shell(x, z, F2);
                boolean landing = Math.abs(z) == 7 || Math.abs(z) == 8; // arrivees des escaliers exterieurs
                boolean end = (x == DX1 && !landing) || (x == DX2 && Math.abs(z) >= 9);
                if (side || end) {
                    set(x, F3 + 1, z, WHITE.at(x, F3 + 1, z));
                    set(x, F3 + 2, z, RAIL);
                }
            }
        // poteaux sous les bords en encorbellement du pont des embarcations (au-dessus des ponts avant et arriere)
        for (int x : new int[]{DX1, DX2})
            for (int z = -12; z <= 12; z += 4)
                if (in(x, z, F2)) for (int y = F2 + 1; y < F3; y++) set(x, y, z, "minecraft:quartz_pillar[axis=y]");
    }

    // ================================================================ rouf du pont promenade

    private void houseShell() {
        // sol des pieces du rouf (remplace par piece ensuite), plafond = pont des embarcations
        for (int x = HX1; x <= HX2; x++)
            for (int z = -HZ; z <= HZ; z++) set(x, F2, z, DECK.at(x, F2, z));
        for (int sz : new int[]{-1, 1}) houseSide(sz);
        houseEnd(HX1, Dir.WEST);
        houseEnd(HX2, Dir.EAST);
    }

    /** Mur lateral du rouf en 3 plans : poteaux, plinthe et corniche (P), mur et fenetres (P-1), interieur. */
    private void houseSide(int sz) {
        int zp = sz * HZ, zi = sz * (HZ - 1);
        Dir out = sz < 0 ? Dir.NORTH : Dir.SOUTH;
        for (int x = HX1; x <= HX2; x++) {
            boolean post = Math.floorMod(x, 4) == 0 || x == HX1 || x == HX2;
            set(x, F2 + 1, zp, x == HX1 || x == HX2 ? "minecraft:quartz_bricks" : stairs("minecraft:smooth_quartz_stairs", out.opposite(), false));
            for (int y = F2 + 2; y <= F3 - 2; y++) set(x, y, zp, post ? "minecraft:quartz_pillar[axis=y]" : B.AIR);
            set(x, F3 - 1, zp, post ? "minecraft:chiseled_quartz_block" : stairs("minecraft:quartz_stairs", out.opposite(), true));
            for (int y = F2 + 1; y <= F3 - 1; y++) set(x, y, zi, WHITE.at(x, y, zi));
            if (!post && windowAt(x)) {
                String pane = x >= -34 && x <= -15 ? (Math.floorMod(x, 8) < 4 ? "minecraft:orange_stained_glass_pane" : "minecraft:yellow_stained_glass_pane")
                        : "minecraft:glass_pane";
                for (int y = F2 + 2; y <= F3 - 2; y++) set(x, y, zi, pane);
                set(x, F2 + 1, zp, stairs("minecraft:smooth_quartz_stairs", out.opposite(), true));
            }
        }
    }

    /** Fenetres du rouf : pas devant les escaliers ni contre les cloisons. */
    private static boolean windowAt(int x) {
        if (x >= -43 && x <= -35) return false;
        if (x >= 21 && x <= 34) return false;
        return x != -14;
    }

    private void houseEnd(int x, Dir out) {
        Dir in = out.opposite();
        for (int z = -HZ; z <= HZ; z++) {
            boolean post = Math.floorMod(z, 3) == 0 || Math.abs(z) == HZ;
            for (int y = F2 + 1; y <= F3 - 1; y++) {
                set(x, y, z, post ? "minecraft:quartz_pillar[axis=y]" : WHITE.at(x, y, z));
                if (!post && y >= F2 + 2 && y <= F3 - 2) set(x, y, z, "minecraft:glass_pane");
            }
            // corniche en saillie (dehors, au-dessus des tetes) : 3e plan de la facade d'extremite
            set(x + out.dx, F3 - 1, z, stairs("minecraft:quartz_stairs", in, true));
        }
    }

    // ================================================================ cloisons

    /** Cloison transversale en x (cases interieures seulement). */
    private void wallX(int x, int y1, int y2, Mat m) {
        for (int z = -14; z <= 14; z++)
            for (int y = y1; y <= y2; y++)
                if (inner(x, z, y)) set(x, y, z, m);
    }

    /** Cloison en x sur z1..z2, cases interieures seulement (ni bordé ni doublage). */
    private void wallXz(int x, int z1, int z2, int y1, int y2, Mat m) {
        for (int z = z1; z <= z2; z++)
            for (int y = y1; y <= y2; y++)
                if (inner(x, z, y)) set(x, y, z, m);
    }

    /** Porte simple (2 de haut) dans une cloison en x, tournee vers facing. */
    private void doorX(int x, int z, int yFeet, Dir facing, String wood, boolean right) {
        set(x, yFeet, z, B.door("minecraft:" + wood + "_door", facing, false, right));
        set(x, yFeet + 1, z, B.door("minecraft:" + wood + "_door", facing, true, right));
    }

    /** Porte double en x : z et z+1. */
    private void door2X(int x, int z, int yFeet, Dir facing, String wood) {
        doorX(x, z, yFeet, facing, wood, false);
        doorX(x, z + 1, yFeet, facing, wood, true);
        set(x, yFeet + 2, z, stairs("minecraft:dark_oak_stairs", facing, true));
        set(x, yFeet + 2, z + 1, stairs("minecraft:dark_oak_stairs", facing, true));
    }

    /** Porte double dans une cloison en z : x et x+1. */
    private void door2Z(int z, int x, int yFeet, Dir facing, String wood) {
        doorX(x, z, yFeet, facing, wood, false);
        doorX(x + 1, z, yFeet, facing, wood, true);
    }

    /** Passage ouvert (2 de haut) avec linteau en dalle. */
    private void openingX(int x, int z1, int z2, int yFeet) {
        for (int z = z1; z <= z2; z++) {
            set(x, yFeet, z, B.AIR);
            set(x, yFeet + 1, z, B.AIR);
            set(x, yFeet + 2, z, slab("minecraft:smooth_quartz_slab", "top"));
        }
    }

    private void partitionsD0() {
        int y1 = F0 + 1, y2 = F1 - 1;
        wallX(-35, y1, y2, STEEL);
        wallX(-14, y1, y2, STEEL);
        wallX(20, y1, y2, STEEL);
        wallX(40, y1, y2, STEEL);
        for (int x : new int[]{-35, -14}) {
            for (int z : new int[]{-10, -9, 9, 10}) { set(x, y1, z, B.AIR); set(x, y1 + 1, z, B.AIR); }
            for (int z : new int[]{-10, 9}) for (int k = 0; k < 2; k++) set(x, y1 + 2, z + k, "minecraft:iron_block");
        }
        for (int x : new int[]{20, 40}) {
            for (int z = -1; z <= 1; z++) { set(x, y1, z, B.AIR); set(x, y1 + 1, z, B.AIR); set(x, y1 + 2, z, "minecraft:iron_block"); }
            for (int z : new int[]{-7, -6, 6, 7}) { set(x, y1, z, B.AIR); set(x, y1 + 1, z, B.AIR); }
        }
        // raidisseurs verticaux sur les deux faces des cloisons etanches
        for (int x : new int[]{-35, -14, 20, 40})
            for (int dx : new int[]{-1, 1})
                for (int z : new int[]{-4, 4})
                    for (int y = y1; y <= y2; y++) if (inner(x + dx, z, y) && isAir(x + dx, y, z)) set(x + dx, y, z, "minecraft:polished_deepslate_wall");
        // membrures : montants contre le bordé, gousset sous le plafond
        for (int x = D0X1; x <= D0X2; x++) {
            if (Math.floorMod(x, 4) != 2) continue;
            for (int sz : new int[]{-1, 1}) {
                int w = hw(x, y2);
                int z = sz * (w - 2);
                if (!inner(x, z, y2) || !isAir(x, y2, z)) continue;
                set(x, y2, z, stairs("minecraft:polished_andesite_stairs", sz < 0 ? Dir.SOUTH : Dir.NORTH, true));
                for (int y = y1; y < y2; y++) if (isAir(x, y, z)) set(x, y, z, "minecraft:polished_deepslate_wall");
            }
        }
    }

    private void partitionsD1() {
        int y1 = F1 + 1, y2 = F2 - 1;
        Mat wood = DARK;
        wallX(-56, y1, y2, CABIN);
        wallX(-43, y1, y2, Mat.of("minecraft:white_terracotta"));
        wallX(-35, y1, y2, STEEL);
        wallX(-14, y1, y2, STEEL);
        wallX(-1, y1, y2, Mat.of("minecraft:white_concrete"));
        wallX(21, y1, y2, MARBLE);
        wallX(33, y1, y2, wood);
        wallX(37, y1, y2, CABIN);
        // portes
        door2X(-56, -7, y1, Dir.EAST, "spruce");
        door2X(-56, 6, y1, Dir.EAST, "spruce");
        door2X(-43, -7, y1, Dir.WEST, "birch");
        door2X(-43, 6, y1, Dir.WEST, "birch");
        for (int x : new int[]{-35, -14})
            for (int z : new int[]{-10, 9}) {
                for (int k = 0; k < 2; k++) { set(x, y1, z + k, B.AIR); set(x, y1 + 1, z + k, B.AIR); set(x, y1 + 2, z + k, "minecraft:iron_block"); }
            }
        door2X(-1, -6, y1, Dir.EAST, "birch");
        door2X(-1, 5, y1, Dir.EAST, "birch");
        openingX(21, -1, 1, y1);
        door2X(21, -9, y1, Dir.EAST, "dark_oak");
        door2X(21, 8, y1, Dir.EAST, "dark_oak");
        openingX(33, -1, 1, y1);
        door2X(33, 7, y1, Dir.EAST, "dark_oak");
        door2X(33, -8, y1, Dir.EAST, "dark_oak");
        openingX(37, -1, 1, y1);
    }

    /** Pilastres sur les deux faces des cloisons du pont principal (apres les escaliers : jamais sur une tremie). */
    private void pilastersD1() {
        int y1 = F1 + 1, y2 = F2 - 1;
        for (int x : new int[]{-56, -43, -35, -14, -1, 21, 33, 37})
            for (int dx : new int[]{-1, 1})
                for (int z : new int[]{-4, 4}) {
                    int px = x + dx;
                    if (!inner(px, z, y1) || isAir(px, F1, z) || !isAir(px, y1, z)) continue;
                    // 1.9.1 : jamais devant la premiere marche d'une volee
                    if (B.name(get(px + 1, y1, z)).endsWith("_stairs") || B.name(get(px - 1, y1, z)).endsWith("_stairs")) continue;
                    String m = x == 21 || x == 33 ? "minecraft:quartz_pillar[axis=y]" : (x == -35 || x == -14 ? "minecraft:polished_deepslate_wall" : "minecraft:stripped_dark_oak_log[axis=y]");
                    for (int y = y1; y <= y2; y++) if (isAir(px, y, z)) set(px, y, z, m);
                }
    }

    private void partitionsD2() {
        int y1 = F2 + 1, y2 = F3 - 1;
        Mat w = WHITE;
        for (int x : new int[]{-43, -35, -14, 21, 34})
            for (int z = -(HZ - 2); z <= HZ - 2; z++)
                for (int y = y1; y <= y2; y++) set(x, y, z, x == -35 || x == -14 ? DARK.at(x, y, z) : w.at(x, y, z));
        // portes entre les pieces
        door2X(-43, -1, y1, Dir.WEST, "birch");
        door2X(-35, -1, y1, Dir.EAST, "dark_oak");
        door2X(-14, -5, y1, Dir.EAST, "dark_oak");
        door2X(-14, 4, y1, Dir.EAST, "dark_oak");
        door2X(21, -5, y1, Dir.EAST, "birch");
        door2X(21, 4, y1, Dir.EAST, "birch");
        door2X(34, 5, y1, Dir.EAST, "dark_oak");
        door2X(34, -6, y1, Dir.EAST, "dark_oak");
        // portes vers les promenades (les deux flancs)
        for (int sz : new int[]{-1, 1}) {
            for (int x : new int[]{-38, -26, -8, 9, 23, 37}) sideDoor(x, sz);
        }
        // facades avant et arriere : portes vers les ponts decouverts
        for (int z : new int[]{-4, 3}) {
            frontDoor(HX1, z, Dir.WEST);
            frontDoor(HX2, z, Dir.EAST);
        }
    }

    /** Porte double du rouf vers la promenade (cases x et x+1). */
    private void sideDoor(int x, int sz) {
        int zp = sz * HZ, zi = sz * (HZ - 1);
        Dir out = sz < 0 ? Dir.NORTH : Dir.SOUTH;
        for (int k = 0; k < 2; k++) {
            int xx = x + k;
            set(xx, F2 + 1, zp, B.AIR);
            set(xx, F2 + 2, zp, B.AIR);
            set(xx, F2 + 3, zp, slab("minecraft:smooth_quartz_slab", "top"));
            set(xx, F2 + 1, zi, B.door("minecraft:birch_door", out, false, k == 1));
            set(xx, F2 + 2, zi, B.door("minecraft:birch_door", out, true, k == 1));
            set(xx, F2 + 3, zi, WHITE.at(xx, F2 + 3, zi));
            set(xx, F2, zp, deck(xx, F2, zp));
        }
    }

    private void frontDoor(int x, int z, Dir out) {
        Dir in = out.opposite();
        for (int k = 0; k < 2; k++) {
            set(x, F2 + 1, z + k, B.door("minecraft:birch_door", out, false, k == 1));
            set(x, F2 + 2, z + k, B.door("minecraft:birch_door", out, true, k == 1));
            set(x, F2 + 3, z + k, "minecraft:white_stained_glass");
            set(x + in.dx, F2 + 1, z + k, B.AIR);
            set(x + in.dx, F2 + 2, z + k, B.AIR);
        }
    }

    // ================================================================ escaliers

    /**
     * Volee de n marches : marche k en (x0,z0)+dir*k (+ cote droit * j, j < w), au rang yb+1+k ; plein dessous,
     * 3 cases d'air au-dessus (un joueur qui descend passe la tete a hauteur de la marche d'au-dessus + 1,8) : la
     * tremie du plancher superieur couvre donc les marches 2 a n-2.
     */
    private void flight(int x0, int z0, Dir dir, int w, int yb, int n, String stairId, Mat under) {
        Dir side = dir.cw();
        for (int k = 0; k < n; k++)
            for (int j = 0; j < w; j++) {
                int x = x0 + dir.dx * k + side.dx * j, z = z0 + dir.dz * k + side.dz * j;
                int y = yb + 1 + k;
                for (int yy = yb + 1; yy < y; yy++) set(x, yy, z, under);
                set(x, y, z, stairs(stairId, dir, false));
                for (int yy = y + 1; yy <= y + 3; yy++) set(x, yy, z, B.AIR);
                headroom.add(new int[]{x, y + 1, z, 3});
            }
        // case d'approche devant la 1re marche : la tete y monte d'un cran en posant le pied sur la marche
        for (int j = 0; j < w; j++)
            headroom.add(new int[]{x0 - dir.dx + side.dx * j, yb + 3, z0 - dir.dz + side.dz * j, 1});
        // palier d'arrivee degage
        int tx = x0 + dir.dx * n, tz = z0 + dir.dz * n;
        for (int j = 0; j < w; j++) {
            set(tx + side.dx * j, yb + n + 1, tz + side.dz * j, B.AIR);
            set(tx + side.dx * j, yb + n + 2, tz + side.dz * j, B.AIR);
        }
    }

    /** Garde-corps (au rang y) sur les cases donnees, seulement dans l'air. */
    private void railLine(int x1, int z1, int x2, int z2, int y, String fence) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                if (isAir(x, y, z) && !isAir(x, y - 1, z)) set(x, y, z, fence);
    }

    private void stairsAll() {
        Mat oak = Mat.of("minecraft:dark_oak_planks");
        // grand escalier : principal -> promenade (deux volees le long des flancs), promenade -> embarcations (centrale)
        flight(31, 7, Dir.WEST, 3, F1, 6, "minecraft:dark_oak_stairs", oak);
        flight(31, -5, Dir.WEST, 3, F1, 6, "minecraft:dark_oak_stairs", oak);
        flight(23, -2, Dir.EAST, 5, F2, 6, "minecraft:dark_oak_stairs", oak);
        // garde-corps autour des tremies (marches 2 a 4) : sur les deux cotes longs et du cote des marches basses
        for (int sz : new int[]{-1, 1}) {
            railLine(27, sz * 4, 29, sz * 4, F2 + 1, "minecraft:dark_oak_fence");
            railLine(30, sz * 5, 30, sz * 7, F2 + 1, "minecraft:dark_oak_fence");
            railLine(25, sz * 3, 27, sz * 3, F3 + 1, "minecraft:dark_oak_fence");
        }
        railLine(24, -2, 24, 2, F3 + 1, "minecraft:dark_oak_fence");
        // escalier arriere. 1.9.1 : entre les cloisons x -43 et -35 il n'y a que 7 cases, la premiere marche
        // etait collee a une cloison (on ne l'abordait que de cote). Chaque volee part maintenant d'une porte de la
        // cloison x -43 (on entre de face sur la premiere marche) : principal -> promenade dans l'axe des portes
        // z -7..-6, promenade -> embarcations dans l'axe des portes z -1..0.
        Mat birch = Mat.of("minecraft:birch_planks");
        flight(-42, -7, Dir.EAST, 2, F1, 6, "minecraft:birch_stairs", birch); // rangs z -7, -6
        flight(-42, -1, Dir.EAST, 2, F2, 6, "minecraft:birch_stairs", birch); // rangs z -1, 0
        railLine(-40, -8, -37, -8, F2 + 1, "minecraft:birch_fence");
        railLine(-40, -5, -37, -5, F2 + 1, "minecraft:birch_fence");
        railLine(-41, -8, -41, -5, F2 + 1, "minecraft:birch_fence"); // cote marches basses de la tremie
        // (garde-corps de la tremie au pont des embarcations : poses par aftHouse)
        // escalier avant : principal -> pont avant, machines -> principal
        Mat steel = Mat.of("minecraft:polished_andesite");
        flight(52, -3, Dir.EAST, 3, F1, 6, "minecraft:spruce_stairs", Mat.of("minecraft:spruce_planks"));
        flight(57, 3, Dir.WEST, 3, F0, 6, "minecraft:polished_andesite_stairs", steel);
        railLine(54, 0, 56, 0, F2 + 1, "minecraft:spruce_fence");
        railLine(54, -4, 56, -4, F2 + 1, "minecraft:spruce_fence");
        railLine(53, -3, 53, -1, F2 + 1, "minecraft:spruce_fence");
        railLine(53, 0, 55, 0, F1 + 1, "minecraft:iron_bars");
        railLine(53, 4, 55, 4, F1 + 1, "minecraft:iron_bars");
        railLine(56, 1, 56, 3, F1 + 1, "minecraft:iron_bars");
        // escalier des chauffeurs : chaufferie -> poste d'equipage (le cote x -56 est la cloison de la piscine)
        flight(-55, -2, Dir.WEST, 3, F0, 6, "minecraft:polished_andesite_stairs", steel);
        railLine(-59, -1, -57, -1, F1 + 1, "minecraft:iron_bars");
        railLine(-59, -5, -57, -5, F1 + 1, "minecraft:iron_bars");
        // descente de la poupe : principal -> pont arriere (le cote x -59 est le mur du rouf)
        flight(-58, 4, Dir.WEST, 3, F1, 6, "minecraft:spruce_stairs", Mat.of("minecraft:spruce_planks"));
        railLine(-62, 1, -60, 1, F2 + 1, "minecraft:spruce_fence");
        railLine(-62, 5, -60, 5, F2 + 1, "minecraft:spruce_fence");
        // escaliers exterieurs vers le pont des embarcations
        Mat white = Mat.of("minecraft:white_concrete");
        flight(-56, 7, Dir.EAST, 2, F2, 6, "minecraft:spruce_stairs", white);
        flight(-56, -8, Dir.EAST, 2, F2, 6, "minecraft:spruce_stairs", white);
        flight(48, 8, Dir.WEST, 2, F2, 6, "minecraft:spruce_stairs", white);
        flight(48, -7, Dir.WEST, 2, F2, 6, "minecraft:spruce_stairs", white);
        // pas de main courante le long de ces volees : une barriere qui monte avec les marches ferait un escalier de
        // dessus de barrieres jusqu'au bord du pont (et par-dessus bord) ; limon plein en quartz a la place
        // cote mer : limon plein qui depasse chaque marche de 3 blocs (ni saut vers le pavois, ni par-dessus bord)
        for (int k = 0; k < 6; k++)
            for (int za : new int[]{-9, 9})
                for (int y = F2 + 1; y <= F2 + 4 + k; y++) {
                    set(-56 + k, y, za, y == F2 + 4 + k ? "minecraft:smooth_quartz" : WHITE.at(-56 + k, y, za));
                    set(48 - k, y, za, y == F2 + 4 + k ? "minecraft:smooth_quartz" : WHITE.at(48 - k, y, za));
                }
        for (int k = 0; k < 6; k++)
            for (int za : new int[]{-6, 6}) {
                if (isAir(-56 + k, F2 + 1, za)) set(-56 + k, F2 + 1, za, slab("minecraft:smooth_quartz_slab", "bottom"));
                if (isAir(48 - k, F2 + 1, za)) set(48 - k, F2 + 1, za, slab("minecraft:smooth_quartz_slab", "bottom"));
            }
    }

    // ================================================================ pont des machines

    private void boilerRoom() {
        int y0 = F0 + 1;
        for (int x = -60; x <= -36; x++)
            for (int z = -12; z <= 12; z++)
                if (inner(x, z, F0)) set(x, F0, z, STEEL_FLOOR.at(x, F0, z));
        // chaudieres : cylindres couches le long de x (rangées babord et tribord)
        for (int bx : new int[]{-53, -47, -41})
            for (int sz : new int[]{-1, 1}) {
                double cz = sz * 7.5, cy = 60.2;
                for (int x = bx; x <= bx + 4; x++)
                    for (int z = (int) cz - 3; z <= (int) cz + 3; z++)
                        for (int y = y0; y <= F1 - 1; y++) {
                            double d = Math.hypot(z - cz, (y - cy) * 1.1);
                            if (d > 2.6 || !inner(x, z, y)) continue;
                            boolean end = x == bx || x == bx + 4;
                            set(x, y, z, end ? "minecraft:polished_blackstone_bricks" : (y == 60 ? "minecraft:iron_block" : "minecraft:black_concrete"));
                        }
                // foyers cote allee centrale
                int fz = sz * 4;
                Dir face = sz < 0 ? Dir.SOUTH : Dir.NORTH;
                set(bx + 1, y0, fz, facing("minecraft:blast_furnace", face).replace("]", ",lit=true]"));
                set(bx + 3, y0, fz, facing("minecraft:blast_furnace", face).replace("]", ",lit=true]"));
                set(bx + 2, y0, fz, "minecraft:polished_blackstone_bricks");
                set(bx + 2, y0 + 1, fz, "minecraft:waxed_copper_bulb[lit=true,powered=false]");
                // tas de charbon contre la chaudiere
                set(bx + 2, y0, sz * 11, "minecraft:coal_block");
                setIfAir(bx + 1, y0, sz * 11, slab("minecraft:blackstone_slab", "bottom"));
            }
        // tuyaux de vapeur au plafond de l'allee
        for (int x = -58; x <= -36; x++)
            for (int sz : new int[]{-1, 1}) {
                // 1.9.1 : le tuyau nord s'arrete avant l'escalier des chauffeurs (il passait a hauteur de tete)
                if (sz < 0 && x <= -55) continue;
                boolean flange = Math.floorMod(x, 6) == 0 || (sz < 0 && x == -54);
                set(x, F1 - 1, sz * 3, flange ? "minecraft:waxed_copper_block" : "minecraft:waxed_cut_copper");
                if (flange) {
                    setIfAir(x, F1 - 1, sz * 2, "minecraft:waxed_copper_bulb[lit=true,powered=false]"); // vanne
                    setIfAir(x, F1 - 1, sz * 4, "minecraft:waxed_copper_block"); // bride
                }
            }
        // pelles, chariot, eclairage
        set(-37, y0, -2, "minecraft:cauldron");
        set(-37, y0, 2, "minecraft:barrel[facing=up,open=false]");
        for (int x : new int[]{-57, -50, -44, -38}) hangLamp(x, F1, 0, "minecraft:lantern");
        set(-58, y0, 8, "minecraft:chest[facing=east,type=single,waterlogged=false]");
        set(-58, y0, 10, "minecraft:coal_block");
        set(-59, y0, 9, "minecraft:coal_block");
        set(-58, y0 + 1, 9, slab("minecraft:blackstone_slab", "bottom"));
    }

    private void engineRoom() {
        // sol du bas, puits sur deux ponts, coursive au niveau du pont principal
        for (int x = -34; x <= -15; x++)
            for (int z = -12; z <= 12; z++) {
                if (inner(x, z, F0)) set(x, F0, z, STEEL_FLOOR.at(x, F0, z));
                if (!inner(x, z, F1)) continue;
                boolean walk = Math.abs(z) >= 8 || x <= -33 || x >= -16 || (x >= -25 && x <= -23);
                set(x, F1, z, walk ? "minecraft:waxed_copper_grate" : B.AIR);
            }
        // garde-corps des coursives laterales et des paliers d'extremite
        for (int x = -34; x <= -15; x++)
            for (int z = -8; z <= 8; z++) {
                boolean lane = Math.abs(z) == 8 && !(x >= -25 && x <= -23) && x > -33 && x < -16;
                boolean endL = (x == -33 || x == -16) && Math.abs(z) < 8;
                if (lane || endL) {
                    set(x, F1 + 1, z, "minecraft:iron_bars");
                    set(x, F1 + 2, z, "minecraft:iron_bars");
                }
            }
        // deux machines alternatives : bati, colonnes, bielles, cylindres en cuivre jusqu'au pont principal
        for (int ex : new int[]{-30, -21}) {
            for (int x = ex; x <= ex + 4; x++)
                for (int z = -3; z <= 3; z++) {
                    set(x, F0 + 1, z, "minecraft:polished_deepslate");
                    for (int y = F1 + 1; y <= F1 + 3; y++) if (Math.abs(z) <= 2) set(x, y, z, (x + y) % 3 == 0 ? "minecraft:waxed_exposed_copper" : "minecraft:waxed_copper_block");
                    if (Math.abs(z) <= 2) set(x, F1 + 4, z, slab("minecraft:waxed_cut_copper_slab", "bottom"));
                }
            for (int x : new int[]{ex, ex + 4})
                for (int z : new int[]{-3, 3})
                    for (int y = F0 + 2; y <= F1; y++) set(x, y, z, "minecraft:iron_block");
            for (int x : new int[]{ex + 1, ex + 3}) {
                set(x, F0 + 2, 0, "minecraft:grindstone[face=floor,facing=north]");
                for (int y = F0 + 3; y <= F1 - 1; y++) set(x, y, 0, chain('y'));
                set(x, F1, 0, "minecraft:piston[facing=up,extended=false]");
            }
            set(ex + 2, F0 + 2, 0, chain('x'));
            set(ex + 2, F1, -2, "minecraft:waxed_copper_bulb[lit=true,powered=false]");
            set(ex + 2, F1, 2, "minecraft:waxed_copper_bulb[lit=true,powered=false]");
            set(ex + 2, F1 + 5, 0, "minecraft:lightning_rod[facing=up]");
        }
        // volants (disques verticaux entre les deux machines)
        for (int y = F0 + 2; y <= F1 - 1; y++)
            for (int z = -2; z <= 2; z++) {
                double d = Math.hypot(z, y - 60);
                if (d <= 2.4 && d >= 1.4) set(-24, y, z, "minecraft:iron_block");
            }
        set(-24, 60, 0, "minecraft:polished_blackstone");
        // escaliers machines -> coursive
        Mat steel = Mat.of("minecraft:polished_andesite");
        flight(-31, -6, Dir.EAST, 2, F0, 6, "minecraft:polished_andesite_stairs", steel);
        flight(-17, 6, Dir.WEST, 2, F0, 6, "minecraft:polished_andesite_stairs", steel);
        // poste de controle : transmetteurs d'ordres, manometres
        for (int z = -6; z <= 3; z += 3) { // 1.9.1 : plus de pupitre en z 6 (devant la premiere marche)
            setIfAir(-16, F0 + 1, z, "minecraft:polished_andesite");
            setIfAir(-16, F0 + 2, z, "minecraft:lever[face=floor,facing=west,powered=false]");
        }
        for (int z = -11; z <= 11; z += 2)
            if (inner(-34, z, F1 + 2) && isAir(-34, F1 + 2, z)) set(-34, F1 + 2, z, "minecraft:waxed_copper_bulb[lit=true,powered=false]");
        for (int x = -32; x <= -17; x += 5) {
            hangLamp(x, F2, 10, "minecraft:lantern");
            hangLamp(x, F2, -10, "minecraft:lantern");
        }
        set(-24, F2 - 1, 0, chain('y'));
        set(-24, F2 - 2, 0, hanging("minecraft:lantern"));
        for (int x = -32; x <= -17; x += 4) setIfAir(x, F0 + 1, 10, "minecraft:light[level=12]");
    }

    private void holds() {
        int y0 = F0 + 1;
        for (int x = -13; x <= D0X2; x++)
            for (int z = -12; z <= 12; z++)
                if (inner(x, z, F0)) set(x, F0, z, HOLD_FLOOR.at(x, F0, z));
        // epontilles
        for (int x = -10; x <= 54; x += 6)
            for (int z : new int[]{-5, 5})
                if (inner(x, z, y0) && isAir(x, y0, z) && x != 20 && x != 40) for (int y = y0; y < F1; y++) set(x, y, z, "minecraft:polished_deepslate_wall");
        // piles de caisses (ilots avec allees)
        int[][] stacks = {{-11, -10, 3, 2, 2}, {-6, 7, 2, 3, 3}, {-2, -3, 3, 2, 1}, {3, 8, 3, 3, 2}, {6, -10, 2, 3, 3}, {10, -2, 2, 2, 2},
                {15, 7, 3, 2, 1}, {16, -8, 2, 2, 2}, {23, 8, 2, 3, 3}, {24, -10, 3, 2, 2}, {33, -3, 3, 2, 2}, {36, 6, 2, 3, 1},
                {43, -6, 2, 3, 2}, {46, 4, 3, 2, 3}, {50, -4, 2, 2, 1},
                {-12, 2, 2, 2, 2}, {1, -6, 2, 2, 1}, {12, -10, 3, 2, 2}, {29, 9, 3, 2, 2}, {31, -9, 2, 2, 3}, {37, 1, 2, 2, 1},
                {53, -6, 2, 2, 2}};
        for (int[] c : stacks) crateStack(c[0], c[1], c[2], c[3], c[4]);
        // malles et sacs postaux
        for (int[] t : new int[][]{{-8, 0}, {1, 11}, {12, 4}, {28, 3}, {38, -9}, {44, 9}}) {
            setIfAir(t[0], y0, t[1], "minecraft:chest[facing=north,type=single,waterlogged=false]");
            setIfAir(t[0] + 1, y0, t[1], "minecraft:white_wool");
            setIfAir(t[0] + 1, y0 + 1, t[1], "minecraft:white_carpet");
        }
        // automobile ancienne dans la cale du milieu
        car(26, -1);
        // filets et cordages au plafond, eclairage
        for (int x = -10; x <= 56; x += 6) hangLamp(x, F1, 0, x % 12 == 2 ? "minecraft:soul_lantern" : "minecraft:lantern");
        for (int x = -12; x <= 56; x += 8) {
            setIfAir(x, y0, 11, "minecraft:light[level=11]");
            setIfAir(x + 4, y0, -11, "minecraft:light[level=11]");
        }
    }

    private void crateStack(int x0, int z0, int w, int d, int h) {
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++) {
                if (!inner(x, z, F0 + 1)) continue;
                int hh = h - (int) (h(x, 3, z) * 2);
                for (int y = F0 + 1; y <= F0 + Math.max(1, hh); y++) {
                    boolean edge = (x == x0 || x == x0 + w - 1) && (z == z0 || z == z0 + d - 1);
                    String b = edge ? "minecraft:stripped_dark_oak_log[axis=y]" : (h(x, y, z) < 0.3 ? "minecraft:barrel[facing=up,open=false]" : "minecraft:spruce_planks");
                    set(x, y, z, b);
                }
                int top = F0 + 1 + Math.max(1, hh);
                if (h(x, 9, z) < 0.35 && isAir(x, top, z)) set(x, top, z, "minecraft:brown_carpet");
            }
    }

    private void car(int x0, int zc) {
        int y = F0 + 1;
        for (int x = x0; x <= x0 + 5; x++)
            for (int z = zc - 1; z <= zc + 1; z++) {
                boolean wheel = (x == x0 + 1 || x == x0 + 4) && z != zc;
                set(x, y, z, wheel ? "minecraft:black_wool" : (z == zc ? slab("minecraft:polished_blackstone_slab", "top") : B.AIR));
                if (x == x0 || x == x0 + 5) set(x, y, z, z == zc ? "minecraft:polished_blackstone" : B.AIR);
            }
        for (int x = x0; x <= x0 + 5; x++)
            for (int z = zc - 1; z <= zc + 1; z++) set(x, y + 1, z, x >= x0 + 4 ? "minecraft:red_terracotta" : (x == x0 ? "minecraft:red_terracotta" : (z == zc ? "minecraft:brown_terracotta" : "minecraft:red_terracotta")));
        set(x0 + 6, y + 1, zc - 1, "minecraft:end_rod[facing=east]");
        set(x0 + 6, y + 1, zc + 1, "minecraft:end_rod[facing=east]");
        set(x0 + 6, y, zc, stairs("minecraft:polished_blackstone_stairs", Dir.WEST, true));
        for (int z = zc - 1; z <= zc + 1; z++) {
            set(x0 + 3, y + 2, z, "minecraft:glass_pane");
            set(x0 + 1, y + 2, z, stairs("minecraft:spruce_stairs", Dir.WEST, false));
            set(x0 + 2, y + 2, z, z == zc ? B.AIR : "minecraft:brown_carpet");
        }
        set(x0 + 2, y + 2, zc, "minecraft:dark_oak_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]");
        set(x0, y + 2, zc, "minecraft:brown_wool");
        set(x0 + 4, y + 2, zc, slab("minecraft:waxed_cut_copper_slab", "bottom"));
    }

    // ================================================================ pont principal

    private void crewMess() {
        int y0 = F1 + 1;
        for (int x = D1X1; x <= -57; x++)
            for (int z = -12; z <= 12; z++)
                if (inner(x, z, F1) && isAir(x, y0, z) && !isAir(x, F1, z) && !B.name(get(x, F1, z)).contains("stairs"))
                    set(x, F1, z, HOLD_FLOOR.at(x, F1, z));
        // longue table d'equipage (barrieres + plateau en plaques) et bancs
        for (int x = -67; x <= -63; x++) {
            set(x, y0, -7, "minecraft:spruce_fence");
            set(x, y0 + 1, -7, "minecraft:spruce_pressure_plate");
            setIfAir(x, y0, -6, stairs("minecraft:spruce_stairs", Dir.SOUTH, false));
            if (inner(x, -8, y0)) setIfAir(x, y0, -8, stairs("minecraft:spruce_stairs", Dir.NORTH, false));
        }
        cafeTable(-65, y0, 0, "spruce", true);
        cafeTable(-62, y0, 8, "spruce", true);
        // couchettes contre la poupe (pied en -67, tete en -68)
        for (int z : new int[]{3, 5, 7}) {
            if (!inner(-68, z, y0)) continue;
            set(-67, y0, z, "minecraft:white_bed[facing=west,part=foot,occupied=false]");
            set(-68, y0, z, "minecraft:white_bed[facing=west,part=head,occupied=false]");
        }
        set(-63, y0, -9, "minecraft:barrel[facing=up,open=false]");
        set(-63, y0 + 1, -9, standing("minecraft:lantern"));
        hangLamp(-64, F2, -4, "minecraft:lantern");
        hangLamp(-60, F2, 7, "minecraft:lantern");
        set(-58, y0, 8, "minecraft:barrel[facing=up,open=false]");
        set(-58, y0, 9, "minecraft:barrel[facing=up,open=false]");
        set(-58, y0 + 1, 8, "minecraft:barrel[facing=up,open=false]");
    }

    private void pool() {
        int y0 = F1 + 1;
        for (int x = -55; x <= -44; x++)
            for (int z = -12; z <= 12; z++) {
                if (!inner(x, z, F1)) continue;
                boolean basin = x >= -53 && x <= -46 && Math.abs(z) <= 3;
                if (basin) {
                    set(x, F1, z, B.WATER);
                    set(x, F1 - 1, z, (x + z) % 4 == 0 ? "minecraft:sea_lantern" : "minecraft:prismarine_bricks");
                } else {
                    boolean rim = x >= -54 && x <= -45 && Math.abs(z) <= 4;
                    set(x, F1, z, rim ? "minecraft:smooth_quartz" : ((x + z) % 2 == 0 ? "minecraft:white_terracotta" : "minecraft:light_blue_terracotta"));
                }
            }
        // colonnes et arcades
        for (int x : new int[]{-53, -49, -45})
            for (int sz : new int[]{-1, 1}) {
                int z = sz * 6;
                for (int y = y0; y <= F2 - 2; y++) set(x, y, z, "minecraft:white_terracotta");
                set(x, F2 - 1, z, "minecraft:chiseled_quartz_block");
                set(x + 1, F2 - 1, z, stairs("minecraft:quartz_stairs", Dir.WEST, true));
                set(x - 1, F2 - 1, z, stairs("minecraft:quartz_stairs", Dir.EAST, true));
            }
        // cabines de bain (niches a rideaux) le long des flancs
        for (int x = -54; x <= -46; x += 3)
            for (int sz : new int[]{-1, 1}) {
                int z = sz * 10;
                if (!inner(x, z, y0)) continue;
                for (int y = y0; y <= y0 + 2; y++) {
                    set(x, y, z, "minecraft:white_terracotta");
                    set(x, y, z - sz, "minecraft:white_terracotta");
                }
                set(x + 1, y0, z, stairs("minecraft:birch_stairs", sz < 0 ? Dir.NORTH : Dir.SOUTH, false));
                set(x + 1, y0 + 2, z - sz, "minecraft:light_blue_wool");
            }
        // echelle de bain, bouees, plantes
        set(-54, F1, 0, "minecraft:smooth_quartz");
        set(-53, F1, 0, B.WATER);
        for (int z : new int[]{-8, 8}) bigPlant(-44, y0, z, "birch");
        pot(-55, y0, -11);
        pot(-55, y0, 11);
        for (int x = -53; x <= -46; x += 3) hangLamp(x, F2, 0, "minecraft:lantern");
        set(-50, F2 - 1, 8, "minecraft:sea_lantern");
        set(-50, F2 - 1, -8, "minecraft:sea_lantern");
    }

    private void aftHall() {
        int y0 = F1 + 1;
        for (int x = -42; x <= -36; x++)
            for (int z = -12; z <= 12; z++)
                if (inner(x, z, F1) && !isAir(x, F1, z) && !B.name(get(x, F1, z)).contains("stairs")) set(x, F1, z, (x + z) % 2 == 0 ? "minecraft:birch_planks" : "minecraft:stripped_birch_wood[axis=x]");
        rug(-41, 1, -37, 5, y0, "minecraft:blue_carpet", "minecraft:light_blue_carpet");
        armchair(-39, y0, 8, Dir.SOUTH, "birch");
        set(-41, y0, 8, "minecraft:potted_fern");
        hangLamp(-39, F2, 3, "minecraft:lantern");
        set(-42, y0, -9, "minecraft:bookshelf");
        set(-42, y0 + 1, -9, "minecraft:potted_azalea_bush");
    }

    private void galley() {
        int y0 = F1 + 1;
        for (int x = -13; x <= -2; x++)
            for (int z = -12; z <= 12; z++)
                if (inner(x, z, F1)) set(x, F1, z, (x + z) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:light_gray_concrete");
        // fourneaux en ilot central
        for (int x = -10; x <= -5; x++) {
            set(x, y0, -1, x % 2 == 0 ? "minecraft:smoker[facing=north,lit=true]" : "minecraft:furnace[facing=north,lit=true]");
            set(x, y0, 1, x % 2 == 0 ? "minecraft:furnace[facing=south,lit=true]" : "minecraft:smoker[facing=south,lit=true]");
            set(x, y0, 0, "minecraft:polished_andesite");
            set(x, y0 + 1, 0, x % 3 == 0 ? "minecraft:cauldron" : slab("minecraft:smooth_stone_slab", "bottom"));
            set(x, F2 - 1, 0, "minecraft:iron_block");
            if (x % 2 == 0) set(x, F2 - 2, 0, hanging("minecraft:lantern"));
        }
        // plans de travail contre les flancs, etageres au-dessus
        for (int x = -11; x <= -4; x++)
            for (int sz : new int[]{-1, 1}) {
                int z = sz * 11;
                if (!inner(x, z, y0)) continue;
                int m = Math.floorMod(x, 4);
                set(x, y0, z, m == 0 ? "minecraft:water_cauldron[level=3]" : (m == 1 ? "minecraft:barrel[facing=up,open=false]" : "minecraft:smooth_stone"));
                if (m >= 2) set(x, y0 + 1, z, m == 2 ? "minecraft:flower_pot" : slab("minecraft:smooth_stone_slab", "bottom"));
                set(x, y0 + 2, z, "minecraft:spruce_trapdoor[facing=" + (sz < 0 ? "south" : "north") + ",half=top,open=false,powered=false,waterlogged=false]");
            }
        // chambre froide (piliers de glace) et sacs
        set(-12, y0, -5, "minecraft:packed_ice");
        set(-12, y0 + 1, -5, "minecraft:packed_ice");
        set(-12, y0, 5, "minecraft:hay_block[axis=y]");
        set(-3, y0, -5, "minecraft:barrel[facing=up,open=false]");
        set(-3, y0, 4, "minecraft:crafting_table");
        hangLamp(-8, F2, -6, "minecraft:lantern");
        hangLamp(-8, F2, 6, "minecraft:lantern");
    }

    private void dining() {
        int y0 = F1 + 1;
        for (int x = 0; x <= 20; x++)
            for (int z = -12; z <= 12; z++) {
                if (!inner(x, z, F1)) continue;
                boolean aisle = Math.abs(z) <= 1;
                set(x, F1, z, aisle ? "minecraft:red_terracotta" : ((x / 2 + z / 2) % 2 == 0 ? "minecraft:white_terracotta" : "minecraft:light_gray_terracotta"));
            }
        rug(0, -1, 20, 1, y0, "minecraft:red_carpet", "minecraft:red_carpet");
        // colonnes et caissons du plafond
        for (int x : new int[]{4, 10, 16})
            for (int sz : new int[]{-1, 1}) {
                int z = sz * 5;
                set(x, y0, z, "minecraft:quartz_bricks");
                for (int y = y0 + 1; y <= F2 - 2; y++) set(x, y, z, "minecraft:quartz_pillar[axis=y]");
                set(x, F2 - 1, z, "minecraft:chiseled_quartz_block");
            }
        for (int x = 0; x <= 20; x++)
            for (int z = -11; z <= 11; z++) {
                if (!inner(x, z, F2 - 1) || !isAir(x, F2 - 1, z)) continue;
                if (x % 6 == 4 || z % 5 == 0) set(x, F2 - 1, z, "minecraft:smooth_quartz_slab[type=top]");
            }
        // tables (rondes, 4 chaises) en quinconce
        for (int x = 2; x <= 19; x += 4)
            for (int z : new int[]{-9, -3, 3, 9}) {
                int xx = x + ((z == -3 || z == 9) ? 2 : 0);
                if (xx > 19 || !inner(xx, z, y0) || !isAir(xx, y0, z)) continue;
                set(xx, y0, z, "minecraft:dark_oak_fence");
                set(xx, y0 + 1, z, candles(2, "white"));
                for (Dir d : Dir.values()) if (h(xx, d.ordinal(), z) < 0.8) setIfAir(xx + d.dx, y0, z + d.dz, stairs("minecraft:dark_oak_stairs", d, false));
            }
        // buffet et lustres
        for (int z = -3; z <= 3; z++) {
            if (Math.abs(z) <= 1) continue;
            set(1, y0, z, "minecraft:dark_oak_planks");
            set(1, y0 + 1, z, "minecraft:white_carpet");
        }
        for (int x : new int[]{7, 13}) chandelier(x, F2 - 1, 0, 1, true, "minecraft:dark_oak_trapdoor", "minecraft:lantern");
        for (int x : new int[]{2, 18}) for (int sz : new int[]{-1, 1}) hangLamp(x, F2, sz * 8, "minecraft:lantern");
    }

    private void grandHall() {
        int y0 = F1 + 1;
        for (int x = 22; x <= 32; x++)
            for (int z = -12; z <= 12; z++)
                if (inner(x, z, F1)) set(x, F1, z, Math.floorMod(x + z, 2) == 0 ? "minecraft:polished_diorite" : "minecraft:polished_blackstone");
        // pendule (contre la cloison avant, a cote du passage), statue, lampadaires de depart des volees
        set(32, y0, -3, "minecraft:dark_oak_planks");
        set(32, y0 + 1, -3, "minecraft:dark_oak_planks");
        set(32, y0 + 2, -3, "minecraft:daylight_detector[inverted=false,power=0]");
        set(31, y0 + 1, -3, "minecraft:dark_oak_trapdoor[facing=west,half=top,open=true,powered=false,waterlogged=false]");
        for (int sz : new int[]{-1, 1}) {
            // 1.9.1 : socles en z +-10 (en z +-8 ils bouchaient les portes doubles x 33)
            set(32, y0, sz * 10, "minecraft:polished_blackstone");
            set(32, y0 + 1, sz * 10, "minecraft:gold_block");
            set(32, y0 + 2, sz * 10, standing("minecraft:lantern"));
            set(32, y0, sz * 4, "minecraft:dark_oak_fence");
            set(32, y0 + 1, sz * 4, standing("minecraft:lantern"));
        }
        set(23, y0, 0, "minecraft:quartz_bricks");
        set(23, y0 + 1, 0, "minecraft:quartz_pillar[axis=y]");
        set(23, y0 + 2, 0, "minecraft:potted_flowering_azalea_bush");
        bigPlant(22, y0, -10, "dark_oak");
        bigPlant(22, y0, 10, "dark_oak");
        chandelier(27, F2 - 1, 0, 1, true, "minecraft:dark_oak_trapdoor", "minecraft:lantern");
    }

    private void lobby() {
        int y0 = F1 + 1;
        for (int x = 34; x <= 36; x++)
            for (int z = -12; z <= 12; z++) if (inner(x, z, F1)) set(x, F1, z, DARK.at(x, F1, z));
        // guichet du commissaire de bord
        for (int z = 4; z <= 9; z++) {
            if (!inner(35, z, y0)) continue;
            set(35, y0, z, z == 4 ? "minecraft:dark_oak_planks" : (z % 2 == 0 ? "minecraft:barrel[facing=up,open=false]" : "minecraft:dark_oak_planks"));
            set(35, y0 + 1, z, z == 6 ? "minecraft:lectern[facing=west,has_book=false,powered=false]" : slab("minecraft:dark_oak_slab", "bottom"));
        }
        set(36, y0, 10, "minecraft:chest[facing=west,type=single,waterlogged=false]");
        armchair(35, y0, -8, Dir.EAST, "dark_oak");
        set(35, y0, -10, "minecraft:potted_fern");
        hangLamp(35, F2, 0, "minecraft:lantern");
    }

    private void cabins() {
        int y0 = F1 + 1, y2 = F2 - 1;
        // couloir central z -1..1, murs de couloir en z +-2, murs de cabines en x 41, 45, 49
        for (int x = 38; x <= 49; x++) {
            for (int z = -1; z <= 1; z++) set(x, F1, z, "minecraft:red_terracotta");
            for (int sz : new int[]{-1, 1}) for (int y = y0; y <= y2; y++) set(x, y, sz * 2, CABIN.at(x, y, sz * 2));
        }
        rug(38, -1, 49, 1, y0, "minecraft:red_carpet", "minecraft:red_carpet");
        for (int x : new int[]{41, 45, 49}) wallXz(x, -12, -3, y0, y2, CABIN);
        for (int x : new int[]{41, 45, 49}) wallXz(x, 3, 12, y0, y2, CABIN);
        for (int x : new int[]{41, 45, 49}) for (int sz : new int[]{-1, 1}) for (int y = y0; y <= y2; y++) set(x, y, sz * 2, "minecraft:stripped_dark_oak_log[axis=y]");
        // (x0 = premiere colonne de la cabine, sz = flanc, open, porte communicante vers +x)
        cabin(38, -1, true, true);
        cabin(42, -1, true, false);
        cabin(46, -1, false, false);
        cabin(38, 1, false, false);
        cabin(42, 1, true, true);
        cabin(46, 1, true, false);
        for (int x : new int[]{39, 44, 48}) hangLamp(x, F2, 0, "minecraft:lantern");
    }

    private void cabin(int x0, int sz, boolean open, boolean linkNext) {
        int y0 = F1 + 1, y2 = F2 - 1;
        int zc = sz * 2;
        Dir toCorridor = sz < 0 ? Dir.SOUTH : Dir.NORTH;
        int dx = x0 + 1;
        if (!open) {
            // cabine fermee : porte en fer close, volume plein derriere
            for (int x = x0; x <= x0 + 2; x++)
                for (int z = sz * 3; Math.abs(z) <= 12; z += sz)
                    for (int y = y0; y <= y2; y++) if (inner(x, z, y)) set(x, y, z, CABIN.at(x, y, z));
            set(dx, y0, zc, B.door("minecraft:iron_door", toCorridor, false, false));
            set(dx, y0 + 1, zc, B.door("minecraft:iron_door", toCorridor, true, false));
            set(dx, y0 + 2, zc + (sz < 0 ? 1 : -1), "minecraft:spruce_wall_sign[facing=" + toCorridor.id() + ",waterlogged=false]");
            return;
        }
        for (int x = x0; x <= x0 + 2; x++)
            for (int z = sz * 3; Math.abs(z) <= 12; z += sz)
                if (inner(x, z, F1)) set(x, F1, z, "minecraft:spruce_planks");
        set(dx, y0, zc, B.door("minecraft:spruce_door", toCorridor, false, false));
        set(dx, y0 + 1, zc, B.door("minecraft:spruce_door", toCorridor, true, false));
        // lit contre le mur arriere de la cabine (cote x0), tete vers la coque
        int zb = sz * 5;
        set(x0, y0, zb, "minecraft:red_bed[facing=" + (sz < 0 ? "north" : "south") + ",part=foot,occupied=false]");
        set(x0, y0, zb + sz, "minecraft:red_bed[facing=" + (sz < 0 ? "north" : "south") + ",part=head,occupied=false]");
        // armoire, lavabo, fauteuil, tapis, applique
        set(x0 + 2, y0, sz * 8, "minecraft:barrel[facing=" + (sz < 0 ? "south" : "north") + ",open=false]");
        set(x0 + 2, y0 + 1, sz * 8, "minecraft:barrel[facing=up,open=false]");
        set(x0 + 2, y0, sz * 4, "minecraft:cauldron");
        set(x0 + 2, y0 + 2, sz * 4, "minecraft:glass_pane");
        set(x0 + 1, y0, sz * 7, "minecraft:blue_carpet");
        set(x0 + 1, y0, sz * 8, "minecraft:blue_carpet");
        set(x0, y0, sz * 8, stairs("minecraft:spruce_stairs", sz < 0 ? Dir.NORTH : Dir.SOUTH, false));
        int zw = sz * (hw(x0 + 1, y0) - 2);
        if (isAir(x0 + 1, y0 + 2, zw)) set(x0 + 1, y0 + 3, zw, hanging("minecraft:lantern"));
        set(x0 + 1, y0 + 4, zw, B.isAir(get(x0 + 1, y0 + 4, zw)) ? "minecraft:spruce_planks" : get(x0 + 1, y0 + 4, zw));
        if (linkNext) {
            // porte communicante vers la cabine voisine (+x) : la suite a deux portes sur le couloir
            // 1.9.1 : en z 3 (en z 6, elle donnait sur la tete du lit de la cabine voisine)
            set(x0 + 3, y0, sz * 3, B.door("minecraft:spruce_door", Dir.EAST, false, false));
            set(x0 + 3, y0 + 1, sz * 3, B.door("minecraft:spruce_door", Dir.EAST, true, false));
        }
    }

    private void foreHall() {
        int y0 = F1 + 1;
        for (int x = 50; x <= D1X2; x++)
            for (int z = -12; z <= 12; z++)
                if (inner(x, z, F1) && !B.name(get(x, F1, z)).contains("stairs") && !isAir(x, F1, z)) set(x, F1, z, "minecraft:spruce_planks");
        // fond de la proue : cloison
        wallX(D1X2, y0, F2 - 1, CABIN);
        set(50, y0, -6, "minecraft:barrel[facing=up,open=false]");
        set(50, y0, 6, "minecraft:barrel[facing=up,open=false]");
        set(51, y0, -6, "minecraft:chest[facing=east,type=single,waterlogged=false]");
        hangLamp(51, F2, -1, "minecraft:lantern");
        hangLamp(56, F2, 5, "minecraft:lantern");
    }

    // ================================================================ pont promenade : pieces du rouf

    private void veranda() {
        int y0 = F2 + 1;
        for (int x = -47; x <= -44; x++)
            for (int z = -7; z <= 7; z++) set(x, F2, z, (x + z) % 2 == 0 ? "minecraft:red_terracotta" : "minecraft:white_terracotta");
        // treillage sur les murs, palmiers en bac, sieges en rotin
        for (int z = -7; z <= 7; z++) for (int y = y0 + 1; y <= F3 - 2; y++) if (Math.floorMod(z + y, 2) == 0 && isAir(-44, y, z) && Math.abs(z) > 2) set(-44, y, z, "minecraft:birch_fence");
        for (int z : new int[]{-7, 7}) bigPlant(-47, y0, z, "birch");
        cafeTable(-46, y0, -3, "bamboo", false);
        cafeTable(-45, y0, 5, "bamboo", false);
        setIfAir(-46, y0, -4, stairs("minecraft:bamboo_mosaic_stairs", Dir.NORTH, false));
        setIfAir(-45, y0, 6, stairs("minecraft:bamboo_mosaic_stairs", Dir.SOUTH, false));
        setIfAir(-47, y0, 0, "minecraft:potted_flowering_azalea_bush");
        hangLamp(-46, F3, 0, "minecraft:lantern");
    }

    private void smokingRoom() {
        int y0 = F2 + 1;
        for (int x = -34; x <= -15; x++)
            for (int z = -7; z <= 7; z++) set(x, F2, z, "minecraft:dark_oak_planks");
        // lambris : pilastres sombres sur les murs lateraux interieurs
        for (int x = -33; x <= -16; x += 3)
            for (int sz : new int[]{-1, 1}) if (isAir(x, y0, sz * 7)) for (int y = y0; y <= F3 - 1; y++) set(x, y, sz * 7, "minecraft:stripped_dark_oak_log[axis=y]");
        // cheminee contre la cloison avant
        for (int z = -2; z <= 2; z++) {
            for (int y = y0; y <= F3 - 1; y++) set(-15, y, z, Math.abs(z) == 2 ? "minecraft:polished_blackstone_bricks" : "minecraft:bricks");
        }
        // foyer en retrait derriere un pare-feu en barreaux
        for (int z = -1; z <= 1; z++) {
            set(-15, y0, z, "minecraft:campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]");
            set(-15, y0 + 1, z, B.AIR);
            set(-16, y0, z, "minecraft:iron_bars");
        }
        set(-16, y0 + 2, -2, stairs("minecraft:polished_blackstone_brick_stairs", Dir.EAST, true));
        for (int z = -1; z <= 1; z++) set(-16, y0 + 2, z, slab("minecraft:polished_blackstone_brick_slab", "top"));
        set(-16, y0 + 2, 2, stairs("minecraft:polished_blackstone_brick_stairs", Dir.EAST, true));
        set(-16, y0 + 3, 0, "minecraft:gold_block");
        // fauteuils de cuir en groupes autour de tables de jeu
        for (int[] g : new int[][]{{-31, -4}, {-31, 4}, {-24, -4}, {-21, 4}, {-19, -4}}) {
            cafeTable(g[0], y0, g[1], "dark_oak", false);
            for (Dir d : Dir.values()) if (h(g[0], d.ordinal(), g[1]) < 0.75) armchairAt(g[0] + d.dx, y0, g[1] + d.dz, d.opposite());
        }
        // gaine de cheminee habillee (pilier a miroirs)
        casing(-27, 0, 1.6, 1.1, "minecraft:dark_oak_planks", "minecraft:gray_stained_glass");
        chandelier(-23, F3 - 1, 0, 1, false, "minecraft:dark_oak_trapdoor", "minecraft:lantern");
        rug(-31, -4, -18, 4, y0, "minecraft:red_carpet", "minecraft:brown_carpet");
        hangLamp(-32, F3, 0, "minecraft:lantern");
        hangLamp(-19, F3, 0, "minecraft:lantern");
    }

    /** Siege simple (escalier) ou le joueur assis regarde vers faces. */
    private void armchairAt(int x, int y, int z, Dir faces) {
        setIfAir(x, y, z, stairs("minecraft:spruce_stairs", faces.opposite(), false));
    }

    /** Gaine verticale elliptique (cheminee qui traverse le pont promenade) habillee de bois et de miroirs. */
    private void casing(int cx, int cz, double rx, double rz, String wood, String mirror) {
        for (int x = (int) Math.floor(cx - rx); x <= (int) Math.ceil(cx + rx); x++)
            for (int z = (int) Math.floor(cz - rz); z <= (int) Math.ceil(cz + rz); z++) {
                double d = Math.pow((x - cx) / rx, 2) + Math.pow((z - cz) / rz, 2);
                if (d > 1.05) continue;
                for (int y = F2 + 1; y <= F3 - 1; y++) {
                    boolean face = d > 0.45 && y >= F2 + 2 && y <= F3 - 2 && (x + z) % 2 == 0;
                    set(x, y, z, face ? mirror : (y == F2 + 1 || y == F3 - 1 ? "minecraft:polished_blackstone" : wood));
                }
            }
    }

    private void ballroom() {
        int y0 = F2 + 1;
        for (int x = -13; x <= 20; x++)
            for (int z = -7; z <= 7; z++) {
                boolean dance = x >= -3 && x <= 10 && Math.abs(z) <= 4;
                set(x, F2, z, dance ? ((x + z) % 2 == 0 ? "minecraft:birch_planks" : "minecraft:dark_oak_planks") : "minecraft:oak_planks");
            }
        // scene de l'orchestre (estrade) avec pupitres et piano
        for (int x = -13; x <= -11; x++)
            for (int z = -3; z <= 3; z++) set(x, F2 + 1, z, x == -11 ? stairs("minecraft:dark_oak_stairs", Dir.WEST, false) : "minecraft:dark_oak_planks");
        set(-13, F2 + 2, -2, "minecraft:note_block");
        set(-13, F2 + 2, -1, "minecraft:note_block");
        set(-13, F2 + 2, 0, "minecraft:jukebox");
        set(-12, F2 + 2, 2, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        set(-12, F2 + 2, -3, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        set(-13, F2 + 2, 3, stairs("minecraft:spruce_stairs", Dir.WEST, false));
        // gaines des cheminees en colonnes a miroirs
        casing(14, 0, 1.6, 1.1, "minecraft:smooth_quartz", "minecraft:light_gray_stained_glass");
        casing(-6, 0, 1.6, 1.1, "minecraft:smooth_quartz", "minecraft:light_gray_stained_glass");
        // tables autour de la piste
        for (int x = -2; x <= 18; x += 4)
            for (int sz : new int[]{-1, 1}) {
                int z = sz * 6;
                if (x >= 12 && x <= 16) continue;
                cafeTable(x, y0, z, "birch", false);
                setIfAir(x - 1, y0, z, stairs("minecraft:birch_stairs", Dir.WEST, false));
                setIfAir(x + 1, y0, z, stairs("minecraft:birch_stairs", Dir.EAST, false));
            }
        // bar en fond de salle
        for (int z = -4; z <= 4; z++) {
            set(19, y0, z, Math.abs(z) == 4 ? "minecraft:quartz_bricks" : "minecraft:dark_oak_planks");
            set(19, y0 + 1, z, slab("minecraft:smooth_quartz_slab", "bottom"));
            if (z % 2 == 0) set(20, y0 + 2, z, "minecraft:decorated_pot[facing=west]");
            set(17, y0, z, z % 2 == 0 ? stairs("minecraft:dark_oak_stairs", Dir.WEST, false) : B.AIR);
        }
        // lustres et appliques
        for (int x : new int[]{0, 7}) chandelier(x, F3 - 1, 0, 1, true, "minecraft:birch_trapdoor", "minecraft:lantern");
        for (int x = -10; x <= 18; x += 7) for (int sz : new int[]{-1, 1}) if (isAir(x, y0 + 2, sz * 7)) sconce(x, y0 + 1, sz * 7, sz < 0 ? Dir.SOUTH : Dir.NORTH, "minecraft:quartz_stairs", "minecraft:lantern");
        bigPlant(-10, y0, -6, "birch");
        bigPlant(-10, y0, 6, "birch");
    }

    private void library() {
        int y0 = F2 + 1;
        for (int x = 35; x <= 39; x++)
            for (int z = -7; z <= 7; z++) set(x, F2, z, "minecraft:spruce_planks");
        for (int x = 35; x <= 39; x++)
            for (int sz : new int[]{-1, 1})
                for (int y = y0; y <= F3 - 2; y++) {
                    int z = sz * 7;
                    if (x == 37 || x == 38) continue; // portes laterales
                    set(x, y, z, y == F3 - 2 ? slab("minecraft:spruce_slab", "top") : "minecraft:bookshelf");
                }
        set(36, y0, -4, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        set(36, y0, 4, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        set(38, y0, 0, "minecraft:cartography_table");
        armchairAt(38, y0, -1, Dir.SOUTH);
        armchairAt(38, y0, 1, Dir.NORTH);
        set(35, y0, 0, "minecraft:spruce_fence");
        set(35, y0 + 1, 0, "minecraft:light_blue_stained_glass");
        hangLamp(37, F3, -3, "minecraft:lantern");
        hangLamp(37, F3, 3, "minecraft:lantern");
        rug(35, -2, 39, 2, y0, "minecraft:green_carpet", "minecraft:lime_carpet");
    }

    // ================================================================ pont promenade couvert

    private void promenade() {
        for (int sz : new int[]{-1, 1}) {
            Dir out = sz < 0 ? Dir.NORTH : Dir.SOUTH;
            for (int x = HX1; x <= HX2; x++) {
                int w = hw(x, F2), zs = sz * w;
                // colonnes et vitrage sur le bordé ; poutres sous le pont des embarcations
                boolean col = Math.floorMod(x, 4) == 2;
                if (col) {
                    for (int y = F2 + 3; y <= F3 - 1; y++) set(x, y, zs, "minecraft:white_concrete");
                    for (int z = sz * (HZ + 1); Math.abs(z) < w; z += sz) set(x, F3 - 1, z, "minecraft:stripped_birch_log[axis=z]");
                } else if (x >= 16) {
                    set(x, F2 + 4, zs, "minecraft:glass_pane"); // promenade avant vitree
                }
                if (Math.floorMod(x, 8) == 6) set(x, F3 - 2, sz * (HZ + 2), hanging("minecraft:lantern"));
                // sieges adosses au pavois (casse le pan interieur du pavois ; trop bas pour l'enjamber)
                if (Math.floorMod(x, 8) == 4 && !isDoorZone(x)) setIfAir(x, F2 + 1, sz * (w - 1), stairs("minecraft:spruce_stairs", out, false));
            }
            // obstacles en quinconce (aucune ligne droite libre sur toute la longueur) : chaises longues et
            // gueridon cote rouf, jardinieres et banc cote mer
            int i = 0;
            for (int x = HX1 + 3; x <= HX2 - 3; x += 6, i++) {
                if (isDoorZone(x)) continue;
                if (i % 2 == 0) {
                    deckChair(x, F2 + 1, sz * (HZ + 1), Dir.EAST);
                    deckChair(x + 2, F2 + 1, sz * (HZ + 1), Dir.EAST);
                    set(x + 1, F2 + 1, sz * (HZ + 2), "minecraft:spruce_fence");
                    set(x + 1, F2 + 2, sz * (HZ + 2), "minecraft:potted_fern");
                } else {
                    int zb = sz * (HZ + 3);
                    set(x, F2 + 1, zb, "minecraft:moss_block");
                    set(x, F2 + 2, zb, h(x, 1, sz) < 0.5 ? "minecraft:azalea" : "minecraft:flowering_azalea");
                    set(x + 1, F2 + 1, zb, "minecraft:moss_block");
                    set(x + 1, F2 + 2, zb, "minecraft:azalea");
                    for (Dir d : new Dir[]{Dir.EAST, Dir.WEST}) {
                        int tx = d == Dir.EAST ? x + 2 : x - 1;
                        setIfAir(tx, F2 + 1, zb, B.trapdoor("minecraft:spruce_trapdoor", d, false, true));
                    }
                    set(x, F2 + 1, sz * (HZ + 2), stairs("minecraft:spruce_stairs", out, false));
                    set(x + 1, F2 + 1, sz * (HZ + 2), stairs("minecraft:spruce_stairs", out, false));
                }
            }
        }
    }

    private static boolean isDoorZone(int x) {
        for (int d : new int[]{-38, -26, -8, 9, 23, 37}) if (x >= d - 2 && x <= d + 3) return true;
        return false;
    }

    /** Chaise longue de pont : dossier (escalier) + repose-jambes (trappe fermee), orientee vers dir. */
    private void deckChair(int x, int y, int z, Dir dir) {
        if (!isAir(x, y, z) || !isAir(x + dir.dx, y, z + dir.dz)) return;
        set(x, y, z, stairs("minecraft:spruce_stairs", dir.opposite(), false));
        set(x + dir.dx, y, z + dir.dz, B.trapdoor("minecraft:spruce_trapdoor", dir, false, false));
    }

    // ================================================================ ponts decouverts

    private void foredeck() {
        int y0 = F2 + 1;
        // rouf de descente de l'escalier avant
        hut(51, -5, 59, 1, "minecraft:spruce");
        for (int y = y0; y <= y0 + 1; y++) set(59, y, -2, B.AIR);
        doorX(59, -2, y0, Dir.EAST, "spruce", false);
        doorX(51, -2, y0, Dir.WEST, "spruce", false);
        doorX(53, -5, y0, Dir.NORTH, "spruce", false);
        // panneau de cale (hiloire + bache), treuils
        for (int x = 53; x <= 57; x++)
            for (int z = 3; z <= 7; z++) {
                if (!inner(x, z, y0)) continue;
                boolean edge = x == 53 || x == 57 || z == 3 || z == 7;
                set(x, y0, z, edge ? "minecraft:spruce_planks" : "minecraft:green_wool");
                if (!edge) set(x, y0 + 1, z, "minecraft:green_carpet");
            }
        for (int z : new int[]{2, 8}) if (inner(51, z, y0)) { set(51, y0, z, "minecraft:polished_blackstone"); set(51, y0 + 1, z, "minecraft:anvil[facing=east]"); }
        // mat de misaine avec nid de pie et cloche
        mast(64, 0, 106, 88);
        set(63, y0 + 2, 0, "minecraft:bell[attachment=single_wall,facing=west,powered=false]");
        // guindeau et chaines d'ancre
        for (int sz : new int[]{-1, 1}) {
            set(67, y0, sz * 2, "minecraft:polished_blackstone");
            set(67, y0 + 1, sz * 2, "minecraft:grindstone[face=floor,facing=east]");
            for (int x = 68; x <= 71; x++) if (inner(x, sz * 2, y0)) set(x, y0, sz * 2, chain('x'));
        }
        // bites d'amarrage, manches a air, projecteur
        for (int[] b : new int[][]{{44, -10}, {44, 10}, {58, -8}, {58, 8}, {70, -3}, {70, 3}}) bollard(b[0], b[1]);
        vent(49, -4, Dir.WEST);
        vent(61, 5, Dir.WEST);
        lampPost(46, y0, -4, 3, "minecraft:dark_oak_fence", "minecraft:polished_blackstone", false);
        lampPost(46, y0, 4, 3, "minecraft:dark_oak_fence", "minecraft:polished_blackstone", false);
        // hampe de beaupre et feu de mat a l'etrave
        for (int y = F2 + 1; y <= F2 + 8; y++) set(75, y, 0, "minecraft:dark_oak_fence");
        set(75, F2 + 9, 0, standing("minecraft:lantern"));
        set(66, y0, 0, "minecraft:barrel[facing=up,open=false]");
        set(62, y0, -4, "minecraft:chest[facing=south,type=single,waterlogged=false]");
    }

    private void afterdeck() {
        int y0 = F2 + 1;
        hut(-65, 0, -59, 6, "minecraft:spruce");
        doorX(-65, 3, y0, Dir.WEST, "spruce", false);
        doorX(-64, 0, y0, Dir.NORTH, "spruce", false);
        doorX(-63, 6, y0, Dir.SOUTH, "spruce", false);
        // mat d'artimon
        mast(-54, 0, 104, 0);
        // gouvernail de secours (roue) a la poupe
        set(-69, y0, 0, "minecraft:polished_blackstone_wall");
        for (int dz = -1; dz <= 1; dz++)
            for (int dy = 0; dy <= 2; dy++) {
                if (dz == 0 && dy == 1) { set(-69, y0 + 1 + dy, dz, "minecraft:dark_oak_planks"); continue; }
                set(-69, y0 + 1 + dy, dz, "minecraft:dark_oak_fence");
            }
        set(-68, y0 + 2, 0, "minecraft:dark_oak_button[face=wall,facing=east,powered=false]");
        // cabestans, bites, bancs, lampadaires
        for (int sz : new int[]{-1, 1}) {
            set(-70, y0, sz * 4, "minecraft:polished_blackstone");
            set(-70, y0 + 1, sz * 4, slab("minecraft:polished_blackstone_slab", "bottom"));
            bollard(-64, sz * 9);
            bench(-55, y0, sz * 11, sz < 0 ? Dir.SOUTH : Dir.NORTH, "spruce");
            lampPost(-52, y0, sz * 3, 3, "minecraft:dark_oak_fence", "minecraft:polished_blackstone", false);
        }
        vent(-57, -3, Dir.EAST);
        // mat de pavillon a la poupe
        for (int y = F2 + 1; y <= F2 + 8; y++) set(STERN + 1, y, 0, "minecraft:dark_oak_fence");
        set(STERN + 1, F2 + 9, 0, "minecraft:white_banner[rotation=4]");
        set(-62, y0, -6, "minecraft:barrel[facing=up,open=false]");
    }

    /** Petit rouf (descente) : murs pleins, toit en dalles avec debord. */
    private void hut(int x1, int z1, int x2, int z2, String wood) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                for (int y = F2 + 1; y <= F2 + 3; y++) {
                    if (!wall) continue;
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    set(x, y, z, corner ? "minecraft:stripped_spruce_log[axis=y]" : (y == F2 + 2 && (x + z) % 3 == 0 ? "minecraft:glass_pane" : WHITE.at(x, y, z)));
                }
                set(x, F2 + 4, z, wood + "_planks");
            }
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++)
                if (x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1) set(x, F2 + 4, z, slab(wood + "_slab", "top"));
        hangLamp((x1 + x2) / 2, F2 + 4, (z1 + z2) / 2, "minecraft:lantern");
    }

    private void bollard(int x, int z) {
        if (!inner(x, z, F2 + 1)) return;
        set(x, F2 + 1, z, "minecraft:polished_blackstone_wall");
        set(x, F2 + 2, z, slab("minecraft:polished_blackstone_slab", "bottom"));
    }

    /** Manche a air : fut blanc, pavillon en escalier tourne vers dir (sur le pont dont le plancher est yb). */
    private void vent(int x, int z, Dir dir) {
        ventAt(x, F2, z, dir);
    }

    private void ventAt(int x, int yb, int z, Dir dir) {
        set(x, yb + 1, z, "minecraft:white_concrete");
        set(x, yb + 2, z, "minecraft:white_concrete");
        set(x, yb + 3, z, "minecraft:red_concrete");
        set(x + dir.dx, yb + 3, z + dir.dz, stairs("minecraft:quartz_stairs", dir.opposite(), true));
    }

    private void mast(int x, int z, int top, int nest) {
        for (int y = F2 + 1; y <= top; y++) set(x, y, z, y < top - 8 ? "minecraft:stripped_spruce_log[axis=y]" : "minecraft:spruce_fence");
        set(x, top + 1, z, standing("minecraft:lantern"));
        // vergue
        for (int dz = -4; dz <= 4; dz++) if (dz != 0) set(x, top - 10, z + dz, "minecraft:spruce_fence");
        if (nest > 0) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    set(x + dx, nest, z + dz, "minecraft:spruce_planks");
                    // 1.9.1 : garde-corps en barrieres (les trappes d'angle ne s'appuyaient sur rien)
                    set(x + dx, nest + 1, z + dz, "minecraft:spruce_fence");
                }
            set(x, nest, z, "minecraft:spruce_planks");
        }
    }

    // ================================================================ pont des embarcations

    private void boatDeck() {
        int y0 = F3 + 1;
        // pont soleil : chaises longues et petites tables
        for (int z = -9; z <= 9; z += 3) {
            if (z == 0) continue;
            deckChair(-49, y0, z, Dir.EAST);
            deckChair(-46, y0, z, Dir.EAST);
        }
        // bancs face a la mer dans les intervalles des canots (coupent l'allee exterieure)
        for (int x : new int[]{19, -1, -21}) for (int sz : new int[]{-1, 1}) bench(x, y0, sz * 9, sz < 0 ? Dir.NORTH : Dir.SOUTH, "spruce");
        cafeTable(-44, y0, 0, "spruce", false);
        // manches a air et coffres a gilets le long des allees
        int[][] vents = {{-33, -8}, {-23, 8}, {-11, -8}, {1, 8}, {9, -8}, {19, 8}, {32, -8}};
        for (int[] v : vents) ventAt(v[0], F3, v[1], v[1] < 0 ? Dir.SOUTH : Dir.NORTH);
        int[][] lockers = {{-33, 8}, {-23, -8}, {-11, 8}, {1, -8}, {9, 8}, {19, -8}};
        for (int[] l : lockers) {
            setIfAir(l[0], y0, l[1], "minecraft:chest[facing=" + (l[1] < 0 ? "south" : "north") + ",type=single,waterlogged=false]");
            setIfAir(l[0] + 1, y0, l[1], "minecraft:spruce_planks");
            setIfAir(l[0] + 1, y0 + 1, l[1], "minecraft:white_carpet");
        }
        // lampadaires
        for (int x : new int[]{-45, -16, 6, 20}) for (int sz : new int[]{-1, 1}) if (isAir(x, y0, sz * 7)) lampPost(x, y0, sz * 7, 3, "minecraft:birch_fence", "minecraft:quartz_bricks", false);
        // feux de position sur les ailerons de passerelle
        // au-dessus de la main courante, qui reste en place (aucun bloc plein a sa place)
        set(41, y0 + 2, -13, "minecraft:red_stained_glass");
        set(41, y0 + 2, 13, "minecraft:lime_stained_glass");
    }

    /** Rouf du pont des embarcations : murs en 2 plans (poteaux en saillie), fenetres, toit en dalles avec debord. */
    private void deckhouse(int x1, int z1, int x2, int z2, Mat wall, boolean windows) {
        int y0 = F3 + 1;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                for (int y = y0; y < RF; y++) set(x, y, z, edge ? wall.at(x, y, z) : B.AIR);
                set(x, RF, z, edge ? "minecraft:white_concrete" : slab("minecraft:smooth_quartz_slab", "bottom"));
            }
        // fenetres (appui en escalier retourne dehors). 1.13.0 : la vitre est DANS le plan du mur ; posee un plan
        // plus a l'interieur, elle flottait dans la piece, decalee de 1 (Esteban, 17/11)
        if (windows)
            for (int x = x1 + 1; x < x2; x++)
                for (int z : new int[]{z1, z2}) {
                    if (Math.floorMod(x - x1, 3) == 0) continue;
                    Dir out = z == z1 ? Dir.NORTH : Dir.SOUTH;
                    set(x, y0, z, stairs("minecraft:smooth_quartz_stairs", out.opposite(), true));
                    set(x, y0 + 1, z, B.pane("minecraft:glass_pane", false, true, false, true));
                    set(x, y0 + 2, z, B.pane("minecraft:glass_pane", false, true, false, true));
                    set(x, y0 + 3, z, slab("minecraft:smooth_quartz_slab", "top"));
                }
        // poteaux en saillie aux angles, corniche (debord du toit)
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (!(x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1)) continue;
                set(x, RF, z, slab("minecraft:smooth_quartz_slab", "bottom"));
                boolean corner = (x == x1 - 1 || x == x2 + 1) && (z == z1 - 1 || z == z2 + 1);
                // 1.9.1 : pas de poteau sur la derniere marche d'un escalier exterieur (il fallait sauter)
                if (corner && !B.name(get(x, y0 - 1, z)).endsWith("_stairs"))
                    for (int y = y0; y < RF; y++) set(x, y, z, "minecraft:quartz_pillar[axis=y]");
            }
        hangLamp((x1 + x2) / 2, RF, (z1 + z2) / 2, "minecraft:lantern");
    }

    private void doorZ(int z, int x, Dir facing, String wood) {
        int y0 = F3 + 1;
        doorX(x, z, y0, facing, wood, false);
        // 1.9.1 : si la porte tombe sur une fenetre en retrait, la vitre interieure (a hauteur de tete) et
        // l'appui sont retires devant / derriere la porte, et l'imposte au-dessus de la porte est muree
        for (int dz : new int[]{-1, 1})
            for (int y = y0; y <= y0 + 1; y++)
                if (B.name(get(x, y, z + dz)).endsWith("glass_pane")) set(x, y, z + dz, B.AIR);
        if (isAir(x, y0 + 2, z)) set(x, y0 + 2, z, "minecraft:white_concrete");
    }

    private void bridge() {
        int y0 = F3 + 1;
        deckhouse(34, -6, 42, 6, WHITE, true);
        // grandes baies en facade avant
        for (int z = -5; z <= 5; z++) if (z % 3 != 0) { set(42, y0 + 1, z, "minecraft:glass_pane"); set(42, y0 + 2, z, "minecraft:glass_pane"); }
        doorZ(-6, 37, Dir.NORTH, "birch");
        doorZ(-6, 38, Dir.NORTH, "birch");
        doorZ(6, 37, Dir.SOUTH, "birch");
        doorZ(6, 38, Dir.SOUTH, "birch");
        doorX(34, 0, y0, Dir.WEST, "birch", false);
        doorX(34, 1, y0, Dir.WEST, "birch", true);
        // roue, habitacle, transmetteurs, table a cartes
        set(40, y0, 0, "minecraft:andesite_wall");
        for (int dz = -1; dz <= 1; dz++)
            for (int dy = 1; dy <= 2; dy++) if (!(dz == 0 && dy == 1)) set(40, y0 + dy, dz, "minecraft:dark_oak_fence");
        set(40, y0 + 1, 0, "minecraft:dark_oak_planks");
        set(41, y0, -3, "minecraft:polished_andesite");
        set(41, y0 + 1, -3, "minecraft:lever[face=floor,facing=east,powered=false]");
        set(41, y0, 3, "minecraft:polished_andesite");
        set(41, y0 + 1, 3, "minecraft:lever[face=floor,facing=east,powered=false]");
        set(39, y0, 0, "minecraft:cauldron");
        set(36, y0, -4, "minecraft:cartography_table");
        set(36, y0, 4, "minecraft:lectern[facing=west,has_book=false,powered=false]");
        set(35, y0, -4, "minecraft:chest[facing=east,type=single,waterlogged=false]");
        set(41, y0 + 2, 3, "minecraft:bell[attachment=single_wall,facing=west,powered=false]");
        // projecteur sur le toit
        set(38, RF + 1, 0, "minecraft:polished_blackstone");
        set(38, RF + 2, 0, "minecraft:sea_lantern");
        // volet du projecteur plaque contre la lanterne marine (1.9.1 : panneau du cote oppose a facing)
        set(39, RF + 2, 0, "minecraft:iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]");
    }

    private void domeHouse() {
        int y0 = F3 + 1;
        deckhouse(22, -6, 31, 6, WHITE, true);
        doorZ(-6, 24, Dir.NORTH, "dark_oak");
        doorZ(-6, 30, Dir.NORTH, "dark_oak");
        doorZ(6, 24, Dir.SOUTH, "dark_oak");
        doorZ(6, 30, Dir.SOUTH, "dark_oak");
        doorX(31, 0, y0, Dir.EAST, "dark_oak", false);
        doorX(31, 1, y0, Dir.EAST, "dark_oak", true);
        // verriere en coupole au-dessus du grand escalier (nervures en quartz)
        double cx = 26.5, cz = 0;
        for (int x = 22; x <= 31; x++)
            for (int z = -6; z <= 6; z++) set(x, RF, z, x == 22 || x == 31 || Math.abs(z) == 6 ? "minecraft:white_concrete" : "minecraft:glass");
        for (int x = 23; x <= 30; x++)
            for (int z = -5; z <= 5; z++) {
                double dx = (x - cx) / 4.6, dz = (z - cz) / 5.6;
                double r = dx * dx + dz * dz;
                if (r > 1) continue;
                int top = RF + (int) Math.round(4 * Math.sqrt(1 - r));
                for (int y = RF + 1; y <= top; y++) {
                    boolean surface = y == top || r > 0.55;
                    if (!surface) continue;
                    boolean rib = x == 26 || x == 27 || z == 0;
                    set(x, y, z, rib ? "minecraft:smooth_quartz" : "minecraft:white_stained_glass");
                }
            }
        // garde-corps de la tremie (deckhouse() a vide l'interieur)
        for (int sz : new int[]{-1, 1}) railLine(25, sz * 3, 27, sz * 3, F3 + 1, "minecraft:dark_oak_fence");
        railLine(24, -2, 24, 2, F3 + 1, "minecraft:dark_oak_fence");
        set(26, RF + 5, 0, "minecraft:gold_block");
        set(27, RF + 5, 0, "minecraft:gold_block");
        // lustre suspendu sous la coupole
        set(26, RF, 0, "minecraft:smooth_quartz");
        set(26, RF - 1, 0, chain('y'));
        set(26, RF - 2, 0, hanging("minecraft:lantern"));
        // 1.9.1 : bacs decales en x 28 (en x 29, leur trappe bouchait les portes x 30)
        bigPlant(28, y0, -5, "dark_oak");
        bigPlant(28, y0, 5, "dark_oak");
        pot(23, y0, -5);
        pot(23, y0, 5);
    }

    private void gym() {
        int y0 = F3 + 1;
        deckhouse(0, -5, 7, 5, WHITE, true);
        doorX(0, -1, y0, Dir.WEST, "birch", false);
        doorX(0, 0, y0, Dir.WEST, "birch", true);
        doorX(7, -1, y0, Dir.EAST, "birch", false);
        doorX(7, 0, y0, Dir.EAST, "birch", true);
        doorZ(5, 3, Dir.SOUTH, "birch");
        doorZ(-5, 4, Dir.NORTH, "birch");
        for (int x = 1; x <= 6; x++) for (int z = -4; z <= 4; z++) set(x, F3, z, (x + z) % 2 == 0 ? "minecraft:oak_planks" : "minecraft:birch_planks");
        // espaliers (echelles), sac de frappe, cheval mecanique, rameurs, halteres
        for (int y = y0; y < RF; y++) { set(6, y, -4, "minecraft:ladder[facing=west,waterlogged=false]"); }
        set(2, RF - 1, 3, chain('y'));
        set(2, RF - 2, 3, "minecraft:brown_wool");
        set(2, RF - 3, 3, "minecraft:brown_wool");
        set(4, y0, -3, "minecraft:polished_blackstone_wall");
        set(4, y0 + 1, -3, "minecraft:brown_terracotta");
        set(3, y0 + 1, -3, stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
        set(1, y0, -3, stairs("minecraft:spruce_stairs", Dir.EAST, false));
        set(1, y0, 2, B.trapdoor("minecraft:iron_trapdoor", Dir.EAST, false, false));
        set(5, y0, 3, "minecraft:anvil[facing=north]");
        set(6, y0, 2, "minecraft:heavy_core");
    }

    private void skylight() {
        int y0 = F3 + 1;
        for (int x = -21; x <= -12; x++)
            for (int z = -4; z <= 4; z++) {
                boolean edge = x == -21 || x == -12 || Math.abs(z) == 4;
                set(x, y0, z, edge ? "minecraft:white_concrete" : "minecraft:glass");
                int ridge = 2 - Math.abs(z) / 2;
                if (!edge && ridge > 0) {
                    set(x, y0 + 1, z, Math.abs(z) <= 1 ? "minecraft:white_stained_glass" : stairs("minecraft:quartz_stairs", z < 0 ? Dir.SOUTH : Dir.NORTH, false));
                }
            }
        for (int x = -20; x <= -13; x++) set(x, y0 + 2, 0, slab("minecraft:smooth_quartz_slab", "bottom"));
    }

    private void aftHouse() {
        int y0 = F3 + 1;
        deckhouse(-42, -5, -35, 5, WHITE, true);
        doorX(-35, 2, y0, Dir.EAST, "birch", false);
        doorX(-35, 3, y0, Dir.EAST, "birch", true);
        doorZ(5, -36, Dir.SOUTH, "birch");
        doorZ(-5, -40, Dir.NORTH, "birch");
        // garde-corps de la tremie de l'escalier arriere (rangs z -1..0, x -40..-37 ; 1.9.1)
        railLine(-41, -2, -37, -2, F3 + 1, "minecraft:birch_fence");
        railLine(-41, 1, -37, 1, F3 + 1, "minecraft:birch_fence");
        railLine(-41, -1, -41, 0, F3 + 1, "minecraft:birch_fence");
        // porte ouest decalee en z 2..3 (avant z -2..-1 : elle donnait sur la tremie)
        doorX(-42, 2, y0, Dir.WEST, "birch", false);
        doorX(-42, 3, y0, Dir.WEST, "birch", true);
        bigPlant(-38, y0, -4, "birch"); // 1.9.1 : plus devant la porte nord (x -40)
    }

    private void funnels() {
        int[][] f = {{14, 1}, {-6, 1}, {-27, 0}};
        for (int[] fu : f) {
            int fx = fu[0];
            boolean smoke = fu[1] == 1;
            for (int y = F3 + 1; y <= 100; y++) {
                double rake = (y - F3) / 9.0;
                double cx = fx - rake, rx = 4.2, rz = 3.2;
                for (int x = fx - 8; x <= fx + 6; x++)
                    for (int z = -4; z <= 4; z++) {
                        double d = Math.pow((x - cx) / rx, 2) + Math.pow(z / rz, 2);
                        if (d > 1) continue;
                        boolean surf = d > 0.5;
                        String b;
                        if (y >= 96) b = "minecraft:black_concrete";
                        else if (y == 95) b = "minecraft:white_concrete";
                        else b = FUNNEL.at(x, y, z);
                        if (y >= 99 && !surf) b = y == 99 && smoke && Math.abs(x - cx) < 1 && z == 0 ? "minecraft:campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]" : B.AIR;
                        if (y == 98 && !surf) b = "minecraft:black_concrete";
                        set(x, y, z, b);
                    }
            }
            // socle (encaissement) et sifflet
            for (int x = fx - 5; x <= fx + 5; x++)
                for (int z = -4; z <= 4; z++) {
                    double d = Math.pow((x - fx) / 5.0, 2) + Math.pow(z / 4.0, 2);
                    // 1.9.1 : d < 1 strict (le bout de l'ellipse bouchait la porte ouest du gymnase)
                    if (d < 1 && isAir(x, F3 + 1, z)) set(x, F3 + 1, z, "minecraft:white_concrete");
                }
            set(fx + 4, F3 + 2, 0, stairs("minecraft:smooth_quartz_stairs", Dir.WEST, true));
            for (int y = F3 + 2; y <= 92; y++) if (isAir(fx + 4 - (int) ((y - F3) / 9.0), y, 1)) set(fx + 4 - (int) ((y - F3) / 9.0), y, 1, "minecraft:andesite_wall");
        }
    }

    private void lifeboats() {
        int[] starts = {31, 21, 11, 1, -9, -19, -29, -40};
        for (int sx : starts)
            for (int sz : new int[]{-1, 1}) {
                int y0 = F3 + 1;
                int zc = sz * 11;
                // canot de 3 de haut (sur ses chantiers, coque, plat-bord, bache) : impossible d'y monter depuis le
                // pont, un banc ou un coffre (sinon on passerait par-dessus le garde-corps)
                for (int x = sx; x <= sx + 6; x++) {
                    boolean end = x == sx || x == sx + 6;
                    if (end) {
                        set(x, y0 + 1, zc, "minecraft:white_concrete");
                        set(x, y0 + 2, zc, "minecraft:white_concrete");
                        set(x, y0 + 3, zc, stairs("minecraft:quartz_stairs", x == sx ? Dir.EAST : Dir.WEST, false));
                        continue;
                    }
                    boolean chock = x == sx + 1 || x == sx + 3 || x == sx + 5;
                    set(x, y0, zc, chock ? "minecraft:spruce_planks" : B.AIR);
                    for (int z = zc - 1; z <= zc + 1; z++) {
                        set(x, y0 + 1, z, z == zc ? "minecraft:spruce_planks" : "minecraft:white_concrete");
                        set(x, y0 + 2, z, z == zc ? "minecraft:spruce_planks" : "minecraft:birch_planks");
                        set(x, y0 + 3, z, "minecraft:white_carpet");
                    }
                }
                // bossoirs : fut sur le bordé, bras au-dessus du canot, croc
                int w = hw(sx, F3);
                for (int bx : new int[]{sx + 1, sx + 5}) {
                    int zs = sz * w;
                    for (int y = y0; y <= y0 + 3; y++) set(bx, y, zs, y == y0 ? "minecraft:white_concrete" : "minecraft:diorite_wall");
                    set(bx, y0 + 4, zs, stairs("minecraft:smooth_quartz_stairs", sz < 0 ? Dir.SOUTH : Dir.NORTH, true));
                    set(bx, y0 + 4, zs - sz, slab("minecraft:smooth_quartz_slab", "bottom"));
                    set(bx, y0 + 4, zc, slab("minecraft:smooth_quartz_slab", "bottom"));
                }
            }
        // caissons a brassieres contre le pavois, dans les intervalles des canots (pas sous les canots)
        int y0 = F3 + 1;
        for (int x = DX1 + 2; x <= DX2 - 3; x += 4)
            for (int sz : new int[]{-1, 1}) {
                int z = sz * (hw(x, F3) - 1);
                if (isAir(x, y0, z) && isAir(x, y0 + 1, z)) set(x, y0, z, "minecraft:barrel[facing=" + (sz < 0 ? "south" : "north") + ",open=false]");
            }
    }

    private void masts() {
        // antenne de TSF tendue entre les deux mats, au-dessus des cheminees
        for (int x = -53; x <= 63; x++) set(x, 103, 0, chain('x'));
    }

    private void iceberg() {
        int cx = 70, cz = -62;
        for (int x = cx - 14; x <= cx + 14; x++)
            for (int z = cz - 14; z <= cz + 14; z++) {
                double d = Math.hypot(x - cx, (z - cz) * 1.2) / 12.0;
                double n = noise.noise2(x / 6.0, z / 6.0) * 0.25;
                double hgt = (1 - d + n) * 22;
                if (hgt <= 0) continue;
                int top = SEA + (int) Math.round(hgt);
                for (int y = 52; y <= top; y++) set(x, y, z, y >= top - 1 ? "minecraft:snow_block" : ICE.at(x, y, z));
            }
        // pic secondaire et glacons flottants
        for (int[] p : new int[][]{{58, -48, 3}, {82, -52, 2}, {62, -75, 2}, {48, -66, 1}}) {
            for (int dx = -p[2]; dx <= p[2]; dx++)
                for (int dz = -p[2]; dz <= p[2]; dz++)
                    if (dx * dx + dz * dz <= p[2] * p[2]) {
                        set(p[0] + dx, SEA, p[1] + dz, "minecraft:packed_ice");
                        if (dx * dx + dz * dz < p[2] * p[2] / 2 + 1) set(p[0] + dx, SEA + 1, p[1] + dz, "minecraft:snow_block");
                    }
        }
    }
}
