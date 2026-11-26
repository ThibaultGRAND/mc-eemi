package fr.eemicraft.resto;

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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Tycoon restaurant d'EEMICRAFT (carte #115). Voir CLAUDE.md, section « Tycoon restaurant ». */
public final class EemiResto extends JavaPlugin implements Listener {
    static final String TAG = "eemiresto";

    World world;
    Geo geo;
    Econ econ;
    boolean textures;
    int maxPlates, maxPlatesTotal;
    double legendaryChance;
    final Random rnd = new Random();

    private final Map<UUID, PData> players = new ConcurrentHashMap<>();
    private UUID[] owners;
    private final Map<UUID, Active> actives = new HashMap<>();
    private final Map<Integer, List<Entity>> statics = new HashMap<>();
    private final Map<Integer, TextDisplay> collectorLabels = new HashMap<>();
    private final Map<UUID, Integer> interactions = new HashMap<>();
    private final Map<Integer, Map<Long, String>> lastPlans = new HashMap<>();
    private final Map<Integer, String> builtSig = new HashMap<>();
    private final Set<Integer> drawing = new HashSet<>();
    private final Set<Integer> redrawAgain = new HashSet<>();
    private final Map<UUID, Long> msgCooldown = new HashMap<>();
    private Placer placer;
    private ExecutorService drawer;
    private NamespacedKey kitKey;
    private File dataFile;
    private int streetVersion;
    private boolean streetReady;
    private boolean spawning;
    private long tickCount;
    private List<PData> top = new ArrayList<>();

