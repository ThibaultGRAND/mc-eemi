package fr.eemicraft.casino;

import fr.eemicraft.casino.build.B;
import fr.eemicraft.casino.build.Connect;
import fr.eemicraft.casino.build.Dir;
import fr.eemicraft.casino.build.MemSink;
import fr.eemicraft.casino.build.Noise;
import fr.eemicraft.casino.build.Palette;
import fr.eemicraft.casino.build.Sink;
import fr.eemicraft.casino.build.Stairs;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Salle du casino (monde vide, sol en y 100, salle x/z -29..29, corniche y 112, plafond y 113). Nord = z negatif.
 * Les positions des postes sont dans {@link Layout} ; tout ce qui touche aux entites (machines 3D adossees aux murs
 * nord et est, zones cliquables, croupiers, panneaux flottants) reste a la meme place.
 *
 * <p>Composition : murs en 3 plans (pilastres en saillie, cadres dans le plan, panneaux de velours en retrait),
 * plafond a caissons, rotonde a 8 colonnes de quartz sous une coupole a nervures dorees au-dessus de la roulette
 * (repere central, grand lustre), galerie en mezzanine a l'ouest avec salon dessous et escalier au nord-ouest,
 * bar au nord-est, kiosques couverts pour la caisse et la boutique, portail monumental au sud, tapis par zones.
 *
 * <p>Tout est ecrit dans un tampon ({@link MemSink}) puis pose une seule fois : le builder ne lit jamais le monde.
 */
final class Builder {
    private static final int F = Layout.FLOOR;
    private static final String AIR = "minecraft:air";
    private static final String SHELL = "minecraft:blackstone";
    private static final String PL = "minecraft:dark_oak_planks";
    private static final String LOG_Y = "minecraft:stripped_dark_oak_log[axis=y]";
    private static final String GOLD = "minecraft:gold_block";
    private static final String RED = "minecraft:red_wool";
    private static final String DST = "minecraft:dark_oak_stairs";
    private static final String PBB = "minecraft:polished_blackstone_bricks";
    private static final String CPB = "minecraft:chiseled_polished_blackstone";
    /** Poutres du plafond (x et z) : caissons de 5 entre elles, caisson central au-dessus de la roulette. */
    static final int[] BEAMS = {-27, -21, -15, -9, -3, 3, 9, 15, 21, 27};
    /** Colonnes de la galerie ouest (x -26). */
    static final int[] GAL_COLS = {-23, -17, -11, -5, 1, 7, 13, 19};
    static final int GAL_Z1 = -27, GAL_Z2 = 21, GAL_FLOOR = F + 7;
    static final double DOME_R = 9.5;

    private static final int N = 0, E = 1, S = 2, W = 3;
    private static final int SOCLE = 0, WAIN = 1, LINE = 2, SILL = 3, PANEL = 4, LINTEL = 5, PLANK = 6, FRIEZE = 7, CORNICE = 8;

    private final MemSink s;
    private final boolean models;
    private final Palette stone = Palette.of(11, PBB, 72, "minecraft:polished_blackstone", 28).period(7);
    int cells;

    Builder(Sink target, boolean models) {
        this.s = new MemSink(target, -32, F - 1, -32, 31, F + 21, 31);
        this.models = models;
    }

    void build() {
        shell();
        floor();
        walls();
        ceiling();
        dome();
        rotunda();
        stair();
        gallery();
        bar();
        entrance();
        kiosk(-16, -6);
        kiosk(7, 17);
        backdrop(-27, -20);
        backdrop(21, 28);
        stations();
        decor();
        lights();
        skin();
        Stairs.fix(s, -31, F + 1, -31, 31, F + 20, 31);
        Connect.fix(s, -31, F + 1, -31, 31, F + 20, 31);
        cells = s.flush();
    }

    // ------------------------------------------------------------------ outils

