package fr.eemicraft.elytra;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

/**
 * Course en elytres : depart groupe depuis une tour, anneaux a traverser dans l'ordre (verts = boost),
 * crash = retour au dernier anneau apres 1 s d'immobilite, classement au temps, records par parcours.
 * Les trois parcours (Canyon rouge, Archipel celeste, Grotte de cristal) sont construits par le plugin.
 */
public final class EemiElytra extends JavaPlugin implements Listener {
    static final String P = "&d&l[Course en élytres] &r";
    /** Changer ce numero (ou le trace dans Course/Builder) reconstruit tous les parcours. */
    static final int BUILD_VERSION = 1;

    enum State { WAITING, COUNTDOWN, PRESTART, RACING, ENDING }

    static final String[] SLOT_CODES = {"&c", "&9", "&a", "&e", "&6", "&5", "&3", "&d", "&f", "&8", "&d", "&b"};

    static final class Racer {
        final UUID id;
        final String name;
        final int slot;
        int next;
        long finishMs = -1;
        int place;
        int hold;
        int grace;
        Location holdLoc;
        Vector push;
        boolean gone;
        int crashes;
        Scoreboard board;

        Racer(UUID id, String name, int slot) {
            this.id = id;
            this.name = name;
            this.slot = slot;
        }
    }

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    World world;
    State state = State.WAITING;
    final Map<String, Course> courses = new LinkedHashMap<>();
    final Set<String> ready = new HashSet<>();
    final Deque<String> buildQueue = new ArrayDeque<>();
    Builder builder;
    String nextCourse;
    String lastCourse;
    /** Parcours impose par /elytra map ou forced-next : pas de nouveau tirage a l'arrivee du premier joueur. */
    boolean nextLocked;
    Course active;
    final Map<UUID, Racer> racers = new LinkedHashMap<>();
    final List<UUID> spectators = new ArrayList<>();
    /** Spectateurs qui ont demande a jouer la course suivante (/elytra join pendant une course). */
    final Set<UUID> wantsNext = new HashSet<>();
    int gen;
    long tickCount;
    int countdown;
    int prestart;
    long goTime;
    long firstFinishAt;
    int finished;
    BossBar bar;
    BukkitTask ticker;
    YamlConfiguration data;
    YamlConfiguration records;
    NamespacedKey itemKey;
    NamespacedKey leaveKey;
    final Random rnd = new Random();
    private final Map<String, BlockData> cache = new HashMap<>();

