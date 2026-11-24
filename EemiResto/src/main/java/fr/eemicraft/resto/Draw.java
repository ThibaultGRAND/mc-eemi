package fr.eemicraft.resto;

import fr.eemicraft.resto.build.B;
import fr.eemicraft.resto.build.Connect;
import fr.eemicraft.resto.build.Dir;
import fr.eemicraft.resto.build.Facade;
import fr.eemicraft.resto.build.Mat;
import fr.eemicraft.resto.build.Noise;
import fr.eemicraft.resto.build.Palette;
import fr.eemicraft.resto.build.Roof;
import fr.eemicraft.resto.build.RoofStyle;
import fr.eemicraft.resto.build.Shapes;
import fr.eemicraft.resto.build.Sink;
import fr.eemicraft.resto.build.Stairs;
import fr.eemicraft.resto.build.Style;
import fr.eemicraft.resto.build.Terrain;
import fr.eemicraft.resto.build.Tree;
import java.util.List;
import java.util.Set;

/**
 * Dessin de la rue et des restaurants (sans Bukkit : tourne hors du fil principal et dans le test).
 * Une parcelle se redessine EN ENTIER a chaque achat (volume vide puis morceaux achetes dans l'ordre de la liste) :
 * le resultat ne depend que de l'ensemble des achats, jamais de l'ordre dans lequel ils ont ete faits.
 */
public final class Draw {
    public static final int VERSION = 1;
    static final int F = Geo.FLOOR;           // sol (on marche en F+1)
    static final int Y0 = F + 1;              // premier rang au-dessus du sol
    static final int WALL_TOP = 70;           // corniche des murs
    static final String AIR = B.AIR;

    private Draw() {
    }

    /** Etat d'une parcelle pour le dessin. */
    public record State(boolean claimed, Set<String> bought, List<Integer> slots, boolean complete) {
    }

    // =================================================================== rue

    public static void street(Sink s, Geo g, long seed) {
        int[] wb = g.worldBox();
        int xa = g.xStart - 3, xb = g.xStart + g.length + 3;
        int za = -Geo.EDGE - Geo.D - 1, zb = Geo.EDGE + Geo.D;
        Terrain t = new Terrain(seed, wb[0], wb[1], wb[2], wb[3]).fbm(68, 9, 38, 4, 14).ridges(4, 30)
                .pad(xa, za, xb, zb, Geo.ROAD, 12).smooth(1).meltFill();
        Terrain.Surface sf = new Terrain.Surface();
        sf.bareFrac = 0.03;
        t.place(s, sf, 54);

        Palette road = Palette.of(seed + 1, "minecraft:polished_andesite", 55, "minecraft:andesite", 25, "minecraft:stone_bricks", 20).period(5);
        Palette walk = Palette.of(seed + 2, "minecraft:polished_granite", 62, "minecraft:granite", 23, "minecraft:terracotta", 15).period(6);
        Palette plaza = Palette.of(seed + 3, "minecraft:stone_bricks", 58, "minecraft:polished_andesite", 27, "minecraft:cracked_stone_bricks", 15).period(6);
        int p0 = g.plazaX0, p1 = g.plazaX0 + Geo.PLAZA - 1, pc = g.plazaX0 + Geo.PLAZA / 2;

        // chaussee pietonne (y 63), trottoirs (y 64) avec bordure, rigole centrale
        for (int x = xa; x <= xb; x++) {
            boolean inPlaza = x >= p0 && x <= p1;
            for (int z = -8; z <= 8; z++) {
                int az = Math.abs(z);
                if (inPlaza) {
                    s.set(x, F, z, plaza);
                    continue;
                }
                if (az <= 4) s.set(x, Geo.ROAD, z, z == 0 ? "minecraft:smooth_stone" : road.at(x, Geo.ROAD, z));
                else s.set(x, F, z, az == 5 ? "minecraft:smooth_stone" : walk.at(x, F, z));
                for (int y = F + 1; y <= F + 8; y++) s.set(x, y, z, AIR);
                if (az <= 4) s.set(x, F, z, AIR);
            }
        }
        // haies entre les parcelles, lampadaires, bout de rue
        for (int col = 0; col < g.cols; col++) {
            int px = g.colX(col);
            for (int side = -1; side <= 1; side += 2) {
                for (int gx : new int[]{px - 2, px - 1, px + Geo.W, px + Geo.W + 1}) {
                    if (gx >= p0 && gx <= p1) continue;
                    for (int v = 0; v < Geo.D; v++) {
                        int z = side * (Geo.EDGE + v);
                        s.set(gx, F, z, "minecraft:grass_block[snowy=false]");
                        for (int y = F + 1; y <= F + 4; y++) s.set(gx, y, z, AIR);
                        if (v >= 6) s.set(gx, F + 1, z, B.leaves(v % 9 < 5 ? "minecraft:oak_leaves" : "minecraft:azalea_leaves"));
                    }
                }
                // lampadaires du trottoir, decales d'une colonne a l'autre (pas de rangee reguliere)
                int[] lx = {px + 3 + (col * 5) % 4, px + 15 + (col * 3) % 5};
                for (int x : lx) lamp(s, x, side * 7);
                // arbres d'alignement, un sur deux, couronnes hors des parcelles
                if (((col + (side > 0 ? 0 : 1)) & 1) == 0) {
                    int tx = px + 9 + (col % 3);
                    s.set(tx, F, side * 6, "minecraft:coarse_dirt");
                    new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:birch_leaves").height(6).crown(1.8).roots(false)
                            .place(s, tx, F + 1, side * 6, seed + col * 31L + side);
                }
            }
        }
        plaza(s, g, seed, p0, p1, pc, plaza);
        // vegetation sur l'herbe (place, haies, collines) puis quelques arbres sur les collines
        t.vegetation(s, seed, 0.55);
        java.util.SplittableRandom r = new java.util.SplittableRandom(seed);
        for (int i = 0; i < 70; i++) {
            int x = wb[0] + 6 + r.nextInt(wb[2] - wb[0] - 12), z = wb[1] + 6 + r.nextInt(wb[3] - wb[1] - 12);
            if (x > xa - 6 && x < xb + 6 && z > za - 6 && z < zb + 6) continue;
            int h = t.height(x, z);
            if (h <= Geo.ROAD + 1 || t.slope(x, z) >= 2) continue;
            boolean birch = r.nextInt(3) == 0;
            new Tree(birch ? "minecraft:birch_log" : "minecraft:oak_log", birch ? "minecraft:birch_leaves" : "minecraft:oak_leaves",
                    "minecraft:azalea_leaves").height(6 + r.nextInt(4)).crown(2.6 + r.nextDouble()).place(s, x, h + 1, z, seed + i);
        }
        Stairs.fix(s, wb[0], F - 2, wb[1], wb[2], F + 30, wb[3]);
        Connect.fix(s, xa - 1, F - 1, za - 1, xb + 1, F + 30, zb + 1);
    }

