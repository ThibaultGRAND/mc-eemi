package fr.eemicraft.dropper;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
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
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Dropper : 5 puits a la suite, tires au hasard dans une reserve de niveaux venus de maps existantes.
 * On tombe dans la zone d'arrivee du fond = niveau suivant ; on touche autre chose = retour au depart du niveau.
 * Le premier a finir les 5 niveaux gagne ; au temps max, classement par niveaux finis puis temps.
 */
public final class EemiDropper extends JavaPlugin implements Listener {
    static final String P = "&b&l[Dropper] &r";
    /** Version de la salle d'attente (data.yml build-version) : la monter force la reconstruction. */
    static final int BUILD_VERSION = WaitingRoom.VERSION;
    /** Salle d'attente : sol en y 100 autour de 0,0. */
    static final int RY = 100;

    enum State { WAITING, COUNTDOWN, PLAYING, ENDING }

    /** Blocs d'arrivee sur lesquels on se pose (on regarde sous les pieds). */
    static final Set<Material> LANDING_ON = EnumSet.of(Material.SLIME_BLOCK, Material.HONEY_BLOCK, Material.HAY_BLOCK);

    static final class Level {
        String id, worldName, name, map;
        int difficulty;
        double sx, sy, sz;
        float yaw, pitch;
        int[] bounds;
        final List<int[]> finish = new ArrayList<>();
        final Set<Material> finishBlocks = EnumSet.noneOf(Material.class);
        boolean nightVision, pumpkin;
        World world;

        Location start() {
            return new Location(world, sx, sy, sz, yaw, pitch);
        }
    }

    static final class Racer {
        final UUID id;
        String name;
        int index;
        int fails;
        long reachedAt;
        long levelStart;
        long finishedAt = -1;
        long[] times;
        boolean leftStart;
        long dropStart;
        long tpAt;
        long resetAt;
        Scoreboard board;

        Racer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    World world;
    State state = State.WAITING;
    final Map<String, Level> levels = new LinkedHashMap<>();
    final Map<UUID, Racer> players = new LinkedHashMap<>();
    final List<UUID> spectators = new ArrayList<>();
    final Set<UUID> gone = new HashSet<>();
    /** Spectateurs qui ont demande a jouer la partie suivante (/dropper join pendant une partie). */
    final Set<UUID> wantsNext = new HashSet<>();
    final List<Level> course = new ArrayList<>();
    final Set<String> forced = new HashSet<>();
    int gen;
    int countdown;
    long gameStart;
    long freezeUntil;
    BossBar bar;
    NamespacedKey itemKey;
    YamlConfiguration records = new YamlConfiguration();
    private int buildVersion;

    static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        s.sendMessage(c(P + m));
    }

