package fr.eemicraft.fete;

import fr.eemicraft.fete.build.B;
import fr.eemicraft.fete.build.Connect;
import fr.eemicraft.fete.build.Dir;
import fr.eemicraft.fete.build.Noise;
import fr.eemicraft.fete.build.Palette;
import fr.eemicraft.fete.build.Sink;
import fr.eemicraft.fete.build.Stairs;
import fr.eemicraft.fete.build.Terrain;
import fr.eemicraft.fete.build.Tree;

import java.util.List;
import java.util.Set;

/**
 * Dessin de la fête foraine (sans Bukkit) : allée foraine commune, place d'entrée, parcs des joueurs.
 * Les manèges posent leur socle ici ; leurs pièces mobiles sont des BlockDisplay en jeu ({@link Ride}).
 */
public final class Draw {
    public static final int VERSION = 2; // à monter à chaque changement de l'allée ou de la place (sinon elle n'est pas reposée)
    static final int F = Geo.FLOOR;
    static final int Y0 = F + 1;
    static final String AIR = B.AIR;

    private Draw() {
    }

    /** État d'un parc pour le dessin. */
    public record State(boolean claimed, Set<String> bought, List<Integer> slots, boolean complete) {
    }

    // =================================================================== allée foraine

    public static void street(Sink s, Geo g, long seed) {
        int[] wb = g.worldBox();
        int xa = g.xStart - 3, xb = g.xStart + g.length + 3;
        int za = -Geo.EDGE - Geo.D - 1, zb = Geo.EDGE + Geo.D;
        Terrain t = new Terrain(seed, wb[0], wb[1], wb[2], wb[3]).fbm(68, 8, 40, 4, 14).ridges(3, 34)
                .pad(xa, za, xb, zb, F, 12).smooth(1).meltFill();
        Terrain.Surface sf = new Terrain.Surface();
        sf.bareFrac = 0.03;
        t.place(s, sf, 54);

        // sol de fête : terre battue et pavés en grappes (pas de poivre et sel)
        Palette ground = Palette.of(seed + 1, "minecraft:packed_mud", 50, "minecraft:mud_bricks", 22,
                "minecraft:coarse_dirt", 18, "minecraft:dirt_path", 10).period(6);
        Palette plaza = Palette.of(seed + 3, "minecraft:polished_andesite", 52, "minecraft:stone_bricks", 30,
                "minecraft:mossy_stone_bricks", 8, "minecraft:polished_diorite", 10).period(5);
        int p0 = g.plazaX0, p1 = g.plazaX0 + Geo.PLAZA - 1, pc = g.plazaX0 + Geo.PLAZA / 2;
        int half = Geo.EDGE - 1;
        for (int x = xa; x <= xb; x++) {
            boolean inPlaza = x >= p0 && x <= p1;
            for (int z = -half; z <= half; z++) {
                s.set(x, F, z, inPlaza ? plaza.at(x, F, z) : ground.at(x, F, z));
                for (int y = F + 1; y <= F + 14; y++) s.set(x, y, z, AIR);
            }
            // bordure basse en pierre de chaque côté de l'allée
            if (!inPlaza) {
                s.set(x, F, half, "minecraft:polished_andesite");
                s.set(x, F, -half, "minecraft:polished_andesite");
            }
        }
        // bandes entre les parcs : haie, et poteaux de guirlandes de part et d'autre de l'allée
        for (int col = 0; col < g.cols; col++) {
            int px = g.colX(col);
            for (int side = -1; side <= 1; side += 2) {
                for (int gx = px - Geo.GAP; gx < px; gx++) {
                    if (gx >= p0 && gx <= p1) continue;
                    for (int v = 0; v < Geo.D; v++) {
                        int z = side * (Geo.EDGE + v);
                        s.set(gx, F, z, "minecraft:grass_block[snowy=false]");
                        for (int y = F + 1; y <= F + 6; y++) s.set(gx, y, z, AIR);
                        int dx = gx - (px - Geo.GAP);
                        if (dx >= 1 && dx <= Geo.GAP - 2 && v >= 2) {
                            s.set(gx, F + 1, z, B.leaves((v + dx) % 7 < 4 ? "minecraft:oak_leaves" : "minecraft:flowering_azalea_leaves"));
                            if ((v * 3 + dx) % 5 == 0) s.set(gx, F + 2, z, B.leaves("minecraft:oak_leaves"));
                        }
                    }
                }
            }
        }
        // guirlandes au-dessus de l'allée : un poteau tous les 9 blocs de chaque côté, chaînette avec ampoules
        int k = 0;
        for (int x = xa + 4; x <= xb - 4; x += 9) {
            if (x >= p0 - 2 && x <= p1 + 2) continue;
            post(s, x, -half);
            post(s, x, half);
            garland(s, x, -half, x, half, F + 9, 3, k++);
        }
        // guirlandes en long, de poteau en poteau, de chaque côté
        for (int side = -1; side <= 1; side += 2) {
            for (int x = xa + 4; x + 9 <= xb - 4; x += 9) {
                if (x + 9 >= p0 - 2 && x <= p1 + 2) continue;
                garlandX(s, x, x + 9, side * half, F + 9, 1.5, k++);
            }
        }
        plaza(s, g, seed, p0, p1, pc, plaza);
        t.vegetation(s, seed, 0.5);
        java.util.SplittableRandom r = new java.util.SplittableRandom(seed);
        for (int i = 0; i < 90; i++) {
            int x = wb[0] + 6 + r.nextInt(wb[2] - wb[0] - 12), z = wb[1] + 6 + r.nextInt(wb[3] - wb[1] - 12);
            if (x > xa - 6 && x < xb + 6 && z > za - 6 && z < zb + 6) continue;
            int h = t.height(x, z);
            if (h <= F || t.slope(x, z) >= 2) continue;
            boolean birch = r.nextInt(3) == 0;
            new Tree(birch ? "minecraft:birch_log" : "minecraft:oak_log", birch ? "minecraft:birch_leaves" : "minecraft:oak_leaves",
                    "minecraft:azalea_leaves").height(6 + r.nextInt(5)).crown(2.6 + r.nextDouble()).place(s, x, h + 1, z, seed + i);
        }
        Stairs.fix(s, wb[0], F - 2, wb[1], wb[2], F + 30, wb[3]);
        Connect.fix(s, xa - 1, F - 1, za - 1, xb + 1, F + 30, zb + 1);
    }

