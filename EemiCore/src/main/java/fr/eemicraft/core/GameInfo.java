/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.World
 */
package fr.eemicraft.core;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

final class GameInfo {
    final String id;
    String name;
    String join = "";
    List<String> joinAliases = new ArrayList<String>();
    String spectate = "";
    List<String> worlds = new ArrayList<String>();
    int min = 2;
    int max = 16;
    double multiplier = 1.0;
    Material icon = Material.PAPER;
    String state = null;
    int waiting;
    int playing;
    long lastUpdate;

    GameInfo(String id) {
        this.id = id;
        this.name = id;
    }

    boolean fresh(long expireMs) {
        return this.state != null && System.currentTimeMillis() - this.lastUpdate < expireMs;
    }

    int worldPlayers() {
        int n = 0;
        for (String w : this.worlds) {
            World world = Bukkit.getWorld((String)w);
            if (world == null) continue;
            n += world.getPlayers().size();
        }
        return n;
    }

    boolean hasWorld(String world) {
        for (String w : this.worlds) {
            if (!w.equalsIgnoreCase(world)) continue;
            return true;
        }
        return false;
    }
}

