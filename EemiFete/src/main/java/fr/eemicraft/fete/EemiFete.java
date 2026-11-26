package fr.eemicraft.fete;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Tycoon Fête foraine d'EEMICRAFT. Voir CONCEPTION.md et CLAUDE.md (section « Tycoon Fête foraine »). */
public final class EemiFete extends JavaPlugin implements Listener {
    static final String TAG = "eemifete";

    World world;
    Geo geo;
    Econ econ;
    long serverTick;
    final Random rnd = new Random();

    private final Map<UUID, PData> players = new ConcurrentHashMap<>();
    private UUID[] owners;
    private final Map<Integer, Show> shows = new HashMap<>();
    private final Map<Integer, Crowd> crowds = new HashMap<>();
    private final Map<Integer, Map<String, Double>> nextRide = new HashMap<>();
    private final Map<Integer, List<Entity>> statics = new HashMap<>();
    private final Map<Integer, TextDisplay> collectorLabels = new HashMap<>();
    private final Map<UUID, Integer> interactions = new HashMap<>();
    private final Map<Integer, Map<Long, String>> lastPlans = new HashMap<>();
    private final Map<Integer, String> builtSig = new HashMap<>();
    private final Set<Integer> drawing = new HashSet<>();
    private final Set<Integer> redrawAgain = new HashSet<>();
    private final Map<UUID, Long> msgCooldown = new HashMap<>();
    private final Map<String, BlockData> blockCache = new HashMap<>();
    private final Set<String> badStates = new HashSet<>();
    private Placer placer;
    private ExecutorService drawer;
    private NamespacedKey kitKey;
    private File dataFile;
    private int streetVersion;
    private boolean streetReady;
    private boolean spawning;
    private long seconds;
    private List<PData> top = new ArrayList<>();