    /** Point d'arrivée : sur la place, au sud du kiosque, face au kiosque {x, y, z, yaw}. */
    public static double[] spawn(Geo g) {
        return new double[]{g.plazaX0 + Geo.PLAZA / 2 + 0.5, F + 1, Geo.EDGE + 4.5, 180};
    }

    /** Poteau de guirlande rayé rouge et blanc, lanterne au sommet. */
    static void post(Sink s, int x, int z) {
        for (int y = F + 1; y <= F + 9; y++) s.set(x, y, z, (y % 2 == 0) ? "minecraft:red_concrete" : "minecraft:white_concrete");
        s.set(x, F + 10, z, "minecraft:lantern[hanging=false]");
    }

    /** Guirlande en travers (le long de z) : chaînette en chaînette pendante, ampoules colorées. */
    static void garland(Sink s, int x, int z1, int x2, int z2, int y, double sag, int k) {
        int n = Math.abs(z2 - z1);
        for (int i = 1; i < n; i++) {
            double f = (double) i / n;
            int z = z1 + (z2 > z1 ? i : -i);
            int yy = (int) Math.round(y - sag * 4 * f * (1 - f));
            s.set(x, yy, z, (i + k) % 3 == 0 ? Rides.bulb(i + k) : "minecraft:iron_chain[axis=z]");
        }
    }

    /** Guirlande en long (le long de x). */
    static void garlandX(Sink s, int x1, int x2, int z, int y, double sag, int k) {
        int n = x2 - x1;
        for (int i = 1; i < n; i++) {
            double f = (double) i / n;
            int yy = (int) Math.round(y - sag * 4 * f * (1 - f));
            s.set(x1 + i, yy, z, (i + k) % 3 == 0 ? Rides.bulb(i + k + 2) : "minecraft:iron_chain[axis=x]");
        }
    }

