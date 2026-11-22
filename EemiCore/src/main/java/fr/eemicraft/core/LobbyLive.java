package fr.eemicraft.core;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 1.7.0 : ce qui fait vivre les hologrammes du lobby.
 * <ul>
 *   <li>Fil des parties : victoires, parties lancées, paliers de niveau (%eemi_feed_1..8%), gardé dans feed.yml.</li>
 *   <li>Joueurs par catégorie de la boussole (%eemi_cat_&lt;cat&gt;%, %eemi_catplayers_&lt;cat&gt;%).</li>
 *   <li>Classement des victoires qui change de jeu toutes les N secondes (%eemi_toprot_title%, %eemi_toprot_&lt;rang&gt;%).</li>
 *   <li>Lignes de classement déjà mises en forme (%eemi_rank_&lt;jeu|level&gt;_&lt;rang&gt;%).</li>
 * </ul>
 */
final class LobbyLive {

    private static final int FEED_MAX = 8;

    private static final class Event {
        String kind;      // win, start, level
        String game;      // id du jeu (win/start)
        List<String> names = new ArrayList<>();
        String extra;     // nombre de joueurs (start), niveau (level)
        long time;
    }

    private final EemiCore core;
    private final File file;
    private final List<Event> feed = new ArrayList<>();
    private final Map<String, Long> lastStart = new LinkedHashMap<>();
    private boolean dirty;

    LobbyLive(EemiCore core) {
        this.core = core;
        this.file = new File(core.getDataFolder(), "feed.yml");
        load();
    }

    // ------------------------------------------------------------------ événements

    void win(String name, GameInfo g) {
        long now = System.currentTimeMillis();
        // plusieurs gagnants de la même partie (équipe, égalité) = une seule ligne
        for (Event e : feed) {
            if (now - e.time > 15000L) break;
            if (e.kind.equals("win") && e.game.equals(g.id)) {
                if (!e.names.contains(name)) e.names.add(name);
                dirty = true;
                return;
            }
        }
        Event e = new Event();
        e.kind = "win";
        e.game = g.id;
        e.names.add(name);
        e.time = now;
        push(e);
    }

    /** Appelé à chaque statut reçu : une forte hausse des joueurs « en partie » = une partie vient de commencer. */
    void status(GameInfo g, int oldPlaying, int newPlaying) {
        if (newPlaying - oldPlaying < 2) return;
        long now = System.currentTimeMillis();
        Long last = lastStart.get(g.id);
        if (last != null && now - last < 30000L) return;
        lastStart.put(g.id, now);
        Event e = new Event();
        e.kind = "start";
        e.game = g.id;
        e.extra = String.valueOf(newPlaying - oldPlaying);
        e.time = now;
        push(e);
    }

    void levelUp(String name, int level) {
        int every = Math.max(1, core.getConfig().getInt("live.feed-level-every", 5));
        if (level % every != 0) return;
        Event e = new Event();
        e.kind = "level";
        e.game = "";
        e.names.add(name);
        e.extra = String.valueOf(level);
        e.time = System.currentTimeMillis();
        push(e);
    }

    private void push(Event e) {
        feed.add(0, e);
        while (feed.size() > FEED_MAX) feed.remove(feed.size() - 1);
        dirty = true;
    }

    private String text(Event e) {
        String who = names(e.names);
        switch (e.kind) {
            case "win": {
                GameInfo g = core.game(e.game);
                String game = g == null ? e.game : g.name;
                return "&f" + who + " &7" + (e.names.size() > 1 ? "gagnent" : "gagne") + " " + game;
            }
            case "start": {
                GameInfo g = core.game(e.game);
                String game = g == null ? e.game : g.name;
                return "&7Partie de " + game + " &7lancée &8(&f" + e.extra + " joueurs&8)";
            }
            default: {
                int lv = EemiCore.parseInt(e.extra, 1);
                return "&f" + who + " &7passe " + core.levelColor(lv) + "niveau " + lv;
            }
        }
    }

