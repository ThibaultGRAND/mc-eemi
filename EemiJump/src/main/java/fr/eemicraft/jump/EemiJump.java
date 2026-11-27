package fr.eemicraft.jump;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Jump solo : une map de parkour toute faite decoupee en niveaux (une plateforme par niveau).
 * Chaque joueur avance a son rythme, sa progression est sauvegardee (niveau atteint, record, chutes),
 * il reprend au dernier niveau a chaque entree et peut rejouer n'importe quel niveau deja debloque.
 */
public final class EemiJump extends JavaPlugin implements Listener {
    static final String P = "&a&l[Jump] &r";
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    /** Un parcours (une map) : points de passage dans l'ordre, 0 = depart, le dernier = arrivee. */
    static final class Course {
        String id, name, worldName, author, difficulty = "difficile", description = "", credit = "";
        Material icon = Material.VINE;
        /** Blocs qui renvoient a la plateforme quand on les touche (pieds ou tete) : lave, feu... */
        final java.util.Set<Material> hazards = java.util.EnumSet.noneOf(Material.class);
        /**
         * Parmi les hazards, ceux qui ne comptent que dans la colonne centrale du joueur (bloc des pieds et bloc juste
         * au-dessus, aux coordonnees entieres de sa position) : on peut frôler une flamme mais pas la traverser.
         * Cle hazards-center du parcours (defaut : fire, soul_fire). Les autres (lave) comptent sur toute la boite.
         */
        final java.util.Set<Material> centerHazards = java.util.EnumSet.of(Material.FIRE, Material.SOUL_FIRE);
        /** Mode « sans chute » (section no-fall du parcours) : finir tous les niveaux d'affilee, une chute = retour au 1. */
        boolean noFall;
        ConfigurationSection noFallBonus;
        /** Niveaux du mode sans chute annonces a tout le serveur avec un bouton [REGARDER] (Esteban, 17/11). */
        java.util.Set<Integer> noFallAnnounce = new java.util.HashSet<>();
        World world;
        double floorY, failY;
        boolean hasFailY;
        int[] bounds;
        /** Grosse recompense de fin (titre / trainee exclusifs de rewards.finish-bonus) : parcours difficile seulement. */
        boolean bigBonus = true;
        /** Recompense de fin propre au parcours (section finish-bonus de son fichier), sinon celle du config. */
        ConfigurationSection bonus;
        /** Recompenses propres au parcours (section rewards de son fichier), sinon celles du config. */
        ConfigurationSection rewards;
        /** Parcours « points » : chaque point = [x, y (pieds), z, hauteur de chute] ; sinon couloir a plateformes. */
        boolean points;
        int radius = 1;
        /** Vrai quand le monde est pret (parcours genere : construction terminee). */
        boolean ready = true;
        final List<double[]> pts = new ArrayList<>();
        final List<int[]> cps = new ArrayList<>(); // x, z, axe (0 = x, 1 = z), debut, fin
        final List<Float> yaws = new ArrayList<>();

        int last() {
            return (points ? pts.size() : cps.size()) - 1;
        }

        Location loc(int n) {
            if (points) {
                double[] p = pts.get(n);
                return new Location(world, p[0] + 0.5, p[1], p[2] + 0.5, yaws.get(n), 0f);
            }
            int[] c = cps.get(n);
            return new Location(world, c[0] + 0.5, floorY, c[1] + 0.5, yaws.get(n), 0f);
        }

        /** Chute : pieds sous la hauteur de chute du point en cours (parcours a points) ou sous fail-y. */
        boolean below(Location l, int cur) {
            if (points) {
                return l.getY() < pts.get(Math.max(0, Math.min(cur, pts.size() - 1)))[3] || hasFailY && l.getY() < failY;
            }
            return l.getY() < failY;
        }

        /** Le joueur est-il sur la plateforme du point n ? */
        boolean on(int n, Location l) {
            if (points) {
                double[] p = pts.get(n);
                return area(n, l) && l.getY() >= p[1] - 0.6 && l.getY() <= p[1] + 1.0;
            }
            // pose sur la plateforme (pas en passant au-dessus)
            if (l.getY() < floorY - 0.6 || l.getY() > floorY + 0.6) {
                return false;
            }
            return area(n, l);
        }

        /** Le joueur est-il au-dessus de l'emprise de la plateforme du point n (hauteur ignoree) ? */
        boolean area(int n, Location l) {
            int bx = l.getBlockX(), bz = l.getBlockZ();
            if (points) {
                double[] p = pts.get(n);
                return Math.abs(bx - (int) p[0]) <= radius && Math.abs(bz - (int) p[2]) <= radius;
            }
            int[] c = cps.get(n);
            if (c[2] == 0) {
                return Math.abs(bx - c[0]) <= 1 && bz >= c[3] && bz <= c[4];
            }
            return Math.abs(bz - c[1]) <= 1 && bx >= c[3] && bx <= c[4];
        }

        /** Pieds ou tete dans un bloc dangereux (lave, feu) : la map d'origine tuait, ici retour a la plateforme. */
        boolean hazard(Location l) {
            if (hazards.isEmpty()) {
                return false;
            }
            // toute la boite du joueur (0,6 de large, 1,8 de haut) : frôler une colonne de lave compte aussi.
            // Les blocs de centerHazards (feu) ne comptent que dans la colonne centrale (pieds et bloc au-dessus).
            World w = l.getWorld();
            int cx = l.getBlockX(), cz = l.getBlockZ(), fy = (int) Math.floor(l.getY());
            int x1 = (int) Math.floor(l.getX() - 0.3), x2 = (int) Math.floor(l.getX() + 0.3 - 1e-4);
            int z1 = (int) Math.floor(l.getZ() - 0.3), z2 = (int) Math.floor(l.getZ() + 0.3 - 1e-4);
            int y1 = fy, y2 = (int) Math.floor(l.getY() + 1.8 - 1e-4);
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    for (int y = y1; y <= y2; y++) {
                        Material m = w.getBlockAt(x, y, z).getType();
                        if (!hazards.contains(m)) {
                            continue;
                        }
                        if (!centerHazards.contains(m) || x == cx && z == cz && (y == fy || y == fy + 1)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        boolean inside(Location l) {
            int[] b = bounds;
            return b == null || l.getX() >= b[0] && l.getX() < b[3] + 1 && l.getZ() >= b[2] && l.getZ() < b[5] + 1 && l.getY() >= b[1] && l.getY() < b[4] + 1;
        }
    }

    /** Progression enregistree d'un joueur sur un parcours. */
    static final class Progress {
        int max, current, falls, finished;
        long playedMs, bestRunMs = -1;
        /** Temps de jeu cumule au moment de la premiere fin du parcours (-1 = inconnu : fini avant la 1.4.0, ou pas fini). */
        long firstFinishMs = -1;
    }

    /** Progression du mode sans chute d'un parcours (courses.<id>.nofall), a part du mode normal. */
    static final class NoFall {
        int best, finished, attempts;
        long bestMs = -1, playedMs;
    }

    /** Joueur actuellement sur un parcours. */
    final class Session {
        final UUID id;
        final Course course;
        int cur;
        int falls;
        long enterAt, levelStart, lastTp, lastItem;
        long runStart = -1;
        /** Mode sans chute ; nfStart = debut du chrono de l'essai (-1 = pas encore parti de la plateforme de depart). */
        boolean noFall;
        long nfStart = -1;
        boolean hide;
        /**
         * Chrono du niveau en cours (classement par niveau, carte #117) : lvStart = depart (-1 = pas encore parti),
         * lvWait = le chrono partira quand le joueur quittera la plateforme (apres un retour, une chute, une reprise),
         * lvValid = faux apres un saut de niveau (/jump niveau, menu, /jump set) jusqu'a la prochaine plateforme validee.
         */
        long lvStart = -1;
        boolean lvWait = true, lvValid = true;
        /** Barre d'action du temps du niveau gardee jusqu'a cette heure (le chrono du sans chute ne l'ecrase pas). */
        long abUntil;
        Scoreboard board;
        final List<String> lines = new ArrayList<>();

        Session(UUID id, Course course) {
            this.id = id;
            this.course = course;
        }
    }

    /** Menu de choix du parcours. */
    static final class Choice implements InventoryHolder {
        final Inventory inv;
        final List<String> ids = new ArrayList<>();

        Choice() {
            this.inv = Bukkit.createInventory(this, 27, c("&2&lJump &8- &fchoisis ton parcours"));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Menu des niveaux. */
    static final class Menu implements InventoryHolder {
        final Inventory inv;
        final Course course;

        Menu(Course course) {
            this.course = course;
            this.inv = Bukkit.createInventory(this, 54, c("&2&lJump &8- &f" + course.name));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Menu du mode sans chute (remplace le menu des niveaux : pas de teleportation vers un niveau). */
    static final class NoFallMenu implements InventoryHolder {
        final Inventory inv;
        final Course course;

        NoFallMenu(Course course) {
            this.course = course;
            this.inv = Bukkit.createInventory(this, 27, c("&4&lSans chute &8- &f" + course.name));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class Entry {
        String name;
        int max, finished;
        long best = -1, played, finishMs = -1;
    }

    /** Cle du classement du mode sans chute d'un parcours. */
    static String nfKey(Course c) {
        return c.id + "#nofall";
    }

    final Map<String, Course> courses = new LinkedHashMap<>();
    final Map<UUID, Session> sessions = new HashMap<>();
    /** Progression en memoire des joueurs connus (charges a la demande), cle uuid puis parcours. */
    final Map<UUID, YamlConfiguration> cache = new HashMap<>();
    /** Classement par parcours. */
    final Map<String, Map<UUID, Entry>> board = new HashMap<>();
    NamespacedKey itemKey;
    LevelTimes levelTimes;

    static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        s.sendMessage(c(P + m));
    }

    static String time(long ms) {
        if (ms < 0) {
            return "-";
        }
        long h = ms / 3600000;
        long m = (ms / 60000) % 60;
        long s = (ms / 1000) % 60;
        // mm:ss, ou h:mm:ss au-dela d'une heure
        return (h > 0 ? h + ":" : "") + (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    /** Temps d'un niveau : mm:ss.cc (centiemes). */
    static String timeCs(long ms) {
        if (ms <= 0) {
            return "-";
        }
        long cs = (ms / 10) % 100;
        return time(ms) + "." + (cs < 10 ? "0" : "") + cs;
    }

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "item");
        levelTimes = new LevelTimes(getDataFolder(), getLogger());
        loadCourses();
        loadBoard();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::sendStatus, 40L, 400L);
        // sauvegarde de securite (chutes, temps de jeu) toutes les 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Session s : sessions.values()) {
                Player p = Bukkit.getPlayer(s.id);
                if (p != null) {
                    save(p, s);
                }
            }
        }, 6000L, 6000L);
        // temps par niveau : fichiers modifies reecrits toutes les 30 s, hors du fil principal
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<Object[]> snap = levelTimes.snapshot();
            if (!snap.isEmpty()) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> levelTimes.write(snap));
            }
        }, 600L, 600L);
    }

    @Override
    public void onDisable() {
        for (Session s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.id);
            if (p != null) {
                leave(p, true);
            }
        }
        sessions.clear();
        if (levelTimes != null) {
            levelTimes.write(levelTimes.snapshot());
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new Empty();
    }