    /** Place d'entrée : grande arche « FÊTE FORAINE » côté nord, kiosque à musique au centre, bancs et ballons. */
    static void plaza(Sink s, Geo g, long seed, int p0, int p1, int pc, Palette paving) {
        int depth = Geo.EDGE + 14;
        for (int x = p0; x <= p1; x++) {
            for (int z = -depth; z <= depth; z++) {
                if (Math.abs(z) < Geo.EDGE) continue;
                s.set(x, F, z, paving.at(x, F, z));
                for (int y = F + 1; y <= F + 14; y++) s.set(x, y, z, AIR);
            }
        }
        // rosace au sol
        for (int x = pc - 9; x <= pc + 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double d = Math.sqrt(Ride.sq(x + 0.5 - pc) + Ride.sq(z + 0.5));
                if (d > 9.5) continue;
                int ring = (int) d;
                s.set(x, F, z, ring % 3 == 0 ? "minecraft:red_terracotta" : (ring % 3 == 1 ? "minecraft:white_terracotta" : "minecraft:yellow_terracotta"));
            }
        }
        kiosque(s, pc + 0.5, 0.5);
        // grande arche d'entrée côté nord de la place (z négatif) et côté sud
        for (int side = -1; side <= 1; side += 2) {
            int z = side * (depth - 2);
            for (int dx : new int[]{-8, 7}) {
                for (int y = F + 1; y <= F + 12; y++) {
                    for (int ddx = 0; ddx <= 1; ddx++) {
                        s.set(pc + dx + ddx, y, z, (y / 2) % 2 == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete");
                    }
                }
                s.set(pc + dx, F + 13, z, "minecraft:gold_block");
                s.set(pc + dx + 1, F + 13, z, "minecraft:gold_block");
                s.set(pc + dx, F + 14, z, "minecraft:pearlescent_froglight");
                s.set(pc + dx + 1, F + 14, z, "minecraft:pearlescent_froglight");
            }
            // fronton en arc avec ampoules
            for (int x = pc - 6; x <= pc + 6; x++) {
                double f = (x - (pc - 6.5)) / 13.0;
                int yy = (int) Math.round(F + 11 + 3 * Math.sin(Math.PI * f));
                for (int y = F + 10; y <= yy; y++) s.set(x, y, z, y == yy ? "minecraft:gold_block" : "minecraft:red_concrete");
                s.set(x, yy + 1, z, Rides.bulb(x));
            }
        }
        // bancs et grappes de ballons
        for (int side = -1; side <= 1; side += 2) {
            for (int dx : new int[]{-14, 13}) {
                int z = side * (Geo.EDGE + 5);
                s.set(pc + dx, F + 1, z, "minecraft:spruce_stairs[facing=" + (side < 0 ? "south" : "north") + ",half=bottom,shape=straight]");
                s.set(pc + dx + 1, F + 1, z, "minecraft:spruce_stairs[facing=" + (side < 0 ? "south" : "north") + ",half=bottom,shape=straight]");
                balloons(s, pc + dx + 3, z, seed + dx + side);
            }
        }
    }

    /**
     * Kiosque à musique (26/11, refait : « gros souci niveau design ») : socle octogonal en pierre à 4 escaliers,
     * plancher en bois, 8 colonnettes en fer forgé (murets de pierre noire) à chapiteau doré, garde-corps en bois,
     * toit conique en escaliers rayé rouge et blanc avec débord, lanternes sous l'avant-toit, guirlande d'ampoules,
     * flèche dorée. Centre (cx, cz) au milieu d'un bloc.
     */
    static void kiosque(Sink s, double cx, double cz) {
        int x0 = (int) Math.floor(cx), z0 = (int) Math.floor(cz);
        for (int x = x0 - 8; x <= x0 + 8; x++) {
            for (int z = z0 - 8; z <= z0 + 8; z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d = oct(dx, dz);
                if (d <= 4.6) {
                    s.set(x, F + 1, z, d > 3.9 ? "minecraft:polished_andesite" : "minecraft:stone_bricks");
                    s.set(x, F + 2, z, d > 3.9 ? "minecraft:polished_andesite" : ((x + z) % 2 == 0 ? "minecraft:spruce_planks" : "minecraft:stripped_spruce_wood[axis=y]"));
                } else if (d <= 5.6) {
                    // marche tout autour (escalier qui monte vers le centre)
                    s.set(x, F + 1, z, B.stairs("minecraft:stone_brick_stairs", towards(dx, dz), false));
                }
                for (int y = F + 3; y <= F + 13; y++) {
                    if (d <= 6.8) s.set(x, y, z, AIR);
                }
            }
        }
        // 4 escaliers d'accès (marche supplémentaire au milieu de chaque face)
        for (int[] o : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            for (int k = -1; k <= 1; k++) {
                int x = x0 + o[0] * 5 + (o[0] == 0 ? k : 0), z = z0 + o[1] * 5 + (o[1] == 0 ? k : 0);
                s.set(x, F + 2, z, B.stairs("minecraft:stone_brick_stairs", towards(x + 0.5 - cx, z + 0.5 - cz), false));
            }
        }
        // garde-corps (ouvert face aux escaliers) ; colonnettes et toit : pièces fines du plugin (Rides.kiosque)
        for (int x = x0 - 5; x <= x0 + 5; x++) {
            for (int z = z0 - 5; z <= z0 + 5; z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d = oct(dx, dz);
                boolean opening = Math.abs(dx) < 1.6 || Math.abs(dz) < 1.6;
                if (d > 3.6 && d <= 4.6 && !opening) s.set(x, F + 3, z, "minecraft:dark_oak_fence");
            }
        }
        s.set(x0, F + 3, z0, "minecraft:jukebox");
        s.set(x0 + 1, F + 3, z0, "minecraft:note_block");
        s.set(x0 - 1, F + 3, z0, "minecraft:note_block");
    }

