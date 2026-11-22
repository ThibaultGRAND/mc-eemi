package fr.eemicraft.hungergames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.structure.Structure;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Hunger Games d'EEMICRAFT, sur les maps Survival Games de Quillmark (« Desolate Desert », « Ruins of
 * Altrac »), tirées au hasard à chaque partie. Chacun pour soi, une seule vie : départ dans des cages
 * autour de la corne d'abondance, 15 s d'attente, ruée vers les coffres, 30 s sans PvP, bordure qui se
 * resserre, puis deathmatch empoisonné. Le dernier survivant gagne. Les coffres de chaque map sont remplis à
 * chaque partie avec les tables de loot de la map, installées par le plugin dans un datapack sans fonctions.
 * Les datapacks d'origine ne sont pas utilisés (ils agiraient sur tout le serveur) : règles, coordonnées et
 * événements des maps (porte de la bibliothèque, zones mortelles, eau bouillante...) sont repris ici.
 * <p>
 * Plusieurs parties en même temps (max-instances) : la partie 1 joue dans le monde d'origine de sa map
 * (« hungergames », « hg_altrac »), la partie n ≥ 2 dans une copie « monde_n » faite depuis « monde_modele »
 * (copie propre faite au démarrage), détruite quand elle ne sert plus.
 */