    private void set(int x, int y, int z, String b) {
        s.set(x, y, z, b.indexOf(':') < 0 ? "minecraft:" + b : b);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, b);
    }

    private static String st(String id, Dir f, boolean top) {
        return B.stairs(id, f, top);
    }

    private boolean air(int x, int y, int z) {
        return B.isAir(s.get(x, y, z));
    }

    private static boolean in(int[] a, int v) {
        for (int i : a) if (i == v) return true;
        return false;
    }

    // ------------------------------------------------------------------ gros oeuvre

    /** Masse pleine (murs epais, dalle, toiture) puis volume interieur vide. */
    private void shell() {
        fill(-32, F - 1, -32, 31, F + 21, 31, SHELL);
        fill(-29, F + 1, -29, 29, F + 12, 29, AIR);
    }

    // ------------------------------------------------------------------ sol

    private void floor() {
        for (int x = -29; x <= 29; x++) {
            for (int z = -29; z <= 29; z++) {
                String c = carpet(x, z);
                if (c == null) {
                    set(x, F, z, marble(x, z));
                } else {
                    set(x, F, z, PBB);
                    set(x, F + 1, z, c + "_carpet");
                }
            }
        }
    }

    private static boolean foyer(int x, int z) {
        return z >= 24 && Math.abs(x) <= 5;
    }

    private static boolean barZone(int x, int z) {
        return x >= 24 && z <= -25;
    }

    private static String marble(int x, int z) {
        if (foyer(x, z) && (Math.abs(x) == 5 || z == 24)) return GOLD;
        return ((x + z) & 1) == 0 ? "minecraft:smooth_quartz" : "minecraft:polished_blackstone";
    }

    /** Tapis par zones (null = sol de marbre visible). */
    static String carpet(int x, int z) {
        if (foyer(x, z) || barZone(x, z)) return null;
        double d = Math.hypot(x, z);
        if (d < DOME_R) {
            // rosace sous la coupole : etoile a 8 branches noire cernee d'or, couronne doree
            if (d >= 8.5) return "yellow";
            if (d < 2.5) return "yellow";
            double th = Math.atan2(z, x);
            double f = Math.abs(Math.cos(4 * th));
            double r = 4.0 + 3.6 * f * f * f;
            if (d < r - 1.1) return "black";
            if (d < r) return "yellow";
            return "red";
        }
        if (d < 11.5) return d < 10.5 ? "black" : "yellow";
        // allee centrale (entree -> rotonde)
        if (Math.abs(x) <= 2 && z >= 11 && z <= 23) return Math.abs(x) == 2 ? "yellow" : "black";
        // allee devant les machines a sous
        if (z >= -24 && z <= -21 && x >= -25 && x <= 26) return (z == -24 || z == -21) ? "yellow" : "black";
        // salon sous la galerie
        if (x <= -27 && z >= GAL_Z1 && z <= GAL_Z2) return "black";
        // rangee des machines : un tapis noir devant chaque machine
        if (z <= -25) {
            for (int sx : Layout.SLOT_X) if (x >= sx && x <= sx + 1) return "black";
            return "red";
        }
        // zone du blackjack : treillis noir sur rouge
        if (x >= -25 && x <= -15 && z >= -19 && z <= 15)
            return (Math.floorMod(x + z, 6) == 0 || Math.floorMod(x - z, 6) == 0) ? "black" : "red";
        // salon du poker : treillis rouge sur noir
        if (x >= 14 && x <= 28 && z >= -18 && z <= 10)
            return (Math.floorMod(x + z, 6) == 0 || Math.floorMod(x - z, 6) == 0) ? "red" : "black";
        // champ : rouge a semis de points dores
        return (Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3) ? "yellow" : "red";
    }

    // ------------------------------------------------------------------ murs

    private static int wx(int side, int u, int d) {
        switch (side) {
            case N: return u;
            case E: return 30 - d;
            case S: return u;
            default: return -30 + d;
        }
    }

    private static int wz(int side, int u, int d) {
        switch (side) {
            case N: return -30 + d;
            case E: return u;
            case S: return 30 - d;
            default: return u;
        }
    }

    private static Dir out(int side) {
        return side == N ? Dir.NORTH : side == E ? Dir.EAST : side == S ? Dir.SOUTH : Dir.WEST;
    }

    /** d = 0 plan du mur (+-30), 1 saillie dans la salle (+-29), -1 retrait (+-31), -2 (nord et ouest seulement). */
    private void w(int side, int u, int y, int d, String b) {
        if (b != null) set(wx(side, u, d), y, wz(side, u, d), b);
    }

    /** Une machine 3D est adossee au mur ici (y 101..104) : rien ne doit sortir du plan. */
    private static boolean machineBehind(int side, int u) {
        if (side == N) return u >= -22 && u <= 23;
        if (side == E) return (u >= -25 && u <= -17) || (u >= 11 && u <= 19);
        return false;
    }

    private static boolean lowFree(int side, int u) {
        if (side == E) return !machineBehind(side, u) && u > -25;
        if (side == S) return Math.abs(u) > 5;
        return false;
    }

    private static int zone(int y, boolean west) {
        int r = y - F;
        if (west) {
            switch (r) {
                case 1: return SOCLE;
                case 2: return SILL;
                case 3: case 4: return PANEL;
                case 5: return LINTEL;
                case 6: return PLANK;
                case 7: return LINE;
                // y 108 = pied du mur de la galerie (plancher en 107) : socle plein, sinon l'allege en escalier
                // bas faisait une marche dans le mur (et une 8e marche en haut de l'escalier)
                case 8: return SOCLE;
                case 9: return SILL;
                case 10: return PANEL;
                case 11: return LINTEL;
                default: return CORNICE;
            }
        }
        switch (r) {
            case 1: return SOCLE;
            case 2: case 3: return WAIN;
            case 4: return LINE;
            case 5: return SILL;
            case 6: case 7: case 8: return PANEL;
            case 9: return LINTEL;
            case 10: return PLANK;
            case 11: return FRIEZE;
            default: return CORNICE;
        }
    }

    private void walls() {
        // pilastres pleins / hauts seulement (au-dessus des machines) / reperes sans pilastre (cadres)
        wall(N, new int[]{24, 29}, new int[]{-23, -16, -10, -4, 5, 11, 17}, new int[]{});
        wall(E, new int[]{-29, -26, -14, -8, -2, 4, 10, 22, 28, 29}, new int[]{-20, 16}, new int[]{});
        wall(S, new int[]{-29, -27, -21, -15, -9, 9, 15, 21, 27, 29}, new int[]{}, new int[]{-4, 4});
        wall(W, new int[]{}, new int[]{}, GAL_COLS);
        // niches eclairees entre les machines a sous (vitrail rouge eclaire par l'arriere) et entre les video poker
        for (int i = 0; i + 1 < Layout.SLOT_X.length; i++) {
            int u = Layout.SLOT_X[i] + 2;
            for (int y = F + 2; y <= F + 4; y++) {
                w(N, u, y, 0, AIR);
                w(N, u, y, -1, "red_stained_glass");
                w(N, u, y, -2, "shroomlight");
            }
        }
        for (int u : new int[]{-22, -19, 14, 17}) {
            for (int y = F + 2; y <= F + 4; y++) {
                w(E, u, y, 0, AIR);
                w(E, u, y, -1, "shroomlight");
            }
        }
    }

    private void wall(int side, int[] full, int[] upper, int[] virt) {
        boolean west = side == W;
        TreeSet<Integer> marks = new TreeSet<>();
        for (int v : full) marks.add(v);
        for (int v : upper) marks.add(v);
        for (int v : virt) marks.add(v);
        Dir o = out(side);
        for (int u = -29; u <= 29; u++) {
            boolean mark = marks.contains(u);
            boolean frame = mark || marks.contains(u - 1) || marks.contains(u + 1) || Math.abs(u) == 29;
            boolean behind = machineBehind(side, u);
            boolean jack = side == N && u >= -3 && u <= 4;
            for (int y = F + 1; y <= F + 12; y++) {
                int zn = zone(y, west);
                String d0, dm = null, dp = null;
                int X = wx(side, u, 0), Z = wz(side, u, 0);
                switch (zn) {
                    case SOCLE:
                        d0 = stone.at(X, y, Z);
                        break;
                    case WAIN:
                        if (frame || behind) d0 = stone.at(X, y, Z);
                        else {
                            d0 = AIR;
                            dm = PL;
                        }
                        break;
                    case LINE:
                        d0 = GOLD;
                        if (lowFree(side, u) && !mark) dp = st(DST, o, true);
                        break;
                    case SILL:
                        // ouest en y 102 : derriere les banquettes du salon, un escalier bas faisait un 2e
                        // gradin (assise -> allege -> niche du panneau) ; allege retournee a la place
                        d0 = frame ? LOG_Y : st(DST, o, west && y == F + 2);
                        break;
                    case PANEL:
                        if (frame) d0 = LOG_Y;
                        else {
                            d0 = AIR;
                            dm = RED;
                        }
                        break;
                    case LINTEL:
                        d0 = frame ? LOG_Y : st(DST, o, true);
                        break;
                    case PLANK:
                        d0 = PL;
                        break;
                    case FRIEZE:
                        d0 = GOLD;
                        break;
                    default:
                        d0 = PL;
                        dp = st(DST, o, true);
                }
                if (jack && y >= F + 5 && y <= F + 10) {
                    // fond noir encadre d'or derriere le panneau du jackpot
                    dm = null;
                    if (u == -3 || u == 4 || y == F + 5 || y == F + 10) d0 = GOLD;
                    else {
                        d0 = AIR;
                        dm = "black_wool";
                    }
                }
                w(side, u, y, 0, d0);
                w(side, u, y, -1, dm);
                w(side, u, y, 1, dp);
            }
        }
        for (int u : full) pilaster(side, u, true);
        for (int u : upper) pilaster(side, u, false);
        // tentures dorees au centre d'un panneau sur deux
        List<Integer> m = new ArrayList<>(marks);
        m.add(0, -30);
        m.add(30);
        int k = 0;
        for (int i = 0; i + 1 < m.size(); i++) {
            int a = m.get(i), b = m.get(i + 1);
            if (b - a - 1 < 5 || ((a + b) & 1) != 0) continue;
            int c = (a + b) / 2;
            k++;
            if ((side == N && c >= -3 && c <= 4) || (side == S && Math.abs(c) <= 5)) continue;
            if (k % 2 == 1) continue;
            int y = west ? F + 10 : F + 8;
            w(side, c, y, 0, "yellow_wall_banner[facing=" + o.opposite().id() + "]");
        }
    }

    private void pilaster(int side, int u, boolean full) {
        Dir o = out(side);
        if (full) {
            w(side, u, F + 1, 1, CPB);
            for (int y = F + 2; y <= F + 9; y++) w(side, u, y, 1, LOG_Y);
            w(side, u, F + 4, 1, GOLD);
        } else {
            w(side, u, F + 5, 1, st(DST, o, true));
            for (int y = F + 6; y <= F + 9; y++) w(side, u, y, 1, LOG_Y);
        }
        w(side, u, F + 10, 1, GOLD);
        w(side, u, F + 11, 1, PL);
    }

    // ------------------------------------------------------------------ plafond

    /** Indice dans le caisson (0..4) entre deux poutres, -1 sur une poutre ou dans la bande le long des murs. */
    private static int local(int v) {
        for (int i = 0; i + 1 < BEAMS.length; i++) {
            if (v > BEAMS[i] && v < BEAMS[i + 1]) return v - BEAMS[i] - 1;
        }
        return -1;
    }

    private void ceiling() {
        for (int x = -29; x <= 29; x++) {
            for (int z = -29; z <= 29; z++) {
                boolean bx = in(BEAMS, x), bz = in(BEAMS, z);
                boolean edge = Math.abs(x) == 29 || Math.abs(z) == 29;
                if (bx || bz) {
                    if (!edge) set(x, F + 12, z, bx && bz ? GOLD : PL);
                    set(x, F + 13, z, PL);
                    continue;
                }
                int lx = local(x), lz = local(z);
                if (lx < 0 || lz < 0) {
                    set(x, F + 13, z, PL);
                    continue;
                }
                boolean ring = lx == 0 || lx == 4 || lz == 0 || lz == 4;
                if (ring) {
                    Dir f = lx == 0 ? Dir.WEST : lx == 4 ? Dir.EAST : lz == 0 ? Dir.NORTH : Dir.SOUTH;
                    set(x, F + 13, z, st(DST, f, true));
                } else {
                    set(x, F + 13, z, AIR);
                    set(x, F + 14, z, lx == 2 && lz == 2 ? GOLD : RED);
                }
            }
        }
    }

    // ------------------------------------------------------------------ coupole et rotonde

    private static int domeH(double d) {
        return F + 13 + (int) Math.round(6.0 * Math.sqrt(Math.max(0, 1 - d * d / (DOME_R * DOME_R))));
    }

    /** Nervures alignees sur les 8 colonnes (22,5 deg + k x 45 deg). */
    private static boolean rib(int x, int z) {
        double d = Math.hypot(x, z);
        double th = Math.atan2(z, x) - Math.toRadians(22.5);
        double k = Math.round(th / (Math.PI / 4));
        double delta = th - k * Math.PI / 4;
        return d * Math.abs(Math.sin(delta)) < 0.62;
    }

    private static String domeMat(int x, int z) {
        double d = Math.hypot(x, z);
        if (d < 1.2) return "minecraft:shroomlight";
        if (d < 2.3) return GOLD;
        if (rib(x, z)) return GOLD;
        if (Math.abs(d - 6.0) < 0.5 || Math.abs(d - 4.0) < 0.5) return PL;
        return RED;
    }

    private void dome() {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                double d = Math.hypot(x, z);
                if (d >= DOME_R) continue;
                int h = domeH(d);
                for (int y = F + 12; y < h; y++) set(x, y, z, AIR);
                set(x, h, z, domeMat(x, z));
                if (d >= 8.5) set(x, F + 12, z, rib(x, z) ? GOLD : PL); // ceinture de la coupole
            }
        }
    }

    private static final int[][] ROT_COLS = {{8, 3}, {3, 8}, {-3, 8}, {-8, 3}, {-8, -3}, {-3, -8}, {3, -8}, {8, -3}};

    private void rotunda() {
        for (int[] c : ROT_COLS) {
            int x = c[0], z = c[1];
            set(x, F + 1, z, "chiseled_quartz_block");
            for (int y = F + 2; y <= F + 10; y++) set(x, y, z, "quartz_pillar[axis=y]");
            set(x, F + 6, z, GOLD);
            set(x, F + 11, z, "chiseled_quartz_block");
            set(x, F + 12, z, GOLD);
            // base moulee
            for (Dir d : Dir.values()) set(x + d.dx, F + 1, z + d.dz, st("minecraft:quartz_stairs", d.opposite(), false));
            // pendentifs sous la ceinture, dans le sens de l'anneau
            int tx = -z, tz = x;
            Dir t = Math.abs(tx) >= Math.abs(tz) ? Dir.EAST : Dir.SOUTH;
            for (int sg : new int[]{-1, 1}) {
                int nx = x + sg * t.dx, nz = z + sg * t.dz;
                Dir toward = Dir.fromDelta(-sg * t.dx, -sg * t.dz);
                set(nx, F + 11, nz, st("minecraft:quartz_stairs", toward, true));
            }
        }
    }

    // ------------------------------------------------------------------ escalier et galerie (ouest)

    private void stair() {
        // 7 marches le long du mur nord, de x -23 (y 101) a x -29 (y 107, au niveau du plancher de la galerie)
        for (int k = 0; k <= 6; k++) {
            int x = -23 - k, y = F + 1 + k;
            for (int z = -29; z <= -28; z++) {
                for (int yy = F + 1; yy < y; yy++) set(x, yy, z, PL);
                set(x, y, z, st(DST, Dir.WEST, false));
            }
        }
    }

    private void gallery() {
        for (int x = -29; x <= -26; x++)
            for (int z = GAL_Z1; z <= GAL_Z2; z++) set(x, GAL_FLOOR, z, PL);
        for (int z = GAL_Z1; z <= GAL_Z2; z++) {
            for (int x = -29; x <= -27; x++) set(x, GAL_FLOOR + 1, z, x == -27 ? "yellow_carpet" : "red_carpet");
            set(-25, GAL_FLOOR, z, st(DST, Dir.WEST, true));                 // corniche du balcon
            set(-26, GAL_FLOOR + 1, z, in(GAL_COLS, z) ? CPB : "dark_oak_fence"); // balustrade
        }
        for (int x = -29; x <= -27; x++) set(x, GAL_FLOOR + 1, GAL_Z2, "dark_oak_fence");
        for (int c : GAL_COLS) {
            set(-26, GAL_FLOOR + 2, c, "lantern[hanging=false]");
            // colonne et arcade sous le balcon
            set(-26, F + 1, c, PBB);
            for (int y = F + 2; y <= F + 5; y++) set(-26, y, c, LOG_Y);
            set(-26, F + 6, c, GOLD);
            set(-26, F + 6, c - 1, st(DST, Dir.SOUTH, true));
            set(-26, F + 6, c + 1, st(DST, Dir.NORTH, true));
        }
        // salon sous la galerie : banquettes adossees au mur (dossier = panneau de velours), tables a plateau dore
        for (int i = 0; i + 1 < GAL_COLS.length; i++) {
            int c = GAL_COLS[i];
            for (int dz : new int[]{1, 2, 4, 5}) set(-29, F + 1, c + dz, st(DST, Dir.WEST, false));
            set(-29, F + 1, c + 3, PBB);
            set(-29, F + 2, c + 3, i % 2 == 0 ? "potted_flowering_azalea_bush" : "potted_fern");
            for (int dz : new int[]{2, 4}) {
                set(-28, F + 1, c + dz, "dark_oak_fence");
                set(-28, F + 2, c + dz, "light_weighted_pressure_plate");
            }
            set(-28, F + 6, c + 3, "lantern[hanging=true]");
        }
    }

    // ------------------------------------------------------------------ bar (nord-est)

    private void bar() {
        String[] shelf = {"brewing_stand", "candle[candles=4,lit=true]", "decorated_pot", "brewing_stand"};
        String[] top = {"potted_red_tulip", "candle[candles=2,lit=true]", "potted_bamboo", "flower_pot"};
        for (int x = 25; x <= 28; x++) {
            set(x, F + 1, -29, PL);
            set(x, F + 2, -29, shelf[x - 25]);
            set(x, F + 3, -29, "dark_oak_slab[type=top]");
            set(x, F + 4, -29, top[x - 25]);
            // comptoir : soubassement de pierre, plateau de marbre
            set(x, F + 1, -26, x == 25 || x == 28 ? CPB : PBB);
            set(x, F + 2, -26, "smooth_quartz_slab[type=bottom]");
        }
        for (int x : new int[]{25, 27}) {
            set(x, F + 1, -25, "dark_oak_fence");
            set(x, F + 2, -25, "red_carpet");
        }
        for (int x = 25; x <= 28; x += 3) pendant(x, -27, F + 8);
    }

    // ------------------------------------------------------------------ portail (sud)

    private void entrance() {
        final int Z = 30;
        // embrasure et baie en plein cintre, vantaux de verre noir en retrait
        for (int x = -3; x <= 3; x++)
            for (int y = F + 1; y <= F + 6; y++) set(x, y, Z, PBB);
        for (int y = F + 1; y <= F + 5; y++) {
            set(-3, y, Z, CPB);
            set(3, y, Z, CPB);
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = F + 1; y <= F + 4; y++) {
                set(x, y, Z, AIR);
                set(x, y, Z + 1, "black_stained_glass");
            }
        }
        for (int x = -1; x <= 1; x++) {
            set(x, F + 5, Z, AIR);
            set(x, F + 5, Z + 1, "black_stained_glass");
        }
        set(-2, F + 5, Z, st("minecraft:polished_blackstone_brick_stairs", Dir.WEST, true));
        set(2, F + 5, Z, st("minecraft:polished_blackstone_brick_stairs", Dir.EAST, true));
        set(0, F + 6, Z, GOLD);
        // colonnes de quartz, entablement, corniche
        for (int sx : new int[]{-4, 4}) {
            set(sx, F + 1, Z - 1, "chiseled_quartz_block");
            for (int y = F + 2; y <= F + 6; y++) set(sx, y, Z - 1, "quartz_pillar[axis=y]");
            set(sx, F + 7, Z - 1, GOLD);
        }
        for (int x = -5; x <= 5; x++) set(x, F + 8, Z - 1, Math.abs(x) == 5 || x == 0 ? GOLD : PL);
        for (int x = -6; x <= 6; x++) set(x, F + 9, Z - 1, st(DST, Dir.SOUTH, true));
        set(-5, F + 7, Z - 1, st(DST, Dir.SOUTH, true));
        set(5, F + 7, Z - 1, st(DST, Dir.SOUTH, true));
        // rideaux de velours retenus par un embrasse doree
        for (int sx : new int[]{-3, 3}) {
            for (int y = F + 1; y <= F + 7; y++) set(sx, y, Z - 1, y == F + 3 ? GOLD : RED);
            int sw = sx < 0 ? -2 : 2;
            set(sw, F + 6, Z - 1, RED);
            set(sw, F + 7, Z - 1, RED);
        }
        // rosace eclairee au-dessus du portail (corniche interrompue devant elle)
        for (int x = -2; x <= 2; x++) set(x, F + 12, Z - 1, AIR);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                double r = Math.hypot(dx, dy);
                int y = F + 11 + dy;
                if (r <= 1.5) {
                    set(dx, y, Z, r < 0.5 ? "yellow_stained_glass" : (Math.abs(dx) + Math.abs(dy) == 1 ? "orange_stained_glass" : "red_stained_glass"));
                    set(dx, y, Z + 1, "shroomlight");
                } else if (r <= 2.3 && y <= F + 12) {
                    set(dx, y, Z - 1, GOLD); // cadre dore en saillie
                }
            }
        }
    }

    // ------------------------------------------------------------------ kiosques de la caisse et de la boutique

    private void kiosk(int xa, int xb) {
        for (int x : new int[]{xa, xb}) {
            for (int z : new int[]{23, 27}) {
                set(x, F + 1, z, PBB);
                for (int y = F + 2; y <= F + 5; y++) set(x, y, z, LOG_Y);
                set(x, F + 6, z, GOLD);
            }
        }
        // toit a croupes avec debord, faitage a fleurons dores
        int x1 = xa - 1, x2 = xb + 1, z1 = 22, z2 = 28;
        int emax = Math.min((x2 - x1) / 2, (z2 - z1) / 2);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int ex1 = x - x1, ex2 = x2 - x, ez1 = z - z1, ez2 = z2 - z;
                int e = Math.min(Math.min(ex1, ex2), Math.min(ez1, ez2));
                Dir f = e == ez1 ? Dir.SOUTH : e == ez2 ? Dir.NORTH : e == ex1 ? Dir.EAST : Dir.WEST;
                if (e == 0) {
                    set(x, F + 7, z, st(DST, f, false));
                    continue;
                }
                for (int y = F + 7; y < F + 7 + e; y++) set(x, y, z, PL);
                boolean end = x == x1 + emax || x == x2 - emax;
                set(x, F + 7 + e, z, e >= emax ? (end ? GOLD : "dark_oak_slab[type=bottom]") : st(DST, f, false));
            }
        }
        set(xa + 1, F + 6, 25, "lantern[hanging=true]");
        set(xb - 1, F + 6, 25, "lantern[hanging=true]");
    }

    /** Panneau encadre derriere les classements flottants. */
    private void backdrop(int xa, int xb) {
        final int z = 26;
        for (int x = xa; x <= xb; x++) {
            set(x, F + 1, z, PBB);
            boolean end = x == xa || x == xb;
            for (int y = F + 2; y <= F + 5; y++) set(x, y, z, end ? LOG_Y : "black_wool");
            set(x, F + 6, z, GOLD);
            set(x, F + 7, z, end ? GOLD : st(DST, Dir.NORTH, false));
        }
        int c = (xa + xb) / 2;
        set(c, F + 7, z, GOLD);
        set(c + 1, F + 7, z, GOLD);
        set(c, F + 8, z, "candle[candles=3,lit=true]");
        set(c + 1, F + 8, z, "candle[candles=3,lit=true]");
    }

    // ------------------------------------------------------------------ postes de jeu (positions du contrat)

    private void stations() {
        // machines a sous (mur nord) sans modeles 3D
        for (int x : Layout.SLOT_X) {
            if (models) break;
            set(x, F + 1, Layout.SLOT_Z, PBB);
            set(x, F + 2, Layout.SLOT_Z, "black_stained_glass");
            set(x, F + 3, Layout.SLOT_Z, GOLD);
            set(x, F + 4, Layout.SLOT_Z, "redstone_lamp[lit=true]");
            set(x + 1, F + 2, Layout.SLOT_Z, "lever[face=wall,facing=east]");
        }
        // video poker (mur est) sans modeles 3D
        for (int z : Layout.VP_Z) {
            if (models) break;
            set(Layout.VP_X, F + 1, z, PBB);
            set(Layout.VP_X, F + 2, z, "blue_stained_glass");
            set(Layout.VP_X, F + 3, z, "lapis_block");
            set(Layout.VP_X, F + 4, z, "sea_lantern");
        }
        // tables de blackjack (ouest), croupier cote ouest
        for (int cz : Layout.BJ_Z) {
            for (int x = Layout.BJ_X1; x <= Layout.BJ_X1 + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean corner = x == Layout.BJ_X1 + 2 && Math.abs(z - cz) == 2;
                    if (corner) continue;
                    boolean inner = x > Layout.BJ_X1 && Math.abs(z - cz) <= 1;
                    set(x, F + 1, z, inner ? "green_wool" : PL);
                }
            }
            set(Layout.BJ_X1 + 1, F + 2, cz, "white_carpet");
        }
        // roulette (centre) : table, cylindre en blocs sous le cylindre 3D, tableau des mises
        for (int x = -4; x <= 4; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean rim = Math.abs(x) == 4 || Math.abs(z) == 2;
                set(x, F + 1, z, rim ? PL : "green_wool");
            }
        }
        set(-2, F + 1, 0, GOLD);
        int[][] ring = {{-3, -1}, {-2, -1}, {-1, -1}, {-1, 0}, {-1, 1}, {-2, 1}, {-3, 1}, {-3, 0}};
        for (int i = 0; i < ring.length; i++)
            set(ring[i][0], F + 1, ring[i][1], i == 0 ? "lime_concrete" : i % 2 == 0 ? "black_concrete" : "red_concrete");
        for (int x = 1; x <= 3; x++) set(x, F + 2, 0, "white_carpet");
        // grande roue : socle et pied derriere la roue (roue de 4,5 blocs centree en x -8,93, y F+4,5, z 13,5)
        fill(-10, F + 1, 12, -10, F + 1, 15, PBB);
        fill(-10, F + 2, 13, -10, F + 4, 14, PL);
        fill(-10, F + 5, 13, -10, F + 5, 14, GOLD);
        set(-11, F + 1, 13, PBB);
        set(-11, F + 1, 14, PBB);
        set(-11, F + 2, 13, st("minecraft:polished_blackstone_brick_stairs", Dir.EAST, false));
        set(-11, F + 2, 14, st("minecraft:polished_blackstone_brick_stairs", Dir.EAST, false));
        // plinko : mur de fond (planche de 4 x 5 blocs en x 9,97, z 11,5..15,5, y F+1,5..F+6,5)
        fill(10, F + 1, 11, 10, F + 7, 15, PL);
        fill(10, F + 1, 11, 10, F + 1, 15, PBB);
        fill(10, F + 7, 11, 10, F + 7, 15, GOLD);
        fill(10, F + 2, 11, 10, F + 6, 11, GOLD);
        fill(10, F + 2, 15, 10, F + 6, 15, GOLD);
        for (int z = 11; z <= 15; z++) set(11, F + 8, z, st(DST, Dir.WEST, false));
        set(11, F + 1, 13, CPB);
        // tables de poker (est)
        for (int cz : Layout.PK_Z) {
            for (int x = 17; x <= 23; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean corner = (x == 17 || x == 23) && Math.abs(z - cz) == 2;
                    if (corner) continue;
                    boolean rim = x == 17 || x == 23 || Math.abs(z - cz) == 2;
                    set(x, F + 1, z, rim ? PL : "green_wool");
                }
            }
            set(16, F + 1, cz - 1, "dark_oak_stairs[facing=west]");
            set(16, F + 1, cz + 1, "dark_oak_stairs[facing=west]");
            set(24, F + 1, cz - 1, "dark_oak_stairs[facing=east]");
            set(24, F + 1, cz + 1, "dark_oak_stairs[facing=east]");
            set(20, F + 1, cz - 3, "dark_oak_stairs[facing=north]");
            set(20, F + 1, cz + 3, "dark_oak_stairs[facing=south]");
        }
        // caisse (sud-ouest) et boutique (sud-est)
        counter(-14, -8, GOLD);
        counter(8, 14, "minecraft:amethyst_block");
    }

    private void counter(int x1, int x2, String top) {
        for (int x = x1; x <= x2; x++) {
            set(x, F + 1, Layout.COUNTER_Z, x == x1 || x == x2 ? CPB : PL);
            set(x, F + 2, Layout.COUNTER_Z, x == x1 || x == x2 ? top : "dark_oak_slab[type=bottom]");
        }
        for (int x = x1; x <= x2; x++) {
            set(x, F + 1, Layout.COUNTER_Z + 4, PL);
            set(x, F + 2, Layout.COUNTER_Z + 4, "bookshelf");
            set(x, F + 3, Layout.COUNTER_Z + 4, x == x1 || x == x2 ? top : "dark_oak_slab[type=bottom]");
        }
    }

    // ------------------------------------------------------------------ cordons, lampadaires, plantes

    private void decor() {
        // cordons de velours le long de l'allee d'entree
        for (int sx : new int[]{-3, 3}) {
            for (int z = 21; z <= 25; z++) {
                if ((z & 1) == 1) {
                    set(sx, F + 1, z, "polished_blackstone_wall");
                    set(sx, F + 2, z, "light_weighted_pressure_plate");
                } else {
                    set(sx, F + 1, z, "iron_chain[axis=z]");
                }
            }
        }
        // lampadaires du vestibule
        for (int sx : new int[]{-5, 5}) {
            set(sx, F + 1, 26, CPB);
            set(sx, F + 2, 26, "polished_blackstone_wall");
            set(sx, F + 3, 26, "polished_blackstone_wall");
            set(sx, F + 4, 26, "lantern[hanging=false]");
        }
        // plantes en pot sur socle
        int[][] plants = {{28, 28}, {-28, 28}, {-11, -18}, {12, -18}, {-12, 18}, {13, 18}, {23, -20}, {-17, -26}};
        String[] kinds = {"potted_bamboo", "potted_flowering_azalea_bush", "potted_azalea_bush", "potted_fern"};
        int i = 0;
        for (int[] p : plants) {
            if (!air(p[0], F + 2, p[1])) continue;
            set(p[0], F + 1, p[1], CPB);
            set(p[0], F + 2, p[1], kinds[i++ % kinds.length]);
        }
    }

    // ------------------------------------------------------------------ eclairage

    private void lights() {
        chandelier(0, 0, F + 8, 3);             // grand lustre sous la coupole
        chandelier(0, 25, F + 8, 2);            // vestibule
        for (int x : new int[]{-12, 12}) chandelier(x, -12, F + 8, 2);
        for (int x : new int[]{-18, -6, 6, 18}) chandelier(x, -24, F + 9, 1);
        for (int cz : Layout.BJ_Z) chandelier(-20, cz, F + 8, 1);
        // poker : rampe suspendue a trois lanternes au-dessus de chaque table
        for (int cz : Layout.PK_Z) {
            // rampe : chaine horizontale tendue entre deux noeuds dores, pendus par leurs chaines
            for (int x = 18; x <= 22; x++) set(x, F + 9, cz, "iron_chain[axis=x]");
            for (int x : new int[]{17, 23}) set(x, F + 9, cz, GOLD);
            for (int x : new int[]{17, 20, 23}) set(x, F + 8, cz, "lantern[hanging=true]");
            for (int x : new int[]{17, 23}) chainUp(x, F + 10, cz);
        }
        // lanternes pendues dans un caisson sur trois environ (choix par hachage, pas de grille)
        for (int x = -29; x <= 29; x++) {
            for (int z = -29; z <= 29; z++) {
                if (local(x) != 2 || local(z) != 2 || Math.hypot(x, z) < 11) continue;
                if (Noise.hash(77, x, 0, z) > 0.4) continue;
                if (!air(x, F + 12, z) || !air(x, F + 11, z)) continue;
                set(x, F + 13, z, "iron_chain[axis=y]");
                set(x, F + 12, z, "lantern[hanging=true]");
            }
        }
        // appliques sur les pilastres pleins
        sconces(E, new int[]{-26, -14, -8, -2, 4, 10, 22, 28});
        sconces(S, new int[]{-27, -21, -15, -9, 9, 15, 21, 27});
        sconces(N, new int[]{24});
        // lumiere invisible (Esteban, 16/11 : « ca manque de lumiere a beaucoup d'endroits ») :
        // une grille serree juste au-dessus des tetes + une grille sous le plafond, au niveau max
        for (int x = -29; x <= 29; x += 4)
            for (int z = -29; z <= 29; z += 4)
                if (air(x, F + 4, z)) set(x, F + 4, z, "light[level=15]");
        for (int x = -27; x <= 27; x += 6)
            for (int z = -27; z <= 27; z += 6)
                if (air(x, F + 11, z)) set(x, F + 11, z, "light[level=15]");
        // galerie (plancher en y 107) et dessous
        for (int x = -29; x <= -26; x += 3)
            for (int z = -27; z <= 21; z += 4)
                if (air(x, F + 9, z)) set(x, F + 9, z, "light[level=15]");
    }

    private void sconces(int side, int[] us) {
        for (int u : us) {
            int x = wx(side, u, 2), z = wz(side, u, 2);
            if (!air(x, F + 6, z) || !air(x, F + 7, z) || !air(x, F + 5, z)) continue;
            set(x, F + 6, z, "dark_oak_trapdoor[facing=" + out(side).id() + ",half=top,open=false]");
            set(x, F + 7, z, "lantern[hanging=false]");
        }
    }

    /** Chaine du point (x, y, z) jusqu'au premier bloc plein au-dessus. */
    private void chainUp(int x, int y, int z) {
        for (int yy = y; yy < F + 21 && air(x, yy, z); yy++) set(x, yy, z, "iron_chain[axis=y]");
    }

    private void pendant(int x, int z, int lanternY) {
        set(x, lanternY, z, "lantern[hanging=true]");
        chainUp(x, lanternY + 1, z);
    }

    /**
     * Lustre suspendu. size 1 : croix doree a 4 lanternes ; 2 : couronne de 8 lanternes et etage bas ;
     * 3 : grand lustre a trois couronnes (16 + 8 lanternes), bougies, pampilles.
     */
    private void chandelier(int cx, int cz, int bottom, int size) {
        if (size == 1) {
            int y = bottom + 1;
            set(cx, y, cz, GOLD);
            for (Dir d : Dir.values()) {
                set(cx + d.dx, y, cz + d.dz, "end_rod[facing=" + d.id() + "]");
                set(cx + 2 * d.dx, y, cz + 2 * d.dz, GOLD);
                set(cx + 2 * d.dx, y - 1, cz + 2 * d.dz, "lantern[hanging=true]");
                set(cx + 2 * d.dx, y + 1, cz + 2 * d.dz, "candle[candles=2,lit=true]");
            }
            set(cx, y - 1, cz, "end_rod[facing=down]");
            chainUp(cx, y + 1, cz);
            return;
        }
        if (size == 2) {
            int y = bottom + 3;
            set(cx, y, cz, GOLD);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || ((dx + dz) & 1) != 0) continue;
                    set(cx + dx, y, cz + dz, GOLD);
                    set(cx + dx, y - 1, cz + dz, "lantern[hanging=true]");
                    set(cx + dx, y + 1, cz + dz, "candle[candles=3,lit=true]");
                }
            }
            for (Dir d : Dir.values()) set(cx + d.dx, y, cz + d.dz, "end_rod[facing=" + d.id() + "]");
            set(cx, y - 1, cz, "iron_chain[axis=y]");
            set(cx, y - 2, cz, GOLD);
            for (Dir d : Dir.values()) {
                set(cx + d.dx, y - 2, cz + d.dz, GOLD);
                set(cx + d.dx, y - 3, cz + d.dz, "lantern[hanging=true]");
            }
            set(cx, y - 3, cz, "end_rod[facing=down]");
            chainUp(cx, y + 1, cz);
            return;
        }
        // grand lustre
        int[][] rings = {{4, 16, 5}, {2, 8, 3}};
        for (int[] r : rings) {
            int y = bottom + r[2];
            for (int k = 0; k < r[1]; k++) {
                double a = 2 * Math.PI * k / r[1];
                int x = cx + (int) Math.round(r[0] * Math.cos(a));
                int z = cz + (int) Math.round(r[0] * Math.sin(a));
                set(x, y, z, GOLD);
                set(x, y - 1, z, (k & 1) == 0 ? "lantern[hanging=true]" : "end_rod[facing=down]");
                set(x, y + 1, z, "candle[candles=3,lit=true]");
            }
            for (Dir d : Dir.values())
                for (int i = 1; i < r[0]; i++) set(cx + i * d.dx, y, cz + i * d.dz, "end_rod[facing=" + d.id() + "]");
            set(cx, y, cz, GOLD);
        }
        for (int y = bottom + 1; y < bottom + 5; y++) if (air(cx, y, cz)) set(cx, y, cz, y == bottom + 1 ? GOLD : "iron_chain[axis=y]");
        set(cx, bottom, cz, "lantern[hanging=true]");
        chainUp(cx, bottom + 6, cz);
    }

    // ------------------------------------------------------------------ peau : aucune masse brute visible

    /** Toute masse brute (blackstone) visible depuis la salle recoit le materiau de sa zone. */
    private void skin() {
        List<int[]> todo = new ArrayList<>();
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                for (int y = F + 1; y <= F + 20; y++) {
                    if (!air(x, y, z)) continue;
                    int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                    for (int[] n : nb) {
                        int ax = x + n[0], ay = y + n[1], az = z + n[2];
                        if (SHELL.equals(s.get(ax, ay, az))) todo.add(new int[]{ax, ay, az});
                    }
                }
            }
        }
        for (int[] p : todo) {
            int x = p[0], y = p[1], z = p[2];
            String m;
            if (y >= F + 13) m = Math.hypot(x, z) < DOME_R + 1 ? domeMat(x, z) : PL;
            else m = y <= F + 1 ? stone.at(x, y, z) : PL;
            set(x, y, z, m);
        }
    }
}