    /** Distance « octogonale » (contour à 8 pans au lieu d'un cercle pixelisé). */
    static double oct(double dx, double dz) {
        double ax = Math.abs(dx), az = Math.abs(dz);
        return Math.max(Math.max(ax, az), (ax + az) / 1.3);
    }

    /** Escalier qui monte vers le centre (facing = côté haut). */
    static Dir towards(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Dir.WEST : Dir.EAST;
        return dz > 0 ? Dir.NORTH : Dir.SOUTH;
    }

    static String face(double dx, double dz) {
        // l'escalier monte vers le centre : facing = direction vers le centre
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "west" : "east";
        return dz > 0 ? "north" : "south";
    }

    /** Grappe de ballons : ficelle (barreau) et 3 à 4 boules de laine colorée. */
    static void balloons(Sink s, int x, int z, long seed) {
        String[] c = {"red", "yellow", "light_blue", "pink", "lime", "orange", "purple"};
        s.set(x, F + 1, z, "minecraft:polished_blackstone_wall");
        for (int y = F + 2; y <= F + 4; y++) s.set(x, y, z, "minecraft:iron_chain[axis=y]");
        int n = 3 + (int) Math.floorMod(seed, 2);
        int[][] off = {{0, 5, 0}, {1, 6, 0}, {-1, 6, 1}, {0, 7, -1}};
        for (int i = 0; i < n; i++) {
            s.set(x + off[i][0], F + off[i][1], z + off[i][2], "minecraft:" + c[(int) Math.floorMod(seed + i * 3, c.length)] + "_wool");
        }
    }

    // =================================================================== parc d'un joueur

    public static void parcel(Sink world, Geo g, int idx, State st) {
        Sink s = g.local(world, idx);
        long seed = 9000L + idx * 131L;
        Palette lawn = Palette.of(seed, "minecraft:grass_block[snowy=false]", 78, "minecraft:moss_block", 12,
                "minecraft:coarse_dirt", 10).period(7);
        for (int u = 0; u < Geo.W; u++) {
            for (int v = 0; v < Geo.D; v++) {
                s.set(u, F, v, lawn.at(g.wx(idx, u, v), F, g.wz(idx, u, v)));
                for (int y = F + 1; y <= Geo.TOP; y++) s.set(u, y, v, AIR);
            }
        }
        // arche « Réclamer ce parc » (toujours là)
        gateArch(s, st.claimed());
        if (st.bought() != null) {
            Set<String> b = st.bought();
            for (Defs.Purchase p : Defs.PURCHASES) if (b.contains(p.id())) piece(s, p.id(), b, seed);
            for (int k = 0; k < st.slots().size() && k < Defs.SLOTS.length; k++) {
                int[] sl = Defs.SLOTS[k];
                s.set(sl[0], Y0, sl[1], "minecraft:red_carpet");
            }
            if (st.complete()) s.set(Defs.SLOTS[0][0], Y0, Defs.SLOTS[0][1], "minecraft:purple_carpet");
        }
        int[] bx = g.box(idx);
        Stairs.fix(world, bx[0], F, bx[1], bx[2], Geo.TOP, bx[3]);
        Connect.fix(world, bx[0], F, bx[1], bx[2], Geo.TOP, bx[3]);
    }

