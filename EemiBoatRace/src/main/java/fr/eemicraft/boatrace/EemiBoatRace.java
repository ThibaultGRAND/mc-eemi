/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.entity.TeleportFlag
 *  io.papermc.paper.entity.TeleportFlag$EntityState
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  net.kyori.adventure.title.Title
 *  net.kyori.adventure.title.Title$Times
 *  org.bukkit.Bukkit
 *  org.bukkit.Color
 *  org.bukkit.FireworkEffect
 *  org.bukkit.FireworkEffect$Type
 *  org.bukkit.GameMode
 *  org.bukkit.GameRules
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.WorldCreator
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.block.Block
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Boat
 *  org.bukkit.entity.Display$Billboard
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Firework
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.TextDisplay
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.entity.CreatureSpawnEvent
 *  org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityDismountEvent
 *  org.bukkit.event.entity.EntitySpawnEvent
 *  org.bukkit.event.entity.FoodLevelChangeEvent
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerSwapHandItemsEvent
 *  org.bukkit.event.player.PlayerTeleportEvent
 *  org.bukkit.event.vehicle.VehicleCreateEvent
 *  org.bukkit.event.vehicle.VehicleDamageEvent
 *  org.bukkit.event.vehicle.VehicleDestroyEvent
 *  org.bukkit.event.vehicle.VehicleEnterEvent
 *  org.bukkit.event.vehicle.VehicleExitEvent
 *  org.bukkit.generator.ChunkGenerator
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.FireworkMeta
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.scoreboard.Criteria
 *  org.bukkit.scoreboard.DisplaySlot
 *  org.bukkit.scoreboard.Objective
 *  org.bukkit.scoreboard.Scoreboard
 *  org.bukkit.scoreboard.Team
 *  org.bukkit.util.Vector
 */
package fr.eemicraft.boatrace;

import fr.eemicraft.boatrace.Track;
import fr.eemicraft.boatrace.WorldClone;
import io.papermc.paper.entity.TeleportFlag;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

