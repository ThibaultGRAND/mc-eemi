package fr.eemicraft.soleil;

import static fr.eemicraft.soleil.Layout.*;

import fr.eemicraft.soleil.build.B;
import fr.eemicraft.soleil.build.Connect;
import fr.eemicraft.soleil.build.Dir;
import fr.eemicraft.soleil.build.Noise;
import fr.eemicraft.soleil.build.Palette;
import fr.eemicraft.soleil.build.Sink;
import fr.eemicraft.soleil.build.Stairs;
import java.util.Random;

/**
 * Les cinq zones d'epreuves de Squid Game (skill builder-minecraft), chacune dans sa boite du monde « soleil » :
 * <ul>
 * <li>dalgona : bac a sable geant entoure de murs-decors de ciel, 4 boites a symboles, 16 postes de decoupe (biscuit
 * vertical 11x11 dans un cadre de fer-blanc, tabouret rose), toboggan geant et dome a grimper ;</li>
 * <li>tir a la corde : hall a panneaux, deux tours en echafaudage pastel au-dessus d'un gouffre, plateformes,
 * guillotines decoratives, loge des exemptes ;</li>
 * <li>billes : ruelle de quartier retro au coucher du soleil, maisons a toits en pente, lampadaires ;</li>
 * <li>pont de verre : hall sombre, 18 paires de dalles au-dessus du vide, quais, galerie VIP aux masques dores ;</li>
 * <li>finale : terre battue, calamar trace a la craie, estrades des gardes, mats a projecteurs, faux ciel.</li>
 * </ul>
 */
final class SquidZones extends Kit {
    /** Boites reconstruites {x1, y1, z1, x2, y2, z2}. */
    static final int[] DALGONA_BOX = {156, FY - 4, -50, 244, FY + 36, 54};
    static final int[] CORDE_BOX = {358, C_BOTTOM - 2, -26, 442, FY + 46, 26};
    static final int[] BILLES_BOX = {546, FY - 4, -29, 654, FY + 38, 30};
    static final int[] PONT_BOX = {752, P_BOTTOM - 2, -28, 846, FY + 47, 28};
    static final int[] FINALE_BOX = {968, FY - 4, -38, 1032, FY + 38, 38};

    SquidZones(Sink s) {
        super(s);
    }

    static int[] box(String id) {
        return switch (id) {
            case "dalgona" -> DALGONA_BOX;
            case "corde" -> CORDE_BOX;
            case "billes" -> BILLES_BOX;
            case "pont" -> PONT_BOX;
            case "finale" -> FINALE_BOX;
            default -> throw new IllegalArgumentException(id);
        };
    }

    void build(String id) {
        switch (id) {
            case "dalgona" -> dalgona();
            case "corde" -> corde();
            case "billes" -> billes();
            case "pont" -> pont();
            case "finale" -> finale();
            default -> throw new IllegalArgumentException(id);
        }
        int[] b = box(id);
        Stairs.fix(s, b[0], b[1], b[2], b[3], b[4], b[5]);
        Connect.fix(s, b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    private void corners(int x1, int z1, int x2, int z2, int y0, int top) {
        int[][] c = {{x1 - 5, z1 - 5}, {x2 + 1, z1 - 5}, {x1 - 5, z2 + 1}, {x2 + 1, z2 + 1}};
        for (int[] p : c) {
            for (int x = p[0]; x <= p[0] + 4; x++)
                for (int z = p[1]; z <= p[1] + 4; z++)
                    for (int y = y0 - 1; y <= top; y++) {
                        boolean edge = x == p[0] || x == p[0] + 4 || z == p[1] || z == p[1] + 4;
                        String st = "smooth_quartz";
                        if (edge && (y - y0) % 8 == 4) st = "white_terracotta";
                        if (edge && y == top) st = "chiseled_quartz_block";
                        if (y <= y0 && edge) st = "polished_andesite";
                        set(x, y, z, st);
                    }
            for (int x = p[0]; x <= p[0] + 4; x++)
                for (int z = p[1]; z <= p[1] + 4; z++) set(x, top + 1, z, "smooth_quartz_slab[type=bottom]");
            int cx = p[0] + 2, cz = p[1] + 2;
            Dir face = cz < (z1 + z2) / 2 ? Dir.SOUTH : Dir.NORTH;
            mast(cx, top + 2, cz, 8, face);
        }
    }

    /** Bouche les angles de quatre murs a panneaux (plans d0..d2) avec le materiau des pilastres. */
    private void cornerFill(int wx1, int wz1, int wx2, int wz2, int y0, int y1, String st) {
        int[][] c = {{wx1, wz1, -1, -1}, {wx2, wz1, 1, -1}, {wx1, wz2, -1, 1}, {wx2, wz2, 1, 1}};
        for (int[] p : c)
            for (int a = 0; a <= 2; a++)
                for (int b = 0; b <= 2; b++)
                    for (int y = y0; y <= y1 + 1; y++)
                        set(p[0] + p[2] * a, y, p[1] + p[3] * b, y == y1 + 1 ? "polished_andesite_slab[type=bottom]" : st);
    }

    // =================================================================== dalgona

    private static final String[] BOX_COLOR = {"yellow_concrete", "pink_concrete", "light_blue_concrete", "purple_concrete"};
    private static final String[][] SYMBOL = {
            {".###.", "#...#", "#...#", "#...#", ".###."},
            {"..#..", ".#.#.", ".#.#.", "#...#", "#####"},
            {"..#..", "#####", ".###.", ".#.#.", "#...#"},
            {".###.", "#####", "..#..", "..#..", ".##.."}};

    void dalgona() {
        int x1 = DX - 33, x2 = DX + 33, z1 = -43, z2 = 47;
        int y0 = FY;
        Palette sand = Palette.of(5101, "sand", 60, "smooth_sandstone", 16, "sandstone", 12, "white_concrete_powder", 12).period(6);
        Noise mound = new Noise(5102);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                fill(x, y0 - 3, z, x, y0 - 1, z, "dirt");
                set(x, y0, z, sand);
                // dunes dans les marges seulement (hors postes, boites, rassemblement)
                boolean play = dPlay(x, z);
                if (!play) {
                    double m = mound.fbm2(x / 9.0, z / 9.0, 3, 0.5, 2);
                    int h = (int) Math.floor(Math.max(0, m) * 4.2);
                    int edge = Math.min(Math.min(x - x1, x2 - x), Math.min(z - z1, z2 - z));
                    if (edge < 2) h = Math.min(h, edge);
                    for (int k = 1; k <= h; k++) set(x, y0 + k, z, sand);
                }
            }
        // chemin de planches du rassemblement aux postes
        for (int z = -12; z <= 1; z++)
            for (int x = DX - 2; x <= DX + 2; x++) {
                boolean edge = Math.abs(x - DX) == 2;
                set(x, y0, z, edge ? "stripped_birch_log[axis=z]" : "birch_planks");
            }
        int[] pilNS = steps(x1, x2, 8, 3);
        int[] pilWE = steps(z1, z2, 8, 2);
        skyWall(Frame.west(x1 - 1), z1, z2, y0, 22, 61, DAY, pilWE);
        skyWall(Frame.east(x2 + 1), z1, z2, y0, 22, 62, DAY, pilWE);
        skyWall(Frame.north(z1 - 1), x1, x2, y0, 22, 63, DAY, pilNS);
        skyWall(Frame.south(z2 + 1), x1, x2, y0, 22, 64, DAY, pilNS);
        corners(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y0, y0 + 22);
        // boites des formes
        for (int i = 0; i < 4; i++) shapeBox(DX + D_BOXX[i], i);
        // postes de decoupe
        for (int r = 0; r < D_ROWS; r++)
            for (int c = 0; c < 4; c++) station(dColX(c), dWallZ(r), (r * 4 + c) % 3);
        slide(DX + 22, -40);
        dome(DX - 25, -31);
        // escaliers pastel « Escher » contre les murs du fond
        escher(DX - 31, y0 + 1, z2 - 2, Dir.NORTH, Dir.WEST, 6, "pink_concrete", "cherry_stairs");
        escher(DX + 31, y0 + 1, z2 - 2, Dir.NORTH, Dir.EAST, 6, "light_blue_concrete", "warped_stairs");
        // pied des deux volees degage (la dune de la marge bouchait la premiere marche)
        for (int x : new int[]{DX - 31, DX - 30, DX + 30, DX + 31})
            for (int y = y0 + 1; y <= y0 + 3; y++) set(x, y, z2 - 1, "air");
        // touffes d'herbes seches et buissons sur les dunes des marges
        Noise veg = new Noise(5104);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean play = dPlay(x, z);
                if (play) continue;
                int y = y0 + 1;
                while (y < y0 + 6 && !air(x, y, z)) y++;
                if (!air(x, y, z) || !B.name(s.get(x, y - 1, z)).contains("sand")) continue;
                double u = veg.uniform2(x / 2.2, z / 2.2);
                if (u > 0.86) set(x, y, z, "short_dry_grass");
                else if (u > 0.81) set(x, y, z, "dead_bush");
                else if (u < 0.04) set(x, y, z, "tall_dry_grass");
            }
        // projecteurs au sol vers les boites
        for (int i = 0; i < 4; i++) {
            int bx = DX + D_BOXX[i];
            set(bx, y0, D_PADZ + 4, "ochre_froglight");
        }
        checkDalgona();
    }

