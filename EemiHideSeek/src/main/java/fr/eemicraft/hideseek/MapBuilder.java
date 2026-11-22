package fr.eemicraft.hideseek;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plan de la map « Village » du cache-cache : toutes les poses de blocs sont d'abord calculées en mémoire
 * (la dernière pose d'une position l'emporte), puis appliquées par lots par le plugin.
 *
 * Disposition (sol en herbe y 64, pieds des joueurs en y 65, barrières en x/z = ±45, plafond y 96) :
 * - centre : place pavée (rayon 9) avec un puits couvert et 4 étals de marché ;
 * - routes en croix (x -2..2 et z -2..2), lampadaires ;
 * - nord-est : grand manoir à étage (salon-bibliothèque, cuisine, bureau, grenier) et jardin à fontaine ;
 * - nord-ouest : deux maisons meublées et un verger (ruches, mare, foin) ;
 * - sud-ouest : grange avec mezzanine, champ de citrouilles, champs cultivés ;
 * - sud-est : forge, taverne et cour de stockage (tonneaux, foin, bûches) ;
 * - hors de la map : salle d'attente (0, 100, 75) et salle des chercheurs (0, 100, -75).
 */
final class MapBuilder {

    /** À incrémenter à chaque modification du plan : la map est reconstruite au démarrage suivant. */
    static final int VERSION = 1;

    static final int GROUND = 64;
    static final int F = 65;
    static final int EDGE = 45;

    private static final BlockData AIR = Material.AIR.createBlockData();

    private final Map<Long, BlockData> ops = new LinkedHashMap<>();
    private final Random rnd = new Random(20261028L);

    Map<Long, BlockData> plan() {
        clear(-EDGE - 1, 60, -EDGE - 1, EDGE + 1, 97, EDGE + 1);
        clear(-9, 99, 66, 9, 107, 84);
        clear(-4, 99, -79, 4, 106, -71);

        ground();
        roads();
        plaza();
        well();
        stall(6, 6, Material.RED_WOOL, Material.MELON, Material.PUMPKIN, Material.HAY_BLOCK);
        stall(-6, 6, Material.YELLOW_WOOL, Material.BARREL, Material.CARVED_PUMPKIN, Material.COMPOSTER);
        stall(6, -6, Material.BLUE_WOOL, Material.BARREL, Material.BOOKSHELF, Material.POTTED_RED_TULIP);
        stall(-6, -6, Material.GREEN_WOOL, Material.MELON, Material.BARREL, Material.JACK_O_LANTERN);
        lamps();

        manor();
        manorGarden();
        houses();
        orchard();
        barn();
        pumpkinPatch();
        fields();
        forge();
        tavern();
        yard();
        extraTrees();
        border();
        decorate();

        waitingRoom();
        seekerRoom();

        connect();
        return ops;
    }

    // ------------------------------------------------------------------ outils

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int kx(long k) {
        return (int) (k >> 38) << 6 >> 6;
    }

    static int kz(long k) {
        return (int) ((k >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    static int ky(long k) {
        return (int) (k & 0xFFF) << 20 >> 20;
    }

    private void put(int x, int y, int z, BlockData d) {
        ops.put(key(x, y, z), d);
    }

    private void put(int x, int y, int z, Material m) {
        put(x, y, z, data(m));
    }

    private BlockData get(int x, int y, int z) {
        return ops.get(key(x, y, z));
    }

    private boolean free(int x, int y, int z) {
        BlockData d = get(x, y, z);
        return d == null || d.getMaterial().isAir();
    }

    /** Pose seulement si la place est libre (feuillages, décor). */
    private void soft(int x, int y, int z, BlockData d) {
        if (free(x, y, z)) put(x, y, z, d);
    }

    static BlockData data(Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof Leaves l) l.setPersistent(true);
        if (d instanceof FaceAttachable fa) fa.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        return d;
    }

    private BlockData facing(Material m, BlockFace f) {
        BlockData d = data(m);
        if (d instanceof Directional dir && dir.getFaces().contains(f)) dir.setFacing(f);
        return d;
    }

    private BlockData stairs(Material m, BlockFace f) {
        Stairs s = (Stairs) m.createBlockData();
        s.setFacing(f);
        s.setHalf(Bisected.Half.BOTTOM);
        return s;
    }

    private BlockData topSlab(Material m) {
        Slab s = (Slab) m.createBlockData();
        s.setType(Slab.Type.TOP);
        return s;
    }

    private BlockData bottomSlab(Material m) {
        Slab s = (Slab) m.createBlockData();
        s.setType(Slab.Type.BOTTOM);
        return s;
    }

    private BlockData axis(Material m, Axis a) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o) o.setAxis(a);
        return d;
    }

    private BlockData hanging() {
        Lantern l = (Lantern) Material.LANTERN.createBlockData();
        l.setHanging(true);
        return l;
    }

