package fr.eemicraft.refuge.refuge;

import fr.eemicraft.refuge.EemiRefuge;
import org.bukkit.Axis;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Le Refuge : un ancien poste fortifié compact (49 × 49), construit par le plugin dans le monde vide « refuge ».
 * Sol en y 64. Palissade de rondins (|x| ou |z| = 23), portail au nord (Le Passeur), feu de camp au centre,
 * deux miradors avec projecteurs, l'Armurerie à l'est (L'Armurier), le bureau de la Notaire à l'ouest,
 * la Réserve au sud (tonneaux et coffres : clic = sa réserve), deux tentes, sacs de sable en laine.
 * Tout est déterministe : relire la disposition sans rien poser retrouve les blocs de la réserve (place = false).
 */
final class Poste {

    static final int VERSION = 1;
    static final int F = 64;
    static final int R = 23;      // palissade
    static final int G = 26;      // bord du terrain

    private final EemiRefuge plugin;
    private World w;
    private boolean place;
    /** Blocs « réserve » (clic droit = ouvrir sa réserve). */
    final Set<Long> reserveBlocks = new HashSet<>();

    Poste(EemiRefuge plugin) {
        this.plugin = plugin;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    Location spawn(World world) {
        return new Location(world, 0.5, F + 1, 9.5, 180, 0);
    }

    /** Points des PNJ (pieds, regard). */
    static final double[][] PNJ = {
            {0.5, F + 1, -19.5, 0},      // Passeur, devant le portail, regarde le sud
            {15.5, F + 1, -0.5, 90},     // Armurier, derrière son comptoir, regarde l'ouest
            {-14.5, F + 1, -0.5, -90},   // Notaire, regarde l'est
    };

    void layout(World world, boolean build) {
        this.w = world;
        this.place = build;
        reserveBlocks.clear();
        long t0 = System.currentTimeMillis();
        List<Chunk> chunks = new ArrayList<>();
        if (build) {
            for (int cx = (-G - 2) >> 4; cx <= (G + 2) >> 4; cx++)
                for (int cz = (-G - 2) >> 4; cz <= (G + 2) >> 4; cz++) {
                    Chunk c = w.getChunkAt(cx, cz);
                    c.addPluginChunkTicket(plugin);
                    chunks.add(c);
                }
            for (Chunk c : chunks)
                for (Entity e : c.getEntities())
                    if (!(e instanceof Player)) e.remove();
            fill(-G - 2, F - 8, -G - 2, G + 2, F + 20, G + 2, Material.AIR);
        }
        ground();
        palissade();
        gate();
        mirador(-19, -19);
        mirador(19, -19);
        campfire();
        armurerie();
        notaire();
        reserve();
        tent(-13, 9);
        tent(13, 9);
        sandbags();
        lamps();
        if (build) {
            for (Chunk c : chunks) c.removePluginChunkTicket(plugin);
            plugin.getLogger().info("Refuge construit en " + (System.currentTimeMillis() - t0) + " ms.");
        }
        place = false;
    }

    // ------------------------------------------------------------------ outils

    private void set(int x, int y, int z, Material m) {
        if (!place) return;
        try {
            w.getBlockAt(x, y, z).setType(m, false);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Refuge : bloc " + m + " impossible en " + x + " " + y + " " + z + " : " + ex.getMessage());
        }
    }

    private void set(int x, int y, int z, BlockData d) {
        if (!place || d == null) return;
        try {
            w.getBlockAt(x, y, z).setBlockData(d, false);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Refuge : etat de bloc impossible en " + x + " " + y + " " + z + " : " + ex.getMessage());
        }
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        if (!place) return;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
    }

    private BlockData facing(Material m, BlockFace f) {
        BlockData d = m.createBlockData();
        if (d instanceof Directional dir && dir.getFaces().contains(f)) dir.setFacing(f);
        return d;
    }

    private BlockData axis(Material m, Axis a) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o && o.getAxes().contains(a)) o.setAxis(a);
        return d;
    }

    private BlockData slab(Material m, boolean top) {
        BlockData d = m.createBlockData();
        if (d instanceof Slab s) s.setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
        return d;
    }

    private BlockData hanging() {
        BlockData d = Material.LANTERN.createBlockData();
        if (d instanceof Lantern l) l.setHanging(true);
        return d;
    }

    private static int hash(int x, int z) {
        int h = x * 73428767 ^ z * 912931;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ (h >>> 15)) & 0x7fffffff;
    }

    // ------------------------------------------------------------------ parties

    private void ground() {
        if (!place) return;
        for (int x = -G; x <= G; x++)
            for (int z = -G; z <= G; z++) {
                int h = hash(x, z);
                boolean edge = Math.abs(x) > R || Math.abs(z) > R;
                int depth = edge ? 3 + (h % 3) : 6;
                fill(x, F - depth, z, x, F - 2, z, (h % 5 == 0) ? Material.STONE : Material.DIRT);
                set(x, F - 1, z, Material.DIRT);
                Material top;
                int r = h % 100;
                if (r < 62) top = Material.GRASS_BLOCK;
                else if (r < 80) top = Material.COARSE_DIRT;
                else if (r < 92) top = Material.PODZOL;
                else top = Material.ROOTED_DIRT;
                set(x, F, z, top);
                if (edge && r > 85) set(x, F + 1, z, Material.DEAD_BUSH);
            }
        // chemins de gravier et de terre battue : portail → feu, feu → est, ouest, sud
        for (int z = -22; z <= 0; z++) path(0, z, 1);
        for (int x = -11; x <= 11; x++) path(x, 0, 1);
        for (int z = 0; z <= 14; z++) path(0, z, 1);
    }

    private void path(int cx, int cz, int half) {
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++) {
                int x = cx + dx, z = cz + dz;
                int h = hash(x, z + 7);
                set(x, F, z, h % 3 == 0 ? Material.GRAVEL : (h % 3 == 1 ? Material.COARSE_DIRT : Material.PACKED_MUD));
            }
    }

    private void palissade() {
        for (int i = -R; i <= R; i++) {
            post(i, -R);
            post(i, R);
            post(-R, i);
            post(R, i);
        }
    }

    private void post(int x, int z) {
        if (z == -R && Math.abs(x) <= 2) return; // portail
        int top = F + 4 + (hash(x, z) % 2);
        fill(x, F + 1, z, x, top, z, Material.SPRUCE_LOG);
        set(x, top + 1, z, Material.SPRUCE_FENCE);
    }

    private void gate() {
        // montants et linteau
        for (int x : new int[]{-3, 3}) fill(x, F + 1, -R, x, F + 6, -R, Material.STRIPPED_SPRUCE_LOG);
        for (int x = -3; x <= 3; x++) set(x, F + 6, -R, axis(Material.STRIPPED_SPRUCE_LOG, Axis.X));
        // vantaux fermés
        fill(-2, F + 1, -R, 2, F + 5, -R, Material.SPRUCE_PLANKS);
        for (int x = -2; x <= 2; x++) set(x, F + 3, -R, axis(Material.STRIPPED_DARK_OAK_LOG, Axis.X));
        set(0, F + 1, -R, Material.DARK_OAK_PLANKS);
        set(0, F + 2, -R, Material.DARK_OAK_PLANKS);
        // lanternes du portail
        set(-3, F + 7, -R, Material.LANTERN);
        set(3, F + 7, -R, Material.LANTERN);
        set(-2, F + 5, -R + 1, hanging());
        set(2, F + 5, -R + 1, hanging());
        set(-2, F + 6, -R + 1, Material.SPRUCE_FENCE);
        set(2, F + 6, -R + 1, Material.SPRUCE_FENCE);
        // tableau des expéditions (barils, cartes) à côté du Passeur
        set(-3, F + 1, -R + 3, Material.CARTOGRAPHY_TABLE);
        set(-4, F + 1, -R + 3, facing(Material.BARREL, BlockFace.UP));
        set(3, F + 1, -R + 3, facing(Material.BARREL, BlockFace.UP));
        set(3, F + 2, -R + 3, Material.LANTERN);
    }

    private void mirador(int cx, int cz) {
        for (int dx : new int[]{-2, 2})
            for (int dz : new int[]{-2, 2}) fill(cx + dx, F + 1, cz + dz, cx + dx, F + 7, cz + dz, Material.SPRUCE_LOG);
        fill(cx - 2, F + 7, cz - 2, cx + 2, F + 7, cz + 2, Material.SPRUCE_PLANKS);
        // garde-corps (on ne saute pas par-dessus une clôture)
        for (int i = -2; i <= 2; i++) {
            set(cx + i, F + 8, cz - 2, Material.SPRUCE_FENCE);
            set(cx + i, F + 8, cz + 2, Material.SPRUCE_FENCE);
            set(cx - 2, F + 8, cz + i, Material.SPRUCE_FENCE);
            set(cx + 2, F + 8, cz + i, Material.SPRUCE_FENCE);
        }
        set(cx, F + 8, cz + 2, Material.AIR); // arrivée de l'échelle
        for (int dx : new int[]{-2, 2})
            for (int dz : new int[]{-2, 2}) set(cx + dx, F + 9, cz + dz, Material.SPRUCE_FENCE);
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++) set(x, F + 10, z, slab(Material.SPRUCE_SLAB, false));
        // échelle côté sud, contre un poteau
        fill(cx, F + 1, cz + 2, cx, F + 6, cz + 2, Material.SPRUCE_LOG);
        for (int y = F + 1; y <= F + 7; y++) set(cx, y, cz + 3, facing(Material.LADDER, BlockFace.SOUTH));
        // projecteur : lanterne marine entre deux trappes de fer, tourné vers l'extérieur
        int px = cx + (cx < 0 ? -1 : 1), pz = cz - 1;
        set(px, F + 8, pz, Material.SEA_LANTERN);
        set(px, F + 9, pz, facing(Material.IRON_TRAPDOOR, BlockFace.NORTH));
        set(cx, F + 8, cz, facing(Material.BARREL, BlockFace.UP));
    }

    private void campfire() {
        BlockData fire = Material.CAMPFIRE.createBlockData();
        if (fire instanceof Lightable l) l.setLit(true);
        set(0, F + 1, 0, fire);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0) set(dx, F, dz, (dx + dz) % 2 == 0 ? Material.COBBLESTONE : Material.MOSSY_COBBLESTONE);
        // bancs en rondins
        for (int i = -1; i <= 1; i++) {
            set(i, F + 1, -3, axis(Material.STRIPPED_SPRUCE_LOG, Axis.X));
            set(i, F + 1, 3, axis(Material.STRIPPED_SPRUCE_LOG, Axis.X));
            set(-3, F + 1, i, axis(Material.STRIPPED_SPRUCE_LOG, Axis.Z));
            set(3, F + 1, i, axis(Material.STRIPPED_SPRUCE_LOG, Axis.Z));
        }
    }

    /** Abri ouvert sur une face : murs en planches, poteaux en rondins, toit en dalles. */
    private void shed(int x1, int z1, int x2, int z2, BlockFace open) {
        int h = 4;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                boolean front = (open == BlockFace.WEST && x == x1) || (open == BlockFace.EAST && x == x2)
                        || (open == BlockFace.NORTH && z == z1) || (open == BlockFace.SOUTH && z == z2);
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                set(x, F, z, Material.SPRUCE_PLANKS);
                if (corner) fill(x, F + 1, z, x, F + h, z, Material.SPRUCE_LOG);
                else if (wall && !front) {
                    fill(x, F + 1, z, x, F + h, z, Material.SPRUCE_PLANKS);
                    if ((x + z) % 3 == 0) set(x, F + 2, z, Material.SPRUCE_TRAPDOOR);
                }
            }
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) set(x, F + h + 1, z, slab(Material.DARK_OAK_SLAB, false));
        // poutre de façade
        if (open == BlockFace.WEST || open == BlockFace.EAST) {
            int x = open == BlockFace.WEST ? x1 : x2;
            for (int z = z1; z <= z2; z++) set(x, F + h, z, axis(Material.STRIPPED_SPRUCE_LOG, Axis.Z));
        } else {
            int z = open == BlockFace.NORTH ? z1 : z2;
            for (int x = x1; x <= x2; x++) set(x, F + h, z, axis(Material.STRIPPED_SPRUCE_LOG, Axis.X));
        }
    }

    private void armurerie() {
        shed(12, -5, 19, 4, BlockFace.WEST);
        // comptoir
        for (int z = -2; z <= 1; z++) set(13, F + 1, z, slab(Material.SPRUCE_SLAB, true));
        set(13, F + 1, -3, facing(Material.BARREL, BlockFace.UP));
        set(13, F + 1, 2, facing(Material.BARREL, BlockFace.UP));
        // atelier
        set(18, F + 1, -4, Material.ANVIL);
        set(18, F + 1, -2, Material.SMITHING_TABLE);
        set(18, F + 1, 1, Material.GRINDSTONE);
        set(18, F + 1, 3, Material.BLAST_FURNACE);
        set(16, F + 1, 3, facing(Material.BARREL, BlockFace.UP));
        set(16, F + 4, -1, hanging());
        set(14, F + 4, -4, hanging());
    }

    private void notaire() {
        shed(-19, -5, -12, 4, BlockFace.EAST);
        for (int z = -2; z <= 1; z++) set(-13, F + 1, z, slab(Material.BIRCH_SLAB, true));
        set(-13, F + 2, 2, Material.LANTERN);
        set(-18, F + 1, -4, Material.BOOKSHELF);
        set(-18, F + 2, -4, Material.BOOKSHELF);
        set(-18, F + 1, -3, Material.BOOKSHELF);
        set(-18, F + 1, 3, Material.CARTOGRAPHY_TABLE);
        set(-18, F + 1, 1, facing(Material.LECTERN, BlockFace.EAST));
        set(-16, F + 4, -1, hanging());
    }

    private void reserve() {
        shed(-6, 13, 6, 20, BlockFace.NORTH);
        for (int x = -5; x <= 5; x++) {
            Material m = x % 2 == 0 ? Material.BARREL : Material.CHEST;
            BlockData d = m == Material.BARREL ? facing(Material.BARREL, BlockFace.UP) : facing(Material.CHEST, BlockFace.NORTH);
            set(x, F + 1, 19, d);
            reserveBlocks.add(key(x, F + 1, 19));
            if (x % 2 == 0) {
                set(x, F + 2, 19, facing(Material.BARREL, BlockFace.UP));
                reserveBlocks.add(key(x, F + 2, 19));
            }
        }
        set(-5, F + 1, 15, Material.HAY_BLOCK);
        set(5, F + 1, 15, facing(Material.BARREL, BlockFace.UP));
        set(0, F + 4, 16, hanging());
    }

    private void tent(int cx, int cz) {
        for (int z = cz - 2; z <= cz + 2; z++) {
            set(cx - 2, F + 1, z, Material.WHITE_WOOL);
            set(cx + 2, F + 1, z, Material.WHITE_WOOL);
            set(cx - 1, F + 2, z, Material.WHITE_WOOL);
            set(cx + 1, F + 2, z, Material.WHITE_WOOL);
            set(cx, F + 3, z, Material.LIGHT_GRAY_WOOL);
            set(cx, F, z, Material.SPRUCE_PLANKS);
        }
        set(cx, F + 1, cz + 1, Material.RED_CARPET);
        set(cx - 1, F + 1, cz - 1, facing(Material.BARREL, BlockFace.UP));
    }

    private void sandbags() {
        int[][] rows = {{-8, -19, -4, -19}, {4, -19, 8, -19}, {-20, 8, -20, 12}, {20, 8, 20, 12}};
        for (int[] r : rows)
            for (int x = r[0]; x <= r[2]; x++)
                for (int z = r[1]; z <= r[3]; z++) {
                    set(x, F + 1, z, Material.LIGHT_GRAY_WOOL);
                    set(x, F + 2, z, (x + z) % 2 == 0 ? Material.LIGHT_GRAY_WOOL : Material.LIGHT_GRAY_CARPET);
                }
    }

    private void lamps() {
        int[][] pts = {{-6, -10}, {6, -10}, {-8, 6}, {8, 6}, {-9, 18}, {9, 18}};
        for (int[] p : pts) {
            fill(p[0], F + 1, p[1], p[0], F + 3, p[1], Material.SPRUCE_FENCE);
            set(p[0], F + 4, p[1], Material.LANTERN);
        }
    }
}
