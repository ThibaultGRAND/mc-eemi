package fr.eemicraft.fete.build;

import fr.eemicraft.fete.build.Sink;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Sink qui écrit dans un monde Paper : {@code Bukkit.createBlockData} mis en cache par chaîne d'état,
 * {@code setBlockData(d, false)} (sans physique ni mise à jour des voisins), compteur d'opérations.
 * Un état invalide est remplacé par de l'air avec un avertissement « Bloc invalide ignore » (une fois par état).
 * Hors des limites de hauteur du monde : ignoré. À appeler sur le fil principal (comme tout setBlockData).
 */
public final class BukkitSink implements Sink {
    private final World world;
    private final Logger log;
    private final Map<String, BlockData> cache = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
    private final BlockData air = Material.AIR.createBlockData();
    private long ops;

    public BukkitSink(World world, Logger log) {
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

    public long operations() { return ops; }

    /** États refusés par le serveur (à journaliser en fin de build). */
    public Set<String> invalidStates() { return invalid; }

    public World world() { return world; }
}
