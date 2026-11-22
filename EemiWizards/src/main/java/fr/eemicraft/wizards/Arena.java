package fr.eemicraft.wizards;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Maps de TNT Wizards, construites par le plugin dans le même monde, l'une à côté de l'autre (décalage en z).
 * Toutes suivent le même plan « vallée des sorciers », symétrique en x (Rouge à l'ouest, Bleu à l'est) :
 *
 * - Bases aux extrémités (|x| baseFront..baseBack) : cour fermée par des remparts de 4 blocs, porte de verre
 *   de l'équipe ouverte après 5 s, tour de sorcier au toit de la couleur de l'équipe derrière.
 * - Points de capture (cercle de rayon 4, balise dont le faisceau prend la couleur du propriétaire) :
 *   autels près des bases, cristaux sur des collines au centre (+ un point au centre sur la grande map).
 * - Décor (ruines, murets, buttes, bibliothèques, arbres, lanternes) placé à l'échelle k de la map.
 * - Barrières invisibles autour et plafond de barrières à floor-y + 26.
 *
 * « vallee » : île de 89 × 59 (k = 1, 4 points). « grande » : île de 129 × 85 (k = 1,45, 5 points), arbres
 * en cerisiers, décalée de 400 blocs en z. Salle d'attente commune : 15 × 15 en (0, lobby-y, 70).
 */
final class Arena {

    /** Version de la construction : si elle change, les maps sont reconstruites au démarrage. */
    static final int VERSION = 2;

    static final int CEILING = 26;
    static final int LOBBY_Z = 70;
    static final int RING = 4;

    /** Un point de capture : lettre, nom, centre (coordonnées de la map, sans le décalage), hauteur du sol. */
    record PointDef(String key, String name, int x, int z, int h) {}

    /** Plan d'une map. oz = décalage en z dans le monde, k = échelle du décor et du relief. */
    record Layout(String id, String name, int oz, double k, int halfX, int halfZ, int baseFront, int baseBack,
                  int baseHalfZ, int gateHalf, String log, String leaves, String flowerLeaves, List<PointDef> points) {
        int wallX() {
            return halfX + 1;
        }

        int wallZ() {
            return halfZ + 1;
        }

        /** Milieu de la cour des bases (en |x|). */
        int baseMid() {
            return (baseFront + baseBack) / 2;
        }

        int tower() {
            return baseBack + 3;
        }
    }

    static final List<Layout> LAYOUTS = List.of(
            new Layout("vallee", "Vallée des sorciers", 0, 1.0, 44, 29, 30, 38, 7, 2,
                    "dark_oak_log", "azalea_leaves[persistent=true]", "flowering_azalea_leaves[persistent=true]", List.of(
                    new PointDef("A", "Autel Ouest", -20, 0, 0),
                    new PointDef("B", "Cristal Nord", 0, -14, 3),
                    new PointDef("C", "Cristal Sud", 0, 14, 3),
                    new PointDef("D", "Autel Est", 20, 0, 0))),
            new Layout("grande", "Grande Vallée", 400, 1.45, 64, 42, 45, 55, 9, 3,
                    "cherry_log", "cherry_leaves[persistent=true]", "flowering_azalea_leaves[persistent=true]", List.of(
                    new PointDef("A", "Autel Ouest", -29, 0, 0),
                    new PointDef("B", "Cristal Nord", 0, -20, 3),
                    new PointDef("C", "Cristal Sud", 0, 20, 3),
                    new PointDef("D", "Autel Est", 29, 0, 0),
                    new PointDef("E", "Cœur de la vallée", 0, 0, 0))));

    static Layout layout(String id) {
        for (Layout l : LAYOUTS) if (l.id().equalsIgnoreCase(id)) return l;
        return null;
    }

    final Layout lay;
    private final World world;
    private final int F, L;
    private final double k;
    private final Logger log;
    private final Map<String, BlockData> cache = new HashMap<>();
    private int placed;