    static void lamp(Sink s, int x, int z) {
        s.set(x, F + 1, z, "minecraft:polished_blackstone_wall");
        s.set(x, F + 2, z, "minecraft:polished_blackstone_wall");
        s.set(x, F + 3, z, "minecraft:polished_blackstone_wall");
        s.set(x, F + 4, z, "minecraft:lantern[hanging=false]");
    }

    /** Place centrale : fontaine, tour de l'horloge (repere), jardins ; dalle secrete derriere la tour. */
    static void plaza(Sink s, Geo g, long seed, int p0, int p1, int pc, Palette paving) {
        int za = -Geo.EDGE - Geo.D, zb = Geo.EDGE + Geo.D - 1;
        Palette lawn = Palette.of(seed + 9, "minecraft:grass_block[snowy=false]", 80, "minecraft:moss_block", 20).period(5);
        for (int x = p0; x <= p1; x++) {
            for (int z = za; z <= zb; z++) {
                int dx = x - pc, az = Math.abs(z);
                for (int y = F + 1; y <= F + 30; y++) s.set(x, y, z, AIR);
                boolean path = Math.abs(dx) <= 2 || az <= 8 || (az - 9) % 11 == 0 && az > 9;
                boolean edge = x == p0 || x == p1;
                s.set(x, F, z, path ? paving.at(x, F, z) : lawn.at(x, F, z));
                if (!path && edge) s.set(x, F + 1, z, B.leaves("minecraft:oak_leaves"));
            }
        }
        // fontaine (rayon 5) : margelle, eau, vasque centrale
        for (int x = pc - 6; x <= pc + 6; x++)
            for (int z = -6; z <= 6; z++) {
                double d = Math.hypot(x - pc, z);
                if (d <= 4.6) s.set(x, F, z, "minecraft:water");
                else if (d <= 5.6) {
                    s.set(x, F, z, "minecraft:polished_andesite");
                    s.set(x, F + 1, z, B.slab("minecraft:smooth_stone_slab", "bottom"));
                }
            }
        for (int y = F - 1; y <= F; y++)
            for (int x = pc - 5; x <= pc + 5; x++)
                for (int z = -5; z <= 5; z++) if (Math.hypot(x - pc, z) <= 4.6 && y == F - 1) s.set(x, y, z, "minecraft:prismarine_bricks");
        s.set(pc, F, 0, "minecraft:stone_bricks");
        s.set(pc, F + 1, 0, "minecraft:stone_brick_wall");
        s.set(pc, F + 2, 0, "minecraft:stone_brick_wall");
        s.set(pc, F + 3, 0, "minecraft:chiseled_stone_bricks");
        for (Dir d : Dir.values()) s.set(pc + d.dx, F + 3, d.dz, B.stairs("minecraft:stone_brick_stairs", d.opposite(), true));
        s.set(pc, F + 4, 0, "minecraft:sea_lantern");
        // tour de l'horloge au nord de la fontaine
        int tz1 = -31, tz2 = -25, tx1 = pc - 3, tx2 = pc + 3, top = F + 18;
        Palette tw = Palette.of(seed + 11, "minecraft:stone_bricks", 62, "minecraft:cracked_stone_bricks", 20, "minecraft:mossy_stone_bricks", 18).period(7);
        for (int x = tx1; x <= tx2; x++)
            for (int z = tz1; z <= tz2; z++) {
                s.set(x, F, z, "minecraft:polished_andesite");
                boolean wall = x == tx1 || x == tx2 || z == tz1 || z == tz2;
                boolean corner = (x == tx1 || x == tx2) && (z == tz1 || z == tz2);
                for (int y = F + 1; y <= top; y++) {
                    if (!wall) {
                        s.set(x, y, z, AIR);
                        continue;
                    }
                    s.set(x, y, z, corner ? "minecraft:quartz_pillar[axis=y]" : tw.at(x, y, z));
                }
            }
        // bandeaux en escaliers retournes (3 plans), passages et beffroi
        for (int yb : new int[]{F + 6, F + 12, top}) {
            for (int x = tx1 - 1; x <= tx2 + 1; x++) {
                s.set(x, yb, tz1 - 1, B.stairs("minecraft:stone_brick_stairs", Dir.SOUTH, true));
                s.set(x, yb, tz2 + 1, B.stairs("minecraft:stone_brick_stairs", Dir.NORTH, true));
            }
            for (int z = tz1; z <= tz2; z++) {
                s.set(tx1 - 1, yb, z, B.stairs("minecraft:stone_brick_stairs", Dir.EAST, true));
                s.set(tx2 + 1, yb, z, B.stairs("minecraft:stone_brick_stairs", Dir.WEST, true));
            }
        }
        for (int y = F + 1; y <= F + 4; y++)
            for (int x = pc - 1; x <= pc + 1; x++) {
                s.set(x, y, tz1, AIR);
                s.set(x, y, tz2, AIR);
            }
        for (int x = pc - 1; x <= pc + 1; x++) {
            s.set(x, F + 4, tz1, B.stairs("minecraft:stone_brick_stairs", Dir.SOUTH, true));
            s.set(x, F + 4, tz2, B.stairs("minecraft:stone_brick_stairs", Dir.NORTH, true));
        }
        for (int y = top - 4; y <= top - 2; y++) {
            for (int x = pc - 1; x <= pc + 1; x++) {
                s.set(x, y, tz1, AIR);
                s.set(x, y, tz2, AIR);
            }
            for (int z = -29; z <= -27; z++) {
                s.set(tx1, y, z, AIR);
                s.set(tx2, y, z, AIR);
            }
        }
        // cadrans (blocs) et cloche
        s.set(pc, top - 7, tz2 + 1, "minecraft:white_glazed_terracotta[facing=north]");
        s.set(pc, top - 7, tz1 - 1, "minecraft:white_glazed_terracotta[facing=south]");
        Shapes.fill(s, tx1 + 1, top - 5, tz1 + 1, tx2 - 1, top - 5, tz2 - 1, Mat.of("minecraft:spruce_planks"));
        s.set(pc, top - 1, -28, "minecraft:bell[attachment=ceiling,facing=north]");
        Shapes.fill(s, tx1 + 1, top, tz1 + 1, tx2 - 1, top, tz2 - 1, Mat.of("minecraft:spruce_planks"));
        Roof.build(s, new RoofStyle().stairs("minecraft:deepslate_tile_stairs").slab("minecraft:deepslate_tile_slab").ridge("minecraft:deepslate_tile_slab")
                .body(Mat.of("minecraft:deepslate_tiles")).overhang(1), Roof.hip(tx1, tz1, tx2, tz2, top).slope(1.5));
        // jardins : arbres et bancs, decor non symetrique
        int[][] trees = {{p0 + 4, -17}, {p1 - 4, -14}, {p0 + 5, 16}, {p1 - 5, 21}, {p0 + 4, 31}, {p1 - 3, 33}, {p0 + 7, -36}};
        for (int i = 0; i < trees.length; i++) {
            int x = trees[i][0], z = trees[i][1];
            new Tree(i % 3 == 0 ? "minecraft:cherry_log" : "minecraft:oak_log", i % 3 == 0 ? "minecraft:cherry_leaves" : "minecraft:oak_leaves",
                    "minecraft:flowering_azalea_leaves").height(7 + i % 3).crown(2.4).place(s, x, F + 1, z, seed + 77 + i);
        }
        for (int z : new int[]{-12, 12, 24}) {
            lamp(s, pc - 3, z);
            lamp(s, pc + 3, z + 2);
        }
        // dalle secrete (brownie de Mehdi) : alcove de haie derriere la tour, un amethyste en indice
        int[] sp = secretPad(g);
        for (int x = sp[0] - 1; x <= sp[0] + 1; x++)
            for (int z = sp[1] - 1; z <= sp[1] + 1; z++) {
                s.set(x, F, z, "minecraft:moss_block");
                if (x != sp[0] || z != sp[1]) s.set(x, F + 1, z, B.leaves("minecraft:flowering_azalea_leaves"));
            }
        s.set(sp[0], F + 1, sp[1] + 1, AIR);
        s.set(sp[0], F + 1, sp[1], "minecraft:purple_carpet");
        s.set(sp[0] + 1, F + 1, sp[1] + 1, "minecraft:small_amethyst_bud[facing=up]");
    }

