package fr.eemicraft.murder;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Une map de Murder : une zone (min/max) d'un monde, dont les joueurs ne peuvent pas sortir,
 * avec ses points d'apparition et ses emplacements d'or.
 */
final class GameMap {

    final String id;
    final String name;
    final String worldName;
    final int[] min;
    final int[] max;
    final List<int[]> spawns;
    final List<int[]> gold;

    private GameMap(String id, String name, String worldName, int[] min, int[] max, List<int[]> spawns, List<int[]> gold) {
        this.id = id;
        this.name = name;
        this.worldName = worldName;
        this.min = min;
        this.max = max;
        this.spawns = spawns;
        this.gold = gold;
    }

    static GameMap load(String id, ConfigurationSection s) {
        int[] a = parse(s.getString("min", "0,0,0"));
        int[] b = parse(s.getString("max", "0,0,0"));
        int[] min = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
        int[] max = {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
        List<int[]> spawns = new ArrayList<>();
        for (String p : s.getStringList("spawns")) spawns.add(parse(p));
        List<int[]> gold = new ArrayList<>();
        for (String p : s.getStringList("gold")) gold.add(parse(p));
        return new GameMap(id, s.getString("name", id), s.getString("world", "murder"), min, max, spawns, gold);
    }

    /** La même map jouée dans un autre monde (copie « monde_n » d'une partie n ≥ 2). */
    GameMap withWorld(String otherWorld) {
        return new GameMap(id, name, otherWorld, min, max, spawns, gold);
    }

    /** Prête à jouer : monde chargé, zone définie, au moins un point d'apparition. */
    boolean isReady() {
        return world() != null && !spawns.isEmpty() && max[0] > min[0] && max[2] > min[2];
    }

    World world() {
        return Bukkit.getWorld(worldName);
    }

    boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        return loc.getX() >= min[0] && loc.getX() < max[0] + 1
                && loc.getY() >= min[1] - 8 && loc.getY() < max[1] + 1
                && loc.getZ() >= min[2] && loc.getZ() < max[2] + 1;
    }

    /** Sous le sol de la map : fosses-pièges (béton rouge du Manoir). */
    boolean isBelowFloor(Location loc) {
        return loc.getY() < min[1] - 1;
    }

    Location spawn(int i) {
        return toLocation(spawns.get(Math.floorMod(i, spawns.size())));
    }

    Location randomSpawn() {
        return spawn(ThreadLocalRandom.current().nextInt(spawns.size()));
    }

    Location randomGold() {
        if (gold.isEmpty()) return null;
        return toLocation(gold.get(ThreadLocalRandom.current().nextInt(gold.size())));
    }

    private Location toLocation(int[] p) {
        Location loc = new Location(world(), p[0] + 0.5, p[1], p[2] + 0.5);
        loc.setYaw(ThreadLocalRandom.current().nextInt(4) * 90f);
        return loc;
    }

    static int[] parse(String s) {
        String[] parts = s.split(",");
        int[] r = new int[3];
        for (int i = 0; i < 3; i++) r[i] = (int) Math.floor(Double.parseDouble(parts[i].trim()));
        return r;
    }
}