    /** Boite en fer-blanc a symbole (7 x 5 x 5) et son tapis de choix. */
    private void shapeBox(int bx, int i) {
        int y0 = FY;
        String col = BOX_COLOR[i];
        for (int x = bx - 3; x <= bx + 3; x++)
            for (int z = D_BOXZ - 4; z <= D_BOXZ; z++)
                for (int y = y0 + 1; y <= y0 + 5; y++) {
                    boolean corner = (x == bx - 3 || x == bx + 3) && (z == D_BOXZ - 4 || z == D_BOXZ);
                    set(x, y, z, corner ? "smooth_quartz" : col);
                }
        // couvercle en debord, rebord en escaliers retournes
        for (int x = bx - 4; x <= bx + 4; x++)
            for (int z = D_BOXZ - 5; z <= D_BOXZ + 1; z++) {
                boolean ring = x == bx - 4 || x == bx + 4 || z == D_BOXZ - 5 || z == D_BOXZ + 1;
                set(x, y0 + 6, z, ring ? "smooth_quartz_slab[type=top]" : "smooth_quartz");
                if (!ring) set(x, y0 + 7, z, "smooth_quartz_slab[type=bottom]");
            }
        set(bx, y0 + 7, D_BOXZ - 2, "lantern[hanging=false]"); // a la place de la dalle du dessus : posee sur le quartz plein
        // symbole en saillie sur la face (plaque blanche), cornieres
        String[] sym = SYMBOL[i];
        for (int row = 0; row < 5; row++)
            for (int k = 0; k < 5; k++)
                if (sym[row].charAt(k) == '#') set(bx - 2 + k, y0 + 5 - row, D_BOXZ + 1, "white_concrete");
        for (int y = y0 + 1; y <= y0 + 5; y++) {
            set(bx - 3, y, D_BOXZ + 1, "quartz_pillar[axis=y]");
            set(bx + 3, y, D_BOXZ + 1, "quartz_pillar[axis=y]");
        }
        // tapis de choix 3x3 et liseré
        for (int x = bx - 2; x <= bx + 2; x++)
            for (int z = D_PADZ - 2; z <= D_PADZ + 2; z++) {
                boolean in = Math.abs(x - bx) <= 1 && Math.abs(z - D_PADZ) <= 1;
                set(x, FY, z, in ? col : "smooth_quartz");
            }
    }

    /** Poste de decoupe : cadre de fer-blanc, biscuit vierge 11x11, fond sombre, tabouret rose pour le joueur. */
    private void station(int xc, int w, int variant) {
        int y0 = FY;
        int half = D_SIZE / 2;
        // fond (z w-1) et dos decore (z w-2 : nervures)
        for (int x = xc - half - 1; x <= xc + half + 1; x++)
            for (int y = y0 + 1; y <= D_BY0 + D_SIZE; y++) {
                set(x, y, w - 1, "gray_concrete");
                boolean rib = Math.floorMod(x - xc, 4) == 0 || y == y0 + 1 || y == D_BY0 + D_SIZE;
                if (rib) set(x, y, w - 2, "light_gray_concrete");
            }
        // cadre (z w) et biscuit vierge
        for (int x = xc - half - 1; x <= xc + half + 1; x++)
            for (int y = y0 + 1; y <= D_BY0 + D_SIZE; y++) {
                boolean frame = x == xc - half - 1 || x == xc + half + 1 || y == y0 + 1 || y == D_BY0 + D_SIZE;
                set(x, y, w, frame ? "smooth_stone" : "honeycomb_block");
            }
        for (int x = xc - half - 1; x <= xc + half + 1; x++) {
            set(x, D_BY0 + D_SIZE + 1, w, "smooth_stone_slab[type=bottom]");
            set(x, D_BY0 + D_SIZE, w + 1, B.stairs("stone_stairs", Dir.NORTH, true));
            set(x, D_BY0 + D_SIZE, w - 2, "smooth_stone_slab[type=top]");
        }
        // pieds
        for (int k = -1; k <= 1; k += 2) {
            set(xc + k * (half + 1), y0 + 1, w + 1, B.stairs("stone_stairs", Dir.NORTH, false));
        }
        // tabouret : plateau 3x2 en y0+5 (on s'y tient en y0+6), fut rose, marches, rambarde sur les cotes et l'arriere
        String top = variant == 0 ? "cherry_planks" : variant == 1 ? "pink_terracotta" : "birch_planks";
        set(xc, y0 + 1, w + 1, B.stairs("stone_stairs", Dir.NORTH, false));
        for (int x = xc - 1; x <= xc + 1; x++)
            for (int z = w + 4; z <= w + 5; z++) set(x, y0 + 5, z, top);
        for (int y = y0 + 1; y <= y0 + 4; y++) {
            set(xc, y, w + 4, "pink_concrete");
            set(xc, y, w + 5, "pink_concrete");
        }
        set(xc - 1, y0 + 4, w + 4, B.stairs("cherry_stairs", Dir.EAST, true));
        set(xc + 1, y0 + 4, w + 4, B.stairs("cherry_stairs", Dir.WEST, true));
        set(xc - 1, y0 + 4, w + 5, B.stairs("cherry_stairs", Dir.EAST, true));
        set(xc + 1, y0 + 4, w + 5, B.stairs("cherry_stairs", Dir.WEST, true));
        for (int x = xc - 1; x <= xc + 1; x++) set(x, y0 + 6, w + 6, "cherry_fence");
        set(xc - 1, y0 + 6, w + 5, "cherry_fence");
        set(xc + 1, y0 + 6, w + 5, "cherry_fence");
        set(xc + 1, y0 + 7, w + 6, "lantern[hanging=false]");
        set(xc, y0 + 1, w + 3, "bamboo_slab[type=bottom]");
    }

