package fr.eemicraft.infection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;





/**
 * Ville abandonnee de nuit (~90 x 90), construite bloc par bloc.
 * x vers l'est, z vers le sud. Sol de la ville en y = G (on marche en G + 1).
 *
 * Quartiers : NO eglise + cimetiere (crypte dessous) + maisons ; NE supermarche + parking ;
 * SO parc + maison ; SE station-service + ruelle + maisons. Hangar d'attente au sud, hors des murs.
 */
final class CityOld {
    static final int G = 64;
    /** Limite jouable : barrieres en x/z = +-WALL (46 avant l'agrandissement du 15/11/2026). */
    static final int WALL = 78;
    /** Ancienne ville (centre) : tout ce qui est construit a l'interieur de +-INNER. */
    static final int INNER = 45;
    /** Marge construite autour des murs (sol, decor). */
    static final int EXT = WALL + 16;
    /** Hangar d'attente au sud, hors des murs. */
    static final int HZ1 = WALL + 14;
    static final int HZ2 = HZ1 + 18;

    // points cles (utilises par le jeu)
    static final double[] WAIT = {0.5, G + 1, HZ1 + 10.5, 180};
    static final double[] CRYPT = {-14.0, G - 9, -33.0, 0};
    static final double[] EXIT = {-14.0, G + 1, -28.5, 0};
    static final double[] SPECT = {0.5, G + 28, 0.5, 0};

    private final fr.eemicraft.infection.build.Sink w;
    private final Logger log;
    private final Random rnd = new Random(20261106L);
    
    /** Cases a laisser libres (portes, escaliers) : cle x/y/z. */
    private final Set<Long> keep = new HashSet<>();
    int ops;

    CityOld(fr.eemicraft.infection.build.Sink w, Logger log) {
        this.w = w;
        this.log = log;
    }

    // ================================================================ outils