    /** Dalle secrete de la rue {x, z} (on se tient dessus en y F+1). */
    public static int[] secretPad(Geo g) {
        int pc = g.plazaX0 + Geo.PLAZA / 2;
        return new int[]{pc + 6, -35};
    }

    /** Point d'arrivee dans la rue {x, y, z, yaw}. */
    public static double[] spawn(Geo g) {
        return new double[]{g.plazaX0 + Geo.PLAZA / 2 + 0.5, F + 1, 8.5, 180};
    }

    // =================================================================== parcelle

    static Style style(int variant) {
        Style st = new Style().spacing(5).storey(6).seed(variant * 17L + 3);
        st.groundDepth = 2;
        st.innerPosts = false;
        switch (variant & 3) {
            case 0 -> st.wall(Palette.of(101, "minecraft:bricks", 68, "minecraft:polished_granite", 17, "minecraft:terracotta", 15).period(8).amp(0.2))
                    .post("minecraft:stripped_dark_oak_log").sill("minecraft:brick_stairs").lintel("minecraft:brick_slab")
                    .cornice("minecraft:dark_oak_stairs").shutter("minecraft:dark_oak_trapdoor").band(null).door("minecraft:spruce_door")
                    .plinth(Mat.of("minecraft:stone_bricks")).plinthStairs("minecraft:stone_brick_stairs");
            case 1 -> st.wall(Palette.of(102, "minecraft:white_terracotta", 62, "minecraft:calcite", 23, "minecraft:diorite", 15).period(8).amp(0.2))
                    .post("minecraft:stripped_oak_log").sill("minecraft:smooth_quartz_stairs").lintel("minecraft:smooth_quartz_slab")
                    .cornice("minecraft:spruce_stairs").shutter("minecraft:warped_trapdoor").band(null).door("minecraft:birch_door")
                    .plinth(Mat.of("minecraft:polished_andesite")).plinthStairs("minecraft:polished_andesite_stairs");
            case 2 -> st.wall(Palette.of(103, "minecraft:stone_bricks", 62, "minecraft:cracked_stone_bricks", 20, "minecraft:mossy_stone_bricks", 18).period(8).amp(0.2))
                    .post("minecraft:stripped_spruce_log").sill("minecraft:stone_brick_stairs").lintel("minecraft:stone_brick_slab")
                    .cornice("minecraft:spruce_stairs").shutter("minecraft:spruce_trapdoor").band(null).door("minecraft:dark_oak_door")
                    .plinth(Mat.of("minecraft:cobbled_deepslate")).plinthStairs("minecraft:cobbled_deepslate_stairs");
            default -> st.wall(Palette.of(104, "minecraft:smooth_sandstone", 62, "minecraft:sandstone", 23, "minecraft:cut_sandstone", 15).period(8).amp(0.2))
                    .post("minecraft:stripped_oak_log").sill("minecraft:sandstone_stairs").lintel("minecraft:smooth_sandstone_slab")
                    .cornice("minecraft:oak_stairs").shutter("minecraft:mangrove_trapdoor").band(null).door("minecraft:oak_door")
                    .plinth(Mat.of("minecraft:smooth_stone")).plinthStairs("minecraft:stone_stairs");
        }
        return st;
    }

