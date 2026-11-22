package fr.eemicraft.duels;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/** Statistiques par joueur dans stats.yml : Elo, victoires et défaites par kit, kills/morts FFA. */
final class Stats {

    private final File file;
    private final YamlConfiguration data;
    private final Logger log;
    private final int startElo;

    Stats(File folder, Logger log, int startElo) {
        this.file = new File(folder, "stats.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.log = log;
        this.startElo = startElo;
    }

    void setName(UUID id, String name) {
        data.set(id + ".name", name);
    }

    int elo(UUID id, Kit kit) {
        return data.getInt(id + "." + kit.key() + ".elo", startElo);
    }

    int get(UUID id, String path) {
        return data.getInt(id + "." + path, 0);
    }

    void add(UUID id, String path, int amount) {
        data.set(id + "." + path, get(id, path) + amount);
    }

    void set(UUID id, String path, int value) {
        data.set(id + "." + path, value);
    }

    /** Elo classique : K × (résultat - probabilité de gagner). Renvoie le gain du gagnant. */
    int applyElo(UUID winner, UUID loser, Kit kit, int k) {
        int ew = elo(winner, kit), el = elo(loser, kit);
        double expected = 1.0 / (1 + Math.pow(10, (el - ew) / 400.0));
        int delta = Math.max(1, (int) Math.round(k * (1 - expected)));
        set(winner, kit.key() + ".elo", ew + delta);
        set(loser, kit.key() + ".elo", Math.max(0, el - delta));
        return delta;
    }

    record Entry(String name, int value) {}

    List<Entry> top(String path, int def, int limit) {
        List<Entry> list = new ArrayList<>();
        for (String key : data.getKeys(false)) {
            ConfigurationSection s = data.getConfigurationSection(key);
            if (s == null || !s.contains(path)) continue;
            list.add(new Entry(s.getString("name", key), s.getInt(path, def)));
        }
        list.sort(Comparator.comparingInt(Entry::value).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            log.warning("Impossible d'enregistrer stats.yml : " + e.getMessage());
        }
    }
}