    static void gateArch(Sink s, boolean claimed) {
        for (int u : new int[]{Defs.GATE_U1 - 1, Defs.GATE_U2 + 1}) {
            for (int y = Y0; y <= Y0 + 5; y++) s.set(u, y, 0, (y % 2 == 0) ? "minecraft:red_concrete" : "minecraft:white_concrete");
            s.set(u, Y0 + 6, 0, "minecraft:gold_block");
            s.set(u, Y0 + 7, 0, "minecraft:lantern[hanging=false]");
        }
        for (int u = Defs.GATE_U1 - 1; u <= Defs.GATE_U2 + 1; u++) {
            s.set(u, Y0 + 5, 0, "minecraft:gold_block");
            if (u > Defs.GATE_U1 - 1 && u < Defs.GATE_U2 + 1) s.set(u, Y0 + 4, 0, claimed ? Rides.bulb(u) : "minecraft:iron_chain[axis=x]");
        }
    }

    static final Palette PATH = Palette.of(501, "minecraft:dirt_path", 60, "minecraft:packed_mud", 25, "minecraft:coarse_dirt", 15).period(5);
    static final Palette PROMENADE = Palette.of(502, "minecraft:polished_andesite", 55, "minecraft:stone_bricks", 30, "minecraft:polished_tuff", 15).period(5);

