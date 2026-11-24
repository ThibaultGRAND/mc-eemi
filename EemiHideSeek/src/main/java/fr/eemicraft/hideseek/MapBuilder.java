/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Axis
 *  org.bukkit.Material
 *  org.bukkit.block.BlockFace
 *  org.bukkit.block.data.Ageable
 *  org.bukkit.block.data.Bisected$Half
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.Directional
 *  org.bukkit.block.data.FaceAttachable
 *  org.bukkit.block.data.FaceAttachable$AttachedFace
 *  org.bukkit.block.data.MultipleFacing
 *  org.bukkit.block.data.Orientable
 *  org.bukkit.block.data.type.Door
 *  org.bukkit.block.data.type.Farmland
 *  org.bukkit.block.data.type.Lantern
 *  org.bukkit.block.data.type.Leaves
 *  org.bukkit.block.data.type.Slab
 *  org.bukkit.block.data.type.Slab$Type
 *  org.bukkit.block.data.type.Stairs
 *  org.bukkit.block.data.type.Wall
 *  org.bukkit.block.data.type.Wall$Height
 */
package fr.eemicraft.hideseek;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

final class MapBuilder {
    static final int VERSION = 1;
    static final int GROUND = 64;
    static final int F = 65;
    static final int EDGE = 45;
    private static final BlockData AIR = Material.AIR.createBlockData();
    private final Map<Long, BlockData> ops = new LinkedHashMap<Long, BlockData>();
    private final Random rnd = new Random(20261028L);

    MapBuilder() {
    }

    Map<Long, BlockData> plan() {
        this.clear(-46, 60, -46, 46, 97, 46);
        this.clear(-9, 99, 66, 9, 107, 84);
        this.clear(-4, 99, -79, 4, 106, -71);
        this.ground();
        this.roads();
        this.plaza();
        this.well();
        this.stall(6, 6, Material.RED_WOOL, Material.MELON, Material.PUMPKIN, Material.HAY_BLOCK);
        this.stall(-6, 6, Material.YELLOW_WOOL, Material.BARREL, Material.CARVED_PUMPKIN, Material.COMPOSTER);
        this.stall(6, -6, Material.BLUE_WOOL, Material.BARREL, Material.BOOKSHELF, Material.POTTED_RED_TULIP);
        this.stall(-6, -6, Material.GREEN_WOOL, Material.MELON, Material.BARREL, Material.JACK_O_LANTERN);
        this.lamps();
        this.manor();
        this.manorGarden();
        this.houses();
        this.orchard();
        this.barn();
        this.pumpkinPatch();
        this.fields();
        this.forge();
        this.tavern();
        this.yard();
        this.extraTrees();
        this.border();
        this.decorate();
        this.waitingRoom();
        this.seekerRoom();
        this.connect();
        return this.ops;
    }

    static long key(int x, int y, int z) {
        return (long)(x & 0x3FFFFFF) << 38 | (long)(z & 0x3FFFFFF) << 12 | (long)(y & 0xFFF);
    }

    static int kx(long k) {
        return (int)(k >> 38) << 6 >> 6;
    }

    static int kz(long k) {
        return (int)(k >> 12 & 0x3FFFFFFL) << 6 >> 6;
    }

    static int ky(long k) {
        return (int)(k & 0xFFFL) << 20 >> 20;
    }

    private void put(int x, int y, int z, BlockData d) {
        this.ops.put(MapBuilder.key(x, y, z), d);
    }

    private void put(int x, int y, int z, Material m) {
        this.put(x, y, z, MapBuilder.data(m));
    }

    private BlockData get(int x, int y, int z) {
        return this.ops.get(MapBuilder.key(x, y, z));
    }

    private boolean free(int x, int y, int z) {
        BlockData d = this.get(x, y, z);
        return d == null || d.getMaterial().isAir();
    }

    private void soft(int x, int y, int z, BlockData d) {
        if (this.free(x, y, z)) {
            this.put(x, y, z, d);
        }
    }

