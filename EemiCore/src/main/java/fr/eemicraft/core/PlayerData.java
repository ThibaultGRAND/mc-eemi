/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 */
package fr.eemicraft.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class PlayerData {
    final UUID uuid;
    String name = "?";
    long coins;
    long xp;
    int level = 1;
    final Map<String, Map<String, Integer>> stats = new HashMap<String, Map<String, Integer>>();
    final Set<String> owned = new HashSet<String>();
    final Map<String, String> equipped = new HashMap<String, String>();
    String questDate = "";
    final List<Quest> quests = new ArrayList<Quest>();
    boolean calls = true;
    /* 1.6.0 : recompense quotidienne (jour de Paris AAAA-MM-JJ, serie de jours consecutifs) et bonus du jour. */
    String dailyDate = "";
    int streak;
    String firstPlayDate = "";
    String firstWinDate = "";

    PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    int stat(String game, String type) {
        Map<String, Integer> m = this.stats.get(game);
        return m == null ? 0 : m.getOrDefault(type, 0);
    }

    void addStat(String game, String type, int n) {
        this.stats.computeIfAbsent(game, k -> new HashMap<String, Integer>()).merge(type, n, Integer::sum);
    }

    int total(String type) {
        int t = 0;
        for (Map<String, Integer> m : this.stats.values()) {
            t += m.getOrDefault(type, 0).intValue();
        }
        return t;
    }

    static PlayerData load(File file, UUID uuid) {
        PlayerData d = new PlayerData(uuid);
        YamlConfiguration y = YamlConfiguration.loadConfiguration((File)file);
        d.name = y.getString("name", "?");
        d.coins = y.getLong("coins", 0L);
        d.xp = y.getLong("xp", 0L);
        d.level = Math.max(1, y.getInt("level", 1));
        ConfigurationSection st = y.getConfigurationSection("stats");
        if (st != null) {
            for (Object game : st.getKeys(false)) {
                ConfigurationSection g = st.getConfigurationSection((String)game);
                if (g == null) continue;
                for (String type : g.getKeys(false)) {
                    d.stats.computeIfAbsent((String)game, k -> new HashMap<String, Integer>()).put(type, g.getInt(type));
                }
            }
        }
        d.owned.addAll(y.getStringList("cosmetics.owned"));
        ConfigurationSection eq = y.getConfigurationSection("cosmetics.equipped");
        if (eq != null) {
            for (String k2 : eq.getKeys(false)) {
                String v = eq.getString(k2, "");
                if (v.isEmpty()) continue;
                d.equipped.put(k2, v);
            }
        }
        d.questDate = y.getString("quests.date", "");
        ConfigurationSection qs = y.getConfigurationSection("quests.list");
        if (qs != null) {
            for (String k3 : qs.getKeys(false)) {
                ConfigurationSection q = qs.getConfigurationSection(k3);
                if (q == null) continue;
                Quest quest = new Quest();
                quest.id = q.getString("id", k3);
                quest.type = q.getString("type", "play");
                quest.game = q.getString("game", "");
                quest.target = Math.max(1, q.getInt("target", 1));
                quest.progress = q.getInt("progress", 0);
                quest.done = q.getBoolean("done", false);
                quest.coins = q.getInt("coins", 0);
                quest.xp = q.getInt("xp", 0);
                quest.text = q.getString("text", quest.id);
                d.quests.add(quest);
            }
        }
        d.calls = y.getBoolean("settings.calls", true);
        d.dailyDate = y.getString("daily.date", "");
        d.streak = Math.max(0, y.getInt("daily.streak", 0));
        d.firstPlayDate = y.getString("daily.first-play", "");
        d.firstWinDate = y.getString("daily.first-win", "");
        return d;
    }

    String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", this.name);
        y.set("coins", this.coins);
        y.set("xp", this.xp);
        y.set("level", this.level);
        for (Map.Entry<String, Map<String, Integer>> e : this.stats.entrySet()) {
            for (Map.Entry<String, Integer> s : e.getValue().entrySet()) {
                y.set("stats." + e.getKey() + "." + s.getKey(), s.getValue());
            }
        }
        y.set("cosmetics.owned", new ArrayList<String>(this.owned));
        LinkedHashMap<String, String> eq = new LinkedHashMap<String, String>(this.equipped);
        y.createSection("cosmetics.equipped", eq);
        y.set("quests.date", this.questDate);
        int i = 0;
        for (Quest q : this.quests) {
            String p = "quests.list." + i++ + ".";
            y.set(p + "id", q.id);
            y.set(p + "type", q.type);
            y.set(p + "game", q.game);
            y.set(p + "target", q.target);
            y.set(p + "progress", q.progress);
            y.set(p + "done", q.done);
            y.set(p + "coins", q.coins);
            y.set(p + "xp", q.xp);
            y.set(p + "text", q.text);
        }
        y.set("settings.calls", this.calls);
        y.set("daily.date", this.dailyDate);
        y.set("daily.streak", this.streak);
        y.set("daily.first-play", this.firstPlayDate);
        y.set("daily.first-win", this.firstWinDate);
        return y.saveToString();
    }

    static final class Quest {
        String id;
        String type;
        String game;
        int target;
        int progress;
        boolean done;
        int coins;
        int xp;
        String text;

        Quest() {
        }
    }
}