    static final class Empty extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }

    void rules(World w) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.FALL_DAMAGE, false);
        // feu des maps (netherrack, lave) : ne se propage pas aux plateformes en bois
        w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
    }

    boolean worldExists(String name) {
        File root = Bukkit.getWorldContainer();
        if (new File(root, name).isDirectory()) {
            return true;
        }
        String main = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName();
        return new File(root, main + File.separator + "dimensions" + File.separator + "minecraft" + File.separator + name).isDirectory();
    }

    World loadWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null) {
            if (!worldExists(name)) {
                return null;
            }
            w = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
            if (w != null) {
                getLogger().info("Monde " + name + " charge.");
            }
        }
        if (w != null) {
            rules(w);
        }
        return w;
    }

    /** Parcours a charger : ceux du config + ceux livres avec le jar que le config ne connait pas (nouvelles maps), sauf disabled-courses. */
    List<String> courseIds() {
        List<String> ids = new ArrayList<>(getConfig().getStringList("courses"));
        org.bukkit.configuration.Configuration def = getConfig().getDefaults();
        if (def != null) {
            for (String id : def.getStringList("courses")) {
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        ids.removeAll(getConfig().getStringList("disabled-courses"));
        return ids;
    }

    void loadCourses() {
        courses.clear();
        for (String id : courseIds()) {
            File f = new File(getDataFolder(), "courses" + File.separator + id + ".yml");
            if (!f.exists() && getResource("courses/" + id + ".yml") != null) {
                saveResource("courses/" + id + ".yml", false);
            }
            if (!f.exists()) {
                getLogger().warning("Parcours " + id + " : fichier courses/" + id + ".yml introuvable.");
                continue;
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            // fichier du jar complete par la copie du serveur : une cle ajoutee dans une nouvelle version (no-fall, hazards-center...)
            // marche meme si la copie, faite une seule fois, ne l'a pas. Une valeur presente dans la copie gagne toujours
            // (pour couper une liste du jar, ecrire une liste vide, ex. hazards: [] ; pour couper le sans chute : no-fall.enabled: false).
            java.io.InputStream in = getResource("courses/" + id + ".yml");
            if (in != null) {
                try (java.io.Reader r = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                    YamlConfiguration merged = YamlConfiguration.loadConfiguration(r);
                    for (String k : y.getKeys(true)) {
                        if (!y.isConfigurationSection(k)) {
                            merged.set(k, y.get(k));
                        }
                    }
                    y = merged;
                } catch (IOException ex) {
                    getLogger().warning("Parcours " + id + " : lecture du fichier du jar impossible : " + ex.getMessage());
                }
            }
            Course c = new Course();
            c.id = id;
            c.name = y.getString("name", id);
            c.author = y.getString("author", "");
            c.worldName = y.getString("world", id);
            c.floorY = y.getDouble("floor-y", 100);
            c.hasFailY = y.contains("fail-y");
            c.failY = y.getDouble("fail-y", c.floorY - 1);
            c.difficulty = y.getString("difficulty", "difficile");
            c.description = y.getString("description", "");
            c.credit = y.getString("credit", "");
            for (String h : y.getStringList("hazards")) {
                Material hm = Material.matchMaterial(h);
                if (hm == null || !hm.isBlock()) {
                    getLogger().warning("Parcours " + id + " : bloc dangereux inconnu " + h + ", ignore.");
                } else {
                    c.hazards.add(hm);
                }
            }
            if (y.contains("hazards-center")) {
                c.centerHazards.clear();
                for (String h : y.getStringList("hazards-center")) {
                    Material hm = Material.matchMaterial(h);
                    if (hm == null || !hm.isBlock()) {
                        getLogger().warning("Parcours " + id + " : bloc de hazards-center inconnu " + h + ", ignore.");
                    } else {
                        c.centerHazards.add(hm);
                    }
                }
            }
            c.noFall = y.getBoolean("no-fall.enabled", false);
            c.noFallBonus = y.getConfigurationSection("no-fall.bonus");
            // annonce « X a passe le niveau N sans tomber [REGARDER] » ; 70 par defaut, liste vide = aucune
            java.util.List<Integer> ann = y.isList("no-fall.announce-levels") ? y.getIntegerList("no-fall.announce-levels") : java.util.List.of(70);
            c.noFallAnnounce.addAll(ann);
            Material icon = Material.matchMaterial(y.getString("icon", "vine"));
            c.icon = icon == null || !icon.isItem() ? Material.VINE : icon;
            if (y.isConfigurationSection("finish-bonus")) {
                c.bonus = y.getConfigurationSection("finish-bonus");
                c.bigBonus = true;
            } else {
                c.bigBonus = y.getBoolean("finish-bonus", true);
            }
            c.rewards = y.getConfigurationSection("rewards");
            c.radius = Math.max(0, y.getInt("radius", 1));
            List<Integer> b = y.getIntegerList("bounds");
            if (b.size() == 6) {
                c.bounds = new int[] {b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5)};
            }
            String gen = y.getString("generator", "");
            if (!gen.isEmpty()) {
                if (!"tranquille".equals(gen)) {
                    getLogger().warning("Parcours " + id + " : generateur inconnu " + gen + ", ignore.");
                    continue;
                }
                Tranquille t = new Tranquille(y.getLong("seed", 20261117L), Math.max(5, Math.min(120, y.getInt("steps", 50))));
                if (!t.problems.isEmpty()) {
                    getLogger().warning("Parcours " + id + " : " + t.problems.size() + " remarque(s) du generateur : " + t.problems);
                }
                c.points = true;
                for (Tranquille.Point p : t.points) {
                    c.pts.add(new double[] {p.x, p.y, p.z, p.failY});
                    c.yaws.add(p.yaw);
                }
                c.bounds = new int[] {t.minX - 10, Tranquille.SEA - 12, t.minZ - 10, t.maxX + 10, Tranquille.BASE_Y + 40, t.maxZ + 10};
                c.world = generatedWorld(c.worldName);
                if (c.world == null) {
                    getLogger().warning("Parcours " + id + " : impossible de creer le monde " + c.worldName + ".");
                    continue;
                }
                courses.put(id, c);
                buildGenerated(c, t, y.getLong("seed", 20261117L) * 31 + y.getInt("steps", 50));
                getLogger().info("Parcours " + c.name + " : " + c.last() + " etapes, " + t.jumps + " sauts (vide max " + t.maxFlatGap
                        + " a plat, " + t.maxUpGap + " en montee), monde " + c.worldName + ".");
                continue;
            }
            if ("points".equals(y.getString("type", ""))) {
                c.points = true;
                for (Object o : y.getList("points", List.of())) {
                    if (o instanceof List<?> l && l.size() >= 5) {
                        try {
                            c.pts.add(new double[] {((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue(), ((Number) l.get(4)).doubleValue()});
                            c.yaws.add(((Number) l.get(3)).floatValue());
                        } catch (ClassCastException e) {
                            getLogger().warning("Parcours " + id + " : point invalide " + l);
                        }
                    }
                }
            }
            for (Object o : c.points ? List.of() : y.getList("checkpoints", List.of())) {
                if (!(o instanceof List<?> l) || l.size() < 6) {
                    continue;
                }
                try {
                    int[] cp = new int[5];
                    cp[0] = ((Number) l.get(0)).intValue();
                    cp[1] = ((Number) l.get(1)).intValue();
                    cp[2] = "z".equals(String.valueOf(l.get(2))) ? 1 : 0;
                    cp[3] = ((Number) l.get(3)).intValue();
                    cp[4] = ((Number) l.get(4)).intValue();
                    c.cps.add(cp);
                    c.yaws.add(((Number) l.get(5)).floatValue());
                } catch (ClassCastException e) {
                    getLogger().warning("Parcours " + id + " : point invalide " + l);
                }
            }
            if (c.last() < 1) {
                getLogger().warning("Parcours " + id + " : moins de 2 points, ignore.");
                continue;
            }
            c.world = loadWorld(c.worldName);
            if (c.world == null) {
                getLogger().warning("Parcours " + id + " : monde " + c.worldName + " introuvable (deposer le dossier a la racine du serveur).");
                continue;
            }
            neutralize(c.world, y.getStringList("command-blocks"));
            applyPatches(c, y.getInt("patches.version", 0), y.getStringList("patches.blocks"));
            courses.put(id, c);
            getLogger().info("Parcours " + c.name + " pret : " + c.last() + " niveaux (monde " + c.worldName + ").");
        }
    }

    /** Monde vide d'un parcours genere : charge s'il existe, sinon cree (jamais pour une map importee). */
    World generatedWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
            if (w != null) {
                getLogger().info("Monde " + name + " charge (parcours genere).");
            }
        }
        if (w != null) {
            rules(w);
            w.setSpawnLocation(0, Tranquille.BASE_Y, 0);
        }
        return w;
    }

    final Map<String, org.bukkit.scheduler.BukkitTask> buildTasks = new HashMap<>();

    /**
     * Pose le parcours genere s'il n'a jamais ete pose dans cette version (data.yml built.<id>). Pose etalee sur plusieurs
     * ticks (25 ms par tick au plus), dans l'ordre des chunks ; le parcours est ferme ("en construction") pendant ce temps.
     */
    void buildGenerated(Course c, Tranquille t, long seedSig) {
        String sig = Tranquille.VERSION + ":" + seedSig;
        File f = new File(getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(f);
        if (sig.equals(data.getString("built." + c.id))) {
            c.ready = true;
            return;
        }
        c.ready = false;
        List<long[]> list = new ArrayList<>();
        Map<String, Integer> idx = new HashMap<>();
        List<org.bukkit.block.data.BlockData> states = new ArrayList<>();
        int[] bad = {0};
        t.build((x, y, z, s) -> {
            Integer i = idx.get(s);
            if (i == null) {
                org.bukkit.block.data.BlockData bd;
                try {
                    bd = Bukkit.createBlockData("minecraft:" + s);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Etat de bloc invalide ignore : " + s);
                    bd = null;
                }
                i = states.size();
                states.add(bd);
                idx.put(s, i);
            }
            if (states.get(i) == null) {
                bad[0]++;
                return;
            }
            list.add(new long[] {x, y, z, i});
        });
        list.sort((a, b) -> {
            int ca = Long.compare(a[0] >> 4, b[0] >> 4);
            return ca != 0 ? ca : Long.compare(a[2] >> 4, b[2] >> 4);
        });
        World w = c.world;
        long t0 = System.currentTimeMillis();
        // boite deja posee (ancienne version, ou pose interrompue) : videe avant de poser, sinon les anciens plots resteraient
        List<Integer> ob = data.getIntegerList("built-box." + c.id);
        int[] old = ob.size() == 6 ? new int[] {ob.get(0), ob.get(1), ob.get(2), ob.get(3), ob.get(4), ob.get(5)} : null;
        int[] nb = {t.minX - 2, Tranquille.SEA - 8, t.minZ - 2, t.maxX + 2, Tranquille.BASE_Y + 30, t.maxZ + 2};
        int[] box = old == null ? nb : new int[] {Math.min(old[0], nb[0]), Math.min(old[1], nb[1]), Math.min(old[2], nb[2]),
            Math.max(old[3], nb[3]), Math.max(old[4], nb[4]), Math.max(old[5], nb[5])};
        data.set("built-box." + c.id, List.of(box[0], box[1], box[2], box[3], box[4], box[5]));
        data.set("built." + c.id, null);
        try {
            getDataFolder().mkdirs();
            data.save(f);
        } catch (IOException ex) {
            getLogger().warning("data.yml : " + ex.getMessage());
        }
        getLogger().info("Construction du parcours " + c.name + " : " + list.size() + " blocs a poser" + (old != null ? " (ancienne version videe d'abord)" : "") + "...");
        org.bukkit.scheduler.BukkitTask prev = buildTasks.remove(c.id);
        if (prev != null) {
            prev.cancel();
        }
        int[] pos = {0};
        long[] col = {old == null ? Long.MAX_VALUE : 0};
        int cw = old == null ? 0 : old[3] - old[0] + 1, cl = old == null ? 0 : old[5] - old[2] + 1;
        buildTasks.put(c.id, Bukkit.getScheduler().runTaskTimer(this, () -> {
            long end = System.nanoTime() + 25_000_000L;
            // 1) vidage de l'ancienne boite, colonne par colonne
            while (old != null && col[0] < (long) cw * cl && System.nanoTime() < end) {
                for (int k = 0; k < 64 && col[0] < (long) cw * cl; k++, col[0]++) {
                    int x = old[0] + (int) (col[0] % cw), z = old[2] + (int) (col[0] / cw);
                    for (int y = old[1]; y <= old[4]; y++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) {
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
            if (old != null && col[0] < (long) cw * cl) {
                return;
            }
            // 2) pose
            while (pos[0] < list.size() && System.nanoTime() < end) {
                for (int k = 0; k < 500 && pos[0] < list.size(); k++, pos[0]++) {
                    long[] e = list.get(pos[0]);
                    w.getBlockAt((int) e[0], (int) e[1], (int) e[2]).setBlockData(states.get((int) e[3]), false);
                }
            }
            if (pos[0] >= list.size()) {
                org.bukkit.scheduler.BukkitTask me = buildTasks.remove(c.id);
                if (me != null) {
                    me.cancel();
                }
                YamlConfiguration d = YamlConfiguration.loadConfiguration(f);
                d.set("built." + c.id, sig);
                d.set("built-box." + c.id, List.of(nb[0], nb[1], nb[2], nb[3], nb[4], nb[5]));
                try {
                    getDataFolder().mkdirs();
                    d.save(f);
                } catch (IOException ex) {
                    getLogger().warning("data.yml : " + ex.getMessage());
                }
                c.ready = true;
                getLogger().info("Parcours " + c.name + " construit : " + list.size() + " blocs en " + (System.currentTimeMillis() - t0) + " ms"
                        + (bad[0] > 0 ? ", " + bad[0] + " bloc(s) d'etat invalide ignore(s)" : "") + ".");
            }
        }, 1L, 1L));
    }

    /**
     * Retouches d'une map importee (ex. niveau refait), appliquees une seule fois par version (data.yml patched.<id>).
     * Chaque ligne : "x y z etat" ou "x1 y1 z1 x2 y2 z2 etat" (boite remplie), dans l'ordre ; etat = minecraft:... sans prefixe.
     */
    void applyPatches(Course c, int version, List<String> lines) {
        if (version <= 0 || lines.isEmpty()) {
            return;
        }
        File f = new File(getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(f);
        if (data.getInt("patched." + c.id, 0) == version) {
            return;
        }
        int set = 0, bad = 0;
        for (String line : lines) {
            String[] p = line.trim().split("\\s+");
            try {
                int[] v;
                String st;
                if (p.length == 4) {
                    v = new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
                    st = p[3];
                } else if (p.length == 7) {
                    v = new int[6];
                    for (int i = 0; i < 6; i++) {
                        v[i] = Integer.parseInt(p[i]);
                    }
                    st = p[6];
                } else {
                    bad++;
                    continue;
                }
                org.bukkit.block.data.BlockData bd = Bukkit.createBlockData("minecraft:" + st);
                for (int x = Math.min(v[0], v[3]); x <= Math.max(v[0], v[3]); x++) {
                    for (int yy = Math.min(v[1], v[4]); yy <= Math.max(v[1], v[4]); yy++) {
                        for (int z = Math.min(v[2], v[5]); z <= Math.max(v[2], v[5]); z++) {
                            c.world.getBlockAt(x, yy, z).setBlockData(bd, false);
                            set++;
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                bad++;
                getLogger().warning("Parcours " + c.id + " : retouche invalide ignoree : " + line);
            }
        }
        getLogger().info("Parcours " + c.name + " : retouches version " + version + " appliquees (" + set + " blocs" + (bad > 0 ? ", " + bad + " ligne(s) invalide(s)" : "") + ").");
        data.set("patched." + c.id, version);
        try {
            getDataFolder().mkdirs();
            data.save(f);
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    /** Retire les blocs de commande de la map d'origine (une seule fois par monde). */
    void neutralize(World w, List<String> positions) {
        File f = new File(getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(f);
        if (data.getBoolean("neutralized." + w.getName(), false) || positions.isEmpty()) {
            return;
        }
        int removed = 0;
        for (String pos : positions) {
            String[] p = pos.trim().split("\\s+");
            if (p.length < 3) {
                continue;
            }
            try {
                Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                Material t = b.getType();
                if (t == Material.COMMAND_BLOCK || t == Material.CHAIN_COMMAND_BLOCK || t == Material.REPEATING_COMMAND_BLOCK) {
                    b.setType(Material.AIR, false);
                    removed++;
                }
            } catch (NumberFormatException e) {
                getLogger().warning("Position invalide : " + pos);
            }
        }
        getLogger().info("Monde " + w.getName() + " : " + removed + " bloc(s) de commande retire(s) sur " + positions.size() + " connus.");
        data.set("neutralized." + w.getName(), true);
        try {
            getDataFolder().mkdirs();
            data.save(f);
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    Course defaultCourse() {
        return courses.isEmpty() ? null : courses.values().iterator().next();
    }

    boolean ours(World w) {
        for (Course c : courses.values()) {
            if (c.world != null && c.world.equals(w)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ sauvegarde

    File file(UUID id) {
        return new File(getDataFolder(), "players" + File.separator + id + ".yml");
    }

    /** Fichiers joueurs illisibles : jamais reecrits (copie .bak gardee) pour ne pas effacer la progression. */
    final java.util.Set<UUID> broken = new java.util.HashSet<>();

    YamlConfiguration data(UUID id) {
        return cache.computeIfAbsent(id, k -> {
            YamlConfiguration y = new YamlConfiguration();
            File f = file(k);
            if (f.exists()) {
                try {
                    y.load(f);
                } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                    broken.add(k);
                    try {
                        java.nio.file.Files.copy(f.toPath(), new File(f.getPath() + ".bak").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                        // la copie est un bonus
                    }
                    getLogger().severe("Fichier de progression illisible " + f.getName() + " (copie .bak) : il ne sera pas reecrit. " + e.getMessage());
                }
            }
            return y;
        });
    }

    Progress progress(UUID id, Course c) {
        YamlConfiguration y = data(id);
        String b = "courses." + c.id + ".";
        Progress p = new Progress();
        p.max = Math.max(0, Math.min(c.last(), y.getInt(b + "max", 0)));
        p.current = Math.max(0, Math.min(p.max, y.getInt(b + "current", 0)));
        p.falls = y.getInt(b + "falls", 0);
        p.finished = y.getInt(b + "finished", 0);
        p.playedMs = y.getLong(b + "played-ms", 0);
        p.bestRunMs = y.getLong(b + "best-run-ms", -1);
        p.firstFinishMs = y.getLong(b + "first-finish-ms", -1);
        return p;
    }

    NoFall noFall(UUID id, Course c) {
        YamlConfiguration y = data(id);
        String b = "courses." + c.id + ".nofall.";
        NoFall n = new NoFall();
        n.best = Math.max(0, Math.min(c.last(), y.getInt(b + "best", 0)));
        n.finished = y.getInt(b + "finished", 0);
        n.attempts = y.getInt(b + "attempts", 0);
        n.bestMs = y.getLong(b + "best-ms", -1);
        n.playedMs = y.getLong(b + "played-ms", 0);
        return n;
    }

    void storeNoFall(UUID id, String name, Course c, NoFall n) {
        YamlConfiguration y = data(id);
        String b = "courses." + c.id + ".nofall.";
        y.set("name", name);
        y.set(b + "best", n.best);
        y.set(b + "finished", n.finished);
        y.set(b + "attempts", n.attempts);
        y.set(b + "best-ms", n.bestMs);
        y.set(b + "played-ms", n.playedMs);
        writeFile(id, name);
        Entry e = board.computeIfAbsent(nfKey(c), k -> new HashMap<>()).computeIfAbsent(id, k -> new Entry());
        e.name = name;
        e.max = n.best;
        e.best = n.bestMs;
        e.finished = n.finished;
        e.played = n.playedMs;
    }

    void store(UUID id, String name, Course c, Progress p) {
        YamlConfiguration y = data(id);
        String b = "courses." + c.id + ".";
        y.set("name", name);
        y.set(b + "max", p.max);
        y.set(b + "current", p.current);
        y.set(b + "falls", p.falls);
        y.set(b + "finished", p.finished);
        y.set(b + "played-ms", p.playedMs);
        y.set(b + "best-run-ms", p.bestRunMs);
        y.set(b + "first-finish-ms", p.firstFinishMs < 0 ? null : p.firstFinishMs);
        writeFile(id, name);
        Entry e = board.computeIfAbsent(c.id, k -> new HashMap<>()).computeIfAbsent(id, k -> new Entry());
        e.name = name;
        e.max = p.max;
        e.best = p.bestRunMs;
        e.finished = p.finished;
        e.played = p.playedMs;
        e.finishMs = p.firstFinishMs;
    }

    /** Ecrit le fichier du joueur (deja a jour en memoire). */
    void writeFile(UUID id, String name) {
        YamlConfiguration y = data(id);
        if (!broken.contains(id)) {
            // ecriture dans un fichier temporaire puis remplacement : un arret brutal ne laisse jamais un fichier a moitie ecrit
            File f = file(id);
            File tmp = new File(f.getPath() + ".tmp");
            try {
                f.getParentFile().mkdirs();
                y.save(tmp);
                try {
                    java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                getLogger().warning("Sauvegarde de " + name + " : " + e.getMessage());
            }
        }
    }

    void loadBoard() {
        board.clear();
        File dir = new File(getDataFolder(), "players");
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            UUID id;
            try {
                id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
            } catch (IllegalArgumentException e) {
                continue;
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection cs = y.getConfigurationSection("courses");
            if (cs == null) {
                continue;
            }
            for (String cid : cs.getKeys(false)) {
                Entry e = new Entry();
                e.name = y.getString("name", "?");
                e.max = cs.getInt(cid + ".max", 0);
                e.best = cs.getLong(cid + ".best-run-ms", -1);
                e.finished = cs.getInt(cid + ".finished", 0);
                e.played = cs.getLong(cid + ".played-ms", 0);
                e.finishMs = cs.getLong(cid + ".first-finish-ms", -1);
                board.computeIfAbsent(cid, k -> new HashMap<>()).put(id, e);
                if (cs.isConfigurationSection(cid + ".nofall")) {
                    Entry n = new Entry();
                    n.name = e.name;
                    n.max = cs.getInt(cid + ".nofall.best", 0);
                    n.best = cs.getLong(cid + ".nofall.best-ms", -1);
                    n.finished = cs.getInt(cid + ".nofall.finished", 0);
                    n.played = cs.getLong(cid + ".nofall.played-ms", 0);
                    board.computeIfAbsent(cid + "#nofall", k -> new HashMap<>()).put(id, n);
                }
            }
        }
    }

    static long orMax(long v) {
        return v < 0 ? Long.MAX_VALUE : v;
    }

    /** Fini = au moins une fin enregistree, ou le dernier niveau atteint (progression mise a la main). */
    static boolean done(Entry e, Course c) {
        return e.finished > 0 || e.max >= c.last();
    }

    /** Temps « jusqu'a la fin » d'un joueur qui a fini : temps de jeu a la 1re fin, sinon (fini avant la 1.4.0) temps de jeu total. */
    static long finishTime(Entry e) {
        // 1.6.3 (#149) : plus jamais le temps de jeu total (il grossissait a chaque partie) : -1 si pas de 1re fin datee
        return e.finishMs;
    }

    List<Entry> top(Course c) {
        List<Entry> l = new ArrayList<>(board.getOrDefault(c.id, Map.of()).values());
        // joueurs qui n'ont que du sans chute (ou un marqueur) sur ce parcours : pas dans le classement normal
        l.removeIf(e -> e.max <= 0 && e.finished <= 0);
        l.sort((a, b) -> {
            if (a.max != b.max) {
                return Integer.compare(b.max, a.max);
            }
            if (done(a, c) != done(b, c)) {
                return done(a, c) ? -1 : 1;
            }
            if (!done(a, c)) {
                return 0;
            }
            // a egalite (ceux qui ont fini) : meilleur chrono complet, puis temps de jeu jusqu'a la fin
            int r = Long.compare(orMax(a.best), orMax(b.best));
            return r != 0 ? r : Long.compare(orMax(finishTime(a)), orMax(finishTime(b)));
        });
        return l;
    }

    /** Classement sans chute : ceux qui ont reussi par meilleur temps, puis les autres par meilleur niveau atteint. */
    List<Entry> topNoFall(Course c) {
        List<Entry> l = new ArrayList<>(board.getOrDefault(nfKey(c), Map.of()).values());
        l.removeIf(e -> e.max <= 0 && e.finished <= 0);
        l.sort((a, b) -> {
            boolean fa = a.finished > 0, fb = b.finished > 0;
            if (fa != fb) {
                return fa ? -1 : 1;
            }
            if (fa) {
                return Long.compare(orMax(a.best), orMax(b.best));
            }
            return Integer.compare(b.max, a.max);
        });
        return l;
    }

    /** Enregistre la session en cours (niveau, chutes, temps joue). */
    void save(Player p, Session s) {
        long now = System.currentTimeMillis();
        if (s.noFall) {
            // mode sans chute : la progression normale (niveau atteint, niveau en cours, chutes) n'est jamais touchee
            NoFall n = noFall(s.id, s.course);
            n.playedMs += now - s.enterAt;
            s.enterAt = now;
            n.best = Math.max(n.best, s.cur);
            storeNoFall(s.id, p.getName(), s.course, n);
            return;
        }
        Progress pr = progress(s.id, s.course);
        pr.playedMs += now - s.enterAt;
        s.enterAt = now;
        pr.falls += s.falls;
        s.falls = 0;
        pr.current = s.cur;
        store(s.id, p.getName(), s.course, pr);
    }

    // ================================================================ EemiCore

    void eemi(String cmd) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /**
     * Recompenses exclusives de fin de parcours (rewards.finish-bonus) : pieces, xp et cosmetiques EemiCore
     * reserves (casino: true dans le config d'EemiCore, donc jamais vendus). Donnees une seule fois par version
     * (courses.<id>.finish-bonus) : changer rewards.finish-bonus.version les redonne a tous ceux qui ont fini.
     * Appelee a la fin du parcours et a la connexion (rattrapage de ceux qui avaient deja fini).
     */
    void grantFinishBonus(Player p, UUID id, Course c) {
        if (!c.bigBonus) {
            return;
        }
        ConfigurationSection b = c.bonus != null ? c.bonus : getConfig().getConfigurationSection("rewards.finish-bonus");
        grantBonus(p, id, c, b, "courses." + c.id + ".finish-bonus", progress(id, c).finished, c.name);
    }

    /** Recompense du mode sans chute (section no-fall.bonus du parcours), une fois par version, marqueur a part. */
    void grantNoFallBonus(Player p, UUID id, Course c) {
        if (!c.noFall) {
            return;
        }
        grantBonus(p, id, c, c.noFallBonus, "courses." + c.id + ".nofall.bonus", noFall(id, c).finished, c.name + " - Sans chute");
    }

    void grantBonus(Player p, UUID id, Course c, ConfigurationSection b, String key, int finished, String label) {
        if (!p.isOnline() || broken.contains(id) || Bukkit.getPluginManager().getPlugin("EemiCore") == null) {
            return;
        }
        if (b == null || !b.getBoolean("enabled", true)) {
            return;
        }
        int version = b.getInt("version", 1);
        YamlConfiguration yd = data(id);
        if (finished <= 0 || yd.getInt(key, 0) >= version) {
            return;
        }
        yd.set(key, version);
        writeFile(id, p.getName());
        int coins = b.getInt("coins", 0);
        int xp = b.getInt("xp", 0);
        if (coins > 0) {
            eemi("eemi give " + p.getName() + " coins " + coins);
        }
        if (xp > 0) {
            eemi("eemi give " + p.getName() + " xp " + xp);
        }
        List<String> cos = b.getStringList("cosmetics");
        for (String cid : cos) {
            eemi("eemi cosmetic " + p.getName() + " " + cid);
        }
        msg(p, "&6&l✦ Récompenses du parcours &f" + label + " &6&l✦");
        if (coins > 0 || xp > 0) {
            msg(p, "&e+" + coins + " pièces &7et &b+" + xp + " xp");
        }
        for (String line : b.getStringList("lines")) {
            msg(p, line);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        getLogger().info("Recompenses (" + key + ", v" + version + ") donnees a " + p.getName() + " pour " + c.id + " : "
                + coins + " pieces, " + xp + " xp, " + cos + ".");
    }

    /**
     * #159 (Esteban, 27/11) : chaque nouvelle fin d'un parcours deja paye rapporte la moitie de la fois d'avant
     * (base / 2, puis / 4, / 8...), pieces et xp. Compteur a part (key) : ceux qui avaient deja fini plusieurs
     * fois avant la 1.6.4 repartent de la moitie. Plus rien quand les deux montants tombent a 0.
     */
    void payReplay(Player p, UUID id, String key, int baseCoins, int baseXp, String label) {
        if (!p.isOnline() || broken.contains(id) || Bukkit.getPluginManager().getPlugin("EemiCore") == null) {
            return;
        }
        YamlConfiguration yd = data(id);
        int k = yd.getInt(key, 0) + 1;
        int coins = k >= 31 ? 0 : Math.max(0, baseCoins) >> k;
        int xp = k >= 31 ? 0 : Math.max(0, baseXp) >> k;
        if (coins <= 0 && xp <= 0) {
            msg(p, "&7Plus de récompense pour une nouvelle fin de &f" + label + "&7.");
            return;
        }
        yd.set(key, k);
        writeFile(id, p.getName());
        if (coins > 0) {
            eemi("eemi give " + p.getName() + " coins " + coins);
        }
        if (xp > 0) {
            eemi("eemi give " + p.getName() + " xp " + xp);
        }
        msg(p, "&6+" + coins + " pièces &7et &b+" + xp + " xp &7pour cette nouvelle fin de &f" + label
                + " &8(la moitié de la fois d'avant)");
        getLogger().info("Nouvelle fin (" + key + " = " + k + ") de " + p.getName() + " : " + coins + " pieces, " + xp + " xp.");
    }

    /** Recompense : section rewards du fichier du parcours, sinon rewards du config. */
    int rw(Course c, String key, int def) {
        if (c.rewards != null && c.rewards.contains(key)) {
            return c.rewards.getInt(key, def);
        }
        return getConfig().getInt("rewards." + key, def);
    }

    void sendStatus() {
        eemi("eemi status " + getConfig().getString("eemicore-id", "jump") + " playing 0 " + sessions.size() + " " + getConfig().getInt("max-players", 50));
    }

    // ================================================================ entree / sortie

    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!ours(p.getWorld()) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemijump.admin")) {
            msg(p, "&cReviens au lobby (&f/lobby&c) pour jouer au jump.");
            return false;
        }
        return true;
    }

    /** Entre sur un parcours, au niveau donne (-1 = reprendre au dernier niveau joue). */
    void enter(Player p, Course c, int level) {
        enter(p, c, level, false);
    }

    /** Entre sur un parcours ; noFall = mode sans chute (toujours au niveau 1, progression a part). */
    void enter(Player p, Course c, int level, boolean noFall) {
        if (c == null || c.world == null) {
            msg(p, "&cLe jump est indisponible pour l'instant.");
            return;
        }
        if (noFall && !c.noFall) {
            msg(p, "&c" + c.name + " &cn'a pas de mode sans chute.");
            return;
        }
        if (!c.ready) {
            msg(p, "&e" + c.name + " &7est en construction, réessaie dans quelques secondes.");
            return;
        }
        Session old = sessions.get(p.getUniqueId());
        if (old != null && old.course == c && old.noFall == noFall) {
            if (noFall) {
                restartNoFall(p, old, true);
            } else {
                goTo(p, old, level < 0 ? old.cur : level, false);
            }
            return;
        }
        if (old == null && !canEnter(p)) {
            return;
        }
        // deja dans le monde du jump (reconnexion, /mv tp) : jamais refuse pour cause de place
        if (!ours(p.getWorld()) && sessions.size() >= getConfig().getInt("max-players", 50)) {
            msg(p, "&cLe jump est plein pour le moment.");
            return;
        }
        Progress pr = progress(p.getUniqueId(), c);
        Session s = new Session(p.getUniqueId(), c);
        s.noFall = noFall;
        s.cur = noFall ? 0 : level < 0 ? pr.current : Math.min(level, pr.max);
        // reprise au niveau en cours (ou depart) : chronometre ; niveau choisi ailleurs ou mis par /jump set : non
        s.lvValid = noFall || s.cur == 0 || s.cur == pr.current && !ltSkip(p.getUniqueId(), c, s.cur);
        s.enterAt = System.currentTimeMillis();
        s.levelStart = s.enterAt;
        s.lastTp = s.enterAt;
        s.hide = getConfig().getBoolean("hide-players", true);
        if (!p.teleport(c.loc(s.cur)) || !p.getWorld().equals(c.world)) {
            msg(p, "&cImpossible de t'emmener sur le jump (descends de ta monture ?).");
            return;
        }
        // changement de parcours : l'ancienne session n'est quittee qu'une fois la teleportation reussie
        if (old != null && sessions.get(p.getUniqueId()) == old) {
            leave(p, false);
        }
        sessions.put(p.getUniqueId(), s);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setFallDistance(0);
        UUID id = p.getUniqueId();
        // Multiverse-Inventories enregistre l'inventaire au changement de monde : on equipe apres la teleportation
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player q = Bukkit.getPlayer(id);
            if (q != null && sessions.get(id) == s && q.getWorld().equals(c.world)) {
                giveKit(q, s);
                q.setGameMode(GameMode.ADVENTURE);
            }
        }, 3L);
        s.board = Bukkit.getScoreboardManager().getNewScoreboard();
        p.setScoreboard(s.board);
        applyVisibility(p, s);
        msg(p, "&f" + c.name + (noFall ? " &4&l- SANS CHUTE" : "") + " &7(" + c.last() + " niveaux" + (c.author.isEmpty() ? "" : ", map de &f" + c.author + "&7") + ")");
        if (!c.credit.isEmpty()) {
            msg(p, "&8" + c.credit);
        }
        if (noFall) {
            NoFall nf = noFall(p.getUniqueId(), c);
            nf.attempts++;
            storeNoFall(p.getUniqueId(), p.getName(), c, nf);
            msg(p, "&4&lMode sans chute : &fles " + c.last() + " niveaux d'affilée, sans point de contrôle.");
            msg(p, "&7Une chute, une flamme, la lave ou une sortie du couloir te renvoie au &cniveau 1&7.");
            msg(p, "&7Le chrono démarre quand tu quittes la plateforme de départ. Meilleur niveau : &e" + nf.best + "&7/" + c.last()
                    + (nf.bestMs >= 0 ? "&7, record : &b" + time(nf.bestMs) : "") + "&7.");
            msg(p, "&8Ta progression normale sur ce parcours n'est pas touchée.");
            p.showTitle(Title.title(c("&4&lSANS CHUTE"), c("&7Une chute = retour au niveau 1"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
            updateBoard(p, s);
            sendStatus();
            return;
        }
        msg(p, s.cur == 0 ? "&7C'est parti pour le niveau &a1&7 ! Une chute te ramene a la derniere plateforme."
                : "&7Tu reprends au niveau &a" + (s.cur + 1) + "&7. Ton record : &a" + pr.max + "&7/" + c.last() + ".");
        updateBoard(p, s);
        sendStatus();
    }

    /** Sortie du parcours : sauvegarde, inventaire et visibilite remis, retour au hub si demande. */
    void leave(Player p, boolean teleport) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) {
            return;
        }
        releaseWatchers(p.getUniqueId());
        save(p, s);
        p.setFireTicks(0);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Session o : sessions.values()) {
            Player q = Bukkit.getPlayer(o.id);
            if (q != null) {
                q.showPlayer(this, p);
                p.showPlayer(this, q);
            }
        }
        if (ours(p.getWorld())) {
            for (PotionEffect e : p.getActivePotionEffects()) {
                p.removePotionEffect(e.getType());
            }
            p.getInventory().clear();
            if (teleport) {
                toHub(p);
            }
        } else {
            // deja arrive ailleurs (/lobby, autre jeu) : on ne touche ni a son mode ni a ses effets
            stripKit(p);
        }
        sendStatus();
    }

    void toHub(Player p) {
        ConfigurationSection h = getConfig().getConfigurationSection("hub");
        Location loc = null;
        if (h != null) {
            World w = Bukkit.getWorld(h.getString("world", "lobby"));
            if (w != null) {
                loc = new Location(w, h.getDouble("x", 722.5), h.getDouble("y", 51), h.getDouble("z", 650.5), (float) h.getDouble("yaw", 90), (float) h.getDouble("pitch", 0));
            }
        }
        if (loc == null) {
            loc = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        p.teleport(loc);
    }

    // ================================================================ objets et menu

    ItemStack item(Material m, String name, String tag, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(c(s));
            }
            meta.lore(l);
        }
        if (tag != null) {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tag);
        }
        it.setItemMeta(meta);
        return it;
    }

    void giveKit(Player p, Session s) {
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        if (s.noFall) {
            p.getInventory().setItem(0, item(Material.RED_DYE, "&cRecommencer au niveau 1 &7(clic droit)", "back"));
            p.getInventory().setItem(3, item(Material.BOOK, "&4Sans chute &7(clic droit)", "menu"));
        } else {
            p.getInventory().setItem(0, item(Material.RED_DYE, "&cRevenir à la plateforme &7(clic droit)", "back"));
            p.getInventory().setItem(3, item(Material.BOOK, "&aNiveaux &7(clic droit)", "menu"));
        }
        p.getInventory().setItem(5, hideItem(s));
        p.getInventory().setItem(8, item(Material.RED_BED, "&cQuitter le jump &7(clic droit)", "leave"));
        p.getInventory().setHeldItemSlot(4);
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }

    ItemStack hideItem(Session s) {
        return s.hide ? item(Material.GRAY_DYE, "&7Joueurs : &fcaches &7(clic droit)", "hide")
                : item(Material.LIME_DYE, "&7Joueurs : &avisibles &7(clic droit)", "hide");
    }

    void stripKit(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) {
                inv.setItem(i, null);
            }
        }
    }

    /** Ce joueur cache-t-il les autres joueurs du jump, et les autres le cachent-ils ? */
    void applyVisibility(Player p, Session s) {
        for (Session o : sessions.values()) {
            if (o == s) {
                continue;
            }
            Player q = Bukkit.getPlayer(o.id);
            if (q == null) {
                continue;
            }
            if (s.hide) {
                p.hidePlayer(this, q);
            } else {
                p.showPlayer(this, q);
            }
            if (o.hide) {
                q.hidePlayer(this, p);
            } else {
                q.showPlayer(this, p);
            }
        }
    }

    void openMenu(Player p, Session s) {
        Course c = s.course;
        Progress pr = progress(s.id, c);
        Menu m = new Menu(c);
        LevelTimes.Board lb = ltEnabled() ? ltBoard(c, false) : null;
        int groups = (c.last() + 9) / 10;
        for (int g = 0; g < groups && g < 45; g++) {
            int first = g * 10 + 1;
            int lastL = Math.min(c.last(), first + 9);
            boolean open = pr.max >= first - 1;
            boolean here = s.cur + 1 >= first && s.cur + 1 <= lastL;
            boolean done = pr.max >= lastL;
            Material mat = here ? Material.EMERALD : done ? Material.LIME_CONCRETE : open ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
            String name = (open ? (done ? "&a" : "&e") : "&8") + "Niveaux " + first + " a " + lastL;
            List<String> lore = new ArrayList<>();
            if (open) {
                lore.add(here ? "&7Tu es ici (niveau " + (s.cur + 1) + ")" : done ? "&7Termines" : "&7En cours");
                lore.add("&fClic : aller au niveau " + first);
            } else {
                lore.add("&cBloques");
                lore.add("&7Finis le niveau " + (first - 1) + " pour les debloquer");
            }
            if (lb != null) {
                // temps par niveau : record, detenteur et meilleur temps du joueur (carte #117)
                lore.add("&8 ");
                lore.add("&6Records des niveaux &8(record | toi)");
                for (int lv = first; lv <= lastL; lv++) {
                    int rec = lb.record[lv];
                    int mine = lb.mine(p.getUniqueId(), lv);
                    boolean mineIsRec = rec > 0 && p.getUniqueId().equals(lb.holder[lv]);
                    lore.add("&7" + lv + ". " + (rec > 0 ? "&b" + timeCs(rec) + " &f" + lb.name(lb.holder[lv]) : "&8aucun temps")
                            + (mineIsRec ? " &6(toi)" : mine > 0 ? " &8| &a" + timeCs(mine) : ""));
                }
            }
            ItemStack it = item(mat, name, null, lore.toArray(new String[0]));
            it.setAmount(Math.max(1, Math.min(64, g + 1)));
            m.inv.setItem(g, it);
        }
        int next = Math.min(c.last(), pr.max + 1);
        m.inv.setItem(46, item(Material.CLOCK, "&bRecommencer depuis le debut", null, "&7Retour au niveau 1 avec un chrono :", "&7finis les " + c.last() + " niveaux d'une traite", "&7pour un record de temps.", "&7Ton record : &f" + time(pr.bestRunMs)));
        m.inv.setItem(48, item(Material.SLIME_BALL, "&aContinuer au niveau " + next, null, "&7Ton record : &f" + pr.max + "&7/" + c.last()));
        m.inv.setItem(50, item(Material.GOLD_INGOT, "&6Classement", null, "&7Les meilleurs joueurs du parcours"));
        m.inv.setItem(52, item(Material.PAPER, "&fMes stats", null,
                "&7Record : &a" + pr.max + "&7/" + c.last(),
                "&7Chutes : &c" + (pr.falls + s.falls),
                "&7Temps de jeu : &f" + time(pr.playedMs + System.currentTimeMillis() - s.enterAt),
                "&7Parcours fini : &f" + pr.finished + " fois",
                "&7Meilleur chrono complet : &f" + time(pr.bestRunMs)));
        m.inv.setItem(53, item(Material.RED_BED, "&cQuitter le jump", null));
        if (courses.size() > 1) {
            m.inv.setItem(45, item(Material.COMPASS, "&bChanger de parcours", null, "&7Ta progression est gardee sur chaque parcours."));
        }
        if (c.noFall) {
            m.inv.setItem(47, noFallIcon(p, c));
        }
        p.openInventory(m.inv);
    }

    /** Icone du mode sans chute d'un parcours (menu de choix et menu des niveaux). */
    ItemStack noFallIcon(Player p, Course c) {
        NoFall nf = noFall(p.getUniqueId(), c);
        List<String> lore = new ArrayList<>();
        lore.add("&4&lSANS CHUTE &8- &7" + c.last() + " niveaux d'affilée");
        lore.add("&7Aucun point de contrôle : une chute,");
        lore.add("&7une flamme ou la lave = retour au niveau 1.");
        lore.add("&7Ta progression normale n'est pas touchée.");
        lore.add("&8 ");
        lore.add("&fMeilleur niveau : &e" + nf.best + "&7/" + c.last());
        lore.add("&fRecord de temps : &b" + time(nf.bestMs));
        lore.add("&fEssais : &7" + nf.attempts + (nf.finished > 0 ? " &6(réussi " + nf.finished + " fois)" : ""));
        if (c.noFallBonus != null && c.noFallBonus.getBoolean("enabled", true)) {
            ConfigurationSection b = c.noFallBonus;
            lore.add("&8 ");
            lore.add("&6Récompense : &e" + b.getInt("coins", 0) + " pièces&7, &b" + b.getInt("xp", 0) + " xp");
            for (String l : b.getStringList("menu-lines")) {
                lore.add(l);
            }
        }
        lore.add("&8 ");
        lore.add("&eClic : tenter le sans chute");
        return item(Material.NETHERITE_BOOTS, "&4&l" + c.name + " &8- &4Sans chute", null, lore.toArray(new String[0]));
    }

    void openNoFallMenu(Player p, Session s) {
        Course c = s.course;
        NoFall nf = noFall(s.id, c);
        NoFallMenu m = new NoFallMenu(c);
        m.inv.setItem(4, item(Material.PAPER, "&fMes stats sans chute", null,
                "&7Niveau de l'essai en cours : &a" + s.cur + "&7/" + c.last(),
                "&7Chrono de l'essai : &b" + (s.nfStart >= 0 ? time(System.currentTimeMillis() - s.nfStart) : "-"),
                "&7Meilleur niveau : &e" + Math.max(nf.best, s.cur) + "&7/" + c.last(),
                "&7Record de temps : &b" + time(nf.bestMs),
                "&7Réussi : &f" + nf.finished + " fois &7- essais : &f" + nf.attempts));
        m.inv.setItem(11, item(Material.CLOCK, "&cRecommencer au niveau 1", null, "&7Abandonne l'essai en cours."));
        m.inv.setItem(13, item(Material.GOLD_INGOT, "&6Classement sans chute", null, "&7Les meilleurs temps sans tomber"));
        m.inv.setItem(15, item(Material.SLIME_BALL, "&aRevenir au mode normal", null, "&7Reprendre ta progression normale", "&7(niveau par niveau)."));
        if (courses.size() > 1) {
            m.inv.setItem(18, item(Material.COMPASS, "&bChanger de parcours", null));
        }
        m.inv.setItem(26, item(Material.RED_BED, "&cQuitter le jump", null));
        p.openInventory(m.inv);
    }

    /** Parcours dans l'ordre d'affichage : faciles d'abord. */
    List<Course> sortedCourses() {
        List<Course> l = new ArrayList<>(courses.values());
        l.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        return l;
    }

    static int rank(Course c) {
        return switch (c.difficulty.toLowerCase()) {
            case "facile", "tranquille" -> 0;
            case "moyen" -> 1;
            default -> 2;
        };
    }

    static String diffLabel(Course c) {
        return switch (rank(c)) {
            case 0 -> "&a&lFACILE";
            case 1 -> "&e&lMOYEN";
            default -> "&c&lDIFFICILE";
        };
    }

    void openChoice(Player p) {
        Choice ch = new Choice();
        List<Course> l = sortedCourses();
        int[] slots = l.size() <= 1 ? new int[] {13} : l.size() == 2 ? new int[] {11, 15} : l.size() == 3 ? new int[] {11, 13, 15}
                : l.size() == 4 ? new int[] {10, 12, 14, 16} : new int[] {10, 11, 12, 13, 14, 15, 16};
        Session s = sessions.get(p.getUniqueId());
        for (int i = 0; i < l.size() && i < slots.length; i++) {
            Course c = l.get(i);
            Progress pr = progress(p.getUniqueId(), c);
            List<String> lore = new ArrayList<>();
            lore.add(diffLabel(c) + " &8- &7" + c.last() + (c.points ? " etapes" : " niveaux"));
            if (!c.description.isEmpty()) {
                for (String d : c.description.split("\\|")) {
                    lore.add("&7" + d.trim());
                }
            }
            if (!c.credit.isEmpty()) {
                lore.add("&8" + c.credit);
            } else if (!c.author.isEmpty()) {
                lore.add("&8Map : " + c.author);
            }
            lore.add("&8 ");
            lore.add("&fTon record : &a" + pr.max + "&7/" + c.last() + (pr.finished > 0 ? " &6(fini " + pr.finished + " fois)" : ""));
            lore.add(!c.ready ? "&eEn construction..." : s != null && s.course == c && !s.noFall ? "&aTu es sur ce parcours" : pr.current > 0 ? "&eClic : reprendre au niveau " + (pr.current + 1) : "&eClic : commencer");
            if (c.noFall) {
                lore.add("&4Clic droit : mode sans chute");
            }
            ItemStack it = item(c.icon, (rank(c) == 0 ? "&a&l" : rank(c) == 1 ? "&e&l" : "&c&l") + c.name, null, lore.toArray(new String[0]));
            ch.inv.setItem(slots[i], it);
            ch.ids.add(slots[i] + ":" + c.id + ":normal");
        }
        // modes sans chute : rangee du bas, sous le milieu
        int[] nfSlots = {22, 21, 23, 20, 24};
        int k = 0;
        for (Course c : l) {
            if (c.noFall && k < nfSlots.length) {
                ch.inv.setItem(nfSlots[k], noFallIcon(p, c));
                ch.ids.add(nfSlots[k] + ":" + c.id + ":nofall");
                k++;
            }
        }
        p.openInventory(ch.inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Choice ch) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
                return;
            }
            for (String k : ch.ids) {
                String[] parts = k.split(":");
                if (Integer.parseInt(parts[0]) == e.getSlot()) {
                    Course c = courses.get(parts[1]);
                    // clic droit sur l'icone d'un parcours qui a un mode sans chute = ce mode
                    boolean nf = "nofall".equals(parts[2]) || c != null && c.noFall && e.getClick().isRightClick();
                    p.closeInventory();
                    if (c != null) {
                        Session s = sessions.get(p.getUniqueId());
                        if (s != null && s.course == c && s.noFall == nf && !nf) {
                            msg(p, "&7Tu es déjà sur &f" + c.name + "&7.");
                        } else {
                            enter(p, c, -1, nf);
                        }
                    }
                    return;
                }
            }
            return;
        }
        if (e.getInventory().getHolder() instanceof NoFallMenu m) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
                return;
            }
            Session s = sessions.get(p.getUniqueId());
            if (s == null || s.course != m.course || !s.noFall) {
                p.closeInventory();
                return;
            }
            switch (e.getSlot()) {
                case 11 -> {
                    p.closeInventory();
                    restartNoFall(p, s, true);
                }
                case 13 -> {
                    p.closeInventory();
                    showTopNoFall(p, s.course);
                }
                case 15 -> {
                    p.closeInventory();
                    enter(p, s.course, -1, false);
                }
                case 18 -> {
                    if (courses.size() > 1) {
                        p.closeInventory();
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            if (p.isOnline()) {
                                openChoice(p);
                            }
                        }, 2L);
                    }
                }
                case 26 -> {
                    p.closeInventory();
                    leave(p, true);
                }
                default -> {
                }
            }
            return;
        }
        if (e.getInventory().getHolder() instanceof Menu m) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
                return;
            }
            Session s = sessions.get(p.getUniqueId());
            if (s == null || s.course != m.course || s.noFall) {
                p.closeInventory();
                return;
            }
            int slot = e.getSlot();
            if (slot == 47 && s.course.noFall) {
                p.closeInventory();
                enter(p, s.course, -1, true);
                return;
            }
            Progress pr = progress(s.id, s.course);
            if (slot < 45) {
                int first = slot * 10 + 1;
                if (first > s.course.last()) {
                    return;
                }
                if (pr.max < first - 1) {
                    msg(p, "&cFinis d'abord le niveau &f" + (first - 1) + "&c.");
                    return;
                }
                p.closeInventory();
                s.runStart = -1;
                goTo(p, s, first - 1, true);
            } else if (slot == 46) {
                p.closeInventory();
                goTo(p, s, 0, true);
                s.runStart = System.currentTimeMillis();
                msg(p, "&bChrono lance ! &7Finis les " + s.course.last() + " niveaux sans changer de niveau dans le menu.");
            } else if (slot == 48) {
                p.closeInventory();
                s.runStart = -1;
                goTo(p, s, Math.min(s.course.last(), pr.max), true);
            } else if (slot == 45 && courses.size() > 1) {
                p.closeInventory();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (p.isOnline()) {
                        openChoice(p);
                    }
                }, 2L);
            } else if (slot == 50) {
                p.closeInventory();
                showTop(p, s.course);
            } else if (slot == 53) {
                p.closeInventory();
                leave(p, true);
            }
        } else if (e.getWhoClicked() instanceof Player p && sessions.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu || e.getInventory().getHolder() instanceof Choice || e.getInventory().getHolder() instanceof NoFallMenu || e.getWhoClicked() instanceof Player p && sessions.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    void showTop(CommandSender to, Course c) {
        List<Entry> l = top(c);
        msg(to, "&6Classement &f" + c.name + " &7(niveau atteint, puis temps)");
        if (l.isEmpty()) {
            msg(to, "&7Personne n'a encore joué.");
        }
        for (int i = 0; i < Math.min(10, l.size()); i++) {
            Entry e = l.get(i);
            StringBuilder sb = new StringBuilder("&e" + (i + 1) + ". &f" + e.name + " &7- ");
            if (done(e, c)) {
                sb.append("&6&lfini");
                if (e.best >= 0) {
                    sb.append(" &7- chrono &b").append(time(e.best));
                }
                long ft = finishTime(e);
                // 1.6.1 (#149) : avec un chrono, on n'affiche plus le temps de jeu cumule (il s'additionnait)
                if (ft >= 0 && e.best < 0) {
                    // temps de jeu a la 1re fin ; pour ceux qui ont fini avant la 1.4.0, temps de jeu total sur le parcours
                    sb.append(" &7- 1re fin en &f").append(time(ft)).append(" &7de jeu");
                }
            } else {
                sb.append("niveau &a").append(e.max).append("&7/").append(c.last());
            }
            to.sendMessage(c(sb.toString()));
        }
        if (c.noFall && to instanceof Player) {
            to.sendMessage(c("&8Classement sans chute : &7/jump top " + c.id + " sanschute"));
        }
        if (ltEnabled() && to instanceof Player) {
            to.sendMessage(c("&8Temps par niveau : &7/jump top " + c.id + " niveau <n>"));
        }
    }

    void showTopNoFall(CommandSender to, Course c) {
        List<Entry> l = topNoFall(c);
        msg(to, "&4Classement sans chute &f" + c.name + " &7(temps, puis meilleur niveau)");
        if (l.isEmpty()) {
            msg(to, "&7Personne n'a encore tenté le sans chute.");
        }
        for (int i = 0; i < Math.min(10, l.size()); i++) {
            Entry e = l.get(i);
            to.sendMessage(c("&e" + (i + 1) + ". &f" + e.name + " &7- " + (e.finished > 0
                    ? "&4&lréussi" + (e.best >= 0 ? " &7en &b" + time(e.best) : "")
                    : "meilleur niveau &a" + e.max + "&7/" + c.last())));
        }
    }

    // ================================================================ jeu

    /** Teleporte sur la plateforme du point n (deja debloque). */
    void goTo(Player p, Session s, int n, boolean announce) {
        Progress pr = progress(s.id, s.course);
        n = Math.max(0, Math.min(n, pr.max));
        // saut vers un autre niveau (menu, /jump niveau) : ce niveau ne sera pas chronometre
        s.lvValid = n == 0 || n == s.cur && s.lvValid;
        lvReset(s);
        s.cur = n;
        s.levelStart = System.currentTimeMillis();
        s.lastTp = s.levelStart;
        p.setFallDistance(0);
        p.setFireTicks(0);
        p.teleport(s.course.loc(n));
        if (announce) {
            msg(p, n >= s.course.last() ? "&7Tu es sur la plateforme d'arrivee." : "&7Niveau &a" + (n + 1) + "&7/" + s.course.last() + " - bonne chance !");
        }
        updateBoard(p, s);
    }

    /**
     * Mode sans chute : nouvel essai depuis le niveau 1 (chute, objet « recommencer », nouvelle entree). Le meilleur niveau
     * atteint pendant l'essai precedent est garde.
     */
    void restartNoFall(Player p, Session s, boolean voluntary) {
        Course c = s.course;
        int reached = s.cur;
        long now = System.currentTimeMillis();
        NoFall nf = noFall(s.id, c);
        nf.best = Math.max(nf.best, reached);
        nf.playedMs += now - s.enterAt;
        s.enterAt = now;
        // essai deja reussi (on quitte l'arrivee) ou pas encore commence (chrono a l'arret au depart) : pas un nouvel essai
        boolean finishedRun = reached >= c.last();
        boolean notStarted = reached == 0 && s.nfStart < 0;
        if (!finishedRun && !notStarted) {
            nf.attempts++;
        }
        if (finishedRun) {
            voluntary = true;
        }
        storeNoFall(s.id, p.getName(), c, nf);
        s.cur = 0;
        s.nfStart = -1;
        s.levelStart = now;
        s.lastTp = now;
        lvReset(s);
        s.lvValid = true;
        p.setFallDistance(0);
        p.setFireTicks(0);
        p.teleport(c.loc(0));
        if (voluntary) {
            p.sendActionBar(c("&eNouvel essai : &7retour au niveau &f1"));
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f);
            p.sendActionBar(c("&cTombé au niveau " + (reached + 1) + " ! &7Retour au niveau &f1"));
            if (reached >= 5) {
                msg(p, "&7Essai terminé au niveau &c" + (reached + 1) + "&7. Meilleur : &e" + nf.best + "&7/" + c.last() + ".");
            }
        }
        updateBoard(p, s);
    }

    void fall(Player p, Session s) {
        if (s.noFall) {
            restartNoFall(p, s, false);
            return;
        }
        s.falls++;
        s.lastTp = System.currentTimeMillis();
        lvReset(s);
        p.setFallDistance(0);
        p.setFireTicks(0);
        p.teleport(s.course.loc(s.cur));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
        p.sendActionBar(c("&cRate ! &7Retour au niveau &f" + (s.cur + 1)));
        updateBoard(p, s);
    }

    /** Mode sans chute : plateforme n atteinte sans chute depuis le niveau 1. */
    void reachNoFall(Player p, Session s, int n) {
        Course c = s.course;
        long now = System.currentTimeMillis();
        long levelMs = now - s.levelStart;
        String lt = levelTime(p, s, n, now);
        s.abUntil = now + 2500;
        if (s.nfStart < 0) {
            // plateforme 1 atteinte sans etre sorti de l'emprise de la 0 (plateformes qui se touchent) : chrono depuis le depart
            s.nfStart = s.levelStart;
        }
        s.cur = n;
        s.levelStart = now;
        NoFall nf = noFall(s.id, c);
        boolean record = n > nf.best;
        if (record) {
            nf.best = n;
        }
        nf.playedMs += now - s.enterAt;
        s.enterAt = now;
        if (n < c.last()) {
            storeNoFall(s.id, p.getName(), c, nf);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, record ? 1.6f : 1.2f);
            p.sendActionBar(c("&4Sans chute &8| &aNiveau " + n + " réussi" + (lt.isEmpty() ? " &7(" + time(levelMs) + ")" : lt) + (record ? " &e- meilleur niveau !" : "")));
            if (n % 10 == 0) {
                p.showTitle(Title.title(c("&4" + n + "&7/" + c.last()), c(record ? "&eMeilleur niveau sans chute !" : "&7Sans chute, continue !"),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(400))));
            }
            if (c.noFallAnnounce.contains(n)) {
                announceNoFall(p, c, n);
            }
            updateBoard(p, s);
            return;
        }
        // arrivee : tout le parcours sans tomber
        long run = s.nfStart >= 0 ? now - s.nfStart : -1;
        s.nfStart = -1;
        nf.finished++;
        boolean first = nf.finished == 1;
        boolean best = run >= 0 && (nf.bestMs < 0 || run < nf.bestMs);
        if (best) {
            nf.bestMs = run;
        }
        storeNoFall(s.id, p.getName(), c, nf);
        p.showTitle(Title.title(c("&4&lSANS FAUTE !"), c("&e" + c.last() + " niveaux sans tomber"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(5), Duration.ofMillis(800))));
        if (!lt.isEmpty()) {
            p.sendActionBar(c("&4Sans chute &8| &aNiveau " + n + " réussi" + lt));
        }
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 120, 0.6, 1, 0.6, 0.5);
        if (first) {
            Bukkit.broadcast(c(P + "&f" + p.getName() + " &4&la fini " + c.name + " SANS CHUTE &r&7(" + c.last() + " niveaux d'affilée"
                    + (run >= 0 ? ", en &b" + time(run) + "&7" : "") + ") !"));
            getLogger().info(p.getName() + " a fini " + c.id + " sans chute" + (run >= 0 ? " en " + time(run) : "") + ".");
        } else {
            msg(p, "&4&lSans chute réussi &7(" + nf.finished + "e fois)" + (run >= 0 ? " en &b" + time(run) : "") + (best ? " &e- nouveau record !" : "")
                    + (nf.bestMs >= 0 && !best ? " &7(record : &b" + time(nf.bestMs) + "&7)" : ""));
        }
        if (!first && c.noFallBonus != null && c.noFallBonus.getBoolean("enabled", true)
                && data(s.id).getInt("courses." + c.id + ".nofall.bonus", 0) > 0) {
            // #159 : nouvelle fin sans chute = la moitie de la fois d'avant
            payReplay(p, s.id, "courses." + c.id + ".nofall.replays",
                    c.noFallBonus.getInt("coins", 0), c.noFallBonus.getInt("xp", 0), c.name + " - Sans chute");
        }
        grantNoFallBonus(p, s.id, c);
        updateBoard(p, s);
    }

    /** « X a passé le niveau N de Masters sans tomber ! [REGARDER] » à tout le serveur (Esteban, 17/11). */
    void announceNoFall(Player p, Course c, int n) {
        Component btn = c("&a&l[REGARDER]")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/jump regarder " + p.getName()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(c("&7Clique pour regarder &f" + p.getName() + " &7en spectateur")));
        Component line = c(P + "&f" + p.getName() + " &7a passé le &4niveau " + n + " &7de &f" + c.name + " &4sans tomber &7! ").append(btn);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p)) {
                o.sendMessage(line);
            }
        }
        msg(p, "&7Ton passage au &4niveau " + n + " &7sans tomber a été annoncé : on peut te regarder !");
        getLogger().info(p.getName() + " a passe le niveau " + n + " de " + c.id + " sans chute (annonce).");
    }

    /** Spectateurs du jump : spectateur -> joueur regardé. */
    final Map<UUID, UUID> watchers = new HashMap<>();

    /**
     * /jump regarder <joueur> : depuis le lobby ou un monde du jump, hors parcours. Le spectateur est téléporté
     * près du joueur puis mis en spectateur et collé à sa caméra ; /lobby (ou la fin du parcours du joueur) le
     * ramène au hub, où Multiverse remet son mode de jeu. Son inventaire n'est jamais touché.
     */
    void watch(Player viewer, String name) {
        Player target = Bukkit.getPlayerExact(name);
        Session ts = target == null ? null : sessions.get(target.getUniqueId());
        if (target == null || ts == null) {
            msg(viewer, "&c" + name + " n'est plus sur le jump.");
            return;
        }
        if (target.equals(viewer)) {
            msg(viewer, "&7Tu ne peux pas te regarder toi-même.");
            return;
        }
        if (sessions.containsKey(viewer.getUniqueId())) {
            msg(viewer, "&cQuitte d'abord ton parcours (&f/jump quitter&c).");
            return;
        }
        boolean fromHub = viewer.getWorld().equals(Bukkit.getWorlds().get(0));
        if (!fromHub && !ours(viewer.getWorld())) {
            msg(viewer, "&cRetourne au lobby pour regarder un joueur.");
            return;
        }
        watchers.put(viewer.getUniqueId(), target.getUniqueId());
        viewer.teleport(target.getLocation().add(0, 2, 0));
        UUID vid = viewer.getUniqueId(), tid = target.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player v = Bukkit.getPlayer(vid), t = Bukkit.getPlayer(tid);
            if (v == null || !tid.equals(watchers.get(vid))) return;
            if (t == null || !sessions.containsKey(tid) || !v.getWorld().equals(t.getWorld())) {
                stopWatching(v, "&7Le joueur a quitté le jump.");
                return;
            }
            v.setGameMode(GameMode.SPECTATOR);
            v.setSpectatorTarget(t);
            msg(v, "&7Tu regardes &f" + t.getName() + "&7. &f/lobby &7pour revenir.");
        }, 5L);
    }

    void stopWatching(Player v, String why) {
        if (watchers.remove(v.getUniqueId()) == null) return;
        if (why != null) msg(v, why);
        if (ours(v.getWorld())) toHub(v);
    }

    /** Le joueur regardé quitte le jump : ses spectateurs rentrent au hub. */
    void releaseWatchers(UUID target) {
        for (Map.Entry<UUID, UUID> e : new java.util.ArrayList<>(watchers.entrySet())) {
            if (e.getValue().equals(target)) {
                Player v = Bukkit.getPlayer(e.getKey());
                if (v == null) watchers.remove(e.getKey());
                else stopWatching(v, "&7Le joueur que tu regardais a quitté le jump.");
            }
        }
    }

    // ================================================================ temps par niveau (carte #117)

    boolean ltEnabled() {
        return getConfig().getBoolean("level-times.enabled", true);
    }

    static String ltKey(Course c, boolean noFall) {
        return noFall ? nfKey(c) : c.id;
    }

    LevelTimes.Board ltBoard(Course c, boolean noFall) {
        return levelTimes.board(ltKey(c, noFall), c.last());
    }

    /** Niveau a partir duquel un nouveau record du niveau est annonce a tout le serveur (0 = jamais). */
    int announceFrom(Course c) {
        String b = "level-times.announce-from-level.";
        return getConfig().getInt(b + c.id, getConfig().getInt(b + "default", 50));
    }

    /** Le chrono du niveau repartira quand le joueur quittera sa plateforme (retour, chute, reprise). */
    static void lvReset(Session s) {
        s.lvStart = -1;
        s.lvWait = true;
    }

    /** Marqueur pose par /jump set : le premier niveau rejoue apres la reprise n'est pas chronometre. */
    boolean ltSkip(UUID id, Course c, int cur) {
        return data(id).getInt("courses." + c.id + ".lt-skip", -1) == cur;
    }

    /**
     * Plateforme n validee : enregistre le temps du niveau n (depuis la validation de la plateforme n-1, ou depuis le
     * depart de cette plateforme apres un retour / une chute / une reprise) et relance le chrono pour le niveau suivant.
     * Renvoie le texte a ajouter a la barre d'action ("" si le niveau n'etait pas chronometre).
     */
    String levelTime(Player p, Session s, int n, long now) {
        // pas encore parti de la plateforme (plateformes qui se touchent) : chrono depuis l'arrivee sur la plateforme
        long start = s.lvStart >= 0 ? s.lvStart : s.lvWait ? s.lastTp : -1;
        boolean valid = s.lvValid && start >= 0;
        s.lvStart = now;
        s.lvWait = false;
        s.lvValid = true;
        if (!valid || !ltEnabled()) {
            return "";
        }
        long ms = now - start;
        if (ms < getConfig().getInt("level-times.min-ms", 300) || ms > Integer.MAX_VALUE) {
            return "";
        }
        Course c = s.course;
        LevelTimes.Result r = levelTimes.submit(ltKey(c, s.noFall), c.last(), s.id, p.getName(), n, (int) ms);
        StringBuilder sb = new StringBuilder(" &7(&b" + timeCs(ms) + "&7)");
        if (r.levelRecord && r.previousRecord <= 0) {
            sb.append(" &6Premier temps du niveau !");
        } else if (r.levelRecord) {
            sb.append(" &6&lRecord du niveau !");
            int from = announceFrom(c);
            if (from > 0 && n >= from) {
                boolean other = r.previousHolder != null && !r.previousHolder.equals(s.id);
                Bukkit.broadcast(c(P + "&f" + p.getName() + " &7bat le record du &aniveau " + n + " &7de &f" + c.name
                        + (s.noFall ? " &4(sans chute)" : "") + " &7: &b" + timeCs(ms)
                        + " &8(avant : " + timeCs(r.previousRecord) + (other ? " par " + ltBoard(c, s.noFall).name(r.previousHolder) : "") + ")"));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        } else if (r.personal && r.previousMine > 0) {
            sb.append(" &eRecord perso !");
        }
        return sb.toString();
    }

    /** /jump top <parcours> niveau <n> [sanschute] : top 10 du niveau. */
    void showTopLevel(CommandSender to, Course c, int n, boolean nf) {
        LevelTimes.Board b = ltBoard(c, nf);
        List<UUID> l = b.top(n);
        msg(to, "&6Meilleurs temps &f" + c.name + " &7- niveau &a" + n + "&7/" + c.last() + (nf ? " &4(sans chute)" : ""));
        if (l.isEmpty()) {
            msg(to, "&7Personne n'a encore de temps sur ce niveau.");
        }
        for (int i = 0; i < Math.min(10, l.size()); i++) {
            UUID id = l.get(i);
            to.sendMessage(c("&e" + (i + 1) + ". &f" + b.name(id) + " &7- &b" + timeCs(b.mine(id, n))));
        }
        if (to instanceof Player pl) {
            int mine = b.mine(pl.getUniqueId(), n);
            int rank = l.indexOf(pl.getUniqueId()) + 1;
            if (mine <= 0) {
                to.sendMessage(c("&7Tu n'as pas encore de temps sur ce niveau."));
            } else if (rank > 10) {
                to.sendMessage(c("&7Toi : &a" + timeCs(mine) + " &7(" + rank + "e)"));
            }
        }
    }

    /**
     * Classement d'un niveau si les arguments de /jump top contiennent « niveau <n> » ; renvoie faux sinon.
     * Formes : top niveau <n> [sanschute] (parcours en cours ou par defaut), top <parcours> niveau <n> [sanschute].
     */
    boolean topLevel(CommandSender to, Session s, String[] args) {
        int at = -1;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("niveau") || a.equals("level") || a.equals("niv")) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return false;
        }
        Course c = course(at > 1 ? args[1] : s != null ? s.course.id : null);
        if (c == null) {
            msg(to, "&cParcours inconnu.");
            return true;
        }
        if (at + 1 >= args.length) {
            msg(to, "&7/jump top " + c.id + " niveau <1-" + c.last() + "> [sanschute]");
            return true;
        }
        int n;
        try {
            n = Integer.parseInt(args[at + 1]);
        } catch (NumberFormatException e) {
            msg(to, "&cNuméro de niveau invalide.");
            return true;
        }
        if (n < 1 || n > c.last()) {
            msg(to, "&c" + c.name + " &ca des niveaux de 1 à " + c.last() + ".");
            return true;
        }
        boolean nf = args.length > at + 2 && isNoFallArg(args[at + 2]);
        if (nf && !c.noFall) {
            msg(to, "&c" + c.name + " &cn'a pas de mode sans chute.");
            return true;
        }
        if (!ltEnabled()) {
            msg(to, "&7Les temps par niveau sont désactivés.");
            return true;
        }
        showTopLevel(to, c, n, nf);
        return true;
    }

    void reach(Player p, Session s, int n) {
        if (s.noFall) {
            reachNoFall(p, s, n);
            return;
        }
        Course c = s.course;
        long now = System.currentTimeMillis();
        long levelMs = now - s.levelStart;
        String lt = levelTime(p, s, n, now);
        // 1.6.1 (#149) : tout passage complet depuis le debut (niveau 0 -> 1) est chronometre, plus besoin de passer
        // par « Recommencer depuis le debut » ; le classement garde le MEILLEUR chrono, jamais un temps cumule.
        // Un changement de niveau par le menu ou /jump niveau remet runStart a -1 (pas de raccourci).
        if (n == 1 && s.cur == 0 && s.runStart < 0) {
            s.runStart = s.levelStart;
        }
        s.cur = n;
        s.levelStart = now;
        Progress pr = progress(s.id, c);
        data(s.id).set("courses." + c.id + ".lt-skip", null);
        boolean record = n > pr.max;
        if (record) {
            pr.max = n;
        }
        boolean finish = n >= c.last();
        if (finish) {
            pr.finished++;
        }
        if (finish && s.runStart >= 0) {
            long run = now - s.runStart;
            // 1.6.2 (Esteban, 26/11) : seul le record du PARCOURS COMPLET (tous joueurs) est annonce a tout le serveur ;
            // les records par niveau ne le sont plus (announce-from-level a 0), ils spammaient le chat.
            long global = -1;
            for (Entry e : board.getOrDefault(c.id, Map.of()).values()) {
                if (e.best >= 0 && (global < 0 || e.best < global)) global = e.best;
            }
            if (global < 0 || run < global) {
                Bukkit.broadcast(c(P + "&6&l★ &f" + p.getName() + " &abat le record de &f" + c.name + " &a: &b" + time(run)
                        + (global >= 0 ? " &8(avant : " + time(global) + ")" : "")));
            }
            if (pr.bestRunMs < 0 || run < pr.bestRunMs) {
                pr.bestRunMs = run;
                msg(p, "&b&lNouveau record de chrono : &f" + time(run) + " &b!");
            }
            s.runStart = -1;
        }
        pr.current = n;
        pr.falls += s.falls;
        s.falls = 0;
        pr.playedMs += now - s.enterAt;
        s.enterAt = now;
        if (finish && pr.finished == 1 && pr.firstFinishMs < 0) {
            // temps de jeu cumule jusqu'a la premiere fin : affiche dans le classement
            pr.firstFinishMs = pr.playedMs;
        }
        store(s.id, p.getName(), c, pr);
        if (finish) {
            p.showTitle(Title.title(c("&6&lBRAVO !"), c("&eLes " + c.last() + " niveaux sont finis"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(800))));
            if (!lt.isEmpty()) {
                p.sendActionBar(c("&aNiveau " + n + " réussi" + lt));
            }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.6, 1, 0.6, 0.4);
            if ((c.bigBonus && c.bonus == null) || pr.finished == 1) {
                Bukkit.broadcast(c(P + "&f" + p.getName() + " &aa fini les " + c.last() + " niveaux de &f" + c.name + " &a!"));
            }
            YamlConfiguration yd = data(s.id);
            String paidKey = "courses." + c.id + ".finish-paid";
            boolean alreadyPaid = yd.getBoolean(paidKey, false);
            if (alreadyPaid) {
                // #159 : nouvelle fin = la moitie de la recompense de la fois d'avant (fin + bonus du parcours)
                ConfigurationSection fb = c.bigBonus ? (c.bonus != null ? c.bonus : getConfig().getConfigurationSection("rewards.finish-bonus")) : null;
                boolean fbOn = fb != null && fb.getBoolean("enabled", true);
                payReplay(p, s.id, "courses." + c.id + ".replays",
                        rw(c, "finish-coins", 200) + (fbOn ? fb.getInt("coins", 0) : 0),
                        rw(c, "finish-xp", 0) + (fbOn ? fb.getInt("xp", 0) : 0), c.name);
            }
            if (!alreadyPaid && Bukkit.getPluginManager().getPlugin("EemiCore") != null) {
                yd.set(paidKey, true);
                store(s.id, p.getName(), c, pr);
                eemi("eemi reward " + p.getName() + " " + getConfig().getString("eemicore-id", "jump") + " win");
                int coins = rw(c, "finish-coins", 200);
                int fxp = rw(c, "finish-xp", 0);
                if (coins > 0) {
                    eemi("eemi give " + p.getName() + " coins " + coins);
                }
                if (fxp > 0) {
                    eemi("eemi give " + p.getName() + " xp " + fxp);
                }
                if (coins > 0 || fxp > 0) {
                    msg(p, "&6+" + coins + " pieces" + (fxp > 0 ? " &7et &b+" + fxp + " xp" : "") + " &7pour avoir fini le parcours !");
                }
            }
            grantFinishBonus(p, s.id, c);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, record ? 1.6f : 1.2f);
            p.sendActionBar(c("&aNiveau " + n + " réussi" + (lt.isEmpty() ? " &7(" + time(levelMs) + ")" : lt) + (record ? " &e- nouveau niveau atteint !" : "")));
            if (n % 10 == 0) {
                p.showTitle(Title.title(c("&a" + n + "&7/" + c.last()), c(record ? "&eNouveau record !" : "&7Continue !"), Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(400))));
            }
        }
        int every = rw(c, "every-levels", 10);
        if (record && every > 0 && n % every == 0) {
            int coins = rw(c, "coins", 5);
            int xp = rw(c, "xp", 20);
            if (coins > 0) {
                eemi("eemi give " + p.getName() + " coins " + coins);
            }
            if (xp > 0) {
                eemi("eemi give " + p.getName() + " xp " + xp);
            }
            if (coins > 0 || xp > 0) {
                msg(p, "&6Palier " + n + " : &e+" + coins + " pieces&7, &b+" + xp + " xp");
            }
        }
        updateBoard(p, s);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        Location to = e.getTo();
        if (!to.getWorld().equals(s.course.world) || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - s.lastTp < 300) {
            return;
        }
        Course c = s.course;
        if (c.below(to, s.cur) && (c.points || !nearBounce(to, c)) || !c.inside(to) || c.hazard(to)) {
            fall(p, s);
            return;
        }
        // sans chute : le chrono part quand on quitte l'emprise de la plateforme de depart
        if (s.noFall && s.cur == 0 && s.nfStart < 0 && !c.area(0, to)) {
            s.nfStart = now;
        }
        // chrono du niveau apres un retour / une chute / une reprise : il part quand on quitte la plateforme
        if (s.lvWait && !c.area(s.cur, to)) {
            s.lvStart = now;
            s.lvWait = false;
        }
        int n = s.cur + 1;
        if (n <= c.last() && p.isOnGround() && c.on(n, to)) {
            reach(p, s, n);
        }
    }

    /**
     * Saut sur un bloc de slime ou de miel pose au ras du sol (sous la limite de chute) : on ne compte pas la chute tant
     * que le joueur est au-dessus ou juste a cote d'un de ces blocs, sans etre descendu plus bas que son dessus.
     */
    boolean nearBounce(Location to, Course c) {
        int top = (int) Math.floor(c.failY);
        int bx = to.getBlockX(), bz = to.getBlockZ();
        for (int y = top - 1; y >= top - 3; y--) {
            if (to.getY() < y + 0.9) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Material m = to.getWorld().getBlockAt(bx + dx, y, bz + dz).getType();
                    if (m == Material.SLIME_BLOCK || m == Material.HONEY_BLOCK) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld()) || builder(p)) {
            return;
        }
        // grandes feuilles : leur bascule passe par une interaction PHYSICAL, il faut la laisser faire
        boolean dripleaf = e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.BIG_DRIPLEAF;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL && !dripleaf) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack it = e.getItem();
        String tag = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        Session s = sessions.get(p.getUniqueId());
        if (tag == null || s == null) {
            return;
        }
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - s.lastItem < 400) {
            return;
        }
        s.lastItem = now;
        switch (tag) {
            case "back" -> {
                if (s.noFall) {
                    // revenir a la plateforme en plein saut serait une chute evitee : en sans chute, c'est un nouvel essai
                    restartNoFall(p, s, true);
                    return;
                }
                s.lastTp = now;
                lvReset(s);
                p.setFallDistance(0);
                p.setFireTicks(0);
                p.teleport(s.course.loc(s.cur));
            }
            case "menu" -> {
                if (s.noFall) {
                    openNoFallMenu(p, s);
                } else {
                    openMenu(p, s);
                }
            }
            case "hide" -> {
                s.hide = !s.hide;
                applyVisibility(p, s);
                p.getInventory().setItem(5, hideItem(s));
                p.sendActionBar(c(s.hide ? "&7Joueurs caches" : "&aJoueurs visibles"));
            }
            case "leave" -> leave(p, true);
            default -> {
            }
        }
    }

    /** Oeufs de dragon de la map (plots du niveau 247-248 d'Overgrown) : un clic les teleportait, meme pour un admin. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggTeleport(org.bukkit.event.block.BlockFromToEvent e) {
        if (e.getBlock().getType() == Material.DRAGON_EGG && ours(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (ours(e.getPlayer().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE && !sessions.containsKey(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (ours(e.getBlock().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (ours(e.getBlock().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && (sessions.containsKey(p.getUniqueId()) || ours(p.getWorld()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && sessions.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        if (sessions.containsKey(e.getEntity().getUniqueId()) || ours(e.getEntity().getWorld())) {
            e.getDrops().clear();
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) {
            s.lastTp = System.currentTimeMillis();
            lvReset(s);
            e.setRespawnLocation(s.course.loc(s.noFall ? 0 : s.cur));
            if (s.noFall) {
                // une mort en sans chute = une chute : nouvel essai depuis le niveau 1
                Player p = e.getPlayer();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (p.isOnline() && sessions.get(p.getUniqueId()) == s) {
                        restartNoFall(p, s, false);
                    }
                }, 1L);
            }
        }
    }

    /**
     * Sans chute : tout changement de mode de jeu (creatif / spectateur pour voler, puis retour en aventure au-dessus
     * d'une plateforme) annule l'essai en cours. Sans effet si l'essai n'a pas commence (mise en aventure a l'entree).
     */
    @EventHandler(ignoreCancelled = true)
    public void onGameMode(org.bukkit.event.player.PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null || e.getNewGameMode() == p.getGameMode()) {
            return;
        }
        if (!s.noFall) {
            // creatif / spectateur pour voler : le niveau en cours n'est plus chronometre
            if (e.getNewGameMode() == GameMode.CREATIVE || e.getNewGameMode() == GameMode.SPECTATOR) {
                s.lvValid = false;
            }
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || sessions.get(p.getUniqueId()) != s || s.cur == 0 && s.nfStart < 0) {
                return;
            }
            boolean was = s.cur < s.course.last();
            restartNoFall(p, s, true);
            if (was) {
                msg(p, "&cMode de jeu changé : essai sans chute annulé.");
            }
        }, 1L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s != null && !p.getWorld().equals(s.course.world)) {
            leave(p, false);
        }
        // spectateur parti du jump (/lobby) : Multiverse lui remet son mode de jeu, on l'oublie
        if (!ours(p.getWorld())) {
            watchers.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        watchers.remove(e.getPlayer().getUniqueId());
        leave(e.getPlayer(), false);
        cache.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // rattrapage : ceux qui ont deja fini un parcours recoivent les recompenses de fin qui leur manquent
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Course x : courses.values()) {
                grantFinishBonus(p, p.getUniqueId(), x);
                grantNoFallBonus(p, p.getUniqueId(), x);
            }
        }, 100L);
        // reconnecte dans le monde du jump apres une deconnexion : on le remet sur son niveau
        if (ours(p.getWorld())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || !ours(p.getWorld()) || sessions.containsKey(p.getUniqueId())) {
                    return;
                }
                // ancien spectateur (deconnecte en regardant quelqu'un) : retour au hub, pas sur un parcours
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    toHub(p);
                    return;
                }
                Course c = null;
                for (Course x : courses.values()) {
                    if (x.world.equals(p.getWorld())) {
                        c = x;
                    }
                }
                if (c == null) {
                    toHub(p);
                } else {
                    // deja dans le monde du jump : canEnter laisse passer
                    enter(p, c, -1);
                    if (!sessions.containsKey(p.getUniqueId()) && ours(p.getWorld())) {
                        toHub(p);
                    }
                }
            }, 10L);
        }
    }

    // ================================================================ affichage

    void tick() {
        for (Session s : sessions.values()) {
            Player p = Bukkit.getPlayer(s.id);
            if (p != null) {
                updateBoard(p, s);
                if (s.noFall && s.cur < s.course.last() && System.currentTimeMillis() >= s.abUntil) {
                    p.sendActionBar(c("&4Sans chute &8| &fNiveau &a" + (s.cur + 1) + "&7/" + s.course.last() + " &8| &b"
                            + (s.nfStart >= 0 ? time(System.currentTimeMillis() - s.nfStart) : "00:00")));
                }
                if (p.getFoodLevel() < 20) {
                    p.setFoodLevel(20);
                }
            }
        }
    }

    void updateBoard(Player p, Session s) {
        if (s.board == null) {
            return;
        }
        Course c = s.course;
        Objective o = s.board.getObjective("jump");
        if (o == null) {
            o = s.board.registerNewObjective("jump", Criteria.DUMMY, c("&a&lJUMP"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            s.lines.clear();
        }
        long now = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        if (s.noFall) {
            NoFall nf = noFall(s.id, c);
            lines.add("&7 ");
            lines.add("&f" + c.name);
            lines.add("&4&lSANS CHUTE");
            lines.add("&8 ");
            lines.add(s.cur >= c.last() ? "&6Réussi !" : "&fNiveau &a" + (s.cur + 1) + "&7/" + c.last());
            lines.add("&fMeilleur : &e" + Math.max(nf.best, s.cur) + "&7/" + c.last());
            lines.add("&fEssais : &7" + nf.attempts);
            lines.add("&0 ");
            lines.add("&fChrono : &b" + (s.nfStart >= 0 ? time(now - s.nfStart) : "00:00"));
            lines.add("&fRecord : &b" + time(nf.bestMs));
            lines.add("&r ");
        } else {
            Progress pr = progress(s.id, c);
            lines.add("&7 ");
            lines.add("&f" + c.name);
            lines.add("&8 ");
            lines.add(s.cur >= c.last() ? "&6Parcours fini !" : "&fNiveau &a" + (s.cur + 1) + "&7/" + c.last());
            lines.add("&fRecord : &e" + pr.max + "&7/" + c.last());
            lines.add("&fChutes : &c" + (pr.falls + s.falls));
            lines.add("&0 ");
            lines.add(s.runStart >= 0 ? "&fChrono : &b" + time(now - s.runStart) : "&fSur ce niveau : &7" + time(now - s.levelStart));
            lines.add("&r ");
        }
        // seules les lignes qui changent sont renvoyees (pas de clignotement)
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String entry = lines.get(i).replace('&', '§') + "§" + Integer.toHexString(i);
            String old = i < s.lines.size() ? s.lines.get(i) : null;
            if (!entry.equals(old)) {
                if (old != null) {
                    s.board.resetScores(old);
                }
                o.getScore(entry).setScore(score - i);
            }
        }
        s.lines.clear();
        for (int i = 0; i < lines.size(); i++) {
            s.lines.add(lines.get(i).replace('&', '§') + "§" + Integer.toHexString(i));
        }
    }

    // ================================================================ commandes

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> opts = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        if (args.length <= 1) {
            opts.addAll(List.of("parcours", "sanschute", "niveau", "top", "stats", "regarder", "leave"));
            opts.addAll(courses.keySet());
            if (sender.hasPermission("eemijump.admin")) {
                opts.addAll(List.of("reload", "info", "set"));
            }
        } else {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "top", "classement" -> {
                    int at = -1;
                    for (int i = 1; i < args.length - 1; i++) {
                        String a = args[i].toLowerCase();
                        if (a.equals("niveau") || a.equals("level") || a.equals("niv")) {
                            at = i;
                            break;
                        }
                    }
                    if (args.length == 2) {
                        opts.addAll(courses.keySet());
                        opts.add("niveau");
                        opts.add("sanschute");
                    } else if (at < 0) {
                        if (args.length == 3 && courses.containsKey(args[1].toLowerCase())) {
                            opts.add("niveau");
                            if (courses.get(args[1].toLowerCase()).noFall) {
                                opts.add("sanschute");
                            }
                        }
                    } else if (args.length - 1 == at + 1) {
                        Course c = at > 1 ? courses.get(args[1].toLowerCase()) : null;
                        if (c == null && sender instanceof Player pl && sessions.get(pl.getUniqueId()) != null) {
                            c = sessions.get(pl.getUniqueId()).course;
                        }
                        if (c == null) {
                            c = defaultCourse();
                        }
                        if (c != null) {
                            for (int i = 1; i <= c.last(); i++) {
                                opts.add(String.valueOf(i));
                            }
                        }
                    } else if (args.length - 1 == at + 2) {
                        opts.add("sanschute");
                    }
                }
                case "sanschute", "nofall", "hardcore", "hc" -> {
                    if (args.length == 2) {
                        for (Course c : courses.values()) {
                            if (c.noFall) {
                                opts.add(c.id);
                            }
                        }
                    }
                }
                case "regarder", "spectate", "watch", "spec", "stats" -> {
                    if (args.length == 2) {
                        return null;
                    }
                }
                case "set" -> {
                    if (!sender.hasPermission("eemijump.admin")) {
                        return opts;
                    }
                    if (args.length == 2) {
                        return null;
                    }
                    if (args.length == 4) {
                        opts.addAll(courses.keySet());
                    }
                }
                default -> {
                }
            }
        }
        opts.removeIf(o -> !o.toLowerCase().startsWith(last));
        return opts;
    }

    Course course(String id) {
        return id == null ? defaultCourse() : courses.get(id.toLowerCase());
    }

    static boolean isNoFallArg(String a) {
        String s = a.toLowerCase();
        return s.equals("sanschute") || s.equals("nofall") || s.equals("hardcore") || s.equals("hc");
    }

    Course firstNoFall() {
        for (Course c : courses.values()) {
            if (c.noFall) {
                return c;
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        boolean admin = sender.hasPermission("eemijump.admin");
        switch (sub) {
            case "reload" -> {
                if (!admin) {
                    break;
                }
                for (Session s : new ArrayList<>(sessions.values())) {
                    Player p = Bukkit.getPlayer(s.id);
                    if (p != null) {
                        leave(p, true);
                    }
                }
                reloadConfig();
                levelTimes.write(levelTimes.snapshot());
                levelTimes.clear();
                cache.clear();
                loadCourses();
                loadBoard();
                msg(sender, "&aRecharge : " + courses.size() + " parcours.");
                return true;
            }
            case "info" -> {
                if (!admin) {
                    break;
                }
                for (Course c : courses.values()) {
                    msg(sender, "&f" + c.id + " &7: " + c.name + ", " + c.last() + " niveaux, monde " + c.worldName);
                }
                msg(sender, "&7Joueurs sur le jump : &f" + sessions.size());
                return true;
            }
            case "set" -> {
                if (!admin) {
                    break;
                }
                if (args.length < 3) {
                    msg(sender, "&7/jump set <joueur> <niveau> [parcours]");
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
                Course c = course(args.length > 3 ? args[3] : null);
                if (op == null || c == null) {
                    msg(sender, "&cJoueur ou parcours inconnu.");
                    return true;
                }
                int n;
                try {
                    n = Math.max(0, Math.min(c.last(), Integer.parseInt(args[2])));
                } catch (NumberFormatException e) {
                    msg(sender, "&cNiveau invalide.");
                    return true;
                }
                Player online = op.getPlayer();
                if (online != null && sessions.containsKey(online.getUniqueId())) {
                    leave(online, true);
                }
                Progress pr = progress(op.getUniqueId(), c);
                pr.max = n;
                pr.current = n;
                // le niveau rejoue apres cette mise a niveau ne compte pas dans les temps par niveau
                data(op.getUniqueId()).set("courses." + c.id + ".lt-skip", n);
                store(op.getUniqueId(), op.getName() == null ? args[1] : op.getName(), c, pr);
                if (online == null) {
                    cache.remove(op.getUniqueId());
                }
                msg(sender, "&aProgression de &f" + args[1] + " &amise au niveau &f" + n + "&a.");
                return true;
            }
            default -> {
            }
        }
        if (!(sender instanceof Player p)) {
            if (sub.equals("top")) {
                if (topLevel(sender, null, args)) {
                    return true;
                }
                Course c = course(args.length > 1 ? args[1] : null);
                if (c != null) {
                    if (args.length > 2 && isNoFallArg(args[2]) && c.noFall) {
                        showTopNoFall(sender, c);
                    } else {
                        showTop(sender, c);
                    }
                }
                return true;
            }
            msg(sender, "&7/jump reload | info | set <joueur> <niveau> | top [parcours] [niveau <n>] [sanschute]");
            return true;
        }
        Session s = sessions.get(p.getUniqueId());
        switch (sub) {
            case "sanschute", "nofall", "hardcore", "hc" -> {
                Course c = args.length > 1 ? courses.get(args[1].toLowerCase()) : s != null && s.course.noFall ? s.course : firstNoFall();
                if (c == null || !c.noFall) {
                    msg(p, "&cAucun parcours sans chute de ce nom." + (firstNoFall() != null ? " &7Essaie &f/jump sanschute " + firstNoFall().id : ""));
                    return true;
                }
                enter(p, c, -1, true);
            }
            case "", "join", "menu", "jouer" -> {
                if (s != null && s.noFall) {
                    openNoFallMenu(p, s);
                } else if (s != null) {
                    openMenu(p, s);
                } else if (courses.size() > 1) {
                    if (canEnter(p)) {
                        openChoice(p);
                    }
                } else {
                    enter(p, defaultCourse(), -1);
                }
            }
            case "parcours", "choix" -> {
                if (s != null || canEnter(p)) {
                    openChoice(p);
                }
            }
            case "regarder", "spectate", "watch", "spec" -> {
                if (args.length < 2) {
                    msg(p, "&7/jump regarder <joueur>");
                } else {
                    watch(p, args[1]);
                }
            }
            case "leave", "quitter" -> {
                if (s == null) {
                    msg(p, "&7Tu n'es pas sur le jump.");
                } else {
                    leave(p, true);
                }
            }
            case "niveau", "level" -> {
                if (args.length < 2) {
                    msg(p, "&7/jump niveau <numero>");
                    return true;
                }
                int n;
                try {
                    n = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    msg(p, "&cNumero invalide.");
                    return true;
                }
                Course c = s != null ? s.course : defaultCourse();
                if (c == null) {
                    msg(p, "&cLe jump est indisponible.");
                    return true;
                }
                if (s != null && s.noFall) {
                    msg(p, "&cPas de changement de niveau en mode sans chute. &7Reviens au mode normal avec &f/jump " + c.id + "&7.");
                    return true;
                }
                Progress pr = progress(p.getUniqueId(), c);
                if (n < 1 || n - 1 > pr.max) {
                    msg(p, "&cTu n'as pas encore debloque ce niveau (record : " + pr.max + ").");
                    return true;
                }
                if (s == null) {
                    enter(p, c, n - 1);
                } else {
                    s.runStart = -1;
                    goTo(p, s, n - 1, true);
                }
            }
            case "top", "classement" -> {
                if (topLevel(p, s, args)) {
                    return true;
                }
                boolean nf = args.length > 1 && isNoFallArg(args[args.length - 1]);
                String cid = args.length > 1 && !isNoFallArg(args[1]) ? args[1] : (s != null ? s.course.id : null);
                if (nf && (cid == null || course(cid) == null || !course(cid).noFall) && firstNoFall() != null) {
                    cid = firstNoFall().id;
                }
                Course c = course(cid);
                if (c == null) {
                    msg(p, "&cParcours inconnu.");
                } else if (nf && c.noFall || !nf && s != null && s.noFall && s.course == c && args.length < 2) {
                    showTopNoFall(p, c);
                } else {
                    showTop(p, c);
                }
            }
            case "stats" -> {
                Course c = s != null ? s.course : defaultCourse();
                if (c == null) {
                    return true;
                }
                OfflinePlayer t = args.length > 1 ? Bukkit.getOfflinePlayerIfCached(args[1]) : p;
                if (t == null) {
                    msg(p, "&cJoueur inconnu.");
                    return true;
                }
                Progress pr = progress(t.getUniqueId(), c);
                msg(p, "&f" + (t.getName() == null ? args[1] : t.getName()) + " &7sur &f" + c.name + " &7: record &a" + pr.max + "&7/" + c.last()
                        + ", chutes &c" + pr.falls + "&7, fini &f" + pr.finished + " fois&7, chrono &b" + time(pr.bestRunMs)
                        + "&7, temps de jeu &f" + time(pr.playedMs) + (pr.firstFinishMs >= 0 ? "&7, fini en &f" + time(pr.firstFinishMs) + " &7de jeu" : ""));
                if (c.noFall) {
                    NoFall nf = noFall(t.getUniqueId(), c);
                    msg(p, "&4Sans chute &7: meilleur niveau &e" + nf.best + "&7/" + c.last() + ", réussi &f" + nf.finished + " fois&7, record &b" + time(nf.bestMs)
                            + "&7, essais &f" + nf.attempts);
                }
                if (!t.isOnline()) {
                    cache.remove(t.getUniqueId());
                }
            }
            default -> {
                Course direct = courses.get(sub);
                if (direct != null) {
                    if (s != null && s.course == direct && !s.noFall) {
                        openMenu(p, s);
                    } else {
                        enter(p, direct, -1);
                    }
                    return true;
                }
                msg(p, "&7/jump &f(choix du parcours ou menu) &7| /jump <" + String.join("|", courses.keySet()) + "> | /jump sanschute [parcours] | /jump niveau <n> | /jump top [parcours] [sanschute] | /jump top [parcours] niveau <n> [sanschute] | /jump stats [joueur] | /jump leave");
            }
        }
        return true;
    }
}