    static BlockData data(Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof Leaves) {
            Leaves l = (Leaves)d;
            l.setPersistent(true);
        }
        if (d instanceof FaceAttachable) {
            FaceAttachable fa = (FaceAttachable)d;
            fa.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        }
        return d;
    }

    private BlockData facing(Material m, BlockFace f) {
        Directional dir;
        BlockData d = MapBuilder.data(m);
        if (d instanceof Directional && (dir = (Directional)d).getFaces().contains(f)) {
            dir.setFacing(f);
        }
        return d;
    }

    private BlockData stairs(Material m, BlockFace f) {
        Stairs s = (Stairs)m.createBlockData();
        s.setFacing(f);
        s.setHalf(Bisected.Half.BOTTOM);
        return s;
    }

    private BlockData topSlab(Material m) {
        Slab s = (Slab)m.createBlockData();
        s.setType(Slab.Type.TOP);
        return s;
    }

    private BlockData bottomSlab(Material m) {
        Slab s = (Slab)m.createBlockData();
        s.setType(Slab.Type.BOTTOM);
        return s;
    }

    private BlockData axis(Material m, Axis a) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable) {
            Orientable o = (Orientable)d;
            o.setAxis(a);
        }
        return d;
    }

    private BlockData hanging() {
        Lantern l = (Lantern)Material.LANTERN.createBlockData();
        l.setHanging(true);
        return l;
    }

    private BlockData crop(Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof Ageable) {
            Ageable a = (Ageable)d;
            a.setAge(a.getMaximumAge());
        }
        return d;
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData d) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); ++x) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); ++y) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); ++z) {
                    this.put(x, y, z, d);
                }
            }
        }
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        this.fill(x1, y1, z1, x2, y2, z2, MapBuilder.data(m));
    }

    private void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    private <T> T pick(List<T> l) {
        return l.get(this.rnd.nextInt(l.size()));
    }

    private void building(int x1, int z1, int x2, int z2, int top, Material wall, Material corner, Material base, Material floor) {
        for (int x = x1; x <= x2; ++x) {
            for (int z = z1; z <= z2; ++z) {
                boolean edgeZ;
                boolean edgeX = x == x1 || x == x2;
                boolean bl = edgeZ = z == z1 || z == z2;
                if (!edgeX && !edgeZ) continue;
                for (int y = 65; y <= top; ++y) {
                    this.put(x, y, z, edgeX && edgeZ ? MapBuilder.data(corner) : (y == 65 ? MapBuilder.data(base) : MapBuilder.data(wall)));
                }
            }
        }
        this.fill(x1 + 1, 64, z1 + 1, x2 - 1, 64, z2 - 1, floor);
    }

    private void windows(int x1, int z1, int x2, int z2, int y, int h) {
        int dy;
        for (int x = x1 + 2; x <= x2 - 2; x += 3) {
            for (dy = 0; dy < h; ++dy) {
                this.put(x, y + dy, z1, Material.GLASS_PANE);
                this.put(x, y + dy, z2, Material.GLASS_PANE);
            }
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 3) {
            for (dy = 0; dy < h; ++dy) {
                this.put(x1, y + dy, z, Material.GLASS_PANE);
                this.put(x2, y + dy, z, Material.GLASS_PANE);
            }
        }
    }

    private void roofX(int x1, int z1, int x2, int z2, int y, Material st, Material full) {
        int zn = z1 - 1;
        int zs = z2 + 1;
        int i = 0;
        while (zn + i <= zs - i) {
            int a = zn + i;
            int b = zs - i;
            int yy = y + i;
            for (int x = x1 - 1; x <= x2 + 1; ++x) {
                if (a == b) {
                    this.put(x, yy, a, full);
                    continue;
                }
                this.put(x, yy, a, this.stairs(st, BlockFace.SOUTH));
                this.put(x, yy, b, this.stairs(st, BlockFace.NORTH));
            }
            for (int z = a + 1; z <= b - 1; ++z) {
                this.put(x1, yy, z, full);
                this.put(x2, yy, z, full);
            }
            ++i;
        }
    }

    private void roofZ(int x1, int z1, int x2, int z2, int y, Material st, Material full) {
        int xw = x1 - 1;
        int xe = x2 + 1;
        int i = 0;
        while (xw + i <= xe - i) {
            int a = xw + i;
            int b = xe - i;
            int yy = y + i;
            for (int z = z1 - 1; z <= z2 + 1; ++z) {
                if (a == b) {
                    this.put(a, yy, z, full);
                    continue;
                }
                this.put(a, yy, z, this.stairs(st, BlockFace.EAST));
                this.put(b, yy, z, this.stairs(st, BlockFace.WEST));
            }
            for (int x = a + 1; x <= b - 1; ++x) {
                this.put(x, yy, z1, full);
                this.put(x, yy, z2, full);
            }
            ++i;
        }
    }

    private void door(int x, int y, int z, BlockFace f, Material m) {
        Door low = (Door)m.createBlockData();
        low.setFacing(f);
        low.setOpen(true);
        low.setHalf(Bisected.Half.BOTTOM);
        Door up = (Door)low.clone();
        up.setHalf(Bisected.Half.TOP);
        this.put(x, y, z, (BlockData)low);
        this.put(x, y + 1, z, (BlockData)up);
    }

    private void tree(int x, int z, Material log, Material leaves, int h) {
        int y;
        BlockData lv = MapBuilder.data(leaves);
        int top = 65 + h - 1;
        for (y = top - 2; y <= top - 1; ++y) {
            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && this.rnd.nextBoolean()) continue;
                    this.soft(x + dx, y, z + dz, lv);
                }
            }
        }
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                this.soft(x + dx, top, z + dz, lv);
                if (dx != 0 && dz != 0) continue;
                this.soft(x + dx, top + 1, z + dz, lv);
            }
        }
        for (y = 65; y <= top; ++y) {
            this.put(x, y, z, MapBuilder.data(log));
        }
    }

    private void bush(int x, int z, int h) {
        for (int y = 65; y < 65 + h; ++y) {
            this.put(x, y, z, MapBuilder.data(this.rnd.nextInt(3) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
        }
    }

    private void lamp(int x, int z) {
        this.fill(x, 65, z, x, 67, z, Material.SPRUCE_FENCE);
        this.put(x, 68, z, Material.LANTERN);
    }

    private void path(int x1, int z1, int x2, int z2) {
        this.fill(x1, 64, z1, x2, 64, z2, Material.DIRT_PATH);
    }

    private void ground() {
        for (int x = -45; x <= 45; ++x) {
            for (int z = -45; z <= 45; ++z) {
                for (int y = 60; y < 64; ++y) {
                    this.put(x, y, z, y == 60 ? Material.STONE : Material.DIRT);
                }
                this.put(x, 64, z, Material.GRASS_BLOCK);
            }
        }
    }

    private void border() {
        BlockData barrier = MapBuilder.data(Material.BARRIER);
        for (int x = -45; x <= 45; ++x) {
            for (int z = -45; z <= 45; ++z) {
                int y;
                this.put(x, 96, z, barrier);
                if (Math.abs(x) == 45 || Math.abs(z) == 45) {
                    for (y = 65; y < 96; ++y) {
                        this.put(x, y, z, barrier);
                    }
                    continue;
                }
                if (Math.abs(x) != 44 && Math.abs(z) != 44) continue;
                for (y = 65; y <= 66; ++y) {
                    this.put(x, y, z, MapBuilder.data(this.rnd.nextInt(5) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.OAK_LEAVES));
                }
            }
        }
    }

    private void roads() {
        this.path(-2, -43, 2, 43);
        this.path(-43, -2, 43, 2);
    }

    private void plaza() {
        for (int x = -9; x <= 9; ++x) {
            for (int z = -9; z <= 9; ++z) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 9.3) continue;
                Material m = r > 8.3 ? Material.POLISHED_ANDESITE : (this.rnd.nextInt(7) == 0 ? Material.MOSSY_STONE_BRICKS : (this.rnd.nextInt(9) == 0 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS));
                this.put(x, 64, z, m);
            }
        }
    }

    private void well() {
        this.fill(-2, 60, -2, 2, 64, 2, Material.COBBLESTONE);
        this.fill(-1, 61, -1, 1, 64, 1, Material.WATER);
        for (int x = -2; x <= 2; ++x) {
            for (int z = -2; z <= 2; ++z) {
                if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                this.put(x, 65, z, this.rnd.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
            }
        }
        for (int sx : new int[]{-2, 2}) {
            for (int sz : new int[]{-2, 2}) {
                this.fill(sx, 66, sz, sx, 67, sz, Material.OAK_FENCE);
            }
        }
        this.fill(-2, 68, -2, 2, 68, 2, this.bottomSlab(Material.SPRUCE_SLAB));
        this.put(0, 68, 0, Material.SPRUCE_PLANKS);
        this.put(0, 67, 0, this.hanging());
    }

    private void stall(int cx, int cz, Material wool, Material a, Material b, Material c) {
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                this.fill(cx + dx, 65, cz + dz, cx + dx, 66, cz + dz, Material.OAK_FENCE);
            }
        }
        this.fill(cx - 1, 67, cz - 1, cx + 1, 67, cz + 1, wool);
        this.put(cx - 1, 65, cz, a);
        this.put(cx, 65, cz, b);
        this.put(cx + 1, 65, cz, c);
    }

    private void lamps() {
        for (int z : new int[]{-40, -28, -14, 14, 28, 40}) {
            this.lamp(3, z);
            this.lamp(-3, z);
        }
        for (int x : new int[]{-40, -28, -14, 14, 28, 40}) {
            this.lamp(x, 3);
            this.lamp(x, -3);
        }
        this.put(4, 65, -14, Material.BARREL);
        this.put(-4, 65, 14, Material.BARREL);
        this.put(-4, 66, 14, Material.POTTED_FERN);
        this.put(14, 65, 4, Material.HAY_BLOCK);
        this.put(-14, 65, -4, Material.COMPOSTER);
        this.put(28, 65, -4, Material.BARREL);
        this.put(-28, 65, 4, Material.PUMPKIN);
    }

    private void manor() {
        int z;
        int x;
        int x1 = 10;
        int z1 = -38;
        int x2 = 36;
        int z2 = -18;
        int top = 74;
        this.building(x1, z1, x2, z2, top, Material.WHITE_TERRACOTTA, Material.DARK_OAK_LOG, Material.COBBLESTONE, Material.DARK_OAK_PLANKS);
        for (x = x1 + 1; x < x2; ++x) {
            this.put(x, 70, z1, Material.DARK_OAK_PLANKS);
            this.put(x, 70, z2, Material.DARK_OAK_PLANKS);
        }
        for (z = z1 + 1; z < z2; ++z) {
            this.put(x1, 70, z, Material.DARK_OAK_PLANKS);
            this.put(x2, 70, z, Material.DARK_OAK_PLANKS);
        }
        for (int y = 65; y <= top; ++y) {
            this.put(23, y, z1, Material.DARK_OAK_LOG);
            this.put(23, y, z2, Material.DARK_OAK_LOG);
            this.put(x1, y, -28, Material.DARK_OAK_LOG);
            this.put(x2, y, -28, Material.DARK_OAK_LOG);
        }
        this.windows(x1, z1, x2, z2, 66, 2);
        this.windows(x1, z1, x2, z2, 72, 2);
        this.fill(x1 + 1, 70, z1 + 1, x2 - 1, 70, z2 - 1, Material.DARK_OAK_PLANKS);
        this.fill(x1 + 1, 75, z1 + 1, x2 - 1, 75, z2 - 1, Material.DARK_OAK_PLANKS);
        this.fill(23, 65, z1 + 1, 23, 69, z2 - 1, Material.WHITE_TERRACOTTA);
        this.fill(23, 71, z1 + 1, 23, 74, z2 - 1, Material.WHITE_TERRACOTTA);
        this.clear(23, 65, -29, 23, 67, -28);
        this.clear(23, 71, -29, 23, 73, -28);
        this.roofX(x1, z1, x2, z2, top, Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);
        this.clear(16, 65, z2, 17, 67, z2);
        this.door(16, 65, z2, BlockFace.SOUTH, Material.DARK_OAK_DOOR);
        this.door(17, 65, z2, BlockFace.SOUTH, Material.DARK_OAK_DOOR);
        this.clear(x2, 65, -24, x2, 66, -24);
        this.door(x2, 65, -24, BlockFace.EAST, Material.SPRUCE_DOOR);
        for (int i = 0; i < 5; ++i) {
            for (int z3 = -37; z3 <= -36; ++z3) {
                if (i > 0) {
                    this.fill(25 + i, 65, z3, 25 + i, 65 + i - 1, z3, Material.DARK_OAK_PLANKS);
                }
                this.put(25 + i, 65 + i, z3, this.stairs(Material.DARK_OAK_STAIRS, BlockFace.EAST));
            }
        }
        this.clear(24, 70, -37, 29, 70, -36);
        for (x = 24; x <= 29; ++x) {
            this.put(x, 71, -35, Material.DARK_OAK_FENCE);
        }
        this.put(24, 71, -37, Material.DARK_OAK_FENCE);
        this.put(24, 71, -36, Material.DARK_OAK_FENCE);
        this.fill(14, 65, -35, 19, 65, -30, Material.RED_CARPET);
        for (z = -36; z <= -20; ++z) {
            if (z == -28) continue;
            this.fill(11, 65, z, 11, 67, z, Material.BOOKSHELF);
        }
        this.put(11, 65, -28, this.facing(Material.LECTERN, BlockFace.EAST));
        this.fill(14, 65, -37, 14, 68, -37, Material.BRICKS);
        this.fill(19, 65, -37, 19, 68, -37, Material.BRICKS);
        this.fill(15, 68, -37, 18, 68, -37, Material.BRICKS);
        this.put(16, 65, -37, Material.CAMPFIRE);
        this.put(17, 65, -37, Material.CAMPFIRE);
        this.put(15, 67, -36, Material.LANTERN);
        this.put(15, 65, -33, this.stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH));
        this.put(18, 65, -33, this.stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH));
        this.put(16, 65, -33, Material.OAK_FENCE);
        this.put(16, 66, -33, Material.OAK_PRESSURE_PLATE);
        this.put(17, 65, -33, Material.POTTED_FERN);
        this.put(22, 65, -36, Material.BARREL);
        this.put(22, 66, -36, Material.POTTED_RED_TULIP);
        this.put(22, 65, -34, Material.CRAFTING_TABLE);
        this.put(22, 65, -32, Material.BOOKSHELF);
        this.put(22, 66, -32, Material.BOOKSHELF);
        this.put(22, 65, -24, Material.CHISELED_BOOKSHELF);
        this.put(22, 65, -22, Material.BARREL);
        this.put(22, 66, -22, Material.LANTERN);
        this.put(12, 65, -20, Material.POTTED_RED_TULIP);
        this.put(21, 65, -20, Material.POTTED_FERN);
        this.put(12, 65, -24, Material.JUKEBOX);
        this.put(12, 65, -25, Material.NOTE_BLOCK);
        this.put(15, 65, -24, Material.WHITE_WOOL);
        this.put(16, 65, -24, Material.WHITE_WOOL);
        for (int[] p : new int[][]{{14, -34}, {19, -34}, {14, -23}, {19, -23}}) {
            this.put(p[0], 69, p[1], this.hanging());
        }
        this.put(35, 65, -34, this.facing(Material.FURNACE, BlockFace.WEST));
        this.put(35, 65, -33, this.facing(Material.FURNACE, BlockFace.WEST));
        this.put(35, 65, -32, this.facing(Material.SMOKER, BlockFace.WEST));
        this.put(35, 65, -31, Material.CAULDRON);
        this.put(35, 65, -30, this.facing(Material.BARREL, BlockFace.WEST));
        this.put(35, 65, -29, this.facing(Material.BARREL, BlockFace.WEST));
        this.put(35, 65, -28, Material.CRAFTING_TABLE);
        this.put(35, 65, -27, Material.COMPOSTER);
        this.put(35, 66, -30, Material.LANTERN);
        this.fill(28, 65, -26, 31, 65, -26, this.topSlab(Material.SPRUCE_SLAB));
        for (int x3 = 28; x3 <= 31; ++x3) {
            this.put(x3, 65, -27, this.stairs(Material.OAK_STAIRS, BlockFace.NORTH));
            this.put(x3, 65, -25, this.stairs(Material.OAK_STAIRS, BlockFace.SOUTH));
        }
        this.put(28, 66, -26, Material.LANTERN);
        this.put(30, 66, -26, Material.POTTED_RED_TULIP);
        this.fill(24, 65, -21, 25, 66, -19, Material.BARREL);
        this.fill(33, 65, -20, 35, 65, -19, Material.HAY_BLOCK);
        this.put(34, 66, -19, Material.PUMPKIN);
        this.put(33, 65, -21, Material.MELON);
        this.put(26, 65, -19, Material.CAULDRON);
        this.put(27, 69, -30, this.hanging());
        this.put(32, 69, -22, this.hanging());
        for (int z4 = -35; z4 <= -31; ++z4) {
            this.fill(13, 71, z4, 13, 73, z4, Material.BOOKSHELF);
            this.fill(17, 71, z4, 17, 73, z4, Material.BOOKSHELF);
        }
        this.put(20, 71, -36, this.facing(Material.LECTERN, BlockFace.SOUTH));
        this.put(20, 71, -35, Material.CARTOGRAPHY_TABLE);
        this.put(21, 71, -35, Material.LOOM);
        this.put(21, 71, -33, Material.FLETCHING_TABLE);
        this.fill(12, 71, -23, 13, 71, -22, Material.RED_WOOL);
        this.put(12, 71, -21, Material.WHITE_WOOL);
        this.put(13, 71, -21, Material.WHITE_WOOL);
        this.put(21, 71, -20, Material.BARREL);
        this.put(21, 72, -20, Material.BARREL);
        this.put(21, 71, -21, Material.POTTED_FERN);
        this.put(16, 71, -20, Material.JUKEBOX);
        this.fill(15, 71, -27, 19, 71, -23, Material.LIGHT_BLUE_CARPET);
        this.put(19, 71, -25, Material.POTTED_RED_TULIP);
        for (int[] p : new int[][]{{15, -33}, {20, -28}, {15, -22}}) {
            this.put(p[0], 74, p[1], this.hanging());
        }
        this.fill(33, 71, -37, 35, 72, -34, Material.HAY_BLOCK);
        this.put(33, 72, -34, AIR);
        this.fill(24, 71, -22, 26, 71, -20, Material.BARREL);
        this.put(24, 72, -20, Material.BARREL);
        this.put(30, 71, -30, Material.PUMPKIN);
        this.put(28, 71, -21, Material.PUMPKIN);
        this.put(31, 71, -34, Material.JACK_O_LANTERN);
        this.put(35, 71, -22, Material.COMPOSTER);
        this.put(35, 71, -21, Material.COMPOSTER);
        this.put(32, 71, -26, Material.CARVED_PUMPKIN);
        this.put(29, 71, -25, Material.NOTE_BLOCK);
        this.put(35, 74, -37, Material.COBWEB);
        this.put(24, 74, -19, Material.COBWEB);
        for (int[] p : new int[][]{{28, -28}, {33, -23}}) {
            this.put(p[0], 74, p[1], this.hanging());
        }
    }

    private void manorGarden() {
        int x;
        this.path(16, -17, 17, -3);
        List<Material> flowers = List.of(Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP, Material.PINK_TULIP);
        for (int[] bed : new int[][]{{11, 14}, {19, 22}}) {
            for (int x2 = bed[0]; x2 <= bed[1]; ++x2) {
                for (int z = -15; z <= -8; ++z) {
                    if (this.rnd.nextInt(3) <= 0) continue;
                    this.put(x2, 65, z, this.pick(flowers));
                }
            }
        }
        this.tree(13, -12, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        for (x = 10; x <= 36; ++x) {
            if (x >= 15 && x <= 18) continue;
            this.put(x, 65, -6, Material.OAK_LEAVES);
        }
        this.fill(28, 64, -13, 32, 64, -9, Material.STONE_BRICKS);
        this.fill(29, 64, -12, 31, 64, -10, Material.WATER);
        for (x = 28; x <= 32; ++x) {
            for (int z = -13; z <= -9; ++z) {
                if (x != 28 && x != 32 && z != -13 && z != -9) continue;
                this.put(x, 65, z, Material.STONE_BRICK_WALL);
            }
        }
        this.fill(30, 64, -11, 30, 66, -11, Material.CHISELED_STONE_BRICKS);
        this.put(30, 67, -11, Material.LANTERN);
        this.put(26, 65, -11, this.stairs(Material.SPRUCE_STAIRS, BlockFace.WEST));
        this.put(34, 65, -11, this.stairs(Material.SPRUCE_STAIRS, BlockFace.EAST));
        this.bush(25, -15, 1);
        this.bush(35, -15, 2);
        this.bush(24, -8, 1);
        this.bush(21, -7, 1);
        this.put(11, 65, -16, this.facing(Material.BEEHIVE, BlockFace.SOUTH));
        this.put(11, 66, -16, this.facing(Material.BEEHIVE, BlockFace.SOUTH));
        this.put(20, 65, -16, Material.PUMPKIN);
        this.put(13, 65, -17, Material.POTTED_FERN);
        this.put(20, 65, -17, Material.POTTED_RED_TULIP);
        this.put(15, 65, -17, Material.LANTERN);
        this.put(18, 65, -17, Material.LANTERN);
        this.tree(34, -8, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        this.fill(9, 65, -31, 9, 65, -28, Material.BARREL);
        this.put(9, 66, -30, Material.BARREL);
        this.put(9, 65, -26, Material.COMPOSTER);
        this.put(9, 65, -25, Material.COMPOSTER);
    }

    private void houses() {
        this.building(-38, -38, -28, -28, 69, Material.OAK_PLANKS, Material.OAK_LOG, Material.COBBLESTONE, Material.OAK_PLANKS);
        this.windows(-38, -38, -28, -28, 66, 2);
        this.fill(-37, 70, -37, -29, 70, -29, Material.OAK_PLANKS);
        this.roofX(-38, -38, -28, -28, 69, Material.SPRUCE_STAIRS, Material.SPRUCE_PLANKS);
        this.clear(-33, 65, -28, -33, 66, -28);
        this.door(-33, 65, -28, BlockFace.SOUTH, Material.OAK_DOOR);
        this.fill(-34, 65, -34, -32, 65, -31, Material.BROWN_CARPET);
        this.put(-37, 65, -37, Material.CRAFTING_TABLE);
        this.put(-36, 65, -37, this.facing(Material.FURNACE, BlockFace.SOUTH));
        this.put(-35, 65, -37, Material.BARREL);
        this.put(-35, 66, -37, Material.BARREL);
        this.put(-35, 67, -37, Material.LANTERN);
        this.fill(-30, 65, -37, -29, 66, -37, Material.BOOKSHELF);
        this.put(-37, 65, -33, Material.CAULDRON);
        this.put(-37, 65, -32, Material.COMPOSTER);
        this.put(-29, 65, -33, Material.LOOM);
        this.put(-29, 65, -32, Material.FLETCHING_TABLE);
        this.put(-29, 65, -30, Material.POTTED_CORNFLOWER);
        this.put(-33, 65, -33, this.topSlab(Material.SPRUCE_SLAB));
        this.put(-33, 66, -33, Material.POTTED_RED_TULIP);
        this.put(-34, 65, -33, this.stairs(Material.OAK_STAIRS, BlockFace.WEST));
        this.put(-32, 65, -33, this.stairs(Material.OAK_STAIRS, BlockFace.EAST));
        this.fill(-37, 65, -30, -36, 65, -29, Material.RED_WOOL);
        this.put(-33, 69, -35, this.hanging());
        this.put(-33, 69, -30, this.hanging());
        this.path(-33, -27, -33, -3);
        this.lamp(-31, -26);
        this.fill(-38, 65, -26, -36, 66, -26, this.axis(Material.OAK_LOG, Axis.X));
        this.building(-22, -38, -12, -28, 69, Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_LOG, Material.COBBLESTONE, Material.SPRUCE_PLANKS);
        this.windows(-22, -38, -12, -28, 66, 2);
        this.fill(-21, 70, -37, -13, 70, -29, Material.SPRUCE_PLANKS);
        this.roofX(-22, -38, -12, -28, 69, Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);
        this.clear(-17, 65, -28, -17, 66, -28);
        this.door(-17, 65, -28, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        this.fill(-19, 65, -34, -15, 65, -31, Material.LIGHT_BLUE_CARPET);
        for (int x = -21; x <= -13; ++x) {
            if (x == -17) continue;
            this.fill(x, 65, -37, x, 67, -37, Material.BOOKSHELF);
        }
        this.put(-17, 65, -37, this.facing(Material.LECTERN, BlockFace.SOUTH));
        this.put(-21, 65, -33, Material.CARTOGRAPHY_TABLE);
        this.put(-21, 65, -32, Material.BARREL);
        this.put(-21, 66, -32, Material.POTTED_FERN);
        this.put(-13, 65, -33, Material.SMITHING_TABLE);
        this.put(-13, 65, -31, Material.BARREL);
        this.put(-13, 66, -31, Material.BARREL);
        this.put(-13, 67, -31, Material.LANTERN);
        this.put(-13, 65, -30, Material.JUKEBOX);
        this.put(-21, 65, -30, Material.POTTED_ALLIUM);
        this.put(-21, 65, -29, Material.WHITE_WOOL);
        this.put(-20, 65, -29, Material.WHITE_WOOL);
        this.put(-17, 69, -33, this.hanging());
        this.path(-17, -27, -17, -3);
    }

    private void orchard() {
        this.tree(-38, -20, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        this.tree(-28, -21, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        this.tree(-22, -19, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        this.tree(-12, -21, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        this.tree(-37, -10, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        this.tree(-25, -10, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        this.tree(-10, -12, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        this.put(-37, 67, -20, this.facing(Material.BEE_NEST, BlockFace.EAST));
        this.put(-11, 67, -21, this.facing(Material.BEE_NEST, BlockFace.EAST));
        this.put(-24, 67, -10, this.facing(Material.BEE_NEST, BlockFace.EAST));
        this.fill(-31, 64, -16, -28, 64, -14, Material.WATER);
        this.put(-30, 65, -15, Material.LILY_PAD);
        this.put(-29, 65, -14, Material.LILY_PAD);
        this.put(-20, 65, -14, Material.HAY_BLOCK);
        this.put(-20, 66, -14, Material.HAY_BLOCK);
        this.put(-19, 65, -14, Material.HAY_BLOCK);
        this.put(-20, 65, -13, Material.HAY_BLOCK);
        for (int[] p : new int[][]{{-36, -16}, {-24, -23}, {-14, -15}, {-8, -19}, {-40, -7}, {-21, -6}}) {
            this.put(p[0], 65, p[1], this.rnd.nextBoolean() ? Material.PUMPKIN : Material.MELON);
        }
        this.put(-9, 65, -9, Material.COMPOSTER);
        this.put(-8, 65, -9, Material.COMPOSTER);
        this.put(-41, 65, -15, this.facing(Material.BEEHIVE, BlockFace.EAST));
        this.put(-41, 65, -14, this.facing(Material.BEEHIVE, BlockFace.EAST));
        this.bush(-35, -24, 1);
        this.bush(-14, -8, 2);
        this.bush(-27, -5, 1);
        this.put(-23, 65, -14, Material.LANTERN);
    }

    private void barn() {
        int z;
        int x;
        int x1 = -40;
        int z1 = 8;
        int x2 = -24;
        int z2 = 24;
        int top = 72;
        this.building(x1, z1, x2, z2, top, Material.SPRUCE_PLANKS, Material.DARK_OAK_LOG, Material.COBBLESTONE, Material.COARSE_DIRT);
        this.windows(x1, z1, x2, z2, 68, 2);
        this.roofZ(x1, z1, x2, z2, top, Material.DARK_OAK_STAIRS, Material.SPRUCE_PLANKS);
        this.clear(x2, 65, 14, x2, 68, 18);
        this.path(-23, 15, -3, 17);
        this.fill(-39, 69, 9, -34, 69, 23, Material.SPRUCE_PLANKS);
        for (int y = 65; y <= 69; ++y) {
            this.put(-33, y, 9, this.facing(Material.LADDER, BlockFace.SOUTH));
        }
        for (int z3 = 10; z3 <= 23; ++z3) {
            this.put(-33, 70, z3, Material.SPRUCE_FENCE);
        }
        this.put(-36, 68, 16, this.hanging());
        this.put(-36, 68, 12, this.hanging());
        for (x = -39; x <= -37; ++x) {
            for (z = 10; z <= 14; ++z) {
                this.put(x, 65, z, Material.HAY_BLOCK);
                if (!this.rnd.nextBoolean()) continue;
                this.put(x, 66, z, Material.HAY_BLOCK);
            }
        }
        this.put(-30, 65, 23, Material.CAULDRON);
        this.put(-29, 65, 23, Material.CAULDRON);
        this.put(-26, 65, 9, Material.COMPOSTER);
        this.put(-25, 65, 9, Material.COMPOSTER);
        this.put(-26, 65, 22, Material.BARREL);
        this.put(-26, 66, 22, Material.BARREL);
        this.put(-26, 67, 22, Material.LANTERN);
        this.put(-25, 65, 23, Material.BARREL);
        this.put(-31, 65, 9, Material.GRINDSTONE);
        this.put(-35, 65, 20, Material.PUMPKIN);
        this.put(-31, 65, 12, this.facing(Material.CARVED_PUMPKIN, BlockFace.EAST));
        this.put(-28, 65, 12, Material.HAY_BLOCK);
        this.put(-28, 66, 12, Material.HAY_BLOCK);
        this.put(-27, 65, 13, Material.HAY_BLOCK);
        this.put(-39, 65, 22, Material.CRAFTING_TABLE);
        this.put(-39, 65, 20, Material.BARREL);
        for (x = -32; x <= -27; ++x) {
            this.put(x, 65, 18, Material.OAK_FENCE);
        }
        this.put(-30, 65, 20, Material.HAY_BLOCK);
        for (x = -39; x <= -37; ++x) {
            for (z = 18; z <= 23; ++z) {
                this.put(x, 70, z, Material.HAY_BLOCK);
                if (this.rnd.nextInt(3) != 0) continue;
                this.put(x, 71, z, Material.HAY_BLOCK);
            }
        }
        this.put(-36, 70, 10, Material.BARREL);
        this.put(-36, 71, 10, Material.BARREL);
        this.put(-35, 70, 15, Material.PUMPKIN);
        this.put(-39, 70, 13, Material.COMPOSTER);
        this.put(-34, 70, 12, Material.LANTERN);
    }

    private void pumpkinPatch() {
        for (int x = -20; x <= -8; ++x) {
            for (int z = 8; z <= 24; ++z) {
                if (z >= 14 && z <= 18) continue;
                int r = this.rnd.nextInt(100);
                if (r < 30) {
                    this.put(x, 65, z, Material.PUMPKIN);
                    continue;
                }
                if (r < 42) {
                    this.put(x, 65, z, Material.MELON);
                    continue;
                }
                if (r >= 45) continue;
                this.put(x, 65, z, this.facing(Material.CARVED_PUMPKIN, BlockFace.EAST));
            }
        }
        this.put(-14, 65, 21, Material.OAK_FENCE);
        this.put(-14, 66, 21, Material.HAY_BLOCK);
        this.put(-14, 67, 21, this.facing(Material.CARVED_PUMPKIN, BlockFace.EAST));
    }

    private void fields() {
        for (int x = -42; x <= -10; ++x) {
            if (x != -26) {
                this.put(x, 65, 28, Material.OAK_FENCE);
            }
            this.put(x, 65, 42, Material.OAK_FENCE);
        }
        for (int z = 28; z <= 42; ++z) {
            this.put(-42, 65, z, Material.OAK_FENCE);
            this.put(-10, 65, z, Material.OAK_FENCE);
        }
        Farmland soil = (Farmland)Material.FARMLAND.createBlockData();
        soil.setMoisture(soil.getMaximumMoisture());
        for (int x = -41; x <= -11; ++x) {
            for (int z = 29; z <= 41; ++z) {
                if ((z - 29) % 4 == 2) {
                    this.put(x, 64, z, Material.WATER);
                    continue;
                }
                this.put(x, 64, z, (BlockData)soil);
                this.put(x, 65, z, this.crop(x < -30 ? Material.WHEAT : (x < -20 ? Material.CARROTS : Material.POTATOES)));
            }
        }
        this.put(-26, 65, 33, Material.OAK_FENCE);
        this.put(-26, 66, 33, Material.HAY_BLOCK);
        this.put(-26, 67, 33, this.facing(Material.CARVED_PUMPKIN, BlockFace.NORTH));
        this.put(-8, 65, 30, Material.HAY_BLOCK);
        this.put(-8, 66, 30, Material.HAY_BLOCK);
        this.put(-8, 65, 31, Material.HAY_BLOCK);
        this.put(-8, 65, 34, Material.COMPOSTER);
        this.put(-8, 65, 35, Material.COMPOSTER);
        this.put(-8, 65, 38, Material.BARREL);
        this.put(-7, 65, 38, Material.BARREL);
    }

    private void forge() {
        int x1 = 10;
        int z1 = 10;
        int x2 = 22;
        int z2 = 20;
        this.building(x1, z1, x2, z2, 69, Material.STONE_BRICKS, Material.DARK_OAK_LOG, Material.COBBLESTONE, Material.POLISHED_ANDESITE);
        this.windows(x1, z1, x2, z2, 66, 2);
        this.fill(x1 + 1, 70, z1 + 1, x2 - 1, 70, z2 - 1, Material.SPRUCE_PLANKS);
        this.roofX(x1, z1, x2, z2, 69, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILES);
        this.clear(x1, 65, 13, x1, 67, 17);
        this.put(12, 65, 12, Material.ANVIL);
        this.put(15, 65, 13, Material.CHIPPED_ANVIL);
        this.put(18, 65, 12, Material.DAMAGED_ANVIL);
        for (int x = 12; x <= 14; ++x) {
            this.put(x, 65, 11, this.facing(Material.FURNACE, BlockFace.SOUTH));
        }
        this.put(15, 65, 11, this.facing(Material.BLAST_FURNACE, BlockFace.SOUTH));
        this.put(16, 65, 11, this.facing(Material.BLAST_FURNACE, BlockFace.SOUTH));
        this.put(17, 65, 11, Material.SMITHING_TABLE);
        this.put(19, 65, 11, Material.GRINDSTONE);
        this.put(21, 65, 11, Material.CAULDRON);
        this.put(21, 65, 19, Material.IRON_BLOCK);
        this.put(20, 65, 19, Material.IRON_BLOCK);
        this.put(21, 66, 19, Material.IRON_BLOCK);
        this.put(21, 65, 18, Material.COAL_BLOCK);
        this.put(19, 65, 19, Material.COAL_BLOCK);
        this.put(12, 65, 19, Material.BARREL);
        this.put(13, 65, 19, Material.BARREL);
        this.put(12, 66, 19, Material.BARREL);
        this.put(16, 65, 19, Material.STONECUTTER);
        this.put(14, 65, 19, Material.CRAFTING_TABLE);
        this.fill(16, 65, 15, 17, 65, 16, Material.BRICKS);
        this.put(16, 66, 15, Material.CAMPFIRE);
        this.put(17, 66, 16, Material.LANTERN);
        this.put(13, 69, 15, this.hanging());
        this.put(19, 69, 15, this.hanging());
        this.fill(23, 65, 11, 24, 66, 14, this.axis(Material.OAK_LOG, Axis.Z));
        this.put(9, 65, 12, Material.BARREL);
        this.put(9, 65, 18, Material.ANVIL);
    }

    private void tavern() {
        int x;
        int x1 = 27;
        int z1 = 8;
        int x2 = 40;
        int z2 = 24;
        this.building(x1, z1, x2, z2, 69, Material.OAK_PLANKS, Material.STRIPPED_OAK_LOG, Material.COBBLESTONE, Material.SPRUCE_PLANKS);
        this.windows(x1, z1, x2, z2, 66, 2);
        this.fill(x1 + 1, 70, z1 + 1, x2 - 1, 70, z2 - 1, Material.OAK_PLANKS);
        this.roofZ(x1, z1, x2, z2, 69, Material.SPRUCE_STAIRS, Material.SPRUCE_PLANKS);
        this.clear(33, 65, z1, 34, 67, z1);
        this.path(33, 3, 34, 7);
        for (x = 29; x <= 36; ++x) {
            this.put(x, 65, 20, Material.BARREL);
        }
        for (x = 28; x <= 39; x += 2) {
            this.put(x, 65, 23, this.facing(Material.BARREL, BlockFace.NORTH));
            this.put(x, 66, 23, this.facing(Material.BARREL, BlockFace.NORTH));
        }
        this.put(38, 65, 22, Material.CAULDRON);
        this.put(39, 65, 21, Material.CRAFTING_TABLE);
        this.put(33, 66, 20, Material.LANTERN);
        for (x = 30; x <= 34; x += 2) {
            this.put(x, 65, 19, Material.SPRUCE_FENCE);
        }
        for (int[] t : new int[][]{{30, 12}, {35, 12}, {30, 16}}) {
            this.put(t[0], 65, t[1], this.topSlab(Material.SPRUCE_SLAB));
            this.put(t[0] + 1, 65, t[1], this.topSlab(Material.SPRUCE_SLAB));
            this.put(t[0], 65, t[1] - 1, this.stairs(Material.OAK_STAIRS, BlockFace.NORTH));
            this.put(t[0] + 1, 65, t[1] + 1, this.stairs(Material.OAK_STAIRS, BlockFace.SOUTH));
            this.put(t[0], 66, t[1], Material.LANTERN);
            this.put(t[0] + 1, 66, t[1], Material.POTTED_RED_TULIP);
        }
        this.fill(39, 65, 10, 39, 66, 11, Material.BOOKSHELF);
        this.put(39, 65, 14, Material.JUKEBOX);
        this.put(39, 65, 15, Material.NOTE_BLOCK);
        this.put(28, 65, 9, Material.BARREL);
        this.put(28, 66, 9, Material.BARREL);
        this.put(29, 65, 9, Material.BARREL);
        this.put(39, 65, 9, Material.POTTED_FERN);
        this.put(28, 65, 15, Material.MELON);
        this.put(28, 65, 16, Material.PUMPKIN);
        for (int[] p : new int[][]{{31, 12}, {36, 16}, {31, 18}}) {
            this.put(p[0], 69, p[1], this.hanging());
        }
    }

    private void yard() {
        int[][] piles;
        for (int x = 9; x <= 41; ++x) {
            if (x >= 24 && x <= 26) continue;
            this.put(x, 65, 27, Material.OAK_FENCE);
        }
        for (int z = 27; z <= 42; ++z) {
            if (z == 34 || z == 35) continue;
            this.put(9, 65, z, Material.OAK_FENCE);
        }
        for (int[] p : piles = new int[][]{{13, 31, 0}, {19, 31, 1}, {25, 31, 2}, {31, 31, 3}, {37, 31, 4}, {13, 37, 2}, {19, 37, 0}, {25, 37, 5}, {31, 37, 1}, {37, 37, 0}, {16, 41, 6}, {28, 41, 0}, {36, 41, 1}}) {
            this.pile(p[0], p[1], p[2]);
        }
        this.lamp(22, 34);
        this.lamp(34, 34);
    }

    private void pile(int x, int z, int type) {
        switch (type) {
            case 0: {
                this.fill(x, 65, z, x + 2, 65, z + 1, Material.BARREL);
                this.put(x, 66, z, Material.BARREL);
                this.put(x + 1, 66, z, Material.BARREL);
                this.put(x + 2, 66, z + 1, Material.LANTERN);
                break;
            }
            case 1: {
                this.fill(x, 65, z, x + 1, 65, z + 1, Material.HAY_BLOCK);
                this.put(x, 66, z, Material.HAY_BLOCK);
                break;
            }
            case 2: {
                this.fill(x, 65, z, x + 2, 65, z + 1, this.axis(Material.OAK_LOG, Axis.X));
                this.fill(x, 66, z, x + 2, 66, z, this.axis(Material.OAK_LOG, Axis.X));
                break;
            }
            case 3: {
                this.put(x, 65, z, Material.CRAFTING_TABLE);
                this.put(x + 1, 65, z, Material.BARREL);
                this.put(x, 66, z, Material.BARREL);
                this.put(x + 1, 65, z + 1, Material.FLETCHING_TABLE);
                this.put(x + 2, 65, z, Material.SPRUCE_PLANKS);
                break;
            }
            case 4: {
                for (int dx = 0; dx < 3; ++dx) {
                    for (int dz = 0; dz < 3; ++dz) {
                        if (this.rnd.nextInt(10) >= 6) continue;
                        this.put(x + dx, 65, z + dz, this.rnd.nextBoolean() ? Material.PUMPKIN : Material.MELON);
                    }
                }
                break;
            }
            case 5: {
                for (int dx = 0; dx < 3; ++dx) {
                    this.put(x + dx, 65, z, Material.COMPOSTER);
                }
                this.put(x + 1, 65, z + 1, Material.CAULDRON);
                break;
            }
            default: {
                this.fill(x, 65, z, x + 2, 65, z + 1, Material.MELON);
            }
        }
    }

    private void extraTrees() {
        this.tree(25, 26, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        this.tree(40, -40, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        this.tree(6, -40, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        this.tree(5, 40, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        this.tree(-6, -40, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        this.tree(-41, -6, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        this.tree(41, 5, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 6);
        this.bush(6, -24, 1);
        this.bush(-6, 24, 2);
        this.bush(24, 6, 1);
        this.bush(-24, -6, 1);
    }

    private void decorate() {
        List<Material> flowers = List.of(Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.AZURE_BLUET);
        for (int x = -43; x <= 43; ++x) {
            for (int z = -43; z <= 43; ++z) {
                BlockData g = this.get(x, 64, z);
                if (g == null || g.getMaterial() != Material.GRASS_BLOCK || !this.free(x, 65, z)) continue;
                int r = this.rnd.nextInt(100);
                if (r < 10) {
                    this.put(x, 65, z, Material.SHORT_GRASS);
                    continue;
                }
                if (r >= 13) continue;
                this.put(x, 65, z, this.pick(flowers));
            }
        }
    }

    private void waitingRoom() {
        int x1 = -8;
        int x2 = 8;
        int z1 = 67;
        int z2 = 83;
        int y = 100;
        for (int x = x1; x <= x2; ++x) {
            for (int z = z1; z <= z2; ++z) {
                boolean edge;
                boolean bl = edge = x == x1 || x == x2 || z == z1 || z == z2;
                this.put(x, y, z, edge ? Material.DARK_OAK_PLANKS : ((x + z) % 2 == 0 ? Material.OAK_PLANKS : Material.BIRCH_PLANKS));
                this.put(x, y + 6, z, Material.SPRUCE_PLANKS);
                if (!edge) continue;
                for (int yy = y + 1; yy <= y + 5; ++yy) {
                    this.put(x, yy, z, (x == x1 || x == x2) && (z == z1 || z == z2) || x % 4 == 0 && (z == z1 || z == z2) || z % 4 == 0 && (x == x1 || x == x2) ? Material.STRIPPED_OAK_LOG : Material.GLASS);
                }
            }
        }
        this.fill(-2, y + 1, 73, 2, y + 1, 77, Material.GREEN_CARPET);
        Material[] show = new Material[]{Material.BARREL, Material.BOOKSHELF, Material.HAY_BLOCK, Material.PUMPKIN, Material.CRAFTING_TABLE, Material.FURNACE, Material.MELON, Material.COMPOSTER, Material.ANVIL, Material.BEEHIVE, Material.LOOM, Material.JACK_O_LANTERN, Material.CAULDRON, Material.POTTED_RED_TULIP};
        int i = 0;
        for (int z = 69; z <= 81; z += 2) {
            this.put(-7, y + 1, z, show[i++ % show.length]);
            this.put(7, y + 1, z, show[i++ % show.length]);
        }
        for (int[] p : new int[][]{{-4, 71}, {4, 71}, {-4, 79}, {4, 79}, {0, 75}}) {
            this.put(p[0], y + 5, p[1], this.hanging());
        }
        this.put(-7, y + 1, 68, Material.POTTED_FERN);
        this.put(7, y + 1, 68, Material.POTTED_FERN);
        this.put(-7, y + 1, 82, Material.POTTED_RED_TULIP);
        this.put(7, y + 1, 82, Material.POTTED_RED_TULIP);
    }

    private void seekerRoom() {
        this.fill(-3, 100, -78, 3, 105, -72, Material.BLACK_CONCRETE);
        this.clear(-2, 101, -77, 2, 104, -73);
    }

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
        if (n == null) {
            return false;
        }
        Material m = n.getMaterial();
        if (m.isAir()) {
            return false;
        }
        if (m.isOccluding()) {
            return true;
        }
        if (MapBuilder.fence(self)) {
            return MapBuilder.fence(m) || m.name().endsWith("_FENCE_GATE");
        }
        if (MapBuilder.pane(self)) {
            return MapBuilder.pane(m) || MapBuilder.wall(m);
        }
        return MapBuilder.wall(m) || MapBuilder.pane(m) || m.name().endsWith("_FENCE_GATE");
    }

    private void connect() {
        BlockFace[] faces = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        ArrayList<Map.Entry<Long, BlockData>> todo = new ArrayList<Map.Entry<Long, BlockData>>();
        for (Map.Entry<Long, BlockData> entry : this.ops.entrySet()) {
            Material m = entry.getValue().getMaterial();
            if (!MapBuilder.fence(m) && !MapBuilder.pane(m) && !MapBuilder.wall(m)) continue;
            todo.add(entry);
        }
        for (Map.Entry<Long, BlockData> entry : todo) {
            long k = entry.getKey();
            int x = MapBuilder.kx(k);
            int y = MapBuilder.ky(k);
            int z = MapBuilder.kz(k);
            BlockData d = ((BlockData)entry.getValue()).clone();
            Material m = d.getMaterial();
            for (BlockFace f : faces) {
                MultipleFacing mf;
                boolean c = this.connects(m, this.get(x + f.getModX(), y, z + f.getModZ()));
                if (d instanceof MultipleFacing && (mf = (MultipleFacing)d).getAllowedFaces().contains(f)) {
                    mf.setFace(f, c);
                    continue;
                }
                if (!(d instanceof Wall)) continue;
                Wall w = (Wall)d;
                w.setHeight(f, c ? Wall.Height.LOW : Wall.Height.NONE);
            }
            this.ops.put(k, d);
        }
    }
}