    private BlockData crop(Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof Ageable a) a.setAge(a.getMaximumAge());
        return d;
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData d) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) put(x, y, z, d);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        fill(x1, y1, z1, x2, y2, z2, data(m));
    }

    private void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    private <T> T pick(List<T> l) {
        return l.get(rnd.nextInt(l.size()));
    }

    // ------------------------------------------------------------------ bâtiments

    /** Murs d'un bâtiment (soubassement, poteaux d'angle) et plancher intérieur. */
    private void building(int x1, int z1, int x2, int z2, int top, Material wall, Material corner, Material base, Material floor) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edgeX = x == x1 || x == x2, edgeZ = z == z1 || z == z2;
                if (!edgeX && !edgeZ) continue;
                for (int y = F; y <= top; y++)
                    put(x, y, z, edgeX && edgeZ ? data(corner) : y == F ? data(base) : data(wall));
            }
        fill(x1 + 1, GROUND, z1 + 1, x2 - 1, GROUND, z2 - 1, floor);
    }

    /** Fenêtres (vitres) sur les 4 murs, une toutes les 3 cases. */
    private void windows(int x1, int z1, int x2, int z2, int y, int h) {
        for (int x = x1 + 2; x <= x2 - 2; x += 3)
            for (int dy = 0; dy < h; dy++) {
                put(x, y + dy, z1, Material.GLASS_PANE);
                put(x, y + dy, z2, Material.GLASS_PANE);
            }
        for (int z = z1 + 2; z <= z2 - 2; z += 3)
            for (int dy = 0; dy < h; dy++) {
                put(x1, y + dy, z, Material.GLASS_PANE);
                put(x2, y + dy, z, Material.GLASS_PANE);
            }
    }

    /** Toit à deux pans, faîtage parallèle à l'axe X (pans au nord et au sud), débord d'un bloc. */
    private void roofX(int x1, int z1, int x2, int z2, int y, Material st, Material full) {
        int zn = z1 - 1, zs = z2 + 1;
        for (int i = 0; zn + i <= zs - i; i++) {
            int a = zn + i, b = zs - i, yy = y + i;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (a == b) { put(x, yy, a, full); continue; }
                put(x, yy, a, stairs(st, BlockFace.SOUTH));
                put(x, yy, b, stairs(st, BlockFace.NORTH));
            }
            for (int z = a + 1; z <= b - 1; z++) {
                put(x1, yy, z, full);
                put(x2, yy, z, full);
            }
        }
    }

    /** Toit à deux pans, faîtage parallèle à l'axe Z (pans à l'ouest et à l'est). */
    private void roofZ(int x1, int z1, int x2, int z2, int y, Material st, Material full) {
        int xw = x1 - 1, xe = x2 + 1;
        for (int i = 0; xw + i <= xe - i; i++) {
            int a = xw + i, b = xe - i, yy = y + i;
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (a == b) { put(a, yy, z, full); continue; }
                put(a, yy, z, stairs(st, BlockFace.EAST));
                put(b, yy, z, stairs(st, BlockFace.WEST));
            }
            for (int x = a + 1; x <= b - 1; x++) {
                put(x, yy, z1, full);
                put(x, yy, z2, full);
            }
        }
    }

    /** Porte ouverte (les joueurs ne peuvent pas la fermer : interactions bloquées). */
    private void door(int x, int y, int z, BlockFace f, Material m) {
        Door low = (Door) m.createBlockData();
        low.setFacing(f);
        low.setOpen(true);
        low.setHalf(Bisected.Half.BOTTOM);
        Door up = (Door) low.clone();
        up.setHalf(Bisected.Half.TOP);
        put(x, y, z, low);
        put(x, y + 1, z, up);
    }

    private void tree(int x, int z, Material log, Material leaves, int h) {
        BlockData lv = data(leaves);
        int top = F + h - 1;
        for (int y = top - 2; y <= top - 1; y++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && rnd.nextBoolean()) continue;
                    soft(x + dx, y, z + dz, lv);
                }
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                soft(x + dx, top, z + dz, lv);
                if (dx == 0 || dz == 0) soft(x + dx, top + 1, z + dz, lv);
            }
        for (int y = F; y <= top; y++) put(x, y, z, data(log));
    }

    private void bush(int x, int z, int h) {
        for (int y = F; y < F + h; y++)
            put(x, y, z, data(rnd.nextInt(3) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
    }

    private void lamp(int x, int z) {
        fill(x, F, z, x, F + 2, z, Material.SPRUCE_FENCE);
        put(x, F + 3, z, Material.LANTERN);
    }

    private void path(int x1, int z1, int x2, int z2) {
        fill(x1, GROUND, z1, x2, GROUND, z2, Material.DIRT_PATH);
    }

    // ------------------------------------------------------------------ extérieur

    private void ground() {
        for (int x = -EDGE; x <= EDGE; x++)
            for (int z = -EDGE; z <= EDGE; z++) {
                for (int y = 60; y < GROUND; y++) put(x, y, z, y == 60 ? Material.STONE : Material.DIRT);
                put(x, GROUND, z, Material.GRASS_BLOCK);
            }
    }

    private void border() {
        BlockData barrier = data(Material.BARRIER);
        for (int x = -EDGE; x <= EDGE; x++)
            for (int z = -EDGE; z <= EDGE; z++) {
                put(x, 96, z, barrier);
                if (Math.abs(x) == EDGE || Math.abs(z) == EDGE) for (int y = F; y < 96; y++) put(x, y, z, barrier);
                else if (Math.abs(x) == EDGE - 1 || Math.abs(z) == EDGE - 1)
                    for (int y = F; y <= F + 1; y++)
                        put(x, y, z, data(rnd.nextInt(5) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.OAK_LEAVES));
            }
    }

    private void roads() {
        path(-2, -43, 2, 43);
        path(-43, -2, 43, 2);
    }

    private void plaza() {
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 9.3) continue;
                Material m = r > 8.3 ? Material.POLISHED_ANDESITE
                        : rnd.nextInt(7) == 0 ? Material.MOSSY_STONE_BRICKS
                        : rnd.nextInt(9) == 0 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS;
                put(x, GROUND, z, m);
            }
    }

    private void well() {
        fill(-2, 60, -2, 2, GROUND, 2, Material.COBBLESTONE);
        fill(-1, 61, -1, 1, GROUND, 1, Material.WATER);
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                if (Math.abs(x) == 2 || Math.abs(z) == 2) put(x, F, z, rnd.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
        for (int sx : new int[]{-2, 2})
            for (int sz : new int[]{-2, 2}) fill(sx, F + 1, sz, sx, F + 2, sz, Material.OAK_FENCE);
        fill(-2, F + 3, -2, 2, F + 3, 2, bottomSlab(Material.SPRUCE_SLAB));
        put(0, F + 3, 0, Material.SPRUCE_PLANKS);
        put(0, F + 2, 0, hanging());
    }

    /** Étal de marché 3×3 : poteaux, toile en laine, trois marchandises au milieu. */
    private void stall(int cx, int cz, Material wool, Material a, Material b, Material c) {
        for (int dx : new int[]{-1, 1})
            for (int dz : new int[]{-1, 1}) fill(cx + dx, F, cz + dz, cx + dx, F + 1, cz + dz, Material.OAK_FENCE);
        fill(cx - 1, F + 2, cz - 1, cx + 1, F + 2, cz + 1, wool);
        put(cx - 1, F, cz, a);
        put(cx, F, cz, b);
        put(cx + 1, F, cz, c);
    }

    private void lamps() {
        for (int z : new int[]{-40, -28, -14, 14, 28, 40}) {
            lamp(3, z);
            lamp(-3, z);
        }
        for (int x : new int[]{-40, -28, -14, 14, 28, 40}) {
            lamp(x, 3);
            lamp(x, -3);
        }
        // quelques tonneaux et pots le long des routes
        put(4, F, -14, Material.BARREL);
        put(-4, F, 14, Material.BARREL);
        put(-4, F + 1, 14, Material.POTTED_FERN);
        put(14, F, 4, Material.HAY_BLOCK);
        put(-14, F, -4, Material.COMPOSTER);
        put(28, F, -4, Material.BARREL);
        put(-28, F, 4, Material.PUMPKIN);
    }

    // ------------------------------------------------------------------ nord-est : manoir

    private void manor() {
        int x1 = 10, z1 = -38, x2 = 36, z2 = -18, top = 74;
        building(x1, z1, x2, z2, top, Material.WHITE_TERRACOTTA, Material.DARK_OAK_LOG, Material.COBBLESTONE, Material.DARK_OAK_PLANKS);
        // bandeau de l'étage et poutres verticales (colombages)
        for (int x = x1 + 1; x < x2; x++) { put(x, 70, z1, Material.DARK_OAK_PLANKS); put(x, 70, z2, Material.DARK_OAK_PLANKS); }
        for (int z = z1 + 1; z < z2; z++) { put(x1, 70, z, Material.DARK_OAK_PLANKS); put(x2, 70, z, Material.DARK_OAK_PLANKS); }
        for (int y = F; y <= top; y++) {
            put(23, y, z1, Material.DARK_OAK_LOG);
            put(23, y, z2, Material.DARK_OAK_LOG);
            put(x1, y, -28, Material.DARK_OAK_LOG);
            put(x2, y, -28, Material.DARK_OAK_LOG);
        }
        windows(x1, z1, x2, z2, 66, 2);
        windows(x1, z1, x2, z2, 72, 2);
        // étage, plafond, cloison centrale
        fill(x1 + 1, 70, z1 + 1, x2 - 1, 70, z2 - 1, Material.DARK_OAK_PLANKS);
        fill(x1 + 1, 75, z1 + 1, x2 - 1, 75, z2 - 1, Material.DARK_OAK_PLANKS);
        fill(23, F, z1 + 1, 23, 69, z2 - 1, Material.WHITE_TERRACOTTA);
        fill(23, 71, z1 + 1, 23, 74, z2 - 1, Material.WHITE_TERRACOTTA);
        clear(23, F, -29, 23, 67, -28);
        clear(23, 71, -29, 23, 73, -28);
        roofX(x1, z1, x2, z2, top, Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);
        // portes : entrée au sud (salon), porte de service à l'est (cuisine)
        clear(16, F, z2, 17, 67, z2);
        door(16, F, z2, BlockFace.SOUTH, Material.DARK_OAK_DOOR);
        door(17, F, z2, BlockFace.SOUTH, Material.DARK_OAK_DOOR);
        clear(x2, F, -24, x2, 66, -24);
        door(x2, F, -24, BlockFace.EAST, Material.SPRUCE_DOOR);

        // escalier (cuisine, le long du mur nord) et trémie
        for (int i = 0; i < 5; i++)
            for (int z = -37; z <= -36; z++) {
                if (i > 0) fill(25 + i, F, z, 25 + i, F + i - 1, z, Material.DARK_OAK_PLANKS);
                put(25 + i, F + i, z, stairs(Material.DARK_OAK_STAIRS, BlockFace.EAST));
            }
        clear(24, 70, -37, 29, 70, -36);
        for (int x = 24; x <= 29; x++) put(x, 71, -35, Material.DARK_OAK_FENCE);
        put(24, 71, -37, Material.DARK_OAK_FENCE);
        put(24, 71, -36, Material.DARK_OAK_FENCE);

        // --- salon-bibliothèque (x 11..22)
        fill(14, F, -35, 19, F, -30, Material.RED_CARPET);
        for (int z = -36; z <= -20; z++)
            if (z != -28) fill(11, F, z, 11, F + 2, z, Material.BOOKSHELF);
        put(11, F, -28, facing(Material.LECTERN, BlockFace.EAST));
        // cheminée
        fill(14, F, -37, 14, 68, -37, Material.BRICKS);
        fill(19, F, -37, 19, 68, -37, Material.BRICKS);
        fill(15, 68, -37, 18, 68, -37, Material.BRICKS);
        put(16, F, -37, Material.CAMPFIRE);
        put(17, F, -37, Material.CAMPFIRE);
        put(15, F + 2, -36, Material.LANTERN);
        // fauteuils et table basse
        put(15, F, -33, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH));
        put(18, F, -33, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH));
        put(16, F, -33, Material.OAK_FENCE);
        put(16, F + 1, -33, Material.OAK_PRESSURE_PLATE);
        put(17, F, -33, Material.POTTED_FERN);
        // meubles le long de la cloison
        put(22, F, -36, Material.BARREL);
        put(22, F + 1, -36, Material.POTTED_RED_TULIP);
        put(22, F, -34, Material.CRAFTING_TABLE);
        put(22, F, -32, Material.BOOKSHELF);
        put(22, F + 1, -32, Material.BOOKSHELF);
        put(22, F, -24, Material.CHISELED_BOOKSHELF);
        put(22, F, -22, Material.BARREL);
        put(22, F + 1, -22, Material.LANTERN);
        put(12, F, -20, Material.POTTED_RED_TULIP);
        put(21, F, -20, Material.POTTED_FERN);
        put(12, F, -24, Material.JUKEBOX);
        put(12, F, -25, Material.NOTE_BLOCK);
        put(15, F, -24, Material.WHITE_WOOL);
        put(16, F, -24, Material.WHITE_WOOL);
        for (int[] p : new int[][]{{14, -34}, {19, -34}, {14, -23}, {19, -23}}) put(p[0], 69, p[1], hanging());

        // --- cuisine (x 24..35)
        put(35, F, -34, facing(Material.FURNACE, BlockFace.WEST));
        put(35, F, -33, facing(Material.FURNACE, BlockFace.WEST));
        put(35, F, -32, facing(Material.SMOKER, BlockFace.WEST));
        put(35, F, -31, Material.CAULDRON);
        put(35, F, -30, facing(Material.BARREL, BlockFace.WEST));
        put(35, F, -29, facing(Material.BARREL, BlockFace.WEST));
        put(35, F, -28, Material.CRAFTING_TABLE);
        put(35, F, -27, Material.COMPOSTER);
        put(35, F + 1, -30, Material.LANTERN);
        fill(28, F, -26, 31, F, -26, topSlab(Material.SPRUCE_SLAB));
        for (int x = 28; x <= 31; x++) {
            put(x, F, -27, stairs(Material.OAK_STAIRS, BlockFace.NORTH));
            put(x, F, -25, stairs(Material.OAK_STAIRS, BlockFace.SOUTH));
        }
        put(28, F + 1, -26, Material.LANTERN);
        put(30, F + 1, -26, Material.POTTED_RED_TULIP);
        fill(24, F, -21, 25, F + 1, -19, Material.BARREL);
        fill(33, F, -20, 35, F, -19, Material.HAY_BLOCK);
        put(34, F + 1, -19, Material.PUMPKIN);
        put(33, F, -21, Material.MELON);
        put(26, F, -19, Material.CAULDRON);
        put(27, 69, -30, hanging());
        put(32, 69, -22, hanging());

        // --- étage ouest : bureau et chambre (x 11..22)
        for (int z = -35; z <= -31; z++) {
            fill(13, 71, z, 13, 73, z, Material.BOOKSHELF);
            fill(17, 71, z, 17, 73, z, Material.BOOKSHELF);
        }
        put(20, 71, -36, facing(Material.LECTERN, BlockFace.SOUTH));
        put(20, 71, -35, Material.CARTOGRAPHY_TABLE);
        put(21, 71, -35, Material.LOOM);
        put(21, 71, -33, Material.FLETCHING_TABLE);
        fill(12, 71, -23, 13, 71, -22, Material.RED_WOOL);
        put(12, 71, -21, Material.WHITE_WOOL);
        put(13, 71, -21, Material.WHITE_WOOL);
        put(21, 71, -20, Material.BARREL);
        put(21, 72, -20, Material.BARREL);
        put(21, 71, -21, Material.POTTED_FERN);
        put(16, 71, -20, Material.JUKEBOX);
        fill(15, 71, -27, 19, 71, -23, Material.LIGHT_BLUE_CARPET);
        put(19, 71, -25, Material.POTTED_RED_TULIP);
        for (int[] p : new int[][]{{15, -33}, {20, -28}, {15, -22}}) put(p[0], 74, p[1], hanging());

        // --- étage est : grenier (x 24..35)
        fill(33, 71, -37, 35, 72, -34, Material.HAY_BLOCK);
        put(33, 72, -34, AIR);
        fill(24, 71, -22, 26, 71, -20, Material.BARREL);
        put(24, 72, -20, Material.BARREL);
        put(30, 71, -30, Material.PUMPKIN);
        put(28, 71, -21, Material.PUMPKIN);
        put(31, 71, -34, Material.JACK_O_LANTERN);
        put(35, 71, -22, Material.COMPOSTER);
        put(35, 71, -21, Material.COMPOSTER);
        put(32, 71, -26, Material.CARVED_PUMPKIN);
        put(29, 71, -25, Material.NOTE_BLOCK);
        put(35, 74, -37, Material.COBWEB);
        put(24, 74, -19, Material.COBWEB);
        for (int[] p : new int[][]{{28, -28}, {33, -23}}) put(p[0], 74, p[1], hanging());
    }

    private void manorGarden() {
        path(16, -17, 17, -3);
        // massifs de fleurs
        List<Material> flowers = List.of(Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY,
                Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP, Material.PINK_TULIP);
        for (int[] bed : new int[][]{{11, 14}, {19, 22}})
            for (int x = bed[0]; x <= bed[1]; x++)
                for (int z = -15; z <= -8; z++) if (rnd.nextInt(3) > 0) put(x, F, z, pick(flowers));
        tree(13, -12, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        // haie basse avec un passage
        for (int x = 10; x <= 36; x++) if (x < 15 || x > 18) put(x, F, -6, Material.OAK_LEAVES);
        // fontaine
        fill(28, GROUND, -13, 32, GROUND, -9, Material.STONE_BRICKS);
        fill(29, GROUND, -12, 31, GROUND, -10, Material.WATER);
        for (int x = 28; x <= 32; x++)
            for (int z = -13; z <= -9; z++)
                if (x == 28 || x == 32 || z == -13 || z == -9) put(x, F, z, Material.STONE_BRICK_WALL);
        fill(30, GROUND, -11, 30, F + 1, -11, Material.CHISELED_STONE_BRICKS);
        put(30, F + 2, -11, Material.LANTERN);
        put(26, F, -11, stairs(Material.SPRUCE_STAIRS, BlockFace.WEST));
        put(34, F, -11, stairs(Material.SPRUCE_STAIRS, BlockFace.EAST));
        // buissons, ruches, décor devant la façade
        bush(25, -15, 1);
        bush(35, -15, 2);
        bush(24, -8, 1);
        bush(21, -7, 1);
        put(11, F, -16, facing(Material.BEEHIVE, BlockFace.SOUTH));
        put(11, F + 1, -16, facing(Material.BEEHIVE, BlockFace.SOUTH));
        put(20, F, -16, Material.PUMPKIN);
        put(13, F, -17, Material.POTTED_FERN);
        put(20, F, -17, Material.POTTED_RED_TULIP);
        put(15, F, -17, Material.LANTERN);
        put(18, F, -17, Material.LANTERN);
        tree(34, -8, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        // tonneaux contre le mur ouest du manoir
        fill(9, F, -31, 9, F, -28, Material.BARREL);
        put(9, F + 1, -30, Material.BARREL);
        put(9, F, -26, Material.COMPOSTER);
        put(9, F, -25, Material.COMPOSTER);
    }

    // ------------------------------------------------------------------ nord-ouest : maisons et verger

    private void houses() {
        // maison A (chêne)
        building(-38, -38, -28, -28, 69, Material.OAK_PLANKS, Material.OAK_LOG, Material.COBBLESTONE, Material.OAK_PLANKS);
        windows(-38, -38, -28, -28, 66, 2);
        fill(-37, 70, -37, -29, 70, -29, Material.OAK_PLANKS);
        roofX(-38, -38, -28, -28, 69, Material.SPRUCE_STAIRS, Material.SPRUCE_PLANKS);
        clear(-33, F, -28, -33, 66, -28);
        door(-33, F, -28, BlockFace.SOUTH, Material.OAK_DOOR);
        fill(-34, F, -34, -32, F, -31, Material.BROWN_CARPET);
        put(-37, F, -37, Material.CRAFTING_TABLE);
        put(-36, F, -37, facing(Material.FURNACE, BlockFace.SOUTH));
        put(-35, F, -37, Material.BARREL);
        put(-35, F + 1, -37, Material.BARREL);
        put(-35, F + 2, -37, Material.LANTERN);
        fill(-30, F, -37, -29, F + 1, -37, Material.BOOKSHELF);
        put(-37, F, -33, Material.CAULDRON);
        put(-37, F, -32, Material.COMPOSTER);
        put(-29, F, -33, Material.LOOM);
        put(-29, F, -32, Material.FLETCHING_TABLE);
        put(-29, F, -30, Material.POTTED_CORNFLOWER);
        put(-33, F, -33, topSlab(Material.SPRUCE_SLAB));
        put(-33, F + 1, -33, Material.POTTED_RED_TULIP);
        put(-34, F, -33, stairs(Material.OAK_STAIRS, BlockFace.WEST));
        put(-32, F, -33, stairs(Material.OAK_STAIRS, BlockFace.EAST));
        fill(-37, F, -30, -36, F, -29, Material.RED_WOOL);
        put(-33, 69, -35, hanging());
        put(-33, 69, -30, hanging());
        path(-33, -27, -33, -3);
        lamp(-31, -26);
        // bûcher à côté
        fill(-38, F, -26, -36, F + 1, -26, axis(Material.OAK_LOG, Axis.X));

        // maison B (sapin)
        building(-22, -38, -12, -28, 69, Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_LOG, Material.COBBLESTONE, Material.SPRUCE_PLANKS);
        windows(-22, -38, -12, -28, 66, 2);
        fill(-21, 70, -37, -13, 70, -29, Material.SPRUCE_PLANKS);
        roofX(-22, -38, -12, -28, 69, Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);
        clear(-17, F, -28, -17, 66, -28);
        door(-17, F, -28, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        fill(-19, F, -34, -15, F, -31, Material.LIGHT_BLUE_CARPET);
        for (int x = -21; x <= -13; x++)
            if (x != -17) fill(x, F, -37, x, F + 2, -37, Material.BOOKSHELF);
        put(-17, F, -37, facing(Material.LECTERN, BlockFace.SOUTH));
        put(-21, F, -33, Material.CARTOGRAPHY_TABLE);
        put(-21, F, -32, Material.BARREL);
        put(-21, F + 1, -32, Material.POTTED_FERN);
        put(-13, F, -33, Material.SMITHING_TABLE);
        put(-13, F, -31, Material.BARREL);
        put(-13, F + 1, -31, Material.BARREL);
        put(-13, F + 2, -31, Material.LANTERN);
        put(-13, F, -30, Material.JUKEBOX);
        put(-21, F, -30, Material.POTTED_ALLIUM);
        put(-21, F, -29, Material.WHITE_WOOL);
        put(-20, F, -29, Material.WHITE_WOOL);
        put(-17, 69, -33, hanging());
        path(-17, -27, -17, -3);
    }

    private void orchard() {
        tree(-38, -20, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        tree(-28, -21, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        tree(-22, -19, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        tree(-12, -21, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        tree(-37, -10, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        tree(-25, -10, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        tree(-10, -12, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        put(-37, 67, -20, facing(Material.BEE_NEST, BlockFace.EAST));
        put(-11, 67, -21, facing(Material.BEE_NEST, BlockFace.EAST));
        put(-24, 67, -10, facing(Material.BEE_NEST, BlockFace.EAST));
        // mare
        fill(-31, GROUND, -16, -28, GROUND, -14, Material.WATER);
        put(-30, F, -15, Material.LILY_PAD);
        put(-29, F, -14, Material.LILY_PAD);
        // foin, citrouilles, composteurs, ruches
        put(-20, F, -14, Material.HAY_BLOCK);
        put(-20, F + 1, -14, Material.HAY_BLOCK);
        put(-19, F, -14, Material.HAY_BLOCK);
        put(-20, F, -13, Material.HAY_BLOCK);
        for (int[] p : new int[][]{{-36, -16}, {-24, -23}, {-14, -15}, {-8, -19}, {-40, -7}, {-21, -6}})
            put(p[0], F, p[1], rnd.nextBoolean() ? Material.PUMPKIN : Material.MELON);
        put(-9, F, -9, Material.COMPOSTER);
        put(-8, F, -9, Material.COMPOSTER);
        put(-41, F, -15, facing(Material.BEEHIVE, BlockFace.EAST));
        put(-41, F, -14, facing(Material.BEEHIVE, BlockFace.EAST));
        bush(-35, -24, 1);
        bush(-14, -8, 2);
        bush(-27, -5, 1);
        put(-23, F, -14, Material.LANTERN);
    }

    // ------------------------------------------------------------------ sud-ouest : ferme

    private void barn() {
        int x1 = -40, z1 = 8, x2 = -24, z2 = 24, top = 72;
        building(x1, z1, x2, z2, top, Material.SPRUCE_PLANKS, Material.DARK_OAK_LOG, Material.COBBLESTONE, Material.COARSE_DIRT);
        windows(x1, z1, x2, z2, 68, 2);
        roofZ(x1, z1, x2, z2, top, Material.DARK_OAK_STAIRS, Material.SPRUCE_PLANKS);
        clear(x2, F, 14, x2, 68, 18);
        path(-23, 15, -3, 17);
        // mezzanine + échelle + garde-corps
        fill(-39, 69, 9, -34, 69, 23, Material.SPRUCE_PLANKS);
        for (int y = F; y <= 69; y++) put(-33, y, 9, facing(Material.LADDER, BlockFace.SOUTH));
        for (int z = 10; z <= 23; z++) put(-33, 70, z, Material.SPRUCE_FENCE);
        put(-36, 68, 16, hanging());
        put(-36, 68, 12, hanging());
        // rez-de-chaussée
        for (int x = -39; x <= -37; x++)
            for (int z = 10; z <= 14; z++) {
                put(x, F, z, Material.HAY_BLOCK);
                if (rnd.nextBoolean()) put(x, F + 1, z, Material.HAY_BLOCK);
            }
        put(-30, F, 23, Material.CAULDRON);
        put(-29, F, 23, Material.CAULDRON);
        put(-26, F, 9, Material.COMPOSTER);
        put(-25, F, 9, Material.COMPOSTER);
        put(-26, F, 22, Material.BARREL);
        put(-26, F + 1, 22, Material.BARREL);
        put(-26, F + 2, 22, Material.LANTERN);
        put(-25, F, 23, Material.BARREL);
        put(-31, F, 9, Material.GRINDSTONE);
        put(-35, F, 20, Material.PUMPKIN);
        put(-31, F, 12, facing(Material.CARVED_PUMPKIN, BlockFace.EAST));
        put(-28, F, 12, Material.HAY_BLOCK);
        put(-28, F + 1, 12, Material.HAY_BLOCK);
        put(-27, F, 13, Material.HAY_BLOCK);
        put(-39, F, 22, Material.CRAFTING_TABLE);
        put(-39, F, 20, Material.BARREL);
        // enclos intérieur
        for (int x = -32; x <= -27; x++) put(x, F, 18, Material.OAK_FENCE);
        put(-30, F, 20, Material.HAY_BLOCK);
        // mezzanine : foin, tonneaux, citrouilles
        for (int x = -39; x <= -37; x++)
            for (int z = 18; z <= 23; z++) {
                put(x, 70, z, Material.HAY_BLOCK);
                if (rnd.nextInt(3) == 0) put(x, 71, z, Material.HAY_BLOCK);
            }
        put(-36, 70, 10, Material.BARREL);
        put(-36, 71, 10, Material.BARREL);
        put(-35, 70, 15, Material.PUMPKIN);
        put(-39, 70, 13, Material.COMPOSTER);
        put(-34, 70, 12, Material.LANTERN);
    }

    private void pumpkinPatch() {
        for (int x = -20; x <= -8; x++)
            for (int z = 8; z <= 24; z++) {
                if (z >= 14 && z <= 18) continue;
                int r = rnd.nextInt(100);
                if (r < 30) put(x, F, z, Material.PUMPKIN);
                else if (r < 42) put(x, F, z, Material.MELON);
                else if (r < 45) put(x, F, z, facing(Material.CARVED_PUMPKIN, BlockFace.EAST));
            }
        put(-14, F, 21, Material.OAK_FENCE);
        put(-14, F + 1, 21, Material.HAY_BLOCK);
        put(-14, F + 2, 21, facing(Material.CARVED_PUMPKIN, BlockFace.EAST));
    }

    private void fields() {
        for (int x = -42; x <= -10; x++) {
            if (x != -26) put(x, F, 28, Material.OAK_FENCE);
            put(x, F, 42, Material.OAK_FENCE);
        }
        for (int z = 28; z <= 42; z++) {
            put(-42, F, z, Material.OAK_FENCE);
            put(-10, F, z, Material.OAK_FENCE);
        }
        Farmland soil = (Farmland) Material.FARMLAND.createBlockData();
        soil.setMoisture(soil.getMaximumMoisture());
        for (int x = -41; x <= -11; x++)
            for (int z = 29; z <= 41; z++) {
                if ((z - 29) % 4 == 2) { put(x, GROUND, z, Material.WATER); continue; }
                put(x, GROUND, z, soil);
                put(x, F, z, crop(x < -30 ? Material.WHEAT : x < -20 ? Material.CARROTS : Material.POTATOES));
            }
        // épouvantail
        put(-26, F, 33, Material.OAK_FENCE);
        put(-26, F + 1, 33, Material.HAY_BLOCK);
        put(-26, F + 2, 33, facing(Material.CARVED_PUMPKIN, BlockFace.NORTH));
        // foin et composteurs au bord du champ
        put(-8, F, 30, Material.HAY_BLOCK);
        put(-8, F + 1, 30, Material.HAY_BLOCK);
        put(-8, F, 31, Material.HAY_BLOCK);
        put(-8, F, 34, Material.COMPOSTER);
        put(-8, F, 35, Material.COMPOSTER);
        put(-8, F, 38, Material.BARREL);
        put(-7, F, 38, Material.BARREL);
    }

    // ------------------------------------------------------------------ sud-est : forge, taverne, cour

    private void forge() {
        int x1 = 10, z1 = 10, x2 = 22, z2 = 20;
        building(x1, z1, x2, z2, 69, Material.STONE_BRICKS, Material.DARK_OAK_LOG, Material.COBBLESTONE, Material.POLISHED_ANDESITE);
        windows(x1, z1, x2, z2, 66, 2);
        fill(x1 + 1, 70, z1 + 1, x2 - 1, 70, z2 - 1, Material.SPRUCE_PLANKS);
        roofX(x1, z1, x2, z2, 69, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILES);
        clear(x1, F, 13, x1, 67, 17);
        put(12, F, 12, Material.ANVIL);
        put(15, F, 13, Material.CHIPPED_ANVIL);
        put(18, F, 12, Material.DAMAGED_ANVIL);
        for (int x = 12; x <= 14; x++) put(x, F, 11, facing(Material.FURNACE, BlockFace.SOUTH));
        put(15, F, 11, facing(Material.BLAST_FURNACE, BlockFace.SOUTH));
        put(16, F, 11, facing(Material.BLAST_FURNACE, BlockFace.SOUTH));
        put(17, F, 11, Material.SMITHING_TABLE);
        put(19, F, 11, Material.GRINDSTONE);
        put(21, F, 11, Material.CAULDRON);
        put(21, F, 19, Material.IRON_BLOCK);
        put(20, F, 19, Material.IRON_BLOCK);
        put(21, F + 1, 19, Material.IRON_BLOCK);
        put(21, F, 18, Material.COAL_BLOCK);
        put(19, F, 19, Material.COAL_BLOCK);
        put(12, F, 19, Material.BARREL);
        put(13, F, 19, Material.BARREL);
        put(12, F + 1, 19, Material.BARREL);
        put(16, F, 19, Material.STONECUTTER);
        put(14, F, 19, Material.CRAFTING_TABLE);
        fill(16, F, 15, 17, F, 16, Material.BRICKS);
        put(16, F + 1, 15, Material.CAMPFIRE);
        put(17, F + 1, 16, Material.LANTERN);
        put(13, 69, 15, hanging());
        put(19, 69, 15, hanging());
        // tas de bois dehors
        fill(23, F, 11, 24, F + 1, 14, axis(Material.OAK_LOG, Axis.Z));
        put(9, F, 12, Material.BARREL);
        put(9, F, 18, Material.ANVIL);
    }

    private void tavern() {
        int x1 = 27, z1 = 8, x2 = 40, z2 = 24;
        building(x1, z1, x2, z2, 69, Material.OAK_PLANKS, Material.STRIPPED_OAK_LOG, Material.COBBLESTONE, Material.SPRUCE_PLANKS);
        windows(x1, z1, x2, z2, 66, 2);
        fill(x1 + 1, 70, z1 + 1, x2 - 1, 70, z2 - 1, Material.OAK_PLANKS);
        roofZ(x1, z1, x2, z2, 69, Material.SPRUCE_STAIRS, Material.SPRUCE_PLANKS);
        clear(33, F, z1, 34, 67, z1);
        path(33, 3, 34, 7);
        // comptoir et tonneaux en perce derrière
        for (int x = 29; x <= 36; x++) put(x, F, 20, Material.BARREL);
        for (int x = 28; x <= 39; x += 2) {
            put(x, F, 23, facing(Material.BARREL, BlockFace.NORTH));
            put(x, F + 1, 23, facing(Material.BARREL, BlockFace.NORTH));
        }
        put(38, F, 22, Material.CAULDRON);
        put(39, F, 21, Material.CRAFTING_TABLE);
        put(33, F + 1, 20, Material.LANTERN);
        for (int x = 30; x <= 34; x += 2) put(x, F, 19, Material.SPRUCE_FENCE);
        // tables et bancs
        for (int[] t : new int[][]{{30, 12}, {35, 12}, {30, 16}}) {
            put(t[0], F, t[1], topSlab(Material.SPRUCE_SLAB));
            put(t[0] + 1, F, t[1], topSlab(Material.SPRUCE_SLAB));
            put(t[0], F, t[1] - 1, stairs(Material.OAK_STAIRS, BlockFace.NORTH));
            put(t[0] + 1, F, t[1] + 1, stairs(Material.OAK_STAIRS, BlockFace.SOUTH));
            put(t[0], F + 1, t[1], Material.LANTERN);
            put(t[0] + 1, F + 1, t[1], Material.POTTED_RED_TULIP);
        }
        fill(39, F, 10, 39, F + 1, 11, Material.BOOKSHELF);
        put(39, F, 14, Material.JUKEBOX);
        put(39, F, 15, Material.NOTE_BLOCK);
        put(28, F, 9, Material.BARREL);
        put(28, F + 1, 9, Material.BARREL);
        put(29, F, 9, Material.BARREL);
        put(39, F, 9, Material.POTTED_FERN);
        put(28, F, 15, Material.MELON);
        put(28, F, 16, Material.PUMPKIN);
        for (int[] p : new int[][]{{31, 12}, {36, 16}, {31, 18}}) put(p[0], 69, p[1], hanging());
    }

    private void yard() {
        for (int x = 9; x <= 41; x++) if (x < 24 || x > 26) put(x, F, 27, Material.OAK_FENCE);
        for (int z = 27; z <= 42; z++) if (z != 34 && z != 35) put(9, F, z, Material.OAK_FENCE);
        int[][] piles = {{13, 31, 0}, {19, 31, 1}, {25, 31, 2}, {31, 31, 3}, {37, 31, 4}, {13, 37, 2}, {19, 37, 0},
                {25, 37, 5}, {31, 37, 1}, {37, 37, 0}, {16, 41, 6}, {28, 41, 0}, {36, 41, 1}};
        for (int[] p : piles) pile(p[0], p[1], p[2]);
        lamp(22, 34);
        lamp(34, 34);
    }

    private void pile(int x, int z, int type) {
        switch (type) {
            case 0 -> { // tonneaux
                fill(x, F, z, x + 2, F, z + 1, Material.BARREL);
                put(x, F + 1, z, Material.BARREL);
                put(x + 1, F + 1, z, Material.BARREL);
                put(x + 2, F + 1, z + 1, Material.LANTERN);
            }
            case 1 -> { // foin
                fill(x, F, z, x + 1, F, z + 1, Material.HAY_BLOCK);
                put(x, F + 1, z, Material.HAY_BLOCK);
            }
            case 2 -> { // bûches
                fill(x, F, z, x + 2, F, z + 1, axis(Material.OAK_LOG, Axis.X));
                fill(x, F + 1, z, x + 2, F + 1, z, axis(Material.OAK_LOG, Axis.X));
            }
            case 3 -> { // caisses
                put(x, F, z, Material.CRAFTING_TABLE);
                put(x + 1, F, z, Material.BARREL);
                put(x, F + 1, z, Material.BARREL);
                put(x + 1, F, z + 1, Material.FLETCHING_TABLE);
                put(x + 2, F, z, Material.SPRUCE_PLANKS);
            }
            case 4 -> { // citrouilles
                for (int dx = 0; dx < 3; dx++)
                    for (int dz = 0; dz < 3; dz++)
                        if (rnd.nextInt(10) < 6) put(x + dx, F, z + dz, rnd.nextBoolean() ? Material.PUMPKIN : Material.MELON);
            }
            case 5 -> { // composteurs
                for (int dx = 0; dx < 3; dx++) put(x + dx, F, z, Material.COMPOSTER);
                put(x + 1, F, z + 1, Material.CAULDRON);
            }
            default -> { // pastèques
                fill(x, F, z, x + 2, F, z + 1, Material.MELON);
            }
        }
    }

    private void extraTrees() {
        tree(25, 26, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        tree(40, -40, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        tree(6, -40, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        tree(5, 40, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        tree(-6, -40, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        tree(-41, -6, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        tree(41, 5, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        bush(6, -24, 1);
        bush(-6, 24, 2);
        bush(24, 6, 1);
        bush(-24, -6, 1);
    }

    /** Herbes et fleurs au hasard sur l'herbe libre. */
    private void decorate() {
        List<Material> flowers = List.of(Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.AZURE_BLUET);
        for (int x = -43; x <= 43; x++)
            for (int z = -43; z <= 43; z++) {
                BlockData g = get(x, GROUND, z);
                if (g == null || g.getMaterial() != Material.GRASS_BLOCK || !free(x, F, z)) continue;
                int r = rnd.nextInt(100);
                if (r < 10) put(x, F, z, Material.SHORT_GRASS);
                else if (r < 13) put(x, F, z, pick(flowers));
            }
    }

    // ------------------------------------------------------------------ salles hors map

    private void waitingRoom() {
        int x1 = -8, x2 = 8, z1 = 67, z2 = 83, y = 100;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                put(x, y, z, edge ? Material.DARK_OAK_PLANKS : (x + z) % 2 == 0 ? Material.OAK_PLANKS : Material.BIRCH_PLANKS);
                put(x, y + 6, z, Material.SPRUCE_PLANKS);
                if (edge)
                    for (int yy = y + 1; yy <= y + 5; yy++)
                        put(x, yy, z, (x == x1 || x == x2) && (z == z1 || z == z2) || x % 4 == 0 && (z == z1 || z == z2)
                                || z % 4 == 0 && (x == x1 || x == x2) ? Material.STRIPPED_OAK_LOG : Material.GLASS);
            }
        fill(-2, y + 1, 73, 2, y + 1, 77, Material.GREEN_CARPET);
        // vitrine des déguisements le long des murs est et ouest
        Material[] show = {Material.BARREL, Material.BOOKSHELF, Material.HAY_BLOCK, Material.PUMPKIN, Material.CRAFTING_TABLE,
                Material.FURNACE, Material.MELON, Material.COMPOSTER, Material.ANVIL, Material.BEEHIVE, Material.LOOM,
                Material.JACK_O_LANTERN, Material.CAULDRON, Material.POTTED_RED_TULIP};
        int i = 0;
        for (int z = 69; z <= 81; z += 2) {
            put(-7, y + 1, z, show[i++ % show.length]);
            put(7, y + 1, z, show[i++ % show.length]);
        }
        for (int[] p : new int[][]{{-4, 71}, {4, 71}, {-4, 79}, {4, 79}, {0, 75}}) put(p[0], y + 5, p[1], hanging());
        put(-7, y + 1, 68, Material.POTTED_FERN);
        put(7, y + 1, 68, Material.POTTED_FERN);
        put(-7, y + 1, 82, Material.POTTED_RED_TULIP);
        put(7, y + 1, 82, Material.POTTED_RED_TULIP);
    }

    private void seekerRoom() {
        fill(-3, 100, -78, 3, 105, -72, Material.BLACK_CONCRETE);
        clear(-2, 101, -77, 2, 104, -73);
    }

    // ------------------------------------------------------------------ connexions des barrières, vitres, murets

    private static boolean fence(Material m) {
        return m.name().endsWith("_FENCE");
    }

    private static boolean pane(Material m) {
        return m.name().endsWith("_PANE") || m == Material.IRON_BARS;
    }

    private static boolean wall(Material m) {
        return m.name().endsWith("_WALL");
    }

    private boolean connects(Material self, BlockData n) {
        if (n == null) return false;
        Material m = n.getMaterial();
        if (m.isAir()) return false;
        if (m.isOccluding()) return true;
        if (fence(self)) return fence(m) || m.name().endsWith("_FENCE_GATE");
        if (pane(self)) return pane(m) || wall(m);
        return wall(m) || pane(m) || m.name().endsWith("_FENCE_GATE");
    }

    private void connect() {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        List<Map.Entry<Long, BlockData>> todo = new ArrayList<>();
        for (Map.Entry<Long, BlockData> e : ops.entrySet()) {
            Material m = e.getValue().getMaterial();
            if (fence(m) || pane(m) || wall(m)) todo.add(e);
        }
        for (Map.Entry<Long, BlockData> e : todo) {
            long k = e.getKey();
            int x = kx(k), y = ky(k), z = kz(k);
            BlockData d = e.getValue().clone();
            Material m = d.getMaterial();
            for (BlockFace f : faces) {
                boolean c = connects(m, get(x + f.getModX(), y, z + f.getModZ()));
                if (d instanceof MultipleFacing mf && mf.getAllowedFaces().contains(f)) mf.setFace(f, c);
                else if (d instanceof Wall w) w.setHeight(f, c ? Wall.Height.LOW : Wall.Height.NONE);
            }
            ops.put(k, d);
        }
    }
}
