package fr.eemicraft.skywars;

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
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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

/**
 * SkyWars d'EEMICRAFT (solo), d'après le SkyWars d'Hypixel, sur des maps importées (Sugar Rush, Twister...).
 * Chaque joueur attend dans la cage de verre de son île ; à l'ouverture, il pille les coffres de son île
 * (armure, épée, blocs), puis rejoint les îlots et l'île centrale (meilleur loot). Tous les blocs se cassent
 * et se posent, on tue à l'arme ou en poussant dans le vide. Deux remplissages des coffres (le second ajoute
 * une perle de l'Ender par coffre), puis le « Doom » (la bordure se resserre sur le centre) et égalité à la
 * fin du temps. Le dernier survivant gagne. Toute la map est remise en état après chaque partie.
 * <p>
 * Plusieurs parties en même temps (max-instances) : la partie 1 joue dans le monde d'origine de sa map
 * (« skywars », « sw_twister »), la partie n ≥ 2 dans une copie « monde_n » faite depuis « monde_modele »
 * (copie propre faite au démarrage), détruite quand elle ne sert plus.
 */
public final class EemiSkyWars extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§cSkyWars§8] §7";
    /** Délai avant de réutiliser le nom d'une copie détruite (suppression des fichiers en cours). */
    private static final long REUSE_DELAY_MS = 15_000;

    enum State { PREPARING, WAITING, STARTING, PLAYING, ENDING, RESETTING }

    /** Coffre de la map : position et type (island, mid, center). */
    private record ChestSpot(int x, int y, int z, String type) {}

    /** Une map (section maps.<id> du config). Cage = {x, y du plancher de verre, z}. world = monde d'origine. */
    private static final class GameMap {
        String id, name, base;
        World world;
        Location center;
        double[] borderCenter;
        int borderSize, doomBorderSize, voidY;
        final List<int[]> cages = new ArrayList<>();
        final List<ChestSpot> chests = new ArrayList<>();
    }

    private NamespacedKey kitKey;
    private Kits kitManager;
    private final Map<String, GameMap> maps = new LinkedHashMap<>();
    /** Map en cours d'édition (/skywars editmap). */
    private String editMap;

    /** Parties par numéro (1 = monde d'origine). */
    private final Map<Integer, Game> games = new TreeMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private final Map<UUID, Game> spectating = new HashMap<>();
    /** Modèle propre de chaque monde de map (base → monde « base_modele »). */
    private final Map<String, World> models = new HashMap<>();
    private final Set<String> modelBusy = new HashSet<>();
    private int copying;
    private final Map<String, Long> freedAt = new HashMap<>();
    private String lastStatus;
    private final Random random = new Random();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        saveDefaultConfig();
        kitManager = new Kits(this, kitKey, PREFIX);
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(kitManager, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::fastTick, 5L, 5L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (GameMap m : maps.values()) WorldClone.cleanup(this, m.world, m.base, maxInstances());
            setupMaps();
            Game g1 = new Game(1);
            games.put(1, g1);
            if (!maps.isEmpty()) g1.setMap(pickMap(null), null);
            remakeAllModels();
            status();
        }, 40L);
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) {
            for (Player p : online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            }
            for (Player p : online(g.spectators)) g.stopSpectating(p);
            // remise en état immédiate si le serveur s'arrête en pleine partie (les copies seront supprimées au démarrage)
            if (g.n == 1 && g.world != null)
                for (Map.Entry<Long, BlockData> en : g.original.entrySet()) {
                    long k = en.getKey();
                    g.world.getBlockAt(kx(k), ky(k), kz(k)).setBlockData(en.getValue(), false);
                }
            g.original.clear();
        }
    }

    private int maxInstances() {
        return Math.max(1, getConfig().getInt("max-instances", 3));
    }

    /** Section « maps » du config (celle du jar si le config du serveur n'en a pas). */
    private ConfigurationSection mapsSection() {
        // un config de serveur sans section « maps » rend une section vide (pas celle du jar) : on prend alors celle du jar
        ConfigurationSection sec = getConfig().getConfigurationSection("maps");
        if ((sec == null || sec.getKeys(false).isEmpty()) && getConfig().getDefaults() != null)
            sec = getConfig().getDefaults().getConfigurationSection("maps");
        return sec;
    }

    /** Charge les maps et les kits du config. */
    private void loadSettings() {
        reloadConfig();
        kitManager.load();
        maps.clear();
        ConfigurationSection sec = mapsSection();
        if (sec == null) getLogger().severe("Aucune map dans le config.");
        else
            for (String id : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(id);
                if (c == null) continue;
                GameMap m = new GameMap();
                m.id = id.toLowerCase();
                m.name = c.getString("name", id);
                m.base = c.getString("world", id);
                m.world = Bukkit.getWorld(m.base);
                if (m.world == null) {
                    getLogger().warning("Map " + id + " : monde " + m.base + " introuvable, ignoree.");
                    continue;
                }
                try {
                    m.center = loc(m.world, c.getString("center", "0.5,100,0.5"));
                    m.borderCenter = c.isString("border-center") ? xz(c.getString("border-center"))
                            : new double[]{m.center.getX(), m.center.getZ()};
                    m.borderSize = c.getInt("border-size", 260);
                    m.doomBorderSize = c.getInt("doom-border-size", 24);
                    m.voidY = c.getInt("void-y", 40);
                    int floorY = c.getInt("cage-floor-y", 111);
                    // cage « x,z » (plancher en cage-floor-y) ou « x,y,z » (y = plancher de verre)
                    for (String s : c.getStringList("cages")) {
                        String[] p = s.split(",");
                        m.cages.add(p.length >= 3
                                ? new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())}
                                : new int[]{Integer.parseInt(p[0].trim()), floorY, Integer.parseInt(p[1].trim())});
                    }
                    for (String s : c.getStringList("chests")) {
                        String[] p = s.trim().split(" ");
                        m.chests.add(new ChestSpot(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3]));
                    }
                } catch (RuntimeException ex) {
                    getLogger().warning("Map " + id + " : config invalide (" + ex.getMessage() + "), ignoree.");
                    continue;
                }
                if (m.cages.size() < 2) {
                    getLogger().warning("Map " + id + " : moins de 2 cages, ignoree (a completer avec /skywars addcage).");
                    continue;
                }
                maps.put(m.id, m);
                getLogger().info("Map " + m.name + " : " + m.cages.size() + " cages, " + m.chests.size() + " coffres, monde " + m.base + ".");
            }
    }

    /** Règles des mondes d'origine et cages manquantes (avant de faire les modèles). */
    private void setupMaps() {
        for (GameMap m : maps.values()) {
            prepareWorld(m.world, m);
            // maps sans cages d'origine (ex. Twister) : on construit les cages manquantes, sur l'air seulement
            int built = 0;
            for (int[] c : m.cages) built += buildCage(m.world, c[0], c[1], c[2]);
            if (built > 0) getLogger().info("Map " + m.id + " : " + built + " blocs de verre poses pour les cages.");
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

    /** Monde de SkyWars : monde d'origine d'une map, une de ses copies ou son modèle. */
    private boolean isSwWorld(World w) {
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

    /** Règles du monde : jour fixe, pas de mobs, feu qui ne se propage pas, régénération naturelle. */
    private void prepareWorld(World w, GameMap m) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, false);
        w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.LOCATOR_BAR, false);
        w.setTime(6000);
        w.setStorm(false);
        Location c = m.center.clone();
        c.setWorld(w);
        w.setSpawnLocation(c);
    }

    // ------------------------------------------------------------------ modèles et copies

    private void remakeAllModels() {
        Set<String> bases = new LinkedHashSet<>();
        for (GameMap m : maps.values()) bases.add(m.base);
        for (String b : bases) remakeModel(b);
    }

    /**
     * (Re)fait la copie propre « base_modele » du monde d'origine. Attend qu'aucune copie ne soit en cours
     * et que la partie 1 ne joue pas dans ce monde.
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
        WorldClone.create(this, src, name, w -> {
            modelBusy.remove(base);
            if (w == null) {
                getLogger().warning("Modele " + name + " impossible : pas de parties supplementaires sur ce monde.");
                return;
            }
            for (GameMap m : maps.values()) if (m.base.equals(base)) { prepareWorld(w, m); break; }
            models.put(base, w);
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

    private static Location loc(World w, String s) {
        String[] p = s.split(",");
        Location l = new Location(w, Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
        if (p.length >= 5) {
            l.setYaw(Float.parseFloat(p[3].trim()));
            l.setPitch(Float.parseFloat(p[4].trim()));
        }
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
            if (g.state == State.PLAYING) play = true;
            for (UUID id : g.players) if (!g.inGame.contains(id)) waiting++;
            ingame += g.alive.size();
        }
        int perGame = 0;
        for (GameMap m : maps.values()) perGame = Math.max(perGame, m.cages.size());
        String st = wait ? "waiting" : play ? "playing" : "waiting";
        String s = "status skywars " + st + " " + waiting + " " + ingame + " " + perGame * maxInstances();
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
        // partie en attente (ou en décompte) qui a de la place, la plus remplie d'abord
        Game best = null;
        for (Game g : games.values())
            if (g.joinable() && g.players.size() < g.capacity() && (best == null || g.players.size() > best.players.size())) best = g;
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
        p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie... §8(map " + m.name + ")");
        copying++;
        WorldClone.create(this, models.get(m.base), WorldClone.name(m.base, n), w -> {
            copying--;
            g.ready(w);
        });
        status();
    }

    /** Spectateur : partie de ce joueur, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String targetName) {
        UUID id = p.getUniqueId();
        if (byPlayer.containsKey(id)) { p.sendMessage(PREFIX + "§cTu es inscrit : quitte SkyWars pour regarder."); return; }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : byPlayer.get(t.getUniqueId());
            if (g == null) { p.sendMessage(PREFIX + "§cCe joueur n'est pas dans une partie de SkyWars."); return; }
            if (g.state != State.PLAYING && g.state != State.ENDING) { p.sendMessage(PREFIX + "§cSa partie n'a pas commencé."); return; }
        } else
            for (Game c : games.values())
                if ((c.state == State.PLAYING || c.state == State.ENDING) && (g == null || c.alive.size() > g.alive.size())) g = c;
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
     * Renvoi au hub : seulement depuis un monde de SkyWars ou le hub (jamais depuis le skyblock ou un autre jeu).
     * Téléportation d'abord (Multiverse-Inventories garde l'inventaire du monde quitté), puis remise à zéro au hub.
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
     * On ne touche à l'inventaire / l'état d'un joueur que s'il est dans un monde de SkyWars (map, copie) ou au hub :
     * ailleurs (skyblock...), Multiverse-Inventories a déjà chargé l'inventaire de ce monde-là.
     */
    private boolean mayTouch(Player p) {
        return p != null && p.isOnline() && (isSwWorld(p.getWorld()) || isHub(p.getWorld()));
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

    /** Construit une cage de verre (plancher y, murs, toit) sur les seules cases d'air : le décor de la map reste. */
    private int buildCage(World w, int x, int y, int z) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = 0; dy <= 4; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    boolean inside = dx == 0 && dz == 0 && dy >= 1 && dy <= 3;
                    Block b = w.getBlockAt(x + dx, y + dy, z + dz);
                    if (inside || !b.getType().isAir()) continue;
                    b.setType(Material.GLASS, false);
                    n++;
                }
        return n;
    }

    // ================================================================== une partie

    final class Game {
        final int n;
        GameMap map;
        World world;
        Location center;
        State state = State.WAITING;
        boolean destroyed;
        String lastMap;

        final Set<UUID> players = new LinkedHashSet<>();
        /** Non-inscrits qui regardent la partie en spectateur (/skywars spectate). */
        final Set<UUID> spectators = new HashSet<>();
        /** Cage (index dans les cages de la map) de chaque joueur en attente / en partie. */
        final Map<UUID, Integer> cageOf = new HashMap<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> inGame = new HashSet<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Dernier joueur à avoir frappé (pour créditer les chutes dans le vide), et quand. */
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitAt = new HashMap<>();
        /** Retour en arrière : état d'origine de chaque bloc modifié pendant la partie. */
        final Map<Long, BlockData> original = new LinkedHashMap<>();

        int timer, elapsed, total;

        Game(int n) {
            this.n = n;
        }

        List<int[]> cages() {
            return map == null ? List.of() : map.cages;
        }

        int capacity() {
            return cages().size();
        }

        boolean joinable() {
            return !destroyed && map != null && (state == State.WAITING || state == State.STARTING || state == State.PREPARING);
        }

        boolean running() {
            return state == State.PLAYING || state == State.ENDING || state == State.RESETTING;
        }

        boolean recording() {
            return state == State.PLAYING || state == State.ENDING;
        }

        /** Change de map (partie 1 : monde d'origine de la map ; copie : son monde w). */
        void setMap(GameMap m, World w) {
            map = m;
            if (m == null) { world = null; center = null; return; }
            world = n == 1 ? m.world : w;
            center = m.center.clone();
            center.setWorld(world);
            gameBorder();
        }

        /** Copie du monde prête (ou null si la copie a échoué) : les inscrits vont dans les cages. */
        void ready(World w) {
            if (destroyed) {
                if (w != null) Bukkit.getScheduler().runTask(EemiSkyWars.this, () -> WorldClone.destroy(EemiSkyWars.this, w, map.base, EemiSkyWars.this::sendToHub));
                return;
            }
            if (w == null) {
                for (Player p : online(players)) p.sendMessage(PREFIX + "§cImpossible de préparer la partie, réessaie plus tard.");
                for (Player p : online(players)) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                for (UUID id : players) byPlayer.remove(id);
                players.clear();
                destroyed = true;
                games.remove(n);
                status();
                return;
            }
            prepareWorld(w, map);
            setMap(map, w);
            state = State.WAITING;
            players.removeIf(id -> Bukkit.getPlayer(id) == null);
            if (players.isEmpty()) { destroyGame(this); return; }
            for (Player p : online(players)) toCage(p);
            broadcast("§7Partie prête (§d" + map.name + "§7) : §f" + cageOf.size() + "/" + capacity() + "§7 joueurs.");
            status();
        }

        void gameBorder() {
            if (map == null || world == null) return;
            WorldBorder b = world.getWorldBorder();
            b.setCenter(map.borderCenter[0], map.borderCenter[1]);
            b.setSize(map.borderSize);
            b.setDamageAmount(1.0);
            b.setDamageBuffer(0);
        }

        // -------------------------------------------------------------- boucles

        void tick() {
            switch (state) {
                case WAITING -> {
                    if (players.size() >= cfg("min-players", 2) && cageOf.size() >= cfg("min-players", 2)) {
                        state = State.STARTING;
                        timer = cfg("countdown", 30);
                        broadcast("§eAssez de joueurs : les cages s'ouvrent dans §6" + timer + " s§e.");
                    }
                }
                case STARTING -> {
                    if (cageOf.size() < cfg("min-players", 2)) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                    } else {
                        if (cageOf.size() >= capacity()) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0)
                            for (Player p : online(players)) {
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> perSecondGame();
                case ENDING -> {
                    if (--timer <= 0) rollback();
                }
                case RESETTING, PREPARING -> { }
            }
            if (!destroyed) for (Player p : online(players)) updateSidebar(p);
        }

        /** Une seconde de partie : remplissages, Doom, fin à égalité. */
        void perSecondGame() {
            elapsed++;
            if (alive.size() <= 1) {
                endGame(alive.isEmpty() ? null : Bukkit.getPlayer(alive.iterator().next()), true);
                return;
            }
            if (elapsed == cfg("refill-1", 300)) refill(false);
            if (elapsed == cfg("refill-2", 600)) refill(true);
            if (elapsed == cfg("doom", 900)) doom();
            if (elapsed >= cfg("game-end", 1200)) {
                StringBuilder sb = new StringBuilder();
                for (Player p : online(alive)) sb.append(p.getName()).append(" ");
                broadcast("§cLe temps est écoulé ! §7Égalité entre : §f" + sb.toString().trim());
                endGame(null, true);
            }
        }

        /** Toutes les 5 ticks : chute dans le vide = mort (créditée au dernier joueur qui a frappé). */
        void fastTick() {
            if (state != State.PLAYING || destroyed) return;
            int voidY = map.voidY;
            for (Player p : online(alive))
                if (p.getWorld().equals(world) && p.getLocation().getY() < voidY && !p.isDead()) p.setHealth(0);
        }

        String nextEvent() {
            int[][] ev = {{cfg("refill-1", 300), 0}, {cfg("refill-2", 600), 1}, {cfg("doom", 900), 2}, {cfg("game-end", 1200), 3}};
            String[] names = {"Remplissage", "Remplissage", "Doom", "Fin"};
            for (int[] e : ev) if (elapsed < e[0]) return names[e[1]] + " " + clock(e[0] - elapsed);
            return "Fin " + clock(0);
        }

        // -------------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("skw", Criteria.DUMMY,
                        Component.text("SKY", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                                .append(Component.text("WARS", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("skw");
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
            boolean playing = inGame.contains(p.getUniqueId()) && (state == State.PLAYING || state == State.ENDING);
            if (!playing) {
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(cageOf.size() + "/" + capacity(), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("Map : ", NamedTextColor.GRAY).append(Component.text(map == null ? "?" : map.name, NamedTextColor.LIGHT_PURPLE)));
                Kits.Kit kit = kitManager.selected(p);
                l.add(Component.text("Kit : ", NamedTextColor.GRAY).append(Component.text(kit == null ? "-" : kit.name(), NamedTextColor.YELLOW)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Ouverture dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.AQUA)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else if (state == State.PREPARING) l.add(Component.text("Préparation...", NamedTextColor.GOLD));
                else if (state == State.RESETTING) l.add(Component.text("Remise à zéro...", NamedTextColor.GOLD));
                else l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
            } else {
                l.add(Component.text("Prochain : ", NamedTextColor.GRAY).append(Component.text(state == State.ENDING ? "-" : nextEvent(), NamedTextColor.GREEN)));
                l.add(Component.text("  "));
                l.add(Component.text("Joueurs restants : ", NamedTextColor.GRAY).append(Component.text(alive.size() + "/" + total, NamedTextColor.WHITE)));
                l.add(Component.text("Kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                l.add(Component.text("Map : ", NamedTextColor.GRAY).append(Component.text(map == null ? "?" : map.name, NamedTextColor.LIGHT_PURPLE)));
                if (!alive.contains(p.getUniqueId())) l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft" + (games.size() > 1 || n > 1 ? " #" + n : ""), NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        // -------------------------------------------------------------- inscription et cages

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
            toCage(p);
            if (cageOf.containsKey(id)) broadcast("§a" + p.getName() + " §7a rejoint SkyWars (§f" + cageOf.size() + "/" + capacity() + "§7).");
        }

        /** Cage libre la plus éloignée des cages déjà prises (les joueurs sont bien répartis). */
        int freeCage() {
            List<int[]> cages = cages();
            int best = -1;
            double bestDist = -1;
            for (int i = 0; i < cages.size(); i++) {
                if (cageOf.containsValue(i)) continue;
                double d = Double.MAX_VALUE;
                for (int j : cageOf.values()) d = Math.min(d, Math.hypot(cages.get(i)[0] - cages.get(j)[0], cages.get(i)[2] - cages.get(j)[2]));
                if (cageOf.isEmpty()) d = random.nextDouble();
                if (d > bestDist) { bestDist = d; best = i; }
            }
            return best;
        }

        Location cageLocation(int i) {
            int[] c = cages().get(i);
            Location l = new Location(world, c[0] + 0.5, c[1] + 1, c[2] + 0.5);
            l.setDirection(center.toVector().subtract(l.toVector()).setY(0));
            return l;
        }

        void toCage(Player p) {
            int i = freeCage();
            if (i < 0) {
                p.sendMessage(PREFIX + "§cToutes les îles sont prises.");
                leave(p, true);
                return;
            }
            cageOf.put(p.getUniqueId(), i);
            Location l = cageLocation(i);
            p.teleport(l);
            later(() -> {
                if (!p.isOnline() || !cageOf.containsKey(p.getUniqueId()) || byPlayer.get(p.getUniqueId()) != this) return;
                if (!p.getWorld().equals(world)) return; // reparti ailleurs entre-temps (skyblock...) : on n'y touche pas
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(l);
                p.getInventory().setItem(0, kitManager.chooser());
                p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter SkyWars"));
                updateSidebar(p);
            });
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            byPlayer.remove(id);
            if (alive.contains(id) && state == State.PLAYING) {
                // abandon en pleine partie = éliminé, ses objets restent sur place
                if (p.getWorld().equals(world))
                    for (ItemStack it : p.getInventory().getContents()) if (it != null && !isKit(it)) world.dropItemNaturally(p.getLocation(), it);
                eliminate(p, killerOf(p), "§7a abandonné");
            }
            cageOf.remove(id);
            alive.remove(id);
            inGame.remove(id);
            boards.remove(id);
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
            if (hub != null && world != null && p.getWorld().equals(world)) p.teleport(hub.getSpawnLocation());
            if (mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
        }

        // -------------------------------------------------------------- partie

        /** Ouverture des cages : coffres remplis, verre retiré, survie, quelques secondes sans dégâts de chute. */
        void startGame() {
            List<Player> list = new ArrayList<>();
            for (Player p : online(players)) if (cageOf.containsKey(p.getUniqueId()) && p.getWorld().equals(world)) list.add(p);
            if (list.size() < 2) { state = State.WAITING; return; }
            original.clear();
            alive.clear();
            inGame.clear();
            kills.clear();
            lastHit.clear();
            lastHitAt.clear();
            clearEntities();
            gameBorder();
            world.setTime(6000);
            fillChests(false);
            state = State.PLAYING;
            elapsed = 0;
            total = list.size();
            lastMap = map.id;
            for (Player p : list) {
                int[] c = cages().get(cageOf.get(p.getUniqueId()));
                openCage(c);
                alive.add(p.getUniqueId());
                inGame.add(p.getUniqueId());
                p.closeInventory(); // menu des kits éventuellement ouvert
                p.getInventory().clear();
                p.setGameMode(GameMode.SURVIVAL);
                kitManager.give(p);
                p.setFallDistance(0);
                p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
                p.showTitle(Title.title(Component.text("GO !", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                        Component.text("Pille ton île, puis le centre !", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
            broadcast("§eLes cages sont ouvertes ! §7Dernier survivant gagnant. Remplissages à §f"
                    + clock(cfg("refill-1", 300)) + "§7 et §f" + clock(cfg("refill-2", 600)) + "§7, Doom à §f" + clock(cfg("doom", 900)) + "§7.");
            status();
        }

        /** Retire le verre de la cage (plancher compris) : le joueur tombe sur son île. */
        void openCage(int[] c) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 4; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        Block b = world.getBlockAt(c[0] + dx, c[1] + dy, c[2] + dz);
                        if (!b.getType().name().contains("GLASS")) continue;
                        record(b);
                        b.setType(Material.AIR, false);
                    }
        }

        void fillChests(boolean pearls) {
            int filled = 0;
            for (ChestSpot s : map.chests) {
                Block b = world.getBlockAt(s.x(), s.y(), s.z());
                if (!(b.getState(false) instanceof Chest)) {
                    record(b);
                    b.setType(Material.CHEST, false);
                }
                // état « vivant » (pas une copie) : les objets vont directement dans le coffre du monde
                if (!(b.getState(false) instanceof Chest chest)) continue;
                Inventory inv = chest.getBlockInventory();
                inv.clear();
                List<ItemStack> loot = switch (s.type()) {
                    case "center" -> centerLoot();
                    case "mid" -> midLoot();
                    default -> islandLoot();
                };
                if (pearls) loot.add(new ItemStack(Material.ENDER_PEARL));
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < inv.getSize(); i++) slots.add(i);
                Collections.shuffle(slots, random);
                for (int i = 0; i < loot.size() && i < slots.size(); i++) inv.setItem(slots.get(i), loot.get(i));
                filled++;
            }
            getLogger().info("Partie " + n + " : " + filled + " coffres remplis" + (pearls ? " (+ perles)" : "") + ".");
        }

        void refill(boolean pearls) {
            fillChests(pearls);
            broadcast("§6Les coffres ont été remplis !" + (pearls ? " §7Chacun contient une §5perle de l'Ender§7." : ""));
            for (Player p : online(alive)) p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 0.8f);
        }

        /** Doom : la bordure (de ce monde) se resserre sur l'île centrale, tous les survivants brillent. */
        void doom() {
            world.getWorldBorder().setSize(map.doomBorderSize, cfg("doom-shrink-time", 180));
            for (Player p : online(alive)) {
                if (mayTouch(p)) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false, false));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
                p.showTitle(Title.title(Component.text("DOOM", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                        Component.text("La bordure se resserre sur le centre !", NamedTextColor.RED)));
            }
            broadcast("§4DOOM ! §7La bordure se resserre sur l'île centrale. Fin de la partie à §f" + clock(cfg("game-end", 1200)) + "§7.");
        }

        // -------------------------------------------------------------- morts et fin

        Player killerOf(Player p) {
            Player k = p.getKiller();
            if (k != null && k != p) return k;
            UUID id = lastHit.get(p.getUniqueId());
            Long at = lastHitAt.get(p.getUniqueId());
            if (id != null && at != null && System.currentTimeMillis() - at < 10_000) return Bukkit.getPlayer(id);
            return null;
        }

        void eliminate(Player p, Player killer, String how) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            int place = alive.size() + 1;
            if (killer != null && killer != p && inGame.contains(killer.getUniqueId())) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " skywars kill");
            }
            status();
            String msg = killer != null && killer != p
                    ? "§c" + p.getName() + " §7" + how + " §7(par §c" + killer.getName() + "§7)"
                    : "§c" + p.getName() + " §7" + how;
            broadcast(msg + " §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            if (p.getWorld().equals(world)) world.strikeLightningEffect(p.getLocation());
            if (p.isOnline())
                p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                        Component.text("Place : " + place + "/" + total, NamedTextColor.GRAY)));
            if (alive.size() <= 1 && state == State.PLAYING) {
                Player winner = alive.isEmpty() ? null : Bukkit.getPlayer(alive.iterator().next());
                Bukkit.getScheduler().runTask(EemiSkyWars.this, () -> endGame(winner, true));
            }
        }

        /** Fin de partie ; « normal » = partie allée à son terme (récompenses EemiCore), pas un /skywars stop. */
        void endGame(Player winner, boolean normal) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            timer = cfg("end-time", 10);
            if (normal) {
                for (Player p : online(inGame)) eemi("reward " + p.getName() + " skywars play");
                if (winner != null && winner.isOnline()) eemi("reward " + winner.getName() + " skywars win");
            }
            status();
            Component title = winner != null
                    ? Component.text(winner.getName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD) // pseudo seul : titre court
                    : Component.text("Égalité !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
            String tieSub = "Fin de la partie";
            if (winner == null) {
                List<String> names = new ArrayList<>();
                for (Player p : online(alive)) names.add(p.getName());
                if (!normal) tieSub = "Partie arrêtée";
                else if (names.isEmpty()) tieSub = "Aucun survivant";
                else {
                    String joined = String.join(", ", names);
                    tieSub = joined.length() <= 38 ? joined : names.size() + " gagnants";
                }
            }
            for (Player p : online(players)) {
                if (!inGame.contains(p.getUniqueId())) continue;
                p.showTitle(Title.title(title, Component.text(winner == p ? "Tu es le dernier survivant !" : winner != null ? "gagne la partie !" : tieSub, NamedTextColor.YELLOW)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            if (winner != null) {
                winner.setInvulnerable(true);
                broadcast("§6" + winner.getName() + " §eremporte SkyWars §7avec §f" + kills.getOrDefault(winner.getUniqueId(), 0) + " kill(s)§7 !");
                for (int i = 0; i < 5; i++)
                    Bukkit.getScheduler().runTaskLater(EemiSkyWars.this, () -> {
                        if (winner.isOnline() && state == State.ENDING && winner.getWorld().equals(world)) firework(winner.getLocation());
                    }, i * 20L);
            } else broadcast("§7La partie se termine sans vainqueur.");
            UUID top = null;
            int topKills = 0;
            for (Map.Entry<UUID, Integer> e : kills.entrySet()) if (e.getValue() > topKills) { topKills = e.getValue(); top = e.getKey(); }
            if (top != null && Bukkit.getPlayer(top) != null)
                broadcast("§7Meilleur tueur : §f" + Bukkit.getPlayer(top).getName() + " §7(" + topKills + " kills)");
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, false);
            if (state == State.ENDING) rollback();
            if (state == State.STARTING) state = State.WAITING;
        }

        // -------------------------------------------------------------- remise en état

        void record(Block b) {
            if (world == null || !b.getWorld().equals(world)) return;
            original.putIfAbsent(key(b.getX(), b.getY(), b.getZ()), b.getBlockData());
        }

        void record(BlockState st) {
            if (world == null || !st.getWorld().equals(world)) return;
            original.putIfAbsent(key(st.getX(), st.getY(), st.getZ()), st.getBlockData());
        }

        /**
         * Fin : tout le monde en spectateur au centre, la map est remise en état, puis retour dans les cages.
         * Une copie sans plus aucun inscrit est supprimée directement.
         */
        void rollback() {
            state = State.RESETTING;
            alive.clear();
            inGame.clear();
            cageOf.clear();
            for (Player p : online(new HashSet<>(spectators))) stopSpectating(p);
            spectators.clear();
            if (n >= 2 && online(players).isEmpty()) {
                destroyGame(this);
                return;
            }
            for (Player p : online(players)) {
                p.setInvulnerable(false);
                if (p.getWorld().equals(world)) {
                    resetPlayer(p, GameMode.SPECTATOR);
                    p.teleport(center);
                }
            }
            status();
            clearEntities();
            List<Map.Entry<Long, BlockData>> todo = new ArrayList<>(original.entrySet());
            original.clear();
            restore(todo, 0);
        }

        void restore(List<Map.Entry<Long, BlockData>> todo, int from) {
            if (destroyed) return;
            int end = Math.min(todo.size(), from + 5000);
            for (int i = from; i < end; i++) {
                long k = todo.get(i).getKey();
                world.getBlockAt(kx(k), ky(k), kz(k)).setBlockData(todo.get(i).getValue(), false);
            }
            if (end < todo.size()) {
                Bukkit.getScheduler().runTaskLater(EemiSkyWars.this, () -> restore(todo, end), 1L);
                return;
            }
            clearEntities();
            for (Chest c : chestStates()) c.getBlockInventory().clear();
            gameBorder();
            getLogger().info("Partie " + n + " : map remise a zero (" + todo.size() + " blocs).");
            state = State.WAITING;
            if (n >= 2 && online(players).isEmpty()) {
                destroyGame(this);
                return;
            }
            // partie 1 : map suivante au hasard (pas la même deux fois de suite) ; une copie garde sa map
            if (n == 1 && !maps.isEmpty()) {
                setMap(pickMap(lastMap), null);
                if (maps.size() > 1) broadcast("§7Prochaine map : §d" + map.name);
            }
            for (Player p : online(players)) toCage(p);
            status();
        }

        List<Chest> chestStates() {
            List<Chest> l = new ArrayList<>();
            for (ChestSpot s : map.chests) if (world.getBlockAt(s.x(), s.y(), s.z()).getState(false) instanceof Chest c) l.add(c);
            return l;
        }

        void clearEntities() {
            if (world == null) return;
            for (Entity e : world.getEntities())
                if (e instanceof Item || e instanceof Projectile || e instanceof ExperienceOrb || e instanceof FallingBlock || e instanceof TNTPrimed) e.remove();
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        String stateName() {
            return switch (state) {
                case PREPARING -> "preparation";
                case WAITING -> "attente";
                case STARTING -> "décompte " + timer + " s";
                case PLAYING -> "en jeu " + clock(elapsed);
                case ENDING -> "fin";
                case RESETTING -> "remise à zéro";
            };
        }
    }

    // ------------------------------------------------------------------ loot (d'après le SkyWars Normal d'Hypixel)

    private int rnd(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private boolean chance(double c) {
        return random.nextDouble() < c;
    }

    private ItemStack ench(Material m, Enchantment e, int level) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.addEnchant(e, level, true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack potion(PotionType t) {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setBasePotionType(t);
        it.setItemMeta(meta);
        return it;
    }

    private Material armor(String piece) {
        String[] mats = {"LEATHER", "CHAINMAIL", "GOLDEN", "IRON"};
        int[] w = {30, 30, 15, 25};
        int r = random.nextInt(100), i = 0;
        while (r >= w[i]) { r -= w[i]; i++; }
        return Material.valueOf(mats[i] + "_" + piece);
    }

    /** Coffre d'île de départ : de quoi s'équiper (épée, armure, blocs, nourriture) et quelques bonus. */
    private List<ItemStack> islandLoot() {
        List<ItemStack> l = new ArrayList<>();
        String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        l.add(new ItemStack(chance(0.6) ? Material.STONE_SWORD : Material.IRON_SWORD));
        for (int i = 0; i < 2; i++) l.add(new ItemStack(armor(pieces[random.nextInt(4)])));
        l.add(new ItemStack(chance(0.5) ? Material.STONE : Material.OAK_PLANKS, rnd(24, 48)));
        l.add(switch (random.nextInt(3)) {
            case 0 -> new ItemStack(Material.COOKED_BEEF, rnd(3, 6));
            case 1 -> new ItemStack(Material.BREAD, rnd(4, 8));
            default -> new ItemStack(Material.BAKED_POTATO, rnd(4, 8));
        });
        if (chance(0.5)) l.add(new ItemStack(chance(0.6) ? Material.STONE_PICKAXE : Material.IRON_PICKAXE));
        if (chance(0.25)) l.add(new ItemStack(Material.STONE_AXE));
        if (chance(0.3)) { l.add(new ItemStack(Material.BOW)); l.add(new ItemStack(Material.ARROW, rnd(8, 16))); }
        if (chance(0.4)) l.add(new ItemStack(chance(0.5) ? Material.SNOWBALL : Material.EGG, rnd(8, 16)));
        if (chance(0.3)) l.add(new ItemStack(Material.WATER_BUCKET));
        if (chance(0.15)) l.add(new ItemStack(Material.LAVA_BUCKET));
        if (chance(0.12)) l.add(new ItemStack(Material.GOLDEN_APPLE));
        return l;
    }

    /** Coffre des îlots : un cran au-dessus (fer enchanté, arcs, pommes d'or, parfois une perle). */
    private List<ItemStack> midLoot() {
        List<ItemStack> l = new ArrayList<>();
        String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        l.add(ench(Material.valueOf("IRON_" + pieces[random.nextInt(4)]), Enchantment.PROTECTION, 1));
        if (chance(0.5)) l.add(ench(Material.IRON_SWORD, Enchantment.SHARPNESS, 1));
        if (chance(0.35)) { l.add(ench(Material.BOW, Enchantment.POWER, 1)); l.add(new ItemStack(Material.ARROW, rnd(12, 24))); }
        if (chance(0.5)) l.add(new ItemStack(Material.GOLDEN_APPLE, rnd(1, 2)));
        if (chance(0.2)) l.add(new ItemStack(Material.ENDER_PEARL));
        if (chance(0.25)) { l.add(new ItemStack(Material.TNT, rnd(2, 4))); l.add(new ItemStack(Material.FLINT_AND_STEEL)); }
        if (chance(0.3)) l.add(new ItemStack(Material.EXPERIENCE_BOTTLE, rnd(6, 12)));
        if (chance(0.25)) l.add(new ItemStack(Material.FISHING_ROD));
        l.add(new ItemStack(Material.OAK_PLANKS, rnd(16, 32)));
        if (chance(0.5)) l.add(new ItemStack(Material.COOKED_BEEF, rnd(3, 6)));
        return l;
    }

    /** Coffre du centre : diamant et fer enchantés, perles, pommes d'or, bâton de recul, potions. */
    private List<ItemStack> centerLoot() {
        List<ItemStack> l = new ArrayList<>();
        l.add(ench(chance(0.5) ? Material.DIAMOND_HELMET : Material.DIAMOND_BOOTS, Enchantment.PROTECTION, rnd(1, 2)));
        l.add(ench(chance(0.5) ? Material.IRON_CHESTPLATE : Material.IRON_LEGGINGS, Enchantment.PROTECTION, 2));
        if (chance(0.4)) l.add(ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1));
        else if (chance(0.5)) l.add(ench(Material.IRON_SWORD, Enchantment.SHARPNESS, 2));
        if (chance(0.6)) l.add(new ItemStack(Material.ENDER_PEARL, rnd(1, 2)));
        l.add(new ItemStack(Material.GOLDEN_APPLE, rnd(1, 3)));
        if (chance(0.25)) l.add(ench(Material.STICK, Enchantment.KNOCKBACK, rnd(1, 2)));
        if (chance(0.3)) { l.add(ench(Material.BOW, Enchantment.POWER, 2)); l.add(new ItemStack(Material.ARROW, rnd(16, 32))); }
        if (chance(0.3)) l.add(potion(chance(0.5) ? PotionType.SWIFTNESS : PotionType.REGENERATION));
        if (chance(0.2)) l.add(new ItemStack(Material.LAVA_BUCKET));
        l.add(new ItemStack(Material.STONE, rnd(16, 32)));
        return l;
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class);
        FireworkMeta m = fw.getFireworkMeta();
        m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.AQUA, Color.WHITE).withFade(Color.BLUE).build());
        m.setPower(1);
        fw.setFireworkMeta(m);
    }

    // ------------------------------------------------------------------ objets

    private ItemStack named(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack kit(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ événements : blocs

    private boolean inWorld(Block b) {
        return isSwWorld(b.getWorld());
    }

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemiskywars.admin");
    }

    /** Pendant la partie, les survivants cassent et posent n'importe quel bloc dans leur monde ; sinon la map est figée. */
    private boolean canBuild(Player p, Block b) {
        Game g = byPlayer.get(p.getUniqueId());
        return g != null && g.state == State.PLAYING && g.alive.contains(p.getUniqueId()) && b.getWorld().equals(g.world);
    }

    /** Partie qui enregistre les modifications de ce monde (null : aucune, la map est figée). */
    private Game recorder(World w) {
        Game g = gameAt(w);
        return g != null && g.recording() ? g : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (!inWorld(e.getBlock()) || admin(e.getPlayer())) return;
        if (!canBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakRecord(BlockBreakEvent e) {
        Game g = recorder(e.getBlock().getWorld());
        if (g != null) g.record(e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (!inWorld(e.getBlock()) || admin(e.getPlayer())) return;
        if (!canBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceRecord(BlockPlaceEvent e) {
        Game g = recorder(e.getBlock().getWorld());
        if (g != null) g.record(e.getBlockReplacedState());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!inWorld(e.getBlock()) || admin(e.getPlayer())) return;
        if (!canBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
        else byPlayer.get(e.getPlayer().getUniqueId()).record(e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!inWorld(e.getBlock()) || admin(e.getPlayer())) return;
        if (!canBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
        else byPlayer.get(e.getPlayer().getUniqueId()).record(e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        World w = e.getLocation().getWorld();
        if (!isSwWorld(w)) return;
        Game g = recorder(w);
        if (g == null) { e.blockList().clear(); return; }
        for (Block b : e.blockList()) g.record(b);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!inWorld(e.getBlock())) return;
        Game g = recorder(e.getBlock().getWorld());
        if (g == null) { e.blockList().clear(); return; }
        for (Block b : e.blockList()) g.record(b);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!inWorld(e.getToBlock())) return;
        Game g = recorder(e.getToBlock().getWorld());
        if (g == null) e.setCancelled(true);
        else g.record(e.getToBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!inWorld(e.getBlock())) return;
        Game g = recorder(e.getBlock().getWorld());
        if (g == null) e.setCancelled(true);
        else g.record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!inWorld(e.getBlock())) return;
        Game g = recorder(e.getBlock().getWorld());
        if (g == null) e.setCancelled(true);
        else g.record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        Game g = recorder(e.getBlock().getWorld());
        if (g != null) g.record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        Game g = recorder(e.getBlock().getWorld());
        if (g != null) g.record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        Game g = recorder(e.getBlock().getWorld());
        if (g != null) g.record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (inWorld(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (!inWorld(e.getBlock())) return;
        Game g = recorder(e.getBlock().getWorld());
        if (g == null) { if (!(e.getEntity() instanceof FallingBlock)) e.setCancelled(true); return; }
        g.record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Game g = recorder(e.getBlock().getWorld());
        if (g == null) return;
        g.record(e.getBlock());
        for (Block b : e.getBlocks()) { g.record(b); g.record(b.getRelative(e.getDirection())); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Game g = recorder(e.getBlock().getWorld());
        if (g == null) return;
        g.record(e.getBlock());
        g.record(e.getBlock().getRelative(e.getDirection()));
        for (Block b : e.getBlocks()) { g.record(b); g.record(b.getRelative(e.getDirection())); }
    }

    // ------------------------------------------------------------------ événements : joueurs

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isSwWorld(e.getLocation().getWorld())
                && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        UUID id = victim.getUniqueId();
        Game g = byPlayer.get(id);
        if (g == null) {
            if (isSwWorld(victim.getWorld())) e.setCancelled(true);
            return;
        }
        if (g.state != State.PLAYING || !g.alive.contains(id)) {
            e.setCancelled(true);
            return;
        }
        // chute depuis la cage : pas de dégâts les premières secondes
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && g.elapsed <= cfg("no-fall-time", 6)) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            Player attacker = d instanceof Player pl ? pl : d instanceof Projectile proj && proj.getShooter() instanceof Player pl ? pl : null;
            if (attacker != null && attacker != victim) {
                g.lastHit.put(id, attacker.getUniqueId());
                g.lastHitAt.put(id, System.currentTimeMillis());
            }
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
        EntityDamageEvent last = p.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = last == null ? EntityDamageEvent.DamageCause.CUSTOM : last.getCause();
        String how = p.getLocation().getY() < g.map.voidY || cause == EntityDamageEvent.DamageCause.VOID ? "est tombé dans le vide"
                : switch (cause) {
                    case FALL -> "s'est écrasé";
                    case WORLD_BORDER -> "a été rattrapé par la bordure";
                    case LAVA, FIRE, FIRE_TICK -> "a brûlé";
                    case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "a explosé";
                    case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE -> "a été tué";
                    default -> "est mort";
                };
        g.eliminate(p, g.killerOf(p), how);
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && p.isDead()) p.spigot().respawn(); }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || g.center == null) return;
        if (g.inGame.contains(p.getUniqueId()) || g.running()) {
            e.setRespawnLocation(g.center);
            later(() -> { if (mayTouch(p)) p.setGameMode(GameMode.SPECTATOR); });
            return;
        }
        Integer c = g.cageOf.get(p.getUniqueId());
        if (c != null) e.setRespawnLocation(g.cageLocation(c));
    }

    /** Dans la cage avant l'ouverture : on ne sort pas (la cage de verre suffit, sécurité si elle a été cassée). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null) return;
        Integer c = g.cageOf.get(p.getUniqueId());
        if (c == null || g.state == State.PLAYING || g.state == State.ENDING || g.state == State.RESETTING) return;
        Location t = e.getTo();
        if (!t.getWorld().equals(g.world)) return;
        int[] cg = g.cages().get(c);
        if (Math.abs(t.getX() - (cg[0] + 0.5)) > 1.2 || Math.abs(t.getZ() - (cg[2] + 0.5)) > 1.2 || t.getY() < cg[1] - 1) {
            Location back = g.cageLocation(c);
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            e.setTo(back);
        }
    }

    /**
     * Quitter le monde de SA partie (/lobby...) = quitter la partie. La partie 1 qui change de map téléporte ses
     * joueurs d'un monde d'origine à l'autre : son monde a déjà changé, ce n'est pas un départ.
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
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (kitManager.isChooser(it)) {
            e.setCancelled(true);
            if (!g.running()) kitManager.open(p);
            return;
        }
        if (it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté SkyWars.");
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
        boolean admin = sender.hasPermission("eemiskywars.admin");
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
                p.sendMessage(PREFIX + "§7Tu as quitté SkyWars.");
            }
            case "start" -> {
                if (!admin) return true;
                Game g = targetGame(sender, p, args);
                if (g == null) return true;
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cLa partie " + g.n + " est déjà en cours (ou en préparation)."); return true; }
                if (g.cageOf.size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs dans les cages."); return true; }
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
                            + " §8| §e" + g.stateName() + " §8| §7map §d" + (g.map == null ? "?" : g.map.name)
                            + " §8| §f" + g.players.size() + "§7 inscrits, §f" + g.alive.size() + "§7 en vie, §f" + g.spectators.size() + "§7 spect.");
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, World> en : models.entrySet()) sb.append(en.getValue().getName()).append(" ");
                sender.sendMessage("§8   modèles prêts : §7" + (sb.isEmpty() ? "aucun" : sb.toString().trim()) + (modelBusy.isEmpty() ? "" : " §8(en cours : " + modelBusy + ")"));
            }
            case "reload" -> {
                if (!admin) return true;
                if (!byPlayer.isEmpty() || games.size() > 1 || copying > 0) {
                    sender.sendMessage(PREFIX + "§cPersonne ne doit être inscrit et seule la partie 1 doit exister (/skywars parties).");
                    return true;
                }
                reloadAll();
                sender.sendMessage(PREFIX + "§aConfig rechargée (" + maps.size() + " maps), modèles refaits.");
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
            case "map" -> {
                if (!admin) return true;
                Game g1 = games.get(1);
                if (args.length < 2 || !maps.containsKey(args[1].toLowerCase()) || g1 == null) {
                    sender.sendMessage(PREFIX + "§7Maps : §f" + String.join(", ", maps.keySet()) + " §7(prochaine de la partie 1 : §d"
                            + (g1 == null || g1.map == null ? "?" : g1.map.id) + "§7)");
                    return true;
                }
                if (g1.running() || g1.state == State.PREPARING) { sender.sendMessage(PREFIX + "§cLa partie 1 est en cours : attends la fin."); return true; }
                // les joueurs déjà en cage passent dans les cages de la nouvelle map
                List<Player> caged = new ArrayList<>();
                for (Player w : online(g1.players)) if (g1.cageOf.containsKey(w.getUniqueId())) caged.add(w);
                g1.cageOf.clear();
                g1.state = State.WAITING;
                GameMap m = maps.get(args[1].toLowerCase());
                g1.setMap(m, null);
                int built = 0;
                for (int[] c : m.cages) built += buildCage(m.world, c[0], c[1], c[2]);
                if (built > 0) remakeModel(m.base);
                for (Player w : caged) g1.toCage(w);
                g1.broadcast("§7Prochaine map : §d" + m.name);
                sender.sendMessage(PREFIX + "§aProchaine map de la partie 1 : " + m.name);
                status();
            }
            case "editmap", "setcenter", "addcage", "scanchests" -> {
                if (!admin) return true;
                editCommand(sender, p, sub, args);
            }
            default -> sender.sendMessage(PREFIX + "§7/skywars <join|leave|spectate [joueur]|kit>"
                    + (admin ? " §8| admin : start [n], stop [n], parties, reload, map [id], editmap <id> [monde], setcenter, addcage, scanchests [rayon]" : ""));
        }
        return true;
    }

    /** Recharge le config, refait les cages manquantes, la map de la partie 1 et les modèles. */
    private void reloadAll() {
        Game g1 = games.get(1);
        String current = g1 == null || g1.map == null ? null : g1.map.id;
        loadSettings();
        setupMaps();
        if (g1 != null) g1.setMap(current != null && maps.containsKey(current) ? maps.get(current) : pickMap(null), null);
        remakeAllModels();
        status();
    }

    // ------------------------------------------------------------------ ajout de maps (admin)

    /** La section « maps » du jar est recopiée dans le config du serveur avant la première modification. */
    private void ensureMapsInFile() {
        ConfigurationSection file = getConfig().getConfigurationSection("maps");
        if (file != null && !file.getKeys(false).isEmpty()) return;
        if (getConfig().getDefaults() == null) return;
        ConfigurationSection def = getConfig().getDefaults().getConfigurationSection("maps");
        if (def == null) return;
        for (String k : def.getKeys(true)) if (!def.isConfigurationSection(k)) getConfig().set("maps." + k, def.get(k));
    }

    private static String pos(double x, double y, double z) {
        return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", x, y, z);
    }

    /** Enregistre le config, puis recharge les maps (et refait les modèles) si personne n'est inscrit. */
    private void saveAndReload(CommandSender sender) {
        saveConfig();
        if (byPlayer.isEmpty() && games.size() <= 1 && copying == 0) reloadAll();
        else sender.sendMessage(PREFIX + "§7Enregistré : pris en compte au prochain §f/skywars reload§7.");
    }

    private void editCommand(CommandSender sender, Player p, String sub, String[] args) {
        ensureMapsInFile();
        if (sub.equals("editmap")) {
            if (args.length < 2) { sender.sendMessage(PREFIX + "§7/skywars editmap <id> [monde] §8(map en édition : " + (editMap == null ? "aucune" : editMap) + ")"); return; }
            String id = args[1].toLowerCase();
            String path = "maps." + id;
            if (getConfig().getConfigurationSection(path) == null || getConfig().getConfigurationSection(path).getKeys(false).isEmpty()) {
                // nouvelle map : monde donné ou celui du joueur, centre = position du joueur
                World w = args.length > 2 ? Bukkit.getWorld(args[2]) : p != null ? p.getWorld() : null;
                if (w == null) { sender.sendMessage(PREFIX + "§cMonde introuvable (/skywars editmap <id> <monde>)."); return; }
                Location c = p != null && p.getWorld().equals(w) ? p.getLocation() : w.getSpawnLocation();
                getConfig().set(path + ".name", id);
                getConfig().set(path + ".world", w.getName());
                getConfig().set(path + ".center", pos(c.getBlockX() + 0.5, c.getBlockY(), c.getBlockZ() + 0.5));
                getConfig().set(path + ".border-center", c.getBlockX() + "," + c.getBlockZ());
                getConfig().set(path + ".border-size", 260);
                getConfig().set(path + ".doom-border-size", 24);
                getConfig().set(path + ".void-y", Math.max(w.getMinHeight() + 5, c.getBlockY() - 60));
                getConfig().set(path + ".cages", new ArrayList<String>());
                getConfig().set(path + ".chests", new ArrayList<String>());
                saveConfig();
                sender.sendMessage(PREFIX + "§aMap " + id + " créée (monde " + w.getName() + ", centre = ta position). §7Ensuite : "
                        + "§f/skywars addcage §7dans chaque cage, §f/skywars scanchests§7, puis règle name/border-size/void-y dans le config.");
            } else sender.sendMessage(PREFIX + "§aMap en édition : " + id);
            editMap = id;
            return;
        }
        if (editMap == null) { sender.sendMessage(PREFIX + "§cChoisis d'abord la map : /skywars editmap <id>."); return; }
        String path = "maps." + editMap;
        ConfigurationSection sec = getConfig().getConfigurationSection(path);
        if (sec == null) { sender.sendMessage(PREFIX + "§cMap " + editMap + " absente du config."); return; }
        World w = Bukkit.getWorld(sec.getString("world", ""));
        if (w == null) { sender.sendMessage(PREFIX + "§cMonde de la map introuvable."); return; }
        Game g1 = games.get(1);
        if (g1 != null && g1.running() && w.equals(g1.world)) { sender.sendMessage(PREFIX + "§cLa partie 1 joue sur cette map : attends la fin."); return; }
        switch (sub) {
            case "setcenter" -> {
                if (p == null || !p.getWorld().equals(w)) { sender.sendMessage(PREFIX + "§cVa dans le monde " + w.getName() + "."); return; }
                Location c = p.getLocation();
                sec.set("center", pos(c.getBlockX() + 0.5, c.getBlockY(), c.getBlockZ() + 0.5));
                sec.set("border-center", c.getBlockX() + "," + c.getBlockZ());
                sender.sendMessage(PREFIX + "§aCentre de " + editMap + " : " + c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ());
                saveAndReload(sender);
            }
            case "addcage" -> {
                if (p == null || !p.getWorld().equals(w)) { sender.sendMessage(PREFIX + "§cVa dans le monde " + w.getName() + "."); return; }
                Location l = p.getLocation();
                int x = l.getBlockX(), y = l.getBlockY() - 1, z = l.getBlockZ();
                List<String> list = new ArrayList<>(sec.getStringList("cages"));
                list.add(x + "," + y + "," + z);
                sec.set("cages", list);
                int glass = buildCage(w, x, y, z);
                sender.sendMessage(PREFIX + "§aCage " + list.size() + " ajoutée en " + x + " " + y + " " + z
                        + (glass > 0 ? " §7(" + glass + " blocs de verre posés sur l'air)" : " §7(cage déjà construite)"));
                saveAndReload(sender);
            }
            case "scanchests" -> {
                Location c = loc(w, sec.getString("center", "0.5,100,0.5"));
                int r = cfg("scan-radius", 150);
                if (args.length > 1) try { r = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { }
                int centerR = cfg("scan-center-radius", 20), islandR = cfg("scan-island-radius", 15);
                int floorY = sec.getInt("cage-floor-y", 111);
                List<int[]> cg = new ArrayList<>();
                for (String s : sec.getStringList("cages")) {
                    String[] q = s.split(",");
                    cg.add(q.length >= 3 ? new int[]{Integer.parseInt(q[0].trim()), Integer.parseInt(q[1].trim()), Integer.parseInt(q[2].trim())}
                            : new int[]{Integer.parseInt(q[0].trim()), floorY, Integer.parseInt(q[1].trim())});
                }
                List<String> found = new ArrayList<>();
                int[] count = new int[3];
                int cx = c.getBlockX(), cz = c.getBlockZ();
                for (int chx = (cx - r) >> 4; chx <= (cx + r) >> 4; chx++)
                    for (int chz = (cz - r) >> 4; chz <= (cz + r) >> 4; chz++)
                        for (BlockState st : w.getChunkAt(chx, chz).getTileEntities()) {
                            if (!(st instanceof Chest)) continue;
                            double d = Math.hypot(st.getX() - c.getX(), st.getZ() - c.getZ());
                            if (d > r) continue;
                            String type;
                            if (d <= centerR) type = "center";
                            else {
                                double near = Double.MAX_VALUE;
                                for (int[] k : cg) near = Math.min(near, Math.hypot(st.getX() - k[0], st.getZ() - k[2]));
                                type = near <= islandR ? "island" : "mid";
                            }
                            count[type.equals("island") ? 0 : type.equals("mid") ? 1 : 2]++;
                            found.add(st.getX() + " " + st.getY() + " " + st.getZ() + " " + type);
                        }
                sec.set("chests", found);
                sender.sendMessage(PREFIX + "§a" + found.size() + " coffres trouvés dans un rayon de " + r + " : §f" + count[0] + " île, "
                        + count[1] + " mid, " + count[2] + " centre§a.");
                if (cg.isEmpty()) sender.sendMessage(PREFIX + "§eAucune cage : ajoute-les d'abord (/skywars addcage), sinon aucun coffre n'est classé « île ».");
                saveAndReload(sender);
            }
            default -> { }
        }
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