    static void path(Sink s, int u1, int v1, int u2, int v2, Palette p) {
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) s.set(u, F, v, p.at(u * 7 + 3, F, v * 5 + 1));
        }
    }

    static void fence(Sink s, int u, int v) {
        s.set(u, Y0, v, "minecraft:spruce_fence");
    }

    /** Pose une pièce achetée. */
    static void piece(Sink s, String id, Set<String> b, long seed) {
        switch (id) {
            case "entree" -> {
                path(s, 1, 1, 46, 3, PROMENADE);
                for (int u = 0; u < Geo.W; u++) {
                    if (u >= Defs.GATE_U1 && u <= Defs.GATE_U2) continue;
                    if (u == Defs.GATE_U1 - 1 || u == Defs.GATE_U2 + 1) continue;
                    fence(s, u, 0);
                }
            }
            case "guichet" -> {
                // kiosque à tickets rayé à gauche de l'entrée
                for (int u = 16; u <= 19; u++) {
                    for (int v = 4; v <= 7; v++) {
                        boolean wall = u == 16 || u == 19 || v == 4 || v == 7;
                        for (int y = Y0; y <= Y0 + 3; y++) {
                            if (wall) s.set(u, y, v, (u + v) % 2 == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete");
                        }
                        s.set(u, Y0 + 4, v, "minecraft:gold_block");
                    }
                }
                s.set(19, Y0 + 1, 5, "minecraft:glass_pane[east=false,west=false,north=true,south=true]");
                s.set(19, Y0 + 1, 6, "minecraft:glass_pane[east=false,west=false,north=true,south=true]");
                s.set(17, Y0 + 5, 5, "minecraft:ochre_froglight");
                s.set(18, Y0 + 5, 6, "minecraft:pearlescent_froglight");
                s.set(Defs.COLLECTOR[0], F, Defs.COLLECTOR[1], "minecraft:gold_block");
            }
            case "allee" -> path(s, 21, 4, 26, 46, PATH);
            case "allees" -> {
                path(s, 1, 10, 46, 11, PATH);
                path(s, 1, 28, 46, 29, PATH);
            }
            case "cloture" -> {
                for (int v = 1; v < Geo.D; v++) {
                    fence(s, 0, v);
                    fence(s, Geo.W - 1, v);
                }
                for (int u = 0; u < Geo.W; u++) fence(s, u, Geo.D - 1);
            }
            case "lampions" -> {
                for (int u = 2; u <= 45; u += 6) {
                    if (u >= Defs.GATE_U1 - 2 && u <= Defs.GATE_U2 + 2) continue;
                    s.set(u, Y0, 3, "minecraft:polished_blackstone_wall");
                    s.set(u, Y0 + 1, 3, "minecraft:polished_blackstone_wall");
                    s.set(u, Y0 + 2, 3, "minecraft:lantern[hanging=false]");
                }
            }
            case "ballons" -> {
                balloons(s, 20, 9, seed + 1);
                balloons(s, 27, 9, seed + 2);
                balloons(s, 27, 27, seed + 3);
                balloons(s, 20, 30, seed + 4);
            }
            case "guirlandes" -> {
                for (int v : new int[]{12, 26}) {
                    post(s, 20, v);
                    post(s, 27, v);
                }
                garland(s, 20, 12, 20, 26, F + 9, 2, 1);
                garland(s, 27, 12, 27, 26, F + 9, 2, 4);
            }
            case "fleurs" -> {
                String[] fl = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium", "minecraft:oxeye_daisy"};
                int[][] beds = {{2, 24}, {42, 26}, {28, 38}, {2, 45}};
                for (int[] bd : beds) {
                    for (int du = 0; du < 3; du++) {
                        for (int dv = 0; dv < 2; dv++) {
                            s.set(bd[0] + du, F, bd[1] + dv, "minecraft:grass_block[snowy=false]");
                            s.set(bd[0] + du, Y0, bd[1] + dv, fl[Math.floorMod(bd[0] + du * 3 + dv, fl.length)]);
                        }
                    }
                }
            }
            case "bancs" -> {
                int[][] bs = {{21, 18, 0}, {26, 18, 1}, {21, 34, 0}, {26, 34, 1}};
                for (int[] bn : bs) {
                    String f = bn[2] == 0 ? "east" : "west";
                    s.set(bn[0], Y0, bn[1], "minecraft:spruce_stairs[facing=" + f + ",half=bottom,shape=straight]");
                    s.set(bn[0], Y0, bn[1] + 1, "minecraft:spruce_stairs[facing=" + f + ",half=bottom,shape=straight]");
                    s.set(bn[0], Y0, bn[1] + 3, "minecraft:composter[level=0]");
                }
            }
            case "musique" -> {
                // orgue de barbarie sur la promenade
                s.set(31, Y0, 2, "minecraft:jukebox");
                s.set(32, Y0, 2, "minecraft:note_block");
                s.set(31, Y0 + 1, 2, "minecraft:gold_block");
            }
            case "mascotte" -> {
                // ours en peluche géant près de l'entrée
                s.set(28, Y0, 7, "minecraft:brown_wool");
                s.set(28, Y0 + 1, 7, "minecraft:brown_wool");
                s.set(28, Y0 + 2, 7, "minecraft:brown_wool");
                s.set(27, Y0 + 1, 7, "minecraft:brown_wool");
                s.set(29, Y0 + 1, 7, "minecraft:brown_wool");
                s.set(28, Y0 + 3, 7, "minecraft:pink_wool");
            }
            case "neons" -> {
                for (int u = 1; u <= 46; u += 3) s.set(u, Y0, 1, (u % 2 == 0) ? "minecraft:magenta_stained_glass" : "minecraft:cyan_stained_glass");
            }
            case "vip" -> {
                for (int u = 23; u <= 24; u++) for (int v = 12; v <= 26; v++) s.set(u, F, v, "minecraft:red_terracotta");
            }
            case "arche" -> {
                for (int u = Defs.GATE_U1 - 1; u <= Defs.GATE_U2 + 1; u++) {
                    double f = (u - (Defs.GATE_U1 - 1.5)) / (Defs.GATE_U2 - Defs.GATE_U1 + 3.0);
                    int yy = (int) Math.round(Y0 + 7 + 3 * Math.sin(Math.PI * f));
                    for (int y = Y0 + 6; y <= yy; y++) s.set(u, y, 0, y == yy ? "minecraft:gold_block" : "minecraft:red_concrete");
                    s.set(u, yy + 1, 0, Rides.bulb(u));
                }
            }
            case "feux" -> {
                for (int[] p : new int[][]{{2, 46}, {45, 46}, {2, 13}, {45, 9}}) {
                    s.set(p[0], Y0, p[1], "minecraft:dispenser[facing=up]");
                    s.set(p[0], Y0 + 1, p[1], "minecraft:red_concrete");
                }
            }
            case "toilettes" -> toilettes(s);
            case "crepes" -> crepes(s);
            case "ferme" -> ferme(s);
            case "carrousel" -> Rides.carrousel(s, Defs.CARROUSEL[0], Defs.CARROUSEL[1]);
            case "autos" -> Rides.autos(s, Defs.AUTOS[0], Defs.AUTOS[1], Defs.AUTOS[2], Defs.AUTOS[3]);
            case "chaises" -> Rides.chaises(s, Defs.CHAISES[0], Defs.CHAISES[1]);
            case "bateau" -> Rides.bateau(s, Defs.BATEAU[0], Defs.BATEAU[1]);
            case "fantome" -> Rides.fantome(s, Defs.FANTOME[0], Defs.FANTOME[1], Defs.FANTOME[2], Defs.FANTOME[3]);
            case "roue" -> Rides.roue(s, Defs.ROUE[0], Defs.ROUE[1], Defs.ROUE[2], Defs.ROUE[3]);
            case "tour" -> Rides.tour(s, Defs.TOUR[0], Defs.TOUR[1]);
            case "montagnes" -> Rides.montagnes(s);
            default -> {
                for (Defs.Stand st : Defs.STANDS) if (st.purchase().equals(id)) Rides.stand(s, st.u(), st.v(), st.type());
            }
        }
    }

    /** Toilettes publiques : 3 cabines ouvertes vers l'allée centrale ; celle du milieu a une porte en fer (« OCCUPÉ »). */
    static void toilettes(Sink s) {
        int[] t = Defs.TOILETTES;
        for (int u = t[0]; u <= t[2]; u++) {
            for (int v = t[1]; v <= t[3]; v++) {
                s.set(u, F, v, "minecraft:smooth_quartz");
                boolean wall = u == t[2] || v == t[1] || v == t[3] || v == 14 || v == 16 || u == t[0];
                for (int y = Y0; y <= Y0 + 2; y++) {
                    if (wall) s.set(u, y, v, (v == 14 || v == 16) ? "minecraft:light_blue_terracotta" : "minecraft:white_terracotta");
                    else s.set(u, y, v, AIR);
                }
                s.set(u, Y0 + 3, v, B.slab("minecraft:prismarine_brick_slab", "bottom"));
            }
        }
        // portes côté allée (u = 27), cuvette (chaudron) au fond de chaque cabine
        for (int v : new int[]{13, 15, 17}) {
            String door = v == 15 ? "minecraft:iron_door" : "minecraft:spruce_door";
            s.set(t[0], Y0, v, door + "[facing=east,half=lower,hinge=left,open=false,powered=false]");
            s.set(t[0], Y0 + 1, v, door + "[facing=east,half=upper,hinge=left,open=false,powered=false]");
            s.set(t[0] + 1, Y0, v, "minecraft:cauldron");
            s.set(t[0], Y0 + 2, v, "minecraft:white_terracotta");
        }
        s.set(t[0] - 1, Y0 + 2, 15, "minecraft:redstone_lamp[lit=true]");
        s.set(t[0] - 1, Y0 + 3, 13, "minecraft:sea_lantern");
        s.set(t[0] - 1, Y0 + 3, 17, "minecraft:sea_lantern");
    }

    /** Kiosque à crêpes, comptoir sur l'allée centrale, toit rayé, enseigne lumineuse. */
    static void crepes(Sink s) {
        int[] c = Defs.CREPES;
        for (int u = c[0]; u <= c[2]; u++) {
            for (int v = c[1]; v <= c[3]; v++) {
                s.set(u, F, v, "minecraft:spruce_planks");
                boolean back = u == c[2], side = v == c[1] || v == c[3];
                for (int y = Y0; y <= Y0 + 2; y++) {
                    if (back || side) s.set(u, y, v, y == Y0 ? "minecraft:stripped_spruce_wood[axis=y]" : "minecraft:yellow_terracotta");
                    else s.set(u, y, v, AIR);
                }
                s.set(u, Y0 + 3, v, (v % 2 == 0) ? "minecraft:orange_wool" : "minecraft:white_wool");
            }
            s.set(c[0] - 1, Y0 + 3, c[1] + (u - c[0]) * 2, "minecraft:orange_wool");
        }
        for (int v = c[1] + 1; v < c[3]; v++) {
            s.set(c[0], Y0, v, "minecraft:spruce_planks");
            s.set(c[0], Y0 + 1, v, B.slab("minecraft:spruce_slab", "bottom"));
        }
        s.set(c[0] + 1, Y0, c[1] + 2, "minecraft:smoker[facing=west,lit=true]");
        s.set(c[0] + 1, Y0, c[1] + 3, "minecraft:smoker[facing=west,lit=true]");
        for (int v = c[1]; v <= c[3]; v++) s.set(c[0] - 1, Y0 + 4, v, (v % 2 == 0) ? "minecraft:ochre_froglight" : "minecraft:pearlescent_froglight");
    }

    /** Mini-ferme : enclos de barrières, foin, abreuvoir, petit abri (les animaux sont posés par le plugin). */
    static void ferme(Sink s) {
        int[] f = Defs.FERME;
        for (int u = f[0]; u <= f[2]; u++) {
            for (int v = f[1]; v <= f[3]; v++) {
                s.set(u, F, v, (u + v) % 5 == 0 ? "minecraft:coarse_dirt" : "minecraft:grass_block[snowy=false]");
                boolean edge = u == f[0] || u == f[2] || v == f[1] || v == f[3];
                if (edge) s.set(u, Y0, v, "minecraft:oak_fence");
            }
        }
        s.set(f[0], Y0, f[1] + 4, "minecraft:oak_fence_gate[facing=east,open=false,in_wall=false,powered=false]");
        s.set(f[0] + 1, Y0, f[3] - 1, "minecraft:hay_block[axis=x]");
        s.set(f[0] + 2, Y0, f[3] - 1, "minecraft:hay_block[axis=x]");
        s.set(f[0] + 1, Y0 + 1, f[3] - 1, "minecraft:hay_block[axis=z]");
        s.set(f[2] - 1, Y0, f[1] + 1, "minecraft:water_cauldron[level=3]");
        // abri au fond : toit en dalles sur 4 poteaux
        for (int u = f[2] - 2; u <= f[2] - 1; u++) {
            for (int v = f[3] - 3; v <= f[3] - 1; v++) s.set(u, Y0 + 3, v, B.slab("minecraft:spruce_slab", "bottom"));
        }
        for (int[] p : new int[][]{{f[2] - 2, f[3] - 3}, {f[2] - 1, f[3] - 3}, {f[2] - 2, f[3] - 1}, {f[2] - 1, f[3] - 1}}) {
            for (int y = Y0; y <= Y0 + 2; y++) s.set(p[0], y, p[1], "minecraft:spruce_fence");
        }
        s.set(f[0], Y0 + 1, f[1], "minecraft:lantern[hanging=false]");
        s.set(f[2], Y0 + 1, f[3], "minecraft:lantern[hanging=false]");
    }

    /** Les manèges (pièces mobiles) achetés dans un parc. */
    public static java.util.List<Ride> rides(Set<String> b) {
        java.util.List<Ride> l = new java.util.ArrayList<>();
        if (b.contains("carrousel")) l.add(Rides.carrousel(null, Defs.CARROUSEL[0], Defs.CARROUSEL[1]));
        if (b.contains("autos")) l.add(Rides.autos(null, Defs.AUTOS[0], Defs.AUTOS[1], Defs.AUTOS[2], Defs.AUTOS[3]));
        if (b.contains("chaises")) l.add(Rides.chaises(null, Defs.CHAISES[0], Defs.CHAISES[1]));
        if (b.contains("bateau")) l.add(Rides.bateau(null, Defs.BATEAU[0], Defs.BATEAU[1]));
        if (b.contains("fantome")) l.add(Rides.fantome(null, Defs.FANTOME[0], Defs.FANTOME[1], Defs.FANTOME[2], Defs.FANTOME[3]));
        if (b.contains("roue")) l.add(Rides.roue(null, Defs.ROUE[0], Defs.ROUE[1], Defs.ROUE[2], Defs.ROUE[3]));
        if (b.contains("tour")) l.add(Rides.tour(null, Defs.TOUR[0], Defs.TOUR[1]));
        if (b.contains("montagnes")) l.add(Rides.montagnes(null));
        return l;
    }

    static Noise NOISE = new Noise(7);
}