    /** Toboggan geant : tour 5x5 pastel a toit en pointe, piste en bambou qui descend vers le sud. */
    private void slide(int tx, int tz) {
        int y0 = FY;
        int ty = y0 + 12;
        for (int x = tx; x <= tx + 4; x++)
            for (int z = tz; z <= tz + 4; z++)
                for (int y = y0 + 1; y <= ty; y++) {
                    boolean corner = (x == tx || x == tx + 4) && (z == tz || z == tz + 4);
                    boolean edge = x == tx || x == tx + 4 || z == tz || z == tz + 4;
                    if (corner) set(x, y, z, "quartz_pillar[axis=y]");
                    else if (edge) set(x, y, z, (y - y0) % 6 == 0 ? "white_concrete" : (y < y0 + 6 ? "light_blue_concrete" : "pink_concrete"));
                }
        for (int x = tx - 1; x <= tx + 5; x++)
            for (int z = tz - 1; z <= tz + 5; z++) {
                boolean ring = x == tx - 1 || x == tx + 5 || z == tz - 1 || z == tz + 5;
                set(x, ty + 1, z, ring ? "smooth_quartz_slab[type=top]" : "bamboo_planks");
            }
        // toit en pyramide (escaliers de cerisier, debord de 1)
        for (int k = 0; k <= 3; k++) {
            int a = tx - 1 + k, b = tx + 5 - k, c = tz - 1 + k, d = tz + 5 - k;
            int y = ty + 5 + k;
            for (int x = a; x <= b; x++) {
                set(x, y, c, B.stairs("cherry_stairs", Dir.SOUTH, false));
                set(x, y, d, B.stairs("cherry_stairs", Dir.NORTH, false));
            }
            for (int z = c + 1; z < d; z++) {
                set(a, y, z, B.stairs("cherry_stairs", Dir.EAST, false));
                set(b, y, z, B.stairs("cherry_stairs", Dir.WEST, false));
            }
        }
        set(tx + 2, ty + 9, tz + 2, "cherry_planks");
        set(tx + 2, ty + 10, tz + 2, "lightning_rod");
        for (int x = tx; x <= tx + 4; x += 4)
            for (int z = tz; z <= tz + 4; z += 4)
                for (int y = ty + 2; y <= ty + 4; y++) set(x, y, z, "cherry_fence");
        // piste : 3 de large, descend de 1 tous les 2 vers le sud
        int sx = tx + 1;
        int len = 22;
        for (int k = 0; k < len; k++) {
            int z = tz + 5 + k;
            double hy = ty - k * 0.5;
            int by = (int) Math.floor(hy);
            boolean half = hy - by >= 0.5;
            for (int x = sx; x <= sx + 2; x++) {
                if (by > y0) {
                    set(x, by, z, half ? "bamboo_planks" : "bamboo_slab[type=top]");
                    if (half) set(x, by + 1, z, "bamboo_slab[type=bottom]");
                }
            }
            set(sx - 1, by + 1, z, "pink_concrete");
            set(sx + 3, by + 1, z, "pink_concrete");
            if (k % 4 == 0 && by > y0 + 1) {
                for (int y = y0 + 1; y < by; y++) {
                    set(sx - 1, y, z, "quartz_pillar[axis=y]");
                    set(sx + 3, y, z, "quartz_pillar[axis=y]");
                }
            }
        }
    }