public final class EemiHungerGames extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§cHunger Games§8] §7";
    private static final String NS = "eemihg";
    /** Délai avant de réutiliser le nom d'une copie détruite (suppression des fichiers en cours). */
    private static final long REUSE_DELAY_MS = 30_000;

    enum State { PREPARING, WAITING, STARTING, CAGES, PLAYING, ENDING }

    /** Boîte de positions (bornes incluses, en coordonnées décimales). */
    private record Box(double x1, double y1, double z1, double x2, double y2, double z2) {
        boolean contains(Location l) {
            return l.getX() >= x1 && l.getX() <= x2 && l.getY() >= y1 && l.getY() <= y2 && l.getZ() >= z1 && l.getZ() <= z2;
        }
    }

    /** Coffre (ou enclume) de la map : position, orientation (« keep » = coffre de la map), qualité, chance. */
    private record ChestSpot(int x, int y, int z, String facing, String quality, double chance) {}

    /** Une map (section maps.<id> du config). world = monde d'origine ; les positions sont valables dans ses copies. */
    private static final class GameMap {
        String id, name, events, loot, restockLoot, spawnType, base;
        World world;
        Location lobby, center;
        double[] lobbyBorderCenter, borderCenter;
        int lobbyBorderSize, borderStart, borderFinal;
        double platformRadius;
        final List<Location> spawns = new ArrayList<>();
        final List<ChestSpot> spots = new ArrayList<>();
    }

    // événements des maps d'origine (prédicats des datapacks)
    private static final Box PYRAMID = new Box(-40, 91.5, -40, 41, 130, 41);
    private static final Box ALTRAC_CORNUCOPIA_TOP = new Box(-228.5, 49, 106.5, -210.5, 62, 124.5);
    private static final Box[] DEATH_BARRIERS = {
            new Box(-363, 3, 228, -322, 9, 268),
            new Box(218, 12, 122, 236, 18, 141)};
    private static final Box CALDERA = new Box(385, 159, 287, 561, 166, 452);
    private static final int[][] LIBRARY_DOOR = {{-335, 82, -296}, {-335, 83, -296}, {-372, 82, -296}, {-372, 83, -296}};

    private NamespacedKey kitKey;
    private Kits kitManager;
    private String lastStatus;
    private final Map<String, GameMap> maps = new LinkedHashMap<>();
    private Structure platform;

    /** Parties par numéro (1 = monde d'origine). */
    private final Map<Integer, Game> games = new TreeMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private final Map<UUID, Game> spectating = new HashMap<>();
    /** Modèle propre de chaque monde de map (base → monde « base_modele »). */
    private final Map<String, World> models = new HashMap<>();
    private final Set<String> modelBusy = new HashSet<>();
    private int copying;
    private final Map<String, Long> freedAt = new HashMap<>();

    /** Marqueur des coffres ouverts (TextDisplay non sauvegardé), par position de bloc de coffre. */
    private NamespacedKey markerKey;
    private final Random random = new Random();

    // ------------------------------------------------------------------ cycle de vie

    /** Avant le chargement des mondes : installe le datapack des tables de loot dans le monde principal. */
    @Override
    public void onLoad() {
        saveDefaultConfig();
        try {
            int written = installDatapack();
            if (written > 0) getLogger().info("Datapack " + NS + " installe/mis a jour (" + written + " fichiers).");
        } catch (IOException ex) {
            getLogger().severe("Installation du datapack impossible : " + ex.getMessage());
        }
    }

    private int installDatapack() throws IOException {
        File dest = new File(getServer().getWorldContainer(), getConfig().getString("datapacks-folder", "lobby/datapacks") + "/" + NS);
        String prefix = "datapack/" + NS + "/";
        int written = 0;
        Set<String> wanted = new HashSet<>();
        try (JarFile jar = new JarFile(getFile())) {
            for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements(); ) {
                JarEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
                File out = new File(dest, e.getName().substring(prefix.length()));
                wanted.add(out.getCanonicalPath());
                byte[] data;
                try (InputStream in = jar.getInputStream(e)) { data = in.readAllBytes(); }
                if (out.isFile() && Arrays.equals(Files.readAllBytes(out.toPath()), data)) continue;
                out.getParentFile().mkdirs();
                Files.write(out.toPath(), data);
                written++;
            }
        }
        // fichiers d'une ancienne version du datapack (ex. tables déplacées) : retirés
        if (dest.isDirectory()) {
            try (var walk = Files.walk(dest.toPath())) {
                for (var path : walk.sorted(Collections.reverseOrder()).toList()) {
                    File f = path.toFile();
                    if (f.isFile() && !wanted.contains(f.getCanonicalPath())) { f.delete(); written++; }
                    else if (f.isDirectory() && !f.equals(dest)) { String[] l = f.list(); if (l != null && l.length == 0) f.delete(); }
                }
            }
        }
        return written;
    }

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        markerKey = new NamespacedKey(this, "chest_marker");
        kitManager = new Kits(this, kitKey, PREFIX);
        loadSettings();
        try (InputStream in = getResource("spawn_platform.nbt")) {
            platform = Bukkit.getStructureManager().loadStructure(in);
        } catch (IOException | NullPointerException ex) {
            getLogger().severe("Plateforme de depart introuvable : " + ex.getMessage());
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(kitManager, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::fastTick, 5L, 5L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (GameMap m : maps.values()) {
                WorldClone.cleanup(this, m.world, m.base, maxInstances());
                prepareWorld(m.world, m);
                removeOrphanMarkers(m.world.getEntities());
            }
            Game g1 = new Game(1);
            games.put(1, g1);
            if (!maps.isEmpty()) g1.setMap(pickMap(null), null);
            if (!lootReady()) {
                getLogger().warning("Tables de loot " + NS + " absentes : rechargement des datapacks.");
                Bukkit.reloadData();
                getLogger().info(!lootReady() ? "Toujours absentes : verifier le datapack " + NS + "."
                        : "Tables de loot " + NS + " chargées.");
            } else getLogger().info("Tables de loot " + NS + " chargees.");
            ensureModels();
            status();
        }, 40L);
    }

    /** Chaque map chargée a sa première table de loot (sinon le datapack n'est pas chargé). */
    private boolean lootReady() {
        for (GameMap m : maps.values())
            for (ChestSpot s : m.spots)
                if (!s.quality().equals("anvil")) {
                    if (lootTable(m.loot + s.quality()) == null) return false;
                    break;
                }
        return true;
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) {
            for (Player p : online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (g.inGame.contains(p.getUniqueId()) || isHgWorld(p.getWorld())) sendToHub(p);
            }
            for (Player p : online(g.spectators)) g.stopSpectating(p);
            g.clearMarkers();
            // les copies seront supprimées au démarrage : seule la partie 1 est remise en état
            if (g.n == 1) g.restoreBlocks();
        }
    }

    private int maxInstances() {
        return Math.max(1, getConfig().getInt("max-instances", 3));
    }

    /** Charge les maps du config (section « maps », celle du jar si le config du serveur n'en a pas). */
    private void loadSettings() {
        reloadConfig();
        kitManager.load();
        maps.clear();
        // un config de serveur sans section « maps » rend une section vide (pas celle du jar) : on prend alors celle du jar
        var sec = getConfig().getConfigurationSection("maps");
        if ((sec == null || sec.getKeys(false).isEmpty()) && getConfig().getDefaults() != null)
            sec = getConfig().getDefaults().getConfigurationSection("maps");
        if (sec == null) { getLogger().severe("Aucune map dans le config."); return; }
        for (String id : sec.getKeys(false)) {
            var c = sec.getConfigurationSection(id);
            GameMap m = new GameMap();
            m.id = id;
            m.name = c.getString("name", id);
            m.base = c.getString("world", id);
            m.world = Bukkit.getWorld(m.base);
            if (m.world == null) {
                getLogger().warning("Map " + id + " : monde " + m.base + " introuvable, ignoree.");
                continue;
            }
            m.lobby = loc(m.world, c.getString("lobby"));
            m.center = loc(m.world, c.getString("center"));
            m.lobbyBorderCenter = c.isString("lobby-border-center") ? xz(c.getString("lobby-border-center")) : null;
            m.lobbyBorderSize = c.getInt("lobby-border-size", 500);
            m.borderCenter = xz(c.getString("border-center", m.center.getX() + "," + m.center.getZ()));
            m.borderStart = c.getInt("border-start-size", 1000);
            m.borderFinal = c.getInt("border-final-size", 40);
            m.spawnType = c.getString("spawn-type", "platforms");
            m.platformRadius = c.getDouble("platform-radius", 31);
            for (String s : c.getStringList("spawns")) m.spawns.add(loc(m.world, s));
            m.loot = c.getString("loot", "");
            m.restockLoot = c.getString("restock-loot", "");
            m.events = c.getString("events", "none");
            loadChests(m, c.getString("chests", "chests_" + id + ".yml"));
            maps.put(id, m);
            getLogger().info("Map " + m.name + " : " + m.spots.size() + " coffres/enclumes, monde " + m.base + ".");
        }
    }

    private void loadChests(GameMap m, String file) {
        InputStream in = getResource(file);
        if (in == null) { getLogger().warning("Map " + m.id + " : " + file + " introuvable."); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String s : y.getStringList("chests")) {
            String[] p = s.split(" ");
            m.spots.add(new ChestSpot(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[4], Double.parseDouble(p[5])));
        }
    }

    /** Map au hasard (pas deux fois de suite la même s'il y en a plusieurs). */
    private GameMap pickMap(String last) {
        List<GameMap> l = new ArrayList<>(maps.values());
        if (l.isEmpty()) return null;
        if (l.size() > 1) l.removeIf(m -> m.id.equals(last));
        return l.get(random.nextInt(l.size()));
    }

    /** Map d'une nouvelle copie : modèle prêt, de préférence une map qu'aucune autre partie n'utilise. */
    private GameMap pickCopyMap() {
        List<GameMap> ready = new ArrayList<>();
        for (GameMap m : maps.values()) if (models.get(m.base) != null && !modelBusy.contains(m.base)) ready.add(m);
        if (ready.isEmpty()) return null;
        List<GameMap> unused = new ArrayList<>(ready);
        for (Game g : games.values()) if (g.map != null) unused.removeIf(m -> m.id.equals(g.map.id));
        List<GameMap> l = unused.isEmpty() ? ready : unused;
        return l.get(random.nextInt(l.size()));
    }

    /** Monde des Hunger Games : monde d'origine d'une map, une de ses copies ou son modèle. */
    private boolean isHgWorld(World w) {
        if (w == null) return false;
        String name = w.getName();
        for (GameMap m : maps.values())
            if (WorldClone.index(m.base, name) > 0 || name.equals(m.base + "_modele")) return true;
        return false;
    }

    /** Partie qui se joue dans ce monde (null : aucune, ex. modèle ou copie en préparation). */
    private Game gameAt(World w) {
        if (w == null) return null;
        for (Game g : games.values()) if (w.equals(g.world)) return g;
        return null;
    }

    /** Règles du monde reprises des datapacks : jour fixe, pas de mobs, régénération naturelle. */
    private void prepareWorld(World w, GameMap m) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, false);
        w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        w.setGameRule(GameRules.FALL_DAMAGE, true);
        w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.LOCATOR_BAR, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setSpawnLocation(at(m.lobby, w));
    }

    /** Même position dans un autre monde (copie de la map). */
    private static Location at(Location l, World w) {
        Location c = l.clone();
        c.setWorld(w);
        return c;
    }

    // ------------------------------------------------------------------ modèles et copies

    /** Fait les modèles manquants (un par monde de map). */
    private void ensureModels() {
        Set<String> bases = new LinkedHashSet<>();
        for (GameMap m : maps.values()) bases.add(m.base);
        for (String b : bases) if (!models.containsKey(b) && !modelBusy.contains(b)) remakeModel(b);
    }

    /**
     * (Re)fait la copie propre « base_modele » du monde d'origine (≈ 210 Mo par monde HG, copie asynchrone).
     * Attend qu'aucune copie ne soit en cours et que la partie 1 ne joue pas dans ce monde.
     */
    private void remakeModel(String base) {
        World src = Bukkit.getWorld(base);
        if (src == null || !isEnabled()) return;
        Game g1 = games.get(1);
        if (copying > 0 || modelBusy.contains(base) || (g1 != null && src.equals(g1.world) && g1.running())) {
            Bukkit.getScheduler().runTaskLater(this, () -> remakeModel(base), 100L);
            return;
        }
        String name = base + "_modele";
        models.remove(base);
        World old = Bukkit.getWorld(name);
        if (old != null) {
            for (Player p : old.getPlayers()) sendToHub(p);
            if (!Bukkit.unloadWorld(old, false)) {
                getLogger().warning("Impossible de decharger le modele " + name + ".");
                return;
            }
        }
        modelBusy.add(base);
        long t0 = System.currentTimeMillis();
        WorldClone.create(this, src, name, w -> {
            modelBusy.remove(base);
            if (w == null) {
                getLogger().warning("Modele " + name + " impossible : pas de parties supplementaires sur ce monde.");
                return;
            }
            for (GameMap m : maps.values()) if (m.base.equals(base)) { prepareWorld(w, m); break; }
            models.put(base, w);
            getLogger().info("Modele " + name + " pret en " + (System.currentTimeMillis() - t0) + " ms.");
        });
    }

    /** Numéro libre pour une nouvelle partie (2..max-instances), ou -1. */
    private int freeIndex() {
        long now = System.currentTimeMillis();
        for (int n = 2; n <= maxInstances(); n++) {
            if (games.containsKey(n)) continue;
            boolean ok = true;
            for (GameMap m : maps.values()) {
                String name = WorldClone.name(m.base, n);
                Long t = freedAt.get(name);
                if (Bukkit.getWorld(name) != null || (t != null && now - t < REUSE_DELAY_MS)) { ok = false; break; }
            }
            if (ok) return n;
        }
        return -1;
    }

    /** Supprime une partie n ≥ 2 et sa copie de monde. */
    private void destroyGame(Game g) {
        if (g.n == 1 || g.destroyed) return;
        g.destroyed = true;
        games.remove(g.n);
        g.clearMarkers();
        for (UUID id : g.players) byPlayer.remove(id);
        for (Player p : online(g.players)) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        for (UUID id : g.spectators) spectating.remove(id);
        g.players.clear();
        g.spectators.clear();
        World w = g.world;
        if (w != null) {
            freedAt.put(w.getName(), System.currentTimeMillis());
            String base = g.map.base;
            Bukkit.getScheduler().runTask(this, () -> WorldClone.destroy(this, w, base, this::sendToHub));
        }
        status();
    }

    // ------------------------------------------------------------------ utilitaires de config

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private static double[] xz(String s) {
        String[] p = s.split(",");
        return new double[]{Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim())};
    }

    /** « x,y,z[,yaw[,pitch]] » */
    private static Location loc(World w, String s) {
        String[] p = s.split(",");
        Location l = new Location(w, Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
        if (p.length >= 4) l.setYaw(Float.parseFloat(p[3].trim()));
        if (p.length >= 5) l.setPitch(Float.parseFloat(p[4].trim()));
        return l;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int kx(long k) {
        return (int) (k >> 38) << 6 >> 6;
    }

    private static int kz(long k) {
        return (int) ((k >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    private static int ky(long k) {
        return (int) (k & 0xFFF) << 20 >> 20;
    }

    /** Table de loot du datapack eemihg, ex. « desert/chests/base/common ». */
    private LootTable lootTable(String path) {
        return Bukkit.getLootTable(new NamespacedKey(NS, path));
    }

    private static String clock(int s) {
        s = Math.max(0, s);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ boucles

    private void tick() {
        for (Game g : new ArrayList<>(games.values())) if (!g.destroyed) g.tick();
        status();
    }

    private void fastTick() {
        for (Game g : new ArrayList<>(games.values())) g.fastTick();
    }

    // ------------------------------------------------------------------ EemiCore

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /** Statut agrégé de toutes les parties envoyé à EemiCore quand il change. */
    private void status() {
        boolean wait = false, play = false;
        int waiting = 0, ingame = 0;
        for (Game g : games.values()) {
            if (g.state == State.WAITING || g.state == State.STARTING || g.state == State.PREPARING) wait = true;
            if (g.state == State.CAGES || g.state == State.PLAYING) play = true;
            for (UUID id : g.players) if (!g.inGame.contains(id)) waiting++;
            ingame += g.alive.size();
        }
        String st = wait ? "waiting" : play ? "playing" : "waiting";
        String s = "status hungergames " + st + " " + waiting + " " + ingame + " " + cfg("max-players", 24) * maxInstances();
        if (s.equals(lastStatus)) return;
        lastStatus = s;
        eemi(s);
    }

    // ------------------------------------------------------------------ inscription

    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (byPlayer.containsKey(id)) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (maps.isEmpty() || games.isEmpty()) { p.sendMessage(PREFIX + "§cLa map n'est pas chargée."); return; }
        Game spec = spectating.get(id);
        if (spec != null) spec.stopSpectating(p);
        int max = cfg("max-players", 24);
        // partie en attente (ou en décompte) qui a de la place, la plus remplie d'abord
        Game best = null;
        for (Game g : games.values())
            if (g.joinable() && g.players.size() < max && (best == null || g.players.size() > best.players.size())) best = g;
        if (best != null) { best.add(p); return; }
        int n = freeIndex();
        GameMap m = n < 0 ? null : pickCopyMap();
        if (n < 0 || m == null) {
            p.sendMessage(PREFIX + "§cToutes les parties sont pleines, réessaie dans un instant.");
            return;
        }
        Game g = new Game(n);
        g.map = m;
        g.state = State.PREPARING;
        games.put(n, g);
        g.players.add(id);
        byPlayer.put(id, g);
        p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie... §8(map " + m.name + ", quelques secondes)");
        copying++;
        long t0 = System.currentTimeMillis();
        WorldClone.create(this, models.get(m.base), WorldClone.name(m.base, n), w -> {
            copying--;
            if (w != null) getLogger().info("Partie " + n + " : copie prete en " + (System.currentTimeMillis() - t0) + " ms.");
            g.ready(w);
        });
        status();
    }

    /** Spectateur : partie de ce joueur, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String targetName) {
        UUID id = p.getUniqueId();
        if (byPlayer.containsKey(id)) { p.sendMessage(PREFIX + "§cTu es inscrit : quitte les Hunger Games pour regarder."); return; }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : byPlayer.get(t.getUniqueId());
            if (g == null) { p.sendMessage(PREFIX + "§cCe joueur n'est pas dans une partie des Hunger Games."); return; }
            if (!g.running()) { p.sendMessage(PREFIX + "§cSa partie n'a pas commencé."); return; }
        } else
            for (Game c : games.values())
                if (c.running() && (g == null || c.alive.size() > g.alive.size())) g = c;
        if (g == null) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return; }
        Game old = spectating.get(id);
        if (old != null && old != g) { old.spectators.remove(id); spectating.remove(id); }
        g.spectators.add(id);
        spectating.put(id, g);
        p.teleport(g.center.clone().add(0, 15, 0));
        if (p.getWorld().equals(g.world)) p.setGameMode(GameMode.SPECTATOR); // seulement une fois arrivé
        p.sendMessage(PREFIX + "§7Tu regardes la partie " + g.n + ", §f/lobby §7pour revenir.");
    }

    /**
     * Renvoi au hub : seulement depuis un monde des Hunger Games ou le hub (jamais depuis le skyblock ou un autre
     * jeu). Téléportation d'abord (Multiverse-Inventories garde l'inventaire du monde quitté), puis remise à zéro.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
        if (hub != null) p.teleport(hub.getSpawnLocation());
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        p.setInvulnerable(false);
    }

    /** Monde du hub (config « hub-world »). */
    private boolean isHub(World w) {
        return w != null && w.getName().equals(getConfig().getString("hub-world", "lobby"));
    }

    /**
     * On ne touche à l'inventaire / l'état d'un joueur que s'il est dans un monde des Hunger Games (map, copie) ou
     * au hub : ailleurs (skyblock...), Multiverse-Inventories a déjà chargé l'inventaire de ce monde-là.
     */
    private boolean mayTouch(Player p) {
        return p != null && p.isOnline() && (isHgWorld(p.getWorld()) || isHub(p.getWorld()));
    }

    private void resetPlayer(Player p, GameMode mode) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    // ================================================================== une partie

    final class Game {
        final int n;
        GameMap map;
        World world;
        Location lobby, center;
        State state = State.WAITING;
        boolean destroyed;
        String lastMap;
        /** Monde des blocs mémorisés dans « original » (la map de la partie qui vient de finir). */
        World originalWorld;

        final Set<UUID> players = new LinkedHashSet<>();
        /** Non-inscrits qui regardent la partie en spectateur (/hungergames spectate). */
        final Set<UUID> spectators = new HashSet<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> inGame = new HashSet<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        final Map<UUID, Location> lastLoc = new HashMap<>();
        final Set<UUID> outOfBounds = new HashSet<>();
        /** Blocs modifiés (plateformes, porte, blocs posés, liquides) : état d'origine, remis en fin de partie. */
        final Map<Long, BlockData> original = new LinkedHashMap<>();
        final List<Block> cornucopiaChests = new ArrayList<>();
        /** Blocs posés par les joueurs pendant la partie : les seuls qu'on peut casser. */
        final Set<Long> placed = new HashSet<>();
        final Map<Long, TextDisplay> markers = new HashMap<>();
        /** Coffres pas encore ouverts : niveau du bonus de loot ajouté à la première ouverture. */
        final Map<Long, Integer> bonusTier = new HashMap<>();

        int timer, elapsed, total;
        int deathmatch = -1;
        boolean chestsReady, libraryOpen;
        /** Seconde de partie où la bordure atteint sa taille finale (-1 : pas encore en mouvement). */
        int borderEnd = -1;
        /** Accélération de la bordure déjà appliquée : 0 aucune, 1 peu de survivants, 2 duel. */
        int rushLevel;

        Game(int n) {
            this.n = n;
        }

        boolean running() {
            return state == State.CAGES || state == State.PLAYING || state == State.ENDING;
        }

        boolean joinable() {
            return !destroyed && map != null && (state == State.WAITING || state == State.STARTING || state == State.PREPARING);
        }

        /** Change de map (partie 1 : monde d'origine de la map ; copie : son monde w). */
        void setMap(GameMap m, World w) {
            map = m;
            if (m == null) { world = null; lobby = center = null; return; }
            world = n == 1 ? m.world : w;
            lobby = at(m.lobby, world);
            center = at(m.center, world);
            lobbyBorder();
        }

        /** Copie du monde prête (ou null si la copie a échoué) : les inscrits vont dans le lobby de la map. */
        void ready(World w) {
            if (destroyed) {
                if (w != null) Bukkit.getScheduler().runTask(EemiHungerGames.this,
                        () -> WorldClone.destroy(EemiHungerGames.this, w, map.base, EemiHungerGames.this::sendToHub));
                return;
            }
            if (w == null) {
                for (Player p : online(players)) {
                    p.sendMessage(PREFIX + "§cImpossible de préparer la partie, réessaie plus tard.");
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                for (UUID id : players) byPlayer.remove(id);
                players.clear();
                destroyed = true;
                games.remove(n);
                status();
                return;
            }
            prepareWorld(w, map);
            state = State.WAITING;
            setMap(map, w);
            players.removeIf(id -> Bukkit.getPlayer(id) == null);
            if (players.isEmpty()) { destroyGame(this); return; }
            for (Player p : online(players)) {
                p.teleport(lobby);
                later(() -> prepareLobbyPlayer(p));
            }
            broadcast("§7Partie prête (§6" + map.name + "§7) : §f" + players.size() + "§7 inscrit(s).");
            status();
        }

        /** Bordure d'attente : autour du lobby s'il est loin de l'arène, sinon la bordure de départ de l'arène. */
        void lobbyBorder() {
            if (map == null || world == null || running()) return;
            WorldBorder b = world.getWorldBorder();
            if (map.lobbyBorderCenter != null) {
                b.setCenter(map.lobbyBorderCenter[0], map.lobbyBorderCenter[1]);
                b.setSize(map.lobbyBorderSize);
            } else {
                b.setCenter(map.borderCenter[0], map.borderCenter[1]);
                b.setSize(map.borderStart);
            }
        }

        // -------------------------------------------------------------- boucles

        void tick() {
            switch (state) {
                case WAITING -> {
                    if (players.size() >= cfg("min-players", 2)) {
                        state = State.STARTING;
                        timer = cfg("countdown", 30);
                        broadcast("§eAssez de joueurs : la partie commence dans §6" + timer + " s§e.");
                    }
                }
                case STARTING -> {
                    if (players.size() < cfg("min-players", 2)) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                    } else {
                        if (players.size() >= cfg("max-players", 24)) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0) for (Player p : online(players)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case CAGES -> {
                    if (timer > 0) timer--;
                    List<Player> list = online(alive);
                    if (timer <= 0 && chestsReady) release();
                    else if (timer <= 0) for (Player p : list) p.sendActionBar(Component.text("Préparation des coffres...", NamedTextColor.GOLD));
                    else {
                        for (Player p : list) {
                            if (timer <= 5) {
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        }
                    }
                }
                case PLAYING -> {
                    elapsed++;
                    perSecondGame();
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
                case PREPARING -> { }
            }
            if (!destroyed) for (Player p : online(players)) updateSidebar(p);
        }

        /** Une seconde de partie : phases (grâce, bordure, deathmatch), réapprovisionnement, boussole, eau bouillante. */
        void perSecondGame() {
            // un seul survivant (abandons pendant les cages, déconnexions) : il gagne
            if (alive.size() <= 1) {
                endGame(alive.isEmpty() ? null : Bukkit.getPlayer(alive.iterator().next()), true);
                return;
            }
            int grace = cfg("grace-period", 30), delay = cfg("border-delay", 300), shrink = cfg("border-shrink-time", 600);
            if (elapsed == grace) {
                broadcast("§cLa période de grâce est terminée : le PvP est actif ! §7Tu peux maintenant poser des blocs partout.");
                for (Player p : online(alive)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
                    // survie pour pouvoir poser des blocs (la casse reste limitée aux blocs posés par les joueurs)
                    if (p.getWorld().equals(world)) p.setGameMode(GameMode.SURVIVAL);
                }
            }
            if (elapsed == delay && borderEnd < 0) {
                world.getWorldBorder().setSize(map.borderFinal, shrink);
                borderEnd = delay + shrink;
                broadcast("§cLa bordure se resserre ! §7Elle atteindra §f" + map.borderFinal + " blocs §7en §f" + shrink / 60 + " min§7.");
                for (Player p : online(alive)) p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
            }
            borderRush(grace, delay, shrink);
            if (cfg("restock-time", 600) > 0 && elapsed == cfg("restock-time", 600)) restock();
            if (deathmatch < 0 && borderEnd >= 0 && elapsed >= borderEnd) {
                deathmatch = cfg("deathmatch-time", 120);
                broadcast("§4Deathmatch ! §7Fin de la partie dans §f" + deathmatch + " s§7, le poison arrive...");
            }
            if (deathmatch >= 0) {
                if (deathmatch == cfg("poison-1-at", 90)) poison(0);
                if (deathmatch == cfg("poison-2-at", 45)) poison(1);
                if (deathmatch == 0) { timeExpired(); return; }
                deathmatch--;
            }
            for (Player p : online(alive)) {
                lastLoc.put(p.getUniqueId(), p.getLocation());
                if (getConfig().getBoolean("tracker-compass", true) && elapsed >= grace) track(p);
                if (map.events.equals("desert") && p.getWorld().equals(world) && CALDERA.contains(p.getLocation()) && p.isInWater()) p.damage(1);
            }
        }

        /**
         * Bordure accélérée quand il reste peu de survivants (voir border-rush.* du config). La bordure est celle
         * du monde de cette partie.
         */
        void borderRush(int grace, int delay, int shrink) {
            if (!getConfig().getBoolean("border-rush.enabled", true) || deathmatch >= 0) return;
            int stage = alive.size() <= 2 ? 2 : alive.size() <= cfg("border-rush.players", 4) ? 1 : 0;
            if (stage <= rushLevel || elapsed < Math.max(grace, cfg("border-rush.min-time", 120))) return;
            rushLevel = stage;
            WorldBorder b = world.getWorldBorder();
            double size = b.getSize();
            if (size <= map.borderFinal + 1) return;
            int want = stage == 2 ? cfg("border-rush.duel-time", 60) : cfg("border-rush.time", 120);
            double speed = Math.max(0.5, getConfig().getDouble("border-rush.max-speed", 4.0));
            int dur = Math.max(want, (int) Math.ceil((size - map.borderFinal) / speed));
            int remaining = borderEnd < 0 ? delay - elapsed + shrink : borderEnd - elapsed;
            if (dur >= remaining) return;
            b.setSize(map.borderFinal, dur);
            borderEnd = elapsed + dur;
            broadcast((stage == 2 ? "§cPlus que 2 survivants" : "§cPlus que " + alive.size() + " survivants")
                    + " : la bordure accélère ! §7Elle atteindra §f" + map.borderFinal + " blocs §7dans §f" + clock(dur) + "§7.");
            for (Player p : online(alive)) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
                p.sendActionBar(Component.text("La bordure accélère !", NamedTextColor.RED));
            }
        }

        void poison(int amp) {
            for (Player p : online(alive))
                if (p.getWorld().equals(world)) p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, PotionEffect.INFINITE_DURATION, amp, false, true, true));
            broadcast("§2Poison " + (amp == 0 ? "I" : "II") + " §7pour tous les survivants !");
        }

        /** Toutes les 5 ticks : zones mortelles (désert) et zone interdite (Wither) au-dessus de la corne. */
        void fastTick() {
            if (state != State.PLAYING || destroyed) return;
            boolean desert = map.events.equals("desert");
            Box forbidden = desert ? PYRAMID : map.events.equals("altrac") ? ALTRAC_CORNUCOPIA_TOP : null;
            for (Player p : online(alive)) {
                Location l = p.getLocation();
                if (!l.getWorld().equals(world)) continue;
                if (desert) for (Box b : DEATH_BARRIERS) if (b.contains(l)) { p.setHealth(0); break; }
                boolean oob = forbidden != null && forbidden.contains(l);
                if (oob && outOfBounds.add(p.getUniqueId())) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, PotionEffect.INFINITE_DURATION, 2, false, true, true));
                    p.sendActionBar(Component.text("Zone interdite : redescends de la corne !", NamedTextColor.RED));
                } else if (!oob && outOfBounds.remove(p.getUniqueId())) p.removePotionEffect(PotionEffectType.WITHER);
            }
        }

        /** La boussole pointe vers le joueur vivant le plus proche (distance affichée quand on la tient). */
        void track(Player p) {
            Player best = null;
            double bd = Double.MAX_VALUE;
            for (Player o : online(alive)) {
                if (o == p || !o.getWorld().equals(p.getWorld())) continue;
                double d = o.getLocation().distanceSquared(p.getLocation());
                if (d < bd) { bd = d; best = o; }
            }
            if (best == null) return;
            p.setCompassTarget(best.getLocation());
            if (p.getInventory().getItemInMainHand().getType() == Material.COMPASS)
                p.sendActionBar(Component.text("Cible : ", NamedTextColor.GRAY).append(Component.text(best.getName(), NamedTextColor.RED))
                        .append(Component.text(" (" + (int) Math.sqrt(bd) + " m)", NamedTextColor.GRAY)));
        }

        // -------------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("hg", Criteria.DUMMY,
                        Component.text("HUNGER ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                                .append(Component.text("GAMES", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("hg");
            for (int i = 0; i < 15; i++) {
                String entry = "§" + Integer.toHexString(i) + "§r";
                Team t = b.getTeam("line" + i);
                if (i < lines.size()) {
                    if (t == null) {
                        t = b.registerNewTeam("line" + i);
                        t.addEntry(entry);
                    }
                    t.prefix(lines.get(i));
                    o.getScore(entry).setScore(lines.size() - i);
                } else if (t != null) {
                    b.resetScores(entry);
                    t.unregister();
                }
            }
        }

        void updateSidebar(Player p) {
            List<Component> l = new ArrayList<>();
            l.add(Component.text(" "));
            boolean playing = inGame.contains(p.getUniqueId()) && (state == State.CAGES || state == State.PLAYING || state == State.ENDING);
            if (!playing) {
                l.add(Component.text("Inscrits : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + cfg("max-players", 24), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("Map : ", NamedTextColor.GRAY).append(Component.text(map == null ? "?" : map.name, NamedTextColor.GOLD)));
                Kits.Kit kit = kitManager.selected(p);
                l.add(Component.text("Kit : ", NamedTextColor.GRAY).append(Component.text(kit == null ? "-" : kit.name(), NamedTextColor.YELLOW)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GOLD)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else if (state == State.PREPARING) l.add(Component.text("Préparation...", NamedTextColor.GOLD));
                else l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
            } else {
                int grace = cfg("grace-period", 30), delay = cfg("border-delay", 300);
                Component phase;
                if (state == State.CAGES) phase = Component.text("Départ : ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GOLD));
                else if (state == State.ENDING) phase = Component.text("Fin de la partie", NamedTextColor.GOLD);
                else if (elapsed < grace) phase = Component.text("PvP dans : ", NamedTextColor.GRAY).append(Component.text(clock(grace - elapsed), NamedTextColor.GREEN));
                else if (borderEnd < 0 && elapsed < delay) phase = Component.text("Bordure : ", NamedTextColor.GRAY).append(Component.text(clock(delay - elapsed), NamedTextColor.YELLOW));
                else if (deathmatch < 0) phase = Component.text("Bordure : ", NamedTextColor.GRAY).append(Component.text((int) world.getWorldBorder().getSize() + " blocs", NamedTextColor.RED));
                else phase = Component.text("Deathmatch : ", NamedTextColor.DARK_RED).append(Component.text(clock(deathmatch), NamedTextColor.RED));
                l.add(phase);
                l.add(Component.text("  "));
                l.add(Component.text("Map : ", NamedTextColor.GRAY).append(Component.text(map.name, NamedTextColor.GOLD)));
                l.add(Component.text("Survivants : ", NamedTextColor.GRAY).append(Component.text(alive.size() + "/" + total, NamedTextColor.GREEN)));
                l.add(Component.text("Kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                if (state != State.CAGES) l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(elapsed), NamedTextColor.WHITE)));
                if (!alive.contains(p.getUniqueId())) l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft" + (games.size() > 1 || n > 1 ? " #" + n : ""), NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        // -------------------------------------------------------------- inscription

        void add(Player p) {
            UUID id = p.getUniqueId();
            players.add(id);
            byPlayer.put(id, this);
            status();
            if (state == State.PREPARING) {
                p.sendMessage(PREFIX + "§7Une nouvelle partie se prépare, tu y seras téléporté.");
                updateSidebar(p);
                return;
            }
            broadcast("§a" + p.getName() + " §7a rejoint les Hunger Games (§f" + players.size() + "§7).");
            p.teleport(lobby);
            later(() -> prepareLobbyPlayer(p));
        }

        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || byPlayer.get(p.getUniqueId()) != this) return;
            if (!p.getWorld().equals(world)) return; // pas (ou plus) dans le monde de la partie : on n'y touche pas
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(0, kitManager.chooser());
            p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter les Hunger Games"));
            updateSidebar(p);
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            byPlayer.remove(id);
            if (alive.contains(id) && (state == State.PLAYING || state == State.CAGES)) {
                // abandon en pleine partie = éliminé, ses objets restent sur place
                // seulement s'il est encore dans le monde de la partie : sinon son inventaire est déjà celui
                // d'un autre monde (skyblock...), Multiverse-Inventories a gardé celui de la partie
                Location at = p.getWorld().equals(world) ? p.getLocation() : null;
                if (at != null && state == State.PLAYING)
                    for (ItemStack it : p.getInventory().getContents()) if (it != null && !isKit(it)) world.dropItemNaturally(at, it);
                eliminate(p, null, "§7a abandonné");
            }
            inGame.remove(id);
            alive.remove(id);
            boards.remove(id);
            outOfBounds.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) sendToHub(p);
            // copie en attente devenue vide : on la supprime
            if (n >= 2 && players.isEmpty() && (state == State.WAITING || state == State.STARTING)) destroyGame(this);
            status();
        }

        /** Fin de partie (ou arrêt) : le spectateur retourne au hub. */
        void stopSpectating(Player p) {
            spectating.remove(p.getUniqueId());
            if (!spectators.remove(p.getUniqueId())) return;
            if (!mayTouch(p)) return; // déjà dans un autre monde : on n'y touche pas
            World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
            if (hub != null && isHgWorld(p.getWorld())) p.teleport(hub.getSpawnLocation());
            if (mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
        }

        // -------------------------------------------------------------- partie

        /** Plateformes / cages, joueurs enfermés, coffres remplis pendant l'attente. */
        void startGame() {
            List<Player> list = online(players);
            list.removeIf(p -> !p.getWorld().equals(world)); // seulement ceux qui sont dans le monde de la partie
            if (list.size() < 2 || map == null) { state = State.WAITING; return; }
            if (!lootReady()) {
                broadcast("§cLes tables de loot ne sont pas chargées, partie annulée (préviens un admin).");
                state = State.WAITING;
                return;
            }
            Collections.shuffle(list);
            alive.clear();
            inGame.clear();
            kills.clear();
            outOfBounds.clear();
            lastLoc.clear();
            restoreBlocks();
            clearEntities(world);
            elapsed = 0;
            deathmatch = -1;
            borderEnd = -1;
            rushLevel = 0;
            lastMap = map.id;
            originalWorld = world;
            WorldBorder border = world.getWorldBorder();
            border.setCenter(map.borderCenter[0], map.borderCenter[1]);
            border.setSize(map.borderStart);
            border.setDamageAmount(0.2);
            border.setDamageBuffer(0);
            border.setWarningDistance(20);
            border.setWarningTimeTicks(0);
            world.setTime(6000);
            if (map.events.equals("desert")) closeLibrary(world);
            placeChests();
            boolean cages = map.spawnType.equals("cages") && !map.spawns.isEmpty();
            if (cages && list.size() > map.spawns.size()) {
                // plus de joueurs que de points de départ : les derniers inscrits attendront la prochaine partie
                for (Player extra : list.subList(map.spawns.size(), list.size()))
                    extra.sendMessage(PREFIX + "§cPartie pleine sur cette map, tu joueras la suivante.");
                list = new ArrayList<>(list.subList(0, map.spawns.size()));
            }
            total = list.size();
            for (int i = 0; i < list.size(); i++) {
                Player p = list.get(i);
                Location spawn;
                if (cages) {
                    // points de départ de la map, dans l'ordre du datapack (les premiers sont les mieux répartis)
                    spawn = at(map.spawns.get(i), world);
                    placeCage(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
                } else {
                    double a = Math.toRadians(360.0 * i / list.size());
                    int bx = (int) Math.floor(center.getX() - Math.sin(a) * map.platformRadius);
                    int bz = (int) Math.floor(center.getZ() + Math.cos(a) * map.platformRadius);
                    int by = center.getBlockY();
                    placePlatform(bx, by, bz);
                    spawn = new Location(world, bx + 0.5, by + 1.5, bz + 0.5);
                }
                spawn.setDirection(center.toVector().subtract(spawn.toVector()).setY(0));
                alive.add(p.getUniqueId());
                inGame.add(p.getUniqueId());
                p.teleport(spawn);
                later(() -> {
                    if (!p.isOnline() || !alive.contains(p.getUniqueId())) return;
                    if (!p.getWorld().equals(world)) return; // parti ailleurs entre-temps : on n'y touche pas
                    p.closeInventory(); // menu des kits éventuellement ouvert
                    resetPlayer(p, GameMode.ADVENTURE);
                    p.teleport(spawn);
                });
            }
            state = State.CAGES;
            timer = cfg("cage-time", 15);
            broadcast("§eQue les Hunger Games commencent ! §7Départ dans §6" + timer + " s§7. Dernier survivant gagnant, "
                    + "§f" + cfg("grace-period", 30) + " s §7sans PvP au départ.");
            status();
        }

        /** Plateforme + cage de verre de la map (structure sg:player/spawn_platform), sur le bloc (x,y,z). */
        void placePlatform(int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 4; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                        original.putIfAbsent(key(b.getX(), b.getY(), b.getZ()), b.getBlockData());
                    }
            if (platform != null) platform.place(new Location(world, x - 1, y, z - 1), false, StructureRotation.NONE, Mirror.NONE, 0, 1f, random);
        }

        /** Cage de barrières (structure sg:player_cage d'Altrac) autour d'un joueur dont les pieds sont sur le bloc (x,y,z). */
        void placeCage(int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 4; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                        original.putIfAbsent(key(b.getX(), b.getY(), b.getZ()), b.getBlockData());
                    }
            world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData("minecraft:smooth_stone_slab[type=bottom]"), false);
            for (int dy = 1; dy <= 3; dy++) world.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
            for (int dy = 0; dy <= 3; dy++)
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Block b = world.getBlockAt(x + d[0], y + dy, z + d[1]);
                    if (dy == 0 && b.getType().isSolid()) continue; // le sol de la map reste
                    b.setType(Material.BARRIER, false);
                }
            world.getBlockAt(x, y + 4, z).setType(Material.BARRIER, false);
        }

        /** Ouverture des cages : on retire le verre marron (plateformes) ou les barrières (cages). */
        void release() {
            state = State.PLAYING;
            elapsed = 0;
            for (long k : new ArrayList<>(original.keySet())) {
                Block b = world.getBlockAt(kx(k), ky(k), kz(k));
                if (b.getType() == Material.BROWN_STAINED_GLASS || b.getType() == Material.BARRIER) b.setType(Material.AIR, false);
            }
            world.strikeLightningEffect(new Location(world, center.getX(), center.getY() + 2, center.getZ()));
            for (Player p : online(alive)) {
                if (!p.getWorld().equals(world)) continue; // kit seulement dans le monde de la partie
                p.setGameMode(GameMode.ADVENTURE);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.75f, 1f);
                p.showTitle(Title.title(Component.text("GO !", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                        Component.text("Que le sort vous soit favorable", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                if (getConfig().getBoolean("tracker-compass", true))
                    p.getInventory().setItem(8, named(kit(new ItemStack(Material.COMPASS)), "Traqueur (joueur le plus proche)"));
                kitManager.give(p);
            }
            broadcast("§aC'est parti ! §7Les coffres de la corne sont au centre. Le PvP commence dans §f"
                    + cfg("grace-period", 30) + " s§7.");
        }

        /** Replace / remplit les coffres et enclumes de la map selon leur chance, chunk par chunk (asynchrone). */
        void placeChests() {
            chestsReady = false;
            cornucopiaChests.clear();
            clearMarkers();
            bonusTier.clear();
            List<ChestSpot> spots = map.spots;
            World w = world;
            Map<Long, List<ChestSpot>> byChunk = new HashMap<>();
            for (ChestSpot s : spots) byChunk.computeIfAbsent(((long) (s.x() >> 4) << 32) | ((s.z() >> 4) & 0xFFFFFFFFL), k -> new ArrayList<>()).add(s);
            int[] left = {byChunk.size()};
            int[] count = {0};
            long start = System.currentTimeMillis();
            for (Map.Entry<Long, List<ChestSpot>> en : byChunk.entrySet()) {
                int cx = (int) (en.getKey() >> 32), cz = (int) (long) en.getKey();
                w.getChunkAtAsync(cx, cz).thenAccept(ch -> {
                    if (destroyed || w != world) return;
                    for (ChestSpot s : en.getValue()) if (placeChest(s)) count[0]++;
                    if (--left[0] == 0) {
                        chestsReady = true;
                        getLogger().info("Partie " + n + " : " + count[0] + " coffres/enclumes places sur " + spots.size() + " en "
                                + (System.currentTimeMillis() - start) + " ms.");
                    }
                }).exceptionally(ex -> {
                    getLogger().warning("Chunk " + cx + "," + cz + " : " + ex.getMessage());
                    if (--left[0] == 0) chestsReady = true;
                    return null;
                });
            }
            if (byChunk.isEmpty()) chestsReady = true;
        }

        boolean placeChest(ChestSpot s) {
            Block b = world.getBlockAt(s.x(), s.y(), s.z());
            if (b.getState() instanceof Container c) c.getInventory().clear();
            boolean spawn = random.nextDouble() < s.chance();
            if (!spawn) {
                b.setType(Material.AIR, false);
                return false;
            }
            if (s.quality().equals("anvil")) {
                b.setBlockData(Bukkit.createBlockData("minecraft:anvil[facing=" + s.facing() + "]"), false);
                return true;
            }
            // « keep » : coffre posé dans la map (on garde son orientation et sa forme double)
            if (!s.facing().equals("keep") || !(b.getState() instanceof Chest))
                b.setBlockData(Bukkit.createBlockData("minecraft:chest[facing=" + (s.facing().equals("keep") ? "north" : s.facing()) + "]"), false);
            if (b.getState() instanceof Chest chest) {
                chest.getBlockInventory().clear();
                chest.setLootTable(lootTable(map.loot + s.quality()), random.nextLong());
                chest.update(true, false);
                bonusTier.put(key(b.getX(), b.getY(), b.getZ()), tier(s.quality()));
                if (s.quality().startsWith("cornucopia")) cornucopiaChests.add(b);
            }
            return true;
        }

        /** Réapprovisionnement : les coffres de la corne reçoivent un loot « rare » (comme les maps). */
        void restock() {
            LootTable lt = lootTable(map.restockLoot);
            int count = 0;
            for (Block b : cornucopiaChests) {
                if (!(b.getState() instanceof Chest chest)) continue;
                chest.getBlockInventory().clear();
                chest.setLootTable(lt, random.nextLong());
                chest.update(true, false);
                // de nouveau plein : le marqueur « Ouvert / Vide » disparaît, bonus de loot à la prochaine ouverture
                long k = key(b.getX(), b.getY(), b.getZ());
                removeMarker(k);
                bonusTier.put(k, 3);
                count++;
            }
            broadcast("§6Les coffres de la corne d'abondance ont été remplis ! §7(" + count + " coffres)");
            for (Player p : online(alive)) p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 0.8f);
        }

        /** Élimination : spectateur sur place, éclair, son de canon pour les joueurs de la partie. */
        void eliminate(Player p, Player killer, String how) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            outOfBounds.remove(id);
            int place = alive.size() + 1;
            if (killer != null && killer != p) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " hungergames kill");
            }
            status();
            String msg = killer != null && killer != p
                    ? "§c" + p.getName() + " §7a été tué par §c" + killer.getName()
                    : "§c" + p.getName() + " " + how;
            broadcast(msg + " §8(" + alive.size() + " survivant" + (alive.size() > 1 ? "s" : "") + ")");
            if (p.getWorld().equals(world)) world.strikeLightningEffect(p.getLocation());
            for (Player o : online(players))
                if (inGame.contains(o.getUniqueId())) o.playSound(o.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
            if (p.isOnline())
                p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                        Component.text("Place : " + place + "/" + total, NamedTextColor.GRAY)));
            if (alive.size() <= 1 && state == State.PLAYING) {
                Player winner = alive.isEmpty() ? null : Bukkit.getPlayer(alive.iterator().next());
                Bukkit.getScheduler().runTask(EemiHungerGames.this, () -> endGame(winner, true));
            }
            if (alive.isEmpty() && state == State.CAGES) Bukkit.getScheduler().runTask(EemiHungerGames.this, () -> endGame(null, true));
        }

        /** Fin de partie ; « normal » = partie allée à son terme (récompenses EemiCore), pas un /hungergames stop. */
        void endGame(Player winner, boolean normal) {
            if (state != State.PLAYING && state != State.CAGES) return;
            state = State.ENDING;
            timer = cfg("end-time", 10);
            if (normal) {
                for (Player p : online(inGame)) eemi("reward " + p.getName() + " hungergames play");
                if (winner != null && winner.isOnline()) eemi("reward " + winner.getName() + " hungergames win");
            }
            status();
            // titres courts (titre <= 16 caractères, sous-titre <= 38) : le pseudo va dans le sous-titre
            Component sub = winner != null
                    ? Component.text(winner.getName() + " gagne !", NamedTextColor.YELLOW)
                    : Component.text("Pas de vainqueur", NamedTextColor.GRAY);
            Component mainTitle = Component.text("Fin de partie", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
            List<String> tied = new ArrayList<>();
            for (Player p : online(alive)) tied.add(p.getName());
            if (winner == null && normal && tied.size() >= 2) {
                // fin du temps avec plusieurs survivants : égalité
                mainTitle = Component.text("Égalité !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
                String joined = String.join(", ", tied);
                sub = Component.text(joined.length() <= 38 ? joined : tied.size() + " survivants", NamedTextColor.YELLOW);
            }
            for (Player p : online(players)) {
                if (!inGame.contains(p.getUniqueId())) continue;
                if (winner == p)
                    p.showTitle(Title.title(Component.text("Victoire !", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                            Component.text("Tu es le dernier survivant !", NamedTextColor.YELLOW)));
                else p.showTitle(Title.title(mainTitle, sub));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (mayTouch(p)) {
                    p.removePotionEffect(PotionEffectType.POISON);
                    p.removePotionEffect(PotionEffectType.WITHER);
                }
            }
            if (winner != null) {
                winner.setInvulnerable(true);
                broadcast("§6" + winner.getName() + " §eremporte les Hunger Games §7avec §f" + kills.getOrDefault(winner.getUniqueId(), 0) + " kill(s)§7 !");
                for (int i = 0; i < 5; i++)
                    Bukkit.getScheduler().runTaskLater(EemiHungerGames.this, () -> {
                        if (winner.isOnline() && state == State.ENDING && winner.getWorld().equals(world)) firework(winner.getLocation());
                    }, i * 20L);
            } else broadcast("§7La partie se termine sans vainqueur.");
            UUID top = null;
            int topKills = 0;
            for (Map.Entry<UUID, Integer> e : kills.entrySet()) if (e.getValue() > topKills) { topKills = e.getValue(); top = e.getKey(); }
            if (top != null && Bukkit.getPlayer(top) != null)
                broadcast("§7Meilleur tueur : §f" + Bukkit.getPlayer(top).getName() + " §7(" + topKills + " kills)");
        }

        /** Fin du deathmatch sans vainqueur, comme la map d'origine. */
        void timeExpired() {
            StringBuilder sb = new StringBuilder();
            for (Player p : online(alive)) sb.append(p.getName()).append(" ");
            broadcast("§cLe temps est écoulé ! §7Survivants : §f" + sb.toString().trim());
            endGame(null, true);
        }

        /**
         * Fin : partie 1 → map suivante tirée au hasard, tout le monde dans son lobby, la map jouée est remise en
         * état. Copie → elle garde sa map et redevient une partie en attente, ou est supprimée s'il n'y a plus
         * personne.
         */
        void backToLobby() {
            List<Player> back = online(players);
            GameMap played = map;
            World playedWorld = world;
            alive.clear();
            inGame.clear();
            outOfBounds.clear();
            state = State.WAITING;
            for (Player p : online(new HashSet<>(spectators))) stopSpectating(p);
            spectators.clear();
            if (n >= 2 && back.isEmpty()) {
                destroyGame(this);
                return;
            }
            status();
            playedWorld.getWorldBorder().setSize(played.borderStart);
            clearMarkers();
            bonusTier.clear();
            if (n == 1) setMap(pickMap(lastMap), null);
            else lobbyBorder();
            for (Player p : back) {
                p.setInvulnerable(false);
                if (!isHgWorld(p.getWorld())) continue; // plus dans un monde du jeu : on ne le téléporte pas
                p.teleport(lobby);
                later(() -> prepareLobbyPlayer(p));
            }
            broadcast("§7Prochaine map : §6" + map.name);
            Bukkit.getScheduler().runTaskLater(EemiHungerGames.this, () -> {
                if (destroyed) return;
                if (state != State.WAITING && state != State.STARTING) return; // déjà relancée : startGame a remis la map
                restoreBlocks();
                if (played.events.equals("desert")) closeLibrary(playedWorld);
                clearEntities(playedWorld);
            }, 20L);
        }

        void forceStop() {
            if (state == State.PLAYING || state == State.CAGES) endGame(null, false);
            if (state == State.ENDING) backToLobby();
            if (state == State.STARTING) state = State.WAITING;
        }

        void restoreBlocks() {
            if (originalWorld != null)
                for (Map.Entry<Long, BlockData> en : original.entrySet()) {
                    long k = en.getKey();
                    originalWorld.getBlockAt(kx(k), ky(k), kz(k)).setBlockData(en.getValue(), false);
                }
            original.clear();
            placed.clear();
        }

        // -------------------------------------------------------------- marqueurs de coffres

        /** Petit texte au-dessus du coffre (non sauvegardé, visible à 16 blocs). */
        void setMarker(List<Block> blocks, boolean empty) {
            if (!getConfig().getBoolean("chest-markers", true)) return;
            Component text = empty ? Component.text("Vide", NamedTextColor.RED) : Component.text("Ouvert", NamedTextColor.GRAY);
            TextDisplay d = null;
            for (Block b : blocks) {
                TextDisplay m = markers.get(key(b.getX(), b.getY(), b.getZ()));
                if (m != null && m.isValid()) { d = m; break; }
            }
            if (d == null) {
                double x = 0, y = 0, z = 0;
                for (Block b : blocks) { x += b.getX() + 0.5; y += b.getY(); z += b.getZ() + 0.5; }
                int c = blocks.size();
                Location at = new Location(blocks.get(0).getWorld(), x / c, y / c + 1.05, z / c);
                d = at.getWorld().spawn(at, TextDisplay.class, t -> {
                    t.setPersistent(false);
                    t.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
                    t.setBillboard(Display.Billboard.CENTER);
                    t.setViewRange(0.25f); // 0,25 x 64 = 16 blocs
                    t.setShadowed(true);
                    t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                            new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
                });
            }
            d.text(text);
            for (Block b : blocks) markers.put(key(b.getX(), b.getY(), b.getZ()), d);
        }

        void removeMarker(long k) {
            TextDisplay d = markers.remove(k);
            if (d == null) return;
            markers.values().removeIf(o -> o == d); // l'autre moitié d'un coffre double
            d.remove();
        }

        void clearMarkers() {
            for (TextDisplay d : new HashSet<>(markers.values())) d.remove();
            markers.clear();
        }

        // -------------------------------------------------------------- porte de la bibliothèque

        void closeLibrary(World w) {
            for (int[] d : LIBRARY_DOOR) w.getBlockAt(d[0], d[1], d[2]).setType(Material.BROWN_STAINED_GLASS_PANE);
            libraryOpen = false;
        }

        /** La clé (membrane de phantom trouvée dans les coffres près de la bibliothèque) ouvre les deux portes. */
        void openLibrary(Player p) {
            for (int[] d : LIBRARY_DOOR) world.getBlockAt(d[0], d[1], d[2]).setType(Material.AIR);
            libraryOpen = true;
            world.playSound(new Location(world, -335, 82, -296), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1f);
            world.playSound(new Location(world, -372, 82, -296), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1f);
            broadcast("§e" + p.getName() + " §7a ouvert la bibliothèque !");
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        String stateName() {
            return switch (state) {
                case PREPARING -> "preparation";
                case WAITING -> "attente";
                case STARTING -> "décompte " + timer + " s";
                case CAGES -> "cages " + timer + " s";
                case PLAYING -> "en jeu " + clock(elapsed);
                case ENDING -> "fin";
            };
        }
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class);
        FireworkMeta m = fw.getFireworkMeta();
        m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.ORANGE, Color.YELLOW).withFade(Color.RED).build());
        m.setPower(1);
        fw.setFireworkMeta(m);
    }

    /** Objets au sol, flèches et XP de l'arène (dans les chunks chargés). */
    private void clearEntities(World w) {
        if (w == null) return;
        for (Entity e : w.getEntities()) {
            if (e instanceof Item || e instanceof Projectile || e instanceof ExperienceOrb) e.remove();
        }
    }

    // ------------------------------------------------------------------ coffres : marqueur « ouvert » et bonus de loot

    /** Niveau d'un coffre d'après sa qualité : 0 commun, 1 peu commun, 2 rare, 3 épique, 4 corne. */
    private static int tier(String quality) {
        String q = quality.replace("_chest", "");
        return switch (q) {
            case "uncommon" -> 1;
            case "rare" -> 2;
            case "epic" -> 3;
            case "cornucopia" -> 4;
            default -> 0;
        };
    }

    /** Objet possible du bonus : matériau, quantité min-max, poids. */
    private record Bonus(Material type, int min, int max, int weight) {}

    private static Bonus b(Material m, int min, int max, int w) {
        return new Bonus(m, min, max, w);
    }

    private static Bonus b(Material m, int w) {
        return new Bonus(m, 1, 1, w);
    }

    /** Nourriture : une pile garantie dans chaque coffre. */
    private static final List<Bonus> BONUS_FOOD = List.of(
            b(Material.BREAD, 2, 4, 3), b(Material.COOKED_BEEF, 2, 4, 3), b(Material.COOKED_PORKCHOP, 2, 4, 2),
            b(Material.BAKED_POTATO, 3, 5, 2), b(Material.APPLE, 2, 4, 2), b(Material.COOKED_CHICKEN, 2, 4, 2),
            b(Material.GOLDEN_CARROT, 1, 3, 1));

    /** Réserves par niveau (index = tier) et nombre de tirages min-max. */
    private static final List<List<Bonus>> BONUS = List.of(
            // commun : de quoi se défendre un peu, matériaux
            List.of(b(Material.ARROW, 4, 8, 4), b(Material.STONE_SWORD, 3), b(Material.STONE_AXE, 2), b(Material.BOW, 1),
                    b(Material.CHAINMAIL_HELMET, 1), b(Material.CHAINMAIL_BOOTS, 1), b(Material.IRON_HELMET, 1), b(Material.IRON_BOOTS, 1),
                    b(Material.IRON_INGOT, 1, 2, 3), b(Material.OAK_PLANKS, 8, 16, 3), b(Material.COBBLESTONE, 8, 16, 3), b(Material.STICK, 2, 4, 1)),
            // peu commun
            List.of(b(Material.ARROW, 6, 12, 4), b(Material.STONE_SWORD, 2), b(Material.IRON_SWORD, 1), b(Material.BOW, 2),
                    b(Material.IRON_HELMET, 2), b(Material.IRON_BOOTS, 2), b(Material.IRON_LEGGINGS, 1), b(Material.CHAINMAIL_CHESTPLATE, 2),
                    b(Material.IRON_INGOT, 2, 3, 3), b(Material.GOLD_INGOT, 2, 4, 1), b(Material.OAK_PLANKS, 16, 16, 2),
                    b(Material.COBBLESTONE, 16, 16, 2), b(Material.FLINT_AND_STEEL, 1)),
            // rare : fer, premiers diamants
            List.of(b(Material.ARROW, 8, 16, 4), b(Material.IRON_SWORD, 3), b(Material.BOW, 2), b(Material.IRON_HELMET, 2),
                    b(Material.IRON_CHESTPLATE, 2), b(Material.IRON_LEGGINGS, 2), b(Material.IRON_BOOTS, 2), b(Material.IRON_INGOT, 2, 4, 3),
                    b(Material.GOLDEN_APPLE, 2), b(Material.DIAMOND, 1), b(Material.EXPERIENCE_BOTTLE, 3, 6, 2), b(Material.WATER_BUCKET, 1)),
            // épique (et réapprovisionnement de la corne)
            List.of(b(Material.IRON_CHESTPLATE, 3), b(Material.IRON_LEGGINGS, 3), b(Material.IRON_SWORD, 2), b(Material.DIAMOND_SWORD, 1),
                    b(Material.DIAMOND_HELMET, 1), b(Material.DIAMOND_BOOTS, 1), b(Material.DIAMOND, 1, 2, 3), b(Material.GOLDEN_APPLE, 1, 2, 3),
                    b(Material.ARROW, 12, 24, 2), b(Material.EXPERIENCE_BOTTLE, 4, 8, 2)),
            // corne d'abondance (premier remplissage)
            List.of(b(Material.IRON_SWORD, 3), b(Material.BOW, 2), b(Material.ARROW, 8, 16, 3), b(Material.IRON_HELMET, 2),
                    b(Material.IRON_CHESTPLATE, 2), b(Material.IRON_LEGGINGS, 2), b(Material.IRON_BOOTS, 2), b(Material.GOLDEN_APPLE, 2),
                    b(Material.DIAMOND, 1), b(Material.IRON_INGOT, 2, 4, 2)));
    private static final int[][] BONUS_ROLLS = {{1, 2}, {2, 2}, {2, 3}, {3, 3}, {2, 3}};

    private ItemStack roll(List<Bonus> pool) {
        int sum = 0;
        for (Bonus x : pool) sum += x.weight();
        int r = random.nextInt(sum);
        for (Bonus x : pool) {
            if ((r -= x.weight()) < 0) return new ItemStack(x.type(), x.min() + random.nextInt(x.max() - x.min() + 1));
        }
        return null;
    }

    /** Bonus ajouté au loot de la map (dans des cases vides au hasard) à la première ouverture du coffre. */
    private void addBonus(Inventory inv, int tier) {
        if (!getConfig().getBoolean("bonus-loot.enabled", true)) return;
        tier = Math.max(0, Math.min(tier, BONUS.size() - 1));
        List<ItemStack> items = new ArrayList<>();
        if (getConfig().getBoolean("bonus-loot.food", true)) items.add(roll(BONUS_FOOD));
        int[] r = BONUS_ROLLS[tier];
        int n = r[0] + random.nextInt(r[1] - r[0] + 1) + Math.max(0, cfg("bonus-loot.extra-rolls", 0));
        for (int i = 0; i < n; i++) items.add(roll(BONUS.get(tier)));
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null || inv.getItem(i).getType().isAir()) free.add(i);
        Collections.shuffle(free, random);
        for (int i = 0; i < items.size() && i < free.size(); i++) if (items.get(i) != null) inv.setItem(free.get(i), items.get(i));
    }

    /** Blocs de coffre derrière un inventaire (un ou deux pour un coffre double). */
    private static List<Block> chestBlocks(InventoryHolder h) {
        List<Block> l = new ArrayList<>();
        if (h instanceof Chest c) l.add(c.getBlock());
        else if (h instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof Chest c) l.add(c.getBlock());
            if (dc.getRightSide() instanceof Chest c) l.add(c.getBlock());
        }
        return l;
    }

    /** Ouverture d'un coffre de la map par un survivant : bonus de loot la première fois, marqueur « Ouvert ». */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || g.state != State.PLAYING || !g.alive.contains(p.getUniqueId())) return;
        List<Block> blocks = chestBlocks(e.getInventory().getHolder(false));
        if (blocks.isEmpty() || !blocks.get(0).getWorld().equals(g.world)) return;
        boolean ours = false;
        for (Block b : blocks) {
            long k = key(b.getX(), b.getY(), b.getZ());
            Integer t = g.bonusTier.remove(k);
            if (t != null) addBonus(e.getInventory(), t);
            ours |= t != null || g.markers.containsKey(k);
        }
        if (ours) g.setMarker(blocks, e.getInventory().isEmpty());
    }

    /** Fermeture : « Vide » si on a tout pris, sinon « Ouvert ». */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChestClose(InventoryCloseEvent e) {
        List<Block> blocks = chestBlocks(e.getInventory().getHolder(false));
        if (blocks.isEmpty()) return;
        Game g = gameAt(blocks.get(0).getWorld());
        if (g == null || g.state != State.PLAYING) return;
        if (!g.markers.containsKey(key(blocks.get(0).getX(), blocks.get(0).getY(), blocks.get(0).getZ()))) return;
        g.setMarker(blocks, e.getInventory().isEmpty());
    }

    /** Marqueurs restés d'un arrêt brutal (normalement jamais sauvegardés) : retirés. */
    private void removeOrphanMarkers(List<Entity> entities) {
        for (Entity en : entities) {
            if (!(en instanceof TextDisplay t) || !t.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE)) continue;
            boolean known = false;
            for (Game g : games.values()) if (g.markers.containsValue(t)) { known = true; break; }
            if (!known) t.remove();
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        if (markerKey != null && isHgWorld(e.getWorld())) removeOrphanMarkers(e.getEntities());
    }

    private boolean isLibraryDoor(Block b) {
        for (int[] d : LIBRARY_DOOR) if (b.getX() == d[0] && b.getY() == d[1] && b.getZ() == d[2]) return true;
        return false;
    }

    // ------------------------------------------------------------------ objets

    private ItemStack named(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    /** Objet du plugin : ne tombe pas à la mort et ne se jette pas. */
    private ItemStack kit(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isHgWorld(e.getLocation().getWorld())
                && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    /** Après la période de grâce, un survivant peut poser des blocs partout (et seaux, feu...) dans le monde de sa partie. */
    private boolean canBuild(Player p, Game g) {
        return g != null && byPlayer.get(p.getUniqueId()) == g && g.state == State.PLAYING
                && g.alive.contains(p.getUniqueId()) && g.elapsed >= cfg("grace-period", 30);
    }

    private boolean inWorld(Block b) {
        return isHgWorld(b.getWorld());
    }

    /** Garde l'état d'origine d'un bloc de la map avant sa première modification (remis en fin de partie). */
    private void record(Block b) {
        Game g = gameAt(b.getWorld());
        if (g != null && g.running()) g.original.putIfAbsent(key(b.getX(), b.getY(), b.getZ()), b.getBlockData());
    }

    /** On ne casse que les blocs posés par les joueurs pendant la partie, la map reste intacte. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!inWorld(b) || admin(e.getPlayer())) return;
        Game g = gameAt(b.getWorld());
        if (!canBuild(e.getPlayer(), g) || !g.placed.contains(key(b.getX(), b.getY(), b.getZ()))) {
            e.setCancelled(true);
            return;
        }
        g.placed.remove(key(b.getX(), b.getY(), b.getZ()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        if (!inWorld(b) || admin(e.getPlayer())) return;
        Game g = gameAt(b.getWorld());
        if (!canBuild(e.getPlayer(), g)) {
            e.setCancelled(true);
            return;
        }
        g.original.putIfAbsent(key(b.getX(), b.getY(), b.getZ()), e.getBlockReplacedState().getBlockData());
        g.placed.add(key(b.getX(), b.getY(), b.getZ()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Block b = e.getBlock();
        if (!inWorld(b) || admin(e.getPlayer())) return;
        Game g = gameAt(b.getWorld());
        if (!canBuild(e.getPlayer(), g)) e.setCancelled(true);
        else {
            record(b);
            g.placed.add(key(b.getX(), b.getY(), b.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Block b = e.getBlock();
        if (!inWorld(b) || admin(e.getPlayer())) return;
        Game g = gameAt(b.getWorld());
        // on ne reprend que l'eau/lave posée par les joueurs (pas l'eau de la map)
        if (!canBuild(e.getPlayer(), g) || !g.placed.contains(key(b.getX(), b.getY(), b.getZ()))) e.setCancelled(true);
        else record(b);
    }

    /** Les liquides posés coulent (mémorisés pour la remise en état). */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        record(e.getToBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (inWorld(e.getBlock())) e.setCancelled(true);
    }

    /** Explosions (TNT...) : elles blessent mais ne cassent que les blocs posés par les joueurs de la partie. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        World w = e.getLocation().getWorld();
        if (!isHgWorld(w)) return;
        Game g = gameAt(w);
        if (g == null) { e.blockList().clear(); return; }
        e.blockList().removeIf(b -> !g.placed.remove(key(b.getX(), b.getY(), b.getZ())));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!inWorld(e.getBlock())) return;
        Game g = gameAt(e.getBlock().getWorld());
        if (g == null) { e.blockList().clear(); return; }
        e.blockList().removeIf(b -> !g.placed.remove(key(b.getX(), b.getY(), b.getZ())));
    }

    /** Terre labourée piétinée, blocs qui tombent : la map ne bouge pas, sauf les blocs des joueurs. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        Block b = e.getBlock();
        if (!inWorld(b)) return;
        if (e.getEntity() instanceof Player p && admin(p)) return;
        if (e.getEntity() instanceof org.bukkit.entity.FallingBlock) {
            Game g = gameAt(b.getWorld());
            if (g == null) return;
            record(b);
            if (e.getTo() != Material.AIR) g.placed.add(key(b.getX(), b.getY(), b.getZ()));
            return;
        }
        e.setCancelled(true);
    }

    /** Cadres et tableaux de la map : incassables. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!isHgWorld(e.getEntity().getWorld())) return;
        if (e.getRemover() instanceof Player p && admin(p)) return;
        e.setCancelled(true);
    }

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemihungergames.admin");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        UUID id = victim.getUniqueId();
        Game g = byPlayer.get(id);
        if (g == null) {
            if (isHgWorld(victim.getWorld())) e.setCancelled(true);
            return;
        }
        // seuls les survivants d'une partie lancée prennent des dégâts (lobby, cages et fin : rien)
        if (g.state != State.PLAYING || !g.alive.contains(id)) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent by && g.elapsed < cfg("grace-period", 30)) {
            Entity d = by.getDamager();
            boolean pvp = d instanceof Player || (d instanceof Projectile proj && proj.getShooter() instanceof Player);
            if (pvp) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || !g.alive.contains(p.getUniqueId())) return;
        e.getDrops().removeIf(this::isKit);
        e.deathMessage(null);
        e.setKeepInventory(false);
        g.lastLoc.put(p.getUniqueId(), p.getLocation());
        Player killer = p.getKiller();
        String how = switch (p.getLastDamageCause() == null ? EntityDamageEvent.DamageCause.CUSTOM : p.getLastDamageCause().getCause()) {
            case FALL -> "§7s'est écrasé au sol";
            case WORLD_BORDER -> "§7a été rattrapé par la bordure";
            case POISON -> "§7a succombé au poison";
            case WITHER -> "§7est resté sur la pyramide";
            case LAVA, FIRE, FIRE_TICK -> "§7a brûlé";
            case DROWNING -> "§7s'est noyé";
            default -> "§7est mort";
        };
        g.eliminate(p, killer, how);
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && p.isDead()) p.spigot().respawn(); }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Game g = byPlayer.get(id);
        if (g == null || g.lobby == null) return;
        if (g.inGame.contains(id) && g.running()) {
            // spectateur là où il est mort (ou au centre s'il est tombé)
            Location at = g.lastLoc.getOrDefault(id, g.center);
            if (!g.world.equals(at.getWorld()) || at.getY() < g.world.getMinHeight() + 5) at = g.center.clone().add(0, 20, 0);
            e.setRespawnLocation(at);
            later(() -> { if (mayTouch(p)) p.setGameMode(GameMode.SPECTATOR); });
            return;
        }
        e.setRespawnLocation(g.lobby);
        later(() -> g.prepareLobbyPlayer(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || g.state != State.CAGES || !g.alive.contains(p.getUniqueId())) return;
        Location f = e.getFrom(), t = e.getTo();
        // immobiles dans les cages (on peut tourner la tête)
        if (f.getX() != t.getX() || f.getZ() != t.getZ() || t.getY() > f.getY()) {
            Location back = f.clone();
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            e.setTo(back);
        }
    }

    /**
     * Un inscrit qui quitte le monde de SA partie (/lobby, /mv tp...) quitte les Hunger Games. La partie 1 qui
     * change de map téléporte ses joueurs au lobby de la map suivante : son monde a déjà changé, ce n'est pas un départ.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null && g.world != null && e.getFrom().equals(g.world) && !p.getWorld().equals(g.world)) g.leave(p, false);
        Game s = spectating.get(p.getUniqueId());
        if (s != null && s.world != null && e.getFrom().equals(s.world) && !p.getWorld().equals(s.world)) {
            spectating.remove(p.getUniqueId());
            s.spectators.remove(p.getUniqueId());
            if (p.getGameMode() == GameMode.SPECTATOR) later(() -> {
                // seulement au hub : dans un autre monde (skyblock...), on ne touche pas au mode de jeu
                if (p.isOnline() && p.getGameMode() == GameMode.SPECTATOR && isHub(p.getWorld())) p.setGameMode(GameMode.SURVIVAL);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) return;
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null) return;
        ItemStack it = e.getItem();
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (right && kitManager.isChooser(it)) {
            e.setCancelled(true);
            if (!g.running()) kitManager.open(p);
            return;
        }
        if (right && it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté les Hunger Games.");
            return;
        }
        Block clicked = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null && g.map != null && g.map.events.equals("desert")
                && clicked.getWorld().equals(g.world) && isLibraryDoor(clicked)
                && g.state == State.PLAYING && g.alive.contains(p.getUniqueId()) && !g.libraryOpen) {
            e.setCancelled(true);
            if (it != null && it.getType() == Material.PHANTOM_MEMBRANE) {
                it.setAmount(it.getAmount() - 1);
                g.openLibrary(p);
            } else {
                p.sendActionBar(Component.text("Verrouillé : il faut la clé de la bibliothèque", NamedTextColor.WHITE));
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            }
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent e) {
        Game g = byPlayer.get(e.getPlayer().getUniqueId());
        if (g == null) return;
        if (g.state != State.PLAYING || isKit(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        // la faim ne compte que pour les survivants en partie
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null && !(g.state == State.PLAYING && g.alive.contains(p.getUniqueId()))) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null) g.leave(p, true);
        Game s = spectating.get(p.getUniqueId());
        if (s != null) s.stopSpectating(p);
    }

    // ------------------------------------------------------------------ commandes

    /** Partie visée par une commande admin : « <n> » donné, sinon celle de l'admin, sinon la partie 1. */
    private Game targetGame(CommandSender sender, Player p, String[] args) {
        if (args.length > 1) {
            try {
                Game g = games.get(Integer.parseInt(args[1]));
                if (g == null) sender.sendMessage(PREFIX + "§cPas de partie " + args[1] + ".");
                return g;
            } catch (NumberFormatException ex) {
                sender.sendMessage(PREFIX + "§cNuméro de partie invalide.");
                return null;
            }
        }
        if (p != null) {
            Game g = byPlayer.get(p.getUniqueId());
            if (g == null) g = gameAt(p.getWorld());
            if (g != null) return g;
        }
        Game g = games.get(1);
        if (g == null) sender.sendMessage(PREFIX + "§cAucune partie.");
        return g;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemihungergames.admin");
        Game g1 = games.get(1);
        switch (sub) {
            case "join" -> {
                Player target = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target);
            }
            case "leave" -> {
                if (p == null) return true;
                Game g = byPlayer.get(p.getUniqueId());
                if (g == null) return true;
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté les Hunger Games.");
            }
            case "start" -> {
                if (!admin) return true;
                Game g = targetGame(sender, p, args);
                if (g == null) return true;
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cLa partie " + g.n + " est déjà en cours (ou en préparation)."); return true; }
                if (g.players.size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs inscrits."); return true; }
                g.startGame();
                sender.sendMessage(PREFIX + "§aPartie " + g.n + " lancée.");
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = targetGame(sender, p, args);
                if (g == null) return true;
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie " + g.n + " arrêtée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (max " + maxInstances() + ") :");
                for (Game g : games.values())
                    sender.sendMessage("§8 - §f#" + g.n + " §7" + (g.world == null ? (g.map == null ? "?" : WorldClone.name(g.map.base, g.n)) : g.world.getName())
                            + " §8| §e" + g.stateName() + " §8| §7map §6" + (g.map == null ? "?" : g.map.name)
                            + " §8| §f" + g.players.size() + "§7 inscrits, §f" + g.alive.size() + "§7 en vie, §f" + g.spectators.size() + "§7 spect.");
                StringBuilder sb = new StringBuilder();
                for (World w : models.values()) sb.append(w.getName()).append(" ");
                sender.sendMessage("§8   modèles prêts : §7" + (sb.isEmpty() ? "aucun" : sb.toString().trim()) + (modelBusy.isEmpty() ? "" : " §8(en cours : " + modelBusy + ")"));
            }
            case "chests" -> {
                if (!admin) return true;
                if (g1 == null || g1.running() || g1.map == null) { sender.sendMessage(PREFIX + "§cLa partie 1 est en cours : attends la fin."); return true; }
                g1.placeChests();
                sender.sendMessage(PREFIX + "§aCoffres de la partie 1 replacés (voir la console).");
            }
            case "reload" -> {
                if (!admin) return true;
                if (games.size() > 1 || copying > 0 || (g1 != null && g1.running())) {
                    sender.sendMessage(PREFIX + "§cAttends la fin des parties : seule la partie 1, en attente, doit exister (/hungergames parties).");
                    return true;
                }
                String current = g1 == null || g1.map == null ? null : g1.map.id;
                loadSettings();
                for (GameMap m : maps.values()) prepareWorld(m.world, m);
                if (g1 != null) {
                    g1.setMap(current != null && maps.containsKey(current) ? maps.get(current) : pickMap(null), null);
                    for (Player w : online(g1.players)) {
                        if (!isHgWorld(w.getWorld())) continue;
                        w.teleport(g1.lobby);
                        later(() -> g1.prepareLobbyPlayer(w));
                    }
                }
                ensureModels();
                sender.sendMessage(PREFIX + "§aConfig rechargée (" + maps.size() + " maps).");
            }
            case "map" -> {
                if (!admin) return true;
                if (g1 == null || args.length < 2 || !maps.containsKey(args[1].toLowerCase())) {
                    sender.sendMessage(PREFIX + "§7Maps : §f" + String.join(", ", maps.keySet()) + " §7(prochaine de la partie 1 : §6"
                            + (g1 == null || g1.map == null ? "?" : g1.map.id) + "§7)");
                    return true;
                }
                if (g1.running()) { sender.sendMessage(PREFIX + "§cLa partie 1 est en cours : attends la fin."); return true; }
                // aucun bloc n'est modifié par le changement de map : le modèle reste valable
                g1.setMap(maps.get(args[1].toLowerCase()), null);
                for (Player w : online(g1.players)) {
                    if (!isHgWorld(w.getWorld())) continue;
                    w.teleport(g1.lobby);
                    later(() -> g1.prepareLobbyPlayer(w));
                }
                g1.broadcast("§7Prochaine map : §6" + g1.map.name);
                sender.sendMessage(PREFIX + "§aProchaine map de la partie 1 : " + g1.map.name);
            }
            case "spectate", "spec" -> {
                if (p == null) return true;
                spectate(p, args.length > 1 ? args[1] : null);
            }
            case "kit", "kits" -> {
                if (p == null) return true;
                Game g = byPlayer.get(p.getUniqueId());
                if (g != null && g.running() && g.inGame.contains(p.getUniqueId())) { p.sendMessage(PREFIX + "§cAttends la fin de la partie."); return true; }
                kitManager.open(p);
            }
            default -> sender.sendMessage(PREFIX + "§7/hungergames <join|leave|spectate [joueur]|kit>"
                    + (admin ? " §8| admin : start [n], stop [n], parties, chests, reload, map [id]" : ""));
        }
        return true;
    }

    // ------------------------------------------------------------------ utilitaires

    private void later(Runnable r) {
        Bukkit.getScheduler().runTaskLater(this, r, 3L);
    }

    private List<Player> online(Set<UUID> ids) {
        List<Player> list = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) list.add(p);
        }
        return list;
    }
}