    private static String names(List<String> n) {
        if (n.isEmpty()) return "?";
        if (n.size() == 1) return n.get(0);
        if (n.size() == 2) return n.get(0) + " &7et &f" + n.get(1);
        return n.get(0) + ", " + n.get(1) + " &7et &f" + (n.size() - 2) + " autre" + (n.size() > 3 ? "s" : "");
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000L);
        if (s < 60) return "à l'instant";
        long m = s / 60;
        if (m < 60) return "il y a " + m + " min";
        long h = m / 60;
        if (h < 24) return "il y a " + h + " h";
        long d = h / 24;
        return "il y a " + d + " j";
    }

    // ------------------------------------------------------------------ catégories

    private static final Map<String, List<String>> DEFAULT_CATS = new LinkedHashMap<>();
    static {
        DEFAULT_CATS.put("iles", List.of("skyblock", "world:oneblock*", "world:abysse*"));
        DEFAULT_CATS.put("survie", List.of("uhc", "speedrun", "bingo"));
        DEFAULT_CATS.put("equipes", List.of("towers", "thebridge", "bedwars", "ballsofsteel", "sheepwars", "paintball"));
        DEFAULT_CATS.put("combat", List.of("skywars", "hungergames", "duels", "ffa", "pitchout", "jumpleague"));
        DEFAULT_CATS.put("tnt", List.of("tntrun", "tnttag", "bowspleef", "pvprun", "tntwars", "tntwizards"));
        DEFAULT_CATS.put("ambiance", List.of("murder", "loupgarou", "hideseek", "blockparty", "buildbattle", "boatrace",
                "deacoudre", "elytra", "soleil", "infection", "dropper", "jump", "world:horscours"));
        DEFAULT_CATS.put("casino", List.of("casino"));
    }

    private List<String> category(String id) {
        ConfigurationSection s = core.getConfig().getConfigurationSection("live.categories");
        if (s != null && s.isList(id)) return s.getStringList(id);
        return DEFAULT_CATS.get(id);
    }

    /** [en jeu, en attente] ; null si la catégorie n'existe pas. */
    private int[] categoryCount(String id) {
        List<String> entries = category(id);
        if (entries == null) return null;
        int playing = 0, waiting = 0;
        for (String raw : entries) {
            String entry = raw.toLowerCase(Locale.ROOT).trim();
            if (entry.startsWith("world:")) {
                String w = entry.substring(6);
                boolean prefix = w.endsWith("*");
                if (prefix) w = w.substring(0, w.length() - 1);
                for (World world : Bukkit.getWorlds()) {
                    String n = world.getName().toLowerCase(Locale.ROOT);
                    if (prefix ? n.startsWith(w) : n.equals(w)) playing += world.getPlayers().size();
                }
                continue;
            }
            GameInfo g = core.game(entry);
            if (g == null) continue;
            if (g.fresh(core.expireMs())) {
                playing += g.playing;
                waiting += g.waiting;
            } else {
                playing += g.worldPlayers();
            }
        }
        return new int[]{playing, waiting};
    }

    private String categoryText(int[] c) {
        if (c[0] > 0 && c[1] > 0) return "&c" + c[0] + " en jeu &8· &a" + c[1] + " en attente";
        if (c[0] > 0) return "&c" + c[0] + " en jeu";
        if (c[1] > 0) return "&a" + c[1] + " en attente";
        ConfigurationSection t = core.getConfig().getConfigurationSection("status-text");
        return t == null ? "&7Aucun joueur" : t.getString("nobody", "&7Aucun joueur");
    }

    // ------------------------------------------------------------------ classements

    private List<String> rotationGames() {
        List<String> out = new ArrayList<>();
        for (GameInfo g : core.allGames()) {
            if (!core.topList(g.id + ":wins").isEmpty()) out.add(g.id);
        }
        return out;
    }

    private String rotationGame() {
        List<String> ids = rotationGames();
        if (ids.isEmpty()) return null;
        long period = Math.max(3, core.getConfig().getInt("live.top-rotation-seconds", 8)) * 1000L;
        return ids.get((int) ((System.currentTimeMillis() / period) % ids.size()));
    }

    private String rankLine(String key, String type, int rank) {
        List<EemiCore.TopEntry> list = core.topList(key);
        String medal = rank == 1 ? "&6" : (rank == 2 ? "&f" : (rank == 3 ? "&c" : "&7"));
        if (rank < 1 || rank > list.size()) return "&8#" + rank + " &8-";
        EemiCore.TopEntry e = list.get(rank - 1);
        return medal + "#" + rank + " &f" + e.name() + " &8- &e" + EemiCore.entryText(type, e);
    }

    // ------------------------------------------------------------------ placeholders

    /** null = pas un placeholder de cette classe. */
    String placeholder(String p) {
        if (p.startsWith("feed_")) {
            int n = EemiCore.parseInt(p.substring(5), 0);
            if (n < 1 || n > FEED_MAX) return "";
            if (n > feed.size()) return n == 1 ? EemiCore.sec("&7Aucune partie pour l'instant") : "";
            Event e = feed.get(n - 1);
            return EemiCore.sec(text(e) + " &8· " + ago(e.time));
        }
        if (p.startsWith("catplayers_")) {
            int[] c = categoryCount(p.substring(11));
            return c == null ? "0" : String.valueOf(c[0] + c[1]);
        }
        if (p.startsWith("cat_")) {
            int[] c = categoryCount(p.substring(4));
            return c == null ? "" : EemiCore.sec(categoryText(c));
        }
        if (p.equals("toprot_title")) {
            String id = rotationGame();
            GameInfo g = id == null ? null : core.game(id);
            return EemiCore.sec("&eVictoires &8» " + (g == null ? "&7tous les jeux" : g.name));
        }
        if (p.startsWith("toprot_")) {
            int rank = EemiCore.parseInt(p.substring(7), 0);
            String id = rotationGame();
            if (id == null) return rank == 1 ? EemiCore.sec("&7Personne pour l'instant") : "";
            return EemiCore.sec(rankLine(id + ":wins", "wins", rank));
        }
        if (p.startsWith("rank_")) {
            String[] parts = p.substring(5).split("_");
            if (parts.length < 2) return "";
            int rank = EemiCore.parseInt(parts[parts.length - 1], 0);
            String id = parts[0];
            if (id.equals("level") || id.equals("niveau")) return EemiCore.sec(rankLine("level:level", "level", rank));
            String type = EemiCore.normTypeOf(parts.length >= 3 ? parts[1] : "wins");
            return EemiCore.sec(rankLine(id + ":" + type, type, rank));
        }
        return null;
    }

    // ------------------------------------------------------------------ fichier

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : y.getMapList("feed")) {
            Event e = new Event();
            e.kind = String.valueOf(m.get("kind"));
            e.game = m.get("game") == null ? "" : String.valueOf(m.get("game"));
            e.extra = m.get("extra") == null ? null : String.valueOf(m.get("extra"));
            Object t = m.get("time");
            e.time = t instanceof Number ? ((Number) t).longValue() : 0L;
            Object n = m.get("names");
            if (n instanceof List<?> l) for (Object o : l) e.names.add(String.valueOf(o));
            if (feed.size() < FEED_MAX) feed.add(e);
        }
    }

    void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Event e : feed) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", e.kind);
            m.put("game", e.game);
            m.put("names", new ArrayList<>(e.names));
            if (e.extra != null) m.put("extra", e.extra);
            m.put("time", e.time);
            list.add(m);
        }
        y.set("feed", list);
        try {
            y.save(file);
        } catch (Exception ex) {
            core.getLogger().warning("Impossible d'ecrire feed.yml : " + ex.getMessage());
        }
    }
}