    /** Dome a grimper : demi-sphere en treillis colore (cases d'une parite sur deux). */
    private void dome(int cx, int cz) {
        int y0 = FY;
        double r = 6.5;
        for (int x = cx - 7; x <= cx + 7; x++)
            for (int z = cz - 7; z <= cz + 7; z++)
                for (int y = y0 + 1; y <= y0 + 7; y++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (y - y0) * (y - y0) * 1.0 + (z - cz) * (z - cz));
                    if (d > r - 0.5 && d <= r + 0.5) {
                        boolean bar = Math.floorMod(x + z, 2) == 0 || Math.floorMod(y - y0, 3) == 0;
                        if (bar) set(x, y, z, y - y0 <= 2 ? "cyan_concrete" : y - y0 <= 4 ? "lime_concrete" : "yellow_concrete");
                    }
                }
        set(cx, y0 + 7, cz, "yellow_concrete");
        set(cx, y0 + 8, cz, "white_banner");
    }

    /** Cases du dalgona sans dune ni vegetation : postes, boites, rassemblement, et le sol des 4 gardes. */
    static boolean dPlay(int x, int z) {
        return (x >= DX - 28 && x <= DX + 28 && z >= 0 && z <= 42) || (x >= DX - 20 && x <= DX + 20 && z >= -38 && z <= -6)
                // gardes d'EpDalgona en (DX-24.5 / DX+25.5, z -20.5) et (DX-29.5 / DX+30.5, z 20.5)
                || (Math.abs(z + 21) <= 2 && (Math.abs(x - (DX - 25)) <= 2 || Math.abs(x - (DX + 25)) <= 2))
                || (Math.abs(z - 20) <= 2 && (Math.abs(x - (DX - 30)) <= 2 || Math.abs(x - (DX + 30)) <= 2));
    }

    private void checkDalgona() {
        for (int r = 0; r < D_ROWS; r++)
            for (int c = 0; c < 4; c++) {
                int xc = dColX(c), w = dWallZ(r);
                if (!B.isSolid(s.get(xc, FY + 5, w + 4))) warnings.add("dalgona : tabouret sans plateau " + c + "," + r);
                for (int y = FY + 6; y <= FY + 7; y++)
                    if (!air(xc, y, w + 4)) warnings.add("dalgona : place du joueur encombree " + c + "," + r);
                for (int z = w + 1; z <= w + 3; z++)
                    for (int y = D_BY0; y < D_BY0 + D_SIZE; y++)
                        if (!air(xc, y, z) && !(z == w + 1 && y == D_BY0 + D_SIZE - 1)) {
                            warnings.add("dalgona : vue du biscuit bouchee " + c + "," + r + " en y " + y + " z " + z);
                            return;
                        }
            }
        for (int i = 0; i < 4; i++)
            for (int y = FY + 1; y <= FY + 2; y++)
                if (!air(DX + D_BOXX[i], y, D_PADZ)) warnings.add("dalgona : tapis " + i + " encombre");
        for (int[] gd : new int[][]{{DX - 25, -21}, {DX + 25, -21}, {DX - 30, 20}, {DX + 30, 20}}) {
            if (!B.isSolid(s.get(gd[0], FY, gd[1]))) warnings.add("dalgona : garde sans sol en " + gd[0] + " " + gd[1]);
            for (int y = FY + 1; y <= FY + 2; y++)
                if (!air(gd[0], y, gd[1])) warnings.add("dalgona : garde enterre en " + gd[0] + " " + y + " " + gd[1]);
        }
    }

    // =================================================================== tir a la corde

    void corde() {
        int x1 = CX - 37, x2 = CX + 37, z1 = -21, z2 = 21;
        int y0 = C_BOTTOM;
        int y1 = FY + 40;
        Palette floor = Palette.of(5201, "blackstone", 70, "tuff", 15, "gravel", 15).period(7);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, y0 - 1, z, "deepslate");
                set(x, y0, z, floor);
            }
        Palette panel = Palette.of(5202, "smooth_stone", 70, "light_gray_terracotta", 30).period(8);
        int[] pilA = steps(z1, z2, 7, 0);
        int[] pilB = steps(x1, x2, 7, 2);
        panelWall(Frame.west(x1 - 1), z1, z2, y0, y1, panel, "polished_andesite", "pink_terracotta", "polished_blackstone",
                "polished_andesite_stairs", pilA, "lantern[hanging=false]", "iron_bars");
        panelWall(Frame.east(x2 + 1), z1, z2, y0, y1, panel, "polished_andesite", "pink_terracotta", "polished_blackstone",
                "polished_andesite_stairs", pilA, "lantern[hanging=false]", "iron_bars");
        panelWall(Frame.north(z1 - 1), x1, x2, y0, y1, panel, "polished_andesite", "light_blue_terracotta", "polished_blackstone",
                "polished_andesite_stairs", pilB, "lantern[hanging=false]", "iron_bars");
        panelWall(Frame.south(z2 + 1), x1, x2, y0, y1, panel, "polished_andesite", "light_blue_terracotta", "polished_blackstone",
                "polished_andesite_stairs", pilB, "lantern[hanging=false]", "iron_bars");
        cornerFill(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y0, y1, "polished_andesite");
        tower(-1);
        tower(1);
        // loge des exemptes / spectateurs (mur nord)
        for (int x = CX - 7; x <= CX + 7; x++)
            for (int z = z1; z <= z1 + 4; z++) {
                boolean rim = z == z1 + 4 || x == CX - 7 || x == CX + 7;
                set(x, CP + 4, z, rim ? "smooth_quartz" : "birch_planks");
                if (z == z1 + 4) {
                    set(x, CP + 5, z, "glass_pane");
                    set(x, CP + 3, z, B.stairs("smooth_quartz_stairs", Dir.NORTH, true));
                }
                if ((x == CX - 7 || x == CX + 7) && z < z1 + 4) set(x, CP + 5, z, "glass_pane");
            }
        for (int x = CX - 7; x <= CX + 7; x += 7) set(x, CP + 3, z1 + 1, B.stairs("smooth_quartz_stairs", Dir.NORTH, true));
        for (int x = CX - 6; x <= CX + 6; x += 4) {
            set(x, CP + 10, z1 + 2, "iron_chain[axis=y]");
            set(x, CP + 9, z1 + 2, "lantern[hanging=true]");
        }
        for (int x = CX - 8; x <= CX + 8; x++)
            for (int z = z1; z <= z1 + 5; z++) set(x, CP + 11, z, (x + z) % 2 == 0 ? "pink_terracotta" : "white_terracotta");
        // mats d'eclairage dans les angles du gouffre
        mast(x1 + 1, y0 + 1, z1 + 1, 50, Dir.SOUTH);
        mast(x2 - 1, y0 + 1, z1 + 1, 50, Dir.SOUTH);
        mast(x1 + 1, y0 + 1, z2 - 1, 50, Dir.NORTH);
        mast(x2 - 1, y0 + 1, z2 - 1, 50, Dir.NORTH);
        // lumieres au fond du gouffre
        for (int x = CX - 30; x <= CX + 30; x += 10) set(x, y0 + 1, (x / 10) % 2 == 0 ? -8 : 8, "soul_lantern[hanging=false]");
        // ligne centrale sous la corde (reperes peints au fond)
        for (int z = -1; z <= 1; z++) set(CX, y0, z, "red_concrete");
        checkCorde();
    }

    /** Tour d'une equipe (side -1 ouest, 1 est) : echafaudage pastel, plateforme, rambardes, guillotine decorative. */
    private void tower(int side) {
        int near = CX + side * C_EDGE;          // bord cote vide
        int far = CX + side * (C_EDGE + C_LEN - 1);
        int a = Math.min(near, far), b = Math.max(near, far);
        String col = side < 0 ? "pink_concrete" : "light_blue_concrete";
        String col2 = side < 0 ? "pink_terracotta" : "cyan_terracotta";
        // poteaux tous les 6 en x, en z -3 et 3, du fond au plancher
        for (int x = a; x <= b; x++) {
            boolean post = Math.floorMod(x - a, 6) == 0 || x == b;
            for (int z = -3; z <= 3; z += 6) {
                if (post) {
                    for (int y = C_BOTTOM + 1; y < CP; y++) set(x, y, z, ((y - C_BOTTOM) / 8) % 2 == 0 ? col : "white_concrete");
                }
            }
            // traverses tous les 9
            for (int y = C_BOTTOM + 9; y < CP - 1; y += 9) {
                set(x, y, -3, col2);
                set(x, y, 3, col2);
            }
            for (int y = C_BOTTOM + 9; y < CP - 1; y += 9)
                if (post) for (int z = -2; z <= 2; z++) set(x, y, z, col2);
        }
        // plancher de la plateforme, liseré, sous-face
        for (int x = a; x <= b; x++)
            for (int z = -3; z <= 3; z++) {
                boolean rim = Math.abs(z) == 3;
                set(x, CP, z, rim ? col : "birch_planks");
                set(x, CP - 1, z, rim ? B.stairs("smooth_quartz_stairs", z < 0 ? Dir.SOUTH : Dir.NORTH, true) : "stripped_birch_log[axis=x]");
            }
        for (int x = a; x <= b; x++) {
            set(x, CP + 1, -3, "cherry_fence");
            set(x, CP + 1, 3, "cherry_fence");
        }
        // bord cote vide : ligne blanche peinte
        for (int z = -2; z <= 2; z++) set(near, CP, z, "white_concrete");
        // guillotine decorative au fond
        int gx = far + side;
        for (int z = -3; z <= 3; z++) set(gx, CP, z, col);
        // estrade des gardes derriere la guillotine (EpCorde : gardes en CX-27.5 / CX+28.5, z -2.5 et 3.5)
        for (int z = -3; z <= 3; z++) {
            set(gx + side, CP, z, col2);
            set(gx + side, CP - 1, z, B.stairs("smooth_quartz_stairs", side < 0 ? Dir.EAST : Dir.WEST, true));
        }
        for (int y = CP + 1; y <= CP + 9; y++) {
            set(gx, y, -3, "dark_oak_log[axis=y]");
            set(gx, y, 3, "dark_oak_log[axis=y]");
        }
        for (int z = -3; z <= 3; z++) set(gx, CP + 10, z, "dark_oak_log[axis=z]");
        for (int z = -2; z <= 2; z++) {
            set(gx, CP + 8, z, "iron_block");
            set(gx, CP + 7, z, B.trapdoor("iron_trapdoor", side < 0 ? Dir.EAST : Dir.WEST, true, false));
        }
        set(gx, CP + 9, 0, "iron_chain[axis=y]");
        set(gx, CP + 11, -3, "lantern[hanging=false]");
        set(gx, CP + 11, 3, "lantern[hanging=false]");
        // escaliers « Escher » qui grimpent le long de la tour (decor, cote exterieur)
        int ex = side < 0 ? a + 2 : b - 2;
        escher(ex, C_BOTTOM + 1, -5, side < 0 ? Dir.EAST : Dir.WEST, Dir.NORTH, 9, col, side < 0 ? "cherry_stairs" : "warped_stairs");
        escher(ex + side * -9, C_BOTTOM + 10, 5, side < 0 ? Dir.EAST : Dir.WEST, Dir.SOUTH, 9, col, side < 0 ? "cherry_stairs" : "warped_stairs");
        // lanternes et chaines sous la plateforme, plinthe en trappes, echelles sur les poteaux exterieurs
        for (int x = a + 3; x <= b; x += 6) {
            set(x, CP - 2, 0, "lantern[hanging=true]");
            for (int y = CP - 8; y <= CP - 2; y++) {
                set(x, y, -2, "iron_chain[axis=y]");
                set(x, y, 2, "iron_chain[axis=y]");
            }
        }
        for (int x = a; x <= b; x++) {
            set(x, CP, -4, B.trapdoor("spruce_trapdoor", Dir.NORTH, true, true));
            set(x, CP, 4, B.trapdoor("spruce_trapdoor", Dir.SOUTH, true, true));
        }
        for (int x = a; x <= b; x++) {
            if (Math.floorMod(x - a, 6) != 0) continue;
            for (int y = C_BOTTOM + 1; y < CP - 1; y++) {
                if (air(x, y, -4)) set(x, y, -4, "ladder[facing=north]");
                if (air(x, y, 4)) set(x, y, 4, "ladder[facing=south]");
            }
        }
        // caisses et rouleaux de corde au pied des tours
        for (int k = 0; k < 4; k++) {
            int bx = a + 2 + k * 5;
            set(bx, C_BOTTOM + 1, side < 0 ? 9 : -9, "barrel[facing=up]");
            set(bx + 1, C_BOTTOM + 1, side < 0 ? 9 : -9, k % 2 == 0 ? "hay_block" : "barrel[facing=north]");
        }
    }

    private void checkCorde() {
        for (int gxx : new int[]{CX - 28, CX + 28})
            for (int gz : new int[]{-3, 3}) {
                if (!B.isSolid(s.get(gxx, CP, gz))) warnings.add("corde : garde sans sol en " + gxx + " " + gz);
                if (!air(gxx, CP + 1, gz) || !air(gxx, CP + 2, gz)) warnings.add("corde : garde encombre en " + gxx + " " + gz);
            }
        for (int i = 0; i < 8; i++) {
            int xa = CX - 8 - 2 * i, xb = CX + 8 + 2 * i;
            if (!B.isSolid(s.get(xa, CP, 0)) || !B.isSolid(s.get(xb, CP, 0))) warnings.add("corde : place " + i + " sans sol");
            if (!air(xa, CP + 1, 0) || !air(xa, CP + 2, 0) || !air(xb, CP + 1, 0) || !air(xb, CP + 2, 0))
                warnings.add("corde : place " + i + " encombree");
        }
        for (int x = CX - C_EDGE + 1; x <= CX + C_EDGE - 1; x++)
            for (int y = C_FALL; y <= CP + 3; y++)
                if (!air(x, y, 0)) {
                    warnings.add("corde : le vide central est bouche en " + x + " " + y);
                    return;
                }
    }

    // =================================================================== billes

    void billes() {
        int x1 = BX - 47, x2 = BX + 47, z1 = -22, z2 = 23;
        int y0 = FY;
        Palette street = Palette.of(5301, "packed_mud", 50, "coarse_dirt", 22, "dirt", 16, "gravel", 12).period(5);
        Palette yard = Palette.of(5302, "moss_block", 40, "coarse_dirt", 30, "rooted_dirt", 30).period(4);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                fill(x, y0 - 3, z, x, y0 - 1, z, "dirt");
                String st;
                if (z >= -5 && z <= 6) st = street.at(x, y0, z);
                else if (z == -6 || z == 7) st = "polished_andesite";
                else st = yard.at(x, y0, z);
                set(x, y0, z, st);
            }
        // caniveaux et petits trous de billes
        for (int x = x1; x <= x2; x++) {
            set(x, y0, -5, "andesite");
            set(x, y0, 6, "andesite");
        }
        for (int k = 0; k < 8; k++) {
            int px = bPairX(k);
            set(px, y0, 0, "mud");
            set(px, y0, 1, "mud");
        }
        // maisons des deux cotes
        Random r = new Random(5303);
        for (int side = -1; side <= 1; side += 2) {
            int x = x1 + 1;
            int idx = side < 0 ? 0 : 1;
            while (x < x2 - 6) {
                int w = 7 + r.nextInt(3);
                if (x + w - 1 > x2 - 1) w = x2 - x;
                if (w < 6) break;
                int front = side < 0 ? -8 : 9;
                house(x, x + w - 1, front, side < 0 ? Dir.SOUTH : Dir.NORTH, r.nextInt(3), r.nextInt(4) == 0 ? 9 : 5, r.nextLong(), idx++);
                x += w + (r.nextInt(3) == 0 ? 1 : 0);
            }
        }
        // jardinets : herbes, fougeres et fleurs en grappes
        Noise veg = new Noise(5304);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (z > -7 && z < 8) continue;
                if (!air(x, y0 + 1, z)) continue;
                String g = B.name(s.get(x, y0, z));
                if (!g.equals("moss_block") && !g.equals("coarse_dirt") && !g.equals("rooted_dirt")) continue;
                double u = veg.uniform2(x / 2.0, z / 2.0);
                double f = veg.derive(3).uniform2(x / 5.0, z / 5.0);
                if (u > 0.72) set(x, y0 + 1, z, f > 0.6 ? "fern" : "short_grass");
                else if (u > 0.66 && f > 0.5) set(x, y0 + 1, z, f > 0.8 ? "poppy" : "dandelion");
            }
        int[] pilNS = steps(x1, x2, 9, 4);
        int[] pilWE = steps(z1, z2, 8, 3);
        skyWall(Frame.north(z1 - 1), x1, x2, y0, 24, 71, SUNSET, pilNS);
        skyWall(Frame.south(z2 + 1), x1, x2, y0, 24, 72, SUNSET, pilNS);
        skyWall(Frame.west(x1 - 1), z1, z2, y0, 24, 73, SUNSET, pilWE);
        skyWall(Frame.east(x2 + 1), z1, z2, y0, 24, 74, SUNSET, pilWE);
        corners(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y0, y0 + 24);
        // lampadaires sur les trottoirs, entre les paires
        for (int k = 0; k <= 8; k++) {
            int lx = bPairX(0) - 4 + 8 * k;
            boolean n = k % 2 == 0;
            lampPost(lx, y0 + 1, n ? -6 : 7, n ? Dir.SOUTH : Dir.NORTH, "lantern[hanging=true]");
            set(lx, y0, n ? -6 : 7, "polished_andesite");
        }
        checkBilles();
    }

    private static final String[][] HOUSE_PAL = {
            {"bricks", "70", "granite", "15", "terracotta", "15"},
            {"pink_terracotta", "65", "white_terracotta", "20", "light_gray_terracotta", "15"},
            {"yellow_terracotta", "60", "white_terracotta", "25", "smooth_sandstone", "15"}};
    private static final String[] HOUSE_TRIM = {"stone_bricks", "white_concrete", "smooth_quartz"};
    private static final String[] HOUSE_ROOF = {"deepslate_tile", "mud_brick", "dark_prismarine"};

    /**
     * Maison de la ruelle : facade sur la rue (out) en 3 plans (cornieres en saillie, mur, fenetres en retrait avec
     * appui et linteau), porte en retrait sous auvent, socle, toit a deux pans le long de la rue avec debord de 1.
     */
    private void house(int xa, int xb, int front, Dir out, int pal, int h, long seed, int idx) {
        int y0 = FY;
        int depth = 6 + (int) Math.floorMod(seed >> 8, 3);
        int dz = out == Dir.SOUTH ? -1 : 1; // vers l'arriere
        int back = front + dz * (depth - 1);
        Palette wall = new Palette(seed);
        String[] p = HOUSE_PAL[pal];
        for (int i = 0; i < p.length; i += 2) wall.add(p[i], Integer.parseInt(p[i + 1]));
        wall.period(5);
        String trim = HOUSE_TRIM[(pal + idx) % 3];
        String roof = HOUSE_ROOF[(pal + idx / 2) % 3];
        int zA = Math.min(front, back), zB = Math.max(front, back);
        // maisons accolees : le debord de toit de la maison precedente (colonne xa) est efface, et cette maison ne pose
        // pas son debord dans la colonne xa-1 si quelque chose y est deja (sinon deux toits s'entremelent en escalier)
        int zr1 = Math.min(front - 2 * dz, front + 12 * dz), zr2 = Math.max(front - 2 * dz, front + 12 * dz);
        boolean leftFree = true;
        for (int y = y0 + 1; y <= y0 + 30 && leftFree; y++)
            for (int z = zr1; z <= zr2; z++) if (!air(xa - 1, y, z)) { leftFree = false; break; }
        for (int y = y0 + 1; y <= y0 + 30; y++)
            for (int z = zr1; z <= zr2; z++) set(xa, y, z, "air");
        int rx1 = leftFree ? xa - 1 : xa;
        // murs pleins (interieur vide inutile : boite pleine, facades travaillees)
        for (int x = xa; x <= xb; x++)
            for (int z = zA; z <= zB; z++)
                for (int y = y0 + 1; y <= y0 + h; y++) {
                    boolean shell = x == xa || x == xb || z == zA || z == zB;
                    if (shell) set(x, y, z, y == y0 + 1 ? "stone_bricks" : wall.at(x, y, z));
                    else set(x, y, z, "air");
                }
        for (int x = xa; x <= xb; x++)
            for (int z = zA; z <= zB; z++) set(x, y0, z, "spruce_planks");
        // cornieres en saillie sur la rue
        int fz = front - dz;
        for (int y = y0 + 1; y <= y0 + h; y++) {
            set(xa, y, fz, trim);
            set(xb, y, fz, trim);
        }
        // bandeau d'etage (maisons hautes)
        if (h >= 8) {
            for (int x = xa + 1; x < xb; x++) set(x, y0 + 5, fz, B.stairs(roof + "_stairs", out.opposite(), true));
        }
        // porte en retrait
        int door = xa + 2 + (int) Math.floorMod(seed, Math.max(1, xb - xa - 4));
        set(door, y0 + 1, front, "air");
        set(door, y0 + 2, front, "air");
        set(door, y0 + 1, front + dz, B.door("dark_oak_door", out, false, (idx & 1) == 0));
        set(door, y0 + 2, front + dz, B.door("dark_oak_door", out, true, (idx & 1) == 0));
        set(door, y0 + 3, fz, B.slab(roof + "_slab", "top"));
        set(door - 1, y0 + 3, fz, B.stairs(roof + "_stairs", out.opposite(), true));
        set(door + 1, y0 + 3, fz, B.stairs(roof + "_stairs", out.opposite(), true));
        set(door, y0, fz, "stone_brick_slab[type=top]");
        // applique a cote de la porte : lanterne pendue sous un corbeau (ou sous le linteau de la fenetre)
        int lx = door + (door + 2 <= xb - 1 ? 2 : -2);
        if (lx > xa && lx < xb) {
            set(lx, y0 + 4, fz, B.stairs("stone_brick_stairs", out.opposite(), true));
            set(lx, y0 + 3, fz, "lantern[hanging=true]");
        }
        // fenetres : 1 de large, en retrait, appui et linteau ; une par travee de 3 hors porte
        for (int lvl = 0; lvl < (h >= 8 ? 2 : 1); lvl++) {
            int wy = y0 + 2 + lvl * 4;
            for (int x = xa + 2; x <= xb - 2; x += 2) {
                if (Math.abs(x - door) <= 1 && lvl == 0) continue;
                set(x, wy, front, "air");
                set(x, wy + 1, front, "air");
                set(x, wy, front + dz, "glass_pane");
                set(x, wy + 1, front + dz, "glass_pane");
                set(x, wy - 1, fz, "smooth_stone_slab[type=top]");
                set(x, wy + 2, fz, B.stairs("stone_brick_stairs", out.opposite(), true));
                if ((x + idx) % 3 == 0) set(x, wy, fz, "potted_red_tulip");
            }
        }
        // toit a deux pans (faitage le long de x), debord de 1 sur les 4 cotes
        int depthIn = zB - zA + 1;
        int rise = (depthIn + 2) / 2;
        for (int k = 0; k < rise; k++) {
            int y = y0 + h + 1 + k;
            int za = zA - 1 + k, zb = zB + 1 - k;
            for (int x = rx1; x <= xb + 1; x++) {
                if (za < zb) {
                    set(x, y, za, B.stairs(roof + "_stairs", Dir.SOUTH, false));
                    set(x, y, zb, B.stairs(roof + "_stairs", Dir.NORTH, false));
                    for (int z = za + 1; z < zb; z++) if (x >= xa && x <= xb) set(x, y, z, wall.at(x, y, z));
                } else if (za == zb) {
                    set(x, y, za, B.slab(roof + "_slab", "bottom"));
                }
            }
        }
        // sous-face du debord et faitage
        for (int x = rx1; x <= xb + 1; x++) {
            set(x, y0 + h, zA - 1, B.stairs(roof + "_stairs", Dir.NORTH, true));
            set(x, y0 + h, zB + 1, B.stairs(roof + "_stairs", Dir.SOUTH, true));
        }
        if (depthIn % 2 == 0) {
            int y = y0 + h + rise;
            for (int x = rx1; x <= xb + 1; x++) set(x, y, (zA + zB) / 2, B.slab(roof + "_slab", "bottom"));
        }
        // cheminee sur une maison sur deux, climatiseur sur les autres
        if (idx % 2 == 0) {
            int cx = xb - 1, cz = back;
            for (int y = y0 + h + 1; y <= y0 + h + rise + 2; y++) set(cx, y, cz, "bricks");
            set(cx, y0 + h + rise + 3, cz, "brick_slab[type=bottom]");
        } else {
            set(xb - 1, y0 + 4, fz, "light_gray_concrete");
            set(xb - 1, y0 + 3, fz, B.trapdoor("iron_trapdoor", out, true, false));
        }
        // facade arriere : descentes d'eau en saillie aux angles, fenetre en retrait avec appui
        int bz = back + dz;
        for (int y = y0 + 1; y <= y0 + h; y++) {
            set(xa, y, bz, "andesite_wall");
            set(xb, y, bz, "andesite_wall");
        }
        int wx = (xa + xb) / 2;
        set(wx, y0 + 3, back, "air");
        set(wx, y0 + 3, back - dz, "glass_pane");
        set(wx, y0 + 2, bz, "smooth_stone_slab[type=top]");
        set(wx, y0 + 4, bz, B.stairs("stone_brick_stairs", out, true));
        // cour arriere : muret et portillon
        int yz = back + dz;
        for (int x = xa; x <= xb; x++) set(x, y0 + 1, back + dz * 3, "cobblestone_wall");
        set(xa + 1, y0 + 1, yz, "barrel[facing=up]");
    }

    private void checkBilles() {
        for (int k = 0; k < 8; k++) {
            int px = bPairX(k);
            for (int z : new int[]{-2, 2})
                for (int y = FY + 1; y <= FY + 2; y++)
                    if (!air(px, y, z)) warnings.add("billes : place de la paire " + k + " encombree");
        }
    }

    // =================================================================== pont de verre

    void pont() {
        int x1 = PX - 43, x2 = PX + 41, z1 = -23, z2 = 23;
        int y0 = P_BOTTOM;
        int y1 = FY + 42;
        Palette floor = Palette.of(5401, "blackstone", 80, "basalt", 20).period(9);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, y0 - 1, z, "deepslate");
                set(x, y0, z, floor);
            }
        Palette panel = Palette.of(5402, "deepslate_tiles", 80, "deepslate_bricks", 20).period(12);
        int[] pilA = steps(z1, z2, 6, 2);
        int[] pilB = steps(x1, x2, 6, 1);
        String lamp = "soul_lantern[hanging=false]";
        panelWall(Frame.west(x1 - 1), z1, z2, y0, y1, panel, "polished_blackstone", "polished_blackstone_bricks", "blackstone",
                "polished_blackstone_brick_stairs", pilA, lamp, "iron_bars");
        panelWall(Frame.east(x2 + 1), z1, z2, y0, y1, panel, "polished_blackstone", "polished_blackstone_bricks", "blackstone",
                "polished_blackstone_brick_stairs", pilA, lamp, "iron_bars");
        panelWall(Frame.north(z1 - 1), x1, x2, y0, y1, panel, "polished_blackstone", "polished_blackstone_bricks", "blackstone",
                "polished_blackstone_brick_stairs", pilB, lamp, "iron_bars");
        panelWall(Frame.south(z2 + 1), x1, x2, y0, y1, panel, "polished_blackstone", "polished_blackstone_bricks", "blackstone",
                "polished_blackstone_brick_stairs", pilB, lamp, "iron_bars");
        cornerFill(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y0, y1, "polished_blackstone");
        // plafond a caissons : poutres tous les 6, projecteurs au-dessus du pont
        int cy = y1 + 2;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean beamX = Math.floorMod(x - x1, 6) == 0;
                boolean beamZ = Math.floorMod(z - z1, 6) == 0;
                set(x, cy, z, "deepslate_tiles");
                if (beamX || beamZ) set(x, cy - 1, z, "polished_blackstone");
                boolean spot = Math.abs(z) <= 4 && !beamX && !beamZ && Math.floorMod(x - x1, 6) == 3 && Math.floorMod(z - z1, 6) == 3;
                if (spot) set(x, cy, z, "sea_lantern");
            }
        // quais
        quay(P_START1, P_START2, true);
        quay(P_END1, P_END2, false);
        // dalles (toutes en verre a la construction ; le jeu tire les cotes trempes a chaque partie)
        for (int r = 0; r < P_ROWS; r++) {
            int x = pRowX(r);
            for (int dx = 0; dx <= 1; dx++)
                for (int dz : new int[]{-3, -2, 2, 3}) set(x + dx, PB, dz, "glass");
        }
        // cables suspendus avec lanternes entre les rangees
        for (int r = 0; r <= P_ROWS; r += 3) {
            int x = pRowX(r) - 1;
            for (int z : new int[]{-5, 5}) {
                // cable accroche au plafond (y cy) : traverse la poutre basse seulement si elle est absente
                for (int y = PB + 5; y < cy; y++) if (y < cy - 1 || air(x, y, z)) set(x, y, z, "iron_chain[axis=y]");
                set(x, PB + 4, z, "soul_lantern[hanging=true]");
            }
        }
        vipGallery(z1);
        // lueurs au fond du gouffre
        for (int x = x1 + 6; x <= x2 - 6; x += 9) set(x, y0 + 1, (x % 2 == 0) ? -6 : 6, "soul_lantern[hanging=false]");
        checkPont();
    }

    /** Quai de depart ou d'arrivee : platelage, liseré dore, poteaux jusqu'au fond, rambardes laterales, porte au mur. */
    private void quay(int xa, int xb, boolean start) {
        for (int x = xa; x <= xb; x++)
            for (int z = -6; z <= 6; z++) {
                boolean rim = Math.abs(z) == 6 || x == (start ? xb : xa);
                set(x, PB, z, rim ? "polished_blackstone_bricks" : "dark_oak_planks");
                set(x, PB - 1, z, Math.abs(z) == 6 ? B.stairs("polished_blackstone_brick_stairs", z < 0 ? Dir.SOUTH : Dir.NORTH, true) : "stripped_dark_oak_log[axis=x]");
            }
        int edge = start ? xb : xa;
        for (int z = -5; z <= 5; z++) set(edge, PB, z, "gold_block");
        for (int x = xa; x <= xb; x++) {
            set(x, PB + 1, -7, "iron_bars");
            set(x, PB + 1, 7, "iron_bars");
            set(x, PB, -7, "polished_blackstone");
            set(x, PB, 7, "polished_blackstone");
        }
        for (int x = xa + 1; x <= xb; x += 5)
            for (int z : new int[]{-6, 6})
                for (int y = P_BOTTOM + 1; y < PB - 1; y++) set(x, y, z, (y - P_BOTTOM) % 10 == 0 ? "chiseled_polished_blackstone" : "polished_basalt[axis=y]");
        int wx = start ? xa - 1 : xb + 1;
        Dir in = start ? Dir.EAST : Dir.WEST;
        for (int dz = -1; dz <= 1; dz += 2) {
            set(wx, PB + 1, dz == -1 ? 0 : 1, B.door("iron_door", in, false, dz == 1));
            set(wx, PB + 2, dz == -1 ? 0 : 1, B.door("iron_door", in, true, dz == 1));
        }
        for (int z = -1; z <= 2; z++) set(wx + in.dx, PB + 3, z, B.stairs("polished_blackstone_brick_stairs", in.opposite(), true));
        // lanternes posees sur les extremites du linteau (avant : a cote de lui, dans le vide)
        set(wx + in.dx, PB + 4, -1, "soul_lantern[hanging=false]");
        set(wx + in.dx, PB + 4, 2, "soul_lantern[hanging=false]");
        // numeros de passage : 16 petites plaques dorees sur le quai de depart
        if (start) {
            for (int i = 0; i < 8; i++) {
                set(xa + 1 + i, PB, -4, i % 2 == 0 ? "gold_block" : "polished_blackstone_bricks");
                set(xa + 1 + i, PB, 4, i % 2 == 1 ? "gold_block" : "polished_blackstone_bricks");
            }
        }
    }

    /** Galerie VIP contre le mur nord : trois gradins, vitre, banquettes rouges, convives aux masques dores. */
    private void vipGallery(int z1) {
        int xa = P_XB - 2, xb = P_END1 + 1;
        int base = PB + 3;
        Random r = new Random(5404);
        for (int t = 0; t < 3; t++) {
            int y = base + t;
            int za = z1 + (2 - t) * 2, zb = za + 1;
            for (int x = xa; x <= xb; x++)
                for (int z = z1; z <= zb; z++) {
                    if (z < za) continue;
                    set(x, y, z, "dark_oak_planks");
                    for (int yy = base - 1; yy < y; yy++) set(x, yy, z, "polished_blackstone");
                }
            for (int x = xa + 1; x < xb; x++) {
                int zs = za;
                // bougies entre les banquettes, jamais devant ni derriere une banquette
                set(x, y + 1, zb, Math.floorMod(x + t, 8) == 2 ? "candle[candles=3,lit=true]" : "red_carpet");
                if (Math.floorMod(x + t, 4) == 0) {
                    set(x, y + 1, zs, B.stairs("red_nether_brick_stairs", Dir.NORTH, false));
                    if (r.nextInt(3) > 0) {
                        set(x, y + 1, zs + 1, "black_concrete");
                        set(x, y + 2, zs + 1, r.nextInt(4) == 0 ? "raw_gold_block" : "gold_block");
                    }
                }
            }
        }
        int fz = z1 + 6;
        for (int x = xa; x <= xb; x++) {
            set(x, base, fz, "polished_blackstone_bricks");
            set(x, base + 1, fz, "black_stained_glass_pane");
            set(x, base + 2, fz, "black_stained_glass_pane");
            set(x, base + 3, fz, "polished_blackstone_brick_slab[type=bottom]");
            set(x, base - 1, fz, B.stairs("polished_blackstone_brick_stairs", Dir.NORTH, true));
            set(x, base + 9, fz, B.stairs("polished_blackstone_brick_stairs", Dir.NORTH, true));
            for (int z = z1; z <= fz; z++) set(x, base + 10, z, "deepslate_tiles");
        }
        for (int x = xa; x <= xb; x += 6) {
            for (int y = base; y <= base + 9; y++) set(x, y, fz, "polished_blackstone");
            set(x, base + 9, fz - 2, "lantern[hanging=true]");
        }
        for (int x = xa + 3; x <= xb; x += 6) set(x, base + 9, fz - 3, "lantern[hanging=true]");
    }

    private void checkPont() {
        for (int r = 0; r < P_ROWS; r++) {
            int x = pRowX(r);
            for (int z : new int[]{-3, 2})
                for (int y = PB + 1; y <= PB + 3; y++)
                    if (!air(x, y, z)) warnings.add("pont : dalle " + r + " encombree au-dessus");
            for (int y = P_FALL; y < PB; y++)
                if (!air(x, y, -3) || !air(x, y, 3)) {
                    warnings.add("pont : quelque chose sous la dalle " + r);
                    break;
                }
        }
        for (int y = PB + 1; y <= PB + 2; y++)
            if (!air(P_START1 + 3, y, 0) || !air(P_END1 + 3, y, 0)) warnings.add("pont : quai encombre");
    }

    // =================================================================== finale

    void finale() {
        int x1 = FX - 25, x2 = FX + 25, z1 = -31, z2 = 31;
        int y0 = FY;
        Palette clay = Palette.of(5501, "coarse_dirt", 40, "packed_mud", 25, "dirt", 22, "rooted_dirt", 13).period(5);
        Palette margin = Palette.of(5502, "gravel", 45, "coarse_dirt", 30, "andesite", 25).period(4);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                fill(x, y0 - 3, z, x, y0 - 1, z, "dirt");
                boolean inArena = Math.abs(x - FX) <= F_ARENA_HW + 1 && Math.abs(z) <= F_ARENA_HZ + 1;
                set(x, y0, z, inArena ? clay.at(x, y0, z) : margin.at(x, y0, z));
            }
        // craie : contour du calamar, taille, cible de la tete, bordure de l'aire
        for (int x = FX - F_ARENA_HW - 1; x <= FX + F_ARENA_HW + 1; x++)
            for (int z = -F_ARENA_HZ - 1; z <= F_ARENA_HZ + 1; z++) {
                boolean border = Math.abs(x - FX) == F_ARENA_HW + 1 || Math.abs(z) == F_ARENA_HZ + 1;
                boolean in = inSquid(x, z);
                boolean edge = in && (!inSquid(x + 1, z) || !inSquid(x - 1, z) || !inSquid(x, z + 1) || !inSquid(x, z - 1));
                boolean waist = z == F_WAIST && Math.abs(x - FX) <= F_BODY_HW;
                double dh = Math.hypot(x - FX, z - F_HEADZ);
                boolean target = dh >= 2.6 && dh <= 3.4;
                if (border || edge || waist) set(x, y0, z, "white_concrete");
                else if (target) set(x, y0, z, "calcite");
            }
        // marque de depart de l'attaquant
        for (int x = FX - 1; x <= FX + 1; x++)
            for (int z = 20; z <= 22; z++) if (Math.abs(x - FX) == 1 || z != 21) set(x, y0, z, "white_concrete");
        int[] pilNS = steps(x1, x2, 8, 1);
        int[] pilWE = steps(z1, z2, 8, 3);
        skyWall(Frame.west(x1 - 1), z1, z2, y0, 24, 81, DUSK, pilWE);
        skyWall(Frame.east(x2 + 1), z1, z2, y0, 24, 82, DUSK, pilWE);
        skyWall(Frame.north(z1 - 1), x1, x2, y0, 24, 83, DUSK, pilNS);
        skyWall(Frame.south(z2 + 1), x1, x2, y0, 24, 84, DUSK, pilNS);
        corners(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y0, y0 + 24);
        // estrades des gardes (ouest et est)
        for (int side = -1; side <= 1; side += 2) {
            int xe = FX + side * 23;
            for (int z = -12; z <= 12; z++)
                for (int dx = -1; dx <= 1; dx++) {
                    int x = xe + dx;
                    for (int y = y0 + 1; y <= y0 + 2; y++) set(x, y, z, Math.abs(z) % 6 == 0 ? "polished_blackstone" : "pink_terracotta");
                    set(x, y0 + 3, z, Math.abs(z) == 12 ? "smooth_quartz" : "cherry_planks");
                }
            for (int z = -12; z <= 12; z++) set(xe - side * 2, y0 + 3, z, B.stairs("cherry_stairs", side < 0 ? Dir.WEST : Dir.EAST, true));
            for (int z = -12; z <= 12; z++) set(xe + side, y0 + 4, z, "cherry_fence");
            for (int z = -12; z <= 12; z += 6) set(xe + side, y0 + 5, z, "lantern[hanging=false]");
            // contreforts en saillie des deux cotes tous les 6, marches au sud
            for (int z = -12; z <= 12; z += 6)
                for (int y = y0 + 1; y <= y0 + 2; y++) {
                    set(xe - 2, y, z, "polished_blackstone");
                    set(xe + 2, y, z, "polished_blackstone");
                }
            for (int z = -9; z <= 9; z += 6) {
                set(xe - 2, y0 + 1, z, B.stairs("polished_blackstone_stairs", Dir.EAST, false));
                set(xe + 2, y0 + 1, z, B.stairs("polished_blackstone_stairs", Dir.WEST, false));
            }
            for (int k = 0; k < 3; k++) {
                set(xe, y0 + 3 - k, 13 + k, B.stairs("cherry_stairs", Dir.NORTH, false));
                for (int y = y0 + 1; y < y0 + 3 - k; y++) set(xe, y, 13 + k, "pink_terracotta");
            }
        }
        // touffes seches dans les marges (hors aire de jeu)
        Noise veg = new Noise(5503);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (Math.abs(x - FX) <= F_ARENA_HW + 1 && Math.abs(z) <= F_ARENA_HZ + 1) continue;
                if (!air(x, y0 + 1, z)) continue;
                double u = veg.uniform2(x / 2.5, z / 2.5);
                if (u > 0.84) set(x, y0 + 1, z, "short_dry_grass");
                else if (u > 0.78) set(x, y0 + 1, z, "dead_bush");
                else if (u < 0.05) set(x, y0 + 1, z, "leaf_litter[facing=north,segment_amount=2]");
            }
        // lampes au sol au bout des traits
        for (int x : new int[]{FX - 22, FX + 22})
            for (int z : new int[]{-28, 28}) set(x, y0, z, "ochre_froglight");
        escher(x1 + 1, y0 + 1, z1 + 2, Dir.SOUTH, Dir.WEST, 7, "pink_concrete", "cherry_stairs");
        escher(x2 - 1, y0 + 1, z1 + 2, Dir.SOUTH, Dir.EAST, 7, "light_blue_concrete", "warped_stairs");
        checkFinale();
    }

    private void checkFinale() {
        for (int x = FX - F_ARENA_HW; x <= FX + F_ARENA_HW; x++)
            for (int z = -F_ARENA_HZ; z <= F_ARENA_HZ; z++)
                for (int y = FY + 1; y <= FY + 3; y++)
                    if (!air(x, y, z)) {
                        warnings.add("finale : aire de jeu encombree en " + x + " " + y + " " + z);
                        return;
                    }
    }
}