    void set(int x, int y, int z, String b) { w.set(x, y, z, b); ops++; }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, b);
                }
            }
        }
    }

    boolean air(int x, int y, int z) {
        return w.isAir(x, y, z);
    }

    void setIfAir(int x, int y, int z, String b) {
        if (air(x, y, z)) {
            set(x, y, z, b);
        }
    }

    String type(int x, int y, int z) { return fr.eemicraft.infection.build.B.name(w.get(x, y, z)); }

    String pick(String... a) {
        return a[rnd.nextInt(a.length)];
    }

    boolean chance(double p) {
        return rnd.nextDouble() < p;
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    void keep(int x, int y, int z) {
        keep.add(key(x, y, z));
        keep.add(key(x, y + 1, z));
    }

    /** Meuble : seulement dans l'air et hors des passages. */
    void furn(int x, int y, int z, String b) {
        if (air(x, y, z) && !keep.contains(key(x, y, z))) {
            set(x, y, z, b);
        }
    }

    static String facing(int nx, int nz) {
        if (nz < 0) {
            return "north";
        }
        if (nz > 0) {
            return "south";
        }
        return nx < 0 ? "west" : "east";
    }

    // ================================================================ construction complete

    void build() {
        // 1. on efface tout (ville, hangar, crypte)
        fill(-EXT, G - 14, -EXT, EXT, G + 42, HZ2 + 16, "minecraft:air");
        // 2. sol
        for (int x = -EXT; x <= EXT; x++) {
            for (int z = -EXT; z <= HZ2 + 16; z++) {
                fill(x, G - 13, z, x, G - 3, z, "minecraft:stone");
                set(x, G - 2, z, "minecraft:dirt");
                set(x, G - 1, z, "minecraft:dirt");
                set(x, G, z, ground());
            }
        }
        roads();
        park();
        church();
        cemetery();
        crypt();
        houses();
        supermarket();
        gasStation();
        ring();
        ringHouses();
        cars();
        lamps();
        barricades();
        boundary();
        outskirts();
        hangar();
        decorate();
        lights();
    }

    String ground() {
        double r = rnd.nextDouble();
        if (r < 0.58) {
            return "minecraft:grass_block";
        }
        if (r < 0.74) {
            return "minecraft:coarse_dirt";
        }
        if (r < 0.84) {
            return "minecraft:podzol";
        }
        if (r < 0.92) {
            return "minecraft:dirt";
        }
        return "minecraft:rooted_dirt";
    }

    // ================================================================ rues

    String asphalt() {
        double r = rnd.nextDouble();
        if (r < 0.55) {
            return "minecraft:black_concrete";
        }
        if (r < 0.72) {
            return "minecraft:gray_concrete_powder";
        }
        if (r < 0.86) {
            return "minecraft:polished_blackstone";
        }
        if (r < 0.95) {
            return "minecraft:cracked_polished_blackstone_bricks";
        }
        return "minecraft:gravel";
    }

    String pavement() {
        double r = rnd.nextDouble();
        if (r < 0.55) {
            return "minecraft:smooth_stone";
        }
        if (r < 0.75) {
            return "minecraft:stone_bricks";
        }
        if (r < 0.92) {
            return "minecraft:cracked_stone_bricks";
        }
        return "minecraft:andesite";
    }

    void roads() {
        // route est-ouest (z -3..3) et nord-sud (x -3..3), trottoirs de 2
        for (int a = -WALL; a <= WALL; a++) {
            for (int b = -5; b <= 5; b++) {
                int ab = Math.abs(b);
                String s = ab <= 3 ? asphalt() : ab == 4 ? "minecraft:polished_andesite" : pavement();
                // EO
                if (!(Math.abs(a) <= 5)) {
                    set(a, G, b, s);
                }
                // NS
                set(b, G, a, s);
            }
        }
        // carrefour : chaussee pleine, coins de trottoir
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                set(x, G, z, Math.abs(x) >= 4 && Math.abs(z) >= 4 ? pavement() : "minecraft:black_concrete");
            }
        }
        for (int i = -3; i <= 3; i += 2) {
            fill(i, G, -5, i, G, -4, "minecraft:white_concrete");
            fill(i, G, 4, i, G, 5, "minecraft:white_concrete");
            fill(-5, G, i, -4, G, i, "minecraft:white_concrete");
            fill(4, G, i, 5, G, i, "minecraft:white_concrete");
        }
        // ligne jaune pointillee (effacee par endroits)
        for (int a = -WALL; a <= WALL; a++) {
            if (Math.abs(a) > 6 && Math.floorMod(a, 4) < 2 && chance(0.85)) {
                set(a, G, 0, "minecraft:yellow_terracotta");
                set(0, G, a, "minecraft:yellow_terracotta");
            }
        }
        // la route nord-sud continue vers le hangar (hors des murs)
        for (int z = WALL + 1; z < HZ1; z++) {
            for (int x = -3; x <= 3; x++) {
                set(x, G, z, chance(0.3) ? "minecraft:gravel" : asphalt());
            }
        }
        // ruelle pavee au sud-est (z 24..26)
        for (int x = 6; x <= 45; x++) {
            for (int z = 24; z <= 26; z++) {
                set(x, G, z, pick("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:gravel", "minecraft:cobblestone", "minecraft:dirt_path"));
            }
        }
        // parking du supermarche
        for (int x = 7; x <= 44; x++) {
            for (int z = -22; z <= -7; z++) {
                boolean line = (z <= -17 || z >= -12) && Math.floorMod(x - 9, 8) == 0;
                set(x, G, z, line ? "minecraft:white_concrete" : chance(0.85) ? "minecraft:gray_concrete" : "minecraft:cracked_stone_bricks");
            }
        }
    }

    // ================================================================ parc (SO)

    void park() {
        for (int x = -43; x <= -8; x++) {
            for (int z = 8; z <= 43; z++) {
                set(x, G, z, chance(0.85) ? "minecraft:grass_block" : "minecraft:moss_block");
            }
        }
        // allees
        for (int x = -43; x <= -8; x++) {
            for (int z = 21; z <= 23; z++) {
                set(x, G, z, chance(0.8) ? "minecraft:dirt_path" : "minecraft:coarse_dirt");
            }
        }
        for (int z = 8; z <= 43; z++) {
            for (int x = -26; x <= -24; x++) {
                set(x, G, z, chance(0.8) ? "minecraft:dirt_path" : "minecraft:coarse_dirt");
            }
        }
        // fontaine
        int fx = -25;
        int fz = 22;
        for (int x = fx - 4; x <= fx + 4; x++) {
            for (int z = fz - 4; z <= fz + 4; z++) {
                double d = Math.sqrt((x - fx) * (x - fx) + (z - fz) * (z - fz));
                if (d <= 2.6) {
                    set(x, G - 1, z, "minecraft:stone_bricks");
                    set(x, G, z, "minecraft:water");
                } else if (d <= 3.6) {
                    set(x, G, z, "minecraft:stone_bricks");
                    set(x, G + 1, z, chance(0.15) ? "minecraft:cracked_stone_bricks" : "minecraft:stone_brick_slab");
                }
            }
        }
        fill(fx, G, fz, fx, G + 2, fz, "minecraft:chiseled_stone_bricks");
        set(fx, G + 3, fz, "minecraft:stone_brick_wall");
        set(fx, G + 4, fz, "minecraft:soul_lantern");
        // etang
        for (int x = -41; x <= -32; x++) {
            for (int z = 11; z <= 19; z++) {
                double dx = (x + 36.5) / 4.6;
                double dz = (z - 15) / 3.8;
                double d = dx * dx + dz * dz;
                if (d <= 1.0) {
                    set(x, G - 1, z, "minecraft:mud");
                    set(x, G, z, "minecraft:water");
                    if (chance(0.12)) {
                        set(x, G + 1, z, "minecraft:lily_pad");
                    }
                } else if (d <= 1.45) {
                    set(x, G, z, chance(0.5) ? "minecraft:gravel" : "minecraft:mud");
                }
            }
        }
        // haies (bordure nord et est, avec passages)
        for (int x = -43; x <= -8; x++) {
            if ((x >= -27 && x <= -23) || x == -40 || x == -12) {
                continue;
            }
            fill(x, G + 1, 8, x, G + 2, 8, chance(0.2) ? "minecraft:flowering_azalea_leaves[persistent=true]" : "minecraft:azalea_leaves[persistent=true]");
        }
        for (int z = 9; z <= 43; z++) {
            if ((z >= 20 && z <= 24) || z == 34 || z == 35) {
                continue;
            }
            fill(-8, G + 1, z, -8, G + 2, z, "minecraft:azalea_leaves[persistent=true]");
        }
        // petites haies en labyrinthe pres de la fontaine
        int[][] hedges = {{-34, 27, -29, 27}, {-20, 27, -15, 27}, {-31, 17, -28, 17}, {-20, 17, -15, 17}, {-31, 28, -31, 31}, {-19, 28, -19, 31}};
        for (int[] h : hedges) {
            for (int x = h[0]; x <= h[2]; x++) {
                for (int z = h[1]; z <= h[3]; z++) {
                    fill(x, G + 1, z, x, G + 2, z, "minecraft:azalea_leaves[persistent=true]");
                }
            }
        }
        // arbres
        int[][] oaks = {{-38, 28}, {-29, 34}, {-17, 30}, {-12, 20}, {-21, 41}, {-30, 41}, {-11, 41}};
        for (int[] o : oaks) {
            oak(o[0], o[1], 5 + rnd.nextInt(2));
        }
        bigTree(-34, 23);
        bigTree(-20, 34);
        deadTree(-10, 11);
        deadTree(-42, 21);
        // bancs le long des allees
        int[][] benches = {{-30, 20, 0}, {-19, 20, 0}, {-30, 24, 1}, {-19, 24, 1}, {-27, 14, 2}, {-23, 31, 3}};
        for (int[] b : benches) {
            String f = b[2] == 0 ? "north" : b[2] == 1 ? "south" : b[2] == 2 ? "west" : "east";
            boolean alongX = b[2] < 2;
            for (int i = -1; i <= 1; i++) {
                int x = alongX ? b[0] + i : b[0];
                int z = alongX ? b[1] : b[1] + i;
                set(x, G + 1, z, "minecraft:spruce_stairs[facing=" + f + "]");
            }
        }
        // kiosque (gazebo) avec echelle pour monter sur le toit
        int gx = -14;
        int gz = 36;
        fill(gx - 3, G, gz - 3, gx + 3, G, gz + 3, "minecraft:spruce_planks");
        for (int x = gx - 3; x <= gx + 3; x++) {
            for (int z = gz - 3; z <= gz + 3; z++) {
                boolean edge = Math.abs(x - gx) == 3 || Math.abs(z - gz) == 3;
                boolean corner = Math.abs(x - gx) == 3 && Math.abs(z - gz) == 3;
                if (corner) {
                    fill(x, G + 1, z, x, G + 3, z, "minecraft:stripped_spruce_log");
                } else if (edge && Math.abs(x - gx) != 0 && Math.abs(z - gz) != 0) {
                    set(x, G + 1, z, Math.abs(x - gx) == 3 ? "minecraft:spruce_fence[north=true,south=true]" : "minecraft:spruce_fence[east=true,west=true]");
                }
            }
        }
        for (int k = 0; k < 3; k++) {
            int r = 3 - k;
            for (int x = gx - r; x <= gx + r; x++) {
                for (int z = gz - r; z <= gz + r; z++) {
                    set(x, G + 4 + k, z, k == 0 && (Math.abs(x - gx) == r || Math.abs(z - gz) == r) ? "minecraft:dark_oak_slab[type=bottom]" : "minecraft:dark_oak_planks");
                }
            }
        }
        set(gx, G + 7, gz, "minecraft:lantern");
        set(gx, G + 3, gz, "minecraft:lantern[hanging=true]");
        fill(gx - 4, G + 1, gz - 3, gx - 4, G + 4, gz - 3, "minecraft:ladder[facing=west]");
        // aire de jeux : toboggan
        fill(-20, G + 1, 11, -20, G + 2, 11, "minecraft:oak_fence");
        fill(-19, G + 1, 11, -19, G + 2, 11, "minecraft:oak_fence");
        fill(-20, G + 1, 12, -20, G + 2, 12, "minecraft:oak_fence");
        fill(-19, G + 1, 12, -19, G + 2, 12, "minecraft:oak_fence");
        fill(-20, G + 3, 11, -19, G + 3, 12, "minecraft:oak_planks");
        fill(-21, G + 1, 11, -21, G + 3, 11, "minecraft:ladder[facing=west]");
        set(-18, G + 2, 11, "minecraft:quartz_stairs[facing=west]");
        set(-18, G + 2, 12, "minecraft:quartz_stairs[facing=west]");
        set(-17, G + 1, 11, "minecraft:quartz_stairs[facing=west]");
        set(-17, G + 1, 12, "minecraft:quartz_stairs[facing=west]");
        set(-16, G + 1, 11, "minecraft:quartz_slab");
        set(-16, G + 1, 12, "minecraft:quartz_slab");
        // balancoire
        fill(-15, G + 1, 16, -15, G + 3, 16, "minecraft:spruce_fence");
        fill(-11, G + 1, 16, -11, G + 3, 16, "minecraft:spruce_fence");
        fill(-14, G + 3, 16, -12, G + 3, 16, "minecraft:spruce_fence[east=true,west=true]");
        set(-13, G + 2, 16, "minecraft:iron_chain");
        set(-13, G + 1, 16, "minecraft:spruce_slab[type=top]");
        // lampadaires du parc
        lamp(-29, 25, 0, -1, true);
        lamp(-21, 19, 0, 1, false);
        lamp(-10, 29, 0, 1, true);
    }

    void oak(int x, int z, int h) {
        fill(x, G + 1, z, x, G + h, z, "minecraft:oak_log");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double d = dx * dx + dz * dz + dy * dy * 1.5;
                    if (d <= 5.5 && chance(0.92)) {
                        setIfAir(x + dx, G + h + dy, z + dz, "minecraft:oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    void bigTree(int x, int z) {
        fill(x, G + 1, z, x + 1, G + 7, z + 1, "minecraft:dark_oak_log");
        for (int dx = -3; dx <= 4; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -3; dz <= 4; dz++) {
                    double cx = dx - 0.5;
                    double cz = dz - 0.5;
                    double d = cx * cx + cz * cz + dy * dy * 3;
                    if (d <= 13 && chance(0.9)) {
                        setIfAir(x + dx, G + 7 + dy, z + dz, "minecraft:dark_oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    void deadTree(int x, int z) {
        int h = 4 + rnd.nextInt(3);
        fill(x, G + 1, z, x, G + h, z, "minecraft:stripped_spruce_log");
        set(x + 1, G + h - 1, z, "minecraft:spruce_log[axis=x]");
        set(x + 2, G + h, z, "minecraft:spruce_fence");
        set(x, G + h - 2, z - 1, "minecraft:spruce_log[axis=z]");
        set(x, G + h - 1, z - 2, "minecraft:spruce_fence");
        set(x - 1, G + h + 1, z, "minecraft:spruce_fence");
    }

    // ================================================================ eglise (NO)

    void church() {
        int x1 = -43;
        int x2 = -25;
        int z1 = -40;
        int z2 = -17;
        int top = G + 9;
        fill(x1, G, z1, x2, G, z2, "minecraft:polished_andesite");
        for (int y = G + 1; y <= top; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    if (edge) {
                        boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                        set(x, y, z, corner ? "minecraft:chiseled_stone_bricks" : pick("minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"));
                    }
                }
            }
        }
        // toit a deux pans (ardoise) + pignons
        int mid = (x1 + x2) / 2;
        for (int i = 0; i <= 9; i++) {
            int y = top + 1 + i;
            int xa = x1 + i;
            int xb = x2 - i;
            for (int z = z1 - 1; z <= z2; z++) {
                if (xa < mid) {
                    set(xa, y, z, "minecraft:deepslate_tile_stairs[facing=east]");
                    set(xb, y, z, "minecraft:deepslate_tile_stairs[facing=west]");
                } else {
                    set(mid, y, z, "minecraft:deepslate_tile_slab[type=bottom]");
                }
            }
            if (xa >= mid) {
                break;
            }
            for (int x = xa + 1; x <= xb - 1; x++) {
                set(x, y, z1, "minecraft:stone_bricks");
                set(x, y, z2, "minecraft:stone_bricks");
            }
        }
        // rosaces sur les pignons
        set(mid, top + 4, z1, "minecraft:purple_stained_glass");
        set(mid, top + 4, z2, "minecraft:red_stained_glass");
        // vitraux sur les longs murs
        String[] glass = {"purple", "blue", "red", "yellow", "magenta"};
        for (int z = z1 + 3; z <= z2 - 3; z += 4) {
            for (int y = G + 3; y <= G + 7; y++) {
                String g = glass[rnd.nextInt(glass.length)];
                boolean broken = chance(0.18);
                set(x1, y, z, broken ? "minecraft:air" : "minecraft:" + g + "_stained_glass_pane[north=true,south=true]");
                set(x2, y, z, chance(0.18) ? "minecraft:air" : "minecraft:" + g + "_stained_glass_pane[north=true,south=true]");
            }
        }
        // porte principale (au sud, a travers le clocher)
        fill(mid - 1, G + 1, z2, mid + 1, G + 4, z2, "minecraft:air");
        for (int x = mid - 1; x <= mid + 1; x++) {
            keep(x, G + 1, z2 - 1);
        }
        // porte laterale cote cimetiere
        fill(x2, G + 1, z1 + 6, x2, G + 3, z1 + 7, "minecraft:air");
        // allee centrale et bancs
        for (int z = z2 - 1; z >= z1 + 4; z--) {
            set(mid, G + 1, z, "minecraft:red_carpet");
        }
        for (int z = z2 - 4; z >= z1 + 8; z -= 2) {
            for (int x = x1 + 2; x <= x2 - 2; x++) {
                if (Math.abs(x - mid) <= 1) {
                    continue;
                }
                if (chance(0.82)) {
                    set(x, G + 1, z, chance(0.1) ? "minecraft:spruce_slab" : "minecraft:spruce_stairs[facing=south]");
                }
            }
        }
        // autel
        fill(x1 + 5, G + 1, z1 + 2, x2 - 5, G + 1, z1 + 3, "minecraft:polished_diorite");
        fill(mid - 2, G + 2, z1 + 2, mid + 2, G + 2, z1 + 2, "minecraft:white_carpet");
        set(mid - 2, G + 3, z1 + 2, "minecraft:candle[candles=3,lit=false]");
        set(mid + 2, G + 3, z1 + 2, "minecraft:candle[candles=2,lit=false]");
        set(mid, G + 2, z1 + 4, "minecraft:lectern[facing=south]");
        fill(mid, G + 2, z1 + 1, mid, G + 6, z1 + 1, "minecraft:dark_oak_log");
        fill(mid - 1, G + 5, z1 + 1, mid + 1, G + 5, z1 + 1, "minecraft:dark_oak_log[axis=x]");
        // tribune au-dessus de l'entree (echelle a l'ouest)
        for (int x = x1 + 1; x <= x2 - 1; x++) {
            for (int z = z2 - 3; z <= z2 - 1; z++) {
                set(x, G + 5, z, "minecraft:dark_oak_planks");
            }
            if (x != x1 + 1) {
                set(x, G + 6, z2 - 4, "minecraft:dark_oak_fence[east=true,west=true]");
            }
        }
        fill(x1 + 1, G + 1, z2 - 2, x1 + 1, G + 5, z2 - 2, "minecraft:ladder[facing=east]");
        // lustres
        for (int z = z1 + 6; z <= z2 - 6; z += 6) {
            fill(mid, G + 12, z, mid, G + 18, z, "minecraft:iron_chain");
            set(mid, G + 11, z, chance(0.5) ? "minecraft:soul_lantern[hanging=true]" : "minecraft:iron_chain");
        }
        // toiles et gravats
        for (int k = 0; k < 18; k++) {
            int x = x1 + 1 + rnd.nextInt(x2 - x1 - 1);
            int z = z1 + 1 + rnd.nextInt(z2 - z1 - 1);
            furn(x, G + 1, z, chance(0.5) ? "minecraft:cobweb" : pick("minecraft:cobblestone", "minecraft:stone_brick_slab", "minecraft:mossy_cobblestone"));
        }
        for (int k = 0; k < 10; k++) {
            setIfAir(x1 + 1 + rnd.nextInt(2), G + 7 + rnd.nextInt(3), z1 + 1 + rnd.nextInt(z2 - z1 - 1), "minecraft:cobweb");
            setIfAir(x2 - 1 - rnd.nextInt(2), G + 7 + rnd.nextInt(3), z1 + 1 + rnd.nextInt(z2 - z1 - 1), "minecraft:cobweb");
        }
        // clocher (devant la facade) : echelle jusqu'au beffroi
        int tx1 = mid - 3;
        int tx2 = mid + 3;
        int tz1 = z2;
        int tz2 = z2 + 6;
        fill(tx1 + 1, G, tz1 + 1, tx2 - 1, G, tz2 - 1, "minecraft:polished_andesite");
        for (int y = G + 1; y <= G + 20; y++) {
            for (int x = tx1; x <= tx2; x++) {
                for (int z = tz1; z <= tz2; z++) {
                    boolean edge = x == tx1 || x == tx2 || z == tz1 || z == tz2;
                    if (!edge || (z == tz1 && y <= top && x > tx1 && x < tx2)) {
                        continue;
                    }
                    boolean corner = (x == tx1 || x == tx2) && (z == tz1 || z == tz2);
                    boolean arch = y >= G + 17 && y <= G + 19 && !corner && Math.abs((x == tx1 || x == tx2) ? z - (tz1 + 3) : x - mid) <= 1;
                    if (arch) {
                        continue;
                    }
                    set(x, y, z, corner ? "minecraft:chiseled_stone_bricks" : pick("minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:mossy_stone_bricks"));
                }
            }
        }
        // face nord du clocher = facade : on reouvre le passage vers la nef
        fill(mid - 1, G + 1, tz1, mid + 1, G + 4, tz1, "minecraft:air");
        // entree sud du clocher
        fill(mid - 1, G + 1, tz2, mid + 1, G + 4, tz2, "minecraft:air");
        for (int x = mid - 1; x <= mid + 1; x++) {
            keep(x, G + 1, tz2 - 1);
            keep(x, G + 1, tz2 + 1);
        }
        // petites fenetres du clocher
        for (int y = G + 7; y <= G + 13; y += 3) {
            set(tx1, y, tz1 + 3, "minecraft:iron_bars[north=true,south=true]");
            set(tx2, y, tz1 + 2, "minecraft:iron_bars[north=true,south=true]");
        }
        // plancher du beffroi + echelle
        fill(tx1 + 1, G + 16, tz1 + 1, tx2 - 1, G + 16, tz2 - 1, "minecraft:spruce_planks");
        fill(tx2 - 1, G + 1, tz1 + 3, tx2 - 1, G + 16, tz1 + 3, "minecraft:ladder[facing=west]");
        fill(tx1 + 1, G + 20, tz1 + 1, tx2 - 1, G + 20, tz2 - 1, "minecraft:spruce_planks");
        set(mid, G + 19, tz1 + 3, "minecraft:bell[attachment=ceiling,facing=north]");
        // toit pyramidal + paratonnerre
        for (int k = 0; k <= 3; k++) {
            int r = 3 - k;
            for (int x = mid - r; x <= mid + r; x++) {
                for (int z = tz1 + 3 - r; z <= tz1 + 3 + r; z++) {
                    set(x, G + 21 + k, z, "minecraft:deepslate_tiles");
                }
            }
        }
        set(mid, G + 25, tz1 + 3, "minecraft:lightning_rod");
    }

    // ================================================================ cimetiere + crypte

    void cemetery() {
        int x1 = -22;
        int x2 = -8;
        int z1 = -42;
        int z2 = -20;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, G, z, pick("minecraft:grass_block", "minecraft:grass_block", "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:moss_block"));
            }
        }
        for (int x = x1; x <= x2; x++) {
            boolean gate = x == -15 || x == -14;
            set(x, G + 1, z1, "minecraft:iron_bars[east=true,west=true]");
            set(x, G + 2, z1, "minecraft:iron_bars[east=true,west=true]");
            if (!gate) {
                set(x, G + 1, z2, "minecraft:iron_bars[east=true,west=true]");
                set(x, G + 2, z2, "minecraft:iron_bars[east=true,west=true]");
            }
        }
        for (int z = z1; z <= z2; z++) {
            boolean gate = z == -31 || z == -30;
            set(x1, G + 1, z, "minecraft:iron_bars[north=true,south=true]");
            set(x1, G + 2, z, "minecraft:iron_bars[north=true,south=true]");
            if (!gate) {
                set(x2, G + 1, z, "minecraft:iron_bars[north=true,south=true]");
                set(x2, G + 2, z, "minecraft:iron_bars[north=true,south=true]");
            }
        }
        int[][] posts = {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}, {-16, z2}, {-13, z2}, {x2, -32}, {x2, -29}};
        for (int[] p : posts) {
            fill(p[0], G + 1, p[1], p[0], G + 2, p[1], "minecraft:stone_bricks");
            set(p[0], G + 3, p[1], "minecraft:soul_lantern");
        }
        // allee jusqu'au mausolee
        for (int z = -29; z <= z2; z++) {
            set(-15, G, z, "minecraft:gravel");
            set(-14, G, z, "minecraft:gravel");
        }
        for (int x = -14; x <= x2; x++) {
            set(x, G, -31, "minecraft:gravel");
            set(x, G, -30, "minecraft:gravel");
        }
        // tombes
        for (int x = -21; x <= -9; x += 3) {
            if (x >= -17 && x <= -12) {
                continue;
            }
            for (int z : new int[]{-39, -35, -27, -24}) {
                grave(x, z);
            }
        }
        for (int x = -20; x <= -10; x += 3) {
            grave(x, -41 + 1);
        }
        // mausolee (les zombies en sortent)
        int mx1 = -18;
        int mx2 = -11;
        int mz1 = -37;
        int mz2 = -31;
        fill(mx1, G, mz1, mx2, G, mz2, "minecraft:deepslate_tiles");
        for (int y = G + 1; y <= G + 5; y++) {
            for (int x = mx1; x <= mx2; x++) {
                for (int z = mz1; z <= mz2; z++) {
                    if (x == mx1 || x == mx2 || z == mz1 || z == mz2) {
                        set(x, y, z, pick("minecraft:deepslate_bricks", "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:mossy_cobblestone"));
                    }
                }
            }
        }
        fill(mx1 - 1, G + 6, mz1 - 1, mx2 + 1, G + 6, mz2 + 1, "minecraft:polished_deepslate_slab[type=bottom]");
        fill(mx1, G + 6, mz1, mx2, G + 6, mz2, "minecraft:polished_deepslate");
        fill(mx1 + 2, G + 7, mz1 + 1, mx2 - 2, G + 7, mz2 - 1, "minecraft:polished_deepslate_slab[type=bottom]");
        fill(-15, G + 1, mz2, -14, G + 3, mz2, "minecraft:air");
        set(-16, G + 4, mz2 + 1, "minecraft:soul_lantern");
        set(-13, G + 4, mz2 + 1, "minecraft:soul_lantern");
        fill(-16, G + 1, mz2 + 1, -16, G + 3, mz2 + 1, "minecraft:polished_deepslate_wall");
        fill(-13, G + 1, mz2 + 1, -13, G + 3, mz2 + 1, "minecraft:polished_deepslate_wall");
        // interieur : cercueil, bougies, cranes, grille vers la crypte
        fill(-16, G + 1, -35, -13, G + 1, -35, "minecraft:dark_oak_planks");
        set(-17, G + 1, -36, "minecraft:candle[candles=4,lit=false]");
        set(-12, G + 1, -36, "minecraft:skeleton_skull[rotation=8]");
        fill(-17, G, -33, -16, G, -33, "minecraft:iron_bars[east=true,west=true]");
        for (int x = -15; x <= -14; x++) {
            for (int z = -29; z <= -26; z++) {
                keep(x, G + 1, z);
            }
        }
        deadTree(-10, -38);
        deadTree(-21, -29);
    }

    void grave(int x, int z) {
        set(x, G + 1, z, pick("minecraft:stone_brick_wall", "minecraft:mossy_cobblestone_wall", "minecraft:andesite_wall", "minecraft:polished_andesite", "minecraft:cobblestone_wall"));
        if (chance(0.22)) {
            // tombe ouverte
            set(x, G, z + 1, "minecraft:air");
            set(x, G, z + 2, "minecraft:air");
            set(x, G - 1, z + 1, "minecraft:coarse_dirt");
            set(x, G - 1, z + 2, "minecraft:coarse_dirt");
            set(x + 1, G + 1, z + 1, "minecraft:coarse_dirt");
        } else {
            set(x, G, z + 1, "minecraft:podzol");
            set(x, G, z + 2, "minecraft:coarse_dirt");
            if (chance(0.15)) {
                set(x, G + 1, z + 2, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
            } else if (chance(0.2)) {
                set(x, G + 1, z + 2, "minecraft:dead_bush");
            }
        }
    }

    void crypt() {
        // salle fermee sous le cimetiere : les zombies y attendent (aucune sortie)
        fill(-20, G - 11, -38, -8, G - 5, -28, "minecraft:deepslate_bricks");
        fill(-19, G - 9, -37, -9, G - 6, -29, "minecraft:air");
        fill(-19, G - 10, -37, -9, G - 10, -29, "minecraft:deepslate_tiles");
        for (int x = -19; x <= -9; x += 5) {
            set(x, G - 6, -33, "minecraft:soul_lantern[hanging=true]");
        }
        for (int z = -36; z <= -30; z += 3) {
            fill(-19, G - 9, z, -18, G - 9, z, "minecraft:dark_oak_planks");
            fill(-10, G - 9, z, -9, G - 9, z, "minecraft:dark_oak_planks");
        }
        set(-19, G - 8, -37, "minecraft:skeleton_skull[rotation=6]");
        set(-9, G - 8, -37, "minecraft:skeleton_skull[rotation=10]");
        set(-14, G - 9, -37, "minecraft:soul_campfire[lit=true]");
        set(-19, G - 6, -29, "minecraft:cobweb");
        set(-9, G - 6, -29, "minecraft:cobweb");
        set(-19, G - 6, -37, "minecraft:cobweb");
        set(-9, G - 6, -37, "minecraft:cobweb");
    }

    // ================================================================ maisons

    static final String[][] STYLES = {
        // mur, coins, plancher, escalier, toit, fenetres cassees
        {"minecraft:bricks", "minecraft:stripped_dark_oak_log", "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:smooth_stone"},
        {"minecraft:white_terracotta", "minecraft:stripped_oak_log", "minecraft:oak_planks", "minecraft:oak_stairs", "minecraft:gray_concrete"},
        {"minecraft:stone_bricks", "minecraft:polished_andesite", "minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:polished_andesite"},
        {"minecraft:light_gray_terracotta", "minecraft:stripped_spruce_log", "minecraft:birch_planks", "minecraft:birch_stairs", "minecraft:smooth_stone"},
        {"minecraft:mud_bricks", "minecraft:stripped_jungle_log", "minecraft:jungle_planks", "minecraft:jungle_stairs", "minecraft:packed_mud"},
        {"minecraft:yellow_terracotta", "minecraft:stripped_birch_log", "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:gray_concrete"}
    };

    /** Maison rectangulaire : 1 ou 2 etages, porte cote `door` (N/S/E/W), echelle vers le toit-terrasse a l'arriere. */
    void house(int x1, int z1, int x2, int z2, int floors, char door, int style, boolean ruined) {
        String[] st = STYLES[style];
        String wall = st[0];
        String corner = st[1];
        String floor = st[2];
        String stair = st[3];
        String roof = st[4];
        int top = G + 5 * floors;
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        char back = switch (door) {
            case 'N' -> 'S';
            case 'S' -> 'N';
            case 'E' -> 'W';
            default -> 'E';
        };
        char side = (door == 'N' || door == 'S') ? 'E' : 'S';
        fill(x1, G, z1, x2, G, z2, floor);
        for (int y = G + 1; y < top; y++) {
            boolean beam = (y - G) % 5 == 0;
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    if (x == x1 || x == x2 || z == z1 || z == z2) {
                        boolean c = (x == x1 || x == x2) && (z == z1 || z == z2);
                        set(x, y, z, c ? corner : beam ? floor : (chance(0.04) ? "minecraft:cobblestone" : wall));
                    }
                }
            }
        }
        for (int f = 1; f < floors; f++) {
            fill(x1 + 1, G + 5 * f, z1 + 1, x2 - 1, G + 5 * f, z2 - 1, floor);
        }
        fill(x1, top, z1, x2, top, z2, roof);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x == x1 || x == x2 || z == z1 || z == z2) {
                    set(x, top + 1, z, wall);
                }
            }
        }
        // porte d'entree (2 de large), porte de cote, echelle a l'arriere
        int[] dc = wallCell(door, x1, z1, x2, z2, 0.5);
        int[] dn = normal(door);
        int[] dc2 = {dc[0] - Math.abs(dn[1]), dc[1] - Math.abs(dn[0])};
        for (int[] c : new int[][]{dc, dc2}) {
            fill(c[0], G + 1, c[1], c[0], G + 2, c[1], "minecraft:air");
            keep(c[0] - dn[0], G + 1, c[1] - dn[1]);
            keep(c[0] + dn[0], G + 1, c[1] + dn[1]);
        }
        int[] sc = wallCell(side, x1, z1, x2, z2, 0.5);
        int[] sn = normal(side);
        fill(sc[0], G + 1, sc[1], sc[0], G + 2, sc[1], "minecraft:air");
        keep(sc[0] - sn[0], G + 1, sc[1] - sn[1]);
        int[] lc = wallCell(back, x1, z1, x2, z2, 0.0);
        int[] ln = normal(back);
        fill(lc[0] + ln[0], G + 1, lc[1] + ln[1], lc[0] + ln[0], top, lc[1] + ln[1], "minecraft:ladder[facing=" + facing(ln[0], ln[1]) + "]");
        set(lc[0], top + 1, lc[1], "minecraft:air");
        // fenetres
        for (char s : new char[]{'N', 'S', 'E', 'W'}) {
            int len = (s == 'N' || s == 'S') ? x2 - x1 - 1 : z2 - z1 - 1;
            String pane = (s == 'N' || s == 'S') ? "[east=true,west=true]" : "[north=true,south=true]";
            for (int i = 0; i < len; i++) {
                if (i % 3 != 1) {
                    continue;
                }
                int x = (s == 'N' || s == 'S') ? x1 + 1 + i : (s == 'W' ? x1 : x2);
                int z = (s == 'E' || s == 'W') ? z1 + 1 + i : (s == 'N' ? z1 : z2);
                if (!air(x, G + 1, z) && !(s == back && x == lc[0] && z == lc[1])) {
                    for (int f = 0; f < floors; f++) {
                        double r = rnd.nextDouble();
                        String b = r < 0.45 ? "minecraft:glass_pane" + pane : r < 0.7 ? "minecraft:air" : "minecraft:spruce_planks";
                        set(x, G + 2 + 5 * f, z, b);
                        set(x, G + 3 + 5 * f, z, r >= 0.7 && chance(0.5) ? "minecraft:air" : b);
                    }
                }
            }
        }
        int ix1 = x1 + 1;
        int ix2 = x2 - 1;
        int iz1 = z1 + 1;
        int iz2 = z2 - 1;
        // escalier vers l'etage (le long de x, contre le mur oppose a la porte)
        if (floors >= 2 && ix2 - ix1 >= 6) {
            int row = door == 'N' ? iz2 : iz1;
            for (int k = 0; k <= 4; k++) {
                int x = ix1 + 1 + k;
                set(x, G + 1 + k, row, stair + "[facing=east]");
                if (k <= 3) {
                    set(x, G + 5, row, "minecraft:air");
                }
                for (int y = G + 2 + k; y <= G + 3 + k; y++) {
                    keep.add(key(x, y, row));
                }
                keep(x, G + 1, row);
            }
            keep(ix1 + 6, G + 6, row);
            keep(ix1, G + 1, row);
            int rail = row == iz1 ? row + 1 : row - 1;
            for (int x = ix1 + 1; x <= ix1 + 3; x++) {
                set(x, G + 6, rail, "minecraft:" + floorWood(floor) + "_fence[east=true,west=true]");
            }
        }
        // meubles
        for (int f = 0; f < floors; f++) {
            int y = G + 1 + 5 * f;
            int ceil = G + 4 + 5 * f;
            if (f == 0) {
                int cx = (ix1 + ix2) / 2;
                int cz = (iz1 + iz2) / 2 + (door == 'N' ? 1 : 0);
                furn(cx, y, cz, "minecraft:" + floorWood(floor) + "_fence");
                furn(cx, y + 1, cz, "minecraft:" + floorWood(floor) + "_pressure_plate");
                furn(cx - 1, y, cz, stair + "[facing=west]");
                furn(cx + 1, y, cz, stair + "[facing=east]");
                furn(ix2, y, iz2, "minecraft:bookshelf");
                furn(ix2, y + 1, iz2, chance(0.5) ? "minecraft:bookshelf" : "minecraft:potted_dead_bush");
                furn(ix2, y, iz2 - 1, "minecraft:furnace[facing=west]");
                furn(ix2, y, iz2 - 2, "minecraft:cauldron");
                furn(ix1, y, iz2, "minecraft:barrel[facing=up]");
                furn(ix1, y + 1, iz2, "minecraft:barrel[facing=up]");
                furn(ix1, y, iz2 - 1, "minecraft:crafting_table");
            } else {
                String bed = pick("red", "blue", "white", "green", "brown");
                if (air(ix2, y, iz2) && air(ix2 - 1, y, iz2) && !keep.contains(key(ix2 - 1, y, iz2))) {
                    set(ix2, y, iz2, "minecraft:" + bed + "_bed[facing=east,part=head]");
                    set(ix2 - 1, y, iz2, "minecraft:" + bed + "_bed[facing=east,part=foot]");
                }
                furn(ix1, y, iz2, "minecraft:barrel[facing=north]");
                furn(ix1, y + 1, iz2, "minecraft:barrel[facing=north]");
                furn(ix1 + 1, y, iz2, "minecraft:chiseled_bookshelf");
                furn(ix2, y, iz1 + 1, "minecraft:lectern[facing=west]");
                furn((ix1 + ix2) / 2, y, (iz1 + iz2) / 2, "minecraft:" + pick("red", "gray", "brown", "cyan") + "_carpet");
            }
            if (chance(0.55)) {
                furn((ix1 + ix2) / 2, ceil, (iz1 + iz2) / 2, "minecraft:lantern[hanging=true]");
            }
            furn(ix1, ceil, iz1, "minecraft:cobweb");
            furn(ix2, ceil, iz2, "minecraft:cobweb");
            if (chance(0.5)) {
                furn(ix1 + rnd.nextInt(Math.max(1, ix2 - ix1)), y, iz1 + rnd.nextInt(Math.max(1, iz2 - iz1)), pick("minecraft:cobweb", "minecraft:composter", "minecraft:barrel[facing=up]"));
            }
        }
        // toit : cheminee + citerne
        fill(x2 - 1, top + 1, z2 - 1, x2 - 1, top + 2, z2 - 1, "minecraft:bricks");
        if (x2 - x1 >= 8) {
            set(x1 + 3, top + 1, mz, "minecraft:barrel[facing=up]");
            set(x1 + 3, top + 2, mz, "minecraft:spruce_trapdoor[half=bottom]");
        }
        if (ruined) {
            for (int x = x1 + 1; x <= x2 - 1; x++) {
                for (int z = z1 + 1; z <= z2 - 1; z++) {
                    if (chance(0.14)) {
                        set(x, top, z, "minecraft:air");
                    }
                }
            }
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    boolean c = (x == x1 || x == x2) && (z == z1 || z == z2);
                    if (edge && !c && !(x == lc[0] && z == lc[1])) {
                        if (chance(0.25)) {
                            set(x, top + 1, z, "minecraft:air");
                        }
                        if (chance(0.08)) {
                            set(x, top - 1, z, "minecraft:air");
                        }
                    }
                }
            }
            for (int k = 0; k < 4; k++) {
                setIfAir(x1 - 1 - rnd.nextInt(2) + (rnd.nextBoolean() ? x2 - x1 + 2 + rnd.nextInt(2) : 0), G + 1, z1 + rnd.nextInt(z2 - z1 + 1), pick("minecraft:cobblestone", "minecraft:gravel", "minecraft:cobblestone_slab"));
            }
        }
    }

    static String floorWood(String floor) {
        return floor.replace("minecraft:", "").replace("_planks", "");
    }

    /** Case du mur `s` a la fraction t de sa longueur (hors coins). */
    static int[] wallCell(char s, int x1, int z1, int x2, int z2, double t) {
        return switch (s) {
            case 'N' -> new int[]{x1 + 1 + (int) Math.round((x2 - x1 - 2) * t), z1};
            case 'S' -> new int[]{x1 + 1 + (int) Math.round((x2 - x1 - 2) * t), z2};
            case 'W' -> new int[]{x1, z1 + 1 + (int) Math.round((z2 - z1 - 2) * t)};
            default -> new int[]{x2, z1 + 1 + (int) Math.round((z2 - z1 - 2) * t)};
        };
    }

    static int[] normal(char s) {
        return switch (s) {
            case 'N' -> new int[]{0, -1};
            case 'S' -> new int[]{0, 1};
            case 'W' -> new int[]{-1, 0};
            default -> new int[]{1, 0};
        };
    }

    void houses() {
        // NO, le long de la grande rue
        house(-28, -15, -20, -7, 2, 'S', 0, false);
        house(-17, -16, -9, -7, 1, 'S', 3, true);
        house(-44, -15, -40, -8, 1, 'E', 2, true);
        // SO, dans le parc
        house(-43, 33, -34, 43, 2, 'E', 1, false);
        // SE, le long de la ruelle
        house(8, 28, 16, 36, 2, 'N', 5, false);
        house(20, 28, 28, 37, 2, 'N', 4, true);
        house(33, 28, 43, 41, 2, 'N', 2, false);
        // cabane de jardin
        house(23, 39, 28, 44, 1, 'N', 3, true);
        // jardins derriere les maisons du SE : cloture avec passages
        for (int x = 7; x <= 44; x++) {
            if (x % 7 == 3 || (x >= 23 && x <= 28)) {
                continue;
            }
            setIfAir(x, G + 1, 43, "minecraft:spruce_fence[east=true,west=true]");
        }
        deadTree(12, 40);
        deadTree(30, 41);
    }

    // ================================================================ supermarche (NE)

    void supermarket() {
        int x1 = 10;
        int x2 = 42;
        int z1 = -42;
        int z2 = -24;
        int top = G + 7;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, G, z, (x + z) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:light_gray_concrete");
            }
        }
        for (int y = G + 1; y < top; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    if (x == x1 || x == x2 || z == z1 || z == z2) {
                        set(x, y, z, y == G + 5 ? "minecraft:cyan_terracotta" : chance(0.05) ? "minecraft:gray_concrete" : "minecraft:light_gray_concrete");
                    }
                }
            }
        }
        // toit avec lumieres (certaines eteintes) et puits de lumiere
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean lamp = x > x1 && x < x2 && z > z1 && z < z2 && Math.floorMod(x - x1, 5) == 2 && Math.floorMod(z - z1, 5) == 2;
                set(x, top, z, lamp ? (chance(0.5) ? "minecraft:sea_lantern" : "minecraft:glass") : "minecraft:smooth_stone");
                if (x == x1 || x == x2 || z == z1 || z == z2) {
                    set(x, top + 1, z, "minecraft:light_gray_concrete");
                }
            }
        }
        // facade sud : grandes vitrines (cassees ou condamnees), entree
        for (int x = x1 + 2; x <= x2 - 2; x++) {
            boolean entrance = x >= 24 && x <= 28;
            for (int y = G + 1; y <= G + 4; y++) {
                if (entrance) {
                    set(x, y, z2, y <= G + 3 ? "minecraft:air" : "minecraft:glass_pane[east=true,west=true]");
                } else {
                    double r = rnd.nextDouble();
                    set(x, y, z2, r < 0.5 ? "minecraft:glass_pane[east=true,west=true]" : r < 0.75 ? "minecraft:air" : "minecraft:spruce_planks");
                }
            }
        }
        for (int x = 24; x <= 28; x++) {
            keep(x, G + 1, z2 - 1);
            keep(x, G + 1, z2 + 1);
        }
        // enseigne
        for (int x = 18; x <= 34; x++) {
            set(x, G + 6, z2, x % 2 == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete");
            set(x, G + 5, z2, "minecraft:red_concrete");
        }
        set(26, G + 6, z2, "minecraft:yellow_concrete");
        // rayons
        String[] goods = {"minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block", "minecraft:bookshelf", "minecraft:white_wool", "minecraft:red_wool", "minecraft:brown_mushroom_block", "minecraft:dried_kelp_block", "minecraft:honeycomb_block", "minecraft:barrel[facing=up]", "minecraft:target", "minecraft:bee_nest[facing=south]"};
        for (int z : new int[]{-39, -36, -33, -30}) {
            for (int x = 13; x <= 33; x++) {
                if (x >= 22 && x <= 24) {
                    continue;
                }
                if (chance(0.12)) {
                    // rayon renverse
                    if (chance(0.5)) {
                        set(x, G + 1, z + 1, goods[rnd.nextInt(goods.length)]);
                    }
                    continue;
                }
                set(x, G + 1, z, "minecraft:spruce_planks");
                set(x, G + 2, z, goods[rnd.nextInt(goods.length)]);
                if (chance(0.5)) {
                    set(x, G + 3, z, "minecraft:spruce_slab");
                }
            }
        }
        // congelateurs au fond
        for (int x = x1 + 2; x <= 33; x++) {
            set(x, G + 1, z1 + 1, "minecraft:packed_ice");
            set(x, G + 2, z1 + 1, chance(0.3) ? "minecraft:air" : "minecraft:light_blue_stained_glass");
        }
        // caisses
        for (int x = 13; x <= 21; x += 4) {
            set(x, G + 1, -27, "minecraft:smooth_quartz");
            set(x + 1, G + 1, -27, "minecraft:smooth_quartz");
            set(x, G + 2, -27, "minecraft:loom");
            set(x + 1, G + 2, -27, "minecraft:quartz_slab");
        }
        for (int x = 30; x <= 34; x += 4) {
            set(x, G + 1, -27, "minecraft:smooth_quartz");
            set(x, G + 2, -27, "minecraft:quartz_slab");
        }
        // reserve a l'est (mur avec passage) + echelle interieure vers le toit
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            boolean door = z == -33 || z == -32;
            if (!door) {
                fill(36, G + 1, z, 36, G + 6, z, "minecraft:light_gray_concrete");
            }
        }
        keep(35, G + 1, -33);
        keep(35, G + 1, -32);
        keep(37, G + 1, -33);
        keep(37, G + 1, -32);
        for (int k = 0; k < 14; k++) {
            int x = 37 + rnd.nextInt(5);
            int z = z1 + 1 + rnd.nextInt(z2 - z1 - 1);
            int h = 1 + rnd.nextInt(3);
            for (int y = G + 1; y < G + 1 + h; y++) {
                furn(x, y, z, pick("minecraft:barrel[facing=up]", "minecraft:spruce_planks", "minecraft:stripped_spruce_log", "minecraft:hay_block"));
            }
        }
        fill(x2 - 1, G + 1, z1 + 1, x2 - 1, top, z1 + 1, "minecraft:ladder[facing=west]");
        for (int y = G + 1; y <= G + 2; y++) {
            keep.add(key(x2 - 2, y, z1 + 1));
        }
        // echelle exterieure sur la facade est
        fill(x2 + 1, G + 1, -30, x2 + 1, top, -30, "minecraft:ladder[facing=east]");
        set(x2, top + 1, -30, "minecraft:air");
        // toit : climatiseurs
        int[][] hvac = {{15, -38}, {22, -30}, {31, -37}, {38, -28}};
        for (int[] h : hvac) {
            fill(h[0], top + 1, h[1], h[0] + 1, top + 2, h[1] + 1, "minecraft:iron_block");
            set(h[0], top + 3, h[1], "minecraft:iron_trapdoor[half=bottom]");
        }
        // toiles et caddies renverses
        for (int k = 0; k < 10; k++) {
            furn(x1 + 1 + rnd.nextInt(x2 - x1 - 1), G + 1 + rnd.nextInt(3), z1 + 1 + rnd.nextInt(z2 - z1 - 1), "minecraft:cobweb");
        }
        // abribus devant le parking
        fill(20, G + 1, -5, 20, G + 3, -5, "minecraft:iron_bars");
        fill(24, G + 1, -5, 24, G + 3, -5, "minecraft:iron_bars");
        fill(20, G + 4, -5, 24, G + 4, -4, "minecraft:smooth_stone_slab");
        fill(21, G + 1, -5, 23, G + 3, -5, "minecraft:glass_pane[east=true,west=true]");
        set(22, G + 2, -5, "minecraft:air");
        fill(21, G + 1, -4, 23, G + 1, -4, "minecraft:spruce_slab");
    }

    // ================================================================ station-service (SE)

    void gasStation() {
        for (int x = 7; x <= 27; x++) {
            for (int z = 7; z <= 22; z++) {
                set(x, G, z, chance(0.88) ? "minecraft:light_gray_concrete" : "minecraft:cracked_stone_bricks");
            }
        }
        // auvent sur 4 piliers, echelle sur le pilier nord-ouest
        int[][] pil = {{11, 10}, {23, 10}, {11, 20}, {23, 20}};
        for (int[] p : pil) {
            fill(p[0], G + 1, p[1], p[0], G + 4, p[1], "minecraft:white_concrete");
        }
        for (int x = 10; x <= 24; x++) {
            for (int z = 10; z <= 20; z++) {
                boolean edge = x == 10 || x == 24 || z == 10 || z == 20;
                boolean light = !edge && x % 4 == 1 && z % 4 == 3;
                set(x, G + 5, z, edge ? "minecraft:red_concrete" : light ? (chance(0.5) ? "minecraft:sea_lantern" : "minecraft:smooth_stone") : "minecraft:white_concrete");
                if (edge) {
                    set(x, G + 6, z, "minecraft:red_concrete");
                }
            }
        }
        fill(11, G + 1, 9, 11, G + 5, 9, "minecraft:ladder[facing=north]");
        set(11, G + 6, 10, "minecraft:air");
        // ilots et pompes
        for (int x = 13; x <= 21; x++) {
            set(x, G + 1, 15, "minecraft:smooth_stone_slab");
        }
        for (int x : new int[]{14, 18, 21}) {
            set(x, G + 1, 15, "minecraft:red_concrete");
            set(x, G + 2, 15, "minecraft:red_concrete");
            set(x, G + 3, 15, "minecraft:white_concrete");
            set(x, G + 2, 16, "minecraft:tripwire_hook[facing=south]");
        }
        // boutique de la station
        house(29, 8, 38, 17, 1, 'W', 1, false);
        for (int z = 10; z <= 15; z++) {
            if (z != 12 && z != 13) {
                set(29, G + 2, z, "minecraft:glass_pane[north=true,south=true]");
                set(29, G + 3, z, chance(0.5) ? "minecraft:air" : "minecraft:glass_pane[north=true,south=true]");
            }
        }
        fill(31, G + 1, 10, 31, G + 1, 11, "minecraft:smooth_quartz");
        fill(34, G + 1, 10, 36, G + 1, 10, "minecraft:spruce_planks");
        fill(34, G + 2, 10, 36, G + 2, 10, "minecraft:dried_kelp_block");
        // enseigne sur poteau
        fill(26, G + 1, 21, 26, G + 6, 21, "minecraft:stone_brick_wall");
        fill(25, G + 7, 21, 27, G + 8, 21, "minecraft:red_concrete");
        set(26, G + 7, 21, "minecraft:yellow_concrete");
        set(26, G + 8, 21, "minecraft:yellow_concrete");
        // bidons
        int[][] cans = {{8, 20}, {9, 20}, {8, 19}, {27, 8}};
        for (int[] c : cans) {
            set(c[0], G + 1, c[1], pick("minecraft:red_concrete", "minecraft:cauldron", "minecraft:barrel[facing=up]"));
        }
    }

    // ================================================================ boulevard et quartiers exterieurs (agrandissement du 15/11/2026)

    /** Boulevard carre autour de l'ancienne ville : chaussee de 48 a 52, trottoirs en 47 et 53. */
    void ring() {
        for (int x = -53; x <= 53; x++) {
            for (int z = -53; z <= 53; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d < 47) {
                    continue;
                }
                if (d == 47 || d == 53) {
                    set(x, G, z, pavement());
                    continue;
                }
                int t = Math.abs(x) == d ? z : x;
                boolean line = d == 50 && Math.abs(t) < 48 && Math.floorMod(t, 4) < 2 && chance(0.85);
                set(x, G, z, line ? "minecraft:yellow_terracotta" : asphalt());
            }
        }
        // trottoir exterieur (54..55) et ruelle de service entre les deux rangees de maisons (65..67)
        for (int side = 0; side < 4; side++) {
            for (int t = -53; t <= 53; t++) {
                if (Math.abs(t) <= 5) {
                    continue;
                }
                for (int a = 54; a <= 67; a++) {
                    if (a > 55 && a < 65) {
                        continue;
                    }
                    int[] c = cell(side, t, a);
                    set(c[0], G, c[1], a <= 55 ? pavement() : pick("minecraft:gravel", "minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:cobblestone"));
                }
            }
        }
    }

    /** Case (t le long du cote, a la distance au centre) d'un cote : 0 nord, 1 sud, 2 ouest, 3 est. */
    static int[] cell(int side, int t, int a) {
        return switch (side) {
            case 0 -> new int[]{t, -a};
            case 1 -> new int[]{t, a};
            case 2 -> new int[]{-a, t};
            default -> new int[]{a, t};
        };
    }

    /** Deux rangees de maisons sur chaque cote, portes vers le centre ; quatre coins a theme. */
    void ringHouses() {
        char[] doors = {'S', 'N', 'E', 'W'};
        for (int side = 0; side < 4; side++) {
            for (int row = 0; row < 2; row++) {
                int near = row == 0 ? 56 : 68;
                int t = -53;
                while (t <= 53) {
                    int t2 = t + 6 + rnd.nextInt(5);
                    if (t2 > 53) {
                        break;
                    }
                    if (t2 >= -7 && t <= 7) {
                        t = 8;
                        continue;
                    }
                    int depth = row == 0 ? 7 + rnd.nextInt(3) : 6 + rnd.nextInt(3);
                    int far = near + depth - 1;
                    int[] p = cell(side, t, near);
                    int[] q = cell(side, t2, far);
                    int x1 = Math.min(p[0], q[0]);
                    int x2 = Math.max(p[0], q[0]);
                    int z1 = Math.min(p[1], q[1]);
                    int z2 = Math.max(p[1], q[1]);
                    if (row == 1 && chance(0.25)) {
                        yard(x1, z1, x2, z2);
                    } else {
                        int floors = 1 + (chance(row == 0 ? 0.45 : 0.3) ? 1 : 0);
                        house(x1, z1, x2, z2, floors, doors[side], rnd.nextInt(STYLES.length), chance(0.3));
                    }
                    t = t2 + 3 + rnd.nextInt(3);
                }
            }
        }
        // coins : parc (NO), entrepot (NE), foret morte (SO), casse automobile (SE)
        cornerPark(-76, -76);
        house(58, -74, 74, -60, 1, 'S', 2, true);
        for (int k = 0; k < 14; k++) {
            int x = -76 + rnd.nextInt(21);
            int z = 56 + rnd.nextInt(21);
            if (air(x, G + 1, z)) {
                deadTree(x, z);
            }
        }
        for (int k = 0; k < 40; k++) {
            int x = -76 + rnd.nextInt(21);
            int z = 56 + rnd.nextInt(21);
            setIfAir(x, G + 1, z, pick("minecraft:cobweb", "minecraft:dead_bush", "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]"));
        }
        for (int x = 56; x <= 76; x++) {
            for (int z = 56; z <= 76; z++) {
                set(x, G, z, pick("minecraft:gravel", "minecraft:coarse_dirt", "minecraft:gravel", "minecraft:mud"));
            }
        }
        int[][] wrecks = {{57, 58, 1}, {64, 57, 0}, {70, 60, 1}, {58, 66, 0}, {66, 65, 1}, {72, 68, 0}, {60, 72, 1}, {67, 73, 0}};
        for (int[] c : wrecks) {
            car(c[0], c[1], c[2] == 1, chance(0.7));
        }
        for (int k = 0; k < 25; k++) {
            setIfAir(56 + rnd.nextInt(21), G + 1, 56 + rnd.nextInt(21), pick("minecraft:barrel[facing=up]", "minecraft:iron_block", "minecraft:anvil", "minecraft:cobweb", "minecraft:smooth_stone_slab"));
        }
    }

    /** Arriere-cour : terre, cabane a outils, arbre, cloture cassee. */
    void yard(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, G, z, pick("minecraft:coarse_dirt", "minecraft:grass_block", "minecraft:podzol", "minecraft:grass_block"));
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (edge && chance(0.45)) {
                    set(x, G + 1, z, "minecraft:spruce_fence");
                }
            }
        }
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        if (chance(0.5)) {
            deadTree(cx, cz);
        } else {
            oak(cx, cz, 4 + rnd.nextInt(2));
        }
        setIfAir(x1 + 1, G + 1, z1 + 1, pick("minecraft:barrel[facing=up]", "minecraft:composter", "minecraft:hay_block"));
        setIfAir(x2 - 1, G + 1, z2 - 1, pick("minecraft:cauldron", "minecraft:barrel[facing=up]", "minecraft:spruce_planks"));
    }

    /** Parc d'angle avec etang, arbres et bancs (x0..x0+20, z0..z0+20). */
    void cornerPark(int x0, int z0) {
        for (int x = x0; x <= x0 + 20; x++) {
            for (int z = z0; z <= z0 + 20; z++) {
                set(x, G, z, chance(0.85) ? "minecraft:grass_block" : "minecraft:moss_block");
                double dx = (x - (x0 + 10)) / 5.0;
                double dz = (z - (z0 + 10)) / 4.0;
                if (dx * dx + dz * dz <= 1.0) {
                    set(x, G - 1, z, "minecraft:mud");
                    set(x, G, z, "minecraft:water");
                    if (chance(0.1)) {
                        set(x, G + 1, z, "minecraft:lily_pad");
                    }
                }
            }
        }
        int[][] trees = {{x0 + 2, z0 + 2}, {x0 + 18, z0 + 3}, {x0 + 3, z0 + 17}, {x0 + 17, z0 + 18}, {x0 + 10, z0 + 2}, {x0 + 2, z0 + 10}};
        for (int[] tr : trees) {
            if (chance(0.5)) {
                oak(tr[0], tr[1], 4 + rnd.nextInt(3));
            } else {
                deadTree(tr[0], tr[1]);
            }
        }
        for (int x = x0 + 6; x <= x0 + 14; x += 4) {
            setIfAir(x, G + 1, z0 + 16, "minecraft:spruce_stairs[facing=north]");
        }
    }

    // ================================================================ voitures, lampadaires, barricades

    static final String[] CAR_COLORS = {"red", "blue", "white", "light_gray", "black", "green", "yellow", "cyan", "orange"};

    void car(int x, int z, boolean alongX, boolean burned) {
        String color = CAR_COLORS[rnd.nextInt(CAR_COLORS.length)];
        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 1; j++) {
                int bx = alongX ? x + i : x + j;
                int bz = alongX ? z + j : z + i;
                String body = burned ? pick("minecraft:black_concrete", "minecraft:coal_block", "minecraft:blackstone") : "minecraft:" + color + "_concrete";
                set(bx, G + 1, bz, (i == 0 || i == 4) && !burned ? "minecraft:" + color + "_terracotta" : body);
                if (i >= 1 && i <= 3) {
                    String cab = i == 2 ? (burned ? "minecraft:air" : "minecraft:" + color + "_concrete") : (burned ? "minecraft:air" : "minecraft:gray_stained_glass");
                    set(bx, G + 2, bz, cab);
                    if (!burned) {
                        set(bx, G + 3, bz, "minecraft:" + color + "_carpet");
                    } else if (chance(0.3)) {
                        set(bx, G + 2, bz, "minecraft:cobweb");
                    }
                }
            }
        }
    }

    void cars() {
        car(-38, -2, true, false);
        car(-21, 1, true, false);
        car(11, -2, true, true);
        car(30, 1, true, false);
        car(38, -2, true, false);
        car(-2, -38, false, false);
        car(1, -20, false, false);
        car(-2, 12, false, true);
        car(1, 32, false, false);
        car(11, -20, false, false);
        car(19, -20, false, true);
        car(31, -20, false, false);
        car(39, -20, false, false);
        car(15, -12, false, false);
        car(27, -12, false, false);
        car(35, -12, false, true);
        car(13, 12, true, false);
        car(30, 25, true, false);
        car(-2, 56, false, true);
        car(-30, 49, true, false);
        car(20, -51, true, true);
        car(49, -30, false, false);
        car(-51, 22, false, false);
        car(38, 51, true, false);
        car(-49, -40, false, true);
        car(-60, 1, true, false);
        car(66, -2, true, true);
        car(1, -64, false, false);
    }

    void lamp(int x, int z, int dx, int dz, boolean on) {
        fill(x, G + 1, z, x, G + 4, z, "minecraft:polished_blackstone_wall");
        set(x, G + 5, z, "minecraft:polished_blackstone");
        set(x + dx, G + 5, z + dz, "minecraft:polished_blackstone_slab[type=top]");
        if (on) {
            set(x + dx, G + 4, z + dz, "minecraft:lantern[hanging=true]");
        } else if (chance(0.5)) {
            set(x + dx, G + 4, z + dz, "minecraft:iron_chain");
        }
    }

    void lamps() {
        int[] at = {-71, -59, -41, -29, -17, 17, 29, 41, 59, 71};
        for (int a : at) {
            if (Math.abs(a) > 45 && Math.abs(a) < 55) {
                continue;
            }
            lamp(a, -5, 0, 1, chance(0.6));
            lamp(a + 6 > WALL - 2 ? a - 6 : a + 6, 5, 0, -1, chance(0.6));
            lamp(-5, a, 1, 0, chance(0.6));
            lamp(5, a + 6 > WALL - 2 ? a - 6 : a + 6, -1, 0, chance(0.6));
        }
        // boulevard circulaire : lampadaires sur le trottoir interieur
        for (int t = -44; t <= 44; t += 11) {
            if (Math.abs(t) <= 6) {
                continue;
            }
            lamp(t, -47, 0, -1, chance(0.6));
            lamp(t, 47, 0, 1, chance(0.6));
            lamp(-47, t, -1, 0, chance(0.6));
            lamp(47, t, 1, 0, chance(0.6));
        }
        // lampadaires de la ruelle et du parking
        lamp(36, 23, 0, 1, true);
        lamp(10, 23, 0, 1, false);
        lamp(23, -14, 0, 1, true);
        lamp(43, -14, -1, 0, false);
    }

    void barricade(int x1, int z1, int x2, int z2, double gaps) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                if (chance(gaps)) {
                    continue;
                }
                int h = 1 + rnd.nextInt(2);
                for (int y = G + 1; y < G + 1 + h; y++) {
                    set(x, y, z, pick("minecraft:spruce_planks", "minecraft:hay_block", "minecraft:barrel[facing=up]", "minecraft:packed_mud", "minecraft:packed_mud", "minecraft:dark_oak_fence", "minecraft:cobweb", "minecraft:stripped_spruce_log[axis=x]"));
                }
            }
        }
    }

    void barricades() {
        // bouts des rues (fermes, contre les murs)
        barricade(-(WALL - 1), -5, -(WALL - 2), 5, 0.0);
        barricade(WALL - 2, -5, WALL - 1, 5, 0.0);
        barricade(-5, -(WALL - 1), 5, -(WALL - 2), 0.0);
        barricade(-5, WALL - 2, 5, WALL - 1, 0.0);
        // anciennes limites de la ville : barricades percees vers le boulevard
        barricade(-44, -5, -43, 5, 0.55);
        barricade(43, -5, 44, 5, 0.55);
        barricade(-5, -44, 5, -43, 0.55);
        barricade(-5, 43, 5, 44, 0.55);
        // dans la ville (avec des trous pour passer)
        barricade(-12, -3, -12, 3, 0.35);
        barricade(-3, -26, 3, -26, 0.4);
        barricade(24, -3, 24, 3, 0.4);
        barricade(-3, 27, 3, 27, 0.4);
        barricade(-24, -44, -23, -41, 0.3);
    }

    // ================================================================ bordure, alentours, hangar

    void boundary() {
        for (int a = -WALL; a <= WALL; a++) {
            for (int y = G + 1; y <= G + 40; y++) {
                set(a, y, -WALL, "minecraft:barrier");
                set(a, y, WALL, "minecraft:barrier");
                set(-WALL, y, a, "minecraft:barrier");
                set(WALL, y, a, "minecraft:barrier");
            }
            // cloture en ruine juste devant les barrieres
            for (int s = 0; s < 4; s++) {
                int x = s == 0 ? a : s == 1 ? a : s == 2 ? -(WALL - 1) : WALL - 1;
                int z = s == 0 ? -(WALL - 1) : s == 1 ? WALL - 1 : a;
                if (Math.abs(a) > WALL - 1 || !air(x, G + 1, z)) {
                    continue;
                }
                double r = rnd.nextDouble();
                String f = s < 2 ? "minecraft:spruce_fence[east=true,west=true]" : "minecraft:spruce_fence[north=true,south=true]";
                if (r < 0.55) {
                    set(x, G + 1, z, f);
                    if (chance(0.5)) {
                        set(x, G + 2, z, f);
                    }
                } else if (r < 0.7) {
                    set(x, G + 1, z, "minecraft:mossy_cobblestone_wall");
                } else if (r < 0.78) {
                    set(x, G + 1, z, "minecraft:cobweb");
                }
            }
        }
    }

    void outskirts() {
        // arbres morts et broussailles hors des murs (decor vu de loin)
        for (int k = 0; k < 60; k++) {
            int x = -(EXT - 2) + rnd.nextInt(2 * EXT - 3);
            int z = -(EXT - 2) + rnd.nextInt(2 * EXT - 3);
            if (Math.abs(x) < WALL + 3 && Math.abs(z) < WALL + 3) {
                continue;
            }
            if (Math.abs(x) <= 14 && z >= WALL + 1) {
                continue;
            }
            if (chance(0.6)) {
                deadTree(x, z);
            } else {
                oak(x, z, 4 + rnd.nextInt(3));
            }
        }
    }

    void hangar() {
        int x1 = -12;
        int x2 = 12;
        int z1 = HZ1;
        int z2 = HZ2;
        int o = HZ1 - 60; // decalage par rapport a l'ancien hangar (z 60..78)
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, G, z, chance(0.85) ? "minecraft:polished_andesite" : "minecraft:stone");
            }
        }
        for (int x = x1; x <= x2; x++) {
            int h = (int) Math.round(4 * Math.sqrt(Math.max(0, 1 - (x / 13.0) * (x / 13.0))));
            int roofY = G + 8 + h;
            for (int z = z1; z <= z2; z++) {
                set(x, roofY, z, "minecraft:waxed_weathered_cut_copper");
                boolean gable = z == z1 || z == z2;
                boolean side = x == x1 || x == x2;
                if (gable || side) {
                    for (int y = G + 1; y < roofY; y++) {
                        boolean corner = (x == x1 || x == x2) && gable;
                        set(x, y, z, corner ? "minecraft:polished_blackstone" : Math.floorMod(gable ? x : z, 2) == 0 ? "minecraft:light_gray_concrete" : "minecraft:gray_concrete");
                    }
                }
            }
        }
        // grande porte condamnee au sud
        fill(-3, G + 1, z2, 3, G + 5, z2, "minecraft:spruce_planks");
        fill(-3, G + 2, z2, 3, G + 2, z2, "minecraft:stripped_dark_oak_log[axis=x]");
        fill(-3, G + 4, z2, 3, G + 4, z2, "minecraft:stripped_dark_oak_log[axis=x]");
        // fenetres hautes a barreaux, certaines condamnees
        for (int z = z1 + 2; z <= z2 - 2; z += 4) {
            set(x1, G + 5, z, chance(0.4) ? "minecraft:spruce_planks" : "minecraft:iron_bars[north=true,south=true]");
            set(x2, G + 5, z, chance(0.4) ? "minecraft:spruce_planks" : "minecraft:iron_bars[north=true,south=true]");
        }
        // caisses, foin, sacs de sable, jeep, table de commandement
        int[][] crates = {{-10, 62}, {-9, 62}, {-10, 63}, {9, 75}, {10, 75}, {10, 76}, {10, 62}, {9, 62}};
        for (int[] c : crates) {
            c[1] += o;
            set(c[0], G + 1, c[1], pick("minecraft:barrel[facing=up]", "minecraft:spruce_planks", "minecraft:stripped_spruce_log"));
            if (chance(0.6)) {
                set(c[0], G + 2, c[1], pick("minecraft:barrel[facing=up]", "minecraft:hay_block", "minecraft:spruce_planks"));
            }
        }
        fill(-10, G + 1, 74 + o, -10, G + 1, 76 + o, "minecraft:hay_block");
        fill(-9, G + 1, 76 + o, -8, G + 1, 76 + o, "minecraft:hay_block");
        for (int x = -6; x <= 6; x++) {
            if (Math.abs(x) > 1) {
                set(x, G + 1, 76 + o, "minecraft:packed_mud");
            }
        }
        car(7, 64 + o, false, false);
        set(-6, G + 1, 66 + o, "minecraft:cartography_table");
        set(-5, G + 1, 66 + o, "minecraft:cartography_table");
        set(-7, G + 1, 66 + o, "minecraft:spruce_stairs[facing=west]");
        set(-4, G + 1, 66 + o, "minecraft:spruce_stairs[facing=east]");
        set(0, G + 1, 74 + o, "minecraft:campfire[lit=true]");
        int[][] lanterns = {{-6, 64}, {6, 70}, {-6, 72}, {0, 66}};
        for (int[] l : lanterns) {
            l[1] += o;
            int h = (int) Math.round(4 * Math.sqrt(Math.max(0, 1 - (l[0] / 13.0) * (l[0] / 13.0))));
            fill(l[0], G + 7, l[1], l[0], G + 7 + h, l[1], "minecraft:iron_chain");
            set(l[0], G + 6, l[1], "minecraft:lantern[hanging=true]");
        }
        // tableau noir pour les regles (texte affiche par le jeu)
        fill(-4, G + 2, z1 + 1, 4, G + 6, z1 + 1, "minecraft:black_concrete");
        fill(-5, G + 2, z1 + 1, -5, G + 6, z1 + 1, "minecraft:dark_oak_log");
        fill(5, G + 2, z1 + 1, 5, G + 6, z1 + 1, "minecraft:dark_oak_log");
    }

    /** Herbes et buissons morts sur le sol libre de la ville. */
    void decorate() {
        for (int x = -(WALL - 1); x <= WALL - 1; x++) {
            for (int z = -(WALL - 1); z <= WALL - 1; z++) {
                if (type(x, G, z).equals("grass_block") && air(x, G + 1, z)) {
                    double r = rnd.nextDouble();
                    if (r < 0.10) {
                        set(x, G + 1, z, "minecraft:short_grass");
                    } else if (r < 0.13) {
                        set(x, G + 1, z, "minecraft:dead_bush");
                    } else if (r < 0.14) {
                        set(x, G + 1, z, "minecraft:fern");
                    }
                }
            }
        }
        // poubelles, bouches d'incendie, sacs sur les trottoirs
        for (int k = 0; k < 50; k++) {
            int a = -(WALL - 2) + rnd.nextInt(2 * WALL - 3);
            int s = rnd.nextBoolean() ? 5 : -5;
            boolean ew = rnd.nextBoolean();
            int x = ew ? a : s;
            int z = ew ? s : a;
            if (Math.abs(a) < 7 || !air(x, G + 1, z) || !air(x, G + 2, z)) {
                continue;
            }
            set(x, G + 1, z, pick("minecraft:cauldron", "minecraft:composter", "minecraft:black_wool", "minecraft:red_nether_brick_wall", "minecraft:cobweb", "minecraft:black_carpet"));
        }
    }

    /** Lumiere invisible faible dans les rues (sinon la nuit est trop noire pour jouer). */
    void lights() {
        for (int x = -(WALL - 2); x <= WALL - 2; x += 7) {
            for (int z = -(WALL - 2); z <= WALL - 2; z += 7) {
                if (air(x, G + 2, z) && air(x, G + 1, z)) {
                    set(x, G + 2, z, "minecraft:light[level=7]");
                }
            }
        }
    }
}