    void tell(String m) {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg(p, m);
            }
        }
    }

    List<UUID> all() {
        List<UUID> l = new ArrayList<>(players.keySet());
        l.removeAll(gone);
        l.addAll(spectators);
        return l;
    }

    /** Tache planifiee liee a la partie en cours. */
    void later(long ticks, Runnable r) {
        int g = gen;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (g == gen) {
                r.run();
            }
        }, ticks);
    }

    static String time(long ms) {
        if (ms < 0) {
            return "-";
        }
        long m = ms / 60000;
        long s = (ms / 1000) % 60;
        long cs = (ms / 10) % 100;
        return m + ":" + (s < 10 ? "0" : "") + s + "." + (cs < 10 ? "0" : "") + cs;
    }

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "levels.yml").exists()) {
            saveResource("levels.yml", false);
        } else {
            mergeLevels();
        }
        itemKey = new NamespacedKey(this, "item");
        File f = new File(getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(f);
        buildVersion = data.getInt("build-version", 0);
        records = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "records.yml"));
        createWorld();
        loadLevels();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        sendStatus();
    }

    @Override
    public void onDisable() {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                reset(p);
                if (ours(p.getWorld())) {
                    toHub(p);
                }
            }
        }
        players.clear();
        spectators.clear();
        unforce();
    }

    /** Pour Multiverse : /mv import <monde> normal avec le generateur EemiDropper (monde vide autour des puits). */
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
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
    }

    private void createWorld() {
        String name = getConfig().getString("world", "dropper");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
            getLogger().info("Monde " + name + " charge.");
        }
        if (world == null) {
            getLogger().severe("Impossible de creer le monde " + name);
            return;
        }
        rules(world);
        world.setSpawnLocation(waitSpawn());
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
            }
        }
        if (buildVersion != BUILD_VERSION) {
            build();
        }
    }

    /** Le dossier du monde existe-t-il (a la racine ou deja migre dans dimensions/minecraft) ? */
    boolean worldExists(String name) {
        File root = Bukkit.getWorldContainer();
        if (new File(root, name).isDirectory()) {
            return true;
        }
        String main = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName();
        return new File(root, main + File.separator + "dimensions" + File.separator + "minecraft" + File.separator + name).isDirectory();
    }

    /** Charge le monde d'une map s'il existe. Ne cree jamais un monde neuf a sa place. */
    World loadMapWorld(String name) {
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

    static double num(List<?> l, int i, double def) {
        if (l == null || i >= l.size() || !(l.get(i) instanceof Number n)) {
            return def;
        }
        return n.doubleValue();
    }

    static int[] box(List<?> l) {
        if (l == null || l.size() < 6) {
            return null;
        }
        int[] b = new int[6];
        for (int i = 0; i < 6; i++) {
            b[i] = (int) Math.floor(num(l, i, 0));
        }
        // remet chaque axe dans l'ordre min..max
        for (int i = 0; i < 3; i++) {
            if (b[i] > b[i + 3]) {
                int t = b[i];
                b[i] = b[i + 3];
                b[i + 3] = t;
            }
        }
        return b;
    }

    /** Lit levels.yml, charge les mondes des maps, neutralise leurs blocs de commande. */
    void loadLevels() {
        levels.clear();
        File lf = new File(getDataFolder(), "levels.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(lf);
        ConfigurationSection ls = y.getConfigurationSection("levels");
        if (ls == null) {
            getLogger().warning("levels.yml : aucune section levels.");
            return;
        }
        Map<String, World> worlds = new HashMap<>();
        Map<String, Integer> ignored = new LinkedHashMap<>();
        for (String id : ls.getKeys(false)) {
            ConfigurationSection s = ls.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) {
                continue;
            }
            Level lv = new Level();
            lv.id = id;
            lv.worldName = s.getString("world", "");
            lv.name = s.getString("name", id);
            lv.map = y.getString("worlds." + lv.worldName + ".map", lv.worldName);
            lv.difficulty = Math.max(1, Math.min(5, s.getInt("difficulty", 3)));
            List<?> st = s.getList("start");
            lv.sx = num(st, 0, 0);
            lv.sy = num(st, 1, 100);
            lv.sz = num(st, 2, 0);
            lv.yaw = (float) num(st, 3, 0);
            lv.pitch = (float) num(st, 4, 0);
            lv.bounds = box(s.getList("bounds"));
            List<?> fl = s.getList("finish");
            if (fl != null) {
                for (Object o : fl) {
                    if (o instanceof List<?> b && box(b) != null) {
                        lv.finish.add(box(b));
                    }
                }
            }
            List<String> fb = s.getStringList("finish-blocks");
            if (fb.isEmpty()) {
                fb = List.of("water");
            }
            for (String m : fb) {
                Material mat = Material.matchMaterial(m);
                if (mat == null) {
                    getLogger().warning("Niveau " + id + " : bloc d'arrivee inconnu " + m);
                } else {
                    lv.finishBlocks.add(mat);
                    if (mat == Material.WATER) {
                        lv.finishBlocks.add(Material.BUBBLE_COLUMN);
                    }
                }
            }
            lv.nightVision = s.getBoolean("night-vision", false);
            lv.pumpkin = s.getBoolean("pumpkin", false);
            if (lv.bounds == null || lv.finish.isEmpty() || lv.finishBlocks.isEmpty()) {
                getLogger().warning("Niveau " + id + " incomplet (bounds/finish) : ignore.");
                continue;
            }
            World w = worlds.computeIfAbsent(lv.worldName, n -> {
                World lw = loadMapWorld(n);
                if (lw != null) {
                    List<String> pos = new ArrayList<>(y.getStringList("worlds." + n + ".command-blocks"));
                    pos.addAll(y.getStringList("worlds." + n + ".click-signs"));
                    neutralize(lw, pos);
                }
                return lw;
            });
            if (w == null) {
                ignored.merge(lv.worldName, 1, Integer::sum);
                continue;
            }
            lv.world = w;
            levels.put(id, lv);
        }
        for (Map.Entry<String, Integer> e : ignored.entrySet()) {
            getLogger().warning("Monde " + e.getKey() + " introuvable : " + e.getValue() + " niveau(x) ignore(s). Deposer le dossier du monde a la racine du serveur.");
        }
        getLogger().info(levels.size() + " niveaux de dropper disponibles.");
    }

    /**
     * Ajoute au levels.yml du serveur les niveaux et mondes du jar qu'il n'a pas encore (nouvelles maps), sans toucher
     * aux niveaux existants (reglages, enabled: false). Un niveau efface volontairement se met dans « retired-levels ».
     */
    void mergeLevels() {
        File lf = new File(getDataFolder(), "levels.yml");
        YamlConfiguration jar;
        try (java.io.InputStream in = getResource("levels.yml")) {
            if (in == null) {
                return;
            }
            jar = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        YamlConfiguration srv = new YamlConfiguration();
        try {
            srv.load(lf);
        } catch (Exception e) {
            getLogger().severe("levels.yml illisible, nouveaux niveaux non ajoutes : " + e.getMessage());
            return;
        }
        List<String> retired = srv.getStringList("retired-levels");
        int added = 0, worldsAdded = 0;
        ConfigurationSection jw = jar.getConfigurationSection("worlds");
        if (jw != null) {
            for (String n : jw.getKeys(false)) {
                if (!srv.isConfigurationSection("worlds." + n)) {
                    srv.set("worlds." + n, jw.getConfigurationSection(n));
                    worldsAdded++;
                }
            }
        }
        ConfigurationSection jl = jar.getConfigurationSection("levels");
        if (jl != null) {
            for (String id : jl.getKeys(false)) {
                if (!srv.isConfigurationSection("levels." + id) && !retired.contains(id)) {
                    srv.set("levels." + id, jl.getConfigurationSection(id));
                    added++;
                }
            }
        }
        if (added == 0 && worldsAdded == 0) {
            return;
        }
        try {
            java.nio.file.Files.copy(lf.toPath(), new File(getDataFolder(), "levels.yml.avant-ajout").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            srv.save(lf);
            getLogger().info("levels.yml : " + added + " nouveau(x) niveau(x) et " + worldsAdded + " monde(s) ajoute(s) (copie levels.yml.avant-ajout).");
        } catch (IOException e) {
            getLogger().warning("levels.yml : " + e.getMessage());
        }
    }

    /** Remplace par de l'air les blocs de commande de la map d'origine (une seule fois par monde). */
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
                // blocs de commande, et panneaux a commande cliquable (click-signs)
                if (t == Material.COMMAND_BLOCK || t == Material.CHAIN_COMMAND_BLOCK || t == Material.REPEATING_COMMAND_BLOCK
                        || org.bukkit.Tag.ALL_SIGNS.isTagged(t)) {
                    b.setType(Material.AIR, false);
                    removed++;
                }
            } catch (NumberFormatException e) {
                getLogger().warning("Position invalide : " + pos);
            }
        }
        getLogger().info("Monde " + w.getName() + " : " + removed + " bloc(s) de commande / panneau(x) neutralise(s) sur " + positions.size() + " connus.");
        data.set("neutralized." + w.getName(), true);
        try {
            getDataFolder().mkdirs();
            data.save(f);
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    private void saveBuild() {
        buildVersion = BUILD_VERSION;
        File f = new File(getDataFolder(), "data.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("build-version", buildVersion);
        try {
            getDataFolder().mkdirs();
            y.save(f);
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    Location waitSpawn() {
        return new Location(world, 0.5, RY + 1, 5.5, 180f, 10f);
    }

    boolean ours(World w) {
        if (w == null) {
            return false;
        }
        if (w.equals(world)) {
            return true;
        }
        for (Level l : levels.values()) {
            if (w.equals(l.world)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ salle d'attente

    private final Map<String, BlockData> cache = new HashMap<>();
    private int ops;

    private void set(int x, int y, int z, String b) {
        BlockData d = cache.computeIfAbsent(b, k -> {
            try {
                return Bukkit.createBlockData(k);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bloc invalide ignore : " + k);
                return Bukkit.createBlockData("minecraft:air");
            }
        });
        world.getBlockAt(x, y, z).setBlockData(d, false);
        ops++;
    }

    /** Etat du bloc pour les correcteurs (escaliers, connexions) de la bibliotheque de construction. */
    private String get(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getBlockData().getAsString();
    }

    /** Salle d'attente « tete de puits » (voir WaitingRoom), puis texte des panneaux des regles. */
    void build() {
        long t0 = System.currentTimeMillis();
        ops = 0;
        WaitingRoom.build(fr.eemicraft.dropper.build.Sink.of(this::set, this::get), RY, true);
        writeSigns();
        saveBuild();
        getLogger().info("Salle d'attente construite (version " + WaitingRoom.VERSION + ") : " + ops + " blocs en "
                + (System.currentTimeMillis() - t0) + " ms.");
    }

    private void writeSigns() {
        for (int i = 0; i < WaitingRoom.SIGNS.length; i++) {
            int[] p = WaitingRoom.SIGNS[i];
            Block b = world.getBlockAt(p[0], RY + p[1], p[2]);
            if (!(b.getState() instanceof org.bukkit.block.Sign sign)) {
                getLogger().warning("Panneau des regles absent en " + p[0] + " " + (RY + p[1]) + " " + p[2]);
                continue;
            }
            org.bukkit.block.sign.SignSide side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
            String[] text = WaitingRoom.SIGN_TEXT[i];
            for (int l = 0; l < 4; l++) {
                side.line(l, c(l < text.length ? text[l] : ""));
            }
            side.setGlowingText(i == 0);
            sign.setWaxed(true);
            sign.update(true, false);
        }
    }

    // ================================================================ deroulement

    private void tick() {
        if (world == null) {
            return;
        }
        int min = Math.max(1, getConfig().getInt("min-players", 2));
        int max = getConfig().getInt("max-players", 16);
        switch (state) {
            case WAITING -> {
                if (players.size() >= min) {
                    state = State.COUNTDOWN;
                    countdown = Math.max(5, getConfig().getInt("countdown", 20));
                    tell("&aLa partie commence dans &e" + countdown + " s&a.");
                    sendStatus();
                }
            }
            case COUNTDOWN -> {
                if (players.size() < min) {
                    state = State.WAITING;
                    tell("&cPas assez de joueurs, decompte annule.");
                    sendStatus();
                    return;
                }
                if (players.size() >= max && countdown > 5) {
                    countdown = 5;
                    tell("&aPartie pleine : depart dans &e5 s&a.");
                }
                countdown--;
                if (countdown <= 0) {
                    start();
                } else if (countdown <= 5 || countdown % 10 == 0) {
                    for (UUID id : players.keySet()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            p.showTitle(Title.title(c("&b" + countdown), c("&7Preparez-vous a sauter"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                        }
                    }
                }
            }
            case PLAYING -> playingTick();
            default -> {
            }
        }
    }

    void playingTick() {
        long now = System.currentTimeMillis();
        long maxMs = Math.max(60, getConfig().getInt("max-time", 360)) * 1000L;
        long el = now < freezeUntil ? 0 : now - gameStart;
        long left = Math.max(0, maxMs - el);
        if (bar != null) {
            bar.progress((float) Math.max(0, Math.min(1, left / (double) maxMs)));
            bar.name(c("&bDropper &7- &f" + (left / 60000) + ":" + String.format("%02d", (left / 1000) % 60) + " &7restantes"));
        }
        long attempt = Math.max(10, getConfig().getInt("attempt-time", 45)) * 1000L;
        for (Racer r : new ArrayList<>(players.values())) {
            if (gone.contains(r.id) || r.finishedAt >= 0) {
                continue;
            }
            Player p = Bukkit.getPlayer(r.id);
            if (p == null) {
                continue;
            }
            if (r.leftStart && now - r.dropStart > attempt) {
                fail(p, r, "Trop long !");
            }
        }
        updateBoards();
        if (now >= freezeUntil && left <= 0) {
            tell("&eTemps ecoule !");
            end();
        }
    }

    void start() {
        if (levels.isEmpty()) {
            tell("&cAucun niveau disponible (mondes des maps absents) : partie annulee.");
            state = State.WAITING;
            sendStatus();
            return;
        }
        gen++;
        gone.clear();
        state = State.PLAYING;
        // tirage : n niveaux au hasard, ranges du plus facile au plus dur
        List<Level> pool = new ArrayList<>(levels.values());
        Collections.shuffle(pool);
        int n = Math.max(1, Math.min(pool.size(), getConfig().getInt("levels-per-game", 5)));
        course.clear();
        course.addAll(pool.subList(0, n));
        course.sort(Comparator.comparingInt(l -> l.difficulty));
        forceCourse();
        long now = System.currentTimeMillis();
        long freeze = Math.max(0, getConfig().getInt("start-freeze", 3)) * 1000L;
        freezeUntil = now + freeze;
        gameStart = freezeUntil;
        for (Racer r : players.values()) {
            r.index = 0;
            r.fails = 0;
            r.finishedAt = -1;
            r.times = new long[course.size()];
            r.reachedAt = 0;
            r.levelStart = gameStart;
            r.board = Bukkit.getScoreboardManager().getNewScoreboard();
        }
        bar = BossBar.bossBar(c("&bDropper"), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < course.size(); i++) {
            Level l = course.get(i);
            sb.append(i == 0 ? "" : "&7, ").append("&f").append(i + 1).append(". &b").append(l.name).append(" &8(").append(stars(l.difficulty)).append("&8)");
        }
        tell("&b&lC'est parti ! &7Niveaux : " + sb);
        tell("&7Tombe dans la zone d'arrivee du fond sans rien toucher. &8Se poser sur un bloc = retour au depart.");
        for (Racer r : players.values()) {
            Player p = Bukkit.getPlayer(r.id);
            if (p == null) {
                continue;
            }
            p.setScoreboard(r.board);
            p.showBossBar(bar);
            enterLevel(p, r);
            // kit 2 ticks apres la teleportation (Multiverse-Inventories), puis effets du niveau
            UUID pid = r.id;
            later(2L, () -> {
                Player q = Bukkit.getPlayer(pid);
                if (q != null && players.containsKey(pid) && !gone.contains(pid) && ours(q.getWorld())) {
                    giveKit(q, true);
                }
            });
        }
        hideRacers();
        for (UUID id : spectators) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showBossBar(bar);
                p.teleport(course.get(0).start().add(0, 3, 0));
                p.setGameMode(GameMode.SPECTATOR);
                keepSpectator(p);
            }
        }
        if (freeze > 0) {
            for (int i = 0; i < freeze / 1000; i++) {
                int sec = (int) (freeze / 1000 - i);
                later(i * 20L, () -> {
                    for (Racer r : players.values()) {
                        Player p = Bukkit.getPlayer(r.id);
                        if (p != null && !gone.contains(r.id)) {
                            p.showTitle(Title.title(c("&b" + sec), c("&7" + course.get(0).name), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                        }
                    }
                });
            }
            later(freeze / 50, () -> {
                for (Racer r : players.values()) {
                    Player p = Bukkit.getPlayer(r.id);
                    if (p != null && !gone.contains(r.id)) {
                        p.showTitle(Title.title(c("&a&lSAUTE !"), c(""), Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(300))));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }
            });
        }
        sendStatus();
        updateBoards();
    }

    static String stars(int d) {
        return "&e" + "★".repeat(d) + "&8" + "★".repeat(5 - d);
    }

    void forceCourse() {
        unforce();
        for (Level l : course) {
            int[] b = l.bounds;
            for (int cx = b[0] >> 4; cx <= b[3] >> 4; cx++) {
                for (int cz = b[2] >> 4; cz <= b[5] >> 4; cz++) {
                    if (!l.world.isChunkForceLoaded(cx, cz)) {
                        l.world.setChunkForceLoaded(cx, cz, true);
                        forced.add(l.world.getName() + ":" + cx + ":" + cz);
                    }
                }
            }
        }
    }

    void unforce() {
        for (String s : forced) {
            String[] p = s.split(":");
            World w = Bukkit.getWorld(p[0]);
            if (w != null) {
                w.setChunkForceLoaded(Integer.parseInt(p[1]), Integer.parseInt(p[2]), false);
            }
        }
        forced.clear();
    }

    void hideRacers() {
        for (Racer a : players.values()) {
            Player pa = Bukkit.getPlayer(a.id);
            if (pa == null) {
                continue;
            }
            for (Racer b : players.values()) {
                Player pb = Bukkit.getPlayer(b.id);
                if (pb != null && pb != pa) {
                    pa.hidePlayer(this, pb);
                }
            }
        }
    }

    void showAll(Player p) {
        for (Player q : Bukkit.getOnlinePlayers()) {
            if (q != p) {
                p.showPlayer(this, q);
                q.showPlayer(this, p);
            }
        }
    }

    /** Teleporte le joueur au depart de son niveau et applique les effets du niveau. */
    void enterLevel(Player p, Racer r) {
        Level l = course.get(r.index);
        r.leftStart = false;
        r.tpAt = System.currentTimeMillis();
        p.setFallDistance(0);
        p.setFireTicks(0);
        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        p.teleport(l.start());
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        applyLevelEffects(p, l);
    }

    void applyLevelEffects(Player p, Level l) {
        if (l.nightVision) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
        } else {
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
        p.getInventory().setHelmet(l.pumpkin ? new ItemStack(Material.CARVED_PUMPKIN) : null);
    }

    boolean inBox(int[] b, Block k) {
        return k.getX() >= b[0] && k.getX() <= b[3] && k.getY() >= b[1] && k.getY() <= b[4] && k.getZ() >= b[2] && k.getZ() <= b[5];
    }

    boolean isFinish(Level l, Block k) {
        if (!l.finishBlocks.contains(k.getType())) {
            return false;
        }
        for (int[] b : l.finish) {
            if (inBox(b, k)) {
                return true;
            }
        }
        return false;
    }

    /** Arrivee : bloc des pieds (eau, toile, lianes) ou bloc sous les pieds (slime, miel, foin). */
    boolean reached(Level l, Location to) {
        Block feet = to.getBlock();
        if (!LANDING_ON.contains(feet.getType()) && isFinish(l, feet)) {
            return true;
        }
        Block below = to.clone().subtract(0, 0.2, 0).getBlock();
        return LANDING_ON.contains(below.getType()) && isFinish(l, below);
    }

    void success(Player p, Racer r) {
        long now = System.currentTimeMillis();
        Level l = course.get(r.index);
        long t = now - Math.max(r.levelStart, gameStart);
        r.times[r.index] = t;
        String rec = "";
        long best = records.getLong(l.id + ".time", -1);
        if (best < 0 || t < best) {
            records.set(l.id + ".time", t);
            records.set(l.id + ".name", r.name);
            saveRecords();
            rec = " &6&lNOUVEAU RECORD !";
        } else {
            rec = " &8(record " + time(best) + " par " + records.getString(l.id + ".name", "?") + ")";
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        r.index++;
        r.reachedAt = now - gameStart;
        r.levelStart = now;
        msg(p, "&aNiveau " + r.index + " reussi : &f" + l.name + " &aen &e" + time(t) + rec);
        if (r.index >= course.size()) {
            r.finishedAt = now - gameStart;
            p.getInventory().setHelmet(null);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            int rank = 0;
            for (Racer o : players.values()) {
                if (o.finishedAt >= 0) {
                    rank++;
                }
            }
            p.showTitle(Title.title(c(rank == 1 ? "&6&lPREMIER !" : "&a&lTERMINE !"), c("&7" + time(r.finishedAt) + " - " + rank + (rank == 1 ? "er" : "e")), Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            tell("&b" + r.name + " &afinit les " + course.size() + " niveaux en &e" + time(r.finishedAt) + " &7(" + rank + (rank == 1 ? "er" : "e") + ")");
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(course.get(course.size() - 1).start().add(0, 2, 0));
            if (allDone()) {
                later(20L, this::end);
            }
        } else {
            Level nx = course.get(r.index);
            p.showTitle(Title.title(c("&a&lREUSSI !"), c("&7Niveau " + (r.index + 1) + " : &b" + nx.name), Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(300))));
            enterLevel(p, r);
        }
        sendStatus();
    }

    void fail(Player p, Racer r, String why) {
        r.fails++;
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
        p.sendActionBar(c("&c" + why + " &7(" + r.fails + " echec" + (r.fails > 1 ? "s" : "") + ")"));
        enterLevel(p, r);
    }

    boolean allDone() {
        for (Racer r : players.values()) {
            if (!gone.contains(r.id) && r.finishedAt < 0) {
                return false;
            }
        }
        return true;
    }

    /** Abandon avant d'avoir fini (un joueur qui part apres avoir fini garde sa place et ses recompenses). */
    boolean out(Racer r) {
        return gone.contains(r.id) && r.finishedAt < 0;
    }

    /** Classement : arrives d'abord (temps), puis le plus de niveaux, puis le plus tot arrive a son niveau. */
    List<Racer> ranking() {
        List<Racer> l = new ArrayList<>(players.values());
        l.sort((a, b) -> {
            boolean ga = out(a), gb = out(b);
            if (ga != gb) {
                return ga ? 1 : -1;
            }
            if ((a.finishedAt >= 0) != (b.finishedAt >= 0)) {
                return a.finishedAt >= 0 ? -1 : 1;
            }
            if (a.finishedAt >= 0) {
                return Long.compare(a.finishedAt, b.finishedAt);
            }
            if (a.index != b.index) {
                return Integer.compare(b.index, a.index);
            }
            return Long.compare(a.reachedAt, b.reachedAt);
        });
        return l;
    }

    void end() {
        if (state != State.PLAYING) {
            return;
        }
        state = State.ENDING;
        List<Racer> rank = ranking();
        Racer winner = null;
        if (!rank.isEmpty() && !out(rank.get(0))) {
            Racer top = rank.get(0);
            if (top.finishedAt >= 0 || (getConfig().getBoolean("win-without-finish", true) && top.index > 0)) {
                winner = top;
            }
        }
        tell("&b&l--- Fin de la partie ---");
        int pos = 0;
        for (Racer r : rank) {
            pos++;
            if (pos > 5) {
                break;
            }
            String res = r.finishedAt >= 0 ? "&e" + time(r.finishedAt) : "&7" + r.index + "/" + course.size() + " niveaux";
            tell("&f" + pos + ". &b" + r.name + " &7- " + res + " &8(" + r.fails + " echecs)" + (out(r) ? " &8abandon" : ""));
        }
        for (Racer r : players.values()) {
            if (out(r)) {
                continue;
            }
            eemi("eemi reward " + r.name + " " + coreId() + " play");
            Player p = Bukkit.getPlayer(r.id);
            if (p == null || gone.contains(r.id)) {
                continue;
            }
            StringBuilder sb = new StringBuilder("&7Tes temps : ");
            for (int i = 0; i < course.size(); i++) {
                sb.append(i == 0 ? "" : "&8, ").append("&f").append(course.get(i).name).append(" &e").append(i < r.index ? time(r.times[i]) : "-");
            }
            msg(p, sb.toString());
            boolean won = r == winner;
            p.showTitle(Title.title(c(won ? "&6&lVICTOIRE !" : "&7Fin de partie"), c(winner == null ? "&7Pas de gagnant" : "&e" + winner.name + " gagne"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
            if (won) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        if (winner != null) {
            eemi("eemi reward " + winner.name + " " + coreId() + " win");
            Bukkit.broadcast(c(P + "&b" + winner.name + " &7gagne une partie de Dropper" + (winner.finishedAt >= 0 ? " en &e" + time(winner.finishedAt) : "") + " &7!"));
        }
        sendStatus();
        later(160L, this::backToWaiting);
    }

    /**
     * Fin normale : les joueurs de la partie (arrives ou non, mais pas ceux partis/deconnectes) et ceux qui ont
     * demande a jouer la suivante restent inscrits et reviennent dans la salle d'attente ; les simples
     * spectateurs repartent au hub. Le decompte repart tout seul des qu'il y a assez de joueurs.
     */
    void backToWaiting() {
        gen++;
        Set<UUID> keep = new LinkedHashSet<>();
        for (Racer r : players.values()) {
            if (!gone.contains(r.id)) {
                keep.add(r.id);
            }
        }
        for (UUID id : spectators) {
            if (wantsNext.contains(id)) {
                keep.add(id);
            }
        }
        List<UUID> everyone = all();
        players.clear();
        spectators.clear();
        gone.clear();
        wantsNext.clear();
        int max = getConfig().getInt("max-players", 16);
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            if (!keep.contains(id) || !ours(p.getWorld()) || players.size() >= max) {
                if (keep.contains(id) && players.size() >= max) {
                    msg(p, "&cLa prochaine partie est pleine : retour au lobby.");
                }
                reset(p);
                if (ours(p.getWorld())) {
                    toHub(p);
                }
                continue;
            }
            // teleporter AVANT de vider l'inventaire (reset), puis kit d'attente 2 ticks plus tard
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(waitSpawn());
            reset(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.setAllowFlight(false);
            p.setFlying(false);
            AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(mh != null ? mh.getValue() : 20);
            p.setFoodLevel(20);
            players.put(id, new Racer(id, p.getName()));
            int g = gen;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (g == gen && p.isOnline() && players.containsKey(id) && p.getWorld().equals(world)) {
                    giveKit(p, false);
                    msg(p, "&7Tu restes dans la file du &bDropper&7. Clique sur le lit (ou &f/dropper leave&7) pour revenir au lobby.");
                }
            }, 2L);
        }
        course.clear();
        bar = null;
        unforce();
        state = State.WAITING;
        sendStatus();
    }

    /** Fin : tout le monde au hub, mondes liberes. */
    void finish() {
        gen++;
        List<UUID> everyone = all();
        players.clear();
        spectators.clear();
        gone.clear();
        wantsNext.clear();
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                reset(p);
                if (ours(p.getWorld())) {
                    toHub(p);
                }
            }
        }
        course.clear();
        bar = null;
        unforce();
        state = State.WAITING;
        sendStatus();
    }

    /** Remet le joueur comme au lobby (scoreboard, effets, inventaire du jeu, visibilite). */
    void reset(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (bar != null) {
            p.hideBossBar(bar);
        }
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        if (ours(p.getWorld())) {
            p.getInventory().clear();
        } else {
            // deja arrive ailleurs (/lobby) : on ne retire que nos objets, pas ceux du lobby (DeluxeHub)
            stripKit(p);
        }
        p.setGlowing(false);
        p.setFireTicks(0);
        p.setFallDistance(0);
        showAll(p);
        if (p.getGameMode() != GameMode.CREATIVE) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    /** Retire les objets du Dropper (marques) et la citrouille de niveau, sans toucher au reste. */
    void stripKit(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) {
                inv.setItem(i, null);
            }
        }
        ItemStack h = inv.getHelmet();
        if (h != null && h.getType() == Material.CARVED_PUMPKIN && !h.hasItemMeta()) {
            inv.setHelmet(null);
        }
    }

    /**
     * Apres une teleportation vers un autre monde (mondes des maps importes dans Multiverse), Multiverse peut
     * reimposer le mode de jeu du monde un tick plus tard : on remet le spectateur en SPECTATOR ensuite.
     */
    void keepSpectator(Player p) {
        UUID id = p.getUniqueId();
        later(3L, () -> {
            Player q = Bukkit.getPlayer(id);
            if (q == null || !ours(q.getWorld())) {
                return;
            }
            Racer r = players.get(id);
            boolean spec = spectators.contains(id) || (r != null && !gone.contains(id) && r.finishedAt >= 0);
            if (spec && q.getGameMode() != GameMode.SPECTATOR) {
                q.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    void updateBoards() {
        if (state != State.PLAYING && state != State.ENDING) {
            return;
        }
        List<Racer> rank = ranking();
        long now = System.currentTimeMillis();
        long el = now < freezeUntil ? 0 : now - gameStart;
        for (Racer r : players.values()) {
            if (r.board == null || gone.contains(r.id)) {
                continue;
            }
            Objective o = r.board.getObjective("drop");
            if (o != null) {
                o.unregister();
            }
            o = r.board.registerNewObjective("drop", Criteria.DUMMY, c("&b&lDROPPER"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            int pos = rank.indexOf(r) + 1;
            boolean done = r.finishedAt >= 0;
            Level l = course.isEmpty() ? null : course.get(Math.min(r.index, course.size() - 1));
            List<String> lines = new ArrayList<>();
            lines.add("&7 ");
            lines.add(done ? "&aTermine !" : "&fNiveau &b" + (r.index + 1) + "&7/&b" + course.size());
            lines.add(done ? "&7" + course.size() + " niveaux" : "&3" + (l == null ? "-" : l.name));
            lines.add("&8 ");
            lines.add("&fTemps : &e" + time(done ? r.finishedAt : el).replaceAll("\\.\\d+$", ""));
            lines.add("&fEchecs : &c" + r.fails);
            lines.add("&fPosition : &a" + pos + "&7/" + players.size());
            lines.add("&0 ");
            int score = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                // suffixe invisible unique par ligne
                o.getScore(legacy(lines.get(i)) + "§" + Integer.toHexString(i)).setScore(score--);
            }
        }
    }

    static String legacy(String s) {
        return s.replace('&', '§');
    }

    void saveRecords() {
        try {
            getDataFolder().mkdirs();
            records.save(new File(getDataFolder(), "records.yml"));
        } catch (IOException e) {
            getLogger().warning("records.yml : " + e.getMessage());
        }
    }

    // ================================================================ entree / sortie

    String coreId() {
        return getConfig().getString("eemicore-id", "dropper");
    }

    void eemi(String cmd) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    int active() {
        int n = 0;
        for (Racer r : players.values()) {
            if (!gone.contains(r.id) && r.finishedAt < 0) {
                n++;
            }
        }
        return n;
    }

    void sendStatus() {
        String st = switch (state) {
            case WAITING, COUNTDOWN -> "waiting";
            case PLAYING -> "playing";
            case ENDING -> "ending";
        };
        int waiting = state == State.PLAYING || state == State.ENDING ? 0 : players.size();
        int inGame = state == State.PLAYING ? active() : 0;
        eemi("eemi status " + coreId() + " " + st + " " + waiting + " " + inGame + " " + getConfig().getInt("max-players", 16));
    }

    ItemStack item(Material m, String name, String tag) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tag);
        it.setItemMeta(meta);
        return it;
    }

    void giveKit(Player p, boolean game) {
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        if (game) {
            p.getInventory().setItem(4, item(Material.RED_DYE, "&cRetour au depart du niveau", "reset"));
            if (state == State.PLAYING) {
                Racer r = players.get(p.getUniqueId());
                if (r != null && r.finishedAt < 0 && r.index < course.size()) {
                    applyLevelEffects(p, course.get(r.index));
                }
            }
        }
        p.getInventory().setItem(8, item(Material.RED_BED, "&cQuitter le Dropper", "leave"));
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }

    void join(Player p) {
        if (world == null) {
            msg(p, "&cLe jeu est indisponible.");
            return;
        }
        if (!canEnter(p)) {
            return;
        }
        UUID id = p.getUniqueId();
        if (spectators.contains(id) && state == State.PLAYING && !(players.containsKey(id) && !gone.contains(id))) {
            wantsNext.add(id);
            msg(p, "&7Tu regardes la partie : tu joueras a la suivante.");
            return;
        }
        if (players.containsKey(id) && !gone.contains(id)) {
            msg(p, "&7Tu es deja dans la partie. &f/dropper leave &7pour sortir.");
            return;
        }
        if (state == State.PLAYING || state == State.ENDING) {
            msg(p, "&7Une partie est en cours : tu la regardes, tu joueras a la suivante.");
            spectate(p);
            wantsNext.add(id);
            return;
        }
        spectators.remove(id);
        if (players.size() >= getConfig().getInt("max-players", 16)) {
            msg(p, "&cLa partie est pleine.");
            return;
        }
        if (levels.isEmpty()) {
            msg(p, "&cAucun niveau disponible pour l'instant.");
            return;
        }
        players.put(id, new Racer(id, p.getName()));
        p.teleport(waitSpawn());
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        // l'inventaire est vide apres la teleportation (Multiverse-Inventories enregistre au changement de monde)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && players.containsKey(id) && ours(p.getWorld())) {
                giveKit(p, false);
            }
        }, 2L);
        tell("&b" + p.getName() + " &7rejoint le Dropper (&f" + players.size() + "&7/&f" + getConfig().getInt("max-players", 16) + "&7)");
        int min = getConfig().getInt("min-players", 2);
        if (players.size() < min) {
            msg(p, "&7En attente de &f" + (min - players.size()) + " &7joueur(s)...");
        }
        sendStatus();
    }

    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!ours(p.getWorld()) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemidropper.admin")) {
            msg(p, "&cReviens au lobby (&f/lobby&c) pour rejoindre.");
            return false;
        }
        return true;
    }

    void spectate(Player p) {
        UUID id = p.getUniqueId();
        if (!spectators.contains(id)) {
            spectators.add(id);
        }
        if (state == State.PLAYING && !course.isEmpty()) {
            p.teleport(course.get(0).start().add(0, 3, 0));
            if (bar != null) {
                p.showBossBar(bar);
            }
        } else {
            p.teleport(waitSpawn().add(0, 2, 0));
        }
        p.setGameMode(GameMode.SPECTATOR);
        keepSpectator(p);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && spectators.contains(id) && ours(p.getWorld())) {
                p.getInventory().clear();
            }
        }, 2L);
    }

    /** Sortie de la partie (commande, deconnexion, changement de monde). */
    void leave(Player p, boolean teleport) {
        UUID id = p.getUniqueId();
        boolean was = players.containsKey(id) && !gone.contains(id) || spectators.contains(id);
        if (!was) {
            return;
        }
        spectators.remove(id);
        wantsNext.remove(id);
        Racer r = players.get(id);
        if (r != null) {
            if (state == State.PLAYING || state == State.ENDING) {
                // abandon : reste au classement, le jeu ne le pilote plus
                gone.add(id);
                if (state == State.PLAYING) {
                    if (r.finishedAt < 0) {
                        tell("&b" + r.name + " &7abandonne.");
                    }
                    if (allDone()) {
                        later(20L, this::end);
                    }
                }
            } else {
                players.remove(id);
                tell("&b" + r.name + " &7quitte le Dropper.");
            }
        }
        reset(p);
        if (teleport && ours(p.getWorld())) {
            toHub(p);
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
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        p.teleport(loc);
    }

    // ================================================================ evenements

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld())) {
            return;
        }
        Location to = e.getTo();
        UUID id = p.getUniqueId();
        // salle d'attente : pas de chute dans le vide
        if (p.getWorld().equals(world)) {
            if (to.getY() < RY - 20 && p.getGameMode() != GameMode.SPECTATOR) {
                p.teleport(waitSpawn());
            }
            return;
        }
        Racer r = players.get(id);
        if (state != State.PLAYING || r == null || gone.contains(id) || r.finishedAt >= 0 || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < freezeUntil) {
            Location from = e.getFrom();
            // gel du depart : ni deplacement ni chute (un depart en l'air, comme jad_fin, ne doit pas descendre)
            if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() != from.getY()) {
                Location back = from.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                e.setTo(back);
                p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                p.setFallDistance(0);
            }
            return;
        }
        if (now - r.tpAt < 400 || r.index >= course.size()) {
            return;
        }
        Level l = course.get(r.index);
        if (!to.getWorld().equals(l.world)) {
            return;
        }
        if (!r.leftStart && to.getY() < l.sy - 2) {
            r.leftStart = true;
            r.dropStart = now;
        }
        if (reached(l, to)) {
            success(p, r);
            return;
        }
        Block feet = to.getBlock();
        Block head = to.clone().add(0, 1.2, 0).getBlock();
        if (feet.getType() == Material.LAVA || head.getType() == Material.LAVA) {
            fail(p, r, "Dans la lave !");
            return;
        }
        int[] b = l.bounds;
        if (to.getX() < b[0] || to.getX() >= b[3] + 1 || to.getZ() < b[2] || to.getZ() >= b[5] + 1 || to.getY() < b[1] || to.getY() > b[4] + 6) {
            fail(p, r, "Sorti du puits !");
            return;
        }
        if (!r.leftStart) {
            return;
        }
        if (feet.getType() == Material.WATER || feet.getType() == Material.BUBBLE_COLUMN) {
            fail(p, r, "Mauvaise eau !");
            return;
        }
        if (Tag.CLIMBABLE.isTagged(feet.getType())) {
            fail(p, r, "Accroche a une paroi !");
            return;
        }
        Block below = to.clone().subtract(0, 0.08, 0).getBlock();
        boolean grounded = p.isOnGround() || below.getType().isSolid();
        if (grounded && to.getY() < l.sy - 1.5) {
            fail(p, r, "Tu as touche un bloc !");
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (ours(e.getFrom()) && !ours(e.getPlayer().getWorld())) {
            leave(e.getPlayer(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (ours(p.getWorld())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && ours(p.getWorld())) {
                    reset(p);
                    toHub(p);
                }
            }, 10L);
        }
        // un joueur cache par une ancienne partie redevient visible
        for (Player q : Bukkit.getOnlinePlayers()) {
            if (!players.containsKey(q.getUniqueId()) || state != State.PLAYING) {
                q.showPlayer(this, p);
            }
        }
    }

    private boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE && !players.containsKey(p.getUniqueId());
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

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld()) || builder(p)) {
            return;
        }
        ItemStack it = e.getItem();
        String tag = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (tag != null && e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                e.setCancelled(true);
                if (tag.equals("leave")) {
                    leave(p, true);
                } else if (tag.equals("reset")) {
                    Racer r = players.get(p.getUniqueId());
                    long now = System.currentTimeMillis();
                    if (state == State.PLAYING && r != null && r.finishedAt < 0 && !gone.contains(r.id) && now >= freezeUntil && now - r.resetAt > 1000) {
                        r.resetAt = now;
                        enterLevel(p, r);
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && ours(p.getWorld()) && !builder(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (ours(e.getEntity().getWorld()) && !(e.getEntity().getShooter() instanceof Player p && builder(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (ours(e.getEntity().getWorld()) && e.isGliding()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (ours(e.getEntity().getWorld())) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Player p) {
                p.setFireTicks(0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (ours(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (ours(e.getPlayer().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    // ================================================================ commandes

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "join" : a[0].toLowerCase();
        boolean admin = s.hasPermission("eemidropper.admin");
        switch (sub) {
            case "join", "rejoindre" -> {
                if (s instanceof Player p) {
                    join(p);
                }
            }
            case "leave", "quitter" -> {
                if (s instanceof Player p) {
                    UUID id = p.getUniqueId();
                    if (players.containsKey(id) && !gone.contains(id) || spectators.contains(id)) {
                        leave(p, true);
                    } else {
                        msg(p, "&7Tu n'es pas dans la partie.");
                    }
                }
            }
            case "spectate", "regarder" -> {
                if (s instanceof Player p) {
                    if (players.containsKey(p.getUniqueId()) && !gone.contains(p.getUniqueId())) {
                        msg(p, "&7Tu es deja inscrit.");
                    } else if (canEnter(p)) {
                        spectate(p);
                    }
                }
            }
            case "niveaux", "levels" -> {
                msg(s, "&b" + levels.size() + " niveaux disponibles :");
                for (Level l : levels.values()) {
                    s.sendMessage(c(" &8- &f" + l.name + " &8(" + stars(l.difficulty) + "&8) &7" + l.worldName));
                }
            }
            case "records" -> {
                msg(s, "&bRecords par niveau :");
                for (Level l : levels.values()) {
                    long t = records.getLong(l.id + ".time", -1);
                    s.sendMessage(c(" &8- &f" + l.name + " &7: " + (t < 0 ? "&8aucun" : "&e" + time(t) + " &7par &b" + records.getString(l.id + ".name", "?"))));
                }
            }
            case "start" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else if (state == State.PLAYING || state == State.ENDING) {
                    msg(s, "&cUne partie est deja en cours.");
                } else if (players.isEmpty()) {
                    msg(s, "&cPersonne n'est inscrit.");
                } else {
                    start();
                }
            }
            case "stop" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else {
                    if (state == State.PLAYING || state == State.ENDING) {
                        tell("&cPartie arretee par un admin.");
                    } else {
                        msg(s, "&7Attente remise a zero.");
                    }
                    state = State.ENDING;
                    finish();
                }
            }
            case "build" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else if (state == State.PLAYING) {
                    msg(s, "&cImpossible pendant une partie.");
                } else {
                    build();
                    msg(s, "&aSalle d'attente reconstruite.");
                }
            }
            case "reload" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else if (state == State.PLAYING || state == State.ENDING) {
                    msg(s, "&cImpossible pendant une partie.");
                } else {
                    reloadConfig();
                    loadLevels();
                    msg(s, "&aConfig et niveaux recharges : &f" + levels.size() + " &aniveaux.");
                }
            }
            case "info" -> {
                Map<String, Integer> per = new LinkedHashMap<>();
                for (Level l : levels.values()) {
                    per.merge(l.worldName, 1, Integer::sum);
                }
                msg(s, "&7Etat &f" + state + "&7, inscrits &f" + players.size() + "&7, en course &f" + active() + "&7, niveaux &f" + per);
                if (!course.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Level l : course) {
                        sb.append(l.id).append(' ');
                    }
                    msg(s, "&7Parcours : &f" + sb);
                }
            }
            default -> {
                msg(s, "&f/dropper join &7| &f/dropper leave &7| &f/dropper spectate &7| &f/dropper niveaux &7| &f/dropper records");
                if (admin) {
                    msg(s, "&c/dropper start | stop | build | reload | info");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>(List.of("join", "leave", "spectate", "niveaux", "records"));
        if (s.hasPermission("eemidropper.admin")) {
            out.addAll(List.of("start", "stop", "build", "reload", "info"));
        }
        if (a.length == 1) {
            out.removeIf(x -> !x.startsWith(a[0].toLowerCase()));
            return out;
        }
        return List.of();
    }
}
