package fr.eemicraft.bridge;

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
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
 * The Bridge d'EEMICRAFT, d'après le Bridge d'Hypixel. Rouge (ouest) contre Bleu (est) : deux îles reliées
 * par un pont étroit au-dessus du vide. Sauter dans le but de l'équipe adverse marque un point ; tout le monde
 * repart alors de sa cage de verre (les blocs posés restent). Une mort renvoie simplement à sa base avec le kit
 * rechargé. On ne casse que les blocs posés par les joueurs.
 *
 * Plusieurs parties en même temps : la partie 1 joue dans le monde d'origine (« thebridge », construit par le
 * plugin), les parties n ≥ 2 dans des copies « thebridge_n » faites depuis le modèle propre « thebridge_modele »
 * (copie de la map neuve, où personne ne va). Toute la logique d'état est dans {@link Game}.
 */
public final class EemiBridge extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§9The Bridge§8] §7";
    /** Position du but et du point de départ sur l'île, en blocs depuis le centre de l'île (vers l'arrière > 0). */
    private static final int GOAL_OFFSET = 5;
    private static final int SPAWN_OFFSET = -3;
    /** Position des joueurs dans la cage (5x5) selon leur rang dans l'équipe. */
    private static final double[][] CAGE_SLOTS = {{0, 0}, {1.5, 1.5}, {-1.5, -1.5}, {1.5, -1.5}, {-1.5, 1.5}};

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Side {
        ROUGE("Rouge", NamedTextColor.RED, Color.RED, Material.RED_TERRACOTTA, Material.RED_CONCRETE,
                Material.RED_STAINED_GLASS, "cherry"),
        BLEU("Bleu", NamedTextColor.BLUE, Color.fromRGB(0x3C44AA), Material.BLUE_TERRACOTTA, Material.BLUE_CONCRETE,
                Material.BLUE_STAINED_GLASS, "spruce");

        final String label;
        final NamedTextColor color;
        final Color armor;
        final Material terracotta;
        final Material concrete;
        final Material glass;
        /** Essence des arbres de l'île (cerisier pour Rouge, sapin pour Bleu). */
        final String wood;

        Side(String label, NamedTextColor color, Color armor, Material terracotta, Material concrete, Material glass, String wood) {
            this.label = label;
            this.color = color;
            this.armor = armor;
            this.terracotta = terracotta;
            this.concrete = concrete;
            this.glass = glass;
            this.wood = wood;
        }

        Side other() {
            return this == ROUGE ? BLEU : ROUGE;
        }

        /** Nom accordé avec « équipe » (féminin) : Rouge, Bleue. */
        String fem() {
            return label.equals("Bleu") ? "Bleue" : label.equals("Vert") ? "Verte" : label;
        }

        String code() {
            return this == ROUGE ? "§c" : "§9";
        }

        String key() {
            return name().toLowerCase();
        }

        /** -1 pour Rouge (île ouest), +1 pour Bleu. */
        int dir() {
            return this == ROUGE ? -1 : 1;
        }
    }

    /** Un bloc à poser lors de la construction (data null = air). */
    private record Op(int x, int y, int z, BlockData data) {}

    private NamespacedKey kitKey;
    /** Nom du monde de la partie 1 (base des copies). */
    private String base;
    private int cy, distance, radius, bridgeHalf, lobbyY, cageHeight, voidY;
    private final Map<Material, BlockData> dataCache = new HashMap<>();
    /** Blocs du milieu (pont + mur) cassables et leur état d'origine (calculé dans loadSettings). */
    private Map<Long, BlockData> middle = new HashMap<>();

    /** Parties par numéro (1 = monde d'origine). */
    private final TreeMap<Integer, Game> games = new TreeMap<>();
    /** Modèle propre (copie de la map neuve) qui sert à faire les copies. */
    private World model;
    private boolean modelBusy;
    /** Copies du modèle en cours (on ne refait pas le modèle pendant ce temps). */
    private int copying;
    private String lastStatus;
    private long lastStatusAt;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        saveDefaultConfig();
        loadSettings();
        World w = setupWorld();
        getServer().getPluginManager().registerEvents(this, this);
        if (w != null) {
            // copies restées d'un arrêt précédent
            WorldClone.cleanup(this, w, base, maxInstances());
            World oldModel = Bukkit.getWorld(modelName());
            if (oldModel != null) Bukkit.unloadWorld(oldModel, false);
            Game g1 = new Game(1);
            games.put(1, g1);
            g1.attach(w);
            // map neuve à chaque démarrage, puis modèle propre
            Bukkit.getScheduler().runTaskLater(this, () -> g1.rebuild(null, this::remakeModel), 100L);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Game g : new ArrayList<>(games.values())) g.fastTick();
        }, 5L, 5L);
        // statut initial pour EemiCore (une fois tous les plugins actives)
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Game g : games.values()) {
            for (Player p : online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (g.inWorld(p)) sendToHub(p);
            }
            for (Player p : online(g.spectators)) if (g.inWorld(p)) sendToHub(p);
            if (g.world != null && g.index == 1) {
                g.removePlaced();
                if (g.caged) g.removeCages();
            }
        }
        // les copies et le modèle sont refaits au prochain démarrage
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "thebridge");
        cy = cfg("floor-y", 100);
        distance = cfg("island-distance", 30);
        radius = cfg("island-radius", 12);
        bridgeHalf = Math.max(0, Math.min(2, cfg("bridge-half-width", 1)));
        lobbyY = cfg("lobby-y", 140);
        cageHeight = cfg("cage-height", 10);
        voidY = cfg("void-y", 84);
        for (Game g : games.values()) if (g.world != null) g.lobby = new Location(g.world, 0.5, lobbyY + 1, 0.5);
        computeMiddle();
    }

    /**
     * Blocs du « milieu » cassables (breakable-middle) : pont et mur plein dessous, seulement les colonnes
     * hors des îles (celles dont le dessus, en floor-y, reste un bloc du pont une fois les îles posées).
     * Buts, cages et points de départ sont sur les îles, donc jamais dedans. Même géométrie pour toutes les copies.
     */
    private void computeMiddle() {
        List<Op> br = new ArrayList<>();
        bridge(br);
        List<Op> isl = new ArrayList<>();
        for (Side s : Side.values()) island(isl, s);
        Set<Long> islandKeys = new HashSet<>();
        for (Op o : isl) islandKeys.add(key(o.x, o.y, o.z));
        Map<Long, BlockData> m = new HashMap<>();
        for (Op o : br) {
            long k = key(o.x, o.y, o.z);
            if (o.data == null || islandKeys.contains(k) || islandKeys.contains(key(o.x, cy, o.z))) continue;
            m.put(k, o.data);
        }
        middle = m;
    }

    private boolean breakableMiddle() {
        return getConfig().getBoolean("breakable-middle", true);
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private int maxPlayers() {
        return cfg("max-players", 8);
    }

    private String modelName() {
        return base + "_modele";
    }

    /** Charge le monde de la partie 1, ou le crée vide (comme EemiDuels) s'il n'existe pas. */
    private World setupWorld() {
        World w = Bukkit.getWorld(base);
        if (w == null) {
            w = new WorldCreator(base).generator(new VoidGenerator(lobbyY + 1)).createWorld();
            if (w != null) getLogger().info("Monde " + base + " cree (vide).");
        }
        if (w == null) getLogger().severe("Impossible de charger ou créer le monde " + base + " : The Bridge désactivé.");
        return w;
    }

    /** Gamerules d'un monde de partie (partie 1, copies, modèle). */
    private void applyRules(World w) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, false);
        w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        w.setGameRule(GameRules.FALL_DAMAGE, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.LOCATOR_BAR, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setSpawnLocation(new Location(w, 0.5, lobbyY + 1, 0.5));
    }

    /** Générateur vide : le monde n'a que ce que le plugin construit. */
    private static final class VoidGenerator extends ChunkGenerator {
        private final int spawnY;

        VoidGenerator(int spawnY) {
            this.spawnY = spawnY;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, spawnY, 0.5);
        }
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
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

    // ------------------------------------------------------------------ mondes de partie

    /** Monde d'une partie (origine ou copie) ou modèle : toutes les protections du jeu s'y appliquent. */
    private boolean isGameWorld(World w) {
        if (w == null || base == null) return false;
        return WorldClone.index(base, w.getName()) > 0 || w.getName().equals(modelName());
    }

    /** Partie qui joue dans ce monde (null pour le modèle ou un autre monde). */
    private Game gameAt(World w) {
        if (w == null) return null;
        for (Game g : games.values()) if (w.equals(g.world)) return g;
        return null;
    }

    /** Partie où le joueur est inscrit (ou en attente de la copie). */
    private Game gameOf(UUID id) {
        for (Game g : games.values()) if (g.players.contains(id) || g.pending.contains(id)) return g;
        return null;
    }

    private Game spectatorGame(UUID id) {
        for (Game g : games.values()) if (g.spectators.contains(id)) return g;
        return null;
    }

    /** Refait le modèle propre depuis la partie 1 (après construction de sa map). */
    private void remakeModel() {
        Game g1 = games.get(1);
        if (g1 == null || g1.world == null || modelBusy) return;
        modelBusy = true;
        if (model != null) {
            Bukkit.unloadWorld(model, false);
            model = null;
        }
        World loaded = Bukkit.getWorld(modelName());
        if (loaded != null) Bukkit.unloadWorld(loaded, false);
        // l'ancien dossier du modèle est supprimé par la copie elle-même
        WorldClone.create(this, g1.world, modelName(), w -> {
            modelBusy = false;
            model = w;
            if (w != null) {
                applyRules(w);
                getLogger().info("Modèle " + w.getName() + " prêt.");
            } else getLogger().warning("Modèle " + modelName() + " impossible : pas de nouvelles parties.");
        });
    }

    /** Crée une nouvelle partie (copie du modèle) pour ce joueur. */
    private void createGame(Player p) {
        int n = 2;
        while (games.containsKey(n)) n++;
        Game g = new Game(n);
        games.put(n, g);
        g.pending.add(p.getUniqueId());
        p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
        String name = WorldClone.name(base, n);
        copying++;
        WorldClone.create(this, model, name, w -> {
            copying--;
            if (games.get(g.index) != g) {
                // partie abandonnée pendant la copie
                if (w != null) WorldClone.destroy(this, w, base, this::sendToHub);
                return;
            }
            if (w == null) {
                games.remove(g.index);
                for (Player q : online(g.pending)) q.sendMessage(PREFIX + "§cImpossible de préparer la partie, réessaie.");
                g.pending.clear();
                reportStatus();
                return;
            }
            applyRules(w);
            g.attach(w);
            World hub = hubWorld();
            for (Player q : online(new LinkedHashSet<>(g.pending))) {
                // parti ailleurs entre-temps (autre jeu) : on ne l'emmène pas
                if (hub != null && !q.getWorld().equals(hub)) {
                    g.pending.remove(q.getUniqueId());
                    q.sendMessage(PREFIX + "§7Ta partie de The Bridge est prête, mais tu es ailleurs : inscription annulée.");
                    continue;
                }
                g.pending.remove(q.getUniqueId());
                g.enter(q);
            }
            g.pending.clear();
            if (g.players.isEmpty()) destroyGame(g);
            reportStatus();
        });
        reportStatus();
    }

    /** Détruit une copie (jamais la partie 1) : joueurs renvoyés au hub, monde supprimé. */
    private void destroyGame(Game g) {
        if (g.index == 1) return;
        games.remove(g.index);
        for (Player p : online(g.players)) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        g.players.clear();
        g.pending.clear();
        g.spectators.clear();
        if (g.world != null) WorldClone.destroy(this, g.world, base, this::sendToHub);
        reportStatus();
    }

    /** Partie en attente (ou en décompte) avec la place pour n joueurs, la plus remplie d'abord. */
    private Game openGame(int n, Game except) {
        Game best = null;
        for (Game g : games.values()) {
            if (g == except || (g.state != State.WAITING && g.state != State.STARTING)) continue;
            if (g.size() + n > maxPlayers()) continue;
            if (best == null || g.size() > best.size()) best = g;
        }
        return best;
    }

    private World hubWorld() {
        return Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
    }

    // ------------------------------------------------------------------ géométrie (identique dans toutes les copies)

    private int islandX(Side s) {
        return s.dir() * distance;
    }

    /** Centre (bloc) du trou du but de l'équipe. */
    private int goalX(Side s) {
        return islandX(s) + s.dir() * GOAL_OFFSET;
    }

    private int spawnX(Side s) {
        return islandX(s) + s.dir() * SPAWN_OFFSET;
    }

    /**
     * Rayon (horizontal) de la zone de départ protégée autour du point d'apparition de chaque équipe.
     * spawn-protection < 0 = automatique : jusqu'au bord avant de l'île (côté pont), calculé depuis la forme
     * de l'île (islandRadius vers le pont, moins le recul du point de départ) ; 8 avec la map de base.
     * Couvre aussi la cage (intérieur 5x5 + verre, à 3 blocs du point de départ).
     */
    private double spawnProtection() {
        double v = getConfig().getDouble("spawn-protection", -1);
        if (v >= 0) return v;
        return Math.max(4, Math.ceil(islandRadius(Math.PI) - Math.abs(SPAWN_OFFSET)));
    }

    /** La colonne (x, z) est-elle dans la zone de départ d'une des deux équipes ? Même géométrie dans toutes les copies. */
    boolean inSpawnZone(int x, int z) {
        double r = spawnProtection();
        for (Side s : Side.values())
            if (Math.hypot(x - spawnX(s), z) < r) return true;
        return false;
    }

    private int cageFloor() {
        return cy + cageHeight;
    }

    /** Le joueur est-il tombé dans le trou du but de cette équipe ? */
    private boolean inGoal(Location l, Side s) {
        return Math.abs(l.getX() - (goalX(s) + 0.5)) < 1.5 && Math.abs(l.getZ() - 0.5) < 1.5 && l.getY() < cy + 0.5 && l.getY() > cy - 6;
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        for (Game g : new ArrayList<>(games.values())) g.tick();
        reportStatus();
    }

    // ------------------------------------------------------------------ joueurs (hors partie)

    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (gameOf(id) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (games.isEmpty()) { p.sendMessage(PREFIX + "§cLa map n'est pas chargée."); return; }
        Game spec = spectatorGame(id);
        if (spec != null) {
            spec.spectators.remove(id);
            if (p.getGameMode() == GameMode.SPECTATOR && mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
        }
        Game g = openGame(1, null);
        if (g != null) {
            g.add(p);
            return;
        }
        if (games.size() >= maxInstances()) { p.sendMessage(PREFIX + "§cToutes les parties sont pleines."); return; }
        if (model == null || modelBusy) { p.sendMessage(PREFIX + "§cNouvelle partie impossible pour l'instant (map en préparation), réessaie dans un instant."); return; }
        createGame(p);
    }

    private void leave(Player p, boolean teleport) {
        Game g = gameOf(p.getUniqueId());
        if (g != null) g.leave(p, teleport);
    }

    /**
     * Renvoi au hub. Seulement si le joueur est dans un monde du jeu ou déjà au hub : ailleurs (skyblock...)
     * on ne le téléporte ni ne le réinitialise. Dans un monde du jeu, la remise à zéro touche l'inventaire
     * de CE monde (Multiverse-Inventories charge ensuite celui du hub) ; au hub, celui du hub.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    /**
     * Le plugin ne touche à l'inventaire, à la vie, aux effets ou au mode de jeu d'un joueur que s'il est
     * dans un monde du jeu (partie, copie, modèle) ou au hub : jamais dans un autre monde (skyblock...),
     * sinon Multiverse-Inventories sauvegarde l'inventaire vidé de ce monde.
     */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        if (isGameWorld(w)) return true;
        World hub = hubWorld();
        return hub != null && w.equals(hub);
    }

    private void resetPlayer(Player p, GameMode mode) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setAbsorptionAmount(0);
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setVelocity(new Vector());
    }

    private static String clock(int s) {
        s = Math.max(0, s);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ================================================================== une partie

    /** Une partie de The Bridge, dans son propre monde (origine pour n = 1, copie sinon). */
    final class Game {
        final int index;
        World world;
        Location lobby;

        final Set<UUID> players = new LinkedHashSet<>();
        /** Inscrits qui attendent au hub que la copie soit prête. */
        final Set<UUID> pending = new LinkedHashSet<>();
        final Map<UUID, Side> teams = new LinkedHashMap<>();
        /** Rang du joueur dans son équipe (place dans la cage). */
        final Map<UUID, Integer> slots = new HashMap<>();
        final Map<Side, Integer> scores = new EnumMap<>(Side.class);
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, Integer> goals = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        /** Depuis quand le joueur n'a plus de flèche (recharge). */
        final Map<UUID, Long> arrowSince = new HashMap<>();
        /** Joueurs morts en attente de réapparition (spectateurs au-dessus de leur base). */
        final Set<UUID> respawning = new HashSet<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Blocs posés par les joueurs pendant la partie : les seuls qu'on peut casser, retirés à la fin. */
        final Set<Long> placed = new HashSet<>();
        /** Blocs du milieu (pont, mur) cassés pendant la partie, avec leur état d'origine : remis à la fin. */
        final Map<Long, BlockData> broken = new HashMap<>();
        /** Spectateurs (non inscrits) de la partie en cours. */
        final Set<UUID> spectators = new LinkedHashSet<>();

        State state = State.WAITING;
        int timer;
        boolean caged;
        int cageTimer;
        boolean building;
        /** Secondes passées vide (copies seulement : détruite au bout d'un moment). */
        int idle;

        Game(int index) {
            this.index = index;
        }

        void attach(World w) {
            world = w;
            lobby = new Location(w, 0.5, lobbyY + 1, 0.5);
            if (index == 1) applyRules(w);
        }

        int size() {
            return players.size() + pending.size();
        }

        boolean running() {
            return state == State.PLAYING || state == State.ENDING;
        }

        boolean inWorld(Player p) {
            return world != null && p.getWorld().equals(world);
        }

        /** Réapparition : sur l'île, devant son but, face au pont. */
        Location spawn(Side s) {
            return new Location(world, spawnX(s) + 0.5, cy + 1, 0.5, s == Side.ROUGE ? -90 : 90, 0);
        }

        Location cageSpot(Side s, int slot) {
            double[] o = CAGE_SLOTS[slot % CAGE_SLOTS.length];
            return new Location(world, spawnX(s) + 0.5 + o[0], cageFloor() + 1, 0.5 + o[1], s == Side.ROUGE ? -90 : 90, 0);
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        // -------------------------------------------------------------- boucles

        void tick() {
            if (world == null) return;
            switch (state) {
                case WAITING -> {
                    // la partie 1 n'est pas lancée pendant la copie du modèle (la map doit rester propre)
                    if (!building && !(index == 1 && modelBusy) && roomCount() >= cfg("min-players", 2)) {
                        state = State.STARTING;
                        timer = cfg("countdown", 20);
                        broadcast("§eAssez de joueurs : la partie commence dans §6" + timer + " s§e.");
                    }
                }
                case STARTING -> {
                    if (roomCount() < cfg("min-players", 2)) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                    } else {
                        if (roomCount() >= maxPlayers()) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0)
                            for (Player p : online(players)) {
                                if (!inWorld(p)) continue;
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.BLUE).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                            }
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> {
                    if (caged) {
                        if (cageTimer <= 0) release();
                        else {
                            for (Player p : online(teams.keySet())) {
                                p.showTitle(Title.title(Component.text(String.valueOf(cageTimer), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                                        scoreLine(), Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(100))));
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
                            }
                            cageTimer--;
                        }
                    } else if (--timer <= 0) {
                        int r = scores.getOrDefault(Side.ROUGE, 0), b = scores.getOrDefault(Side.BLEU, 0);
                        endGame(r == b ? null : (r > b ? Side.ROUGE : Side.BLEU), "Temps écoulé !");
                    }
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (games.get(index) != this) return; // détruite pendant ce tick
            for (Player p : online(players)) updateSidebar(p);
            // copie vide en attente : supprimée au bout de 10 s
            if (index > 1 && state == State.WAITING && !building && size() == 0 && spectators.isEmpty()) {
                if (++idle >= 10) destroyGame(this);
            } else idle = 0;
        }

        /** Toutes les 5 ticks : recharge de la flèche (affichée dans la barre d'XP). */
        void fastTick() {
            if (world == null || state != State.PLAYING || caged) return;
            long now = System.currentTimeMillis();
            long cooldown = Math.round(getConfig().getDouble("arrow-cooldown", 3.5) * 1000);
            for (Player p : online(teams.keySet())) {
                if (!inWorld(p) || p.getGameMode() != GameMode.SURVIVAL) continue;
                UUID id = p.getUniqueId();
                if (p.getInventory().contains(Material.ARROW)) {
                    if (arrowSince.remove(id) != null) {
                        p.setLevel(0);
                        p.setExp(0);
                    }
                    continue;
                }
                long since = arrowSince.computeIfAbsent(id, k -> now);
                long left = cooldown - (now - since);
                if (left <= 0) {
                    giveArrow(p);
                    arrowSince.remove(id);
                    p.setLevel(0);
                    p.setExp(0);
                } else {
                    p.setLevel((int) Math.ceil(left / 1000.0));
                    p.setExp((float) Math.max(0, Math.min(0.999, left / (double) cooldown)));
                }
            }
        }

        /** Joueurs inscrits présents dans la salle d'attente (le monde de la partie). */
        int roomCount() {
            int n = 0;
            for (Player p : online(players)) if (inWorld(p)) n++;
            return n;
        }

        int count(Side s) {
            int n = 0;
            for (Map.Entry<UUID, Side> e : teams.entrySet()) if (e.getValue() == s && Bukkit.getPlayer(e.getKey()) != null) n++;
            return n;
        }

        // -------------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("bridge", Criteria.DUMMY,
                        Component.text("THE ", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
                                .append(Component.text("BRIDGE", NamedTextColor.BLUE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        /** Écrit les lignes du scoreboard (une équipe par ligne, sans clignotement). */
        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("bridge");
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

        /** Ligne de score d'une équipe : pastilles pleines pour les buts marqués (chiffres si plus de 10 buts). */
        Component teamScore(Side s) {
            int goal = cfg("goals-to-win", 5), sc = scores.getOrDefault(s, 0);
            Component c = Component.text(s == Side.ROUGE ? "[R] " : "[B] ", s.color).decorate(TextDecoration.BOLD);
            if (goal <= 10) {
                c = c.append(Component.text("●".repeat(Math.min(sc, goal)), s.color).decoration(TextDecoration.BOLD, false))
                        .append(Component.text("●".repeat(Math.max(0, goal - sc)), NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.text(" " + sc, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
            } else c = c.append(Component.text(sc + "/" + goal, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
            return c;
        }

        Component scoreLine() {
            return Component.text(scores.getOrDefault(Side.ROUGE, 0) + "", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(scores.getOrDefault(Side.BLEU, 0) + "", NamedTextColor.BLUE).decorate(TextDecoration.BOLD));
        }

        void updateSidebar(Player p) {
            List<Component> l = new ArrayList<>();
            l.add(Component.text(" "));
            if (games.size() > 1 || index > 1)
                l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("#" + index, NamedTextColor.WHITE)));
            if (state == State.WAITING || state == State.STARTING) {
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(roomCount() + "/" + maxPlayers(), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("Buts pour gagner : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("goals-to-win", 5)), NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                if (building) l.add(Component.text("Construction de la map...", NamedTextColor.GOLD));
                else if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.BLUE)));
                else l.add(Component.text("En attente...", NamedTextColor.YELLOW));
            } else {
                if (state == State.PLAYING && caged)
                    l.add(Component.text("Départ dans : ", NamedTextColor.GRAY).append(Component.text(Math.max(0, cageTimer) + " s", NamedTextColor.YELLOW)));
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(state == State.ENDING ? 0 : timer), NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                l.add(teamScore(Side.ROUGE));
                l.add(teamScore(Side.BLEU));
                l.add(Component.text("   "));
                Side s = teams.get(p.getUniqueId());
                if (s != null) {
                    l.add(Component.text("Équipe : ", NamedTextColor.GRAY).append(Component.text(s.fem(), s.color)));
                    l.add(Component.text("Kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                    l.add(Component.text("Buts : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(goals.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                } else l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
            }
            l.add(Component.text("    "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        /** Pseudos colorés par équipe, sur le scoreboard de chaque joueur de la partie. */
        void colorNames() {
            for (Player viewer : online(players)) {
                Scoreboard b = board(viewer);
                for (Side s : Side.values()) {
                    Team t = b.getTeam("tb_" + s.key());
                    if (t == null) {
                        t = b.registerNewTeam("tb_" + s.key());
                        t.color(s.color);
                        t.prefix(Component.text("[" + s.label + "] ", s.color));
                        t.setAllowFriendlyFire(false);
                    }
                    for (String e : new ArrayList<>(t.getEntries())) t.removeEntry(e);
                }
                for (Map.Entry<UUID, Side> e : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) b.getTeam("tb_" + e.getValue().key()).addEntry(p.getName());
                }
            }
        }

        void clearNames() {
            for (Scoreboard b : boards.values())
                for (Side s : Side.values()) {
                    Team t = b.getTeam("tb_" + s.key());
                    if (t != null) t.unregister();
                }
        }

        // -------------------------------------------------------------- inscription

        /** Ajoute un joueur : téléporté dans la salle d'attente, ou mis en attente si la copie se prépare. */
        void add(Player p) {
            if (world == null) {
                pending.add(p.getUniqueId());
                p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
                reportStatus();
                return;
            }
            enter(p);
        }

        void enter(Player p) {
            players.add(p.getUniqueId());
            p.teleport(lobby);
            later(() -> prepareLobbyPlayer(p));
            broadcast("§a" + p.getName() + " §7a rejoint The Bridge (§f" + roomCount() + "/" + maxPlayers() + "§7).");
            reportStatus();
        }

        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || !players.contains(p.getUniqueId())) return;
            // téléportation refusée ou joueur reparti entre-temps : on ne touche à rien hors du monde de la partie
            if (!inWorld(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter The Bridge"));
            updateSidebar(p);
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (pending.remove(id)) {
                p.sendMessage(PREFIX + "§7Inscription annulée.");
                reportStatus();
                return;
            }
            if (!players.remove(id)) return;
            Side side = teams.remove(id);
            slots.remove(id);
            arrowSince.remove(id);
            respawning.remove(id);
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) sendToHub(p);
            if (side != null) {
                colorNames();
                if (state == State.PLAYING) {
                    broadcast(side.code() + p.getName() + " §7a quitté la partie.");
                    if (count(side) == 0) endGame(side.other(), "L'équipe " + side.fem() + " a abandonné !");
                }
            }
            reportStatus();
        }

        // -------------------------------------------------------------- partie

        void startGame() {
            List<Player> list = new ArrayList<>();
            for (Player p : online(players)) if (inWorld(p)) list.add(p);
            if (list.size() < 2 || building) { state = State.WAITING; return; }
            Collections.shuffle(list);
            int max = maxPlayers();
            if (list.size() > max) list = new ArrayList<>(list.subList(0, max));
            teams.clear();
            slots.clear();
            kills.clear();
            goals.clear();
            lastHit.clear();
            lastHitTime.clear();
            arrowSince.clear();
            removePlaced(); // normalement déjà fait à la fin de la partie précédente
            for (Side s : Side.values()) scores.put(s, 0);
            // équipes équilibrées : un joueur sur deux
            for (int i = 0; i < list.size(); i++) {
                teams.put(list.get(i).getUniqueId(), i % 2 == 0 ? Side.ROUGE : Side.BLEU);
                slots.put(list.get(i).getUniqueId(), i / 2);
            }
            colorNames();
            state = State.PLAYING;
            timer = cfg("game-time", 900);
            for (Player p : online(players))
                if (!teams.containsKey(p.getUniqueId())) p.sendMessage(PREFIX + "§7La partie est pleine : tu joueras la suivante.");
            for (Player p : list) {
                Side s = teams.get(p.getUniqueId());
                p.sendMessage(PREFIX + "§7Tu es dans l'équipe " + s.code() + s.fem() + "§7.");
            }
            broadcast("§eLa partie commence ! §7Saute dans le but adverse (portail) pour marquer. Première équipe à §f"
                    + cfg("goals-to-win", 5) + " buts§7, sinon celle qui mène après §f" + clock(timer) + "§7.");
            reportStatus();
            newRound();
        }

        /** Nouvelle manche : cages reconstruites, tout le monde dedans avec le kit, départ dans cage-time secondes. */
        void newRound() {
            respawning.clear();
            caged = true;
            cageTimer = cfg("cage-time", 5);
            clearEntities();
            lastHit.clear();
            lastHitTime.clear();
            arrowSince.clear();
            buildCages();
            for (Player p : online(teams.keySet())) {
                Side s = teams.get(p.getUniqueId());
                p.teleport(cageSpot(s, slots.getOrDefault(p.getUniqueId(), 0)));
                equip(p, GameMode.ADVENTURE);
            }
        }

        /** Ouverture des cages : le verre disparaît, tout le monde passe en survie. */
        void release() {
            caged = false;
            removeCages();
            for (Player p : online(teams.keySet())) {
                if (!inWorld(p)) continue;
                p.setGameMode(GameMode.SURVIVAL);
                p.setFallDistance(0);
                p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
                p.showTitle(Title.title(Component.text("GO !", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                        scoreLine(), Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            }
        }

        void buildCages() {
            for (Side s : Side.values()) cage(s, true);
        }

        void removeCages() {
            for (Side s : Side.values()) cage(s, false);
        }

        /** Cage de verre de l'équipe (intérieur 5x5, 4 de haut) au-dessus de son point de départ. */
        void cage(Side s, boolean build) {
            int cx = spawnX(s), f = cageFloor();
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++)
                    for (int dy = 0; dy <= 5; dy++) {
                        boolean shell = dy == 0 || dy == 5 || Math.abs(dx) == 3 || Math.abs(dz) == 3;
                        Block b = world.getBlockAt(cx + dx, f + dy, dz);
                        Material m = build && shell ? s.glass : Material.AIR;
                        if (b.getType() != m) b.setType(m, false);
                    }
        }

        /** Kit The Bridge : armure de cuir teinte, épée en fer, arc, pioche, terracotta, pommes dorées, 1 flèche. */
        void equip(Player p, GameMode mode) {
            Side s = teams.get(p.getUniqueId());
            if (!p.isOnline() || s == null || !inWorld(p)) return;
            resetPlayer(p, mode);
            PlayerInventory inv = p.getInventory();
            inv.setChestplate(armor(Material.LEATHER_CHESTPLATE, s));
            inv.setLeggings(armor(Material.LEATHER_LEGGINGS, s));
            inv.setBoots(armor(Material.LEATHER_BOOTS, s));
            inv.setItem(0, unbreakable(kit(new ItemStack(Material.IRON_SWORD))));
            inv.setItem(1, unbreakable(kit(new ItemStack(Material.BOW))));
            ItemStack pick = unbreakable(kit(new ItemStack(Material.DIAMOND_PICKAXE)));
            int eff = cfg("pickaxe-efficiency", 2);
            if (eff > 0) pick.addUnsafeEnchantment(Enchantment.EFFICIENCY, eff);
            inv.setItem(2, pick);
            int stacks = Math.max(1, Math.min(4, cfg("block-stacks", 2)));
            for (int i = 0; i < stacks; i++) inv.setItem(3 + i, kit(new ItemStack(s.terracotta, 64)));
            int apples = cfg("golden-apples", 8);
            if (apples > 0) inv.setItem(3 + stacks, kit(new ItemStack(Material.GOLDEN_APPLE, apples)));
            inv.setItem(8, kit(new ItemStack(Material.ARROW)));
            inv.setHeldItemSlot(0);
        }

        /** Point marqué : annonce, et soit victoire, soit nouvelle manche depuis les cages. */
        void score(Player p, Side s) {
            scores.merge(s, 1, Integer::sum);
            goals.merge(p.getUniqueId(), 1, Integer::sum);
            broadcast(s.code() + p.getName() + " §7a marqué pour l'équipe " + s.code() + s.fem() + " §7! §f("
                    + "§c" + scores.get(Side.ROUGE) + " §7- §9" + scores.get(Side.BLEU) + "§f)");
            // Titre court : le pseudo seul, « a marqué » passe dans le sous-titre avec le score
            Component title = Component.text(p.getName(), s.color).decorate(TextDecoration.BOLD);
            Component sub = Component.text("a marqué !  ", s.color).append(scoreLine());
            for (Player o : online(teams.keySet())) {
                o.showTitle(Title.title(title, sub, Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                o.playSound(o.getLocation(), teams.get(o.getUniqueId()) == s ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 1f);
            }
            firework(new Location(world, goalX(s.other()) + 0.5, cy + 1, 0.5), s);
            if (scores.get(s) >= cfg("goals-to-win", 5)) endGame(s, "L'équipe " + s.fem() + " a marqué " + scores.get(s) + " buts !");
            else newRound();
        }

        /** Mort (coup fatal annulé, vide, propre but) : retour à sa base avec le kit rechargé. */
        void die(Player p, Player killer, String how) {
            Side s = teams.get(p.getUniqueId());
            if (s == null) return;
            if (killer != null && killer != p && teams.containsKey(killer.getUniqueId())) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " thebridge kill");
                killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                broadcast(s.code() + p.getName() + " §7" + how + " §7(par " + teams.get(killer.getUniqueId()).code() + killer.getName() + "§7)");
            } else broadcast(s.code() + p.getName() + " §7" + how);
            lastHit.remove(p.getUniqueId());
            lastHitTime.remove(p.getUniqueId());
            arrowSince.remove(p.getUniqueId());
            int delay = cfg("respawn-delay", 3);
            if (delay <= 0) {
                p.teleport(spawn(s));
                equip(p, GameMode.SURVIVAL);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
                return;
            }
            // Délai de réapparition : spectateur au-dessus de sa base, puis retour avec le kit
            UUID id = p.getUniqueId();
            respawning.add(id);
            resetPlayer(p, GameMode.SPECTATOR);
            Location view = spawn(s).add(0, 8, 0);
            view.setPitch(60f);
            p.teleport(view);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
            for (int i = 0; i < delay; i++) {
                int left = delay - i;
                Bukkit.getScheduler().runTaskLater(EemiBridge.this, () -> {
                    if (!respawning.contains(id) || !p.isOnline()) return;
                    p.showTitle(Title.title(Component.text("MORT", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                            Component.text("Réapparition dans " + left + " s", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                }, i * 20L);
            }
            Bukkit.getScheduler().runTaskLater(EemiBridge.this, () -> {
                // une nouvelle manche ou la fin de partie a déjà replacé le joueur
                if (!respawning.remove(id) || !p.isOnline() || state != State.PLAYING || caged || !teams.containsKey(id)) return;
                if (!inWorld(p)) return; // parti ailleurs (skyblock...) : on ne le ramène pas
                p.teleport(spawn(s));
                equip(p, GameMode.SURVIVAL);
            }, delay * 20L);
        }

        Player killerOf(Player p) {
            Long t = lastHitTime.get(p.getUniqueId());
            if (t == null || System.currentTimeMillis() - t > 10_000) return null;
            return Bukkit.getPlayer(lastHit.get(p.getUniqueId()));
        }

        void endGame(Side winner, String reason) {
            endGame(winner, reason, true);
        }

        /** rewards = false pour un arrêt admin (pas de récompenses EemiCore). */
        void endGame(Side winner, String reason, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            if (rewards) {
                for (Player p : online(teams.keySet())) {
                    eemi("reward " + p.getName() + " thebridge play");
                    if (winner != null && teams.get(p.getUniqueId()) == winner) eemi("reward " + p.getName() + " thebridge win");
                }
            }
            reportStatus();
            timer = cfg("end-time", 8);
            if (caged) {
                caged = false;
                removeCages();
            }
            Component title = winner == null ? Component.text("Égalité !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                    : Component.text("Victoire " + winner.label + " !", winner.color).decorate(TextDecoration.BOLD); // titre court (≤ 16 caractères)
            for (Player p : online(teams.keySet())) {
                p.showTitle(Title.title(title, Component.text(reason, NamedTextColor.GRAY)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (p.getGameMode() == GameMode.SURVIVAL && inWorld(p)) p.setGameMode(GameMode.ADVENTURE);
                if (winner != null && teams.get(p.getUniqueId()) == winner) {
                    Player w = p;
                    for (int i = 0; i < 4; i++)
                        Bukkit.getScheduler().runTaskLater(EemiBridge.this, () -> {
                            if (w.isOnline() && state == State.ENDING && inWorld(w)) firework(w.getLocation(), winner);
                        }, i * 20L);
                }
            }
            broadcast("§6" + reason + " §7Score final : §c" + scores.getOrDefault(Side.ROUGE, 0) + " §7- §9" + scores.getOrDefault(Side.BLEU, 0));
            UUID top = null;
            int best = 0;
            for (Map.Entry<UUID, Integer> e : goals.entrySet()) if (e.getValue() > best) { best = e.getValue(); top = e.getKey(); }
            if (top != null && Bukkit.getPlayer(top) != null) broadcast("§7Meilleur buteur : §f" + Bukkit.getPlayer(top).getName() + " §7(" + best + " buts)");
            top = null;
            best = 0;
            for (Map.Entry<UUID, Integer> e : kills.entrySet()) if (e.getValue() > best) { best = e.getValue(); top = e.getKey(); }
            if (top != null && Bukkit.getPlayer(top) != null) broadcast("§7Meilleur tueur : §f" + Bukkit.getPlayer(top).getName() + " §7(" + best + " kills)");
        }

        /**
         * Fin : partie 1 → tout le monde dans la salle d'attente, blocs posés retirés, map refaite.
         * Copie → les inscrits rejoignent une autre partie en attente qui a la place pour tous (la copie est
         * détruite), sinon la copie redevient une partie en attente (remise à zéro) ; vide, elle est détruite.
         */
        void backToLobby() {
            clearNames();
            removePlaced();
            teams.clear();
            slots.clear();
            arrowSince.clear();
            respawning.clear();
            state = State.WAITING;
            for (Player p : online(spectators)) {
                sendToHub(p);
                p.sendMessage(PREFIX + "§7Partie terminée, retour au hub.");
            }
            spectators.clear();
            if (index > 1) {
                List<Player> stay = online(players);
                if (stay.isEmpty()) {
                    destroyGame(this);
                    return;
                }
                Game other = openGame(stay.size(), this);
                if (other != null && other.world != null) {
                    for (Player p : stay) {
                        players.remove(p.getUniqueId());
                        boards.remove(p.getUniqueId());
                        other.enter(p);
                    }
                    destroyGame(this);
                    return;
                }
            }
            for (Player p : online(players)) {
                if (!mayTouch(p)) continue; // pas dans un monde du jeu ni au hub : on ne le téléporte pas
                p.teleport(lobby);
                later(() -> prepareLobbyPlayer(p));
            }
            reportStatus();
            Bukkit.getScheduler().runTaskLater(EemiBridge.this, () -> { if (games.get(index) == this) rebuild(null, null); }, 20L);
        }

        void removePlaced() {
            if (world == null) return;
            for (long k : placed) {
                Block b = world.getBlockAt(kx(k), ky(k), kz(k));
                if (!b.getType().isAir()) b.setType(Material.AIR, false);
            }
            placed.clear();
            // puis le pont et le mur cassés reprennent leur état d'origine (après les blocs posés à leur place)
            for (Map.Entry<Long, BlockData> e : broken.entrySet()) {
                long k = e.getKey();
                Block b = world.getBlockAt(kx(k), ky(k), kz(k));
                if (!b.getBlockData().equals(e.getValue())) b.setBlockData(e.getValue(), false);
            }
            broken.clear();
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, "Partie arrêtée par un admin.", false);
            if (state == State.ENDING) backToLobby();
            if (state == State.STARTING) state = State.WAITING;
        }

        /** Vide la zone de jeu puis construit la map (par paquets pour ne pas geler le serveur). */
        void rebuild(CommandSender sender, Runnable after) {
            if (world == null || building) return;
            building = true;
            clearEntities();
            placed.clear();
            broken.clear(); // la map entière est refaite
            apply(mapOps(), 0, sender, after);
        }

        void apply(List<Op> ops, int from, CommandSender sender, Runnable after) {
            if (games.get(index) != this || world == null) {
                building = false;
                return;
            }
            int end = Math.min(ops.size(), from + 40000);
            for (int i = from; i < end; i++) {
                Op o = ops.get(i);
                Block b = world.getBlockAt(o.x, o.y, o.z);
                if (o.data == null) {
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                } else if (!b.getBlockData().equals(o.data)) b.setBlockData(o.data, false);
            }
            if (end < ops.size()) {
                Bukkit.getScheduler().runTaskLater(EemiBridge.this, () -> apply(ops, end, sender, after), 1L);
                return;
            }
            building = false;
            clearEntities();
            getLogger().info("Map The Bridge construite (" + world.getName() + ").");
            if (sender != null) sender.sendMessage(PREFIX + "§aMap construite.");
            if (after != null) after.run();
        }

        /** Retire ce qu'une partie laisse : objets, flèches, XP, blocs qui tombent, mobs. */
        void clearEntities() {
            if (world == null) return;
            for (Entity e : world.getEntities())
                if (e instanceof Item || (e instanceof Projectile && !(e instanceof Firework)) || e instanceof ExperienceOrb
                        || e instanceof FallingBlock || e instanceof Mob) e.remove();
        }

        boolean active(Player p) {
            return state == State.PLAYING && !caged && teams.containsKey(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL;
        }

        /** Pose autorisée : en partie, dans la zone et les hauteurs permises, hors des buts, loin du but adverse et des zones de départ. */
        boolean canPlace(Player p, Block b) {
            if (!active(p)) return false;
            int x = b.getX(), y = b.getY(), z = b.getZ();
            if (inSpawnZone(x, z)) return false;
            if (y < cfg("build-min-y", 88) || y > cfg("build-max-y", 108)) return false;
            if (Math.abs(x) > cfg("build-half-length", 46) || Math.abs(z) > cfg("build-half-width", 20)) return false;
            for (Side s : Side.values())
                if (Math.abs(x - goalX(s)) <= 2 && Math.abs(z) <= 2 && y >= cy - 4) return false;
            Side enemy = teams.get(p.getUniqueId()).other();
            return Math.hypot(x - goalX(enemy), z) > cfg("enemy-goal-radius", 5);
        }

        /** Regarder la partie en cours en spectateur, au-dessus du milieu du pont. */
        void spectate(Player p) {
            Location view = new Location(world, 0.5, cy + 15, 18.5);
            view.setYaw(180f);
            view.setPitch(35f);
            spectators.add(p.getUniqueId());
            // téléporter d'abord (Multiverse-Inventories sauvegarde l'inventaire du monde quitté), puis vider
            p.teleport(view);
            if (!inWorld(p)) {
                spectators.remove(p.getUniqueId());
                return;
            }
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage(PREFIX + "§7Tu regardes la partie #" + index + ", §f/lobby §7pour revenir.");
        }

        String describe() {
            int in = 0;
            for (Player p : online(players)) if (running() && teams.containsKey(p.getUniqueId())) in++;
            return "§f#" + index + " §7" + (world == null ? "(preparation)" : world.getName()) + " §e" + state.name().toLowerCase()
                    + (building ? " §6(construction)" : "") + " §7joueurs §f" + players.size() + "/" + maxPlayers()
                    + (pending.isEmpty() ? "" : " §7+" + pending.size() + " en attente") + " §7en jeu §f" + in
                    + " §7spectateurs §f" + spectators.size();
        }
    }

    // ------------------------------------------------------------------ construction de la map

    private BlockData data(Material m) {
        return dataCache.computeIfAbsent(m, Material::createBlockData);
    }

    private void put(List<Op> ops, int x, int y, int z, Material m) {
        ops.add(new Op(x, y, z, m == null ? null : data(m)));
    }

    private void put(List<Op> ops, int x, int y, int z, String data) {
        ops.add(new Op(x, y, z, Bukkit.createBlockData(data)));
    }

    /** Liste des blocs de la map : zone vidée, pont, deux îles (buts, décor), salle d'attente. */
    private List<Op> mapOps() {
        List<Op> ops = new ArrayList<>();
        int hl = cfg("build-half-length", 46) + 4, hw = cfg("build-half-width", 20) + 4;
        for (int x = -hl; x <= hl; x++)
            for (int z = -hw; z <= hw; z++)
                for (int y = cy - 18; y <= cy + 18; y++) ops.add(new Op(x, y, z, null));
        bridge(ops);
        for (Side s : Side.values()) island(ops, s);
        lobbyRoom(ops);
        return ops;
    }

    /**
     * Pont central façon Hypixel : une ligne qui relie les deux îles sans rien au milieu, posée sur un mur plein
     * de "bridge-depth" blocs de haut (rouge côté Rouge, bleu côté Bleu, colonne blanche au centre).
     */
    private void bridge(List<Op> ops) {
        int depth = Math.max(1, cfg("bridge-depth", 10));
        for (int x = -distance; x <= distance; x++) {
            Material m = x == 0 ? Material.WHITE_TERRACOTTA : (x < 0 ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA);
            for (int z = -bridgeHalf; z <= bridgeHalf; z++)
                for (int y = cy - depth + 1; y <= cy; y++) put(ops, x, y, z, m);
        }
    }

    /** Rayon de l'île selon l'angle (bord irrégulier, symétrique en z pour que les deux camps soient identiques). */
    private double islandRadius(double angle) {
        return radius + Math.cos(3 * angle) + 0.5 * Math.cos(5 * angle) + 0.3 * Math.cos(8 * angle);
    }

    /**
     * Une île : dessus en terracotta de l'équipe avec cercles de béton, bord en béton, allée blanche jusqu'au but ;
     * dessous en cône de pierre irrégulier ; but (trou bordé de cadres de portail de l'End, portail au fond) ;
     * arche derrière le but, lampadaires, deux arbres. Coordonnées locales : lx > 0 vers l'arrière de l'île.
     */
    private void island(List<Op> ops, Side s) {
        int d = s.dir(), cx = islandX(s);
        Random r = new Random(11);
        int ext = radius + 3;
        for (int lx = -ext; lx <= ext; lx++)
            for (int z = -ext; z <= ext; z++) {
                double dist = Math.hypot(lx, z), rr = islandRadius(Math.atan2(z, lx));
                int x = cx + lx * d;
                if (dist <= rr) {
                    Material top;
                    if (dist > rr - 1) top = s.concrete;
                    else if (Math.abs(z) <= 1 && lx < GOAL_OFFSET) top = Material.WHITE_TERRACOTTA;
                    else if (((int) dist) % 4 == 2) top = s.concrete;
                    else top = s.terracotta;
                    put(ops, x, cy, z, top);
                }
                // dessous : cône irrégulier
                for (int k = 1; k <= 10; k++) {
                    double rk = rr * Math.pow(1 - k / 11.0, 0.7);
                    if (dist > rk) continue;
                    if (k >= 6 && r.nextInt(6) == 0) continue;
                    Material m;
                    if (k == 1) m = s.terracotta;
                    else {
                        int n = r.nextInt(10);
                        m = n < 6 ? Material.STONE : n < 8 ? Material.ANDESITE : n < 9 ? Material.COBBLESTONE : Material.TUFF;
                    }
                    put(ops, x, cy - k, z, m);
                }
            }
        // point de départ
        int sx = cx + SPAWN_OFFSET * d;
        put(ops, sx, cy, 0, Material.CHISELED_QUARTZ_BLOCK);
        // but : trou 3x3 (portail de l'End au fond), cadres de portail autour, obsidienne dessous
        int gx = goalX(s);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                int x = gx + dx;
                boolean hole = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (hole) {
                    put(ops, x, cy, dz, (Material) null);
                    put(ops, x, cy - 1, dz, (Material) null);
                    put(ops, x, cy - 2, dz, Material.END_PORTAL);
                } else {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (corner) put(ops, x, cy, dz, Material.OBSIDIAN);
                    else {
                        String facing = Math.abs(dx) == 2 ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north");
                        put(ops, x, cy, dz, "minecraft:end_portal_frame[eye=true,facing=" + facing + "]");
                    }
                    put(ops, x, cy - 1, dz, Material.OBSIDIAN);
                    put(ops, x, cy - 2, dz, Material.OBSIDIAN);
                }
                put(ops, x, cy - 3, dz, Material.OBSIDIAN);
            }
        // arche derrière le but
        int ax = cx + (radius - 2) * d;
        for (int z = -3; z <= 3; z++) {
            if (Math.abs(z) == 3) {
                for (int y = cy + 1; y <= cy + 5; y++) put(ops, ax, y, z, s.concrete);
                put(ops, ax, cy + 6, z, Material.CHISELED_QUARTZ_BLOCK);
            } else put(ops, ax, cy + 5, z, Math.abs(z) == 2 ? Material.QUARTZ_BRICKS : s.concrete);
        }
        put(ops, ax, cy + 4, 0, "minecraft:lantern[hanging=true]");
        put(ops, ax, cy + 6, 0, s.concrete);
        put(ops, ax, cy + 7, 0, "minecraft:lantern");
        // lampadaires
        for (int[] pos : new int[][]{{-7, -7}, {-7, 7}, {6, -6}, {6, 6}}) {
            int x = cx + pos[0] * d;
            put(ops, x, cy, pos[1], Material.QUARTZ_BRICKS);
            put(ops, x, cy + 1, pos[1], s.concrete);
            put(ops, x, cy + 2, pos[1], "minecraft:stone_brick_wall[up=true]");
            put(ops, x, cy + 3, pos[1], "minecraft:stone_brick_wall[up=true]");
            put(ops, x, cy + 4, pos[1], "minecraft:lantern");
        }
        // deux arbres sur les côtés
        for (int tz : new int[]{-8, 8}) tree(ops, cx - 2 * d, tz, s, r);
    }

    private void tree(List<Op> ops, int x, int z, Side s, Random r) {
        String log = "minecraft:" + s.wood + "_log", leaves = "minecraft:" + s.wood + "_leaves[persistent=true]";
        put(ops, x, cy, z, Material.MOSS_BLOCK);
        for (int y = cy + 1; y <= cy + 4; y++) put(ops, x, y, z, log);
        for (int dy = 3; dy <= 6; dy++) {
            int rad = dy <= 4 ? 2 : (dy == 5 ? 1 : 0);
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx == 0 && dz == 0 && dy <= 4) continue;
                    if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2 && r.nextInt(2) == 0) continue;
                    put(ops, x + dx, cy + dy, z + dz, leaves);
                }
        }
    }

    /** Salle d'attente au-dessus du pont : moitié rouge, moitié bleue, vitre au centre, murs invisibles. */
    private void lobbyRoom(List<Op> ops) {
        int y = lobbyY;
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++)
                for (int dy = 0; dy <= 5; dy++) put(ops, x, y + dy, z, (Material) null);
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                boolean edge = Math.abs(x) == 8 || Math.abs(z) == 8;
                Material floor;
                if (edge) floor = Material.QUARTZ_BRICKS;
                else if (Math.abs(x) <= 1 && Math.abs(z) <= 1) floor = Material.GLASS;
                else if (x == 0) floor = Material.WHITE_TERRACOTTA;
                else floor = x < 0 ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA;
                put(ops, x, y, z, floor);
                boolean corner = Math.abs(x) == 8 && Math.abs(z) == 8;
                if (corner) {
                    for (int dy = 1; dy <= 3; dy++) put(ops, x, y + dy, z, Material.QUARTZ_PILLAR);
                    put(ops, x, y + 4, z, Material.LANTERN);
                } else if (edge) for (int dy = 1; dy <= 4; dy++) put(ops, x, y + dy, z, Material.BARRIER);
            }
        // bannières de couleur des deux camps (laine) sur les murs est/ouest
        for (int z = -2; z <= 2; z++) {
            put(ops, -8, y + 1, z, Material.RED_WOOL);
            put(ops, 8, y + 1, z, Material.BLUE_WOOL);
        }
    }

    // ------------------------------------------------------------------ objets

    private void firework(Location l, Side s) {
        Firework fw = l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class);
        FireworkMeta m = fw.getFireworkMeta();
        m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(s.armor, Color.WHITE).build());
        m.setPower(1);
        fw.setFireworkMeta(m);
    }

    private void giveArrow(Player p) {
        ItemStack arrow = kit(new ItemStack(Material.ARROW));
        PlayerInventory inv = p.getInventory();
        ItemStack cur = inv.getItem(8);
        if (cur == null || cur.getType().isAir()) inv.setItem(8, arrow);
        else inv.addItem(arrow);
    }

    private ItemStack armor(Material type, Side s) {
        ItemStack it = unbreakable(kit(new ItemStack(type)));
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(s.armor);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack named(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    /** Objet de kit : marqué pour ne pas tomber à la mort ni être jeté. */
    private ItemStack kit(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack unbreakable(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    /** Critique : chute en cours (règle vanilla/1.8), sans échelle, eau, cécité ni monture. */
    private boolean critical(Player a) {
        return a.getFallDistance() > 0 && !a.isOnGround() && !a.isClimbing() && !a.isInWater()
                && !a.hasPotionEffect(PotionEffectType.BLINDNESS) && !a.isInsideVehicle();
    }

    // ------------------------------------------------------------------ événements : blocs

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemibridge.admin");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        if (!isGameWorld(b.getWorld()) || admin(e.getPlayer())) return;
        Game g = gameAt(b.getWorld());
        if (g == null || !g.canPlace(e.getPlayer(), b)) {
            e.setCancelled(true);
            if (g != null && g.active(e.getPlayer()))
                e.getPlayer().sendActionBar(Component.text(inSpawnZone(b.getX(), b.getZ())
                        ? "Zone de départ protégée : tu ne peux pas construire ici !" : "Tu ne peux pas construire ici !", NamedTextColor.RED));
            return;
        }
        g.placed.add(key(b.getX(), b.getY(), b.getZ()));
    }

    /**
     * On casse les blocs posés par les joueurs et, si breakable-middle, le pont et le mur du milieu (remis à la fin
     * de la partie) ; la terracotta revient dans l'inventaire (celle de son équipe), rien ne tombe au sol.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        if (!isGameWorld(b.getWorld()) || admin(p)) return;
        Game g = gameAt(b.getWorld());
        long k = key(b.getX(), b.getY(), b.getZ());
        if (g == null || !g.active(p)) {
            e.setCancelled(true);
            return;
        }
        if (!g.placed.remove(k)) {
            // bloc de la map : seul le milieu (pont + mur dessous, hors îles) se casse, et il est noté pour être remis
            BlockData orig = breakableMiddle() ? middle.get(k) : null;
            if (orig == null || b.getType() != orig.getMaterial()) {
                e.setCancelled(true);
                return;
            }
            g.broken.putIfAbsent(k, orig);
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        if (b.getType().name().endsWith("_TERRACOTTA")) {
            Side s = g.teams.get(p.getUniqueId());
            int have = 0;
            for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == s.terracotta) have += it.getAmount();
            if (have < Math.max(1, Math.min(4, cfg("block-stacks", 2))) * 64) p.getInventory().addItem(kit(new ItemStack(s.terracotta)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ événements : joueurs

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isGameWorld(e.getLocation().getWorld()) && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        if (isGameWorld(e.getLocation().getWorld())) e.setCancelled(true);
    }

    /** Le portail de l'End des buts n'est qu'un décor : aucune téléportation. */
    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent e) {
        if (isGameWorld(e.getEntity().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (isGameWorld(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent e) {
        if (isGameWorld(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !isGameWorld(victim.getWorld())) return;
        UUID id = victim.getUniqueId();
        Game g = gameOf(id);
        if (g == null || !g.inWorld(victim) || !g.active(victim) || e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            return;
        }
        Player attacker = null;
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            attacker = d instanceof Player pl ? pl : d instanceof Projectile proj && proj.getShooter() instanceof Player pl ? pl : null;
            if (attacker != null && attacker != victim) {
                if (g.teams.get(attacker.getUniqueId()) == g.teams.get(id) || !g.active(attacker)) {
                    e.setCancelled(true);
                    return;
                }
                g.lastHit.put(id, attacker.getUniqueId());
                g.lastHitTime.put(id, System.currentTimeMillis());
                // anti-critique : le bonus x1,5 (vanilla ou OldCombatMechanics) est retiré des coups d'épée
                if (by.getDamager() == attacker && getConfig().getBoolean("no-critical-hits", true)
                        && (by.isCritical() || critical(attacker)))
                    e.setDamage(e.getDamage() / getConfig().getDouble("critical-multiplier", 1.5));
            }
        }
        if (e.isCancelled()) return;
        // coup fatal : pas d'écran de mort, retour direct à la base
        if (e.getFinalDamage() >= victim.getHealth()) {
            e.setCancelled(true);
            Player killer = attacker != null && attacker != victim ? attacker : g.killerOf(victim);
            String how = switch (e.getCause()) {
                case PROJECTILE -> "a été abattu à l'arc";
                case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> "a été tué";
                case LAVA, FIRE, FIRE_TICK -> "a brûlé";
                default -> "est mort";
            };
            g.die(victim, killer, how);
        }
    }

    /** Sécurité (/kill...) : pas de drops, réapparition immédiate à la base. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Game g = gameOf(p.getUniqueId());
        if (g == null || !g.inWorld(p)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        e.setKeepInventory(false);
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && p.isDead()) p.spigot().respawn(); }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p.getUniqueId());
        if (g == null || g.lobby == null || !g.inWorld(p)) return;
        Side s = g.teams.get(p.getUniqueId());
        if (s != null && g.state == State.PLAYING) {
            e.setRespawnLocation(g.caged ? g.cageSpot(s, g.slots.getOrDefault(p.getUniqueId(), 0)) : g.spawn(s));
            later(() -> { if (g.state == State.PLAYING && g.teams.containsKey(p.getUniqueId()) && g.inWorld(p)) g.equip(p, g.caged ? GameMode.ADVENTURE : GameMode.SURVIVAL); });
            return;
        }
        if (s != null && g.state == State.ENDING) {
            e.setRespawnLocation(g.spawn(s));
            later(() -> { if (g.inWorld(p)) resetPlayer(p, GameMode.ADVENTURE); });
            return;
        }
        e.setRespawnLocation(g.lobby);
        later(() -> g.prepareLobbyPlayer(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p.getUniqueId());
        if (g == null || g.world == null) return;
        Location to = e.getTo();
        if (!to.getWorld().equals(g.world)) return;
        if (to.getBlockY() == e.getFrom().getBlockY() && to.getBlockX() == e.getFrom().getBlockX()
                && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        Side s = g.teams.get(p.getUniqueId());
        if (s == null || !g.running()) {
            // salle d'attente : on ne tombe pas
            if (to.getY() < lobbyY - 3) e.setTo(g.lobby.clone().setDirection(to.getDirection()));
            return;
        }
        if (g.state == State.ENDING) {
            if (to.getY() < voidY) e.setTo(g.spawn(s));
            return;
        }
        if (g.caged) {
            double cx = spawnX(s) + 0.5;
            if (Math.abs(to.getX() - cx) > 2.7 || Math.abs(to.getZ() - 0.5) > 2.7 || to.getY() < cageFloor()) {
                Location back = g.cageSpot(s, g.slots.getOrDefault(p.getUniqueId(), 0));
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                e.setTo(back);
            }
            return;
        }
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (to.getY() < voidY) {
            g.die(p, g.killerOf(p), "est tombé dans le vide");
            return;
        }
        if (inGoal(to, s.other())) g.score(p, s);
        else if (inGoal(to, s)) g.die(p, g.killerOf(p), "est tombé dans son propre but");
    }

    /** Une seule flèche : pas de ramassage, elle disparaît à l'impact ; la recharge démarre au tir. */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = gameOf(p.getUniqueId());
        if (g == null || !g.teams.containsKey(p.getUniqueId()) || !g.inWorld(p)) return;
        if (!g.active(p)) {
            e.setCancelled(true);
            return;
        }
        if (e.getProjectile() instanceof AbstractArrow a) a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        g.arrowSince.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (isGameWorld(e.getEntity().getWorld()) && e.getEntity() instanceof AbstractArrow a)
            Bukkit.getScheduler().runTask(this, a::remove);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (gameOf(p.getUniqueId()) == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté The Bridge.");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Game g = gameOf(e.getPlayer().getUniqueId());
        if (g != null && g.inWorld(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = gameOf(p.getUniqueId());
        if (g != null && g.inWorld(p)) e.setCancelled(true);
    }

    /** Un inscrit qui quitte le monde de SA partie (/lobby, /mv tp...) quitte la partie, au lieu d'y être renvoyé. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player sp = e.getPlayer();
        Game spec = spectatorGame(sp.getUniqueId());
        if (spec != null && !spec.inWorld(sp)) {
            // spectateur parti (/lobby) : il redevient un joueur normal
            spec.spectators.remove(sp.getUniqueId());
            if (sp.getGameMode() == GameMode.SPECTATOR && mayTouch(sp)) sp.setGameMode(GameMode.SURVIVAL);
        }
        Game g = gameOf(sp.getUniqueId());
        if (g != null && g.world != null && e.getFrom().equals(g.world) && !g.inWorld(sp)) leave(sp, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game spec = spectatorGame(e.getPlayer().getUniqueId());
        if (spec != null) {
            spec.spectators.remove(e.getPlayer().getUniqueId());
            sendToHub(e.getPlayer());
        }
        if (gameOf(e.getPlayer().getUniqueId()) != null) leave(e.getPlayer(), true);
    }

    // ------------------------------------------------------------------ commandes

    /** Partie visée par une commande admin : numéro donné, sinon celle de l'admin, sinon la partie 1. */
    private Game target(CommandSender sender, String[] args) {
        if (args.length > 1) {
            try {
                return games.get(Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (sender instanceof Player p) {
            Game g = gameOf(p.getUniqueId());
            if (g == null) g = gameAt(p.getWorld());
            if (g != null) return g;
        }
        return games.get(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemibridge.admin");
        switch (sub) {
            case "join" -> {
                Player target = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target);
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "leave" -> {
                if (p == null) return true;
                Game spec = spectatorGame(p.getUniqueId());
                if (spec != null) {
                    spec.spectators.remove(p.getUniqueId());
                    sendToHub(p);
                    return true;
                }
                if (gameOf(p.getUniqueId()) == null) return true;
                leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté The Bridge.");
            }
            case "start" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null || g.world == null) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cLa partie #" + g.index + " est déjà en cours."); return true; }
                if (g.roomCount() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs dans la salle d'attente."); return true; }
                if (g.building) { sender.sendMessage(PREFIX + "§cLa map est en construction."); return true; }
                g.startGame();
                sender.sendMessage(PREFIX + "§7Partie #" + g.index + " lancée.");
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie #" + g.index + " arrêtée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ") ; modèle : "
                        + (model != null ? "§aprêt" : modelBusy ? "§6en copie" : "§cabsent"));
                for (Game g : games.values()) sender.sendMessage("§8- " + g.describe());
            }
            case "build" -> {
                if (!admin) return true;
                Game g1 = games.get(1);
                if (g1 == null || g1.world == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g1.running()) { sender.sendMessage(PREFIX + "§cAttends la fin de la partie #1."); return true; }
                if (g1.building) { sender.sendMessage(PREFIX + "§cConstruction déjà en cours."); return true; }
                if (modelBusy || copying > 0) { sender.sendMessage(PREFIX + "§cCopie du modèle en cours, réessaie dans un instant."); return true; }
                sender.sendMessage(PREFIX + "§7Construction de la map (partie #1), puis copie du modèle...");
                g1.rebuild(sender, this::remakeModel);
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.running()) { sender.sendMessage(PREFIX + "§cAttends la fin des parties en cours."); return true; }
                loadSettings();
                sender.sendMessage(PREFIX + "§aConfig rechargée §7(/bridge build pour reconstruire la map si la géométrie a changé).");
            }
            default -> sender.sendMessage(PREFIX + "§7/bridge <join|leave|spectate [joueur]>"
                    + (admin ? " §8| admin : start|stop [n], parties, build, reload" : ""));
        }
        return true;
    }

    /** Spectateur : la partie du joueur nommé, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String name) {
        if (gameOf(p.getUniqueId()) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/bridge leave §cd'abord."); return; }
        Game g = null;
        if (name != null) {
            Player t = Bukkit.getPlayerExact(name);
            if (t == null) { p.sendMessage(PREFIX + "§cJoueur introuvable."); return; }
            g = gameOf(t.getUniqueId());
            if (g == null || g.state != State.PLAYING) { p.sendMessage(PREFIX + "§c" + t.getName() + " n'est pas en partie."); return; }
        } else {
            for (Game c : games.values())
                if (c.state == State.PLAYING && c.world != null && (g == null || c.teams.size() > g.teams.size())) g = c;
            if (g == null) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return; }
        }
        Game old = spectatorGame(p.getUniqueId());
        if (old != null) old.spectators.remove(p.getUniqueId());
        g.spectate(p);
    }

    // ------------------------------------------------------------------ EemiCore

    /** Prévient EemiCore (statut des parties, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /** Statut agrégé de toutes les parties pour EemiCore : envoyé s'il a changé (ou toutes les 30 s). */
    private void reportStatus() {
        int waiting = 0, playing = 0;
        boolean anyWaiting = false, anyPlaying = false;
        for (Game g : games.values()) {
            if (g.state == State.WAITING || g.state == State.STARTING) anyWaiting = true;
            else anyPlaying = true;
            waiting += g.pending.size();
            for (Player p : online(g.players)) {
                if (g.running() && g.teams.containsKey(p.getUniqueId())) playing++;
                else waiting++;
            }
        }
        String state = anyWaiting || !anyPlaying ? "waiting" : "playing";
        String status = "status thebridge " + state + " " + waiting + " " + playing + " " + (maxPlayers() * maxInstances());
        long now = System.currentTimeMillis();
        if (status.equals(lastStatus) && now - lastStatusAt < 30_000) return;
        lastStatus = status;
        lastStatusAt = now;
        eemi(status);
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
