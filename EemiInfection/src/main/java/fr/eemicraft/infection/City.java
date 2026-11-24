package fr.eemicraft.infection;

import fr.eemicraft.infection.build.B;
import fr.eemicraft.infection.build.Connect;
import fr.eemicraft.infection.build.Dir;
import fr.eemicraft.infection.build.Facade;
import fr.eemicraft.infection.build.Mat;
import fr.eemicraft.infection.build.Noise;
import fr.eemicraft.infection.build.Palette;
import fr.eemicraft.infection.build.Roof;
import fr.eemicraft.infection.build.RoofStyle;
import fr.eemicraft.infection.build.Shapes;
import fr.eemicraft.infection.build.Sink;
import fr.eemicraft.infection.build.Stairs;
import fr.eemicraft.infection.build.Style;
import fr.eemicraft.infection.build.Tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.logging.Logger;

/**
 * Ville abandonnee de nuit, construite par code (version 3, 16/11/2026, refaite selon le skill builder-minecraft).
 * x vers l'est, z vers le sud. Sol des rues en y = G (on marche en G + 1) ; le terrain hors des rues monte de 0 a 3.
 *
 * <p>Ancienne ville au centre (|x|,|z| <= 45) : NO eglise a bas-cotes + clocher (repere) + cimetiere (crypte dessous) ;
 * NE supermarche en 3 volumes + parking ; SO parc vallonne ; SE station-service, immeuble, maisons. Boulevard carre
 * (47..53), deux rangees de parcelles sur chaque cote (maisons a toits en pente, commerces a toit plat relies par des
 * passerelles, chantiers, cours), coins a theme. Limite : palissades, haies, gravats, carcasses ; barrieres invisibles
 * seulement derriere, terrain qui monte et foret au-dela. Hangar d'attente au sud, hors des murs.
 *
 * <p>Ecrit dans un {@link Sink} (BukkitSink sur le serveur, RecordingSink pour le test hors serveur).
 */
final class City {
    static final int G = 64;
    /** Limite jouable : barrieres en x/z = +-WALL. */
    static final int WALL = 78;
    /** Ancienne ville (centre). */
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

    private final Sink s;
    private final Logger log;
    private final Random rnd = new Random(20261116L);
    private final Noise nz = new Noise(20261116L);
    private final Noise nz2 = new Noise(77017L);
    /** Cases a laisser libres (portes, escaliers, sortie de la crypte) : cle x/y/z. */
    private final Set<Long> keep = new HashSet<>();
    long ops;

    // grille du terrain
    private static final int TX0 = -EXT, TZ0 = -EXT, TNX = 2 * EXT + 1, TNZ = HZ2 + 16 + EXT + 1;
    private final int[][] hg = new int[TNX][TNZ];
    private final boolean[][] flat = new boolean[TNX][TNZ];

    private final List<Lot> lots = new ArrayList<>();
    /** Acces demandes (echelles, escaliers de secours) : chaque groupe = options essayees dans l'ordre, la 1re possible est posee. */
    private final List<int[][]> ladders = new ArrayList<>();
    /** Volees d'escalier praticables posees : {x, y, z de la 1re marche, facing (ordinal de Dir), nombre de marches}. Lues par le test hors serveur. */
    final List<int[]> flights = new ArrayList<>();
    private int buildings;

    City(Sink sink, Logger log) {
        this.log = log;
        this.s = new Sink() {
            @Override
            public void set(int x, int y, int z, String st) {
                ops++;
                sink.set(x, y, z, st);
            }

            @Override
            public String get(int x, int y, int z) {
                return sink.get(x, y, z);
            }
        };
    }

    // ================================================================ outils

    void set(int x, int y, int z, String b) {
        s.set(x, y, z, B.id(b));
    }

