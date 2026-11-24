/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.configuration.ConfigurationSection
 */
package fr.eemicraft.murder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

final class GameMap {
    final String id;
    final String name;
    final String worldName;
    final int[] min;
    final int[] max;
    final List<int[]> spawns;
    final List<int[]> gold;
    /** 1.10.0 : petite map (cle size: small), tiree seulement quand il y a peu de joueurs (small-map-max-players). */
    final boolean small;

    private GameMap(String id, String name, String worldName, int[] min, int[] max, List<int[]> spawns, List<int[]> gold, boolean small) {
        this.small = small;
        this.id = id;
        this.name = name;
        this.worldName = worldName;
        this.min = min;
        this.max = max;
        this.spawns = spawns;
        this.gold = gold;
    }

    static GameMap load(String id, ConfigurationSection s) {
        int[] a = GameMap.parse(s.getString("min", "0,0,0"));
        int[] b = GameMap.parse(s.getString("max", "0,0,0"));
        int[] min = new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
        int[] max = new int[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
        ArrayList<int[]> spawns = new ArrayList<int[]>();
        for (String p : s.getStringList("spawns")) {
            spawns.add(GameMap.parse(p));
        }
        ArrayList<int[]> gold = new ArrayList<int[]>();
        for (String p : s.getStringList("gold")) {
            gold.add(GameMap.parse(p));
        }
        return new GameMap(id, s.getString("name", id), s.getString("world", "murder"), min, max, spawns, gold,
                "small".equalsIgnoreCase(s.getString("size", "large").trim()));
    }

    GameMap withWorld(String otherWorld) {
        return new GameMap(this.id, this.name, otherWorld, this.min, this.max, this.spawns, this.gold, this.small);
    }

    boolean isReady() {
        return this.world() != null && !this.spawns.isEmpty() && this.max[0] > this.min[0] && this.max[2] > this.min[2];
    }

    World world() {
        return Bukkit.getWorld((String)this.worldName);
    }

    boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(this.worldName)) {
            return false;
        }
        return loc.getX() >= (double)this.min[0] && loc.getX() < (double)(this.max[0] + 1) && loc.getY() >= (double)(this.min[1] - 8) && loc.getY() < (double)(this.max[1] + 1) && loc.getZ() >= (double)this.min[2] && loc.getZ() < (double)(this.max[2] + 1);
    }

    boolean isBelowFloor(Location loc) {
        return loc.getY() < (double)(this.min[1] - 1);
    }

    Location spawn(int i) {
        return this.toLocation(this.spawns.get(Math.floorMod(i, this.spawns.size())));
    }

    Location randomSpawn() {
        return this.spawn(ThreadLocalRandom.current().nextInt(this.spawns.size()));
    }

    Location randomGold() {
        if (this.gold.isEmpty()) {
            return null;
        }
        return this.toLocation(this.gold.get(ThreadLocalRandom.current().nextInt(this.gold.size())));
    }

    private Location toLocation(int[] p) {
        Location loc = new Location(this.world(), (double)p[0] + 0.5, (double)p[1], (double)p[2] + 0.5);
        loc.setYaw((float)ThreadLocalRandom.current().nextInt(4) * 90.0f);
        return loc;
    }

    static int[] parse(String s) {
        String[] parts = s.split(",");
        int[] r = new int[3];
        for (int i = 0; i < 3; ++i) {
            r[i] = (int)Math.floor(Double.parseDouble(parts[i].trim()));
        }
        return r;
    }
}

