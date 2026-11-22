package fr.eemicraft.abysse;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Données de l'Abysse : joueurs ({@code joueurs/<uuid>.yml}), Compagnies et leurs machines
 * ({@code compagnies.yml}). Tout est gardé en mémoire et sauvegardé toutes les 5 min et à l'arrêt.
 */
final class Store {

    static final String[] JOBS = {"mineur", "ingenieur", "chasseur"};

    static final class PlayerData {
        final UUID id;
        String name = "";
        String company;
        /** Index de la quête principale en cours (0 = pas encore parlé à Gaspard). */
        int quest;
        /** Avancement de l'objectif de la quête en cours. */
        double questProgress;
        /** Strate la plus profonde débloquée (1 au départ). */
        int unlocked = 1;
        final Map<String, Integer> jobXp = new HashMap<>();
        /** Pages lues, « strate:numéro ». */
        final Set<String> pages = new HashSet<>();
        final Set<Integer> pageRewards = new HashSet<>();
        /** Machines dont l'explication du guide a déjà été donnée à la première pose. */
        final Set<String> guides = new HashSet<>();
        int kills;
        boolean kitGiven;
        /** Bilan d'absence : départ (ms, 0 = rien à dire) et compteurs de la Compagnie à ce moment-là. */
        long awayAt;
        long awayProduced;
        double awaySold;

        PlayerData(UUID id) {
            this.id = id;
        }

        int xp(String job) {
            return jobXp.getOrDefault(job, 0);
        }
    }

    static final class Machine {
        final String id;
        String type;
        String world;
        int x, y, z;
        final Map<String, Integer> in = new LinkedHashMap<>();
        final Map<String, Integer> out = new LinkedHashMap<>();
        /** Recette en cours (null = à l'arrêt) et temps restant en secondes. */
        String running;
        double left;
        /** Recette choisie (assembleur). */
        String recipe;
        /** Machine qui reçoit les produits (liaison par entonnoirs), ou null. */
        String target;
        /** Laveur : de l'eau juste à côté. */
        boolean water;
        /** Foreuse : usure (100 = neuve) et minerai extrait. */
        double durability = 100;
        String ore;
        /** Foreuse : 1 = normale, 2 = renforcée. */
        int tier = 1;
        /** Foreuse : quai de réception qui reçoit tout ce qu'elle extrait (null = monte-charge commun). */
        String dest;
        /** Modules posés dans la machine (vitesse, économie, rendement ; cellules dans un générateur). */
        final java.util.List<String> modules = new java.util.ArrayList<>();
        /** Trieur : filtre de chaque sortie (north/east/south/west → objet). */
        final Map<String, String> filters = new LinkedHashMap<>();
        /** Séparateur et trieur : sorties trouvées par les convoyeurs, et tour de rôle. */
        transient java.util.List<Machines.Out> outs;
        transient int rr;
        /** Ligne de convoyeurs vers la cible (positions x, y, z), null si la liaison passe par des entonnoirs. */
        transient java.util.List<int[]> belt;
        /** Dernier message d'état (affiché au-dessus). */
        transient String state = "";

        Machine(String id) {
            this.id = id;
        }

        int count(Map<String, Integer> m) {
            int n = 0;
            for (int v : m.values()) n += v;
            return n;
        }

        boolean at(String w, int bx, int by, int bz) {
            return world.equals(w) && x == bx && y == by && z == bz;
        }
    }

    static final class Company {
        final String id;
        String name;
        UUID leader;
        final List<UUID> members = new ArrayList<>();
        double money;
        int plot = -1;
        int plotTier;
        double energy;
        long lastActive;
        final Map<String, Machine> machines = new LinkedHashMap<>();
        /** Compteurs cumulés (bilan d'absence) : objets produits par l'usine et les foreuses, éclats des quais d'expédition. */
        long produced;
        double autoSold;
        /** Dernière mise en veille de l'usine (personne de la Compagnie connecté) : début, fin (0 = encore en veille). */
        long sleepStart, sleepEnd;
        /** Minerai des foreuses en attente d'un Quai de réception. */
        final Map<String, Integer> waiting = new LinkedHashMap<>();

        Company(String id) {
            this.id = id;
        }

        boolean online() {
            for (UUID u : members) if (Bukkit.getPlayer(u) != null) return true;
            return false;
        }
    }