    void set(int x, int y, int z, Mat m) {
        s.set(x, y, z, m.at(x, y, z));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        String id = B.id(b);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) s.set(x, y, z, id);
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Mat m) {
        Shapes.fill(s, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), m);
    }

    boolean air(int x, int y, int z) {
        return s.isAir(x, y, z);
    }

    String nm(int x, int y, int z) {
        return B.name(s.get(x, y, z));
    }

    void setIfAir(int x, int y, int z, String b) {
        if (air(x, y, z)) set(x, y, z, b);
    }

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

    boolean kept(int x, int y, int z) {
        return keep.contains(key(x, y, z));
    }

    /** Meuble / decor : seulement dans l'air, hors des passages et pas devant une porte. */
    void furn(int x, int y, int z, String b) {
        if (!air(x, y, z) || kept(x, y, z)) return;
        for (Dir d : Dir.values()) {
            String n = nm(x + d.dx, y, z + d.dz);
            if (n.endsWith("_door") || n.equals("ladder")) return;
        }
        set(x, y, z, b);
    }

    static String stairs(String id, Dir facing, boolean top) {
        return B.stairs(B.id(id), facing, top);
    }

    static String slab(String id, boolean top) {
        return B.slab(B.id(id), top ? "top" : "bottom");
    }

    /** Hauteur du terrain (0..) au-dessus de G. */
    int h(int x, int z) {
        int i = x - TX0, k = z - TZ0;
        if (i < 0 || k < 0 || i >= TNX || k >= TNZ) return 0;
        return hg[i][k];
    }

    /** Y du bloc de surface. */
    int top(int x, int z) {
        return G + h(x, z);
    }

    void markFlat(int x1, int z1, int x2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                int i = x - TX0, k = z - TZ0;
                if (i >= 0 && k >= 0 && i < TNX && k < TNZ) flat[i][k] = true;
            }
    }

    static boolean in(int x, int z, int x1, int z1, int x2, int z2) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    // ================================================================ palettes et styles

    static Palette pal(long seed, Object... sw) {
        return Palette.of(seed, sw).period(10).amp(0.2);
    }

    /** Style de maison (toit en pente). Identifiants sans propriete. */
    static final class HStyle {
        final Palette wall, roof;
        final String plinth, plinthStairs, post, sill, lintel, band, cornice, door, shutter, wood, ridge, body, gablePost, chimney;

        HStyle(Palette wall, String plinth, String plinthStairs, String post, String sill, String lintel, String band, String cornice,
               String door, String shutter, String wood, Palette roof, String ridge, String body, String gablePost, String chimney) {
            this.wall = wall;
            this.plinth = plinth;
            this.plinthStairs = plinthStairs;
            this.post = post;
            this.sill = sill;
            this.lintel = lintel;
            this.band = band;
            this.cornice = cornice;
            this.door = door;
            this.shutter = shutter;
            this.wood = wood;
            this.roof = roof;
            this.ridge = ridge;
            this.body = body;
            this.gablePost = gablePost;
            this.chimney = chimney;
        }

        Style style(long seed, int spacing) {
            return new Style().wall(wall).plinth(Mat.of(plinth)).plinthStairs(plinthStairs).post(post).sill(sill).lintel(lintel)
                    .shutter(shutter).band(band).cornice(cornice).door(door).floor(Mat.of(wood + "_planks")).spacing(spacing)
                    .storey(4).seed(seed).shutters(0.55).blankBays(0.2);
        }

        RoofStyle roofStyle(int spacing) {
            return new RoofStyle().stairs(roof).slab(ridge).ridge(ridge).body(Mat.of(body)).gable(wall).gablePost(gablePost)
                    .overhang(1).spacing(spacing);
        }
    }

    static final HStyle[] HOUSES = {
        // 0 briques, ardoise
        new HStyle(pal(101, "bricks", 80, "terracotta", 12, "brown_terracotta", 8), "cobbled_deepslate", "cobbled_deepslate_stairs",
                "stripped_dark_oak_log", "stone_brick_stairs", "stone_brick_slab", "brick_stairs", "stone_brick_stairs", "dark_oak_door",
                "dark_oak_trapdoor", "dark_oak", pal(102, "cobbled_deepslate_stairs", 25, "deepslate_tile_stairs", 55, "deepslate_brick_stairs", 20),
                "deepslate_tile_slab", "deepslate_tiles", "stripped_dark_oak_log", "bricks"),
        // 1 crepi blanc et colombage, tuiles
        new HStyle(pal(103, "white_terracotta", 85, "calcite", 15), "cobblestone", "cobblestone_stairs", "stripped_dark_oak_log",
                "spruce_stairs", "spruce_slab", "spruce_stairs", "spruce_stairs", "spruce_door", "spruce_trapdoor", "oak",
                pal(104, "mud_brick_stairs", 20, "brick_stairs", 60, "granite_stairs", 20), "brick_slab", "bricks", "stripped_dark_oak_log", "bricks"),
        // 2 pierre, bardeaux
        new HStyle(pal(105, "stone_bricks", 84, "mossy_stone_bricks", 16), "cobbled_deepslate", "cobbled_deepslate_stairs",
                "stripped_spruce_log", "spruce_stairs", "spruce_slab", "spruce_stairs", "dark_oak_stairs", "spruce_door", "spruce_trapdoor",
                "spruce", pal(106, "spruce_stairs", 50, "dark_oak_stairs", 50), "dark_oak_slab", "dark_oak_planks", "stripped_spruce_log", "cobblestone"),
        // 3 crepi gris, ardoise
        new HStyle(pal(107, "light_gray_terracotta", 85, "andesite", 15), "cobbled_deepslate", "cobbled_deepslate_stairs",
                "stripped_spruce_log", "stone_stairs", "stone_slab", "stone_brick_stairs", "stone_brick_stairs", "birch_door", "birch_trapdoor",
                "birch", pal(108, "stone_brick_stairs", 30, "cobbled_deepslate_stairs", 45, "polished_blackstone_brick_stairs", 25),
                "cobbled_deepslate_slab", "cobbled_deepslate", "stripped_spruce_log", "bricks"),
        // 4 torchis ocre, bois rouge
        new HStyle(pal(109, "mud_bricks", 80, "packed_mud", 12, "brown_terracotta", 8), "cobblestone", "cobblestone_stairs",
                "stripped_jungle_log", "mud_brick_stairs", "mud_brick_slab", "jungle_stairs", "jungle_stairs", "jungle_door", "jungle_trapdoor",
                "jungle", pal(110, "acacia_stairs", 30, "mangrove_stairs", 50, "dark_oak_stairs", 20), "mangrove_slab", "mangrove_planks",
                "stripped_jungle_log", "bricks"),
        // 5 crepi jaune, volets verts
        new HStyle(pal(111, "yellow_terracotta", 82, "sandstone", 12, "terracotta", 6), "stone_bricks", "stone_brick_stairs",
                "stripped_oak_log", "sandstone_stairs", "sandstone_slab", "oak_stairs", "smooth_sandstone_stairs", "oak_door", "warped_trapdoor",
                "oak", pal(112, "spruce_stairs", 45, "dark_oak_stairs", 55), "spruce_slab", "spruce_planks", "stripped_oak_log", "cobblestone")
    };

    /** Style de commerce / immeuble (toit plat a acrotere). */
    static final class UStyle {
        final Palette wall;
        final String plinth, plinthStairs, post, sill, lintel, band, cornice, door, wood, coping, awning;
        final Palette roofSurface;

        UStyle(Palette wall, String plinth, String plinthStairs, String post, String sill, String lintel, String band, String cornice,
               String door, String wood, String coping, String awning, Palette roofSurface) {
            this.wall = wall;
            this.plinth = plinth;
            this.plinthStairs = plinthStairs;
            this.post = post;
            this.sill = sill;
            this.lintel = lintel;
            this.band = band;
            this.cornice = cornice;
            this.door = door;
            this.wood = wood;
            this.coping = coping;
            this.awning = awning;
            this.roofSurface = roofSurface;
        }

        Style style(long seed) {
            return new Style().wall(wall).plinth(Mat.of(plinth)).plinthStairs(plinthStairs).post(post).sill(sill).lintel(lintel)
                    .shutter(null).band(band).cornice(cornice).door(door).floor(Mat.of(wood + "_planks")).spacing(4).storey(4)
                    .seed(seed).blankBays(0.12);
        }
    }

    static Palette roofSurf(long seed) {
        return roofSurf(seed, "smooth_stone");
    }

    static Palette roofSurf(long seed, String base) {
        return Palette.of(seed, base, 84, "moss_block", 10, "coarse_dirt", 6).period(6);
    }

    static final UStyle[] URBAN = {
        new UStyle(pal(201, "light_gray_concrete", 86, "smooth_stone", 14), "polished_blackstone", "polished_blackstone_stairs", "quartz_pillar",
                "smooth_quartz_stairs", "smooth_stone_slab", "smooth_quartz_stairs", "smooth_quartz_stairs", "dark_oak_door", "spruce",
                "smooth_stone_slab", "red_wool", roofSurf(301, "smooth_stone")),
        new UStyle(pal(202, "bricks", 88, "packed_mud", 12), "polished_andesite", "polished_andesite_stairs", "polished_basalt",
                "stone_brick_stairs", "stone_brick_slab", "stone_brick_stairs", "stone_brick_stairs", "spruce_door", "spruce",
                "stone_brick_slab", "green_wool", roofSurf(302, "polished_andesite")),
        new UStyle(pal(203, "smooth_sandstone", 85, "white_terracotta", 15), "polished_granite", "polished_granite_stairs", "stripped_oak_log",
                "sandstone_stairs", "smooth_sandstone_slab", "smooth_sandstone_stairs", "smooth_sandstone_stairs", "oak_door", "oak",
                "smooth_sandstone_slab", "blue_wool", roofSurf(303, "polished_granite")),
        new UStyle(pal(204, "cyan_terracotta", 85, "tuff", 15), "cobbled_deepslate", "cobbled_deepslate_stairs", "stripped_birch_log",
                "polished_andesite_stairs", "polished_andesite_slab", "polished_andesite_stairs", "polished_andesite_stairs", "birch_door", "birch",
                "polished_andesite_slab", "orange_wool", roofSurf(304, "black_concrete")),
        new UStyle(pal(205, "deepslate_bricks", 85, "tuff_bricks", 15), "polished_blackstone", "polished_blackstone_stairs", "quartz_pillar",
                "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_stairs", "polished_blackstone_stairs", "dark_oak_door",
                "dark_oak", "polished_blackstone_slab", "yellow_wool", roofSurf(305, "smooth_stone")),
        new UStyle(pal(206, "white_concrete", 80, "calcite", 20), "polished_andesite", "polished_andesite_stairs", "stripped_spruce_log",
                "andesite_stairs", "andesite_slab", "stone_stairs", "stone_brick_stairs", "spruce_door", "spruce",
                "stone_brick_slab", "purple_wool", roofSurf(306, "tuff"))
    };

    // ================================================================ parcelles

    static final int HOUSE = 0, SHOP = 1, YARD = 2, SITE = 3;

    static final class Lot {
        final int x1, z1, x2, z2;
        final Dir door;
        int kind, storeys, style;
        boolean ruined;
        final long seed;
        int roofY = -1;
        int side = -1, row = -1, t1, t2, a1, a2;

        Lot(int x1, int z1, int x2, int z2, Dir door, int kind, int storeys, int style, boolean ruined, long seed) {
            this.x1 = Math.min(x1, x2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.z2 = Math.max(z1, z2);
            this.door = door;
            this.kind = kind;
            this.storeys = storeys;
            this.style = style;
            this.ruined = ruined;
            this.seed = seed;
        }

        Frame frame() {
            return new Frame(x1, z1, x2, z2, door);
        }
    }

    /** Repere local d'une parcelle : u le long de la rue, v en profondeur depuis la facade (v = 0 cote porte). */
    static final class Frame {
        final int x1, z1, x2, z2, W, D;
        final Dir door, up, in;

        Frame(int x1, int z1, int x2, int z2, Dir door) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            this.door = door;
            this.up = door.ccw();
            this.in = door.opposite();
            boolean ns = door.axis() == 'z';
            W = ns ? x2 - x1 + 1 : z2 - z1 + 1;
            D = ns ? z2 - z1 + 1 : x2 - x1 + 1;
        }

        int x(int u, int v) {
            return switch (door) {
                case SOUTH -> x1 + u;
                case NORTH -> x2 - u;
                case EAST -> x2 - v;
                default -> x1 + v;
            };
        }

        int z(int u, int v) {
            return switch (door) {
                case SOUTH -> z2 - v;
                case NORTH -> z1 + v;
                case EAST -> z2 - u;
                default -> z1 + u;
            };
        }

        /** Profondeur v d'une case du monde (0 = facade). */
        int v(int x, int z) {
            return switch (door) {
                case SOUTH -> z2 - z;
                case NORTH -> z - z1;
                case EAST -> x2 - x;
                default -> x - x1;
            };
        }

        /** {ax, az, bx, bz} du rectangle (u0..u1, v0..v1). */
        int[] rect(int u0, int v0, int u1, int v1) {
            int xa = x(u0, v0), za = z(u0, v0), xb = x(u1, v1), zb = z(u1, v1);
            return new int[]{Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb)};
        }
    }

    static Roof.Part gable(char ridgeAxis, int[] r, int yTop) {
        return ridgeAxis == 'x' ? Roof.gableX(r[0], r[1], r[2], r[3], yTop) : Roof.gableZ(r[0], r[1], r[2], r[3], yTop);
    }

    /** Cellules des poteaux (plan P) d'un mur pose par Facade.building, dans l'ordre. */
    static List<int[]> wallPosts(int ax, int az, int bx, int bz, Dir side, int spacing) {
        List<int[]> r = new ArrayList<>();
        int len = side.axis() == 'z' ? bx - ax : bz - az;
        for (int t : Facade.postPositions(len, spacing)) {
            switch (side) {
                case NORTH -> r.add(new int[]{ax + t, az});
                case SOUTH -> r.add(new int[]{ax + t, bz});
                case WEST -> r.add(new int[]{ax, az + t});
                default -> r.add(new int[]{bx, az + t});
            }
        }
        return r;
    }

    // ================================================================ construction complete

    void build() {
        long t0 = System.currentTimeMillis();
        // 1. on efface tout (ville, hangar, crypte)
        fill(-EXT, G - 14, -EXT, EXT, G + 42, HZ2 + 16, "minecraft:air");
        // 2. plan, terrain
        plan();
        terrain();
        roads();
        // 3. ancienne ville
        park();
        cemetery();
        crypt();
        church();
        supermarket();
        gasStation();
        for (Lot l : lots) buildLot(l);
        // 4. boulevard, coins
        bridges();
        corners();
        doLadders();
        // 5. rues, limites, alentours
        streetObstacles();
        alleyClutter();
        lamps();
        streetTrees();
        boundary();
        outskirts();
        hangar();
        decorate();
        lights();
        clearFlights();
        if (log != null) log.info("Ville : " + buildings + " batiments, " + lots.size() + " parcelles, " + ops + " operations, "
                + (System.currentTimeMillis() - t0) + " ms.");
    }

    // ================================================================ plan des parcelles

    void plan() {
        // rues et trottoirs
        markFlat(-WALL, -5, WALL, 5);
        markFlat(-5, -WALL, 5, WALL);
        for (int x = -55; x <= 55; x++)
            for (int z = -55; z <= 55; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d >= 47) markFlat(x, z, x, z);
            }
        for (int side = 0; side < 4; side++)
            for (int t = -55; t <= 55; t++)
                for (int a = 65; a <= 67; a++) {
                    int[] c = cell(side, t, a);
                    markFlat(c[0], c[1], c[0], c[1]);
                }
        markFlat(-4, WALL, 4, HZ1 - 1);
        markFlat(-14, HZ1 - 2, 14, HZ2 + 2);
        // ancienne ville
        markFlat(-45, -42, -24, -6);       // eglise, parvis
        markFlat(-39, -46, -29, -40);      // abside
        markFlat(-19, -38, -10, -26);      // mausolee + sortie de la crypte
        markFlat(-16, -26, -13, -19);      // allee du cimetiere
        markFlat(-14, -32, -7, -29);
        markFlat(7, -44, 46, -6);          // supermarche + parking
        markFlat(6, 6, 46, 23);            // station-service
        markFlat(6, 24, 46, 26);           // ruelle
        markFlat(-31, 16, -19, 28);        // fontaine
        markFlat(-42, 11, -31, 19);        // etang
        markFlat(-18, 32, -10, 40);        // kiosque
        markFlat(-22, 10, -15, 18);        // aire de jeux
        // maisons de l'ancienne ville
        addLot(new Lot(-44, -15, -39, -7, Dir.SOUTH, HOUSE, 1, 2, true, 11));
        addLot(new Lot(-29, -16, -23, -7, Dir.SOUTH, HOUSE, 1, 0, false, 12));
        addLot(new Lot(-22, -14, -16, -7, Dir.SOUTH, SHOP, 2, 1, false, 13));
        addLot(new Lot(-13, -16, -7, -7, Dir.SOUTH, HOUSE, 1, 3, true, 14));
        addLot(new Lot(-43, 33, -33, 43, Dir.EAST, HOUSE, 2, 1, false, 15));
        addLot(new Lot(7, 28, 17, 37, Dir.NORTH, SHOP, 3, 1, false, 16));
        addLot(new Lot(20, 28, 29, 37, Dir.NORTH, HOUSE, 2, 5, false, 17));
        addLot(new Lot(32, 28, 43, 41, Dir.NORTH, HOUSE, 2, 4, false, 18));
        addLot(new Lot(21, 39, 28, 44, Dir.NORTH, HOUSE, 1, 2, true, 19));
        addLot(new Lot(29, 8, 38, 17, Dir.WEST, SHOP, 1, 0, false, 20));
        addLot(new Lot(39, 8, 45, 20, Dir.WEST, HOUSE, 1, 3, false, 21));
        // rangees du boulevard
        planRing();
        // coins
        markFlat(56, -75, 72, -60);        // entrepot
        markFlat(70, -62, 77, -54);        // chateau d'eau
        markFlat(-71, 65, -63, 72);        // cabane de la foret
    }

    void addLot(Lot l) {
        lots.add(l);
        markFlat(l.x1 - 1, l.z1 - 1, l.x2 + 1, l.z2 + 1);
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

    static final Dir[] SIDE_DOOR = {Dir.SOUTH, Dir.NORTH, Dir.EAST, Dir.WEST};

    Lot ringLot(int side, int t1, int t2, int a1, int a2, int kind, int storeys, boolean ruined) {
        int[] p = cell(side, t1, a1);
        int[] q = cell(side, t2, a2);
        int style = kind == SHOP ? rnd.nextInt(URBAN.length) : rnd.nextInt(HOUSES.length);
        Lot l = new Lot(p[0], p[1], q[0], q[1], SIDE_DOOR[side], kind, storeys, style, ruined, rnd.nextLong());
        l.side = side;
        l.t1 = t1;
        l.t2 = t2;
        l.a1 = a1;
        l.a2 = a2;
        return l;
    }

    int shopStoreys() {
        double r = rnd.nextDouble();
        return r < 0.3 ? 2 : r < 0.88 ? 3 : 4;
    }

    void planRing() {
        int[][] segs = {{-53, -7}, {7, 53}};
        for (int side = 0; side < 4; side++) {
            for (int[] seg : segs) {
                List<Lot> near = new ArrayList<>();
                int t = seg[0] + rnd.nextInt(2);
                while (t <= seg[1] - 6) {
                    double q = rnd.nextDouble();
                    int kind = q < 0.40 ? SHOP : q < 0.84 ? HOUSE : q < 0.95 ? SITE : YARD;
                    int w = kind == SHOP ? 9 + rnd.nextInt(4) : 9 + rnd.nextInt(4) - (rnd.nextDouble() < 0.2 ? 2 : 0);
                    if (t + w - 1 > seg[1]) {
                        w = seg[1] - t + 1;
                        if (w < 7) break;
                    }
                    int a1 = 56 + (kind != SHOP && rnd.nextDouble() < 0.3 ? 1 : 0);
                    int a2 = Math.min(64, a1 + 8 + rnd.nextInt(2) - 1);
                    int storeys = kind == SHOP ? shopStoreys() : (rnd.nextDouble() < 0.72 ? 2 : 1);
                    Lot l = ringLot(side, t, t + w - 1, a1, a2, kind, storeys, rnd.nextDouble() < 0.22);
                    l.row = 0;
                    near.add(l);
                    addLot(l);
                    double g = rnd.nextDouble();
                    int gap = g < 0.25 ? 0 : g < 0.40 ? 1 : g < 0.65 ? 2 : g < 0.9 ? 3 : 4;
                    t += w + gap;
                }
                // immeuble-porte de 4 etages a l'entree de chaque avenue (reperes secondaires, symetrie du plan)
                Lot gate = null;
                for (Lot n : near)
                    if (gate == null || Math.min(Math.abs(n.t1), Math.abs(n.t2)) < Math.min(Math.abs(gate.t1), Math.abs(gate.t2))) gate = n;
                if (gate != null && gate.t2 - gate.t1 >= 7) {
                    gate.kind = SHOP;
                    gate.storeys = 4;
                    gate.ruined = false;
                    gate.style = rnd.nextInt(URBAN.length);
                }
                // rangee du fond, en face (passerelles entre commerces de meme hauteur)
                for (Lot n : near) {
                    int ft1 = n.t1 + rnd.nextInt(2), ft2 = n.t2 - rnd.nextInt(2);
                    if (ft2 - ft1 + 1 < 6) continue;
                    double q = rnd.nextDouble();
                    int kind, storeys;
                    if (n.kind == SHOP && q < 0.6) {
                        kind = SHOP;
                        storeys = n.storeys;
                        ft1 = n.t1;
                        ft2 = n.t2;
                    } else {
                        double k = rnd.nextDouble();
                        kind = k < 0.58 ? HOUSE : k < 0.76 ? SHOP : k < 0.88 ? YARD : SITE;
                        storeys = kind == SHOP ? shopStoreys() : (rnd.nextDouble() < 0.65 ? 2 : 1);
                    }
                    int a2 = rnd.nextDouble() < 0.2 ? 74 : 75;
                    Lot l = ringLot(side, ft1, ft2, 68, Math.min(75, a2), kind, storeys, kind != YARD && rnd.nextDouble() < 0.22);
                    l.row = 1;
                    addLot(l);
                }
            }
        }
    }

    // ================================================================ terrain

    double amp(int x, int z) {
        int d = Math.max(Math.abs(x), Math.abs(z));
        if (d > WALL) return 2.5 + Math.min(7.5, (d - WALL) * 0.5);
        if (d >= 74) return 3.0;
        if (in(x, z, -43, 8, -8, 43)) return 2.8;
        if (in(x, z, -22, -42, -8, -20)) return 1.2;
        if (in(x, z, -76, 56, -56, 76)) return 3.4;
        if (in(x, z, -76, -76, -56, -56)) return 2.4;
        if (in(x, z, 56, 56, 76, 76)) return 2.4;
        if (in(x, z, 56, -76, 76, -56)) return 1.4;
        return d > 54 ? 2.0 : 1.6;
    }

    void terrain() {
        int[][] dist = new int[TNX][TNZ];
        for (int i = 0; i < TNX; i++)
            for (int k = 0; k < TNZ; k++) dist[i][k] = flat[i][k] ? 0 : 1000;
        for (int i = 0; i < TNX; i++)
            for (int k = 0; k < TNZ; k++) {
                int d = dist[i][k];
                if (i > 0) d = Math.min(d, dist[i - 1][k] + 3);
                if (k > 0) d = Math.min(d, dist[i][k - 1] + 3);
                if (i > 0 && k > 0) d = Math.min(d, dist[i - 1][k - 1] + 4);
                if (i < TNX - 1 && k > 0) d = Math.min(d, dist[i + 1][k - 1] + 4);
                dist[i][k] = d;
            }
        for (int i = TNX - 1; i >= 0; i--)
            for (int k = TNZ - 1; k >= 0; k--) {
                int d = dist[i][k];
                if (i < TNX - 1) d = Math.min(d, dist[i + 1][k] + 3);
                if (k < TNZ - 1) d = Math.min(d, dist[i][k + 1] + 3);
                if (i < TNX - 1 && k < TNZ - 1) d = Math.min(d, dist[i + 1][k + 1] + 4);
                if (i > 0 && k < TNZ - 1) d = Math.min(d, dist[i - 1][k + 1] + 4);
                dist[i][k] = d;
            }
        for (int i = 0; i < TNX; i++)
            for (int k = 0; k < TNZ; k++) {
                if (flat[i][k]) continue;
                int x = i + TX0, z = k + TZ0;
                double a = amp(x, z);
                double u = 0.5 + 0.55 * nz.fbm2(x / 19.0, z / 19.0, 3, 0.5, 2.0);
                u = Math.max(0, Math.min(1, u));
                if (Math.max(Math.abs(x), Math.abs(z)) > WALL) u = 0.35 + 0.65 * u;
                double bl = Math.min(1.0, dist[i][k] / 12.0);
                bl = bl * bl * (3 - 2 * bl);
                hg[i][k] = (int) Math.round(a * u * bl);
            }
        Palette grass = Palette.of(11, "grass_block", 70, "coarse_dirt", 12, "podzol", 10, "moss_block", 8).period(7);
        Palette junk = Palette.of(12, "gravel", 45, "coarse_dirt", 30, "mud", 15, "packed_mud", 10).period(5);
        Palette forest = Palette.of(13, "podzol", 45, "coarse_dirt", 30, "grass_block", 15, "rooted_dirt", 10).period(5);
        for (int x = -EXT; x <= EXT; x++)
            for (int z = -EXT; z <= HZ2 + 16; z++) {
                int hh = h(x, z);
                for (int y = G - 13; y <= G - 3; y++) s.set(x, y, z, "minecraft:stone");
                for (int y = G - 2; y < G + hh; y++) s.set(x, y, z, "minecraft:dirt");
                int y = G + hh;
                String top;
                boolean steep = Math.abs(h(x + 1, z) - hh) >= 2 || Math.abs(h(x, z + 1) - hh) >= 2
                        || Math.abs(h(x - 1, z) - hh) >= 2 || Math.abs(h(x, z - 1) - hh) >= 2;
                if (in(x, z, 56, 56, 77, 77)) top = junk.at(x, y, z);
                else if (in(x, z, -77, 56, -56, 77) || Math.max(Math.abs(x), Math.abs(z)) > WALL + 2) top = forest.at(x, y, z);
                else top = grass.at(x, y, z);
                if (steep) top = "minecraft:coarse_dirt";
                s.set(x, y, z, top);
            }
    }

    // ================================================================ rues

    final Palette asphaltP = Palette.of(21, "black_concrete", 78, "polished_blackstone", 14, "tuff", 8).period(6);
    final Noise crack = new Noise(31);

    String asphalt(int x, int z) {
        double c = crack.ridge2(x / 9.0, z / 9.0, 2, 0.5, 2.0);
        if (c > 0.93) return "minecraft:gravel";
        if (nz2.uniform2(x / 4.0, z / 4.0) > 0.975) return "minecraft:coarse_dirt";
        return asphaltP.at(x, G, z);
    }

    String sidewalk(int x, int z) {
        double c = crack.ridge2(x / 7.0 + 40, z / 7.0, 2, 0.5, 2.0);
        if (c > 0.94) return "minecraft:gravel";
        if (nz2.uniform2(x / 5.0 + 9, z / 5.0) > 0.93) return "minecraft:moss_block";
        return "minecraft:smooth_stone";
    }

    void roads() {
        // croix centrale : chaussee |b| <= 3, bordure 4, trottoir 5
        for (int a = -WALL; a <= WALL; a++)
            for (int b = -5; b <= 5; b++) {
                int ab = Math.abs(b);
                for (int pass = 0; pass < 2; pass++) {
                    int x = pass == 0 ? a : b, z = pass == 0 ? b : a;
                    if (pass == 0 && Math.abs(a) <= 5) continue;
                    s.set(x, G, z, ab <= 3 ? asphalt(x, z) : ab == 4 ? "minecraft:calcite" : sidewalk(x, z));
                }
            }
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) s.set(x, G, z, Math.abs(x) >= 4 && Math.abs(z) >= 4 ? sidewalk(x, z) : asphalt(x, z));
        // passages pietons
        for (int i = -3; i <= 3; i += 2) {
            fill(i, G, -5, i, G, -4, "minecraft:smooth_quartz");
            fill(i, G, 4, i, G, 5, "minecraft:smooth_quartz");
            fill(-5, G, i, -4, G, i, "minecraft:smooth_quartz");
            fill(4, G, i, 5, G, i, "minecraft:smooth_quartz");
        }
        // ligne jaune pointillee, effacee par endroits (bruit, pas bloc par bloc)
        for (int a = -WALL; a <= WALL; a++) {
            if (Math.abs(a) <= 6 || Math.floorMod(a, 4) >= 2 || nz2.uniform2(a / 11.0, 3.3) < 0.18) continue;
            if (Math.abs(a) < 47 || Math.abs(a) > 53) {
                s.set(a, G, 0, "minecraft:yellow_terracotta");
                s.set(0, G, a, "minecraft:yellow_terracotta");
            }
        }
        // route vers le hangar
        for (int z = WALL + 1; z < HZ1; z++)
            for (int x = -4; x <= 4; x++) s.set(x, G, z, Math.abs(x) == 4 ? "minecraft:coarse_dirt" : asphalt(x, z));
        // boulevard : chaussee 48..52, trottoirs 47 et 53..55
        for (int x = -55; x <= 55; x++)
            for (int z = -55; z <= 55; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d < 47) continue;
                boolean crossRoad = Math.min(Math.abs(x), Math.abs(z)) <= 3;
                if (d == 47 || d >= 53) {
                    if (!crossRoad) s.set(x, G, z, sidewalk(x, z));
                    continue;
                }
                int t = Math.abs(x) == d ? z : x;
                boolean line = d == 50 && Math.abs(t) < 47 && Math.floorMod(t, 4) < 2 && nz2.uniform2(t / 9.0, d) > 0.15;
                s.set(x, G, z, line ? "minecraft:yellow_terracotta" : asphalt(x, z));
            }
        // ruelles de service entre les deux rangees (65..67)
        Palette alley = Palette.of(22, "dirt_path", 45, "gravel", 25, "coarse_dirt", 18, "cobblestone", 12).period(4);
        for (int side = 0; side < 4; side++)
            for (int t = -55; t <= 55; t++) {
                if (Math.abs(t) <= 5) continue;
                for (int a = 65; a <= 67; a++) {
                    int[] c = cell(side, t, a);
                    s.set(c[0], G, c[1], alley.at(c[0], G, c[1]));
                }
            }
        // ruelle pavee au sud-est (z 24..26)
        Palette cob = Palette.of(23, "cobblestone", 60, "gravel", 20, "dirt_path", 12, "coarse_dirt", 8).period(4);
        for (int x = 6; x <= 46; x++)
            for (int z = 24; z <= 26; z++) s.set(x, G, z, cob.at(x, G, z));
        // parking du supermarche : places, ilots plantes
        for (int x = 7; x <= 44; x++)
            for (int z = -22; z <= -7; z++) {
                boolean line = (z <= -18 || z >= -11) && Math.floorMod(x - 9, 5) == 0;
                s.set(x, G, z, line ? "minecraft:smooth_quartz" : asphalt(x, z));
            }
        for (int ix : new int[]{9, 25, 41}) {
            fill(ix - 1, G, -16, ix + 1, G, -13, "minecraft:calcite");
            fill(ix, G, -15, ix, G, -14, "minecraft:coarse_dirt");
            deadTree(ix, G + 1, -15, 4);
            setIfAir(ix - 1, G + 1, -13, "minecraft:dead_bush");
        }
    }

    // ================================================================ batiments generiques

    void buildLot(Lot l) {
        switch (l.kind) {
            case HOUSE -> house(l);
            case SHOP -> shop(l);
            case SITE -> site(l);
            default -> yard(l);
        }
    }

    /** Maison : volume principal + porche / aile en L / annexe a terrasse, toit en pente avec debord, cheminee. */
    void house(Lot L) {
        Frame f = L.frame();
        HStyle hs = HOUSES[L.style];
        SplittableRandom r = new SplittableRandom(L.seed);
        int W = f.W, D = f.D;
        int storeys = (W < 9 || D < 8) ? 1 : L.storeys;
        int spacing = W >= 10 ? 5 : 4;
        Style st = hs.style(L.seed, spacing);
        RoofStyle rs = hs.roofStyle(spacing);
        int yBase = G + 1, yTop = yBase + 4 * storeys;
        int shape = 0;
        double q = r.nextDouble();
        if (D >= 10 && q < 0.35) shape = 1;
        else if (D >= 11 && W >= 9 && q < 0.65) shape = 2;
        else if (W >= 12 && D >= 8 && q < 0.85) shape = 3;
        if (shape == 3 && storeys == 2 && W < 14) storeys = 1;
        yTop = yBase + 4 * storeys;
        int mu0 = 0, mu1 = W - 1, mv0 = shape == 1 ? 2 : 0, mv1 = D - 1;
        boolean annexUp = r.nextBoolean();
        if (shape == 2) mv1 = Math.min(D - 5, mv0 + Math.max(6, (int) Math.round(D * 0.6)) - 1);
        if (shape == 3) {
            if (annexUp) mu1 = W - 6;
            else mu0 = 5;
        }
        int[] M = f.rect(mu0, mv0, mu1, mv1);
        int lenFront = f.door.axis() == 'z' ? M[2] - M[0] : M[3] - M[1];
        int doorT = Math.max(2, Math.min(lenFront - 2, lenFront / 2 + r.nextInt(3) - 1));
        Facade.building(s, st, M[0], M[1], M[2], M[3], yBase, yTop, f.door, doorT);
        buildings++;
        boolean ridgeU = (mu1 - mu0) >= (mv1 - mv0) ? r.nextDouble() < 0.8 : r.nextDouble() < 0.25;
        Roof.Part pm = r.nextDouble() < 0.2 ? Roof.hip(M[0], M[1], M[2], M[3], yTop) : gable(ridgeU ? f.up.axis() : f.in.axis(), M, yTop);
        int ridgeTop;
        // aile en L (a l'arriere, d'un cote)
        int[] Wg = null;
        int wu0 = 0, wu1 = 0;
        if (shape == 2) {
            int ww = Math.max(5, W / 2 + r.nextInt(2));
            boolean left = r.nextBoolean();
            wu0 = left ? 0 : W - ww;
            wu1 = left ? ww - 1 : W - 1;
            Wg = f.rect(wu0, mv1, wu1, D - 1);
            int wTop = yBase + 4;
            Facade.building(s, st, Wg[0], Wg[1], Wg[2], Wg[3], yBase, wTop, f.in, -1, f.door);
            if (wTop == yTop) {
                ridgeTop = Roof.build(s, rs, pm, gable(f.in.axis(), Wg, wTop));
            } else {
                ridgeTop = Roof.build(s, rs, pm);
                int[] wr = f.rect(wu0, mv1 + 1, wu1, D - 1);
                Roof.build(s, rs, gable(f.in.axis(), wr, wTop));
            }
            // passage entre la maison et l'aile
            int du = (wu0 + wu1) / 2;
            for (int v = mv1 - 2; v <= mv1 + 1; v++) {
                fill(f.x(du, v), yBase + 1, f.z(du, v), f.x(du, v), yBase + 2, f.z(du, v), "minecraft:air");
                keep(f.x(du, v), yBase + 1, f.z(du, v));
            }
        } else {
            ridgeTop = Roof.build(s, rs, pm);
        }
        // annexe a toit-terrasse
        if (shape == 3) {
            int au0 = annexUp ? mu1 : 0, au1 = annexUp ? W - 1 : mu0;
            int[] A = f.rect(au0, 1, au1, D - 2);
            int aTop = yBase + 4;
            Facade.building(s, st, A[0], A[1], A[2], A[3], yBase, aTop, f.door, -1, annexUp ? f.up.opposite() : f.up);
            String fence = B.id(hs.wood + "_fence");
            String deck = B.id(hs.wood + "_planks");
            int shared = annexUp ? au0 : au1;
            for (int u = au0; u <= au1; u++)
                for (int v = 1; v <= D - 2; v++) {
                    if (u == shared) continue;
                    boolean ring = u == au0 || u == au1 || v == 1 || v == D - 2;
                    if (ring) set(f.x(u, v), aTop + 1, f.z(u, v), fence);
                    else set(f.x(u, v), aTop, f.z(u, v), deck);
                }
            int vm = (1 + D - 2) / 2;
            int cu = annexUp ? mu1 : mu0, du = annexUp ? -1 : 1;
            for (int k = 0; k < 3; k++) {
                int x = f.x(cu + du * k, vm), z = f.z(cu + du * k, vm);
                fill(x, yBase + 1, z, x, yBase + 2, z, "minecraft:air");
                keep(x, yBase + 1, z);
                if (storeys == 2) {
                    fill(x, yBase + 5, z, x, yBase + 6, z, "minecraft:air");
                    keep(x, yBase + 5, z);
                }
            }
            // echelle vers la terrasse (face avant de l'annexe)
            int lu = annexUp ? au1 - 1 : au0 + 1;
            ladder(new int[]{f.x(lu, 1), f.z(lu, 1), f.door.ordinal(), aTop + 1, 1});
            buildings++;
        }
        // porche (balcon a l'etage pour une maison a 2 niveaux)
        int doorU = -1;
        for (int u = mu0; u <= mu1; u++)
            if (nm(f.x(u, mv0 + 1), yBase + 1, f.z(u, mv0 + 1)).endsWith("_door")) doorU = u;
        if (shape == 1) {
            String wood = hs.wood;
            int ry = storeys == 2 ? yBase + 4 : yBase + 3;
            for (int u = mu0; u <= mu1; u++)
                for (int v = 0; v <= 1; v++) {
                    int x = f.x(u, v), z = f.z(u, v);
                    set(x, G + 1, z, slab(wood + "_slab", false));
                    set(x, ry, z, slab(wood + "_slab", storeys == 2));
                }
            for (int u : new int[]{mu0, (mu0 + mu1) / 2 == doorU ? (mu0 + mu1) / 2 + 1 : (mu0 + mu1) / 2, mu1}) {
                int x = f.x(u, 0), z = f.z(u, 0);
                for (int y = G + 2; y < ry; y++) set(x, y, z, B.id(wood + "_fence"));
            }
            if (storeys == 2) {
                for (int u = mu0; u <= mu1; u++) set(f.x(u, 0), ry + 1, f.z(u, 0), B.id(wood + "_fence"));
                for (int v = 0; v <= 1; v++) {
                    set(f.x(mu0, v), ry + 1, f.z(mu0, v), B.id(wood + "_fence"));
                    set(f.x(mu1, v), ry + 1, f.z(mu1, v), B.id(wood + "_fence"));
                }
                if (doorU >= 0)
                    for (int v = mv0; v <= mv0 + 2; v++) {
                        int x = f.x(doorU, v), z = f.z(doorU, v);
                        fill(x, yBase + 5, z, x, yBase + 6, z, "minecraft:air");
                        keep(x, yBase + 5, z);
                    }
            } else {
                set(f.x((mu0 + mu1) / 2, 1), ry - 1, f.z((mu0 + mu1) / 2, 1), "minecraft:lantern[hanging=true]");
            }
        }
        // escalier interieur
        int iu0 = mu0 + 2, iu1 = mu1 - 2, iv0 = mv0 + 2, iv1 = mv1 - 2;
        int stairRow = -99;
        if (storeys == 2) {
            stairRow = iv1 - 1;
            boolean upDir = shape != 2 || (wu0 + wu1) / 2 > iu0 + 5 || (wu0 + wu1) / 2 < iu0;
            interiorStairs(f, iu0, iu1, stairRow, -1, yBase, upDir, hs.wood);
        }
        // meubles
        furnish(f, iu0, iu1, iv0, iv1, yBase + 1, hs.wood, false, stairRow, r);
        if (storeys == 2) furnish(f, iu0, iu1, iv0, iv1, yBase + 5, hs.wood, true, stairRow, r);
        // cheminee
        int cu = r.nextBoolean() ? mu0 + 1 : mu1 - 1;
        int cx = f.x(cu, mv1 - 1), cz = f.z(cu, mv1 - 1);
        if (ridgeTop > yTop) Roof.chimney(s, cx, cz, cx, cz, yTop + 1, ridgeTop + 1, Mat.of(B.id(hs.chimney)), null);
        int bx1 = Math.min(M[0], Wg == null ? M[0] : Wg[0]) - 2, bz1 = Math.min(M[1], Wg == null ? M[1] : Wg[1]) - 2;
        int bx2 = Math.max(M[2], Wg == null ? M[2] : Wg[2]) + 2, bz2 = Math.max(M[3], Wg == null ? M[3] : Wg[3]) + 2;
        bx1 = Math.min(bx1, L.x1 - 1);
        bz1 = Math.min(bz1, L.z1 - 1);
        bx2 = Math.max(bx2, L.x2 + 1);
        bz2 = Math.max(bz2, L.z2 + 1);
        weather(bx1, yBase, bz1, bx2, ridgeTop + 2, bz2, L.seed, L.ruined ? 0.45 : 0.22, L.ruined ? 0.25 : 0.12, "minecraft:" + hs.wood + "_planks");
        if (L.ruined) {
            ruin(M[0], yBase + 3, M[1], M[2], ridgeTop + 2, M[3], L.seed, 0.8);
        } else {
            // echelle vers le toit, sur un mur gouttereau (arriere si le faitage suit la rue)
            Dir side = ridgeU || pm.type == Roof.Type.HIP ? f.in : (r.nextBoolean() ? f.up : f.up.opposite());
            List<int[]> posts = wallPosts(M[0], M[1], M[2], M[3], side, spacing);
            if (!posts.isEmpty()) {
                List<int[]> opts = new ArrayList<>();
                for (int i = 0; i < posts.size(); i++) {
                    int[] p = posts.get((i + r.nextInt(posts.size())) % posts.size());
                    opts.add(new int[]{p[0], p[1], side.ordinal(), yTop + 1, 0});
                }
                ladders.add(opts.toArray(new int[0][]));
            }
        }
        Connect.fix(s, bx1, G + 1, bz1, bx2, ridgeTop + 2, bz2);
    }

    /** Cases a laisser libres (pieds/tete) des volees, revidees a la toute fin (arbres, decor poses apres). */
    private final Set<Long> clearCells = new HashSet<>();

    /**
     * Vide une case pour le passage. Si c'etait une vitre du plan P-2 (u = iu0 ou iu1), elle est reposee un plan plus
     * dehors (embrasure P-1, vide) : pas de trou dans la fenetre.
     */
    void clearPass(Frame f, int u, int v, int y, int iu0, int iu1) {
        int x = f.x(u, v), z = f.z(u, v);
        String st = s.get(x, y, z);
        if (!B.isAir(st)) {
            String n = B.name(st);
            if ((n.endsWith("_pane") || n.equals("iron_bars")) && (u == iu0 || u == iu1)) {
                int uo = u == iu0 ? u - 1 : u + 1;
                int ox = f.x(uo, v), oz = f.z(uo, v);
                if (air(ox, y, oz)) set(ox, y, oz, st);
            }
            set(x, y, z, "minecraft:air");
        }
        keep.add(key(x, y, z));
        clearCells.add(key(x, y, z));
    }

    /**
     * Escalier droit le long de u (storey 4) dans la rangee row, du rez-de-chaussee au 1er etage, tremie + garde-corps.
     * {@code inner} = cote de la piece (row + inner est a l'interieur) : garde-corps et abord lateral de ce cote.
     * 1.6.0 : la volee + l'arrivee tiennent dans l'interieur, en evitant si possible le plan P-2 (u = iu0 / iu1, vitres
     * et pilastres) : avant, une piece etroite envoyait la derniere marche contre le mur (il fallait sauter) et l'abord
     * tombait sur une vitre. Abord de face si la place le permet, sinon par le cote (on monte sur la moitie basse d'une
     * marche par le cote sans sauter). Tout est verifie par le test hors serveur (TestCity.flights).
     */
    void interiorStairs(Frame f, int iu0, int iu1, int row, int inner, int yFloor, boolean upDir, String wood) {
        int n = 4;
        int du = upDir ? 1 : -1;
        int lo = upDir ? iu0 : iu1;
        int span = iu1 - iu0;
        int o0;
        boolean front;
        if (span >= n + 3) {
            front = true;
            o0 = 2;
        } else if (span >= n + 2) {
            front = false;
            o0 = 1;
        } else if (span >= n) {
            front = false;
            o0 = 0;
        } else {
            return;
        }
        int u0 = lo + du * o0;
        Dir face = upDir ? f.up : f.up.opposite();
        String st = B.id(wood + "_stairs");
        for (int k = 0; k < n; k++) {
            int u = u0 + du * k;
            int x = f.x(u, row), z = f.z(u, row);
            set(x, yFloor + 1 + k, z, B.stairs(st, face, false));
            keep.add(key(x, yFloor + 1 + k, z));
            // 2 blocs libres au-dessus de chaque marche (tremie dans le plancher de l'etage)
            clearPass(f, u, row, yFloor + 2 + k, iu0, iu1);
            clearPass(f, u, row, yFloor + 3 + k, iu0, iu1);
            // passage d'une marche a la suivante : le joueur monte de 0,5 alors que sa boite est encore au-dessus de
            // cette marche (0,5 + 1,8 = 2,3) : un 3e bloc libre au-dessus de chaque marche sauf la derniere
            if (k < n - 1) clearPass(f, u, row, yFloor + 4 + k, iu0, iu1);
        }
        // abord : de face si possible, toujours par le cote interieur de la premiere marche
        if (front) {
            clearPass(f, u0 - du, row, yFloor + 1, iu0, iu1);
            clearPass(f, u0 - du, row, yFloor + 2, iu0, iu1);
            // tete en posant le pied sur la 1re marche (Esteban, 17/11)
            clearPass(f, u0 - du, row, yFloor + 3, iu0, iu1);
        }
        clearPass(f, u0, row + inner, yFloor + 1, iu0, iu1);
        clearPass(f, u0, row + inner, yFloor + 2, iu0, iu1);
        clearPass(f, u0, row + inner, yFloor + 3, iu0, iu1);
        // arrivee : plancher plein + 2 blocs libres
        int ue = u0 + du * n;
        int ex = f.x(ue, row), ez = f.z(ue, row);
        if (!B.isSolid(s.get(ex, yFloor + 4, ez))) set(ex, yFloor + 4, ez, "minecraft:" + wood + "_planks");
        keep.add(key(ex, yFloor + 4, ez));
        clearPass(f, ue, row, yFloor + 5, iu0, iu1);
        clearPass(f, ue, row, yFloor + 6, iu0, iu1);
        // garde-corps a l'etage, du cote de la piece, le long de la tremie
        int rail = row + inner;
        for (int k = 0; k < n - 1; k++) {
            int u = u0 + du * k;
            int x = f.x(u, rail), z = f.z(u, rail);
            if (air(x, yFloor + 5, z)) set(x, yFloor + 5, z, B.id(wood + "_fence"));
        }
        flights.add(new int[]{f.x(u0, row), yFloor + 1, f.z(u0, row), face.ordinal(), n});
    }

    /** Fin de construction : les passages des volees sont revides (arbres, decor ou gravats poses apres). */
    void clearFlights() {
        for (long k : clearCells) {
            int x = (int) (k >> 40) - 512, y = (int) ((k >> 20) & 0xFFFFF) - 512, z = (int) (k & 0xFFFFF) - 512;
            if (!air(x, y, z)) set(x, y, z, "minecraft:air");
        }
    }
    void furnF(Frame f, int u, int v, int y, String b) {
        furn(f.x(u, v), y, f.z(u, v), b);
    }

    void furnish(Frame f, int iu0, int iu1, int iv0, int iv1, int y, String wood, boolean upper, int stairRow, SplittableRandom r) {
        if (iu1 - iu0 < 2 || iv1 - iv0 < 1) return;
        int cu = (iu0 + iu1) / 2, cv = (iv0 + iv1) / 2;
        if (cv == stairRow) cv--;
        int back = iv1 == stairRow ? iv1 - 1 : iv1;
        if (back == stairRow) back = iv0;
        String toward = f.up.opposite().id();
        if (!upper) {
            furnF(f, cu, cv, y, wood + "_fence");
            furnF(f, cu, cv, y + 1, wood + "_pressure_plate");
            furnF(f, cu - 1, cv, y, stairs(wood + "_stairs", f.up.opposite(), false));
            furnF(f, cu + 1, cv, y, stairs(wood + "_stairs", f.up, false));
            if (back != stairRow) {
                furnF(f, iu1, back, y, "minecraft:furnace[facing=" + toward + "]");
                furnF(f, iu1 - 1, back, y, "minecraft:cauldron");
                furnF(f, iu0, back, y, "minecraft:barrel[facing=up]");
                furnF(f, iu0, back, y + 1, "minecraft:barrel[facing=up]");
            }
            furnF(f, iu1, iv0, y, "minecraft:bookshelf");
            furnF(f, iu1, iv0, y + 1, r.nextBoolean() ? "minecraft:bookshelf" : "minecraft:potted_dead_bush");
            furnF(f, iu0, iv0, y, "minecraft:crafting_table");
        } else {
            String[] beds = {"red", "blue", "white", "green", "brown", "gray"};
            String bed = beds[r.nextInt(beds.length)];
            int bv = back;
            int hx = f.x(iu1, bv), hz = f.z(iu1, bv), fx = f.x(iu1 - 1, bv), fz = f.z(iu1 - 1, bv);
            if (air(hx, y, hz) && air(fx, y, fz) && !kept(hx, y, hz) && !kept(fx, y, fz)) {
                set(hx, y, hz, "minecraft:" + bed + "_bed[facing=" + f.up.id() + ",part=head]");
                set(fx, y, fz, "minecraft:" + bed + "_bed[facing=" + f.up.id() + ",part=foot]");
            }
            furnF(f, iu0, bv, y, "minecraft:chest[facing=" + f.up.id() + "]");
            furnF(f, iu0, iv0, y, "minecraft:barrel[facing=up]");
            furnF(f, iu1, iv0, y, "minecraft:lectern[facing=" + toward + "]");
            String[] carpets = {"red", "gray", "brown", "cyan"};
            furnF(f, cu, cv, y, "minecraft:" + carpets[r.nextInt(carpets.length)] + "_carpet");
        }
        // fenetres eclairees (pas dans toutes les pieces)
        if (r.nextDouble() < 0.4) furnF(f, cu, cv, y + 2, "minecraft:lantern[hanging=true]");
        furnF(f, iu0, iv0, y + 2, "minecraft:cobweb");
        if (r.nextDouble() < 0.6) furnF(f, iu1, iv1, y + 2, "minecraft:cobweb");
        if (r.nextDouble() < 0.4) furnF(f, iu0 + r.nextInt(Math.max(1, iu1 - iu0)), iv0 + r.nextInt(Math.max(1, iv1 - iv0)), y,
                r.nextBoolean() ? "minecraft:cobweb" : "minecraft:composter");
    }

    /** Commerce / immeuble a toit plat : vitrine et auvent au rez-de-chaussee, escaliers interieurs, toit technique. */
    void shop(Lot L) {
        Frame f = L.frame();
        UStyle us = URBAN[L.style];
        SplittableRandom r = new SplittableRandom(L.seed);
        int W = f.W, D = f.D;
        int n = Math.max(1, L.storeys);
        Style st = us.style(L.seed);
        int yBase = G + 1, yTop = yBase + 4 * n;
        int[] R = f.rect(0, 0, W - 1, D - 1);
        int lenFront = f.door.axis() == 'z' ? R[2] - R[0] : R[3] - R[1];
        int doorT = Math.max(2, Math.min(lenFront - 2, lenFront / 2 + r.nextInt(3) - 1));
        Facade.building(s, st, R[0], R[1], R[2], R[3], yBase, yTop, f.door, doorT);
        buildings++;
        storefront(f, us, yBase, r, L.ruined, B.name(st.post));
        if (!L.ruined) {
            // acces au toit : escalier de secours sur un pignon, sinon echelle a l'arriere
            List<int[]> opts = new ArrayList<>();
            Dir[] sides = r.nextBoolean() ? new Dir[]{f.up, f.up.opposite()} : new Dir[]{f.up.opposite(), f.up};
            for (Dir sd : sides) {
                int[] best = null;
                int bv = -1;
                for (int[] p : wallPosts(R[0], R[1], R[2], R[3], sd, 4)) {
                    int v = f.v(p[0], p[1]);
                    if (v - 4 >= 1 && v > bv) {
                        bv = v;
                        best = p;
                    }
                }
                if (best != null && n >= 2) opts.add(new int[]{best[0], best[1], sd.ordinal(), yTop + 1, 4, f.door.ordinal(), yBase, n});
            }
            for (int[] p : wallPosts(R[0], R[1], R[2], R[3], f.in, 4)) opts.add(new int[]{p[0], p[1], f.in.ordinal(), yTop + 1, 3});
            for (Dir sd : sides)
                for (int[] p : wallPosts(R[0], R[1], R[2], R[3], sd, 4)) opts.add(new int[]{p[0], p[1], sd.ordinal(), yTop + 1, 3});
            ladders.add(opts.toArray(new int[0][]));
        }
        Roof.flat(s, R[0], R[1], R[2], R[3], yTop, us.roofSurface, us.wall, B.id(us.coping), 1, true);
        L.roofY = yTop;
        roofExtras(f, R, yTop, r);
        // escaliers interieurs, d'un cote puis de l'autre
        int iu0 = 2, iu1 = W - 3, iv0 = 2, iv1 = D - 3;
        if (W >= 9 && D >= 8)
            for (int k = 0; k < n - 1; k++) {
                int row = k % 2 == 0 ? iv1 - 1 : iv0 + 1;
                interiorStairs(f, iu0, iu1, row, k % 2 == 0 ? -1 : 1, yBase + 4 * k, k % 2 == 0, us.wood);
            }
        // rez-de-chaussee : comptoir, rayons
        int y = yBase + 1;
        int cv = iv0 + 1;
        for (int u = iu0; u <= iu1; u++) {
            int x = f.x(u, iv1), z = f.z(u, iv1);
            if (r.nextDouble() < 0.75) furn(x, y, z, r.nextBoolean() ? "minecraft:bookshelf" : "minecraft:barrel[facing=up]");
        }
        for (int u = iu0; u <= Math.min(iu1, iu0 + 2); u++) furnF(f, u, cv + 1, y, "minecraft:smooth_quartz");
        furnF(f, iu0 + 1, cv + 1, y + 1, "minecraft:quartz_slab");
        for (int k = 1; k < n; k++) furnish(f, iu0, iu1, iv0, iv1, yBase + 1 + 4 * k, us.wood, k % 2 == 1, k % 2 == 0 ? iv1 - 1 : iv0 + 1, r);
        if (r.nextDouble() < 0.5) furnF(f, (iu0 + iu1) / 2, (iv0 + iv1) / 2, y + 2, "minecraft:lantern[hanging=true]");
        weather(R[0] - 1, yBase, R[1] - 1, R[2] + 1, yTop + 3, R[3] + 1, L.seed, L.ruined ? 0.45 : 0.25, L.ruined ? 0.25 : 0.12,
                "minecraft:" + us.wood + "_planks");
        if (L.ruined) {
            ruin(R[0], yBase + 3, R[1], R[2], yTop + 3, R[3], L.seed, 0.7);
            L.roofY = -1;
        }
        Connect.fix(s, R[0] - 2, G + 1, R[1] - 2, R[2] + 2, yTop + 4, R[3] + 2);
    }

    /** Vitrine (retrait d'un bloc derriere les poteaux), auvent colore sur le trottoir. */
    void storefront(Frame f, UStyle us, int yBase, SplittableRandom r, boolean ruined, String postName) {
        int W = f.W;
        String paneAlong = f.up.axis() == 'x' ? B.pane("minecraft:glass_pane", false, true, false, true) : B.pane("minecraft:glass_pane", true, false, true, false);
        long seed = r.nextLong();
        for (int u = 1; u <= W - 2; u++) {
            int px = f.x(u, 0), pz = f.z(u, 0);
            String pn = nm(px, yBase + 2, pz);
            if (pn.equals(postName)) continue;
            if (nm(f.x(u, 1), yBase + 1, f.z(u, 1)).endsWith("_door")) continue;
            if (u > 1 && nm(f.x(u - 1, 1), yBase + 1, f.z(u - 1, 1)).endsWith("_door")) continue;
            if (u < W - 2 && nm(f.x(u + 1, 1), yBase + 1, f.z(u + 1, 1)).endsWith("_door")) continue;
            double hsh = Noise.hash(seed, u / 2, 0, 0);
            for (int y = yBase + 1; y <= yBase + 3; y++) set(px, y, pz, "minecraft:air");
            for (int y = yBase + 2; y <= yBase + 3; y++) {
                String g = hsh < (ruined ? 0.5 : 0.3) ? "minecraft:air" : hsh < (ruined ? 0.7 : 0.42) ? "minecraft:" + us.wood + "_planks" : paneAlong;
                set(f.x(u, 1), y, f.z(u, 1), g);
                set(f.x(u, 2), y, f.z(u, 2), "minecraft:air");
            }
        }
        // auvent
        for (int u = 0; u < W; u++) {
            int x = f.x(u, -1), z = f.z(u, -1);
            if (ruined && Noise.hash(seed, u / 3, 1, 0) < 0.45) continue;
            if (!air(x, yBase + 3, z)) continue;
            set(x, yBase + 3, z, us.awning);
            if (u % 2 == 0) set(x, yBase + 4, z, "minecraft:white_carpet");
        }
    }

    /** Toit technique : caissons, citerne, antenne, verriere. */
    void roofExtras(Frame f, int[] R, int yTop, SplittableRandom r) {
        int W = f.W, D = f.D;
        if (W >= 9 && D >= 8 && r.nextDouble() < 0.45) {
            int cu = W - 4, cv = 3;
            int x = f.x(cu, cv), z = f.z(cu, cv);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1) set(x + dx, yTop + 1, z + dz, "minecraft:spruce_fence");
            Shapes.cylinder(s, x, z, yTop + 2, yTop + 4, 1.6, Mat.of("minecraft:spruce_planks"), false);
            Shapes.cylinder(s, x, z, yTop + 3, yTop + 3, 1.6, Mat.of("minecraft:stripped_spruce_wood"), true);
            set(x, yTop + 5, z, "minecraft:spruce_slab[type=bottom]");
        }
        if (r.nextDouble() < 0.5) {
            int x = f.x(2, D - 3), z = f.z(2, D - 3);
            fill(x, yTop + 1, z, x, yTop + 3, z, "minecraft:iron_bars");
            set(x, yTop + 4, z, "minecraft:lightning_rod");
        }
        if (W >= 10 && D >= 9 && r.nextDouble() < 0.4) {
            int x = f.x(W / 2, D / 2), z = f.z(W / 2, D / 2);
            if (!air(x, yTop, z)) fill(x - 1, yTop, z - 1, x, yTop, z, "minecraft:glass");
        }
    }

    /** Vitres : cassees ou condamnees par fenetre (hachage par groupe de cases, jamais bloc par bloc). */
    void weather(int x1, int y1, int z1, int x2, int y2, int z2, long seed, double broken, double boarded, String board) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    String n = nm(x, y, z);
                    if (!n.equals("glass_pane")) continue;
                    double hsh = Noise.hash(seed, x >> 1, (y - G) / 4, z >> 1);
                    if (hsh < broken) set(x, y, z, "minecraft:air");
                    else if (hsh < broken + boarded) set(x, y, z, board);
                }
    }

    static String slabOf(String n) {
        return switch (n) {
            case "bricks" -> "brick_slab";
            case "stone_bricks" -> "stone_brick_slab";
            case "mossy_stone_bricks" -> "mossy_stone_brick_slab";
            case "mud_bricks" -> "mud_brick_slab";
            case "cobblestone" -> "cobblestone_slab";
            case "deepslate_bricks" -> "deepslate_brick_slab";
            case "deepslate_tiles" -> "deepslate_tile_slab";
            case "smooth_sandstone" -> "smooth_sandstone_slab";
            case "sandstone" -> "sandstone_slab";
            case "smooth_stone" -> "smooth_stone_slab";
            case "tuff_bricks" -> "tuff_brick_slab";
            case "cobbled_deepslate" -> "cobbled_deepslate_slab";
            default -> n.endsWith("_planks") ? n.replace("_planks", "_slab") : null;
        };
    }

    /**
     * Ruine : retrait par masque bruite, plus fort en haut et vers un coin effondre ; bords casses en dalles ; gravats
     * au pied, proportionnels au volume retire.
     */
    void ruin(int x1, int y1, int z1, int x2, int y2, int z2, long seed, double amount) {
        Noise rn = new Noise(seed ^ 0x9E37L);
        SplittableRandom r = new SplittableRandom(seed);
        int cx = r.nextBoolean() ? x1 : x2, cz = r.nextBoolean() ? z1 : z2;
        double diag = Math.hypot(x2 - x1, z2 - z1) + 1;
        int removed = 0;
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++)
                for (int y = y1; y <= y2; y++) {
                    // 1.6.0 : passages gardes (volees, abords, arrivees, portes) jamais casses par la ruine
                    if (air(x, y, z) || kept(x, y, z)) continue;
                    double hy = (y - y1 + 1.0) / (y2 - y1 + 1);
                    double dc = 1 - Math.hypot(x - cx, z - cz) / diag;
                    double v = 0.5 * rn.uniform3(x / 3.5, y / 3.5, z / 3.5) + 0.35 * hy + 0.5 * dc * amount;
                    if (v > 0.82) {
                        set(x, y, z, "minecraft:air");
                        removed++;
                    }
                }
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++)
                for (int y = y1; y <= y2; y++) {
                    String n = nm(x, y, z);
                    String sl = slabOf(n);
                    if (sl != null && !kept(x, y, z) && air(x, y + 1, z) && rn.uniform3(x / 2.0 + 30, y / 2.0, z / 2.0) > 0.5) set(x, y, z, slab(sl, false));
                }
        Palette rubble = Palette.of(seed, "cobblestone", 45, "gravel", 30, "mossy_cobblestone", 15, "coarse_dirt", 10).period(3);
        int nb = removed / 6;
        for (int k = 0; k < nb; k++) {
            int x = cx + (cx == x1 ? 1 : -1) * (r.nextInt(Math.max(1, (x2 - x1) / 2 + 3)) - 2);
            int z = cz + (cz == z1 ? 1 : -1) * (r.nextInt(Math.max(1, (z2 - z1) / 2 + 3)) - 2);
            int y = y2;
            while (y > G - 1 && air(x, y, z)) y--;
            if (y < G || y > y2 - 1 || kept(x, y + 1, z) || !air(x, y + 1, z) || !air(x, y + 2, z)) continue;
            String below = nm(x, y, z);
            if (below.contains("slab") || below.contains("stairs") || below.contains("fence") || below.contains("pane")) continue;
            if (r.nextDouble() < 0.3) set(x, y + 1, z, slab("cobblestone_slab", false));
            else set(x, y + 1, z, rubble.at(x, y + 1, z));
        }
    }

    /** Chantier : dalle, poteaux de beton, plancher partiel a l'etage, echafaudage escaladable, palissade. */
    void site(Lot L) {
        Frame f = L.frame();
        SplittableRandom r = new SplittableRandom(L.seed);
        int W = f.W, D = f.D;
        Palette slabP = Palette.of(L.seed, "gray_concrete", 85, "cyan_terracotta", 15).period(5);
        for (int u = 0; u < W; u++)
            for (int v = 0; v < D; v++) set(f.x(u, v), G, f.z(u, v), slabP);
        Noise hole = new Noise(L.seed);
        for (int u = 1; u < W - 1; u++)
            for (int v = 1; v < D - 1; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                boolean col = (u - 1) % 4 == 0 && (v - 1) % 3 == 0;
                if (col) fill(x, G + 1, z, x, G + 4, z, "minecraft:polished_andesite");
                if (hole.uniform2(u / 3.0, v / 3.0) > 0.32 || col) set(x, G + 5, z, "minecraft:smooth_stone");
                if (col && r.nextDouble() < 0.6) {
                    fill(x, G + 6, z, x, G + 7, z, "minecraft:polished_andesite");
                    set(x, G + 8, z, "minecraft:iron_bars");
                }
            }
        // echafaudage (on y grimpe) et echelle
        int sx = f.x(W - 1, 1), sz = f.z(W - 1, 1);
        fill(sx, G + 1, sz, sx, G + 6, sz, "minecraft:scaffolding[distance=0,bottom=false]");
        ladder(new int[]{f.x(1, D - 1), f.z(1, D - 1), f.in.ordinal(), G + 5, 2});
        // palissade avant avec entree
        for (int u = 0; u < W; u++) {
            if (Math.abs(u - W / 2) <= 1) continue;
            int x = f.x(u, -1), z = f.z(u, -1);
            if (!air(x, G + 1, z)) continue;
            set(x, G + 1, z, "minecraft:white_concrete");
            if (Noise.hash(L.seed, u / 2, 0, 0) > 0.3) set(x, G + 2, z, u % 3 == 0 ? "minecraft:stripped_spruce_log" : "minecraft:white_concrete");
        }
        // materiaux
        int mx = f.x(2, D - 2), mz = f.z(2, D - 2);
        fill(mx, G + 1, mz, mx, G + 2, mz, "minecraft:bricks");
        furn(mx + f.up.dx, G + 1, mz + f.up.dz, "minecraft:spruce_slab[type=bottom]");
        furn(f.x(W - 3, D - 2), G + 1, f.z(W - 3, D - 2), "minecraft:cauldron");
        furn(f.x(W - 3, D - 3), G + 1, f.z(W - 3, D - 3), "minecraft:packed_mud");
        buildings++;
        Connect.fix(s, L.x1 - 2, G + 1, L.z1 - 2, L.x2 + 2, G + 9, L.z2 + 2);
    }

    /** Cour : cloture cassee, arbre, potager, cabane a outils. */
    void yard(Lot L) {
        Frame f = L.frame();
        SplittableRandom r = new SplittableRandom(L.seed);
        int W = f.W, D = f.D;
        for (int u = 0; u < W; u++)
            for (int v = 0; v < D; v++) {
                boolean edge = u == 0 || u == W - 1 || v == 0 || v == D - 1;
                int x = f.x(u, v), z = f.z(u, v);
                if (edge && Noise.hash(L.seed, u / 2, v / 2, 3) > 0.35 && air(x, top(x, z) + 1, z))
                    set(x, top(x, z) + 1, z, "minecraft:spruce_fence");
            }
        int cx = f.x(W / 2, D / 2), cz = f.z(W / 2, D / 2);
        if (r.nextBoolean()) deadTree(cx, top(cx, cz) + 1, cz, 4 + r.nextInt(3));
        else new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(5 + r.nextInt(2)).crown(2.4)
                .place(s, cx, top(cx, cz) + 1, cz, L.seed);
        // potager seche
        for (int u = 1; u <= Math.min(4, W - 2); u++)
            for (int v = 1; v <= 2; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                if (h(x, z) == 0 && air(x, G + 1, z)) {
                    set(x, G, z, "minecraft:coarse_dirt");
                    if ((u + v) % 2 == 0) set(x, G + 1, z, "minecraft:dead_bush");
                }
            }
        // cabane a outils 3x3
        if (W >= 8 && D >= 6) {
            int u0 = W - 4, v0 = D - 4;
            for (int u = u0; u <= u0 + 2; u++)
                for (int v = v0; v <= v0 + 2; v++) {
                    int x = f.x(u, v), z = f.z(u, v);
                    int yb = top(x, z) + 1;
                    boolean wall = u == u0 || u == u0 + 2 || v == v0 || v == v0 + 2;
                    if (wall && !(u == u0 + 1 && v == v0)) fill(x, yb, z, x, yb + 1, z, "minecraft:spruce_planks");
                    set(x, yb + 2, z, slab("spruce_slab", false));
                }
            furn(f.x(u0 + 1, v0 + 1), top(f.x(u0 + 1, v0 + 1), f.z(u0 + 1, v0 + 1)) + 1, f.z(u0 + 1, v0 + 1), "minecraft:barrel[facing=up]");
        }
        furn(f.x(1, D - 2), top(f.x(1, D - 2), f.z(1, D - 2)) + 1, f.z(1, D - 2), r.nextBoolean() ? "minecraft:composter" : "minecraft:hay_block");
        Connect.fix(s, L.x1 - 1, G + 1, L.z1 - 1, L.x2 + 1, G + 6, L.z2 + 1);
    }

    /** Echelles demandees par les batiments, posees a la fin (les voisins sont construits) si la place est libre. */
    void ladder(int[] opt) {
        ladders.add(new int[][]{opt});
    }

    int laddersMade, fireEscapes;

    /**
     * Options : {x, z du poteau (plan P), cote exterieur, y du haut, type[, vers la facade, yBase, etages]}.
     * Types : 0 toit en pente (remplace la case d'egout), 1 terrasse (ouvre la barriere), 2 chantier, 3 toit plat (ouvre
     * l'acrotere), 4 escalier de secours (echelle + paliers a chaque etage, portes vers les etages, puis toit plat).
     */
    void doLadders() {
        for (int[][] group : ladders)
            for (int[] l : group)
                if (tryLadder(l)) break;
        if (log != null) log.fine("Echelles : " + laddersMade + ", escaliers de secours : " + fireEscapes);
    }

    static boolean ladderPassable(String n) {
        return n.endsWith("_stairs") || n.endsWith("_slab") || n.endsWith("_carpet");
    }

    boolean tryLadder(int[] l) {
        Dir out = Dir.values()[l[2]];
        int px = l[0], pz = l[1], yTo = l[3], kind = l[4];
        int lx = px + out.dx, lz = pz + out.dz;
        int y0 = top(lx, lz) + 1;
        if (y0 > yTo - 2 || kept(lx, y0, lz)) return false;
        for (int y = y0; y <= yTo; y++) {
            String st = s.get(lx, y, lz);
            if (!B.isAir(st) && !(y >= yTo - 1 && ladderPassable(B.name(st)))) return false;
        }
        if (!air(lx, yTo + 1, lz)) return false;
        Dir tw = null;
        int yBase = 0, n = 0;
        if (kind == 4) {
            tw = Dir.values()[l[5]];
            yBase = l[6];
            n = l[7];
            for (int k = 1; k < n; k++) {
                int y = yBase + 4 * k;
                for (int j = 1; j <= 4; j++)
                    for (int o = 1; o <= 2; o++) {
                        int x = px + tw.dx * j + out.dx * o, z = pz + tw.dz * j + out.dz * o;
                        for (int yy = y; yy <= y + 2; yy++) if (!air(x, yy, z)) return false;
                    }
            }
        }
        String ld = "minecraft:ladder[facing=" + out.id() + ",waterlogged=false]";
        for (int y = y0; y <= yTo; y++) {
            set(lx, y, lz, ld);
            keep(lx, y, lz);
        }
        keep(lx + out.dx, y0, lz + out.dz);
        if (kind == 1) {
            set(px, yTo, pz, "minecraft:air");
        } else if (kind == 3 || kind == 4) {
            set(px, yTo, pz, "minecraft:air");
            set(px, yTo + 1, pz, "minecraft:air");
        }
        if (kind == 4) {
            Dir in = out.opposite();
            for (int k = 1; k < n; k++) {
                int y = yBase + 4 * k;
                for (int j = 1; j <= 3; j++) {
                    for (int o = 1; o <= 2; o++) set(px + tw.dx * j + out.dx * o, y, pz + tw.dz * j + out.dz * o, "minecraft:smooth_stone_slab[type=top]");
                    set(px + tw.dx * j + out.dx * 2, y + 1, pz + tw.dz * j + out.dz * 2, "minecraft:iron_bars");
                }
                for (int o = 1; o <= 2; o++) set(px + tw.dx * 4 + out.dx * o, y + 1, pz + tw.dz * 4 + out.dz * o, "minecraft:iron_bars");
                set(px + out.dx * 2, y + 1, pz + out.dz * 2, "minecraft:iron_bars");
                // porte-fenetre vers l'etage
                int dx = px + tw.dx * 2, dz = pz + tw.dz * 2;
                for (int d = 0; d <= 2; d++) {
                    int x = dx + in.dx * d, z = dz + in.dz * d;
                    // 1.6.0 : ne pas creuser dans une volee d'escalier interieure (une marche disparaissait)
                    for (int yy = y + 1; yy <= y + 2; yy++) if (!Stairs.isStairsAt(s, x, yy, z)) set(x, yy, z, "minecraft:air");
                    keep(x, y + 1, z);
                }
                keep(dx + out.dx, y + 1, dz + out.dz);
            }
            // console sous les paliers
            for (int k = 1; k < n; k++) {
                int y = yBase + 4 * k - 1;
                set(px + tw.dx * 3 + out.dx * 2, y, pz + tw.dz * 3 + out.dz * 2, "minecraft:iron_bars");
            }
            fireEscapes++;
        }
        laddersMade++;
        return true;
    }

    // ================================================================ passerelles entre toits plats

    void bridges() {
        int made = 0;
        for (Lot a : lots) {
            if (a.side < 0 || a.kind != SHOP || a.roofY < 0) continue;
            for (Lot b : lots) {
                if (b == a || b.side != a.side || b.kind != SHOP || b.roofY != a.roofY) continue;
                // en face, par-dessus la ruelle
                if (a.row == 0 && b.row == 1) {
                    int t1 = Math.max(a.t1 + 1, b.t1 + 1), t2 = Math.min(a.t2 - 1, b.t2 - 1);
                    if (t2 - t1 < 1) continue;
                    int tc = (t1 + t2) / 2;
                    bridgeRing(a.side, tc, a.a2 + 1, b.a1 - 1, a.roofY, true);
                    made++;
                }
                // voisins de la meme rangee, par-dessus un passage
                if (a.row == b.row && b.t1 > a.t2 && b.t1 - a.t2 - 1 <= 4) {
                    int am1 = Math.max(a.a1 + 2, b.a1 + 2), am2 = Math.min(a.a2 - 2, b.a2 - 2);
                    if (am2 - am1 < 1) continue;
                    int ac = (am1 + am2) / 2;
                    if (b.t1 - a.t2 - 1 == 0) {
                        // murs mitoyens : on ouvre les acroteres
                        for (int aa = ac; aa <= ac + 1; aa++) {
                            int[] c1 = cell(a.side, a.t2, aa), c2 = cell(a.side, b.t1, aa);
                            fill(c1[0], a.roofY + 1, c1[1], c1[0], a.roofY + 2, c1[1], "minecraft:air");
                            fill(c2[0], a.roofY + 1, c2[1], c2[0], a.roofY + 2, c2[1], "minecraft:air");
                        }
                    } else {
                        bridgeAlong(a.side, a.t2 + 1, b.t1 - 1, ac, a.roofY);
                    }
                    made++;
                }
            }
        }
        if (log != null) log.fine("Passerelles : " + made);
    }

    /** Passerelle en planches perpendiculaire au cote (t, t+1), de a1 a a2, tablier en y (on marche en y + 1). */
    void bridgeRing(int side, int t, int aFrom, int aTo, int y, boolean openEnds) {
        for (int a = aFrom; a <= aTo; a++) {
            for (int tt = t; tt <= t + 1; tt++) {
                int[] c = cell(side, tt, a);
                set(c[0], y, c[1], "minecraft:spruce_slab[type=top]");
                set(c[0], y + 1, c[1], "minecraft:air");
                set(c[0], y + 2, c[1], "minecraft:air");
            }
            int[] l = cell(side, t - 1, a), rr = cell(side, t + 2, a);
            set(l[0], y + 1, l[1], "minecraft:spruce_fence");
            set(rr[0], y + 1, rr[1], "minecraft:spruce_fence");
            if ((a - aFrom) % 3 == 1) {
                set(l[0], y, l[1], "minecraft:stripped_spruce_log");
                set(rr[0], y, rr[1], "minecraft:stripped_spruce_log");
            }
        }
        if (openEnds)
            for (int tt = t; tt <= t + 1; tt++)
                for (int a : new int[]{aFrom - 1, aTo + 1}) {
                    int[] c = cell(side, tt, a);
                    set(c[0], y + 1, c[1], "minecraft:air");
                    set(c[0], y + 2, c[1], "minecraft:air");
                }
        int[] c0 = cell(side, t - 1, aFrom), c1 = cell(side, t + 2, aTo);
        Connect.fix(s, Math.min(c0[0], c1[0]) - 1, y, Math.min(c0[1], c1[1]) - 1, Math.max(c0[0], c1[0]) + 1, y + 2, Math.max(c0[1], c1[1]) + 1);
    }

    void bridgeAlong(int side, int tFrom, int tTo, int a, int y) {
        for (int t = tFrom; t <= tTo; t++) {
            for (int aa = a; aa <= a + 1; aa++) {
                int[] c = cell(side, t, aa);
                set(c[0], y, c[1], "minecraft:spruce_slab[type=top]");
            }
            int[] l = cell(side, t, a - 1), rr = cell(side, t, a + 2);
            set(l[0], y + 1, l[1], "minecraft:spruce_fence");
            set(rr[0], y + 1, rr[1], "minecraft:spruce_fence");
        }
        for (int aa = a; aa <= a + 1; aa++)
            for (int t : new int[]{tFrom - 1, tTo + 1}) {
                int[] c = cell(side, t, aa);
                set(c[0], y + 1, c[1], "minecraft:air");
                set(c[0], y + 2, c[1], "minecraft:air");
            }
        int[] c0 = cell(side, tFrom - 1, a - 1), c1 = cell(side, tTo + 1, a + 2);
        Connect.fix(s, Math.min(c0[0], c1[0]) - 1, y, Math.min(c0[1], c1[1]) - 1, Math.max(c0[0], c1[0]) + 1, y + 2, Math.max(c0[1], c1[1]) + 1);
    }

    // ================================================================ parc (SO)

    void park() {
        // allees sinueuses (suivent le terrain)
        for (int a = 8; a <= 43; a++) {
            int cx = -25 + (int) Math.round(2.5 * Math.sin(a / 6.0));
            for (int dx = -1; dx <= 1; dx++) path(cx + dx, a);
            int cz = 22 + (int) Math.round(2.0 * Math.sin((a + 40) / 5.0));
            int x = -43 + (a - 8);
            for (int dz = -1; dz <= 1; dz++) path(x, cz + dz);
        }
        // fontaine
        int fx = -25, fz = 22;
        for (int x = fx - 4; x <= fx + 4; x++)
            for (int z = fz - 4; z <= fz + 4; z++) {
                double d = Math.hypot(x - fx, z - fz);
                if (d <= 2.6) {
                    set(x, G - 1, z, "minecraft:stone_bricks");
                    set(x, G, z, "minecraft:water");
                } else if (d <= 3.6) {
                    set(x, G, z, "minecraft:stone_bricks");
                    Dir o = Math.abs(x - fx) >= Math.abs(z - fz) ? (x > fx ? Dir.WEST : Dir.EAST) : (z > fz ? Dir.NORTH : Dir.SOUTH);
                    set(x, G + 1, z, stairs("stone_brick_stairs", o, false));
                } else if (d <= 4.6) {
                    set(x, G, z, "minecraft:polished_andesite");
                }
            }
        fill(fx, G, fz, fx, G + 2, fz, "minecraft:chiseled_stone_bricks");
        set(fx, G + 3, fz, "minecraft:stone_brick_wall");
        set(fx, G + 4, fz, "minecraft:soul_lantern");
        Stairs.fix(s, fx - 4, G + 1, fz - 4, fx + 4, G + 1, fz + 4);
        // etang (creux dans le terrain)
        for (int x = -42; x <= -31; x++)
            for (int z = 11; z <= 19; z++) {
                double dx = (x + 36.5) / 4.8, dz = (z - 15) / 3.9;
                double d = dx * dx + dz * dz + 0.25 * nz2.noise2(x / 3.0, z / 3.0);
                if (d <= 1.0) {
                    set(x, G - 1, z, "minecraft:mud");
                    set(x, G, z, "minecraft:water");
                    if (nz2.uniform2(x / 2.0, z / 2.0) > 0.85) set(x, G + 1, z, "minecraft:lily_pad");
                } else if (d <= 1.5) {
                    set(x, G, z, nz2.uniform2(x / 2.5, z / 2.5) > 0.5 ? "minecraft:mud" : "minecraft:gravel");
                    if (nz2.uniform2(x / 2.0 + 7, z / 2.0) > 0.7 && air(x, G + 1, z) && air(x, G + 2, z)) {
                        set(x, G + 1, z, "minecraft:tall_grass[half=lower]");
                        set(x, G + 2, z, "minecraft:tall_grass[half=upper]");
                    }
                }
            }
        // haies (bordure nord et est, avec des passages)
        for (int x = -43; x <= -8; x++) {
            if (Math.abs(x + 25) <= 3 || nz2.uniform2(x / 4.0, 8.5) < 0.25) continue;
            int y = top(x, 8) + 1;
            fill(x, y, 8, x, y + 1, 8, "minecraft:azalea_leaves[persistent=true]");
        }
        for (int z = 9; z <= 43; z++) {
            if (Math.abs(z - 22) <= 3 || nz2.uniform2(-8.5, z / 4.0) < 0.25) continue;
            int y = top(-8, z) + 1;
            fill(-8, y, z, -8, y + 1, z, "minecraft:azalea_leaves[persistent=true]");
        }
        // arbres en bosquets
        int[][] oaks = {{-38, 28}, {-36, 31}, {-29, 36}, {-17, 29}, {-12, 20}, {-21, 41}, {-30, 41}, {-11, 41}, {-40, 23}};
        long sd = 500;
        for (int[] o : oaks) {
            int y = top(o[0], o[1]) + 1;
            if (air(o[0], y, o[1]))
                new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:dark_oak_leaves").height(6 + (int) (sd % 3)).crown(2.8)
                        .place(s, o[0], y, o[1], sd);
            sd += 37;
        }
        new Tree("minecraft:dark_oak_log", "minecraft:dark_oak_leaves", "minecraft:oak_leaves").height(9).crown(3.6).place(s, -34, top(-34, 24) + 1, 24, 911);
        new Tree("minecraft:dark_oak_log", "minecraft:dark_oak_leaves", "minecraft:azalea_leaves").height(8).crown(3.3).place(s, -19, top(-19, 35) + 1, 35, 912);
        deadTree(-10, top(-10, 11) + 1, 11, 6);
        deadTree(-42, top(-42, 21) + 1, 21, 5);
        // bancs
        int[][] benches = {{-30, 19, 0}, {-19, 19, 0}, {-30, 25, 1}, {-19, 25, 1}, {-28, 13, 2}, {-22, 31, 3}};
        for (int[] b : benches) {
            Dir face = b[2] == 0 ? Dir.NORTH : b[2] == 1 ? Dir.SOUTH : b[2] == 2 ? Dir.WEST : Dir.EAST;
            boolean alongX = b[2] < 2;
            for (int i = -1; i <= 1; i++) {
                int x = alongX ? b[0] + i : b[0], z = alongX ? b[1] : b[1] + i;
                setIfAir(x, top(x, z) + 1, z, stairs("spruce_stairs", face, false));
            }
        }
        // kiosque a toit en croupe, echelle vers le toit
        int gx = -14, gz = 36;
        fill(gx - 3, G, gz - 3, gx + 3, G, gz + 3, "minecraft:spruce_planks");
        for (int x = gx - 3; x <= gx + 3; x++)
            for (int z = gz - 3; z <= gz + 3; z++) {
                boolean corner = Math.abs(x - gx) == 3 && Math.abs(z - gz) == 3;
                boolean edge = Math.abs(x - gx) == 3 || Math.abs(z - gz) == 3;
                if (corner) fill(x, G + 1, z, x, G + 3, z, "minecraft:stripped_spruce_log");
                else if (edge && Math.abs(x - gx) != 0 && Math.abs(z - gz) != 0) set(x, G + 1, z, "minecraft:spruce_fence");
            }
        fill(gx - 2, G + 4, gz - 2, gx + 2, G + 4, gz + 2, "minecraft:dark_oak_planks");
        Roof.build(s, new RoofStyle().stairs(Palette.of(5, "dark_oak_stairs", 60, "spruce_stairs", 40)).ridge("minecraft:dark_oak_slab")
                .body(Mat.of("minecraft:dark_oak_planks")).gablePost(null).overhang(1), Roof.hip(gx - 3, gz - 3, gx + 3, gz + 3, G + 4));
        set(gx, G + 3, gz, "minecraft:lantern[hanging=true]");
        fill(gx - 4, G + 1, gz, gx - 4, G + 5, gz, "minecraft:ladder[facing=west]");
        Connect.fix(s, gx - 4, G + 1, gz - 4, gx + 4, G + 2, gz + 4);
        // aire de jeux : toboggan, balancoire
        fill(-20, G + 1, 11, -19, G + 2, 12, "minecraft:oak_fence");
        fill(-20, G + 3, 11, -19, G + 3, 12, "minecraft:oak_planks");
        fill(-21, G + 1, 11, -21, G + 3, 11, "minecraft:ladder[facing=west]");
        set(-18, G + 2, 11, "minecraft:quartz_stairs[facing=west]");
        set(-18, G + 2, 12, "minecraft:quartz_stairs[facing=west]");
        set(-17, G + 1, 11, "minecraft:quartz_stairs[facing=west]");
        set(-17, G + 1, 12, "minecraft:quartz_stairs[facing=west]");
        set(-16, G + 1, 11, "minecraft:quartz_slab");
        set(-16, G + 1, 12, "minecraft:quartz_slab");
        fill(-21, G + 1, 16, -21, G + 3, 16, "minecraft:spruce_fence");
        fill(-17, G + 1, 16, -17, G + 3, 16, "minecraft:spruce_fence");
        fill(-20, G + 3, 16, -18, G + 3, 16, "minecraft:spruce_fence[east=true,west=true]");
        set(-19, G + 2, 16, "minecraft:iron_chain");
        set(-19, G + 1, 16, "minecraft:spruce_slab[type=top]");
        Connect.fix(s, -22, G + 1, 10, -15, G + 3, 17);
    }

    void path(int x, int z) {
        if (!in(x, z, -43, 8, -8, 43)) return;
        int y = top(x, z);
        String n = nm(x, y, z);
        if (n.equals("water") || n.contains("stone")) return;
        s.set(x, y, z, nz2.uniform2(x / 3.0, z / 3.0) > 0.25 ? "minecraft:dirt_path" : "minecraft:coarse_dirt");
    }

    /** Arbre mort : tronc qui se decale, branches en buches et barrieres. */
    void deadTree(int x, int y, int z, int hgt) {
        int cx = x, cz = z;
        long sd = key(x, y, z);
        for (int i = 0; i < hgt; i++) {
            set(cx, y + i, cz, "minecraft:stripped_spruce_log");
            if (i == 2 && (sd & 1) == 0) cx += (sd & 2) == 0 ? 1 : -1;
        }
        int ty = y + hgt - 1;
        set(cx + 1, ty - 1, cz, "minecraft:spruce_log[axis=x]");
        setIfAir(cx + 2, ty, cz, "minecraft:spruce_fence");
        set(cx, ty - 2, cz - 1, "minecraft:spruce_log[axis=z]");
        setIfAir(cx, ty - 1, cz - 2, "minecraft:spruce_fence");
        setIfAir(cx - 1, ty + 1, cz, "minecraft:spruce_fence");
        if ((sd & 4) == 0) setIfAir(cx, ty + 1, cz + 1, "minecraft:spruce_fence");
    }

    // ================================================================ eglise (NO), repere de la ville

    void church() {
        Style st = new Style().wall(Palette.of(401, "stone_bricks", 82, "mossy_stone_bricks", 18).period(11).amp(0.2))
                .plinth(Mat.of("minecraft:cobbled_deepslate")).plinthStairs("minecraft:cobbled_deepslate_stairs").post("minecraft:stripped_dark_oak_log")
                .corner(Mat.of("minecraft:chiseled_stone_bricks")).pane("minecraft:purple_stained_glass_pane").sill("minecraft:stone_brick_stairs")
                .lintel("minecraft:stone_brick_slab").shutter(null).band("minecraft:stone_brick_stairs").cornice("minecraft:stone_brick_stairs")
                .door("minecraft:dark_oak_door").spacing(4).storey(7).seed(41).blankBays(0.0);
        int yb = G + 1;
        // nef (x -39..-29, z -40..-17)
        int nx1 = -39, nx2 = -29, nz1 = -40, nz2 = -17, nTop = yb + 14;
        fill(nx1 + 1, yb + 1, nz1 + 1, nx2 - 1, nTop, nz2 - 1, Mat.AIR);
        fill(nx1, yb, nz1, nx2, yb, nz2, "minecraft:polished_andesite");
        Facade.wall(s, st, nx1, nz1, Dir.EAST, nx2 - nx1, Dir.NORTH, yb, nTop, -1);
        Facade.wall(s, st, nx1, nz2, Dir.EAST, nx2 - nx1, Dir.SOUTH, yb, nTop, -1);
        Facade.wall(s, st, nx1, nz1, Dir.SOUTH, nz2 - nz1, Dir.WEST, yb, nTop, -1);
        Facade.wall(s, st, nx2, nz1, Dir.SOUTH, nz2 - nz1, Dir.EAST, yb, nTop, -1);
        // bas-cotes (z -38..-19), plus bas
        Style sa = new Style().wall(st.wall).plinth(st.plinth).plinthStairs(st.plinthStairs).post(st.post).corner(st.corner).pane(st.pane)
                .sill(st.sillStairs).lintel(st.lintelSlab).shutter(null).band(null).cornice(st.corniceStairs).door(st.door).spacing(4).storey(6)
                .seed(42).blankBays(0.0);
        int aTop = yb + 6, az1 = -38, az2 = -19;
        for (int side = 0; side < 2; side++) {
            int ox = side == 0 ? -43 : -25, ix = side == 0 ? nx1 : nx2;
            int x1 = Math.min(ox, ix), x2 = Math.max(ox, ix);
            fill(x1 + 1, yb + 1, az1 + 1, x2 - 1, aTop + 4, az2 - 1, Mat.AIR);
            fill(x1, yb, az1, x2, yb, az2, "minecraft:polished_andesite");
            Dir out = side == 0 ? Dir.WEST : Dir.EAST;
            Facade.wall(s, sa, ox, az1, Dir.SOUTH, az2 - az1, out, yb, aTop, side == 1 ? 5 : -1);
            Facade.wall(s, sa, x1, az1, Dir.EAST, x2 - x1, Dir.NORTH, yb, aTop, -1);
            Facade.wall(s, sa, x1, az2, Dir.EAST, x2 - x1, Dir.SOUTH, yb, aTop, side == 0 ? 2 : -1);
            // arcades : on ouvre le mur de la nef entre les poteaux
            List<int[]> posts = wallPosts(nx1, nz1, nx2, nz2, side == 0 ? Dir.WEST : Dir.EAST, 4);
            for (int z = az1 + 1; z <= az2 - 1; z++) {
                boolean post = false;
                for (int[] p : posts) if (p[1] == z) post = true;
                if (post) continue;
                int dzMin = 99;
                for (int[] p : posts) dzMin = Math.min(dzMin, Math.abs(p[1] - z));
                int topY = yb + 4 + (dzMin >= 2 ? 1 : 0);
                int dir = side == 0 ? 1 : -1;
                for (int k = 0; k <= 2; k++) fill(ix + dir * k, yb + 1, z, ix + dir * k, topY, z, "minecraft:air");
                final int zz = z;
                if (dzMin == 1) set(ix + dir, topY + 1, z, stairs("stone_brick_stairs", Dir.fromDelta(0, posts.stream().anyMatch(p -> p[1] == zz - 1) ? -1 : 1), true));
            }
            // toit en appentis (dalles a 22,5 degres) contre la nef
            for (int z = az1 - 1; z <= az2 + 1; z++)
                for (int k = 0; k <= 4; k++) {
                    int x = side == 0 ? -44 + k : -24 - k;
                    double hy = aTop + k * 0.5;
                    int ly = (int) Math.floor(hy);
                    boolean upper = hy - ly >= 0.5;
                    set(x, ly, z, slab("deepslate_tile_slab", upper));
                    if (k >= 1 && k <= 4 && z >= az1 && z <= az2) {
                        for (int y = aTop + 1; y < ly; y++) if (air(x, y, z)) set(x, y, z, "minecraft:deepslate_tiles");
                    }
                }
            // contreforts au droit des poteaux du mur exterieur
            for (int[] p : wallPosts(Math.min(ox, ox), az1, Math.max(ox, ox), az2, out, 4)) {
                int px = p[0] + out.dx, pz = p[1];
                fill(px, yb, pz, px, yb + 5, pz, st.wall);
                set(px, yb + 6, pz, stairs("stone_brick_stairs", out.opposite(), false));
                fill(px + out.dx, yb, pz, px + out.dx, yb + 3, pz, st.wall);
                set(px + out.dx, yb + 4, pz, stairs("stone_brick_stairs", out.opposite(), false));
                set(px + out.dx, yb - 1, pz, "minecraft:cobbled_deepslate");
            }
        }
        // toit de la nef (forte pente, ardoise), corniche a nTop
        RoofStyle rs = new RoofStyle().stairs(Palette.of(402, "cobbled_deepslate_stairs", 25, "deepslate_tile_stairs", 60, "deepslate_brick_stairs", 15))
                .ridge("minecraft:deepslate_tile_slab").body(Mat.of("minecraft:deepslate_tiles")).gable(st.wall).gablePost("minecraft:stripped_dark_oak_log")
                .overhang(1).spacing(4);
        int ridge = Roof.build(s, rs, Roof.gableZ(nx1, nz1, nx2, nz2, nTop));
        // pilastres interieurs et entraits (charpente apparente) : cassent les grands pans interieurs
        Facade.innerPosts(s, st, nx1, nz1, Dir.SOUTH, nz2 - nz1, Dir.WEST, yb, nTop);
        Facade.innerPosts(s, st, nx2, nz1, Dir.SOUTH, nz2 - nz1, Dir.EAST, yb, nTop);
        for (int z = nz1 + 3; z <= nz2 - 3; z += 4) {
            fill(nx1 + 2, nTop, z, nx2 - 2, nTop, z, "minecraft:stripped_dark_oak_log[axis=x]");
            set(nx1 + 2, nTop + 1, z, "minecraft:stripped_dark_oak_log");
            set(nx2 - 2, nTop + 1, z, "minecraft:stripped_dark_oak_log");
            set(-34, nTop + 1, z, "minecraft:stripped_dark_oak_log");
            set(-34, nTop + 2, z, "minecraft:stripped_dark_oak_log");
        }
        // abside semi-circulaire au nord
        int acx = -34, acz = -40;
        for (int x = acx - 6; x <= acx + 6; x++)
            for (int z = acz - 7; z <= acz - 1; z++) {
                double d = Math.hypot(x - acx, z - acz);
                if (d > 6.6) continue;
                set(x, yb, z, "minecraft:polished_andesite");
                if (d >= 4.6 && d <= 5.6) {
                    for (int y = yb + 1; y <= yb + 10; y++) set(x, y, z, st.wall);
                    set(x, yb, z, "minecraft:cobbled_deepslate");
                } else if (d < 4.6) {
                    fill(x, yb + 1, z, x, yb + 10, z, "minecraft:air");
                }
                // toit conique
                int ry = yb + 11 + (int) Math.round(Math.max(0, 6.0 - d));
                Dir face = Math.abs(x - acx) > Math.abs(z - acz) ? (x > acx ? Dir.WEST : Dir.EAST) : Dir.SOUTH;
                if (d >= 0.5) set(x, ry, z, stairs(d > 4.5 ? "cobbled_deepslate_stairs" : "deepslate_tile_stairs", face, false));
                else set(x, ry, z, "minecraft:deepslate_tile_slab[type=bottom]");
                if (d < 5.6) set(x, ry - 1, z, "minecraft:deepslate_tiles");
                if (d > 5.6) set(x, ry - 1, z, stairs("cobbled_deepslate_stairs", face, true));
            }
        Stairs.fix(s, acx - 7, yb + 10, acz - 8, acx + 7, yb + 18, acz);
        // vitraux de l'abside
        for (int[] w : new int[][]{{acx, acz - 5}, {acx - 4, acz - 3}, {acx + 4, acz - 3}})
            for (int y = yb + 3; y <= yb + 7; y++) set(w[0], y, w[1], "minecraft:red_stained_glass");
        // ouverture nef -> abside
        fill(nx1 + 2, yb + 1, nz1, nx2 - 2, yb + 8, nz1 + 1, "minecraft:air");
        // clocher au sud (x -37..-31, z -17..-11) : repere de toute la ville
        Style tw = new Style().wall(st.wall).plinth(st.plinth).plinthStairs(st.plinthStairs).post("minecraft:stripped_dark_oak_log")
                .corner(Mat.of("minecraft:chiseled_stone_bricks")).pane("minecraft:iron_bars").sill("minecraft:stone_brick_stairs")
                .lintel("minecraft:stone_brick_slab").shutter(null).band("minecraft:stone_brick_stairs").cornice("minecraft:stone_brick_stairs")
                .door("minecraft:dark_oak_door").spacing(3).storey(5).seed(43).blankBays(0.2);
        int tx1 = -37, tx2 = -31, tz1 = -17, tz2 = -11, tTop = yb + 22;
        fill(tx1 + 1, yb + 1, tz1 + 1, tx2 - 1, tTop, tz2 - 1, Mat.AIR);
        fill(tx1, yb, tz1, tx2, yb, tz2, "minecraft:polished_andesite");
        Facade.wall(s, tw, tx1, tz2, Dir.EAST, tx2 - tx1, Dir.SOUTH, yb, tTop, -1);
        Facade.wall(s, tw, tx1, tz1, Dir.SOUTH, tz2 - tz1, Dir.WEST, yb, tTop, -1);
        Facade.wall(s, tw, tx2, tz1, Dir.SOUTH, tz2 - tz1, Dir.EAST, yb, tTop, -1);
        Facade.wall(s, tw, tx1, tz1, Dir.EAST, tx2 - tx1, Dir.NORTH, yb + 13, tTop, -1);
        int mid = -34;
        // portail sud (3 de large, en arc) et passage vers la nef
        for (int z = tz2; z >= nz2 - 2; z--) {
            fill(mid - 1, yb + 1, z, mid + 1, yb + 4, z, "minecraft:air");
            set(mid, yb + 5, z, "minecraft:air");
        }
        set(mid - 1, yb + 5, tz2, stairs("stone_brick_stairs", Dir.EAST, true));
        set(mid + 1, yb + 5, tz2, stairs("stone_brick_stairs", Dir.WEST, true));
        for (int x = mid - 1; x <= mid + 1; x++) {
            set(x, yb, tz2, stairs("cobbled_deepslate_stairs", Dir.NORTH, false));
            for (int z = tz2 + 1; z >= nz2 - 3; z--) keep(x, yb + 1, z);
        }
        // beffroi : ouvertures, plancher, echelle, cloche
        for (int y = yb + 17; y <= yb + 20; y++) {
            for (int x = mid - 1; x <= mid + 1; x++) {
                set(x, y, tz2, "minecraft:air");
                set(x, y, tz2 - 1, "minecraft:air");
                set(x, y, tz1, "minecraft:air");
                set(x, y, tz1 + 1, "minecraft:air");
            }
            for (int z = -15; z <= -13; z++) {
                set(tx1, y, z, "minecraft:air");
                set(tx1 + 1, y, z, "minecraft:air");
                set(tx2, y, z, "minecraft:air");
                set(tx2 - 1, y, z, "minecraft:air");
            }
        }
        fill(tx1 + 2, yb + 16, tz1 + 2, tx2 - 2, yb + 16, tz2 - 2, "minecraft:spruce_planks");
        fill(tx1 + 2, tTop, tz1 + 2, tx2 - 2, tTop, tz2 - 2, "minecraft:spruce_planks");
        fill(tx1 + 2, yb + 1, tz1 + 2, tx1 + 2, yb + 16, tz1 + 2, "minecraft:ladder[facing=east]");
        keep(tx1 + 3, yb + 1, tz1 + 2);
        set(mid, yb + 20, -14, "minecraft:bell[attachment=ceiling,facing=north]");
        // fleche a 63 degres + paratonnerre
        int scx = mid, scz = -14;
        for (int rr = 4; rr >= 0; rr--) {
            int y0 = tTop + 1 + 2 * (4 - rr);
            for (int x = scx - rr; x <= scx + rr; x++)
                for (int z = scz - rr; z <= scz + rr; z++) {
                    if (Math.max(Math.abs(x - scx), Math.abs(z - scz)) != rr) continue;
                    if (rr == 0) {
                        set(x, y0, z, "minecraft:deepslate_tiles");
                        continue;
                    }
                    Dir face = Math.abs(x - scx) == rr ? (x > scx ? Dir.WEST : Dir.EAST) : (z > scz ? Dir.NORTH : Dir.SOUTH);
                    set(x, y0, z, stairs("deepslate_tile_stairs", face, false));
                    if (rr <= 3) set(x, y0 + 1, z, "minecraft:deepslate_tiles");
                }
        }
        set(scx, tTop + 10, scz, "minecraft:lightning_rod");
        Stairs.fix(s, scx - 5, tTop + 1, scz - 5, scx + 5, tTop + 11, scz + 5);
        // interieur : allee, bancs, autel dans l'abside, tribune, lustres, gravats
        for (int z = nz2 - 1; z >= nz1 - 3; z--) setIfAir(mid, yb + 1, z, "minecraft:red_carpet");
        for (int z = nz2 - 4; z >= nz1 + 5; z -= 2)
            for (int x = nx1 + 2; x <= nx2 - 2; x++) {
                if (Math.abs(x - mid) <= 1) continue;
                double hsh = Noise.hash(44, x / 2, 0, z);
                if (hsh < 0.12) continue;
                setIfAir(x, yb + 1, z, hsh < 0.2 ? slab("spruce_slab", false) : stairs("spruce_stairs", Dir.SOUTH, false));
            }
        fill(acx - 2, yb + 1, acz - 3, acx + 2, yb + 1, acz - 2, "minecraft:polished_diorite");
        fill(acx - 2, yb + 2, acz - 3, acx + 2, yb + 2, acz - 3, "minecraft:white_carpet");
        set(acx - 2, yb + 3, acz - 3, "minecraft:candle[candles=3,lit=false]");
        set(acx + 2, yb + 3, acz - 3, "minecraft:candle[candles=2,lit=false]");
        set(acx, yb + 2, acz - 1, "minecraft:lectern[facing=south]");
        fill(acx, yb + 2, acz - 4, acx, yb + 6, acz - 4, "minecraft:dark_oak_log");
        fill(acx - 1, yb + 5, acz - 4, acx + 1, yb + 5, acz - 4, "minecraft:dark_oak_log[axis=x]");
        for (int x = nx1 + 2; x <= nx2 - 2; x++) {
            for (int z = nz2 - 4; z <= nz2 - 2; z++) setIfAir(x, yb + 6, z, "minecraft:dark_oak_planks");
            if (x != nx1 + 2) setIfAir(x, yb + 7, nz2 - 5, "minecraft:dark_oak_fence");
        }
        fill(nx1 + 2, yb + 1, nz2 - 3, nx1 + 2, yb + 6, nz2 - 3, "minecraft:ladder[facing=east]");
        for (int z = nz1 + 6; z <= nz2 - 6; z += 6) {
            fill(mid, yb + 11, z, mid, nTop - 1, z, "minecraft:iron_chain");
            set(mid, yb + 10, z, z % 12 == 0 ? "minecraft:soul_lantern[hanging=true]" : "minecraft:iron_chain");
        }
        SplittableRandom r = new SplittableRandom(45);
        for (int k = 0; k < 18; k++) {
            int x = nx1 + 2 + r.nextInt(nx2 - nx1 - 3), z = nz1 + 2 + r.nextInt(nz2 - nz1 - 3);
            furn(x, yb + 1, z, r.nextBoolean() ? "minecraft:cobweb" : r.nextBoolean() ? "minecraft:cobblestone_slab" : "minecraft:mossy_cobblestone");
        }
        for (int k = 0; k < 12; k++) {
            furn(nx1 + 2, yb + 9 + r.nextInt(4), nz1 + 2 + r.nextInt(nz2 - nz1 - 3), "minecraft:cobweb");
            furn(nx2 - 2, yb + 9 + r.nextInt(4), nz1 + 2 + r.nextInt(nz2 - nz1 - 3), "minecraft:cobweb");
        }
        // vitraux de couleurs, quelques-uns brises
        String[] glass = {"purple", "blue", "red", "yellow", "magenta", "light_blue"};
        for (int x = -46; x <= -22; x++)
            for (int z = -42; z <= -10; z++)
                for (int y = yb; y <= nTop; y++) {
                    String n = nm(x, y, z);
                    if (!n.endsWith("stained_glass_pane")) continue;
                    double hsh = Noise.hash(46, x, y / 3, z);
                    if (hsh < 0.15) set(x, y, z, "minecraft:air");
                    else {
                        String cur = s.get(x, y, z);
                        set(x, y, z, cur.replace(B.base(cur), "minecraft:" + glass[(int) (hsh * 997) % glass.length] + "_stained_glass_pane"));
                    }
                }
        // parvis
        Palette parvis = Palette.of(47, "polished_andesite", 80, "andesite", 20).period(5);
        for (int x = -38; x <= -30; x++)
            for (int z = -10; z <= -6; z++) s.set(x, G, z, parvis.at(x, G, z));
        // echelle vers le toit du bas-cote ouest
        ladder(new int[]{-43, -30, Dir.WEST.ordinal(), aTop + 1, 0});
        buildings += 4;
        Connect.fix(s, -46, yb, -46, -22, ridge + 2, -9);
    }

    // ================================================================ cimetiere + crypte

    void cemetery() {
        int x1 = -22, x2 = -8, z1 = -42, z2 = -20;
        Palette ground = Palette.of(51, "grass_block", 60, "podzol", 20, "coarse_dirt", 12, "moss_block", 8).period(4);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                int y = top(x, z);
                s.set(x, y, z, ground.at(x, y, z));
            }
        // muret + grille, piliers a lanterne
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (!edge) continue;
                boolean gate = (z == z2 && (x == -15 || x == -14)) || (x == x2 && (z == -31 || z == -30));
                if (gate) continue;
                boolean pillar = (x - x1) % 5 == 0 && (z == z1 || z == z2) || (z - z1) % 5 == 0 && (x == x1 || x == x2);
                int y = top(x, z) + 1;
                if (pillar) {
                    fill(x, y, z, x, y + 1, z, "minecraft:stone_bricks");
                    set(x, y + 2, z, Noise.hash(52, x, 0, z) < 0.6 ? "minecraft:soul_lantern" : "minecraft:stone_brick_slab[type=bottom]");
                } else {
                    set(x, y, z, "minecraft:mossy_cobblestone_wall");
                    if (Noise.hash(53, x / 3, 0, z / 3) > 0.2) set(x, y + 1, z, "minecraft:iron_bars");
                }
            }
        for (int x : new int[]{-16, -13}) {
            fill(x, G + 1, z2, x, G + 2, z2, "minecraft:stone_bricks");
            set(x, G + 3, z2, "minecraft:soul_lantern");
        }
        // allees
        for (int z = -30; z <= z2; z++) {
            s.set(-15, top(-15, z), z, "minecraft:gravel");
            s.set(-14, top(-14, z), z, "minecraft:gravel");
        }
        for (int x = -14; x <= x2; x++) {
            s.set(x, top(x, -31), -31, "minecraft:gravel");
            s.set(x, top(x, -30), -30, "minecraft:gravel");
        }
        // tombes (sur leur butte)
        for (int x = -21; x <= -9; x += 3) {
            if (x >= -17 && x <= -12) continue;
            for (int z : new int[]{-39, -35, -27, -24}) grave(x, z);
        }
        for (int x = -20; x <= -10; x += 3) grave(x, -41);
        // mausolee (les zombies en sortent)
        Style ms = new Style().wall(Palette.of(54, "deepslate_bricks", 82, "cracked_deepslate_bricks", 18).period(6))
                .plinth(Mat.of("minecraft:polished_blackstone")).plinthStairs("minecraft:polished_blackstone_stairs").post("minecraft:polished_basalt")
                .pane("minecraft:iron_bars").sill("minecraft:polished_deepslate_stairs").lintel("minecraft:polished_deepslate_slab").shutter(null)
                .band(null).cornice("minecraft:deepslate_brick_stairs").door("minecraft:dark_oak_door").spacing(5).storey(5).seed(55).innerPosts(false);
        int mx1 = -18, mx2 = -11, mz1 = -37, mz2 = -31;
        Facade.building(s, ms, mx1, mz1, mx2, mz2, G + 1, G + 6, Dir.SOUTH, 3);
        fill(mx1 + 1, G + 1, mz1 + 1, mx2 - 1, G + 1, mz2 - 1, "minecraft:deepslate_tiles");
        Roof.build(s, new RoofStyle().stairs(Palette.of(56, "deepslate_tile_stairs", 70, "deepslate_brick_stairs", 30)).ridge("minecraft:deepslate_tile_slab")
                .body(Mat.of("minecraft:deepslate_tiles")).gablePost(null).overhang(1), Roof.hip(mx1, mz1, mx2, mz2, G + 6));
        for (int x = -15; x <= -14; x++) {
            fill(x, G + 2, mz2 - 1, x, G + 4, mz2, "minecraft:air");
            set(x, G + 1, mz2, stairs("polished_blackstone_stairs", Dir.NORTH, false));
        }
        set(-16, G + 5, mz2 + 1, "minecraft:soul_lantern[hanging=true]");
        set(-13, G + 5, mz2 + 1, "minecraft:soul_lantern[hanging=true]");
        fill(-16, G + 1, mz2 + 1, -16, G + 3, mz2 + 1, "minecraft:polished_deepslate_wall");
        fill(-13, G + 1, mz2 + 1, -13, G + 3, mz2 + 1, "minecraft:polished_deepslate_wall");
        set(-16, G + 4, mz2 + 1, "minecraft:polished_deepslate_slab[type=bottom]");
        set(-13, G + 4, mz2 + 1, "minecraft:polished_deepslate_slab[type=bottom]");
        fill(-16, G + 2, -35, -13, G + 2, -35, "minecraft:dark_oak_planks");
        set(-15, G + 3, -35, "minecraft:candle[candles=4,lit=false]");
        set(-12, G + 2, -34, "minecraft:skeleton_skull[rotation=8]");
        fill(-17, G + 1, -33, -16, G + 1, -33, "minecraft:iron_bars[east=true,west=true]");
        buildings++;
        for (int x = -15; x <= -14; x++)
            for (int z = -30; z <= -25; z++) keep(x, G + 1, z);
        deadTree(-10, top(-10, -38) + 1, -38, 5);
        deadTree(-21, top(-21, -29) + 1, -29, 6);
        Connect.fix(s, x1 - 1, G, z1 - 1, x2 + 1, G + 8, z2 + 1);
    }

    void grave(int x, int z) {
        double hsh = Noise.hash(57, x, 0, z);
        int y = top(x, z);
        String[] stones = {"minecraft:stone_brick_wall", "minecraft:mossy_cobblestone_wall", "minecraft:andesite_wall", "minecraft:cobblestone_wall"};
        if (hsh < 0.2) {
            set(x, y + 1, z, "minecraft:polished_andesite");
            set(x, y + 2, z, "minecraft:stone_button[face=floor,facing=north]");
        } else if (hsh < 0.35) {
            set(x, y + 1, z, "minecraft:cobblestone_wall");
            set(x, y + 2, z, "minecraft:cobblestone_wall");
            set(x - 1, y + 2, z, "minecraft:stone_brick_slab[type=top]");
            set(x + 1, y + 2, z, "minecraft:stone_brick_slab[type=top]");
        } else {
            set(x, y + 1, z, stones[(int) (hsh * 100) % stones.length]);
        }
        if (hsh > 0.78) {
            // tombe ouverte
            for (int dz = 1; dz <= 2; dz++) {
                set(x, top(x, z + dz), z + dz, "minecraft:air");
                set(x, top(x, z + dz) - 1, z + dz, "minecraft:coarse_dirt");
            }
            setIfAir(x + 1, top(x + 1, z + 1) + 1, z + 1, "minecraft:coarse_dirt");
        } else {
            s.set(x, top(x, z + 1), z + 1, "minecraft:podzol");
            s.set(x, top(x, z + 2), z + 2, "minecraft:coarse_dirt");
            if (hsh > 0.66) setIfAir(x, top(x, z + 2) + 1, z + 2, "minecraft:skeleton_skull[rotation=" + ((int) (hsh * 160) % 16) + "]");
            else if (hsh > 0.5) setIfAir(x, top(x, z + 2) + 1, z + 2, "minecraft:dead_bush");
        }
    }

    void crypt() {
        // salle fermee sous le cimetiere : les zombies y attendent (aucune sortie)
        fill(-20, G - 11, -38, -8, G - 5, -28, "minecraft:deepslate_bricks");
        fill(-19, G - 9, -37, -9, G - 6, -29, "minecraft:air");
        Palette fl = Palette.of(58, "deepslate_tiles", 80, "cracked_deepslate_tiles", 20).period(4);
        fill(-19, G - 10, -37, -9, G - 10, -29, fl);
        for (int x : new int[]{-19, -9})
            for (int z : new int[]{-37, -29}) fill(x, G - 9, z, x, G - 6, z, "minecraft:polished_deepslate");
        for (int x = -18; x <= -10; x++) {
            set(x, G - 6, -37, "minecraft:deepslate_brick_stairs[facing=south,half=top]");
            set(x, G - 6, -29, "minecraft:deepslate_brick_stairs[facing=north,half=top]");
        }
        for (int x = -19; x <= -9; x += 5) set(x == -19 ? -18 : x == -9 ? -10 : x, G - 6, -33, "minecraft:soul_lantern[hanging=true]");
        for (int z = -36; z <= -30; z += 3) {
            fill(-19, G - 9, z, -18, G - 9, z, "minecraft:dark_oak_planks");
            fill(-10, G - 9, z, -9, G - 9, z, "minecraft:dark_oak_planks");
        }
        set(-18, G - 8, -36, "minecraft:skeleton_skull[rotation=6]");
        set(-10, G - 8, -36, "minecraft:skeleton_skull[rotation=10]");
        set(-14, G - 9, -37, "minecraft:soul_campfire[lit=true]");
        set(-18, G - 7, -30, "minecraft:cobweb");
        set(-10, G - 7, -30, "minecraft:cobweb");
    }

    // ================================================================ supermarche (NE) en trois volumes

    void supermarket() {
        UStyle us = URBAN[0];
        Style st = new Style().wall(us.wall).plinth(Mat.of("minecraft:polished_blackstone")).plinthStairs("minecraft:polished_blackstone_stairs")
                .post("minecraft:quartz_pillar").sill("minecraft:smooth_quartz_stairs").lintel("minecraft:smooth_stone_slab").shutter(null)
                .band(null).cornice("minecraft:smooth_quartz_stairs").door("minecraft:dark_oak_door")
                .floor(Mat.of("minecraft:spruce_planks")).spacing(5).storey(7).seed(61).blankBays(0.3);
        int yb = G + 1;
        // grande salle
        int hx1 = 10, hx2 = 36, hz1 = -42, hz2 = -25, hTop = yb + 7;
        Facade.building(s, st, hx1, hz1, hx2, hz2, yb, hTop, Dir.SOUTH, 6);
        for (int x = hx1 + 1; x <= hx2 - 1; x++)
            for (int z = hz1 + 1; z <= hz2 - 1; z++) s.set(x, yb, z, (x + z) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:polished_andesite");
        Roof.flat(s, hx1, hz1, hx2, hz2, hTop, us.roofSurface, us.wall, "minecraft:smooth_stone_slab", 1, true);
        // reserve, plus basse
        int rx1 = 36, rx2 = 42, rz1 = -42, rz2 = -28, rTop = yb + 5;
        Style sr = new Style().wall(Palette.of(62, "bricks", 88, "packed_mud", 12).period(9)).plinth(st.plinth).plinthStairs(st.plinthStairs)
                .post("minecraft:polished_basalt").sill("minecraft:stone_brick_stairs").lintel("minecraft:stone_brick_slab").shutter(null)
                .band(null).cornice("minecraft:stone_brick_stairs").door("minecraft:spruce_door").floor(Mat.of("minecraft:spruce_planks"))
                .spacing(4).storey(5).seed(63);
        Facade.building(s, sr, rx1, rz1, rx2, rz2, yb, rTop, Dir.EAST, 7, Dir.WEST);
        Roof.flat(s, rx1, rz1, rx2, rz2, rTop, roofSurf(64), sr.wall, "minecraft:stone_brick_slab", 1, false);
        for (int z = -33; z <= -32; z++) {
            fill(rx1 - 2, yb + 1, z, rx1, yb + 2, z, "minecraft:air");
            keep(rx1 - 3, yb + 1, z);
            keep(rx1 + 1, yb + 1, z);
        }
        // quai de chargement + auvent
        fill(43, yb, -38, 44, yb, -32, "minecraft:smooth_stone_slab[type=top]");
        fill(43, yb + 4, -38, 44, yb + 4, -32, "minecraft:smooth_stone_slab[type=bottom]");
        for (int z : new int[]{-38, -32}) fill(44, yb + 1, z, 44, yb + 3, z, "minecraft:iron_bars");
        // entree, plus haute, avec enseigne
        int ex1 = 20, ex2 = 30, ez1 = -27, ez2 = -22, eTop = yb + 9;
        Style se = new Style().wall(Palette.of(65, "white_concrete", 85, "calcite", 15).period(8)).plinth(st.plinth).plinthStairs(st.plinthStairs)
                .post("minecraft:quartz_pillar").sill("minecraft:smooth_quartz_stairs").lintel("minecraft:smooth_stone_slab").shutter(null).band(null)
                .cornice("minecraft:smooth_quartz_stairs").door("minecraft:dark_oak_door").floor(Mat.of("minecraft:spruce_planks")).spacing(5).storey(9).seed(66);
        Facade.building(s, se, ex1, ez1, ex2, ez2, yb, eTop, Dir.SOUTH, 5, Dir.NORTH);
        for (int x = ex1 + 1; x <= ex2 - 1; x++)
            for (int z = ez1 + 1; z <= ez2 - 1; z++) s.set(x, yb, z, (x + z) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:polished_andesite");
        fill(ex1 + 1, yb + 1, hz2 - 1, ex2 - 1, yb + 5, hz2, "minecraft:air");
        Roof.flat(s, ex1, ez1, ex2, ez2, eTop, roofSurf(67), se.wall, "minecraft:smooth_stone_slab", 1, false);
        // porte vitree cassee, 3 de large
        for (int x = 24; x <= 26; x++) {
            fill(x, yb + 1, ez2 - 1, x, yb + 3, ez2, "minecraft:air");
            set(x, yb, ez2, "minecraft:polished_blackstone_stairs[facing=north]");
            keep(x, yb + 1, ez2 + 1);
            keep(x, yb + 1, ez2 - 2);
        }
        // enseigne
        for (int x = ex1 + 1; x <= ex2 - 1; x++) {
            set(x, yb + 6, ez2 + 1, x % 2 == 0 ? "minecraft:red_wool" : "minecraft:white_wool");
            set(x, yb + 5, ez2 + 1, "minecraft:red_terracotta");
        }
        set(25, yb + 7, ez2 + 1, "minecraft:yellow_wool");
        // vitrines de la grande salle
        String paneEW = B.pane("minecraft:glass_pane", false, true, false, true);
        for (int x = hx1 + 2; x <= hx2 - 2; x++) {
            if (x >= ex1 - 1 && x <= ex2 + 1) continue;
            if (nm(x, yb + 2, hz2).equals("quartz_pillar")) continue;
            double hsh = Noise.hash(68, x / 2, 0, 0);
            // la vitrine coupe une porte de la facade : on retire aussi sa moitie basse (sinon demi-porte orpheline)
            if (nm(x, yb + 1, hz2 - 1).endsWith("_door")) set(x, yb + 1, hz2 - 1, "minecraft:air");
            for (int y = yb + 1; y <= yb + 4; y++) set(x, y, hz2, "minecraft:air");
            for (int y = yb + 2; y <= yb + 4; y++) {
                set(x, y, hz2 - 1, hsh < 0.35 ? "minecraft:air" : hsh < 0.5 ? "minecraft:spruce_planks" : paneEW);
                set(x, y, hz2 - 2, "minecraft:air");
            }
        }
        // rayons
        String[] goods = {"minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block", "minecraft:bookshelf", "minecraft:white_wool", "minecraft:red_wool",
            "minecraft:brown_mushroom_block", "minecraft:dried_kelp_block", "minecraft:honeycomb_block", "minecraft:barrel[facing=up]", "minecraft:target"};
        SplittableRandom r = new SplittableRandom(69);
        for (int z : new int[]{-39, -36, -33, -30}) {
            for (int x = 13; x <= 33; x++) {
                if (x >= 22 && x <= 24) continue;
                double hsh = Noise.hash(70, x / 3, 0, z);
                if (hsh < 0.12) {
                    if (r.nextBoolean()) furn(x, yb + 1, z + 1, goods[r.nextInt(goods.length)]);
                    continue;
                }
                set(x, yb + 1, z, "minecraft:spruce_planks");
                set(x, yb + 2, z, goods[r.nextInt(goods.length)]);
                if (hsh > 0.5) set(x, yb + 3, z, "minecraft:spruce_slab[type=bottom]");
            }
        }
        for (int x = hx1 + 2; x <= 33; x++) {
            set(x, yb + 1, hz1 + 2, "minecraft:packed_ice");
            set(x, yb + 2, hz1 + 2, Noise.hash(71, x / 2, 0, 0) < 0.3 ? "minecraft:air" : "minecraft:light_blue_stained_glass");
        }
        for (int x = 13; x <= 21; x += 4) {
            set(x, yb + 1, -28, "minecraft:smooth_quartz");
            set(x + 1, yb + 1, -28, "minecraft:smooth_quartz");
            set(x, yb + 2, -28, "minecraft:loom");
        }
        for (int k = 0; k < 14; k++) {
            int x = 37 + r.nextInt(4), z = rz1 + 2 + r.nextInt(rz2 - rz1 - 3);
            int hh = 1 + r.nextInt(3);
            for (int y = yb + 1; y < yb + 1 + hh; y++)
                furn(x, y, z, r.nextBoolean() ? "minecraft:barrel[facing=up]" : r.nextBoolean() ? "minecraft:spruce_planks" : "minecraft:hay_block");
        }
        // lumieres du plafond (certaines eteintes)
        for (int x = hx1 + 3; x <= hx2 - 3; x += 5)
            for (int z = hz1 + 3; z <= hz2 - 3; z += 5) set(x, hTop - 1, z, Noise.hash(72, x, 0, z) < 0.5 ? "minecraft:lantern[hanging=true]" : "minecraft:iron_chain");
        // echelles : quai -> toit de la reserve -> toit de la salle -> toit de l'entree
        ladder(new int[]{rx2, -30, Dir.EAST.ordinal(), rTop + 1, 3});
        fill(rx1 + 1, rTop + 1, -40, rx1 + 1, hTop + 1, -40, "minecraft:ladder[facing=east]");
        set(rx1, hTop + 1, -40, "minecraft:air");
        set(rx1, hTop + 2, -40, "minecraft:air");
        int lx = 31;
        fill(lx, hTop + 1, ez1 + 1, lx, eTop + 1, ez1 + 1, "minecraft:ladder[facing=east]");
        set(ex2, eTop + 1, ez1 + 1, "minecraft:air");
        set(ex2, eTop + 2, ez1 + 1, "minecraft:air");
        // caissons de clim sur le toit
        for (int[] hv : new int[][]{{14, -38}, {22, -33}, {32, -38}}) {
            fill(hv[0], hTop + 1, hv[1], hv[0] + 1, hTop + 2, hv[1] + 1, "minecraft:iron_block");
            fill(hv[0], hTop + 3, hv[1], hv[0] + 1, hTop + 3, hv[1] + 1, "minecraft:iron_trapdoor[half=bottom]");
        }
        for (int x = 14; x <= 34; x += 6) fill(x, hTop, -36, x + 1, hTop, -35, "minecraft:glass");
        for (int k = 0; k < 10; k++) furn(hx1 + 2 + r.nextInt(hx2 - hx1 - 3), yb + 1 + r.nextInt(3), hz1 + 2 + r.nextInt(hz2 - hz1 - 3), "minecraft:cobweb");
        weather(hx1 - 1, yb, hz1 - 1, rx2 + 2, eTop + 2, ez2 + 2, 73, 0.3, 0.15, "minecraft:spruce_planks");
        // abribus
        fill(20, G + 1, -5, 20, G + 3, -5, "minecraft:iron_bars");
        fill(24, G + 1, -5, 24, G + 3, -5, "minecraft:iron_bars");
        fill(20, G + 4, -5, 24, G + 4, -4, "minecraft:smooth_stone_slab");
        fill(21, G + 1, -5, 23, G + 3, -5, "minecraft:glass_pane[east=true,west=true]");
        set(22, G + 2, -5, "minecraft:air");
        fill(21, G + 1, -4, 23, G + 1, -4, "minecraft:spruce_slab");
        // voitures du parking
        int[][] cars = {{11, -21, 0, 0}, {19, -21, 0, 1}, {31, -21, 0, 0}, {39, -21, 0, 0}, {14, -11, 0, 0}, {27, -11, 0, 0}, {36, -11, 0, 1}, {22, -16, 1, 0}};
        for (int[] c : cars) car(c[0], c[1], c[2] == 1, c[3] == 1);
        buildings += 3;
        Connect.fix(s, hx1 - 1, yb, hz1 - 1, 45, eTop + 3, ez2 + 2);
    }

    // ================================================================ station-service (SE)

    void gasStation() {
        Palette apron = Palette.of(81, "light_gray_concrete", 85, "smooth_stone", 15).period(6);
        for (int x = 7; x <= 27; x++)
            for (int z = 7; z <= 22; z++) s.set(x, G, z, apron.at(x, G, z));
        int[][] pil = {{11, 10}, {23, 10}, {11, 20}, {23, 20}};
        for (int[] p : pil) {
            set(p[0], G + 1, p[1], "minecraft:polished_blackstone");
            fill(p[0], G + 2, p[1], p[0], G + 4, p[1], "minecraft:quartz_pillar");
        }
        // auvent : bandeau rouge en saillie, sous-face blanche avec eclairage
        for (int x = 9; x <= 25; x++)
            for (int z = 9; z <= 21; z++) {
                boolean edge = x == 9 || x == 25 || z == 9 || z == 21;
                boolean in2 = x == 10 || x == 24 || z == 10 || z == 20;
                if (edge) {
                    set(x, G + 5, z, "minecraft:red_concrete");
                    set(x, G + 6, z, "minecraft:red_concrete");
                    boolean corner = (x == 9 || x == 25) && (z == 9 || z == 21);
                    if (!corner && Math.floorMod(x + z, 4) == 0) {
                        int ox = x == 9 ? -1 : x == 25 ? 1 : 0, oz = ox != 0 ? 0 : (z == 9 ? -1 : 1);
                        set(x + ox, G + 5, z + oz, "minecraft:white_concrete");
                        set(x + ox, G + 6, z + oz, "minecraft:white_concrete");
                    }
                    set(x, G + 4, z, stairs("smooth_quartz_stairs", x == 9 ? Dir.EAST : x == 25 ? Dir.WEST : z == 9 ? Dir.SOUTH : Dir.NORTH, true));
                } else {
                    boolean light = x % 4 == 1 && z % 4 == 3;
                    set(x, G + 5, z, light ? (Noise.hash(82, x, 0, z) < 0.5 ? "minecraft:sea_lantern" : "minecraft:glass") : in2 ? "minecraft:white_concrete" : "minecraft:smooth_quartz");
                }
            }
        Stairs.fix(s, 9, G + 4, 9, 25, G + 4, 21);
        fill(11, G + 1, 8, 11, G + 6, 8, "minecraft:ladder[facing=north]");
        set(11, G + 6, 9, "minecraft:air");
        // ilots et pompes
        for (int x = 13; x <= 21; x++) set(x, G + 1, 15, "minecraft:smooth_stone_slab");
        for (int x : new int[]{14, 18, 21}) {
            set(x, G + 1, 15, "minecraft:red_concrete");
            set(x, G + 2, 15, "minecraft:red_concrete");
            set(x, G + 3, 15, "minecraft:white_concrete");
            set(x, G + 2, 16, "minecraft:tripwire_hook[facing=south]");
        }
        // enseigne sur poteau
        fill(26, G + 1, 21, 26, G + 7, 21, "minecraft:stone_brick_wall");
        fill(25, G + 8, 21, 27, G + 9, 21, "minecraft:red_concrete");
        set(26, G + 8, 21, "minecraft:yellow_concrete");
        set(26, G + 9, 21, "minecraft:yellow_concrete");
        int[][] cans = {{8, 20}, {9, 20}, {8, 19}, {27, 8}};
        for (int[] c : cans) set(c[0], G + 1, c[1], (c[0] + c[1]) % 3 == 0 ? "minecraft:cauldron" : "minecraft:barrel[facing=up]");
        car(16, 17, true, false);
        buildings++;
        Connect.fix(s, 7, G + 1, 7, 27, G + 9, 22);
    }

    // ================================================================ coins du boulevard

    void corners() {
        // NO : parc a etang
        int px = -66, pz = -66;
        for (int x = -76; x <= -56; x++)
            for (int z = -76; z <= -56; z++) {
                double dx = (x - px) / 5.5, dz = (z - pz) / 4.2;
                double d = dx * dx + dz * dz + 0.2 * nz2.noise2(x / 3.0, z / 3.0);
                if (d <= 1.0) {
                    fill(x, G - 1, z, x, top(x, z), z, "minecraft:mud");
                    set(x, G - 1, z, "minecraft:mud");
                    set(x, G, z, "minecraft:water");
                    for (int y = G + 1; y <= top(x, z); y++) set(x, y, z, "minecraft:air");
                    if (nz2.uniform2(x / 2.0, z / 2.0) > 0.86) set(x, G + 1, z, "minecraft:lily_pad");
                }
            }
        long sd = 700;
        for (int[] t : new int[][]{{-73, -73}, {-59, -72}, {-72, -59}, {-60, -60}, {-66, -58}}) {
            int y = top(t[0], t[1]) + 1;
            if (air(t[0], y, t[1])) new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(6 + (int) (sd % 3)).crown(3.0)
                    .place(s, t[0], y, t[1], sd);
            sd += 41;
        }
        deadTree(-75, top(-75, -64) + 1, -64, 6);
        for (int x = -69; x <= -63; x += 3) setIfAir(x, top(x, -60) + 1, -60, stairs("spruce_stairs", Dir.SOUTH, false));
        // NE : entrepot en ruine (toit en sheds) + chateau d'eau
        Style ws = new Style().wall(URBAN[1].wall).plinth(Mat.of("minecraft:polished_andesite")).plinthStairs("minecraft:polished_andesite_stairs")
                .post("minecraft:polished_basalt").sill("minecraft:stone_brick_stairs").lintel("minecraft:stone_brick_slab").shutter(null)
                .band(null).cornice("minecraft:stone_brick_stairs").door("minecraft:spruce_door").floor(Mat.of("minecraft:spruce_planks"))
                .spacing(5).storey(7).seed(91).blankBays(0.1);
        int wx1 = 57, wx2 = 71, wz1 = -75, wz2 = -61, wTop = G + 8;
        Facade.building(s, ws, wx1, wz1, wx2, wz2, G + 1, wTop, Dir.SOUTH, 7);
        fill(wx1 + 2, G + 1, wz1 + 2, wx2 - 2, G + 1, wz2 - 2, Palette.of(92, "gray_concrete", 80, "cyan_terracotta", 20).period(5));
        RoofStyle sheds = new RoofStyle().stairs(Palette.of(93, "cut_copper_stairs", 30, "exposed_cut_copper_stairs", 40, "weathered_cut_copper_stairs", 30))
                .ridge("minecraft:weathered_cut_copper_slab").body(Mat.of("minecraft:weathered_cut_copper")).gable(ws.wall).gablePost(null).overhang(1);
        int wr = 0;
        for (int z = wz1; z + 3 <= wz2; z += 4) wr = Math.max(wr, Roof.build(s, sheds, Roof.gableX(wx1, z, wx2, Math.min(wz2, z + 3), wTop)));
        for (int z = wz2 - 1; z <= wz2; z++) fill(62, G + 2, z, 66, G + 5, z, "minecraft:air");
        for (int x = 62; x <= 66; x++) keep(x, G + 1, wz2 + 1);
        ruin(wx1, G + 4, wz1, wx2, wr + 1, wz2, 94, 0.9);
        SplittableRandom r = new SplittableRandom(95);
        for (int k = 0; k < 16; k++) {
            int x = wx1 + 2 + r.nextInt(wx2 - wx1 - 3), z = wz1 + 2 + r.nextInt(wz2 - wz1 - 3);
            int hh = 1 + r.nextInt(3);
            for (int y = G + 2; y < G + 2 + hh; y++) furn(x, y, z, r.nextBoolean() ? "minecraft:barrel[facing=up]" : "minecraft:spruce_planks");
        }
        waterTower(74, -58);
        buildings += 2;
        // SO : foret morte et cabane
        for (int k = 0; k < 16; k++) {
            int x = -76 + r.nextInt(21), z = 56 + r.nextInt(21);
            if (in(x, z, -72, 64, -62, 73)) continue;
            int y = top(x, z) + 1;
            if (!air(x, y, z) || !air(x, y + 2, z)) continue;
            if (k % 4 == 0) new Tree("minecraft:spruce_log", "minecraft:spruce_leaves", null).height(8 + r.nextInt(3)).crown(2.4).place(s, x, y, z, 800 + k);
            else deadTree(x, y, z, 4 + r.nextInt(4));
        }
        for (int k = 0; k < 6; k++) {
            int x = -75 + r.nextInt(18), z = 57 + r.nextInt(18);
            int y = top(x, z) + 1;
            boolean ax = r.nextBoolean();
            for (int i = 0; i < 4; i++) {
                int xx = ax ? x + i : x, zz = ax ? z : z + i;
                if (top(xx, zz) + 1 == y && air(xx, y, zz)) set(xx, y, zz, ax ? "minecraft:spruce_log[axis=x]" : "minecraft:spruce_log[axis=z]");
            }
        }
        Lot cabin = new Lot(-71, 65, -64, 71, Dir.NORTH, HOUSE, 1, 2, true, 96);
        house(cabin);
        for (int k = 0; k < 35; k++) {
            int x = -76 + r.nextInt(21), z = 56 + r.nextInt(21);
            int y = top(x, z) + 1;
            if (air(x, y, z)) set(x, y, z, k % 3 == 0 ? "minecraft:cobweb" : k % 3 == 1 ? "minecraft:dead_bush" : "minecraft:skeleton_skull[rotation=" + (k % 16) + "]");
        }
        // SE : casse auto, conteneurs empiles
        int[][] wrecks = {{58, 58, 1}, {64, 57, 0}, {70, 60, 1}, {58, 66, 0}, {66, 66, 1}, {72, 69, 0}, {60, 72, 1}};
        for (int[] c : wrecks) car(c[0], c[1], c[2] == 1, (c[0] + c[1]) % 3 != 0);
        container(62, 70, true, "orange", 0);
        container(62, 70, true, "blue", 3);
        container(71, 57, false, "green", 0);
        container(57, 61, false, "red", 0);
        // pile de pneus et tas de ferraille
        for (int[] p : new int[][]{{68, 63}, {74, 74}, {59, 75}}) {
            int y = top(p[0], p[1]) + 1;
            fill(p[0], y, p[1], p[0] + 1, y + 1, p[1] + 1, "minecraft:black_concrete");
            set(p[0], y + 2, p[1], "minecraft:black_carpet");
        }
        for (int k = 0; k < 22; k++) {
            int x = 56 + r.nextInt(21), z = 56 + r.nextInt(21);
            int y = top(x, z) + 1;
            if (air(x, y, z) && air(x, y + 1, z)) set(x, y, z, k % 4 == 0 ? "minecraft:anvil[facing=north]" : k % 4 == 1 ? "minecraft:iron_block" : k % 4 == 2 ? "minecraft:smooth_stone_slab" : "minecraft:cobweb");
        }
        Connect.fix(s, -77, G, -77, -55, G + 12, -55);
    }

    /** Conteneur 3 x 3 x 7 (porte a barreaux au bout), pose a la hauteur hLevel au-dessus du sol. */
    void container(int x, int z, boolean alongX, String color, int hLevel) {
        int y0 = G + 1 + hLevel;
        for (int i = 0; i < 7; i++)
            for (int j = 0; j < 3; j++) {
                int bx = alongX ? x + i : x + j, bz = alongX ? z + j : z + i;
                int yb = Math.max(y0, top(bx, bz) + 1);
                if (hLevel == 0) yb = y0 + Math.max(0, top(x, z) - G);
                for (int y = yb; y <= yb + 2; y++) {
                    boolean shell = j == 0 || j == 2 || y == yb || y == yb + 2 || i == 0 || i == 6;
                    if (!shell) {
                        set(bx, y, bz, "minecraft:air");
                        continue;
                    }
                    boolean end = i == 0 || i == 6;
                    set(bx, y, bz, end && j == 1 && y == yb + 1 ? "minecraft:iron_bars" : (end ? "minecraft:" + color + "_terracotta" : "minecraft:" + color + "_concrete"));
                }
            }
        if (hLevel == 0) {
            int ex = alongX ? x : x + 1, ez = alongX ? z + 1 : z;
            set(ex, G + 2 + Math.max(0, top(x, z) - G), ez, "minecraft:air");
        }
    }

    void waterTower(int cx, int cz) {
        int[][] legs = {{cx - 2, cz - 2}, {cx + 2, cz - 2}, {cx - 2, cz + 2}, {cx + 2, cz + 2}};
        for (int[] l : legs) fill(l[0], G + 1, l[1], l[0], G + 12, l[1], "minecraft:stripped_dark_oak_log");
        for (int y : new int[]{G + 5, G + 9}) {
            fill(cx - 1, y, cz - 2, cx + 1, y, cz - 2, "minecraft:dark_oak_fence");
            fill(cx - 1, y, cz + 2, cx + 1, y, cz + 2, "minecraft:dark_oak_fence");
            fill(cx - 2, y, cz - 1, cx - 2, y, cz + 1, "minecraft:dark_oak_fence");
            fill(cx + 2, y, cz - 1, cx + 2, y, cz + 1, "minecraft:dark_oak_fence");
        }
        // passerelle circulaire
        for (int x = cx - 4; x <= cx + 4; x++)
            for (int z = cz - 4; z <= cz + 4; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= 3.6) set(x, G + 12, z, "minecraft:spruce_slab[type=top]");
                else if (d <= 4.4) set(x, G + 13, z, "minecraft:spruce_fence");
            }
        Shapes.cylinder(s, cx, cz, G + 13, G + 18, 3.0, Palette.of(97, "spruce_planks", 80, "dark_oak_planks", 20).period(4), true);
        for (int y : new int[]{G + 14, G + 17}) Shapes.cylinder(s, cx, cz, y, y, 3.0, Mat.of("minecraft:stripped_spruce_wood"), true);
        fill(cx - 2, G + 13, cz - 2, cx + 2, G + 13, cz + 2, Mat.of("minecraft:spruce_planks"));
        Roof.build(s, new RoofStyle().stairs("minecraft:dark_oak_stairs").ridge("minecraft:dark_oak_slab").body(Mat.of("minecraft:dark_oak_planks"))
                .gablePost(null).overhang(1), Roof.hip(cx - 3, cz - 3, cx + 3, cz + 3, G + 18));
        fill(cx - 3, G + 1, cz - 2, cx - 3, G + 12, cz - 2, "minecraft:ladder[facing=west]");
        set(cx - 3, G + 13, cz - 2, "minecraft:air");
        Connect.fix(s, cx - 5, G + 1, cz - 5, cx + 5, G + 14, cz + 5);
    }

    // ================================================================ voitures, obstacles, lampadaires

    static final String[] CAR_COLORS = {"red", "blue", "white", "light_gray", "black", "green", "yellow", "cyan", "orange"};

    void car(int x, int z, boolean alongX, boolean burned) {
        String color = CAR_COLORS[Math.floorMod(x * 31 + z * 17, CAR_COLORS.length)];
        int base = 0;
        for (int i = 0; i <= 4; i++)
            for (int j = 0; j <= 1; j++) base = Math.max(base, h(alongX ? x + i : x + j, alongX ? z + j : z + i));
        int y = G + 1 + base;
        for (int i = 0; i <= 4; i++)
            for (int j = 0; j <= 1; j++) {
                int bx = alongX ? x + i : x + j, bz = alongX ? z + j : z + i;
                boolean end = i == 0 || i == 4;
                String body = burned ? (end ? "minecraft:coal_block" : "minecraft:blackstone") : "minecraft:" + color + "_concrete";
                set(bx, y, bz, end && !burned ? "minecraft:" + color + "_terracotta" : body);
                if (i >= 1 && i <= 3) {
                    String cab = i == 2 ? (burned ? "minecraft:air" : "minecraft:" + color + "_concrete") : (burned ? "minecraft:air" : "minecraft:gray_stained_glass");
                    set(bx, y + 1, bz, cab);
                    if (!burned) set(bx, y + 2, bz, "minecraft:" + color + "_carpet");
                    else if (i == 2 && j == 0) set(bx, y + 1, bz, "minecraft:cobweb");
                } else {
                    set(bx, y + 1, bz, burned ? "minecraft:air" : "minecraft:" + color + "_carpet");
                }
            }
    }

    void bus(int x, int z, boolean alongX) {
        for (int i = 0; i <= 8; i++)
            for (int j = 0; j <= 2; j++) {
                int bx = alongX ? x + i : x + j, bz = alongX ? z + j : z + i;
                boolean end = i == 0 || i == 8;
                set(bx, G + 1, bz, end ? "minecraft:orange_terracotta" : "minecraft:yellow_concrete");
                set(bx, G + 2, bz, j == 1 && !end ? "minecraft:air" : (i % 2 == 0 || end ? "minecraft:yellow_concrete" : "minecraft:gray_stained_glass"));
                set(bx, G + 3, bz, j == 1 && !end ? "minecraft:air" : "minecraft:yellow_concrete");
                set(bx, G + 4, bz, "minecraft:smooth_stone_slab[type=bottom]");
            }
        // portes ouvertes (on traverse le bus)
        int mx = alongX ? x + 4 : x, mz = alongX ? z : z + 4;
        int mx2 = alongX ? x + 4 : x + 2, mz2 = alongX ? z + 2 : z + 4;
        fill(mx, G + 1, mz, mx, G + 3, mz, "minecraft:air");
        fill(mx2, G + 1, mz2, mx2, G + 3, mz2, "minecraft:air");
        for (int i = 1; i <= 7; i++) {
            int bx = alongX ? x + i : x + 1, bz = alongX ? z + 1 : z + i;
            set(bx, G + 1, bz, i == 4 ? "minecraft:air" : "minecraft:" + (i % 2 == 0 ? "red_carpet" : "gray_carpet"));
        }
    }

    /** Tas de gravats arrondi (couverture basse). */
    void debris(int cx, int cz, int r) {
        Palette p = Palette.of(cx * 31L + cz, "cobblestone", 50, "gravel", 25, "mossy_cobblestone", 15, "stone_bricks", 10).period(3);
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++) {
                double d = Math.hypot(x - cx, z - cz) + 0.6 * nz2.noise2(x / 2.0, z / 2.0);
                if (d > r + 0.3) continue;
                int hh = d < r * 0.45 ? 2 : 1;
                int y0 = top(x, z) + 1;
                for (int y = y0; y < y0 + hh; y++) setIfAir(x, y, z, p.at(x, y, z));
                if (d > r - 0.6) {
                    int y = y0 + hh - 1;
                    if (nm(x, y, z).equals("cobblestone")) set(x, y, z, slab("cobblestone_slab", false));
                }
            }
    }

    void barricade(int x1, int z1, int x2, int z2, double gaps, long seed) {
        barricade(x1, z1, x2, z2, gaps, seed, 1);
    }

    void barricade(int x1, int z1, int x2, int z2, double gaps, long seed, int minH) {
        int i = 0;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++, i++) {
                double hsh = Noise.hash(seed, x / 2, 0, z / 2);
                if (hsh < gaps) continue;
                int y0 = top(x, z) + 1;
                int hh = Math.max(minH, hsh > 0.7 ? 2 : 1);
                String b = switch ((int) (Noise.hash(seed, x / 3, 1, z / 3) * 5)) {
                    case 0 -> "minecraft:packed_mud";
                    case 1 -> "minecraft:spruce_planks";
                    case 2 -> "minecraft:barrel[facing=up]";
                    case 3 -> "minecraft:hay_block";
                    default -> "minecraft:stripped_spruce_log[axis=x]";
                };
                for (int y = y0; y < y0 + hh; y++) set(x, y, z, b);
                if (hh == 1 && hsh > 0.55) set(x, y0 + 1, z, "minecraft:dark_oak_fence");
            }
    }

    /** Obstacles dans les rues : coupent les longues lignes de vue en gardant toujours une voie libre. */
    void streetObstacles() {
        // croix centrale : alternance des voies
        int[] at = {-72, -65, -59, -40, -34, -28, -21, -15, -10, 10, 15, 21, 28, 34, 40, 59, 65, 72};
        for (int k = 0; k < at.length; k++) {
            int a = at[k];
            boolean north = k % 2 == 0;
            int type = k % 4;
            for (int pass = 0; pass < 2; pass++) {
                boolean ew = pass == 0;
                int b0 = north ? -3 : 1;
                int x = ew ? a : b0, z = ew ? b0 : a;
                switch (type) {
                    case 0 -> car(ew ? a - 2 : b0, ew ? b0 : a - 2, ew, (a & 1) == 0);
                    case 1 -> barricade(ew ? a : b0, ew ? b0 : a, ew ? a : b0 + 2, ew ? b0 + 2 : a, 0.2, a * 7L + pass);
                    case 2 -> debris(ew ? a : b0 + 1, ew ? b0 + 1 : a, 2);
                    default -> {
                        if (Math.abs(a) > 55) bus(ew ? a - 4 : b0 - (north ? 0 : 0), ew ? (north ? -3 : 1) : a - 4, ew);
                        else car(x, z, ew, false);
                    }
                }
            }
        }
        // boulevard
        for (int side = 0; side < 4; side++)
            for (int t = -42; t <= 42; t += 10) {
                if (Math.abs(t) <= 6) continue;
                boolean inner = Math.floorMod(t / 10 + side, 2) == 0;
                int a = inner ? 48 : 50;
                int[] c = cell(side, t, a);
                int kind = Math.floorMod(t + side * 3, 3);
                boolean alongX = side < 2;
                if (kind == 0) car(c[0], c[1], alongX, (t & 1) == 0);
                else if (kind == 1) {
                    int[] c2 = cell(side, t, a + 2);
                    barricade(c[0], c[1], c2[0], c2[1], 0.15, t * 13L + side);
                } else debris(c[0], c[1], 1);
            }
        // anciennes limites de la ville : barricades percees
        barricade(-44, -5, -43, 5, 0.5, 1);
        barricade(43, -5, 44, 5, 0.5, 2);
        barricade(-5, -44, 5, -43, 0.5, 3);
        barricade(-5, 43, 5, 44, 0.5, 4);
        // bouts des rues contre les limites : fermes (bus + conteneurs + sacs)
        bus(-4, -77 + 0, true);
        container(-4, 74, true, "gray", 0);
        container(-WALL + 1, -3, false, "red", 0);
        container(WALL - 3, -3, false, "blue", 0);
        barricade(-WALL + 1, -5, -WALL + 2, -4, 0.0, 5, 2);
        barricade(-WALL + 1, 4, -WALL + 2, 5, 0.0, 5, 2);
        barricade(WALL - 2, -5, WALL - 1, -4, 0.0, 6, 2);
        barricade(WALL - 2, 4, WALL - 1, 5, 0.0, 6, 2);
        barricade(-5, WALL - 1, 5, WALL - 1, 0.0, 7, 2);
        barricade(-5, -WALL + 4, 5, -WALL + 4, 0.0, 8, 2);
    }

    /** Ruelles de service : poubelles, palettes, caisses, portillons, en quinconce (une voie de 2 reste libre). */
    void alleyClutter() {
        for (int side = 0; side < 4; side++)
            for (int t = -52; t <= 52; t += 7 + (int) (Noise.hash(150, t, side, 0) * 5)) {
                if (Math.abs(t) <= 7) continue;
                int a = Math.floorMod(t + side, 2) == 0 ? 65 : 67;
                int[] c = cell(side, t, a), c2 = cell(side, t + 1, a);
                if (!air(c[0], G + 1, c[1]) || !air(c2[0], G + 1, c2[1])) continue;
                switch ((int) (Noise.hash(151, t, side, 0) * 4)) {
                    case 0 -> {
                        // benne a ordures
                        set(c[0], G + 1, c[1], "minecraft:green_terracotta");
                        set(c2[0], G + 1, c2[1], "minecraft:green_terracotta");
                        set(c[0], G + 2, c[1], "minecraft:dark_oak_trapdoor[half=bottom]");
                        // couvercle ferme (ouvert, il restait debout au bord de la case sans rien derriere)
                        set(c2[0], G + 2, c2[1], "minecraft:dark_oak_trapdoor[half=bottom]");
                    }
                    case 1 -> {
                        set(c[0], G + 1, c[1], "minecraft:barrel[facing=up]");
                        set(c[0], G + 2, c[1], "minecraft:barrel[facing=up]");
                        set(c2[0], G + 1, c2[1], "minecraft:composter");
                    }
                    case 2 -> {
                        set(c[0], G + 1, c[1], "minecraft:spruce_slab[type=bottom]");
                        set(c2[0], G + 1, c2[1], "minecraft:spruce_planks");
                        set(c2[0], G + 2, c2[1], "minecraft:spruce_slab[type=bottom]");
                    }
                    default -> {
                        // portillon ouvert entre deux poteaux
                        int[] e1 = cell(side, t, 65), e2 = cell(side, t, 67);
                        if (air(e1[0], G + 1, e1[1]) && air(e2[0], G + 1, e2[1])) {
                            fill(e1[0], G + 1, e1[1], e1[0], G + 2, e1[1], "minecraft:spruce_fence");
                            fill(e2[0], G + 1, e2[1], e2[0], G + 2, e2[1], "minecraft:spruce_fence");
                            int[] mid = cell(side, t, 66);
                            Dir along = side < 2 ? Dir.EAST : Dir.SOUTH;
                            set(mid[0], G + 1, mid[1], "minecraft:spruce_fence_gate[open=true,facing=" + along.id() + "]");
                        }
                    }
                }
            }
    }

    void lamp(int x, int z, Dir arm, boolean on) {
        int y = top(x, z) + 1;
        if (!air(x, y, z)) return;
        set(x, y, z, "minecraft:polished_blackstone");
        fill(x, y + 1, z, x, y + 3, z, "minecraft:polished_blackstone_wall");
        set(x, y + 4, z, "minecraft:polished_blackstone");
        set(x + arm.dx, y + 4, z + arm.dz, stairs("polished_blackstone_stairs", arm.opposite(), true));
        if (on) set(x + arm.dx, y + 3, z + arm.dz, "minecraft:lantern[hanging=true]");
        else set(x + arm.dx, y + 3, z + arm.dz, "minecraft:iron_chain");
    }

    void lamps() {
        for (int a = -73; a <= 73; a += 12) {
            if (Math.abs(a) <= 6 || (Math.abs(a) >= 45 && Math.abs(a) <= 55)) continue;
            boolean on = Noise.hash(101, a, 0, 0) < 0.6;
            lamp(a, -5, Dir.SOUTH, on);
            lamp(a + 6 > 76 ? a - 6 : a + 6, 5, Dir.NORTH, Noise.hash(102, a, 0, 0) < 0.6);
            lamp(-5, a, Dir.EAST, Noise.hash(103, a, 0, 0) < 0.6);
            lamp(5, a + 6 > 76 ? a - 6 : a + 6, Dir.WEST, Noise.hash(104, a, 0, 0) < 0.6);
        }
        for (int t = -44; t <= 44; t += 11) {
            if (Math.abs(t) <= 6) continue;
            lamp(t, -47, Dir.NORTH, Noise.hash(105, t, 0, 0) < 0.6);
            lamp(t, 47, Dir.SOUTH, Noise.hash(106, t, 0, 0) < 0.6);
            lamp(-47, t, Dir.WEST, Noise.hash(107, t, 0, 0) < 0.6);
            lamp(47, t, Dir.EAST, Noise.hash(108, t, 0, 0) < 0.6);
        }
        lamp(36, 23, Dir.SOUTH, true);
        lamp(10, 23, Dir.SOUTH, false);
        lamp(17, -14, Dir.SOUTH, true);
        lamp(33, -14, Dir.SOUTH, false);
        lamp(-29, 25, Dir.NORTH, true);
        lamp(-21, 19, Dir.SOUTH, false);
        lamp(-10, 29, Dir.SOUTH, true);
    }

    /** Arbres d'alignement sur le trottoir exterieur du boulevard (54..55). */
    void streetTrees() {
        long sd = 900;
        for (int side = 0; side < 4; side++)
            for (int t = -50; t <= 50; t += 15) {
                if (Math.abs(t) <= 8) continue;
                int[] c = cell(side, t + (side % 2) * 4, 54);
                if (!air(c[0], G + 1, c[1]) || !air(c[0], G + 3, c[1])) continue;
                s.set(c[0], G, c[1], "minecraft:coarse_dirt");
                if (sd % 3 == 0) deadTree(c[0], G + 1, c[1], 5);
                else new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:dark_oak_leaves").height(5).crown(2.2).roots(false).place(s, c[0], G + 1, c[1], sd);
                sd += 53;
            }
    }

    // ================================================================ limites, alentours, hangar

    /** Limites naturelles (palissades, haies, gravats) devant les barrieres invisibles, qui ne servent que de garde-fou. */
    void boundary() {
        for (int side = 0; side < 4; side++)
            for (int t = -77; t <= 77; t++) {
                int[] c = cell(side, t, 77);
                int x = c[0], z = c[1];
                int y = top(x, z) + 1;
                double seg = nz2.uniform2(t / 18.0 + side * 50, 3.7);
                int type = seg < 0.4 ? 0 : seg < 0.7 ? 1 : 2;
                if (!air(x, y, z)) continue;
                if (type == 0) {
                    // palissade de chantier : planches entre des poteaux epaules par une jambe de force, trouee par endroits
                    boolean post = Math.floorMod(t, 4) == 0;
                    int hh = Noise.hash(111, t / 3, side, 0) < 0.15 ? 2 : 3;
                    for (int yy = y; yy < y + hh; yy++)
                        set(x, yy, z, post ? "minecraft:stripped_spruce_log" : (yy == y + 1 && Math.floorMod(t, 8) < 4 ? "minecraft:white_concrete" : "minecraft:spruce_planks"));
                    if (post) {
                        int[] c2 = cell(side, t, 76);
                        int y2 = top(c2[0], c2[1]) + 1;
                        Dir in = side == 0 ? Dir.SOUTH : side == 1 ? Dir.NORTH : side == 2 ? Dir.EAST : Dir.WEST;
                        if (air(c2[0], y2, c2[1])) {
                            set(c2[0], y2, c2[1], "minecraft:stripped_spruce_log");
                            set(c2[0], y2 + 1, c2[1], stairs("spruce_stairs", in.opposite(), false));
                        }
                    }
                } else if (type == 1) {
                    fill(x, y, z, x, y + 2, z, "minecraft:azalea_leaves[persistent=true]");
                    int[] c2 = cell(side, t, 76);
                    int y2 = top(c2[0], c2[1]) + 1;
                    if (air(c2[0], y2, c2[1]) && Noise.hash(112, t, side, 0) < 0.6) fill(c2[0], y2, c2[1], c2[0], y2 + 1, c2[1], "minecraft:oak_leaves[persistent=true]");
                } else {
                    // gravats et sacs de sable, en redans (jamais un pan droit)
                    int a = Math.floorMod(t, 6) < 3 ? 77 : 76;
                    int[] c3 = cell(side, t, a);
                    int y3 = top(c3[0], c3[1]) + 1;
                    if (!air(c3[0], y3, c3[1])) continue;
                    set(c3[0], y3, c3[1], Noise.hash(113, t / 2, side, 0) < 0.5 ? "minecraft:packed_mud" : "minecraft:cobblestone");
                    set(c3[0], y3 + 1, c3[1], Noise.hash(114, t / 2, side, 0) < 0.5 ? "minecraft:packed_mud" : "minecraft:mossy_cobblestone");
                    if (Noise.hash(115, t, side, 0) < 0.5) set(c3[0], y3 + 2, c3[1], slab("cobblestone_slab", false));
                    if (a == 76) {
                        int[] c4 = cell(side, t, 77);
                        int y4 = top(c4[0], c4[1]) + 1;
                        if (air(c4[0], y4, c4[1])) set(c4[0], y4, c4[1], "minecraft:cobblestone");
                    }
                }
            }
        // barrieres invisibles (garde-fou), seulement au-dessus du terrain
        for (int a = -WALL; a <= WALL; a++)
            for (int[] c : new int[][]{{a, -WALL}, {a, WALL}, {-WALL, a}, {WALL, a}})
                for (int y = top(c[0], c[1]) + 1; y <= G + 40; y++) if (air(c[0], y, c[1])) set(c[0], y, c[1], "minecraft:barrier");
        Connect.fix(s, -WALL, G, -WALL, WALL, G + 14, WALL);
    }

    void outskirts() {
        // foret dense au-dela des limites (vue de loin) ; pas sur la route ni le hangar
        SplittableRandom r = new SplittableRandom(120);
        for (int x = -EXT + 2; x <= EXT - 2; x += 4)
            for (int z = -EXT + 2; z <= HZ2 + 14; z += 4) {
                int jx = x + r.nextInt(3) - 1, jz = z + r.nextInt(3) - 1;
                if (Math.abs(jx) <= WALL + 3 && Math.abs(jz) <= WALL + 3) continue;
                if (Math.abs(jx) <= 16 && jz >= WALL) continue;
                int y = top(jx, jz) + 1;
                if (!air(jx, y, jz)) continue;
                double q = r.nextDouble();
                if (q < 0.3) deadTree(jx, y, jz, 4 + r.nextInt(4));
                else if (q < 0.65) new Tree("minecraft:spruce_log", "minecraft:spruce_leaves", null).height(7 + r.nextInt(4)).crown(2.4).roots(false)
                        .place(s, jx, y, jz, r.nextLong());
                else new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:dark_oak_leaves").height(6 + r.nextInt(3)).crown(2.8).roots(false)
                        .place(s, jx, y, jz, r.nextLong());
            }
    }

    void hangar() {
        int x1 = -12, x2 = 12, z1 = HZ1, z2 = HZ2;
        int o = HZ1 - 60;
        Palette floorP = Palette.of(131, "polished_andesite", 80, "andesite", 20).period(5);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) s.set(x, G, z, floorP.at(x, G, z));
        Palette wallP = Palette.of(132, "light_gray_concrete", 85, "gray_concrete", 15).period(6);
        for (int x = x1; x <= x2; x++) {
            int hh = (int) Math.round(4 * Math.sqrt(Math.max(0, 1 - (x / 13.0) * (x / 13.0))));
            int roofY = G + 8 + hh;
            for (int z = z1; z <= z2; z++) {
                set(x, roofY, z, Math.floorMod(z, 4) == 0 ? "minecraft:weathered_cut_copper" : "minecraft:waxed_weathered_cut_copper");
                boolean gable = z == z1 || z == z2;
                boolean side = x == x1 || x == x2;
                if (gable || side) {
                    boolean post = side ? Math.floorMod(z - z1, 4) == 0 : Math.floorMod(x - x1, 4) == 0;
                    boolean corner = side && gable;
                    for (int y = G + 1; y < roofY; y++)
                        set(x, y, z, corner || post ? "minecraft:polished_blackstone" : y == G + 1 ? "minecraft:polished_blackstone_bricks" : wallP.at(x, y, z));
                    if (post && !corner) {
                        int ox = side ? (x == x1 ? -1 : 1) : 0, oz = gable ? (z == z1 ? -1 : 1) : 0;
                        fill(x + ox, G + 1, z + oz, x + ox, G + 4, z + oz, "minecraft:polished_blackstone_wall");
                        set(x + ox, G + 5, z + oz, stairs("polished_blackstone_stairs", side ? (x == x1 ? Dir.EAST : Dir.WEST) : (z == z1 ? Dir.SOUTH : Dir.NORTH), false));
                    }
                }
            }
        }
        // debord du toit sur les cotes
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            set(x1 - 1, G + 8, z, "minecraft:cut_copper_stairs[facing=east,half=bottom]");
            set(x2 + 1, G + 8, z, "minecraft:cut_copper_stairs[facing=west,half=bottom]");
        }
        // grande porte condamnee au sud
        fill(-3, G + 1, z2, 3, G + 5, z2, "minecraft:spruce_planks");
        fill(-3, G + 2, z2, 3, G + 2, z2, "minecraft:stripped_dark_oak_log[axis=x]");
        fill(-3, G + 4, z2, 3, G + 4, z2, "minecraft:stripped_dark_oak_log[axis=x]");
        // fenetres hautes a barreaux
        for (int z = z1 + 2; z <= z2 - 2; z += 4) {
            set(x1, G + 5, z, Noise.hash(133, z, 0, 0) < 0.4 ? "minecraft:spruce_planks" : "minecraft:iron_bars[north=true,south=true]");
            set(x2, G + 5, z, Noise.hash(134, z, 0, 0) < 0.4 ? "minecraft:spruce_planks" : "minecraft:iron_bars[north=true,south=true]");
        }
        int[][] crates = {{-10, 62}, {-9, 62}, {-10, 63}, {9, 75}, {10, 75}, {10, 76}, {10, 62}, {9, 62}};
        for (int[] c : crates) {
            int cz = c[1] + o;
            set(c[0], G + 1, cz, (c[0] + c[1]) % 2 == 0 ? "minecraft:barrel[facing=up]" : "minecraft:spruce_planks");
            if ((c[0] * 3 + c[1]) % 5 < 3) set(c[0], G + 2, cz, (c[0] + c[1]) % 3 == 0 ? "minecraft:hay_block" : "minecraft:barrel[facing=up]");
        }
        fill(-10, G + 1, 74 + o, -10, G + 1, 76 + o, "minecraft:hay_block");
        fill(-9, G + 1, 76 + o, -8, G + 1, 76 + o, "minecraft:hay_block");
        for (int x = -6; x <= 6; x++) if (Math.abs(x) > 1) set(x, G + 1, 76 + o, "minecraft:packed_mud");
        car(7, 64 + o, false, false);
        set(-6, G + 1, 66 + o, "minecraft:cartography_table");
        set(-5, G + 1, 66 + o, "minecraft:cartography_table");
        set(-7, G + 1, 66 + o, "minecraft:spruce_stairs[facing=west]");
        set(-4, G + 1, 66 + o, "minecraft:spruce_stairs[facing=east]");
        set(0, G + 1, 74 + o, "minecraft:campfire[lit=true]");
        int[][] lanterns = {{-6, 64}, {6, 70}, {-6, 72}, {0, 66}};
        for (int[] l : lanterns) {
            int lz = l[1] + o;
            int hh = (int) Math.round(4 * Math.sqrt(Math.max(0, 1 - (l[0] / 13.0) * (l[0] / 13.0))));
            fill(l[0], G + 7, lz, l[0], G + 7 + hh, lz, "minecraft:iron_chain");
            set(l[0], G + 6, lz, "minecraft:lantern[hanging=true]");
        }
        // tableau noir pour les regles (texte affiche par le jeu en z HZ1 + 2.05)
        fill(-4, G + 2, z1 + 1, 4, G + 6, z1 + 1, "minecraft:black_concrete");
        fill(-5, G + 2, z1 + 1, -5, G + 6, z1 + 1, "minecraft:dark_oak_log");
        fill(5, G + 2, z1 + 1, 5, G + 6, z1 + 1, "minecraft:dark_oak_log");
        fill(-5, G + 1, z1 + 1, 5, G + 1, z1 + 1, "minecraft:dark_oak_slab[type=top]");
        fill(-5, G + 7, z1 + 1, 5, G + 7, z1 + 1, "minecraft:dark_oak_slab[type=bottom]");
    }

    /** Vegetation en grappes (bruit) sur le sol libre ; lierre sur les ruines. */
    void decorate() {
        for (int x = -(WALL - 1); x <= WALL - 1; x++)
            for (int z = -(WALL - 1); z <= WALL - 1; z++) {
                int y = top(x, z);
                String n = nm(x, y, z);
                if (!air(x, y + 1, z)) continue;
                double u = nz2.uniform2(x / 4.0 + 100, z / 4.0);
                double v = Noise.hash(141, x, 0, z);
                if (n.equals("grass_block") || n.equals("moss_block")) {
                    if (u > 0.44 && v < 0.62) set(x, y + 1, z, "minecraft:short_grass");
                    else if (u > 0.85 && v < 0.75) set(x, y + 1, z, "minecraft:fern");
                    else if (u < 0.08 && v < 0.5) set(x, y + 1, z, "minecraft:poppy");
                } else if (n.equals("coarse_dirt") || n.equals("podzol") || n.equals("gravel")) {
                    if (u > 0.6 && v < 0.25) set(x, y + 1, z, "minecraft:dead_bush");
                }
            }
    }

    /** Lumiere invisible faible au-dessus des rues et places (complement des lanternes visibles). */
    void lights() {
        for (int x = -(WALL - 2); x <= WALL - 2; x += 7)
            for (int z = -(WALL - 2); z <= WALL - 2; z += 7) {
                int y = top(x, z);
                boolean open = true;
                for (int yy = y + 1; yy <= y + 4; yy++) if (!air(x, yy, z)) open = false;
                if (open) set(x, y + 4, z, "minecraft:light[level=7]");
            }
    }
}