    // =================================================================== cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kitKey = new NamespacedKey(this, "kit");
        readConfig();
        placer = new Placer(getLogger());
        drawer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EemiResto-dessin");
            t.setDaemon(true);
            return t;
        });
        dataFile = new File(getDataFolder(), "data.yml");
        loadAll();
        createWorld();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> placer.tick(world, Math.max(1, getConfig().getLong("build-ms-per-tick", 8))), 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::fastTick, 20L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, this::secondTick, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            saveDirty();
            computeTop();
            sendStatus();
        }, 1200L, 1200L);
        Bukkit.getScheduler().runTask(this, this::startup);
        getLogger().info("Tycoon restaurant : " + geo.parcels + " parcelles, " + players.size() + " comptes, " + Defs.PURCHASES.size() + " achats.");
    }

    @Override
    public void onDisable() {
        for (Active a : actives.values()) {
            try {
                a.stop();
            } catch (RuntimeException ex) {
                getLogger().warning("Arret d'un restaurant : " + ex);
            }
        }
        actives.clear();
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
        textures = getConfig().getBoolean("textures", false);
        maxPlates = Math.max(5, getConfig().getInt("plates.max-per-resto", 40));
        maxPlatesTotal = Math.max(maxPlates, getConfig().getInt("plates.max-total", 400));
        legendaryChance = Math.max(0, Math.min(1, getConfig().getDouble("legendary-chance", 0.004)));
        econ = new Econ(getConfig().getDouble("economy.base-wait", 25), getConfig().getDouble("economy.growth", 1.096),
                getConfig().getDouble("economy.rebirth-gain", 0.5), getConfig().getDouble("economy.rebirth-price", 0.25));
        if (geo == null) geo = new Geo(getConfig().getInt("parcels", 16));
    }

    // =================================================================== donnees

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
                    // fichier etranger
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
                    else getLogger().warning("Parcelle " + i + " hors de la rue actuelle (" + geo.parcels + ") : proprietaire " + id + " ignore.");
                } catch (IllegalArgumentException ignored) {
                    // entree invalide
                }
            }
        }
        var bs = y.getConfigurationSection("built");
        if (bs != null) for (String k : bs.getKeys(false)) builtSig.put(Integer.parseInt(k), bs.getString(k));
        // les fichiers joueurs suivent data.yml (source de verite des parcelles)
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
        String name = getConfig().getString("world", "resto");
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
        world.setTime(getConfig().getLong("time", 6000));
        world.setStorm(false);
        world.setSpawnLocation(spawn());
        int[] b = geo.worldBox();
        int n = 0;
        for (int cx = b[0] >> 4; cx <= b[2] >> 4; cx++)
            for (int cz = (-Geo.EDGE - Geo.D - 2) >> 4; cz <= (Geo.EDGE + Geo.D + 2) >> 4; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
                n++;
            }
        getLogger().info("Monde " + name + " pret, " + n + " chunks charges en permanence (rue et parcelles).");
    }

    Location spawn() {
        double[] s = Draw.spawn(geo);
        return new Location(world, s[0], s[1], s[2], (float) s[3], 0f);
    }

    Location parcelEntrance(int i) {
        return new Location(world, geo.wxd(i, 11.5, -1.5), Geo.FLOOR + 1, geo.wzd(i, 11.5, -1.5), geo.yawInto(i), 0f);
    }

    int streetSignature() {
        return Draw.VERSION * 100 + geo.parcels;
    }

    /** Au demarrage : rue (si sa version a change) puis chaque parcelle dont le dessin n'est plus a jour. */
    private void startup() {
        if (world == null) return;
        removeTagged();
        if (streetVersion != streetSignature()) {
            getLogger().info("Construction de la rue (version " + streetSignature() + ")...");
            long seed = getConfig().getLong("seed", 1137);
            drawer.submit(() -> {
                long t0 = System.currentTimeMillis();
                Plan plan = new Plan();
                Draw.street(plan, geo, seed);
                Plan.Compiled c = plan.compile(null, false);
                long ms = System.currentTimeMillis() - t0;
                Bukkit.getScheduler().runTask(this, () -> {
                    getLogger().info("Rue dessinee en " + ms + " ms (" + c.size() + " blocs).");
                    placer.add(new Placer.Job(c, () -> {
                        streetVersion = streetSignature();
                        streetReady = true;
                        builtSig.clear();
                        saveData();
                        for (int i = 0; i < geo.parcels; i++) redraw(i);
                    }, "Rue du resto"));
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
            if (n > 0) getLogger().info(n + " parcelle(s) a redessiner.");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            data(p);
            if (inWorld(p)) enter(p);
        }
    }

    // =================================================================== parcelles

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

    /** Redessine une parcelle (hors du fil principal) puis pose ce qui a change. */
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
                getLogger().warning("Dessin de la parcelle " + i + " : " + ex);
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
                if (redrawAgain.remove(i)) redraw(i);
            }, prev == null ? "Parcelle " + i : null)));
        });
    }

    // =================================================================== entites

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

    ItemDisplay spawnDisplay(Location l, Consumer<ItemDisplay> setup) {
        return spawn(l, ItemDisplay.class, setup);
    }

    TextDisplay text(Location l, String txt, float scale) {
        return spawn(l, TextDisplay.class, t -> {
            t.text(U.c(txt));
            t.setBillboard(Display.Billboard.VERTICAL);
            t.setShadowed(true);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            t.setLineWidth(240);
            t.setViewRange(0.5f);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            t.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /** Texte qui monte puis disparait. */
    void popup(Location l, String txt, int ticks) {
        TextDisplay t = text(l, txt, 0.9f);
        if (t == null) return;
        t.setTeleportDuration(Math.min(59, ticks));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (t.isValid()) t.teleport(l.clone().add(0, 1.2, 0));
        }, 2L);
        Bukkit.getScheduler().runTaskLater(this, t::remove, ticks + 4L);
    }

    void particle(Location l, Particle p, int n) {
        world.spawnParticle(p, l, n, 0.15, 0.1, 0.15, 0);
    }

    Location at(int i, double u, double y, double v) {
        return new Location(world, geo.wxd(i, u, v), y, geo.wzd(i, u, v));
    }

    /** Recree les entites fixes d'une parcelle : arche, caisse, boutons, enseigne. */
    void refreshParcel(int i) {
        if (world == null) return;
        List<Entity> old = statics.remove(i);
        if (old != null) for (Entity e : old) {
            interactions.remove(e.getUniqueId());
            if (e.isValid()) e.remove();
        }
        collectorLabels.remove(i);
        List<Entity> l = new ArrayList<>();
        statics.put(i, l);
        PData d = owner(i);
        int y = Geo.FLOOR;
        if (d == null) {
            add(l, text(at(i, 11.5, y + 5.6, 0.5), "&a&lRÉCLAMER CE RESTAURANT\n&7Passe sous l'arche pour qu'il soit à toi", 1.0f));
            return;
        }
        add(l, text(at(i, 11.5, y + 5.6, 0.5), "&6&lChez " + d.name + (d.rebirths > 0 ? "\n&d✦ Renaissance " + d.rebirths : ""), 1.0f));
        if (d.bought.contains("etoile")) add(l, text(at(i, 11.5, y + 7.4, 0.5), "&e&l★ Étoile du guide ★", 0.9f));
        if (d.bought.contains("enseigne")) add(l, text(at(i, 11.5, y + 4.3, 4.35), "&f&lChez " + d.name, 1.1f));
        if (d.bought.contains("comptoir")) {
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
            if (idx < 0) txt = "&d&lRENAISSANCE\n&7Tout recommencer : gains &a×" + fmtMult(1 + econ.rebirthGain * (d.rebirths + 1));
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
        return "&6&lCAISSE\n&a" + U.eur(d.pending) + "\n&7Marche dessus pour encaisser";
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

    int totalPlates() {
        int n = 0;
        for (Active a : actives.values()) n += a.count();
        return n;
    }

    double beltSpeed(Set<String> b) {
        int t = (b.contains("tapis1") ? 1 : 0) + (b.contains("tapis2") ? 1 : 0) + (b.contains("tapis3") ? 1 : 0);
        return getConfig().getDouble("plates.speed", 1.2) * Math.pow(1.25, t);
    }

    // =================================================================== boucles

    private void fastTick() {
        for (Active a : actives.values()) {
            try {
                a.tick();
            } catch (RuntimeException ex) {
                getLogger().warning("Production de " + a.d.name + " : " + ex);
            }
        }
    }

    private void secondTick() {
        tickCount++;
        long now = System.currentTimeMillis();
        int playMin = Math.max(1, getConfig().getInt("rewards.play-minutes", 30));
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean in = inWorld(p);
            PData d = in ? data(p) : players.get(p.getUniqueId());
            if (d == null) continue;
            // proprietaire present : production reelle ; ailleurs mais connecte : revenu theorique
            if (d.parcel >= 0) {
                Active a = actives.get(p.getUniqueId());
                if (in && a == null) activate(p, d);
                else if (!in && a != null) deactivate(p.getUniqueId());
                if (!in) {
                    double r = econ.rate(d.bought, d.rebirths);
                    d.pending += r;
                    d.earned += r;
                    d.dirty = true;
                }
                if (tickCount % 30 == 0) d.lastSeen = now;
                milestones(p, d);
                updateButtons(d.parcel, d);
                TextDisplay cl = collectorLabels.get(d.parcel);
                if (cl != null && cl.isValid()) cl.text(U.c(collectorText(d)));
            }
            if (in) {
                d.presence++;
                if (d.presence >= playMin * 60L) {
                    d.presence = 0;
                    eemi("eemi reward " + p.getName() + " resto play");
                }
                if (d.parcel >= 0) {
                    p.sendActionBar(U.c("&6" + U.eur(d.money) + " &8| &7caisse &a" + U.eur(d.pending) + " &8| &7" + U.eur(econ.rate(d.bought, d.rebirths)) + "/s"));
                }
            }
        }
        // indice de la dalle secrete : quelques etincelles visibles de pres
        if (world != null && tickCount % 3 == 0) {
            int[] sp = Draw.secretPad(geo);
            Location l = new Location(world, sp[0] + 0.5, Geo.FLOOR + 1.3, sp[1] + 0.5);
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(l) < 100) p.spawnParticle(Particle.WITCH, l, 3, 0.3, 0.2, 0.3, 0);
            }
        }
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
        eemi("eemi status resto playing 0 " + world.getPlayers().size() + " " + geo.parcels);
    }

    boolean inWorld(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    void activate(Player p, PData d) {
        if (d.parcel < 0 || actives.containsKey(p.getUniqueId())) return;
        actives.put(p.getUniqueId(), new Active(this, d));
    }

    void deactivate(UUID id) {
        Active a = actives.remove(id);
        if (a != null) a.stop();
    }

    // =================================================================== regles du jeu

    boolean secretEnabled(PData d, String dish) {
        return switch (dish) {
            case "brownie_mehdi" -> d.magic;
            case "bk_esteban" -> d.bkButton;
            case "saucisse_dylan" -> {
                List<Integer> h = getConfig().getIntegerList("secrets.dylan-hours");
                yield Active.lunch(h.size() > 0 ? h.get(0) : 12, h.size() > 1 ? h.get(1) : 14);
            }
            case "sushi_mathilde" -> d.rebirths >= 1;
            case "ptit_wrap" -> d.sold >= getConfig().getLong("secrets.wrap-sold", 5000);
            default -> false;
        };
    }

    /** Premiere decouverte d'un plat secret : annonce serveur, cadeau EemiCore. */
    void discover(PData d, String dish) {
        if (!d.secrets.add(dish)) return;
        d.dirty = true;
        Defs.Dish ds = Defs.DISHES.get(dish);
        String name = ds == null ? dish : ds.name();
        for (Player o : Bukkit.getOnlinePlayers()) U.msg(o, "&f" + d.name + " &7a découvert un plat secret : &d&l" + name + " &7!");
        int coins = getConfig().getInt("rewards.secret-coins", 25);
        if (coins > 0) eemi("eemi give " + d.name + " coins " + coins);
        Player p = Bukkit.getPlayer(d.uuid);
        if (p != null) {
            p.showTitle(U.title("&6&lLÉGENDAIRE !", "&d" + name));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

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
        activate(p, d);
        p.showTitle(U.title("&6&lRestaurant à toi", "&7Marche sur les boutons pour construire"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        U.msg(p, "&aCe restaurant est à toi ! &7Les plats arrivent au comptoir, encaisse sur la &6dalle d'or&7, puis marche sur les &aboutons verts &7pour construire.");
    }

    void buy(Player p, PData d, int idx) {
        if (!Econ.available(d.bought).contains(idx)) return;
        long price = econ.price(idx, d.rebirths);
        Defs.Purchase pu = Defs.PURCHASES.get(idx);
        if (d.money < price) {
            if (cooldown(p, 2000)) U.msg(p, "&c" + pu.name() + " coûte &e" + U.eur(price) + "&c : il te manque &e" + U.eur(price - d.money) + "&c. Encaisse à la caisse !");
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
            U.msg(p, "&6&lTon restaurant est complet ! &7Une dalle &dRENAISSANCE &7est apparue.");
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
        deactivate(d.uuid);
        d.rebirths++;
        d.rebirthAt = System.currentTimeMillis();
        d.resetRun();
        d.dirty = true;
        d.save(getDataFolder(), getLogger());
        redraw(d.parcel);
        refreshParcel(d.parcel);
        if (inWorld(p)) activate(p, d);
        p.showTitle(U.title("&d&lRenaissance !", "&7Gains ×" + fmtMult(1 + econ.rebirthGain * d.rebirths) + " pour toujours"));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        for (Player o : Bukkit.getOnlinePlayers()) U.msg(o, "&f" + d.name + " &7a fait renaître son restaurant (&d" + d.rebirths + "&7) !");
        eemi("eemi reward " + p.getName() + " resto win");
        // Recompense EemiCore de la renaissance (remplace l'ancienne cle rewards.rebirth-coins, plus lue).
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

    /** Pas du joueur sur une case du monde du resto. */
    void step(Player p, int x, int y, int z) {
        int[] sp = Draw.secretPad(geo);
        if (x == sp[0] && z == sp[1] && y == Geo.FLOOR + 1) {
            PData d = data(p);
            if (d.parcel < 0) {
                if (cooldown(p, 5000)) U.msg(p, "&dUne poudre étrange scintille… &7réclame d'abord un restaurant.");
            } else if (!d.magic) {
                d.magic = true;
                d.dirty = true;
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
                world.spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0);
                U.msg(p, "&dTu trouves une pincée de poudre magique… &7tes fours sentent soudain le chocolat.");
            }
            return;
        }
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
                    if (cooldown(p, 5000)) U.msg(p, "&7Tu as déjà un restaurant (&f/resto&7).");
                } else claim(p, i);
            }
            return;
        }
        if (!o.uuid.equals(p.getUniqueId())) return; // visiteur : rien a acheter ni encaisser
        if (u == Defs.COLLECTOR[0] && v == Defs.COLLECTOR[1] && o.bought.contains("comptoir")) {
            collect(p, o);
            return;
        }
        if (u == Defs.BK_PAD[0] && v == Defs.BK_PAD[1] && o.bought.contains("grill") && !o.bkButton) {
            long price = getConfig().getLong("secrets.bk-price", 5000);
            if (o.money >= price) {
                o.money -= price;
                o.bkButton = true;
                o.dirty = true;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.7f);
                U.msg(p, "&dBouton secret ! &7Ton grill et ta friteuse connaissent maintenant une recette mystère…");
            } else if (cooldown(p, 10000)) {
                U.msg(p, "&8Ce paillasson grince… &7Un bouton secret ? Il faudrait &e" + U.eur(price) + "&7.");
            }
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

    // =================================================================== entree / sortie

    void enter(Player p) {
        if (!(p.isOp() && p.getGameMode() == GameMode.CREATIVE)) p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        PData d = data(p);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || !inWorld(p)) return;
            giveKit(p);
        }, 5L);
        if (d.parcel >= 0) activate(p, d);
        else if (freeParcels() == 0) U.msg(p, "&7Toutes les parcelles sont prises pour l'instant : tu peux visiter les restaurants des autres (&f/resto visiter&7).");
        else U.msg(p, "&7Bienvenue dans la rue des restaurants ! Passe sous une arche &a« Réclamer ce restaurant » &7pour avoir le tien.");
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
        put(p, 8, kit(Material.NETHER_STAR, "&6&lMenu du resto &7(clic droit)", "menu"));
        put(p, 7, kit(Material.BOOK, "&d&lCollection de plats &7(clic droit)", "collection"));
    }

    /** Pose un objet du kit sans jamais ecraser un objet du joueur (deplace ailleurs, sinon kit non donne). */
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
        ItemStack it = U.item(m, name, List.of("&7Objet du Tycoon restaurant"), false);
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
        if (inWorld(p) || p.hasPermission("eemiresto.admin")) return true;
        List<String> from = getConfig().getStringList("join-from");
        return from.isEmpty() || from.contains(p.getWorld().getName());
    }

    void goHome(Player p) {
        if (world == null) {
            U.msg(p, "&cLe resto est fermé.");
            return;
        }
        if (!canJoinFrom(p)) {
            U.msg(p, "&cOn va au resto depuis le lobby (&f/lobby&c).");
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
        U.msg(p, "&7Pendant ton absence, ton restaurant a gagné &a" + U.eur(gain) + " &7(dans ta caisse, &f/resto&7).");
    }

    // =================================================================== menus

    void openMain(Player p) {
        PData d = data(p);
        U.Gui g = new U.Gui(27, "&8Tycoon restaurant");
        int n = 0;
        for (Defs.Purchase pu : Defs.PURCHASES) if (d.bought.contains(pu.id())) n++;
        List<String> lore = new ArrayList<>();
        if (d.parcel < 0) lore.add("&7Pas encore de restaurant : passe sous une arche « Réclamer ».");
        else {
            lore.add("&7Argent : &6" + U.eur(d.money));
            lore.add("&7En caisse : &a" + U.eur(d.pending));
            lore.add("&7Revenu : &f" + U.eur(econ.rate(d.bought, d.rebirths)) + "/s");
            lore.add("&7Construit : &f" + n + "/" + Defs.PURCHASES.size());
            lore.add("&7Renaissances : &d" + d.rebirths + " &7(gains ×" + fmtMult(1 + econ.rebirthGain * d.rebirths) + ")");
            lore.add("&7Plats vendus : &f" + U.fmt(d.sold));
            lore.add("&7Plats secrets : &d" + d.secrets.size() + "/" + Defs.SECRETS.size());
        }
        g.set(10, U.item(Material.EMERALD, "&a&lMon restaurant", lore, false), null);
        g.set(11, U.item(Material.OAK_DOOR, "&eAller à mon restaurant", List.of("&7Ou à la place centrale si tu n'en as pas."), false), c -> goHome(p));
        g.set(13, U.item(Material.BOOK, "&d&lCollection de plats", List.of("&7Les plats et les 5 plats secrets"), false), c -> openCollection(p));
        g.set(14, U.item(Material.SPYGLASS, "&bVisiter un restaurant", List.of("&7Va voir ce que font les autres"), false), c -> openVisit(p));
        g.set(15, U.item(Material.GOLD_INGOT, "&6Classement", List.of("&7Les plus gros chiffres d'affaires"), false), c -> openTop(p));
        g.set(16, U.item(Material.RED_BED, "&cRetour au lobby", List.of(), false), c -> goLobby(p));
        p.openInventory(g.inv);
    }

    void openCollection(Player p) {
        PData d = data(p);
        U.Gui g = new U.Gui(36, "&8Collection de plats");
        int s = 0;
        for (Defs.Dropper dr : Defs.DROPPERS) {
            Defs.Dish ds = Defs.DISHES.get(dr.dish());
            if (d.dishes.contains(dr.dish()) || d.bought.contains(dr.purchase())) {
                g.set(s, U.dish(dr.dish(), textures, List.of("&7Valeur de base : &e" + U.eur(dr.value()), "&7Toutes les " + dr.interval() + " s")), null);
            } else {
                g.set(s, U.item(Material.GRAY_DYE, "&8???", List.of("&7Pas encore cuisiné"), false), null);
            }
            s++;
            if (ds == null) continue;
        }
        int[] slots = {20, 21, 22, 23, 24};
        for (int k = 0; k < Defs.SECRETS.size(); k++) {
            Defs.Secret sc = Defs.SECRETS.get(k);
            if (d.secrets.contains(sc.dish())) {
                g.set(slots[k], U.dish(sc.dish(), textures, List.of("&6Plat légendaire", "&f" + sc.line())), null);
            } else {
                g.set(slots[k], U.item(Material.BLACK_DYE, "&5&l???", List.of("&7Plat secret", "&8Indice : &7" + sc.hint()), false), null);
            }
        }
        g.set(31, U.item(Material.ARROW, "&eRetour", List.of(), false), c -> openMain(p));
        p.openInventory(g.inv);
    }

    void openVisit(Player p) {
        U.Gui g = new U.Gui(54, "&8Visiter un restaurant");
        int s = 0;
        for (int i = 0; i < owners.length && s < 45; i++) {
            PData d = owner(i);
            if (d == null) continue;
            int fi = i;
            ItemStack h = U.item(Material.PLAYER_HEAD, "&6Chez " + d.name, List.of("&7Construit : &f" + d.bought.size() + "/" + Defs.PURCHASES.size(),
                    "&7Renaissances : &d" + d.rebirths, "&7Chiffre d'affaires : &a" + U.eur(d.earned)), false);
            if (h.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(d.uuid));
                h.setItemMeta(sm);
            }
            g.set(s++, h, c -> {
                if (!canJoinFrom(p)) {
                    U.msg(p, "&cOn va au resto depuis le lobby.");
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
        U.Gui g = new U.Gui(27, "&8Classement des restaurants");
        computeTop();
        int[] sl = {4, 12, 13, 14, 20, 21, 22, 23, 24, 11};
        for (int k = 0; k < top.size() && k < sl.length; k++) {
            PData d = top.get(k);
            ItemStack h = U.item(Material.PLAYER_HEAD, (k == 0 ? "&6&l" : "&e") + (k + 1) + ". " + d.name,
                    List.of("&7Chiffre d'affaires : &a" + U.eur(d.earned), "&7Renaissances : &d" + d.rebirths, "&7Plats secrets : &d" + d.secrets.size()), k == 0);
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
        g.set(11, U.item(Material.LIME_CONCRETE, "&a&lRenaître", List.of("&7Ton restaurant repart de zéro (argent et achats).",
                "&7Gains ×" + fmtMult(1 + econ.rebirthGain * (d.rebirths + 1)) + " pour toujours.", "&7Tes plats secrets et ta collection restent."), true), c -> {
            p.closeInventory();
            rebirth(p, d);
        });
        g.set(15, U.item(Material.RED_CONCRETE, "&cPas maintenant", List.of(), false), c -> p.closeInventory());
        p.openInventory(g.inv);
    }

    // =================================================================== evenements

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (world == null || !e.getTo().getWorld().equals(world)) return;
        if (e.getTo().getY() < Geo.ROAD - 14) {
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
        if (e.getFrom().equals(world)) {
            // quitter le monde = quitter le jeu sans toucher a l'inventaire (le joueur est deja ailleurs)
            deactivate(p.getUniqueId());
            sendStatusLater();
        }
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
        deactivate(p.getUniqueId());
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
        Integer i = interactions.get(e.getRightClicked().getUniqueId());
        if (i == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
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
            String id = it.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
            if ("collection".equals(id)) openCollection(p);
            else openMain(p);
            return;
        }
        if (builder(p)) return;
        // portes, portillons et trappes en bois : on doit pouvoir entrer et sortir (sinon on reste bloque dans le resto)
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && openable(e.getClickedBlock())) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    static boolean openable(org.bukkit.block.Block b) {
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
            U.msg(sender, "/resto admin <reload|info|reset|give|secret|acheter|build>");
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
                    U.msg(p, "&c" + args[1] + " n'a pas de restaurant.");
                    return true;
                }
                if (!canJoinFrom(p)) {
                    U.msg(p, "&cOn va au resto depuis le lobby (&f/lobby&c).");
                    return true;
                }
                p.teleport(parcelEntrance(d.parcel));
                U.msg(p, "&7Bienvenue chez &6" + d.name + "&7 !");
            }
            case "top", "classement" -> openTop(p);
            case "collection", "recettes" -> openCollection(p);
            case "lobby", "quitter" -> {
                if (inWorld(p)) goLobby(p);
            }
            default -> U.msg(p, "&7/resto [menu|aller|visiter <joueur>|top|collection|quitter]");
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("eemiresto.admin")) {
            U.msg(s, "&cRéservé aux admins.");
            return true;
        }
        String sub = a.length > 1 ? a[1].toLowerCase(java.util.Locale.ROOT) : "";
        PData t = a.length > 2 ? byName(a[2]) : null;
        switch (sub) {
            case "reload" -> {
                readConfig();
                for (int i = 0; i < geo.parcels; i++) refreshParcel(i);
                U.msg(s, "&aConfiguration rechargée (textures " + (textures ? "oui" : "non") + "). Le nombre de parcelles change au redémarrage.");
            }
            case "info" -> {
                if (t == null) {
                    int claimed = 0;
                    for (UUID u : owners) if (u != null) claimed++;
                    U.msg(s, "&7Parcelles " + claimed + "/" + geo.parcels + ", restaurants actifs " + actives.size() + ", plats affichés " + totalPlates()
                            + ", pose en cours : " + (placer.busy() ? "oui" : "non") + ", états invalides " + placer.invalidCount());
                } else {
                    U.msg(s, "&f" + t.name + " &7: parcelle " + t.parcel + ", argent " + U.eur(t.money) + ", caisse " + U.eur(t.pending) + ", achats " + t.bought.size()
                            + "/" + Defs.PURCHASES.size() + ", renaissances " + t.rebirths + ", vendus " + t.sold + ", secrets " + t.secrets
                            + ", poudre " + t.magic + ", bouton BK " + t.bkButton + ", revenu " + U.eur(econ.rate(t.bought, t.rebirths)) + "/s");
                }
            }
            case "reset" -> {
                if (t == null) {
                    U.msg(s, "&c/resto admin reset <joueur>");
                    return true;
                }
                deactivate(t.uuid);
                int i = t.parcel;
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
                U.msg(s, "&a" + t.name + " remis à zéro" + (i >= 0 ? ", parcelle " + i + " libérée." : "."));
            }
            case "give" -> {
                if (t == null || a.length < 4) {
                    U.msg(s, "&c/resto admin give <joueur> <montant>");
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
            case "secret" -> {
                if (t == null || a.length < 4 || Defs.secret(a[3]) == null) {
                    U.msg(s, "&c/resto admin secret <joueur> <brownie_mehdi|bk_esteban|saucisse_dylan|sushi_mathilde|ptit_wrap>");
                    return true;
                }
                discover(t, a[3]);
                U.msg(s, "&aDécouvert pour " + t.name + ".");
            }
            case "legendaire" -> {
                if (!(s instanceof Player p) || a.length < 3 || Defs.secret(a[2]) == null) {
                    U.msg(s, "&c/resto admin legendaire <id> (en jeu, dans ton restaurant)");
                    return true;
                }
                Active ac = actives.get(p.getUniqueId());
                if (ac == null) {
                    U.msg(s, "&cIl faut être dans le monde du resto avec un restaurant.");
                    return true;
                }
                Defs.Dropper dr = Defs.DROPPERS[0];
                discover(ac.d, a[2]);
                ac.spawnPlate(0, Math.max(0, Defs.pathIndex(Defs.LINE_A, dr.u(), dr.v() - 1)), a[2],
                        dr.value() * econ.legendMult * econ.globalMult(ac.d.bought, ac.d.rebirths), true);
            }
            case "acheter" -> {
                if (t == null || t.parcel < 0 || a.length < 4) {
                    U.msg(s, "&c/resto admin acheter <joueur> <nombre|tout>");
                    return true;
                }
                int n = a[3].equalsIgnoreCase("tout") ? Defs.PURCHASES.size() : Integer.parseInt(a[3]);
                for (int k = 0; k < Defs.PURCHASES.size() && k < n; k++) t.bought.add(Defs.PURCHASES.get(k).id());
                t.dirty = true;
                redraw(t.parcel);
                refreshParcel(t.parcel);
                U.msg(s, "&a" + t.name + " : " + t.bought.size() + " achats.");
            }
            case "build" -> {
                streetVersion = 0;
                saveData();
                U.msg(s, "&eRue et parcelles reconstruites au prochain démarrage (ou &f/resto admin build maintenant&e : relance).");
                if (a.length > 2 && a[2].equalsIgnoreCase("maintenant")) {
                    streetReady = false;
                    startup();
                }
            }
            default -> U.msg(s, "&7/resto admin reload | info [joueur] | reset <joueur> | give <joueur> <montant> | secret <joueur> <id> | legendaire <id> | acheter <joueur> <n|tout> | build [maintenant]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1) {
            for (String x : new String[]{"menu", "aller", "visiter", "top", "collection", "quitter"}) if (x.startsWith(a[0].toLowerCase())) r.add(x);
            if (s.hasPermission("eemiresto.admin") && "admin".startsWith(a[0].toLowerCase())) r.add("admin");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("visiter")) {
            for (int i = 0; i < owners.length; i++) {
                PData d = owner(i);
                if (d != null && d.name.toLowerCase().startsWith(a[1].toLowerCase())) r.add(d.name);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin")) {
            for (String x : new String[]{"reload", "info", "reset", "give", "secret", "legendaire", "acheter", "build"}) if (x.startsWith(a[1].toLowerCase())) r.add(x);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("admin") && a[1].equalsIgnoreCase("legendaire")
                || a.length == 4 && a[0].equalsIgnoreCase("admin") && a[1].equalsIgnoreCase("secret")) {
            for (Defs.Secret sc : Defs.SECRETS) r.add(sc.dish());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("admin")) {
            for (PData d : players.values()) if (d.name.toLowerCase().startsWith(a[2].toLowerCase())) r.add(d.name);
        }
        return r;
    }
}
