package fr.eemicraft.casino;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Compte casino d'un joueur. */
final class PData {
    final UUID uuid;
    String name = "?";
    long chips;
    long wagered;
    long won;
    long biggest;
    int jackpots;
    String dailyDate = "";
    int streak;
    boolean welcomed;
    final Set<String> owned = new HashSet<>();
    final Map<String, Long> bets = new HashMap<>();

    PData(UUID uuid) {
        this.uuid = uuid;
    }

    long bet(String game, long def) {
        return bets.getOrDefault(game, def);
    }

    static PData load(UUID id, ConfigurationSection s) {
        PData d = new PData(id);
        d.name = s.getString("name", "?");
        d.chips = s.getLong("chips");
        d.wagered = s.getLong("wagered");
        d.won = s.getLong("won");
        d.biggest = s.getLong("biggest");
        d.jackpots = s.getInt("jackpots");
        d.dailyDate = s.getString("daily.date", "");
        d.streak = s.getInt("daily.streak");
        d.welcomed = s.getBoolean("welcomed");
        d.owned.addAll(s.getStringList("owned"));
        ConfigurationSection b = s.getConfigurationSection("bets");
        if (b != null) {
            for (String k : b.getKeys(false)) {
                d.bets.put(k, b.getLong(k));
            }
        }
        return d;
    }

    void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("chips", chips);
        s.set("wagered", wagered);
        s.set("won", won);
        s.set("biggest", biggest);
        s.set("jackpots", jackpots);
        s.set("daily.date", dailyDate);
        s.set("daily.streak", streak);
        s.set("welcomed", welcomed);
        s.set("owned", new java.util.ArrayList<>(owned));
        for (Map.Entry<String, Long> e : bets.entrySet()) {
            s.set("bets." + e.getKey(), e.getValue());
        }
    }
}
