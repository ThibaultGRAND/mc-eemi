package fr.eemicraft.casino;

import fr.eemicraft.casino.build.Sink;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Sink qui ecrit dans le monde (copie de _commun/build BukkitSink) : createBlockData en cache, setBlockData sans
 * physique, etat invalide remplace par de l'air avec « Bloc invalide ignore » une fois par etat.
 */
final class BukkitSink implements Sink {
    private final World world;
    private final Logger log;
    private final Map<String, BlockData> cache = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
    private final BlockData air = Material.AIR.createBlockData();
    private long ops;

    BukkitSink(World world, Logger log) {
        this.world = world;
        this.log = log;
    }

    private BlockData data(String state) {
        BlockData d = cache.get(state);
        if (d != null) return d;
        try {
            d = Bukkit.createBlockData(state);
        } catch (IllegalArgumentException e) {
            if (invalid.add(state)) log.warning("Bloc invalide ignore : " + state);
            d = air;
        }
        cache.put(state, d);
        return d;
    }

    @Override
    public void set(int x, int y, int z, String state) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        world.getBlockAt(x, y, z).setBlockData(data(state == null ? "minecraft:air" : state), false);
        ops++;
    }

    @Override
    public String get(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return "minecraft:air";
        return world.getBlockAt(x, y, z).getBlockData().getAsString();
    }

    long operations() {
        return ops;
    }

    Set<String> invalidStates() {
        return invalid;
    }
}