    Arena(World world, int floorY, int lobbyY, Logger log, Layout lay) {
        this.world = world;
        this.F = floorY;
        this.L = lobbyY;
        this.log = log;
        this.lay = lay;
        this.k = lay.k();
    }

    /** Une longueur du plan d'origine, à l'échelle de la map. */
    private int s(double v) {
        return (int) Math.round(v * k);
    }

    // ------------------------------------------------------------------ relief

    /** Bosse : hauteur h jusqu'à flat blocs du centre, puis descente en cloche (largeur s). */
    private static double bump(double ax, double az, double cx, double cz, double h, double flat, double s) {
        double d = Math.hypot(ax - cx, az - cz);
        double k = Math.max(0, d - flat) / s;
        return h * Math.exp(-k * k);
    }

    /** Hauteur du sol (0 = floor-y) en x, z de la map : symétrique en x et en z. */
    int height(int x, int z) {
        double ax = Math.abs(x), az = Math.abs(z);
        // bases, autels et centre restent plats
        if (ax >= lay.baseFront() - 2 && az <= lay.baseHalfZ() + 3) return 0;
        if (Math.hypot(ax - s(20), az) <= 6.5 * k) return 0;
        if (Math.hypot(ax, az) <= 3.5 * k + (centerPoint() ? 2 : 0)) return 0;
        double dx = ax / k, dz = az / k, v = 0;
        v = Math.max(v, bump(dx, dz, 0, 14, 3, 5.5, 5));   // collines des cristaux
        v = Math.max(v, bump(dx, dz, 12, 9, 2, 1, 3.5));   // buttes de couverture
        v = Math.max(v, bump(dx, dz, 26, 19, 3, 1.5, 4)); // collines des coins
        v = Math.max(v, bump(dx, dz, 12, 23, 2, 1, 3.5));
        return (int) Math.round(v);
    }

    private boolean centerPoint() {
        for (PointDef p : lay.points()) if (p.x() == 0 && p.z() == 0) return true;
        return false;
    }

    /** y du bloc de surface (on marche à topY + 1). */
    int topY(int x, int z) {
        return F - 1 + height(x, z);
    }

    // ------------------------------------------------------------------ pose