public final class EemiBoatRace
extends JavaPlugin
implements Listener {
    private static final String PREFIX = "\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r";
    private static final String GAME = "boatrace";
    private NamespacedKey kitKey;
    private NamespacedKey boatKey;
    private Kart kart;
    private Track track;
    private int floorY;
    private int lobbyY;
    private int width;
    private int laps;
    private int checkpoints;
    private double half;
    private double wallOut;
    private final List<int[]> lightPos = new ArrayList<int[]>();
    private World bw;
    private static final Map<String, Circuit> CIRCUITS = new LinkedHashMap<String, Circuit>();
    private final Map<String, Course> courses = new LinkedHashMap<String, Course>();
    private final Set<UUID> exitAllowed = new HashSet<UUID>();
    private String lastStatus;
    private long lastStatusAt;
    private long ticks;
    private YamlConfiguration records;
    /** 2.4.1 : dernier circuit joue (records.yml, cle last-map), exclu du tirage meme apres un redemarrage. */
    private String lastPlayed;
    private final Random random = new Random();
    private final Map<Integer, Game> games = new TreeMap<Integer, Game>();
    private final Map<UUID, Game> byPlayer = new HashMap<UUID, Game>();
    private final Map<UUID, Game> spectating = new HashMap<UUID, Game>();
    private final Map<Integer, List<UUID>> creating = new LinkedHashMap<Integer, List<UUID>>();
    private final Map<Integer, Course> creatingCourse = new HashMap<Integer, Course>();
    private final Map<UUID, Integer> pendingOf = new HashMap<UUID, Integer>();
    private static final Material[] WOOLS;

    public void onEnable() {
        this.kitKey = new NamespacedKey((Plugin)this, "kit");
        this.boatKey = new NamespacedKey((Plugin)this, "boat");
        this.kart = new Kart(this);
        this.saveDefaultConfig();
        this.loadSettings();
        this.loadCourses();
        this.loadRecords();
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        if (!this.courses.isEmpty()) {
            Course c = this.courses.values().iterator().next();
            if (this.getConfig().getBoolean("rotation", true)) {
                ArrayList<Course> all = new ArrayList<Course>(this.courses.values());
                all.removeIf(co -> co.base() == null);
                this.dropLast(all);
                if (!all.isEmpty()) {
                    c = all.get(this.random.nextInt(all.size()));
                }
            }
            this.games.put(1, new Game(1, c.base(), c, false));
        }
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            long delay = 20L;
            for (Course c : this.courses.values()) {
                World w = c.base();
                if (w == null) continue;
                this.build(w, c);
                WorldClone.cleanup((Plugin)this, w, c.worldName, this.maxInstances());
                Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.makeModel(c), delay);
                delay += 40L;
            }
        }, 20L);
        Bukkit.getScheduler().runTask((Plugin)this, this::reportStatus);
    }

    public void onDisable() {
        for (Game g : new ArrayList<Game>(this.games.values())) {
            for (Player p : this.online(g.players)) {
                this.exitAllowed.add(p.getUniqueId());
            }
            g.removeBoats();
            g.clearCages();
            for (Player p : this.online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (!p.getWorld().equals(g.world)) continue;
                this.sendToHub(p);
            }
            if (g.sign != null) {
                g.sign.remove();
            }
            if (!g.copy) continue;
            for (Player p : g.world.getPlayers()) {
                this.sendToHub(p);
            }
            Bukkit.unloadWorld((World)g.world, (boolean)false);
        }
        this.games.clear();
        this.byPlayer.clear();
        this.spectating.clear();
    }

    private int maxInstances() {
        return Math.max(1, this.getConfig().getInt("max-instances", 3));
    }

    private void makeModel(Course c) {
        World base = c.base();
        if (base == null) {
            return;
        }
        World old = Bukkit.getWorld((String)c.modelName());
        c.model = null;
        if (old != null) {
            for (Player p : old.getPlayers()) {
                this.sendToHub(p);
            }
            Bukkit.unloadWorld((World)old, (boolean)false);
        }
        WorldClone.create((Plugin)this, base, c.modelName(), w -> {
            c.model = w;
            if (w == null) {
                this.getLogger().warning("Modele " + c.modelName() + " non cree : pas de copie de " + c.name + ".");
            } else {
                this.getLogger().info("Modele " + c.modelName() + " pret (" + c.name + ").");
            }
        });
    }

    private void loadSettings() {
        this.reloadConfig();
        this.floorY = this.cfg("floor-y", 100);
        this.lobbyY = this.cfg("lobby-y", 140);
        this.width = Math.max(4, this.cfg("track-width", 7));
        this.half = (double)this.width / 2.0;
        this.wallOut = this.half + 1.5;
        this.laps = Math.max(1, this.cfg("laps", 3));
        this.checkpoints = Math.max(4, this.cfg("checkpoints", 16));
        if (this.track == null) {
            this.track = new Track(Math.max(0.6, this.cfgD("track-scale", 1.4)), 1.0);
            this.track.setFloor(this.floorY + 1);
        }
    }

    private void loadCourses() {
        this.courses.clear();
        String first = this.getConfig().getString("circuit", "cosmic").toLowerCase(Locale.ROOT);
        ArrayList<String> ids = new ArrayList<String>();
        if (first.equals("procedural")) {
            ids.add("procedural");
        } else if (this.getConfig().isList("circuits")) {
            if (CIRCUITS.containsKey(first)) {
                ids.add(first);
            }
            for (String s : this.getConfig().getStringList("circuits")) {
                ids.add(s.toLowerCase(Locale.ROOT).trim());
            }
        } else {
            if (CIRCUITS.containsKey(first)) {
                ids.add(first);
            }
            ids.addAll(CIRCUITS.keySet());
        }
        for (String id : ids) {
            Track t;
            World w;
            if (this.courses.containsKey(id)) continue;
            if (id.equals("procedural")) {
                String name = this.getConfig().getString("world", GAME);
                w = Bukkit.getWorld((String)name);
                if (w == null && (w = new WorldCreator(name).generator((ChunkGenerator)new VoidGenerator()).createWorld()) != null) {
                    this.getLogger().info("Monde " + name + " cree (vide).");
                }
                if (w == null) {
                    this.getLogger().severe("Impossible de charger ou creer le monde " + name + ".");
                    continue;
                }
                this.prepareWorld(w, this.lobbyY);
                this.courses.put(id, new Course(id, "Circuit d'EEMICRAFT", name, false, this.track, this.lobbyY, 2, 0, 0));
                continue;
            }
            Circuit c = CIRCUITS.get(id);
            if (c == null) {
                this.getLogger().warning("Circuit inconnu ignore : " + id);
                continue;
            }
            w = Bukkit.getWorld((String)c.world());
            if (w == null) {
                this.getLogger().severe("Monde " + c.world() + " introuvable (a importer avec /mv import " + c.world() + " normal) : circuit " + c.name() + " ignore.");
                continue;
            }
            try {
                t = new Track(this.loadLine(id, c.floor()), 1, c.fx(), c.fz(), 1.0);
            }
            catch (IllegalStateException e) {
                this.getLogger().severe(e.getMessage());
                continue;
            }
            this.prepareWorld(w, c.lobbyY());
            this.courses.put(id, new Course(id, c.name(), c.world(), true, t, c.lobbyY(), Math.max(1, c.gridCols()), c.laps(), c.checkpoints()));
        }
        if (this.courses.isEmpty()) {
            this.getLogger().severe("Aucun circuit utilisable : Mario Barque desactive.");
        } else {
            this.getLogger().info("Circuits : " + String.join((CharSequence)", ", this.courses.keySet()) + ".");
        }
    }

    private double[][] loadLine(String id, int floor) {
        ArrayList<double[]> pts = new ArrayList<double[]>();
        try (InputStream in = this.getResource("tracks/" + id + ".txt");){
            if (in == null) {
                throw new IOException("tracks/" + id + ".txt absent du jar");
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.trim().split("\\s+");
                if (p.length >= 4) {
                    pts.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])});
                    continue;
                }
                if (p.length < 2) continue;
                pts.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), floor, 1.0});
            }
        }
        catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("Ligne du circuit " + id + " illisible : " + e.getMessage(), e);
        }
        return pts.toArray(new double[0][]);
    }

    private void loadRecords() {
        File f = new File(this.getDataFolder(), "records.yml");
        this.records = YamlConfiguration.loadConfiguration((File)f);
        this.lastPlayed = this.records.getString("last-map", null);
    }

    /** Note le circuit qui demarre comme dernier joue (records.yml, cle last-map). */
    private void markPlayed(Course c) {
        this.lastPlayed = c.id;
        this.records.set("last-map", (Object)c.id);
        this.saveRecords();
    }

    /** Retire le dernier circuit joue d'une liste de candidats, sauf s'il est le seul. */
    private void dropLast(List<Course> list) {
        if (list.size() > 1 && this.lastPlayed != null) {
            list.removeIf(co -> co.id.equals(this.lastPlayed));
        }
    }

    /**
     * Tirage uniforme du circuit d'une partie en attente (2.4.1) : circuits libres (monde charge, pris par aucune
     * autre partie) + son circuit actuel, sans le dernier joue ni, si {@code excludeCurrent}, celui qu'elle vient de
     * jouer (sauf s'il ne reste qu'un candidat). Peut rendre le circuit actuel (rien a changer).
     */
    private Course pickCourse(Game g, boolean excludeCurrent) {
        ArrayList<Course> list = new ArrayList<Course>();
        for (Course c : this.courses.values()) {
            World w;
            Game o;
            if (c == g.c) {
                list.add(c);
                continue;
            }
            if ((w = c.base()) == null || (o = this.gameOfWorld(w)) != null && o != g) continue;
            list.add(c);
        }
        if (list.isEmpty()) {
            return g.c;
        }
        this.dropLast(list);
        if (excludeCurrent && list.size() > 1) {
            list.remove(g.c);
        }
        return list.get(this.random.nextInt(list.size()));
    }

    private void saveRecords() {
        try {
            this.records.save(new File(this.getDataFolder(), "records.yml"));
        }
        catch (IOException e) {
            this.getLogger().warning("Impossible d'enregistrer records.yml : " + e.getMessage());
        }
    }

    private void prepareWorld(World world, int lobbyY) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setTime((long)this.cfg("time", 6000));
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnLocation(this.lobbySpawn(world, lobbyY));
    }

    private int cfg(String key, int def) {
        return this.getConfig().getInt(key, def);
    }

    private double cfgD(String key, double def) {
        return this.getConfig().getDouble(key, def);
    }

    private int maxPlayers() {
        return Math.max(1, Math.min(this.cfg("max-players", 12), 24));
    }

    private Location lobbySpawn(World world, int lobbyY) {
        Location l = new Location(world, 0.5, (double)(lobbyY + 1), 0.5);
        l.setYaw(180.0f);
        return l;
    }

    private Location trackLocation(World world, Track t, double s, double lateral) {
        double[] a = t.at(s);
        Location l = new Location(world, a[0] - a[3] * lateral, t.yAt(s), a[1] + a[2] * lateral);
        l.setYaw(Track.yaw(a[2], a[3]));
        l.setPitch(0.0f);
        return l;
    }

    private void set(int x, int y, int z, Material m) {
        this.bw.getBlockAt(x, y, z).setType(m, false);
    }

    private void setData(int x, int y, int z, String data) {
        try {
            this.bw.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData((String)data), false);
        }
        catch (IllegalArgumentException e) {
            this.getLogger().warning("Bloc invalide ignore : " + data);
        }
    }

    private void build(World world, Course co) {
        int bz;
        int bx;
        int along;
        double l;
        Block b;
        int y;
        int z;
        int x;
        int cz;
        int cx;
        this.bw = world;
        Game g1 = this.gameOfWorld(world);
        if (co.imported) {
            for (int cx2 = -1; cx2 <= 0; ++cx2) {
                for (int cz2 = -1; cz2 <= 0; ++cz2) {
                    world.getChunkAt(cx2, cz2).load(true);
                }
            }
            if (g1 != null) {
                g1.removeBoats();
            }
            if (g1 != null) {
                g1.cages.clear();
            }
            this.buildLobby(co.lobbyY);
            world.setSpawnLocation(this.lobbySpawn(world, co.lobbyY));
            if (g1 != null) {
                g1.ensureSign();
            }
            this.getLogger().info(String.format(Locale.ROOT, "Circuit %s pret : %.0f blocs par tour, %d tour(s), sol de y %.0f a %.0f, salle d'attente en y %d.", co.name, co.track.length, co.laps(), co.track.minY(), EemiBoatRace.maxY(co.track), co.lobbyY));
            return;
        }
        long t0 = System.currentTimeMillis();
        int ops = 0;
        int F = this.floorY;
        Track.Grid g = this.track.grid(this.cfg("ground-margin", 14), this.wallOut + 10.0);
        int x0 = g.minX;
        int z0 = g.minZ;
        int x1 = g.minX + g.w - 1;
        int z1 = g.minZ + g.h - 1;
        for (cx = x0 >> 4; cx <= x1 >> 4; ++cx) {
            for (cz = z0 >> 4; cz <= z1 >> 4; ++cz) {
                world.getChunkAt(cx, cz).load(true);
            }
        }
        for (cx = -1; cx <= 0; ++cx) {
            for (cz = -1; cz <= 0; ++cz) {
                world.getChunkAt(cx, cz).load(true);
            }
        }
        if (g1 != null) {
            g1.removeBoats();
            g1.cages.clear();
        }
        this.lightPos.clear();
        for (x = x0; x <= x1; ++x) {
            for (z = z0; z <= z1; ++z) {
                for (y = F - 2; y <= F + 14; ++y) {
                    b = world.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    b.setType(Material.AIR, false);
                    ++ops;
                }
            }
        }
        for (x = -10; x <= 10; ++x) {
            for (z = -10; z <= 10; ++z) {
                for (y = this.lobbyY - 1; y <= this.lobbyY + 7; ++y) {
                    b = world.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    b.setType(Material.AIR, false);
                    ++ops;
                }
            }
        }
        boolean[] used = new boolean[g.w * g.h];
        for (int x2 = x0; x2 <= x1; ++x2) {
            for (int z2 = z0; z2 <= z1; ++z2) {
                float d = g.dist(x2, z2);
                double s = g.pos(x2, z2);
                boolean line = Math.abs(this.track.wrap(s)) < 1.0;
                this.set(x2, F - 1, z2, (double)d <= this.wallOut ? Material.PACKED_ICE : Material.STONE);
                if ((double)d <= this.half) {
                    this.set(x2, F, z2, line && (x2 + z2 & 1) == 0 ? Material.PACKED_ICE : Material.BLUE_ICE);
                    used[x2 - x0 + (z2 - z0) * g.w] = true;
                } else if ((double)d <= this.wallOut) {
                    this.set(x2, F, z2, Material.PACKED_ICE);
                    Material curb = line ? ((x2 + z2 & 1) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE) : (((int)Math.floor(s / 2.0) & 1) == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE);
                    this.set(x2, F + 1, z2, curb);
                    used[x2 - x0 + (z2 - z0) * g.w] = true;
                } else {
                    this.set(x2, F, z2, Material.SNOW_BLOCK);
                }
                ops += 2;
            }
        }
        int sx0 = -this.cfg("stands-half-length", 30);
        int sx1 = this.cfg("stands-half-length", 30);
        for (int x3 = sx0; x3 <= sx1; ++x3) {
            int y2;
            int h;
            int z3;
            int k;
            for (k = 0; k < 6; ++k) {
                z3 = -7 - k;
                h = k + 1;
                for (int y3 = F + 1; y3 < F + h; ++y3) {
                    this.set(x3, y3, z3, Material.SPRUCE_PLANKS);
                }
                this.setData(x3, F + h, z3, "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]");
                this.mark(used, g, x3, z3);
            }
            for (y2 = F + 1; y2 <= F + 9; ++y2) {
                this.set(x3, y2, -13, Math.floorMod(x3, 6) == 0 ? Material.SPRUCE_LOG : Material.SPRUCE_PLANKS);
            }
            this.mark(used, g, x3, -13);
            for (int z4 = -13; z4 <= -9; ++z4) {
                this.set(x3, F + 10, z4, Math.floorMod(x3, 4) < 2 ? Material.RED_WOOL : Material.WHITE_WOOL);
            }
            if (Math.floorMod(x3, 6) == 0) {
                this.setData(x3, F + 11, -13, "minecraft:spruce_fence");
                this.set(x3, F + 12, -13, EemiBoatRace.wool(x3 / 6));
            }
            for (k = 0; k < 3; ++k) {
                z3 = 8 + k;
                h = k + 1;
                for (int y4 = F + 1; y4 < F + h; ++y4) {
                    this.set(x3, y4, z3, Material.SPRUCE_PLANKS);
                }
                this.setData(x3, F + h, z3, "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight]");
                this.mark(used, g, x3, z3);
            }
            for (y2 = F + 1; y2 <= F + 5; ++y2) {
                this.set(x3, y2, 11, Math.floorMod(x3, 6) == 0 ? Material.SPRUCE_LOG : Material.SPRUCE_PLANKS);
            }
            this.mark(used, g, x3, 11);
            if (Math.floorMod(x3, 6) != 3) continue;
            this.setData(x3, F + 6, 11, "minecraft:lantern[hanging=false]");
        }
        double[] a = this.track.at(0.0);
        double nx = -a[3];
        double nz = a[2];
        double span = this.half + 2.5;
        for (double l2 = -span; l2 <= span + 0.01; l2 += 0.5) {
            int y5;
            boolean pillar;
            int bx2 = (int)Math.floor(a[0] + nx * l2);
            int bz2 = (int)Math.floor(a[1] + nz * l2);
            boolean bl = pillar = Math.abs(l2) >= span - 0.01;
            if (pillar) {
                for (y5 = F + 1; y5 <= F + 8; ++y5) {
                    this.setData(bx2, y5, bz2, "minecraft:quartz_pillar[axis=y]");
                }
                this.set(bx2, F + 9, bz2, Material.CHISELED_QUARTZ_BLOCK);
            }
            for (y5 = F + 7; y5 <= F + 8; ++y5) {
                if (pillar) continue;
                this.set(bx2, y5, bz2, (bx2 + bz2 + y5 & 1) == 0 ? Material.BLACK_WOOL : Material.WHITE_WOOL);
            }
            this.mark(used, g, bx2, bz2);
        }
        for (int l3 = -1; l3 <= 1; ++l3) {
            int bx3 = (int)Math.floor(a[0] + nx * (double)l3);
            int bz3 = (int)Math.floor(a[1] + nz * (double)l3);
            world.getBlockAt(bx3, F + 6, bz3).setType(Material.BLACK_CONCRETE, false);
            this.lightPos.add(new int[]{bx3, F + 6, bz3});
        }
        int every = Math.max(4, this.cfg("flag-every", 12));
        int c = 0;
        for (int i = every / 2; i < this.track.n; i += every) {
            double s = (double)i * this.track.step;
            if (Math.abs(this.track.wrap(s)) < 8.0) continue;
            double[] p = this.track.at(s);
            double side = (double)(c % 2 == 0 ? 1 : -1) * (this.half + 3.0);
            int bx4 = (int)Math.floor(p[0] - p[3] * side);
            int bz4 = (int)Math.floor(p[1] + p[2] * side);
            ++c;
            if ((double)g.dist(bx4, bz4) <= this.wallOut + 0.5 || this.isUsed(used, g, bx4, bz4)) continue;
            for (int y6 = F + 1; y6 <= F + 3; ++y6) {
                this.setData(bx4, y6, bz4, "minecraft:spruce_fence");
            }
            Material w = EemiBoatRace.wool(c);
            this.set(bx4, F + 4, bz4, w);
            this.set(bx4, F + 5, bz4, w);
            int fx = bx4 + (int)Math.round(p[2]);
            int fz = bz4 + (int)Math.round(p[3]);
            if ((double)g.dist(fx, fz) > this.wallOut + 0.5 && !this.isUsed(used, g, fx, fz)) {
                this.set(fx, F + 4, fz, w);
                this.set(fx, F + 5, fz, w);
                this.mark(used, g, fx, fz);
            }
            this.mark(used, g, bx4, bz4);
        }
        double sc = this.cfgD("track-scale", 1.4);
        double bridgeS = (double)this.track.nearestGlobal(-20.0 * sc + 0.5, 28.0 * sc + 0.5) * this.track.step;
        double[] b2 = this.track.at(bridgeS);
        double bnx = -b2[3];
        double bnz = b2[2];
        double bspan = this.half + 4.0;
        for (l = -bspan; l <= bspan + 0.01; l += 0.5) {
            for (along = -1; along <= 1; ++along) {
                bx = (int)Math.floor(b2[0] + bnx * l + b2[2] * (double)along);
                bz = (int)Math.floor(b2[1] + bnz * l + b2[3] * (double)along);
                this.set(bx, F + 6, bz, Material.SPRUCE_PLANKS);
                this.mark(used, g, bx, bz);
            }
        }
        for (l = -bspan; l <= bspan + 0.01; l += 0.5) {
            for (along = -2; along <= 2; along += 4) {
                bx = (int)Math.floor(b2[0] + bnx * l + b2[2] * (double)along);
                bz = (int)Math.floor(b2[1] + bnz * l + b2[3] * (double)along);
                this.setData(bx, F + 6, bz, "minecraft:spruce_slab[type=bottom]");
                this.setData(bx, F + 7, bz, "minecraft:spruce_fence");
            }
        }
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            for (int along2 = -1; along2 <= 1; along2 += 2) {
                int bx5 = (int)Math.floor(b2[0] + bnx * (double)sgn * bspan + b2[2] * (double)along2);
                int bz5 = (int)Math.floor(b2[1] + bnz * (double)sgn * bspan + b2[3] * (double)along2);
                for (int y7 = F + 1; y7 <= F + 5; ++y7) {
                    this.set(bx5, y7, bz5, Material.STONE_BRICKS);
                }
                this.setData(bx5, F + 8, bz5, "minecraft:lantern[hanging=false]");
                this.mark(used, g, bx5, bz5);
            }
        }
        Random r = new Random(1234L);
        double chance = this.cfgD("tree-chance", 0.006);
        for (int x4 = x0 + 2; x4 <= x1 - 2; ++x4) {
            for (int z5 = z0 + 2; z5 <= z1 - 2; ++z5) {
                int dz;
                int dx;
                if (r.nextDouble() >= chance || (double)g.dist(x4, z5) < this.half + 9.0) continue;
                boolean free = true;
                for (dx = -2; dx <= 2 && free; ++dx) {
                    for (dz = -2; dz <= 2 && free; ++dz) {
                        if (!this.isUsed(used, g, x4 + dx, z5 + dz)) continue;
                        free = false;
                    }
                }
                if (!free || x4 >= sx0 - 3 && x4 <= sx1 + 3 && z5 >= -16 && z5 <= 14) continue;
                this.tree(x4, z5, 5 + r.nextInt(3));
                for (dx = -2; dx <= 2; ++dx) {
                    for (dz = -2; dz <= 2; ++dz) {
                        this.mark(used, g, x4 + dx, z5 + dz);
                    }
                }
            }
        }
        this.buildLobby(this.lobbyY);
        world.setSpawnLocation(this.lobbySpawn(world, this.lobbyY));
        if (g1 != null) {
            g1.ensureSign();
            g1.removeBoats();
        }
        this.getLogger().info(String.format(Locale.ROOT, "Circuit construit : %.0f blocs par tour, piste de %d de large, %d x %d blocs (%d a %d, %d a %d), %d operations en %d ms.", this.track.length, this.width, g.w, g.h, x0, x1, z0, z1, ops, System.currentTimeMillis() - t0));
    }

    private void mark(boolean[] used, Track.Grid g, int x, int z) {
        if (g.in(x, z)) {
            used[x - g.minX + (z - g.minZ) * g.w] = true;
        }
    }

    private boolean isUsed(boolean[] used, Track.Grid g, int x, int z) {
        return !g.in(x, z) || used[x - g.minX + (z - g.minZ) * g.w];
    }

    private static Material wool(int i) {
        return WOOLS[Math.floorMod(i, WOOLS.length)];
    }

    private void tree(int x, int z, int height) {
        int F = this.floorY;
        for (int y = F + 1; y <= F + height; ++y) {
            this.set(x, y, z, Material.SPRUCE_LOG);
        }
        String leaves = "minecraft:spruce_leaves[persistent=true]";
        for (int y = F + 2; y <= F + height + 1; ++y) {
            int fromTop = F + height + 1 - y;
            int rad = fromTop == 0 ? 0 : (fromTop % 2 == 1 ? 1 : 2);
            if (y == F + 2) {
                rad = 2;
            }
            for (int dx = -rad; dx <= rad; ++dx) {
                for (int dz = -rad; dz <= rad; ++dz) {
                    if (dx == 0 && dz == 0 && y <= F + height || rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2 || rad == 1 && Math.abs(dx) == 1 && Math.abs(dz) == 1 || !this.bw.getBlockAt(x + dx, y, z + dz).getType().isAir()) continue;
                    this.setData(x + dx, y, z + dz, leaves);
                }
            }
        }
        this.set(x, F + height + 2, z, Material.SNOW_BLOCK);
    }

    private static double maxY(Track t) {
        double m = -1.7976931348623157E308;
        for (double v : t.y) {
            m = Math.max(m, v);
        }
        return m;
    }

    private void buildLobby(int L) {
        for (int x = -8; x <= 7; ++x) {
            for (int z = -8; z <= 7; ++z) {
                int d = Math.max(x < 0 ? -x - 1 : x, z < 0 ? -z - 1 : z);
                if (d <= 2) {
                    this.set(x, L, z, Material.GLASS);
                } else if (d <= 6) {
                    this.set(x, L, z, (x + z & 1) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE);
                } else {
                    this.set(x, L, z, Material.PACKED_ICE);
                    this.set(x, L + 1, z, Material.BLUE_ICE);
                    for (int y = L + 2; y <= L + 4; ++y) {
                        this.set(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                    }
                    this.set(x, L + 5, z, Material.PACKED_ICE);
                }
                this.set(x, L + 6, z, d <= 6 && Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? Material.SEA_LANTERN : Material.PACKED_ICE);
            }
        }
    }

    NamespacedKey boatKeyForKart() {
        return this.boatKey;
    }

    boolean isOurBoat(Entity e) {
        return e instanceof Boat && e.getPersistentDataContainer().has(this.boatKey, PersistentDataType.STRING);
    }

    Entity boatOf(Racer r) {
        if (r.boat == null) {
            return null;
        }
        Entity e = Bukkit.getEntity((UUID)r.boat);
        return e != null && e.isValid() ? e : null;
    }

    private void removeBoat(Racer r, Player p) {
        Entity e;
        this.exitAllowed.add(r.id);
        if (p != null && p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        if ((e = this.boatOf(r)) != null) {
            e.eject();
            e.remove();
        }
        r.boat = null;
    }

    private void tick() {
        ++this.ticks;
        for (Game g : new ArrayList<Game>(this.games.values())) {
            if (g.destroyed) continue;
            g.tick();
            if (this.ticks % 20L == 0L && !g.destroyed) {
                g.second();
            }
            if (this.ticks % 5L != 0L || g.destroyed) continue;
            g.safety();
        }
        if (this.ticks % 20L == 0L) {
            this.reportStatus();
        }
    }

    private Game gameOfWorld(World w) {
        if (w == null) {
            return null;
        }
        for (Game g : this.games.values()) {
            if (g.destroyed || !g.world.equals(w)) continue;
            return g;
        }
        return null;
    }

    private boolean isGameWorld(World w) {
        if (w == null) {
            return false;
        }
        if (this.gameOfWorld(w) != null) {
            return true;
        }
        for (Course c : this.courses.values()) {
            if (!c.owns(w.getName())) continue;
            return true;
        }
        return false;
    }

    private Course freeCourse(Course except) {
        ArrayList<Course> list = new ArrayList<Course>();
        for (Course c : this.courses.values()) {
            World w;
            if (c == except || (w = c.base()) == null || this.gameOfWorld(w) != null) continue;
            list.add(c);
        }
        this.dropLast(list);
        return list.isEmpty() ? null : (Course)list.get(this.random.nextInt(list.size()));
    }

    private boolean racing(UUID id) {
        Game g = this.byPlayer.get(id);
        return g != null && g.racing(id);
    }

    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (this.byPlayer.containsKey(id) || this.pendingOf.containsKey(id)) {
            p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Tu es deja inscrit.");
            return;
        }
        if (this.games.get(1) == null) {
            p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cLe monde de la course n'est pas charge.");
            return;
        }
        int max = this.maxPlayers();
        Game best = null;
        for (Game game : this.games.values()) {
            if (game.destroyed || game.state != State.WAITING && game.state != State.STARTING || game.players.size() >= max || best != null && game.players.size() <= best.players.size()) continue;
            best = game;
        }
        if (best != null) {
            best.add(p);
            return;
        }
        for (Map.Entry entry : this.creating.entrySet()) {
            if (((List)entry.getValue()).size() >= max) continue;
            ((List)entry.getValue()).add(id);
            this.pendingOf.put(id, (Integer)entry.getKey());
            p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Preparation d'une nouvelle partie...");
            this.reportStatus();
            return;
        }
        if (this.games.size() + this.creating.size() >= this.maxInstances()) {
            p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cToutes les parties sont pleines.");
            return;
        }
        int n = 2;
        while (this.games.containsKey(n) || this.creating.containsKey(n)) {
            ++n;
        }
        Course course = this.freeCourse(null);
        if (course != null) {
            Game g = new Game(n, course.base(), course, false);
            this.games.put(n, g);
            this.prepareWorld(g.world, g.lobbyY);
            for (Chunk0 c0 : Chunk0.ALL) {
                g.world.getChunkAt(c0.x, c0.z).load(true);
            }
            g.add(p);
            this.reportStatus();
            return;
        }
        ArrayList<Course> ready = new ArrayList<Course>();
        for (Course c : this.courses.values()) {
            if (c.model == null) continue;
            ready.add(c);
        }
        if (ready.isEmpty()) {
            p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cLes parties ne sont pas encore pretes, reessaie dans un instant.");
            return;
        }
        this.dropLast(ready);
        Course cc = (Course)ready.get(this.random.nextInt(ready.size()));
        ArrayList<UUID> list = new ArrayList<UUID>();
        list.add(id);
        this.creating.put(n, list);
        this.creatingCourse.put(n, cc);
        this.pendingOf.put(id, n);
        p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Preparation d'une nouvelle partie...");
        this.reportStatus();
        int num = n;
        WorldClone.create((Plugin)this, cc.model, WorldClone.name(cc.worldName, num), w -> {
            this.creatingCourse.remove(num);
            List<UUID> waiting = this.creating.remove(num);
            if (waiting == null) {
                waiting = List.of();
            }
            if (w == null || !this.isEnabled()) {
                for (UUID u : waiting) {
                    Player pl;
                    if (!this.pendingOf.remove(u, num) || (pl = Bukkit.getPlayer((UUID)u)) == null) continue;
                    pl.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cImpossible de preparer une nouvelle partie.");
                }
                this.reportStatus();
                return;
            }
            Game g = new Game(num, (World)w, cc, true);
            this.games.put(num, g);
            this.prepareWorld((World)w, g.lobbyY);
            for (Chunk0 c0 : Chunk0.ALL) {
                w.getChunkAt(c0.x, c0.z).load(true);
            }
            for (UUID u : waiting) {
                Player pl;
                if (!this.pendingOf.remove(u, num) || (pl = Bukkit.getPlayer((UUID)u)) == null || g.players.size() >= max) continue;
                g.add(pl);
            }
            if (g.players.isEmpty()) {
                this.destroyGame(g);
            }
            this.reportStatus();
        });
    }

    private void destroyGame(Game g) {
        if (g.n == 1 || g.destroyed) {
            return;
        }
        for (UUID id : g.players) {
            this.exitAllowed.add(id);
        }
        g.removeBoats();
        g.clearCages();
        if (g.sign != null) {
            g.sign.remove();
        }
        g.destroyed = true;
        this.games.remove(g.n);
        for (Player p : this.online(g.spectators)) {
            this.spectating.remove(p.getUniqueId(), g);
            this.sendToHub(p);
        }
        for (UUID id : g.spectators) {
            this.spectating.remove(id, g);
        }
        g.spectators.clear();
        for (Player p : this.online(g.players)) {
            this.byPlayer.remove(p.getUniqueId(), g);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            this.sendToHub(p);
        }
        for (UUID id : g.players) {
            this.byPlayer.remove(id, g);
        }
        g.players.clear();
        g.racers.clear();
        if (g.copy) {
            WorldClone.destroy((Plugin)this, g.world, g.c.worldName, this::sendToHub);
            this.getLogger().info("Partie " + g.n + " terminee, copie " + g.world.getName() + " supprimee.");
        } else {
            this.getLogger().info("Partie " + g.n + " terminee (" + g.c.name + ").");
        }
        this.reportStatus();
    }

    private boolean cancelPending(UUID id) {
        Integer n = this.pendingOf.remove(id);
        if (n == null) {
            return false;
        }
        List<UUID> l = this.creating.get(n);
        if (l != null) {
            l.remove(id);
        }
        this.reportStatus();
        return true;
    }

    private List<EntityType> boatTypes() {
        ArrayList<EntityType> out = new ArrayList<EntityType>();
        for (String s : this.getConfig().getStringList("boat-types")) {
            try {
                EntityType t = EntityType.valueOf((String)s.trim().toUpperCase(Locale.ROOT));
                if (t.getEntityClass() != null && Boat.class.isAssignableFrom(t.getEntityClass())) {
                    out.add(t);
                    continue;
                }
                this.getLogger().warning("boat-types : " + s + " n'est pas un bateau.");
            }
            catch (IllegalArgumentException e) {
                this.getLogger().warning("boat-types : type inconnu " + s);
            }
        }
        if (out.isEmpty()) {
            out.add(EntityType.OAK_BOAT);
        }
        return out;
    }

    private void firework(Location l) {
        Firework fw = (Firework)l.getWorld().spawn(l, Firework.class);
        FireworkMeta m = fw.getFireworkMeta();
        m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(new Color[]{Color.AQUA, Color.WHITE}).withFade(Color.BLUE).flicker(true).build());
        m.setPower(1);
        fw.setFireworkMeta(m);
    }

    private void sendToHub(Player p) {
        Game sg = this.spectating.remove(p.getUniqueId());
        if (sg != null) {
            sg.spectators.remove(p.getUniqueId());
        }
        if (!this.mayTouch(p)) {
            return;
        }
        this.exitAllowed.add(p.getUniqueId());
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        this.resetPlayer(p, GameMode.SURVIVAL);
        World hub = this.hubWorld();
        if (hub != null) {
            p.teleport(hub.getSpawnLocation());
        }
    }

    private World hubWorld() {
        return Bukkit.getWorld((String)this.getConfig().getString("hub-world", "lobby"));
    }

    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        if (this.isGameWorld(w)) {
            return true;
        }
        World hub = this.hubWorld();
        return hub != null && w.equals(hub);
    }

    private void resetPlayer(Player p, GameMode mode) {
        if (!this.mayTouch(p)) {
            return;
        }
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20.0 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5.0f);
        p.setLevel(0);
        p.setExp(0.0f);
        p.setFireTicks(0);
        p.setFallDistance(0.0f);
    }

    private void spectate(Player p, String targetName) {
        Game old;
        if (this.byPlayer.containsKey(p.getUniqueId()) || this.pendingOf.containsKey(p.getUniqueId())) {
            p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cTu es inscrit : \u00a7f/boatrace leave \u00a7cd'abord.");
            return;
        }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact((String)targetName);
            if (t == null) {
                p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cJoueur introuvable.");
                return;
            }
            g = this.byPlayer.get(t.getUniqueId());
            if (g == null || g.state != State.PLAYING) {
                p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7c" + t.getName() + " n'est pas dans une course en cours.");
                return;
            }
        } else {
            for (Game c : this.games.values()) {
                if (c.destroyed || c.state != State.PLAYING || g != null && c.racers.size() <= g.racers.size()) continue;
                g = c;
            }
            if (g == null) {
                p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cAucune course en cours.");
                return;
            }
        }
        if ((old = this.spectating.remove(p.getUniqueId())) != null) {
            old.spectators.remove(p.getUniqueId());
        }
        g.addSpectator(p);
    }

    private Component recordLine(Game g) {
        String key = g.c.recordKey() + ".race-" + g.laps;
        long t = this.records.getLong(key + ".time", -1L);
        if (t < 0L) {
            return Component.text((String)"Record : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)"aucun", (TextColor)NamedTextColor.DARK_GRAY));
        }
        return ((TextComponent)Component.text((String)"Record : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)EemiBoatRace.time(t), (TextColor)NamedTextColor.LIGHT_PURPLE))).append((Component)Component.text((String)(" " + this.records.getString(key + ".player", "?")), (TextColor)NamedTextColor.WHITE));
    }

    private ItemStack named(ItemStack it, String name, NamedTextColor color) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text((String)name, (TextColor)color).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack kit(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(this.kitKey, PersistentDataType.BYTE, (byte)1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(this.kitKey, PersistentDataType.BYTE);
    }

    private static String ordinal(int n) {
        return n == 1 ? "er" : "e";
    }

    private static String time(long ms) {
        if (ms < 0L) {
            ms = 0L;
        }
        return String.format(Locale.ROOT, "%d:%02d.%03d", ms / 60000L, ms / 1000L % 60L, ms % 1000L);
    }

    private boolean inGameWorld(Player p) {
        return this.isGameWorld(p.getWorld());
    }

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemiboatrace.admin");
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (this.isOurBoat(e.getEntity())) {
            e.setCancelled(false);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onVehicleCreate(VehicleCreateEvent e) {
        if (this.isOurBoat((Entity)e.getVehicle())) {
            e.setCancelled(false);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onVehicleDamage(VehicleDamageEvent e) {
        if (this.isOurBoat((Entity)e.getVehicle())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        if (this.isOurBoat((Entity)e.getVehicle())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (!this.isOurBoat((Entity)e.getVehicle())) {
            return;
        }
        String owner = (String)e.getVehicle().getPersistentDataContainer().get(this.boatKey, PersistentDataType.STRING);
        if (owner == null || !owner.equals(e.getEntered().getUniqueId().toString())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onVehicleExit(VehicleExitEvent e) {
        UUID id = e.getExited().getUniqueId();
        if (this.racing(id) && !this.exitAllowed.contains(id) && e.isCancellable()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onDismount(EntityDismountEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (this.racing(id) && !this.exitAllowed.contains(id) && e.isCancellable()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Game g = this.byPlayer.get(id);
        if (g == null || !g.racers.containsKey(id)) {
            return;
        }
        String cmd = e.getMessage().toLowerCase();
        if (cmd.startsWith("/boatrace") || cmd.startsWith("/course") || cmd.startsWith("/br ") || cmd.equals("/br")) {
            return;
        }
        this.exitAllowed.add(id);
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            Player p = Bukkit.getPlayer((UUID)id);
            if (p != null && !g.destroyed && p.getWorld().equals(g.world) && g.racers.containsKey(id)) {
                this.exitAllowed.remove(id);
            }
        });
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onTeleport(PlayerTeleportEvent e) {
        Game g = this.byPlayer.get(e.getPlayer().getUniqueId());
        if (g != null && g.racers.containsKey(e.getPlayer().getUniqueId()) && e.getTo().getWorld() != null && !e.getTo().getWorld().equals(g.world)) {
            this.exitAllowed.add(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        Player p;
        Entity entity = e.getEntity();
        if (entity instanceof Player && (this.inGameWorld(p = (Player)entity) || this.byPlayer.containsKey(p.getUniqueId()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        Player p;
        HumanEntity humanEntity = e.getEntity();
        if (humanEntity instanceof Player && (this.inGameWorld(p = (Player)humanEntity) || this.byPlayer.containsKey(p.getUniqueId()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (this.isGameWorld(e.getBlock().getWorld()) && !this.admin(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (this.isGameWorld(e.getBlock().getWorld()) && !this.admin(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (this.isGameWorld(e.getLocation().getWorld()) && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Game g;
        Player p = e.getPlayer();
        if (this.inGameWorld(p) && !this.admin(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
        }
        if ((g = this.byPlayer.get(p.getUniqueId())) == null || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack it = e.getItem();
        if (this.kart.isKartItem(it)) {
            e.setCancelled(true);
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                this.kart.use(g, p);
            }
            return;
        }
        if (it == null || !this.isKit(it)) {
            return;
        }
        e.setCancelled(true);
        if (it.getType() == Material.RED_BED && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            g.leave(p, true);
            p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Tu as quitte la course.");
        } else if (it.getType() == Material.RECOVERY_COMPASS && e.getAction() != Action.PHYSICAL) {
            g.askRespawn(p);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = this.byPlayer.get(e.getPlayer().getUniqueId());
        if (g == null) {
            return;
        }
        e.setCancelled(true);
        g.askRespawn(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Game kg = this.byPlayer.get(e.getPlayer().getUniqueId());
        if (kg != null && this.kart.isKartItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            Player kp = e.getPlayer();
            Bukkit.getScheduler().runTask((Plugin)this, () -> this.kart.use(kg, kp));
            return;
        }
        if (this.byPlayer.containsKey(e.getPlayer().getUniqueId()) || this.isKit(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Game g = this.gameOfWorld(e.getEntity().getWorld());
        int res = g == null ? 0 : this.kart.hit(g, e.getEntity(), e.getHitEntity());
        if (res > 0) {
            e.setCancelled(true);
        }
        if (res == 2) {
            e.getEntity().remove();
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player && this.byPlayer.containsKey((p = (Player)humanEntity).getUniqueId()) && !this.admin(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Game g;
        Player sp = e.getPlayer();
        Game sg = this.spectating.get(sp.getUniqueId());
        if (sg != null && !sp.getWorld().equals(sg.world)) {
            this.spectating.remove(sp.getUniqueId());
            sg.spectators.remove(sp.getUniqueId());
            if (sp.getGameMode() == GameMode.SPECTATOR && this.mayTouch(sp)) {
                sp.setGameMode(GameMode.SURVIVAL);
            }
        }
        if ((g = this.byPlayer.get(sp.getUniqueId())) != null && e.getFrom().equals(g.world) && !sp.getWorld().equals(g.world)) {
            g.leave(sp, false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game g;
        Player p = e.getPlayer();
        this.exitAllowed.add(p.getUniqueId());
        this.cancelPending(p.getUniqueId());
        if (this.spectating.containsKey(p.getUniqueId())) {
            this.sendToHub(p);
        }
        if ((g = this.byPlayer.get(p.getUniqueId())) != null) {
            g.leave(p, true);
        }
        this.exitAllowed.remove(p.getUniqueId());
    }

    private Game target(CommandSender sender, String[] args) {
        Game g;
        if (args.length > 1) {
            try {
                Game g2 = this.games.get(Integer.parseInt(args[1]));
                if (g2 == null) {
                    sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cPartie " + args[1] + " inexistante.");
                }
                return g2;
            }
            catch (NumberFormatException ignored) {
                sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cNumero de partie invalide.");
                return null;
            }
        }
        if (sender instanceof Player) {
            Player p = (Player)sender;
            Game g3 = this.byPlayer.get(p.getUniqueId());
            if (g3 == null) {
                g3 = this.spectating.get(p.getUniqueId());
            }
            if (g3 == null) {
                g3 = this.gameOfWorld(p.getWorld());
            }
            if (g3 != null) {
                return g3;
            }
        }
        if ((g = this.games.get(1)) == null) {
            sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cMonde introuvable.");
        }
        return g;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player pl;
        String sub = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player ? (pl = (Player)sender) : null;
        boolean admin = sender.hasPermission("eemiboatrace.admin");
        switch (sub) {
            case "join": {
                Player t;
                Player player = t = args.length > 1 && admin ? Bukkit.getPlayerExact((String)args[1]) : p;
                if (t == null) {
                    sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cJoueur introuvable.");
                    return true;
                }
                this.join(t);
                break;
            }
            case "spectate": 
            case "spec": {
                if (p == null) break;
                this.spectate(p, args.length > 1 ? args[1] : null);
                break;
            }
            case "leave": {
                if (p == null) {
                    return true;
                }
                if (this.spectating.containsKey(p.getUniqueId())) {
                    this.sendToHub(p);
                    return true;
                }
                if (this.cancelPending(p.getUniqueId())) {
                    p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Tu as quitte la course.");
                    return true;
                }
                Game g = this.byPlayer.get(p.getUniqueId());
                if (g == null) {
                    return true;
                }
                g.leave(p, true);
                p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Tu as quitte la course.");
                break;
            }
            case "record": 
            case "records": {
                for (Course c : this.courses.values()) {
                    String string = c.recordKey();
                    int l = c.laps();
                    long race = this.records.getLong(string + ".race-" + l + ".time", -1L);
                    long lap = this.records.getLong(string + ".lap.time", -1L);
                    sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7b" + c.name + " \u00a77- " + l + (l > 1 ? " tours : " : " tour : ") + (String)(race < 0L ? "\u00a78aucun" : "\u00a7d" + EemiBoatRace.time(race) + " \u00a7f" + this.records.getString(string + ".race-" + l + ".player")) + " \u00a77- tour : " + (String)(lap < 0L ? "\u00a78aucun" : "\u00a7d" + EemiBoatRace.time(lap) + " \u00a7f" + this.records.getString(string + ".lap.player")));
                }
                break;
            }
            case "start": {
                if (!admin) {
                    return true;
                }
                Game g = this.target(sender, args);
                if (g == null) {
                    return true;
                }
                if (g.running()) {
                    sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cLa course " + g.n + " est deja en cours.");
                    return true;
                }
                if (g.players.isEmpty()) {
                    sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cPersonne n'est inscrit (partie " + g.n + ").");
                    return true;
                }
                g.startGame();
                sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7aCourse " + g.n + " lancee.");
                break;
            }
            case "stop": {
                if (!admin) {
                    return true;
                }
                Game g = this.target(sender, args);
                if (g == null) {
                    return true;
                }
                g.forceStop();
                sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Course " + g.n + " arretee.");
                break;
            }
            case "build": {
                if (!admin) {
                    return true;
                }
                int done = 0;
                for (Course course : this.courses.values()) {
                    World w = course.base();
                    if (w == null) continue;
                    Game g = this.gameOfWorld(w);
                    if (g != null && g.running()) {
                        sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7c" + course.name + " : course en cours, ignore.");
                        continue;
                    }
                    this.build(w, course);
                    if (g != null) {
                        for (Player pl2 : this.online(g.players)) {
                            g.toWaiting(pl2);
                        }
                    }
                    this.makeModel(course);
                    ++done;
                }
                sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7a" + done + " circuit(s) prepare(s), modeles refaits.");
                break;
            }
            case "reload": {
                if (!admin) {
                    return true;
                }
                for (Game g : this.games.values()) {
                    if (!g.running()) continue;
                    sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7cImpossible pendant une course.");
                    return true;
                }
                this.loadSettings();
                this.loadRecords();
                for (Game g : this.games.values()) {
                    this.prepareWorld(g.world, g.lobbyY);
                    g.setCourse(g.c);
                    if (g.sign == null) continue;
                    g.sign.remove();
                    g.sign = null;
                }
                sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a7aConfig rechargee. \u00a77(liste des circuits et circuit procedural : au redemarrage)");
                break;
            }
            case "parties": 
            case "games": {
                if (!admin) {
                    return true;
                }
                StringBuilder models = new StringBuilder();
                for (Course course : this.courses.values()) {
                    models.append(" \u00a7f").append(course.id).append(course.model != null ? " \u00a7apret" : " \u00a7cabsent");
                }
                sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Parties (" + this.games.size() + "/" + this.maxInstances() + ", modeles" + String.valueOf(models) + "\u00a77) :");
                for (Game game : this.games.values()) {
                    sender.sendMessage("\u00a77 #" + game.n + " \u00a7f" + game.world.getName() + " \u00a77(" + game.c.name + ") - " + game.state.name().toLowerCase(Locale.ROOT) + " - \u00a7f" + game.players.size() + " \u00a77inscrits, \u00a7f" + game.playingCount() + " \u00a77en course, \u00a7f" + game.spectators.size() + " \u00a77spectateurs");
                }
                for (Map.Entry entry : this.creating.entrySet()) {
                    sender.sendMessage("\u00a77 #" + String.valueOf(entry.getKey()) + " \u00a7f" + WorldClone.name(this.creatingCourse.get(entry.getKey()).worldName, (Integer)entry.getKey()) + " \u00a77- preparation - \u00a7f" + ((List)entry.getValue()).size() + " \u00a77en attente");
                }
                break;
            }
            default: {
                sender.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77/boatrace <join|leave|spectate [joueur]|record>" + (admin ? " \u00a78| admin : start [n], stop [n], build, reload, parties" : ""));
            }
        }
        return true;
    }

    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore")) {
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)("eemi " + args));
        }
    }

    private void reportStatus() {
        boolean anyWaiting = this.games.isEmpty();
        boolean anyPlaying = false;
        int waiting = 0;
        int playing = 0;
        for (Game game : this.games.values()) {
            if (game.destroyed) continue;
            if (game.state == State.WAITING || game.state == State.STARTING) {
                anyWaiting = true;
            } else {
                anyPlaying = true;
            }
            waiting += game.waitingCount();
            playing += game.playingCount();
        }
        for (List list : this.creating.values()) {
            waiting += list.size();
            anyWaiting = true;
        }
        String st = anyWaiting ? "waiting" : (anyPlaying ? "playing" : "waiting");
        String string = "status boatrace " + st + " " + waiting + " " + playing + " " + this.maxPlayers() * this.maxInstances();
        long now = System.currentTimeMillis();
        if (string.equals(this.lastStatus) && now - this.lastStatusAt < 30000L) {
            return;
        }
        this.lastStatus = string;
        this.lastStatusAt = now;
        this.eemi(string);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTaskLater((Plugin)this, r, 3L);
    }

    List<Player> online(Collection<UUID> ids) {
        ArrayList<Player> list = new ArrayList<Player>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer((UUID)id);
            if (p == null) continue;
            list.add(p);
        }
        return list;
    }

    static {
        CIRCUITS.put("cosmic", new Circuit("Cosmic Cove", "boat_cosmic", 103, 220, 121.5, -25.0, 0, 0, 2));
        CIRCUITS.put("adventure", new Circuit("The Adventure", "boat_adventure", -59, 220, -259.5, -35.5, 1, 40, 1));
        CIRCUITS.put("stadium", new Circuit("Mario Kart Stadium", "boat_stadium", -60, 220, -319.5, 520.0, 0, 0, 2));
        WOOLS = new Material[]{Material.RED_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL, Material.MAGENTA_WOOL, Material.ORANGE_WOOL, Material.BLUE_WOOL, Material.PINK_WOOL};
    }

    private final class Course {
        final String id;
        final String name;
        final String worldName;
        final boolean imported;
        final Track track;
        final int lobbyY;
        final int gridCols;
        final int lapsOwn;
        final int cpOwn;
        World model;

        Course(String id, String name, String worldName, boolean imported, Track track, int lobbyY, int gridCols, int lapsOwn, int cpOwn) {
            this.id = id;
            this.name = name;
            this.worldName = worldName;
            this.imported = imported;
            this.track = track;
            this.lobbyY = lobbyY;
            this.gridCols = gridCols;
            this.lapsOwn = lapsOwn;
            this.cpOwn = cpOwn;
        }

        int laps() {
            return this.lapsOwn > 0 ? this.lapsOwn : EemiBoatRace.this.laps;
        }

        int checkpoints() {
            return this.cpOwn > 0 ? this.cpOwn : EemiBoatRace.this.checkpoints;
        }

        String modelName() {
            return this.worldName + "_modele";
        }

        String recordKey() {
            if (this.imported) {
                return "circuit-" + this.id;
            }
            return "circuit-" + Math.round(EemiBoatRace.this.cfgD("track-scale", 1.4) * 100.0) + "-" + EemiBoatRace.this.width;
        }

        World base() {
            return Bukkit.getWorld((String)this.worldName);
        }

        boolean owns(String w) {
            return w.equals(this.worldName) || w.equals(this.modelName()) || WorldClone.index(this.worldName, w) > 0;
        }
    }

    final class Game {
        final int n;
        World world;
        boolean copy;
        Course c;
        Track track;
        int floorY;
        int lobbyY;
        int laps;
        int checkpoints;
        final Set<UUID> players = new LinkedHashSet<UUID>();
        final Map<UUID, Racer> racers = new LinkedHashMap<UUID, Racer>();
        final Set<UUID> spectators = new LinkedHashSet<UUID>();
        final Map<UUID, Scoreboard> boards = new HashMap<UUID, Scoreboard>();
        final List<Block> cages = new ArrayList<Block>();
        State state = State.WAITING;
        Phase phase = Phase.NONE;
        int timer;
        int phaseTicks;
        int grace = -1;
        int finished;
        long raceStart;
        TextDisplay sign;
        boolean destroyed;

        Game(int n, World world, Course c, boolean copy) {
            this.n = n;
            this.world = world;
            this.copy = copy;
            this.setCourse(c);
        }

        void setCourse(Course c) {
            this.c = c;
            this.track = c.track;
            this.floorY = (int)Math.floor(this.track.minY());
            this.lobbyY = c.lobbyY;
            this.laps = c.laps();
            this.checkpoints = c.checkpoints();
        }

        Location lobbySpawn() {
            return EemiBoatRace.this.lobbySpawn(this.world, this.lobbyY);
        }

        Location spectatorSpot() {
            if (this.c.imported) {
                Location l = this.trackLocation(0.0, 0.0).add(0.0, 15.0, 0.0);
                l.setPitch(35.0f);
                return l;
            }
            Location l = new Location(this.world, 0.5, (double)(this.floorY + 15), -24.5);
            l.setYaw(0.0f);
            l.setPitch(35.0f);
            return l;
        }

        Location trackLocation(double s, double lateral) {
            return EemiBoatRace.this.trackLocation(this.world, this.track, s, lateral);
        }

        Location slotLocation(int k) {
            int cols = this.c.gridCols >= 2 ? 2 : 1;
            int row = k / cols;
            double lateral = cols == 1 ? 0.0 : (k % 2 == 0 ? -2.5 : 2.5);
            Location l = this.trackLocation(-(EemiBoatRace.this.cfgD("grid-first", 4.5) + (double)(3 * row)), lateral);
            l.setX((double)Math.round(l.getX()));
            l.setZ((double)Math.round(l.getZ()));
            return l;
        }

        boolean running() {
            return this.state == State.PLAYING || this.state == State.ENDING;
        }

        void broadcast(String msg) {
            LinkedHashSet<UUID> all = new LinkedHashSet<UUID>(this.players);
            all.addAll(this.spectators);
            for (Player p : EemiBoatRace.this.online(all)) {
                p.sendMessage(EemiBoatRace.PREFIX + msg);
            }
        }

        void ensureSign() {
            if (this.sign != null && this.sign.isValid()) {
                return;
            }
            Location l = new Location(this.world, 0.5, (double)this.lobbyY + 2.8, -6.0);
            if (!l.isChunkLoaded()) {
                return;
            }
            this.sign = (TextDisplay)this.world.spawn(l, TextDisplay.class, td -> {
                td.text(((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text((String)"MARIO BARQUE", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD)).append((Component)Component.newline())).append(Component.text((String)this.c.name, (TextColor)NamedTextColor.GOLD).decorate(TextDecoration.BOLD))).append((Component)Component.newline())).append(Component.text((String)(this.laps + (this.laps > 1 ? " tours" : " tour") + " sur la glace, le premier gagne !"), (TextColor)NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))).append((Component)Component.newline())).append(Component.text((String)"Passe tous les points de controle : pas de raccourci.", (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))).append((Component)Component.newline())).append(Component.text((String)"Coince ? Touche F ou objet \u00ab Revenir \u00bb.", (TextColor)NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)).append((Component)Component.newline()).append(Component.text((String)(EemiBoatRace.this.kart.enabled() ? "Boites ? : objets surprises, clic droit ou Q pour les lancer !" : ""), (TextColor)NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, false)));
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(false);
            });
        }

        Entity spawnBoat(Location l, Racer r) {
            Entity e = this.world.spawnEntity(l, r.boatType, CreatureSpawnEvent.SpawnReason.CUSTOM, en -> {
                en.getPersistentDataContainer().set(EemiBoatRace.this.boatKey, PersistentDataType.STRING, r.id.toString());
                en.setPersistent(false);
                en.setInvulnerable(true);
            });
            if (e == null || !e.isValid()) {
                EemiBoatRace.this.getLogger().warning("Le bateau de " + r.name + " n'a pas pu apparaitre en " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " (" + this.world.getName() + ").");
                return null;
            }
            r.boat = e.getUniqueId();
            return e;
        }

        void removeBoats() {
            EemiBoatRace.this.kart.clear(this);
            for (Entity e : this.world.getEntities()) {
                if (!EemiBoatRace.this.isOurBoat(e)) continue;
                for (Entity pass : e.getPassengers()) {
                    EemiBoatRace.this.exitAllowed.add(pass.getUniqueId());
                }
                e.eject();
                e.remove();
            }
            for (Racer r : this.racers.values()) {
                r.boat = null;
            }
        }

        void boardAt(Player p, Racer r, Location l) {
            EemiBoatRace.this.removeBoat(r, p);
            r.respawning = true;
            p.setFallDistance(0.0f);
            p.teleport(l);
            Bukkit.getScheduler().runTaskLater((Plugin)EemiBoatRace.this, () -> {
                if (this.destroyed || !p.isOnline() || this.racers.get(r.id) != r || this.state != State.PLAYING || r.finish >= 0L) {
                    r.respawning = false;
                    return;
                }
                p.teleport(l);
                Entity boat = this.spawnBoat(l, r);
                if (boat != null) {
                    boat.addPassenger((Entity)p);
                }
                EemiBoatRace.this.exitAllowed.remove(r.id);
                r.respawning = false;
                r.offBoatTicks = 0;
            }, 2L);
        }

        void tick() {
            if (this.state == State.PLAYING) {
                if (this.phase == Phase.GRID) {
                    this.gridTick();
                } else if (this.phase == Phase.RACE) {
                    this.raceTick();
                }
            }
        }

        void second() {
            if (this.world.getTime() != (long)EemiBoatRace.this.cfg("time", 6000)) {
                this.world.setTime((long)EemiBoatRace.this.cfg("time", 6000));
            }
            int min = Math.max(1, EemiBoatRace.this.cfg("min-players", 2));
            switch (this.state.ordinal()) {
                case 0: {
                    if (this.players.size() < min) break;
                    this.state = State.STARTING;
                    this.timer = EemiBoatRace.this.cfg("countdown", 20);
                    this.broadcast("\u00a7eAssez de joueurs : depart dans \u00a76" + this.timer + " s\u00a7e !");
                    break;
                }
                case 1: {
                    if (this.players.size() < min) {
                        this.state = State.WAITING;
                        this.broadcast("\u00a7cPas assez de joueurs, depart annule.");
                        break;
                    }
                    if (this.players.size() >= EemiBoatRace.this.maxPlayers()) {
                        this.timer = Math.min(this.timer, EemiBoatRace.this.cfg("full-countdown", 10));
                    }
                    if (this.timer <= 5 && this.timer > 0) {
                        for (Player p : EemiBoatRace.this.online(this.players)) {
                            p.showTitle(Title.title((Component)Component.text((String)String.valueOf(this.timer), (TextColor)NamedTextColor.AQUA).decorate(TextDecoration.BOLD), (Component)Component.empty(), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofMillis(900L), (Duration)Duration.ofMillis(100L))));
                            p.playSound((Entity)p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        }
                    }
                    if (this.timer <= 0) {
                        this.startGame();
                        break;
                    }
                    --this.timer;
                    break;
                }
                case 2: {
                    if (this.phase != Phase.RACE) break;
                    this.raceSecond();
                    break;
                }
                case 3: {
                    if (--this.timer > 0) break;
                    this.backToLobby();
                }
            }
            if (this.destroyed) {
                return;
            }
            if (!this.players.isEmpty()) {
                this.ensureSign();
            }
            for (Player p : EemiBoatRace.this.online(this.players)) {
                this.updateSidebar(p);
            }
        }

        void safety() {
            for (Player p : this.world.getPlayers()) {
                if (p.getLocation().getY() > (double)(this.floorY - 20) || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE || this.racing(p.getUniqueId())) continue;
                p.setFallDistance(0.0f);
                p.teleport(this.running() && this.players.contains(p.getUniqueId()) ? this.spectatorSpot() : this.lobbySpawn());
            }
        }

        void startGame() {
            List<Player> list = EemiBoatRace.this.online(this.players);
            if (list.isEmpty()) {
                this.state = State.WAITING;
                return;
            }
            Collections.shuffle(list, EemiBoatRace.this.random);
            this.setCourse(this.c);
            EemiBoatRace.this.markPlayed(this.c);
            this.racers.clear();
            this.finished = 0;
            this.grace = -1;
            this.state = State.PLAYING;
            this.phase = Phase.GRID;
            this.phaseTicks = Math.max(3, EemiBoatRace.this.cfg("start-lights", 5)) * 20;
            this.removeBoats();
            this.clearCages();
            List<EntityType> types = EemiBoatRace.this.boatTypes();
            int k = 0;
            for (Player p : list) {
                Racer r = new Racer(p.getUniqueId(), p.getName());
                r.slot = k;
                r.boatType = types.get(k % types.size());
                this.racers.put(r.id, r);
                Location l = this.slotLocation(k);
                this.placeCage(l);
                if (EemiBoatRace.this.mayTouch(p)) {
                    EemiBoatRace.this.resetPlayer(p, GameMode.ADVENTURE);
                    p.getInventory().setItem(8, EemiBoatRace.this.kit(EemiBoatRace.this.named(new ItemStack(Material.RECOVERY_COMPASS), "Revenir au dernier point", NamedTextColor.YELLOW)));
                    p.getInventory().setHeldItemSlot(0);
                }
                this.boardAt(p, r, l);
                p.showTitle(Title.title((Component)Component.text((String)"A VOS MARQUES", (TextColor)NamedTextColor.AQUA).decorate(TextDecoration.BOLD), (Component)Component.text((String)(this.laps + (this.laps > 1 ? " tours" : " tour") + " - attends le feu vert !"), (TextColor)NamedTextColor.GRAY), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofSeconds(2L), (Duration)Duration.ofMillis(400L))));
                ++k;
            }
            this.setLights(Material.BLACK_CONCRETE);
            this.broadcast("\u00a7eDepart sur \u00a7b" + this.c.name + " \u00a7eavec \u00a76" + this.racers.size() + " \u00a7epilote(s), \u00a76" + this.laps + " \u00a7etour(s) ! \u00a77Touche \u00a7fF \u00a77(ou l'objet Revenir) si tu es coince.");
            EemiBoatRace.this.reportStatus();
        }

        void placeCage(Location l) {
            int base;
            int bx = l.getBlockX();
            int bz = l.getBlockZ();
            int y = base = (int)Math.floor(l.getY());
            for (int sy = base + 2; sy >= base - 5; --sy) {
                if (!this.world.getBlockAt(bx, sy, bz).getType().isSolid() && !this.world.getBlockAt(bx - 1, sy, bz - 1).getType().isSolid()) continue;
                y = sy + 1;
                break;
            }
            for (int x = bx - 2; x <= bx + 1; ++x) {
                for (int z = bz - 2; z <= bz + 1; ++z) {
                    boolean border;
                    boolean bl = border = x == bx - 2 || x == bx + 1 || z == bz - 2 || z == bz + 1;
                    if (!border) continue;
                    for (int dy = 0; dy <= 1; ++dy) {
                        Block b = this.world.getBlockAt(x, y + dy, z);
                        if (!b.getType().isAir()) continue;
                        b.setType(Material.BARRIER, false);
                        this.cages.add(b);
                    }
                }
            }
        }

        void holdBoats() {
            for (Racer r : this.racers.values()) {
                double dz;
                Entity boat;
                if (r.respawning || r.boat == null || (boat = Bukkit.getEntity((UUID)r.boat)) == null || !boat.isValid()) continue;
                Location slot = this.slotLocation(r.slot);
                Location at = boat.getLocation();
                double dx = at.getX() - slot.getX();
                if (dx * dx + (dz = at.getZ() - slot.getZ()) * dz <= 0.25 && boat.getVelocity().lengthSquared() < 0.01) continue;
                boat.setVelocity(new Vector());
                if (!(dx * dx + dz * dz > 0.25)) continue;
                Location back = slot.clone();
                back.setY(at.getY());
                back.setYaw(at.getYaw());
                back.setPitch(0.0f);
                boat.teleport(back, new TeleportFlag[]{TeleportFlag.EntityState.RETAIN_PASSENGERS});
            }
        }

        void clearCages() {
            for (Block b : this.cages) {
                if (b.getType() != Material.BARRIER) continue;
                b.setType(Material.AIR, false);
            }
            this.cages.clear();
        }

        void setLights(Material m) {
            if (this.c.imported) {
                return;
            }
            for (int[] p : EemiBoatRace.this.lightPos) {
                this.world.getBlockAt(p[0], p[1], p[2]).setType(m, false);
            }
        }

        void gridTick() {
            int sec;
            --this.phaseTicks;
            this.holdBoats();
            if (this.phaseTicks % 20 == 0) {
                this.purgeBoats();
                for (Racer r : this.racers.values()) {
                    Entity v;
                    Player p = Bukkit.getPlayer((UUID)r.id);
                    if (p == null || r.respawning || (v = p.getVehicle()) != null && v.getUniqueId().equals(r.boat)) continue;
                    this.boardAt(p, r, this.slotLocation(r.slot));
                }
            }
            if (this.phaseTicks > 0 && this.phaseTicks % 20 == 0 && (sec = this.phaseTicks / 20) <= 3) {
                this.setLights(sec == 1 ? Material.ORANGE_CONCRETE : Material.RED_CONCRETE);
                for (Player p : EemiBoatRace.this.online(this.racers.keySet())) {
                    p.showTitle(Title.title((Component)Component.text((String)String.valueOf(sec), (TextColor)(sec == 1 ? NamedTextColor.GOLD : NamedTextColor.RED)).decorate(TextDecoration.BOLD), (Component)Component.text((String)"Prepare-toi...", (TextColor)NamedTextColor.GRAY), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofMillis(900L), (Duration)Duration.ofMillis(100L))));
                    p.playSound((Entity)p, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                }
            }
            if (this.phaseTicks <= 0) {
                this.go();
            }
        }

        void go() {
            this.phase = Phase.RACE;
            this.clearCages();
            this.setLights(Material.LIME_CONCRETE);
            this.raceStart = System.currentTimeMillis();
            EemiBoatRace.this.kart.start(this);
            for (Racer r : this.racers.values()) {
                Player p = Bukkit.getPlayer((UUID)r.id);
                Location l = p != null && p.getVehicle() != null ? p.getVehicle().getLocation() : this.slotLocation(r.slot);
                r.idx = this.track.nearestGlobal(l.getX(), l.getZ());
                r.s = this.track.position(r.idx, l.getX(), l.getZ());
                r.dist = this.track.wrap(r.s);
                r.passed = 0;
                r.lapStart = this.raceStart;
                r.stuckRef = r.dist;
                r.stuckSec = 0;
                if (p == null) continue;
                p.showTitle(Title.title((Component)Component.text((String)"GO !", (TextColor)NamedTextColor.GREEN).decorate(TextDecoration.BOLD), (Component)Component.empty(), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofMillis(1000L), (Duration)Duration.ofMillis(300L))));
                p.playSound((Entity)p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                p.playSound((Entity)p, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            }
        }

        boolean racing(UUID id) {
            Racer r = this.racers.get(id);
            return r != null && this.state == State.PLAYING && r.finish < 0L;
        }

        double cpPos(int g) {
            return (double)g * this.track.length / (double)this.checkpoints;
        }

        void raceTick() {
            EemiBoatRace.this.kart.tick(this);
            for (Racer r : new ArrayList<Racer>(this.racers.values())) {
                Player p;
                if (r.finish >= 0L || r.respawning || (p = Bukkit.getPlayer((UUID)r.id)) == null) continue;
                Entity v = p.getVehicle();
                if (v == null || !v.getUniqueId().equals(r.boat)) {
                    if (++r.offBoatTicks <= 10) continue;
                    this.respawn(p, r, "Tu es sorti du bateau");
                    continue;
                }
                r.offBoatTicks = 0;
                Location l = v.getLocation();
                double low = this.c.imported ? this.track.minY() : this.track.minY(r.idx, 15);
                if (l.getY() < low - (double)EemiBoatRace.this.cfg("fall-depth", 3)) {
                    this.respawn(p, r, "Hors piste");
                    continue;
                }
                int ni = this.track.nearest(l.getX(), l.getZ(), r.idx, 12, 40);
                double lim = EemiBoatRace.this.half + EemiBoatRace.this.cfgD("off-track-margin", 2.5);
                if (!this.c.imported && this.track.distSq(ni, l.getX(), l.getZ()) > lim * lim) {
                    this.respawn(p, r, "Hors piste");
                    continue;
                }
                double ns = this.track.position(ni, l.getX(), l.getZ());
                r.dist += this.track.wrap(ns - r.s);
                r.s = ns;
                r.idx = ni;
                int total = this.laps * this.checkpoints;
                while (r.passed < total && r.dist >= this.cpPos(r.passed + 1)) {
                    ++r.passed;
                    if (r.passed % this.checkpoints == 0) {
                        this.lapDone(p, r);
                    }
                    if (r.finish < 0L) continue;
                }
                if (r.finish >= 0L || EemiBoatRace.this.ticks % 5L != 0L) continue;
                int lap = Math.min(this.laps, r.passed / this.checkpoints + 1);
                p.sendActionBar(((TextComponent)Component.text((String)("Tour " + lap + "/" + this.laps + " - "), (TextColor)NamedTextColor.AQUA).append(Component.text((String)(this.placeOf(r) + EemiBoatRace.ordinal(this.placeOf(r))), (TextColor)NamedTextColor.GOLD).decorate(TextDecoration.BOLD))).append((Component)Component.text((String)("  " + EemiBoatRace.time(System.currentTimeMillis() - this.raceStart)), (TextColor)NamedTextColor.WHITE)).append(EemiBoatRace.this.kart.hud(r)));
            }
        }

        void purgeBoats() {
            for (Boat b : this.world.getEntitiesByClass(Boat.class)) {
                if (!EemiBoatRace.this.isOurBoat(b) || !b.getPassengers().isEmpty()) continue;
                Racer r = null;
                try {
                    r = this.racers.get(UUID.fromString(b.getPersistentDataContainer().get(EemiBoatRace.this.boatKey, PersistentDataType.STRING)));
                }
                catch (IllegalArgumentException | NullPointerException ignored) {
                }
                if (r != null && r.respawning) continue;
                if (r == null || r.finish >= 0L || !b.getUniqueId().equals(r.boat)) {
                    b.remove();
                }
            }
        }

        void raceSecond() {
            this.purgeBoats();
            double minMove = EemiBoatRace.this.cfgD("stuck-min-distance", 3.0);
            int stuckSecs = EemiBoatRace.this.cfg("stuck-seconds", 10);
            for (Racer r : new ArrayList<Racer>(this.racers.values())) {
                Player p;
                if (r.finish >= 0L || r.respawning || (p = Bukkit.getPlayer((UUID)r.id)) == null) continue;
                if (r.dist - r.stuckRef >= minMove) {
                    r.stuckRef = r.dist;
                    r.stuckSec = 0;
                    continue;
                }
                if (stuckSecs <= 0 || !this.track.solid[r.idx] || ++r.stuckSec < stuckSecs) continue;
                this.respawn(p, r, "Bloque");
            }
            long elapsed = System.currentTimeMillis() - this.raceStart;
            boolean allDone = true;
            for (Racer r : this.racers.values()) {
                if (r.finish >= 0L) continue;
                allDone = false;
                break;
            }
            if (this.racers.isEmpty() || allDone) {
                this.endGame(true);
                return;
            }
            // pas de delai apres le 1er, mais un dernier pilote seul (AFK ?) ne bloque pas la course
            if (this.grace < 0 && this.finished > 0) {
                int left = 0;
                for (Racer o : this.racers.values()) {
                    if (o.finish < 0L) {
                        left++;
                    }
                }
                int last = EemiBoatRace.this.cfg("last-racer-grace", 60);
                if (left == 1 && last > 0) {
                    this.grace = last;
                    this.broadcast("§eDernier pilote en course : §6" + last + " s §epour terminer !");
                }
            }
            if (this.grace > 0) {
                --this.grace;
                if (this.grace <= 10 && this.grace > 0) {
                    for (Player p : EemiBoatRace.this.online(this.racers.keySet())) {
                        p.playSound((Entity)p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.5f);
                    }
                }
                if (this.grace == 0) {
                    this.broadcast("\u00a7cTemps ecoule !");
                    this.endGame(true);
                    return;
                }
            }
            if (elapsed >= (long)EemiBoatRace.this.cfg("max-time", 600) * 1000L) {
                this.broadcast("\u00a7cTemps maximal de la course atteint.");
                this.endGame(true);
            }
        }

        void respawn(Player p, Racer r, String reason) {
            double d;
            if (r.respawning || r.finish >= 0L || this.phase != Phase.RACE) {
                return;
            }
            r.dist = d = this.track.solidBack(r.passed > 0 ? this.cpPos(r.passed) + 1.5 : -3.0);
            r.s = this.track.mod(d);
            r.idx = this.track.index(d);
            r.stuckRef = d;
            r.stuckSec = 0;
            r.lastRespawn = System.currentTimeMillis();
            EemiBoatRace.this.kart.onRespawn(r);
            Location l = this.trackLocation(d, 0.0);
            l.setY(this.track.yAt(d) + 0.2);
            this.boardAt(p, r, l);
            p.playSound((Entity)p, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
            p.sendActionBar((Component)Component.text((String)(reason + " : retour au dernier point de controle."), (TextColor)NamedTextColor.YELLOW));
        }

        void askRespawn(Player p) {
            Racer r = this.racers.get(p.getUniqueId());
            if (r == null || !this.racing(r.id)) {
                return;
            }
            if (this.phase != Phase.RACE) {
                p.sendActionBar((Component)Component.text((String)"Attends le feu vert !", (TextColor)NamedTextColor.RED));
                return;
            }
            long cd = (long)(EemiBoatRace.this.cfgD("respawn-cooldown", 2.0) * 1000.0);
            if (System.currentTimeMillis() - r.lastRespawn < cd) {
                return;
            }
            this.respawn(p, r, "Demande");
        }

        void lapDone(Player p, Racer r) {
            boolean best;
            long now = System.currentTimeMillis();
            long lap = now - r.lapStart;
            r.lapStart = now;
            int lapNo = r.passed / this.checkpoints;
            boolean bl = best = r.bestLap < 0L || lap < r.bestLap;
            if (best) {
                r.bestLap = lap;
            }
            this.checkLapRecord(r, lap);
            if (lapNo >= this.laps) {
                this.finish(p, r, now - this.raceStart);
                return;
            }
            p.showTitle(Title.title((Component)Component.empty(), (Component)((TextComponent)Component.text((String)("Tour " + (lapNo + 1) + "/" + this.laps), (TextColor)NamedTextColor.AQUA).decorate(TextDecoration.BOLD)).append(Component.text((String)("  (" + EemiBoatRace.time(lap) + ")"), (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofMillis(1500L), (Duration)Duration.ofMillis(300L))));
            p.playSound((Entity)p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
            if (lapNo == this.laps - 1) {
                p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a76Dernier tour !");
                p.playSound((Entity)p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            }
        }

        void finish(Player p, Racer r, long time) {
            r.finish = time;
            r.place = ++this.finished;
            r.item = null;
            r.rollTicks = 0;
            EemiBoatRace.this.removeBoat(r, p);
            if (p.getWorld().equals(this.world)) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(this.spectatorSpot());
            }
            p.showTitle(Title.title((Component)Component.text((String)(r.place + EemiBoatRace.ordinal(r.place) + " !"), (TextColor)(r.place == 1 ? NamedTextColor.GOLD : NamedTextColor.AQUA)).decorate(TextDecoration.BOLD), (Component)Component.text((String)("Temps : " + EemiBoatRace.time(time)), (TextColor)NamedTextColor.WHITE)));
            p.playSound((Entity)p, r.place == 1 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            this.broadcast("\u00a7a" + r.name + " \u00a77termine \u00a76" + r.place + EemiBoatRace.ordinal(r.place) + " \u00a77en \u00a7f" + EemiBoatRace.time(time) + "\u00a77.");
            // recompenses donnees a l'arrivee : le pilote peut partir sans attendre les autres
            EemiBoatRace.this.eemi("reward " + p.getName() + " boatrace play");
            if (r.place == 1) {
                EemiBoatRace.this.eemi("reward " + p.getName() + " boatrace win");
            }
            r.rewarded = true;
            if (r.place == 1) {
                EemiBoatRace.this.firework(this.trackLocation(0.0, 0.0).add(0.0, 3.0, 0.0));
                // finish-grace <= 0 : aucun delai, chacun termine sa course (dans la limite de max-time)
                int g = EemiBoatRace.this.cfg("finish-grace", 0);
                boolean others = false;
                for (Racer o : this.racers.values()) {
                    if (o.finish >= 0L) continue;
                    others = true;
                    break;
                }
                if (others && g > 0) {
                    this.grace = g;
                    this.broadcast("\u00a7eLes autres pilotes ont \u00a76" + g + " s \u00a7epour terminer !");
                }
            }
            this.checkRaceRecord(r, time);
        }

        void checkLapRecord(Racer r, long lap) {
            String key = this.c.recordKey() + ".lap";
            long old = EemiBoatRace.this.records.getLong(key + ".time", -1L);
            if (old >= 0L && lap >= old) {
                return;
            }
            EemiBoatRace.this.records.set(key + ".time", lap);
            EemiBoatRace.this.records.set(key + ".player", r.name);
            EemiBoatRace.this.records.set(key + ".date", LocalDate.now().toString());
            EemiBoatRace.this.saveRecords();
            this.broadcast("\u00a7d\u00a7lRecord du tour ! \u00a7r\u00a7d" + r.name + " \u00a77en \u00a7f" + EemiBoatRace.time(lap) + (String)(old >= 0L ? " \u00a78(ancien " + EemiBoatRace.time(old) + ")" : ""));
        }

        void checkRaceRecord(Racer r, long time) {
            String key = this.c.recordKey() + ".race-" + this.laps;
            long old = EemiBoatRace.this.records.getLong(key + ".time", -1L);
            if (old >= 0L && time >= old) {
                return;
            }
            EemiBoatRace.this.records.set(key + ".time", time);
            EemiBoatRace.this.records.set(key + ".player", r.name);
            EemiBoatRace.this.records.set(key + ".date", LocalDate.now().toString());
            EemiBoatRace.this.saveRecords();
            Component t = Component.text((String)"Nouveau record !", (TextColor)NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD);
            for (Player p : EemiBoatRace.this.online(this.players)) {
                p.showTitle(Title.title((Component)t, (Component)Component.text((String)(r.name + " - " + EemiBoatRace.time(time)), (TextColor)NamedTextColor.WHITE)));
            }
            this.broadcast("\u00a7d\u00a7lNouveau record du circuit ! \u00a7r\u00a7d" + r.name + " \u00a77en \u00a7f" + EemiBoatRace.time(time) + (String)(old >= 0L ? " \u00a78(ancien " + EemiBoatRace.time(old) + ")" : ""));
        }

        List<Racer> ranking() {
            ArrayList<Racer> list = new ArrayList<Racer>(this.racers.values());
            list.sort((a, b) -> {
                if (a.finish >= 0L && b.finish >= 0L) {
                    return Long.compare(a.finish, b.finish);
                }
                if (a.finish >= 0L) {
                    return -1;
                }
                if (b.finish >= 0L) {
                    return 1;
                }
                return Double.compare(b.dist, a.dist);
            });
            return list;
        }

        int placeOf(Racer r) {
            return this.ranking().indexOf(r) + 1;
        }

        void endGame(boolean rewards) {
            if (this.state != State.PLAYING) {
                return;
            }
            List<Racer> rank = this.ranking();
            this.state = State.ENDING;
            this.phase = Phase.NONE;
            this.grace = -1;
            this.timer = EemiBoatRace.this.cfg("end-time", 10);
            for (Racer r : this.racers.values()) {
                EemiBoatRace.this.exitAllowed.add(r.id);
            }
            this.removeBoats();
            this.clearCages();
            this.setLights(Material.BLACK_CONCRETE);
            if (rewards) {
                for (Racer r : rank) {
                    Player p = Bukkit.getPlayer((UUID)r.id);
                    if (p == null || r.rewarded) continue;
                    // pilotes arrives : deja recompenses a l'arrivee ; les autres ont leur participation
                    EemiBoatRace.this.eemi("reward " + p.getName() + " boatrace play");
                }
            }
            ArrayList<String> lines = new ArrayList<String>();
            lines.add("\u00a7b\u00a7m          \u00a7r \u00a7fClassement final \u00a7b\u00a7m          ");
            int i = 1;
            for (Racer r : rank) {
                String col = i == 1 ? "\u00a76" : (i == 2 ? "\u00a7f" : (i == 3 ? "\u00a7c" : "\u00a77"));
                if (r.finish >= 0L) {
                    lines.add(col + i + ". " + r.name + " \u00a78- \u00a7f" + EemiBoatRace.time(r.finish) + (String)(r.bestLap >= 0L ? " \u00a78(meilleur tour " + EemiBoatRace.time(r.bestLap) + ")" : ""));
                } else {
                    lines.add(col + i + ". " + r.name + " \u00a78- \u00a77non classe (tour " + Math.min(this.laps, r.passed / this.checkpoints + 1) + "/" + this.laps + ")");
                }
                ++i;
            }
            Racer winner = rank.isEmpty() || rank.get((int)0).finish < 0L ? null : rank.get(0);
            Component title = winner == null ? Component.text((String)"Course terminee", (TextColor)NamedTextColor.GRAY).decorate(TextDecoration.BOLD) : Component.text((String)winner.name, (TextColor)NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
            LinkedHashSet<UUID> audience = new LinkedHashSet<UUID>(this.players);
            audience.addAll(this.spectators);
            for (Player p : EemiBoatRace.this.online(audience)) {
                boolean bl;
                for (String string : lines) {
                    p.sendMessage(string);
                }
                Racer me = this.racers.get(p.getUniqueId());
                boolean bl2 = bl = winner != null && me == winner;
                TextComponent sub = me == null ? (winner == null ? Component.empty() : Component.text((String)"gagne la course !", (TextColor)NamedTextColor.YELLOW)) : Component.text((String)(bl ? "Tu as gagne la course !" : "Tu termines " + (rank.indexOf(me) + 1) + EemiBoatRace.ordinal(rank.indexOf(me) + 1)), (TextColor)NamedTextColor.YELLOW);
                p.showTitle(Title.title((Component)title, (Component)sub));
                if (!this.racers.containsKey(p.getUniqueId()) || !p.getWorld().equals(this.world)) continue;
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(this.spectatorSpot());
            }
            if (winner != null) {
                for (int k = 0; k < 5; ++k) {
                    Bukkit.getScheduler().runTaskLater((Plugin)EemiBoatRace.this, () -> {
                        if (!this.destroyed && this.state == State.ENDING) {
                            EemiBoatRace.this.firework(this.trackLocation(0.0, EemiBoatRace.this.random.nextInt(7) - 3).add(0.0, 2.0, 0.0));
                        }
                    }, (long)k * 20L);
                }
            }
            EemiBoatRace.this.reportStatus();
        }

        void backToLobby() {
            Course next;
            this.state = State.WAITING;
            this.phase = Phase.NONE;
            for (UUID id : this.racers.keySet()) {
                EemiBoatRace.this.exitAllowed.remove(id);
            }
            this.racers.clear();
            for (Player p : EemiBoatRace.this.online(this.spectators)) {
                EemiBoatRace.this.spectating.remove(p.getUniqueId(), this);
                EemiBoatRace.this.sendToHub(p);
                p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Course terminee, retour au hub.");
            }
            for (UUID id : this.spectators) {
                EemiBoatRace.this.spectating.remove(id, this);
            }
            this.spectators.clear();
            // 2.4.1 : jamais le circuit qui vient d'etre joue (ni le dernier joue, sauvegarde)
            next = EemiBoatRace.this.getConfig().getBoolean("rotation", true) && (!this.players.isEmpty() || this.n == 1) ? EemiBoatRace.this.pickCourse(this, true) : null;
            if (next != null && next != this.c) {
                this.switchCourse(next);
            } else {
                for (Player p : EemiBoatRace.this.online(this.players)) {
                    this.toWaiting(p);
                }
            }
            for (Player p : EemiBoatRace.this.online(this.players)) {
                p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Tu restes dans la file de Mario Barque. Clique sur le lit (ou \u00a7f/boatrace leave\u00a77) pour revenir au lobby.");
            }
            EemiBoatRace.this.reportStatus();
            this.checkEmpty();
        }

        void switchCourse(Course nc) {
            World w = nc.base();
            if (w == null) {
                for (Player p : EemiBoatRace.this.online(this.players)) {
                    this.toWaiting(p);
                }
                return;
            }
            World old = this.world;
            Course oc = this.c;
            boolean wasCopy = this.copy;
            this.removeBoats();
            this.clearCages();
            if (this.sign != null) {
                this.sign.remove();
                this.sign = null;
            }
            this.world = w;
            this.copy = false;
            this.setCourse(nc);
            EemiBoatRace.this.prepareWorld(w, this.lobbyY);
            for (Chunk0 c0 : Chunk0.ALL) {
                w.getChunkAt(c0.x, c0.z).load(true);
            }
            for (Player p : EemiBoatRace.this.online(this.players)) {
                this.toWaiting(p);
            }
            this.broadcast("\u00a77Prochaine course : \u00a7b" + nc.name + " \u00a77(" + this.laps + (this.laps > 1 ? " tours" : " tour") + ").");
            EemiBoatRace.this.getLogger().info("Partie " + this.n + " : circuit " + oc.name + " -> " + nc.name + ".");
            if (wasCopy) {
                Bukkit.getScheduler().runTaskLater((Plugin)EemiBoatRace.this, () -> WorldClone.destroy((Plugin)EemiBoatRace.this, old, oc.worldName, EemiBoatRace.this::sendToHub), 20L);
            }
        }

        void forceStop() {
            if (this.state == State.PLAYING) {
                this.endGame(false);
            }
            if (this.state == State.ENDING) {
                this.backToLobby();
            }
            if (this.state == State.STARTING) {
                this.state = State.WAITING;
            }
        }

        void checkEmpty() {
            if (this.n == 1 || this.destroyed) {
                return;
            }
            Bukkit.getScheduler().runTask((Plugin)EemiBoatRace.this, () -> {
                if (!this.destroyed && (this.state == State.WAITING || this.state == State.STARTING) && this.players.isEmpty()) {
                    EemiBoatRace.this.destroyGame(this);
                }
            });
        }

        void add(Player p) {
            if (this.players.isEmpty() && this.state == State.WAITING && EemiBoatRace.this.getConfig().getBoolean("rotation", true)) {
                // 2.4.1 : partie vide = tirage uniforme parmi les circuits libres + l'actuel, sans le dernier joue
                Course nc = EemiBoatRace.this.pickCourse(this, false);
                if (nc != null && nc != this.c) {
                    this.switchCourse(nc);
                }
            }
            UUID id = p.getUniqueId();
            Game sg = EemiBoatRace.this.spectating.remove(id);
            if (sg != null) {
                sg.spectators.remove(id);
            }
            this.players.add(id);
            EemiBoatRace.this.byPlayer.put(id, this);
            this.toWaiting(p);
            this.broadcast("\u00a7a" + p.getName() + " \u00a77a rejoint la course (\u00a7f" + this.players.size() + "/" + EemiBoatRace.this.maxPlayers() + "\u00a77).");
            EemiBoatRace.this.reportStatus();
        }

        void toWaiting(Player p) {
            Location l = this.lobbySpawn();
            p.teleport(l);
            EemiBoatRace.this.later(() -> {
                if (!p.isOnline() || !this.players.contains(p.getUniqueId()) || this.destroyed) {
                    return;
                }
                if (!p.getWorld().equals(this.world)) {
                    return;
                }
                EemiBoatRace.this.resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(l);
                p.getInventory().setItem(8, EemiBoatRace.this.kit(EemiBoatRace.this.named(new ItemStack(Material.RED_BED), "Retour au lobby", NamedTextColor.RED)));
                this.updateSidebar(p);
            });
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!this.players.remove(id)) {
                return;
            }
            EemiBoatRace.this.byPlayer.remove(id, this);
            Racer r = this.racers.remove(id);
            if (r != null) {
                EemiBoatRace.this.removeBoat(r, p);
                if (this.state == State.PLAYING && r.finish < 0L) {
                    this.broadcast("\u00a7c" + p.getName() + " \u00a77a abandonne.");
                }
            }
            this.boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) {
                EemiBoatRace.this.sendToHub(p);
            }
            EemiBoatRace.this.exitAllowed.remove(id);
            if (this.state == State.PLAYING && this.racers.isEmpty()) {
                this.endGame(false);
            }
            EemiBoatRace.this.reportStatus();
            this.checkEmpty();
        }

        void addSpectator(Player p) {
            this.spectators.add(p.getUniqueId());
            EemiBoatRace.this.spectating.put(p.getUniqueId(), this);
            p.teleport(this.spectatorSpot());
            if (!p.getWorld().equals(this.world)) {
                this.spectators.remove(p.getUniqueId());
                EemiBoatRace.this.spectating.remove(p.getUniqueId(), this);
                return;
            }
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage("\u00a7c[\u00a7eMario Barque\u00a7c] \u00a7r\u00a77Tu regardes la course " + this.n + ", \u00a7f/lobby \u00a77pour revenir.");
        }

        int waitingCount() {
            int w = 0;
            for (UUID id : this.players) {
                Racer r = this.racers.get(id);
                if (this.running() && r != null) continue;
                ++w;
            }
            return w;
        }

        int playingCount() {
            int c = 0;
            for (UUID id : this.players) {
                Racer r = this.racers.get(id);
                if (!this.running() || r == null || this.state != State.ENDING && r.finish >= 0L) continue;
                ++c;
            }
            return c;
        }

        Scoreboard board(Player p) {
            Scoreboard b = this.boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("br", Criteria.DUMMY, ((TextComponent)Component.text((String)"MARIO ", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD)).append(Component.text((String)"BARQUE", (TextColor)NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                this.boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) {
                p.setScoreboard(b);
            }
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = this.board(p);
            Objective o = b.getObjective("br");
            for (int i = 0; i < 15; ++i) {
                String entry = "\u00a7" + Integer.toHexString(i) + "\u00a7r";
                Team t = b.getTeam("line" + i);
                if (i < lines.size()) {
                    if (t == null) {
                        t = b.registerNewTeam("line" + i);
                        t.addEntry(entry);
                    }
                    t.prefix(lines.get(i));
                    o.getScore(entry).setScore(lines.size() - i);
                    continue;
                }
                if (t == null) continue;
                b.resetScores(entry);
                t.unregister();
            }
        }

        void updateSidebar(Player p) {
            ArrayList<Component> l = new ArrayList<Component>();
            l.add((Component)Component.text((String)" "));
            Racer me = this.racers.get(p.getUniqueId());
            if (me == null || this.state != State.PLAYING) {
                l.add(Component.text((String)"Partie : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)("#" + this.n), (TextColor)NamedTextColor.WHITE)));
                l.add(Component.text((String)"Circuit : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)this.c.name, (TextColor)NamedTextColor.AQUA)));
                l.add(Component.text((String)"Joueurs : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.players.size() + "/" + EemiBoatRace.this.maxPlayers()), (TextColor)NamedTextColor.WHITE)));
                l.add(Component.text((String)"Minimum : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.valueOf(EemiBoatRace.this.cfg("min-players", 2)), (TextColor)NamedTextColor.WHITE)));
                l.add(Component.text((String)"Tours : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.valueOf(this.laps), (TextColor)NamedTextColor.WHITE)));
                l.add((Component)Component.text((String)"  "));
                if (this.state == State.STARTING) {
                    l.add(Component.text((String)"Depart dans ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.timer + " s"), (TextColor)NamedTextColor.AQUA)));
                } else if (this.state == State.WAITING) {
                    l.add((Component)Component.text((String)"En attente...", (TextColor)NamedTextColor.YELLOW));
                } else if (this.state == State.ENDING) {
                    l.add((Component)Component.text((String)"Fin de course", (TextColor)NamedTextColor.GOLD));
                } else {
                    l.add((Component)Component.text((String)"Course en cours...", (TextColor)NamedTextColor.YELLOW));
                    l.add((Component)Component.text((String)"Spectateur", (TextColor)NamedTextColor.DARK_GRAY));
                }
            } else {
                List<Racer> rank = this.ranking();
                int lap = Math.min(this.laps, me.passed / this.checkpoints + 1);
                int pos = rank.indexOf(me) + 1;
                long now = System.currentTimeMillis();
                long el = this.phase == Phase.RACE ? now - this.raceStart : 0L;
                l.add(Component.text((String)"Tour : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(me.finish >= 0L ? "arrive" : lap + "/" + this.laps), (TextColor)NamedTextColor.WHITE)));
                l.add(Component.text((String)"Position : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(pos + EemiBoatRace.ordinal(pos) + "/" + rank.size()), (TextColor)NamedTextColor.GOLD)));
                l.add(Component.text((String)"Temps : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)EemiBoatRace.time(me.finish >= 0L ? me.finish : el), (TextColor)NamedTextColor.WHITE)));
                l.add(Component.text((String)"Meilleur tour : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(me.bestLap >= 0L ? EemiBoatRace.time(me.bestLap) : "-"), (TextColor)NamedTextColor.AQUA)));
                if (this.grace > 0) {
                    l.add(Component.text((String)"Fin dans ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.grace + " s"), (TextColor)NamedTextColor.RED)));
                }
                l.add((Component)Component.text((String)"  "));
                int shown = 0;
                for (Racer r : rank) {
                    if (shown >= 6) break;
                    String info = r.finish >= 0L ? EemiBoatRace.time(r.finish) : "T" + Math.min(this.laps, r.passed / this.checkpoints + 1);
                    NamedTextColor c = r == me ? NamedTextColor.YELLOW : NamedTextColor.WHITE;
                    l.add(((TextComponent)Component.text((String)(++shown + ". "), (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)r.name, (TextColor)c))).append((Component)Component.text((String)(" " + info), (TextColor)NamedTextColor.DARK_GRAY)));
                }
            }
            l.add((Component)Component.text((String)"   "));
            l.add(EemiBoatRace.this.recordLine(this));
            l.add((Component)Component.text((String)"eemicraft", (TextColor)NamedTextColor.DARK_GRAY));
            this.setLines(p, l);
        }
    }

    private static final class VoidGenerator
    extends ChunkGenerator {
        private VoidGenerator() {
        }

        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 141.0, 0.5);
        }
    }

    private record Circuit(String name, String world, int floor, int lobbyY, double fx, double fz, int laps, int checkpoints, int gridCols) {
    }

    static final class Racer {
        final UUID id;
        final String name;
        EntityType boatType;
        UUID boat;
        int slot;
        int idx;
        double s;
        double dist;
        int passed;
        long lapStart;
        long bestLap = -1L;
        long finish = -1L;
        int place;
        boolean respawning;
        int offBoatTicks;
        double stuckRef;
        int stuckSec;
        long lastRespawn;
        Kart.Item item;
        int rollTicks;
        int boost;
        int turbo;
        /** Recompenses EemiCore deja donnees (a l'arrivee). */
        boolean rewarded;
        int stun;
        int star;
        double carry;

        Racer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static enum State {
        WAITING,
        STARTING,
        PLAYING,
        ENDING;

    }

    private record Chunk0(int x, int z) {
        static final List<Chunk0> ALL = List.of(new Chunk0(-1, -1), new Chunk0(-1, 0), new Chunk0(0, -1), new Chunk0(0, 0));
    }

    static enum Phase {
        NONE,
        GRID,
        RACE;

    }
}