    static RoofStyle roof(int variant) {
        return switch (variant & 3) {
            case 0 -> new RoofStyle().stairs("minecraft:dark_oak_stairs")
                    .slab("minecraft:dark_oak_slab").ridge("minecraft:dark_oak_slab").body(Mat.of("minecraft:dark_oak_planks")).gable(Mat.of("minecraft:spruce_planks")).overhang(1);
            case 1 -> new RoofStyle().stairs("minecraft:deepslate_tile_stairs").slab("minecraft:deepslate_tile_slab").ridge("minecraft:deepslate_tile_slab")
                    .body(Mat.of("minecraft:deepslate_tiles")).gable(Mat.of("minecraft:spruce_planks")).overhang(1);
            case 2 -> new RoofStyle().stairs("minecraft:spruce_stairs").slab("minecraft:spruce_slab").ridge("minecraft:spruce_slab")
                    .body(Mat.of("minecraft:spruce_planks")).gable(Mat.of("minecraft:stripped_spruce_wood[axis=y]")).overhang(1);
            default -> new RoofStyle().stairs("minecraft:mud_brick_stairs").slab("minecraft:mud_brick_slab").ridge("minecraft:mud_brick_slab")
                    .body(Mat.of("minecraft:mud_bricks")).gable(Mat.of("minecraft:smooth_sandstone")).overhang(1);
        };
    }

