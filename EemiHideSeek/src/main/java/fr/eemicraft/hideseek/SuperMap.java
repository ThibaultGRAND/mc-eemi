package fr.eemicraft.hideseek;

import fr.eemicraft.hideseek.build.B;
import fr.eemicraft.hideseek.build.Connect;
import fr.eemicraft.hideseek.build.Dir;
import fr.eemicraft.hideseek.build.Palette;
import fr.eemicraft.hideseek.build.Sink;
import fr.eemicraft.hideseek.build.Stairs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * « Supermarche » : grande surface de 90 x 90 (local 0..89), du nord au sud : reserve avec bureaux en mezzanine
 * (vue sur la surface de vente), surface de vente (frais au mur du fond, epicerie en gondoles decalees, boissons a
 * l'ouest, bazar et fruits et legumes a l'est), ligne de caisses et accueil, hall d'entree, portes coulissantes,
 * parking couvert.
 *
 * <p>Le decor est fait des blocs de deguisement : les produits des rayons, les palettes, l'electromenager, les
 * cageots de fruits sont des blocs que les cacheurs peuvent copier. Les dessus de gondoles (on y monte par les
 * piles promotionnelles des tetes de gondole) sont garnis de produits avec des trous : ce sont les meilleures
 * cachettes. Sol (bloc) y 64, on se tient en y 65, plafond de la surface y 76, du parking y 70.
 */
public final class SuperMap extends HsMap {
    static final int X0 = 755, Z0 = -45;
    static final int FY = 64, Y = 65, CY = 76, PCY = 70, MEZ = 70;

    // zones (local)
    static final int RES_Z1 = 2, RES_Z2 = 14;       // reserve
    static final int BACK_Z1 = 15, BACK_Z2 = 16;    // mur du fond de la surface
    static final int SALE_Z1 = 17, SALE_Z2 = 58;    // surface de vente
    static final int CHK_Z1 = 59, CHK_Z2 = 66;      // caisses
    static final int HALL_Z1 = 67, HALL_Z2 = 72;    // hall d'entree
    static final int FRONT_Z1 = 73, FRONT_Z2 = 74;  // facade vitree
    static final int PARK_Z1 = 75, PARK_Z2 = 87;    // parking couvert
    static final int MEZ_X2 = 40;                    // mezzanine : x 2..40 au-dessus de la reserve

    static final int WX = 800, WY = 100, WZ = 0;
    static final int SX = 836, SZ = 0;

    private Palette tileP, ceilP, asphaltP, wallP, leavesP;

    public SuperMap() {
        super("supermarche", "Supermarche", 1);
    }

    @Override
    public int[] box() {
        return new int[]{X0 - 3, 58, Z0 - 3, X0 + 92, 112, Z0 + 92};
    }

    @Override
    public int[] playBox() {
        return new int[]{X0, FY - 1, Z0, X0 + 89, CY + 1, Z0 + 89};
    }

    @Override
    public int measureFloorY() { return FY; }

    @Override
    public int measureCeilY() { return CY; }

    @Override
    public double[] point(String key) {
        switch (key) {
            case "waiting-spawn": return new double[]{WX + 0.5, WY + 1, WZ + 4.5, 180, 0};
            case "seeker-room": return new double[]{SX + 0.5, WY + 1, SZ + 0.5, 0, 0};
            // cacheurs : hall d'entree ; chercheurs : parking (ils arrivent en voiture)
            case "hider-spawn": return new double[]{X0 + 30.5, Y, Z0 + 69.5, 180, 0};
            case "seeker-spawn": return new double[]{X0 + 42.5, Y, Z0 + 80.5, 180, 0};
            case "spectate": return new double[]{X0 + 44.5, Y + 9, Z0 + 50.5, 180, 30};
            default: return null;
        }
    }

