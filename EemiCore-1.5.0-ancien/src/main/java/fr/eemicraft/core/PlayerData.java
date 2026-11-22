package fr.eemicraft.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Données d'un joueur, gardées en mémoire et écrites dans players/&lt;uuid&gt;.yml :
 * pseudo, pièces, xp, niveau, stats par jeu, cosmétiques, quêtes du jour, réglages.
 */
final class PlayerData {

    /** Une quête du jour tirée du pool de la config. */
    static final class Quest {
        String id;        // id de la quête dans le pool
        String type;      // play | win | kill
        String game;      // id du jeu, ou "" pour n'importe quel jeu
        int target;
        int progress;
        boolean done;
        int coins;
        int xp;
        String text;      // texte déjà résolu (%game%, %target% remplacés)
    }

    final UUID uuid;
    String name = "?";
    long coins;
    long xp;          // xp dans le niveau en cours
    int level = 1;
    /** jeu → (plays|wins|kills → valeur). */
    final Map<String, Map<String, Integer>> stats = new HashMap<>();
    final Set<String> owned = new HashSet<>();
    /** catégorie (trail|hat|title|join) → id du cosmétique équipé. */
    final Map<String, String> equipped = new HashMap<>();
    String questDate = "";
    final List<Quest> quests = new ArrayList<>();
    boolean calls = true;

    PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    int stat(String game, String type) {
        Map<String, Integer> m = stats.get(game);
        return m == null ? 0 : m.getOrDefault(type, 0);
    }

    void addStat(String game, String type, int n) {
        stats.computeIfAbsent(game, k -> new HashMap<>()).merge(type, n, Integer::sum);
    }

    /** Total d'un type de stat sur tous les jeux. */
    int total(String type) {
        int t = 0;
        for (Map<String, Integer> m : stats.values()) t += m.getOrDefault(type, 0);
        return t;
    }

    static PlayerData load(File file, UUID uuid) {
        PlayerData d = new PlayerData(uuid);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        d.name = y.getString("name", "?");
        d.coins = y.getLong("coins", 0);
        d.xp = y.getLong("xp", 0);
        d.level = Math.max(1, y.getInt("level", 1));
        ConfigurationSection st = y.getConfigurationSection("stats");
        if (st != null) {
            for (String game : st.getKeys(false)) {
                ConfigurationSection g = st.getConfigurationSection(game);
                if (g == null) continue;
                for (String type : g.getKeys(false)) d.stats.computeIfAbsent(game, k -> new HashMap<>()).put(type, g.getInt(type));
            }
        }
        d.owned.addAll(y.getStringList("cosmetics.owned"));
        ConfigurationSection eq = y.getConfigurationSection("cosmetics.equipped");
        if (eq != null) for (String k : eq.getKeys(false)) {
            String v = eq.getString(k, "");
            if (!v.isEmpty()) d.equipped.put(k, v);
        }
        d.questDate = y.getString("quests.date", "");
        ConfigurationSection qs = y.getConfigurationSection("quests.list");
        if (qs != null) {
            for (String k : qs.getKeys(false)) {
                ConfigurationSection q = qs.getConfigurationSection(k);
                if (q == null) continue;
                Quest quest = new Quest();
                quest.id = q.getString("id", k);
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
        return d;
    }

    /** Sérialise en texte YAML (à appeler sur le thread principal ; l'écriture du fichier peut être asynchrone). */
    String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("coins", coins);
        y.set("xp", xp);
        y.set("level", level);
        for (Map.Entry<String, Map<String, Integer>> e : stats.entrySet())
            for (Map.Entry<String, Integer> s : e.getValue().entrySet()) y.set("stats." + e.getKey() + "." + s.getKey(), s.getValue());
        y.set("cosmetics.owned", new ArrayList<>(owned));
        Map<String, Object> eq = new LinkedHashMap<>(equipped);
        y.createSection("cosmetics.equipped", eq);
        y.set("quests.date", questDate);
        int i = 0;
        for (Quest q : quests) {
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
        y.set("settings.calls", calls);
        return y.saveToString();
    }
}
