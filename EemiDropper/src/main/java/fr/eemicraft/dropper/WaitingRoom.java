package fr.eemicraft.dropper;

import fr.eemicraft.dropper.build.B;
import fr.eemicraft.dropper.build.Connect;
import fr.eemicraft.dropper.build.Dir;
import fr.eemicraft.dropper.build.Noise;
import fr.eemicraft.dropper.build.Palette;
import fr.eemicraft.dropper.build.Sink;
import fr.eemicraft.dropper.build.Stairs;

/**
 * Salle d'attente du Dropper : « tete de puits » suspendue dans le vide (sans dependance Bukkit, ecrit dans un Sink).
 * <ul>
 * <li>Plateforme carree (sol jouable |x|,|z| <= 7, 225 m², sol en ry) posee sur un eperon de roche en cone inverse
 *     traverse par le puits (on voit l'eau 15 blocs plus bas a travers le verre central).</li>
 * <li>Galerie a colonnade (poteaux de chene noir, garde-corps en murets, barrieres invisibles derriere eux) sous un
 *     toit en cuivre a 45° avec debord ; nef centrale surelevee (claire-voie vitree) ; lanterneau au sommet.</li>
 * <li>Repere central : margelle et chevalement au-dessus du puits, poulie et chaine.</li>
 * <li>Tableau des regles au nord (face au point d'arrivee), balcon a plancher de verre a l'est.</li>
 * </ul>
 * Contrat : point d'arrivee (0.5, ry+1, 5.5) libre sur 3 de haut ; aucune sortie possible a pied.
 */
public final class WaitingRoom {
    /** A monter a chaque changement de la salle (force la reconstruction). */
    public static final int VERSION = 2;

    /** Panneaux des regles : {x, dy, z} (dy relatif au sol). Le premier est le titre. Face au sud. */
    public static final int[][] SIGNS = {{0, 3, -7}, {-1, 2, -7}, {0, 2, -7}, {1, 2, -7}};
    /** Texte des panneaux (codes & ; 4 lignes de 15 caracteres environ). */
    public static final String[][] SIGN_TEXT = {
            {"", "&3&lDROPPER", "&85 puits", "&8a la suite"},
            {"&3&lSaute !", "&0Tombe sur", "&0l'arrivee :", "&0eau, slime..."},
            {"&3&lRate ?", "&0Toucher autre", "&0chose = retour", "&0au depart"},
            {"&3&lGagne", "&0Le premier", "&0a finir les", "&05 puits"}};

    /** Boite a vider avant de construire (tient dans les chunks -1..0 forces). */
    public static final int CX1 = -16, CX2 = 15, CZ1 = -16, CZ2 = 15, CDY1 = -26, CDY2 = 26;

    private final Sink s;
    private final int ry;
    private final Noise noise = new Noise(0xD20FFL);

    private final Palette stone = Palette.of(11, "minecraft:stone_bricks", 80, "minecraft:cracked_stone_bricks", 12,
            "minecraft:mossy_stone_bricks", 8).period(9).amp(0.2);
    private final Palette floor = Palette.of(12, "minecraft:polished_andesite", 84, "minecraft:andesite", 16).period(8).amp(0.2);
    private final Palette rock = Palette.of(13, "minecraft:stone", 45, "minecraft:deepslate", 35,
            "minecraft:dripstone_block", 20).period(6).amp(0.12);
    private final Palette copper = Palette.of(14, "minecraft:waxed_exposed_cut_copper_stairs", 30,
            "minecraft:waxed_weathered_cut_copper_stairs", 40, "minecraft:waxed_oxidized_cut_copper_stairs", 30).period(5).amp(0.18);

    private static final String AIR = "minecraft:air";
    private static final String BARRIER = "minecraft:barrier";
    private static final String PLINTH = "minecraft:polished_deepslate";
    private static final String POST = "minecraft:stripped_spruce_log";
    private static final String BEAM = "minecraft:dark_oak_log";
    private static final String KNOT = "minecraft:dark_oak_log[axis=y]";
    private static final String RAIL = "minecraft:polished_deepslate_wall";
    private static final String SOFFIT = "minecraft:dark_oak_stairs";
    private static final String LANTERN_H = "minecraft:lantern[hanging=true]";
    private static final String CHAIN = "minecraft:iron_chain[axis=y]";

