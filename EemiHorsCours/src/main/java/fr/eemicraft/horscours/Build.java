package fr.eemicraft.horscours;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

/** Petites aides de construction (sans physique : les formes sont posées à la main). */
final class Build {
    final World w;

    Build(World w) {
        this.w = w;
    }

    void set(int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    void set(int x, int y, int z, BlockData d) {
        w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    w.getBlockAt(x, y, z).setType(m, false);
    }

    /** Vitre reliée sur un axe (x = mur nord-sud... : alongX = le mur court le long de x). */
    void pane(int x, int y, int z, Material m, boolean alongX) {
        BlockData d = m.createBlockData();
        if (d instanceof MultipleFacing mf) {
            if (alongX) {
                mf.setFace(BlockFace.EAST, true);
                mf.setFace(BlockFace.WEST, true);
            } else {
                mf.setFace(BlockFace.NORTH, true);
                mf.setFace(BlockFace.SOUTH, true);
            }
        }
        set(x, y, z, d);
    }

    void door(int x, int y, int z, Material m, BlockFace facing, boolean open) {
        for (int i = 0; i < 2; i++) {
            Door d = (Door) m.createBlockData();
            d.setFacing(facing);
            d.setHalf(i == 0 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            d.setHinge(Door.Hinge.LEFT);
            d.setOpen(open);
            set(x, y + i, z, d);
        }
    }

    void stairs(int x, int y, int z, Material m, BlockFace facing) {
        Stairs s = (Stairs) m.createBlockData();
        s.setFacing(facing);
        set(x, y, z, s);
    }

    void slabTop(int x, int y, int z, Material m) {
        Slab s = (Slab) m.createBlockData();
        s.setType(Slab.Type.TOP);
        set(x, y, z, s);
    }

    void slabBottom(int x, int y, int z, Material m) {
        Slab s = (Slab) m.createBlockData();
        s.setType(Slab.Type.BOTTOM);
        set(x, y, z, s);
    }

    void facing(int x, int y, int z, Material m, BlockFace f) {
        BlockData d = m.createBlockData();
        if (d instanceof Directional dir && dir.getFaces().contains(f)) dir.setFacing(f);
        set(x, y, z, d);
    }

    void lit(int x, int y, int z, Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof Lightable l) l.setLit(true);
        set(x, y, z, d);
    }
}
