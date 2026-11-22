package fr.eemicraft.abysse;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Habillage des convoyeurs de la Concession : le bloc posé reste une terracotta émaillée magenta (vanilla, pour
 * la collision, le minage et le sens lu par {@link Machines#beltDir}), recouvert d'un cube du pack
 * ({@code eemicraft:abysse/convoyeur}, item_display à l'échelle 1,004) tourné pour que la flèche du dessus montre
 * le sens du transport. Le modèle vanilla n'est plus remplacé dans le pack : seules les terracottas émaillées
 * magenta du monde des Concessions ont cet aspect.
 * <p>
 * Positions des convoyeurs gardées dans le PDC de chaque chunk (relevé complet une seule fois par chunk, puis
 * pose / casse), habillages créés pour les chunks chargés seulement et vérifiés toutes les 2 s.
 */
final class Belts implements Listener {

    /** Version du relevé d'un chunk : la changer force un nouveau relevé de tous les chunks. */
    private static final int SCAN_VERSION = 1;

    private final EemiAbysse plugin;
    private final NamespacedKey labelKey, skinKey, posKey, scanKey;
    /** Chunk chargé (clé du chunk) → convoyeurs (position locale) → habillage. */
    private final Map<Long, Map<Integer, Skin>> chunks = new HashMap<>();

    private static final class Skin {
        UUID id;
        BlockFace dir;
        int light = -1;
    }

    Belts(EemiAbysse plugin) {
        this.plugin = plugin;
        labelKey = new NamespacedKey(plugin, "machine_label"); // même clé que les noms / habillages des machines
        skinKey = new NamespacedKey(plugin, "belt_skin");
        posKey = new NamespacedKey(plugin, "belts");
        scanKey = new NamespacedKey(plugin, "belts_scan");
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        World w = Bukkit.getWorld(Worlds.BASE);
        if (w != null) for (Chunk ch : w.getLoadedChunks()) load(ch);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 45L, 40L);
    }

    // ------------------------------------------------------------------ positions

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private static int pack(World w, int x, int y, int z) {
        return (x & 15) | (z & 15) << 4 | (y - w.getMinHeight()) << 8;
    }

    private static boolean base(World w) {
        return w.getName().equals(Worlds.BASE);
    }

    private void load(Chunk ch) {
        World w = ch.getWorld();
        PersistentDataContainer pdc = ch.getPersistentDataContainer();
        Map<Integer, Skin> belts = new HashMap<>();
        Integer scanned = pdc.get(scanKey, PersistentDataType.INTEGER);
        if (scanned == null || scanned != SCAN_VERSION) {
            // premier passage dans ce chunk : relevé de toutes les terracottas émaillées magenta déjà posées
            ChunkSnapshot snap = ch.getChunkSnapshot(false, false, false);
            int min = w.getMinHeight(), sections = (w.getMaxHeight() - min) >> 4;
            for (int sy = 0; sy < sections; sy++) {
                if (snap.isSectionEmpty(sy)) continue;
                for (int y = min + (sy << 4); y < min + (sy << 4) + 16; y++)
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++)
                            if (snap.getBlockType(x, y, z) == Material.MAGENTA_GLAZED_TERRACOTTA)
                                belts.put(pack(w, x, y, z), new Skin());
            }
            pdc.set(scanKey, PersistentDataType.INTEGER, SCAN_VERSION);
            save(ch, belts);
        } else {
            int[] list = pdc.get(posKey, PersistentDataType.INTEGER_ARRAY);
            if (list != null) for (int p : list) belts.put(p, new Skin());
        }
        if (belts.isEmpty()) chunks.remove(key(ch.getX(), ch.getZ()));
        else chunks.put(key(ch.getX(), ch.getZ()), belts);
    }

    private void save(Chunk ch, Map<Integer, Skin> belts) {
        PersistentDataContainer pdc = ch.getPersistentDataContainer();
        if (belts.isEmpty()) pdc.remove(posKey);
        else pdc.set(posKey, PersistentDataType.INTEGER_ARRAY, belts.keySet().stream().mapToInt(Integer::intValue).toArray());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (base(e.getWorld())) load(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (!base(e.getWorld())) return;
        Map<Integer, Skin> belts = chunks.remove(key(e.getChunk().getX(), e.getChunk().getZ()));
        if (belts != null) for (Skin s : belts.values()) remove(s);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        if (!base(b.getWorld()) || b.getType() != Material.MAGENTA_GLAZED_TERRACOTTA) return;
        Chunk ch = b.getChunk();
        Map<Integer, Skin> belts = chunks.computeIfAbsent(key(ch.getX(), ch.getZ()), k -> new HashMap<>());
        belts.putIfAbsent(pack(b.getWorld(), b.getX(), b.getY(), b.getZ()), new Skin());
        save(ch, belts);
        // le sens est fixé par EemiAbysse#onPlace (priorité HIGH) : on habille au tick suivant
        Bukkit.getScheduler().runTask(plugin, () -> refresh(ch));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!base(b.getWorld()) || b.getType() != Material.MAGENTA_GLAZED_TERRACOTTA) return;
        Chunk ch = b.getChunk();
        Map<Integer, Skin> belts = chunks.get(key(ch.getX(), ch.getZ()));
        if (belts == null) return;
        Skin s = belts.remove(pack(b.getWorld(), b.getX(), b.getY(), b.getZ()));
        if (s != null) remove(s);
        save(ch, belts);
        if (belts.isEmpty()) chunks.remove(key(ch.getX(), ch.getZ()));
    }

    // ------------------------------------------------------------------ habillages

    /** Toutes les 2 s : chaque convoyeur des chunks chargés a son habillage, dans le bon sens et à la bonne lumière. */
    void tick() {
        World w = Bukkit.getWorld(Worlds.BASE);
        if (w == null) {
            chunks.clear();
            return;
        }
        for (Iterator<Map.Entry<Long, Map<Integer, Skin>>> it = chunks.entrySet().iterator(); it.hasNext(); ) {
            long k = it.next().getKey();
            int cx = (int) (k >> 32), cz = (int) k;
            if (!w.isChunkLoaded(cx, cz)) {
                for (Skin s : chunks.get(k).values()) remove(s);
                it.remove();
                continue;
            }
            if (refresh(w.getChunkAt(cx, cz))) it.remove();
        }
    }

    /** Met à jour les habillages d'un chunk ; renvoie true s'il n'y a plus aucun convoyeur dedans. */
    private boolean refresh(Chunk ch) {
        World w = ch.getWorld();
        Map<Integer, Skin> belts = chunks.get(key(ch.getX(), ch.getZ()));
        if (belts == null || !ch.isLoaded()) return false;
        boolean changed = false;
        int bx = ch.getX() << 4, bz = ch.getZ() << 4, min = w.getMinHeight();
        for (Iterator<Map.Entry<Integer, Skin>> it = belts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Skin> en = it.next();
            int p = en.getKey();
            Block b = w.getBlockAt(bx + (p & 15), min + (p >>> 8), bz + (p >> 4 & 15));
            Skin s = en.getValue();
            if (!Machines.isBelt(b)) { // cassé autrement (piston, commande...) : on oublie ce convoyeur
                remove(s);
                it.remove();
                changed = true;
                continue;
            }
            show(b, s);
        }
        if (changed) save(ch, belts);
        return belts.isEmpty();
    }

    private void show(Block b, Skin s) {
        BlockFace dir = Machines.beltDir(b);
        int block = 0, sky = 0;
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
            Block n = b.getRelative(f);
            block = Math.max(block, n.getLightFromBlocks());
            sky = Math.max(sky, n.getLightFromSky());
        }
        int light = block << 4 | sky;
        Entity e = s.id == null ? null : Bukkit.getEntity(s.id);
        ItemDisplay d = e instanceof ItemDisplay x && x.isValid() ? x : null;
        if (d == null) {
            d = adopt(b, s);
            s.dir = null;
            s.light = -1;
        }
        if (d == null) {
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            d = b.getWorld().spawn(at, ItemDisplay.class, it -> {
                it.setPersistent(false);
                it.setItemStack(Items.make("convoyeur", 1));
                it.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                it.setViewRange(0.6f);
                it.getPersistentDataContainer().set(labelKey, PersistentDataType.BYTE, (byte) 1);
                it.getPersistentDataContainer().set(skinKey, PersistentDataType.BYTE, (byte) 1);
            });
            s.id = d.getUniqueId();
        }
        if (dir != s.dir) {
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(angle(dir), 0, 1, 0),
                    new Vector3f(1.004f, 1.004f, 1.004f), new AxisAngle4f()));
            s.dir = dir;
        }
        if (light != s.light) {
            d.setBrightness(new Display.Brightness(block, sky));
            s.light = light;
        }
    }

    /**
     * Rotation autour de Y pour que la flèche montre {@code dir}. Le modèle a la flèche vers le haut de la texture
     * du dessus (vers le nord du modèle) ; le client tourne toute item_display de 180° autour de Y
     * (DisplayRenderer$ItemDisplayRenderer.submitInner, relu dans le jar client 26.3), donc sans rotation la
     * flèche montre le sud ; une rotation θ (sens JOML) l'amène vers (sin θ, cos θ) en (x, z).
     */
    static float angle(BlockFace dir) {
        return (float) Math.atan2(dir.getModX(), dir.getModZ());
    }

    /** Habillage déjà présent sur ce bloc (après un oubli) : on le garde et on supprime les doublons. */
    private ItemDisplay adopt(Block b, Skin s) {
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay keep = null;
        List<Entity> extra = new ArrayList<>();
        for (Entity x : b.getWorld().getNearbyEntities(at, 0.3, 0.3, 0.3)) {
            if (!(x instanceof ItemDisplay d) || !x.isValid() || !x.getPersistentDataContainer().has(skinKey)) continue;
            if (keep == null) keep = d;
            else extra.add(x);
        }
        for (Entity x : extra) x.remove();
        s.id = keep == null ? null : keep.getUniqueId();
        return keep;
    }

    private static void remove(Skin s) {
        Entity e = s.id == null ? null : Bukkit.getEntity(s.id);
        if (e != null) e.remove();
        s.id = null;
    }

    void removeAll() {
        for (Map<Integer, Skin> belts : chunks.values()) for (Skin s : belts.values()) remove(s);
        chunks.clear();
    }
}