    private WaitingRoom(Sink s, int ry) {
        this.s = s;
        this.ry = ry;
    }

    /** Construit la salle. {@code clear} : vide d'abord la boite CX/CZ/CDY (ancienne salle comprise). */
    public static void build(Sink s, int ry, boolean clear) {
        WaitingRoom w = new WaitingRoom(s, ry);
        if (clear) {
            for (int x = CX1; x <= CX2; x++)
                for (int y = ry + CDY1; y <= ry + CDY2; y++)
                    for (int z = CZ1; z <= CZ2; z++) s.set(x, y, z, AIR);
        }
        w.rock();
        w.deck();
        w.shaft();
        w.balcony();
        w.caveVines();
        w.colonnade();
        w.galleryRoof();
        w.nave();
        w.upperRoof();
        w.lantern();
        w.headframe();
        w.board();
        w.furniture();
        w.lights();
        Stairs.fix(s, -13, ry - 26, -13, 13, ry + 26, 13);
        Connect.fix(s, -13, ry - 2, -13, 13, ry + 26, 13);
    }

    // ------------------------------------------------------------------ outils

    static int m(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z));
    }

    /** Direction vers l'exterieur de l'anneau carre (axe dominant, x en cas d'egalite). */
    static Dir out(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) return x >= 0 ? Dir.EAST : Dir.WEST;
        return z >= 0 ? Dir.SOUTH : Dir.NORTH;
    }

    /** Axe de la ligne (cote de l'anneau) qui passe par (x,z). */
    static char along(int x, int z) {
        return Math.abs(x) >= Math.abs(z) ? 'z' : 'x';
    }

    private void set(int x, int dy, int z, String st) {
        s.set(x, ry + dy, z, st);
    }

    private double h01(int x, int y, int z) {
        return Noise.hash(0xD20FFL, x, y, z);
    }

    // ------------------------------------------------------------------ eperon de roche

    private void rock() {
        // deux rangs de structure carres sous le plancher (encorbellement), puis le cone de roche
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                int mm = m(x, z);
                if (mm <= 8) set(x, -1, z, mm == 8 ? PLINTH : "minecraft:stone");
                if (mm == 7) set(x, -2, z, B.stairs("minecraft:deepslate_tile_stairs", out(x, z).opposite(), true));
                else if (mm < 7) set(x, -2, z, "minecraft:stone");
            }
        int top = -3, depth = 21;
        for (int d = 0; d <= depth; d++) {
            double t = d / (double) depth;
            double r = 8.4 * Math.pow(1 - t, 0.8) + 0.4;
            int dy = top - d;
            for (int x = -11; x <= 11; x++)
                for (int z = -11; z <= 11; z++) {
                    double[] w = noise.warp2(x, z, 2.0, 9);
                    double dist = Math.hypot(w[0], w[1]);
                    double rr = r + 1.4 * noise.noise3(x / 5.0, (ry + dy) / 4.0, z / 5.0);
                    if (dist <= rr) set(x, dy, z, rock.pick(x, ry + dy, z, Math.min(1, t * 1.1)));
                }
        }
        // stalactites sous le cone
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                if (h01(x, 7, z) > 0.10) continue;
                for (int dy = -4; dy >= -24; dy--) {
                    if (B.isAir(s.get(x, ry + dy, z)) && !B.isAir(s.get(x, ry + dy + 1, z))) {
                        if (dy > -6) break; // pas sous la structure
                        int len = 1 + (int) (h01(x, 9, z) * 4);
                        for (int i = 0; i < len; i++) {
                            String th = i == len - 1 ? "tip" : i == len - 2 ? "frustum" : i == 0 ? "base" : "middle";
                            set(x, dy - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" + th + "]");
                        }
                        break;
                    }
                }
            }
    }

    /** Lianes a baies lumineuses sous l'eperon et sous la levre du plancher, en grappes. */
    private void caveVines() {
        for (int x = -11; x <= 12; x++)
            for (int z = -11; z <= 11; z++) {
                if (noise.uniform2(x / 3.5 + 20, z / 3.5 - 7) < 0.72) continue;
                for (int dy = 0; dy >= -24; dy--) {
                    if (!B.isAir(s.get(x, ry + dy - 1, z)) || B.isAir(s.get(x, ry + dy, z))) continue;
                    String above = s.get(x, ry + dy, z);
                    if (above.contains("pointed_dripstone") || above.contains("glass") || above.contains("water")) break;
                    int len = 2 + (int) (h01(x, 11, z) * 3);
                    for (int i = 1; i <= len; i++) {
                        boolean last = i == len;
                        boolean berries = h01(x, dy - i, z) < 0.45;
                        set(x, dy - i, z, (last ? "minecraft:cave_vines[age=25,berries=" : "minecraft:cave_vines_plant[berries=") + berries + "]");
                    }
                    break;
                }
            }
    }

    // ------------------------------------------------------------------ plancher

    private void deck() {
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                int mm = m(x, z);
                String st;
                if (mm == 9) st = B.stairs("minecraft:deepslate_tile_stairs", out(x, z).opposite(), true);
                else if (mm == 8) st = PLINTH;
                else if (mm <= 1) st = "minecraft:glass";
                else if (mm == 2) st = PLINTH;
                else if (mm == 3) st = "minecraft:calcite"; // halo clair autour du puits
                else if (x == 0 || z == 0) st = "minecraft:calcite"; // allees vers les 4 ouvertures
                else st = floor.at(x, ry, z);
                set(x, 0, z, st);
            }
    }

    // ------------------------------------------------------------------ puits

    private void shaft() {
        for (int dy = -1; dy >= -16; dy--)
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++) {
                    if (m(x, z) == 2) {
                        boolean ring = (dy % 4 == -3) && (x == 0 || z == 0);
                        String st = ring ? "minecraft:sea_lantern" : dy > -9 ? "minecraft:prismarine_bricks" : "minecraft:dark_prismarine";
                        set(x, dy, z, st);
                    } else if (dy >= -13) set(x, dy, z, AIR);
                    else if (dy >= -15) set(x, dy, z, "minecraft:water");
                    else set(x, dy, z, "minecraft:sea_lantern");
                }
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) set(x, -17, z, "minecraft:prismarine_bricks");
    }

    // ------------------------------------------------------------------ balcon est

    private void balcony() {
        for (int x = 9; x <= 12; x++)
            for (int z = -3; z <= 3; z++) {
                boolean edge = x == 12 || Math.abs(z) == 3;
                boolean glass = x >= 10 && x <= 11 && Math.abs(z) <= 1;
                set(x, 0, z, edge ? PLINTH : glass ? "minecraft:glass" : "minecraft:dark_oak_planks");
                if (edge) {
                    Dir in = x == 12 ? Dir.WEST : z > 0 ? Dir.NORTH : Dir.SOUTH;
                    set(x, -1, z, B.stairs("minecraft:deepslate_tile_stairs", in, true));
                    set(x, 1, z, RAIL);
                    set(x, 2, z, BARRIER);
                    set(x, 3, z, BARRIER);
                } else {
                    set(x, -1, z, glass ? AIR : "minecraft:dark_oak_slab[type=top]");
                }
            }
        // lanternes aux deux angles avant, contrefiches en diagonale sous les bords
        set(12, 2, -3, "minecraft:lantern");
        set(12, 2, 3, "minecraft:lantern");
        for (int k = 0; k < 3; k++) {
            set(11 - k, -2 - k, -3, B.stairs("minecraft:deepslate_tile_stairs", Dir.WEST, true));
            set(11 - k, -2 - k, 3, B.stairs("minecraft:deepslate_tile_stairs", Dir.WEST, true));
        }
    }

    // ------------------------------------------------------------------ colonnade exterieure (anneau 8)

    static boolean outerColumn(int x, int z) {
        if (m(x, z) != 8) return false;
        int a = along(x, z) == 'z' ? z : x; // coordonnee le long du cote
        if (Math.abs(x) == 8 && Math.abs(z) == 8) return true;
        return a == -8 || a == -3 || a == 3 || a == 8;
    }

    static boolean boardCell(int x, int z) {
        return z == -8 && Math.abs(x) <= 2;
    }

    static boolean balconyOpening(int x, int z) {
        return x == 8 && Math.abs(z) <= 2;
    }

    private void colonnade() {
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                if (m(x, z) != 8) continue;
                char ax = along(x, z);
                boolean corner = Math.abs(x) == 8 && Math.abs(z) == 8;
                if (outerColumn(x, z)) {
                    set(x, 1, z, PLINTH);
                    set(x, 2, z, B.axis(POST, 'y'));
                    set(x, 3, z, B.axis(POST, 'y'));
                    set(x, 4, z, KNOT);
                    continue;
                }
                set(x, 4, z, corner ? KNOT : B.axis(BEAM, ax));
                if (boardCell(x, z)) continue; // tableau des regles
                if (balconyOpening(x, z)) continue; // passage vers le balcon
                set(x, 1, z, RAIL);
                set(x, 2, z, BARRIER);
                set(x, 3, z, BARRIER);
            }
        // corbeaux sous la poutre de part et d'autre des colonnes (pas dans le tableau)
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                if (m(x, z) != 8 || outerColumn(x, z) || boardCell(x, z)) continue;
                char ax = along(x, z);
                int dx = ax == 'x' ? 1 : 0, dz = ax == 'z' ? 1 : 0;
                if (outerColumn(x + dx, z + dz)) set(x, 3, z, B.stairs(SOFFIT, Dir.fromDelta(dx, dz), true));
                else if (outerColumn(x - dx, z - dz)) set(x, 3, z, B.stairs(SOFFIT, Dir.fromDelta(-dx, -dz), true));
            }
    }

    // ------------------------------------------------------------------ toits

    /** Toit en escaliers de cuivre (montant vers le centre) + sous-face en chene noir. */
    private void roofCell(int x, int z, int dy, double t, boolean soffit) {
        Dir in = out(x, z).opposite();
        set(x, dy, z, B.stairs(copper.pick(x, ry + dy, z, t), in, false));
        if (soffit) set(x, dy - 1, z, B.stairs(SOFFIT, in, true));
    }

    private void galleryRoof() {
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                int mm = m(x, z);
                if (mm < 6) continue;
                int dy = 14 - mm; // 9 -> +5 ... 6 -> +8
                roofCell(x, z, dy, (mm - 6) / 3.0 * 0.8 + 0.2, mm != 9); // egout (9) : debord sans sous-face
            }
    }

    /** Nef : colonnes interieures (anneau 5), poutre, claire-voie vitree en 3 plans. */
    private void nave() {
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                if (m(x, z) != 5) continue;
                boolean corner = Math.abs(x) == 5 && Math.abs(z) == 5;
                char ax = along(x, z);
                int a = ax == 'z' ? z : x;
                Dir o = out(x, z);
                if (corner) {
                    set(x, 1, z, PLINTH);
                    for (int dy = 2; dy <= 4; dy++) set(x, dy, z, B.axis(POST, 'y'));
                    set(x, 5, z, KNOT);
                } else {
                    set(x, 5, z, B.axis(BEAM, ax));
                }
                boolean post = corner || a == -2 || a == 2;
                int ix = x - o.dx, iz = z - o.dz; // plan interieur (anneau 4)
                // plan P : poteaux (anneau 5) ; plan P-1 : remplissage et vitres en retrait (anneau 4)
                for (int dy = 6; dy <= 12; dy++) {
                    if (post) {
                        set(x, dy, z, corner ? KNOT : B.axis(POST, 'y'));
                        if (!corner && dy >= 9 && dy <= 10) set(ix, dy, iz, B.axis(POST, 'y'));
                    } else {
                        set(x, dy, z, AIR);
                        if (dy == 9 || dy == 10) set(ix, dy, iz, "minecraft:glass_pane");
                        else if (dy == 11) set(ix, dy, iz, B.stairs("minecraft:stone_brick_stairs", o.opposite(), true)); // linteau
                        else if (dy == 8) set(ix, dy, iz, B.stairs("minecraft:stone_brick_stairs", o.opposite(), false)); // appui
                        else set(ix, dy, iz, stone.at(ix, ry + dy, iz));
                    }
                }
                if (!corner && !post) set(ix, 5, iz, B.stairs(SOFFIT, o, true)); // corniche interieure sous le mur
            }
        // corbeaux sous la poutre interieure
        int[][] c = {{-5, -5}, {5, -5}, {5, 5}, {-5, 5}};
        for (int[] p : c) {
            int sx = Integer.signum(p[0]), sz = Integer.signum(p[1]);
            set(p[0] - sx, 4, p[1], B.stairs(SOFFIT, Dir.fromDelta(sx, 0), true));
            set(p[0], 4, p[1] - sz, B.stairs(SOFFIT, Dir.fromDelta(0, sz), true));
        }
    }

    private void upperRoof() {
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                int mm = m(x, z);
                int dy = 18 - mm; // 6 -> +12 ... 0 -> +18
                double t = mm / 6.0 * 0.85;
                if (mm == 0) {
                    set(x, dy, z, "minecraft:waxed_exposed_cut_copper");
                    set(x, dy - 1, z, "minecraft:dark_oak_planks");
                } else {
                    roofCell(x, z, dy, t, mm != 5 && mm != 6); // 5 : sur le mur ; 6 : debord
                }
            }
    }

    /** Lanterneau au faite : 4 poteaux, lanterne, petit toit en pointe. */
    private void lantern() {
        for (int x = -1; x <= 1; x += 2)
            for (int z = -1; z <= 1; z += 2)
                for (int dy = 18; dy <= 20; dy++) set(x, dy, z, "minecraft:dark_oak_fence");
        set(0, 19, 0, "minecraft:lantern");
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                int mm = m(x, z);
                Dir in = out(x, z).opposite();
                if (mm == 2) {
                    set(x, 21, z, B.stairs(copper.pick(x, ry + 21, z, 0.3), in, false));
                    set(x, 20, z, B.stairs(SOFFIT, in, true));
                } else if (mm == 1) {
                    set(x, 21, z, "minecraft:dark_oak_planks");
                    set(x, 22, z, B.stairs(copper.pick(x, ry + 22, z, 0.1), in, false));
                } else {
                    set(x, 21, z, "minecraft:dark_oak_planks");
                    set(x, 22, z, "minecraft:dark_oak_planks");
                    set(x, 23, z, "minecraft:waxed_exposed_cut_copper");
                    set(x, 24, z, "minecraft:lightning_rod");
                }
            }
    }

    // ------------------------------------------------------------------ repere central

    private void headframe() {
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                if (m(x, z) != 2) continue;
                boolean leg = Math.abs(x) == 2 && Math.abs(z) == 2;
                if (leg) {
                    set(x, 1, z, PLINTH);
                    for (int dy = 2; dy <= 4; dy++) set(x, dy, z, B.axis(POST, 'y'));
                    set(x, 5, z, KNOT);
                } else {
                    set(x, 1, z, "minecraft:deepslate_tile_slab[type=bottom]"); // margelle (on monte dessus)
                    set(x, 5, z, B.axis(BEAM, along(x, z)));
                }
            }
        // traverse centrale, poulie, corde vers le plafond et lanterne au-dessus de l'eau
        for (int x = -1; x <= 1; x++) set(x, 5, 0, B.axis(BEAM, 'x'));
        set(0, 6, 0, "minecraft:grindstone[face=floor,facing=east]");
        for (int dy = 7; dy <= 16; dy++) set(0, dy, 0, CHAIN);
        // lanternes de part et d'autre de l'axe (le regard du point d'arrivee vers le tableau reste libre)
        for (int x = -1; x <= 1; x += 2) {
            set(x, 4, 0, CHAIN);
            set(x, 3, 0, LANTERN_H);
        }
        // corbeaux sous les traverses
        int[][] c = {{-2, -2}, {2, -2}, {2, 2}, {-2, 2}};
        for (int[] p : c) {
            int sx = Integer.signum(p[0]), sz = Integer.signum(p[1]);
            set(p[0] - sx, 4, p[1], B.stairs(SOFFIT, Dir.fromDelta(sx, 0), true));
            set(p[0], 4, p[1] - sz, B.stairs(SOFFIT, Dir.fromDelta(0, sz), true));
        }
    }

    // ------------------------------------------------------------------ tableau des regles (nord)

    private void board() {
        for (int x = -2; x <= 2; x++) {
            set(x, 1, -8, PLINTH);
            set(x, 2, -8, "minecraft:calcite");
            set(x, 3, -8, "minecraft:calcite");
            set(x, 4, -7, B.stairs(SOFFIT, Dir.NORTH, true)); // auvent
        }
        for (int[] p : SIGNS) set(p[0], p[1], p[2], "minecraft:birch_wall_sign[facing=south]");
        set(-1, 3, -7, B.trapdoor("minecraft:spruce_trapdoor", Dir.SOUTH, true, true));
        set(1, 3, -7, B.trapdoor("minecraft:spruce_trapdoor", Dir.SOUTH, true, true));
        set(-2, 3, -7, LANTERN_H);
        set(2, 3, -7, LANTERN_H);
    }

    // ------------------------------------------------------------------ mobilier et vegetation

    private void bench(int x1, int x2, int z, Dir back) {
        for (int x = x1; x <= x2; x++) set(x, 1, z, B.stairs("minecraft:dark_oak_stairs", back, false));
        set(x1 - 1, 1, z, B.trapdoor("minecraft:spruce_trapdoor", Dir.WEST, false, true));
        set(x2 + 1, 1, z, B.trapdoor("minecraft:spruce_trapdoor", Dir.EAST, false, true));
    }

    private void furniture() {
        bench(-6, -4, 7, Dir.SOUTH);
        bench(3, 5, 7, Dir.SOUTH);
        // banc ouest (le long de x = -7)
        for (int z = 2; z <= 4; z++) set(-7, 1, z, B.stairs("minecraft:dark_oak_stairs", Dir.WEST, false));
        set(-7, 1, 1, B.trapdoor("minecraft:spruce_trapdoor", Dir.NORTH, false, true));
        set(-7, 1, 5, B.trapdoor("minecraft:spruce_trapdoor", Dir.SOUTH, false, true));
        // coin du treuil (nord-ouest) : tonneaux, meule, chaine enroulee
        set(-7, 1, -7, "minecraft:barrel[facing=up]");
        set(-6, 1, -7, "minecraft:barrel[facing=north]");
        set(-7, 1, -6, "minecraft:barrel[facing=west]");
        set(-7, 2, -7, "minecraft:lantern");
        set(-5, 1, -7, "minecraft:dark_oak_slab[type=bottom]");
        set(-7, 1, -5, "minecraft:potted_fern");
        // coins fleuris (sud-est, nord-est)
        set(7, 1, 7, "minecraft:potted_flowering_azalea_bush");
        set(7, 1, 6, "minecraft:potted_azalea_bush");
        set(7, 1, -7, "minecraft:potted_flowering_azalea_bush");
        set(7, 1, -6, "minecraft:spruce_trapdoor[facing=east,half=bottom,open=false]");
        // lierre qui pend du debord du toit, en grappes
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                if (m(x, z) != 9 || (x >= 9 && Math.abs(z) <= 3)) continue;
                if (noise.uniform2(x / 3.0, z / 3.0) < 0.78) continue;
                int len = 1 + (int) (h01(x, 3, z) * 2);
                for (int i = 0; i < len; i++) set(x, 4 - i, z, "minecraft:vine[up=" + (i == 0) + ",north=false,south=false,east=false,west=false]");
            }
        // mousse sur la levre du plancher cote nord-ouest (ombre)
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++)
                if (m(x, z) == 9 && x + z < -6 && noise.uniform2(x / 4.0 + 50, z / 4.0) > 0.55 && B.isAir(s.get(x, ry + 1, z)))
                    set(x, 1, z, "minecraft:moss_carpet");
    }

    // ------------------------------------------------------------------ eclairage

    private void lights() {
        int[][] gallery = {{7, 0}, {0, 7}, {-7, 0}, {-7, -7}, {7, -7}, {7, 7}, {-7, 7}}; // nord : lanternes du tableau
        for (int[] p : gallery) {
            set(p[0], 5, p[1], CHAIN);
            set(p[0], 4, p[1], LANTERN_H);
        }
        int[][] nave = {{5, 0}, {-5, 0}, {0, -5}, {3, 5}, {-3, 5}};
        for (int[] p : nave) set(p[0], 4, p[1], LANTERN_H);
        // complement discret (invisible) dans la nef haute
        int[][] l = {{-3, -3}, {3, -3}, {3, 3}, {-3, 3}};
        for (int[] p : l) set(p[0], 10, p[1], "minecraft:light[level=12]");
    }
}