    private static long hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ 0x5DEECE66DL;
        h ^= (h >>> 17);
        h *= 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 29);
    }

    /** Nombre pseudo-aléatoire stable dans [0, 1) (même valeur des deux côtés : on passe |x|). */
    private static double rnd(int x, int y, int z) {
        return ((hash(Math.abs(x), y, z) >>> 11) & 0xFFFFF) / (double) 0x100000;
    }

    BlockData data(String state) {
        if (cache.containsKey(state)) return cache.get(state);
        BlockData d = null;
        try {
            d = Bukkit.createBlockData(state.contains(":") ? state : "minecraft:" + state);
        } catch (IllegalArgumentException ex) {
            log.warning("Bloc invalide ignore : " + state);
        }
        cache.put(state, d);
        return d;
    }

    /** Pose en coordonnées de la map (le décalage en z est ajouté ici). */
    void put(int x, int y, int z, String state) {
        BlockData d = data(state);
        if (d == null) return;
        world.getBlockAt(x, y, z + lay.oz()).setBlockData(d, false);
        placed++;
    }

    void put(int x, int y, int z, Material m) {
        world.getBlockAt(x, y, z + lay.oz()).setType(m, false);
        placed++;
    }

    private boolean air(int x, int y, int z) {
        return world.getBlockAt(x, y, z + lay.oz()).getType().isAir();
    }

    // ------------------------------------------------------------------ construction

    /** Construit la map (et la salle d'attente si withLobby) ; rend le nombre de blocs posés. */
    int build(boolean withLobby) {
        placed = 0;
        int wx = lay.wallX() + 2, wz = lay.wallZ() + 2;
        for (int cx = (-wx) >> 4; cx <= wx >> 4; cx++)
            for (int cz = (lay.oz() - wz) >> 4; cz <= (lay.oz() + wz) >> 4; cz++) world.getChunkAt(cx, cz).load(true);

        // 1) on vide le volume
        for (int x = -wx; x <= wx; x++)
            for (int z = -wz; z <= wz; z++)
                for (int y = F - 22; y <= F + CEILING + 2; y++) {
                    Block b = world.getBlockAt(x, y, z + lay.oz());
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }

        // 2) île, structures, décor
        safe("terrain", this::terrain);
        safe("bases", () -> { base(-1); base(1); });
        safe("points", this::points);
        safe("ruines", () -> { ruins(-1); ruins(1); });
        safe("cristaux", this::crystals);
        safe("centre", this::center);
        safe("decor", () -> { decor(-1); decor(1); });
        safe("plantes", this::plants);
        safe("limites", this::limits);
        if (withLobby) {
            for (int cx = -1; cx <= 0; cx++)
                for (int cz = (LOBBY_Z - 10) >> 4; cz <= (LOBBY_Z + 10) >> 4; cz++) world.getChunkAt(cx, cz).load(true);
            for (int x = -9; x <= 9; x++)
                for (int z = LOBBY_Z - 9; z <= LOBBY_Z + 9; z++)
                    for (int y = L - 1; y <= L + 7; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
            safe("salle d'attente", this::lobby);
        }
        return placed;
    }

    private void safe(String step, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            log.log(java.util.logging.Level.WARNING, "Construction de " + lay.id() + " (" + step + ") interrompue", ex);
        }
    }

    /** Distance de (px, pz) au segment (ax, az)-(bx, bz). */
    private static double segDist(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double t = ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz);
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
    }

    boolean path(int x, int z) {
        double ax = Math.abs(x), az = Math.abs(z);
        return segDist(ax, az, s(20), 0, lay.baseFront(), 0) <= 1.6
                || segDist(ax, az, s(20), 0, s(4), s(11)) <= 1.3
                || segDist(ax, az, 0, 3.5 * k, 0, s(8.5)) <= 1.2;
    }

    private void terrain() {
        for (int x = -lay.halfX(); x <= lay.halfX(); x++)
            for (int z = -lay.halfZ(); z <= lay.halfZ(); z++) {
                int edge = Math.min(lay.halfX() - Math.abs(x), lay.halfZ() - Math.abs(z));
                int top = topY(x, z);
                int bottom = F - 3 - Math.min(13, (int) (edge * 0.7)) - (int) (rnd(x, 1, z) * 3);
                for (int y = bottom; y <= top; y++) {
                    String m;
                    if (y == top) {
                        double r = rnd(x, 2, z);
                        if (path(x, z)) m = "dirt_path";
                        else if (r < 0.10) m = "moss_block";
                        else if (r < 0.14) m = "podzol";
                        else if (r < 0.17) m = "coarse_dirt";
                        else m = "grass_block";
                    } else if (y >= top - 2) {
                        m = edge <= 0 && rnd(x, y, z) < 0.5 ? "mossy_cobblestone" : "dirt";
                    } else {
                        double r = rnd(x, y, z);
                        if (y < bottom + 3) m = r < 0.5 ? "deepslate" : "cobbled_deepslate";
                        else if (r < 0.15) m = "tuff";
                        else if (r < 0.22) m = "andesite";
                        else if (r < 0.25) m = "amethyst_block";
                        else m = "stone";
                    }
                    put(x, y, z, m);
                }
                // stalactites d'améthyste sous l'île
                if (rnd(x, 3, z) < 0.04) put(x, bottom - 1, z, "pointed_dripstone[vertical_direction=down,thickness=tip]");
            }
    }

    /** Base d'une équipe (sx = -1 Rouge, +1 Bleu) : cour, remparts, tour du sorcier. */
    private void base(int sx) {
        String dye = Side.ofSign(sx).dye;
        int front = lay.baseFront(), back = lay.baseBack(), hz = lay.baseHalfZ(), mid = lay.baseMid();
        // cour
        for (int ax = front; ax <= back; ax++)
            for (int z = -hz; z <= hz; z++) put(sx * ax, F - 1, z, (ax + z) % 2 == 0 ? "smooth_quartz" : dye + "_terracotta");
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) put(sx * mid + dx, F - 1, dz, dx == 0 && dz == 0 ? "sea_lantern" : dye + "_concrete");
        // remparts (4 blocs), bande de laine de l'équipe, créneaux
        for (int ax = front; ax <= back; ax++)
            for (int z = -hz; z <= hz; z++) {
                boolean frontWall = ax == front && Math.abs(z) > lay.gateHalf();
                boolean lintel = ax == front && Math.abs(z) <= lay.gateHalf();
                boolean wall = frontWall || ax == back || Math.abs(z) == hz;
                int x = sx * ax;
                if (lintel) {
                    put(x, F + 3, z, z == 0 ? "chiseled_deepslate" : "deepslate_bricks");
                    put(x, F + 4, z, "deepslate_bricks");
                    continue;
                }
                if (!wall) continue;
                boolean pillar = (ax == front || ax == back) && Math.abs(z) == hz;
                for (int y = F; y <= F + 3; y++)
                    put(x, y, z, pillar ? "polished_blackstone_bricks" : y == F + 2 ? dye + "_wool" : "deepslate_bricks");
                if ((ax + z) % 2 == 0 || pillar) put(x, F + 4, z, pillar ? "polished_blackstone_bricks" : "deepslate_bricks");
                if (pillar) put(x, F + 5, z, "soul_lantern");
            }
        // tour du sorcier (rayon 3) derrière la cour
        int tx = sx * lay.tower();
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 3.4) continue;
                for (int y = F - 1; y <= F + 13; y++) {
                    if (d < 2.3 && y > F - 1 && y < F + 13) continue; // creuse
                    boolean window = d >= 2.3 && (dx == 0 || dz == 0) && (y == F + 6 || y == F + 7 || y == F + 11);
                    put(tx + dx, y, dz, window ? "purple_stained_glass" : d >= 2.3 && y % 4 == 0 ? "polished_deepslate" : "deepslate_tiles");
                }
            }
        // toit conique de la couleur de l'équipe
        for (int kk = 0; kk <= 6; kk++) {
            double r = 4.3 - 0.65 * kk;
            for (int dx = -5; dx <= 5; dx++)
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d <= r && d > r - 1.4) put(tx + dx, F + 14 + kk, dz, dye + "_terracotta");
                }
        }
        put(tx, F + 20, 0, dye + "_terracotta");
        put(tx, F + 21, 0, "end_rod[facing=up]");
        // bande de laine de l'équipe sur la façade de la tour
        for (int y = F + 8; y <= F + 10; y++) put(tx - sx * 3, y, 0, dye + "_wool");
        // lanternes et mobilier de la cour
        int lz = hz - 2;
        put(sx * (front + 2), F, lz, "lantern");
        put(sx * (front + 2), F, -lz, "lantern");
        put(sx * (back - 2), F, lz, "enchanting_table");
        put(sx * (back - 2), F, -lz, "bookshelf");
        put(sx * (back - 2), F + 1, -lz, "purple_candle[candles=3,lit=true]");
    }

    /** Porte de verre d'une base (fermée) ou ouverte (air). */
    void gate(int sx, boolean closed) {
        String dye = Side.ofSign(sx).dye;
        for (int z = -lay.gateHalf(); z <= lay.gateHalf(); z++)
            for (int y = F; y <= F + 2; y++) {
                if (closed) put(sx * lay.baseFront(), y, z, dye + "_stained_glass");
                else put(sx * lay.baseFront(), y, z, Material.AIR);
            }
    }

    /** Cases de l'anneau d'un point (cercle de rayon 4 au sol), en coordonnées de la map. */
    static List<int[]> ring(PointDef p) {
        List<int[]> l = new ArrayList<>();
        for (int dx = -RING - 1; dx <= RING + 1; dx++)
            for (int dz = -RING - 1; dz <= RING + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d >= RING - 0.5 && d < RING + 0.5) l.add(new int[]{p.x() + dx, p.z() + dz});
            }
        return l;
    }

    /** Points de capture : sol de calcite, anneau de béton, balise sous une vitre au centre. */
    private void points() {
        for (PointDef p : lay.points()) {
            int top = F - 1 + p.h();
            for (int dx = -RING; dx <= RING; dx++)
                for (int dz = -RING; dz <= RING; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d >= RING - 0.5) continue;
                    put(p.x() + dx, top, p.z() + dz, (dx + dz) % 2 == 0 ? "calcite" : "polished_tuff");
                    // le sol du point est plein jusqu'au socle de la balise
                    for (int y = top - 3; y < top; y++) if (air(p.x() + dx, y, p.z() + dz)) put(p.x() + dx, y, p.z() + dz, "stone");
                }
            for (int[] c : ring(p)) put(c[0], top, c[1], "white_concrete");
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) put(p.x() + dx, top - 2, p.z() + dz, "iron_block");
            put(p.x(), top - 1, p.z(), "beacon");
            put(p.x(), top, p.z(), "white_stained_glass");
            // ciel dégagé au-dessus de la balise
            for (int y = top + 1; y <= F + CEILING - 1; y++) put(p.x(), y, p.z(), Material.AIR);
        }
    }

    /** Couleur d'un point (anneau + vitre de la balise) : null = neutre (blanc). */
    void paintPoint(PointDef p, Side owner) {
        String dye = owner == null ? "white" : owner.dye;
        int top = F - 1 + p.h();
        for (int[] c : ring(p)) put(c[0], top, c[1], dye + "_concrete");
        put(p.x(), top, p.z(), dye + "_stained_glass");
    }

    /** Pilier en ruine de hauteur h sur le sol (la colonne monte depuis le dessus de la surface). */
    private void pillar(int x, int z, int h, boolean lantern) {
        int y0 = topY(x, z) + 1;
        for (int y = y0; y < y0 + h; y++) {
            double r = rnd(x, y, z);
            put(x, y, z, y == y0 + h - 1 ? "chiseled_stone_bricks" : r < 0.35 ? "mossy_stone_bricks" : r < 0.55 ? "cracked_stone_bricks" : "stone_bricks");
        }
        if (lantern) put(x, y0 + h, z, "lantern");
    }

    /** Ruines autour de l'autel d'un camp + arche + muret de couverture vers le centre. */
    private void ruins(int sx) {
        int[] heights = {4, 2, 3, 3, 2, 4};
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(30 + 60 * i);
            int x = sx * (int) Math.round(s(20) + 7.5 * k * Math.cos(a));
            int z = (int) Math.round(7.5 * k * Math.sin(a));
            pillar(x, z, heights[i], heights[i] == 4);
            // gravats au pied
            int gx = x + (rnd(x, 7, z) < 0.5 ? 1 : -1);
            if (air(gx, topY(gx, z) + 1, z) && !path(gx, z)) put(gx, topY(gx, z) + 1, z, "mossy_cobblestone");
        }
        // arche brisée derrière l'autel (côté base)
        for (int z = -2; z <= 2; z++) {
            int x = sx * s(27);
            if (Math.abs(z) == 2) pillar(x, z, 4, false);
            else if (z != 0) put(x, F + 3, z, "stone_brick_slab[type=top]");
        }
        // murets de couverture entre l'autel et le centre (deux sur la grande map)
        int[] wall = {2, 3, 3, 3, 2};
        int[] zs = centerPoint() ? new int[]{-5, 5} : new int[]{0};
        for (int zc : zs)
            for (int i = 0; i < 5; i++) {
                int z = zc + i - 2;
                for (int y = F; y < F + wall[i]; y++)
                    put(sx * s(9), y + height(sx * s(9), z), z, rnd(9, y, z) < 0.4 ? "cracked_stone_bricks" : "mossy_stone_bricks");
            }
    }

    /** Flèches d'améthyste autour des points du centre. */
    private void crystals() {
        int[][] spots = {{5, -19}, {5, -9}, {5, 9}, {5, 19}, {-5, -19}, {-5, -9}, {-5, 9}, {-5, 19}};
        for (int[] sp : spots) {
            int x = s(sp[0]), z = s(sp[1]);
            int y0 = topY(x, z) + 1;
            int h = Math.abs(sp[1]) > 14 ? 4 : 3;
            for (int y = y0; y < y0 + h; y++) put(x, y, z, y == y0 ? "budding_amethyst" : "amethyst_block");
            put(x, y0 + h, z, "amethyst_cluster[facing=up]");
            int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : side) {
                int bx = x + d[0], bz = z + d[1];
                int by = topY(bx, bz) + 1;
                if (air(bx, by, bz) && rnd(bx, by, bz) < 0.6) put(bx, by, bz, "large_amethyst_bud[facing=up]");
            }
        }
    }

    /** Obélisque central (remplacé par un point de capture sur la grande map, entouré de 4 obélisques). */
    private void center() {
        if (centerPoint()) {
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = sx * 7, z = sz * 7, y0 = topY(x, z) + 1;
                    put(x, y0, z, "polished_blackstone_bricks");
                    for (int y = y0 + 1; y <= y0 + 4; y++) put(x, y, z, y == y0 + 4 ? "budding_amethyst" : "amethyst_block");
                    put(x, y0 + 5, z, "end_rod[facing=up]");
                }
            return;
        }
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) put(dx, F, dz, "polished_blackstone_bricks");
        for (int y = F + 1; y <= F + 7; y++) put(0, y, 0, y == F + 7 ? "budding_amethyst" : "amethyst_block");
        for (int y = F + 2; y <= F + 4; y++) {
            put(1, y, 0, "purple_stained_glass");
            put(-1, y, 0, "purple_stained_glass");
            put(0, y, 1, "purple_stained_glass");
            put(0, y, -1, "purple_stained_glass");
        }
        put(0, F + 8, 0, "end_rod[facing=up]");
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                int y0 = topY(sx * 3, sz * 3) + 1;
                put(sx * 3, y0, sz * 3, "obsidian");
                put(sx * 3, y0 + 1, sz * 3, "crying_obsidian");
                put(sx * 3, y0 + 2, sz * 3, "soul_lantern");
            }
    }

    private void tree(int x, int z) {
        int y0 = topY(x, z) + 1;
        for (int y = y0; y < y0 + 5; y++) put(x, y, z, lay.log());
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 2.0 + dz * dz);
                    int bx = x + dx, by = y0 + 5 + dy, bz = z + dz;
                    if (d > 2.9 || !air(bx, by, bz)) continue;
                    put(bx, by, bz, rnd(bx, by, bz) < 0.4 ? lay.flowerLeaves() : lay.leaves());
                }
    }

    private void bush(int x, int z) {
        int y0 = topY(x, z) + 1;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 1; dy++) {
                    if (dy == 1 && Math.abs(dx) + Math.abs(dz) == 2) continue;
                    int by = topY(x + dx, z + dz) + 1 + dy;
                    put(x + dx, by, z + dz, rnd(x + dx, by, z + dz) < 0.5 ? "flowering_azalea_leaves[persistent=true]" : "azalea_leaves[persistent=true]");
                }
        put(x, y0 + 2, z, "flowering_azalea_leaves[persistent=true]");
    }

    private void post(int x, int z) {
        int y0 = topY(x, z) + 1;
        put(x, y0, z, "stone_brick_wall");
        put(x, y0 + 1, z, "stone_brick_wall");
        put(x, y0 + 2, z, "lantern");
    }

    /** Décor d'un côté : arbres, buissons, bibliothèques en ruine, lanternes. */
    private void decor(int sx) {
        for (int sz = -1; sz <= 1; sz += 2) {
            tree(sx * s(33), sz * s(20));
            tree(sx * s(11), sz * s(23));
            bush(sx * s(12), sz * s(9));
            if (k > 1.2) {
                // grande map : plus d'arbres et de buissons pour couvrir les grands espaces
                tree(sx * s(38), sz * s(26));
                tree(sx * s(22), sz * s(14));
                bush(sx * s(26), sz * s(9));
                bush(sx * s(5), sz * s(25));
                post(sx * s(8), sz * s(16));
            }
            // bibliothèque en ruine (coin en L) avec table d'enchantement
            int bx = sx * s(21), bz = sz * s(21);
            for (int i = -2; i <= 2; i++) {
                int h1 = 2 - (Math.abs(i) == 2 ? 1 : 0);
                for (int dy = 1; dy <= h1; dy++) {
                    put(bx + i, topY(bx + i, bz + sz * 2) + dy, bz + sz * 2, "bookshelf");
                    put(bx + sx * 2, topY(bx + sx * 2, bz + i) + dy, bz + i, i == 0 && dy == 2 ? "chiseled_bookshelf" : "bookshelf");
                }
            }
            put(bx, topY(bx, bz) + 1, bz, "enchanting_table");
            put(bx + sx, topY(bx + sx, bz + sz * 2) + 3, bz + sz * 2, "purple_candle[candles=2,lit=true]");
            put(bx + sx * 2, topY(bx + sx * 2, bz - sz) + 3, bz - sz, "magenta_candle[candles=3,lit=true]");
            // lanternes le long des chemins
            post(sx * s(16), sz * s(5));
        }
    }

    /** Herbes et fleurs sur l'herbe libre (pas sur les chemins ni les points). */
    private void plants() {
        for (int x = -lay.halfX(); x <= lay.halfX(); x++)
            for (int z = -lay.halfZ(); z <= lay.halfZ(); z++) {
                int top = topY(x, z);
                if (world.getBlockAt(x, top, z + lay.oz()).getType() != Material.GRASS_BLOCK || !air(x, top + 1, z)) continue;
                double r = rnd(x, 9, z);
                if (r < 0.10) put(x, top + 1, z, "short_grass");
                else if (r < 0.12) put(x, top + 1, z, "fern");
                else if (r < 0.135) put(x, top + 1, z, "allium");
                else if (r < 0.15) put(x, top + 1, z, "blue_orchid");
                else if (r < 0.16) put(x, top + 1, z, k > 1.2 ? "pink_petals[flower_amount=3]" : "cornflower");
            }
    }

    /** Murs et plafond de barrières (invisibles). */
    private void limits() {
        int wx = lay.wallX(), wz = lay.wallZ();
        for (int x = -wx; x <= wx; x++)
            for (int z = -wz; z <= wz; z++) {
                boolean border = Math.abs(x) == wx || Math.abs(z) == wz;
                if (border) for (int y = F - 6; y < F + CEILING; y++) put(x, y, z, Material.BARRIER);
                put(x, F + CEILING, z, Material.BARRIER);
            }
    }

    /** Salle d'attente (sol en lobby-y, centre 0, LOBBY_Z), hors décalage. */
    private void lobby() {
        int z0 = LOBBY_Z - lay.oz();
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d <= 1) put(x, L, z0 + z, "amethyst_block");
                else if (d == 2) put(x, L, z0 + z, "sea_lantern");
                else if (d < 7) put(x, L, z0 + z, Math.floorMod(x + z, 2) == 0 ? "purple_concrete" : "white_concrete");
                else {
                    put(x, L, z0 + z, "polished_blackstone_bricks");
                    for (int y = L + 1; y <= L + 4; y++)
                        put(x, y, z0 + z, Math.abs(x) == 7 && Math.abs(z) == 7 ? "polished_blackstone_bricks"
                                : z == -7 && Math.abs(x) <= 3 ? (x < 0 ? "red_stained_glass" : x > 0 ? "blue_stained_glass" : "purple_stained_glass")
                                : "purple_stained_glass");
                }
                put(x, L + 5, z0 + z, Material.BARRIER);
            }
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                put(sx * 6, L + 1, z0 + sz * 6, "bookshelf");
                put(sx * 6, L + 2, z0 + sz * 6, "purple_candle[candles=4,lit=true]");
            }
    }
}