    private final EemiAbysse plugin;
    final Map<UUID, PlayerData> players = new HashMap<>();
    final Map<String, Company> companies = new LinkedHashMap<>();
    int nextPlot;
    private int nextMachine;

    Store(EemiAbysse plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ joueurs

    PlayerData player(Player p) {
        PlayerData d = player(p.getUniqueId());
        d.name = p.getName();
        return d;
    }

    PlayerData player(UUID id) {
        PlayerData d = players.get(id);
        if (d != null) return d;
        d = new PlayerData(id);
        File f = new File(plugin.getDataFolder(), "joueurs/" + id + ".yml");
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            d.name = y.getString("name", "");
            d.company = y.getString("company");
            d.quest = y.getInt("quest");
            d.questProgress = y.getDouble("quest-progress");
            d.unlocked = y.getInt("unlocked", 1);
            d.kills = y.getInt("kills");
            d.kitGiven = y.getBoolean("kit");
            ConfigurationSection j = y.getConfigurationSection("jobs");
            if (j != null) for (String k : j.getKeys(false)) d.jobXp.put(k, j.getInt(k));
            d.pages.addAll(y.getStringList("pages"));
            for (int i : y.getIntegerList("page-rewards")) d.pageRewards.add(i);
            d.guides.addAll(y.getStringList("guides"));
            d.awayAt = y.getLong("away.at");
            d.awayProduced = y.getLong("away.produced");
            d.awaySold = y.getDouble("away.sold");
        }
        players.put(id, d);
        return d;
    }