    static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        s.sendMessage(c(P + m));
    }

    static String legacy(String s) {
        return s.replace('&', '§');
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
        List<UUID> l = new ArrayList<>();
        for (Racer r : racers.values()) {
            if (!r.gone) {
                l.add(r.id);
            }
        }
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

    BlockData data(String b) {
        return cache.computeIfAbsent(b, k -> {
            try {
                return Bukkit.createBlockData(k.contains(":") ? k : "minecraft:" + k);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bloc invalide ignore : " + k);
                return Bukkit.createBlockData("minecraft:air");
            }
        });
    }

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "item");
        leaveKey = new NamespacedKey(this, "leave");
        courses.put("canyon", new Course("canyon", "Canyon rouge", "canyon", 0, 0, 230, 0xC4A11L, 34));
        courses.put("archipel", new Course("archipel", "Archipel céleste", "archipel", 0, 6000, 262, 0xA2C37L, 36));
        courses.put("grotte", new Course("grotte", "Grotte de cristal", "grotte", 0, 12000, 228, 0x6B07DL, 32));
        for (Course k : courses.values()) {
            getLogger().info("Parcours " + k.name + " : " + k.rings.size() + " anneaux dont " + k.boosts() + " boosts, "
                + (int) k.length + " blocs de vol, de y " + (int) k.maxY + " a y " + (int) k.minY + ".");
        }
        data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));
        records = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "records.yml"));
        createWorld();
        if (world != null) {
            for (Course k : courses.values()) {
                String path = "courses." + k.id;
                if (data.getInt(path + ".version", 0) == BUILD_VERSION && data.getBoolean(path + ".complete", false)) {
                    ready.add(k.id);
                } else {
                    buildQueue.add(k.id);
                }
            }
            if (!buildQueue.isEmpty()) {
                getLogger().info("Parcours a construire : " + buildQueue + " (en fond, quelques ms par tick).");
            }
        }
        // porte restee ouverte apres un arret brutal pendant une course
        for (String id : ready) {
            gateFor(courses.get(id), true);
        }
        // 1.1.1 : dernier parcours joue (data.yml, cle last-map), exclu du tirage meme apres un redemarrage
        lastCourse = data.getString("last-map", null);
        nextCourse = chooseNext();
        getServer().getPluginManager().registerEvents(this, this);
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        sendStatus();
    }

    @Override
    public void onDisable() {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && inWorld(p)) {
                toHub(p);
            }
        }
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        racers.clear();
        spectators.clear();
        if (builder != null) {
            getLogger().warning("Construction de " + builder.c.name + " interrompue : elle reprendra au prochain demarrage.");
        }
        if (active != null && world != null) {
            gateFor(active, true);
        }
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

    private void createWorld() {
        String name = getConfig().getString("world", "elytra");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
            getLogger().info("Monde " + name + " charge.");
        }
        if (world == null) {
            getLogger().severe("Impossible de creer le monde " + name);
            return;
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setTime(6000);
        world.setStorm(false);
        Course first = courses.values().iterator().next();
        world.setSpawnLocation(waitSpawn(first));
        // tours de depart chargees en permanence
        for (Course k : courses.values()) {
            for (int cx = Math.floorDiv(k.ox - 16, 16); cx <= Math.floorDiv(k.ox + 4, 16); cx++) {
                for (int cz = Math.floorDiv(k.oz - 8, 16); cz <= Math.floorDiv(k.oz + 8, 16); cz++) {
                    world.setChunkForceLoaded(cx, cz, true);
                }
            }
        }
    }

    void saveData() {
        try {
            getDataFolder().mkdirs();
            data.save(new File(getDataFolder(), "data.yml"));
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    void saveRecords() {
        try {
            getDataFolder().mkdirs();
            records.save(new File(getDataFolder(), "records.yml"));
        } catch (IOException e) {
            getLogger().warning("records.yml : " + e.getMessage());
        }
    }

    // ================================================================ construction

    void startNextBuild() {
        String id = buildQueue.poll();
        Course k = courses.get(id);
        if (k == null) {
            return;
        }
        ready.remove(id);
        String path = "courses." + id;
        List<Long> old = new ArrayList<>();
        for (String s : data.getStringList(path + ".chunks")) {
            String[] a = s.split(";");
            try {
                old.add(Course.key(Integer.parseInt(a[0]), Integer.parseInt(a[1])));
            } catch (RuntimeException ignored) {
                // entree illisible
            }
        }
        int y0 = data.getInt(path + ".ymin", -64), y1 = data.getInt(path + ".ymax", 319);
        builder = new Builder(this, world, k, old, y0, y1);
        List<String> planned = new ArrayList<>();
        for (long key : builder.chunks) {
            planned.add((int) (key >> 32) + ";" + (int) key);
        }
        data.set(path + ".complete", false);
        data.set(path + ".version", BUILD_VERSION);
        data.set(path + ".chunks", planned);
        data.set(path + ".ymin", builder.yLow());
        data.set(path + ".ymax", builder.yHigh());
        saveData();
        getLogger().info("Construction de " + k.name + " : " + builder.steps.size() + " etapes (" + old.size() + " chunks a effacer, " + planned.size() + " chunks).");
    }

    void buildTick() {
        if (builder == null) {
            if (buildQueue.isEmpty() || world == null) {
                return;
            }
            startNextBuild();
            if (builder == null) {
                return;
            }
        }
        // pas de construction pendant une course (certaines etapes depassent le budget)
        if (state == State.RACING || state == State.PRESTART) {
            return;
        }
        int ms = getConfig().getInt("build-ms-per-tick", 25);
        if (builder.run(Math.max(1, ms) * 1_000_000L)) {
            Builder b = builder;
            builder = null;
            data.set("courses." + b.c.id + ".complete", true);
            saveData();
            ready.add(b.c.id);
            getLogger().info("Map construite : " + b.c.name + ", " + b.ops + " blocs en " + (b.nanos / 1_000_000) + " ms ("
                + (System.currentTimeMillis() - b.started) / 1000 + " s au total).");
            if (nextCourse == null || !ready.contains(nextCourse)) {
                if (state == State.WAITING && racers.isEmpty()) {
                    nextCourse = chooseNext();
                }
            }
        }
    }

    String chooseNext() {
        List<String> pool = new ArrayList<>();
        for (String id : getConfig().getStringList("maps").isEmpty() ? new ArrayList<>(courses.keySet()) : getConfig().getStringList("maps")) {
            if (courses.containsKey(id) && ready.contains(id)) {
                pool.add(id);
            }
        }
        if (pool.isEmpty()) {
            for (String id : courses.keySet()) {
                if (ready.contains(id)) {
                    pool.add(id);
                }
            }
        }
        if (pool.isEmpty()) {
            // aucun parcours pret : valeur d'attente au hasard, retiree des qu'un parcours est construit
            List<String> all = new ArrayList<>(courses.keySet());
            return all.get(rnd.nextInt(all.size()));
        }
        if (pool.size() > 1 && lastCourse != null) {
            pool.remove(lastCourse);
        }
        return pool.get(rnd.nextInt(pool.size()));
    }

    // ================================================================ positions

    Location waitSpawn(Course k) {
        return new Location(world, k.ox - 8.5, k.ys, k.oz + 0.5, -90f, 0f);
    }

    Location slotLoc(Course k, int i) {
        int row = i / 6, col = i % 6;
        return new Location(world, (row == 0 ? k.ox : k.ox - 2) + 0.5, k.ys, k.oz - 5 + col * 2 + 0.5, -90f, 0f);
    }

    int terraceY(Course k) {
        return (int) Math.floor(k.endY) - 2;
    }

    Location terraceLoc(Course k) {
        float yaw = (float) Math.toDegrees(Math.atan2(k.endDirX, -k.endDirZ));
        return new Location(world, Math.floor(k.endX) + 0.5, terraceY(k) + 1, Math.floor(k.endZ) + 0.5, yaw, 10f);
    }

    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    static float pitch(double dy) {
        return (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, dy))));
    }

    /** Point de reapparition : 2,5 blocs avant le dernier anneau franchi (ou devant la tour). */
    Location checkpoint(Racer r) {
        Course k = active;
        if (r.next <= 0) {
            return new Location(world, k.p0x, k.p0y + 1, k.p0z, -90f, 10f);
        }
        Course.Ring g = k.rings.get(r.next - 1);
        return new Location(world, g.x - g.nx * 2.5, g.y - g.ny * 2.5 - 0.4, g.z - g.nz * 2.5, yaw(g.nx, g.nz), pitch(g.ny));
    }

    Location hubLoc() {
        World w = Bukkit.getWorld(getConfig().getString("hub.world", "lobby"));
        if (w == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(w, getConfig().getDouble("hub.x", 722.5), getConfig().getDouble("hub.y", 51), getConfig().getDouble("hub.z", 650.5),
            (float) getConfig().getDouble("hub.yaw", 90), (float) getConfig().getDouble("hub.pitch", 0));
    }

    boolean inWorld(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    void gateFor(Course k, boolean closed) {
        for (int y = k.ys; y <= k.ys + 3; y++) {
            for (int z = k.oz - 6; z <= k.oz + 6; z++) {
                world.getBlockAt(k.ox + 1, y, z).setBlockData(data(closed ? "light_blue_stained_glass" : "air"), false);
            }
        }
    }

    // ================================================================ objets

    ItemStack tag(ItemStack it, String name) {
        ItemMeta m = it.getItemMeta();
        m.displayName(c(name));
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean ours(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    void giveKit(Player p) {
        p.getInventory().clear();
        p.getInventory().setChestplate(tag(new ItemStack(Material.ELYTRA), "&dÉlytres de course"));
        int fw = Math.max(0, Math.min(3, getConfig().getInt("fireworks", 0)));
        if (fw > 0) {
            ItemStack it = new ItemStack(Material.FIREWORK_ROCKET, fw);
            FireworkMeta fm = (FireworkMeta) it.getItemMeta();
            fm.setPower(1);
            it.setItemMeta(fm);
            p.getInventory().setItem(0, tag(it, "&eFusée de secours"));
        }
    }

    void stripOurItems(Player p) {
        ItemStack[] cont = p.getInventory().getContents();
        for (int i = 0; i < cont.length; i++) {
            if (ours(cont[i])) {
                p.getInventory().setItem(i, null);
            }
        }
        if (ours(p.getInventory().getChestplate())) {
            p.getInventory().setChestplate(null);
        }
    }

    // ================================================================ deroulement

    private void tick() {
        tickCount++;
        buildTick();
        if (world == null) {
            return;
        }
        if (tickCount % 20 == 0) {
            secondTick();
        }
        if (state == State.PRESTART) {
            prestartTick();
        } else if (state == State.RACING) {
            raceTick();
        }
        if (tickCount % 10 == 0 && (state == State.RACING || state == State.ENDING)) {
            updateBoards();
        }
    }

    private void secondTick() {
        int min = Math.max(1, getConfig().getInt("min-players", 2));
        int max = Math.min(12, getConfig().getInt("max-players", 12));
        switch (state) {
            case WAITING -> {
                if (racers.size() >= min && ready.contains(nextCourse)) {
                    state = State.COUNTDOWN;
                    countdown = getConfig().getInt("countdown", 20);
                    tell("&aDépart dans &e" + countdown + " s &asur &f" + courses.get(nextCourse).name + "&a.");
                    sendStatus();
                }
            }
            case COUNTDOWN -> {
                if (racers.size() < min) {
                    state = State.WAITING;
                    tell("&cPas assez de joueurs, décompte annulé.");
                    sendStatus();
                    return;
                }
                if (racers.size() >= max) {
                    countdown = Math.min(countdown, getConfig().getInt("countdown-full", 5));
                }
                countdown--;
                if (countdown <= 0) {
                    prestart();
                } else if (countdown <= 5 || countdown % 10 == 0) {
                    for (Racer r : racers.values()) {
                        Player p = Bukkit.getPlayer(r.id);
                        if (p != null) {
                            p.showTitle(Title.title(c("&d" + countdown), c("&7Prépare tes élytres"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                        }
                    }
                }
            }
            case RACING -> {
                long now = System.currentTimeMillis();
                long maxMs = getConfig().getInt("max-time", 240) * 1000L;
                if (now - goTime >= maxMs) {
                    tell("&eTemps écoulé !");
                    end();
                } else if (firstFinishAt > 0 && now - firstFinishAt >= getConfig().getInt("finish-grace", 30) * 1000L) {
                    tell("&eFin de la course (" + getConfig().getInt("finish-grace", 30) + " s après le premier).");
                    end();
                } else if (activeCount() == 0 && finished == 0) {
                    end();
                }
                updateBar();
            }
            default -> {
            }
        }
    }

    int activeCount() {
        int n = 0;
        for (Racer r : racers.values()) {
            if (!r.gone && r.finishMs < 0) {
                n++;
            }
        }
        return n;
    }

    int presentCount() {
        int n = 0;
        for (Racer r : racers.values()) {
            if (!r.gone) {
                n++;
            }
        }
        return n;
    }

    /** Joueurs sur les cases de depart, 3-2-1. */
    void prestart() {
        if (!ready.contains(nextCourse)) {
            tell("&cLe parcours est encore en construction, patiente un peu.");
            state = State.WAITING;
            return;
        }
        gen++;
        active = courses.get(nextCourse);
        // 1.1.1 : parcours joue note tout de suite (exclu du prochain tirage, meme apres un redemarrage)
        lastCourse = active.id;
        nextLocked = false;
        data.set("last-map", active.id);
        saveData();
        state = State.PRESTART;
        prestart = 60;
        finished = 0;
        firstFinishAt = 0;
        gateFor(active, true);
        int i = 0;
        for (Racer r : racers.values()) {
            Player p = Bukkit.getPlayer(r.id);
            r.next = 0;
            r.finishMs = -1;
            r.hold = 0;
            r.grace = 0;
            r.crashes = 0;
            if (p == null) {
                continue;
            }
            p.teleport(slotLoc(active, r.slot));
            p.setGameMode(GameMode.ADVENTURE);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setVelocity(new Vector());
            giveKit(p);
            r.board = Bukkit.getScoreboardManager().getNewScoreboard();
            p.setScoreboard(r.board);
            i++;
        }
        // pas de collisions entre coureurs
        for (Racer r : racers.values()) {
            if (r.board == null) {
                continue;
            }
            Team t = r.board.registerNewTeam("eelytra");
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            for (Racer o : racers.values()) {
                t.addEntry(o.name);
            }
        }
        bar = BossBar.bossBar(c("&dCourse en élytres"), 1f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
        tell("&d&l" + active.name + " &7- " + active.rings.size() + " anneaux, traverse-les dans l'ordre. &aVerts &7= boost.");
        updateBoards();
        sendStatus();
    }

    void prestartTick() {
        if (prestart % 20 == 0 && prestart > 0) {
            int n = prestart / 20;
            for (Racer r : racers.values()) {
                Player p = Bukkit.getPlayer(r.id);
                if (p != null && !r.gone) {
                    p.showTitle(Title.title(c("&e&l" + n), c("&7" + active.name), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
            }
        }
        prestart--;
        if (prestart <= 0) {
            go();
        }
    }

    void go() {
        state = State.RACING;
        goTime = System.currentTimeMillis();
        gateFor(active, false);
        double push = getConfig().getDouble("launch-push", 1.2);
        for (Racer r : racers.values()) {
            Player p = Bukkit.getPlayer(r.id);
            if (p == null || r.gone) {
                continue;
            }
            p.showTitle(Title.title(c("&a&lGO !"), c("&7Plane jusqu'au premier anneau"), Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
            p.setVelocity(new Vector(push, 0.42, 0));
            r.grace = 25;
            p.showBossBar(bar);
        }
        for (UUID id : spectators) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showBossBar(bar);
            }
        }
        sendStatus();
    }

    void raceTick() {
        int penalty = Math.max(1, getConfig().getInt("respawn-penalty-ticks", 20));
        for (Racer r : racers.values()) {
            if (r.gone || r.finishMs >= 0) {
                continue;
            }
            Player p = Bukkit.getPlayer(r.id);
            if (p == null) {
                continue;
            }
            if (r.hold > 0) {
                r.hold--;
                p.teleport(r.holdLoc);
                p.setVelocity(new Vector());
                p.setGliding(true);
                if (r.hold % 4 == 0) {
                    world.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.5, 0), 6, 0.4, 0.3, 0.4, 0.01);
                }
                if (r.hold == penalty / 2) {
                    p.sendActionBar(c("&cPénalité... &f" + String.format("%.1f", r.hold / 20.0) + " s"));
                }
                if (r.hold == 0) {
                    p.setGliding(true);
                    p.setVelocity(r.push);
                    r.grace = 12;
                    p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.5f, 1.4f);
                }
                continue;
            }
            if (r.grace > 0) {
                r.grace--;
                @SuppressWarnings("deprecation")
                boolean ground = p.isOnGround();
                if (!p.isGliding() && !ground) {
                    p.setGliding(true);
                }
                if (r.grace == 0 && ground) {
                    crash(r, p, "Reste en l'air !");
                }
            } else if (!p.isGliding()) {
                crash(r, p, "Atterrissage");
                continue;
            }
            if (tickCount % 4 == 0) {
                showRings(p, r);
            }
            if (tickCount % 5 == 0) {
                actionBar(p, r);
            }
        }
    }

    void showRings(Player p, Racer r) {
        List<Course.Ring> rings = active.rings;
        if (r.next >= rings.size()) {
            return;
        }
        Course.Ring g = rings.get(r.next);
        Color col = g.finish ? Color.WHITE : g.boost ? Color.LIME : Color.YELLOW;
        circle(p, g, new Particle.DustOptions(col, 2.0f), 28);
        if (r.next + 1 < rings.size()) {
            circle(p, rings.get(r.next + 1), new Particle.DustOptions(Color.fromRGB(190, 190, 190), 1.0f), 12);
        }
    }

    void circle(Player p, Course.Ring g, Particle.DustOptions dust, int points) {
        double nx = g.nx, ny = g.ny, nz = g.nz;
        double rx = 0, ry = 1, rz = 0;
        if (Math.abs(ny) > 0.9) {
            rx = 1;
            ry = 0;
        }
        double ux = ry * nz - rz * ny, uy = rz * nx - rx * nz, uz = rx * ny - ry * nx;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
        double rad = g.r + 0.1;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            double ca = Math.cos(a) * rad, sa = Math.sin(a) * rad;
            p.spawnParticle(Particle.DUST, g.x + ux * ca + vx * sa, g.y + uy * ca + vy * sa, g.z + uz * ca + vz * sa, 1, 0, 0, 0, 0, dust);
        }
    }

    static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    void actionBar(Player p, Racer r) {
        List<Course.Ring> rings = active.rings;
        if (r.next >= rings.size()) {
            return;
        }
        Course.Ring g = rings.get(r.next);
        Location e = p.getEyeLocation();
        double dx = g.x - e.getX(), dy = g.y - e.getY(), dz = g.z - e.getZ();
        double rel = yaw(dx, dz) - e.getYaw();
        rel = ((rel % 360) + 540) % 360 - 180;
        int idx = Math.floorMod((int) Math.round(rel / 45.0), 8);
        String vert = dy > 4 ? " &b↑" : dy < -4 ? " &6↓" : "";
        String kind = g.finish ? "&f&lARRIVÉE" : g.boost ? "&aBoost" : "&eAnneau";
        p.sendActionBar(c("&d" + ARROWS[idx] + " " + kind + " &f" + (r.next + 1) + "&7/" + rings.size() + " &8| &f" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + " m" + vert));
    }

    /** Crash : retour au dernier anneau franchi, 1 s immobile, puis petite poussee. */
    void crash(Racer r, Player p, String why) {
        if (state != State.RACING || r.hold > 0 || r.finishMs >= 0 || r.gone) {
            return;
        }
        r.crashes++;
        Location cp = checkpoint(r);
        r.holdLoc = cp;
        boolean boost = r.next > 0 && active.rings.get(r.next - 1).boost;
        double push = boost ? getConfig().getDouble("boost-speed", 2.6) : getConfig().getDouble("respawn-push", 1.0);
        r.push = cp.getDirection().multiply(push);
        r.hold = Math.max(1, getConfig().getInt("respawn-penalty-ticks", 20));
        r.grace = 0;
        p.setFallDistance(0);
        p.teleport(cp);
        p.setVelocity(new Vector());
        p.setGliding(true);
        p.showTitle(Title.title(c("&c&lCrash !"), c("&7" + why + " - repars dans " + String.format("%.1f", r.hold / 20.0) + " s"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.8f);
    }

    boolean crosses(Course.Ring g, Location a, Location b) {
        double ax = a.getX() - g.x, ay = a.getY() + 0.4 - g.y, az = a.getZ() - g.z;
        double bx = b.getX() - g.x, by = b.getY() + 0.4 - g.y, bz = b.getZ() - g.z;
        double da = ax * g.nx + ay * g.ny + az * g.nz;
        double db = bx * g.nx + by * g.ny + bz * g.nz;
        if (!(da < 0 && db >= 0)) {
            return false;
        }
        double t = da / (da - db);
        double px = ax + (bx - ax) * t, py = ay + (by - ay) * t, pz = az + (bz - az) * t;
        double rr = g.r + getConfig().getDouble("ring-detect-bonus", 0.6);
        return px * px + py * py + pz * pz <= rr * rr;
    }

    void passRing(Racer r, Player p, Course.Ring g) {
        r.next++;
        if (g.finish) {
            finishRace(r, p);
            return;
        }
        if (g.boost) {
            double sp = getConfig().getDouble("boost-speed", 2.6);
            p.setVelocity(p.getLocation().getDirection().multiply(sp));
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.3f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, g.x, g.y, g.z, 30, 2, 2, 2, 0);
        } else {
            float pitchSnd = 0.8f + 1.2f * r.next / active.rings.size();
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, Math.min(2f, pitchSnd));
        }
        p.spawnParticle(Particle.END_ROD, g.x, g.y, g.z, 20, 1.5, 1.5, 1.5, 0.05);
    }

    static String fmt(long ms) {
        if (ms < 0) {
            return "-";
        }
        long m = ms / 60000, s = (ms / 1000) % 60, cs = (ms / 10) % 100;
        return m + ":" + (s < 10 ? "0" : "") + s + "." + (cs < 10 ? "0" : "") + cs;
    }

    static String place(int n) {
        return n == 1 ? "1er" : n + "e";
    }

    void finishRace(Racer r, Player p) {
        long t = System.currentTimeMillis() - goTime;
        r.finishMs = t;
        r.place = ++finished;
        if (firstFinishAt == 0) {
            firstFinishAt = System.currentTimeMillis();
            if (activeCount() > 0) {
                tell("&7Les autres ont &e" + getConfig().getInt("finish-grace", 30) + " s &7pour finir.");
            }
        }
        p.setGliding(false);
        p.setVelocity(new Vector());
        if (ours(p.getInventory().getChestplate())) {
            p.getInventory().setChestplate(null);
        }
        p.teleport(terraceLoc(active));
        p.setFallDistance(0);
        p.showTitle(Title.title(c(r.place == 1 ? "&6&lVictoire !" : "&a&lArrivée !"), c("&f" + place(r.place) + " &7- &e" + fmt(t)), Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        world.spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.1);
        tell(SLOT_CODES[r.slot] + r.name + " &aarrive " + place(r.place) + " &7en &e" + fmt(t) + (r.crashes > 0 ? " &8(" + r.crashes + " crash)" : ""));
        // records
        String base = active.id + ".";
        long pb = records.getLong(base + "players." + r.id + ".time", -1);
        if (pb < 0 || t < pb) {
            records.set(base + "players." + r.id + ".time", t);
            records.set(base + "players." + r.id + ".name", r.name);
            msg(p, pb < 0 ? "&aPremier temps enregistré sur ce parcours : &e" + fmt(t) : "&a&lNouveau record perso ! &e" + fmt(t) + " &7(avant " + fmt(pb) + ")");
        } else {
            msg(p, "&7Ton record perso : &e" + fmt(pb));
        }
        long best = records.getLong(base + "best.time", -1);
        if (best < 0 || t < best) {
            records.set(base + "best.time", t);
            records.set(base + "best.name", r.name);
            tell("&6&l★ NOUVEAU RECORD DU PARCOURS ★ &f" + r.name + " &e" + fmt(t));
        }
        saveRecords();
        updateBoards();
        if (activeCount() == 0) {
            later(40L, this::end);
        }
    }

    void end() {
        if (state != State.RACING) {
            return;
        }
        state = State.ENDING;
        List<Racer> ranking = ranking();
        tell("&d&l--- Classement : " + active.name + " ---");
        int i = 1;
        Racer winner = null;
        for (Racer r : ranking) {
            String time = r.finishMs >= 0 ? "&e" + fmt(r.finishMs) : "&8non arrivé (" + r.next + "/" + active.rings.size() + ")";
            tell("&f" + i + ". " + SLOT_CODES[r.slot] + r.name + " " + time + (r.gone ? " &8(parti)" : ""));
            if (winner == null && r.finishMs >= 0 && !r.gone) {
                winner = r;
            }
            i++;
        }
        // course lancee seul par un admin (test) : pas de recompenses
        boolean rewards = racers.size() >= 2;
        for (Racer r : racers.values()) {
            if (!r.gone && rewards) {
                eemi("eemi reward " + r.name + " " + coreId() + " play");
            }
        }
        if (winner != null) {
            if (rewards) {
                eemi("eemi reward " + winner.name + " " + coreId() + " win");
            }
            Bukkit.broadcast(c(P + winner.name + " &7gagne la course &f" + active.name + " &7en &e" + fmt(winner.finishMs) + "&7 !"));
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Racer r = racers.get(id);
            boolean won = r != null && r == winner;
            p.showTitle(Title.title(c(won ? "&6&lVICTOIRE !" : "&7Fin de course"), c(winner == null ? "&7Personne n'est arrivé" : "&e" + winner.name + " &7- " + fmt(winner.finishMs)), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
            if (r != null && r.finishMs < 0) {
                p.setGliding(false);
                p.setVelocity(new Vector());
                stripOurItems(p);
                p.teleport(terraceLoc(active));
                r.finishMs = -2;
            }
        }
        updateBoards();
        sendStatus();
        later(120L, this::backToWaiting);
    }

    List<Racer> ranking() {
        List<Racer> l = new ArrayList<>(racers.values());
        Map<UUID, Double> dist = new HashMap<>();
        for (Racer r : l) {
            double d = Double.MAX_VALUE;
            Player p = Bukkit.getPlayer(r.id);
            if (p != null && active != null && r.next < active.rings.size() && inWorld(p)) {
                Course.Ring g = active.rings.get(r.next);
                d = p.getLocation().distanceSquared(new Location(world, g.x, g.y, g.z));
            }
            dist.put(r.id, d);
        }
        l.sort(Comparator.<Racer>comparingInt(r -> r.finishMs >= 0 ? 0 : 1)
            .thenComparingLong(r -> r.finishMs >= 0 ? r.finishMs : 0)
            .thenComparingInt(r -> -r.next)
            .thenComparingDouble(r -> dist.get(r.id)));
        return l;
    }

    /** Objet « Retour au lobby » de la salle d'attente (case 8). */
    void giveWaitKit(Player p) {
        ItemStack bed = tag(new ItemStack(Material.RED_BED), "&cRetour au lobby &7(clic droit)");
        ItemMeta m = bed.getItemMeta();
        m.getPersistentDataContainer().set(leaveKey, PersistentDataType.BYTE, (byte) 1);
        bed.setItemMeta(m);
        p.getInventory().setItem(8, bed);
    }

    boolean isLeaveItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(leaveKey, PersistentDataType.BYTE);
    }

    /**
     * Fin normale : les coureurs de la course (arrives ou non, mais pas ceux partis/deconnectes) et les
     * spectateurs qui ont demande la suivante restent inscrits et reviennent dans la salle d'attente du
     * prochain parcours ; les simples spectateurs repartent au hub. Le decompte repart tout seul.
     */
    void backToWaiting() {
        gen++;
        Map<UUID, Integer> keep = new LinkedHashMap<>();
        for (Racer r : racers.values()) {
            if (!r.gone) {
                keep.put(r.id, r.slot);
            }
        }
        for (UUID id : spectators) {
            if (wantsNext.contains(id) && !keep.containsKey(id)) {
                keep.put(id, -1);
            }
        }
        List<UUID> everyone = all();
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
            bar = null;
        }
        if (active != null) {
            gateFor(active, true);
            lastCourse = active.id;
        }
        active = null;
        state = State.WAITING;
        String forced = getConfig().getString("forced-next", "");
        nextCourse = chooseNext();
        nextLocked = false;
        if (forced != null && !forced.isEmpty() && ready.contains(forced)) {
            nextCourse = forced;
            nextLocked = true;
        }
        getConfig().set("forced-next", null);
        boolean courseOk = ready.contains(nextCourse);
        Course k = courses.get(nextCourse);
        racers.clear();
        spectators.clear();
        wantsNext.clear();
        int max = Math.min(12, getConfig().getInt("max-players", 12));
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Integer old = keep.get(id);
            int slot = old == null ? -1 : old >= 0 ? old : freeSlot();
            if (old == null || !courseOk || !inWorld(p) || slot < 0 || racers.size() >= max) {
                if (old != null && inWorld(p)) {
                    msg(p, !courseOk ? "&cLe prochain parcours est encore en construction : retour au lobby." : "&cLa prochaine course est pleine : retour au lobby.");
                }
                if (inWorld(p)) {
                    toHub(p);
                }
                continue;
            }
            // teleporter AVANT de vider l'inventaire, kit d'attente 2 ticks plus tard
            p.setGliding(false);
            p.setVelocity(new Vector());
            p.setFallDistance(0);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(waitSpawn(k));
            p.getInventory().clear();
            p.setGlowing(false);
            for (PotionEffect pe : p.getActivePotionEffects()) {
                p.removePotionEffect(pe.getType());
            }
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.setAllowFlight(false);
            p.setFlying(false);
            org.bukkit.attribute.AttributeInstance mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            p.setHealth(mh != null ? mh.getValue() : 20);
            p.setFoodLevel(20);
            p.setSaturation(20);
            racers.put(id, new Racer(id, p.getName(), slot));
            int g = gen;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (g == gen && p.isOnline() && inWorld(p) && racers.containsKey(id) && state != State.PRESTART && state != State.RACING) {
                    p.getInventory().clear();
                    p.setGameMode(GameMode.ADVENTURE);
                    giveWaitKit(p);
                    msg(p, "&7Tu restes dans la file de la &dCourse en élytres&7. Clique sur le lit (ou &f/elytra leave&7) pour revenir au lobby.");
                }
            }, 2L);
        }
        if (!racers.isEmpty()) {
            tell("&7Prochain parcours : &d" + k.name);
        }
        sendStatus();
    }

    /** Fin : tout le monde au hub, porte refermee, parcours suivant tire au sort. */
    void finish() {
        gen++;
        List<UUID> everyone = all();
        racers.clear();
        spectators.clear();
        wantsNext.clear();
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && inWorld(p)) {
                toHub(p);
            }
        }
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
            bar = null;
        }
        if (active != null) {
            gateFor(active, true);
            lastCourse = active.id;
        }
        active = null;
        state = State.WAITING;
        String forced = getConfig().getString("forced-next", "");
        nextCourse = chooseNext();
        nextLocked = false;
        if (forced != null && !forced.isEmpty() && ready.contains(forced)) {
            nextCourse = forced;
            nextLocked = true;
        }
        getConfig().set("forced-next", null);
        sendStatus();
    }

    void updateBar() {
        if (bar == null || state != State.RACING) {
            return;
        }
        long maxMs = getConfig().getInt("max-time", 240) * 1000L;
        long left = Math.max(0, maxMs - (System.currentTimeMillis() - goTime));
        if (firstFinishAt > 0) {
            left = Math.min(left, Math.max(0, getConfig().getInt("finish-grace", 30) * 1000L - (System.currentTimeMillis() - firstFinishAt)));
        }
        bar.name(c((firstFinishAt > 0 ? "&eFin dans " : "&dTemps restant ") + "&f" + (left / 60000) + ":" + String.format("%02d", (left / 1000) % 60)));
        bar.progress(Math.max(0f, Math.min(1f, (float) left / maxMs)));
    }

    void updateBoards() {
        if (active == null) {
            return;
        }
        List<Racer> rk = ranking();
        int total = 0;
        for (Racer r : racers.values()) {
            if (!r.gone) {
                total++;
            }
        }
        String recTime = fmt(records.getLong(active.id + ".best.time", -1));
        String recName = records.getString(active.id + ".best.name", "personne");
        long elapsed = state == State.RACING || state == State.ENDING ? System.currentTimeMillis() - goTime : 0;
        for (Racer r : racers.values()) {
            if (r.gone || r.board == null) {
                continue;
            }
            Objective o = r.board.getObjective("elytra");
            if (o != null) {
                o.unregister();
            }
            o = r.board.registerNewObjective("elytra", Criteria.DUMMY, c("&d&lCOURSE EN ÉLYTRES"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            int line = 15;
            o.getScore(legacy("&7" + active.name)).setScore(line--);
            o.getScore(legacy(" ")).setScore(line--);
            o.getScore(legacy("&fAnneau : &e" + Math.min(r.next, active.rings.size()) + "&7/" + active.rings.size())).setScore(line--);
            o.getScore(legacy("&fTemps : &e" + fmt(r.finishMs >= 0 ? r.finishMs : elapsed))).setScore(line--);
            o.getScore(legacy("&fPosition : &e" + (rk.indexOf(r) + 1) + "&7/" + total)).setScore(line--);
            o.getScore(legacy("  ")).setScore(line--);
            o.getScore(legacy("&fRecord : &6" + recTime)).setScore(line--);
            o.getScore(legacy("&8" + recName)).setScore(line--);
            o.getScore(legacy("&fTon record : &e" + fmt(records.getLong(active.id + ".players." + r.id + ".time", -1)))).setScore(line--);
        }
    }

    // ================================================================ entree / sortie

    String coreId() {
        return getConfig().getString("eemicore-id", "elytra");
    }

    void eemi(String cmd) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    void sendStatus() {
        String st = switch (state) {
            case WAITING, COUNTDOWN -> "waiting";
            case PRESTART, RACING -> "playing";
            case ENDING -> "ending";
        };
        boolean playing = state == State.PRESTART || state == State.RACING || state == State.ENDING;
        int waiting = playing ? 0 : racers.size();
        int inGame = playing ? presentCount() : 0;
        eemi("eemi status " + coreId() + " " + st + " " + waiting + " " + inGame + " " + Math.min(12, getConfig().getInt("max-players", 12)));
    }

    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!inWorld(p) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemielytra.admin")) {
            msg(p, "&cReviens au lobby (&f/lobby&c) pour rejoindre.");
            return false;
        }
        return true;
    }

    int freeSlot() {
        boolean[] used = new boolean[12];
        for (Racer r : racers.values()) {
            used[r.slot] = true;
        }
        for (int i = 0; i < 12; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return -1;
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
        if (racers.containsKey(id) && !racers.get(id).gone) {
            msg(p, "&7Tu es déjà inscrit. &f/elytra leave &7pour sortir.");
            return;
        }
        if (state != State.WAITING && state != State.COUNTDOWN) {
            msg(p, "&7Une course est en cours : tu la regardes, tu joueras à la suivante.");
            spectate(p);
            wantsNext.add(id);
            return;
        }
        // 1.1.1 : premier joueur dans une salle d'attente vide = nouveau parcours tire au hasard
        if (state == State.WAITING && racers.isEmpty() && !nextLocked) {
            nextCourse = chooseNext();
        }
        if (!ready.contains(nextCourse)) {
            nextCourse = chooseNext();
            if (!ready.contains(nextCourse)) {
                msg(p, "&cLes parcours sont en construction" + (builder != null ? " (" + builder.c.name + " " + builder.percent() + " %)" : "") + ", réessaie dans un moment.");
                return;
            }
        }
        int max = Math.min(12, getConfig().getInt("max-players", 12));
        int slot = freeSlot();
        if (racers.size() >= max || slot < 0) {
            msg(p, "&cLa course est pleine.");
            return;
        }
        spectators.remove(id);
        racers.put(id, new Racer(id, p.getName(), slot));
        Course k = courses.get(nextCourse);
        p.teleport(waitSpawn(k));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inWorld(p) && racers.containsKey(id)) {
                p.getInventory().clear();
                for (PotionEffect pe : p.getActivePotionEffects()) {
                    p.removePotionEffect(pe.getType());
                }
                p.setGameMode(GameMode.ADVENTURE);
                p.setFoodLevel(20);
                p.setSaturation(20);
                p.setAllowFlight(false);
                p.setFlying(false);
                giveWaitKit(p);
            }
        }, 2L);
        tell(SLOT_CODES[slot] + p.getName() + " &7rejoint la course (&f" + racers.size() + "&7/&f" + max + "&7) - parcours &f" + k.name);
        int min = getConfig().getInt("min-players", 2);
        if (racers.size() < min) {
            msg(p, "&7En attente de &f" + (min - racers.size()) + " &7joueur(s)...");
        }
        sendStatus();
    }

    void spectate(Player p) {
        if (racers.containsKey(p.getUniqueId()) && !racers.get(p.getUniqueId()).gone) {
            msg(p, "&7Tu es déjà inscrit.");
            return;
        }
        if (!spectators.contains(p.getUniqueId())) {
            spectators.add(p.getUniqueId());
        }
        Course k = active != null ? active : courses.get(nextCourse);
        p.teleport(waitSpawn(k).add(4, 8, 0));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inWorld(p) && spectators.contains(p.getUniqueId())) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                if (bar != null) {
                    p.showBossBar(bar);
                }
            }
        }, 2L);
    }

    /** Sortie (commande, deconnexion, changement de monde). */
    void leave(Player p, boolean teleport) {
        UUID id = p.getUniqueId();
        boolean was = spectators.remove(id);
        wantsNext.remove(id);
        Racer r = racers.get(id);
        if (r != null && !r.gone) {
            was = true;
            if (state == State.WAITING || state == State.COUNTDOWN) {
                racers.remove(id);
                tell(SLOT_CODES[r.slot] + r.name + " &7quitte la course.");
            } else {
                r.gone = true;
                if (r.finishMs < 0 && state == State.RACING) {
                    tell(SLOT_CODES[r.slot] + r.name + " &7abandonne.");
                }
                for (Racer o : racers.values()) {
                    if (o.board != null) {
                        Team t = o.board.getTeam("eelytra");
                        if (t != null) {
                            t.removeEntry(r.name);
                        }
                    }
                }
            }
        }
        if (!was) {
            return;
        }
        if (bar != null) {
            p.hideBossBar(bar);
        }
        if (teleport && inWorld(p)) {
            toHub(p);
        } else {
            reset(p);
        }
        sendStatus();
    }

    void reset(Player p) {
        stripOurItems(p);
        p.setGliding(false);
        p.setGlowing(false);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (p.getWorld().getName().equals(getConfig().getString("hub.world", "lobby"))
            && (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.ADVENTURE)) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    void toHub(Player p) {
        p.getInventory().clear();
        p.setGliding(false);
        p.setGlowing(false);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.setVelocity(new Vector());
        p.setFallDistance(0);
        p.setGameMode(GameMode.SURVIVAL);
        if (bar != null) {
            p.hideBossBar(bar);
        }
        p.teleport(hubLoc());
    }

    // ================================================================ evenements

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inWorld(p) || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location from = e.getFrom(), to = e.getTo();
        Racer r = racers.get(p.getUniqueId());
        if (r != null && !r.gone && state == State.PRESTART) {
            // immobile sur sa case jusqu'au GO
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location l = from.clone();
                l.setYaw(to.getYaw());
                l.setPitch(to.getPitch());
                e.setTo(l);
            }
            return;
        }
        if (r != null && !r.gone && state == State.RACING && active != null) {
            if (r.finishMs >= 0) {
                if (to.getY() < terraceY(active) - 15) {
                    p.teleport(terraceLoc(active));
                }
                return;
            }
            if (r.hold > 0) {
                return;
            }
            List<Course.Ring> rings = active.rings;
            if (r.next < rings.size() && crosses(rings.get(r.next), from, to)) {
                passRing(r, p, rings.get(r.next));
                return;
            }
            if (r.grace > 0) {
                return;
            }
            double prevX, prevY, prevZ;
            if (r.next == 0) {
                prevX = active.p0x;
                prevY = active.p0y;
                prevZ = active.p0z;
            } else {
                Course.Ring g = rings.get(r.next - 1);
                prevX = g.x;
                prevY = g.y;
                prevZ = g.z;
            }
            Course.Ring nx = rings.get(Math.min(r.next, rings.size() - 1));
            double limit = Math.min(prevY, nx.y) - getConfig().getDouble("fall-depth", 30);
            if (to.getY() < limit) {
                crash(r, p, "Trop bas");
                return;
            }
            Block feet = to.getBlock();
            Block mid = to.clone().add(0, 0.5, 0).getBlock();
            if (feet.isLiquid() || mid.isLiquid()) {
                crash(r, p, "Plouf");
                return;
            }
            @SuppressWarnings("deprecation")
            boolean ground = p.isOnGround();
            if (ground) {
                crash(r, p, "Au sol");
                return;
            }
            if (segDist(to, prevX, prevY, prevZ, nx.x, nx.y, nx.z) > getConfig().getDouble("max-distance", 90)) {
                crash(r, p, "Hors parcours");
            }
            return;
        }
        // salle d'attente / terrasse : on ne tombe pas de la tour
        if (!builder(p)) {
            if (state == State.ENDING && active != null) {
                if (to.getY() < terraceY(active) - 15) {
                    p.teleport(terraceLoc(active));
                }
                return;
            }
            Course k = active != null ? active : courses.get(nextCourse);
            if (k != null && to.getY() < k.ys - 8) {
                if (ready.contains(k.id)) {
                    p.teleport(waitSpawn(k));
                } else {
                    toHub(p);
                }
            }
        }
    }

    static double segDist(Location p, double ax, double ay, double az, double bx, double by, double bz) {
        double vx = bx - ax, vy = by - ay, vz = bz - az;
        double wx = p.getX() - ax, wy = p.getY() - ay, wz = p.getZ() - az;
        double l2 = vx * vx + vy * vy + vz * vz;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, (wx * vx + wy * vy + wz * vz) / l2));
        double dx = wx - vx * t, dy = wy - vy * t, dz = wz - vz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world) || !(e.getEntity() instanceof Player p)) {
            return;
        }
        Racer r = racers.get(p.getUniqueId());
        if (e.isGliding()) {
            if (r == null || r.gone || state != State.RACING || r.finishMs >= 0) {
                if (!builder(p)) {
                    e.setCancelled(true);
                }
            }
            return;
        }
        if (r != null && !r.gone && state == State.RACING && r.finishMs < 0 && r.hold == 0 && r.grace == 0) {
            later(1L, () -> {
                if (p.isOnline() && !p.isGliding()) {
                    crash(r, p, "Atterrissage");
                }
            });
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (world != null && e.getFrom().equals(world)) {
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
        if (inWorld(p)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && inWorld(p)) {
                    toHub(p);
                }
            }, 10L);
        }
    }

    private boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE && !racers.containsKey(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (world == null || !p.getWorld().equals(world) || builder(p)) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Racer r = racers.get(p.getUniqueId());
            ItemStack it = e.getItem();
            if (isLeaveItem(it) && e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                e.setUseItemInHand(Event.Result.DENY);
                e.setCancelled(true);
                if ((r != null && !r.gone) || spectators.contains(p.getUniqueId())) {
                    leave(p, true);
                } else {
                    stripOurItems(p);
                }
                return;
            }
            boolean rocket = it != null && it.getType() == Material.FIREWORK_ROCKET && ours(it)
                && r != null && !r.gone && state == State.RACING && r.finishMs < 0 && r.hold == 0 && p.isGliding();
            if (!rocket) {
                e.setUseItemInHand(Event.Result.DENY);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world)) {
            return;
        }
        if (e.getEntity() instanceof Firework && e.getEntity().getShooter() instanceof Player p && racers.containsKey(p.getUniqueId())) {
            return;
        }
        if (!(e.getEntity().getShooter() instanceof Player p && builder(p))) {
            e.setCancelled(true);
        }
    }

    /** WorldGuard bloque les apparitions par plugin : on retablit les fusees des coureurs. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world) && e.getEntity() instanceof Firework fw
            && fw.getShooter() instanceof Player p && racers.containsKey(p.getUniqueId())) {
            e.setCancelled(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world)) {
            return;
        }
        e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL && e.getEntity() instanceof Player p) {
            Racer r = racers.get(p.getUniqueId());
            if (r != null && !r.gone && state == State.RACING && r.finishMs < 0 && r.hold == 0 && r.grace == 0) {
                later(1L, () -> {
                    if (p.isOnline()) {
                        crash(r, p, "Dans le mur");
                    }
                });
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && p.getWorld().equals(world) && !builder(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    // ================================================================ commandes

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "join" : a[0].toLowerCase();
        boolean admin = s.hasPermission("eemielytra.admin");
        switch (sub) {
            case "join", "rejoindre" -> {
                if (s instanceof Player p) {
                    join(p);
                }
            }
            case "leave", "quitter" -> {
                if (s instanceof Player p) {
                    Racer r = racers.get(p.getUniqueId());
                    if ((r != null && !r.gone) || spectators.contains(p.getUniqueId())) {
                        leave(p, true);
                    } else {
                        msg(p, "&7Tu n'es pas dans la course.");
                    }
                }
            }
            case "spectate", "regarder" -> {
                if (s instanceof Player p && canEnter(p)) {
                    spectate(p);
                }
            }
            case "record", "records" -> {
                String id = a.length > 1 ? a[1].toLowerCase() : active != null ? active.id : nextCourse;
                Course k = courses.get(id);
                if (k == null) {
                    msg(s, "&cParcours inconnu. &7" + String.join(", ", courses.keySet()));
                    return true;
                }
                msg(s, "&d" + k.name + " &7- record : &6" + fmt(records.getLong(id + ".best.time", -1)) + " &7par &f" + records.getString(id + ".best.name", "personne"));
                List<long[]> list = new ArrayList<>();
                List<String> names = new ArrayList<>();
                org.bukkit.configuration.ConfigurationSection sec = records.getConfigurationSection(id + ".players");
                if (sec != null) {
                    for (String u : sec.getKeys(false)) {
                        list.add(new long[]{sec.getLong(u + ".time"), names.size()});
                        names.add(sec.getString(u + ".name", "?"));
                    }
                }
                list.sort(Comparator.comparingLong(x -> x[0]));
                for (int i = 0; i < Math.min(5, list.size()); i++) {
                    msg(s, "&f" + (i + 1) + ". " + names.get((int) list.get(i)[1]) + " &e" + fmt(list.get(i)[0]));
                }
                if (s instanceof Player p) {
                    msg(s, "&7Ton record : &e" + fmt(records.getLong(id + ".players." + p.getUniqueId() + ".time", -1)));
                }
            }
            case "map", "parcours" -> {
                if (a.length < 2 || !admin) {
                    StringBuilder sb = new StringBuilder();
                    for (Course k : courses.values()) {
                        sb.append("&f").append(k.id).append(" &7(").append(k.name).append(ready.contains(k.id) ? "" : ", en construction").append(") ");
                    }
                    msg(s, "&7Parcours : " + sb + "&7- prochain : &d" + courses.get(nextCourse).name);
                    return true;
                }
                String id = a[1].toLowerCase();
                if (!courses.containsKey(id)) {
                    msg(s, "&cParcours inconnu.");
                } else if (!ready.contains(id)) {
                    msg(s, "&cCe parcours est en construction.");
                } else if (state == State.WAITING || state == State.COUNTDOWN) {
                    nextCourse = id;
                    nextLocked = true;
                    Course k = courses.get(id);
                    for (Racer r : racers.values()) {
                        Player p = Bukkit.getPlayer(r.id);
                        if (p != null) {
                            p.teleport(waitSpawn(k));
                        }
                    }
                    for (UUID sid : spectators) {
                        Player p = Bukkit.getPlayer(sid);
                        if (p != null) {
                            p.teleport(waitSpawn(k).add(4, 8, 0));
                        }
                    }
                    tell("&7Prochain parcours : &d" + k.name);
                    msg(s, "&aProchain parcours : " + k.name);
                } else {
                    getConfig().set("forced-next", id);
                    msg(s, "&aLa prochaine course sera sur " + courses.get(id).name + ".");
                }
            }
            case "start" -> {
                if (!admin) {
                    msg(s, "&cPermission refusée.");
                } else if (state != State.WAITING && state != State.COUNTDOWN) {
                    msg(s, "&cUne course est déjà en cours.");
                } else if (racers.isEmpty()) {
                    msg(s, "&cPersonne n'est inscrit.");
                } else {
                    prestart();
                }
            }
            case "stop" -> {
                if (!admin) {
                    msg(s, "&cPermission refusée.");
                } else {
                    if (state != State.WAITING && state != State.COUNTDOWN) {
                        tell("&cCourse arrêtée par un admin.");
                    }
                    finish();
                    msg(s, "&7Course remise à zéro.");
                }
            }
            case "build" -> {
                if (!admin) {
                    msg(s, "&cPermission refusée.");
                } else if (state != State.WAITING && state != State.COUNTDOWN) {
                    msg(s, "&cImpossible pendant une course.");
                } else {
                    List<String> ids = new ArrayList<>();
                    if (a.length > 1 && courses.containsKey(a[1].toLowerCase())) {
                        ids.add(a[1].toLowerCase());
                    } else {
                        ids.addAll(courses.keySet());
                    }
                    // les inscrits attendent sur une tour qui va etre effacee
                    for (UUID id : new ArrayList<>(all())) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            msg(p, "&7Parcours en reconstruction, retour au lobby.");
                            leave(p, true);
                        }
                    }
                    state = State.WAITING;
                    for (String id : ids) {
                        if (builder != null && builder.c.id.equals(id)) {
                            continue;
                        }
                        ready.remove(id);
                        buildQueue.remove(id);
                        buildQueue.add(id);
                    }
                    msg(s, "&aReconstruction lancée en fond : " + ids + ". Suis la console.");
                    sendStatus();
                }
            }
            case "reload" -> {
                if (!admin) {
                    msg(s, "&cPermission refusée.");
                } else {
                    reloadConfig();
                    msg(s, "&aConfig rechargée.");
                }
            }
            case "info" -> {
                msg(s, "&7État &f" + state + "&7, inscrits &f" + racers.size() + "&7, spectateurs &f" + spectators.size()
                    + "&7, prochain &f" + nextCourse + "&7, prêts &f" + ready
                    + (builder != null ? "&7, construction &f" + builder.c.id + " " + builder.percent() + " %" : "")
                    + (buildQueue.isEmpty() ? "" : "&7, file " + buildQueue));
            }
            default -> {
                msg(s, "&f/elytra join &7- rejoindre | &f/elytra leave &7- quitter | &f/elytra spectate &7- regarder | &f/elytra record [parcours] | /elytra map");
                if (admin) {
                    msg(s, "&c/elytra start | stop | build [parcours] | map <parcours> | reload | info");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>(List.of("join", "leave", "spectate", "record", "map"));
        if (s.hasPermission("eemielytra.admin")) {
            out.addAll(List.of("start", "stop", "build", "reload", "info"));
        }
        if (a.length == 1) {
            out.removeIf(x -> !x.startsWith(a[0].toLowerCase()));
            return out;
        }
        if (a.length == 2 && List.of("record", "records", "map", "build").contains(a[0].toLowerCase())) {
            List<String> ids = new ArrayList<>(courses.keySet());
            ids.removeIf(x -> !x.startsWith(a[1].toLowerCase()));
            return ids;
        }
        return List.of();
    }
}