    /** Dessine toute la parcelle dans le Sink monde. */
    public static void parcel(Sink world, Geo g, int idx, State st) {
        Sink s = g.local(world, idx);
        int variant = idx * 7 % 4;
        long seed = 9000L + idx * 131L;
        Palette lot = Palette.of(seed, "minecraft:coarse_dirt", 55, "minecraft:gravel", 25, "minecraft:rooted_dirt", 20).period(5);
        for (int u = 0; u < Geo.W; u++)
            for (int v = 0; v < Geo.D; v++) {
                s.set(u, F, v, lot.at(g.wx(idx, u, v), F, g.wz(idx, u, v)));
                for (int y = F + 1; y <= Geo.TOP; y++) s.set(u, y, v, AIR);
            }
        // haies de bord et arche d'entree
        Noise hn = new Noise(seed + 3);
        for (int v = 6; v < Geo.D; v++) {
            hedge(s, hn, 0, v, 1, 0);
            hedge(s, hn, Geo.W - 1, v, -1, 0);
        }
        for (int u = 1; u < Geo.W - 1; u++) hedge(s, hn, u, Geo.D - 1, 0, -1);
        for (int u : new int[]{9, 13}) {
            for (int y = Y0; y <= Y0 + 2; y++) s.set(u, y, 0, "minecraft:stripped_spruce_log[axis=y]");
            s.set(u, Y0 + 4, 0, "minecraft:lantern[hanging=false]");
        }
        for (int u = 9; u <= 13; u++) s.set(u, Y0 + 3, 0, "minecraft:stripped_spruce_log[axis=x]");
        if (st.bought() != null) {
            Set<String> b = st.bought();
            for (Defs.Purchase p : Defs.PURCHASES) if (b.contains(p.id())) piece(s, p.id(), b, variant, seed);
            // boutons d'achat (la couleur est remise par le jeu selon l'argent)
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

    /** Haie de bord : hauteur 1 ou 2 et renflements vers l'interieur par bruit (pas un pan plat). */
    static void hedge(Sink s, Noise n, int u, int v, int iu, int iv) {
        double a = n.uniform2(u / 4.0, v / 4.0), b = n.uniform2(u / 3.0 + 40, v / 3.0 + 40);
        String leaf = B.leaves(b > 0.78 ? "minecraft:flowering_azalea_leaves" : "minecraft:oak_leaves");
        s.set(u, Y0, v, leaf);
        if (a > 0.55) s.set(u, Y0 + 1, v, leaf);
        if (a > 0.72) s.set(u + iu, Y0, v + iv, leaf);
    }

    static final Palette KITCHEN = Palette.of(301, "minecraft:smooth_quartz", 62, "minecraft:polished_diorite", 23, "minecraft:calcite", 15).period(5);
    static final Palette PARQUET = Palette.of(302, "minecraft:dark_oak_planks", 62, "minecraft:spruce_planks", 28, "minecraft:stripped_dark_oak_wood[axis=y]", 10).period(6);

    static void floor(Sink s, int u1, int v1, int u2, int v2, Palette p, long seed) {
        for (int u = u1; u <= u2; u++)
            for (int v = v1; v <= v2; v++) s.set(u, F, v, p.at(u + (int) seed, F, v));
    }

    static void table(Sink s, int u, int v, String cloth) {
        s.set(u, Y0, v, "minecraft:dark_oak_fence");
        s.set(u, Y0 + 1, v, cloth);
        // chaises : escaliers dos au mur, tournes vers la table
        s.set(u - 1, Y0, v, B.stairs("minecraft:spruce_stairs", Dir.WEST, false));
        s.set(u + 1, Y0, v, B.stairs("minecraft:spruce_stairs", Dir.EAST, false));
    }

    static void hangingLight(Sink s, int u, int v) {
        s.set(u, WALL_TOP - 1, v, "minecraft:iron_chain[axis=y]");
        s.set(u, WALL_TOP - 2, v, "minecraft:lantern[hanging=true]");
    }

    static void piece(Sink s, String id, Set<String> b, int variant, long seed) {
        Style st = style(variant);
        Defs.Dropper dr = Defs.dropper(id);
        if (dr != null) {
            dropper(s, dr);
            if (id.equals("grill")) s.set(Defs.BK_PAD[0], Y0, Defs.BK_PAD[1], "minecraft:brown_carpet");
            return;
        }
        Defs.Upgrader up = Defs.upgrader(id);
        if (up != null) {
            upgrader(s, up);
            return;
        }
        switch (id) {
            case "sol_depart" -> {
                floor(s, 4, 17, 19, 25, KITCHEN, seed);
                floor(s, 10, 5, 13, 16, PARQUET, 0);
            }
            case "tapis_a" -> belt(s, Defs.LINE_A);
            case "tapis_b" -> belt(s, Defs.LINE_B);
            case "comptoir" -> {
                for (int u = 4; u <= 17; u++) {
                    if (Defs.pathIndex(Defs.LINE_A, u, 17) >= 0 || Defs.pathIndex(Defs.LINE_B, u, 17) >= 0 && b.contains("tapis_b")) continue;
                    s.set(u, Y0, 17, "minecraft:stripped_spruce_log[axis=x]");
                    s.set(u, Y0 + 1, 17, B.slab("minecraft:smooth_quartz_slab", "bottom"));
                }
                s.set(Defs.COLLECTOR[0], F, Defs.COLLECTOR[1], "minecraft:gold_block");
                s.set(12, Y0 + 1, 17, "minecraft:lectern[facing=north]");
            }
            case "sol_salle" -> floor(s, 4, 6, 19, 16, PARQUET, 0);
            case "mur_ouest" -> Facade.wall(s, st, 2, 5, Dir.SOUTH, 22, Dir.WEST, F, WALL_TOP, -1);
            case "mur_est" -> Facade.wall(s, st, 21, 5, Dir.SOUTH, 22, Dir.EAST, F, WALL_TOP, -1);
            case "mur_fond" -> Facade.wall(s, st, 2, 27, Dir.EAST, 19, Dir.SOUTH, F, WALL_TOP, -1);
            case "facade" -> Facade.wall(s, st, 2, 5, Dir.EAST, 19, Dir.NORTH, F, WALL_TOP, 9);
            case "lampes" -> {
                for (int v : new int[]{19, 23}) {
                    s.set(4, Y0, v, "minecraft:spruce_fence");
                    s.set(4, Y0 + 1, v, "minecraft:spruce_fence");
                    s.set(4, Y0 + 2, v, "minecraft:lantern[hanging=false]");
                }
            }
            case "tables1" -> {
                table(s, 6, 9, "minecraft:white_carpet");
                table(s, 16, 9, "minecraft:red_carpet");
            }
            case "tables2" -> {
                table(s, 6, 13, "minecraft:red_carpet");
                table(s, 16, 13, "minecraft:white_carpet");
            }
            case "toit" -> {
                Shapes.fill(s, 4, WALL_TOP, 6, 19, WALL_TOP, 26, Mat.of("minecraft:spruce_planks"));
                int ridge = Roof.build(s, roof(variant), Roof.hip(2, 5, 21, 27, WALL_TOP));
                Roof.chimney(s, 16, 23, 17, 24, WALL_TOP + 1, Math.max(WALL_TOP + 4, ridge + 1), Mat.of("minecraft:bricks"), "minecraft:brick_slab");
            }
            case "enseigne" -> {
                for (int u = 9; u <= 13; u++)
                    for (int y = 68; y <= 69; y++) s.set(u, y, 5, (u == 9 || u == 13) ? "minecraft:stripped_dark_oak_log[axis=y]" : "minecraft:dark_oak_planks");
            }
            case "terrasse" -> {
                floor(s, 2, 1, 21, 4, Palette.of(seed + 5, "minecraft:spruce_planks", 75, "minecraft:stripped_spruce_wood[axis=y]", 25).period(5), seed);
                for (int u = 2; u <= 21; u++) if (u < 9 || u > 13) s.set(u, Y0, 1, "minecraft:spruce_fence");
                for (int v = 2; v <= 4; v++) {
                    s.set(2, Y0, v, "minecraft:spruce_fence");
                    s.set(21, Y0, v, "minecraft:spruce_fence");
                }
                table(s, 5, 3, "minecraft:white_carpet");
                table(s, 18, 3, "minecraft:white_carpet");
            }
            case "lustres" -> {
                for (int[] p : new int[][]{{8, 9}, {15, 9}, {8, 13}, {15, 13}, {11, 11}, {12, 21}, {15, 18}, {16, 23}}) hangingLight(s, p[0], p[1]);
            }
            case "auvent" -> {
                for (int u = 9; u <= 13; u++) {
                    String id2 = (u & 1) == 0 ? "minecraft:red_nether_brick" : "minecraft:smooth_quartz";
                    s.set(u, 67, 4, B.stairs(id2 + "_stairs", Dir.SOUTH, false));
                    s.set(u, 67, 3, B.slab(id2 + "_slab", "bottom"));
                }
            }
            case "plantes" -> {
                for (int[] p : new int[][]{{4, 7}, {19, 7}, {4, 16}, {19, 15}, {8, 16}, {15, 16}})
                    s.set(p[0], Y0, p[1], (p[0] + p[1]) % 2 == 0 ? "minecraft:potted_azalea_bush" : "minecraft:potted_flowering_azalea_bush");
            }
            case "parasols" -> {
                for (int u : new int[]{5, 18}) {
                    s.set(u, Y0 + 1, 3, "minecraft:dark_oak_fence");
                    s.set(u, Y0 + 2, 3, "minecraft:dark_oak_fence");
                    for (int du = -1; du <= 1; du++)
                        for (int dv = -1; dv <= 1; dv++)
                            s.set(u + du, Y0 + 3, 3 + dv, du == 0 && dv == 0 ? "minecraft:white_wool" : B.slab("minecraft:smooth_quartz_slab", "top"));
                }
            }
            case "jardin" -> {
                Noise n = new Noise(seed + 41);
                String[] flowers = {"minecraft:poppy", "minecraft:dandelion", "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:cornflower"};
                for (int v = 5; v <= 28; v++)
                    for (int u : new int[]{1, Geo.W - 2}) {
                        s.set(u, F, v, "minecraft:grass_block[snowy=false]");
                        double f = n.uniform2(u / 3.0, v / 3.0);
                        if (f > 0.55) s.set(u, Y0, v, flowers[(int) (n.uniform2(u / 7.0 + 9, v / 7.0) * 4.99)]);
                        else if (f < 0.18) s.set(u, Y0, v, B.leaves("minecraft:flowering_azalea_leaves"));
                    }
                for (int u = 2; u <= Geo.W - 3; u++) {
                    s.set(u, F, 28, "minecraft:grass_block[snowy=false]");
                    if (u % 5 == 2) s.set(u, Y0, 28, "minecraft:rose_bush[half=lower]");
                    if (u % 5 == 2) s.set(u, Y0 + 1, 28, "minecraft:rose_bush[half=upper]");
                }
            }
            case "fontaine" -> {
                for (int cu : new int[]{1, Geo.W - 2}) {
                    for (int du = -1; du <= 1; du++)
                        for (int dv = -1; dv <= 1; dv++) {
                            int u = cu + du, v = 2 + dv;
                            s.set(u, F, v, "minecraft:polished_andesite");
                            s.set(u, Y0, v, du == 0 && dv == 0 ? "minecraft:water" : "minecraft:stone_brick_wall");
                        }
                    s.set(cu, F, 2, "minecraft:sea_lantern");
                }
            }
            case "etoile" -> {
                s.set(11, Y0 + 3, 0, "minecraft:gold_block");
                s.set(11, Y0 + 4, 0, "minecraft:gold_block");
                s.set(10, Y0 + 4, 0, B.slab("minecraft:smooth_quartz_slab", "bottom"));
                s.set(12, Y0 + 4, 0, B.slab("minecraft:smooth_quartz_slab", "bottom"));
                s.set(11, Y0 + 5, 0, "minecraft:lightning_rod[facing=up]");
            }
            default -> {
                // commis, cartes, tapis rapides : pas de blocs
            }
        }
    }

    static void belt(Sink s, Defs.Line l) {
        for (int[] c : l.path()) {
            s.set(c[0], Y0, c[1], B.slab("minecraft:polished_blackstone_slab", "top"));
            s.set(c[0], Y0 + 1, c[1], AIR);
        }
    }

    static void dropper(Sink s, Defs.Dropper d) {
        int u = d.u(), v = d.v();
        String n = Dir.NORTH.id();
        switch (d.purchase()) {
            case "four_pain" -> {
                s.set(u, Y0, v, "minecraft:smoker[facing=" + n + ",lit=true]");
                s.set(u, Y0 + 1, v, "minecraft:bricks");
                s.set(u, Y0 + 2, v, B.slab("minecraft:brick_slab", "bottom"));
            }
            case "friteuse" -> {
                s.set(u, Y0, v, "minecraft:blast_furnace[facing=" + n + ",lit=true]");
                s.set(u, Y0 + 1, v, B.trapdoor("minecraft:iron_trapdoor", Dir.NORTH, false, false));
            }
            case "grill" -> {
                s.set(u, Y0, v, "minecraft:polished_blackstone_bricks");
                s.set(u, Y0 + 1, v, "minecraft:campfire[facing=" + n + ",lit=true,signal_fire=false,waterlogged=false]");
            }
            case "four_pizza" -> {
                s.set(u, Y0, v, "minecraft:furnace[facing=" + n + ",lit=true]");
                s.set(u, Y0 + 1, v, "minecraft:bricks");
                s.set(u, Y0 + 2, v, "minecraft:brick_wall");
            }
            case "marmite" -> {
                s.set(u, Y0, v, "minecraft:campfire[facing=" + n + ",lit=true,signal_fire=false,waterlogged=false]");
                s.set(u, Y0 + 1, v, "minecraft:water_cauldron[level=3]");
            }
            case "salades" -> {
                s.set(u, Y0, v, "minecraft:spruce_planks");
                s.set(u, Y0 + 1, v, "minecraft:potted_fern");
            }
            case "sushi" -> {
                s.set(u, Y0, v, "minecraft:bamboo_block[axis=y]");
                s.set(u, Y0 + 1, v, B.slab("minecraft:bamboo_mosaic_slab", "bottom"));
            }
            case "patisserie" -> {
                s.set(u, Y0, v, "minecraft:white_terracotta");
                s.set(u, Y0 + 1, v, "minecraft:cake[bites=0]");
            }
            case "glacier" -> {
                s.set(u, Y0, v, "minecraft:packed_ice");
                s.set(u, Y0 + 1, v, "minecraft:powder_snow_cauldron[level=3]");
            }
            default -> s.set(u, Y0, v, "minecraft:furnace[facing=" + n + ",lit=true]");
        }
    }

    static void upgrader(Sink s, Defs.Upgrader up) {
        String color = switch (up.purchase()) {
            case "epices" -> "minecraft:red_concrete";
            case "sauce" -> "minecraft:orange_concrete";
            case "emballage" -> "minecraft:white_concrete";
            case "presentation" -> "minecraft:gold_block";
            default -> "minecraft:lime_concrete";
        };
        int u = up.u(), v = up.v();
        int du = up.column() ? 1 : 0, dv = up.column() ? 0 : 1;
        for (int k = -1; k <= 1; k += 2) {
            s.set(u + k * du, Y0, v + k * dv, "minecraft:polished_blackstone_wall");
            s.set(u + k * du, Y0 + 1, v + k * dv, "minecraft:polished_blackstone_wall");
        }
        for (int k = -1; k <= 1; k++) s.set(u + k * du, Y0 + 2, v + k * dv, k == 0 ? color : "minecraft:polished_blackstone_bricks");
    }
}