    private void savePlayer(PlayerData d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", d.name);
        y.set("company", d.company);
        y.set("quest", d.quest);
        y.set("quest-progress", d.questProgress);
        y.set("unlocked", d.unlocked);
        y.set("kills", d.kills);
        y.set("kit", d.kitGiven);
        for (Map.Entry<String, Integer> e : d.jobXp.entrySet()) y.set("jobs." + e.getKey(), e.getValue());
        y.set("pages", new ArrayList<>(d.pages));
        y.set("page-rewards", new ArrayList<>(d.pageRewards));
        y.set("guides", new ArrayList<>(d.guides));
        if (d.awayAt > 0) {
            y.set("away.at", d.awayAt);
            y.set("away.produced", d.awayProduced);
            y.set("away.sold", d.awaySold);
        }
        try {
            y.save(new File(plugin.getDataFolder(), "joueurs/" + d.id + ".yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Sauvegarde du joueur " + d.name + " impossible : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ compagnies

    /** Compagnie du joueur, créée (seul) si besoin. */
    Company company(Player p) {
        PlayerData d = player(p);
        Company c = d.company == null ? null : companies.get(d.company);
        if (c == null) {
            c = new Company(UUID.randomUUID().toString().substring(0, 8));
            c.name = "Compagnie de " + p.getName();
            c.leader = p.getUniqueId();
            c.members.add(p.getUniqueId());
            c.money = plugin.getConfig().getDouble("start-money", 200);
            c.lastActive = System.currentTimeMillis();
            companies.put(c.id, c);
            d.company = c.id;
        }
        return c;
    }

    Company companyOf(UUID player) {
        PlayerData d = player(player);
        return d.company == null ? null : companies.get(d.company);
    }

    Company companyByPlot(int plot) {
        for (Company c : companies.values()) if (c.plot == plot) return c;
        return null;
    }

    String newMachineId() {
        return "m" + (++nextMachine);
    }

    Machine machineAt(String world, int x, int y, int z) {
        for (Company c : companies.values())
            for (Machine m : c.machines.values()) if (m.at(world, x, y, z)) return m;
        return null;
    }

    Company owner(Machine m) {
        for (Company c : companies.values()) if (c.machines.containsKey(m.id)) return c;
        return null;
    }

    // ------------------------------------------------------------------ fichiers

    void load() {
        File f = new File(plugin.getDataFolder(), "compagnies.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        nextPlot = y.getInt("next-plot");
        nextMachine = y.getInt("next-machine");
        ConfigurationSection all = y.getConfigurationSection("companies");
        if (all == null) return;
        for (String id : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(id);
            Company c = new Company(id);
            c.name = s.getString("name", id);
            c.leader = UUID.fromString(s.getString("leader"));
            for (String m : s.getStringList("members")) c.members.add(UUID.fromString(m));
            c.money = s.getDouble("money");
            c.plot = s.getInt("plot", -1);
            c.plotTier = s.getInt("plot-tier");
            c.energy = s.getDouble("energy");
            c.lastActive = s.getLong("last-active");
            c.produced = s.getLong("produced");
            c.autoSold = s.getDouble("auto-sold");
            c.sleepStart = s.getLong("sleep-start");
            c.sleepEnd = s.getLong("sleep-end");
            readCounts(s.getConfigurationSection("waiting"), c.waiting);
            ConfigurationSection ms = s.getConfigurationSection("machines");
            if (ms != null) for (String mid : ms.getKeys(false)) {
                ConfigurationSection q = ms.getConfigurationSection(mid);
                Machine m = new Machine(mid);
                m.type = q.getString("type");
                m.world = q.getString("world");
                m.x = q.getInt("x");
                m.y = q.getInt("y");
                m.z = q.getInt("z");
                m.running = q.getString("running");
                m.left = q.getDouble("left");
                m.recipe = q.getString("recipe");
                m.target = q.getString("target");
                m.water = q.getBoolean("water");
                m.durability = q.getDouble("durability", 100);
                m.tier = q.getInt("tier", 1);
                m.dest = q.getString("dest");
                m.ore = q.getString("ore");
                m.modules.addAll(q.getStringList("modules"));
                var fs = q.getConfigurationSection("filters");
                if (fs != null) for (String k : fs.getKeys(false)) m.filters.put(k, fs.getString(k));
                readCounts(q.getConfigurationSection("in"), m.in);
                readCounts(q.getConfigurationSection("out"), m.out);
                c.machines.put(mid, m);
            }
            companies.put(id, c);
        }
    }

    private static void readCounts(ConfigurationSection s, Map<String, Integer> into) {
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            int v = s.getInt(k);
            if (v > 0 && Items.def(k) != null) into.put(k, v);
        }
    }

    void saveAll() {
        for (PlayerData d : players.values()) savePlayer(d);
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-plot", nextPlot);
        y.set("next-machine", nextMachine);
        for (Company c : companies.values()) {
            String b = "companies." + c.id + ".";
            y.set(b + "name", c.name);
            y.set(b + "leader", c.leader.toString());
            List<String> mem = new ArrayList<>();
            for (UUID u : c.members) mem.add(u.toString());
            y.set(b + "members", mem);
            y.set(b + "money", c.money);
            y.set(b + "plot", c.plot);
            y.set(b + "plot-tier", c.plotTier);
            y.set(b + "energy", c.energy);
            y.set(b + "last-active", c.lastActive);
            y.set(b + "produced", c.produced);
            y.set(b + "auto-sold", c.autoSold);
            y.set(b + "sleep-start", c.sleepStart);
            y.set(b + "sleep-end", c.sleepEnd);
            for (Map.Entry<String, Integer> e : c.waiting.entrySet()) y.set(b + "waiting." + e.getKey(), e.getValue());
            for (Machine m : c.machines.values()) {
                String mb = b + "machines." + m.id + ".";
                y.set(mb + "type", m.type);
                y.set(mb + "world", m.world);
                y.set(mb + "x", m.x);
                y.set(mb + "y", m.y);
                y.set(mb + "z", m.z);
                y.set(mb + "running", m.running);
                y.set(mb + "left", m.left);
                y.set(mb + "recipe", m.recipe);
                y.set(mb + "target", m.target);
                y.set(mb + "water", m.water);
                y.set(mb + "durability", m.durability);
                if (m.tier > 1) y.set(mb + "tier", m.tier);
                if (m.dest != null) y.set(mb + "dest", m.dest);
                y.set(mb + "ore", m.ore);
                y.set(mb + "modules", new ArrayList<>(m.modules));
                for (Map.Entry<String, String> f : m.filters.entrySet()) y.set(mb + "filters." + f.getKey(), f.getValue());
                for (Map.Entry<String, Integer> e : m.in.entrySet()) y.set(mb + "in." + e.getKey(), e.getValue());
                for (Map.Entry<String, Integer> e : m.out.entrySet()) y.set(mb + "out." + e.getKey(), e.getValue());
            }
        }
        try {
            y.save(new File(plugin.getDataFolder(), "compagnies.yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Sauvegarde des compagnies impossible : " + ex.getMessage());
        }
        // on oublie les joueurs déconnectés (ils sont sauvegardés)
        players.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }
}