    @Override
    public LinkedHashMap<String, String> disguises() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("BARREL", "Carton");
        m.put("DRIED_KELP_BLOCK", "Paquet");
        m.put("WHITE_CONCRETE", "Produit blanc");
        m.put("LIGHT_GRAY_CONCRETE", "Produit gris");
        m.put("BLACK_CONCRETE", "Produit noir");
        m.put("YELLOW_CONCRETE", "Produit jaune");
        m.put("RED_WOOL", "Laine rouge");
        m.put("WHITE_WOOL", "Laine blanche");
        m.put("ORANGE_WOOL", "Laine orange");
        m.put("BLUE_WOOL", "Laine bleue");
        m.put("GREEN_WOOL", "Laine verte");
        m.put("IRON_BLOCK", "Bloc de fer");
        m.put("COAL_BLOCK", "Bloc de charbon");
        m.put("MELON", "Pastèque");
        m.put("PUMPKIN", "Citrouille");
        m.put("JACK_O_LANTERN", "Citrouille-lanterne");
        m.put("HAY_BLOCK", "Botte de foin");
        m.put("OAK_LOG", "Bûche de chêne");
        m.put("SPRUCE_PLANKS", "Palette (sapin)");
        m.put("BIRCH_PLANKS", "Bois clair");
        m.put("BOOKSHELF", "Bibliothèque");
        m.put("COMPOSTER", "Composteur");
        m.put("CAULDRON", "Chaudron");
        m.put("FURNACE", "Four");
        m.put("SMOKER", "Fumoir");
        m.put("BLAST_FURNACE", "Haut fourneau");
        m.put("JUKEBOX", "Juke-box");
        m.put("NOTE_BLOCK", "Bloc musical");
        m.put("CRAFTING_TABLE", "Table de craft");
        m.put("GRINDSTONE", "Meule");
        m.put("ANVIL", "Enclume");
        m.put("BEEHIVE", "Ruche");
        m.put("LANTERN", "Lanterne");
        m.put("POTTED_FERN", "Pot de fougère");
        m.put("POTTED_RED_TULIP", "Pot de fleur");
        m.put("OAK_LEAVES", "Feuilles de chêne");
        m.put("AZALEA_LEAVES", "Feuilles d'azalée");
        m.put("FLOWERING_AZALEA_LEAVES", "Azalée en fleurs");
        return m;
    }

    // ------------------------------------------------------------------ construction

    @Override
    protected void draw() {
        Sink abs = s;
        tileP = Palette.of(21, "smooth_quartz", 80, "calcite", 20).period(7);
        ceilP = Palette.of(22, "gray_concrete", 75, "light_gray_terracotta", 25).period(8);
        asphaltP = Palette.of(23, "gray_concrete_powder", 80, "andesite", 20).period(7);
        wallP = Palette.of(24, "smooth_stone", 80, "calcite", 20).period(9).amp(0.2);
        leavesP = Palette.of(25, "oak_leaves", 55, "azalea_leaves", 30, "flowering_azalea_leaves", 15).period(3);

        List<String> show = new ArrayList<>();
        for (String k : disguises().keySet()) show.add(k.toLowerCase());
        waitingRoom(WX, WY, WZ, 8, "white_concrete", "smooth_quartz", "stripped_spruce_log", "smooth_stone",
                "white_concrete", show);
        // enseigne de magasin (bandeau rouge et jaune) au-dessus des vitrines du fond
        for (int x = WX - 6; x <= WX + 6; x++) set(x, WY + 6, WZ - 8, (x % 2 == 0) ? "red_wool" : "yellow_concrete");
        seekerRoom(SX, WY, SZ);

        s = abs.offset(X0, 0, Z0);
        shell();
        reserve();
        mezzanine();
        salesFloor();
        fresh();
        grocery();
        drinks();
        bazaar();
        produce();
        promo();
        textile();
        details();
        checkout();
        hall();
        facade();
        parking();
        Stairs.fix(s, 0, FY, 0, 89, CY, 89);
        Connect.fix(s, 0, FY, 0, 89, CY, 89);
        s = abs;
    }

    /** Enveloppe : murs de 2 en 3 plans (poteaux saillants vers l'interieur), sols, plafonds, toit plein. */
    private void shell() {
        fill(0, FY - 1, 0, 89, FY - 1, 89, "gray_concrete");
        for (int x = 0; x <= 89; x++)
            for (int z = 0; z <= 89; z++) {
                boolean outer = x <= 1 || x >= 88 || z <= 1 || z >= 88;
                set(x, FY, z, "gray_concrete");
                for (int y = Y; y <= CY + 1; y++) set(x, y, z, outer || y >= CY ? wallP.clustered(x, y, z) : B.AIR);
            }
        // poteaux saillants (1 bloc) tous les 8 contre les murs ouest et est, et sur le mur nord
        for (int z = 4; z <= 86; z += 8) {
            if (z >= PARK_Z1) continue;
            fill(2, Y, z, 2, CY - 1, z, "quartz_pillar[axis=y]");
            fill(87, Y, z, 87, CY - 1, z, "quartz_pillar[axis=y]");
        }
        for (int x = 4; x <= 86; x += 8) fill(x, Y, 2, x, CY - 1, 2, "quartz_pillar[axis=y]");
    }

    private boolean inPark(int z) {
        return z >= PARK_Z1 && z <= PARK_Z2;
    }

    // ------------------------------------------------------------------ reserve et mezzanine

    private void reserve() {
        // sol beton, mur du fond (z 15..16) avec 3 passages a lanieres
        for (int x = 2; x <= 87; x++)
            for (int z = RES_Z1; z <= RES_Z2; z++) set(x, FY, z, asphaltP.clustered(x, FY, z));
        for (int x = 2; x <= 87; x++)
            for (int z = BACK_Z1; z <= BACK_Z2; z++)
                for (int y = Y; y < CY; y++) set(x, y, z, y == Y ? "polished_blackstone" : wallP.clustered(x, y, z));
        for (int[] o : new int[][]{{20, 23}, {48, 51}, {70, 73}}) {
            air(o[0], Y, BACK_Z1, o[1], Y + 3, BACK_Z2);
            fill(o[0] - 1, Y, BACK_Z2, o[0] - 1, Y + 4, BACK_Z2, "stripped_spruce_log[axis=y]");
            fill(o[1] + 1, Y, BACK_Z2, o[1] + 1, Y + 4, BACK_Z2, "stripped_spruce_log[axis=y]");
            fill(o[0], Y + 4, BACK_Z2, o[1], Y + 4, BACK_Z2, "stripped_spruce_log[axis=x]");
            // panneau « reserve » rouge au-dessus
            fill(o[0], Y + 5, BACK_Z2 + 1, o[1], Y + 5, BACK_Z2 + 1, "red_terracotta");
            for (int x = o[0]; x <= o[1]; x++) set(x, FY, BACK_Z1, "yellow_terracotta");
        }
        // fond des racks contre le mur nord : plein (pas de couloir ferme derriere)
        for (int x = 42; x <= 87; x++) for (int y = Y; y < CY; y++) set(x, y, 2, wallP.clustered(x, y, 2));
        for (int z = 3; z <= 4; z++) for (int y = Y; y < CY; y++) set(87, y, z, wallP.clustered(87, y, z));
        // racks a palettes le long du mur nord (partie haute, x 42..86), 3 niveaux de lisses
        for (int x = 42; x <= 86; x++) {
            boolean upright = (x - 42) % 5 == 0;
            for (int z = 3; z <= 4; z++) {
                if (upright) { for (int y = Y; y <= Y + 9; y++) set(x, y, z, "blue_terracotta"); continue; }
                for (int lv = 0; lv < 3; lv++) {
                    int by = Y + 3 * lv;
                    if (lv > 0) set(x, by, z, slab("smooth_stone_slab", false));
                    double r = hash(31, x, lv, z);
                    String box = r < 0.4 ? "barrel[facing=up,open=false]" : r < 0.6 ? "dried_kelp_block"
                            : r < 0.72 ? "hay_block[axis=y]" : r < 0.82 ? "white_concrete" : null;
                    if (lv == 0 && box == null) box = "barrel[facing=up,open=false]";
                    if (box != null && (lv == 0 || hash(32, x / 2, lv, 0) < 0.8)) {
                        set(x, by, z, lv == 0 ? slab("spruce_slab", false) : slab("smooth_stone_slab", false));
                        set(x, by + 1, z, box);
                        if (hash(33, x, lv, z) < 0.6) set(x, by + 2, z, box);
                    }
                }
            }
       }
        // palettes au sol en rangees decalees (cachettes entre les piles)
        for (int x = 49; x <= 59; x += 5) {
            for (int z = 8; z <= 11; z += 3) {
                int ox = (z == 11) ? 2 : 0;
                pallet(x + ox, z, (int) (1 + hash(34, x, 0, z) * 3), x + z);
            }
        }
        // chambre froide (beton blanc) avec carcasses et cartons
        for (int x = 74; x <= 84; x++)
            for (int z = 7; z <= 13; z++)
                for (int y = Y; y <= Y + 4; y++) {
                    boolean shellC = x == 74 || x == 84 || z == 7 || z == 13 || y == Y + 4;
                    set(x, y, z, shellC ? "white_concrete" : B.AIR);
                }
        air(74, Y, 9, 74, Y + 2, 10);                                     // porte de la chambre froide
        for (int x = 76; x <= 82; x += 2) { set(x, Y + 3, 9, "iron_chain[axis=y,waterlogged=false]"); set(x, Y + 2, 9, "red_wool"); set(x, Y + 1, 9, "red_wool"); }
        for (int x = 75; x <= 83; x++) { set(x, Y, 12, "barrel[facing=up,open=false]"); if (x % 2 == 0) set(x, Y + 1, 12, "white_concrete"); }
        // chariot elevateur (jaune et noir)
        forklift(66, 11);
        // compacteur a cartons
        for (int x = 86; x <= 87; x++) for (int z = 10; z <= 13; z++) for (int y = Y; y <= Y + 3; y++) set(x, y, z, "iron_block");
        // sous la mezzanine (x 2..40) : etageres basses, poubelles, piliers
        for (int x = 8; x <= 40; x += 8) for (int z : new int[]{5, 11}) fill(x, Y, z, x, MEZ - 2, z, "quartz_pillar[axis=y]");
        for (int x = 2; x <= 30; x++) {
            if (x % 8 == 0) continue;
            set(x, Y, 3, "spruce_planks");
            set(x, Y + 1, 3, slab("smooth_stone_slab", false));
            double r = hash(35, x, 0, 3);
            set(x, Y + 2, 3, r < 0.3 ? "barrel[facing=up,open=false]" : r < 0.5 ? "white_wool" : r < 0.7 ? "dried_kelp_block" : B.AIR);
        }
        for (int z = 6; z <= 10; z++) { set(3, Y, z, "cauldron"); }
        fill(2, Y, 2, 30, MEZ - 2, 2, "smooth_stone");
        pallet(12, 8, 2, 7);
        pallet(18, 9, 1, 8);
        pallet(26, 7, 3, 9);
        pallet(32, 9, 2, 10);
        // eclairage : lampes suspendues
        for (int x = 46; x <= 84; x += 8) for (int z : new int[]{6, 12}) { set(x, CY - 1, z, "iron_chain[axis=y,waterlogged=false]"); set(x, CY - 2, z, hangingLantern()); }
        for (int x = 6; x <= 38; x += 8) set(x, MEZ - 1, 8, hangingLantern());
    }

    /** Palette de sapin chargee : dalle de palette puis piles (cartons, paquets, bottes...). */
    private void pallet(int x, int z, int h, long seed) {
        String[] goods = {"barrel[facing=up,open=false]", "dried_kelp_block", "hay_block[axis=y]", "white_wool", "melon", "blue_wool"};
        String g = goods[(int) (hash(seed, x, 1, z) * goods.length)];
        for (int dx = 0; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++) {
                set(x + dx, Y, z + dz, slab("spruce_slab", false));
                int hh = h - ((dx + dz == 2 && h > 1) ? 1 : 0);
                for (int y = Y + 1; y <= Y + hh && y < CY - 1; y++) set(x + dx, y, z + dz, g);
            }
    }

    private void forklift(int x, int z) {
        // chassis 3x2, siege, mat, fourches
        for (int dx = 0; dx <= 2; dx++) for (int dz = 0; dz <= 1; dz++) { set(x + dx, Y, z + dz, "yellow_concrete"); }
        set(x, Y + 1, z, "coal_block"); set(x, Y + 1, z + 1, "coal_block");
        set(x + 1, Y + 1, z, "yellow_concrete"); set(x + 1, Y + 1, z + 1, "coal_block");
        for (int y = Y + 2; y <= Y + 3; y++) for (int dx : new int[]{0, 2}) for (int dz : new int[]{0, 1}) set(x + dx, y, z + dz, "iron_chain[axis=y,waterlogged=false]");
        for (int dx = 0; dx <= 2; dx++) for (int dz = 0; dz <= 1; dz++) set(x + dx, Y + 4, z + dz, slab("smooth_stone_slab", false));
        for (int y = Y; y <= Y + 4; y++) set(x + 3, y, z, "coal_block");
        set(x + 4, Y, z, slab("smooth_stone_slab", false));
        set(x + 4, Y, z + 1, slab("smooth_stone_slab", false));
    }

    /** Bureaux en mezzanine au-dessus de l'ouest de la reserve, vitres sur la surface de vente, escalier. */
    private void mezzanine() {
        for (int x = 2; x <= MEZ_X2; x++)
            for (int z = RES_Z1; z <= RES_Z2; z++) {
                set(x, MEZ - 1, z, "smooth_stone");
                set(x, MEZ, z, ((x / 3 + z / 3) % 2 == 0) ? "spruce_planks" : "birch_planks");
            }
        // garde-corps cote est (x 40) sauf l'arrivee de l'escalier
        for (int z = RES_Z1; z <= RES_Z2; z++) if (z < 11 || z > 13) set(MEZ_X2, MEZ + 1, z, "iron_bars");
        // escalier (2 de large) de x 46 (bas) a x 41 (haut), z 11..12, montee vers l'ouest
        for (int i = 0; i < 6; i++) {
            int x = 46 - i, y = Y + i;
            for (int z = 11; z <= 12; z++) {
                set(x, y, z, stairs("spruce_stairs", Dir.WEST, false));
                for (int yy = Y; yy < y; yy++) set(x, yy, z, "spruce_planks");
                for (int yy = y + 1; yy <= y + 3; yy++) set(x, yy, z, B.AIR);
            }
        }
        for (int z = 11; z <= 12; z++) { set(40, MEZ, z, "spruce_planks"); air(40, MEZ + 1, z, 40, MEZ + 3, z); }
        for (int i = 0; i < 6; i++) set(46 - i, Y + i + 1, 13, "iron_bars");
        // facade vitree sur la surface de vente (mur z 15..16, y 71..75)
        for (int x = 2; x <= MEZ_X2; x++) {
            boolean post = (x - 2) % 5 == 0;
            for (int y = MEZ + 1; y < CY; y++) {
                set(x, y, BACK_Z1, post ? "quartz_pillar[axis=y]" : "glass_pane");
                set(x, y, BACK_Z2, post ? "quartz_pillar[axis=y]" : B.AIR);
            }
            set(x, MEZ + 1, BACK_Z2, post ? "quartz_pillar[axis=y]" : stairs("polished_andesite_stairs", Dir.NORTH, true));
        }
        // cloison entre bureau du directeur (ouest) et salle de pause (est) a x 21, porte
        for (int z = RES_Z1; z <= RES_Z2; z++) for (int y = MEZ + 1; y < CY; y++) set(21, y, z, (z % 4 == 0) ? "stripped_birch_log[axis=y]" : (y >= MEZ + 3 ? "glass_pane" : "birch_planks"));
        air(21, MEZ + 1, 7, 21, MEZ + 3, 9);
        // bureau : table, ecrans, chaises, armoires, plante
        for (int x = 6; x <= 12; x++) for (int z = 6; z <= 7; z++) set(x, MEZ + 1, z, slab("birch_slab", true));
       set(8, MEZ + 2, 6, "coal_block"); set(9, MEZ + 2, 6, "coal_block");
        set(11, MEZ + 2, 7, lantern(false));
        set(9, MEZ + 1, 9, "black_concrete"); set(9, MEZ + 1, 10, "black_concrete"); set(9, MEZ + 2, 10, "black_concrete");
        for (int z = 3; z <= 6; z++) { set(3, MEZ + 1, z, "iron_block"); set(3, MEZ + 2, z, "iron_block"); }
        set(3, MEZ + 3, 4, "potted_fern");
        set(18, MEZ + 1, 3, "bookshelf"); set(19, MEZ + 1, 3, "bookshelf"); set(18, MEZ + 2, 3, "bookshelf");
        set(15, MEZ + 1, 12, "composter[level=0]");
        bush(16, MEZ + 1, 4);
        // salle de pause : table, chaises, frigo, machine a cafe, casiers
        for (int x = 27; x <= 32; x++) for (int z = 7; z <= 9; z++) set(x, MEZ + 1, z, slab("spruce_slab", true));
        for (int x = 27; x <= 32; x += 2) { set(x, MEZ + 1, 6, "white_concrete"); set(x, MEZ + 1, 10, "white_concrete"); }
        for (int y = MEZ + 1; y <= MEZ + 4; y++) { set(23, y, 3, "white_concrete"); set(24, y, 3, "white_concrete"); }
        set(26, MEZ + 1, 3, "black_concrete"); set(26, MEZ + 2, 3, "black_concrete");
        for (int x = 30; x <= 38; x++) for (int y = MEZ + 1; y <= MEZ + 3; y++) set(x, y, 3, ((x + y) % 3 == 0) ? "light_gray_concrete" : "iron_block");
        set(38, MEZ + 1, 12, "cauldron");
        // plafond lumineux de la mezzanine
        for (int x = 5; x <= 38; x += 6) set(x, CY, 8, "sea_lantern");
    }

    private void bush(int cx, int y0, int cz) {
        set(cx, y0, cz, "spruce_planks");
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int z = cz - 1; z <= cz + 1; z++)
                for (int y = y0 + 1; y <= y0 + 2; y++)
                    if ((Math.abs(x - cx) + Math.abs(z - cz) + (y - y0 - 1)) <= 2 && air(x, y, z)) set(x, y, z, B.leaves(leavesP.clustered(x, y, z)));
    }

    // ------------------------------------------------------------------ surface de vente

    private void salesFloor() {
        for (int x = 2; x <= 87; x++)
            for (int z = SALE_Z1; z <= HALL_Z2; z++) {
                boolean grout = x % 4 == 0 || z % 4 == 0;
                set(x, FY, z, grout ? "polished_diorite" : tileP.clustered(x, FY, z));
            }
        // plafond : bac acier gris, poutres, rampes lumineuses
        for (int x = 2; x <= 87; x++)
            for (int z = SALE_Z1; z <= HALL_Z2; z++) {
                String st = ceilP.clustered(x, CY, z);
                if (x % 10 == 5) st = "polished_andesite";                       // poutres
                else if (z % 6 == 3 && x % 10 != 0) st = "sea_lantern";          // rampes
                set(x, CY, z, st);
            }
        // grandes enseignes suspendues par zone (panneaux de couleur sur chaines)
        sign(13, 30, "blue_terracotta");       // boissons
        sign(37, 22, "red_terracotta");        // epicerie
        sign(73, 30, "purple_terracotta");     // bazar
        sign(73, 50, "lime_terracotta");       // fruits et legumes
        sign(37, 62, "yellow_terracotta");     // caisses
    }

    private void sign(int cx, int cz, String col) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            set(x, CY - 3, cz, col);
            set(x, CY - 4, cz, (x == cx - 3 || x == cx + 3) ? col : "white_terracotta");
        }
        set(cx - 2, CY - 1, cz, "iron_chain[axis=y,waterlogged=false]");
        set(cx - 2, CY - 2, cz, "iron_chain[axis=y,waterlogged=false]");
        set(cx + 2, CY - 1, cz, "iron_chain[axis=y,waterlogged=false]");
        set(cx + 2, CY - 2, cz, "iron_chain[axis=y,waterlogged=false]");
    }

    /**
     * Produits d'un rayon par « facing » de 4 blocs, meme produit sur toute la hauteur. Les listes alternent les
     * familles (laine / beton / autre) : deux facings voisins ne sont jamais deux couleurs de la meme famille.
     */
    private String product(String[] cat, long seed, int x, int y, int z, int z1) {
        int seg = (z - z1) / 4;
        if (hash(seed + 1, x, y, seg) < 0.06) return B.AIR;                 // case vide
        int base = (int) (hash(seed, 0, 0, 0) * cat.length);
        return cat[Math.floorMod(base + seg, cat.length)];
    }

    static final String[] SWEET = {"red_wool", "yellow_concrete", "orange_wool", "dried_kelp_block", "blue_wool", "white_concrete"};
    static final String[] SALTY = {"dried_kelp_block", "green_wool", "barrel[facing=up,open=false]", "yellow_concrete", "white_wool", "light_gray_concrete"};
    static final String[] CANS = {"iron_block", "red_wool", "light_gray_concrete", "green_wool", "white_concrete", "orange_wool"};
    static final String[] HYGIENE = {"white_concrete", "blue_wool", "light_gray_concrete", "white_wool", "dried_kelp_block", "orange_wool"};
    static final String[] DRINK = {"barrel[facing=east,open=false]", "green_wool", "white_concrete", "blue_wool", "dried_kelp_block", "orange_wool"};
    static final String[] PROMO = {"barrel[facing=up,open=false]", "dried_kelp_block", "yellow_concrete", "melon", "orange_wool", "red_wool"};

    /**
     * Gondole double face de 2 de profondeur (x, x+1) de z1 a z2 : socle gris, 3 niveaux de produits, produits
     * sur le dessus avec des trous (cachettes) ; tetes de gondole : piles en escalier (1, 2, 3) pour y monter.
     */
    private void gondola(int x, int z1, int z2, String[] cat, long seed, boolean capNorth, boolean capSouth) {
        for (int z = z1; z <= z2; z++)
            for (int dx = 0; dx <= 1; dx++) {
                int xx = x + dx;
                // socle en retrait (plinthe) : escalier retourne, la marche vers le coeur de la gondole
                set(xx, Y, z, stairs("polished_andesite_stairs", dx == 0 ? Dir.EAST : Dir.WEST, true));
                for (int y = Y + 1; y <= Y + 2; y++) {
                    String p = product(cat, seed + dx * 7919, xx, y, z + dx * 4, z1);
                    set(xx, y, z, p);
                }
                // dessus : produits en stock avec trous
                if (z >= z1 + 2 && hash(seed + 3, xx, 0, z / 2) < 0.55) set(xx, Y + 3, z, product(cat, seed + 11, xx, Y + 3, z + 2, z1));
            }
        if (capNorth) endCap(x, z1 - 1, -1, seed);
        if (capSouth) endCap(x, z2 + 1, 1, seed + 5);
    }

    private void endCap(int x, int z, int dir, long seed) {
        String g = PROMO[(int) (hash(seed, x, 9, z) * PROMO.length)];
        for (int dx = 0; dx <= 1; dx++) {
            for (int y = Y; y <= Y + 1; y++) set(x + dx, y, z, g);
            set(x + dx, Y, z + dir, g);
        }
    }

    /** Frais : meubles froids muraux le long du mur du fond (z 17..18), portes vitrees ou ouverts. */
    private void fresh() {
        String[] cold = {"white_wool", "white_concrete", "red_wool", "yellow_concrete", "orange_wool", "light_gray_concrete"};
        for (int x = 2; x <= 87; x++) {
            if (x >= 19 && x <= 24 || x >= 47 && x <= 52 || x >= 69 && x <= 74) continue;   // passages vers la reserve
            boolean glassDoor = (x / 12) % 2 == 0;
            set(x, Y, 17, "iron_block");
            set(x, Y, 18, "iron_block");
            for (int y = Y + 1; y <= Y + 3; y++) {
                set(x, y, 17, product(cold, 41, 0, y, x, 0));
                if (B.isAir(get(x, y, 17))) set(x, y, 17, "iron_block");
                set(x, y, 18, glassDoor ? ((x % 3 == 0) ? "iron_bars" : "glass_pane") : B.AIR);
                if (!glassDoor && y == Y + 2) set(x, y, 18, slab("smooth_quartz_slab", false));
            }
            set(x, Y + 4, 17, "smooth_quartz");
            set(x, Y + 4, 18, x % 4 == 0 ? "sea_lantern" : "smooth_quartz");
            set(x, Y + 5, 17, "light_blue_terracotta");
        }
        // ilots surgeles (bacs bas) entre epicerie et fruits et legumes
        for (int z = 21; z <= 41; z += 10)
            for (int x = 58; x <= 60; x++)
                for (int zz = z; zz <= z + 6; zz++) {
                    set(x, Y, zz, "white_concrete");
                    set(x, Y + 1, zz, (x == 59 && zz > z && zz < z + 6) ? "light_blue_stained_glass" : "white_concrete");
                }
    }

    /** Epicerie : 6 gondoles en deux troncons decales (allee transversale qui change de place). */
    private void grocery() {
        String[][] cats = {SWEET, SALTY, CANS, HYGIENE, SWEET, SALTY};
        int[] xs = {19, 25, 31, 37, 43, 49};
        for (int i = 0; i < xs.length; i++) {
            int shift = (i % 2 == 0) ? 0 : 3;                 // troncons decales : pas de ligne de vue continue
            gondola(xs[i], 24 + shift, 34 + shift, cats[i], 100 + i * 13, true, false);
            gondola(xs[i], 40 + shift, 49 + shift, cats[(i + 2) % cats.length], 200 + i * 17, true, false);
        }
        // presentoir de bonbons, panier de promotions au milieu de l'allee centrale
        for (int z = 36; z <= 37; z++) for (int x = 28; x <= 29; x++) { set(x, Y, z, "barrel[facing=up,open=false]"); set(x, Y + 1, z, x == 29 ? "red_wool" : "yellow_concrete"); }
        // pots et plantes de fin de rayon
        set(55, Y, 23, "potted_red_tulip");
        set(17, Y, 57, "cauldron");
    }

    /** Boissons : rayonnage mural (casiers a vin, bouteilles) et palettes de packs d'eau. */
    private void drinks() {
        for (int z = 19; z <= 57; z++) {
            if (z % 8 == 4) continue;                          // poteaux du mur
            for (int y = Y; y <= Y + 4; y++) {
                String st = y == Y ? "spruce_planks" : product(DRINK, 51, 2, y, z, 19);
                set(2, y, z, B.isAir(st) ? "spruce_planks" : st);
           }
       }
        // gondoles de boissons et palettes de packs (eau, jus, bieres) en quinconce
        gondola(7, 23, 35, DRINK, 53, true, false);
        gondola(7, 41, 53, DRINK, 54, true, false);
        for (int z = 21; z <= 55; z += 5) pallet(12, z + 2, 1 + (int) (hash(52, 12, 0, z) * 3), 72 + z);
        // montants en bois devant le rayonnage (tous les 4)
        for (int z = 20; z <= 56; z += 4) if (z % 8 != 4) for (int y = Y; y <= Y + 4; y++) set(3, y, z, "spruce_fence");
        // tonneaux de cidre
        for (int z = 46; z <= 50; z += 2) { set(14, Y, z, "barrel[facing=east,open=false]"); set(14, Y + 1, z, "barrel[facing=east,open=false]"); }
    }

    /** Bazar : electromenager, bricolage, livres, jardin, rayon Halloween, murs d'ecrans. */
    private void bazaar() {
        String[] appliance = {"furnace[facing=west,lit=false]", "smoker[facing=west,lit=false]", "blast_furnace[facing=west,lit=false]", "iron_block", "white_concrete"};
        String[] hifi = {"jukebox", "note_block", "black_concrete", "coal_block"};
        String[] tools = {"crafting_table", "grindstone[face=floor,facing=north]", "anvil[facing=north]", "lantern[hanging=false,waterlogged=false]", "iron_block"};
        String[] garden = {"composter[level=0]", "hay_block[axis=y]", "oak_log[axis=y]", "potted_fern", "potted_red_tulip"};
        gondola(63, 24, 32, appliance, 301, true, false);
        gondola(69, 26, 34, hifi, 302, true, false);
        // bricolage : etabli-presentoir bas (on peut monter dessus)
        for (int x = 75; x <= 81; x++)
            for (int z = 22; z <= 23; z++) {
                set(x, Y, z, "birch_planks");
                if (hash(303, x, 0, z) < 0.7) set(x, Y + 1, z, tools[(int) (hash(304, x, 0, z) * tools.length)]);
            }
        // livres : rayon de bibliotheques
        for (int z = 26; z <= 33; z++) { set(78, Y, z, "bookshelf"); set(78, Y + 1, z, "bookshelf"); if (z % 3 != 0) set(78, Y + 2, z, "bookshelf"); }
        // mur d'ecrans (televisions) sur le mur est
        for (int z = 19; z <= 37; z++) {
            if (z % 8 == 4) continue;
            set(87, Y, z, "spruce_planks");
            for (int y = Y + 1; y <= Y + 4; y++) set(87, y, z, (y == Y + 1 || y == Y + 3) ? "coal_block" : "black_concrete");
            if (z % 4 == 0) for (int y = Y; y <= Y + 4; y++) set(86, y, z, "spruce_fence");
        }
        // jardin : plantes en pots, buissons, composteurs, bottes de foin, buches
        for (int x = 63; x <= 72; x++)
            for (int z = 36; z <= 38; z++) {
                if ((x + z) % 3 == 0) continue;
                set(x, Y, z, garden[(int) (hash(305, x, 0, z) * garden.length)]);
            }
        bush(65, Y, 39);
        bush(70, Y, 39);
        // Halloween : pyramide de citrouilles
        pumpkins(80, 38);
        // ruches (miel) sur une palette
        set(84, Y, 26, slab("spruce_slab", false));
        set(84, Y + 1, 26, "beehive[facing=west,honey_level=0]");
        set(84, Y, 27, slab("spruce_slab", false));
        set(84, Y + 1, 27, "beehive[facing=west,honey_level=0]");
        set(84, Y + 2, 26, "beehive[facing=west,honey_level=0]");
    }

    private void pumpkins(int cx, int cz) {
        for (int lv = 0; lv < 3; lv++)
            for (int x = cx - 2 + lv; x <= cx + 2 - lv; x++)
                for (int z = cz - 2 + lv; z <= cz + 2 - lv; z++) {
                    boolean lantern = hash(81, x, lv, z) < 0.2;
                    set(x, Y + lv, z, lantern ? "jack_o_lantern[facing=south]" : "pumpkin");
                }
    }

    /** Allee des promotions devant les caisses : palettes et bacs a promotions en quinconce. */
    private void promo() {
        int k = 0;
        for (int x = 17; x <= 54; x += 6) {
            if ((k++ & 1) == 0) pallet(x, 55, 2 + (int) (hash(61, x, 0, 0) * 2), 90 + x);
            else {
                // bac : bord en tonneaux, produits en vrac dedans (on peut sauter dedans)
                for (int dx = 0; dx <= 2; dx++)
                    for (int dz = 0; dz <= 2; dz++) {
                        boolean rim = dx != 1 || dz != 1;
                        set(x + dx, Y, 55 + dz, rim ? "barrel[facing=up,open=false]" : PROMO[(int) (hash(62, x, 0, 0) * PROMO.length)]);
                        if (rim && (dx + dz) % 2 == 0) set(x + dx, Y + 1, 55 + dz, PROMO[(int) (hash(63, x, dx, dz) * PROMO.length)]);
                    }
            }
        }
    }

    /** Textile : tables de vetements plies (piles de laine), dans le bazar. */
    private void textile() {
        String[] cloth = {"white_wool", "light_gray_concrete", "blue_wool", "white_concrete", "red_wool", "yellow_concrete"};
        for (int[] tb : new int[][]{{81, 28}, {75, 34}}) {
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 5; dz++) {
                    int x = tb[0] + dx, z = tb[1] + dz;
                    set(x, Y, z, "birch_planks");
                    String c = cloth[(dz / 2 + dx) % cloth.length];
                    set(x, Y + 1, z, c);
                    if (hash(64, x, 0, z) < 0.5) set(x, Y + 2, z, c);
                }
        }
    }

    /** Fruits et legumes : etals en bois a gradins (bacs de 2x2), pyramide de pasteques, fleurs. */
    private void produce() {
        String[] veg = {"melon", "red_wool", "pumpkin", "orange_wool", "yellow_concrete", "green_wool", "oak_leaves[persistent=true]", "white_wool"};
        int k = 0;
        for (int z = 44; z <= 50; z += 6)
            for (int x = 63; x <= 83; x += 7) {
                int ox = ((z / 6) % 2 == 0) ? 0 : 2;                         // etals decales
                if (x + ox + 4 > 86) continue;
                stall(x + ox, z, veg, 400 + k++);
            }
        // pyramide de pasteques (repere)
        for (int lv = 0; lv < 3; lv++)
            for (int x = 66 + lv; x <= 69 - lv + 1; x++)
                for (int z = 41 + lv; z <= 42; z++) set(x, Y + lv, z, "melon");
        // balance
        set(61, Y, 46, "iron_block");
        set(61, Y + 1, 46, slab("smooth_stone_slab", false));
        // fleuriste pres de l'entree
        for (int x = 80; x <= 86; x++) {
            set(x, Y, 57, "spruce_planks");
            set(x, Y + 1, 57, (x % 2 == 0) ? "potted_red_tulip" : "potted_fern");
        }
        bush(85, Y, 54);
    }

    private void stall(int x, int z, String[] veg, long seed) {
        // 5 de long (x), 3 de profondeur (z) : rang avant en y 66, rang arriere en y 67 (gradins)
        for (int dx = 0; dx <= 4; dx++)
            for (int dz = 0; dz <= 2; dz++) {
                set(x + dx, Y, z + dz, "spruce_planks");
                int base = (int) (hash(seed, 0, 0, 0) * veg.length);
                String v = veg[Math.floorMod(base + dx / 2, veg.length)];
                if (dz <= 1) set(x + dx, Y + 1, z + dz, v);
                else { set(x + dx, Y + 1, z + dz, "spruce_planks"); set(x + dx, Y + 2, z + dz, veg[Math.floorMod(base + dx / 2 + 1, veg.length)]); }
            }
        // cageot pose au bout de l'etal : marchepied pour monter sur les fruits
        if (air(x - 1, Y, z)) set(x - 1, Y, z, "barrel[facing=up,open=false]");
        // auvent en trappes
        for (int dx = 0; dx <= 4; dx++) set(x + dx, Y + 3, z + 2, trap("spruce_trapdoor", Dir.NORTH, true, false));
    }

    /** Pilastres sur les deux faces du mur du fond, lampes suspendues au-dessus des allees. */
    private void details() {
        // haut du mur de facade cote hall, mur sud du parking, mur du fond sous la mezzanine
        for (int x = 2; x <= 87; x += 6) {
            for (int y = PCY; y < CY; y++) if (air(x, y, HALL_Z2)) set(x, y, HALL_Z2, "quartz_pillar[axis=y]");
            if (air(x, PCY - 1, PARK_Z2) || B.name(get(x, PCY - 1, PARK_Z2)).endsWith("leaves")) for (int y = Y + 2; y < PCY; y++) set(x, y, PARK_Z2, "quartz_pillar[axis=y]");
            if (x <= MEZ_X2) for (int y = Y; y < MEZ - 1; y++) if (air(x, y, RES_Z2)) set(x, y, RES_Z2, "quartz_pillar[axis=y]");
        }
        for (int x = 41; x <= 87; x += 6) {
            if (!(x >= 47 && x <= 52) && !(x >= 69 && x <= 85)) for (int y = Y; y < CY; y++) if (air(x, y, RES_Z2)) set(x, y, RES_Z2, "quartz_pillar[axis=y]");
            for (int y = MEZ + 1; y < CY; y++) set(x, y, SALE_Z1, "quartz_pillar[axis=y]");
            if (x >= 69 && x <= 85) for (int y = MEZ; y < CY; y++) if (air(x, y, RES_Z2)) set(x, y, RES_Z2, "quartz_pillar[axis=y]");
            set(x, CY - 1, SALE_Z1 + 1, stairs("quartz_stairs", Dir.NORTH, true));
        }
        // corniche (escaliers retournes) sous le plafond le long de tous les murs de la surface et du hall
        for (int x = 2; x <= 87; x++)
            for (int z = SALE_Z1; z <= HALL_Z2; z++) {
                if (!air(x, CY - 1, z)) continue;
                for (Dir d : new Dir[]{Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST}) {
                    String n = B.name(get(x + d.dx, CY - 1, z + d.dz));
                    if (n.equals("smooth_stone") || n.equals("calcite") || n.equals("quartz_pillar")) { set(x, CY - 1, z, stairs("quartz_stairs", d, true)); break; }
                }
            }
        // plinthe en dalles devant les murs du hall et de la caisse (pas dans les allees de rayons)
        for (int x = 3; x <= 86; x++) if (air(x, Y, HALL_Z2) ) set(x, Y, HALL_Z2, slab("smooth_quartz_slab", false));
        for (int x = 22; x <= 54; x += 6)
            for (int z = 26; z <= 50; z += 8) {
                if (!air(x, CY - 4, z)) continue;
                for (int y = CY - 3; y < CY; y++) set(x, y, z, "iron_chain[axis=y,waterlogged=false]");
                set(x, CY - 4, z, hangingLantern());
            }
    }

    // ------------------------------------------------------------------ caisses, hall, facade, parking

    private void checkout() {
        int[] lanes = {12, 20, 28, 36, 44, 52};
        for (int c : lanes) {
            for (int z = 60; z <= 64; z++) {
                set(c, Y, z, "light_gray_concrete");
                set(c, Y + 1, z, z <= 62 ? "black_carpet" : B.AIR);
            }
            set(c, Y + 1, 63, "iron_block");                 // caisse enregistreuse
            set(c, Y + 2, 63, "coal_block");
            set(c + 1, Y, 64, "blue_wool");                  // siege du caissier
            set(c + 1, Y, 65, "light_gray_concrete");
            set(c + 1, Y + 1, 65, "light_gray_concrete");
            // presentoir de bonbons a l'entree de la caisse
            set(c - 1, Y, 60, "light_gray_concrete");
            set(c - 1, Y + 1, 60, (c % 3 == 0) ? "red_wool" : "yellow_concrete");
            set(c - 1, Y + 2, 60, (c % 3 == 0) ? "orange_wool" : "blue_wool");
            // numero de caisse (lampe sur poteau)
            set(c, Y + 2, 60, "iron_chain[axis=y,waterlogged=false]");
            set(c, Y + 3, 60, "iron_chain[axis=y,waterlogged=false]");
            set(c, Y + 4, 60, "sea_lantern");
        }
        // accueil : comptoir en L, ecran, casiers derriere
        for (int x = 62; x <= 72; x++) { set(x, Y, 61, "birch_planks"); set(x, Y + 1, 61, slab("birch_slab", false)); }
        for (int z = 62; z <= 64; z++) { set(72, Y, z, "birch_planks"); set(72, Y + 1, z, slab("birch_slab", false)); }
        set(66, Y + 1, 61, "coal_block");
        for (int x = 62; x <= 70; x++) for (int y = Y; y <= Y + 3; y++) set(x, y, 59, (x % 3 == 0) ? "light_gray_concrete" : "iron_block");
        set(64, Y, 63, "black_concrete"); set(64, Y + 1, 63, "black_concrete");
        // chariots ranges (barreaux) a l'est
        for (int z = 60; z <= 65; z++) for (int x = 78; x <= 85; x += 2) { set(x, Y, z, "iron_bars"); set(x, Y + 1, z, "iron_bars"); }
        for (int x = 77; x <= 86; x++) set(x, Y + 2, 59, "iron_bars");
    }

    private void hall() {
        // bancs, plantes, distributeur, photomaton, distributeur de billets, presentoir de prospectus
        for (int x = 8; x <= 12; x++) set(x, Y, 70, "spruce_planks");
        bush(6, Y, 70);
        bush(56, Y, 70);
        for (int y = Y; y <= Y + 3; y++) { set(18, y, 69, "red_wool"); set(19, y, 69, "red_wool"); }
        set(18, Y + 2, 70, "light_blue_stained_glass");
        // photomaton (cabine bleue, rideau ouvert : on peut y entrer)
        for (int x = 72; x <= 75; x++)
            for (int z = 68; z <= 71; z++)
                for (int y = Y; y <= Y + 4; y++) {
                    boolean shellB = x == 72 || x == 75 || z == 68 || y == Y + 4;
                    set(x, y, z, shellB ? "blue_wool" : B.AIR);
                }
        set(74, Y, 69, "white_concrete");                        // tabouret
        // distributeur de billets
        set(60, Y, 72, "iron_block"); set(60, Y + 1, 72, "coal_block"); set(60, Y + 2, 72, "iron_block");
        // prospectus et panneaux
        set(34, Y, 68, "lectern[facing=south,has_book=false,powered=false]");
        for (int x = 80; x <= 85; x++) set(x, Y, 72, "cauldron");
    }

    /** Facade vitree (z 73..74) avec portes coulissantes ouvertes et mur-rideau. */
    private void facade() {
        for (int x = 2; x <= 87; x++) {
            boolean post = (x - 2) % 6 == 0;
            for (int y = Y; y < CY; y++) {
                String st;
                if (y == Y) st = "polished_blackstone";
                else if (y >= PCY) st = wallP.clustered(x, y, FRONT_Z1);
                else st = post ? "quartz_pillar[axis=y]" : "glass_pane";
                set(x, y, FRONT_Z1, st);
                set(x, y, FRONT_Z2, (y >= PCY || post || y == Y) ? (post && y < PCY ? "quartz_pillar[axis=y]" : y == Y ? "polished_blackstone" : wallP.clustered(x, y, FRONT_Z2)) : B.AIR);
            }
            // bandeau d'enseigne rouge entre les poteaux, cote parking
            if (!post) set(x, PCY - 1, FRONT_Z2, "red_terracotta");
        }
        // portes coulissantes ouvertes (x 40..47) et sas avec vantaux de verre ranges sur les cotes
        air(40, Y, FRONT_Z1, 47, PCY - 2, FRONT_Z2);
        for (int x = 40; x <= 47; x++) { set(x, FY, FRONT_Z1, "polished_andesite"); set(x, FY, FRONT_Z2, "polished_andesite"); set(x, PCY - 1, FRONT_Z2, "red_terracotta"); }
        for (int y = Y; y <= PCY - 2; y++) { set(38, y, FRONT_Z1 - 1, "glass_pane"); set(39, y, FRONT_Z1 - 1, "glass_pane"); set(48, y, FRONT_Z1 - 1, "glass_pane"); set(49, y, FRONT_Z1 - 1, "glass_pane"); }
        // deuxieme entree a l'est (x 66..69)
        air(66, Y, FRONT_Z1, 69, PCY - 2, FRONT_Z2);
    }

    private void parking() {
        for (int x = 2; x <= 87; x++)
            for (int z = PARK_Z1; z <= PARK_Z2; z++) {
                String st = asphaltP.clustered(x, FY, z);
                // places (lignes blanches tous les 6, sur la rangee sud), passage pieton face a l'entree
                if (z >= 82 && x % 6 == 2) st = "smooth_quartz";
                if (z == 81) st = "smooth_quartz";
                if (x >= 40 && x <= 47 && z <= 80 && (z % 2 == 0)) st = "smooth_quartz";
                set(x, FY, z, st);
                for (int y = Y; y < PCY; y++) set(x, y, z, B.AIR);
                boolean light = z % 6 == 0 && x % 6 == 5;
                set(x, PCY, z, light ? "sea_lantern" : "smooth_stone");
            }
        // piliers (bande jaune et noire en bas)
        for (int x = 8; x <= 82; x += 12)
            for (int z : new int[]{78}) {
                for (int y = Y; y < PCY; y++) {
                    String st = y == Y ? "yellow_concrete" : y == Y + 1 ? "coal_block" : "smooth_stone";
                    set(x, y, z, st); set(x + 1, y, z, st);
                }
            }
        // voitures garees (blanc, gris, noir, jaune) sur la rangee sud, une place vide sur deux
        String[] cols = {"white_concrete", "light_gray_concrete", "black_concrete", "yellow_concrete", "white_concrete", "light_gray_concrete"};
        int ci = 0;
        for (int x = 3; x + 4 <= 86; x += 6) {
            if (hash(91, x, 0, 0) < 0.35) continue;
            car(x + 1, 82, cols[ci++ % cols.length]);
        }
        // mur sud du parking : fentes d'aeration a barreaux, jardinieres de haies devant
        for (int x = 2; x <= 87; x++) {
            for (int y = Y + 1; y <= Y + 3; y++) if (x % 6 != 2) set(x, y, 88, "iron_bars");
            if (x % 6 != 2 && x > 3 && x < 86) {
                set(x, Y, 87, "spruce_planks");
                set(x, Y + 1, 87, B.leaves(leavesP.clustered(x, Y + 1, 87)));
            }
        }
        // abri a chariots et borne
        for (int x = 70; x <= 76; x++) { set(x, PCY - 1, 76, slab("smooth_stone_slab", true)); }
        for (int x : new int[]{70, 76}) for (int y = Y; y < PCY - 1; y++) set(x, y, 76, "iron_bars");
        for (int x = 71; x <= 75; x++) { set(x, Y, 77, "iron_bars"); }
        set(20, Y, 77, "iron_block"); set(20, Y + 1, 77, "coal_block");
        // dalle pleine au-dessus du parking (pas de vide enferme)
        for (int x = 2; x <= 87; x++) for (int z = PARK_Z1; z <= PARK_Z2; z++) for (int y = PCY + 1; y <= CY + 1; y++) set(x, y, z, wallP.clustered(x, y, z));
    }

    /** Voiture (5 de long sur z, 3 de large) : roues, caisse, vitres teintees, toit. */
    private void car(int x, int z, String col) {
        for (int dx = 0; dx <= 2; dx++)
            for (int dz = 0; dz <= 4; dz++) {
                boolean wheel = (dx == 0 || dx == 2) && (dz == 0 || dz == 4);
                set(x + dx, Y, z + dz, wheel ? "coal_block" : col);
                set(x + dx, Y + 1, z + dz, col);
                boolean cabin = dz >= 1 && dz <= 3;
                if (cabin) {
                    set(x + dx, Y + 2, z + dz, (dx == 1 && dz == 2) ? col : "black_stained_glass");
                    set(x + dx, Y + 3, z + dz, col);
                }
            }
        set(x, Y + 1, z, "ochre_froglight");
        set(x + 2, Y + 1, z, "ochre_froglight");
    }
}