    // =================================================================== cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kitKey = new NamespacedKey(this, "kit");
        readConfig();
        placer = new Placer(getLogger());
        drawer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EemiFete-dessin");
            t.setDaemon(true);
            return t;
        });
        dataFile = new File(getDataFolder(), "data.yml");
        loadAll();
        createWorld();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> placer.tick(world, Math.max(1, getConfig().getLong("build-ms-per-tick", 8))), 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::everyTick, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::secondTick, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            saveDirty();
            computeTop();
            sendStatus();
        }, 1200L, 1200L);
        Bukkit.getScheduler().runTask(this, this::startup);
        getLogger().info("Tycoon fete foraine : " + geo.parcels + " parcs, " + players.size() + " comptes, " + Defs.PURCHASES.size() + " achats.");
    }

    @Override
    public void onDisable() {
        stopAllShows();
        removeAllEntities();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PData d = players.get(p.getUniqueId());
            if (d != null) {
                d.lastSeen = now;
                d.dirty = true;
            }
        }
        saveDirty();
        saveData();
        if (drawer != null) drawer.shutdownNow();
    }

    void readConfig() {
        reloadConfig();
        econ = new Econ(getConfig().getDouble("economy.base-wait", 30), getConfig().getDouble("economy.growth", 1.17),
                getConfig().getDouble("economy.rebirth-gain", 0.5), getConfig().getDouble("economy.rebirth-price", 0.25));
        if (geo == null) geo = new Geo(getConfig().getInt("parcels", 10));
    }

    // =================================================================== données

    private void loadAll() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        streetVersion = y.getInt("street-version", 0);
        owners = new UUID[geo.parcels];
        File dir = new File(getDataFolder(), "players");
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (fs != null) {
            for (File f : fs) {
                try {
                    UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                    players.put(id, PData.load(getDataFolder(), id, getLogger()));
                } catch (IllegalArgumentException ignored) {
                    // fichier étranger
                }
            }
        }
        var ps = y.getConfigurationSection("parcels");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try {
                    int i = Integer.parseInt(k);
                    UUID id = UUID.fromString(ps.getString(k, ""));
                    if (i >= 0 && i < owners.length) owners[i] = id;
                    else getLogger().warning("Parc " + i + " hors de l'allee actuelle (" + geo.parcels + ") : proprietaire " + id + " ignore.");
                } catch (IllegalArgumentException ignored) {
                    // entrée invalide
                }
            }
        }
        var bs = y.getConfigurationSection("built");
        if (bs != null) for (String k : bs.getKeys(false)) builtSig.put(Integer.parseInt(k), bs.getString(k));
        for (PData d : players.values()) {
            int i = d.parcel;
            if (i >= 0 && (i >= owners.length || !d.uuid.equals(owners[i]))) d.parcel = -1;
        }
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] == null) continue;
            PData d = players.computeIfAbsent(owners[i], PData::new);
            d.parcel = i;
            if (d.bought.isEmpty()) d.resetRun();
        }
        computeTop();
    }

    void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("street-version", streetVersion);
        for (int i = 0; i < owners.length; i++) if (owners[i] != null) y.set("parcels." + i, owners[i].toString());
        for (var e : builtSig.entrySet()) y.set("built." + e.getKey(), e.getValue());
        File tmp = new File(dataFile.getPath() + ".tmp");
        try {
            getDataFolder().mkdirs();
            y.save(tmp);
            try {
                java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            getLogger().warning("Sauvegarde de data.yml : " + e.getMessage());
        }
    }

    void saveDirty() {
        for (PData d : players.values()) if (d.dirty) d.save(getDataFolder(), getLogger());
    }

    PData data(Player p) {
        PData d = players.computeIfAbsent(p.getUniqueId(), PData::new);
        if (!p.getName().equals(d.name)) {
            d.name = p.getName();
            d.dirty = true;
        }
        return d;
    }

    PData byName(String name) {
        for (PData d : players.values()) if (d.name.equalsIgnoreCase(name)) return d;
        return null;
    }

    void computeTop() {
        List<PData> l = new ArrayList<>(players.values());
        l.removeIf(d -> d.earned <= 0);
        l.sort((a, b) -> Double.compare(b.earned, a.earned));
        top = l.size() > 10 ? new ArrayList<>(l.subList(0, 10)) : l;
    }

    BlockData blockData(String state) {
        BlockData d = blockCache.get(state);
        if (d != null || blockCache.containsKey(state)) return d;
        try {
            d = Bukkit.createBlockData(state);
        } catch (IllegalArgumentException e) {
            if (badStates.add(state)) getLogger().warning("Bloc invalide ignore (manege) : " + state);
            d = null;
        }
        blockCache.put(state, d);
        return d;
    }

    // =================================================================== monde

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
        String name = getConfig().getString("world", "fete");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
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
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setTime(getConfig().getLong("time", 17000));
        world.setStorm(false);
        world.setSpawnLocation(spawn());
        int[] b = geo.worldBox();
        int n = 0;
        for (int cx = b[0] >> 4; cx <= b[2] >> 4; cx++) {
            for (int cz = (-Geo.EDGE - Geo.D - 2) >> 4; cz <= (Geo.EDGE + Geo.D + 2) >> 4; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
                n++;
            }
        }
        getLogger().info("Monde " + name + " pret, " + n + " chunks charges en permanence (allee et parcs).");
    }

    Location spawn() {
        double[] s = Draw.spawn(geo);
        return new Location(world, s[0], s[1], s[2], (float) s[3], 0f);
    }

    Location parcelEntrance(int i) {
        return new Location(world, geo.wxd(i, 24.0, -1.5), Geo.FLOOR + 1, geo.wzd(i, 24.0, -1.5), geo.yawInto(i), 0f);
    }

    int streetSignature() {
        return Draw.VERSION * 100 + geo.parcels;
    }

    private void startup() {
        if (world == null) return;
        removeTagged();
        if (streetVersion != streetSignature()) {
            getLogger().info("Construction de l'allee foraine (version " + streetSignature() + ")...");
            long seed = getConfig().getLong("seed", 4242);
            drawer.submit(() -> {
                long t0 = System.currentTimeMillis();
                Plan plan = new Plan();
                Draw.street(plan, geo, seed);
                Plan.Compiled c = plan.compile(null, false);
                long ms = System.currentTimeMillis() - t0;
                Bukkit.getScheduler().runTask(this, () -> {
                    getLogger().info("Allee dessinee en " + ms + " ms (" + c.size() + " blocs).");
                    placer.add(new Placer.Job(c, () -> {
                        streetVersion = streetSignature();
                        streetReady = true;
                        builtSig.clear();
                        saveData();
                        for (int i = 0; i < geo.parcels; i++) redraw(i);
                    }, "Allee foraine"));
                });
            });
        } else {
            streetReady = true;
            int n = 0;
            for (int i = 0; i < geo.parcels; i++) {
                if (!sig(i).equals(builtSig.get(i))) {
                    redraw(i);
                    n++;
                } else refreshParcel(i);
            }
            if (n > 0) getLogger().info(n + " parc(s) a redessiner.");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            data(p);
            if (inWorld(p)) enter(p);
        }
    }

    // =================================================================== parcs

    PData owner(int i) {
        return i < 0 || i >= owners.length || owners[i] == null ? null : players.get(owners[i]);
    }

    List<Integer> slots(PData d) {
        if (d == null) return List.of();
        if (Econ.complete(d.bought)) return List.of(-1);
        List<Integer> av = Econ.available(d.bought);
        return av.size() > Defs.SLOTS.length ? av.subList(0, Defs.SLOTS.length) : av;
    }

    Draw.State state(int i) {
        PData d = owner(i);
        if (d == null) return new Draw.State(false, null, List.of(), false);
        boolean complete = Econ.complete(d.bought);
        return new Draw.State(true, new TreeSet<>(d.bought), complete ? List.of() : new ArrayList<>(slots(d)), complete);
    }

    String sig(int i) {
        Draw.State s = state(i);
        return Draw.VERSION + "|" + s.claimed() + "|" + (s.bought() == null ? "" : String.join(",", s.bought())) + "|" + s.slots() + "|" + s.complete();
    }

    void redraw(int i) {
        if (!streetReady || i < 0 || i >= geo.parcels) return;
        if (drawing.contains(i)) {
            redrawAgain.add(i);
            return;
        }
        drawing.add(i);
        Draw.State st = state(i);
        String sg = sig(i);
        Map<Long, String> prev = lastPlans.get(i);
        drawer.submit(() -> {
            Plan.Compiled c;
            try {
                Plan plan = new Plan();
                Draw.parcel(plan, geo, i, st);
                c = plan.compile(prev, true);
            } catch (RuntimeException ex) {
                getLogger().warning("Dessin du parc " + i + " : " + ex);
                ex.printStackTrace();
                Bukkit.getScheduler().runTask(this, () -> drawing.remove(i));
                return;
            }
            Plan.Compiled fc = c;
            Bukkit.getScheduler().runTask(this, () -> placer.add(new Placer.Job(fc, () -> {
                lastPlans.put(i, fc.all());
                builtSig.put(i, sg);
                drawing.remove(i);
                saveData();
                refreshParcel(i);
                restartShow(i);
                if (redrawAgain.remove(i)) redraw(i);
            }, prev == null ? "Parc " + i : null)));
        });
    }

    // =================================================================== entités

    void removeTagged() {
        if (world == null) return;
        for (Entity e : world.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
    }

    private void removeAllEntities() {
        for (List<Entity> l : statics.values()) for (Entity e : l) if (e.isValid()) e.remove();
        statics.clear();
        collectorLabels.clear();
        interactions.clear();
        removeTagged();
    }

    <T extends Entity> T spawn(Location l, Class<T> cls, Consumer<T> setup) {
        spawning = true;
        try {
            T e = world.spawn(l, cls, x -> {
                x.setPersistent(false);
                x.addScoreboardTag(TAG);
                setup.accept(x);
            });
            return e.isValid() ? e : null;
        } catch (RuntimeException ex) {
            getLogger().warning("Apparition impossible : " + ex);
            return null;
        } finally {
            spawning = false;
        }
    }

    TextDisplay text(Location l, String txt, float scale) {
        return spawn(l, TextDisplay.class, t -> {
            t.text(U.c(txt));
            t.setBillboard(Display.Billboard.VERTICAL);
            t.setShadowed(true);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            t.setLineWidth(240);
            t.setViewRange(0.6f);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            t.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /** Texte qui monte puis disparaît. */
    void popup(Location l, String txt, int ticks) {
        TextDisplay t = text(l, txt, 1.1f);
        if (t == null) return;
        t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        t.setTeleportDuration(Math.min(59, ticks));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (t.isValid()) t.teleport(l.clone().add(0, 1.6, 0));
        }, 2L);
        Bukkit.getScheduler().runTaskLater(this, t::remove, ticks + 4L);
    }

    Location at(int i, double u, double y, double v) {
        return new Location(world, geo.wxd(i, u, v), y, geo.wzd(i, u, v));
    }

    /** Recrée les entités fixes d'un parc : arche, caisse, boutons, enseigne. */
    void refreshParcel(int i) {
        if (world == null) return;
        List<Entity> old = statics.remove(i);
        if (old != null) {
            for (Entity e : old) {
                interactions.remove(e.getUniqueId());
                if (e.isValid()) e.remove();
            }
        }
        collectorLabels.remove(i);
        List<Entity> l = new ArrayList<>();
        statics.put(i, l);
        PData d = owner(i);
        int y = Geo.FLOOR;
        double gu = (Defs.GATE_U1 + Defs.GATE_U2 + 1) / 2.0;
        if (d == null) {
            add(l, text(at(i, gu, y + 9.0, 0.5), "&a&lRÉCLAMER CE PARC\n&7Passe sous l'arche pour qu'il soit à toi", 1.1f));
            return;
        }
        add(l, text(at(i, gu, y + 9.0, 0.5), "&6&l" + title(d) + (d.parkName.isEmpty() ? "" : "\n&7de &f" + d.name)
                + (d.rebirths > 0 ? "\n&d✦ Renaissance " + d.rebirths : ""), 1.1f));
        if (d.bought.contains("feux")) add(l, text(at(i, gu, y + 11.6, 0.5), "&e&l★ Parc complet ★", 1.0f));
        if (d.bought.contains("guichet")) {
            Interaction it = spawn(at(i, Defs.COLLECTOR[0] + 0.5, y + 1, Defs.COLLECTOR[1] + 0.5), Interaction.class, e -> {
                e.setInteractionWidth(1.1f);
                e.setInteractionHeight(1.2f);
                e.setResponsive(true);
            });
            if (it != null) {
                l.add(it);
                interactions.put(it.getUniqueId(), i);
            }
            TextDisplay cl = text(at(i, Defs.COLLECTOR[0] + 0.5, y + 2.6, Defs.COLLECTOR[1] + 0.5), collectorText(d), 0.8f);
            if (cl != null) {
                l.add(cl);
                collectorLabels.put(i, cl);
            }
        }
        List<Integer> sl = slots(d);
        for (int k = 0; k < sl.size() && k < Defs.SLOTS.length; k++) {
            int idx = sl.get(k);
            String txt;
            if (idx < 0) txt = "&d&lRENAISSANCE\n&7Tout recommencer : tickets &a×" + fmtMult(1 + econ.rebirthGain * (d.rebirths + 1));
            else {
                Defs.Purchase p = Defs.PURCHASES.get(idx);
                txt = "&f&l" + p.name() + "\n&e" + U.eur(econ.price(idx, d.rebirths)) + "\n&7" + p.desc();
            }
            add(l, text(at(i, Defs.SLOTS[k][0] + 0.5, y + 1.55, Defs.SLOTS[k][1] + 0.5), txt, 0.55f));
        }
        updateButtons(i, d);
    }

    static String fmtMult(double m) {
        return String.format(java.util.Locale.FRANCE, "%.1f", m);
    }

    private static void add(List<Entity> l, Entity e) {
        if (e != null) l.add(e);
    }

    String collectorText(PData d) {
        return "&6&lGUICHET\n&a" + U.eur(d.pending) + "\n&7Marche dessus pour encaisser";
    }

    /** Couleur des boutons : vert si achetable, rouge sinon. */
    void updateButtons(int i, PData d) {
        if (world == null || d == null || drawing.contains(i)) return;
        List<Integer> sl = slots(d);
        for (int k = 0; k < sl.size() && k < Defs.SLOTS.length; k++) {
            int idx = sl.get(k);
            Material want = idx < 0 ? Material.PURPLE_CARPET : d.money >= econ.price(idx, d.rebirths) ? Material.LIME_CARPET : Material.RED_CARPET;
            Block b = world.getBlockAt(geo.wx(i, Defs.SLOTS[k][0], Defs.SLOTS[k][1]), Geo.FLOOR + 1, geo.wz(i, Defs.SLOTS[k][0], Defs.SLOTS[k][1]));
            Material m = b.getType();
            if (m != want && (m == Material.RED_CARPET || m == Material.LIME_CARPET || m == Material.PURPLE_CARPET)) b.setType(want, false);
        }
    }

    // =================================================================== manèges et visiteurs

    /** Parcs à animer : réclamés, posés, avec un joueur à moins de {@code show-distance} blocs. */
    private Set<Integer> wantedShows() {
        Set<Integer> want = new HashSet<>();
        if (world == null || !streetReady) return want;
        double dist = getConfig().getDouble("show-distance", 80);
        double d2 = dist * dist;
        for (int i = 0; i < geo.parcels; i++) {
            if (owner(i) == null || drawing.contains(i) || !builtSig.containsKey(i)) continue;
            Location c = at(i, Geo.W / 2.0, Geo.FLOOR + 1, Geo.D / 2.0);
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(c) < d2) {
                    want.add(i);
                    break;
                }
            }
        }
        return want;
    }

    private void updateShows() {
        Set<Integer> want = wantedShows();
        for (Integer i : new ArrayList<>(shows.keySet())) if (!want.contains(i)) stopShow(i);
        int maxShows = getConfig().getInt("max-shows", 6);
        for (int i : want) {
            if (shows.containsKey(i) || shows.size() >= maxShows) continue;
            startShow(i);
        }
    }

    void startShow(int i) {
        PData d = owner(i);
        if (d == null || world == null) return;
        Set<String> b = new TreeSet<>(d.bought);
        try {
            shows.put(i, new Show(this, i, b));
            if (getConfig().getBoolean("visitors.enabled", true)) crowds.put(i, new Crowd(this, i, b));
        } catch (RuntimeException ex) {
            getLogger().warning("Animation du parc " + i + " : " + ex);
            ex.printStackTrace();
        }
    }

    void stopShow(int i) {
        Show s = shows.remove(i);
        if (s != null) s.stop();
        Crowd c = crowds.remove(i);
        if (c != null) c.stop();
        nextRide.remove(i);
    }

    void restartShow(int i) {
        if (shows.containsKey(i)) {
            stopShow(i);
            startShow(i);
        }
    }

    void stopAllShows() {
        for (Integer i : new ArrayList<>(shows.keySet())) stopShow(i);
    }

    private void everyTick() {
        serverTick++;
        if (serverTick % Show.STEP == 0) {
            for (Show s : shows.values()) {
                try {
                    s.tick(serverTick);
                } catch (RuntimeException ex) {
                    getLogger().warning("Animation du parc " + s.parcel + " : " + ex);
                }
            }
        }
        if (serverTick % 2 == 0) {
            for (Crowd c : crowds.values()) {
                try {
                    c.tick();
                } catch (RuntimeException ex) {
                    getLogger().warning("Visiteurs du parc " + c.parcel + " : " + ex);
                }
            }
        }
    }

    /** « +tickets » au-dessus de chaque attraction à la fin de son tour, feux d'artifice du parc complet. */
    private void ridePopups() {
        double now = serverTick / 20.0;
        for (int i : shows.keySet()) {
            PData d = owner(i);
            if (d == null) continue;
            Map<String, Double> next = nextRide.computeIfAbsent(i, k -> new HashMap<>());
            for (Defs.Attraction a : Defs.ATTRACTIONS) {
                if (!d.bought.contains(a.purchase())) continue;
                double cyc = econ.cycle(d.bought, a);
                Double t = next.get(a.purchase());
                if (t == null) {
                    next.put(a.purchase(), now + rnd.nextDouble() * cyc);
                    continue;
                }
                if (now < t) continue;
                next.put(a.purchase(), t + cyc);
                double[] pp = Defs.POPUP.get(a.purchase());
                if (pp != null) popup(at(i, pp[0], pp[1], pp[2]), "&e+" + U.eur(econ.value(d.bought, d.rebirths, a)), 30);
                d.rides++;
            }
            if (d.bought.contains("feux") && seconds % 20 == i % 20) firework(i);
        }
    }

    void firework(int i) {
        double u = 4 + rnd.nextDouble() * 40, v = 8 + rnd.nextDouble() * 36;
        Location l = at(i, u, Geo.FLOOR + 2, v);
        Color[] cs = {Color.RED, Color.YELLOW, Color.AQUA, Color.FUCHSIA, Color.LIME, Color.ORANGE, Color.WHITE};
        spawn(l, Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.values()[rnd.nextInt(FireworkEffect.Type.values().length)])
                    .withColor(cs[rnd.nextInt(cs.length)], cs[rnd.nextInt(cs.length)]).withFade(cs[rnd.nextInt(cs.length)])
                    .trail(rnd.nextBoolean()).flicker(rnd.nextBoolean()).build());
            m.setPower(1 + rnd.nextInt(2));
            f.setFireworkMeta(m);
        });
    }

    // =================================================================== boucles

    private void secondTick() {
        seconds++;
        long now = System.currentTimeMillis();
        int playMin = Math.max(1, getConfig().getInt("rewards.play-minutes", 30));
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean in = inWorld(p);
            PData d = in ? data(p) : players.get(p.getUniqueId());
            if (d == null) continue;
            if (d.parcel >= 0) {
                // propriétaire connecté (dans le monde ou ailleurs) : revenu plein
                double r = econ.rate(d.bought, d.rebirths);
                d.pending += r;
                d.earned += r;
                d.dirty = true;
                if (seconds % 30 == 0) d.lastSeen = now;
                milestones(p, d);
                updateButtons(d.parcel, d);
                TextDisplay cl = collectorLabels.get(d.parcel);
                if (cl != null && cl.isValid()) cl.text(U.c(collectorText(d)));
            }
            if (in) {
                d.presence++;
                if (d.presence >= playMin * 60L) {
                    d.presence = 0;
                    eemi("eemi reward " + p.getName() + " fete play");
                }
                if (d.parcel >= 0) {
                    p.sendActionBar(U.c("&6" + U.eur(d.money) + " &8| &7guichet &a" + U.eur(d.pending) + " &8| &7" + U.eur(econ.rate(d.bought, d.rebirths)) + "/s"));
                }
            }
        }
        if (seconds % 2 == 0) updateShows();
        ridePopups();
    }

    void milestones(Player p, PData d) {
        List<Integer> ms = getConfig().getIntegerList("rewards.milestones-coins");
        List<Double> th = getConfig().getDoubleList("rewards.milestones-earned");
        for (int k = 0; k < th.size() && k < ms.size(); k++) {
            if (d.earned >= th.get(k) && !d.milestones.contains(k)) {
                d.milestones.add(k);
                d.dirty = true;
                eemi("eemi give " + p.getName() + " coins " + ms.get(k));
                U.msg(p, "&aPalier atteint : &f" + U.eur(th.get(k)) + " &agagnés au total ! &e+" + ms.get(k) + " pièces EEMICRAFT.");
            }
        }
    }

    void eemi(String cmd) {
        if (getServer().getPluginManager().getPlugin("EemiCore") != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    void sendStatus() {
        if (world == null) return;
        eemi("eemi status fete playing 0 " + world.getPlayers().size() + " " + geo.parcels);
    }

    boolean inWorld(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    // =================================================================== règles du jeu

    void claim(Player p, int i) {
        PData d = data(p);
        if (d.parcel >= 0 || owners[i] != null) return;
        owners[i] = p.getUniqueId();
        d.parcel = i;
        if (d.bought.isEmpty()) d.resetRun();
        d.lastSeen = System.currentTimeMillis();
        d.dirty = true;
        d.save(getDataFolder(), getLogger());
        saveData();
        redraw(i);
        refreshParcel(i);
        p.showTitle(U.title("&6&lParc à toi", "&7Marche sur les boutons pour construire"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        U.msg(p, "&aCe parc est à toi ! &7Les attractions rapportent des tickets : encaisse sur la &6dalle d'or &7du guichet, puis marche sur les &aboutons verts &7pour construire.");
        U.msg(p, "&7Donne-lui un nom avec &f/fete nom <nom>&7 (sinon : &6Fête de " + p.getName() + "&7).");
    }

    void buy(Player p, PData d, int idx) {
        if (!Econ.available(d.bought).contains(idx)) return;
        long price = econ.price(idx, d.rebirths);
        Defs.Purchase pu = Defs.PURCHASES.get(idx);
        if (d.money < price) {
            if (cooldown(p, 2000)) U.msg(p, "&c" + pu.name() + " coûte &e" + U.eur(price) + "&c : il te manque &e" + U.eur(price - d.money) + "&c. Encaisse au guichet !");
            return;
        }
        d.money -= price;
        d.bought.add(pu.id());
        d.dirty = true;
        d.save(getDataFolder(), getLogger());
        redraw(d.parcel);
        refreshParcel(d.parcel);
        p.showTitle(U.title("&a&lConstruit !", "&f" + pu.name()));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
        if (Econ.complete(d.bought)) {
            U.msg(p, "&6&lTon parc est complet ! &7Une dalle &dRENAISSANCE &7est apparue.");
            for (Player o : Bukkit.getOnlinePlayers()) U.msg(o, "&f" + d.name + " &7a terminé son parc : &6" + title(d) + " &7!");
        }
    }

    void collect(Player p, PData d) {
        if (d.pending < 0.5) return;
        double v = Math.floor(d.pending);
        d.money += v;
        d.pending -= v;
        d.dirty = true;
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        p.sendActionBar(U.c("&a+" + U.eur(v) + " &7encaissés"));
        updateButtons(d.parcel, d);
        TextDisplay cl = collectorLabels.get(d.parcel);
        if (cl != null && cl.isValid()) cl.text(U.c(collectorText(d)));
    }

    void rebirth(Player p, PData d) {
        if (!Econ.complete(d.bought)) return;
        d.rebirths++;
        d.rebirthAt = System.currentTimeMillis();
        d.resetRun();
        d.dirty = true;
        d.save(getDataFolder(), getLogger());
        redraw(d.parcel);
        refreshParcel(d.parcel);
        p.showTitle(U.title("&d&lRenaissance !", "&7Tickets ×" + fmtMult(1 + econ.rebirthGain * d.rebirths) + " pour toujours"));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        for (Player o : Bukkit.getOnlinePlayers()) U.msg(o, "&f" + d.name + " &7a fait renaître son parc (&d" + d.rebirths + "&7) !");
        eemi("eemi reward " + p.getName() + " fete win");
        int coins = Math.max(0, getConfig().getInt("renaissance.coins", 250));
        int xp = Math.max(0, getConfig().getInt("renaissance.xp", 300));
        if (coins > 0) eemi("eemi give " + p.getName() + " coins " + coins);
        if (xp > 0) eemi("eemi give " + p.getName() + " xp " + xp);
        if (coins > 0 || xp > 0) {
            String parts = (coins > 0 ? "&e+" + coins + " pièces" : "") + (coins > 0 && xp > 0 ? " &7et " : "") + (xp > 0 ? "&b+" + xp + " xp" : "");
            U.msg(p, "&7Récompense de renaissance : " + parts + " &7!");
        }
    }

    boolean cooldown(Player p, long ms) {
        long now = System.currentTimeMillis();
        Long l = msgCooldown.get(p.getUniqueId());
        if (l != null && now - l < ms) return false;
        msgCooldown.put(p.getUniqueId(), now);
        return true;
    }

    /** Pas du joueur sur une case du monde de la fête. */
    void step(Player p, int x, int y, int z) {
        if (y != Geo.FLOOR + 1) return;
        int i = geo.parcelAt(x, z);
        if (i < 0) return;
        int[] uv = geo.local(i, x, z);
        int u = uv[0], v = uv[1];
        PData o = owner(i);
        if (o == null) {
            if (v <= 1 && u >= Defs.GATE_U1 && u <= Defs.GATE_U2) {
                PData d = data(p);
                if (d.parcel >= 0) {
                    if (cooldown(p, 5000)) U.msg(p, "&7Tu as déjà un parc (&f/fete&7).");
                } else claim(p, i);
            }
            return;
        }
        if (!o.uuid.equals(p.getUniqueId())) return; // visiteur : rien à acheter ni encaisser
        if (u == Defs.COLLECTOR[0] && v == Defs.COLLECTOR[1] && o.bought.contains("guichet")) {
            collect(p, o);
            return;
        }
        List<Integer> sl = slots(o);
        for (int k = 0; k < sl.size() && k < Defs.SLOTS.length; k++) {
            if (Defs.SLOTS[k][0] != u || Defs.SLOTS[k][1] != v) continue;
            if (sl.get(k) < 0) openRebirth(p, o);
            else buy(p, o, sl.get(k));
            return;
        }
    }

    // =================================================================== entrée / sortie

    void enter(Player p) {
        if (!(p.isOp() && p.getGameMode() == GameMode.CREATIVE)) p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        PData d = data(p);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || !inWorld(p)) return;
            giveKit(p);
        }, 5L);
        if (d.parcel < 0 && freeParcels() == 0) U.msg(p, "&7Tous les parcs sont pris pour l'instant : tu peux visiter ceux des autres (&f/fete visiter&7).");
        else if (d.parcel < 0) U.msg(p, "&7Bienvenue à la fête foraine ! Passe sous une arche &a« Réclamer ce parc » &7pour avoir le tien.");
        sendStatusLater();
    }

    int freeParcels() {
        int n = 0;
        for (UUID u : owners) if (u == null) n++;
        return n;
    }

    void sendStatusLater() {
        Bukkit.getScheduler().runTaskLater(this, this::sendStatus, 5L);
    }

    boolean isKit(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.STRING);
    }

    void giveKit(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        for (int k = 0; k < c.length; k++) if (isKit(c[k])) p.getInventory().setItem(k, null);
        put(p, 8, kit(Material.NETHER_STAR, "&6&lMenu de la fête &7(clic droit)", "menu"));
    }

    private void put(Player p, int slot, ItemStack kit) {
        var inv = p.getInventory();
        ItemStack cur = inv.getItem(slot);
        if (cur != null && !cur.getType().isAir() && !isKit(cur)) {
            int free = inv.firstEmpty();
            if (free < 0 || free == slot) return;
            inv.setItem(free, cur);
        }
        inv.setItem(slot, kit);
    }

    ItemStack kit(Material m, String name, String id) {
        ItemStack it = U.item(m, name, List.of("&7Objet du Tycoon fête foraine"), false);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    void goLobby(Player p) {
        p.closeInventory();
        if (!p.performCommand(getConfig().getString("lobby-command", "lobby"))) p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
    }

    boolean canJoinFrom(Player p) {
        if (inWorld(p) || p.hasPermission("eemifete.admin")) return true;
        List<String> from = getConfig().getStringList("join-from");
        return from.isEmpty() || from.contains(p.getWorld().getName());
    }

    void goHome(Player p) {
        if (world == null) {
            U.msg(p, "&cLa fête est fermée.");
            return;
        }
        if (!canJoinFrom(p)) {
            U.msg(p, "&cOn va à la fête depuis le lobby (&f/lobby&c).");
            return;
        }
        PData d = data(p);
        p.teleport(d.parcel >= 0 ? parcelEntrance(d.parcel) : spawn());
    }

    void offlineIncome(Player p, PData d) {
        long now = System.currentTimeMillis();
        if (d.parcel < 0 || d.lastSeen <= 0) {
            d.lastSeen = now;
            return;
        }
        long el = (now - d.lastSeen) / 1000L;
        d.lastSeen = now;
        d.dirty = true;
        if (el < 60) return;
        double cap = getConfig().getDouble("offline.max-hours", 6) * 3600;
        double gain = econ.rate(d.bought, d.rebirths) * getConfig().getDouble("offline.factor", 0.4) * Math.min(el, cap);
        if (gain < 1) return;
        d.pending += gain;
        d.earned += gain;
        U.msg(p, "&7Pendant ton absence, ton parc a gagné &a" + U.eur(gain) + " &7(au guichet, &f/fete&7).");
    }

    // =================================================================== menus

    void openMain(Player p) {
        PData d = data(p);
        U.Gui g = new U.Gui(27, "&8Tycoon fête foraine");
        int n = 0;
        for (Defs.Purchase pu : Defs.PURCHASES) if (d.bought.contains(pu.id())) n++;
        List<String> lore = new ArrayList<>();
        if (d.parcel < 0) lore.add("&7Pas encore de parc : passe sous une arche « Réclamer ».");
        else {
            lore.add("&7Tickets : &6" + U.eur(d.money));
            lore.add("&7Au guichet : &a" + U.eur(d.pending));
            lore.add("&7Revenu : &f" + U.eur(econ.rate(d.bought, d.rebirths)) + "/s");
            lore.add("&7Construit : &f" + n + "/" + Defs.PURCHASES.size());
            lore.add("&7Renaissances : &d" + d.rebirths + " &7(tickets ×" + fmtMult(1 + econ.rebirthGain * d.rebirths) + ")");
            lore.add("&7Tours de manège : &f" + U.fmt(d.rides));
        }
        g.set(10, U.item(Material.FIREWORK_ROCKET, "&a&lMon parc", lore, false), null);
        g.set(11, U.item(Material.OAK_DOOR, "&eAller à mon parc", List.of("&7Ou à la place d'entrée si tu n'en as pas."), false), c -> goHome(p));
        g.set(13, U.item(Material.SPYGLASS, "&bVisiter un parc", List.of("&7Va voir ce que font les autres"), false), c -> openVisit(p));
        g.set(15, U.item(Material.GOLD_INGOT, "&6Classement", List.of("&7Les parcs qui ont vendu le plus de tickets"), false), c -> openTop(p));
        g.set(16, U.item(Material.RED_BED, "&cRetour au lobby", List.of(), false), c -> goLobby(p));
        p.openInventory(g.inv);
    }

    void openVisit(Player p) {
        U.Gui g = new U.Gui(54, "&8Visiter un parc");
        int s = 0;
        for (int i = 0; i < owners.length && s < 45; i++) {
            PData d = owner(i);
            if (d == null) continue;
            int fi = i;
            ItemStack h = U.item(Material.PLAYER_HEAD, "&6" + title(d), List.of("&7Forain : &f" + d.name, "&7Construit : &f" + d.bought.size() + "/" + Defs.PURCHASES.size(),
                    "&7Renaissances : &d" + d.rebirths, "&7Tickets vendus : &a" + U.eur(d.earned)), false);
            if (h.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(d.uuid));
                h.setItemMeta(sm);
            }
            g.set(s++, h, c -> {
                if (!canJoinFrom(p)) {
                    U.msg(p, "&cOn va à la fête depuis le lobby.");
                    return;
                }
                p.closeInventory();
                p.teleport(parcelEntrance(fi));
            });
        }
        g.set(49, U.item(Material.ARROW, "&eRetour", List.of(), false), c -> openMain(p));
        p.openInventory(g.inv);
    }

    void openTop(Player p) {
        U.Gui g = new U.Gui(27, "&8Classement des parcs");
        computeTop();
        int[] sl = {4, 12, 13, 14, 20, 21, 22, 23, 24, 11};
        for (int k = 0; k < top.size() && k < sl.length; k++) {
            PData d = top.get(k);
            ItemStack h = U.item(Material.PLAYER_HEAD, (k == 0 ? "&6&l" : "&e") + (k + 1) + ". " + title(d),
                    List.of("&7Forain : &f" + d.name, "&7Tickets vendus : &a" + U.eur(d.earned), "&7Renaissances : &d" + d.rebirths), k == 0);
            if (h.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(d.uuid));
                h.setItemMeta(sm);
            }
            g.set(sl[k], h, null);
        }
        g.set(18, U.item(Material.ARROW, "&eRetour", List.of(), false), c -> openMain(p));
        p.openInventory(g.inv);
    }

    void openRebirth(Player p, PData d) {
        if (!cooldown(p, 1500)) return;
        U.Gui g = new U.Gui(27, "&8Renaissance ?");
        g.set(11, U.item(Material.LIME_CONCRETE, "&a&lRenaître", List.of("&7Ton parc repart de zéro (tickets et achats).",
                "&7Tickets ×" + fmtMult(1 + econ.rebirthGain * (d.rebirths + 1)) + " pour toujours."), true), c -> {
            p.closeInventory();
            rebirth(p, d);
        });
        g.set(15, U.item(Material.RED_CONCRETE, "&cPas maintenant", List.of(), false), c -> p.closeInventory());
        p.openInventory(g.inv);
    }

    // =================================================================== événements

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (world == null || !e.getTo().getWorld().equals(world)) return;
        if (e.getTo().getY() < Geo.FLOOR - 14) {
            Player p = e.getPlayer();
            PData d = data(p);
            p.teleport(d.parcel >= 0 ? parcelEntrance(d.parcel) : spawn());
            return;
        }
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        try {
            step(e.getPlayer(), e.getTo().getBlockX(), e.getTo().getBlockY(), e.getTo().getBlockZ());
        } catch (RuntimeException ex) {
            getLogger().warning("Pas de " + e.getPlayer().getName() + " : " + ex);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (world == null) return;
        if (e.getFrom().equals(world)) sendStatusLater();
        if (inWorld(p)) enter(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PData d = players.get(p.getUniqueId());
        if (d != null) offlineIncome(p, data(p));
        if (inWorld(p)) Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inWorld(p)) enter(p);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        PData d = players.get(p.getUniqueId());
        if (d != null) {
            d.lastSeen = System.currentTimeMillis();
            d.save(getDataFolder(), getLogger());
        }
        msgCooldown.remove(p.getUniqueId());
        sendStatusLater();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent e) {
        if (spawning && world != null && e.getEntity().getWorld().equals(world)) e.setCancelled(false);
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) {
        if (world == null || !e.getWorld().equals(world)) return;
        for (Entity en : e.getEntities()) if (en.getScoreboardTags().contains(TAG)) en.remove();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (world == null || !e.getRightClicked().getWorld().equals(world)) return;
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
        Integer i = interactions.get(e.getRightClicked().getUniqueId());
        if (i == null || e.getHand() != EquipmentSlot.HAND) return;
        PData o = owner(i);
        if (o != null && o.uuid.equals(e.getPlayer().getUniqueId())) collect(e.getPlayer(), o);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAttackEntity(PrePlayerAttackEntityEvent e) {
        if (world == null || !e.getAttacked().getWorld().equals(world)) return;
        if (!e.getAttacked().getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        Integer i = interactions.get(e.getAttacked().getUniqueId());
        PData o = i == null ? null : owner(i);
        if (o != null && o.uuid.equals(e.getPlayer().getUniqueId())) collect(e.getPlayer(), o);
    }

    private boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (world == null || !inWorld(p)) return;
        ItemStack it = e.getItem();
        if (isKit(it) && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            openMain(p);
            return;
        }
        if (builder(p)) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && openable(e.getClickedBlock())) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    static boolean openable(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        if (m == Material.IRON_DOOR || m == Material.IRON_TRAPDOOR) return false;
        return org.bukkit.Tag.DOORS.isTagged(m) || org.bukkit.Tag.FENCE_GATES.isTagged(m) || org.bukkit.Tag.TRAPDOORS.isTagged(m);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof U.Gui g) {
            e.setCancelled(true);
            if (e.getClickedInventory() != g.inv || !(e.getWhoClicked() instanceof Player p)) return;
            var a = g.actions.get(e.getRawSlot());
            if (a == null || e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) return;
            var click = e.getClick();
            Bukkit.getScheduler().runTask(this, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == g) a.accept(click);
            });
            return;
        }
        if (e.getWhoClicked() instanceof Player p && inWorld(p) && !builder(p)
                && (isKit(e.getCurrentItem()) || isKit(e.getCursor()) || (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                && e.getHotbarButton() >= 0 && isKit(p.getInventory().getItem(e.getHotbarButton()))))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof U.Gui) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (inWorld(e.getPlayer()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (world != null && e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (world != null && e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (world != null && e.getBlock().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (world != null && e.getBlock().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (world != null && e.getBlock().getWorld().equals(world)) e.setCancelled(true);
    }

    // =================================================================== commandes

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) return admin(sender, args);
        if (!(sender instanceof Player p)) {
            U.msg(sender, "/fete admin <reload|info|reset|give|acheter|build|nom>");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (!inWorld(p)) goHome(p);
                else openMain(p);
            }
            case "menu" -> openMain(p);
            case "aller", "go", "home" -> goHome(p);
            case "visiter", "visit" -> {
                if (args.length < 2) {
                    openVisit(p);
                    return true;
                }
                PData d = byName(args[1]);
                if (d == null || d.parcel < 0) {
                    U.msg(p, "&c" + args[1] + " n'a pas de parc.");
                    return true;
                }
                if (!canJoinFrom(p)) {
                    U.msg(p, "&cOn va à la fête depuis le lobby (&f/lobby&c).");
                    return true;
                }
                p.teleport(parcelEntrance(d.parcel));
                U.msg(p, "&7Bienvenue à &6" + title(d) + (d.parkName.isEmpty() ? "" : " &7(de &f" + d.name + "&7)") + " &7!");
            }
            case "top", "classement" -> openTop(p);
            case "nom", "renommer", "name" -> rename(p, data(p), args, 1, false);
            case "lobby", "quitter" -> {
                if (inWorld(p)) goLobby(p);
            }
            default -> U.msg(p, "&7/fete [menu|aller|visiter <joueur>|top|nom <nom>|quitter]");
        }
        return true;
    }

    /** Nom affiché d'un parc : son nom choisi, sinon « Fête de pseudo ». */
    static String title(PData d) {
        return d.parkName.isEmpty() ? "Fête de " + d.name : d.parkName;
    }

    static final java.util.regex.Pattern PARK_NAME = java.util.regex.Pattern.compile("[\\p{L}0-9 '’&.!-]+");
    static final String[] PARK_BANNED = {"admin", "modo", "staff", "eemicraft", "serveur", "owner", "op"};

    void rename(CommandSender s, PData d, String[] a, int from, boolean admin) {
        if (d == null) {
            U.msg(s, "&cJoueur inconnu.");
            return;
        }
        if (d.parcel < 0) {
            U.msg(s, admin ? "&c" + d.name + " n'a pas de parc." : "&cRéclame d'abord un parc (&f/fete&c).");
            return;
        }
        if (a.length <= from) {
            U.msg(s, "&7Nom actuel : &6" + title(d) + "&7. Change-le avec &f/fete nom <nom>&7, ou &f/fete nom reset&7.");
            return;
        }
        String raw = String.join(" ", java.util.Arrays.copyOfRange(a, from, a.length)).replaceAll("[&§].?", "").replaceAll("\\s+", " ").trim();
        long now = System.currentTimeMillis();
        if (!admin && now - d.renamedAt < 60_000) {
            U.msg(s, "&cAttends encore &f" + ((60_000 - (now - d.renamedAt)) / 1000 + 1) + " s &cavant de renommer ton parc.");
            return;
        }
        String name;
        if (raw.equalsIgnoreCase("reset") || raw.equalsIgnoreCase("aucun")) {
            name = "";
        } else {
            if (raw.length() < 2 || raw.length() > 24) {
                U.msg(s, "&cLe nom doit faire entre 2 et 24 caractères.");
                return;
            }
            if (!PARK_NAME.matcher(raw).matches()) {
                U.msg(s, "&cLettres, chiffres, espaces et ' & . ! - seulement.");
                return;
            }
            if (!admin) {
                String low = java.text.Normalizer.normalize(raw.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                for (String w : PARK_BANNED) {
                    if (low.matches(".*\\b" + w + "\\b.*")) {
                        U.msg(s, "&cCe nom n'est pas autorisé.");
                        return;
                    }
                }
                for (PData o : players.values()) {
                    if (o != d && o.parkName.equalsIgnoreCase(raw)) {
                        U.msg(s, "&cUn autre parc s'appelle déjà comme ça.");
                        return;
                    }
                }
            }
            name = Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
        d.parkName = name;
        if (!admin) d.renamedAt = now;
        d.dirty = true;
        d.save(getDataFolder(), getLogger());
        refreshParcel(d.parcel);
        U.msg(s, name.isEmpty() ? "&aLe parc reprend son nom : &6" + title(d) + "&a." : "&aLe parc s'appelle maintenant &6" + name + "&a !");
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("eemifete.admin")) {
            U.msg(s, "&cRéservé aux admins.");
            return true;
        }
        String sub = a.length > 1 ? a[1].toLowerCase(java.util.Locale.ROOT) : "";
        PData t = a.length > 2 ? byName(a[2]) : null;
        switch (sub) {
            case "reload" -> {
                readConfig();
                stopAllShows();
                for (int i = 0; i < geo.parcels; i++) refreshParcel(i);
                U.msg(s, "&aConfiguration rechargée. Le nombre de parcs change au redémarrage.");
            }
            case "info" -> {
                if (t == null) {
                    int claimed = 0;
                    for (UUID u : owners) if (u != null) claimed++;
                    int cubes = 0, walkers = 0;
                    for (Show sh : shows.values()) cubes += sh.size();
                    for (Crowd c : crowds.values()) walkers += c.size();
                    U.msg(s, "&7Parcs " + claimed + "/" + geo.parcels + ", animés " + shows.size() + " (" + cubes + " pièces, " + walkers + " visiteurs)"
                            + ", pose en cours : " + (placer.busy() ? "oui" : "non") + ", états invalides " + (placer.invalidCount() + badStates.size()));
                } else {
                    U.msg(s, "&f" + t.name + " &7: parc " + t.parcel + ", tickets " + U.eur(t.money) + ", guichet " + U.eur(t.pending) + ", achats " + t.bought.size()
                            + "/" + Defs.PURCHASES.size() + ", renaissances " + t.rebirths + ", tours " + t.rides + ", revenu " + U.eur(econ.rate(t.bought, t.rebirths)) + "/s");
                }
            }
            case "reset" -> {
                if (t == null) {
                    U.msg(s, "&c/fete admin reset <joueur>");
                    return true;
                }
                int i = t.parcel;
                if (i >= 0) stopShow(i);
                PData fresh = new PData(t.uuid);
                fresh.name = t.name;
                fresh.dirty = true;
                players.put(t.uuid, fresh);
                fresh.save(getDataFolder(), getLogger());
                if (i >= 0 && i < owners.length) {
                    owners[i] = null;
                    saveData();
                    redraw(i);
                    refreshParcel(i);
                }
                U.msg(s, "&a" + t.name + " remis à zéro" + (i >= 0 ? ", parc " + i + " libéré." : "."));
            }
            case "give" -> {
                if (t == null || a.length < 4) {
                    U.msg(s, "&c/fete admin give <joueur> <montant>");
                    return true;
                }
                try {
                    double v = Double.parseDouble(a[3].replace(",", "."));
                    t.money = Math.max(0, t.money + v);
                    t.dirty = true;
                    U.msg(s, "&a" + t.name + " : " + U.eur(t.money));
                } catch (NumberFormatException ex) {
                    U.msg(s, "&cMontant invalide.");
                }
            }
            case "acheter" -> {
                if (t == null || t.parcel < 0 || a.length < 4) {
                    U.msg(s, "&c/fete admin acheter <joueur> <nombre|tout>");
                    return true;
                }
                int n;
                try {
                    n = a[3].equalsIgnoreCase("tout") ? Defs.PURCHASES.size() : Integer.parseInt(a[3]);
                } catch (NumberFormatException ex) {
                    U.msg(s, "&cNombre invalide.");
                    return true;
                }
                for (int k = 0; k < Defs.PURCHASES.size() && k < n; k++) t.bought.add(Defs.PURCHASES.get(k).id());
                t.dirty = true;
                redraw(t.parcel);
                refreshParcel(t.parcel);
                U.msg(s, "&a" + t.name + " : " + t.bought.size() + " achats.");
            }
            case "build" -> {
                streetVersion = 0;
                saveData();
                U.msg(s, "&eAllée et parcs reconstruits au prochain démarrage (ou &f/fete admin build maintenant&e).");
                if (a.length > 2 && a[2].equalsIgnoreCase("maintenant")) {
                    streetReady = false;
                    stopAllShows();
                    startup();
                }
            }
            case "nom" -> rename(s, t, a, 3, true);
            default -> U.msg(s, "&7/fete admin reload | info [joueur] | reset <joueur> | nom <joueur> <nom|reset> | give <joueur> <montant> | acheter <joueur> <n|tout> | build [maintenant]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1) {
            for (String x : new String[]{"menu", "aller", "visiter", "top", "nom", "quitter"}) if (x.startsWith(a[0].toLowerCase())) r.add(x);
            if (s.hasPermission("eemifete.admin") && "admin".startsWith(a[0].toLowerCase())) r.add("admin");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("visiter")) {
            for (int i = 0; i < owners.length; i++) {
                PData d = owner(i);
                if (d != null && d.name.toLowerCase().startsWith(a[1].toLowerCase())) r.add(d.name);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin")) {
            for (String x : new String[]{"reload", "info", "reset", "give", "acheter", "build", "nom"}) if (x.startsWith(a[1].toLowerCase())) r.add(x);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("admin")) {
            for (PData d : players.values()) if (d.name.toLowerCase().startsWith(a[2].toLowerCase())) r.add(d.name);
        }
        return r;
    }
}
